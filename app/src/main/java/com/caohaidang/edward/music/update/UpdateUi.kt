package com.caohaidang.edward.music.update

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.caohaidang.edward.music.BuildConfig
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.AutoCheckUpdateKey
import com.caohaidang.edward.music.constants.LastUpdateCheckKey
import com.caohaidang.edward.music.ui.component.DefaultDialog
import com.caohaidang.edward.music.ui.component.PreferenceEntry
import com.caohaidang.edward.music.ui.component.PreferenceGroupTitle
import com.caohaidang.edward.music.ui.component.SwitchPreference
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private const val AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

private sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Ready(val file: File) : DownloadState
    data object Failed : DownloadState
}

/** Dialog thông báo bản mới: xem ghi chú, tải APK (có tiến độ) rồi mở trình cài đặt. */
@Composable
fun UpdateDialog(info: UpdateChecker.UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun install(file: File) {
        if (UpdateChecker.canInstall(context)) {
            UpdateChecker.install(context, file)
        } else {
            Toast.makeText(context, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            UpdateChecker.openInstallPermissionSettings(context)
        }
    }

    fun startDownload() {
        state = DownloadState.Downloading(0f)
        job = scope.launch {
            try {
                val file = UpdateChecker.download(context, info) { state = DownloadState.Downloading(it) }
                state = DownloadState.Ready(file)
                install(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.w(e, "Update download failed")
                state = DownloadState.Failed
            }
        }
    }

    DefaultDialog(
        onDismiss = onDismiss, // rời dialog -> scope bị huỷ -> dừng tải
        icon = { Icon(painterResource(R.drawable.update), contentDescription = null) },
        title = { Text(stringResource(R.string.update_available_title)) },
        buttons = {
            when (val s = state) {
                is DownloadState.Downloading -> TextButton(onClick = {
                    job?.cancel()
                    state = DownloadState.Idle
                }) { Text(stringResource(R.string.update_cancel)) }

                is DownloadState.Ready -> {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
                    TextButton(onClick = { install(s.file) }) { Text(stringResource(R.string.update_install)) }
                }

                else -> {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
                    TextButton(onClick = ::startDownload) {
                        Text(stringResource(if (s is DownloadState.Failed) R.string.update_retry else R.string.update_download))
                    }
                }
            }
        }
    ) {
        Text(
            text = stringResource(R.string.update_version_line, BuildConfig.VERSION_NAME, info.versionName),
            style = MaterialTheme.typography.titleMedium,
        )
        if (info.apkSize > 0) {
            Text(
                text = "%.1f MB".format(info.apkSize / 1_048_576f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (info.notes.isNotBlank()) {
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.update_whats_new),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(text = info.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
        when (val s = state) {
            is DownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.update_downloading, (s.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            DownloadState.Failed -> Text(
                text = stringResource(R.string.update_download_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )

            else -> Unit
        }
    }
}

/** Đặt 1 lần trong MainActivity: tự kiểm tra (tối đa 12 giờ/lần) rồi hiện dialog nếu có bản mới. */
@Composable
fun AutoUpdatePrompt() {
    if (BuildConfig.DEBUG) return // bản debug có applicationId khác, không cài đè được bản release
    val context = LocalContext.current
    var info by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        if (prefs[AutoCheckUpdateKey] == false) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - (prefs[LastUpdateCheckKey] ?: 0L) < AUTO_CHECK_INTERVAL_MS) return@LaunchedEffect
        try {
            val result = UpdateChecker.check()
            context.dataStore.edit { it[LastUpdateCheckKey] = now }
            info = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Auto update check failed") // im lặng, lần mở sau thử lại
        }
    }

    info?.let { UpdateDialog(it, onDismiss = { info = null }) }
}

/** Nhóm "Cập nhật" trong Settings: nút kiểm tra thủ công + công tắc tự động kiểm tra. */
@Composable
fun UpdateSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (autoCheck, onAutoCheckChange) = rememberPreference(AutoCheckUpdateKey, defaultValue = true)
    var status by remember { mutableStateOf<Int?>(null) } // null = chưa kiểm tra
    var checking by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }

    PreferenceGroupTitle(title = stringResource(R.string.update_title))

    PreferenceEntry(
        title = { Text(stringResource(R.string.update_check)) },
        description = when {
            checking -> stringResource(R.string.update_checking)
            status != null -> stringResource(status!!)
            else -> stringResource(R.string.update_check_current, BuildConfig.VERSION_NAME)
        },
        icon = { Icon(painterResource(R.drawable.update), null) },
        isEnabled = !checking,
        onClick = {
            checking = true
            scope.launch {
                try {
                    val result = UpdateChecker.check()
                    context.dataStore.edit { it[LastUpdateCheckKey] = System.currentTimeMillis() }
                    info = result
                    status = if (result == null) R.string.update_up_to_date else R.string.update_available_title
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    timber.log.Timber.w(e, "Manual update check failed")
                    status = R.string.update_check_failed
                } finally {
                    checking = false
                }
            }
        },
    )

    SwitchPreference(
        title = { Text(stringResource(R.string.update_auto_check)) },
        description = stringResource(R.string.update_auto_check_desc),
        icon = { Icon(painterResource(R.drawable.sync), null) },
        checked = autoCheck,
        onCheckedChange = onAutoCheckChange,
    )

    info?.let { UpdateDialog(it, onDismiss = { info = null }) }
}
