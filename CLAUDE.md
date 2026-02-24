# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Materialistic is a Material Design Hacker News client for Android. It uses the official HackerNews API, Algolia Search API, and Mercury Web Parser API.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Debug build with LeakCanary memory leak detection
./gradlew assembleDebug -Pleak

# Release build (requires signing configuration)
./gradlew assembleRelease

# Run lint checks
./gradlew lint
```

There is no test suite in the project — no unit tests or instrumentation tests exist.

## Requirements

- JDK 17 (compilation target)
- Android SDK with compileSdk/targetSdk 36
- Min SDK: 23 (Android 6.0)
- Gradle 9.3.1 (via wrapper)

## Build System

Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Key dependency versions: AGP 9.0.1, Kotlin 2.3.10, Retrofit 3.0.0, OkHttp 5.x, Room 2.8.x, Dagger 1.2.5, RxJava 1.3.8. Uses KAPT for annotation processing (Dagger 1 and Room).

## Build Warnings

The build currently emits four deprecation warnings that cannot be resolved without a major refactor:

- `android.builtInKotlin=false` is deprecated (AGP default is now `true`)
- `android.newDsl=false` is deprecated (AGP default is now `true`)
- `org.jetbrains.kotlin.android` plugin is deprecated in AGP 9.0+
- `BaseAppModuleExtension` `android {}` accessor is deprecated, replaced by `ApplicationExtension`

**Root cause:** AGP 9.0 built-in Kotlin is incompatible with the `kotlin.kapt` plugin. KAPT cannot be removed because Dagger 1.2.5 has no KSP migration path. Resolving these warnings requires replacing Dagger 1.x with Dagger 2 / Hilt and migrating Room annotation processing from KAPT to KSP.

All other AGP deprecation warnings have been resolved (removed `android.usesSdkInManifest.disallowed`, `android.sdk.defaultTargetSdkToCompileSdkIfUnset`, `android.enableAppCompileTimeRClass`, `android.defaults.buildfeatures.resvalues`, `android.r8.optimizedResourceShrinking`; suppressed `excludeLibraryComponentsFromConstraints` sync warnings).

Lint configuration is in `lint.xml` at the project root. Lint is strict (`abortOnError = true`) — builds fail on warnings or errors.

ProGuard is split across four files (`proguard-rules.pro`, `proguard-square.pro`, `proguard-support.pro`, `proguard-rx.pro`). Obfuscation is disabled (`-dontobfuscate`); only shrinking is enabled for release builds.

Resource locale filtering: only `en`, `zh-rCN`, `es` are included. Bundle splits are all disabled (universal APK).

## Architecture

### Dependency Injection (Dagger 1.x)

The app uses Square's Dagger 1.2.5 (not Dagger 2/Hilt).

- `Injectable` interface provides `inject()` and `getApplicationGraph()` methods
- `Application` class creates the root `ObjectGraph` in `attachBaseContext()` (not `onCreate()` — this is intentional for early initialization)
- Activities extend `InjectableActivity` which creates a child graph via `ObjectGraph.plus(new ActivityModule(this), new UiModule())`
- Fragments do NOT have an injectable base class. Instead, `BaseFragment.onActivityCreated()` casts the parent activity to `Injectable` and calls `inject(this)`. Fragments must be listed in `ActivityModule` or `UiModule`'s `injects` array.

Modules:
- `DataModule` — API clients, database, managers. Uses `@Named` for three `ItemManager` implementations: `HN`, `ALGOLIA`, `POPULAR`
- `NetworkModule` — OkHttp with per-host cache policies (HN/Algolia: 30 min, Readability: 24 hours) and offline `FORCE_CACHE` support
- `ActivityModule` — Activity-scoped dependencies; declares injectable targets in `injects = {}`
- `UiModule` — UI components (fragments, widgets)

### Data Layer

**API Clients** (Retrofit 3.x + RxJava 1.x):
- `HackerNewsClient` — Official HN API
- `AlgoliaClient` — Search functionality
- `ReadabilityClient` — Article parsing via Mercury
- `UserServicesClient` — Authentication (relies on redirect requests to HN website)

**Database** (Room):
- `MaterialisticDatabase` (version 4) with tables: `SavedStory`, `ReadStory`, `Readable`
- Has a manual SQL migration (3→4) that renames legacy tables (`favorite`→`saved`, `viewed`→`read`, `readability`→`readable`)

**Caching**:
- `LocalCache` interface with Kotlin implementation in `data/android/Cache.kt`
- OkHttp interceptor-based HTTP caching (20 MB disk cache) with per-host cache control via `ConnectionAwareInterceptor`

### UI Layer

Activity inheritance chain:
```
ThemedActivity              — Theme switching (applied BEFORE super.onCreate())
  └─ InjectableActivity     — Activity-scoped ObjectGraph
       └─ DrawerActivity    — Navigation drawer
            └─ BaseListActivity — List screens with search/tabs/FAB, multi-pane support
