package com.spendwise.android.core.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Display + Material3 DatePicker conversion helpers for `LocalDate`
 * (M11 — transaction `occurredOn`).
 *
 * Two hard rules, both tested in `DateFormatTest`:
 *
 * - Display is locale-aware: `ofLocalizedDate(MEDIUM)` on the device
 *   locale — never an ISO string and never a hardcoded en-US pattern.
 * - Picker conversion is UTC-anchored: Material3 `DatePickerState`
 *   speaks epoch-millis at UTC start-of-day. Converting through the
 *   device zone can shift the selected calendar day by ±1 — so both
 *   directions pin the instant to UTC midnight exactly.
 */
object DateFormat {

    /** Locale-aware human date ("Oct 6, 2026" en-US / "6 thg 10, 2026" vi). */
    fun format(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    /** `LocalDate` → the `selectedDateMillis` a Material3 DatePicker expects. */
    fun LocalDate.toPickerUtcMillis(): Long =
        atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** `selectedDateMillis` → `LocalDate` — inverse of [toPickerUtcMillis]. */
    fun pickerUtcMillisToLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
