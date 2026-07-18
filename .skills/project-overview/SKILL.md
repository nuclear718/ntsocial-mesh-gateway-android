# Skill: Project Overview & Codebase Map

## Description
Module directory, namespacing conventions, environment setup, and troubleshooting for NTsocial MeshLink Android.

- **Build System:** Gradle (Kotlin DSL). JDK 21 REQUIRED. Target SDK: API 37. Min SDK: API 26.
- **Flavors:** `fdroid` and `google` are both OSS and cloud-runtime-free. The `google` name remains only for existing Google Play publication compatibility.
- **Android-only Modules:** `core:api` (AIDL), `core:barcode` (CameraX + local ZXing decoding). Shared contracts abstracted into `core:ui/commonMain`.

## Codebase Map

| Directory | Description |
| :--- | :--- |
| `app/` | Main application module. Contains `MainActivity`, Koin DI modules, and app-level logic. Uses package `com.ntsocial.meshlink.app`. |
| `build-logic/` | Convention plugins for shared build configuration (e.g., `com.ntsocial.meshlink.kmp.feature`, `com.ntsocial.meshlink.kmp.library`, `com.ntsocial.meshlink.kmp.jvm.android`, `com.ntsocial.meshlink.koin`). |
| `config/` | Detekt static analysis rules (`config/detekt/detekt.yml`) and Spotless formatting config (`config/spotless/.editorconfig`). |
| `docs/` | Architecture docs and agent playbooks. See `docs/kmp-status.md` and `docs/roadmap.md` for current status. |
| `core/model` | Domain models and common data structures. |
| `core:proto` | Protobuf definitions (Git submodule). |
| `core:common` | Low-level utilities, I/O abstractions (Okio), and common types. |
| `core:database` | Room KMP database implementation. |
| `core:datastore` | Multiplatform DataStore for preferences. |
| `core:repository` | High-level domain interfaces (e.g., `NodeRepository`, `LocationRepository`). |
| `core:domain` | Pure KMP business logic and UseCases. |
| `core:data` | Core manager implementations and data orchestration. |
| `core:network` | KMP networking layer using Ktor, MQTT abstractions, and shared transport (`StreamFrameCodec`, `TcpTransport`, `SerialTransport`, `BleRadioInterface`). |
| `core:di` | Common DI qualifiers and dispatchers. |
| `core:navigation` | Shared navigation keys/routes for Navigation 3 using `@Serializable sealed interface` hierarchies. `DeepLinkRouter` for typed backstack synthesis, and `MeshtasticNavSavedStateConfig` with `subclassesOfSealed()` for automatic polymorphic backstack persistence. |
| `core:ui` | Shared Compose UI components (`MeshtasticAppShell`, `MeshtasticNavDisplay`, `MeshtasticNavigationSuite`, `AlertHost`, `SharedDialogs`, `PlaceholderScreen`, `MainAppBar`, dialogs, preferences) and platform abstractions. |
| `core:service` | KMP service layer; Android bindings stay in `androidMain`. |
| `core:api` | Public AIDL/API integration module for external clients. |
| `core:prefs` | KMP preferences layer built on DataStore abstractions. |
| `core:barcode` | Android-only CameraX scanning with local ZXing decoding; no ML Kit or cloud service. |
| `core:nfc` | NFC abstractions (KMP). Android NFC hardware implementation in `androidMain`. |
| `core/ble/` | Bluetooth Low Energy stack using Kable. |
| `core/resources/` | Centralized string and image resources (Compose Multiplatform). |
| `core/testing/` | Shared test doubles, fakes, and utilities for `commonTest` across all KMP modules. |
| `feature/` | Feature modules (e.g., `settings`, `messaging`, `node`, `intro`, `connections`, `firmware`, `wifi-provision`, `widget`). All are KMP except `widget`. Use `com.ntsocial.meshlink.kmp.feature` convention plugin. |
| `feature/wifi-provision` | KMP WiFi provisioning via BLE (Nymea protocol). Uses `core:ble` Kable abstractions. |
| `feature/firmware` | Fully KMP firmware update system: Unified OTA (BLE + WiFi), native Nordic Secure DFU protocol (pure KMP), USB/UF2 updates, and `FirmwareRetriever` with manifest-based resolution. Desktop is a first-class target. |
| `desktop/` | Compose Desktop application. Thin host shell relying on feature modules for shared UI. Full Koin DI graph, TCP, Serial/USB, and BLE transports. Versioning via `config.properties` + `GitVersionValueSource`. |

## Namespacing
- **Standard:** Use the `com.ntsocial.meshlink.*` namespace for project-owned code.
- **Protocol boundary:** Keep generated upstream Meshtastic protobufs under `org.meshtastic.proto`.
- **Application ID:** Use `com.ntsocial.meshlink`.

## Environment Setup
1. **JDK 21 MUST be used** to prevent Gradle sync/build failures.
2. **Android SDK path:** Prefer `ANDROID_HOME`. If the local IDE requires a file, create an untracked
   `local.properties` containing only `sdk.dir=...`; no Maps, Firebase, Crashlytics, Datadog, or
   barcode cloud credentials are needed.

## Workspace Bootstrap (MUST run before any build)
Agents **MUST** perform these steps automatically at the start of every session before running any Gradle task. Do not wait for the user to tell you.

1. **Android SDK:** `ANDROID_HOME` may not be set in agent workspaces. Detect and export it:
   ```bash
   # Check common macOS/Linux locations in order of preference
   if [ -z "$ANDROID_HOME" ]; then
     for dir in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "/opt/android-sdk"; do
       if [ -d "$dir" ]; then export ANDROID_HOME="$dir"; break; fi
     done
   fi
   ```
   All `./gradlew` invocations must include `ANDROID_HOME` in the environment. If the SDK cannot be found, ask the user for the path.

2. **Proto submodule:** `core/proto/src/main/proto` is a Git submodule containing Protobuf definitions. It must be initialized or builds will fail with proto generation errors:
   ```bash
   git submodule update --init
   ```

3. **No cloud bootstrap:** Both Android flavors build without Google Cloud, Maps, Firebase,
   Crashlytics, Datadog, or ML Kit configuration. Do not create dummy cloud credentials.

## Troubleshooting
- **Build Failures:** Check `gradle/libs.versions.toml` for dependency conflicts.
- **Configuration Cache:** Add `--no-configuration-cache` if cache-related issues persist.
- **Koin Injection Failures:** Verify the component is included in `AppKoinModule`.
