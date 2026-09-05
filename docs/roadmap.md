# Roadmap

> **2026-09-05 status notice:** This is a historical roadmap. Completed iOS work, Bluetooth-only UI and host no-op capabilities must be checked against current source before planning.
> See [AGENTS.md](../AGENTS.md) and the [current development audit](development-status-audit-2026-09-05.md) for source-backed capability, blockers and validation.

> Last updated: 2026-07-18

Historical priorities for the Meshtastic KMP multi-target effort. For current state and open findings,
see [`AGENTS.md`](../AGENTS.md) and the [September 5 audit](development-status-audit-2026-09-05.md).

## Architecture Health (Immediate)

These items address structural gaps identified in the March 2026 architecture review. They are prerequisites for safe multi-target expansion.

| Item | Impact | Effort | Status |
|---|---|---|---|
| Purge `java.util.Locale` from `commonMain` (3 files) | High | Low | ✅ |
| Replace `ConcurrentHashMap` in `commonMain` (3 files) | High | Low | ✅ |
| Create `core:testing` shared test fixtures | Medium | Low | ✅ |
| Add feature module `commonTest` (settings, node, messaging) | Medium | Medium | ✅ |
| Desktop Koin `checkModules()` integration test | Medium | Low | ✅ |
| Auto-wire Desktop ViewModels via K2 Compiler (eliminate manual wiring) | Medium | Low | ✅ |
| **Migrate to JetBrains Compose Multiplatform dependencies** | High | Low | ✅ |
| **iOS CI gate (compile-only validation)** | High | Medium | ✅ |
| **Commonize utilities** (`formatString`, `SfppHasher`, `CryptoCodec`, `CommonUri`) | High | Medium | ✅ |
| **Centralize metric formatting** (`MetricFormatter`) | Medium | Low | ✅ |

## Active Work

### Desktop Feature Completion (Phase 4)

**Objective:** Complete desktop wiring for all features and ensure full integration.

**Current State (March 2026):**
- ✅ **Settings:** ~35 screens with real configuration, including theme/about parity and desktop language picker support
- ✅ **Nodes:** Adaptive list-detail with node management
- ✅ **Messaging:** Adaptive contacts with message view + send
- ✅ **Connections:** Dynamic discovery of platform-supported transports (TCP, Serial/USB, BLE)
- ✅ **Location UI:** Rendered maps were intentionally removed; coordinates, position logs, distance, and compass remain
- ⚠️ **Firmware:** Fully KMP (Unified OTA + native Secure DFU + USB/UF2); desktop is first-class target
- ⚠️ **Intro:** Onboarding flow (may not apply to desktop)

**Implementation Steps:**

1.  **Tier 1: Core Wiring (Essential)**
    -   Verify every target remains free of cloud map and diagnostics runtimes
    -   Verify all features accessible via navigation
    -   Test navigation flows end-to-end
2.  **Tier 2: Polish (High Priority)**
    -   Additional desktop-specific settings polish
    -   ✅ **Keyboard shortcuts** via `onPreviewKeyEvent` (MenuBar removed)
    -   **Adaptive density & multitasking optimizations** (2026 Desktop Guidelines)
    -   Window management
    -   State persistence
3.  **Tier 3: Advanced (Nice-to-have)**
    -   Performance optimization
    -   Additional privacy and transport-security hardening
    -   Theme customization
    -   Multi-window support

| Transport | Platform | Status |
|---|---|---|
| TCP | Desktop (JVM) | ✅ Done — shared `StreamFrameCodec` + `TcpTransport` in `core:network` |
| Serial/USB | Desktop (JVM) | ✅ Done — jSerialComm |
| MQTT | All (KMP) | ✅ Completed — KMQTT in commonMain |
| BLE | All (KMP) | ✅ Done — Kable in `commonMain` (`BleRadioTransport`) |

### Desktop Feature Gaps

