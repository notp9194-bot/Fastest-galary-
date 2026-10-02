package com.fastgallery.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

class MediaRepository(private val cr: ContentResolver) {

    fun queryAll(): List<MediaItem> {
        val out = ArrayList<MediaItem>(4096)
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, out)
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, out)
        out.sortByDescending { it.dateAdded }
        return out
    }

    private fun query(base: Uri, video: Boolean, out: MutableList<MediaItem>) {
        // Common column names images aur videos dono me same hain.
        val cols = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        if (video) cols += MediaStore.Video.Media.DURATION

        try {
            cr.query(base, cols.toTypedArray(), null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")
                ?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val iMime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val iDate = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val iBucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    val iBucketName = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val iDur = if (video) c.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                    val fallbackMime = if (video) "video/*" else "image/*"

                    while (c.moveToNext()) {
                        val id = c.getLong(iId)
                        out += MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(base, id),
                            name = c.getString(iName) ?: "",
                            mime = c.getString(iMime) ?: fallbackMime,
                            dateAdded = c.getLong(iDate),
                            isVideo = video,
                            durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                            bucketId = c.getLong(iBucket),
                            bucketName = c.getString(iBucketName) ?: "Unknown",
                        )
                    }
                }
        } catch (_: Exception) {
            // Permission nahi / provider error: khaali list.
        }
    }
}
