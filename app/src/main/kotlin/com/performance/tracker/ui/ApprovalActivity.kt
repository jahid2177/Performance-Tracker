package com.performance.tracker.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
                if (action == "APPROVE") {
                    updateUserStatus(user.employeeId, "Approved")
                } else if (action == "DELETE") {
                    confirmDeletePendingUser(user)
                }
            }
        )
        
        binding.recyclerApproval.layoutManager = LinearLayoutManager(this)
        binding.recyclerApproval.adapter = adapter

        fetchPendingUsers()
    }

    private fun fetchPendingUsers() {
        binding.progressBar.visibility = View.VISIBLE
        
        db.collection("employees")
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE
                val users = snapshot.documents.mapNotNull { doc ->
                    val u = doc.toObject(User::class.java)
                    if (u != null) {
                        val effective = if (u.employeeId.isBlank()) u.copy(employeeId = doc.id) else u
                        if (effective.status.trim().equals("Pending", ignoreCase = true)) effective else null
                    } else null
                }
                
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
        val cleanId = id.trim()
        db.collection("employees").document(cleanId).update("status", status)
            .addOnSuccessListener {
                Toast.makeText(this, "User Approved Successfully!", Toast.LENGTH_SHORT).show()
                fetchPendingUsers()
            }
            .addOnFailureListener {
                db.collection("employees").whereEqualTo("employeeId", cleanId).get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val realDocId = snapshot.documents.first().id
                            db.collection("employees").document(realDocId).update("status", status)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "User Approved Successfully!", Toast.LENGTH_SHORT).show()
                                    fetchPendingUsers()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to approve: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Failed to approve user document", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to approve: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun confirmDeletePendingUser(user: User) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Warning: Reject & Delete")
            .setMessage("Are you sure you want to reject and permanently delete the registration request for ${user.name} (ID: ${user.employeeId})?\n\nThis action cannot be undone.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes, Delete") { _, _ ->
                deleteUser(user.employeeId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUser(id: String) {
        val cleanId = id.trim()
        db.collection("employees").document(cleanId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "User Rejected & Deleted!", Toast.LENGTH_SHORT).show()
                fetchPendingUsers()
            }
            .addOnFailureListener {
                db.collection("employees").whereEqualTo("employeeId", cleanId).get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val realDocId = snapshot.documents.first().id
                            db.collection("employees").document(realDocId).delete()
                                .addOnSuccessListener {
                                    Toast.makeText(this, "User Rejected & Deleted!", Toast.LENGTH_SHORT).show()
                                    fetchPendingUsers()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Failed to find user document to delete", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }
}
