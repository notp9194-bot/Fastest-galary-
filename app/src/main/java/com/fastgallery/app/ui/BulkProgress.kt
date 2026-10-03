package com.fastgallery.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fastgallery.app.R
import kotlinx.coroutines.delay

/**
 * Chalte hue bulk kaam (delete / copy) ki halat.
 * fraction = null: kitna baaki hai pata nahi (indeterminate bar).
 */
data class BulkProgress(
    val label: String,
    val fraction: Float? = null,
    val cancellable: Boolean = true,
)

/**
 * Snackbar jaisa progress card. Chhote kaam (kuch ms) me flash na kare isliye 350 ms baad hi dikhta hai.
 */
@Composable
fun BulkProgressBar(
    progress: BulkProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val active = progress != null
    LaunchedEffect(active) {
        if (active) {
            delay(350)
            visible = true
        } else visible = false
    }
    // Hide hote waqt animation ke liye aakhri value yaad rakho.
    var last by remember { mutableStateOf(progress) }
    if (progress != null) last = progress
    val shown = last

    AnimatedVisibility(
        visible = visible && active,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        if (shown != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            shown.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (shown.cancellable) {
                            TextButton(onClick = onCancel) {
                                Text(
                                    stringResource(R.string.action_cancel),
                                    color = MaterialTheme.colorScheme.inversePrimary,
                                )
                            }
                        }
                    }
                    val bar = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp)
                    val fraction = shown.fraction
                    if (fraction == null) {
                        LinearProgressIndicator(
                            modifier = bar,
                            color = MaterialTheme.colorScheme.inversePrimary,
                            trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.25f),
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = bar,
                            color = MaterialTheme.colorScheme.inversePrimary,
                            trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.25f),
                        )
                    }
                }
            }
        }
    }
}
