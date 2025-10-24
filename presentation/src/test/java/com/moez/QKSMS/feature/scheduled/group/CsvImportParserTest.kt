package dev.octoshrimpy.quik.feature.scheduled.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class CsvImportParserTest {

    private val parser = CsvImportParser()

    @Test
    fun parse_validRows_returnsAllRows() {
        val csv = """
            姓名,手机号,时间,短信内容
            甲,111111,2024-04-18 09:30,Hello World
            乙,222222,2024/04/19 09:30,Hi There
            丙,333333,2026/10/24/09:00,Howdy
        """.trimIndent()

        val result = parser.parse(csv.byteInputStream(StandardCharsets.UTF_8))

        assertTrue(result.errors.isEmpty())
        assertEquals(3, result.rows.size)
        assertEquals("甲", result.rows[0].name)
        assertEquals("111111", result.rows[0].phoneNumber)
        assertEquals("Hello World", result.rows[0].body)
    }

    @Test
    fun parse_invalidTime_reportsError() {
        val csv = """
            姓名,手机号,时间,短信内容
            甲,111111,时间,Hello World
        """.trimIndent()

        val result = parser.parse(csv.byteInputStream(StandardCharsets.UTF_8))

        assertTrue(result.rows.isEmpty())
        assertEquals(1, result.errors.size)
        val error = result.errors.first()
        assertEquals(2, error.lineNumber)
        assertTrue(error.kind is CsvImportParser.RowError.Kind.InvalidTime)
    }

    @Test
    fun parse_missingPhone_reportsError() {
        val csv = """
            姓名,手机号,时间,短信内容
            甲,,2024-04-18 09:30,Hello World
        """.trimIndent()

        val result = parser.parse(csv.byteInputStream(StandardCharsets.UTF_8))

        assertTrue(result.rows.isEmpty())
        assertTrue(result.errors.any { it.kind is CsvImportParser.RowError.Kind.MissingPhoneNumber })
        assertEquals(2, result.errors.first().lineNumber)
    }
}
