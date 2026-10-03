@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.fastgallery.app.ui

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Stable
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fastgallery.app.R
import com.fastgallery.app.data.GalleryPreferences
import com.fastgallery.app.data.MediaItem as GalleryMediaItem
import com.fastgallery.app.findActivity
import java.util.Locale

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val AUTO_HIDE_MS = 3000L

/** Material "Volume up" icon (extended icons dependency ke bina). */
internal val VolumeUpIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "VolumeUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "Volume off" icon. */
internal val VolumeOffIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "VolumeOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zM19,12c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27 7.73,9L3,9v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18L12,4z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "Repeat" icon. */
internal val RepeatIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Repeat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M7,7h10v3l4,-4 -4,-4v3L5,5v6h2L7,7zM17,17L7,17v-3l-4,4 4,4v-3h12v-6h-2v4z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "Picture in picture alt" icon. */
internal val PipIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "PictureInPicture",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M19,11h-8v6h8v-6zM21,19L21,4.98C21,3.88 20.1,3 19,3L5,3c-1.1,0 -2,0.88 -2,1.98L3,19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2zM19,19.02L5,19.02L5,4.97h14v14.05z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Video par vertical swipe se kya badle: left side = brightness, right side = volume. */
enum class VideoAdjust { BRIGHTNESS, VOLUME }

/** Screen ki chaudai ka itna hissa (left/right) side-zone hai: left = brightness / -10 s, right = volume / +10 s. */
private const val SIDE_ZONE_FRACTION = 0.33f
private const val SEEK_STEP_MS = 10_000L
private const val SEEK_HUD_MS = 700L
private const val ADJUST_HUD_LINGER_MS = 700L
private const val HOLD_SPEED = 2f
/** Poori range (0..100%) itni screen-height ki swipe me tay hoti hai. */
private const val ADJUST_RANGE_FRACTION = 0.7f

/**
 * x position se zone: left = brightness, right = volume, beech = null (beech me swipe-down close / swipe-up details
 * pehle jaise chalte hain).
 */
fun adjustZoneAt(x: Float, width: Float): VideoAdjust? = when {
    width <= 0f -> null
    x < width * SIDE_ZONE_FRACTION -> VideoAdjust.BRIGHTNESS
    x > width * (1f - SIDE_ZONE_FRACTION) -> VideoAdjust.VOLUME
    else -> null
}

/**
 * Viewer (jo touch gestures pakadta hai) aur VideoPlayer (jo player/HUD sambhalta hai) ke beech ka pul.
 * VideoPlayer apne lambdas register karta hai; Viewer ke gestures unhe bulate hain. Player na ho to sab no-op.
 */
@Stable
class VideoGestureHandler {
    internal var doubleTapImpl: (Float, Float) -> Unit = { _, _ -> }
    internal var tapAsSeekImpl: (Float, Float) -> Boolean = { _, _ -> false }
    internal var holdImpl: (Boolean) -> Unit = {}
    internal var adjustStartImpl: (VideoAdjust) -> Unit = {}
    internal var adjustByImpl: (VideoAdjust, Float) -> Unit = { _, _ -> }
    internal var adjustEndImpl: () -> Unit = {}

    /** Double-tap: left = -10 s, right = +10 s, beech = play/pause. */
    fun doubleTap(x: Float, width: Float) = doubleTapImpl(x, width)

    /** Double-tap seek ke turant baad ke single taps bhi seek hi karte hain (true = tap istemal ho gaya, chrome mat badlo). */
    fun tapAsSeek(x: Float, width: Float): Boolean = tapAsSeekImpl(x, width)

    /** Long-press pakde rakhne par 2x (true), chhodne par wapas (false). */
    fun hold(active: Boolean) = holdImpl(active)

    fun adjustStart(kind: VideoAdjust) = adjustStartImpl(kind)

    /** dragAmountPx: Compose ka vertical drag (upar = negative). */
    fun adjustBy(kind: VideoAdjust, dragAmountPx: Float, heightPx: Float) =
        adjustByImpl(kind, -dragAmountPx / (heightPx * ADJUST_RANGE_FRACTION).coerceAtLeast(1f))

    fun adjustEnd() = adjustEndImpl()

    internal fun reset() {
        doubleTapImpl = { _, _ -> }
        tapAsSeekImpl = { _, _ -> false }
        holdImpl = {}
        adjustStartImpl = {}
        adjustByImpl = { _, _ -> }
        adjustEndImpl = {}
    }
}

/**
 * Window ki brightness override. Pehli adjust pe window ka asli (aksar -1 = system) value yaad rakhte hain;
 * Viewer band hote hi [restore] se wapas. Window ke hisaab se yaad rakhta hai, taaki MainActivity/ViewActivity na mile.
 */
internal object VideoBrightness {
    private val originals = WeakHashMap<android.view.Window, Float>()

