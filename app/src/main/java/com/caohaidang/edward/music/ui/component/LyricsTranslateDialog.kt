package com.caohaidang.edward.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.utils.TranslationHelper
import com.caohaidang.edward.music.utils.TranslationHelper.AiConfig
import com.caohaidang.edward.music.utils.TranslationHelper.Provider

@Composable
fun LyricsTranslateDialog(
    translationEnabled: Boolean,
    onTranslationEnabledChange: (Boolean) -> Unit,
    romanizationEnabled: Boolean,
    onRomanizationEnabledChange: (Boolean) -> Unit,
    targetLanguage: String,
    onTargetLanguageChange: (String) -> Unit,
    aiConfig: AiConfig,
    onAiConfigChange: (AiConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_translation)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(stringResource(R.string.translate_lyrics), translationEnabled, onTranslationEnabledChange)
                SwitchRow(stringResource(R.string.romanize_lyrics), romanizationEnabled, onRomanizationEnabledChange)

                Text(
                    text = stringResource(R.string.translate_to),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                TranslationHelper.languages.forEach { language ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTargetLanguageChange(language.code)
                                if (!translationEnabled) onTranslationEnabledChange(true)
                            }
                    ) {
                        RadioButton(
                            selected = language.code == targetLanguage,
                            onClick = {
                                onTargetLanguageChange(language.code)
                                if (!translationEnabled) onTranslationEnabledChange(true)
                            }
                        )
                        Text(language.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Text(
                    text = stringResource(R.string.ai_provider),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Provider.entries.forEach { provider ->
                        FilterChip(
                            selected = provider == aiConfig.provider,
                            onClick = {
                                if (provider != aiConfig.provider) {
                                    onAiConfigChange(
                                        aiConfig.copy(
                                            provider = provider,
                                            model = provider.defaultModel
                                        )
                                    )
                                }
                            },
                            label = { Text(provider.label.substringBefore(" (")) }
                        )
                    }
                }

                if (aiConfig.provider == Provider.CUSTOM) {
                    OutlinedTextField(
                        value = aiConfig.customBaseUrl,
                        onValueChange = { onAiConfigChange(aiConfig.copy(customBaseUrl = it)) },
                        label = { Text(stringResource(R.string.ai_endpoint_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = aiConfig.apiKey,
                    onValueChange = { onAiConfigChange(aiConfig.copy(apiKey = it.trim())) },
                    label = { Text(stringResource(R.string.ai_api_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (aiConfig.provider.keyUrl.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_api_key_hint, aiConfig.provider.keyUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { uriHandler.openUri(aiConfig.provider.keyUrl) }
                    )
                }
                OutlinedTextField(
                    value = aiConfig.model,
                    onValueChange = { onAiConfigChange(aiConfig.copy(model = it.trim())) },
                    label = { Text(stringResource(R.string.ai_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (aiConfig.apiKey.isBlank()) {
                    Text(
                        text = stringResource(R.string.ai_key_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.translate_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
