package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.databinding.ActivitySettingsBinding
import com.performance.tracker.model.User
import com.performance.tracker.util.ImageUtils
import com.performance.tracker.util.SessionManager

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
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun updateAdminSectionVisibility() {
        val isAdmin = currentUserRole.equals("ADMIN", ignoreCase = true)
        binding.layoutAdminSection.visibility = if (isAdmin) android.view.View.VISIBLE else android.view.View.GONE
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

        // 3. Admin: Create User option (Sales Manager, AGM, DGM)
        binding.cardSettingsCreateUser.setOnClickListener {
            showCreateUserDialog()
        }

        // 4. About Application
        binding.cardSettingsAbout.setOnClickListener {
            showAboutDialog()
        }

        // 5. Help & Support
        binding.cardSettingsSupport.setOnClickListener {
            showSupportDialog()
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
        MaterialAlertDialogBuilder(this)
            .setTitle("About Application")
            .setMessage("Performance Tracker System\n\nVersion: 1.2\nBuild: 2026.1\n\nDesigned for tracking performance metrics, monthly reviews, team reports, and evaluation analytics across all regional branches and sales teams.")
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showSupportDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Help & Support")
            .setMessage("For system assistance, approval requests, or technical issues, please contact your System Administrator or IT Support:\n\n• Helpline: +880 1915-519710\n• Official Support: support@performancetracker.com\n• Support Hours: 9:00 AM - 6:00 PM (Sun - Thu)")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
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
}
