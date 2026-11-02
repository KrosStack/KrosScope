# TaskTide

TaskTide is a small offline-first task tracker with background sync. It is a standalone Gradle project made
of an Android app, a desktop app and the libraries behind them: Kotlin and Java side by side, Kotlin
Multiplatform with Android and desktop targets, XML views and Compose, SQLDelight, Room, Wire, AIDL and a
JNI binding.

The project has its own Gradle wrapper and does not depend on the repository that contains it.

## Modules

| Module | What it contains |
|---|---|
| `:app` | Android application (`demo` and `full` flavors), hosts the screens and binds the sync service |
| `:desktop` | Compose desktop application |
| `:core:model` | Domain model shared by every target (Kotlin Multiplatform: Android + desktop) |
| `:core:legacy` | Older Java formatting and export code mixed with Kotlin (Groovy build script) |
| `:data:db` | Task persistence with SQLDelight (Kotlin Multiplatform) |
| `:data:settings` | User preferences with Room |
| `:sync:protocol` | Wire protocol buffers for the sync exchange |
| `:sync:engine` | Sync logic on top of the database and the protocol |
| `:sync:service` | Android bound service (AIDL) and boot receiver |
| `:feature:tasks` | Task list and detail screens built with XML layouts and view binding |
| `:feature:settings` | Settings screen built with Jetpack Compose |
| `:ui:shared` | Compose Multiplatform components and resources used by Android and desktop |
| `:native:bridge` | JNI checksum binding (C sources under `src/main/cpp`) |
| `build-logic` | Included build with the convention plugins `tasktide.android.library`, `tasktide.kmp.library` and `tasktide.jvm.library` |

Dependency versions live in `gradle/libs.versions.toml`.

## Requirements

- JDK 17
- Android SDK with platform 36 (set `sdk.dir` in `local.properties` or `ANDROID_HOME`)
- Network access for the first build to download dependencies; later builds can run with `--offline`

The wrapper script starts its client JVM with a 64 MB heap. If the client runs out of memory on a busy machine,
export `GRADLE_OPTS=-Xmx256m` before running `./gradlew`.

## Build and test

```
./gradlew assembleDebug :desktop:assemble testDebugUnitTest :app:testDemoDebugUnitTest :app:testFullDebugUnitTest \
  :core:model:desktopTest :data:db:desktopTest :core:legacy:test :sync:engine:test \
  :app:compileDemoDebugAndroidTestKotlin :app:compileFullDebugAndroidTestKotlin
```

The last two tasks only compile the instrumented tests; they need a device to run. Run the desktop app
with `./gradlew :desktop:run`.

The native sources in `:native:bridge` are not part of the Gradle build; the project compiles and its unit
tests run without an NDK.

## Versions

| Component | Version |
|---|---|
| Gradle | 9.5.1 |
| Android Gradle Plugin | 9.3.3 |
| Kotlin | 2.4.20 |
| KSP | 2.3.12 |
| Compose Multiplatform | 1.11.1 |
| AndroidX Compose BOM | 2026.06.01 |
| SQLDelight | 2.4.0 |
| Wire | 7.1.0 |
| Room | 2.8.5 |
| kotlinx.coroutines | 1.11.0 |
| kotlinx.serialization | 1.11.0 |
| AndroidX core / appcompat | 1.18.0 / 1.8.0 |
| AndroidX activity / fragment | 1.12.4 / 1.9.1 |
| AndroidX lifecycle / navigation | 2.10.0 / 2.9.7 |
| AndroidX recyclerview | 1.4.0 |

Android: `compileSdk` 36, `minSdk` 24, JVM target 17.

The newest releases of Compose Multiplatform (1.12), AndroidX core (1.19), lifecycle (2.11) and navigation (2.10)
require `compileSdk` 37, so the project stays on the previous release line of each to keep building against
platform 36.

## License

Apache License 2.0. See `LICENSE`.
