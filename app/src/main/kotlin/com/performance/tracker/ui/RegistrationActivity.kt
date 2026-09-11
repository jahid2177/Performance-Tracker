package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.databinding.ActivityRegistrationBinding
import com.performance.tracker.model.User
import com.performance.tracker.util.ImageUtils
import com.performance.tracker.viewmodel.MainViewModel

class RegistrationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistrationBinding
    private val viewModel: MainViewModel by viewModels()
    private val db = FirebaseFirestore.getInstance()
    private val salesManagersList = mutableListOf<String>()
    private var selectedProfileImageBase64: String = ""

    private val pickMediaLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val base64 = ImageUtils.uriToCompressedBase64(this, uri)
            if (base64 != null) {
                selectedProfileImageBase64 = base64
                ImageUtils.loadProfileImage(base64, binding.ivRegAvatar)
            } else {
                Toast.makeText(this, "Failed to process photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Photo pickers
        val onPhotoClick = View.OnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.cardRegAvatar.setOnClickListener(onPhotoClick)
        binding.cardRegCameraBadge.setOnClickListener(onPhotoClick)

        // Public registration is strictly for standard User/Employee
        binding.layoutUserExtraFields.visibility = View.VISIBLE

        // ডাইনামিকভাবে ফায়ারবেস থেকে সেলস ম্যানেজারদের লিস্ট লোড
        loadSalesManagers()

        binding.etRegManager.setOnClickListener {
            binding.etRegManager.showDropDown()
        }
        binding.etRegManager.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etRegManager.showDropDown()
        }

        binding.btnCompleteReg.setOnClickListener {
            // ইনপুট নেওয়া
            val empId = binding.etRegId.text.toString().trim()
            val name = binding.etRegName.text.toString().trim()
            val email = binding.etRegEmail.text.toString().trim()
            val mobile = binding.etMobile.text.toString().trim()
            val officialNumber = binding.etOfficialNumber.text.toString().trim()
            val password = binding.etRegPassword.text.toString().trim()
            val branch = binding.etRegBranch.text.toString().trim()
            val zone = binding.etRegZone.text.toString().trim()
            val manager = binding.etRegManager.text.toString().trim()

            // ভ্যালিডেশন
            if (empId.length != 12) {
                binding.etRegId.error = "Employee ID must be 12 digits"
                return@setOnClickListener
            }
            if (name.isEmpty() || mobile.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etRegEmail.error = "Valid email address is required for password recovery"
                Toast.makeText(this, "Please provide a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                binding.etRegPassword.error = "Min 6 chars"
                return@setOnClickListener
            }
            if (branch.isEmpty() || zone.isEmpty() || manager.isEmpty()) {
                Toast.makeText(this, "Please provide Branch, Zone & Sales Manager", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newUser = User(
                employeeId = empId,
                name = name,
                email = email,
                branch = branch,
                salesManager = manager,
                mobile = mobile,
                officialNumber = officialNumber,
                zone = zone,
                password = password, 
                role = "USER",
                status = "Pending",
                createdAt = System.currentTimeMillis(),
                profileImage = selectedProfileImageBase64
            )

            // Check if user ID already exists in database
            setLoading(true)
            db.collection("employees").document(empId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        setLoading(false)
                        binding.etRegId.error = "Employee ID already exists"
                        Toast.makeText(this, "An account with Employee ID $empId already exists!", Toast.LENGTH_LONG).show()
                    } else {
                        viewModel.registerUser(newUser)
                    }
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    Toast.makeText(this, "Connection error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        viewModel.userState.observe(this) { user ->
            if (user != null) {
                setLoading(false)
                Toast.makeText(this, "Registration request submitted! Please wait for Admin approval before logging in.", Toast.LENGTH_LONG).show()
                
                // Save credentials for convenient login after approval
                com.performance.tracker.util.SessionManager.saveCredentials(this, user.employeeId, user.password, true)

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

    private fun loadSalesManagers() {
        db.collection("employees")
            .get()
            .addOnSuccessListener { snapshot ->
                salesManagersList.clear()
                val managers = snapshot.documents.filter { doc ->
                    val role = doc.getString("role") ?: ""
                    role.equals("Sales Manager", ignoreCase = true)
                }.mapNotNull { doc ->
                    val name = doc.getString("name")?.trim() ?: ""
                    if (name.isNotEmpty()) name else null
                }.distinct()

                salesManagersList.addAll(managers)

                if (salesManagersList.isNotEmpty()) {
                    val managerAdapter = ArrayAdapter(
                        this@RegistrationActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        salesManagersList
                    )
                    binding.etRegManager.setAdapter(managerAdapter)
                }
            }
            .addOnFailureListener {
                // Ignore silently, allows manual entry
            }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnCompleteReg.isEnabled = !isLoading
        binding.btnCompleteReg.text = if (isLoading) "Processing..." else "REGISTER & LOGIN"
    }
}

