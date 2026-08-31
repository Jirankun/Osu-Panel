<p align="center">
  <img src="repo/Osu!_Panel.png" width="100">
</p>

# Osu! Panel — Native Android

[![GitHub release](https://img.shields.io/github/v/release/Jirankun/Osu-Panel?label=release)](https://github.com/Jirankun/Osu-Panel/releases)
[![License](https://img.shields.io/github/license/Jirankun/Osu-Panel)](https://github.com/Jirankun/Osu-Panel/blob/main/LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/Jirankun/Osu-Panel?style=social)](https://github.com/Jirankun/Osu-Panel/stargazers)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![osu! API v2](https://img.shields.io/badge/osu!-API%20v2-FF66AA?logo=osu&logoColor=white)](https://osu.ppy.sh/docs/index.html)

[Repository](https://github.com/Jirankun/Osu-Panel)
·
[Releases](https://github.com/Jirankun/Osu-Panel/releases)
·
[Issues](https://github.com/Jirankun/Osu-Panel/issues)

A native Android app for osu!, built with Jetpack Compose + Material 3.
It includes **OAuth login + the full osu! API v2 layer**, **3 home screen
widgets**, a **card generator**, and most main screens (Dashboard, Maps,
Rankings, Profile, Beatmap Detail, QR Share, Settings, License, Contributors).

> **License**: MIT — see [LICENSE](LICENSE).

---

## Features

- **OAuth login with osu!** (Authorization Code Grant + PKCE) through a
  Cloudflare Worker that holds the Client Secret — the app never touches it.
- **Quick login** via identifier (ID/username) using Client Credentials, plus
  a **guest mode** (browse without an account).
- **Dashboard** — profile header, stats grid, progress, detailed stats,
  all 352 medals (achieved bright / unachieved grey), badges, daily challenge.
- **Maps** — Last Play / Best Scores / Most Played / Favourite tabs with pagination.
  Each tab has pull-to-refresh and infinite scroll.
- **Rankings** — global performance ranking + **fuzzy user search**
  (osu! search endpoint), country filter.
- **Profile detail** — full profile, rank history chart, grade counts,
  badges, groups, medals (expandable, per-medal dialog), kudosu, best
  scores, most played, and a **card generator** FAB (Full stats / Skills /
  Mini templates → share the PNG via the Android share sheet).
- **Beatmap detail** — cover, quick stats, star difficulty, difficulties, info, creator,
  leaderboard (with a **YOU** row + **#1** row at the top when applicable),
  audio preview via Media3 ExoPlayer with **disk caching** (plays offline after first load),
  **Bookmark** (save/unsave beatmaps to local JSON storage),
  **Share** (Android share sheet with QR viewer URL + beatmap details),
  and **QR Beatmap Share** (full-screen QR display → PC scans via web viewer →
  beatmap card with audio preview).
- **Saved Maps** — view all bookmarked beatmaps, pull-to-refresh, tap to open detail.
- **Home screen widgets** — Profile Large (stat-signature card with
  `with stats` / `with skills` layouts), Rank & Level, and PP.
- **Update check popup** — checks the store via the proxy worker on app open.
  Popup appears every app open when an update is cached.
- **License & Contributor screens** — WebView pages with laser-triangle
  canvas, Torus font, and GitHub links.
- **Laser triangles** — two variants of the osu!lazer `TrianglesV2`
  background: `Modifier.trianglesLine()` (outline) and
  `Modifier.trianglesFill()` (filled + 3D shadow).
- **Chat (Chatango)** — private messages, group chat, user search, profile editing.
- **QR Beatmap Share (Web Viewer)** — Cloudflare Worker serves a viewer page with
  **dynamic OG meta tags** for social media previews. Camera-based QR scanning
  runs in the browser (JavaScript BarcodeDetector API), no native camera
  permissions needed. The viewer also supports **image upload** for QR detection.

### Share Message Format

```
https://osu-panel.zhyllanfyllah.my.id/qr/{beatmapsetId}

🎵 Title — Artist
👤 Mapper | ★2.15 Hard
🎵 BPM: 190
```

---

## Architecture

```
┌──────────────┐   OAuth (Custom Tab)  ┌──────────────┐    ┌──────────┐
│  App native  │──────────────────────▶│   osu! web   │    │  osu!    │
│  (Compose)   │◀──────────────────────│   authorize  │    │  API v2  │
└──────────────┘     osupanel://       └──────────────┘    └──────────┘
       │  ▲
       │  │ POST /auth/code, /auth/refresh, /auth/token
       ▼  │
┌──────────────────┐   GET /me, /users, /rankings, ...
│ Cloudflare Worker │───────▶ osu! API v2 (Bearer token)
│ (holds Client    │
│  Secret)          │
└──────────────────┘

┌──────────────┐   QR code (embedded)  ┌──────────────────┐
│  Android App │──────────────────────▶│  PC Browser      │
│  (beatmap)   │  base64 JSON in QR    │  (camera scan →  │
│  Share button│  /qr/{id} URL         │   beatmap card   │
└──────────────┘                       │   + audio play)  │
     │                                 └──────────────────┘
     │ share sheet                           ▲
     ▼                                       │ OG tags
┌──────────────────┐  Cloudflare Worker  ┌───┴──────────┐
│ Social Media     │────────────────────▶│  QR Viewer   │
│ (Twitter/Discord)│  dynamic OG tags    │  (Worker)    │
└──────────────────┘                     └──────────────┘
```

- The app **never stores the Client Secret** — auth is proxied through the
  Cloudflare Worker (`https://api-osupanel.zhyllanfyllah.my.id`). The worker
  source lives in `worker.js` at the project root.
- **Rate limiting**: global (200 req/60s, protects shared Client ID) + per-IP
  (30 req/60s) on all worker endpoints that call the osu! API.
- **Caching**: beatmap data cached 10 min in worker + session cache in app,
  user scores cached per session, audio preview cached on disk (10 MB LRU),
  token cached 50 min.
- Tokens stored encrypted in Android Keystore (`EncryptedSharedPreferences`).
- `AuthInterceptor` (OkHttp) injects `Authorization: Bearer`, handles
  401 → refresh via the worker → retry once.

---

## Project Structure

```
app/src/main/java/net/aokaze/osupanel/
├── OsuPanelApp.kt              # Application entry point
├── MainActivity.kt             # Activity — OAuth via Custom Tab
├── core/
│   ├── config/Env.kt           # Worker URL, client id, redirect, scopes
│   ├── navigation/             # Routes.kt + OsuPanelNavHost.kt
│   ├── theme/                  # Material 3 theme + OsuColors
│   └── util/                   # Formatters, Links
├── data/
│   ├── model/                  # DTOs (kotlinx.serialization)
│   ├── local/                  # TokenStore, DataCache, WidgetDataStore, ChatSettingsStore, BookmarkStore
│   ├── medal/                  # MedalService + MedalAssets (352 medals)
│   ├── skills/                 # SkillsFetcher (osu!skills radar data)
│   ├── remote/                 # WorkerApi, OsuApi, AuthInterceptor
│   └── repository/             # AuthRepository, ContentRepository
├── feature/
│   ├── auth/                   # AuthViewModel, Splash, Login
│   ├── home/ui/                # MainShell, DashboardScreen, SavedMapsScreen
│   ├── maps/                   # MapsViewModel (4 tabs) + MapsScreen
│   ├── rankings/               # RankingsViewModel + RankingsScreen
│   ├── profile/                # ProfileViewModel + ProfileScreen
│   ├── cardgen/                # Card generator
│   ├── beatmap/                # BeatmapDetailViewModel, BeatmapDetailScreen, QrCodeScreen
│   ├── chat/                   # ChatViewModel, ChatScreen, ChatSettings
│   ├── settings/ui/            # SettingsScreen, InfoWebViewScreens
│   └── update/                 # UpdateChecker
├── widget/                     # 3 home screen widgets + SignatureRenderer
└── ui/components/              # OsuSpinner, Triangles, BadgeImage, MedalImage, etc.
```

---

## Dependencies

| Library | Version | Purpose |
| --- | --- | --- |
| Jetpack Compose BOM | 2024.09 | UI framework |
| Material 3 | (via BOM) | Material Design |
| Material Icons Extended | (via BOM) | Full icon set |
| OkHttp | 4.12 | HTTP client |
| Retrofit | 2.11 | REST API client |
| kotlinx.serialization | 1.6.3 | JSON serialization |
| AppAuth | 0.11.1 | OAuth 2.0 (Authorization Code + PKCE) |
| Browser (Custom Tabs) | 1.8.0 | In-app browser for OAuth/login |
| Security-Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences (token storage) |
| Coil | 2.7 | Image loading + GIF support |
| AndroidSVG | 1.4 | SVG rendering (widget signature) |
| Media3 ExoPlayer | 1.4.1 | Audio preview playback + disk caching |
| ZXing | 3.5.3 | QR code generation |

---

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | API calls to osu! and Cloudflare Worker |
| `ACCESS_NETWORK_STATE` | Check connectivity before requests |
| `VIBRATE` | Haptic feedback on QR tap |

No camera permission needed — QR scanning runs in the browser via JavaScript BarcodeDetector.

---

## Build

```bash
./gradlew :app:assembleDebug      # Debug APK (installable, no signing)
./gradlew :app:assembleRelease    # Release APK (R8 + signing from SIGN_* env)
```

Requirements: JDK 17, Android SDK (`local.properties` → `sdk.dir`).

---

## Security

- `worker.js` has **no** `CLIENT_SECRET` fallback — it must be set as a Cloudflare env variable.
- **Global rate limiting** (200 req/60s) protects the shared osu! Client ID quota.
- **Per-IP rate limiting** (30 req/60s) prevents abuse.
- Beatmap data cached in worker (10 min) to minimize osu! API hits.
- Audio preview cached on disk (10 MB) — no repeated downloads for loops.
- `key.txt` (local OAuth keys) is **git-ignored**.

---

## License

MIT — see [LICENSE](LICENSE).

Third-party licenses are listed in the app (Settings → License).
