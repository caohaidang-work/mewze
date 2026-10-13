package com.caohaidang.edward.music.constants

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

enum class WrappedPeriod {
    WEEK, MONTH, YEAR;

    fun range(offset: Int): Pair<LocalDateTime, LocalDateTime> {
        val today = LocalDate.now()
        val start: LocalDate
        val end: LocalDate
        when (this) {
            WEEK -> {
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
                end = start.plusWeeks(1)
            }
            MONTH -> {
                start = today.withDayOfMonth(1).plusMonths(offset.toLong())
                end = start.plusMonths(1)
            }
            YEAR -> {
                start = today.withDayOfYear(1).plusYears(offset.toLong())
                end = start.plusYears(1)
            }
        }
        return start.atStartOfDay() to end.atStartOfDay()
    }
}

fun LocalDateTime.toDbMillis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()
