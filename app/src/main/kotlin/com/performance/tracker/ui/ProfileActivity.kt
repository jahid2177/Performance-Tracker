package com.performance.tracker.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.databinding.ActivityProfileBinding
import com.performance.tracker.model.User

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // intent থেকে ID রিসিভ করা (যেটির প্রোফাইল দেখা হচ্ছে)
        val employeeId = intent.getStringExtra("USER_ID") ?: ""

        if (employeeId.isNotEmpty()) {
            loadProfileData(employeeId)
        } else {
            Toast.makeText(this, "User ID Missing!", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnLogout.setOnClickListener {
            // আপনার Logout লজিক এখানে হবে
            Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadProfileData(id: String) {
        db.collection("employees").document(id).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                user?.let {
                    binding.tvProfileName.text = it.name
                    binding.tvProfileId.text = "ID: ${it.employeeId}"
                    binding.tvProfileRole.text = "Role: ${it.role}"
                    binding.tvProfileMobile.text = it.mobile
                    binding.tvProfileBranch.text = it.branch
                    binding.tvProfileZone.text = it.zone
                    binding.tvProfileManager.text = it.salesManager
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile data", Toast.LENGTH_SHORT).show()
            }
    }
}
