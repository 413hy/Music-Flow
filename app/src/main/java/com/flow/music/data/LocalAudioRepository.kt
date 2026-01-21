package com.flow.music.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalAudioRepository {
    suspend fun loadAllAudio(context: Context): List<AudioTrack> = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        val audioList = mutableListOf<AudioTrack>()
        val collection: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        resolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "未知标题"
                val artist = cursor.getString(artistColumn) ?: "未知艺术家"
                val duration = cursor.getLong(durationColumn)
                val contentUri = Uri.withAppendedPath(collection, id.toString())
                audioList.add(
                    AudioTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        uri = contentUri,
                        durationMs = duration
                    )
                )
            }
        }
        audioList
    }
}
