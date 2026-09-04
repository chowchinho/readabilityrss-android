# Readability Reader

The native Android client for
**[ReadabilityRSS](https://github.com/chowchinho/readabilityrss-web)**, a self-hosted
full-text RSS reader. Built with Kotlin and Jetpack Compose, and designed around offline
reading, adaptive phone/tablet layouts, and first-class E-Ink support.

It talks to the server over the FEVER API, so it also works with **any FEVER-compatible
backend** — Miniflux, FreshRSS, Tiny Tiny RSS. Pairing it with ReadabilityRSS additionally
unlocks full-article extraction, ML ranking and focal-point thumbnails; see
[Pairing with ReadabilityRSS](#pairing-with-readabilityrss) below.

Distributed as a sideloadable APK. Not on the Play Store.

## Why

Most RSS clients assume you are online and reading on a bright LCD. This one assumes the
opposite: that you sync at home, read on the Underground with no signal, and that the
screen might be electronic paper. Articles, inline images and favicons are all cached to
disk and served without revalidation, so a synced article renders identically with the
radio off.

## Features

**Reading**
- Native HTML rendering — no WebView. `HtmlCompat.fromHtml` with custom Spannable styling
  and a Coil-backed `ImageGetter`, which is what makes E-Ink theming and font control work.
- Swipe left/right in the reader to move between articles.
- Always-on-top action bar: read/unread, save, open in browser, share.
- Configurable auto-mark-as-read delay (2s / 5s / 10s / off).
- Adjustable font size and family for both the list and the reader, with live preview.

**Offline**
- Full offline-first design. Every read and state change works from the local cache;
  mutations queue and flush on the next sync.
- Article HTML, all inline images and favicons cached to disk.
- Time-based eviction (1–30 days) with a user-configurable disk cap (250 MB – unlimited).
- Saved articles are permanent and never evicted.
- Force-offline toggle for testing and for deliberate low-data use.

**Layout**
- Phone: Feed List → Article List → Reader.
- Tablet/landscape: adaptive three-pane shell.
- Two list view modes — Standard and Full Image — remembered per feed and per category.
- Uniform-height article cards, with line heights pinned so English, Chinese and Japanese
  rows measure identically.

**E-Ink**
- Dedicated light and dark E-Ink themes: pure black-on-white, grayscale image filter, all
  animations suppressed, minimum body font clamped to 20sp.
- Reader supports physical page-turn buttons.

**Sync**
- WorkManager periodic sync (1 / 3 / 6 / 24 h), WiFi-only and charging-only options.
- Expedited manual refresh, foreground-service progress notifications.
- Multiple server profiles; credentials stored in `EncryptedSharedPreferences`.

## Requirements

- Android 12 (API 31) or newer
- A FEVER-compatible RSS backend

## Setting up a server

The app speaks only the [FEVER API](https://feedafever.com/api). Use
**[ReadabilityRSS](https://github.com/chowchinho/readabilityrss-web)** for the full
experience, or any other server implementing the API — **Miniflux**, **FreshRSS**,
**Tiny Tiny RSS** (via the Fever plugin), or Fever itself.

On first run you are asked for:

| Field | Example |
|---|---|
| Server URL | `https://your-server.example/fever/` |
| Username | your account username |
| Password | your account password |

The app derives the API key as `MD5("username:password")` locally. Your password is stored
encrypted on device and is never sent in plain text as a separate credential.

## Building

```bash
git clone https://github.com/chowchinho/readabilityrss-android.git
cd readabilityrss-android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+ and Android SDK 31+. Gradle will create `local.properties` for you, or set
`sdk.dir` there yourself.

Run the test suite with:

```bash
./gradlew testDebugUnitTest
```

## Pairing with ReadabilityRSS

This app is the Android client for
**[ReadabilityRSS](https://github.com/chowchinho/readabilityrss-web)** — a self-hosted
FastAPI server that fetches each article, extracts the real content, repairs images
publishers hide behind lazy-loading, and exposes the result over a Fever-compatible
endpoint at `/fever?api`.

Everything in the Features list works against any FEVER server. These additional features
require ReadabilityRSS specifically, because they depend on endpoints the FEVER spec does
not define:

| Feature | Needs ReadabilityRSS |
|---|---|
| Full article text rather than truncated feed summaries | yes |
| ML ranking — personalised ordering, swipe voting, score breakdown | yes |
| Server-computed image focal points for smart thumbnail cropping | yes |
| Archival export of saved articles | yes |

Each degrades cleanly without it: articles fall back to reverse-chronological order,
thumbnails centre-crop, and the export path simply goes unused. Nothing breaks.

## Architecture

MVVM + Clean, with Hilt for DI.

```
app/
├── data/
│   ├── local/        Room database, DAOs, entities
│   ├── remote/       Retrofit FEVER service, DTOs
│   └── repository/   Repository impls, connectivity, image cache
├── domain/           Pure models, repository interfaces, use cases
├── ui/               Compose screens (feeds, articles, reader, saved,
│                     settings, setup, tablet) + theme
└── worker/           Sync and image-cache workers
```

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| DI | Hilt |
| HTTP | Retrofit 2 + OkHttp |
| Database | Room |
| Images | Coil 2.x |
| Background | WorkManager |

Domain models are pure Kotlin with no Android imports. UI state is a sealed interface
(`Loading` / `Success` / `Error`) collected with `collectAsStateWithLifecycle()`.

## Licence

MIT — see [LICENSE](LICENSE).
