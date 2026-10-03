@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.fastgallery.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fastgallery.app.R
import com.fastgallery.app.data.Album
import com.fastgallery.app.data.MediaItem

/**
 * Copy YA move ke liye album picker (mode bahar se aata hai, picker ke andar badalta nahi): existing albums ki list (cover + naam + count) aur sabse upar "New album".
 * Album tap karte hi kaam shuru; folder ka naam type nahi karna padta.
 * onPick(naam, relativePath, move): relativePath = album ka asli folder (null = naya album, naam se banega).
 */
@Composable
fun AlbumPickerSheet(
    source: MediaItem,
    move: Boolean,
    albums: List<Album>,
    onDismiss: () -> Unit,
    onPick: (name: String, relativePath: String?, move: Boolean) -> Unit,
) = AlbumPickerSheet(listOf(source), move, albums, onDismiss, onPick)

/** Kai items ke liye: album "current" (move me disabled) tabhi maana jaata hai jab SAARE items usi album me hon. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(
    sources: List<MediaItem>,
    move: Boolean,
    albums: List<Album>,
    onDismiss: () -> Unit,
    onPick: (name: String, relativePath: String?, move: Boolean) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val thumbPx = with(LocalDensity.current) { 48.dp.roundToPx() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
        ) {
            // Mode (Copy ya Move) action chunte waqt hi tay ho chuka hai: title aur hint se saaf dikhta hai
            // ki original rahega ya hatega. Pehle yahan ek chhota switch tha jo aksar dikhta hi nahi tha.
            Text(
                stringResource(if (move) R.string.move_title else R.string.copy_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 2.dp),
            )
            Text(
                stringResource(if (move) R.string.move_hint else R.string.copy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                item(key = "new_album") {
                    PickerRow(
                        title = stringResource(R.string.copy_new_album),
                        subtitle = null,
                        enabled = true,
                        onClick = { creating = true },
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                items(albums, key = { it.id }) { album ->
                    val isCurrent = sources.isNotEmpty() && sources.all { it.bucketId == album.id }
                    // Move ka matlab usi album me wapas daalna bekaar hai; copy chalega (usi album me duplicate).
                    val enabled = !(move && isCurrent)
                    PickerRow(
                        title = album.name,
                        subtitle = if (isCurrent) {
                            stringResource(R.string.copy_current_album, album.count)
                        } else {
                            album.count.toString()
                        },
                        enabled = enabled,
                        onClick = { onPick(album.name, album.cover.relativePath.ifBlank { null }, move) },
                    ) {
                        AsyncImage(
                            model = rememberThumbRequest(album.cover.uri, thumbPx),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text(stringResource(R.string.copy_new_album)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.copy_folder_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = { onPick(newName.trim(), null, move); creating = false },
                ) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
