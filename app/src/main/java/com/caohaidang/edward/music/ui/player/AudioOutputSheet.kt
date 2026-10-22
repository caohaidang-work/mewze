package com.caohaidang.edward.music.ui.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caohaidang.edward.music.LocalPlayerConnection
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.ui.component.LocalMenuState
import com.caohaidang.edward.music.ui.component.MenuState

/** Màu nhấn khi đang phát ra thiết bị ngoài (tai nghe, loa Bluetooth...). */
private val ExternalOutputColor = Color(0xFF6EE7A0)

enum class OutputKind { PHONE, BLUETOOTH, WIRED, USB, HDMI, OTHER }

data class AudioOutput(
    val info: AudioDeviceInfo,
    val name: String,
    val kind: OutputKind,
) {
    val id: Int get() = info.id
    val isExternal: Boolean get() = kind != OutputKind.PHONE
}

data class AudioOutputs(
    val devices: List<AudioOutput>,
    val current: AudioOutput?,
)

/** Thiết bị đầu ra người dùng chọn thủ công trong app (null = để hệ thống tự chọn). */
object AudioOutputPrefs {
    var preferredId by mutableStateOf<Int?>(null)
}

private fun AudioDeviceInfo.kind(): OutputKind? = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.PHONE
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    26 /* TYPE_BLE_HEADSET */, 27 /* TYPE_BLE_SPEAKER */,
    AudioDeviceInfo.TYPE_HEARING_AID -> OutputKind.BLUETOOTH
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_LINE_ANALOG -> OutputKind.WIRED
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_DOCK -> OutputKind.USB
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC -> OutputKind.HDMI
    else -> null // earpiece, SCO (trùng A2DP), telephony... không dùng để phát nhạc
}

private fun AudioDeviceInfo.displayName(kind: OutputKind): String {
    val product = productName?.toString()?.trim().orEmpty()
    return when (kind) {
        OutputKind.PHONE -> "Điện thoại này"
        OutputKind.BLUETOOTH -> product.ifBlank { "Bluetooth" }
        OutputKind.WIRED -> "Tai nghe có dây"
        OutputKind.USB -> product.ifBlank { "Thiết bị USB" }
        OutputKind.HDMI -> product.ifBlank { "HDMI" }
        OutputKind.OTHER -> product.ifBlank { "Thiết bị âm thanh" }
    }
}

private fun readOutputs(audioManager: AudioManager): AudioOutputs {
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .mapNotNull { info -> info.kind()?.let { AudioOutput(info, info.displayName(it), it) } }
        .distinctBy { it.id }
        .sortedBy { if (it.kind == OutputKind.PHONE) 1 else 0 } // thiết bị ngoài lên trước

    val preferred = AudioOutputPrefs.preferredId?.let { id -> devices.firstOrNull { it.id == id } }

    val routed = if (preferred == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioManager.getAudioDevicesForAttributes(attrs)
            .firstNotNullOfOrNull { routedInfo -> devices.firstOrNull { it.id == routedInfo.id } }
    } else null

    // Không biết chắc hệ thống đang chọn gì: ưu tiên thiết bị ngoài, cuối cùng mới tới loa máy.
    val current = preferred ?: routed ?: devices.firstOrNull { it.isExternal } ?: devices.firstOrNull()
    return AudioOutputs(devices, current)
}

/** Danh sách thiết bị đầu ra + thiết bị đang dùng, tự cập nhật khi cắm/rút hoặc kết nối/ngắt Bluetooth. */
@Composable
fun rememberAudioOutputs(): AudioOutputs {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) { tick++ }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val removed = removedDevices?.map { it.id }.orEmpty()
                if (AudioOutputPrefs.preferredId in removed) AudioOutputPrefs.preferredId = null
                tick++
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    val preferredId = AudioOutputPrefs.preferredId
    return remember(tick, preferredId) { readOutputs(audioManager) }
}

