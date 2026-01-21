package com.flow.music

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flow.music.alarm.AlarmScheduler
import com.flow.music.data.AudioTrack
import com.flow.music.ui.theme.FlowMusicTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlowMusicTheme {
                FlowMusicApp(viewModel, intent)
            }
        }
    }

    companion object {
        const val EXTRA_AUTO_PLAY = "extra_auto_play"
    }
}

private enum class FlowTab(val title: String) {
    NowPlaying("正在播放"),
    Library("本地曲库"),
    Settings("设置")
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FlowMusicApp(viewModel: MainViewModel, launchIntent: android.content.Intent?) {
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else null
    val permissionsList = listOfNotNull(readPermission, notificationPermission)
    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsList)
    val hasReadPermission = permissionsState.permissions.firstOrNull { it.permission == readPermission }?.status == PermissionStatus.Granted

    val context = LocalContext.current
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else true

    LaunchedEffect(Unit) {
        permissionsState.launchMultiplePermissionRequest()
    }

    LaunchedEffect(hasReadPermission) {
        if (hasReadPermission) {
            viewModel.loadLibrary()
        }
    }

    var currentTab by remember { mutableStateOf(FlowTab.NowPlaying) }

    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val scheduledTime by viewModel.scheduledTime.collectAsStateWithLifecycle()
    val scheduledDays by viewModel.scheduledDays.collectAsStateWithLifecycle()
    val skipDates by viewModel.skipDates.collectAsStateWithLifecycle()
    val autoStart by viewModel.autoStart.collectAsStateWithLifecycle()

    val appContext = context.applicationContext
    val scheduler = remember(appContext) { AlarmScheduler(appContext) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(launchIntent) {
        if (launchIntent?.getBooleanExtra(MainActivity.EXTRA_AUTO_PLAY, false) == true) {
            viewModel.requestAutoPlayOnNextLoad()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flow 音乐") },
                actions = {
                    Icon(imageVector = Icons.Default.MusicNote, contentDescription = null)
                }
            )
        },
        bottomBar = {
            NavigationBar {
                FlowTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            when (tab) {
                                FlowTab.NowPlaying -> Icon(Icons.Default.PlayArrow, contentDescription = null)
                                FlowTab.Library -> Icon(Icons.Default.LibraryMusic, contentDescription = null)
                                FlowTab.Settings -> Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!permissionsState.allPermissionsGranted) {
                PermissionRequest(
                    onGrantClick = { permissionsState.launchMultiplePermissionRequest() }
                )
            } else {
                when (currentTab) {
                    FlowTab.NowPlaying -> NowPlayingScreen(
                        track = currentTrack,
                        playlist = playlist,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        volume = volume,
                        onPlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onPrevious = { viewModel.previous() },
                        onSeek = { viewModel.seekTo(it) },
                        onVolumeChange = { viewModel.setVolume(it) },
                        onPlayTrack = { viewModel.play(it) },
                        onMoveUp = { index ->
                            if (index > 0) viewModel.moveInPlaylist(index, index - 1)
                        },
                        onMoveDown = { index ->
                            if (index < playlist.lastIndex) viewModel.moveInPlaylist(index, index + 1)
                        },
                        onRemove = { viewModel.removeFromPlaylist(it) },
                        onMoveDirect = { from, to -> viewModel.moveInPlaylist(from, to) }
                    )
                    FlowTab.Library -> LibraryScreen(
                        tracks = tracks,
                        onRefresh = { viewModel.loadLibrary() },
                        onPlay = { viewModel.play(it) },
                        onAdd = { viewModel.addToPlaylist(it) }
                    )
                    FlowTab.Settings -> SettingsScreen(
                        scheduledTime = scheduledTime,
                        scheduledDays = scheduledDays,
                        skipDates = skipDates,
                        autoStart = autoStart,
                        onTimeChange = { h, m -> viewModel.updateScheduledTime(h, m) },
                        onToggleDay = { viewModel.toggleDay(it) },
                        onToggleDate = { viewModel.toggleDate(it) },
                        onToggleAutoStart = { viewModel.setAutoStart(it) },
                        onSaveSchedule = {
                            scope.launch(Dispatchers.Default) {
                                scheduler.schedule(
                                    time = scheduledTime,
                                    daysOfWeek = scheduledDays,
                                    skipDates = skipDates,
                                    autoStart = autoStart
                                )
                                withContext(Dispatchers.Main) {
                                    viewModel.persistSchedule()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExact) {
                                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                                    }
                                }
                            }
                        },
                        onCancelSchedule = { scheduler.cancelAll() },
                        canScheduleExact = canScheduleExact,
                        onRequestExact = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                            }
                        },
                        onRequestBattery = {
                            val pm = context.getSystemService(PowerManager::class.java)
                            if (pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                })
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequest(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "需要读取本地音频文件才能使用", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onGrantClick) {
            Text("授予权限")
        }
    }
}

@Composable
private fun NowPlayingScreen(
    track: AudioTrack?,
    playlist: List<AudioTrack>,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (AudioTrack) -> Unit,
    onMoveDirect: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = track?.title ?: "未选择歌曲",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track?.artist ?: "请选择歌曲开始播放",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProgressSection(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onSeek = onSeek
                )
                ControlButtons(
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious
                )
                VolumeSection(volume = volume, onVolumeChange = onVolumeChange)
            }
        }

