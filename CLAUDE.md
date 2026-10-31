# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Materialistic is a Material Design Hacker News client for Android. It uses the official HackerNews API, Algolia Search API, and Mercury Web Parser API.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing configuration)
./gradlew assembleRelease

# Run lint checks (all variants)
./gradlew lint

# Run lint on the debug variant only (faster)
./gradlew lintDebug
```

There is no test suite in the project — no unit tests or instrumentation tests exist (`app/src` has only `main`, `debug`, `release`). The leftover `kaptTest(...)` dependencies in `app/build.gradle.kts` are vestigial.

LeakCanary is not a dependency (the old `-Pleak` build flag was removed). Debug builds rely on `StrictMode` (set up in `Application.java`) for leak detection instead.

## Requirements

- JDK 17 (compilation target). The Gradle daemon JVM is pinned to JDK 17 (any vendor) via `gradle/gradle-daemon-jvm.properties`; the foojay toolchain resolver plugin in `settings.gradle.kts` lets Gradle download one if none is installed. Regenerate with `./gradlew updateDaemonJvm --jvm-version=17` (omit `--jvm-vendor` to keep it vendor-neutral).
- Android SDK with compileSdk/targetSdk 36
- Min SDK: 23 (Android 6.0)
- Gradle 9.8.2 (via wrapper, with `distributionSha256Sum` verification)

## Build System

Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Key dependency versions: AGP 9.5.0, Kotlin 2.3.10, Retrofit 3.0.0, OkHttp 5.4.0, Room 2.8.5, Dagger 1.2.5, RxJava 1.3.8. Uses KAPT for annotation processing (Dagger 1 and Room).

### Dependency Update Status (October 31, 2026)

Updates are done in risk-ordered groups, one commit each, verified with `assembleDebug assembleRelease lint` and then device-tested by the user.

- **Group A (done, device-tested successfully):** Room 2.8.4 → 2.8.5, OkHttp + logging-interceptor 5.3.2 → 5.4.0, androidx.browser 1.9.0 → 1.10.0; removed `lifecycle-compiler` (an annotation processor wrongly declared as `implementation`, and unused). OkHttp is held at 5.4.0 because 5.5.0+ requires `compileSdk` 37 (API 37 platform is not installed); bumping `compileSdk` would unlock 5.5.x.
- **Group B (paused, not started):** appcompat 1.7.1 → 1.8.0, Material 1.13.0 → 1.14.0, lifecycle 2.10.0 → 2.11.0. Device-test themes, the item screen's collapsing header/tabs, Snackbars and comments.
- **Group C (paused, not started):** Kotlin + KAPT plugin 2.3.10 → 2.4.x (expected to clear the `Configuration.setVisible` warning).
- **Blocked:** Dagger 1.2.5 and RxJava/RxAndroid 1.x are end-of-line; updating them is a migration, not a version bump.

## Build Warnings

The build currently emits five deprecation warnings that cannot be resolved without a major refactor:

- `android.builtInKotlin=false` is deprecated (AGP default is now `true`)
- `android.newDsl=false` is deprecated (AGP default is now `true`)
- `org.jetbrains.kotlin.android` plugin is deprecated in AGP 9.0+
- `BaseAppModuleExtension` `android {}` accessor is deprecated, replaced by `ApplicationExtension`
- Gradle 9.8's `Configuration.setVisible(boolean)` deprecation, emitted by the Kotlin Gradle plugin 2.3.10 itself (likely fixed by a Kotlin 2.4.x bump)

**Root cause** (first four): AGP 9.0 built-in Kotlin is incompatible with the `kotlin.kapt` plugin. KAPT cannot be removed because Dagger 1.2.5 has no KSP migration path. Resolving these warnings requires replacing Dagger 1.x with Dagger 2 / Hilt and migrating Room annotation processing from KAPT to KSP.

All other AGP deprecation warnings have been resolved (removed `android.usesSdkInManifest.disallowed`, `android.sdk.defaultTargetSdkToCompileSdkIfUnset`, `android.enableAppCompileTimeRClass`, `android.defaults.buildfeatures.resvalues`, `android.r8.optimizedResourceShrinking`; suppressed `excludeLibraryComponentsFromConstraints` sync warnings; removed the lint `htmlReport`/`xmlReport`/`textReport` options, which AGP 9.5 deprecates because reports are always generated).

Lint configuration is in `lint.xml` at the project root. Lint runs with `abortOnError = true`: lint errors fail the build, warnings are reported but do not. `./gradlew assembleRelease lint` currently passes with 0 errors.

`gradle.properties` sets `org.gradle.jvmargs=-Xmx4g`; with Gradle's default 512 MB daemon heap, R8 runs out of memory on `assembleRelease`.

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
- `WebView` (`widget/WebView.java`) — Wraps Android WebView with `HistoryWebViewClient` that manages reload sequences (load `about:blank` → load target URL → clear history). Visibility is set when any non-blank page starts loading while a pending URL exists (handles redirects). Also implements nested scrolling in `onTouchEvent()`: vertical drags are offered to nested scrolling parents (the collapsing `AppBarLayout` in `ItemActivity`) first, and only the remainder is fed to web content; drags the page cannot scroll are reported as unconsumed.
- `CacheableWebView` (`widget/CacheableWebView.java`) — Adds `.mht` web archive caching. Configures WebView settings: JavaScript, DOM storage, third-party cookies, mixed content mode. Uses `LOAD_CACHE_ELSE_NETWORK` when online, `LOAD_CACHE_ONLY` when offline.
- `AdBlockWebViewClient` (`widget/AdBlockWebViewClient.java`) — Intercepts requests via `shouldInterceptRequest()` and blocks ad hosts loaded from `assets/pgl.yoyo.org.txt`. Uses recursive subdomain matching.
- `WebFragment` — Main article viewer. Uses `CacheableWebView` with `AdBlockWebViewClient`. Supports readability mode toggle, PDF viewing via JavaScript bridge, fullscreen mode, and find-in-page.

**Important:** The article WebView must have a fixed (`match_parent`) height and scroll itself. Do not wrap it in a `ScrollView`/`NestedScrollView` with `wrap_content` height: Chromium then has no real viewport height, so pages sized with `vh` units collapse or render off-screen. `WebFragment` disables nested scrolling in fullscreen mode.

**Important:** `HistoryWebViewClient` only delegates `onPageStarted`, `onPageFinished`, and `shouldInterceptRequest` to the wrapped client. Other `WebViewClient` callbacks (e.g. `shouldOverrideUrlLoading`) are NOT delegated.

### Link Handling

Links in comments, story text and user profiles are made tappable by `AppUtils.setTextWithLinks()` (a custom touch listener, not `LinkMovementMethod`):

- `AppUtils.openHackerNewsUrlInternal()` routes `news.ycombinator.com/item?id=…` and `/user?id=…` links in-app (`ItemActivity` / `UserActivity`, started on top so Back returns to the tapped link). Everything else goes to `openWebUrlExternal()` (browser or Custom Tabs; HN links opened through it deliberately exclude this app, e.g. "Open in browser").
- Item links set `ItemActivity.EXTRA_SHOW_IN_STORY`. If the item is a comment, `ItemActivity` walks `getParent()` up to the story, then opens the story with the comment path (`ItemFragment.EXTRA_COMMENT_PATH`, via `ItemPagerAdapter.Builder.setCommentPath()`). `ItemFragment` calls the adapter's `revealPath()` once, **before** `setAdapter()` (it mutates adapter state without notifying), then scrolls to the returned position; the comment is flashed on first bind. Single-page mode expands ancestors to the exact comment; multi-page mode can only show the top-level ancestor. If the parent chain can't be loaded, it falls back to showing the comment as its own thread. Deep links from outside the app don't set the flag.

**Important:** the app targets SDK 36, so Android 11+ package visibility applies. `resolveActivity()` / `queryIntentActivities()` only see apps matching the manifest's `<queries>` (VIEW http/https, SEND text/plain, Custom Tabs service). Add a `<queries>` entry for any new implicit intent that is resolved before launching, or launch it and catch `ActivityNotFoundException` instead. `lint.xml` downgrades `QueryPermissionsNeeded` to informational, so lint won't flag this.

### Reactive Patterns

Uses RxJava 1.x (not 2.x) with schedulers injected via `@Named(IO_THREAD)` and `@Named(MAIN_THREAD)`.

Jetpack ViewModel/LiveData is used in a few places alongside Rx (e.g. `StoryListViewModel` backing `ListFragment`, a `MutableLiveData<Uri>` change notifier in `MaterialisticDatabase`).

### AndroidManifest Highlights

- Custom signature-level permissions for sync and account authentication
- Deep linking: `news.ycombinator.com/item`, `news.ycombinator.com/user`, and `materialistic://` custom scheme
- Sync adapter runs in a separate `:sync` process

## Code Style

- Follow [Android code style](https://source.android.com/source/code-style.html)
- Mix of Java and Kotlin; newer files tend to be Kotlin
- `@PublicApi` — Marks APIs that should be discoverable (SOURCE retention)
- `@Synthetic` — Marks fields/methods with relaxed visibility to avoid synthetic accessor generation

## Before Each Claude Push

After the user has tested the changes successfully, and before pushing, update the "Updated by Claude" date in the About screen to the current date. It lives in the `application_info_text` string in all three locales:

- `app/src/main/res/values/strings.xml`: `Updated by Claude, October 31, 2026`
- `app/src/main/res/values-es/strings.xml`: `Actualizado por Claude, 31 de octubre de 2026`
- `app/src/main/res/values-zh-rCN/strings.xml`: `由 Claude 更新，2026年10月31日`

The same string also links the updated source repo (`github.com/jimandreas/materialistic`) under the original `hidroh/materialistic` link.

## API Keys

Optional build config fields `MERCURY_TOKEN` and `GITHUB_TOKEN` (both empty by default).
