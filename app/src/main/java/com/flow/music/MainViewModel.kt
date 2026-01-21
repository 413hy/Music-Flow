package com.flow.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flow.music.data.AudioTrack
import com.flow.music.data.LocalAudioRepository
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.flow.music.alarm.ScheduleStorage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalAudioRepository()
    private val player: ExoPlayer = ExoPlayer.Builder(application).build()
    private val scheduleStorage = ScheduleStorage(application)

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    private val _playlist = MutableStateFlow<List<AudioTrack>>(emptyList())
    val playlist: StateFlow<List<AudioTrack>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow<Int?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentIndex
        .combine(_playlist) { idx, list ->
            idx?.let { list.getOrNull(it) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _scheduledTime = MutableStateFlow(LocalTime.of(7, 0))
    val scheduledTime: StateFlow<LocalTime> = _scheduledTime.asStateFlow()

    private val _scheduledDays = MutableStateFlow(DayOfWeek.values().toSet())
    val scheduledDays: StateFlow<Set<DayOfWeek>> = _scheduledDays.asStateFlow()

    private val _skipDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val skipDates: StateFlow<Set<LocalDate>> = _skipDates.asStateFlow()

    private val _autoStart = MutableStateFlow(true)
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private var progressJob: Job? = null
    private var autoPlayOnLoad = false

    init {
        loadSavedSchedule()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentIndex.value = player.currentMediaItemIndex
                _durationMs.value = player.duration.coerceAtLeast(0L)
            }
        })
        _volume.value = player.volume
    }

    fun loadLibrary() {
        viewModelScope.launch {
            val list = repository.loadAllAudio(getApplication())
            _tracks.value = list
            if (_playlist.value.isEmpty()) {
                setPlaylist(list, startTrackId = null, keepPosition = 0L, playWhenReady = false)
            }
            if (autoPlayOnLoad && list.isNotEmpty()) {
                play(list.first())
                autoPlayOnLoad = false
            }
        }
    }

    fun play(track: AudioTrack) {
        val index = _playlist.value.indexOfFirst { it.id == track.id }
        if (index == -1) return
        ensurePlayerItems()
        player.seekTo(index, 0L)
        player.playWhenReady = true
        player.play()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.currentMediaItem == null && _playlist.value.isNotEmpty()) {
                play(_playlist.value.first())
            } else {
                player.play()
            }
        }
    }

    fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNext()
        }
    }

    fun previous() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPrevious()
        } else {
            player.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun setVolume(volume: Float) {
        player.volume = volume
        _volume.value = volume
    }

    fun addToPlaylist(track: AudioTrack) {
        _playlist.value = _playlist.value + track
        setPlaylist(_playlist.value, currentTrack.value?.id, player.currentPosition, player.isPlaying)
    }

    fun removeFromPlaylist(track: AudioTrack) {
        val updated = _playlist.value.filterNot { it.id == track.id }
        setPlaylist(updated, currentTrack.value?.id, player.currentPosition, player.isPlaying)
    }

    fun moveInPlaylist(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val list = _playlist.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        setPlaylist(list, currentTrack.value?.id, player.currentPosition, player.isPlaying)
    }

    fun updateScheduledTime(hour: Int, minute: Int) {
        _scheduledTime.value = LocalTime.of(hour, minute)
    }

    fun toggleDay(day: DayOfWeek) {
        _scheduledDays.value = _scheduledDays.value.let { set ->
            if (set.contains(day)) set - day else set + day
        }
    }

    fun toggleDate(date: LocalDate) {
        _skipDates.value = _skipDates.value.let { set ->
            if (set.contains(date)) set - date else set + date
        }
    }

    fun requestAutoPlayOnNextLoad() {
        autoPlayOnLoad = true
    }

    fun persistSchedule() {
        scheduleStorage.save(_scheduledTime.value, _scheduledDays.value, _skipDates.value, _autoStart.value)
    }

    fun setAutoStart(enabled: Boolean) {
        _autoStart.value = enabled
        persistSchedule()
    }

    private fun loadSavedSchedule() {
        scheduleStorage.read()?.let {
            _scheduledTime.value = it.time
            _scheduledDays.value = it.days.ifEmpty { DayOfWeek.values().toSet() }
            _skipDates.value = it.skipDates
            _autoStart.value = it.autoStart
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        player.release()
    }

    private fun setPlaylist(
        playlist: List<AudioTrack>,
        startTrackId: Long?,
        keepPosition: Long,
        playWhenReady: Boolean
    ) {
        _playlist.value = playlist
        val mediaItems = playlist.map { track ->
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.id.toString())
                .setTag(track)
                .setMediaMetadata(
                    com.google.android.exoplayer2.MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .build()
                )
                .build()
        }
        val targetIndex = startTrackId?.let { id -> playlist.indexOfFirst { it.id == id } } ?: 0
        val position = if (targetIndex >= 0) keepPosition else 0L
        player.setMediaItems(mediaItems, targetIndex.coerceAtLeast(0), position)
        player.prepare()
        if (playWhenReady && playlist.isNotEmpty()) player.play() else player.pause()
        _currentIndex.value = targetIndex.takeIf { playlist.indices.contains(it) }
        _durationMs.value = player.duration.coerceAtLeast(0L)
        _positionMs.value = position
    }

    private fun ensurePlayerItems() {
        if (player.mediaItemCount == 0 && _playlist.value.isNotEmpty()) {
            setPlaylist(_playlist.value, _playlist.value.first().id, 0L, playWhenReady = false)
        }
    }

    private fun startProgressUpdates() {
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (true) {
                _positionMs.value = player.currentPosition
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }
}
