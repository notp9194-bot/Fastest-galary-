package com.fastgallery.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )
}

fun hasMediaAccess(ctx: Context): Boolean =
    mediaPermissions().any {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Android 14+ "Selected photos and videos" mode: user ne sirf chuni hui media ka access diya hai
 * (READ_MEDIA_VISUAL_USER_SELECTED granted, par full READ_MEDIA_IMAGES/VIDEO nahi).
 */
fun isPartialMediaAccess(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return false
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
    return granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) &&
        !granted(Manifest.permission.READ_MEDIA_IMAGES) &&
        !granted(Manifest.permission.READ_MEDIA_VIDEO)
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
