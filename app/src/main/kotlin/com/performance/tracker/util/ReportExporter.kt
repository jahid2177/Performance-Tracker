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
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private const val CHANNEL_ID = "performance_reports"

    private data class PdfCol(val title: String, val width: Float, val isCenter: Boolean)

    // ========================================================
    // 1. NATIVE ANDROID PDF: Monthly Performance Report (Download / Share)
    // ========================================================
    fun generateMonthlyPdf(
        context: Context,
        data: List<Performance>,
        monthRange: String,
        managerName: String,
        isSummary: Boolean,
        isShare: Boolean
    ) {
        if (data.isEmpty()) {
            Toast.makeText(context, "No performance data found for the selected period", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Monthly_Performance_Report_$timestamp.pdf"
            val mimeType = "application/pdf"

            // Always write first to cache directory to ensure guaranteed success without permission failure
            val reportsDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
            val targetFile = File(reportsDir, fileName)
            val outputStream = FileOutputStream(targetFile)

            // PDF Dimensions (Standard A4 @ 72 DPI)
            val pageWidth = 595
            val pageHeight = 842
            val marginLeft = 36f
            val marginRight = 559f
            val contentWidth = marginRight - marginLeft // 523f

            val pdfDocument = PdfDocument()
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas = currentPage.canvas

            // Paints
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E40AF")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 8.5f
                textAlign = Paint.Align.CENTER
            }
            val statsCardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F1F5F9")
                style = Paint.Style.FILL
            }
            val statsCardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val statsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val headerBgPaint = Paint().apply {
                color = Color.parseColor("#1E3A8A")
                style = Paint.Style.FILL
            }
            val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rowBgPaint = Paint().apply {
                style = Paint.Style.FILL
            }
            val dataTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1E293B")
                textSize = 8f
            }
            val dataBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                strokeWidth = 0.5f
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

            fun drawFooter(c: Canvas, pNum: Int) {
                c.drawLine(marginLeft, pageHeight - 32f, marginRight, pageHeight - 32f, dividerPaint)
                c.drawText("Generated by Employee Performance Tracker", marginLeft, pageHeight - 18f, footerPaint)
                val pText = "Page $pNum"
                val pWidth = footerPaint.measureText(pText)
                c.drawText(pText, marginRight - pWidth, pageHeight - 18f, footerPaint)
            }

            val summaryCols = listOf(
                PdfCol("SL", 35f, true),
                PdfCol("Month", 75f, false),
                PdfCol("Officer Name", 175f, false),
                PdfCol("Branch", 148f, false),
                PdfCol("Total Cards", 90f, true)
            )

            val detailCols = listOf(
                PdfCol("SL", 28f, true),
                PdfCol("Month", 58f, false),
                PdfCol("Officer", 95f, false),
                PdfCol("Branch", 85f, false),
                PdfCol("Applicant Name", 110f, false),
                PdfCol("A/C No", 92f, false),
                PdfCol("Limit", 55f, true)
            )

            val activeCols = if (isSummary) summaryCols else detailCols
            val headerRowHeight = 24f
            val dataRowHeight = 20f

            fun drawHeaderRow(c: Canvas, y: Float) {
                c.drawRect(marginLeft, y, marginRight, y + headerRowHeight, headerBgPaint)
                var curX = marginLeft
                for (col in activeCols) {
                    val label = fitText(col.title, col.width - 4f, headerTextPaint)
                    val labelY = y + (headerRowHeight / 2f) - ((headerTextPaint.descent() + headerTextPaint.ascent()) / 2f)
                    val labelX = if (col.isCenter) {
                        curX + (col.width - headerTextPaint.measureText(label)) / 2f
                    } else {
                        curX + 5f
                    }
                    c.drawText(label, labelX, labelY, headerTextPaint)

                    dividerPaint.color = Color.parseColor("#3B82F6")
                    dividerPaint.strokeWidth = 0.5f
                    c.drawLine(curX + col.width, y, curX + col.width, y + headerRowHeight, dividerPaint)

                    curX += col.width
                }
            }

            var currentY = 40f

            // Document Title
            canvas.drawText("MONTHLY PERFORMANCE REPORT", pageWidth / 2f, currentY, titlePaint)
            currentY += 16f

            // Subtitle / Period
            canvas.drawText("Period: $monthRange", pageWidth / 2f, currentY, subTitlePaint)
            currentY += 14f

            // Meta Info
            val metaBuilder = StringBuilder()
            if (managerName.isNotBlank()) metaBuilder.append("Manager / Team: $managerName  |  ")
            metaBuilder.append("Generated On: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}")
            canvas.drawText(metaBuilder.toString(), pageWidth / 2f, currentY, metaPaint)
            currentY += 16f

            // Grouped Data for Stats
            val groupedData = data.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
                val first = performances.first()
                val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
                ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
            }
            val totalOfficers = groupedData.map { it.employeeId }.distinct().size
            val totalCards = groupedData.sumOf { it.totalRecords }

            // Summary Stats Box
            val statsBoxRect = RectF(marginLeft, currentY, marginRight, currentY + 28f)
            canvas.drawRoundRect(statsBoxRect, 4f, 4f, statsCardBgPaint)
            canvas.drawRoundRect(statsBoxRect, 4f, 4f, statsCardBorderPaint)

            val statsBaseline = currentY + 18f
            canvas.drawText("Total Active Officers: $totalOfficers", marginLeft + 12f, statsBaseline, statsTextPaint)
            val cardsText = "Total Cards Issued: $totalCards"
            canvas.drawText(cardsText, marginRight - 12f - statsTextPaint.measureText(cardsText), statsBaseline, statsTextPaint)
            currentY += 38f

            // Table Header on Page 1
            drawHeaderRow(canvas, currentY)
            currentY += headerRowHeight

            // Prepare Row Data
            val rows: List<List<String>> = if (isSummary) {
                groupedData.mapIndexed { idx, item ->
                    listOf(
                        (idx + 1).toString(),
                        item.month,
                        item.employeeName,
                        item.branch,
                        item.totalRecords.toString()
                    )
                }
            } else {
                val details = data.filter { !it.limit.equals("NIL", ignoreCase = true) }
                if (details.isEmpty()) {
                    emptyList()
                } else {
                    details.mapIndexed { idx, item ->
                        listOf(
                            (idx + 1).toString(),
                            item.month,
                            item.employeeName,
                            item.branch,
                            item.applicantName,
                            item.accountNo,
                            item.limit.ifBlank { "-" }
                        )
                    }
                }
            }

            if (rows.isEmpty() && !isSummary) {
                // Empty Details Row
                rowBgPaint.color = Color.WHITE
                canvas.drawRect(marginLeft, currentY, marginRight, currentY + 28f, rowBgPaint)
                val emptyNotice = "No individual card records submitted (All NIL or no details)"
                val noticeX = (pageWidth - dataTextPaint.measureText(emptyNotice)) / 2f
                val noticeY = currentY + 18f
                canvas.drawText(emptyNotice, noticeX, noticeY, dataTextPaint)
                canvas.drawLine(marginLeft, currentY + 28f, marginRight, currentY + 28f, dividerPaint)
                currentY += 28f
            } else {
                rows.forEachIndexed { rowIdx, rowValues ->
                    // Check if new page is needed
                    if (currentY + dataRowHeight > pageHeight - 45f) {
                        drawFooter(canvas, pageNum)
                        pdfDocument.finishPage(currentPage)
                        pageNum++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                        currentPage = pdfDocument.startPage(pageInfo)
                        canvas = currentPage.canvas

                        // Mini running header
                        metaPaint.textAlign = Paint.Align.LEFT
                        canvas.drawText("MONTHLY PERFORMANCE REPORT - $monthRange", marginLeft, 26f, metaPaint)
                        metaPaint.textAlign = Paint.Align.CENTER
                        dividerPaint.color = Color.parseColor("#CBD5E1")
                        canvas.drawLine(marginLeft, 32f, marginRight, 32f, dividerPaint)

                        currentY = 40f
                        drawHeaderRow(canvas, currentY)
                        currentY += headerRowHeight
                    }

                    // Draw Data Row
                    val isEven = (rowIdx % 2 == 0)
                    rowBgPaint.color = if (isEven) Color.parseColor("#F8FAFC") else Color.WHITE
                    canvas.drawRect(marginLeft, currentY, marginRight, currentY + dataRowHeight, rowBgPaint)

                    var curX = marginLeft
                    for (cIdx in activeCols.indices) {
                        val col = activeCols[cIdx]
                        val rawVal = rowValues.getOrElse(cIdx) { "" }
                        val textToDraw = fitText(rawVal, col.width - 6f, dataTextPaint)
                        val textY = currentY + (dataRowHeight / 2f) - ((dataTextPaint.descent() + dataTextPaint.ascent()) / 2f)
                        val textX = if (col.isCenter) {
                            curX + (col.width - dataTextPaint.measureText(textToDraw)) / 2f
                        } else {
                            curX + 5f
                        }
                        canvas.drawText(textToDraw, textX, textY, dataTextPaint)

                        // Vertical divider
                        dividerPaint.color = Color.parseColor("#E2E8F0")
                        dividerPaint.strokeWidth = 0.5f
                        canvas.drawLine(curX + col.width, currentY, curX + col.width, currentY + dataRowHeight, dividerPaint)

                        curX += col.width
                    }

                    // Bottom horizontal border
                    dividerPaint.color = Color.parseColor("#E2E8F0")
                    canvas.drawLine(marginLeft, currentY + dataRowHeight, marginRight, currentY + dataRowHeight, dividerPaint)
                    // Outer border
                    canvas.drawLine(marginLeft, currentY, marginLeft, currentY + dataRowHeight, dividerPaint)
                    canvas.drawLine(marginRight, currentY, marginRight, currentY + dataRowHeight, dividerPaint)

                    currentY += dataRowHeight
                }

                // Total row for summary
                if (isSummary) {
                    if (currentY + 22f > pageHeight - 45f) {
                        drawFooter(canvas, pageNum)
                        pdfDocument.finishPage(currentPage)
                        pageNum++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                        currentPage = pdfDocument.startPage(pageInfo)
                        canvas = currentPage.canvas
                        currentY = 40f
                    }
                    val totalRowHeight = 22f
                    rowBgPaint.color = Color.parseColor("#F1F5F9")
                    canvas.drawRect(marginLeft, currentY, marginRight, currentY + totalRowHeight, rowBgPaint)

                    val totalLabel = "TOTAL"
                    val labelY = currentY + (totalRowHeight / 2f) - ((dataBoldPaint.descent() + dataBoldPaint.ascent()) / 2f)
                    canvas.drawText(totalLabel, marginLeft + 35f + 75f + 5f, labelY, dataBoldPaint)

                    val cardsSumText = totalCards.toString()
                    val totalCardsX = marginLeft + 35f + 75f + 175f + 148f + (90f - dataBoldPaint.measureText(cardsSumText)) / 2f
                    canvas.drawText(cardsSumText, totalCardsX, labelY, dataBoldPaint)

                    dividerPaint.color = Color.parseColor("#94A3B8")
                    dividerPaint.strokeWidth = 1f
                    canvas.drawLine(marginLeft, currentY + totalRowHeight, marginRight, currentY + totalRowHeight, dividerPaint)
                    canvas.drawLine(marginLeft, currentY, marginLeft, currentY + totalRowHeight, dividerPaint)
                    canvas.drawLine(marginRight, currentY, marginRight, currentY + totalRowHeight, dividerPaint)
                    currentY += totalRowHeight
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

    private data class OfficerSummary(
        val employeeId: String,
        val employeeName: String,
        val branch: String,
        val zone: String,
        val salesManager: String,
        val total: Int,
        val target: Int,
        val short: Int,
        val achievementRate: Double,
        val achievementStr: String
    )

    fun exportPerformanceXlsx(
        context: Context,
        data: List<Performance>,
        monthRange: String,
        officerTargets: Map<String, Int> = emptyMap(),
        includeZone: Boolean = true,
        includeSalesManager: Boolean = false,
        includeApplicantDetails: Boolean = true,
        isShare: Boolean = false
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

            // 1. Grouped Performance Data per Officer
            val groupedByOfficer = data.groupBy { it.employeeId }
            val officerSummaries = groupedByOfficer.map { (empId, list) ->
                val first = list.first()
                val cardCount = list.count { !it.limit.equals("NIL", ignoreCase = true) }
                // Yearly target: priority to employee's yearlyTarget, else monthlyTarget * 12, default 25 * 12 = 300
                val yearlyTarget = officerTargets[empId]?.takeIf { it > 0 } ?: (25 * 12)
                val short = maxOf(0, yearlyTarget - cardCount)
                val rate = if (yearlyTarget > 0) (cardCount.toDouble() / yearlyTarget.toDouble()) * 100.0 else 0.0
                val rateStr = String.format(Locale.US, "%.1f%%", rate)

                OfficerSummary(
                    employeeId = empId,
                    employeeName = first.employeeName.uppercase(Locale.getDefault()),
                    branch = first.branch,
                    zone = first.zone,
                    salesManager = first.salesManager,
                    total = cardCount,
                    target = yearlyTarget,
                    short = short,
                    achievementRate = rate,
                    achievementStr = rateStr
                )
            }.sortedByDescending { it.achievementRate }

            val totalAchievedSum = officerSummaries.sumOf { it.total }
            val totalTargetSum = officerSummaries.sumOf { it.target }
            val totalShortSum = officerSummaries.sumOf { it.short }
            val overallRate = if (totalTargetSum > 0) (totalAchievedSum.toDouble() / totalTargetSum.toDouble()) * 100.0 else 0.0
            val overallRateStr = String.format(Locale.US, "%.1f%%", overallRate)

            // Dynamic columns matching the uploaded Excel image layout:
            // Branch | Branch Official | (Sales Manager) | (Zone) | Total | Target | SHORT | Achievemnet
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
            summaryCols.add(XlsxGenerator.ColumnDef("Achievemnet", width = 16.0, isCenter = true, isBold = true, isAchievement = true))

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

            sheets.add(
                XlsxGenerator.SheetDef(
                    name = "Performance Summary",
                    reportTitle = "EMPLOYEE PERFORMANCE REPORT",
                    period = monthRange,
                    metadata = summaryMeta,
                    columns = summaryCols,
                    rows = summaryRows,
                    totalRow = summaryTotalRow,
                    enableAutoFilter = true
                )
            )

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
