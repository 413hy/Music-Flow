package com.flow.music.data

import android.net.Uri

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val durationMs: Long
)
