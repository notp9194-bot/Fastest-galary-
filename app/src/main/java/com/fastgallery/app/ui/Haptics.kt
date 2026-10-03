package com.fastgallery.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * Settings ke "Haptic feedback" switch ke liye: band hone par neeche ke saare LocalHapticFeedback calls no-op ho jaate hain.
 * Provider hamesha lagta hai (sirf value badalti hai), isliye toggle karne par content ka state reset nahi hota.
 */
@Composable
fun HapticsGate(enabled: Boolean, content: @Composable () -> Unit) {
    val real = LocalHapticFeedback.current
    CompositionLocalProvider(LocalHapticFeedback provides if (enabled) real else NoHaptics, content = content)
}
