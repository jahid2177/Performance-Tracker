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

        // 🔥 ৩. ডাইনামিকভাবে ফায়ারবেস থেকে সেলস ম্যানেজারদের লিস্ট লোড
        loadSalesManagers()

        binding.etRegManager.setOnClickListener {
            binding.etRegManager.showDropDown()
        }
        binding.etRegManager.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.etRegManager.showDropDown()
        }

        binding.btnCompleteReg.setOnClickListener {
            // কমন ইনপুট নেওয়া
            val empId = binding.etRegId.text.toString().trim()
            val name = binding.etRegName.text.toString().trim()
            val mobile = binding.etMobile.text.toString().trim()
            val officialNumber = binding.etOfficialNumber.text.toString().trim()
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
                officialNumber = officialNumber,
                zone = zone,
                password = password, 
                role = selectedRole,
                status = accountStatus,
                createdAt = System.currentTimeMillis(),
                profileImage = selectedProfileImageBase64
            )

            viewModel.registerUser(newUser)
        }

        viewModel.userState.observe(this) { user ->
            if (user != null) {
                setLoading(false)
                Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()
                
                // Save credentials so Login page already has them filled
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