    fun current(activity: Activity?): Float {
        val window = activity?.window ?: return 0.5f
        val override = window.attributes.screenBrightness
        if (override >= 0f) return override
        return runCatching {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f).coerceIn(0.05f, 1f)
    }

    fun set(activity: Activity?, value: Float) {
        val window = activity?.window ?: return
        val attrs = window.attributes
        if (!originals.containsKey(window)) originals[window] = attrs.screenBrightness
        attrs.screenBrightness = value.coerceIn(0.02f, 1f)
        window.attributes = attrs
    }

    fun restore(activity: Activity?) {
        val window = activity?.window ?: return
        val original = originals.remove(window) ?: return
        val attrs = window.attributes
        attrs.screenBrightness = original
        window.attributes = attrs
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

private fun speedText(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

/**
 * Custom video player UI: ExoPlayer (bina default controller) + Compose controls -
 * play/pause, seek bar with time, mute aur playback speed.
 * Controls viewer ke chrome ke saath dikhte/chhupte hain (tap se); video chalte waqt 3 sec baad auto-hide.
 */
@Composable
fun VideoPlayer(
    item: GalleryMediaItem,
    isCurrent: Boolean,
    controlsVisible: Boolean,
    onHideControls: () -> Unit,
    /** Double-tap seek, long-press 2x, brightness/volume swipe: Viewer ke gestures isi se player tak aate hain. */
    gestures: VideoGestureHandler = remember { VideoGestureHandler() },
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val hideControls by rememberUpdatedState(onHideControls)
    val currentFlag by rememberUpdatedState(isCurrent)
    // PiP window me controls nahi dikhte (system ke apne play/pause button hote hain).
    val controlsVisible = controlsVisible && !PipController.inPip
    // Pichhli baar jahan chhoda tha wahin se shuru (3 sec se zyada dekha ho to).
    val startMs = remember(item.key) { GalleryPreferences.videoPosition(context, item.key) }
    val player = remember(item.key) {
        ExoPlayer.Builder(context).build().apply {
            if (startMs > 0L) setMediaItem(MediaItem.fromUri(item.uri), startMs)
            else setMediaItem(MediaItem.fromUri(item.uri))
            repeatMode = if (GalleryPreferences.videoLoop(context)) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = false
        }
    }

    var isPlaying by remember(player) { mutableStateOf(false) }
    var durationMs by remember(player) { mutableLongStateOf(item.durationMs.coerceAtLeast(0L)) }
    var positionMs by remember(player) { mutableLongStateOf(startMs) }
    var muted by remember(player) { mutableStateOf(GalleryPreferences.videoMuted(context)) }
    var speed by remember(player) { mutableFloatStateOf(1f) }
    var seeking by remember(player) { mutableStateOf(false) }
    var seekFraction by remember(player) { mutableFloatStateOf(0f) }
    var interaction by remember(player) { mutableIntStateOf(0) }
    var speedMenu by remember(player) { mutableStateOf(false) }
    var loop by remember(player) { mutableStateOf(GalleryPreferences.videoLoop(context)) }
    val pipSupported = remember(activity) { PipController.isSupported(activity) }
    val haptic = LocalHapticFeedback.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    // Gesture HUD: double-tap seek (direction -1/+1, jama seconds), 2x hold, brightness/volume.
    var seekDirection by remember(player) { mutableIntStateOf(0) }
    var seekTotalSec by remember(player) { mutableIntStateOf(0) }
    var seekStamp by remember(player) { mutableIntStateOf(0) }
    var holdFast by remember(player) { mutableStateOf(false) }
    var adjustKind by remember(player) { mutableStateOf<VideoAdjust?>(null) }
    var adjustValue by remember(player) { mutableFloatStateOf(0f) }
    var adjusting by remember(player) { mutableStateOf(false) }
    var adjustEndStamp by remember(player) { mutableIntStateOf(0) }

    // Position yaad rakho: bahut shuru/bahut aakhir ya poora dekh liya ho to saved position hata do.
    fun savePosition() {
        val d = player.duration
        if (d == C.TIME_UNSET || d <= 0L) return // abhi prepare nahi hua: purani saved position mat chhedo
        val p = player.currentPosition
        val keep = player.playbackState != Player.STATE_ENDED && p >= 3_000L && p <= d - 3_000L
        GalleryPreferences.setVideoPosition(context, item.key, if (keep) p else 0L)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (currentFlag) PipController.update(activity, playing, player.videoSize.width, player.videoSize.height)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (currentFlag && player.isPlaying) PipController.update(activity, true, videoSize.width, videoSize.height)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = player.duration
                if (d != C.TIME_UNSET && d > 0L) durationMs = d
                if (playbackState == Player.STATE_ENDED) positionMs = durationMs
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            savePosition()
            if (currentFlag) PipController.disarm(activity)
            player.release()
        }
    }

    // Swipe karke dusre page pe jaane ya app background me jaane par video pause.
    val autoplay = remember(context) { GalleryPreferences.videoAutoplay(context) }
    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            player.pause()
            savePosition()
            PipController.disarm(activity)
        } else if (autoplay && player.playbackState != Player.STATE_ENDED) {
            // Settings: "Autoplay videos". Page current hote hi chalu.
            player.play()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Chalte video me Home dabane par PiP shuru hota hai (activity pause hoti hai, stop nahi): tab pause mat karo.
                Lifecycle.Event.ON_PAUSE -> {
                    if (!PipController.armed) player.pause()
                    savePosition()
                }
                // App sach me background me gaya (ya PiP window band hui): pause.
                Lifecycle.Event.ON_STOP -> {
                    player.pause()
                    savePosition()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // PiP window ke play/pause button ka broadcast.
    DisposableEffect(player, isCurrent, pipSupported) {
        if (!isCurrent || !pipSupported) {
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent?.action != PipController.ACTION_TOGGLE) return
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PipController.ACTION_TOGGLE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }

    LaunchedEffect(player, loop) { player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(player, speed) { player.setPlaybackSpeed(speed) }

    // Controls dikh rahe hon tabhi position poll karo.
    LaunchedEffect(player, controlsVisible) {
        if (controlsVisible) {
            while (true) {
                if (!seeking) positionMs = player.currentPosition.coerceAtLeast(0L)
                kotlinx.coroutines.delay(250)
            }
        }
    }
    // Chalte video me controls auto-hide; koi interaction ho to timer reset.
    LaunchedEffect(isPlaying, controlsVisible, interaction, seeking, speedMenu) {
        if (isPlaying && controlsVisible && !seeking && !speedMenu) {
            kotlinx.coroutines.delay(AUTO_HIDE_MS)
            hideControls()
        }
    }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        interaction++
    }

    // Seek HUD kuch der baad apne aap hat jata hai (har naye seek pe timer dobara).
    LaunchedEffect(seekStamp) {
        if (seekStamp > 0) {
            kotlinx.coroutines.delay(SEEK_HUD_MS)
            seekDirection = 0
            seekTotalSec = 0
        }
    }
    LaunchedEffect(adjustEndStamp) {
        if (adjustEndStamp > 0) {
            kotlinx.coroutines.delay(ADJUST_HUD_LINGER_MS)
            if (!adjusting) adjustKind = null
        }
    }

    fun seekBy(direction: Int) {
        val d = player.duration
        val max = if (d == C.TIME_UNSET || d <= 0L) Long.MAX_VALUE else d
        val target = (player.currentPosition + direction * SEEK_STEP_MS).coerceIn(0L, max)
        player.seekTo(target)
        positionMs = target
        seekTotalSec = if (direction == seekDirection) seekTotalSec + (SEEK_STEP_MS / 1000L).toInt() else (SEEK_STEP_MS / 1000L).toInt()
        seekDirection = direction
        seekStamp++
        interaction++
    }

    DisposableEffect(gestures, player) {
        gestures.doubleTapImpl = { x, width ->
            when {
                width <= 0f -> Unit
                x < width * SIDE_ZONE_FRACTION -> seekBy(-1)
                x > width * (1f - SIDE_ZONE_FRACTION) -> seekBy(1)
                else -> togglePlay()
            }
        }
        gestures.tapAsSeekImpl = { x, width ->
            when {
                seekDirection == 0 || width <= 0f -> false
                x < width * SIDE_ZONE_FRACTION -> { seekBy(-1); true }
                x > width * (1f - SIDE_ZONE_FRACTION) -> { seekBy(1); true }
                else -> false
            }
        }
        gestures.holdImpl = { active ->
            if (active) {
                // Sirf chalte video me; ruka hua ho to long-press kuch nahi karta.
                if (player.isPlaying && !holdFast) {
                    holdFast = true
                    player.setPlaybackSpeed(HOLD_SPEED)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } else if (holdFast) {
                holdFast = false
                player.setPlaybackSpeed(speed)
            }
        }
        gestures.adjustStartImpl = { kind ->
            if (kind == VideoAdjust.VOLUME && audio.isVolumeFixed) {
                adjusting = false
                adjustKind = null
            } else {
                adjusting = true
                adjustKind = kind
                adjustValue = when (kind) {
                    VideoAdjust.BRIGHTNESS -> VideoBrightness.current(activity)
                    VideoAdjust.VOLUME -> audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                }
                interaction++
            }
        }
        gestures.adjustByImpl = { kind, fraction ->
            if (adjusting && adjustKind == kind) {
                adjustValue = (adjustValue + fraction).coerceIn(0f, 1f)
                when (kind) {
                    VideoAdjust.BRIGHTNESS -> VideoBrightness.set(activity, adjustValue)
                    VideoAdjust.VOLUME -> {
                        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        runCatching {
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (adjustValue * maxVolume).roundToInt(), 0)
                        }
                    }
                }
            }
        }
        gestures.adjustEndImpl = {
            adjusting = false
            adjustEndStamp++
        }
        onDispose {
            if (holdFast) player.setPlaybackSpeed(speed)
            gestures.reset()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = {
                it.player = player
                it.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(
                onClick = { togglePlay() },
                modifier = Modifier.size(64.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    if (isPlaying) PauseIcon else Icons.Filled.PlayArrow,
                    stringResource(if (isPlaying) R.string.video_pause else R.string.video_play),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            // Viewer ka bottom action bar iske neeche rehta hai.
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                val duration = durationMs.coerceAtLeast(1L)
                val fraction = if (seeking) seekFraction else (positionMs.toFloat() / duration).coerceIn(0f, 1f)
                val shownMs = if (seeking) (seekFraction * duration).toLong() else positionMs
                val seekLabel = stringResource(R.string.video_seek)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(shownMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = fraction,
                        onValueChange = {
                            seeking = true
                            seekFraction = it
                        },
                        onValueChangeFinished = {
                            val target = (seekFraction * duration).toLong()
                            player.seekTo(target)
                            positionMs = target
                            seeking = false
                            interaction++
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            .semantics { contentDescription = seekLabel },
                    )
                    Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        muted = !muted
                        interaction++
                    }) {
                        Icon(
                            if (muted) VolumeOffIcon else VolumeUpIcon,
                            stringResource(if (muted) R.string.video_unmute else R.string.video_mute),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = {
                            loop = !loop
                            GalleryPreferences.setVideoLoop(context, loop)
                            interaction++
                        },
                        modifier = Modifier.background(
                            if (loop) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                            CircleShape,
                        ),
                    ) {
                        Icon(
                            RepeatIcon,
                            stringResource(if (loop) R.string.video_loop_on else R.string.video_loop_off),
                            tint = Color.White,
                        )
                    }
                    if (pipSupported) {
                        IconButton(onClick = {
                            interaction++
                            PipController.enter(activity)
                        }) {
                            Icon(PipIcon, stringResource(R.string.video_pip), tint = Color.White)
                        }
                    }
                    Box {
                        val speedDescription = stringResource(R.string.video_speed)
                        TextButton(
                            onClick = { speedMenu = true },
                            modifier = Modifier.semantics { contentDescription = speedDescription },
                        ) {
                            Text(
                                stringResource(R.string.video_speed_format, speedText(speed)),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                            SPEEDS.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.video_speed_format, speedText(option))) },
                                    onClick = {
                                        speed = option
                                        speedMenu = false
                                        interaction++
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!PipController.inPip) {
            if (seekDirection != 0) {
                SeekHud(
                    direction = seekDirection,
                    seconds = seekTotalSec,
                    modifier = Modifier
                        .align(if (seekDirection < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 36.dp),
                )
            }
            val hudModifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(top = 64.dp)
            val kind = adjustKind
            if (kind != null) {
                AdjustHud(kind, adjustValue, hudModifier)
            } else if (holdFast) {
                HoldHud(hudModifier)
            }
        }
    }
}

@Composable
private fun SeekHud(direction: Int, seconds: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = if (direction < 0) -1f else 1f },
        )
        Text(
            stringResource(
                if (direction < 0) R.string.video_seek_back_hud else R.string.video_seek_forward_hud,
                seconds,
            ),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun HoldHud(modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.video_hold_speed_hud),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun AdjustHud(kind: VideoAdjust, value: Float, modifier: Modifier = Modifier) {
    val percent = (value * 100f).roundToInt()
    val description = stringResource(
        if (kind == VideoAdjust.BRIGHTNESS) R.string.video_hud_brightness else R.string.video_hud_volume,
        percent,
    )
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (kind == VideoAdjust.BRIGHTNESS) {
            BrightnessGlyph()
        } else {
            Icon(
                if (value <= 0.001f) VolumeOffIcon else VolumeUpIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.width(110.dp).padding(horizontal = 10.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f),
        )
        Text("$percent%", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/** Chhota "sun" icon (extended icons dependency ke bina): beech me gola + 8 kirnein. */
@Composable
private fun BrightnessGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val c = center
        val unit = size.minDimension
        drawCircle(Color.White, radius = unit * 0.22f, center = c)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                Color.White,
                start = Offset(c.x + dx * unit * 0.34f, c.y + dy * unit * 0.34f),
                end = Offset(c.x + dx * unit * 0.48f, c.y + dy * unit * 0.48f),
                strokeWidth = unit * 0.09f,
                cap = StrokeCap.Round,
            )
        }
    }
}
