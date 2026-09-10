package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.databinding.ActivityOfficerListBinding 
import com.performance.tracker.model.User 

class OfficerListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOfficerListBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var adapter: OfficerListAdapter
    private var officerList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOfficerListBinding.inflate(layoutInflater) 
        setContentView(binding.root)

        binding.btnBack?.setOnClickListener { finish() }
        
        setupRecyclerView()
        loadApprovedOfficers()
    }

    private fun setupRecyclerView() {
        adapter = OfficerListAdapter(officerList) { user ->
            confirmDeleteOfficer(user)
        }
        binding.recyclerOfficers.layoutManager = LinearLayoutManager(this)
        binding.recyclerOfficers.adapter = adapter
    }

    private fun loadApprovedOfficers() {
        binding.progressBar.visibility = View.VISIBLE 

        // 🔥 Firestore কোয়েরি: রেজিস্ট্রেশন লজিক অনুযায়ী status "Approved" হওয়া জরুরি (Case-sensitive)
        db.collection("employees") 
            .whereEqualTo("role", "USER") 
            .whereEqualTo("status", "APPROVED") 
            .get()
            .addOnSuccessListener { snapshot ->
                binding.progressBar.visibility = View.GONE 
                officerList.clear()
                
                for (doc in snapshot.documents) {
                    val user = doc.toObject(User::class.java)
                    if (user != null) {
                        officerList.add(user)
                    }
                }
                adapter.notifyDataSetChanged()

                if (officerList.isEmpty()) {
                    binding.tvEmpty?.visibility = View.VISIBLE
                    binding.tvEmpty?.text = "No approved officers found."
                } else {
                    binding.tvEmpty?.visibility = View.GONE
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE 
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmDeleteOfficer(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Remove Officer")
            .setMessage("Are you sure you want to remove ${user.name} from the approved list?")
            .setPositiveButton("Remove") { _, _ ->
                db.collection("employees").document(user.employeeId) 
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "${user.name} removed", Toast.LENGTH_SHORT).show()
                        loadApprovedOfficers() 
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==========================================
    // 🔥 Adapter for Officer List
    // ==========================================
    inner class OfficerListAdapter(
        private val list: List<User>,
        private val onDeleteClick: (User) -> Unit
    ) : RecyclerView.Adapter<OfficerListAdapter.OfficerVH>() {

        inner class OfficerVH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvOfficerName)
            val tvId: TextView = view.findViewById(R.id.tvOfficerId)
            val tvBranchZone: TextView = view.findViewById(R.id.tvBranchZone)
            val btnDelete: MaterialCardView = view.findViewById(R.id.btnDeleteOfficer)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OfficerVH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_officer, parent, false)
            return OfficerVH(view)
        }

        override fun onBindViewHolder(holder: OfficerVH, position: Int) {
            val user = list[position]
            holder.tvName.text = user.name
            holder.tvId.text = "ID: ${user.employeeId}"
            holder.tvBranchZone.text = "Branch: ${user.branch} | Zone: ${user.zone}"

            // 🔥 কার্ডের ওপর ক্লিক করলে এখন আধুনিক প্রোফাইল পেজ (Activity) ওপেন হবে
            holder.itemView.setOnClickListener {
                val intent = Intent(this@OfficerListActivity, ProfileActivity::class.java)
                intent.putExtra("USER_ID", user.employeeId) // আইডি পাস করা হচ্ছে
                startActivity(intent)
            }

            holder.btnDelete.setOnClickListener {
                onDeleteClick(user)
            }
        }

        override fun getItemCount() = list.size
    }
}
