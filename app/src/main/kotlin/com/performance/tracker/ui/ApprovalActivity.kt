package com.performance.tracker.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.adapter.UserAdapter
import com.performance.tracker.databinding.ActivityApprovalBinding
import com.performance.tracker.model.User

class ApprovalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityApprovalBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: UserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApprovalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // অ্যাডাপ্টার সেটআপ
        adapter = UserAdapter(
            list = emptyList(), 
            isApprovalMode = true,
            onCardClick = null, 
            onActionClick = { user, action ->
                // 🔥 স্ট্যাটাস কে "Approved" (ক্যাপিটালাইজড বা আপনার মডেলে যেমন আছে) হিসেবে আপডেট করবে
                if (action == "APPROVE") updateUserStatus(user.employeeId, "Approved")
                else if (action == "DELETE") deleteUser(user.employeeId)
            }
        )
        
        binding.recyclerApproval.layoutManager = LinearLayoutManager(this)
        binding.recyclerApproval.adapter = adapter

        fetchPendingUsers()
    }

    private fun fetchPendingUsers() {
        binding.progressBar.visibility = View.VISIBLE
        
        // 🔥 "role", "USER" ফিল্টারটি বাদ দেওয়া হয়েছে। এখন শুধুমাত্র "status" == "Pending" খুঁজবে।
        // মনে রাখবেন, RegistrationActivity তে আমরা status কে "Pending" (Capital P) হিসেবে সেভ করেছি।
        db.collection("employees")
            .whereEqualTo("status", "Pending") 
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE
                val users = snapshot.toObjects(User::class.java)
                
                if (users.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "No pending approvals"
                } else {
                    binding.tvEmpty.visibility = View.GONE
                }
                
                adapter.updateList(users)
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error fetching data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUserStatus(id: String, status: String) {
        db.collection("employees").document(id).update("status", status)
            .addOnSuccessListener {
                Toast.makeText(this, "User Approved Successfully!", Toast.LENGTH_SHORT).show()
                fetchPendingUsers() // ডাটা রিলোড করবে
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to approve: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteUser(id: String) {
        db.collection("employees").document(id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "User Rejected & Deleted!", Toast.LENGTH_SHORT).show()
                fetchPendingUsers() // ডাটা রিলোড করবে
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to delete: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
