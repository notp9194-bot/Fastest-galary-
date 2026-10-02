package com.fastgallery.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fastgallery.app.data.buildEntries
import com.fastgallery.app.ui.AlbumsGrid
import com.fastgallery.app.ui.CenterMessage
import com.fastgallery.app.ui.GalleryTheme
import com.fastgallery.app.ui.MediaGrid
import com.fastgallery.app.ui.PermissionScreen
import com.fastgallery.app.ui.Viewer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GalleryTheme { GalleryRoot() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryRoot(vm: GalleryViewModel = viewModel()) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    var granted by remember { mutableStateOf(hasMediaAccess(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaAccess(ctx)
        if (granted) vm.load()
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(mediaPermissions())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasMediaAccess(ctx)
        if (granted) vm.load()
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var albumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }

    val inAlbum = tab == 1 && albumId != null
    val albumItems = remember(state.items, albumId) {
        albumId?.let { id -> state.items.filter { it.bucketId == id } } ?: emptyList()
    }
    val albumEntries = remember(albumItems) { buildEntries(albumItems) }
    val currentList = if (inAlbum) albumItems else state.items
    val albumTitle = state.albums.firstOrNull { it.id == albumId }?.name ?: ""

    BackHandler(enabled = viewerIndex >= 0) { viewerIndex = -1 }
    BackHandler(enabled = viewerIndex < 0 && inAlbum) { albumId = null }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (inAlbum) albumTitle else if (tab == 0) "Gallery" else "Albums") },
                    navigationIcon = {
                        if (inAlbum) {
                            IconButton(onClick = { albumId = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0; albumId = null },
                        icon = { Icon(painterResource(R.drawable.ic_photos), null) },
                        label = { Text("Photos") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1; albumId = null },
                        icon = { Icon(painterResource(R.drawable.ic_albums), null) },
                        label = { Text("Albums") },
                    )
                }
            },
        ) { padding ->
            when {
                !granted -> PermissionScreen(padding) { launcher.launch(mediaPermissions()) }
                state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> CenterMessage("Koi photo ya video nahi mila", padding)
                tab == 0 -> MediaGrid(state.entries, padding) { viewerIndex = it }
                inAlbum -> MediaGrid(albumEntries, padding) { viewerIndex = it }
                else -> AlbumsGrid(state.albums, padding) { albumId = it.id }
            }
        }

        if (viewerIndex >= 0 && currentList.isNotEmpty()) {
            Viewer(items = currentList, startIndex = viewerIndex, onClose = { viewerIndex = -1 })
        }
    }
}
