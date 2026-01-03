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

## Requirements

- JDK 17 (compilation target)
- Android SDK with API 35
- Min SDK: 21 (Android 5.0)

## Architecture

### Dependency Injection (Dagger 1.x)

The app uses Square's Dagger 1.2.5 (not Dagger 2). Key concepts:

- `Injectable` interface provides `inject()` and `getApplicationGraph()` methods
- `Application` class creates the root `ObjectGraph` in `attachBaseContext()`
- Activities extend from `InjectableActivity` which handles injection in `onCreate()`
- Activity-level graphs use `ObjectGraph.plus(new ActivityModule(this))`

Modules:
- `DataModule` - API clients, database, managers. Uses `@Named` for different ItemManager implementations (`HN`, `ALGOLIA`, `POPULAR`)
- `NetworkModule` - OkHttp configuration with caching interceptors
- `ActivityModule` - Activity-scoped dependencies
- `UiModule` - UI components

### Data Layer

**API Clients** (Retrofit 2.x + RxJava 1.x):
- `HackerNewsClient` - Official HN API
- `AlgoliaClient` - Search functionality
- `ReadabilityClient` - Article parsing via Mercury
- `UserServicesClient` - Authentication

**Database** (Room):
- `MaterialisticDatabase` with tables: `SavedStory`, `ReadStory`, `Readable`
- DAOs: `SavedStoriesDao`, `ReadStoriesDao`, `ReadableDao`

**Caching**:
- `LocalCache` interface with Kotlin implementation in `data/android/Cache.kt`
- OkHttp interceptor-based HTTP caching with offline support

### UI Layer

Base classes hierarchy:
- `ThemedActivity` - Theme switching support
- `InjectableActivity extends ThemedActivity` - Adds DI
- `BaseListActivity extends InjectableActivity` - List screens with search/tabs/FAB

Multi-pane layouts supported for tablets (landscape configurations).

### Reactive Patterns

Uses RxJava 1.x with schedulers injected via `@Named(IO_THREAD)` and `@Named(MAIN_THREAD)`.

## Code Style

- Follow [Android code style](https://source.android.com/source/code-style.html)
- Lint is strict (`abortOnError = true`) - builds fail on warnings/errors
- Mix of Java and Kotlin; newer files tend to be Kotlin
- Custom annotations: `@PublicApi`, `@Synthetic`

## Key Directories

```
app/src/main/java/io/github/hidroh/materialistic/
├── accounts/       # HN account authentication
├── annotation/     # Custom annotations
├── appwidget/      # Home screen widget
├── data/           # API clients, database, managers
│   └── android/    # Android-specific implementations (Cache.kt)
├── ktx/            # Kotlin extensions
├── preference/     # Settings/preference screens
├── widget/         # Custom UI widgets
├── Application.java
├── *Activity.java  # Screen implementations
├── *Fragment.java  # Fragment implementations
└── *Module.java    # Dagger modules
```

## API Keys

Optional API key for Mercury Web Parser can be configured via `MERCURY_TOKEN` build config field.
