package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.performance.tracker.databinding.ActivityRegistrationBinding
import com.performance.tracker.model.User
import com.performance.tracker.viewmodel.MainViewModel

class RegistrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistrationBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🔥 ১. AGM এবং DGM বাদ দিয়ে স্পিনার আপডেট করা হলো
        val roles = arrayOf("USER", "Sales Manager")
        val roleAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, roles)
        binding.autoCompleteRole.setAdapter(roleAdapter)
        
        // ডিফল্টভাবে "USER" সিলেক্ট করে রাখা
        binding.autoCompleteRole.setText("USER", false)

        // 🔥 ২. রোল অনুযায়ী ফর্মের ফিল্ড Hide/Show করার লজিক
        binding.autoCompleteRole.setOnItemClickListener { _, _, position, _ ->
            val selectedRole = roles[position]
            if (selectedRole == "USER") {
                binding.layoutUserExtraFields.visibility = View.VISIBLE
            } else {
                // Sales Manager এর জন্য Branch, Zone, Manager ফিল্ড হাইড হবে
                binding.layoutUserExtraFields.visibility = View.GONE
            }
        }

        binding.btnCompleteReg.setOnClickListener {
            // কমন ইনপুট নেওয়া
            val empId = binding.etRegId.text.toString().trim()
            val name = binding.etRegName.text.toString().trim()
            val mobile = binding.etMobile.text.toString().trim()
            val password = binding.etRegPassword.text.toString().trim()
            val selectedRole = binding.autoCompleteRole.text.toString().trim()

            // কমন ভ্যালিডেশন
            if (empId.length != 12) {
                binding.etRegId.error = "Employee ID must be 12 digits"
                return@setOnClickListener
            }
            if (name.isEmpty() || mobile.isEmpty() || password.isEmpty() || selectedRole.isEmpty()) {
                Toast.makeText(this, "Please fill all visible fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                binding.etRegPassword.error = "Min 6 chars"
                return@setOnClickListener
            }

            // ডাইনামিক ফিল্ড লজিক
            var branch = "N/A"
            var zone = "N/A"
            var manager = "N/A"

            if (selectedRole == "USER") {
                branch = binding.etRegBranch.text.toString().trim()
                zone = binding.etRegZone.text.toString().trim()
                manager = binding.etRegManager.text.toString().trim()
                
                if (branch.isEmpty() || zone.isEmpty() || manager.isEmpty()) {
                    Toast.makeText(this, "Please provide Branch, Zone & Manager", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            setLoading(true)

            // 🔥 Approval লজিক: Sales Manager হলে Pending, User হলে Approved
            val accountStatus = if (selectedRole == "USER") "Approved" else "Pending"

            val newUser = User(
                employeeId = empId,
                name = name,
                branch = branch,
                salesManager = manager,
                mobile = mobile,
                zone = zone,
                password = password, 
                role = selectedRole,
                status = accountStatus, // স্ট্যাটাস যুক্ত করা হলো
                createdAt = System.currentTimeMillis()
            )

            viewModel.registerUser(newUser)
        }

        viewModel.userState.observe(this) { user ->
            if (user != null) {
                setLoading(false)
                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()
                
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

        viewModel.errorMsg.observe(this) { error ->
            if (error != null) {
                setLoading(false)
                Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnCompleteReg.isEnabled = !isLoading
        binding.btnCompleteReg.text = if (isLoading) "Processing..." else "REGISTER & LOGIN"
    }
}
