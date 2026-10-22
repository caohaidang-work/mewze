package com.caohaidang.edward.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import coil.compose.AsyncImage
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.ProfileAvatarKey
import com.caohaidang.edward.music.constants.ProfileNameKey
import com.caohaidang.edward.music.ui.component.DefaultDialog
import com.caohaidang.edward.music.utils.ProfileAvatarStorage
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_NAME_LENGTH = 30

/**
 * Edits the in-app profile (display name + avatar). It is stored on this device only and does not
 * touch the Google / YouTube account.
 */
@Composable
fun EditProfileDialog(
    googleName: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(context.dataStore[ProfileNameKey].orEmpty()) }
    var avatar by remember { mutableStateOf(context.dataStore[ProfileAvatarKey].orEmpty()) }
    var avatarFailed by remember { mutableStateOf(false) }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = ProfileAvatarStorage.save(context, uri)
            if (path != null) {
                avatar = path
                avatarFailed = false
            } else {
                avatarFailed = true
            }
        }
    }

    val savedAvatar = remember { context.dataStore[ProfileAvatarKey].orEmpty() }
    val cancel: () -> Unit = {
        // Throw away any avatar picked in this dialog but not confirmed.
        scope.launch(Dispatchers.IO) { ProfileAvatarStorage.prune(context, savedAvatar) }
        onDismiss()
    }

    DefaultDialog(
        onDismiss = cancel,
        title = { Text("Hồ sơ của bạn") },
        buttons = {
            TextButton(onClick = cancel) { Text(context.getString(android.R.string.cancel)) }
            TextButton(
                onClick = {
                    scope.launch {
                        context.dataStore.edit {
                            it[ProfileNameKey] = name.trim()
                            it[ProfileAvatarKey] = avatar
                        }
                        withContext(Dispatchers.IO) { ProfileAvatarStorage.prune(context, avatar) }
                        onDismiss()
                    }
                }
            ) { Text(context.getString(android.R.string.ok)) }
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .clickable {
                    pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
        ) {
            if (avatar.isNotBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(
            onClick = {
                pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        ) { Text("Chọn ảnh đại diện") }
        if (avatar.isNotBlank()) {
            TextButton(onClick = { avatar = "" }) { Text("Xoá ảnh đại diện") }
        }
        if (avatarFailed) {
            Text(
                "Không thể dùng ảnh này",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= MAX_NAME_LENGTH) name = it },
            singleLine = true,
            label = { Text("Tên hiển thị") },
            placeholder = { Text(googleName ?: "Chưa đặt tên") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Hồ sơ này chỉ dùng trong Mewze và lưu trên máy, không thay đổi tài khoản Google.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
