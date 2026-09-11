package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.performance.tracker.databinding.ActivityLoginBinding
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

        // Load saved credentials if Remember Me is enabled
        val isRemember = com.performance.tracker.util.SessionManager.isRememberMe(this)
        binding.cbRememberMe.isChecked = isRemember
        if (isRemember) {
            val savedId = com.performance.tracker.util.SessionManager.getSavedId(this)
            val savedPass = com.performance.tracker.util.SessionManager.getSavedPassword(this)
            if (savedId.isNotEmpty()) {
                binding.etId.setText(savedId)
            }
            if (savedPass.isNotEmpty()) {
                binding.etPassword.setText(savedPass)
            }
        }

        binding.btnLogin.setOnClickListener {
            val id = binding.etId.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (id.isEmpty()) {
                binding.etId.error = "ID Required"
                return@setOnClickListener
            }
            if (pass.isEmpty()) {
                binding.etPassword.error = "Password Required"
                return@setOnClickListener
            }

            isChecking = true
            setLoading(true) 
            
            viewModel.checkUser(id)
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
                    
                    // 🔥 ১. স্ট্যাটাস চেক করা (Admin হলে কখনোই ব্লক করবে না)
                    if (user.status == "Pending" && !user.role.equals("ADMIN", ignoreCase = true)) {
                        setLoading(false)
                        isChecking = false
                        Toast.makeText(this, "Your account is waiting for Admin approval.", Toast.LENGTH_LONG).show()
                        return@observe // এখানেই লগইন প্রসেস থামিয়ে দিবে
                    }

                    // 🔥 ২. স্ট্যাটাস Approved হলে বা Admin হলে ড্যাশবোর্ডে যেতে দিবে
                    setLoading(false)
                    isChecking = false

                    // Ensure user ID is not empty
                    val effectiveId = if (user.employeeId.isNotEmpty()) user.employeeId else inputId

                    // Save or clear remembered credentials based on checkbox
                    if (binding.cbRememberMe.isChecked) {
                        com.performance.tracker.util.SessionManager.saveCredentials(this, effectiveId, inputPass, true)
                    } else {
                        com.performance.tracker.util.SessionManager.clearCredentials(this)
                    }

                    // Save session
                    com.performance.tracker.util.SessionManager.saveUser(this, effectiveId, user.role, user.name)

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

    private fun setLoading(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "Verifying..." else "LOGIN"
    }
}