| Feature | Status |
|---|---|
| Settings | ✅ ~35 real screens (fully shared); `DeviceConfig`, `PositionConfig`, `SecurityConfig`, `ExternalNotificationConfig` fully unified into `commonMain` |
| Node list | ✅ Adaptive list-detail with real `NodeDetailContent` |
| Messaging | ✅ Adaptive contacts with real message view + send |
| Connections | ✅ Unified shared UI with dynamic transport detection |
| Metrics logs | ✅ TracerouteLog, NeighborInfoLog, HostMetricsLog |
| Location | ✅ Coordinates, position logs, distance, and compass; no rendered map |
| QR Generation | ✅ Pure KMP generation via `qrcode-kotlin` |
| Charts | ✅ Vico KMP charts wired in commonMain (Device, Environment, Signal, Power, Pax) |
| Debug Panel | ✅ Real screen (mesh log viewer via shared `DebugViewModel`) |
| Notifications | ✅ Desktop native notifications with system tray icon support |
| MenuBar | ✅ Removed — replaced with `onPreviewKeyEvent` keyboard shortcuts (⌘Q, ⌘,, ⌘⇧T, ⌘1-4, ⌘/) |
| About | ✅ Shared `commonMain` screen (AboutLibraries KMP `produceLibraries` + per-platform JSON) |
| Desktop packaging | ✅ Done — Native distribution pipeline in CI (DMG, MSI, DEB). Windows `upgradeUuid` set; macOS signing/notarization wired behind `SIGN_MACOS` env flag; desktop build attestation in release CI. Flatpak packaging maintained externally at [vidplace7/com.ntsocial.meshlink.desktop](https://github.com/vidplace7/com.ntsocial.meshlink.desktop) (includes AppStream metainfo, `.desktop` entry, and JBR bundling); see [PR #4807](https://github.com/meshtastic/Meshtastic-Android/pull/4807) for `flatpakGradleGenerator` integration |
| Android Play delivery | ⚠️ Packaging verified, release incomplete — cloud-free `bundleGoogleRelease` passes, but the local AAB is unsigned; upload-key signing, Play App Signing/certificate pairing, Console declarations, and track testing remain |

## Near-Term Priorities (30 days)

1. **Evaluate KMP-native testing tools** — ✅ **Done:** Fully evaluated and integrated `Mokkery`, `Turbine`, and `Kotest` across the KMP modules. `mockk` has been successfully replaced, enabling property-based and Flow testing in `commonTest` for iOS readiness.
2. **Cloud-runtime guard coverage** — Keep both Android flavors free of Google Cloud, Maps, Firebase, Crashlytics, Datadog, and ML Kit artifacts through dependency and merged-manifest checks.
3. **Store privacy verification** — Reconcile runtime behavior, Play Data safety declarations, foreground-service disclosure, and published policy text for every release artifact.
   - The Play publishing flavor name does not authorize a Google runtime dependency; QR/barcode decoding stays local
     and rendered maps remain intentionally absent.
4. **iOS CI gate** — ✅ **Done:** added `iosArm64()`/`iosSimulatorArm64()` to convention plugins and CI. `commonMain` successfully compiles on iOS.

## Medium-Term Priorities (60 days)

1. **iOS proof target** — ✅ **Done (Stubbing):** Stubbed iOS target implementations (`NoopStubs.kt` equivalent) to successfully pass compile-time checks. **Next:** Setup an Xcode skeleton project and launch the iOS app.
2. **Migrate to Navigation 3 Scene-based architecture** — leverage the first stable release of Nav 3 to support multi-pane layouts. **Investigate 3-pane "Power User" scenes** (e.g., Node List + Detail + Charts) on Large (1200dp) and Extra-large (1600dp) displays (Android 16 QPR3).
3. **`core:api` contract split** — separate transport-neutral service contracts from the Android AIDL packaging to support iOS/Desktop service layers.

## Longer-Term (90+ days)

1. **Platform-Native UI Interop** — 
   - **iOS Camera:** Leverage `AVCaptureSession` wrapped in `UIKitView` to fulfill the local `LocalBarcodeScannerProvider` contract.
   - **Web (wasmJs) Integrations:** Leverage `HtmlView` for required raw DOM elements such as local camera `<video>` while binding the root app via `CanvasBasedWindow`.
2. **Module maturity dashboard** — living inventory of per-module KMP readiness.
3. **Shared UI vs Shared Logic split** — If the iOS target utilizes native SwiftUI instead of Compose Multiplatform, evaluate splitting feature modules into pure `sharedLogic` (business rules, ViewModels) and `sharedUI` (Compose Multiplatform) to prevent dragging Compose dependencies into pure native iOS apps.

## Design Principles

1. **Solve in `commonMain` first.** If it doesn't need platform APIs, it belongs in `commonMain`.
2. **Interfaces in `commonMain`, implementations per-target.** The repository pattern is established — extend it. Prefer dependency injection (Koin) with interfaces over `expect`/`actual` declarations whenever possible to keep architecture decoupled and highly testable.
3. **UI Interop Strategies.** When a Compose Multiplatform equivalent doesn't exist (e.g., Camera), use standard interop APIs rather than extracting the entire screen to native code. Use `AndroidView` for Android, `UIKitView` for iOS, `SwingPanel` for JVM/Desktop, and `HtmlView` for Web (`wasmJs`). Always wrap these in a shared `commonMain` interface contract (like `LocalBarcodeScannerProvider`).
4. **Stubs are a valid first implementation.** Every target starts with no-op stubs, then graduates to real implementations.
5. **Feature modules stay target-agnostic in `commonMain`.** Platform UI goes in platform source sets. Keep the UI layer dumb and rely on shared ViewModels (Unidirectional Data Flow) to drive state.
6. **Transport is a pluggable adapter.** BLE, serial, TCP, MQTT all implement `RadioTransport` and are orchestrated by a shared `RadioInterfaceService`.
7. **CI validates every target.** If a module declares `jvm()`, CI compiles it. No exceptions. Run tests on appropriate host runners (macOS for iOS, Linux for JVM/Android) to catch platform regressions.
8. **Test in `commonTest` first.** ViewModel and business logic tests belong in `commonTest` so every target runs them. Use shared `core:testing` utilities to minimize duplication.
9. **Zero Platform Leaks.** Never import `java.*` or `android.*` inside `commonMain`. Use KMP-native alternatives like `kotlinx-datetime` and `Okio`.
