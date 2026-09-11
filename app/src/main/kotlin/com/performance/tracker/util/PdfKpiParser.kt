package com.performance.tracker.util

import android.content.Context
import android.net.Uri
import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import java.io.InputStream
import java.util.regex.Pattern

data class ParsedKpiAccount(
    val slNo: Int,
    val accountNo: String,
    val accountTitle: String,
    val achievementAmount: String,
    val month: String,
    val period: String = "",
    val originatingBranch: String = ""
)

object PdfKpiParser {

    val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    private val MONTH_MAP = mapOf(
        "01" to "January",
        "02" to "February",
        "03" to "March",
        "04" to "April",
        "05" to "May",
        "06" to "June",
        "07" to "July",
        "08" to "August",
        "09" to "September",
        "10" to "October",
        "11" to "November",
        "12" to "December"
    )

    fun extractTextFromPdf(inputStream: InputStream): String {
        val reader = PdfReader(inputStream)
        val extractor = PdfTextExtractor(reader)
        val sb = StringBuilder()
        val numPages = reader.numberOfPages
        for (i in 1..numPages) {
            try {
                val pageText = extractor.getTextFromPage(i)
                sb.append(pageText).append("\n")
            } catch (e: Exception) {
                // Fallback or ignore empty page
            }
        }
        reader.close()
        return sb.toString()
    }

