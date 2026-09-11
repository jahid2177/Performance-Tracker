package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.databinding.ActivityLoginBinding
import com.performance.tracker.model.User
import com.performance.tracker.util.BiometricHelper
import com.performance.tracker.util.SessionManager
import com.performance.tracker.util.UpdateManager
import com.performance.tracker.viewmodel.MainViewModel

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: MainViewModel by viewModels()

    private var isChecking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Check for in-app updates via Firebase Remote Config
        UpdateManager.checkForAppUpdate(this)

        // 2. Load saved credentials if Remember Me is enabled
        val isRemember = SessionManager.isRememberMe(this)
        binding.cbRememberMe.isChecked = isRemember
        val savedId = SessionManager.getSavedId(this)
        val savedPass = SessionManager.getSavedPassword(this)
        if (isRemember) {
            if (savedId.isNotEmpty()) {
                binding.etId.setText(savedId)
            }
            if (savedPass.isNotEmpty()) {
                binding.etPassword.setText(savedPass)
            }
        }

        binding.btnLogin.setOnClickListener {
            performPasswordLogin()
        }

        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.btnBiometricLogin.setOnClickListener {
            performBiometricLogin()
        }

        binding.tvRegisterLink.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }

        viewModel.userState.observe(this) { user ->
            if (!isChecking) return@observe

            val inputId = binding.etId.text.toString().trim()
            val inputPass = binding.etPassword.text.toString().trim()

            if (user != null) {
                if (user.password == inputPass) {
                    
                    // 🔥 ১. স্ট্যাটাস চেক করা (Admin ব্যতীত অন্যদের অবশ্যই Approved হতে হবে)
                    val isApproved = user.status.equals("Approved", ignoreCase = true)
                    val isAdmin = user.role.equals("ADMIN", ignoreCase = true)

                    if (!isAdmin && !isApproved) {
                        setLoading(false)
                        isChecking = false
                        Toast.makeText(this, "Your account is pending Admin approval. Please contact Admin.", Toast.LENGTH_LONG).show()
                        return@observe // Login blocked until approved
                    }

                    // 🔥 ২. স্ট্যাটাস Approved হলে বা Admin হলে ড্যাশবোর্ডে যেতে দিবে
                    setLoading(false)
                    isChecking = false

                    // Ensure user ID is not empty
                    val effectiveId = if (user.employeeId.isNotEmpty()) user.employeeId else inputId

                    // Save or clear remembered credentials based on checkbox
                    if (binding.cbRememberMe.isChecked) {
                        SessionManager.saveCredentials(this, effectiveId, inputPass, true)
                    } else {
                        SessionManager.clearCredentials(this)
                    }

                    // Save session with email
                    SessionManager.saveUser(this, effectiveId, user.role, user.name, user.email)

                    navigateToDashboard(user, effectiveId)
                } else {
                    setLoading(false)
                    isChecking = false
                    Toast.makeText(this, "Incorrect Password!", Toast.LENGTH_SHORT).show()
                    binding.etPassword.error = "Wrong Password"
                }
            } else {
                setLoading(false)
                isChecking = false
                Toast.makeText(this, "Account not found! Please register.", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.errorMsg.observe(this) { error ->
            if (error != null && isChecking) {
                setLoading(false)
                isChecking = false
                Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performPasswordLogin() {
        val id = binding.etId.text.toString().trim()
        val pass = binding.etPassword.text.toString().trim()

        if (id.isEmpty()) {
            binding.etId.error = "ID Required"
            return
        }
        if (pass.isEmpty()) {
            binding.etPassword.error = "Password Required"
            return
        }

        isChecking = true
        setLoading(true)
        viewModel.checkUser(id)
    }

    private fun performBiometricLogin() {
        if (!BiometricHelper.isBiometricAvailable(this)) {
            Toast.makeText(this, "Biometric authentication is not supported or set up on this device.", Toast.LENGTH_SHORT).show()
            return
        }

        val savedId = SessionManager.getSavedId(this).ifBlank { binding.etId.text.toString().trim() }
        val savedPass = SessionManager.getSavedPassword(this)

        if (savedId.isBlank()) {
            Toast.makeText(this, "Please enter your User ID or log in once with password.", Toast.LENGTH_LONG).show()
            return
        }

        BiometricHelper.authenticate(
            activity = this,
            title = "Biometric Login",
            subtitle = "Authenticate with Fingerprint/Face to access Tracker",
            negativeButtonText = "Use Password",
            onSuccess = {
                setLoading(true)
                FirebaseFirestore.getInstance().collection("employees")
                    .whereEqualTo("employeeId", savedId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        setLoading(false)
                        if (!snapshot.isEmpty) {
                            val doc = snapshot.documents.first()
                            val user = doc.toObject(User::class.java)
                            if (user != null) {
                                val isApproved = user.status.equals("Approved", ignoreCase = true)
                                val isAdmin = user.role.equals("ADMIN", ignoreCase = true)

                                if (!isAdmin && !isApproved) {
                                    Toast.makeText(this, "Your account is pending Admin approval. Please contact Admin.", Toast.LENGTH_LONG).show()
                                    return@addOnSuccessListener
                                }
                                SessionManager.saveUser(this, user.employeeId, user.role, user.name, user.email)
                                if (savedPass.isNotBlank()) {
                                    SessionManager.saveCredentials(this, user.employeeId, savedPass, true)
                                }
                                Toast.makeText(this, "Biometric Login Successful! Welcome ${user.name}", Toast.LENGTH_SHORT).show()
                                navigateToDashboard(user, user.employeeId)
                            } else {
                                Toast.makeText(this, "Account data error.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Account with ID '$savedId' not found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        Toast.makeText(this, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            },
            onError = { errMsg ->
                Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showForgotPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
        val tilForgotId = dialogView.findViewById<TextInputLayout>(R.id.tilForgotId)
        val etForgotId = dialogView.findViewById<TextInputEditText>(R.id.etForgotEmployeeId)
        val tilForgotEmail = dialogView.findViewById<TextInputLayout>(R.id.tilForgotEmail)
        val etForgotEmail = dialogView.findViewById<TextInputEditText>(R.id.etForgotEmail)
        val btnVerify = dialogView.findViewById<Button>(R.id.btnVerifyForgot)

        val layoutStep1 = dialogView.findViewById<LinearLayout>(R.id.layoutStep1Verify)
        val layoutStep2 = dialogView.findViewById<LinearLayout>(R.id.layoutStep2NewPass)
        val tvVerifiedUser = dialogView.findViewById<TextView>(R.id.tvVerifiedUser)
        val etNewPass = dialogView.findViewById<TextInputEditText>(R.id.etForgotNewPassword)
        val etConfirmPass = dialogView.findViewById<TextInputEditText>(R.id.etForgotConfirmPassword)
        val btnSubmitNewPass = dialogView.findViewById<Button>(R.id.btnSubmitNewPassword)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelForgot)

        // Pre-fill ID if typed in login screen
        val currentId = binding.etId.text.toString().trim()
        if (currentId.isNotBlank()) {
            etForgotId.setText(currentId)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var matchedDocId = ""
        var verifiedEmpId = ""

        btnVerify.setOnClickListener {
            val empId = etForgotId.text.toString().trim()
            val email = etForgotEmail.text.toString().trim()

            if (empId.isEmpty()) {
                tilForgotId.error = "Employee ID is required"
                return@setOnClickListener
            } else {
                tilForgotId.error = null
            }

            if (email.isEmpty()) {
                tilForgotEmail.error = "Email address is required"
                return@setOnClickListener
            } else {
                tilForgotEmail.error = null
            }

            btnVerify.isEnabled = false
            btnVerify.text = "Verifying..."

            FirebaseFirestore.getInstance().collection("employees")
                .whereEqualTo("employeeId", empId)
                .get()
                .addOnSuccessListener { snapshot ->
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify Identity"

                    if (snapshot.isEmpty) {
                        tilForgotId.error = "Employee ID not found"
                        return@addOnSuccessListener
                    }

                    val doc = snapshot.documents.first()
                    val user = doc.toObject(User::class.java)
                    val registeredEmail = user?.email?.trim() ?: ""

                    if (registeredEmail.isBlank()) {
                        Toast.makeText(this, "No registered email found for this ID. Please contact Admin/AGM.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    if (!registeredEmail.equals(email, ignoreCase = true)) {
                        tilForgotEmail.error = "Email does not match our records"
                        return@addOnSuccessListener
                    }

                    // Verified! Switch to Step 2
                    matchedDocId = doc.id
                    verifiedEmpId = empId
                    layoutStep1.visibility = View.GONE
                    layoutStep2.visibility = View.VISIBLE
                    tvVerifiedUser.text = "Identity Verified: ${user?.name} (${user?.role})"
                }
                .addOnFailureListener { e ->
                    btnVerify.isEnabled = true
                    btnVerify.text = "Verify Identity"
                    Toast.makeText(this, "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnSubmitNewPass.setOnClickListener {
            val newPass = etNewPass.text.toString().trim()
            val confirmPass = etConfirmPass.text.toString().trim()

            if (newPass.length < 6) {
                etNewPass.error = "Minimum 6 characters required"
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                etConfirmPass.error = "Passwords do not match"
                return@setOnClickListener
            }

            btnSubmitNewPass.isEnabled = false
            btnSubmitNewPass.text = "Updating Password..."

            FirebaseFirestore.getInstance().collection("employees").document(matchedDocId)
                .update("password", newPass)
                .addOnSuccessListener {
                    Toast.makeText(this, "Password reset successfully! Please login with your new password.", Toast.LENGTH_LONG).show()
                    binding.etId.setText(verifiedEmpId)
                    binding.etPassword.setText(newPass)
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    btnSubmitNewPass.isEnabled = true
                    btnSubmitNewPass.text = "Save New Password"
                    Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun navigateToDashboard(user: User, effectiveId: String) {
        if (user.role == "ADMIN" || user.role == "AGM" || user.role == "DGM" || user.role == "Sales Manager") {
            val intent = Intent(this, AdminDashboardActivity::class.java)
            intent.putExtra("USER_ROLE", user.role) 
            intent.putExtra("USER_NAME", user.name) 
            intent.putExtra("USER_ID", effectiveId)
            startActivity(intent)
        } else {
            val intent = Intent(this, UserDashboardActivity::class.java)
            intent.putExtra("USER_ID", effectiveId)
            intent.putExtra("USER_NAME", user.name)
            startActivity(intent)
        }
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "Verifying..." else "LOGIN"
    }
}
