package com.spendwise.android.core.ui.format

import com.spendwise.android.core.ui.format.DateFormat.pickerUtcMillisToLocalDate
import com.spendwise.android.core.ui.format.DateFormat.toPickerUtcMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

/**
 * M11 — the two hard rules of [DateFormat]: locale-aware display and
 * UTC-anchored picker conversion that can never shift the calendar day.
 */
class DateFormatTest {

    @Test
    fun `format is locale-aware, not a hardcoded pattern`() {
        val date = LocalDate.of(2026, 10, 4)

        val en = DateFormat.format(date, Locale.US)
        val vi = DateFormat.format(date, Locale.forLanguageTag("vi"))

        assertEquals("Oct 4, 2026", en)
        assertTrue("vi MEDIUM should differ from en-US and stay non-ISO: $vi", vi != en)
        assertTrue("never leaks the ISO wire shape: $vi", !vi.contains("2026-10-04"))
    }

    @Test
    fun `picker millis round-trip the exact LocalDate`() {
        listOf(
            LocalDate.of(2026, 10, 4),
            LocalDate.of(2024, 2, 29),  // leap day
            LocalDate.of(2025, 12, 31), // year boundary
            LocalDate.of(2026, 1, 1),
            LocalDate.of(1970, 1, 1),   // epoch
        ).forEach { date ->
            assertEquals(date, pickerUtcMillisToLocalDate(date.toPickerUtcMillis()))
        }
    }

    @Test
    fun `picker millis are always UTC midnight — device zone cannot drift the day`() {
        val date = LocalDate.of(2026, 10, 4)
        val expected = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val original = TimeZone.getDefault()
        try {
            // +14:00 and -11:00 bracket every real-world offset.
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val plus14 = date.toPickerUtcMillis()
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Niue"))
            val minus11 = date.toPickerUtcMillis()

            assertEquals(expected, plus14)
            assertEquals(expected, minus11)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `picker millis to LocalDate is UTC-anchored under any device zone`() {
        val millis = LocalDate.of(2026, 3, 1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val plus14 = pickerUtcMillisToLocalDate(millis)
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Niue"))
            val minus11 = pickerUtcMillisToLocalDate(millis)

            assertEquals(LocalDate.of(2026, 3, 1), plus14)
            assertEquals(LocalDate.of(2026, 3, 1), minus11)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