    fun parseKpiText(fullText: String): List<ParsedKpiAccount> {
        val result = mutableListOf<ParsedKpiAccount>()
        
        // Account pattern: e.g. 2706-906-000746 or 2369-906-002021 or \d{4}-\d{3}-\d{6}
        val acPattern = Pattern.compile("\\b(\\d{4}-\\d{3}-\\d{6})\\b")
        val matcher = acPattern.matcher(fullText)

        val accountMatches = mutableListOf<Pair<String, Int>>() // Pair(accountNo, startIndex)
        while (matcher.find()) {
            val ac = matcher.group(1)
            if (ac != null) {
                accountMatches.add(Pair(ac, matcher.start()))
            }
        }

        if (accountMatches.isEmpty()) {
            return emptyList()
        }

        val datePattern = Pattern.compile("(\\d{2})[-/](\\d{2})[-/](\\d{4})")
        val amountPattern = Pattern.compile("\\b([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{2})?|[0-9]+(?:\\.[0-9]{2})?)\\b")

        for (i in accountMatches.indices) {
            val (accountNo, startIndex) = accountMatches[i]
            val endIndex = if (i + 1 < accountMatches.size) {
                accountMatches[i + 1].second
            } else {
                // Up to "TOTAL NUMBER OF" or text end
                val totalIdx = fullText.indexOf("TOTAL NUMBER", startIndex)
                if (totalIdx != -1) totalIdx else fullText.length
            }

            val rawBlock = fullText.substring(startIndex, endIndex)
            
            // Text preceding the account number in this record or previous boundary
            val preBlockStart = if (i > 0) accountMatches[i - 1].second else 0
            val precedingText = fullText.substring(preBlockStart, startIndex)

            // 1. Serial Number: look for number before NEW LOAN ACCOUNT or before accountNo
            var slNo = i + 1
            val slMatcher = Pattern.compile("(\\d{1,3})\\s+(?:NEW LOAN ACCOUNT|LOAN)", Pattern.CASE_INSENSITIVE).matcher(precedingText)
            var lastSl: Int? = null
            while (slMatcher.find()) {
                lastSl = slMatcher.group(1)?.toIntOrNull()
            }
            if (lastSl != null) {
                slNo = lastSl
            }

            // 2. Dates in block:
            // Pubali Bank format has Maker Date, Checker Date, Year, Verify, then Period (Period Start, Period End)
            val dateMatcher = datePattern.matcher(rawBlock)
            val datesInBlock = mutableListOf<String>()
            while (dateMatcher.find()) {
                datesInBlock.add(dateMatcher.group())
            }

            // Month determination:
            // Period is typically the last two dates in the block: e.g. 01-01-2026, 31-01-2026
            var determinedMonth = ""
            var periodString = ""
            if (datesInBlock.isNotEmpty()) {
                // Look from the end backwards for period dates (e.g. 01-MM-yyyy to 31-MM-yyyy)
                val periodDates = datesInBlock.takeLast(2)
                periodString = periodDates.joinToString(" to ")

                for (dt in periodDates.reversed()) {
                    val parts = dt.split("-", "/")
                    if (parts.size == 3) {
                        val mm = parts[1]
                        if (MONTH_MAP.containsKey(mm)) {
                            determinedMonth = MONTH_MAP[mm] ?: ""
                            break
                        }
                    }
                }
            }

            if (determinedMonth.isEmpty()) {
                // Default fallback to first valid month found in any date in block
                for (dt in datesInBlock) {
                    val parts = dt.split("-", "/")
                    if (parts.size == 3) {
                        val mm = parts[1]
                        if (MONTH_MAP.containsKey(mm)) {
                            determinedMonth = MONTH_MAP[mm] ?: ""
                            break
                        }
                    }
                }
            }

            // 3. Name (Account Title) and Amount:
            // In rawBlock: starts with accountNo, followed by Account Title, then Amount, then Branch, then Dates
            val afterAc = rawBlock.substring(accountNo.length).trim()
            
            // Find where amount or dates start
            val firstDateIdx = datesInBlock.firstOrNull()?.let { afterAc.indexOf(it) } ?: -1
            val contentBeforeDates = if (firstDateIdx != -1) afterAc.substring(0, firstDateIdx).trim() else afterAc

            // Find amount in contentBeforeDates
            val amtMatcher = amountPattern.matcher(contentBeforeDates)
            var amountStr = ""
            var nameStr = ""
            var branchStr = ""

            if (amtMatcher.find()) {
                amountStr = amtMatcher.group(1) ?: ""
                val amtStart = amtMatcher.start()
                val amtEnd = amtMatcher.end()

                nameStr = contentBeforeDates.substring(0, amtStart).trim()
                branchStr = contentBeforeDates.substring(amtEnd).trim()
            } else {
                // If no amount is found (e.g. NIL or blank like SL 12, 25)
                // Name is until known branch or end of contentBeforeDates
                val knownBranches = listOf("ASAD AVENUE", "TEJGAON", "SHISHU HOSPITAL", "SHISHU HOSPITAL SUB", "BRANCH", "SUB-BRANCH")
                var foundBranchIdx = -1
                for (kb in knownBranches) {
                    val idx = contentBeforeDates.indexOf(kb, ignoreCase = true)
                    if (idx != -1 && (foundBranchIdx == -1 || idx < foundBranchIdx)) {
                        foundBranchIdx = idx
                    }
                }

                if (foundBranchIdx != -1) {
                    nameStr = contentBeforeDates.substring(0, foundBranchIdx).trim()
                    branchStr = contentBeforeDates.substring(foundBranchIdx).trim()
                } else {
                    nameStr = contentBeforeDates.trim()
                }
                amountStr = "0"
            }

            // Clean up nameStr: remove line breaks, multiple spaces, and any leftover header text
            nameStr = cleanTitleName(nameStr)
            
            // Clean up amountStr: remove commas or keep readable integer
            val cleanAmount = formatAmount(amountStr)

            result.add(
                ParsedKpiAccount(
                    slNo = slNo,
                    accountNo = accountNo,
                    accountTitle = nameStr.ifBlank { "Account $accountNo" },
                    achievementAmount = cleanAmount,
                    month = determinedMonth.ifBlank { "January" },
                    period = periodString,
                    originatingBranch = branchStr
                )
            )
        }

        return result
    }

    private fun cleanTitleName(raw: String): String {
        var s = raw.replace("\n", " ").replace("\r", " ")
        // Remove known noise or header words if caught
        s = s.replace("Account Title", "", ignoreCase = true)
        s = s.replace("Achievement", "", ignoreCase = true)
        s = s.replace("Amount", "", ignoreCase = true)
        s = s.replace("NEW LOAN ACCOUNT", "", ignoreCase = true)
        s = s.replace("Originating Branch", "", ignoreCase = true)
        s = s.replace("Sub-Branch", "", ignoreCase = true)
        s = s.replace("\\s+".toRegex(), " ").trim()
        return s
    }

    private fun formatAmount(raw: String): String {
        if (raw.isBlank() || raw == "0") return "0"
        // E.g. "200,000.00" -> "200,000" or "200000"
        var clean = raw.trim()
        if (clean.endsWith(".00")) {
            clean = clean.substring(0, clean.length - 3)
        }
        return clean
    }
}
