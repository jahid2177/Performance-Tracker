package com.performance.tracker.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.databinding.ActivityReportDetailsBinding
import com.performance.tracker.model.Performance

class ReportDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportDetailsBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val empId = intent.getStringExtra("EMP_ID") ?: ""
        val month = intent.getStringExtra("MONTH") ?: ""
        val empName = intent.getStringExtra("EMP_NAME") ?: "Employee"
        val branch = intent.getStringExtra("BRANCH") ?: "Branch"

        // UI Setup
        binding.tvDetailName.text = empName
        binding.tvDetailMonth.text = "Reporting Month: $month"
        binding.tvDetailBranch.text = "Branch: $branch"

        binding.btnBack.setOnClickListener { finish() }

        binding.recyclerTable.layoutManager = LinearLayoutManager(this)

        // ডাটা লোড করা
        if (empId.isNotEmpty() && month.isNotEmpty()) {
            loadTableData(empId, month)
        } else {
            Toast.makeText(this, "Invalid Data Provided!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTableData(empId: String, month: String) {
        db.collection("performance")
            .whereEqualTo("employeeId", empId)
            .whereEqualTo("month", month)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.toObjects(Performance::class.java)
                
                // টেবিল অ্যাডাপ্টার সেট করা
                binding.recyclerTable.adapter = TableAdapter(list)

                // টোটাল লিমিট ক্যালকুলেশন
                var totalLimit = 0.0
                for (item in list) {
                    val limitVal = item.limit.replace(",", "").toDoubleOrNull() ?: 0.0
                    totalLimit += limitVal
                }
                
                // 🔥 "Records" এর বদলে "card" এবং 0 হলে লাল কালার দেখানোর লজিক
                val count = list.size
                if (count == 0) {
                    binding.tvTotalLimit.text = "0 card | Total: 0"
                    binding.tvTotalLimit.setTextColor(Color.RED)
                } else {
                    val formattedTotal = String.format("%,.0f", totalLimit)
                    val cardText = if (count == 1) "1 card" else "$count cards"
                    binding.tvTotalLimit.text = "$cardText | Total: $formattedTotal"
                    binding.tvTotalLimit.setTextColor(Color.parseColor("#E65100")) // ডিফল্ট কমলা রঙ
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load report", Toast.LENGTH_SHORT).show()
            }
    }

    // ==========================================
    // 🔥 Table Adapter
    // ==========================================
    inner class TableAdapter(private val list: List<Performance>) : RecyclerView.Adapter<TableAdapter.TableVH>() {

        inner class TableVH(view: View) : RecyclerView.ViewHolder(view) {
            val tvSl: TextView = view.findViewById(R.id.tvSlNo)
            val tvName: TextView = view.findViewById(R.id.tvApplicantName)
            val tvAc: TextView = view.findViewById(R.id.tvAccountNo)
            val tvLimit: TextView = view.findViewById(R.id.tvLimit)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TableVH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_table_row, parent, false)
            return TableVH(view)
        }

        override fun onBindViewHolder(holder: TableVH, position: Int) {
            val item = list[position]
            
            holder.tvSl.text = (position + 1).toString()
            holder.tvName.text = item.applicantName
            holder.tvAc.text = item.accountNo
            
            // 🔥 NIL হ্যান্ডেলিং এবং নাম্বার ফরম্যাটিং
            if (item.limit.equals("NIL", ignoreCase = true)) {
                holder.tvLimit.text = "NIL"
                holder.tvLimit.setTextColor(Color.RED)
                holder.tvLimit.setTypeface(null, Typeface.BOLD)
            } else {
                val limitNum = item.limit.replace(",", "").toDoubleOrNull()
                if(limitNum != null) {
                    holder.tvLimit.text = String.format("%,.0f", limitNum)
                } else {
                    holder.tvLimit.text = item.limit
                }
                holder.tvLimit.setTextColor(Color.parseColor("#2E7D32")) // সাধারণ ক্ষেত্রে সবুজ
                holder.tvLimit.setTypeface(null, Typeface.BOLD)
            }

            // জোড়-বিজোড় সারির কালার পরিবর্তন (এক্সেল শিটের মতো)
            if (position % 2 == 0) {
                holder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"))
            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
        }

        override fun getItemCount() = list.size
    }
}
