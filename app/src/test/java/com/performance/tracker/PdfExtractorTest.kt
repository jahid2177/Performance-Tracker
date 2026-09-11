package com.performance.tracker

import com.lowagie.text.pdf.parser.PdfTextExtractor
import com.performance.tracker.util.PdfKpiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExtractorTest {
    @Test
    fun testPdfExtractorClassAvailable() {
        assertNotNull(PdfTextExtractor::class.java)
    }

    @Test
    fun testParseKpiTextSample() {
        val sampleText = """
            PUBALI BANK PLC.
            ASAD AVENUE BRANCH
            Employee Wise KPI Register Details
            Printed On: 10/09/2026
            Employee ID : 202429021705
            Employee Name : MD. HABIBUR RAHAMAN
            Designation : Deputy Junior Officer
            SL No. KPI Category
            Account No.
            Account Title
            Achievement
            Amount
            Originating Branch/
            Sub-Branch
            Maker Date
            Checker Date
            Year
            Verify Period Remarks
            1 NEW LOAN ACCOUNT
            2706-906-000746
            MD. SIDDIQUR
            RAHMAN
            200,000.00 ASAD AVENUE
            05-02-2026
            15-02-2026
            2026
            Y
            01-01-2026
            31-01-2026
            2 NEW LOAN ACCOUNT
            2369-906-002021
            IMANA SHAHRIN
            TANIA
            700,000.00 TEJGAON
            16-02-2026
            07-04-2026
            2026
            Y
            01-01-2026
            31-01-2026
            4 NEW LOAN ACCOUNT
            2706-379-002732
            MD. RASHEDUL
            HASSAN
            4,000,000.00 ASAD AVENUE
            03-03-2026
            05-03-2026
            2026
            Y
            01-02-2026
            28-02-2026
            6 NEW LOAN ACCOUNT
            2706-379-002844
            MD. NAWAS SHARIF
            950,000.00 ASAD AVENUE
            06-04-2026
            07-04-2026
            2026
            Y
            01-03-2026
            31-03-2026
            51 NEW LOAN ACCOUNT
            6561-906-000277
            MD. SHARIAR
            HOSSAIN
            300,000.00 SHISHU HOSPITAL SUB
            10-08-2026
            10-08-2026
            2026
            Y
            01-08-2026
            31-08-2026
            TOTAL NUMBER OF A/C(s) : 68
        """.trimIndent()

        val parsed = PdfKpiParser.parseKpiText(sampleText)
        assertEquals(5, parsed.size)

        // Item 1
        assertEquals("2706-906-000746", parsed[0].accountNo)
        assertEquals("MD. SIDDIQUR RAHMAN", parsed[0].accountTitle)
        assertEquals("200,000", parsed[0].achievementAmount)
        assertEquals("January", parsed[0].month)

        // Item 2
        assertEquals("2369-906-002021", parsed[1].accountNo)
        assertEquals("IMANA SHAHRIN TANIA", parsed[1].accountTitle)
        assertEquals("700,000", parsed[1].achievementAmount)
        assertEquals("January", parsed[1].month)

        // Item 3 (Period 01-02-2026 -> February)
        assertEquals("2706-379-002732", parsed[2].accountNo)
        assertEquals("MD. RASHEDUL HASSAN", parsed[2].accountTitle)
        assertEquals("4,000,000", parsed[2].achievementAmount)
        assertEquals("February", parsed[2].month)

        // Item 4 (Period 01-03-2026 -> March)
        assertEquals("2706-379-002844", parsed[3].accountNo)
        assertEquals("MD. NAWAS SHARIF", parsed[3].accountTitle)
        assertEquals("950,000", parsed[3].achievementAmount)
        assertEquals("March", parsed[3].month)

        // Item 5 (Period 01-08-2026 -> August)
        assertEquals("6561-906-000277", parsed[4].accountNo)
        assertEquals("MD. SHARIAR HOSSAIN", parsed[4].accountTitle)
        assertEquals("300,000", parsed[4].achievementAmount)
        assertEquals("August", parsed[4].month)
    }
}
