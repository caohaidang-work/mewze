package com.caohaidang.edward.music.ui.screens

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.spotify.SpotifyAuth
import com.caohaidang.edward.music.spotify.SpotifyAuthState
import com.caohaidang.edward.music.spotify.SpotifyImportResult
import com.caohaidang.edward.music.spotify.SpotifyImportState
import com.caohaidang.edward.music.ui.component.IconButton
import com.caohaidang.edward.music.ui.component.glassCard
import com.caohaidang.edward.music.ui.utils.backToMain
import com.caohaidang.edward.music.viewmodels.SpotifyImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    navController: NavController,
    viewModel: SpotifyImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val library by viewModel.library.collectAsState()
    var configured by remember { mutableStateOf(viewModel.isConfigured) }

    // Load the library as soon as we are signed in (also right after coming back from the browser).
    LaunchedEffect(authState) {
        if (authState is SpotifyAuthState.SignedIn) viewModel.loadLibrary()
    }

    // If the user returns from the browser without finishing, do not leave the button stuck.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // The redirect (if any) is delivered via onNewIntent before/around ON_RESUME,
                // so give it a moment before treating the wait as cancelled.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    viewModel.cancelPendingLogin()
                }, 1500)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
    ) {
        TopAppBar(
            title = { Text("Nhập từ Spotify") },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            }
        )

        when {
            !configured -> ClientIdSetup(
                initial = viewModel.clientId,
                onSave = {
                    viewModel.saveClientId(it)
                    configured = viewModel.isConfigured
                }
            )

            authState !is SpotifyAuthState.SignedIn -> SignInCard(
                waiting = authState is SpotifyAuthState.WaitingForBrowser,
                error = (authState as? SpotifyAuthState.Error)?.message,
                onSignIn = {
                    viewModel.clearAuthError()
                    try {
                        context.startActivity(viewModel.buildLoginIntent())
                    } catch (e: ActivityNotFoundException) {
                        viewModel.cancelPendingLogin()
                        Toast.makeText(context, "Không tìm thấy trình duyệt để đăng nhập", Toast.LENGTH_LONG).show()
                    }
                },
                onChangeClientId = if (viewModel.isClientIdFromBuild) null else ({ configured = false }),
            )

            else -> {
                val account = (authState as SpotifyAuthState.SignedIn).account
                when (val s = importState) {
                    is SpotifyImportState.Running -> ProgressCard(s)
                    is SpotifyImportState.Done -> ResultCard(s.result, onClose = viewModel::dismissResult)
                    is SpotifyImportState.Failed -> FailedCard(s.message, onClose = viewModel::dismissResult)
                    SpotifyImportState.Idle -> LibraryPicker(
                        accountName = account.displayName,
                        viewModel = viewModel,
                        library = library,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientIdSetup(initial: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Thiết lập Spotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "1. Vào developer.spotify.com/dashboard → Create app.\n" +
                "2. Thêm Redirect URI đúng như sau:\n    ${SpotifyAuth.REDIRECT_URI}\n" +
                "3. Tick “Web API”, lưu lại rồi dán Client ID vào bên dưới.\n" +
                "4. App ở Development mode chỉ cho tối đa 5 tài khoản: hãy thêm tài khoản Spotify của bạn trong User Management.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Spotify Client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Lưu") }
    }
}

@Composable
private fun SignInCard(waiting: Boolean, error: String?, onSignIn: () -> Unit, onChangeClientId: (() -> Unit)?) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().glassCard(RoundedCornerShape(28.dp)).padding(20.dp)) {
            Text("Đăng nhập Spotify", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Đăng nhập qua trang chính thức của Spotify (OAuth). App không nhận mật khẩu của bạn, chỉ được " +
                    "quyền đọc Bài hát đã thích và playlist. Sau đó các bài sẽ được tìm trên YouTube Music " +
                    "để phát trong app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onSignIn, enabled = !waiting) {
                if (waiting) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Đang chờ Spotify…")
                } else Text("Đăng nhập bằng Spotify")
            }
            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (onChangeClientId != null) {
            TextButton(onClick = onChangeClientId) { Text("Đổi Client ID") }
        }
    }
}

@Composable
private fun LibraryPicker(
    accountName: String,
    viewModel: SpotifyImportViewModel,
    library: com.caohaidang.edward.music.viewmodels.SpotifyLibraryUi,
) {
    val selectedCount = library.selectedPlaylistIds.size + if (library.importLiked) 1 else 0
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Spotify", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    accountName.ifBlank { "Đã đăng nhập" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = viewModel::signOut,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Đăng xuất Spotify") }
        }

        when {
            library.loading -> Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }

            library.error != null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(library.error, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::loadLibrary) { Text("Thử lại") }
            }

            else -> {
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        PickRow(
                            checked = library.importLiked,
                            title = "Bài hát đã thích",
                            subtitle = "${library.likedCount} bài → Bài hát đã thích của app",
                            onToggle = { viewModel.setImportLiked(!library.importLiked) },
                        )
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Playlist (${library.playlists.size})",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.selectAllPlaylists(true) }) { Text("Chọn hết") }
                            TextButton(onClick = { viewModel.selectAllPlaylists(false) }) { Text("Bỏ chọn") }
                        }
                    }
                    items(library.playlists, key = { it.id }) { pl ->
                        val readable = pl.id !in library.unreadablePlaylistIds
                        PickRow(
                            checked = pl.id in library.selectedPlaylistIds,
                            enabled = readable,
                            title = pl.name,
                            subtitle = if (readable) "${pl.trackCount} bài"
                            else "Playlist bạn chỉ theo dõi — Spotify không cho app đọc",
                            onToggle = { viewModel.togglePlaylist(pl.id) },
                        )
                    }
                }
                Button(
                    onClick = viewModel::startImport,
                    enabled = selectedCount > 0,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) { Text("Nhập vào thư viện") }
            }
        }
    }
}

@Composable
private fun PickRow(checked: Boolean, title: String, subtitle: String, onToggle: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked && enabled, onCheckedChange = { onToggle() }, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgressCard(s: SpotifyImportState.Running) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(s.step, style = MaterialTheme.typography.titleSmall)
        if (s.total > 0) {
            LinearProgressIndicator(progress = { (s.done.toFloat() / s.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("${s.done} / ${s.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            "Bạn có thể thoát màn hình này, việc nhập vẫn tiếp tục khi app còn chạy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultCard(r: SpotifyImportResult, onClose: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Hoàn tất", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("• ${r.likedImported} bài hát đã thích mới")
            Text("• ${r.playlistsImported} playlist (${r.songsInPlaylists} bài mới được thêm)")
            Text("• ${r.unmatched.size} bài không tìm thấy trên YouTube Music")
            r.errors.forEach { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClose) { Text("Xong") }
            if (r.unmatched.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Bài không khớp", style = MaterialTheme.typography.titleSmall)
            }
        }
        items(r.unmatched) { u ->
            Column {
                Text(u.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${u.artists} · ${u.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FailedCard(message: String, onClose: () -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Nhập thất bại", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(message)
        Button(onClick = onClose) { Text("Đóng") }
    }
}
