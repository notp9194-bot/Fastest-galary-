package com.fastgallery.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import com.fastgallery.app.ui.PipController
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.data.MediaItem
import com.fastgallery.app.ui.GalleryTheme
import com.fastgallery.app.ui.Viewer

/**
 * "Open with" (ACTION_VIEW): file manager, chat app, browser etc. se photo/video yahan khulti hai.
 * Halka aur alag Activity hai (poori library load nahi hoti); sirf dekhne, zoom, details aur share ke liye.
 * Bahar ki URI par edit/trash/rename/delete nahi chalate, isliye Viewer `readOnly` mode me hai.
 */
class ViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent?.data ?: streamExtra(intent)
        if (uri == null) {
            finish()
            return
        }
        val item = buildItem(uri, intent?.type)
        setContent {
            GalleryTheme(GalleryPreferences.theme(this)) {
                Viewer(
                    items = listOf(item),
                    startIndex = 0,
                    favoriteKeys = emptySet(),
                    trashedKeys = emptySet(),
                    onLoadMore = {},
                    onClose = { finish() },
                    onFavorite = {},
                    onSetTrashed = { _, _ -> },
                    onDelete = {},
                    onRename = { _, _ -> },
                    onCopyOrMove = { _, _, _ -> },
                    onWallpaper = {},
                    onEdit = { _, _ -> },
                    readOnly = true,
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PipController.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.setInPip(isInPictureInPictureMode)
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent?): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent?.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun buildItem(uri: Uri, typeHint: String?): MediaItem {
        var name: String? = null
        var size = 0L
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        }
        val mime = (runCatching { contentResolver.getType(uri) }.getOrNull() ?: typeHint)
            ?.takeIf { it.contains('/') } ?: "image/*"
        return MediaItem(
            id = 0L,
            uri = uri,
            name = name ?: uri.lastPathSegment.orEmpty(),
            mime = mime,
            dateAdded = 0L,
            isVideo = mime.startsWith("video/"),
            durationMs = 0L,
            bucketId = 0L,
            bucketName = "",
            sizeBytes = size,
        )
    }
}
