package com.fastgallery.app

import android.content.Intent
import com.fastgallery.app.data.MediaFilter

/**
 * Doosre app ne "photo/video chuno" (ACTION_GET_CONTENT / ACTION_PICK) ke liye Fast Gallery kholi ho to uski details.
 * `filter` shuruaati media-type filter hai (user baad me badal sakta hai).
 */
data class PickRequest(val multiple: Boolean, val filter: MediaFilter) {
    companion object {
        fun from(intent: Intent?): PickRequest? {
            if (intent == null) return null
            if (intent.action != Intent.ACTION_GET_CONTENT && intent.action != Intent.ACTION_PICK) return null
            val types = buildList {
                intent.type?.let { add(it) }
                intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.let { addAll(it) }
            }.filter { it != "*/*" }
            val filter = when {
                types.isNotEmpty() && types.all { it.startsWith("video/") || it.endsWith("/video") } -> MediaFilter.VIDEOS
                types.isNotEmpty() && types.all { it.startsWith("image/") || it.endsWith("/image") } -> MediaFilter.PHOTOS
                else -> MediaFilter.ALL
            }
            return PickRequest(
                multiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
                filter = filter,
            )
        }
    }
}
