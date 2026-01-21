package com.flow.music.alarm

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class PlaySchedule(
    val time: LocalTime,
    val days: Set<DayOfWeek>,
    val skipDates: Set<LocalDate>,
    val autoStart: Boolean
)

class ScheduleStorage(context: Context) {
    private val prefs = context.getSharedPreferences("flow_schedule", Context.MODE_PRIVATE)

    fun save(time: LocalTime, days: Set<DayOfWeek>, skipDates: Set<LocalDate>, autoStart: Boolean) {
        prefs.edit()
            .putInt(KEY_HOUR, time.hour)
            .putInt(KEY_MINUTE, time.minute)
            .putStringSet(KEY_DAYS, days.map { it.name }.toSet())
            .putStringSet(KEY_DATES, skipDates.map { it.toString() }.toSet())
            .putBoolean(KEY_AUTO_START, autoStart)
            .apply()
    }

    fun read(): PlaySchedule? {
        if (!prefs.contains(KEY_HOUR) || !prefs.contains(KEY_MINUTE)) return null
        val hour = prefs.getInt(KEY_HOUR, 7)
        val minute = prefs.getInt(KEY_MINUTE, 0)
        val days = prefs.getStringSet(KEY_DAYS, emptySet())?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.toSet() ?: emptySet()
        val dates = prefs.getStringSet(KEY_DATES, emptySet())?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }?.toSet() ?: emptySet()
        val autoStart = prefs.getBoolean(KEY_AUTO_START, true)
        return PlaySchedule(time = LocalTime.of(hour, minute), days = days, skipDates = dates, autoStart = autoStart)
    }

    companion object {
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_DAYS = "days"
        private const val KEY_DATES = "dates"
        private const val KEY_AUTO_START = "auto_start"
    }
}