```

**Multi-pane**: Detected via `R.bool.multi_pane` resource boolean. In multi-pane mode (tablets/landscape), list is shown on the left with a ViewPager for item details on the right. Single-pane navigates to `ItemActivity`.

**Theme application**: `Preferences.Theme` applies the theme in `onCreate()` **before** `super.onCreate()`. Theme changes trigger a full activity restart via `AppUtils.restart()`.

### WebView Layer

The app uses a custom WebView hierarchy for displaying article content:

```
android.webkit.WebView
  └─ WebView (widget)        — History management, pending URL reload via about:blank
       └─ CacheableWebView   — Web archive (.mht) caching, offline support
```

**Key classes:**
- `WebView` (`widget/WebView.java`) — Wraps Android WebView with `HistoryWebViewClient` that manages reload sequences (load `about:blank` → load target URL → clear history). Visibility is set when any non-blank page starts loading while a pending URL exists (handles redirects).
- `CacheableWebView` (`widget/CacheableWebView.java`) — Adds `.mht` web archive caching. Configures WebView settings: JavaScript, DOM storage, third-party cookies, mixed content mode. Uses `LOAD_CACHE_ELSE_NETWORK` when online, `LOAD_CACHE_ONLY` when offline.
- `AdBlockWebViewClient` (`widget/AdBlockWebViewClient.java`) — Intercepts requests via `shouldInterceptRequest()` and blocks ad hosts loaded from `assets/pgl.yoyo.org.txt`. Uses recursive subdomain matching.
- `WebFragment` — Main article viewer. Uses `CacheableWebView` with `AdBlockWebViewClient`. Supports readability mode toggle, PDF viewing via JavaScript bridge, fullscreen mode, and find-in-page.

**Important:** `HistoryWebViewClient` only delegates `onPageStarted`, `onPageFinished`, and `shouldInterceptRequest` to the wrapped client. Other `WebViewClient` callbacks (e.g. `shouldOverrideUrlLoading`) are NOT delegated.

### Reactive Patterns

Uses RxJava 1.x (not 2.x) with schedulers injected via `@Named(IO_THREAD)` and `@Named(MAIN_THREAD)`.

### AndroidManifest Highlights

- Custom signature-level permissions for sync and account authentication
- Deep linking: `news.ycombinator.com/item`, `news.ycombinator.com/user`, and `materialistic://` custom scheme
- Sync adapter runs in a separate `:sync` process

## Code Style

- Follow [Android code style](https://source.android.com/source/code-style.html)
- Mix of Java and Kotlin; newer files tend to be Kotlin
- `@PublicApi` — Marks APIs that should be discoverable (SOURCE retention)
- `@Synthetic` — Marks fields/methods with relaxed visibility to avoid synthetic accessor generation

## API Keys

Optional build config fields `MERCURY_TOKEN` and `GITHUB_TOKEN` (both empty by default).
