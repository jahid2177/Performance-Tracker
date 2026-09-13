package com.performance.tracker.util

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.performance.tracker.R
import com.performance.tracker.model.Performance
import com.performance.tracker.model.ReportSummary
import com.performance.tracker.model.User
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private const val CHANNEL_ID = "performance_reports"

    private data class PdfCol(val key: String, val title: String, val width: Float, val isCenter: Boolean) {
        constructor(title: String, width: Float, isCenter: Boolean) : this("", title, width, isCenter)
    }

    data class OfficerSummary(
        val employeeId: String,
        val employeeName: String,
        val branch: String,
        val zone: String,
        val salesManager: String,
        val total: Int,
        val target: Int,
        val yearlyTarget: Int = 0,
        val short: Int,
        val achievementRate: Double,
        val achievementStr: String
    )

    data class SalesManagerSummary(
        val managerName: String,
        val zone: String,
        val officerCount: Int,
        val total: Int,
        val target: Int,
        val short: Int,
        val achievementRate: Double,
        val achievementStr: String
    )

    // ========================================================
    // 1. NATIVE ANDROID PDF: Performance & Target Achievement Report (Download / Share)
    // Supports Officer Performance and Sales Manager Performance with dynamic Zone & Sales Manager toggles
    // ========================================================
    fun generateMonthlyPdf(
        context: Context,
        data: List<Performance>,
        monthRange: String,
        managerName: String,
        isSummary: Boolean,
        isShare: Boolean,
        officerMonthlyTargets: Map<String, Int> = emptyMap(),
        officerYearlyTargets: Map<String, Int> = emptyMap(),
        monthCount: Int = 1,
        includeDetails: Boolean = false,
        includeZone: Boolean = true,
        includeSalesManager: Boolean = false,
        isSalesManagerReport: Boolean = false,
        allEmployees: List<User> = emptyList()
    ) {
        if (data.isEmpty()) {
            Toast.makeText(context, "No performance data found for the selected period", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Performance_Report_$timestamp.pdf"
            val mimeType = "application/pdf"

            // Cache directory destination
            val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
            val targetFile = File(reportsDir, fileName)
            val outputStream = FileOutputStream(targetFile)

            // Standard A4 dimensions
            val pageWidth = 595
            val pageHeight = 842
            val marginLeft = 20f
            val marginRight = 575f
            val contentWidth = marginRight - marginLeft // 555f

            val pdfDocument = PdfDocument()
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas

            // Paints
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = 7.5f
                textAlign = Paint.Align.CENTER
            }
            val headerBgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rowBgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val totalRowBgPaint = Paint().apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }
            val dataTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 8.0f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val dataBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 8.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val achievementGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#15803D") // Sharp green from reference image
                textSize = 8.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val achievementAmberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D97706")
                textSize = 8.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val achievementRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#DC2626")
                textSize = 8.2f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val gridPaint = Paint().apply {
                color = Color.parseColor("#4B5563") // Crisp cell border
                strokeWidth = 0.65f
                style = Paint.Style.STROKE
            }
            val outerGridPaint = Paint().apply {
                color = Color.parseColor("#1F2937")
                strokeWidth = 1.0f
                style = Paint.Style.STROKE
            }
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 7.5f
            }

            fun fitText(text: String, maxWidth: Float, paint: Paint): String {
                if (paint.measureText(text) <= maxWidth) return text
                var low = 0
                var high = text.length
                var best = ""
                while (low <= high) {
                    val mid = (low + high) / 2
                    val candidate = text.substring(0, mid) + "…"
                    if (paint.measureText(candidate) <= maxWidth) {
                        best = candidate
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                return if (best.isNotEmpty()) best else text.take(1)
            }

            fun drawFitText(
                c: Canvas,
                text: String,
                x: Float,
                y: Float,
                maxWidth: Float,
                basePaint: Paint,
                align: Paint.Align = Paint.Align.LEFT
            ) {
                val tempPaint = Paint(basePaint)
                var currentSize = basePaint.textSize
                tempPaint.textSize = currentSize
                while (tempPaint.measureText(text) > maxWidth && currentSize > 6.2f) {
                    currentSize -= 0.3f
                    tempPaint.textSize = currentSize
                }
                val finalText = fitText(text, maxWidth, tempPaint)
                tempPaint.textAlign = align
                c.drawText(finalText, x, y, tempPaint)
            }

            fun drawFooter(c: Canvas, pNum: Int) {
                c.drawLine(marginLeft, pageHeight - 24f, marginRight, pageHeight - 24f, gridPaint)
                footerPaint.textAlign = Paint.Align.LEFT
                c.drawText("Generated by Employee Performance Tracker", marginLeft, pageHeight - 12f, footerPaint)
                footerPaint.textAlign = Paint.Align.RIGHT
                c.drawText("Page $pNum", marginRight, pageHeight - 12f, footerPaint)
            }

            val colDefs = if (isSalesManagerReport) {
                if (includeZone) {
                    listOf(
                        PdfCol("sl", "SL", 30f, true),
                        PdfCol("manager", "Sales Manager", 155f, false),
                        PdfCol("zone", "Zone", 70f, true),
                        PdfCol("officers", "Officers", 55f, true),
                        PdfCol("total", "Total", 55f, true),
                        PdfCol("target", "Target", 55f, true),
                        PdfCol("short", "SHORT", 50f, true),
                        PdfCol("achievement", "Achievement", 85f, true)
                    )
                } else {
                    listOf(
                        PdfCol("sl", "SL", 30f, true),
                        PdfCol("manager", "Sales Manager", 225f, false),
                        PdfCol("officers", "Officers", 55f, true),
                        PdfCol("total", "Total", 55f, true),
                        PdfCol("target", "Target", 55f, true),
                        PdfCol("short", "SHORT", 50f, true),
                        PdfCol("achievement", "Achievement", 85f, true)
                    )
                }
            } else {
                when {
                    includeZone && includeSalesManager -> listOf(
                        PdfCol("branch", "Branch", 105f, false),
                        PdfCol("official", "Branch Official", 105f, false),
                        PdfCol("manager", "Sales Manager", 82f, false),
                        PdfCol("zone", "Zone", 40f, true),
                        PdfCol("total", "Total", 46f, true),
                        PdfCol("target", "Target", 46f, true),
                        PdfCol("short", "SHORT", 46f, true),
                        PdfCol("achievement", "Achievement", 85f, true)
                    )
                    includeZone && !includeSalesManager -> listOf(
                        PdfCol("branch", "Branch", 135f, false),
                        PdfCol("official", "Branch Official", 145f, false),
                        PdfCol("zone", "Zone", 48f, true),
                        PdfCol("total", "Total", 46f, true),
                        PdfCol("target", "Target", 46f, true),
                        PdfCol("short", "SHORT", 46f, true),
                        PdfCol("achievement", "Achievement", 89f, true)
                    )
                    !includeZone && includeSalesManager -> listOf(
                        PdfCol("branch", "Branch", 120f, false),
                        PdfCol("official", "Branch Official", 130f, false),
                        PdfCol("manager", "Sales Manager", 115f, false),
                        PdfCol("total", "Total", 48f, true),
                        PdfCol("target", "Target", 48f, true),
                        PdfCol("short", "SHORT", 46f, true),
                        PdfCol("achievement", "Achievement", 88f, true)
                    )
                    else -> listOf(
                        PdfCol("branch", "Branch", 165f, false),
                        PdfCol("official", "Branch Official", 167f, false),
                        PdfCol("total", "Total", 49f, true),
                        PdfCol("target", "Target", 49f, true),
                        PdfCol("short", "SHORT", 47f, true),
                        PdfCol("achievement", "Achievement", 78f, true)
                    )
                }
            }

            val headerRowHeight = 22f
            val dataRowHeight = 18.5f

            fun drawHeaderRow(c: Canvas, y: Float) {
                c.drawRect(marginLeft, y, marginRight, y + headerRowHeight, headerBgPaint)
                c.drawRect(marginLeft, y, marginRight, y + headerRowHeight, outerGridPaint)

                var curX = marginLeft
                for (col in colDefs) {
                    // Header label
                    val maxLabelW = col.width - 6f
                    val labelY = y + (headerRowHeight / 2f) - ((headerTextPaint.descent() + headerTextPaint.ascent()) / 2f)
                    val labelX = if (col.isCenter) {
                        curX + (col.width / 2f)
                    } else {
                        curX + 5f
                    }
                    drawFitText(c, col.title, labelX, labelY, maxLabelW, headerTextPaint, if (col.isCenter) Paint.Align.CENTER else Paint.Align.LEFT)

                    // Vertical divider line
                    c.drawLine(curX + col.width, y, curX + col.width, y + headerRowHeight, outerGridPaint)
                    curX += col.width
                }
            }

            val empMap = allEmployees.associateBy { it.employeeId.trim() }

            // Only include officers who have actual records in the submitted data
            val groupedByOfficer = data.groupBy { it.employeeId.trim() }
            val allRelevantEmpIds = data.map { it.employeeId.trim() }.filter { it.isNotBlank() }.distinct()

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

                val baseMonthlyTarget = officerMonthlyTargets[empId]?.takeIf { it > 0 }
                    ?: officerEmp?.monthlyTarget?.takeIf { it > 0 }
                    ?: when {
                        zone.contains("Dhaka", ignoreCase = true) -> 75
                        zone.contains("CTG", ignoreCase = true) || zone.contains("Chittagong", ignoreCase = true) -> 60
                        else -> 50
                    }
                val target = baseMonthlyTarget * monthCount.coerceAtLeast(1)
                val yearlyTarget = officerYearlyTargets[empId]?.takeIf { it > 0 }
                    ?: officerEmp?.yearlyTarget?.takeIf { it > 0 }
                    ?: (baseMonthlyTarget * 12)
                val short = maxOf(0, target - cardCount)
                val rate = if (target > 0) (cardCount.toDouble() / target.toDouble()) * 100.0 else 0.0
                val rateStr = String.format(Locale.US, "%.1f%%", rate)

                OfficerSummary(
                    employeeId = empId,
                    employeeName = empName,
                    branch = branch,
                    zone = zone,
                    salesManager = salesMgr,
                    total = cardCount,
                    target = target,
                    yearlyTarget = yearlyTarget,
                    short = short,
                    achievementRate = rate,
                    achievementStr = rateStr
                )
            }.sortedWith(
                compareByDescending<OfficerSummary> { it.achievementRate }
                    .thenByDescending { it.total }
                    .thenBy { it.employeeName }
            )

            val knownSalesManagers = mutableSetOf<String>()
            officerSummaries.forEach {
                if (it.salesManager.isNotBlank() && !it.salesManager.equals("N/A", ignoreCase = true) && !it.salesManager.equals("Direct / Head Office", ignoreCase = true)) {
                    knownSalesManagers.add(it.salesManager.trim())
                }
            }

            val managersList = if (isSalesManagerReport) {
                knownSalesManagers.toList()
            } else if (managerName.isNotBlank()) {
                knownSalesManagers.filter { it.equals(managerName.trim(), ignoreCase = true) }
            } else {
                knownSalesManagers.toList()
            }

            val managerSummaries = managersList.mapNotNull { mName ->
                val officers = officerSummaries.filter { it.salesManager.equals(mName, ignoreCase = true) }
                if (officers.isEmpty()) {
                    return@mapNotNull null
                }
                val mgrUser = allEmployees.firstOrNull { it.name.trim().equals(mName, ignoreCase = true) }
                val officerCount = officers.size
                // Total is the total cards completed by the Sales Manager's team
                val totalCards = officers.sumOf { it.total }
                // Target is the Sales Manager's team's total yearly target
                val totalYearlyTarget = officers.sumOf { it.yearlyTarget }.let { sum ->
                    if (sum > 0) sum else (mgrUser?.yearlyTarget?.takeIf { it > 0 } ?: 0)
                }
                val short = maxOf(0, totalYearlyTarget - totalCards)
                val rate = if (totalYearlyTarget > 0) (totalCards.toDouble() / totalYearlyTarget.toDouble()) * 100.0 else 0.0
                val rateStr = String.format(Locale.US, "%.1f%%", rate)
                val zone = officers.map { it.zone.trim() }
                    .filter { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { mgrUser?.zone?.takeIf { it.isNotBlank() } ?: "General" }

                SalesManagerSummary(
                    managerName = mName.uppercase(Locale.getDefault()),
                    zone = zone,
                    officerCount = officerCount,
                    total = totalCards,
                    target = totalYearlyTarget,
                    short = short,
                    achievementRate = rate,
                    achievementStr = rateStr
                )
            }.sortedWith(
                compareByDescending<SalesManagerSummary> { it.achievementRate }
                    .thenByDescending { it.total }
                    .thenBy { it.managerName }
            )

            val renderSummaryTable = isSummary || !includeDetails || true // Target Summary is always primary

            if (renderSummaryTable) {
                var currentY = 24f

                // Document Header
                val reportTitleText = if (isSalesManagerReport) {
                    "SALES MANAGER PERFORMANCE & TARGET ACHIEVEMENT REPORT"
                } else {
                    "EMPLOYEE PERFORMANCE & TARGET ACHIEVEMENT REPORT"
                }
                canvas.drawText(reportTitleText, pageWidth / 2f, currentY, titlePaint)
                currentY += 13f

                val dateFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
                val metaText = StringBuilder().apply {
                    append("Period: $monthRange")
                    if (isSalesManagerReport) {
                        append("  |  Total Managers: ${managerSummaries.size}")
                        append("  |  Total Officers: ${officerSummaries.size}")
                    } else {
                        append("  |  Total Officers: ${officerSummaries.size}")
                        if (managerName.isNotBlank()) append("  |  Manager: $managerName")
                    }
                    append("  |  Generated: $dateFormatted")
                }.toString()
                canvas.drawText(metaText, pageWidth / 2f, currentY, metaPaint)
                currentY += 10f

                // Header row
                drawHeaderRow(canvas, currentY)
                currentY += headerRowHeight

                if (isSalesManagerReport) {
                    // Draw Sales Manager Performance rows
                    managerSummaries.forEachIndexed { idx, mgr ->
                        if (currentY + dataRowHeight > pageHeight - 32f) {
                            drawFooter(canvas, pageNum)
                            pdfDocument.finishPage(currentPage)
                            pageNum++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                            currentPage = pdfDocument.startPage(pageInfo)
                            canvas = currentPage.canvas

                            metaPaint.textAlign = Paint.Align.LEFT
                            canvas.drawText("SALES MANAGER PERFORMANCE & TARGET ACHIEVEMENT - $monthRange", marginLeft, 20f, metaPaint)
                            metaPaint.textAlign = Paint.Align.CENTER
                            canvas.drawLine(marginLeft, 24f, marginRight, 24f, outerGridPaint)

                            currentY = 28f
                            drawHeaderRow(canvas, currentY)
                            currentY += headerRowHeight
                        }

                        canvas.drawRect(marginLeft, currentY, marginRight, currentY + dataRowHeight, rowBgPaint)

                        val textY = currentY + (dataRowHeight / 2f) - ((dataBoldPaint.descent() + dataBoldPaint.ascent()) / 2f)
                        var curX = marginLeft

                        for (col in colDefs) {
                            when (col.key) {
                                "sl" -> drawFitText(canvas, (idx + 1).toString(), curX + (col.width / 2f), textY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                                "manager" -> drawFitText(canvas, mgr.managerName, curX + 4f, textY, col.width - 8f, dataBoldPaint, Paint.Align.LEFT)
                                "zone" -> drawFitText(canvas, mgr.zone, curX + (col.width / 2f), textY, col.width - 4f, dataTextPaint, Paint.Align.CENTER)
                                "officers" -> drawFitText(canvas, mgr.officerCount.toString(), curX + (col.width / 2f), textY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                                "total" -> drawFitText(canvas, mgr.total.toString(), curX + (col.width / 2f), textY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                                "target" -> drawFitText(canvas, mgr.target.toString(), curX + (col.width / 2f), textY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                                "short" -> drawFitText(canvas, mgr.short.toString(), curX + (col.width / 2f), textY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                                "achievement" -> {
                                    val achPaint = if (mgr.achievementRate >= 50.0) achievementGreenPaint else achievementRedPaint
                                    drawFitText(canvas, mgr.achievementStr, curX + (col.width / 2f), textY, col.width - 4f, achPaint, Paint.Align.CENTER)
                                }
                            }
                            curX += col.width
                        }

                        // Cell Borders
                        canvas.drawLine(marginLeft, currentY + dataRowHeight, marginRight, currentY + dataRowHeight, gridPaint)
                        canvas.drawLine(marginLeft, currentY, marginLeft, currentY + dataRowHeight, outerGridPaint)
                        canvas.drawLine(marginRight, currentY, marginRight, currentY + dataRowHeight, outerGridPaint)

                        var lineX = marginLeft
                        for (col in colDefs) {
                            lineX += col.width
                            canvas.drawLine(lineX, currentY, lineX, currentY + dataRowHeight, gridPaint)
                        }

                        currentY += dataRowHeight
                    }
                } else {
                    // Draw Officer Performance rows (with dynamic Zone and Sales Manager)
                    for (officer in officerSummaries) {
                        if (currentY + dataRowHeight > pageHeight - 32f) {
                            drawFooter(canvas, pageNum)
                            pdfDocument.finishPage(currentPage)
                            pageNum++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                            currentPage = pdfDocument.startPage(pageInfo)
                            canvas = currentPage.canvas

                            // Running title on continuation page
                            metaPaint.textAlign = Paint.Align.LEFT
                            canvas.drawText("EMPLOYEE PERFORMANCE & TARGET ACHIEVEMENT - $monthRange", marginLeft, 20f, metaPaint)
                            metaPaint.textAlign = Paint.Align.CENTER
                            canvas.drawLine(marginLeft, 24f, marginRight, 24f, outerGridPaint)

                            currentY = 28f
                            drawHeaderRow(canvas, currentY)
                            currentY += headerRowHeight
                        }

                        // Background
                        canvas.drawRect(marginLeft, currentY, marginRight, currentY + dataRowHeight, rowBgPaint)

                        val textY = currentY + (dataRowHeight / 2f) - ((dataTextPaint.descent() + dataTextPaint.ascent()) / 2f)
                        var curX = marginLeft

                        for (col in colDefs) {
                            when (col.key) {
                                "branch" -> drawFitText(canvas, officer.branch, curX + 4f, textY, col.width - 8f, dataTextPaint, Paint.Align.LEFT)
                                "official" -> drawFitText(canvas, officer.employeeName, curX + 4f, textY, col.width - 8f, dataBoldPaint, Paint.Align.LEFT)
                                "manager" -> drawFitText(canvas, officer.salesManager, curX + 4f, textY, col.width - 8f, dataTextPaint, Paint.Align.LEFT)
                                "zone" -> drawFitText(canvas, officer.zone, curX + (col.width / 2f), textY, col.width - 4f, dataTextPaint, Paint.Align.CENTER)
                                "total" -> drawFitText(canvas, officer.total.toString(), curX + (col.width / 2f), textY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                                "target" -> drawFitText(canvas, officer.target.toString(), curX + (col.width / 2f), textY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                                "short" -> drawFitText(canvas, officer.short.toString(), curX + (col.width / 2f), textY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                                "achievement" -> {
                                    val achPaint = if (officer.achievementRate >= 50.0) achievementGreenPaint else achievementRedPaint
                                    drawFitText(canvas, officer.achievementStr, curX + (col.width / 2f), textY, col.width - 4f, achPaint, Paint.Align.CENTER)
                                }
                            }
                            curX += col.width
                        }

                        // Cell Borders
                        canvas.drawLine(marginLeft, currentY + dataRowHeight, marginRight, currentY + dataRowHeight, gridPaint)
                        canvas.drawLine(marginLeft, currentY, marginLeft, currentY + dataRowHeight, outerGridPaint)
                        canvas.drawLine(marginRight, currentY, marginRight, currentY + dataRowHeight, outerGridPaint)

                        var lineX = marginLeft
                        for (col in colDefs) {
                            lineX += col.width
                            canvas.drawLine(lineX, currentY, lineX, currentY + dataRowHeight, gridPaint)
                        }

                        currentY += dataRowHeight
                    }
                }

                // Total summary row at bottom
                if (currentY + 22f > pageHeight - 32f) {
                    drawFooter(canvas, pageNum)
                    pdfDocument.finishPage(currentPage)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    currentY = 28f
                    drawHeaderRow(canvas, currentY)
                    currentY += headerRowHeight
                }

                val totalRowHeight = 22f
                canvas.drawRect(marginLeft, currentY, marginRight, currentY + totalRowHeight, totalRowBgPaint)

                val totalTextY = currentY + (totalRowHeight / 2f) - ((dataBoldPaint.descent() + dataBoldPaint.ascent()) / 2f)

                val sumTotal = if (isSalesManagerReport) managerSummaries.sumOf { it.total } else officerSummaries.sumOf { it.total }
                val sumTarget = if (isSalesManagerReport) managerSummaries.sumOf { it.target } else officerSummaries.sumOf { it.target }
                val sumShort = if (isSalesManagerReport) managerSummaries.sumOf { it.short } else officerSummaries.sumOf { it.short }
                val sumOfficers = if (isSalesManagerReport) managerSummaries.sumOf { it.officerCount } else officerSummaries.size
                val overallRate = if (sumTarget > 0) (sumTotal.toDouble() / sumTarget.toDouble()) * 100.0 else 0.0
                val overallRateStr = String.format(Locale.US, "%.1f%%", overallRate)
                val totalAchPaint = if (overallRate >= 50.0) achievementGreenPaint else achievementRedPaint

                var curTotalX = marginLeft
                for (col in colDefs) {
                    when (col.key) {
                        "branch", "manager" -> {
                            drawFitText(canvas, "Total", curTotalX + 5f, totalTextY, col.width - 8f, dataBoldPaint, Paint.Align.LEFT)
                        }
                        "officers" -> {
                            drawFitText(canvas, sumOfficers.toString(), curTotalX + (col.width / 2f), totalTextY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                        }
                        "total" -> {
                            drawFitText(canvas, sumTotal.toString(), curTotalX + (col.width / 2f), totalTextY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                        }
                        "target" -> {
                            drawFitText(canvas, sumTarget.toString(), curTotalX + (col.width / 2f), totalTextY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                        }
                        "short" -> {
                            drawFitText(canvas, sumShort.toString(), curTotalX + (col.width / 2f), totalTextY, col.width - 4f, dataBoldPaint, Paint.Align.CENTER)
                        }
                        "achievement" -> {
                            drawFitText(canvas, overallRateStr, curTotalX + (col.width / 2f), totalTextY, col.width - 4f, totalAchPaint, Paint.Align.CENTER)
                        }
                    }
                    curTotalX += col.width
                }

                // Borders for total row (double bottom border)
                canvas.drawLine(marginLeft, currentY, marginRight, currentY, outerGridPaint)
                canvas.drawLine(marginLeft, currentY + totalRowHeight - 1.5f, marginRight, currentY + totalRowHeight - 1.5f, outerGridPaint)
                canvas.drawLine(marginLeft, currentY + totalRowHeight, marginRight, currentY + totalRowHeight, outerGridPaint)
                canvas.drawLine(marginLeft, currentY, marginLeft, currentY + totalRowHeight, outerGridPaint)
                canvas.drawLine(marginRight, currentY, marginRight, currentY + totalRowHeight, outerGridPaint)

                var tLineX = marginLeft
                for (col in colDefs) {
                    tLineX += col.width
                    canvas.drawLine(tLineX, currentY, tLineX, currentY + totalRowHeight, gridPaint)
                }

                currentY += totalRowHeight
            }

            // If user explicitly selected Full Report or Details, append Card Application Details
            if (includeDetails) {
                val details = data.filter { !it.limit.equals("NIL", ignoreCase = true) }
                if (details.isNotEmpty()) {
                    // Start new page for details
                    drawFooter(canvas, pageNum)
                    pdfDocument.finishPage(currentPage)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas

                    var currentY = 24f
                    canvas.drawText("INDIVIDUAL CARD APPLICATION DETAILS", pageWidth / 2f, currentY, titlePaint)
                    currentY += 13f
                    canvas.drawText("Period: $monthRange  |  Total Approved Cards: ${details.size}", pageWidth / 2f, currentY, metaPaint)
                    currentY += 12f

                    val detailCols = listOf(
                        PdfCol("SL", 28f, true),
                        PdfCol("Month", 58f, false),
                        PdfCol("Officer", 100f, false),
                        PdfCol("Branch", 95f, false),
                        PdfCol("Applicant Name", 115f, false),
                        PdfCol("A/C No", 95f, false),
                        PdfCol("Limit", 64f, true)
                    )

                    fun drawDetailHeader(c: Canvas, y: Float) {
                        c.drawRect(marginLeft, y, marginRight, y + headerRowHeight, headerBgPaint)
                        c.drawRect(marginLeft, y, marginRight, y + headerRowHeight, outerGridPaint)
                        var dCurX = marginLeft
                        for (dCol in detailCols) {
                            val dLabelY = y + (headerRowHeight / 2f) - ((headerTextPaint.descent() + headerTextPaint.ascent()) / 2f)
                            val dLabelX = if (dCol.isCenter) dCurX + (dCol.width / 2f) else dCurX + 4f
                            drawFitText(c, dCol.title, dLabelX, dLabelY, dCol.width - 6f, headerTextPaint, if (dCol.isCenter) Paint.Align.CENTER else Paint.Align.LEFT)
                            c.drawLine(dCurX + dCol.width, y, dCurX + dCol.width, y + headerRowHeight, outerGridPaint)
                            dCurX += dCol.width
                        }
                    }

                    drawDetailHeader(canvas, currentY)
                    currentY += headerRowHeight

                    details.forEachIndexed { idx, item ->
                        if (currentY + dataRowHeight > pageHeight - 32f) {
                            drawFooter(canvas, pageNum)
                            pdfDocument.finishPage(currentPage)
                            pageNum++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                            currentPage = pdfDocument.startPage(pageInfo)
                            canvas = currentPage.canvas

                            metaPaint.textAlign = Paint.Align.LEFT
                            canvas.drawText("CARD APPLICATION DETAILS - $monthRange", marginLeft, 20f, metaPaint)
                            metaPaint.textAlign = Paint.Align.CENTER
                            canvas.drawLine(marginLeft, 24f, marginRight, 24f, outerGridPaint)

                            currentY = 28f
                            drawDetailHeader(canvas, currentY)
                            currentY += headerRowHeight
                        }

                        val isEven = (idx % 2 == 0)
                        rowBgPaint.color = if (isEven) Color.parseColor("#F8FAFC") else Color.WHITE
                        canvas.drawRect(marginLeft, currentY, marginRight, currentY + dataRowHeight, rowBgPaint)

                        val dTextY = currentY + (dataRowHeight / 2f) - ((dataTextPaint.descent() + dataTextPaint.ascent()) / 2f)
                        var dCurX = marginLeft

                        val rowVals = listOf(
                            (idx + 1).toString(),
                            item.month,
                            item.employeeName,
                            item.branch,
                            item.applicantName,
                            item.accountNo,
                            item.limit.ifBlank { "-" }
                        )

                        for (cIdx in detailCols.indices) {
                            val col = detailCols[cIdx]
                            val textVal = rowVals[cIdx]
                            val tX = if (col.isCenter) dCurX + (col.width / 2f) else dCurX + 4f
                            val p = if (cIdx == 2) dataBoldPaint else dataTextPaint
                            drawFitText(canvas, textVal, tX, dTextY, col.width - 6f, p, if (col.isCenter) Paint.Align.CENTER else Paint.Align.LEFT)
                            canvas.drawLine(dCurX + col.width, currentY, dCurX + col.width, currentY + dataRowHeight, gridPaint)
                            dCurX += col.width
                        }

                        canvas.drawLine(marginLeft, currentY + dataRowHeight, marginRight, currentY + dataRowHeight, gridPaint)
                        canvas.drawLine(marginLeft, currentY, marginLeft, currentY + dataRowHeight, outerGridPaint)
                        canvas.drawLine(marginRight, currentY, marginRight, currentY + dataRowHeight, outerGridPaint)

                        currentY += dataRowHeight
                    }
                }
            }

            // Draw footer on final page
            drawFooter(canvas, pageNum)
            pdfDocument.finishPage(currentPage)

            // Write to file
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            val shareUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                targetFile
            )

            // Save copy to Downloads for user file explorer access across all Android versions
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val cv = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val downloadUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    if (downloadUri != null) {
                        context.contentResolver.openOutputStream(downloadUri)?.use { out ->
                            targetFile.inputStream().use { input -> input.copyTo(out) }
                        }
                    }
                } else {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadDir.exists()) downloadDir.mkdirs()
                    val destFile = File(downloadDir, fileName)
                    targetFile.copyTo(destFile, overwrite = true)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            if (isShare) {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Monthly Performance Report - $monthRange")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Monthly PDF Report").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot share report: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Monthly PDF report generated successfully!", Toast.LENGTH_SHORT).show()
                showNotification(context, fileName, shareUri, mimeType)

                // Safely show dialog with direct Open PDF and Share buttons
                if (context is Activity && !context.isFinishing && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !context.isDestroyed)) {
                    try {
                        val builder = AlertDialog.Builder(context)
                        builder.setTitle("Monthly PDF Ready")
                        builder.setMessage("Report has been generated successfully!\n\nPeriod: $monthRange\nRecords: ${data.size} items\nFile: $fileName")
                        builder.setIcon(R.drawable.ic_file_pdf)
                        builder.setPositiveButton("Open PDF") { _, _ ->
                            try {
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(shareUri, mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open PDF with"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No PDF viewer found on device", Toast.LENGTH_SHORT).show()
                            }
                        }
                        builder.setNeutralButton("Share") { _, _ ->
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Monthly Performance Report - $monthRange")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Monthly PDF Report"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot share report: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        builder.setNegativeButton("Close", null)
                        builder.show()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ========================================================
    // 2. KOTLIN-CSV-JVM: Employee Performance CSV Export
    // ========================================================
    fun exportPerformanceCsv(
        context: Context,
        data: List<Performance>,
        monthRange: String,
        isSummary: Boolean,
        isShare: Boolean
    ) {
        if (data.isEmpty()) {
            Toast.makeText(context, "No performance records found to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Employee_Performance_$timestamp.csv"
            val mimeType = "text/csv"

            val targetFile: File?
            val outputStream: OutputStream?
            val shareUri: Uri?

            if (isShare) {
                val reportsDir = File(context.cacheDir, "reports")
                if (!reportsDir.exists()) reportsDir.mkdirs()
                targetFile = File(reportsDir, fileName)
                outputStream = FileOutputStream(targetFile)
                shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )
            } else {
                targetFile = null
                val pair = getDownloadStream(context, fileName, mimeType)
                shareUri = pair.first
                outputStream = pair.second
            }

            if (outputStream == null) {
                Toast.makeText(context, "Unable to create CSV output stream", Toast.LENGTH_SHORT).show()
                return
            }

            outputStream.use { stream ->
                csvWriter().open(stream) {
                    writeRow(listOf("Employee Performance Data Export"))
                    writeRow(listOf("Period", monthRange))
                    writeRow(listOf("Exported At", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
                    writeRow(listOf(""))

                    if (isSummary) {
                        writeRow(listOf("SL", "Month", "Employee ID", "Officer Name", "Branch", "Zone", "Sales Manager", "Total Cards"))
                        val grouped = data.groupBy { it.employeeId + "_" + it.month }.map { (_, list) ->
                            val first = list.first()
                            val count = if (list.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else list.size
                            first to count
                        }

                        grouped.forEachIndexed { idx, (first, count) ->
                            writeRow(listOf(
                                (idx + 1).toString(),
                                first.month,
                                first.employeeId,
                                first.employeeName,
                                first.branch,
                                first.zone,
                                first.salesManager,
                                count.toString()
                            ))
                        }
                    } else {
                        writeRow(listOf("SL", "Month", "Employee ID", "Officer Name", "Branch", "Zone", "Sales Manager", "Applicant Name", "Account No", "Limit"))
                        val details = data.filter { !it.limit.equals("NIL", ignoreCase = true) }

                        details.forEachIndexed { idx, item ->
                            writeRow(listOf(
                                (idx + 1).toString(),
                                item.month,
                                item.employeeId,
                                item.employeeName,
                                item.branch,
                                item.zone,
                                item.salesManager,
                                item.applicantName,
                                item.accountNo,
                                item.limit
                            ))
                        }
                    }
                }
            }

            if (isShare && shareUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Employee Performance Data ($monthRange)")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share CSV Report"))
            } else {
                Toast.makeText(context, "CSV exported to Downloads folder", Toast.LENGTH_SHORT).show()
                showNotification(context, fileName, shareUri, mimeType)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "CSV Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ========================================================
    // 3. STANDARD .XLSX: Employee Performance Excel Export
    // ========================================================
    enum class XlsxReportMode { FULL, SUMMARY, DETAILS }

    fun exportPerformanceXlsx(
        context: Context,
        data: List<Performance>,
        monthRange: String,
        officerTargets: Map<String, Int> = emptyMap(),
        officerYearlyTargets: Map<String, Int> = emptyMap(),
        includeZone: Boolean = true,
        includeSalesManager: Boolean = false,
        includeApplicantDetails: Boolean = true,
        isShare: Boolean = false,
        isSalesManagerReport: Boolean = false,
        monthCount: Int = 1,
        allEmployees: List<User> = emptyList(),
        managerName: String = ""
    ) {
        if (data.isEmpty()) {
            Toast.makeText(context, "No performance records found to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Performance_Report_$timestamp.xlsx"
            val mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

            val targetFile: File?
            val outputStream: OutputStream?
            val shareUri: Uri?

            if (isShare) {
                val reportsDir = File(context.cacheDir, "reports")
                if (!reportsDir.exists()) reportsDir.mkdirs()
                targetFile = File(reportsDir, fileName)
                outputStream = FileOutputStream(targetFile)
                shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )
            } else {
                targetFile = null
                val pair = getDownloadStream(context, fileName, mimeType)
                shareUri = pair.first
                outputStream = pair.second
            }

            if (outputStream == null) {
                Toast.makeText(context, "Unable to create Excel output stream", Toast.LENGTH_SHORT).show()
                return
            }

            val sheets = mutableListOf<XlsxGenerator.SheetDef>()
            val nowFormatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

            val empMap = allEmployees.associateBy { it.employeeId.trim() }

            // Only include officers who have actual records in the submitted data
            val groupedByOfficer = data.groupBy { it.employeeId.trim() }
            val allRelevantEmpIds = data.map { it.employeeId.trim() }.filter { it.isNotBlank() }.distinct()

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

                val baseMonthlyTarget = officerTargets[empId]?.takeIf { it > 0 }
                    ?: officerEmp?.monthlyTarget?.takeIf { it > 0 }
                    ?: when {
                        zone.contains("Dhaka", ignoreCase = true) -> 75
                        zone.contains("CTG", ignoreCase = true) || zone.contains("Chittagong", ignoreCase = true) -> 60
                        else -> 50
                    }
                val target = baseMonthlyTarget * monthCount.coerceAtLeast(1)
                val yearlyTarget = officerYearlyTargets[empId]?.takeIf { it > 0 }
                    ?: officerEmp?.yearlyTarget?.takeIf { it > 0 }
                    ?: (baseMonthlyTarget * 12)
                val short = maxOf(0, target - cardCount)
                val rate = if (target > 0) (cardCount.toDouble() / target.toDouble()) * 100.0 else 0.0
                val rateStr = String.format(Locale.US, "%.1f%%", rate)

                OfficerSummary(
                    employeeId = empId,
                    employeeName = empName,
                    branch = branch,
                    zone = zone,
                    salesManager = salesMgr,
                    total = cardCount,
                    target = target,
                    yearlyTarget = yearlyTarget,
                    short = short,
                    achievementRate = rate,
                    achievementStr = rateStr
                )
            }.sortedWith(
                compareByDescending<OfficerSummary> { it.achievementRate }
                    .thenByDescending { it.total }
                    .thenBy { it.employeeName }
            )

            val totalAchievedSum = officerSummaries.sumOf { it.total }
            val totalTargetSum = officerSummaries.sumOf { it.target }
            val totalShortSum = officerSummaries.sumOf { it.short }
            val overallRate = if (totalTargetSum > 0) (totalAchievedSum.toDouble() / totalTargetSum.toDouble()) * 100.0 else 0.0
            val overallRateStr = String.format(Locale.US, "%.1f%%", overallRate)

            // Dynamic columns matching the uploaded Excel image layout:
            // Branch | Branch Official | (Sales Manager) | (Zone) | Total | Target | SHORT | Achievement
            val summaryCols = mutableListOf<XlsxGenerator.ColumnDef>()
            summaryCols.add(XlsxGenerator.ColumnDef("Branch", width = 24.0, isBold = false))
            summaryCols.add(XlsxGenerator.ColumnDef("Branch Official", width = 26.0, isBold = true))
            if (includeSalesManager) {
                summaryCols.add(XlsxGenerator.ColumnDef("Sales Manager", width = 24.0, isBold = false))
            }
            if (includeZone) {
                summaryCols.add(XlsxGenerator.ColumnDef("Zone", width = 14.0, isCenter = true, isBold = false))
            }
            summaryCols.add(XlsxGenerator.ColumnDef("Total", width = 12.0, isNumeric = true, isCenter = true, isBold = true))
            summaryCols.add(XlsxGenerator.ColumnDef("Target", width = 12.0, isNumeric = true, isCenter = true, isBold = true))
            summaryCols.add(XlsxGenerator.ColumnDef("SHORT", width = 12.0, isNumeric = true, isCenter = true, isBold = true))
            summaryCols.add(XlsxGenerator.ColumnDef("Achievement", width = 16.0, isCenter = true, isBold = true, isAchievement = true))

            val summaryRows = officerSummaries.map { officer ->
                val row = mutableListOf<Any?>()
                row.add(officer.branch)
                row.add(officer.employeeName)
                if (includeSalesManager) {
                    row.add(officer.salesManager)
                }
                if (includeZone) {
                    row.add(officer.zone)
                }
                row.add(officer.total.toLong())
                row.add(officer.target.toLong())
                row.add(officer.short.toLong())
                row.add(officer.achievementStr)
                row
            }

            val summaryTotalRow = mutableListOf<Any?>()
            summaryTotalRow.add("Total")
            summaryTotalRow.add("")
            if (includeSalesManager) {
                summaryTotalRow.add("")
            }
            if (includeZone) {
                summaryTotalRow.add("")
            }
            summaryTotalRow.add(totalAchievedSum.toLong())
            summaryTotalRow.add(totalTargetSum.toLong())
            summaryTotalRow.add(totalShortSum.toLong())
            summaryTotalRow.add(overallRateStr)

            val summaryMeta = listOf(
                "Print / Generated Date:" to nowFormatted,
                "Total Active Officers:" to "${officerSummaries.size} Officers",
                "Total Search Records:" to "${data.size} items"
            )

            // 2. Grouped Performance Data per Sales Manager
            val knownSalesManagers = mutableSetOf<String>()
            officerSummaries.forEach {
                if (it.salesManager.isNotBlank() && !it.salesManager.equals("N/A", ignoreCase = true) && !it.salesManager.equals("Direct / Head Office", ignoreCase = true)) {
                    knownSalesManagers.add(it.salesManager.trim())
                }
            }

            val managersList = if (isSalesManagerReport) {
                knownSalesManagers.toList()
            } else if (managerName.isNotBlank()) {
                knownSalesManagers.filter { it.equals(managerName.trim(), ignoreCase = true) }
            } else {
                knownSalesManagers.toList()
            }

            val managerSummaries = managersList.mapNotNull { mName ->
                val officers = officerSummaries.filter { it.salesManager.equals(mName, ignoreCase = true) }
                if (officers.isEmpty()) {
                    return@mapNotNull null
                }
                val mgrUser = allEmployees.firstOrNull { it.name.trim().equals(mName, ignoreCase = true) }
                val officerCount = officers.size
                // Total is the total cards completed by the Sales Manager's team
                val totalCards = officers.sumOf { it.total }
                // Target is the Sales Manager's team's total yearly target
                val totalYearlyTarget = officers.sumOf { it.yearlyTarget }.let { sum ->
                    if (sum > 0) sum else (mgrUser?.yearlyTarget?.takeIf { it > 0 } ?: 0)
                }
                val short = maxOf(0, totalYearlyTarget - totalCards)
                val rate = if (totalYearlyTarget > 0) (totalCards.toDouble() / totalYearlyTarget.toDouble()) * 100.0 else 0.0
                val rateStr = String.format(Locale.US, "%.1f%%", rate)
                val zone = officers.map { it.zone.trim() }
                    .filter { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { mgrUser?.zone?.takeIf { it.isNotBlank() } ?: "General" }

                SalesManagerSummary(
                    managerName = mName.uppercase(Locale.getDefault()),
                    zone = zone,
                    officerCount = officerCount,
                    total = totalCards,
                    target = totalYearlyTarget,
                    short = short,
                    achievementRate = rate,
                    achievementStr = rateStr
                )
            }.sortedWith(
                compareByDescending<SalesManagerSummary> { it.achievementRate }
                    .thenByDescending { it.total }
                    .thenBy { it.managerName }
            )

            val smTotalAchieved = managerSummaries.sumOf { it.total }
            val smTotalTarget = managerSummaries.sumOf { it.target }
            val smTotalShort = managerSummaries.sumOf { it.short }
            val smTotalOfficers = managerSummaries.sumOf { it.officerCount }
            val smOverallRate = if (smTotalTarget > 0) (smTotalAchieved.toDouble() / smTotalTarget.toDouble()) * 100.0 else 0.0
            val smOverallRateStr = String.format(Locale.US, "%.1f%%", smOverallRate)

            val smCols = mutableListOf<XlsxGenerator.ColumnDef>()
            smCols.add(XlsxGenerator.ColumnDef("SL", width = 8.0, isNumeric = true, isCenter = true))
            smCols.add(XlsxGenerator.ColumnDef("Sales Manager", width = 26.0, isBold = true))
            if (includeZone) {
                smCols.add(XlsxGenerator.ColumnDef("Zone", width = 16.0, isCenter = true))
            }
            smCols.add(XlsxGenerator.ColumnDef("Officers", width = 12.0, isNumeric = true, isCenter = true, isBold = true))
            smCols.add(XlsxGenerator.ColumnDef("Total", width = 12.0, isNumeric = true, isCenter = true, isBold = true))
            smCols.add(XlsxGenerator.ColumnDef("Target", width = 12.0, isNumeric = true, isCenter = true, isBold = true))
            smCols.add(XlsxGenerator.ColumnDef("SHORT", width = 12.0, isNumeric = true, isCenter = true, isBold = true))
            smCols.add(XlsxGenerator.ColumnDef("Achievement", width = 16.0, isCenter = true, isBold = true, isAchievement = true))

            val smRows = managerSummaries.mapIndexed { idx, mgr ->
                val row = mutableListOf<Any?>()
                row.add((idx + 1).toLong())
                row.add(mgr.managerName)
                if (includeZone) {
                    row.add(mgr.zone)
                }
                row.add(mgr.officerCount.toLong())
                row.add(mgr.total.toLong())
                row.add(mgr.target.toLong())
                row.add(mgr.short.toLong())
                row.add(mgr.achievementStr)
                row
            }

            val smTotalRow = mutableListOf<Any?>()
            smTotalRow.add("Total")
            smTotalRow.add("")
            if (includeZone) {
                smTotalRow.add("")
            }
            smTotalRow.add(smTotalOfficers.toLong())
            smTotalRow.add(smTotalAchieved.toLong())
            smTotalTarget.toLong().let { smTotalRow.add(it) }
            smTotalShort.toLong().let { smTotalRow.add(it) }
            smTotalRow.add(smOverallRateStr)

            val smMeta = listOf(
                "Print / Generated Date:" to nowFormatted,
                "Total Managers:" to "${managerSummaries.size} Managers",
                "Total Supervised Officers:" to "${officerSummaries.size} Officers",
                "Total Search Records:" to "${data.size} items"
            )

            val smSheetDef = XlsxGenerator.SheetDef(
                name = if (isSalesManagerReport) "Sales Manager Performance" else "Sales Manager Summary",
                reportTitle = "SALES MANAGER PERFORMANCE REPORT",
                period = monthRange,
                metadata = smMeta,
                columns = smCols,
                rows = smRows,
                totalRow = smTotalRow,
                enableAutoFilter = true
            )

            val officerSheetDef = XlsxGenerator.SheetDef(
                name = if (isSalesManagerReport) "Officer Breakdown" else "Performance Summary",
                reportTitle = "EMPLOYEE PERFORMANCE REPORT",
                period = monthRange,
                metadata = summaryMeta,
                columns = summaryCols,
                rows = summaryRows,
                totalRow = summaryTotalRow,
                enableAutoFilter = true
            )

            if (isSalesManagerReport) {
                sheets.add(smSheetDef)
                sheets.add(officerSheetDef)
            } else {
                sheets.add(officerSheetDef)
                sheets.add(smSheetDef)
            }

            // Sheet 2: Optional Applicant / Cards Details
            if (includeApplicantDetails) {
                val details = data.filter { !it.limit.equals("NIL", ignoreCase = true) }
                val detailCols = listOf(
                    XlsxGenerator.ColumnDef("SL", width = 8.0, isNumeric = true, isCenter = true),
                    XlsxGenerator.ColumnDef("Month", width = 16.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Employee ID", width = 16.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Officer Name", width = 26.0, isBold = true),
                    XlsxGenerator.ColumnDef("Branch", width = 22.0),
                    XlsxGenerator.ColumnDef("Zone", width = 16.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Sales Manager", width = 22.0),
                    XlsxGenerator.ColumnDef("Applicant Name", width = 26.0),
                    XlsxGenerator.ColumnDef("Account No", width = 22.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Limit / Status", width = 16.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Submission Date", width = 22.0, isCenter = true)
                )

                val detailRows = details.mapIndexed { idx, item ->
                    listOf<Any?>(
                        (idx + 1).toLong(),
                        item.month,
                        item.employeeId,
                        item.employeeName,
                        item.branch,
                        item.zone,
                        item.salesManager,
                        item.applicantName,
                        item.accountNo,
                        item.limit,
                        item.timestamp
                    )
                }

                val detailTotalRow = listOf<Any?>(
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "TOTAL RECORDS",
                    details.size.toLong(),
                    "",
                    "",
                    ""
                )

                val detailMeta = listOf(
                    "Exported On:" to nowFormatted,
                    "Total Verified Cards:" to details.size.toString()
                )

                sheets.add(
                    XlsxGenerator.SheetDef(
                        name = "Applicant Details",
                        reportTitle = "FULL APPLICANT PERFORMANCE RECORDS",
                        period = monthRange,
                        metadata = detailMeta,
                        columns = detailCols,
                        rows = detailRows,
                        totalRow = detailTotalRow,
                        enableAutoFilter = true
                    )
                )
            }

            outputStream.use { stream ->
                XlsxGenerator.writeWorkbook(stream, sheets)
            }

            if (isShare && shareUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Employee Performance Data ($monthRange) - Excel")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Excel (.xlsx) Report"))
            } else {
                Toast.makeText(context, "Excel report (.xlsx) saved to Downloads", Toast.LENGTH_SHORT).show()
                showNotification(context, fileName, shareUri, mimeType)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Excel Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getDownloadStream(context: Context, fileName: String, mimeType: String): Pair<Uri?, OutputStream?> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                val stream = uri?.let { context.contentResolver.openOutputStream(it) }
                if (uri != null && stream != null) {
                    Pair(uri, stream)
                } else {
                    fallbackLocalFile(context, fileName)
                }
            } else {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) downloadDir.mkdirs()
                val file = File(downloadDir, fileName)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val stream = FileOutputStream(file)
                Pair(uri, stream)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            fallbackLocalFile(context, fileName)
        }
    }

    private fun fallbackLocalFile(context: Context, fileName: String): Pair<Uri?, OutputStream?> {
        return try {
            val dir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
            val file = File(dir, fileName)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val stream = FileOutputStream(file)
            Pair(uri, stream)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(null, null)
        }
    }

    private fun showNotification(context: Context, fileName: String, fileUri: Uri?, mimeType: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Report Downloads",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for downloaded performance reports (PDF & Excel)"
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            val isPdf = mimeType.contains("pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)
            val isExcel = mimeType.contains("spreadsheet", ignoreCase = true) || fileName.endsWith(".xlsx", ignoreCase = true)

            val title = when {
                isPdf -> "📄 PDF Report Downloaded"
                isExcel -> "📊 Excel Report Downloaded"
                else -> "📥 Report Downloaded"
            }

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (System.currentTimeMillis() % 100000).toInt(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val sharePendingIntent = PendingIntent.getActivity(
                context,
                ((System.currentTimeMillis() + 1) % 100000).toInt(),
                Intent.createChooser(shareIntent, "Share Report"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(fileName)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText("File: $fileName\nLocation: Downloads folder\nTap to open or share.")
                )
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_view, "Open", pendingIntent)
                .addAction(android.R.drawable.ic_menu_share, "Share", sharePendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build()

            notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
