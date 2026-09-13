package com.performance.tracker.ui

import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var allRawReports: List<Performance> = ArrayList()

    private val monthsList = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val shortMonthsList = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun getMonthIndex(monthStr: String): Int {
        val clean = monthStr.trim()
        val idx = monthsList.indexOfFirst {
            it.equals(clean, ignoreCase = true) || clean.startsWith(it, ignoreCase = true) || clean.contains(it, ignoreCase = true)
        }
        return if (idx >= 0) idx else 999
    }

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
        setupExitNavigation()
        
        if (userRole.equals("Sales Manager", ignoreCase = true)) {
            binding.tvHeaderTitle.text = "Manager Dashboard"
        } else {
            binding.tvHeaderTitle.text = "Control Panel ($userRole)"
        }
        
        loadDashboardData()
        checkNotificationPermission()
        setupSyncStatus()
        
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

    override fun onResume() {
        super.onResume()
        checkSettingsRedDot()
        com.performance.tracker.util.UpdateManager.checkForAppUpdate(this)
        com.performance.tracker.util.OfflineSyncManager.syncPendingReports(this, showIndicator = false)
    }

    private fun setupSyncStatus() {
        binding.btnAdminSyncStatus.setOnClickListener {
            binding.ivAdminSyncIcon.animate().rotationBy(360f).setDuration(600).start()
            com.performance.tracker.util.OfflineSyncManager.performFullSync(this, showIndicator = true)
        }

        com.performance.tracker.util.OfflineSyncManager.syncState.observe(this) { state ->
            when (state) {
                is com.performance.tracker.util.OfflineSyncManager.SyncState.Syncing -> {
                    binding.ivAdminSyncIcon.setColorFilter(Color.parseColor("#3B82F6"))
                    binding.ivAdminSyncIcon.animate().rotationBy(180f).setDuration(400).start()
                }
                is com.performance.tracker.util.OfflineSyncManager.SyncState.Synced -> {
                    binding.ivAdminSyncIcon.setColorFilter(Color.parseColor("#15803D"))
                }
                is com.performance.tracker.util.OfflineSyncManager.SyncState.Error -> {
                    binding.ivAdminSyncIcon.setColorFilter(Color.parseColor("#DC2626"))
                }
                is com.performance.tracker.util.OfflineSyncManager.SyncState.Idle -> {
                    binding.ivAdminSyncIcon.setColorFilter(Color.parseColor("#15803D"))
                }
            }
        }
    }

    private fun checkSettingsRedDot() {
        val email = com.performance.tracker.util.SessionManager.getUserEmail(this)
        if (email.isNotBlank()) {
            binding.viewAdminSettingsRedDot.visibility = View.GONE
        } else {
            val userId = intent.getStringExtra("USER_ID") ?: com.performance.tracker.util.SessionManager.getUserId(this)
            if (userId.isNotEmpty()) {
                db.collection("employees").document(userId).get()
                    .addOnSuccessListener { doc ->
                        val remoteEmail = doc.getString("email") ?: ""
                        if (remoteEmail.isNotBlank()) {
                            com.performance.tracker.util.SessionManager.saveUser(this, userId, userRole, userName, remoteEmail)
                            binding.viewAdminSettingsRedDot.visibility = View.GONE
                        } else {
                            binding.viewAdminSettingsRedDot.visibility = View.VISIBLE
                        }
                    }
                    .addOnFailureListener {
                        binding.viewAdminSettingsRedDot.visibility = View.VISIBLE
                    }
            } else {
                binding.viewAdminSettingsRedDot.visibility = View.VISIBLE
            }
        }
    }

    private fun loadDashboardData() {
        binding.progressBar.visibility = View.VISIBLE
        fetchTargetsCache()
        viewModel.getAllReports()
        viewModel.reportList.observe(this) { list ->
            binding.progressBar.visibility = View.GONE
            if (list != null && list.isNotEmpty()) {
                allRawReports = list
                com.performance.tracker.util.OfflineSyncManager.cacheFirestoreReports(this@AdminDashboardActivity, list, notifyUser = false)
                val cleanRole = userRole.trim()
                val isSalesManager = cleanRole.equals("Sales Manager", ignoreCase = true)
                
                if (isSalesManager) {
                    allReports = list.filter { 
                        it.salesManager.trim().equals(userName.trim(), ignoreCase = true) 
                    }
                } else {
                    // AGM, DGM, ADMIN and all management accounts load ALL reports automatically
                    allReports = list
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
        }.sortedWith(
            compareBy<ReportSummary> { getMonthIndex(it.month) }
                .thenBy { it.employeeName }
        )
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
        val cleanRole = userRole.trim()
        val isAdmin = cleanRole.equals("ADMIN", ignoreCase = true)
        val isAgmOrDgm = cleanRole.equals("AGM", ignoreCase = true) || cleanRole.equals("DGM", ignoreCase = true)
        val isSalesManager = cleanRole.equals("Sales Manager", ignoreCase = true)
        val canEdit = isAdmin || isAgmOrDgm || isSalesManager
        val canDelete = isAdmin || isAgmOrDgm

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
            if (allReports.isEmpty()) {
                Toast.makeText(this, "No performance data available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showMonthSelectionGrid()
        }
        binding.btnFilterRanking?.setOnClickListener {
            if (allReports.isEmpty()) {
                Toast.makeText(this, "No performance data available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showRankingDialog()
        }
        binding.btnFilterIndividual.setOnClickListener { 
            if (allReports.isEmpty()) {
                Toast.makeText(this, "No performance data available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val uniqueEmployees = allReports.distinctBy { it.employeeId }.sortedBy { it.employeeName }
            showEmployeeListDialog("Individual Officers", "Select an officer to view full performance analytics", uniqueEmployees)
        }
        binding.btnFilterZone.setOnClickListener { 
            if (allReports.isEmpty()) {
                Toast.makeText(this, "No performance data available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showZoneDialog()
        }
        binding.btnFilterTeam.setOnClickListener { 
            if (allReports.isEmpty()) {
                Toast.makeText(this, "No performance data available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showManagerTeamDialog()
        }

        binding.btnOfficerList.setOnClickListener { startActivity(Intent(this, OfficerListActivity::class.java)) }
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
            
            val isSalesManager = userRole.trim().equals("Sales Manager", ignoreCase = true)
            val approvedUsers = allEmployees.filter { 
                it.role.equals("USER", ignoreCase = true) && 
                (it.status.isBlank() || it.status.equals("Approved", ignoreCase = true)) &&
                (!isSalesManager || it.salesManager.trim().equals(userName.trim(), ignoreCase = true))
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
    private val cachedOfficerYearlyTargets = mutableMapOf<String, Int>()
    private val cachedOfficerMonthlyTargets = mutableMapOf<String, Int>()
    private val cachedEmployeesList = mutableListOf<com.performance.tracker.model.User>()

    private fun fetchTargetsCache() {
        db.collection("employees").get().addOnSuccessListener { snapshot ->
            cachedEmployeesList.clear()
            val users = snapshot.toObjects(com.performance.tracker.model.User::class.java)
            cachedEmployeesList.addAll(users)
            snapshot.documents.forEach { doc ->
                val empId = doc.getString("employeeId") ?: ""
                val monthly = doc.getLong("monthlyTarget")?.toInt() ?: 0
                val yearly = doc.getLong("yearlyTarget")?.toInt() ?: 0
                val calculatedYearly = if (yearly > 0) yearly else if (monthly > 0) monthly * 12 else 0
                if (empId.isNotBlank() && monthly > 0) {
                    cachedOfficerMonthlyTargets[empId] = monthly
                }
                if (empId.isNotBlank() && calculatedYearly > 0) {
                    cachedOfficerYearlyTargets[empId] = calculatedYearly
                }
            }
        }
    }

    private fun showExportDialog(defaultFormatIsPdf: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_report, null)
        val spinnerFrom = dialogView.findViewById<Spinner>(R.id.spinnerFromMonth)
        val spinnerTo = dialogView.findViewById<Spinner>(R.id.spinnerToMonth)
        val cbAllMonths = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbAllMonths)
        val tvRecordCount = dialogView.findViewById<TextView>(R.id.tvExportRecordCount)
        val layoutMonthSpinners = dialogView.findViewById<View>(R.id.layoutMonthSpinners)
        val btnDownload = dialogView.findViewById<Button>(R.id.btnDownloadReport)
        val btnShare = dialogView.findViewById<Button>(R.id.btnShareReport)
        val btnPreview = dialogView.findViewById<Button>(R.id.btnPreviewReport)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvExportTitle)
        val layoutFormatSelection = dialogView.findViewById<View>(R.id.layoutFormatSelection)
        val layoutPdfContentOptions = dialogView.findViewById<View>(R.id.layoutPdfContentOptions)
        val layoutExcelContentOptions = dialogView.findViewById<View>(R.id.layoutExcelContentOptions)
        val rbSummary = dialogView.findViewById<RadioButton>(R.id.rbSummary)
        val rbSalesManagerReport = dialogView.findViewById<RadioButton>(R.id.rbSalesManagerReport)
        val rbDetails = dialogView.findViewById<RadioButton>(R.id.rbDetails)
        val layoutPdfColumns = dialogView.findViewById<View>(R.id.layoutPdfColumns)
        val cbPdfZone = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbPdfZone)
        val cbPdfSalesManager = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbPdfSalesManager)
        val rbExcelOfficerPerformance = dialogView.findViewById<RadioButton>(R.id.rbExcelOfficerPerformance)
        val rbExcelSalesManagerReport = dialogView.findViewById<RadioButton>(R.id.rbExcelSalesManagerReport)
        val cbExcelZone = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbExcelZone)
        val cbExcelSalesManager = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbExcelSalesManager)
        val cbExcelApplicantDetails = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbExcelApplicantDetails)

        // Initialize visibility based on default checked state
        if (rbSalesManagerReport?.isChecked == true) {
            layoutPdfColumns?.visibility = View.GONE
            cbPdfZone?.visibility = View.GONE
            cbPdfSalesManager?.visibility = View.GONE
        }
        if (rbExcelSalesManagerReport?.isChecked == true) {
            cbExcelZone?.visibility = View.GONE
            cbExcelSalesManager?.visibility = View.GONE
        }

        // Always hide format selection radio group as per requirement
        layoutFormatSelection.visibility = View.GONE

        // Refresh targets from Firestore cache
        fetchTargetsCache()

        if (defaultFormatIsPdf) {
            tvTitle.text = "Monthly Performance Report (PDF)"
            layoutPdfContentOptions.visibility = View.VISIBLE
            layoutExcelContentOptions.visibility = View.GONE
            btnDownload.text = "Download PDF"
            btnShare.text = "Share PDF"
        } else {
            tvTitle.text = "Export Performance Report (Excel)"
            layoutPdfContentOptions.visibility = View.GONE
            layoutExcelContentOptions.visibility = View.VISIBLE
            btnDownload.text = "Download Excel"
            btnShare.text = "Share Excel"
        }

        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monthsList)
        spinnerFrom.adapter = monthAdapter
        spinnerTo.adapter = monthAdapter

        val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
        val availableMonthsInReports = allReports.map { it.month.trim() }.filter { it.isNotBlank() }.distinct()

        val defaultMonth = if (availableMonthsInReports.contains(currentMonth) || availableMonthsInReports.isEmpty()) {
            currentMonth
        } else {
            availableMonthsInReports.first()
        }

        val monthIdx = monthsList.indexOf(defaultMonth)
        if (monthIdx >= 0) {
            spinnerFrom.setSelection(monthIdx)
            spinnerTo.setSelection(monthIdx)
        }

        fun getSelectedData(): List<Performance> {
            val isSmSelected = (defaultFormatIsPdf && rbSalesManagerReport?.isChecked == true) ||
                                (!defaultFormatIsPdf && rbExcelSalesManagerReport?.isChecked == true)
            val base = if (isSmSelected && allRawReports.isNotEmpty()) allRawReports else allReports
            return if (cbAllMonths.isChecked) {
                base
            } else {
                val fromIdx = spinnerFrom.selectedItemPosition
                val toIdx = spinnerTo.selectedItemPosition
                if (fromIdx in monthsList.indices && toIdx in monthsList.indices && fromIdx <= toIdx) {
                    val selected = monthsList.subList(fromIdx, toIdx + 1)
                    base.filter { r ->
                        selected.any { sm -> sm.equals(r.month.trim(), ignoreCase = true) || r.month.contains(sm, ignoreCase = true) }
                    }
                } else emptyList()
            }
        }

        fun updateLiveRecordCount() {
            val matching = getSelectedData()
            if (matching.isEmpty()) {
                tvRecordCount.text = "⚠️ 0 records found in this range"
                tvRecordCount.setTextColor(Color.parseColor("#D32F2F"))
            } else {
                tvRecordCount.text = "✓ Found ${matching.size} records ready to export"
                tvRecordCount.setTextColor(Color.parseColor("#2E7D32"))
            }
        }

        rbSalesManagerReport?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutPdfColumns?.visibility = View.GONE
                cbPdfSalesManager?.visibility = View.GONE
                cbPdfZone?.visibility = View.GONE
            }
            updateLiveRecordCount()
        }
        rbSummary?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                layoutPdfColumns?.visibility = View.VISIBLE
                cbPdfSalesManager?.visibility = View.VISIBLE
                cbPdfZone?.visibility = View.VISIBLE
            }
            updateLiveRecordCount()
        }
        rbDetails?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) layoutPdfColumns?.visibility = View.GONE
            updateLiveRecordCount()
        }

        rbExcelSalesManagerReport?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                cbExcelSalesManager?.visibility = View.GONE
                cbExcelZone?.visibility = View.GONE
            }
            updateLiveRecordCount()
        }
        rbExcelOfficerPerformance?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                cbExcelSalesManager?.visibility = View.VISIBLE
                cbExcelZone?.visibility = View.VISIBLE
            }
            updateLiveRecordCount()
        }

        cbAllMonths.setOnCheckedChangeListener { _, isChecked ->
            layoutMonthSpinners.visibility = if (isChecked) View.GONE else View.VISIBLE
            updateLiveRecordCount()
        }

        val monthSelectListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                updateLiveRecordCount()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        spinnerFrom.onItemSelectedListener = monthSelectListener
        spinnerTo.onItemSelectedListener = monthSelectListener

        updateLiveRecordCount()

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val handleExport = { isShare: Boolean ->
            val isAll = cbAllMonths.isChecked
            val fromMonth = spinnerFrom.selectedItem.toString()
            val toMonth = spinnerTo.selectedItem.toString()
            val fromIdx = spinnerFrom.selectedItemPosition
            val toIdx = spinnerTo.selectedItemPosition

            if (!isAll && fromIdx > toIdx) {
                Toast.makeText(this, "Invalid Range: 'From Month' cannot be after 'To Month'", Toast.LENGTH_LONG).show()
            } else {
                val selectedMonths = if (isAll) monthsList else monthsList.subList(fromIdx, toIdx + 1)
                val isPdfSummary = rbSummary.isChecked
                val includeZone = cbExcelZone.isChecked
                val includeSalesManager = cbExcelSalesManager.isChecked
                val includeApplicantDetails = cbExcelApplicantDetails.isChecked

                dialog.dismiss()

                val isSalesManagerPdf = defaultFormatIsPdf && (rbSalesManagerReport?.isChecked == true)
                val isSalesManagerExcel = !defaultFormatIsPdf && (rbExcelSalesManagerReport?.isChecked == true)
                val isSalesManagerMode = isSalesManagerPdf || isSalesManagerExcel
                val baseData = if (isSalesManagerMode && allRawReports.isNotEmpty()) allRawReports else allReports

                val filteredData = if (isAll) {
                    baseData
                } else {
                    baseData.filter { r ->
                        selectedMonths.any { sm -> sm.equals(r.month.trim(), ignoreCase = true) || r.month.contains(sm, ignoreCase = true) }
                    }
                }

                if (filteredData.isEmpty()) {
                    Toast.makeText(this, "No records found for the selected period", Toast.LENGTH_SHORT).show()
                } else {
                    val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
                    val monthRange = if (isAll) "All Months $year" else if (fromMonth == toMonth) "$fromMonth $year" else "$fromMonth to $toMonth $year"
                    val monthCount = if (isAll) 12 else (toIdx - fromIdx + 1).coerceAtLeast(1)

                    if (defaultFormatIsPdf) {
                        val includePdfDetails = rbDetails?.isChecked == true
                        val pdfIncludeZone = if (isSalesManagerPdf) false else (cbPdfZone?.isChecked ?: true)
                        val pdfIncludeSalesManager = if (isSalesManagerPdf) false else (cbPdfSalesManager?.isChecked ?: false)
                        ReportExporter.generateMonthlyPdf(
                            context = this,
                            data = filteredData,
                            monthRange = monthRange,
                            managerName = if (isSalesManagerPdf) "" else if (userRole.equals("Sales Manager", ignoreCase = true)) userName else "",
                            isSummary = isPdfSummary,
                            isShare = isShare,
                            officerMonthlyTargets = cachedOfficerMonthlyTargets,
                            officerYearlyTargets = cachedOfficerYearlyTargets,
                            monthCount = monthCount,
                            includeDetails = includePdfDetails,
                            includeZone = pdfIncludeZone,
                            includeSalesManager = pdfIncludeSalesManager,
                            isSalesManagerReport = isSalesManagerPdf,
                            allEmployees = cachedEmployeesList
                        )
                    } else {
                        val excelIncludeZone = if (isSalesManagerExcel) false else cbExcelZone.isChecked
                        val excelIncludeSalesManager = if (isSalesManagerExcel) false else includeSalesManager
                        ReportExporter.exportPerformanceXlsx(
                            context = this,
                            data = filteredData,
                            monthRange = monthRange,
                            officerTargets = cachedOfficerMonthlyTargets,
                            officerYearlyTargets = cachedOfficerYearlyTargets,
                            includeZone = excelIncludeZone,
                            includeSalesManager = excelIncludeSalesManager,
                            includeApplicantDetails = includeApplicantDetails,
                            isShare = isShare,
                            isSalesManagerReport = isSalesManagerExcel,
                            monthCount = monthCount,
                            allEmployees = cachedEmployeesList,
                            managerName = if (isSalesManagerExcel) "" else if (userRole.equals("Sales Manager", ignoreCase = true)) userName else ""
                        )
                    }
                }
            }
        }

        btnDownload.setOnClickListener { handleExport(false) }
        btnShare.setOnClickListener { handleExport(true) }

        btnPreview?.setOnClickListener {
            val isAll = cbAllMonths.isChecked
            val fromMonth = spinnerFrom.selectedItem.toString()
            val toMonth = spinnerTo.selectedItem.toString()
            val fromIdx = spinnerFrom.selectedItemPosition
            val toIdx = spinnerTo.selectedItemPosition

            if (!isAll && fromIdx > toIdx) {
                Toast.makeText(this, "Invalid Range: 'From Month' cannot be after 'To Month'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val selectedMonths = if (isAll) monthsList else monthsList.subList(fromIdx, toIdx + 1)
            val isPdfSummary = rbSummary.isChecked
            val isSalesManagerPdf = defaultFormatIsPdf && (rbSalesManagerReport?.isChecked == true)
            val isSalesManagerExcel = !defaultFormatIsPdf && (rbExcelSalesManagerReport?.isChecked == true)
            val isSalesManagerMode = isSalesManagerPdf || isSalesManagerExcel
            val baseData = if (isSalesManagerMode && allRawReports.isNotEmpty()) allRawReports else allReports

            val filteredData = if (isAll) {
                baseData
            } else {
                baseData.filter { r ->
                    selectedMonths.any { sm -> sm.equals(r.month.trim(), ignoreCase = true) || r.month.contains(sm, ignoreCase = true) }
                }
            }

            if (filteredData.isEmpty()) {
                Toast.makeText(this, "No records found for the selected period", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            val monthRange = if (isAll) "All Months $year" else if (fromMonth == toMonth) "$fromMonth $year" else "$fromMonth to $toMonth $year"
            val monthCount = if (isAll) 12 else (toIdx - fromIdx + 1).coerceAtLeast(1)

            val includePdfDetails = rbDetails?.isChecked == true
            val pdfIncludeZone = if (isSalesManagerPdf) false else (cbPdfZone?.isChecked ?: true)
            val pdfIncludeSalesManager = if (isSalesManagerPdf) false else (cbPdfSalesManager?.isChecked ?: false)

            ReportPreviewActivity.previewDataHolder = filteredData
            ReportPreviewActivity.previewEmployeesHolder = cachedEmployeesList
            ReportPreviewActivity.previewMonthlyTargets = cachedOfficerMonthlyTargets
            ReportPreviewActivity.previewYearlyTargets = cachedOfficerYearlyTargets

            val previewIntent = Intent(this, ReportPreviewActivity::class.java).apply {
                putExtra(ReportPreviewActivity.EXTRA_MONTH_RANGE, monthRange)
                putExtra(ReportPreviewActivity.EXTRA_MANAGER_NAME, if (isSalesManagerPdf) "" else if (userRole.equals("Sales Manager", ignoreCase = true)) userName else "")
                putExtra(ReportPreviewActivity.EXTRA_IS_SUMMARY, isPdfSummary)
                putExtra(ReportPreviewActivity.EXTRA_IS_SALES_MANAGER_REPORT, isSalesManagerPdf)
                putExtra(ReportPreviewActivity.EXTRA_INCLUDE_DETAILS, includePdfDetails)
                putExtra(ReportPreviewActivity.EXTRA_INCLUDE_ZONE, pdfIncludeZone)
                putExtra(ReportPreviewActivity.EXTRA_INCLUDE_SALES_MANAGER, pdfIncludeSalesManager)
                putExtra(ReportPreviewActivity.EXTRA_MONTH_COUNT, monthCount)
            }

            startActivity(previewIntent)
        }

        dialog.show()
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
            binding.tvFilterStatus.text = "Viewing: $month (0 Employee)"
            binding.tvFilterStatus.setTextColor(Color.RED)
        } else {
            val empText = if (count == 1) "1 Employee" else "$count Employee"
            binding.tvFilterStatus.text = "Viewing: $month ($empText)"
            binding.tvFilterStatus.setTextColor(Color.parseColor("#3F51B5"))
        }
    }

    private fun filterDashboardByEmployee(empId: String, empName: String) {
        val filtered = allReports.filter { it.employeeId == empId }
        val groupedList = filtered.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
            val first = performances.first()
            val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
            ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
        }.sortedBy { getMonthIndex(it.month) }
        adapter.updateList(groupedList)
        
        val count = groupedList.size
        if (count == 0) {
            binding.tvFilterStatus.text = "Viewing: $empName (0 months)"
            binding.tvFilterStatus.setTextColor(Color.RED)
        } else {
            val monthText = if (count == 1) "1 month" else "$count months"
            binding.tvFilterStatus.text = "Viewing: $empName ($monthText)"
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
        val dialog = Dialog(this, R.style.Theme_FullScreenDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_fullscreen_ranking, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#F8FAFC")))
        }

        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnRankingClose)
        val tvTotalRankedCount = dialogView.findViewById<TextView>(R.id.tvTotalRankedCount)
        val tvPodiumRank1Name = dialogView.findViewById<TextView>(R.id.tvPodiumRank1Name)
        val tvPodiumRank1Score = dialogView.findViewById<TextView>(R.id.tvPodiumRank1Score)
        val tvPodiumRank2Name = dialogView.findViewById<TextView>(R.id.tvPodiumRank2Name)
        val tvPodiumRank2Score = dialogView.findViewById<TextView>(R.id.tvPodiumRank2Score)
        val tvPodiumRank3Name = dialogView.findViewById<TextView>(R.id.tvPodiumRank3Name)
        val tvPodiumRank3Score = dialogView.findViewById<TextView>(R.id.tvPodiumRank3Score)
        val etSearch = dialogView.findViewById<EditText>(R.id.etRankingSearch)
        val btnClearSearch = dialogView.findViewById<ImageButton>(R.id.btnClearRankingSearch)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerFullscreenRanking)
        val layoutEmpty = dialogView.findViewById<LinearLayout>(R.id.layoutRankingEmpty)

        btnClose.setOnClickListener { dialog.dismiss() }

        // Group and rank all officers across all reports
        val reportsToRank = viewModel.reportList.value?.ifEmpty { allReports } ?: allReports
        val officerReportsMap = reportsToRank.groupBy { it.employeeName.trim() }
        val rawRanked = officerReportsMap.map { (_, reports) ->
            val sample = reports.first()
            val totalCards = reports.size
            Pair(sample, totalCards)
        }.sortedByDescending { it.second }

        val rankedList = rawRanked.mapIndexed { index, pair ->
            ModernRankingEntry(
                rank = index + 1,
                employeeId = pair.first.employeeId,
                employeeName = pair.first.employeeName,
                branch = pair.first.branch,
                zone = pair.first.zone,
                score = pair.second
            )
        }

        tvTotalRankedCount.text = "${rankedList.size} Officers"

        // Populate Podium Highlights
        if (rankedList.isNotEmpty()) {
            val first = rankedList[0]
            tvPodiumRank1Name.text = first.employeeName
            tvPodiumRank1Score.text = "${first.score} cards"
        }
        if (rankedList.size > 1) {
            val second = rankedList[1]
            tvPodiumRank2Name.text = second.employeeName
            tvPodiumRank2Score.text = "${second.score} cards"
        }
        if (rankedList.size > 2) {
            val third = rankedList[2]
            tvPodiumRank3Name.text = third.employeeName
            tvPodiumRank3Score.text = "${third.score} cards"
        }

        val adapter = FullscreenRankingAdapter(rankedList) { selectedEntry ->
            dialog.dismiss()
            filterDashboardByEmployee(selectedEntry.employeeId, selectedEntry.employeeName)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                val filtered = if (query.isEmpty()) {
                    rankedList
                } else {
                    rankedList.filter {
                        it.employeeName.contains(query, ignoreCase = true) ||
                        it.employeeId.contains(query, ignoreCase = true) ||
                        it.branch.contains(query, ignoreCase = true) ||
                        it.zone.contains(query, ignoreCase = true)
                    }
                }
                adapter.updateList(filtered)
                layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        btnClearSearch.setOnClickListener { etSearch.setText("") }
        dialog.show()
    }

    private fun showManagerTeamDialog() {
        val dialog = Dialog(this, R.style.Theme_FullScreenDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_fullscreen_filter, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#F8FAFC")))
        }

        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnFilterClose)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvFilterHeaderTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvFilterHeaderSubtitle)
        val tvItemCount = dialogView.findViewById<TextView>(R.id.tvFilterItemCount)
        val etSearch = dialogView.findViewById<EditText>(R.id.etFilterSearch)
        val btnClearSearch = dialogView.findViewById<ImageButton>(R.id.btnClearFilterSearch)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerFullscreenFilter)
        val layoutEmpty = dialogView.findViewById<LinearLayout>(R.id.layoutFilterEmpty)

        tvTitle.text = "Sales Managers & Teams"
        tvSubtitle.text = "Select a manager to view team members and performance"
        btnClose.setOnClickListener { dialog.dismiss() }

        val reportsSource = if (allRawReports.isNotEmpty()) allRawReports else allReports
        val managerGroups = reportsSource.filter { 
            it.salesManager.isNotBlank() && 
            !it.salesManager.equals("N/A", ignoreCase = true)
        }.groupBy { it.salesManager.trim() }

        val categoryList = managerGroups.map { (managerName, reports) ->
            val officers = reports.distinctBy { it.employeeId }
            val totalCards = reports.size
            val branches = officers.map { it.branch }.filter { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }.distinct().take(3).joinToString(", ")
            val branchText = if (branches.isNotBlank()) " • $branches" else ""
            ModernCategoryEntry(
                name = managerName,
                officersCount = officers.size,
                totalCards = totalCards,
                branches = "${officers.size} Officers • $totalCards Cards$branchText",
                iconRes = R.drawable.ic_supervisor
            )
        }.sortedByDescending { it.totalCards }

        tvItemCount.text = "${categoryList.size} Managers"

        val adapter = FullscreenCategoryAdapter(categoryList) { selectedCategory ->
            val empUnderManager = reportsSource.filter { it.salesManager.equals(selectedCategory.name, ignoreCase = true) }
                .distinctBy { it.employeeId }

            MaterialAlertDialogBuilder(this)
                .setTitle("Team: ${selectedCategory.name}")
                .setMessage("Choose an action for Manager ${selectedCategory.name}:")
                .setPositiveButton("View Officers (${empUnderManager.size})") { _, _ ->
                    showEmployeeListDialog(
                        title = "Team: ${selectedCategory.name}",
                        subtitle = "Total ${empUnderManager.size} active officers under this manager",
                        employeeList = empUnderManager,
                        parentDialog = dialog
                    )
                }
                .setNeutralButton("Filter Main Dashboard") { _, _ ->
                    dialog.dismiss()
                    filterDashboardByManager(selectedCategory.name)
                }
                .setNegativeButton("Cancel") { dialogInterface, _ -> dialogInterface.dismiss() }
                .show()
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        etSearch.hint = "Search manager name or branch..."
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                val filtered = if (query.isEmpty()) {
                    categoryList
                } else {
                    categoryList.filter {
                        it.name.contains(query, ignoreCase = true) ||
                        it.branches.contains(query, ignoreCase = true)
                    }
                }
                adapter.updateList(filtered)
                layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        btnClearSearch.setOnClickListener { etSearch.setText("") }
        dialog.show()
    }

    private fun showZoneDialog() {
        val dialog = Dialog(this, R.style.Theme_FullScreenDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_fullscreen_filter, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#F8FAFC")))
        }

        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnFilterClose)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvFilterHeaderTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvFilterHeaderSubtitle)
        val tvItemCount = dialogView.findViewById<TextView>(R.id.tvFilterItemCount)
        val etSearch = dialogView.findViewById<EditText>(R.id.etFilterSearch)
        val btnClearSearch = dialogView.findViewById<ImageButton>(R.id.btnClearFilterSearch)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerFullscreenFilter)
        val layoutEmpty = dialogView.findViewById<LinearLayout>(R.id.layoutFilterEmpty)

        tvTitle.text = "Regional Zones"
        tvSubtitle.text = "Select a regional zone to inspect branch performance"
        btnClose.setOnClickListener { dialog.dismiss() }

        val zoneGroups = allReports.filter { it.zone.isNotBlank() && !it.zone.equals("N/A", ignoreCase = true) }
            .groupBy { it.zone.trim() }

        val categoryList = zoneGroups.map { (zoneName, reports) ->
            val officers = reports.distinctBy { it.employeeId }
            val totalCards = reports.size
            val branches = officers.map { it.branch }.filter { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }.distinct().take(3).joinToString(", ")
            val branchText = if (branches.isNotBlank()) " • $branches" else ""
            ModernCategoryEntry(
                name = zoneName,
                officersCount = officers.size,
                totalCards = totalCards,
                branches = "${officers.size} Officers • $totalCards Cards$branchText",
                iconRes = R.drawable.ic_location
            )
        }.sortedByDescending { it.totalCards }

        tvItemCount.text = "${categoryList.size} Zones"

        val adapter = FullscreenCategoryAdapter(categoryList) { selectedCategory ->
            val empInZone = allReports.filter { it.zone.equals(selectedCategory.name, ignoreCase = true) }
                .distinctBy { it.employeeId }

            MaterialAlertDialogBuilder(this)
                .setTitle("Zone: ${selectedCategory.name}")
                .setMessage("Choose an action for Zone ${selectedCategory.name}:")
                .setPositiveButton("View Officers (${empInZone.size})") { _, _ ->
                    showEmployeeListDialog(
                        title = "Zone: ${selectedCategory.name}",
                        subtitle = "Total ${empInZone.size} officers operating in this zone",
                        employeeList = empInZone,
                        parentDialog = dialog
                    )
                }
                .setNeutralButton("Filter Main Dashboard") { _, _ ->
                    dialog.dismiss()
                    filterDashboardByZone(selectedCategory.name)
                }
                .setNegativeButton("Cancel") { dialogInterface, _ -> dialogInterface.dismiss() }
                .show()
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        etSearch.hint = "Search zone name or branch..."
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                val filtered = if (query.isEmpty()) {
                    categoryList
                } else {
                    categoryList.filter {
                        it.name.contains(query, ignoreCase = true) ||
                        it.branches.contains(query, ignoreCase = true)
                    }
                }
                adapter.updateList(filtered)
                layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        btnClearSearch.setOnClickListener { etSearch.setText("") }
        dialog.show()
    }

    private fun showEmployeeListDialog(title: String, employeeList: List<Performance>) {
        showEmployeeListDialog(title, "Select an officer to view full performance analytics", employeeList)
    }

    private fun showEmployeeListDialog(
        title: String,
        subtitle: String = "Select an officer to view full performance analytics",
        employeeList: List<Performance>,
        parentDialog: Dialog? = null
    ) {
        val dialog = Dialog(this, R.style.Theme_FullScreenDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_fullscreen_filter, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#F8FAFC")))
        }

        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnFilterClose)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvFilterHeaderTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvFilterHeaderSubtitle)
        val tvItemCount = dialogView.findViewById<TextView>(R.id.tvFilterItemCount)
        val etSearch = dialogView.findViewById<EditText>(R.id.etFilterSearch)
        val btnClearSearch = dialogView.findViewById<ImageButton>(R.id.btnClearFilterSearch)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerFullscreenFilter)
        val layoutEmpty = dialogView.findViewById<LinearLayout>(R.id.layoutFilterEmpty)

        tvTitle.text = title
        tvSubtitle.text = subtitle
        btnClose.setOnClickListener { dialog.dismiss() }

        val officerList = employeeList.distinctBy { it.employeeId }.map { emp ->
            val officerCards = allReports.count { it.employeeId == emp.employeeId }
            ModernOfficerEntry(
                employeeId = emp.employeeId,
                name = emp.employeeName,
                branch = emp.branch,
                zone = emp.zone,
                manager = emp.salesManager,
                totalCards = officerCards
            )
        }.sortedByDescending { it.totalCards }

        tvItemCount.text = "${officerList.size} Officers"

        val adapter = FullscreenOfficerAdapter(officerList) { selectedOfficer ->
            dialog.dismiss()
            parentDialog?.dismiss()
            filterDashboardByEmployee(selectedOfficer.employeeId, selectedOfficer.name)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        etSearch.hint = "Search by officer name, ID, branch or zone..."
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                val filtered = if (query.isEmpty()) {
                    officerList
                } else {
                    officerList.filter {
                        it.name.contains(query, ignoreCase = true) ||
                        it.employeeId.contains(query, ignoreCase = true) ||
                        it.branch.contains(query, ignoreCase = true) ||
                        it.zone.contains(query, ignoreCase = true) ||
                        it.manager.contains(query, ignoreCase = true)
                    }
                }
                adapter.updateList(filtered)
                layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        btnClearSearch.setOnClickListener { etSearch.setText("") }
        dialog.show()
    }

    private fun filterDashboardByZone(zoneName: String) {
        val filtered = allReports.filter { it.zone.equals(zoneName, ignoreCase = true) }
        val groupedList = filtered.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
            val first = performances.first()
            val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
            ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
        }.sortedWith(
            compareBy<ReportSummary> { getMonthIndex(it.month) }
                .thenBy { it.employeeName }
        )
        adapter.updateList(groupedList)

        val count = groupedList.size
        if (count == 0) {
            binding.tvFilterStatus.text = "Viewing Zone: $zoneName (0 Officers)"
            binding.tvFilterStatus.setTextColor(Color.RED)
        } else {
            val cardText = if (count == 1) "1 Officer record" else "$count Officer records"
            binding.tvFilterStatus.text = "Viewing Zone: $zoneName ($cardText)"
            binding.tvFilterStatus.setTextColor(Color.parseColor("#3F51B5"))
        }
    }

    private fun filterDashboardByManager(managerName: String) {
        val filtered = allReports.filter { it.salesManager.equals(managerName, ignoreCase = true) }
        val groupedList = filtered.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
            val first = performances.first()
            val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
            ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
        }.sortedWith(
            compareBy<ReportSummary> { getMonthIndex(it.month) }
                .thenBy { it.employeeName }
        )
        adapter.updateList(groupedList)

        val count = groupedList.size
        if (count == 0) {
            binding.tvFilterStatus.text = "Viewing Team: $managerName (0 Officers)"
            binding.tvFilterStatus.setTextColor(Color.RED)
        } else {
            val cardText = if (count == 1) "1 Officer record" else "$count Officer records"
            binding.tvFilterStatus.text = "Viewing Team: $managerName ($cardText)"
            binding.tvFilterStatus.setTextColor(Color.parseColor("#3F51B5"))
        }
    }

    private fun showCategoryDialog(title: String, categories: List<String>, onSelected: (String) -> Unit) {
        val dialog = Dialog(this, R.style.Theme_FullScreenDialog)
        val dialogView = layoutInflater.inflate(R.layout.dialog_fullscreen_filter, null)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#F8FAFC")))
        }

        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnFilterClose)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvFilterHeaderTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvFilterHeaderSubtitle)
        val tvItemCount = dialogView.findViewById<TextView>(R.id.tvFilterItemCount)
        val etSearch = dialogView.findViewById<EditText>(R.id.etFilterSearch)
        val btnClearSearch = dialogView.findViewById<ImageButton>(R.id.btnClearFilterSearch)
        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerFullscreenFilter)
        val layoutEmpty = dialogView.findViewById<LinearLayout>(R.id.layoutFilterEmpty)

        tvTitle.text = title
        tvSubtitle.text = "Select an option from below"
        tvItemCount.text = "${categories.size} Items"
        btnClose.setOnClickListener { dialog.dismiss() }

        val categoryList = categories.map {
            ModernCategoryEntry(
                name = it,
                officersCount = 0,
                totalCards = 0,
                branches = "Tap to view options",
                iconRes = R.drawable.ic_officers
            )
        }

        val catAdapter = FullscreenCategoryAdapter(categoryList) { selected ->
            dialog.dismiss()
            onSelected(selected.name)
        }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = catAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                val filtered = if (query.isEmpty()) {
                    categoryList
                } else {
                    categoryList.filter { it.name.contains(query, ignoreCase = true) }
                }
                catAdapter.updateList(filtered)
                layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                recycler.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        })

        btnClearSearch.setOnClickListener { etSearch.setText("") }
        dialog.show()
    }

    private fun confirmDeleteGroup(summary: ReportSummary) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Warning: Delete Records")
            .setMessage("Are you sure you want to permanently delete all performance records for ${summary.employeeName} (${summary.month})?\n\nThis action cannot be undone.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Yes, Delete") { _, _ ->
                db.collection("performance").whereEqualTo("employeeId", summary.employeeId).whereEqualTo("month", summary.month)
                    .get().addOnSuccessListener { snapshot ->
                        for (doc in snapshot.documents) doc.reference.delete()
                        viewModel.getAllReports()
                        Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to delete: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }.setNegativeButton("Cancel", null).show()
    }

    // ========================================================
    // 🔥 Modern Models & Adapters
    // ========================================================
    data class ModernRankingEntry(
        val rank: Int,
        val employeeId: String,
        val employeeName: String,
        val branch: String,
        val zone: String,
        val score: Int
    )

    data class ModernCategoryEntry(
        val name: String,
        val officersCount: Int,
        val totalCards: Int,
        val branches: String,
        val iconRes: Int
    )

    data class ModernOfficerEntry(
        val employeeId: String,
        val name: String,
        val branch: String,
        val zone: String,
        val manager: String,
        val totalCards: Int
    )

    inner class FullscreenRankingAdapter(
        private var list: List<ModernRankingEntry>,
        private val onItemClick: (ModernRankingEntry) -> Unit
    ) : RecyclerView.Adapter<FullscreenRankingAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view.findViewById(R.id.cardRankItem)
            val layoutBadge: FrameLayout = view.findViewById(R.id.layoutRankBadge)
            val tvNumber: TextView = view.findViewById(R.id.tvRankItemNumber)
            val tvName: TextView = view.findViewById(R.id.tvRankItemName)
            val tvBranch: TextView = view.findViewById(R.id.tvRankItemBranch)
            val tvZone: TextView = view.findViewById(R.id.tvRankItemZone)
            val tvScore: TextView = view.findViewById(R.id.tvRankItemScore)
            val tvStatus: TextView = view.findViewById(R.id.tvRankItemStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ranking_modern, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvNumber.text = item.rank.toString()
            holder.tvName.text = item.employeeName
            holder.tvBranch.text = if (item.branch.isNotBlank()) "Branch: ${item.branch}" else "ID: ${item.employeeId}"
            
            if (item.zone.isNotBlank()) {
                holder.tvZone.visibility = View.VISIBLE
                holder.tvZone.text = item.zone
            } else {
                holder.tvZone.visibility = View.GONE
            }

            val cardText = if (item.score == 1) "1 card" else "${item.score} cards"
            holder.tvScore.text = cardText

            // Styling based on Rank position
            when (item.rank) {
                1 -> {
                    holder.card.strokeColor = Color.parseColor("#FDE68A")
                    holder.card.strokeWidth = 3
                    holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                    holder.tvNumber.setTextColor(Color.parseColor("#92400E"))
                    holder.tvStatus.visibility = View.VISIBLE
                    holder.tvStatus.text = "👑 Champion (#1)"
                    holder.tvStatus.setTextColor(Color.parseColor("#B45309"))
                }
                2 -> {
                    holder.card.strokeColor = Color.parseColor("#CBD5E1")
                    holder.card.strokeWidth = 2
                    holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_grey)
                    holder.tvNumber.setTextColor(Color.parseColor("#334155"))
                    holder.tvStatus.visibility = View.VISIBLE
                    holder.tvStatus.text = "🥈 Runner Up (#2)"
                    holder.tvStatus.setTextColor(Color.parseColor("#475569"))
                }
                3 -> {
                    holder.card.strokeColor = Color.parseColor("#FED7AA")
                    holder.card.strokeWidth = 2
                    holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                    holder.tvNumber.setTextColor(Color.parseColor("#C2410C"))
                    holder.tvStatus.visibility = View.VISIBLE
                    holder.tvStatus.text = "🥉 3rd Place (#3)"
                    holder.tvStatus.setTextColor(Color.parseColor("#C2410C"))
                }
                else -> {
                    holder.card.strokeColor = Color.parseColor("#E2E8F0")
                    holder.card.strokeWidth = 1
                    holder.layoutBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                    holder.tvNumber.setTextColor(Color.parseColor("#1E40AF"))
                    holder.tvStatus.visibility = View.GONE
                }
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = list.size

        fun updateList(newList: List<ModernRankingEntry>) {
            list = newList
            notifyDataSetChanged()
        }
    }

    inner class FullscreenCategoryAdapter(
        private var list: List<ModernCategoryEntry>,
        private val onItemClick: (ModernCategoryEntry) -> Unit
    ) : RecyclerView.Adapter<FullscreenCategoryAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivCategoryIcon)
            val tvTitle: TextView = view.findViewById(R.id.tvCategoryTitle)
            val tvSubtitle: TextView = view.findViewById(R.id.tvCategorySubtitle)
            val tvBadge: TextView = view.findViewById(R.id.tvCategoryCountBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_modern, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvTitle.text = item.name
            holder.tvSubtitle.text = item.branches
            holder.ivIcon.setImageResource(item.iconRes)
            
            if (item.officersCount > 0) {
                holder.tvBadge.visibility = View.VISIBLE
                holder.tvBadge.text = "${item.officersCount} Officers"
            } else {
                holder.tvBadge.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = list.size

        fun updateList(newList: List<ModernCategoryEntry>) {
            list = newList
            notifyDataSetChanged()
        }
    }

    inner class FullscreenOfficerAdapter(
        private var list: List<ModernOfficerEntry>,
        private val onItemClick: (ModernOfficerEntry) -> Unit
    ) : RecyclerView.Adapter<FullscreenOfficerAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvInitial: TextView = view.findViewById(R.id.tvOfficerAvatarInitial)
            val tvName: TextView = view.findViewById(R.id.tvOfficerName)
            val tvEmpId: TextView = view.findViewById(R.id.tvOfficerEmpIdBadge)
            val tvBranch: TextView = view.findViewById(R.id.tvOfficerBranch)
            val tvCards: TextView = view.findViewById(R.id.tvOfficerCardsBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_officer_filter_modern, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            val initial = item.name.trim().take(1).uppercase()
            holder.tvInitial.text = if (initial.isNotEmpty()) initial else "O"
            holder.tvName.text = item.name
            holder.tvEmpId.text = "ID: ${item.employeeId}"
            holder.tvBranch.text = if (item.branch.isNotBlank()) "Branch: ${item.branch}" else "Zone: ${item.zone}"
            
            val cardText = if (item.totalCards == 1) "1 Card" else "${item.totalCards} Cards"
            holder.tvCards.text = cardText

            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = list.size

        fun updateList(newList: List<ModernOfficerEntry>) {
            list = newList
            notifyDataSetChanged()
        }
    }

    inner class ModernMonthGridAdapter(private val months: List<String>, private val onItemClick: (String) -> Unit) : RecyclerView.Adapter<ModernMonthGridAdapter.VH>() {
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
            holder.tvName.text = months[position]
        }
        override fun getItemCount() = months.size
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
        // Option removed from dashboard
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
                val yearlyTarget = if (officer.yearlyTarget > 0) officer.yearlyTarget else (target * 12)
                val yearlyAchieved = TargetUtils.countCards(allReports, officer.employeeId, null)

                if (target > 0) {
                    totalTargetSum += target
                    officersWithTargetCount++
                }
                totalAchievedSum += achieved

                TargetOfficerItem(officer, target, achieved, rate, yearlyTarget, yearlyAchieved)
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
        val isSalesManager = userRole.trim().equals("Sales Manager", ignoreCase = true)
        db.collection("employees")
            .get()
            .addOnSuccessListener { snapshot ->
                val officers = snapshot.documents.mapNotNull { doc ->
                    val u = doc.toObject(User::class.java)
                    if (u != null) {
                        val effective = if (u.employeeId.isBlank()) u.copy(employeeId = doc.id) else u
                        val isApproved = effective.status.isBlank() ||
                                         effective.status.equals("Approved", ignoreCase = true) ||
                                         effective.status.equals("APPROVED", ignoreCase = true)
                        val belongsToManager = if (isSalesManager) {
                            effective.salesManager.trim().equals(userName.trim(), ignoreCase = true)
                        } else true
                        if (isApproved && effective.isTargetEligible && belongsToManager) effective else null
                    } else null
                }.sortedBy { it.name }
                onLoaded(officers)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load officers", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSetTargetDialog(user: User, initialMonth: String = "", onSaved: (() -> Unit)? = null) {
        val isSalesManager = userRole.trim().equals("Sales Manager", ignoreCase = true)
        if (isSalesManager && !user.salesManager.trim().equals(userName.trim(), ignoreCase = true)) {
            Toast.makeText(this, "Access Denied: You can only set targets for your own team members.", Toast.LENGTH_SHORT).show()
            return
        }
        val targetBinding = DialogSetTargetBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(targetBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        targetBinding.tvSetTargetEmployee.text = "${user.name} (ID: ${user.employeeId})"

        val periodOptions = listOf("General Target (Monthly & Yearly)") + monthsList
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, periodOptions)
        targetBinding.spinnerTargetMonth.adapter = spinnerAdapter

        if (initialMonth.isNotBlank()) {
            val idx = periodOptions.indexOfFirst { it.equals(initialMonth, ignoreCase = true) }
            if (idx >= 0) targetBinding.spinnerTargetMonth.setSelection(idx)
        }

        if (user.monthlyTarget > 0) {
            targetBinding.etTargetCards.setText(user.monthlyTarget.toString())
        }

        val initialYearly = if (user.yearlyTarget > 0) user.yearlyTarget else (user.monthlyTarget * 12)
        if (initialYearly > 0) {
            targetBinding.etYearlyTargetCards.setText(initialYearly.toString())
        }

        // Auto-calculate yearly target when typing monthly target if yearly is empty or default
        targetBinding.etTargetCards.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val mVal = s.toString().trim().toIntOrNull()
                if (mVal != null && mVal >= 0) {
                    val yText = targetBinding.etYearlyTargetCards.text.toString().trim()
                    if (yText.isEmpty() || yText == "0" || yText.toIntOrNull() == (user.monthlyTarget * 12)) {
                        targetBinding.etYearlyTargetCards.setText((mVal * 12).toString())
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        targetBinding.btnCancelTarget.setOnClickListener { dialog.dismiss() }

        targetBinding.btnSaveTarget.setOnClickListener {
            val monthlyStr = targetBinding.etTargetCards.text.toString().trim()
            val monthlyVal = monthlyStr.toIntOrNull()
            if (monthlyVal == null || monthlyVal < 0) {
                targetBinding.tilTargetCards.error = "Please enter a valid monthly target"
                return@setOnClickListener
            }
            targetBinding.tilTargetCards.error = null

            val yearlyStr = targetBinding.etYearlyTargetCards.text.toString().trim()
            val yearlyVal = yearlyStr.toIntOrNull() ?: (monthlyVal * 12)

            val selectedPeriod = periodOptions[targetBinding.spinnerTargetMonth.selectedItemPosition]
            val monthToSave = if (selectedPeriod.startsWith("General")) "General" else selectedPeriod

            targetBinding.btnSaveTarget.isEnabled = false
            TargetUtils.saveTarget(
                employeeId = user.employeeId,
                employeeName = user.name,
                month = monthToSave,
                targetCards = monthlyVal,
                yearlyTargetCards = yearlyVal,
                adminName = userName.ifBlank { "Admin" },
                onSuccess = {
                    Toast.makeText(this, "Target set: Monthly $monthlyVal cards, Yearly $yearlyVal cards for ${user.name}", Toast.LENGTH_SHORT).show()
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
        val isSalesManager = userRole.trim().equals("Sales Manager", ignoreCase = true)
        if (isSalesManager && !user.salesManager.trim().equals(userName.trim(), ignoreCase = true)) {
            Toast.makeText(this, "Access Denied: You can only view details for your own team members.", Toast.LENGTH_SHORT).show()
            return
        }
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

        // Annual / Yearly Target progress
        val yearlyTarget = if (user.yearlyTarget > 0) user.yearlyTarget else (target * 12)
        val yearlyAchieved = TargetUtils.countCards(allReports, user.employeeId, null)
        val yearlyRate = TargetUtils.calculateAchievementRate(yearlyAchieved, yearlyTarget)

        detailsBinding.tvDetailYearlyTargetCount.text = "Yearly Target: $yearlyTarget Cards"
        detailsBinding.tvDetailYearlyAchievedCount.text = "Total Achieved: $yearlyAchieved Cards"
        detailsBinding.tvDetailYearlyAchievementPercent.text = TargetUtils.formatAchievementRate(yearlyAchieved, yearlyTarget)
        val yearlyColor = TargetUtils.getAchievementColor(yearlyAchieved, yearlyTarget)
        detailsBinding.tvDetailYearlyAchievementPercent.setTextColor(yearlyColor)
        detailsBinding.progressDetailYearly.progress = yearlyRate.toInt().coerceIn(0, 100)
        detailsBinding.progressDetailYearly.setIndicatorColor(yearlyColor)

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

    private fun setupExitNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitDialog()
            }
        })
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?")
            .setIcon(R.drawable.ic_logout)
            .setPositiveButton("Yes") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}
