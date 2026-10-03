package com.fastgallery.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fastgallery.app.R

internal fun materialIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

/** Material "Select all" icon (extended icons dependency ke bina). */
val SelectAllIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    materialIcon(
        "SelectAll",
        "M3,5h2L5,3c-1.1,0 -2,0.9 -2,2zM3,13h2v-2L3,11v2zM7,21h2v-2L7,19v2zM3,9h2L5,7L3,7v2zM13,3h-2v2h2L13,3zM19,3v2h2c0,-1.1 -0.9,-2 -2,-2zM5,21v-2L3,19c0,1.1 0.9,2 2,2zM3,17h2v-2L3,15v2zM9,3L7,3v2h2L9,3zM11,21h2v-2h-2v2zM19,13h2v-2h-2v2zM19,21c1.1,0 2,-0.9 2,-2h-2v2zM19,9h2L21,7h-2v2zM19,17h2v-2h-2v2zM15,21h2v-2h-2v2zM15,5h2L17,3h-2v2zM7,17h10L17,7L7,7v10zM9,9h6v6L9,15L9,9z",
    )
}

/** Material "Push pin" icon (extended icons dependency ke bina). */
val PinIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    materialIcon(
        "PushPin",
        "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z",
    )
}

/** Material "Delete forever" icon (extended icons dependency ke bina). */
val DeleteForeverIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    materialIcon(
        "DeleteForever",
        "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM8.46,11.88l1.41,-1.41L12,12.59l2.12,-2.12 1.41,1.41L13.41,14l2.12,2.12 -1.41,1.41L12,15.41l-2.12,2.12 -1.41,-1.41L10.59,14l-2.13,-2.12zM15.5,4l-1,-1h-5l-1,1H5v2h14V4z",
    )
}

/** Material "Wallpaper" icon (extended icons dependency ke bina). */
val WallpaperIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    materialIcon(
        "Wallpaper",
        "M4,4h7V2H4C2.9,2 2,2.9 2,4v7h2V4zM10,13l-4,5h12l-3,-4 -2.03,2.71L10,13zM17,8.5c0,-0.83 -0.67,-1.5 -1.5,-1.5S14,7.67 14,8.5s0.67,1.5 1.5,1.5S17,9.33 17,8.5zM20,2h-7v2h7v7h2V4C22,2.9 21.1,2 20,2zM20,20h-7v2h7c1.1,0 2,-0.9 2,-2v-7h-2V20zM4,13H2v7c0,1.1 0.9,2 2,2h7v-2H4V13z",
    )
}

/** Material "Content copy" icon (extended icons dependency ke bina). */
val CopyIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    materialIcon(
        "ContentCopy",
        "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z",
    )
}

/** Material "Drive file move" icon (folder + arrow; extended icons dependency ke bina). */
val MoveIcon: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    materialIcon(
        "DriveFileMove",
        "M20,6h-8l-2,-2H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zM12,17v-3H8v-2h4V9l4,4 -4,4z",
    )
}

/** Multi-select ke waqt neeche dikhne wala action bar: Share, Favorite, Copy, Move (dono alag; Trash tab me nahi), Trash/Restore, Delete. */
@Composable
fun SelectionActionBar(
    inTrash: Boolean,
    allFavorite: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onTrashOrRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.navigationBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionAction(Icons.Filled.Share, stringResource(R.string.action_share), onShare)
            SelectionAction(
                if (allFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                stringResource(if (allFavorite) R.string.action_unfavorite_short else R.string.action_favorite_short),
                onFavorite,
            )
            // Trash me pade items copy/move nahi hote (pehle Restore karo).
            // Copy aur Move alag buttons: user ko action chunte waqt hi pata ho ki original rahega ya hatega.
            if (!inTrash) {
                SelectionAction(CopyIcon, stringResource(R.string.action_copy_short), onCopy)
                SelectionAction(MoveIcon, stringResource(R.string.action_move_short), onMove)
            }
            SelectionAction(
                if (inTrash) Icons.Filled.Refresh else Icons.Filled.Delete,
                stringResource(if (inTrash) R.string.action_restore_short else R.string.action_trash_short),
                onTrashOrRestore,
            )
            SelectionAction(DeleteForeverIcon, stringResource(R.string.action_delete_short), onDelete, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
