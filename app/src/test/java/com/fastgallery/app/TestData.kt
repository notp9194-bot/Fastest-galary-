package com.fastgallery.app

import android.net.Uri
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.MediaItem

/** 2023-11-15 00:00:00 UTC (Wed). Tests me sab dates isi ke aas-paas rakhi hain. */
const val DAY_START = 1_700_006_400L
const val DAY_SECONDS = 86_400L

fun testItem(
    id: Long,
    name: String = "img_$id.jpg",
    mime: String = "image/jpeg",
    dateAdded: Long = DAY_START,
    isVideo: Boolean = false,
    bucketId: Long = 1L,
    bucketName: String = "Camera",
    sizeBytes: Long = 0L,
    isTrashed: Boolean = false,
) = MediaItem(
    id = id,
    uri = Uri.parse("content://media/external/file/$id"),
    name = name,
    mime = mime,
    dateAdded = dateAdded,
    isVideo = isVideo,
    durationMs = 0L,
    bucketId = bucketId,
    bucketName = bucketName,
    sizeBytes = sizeBytes,
    isTrashed = isTrashed,
)

fun testKey(id: Long): String = "content://media/external/file/$id"

fun testAlbum(id: Long, name: String, coverDate: Long = DAY_START, count: Int = 1) = Album(
    id = id,
    name = name,
    cover = testItem(id * 1000, dateAdded = coverDate, bucketId = id, bucketName = name),
    count = count,
)
