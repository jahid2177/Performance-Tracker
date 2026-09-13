package com.performance.tracker.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.performance.tracker.R
import com.performance.tracker.databinding.ActivityReportPreviewBinding
import com.performance.tracker.model.Performance
import com.performance.tracker.model.User
import com.performance.tracker.util.ReportExporter
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportPreviewBinding

    private var reportData: List<Performance> = emptyList()
    private var allEmployees: List<User> = emptyList()
    private var cachedMonthlyTargets: Map<String, Int> = emptyMap()
    private var cachedYearlyTargets: Map<String, Int> = emptyMap()

    private var monthRange: String = ""
    private var managerName: String = ""
    private var isSummary: Boolean = true
    private var isSalesManagerReport: Boolean = false
    private var includeDetails: Boolean = false
    private var includeZone: Boolean = true
    private var includeSalesManager: Boolean = false
    private var monthCount: Int = 1

    companion object {
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_EMPLOYEES = "EXTRA_EMPLOYEES"
        const val EXTRA_MONTH_TARGETS = "EXTRA_MONTH_TARGETS"
        const val EXTRA_YEAR_TARGETS = "EXTRA_YEAR_TARGETS"
        const val EXTRA_MONTH_RANGE = "EXTRA_MONTH_RANGE"
        const val EXTRA_MANAGER_NAME = "EXTRA_MANAGER_NAME"
        const val EXTRA_IS_SUMMARY = "EXTRA_IS_SUMMARY"
        const val EXTRA_IS_SALES_MANAGER_REPORT = "EXTRA_IS_SALES_MANAGER_REPORT"
        const val EXTRA_INCLUDE_DETAILS = "EXTRA_INCLUDE_DETAILS"
        const val EXTRA_INCLUDE_ZONE = "EXTRA_INCLUDE_ZONE"
        const val EXTRA_INCLUDE_SALES_MANAGER = "EXTRA_INCLUDE_SALES_MANAGER"
        const val EXTRA_MONTH_COUNT = "EXTRA_MONTH_COUNT"

        // Holder to avoid TransactionTooLargeException for large datasets
        var previewDataHolder: List<Performance>? = null
        var previewEmployeesHolder: List<User>? = null
        var previewMonthlyTargets: Map<String, Int>? = null
        var previewYearlyTargets: Map<String, Int>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractIntentData()
        setupTopBar()
        populateDocumentHeader()
        buildPreviewTable()
        setupActionButtons()
    }

    private fun extractIntentData() {
        reportData = previewDataHolder ?: (intent.getSerializableExtra(EXTRA_DATA) as? ArrayList<Performance>) ?: emptyList()
        allEmployees = previewEmployeesHolder ?: (intent.getSerializableExtra(EXTRA_EMPLOYEES) as? ArrayList<User>) ?: emptyList()

        cachedMonthlyTargets = previewMonthlyTargets ?: emptyMap()
        cachedYearlyTargets = previewYearlyTargets ?: emptyMap()

        monthRange = intent.getStringExtra(EXTRA_MONTH_RANGE) ?: "Selected Period"
        managerName = intent.getStringExtra(EXTRA_MANAGER_NAME) ?: ""
        isSummary = intent.getBooleanExtra(EXTRA_IS_SUMMARY, true)
        isSalesManagerReport = intent.getBooleanExtra(EXTRA_IS_SALES_MANAGER_REPORT, false)
        includeDetails = intent.getBooleanExtra(EXTRA_INCLUDE_DETAILS, false)
        includeZone = intent.getBooleanExtra(EXTRA_INCLUDE_ZONE, true)
        includeSalesManager = intent.getBooleanExtra(EXTRA_INCLUDE_SALES_MANAGER, false)
        monthCount = intent.getIntExtra(EXTRA_MONTH_COUNT, 1).coerceAtLeast(1)
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener { finish() }

        val typeText = when {
            isSalesManagerReport -> "Sales Manager Report"
            includeDetails -> "Detailed Accounts Report"
            else -> "Officer Performance (Summary)"
        }
        binding.tvPreviewSubtitle.text = "Sample PDF Layout: $typeText"
    }

    private fun populateDocumentHeader() {
        val currentDate = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()).format(Date())
        binding.tvGeneratedDate.text = "Generated: $currentDate"
        binding.tvReportPeriod.text = "Period: $monthRange"

        when {
            isSalesManagerReport -> {
                binding.tvReportTitle.text = "SALES MANAGER PERFORMANCE REPORT"
                binding.tvScope.text = "Scope: Sales Managers"
            }
            includeDetails -> {
                binding.tvReportTitle.text = "DETAILED APPLICANT PERFORMANCE REPORT"
                binding.tvScope.text = "Scope: All Approved Cards"
            }
            else -> {
                binding.tvReportTitle.text = "OFFICER PERFORMANCE SUMMARY REPORT"
                binding.tvScope.text = if (managerName.isNotBlank()) "Manager: $managerName" else "Scope: All Officers"
            }
        }
    }

    private fun buildPreviewTable() {
        binding.tablePreview.removeAllViews()

        if (isSalesManagerReport) {
            buildSalesManagerTable()
        } else if (includeDetails) {
            buildDetailedAccountsTable()
        } else {
            buildOfficerSummaryTable()
        }
    }

    private fun buildOfficerSummaryTable() {
        val empMap = allEmployees.associateBy { it.employeeId.trim() }
        val groupedByOfficer = reportData.groupBy { it.employeeId.trim() }
        val allRelevantEmpIds = reportData.map { it.employeeId.trim() }.filter { it.isNotBlank() }.distinct()

        val officerSummaries = allRelevantEmpIds.mapNotNull { empId ->
            val list = groupedByOfficer[empId] ?: return@mapNotNull null
            if (list.isEmpty()) return@mapNotNull null
            val officerEmp = empMap[empId]
            val first = list.first()

            val cardCount = list.count { !it.limit.equals("NIL", ignoreCase = true) }
            val empName = (officerEmp?.name?.takeIf { it.isNotBlank() } ?: first.employeeName.takeIf { it.isNotBlank() } ?: empId).trim().uppercase(Locale.getDefault())
            val branch = (officerEmp?.branch?.takeIf { it.isNotBlank() } ?: first.branch).trim()
            val rawZone = (first.zone.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                ?: officerEmp?.zone?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: "").trim()

            val zone = when {
                rawZone.isNotBlank() -> rawZone
                branch.contains("CTG", ignoreCase = true) || branch.contains("Chittagong", ignoreCase = true) -> "CTG"
                branch.contains("Dhaka", ignoreCase = true) || branch.contains("Shahbagh", ignoreCase = true) ||
                branch.contains("Gulshan", ignoreCase = true) || branch.contains("Dhanmondi", ignoreCase = true) ||
                branch.contains("Sonargaon", ignoreCase = true) || branch.contains("Nazimuddin", ignoreCase = true) ||
                branch.contains("Mohammadpur", ignoreCase = true) || branch.contains("Shishu Park", ignoreCase = true) ||
                branch.contains("Hotel Intercontinental", ignoreCase = true) || branch.contains("Mohakhali", ignoreCase = true) ||
                branch.contains("Kafrul", ignoreCase = true) || branch.contains("Kuril", ignoreCase = true) ||
                branch.contains("Ring Road", ignoreCase = true) || branch.contains("Darussalam", ignoreCase = true) -> "Dhaka"
                else -> "Others"
            }

            val salesMgr = (officerEmp?.salesManager?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                ?: first.salesManager.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: "").trim()

            val baseMonthlyTarget = cachedMonthlyTargets[empId]?.takeIf { it > 0 }
                ?: officerEmp?.monthlyTarget?.takeIf { it > 0 }
                ?: 8

            val totalTarget = if (monthCount == 12 && (cachedYearlyTargets[empId] ?: 0) > 0) {
                cachedYearlyTargets[empId]!!
            } else {
                baseMonthlyTarget * monthCount
            }

            val variance = totalTarget - cardCount
            val achPercent = if (totalTarget > 0) (cardCount.toFloat() / totalTarget.toFloat()) * 100f else 0f

            OfficerRowData(empId, empName, branch, zone, salesMgr, totalTarget, cardCount, variance, achPercent)
        }.sortedWith(compareByDescending<OfficerRowData> { it.cardCount }.thenBy { it.name })

        // Update KPI Summary
        val totalOfficers = officerSummaries.size
        val totalCards = officerSummaries.sumOf { it.cardCount }
        val totalTargetSum = officerSummaries.sumOf { it.target }
        val overallRate = if (totalTargetSum > 0) (totalCards.toFloat() / totalTargetSum.toFloat() * 100f) else 0f

        binding.tvSummaryTotalOfficers.text = totalOfficers.toString()
        binding.tvSummaryTotalCards.text = totalCards.toString()
        binding.tvSummaryAchievementRate.text = String.format(Locale.US, "%.1f%%", overallRate)

        // Columns definition
        val columns = mutableListOf<String>()
        columns.add("SL")
        columns.add("ID")
        columns.add("Name")
        columns.add("Branch")
        if (includeZone) columns.add("Zone")
        if (includeSalesManager) columns.add("Sales Manager")
        columns.add("Target")
        columns.add("Achieved")
        columns.add("Short")
        columns.add("Ach. %")

        addTableHeader(columns)

        officerSummaries.forEachIndexed { index, row ->
            val cells = mutableListOf<String>()
            cells.add((index + 1).toString())
            cells.add(row.empId)
            cells.add(row.name)
            cells.add(row.branch)
            if (includeZone) cells.add(row.zone)
            if (includeSalesManager) cells.add(row.salesManager.takeIf { it.isNotBlank() } ?: "Direct")
            cells.add(row.target.toString())
            cells.add(row.cardCount.toString())
            cells.add(if (row.variance > 0) row.variance.toString() else "0")
            cells.add(String.format(Locale.US, "%.1f%%", row.achievementPercent))

            val achColor = when {
                row.achievementPercent >= 100f -> Color.parseColor("#15803D")
                row.achievementPercent >= 75f -> Color.parseColor("#D97706")
                else -> Color.parseColor("#DC2626")
            }

            addDataRow(cells, index % 2 == 1, highlightColumnIndex = cells.size - 1, highlightColor = achColor)
        }

        // Totals Row
        val totalCells = mutableListOf<String>()
        totalCells.add("TOTAL")
        totalCells.add("")
        totalCells.add("$totalOfficers Officers")
        totalCells.add("")
        if (includeZone) totalCells.add("")
        if (includeSalesManager) totalCells.add("")
        totalCells.add(totalTargetSum.toString())
        totalCells.add(totalCards.toString())
        val totalShort = (totalTargetSum - totalCards).coerceAtLeast(0)
        totalCells.add(totalShort.toString())
        totalCells.add(String.format(Locale.US, "%.1f%%", overallRate))

        addTotalRow(totalCells)
    }

    private fun buildSalesManagerTable() {
        val empMap = allEmployees.associateBy { it.employeeId.trim() }
        val groupedByOfficer = reportData.groupBy { it.employeeId.trim() }
        val allRelevantEmpIds = reportData.map { it.employeeId.trim() }.filter { it.isNotBlank() }.distinct()

        val officerSummaries = allRelevantEmpIds.mapNotNull { empId ->
            val list = groupedByOfficer[empId] ?: return@mapNotNull null
            if (list.isEmpty()) return@mapNotNull null
            val officerEmp = empMap[empId]
            val first = list.first()

            val cardCount = list.count { !it.limit.equals("NIL", ignoreCase = true) }
            val empName = (officerEmp?.name?.takeIf { it.isNotBlank() } ?: first.employeeName.takeIf { it.isNotBlank() } ?: empId).trim().uppercase(Locale.getDefault())
            val branch = (officerEmp?.branch?.takeIf { it.isNotBlank() } ?: first.branch).trim()
            val salesMgr = (officerEmp?.salesManager?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                ?: first.salesManager.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) } ?: "").trim()

            val baseMonthlyTarget = cachedMonthlyTargets[empId]?.takeIf { it > 0 }
                ?: officerEmp?.monthlyTarget?.takeIf { it > 0 }
                ?: 8

            val totalTarget = if (monthCount == 12 && (cachedYearlyTargets[empId] ?: 0) > 0) {
                cachedYearlyTargets[empId]!!
            } else {
                baseMonthlyTarget * monthCount
            }

            OfficerRowData(empId, empName, branch, "", salesMgr, totalTarget, cardCount, totalTarget - cardCount, 0f)
        }

        val knownSalesManagers = mutableSetOf<String>()
        officerSummaries.forEach {
            if (it.salesManager.isNotBlank() && !it.salesManager.equals("N/A", ignoreCase = true) && !it.salesManager.equals("Direct / Head Office", ignoreCase = true)) {
                knownSalesManagers.add(it.salesManager.trim())
            }
        }

        val managerSummaries = knownSalesManagers.mapNotNull { mName ->
            val officers = officerSummaries.filter { it.salesManager.equals(mName, ignoreCase = true) }
            if (officers.isEmpty()) return@mapNotNull null

            val totalCards = officers.sumOf { it.cardCount }
            val totalTarget = officers.sumOf { it.target }
            val short = (totalTarget - totalCards).coerceAtLeast(0)
            val achPercent = if (totalTarget > 0) (totalCards.toFloat() / totalTarget.toFloat()) * 100f else 0f

            ManagerRowData(mName, officers.size, totalCards, totalTarget, short, achPercent)
        }.sortedWith(compareByDescending<ManagerRowData> { it.totalCards }.thenBy { it.name })

        // Update KPI Summary
        val totalManagers = managerSummaries.size
        val totalCards = managerSummaries.sumOf { it.totalCards }
        val totalTargetSum = managerSummaries.sumOf { it.target }
        val overallRate = if (totalTargetSum > 0) (totalCards.toFloat() / totalTargetSum.toFloat() * 100f) else 0f

        binding.tvSummaryTotalOfficers.text = totalManagers.toString()
        binding.tvSummaryTotalCards.text = totalCards.toString()
        binding.tvSummaryAchievementRate.text = String.format(Locale.US, "%.1f%%", overallRate)

        val columns = listOf("SL", "Sales Manager", "Officers", "Total Cards", "Target", "Short", "Achievement %")
        addTableHeader(columns)

        managerSummaries.forEachIndexed { index, row ->
            val cells = listOf(
                (index + 1).toString(),
                row.name,
                row.officerCount.toString(),
                row.totalCards.toString(),
                row.target.toString(),
                row.short.toString(),
                String.format(Locale.US, "%.1f%%", row.achPercent)
            )

            val achColor = when {
                row.achPercent >= 100f -> Color.parseColor("#15803D")
                row.achPercent >= 75f -> Color.parseColor("#D97706")
                else -> Color.parseColor("#DC2626")
            }

            addDataRow(cells, index % 2 == 1, highlightColumnIndex = cells.size - 1, highlightColor = achColor)
        }

        // Total Row
        val totalCells = listOf(
            "TOTAL",
            "$totalManagers Managers",
            officerSummaries.size.toString(),
            totalCards.toString(),
            totalTargetSum.toString(),
            (totalTargetSum - totalCards).coerceAtLeast(0).toString(),
            String.format(Locale.US, "%.1f%%", overallRate)
        )
        addTotalRow(totalCells)
    }

    private fun buildDetailedAccountsTable() {
        val nonNilReports = reportData.filter { !it.limit.equals("NIL", ignoreCase = true) }

        binding.tvSummaryTotalOfficers.text = nonNilReports.map { it.employeeId }.distinct().size.toString()
        binding.tvSummaryTotalCards.text = nonNilReports.size.toString()
        binding.tvSummaryAchievementRate.text = "100%"

        val columns = listOf("SL", "Officer ID", "Officer Name", "Branch", "Applicant Name", "Account No", "Limit", "Month")
        addTableHeader(columns)

        nonNilReports.take(150).forEachIndexed { index, item ->
            val cells = listOf(
                (index + 1).toString(),
                item.employeeId,
                item.employeeName,
                item.branch,
                item.applicantName,
                item.accountNo,
                item.limit,
                item.month
            )
            addDataRow(cells, index % 2 == 1)
        }

        val totalCells = listOf("TOTAL", "", "${nonNilReports.size} Accounts Approved", "", "", "", "", "")
        addTotalRow(totalCells)
    }

    private fun addTableHeader(columns: List<String>) {
        val headerRow = TableRow(this).apply {
            setBackgroundColor(Color.parseColor("#1E3A8A"))
            setPadding(0, 0, 0, 0)
        }

        columns.forEach { title ->
            val tv = TextView(this).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(16, 14, 16, 14)
                setBackgroundResource(R.drawable.bg_table_row)
            }
            headerRow.addView(tv)
        }

        binding.tablePreview.addView(headerRow)
    }

    private fun addDataRow(cells: List<String>, isAlternate: Boolean, highlightColumnIndex: Int = -1, highlightColor: Int = Color.BLACK) {
        val row = TableRow(this).apply {
            setBackgroundColor(if (isAlternate) Color.parseColor("#F8FAFC") else Color.WHITE)
        }

        cells.forEachIndexed { idx, value ->
            val tv = TextView(this).apply {
                text = value
                textSize = 11f
                gravity = if (idx == 1 || idx == 2 || idx == 3 || idx == 4) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
                setPadding(14, 10, 14, 10)
                if (idx == highlightColumnIndex) {
                    setTextColor(highlightColor)
                    typeface = Typeface.DEFAULT_BOLD
                } else {
                    setTextColor(Color.parseColor("#1F2937"))
                    typeface = if (idx == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
            }
            row.addView(tv)
        }

        binding.tablePreview.addView(row)
    }

    private fun addTotalRow(cells: List<String>) {
        val row = TableRow(this).apply {
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            setPadding(0, 0, 0, 0)
        }

        cells.forEachIndexed { idx, value ->
            val tv = TextView(this).apply {
                text = value
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = if (idx == 0 || idx == 2) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
                setTextColor(Color.parseColor("#0F172A"))
                setPadding(14, 12, 14, 12)
            }
            row.addView(tv)
        }

        binding.tablePreview.addView(row)
    }

    private fun setupActionButtons() {
        binding.btnDownloadPdf.setOnClickListener {
            ReportExporter.generateMonthlyPdf(
                context = this,
                data = reportData,
                monthRange = monthRange,
                managerName = managerName,
                isSummary = isSummary,
                isShare = false,
                officerMonthlyTargets = cachedMonthlyTargets,
                officerYearlyTargets = cachedYearlyTargets,
                monthCount = monthCount,
                includeDetails = includeDetails,
                includeZone = includeZone,
                includeSalesManager = includeSalesManager,
                isSalesManagerReport = isSalesManagerReport,
                allEmployees = allEmployees
            )
        }

        binding.btnSharePdf.setOnClickListener {
            ReportExporter.generateMonthlyPdf(
                context = this,
                data = reportData,
                monthRange = monthRange,
                managerName = managerName,
                isSummary = isSummary,
                isShare = true,
                officerMonthlyTargets = cachedMonthlyTargets,
                officerYearlyTargets = cachedYearlyTargets,
                monthCount = monthCount,
                includeDetails = includeDetails,
                includeZone = includeZone,
                includeSalesManager = includeSalesManager,
                isSalesManagerReport = isSalesManagerReport,
                allEmployees = allEmployees
            )
        }
    }

    private data class OfficerRowData(
        val empId: String,
        val name: String,
        val branch: String,
        val zone: String,
        val salesManager: String,
        val target: Int,
        val cardCount: Int,
        val variance: Int,
        val achievementPercent: Float
    )

    private data class ManagerRowData(
        val name: String,
        val officerCount: Int,
        val totalCards: Int,
        val target: Int,
        val short: Int,
        val achPercent: Float
    )
}
