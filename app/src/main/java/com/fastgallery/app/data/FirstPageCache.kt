package com.fastgallery.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Cold start ke liye pehle page (FIRST_PAGE_SIZE items) ki halki snapshot.
 *
 * App khulte hi ye list turant dikhti hai; asli MediaStore query background me chalti hai aur
 * result is list ko replace kar deta hai (delete hui photos apne aap hat jaati hain).
 * Cache stale ho sakti hai, isliye UI refresh hone tak tap/open allow nahi karta.
 *
 * Format: binary (DataOutputStream), version ke saath. Koi bhi gadbad / purana version => miss (null).
 * File noBackupFilesDir me hai: cloud backup me nahi jaati. Write atomic hai (tmp + rename).
 */
class FirstPageCache(context: Context, fileName: String = FILE_NAME) {
    private val file = File(context.noBackupFilesDir, fileName)
    private val tmp = File(context.noBackupFilesDir, "$fileName.tmp")

    /** Aakhri baar jo list disk se padhi / likhi gayi; same list dobara likhne se bachne ke liye. */
    @Volatile private var last: List<MediaItem>? = null

    /** Disk se snapshot padho. Miss / corrupt / purana version => null. IO thread se bulao. */
    fun read(): List<MediaItem>? {
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != VERSION) return null
                val count = input.readInt()
                if (count !in 0..MAX_ITEMS) return null
                val out = ArrayList<MediaItem>(count)
                repeat(count) { out += readItem(input) }
                out
            }.also { last = it }
        } catch (error: Exception) {
            Log.w(TAG, "First page cache unreadable, ignoring", error)
            runCatching { file.delete() }
            null
        }
    }

    /** Pehle `limit` items save karo. Same content ho to disk touch nahi hoti. IO thread se bulao. */
    fun save(items: List<MediaItem>, limit: Int = MAX_ITEMS) {
        val snapshot = items.take(limit.coerceAtMost(MAX_ITEMS))
        if (snapshot == last) return
        try {
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(VERSION)
                out.writeInt(snapshot.size)
                snapshot.forEach { writeItem(out, it) }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) error("rename failed")
            }
            last = snapshot
        } catch (error: Exception) {
            Log.w(TAG, "First page cache write failed", error)
            runCatching { tmp.delete() }
        }
    }

    /** Library khali ho / permission gayi: purani cache hata do. */
    fun clear() {
        last = null
        runCatching { file.delete() }
        runCatching { tmp.delete() }
    }

    private fun writeItem(out: DataOutputStream, item: MediaItem) {
        out.writeLong(item.id)
        out.writeBoolean(item.isVideo)
        out.writeLong(item.dateAdded)
        out.writeLong(item.bucketId) // hidden/locked album filter ke liye zaroori
        out.writeLong(item.durationMs)
        out.writeLong(item.sizeBytes)
        out.writeLong(item.dateTaken)
        out.writeInt(item.width)
        out.writeInt(item.height)
        out.writeUTF(item.name)
        out.writeUTF(item.mime)
        out.writeUTF(item.bucketName)
        out.writeUTF(item.relativePath)
    }

    private fun readItem(input: DataInputStream): MediaItem {
        val id = input.readLong()
        val isVideo = input.readBoolean()
        val dateAdded = input.readLong()
        val bucketId = input.readLong()
        val durationMs = input.readLong()
        val sizeBytes = input.readLong()
        val dateTaken = input.readLong()
        val width = input.readInt()
        val height = input.readInt()
        val name = input.readUTF()
        val mime = input.readUTF()
        val bucketName = input.readUTF()
        val relativePath = input.readUTF()
        val uri: Uri = ContentUris.withAppendedId(
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            id,
        )
        return MediaItem(
            id = id,
            uri = uri,
            name = name,
            mime = mime,
            dateAdded = dateAdded,
            isVideo = isVideo,
            durationMs = durationMs,
            bucketId = bucketId,
            bucketName = bucketName,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            dateTaken = dateTaken,
            relativePath = relativePath,
        )
    }

    companion object {
        private const val TAG = "FirstPageCache"
        private const val FILE_NAME = "first_page.bin"
        private const val VERSION = 1
        const val MAX_ITEMS = 120
    }
}