private fun OutputKind.iconRes() = when (this) {
    OutputKind.PHONE -> R.drawable.audio_smartphone
    OutputKind.BLUETOOTH, OutputKind.WIRED, OutputKind.USB -> R.drawable.audio_headphones
    OutputKind.HDMI, OutputKind.OTHER -> R.drawable.audio_speaker
}

/** Nền kính tối của popup chọn thiết bị. */
private val PopupBackground = Color(0xFF1E211E)

/**
 * Nút thiết bị ở thanh dưới cùng của player: icon + dòng chữ "Điện thoại → Tai nghe".
 * Bấm để mở popup kính mờ liệt kê thiết bị đầu ra, thiết bị đang dùng có dấu ✓.
 */
@Composable
fun AudioOutputButton(modifier: Modifier = Modifier) {
    val outputs = rememberAudioOutputs()
    val current = outputs.current ?: return
    var expanded by remember { mutableStateOf(false) }
    val tint = if (current.isExternal) ExternalOutputColor else Color.White.copy(alpha = 0.65f)
    val caption = if (current.isExternal) "Điện thoại → ${current.name}" else "Điện thoại"

    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp)
        ) {
            // Khung icon cao 40dp, trùng với nút lyrics/danh sách chờ để 3 icon ngang hàng
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(current.kind.iconRes()),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (expanded) {
            AudioOutputPopup(outputs = outputs, onDismiss = { expanded = false })
        }
    }
}

/** Vị trí popup: căn giữa theo nút, nằm ngay phía trên nút và không tràn ra ngoài màn hình. */
private class AboveAnchorPositionProvider(private val marginPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(marginPx, (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx))
        val y = (anchorBounds.top - popupContentSize.height - marginPx).coerceAtLeast(marginPx)
        return IntOffset(x, y)
    }
}

@Composable
private fun AudioOutputPopup(outputs: AudioOutputs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata = playerConnection?.mediaMetadata?.collectAsState()?.value
    val density = LocalDensity.current
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    Popup(
        popupPositionProvider = remember(density) { AboveAnchorPositionProvider(with(density) { 12.dp.roundToPx() }) },
        onDismissRequest = onDismiss,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.85f,
                transformOrigin = TransformOrigin(0.5f, 1f),
                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
            ),
            exit = fadeOut(tween(120)),
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .shadow(24.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(PopupBackground.copy(alpha = 0.88f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                    .padding(14.dp)
            ) {
                if (mediaMetadata != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = mediaMetadata.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = mediaMetadata.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mediaMetadata.artists.joinToString { it.name },
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                outputs.devices.forEach { device ->
                    val selected = device.id == outputs.current?.id
                    val bg by animateColorAsState(
                        if (selected) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.1f),
                        label = "deviceBg"
                    )
                    val fg = if (selected) Color(0xFF111411) else Color.White
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(bg)
                            .clickable {
                                val ok = playerConnection?.player?.let { p ->
                                    runCatching { p.setPreferredAudioDevice(device.info) }.isSuccess
                                } == true
                                if (ok) AudioOutputPrefs.preferredId = device.id
                                onDismiss()
                            }
                            .padding(horizontal = 18.dp)
                    ) {
                        Icon(
                            painter = painterResource(device.kind.iconRes()),
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = device.name,
                            color = fg,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = fg,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Loa mạng / Cast / thiết bị chưa ghép nối: dùng bộ chuyển đầu ra của hệ thống.
                Text(
                    text = "Thiết bị khác…",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { if (openSystemOutputSwitcher(context)) onDismiss() }
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

/** Mở bộ chuyển đầu ra của hệ thống (chọn Bluetooth / Cast / loa...). Trả về true nếu mở được. */
private fun openSystemOutputSwitcher(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val shown = runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
        if (shown) return true
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val opened = runCatching {
            context.startActivity(
                Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                    .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (opened) return true
    }
    return runCatching {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
