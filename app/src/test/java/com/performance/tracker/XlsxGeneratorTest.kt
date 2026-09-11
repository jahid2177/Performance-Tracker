package com.performance.tracker

import com.performance.tracker.util.XlsxGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxGeneratorTest {

    @Test
    fun testXlsxGeneration_createsValidZipStructure() {
        val sheet1 = XlsxGenerator.SheetDef(
            name = "Summary",
            reportTitle = "PERFORMANCE SUMMARY",
            period = "January 2026",
            metadata = listOf("Generated On:" to "11 Sep 2026", "Total Cards:" to "50"),
            columns = listOf(
                XlsxGenerator.ColumnDef("SL", width = 8.0, isNumeric = true, isCenter = true),
                XlsxGenerator.ColumnDef("Officer Name", width = 26.0),
                XlsxGenerator.ColumnDef("Total Cards", width = 16.0, isNumeric = true, isCenter = true)
            ),
            rows = listOf(
                listOf(1L, "Alice Johnson", 25L),
                listOf(2L, "Bob Smith", 25L)
            ),
            totalRow = listOf("", "TOTAL", 50L)
        )

        val sheet2 = XlsxGenerator.SheetDef(
            name = "Details",
            reportTitle = "DETAILED RECORDS",
            period = "January 2026",
            metadata = listOf("Total Records:" to "50"),
            columns = listOf(
                XlsxGenerator.ColumnDef("SL", width = 8.0, isNumeric = true, isCenter = true),
                XlsxGenerator.ColumnDef("Applicant", width = 26.0),
                XlsxGenerator.ColumnDef("Account No", width = 20.0, isCenter = true)
            ),
            rows = listOf(
                listOf(1L, "John Doe", "1234567890"),
                listOf(2L, "Jane Doe", "9876543210")
            )
        )

        val outputStream = ByteArrayOutputStream()
        XlsxGenerator.writeWorkbook(outputStream, listOf(sheet1, sheet2))

        val bytes = outputStream.toByteArray()
        assertTrue("Output bytes should not be empty", bytes.isNotEmpty())

        val entryNames = mutableListOf<String>()
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zis.nextEntry
        val fileContents = mutableMapOf<String, String>()

        while (entry != null) {
            entryNames.add(entry.name)
            fileContents[entry.name] = zis.bufferedReader(Charsets.UTF_8).readText()
            zis.closeEntry()
            entry = zis.nextEntry
        }

        // Verify required OpenXML structure
        assertTrue("Contains [Content_Types].xml", entryNames.contains("[Content_Types].xml"))
        assertTrue("Contains _rels/.rels", entryNames.contains("_rels/.rels"))
        assertTrue("Contains xl/_rels/workbook.xml.rels", entryNames.contains("xl/_rels/workbook.xml.rels"))
        assertTrue("Contains xl/workbook.xml", entryNames.contains("xl/workbook.xml"))
        assertTrue("Contains xl/styles.xml", entryNames.contains("xl/styles.xml"))
        assertTrue("Contains xl/worksheets/sheet1.xml", entryNames.contains("xl/worksheets/sheet1.xml"))
        assertTrue("Contains xl/worksheets/sheet2.xml", entryNames.contains("xl/worksheets/sheet2.xml"))

        // Verify workbook contains both sheet names
        val workbookXml = fileContents["xl/workbook.xml"]
        assertNotNull(workbookXml)
        assertTrue(workbookXml!!.contains("""sheet name="Summary""""))
        assertTrue(workbookXml.contains("""sheet name="Details""""))

        // Verify sheet 1 data contents
        val sheet1Xml = fileContents["xl/worksheets/sheet1.xml"]
        assertNotNull(sheet1Xml)
        assertTrue(sheet1Xml!!.contains("PERFORMANCE SUMMARY"))
        assertTrue(sheet1Xml.contains("Alice Johnson"))
        assertTrue(sheet1Xml.contains("Bob Smith"))
        assertTrue(sheet1Xml.contains("<v>25</v>"))
        assertTrue(sheet1Xml.contains("<v>50</v>"))

        // Verify sheet 2 data contents
        val sheet2Xml = fileContents["xl/worksheets/sheet2.xml"]
        assertNotNull(sheet2Xml)
        assertTrue(sheet2Xml!!.contains("DETAILED RECORDS"))
        assertTrue(sheet2Xml.contains("John Doe"))
        assertTrue(sheet2Xml.contains("1234567890"))
    }

    @Test
    fun testCellRef() {
        assertEquals("A1", XlsxGenerator.getCellRef(1, 1))
        assertEquals("B5", XlsxGenerator.getCellRef(2, 5))
        assertEquals("H10", XlsxGenerator.getCellRef(8, 10))
        assertEquals("Z1", XlsxGenerator.getCellRef(26, 1))
        assertEquals("AA1", XlsxGenerator.getCellRef(27, 1))
    }
}
