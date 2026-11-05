package com.example.studenthelper.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// DateTimeFormatter is immutable and thread-safe. Reusing it avoids creating a
// formatter for every task row during list composition and scroll.
private val TASK_DATE_FORMATTER: DateTimeFormatter by lazy {
    DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
}

fun taskDueLabel(
    dueDateEpochDay: Long,
    todayEpochDay: Long = LocalDate.now().toEpochDay(),
    isCompleted: Boolean = false
): String {
    if (isCompleted) {
        val date = LocalDate.ofEpochDay(dueDateEpochDay).format(TASK_DATE_FORMATTER)
        return "Completed · $date"
    }

    return when {
        dueDateEpochDay < todayEpochDay -> {
            val date = LocalDate.ofEpochDay(dueDateEpochDay).format(TASK_DATE_FORMATTER)
            "Overdue · $date"
        }
        dueDateEpochDay == todayEpochDay -> "Due today"
        dueDateEpochDay == todayEpochDay + 1L -> "Due tomorrow"
        else -> {
            val date = LocalDate.ofEpochDay(dueDateEpochDay).format(TASK_DATE_FORMATTER)
            "Due $date"
        }
    }
}

fun pickerMillisToEpochDay(millis: Long): Long =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
        .toEpochDay()

fun epochDayToPickerMillis(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay)
        .atStartOfDay(java.time.ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
