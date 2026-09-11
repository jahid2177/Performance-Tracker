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

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun loadUserData() {
        if (currentUserId.isEmpty()) {
            binding.tvSettingsName.text = currentUserName.ifEmpty { "User" }
            binding.tvSettingsEmpId.text = "Role: $currentUserRole"
            binding.tvSettingsRoleBadge.text = "Role: $currentUserRole"
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

                    // Update session name if changed
                    if (user.name.isNotEmpty()) {
                        currentUserName = user.name
                        currentUserRole = user.role
                    }
                } else {
                    binding.tvSettingsName.text = currentUserName.ifEmpty { "User" }
                    binding.tvSettingsEmpId.text = "ID: $currentUserId"
                    binding.tvSettingsRoleBadge.text = "Role: $currentUserRole"
                }
            }
            .addOnFailureListener {
                binding.tvSettingsName.text = currentUserName.ifEmpty { "User" }
                binding.tvSettingsEmpId.text = "ID: $currentUserId"
                binding.tvSettingsRoleBadge.text = "Role: $currentUserRole"
            }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
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

        // 3. About Application
        binding.cardSettingsAbout.setOnClickListener {
            showAboutDialog()
        }

        // 4. Help & Support
        binding.cardSettingsSupport.setOnClickListener {
            showSupportDialog()
        }

        // 5. Exit / Logout option
        binding.cardSettingsExit.setOnClickListener {
            showLogoutConfirmationDialog()
        }
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

                db.collection("employees").document(currentUserId)
                    .update("password", newPass)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to update password: ${e.message}", Toast.LENGTH_SHORT).show()
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
