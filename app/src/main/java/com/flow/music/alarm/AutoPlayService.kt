package com.flow.music.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.flow.music.MainActivity
import com.flow.music.R
import com.flow.music.data.LocalAudioRepository
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AutoPlayService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("正在准备播放"))
        scope.launch {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            if (!hasPermission) {
                stopSelf()
                return@launch
            }
            val repo = LocalAudioRepository()
            val tracks = repo.loadAllAudio(applicationContext)
            if (tracks.isEmpty()) {
                stopSelf()
                return@launch
            }
            val exo = ExoPlayer.Builder(applicationContext).build().also { player = it }
            val items = tracks.map { track ->
                MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaId(track.id.toString())
                    .build()
            }
            exo.setMediaItems(items, /* startIndex= */0, /* startPositionMs= */0)
            exo.prepare()
            exo.playWhenReady = true
            exo.play()
            // Update notification to show playing
            startForeground(NOTIFICATION_ID, buildNotification("已自动播放闹钟音乐"))
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Flow闹钟播放",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_AUTO_PLAY, true)
        }
        val pending = PendingIntent.getActivity(
            this,
            7777,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flow 定时播放")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "flow_alarm_playback"
        private const val NOTIFICATION_ID = 2001
    }
}
