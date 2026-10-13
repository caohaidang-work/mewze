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
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncAt by viewModel.lastSyncAt.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) viewModel.autoSync()
    }

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
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isLoggedIn) accountName.ifBlank { stringResource(R.string.account_connected) }
                    else stringResource(R.string.account_not_signed_in),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLoggedIn) {
                    SyncStatusLine(syncState = syncState, lastSyncAt = lastSyncAt, email = accountEmail)
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
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SyncStatusLine(syncState: SyncState, lastSyncAt: Long, email: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (syncState) {
        is SyncState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text("Đang đồng bộ…", style = MaterialTheme.typography.bodySmall, color = muted)
        }
        is SyncState.Failed -> Text(
            "Đồng bộ thất bại, sẽ thử lại sau",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        else -> Text(
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
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

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
                    onClick = {
                        innerTubeCookie = ""
                        accountName = ""
                        accountEmail = ""
                        accountChannelHandle = ""
                        YouTube.cookie = null
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        viewModel.onLoggedOut()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Đăng xuất") }
            } else {
                Button(onClick = { navController.navigate(Screens.Login.route) }) { Text("Đăng nhập") }
            }
        }
    }
}
