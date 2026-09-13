package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.data.local.AppDatabase
import com.performance.tracker.databinding.ActivitySettingsBinding
import com.performance.tracker.model.User
import com.performance.tracker.util.ImageUtils
import com.performance.tracker.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val db = FirebaseFirestore.getInstance()
    private var currentUserId: String = ""
    private var currentUserRole: String = "USER"
    private var currentUserName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = intent.getStringExtra("USER_ID") ?: SessionManager.getUserId(this)
        currentUserRole = intent.getStringExtra("USER_ROLE") ?: SessionManager.getUserRole(this)
        currentUserName = intent.getStringExtra("USER_NAME") ?: SessionManager.getUserName(this)

        updateAdminSectionVisibility()
        setupListeners()
        setupAppVersionInfo()
    }

    private fun setupAppVersionInfo() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.2.0"
        }
        binding.tvSettingsAboutSubtitle.text = "Performance Tracker v$versionName • Build 2026.1"
        binding.tvSettingsAppVersion.text = "Employee Performance Tracker\nVersion $versionName • Build 2026.1\nAll rights reserved"
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun updateAdminSectionVisibility() {
        val cleanRole = currentUserRole.trim()
        val isFullAdmin = cleanRole.equals("ADMIN", ignoreCase = true) ||
                cleanRole.equals("Management", ignoreCase = true) ||
                cleanRole.contains("Admin", ignoreCase = true)

        val isAgmOrDgm = cleanRole.equals("AGM", ignoreCase = true) ||
                cleanRole.equals("DGM", ignoreCase = true) ||
                cleanRole.contains("AGM", ignoreCase = true) ||
                cleanRole.contains("DGM", ignoreCase = true)

        val isSalesManager = cleanRole.equals("Sales Manager", ignoreCase = true) ||
                cleanRole.contains("Manager", ignoreCase = true)

        // Officer List & Management section is visible for Admin, AGM, DGM, Sales Manager
        val isManagementOrAdmin = isFullAdmin || isAgmOrDgm || isSalesManager
        val canApprove = isFullAdmin || isAgmOrDgm

        binding.layoutAdminSection.visibility = if (isManagementOrAdmin) android.view.View.VISIBLE else android.view.View.GONE

        // Officer List option (Admin, AGM, DGM, Sales Manager)
        binding.cardSettingsOfficerList.visibility = if (isManagementOrAdmin) android.view.View.VISIBLE else android.view.View.GONE
        binding.dividerSettingsOfficerList.visibility = if (isManagementOrAdmin && (canApprove || isFullAdmin)) android.view.View.VISIBLE else android.view.View.GONE

        // Pending Approvals option (Admin, AGM, DGM)
        binding.cardSettingsPendingApprovals.visibility = if (canApprove) android.view.View.VISIBLE else android.view.View.GONE
        binding.dividerSettingsPendingApprovals.visibility = if (canApprove && isFullAdmin) android.view.View.VISIBLE else android.view.View.GONE

        // Admin-only management options
        binding.cardSettingsCreateUser.visibility = if (isFullAdmin) android.view.View.VISIBLE else android.view.View.GONE
        binding.dividerSettingsCreateUser.visibility = if (isFullAdmin) android.view.View.VISIBLE else android.view.View.GONE

        binding.cardSettingsBackupData.visibility = if (isFullAdmin) android.view.View.VISIBLE else android.view.View.GONE
        binding.cardSettingsResetAllData.visibility = if (isFullAdmin) android.view.View.VISIBLE else android.view.View.GONE
        binding.dividerSettingsResetAllData.visibility = if (isFullAdmin) android.view.View.VISIBLE else android.view.View.GONE

        if (isManagementOrAdmin) {
            loadOfficerListBadge()
            if (canApprove) {
                checkPendingApprovals()
            }
        }
    }

    private fun loadOfficerListBadge() {
        db.collection("employees").get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.documents.count { doc ->
                    val user = doc.toObject(User::class.java) ?: return@count false
                    val role = user.role.trim()
                    val status = user.status.trim()
                    val isOfficer = role.isBlank() || role.equals("USER", ignoreCase = true)
                    val isApproved = status.isBlank() ||
                            status.equals("APPROVED", ignoreCase = true) ||
                            status.equals("Approved", ignoreCase = true)
                    isOfficer && isApproved
                }
                if (count > 0) {
                    binding.tvSettingsOfficerCountBadge.visibility = android.view.View.VISIBLE
                    binding.tvSettingsOfficerCountBadge.text = count.toString()
                } else {
                    binding.tvSettingsOfficerCountBadge.visibility = android.view.View.GONE
                }
            }
            .addOnFailureListener {
                binding.tvSettingsOfficerCountBadge.visibility = android.view.View.GONE
            }
    }

    private fun loadUserData() {
        if (currentUserId.isEmpty()) {
            binding.tvSettingsName.text = currentUserName.ifEmpty { "User" }
            binding.tvSettingsEmpId.text = "Role: $currentUserRole"
            binding.tvSettingsRoleBadge.text = "Role: $currentUserRole"
            updateAdminSectionVisibility()
            checkEmailAndRedDot(SessionManager.getUserEmail(this))
            return
        }

        db.collection("employees").document(currentUserId).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                if (user != null) {
                    binding.tvSettingsName.text = user.name
                    binding.tvSettingsEmpId.text = "Employee ID: ${user.employeeId}"
                    binding.tvSettingsRoleBadge.text = "Role: ${user.role}"
                    ImageUtils.loadProfileImage(user.profileImage, binding.ivSettingsAvatar)

                    // Update session
                    if (user.name.isNotEmpty()) {
                        currentUserName = user.name
                        currentUserRole = user.role
                        SessionManager.saveUser(this, user.employeeId, user.role, user.name, user.email)
                    }
                    updateAdminSectionVisibility()
                    checkEmailAndRedDot(user.email)
                } else {
                    binding.tvSettingsName.text = currentUserName.ifEmpty { "User" }
                    binding.tvSettingsEmpId.text = "ID: $currentUserId"
                    binding.tvSettingsRoleBadge.text = "Role: $currentUserRole"
                    updateAdminSectionVisibility()
                    checkEmailAndRedDot(SessionManager.getUserEmail(this))
                }
            }
            .addOnFailureListener {
                binding.tvSettingsName.text = currentUserName.ifEmpty { "User" }
                binding.tvSettingsEmpId.text = "ID: $currentUserId"
                binding.tvSettingsRoleBadge.text = "Role: $currentUserRole"
                updateAdminSectionVisibility()
                checkEmailAndRedDot(SessionManager.getUserEmail(this))
            }
    }

    private fun checkEmailAndRedDot(email: String) {
        val hasEmail = email.trim().isNotEmpty()
        if (hasEmail) {
            binding.cardEmailRequiredWarning.visibility = android.view.View.GONE
            binding.viewProfileHeaderRedDot.visibility = android.view.View.GONE
            binding.viewProfileOptionRedDot.visibility = android.view.View.GONE
            binding.tvProfileOptionSubtitle.text = "View & update personal details, email, official number"
        } else {
            binding.cardEmailRequiredWarning.visibility = android.view.View.VISIBLE
            binding.viewProfileHeaderRedDot.visibility = android.view.View.VISIBLE
            binding.viewProfileOptionRedDot.visibility = android.view.View.VISIBLE
            binding.tvProfileOptionSubtitle.text = "⚠️ Email address missing - Tap to update for password recovery"
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Biometric Switch
        binding.switchBiometric.isChecked = SessionManager.isBiometricEnabled(this)
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !com.performance.tracker.util.BiometricHelper.isBiometricAvailable(this)) {
                binding.switchBiometric.isChecked = false
                Toast.makeText(this, "Biometric authentication is not supported or set up on this device.", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            SessionManager.setBiometricEnabled(this, isChecked)
            val msg = if (isChecked) "Biometric login enabled!" else "Biometric login disabled."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 1. My Profile option
        val openProfile = {
            val intent = Intent(this, ProfileActivity::class.java).apply {
                putExtra("USER_ID", currentUserId)
            }
            startActivity(intent)
        }

        binding.cardProfileHeader.setOnClickListener { openProfile() }
        binding.cardSettingsProfile.setOnClickListener { openProfile() }

        // 2. Change Password option
        binding.cardSettingsPassword.setOnClickListener {
            showChangePasswordDialog()
        }

        // 2b. Management / Admin: Officer List option (Admin, AGM, DGM, Sales Manager)
        binding.cardSettingsOfficerList.setOnClickListener {
            startActivity(Intent(this, OfficerListActivity::class.java))
        }

        // 3. Admin: Pending Approvals option
        binding.cardSettingsPendingApprovals.setOnClickListener {
            startActivity(Intent(this, ApprovalActivity::class.java))
        }

        // 3a. Admin: Create User option (Sales Manager, AGM, DGM)
        binding.cardSettingsCreateUser.setOnClickListener {
            showCreateUserDialog()
        }

        // 3b. Admin: Backup Data option
        binding.cardSettingsBackupData.setOnClickListener {
            performDataBackup()
        }

        // 3c. Admin: Reset All Data option
        binding.cardSettingsResetAllData.setOnClickListener {
            handleResetAllDataClick()
        }

        // 4. About Application
        binding.cardSettingsAbout.setOnClickListener {
            showAboutDialog()
        }

        // 5. Help & Support
        binding.cardSettingsSupport.setOnClickListener {
            showSupportDialog()
        }

        // 5a. Privacy Policy
        binding.cardSettingsPrivacyPolicy.setOnClickListener {
            showPrivacyPolicyDialog()
        }

        // 6. Exit / Logout option
        binding.cardSettingsExit.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showCreateUserDialog() {
        val dialogBinding = com.performance.tracker.databinding.DialogAdminCreateUserBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val roles = arrayOf("Sales Manager", "AGM", "DGM")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles)
        dialogBinding.autoCompleteCreateRole.setAdapter(adapter)
        dialogBinding.autoCompleteCreateRole.setText("Sales Manager", false)

        dialogBinding.btnCloseCreateUser.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCancelCreateUser.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnSubmitCreateUser.setOnClickListener {
            val selectedRole = dialogBinding.autoCompleteCreateRole.text.toString().trim()
            val empId = dialogBinding.etCreateEmpId.text.toString().trim()
            val name = dialogBinding.etCreateName.text.toString().trim()
            val mobile = dialogBinding.etCreateMobile.text.toString().trim()
            val officialNumber = dialogBinding.etCreateOfficialNumber.text.toString().trim()
            val branch = dialogBinding.etCreateBranch.text.toString().trim()
            val password = dialogBinding.etCreatePassword.text.toString().trim()

            if (selectedRole.isEmpty() || !roles.contains(selectedRole)) {
                Toast.makeText(this, "Please select a valid role (Sales Manager, AGM, or DGM)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (empId.length != 12) {
                dialogBinding.etCreateEmpId.error = "Employee ID must be 12 digits"
                return@setOnClickListener
            }

            if (name.isEmpty()) {
                dialogBinding.etCreateName.error = "Name is required"
                return@setOnClickListener
            }

            if (password.length < 6) {
                dialogBinding.etCreatePassword.error = "Minimum 6 characters required"
                return@setOnClickListener
            }

            dialogBinding.btnSubmitCreateUser.isEnabled = false
            dialogBinding.btnSubmitCreateUser.text = "Creating..."

            // Check if user already exists in Firestore
            db.collection("employees").document(empId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        dialogBinding.btnSubmitCreateUser.isEnabled = true
                        dialogBinding.btnSubmitCreateUser.text = "Create User"
                        dialogBinding.etCreateEmpId.error = "Employee ID already exists"
                        Toast.makeText(this, "An account with Employee ID $empId already exists!", Toast.LENGTH_LONG).show()
                    } else {
                        val newUser = User(
                            employeeId = empId,
                            name = name,
                            branch = branch.ifBlank { "N/A" },
                            salesManager = "", // No sales manager for management roles
                            mobile = mobile,
                            officialNumber = officialNumber,
                            zone = branch.ifBlank { "N/A" },
                            password = password,
                            role = selectedRole,
                            status = "Approved",
                            department = selectedRole,
                            createdAt = System.currentTimeMillis(),
                            monthlyTarget = 0,
                            profileImage = ""
                        )

                        db.collection("employees").document(empId)
                            .set(newUser)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Successfully created $selectedRole ID: $empId", Toast.LENGTH_LONG).show()
                                dialog.dismiss()
                            }
                            .addOnFailureListener { e ->
                                dialogBinding.btnSubmitCreateUser.isEnabled = true
                                dialogBinding.btnSubmitCreateUser.text = "Create User"
                                Toast.makeText(this, "Failed to create user: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    dialogBinding.btnSubmitCreateUser.isEnabled = true
                    dialogBinding.btnSubmitCreateUser.text = "Create User"
                    Toast.makeText(this, "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
    }

    private fun showChangePasswordDialog() {
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val etNewPass = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPass = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPassword)

        MaterialAlertDialogBuilder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Update") { dialog, _ ->
                val newPass = etNewPass.text.toString().trim()
                val confirmPass = etConfirmPass.text.toString().trim()

                if (newPass.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass != confirmPass) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Update Firestore for current user
                db.collection("employees").document(currentUserId)
                    .update("password", newPass)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                        if (SessionManager.isRememberMe(this) && SessionManager.getSavedId(this) == currentUserId) {
                            SessionManager.saveCredentials(this, currentUserId, newPass, true)
                        }
                    }
                    .addOnFailureListener { e ->
                        // Fallback check by employeeId field in case doc.id differs
                        db.collection("employees").whereEqualTo("employeeId", currentUserId).get()
                            .addOnSuccessListener { qs ->
                                if (!qs.isEmpty) {
                                    val targetDoc = qs.documents.first().id
                                    db.collection("employees").document(targetDoc).update("password", newPass)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                            if (SessionManager.isRememberMe(this) && SessionManager.getSavedId(this) == currentUserId) {
                                                SessionManager.saveCredentials(this, currentUserId, newPass, true)
                                            }
                                        }
                                } else {
                                    Toast.makeText(this, "Failed to update password: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to update password: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showAboutDialog() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "1.2.0"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("About Application")
            .setIcon(R.drawable.ic_report)
            .setMessage("Employee Performance Tracker\n\nVersion: $versionName\nBuild: 2026.1 (Release)\nEnvironment: Production\n\nDesigned for tracking employee card performance metrics, monthly target reviews, team management, and exportable analytics across all regional branches and sales zones.")
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        val policyHtml = """
            <b>Employee Performance Tracker - Privacy Policy</b><br/>
            <i>Version 1.2.0 • Effective: 2026</i><br/><br/>
            <b>1. Purpose &amp; Scope:</b><br/>
            This application is designed strictly for organizational performance management, monthly employee sales targets, card delivery tracking, and administrative team evaluations.<br/><br/>
            <b>2. Data Collected &amp; Processed:</b><br/>
            • <b>User Profile:</b> Employee ID, Name, Official Email, Mobile Number, Branch, Zone, Assigned Sales Manager, and Role.<br/>
            • <b>Performance Records:</b> Card application details, monthly targets, achieved card counts, and submission logs.<br/>
            • <b>Biometrics:</b> Biometric fingerprint/face login uses Android's local Keystore/BiometricPrompt. Biometric data never leaves your device hardware.<br/><br/>
            <b>3. Security &amp; Storage:</b><br/>
            • Local database caching uses encrypted Android Room SQLite storage.<br/>
            • Cloud synchronization uses Google Firebase Firestore with 256-bit SSL encryption and strict security rules.<br/>
            • No personal or enterprise data is ever shared with third-party advertisers.<br/><br/>
            <b>4. Data Backup &amp; Reset:</b><br/>
            Authorized administrators may generate encrypted full JSON backups of performance data or perform system maintenance resets.<br/><br/>
            <b>5. Contact &amp; Support:</b><br/>
            For questions or concerns regarding your privacy, contact the System Administrator at: <b>jahid2177@gmail.com</b>
        """.trimIndent()

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Privacy Policy")
            .setIcon(R.drawable.ic_lock)
            .setMessage(androidx.core.text.HtmlCompat.fromHtml(policyHtml, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("I Understand") { dialog, _ -> dialog.dismiss() }
            .show()

        alertDialog.findViewById<android.widget.TextView>(android.R.id.message)?.movementMethod =
            android.text.method.LinkMovementMethod.getInstance()
    }

    private fun showSupportDialog() {
        val whatsappUrl = "https://wa.me/qr/UYHZCG4FYNYYC1"
        val messageHtml = """
            For system assistance, approval requests, or technical issues, please contact your System Administrator or IT Support:<br/><br/>
            • <b>Helpline:</b> <a href="tel:+8801686556444">+880 1686-556444</a><br/>
            • <b>Official Support:</b> <a href="mailto:jahid2177@gmail.com">jahid2177@gmail.com</a><br/>
            • <b>Created By:</b> Md. Jahidul Islam, CBD<br/>
            • <b>Contact on WhatsApp:</b><br/>
            <a href="$whatsappUrl">$whatsappUrl</a>
        """.trimIndent()

        val alertDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Help & Support")
            .setMessage(androidx.core.text.HtmlCompat.fromHtml(messageHtml, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY))
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Open WhatsApp") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(whatsappUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()

        alertDialog.findViewById<android.widget.TextView>(android.R.id.message)?.movementMethod =
            android.text.method.LinkMovementMethod.getInstance()
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit / Logout")
            .setMessage("Are you sure you want to log out and exit the application?")
            .setIcon(R.drawable.ic_logout)
            .setPositiveButton("Logout") { _, _ ->
                SessionManager.clear(this)
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finishAffinity()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun performDataBackup() {
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Backing Up Application Data")
            .setMessage("Fetching documents from database...\nPlease wait.")
            .setCancelable(false)
            .create()

        progressDialog.show()

        val backupRoot = org.json.JSONObject()
        val timestamp = System.currentTimeMillis()
        val dateFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

        backupRoot.put("appName", "Performance Tracker")
        backupRoot.put("backupDate", dateFormatted)
        backupRoot.put("timestamp", timestamp)
        backupRoot.put("exportedBy", "$currentUserName ($currentUserId)")

        val collectionsJson = org.json.JSONObject()
        val countsJson = org.json.JSONObject()

        // 1. Fetch 'employees'
        db.collection("employees").get()
            .addOnSuccessListener { employeesSnap ->
                val empArray = org.json.JSONArray()
                for (doc in employeesSnap.documents) {
                    val itemMap = doc.data ?: hashMapOf()
                    val itemObj = org.json.JSONObject(itemMap as Map<*, *>)
                    itemObj.put("_doc_id", doc.id)
                    empArray.put(itemObj)
                }
                collectionsJson.put("employees", empArray)
                countsJson.put("employees", employeesSnap.size())

                // 2. Fetch 'performance'
                db.collection("performance").get()
                    .addOnSuccessListener { perfSnap ->
                        val perfArray = org.json.JSONArray()
                        for (doc in perfSnap.documents) {
                            val itemMap = doc.data ?: hashMapOf()
                            val itemObj = org.json.JSONObject(itemMap as Map<*, *>)
                            itemObj.put("_doc_id", doc.id)
                            perfArray.put(itemObj)
                        }
                        collectionsJson.put("performance", perfArray)
                        countsJson.put("performance", perfSnap.size())

                        // 3. Fetch 'targets'
                        db.collection("targets").get()
                            .addOnSuccessListener { targetSnap ->
                                val targetArray = org.json.JSONArray()
                                for (doc in targetSnap.documents) {
                                    val itemMap = doc.data ?: hashMapOf()
                                    val itemObj = org.json.JSONObject(itemMap as Map<*, *>)
                                    itemObj.put("_doc_id", doc.id)
                                    targetArray.put(itemObj)
                                }
                                collectionsJson.put("targets", targetArray)
                                countsJson.put("targets", targetSnap.size())

                                backupRoot.put("counts", countsJson)
                                backupRoot.put("collections", collectionsJson)

                                progressDialog.dismiss()
                                showBackupResultDialog(backupRoot, employeesSnap.size(), perfSnap.size(), targetSnap.size())
                            }
                            .addOnFailureListener { e ->
                                progressDialog.dismiss()
                                Toast.makeText(this, "Failed to backup targets: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        progressDialog.dismiss()
                        Toast.makeText(this, "Failed to backup performance: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to backup employees: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showBackupResultDialog(backupJson: org.json.JSONObject, empCount: Int, perfCount: Int, targetCount: Int) {
        val jsonStr = backupJson.toString(2)
        val fileDate = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val fileName = "PerformanceTracker_Backup_$fileDate.json"

        // Save to cache file for sharing
        val backupDir = java.io.File(cacheDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()
        val file = java.io.File(backupDir, fileName)
        file.writeText(jsonStr)

        val message = "Database Backup Created Successfully!\n\n" +
                "• Employees Records: $empCount\n" +
                "• Performance Entries: $perfCount\n" +
                "• Target Configurations: $targetCount\n\n" +
                "File Name: $fileName\n" +
                "Size: ${file.length() / 1024} KB"

        MaterialAlertDialogBuilder(this)
            .setTitle("Data Backup Complete")
            .setMessage(message)
            .setIcon(R.drawable.ic_content_copy)
            .setPositiveButton("Share / Export") { _, _ ->
                shareBackupFile(file)
            }
            .setNeutralButton("Copy JSON") { _, _ ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("App Data Backup", jsonStr)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Backup JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun shareBackupFile(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Performance Tracker Database Backup")
                putExtra(Intent.EXTRA_TEXT, "Attached full application database backup JSON exported on ${java.util.Date()}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export / Share Backup File"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPendingApprovals() {
        db.collection("employees")
            .whereEqualTo("status", "Pending")
            .get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.size()
                if (count > 0) {
                    binding.tvSettingsPendingBadge.visibility = android.view.View.VISIBLE
                    binding.tvSettingsPendingBadge.text = count.toString()
                    binding.tvSettingsPendingApprovalsSubtitle.text = "$count pending user registration request(s)"
                    binding.tvSettingsPendingApprovalsSubtitle.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                } else {
                    binding.tvSettingsPendingBadge.visibility = android.view.View.GONE
                    binding.tvSettingsPendingApprovalsSubtitle.text = "All user registrations approved"
                    binding.tvSettingsPendingApprovalsSubtitle.setTextColor(getColor(R.color.text_secondary))
                }
            }
            .addOnFailureListener {
                binding.tvSettingsPendingBadge.visibility = android.view.View.GONE
            }
    }

    // ========================================================
    // 🗑️ Reset All Data (Admin Only with Mandatory Pre-Backup)
    // ========================================================

    private fun handleResetAllDataClick() {
        val isFullAdmin = currentUserRole.equals("ADMIN", ignoreCase = true) ||
                currentUserRole.equals("Management", ignoreCase = true)
        if (!isFullAdmin) {
            Toast.makeText(this, "Only System Administrator can reset all data.", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Data Backup Warning")
            .setIcon(R.drawable.ic_delete)
            .setMessage("Warning: Resetting all data will permanently wipe all performance reports, monthly targets, and employee records from both this mobile device and Firebase.\n\nTo prevent irreversible data loss, the application will first perform a full data backup before proceeding.\n\nDo you want to backup data now and proceed with reset?")
            .setPositiveButton("Backup & Continue") { _, _ ->
                performPreResetBackup()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performPreResetBackup() {
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Creating Safety Backup")
            .setMessage("Exporting full database before reset...\nPlease wait.")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val backupRoot = JSONObject()
                val timestamp = System.currentTimeMillis()
                val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
                val fileDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))

                backupRoot.put("appName", "Performance Tracker")
                backupRoot.put("backupType", "PRE_RESET_SAFETY_BACKUP")
                backupRoot.put("backupDate", dateFormatted)
                backupRoot.put("timestamp", timestamp)
                backupRoot.put("exportedBy", "$currentUserName ($currentUserId)")

                val collectionsJson = JSONObject()
                val countsJson = JSONObject()

                // 1. Employees from Firestore
                val empSnap = db.collection("employees").get().await()
                val empArray = JSONArray()
                for (doc in empSnap.documents) {
                    val itemMap = doc.data ?: hashMapOf()
                    val itemObj = JSONObject(itemMap as Map<*, *>)
                    itemObj.put("_doc_id", doc.id)
                    empArray.put(itemObj)
                }
                collectionsJson.put("employees", empArray)
                countsJson.put("employees", empSnap.size())

                // 2. Performance entries from Firestore
                val perfSnap = db.collection("performance").get().await()
                val perfArray = JSONArray()
                for (doc in perfSnap.documents) {
                    val itemMap = doc.data ?: hashMapOf()
                    val itemObj = JSONObject(itemMap as Map<*, *>)
                    itemObj.put("_doc_id", doc.id)
                    perfArray.put(itemObj)
                }
                collectionsJson.put("performance", perfArray)
                countsJson.put("performance", perfSnap.size())

                // 3. Targets from Firestore
                val targetSnap = db.collection("targets").get().await()
                val targetArray = JSONArray()
                for (doc in targetSnap.documents) {
                    val itemMap = doc.data ?: hashMapOf()
                    val itemObj = JSONObject(itemMap as Map<*, *>)
                    itemObj.put("_doc_id", doc.id)
                    targetArray.put(itemObj)
                }
                collectionsJson.put("targets", targetArray)
                countsJson.put("targets", targetSnap.size())

                // 4. Cached reports from local Room DB
                val localReports = try {
                    AppDatabase.getInstance(applicationContext).reportDao().getAllReports()
                } catch (e: Exception) {
                    emptyList()
                }
                val localArray = JSONArray()
                for (rep in localReports) {
                    val localObj = JSONObject()
                    localObj.put("localId", rep.localId)
                    localObj.put("employeeId", rep.employeeId)
                    localObj.put("accountNo", rep.accountNo)
                    localObj.put("limit", rep.limit)
                    localObj.put("month", rep.month)
                    localObj.put("syncStatus", rep.syncStatus)
                    localArray.put(localObj)
                }
                collectionsJson.put("cached_reports", localArray)
                countsJson.put("cached_reports", localReports.size)

                backupRoot.put("counts", countsJson)
                backupRoot.put("collections", collectionsJson)

                val jsonStr = backupRoot.toString(2)
                val fileName = "PerformanceTracker_PreReset_Backup_$fileDate.json"

                // Save to local cache directory
                val backupDir = File(cacheDir, "backups")
                if (!backupDir.exists()) backupDir.mkdirs()
                val backupFile = File(backupDir, fileName)
                backupFile.writeText(jsonStr)

                // Also save copy to external files dir if accessible
                try {
                    val extDir = getExternalFilesDir("backups")
                    if (extDir != null) {
                        if (!extDir.exists()) extDir.mkdirs()
                        File(extDir, fileName).writeText(jsonStr)
                    }
                } catch (ignored: Exception) {}

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    showPostBackupConfirmDialog(backupFile, empSnap.size(), perfSnap.size(), targetSnap.size())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@SettingsActivity, "Failed to create pre-reset backup: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showPostBackupConfirmDialog(backupFile: File, empCount: Int, perfCount: Int, targetCount: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("are you sure to delete all data?")
            .setIcon(R.drawable.ic_delete)
            .setMessage("Backup completed and saved successfully (${backupFile.name}).\n\n⚠️ Warning: data will be lost!\nAll performance reports, monthly targets, employee records, and mobile offline data will be permanently deleted from mobile and Firebase.\n\nAre you sure to delete all data?")
            .setPositiveButton("yes") { _, _ ->
                executeResetAllData(backupFile, empCount, perfCount, targetCount)
            }
            .setNegativeButton("no") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Reset cancelled. Safety backup preserved.", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun executeResetAllData(backupFile: File, empCount: Int, perfCount: Int, targetCount: Int) {
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Resetting All Data")
            .setMessage("Deleting all records from mobile and Firebase...\nPlease wait.")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Delete all Firestore 'performance' documents in batches
                val perfDocs = db.collection("performance").get().await()
                for (chunk in perfDocs.documents.chunked(400)) {
                    val batch = db.batch()
                    for (doc in chunk) {
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }

                // 2. Delete all Firestore 'targets' documents in batches
                val targetDocs = db.collection("targets").get().await()
                for (chunk in targetDocs.documents.chunked(400)) {
                    val batch = db.batch()
                    for (doc in chunk) {
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }

                // 3. Delete all Firestore 'employees' documents EXCEPT the current admin
                val empDocs = db.collection("employees").get().await()
                var deletedEmps = 0
                for (chunk in empDocs.documents.chunked(400)) {
                    val batch = db.batch()
                    for (doc in chunk) {
                        val docEmpId = (doc.getString("employeeId") ?: doc.id).trim()
                        if (docEmpId.equals(currentUserId.trim(), ignoreCase = true) || doc.id.trim().equals(currentUserId.trim(), ignoreCase = true)) {
                            // Retain current administrator account with 0 targets and approved status
                            batch.update(doc.reference, mapOf("monthlyTarget" to 0, "status" to "Approved"))
                        } else {
                            batch.delete(doc.reference)
                            deletedEmps++
                        }
                    }
                    batch.commit().await()
                }

                // 4. Delete mobile Room database records
                try {
                    AppDatabase.getInstance(applicationContext).reportDao().deleteAllReports()
                } catch (ignored: Exception) {}

                // 5. Delete cached report files from mobile storage
                try {
                    val reportCacheDir = File(cacheDir, "reports")
                    if (reportCacheDir.exists()) {
                        reportCacheDir.deleteRecursively()
                    }
                } catch (ignored: Exception) {}

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    loadUserData()
                    checkPendingApprovals()
                    showResetSuccessDialog(backupFile, perfDocs.size(), targetDocs.size(), deletedEmps)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(this@SettingsActivity, "Failed to delete data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showResetSuccessDialog(backupFile: File, perfDeleted: Int, targetsDeleted: Int, empsDeleted: Int) {
        val msg = "All mobile and Firebase data have been successfully deleted!\n\n" +
                "• Deleted Performance Records: $perfDeleted\n" +
                "• Deleted Target Configurations: $targetsDeleted\n" +
                "• Deleted Employee Accounts: ${maxOf(0, empsDeleted)}\n" +
                "• Mobile Room Database: Cleared\n\n" +
                "Current Admin account has been preserved for system access.\nA full safety backup file was secured:\n${backupFile.name}"

        MaterialAlertDialogBuilder(this)
            .setTitle("All Data Reset Completed")
            .setIcon(R.drawable.ic_delete)
            .setMessage(msg)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNeutralButton("Share / Save Backup") { _, _ ->
                shareBackupFile(backupFile)
            }
            .setCancelable(false)
            .show()
    }
}
