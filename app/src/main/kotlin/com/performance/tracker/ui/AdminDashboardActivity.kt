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
import com.lowagie.text.Document
import com.lowagie.text.Font
import com.lowagie.text.Paragraph
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import com.performance.tracker.R
import com.performance.tracker.adapter.ReportAdapter
import com.performance.tracker.databinding.ActivityAdminDashboardBinding
import com.performance.tracker.model.Performance
import com.performance.tracker.model.ReportSummary
import com.performance.tracker.model.User
import com.performance.tracker.viewmodel.MainViewModel
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
        
        binding.tvHeaderTitle.text = "Control Panel ($userRole)"
        
        loadDashboardData()
        
        binding.btnRefresh.setOnClickListener {
            Toast.makeText(this, "Refreshing data...", Toast.LENGTH_SHORT).show()
            loadDashboardData()
        }

        binding.btnTopAction.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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
                } else {
                    binding.tvFilterStatus.text = "0 card"
                    binding.tvFilterStatus.setTextColor(Color.RED)
                    adapter.updateList(emptyList())
                }

            } else {
                binding.tvFilterStatus.text = "0 card"
                binding.tvFilterStatus.setTextColor(Color.RED)
                adapter.updateList(emptyList())
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
        val canEditOrDelete = userRole.equals("ADMIN", ignoreCase = true)

        adapter = ReportAdapter(emptyList(), isAdmin = canEditOrDelete, 
            onCardClick = { summary ->
                val intent = Intent(this, ReportDetailsActivity::class.java).apply {
                    putExtra("EMP_ID", summary.employeeId)
                    putExtra("MONTH", summary.month)
                    putExtra("EMP_NAME", summary.employeeName)
                    putExtra("BRANCH", summary.branch)
                }
                startActivity(intent)
            },
            onActionClick = { summary, action ->
                if (action == "DELETE") confirmDeleteGroup(summary)
            }
        )
        binding.recyclerAdminReport.layoutManager = LinearLayoutManager(this)
        binding.recyclerAdminReport.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnDownloadExcel.setOnClickListener { showExportDialog(isExcel = true) }
        
        // PDF বাটনের আইডি ব্যবহার করে "Who Not Submitted" অপশনটি ফায়ার করা হয়েছে
        val btnWhoNotSubmit = findViewById<Button>(resources.getIdentifier("btnWhoNotSubmitted", "id", packageName)) 
            ?: findViewById<Button>(resources.getIdentifier("btnDownloadPdf", "id", packageName))
            
        btnWhoNotSubmit?.setOnClickListener { 
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
    // 🔥 Custom Export Dialog & Logic (Summary + Details)
    // ========================================================
    private fun showExportDialog(isExcel: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_report, null)
        val spinnerFrom = dialogView.findViewById<Spinner>(R.id.spinnerFromMonth)
        val spinnerTo = dialogView.findViewById<Spinner>(R.id.spinnerToMonth)
        val btnExport = dialogView.findViewById<Button>(R.id.btnExportData)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvExportTitle)
        
        val rgExportType = dialogView.findViewById<RadioGroup>(R.id.rgExportType)
        val rbSummary = dialogView.findViewById<RadioButton>(R.id.rbSummary)

        tvTitle.text = if (isExcel) "Export Excel Report" else "Export PDF Report"
        rgExportType.visibility = if (isExcel) View.VISIBLE else View.GONE

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

        btnExport.setOnClickListener {
            val fromMonth = spinnerFrom.selectedItem.toString()
            val toMonth = spinnerTo.selectedItem.toString()
            
            val fromIdx = monthsList.indexOf(fromMonth)
            val toIdx = monthsList.indexOf(toMonth)

            if (fromIdx > toIdx) {
                Toast.makeText(this, "Invalid Range: 'From Month' cannot be after 'To Month'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val selectedMonths = monthsList.subList(fromIdx, toIdx + 1)
            val isSummary = isExcel && rbSummary.isChecked 

            dialog.dismiss()
            processExport(selectedMonths, isExcel, fromMonth, toMonth, isSummary)
        }
        
        dialog.show()
    }

    private fun processExport(selectedMonths: List<String>, isExcel: Boolean, fromMonth: String, toMonth: String, isSummary: Boolean) {
        val filteredData = allReports.filter { selectedMonths.contains(it.month) }

        if (filteredData.isEmpty()) { 
            Toast.makeText(this, "No records found for this range", Toast.LENGTH_SHORT).show()
            return 
        }

        val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
        val headerRange = if (fromMonth == toMonth) "($fromMonth-$year)" else "($fromMonth To $toMonth-$year)"

        if (isExcel) {
            generateExcel(filteredData, headerRange, isSummary)
        } else {
            generatePDF(filteredData, headerRange)
        }
    }

    private fun generateExcel(data: List<Performance>, headerRange: String, isSummary: Boolean) {
        try {
            val fileName = "Performance_Report_${System.currentTimeMillis()}.csv"
            val mimeType = "text/csv"
            val (fileUri, outputStream) = getFileUriAndStream(fileName, mimeType)
            
            if (outputStream == null) return
            
            outputStream.use { stream ->
                csvWriter().open(stream) {
                    writeRow(listOf("Performance Report"))
                    writeRow(listOf(headerRange))
                    writeRow(listOf("")) 
                    
                    if (isSummary) {
                        writeRow(listOf("SL", "Month", "Officer", "Branch", "Total card"))
                        
                        val groupedData = data.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
                            val first = performances.first()
                            val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
                            ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
                        }
                        
                        groupedData.forEachIndexed { index, item -> 
                            writeRow(listOf(
                                (index + 1).toString(), 
                                item.month,          
                                item.employeeName, 
                                item.branch, 
                                item.totalRecords.toString()
                            )) 
                        }
                    } else {
                        writeRow(listOf("SL", "Month", "Officer", "Branch", "Applicant Name", "A/C No"))
                        
                        val detailsData = data.filter { !it.limit.equals("NIL", ignoreCase = true) }
                        
                        detailsData.forEachIndexed { index, item -> 
                            writeRow(listOf(
                                (index + 1).toString(), 
                                item.month,          
                                item.employeeName, 
                                item.branch, 
                                item.applicantName, 
                                item.accountNo
                            )) 
                        }
                    }
                }
            }
            Toast.makeText(this, "Excel saved in Downloads", Toast.LENGTH_SHORT).show()
            showDownloadNotification(fileName, fileUri, mimeType)
        } catch (e: Exception) { 
            e.printStackTrace()
            Toast.makeText(this, "Error generating Excel: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generatePDF(data: List<Performance>, headerRange: String) {
        try {
            val fileName = "Performance_Report_${System.currentTimeMillis()}.pdf"
            val mimeType = "application/pdf"
            val (fileUri, outputStream) = getFileUriAndStream(fileName, mimeType)
            
            if (outputStream == null) return
            
            val doc = Document()
            PdfWriter.getInstance(doc, outputStream)
            doc.open()
            
            val titleFont = Font(Font.HELVETICA, 18f, Font.BOLD)
            val titlePara = Paragraph("Performance Report", titleFont)
            titlePara.alignment = Paragraph.ALIGN_CENTER
            doc.add(titlePara)
            
            val subFont = Font(Font.HELVETICA, 14f, Font.BOLD)
            val subPara = Paragraph(headerRange, subFont)
            subPara.alignment = Paragraph.ALIGN_CENTER
            subPara.spacingAfter = 20f
            doc.add(subPara)

            val table = PdfPTable(6).apply { 
                widthPercentage = 100f 
                setWidths(floatArrayOf(1f, 2f, 3f, 2.5f, 3.5f, 2.5f)) 
            }
            
            val headerFont = Font(Font.HELVETICA, 12f, Font.BOLD)
            val headers = listOf("SL", "Month", "Officer", "Branch", "Applicant Name", "A/C No")
            headers.forEach { headerText ->
                table.addCell(Paragraph(headerText, headerFont))
            }
            
            val dataFont = Font(Font.HELVETICA, 11f, Font.NORMAL)
            
            val detailsData = data.filter { !it.limit.equals("NIL", ignoreCase = true) }
            
            detailsData.forEachIndexed { index, item ->
                table.addCell(Paragraph((index + 1).toString(), dataFont))
                table.addCell(Paragraph(item.month, dataFont))
                table.addCell(Paragraph(item.employeeName, dataFont))
                table.addCell(Paragraph(item.branch, dataFont))
                table.addCell(Paragraph(item.applicantName, dataFont))
                table.addCell(Paragraph(item.accountNo, dataFont))
            }
            
            doc.add(table)
            doc.close()
            outputStream.close()
            
            Toast.makeText(this, "PDF saved in Downloads", Toast.LENGTH_SHORT).show()
            showDownloadNotification(fileName, fileUri, mimeType)
        } catch (e: Exception) { 
            e.printStackTrace()
            Toast.makeText(this, "PDF Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getFileUriAndStream(fileName: String, mimeType: String): Pair<Uri?, OutputStream?> {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
        val stream = uri?.let { contentResolver.openOutputStream(it) }
        return Pair(uri, stream)
    }

    private fun showDownloadNotification(fileName: String, fileUri: Uri?, mimeType: String) {
        val channelId = "report_downloads"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Report Download Complete")
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
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
}
