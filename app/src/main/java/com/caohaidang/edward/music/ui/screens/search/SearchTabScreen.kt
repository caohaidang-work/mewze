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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = {
                Text(
                    when (searchSource) {
                        SearchSource.ONLINE -> "Tìm bài hát, album, nghệ sĩ…"
                        SearchSource.LOCAL -> "Tìm trong thư viện của bạn…"
                    }
                )
            },
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.search), contentDescription = null)
            },
            trailingIcon = {
                if (query.text.isNotEmpty()) {
                    IconButton(onClick = { query = TextFieldValue("") }) {
                        Icon(painter = painterResource(R.drawable.close), contentDescription = null)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    when (searchSource) {
                        SearchSource.ONLINE -> doSearch(query.text)
                        SearchSource.LOCAL -> focusManager.clearFocus()
                    }
                }
            ),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                .glassCard(RoundedCornerShape(28.dp), tintAlpha = 0.60f)
                .focusRequester(focusRequester)
        )

        ChipsRow(
            chips = listOf(
                SearchSource.ONLINE to "YouTube Music",
                SearchSource.LOCAL to "Thư viện",
            ),
            currentValue = searchSource,
            onValueUpdate = { searchSource = it },
            containerColor = MaterialTheme.colorScheme.surface,
        )

        if (query.text.isEmpty() && searchSource == SearchSource.ONLINE && history.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp)
            ) {
                Text(
                    text = "Tìm kiếm gần đây",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { database.query { clearSearchHistory() } }) {
                    Text("Xóa tất cả")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding())
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
    }
}
