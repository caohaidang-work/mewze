package com.caohaidang.edward.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.caohaidang.edward.music.R

sealed class Screens(
    val route: String,
    @StringRes val titleId: Int = 0,
    @DrawableRes val iconId: Int = 0,

    val showLabel: Boolean = true
) {
    // Icon mèo riêng cho thanh điều hướng (nav_*), không dùng chung với các màn hình khác.
    object Home : Screens("home", R.string.home, R.drawable.nav_home)
    object Search : Screens("search_tab", R.string.search, R.drawable.nav_search, showLabel = false)
    object Library : Screens("library", R.string.library, R.drawable.nav_library, showLabel = false)
    object Account : Screens("account", R.string.account, R.drawable.nav_account)

    object Welcome : Screens("welcome")
    object Login : Screens("login")

    object Songs : Screens("songs")
    object Artists : Screens("artists")
    object Albums : Screens("albums")
    object Playlists : Screens("playlists")

    companion object {
        val MainScreens = listOf(Home, Search, Library, Account)
    }
}
