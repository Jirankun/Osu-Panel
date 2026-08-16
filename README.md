# Osu! Panel — Native Android

[![GitHub release](https://img.shields.io/github/v/release/Jirankun/Osu-Panel?label=release)](https://github.com/Jirankun/Osu-Panel/releases)
[![License](https://img.shields.io/github/license/Jirankun/Osu-Panel)](https://github.com/Jirankun/Osu-Panel/blob/main/LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/Jirankun/Osu-Panel?style=social)](https://github.com/Jirankun/Osu-Panel/stargazers)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2035-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Material 3](https://img.shields.io/badge/Material%203-UI-6750A4?logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![osu! API v2](https://img.shields.io/badge/osu!-API%20v2-FF66AA?logo=osu&logoColor=white)](https://osu.ppy.sh/docs/index.html)

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Jirankun/Osu-Panel)

<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-35-orange.svg?style=flat-square)](CONTRIBUTORS.md)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

[Repository](https://github.com/Jirankun/Osu-Panel)
·
[Releases](https://github.com/Jirankun/Osu-Panel/releases)
·
[Issues](https://github.com/Jirankun/Osu-Panel/issues)
·
[License](https://github.com/Jirankun/Osu-Panel/blob/main/LICENSE)

A native Android rewrite of the Osu! Panel app, built
with Jetpack Compose + Material 3.
It includes **OAuth login + the full osu! API v2 layer**, **3 home screen
widgets**, a **card generator**, and most main screens (Dashboard, Maps,
Rankings, Profile, Beatmap Detail, Settings, License, Contributor).

> **License**: MIT — see [LICENSE](LICENSE).
>
> **Language**: [English](#english) · [Bahasa Indonesia](#bahasa-indonesia)

---

<a id="english"></a>

## 🇬🇧 English

### Features

- **OAuth login with osu!** (Authorization Code Grant + PKCE) through a
  Cloudflare Worker that holds the Client Secret — the app never touches it.
- **Quick login** via identifier (ID/username) using Client Credentials, plus
  a **guest mode** (enter the app without an account for browsing).
- **Dashboard** — profile header, stats grid, progress, detailed stats, all
  352 medals (achieved bright / unachieved grey), badges.
- **Maps** — Last Play / Best Scores / Most Played / Loved with pagination.
- **Rankings** — global performance ranking + **partial/fuzzy user search**
  (osu! search endpoint), country filter, laser-triangle UI accents.
- **Profile detail** — full profile, rank history chart, grade counts,
  badges, groups, medals (expandable, per-medal dialog), kudosu, best
  scores, most played, and a **card generator** FAB (Full stats / Skills /
  Mini templates → share the PNG via the Android share sheet).
- **Beatmap detail** — cover, quick stats, difficulties, info, creator,
  leaderboard (with a **YOU** row + **#1** row at the top when applicable),
  and audio preview via Media3.
- **Home screen widgets** — Profile Large (stat-sign signature card with
  `with stats` / `with skills` layouts), Rank & Level, and PP.
- **Update check popup** — checks the Appteka store via the proxy worker on
  app open, shows "Update available!" at most **once per day**.
- **License & Contributor screens** — WebView pages with the laser-triangle
  canvas, Torus font injected from `res/font`, and GitHub links opened in an
  external browser.
- **Laser triangles everywhere** — the osu!lazer `TrianglesV2` background
  (`Modifier.trianglesBackground()`), used by buttons, cards, and screens.

### Architecture

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
```

- The app **never stores the Client Secret** — auth is proxied through the
  Cloudflare Worker (`https://api-osupanel.zhyllanfyllah.my.id`). The worker
  source lives in `worker.js` at the project root. `CLIENT_ID` /
  `CLIENT_SECRET` are configured as Cloudflare environment variables.
- Tokens are stored encrypted in the Android Keystore
  (`EncryptedSharedPreferences`).
- `AuthInterceptor` (OkHttp) injects `Authorization: Bearer`, handles
  401 → refresh via the worker (shared across concurrent requests) → retry
  once. A **definite** refresh failure logs out; a **network** failure keeps
  the token and surfaces a friendly error.
- Home screen widgets read a user snapshot from `WidgetDataStore`
  (a dedicated SharedPreferences), rewritten on every login / app open /
  refresh.

### Login flow

1. "Login with osu!" → AppAuth opens the osu! authorize page in a system
   Custom Tab (`client_id` from `Env.kt`, scopes `identify public friends.read`,
   redirect `osupanel://callback`).
2. The user logs in & approves → redirect back to the app
   (`RedirectUriReceiverActivity`, scheme `osupanel`).
3. The code is exchanged via the worker `POST /auth/code {code, redirect_uri,
   code_verifier}` → access + refresh tokens stored (PKCE is required by
   osu!).
4. The user is fetched via `GET /me`.
5. Any API 401 → automatic refresh via `POST /auth/refresh`.

Besides OAuth login, there is a **quick login** via identifier (ID/username)
using Client Credentials (`POST /auth/token` through the worker).

### Project structure

```
app/src/main/java/net/aokaze/osupanel/
├── OsuPanelApp.kt             # Application — entry point (builds AppContainer)
├── MainActivity.kt            # Activity — entry point (OAuth via Custom Tab)
├── di/AppContainer.kt         # Manual DI: Retrofit, OkHttp, TokenStore, repos, flows
├── core/
│   ├── config/Env.kt          # Worker URL, client id, redirect, scopes, timeouts
│   ├── util/Formatters.kt     # Number/duration/accuracy/date formatting
│   ├── theme/                 # Material 3 + Torus font (colors from colors.xml)
│   └── navigation/            # Routes.kt + OsuPanelNavHost.kt (auth-based NavHost)
├── data/                      # DATA LAYER — UI-free, shared by every feature
│   ├── model/                 # DTOs (kotlinx.serialization)
│   ├── local/                 # TokenStore (encrypted), DataCache, WidgetDataStore
│   ├── medal/                 # MedalService + MedalAssets (all 352 medals)
│   ├── skills/                # SkillsFetcher (osu!skills radar data)
│   ├── remote/                # WorkerApi, OsuApi, AuthInterceptor, ApiError
│   └── repository/            # AuthRepository, ContentRepository
├── feature/                   # PER-FEATURE SCREENS
│   ├── auth/                  # AuthModels, AuthViewModel + ui/ (Splash, Login)
│   ├── home/ui/               # MainShell (bottom nav + pager), DashboardScreen
│   ├── maps/                  # MapsViewModel (4 paged tabs) + ui/MapsScreen
│   ├── rankings/              # RankingsViewModel + ui/RankingsScreen
│   ├── profile/               # ProfileViewModel + ui/ProfileScreen (+ cardgen FAB)
│   ├── cardgen/               # Card generator (ViewModel + UI + share)
│   ├── beatmap/               # BeatmapDetailViewModel + ui/BeatmapDetailScreen
│   ├── settings/ui/           # SettingsScreen, InfoWebViewScreens
│   └── update/                # UpdateChecker + UpdateCheckViewModel
├── widget/                    # HOME SCREEN WIDGETS (3) + SignatureRenderer
└── ui/components/             # Global components (OsuSpinner, TrianglesBackground, ...)
```

### Build

```bash
./gradlew :app:assembleDebug      # debug APK (installable, no signing needed)
./gradlew :app:assembleRelease    # release APK (R8 + signing from SIGN_* env)
```

Requirements: JDK 17, Android SDK (`local.properties` → `sdk.dir`). Release
signing reads `SIGN_KEY/SIGN_ALIAS/SIGN_KEY_PASS/SIGN_STORE_PASS` from the
environment; without them the release APK still builds but is unsigned.

### Releases (GitHub Actions)

Every build produces **two APKs** from a single `gradlew` run:

- `app-debug.apk` — **never signed**, installable on any device.
- `app-release.apk` — **signed** (when the `SIGN_*` secrets are configured),
  otherwise unsigned. Signing only applies to **nightly** and **release**
  builds — debug is intentionally never signed.

**Release** — created **only from a comment on a pull request**, never
automatically:

| Command | Effect |
| --- | --- |
| `/release` | Build from the PR head, keep the version in `gradle.properties`, create a **draft** GitHub release with both APKs. |
| `/release 1.2.3` | Same, but bump `APP_VERSION_NAME`/`APP_VERSION_CODE` to `1.2.3` first. |

Only the repository **owner / members / collaborators** can trigger it. The
release is a **draft** — review the notes and publish it manually.

**Nightly** — `nightly.yml` runs every day at 00:00 UTC (and manually via
Actions → Nightly → Run workflow). It builds both APKs and **publishes** the
`nightly` GitHub release automatically (recreated on each run).

**CI** — `build.yml` runs on every push to `main` and every pull request and
uploads both APKs as artifacts.

### Signing (keystore + env)

The keystore is **never committed**. The release APK is signed only when all
four `SIGN_*` values are present — locally from your shell env, in CI from
repository secrets:

| Variable | Meaning |
| --- | --- |
| `SIGN_KEY` | Path to the `.jks` keystore file |
| `SIGN_ALIAS` | Alias of the signing key inside the keystore |
| `SIGN_KEY_PASS` | Password of that key |
| `SIGN_STORE_PASS` | Password of the keystore itself |

**Locally** — keep the keystore **outside the repo** (or in `keystore/`, which
is git-ignored) and export the vars in `~/.environment_variable.sh`:

```bash
# ~/.environment_variable.sh (git-ignored)
export SIGN_KEY="$HOME/.keystores/osu-panel-release.jks"
export SIGN_ALIAS="osu-panel"
export SIGN_KEY_PASS="..."
export SIGN_STORE_PASS="..."
```

Create a keystore once with:

```bash
keytool -genkey -v -keystore ~/.keystores/osu-panel-release.jks \
  -alias osu-panel -keyalg RSA -keysize 2048 -validity 10000
```

**GitHub Actions** — add these **repository secrets** (Settings → Secrets and
variables → Actions):

| Secret | Value |
| --- | --- |
| `SIGN_KEYSTORE_B64` | The keystore **encoded in base64** (workflows decode it at build time) |
| `SIGN_ALIAS` | Alias of the signing key |
| `SIGN_KEY_PASS` | Key password |
| `SIGN_STORE_PASS` | Store password |

```bash
# one-time: print the base64 to paste into the SIGN_KEYSTORE_B64 secret
base64 -w 0 ~/.keystores/osu-panel-release.jks
```

Without these secrets every workflow still succeeds — the release APK is just
unsigned (debug is never signed either way).

### Contribution guidelines

> **Project Owner:** Jirang
>
> Please respect the project's direction and follow the guidelines below before submitting a pull request.

1. **New feature** → create `feature/<name>/` with `XxxViewModel.kt` +
   `ui/XxxScreen.kt`, then register the route in `core/navigation/`.
2. **New API endpoint** → add a method in `data/remote/OsuApi.kt` (osu! API)
   or `WorkerApi.kt` (worker). DTOs go in `data/model/`.
3. **Shared UI component** → put it in `ui/components/`, one file per
   component. Laser-triangle backgrounds → `Modifier.trianglesBackground()`.
4. **Text** → `res/values/strings.xml`. **Colors** → `res/values/colors.xml`.
5. **Dependencies** → register the instance in `di/AppContainer.kt`
   (manual DI — access via `(application as OsuPanelApp).container`).
6. **New widget** → provider in `widget/`, layout in `res/layout/`, metadata
   in `res/xml/`, receiver in `AndroidManifest.xml`, and add the class to the
   `widgetProviders` list in `WidgetDataStore.kt`.

### Security notes

- `key.txt` (local OAuth keys) is **git-ignored** — never commit it.
- `worker.js` intentionally contains **no** `CLIENT_SECRET` fallback — set it
  as a Cloudflare environment variable (`CLIENT_ID`, `CLIENT_SECRET`).

### License

MIT — see [LICENSE](LICENSE). Third-party licenses are listed in
[THIRD_PARTY_LICENSES.txt](THIRD_PARTY_LICENSES.txt) and inside the app
(Settings → License).

---

<a id="bahasa-indonesia"></a>

## 🇮🇩 Bahasa Indonesia

### Fitur

- **Login OAuth dengan osu!** (Authorization Code Grant + PKCE) lewat
  Cloudflare Worker yang menyimpan Client Secret — aplikasi tidak pernah
  menyentuhnya.
- **Quick login** via identifier (ID/username) memakai Client Credentials,
  plus **guest mode** (masuk aplikasi tanpa akun untuk menjelajah).
- **Dashboard** — header profil, grid statistik, progress, statistik detail,
  seluruh 352 medal (yang diraih terang / belum abu-abu), badge.
- **Maps** — Last Play / Best Scores / Most Played / Loved dengan paginasi.
- **Rankings** — ranking performa global + **pencarian user sebagian/mirip**
  (endpoint search osu!), filter negara, aksen UI segitiga lazer.
- **Profile detail** — profil lengkap, grafik rank history, grade counts,
  badge, grup, medal (bisa diperluas, popup per medal), kudosu, best scores,
  most played, plus **FAB generator kartu** (template Full stats / Skills /
  Mini → bagikan PNG lewat share sheet Android).
- **Beatmap detail** — cover, statistik singkat, difficulties, info, creator,
  leaderboard (dengan baris **YOU** dan **#1** di atas saat relevan), dan
  preview audio via Media3.
- **Widget home screen** — Profile Large (kartu signature stat-sign dengan
  layout `with stats` / `with skills`), Rank & Level, dan PP.
- **Popup update** — cek toko Appteka via proxy worker saat app dibuka,
  menampilkan "Update available!" maksimal **sekali per hari**.
- **Layar License & Contributor** — halaman WebView dengan canvas segitiga
  lazer, font Torus di-inject dari `res/font`, link GitHub dibuka di browser
  eksternal.
- **Segitiga lazer di mana-mana** — latar `TrianglesV2` milik osu!lazer
  (`Modifier.trianglesBackground()`), dipakai tombol, kartu, dan layar.

### Arsitektur

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
│ (menyimpan Client│
│  Secret)          │
└──────────────────┘
```

- Aplikasi **tidak pernah menyimpan Client Secret** — autentikasi diproxy
  lewat Cloudflare Worker (`https://api-osupanel.zhyllanfyllah.my.id`).
  Kode worker ada di `worker.js` (root proyek). `CLIENT_ID` / `CLIENT_SECRET`
  dikonfigurasi sebagai environment variable Cloudflare.
- Token disimpan terenkripsi di Android Keystore
  (`EncryptedSharedPreferences`).
- `AuthInterceptor` (OkHttp) menyuntik `Authorization: Bearer`, menangani
  401 → refresh via worker (dibagi antar request konkuren) → retry sekali.
  Kegagalan refresh **definitif** = logout; kegagalan **jaringan** = token
  dipertahankan dan muncul error yang ramah.
- Widget home screen membaca snapshot user dari `WidgetDataStore`
  (SharedPreferences khusus), ditulis ulang setiap login / buka app / refresh.

### Alur login

1. "Login with osu!" → AppAuth membuka halaman authorize osu! di Custom Tab
   (`client_id` dari `Env.kt`, scope `identify public friends.read`, redirect
   `osupanel://callback`).
2. User login & menyetujui → redirect balik ke app
   (`RedirectUriReceiverActivity`, scheme `osupanel`).
3. Kode ditukar via worker `POST /auth/code {code, redirect_uri,
   code_verifier}` → access + refresh token disimpan (PKCE wajib di osu!).
4. User diambil via `GET /me`.
5. API 401 apa pun → refresh otomatis via `POST /auth/refresh`.

Selain login OAuth, ada **quick login** via identifier (ID/username) memakai
Client Credentials (`POST /auth/token` lewat worker).

### Struktur proyek

```
app/src/main/java/net/aokaze/osupanel/
├── OsuPanelApp.kt             # Application — titik masuk (membangun AppContainer)
├── MainActivity.kt            # Activity — titik masuk (OAuth via Custom Tab)
├── di/AppContainer.kt         # DI manual: Retrofit, OkHttp, TokenStore, repo, flow
├── core/
│   ├── config/Env.kt          # URL worker, client id, redirect, scope, timeout
│   ├── util/Formatters.kt     # Format angka/durasi/akurasi/tanggal
│   ├── theme/                 # Material 3 + font Torus (warna dari colors.xml)
│   └── navigation/            # Routes.kt + OsuPanelNavHost.kt (NavHost berbasis auth)
├── data/                      # LAPISAN DATA — bebas UI, dipakai semua fitur
│   ├── model/                 # DTO (kotlinx.serialization)
│   ├── local/                 # TokenStore (enkripsi), DataCache, WidgetDataStore
│   ├── medal/                 # MedalService + MedalAssets (352 medal)
│   ├── skills/                # SkillsFetcher (data radar osu!skills)
│   ├── remote/                # WorkerApi, OsuApi, AuthInterceptor, ApiError
│   └── repository/            # AuthRepository, ContentRepository
├── feature/                   # LAYAR PER-FITUR
│   ├── auth/                  # AuthModels, AuthViewModel + ui/ (Splash, Login)
│   ├── home/ui/               # MainShell (bottom nav + pager), DashboardScreen
│   ├── maps/                  # MapsViewModel (4 tab berpaginasi) + ui/MapsScreen
│   ├── rankings/              # RankingsViewModel + ui/RankingsScreen
│   ├── profile/               # ProfileViewModel + ui/ProfileScreen (+ FAB cardgen)
│   ├── cardgen/               # Generator kartu (ViewModel + UI + share)
│   ├── beatmap/               # BeatmapDetailViewModel + ui/BeatmapDetailScreen
│   ├── settings/ui/           # SettingsScreen, InfoWebViewScreens
│   └── update/                # UpdateChecker + UpdateCheckViewModel
├── widget/                    # WIDGET HOME SCREEN (3) + SignatureRenderer
└── ui/components/             # Komponen global (OsuSpinner, TrianglesBackground, ...)
```

### Build

```bash
./gradlew :app:assembleDebug      # APK debug (bisa install, tanpa signing)
./gradlew :app:assembleRelease    # APK release (R8 + signing dari env SIGN_*)
```

Syarat: JDK 17, Android SDK (`local.properties` → `sdk.dir`). Signing release
membaca `SIGN_KEY/SIGN_ALIAS/SIGN_KEY_PASS/SIGN_STORE_PASS` dari environment;
tanpa itu APK release tetap ter-build tapi unsigned.

### Rilis (GitHub Actions)

Setiap build menghasilkan **dua APK** dari satu kali `gradlew`:

- `app-debug.apk` — **tidak pernah di-sign**, bisa di-install di perangkat apa pun.
- `app-release.apk` — **di-sign** (jika secret `SIGN_*` dikonfigurasi),
  kalau tidak, tanpa tanda tangan. Signing hanya berlaku untuk build
  **nightly** dan **release** — debug sengaja tidak pernah di-sign.

**Release** — dibuat **hanya dari komentar pada pull request**, tidak pernah
otomatis:

| Perintah | Efek |
| --- | --- |
| `/release` | Build dari head PR, pertahankan versi di `gradle.properties`, buat release GitHub **draft** dengan kedua APK. |
| `/release 1.2.3` | Sama, tapi bump `APP_VERSION_NAME`/`APP_VERSION_CODE` ke `1.2.3` dulu. |

Hanya **owner / member / collaborator** repo yang bisa memicunya. Rilis dibuat
sebagai **draft** — tinjau catatan lalu publish manual.

**Nightly** — `nightly.yml` berjalan setiap hari pukul 00:00 UTC (dan manual
via Actions → Nightly → Run workflow). Ia membangun kedua APK dan
**menerbitkan** release `nightly` secara otomatis (dibuat ulang tiap run).

**CI** — `build.yml` berjalan setiap push ke `main` dan setiap pull request
serta mengunggah kedua APK sebagai artifact.

### Signing (keystore + env)

Keystore **tidak pernah di-commit**. APK release hanya di-sign saat keempat
nilai `SIGN_*` tersedia — lokal dari env shell, di CI dari secret repo:

| Variabel | Arti |
| --- | --- |
| `SIGN_KEY` | Path file keystore `.jks` |
| `SIGN_ALIAS` | Alias kunci signing di dalam keystore |
| `SIGN_KEY_PASS` | Password kunci tersebut |
| `SIGN_STORE_PASS` | Password keystore itu sendiri |

**Lokal** — simpan keystore **di luar repo** (atau di `keystore/`, yang
sudah di-ignore git) dan export variabelnya di `~/.environment_variable.sh`:

```bash
# ~/.environment_variable.sh (di-ignore git)
export SIGN_KEY="$HOME/.keystores/osu-panel-release.jks"
export SIGN_ALIAS="osu-panel"
export SIGN_KEY_PASS="..."
export SIGN_STORE_PASS="..."
```

Buat keystore sekali dengan:

```bash
keytool -genkey -v -keystore ~/.keystores/osu-panel-release.jks \
  -alias osu-panel -keyalg RSA -keysize 2048 -validity 10000
```

**GitHub Actions** — tambahkan **repository secrets** ini (Settings → Secrets
and variables → Actions):

| Secret | Nilai |
| --- | --- |
| `SIGN_KEYSTORE_B64` | Keystore yang **di-encode base64** (workflow mendekode-nya saat build) |
| `SIGN_ALIAS` | Alias kunci signing |
| `SIGN_KEY_PASS` | Password kunci |
| `SIGN_STORE_PASS` | Password store |

```bash
# sekali saja: cetak base64 untuk ditempel ke secret SIGN_KEYSTORE_B64
base64 -w 0 ~/.keystores/osu-panel-release.jks
```

Tanpa secret ini semua workflow tetap sukses — APK release hanya tanpa
tanda tangan (debug tetap tidak pernah di-sign).

### Panduan kontribusi

> **Pemilik Proyek:** Jirang
>
> Mohon hormati arah proyek ini dan ikuti panduan di bawah ini sebelum mengirimkan pull request.

1. **Fitur baru** → buat `feature/<nama>/` dengan `XxxViewModel.kt` +
   `ui/XxxScreen.kt`, lalu daftarkan route di `core/navigation/`.
2. **Endpoint API baru** → tambah method di `data/remote/OsuApi.kt` (API osu!)
   atau `WorkerApi.kt` (worker). DTO di `data/model/`.
3. **Komponen UI bersama** → taruh di `ui/components/`, satu file per
   komponen. Latar segitiga lazer → `Modifier.trianglesBackground()`.
4. **Teks** → `res/values/strings.xml`. **Warna** → `res/values/colors.xml`.
5. **Dependensi** → daftarkan instance di `di/AppContainer.kt`
   (DI manual — akses via `(application as OsuPanelApp).container`).
6. **Widget baru** → provider di `widget/`, layout di `res/layout/`, metadata
   di `res/xml/`, receiver di `AndroidManifest.xml`, dan tambah kelasnya ke
   daftar `widgetProviders` di `WidgetDataStore.kt`.

### Catatan keamanan

- `key.txt` (kunci OAuth lokal) **di-ignore git** — jangan pernah commit.
- `worker.js` sengaja **tidak** berisi fallback `CLIENT_SECRET` — set sebagai
  environment variable Cloudflare (`CLIENT_ID`, `CLIENT_SECRET`).

### Lisensi

MIT — lihat [LICENSE](LICENSE). Lisensi pihak ketiga ada di
[THIRD_PARTY_LICENSES.txt](THIRD_PARTY_LICENSES.txt) dan di dalam aplikasi
(Settings → License).
```
