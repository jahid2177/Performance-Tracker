package com.performance.tracker.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.performance.tracker.R
import com.performance.tracker.adapter.ReportAdapter
import com.performance.tracker.adapter.TargetOfficerAdapter
import com.performance.tracker.adapter.TargetOfficerItem
import com.performance.tracker.databinding.ActivityAdminDashboardBinding
import com.performance.tracker.databinding.DialogSetTargetBinding
import com.performance.tracker.databinding.DialogTargetManagementBinding
import com.performance.tracker.model.Performance
import com.performance.tracker.model.ReportSummary
import com.performance.tracker.model.User
import com.performance.tracker.util.ReportExporter
import com.performance.tracker.util.TargetUtils
import com.performance.tracker.viewmodel.MainViewModel
import android.widget.AdapterView
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminDashboardBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ReportAdapter
    
    private val db = FirebaseFirestore.getInstance()
    private var allReports: List<Performance> = ArrayList()

    private val monthsList = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val shortMonthsList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private var userRole = "ADMIN"
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("USER_ROLE") ?: "ADMIN"
        userName = intent.getStringExtra("USER_NAME") ?: ""

        setupRecyclerView()
        setupButtons()
        setupTrendChart()
        
        if (userRole.equals("Sales Manager", ignoreCase = true)) {
            binding.tvHeaderTitle.text = "Manager Dashboard"
        } else {
            binding.tvHeaderTitle.text = "Control Panel ($userRole)"
        }
        
        loadDashboardData()
        
        binding.btnRefresh.setOnClickListener {
            Toast.makeText(this, "Refreshing data...", Toast.LENGTH_SHORT).show()
            loadDashboardData()
        }

        binding.btnAdminSettings.setOnClickListener {
            val loggedInId = intent.getStringExtra("USER_ID") ?: com.performance.tracker.util.SessionManager.getUserId(this)
            val intent = Intent(this, SettingsActivity::class.java).apply {
                putExtra("USER_ID", loggedInId)
                putExtra("USER_ROLE", userRole)
                putExtra("USER_NAME", userName)
            }
            startActivity(intent)
        }
    }

    private fun loadDashboardData() {
        binding.progressBar.visibility = View.VISIBLE
        viewModel.getAllReports()
        viewModel.reportList.observe(this) { list ->
            binding.progressBar.visibility = View.GONE
            if (list != null && list.isNotEmpty()) {
                allReports = if (userRole.equals("Sales Manager", ignoreCase = true)) {
                    list.filter { it.salesManager.equals(userName, ignoreCase = true) }
                } else {
                    list 
                }

                if (allReports.isNotEmpty()) {
                    showDefaultDashboard()
                    populateChartEmployeeSpinner()
                    updateTeamTargetOverview()
                } else {
                    binding.tvFilterStatus.text = "0 card"
                    binding.tvFilterStatus.setTextColor(Color.RED)
                    adapter.updateList(emptyList())
                    updateTeamTargetOverview()
                }

            } else {
                binding.tvFilterStatus.text = "0 card"
                binding.tvFilterStatus.setTextColor(Color.RED)
                adapter.updateList(emptyList())
                updateTeamTargetOverview()
            }
        }
    }

    private fun showDefaultDashboard() {
        val groupedList = allReports.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
            val first = performances.first()
            val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
            ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
        }
        adapter.updateList(groupedList)
        
        val count = groupedList.size
        if (count == 0) {
            binding.tvFilterStatus.text = "0 card"
            binding.tvFilterStatus.setTextColor(Color.RED)
        } else {
            val cardText = if(count == 1) "1 card" else "$count cards"
            binding.tvFilterStatus.text = "Dashboard: $cardText"
            binding.tvFilterStatus.setTextColor(Color.BLACK)
        }
    }

    private fun setupRecyclerView() {
        val isAdmin = userRole.equals("ADMIN", ignoreCase = true)
        val isSalesManager = userRole.equals("Sales Manager", ignoreCase = true)
        val canEdit = isAdmin || isSalesManager
        val canDelete = isAdmin

        adapter = ReportAdapter(
            emptyList(),
            canEdit = canEdit,
            canDelete = canDelete,
            onCardClick = { summary ->
                val intent = Intent(this, ReportDetailsActivity::class.java).apply {
                    putExtra("EMP_ID", summary.employeeId)
                    putExtra("MONTH", summary.month)
                    putExtra("EMP_NAME", summary.employeeName)
                    putExtra("BRANCH", summary.branch)
                    putExtra("CAN_EDIT", canEdit)
                }
                startActivity(intent)
            },
            onActionClick = { summary, action ->
                if (action == "DELETE" && canDelete) {
                    confirmDeleteGroup(summary)
                } else if (action == "EDIT" && canEdit) {
                    val intent = Intent(this, ReportDetailsActivity::class.java).apply {
                        putExtra("EMP_ID", summary.employeeId)
                        putExtra("MONTH", summary.month)
                        putExtra("EMP_NAME", summary.employeeName)
                        putExtra("BRANCH", summary.branch)
                        putExtra("CAN_EDIT", true)
                    }
                    startActivity(intent)
                }
            }
        )
        binding.recyclerAdminReport.layoutManager = LinearLayoutManager(this)
        binding.recyclerAdminReport.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnMonthlyPdf.setOnClickListener { 
            if (allReports.isEmpty()) {
                Toast.makeText(this, "No data available to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showExportDialog(defaultFormatIsPdf = true) 
        }

        binding.btnExportCsv.setOnClickListener { 
            if (allReports.isEmpty()) {
                Toast.makeText(this, "No data available to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showExportDialog(defaultFormatIsPdf = false) 
        }

        binding.btnDownloadExcel.setOnClickListener { 
            showExportDialog(defaultFormatIsPdf = false) 
        }

        binding.btnToggleTrendChart.setOnClickListener {
            val isVisible = binding.cardTrendChart.visibility == View.VISIBLE
            binding.cardTrendChart.visibility = if (isVisible) View.GONE else View.VISIBLE
            val status = if (isVisible) "hidden" else "visible"
            Toast.makeText(this, "Trend chart $status", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnWhoNotSubmitted.setOnClickListener { 
            showWhoNotSubmittedDialog() 
        }

        binding.btnFilterMonthWise?.setOnClickListener { 
            if (allReports.isEmpty()) return@setOnClickListener
            showMonthSelectionGrid()
        }
        binding.btnFilterRanking?.setOnClickListener {
            if (allReports.isEmpty()) return@setOnClickListener
            showRankingDialog()
        }
        binding.btnFilterIndividual.setOnClickListener { 
            if (allReports.isEmpty()) return@setOnClickListener
            val uniqueEmployees = allReports.distinctBy { it.employeeId }.sortedBy { it.employeeName }
            showEmployeeListDialog("Select Employee", uniqueEmployees)
        }
        binding.btnFilterZone.setOnClickListener { 
            if (allReports.isEmpty()) return@setOnClickListener
            val uniqueZones = allReports.map { it.zone.uppercase().trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            showCategoryDialog("Select Zone", uniqueZones) { selectedZone ->
                val empInZone = allReports.filter { it.zone.equals(selectedZone, ignoreCase = true) }.distinctBy { it.employeeId }
                showEmployeeListDialog("Employees in $selectedZone", empInZone)
            }
        }
        binding.btnFilterTeam.setOnClickListener { 
            if (allReports.isEmpty()) return@setOnClickListener
            val uniqueManagers = allReports.map { it.salesManager.uppercase().trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            showCategoryDialog("Select Manager", uniqueManagers) { selectedManager ->
                val empUnderManager = allReports.filter { it.salesManager.equals(selectedManager, ignoreCase = true) }.distinctBy { it.employeeId }
                showEmployeeListDialog("Team: $selectedManager", empUnderManager)
            }
        }

        binding.btnOfficerList.setOnClickListener { startActivity(Intent(this, OfficerListActivity::class.java)) }
        binding.btnApproval.setOnClickListener { startActivity(Intent(this, ApprovalActivity::class.java)) }

        binding.btnManageTargets.setOnClickListener { showTargetManagementDialog() }
        binding.btnAdminQuickManageTargets.setOnClickListener { showTargetManagementDialog() }
        binding.cardAdminTargetOverview.setOnClickListener { showTargetManagementDialog() }
    }

    // ========================================================
    // 🔥 NEW: Who Not Submitted Logic & UI
    // ========================================================
    private fun showWhoNotSubmittedDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_employee_search, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchEmployee)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerEmployeeList)

        tvTitle.text = "Select Month"
        tvTitle.setTextColor(Color.parseColor("#2196F3"))
        tvTitle.textSize = 24f
        etSearch.visibility = View.GONE
        
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        recycler.layoutManager = GridLayoutManager(this, 4) 
        
        recycler.adapter = ModernMonthGridAdapter(shortMonthsList) { selectedShortMonth ->
            dialog.dismiss()
            val fullMonthIndex = shortMonthsList.indexOf(selectedShortMonth)
            val fullMonthName = monthsList[fullMonthIndex]
            fetchWhoNotSubmitted(fullMonthName)
        }
        
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun fetchWhoNotSubmitted(targetMonth: String) {
    binding.progressBar.visibility = View.VISIBLE
    
    // 🔥 কোয়েরিতে status ফিল্টার বাদ দিয়ে সব ইউজার আনছি, পরে ফিল্টার করব
    db.collection("employees").get()
        .addOnSuccessListener { snapshot ->
            binding.progressBar.visibility = View.GONE
            val allEmployees = snapshot.toObjects(User::class.java)
            
            // শুধু Approved এবং USER রোলধারী এমপ্লয়িদের ফিল্টার করা (Case Insensitive)
            val approvedUsers = allEmployees.filter { 
                it.role.equals("USER", ignoreCase = true) && 
                it.status.equals("Approved", ignoreCase = true) 
            }
            
            // যারা ওই মাসে অন্তত একটি রিপোর্ট (NIL বা আসল) জমা দিয়েছে তাদের আইডি
            val submittedIds = allReports.filter { it.month.equals(targetMonth, ignoreCase = true) }
                .map { it.employeeId }.distinct()
            
            // যারা জমা দেয়নি (Approved লিস্টে আছে কিন্তু Submitted লিস্টে নেই)
            val defaulters = approvedUsers.filter { 
                it.employeeId !in submittedIds 
            }.sortedBy { it.name }

            if (defaulters.isEmpty()) {
                Toast.makeText(this, "Excellent! Everyone submitted for $targetMonth", Toast.LENGTH_LONG).show()
            } else {
                val missingPerformances = defaulters.map { 
                    Performance(
                        employeeId = it.employeeId, 
                        employeeName = it.name, 
                        branch = it.branch, 
                        zone = it.zone, 
                        salesManager = it.salesManager, 
                        month = targetMonth, 
                        applicantName = "Report Pending", // একটু সুন্দর টেক্সট
                        accountNo = "N/A", 
                        limit = "0"
                    )
                }
                showEmployeeListDialog("Not Submitted: $targetMonth (${defaulters.size})", missingPerformances)
            }
        }
        .addOnFailureListener {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Failed to load employees data", Toast.LENGTH_SHORT).show()
        }
}

    // ========================================================
    // 🔥 MPAndroidChart: Individual Performance Trends
    // ========================================================
    private fun setupTrendChart() {
        binding.cardTrendChart.visibility = View.VISIBLE
        val chart = binding.trendLineChart
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = false
        chart.setExtraOffsets(8f, 10f, 8f, 10f)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(shortMonthsList)
        xAxis.granularity = 1f
        xAxis.textColor = Color.parseColor("#64748B")
        xAxis.textSize = 9.5f

        val yAxis = chart.axisLeft
        yAxis.axisMinimum = 0f
        yAxis.granularity = 1f
        yAxis.textColor = Color.parseColor("#64748B")
        yAxis.gridColor = Color.parseColor("#E2E8F0")

        chart.axisRight.isEnabled = false
    }

    private fun populateChartEmployeeSpinner() {
        val uniqueEmployees = allReports.distinctBy { it.employeeId }.sortedBy { it.employeeName }
        if (uniqueEmployees.isEmpty()) return

        val spinnerItems = ArrayList<String>()
        spinnerItems.add("All Team Combined")
        uniqueEmployees.forEach { emp ->
            spinnerItems.add("${emp.employeeName} (${emp.employeeId})")
        }

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spinnerItems)
        binding.spinnerChartEmployee.adapter = spinnerAdapter

        binding.spinnerChartEmployee.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    renderTrendChart(null, "All Team Combined")
                } else {
                    val emp = uniqueEmployees[position - 1]
                    renderTrendChart(emp.employeeId, emp.employeeName)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        if (uniqueEmployees.isNotEmpty()) {
            binding.spinnerChartEmployee.setSelection(1)
        }
    }

    private fun renderTrendChart(employeeId: String?, displayName: String) {
        val chart = binding.trendLineChart
        binding.tvTrendSelectedOfficer.text = displayName

        val targetData = if (employeeId == null) {
            allReports
        } else {
            allReports.filter { it.employeeId == employeeId }
        }

        val entries = ArrayList<Entry>()
        var totalCardsAnnual = 0
        var peakCards = 0
        var peakMonthName = "-"
        var activeMonthsCount = 0

        monthsList.forEachIndexed { index, fullMonthName ->
            val monthData = targetData.filter { it.month.equals(fullMonthName, ignoreCase = true) }
            val cardsCount = if (monthData.size == 1 && monthData.first().limit.equals("NIL", ignoreCase = true)) {
                0
            } else {
                monthData.size
            }

            entries.add(Entry(index.toFloat(), cardsCount.toFloat()))
            totalCardsAnnual += cardsCount
            if (cardsCount > 0) activeMonthsCount++
            if (cardsCount > peakCards) {
                peakCards = cardsCount
                peakMonthName = shortMonthsList[index]
            }
        }

        // Update Stat Cards
        binding.tvTrendTotalCards.text = "Total: $totalCardsAnnual"
        binding.tvTrendPeakMonth.text = if (peakCards > 0) "Peak: $peakMonthName ($peakCards)" else "Peak: None"
        val avgMonthly = if (activeMonthsCount > 0) String.format(Locale.US, "%.1f", totalCardsAnnual.toFloat() / activeMonthsCount) else "0.0"
        binding.tvTrendAvgMonthly.text = "Avg: $avgMonthly"

        val dataSet = LineDataSet(entries, "Performance").apply {
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            color = Color.parseColor("#2563EB")
            lineWidth = 2.5f
            setCircleColor(Color.parseColor("#2563EB"))
            circleRadius = 4.5f
            circleHoleColor = Color.WHITE
            circleHoleRadius = 2.5f
            setDrawValues(true)
            valueTextSize = 9.5f
            valueTextColor = Color.parseColor("#1E293B")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) value.toInt().toString() else ""
                }
            }
            setDrawFilled(true)
            fillColor = Color.parseColor("#93C5FD")
            fillAlpha = 65
            highLightColor = Color.parseColor("#1D4ED8")
            setDrawHighlightIndicators(true)
        }

        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.animateY(500)
        chart.invalidate()
    }

    // ========================================================
    // 🔥 OpenPDF, Kotlin-CSV & Standard XLSX: Performance Report Exports
    // ========================================================
    private enum class ReportFormatOption { XLSX, CSV, PDF }

    private fun showExportDialog(defaultFormatIsPdf: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_report, null)
        val spinnerFrom = dialogView.findViewById<Spinner>(R.id.spinnerFromMonth)
        val spinnerTo = dialogView.findViewById<Spinner>(R.id.spinnerToMonth)
        val btnDownload = dialogView.findViewById<Button>(R.id.btnDownloadReport)
        val btnShare = dialogView.findViewById<Button>(R.id.btnShareReport)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvExportTitle)
        val rgFormat = dialogView.findViewById<RadioGroup>(R.id.rgExportFormat)
        val rbXlsx = dialogView.findViewById<RadioButton>(R.id.rbFormatXlsx)
        val rbCsv = dialogView.findViewById<RadioButton>(R.id.rbFormatCsv)
        val rbPdf = dialogView.findViewById<RadioButton>(R.id.rbFormatPdf)
        val rgExportType = dialogView.findViewById<RadioGroup>(R.id.rgExportType)
        val rbFullReport = dialogView.findViewById<RadioButton>(R.id.rbFullReport)
        val rbSummary = dialogView.findViewById<RadioButton>(R.id.rbSummary)
        val rbDetails = dialogView.findViewById<RadioButton>(R.id.rbDetails)

        if (defaultFormatIsPdf) {
            rbPdf.isChecked = true
            tvTitle.text = "Monthly Performance Report (PDF)"
        } else {
            rbXlsx.isChecked = true
            tvTitle.text = "Employee Performance Export (Excel .xlsx)"
        }

        rgFormat.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbFormatXlsx -> tvTitle.text = "Employee Performance Export (Excel .xlsx)"
                R.id.rbFormatCsv -> tvTitle.text = "Employee Performance Export (CSV)"
                R.id.rbFormatPdf -> tvTitle.text = "Monthly Performance Report (PDF)"
            }
        }

        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monthsList)
        spinnerFrom.adapter = monthAdapter
        spinnerTo.adapter = monthAdapter

        val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
        val monthIdx = monthsList.indexOf(currentMonth)
        if (monthIdx >= 0) {
            spinnerFrom.setSelection(monthIdx)
            spinnerTo.setSelection(monthIdx)
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val handleExport = { isShare: Boolean ->
            val fromMonth = spinnerFrom.selectedItem.toString()
            val toMonth = spinnerTo.selectedItem.toString()
            val fromIdx = monthsList.indexOf(fromMonth)
            val toIdx = monthsList.indexOf(toMonth)

            if (fromIdx > toIdx) {
                Toast.makeText(this, "Invalid Range: 'From Month' cannot be after 'To Month'", Toast.LENGTH_LONG).show()
            } else {
                val selectedMonths = monthsList.subList(fromIdx, toIdx + 1)
                val format = when {
                    rbXlsx.isChecked -> ReportFormatOption.XLSX
                    rbCsv.isChecked -> ReportFormatOption.CSV
                    else -> ReportFormatOption.PDF
                }
                val scope = when {
                    rbDetails.isChecked -> ReportExporter.XlsxReportMode.DETAILS
                    rbSummary.isChecked -> ReportExporter.XlsxReportMode.SUMMARY
                    else -> ReportExporter.XlsxReportMode.FULL
                }

                dialog.dismiss()
                executeExport(selectedMonths, format, fromMonth, toMonth, scope, isShare)
            }
        }

        btnDownload.setOnClickListener { handleExport(false) }
        btnShare.setOnClickListener { handleExport(true) }

        dialog.show()
    }

    private fun executeExport(
        selectedMonths: List<String>,
        format: ReportFormatOption,
        fromMonth: String,
        toMonth: String,
        scope: ReportExporter.XlsxReportMode,
        isShare: Boolean
    ) {
        val filteredData = allReports.filter { selectedMonths.contains(it.month) }

        if (filteredData.isEmpty()) { 
            Toast.makeText(this, "No records found for the selected period", Toast.LENGTH_SHORT).show()
            return 
        }

        val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val monthRange = if (fromMonth == toMonth) "$fromMonth $year" else "$fromMonth to $toMonth $year"

        when (format) {
            ReportFormatOption.XLSX -> {
                ReportExporter.exportPerformanceXlsx(
                    context = this,
                    data = filteredData,
                    monthRange = monthRange,
                    mode = scope,
                    isShare = isShare
                )
            }
            ReportFormatOption.CSV -> {
                ReportExporter.exportPerformanceCsv(
                    context = this,
                    data = filteredData,
                    monthRange = monthRange,
                    isSummary = (scope == ReportExporter.XlsxReportMode.SUMMARY),
                    isShare = isShare
                )
            }
            ReportFormatOption.PDF -> {
                ReportExporter.generateMonthlyPdf(
                    context = this,
                    data = filteredData,
                    monthRange = monthRange,
                    managerName = if (userRole.equals("Sales Manager", ignoreCase = true)) userName else "",
                    isSummary = (scope == ReportExporter.XlsxReportMode.SUMMARY),
                    isShare = isShare
                )
            }
        }
    }

    // ========================================================
    // Other Helpers (Grids, Search, Delete)
    // ========================================================
    private fun filterByMonth(month: String) {
        val filtered = allReports.filter { it.month.equals(month, ignoreCase = true) }
        val groupedList = filtered.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
            val first = performances.first()
            val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
            ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
        }
        adapter.updateList(groupedList)
        
        val count = groupedList.size
        if (count == 0) {
            binding.tvFilterStatus.text = "0 card"
            binding.tvFilterStatus.setTextColor(Color.RED)
        } else {
            val cardText = if(count == 1) "1 card" else "$count cards"
            binding.tvFilterStatus.text = "Viewing: $month ($cardText)"
            binding.tvFilterStatus.setTextColor(Color.parseColor("#3F51B5"))
        }
    }

    private fun filterDashboardByEmployee(empId: String, empName: String) {
        val filtered = allReports.filter { it.employeeId == empId }
        val groupedList = filtered.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
            val first = performances.first()
            val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
            ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
        }
        adapter.updateList(groupedList)
        
        val count = groupedList.size
        if (count == 0) {
            binding.tvFilterStatus.text = "0 card"
            binding.tvFilterStatus.setTextColor(Color.RED)
        } else {
            val cardText = if(count == 1) "1 card" else "$count cards"
            binding.tvFilterStatus.text = "Viewing: $empName ($cardText)"
            binding.tvFilterStatus.setTextColor(Color.parseColor("#2196F3"))
        }

        binding.cardTrendChart.visibility = View.VISIBLE
        renderTrendChart(empId, empName)
    }

    private fun showMonthSelectionGrid() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_employee_search, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchEmployee)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerEmployeeList)

        tvTitle.text = "Select Month"
        etSearch.visibility = View.GONE
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        recycler.layoutManager = GridLayoutManager(this, 4)
        recycler.adapter = MonthGridAdapter(monthsList) { selectedMonth ->
            dialog.dismiss()
            filterByMonth(selectedMonth)
        }
        dialog.show()
    }

    private fun showRankingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_employee_search, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchEmployee)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerEmployeeList)

        tvTitle.text = "Overall Ranking (Top Submissions)"
        etSearch.visibility = View.GONE

        val rankedList = allReports.groupBy { it.employeeName }
            .map { Pair(it.key, it.value.size) }
            .sortedByDescending { it.second }

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = RankingAdapter(rankedList)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showCategoryDialog(title: String, categories: List<String>, onSelected: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_employee_search, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchEmployee)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerEmployeeList)

        tvTitle.text = title
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        val catAdapter = CategoryAdapter(categories) { selected -> dialog.dismiss(); onSelected(selected) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = catAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                catAdapter.updateList(categories.filter { it.lowercase().contains(s.toString().lowercase()) })
            }
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })
        dialog.show()
    }

    private fun showEmployeeListDialog(title: String, employeeList: List<Performance>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_employee_search, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etSearch = dialogView.findViewById<EditText>(R.id.etSearchEmployee)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerEmployeeList)

        tvTitle.text = title
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        val empAdapter = EmployeeSearchAdapter(employeeList) { selectedEmp -> dialog.dismiss(); filterDashboardByEmployee(selectedEmp.employeeId, selectedEmp.employeeName) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = empAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                empAdapter.updateList(employeeList.filter { it.employeeName.lowercase().contains(s.toString().lowercase()) })
            }
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })
        dialog.show()
    }

    private fun confirmDeleteGroup(summary: ReportSummary) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Delete records for ${summary.employeeName} (${summary.month})?")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("performance").whereEqualTo("employeeId", summary.employeeId).whereEqualTo("month", summary.month)
                    .get().addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) doc.reference.delete()
                        viewModel.getAllReports()
                        Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    }
            }.setNegativeButton("Cancel", null).show()
    }

    // ========================================================
    // 🔥 All Adapters
    // ========================================================
    
    // Modern Month Grid Adapter for "Who Not Submitted"
    inner class ModernMonthGridAdapter(private val months: List<String>, private val onItemClick: (String) -> Unit) : RecyclerView.Adapter<ModernMonthGridAdapter.VH>() {
        inner class VH(val card: MaterialCardView, val tvName: TextView) : RecyclerView.ViewHolder(card) {
            init { card.setOnClickListener { onItemClick(months[adapterPosition]) } }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = MaterialCardView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(12, 12, 12, 12) }
                radius = 24f
                setCardBackgroundColor(Color.parseColor("#EAF4FF")) // Light Blue
                cardElevation = 0f 
            }
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(8, 32, 8, 32)
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(Color.parseColor("#1C3A70")) // Dark Blue
                setTypeface(null, Typeface.BOLD)
            }
            card.addView(tv)
            return VH(card, tv)
        }
        override fun onBindViewHolder(holder: VH, position: Int) { 
            holder.tvName.text = months[position] 
        }
        override fun getItemCount() = months.size
    }

    inner class CategoryAdapter(private var list: List<String>, private val onItemClick: (String) -> Unit) : RecyclerView.Adapter<CategoryAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) { val tvName: TextView = view.findViewById(R.id.tvEmpName); val tvBranch: TextView = view.findViewById(R.id.tvEmpBranch) }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH { return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_employee_search, parent, false)) }
        override fun onBindViewHolder(holder: VH, position: Int) { holder.tvName.text = list[position]; holder.tvBranch.visibility = View.GONE; holder.itemView.setOnClickListener { onItemClick(list[position]) } }
        override fun getItemCount() = list.size
        fun updateList(newList: List<String>) { list = newList; notifyDataSetChanged() }
    }

    inner class EmployeeSearchAdapter(private var empList: List<Performance>, private val onItemClick: (Performance) -> Unit) : RecyclerView.Adapter<EmployeeSearchAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) { val tvName: TextView = view.findViewById(R.id.tvEmpName); val tvBranch: TextView = view.findViewById(R.id.tvEmpBranch) }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH { return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_employee_search, parent, false)) }
        override fun onBindViewHolder(holder: VH, position: Int) { holder.tvName.text = empList[position].employeeName; holder.tvBranch.text = "Branch: ${empList[position].branch}"; holder.tvBranch.visibility = View.VISIBLE; holder.itemView.setOnClickListener { onItemClick(empList[position]) } }
        override fun getItemCount() = empList.size
        fun updateList(newList: List<Performance>) { empList = newList; notifyDataSetChanged() }
    }

    inner class RankingAdapter(private val rankList: List<Pair<String, Int>>) : RecyclerView.Adapter<RankingAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cardBadge: MaterialCardView = view.findViewById(R.id.cardRankBadge)
            val tvNumber: TextView = view.findViewById(R.id.tvRankNumber)
            val tvName: TextView = view.findViewById(R.id.tvRankName)
            val tvScore: TextView = view.findViewById(R.id.tvRankScore)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_ranking, parent, false))
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val rankData = rankList[position]
            val rankPosition = position + 1 
            holder.tvNumber.text = rankPosition.toString()
            holder.tvName.text = rankData.first
            when (rankPosition) {
                1 -> { holder.cardBadge.setCardBackgroundColor(Color.parseColor("#FFD700")); holder.tvNumber.setTextColor(Color.WHITE); holder.tvName.setTextColor(Color.parseColor("#D4AF37")) }
                2 -> { holder.cardBadge.setCardBackgroundColor(Color.parseColor("#C0C0C0")); holder.tvNumber.setTextColor(Color.WHITE); holder.tvName.setTextColor(Color.parseColor("#757575")) }
                3 -> { holder.cardBadge.setCardBackgroundColor(Color.parseColor("#CD7F32")); holder.tvNumber.setTextColor(Color.WHITE); holder.tvName.setTextColor(Color.parseColor("#8D6E63")) }
                else -> { holder.cardBadge.setCardBackgroundColor(Color.parseColor("#F5F5F5")); holder.tvNumber.setTextColor(Color.parseColor("#333333")); holder.tvName.setTextColor(Color.parseColor("#212121")) }
            }
            val count = rankData.second
            if (count == 0) {
                holder.tvScore.text = "0 card"
                holder.tvScore.setTextColor(Color.RED)
            } else {
                val cardText = if(count == 1) "1 card" else "$count cards"
                holder.tvScore.text = cardText
                holder.tvScore.setTextColor(Color.parseColor("#E91E63")) 
            }
        }
        override fun getItemCount() = rankList.size
    }

    inner class MonthGridAdapter(private val months: List<String>, private val onItemClick: (String) -> Unit) : RecyclerView.Adapter<MonthGridAdapter.VH>() {
        inner class VH(val card: MaterialCardView, val tvName: TextView) : RecyclerView.ViewHolder(card) {
            init { card.setOnClickListener { onItemClick(months[adapterPosition]) } }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = MaterialCardView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(8, 8, 8, 8) }
                radius = 16f
                setCardBackgroundColor(Color.parseColor("#E3F2FD"))
                cardElevation = 2f
            }
            val tv = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(8, 24, 8, 24)
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(Color.parseColor("#1A237E"))
                setTypeface(null, Typeface.BOLD)
            }
            card.addView(tv)
            return VH(card, tv)
        }
        override fun onBindViewHolder(holder: VH, position: Int) { 
            holder.tvName.text = months[position].take(3)
        }
        override fun getItemCount() = months.size
    }

    // ========================================================
    // 🔥 Target & Achievement Management Logic
    // ========================================================

    private fun updateTeamTargetOverview() {
        db.collection("employees")
            .whereEqualTo("status", "Approved")
            .get()
            .addOnSuccessListener { snapshot ->
                val employees = snapshot.toObjects(User::class.java)
                val currentMonth = SimpleDateFormat("MMMM", Locale.US).format(Date())
                val totalTarget = employees.sumOf { it.monthlyTarget }
                val achievedInMonth = allReports.count { 
                    !it.limit.equals("NIL", ignoreCase = true) && it.month.equals(currentMonth, ignoreCase = true) 
                }
                
                val rate = TargetUtils.calculateAchievementRate(achievedInMonth, totalTarget)
                binding.tvAdminOverallAchievementBadge.text = TargetUtils.formatAchievementRate(achievedInMonth, totalTarget)
                val color = TargetUtils.getAchievementColor(achievedInMonth, totalTarget)
                binding.tvAdminOverallAchievementBadge.setTextColor(color)
                binding.tvAdminTargetSummaryText.text = "Target: $totalTarget | Achieved: $achievedInMonth Cards ($currentMonth)"
                binding.progressAdminOverallTarget.progress = rate.toInt().coerceIn(0, 100)
                binding.progressAdminOverallTarget.setIndicatorColor(color)
            }
    }

    private fun showTargetManagementDialog() {
        val dialogBinding = DialogTargetManagementBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnCloseTargetDialog.setOnClickListener { dialog.dismiss() }

        val filterMonths = listOf("Current Month", "All Months / Total") + monthsList
        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterMonths)
        dialogBinding.spinnerTargetFilterMonth.adapter = monthAdapter

        val currentMonthName = SimpleDateFormat("MMMM", Locale.US).format(Date())

        var allApprovedOfficers = listOf<User>()

        fun showEmployeeTargetDetails(officer: User) {
            showUserTargetDetailsDialog(officer)
        }

        lateinit var targetOfficerAdapter: TargetOfficerAdapter
        targetOfficerAdapter = TargetOfficerAdapter(
            items = emptyList(),
            onSetTargetClick = { officer ->
                val selectedMonthPos = dialogBinding.spinnerTargetFilterMonth.selectedItemPosition
                val targetMonth = when (selectedMonthPos) {
                    0 -> currentMonthName
                    1 -> "General"
                    else -> filterMonths[selectedMonthPos]
                }
                showSetTargetDialog(officer, targetMonth) {
                    loadTargetDataForDialog(dialogBinding, currentMonthName, targetOfficerAdapter) { officers ->
                        allApprovedOfficers = officers
                    }
                    updateTeamTargetOverview()
                }
            },
            onItemClick = { officer ->
                showEmployeeTargetDetails(officer)
            }
        )

        dialogBinding.recyclerTargetOfficers.layoutManager = LinearLayoutManager(this)
        dialogBinding.recyclerTargetOfficers.adapter = targetOfficerAdapter

        fun applyFilterAndSearch() {
            val query = dialogBinding.etSearchTargetOfficer.text.toString().trim()
            val selectedMonthPos = dialogBinding.spinnerTargetFilterMonth.selectedItemPosition
            val filterMonth = when (selectedMonthPos) {
                0 -> currentMonthName
                1 -> null
                else -> filterMonths[selectedMonthPos]
            }

            val filteredOfficers = if (query.isEmpty()) {
                allApprovedOfficers
            } else {
                allApprovedOfficers.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.employeeId.contains(query, ignoreCase = true) ||
                    it.branch.contains(query, ignoreCase = true) ||
                    it.department.contains(query, ignoreCase = true)
                }
            }

            var totalTargetSum = 0
            var totalAchievedSum = 0
            var officersWithTargetCount = 0

            val items = filteredOfficers.map { officer ->
                val target = officer.monthlyTarget
                val achieved = TargetUtils.countCards(allReports, officer.employeeId, filterMonth)
                val rate = TargetUtils.calculateAchievementRate(achieved, target)

                if (target > 0) {
                    totalTargetSum += target
                    officersWithTargetCount++
                }
                totalAchievedSum += achieved

                TargetOfficerItem(officer, target, achieved, rate)
            }

            targetOfficerAdapter.updateList(items)

            if (items.isEmpty()) {
                dialogBinding.layoutTargetEmptyState.visibility = View.VISIBLE
                dialogBinding.recyclerTargetOfficers.visibility = View.GONE
            } else {
                dialogBinding.layoutTargetEmptyState.visibility = View.GONE
                dialogBinding.recyclerTargetOfficers.visibility = View.VISIBLE
            }

            val teamOverallRate = TargetUtils.calculateAchievementRate(totalAchievedSum, totalTargetSum)
            dialogBinding.tvTeamTotalTarget.text = "$totalTargetSum Cards"
            dialogBinding.tvTeamTotalAchieved.text = "$totalAchievedSum Cards"
            dialogBinding.tvTeamTargetOfficersCount.text = "$officersWithTargetCount Officers"
            dialogBinding.tvTeamOverallPercent.text = TargetUtils.formatAchievementRate(totalAchievedSum, totalTargetSum)
            val teamColor = TargetUtils.getAchievementColor(totalAchievedSum, totalTargetSum)
            dialogBinding.tvTeamOverallPercent.setTextColor(teamColor)
            dialogBinding.progressTeamOverall.progress = teamOverallRate.toInt().coerceIn(0, 100)
            dialogBinding.progressTeamOverall.setIndicatorColor(teamColor)
        }

        dialogBinding.spinnerTargetFilterMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                applyFilterAndSearch()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        dialogBinding.etSearchTargetOfficer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilterAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadTargetDataForDialog(dialogBinding, currentMonthName, targetOfficerAdapter) { officers ->
            allApprovedOfficers = officers
            applyFilterAndSearch()
        }

        dialog.show()
    }

    private fun loadTargetDataForDialog(
        dialogBinding: DialogTargetManagementBinding,
        currentMonth: String,
        adapter: TargetOfficerAdapter,
        onLoaded: (List<User>) -> Unit
    ) {
        db.collection("employees")
            .whereEqualTo("status", "Approved")
            .get()
            .addOnSuccessListener { snapshot ->
                val officers = snapshot.toObjects(User::class.java).sortedBy { it.name }
                onLoaded(officers)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load officers", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSetTargetDialog(user: User, initialMonth: String = "", onSaved: (() -> Unit)? = null) {
        val targetBinding = DialogSetTargetBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(targetBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        targetBinding.tvSetTargetEmployee.text = "${user.name} (ID: ${user.employeeId})"

        val periodOptions = listOf("Monthly Target (All Months)") + monthsList
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, periodOptions)
        targetBinding.spinnerTargetMonth.adapter = spinnerAdapter

        if (initialMonth.isNotBlank()) {
            val idx = periodOptions.indexOfFirst { it.equals(initialMonth, ignoreCase = true) }
            if (idx >= 0) targetBinding.spinnerTargetMonth.setSelection(idx)
        }

        if (user.monthlyTarget > 0) {
            targetBinding.etTargetCards.setText(user.monthlyTarget.toString())
        }

        targetBinding.btnCancelTarget.setOnClickListener { dialog.dismiss() }

        targetBinding.btnSaveTarget.setOnClickListener {
            val targetStr = targetBinding.etTargetCards.text.toString().trim()
            val targetVal = targetStr.toIntOrNull()
            if (targetVal == null || targetVal < 0) {
                targetBinding.tilTargetCards.error = "Please enter a valid target count"
                return@setOnClickListener
            }

            targetBinding.tilTargetCards.error = null
            val selectedPeriod = periodOptions[targetBinding.spinnerTargetMonth.selectedItemPosition]
            val monthToSave = if (selectedPeriod.startsWith("Monthly Target")) "General" else selectedPeriod

            targetBinding.btnSaveTarget.isEnabled = false
            TargetUtils.saveTarget(
                employeeId = user.employeeId,
                employeeName = user.name,
                month = monthToSave,
                targetCards = targetVal,
                adminName = userName.ifBlank { "Admin" },
                onSuccess = {
                    Toast.makeText(this, "Target set to $targetVal cards for ${user.name}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    updateTeamTargetOverview()
                    onSaved?.invoke()
                },
                onFailure = { err ->
                    targetBinding.btnSaveTarget.isEnabled = true
                    Toast.makeText(this, "Failed to save target: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        dialog.show()
    }

    private fun showUserTargetDetailsDialog(user: User) {
        val detailsBinding = com.performance.tracker.databinding.DialogUserTargetDetailsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(detailsBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        detailsBinding.tvUserTargetDetailsTitle.text = "${user.name}'s Target & Progress"
        detailsBinding.tvUserTargetDetailsSubtitle.text = "ID: ${user.employeeId} | ${user.branch.ifBlank { user.displayDepartment }}"

        detailsBinding.btnCloseUserTargetDetails.setOnClickListener { dialog.dismiss() }

        val currentMonthName = SimpleDateFormat("MMMM", Locale.US).format(Date())
        detailsBinding.tvDetailCurrentMonthName.text = "$currentMonthName Target"

        val target = user.monthlyTarget
        val currentAchieved = TargetUtils.countCards(allReports, user.employeeId, currentMonthName)
        val currentRate = TargetUtils.calculateAchievementRate(currentAchieved, target)

        detailsBinding.tvDetailCurrentTargetCount.text = "Target: $target Cards"
        detailsBinding.tvDetailCurrentAchievedCount.text = "Achieved: $currentAchieved Cards"
        detailsBinding.tvDetailCurrentAchievementPercent.text = TargetUtils.formatAchievementRate(currentAchieved, target)

        val currentColor = TargetUtils.getAchievementColor(currentAchieved, target)
        detailsBinding.tvDetailCurrentAchievementPercent.setTextColor(currentColor)
        detailsBinding.progressDetailCurrent.progress = currentRate.toInt().coerceIn(0, 100)
        detailsBinding.progressDetailCurrent.setIndicatorColor(currentColor)

        val monthlyItems = monthsList.map { m ->
            val ach = TargetUtils.countCards(allReports, user.employeeId, m)
            val r = TargetUtils.calculateAchievementRate(ach, target)
            com.performance.tracker.adapter.MonthTargetItem(m, target, ach, r)
        }

        val monthAdapter = com.performance.tracker.adapter.UserTargetMonthAdapter(monthlyItems)
        detailsBinding.recyclerUserTargetMonths.layoutManager = LinearLayoutManager(this)
        detailsBinding.recyclerUserTargetMonths.adapter = monthAdapter

        dialog.show()
    }
}
