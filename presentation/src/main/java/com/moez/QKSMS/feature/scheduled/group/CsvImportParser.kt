/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.feature.scheduled.group

import java.io.InputStream
import java.io.InputStreamReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class CsvImportParser @Inject constructor() {

    data class Row(
        val name: String,
        val phoneNumber: String,
        val scheduledAtMillis: Long,
        val body: String
    )

    data class RowError(
        val lineNumber: Int,
        val kind: Kind
    ) {
        sealed class Kind {
            object MissingPhoneNumber : Kind()
            object MissingTime : Kind()
            data class InvalidTime(val rawValue: String) : Kind()
            object MissingBody : Kind()
        }
    }

    data class Result(
        val rows: List<Row>,
        val errors: List<RowError>
    )

    fun parse(inputStream: InputStream): Result {
        val rows = mutableListOf<Row>()
        val errors = mutableListOf<RowError>()

        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            reader.buffered().useLines { sequence ->
                var lineNumber = 0
                var skippedHeader = false

                sequence.forEach { rawLine ->
                    lineNumber += 1
                    val line = rawLine.trimEnd()
                    if (line.isEmpty()) {
                        return@forEach
                    }

                    val columns = parseCsvLine(line)
                    if (!skippedHeader && looksLikeHeader(columns)) {
                        skippedHeader = true
                        return@forEach
                    }

                    val name = columns.getOrNull(0)?.trim().orEmpty()
                    val phone = columns.getOrNull(1)?.trim().orEmpty()
                    val timeRaw = columns.getOrNull(2)?.trim().orEmpty()
                    val body = columns.getOrNull(3)?.trim().orEmpty()

                    var hasError = false

                    if (phone.isBlank()) {
                        errors += RowError(lineNumber, RowError.Kind.MissingPhoneNumber)
                        hasError = true
                    }

                    val scheduledMillis = when {
                        timeRaw.isBlank() -> {
                            errors += RowError(lineNumber, RowError.Kind.MissingTime)
                            hasError = true
                            null
                        }

                        else -> parseScheduledAt(timeRaw)?.also {
                            // parsed successfully
                        } ?: run {
                            errors += RowError(lineNumber, RowError.Kind.InvalidTime(timeRaw))
                            hasError = true
                            null
                        }
                    }

                    if (body.isBlank()) {
                        errors += RowError(lineNumber, RowError.Kind.MissingBody)
                        hasError = true
                    }

                    if (!hasError && scheduledMillis != null) {
                        rows += Row(name, phone, scheduledMillis, body)
                    }
                }
            }
        }

        return Result(rows, errors)
    }

    private fun parseCsvLine(line: String): List<String> {
        val results = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' -> {
                    if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                        current.append('"')
                        index += 1
                    } else {
                        inQuotes = !inQuotes
                    }
                }

                char == ',' && !inQuotes -> {
                    results += current.toString()
                    current.setLength(0)
                }

                else -> current.append(char)
            }
            index += 1
        }

        results += current.toString()
        return results.map { it.trim() }
    }

    private fun looksLikeHeader(columns: List<String>): Boolean {
        if (columns.size < 4) {
            return false
        }

        fun matches(value: String, options: Set<String>): Boolean {
            return options.any { option -> value.equals(option, ignoreCase = true) }
        }

        val normalized = columns.map { it.trim() }
        return matches(normalized[0], NAME_HEADERS) &&
            matches(normalized[1], PHONE_HEADERS) &&
            matches(normalized[2], TIME_HEADERS) &&
            matches(normalized[3], BODY_HEADERS)
    }

    private fun parseScheduledAt(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        if (trimmed.matches(NUMERIC_EPOCH_MILLIS)) {
            return runCatching { trimmed.toLong() }.getOrNull()
        }

        if (trimmed.matches(NUMERIC_EPOCH_SECONDS)) {
            return runCatching { trimmed.toLong() * 1000 }.getOrNull()
        }

        for (pattern in SUPPORTED_DATE_PATTERNS) {
            val formatter = SimpleDateFormat(pattern, Locale.getDefault())
            formatter.isLenient = false
            try {
                val date = formatter.parse(trimmed)
                if (date != null) {
                    return date.time
                }
            } catch (_: ParseException) {
                // continue to next formatter
            }
        }

        return null
    }

    companion object {
        val SUPPORTED_FORMATS: List<String> = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy.MM.dd HH:mm",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "MM/dd/yyyy HH:mm",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy hh:mm a",
            "yyyy-MM-dd hh:mm a",
            "yyyy/MM/dd hh:mm a",
            "yyyy年MM月dd日 HH:mm",
            "yyyy年MM月dd日HH:mm",
            "yyyyMMdd HHmm",
            "yyyyMMddHHmm"
        ).distinct()

        private val SUPPORTED_DATE_PATTERNS: List<String> = SUPPORTED_FORMATS
        private val NUMERIC_EPOCH_MILLIS = Regex("^\\d{13}$")
        private val NUMERIC_EPOCH_SECONDS = Regex("^\\d{10}$")

        private val NAME_HEADERS = setOf("name", "姓名", "联系人", "contact", "昵称")
        private val PHONE_HEADERS = setOf("phone", "phone number", "mobile", "手机号", "号码", "电话")
        private val TIME_HEADERS = setOf("time", "datetime", "schedule", "发送时间", "时间", "发送日期")
        private val BODY_HEADERS = setOf("message", "body", "text", "content", "短信内容", "内容", "短信")
    }
}
