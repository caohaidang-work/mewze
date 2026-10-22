package com.caohaidang.edward.music.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.caohaidang.edward.music.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Spotify profile shown in the UI once the user is signed in. */
data class SpotifyAccount(
    val id: String,
    val displayName: String,
)

sealed interface SpotifyAuthState {
    data object SignedOut : SpotifyAuthState
    data object WaitingForBrowser : SpotifyAuthState
    data class SignedIn(val account: SpotifyAccount) : SpotifyAuthState
    data class Error(val message: String) : SpotifyAuthState
}

class SpotifyAuthException(message: String, val unauthorized: Boolean = false) : Exception(message)

/**
 * "Log in with Spotify" using the Authorization Code flow with PKCE (RFC 7636), the flow Spotify
 * recommends for mobile apps: no client secret is shipped inside the APK.
 *
 *  1. [buildAuthorizeIntent] opens accounts.spotify.com in the browser.
 *  2. Spotify redirects to [REDIRECT_URI] (handled in MainActivity -> [handleRedirect]).
 *  3. The code is exchanged for an access + refresh token, which are kept in app-private storage.
 */
@Singleton
class SpotifyAuth @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()

    private val _state = MutableStateFlow<SpotifyAuthState>(
        if (prefs.getString(KEY_REFRESH, null) != null) {
            SpotifyAuthState.SignedIn(
                SpotifyAccount(
                    id = prefs.getString(KEY_USER_ID, "").orEmpty(),
                    displayName = prefs.getString(KEY_USER_NAME, "").orEmpty(),
                )
            )
        } else SpotifyAuthState.SignedOut
    )
    val state: StateFlow<SpotifyAuthState> = _state.asStateFlow()

    /** Client ID from the build (SPOTIFY_CLIENT_ID) or, failing that, the one typed in the app. */
    var clientId: String
        get() = BuildConfig.SPOTIFY_CLIENT_ID.ifBlank { prefs.getString(KEY_CLIENT_ID, "").orEmpty() }
        set(value) {
            prefs.edit().putString(KEY_CLIENT_ID, value.trim()).apply()
        }

    val isClientIdFromBuild: Boolean get() = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()
    val isConfigured: Boolean get() = clientId.isNotBlank()

    // ---------------------------------------------------------------- step 1: authorize

    /** Intent that opens the Spotify consent page. Verifier + state are persisted (the process may die meanwhile). */
    fun buildAuthorizeIntent(): Intent {
        check(isConfigured) { "Chưa có Spotify Client ID" }
        val verifier = randomUrlSafe(64)
        val oauthState = randomUrlSafe(16)
        prefs.edit()
            .putString(KEY_VERIFIER, verifier)
            .putString(KEY_OAUTH_STATE, oauthState)
            .apply()

        val uri = Uri.Builder()
            .scheme("https").authority("accounts.spotify.com").path("/authorize")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", oauthState)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challengeFor(verifier))
            .build()
        _state.value = SpotifyAuthState.WaitingForBrowser
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Called when the user comes back from the browser without finishing (so the UI is not stuck). */
    fun cancelPending() {
        if (!exchanging && _state.value is SpotifyAuthState.WaitingForBrowser) _state.value = signedInOrOut()
    }

    /** True while the authorization code is being exchanged, so a resume does not cancel the login. */
    @Volatile
    private var exchanging = false

    // ---------------------------------------------------------------- step 2: redirect

    fun isRedirect(uri: Uri?): Boolean =
        uri != null && uri.scheme == REDIRECT_SCHEME && uri.host == REDIRECT_HOST

    suspend fun handleRedirect(uri: Uri) {
        if (!isRedirect(uri)) return
        exchanging = true
        try {
            processRedirect(uri)
        } finally {
            exchanging = false
        }
    }

    private suspend fun processRedirect(uri: Uri) {
        val expectedState = prefs.getString(KEY_OAUTH_STATE, null)
        val verifier = prefs.getString(KEY_VERIFIER, null)
        prefs.edit().remove(KEY_OAUTH_STATE).remove(KEY_VERIFIER).apply()
        // Nothing was pending: a stale/replayed redirect, ignore it silently.
        if (expectedState == null && verifier == null) return

        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        when {
            error != null -> _state.value = SpotifyAuthState.Error(
                if (error == "access_denied") "Bạn đã từ chối cấp quyền Spotify" else "Spotify báo lỗi: $error"
            )
            expectedState == null || verifier == null || uri.getQueryParameter("state") != expectedState ->
                _state.value = SpotifyAuthState.Error("Phiên đăng nhập không hợp lệ, hãy thử lại")
            code == null -> _state.value = SpotifyAuthState.Error("Spotify không trả về mã xác thực")
            else -> try {
                exchangeCode(code, verifier)
                val me = SpotifyApi(this).me()
                prefs.edit().putString(KEY_USER_ID, me.id).putString(KEY_USER_NAME, me.displayName).apply()
                _state.value = SpotifyAuthState.SignedIn(me)
            } catch (e: Exception) {
                _state.value = SpotifyAuthState.Error(e.message ?: "Đăng nhập Spotify thất bại")
            }
        }
    }

    private suspend fun exchangeCode(code: String, verifier: String) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .add("client_id", clientId)
            .add("code_verifier", verifier)
            .build()
        saveTokens(requestToken(form), previousRefresh = null)
    }

    // ---------------------------------------------------------------- tokens

    /** A valid access token, refreshed when it is about to expire. */
    suspend fun accessToken(forceRefresh: Boolean = false): String = refreshMutex.withLock {
        val token = prefs.getString(KEY_ACCESS, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (!forceRefresh && token != null && System.currentTimeMillis() < expiresAt - 60_000) return token

        val refresh = prefs.getString(KEY_REFRESH, null)
            ?: throw SpotifyAuthException("Chưa đăng nhập Spotify", unauthorized = true)
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()
        try {
            saveTokens(requestToken(form), previousRefresh = refresh)
        } catch (e: SpotifyAuthException) {
            if (e.unauthorized) signOut() // refresh token revoked / expired
            throw e
        }
        prefs.getString(KEY_ACCESS, null)!!
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES_AT)
            .remove(KEY_USER_ID).remove(KEY_USER_NAME)
            .remove(KEY_VERIFIER).remove(KEY_OAUTH_STATE)
            .apply()
        _state.value = SpotifyAuthState.SignedOut
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String,
        val expires_in: Long = 3600,
        val refresh_token: String? = null,
    )

    private suspend fun requestToken(form: FormBody): TokenResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(TOKEN_URL).post(form).build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // Only a revoked/expired grant means "sign in again"; other 400s must not log the user out.
                val invalid = resp.code == 401 || (resp.code == 400 && "invalid_grant" in body)
                throw SpotifyAuthException(
                    if (invalid) "Phiên Spotify đã hết hạn hoặc bị thu hồi, hãy đăng nhập lại"
                    else "Spotify lỗi ${resp.code}",
                    unauthorized = invalid,
                )
            }
            json.decodeFromString(TokenResponse.serializer(), body)
        }
    }

    private fun saveTokens(t: TokenResponse, previousRefresh: String?) {
        prefs.edit()
            .putString(KEY_ACCESS, t.access_token)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + t.expires_in * 1000)
            // Spotify may or may not rotate the refresh token: keep the old one if no new one is sent.
            .putString(KEY_REFRESH, t.refresh_token ?: previousRefresh)
            .apply()
    }

    private fun signedInOrOut(): SpotifyAuthState =
        if (prefs.getString(KEY_REFRESH, null) != null) {
            SpotifyAuthState.SignedIn(
                SpotifyAccount(
                    prefs.getString(KEY_USER_ID, "").orEmpty(),
                    prefs.getString(KEY_USER_NAME, "").orEmpty(),
                )
            )
        } else SpotifyAuthState.SignedOut

    fun clearError() {
        if (_state.value is SpotifyAuthState.Error) _state.value = signedInOrOut()
    }

    companion object {
        const val REDIRECT_SCHEME = "mewze"
        const val REDIRECT_HOST = "spotify-callback"
        const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"
        const val SCOPES = "user-library-read playlist-read-private playlist-read-collaborative"

        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val PREFS = "spotify_auth"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_VERIFIER = "pkce_verifier"
        private const val KEY_OAUTH_STATE = "oauth_state"

        private fun randomUrlSafe(bytes: Int): String {
            val buf = ByteArray(bytes)
            SecureRandom().nextBytes(buf)
            return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }

        internal fun challengeFor(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}
