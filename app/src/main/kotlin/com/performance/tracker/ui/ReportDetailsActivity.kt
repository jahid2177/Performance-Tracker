package com.performance.tracker.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.R
import com.performance.tracker.databinding.ActivityReportDetailsBinding
import com.performance.tracker.databinding.DialogEditReportBinding
import com.performance.tracker.model.Performance
import com.performance.tracker.util.SessionManager

class ReportDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private var canEdit = false
    private var empId = ""
    private var month = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        empId = intent.getStringExtra("EMP_ID") ?: ""
        month = intent.getStringExtra("MONTH") ?: ""
        val empName = intent.getStringExtra("EMP_NAME") ?: "Employee"
        val branch = intent.getStringExtra("BRANCH") ?: "Branch"

        val loggedInRole = SessionManager.getUserRole(this)
        val isAdmin = loggedInRole.equals("ADMIN", ignoreCase = true)
        val isSalesManager = loggedInRole.equals("Sales Manager", ignoreCase = true)

        // Permission: Admin can edit any report. Sales Manager can edit reports under their team.
        canEdit = intent.getBooleanExtra("CAN_EDIT", false) || isAdmin || isSalesManager

        // UI Setup
        binding.tvDetailName.text = empName
        binding.tvDetailMonth.text = "Reporting Month: $month"
        binding.tvDetailBranch.text = "Branch: $branch"

        binding.btnBack.setOnClickListener { finish() }
        binding.recyclerTable.layoutManager = LinearLayoutManager(this)

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
                val list = mutableListOf<Performance>()
                for (doc in snapshot.documents) {
                    val perf = doc.toObject(Performance::class.java)
                    if (perf != null) {
                        perf.id = doc.id
                        list.add(perf)
                    }
                }

                // Table adapter
                binding.recyclerTable.adapter = TableAdapter(list, canEdit) { item ->
                    showEditRecordDialog(item)
                }

                // Calculate total limit
                var totalLimit = 0.0
                for (item in list) {
                    val limitVal = item.limit.replace(",", "").toDoubleOrNull() ?: 0.0
                    totalLimit += limitVal
                }

                val count = if (list.size == 1 && list.first().limit.equals("NIL", ignoreCase = true)) 0 else list.size
                if (count == 0) {
                    binding.tvTotalLimit.text = "0 card | Total: 0"
                    binding.tvTotalLimit.setTextColor(Color.RED)
                } else {
                    val formattedTotal = String.format("%,.0f", totalLimit)
                    val cardText = if (count == 1) "1 card" else "$count cards"
                    binding.tvTotalLimit.text = "$cardText | Total: $formattedTotal"
                    binding.tvTotalLimit.setTextColor(Color.parseColor("#E65100"))
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load report", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditRecordDialog(item: Performance) {
        val dialogBinding = DialogEditReportBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.etEditApplicant.setText(if (item.applicantName.equals("NIL", ignoreCase = true)) "" else item.applicantName)
        dialogBinding.etEditAccount.setText(if (item.accountNo.equals("NIL", ignoreCase = true)) "" else item.accountNo)
        dialogBinding.etEditLimit.setText(if (item.limit.equals("NIL", ignoreCase = true)) "" else item.limit)

        dialogBinding.btnCancelEditRecord.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveEditRecord.setOnClickListener {
            val newApplicant = dialogBinding.etEditApplicant.text.toString().trim()
            val newAccount = dialogBinding.etEditAccount.text.toString().trim()
            val newLimit = dialogBinding.etEditLimit.text.toString().trim()

            if (newApplicant.isEmpty()) {
                dialogBinding.etEditApplicant.error = "Applicant Name is required"
                return@setOnClickListener
            }

            dialogBinding.btnSaveEditRecord.isEnabled = false
            dialogBinding.btnSaveEditRecord.text = "Updating..."

            val updates = mapOf<String, Any>(
                "applicantName" to newApplicant,
                "accountNo" to newAccount,
                "limit" to newLimit.ifEmpty { "0" }
            )

            db.collection("performance").document(item.id)
                .update(updates)
                .addOnSuccessListener {
                    dialog.dismiss()
                    Toast.makeText(this, "Record updated successfully!", Toast.LENGTH_SHORT).show()
                    loadTableData(empId, month)
                }
                .addOnFailureListener { e ->
                    dialogBinding.btnSaveEditRecord.isEnabled = true
                    dialogBinding.btnSaveEditRecord.text = "Update"
                    Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    // ==========================================
    // Table Adapter
    // ==========================================
    inner class TableAdapter(
        private val list: List<Performance>,
        private val isEditable: Boolean,
        private val onEditClick: (Performance) -> Unit
    ) : RecyclerView.Adapter<TableAdapter.TableVH>() {

        inner class TableVH(view: View) : RecyclerView.ViewHolder(view) {
            val tvSl: TextView = view.findViewById(R.id.tvSlNo)
            val tvName: TextView = view.findViewById(R.id.tvApplicantName)
            val tvAc: TextView = view.findViewById(R.id.tvAccountNo)
            val tvLimit: TextView = view.findViewById(R.id.tvLimit)
            val ivEdit: ImageView = view.findViewById(R.id.ivEditRow)
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

            if (item.limit.equals("NIL", ignoreCase = true)) {
                holder.tvLimit.text = "NIL"
                holder.tvLimit.setTextColor(Color.RED)
                holder.tvLimit.setTypeface(null, Typeface.BOLD)
            } else {
                val limitNum = item.limit.replace(",", "").toDoubleOrNull()
                if (limitNum != null) {
                    holder.tvLimit.text = String.format("%,.0f", limitNum)
                } else {
                    holder.tvLimit.text = item.limit
                }
                holder.tvLimit.setTextColor(Color.parseColor("#2E7D32"))
                holder.tvLimit.setTypeface(null, Typeface.BOLD)
            }

            if (isEditable) {
                holder.ivEdit.visibility = View.VISIBLE
                holder.ivEdit.setOnClickListener { onEditClick(item) }
                holder.itemView.setOnClickListener { onEditClick(item) }
            } else {
                holder.ivEdit.visibility = View.GONE
                holder.itemView.setOnClickListener(null)
            }

            if (position % 2 == 0) {
                holder.itemView.setBackgroundColor(Color.parseColor("#FFFFFF"))
            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
        }

        override fun getItemCount() = list.size
    }
}
