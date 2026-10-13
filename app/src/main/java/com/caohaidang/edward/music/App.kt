package com.caohaidang.edward.music

import android.app.Application
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.caohaidang.edward.innertube.YouTube
import com.caohaidang.edward.innertube.models.YouTubeLocale
import com.caohaidang.edward.kugou.KuGou
import com.caohaidang.edward.music.constants.ContentCountryKey
import com.caohaidang.edward.music.constants.ContentLanguageKey
import com.caohaidang.edward.music.constants.CountryCodeToName
import com.caohaidang.edward.music.constants.InnerTubeCookieKey
import com.caohaidang.edward.music.constants.LanguageCodeToName
import com.caohaidang.edward.music.constants.MaxImageCacheSizeKey
import com.caohaidang.edward.music.constants.ProxyEnabledKey
import com.caohaidang.edward.music.constants.ProxyTypeKey
import com.caohaidang.edward.music.constants.ProxyUrlKey
import com.caohaidang.edward.music.constants.SYSTEM_DEFAULT
import com.caohaidang.edward.music.constants.UseLoginForBrowse
import com.caohaidang.edward.music.constants.VisitorDataKey
import com.caohaidang.edward.music.extensions.toEnum
import com.caohaidang.edward.music.extensions.toInetSocketAddress
import com.caohaidang.edward.music.utils.dataStore
import com.caohaidang.edward.music.utils.get
import com.caohaidang.edward.music.utils.reportException
import com.caohaidang.edward.innertube.utils.parseCookieString
import com.caohaidang.edward.music.sync.LibrarySyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.Proxy
import java.util.Locale

@HiltAndroidApp
class App : Application(), ImageLoaderFactory {
    @Inject lateinit var syncManager: LibrarySyncManager
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse proxy url.", LENGTH_SHORT).show()
                reportException(e)
            }
        }

        if (dataStore[UseLoginForBrowse] == true) {
            YouTube.useLoginForBrowse = true
        }

        GlobalScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" }
                        ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        } ?: YouTube.DEFAULT_VISITOR_DATA
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    YouTube.cookie = cookie

                    if (cookie != null && "SAPISID" in parseCookieString(cookie)) {
                        GlobalScope.launch { syncManager.syncAll() }
                    }
                }
        }
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .crossfade(true)
        .respectCacheHeaders(false)
        .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        .diskCache(
            DiskCache.Builder()
                .directory(cacheDir.resolve("coil"))
                .maxSizeBytes((dataStore[MaxImageCacheSizeKey] ?: 512) * 1024 * 1024L)
                .build()
        )
        .build()
}
