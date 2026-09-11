package com.performance.tracker.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.databinding.ActivityProfileBinding
import com.performance.tracker.databinding.DialogEditProfileBinding
import com.performance.tracker.databinding.DialogSetTargetBinding
import com.performance.tracker.model.Performance
import com.performance.tracker.model.User
import com.performance.tracker.util.ImageUtils
import com.performance.tracker.util.SessionManager
import com.performance.tracker.util.TargetUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private val db = FirebaseFirestore.getInstance()
    private var currentUser: User? = null
    private var targetEmployeeId: String = ""

    private var activeDialogBinding: DialogEditProfileBinding? = null
    private var dialogSelectedImageBase64: String? = null

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val base64 = ImageUtils.uriToCompressedBase64(this, uri)
            if (base64 != null) {
                if (activeDialogBinding != null) {
                    dialogSelectedImageBase64 = base64
                    ImageUtils.loadProfileImage(base64, activeDialogBinding!!.ivDialogAvatar)
                } else {
                    saveProfileImage(base64)
                }
            } else {
                Toast.makeText(this, "Failed to process photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        targetEmployeeId = intent.getStringExtra("USER_ID") ?: ""
        if (targetEmployeeId.isEmpty()) {
            targetEmployeeId = SessionManager.getUserId(this)
        }

        if (targetEmployeeId.isNotEmpty()) {
            loadProfileData(targetEmployeeId)
        } else {
            Toast.makeText(this, "User ID Missing!", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnLogout.setOnClickListener {
            SessionManager.clear(this)
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun loadProfileData(id: String) {
        db.collection("employees").document(id).get()
            .addOnSuccessListener { document ->
                val user = if (document.exists()) {
                    document.toObject(User::class.java)?.let {
                        if (it.employeeId.isEmpty()) it.copy(employeeId = document.id) else it
                    }
                } else null

                if (user != null) {
                    currentUser = user
                    targetEmployeeId = user.employeeId.ifEmpty { id }
                    updateProfileUI(user)
                } else {
                    // Fallback for Admin or missing document in employees collection
                    val loggedInId = SessionManager.getUserId(this)
                    val loggedInRole = SessionManager.getUserRole(this)
                    val loggedInName = SessionManager.getUserName(this)
                    val fallbackUser = User(
                        employeeId = id,
                        name = if (id == loggedInId && loggedInName.isNotEmpty()) loggedInName else if (loggedInRole.equals("ADMIN", ignoreCase = true)) "Administrator" else "User",
                        role = if (id == loggedInId) loggedInRole else if (loggedInRole.equals("ADMIN", ignoreCase = true)) "ADMIN" else "USER",
                        status = "Approved"
                    )
                    currentUser = fallbackUser
                    targetEmployeeId = id
                    updateProfileUI(fallbackUser)
                }
            }
            .addOnFailureListener {
                val loggedInId = SessionManager.getUserId(this)
                val loggedInRole = SessionManager.getUserRole(this)
                val loggedInName = SessionManager.getUserName(this)
                val fallbackUser = User(
                    employeeId = id,
                    name = if (id == loggedInId && loggedInName.isNotEmpty()) loggedInName else if (loggedInRole.equals("ADMIN", ignoreCase = true)) "Administrator" else "User",
                    role = if (id == loggedInId) loggedInRole else if (loggedInRole.equals("ADMIN", ignoreCase = true)) "ADMIN" else "USER",
                    status = "Approved"
                )
                currentUser = fallbackUser
                targetEmployeeId = id
                updateProfileUI(fallbackUser)
            }
    }

    private fun updateProfileUI(user: User) {
        binding.tvProfileName.text = user.name
        binding.tvProfileId.text = "ID: ${user.employeeId.ifEmpty { targetEmployeeId }}"
        binding.tvProfileRole.text = "Role: ${user.role}"
        binding.tvProfileStatus.text = user.status.ifEmpty { "Approved" }

        // Avatar Image
        ImageUtils.loadProfileImage(user.profileImage, binding.ivProfileAvatar)

        if (user.status.equals("Approved", ignoreCase = true)) {
            binding.tvProfileStatus.setBackgroundResource(R.drawable.bg_badge_green)
            binding.tvProfileStatus.setTextColor(getColor(R.color.badge_green_icon))
        } else {
            binding.tvProfileStatus.setBackgroundResource(R.drawable.bg_badge_amber)
            binding.tvProfileStatus.setTextColor(getColor(R.color.badge_amber_icon))
        }

        binding.tvProfileMobile.text = user.mobile.ifEmpty { "N/A" }
        binding.tvProfileOfficialNumber.text = user.officialNumber.ifEmpty { "N/A" }
        binding.tvProfileBranch.text = user.branch.ifEmpty { "N/A" }
        binding.tvProfileZone.text = user.zone.ifEmpty { "N/A" }
        binding.tvProfileManager.text = user.salesManager.ifEmpty { "N/A" }

        // Target & Achievement Progress Display
        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
        db.collection("performance")
            .whereEqualTo("employeeId", user.employeeId.ifEmpty { targetEmployeeId })
            .get()
            .addOnSuccessListener { snapshot ->
                val performances = snapshot.toObjects(Performance::class.java)
                val target = user.monthlyTarget
                val achieved = TargetUtils.countCards(performances, user.employeeId.ifEmpty { targetEmployeeId }, currentMonth)
                val rate = TargetUtils.calculateAchievementRate(achieved, target)

                binding.tvProfileTargetCount.text = "$target Cards ($currentMonth)"
                binding.tvProfileAchievedCount.text = "$achieved Cards"
                binding.tvProfileAchievementBadge.text = TargetUtils.formatAchievementRate(achieved, target)

                val color = TargetUtils.getAchievementColor(achieved, target)
                binding.tvProfileAchievementBadge.setTextColor(color)

                val progress = rate.toInt().coerceIn(0, 100)
                binding.progressProfileTarget.progress = progress
                binding.progressProfileTarget.setIndicatorColor(color)
            }

        // Role-Based Permission Check
        val loggedInId = SessionManager.getUserId(this)
        val loggedInRole = SessionManager.getUserRole(this)

        val isAdmin = loggedInRole.equals("ADMIN", ignoreCase = true) || user.role.equals("ADMIN", ignoreCase = true)
        val isOwnProfile = (loggedInId.isNotEmpty() && (loggedInId == user.employeeId || loggedInId == targetEmployeeId))

        // Target setting is strictly for ADMIN
        if (isAdmin) {
            binding.btnProfileSetTarget.visibility = View.VISIBLE
            binding.btnProfileSetTarget.setOnClickListener {
                showSetTargetDialog(user)
            }
        } else {
            binding.btnProfileSetTarget.visibility = View.GONE
        }

        // Rule 1: User can only edit own profile
        // Rule 2: Sales Manager can only edit own profile
        // Rule 3: Admin can edit own profile and everyone else's profile
        val canEdit = isAdmin || isOwnProfile

        binding.btnEditProfile.visibility = if (canEdit) View.VISIBLE else View.GONE
        binding.btnLogout.visibility = if (isOwnProfile) View.VISIBLE else View.GONE

        if (canEdit) {
            binding.cardEditPhotoBadge.visibility = View.VISIBLE
            binding.cardProfileAvatar.isClickable = true

            val onPhotoClick = View.OnClickListener {
                showPhotoOptionsDialog(user)
            }
            binding.cardProfileAvatar.setOnClickListener(onPhotoClick)
            binding.cardEditPhotoBadge.setOnClickListener(onPhotoClick)

            binding.btnEditProfile.setOnClickListener {
                showEditProfileDialog(user, isAdmin)
            }
        } else {
            binding.cardEditPhotoBadge.visibility = View.GONE
            binding.cardProfileAvatar.isClickable = false
        }
    }

    private fun showPhotoOptionsDialog(user: User) {
        val options = if (user.profileImage.isNotEmpty()) {
            arrayOf("Upload New Photo", "Remove Photo")
        } else {
            arrayOf("Upload Photo")
        }

        AlertDialog.Builder(this)
            .setTitle("Profile Photo")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Upload New Photo", "Upload Photo" -> {
                        activeDialogBinding = null
                        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    "Remove Photo" -> {
                        removeProfileImage()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveProfileImage(base64: String) {
        val user = currentUser ?: return
        val docId = if (user.employeeId.isNotEmpty()) user.employeeId else targetEmployeeId
        if (docId.isEmpty()) return

        db.collection("employees").document(docId)
            .set(mapOf("profileImage" to base64), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                currentUser = user.copy(profileImage = base64)
                ImageUtils.loadProfileImage(base64, binding.ivProfileAvatar)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeProfileImage() {
        val user = currentUser ?: return
        val docId = if (user.employeeId.isNotEmpty()) user.employeeId else targetEmployeeId
        if (docId.isEmpty()) return

        db.collection("employees").document(docId)
            .set(mapOf("profileImage" to ""), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profile photo removed", Toast.LENGTH_SHORT).show()
                currentUser = user.copy(profileImage = "")
                ImageUtils.loadProfileImage("", binding.ivProfileAvatar)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to remove photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditProfileDialog(user: User, isAdmin: Boolean) {
        val dialogBinding = DialogEditProfileBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        activeDialogBinding = dialogBinding
        dialogSelectedImageBase64 = user.profileImage

        // Load Avatar in dialog
        ImageUtils.loadProfileImage(user.profileImage, dialogBinding.ivDialogAvatar)
        dialogBinding.btnDialogChangePhoto.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Pre-fill existing data
        dialogBinding.etEditProfileName.setText(user.name)
        dialogBinding.etEditProfileMobile.setText(user.mobile)
        dialogBinding.etEditProfileOfficialNumber.setText(user.officialNumber)
        dialogBinding.etEditProfileBranch.setText(user.branch)
        dialogBinding.etEditProfileZone.setText(user.zone)
        dialogBinding.etEditProfileManager.setText(user.salesManager)
        dialogBinding.etEditProfilePassword.setText(user.password)

        // Dynamic Sales Managers dropdown
        db.collection("employees").get()
            .addOnSuccessListener { snap ->
                val managers = snap.documents.filter { doc ->
                    doc.getString("role")?.equals("Sales Manager", ignoreCase = true) == true
                }.mapNotNull { it.getString("name")?.trim() }.filter { it.isNotEmpty() }.distinct()

                if (managers.isNotEmpty()) {
                    val mgrAdapter = ArrayAdapter(this@ProfileActivity, android.R.layout.simple_dropdown_item_1line, managers)
                    dialogBinding.etEditProfileManager.setAdapter(mgrAdapter)
                }
            }
            .addOnFailureListener {
                // Ignore silently
            }

        dialogBinding.etEditProfileManager.setOnClickListener {
            dialogBinding.etEditProfileManager.showDropDown()
        }
        dialogBinding.etEditProfileManager.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) dialogBinding.etEditProfileManager.showDropDown()
        }

        val loggedInId = SessionManager.getUserId(this)
        val isOwnProfile = (loggedInId.isNotEmpty() && (loggedInId == user.employeeId || loggedInId == targetEmployeeId))
        val isEditingOtherUserAsAdmin = isAdmin && !isOwnProfile

        val roleList = listOf("USER", "Sales Manager", "ADMIN", "AGM", "DGM")
        val statusList = listOf("Approved", "Pending")

        if (isEditingOtherUserAsAdmin) {
            dialogBinding.layoutAdminRole.visibility = View.VISIBLE
            dialogBinding.layoutAdminStatus.visibility = View.VISIBLE

            val roleAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roleList)
            dialogBinding.spEditProfileRole.adapter = roleAdapter
            val currentRoleIdx = roleList.indexOfFirst { it.equals(user.role, ignoreCase = true) }
            if (currentRoleIdx >= 0) dialogBinding.spEditProfileRole.setSelection(currentRoleIdx)

            val statusAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statusList)
            dialogBinding.spEditProfileStatus.adapter = statusAdapter
            val currentStatusIdx = statusList.indexOfFirst { it.equals(user.status, ignoreCase = true) }
            if (currentStatusIdx >= 0) dialogBinding.spEditProfileStatus.setSelection(currentStatusIdx)
        } else {
            dialogBinding.layoutAdminRole.visibility = View.GONE
            dialogBinding.layoutAdminStatus.visibility = View.GONE

            if (user.role.equals("Sales Manager", ignoreCase = true) || user.role.equals("ADMIN", ignoreCase = true)) {
                dialogBinding.layoutEditProfileManager.visibility = View.GONE
            }
        }

        dialog.setOnDismissListener {
            activeDialogBinding = null
        }

        dialogBinding.btnCancelEditProfile.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveEditProfile.setOnClickListener {
            val newName = dialogBinding.etEditProfileName.text.toString().trim()
            val newMobile = dialogBinding.etEditProfileMobile.text.toString().trim()
            val newOfficialNumber = dialogBinding.etEditProfileOfficialNumber.text.toString().trim()
            val newBranch = dialogBinding.etEditProfileBranch.text.toString().trim()
            val newZone = dialogBinding.etEditProfileZone.text.toString().trim()
            val newManager = dialogBinding.etEditProfileManager.text.toString().trim()
            val newPassword = dialogBinding.etEditProfilePassword.text.toString().trim()
            val finalImage = dialogSelectedImageBase64 ?: user.profileImage

            if (newName.isEmpty()) {
                dialogBinding.etEditProfileName.error = "Name is required"
                return@setOnClickListener
            }

            val finalPassword = if (newPassword.isNotEmpty()) newPassword else user.password

            dialogBinding.btnSaveEditProfile.isEnabled = false
            dialogBinding.btnSaveEditProfile.text = "Saving..."

            val docId = if (user.employeeId.isNotEmpty()) user.employeeId else targetEmployeeId
            if (docId.isEmpty()) {
                dialogBinding.btnSaveEditProfile.isEnabled = true
                dialogBinding.btnSaveEditProfile.text = "Update"
                Toast.makeText(this, "Employee ID is missing", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updates = hashMapOf<String, Any>(
                "employeeId" to docId,
                "name" to newName,
                "mobile" to newMobile,
                "officialNumber" to newOfficialNumber,
                "branch" to newBranch,
                "zone" to newZone,
                "salesManager" to newManager,
                "password" to finalPassword,
                "profileImage" to finalImage
            )

            var finalRole = user.role.ifEmpty { if (isAdmin) "ADMIN" else "USER" }
            if (isEditingOtherUserAsAdmin) {
                finalRole = dialogBinding.spEditProfileRole.selectedItem.toString()
                val finalStatus = dialogBinding.spEditProfileStatus.selectedItem.toString()
                updates["role"] = finalRole
                updates["status"] = finalStatus
            } else {
                updates["role"] = finalRole
                updates["status"] = user.status.ifEmpty { "Approved" }
            }

            db.collection("employees").document(docId).set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    dialog.dismiss()
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()

                    // If user edited own profile, update session as well
                    if (loggedInId == docId || isOwnProfile) {
                        SessionManager.saveUser(this, docId, finalRole, newName)
                    }

                    // Reload UI
                    loadProfileData(docId)
                }
                .addOnFailureListener { e ->
                    dialogBinding.btnSaveEditProfile.isEnabled = true
                    dialogBinding.btnSaveEditProfile.text = "Update"
                    Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showSetTargetDialog(user: User) {
        val targetBinding = DialogSetTargetBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(targetBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        targetBinding.tvSetTargetEmployee.text = "${user.name} (ID: ${user.employeeId.ifEmpty { targetEmployeeId }})"

        val monthsList = listOf(
            "Monthly Target (All Months)", "January", "February", "March", "April",
            "May", "June", "July", "August", "September", "October", "November", "December"
        )
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monthsList)
        targetBinding.spinnerTargetMonth.adapter = spinnerAdapter

        val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
        val currentMonthIdx = monthsList.indexOfFirst { it.equals(currentMonth, ignoreCase = true) }
        if (currentMonthIdx >= 0) {
            targetBinding.spinnerTargetMonth.setSelection(currentMonthIdx)
        }

        if (user.monthlyTarget > 0) {
            targetBinding.etTargetCards.setText(user.monthlyTarget.toString())
        }

        targetBinding.btnCancelTarget.setOnClickListener { dialog.dismiss() }

        targetBinding.btnSaveTarget.setOnClickListener {
            val targetStr = targetBinding.etTargetCards.text.toString().trim()
            val targetVal = targetStr.toIntOrNull()
            if (targetVal == null || targetVal < 0) {
                targetBinding.tilTargetCards.error = "Please enter a valid target count"
                return@setOnClickListener
            }

            targetBinding.tilTargetCards.error = null
            val selectedPeriod = monthsList[targetBinding.spinnerTargetMonth.selectedItemPosition]
            val monthToSave = if (selectedPeriod.startsWith("Monthly Target")) "General" else selectedPeriod
            val adminName = SessionManager.getUserName(this).ifBlank { "Admin" }

            targetBinding.btnSaveTarget.isEnabled = false
            TargetUtils.saveTarget(
                employeeId = user.employeeId.ifEmpty { targetEmployeeId },
                employeeName = user.name,
                month = monthToSave,
                targetCards = targetVal,
                adminName = adminName,
                onSuccess = {
                    Toast.makeText(this, "Target set to $targetVal cards for ${user.name}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadProfileData(user.employeeId.ifEmpty { targetEmployeeId })
                },
                onFailure = { err ->
                    targetBinding.btnSaveTarget.isEnabled = true
                    Toast.makeText(this, "Failed to save target: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        dialog.show()
    }
}
