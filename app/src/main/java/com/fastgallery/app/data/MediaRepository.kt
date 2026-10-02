package com.fastgallery.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

class MediaRepository(private val cr: ContentResolver) {

    /**
     * Loads a bounded window from the combined MediaStore.Files collection.
     * Sorting on the provider keeps page boundaries consistent for photos and videos.
     */
    fun queryPage(offset: Int, pageSize: Int): MediaPage {
        val collection = MediaStore.Files.getContentUri("external")
        val mediaTypeColumn = MediaStore.Files.FileColumns.MEDIA_TYPE
        val columns = mutableListOf(
            MediaStore.MediaColumns._ID,
            mediaTypeColumn,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Video.Media.DURATION,
        ).distinct().toMutableList()
        if (Build.VERSION.SDK_INT >= 29) columns += MediaStore.MediaColumns.RELATIVE_PATH
        val args = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "$mediaTypeColumn IN (?, ?)",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC, " +
                    "${MediaStore.MediaColumns._ID} DESC",
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize + 1)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset.coerceAtLeast(0))
        }

        return try {
            val result = ArrayList<MediaItem>(pageSize + 1)
            cr.query(collection, columns.toTypedArray(), args, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val typeColumn = cursor.getColumnIndexOrThrow(mediaTypeColumn)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val takenColumn = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val pathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val mediaUri: Uri = ContentUris.withAppendedId(
                        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    val dateTaken = if (takenColumn >= 0) cursor.getLong(takenColumn) else 0L
                    result += MediaItem(
                        id = id,
                        uri = mediaUri,
                        name = cursor.getString(nameColumn).orEmpty(),
                        mime = cursor.getString(mimeColumn)
                            ?: if (isVideo) "video/*" else "image/*",
                        dateAdded = cursor.getLong(dateColumn).takeIf { it > 0 }
                            ?: dateTaken / 1000L,
                        isVideo = isVideo,
                        durationMs = if (isVideo && durationColumn >= 0) {
                            cursor.getLong(durationColumn)
                        } else {
                            0L
                        },
                        bucketId = cursor.getLong(bucketColumn),
                        bucketName = cursor.getString(bucketNameColumn) ?: "Unknown",
                        sizeBytes = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L,
                        width = if (widthColumn >= 0) cursor.getInt(widthColumn) else 0,
                        height = if (heightColumn >= 0) cursor.getInt(heightColumn) else 0,
                        dateTaken = dateTaken,
                        relativePath = if (pathColumn >= 0) cursor.getString(pathColumn).orEmpty() else "",
                    )
                }
            }
            MediaPage(
                items = result.take(pageSize),
                hasMore = result.size > pageSize,
            )
        } catch (_: Exception) {
            MediaPage(items = emptyList(), hasMore = false)
        }
    }
}

data class MediaPage(val items: List<MediaItem>, val hasMore: Boolean)
