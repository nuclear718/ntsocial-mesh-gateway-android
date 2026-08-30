# iOS NTsocial MeshLink UI Parity Phase 1 Implementation Report

> Historical snapshot: this report accurately describes the 2026-08-29 UI-parity phase. Its single-radio limitation
> was superseded by the 2026-08-30 iOS multi-node phase documented in
> `IOS_MULTI_NODE_PHASE1_IMPLEMENTATION_REPORT_2026-08-30.md`.

## Scope and product boundary

This change advances the **iOS NTsocial MeshLink** track in `ios/runtime` and `iosApp`. It replaces the previous
engineering-oriented Connection/Integration shell with the same shared Compose Multiplatform application shell and
Navigation 3 hierarchy used by Android and Desktop. Android remains the behavior and visual-structure reference;
Desktop/Windows remains a shared-KMP compatibility target.

The existing Apple radio and Gateway runtime was not replaced. Kable/CoreBluetooth remains the only iOS Meshtastic
transport owner, and the existing Room, DataStore, exact-session channel/readback, private ledger, App Group mailbox,
Keychain HMAC, and fail-closed Apple Gateway boundaries remain authoritative.

This is the first product-UI parity phase. It does **not** add iOS multi-radio ownership or MeshCore transport.

## Implemented product surface

- Replaced the two-tab iOS shell with the shared adaptive `MeshtasticAppShell`, navigation suite, multi-back-stack,
  background treatment, theme selection, snackbar/alert host, and five top-level destinations: conversations, nodes,
  MeshCore, settings, and connections.
- Reused the shared conversations, node, channel, Bluetooth connections, Wi-Fi provisioning, settings, and MeshCore
  navigation graphs. The MeshCore surface is explicitly a transport-pending roadmap screen; it cannot send or imply a
  connected MeshCore radio.
- Added iOS Koin registrations for the shared feature ViewModels and the Bluetooth-only discovery projection. The first
  Simulator launch exposed a missing `ScannerViewModel` binding; the final source adds an Apple binding plus an explicit
  empty USB scanner, and the clean host launch remains alive.
- Replaced the empty iOS Settings implementation with the real shared radio configuration hierarchy, theme picker,
  homoglyph preference, database cache limit, About/version entries, channel and Wi-Fi routes, and remote-node
  administration header. Unsupported iOS OTA, backup/import/export, and notification controls stay hidden.
- Embedded Apple companion/Gateway readiness inside Settings through a host-owned composition local, preserving the
  existing App Group/Keychain/parent-handoff diagnostics without making the shared Settings module depend on iOS.
- Added shared feature-deep-link queuing from the Swift host into `UIViewModel`/Navigation 3 while keeping the exact
  Apple Gateway `process` handoff distinct from ordinary feature routes.
- Replaced several iOS UI stubs with real Foundation/UIKit behavior: locale-aware date/time and relative-time formats,
  regional measurement selection, clipboard text creation, URL opening, and enum-backed preference contents.
- Kept unavailable iOS file picking, one-shot location, compass, and Android fleet aggregation explicit and fail closed.
  No synthetic sensor, location, multi-radio, or connection state is published.
- Added Japanese translations for the iOS Gateway/readiness surface and filled the Bluetooth empty-state strings in
  Traditional Chinese and Japanese. The final Traditional Chinese Simulator screenshot has no English fallback in the
  first-run Connections content.

## Platform impact

- **iOS:** at this phase's snapshot, the new shared product shell and settings experience sat on top of the then-current
  single-radio Apple runtime.
- **Android:** no navigation/runtime behavior change. Shared enum preference enumeration now uses Kotlin common
  `enumValues`; both Android Debug flavors rebuilt successfully. Traditional Chinese/Japanese Bluetooth strings improve
  the shared resource catalog.
- **Windows/Desktop:** retains the existing single-radio behavior. Desktop tests pass with the shared UI changes.

## Verification

The final source was validated with JDK 21, a valid Android SDK, one Gradle worker, and
`JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"` for English-resource UI tests.

- Baseline iOS runtime JVM/native gate passed before implementation.
- Final focused iOS/Windows run completed 368 actionable tasks in 7m24s: `:ios:runtime:jvmTest`, both iOS Kotlin
  architectures, Simulator Debug and arm64 Release framework links, and `:desktop:test` passed. The runtime suite is
  17/17.
- Final en-US root gate requested `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue`. It completed 2,015 actionable tasks (97 executed, 1,918
  up-to-date). Formatting, both Android Debug assemblies, tests/`allTests`, Desktop/JVM, shared KMP/iOS Simulator
  compilation, cloud-runtime guards, and both Android Debug lints passed. The command was nonzero only for the six
  already-recorded Detekt findings in unmodified BLE (3), domain precise-location sharing (1), model identity (1), and
  network transport (1) sources.
- A first broad run accidentally inherited the Mac's `zh-TW` JVM locale and old Compose UI tests that search for
  English labels failed. Re-running the three affected modules with the repository-required en-US JVM properties passed
  all 121 tasks; no source fix was needed for those failures.
- After the final localization edit, a 749-task targeted replay passed iOS Simulator framework link, iOS runtime tests,
  both Android Debug assemblies, Desktop tests, and resource formatting. The final arm64 Release framework replay then
  passed 161 tasks in 7m31s.
- Final static frameworks are 370 MiB for Simulator Debug and 233 MiB for arm64 Release before application packaging.
- Fresh signing-disabled Simulator Debug Derived Data
  `/tmp/ntsocial-ios-ui-parity-final.qCQEYr` built with quiet exit 0 and no output; the final three-string localization
  correction then rebuilt that same Derived Data incrementally with quiet exit 0 and no output. The final localized App
  installed on `Codex iPhone 17` (`E3249756-57AF-4D9C-AA2B-3332E9309529`), cold-launched as PID 22251, and returned
  the same PID three seconds later. The visually checked Connections screen renders the five-destination shell and
  localized Bluetooth empty state.
- Fresh signing-disabled generic-iphoneos Release Derived Data
  `/tmp/ntsocial-ios-ui-parity-device.ncW63J` built with quiet exit 0 and no output. The bundle remains version
  `1.0.0 (1)`.
- `git diff --check` passes. Kotlin/Native iOS test execution remains disabled by project convention; native evidence is
  compilation/framework/host linkage, not native test execution.

## Known limits and next gates

- At this phase's snapshot, iOS still owned exactly one selected Meshtastic radio. The subsequent 2026-08-30 phase added
  durable per-endpoint Room/DataStore/Koin/session ownership; concurrent physical-radio validation remains a separate
  hardware gate.
- MeshCore is present only as an explicit future/pending UI destination. No MeshCore BLE/TCP/USB transport, session,
  database, send, receive, or bridging claim is made.
- The Node feature brings an existing Coil 3.4.0/Skiko version-alignment warning and Vico linker diagnostics for
  inaccessible `Paint.shader` accessors. Both iOS frameworks and both Xcode host builds succeed, and the Connections
  screen is stable, but node metric charts require dependency alignment and physical-device rendering validation.
- There is no signed archive or physical-device proof for Bluetooth permissions/restoration, App Group/Keychain
  entitlements, parent interoperability, connected-radio administration, LoRa/RF delivery, remote receipt, background
  restoration, TestFlight, or App Store readiness.
- File import/export, CoreLocation phone position, and compass/magnetic-field features remain unavailable/fail closed
  until their Apple lifecycle and permission bridges are implemented.
- Only the first Connections screen received a final visual Simulator check. Feature routes and initial-selection logic
  are covered by tests and framework linkage, but a complete accessibility and per-screen device walkthrough remains a
  release gate.
