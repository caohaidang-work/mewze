package com.caohaidang.edward.music.ui.screens

import android.text.format.DateUtils
import android.webkit.CookieManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.caohaidang.edward.music.ui.component.glassCard
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.caohaidang.edward.music.utils.ProfileAvatarStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.utils.parseCookieString
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.AutoPlayKey
import com.caohaidang.edward.music.constants.ProfileAvatarKey
import com.caohaidang.edward.music.constants.ProfileNameKey
import com.caohaidang.edward.music.constants.HideExplicitKey
import com.caohaidang.edward.music.playback.queues.PersonalMixQueue
import com.caohaidang.edward.music.constants.AccountChannelHandleKey
import com.caohaidang.edward.music.constants.AccountEmailKey
import com.caohaidang.edward.music.constants.AccountNameKey
import com.caohaidang.edward.music.constants.InnerTubeCookieKey
import com.caohaidang.edward.music.sync.SyncState
import com.caohaidang.edward.music.utils.rememberPreference
import com.caohaidang.edward.music.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val accountName by rememberPreference(AccountNameKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

    val accountEmail by rememberPreference(AccountEmailKey, "")
    val profileName by rememberPreference(ProfileNameKey, "")
    val profileAvatar by rememberPreference(ProfileAvatarKey, "")
    var showEditProfile by remember { mutableStateOf(false) }
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.autoSync()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.failureEvents.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = "Đồng bộ lỗi: $message",
                actionLabel = "Thử lại",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.retrySync()
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            googleName = accountName.takeIf { isLoggedIn && it.isNotBlank() },
            onDismiss = { showEditProfile = false }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .glassCard(CircleShape)
                    .clip(CircleShape)
                    .clickable { showEditProfile = true }
            ) {
                if (profileAvatar.isNotBlank()) {
                    AsyncImage(
                        model = profileAvatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable { showEditProfile = true }
            ) {
                Text(
                    text = profileName.ifBlank {
                        if (isLoggedIn) accountName.ifBlank { stringResource(R.string.account_connected) }
                        else stringResource(R.string.account_not_signed_in)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLoggedIn) {
                    SyncStatusLine(
                        syncState = syncState,
                        lastSyncAt = lastSyncAt,
                        email = accountEmail,
                        onRetry = viewModel::retrySync
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .glassCard(CircleShape)
                    .clickable { navController.navigate("settings") }
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        WrappedSection(navController = navController)

        Spacer(Modifier.height(16.dp))
        PersonalMixCard(modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(16.dp))
        AccountSection(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))
        SpotifyCard(navController, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
    )
    }
}

@Composable
private fun SyncStatusLine(syncState: SyncState, lastSyncAt: Long, email: String, onRetry: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (syncState) {
        is SyncState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text("Đang đồng bộ…", style = MaterialTheme.typography.bodySmall, color = muted)
        }
        is SyncState.Failed -> Text(
            "Đồng bộ lỗi · Nhấn để thử lại",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onRetry)
        )
        is SyncState.Done -> if (syncState.summary.warnings.isNotEmpty()) {
            Text(
                "Đồng bộ chưa trọn vẹn: " + syncState.summary.warnings.first() + " · Nhấn để thử lại",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onRetry)
            )
        } else SyncedText(lastSyncAt, email, muted)
        else -> SyncedText(lastSyncAt, email, muted)
    }
}

@Composable
private fun SyncedText(lastSyncAt: Long, email: String, muted: androidx.compose.ui.graphics.Color) {
    run {
        Text(
            text = when {
                lastSyncAt > 0L -> "Đã đồng bộ " + DateUtils.getRelativeTimeSpanString(
                    lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                )
                email.isNotBlank() -> email
                else -> "Đồng bộ tự động"
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PersonalMixCard(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    var autoPlay by rememberPreference(AutoPlayKey, true)
    var hideExplicit by rememberPreference(HideExplicitKey, false)

    Box(modifier = modifier.fillMaxWidth().glassCard(RoundedCornerShape(28.dp))) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.personal_mix), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.personal_mix_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    playerConnection.playQueue(
                        PersonalMixQueue(database, hideExplicit, title = null)
                    )
                }
            ) {
                Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.personal_mix_play))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.autoplay), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.autoplay_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoPlay, onCheckedChange = { autoPlay = it })
            }
        }
    }
}