        PlaylistSection(
            playlist = playlist,
            currentTrackId = track?.id,
            onPlayTrack = onPlayTrack,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onRemove = onRemove,
            onMoveDirect = onMoveDirect
        )
    }
}

@Composable
private fun ProgressSection(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    Column {
        Slider(
            value = progress,
            onValueChange = { onSeek((it * safeDuration).toLong()) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(durationMs), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ControlButtons(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.SkipPrevious,
            contentDescription = "上一首",
            modifier = Modifier
                .clickable { onPrevious() }
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = "播放/暂停",
            modifier = Modifier
                .clickable { onPlayPause() }
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "下一首",
            modifier = Modifier
                .clickable { onNext() }
                .padding(4.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun VolumeSection(volume: Float, onVolumeChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("音量", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("${(volume * 100).toInt()}%", modifier = Modifier.padding(start = 4.dp))
            }
        }
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = onVolumeChange
        )
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<AudioTrack>,
    onRefresh: () -> Unit,
    onPlay: (AudioTrack) -> Unit,
    onAdd: (AudioTrack) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("本地音乐 (${tracks.size})", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onRefresh) {
                Text("重新扫描")
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("未找到音乐文件", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onPlay = { onPlay(track) },
                        onAdd = { onAdd(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: AudioTrack,
    onPlay: () -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    modifier = Modifier.clickable { onPlay() },
                    tint = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "加入播放列表",
                    modifier = Modifier.clickable { onAdd() },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    scheduledTime: java.time.LocalTime,
    scheduledDays: Set<DayOfWeek>,
    skipDates: Set<LocalDate>,
    autoStart: Boolean,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onToggleDate: (LocalDate) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onSaveSchedule: () -> Unit,
    onCancelSchedule: () -> Unit,
    canScheduleExact: Boolean,
    onRequestExact: () -> Unit,
    onRequestBattery: () -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    val timeState: TimePickerState = rememberTimePickerState(
        initialHour = scheduledTime.hour,
        initialMinute = scheduledTime.minute
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.titleMedium)
        SettingRow(
            title = "开机自动启动",
            description = "未来版本可开机即加载定时播放计划。",
            checked = autoStart,
            onCheckedChange = { onToggleAutoStart(it) }
        )
        TimePickerRow(
            scheduledTime = scheduledTime,
            onClick = { showTimePicker = true }
        )
        DayOfWeekSelector(
            selectedDays = scheduledDays,
            onToggleDay = onToggleDay
        )
        Button(onClick = { showCalendar = true }) {
            Text("选择需关闭的具体日期 (${skipDates.size} 天)")
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("精确闹钟权限", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (canScheduleExact) "已允许精确闹钟" else "未允许精确闹钟，可能无法准时播放",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (canScheduleExact) MaterialTheme.colorScheme.primary else Color.Gray
                )
                Button(onClick = onRequestExact) { Text("请求精确闹钟权限") }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("电池优化", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "建议忽略电池优化，避免后台被杀导致定时失效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Button(onClick = onRequestBattery) { Text("前往关闭优化") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onSaveSchedule
            ) { Text("保存并启用定时") }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onCancelSchedule
            ) { Text("取消全部") }
        }
        Text(
            text = "提示：暂未接入在线音乐源，当前仅支持本地扫描的音频文件。",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }

    if (showTimePicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                Button(onClick = {
                    onTimeChange(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                Button(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = timeState) }
        )
    }

    if (showCalendar) {
        CalendarDialog(
            scheduledDays = scheduledDays,
            skipDates = skipDates,
            onToggleDate = onToggleDate,
            onClose = { showCalendar = false }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PlaylistSection(
    playlist: List<AudioTrack>,
    currentTrackId: Long?,
    onPlayTrack: (AudioTrack) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRemove: (AudioTrack) -> Unit,
    onMoveDirect: (Int, Int) -> Unit
) {
    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 70.dp.toPx() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("播放列表（可调整顺序）", style = MaterialTheme.typography.titleMedium)
        if (playlist.isEmpty()) {
            Text("播放列表为空，去曲库添加吧", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(playlist.size) { idx ->
                    val track = playlist[idx]
                    var accumulatedDy by remember { mutableStateOf(0f) }
                    var draggingIndex by remember { mutableStateOf<Int?>(null) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayTrack(track) }
                            .pointerInput(playlist.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingIndex = idx; accumulatedDy = 0f },
                                    onDragEnd = { draggingIndex = null; accumulatedDy = 0f },
                                    onDragCancel = { draggingIndex = null; accumulatedDy = 0f },
                                    onDrag = { _, dragAmount ->
                                        accumulatedDy += dragAmount.y
                                        val steps = (accumulatedDy / itemHeightPx).roundToInt()
                                        if (draggingIndex != null && steps != 0) {
                                            val target = (draggingIndex!! + steps).coerceIn(0, playlist.lastIndex)
                                            if (target != draggingIndex) {
                                                onMoveDirect(draggingIndex!!, target)
                                                draggingIndex = target
                                                accumulatedDy = 0f
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontWeight = if (track.id == currentTrackId) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropUp,
                                    contentDescription = "上移",
                                    modifier = Modifier.clickable { onMoveUp(idx) },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "下移",
                                    modifier = Modifier.clickable { onMoveDown(idx) },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "移除",
                                    modifier = Modifier.clickable { onRemove(track) },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(
    scheduledTime: java.time.LocalTime,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("定时播放时间", fontWeight = FontWeight.SemiBold)
                Text("每天此时触发定时播放（预留 AlarmManager）", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(
                text = "%02d:%02d".format(scheduledTime.hour, scheduledTime.minute),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekSelector(
    selectedDays: Set<DayOfWeek>,
    onToggleDay: (DayOfWeek) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("每周播放日", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DayOfWeek.values().forEach { day ->
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = { onToggleDay(day) },
                    label = { Text(dayLabel(day)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CalendarDialog(
    scheduledDays: Set<DayOfWeek>,
    skipDates: Set<LocalDate>,
    onToggleDate: (LocalDate) -> Unit,
    onClose: () -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy / MM") }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("d") }
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDay = currentMonth.atDay(1)
    val leadingBlanks = (firstDay.dayOfWeek.value % 7)
    val days = remember(currentMonth) { (1..daysInMonth).map { currentMonth.atDay(it) } }
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + days

    fun prevMonth() { currentMonth = currentMonth.minusMonths(1) }
    fun nextMonth() { currentMonth = currentMonth.plusMonths(1) }
    fun prevYear() { currentMonth = currentMonth.minusYears(1) }
    fun nextYear() { currentMonth = currentMonth.plusYears(1) }

    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { Button(onClick = onClose) { Text("完成") } },
        title = { Text("选择需临时关闭的日期") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { prevMonth() }) { Text("<") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatter.format(currentMonth), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("周一至周日自动标记播放日，点日期切换关闭", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Button(onClick = { nextMonth() }) { Text(">") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(cells) { date ->
                        if (date == null) {
                            Spacer(modifier = Modifier.height(0.dp))
                            return@items
                        }
                        val playable = date.dayOfWeek in scheduledDays && !skipDates.contains(date)
                        val isSkip = skipDates.contains(date)
                        val bgColor = when {
                            isSkip -> Color(0xFF2B2B2B)
                            playable -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        }
                        val textColor = when {
                            isSkip -> Color.Gray
                            playable -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleDate(date) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .background(bgColor)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = dayFormatter.format(date),
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Text(
                                    text = if (isSkip) "关闭" else "播放",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
