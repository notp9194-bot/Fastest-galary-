package com.fastgallery.app.ui

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fastgallery.app.R

/**
 * Video ke liye Picture-in-Picture. VideoPlayer yahan "video chal raha hai + size" batata hai,
 * Activity (onUserLeaveHint / onPictureInPictureModeChanged) yahan se PiP me jaati hai.
 * Chalte video me Home dabane par (API 31+ auto-enter, purane me onUserLeaveHint) PiP window khulti hai.
 */
object PipController {
    const val ACTION_TOGGLE = "com.fastgallery.app.PIP_TOGGLE"

    /** Compose ko dikhta hai: PiP window me chrome/controls chhupane ke liye. */
    var inPip by mutableStateOf(false)
        private set

    /** true = abhi video chal raha hai aur PiP me jaana theek hai. */
    @Volatile
    var armed: Boolean = false
        private set

    private var playing = false
    private var aspect = Rational(16, 9)

    fun setInPip(value: Boolean) {
        inPip = value
    }

    fun isSupported(activity: Activity?): Boolean =
        activity != null && Build.VERSION.SDK_INT >= 26 &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Video play/pause ya size badalne par. Sirf current page ka player bulaye. */
    fun update(activity: Activity?, isPlaying: Boolean, width: Int, height: Int) {
        if (!isSupported(activity)) return
        activity!!
        playing = isPlaying
        armed = isPlaying
        if (width > 0 && height > 0) {
            val ratio = (width.toFloat() / height).coerceIn(0.42f, 2.39f)
            aspect = Rational((ratio * 1000).toInt(), 1000)
        }
        runCatching { activity.setPictureInPictureParams(params(activity)) }
    }

    /** Video band/swipe-away/release: auto PiP band. */
    fun disarm(activity: Activity?) {
        armed = false
        playing = false
        if (!isSupported(activity)) return
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                activity!!.setPictureInPictureParams(
                    PictureInPictureParams.Builder().setAutoEnterEnabled(false).build(),
                )
            }
        }
    }

    fun enter(activity: Activity?): Boolean {
        if (!isSupported(activity)) return false
        return runCatching { activity!!.enterPictureInPictureMode(params(activity)) }.getOrDefault(false)
    }

    /** Activity.onUserLeaveHint se. */
    fun onUserLeaveHint(activity: Activity) {
        if (armed) enter(activity)
    }

    private fun params(activity: Activity): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
            .setActions(listOf(toggleAction(activity)))
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setAutoEnterEnabled(armed).setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    private fun toggleAction(activity: Activity): RemoteAction {
        val intent = PendingIntent.getBroadcast(
            activity,
            0,
            Intent(ACTION_TOGGLE).setPackage(activity.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = activity.getString(if (playing) R.string.video_pause else R.string.video_play)
        val icon = Icon.createWithResource(
            activity,
            if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
        )
        return RemoteAction(icon, title, title, intent)
    }
}
