package com.flow.music.alarm

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.flow.music.MainActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val storage = ScheduleStorage(context)

    fun schedule(
        time: LocalTime,
        daysOfWeek: Set<DayOfWeek>,
        skipDates: Set<LocalDate>,
        autoStart: Boolean
    ) {
        cancelAll()
        storage.save(time, daysOfWeek, skipDates, autoStart)
        val today = LocalDate.now()
        // Schedule for the next year to ensure long-term coverage.
        (0..364).forEach { offset ->
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek in daysOfWeek && !skipDates.contains(date)) {
                scheduleExact(LocalDateTime.of(date, time), requestCodeFor(date))
            }
        }
    }

    fun rescheduleFromStorage() {
        val saved = storage.read() ?: return
        schedule(saved.time, saved.days, saved.skipDates, saved.autoStart)
    }

    fun cancelAll() {
        // We cancel a reasonable range of request codes that we use.
        (0..4000).forEach { requestCode ->
            alarmManager?.cancel(alarmPendingIntent(requestCode))
        }
    }

    private fun scheduleExact(dateTime: LocalDateTime, requestCode: Int) {
        val triggerAtMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = alarmPendingIntent(requestCode)
        val showIntent = launcherPendingIntent()
        if (showIntent != null) {
            alarmManager?.setAlarmClock(
                AlarmClockInfo(triggerAtMillis, showIntent),
                pi
            )
        } else {
            alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun requestCodeFor(date: LocalDate): Int {
        return (date.toEpochDay() % 4000).toInt()
    }

    private fun alarmPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun launcherPendingIntent(): PendingIntent? {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_AUTO_PLAY, true)
        }
        return PendingIntent.getActivity(
            context,
            9999,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
