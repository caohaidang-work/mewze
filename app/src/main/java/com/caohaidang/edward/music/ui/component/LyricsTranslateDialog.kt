package com.caohaidang.edward.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.utils.TranslationHelper

@Composable
fun LyricsTranslateDialog(
    translationEnabled: Boolean,
    onTranslationEnabledChange: (Boolean) -> Unit,
    romanizationEnabled: Boolean,
    onRomanizationEnabledChange: (Boolean) -> Unit,
    targetLanguage: String,
    onTargetLanguageChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_translation)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        text = {
            Column {
                SwitchRow(stringResource(R.string.translate_lyrics), translationEnabled, onTranslationEnabledChange)
                SwitchRow(stringResource(R.string.romanize_lyrics), romanizationEnabled, onRomanizationEnabledChange)

                Text(
                    text = stringResource(R.string.translate_to),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(TranslationHelper.languages, key = { it.code }) { language ->
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
