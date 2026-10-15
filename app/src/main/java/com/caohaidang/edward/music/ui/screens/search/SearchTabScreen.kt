package com.caohaidang.edward.music.ui.screens.search

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import com.caohaidang.edward.music.LocalDatabase
import com.caohaidang.edward.music.LocalPlayerAwareWindowInsets
import com.caohaidang.edward.music.R
import com.caohaidang.edward.music.constants.PauseSearchHistoryKey
import com.caohaidang.edward.music.constants.SearchSource
import com.caohaidang.edward.music.constants.SearchSourceKey
import com.caohaidang.edward.music.db.entities.SearchHistory
import com.caohaidang.edward.music.ui.component.ChipsRow
import com.caohaidang.edward.music.ui.component.glassCard
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get
import com.caohaidang.edward.music.utils.rememberEnumPreference
import com.caohaidang.edward.music.utils.urlEncode

@Composable
fun SearchTabScreen(
    navController: NavController,
    autoFocus: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

    val history by remember(database) { database.searchHistory() }
        .collectAsState(initial = emptyList())

    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }

    val doSearch: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            focusManager.clearFocus()
            navController.navigate("search/${text.urlEncode()}")
            if (context.dataStore[PauseSearchHistoryKey] != true) {
                database.query {
                    insert(SearchHistory(query = text))
                }
            }
        }
    }

    val density = LocalDensity.current
    val imeBottom = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val playerBottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (imeBottom > playerBottom) imeBottom else playerBottom

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        SearchSourceSwitcher(
            options = listOf(
                SearchSource.ONLINE to "YouTube Music",
                SearchSource.LOCAL to "Thư viện",
            ),
            selected = searchSource,
            onSelect = { searchSource = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Crossfade(targetState = searchSource, label = "searchSource") { source ->
                when (source) {
                    SearchSource.ONLINE -> OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = { query = it },
                        navController = navController,
                        onSearch = doSearch,
                        onDismiss = { focusManager.clearFocus() }
                    )

                    SearchSource.LOCAL -> LocalSearchScreen(
                        query = query.text,
                        navController = navController,
                        onDismiss = { focusManager.clearFocus() }
                    )
                }
            }
        }

        // Thanh tìm kiếm nằm dưới cùng, ngay trên bàn phím
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 8.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        when (searchSource) {
                            SearchSource.ONLINE -> doSearch(query.text)
                            SearchSource.LOCAL -> focusManager.clearFocus()
                        }
                    }
                ),
                decorationBox = { inner ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.text.isEmpty()) {
                                Text(
                                    text = when (searchSource) {
                                        SearchSource.ONLINE -> "Tìm bài hát, album, nghệ sĩ…"
                                        SearchSource.LOCAL -> "Tìm trong thư viện của bạn…"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .focusRequester(focusRequester)
            )

            Spacer(Modifier.width(10.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        if (query.text.isNotEmpty()) query = TextFieldValue("")
                        else focusManager.clearFocus()
                    }
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Xóa",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Công tắc dạng viên thuốc (segmented control) để đổi nguồn tìm kiếm. */
@Composable
private fun SearchSourceSwitcher(
    options: List<Pair<SearchSource, String>>,
    selected: SearchSource,
    onSelect: (SearchSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(3.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onSelect(value) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