@Composable
private fun AccountSection(
    navController: NavController,
    viewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var profileName by rememberPreference(ProfileNameKey, "")
    var profileAvatar by rememberPreference(ProfileAvatarKey, "")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }
    val playerConnection = LocalPlayerConnection.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showUnsyncedWarning by remember { mutableStateOf(false) }
    var isSyncingBeforeLogout by remember { mutableStateOf(false) }

    // Signs out and wipes everything that belongs to the account, so nothing leaks to the next one.
    val performLogout: () -> Unit = {
        showLogoutConfirm = false
        showUnsyncedWarning = false
        isSyncingBeforeLogout = false
        innerTubeCookie = ""
        accountName = ""
        accountEmail = ""
        accountChannelHandle = ""
        profileName = ""
        profileAvatar = ""
        YouTube.cookie = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        playerConnection?.player?.run {
            stop()
            clearMediaItems()
        }
        scope.launch(Dispatchers.IO) {
            ProfileAvatarStorage.prune(context, null)
            context.filesDir.resolve("persistent_queue.data").delete()
        }
        viewModel.onLoggedOut()
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isSyncingBeforeLogout) showLogoutConfirm = false },
            title = { Text("Đăng xuất") },
            text = {
                if (isSyncingBeforeLogout) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Đang đồng bộ lần cuối lên YouTube Music…")
                    }
                } else {
                    Text(
                        "Bạn có chắc chắn muốn đăng xuất không? Thư viện, lịch sử nghe và lịch sử tìm kiếm " +
                            "trên máy sẽ bị xoá. Đăng nhập lại để đồng bộ từ YouTube Music."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSyncingBeforeLogout,
                    onClick = {
                        isSyncingBeforeLogout = true
                        viewModel.syncBeforeLogout { ok ->
                            if (ok) {
                                performLogout()
                            } else {
                                isSyncingBeforeLogout = false
                                showLogoutConfirm = false
                                showUnsyncedWarning = true
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Đăng xuất") }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSyncingBeforeLogout,
                    onClick = { showLogoutConfirm = false }
                ) { Text("Hủy") }
            }
        )
    }

    if (showUnsyncedWarning) {
        AlertDialog(
            onDismissRequest = { showUnsyncedWarning = false },
            title = { Text("Chưa đồng bộ xong") },
            text = {
                Text(
                    "Không thể đồng bộ hết thay đổi mới nhất lên YouTube Music (có thể do mất mạng). " +
                        "Nếu vẫn đăng xuất, playlist và thay đổi chưa đồng bộ sẽ bị mất."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = performLogout,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Vẫn đăng xuất") }
            },
            dismissButton = {
                TextButton(onClick = { showUnsyncedWarning = false }) { Text("Hủy") }
            }
        )
    }

    Box(modifier = modifier.fillMaxWidth().glassCard(RoundedCornerShape(28.dp))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                if (isLoggedIn) {
                    Text("YouTube Music", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = accountChannelHandle.ifBlank { accountEmail }.ifBlank { "Thư viện được đồng bộ tự động" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text("YouTube Music", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Đăng nhập để tự động đồng bộ bài hát đã thích, playlist, album và nghệ sĩ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isLoggedIn) {
                TextButton(
                    onClick = { showLogoutConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Đăng xuất") }
            } else {
                Button(onClick = { navController.navigate(Screens.Login.route) }) { Text("Đăng nhập") }
            }
        }
    }
}

@Composable
private fun SpotifyCard(navController: NavController, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().glassCard(RoundedCornerShape(28.dp))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Spotify", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Đăng nhập Spotify để nhập bài hát đã thích và playlist vào thư viện",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { navController.navigate("spotify_import") }) { Text("Nhập") }
        }
    }
}
