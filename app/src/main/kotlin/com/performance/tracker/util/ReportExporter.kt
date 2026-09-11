package com.performance.tracker.util

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
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
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

    // ========================================================
    // 1. OPENPDF: Monthly Performance Report (Download / Share)
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
                Toast.makeText(context, "Unable to create output file", Toast.LENGTH_SHORT).show()
                return
            }

            // Generate PDF with OpenPDF
            val document = Document()
            PdfWriter.getInstance(document, outputStream)
            document.open()

            // Document Header
            val titleFont = Font(Font.HELVETICA, 18f, Font.BOLD)
            val subFont = Font(Font.HELVETICA, 12f, Font.BOLD)
            val normalFont = Font(Font.HELVETICA, 10f, Font.NORMAL)
            val tableHeaderFont = Font(Font.HELVETICA, 10f, Font.BOLD)
            val tableDataFont = Font(Font.HELVETICA, 9f, Font.NORMAL)

            val titlePara = Paragraph("MONTHLY PERFORMANCE REPORT", titleFont).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 4f
            }
            document.add(titlePara)

            val subTitlePara = Paragraph("Period: $monthRange", subFont).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 4f
            }
            document.add(subTitlePara)

            val metaText = StringBuilder()
            if (managerName.isNotEmpty()) {
                metaText.append("Manager / Team: $managerName | ")
            }
            metaText.append("Generated On: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}")

            val metaPara = Paragraph(metaText.toString(), normalFont).apply {
                alignment = Element.ALIGN_CENTER
                spacingAfter = 14f
            }
            document.add(metaPara)

            // Statistics Summary
            val groupedData = data.groupBy { it.employeeId + "_" + it.month }.map { (_, performances) ->
                val first = performances.first()
                val actualCount = if (performances.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else performances.size
                ReportSummary(first.employeeId, first.employeeName, first.branch, first.month, first.timestamp, actualCount)
            }
            val totalOfficers = groupedData.map { it.employeeId }.distinct().size
            val totalCards = groupedData.sumOf { it.totalRecords }

            val statsTable = PdfPTable(2).apply {
                widthPercentage = 100f
                setSpacingAfter(12f)
                setWidths(floatArrayOf(1f, 1f))
            }
            val statsCell1 = PdfPCell(Phrase("Total Active Officers: $totalOfficers", subFont)).apply {
                setPadding(6f)
                horizontalAlignment = Element.ALIGN_LEFT
            }
            val statsCell2 = PdfPCell(Phrase("Total Cards Issued: $totalCards", subFont)).apply {
                setPadding(6f)
                horizontalAlignment = Element.ALIGN_RIGHT
            }
            statsTable.addCell(statsCell1)
            statsTable.addCell(statsCell2)
            document.add(statsTable)

            // Main Table
            if (isSummary) {
                val table = PdfPTable(5).apply {
                    widthPercentage = 100f
                    setWidths(floatArrayOf(0.8f, 1.8f, 3.2f, 2.7f, 1.5f))
                }

                val headers = listOf("SL", "Month", "Officer Name", "Branch", "Total Cards")
                headers.forEach { h ->
                    val cell = PdfPCell(Phrase(h, tableHeaderFont)).apply {
                        setPadding(6f)
                        horizontalAlignment = if (h == "Total Cards" || h == "SL") Element.ALIGN_CENTER else Element.ALIGN_LEFT
                    }
                    table.addCell(cell)
                }

                groupedData.forEachIndexed { idx, item ->
                    table.addCell(PdfPCell(Phrase((idx + 1).toString(), tableDataFont)).apply {
                        setPadding(5f)
                        horizontalAlignment = Element.ALIGN_CENTER
                    })
                    table.addCell(PdfPCell(Phrase(item.month, tableDataFont)).apply { setPadding(5f) })
                    table.addCell(PdfPCell(Phrase(item.employeeName, tableDataFont)).apply { setPadding(5f) })
                    table.addCell(PdfPCell(Phrase(item.branch, tableDataFont)).apply { setPadding(5f) })
                    table.addCell(PdfPCell(Phrase(item.totalRecords.toString(), tableDataFont)).apply {
                        setPadding(5f)
                        horizontalAlignment = Element.ALIGN_CENTER
                    })
                }
                document.add(table)
            } else {
                val table = PdfPTable(6).apply {
                    widthPercentage = 100f
                    setWidths(floatArrayOf(0.7f, 1.6f, 2.5f, 2f, 2.7f, 2f))
                }

                val headers = listOf("SL", "Month", "Officer", "Branch", "Applicant Name", "A/C No")
                headers.forEach { h ->
                    val cell = PdfPCell(Phrase(h, tableHeaderFont)).apply {
                        setPadding(6f)
                        horizontalAlignment = if (h == "SL") Element.ALIGN_CENTER else Element.ALIGN_LEFT
                    }
                    table.addCell(cell)
                }

                val details = data.filter { !it.limit.equals("NIL", ignoreCase = true) }
                details.forEachIndexed { idx, item ->
                    table.addCell(PdfPCell(Phrase((idx + 1).toString(), tableDataFont)).apply {
                        setPadding(5f)
                        horizontalAlignment = Element.ALIGN_CENTER
                    })
                    table.addCell(PdfPCell(Phrase(item.month, tableDataFont)).apply { setPadding(5f) })
                    table.addCell(PdfPCell(Phrase(item.employeeName, tableDataFont)).apply { setPadding(5f) })
                    table.addCell(PdfPCell(Phrase(item.branch, tableDataFont)).apply { setPadding(5f) })
                    table.addCell(PdfPCell(Phrase(item.applicantName, tableDataFont)).apply { setPadding(5f) })
                    table.addCell(PdfPCell(Phrase(item.accountNo, tableDataFont)).apply { setPadding(5f) })
                }
                document.add(table)
            }

            document.close()
            outputStream.close()

            if (isShare && shareUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Monthly Performance Report - $monthRange")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Monthly PDF Report"))
            } else {
                Toast.makeText(context, "Monthly PDF report saved to Downloads", Toast.LENGTH_SHORT).show()
                showNotification(context, fileName, shareUri, mimeType)
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
        mode: XlsxReportMode = XlsxReportMode.FULL,
        isShare: Boolean = false
    ) {
        if (data.isEmpty()) {
            Toast.makeText(context, "No performance records found to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Employee_Performance_$timestamp.xlsx"
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

            // 1. Grouped Summary Data
            val grouped = data.groupBy { it.employeeId + "_" + it.month }.map { (_, list) ->
                val first = list.first()
                val count = if (list.size == 1 && first.limit.equals("NIL", ignoreCase = true)) 0 else list.size
                first to count
            }
            val totalOfficers = grouped.map { it.first.employeeId }.distinct().size
            val totalCards = grouped.sumOf { it.second }

            // Add Summary Sheet if FULL or SUMMARY
            if (mode == XlsxReportMode.FULL || mode == XlsxReportMode.SUMMARY) {
                val summaryCols = listOf(
                    XlsxGenerator.ColumnDef("SL", width = 8.0, isNumeric = true, isCenter = true),
                    XlsxGenerator.ColumnDef("Month", width = 16.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Employee ID", width = 16.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Officer Name", width = 26.0),
                    XlsxGenerator.ColumnDef("Branch", width = 22.0),
                    XlsxGenerator.ColumnDef("Zone", width = 18.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Sales Manager", width = 22.0),
                    XlsxGenerator.ColumnDef("Total Cards", width = 16.0, isNumeric = true, isCenter = true)
                )

                val summaryRows = grouped.mapIndexed { idx, (first, count) ->
                    listOf<Any?>(
                        (idx + 1).toLong(),
                        first.month,
                        first.employeeId,
                        first.employeeName,
                        first.branch,
                        first.zone,
                        first.salesManager,
                        count.toLong()
                    )
                }

                val summaryTotalRow = listOf<Any?>(
                    "",
                    "",
                    "",
                    "TOTAL",
                    "",
                    "",
                    "",
                    totalCards.toLong()
                )

                val summaryMeta = listOf(
                    "Exported On:" to nowFormatted,
                    "Total Active Officers:" to totalOfficers.toString(),
                    "Total Cards Issued:" to totalCards.toString()
                )

                sheets.add(
                    XlsxGenerator.SheetDef(
                        name = "Performance Summary",
                        reportTitle = "EMPLOYEE PERFORMANCE SUMMARY",
                        period = monthRange,
                        metadata = summaryMeta,
                        columns = summaryCols,
                        rows = summaryRows,
                        totalRow = summaryTotalRow
                    )
                )
            }

            // Add Detailed Sheet if FULL or DETAILS
            if (mode == XlsxReportMode.FULL || mode == XlsxReportMode.DETAILS) {
                val details = data.filter { !it.limit.equals("NIL", ignoreCase = true) }
                val detailCols = listOf(
                    XlsxGenerator.ColumnDef("SL", width = 8.0, isNumeric = true, isCenter = true),
                    XlsxGenerator.ColumnDef("Month", width = 16.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Employee ID", width = 16.0, isCenter = true),
                    XlsxGenerator.ColumnDef("Officer Name", width = 26.0),
                    XlsxGenerator.ColumnDef("Branch", width = 22.0),
                    XlsxGenerator.ColumnDef("Zone", width = 18.0, isCenter = true),
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
                        name = "Applicant Records",
                        reportTitle = "FULL APPLICANT PERFORMANCE RECORDS",
                        period = monthRange,
                        metadata = detailMeta,
                        columns = detailCols,
                        rows = detailRows,
                        totalRow = detailTotalRow
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
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
        val stream = uri?.let { context.contentResolver.openOutputStream(it) }
        return Pair(uri, stream)
    }

    private fun showNotification(context: Context, fileName: String, fileUri: Uri?, mimeType: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Report Downloads", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Report Ready")
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
