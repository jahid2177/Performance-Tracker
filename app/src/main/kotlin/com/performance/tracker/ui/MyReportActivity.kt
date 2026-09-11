package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.performance.tracker.R
import com.performance.tracker.adapter.ReportAdapter
import com.performance.tracker.databinding.ActivityAdminDashboardBinding
import com.performance.tracker.model.ReportSummary
import com.performance.tracker.viewmodel.MainViewModel

class MyReportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminDashboardBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUserId = intent.getStringExtra("USER_ID") ?: ""

        // হেডার আপডেট
        binding.tvHeaderTitle.text = "My Reports" 
        binding.tvFilterStatus.text = "Viewing: My Monthly Submissions"
        binding.tvFilterStatus.setTextColor(android.graphics.Color.parseColor("#2196F3"))

        // 🔥 এখন optionsLayout আইডিটি কাজ করবে
        binding.optionsLayout.visibility = View.GONE 
        binding.progressBar.visibility = View.VISIBLE

        // ব্যাক বাটন হিসেবে আইকনটি পরিবর্তন
        binding.ivAdminSettingsIcon.setImageResource(R.drawable.ic_chevron_right)
        binding.ivAdminSettingsIcon.rotation = 180f
        binding.btnAdminSettings.setOnClickListener { finish() }

        setupRecyclerView()
        
        viewModel.getAllReports()
        viewModel.reportList.observe(this) { list ->
            binding.progressBar.visibility = View.GONE
            
            if (list != null) {
                val myData = list.filter { it.employeeId == currentUserId }

                val groupedList = myData.groupBy { it.month }.map { (_, performances) ->
                    val first = performances.first()
                    
                    val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) {
                        0
                    } else {
                        performances.size
                    }

                    ReportSummary(
                        employeeId = first.employeeId,
                        employeeName = first.employeeName,
                        branch = first.branch,
                        month = first.month,
                        timestamp = first.timestamp,
                        totalRecords = actualCount
                    )
                }
                
                adapter.updateList(groupedList)
                
                if (groupedList.isEmpty()) {
                    binding.tvFilterStatus.text = "No reports found for your account."
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ReportAdapter(emptyList(), canEdit = false, canDelete = false, onCardClick = { summary ->
            val intent = Intent(this, ReportDetailsActivity::class.java).apply {
                putExtra("EMP_ID", summary.employeeId)
                putExtra("MONTH", summary.month)
                putExtra("EMP_NAME", summary.employeeName)
                putExtra("BRANCH", summary.branch)
                putExtra("CAN_EDIT", false)
            }
            startActivity(intent)
        })
        binding.recyclerAdminReport.layoutManager = LinearLayoutManager(this)
        binding.recyclerAdminReport.adapter = adapter
    }
}
