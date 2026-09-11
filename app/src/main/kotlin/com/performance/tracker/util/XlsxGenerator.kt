package com.performance.tracker.util

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Clean, zero-dependency OpenXML SpreadsheetML (.xlsx) generator.
 * Generates 100% compliant standard Office Open XML (.xlsx) workbooks
 * compatible with Microsoft Excel, Google Sheets, LibreOffice, and mobile Office apps.
 */
object XlsxGenerator {

    data class ColumnDef(
        val header: String,
        val width: Double = 18.0,
        val isNumeric: Boolean = false,
        val isCenter: Boolean = false,
        val isBold: Boolean = false,
        val isAchievement: Boolean = false
    )

    data class SheetDef(
        val name: String,
        val reportTitle: String = "",
        val period: String = "",
        val metadata: List<Pair<String, String>> = emptyList(),
        val columns: List<ColumnDef>,
        val rows: List<List<Any?>>,
        val totalRow: List<Any?>? = null,
        val enableAutoFilter: Boolean = true
    )

    fun writeWorkbook(outputStream: OutputStream, sheets: List<SheetDef>) {
        val zip = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write(buildContentTypesXml(sheets.size).toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 2. _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write(buildPackageRelsXml().toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 3. xl/_rels/workbook.xml.rels
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write(buildWorkbookRelsXml(sheets.size).toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 4. xl/workbook.xml
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write(buildWorkbookXml(sheets).toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 5. xl/styles.xml
        zip.putNextEntry(ZipEntry("xl/styles.xml"))
        zip.write(buildStylesXml().toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        // 6. xl/worksheets/sheet{N}.xml
        sheets.forEachIndexed { index, sheet ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet${index + 1}.xml"))
            zip.write(buildWorksheetXml(sheet).toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        zip.finish()
        zip.flush()
    }

    private fun buildContentTypesXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        sb.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        sb.append("""<Default Extension="xml" ContentType="application/xml"/>""")
        sb.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        sb.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            sb.append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        sb.append("""</Types>""")
        return sb.toString()
    }

    private fun buildPackageRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            sb.append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        val stylesRelId = "rId${sheetCount + 1}"
        sb.append("""<Relationship Id="$stylesRelId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        sb.append("""</Relationships>""")
        return sb.toString()
    }

    private fun buildWorkbookXml(sheets: List<SheetDef>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<sheets>""")
        sheets.forEachIndexed { index, sheet ->
            val sheetId = index + 1
            val relId = "rId$sheetId"
            val safeName = escapeXml(sheet.name.take(31))
            sb.append("""<sheet name="$safeName" sheetId="$sheetId" r:id="$relId"/>""")
        }
        sb.append("""</sheets>""")
        sb.append("""</workbook>""")
        return sb.toString()
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="7">
    <font><sz val="11"/><color rgb="FF1E293B"/><name val="Calibri"/></font>
    <font><b/><sz val="15"/><color rgb="FF1E3A8A"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF000000"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF1E293B"/><name val="Calibri"/></font>
    <font><i/><sz val="10"/><color rgb="FF64748B"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF16A34A"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFDC2626"/><name val="Calibri"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF8FAFC"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE2E8F0"/></patternFill></fill>
  </fills>
  <borders count="3">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD4D4D4"/></left>
      <right style="thin"><color rgb="FFD4D4D4"/></right>
      <top style="thin"><color rgb="FFD4D4D4"/></top>
      <bottom style="thin"><color rgb="FFD4D4D4"/></bottom>
      <diagonal/>
    </border>
    <border>
      <left style="thin"><color rgb="FFD4D4D4"/></left>
      <right style="thin"><color rgb="FFD4D4D4"/></right>
      <top style="thin"><color rgb="FF1E293B"/></top>
      <bottom style="double"><color rgb="FF1E293B"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="13">
    <!-- 0: default -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <!-- 1: title -->
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <!-- 2: table header -->
    <xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <!-- 3: normal text (left) -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <!-- 4: normal text (center) -->
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 5: bold text (left, e.g. Branch Official) -->
    <xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <!-- 6: bold numeric (center, e.g. Total, Target, SHORT) -->
    <xf numFmtId="0" fontId="3" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 7: green bold percentage (center, Achievement >= 100%) -->
    <xf numFmtId="0" fontId="5" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 8: red bold percentage (center, Achievement < 100%) -->
    <xf numFmtId="0" fontId="6" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 9: total row label -->
    <xf numFmtId="0" fontId="3" fillId="4" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <!-- 10: total row numeric -->
    <xf numFmtId="0" fontId="3" fillId="4" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 11: total row green percentage -->
    <xf numFmtId="0" fontId="5" fillId="4" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <!-- 12: total row red percentage -->
    <xf numFmtId="0" fontId="6" fillId="4" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
  <cellStyles count="1">
    <cellStyle name="Normal" xfId="0" builtinId="0"/>
  </cellStyles>
</styleSheet>"""
    }

    private fun buildWorksheetXml(sheet: SheetDef): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // Column Widths
        sb.append("""<cols>""")
        sheet.columns.forEachIndexed { idx, col ->
            val colNum = idx + 1
            sb.append("""<col min="$colNum" max="$colNum" width="${col.width}" customWidth="1"/>""")
        }
        sb.append("""</cols>""")

        sb.append("""<sheetData>""")

        var currentRow = 1
        var headerRowIndex = 1

        if (sheet.reportTitle.isNotBlank()) {
            // 1. Title Row (Row 1)
            sb.append("""<row r="$currentRow" ht="30" customHeight="1">""")
            sb.append(createCell(1, currentRow, sheet.reportTitle, styleId = 1))
            sb.append("""</row>""")
            currentRow++

            // 2. Period Row (Row 2)
            if (sheet.period.isNotBlank()) {
                sb.append("""<row r="$currentRow" ht="20" customHeight="1">""")
                sb.append(createCell(1, currentRow, "Performance Report Date:", styleId = 5))
                sb.append(createCell(2, currentRow, sheet.period, styleId = 0))
                sb.append("""</row>""")
                currentRow++
            }

            // 3. Metadata Rows
            sheet.metadata.forEach { (label, value) ->
                sb.append("""<row r="$currentRow" ht="20" customHeight="1">""")
                sb.append(createCell(1, currentRow, label, styleId = 5))
                sb.append(createCell(2, currentRow, value, styleId = 0))
                sb.append("""</row>""")
                currentRow++
            }

            // Empty spacing row
            sb.append("""<row r="$currentRow"/>""")
            currentRow++
        }

        headerRowIndex = currentRow

        // 4. Table Header Row
        sb.append("""<row r="$headerRowIndex" ht="28" customHeight="1">""")
        sheet.columns.forEachIndexed { colIdx, col ->
            sb.append(createCell(colIdx + 1, headerRowIndex, col.header, styleId = 2))
        }
        sb.append("""</row>""")
        currentRow++

        // 5. Data Rows
        sheet.rows.forEachIndexed { _, rowData ->
            sb.append("""<row r="$currentRow" ht="22" customHeight="1">""")
            sheet.columns.forEachIndexed { colIdx, col ->
                val cellValue = if (colIdx < rowData.size) rowData[colIdx] else ""
                val isNum = col.isNumeric && cellValue != null && cellValue.toString().toLongOrNull() != null
                val styleId = when {
                    col.isAchievement -> {
                        val str = cellValue?.toString()?.replace("%", "")?.trim() ?: ""
                        val rate = str.toDoubleOrNull() ?: 0.0
                        if (rate >= 100.0) 7 else 8
                    }
                    col.isBold && col.isCenter -> 6
                    col.isBold && !col.isCenter -> 5
                    col.isCenter || isNum -> 4
                    else -> 3
                }
                sb.append(createCell(colIdx + 1, currentRow, cellValue, styleId, isNumeric = isNum))
            }
            sb.append("""</row>""")
            currentRow++
        }

        val lastDataRowIndex = currentRow - 1

        // 6. Optional Total Summary Row
        if (sheet.totalRow != null) {
            sb.append("""<row r="$currentRow" ht="26" customHeight="1">""")
            sheet.columns.forEachIndexed { colIdx, col ->
                val cellValue = if (colIdx < sheet.totalRow.size) sheet.totalRow[colIdx] else ""
                val isNum = col.isNumeric && cellValue != null && cellValue.toString().toLongOrNull() != null
                val styleId = when {
                    col.isAchievement -> {
                        val str = cellValue?.toString()?.replace("%", "")?.trim() ?: ""
                        val rate = str.toDoubleOrNull() ?: 0.0
                        if (rate >= 100.0) 11 else 12
                    }
                    col.isCenter || isNum -> 10
                    else -> 9
                }
                sb.append(createCell(colIdx + 1, currentRow, cellValue, styleId, isNumeric = isNum))
            }
            sb.append("""</row>""")
            currentRow++
        }

        sb.append("""</sheetData>""")

        // AutoFilter dropdown icon on headers matching standard Excel table
        if (sheet.enableAutoFilter && sheet.rows.isNotEmpty()) {
            val lastColLetter = getColLetter(sheet.columns.size)
            sb.append("""<autoFilter ref="A$headerRowIndex:$lastColLetter$lastDataRowIndex"/>""")
        }

        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun createCell(col1Based: Int, row1Based: Int, value: Any?, styleId: Int, isNumeric: Boolean = false): String {
        val cellRef = getCellRef(col1Based, row1Based)
        if (value == null || value.toString().isEmpty()) {
            return """<c r="$cellRef" s="$styleId"/>"""
        }

        return if (isNumeric) {
            val numStr = value.toString().trim()
            """<c r="$cellRef" s="$styleId"><v>$numStr</v></c>"""
        } else {
            val safeText = escapeXml(value.toString())
            """<c r="$cellRef" s="$styleId" t="inlineStr"><is><t>$safeText</t></is></c>"""
        }
    }

    fun getColLetter(col1Based: Int): String {
        var n = col1Based
        var colName = ""
        while (n > 0) {
            val rem = (n - 1) % 26
            colName = ('A' + rem) + colName
            n = (n - 1) / 26
        }
        return colName
    }

    fun getCellRef(col1Based: Int, row1Based: Int): String {
        return "${getColLetter(col1Based)}$row1Based"
    }

    fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
