package com.performance.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.performance.tracker.adapter.ReportAdapter
import com.performance.tracker.databinding.ActivityMyReportBinding
import com.performance.tracker.model.EmployeeTarget
import com.performance.tracker.model.ReportSummary
import com.performance.tracker.viewmodel.MainViewModel

class MyReportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyReportBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ReportAdapter
    private val db = FirebaseFirestore.getInstance()
    private var currentUserId = ""
    private val targetMap = mutableMapOf<String, Int>()
    private var defaultMonthlyTarget = 8

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = intent.getStringExtra("USER_ID") ?: ""

        setupViews()
        setupRecyclerView()
        loadData()
    }

    private fun setupViews() {
        binding.tvHeaderTitle.text = "My Reports"
        binding.tvFilterStatus.text = "Viewing: My Monthly Submissions"

        binding.btnBack.setOnClickListener { finish() }

        binding.btnRefresh.setOnClickListener {
            binding.btnRefresh.animate().rotationBy(360f).setDuration(500).start()
            loadData()
        }
    }

    private fun setupRecyclerView() {
        adapter = ReportAdapter(
            emptyList(),
            canEdit = false,
            canDelete = false,
            onCardClick = { summary ->
                val intent = Intent(this, ReportDetailsActivity::class.java).apply {
                    putExtra("EMP_ID", summary.employeeId)
                    putExtra("MONTH", summary.month)
                    putExtra("EMP_NAME", summary.employeeName)
                    putExtra("BRANCH", summary.branch)
                    putExtra("CAN_EDIT", false)
                }
                startActivity(intent)
            }
        )
        binding.recyclerMyReports.layoutManager = LinearLayoutManager(this)
        binding.recyclerMyReports.adapter = adapter
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE

        val cleanUserId = currentUserId.trim()

        val onUserFound = { userDocSnapshot: com.google.firebase.firestore.DocumentSnapshot? ->
            val mTarget = userDocSnapshot?.getLong("monthlyTarget")?.toInt() ?: 0
            if (mTarget > 0) defaultMonthlyTarget = mTarget

            db.collection("targets")
                .whereEqualTo("employeeId", cleanUserId)
                .get()
                .addOnSuccessListener { targetSnapshot ->
                    targetMap.clear()
                    for (doc in targetSnapshot.documents) {
                        val target = doc.toObject(EmployeeTarget::class.java)
                        if (target != null && target.targetCards > 0) {
                            targetMap[target.month.trim().lowercase()] = target.targetCards
                        }
                    }
                    fetchAndBindReports()
                }
                .addOnFailureListener {
                    fetchAndBindReports()
                }
        }

        db.collection("employees").document(cleanUserId).get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    onUserFound(userDoc)
                } else {
                    db.collection("employees").whereEqualTo("employeeId", cleanUserId).get()
                        .addOnSuccessListener { snap ->
                            val doc = if (!snap.isEmpty) snap.documents.first() else null
                            onUserFound(doc)
                        }
                        .addOnFailureListener { fetchAndBindReports() }
                }
            }
            .addOnFailureListener {
                fetchAndBindReports()
            }
    }

    private fun fetchAndBindReports() {
        val cleanUserId = currentUserId.trim()
        viewModel.getAllReports()
        viewModel.reportList.observe(this) { list ->
            binding.progressBar.visibility = View.GONE

            if (list != null) {
                val myData = list.filter { it.employeeId.trim().equals(cleanUserId, ignoreCase = true) }

                val groupedList = myData.groupBy { it.month }.map { (monthName, performances) ->
                    val first = performances.first()

                    val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) {
                        0
                    } else {
                        performances.size
                    }

                    val specificTarget = targetMap[monthName.trim().lowercase()] ?: defaultMonthlyTarget

                    ReportSummary(
                        employeeId = first.employeeId,
                        employeeName = first.employeeName,
                        branch = first.branch,
                        month = first.month,
                        timestamp = first.timestamp,
                        totalRecords = actualCount,
                        targetCards = specificTarget
                    )
                }

                adapter.updateList(groupedList)

                if (groupedList.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                }
            }
        }
    }
}
