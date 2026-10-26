<div align="center">

# 🎧 Mewze

**A YouTube Music client for Android**

Kotlin · Jetpack Compose · Material 3 · Media3

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-GPL--3.0-blue)

</div>

> **Credits & license.** Mewze is a derivative work of
> [InnerTune](https://github.com/z-huang/InnerTune) by **Zion Huang** and its contributors,
> released under the **GNU GPL v3.0**. Mewze is also released under **GPL-3.0** (see [`LICENSE`](LICENSE)),
> and its source code must remain available whenever it is distributed. See [`NOTICE.md`](NOTICE.md) for details.

---

## ✨ Features

| | |
|---|---|
| 🏠 **Glass-style home** | YouTube Music-style suggestions and a new-releases section based on your listening habits |
| 🔄 **YouTube Music sync** | Liked songs, playlists, albums and artists sync automatically after you sign in |
| 📓 **Wrapped** | Weekly / monthly / yearly recap in a school-notebook style, shareable to stories |
| 🎶 **Personal mix & autoplay** | Queues generated from your listening history |
| 🖼️ **Custom playlist covers** | Pick a cover from your photo gallery |
| 📝 **Lyrics** | LrcLib, KuGou and Musixmatch; share lyrics as an image |
| 🎨 **Dynamic color & light/dark mode** | Palette extracted from the current track |

## 🧩 Modules

| Module | Role |
|---|---|
| `:app` | UI (Compose), Hilt, Room, playback service (Media3), offline downloads |
| `:innertube` | YouTube Music API client (search, home, album, artist, playlist, queue) |
| `:lrclib`, `:kugou` | Lyrics sources |
| `:material-color-utilities` | Dynamic color palette (Apache-2.0, Google) |

## 🔐 YouTube account sync

Sign in on the **Profile** page (Google WebView). The app keeps the session cookie and syncs automatically
whenever you open that page (at most once every 15 minutes).

| Data | Direction |
|---|---|
| Liked songs | Two-way (merged against the previous sync, so neither side overwrites the other) |
| Playlists | YouTube → device (local copies are overwritten; playlists created on the device are kept) |
| Albums, artists | YouTube → device |

> [!WARNING]
> The cookie is equivalent to a session password for your Google account. It is stored in the app's DataStore
> and excluded from cloud backup. **Never share the app's data or backups with anyone.**

## 🛠️ Build

Requirements: recent Android Studio, JDK 17, Android SDK 35.

```bash
./gradlew :app:assembleDebug
```

Never commit `local.properties`, keystores, or any other secret.

## ⚠️ Disclaimer

The app accesses YouTube Music through an unofficial API. This may violate YouTube's terms of service and
may stop working at any time. Use at your own risk.

## 📄 License

[GNU General Public License v3.0](LICENSE). Mewze is not affiliated with Google, YouTube, or the InnerTune authors.

## 🔄 Cập nhật trong ứng dụng

Mewze kiểm tra bản mới từ [GitHub Releases](../../releases) (Cài đặt → Cập nhật, hoặc tự động khi mở app, tối đa 12 giờ/lần),
tải APK, kiểm tra SHA-256 rồi mở trình cài đặt của Android.

**Phát hành bản mới (maintainer):**
1. Tăng `versionCode` / `versionName` trong `app/build.gradle.kts`.
2. `git tag v<versionName> && git push origin v<versionName>`: workflow *Release APK* sẽ build, ký và tạo release.

Cần các GitHub Secrets: `RELEASE_KEYSTORE_BASE64` (`base64 -w0 release.keystore`), `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. **Luôn dùng cùng một keystore**, nếu đổi, người dùng phải gỡ app cài lại.
