# NTsocial MeshLink Android, Windows & iOS - Copilot Instructions

> Reviewed 2026-09-05 at source `99bc567d9`. [AGENTS.md](../AGENTS.md) is authoritative for
> project rules and current status; [the audit](../docs/development-status-audit-2026-09-05.md)
> contains evidence and gate results. This file is the operational subset, not a second historical log.

## Scope and identity

LiberaNt LLC and the NTsocial team maintain three GPL-3.0-or-later radio companions on the shared
Meshtastic/KMP foundation: Android in `app/`, Windows in `desktop/`, and iOS in `ios/runtime` +
`iosApp/`. Name the affected tracks in plans, validation and release claims; shared changes must
preserve all three. Parent NTsocial products remain separate and own canonical social history.

All three products display `NTsocial MeshLink`. Base Android ID is `com.ntsocial.meshlink`
(flavor/build suffixes apply), Desktop ID is `com.ntsocial.meshlink.desktop`, iOS ID is
`com.ntsocial.meshlink.ios`, and framework ID is `com.ntsocial.meshlink.ios.framework`.
macOS/Linux Desktop identity remains unchanged. Project-owned code uses `com.ntsocial.meshlink.*`;
keep generated upstream protos in `org.meshtastic.proto`.

Android/Desktop normal version configuration is `config.properties` (`1.0.8`, Android code `9`).
iOS has independent Xcode version `1.0.0 (1)`. Read configuration files before changing versions.
Preserve `NOTICE.md`, `THIRD_PARTY_NOTICES.md`, and `docs/copyright-and-attribution.md`.
Use synchronized copyright templates; do not import proprietary parent code, secrets or EULA text.

## Current capability and blockers

- Android and iOS support up to four isolated Meshtastic BLE endpoints in source, with independent
  radio databases/DataStores/Koin/packet ownership and endpoint-scoped UI. App preferences remain
  shared. Switching endpoints returns the current feature to its root.
- Android Gateway v1/v2 remain legacy-primary contracts. v3 fleet status, catalogs/history,
  route tokens, endpoint ledger keys and dispatch plumbing exist, but READY secondary sources
  advertise sends while resolving `SecondaryGatewayRepository`, whose durable sends throw.
  Do not claim production secondary Gateway sending works; preserve primary isolation when fixing it.
- iOS uses the shared Compose shell and a primary-only Apple Gateway, with App Group SQLite,
  shared-Keychain HMAC, exact session/readback gating and durable outbox. Secondary Apple Gateway
  exposure remains intentionally unavailable. Native rows drain independently of gated Gateway rows.
- iOS Channels scanning uses VisionKit and shared endpoint confirmation; Android uses CameraX/ZXing.
  Other iOS scanner surfaces and Desktop scanning remain unavailable. The Swift recognition callback
  has an untested scanner-instance ownership gap; Kotlin token checks alone do not establish complete
  stale-native-callback protection.
- Windows has branding/theme/splash/installer source and shared radio/features, but remains single-radio.
  Fleet, host MQTT/location and scanner capabilities are no-op/unavailable. Windows parent IPC,
  Windows Service and Authenticator integration are not implemented; first-pair/PIN device recovery
  is still unproven by the retained Windows diagnosis.
- Connections is Bluetooth-only on all three UI tracks; preserve backend TCP/USB/Serial capability.
  MeshCore has protocol/UI foundations, with production radio transport still pending.
- Root Detekt currently has **8** findings: BLE 3, domain 1, model 1, network 1, core UI 2.
  `PacketRepositoryImpl` also retains the known live history-clear/epoch publication defect.
- Retained Android two-radio BLE, iOS signed Debug BLE/READY, and native two-way message runs cover
  their dated scenarios only. Do not turn local acceptance into RF or parent canonical delivery,
  or a Debug/device build into store readiness. See the audit for precise evidence boundaries.

## Build and verification

Read `.skills/project-overview/SKILL.md` and bootstrap before Gradle: JDK 21, valid `ANDROID_HOME`,
`git submodule update --init`. Every Gradle invocation must inherit `ANDROID_HOME`; set
`JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"` for English-resource tests.
Root Gradle uses G1GC; do not restore unsupported ZGC options without checking the exact JVM.

```bash
# Implementation baseline (format before checking)
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile \
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache

# Focused KMP tests / Android host tests / Desktop tests
./gradlew :core:data:allTests
./gradlew :app:testFdroidDebugUnitTest
./gradlew :desktop:test

# Modules omitted from the current CI test-shard inventory
./gradlew :core:gateway:allTests :core:meshcore:allTests :core:radio-fleet:allTests \
  :feature:meshcore:allTests :ios:runtime:jvmTest

# Apple runtime source and framework checks (on an appropriate Apple host)
./gradlew :ios:runtime:jvmTest :ios:runtime:compileKotlinIosArm64 \
  :ios:runtime:compileKotlinIosSimulatorArm64 :ios:runtime:compileTestKotlinIosSimulatorArm64 \
  :ios:runtime:linkDebugFrameworkIosSimulatorArm64

# Play candidate: also verify exact signing, bundletool, native alignment and installed artifact
./gradlew :app:verifyGoogleReleaseNoCloudRuntimeDependencies :app:bundleGoogleRelease

# Windows packaging: run on Windows with full JDK 21 including jpackage.exe
./gradlew :desktop:packageReleaseDistributionForCurrentOS
```

`test` and `allTests` cover different module kinds; retain both in the root baseline. Native iOS
**test executable link and run** are disabled by convention; test-source compilation and application
framework linkage do not mean native tests ran. Host-wiring changes also need signing-disabled Xcode
Simulator build/launch and the applicable arm64 framework/host checks in AGENTS.md.

`kmpSmokeCompile` uses a handwritten module inventory that omits `core:radio-fleet` directly, though
it may compile transitively. Current CI test/Kover shards omit five modules listed above and provide
no explicit Xcode host gate. Update settings, root inventory and CI shards together when adding modules.
`DESKTOP_ONLY=true` / `-Pdesktop.only=true` excludes Android/iOS and is not a three-platform gate.

Documentation-only work normally needs link/path, consistency and `git diff --check` validation.
Use a wider read-only gate when auditing status; do not run `spotlessApply` just to inspect source.
Record every failure and skipped task; changed-module Spotless/Detekt must pass without suppressions.

| Intent | KMP module | Android-only flavored module |
| --- | --- | --- |
| Tests | `:module:allTests` or `:module:jvmTest` | `:module:testFdroidDebugUnitTest` |
| Detekt | `:module:detekt` | `:module:detekt` |
| Compile | `:module:compileKotlinJvm` | `:module:compileFdroidDebugKotlin` |

`core:api` and `feature:widget` are unflavored Android modules; use their actual Debug task names.
Do not invent `detektMain` or flavor-specific tasks for KMP modules.

## Implementation contracts

- `commonMain` holds shared logic, ViewModels and Compose UI; no `java.*` or `android.*` imports.
  Use Okio, coroutines and existing platform interfaces. Host lifecycle/permissions/entitlements stay
  in their platform source sets. Koin endpoint factories must preserve qualifiers and `Lazy<T>`.
- Use existing `MeshtasticAppShell`, `MeshtasticNavDisplay`, `NavigationBackHandler`, Navigation 3
  entry scopes and adaptive layout. Test entry-provider registration directly without Activity/Compose
  setup. Do not reintroduce animated navigation glows that continuously render under radio activity.
- Shared strings use Compose resources. Consult `.skills/compose-ui/strings-index.txt` first and run
  `python3 scripts/sort-strings.py` after changes. CMP supports `%N$s`/`%N$d`; pre-format floats with
  `NumberFormatter`. Preserve English, Traditional Chinese and Japanese launch/settings flows.
- Use `safeCatching` in suspend work so cancellation propagates. Preserve exact session/generation,
  database, route, packet and channel ownership through asynchronous work and teardown.
- Android v1 columns/paths/commands remain immutable. New parent integration uses protected Gateway
  Provider/capability/explicit commands, not deprecated AIDL. Outbound NTsocial envelopes use port 256;
  legacy 497 is receive-only. Tokens and private payloads never enter events or logs.
- Stable source identity is opaque and PSK-derived for encrypted channels; numeric slot is only a
  locator. Keep insertion-captured identity, per-database history epochs and endpoint-scoped cursors.
  Preserve idempotency and native-history exclusion rules in AGENTS.md.
- QR/manual changes preserve exact-session readback and neutral `VERIFICATION_PENDING` semantics.
  Built-in channel registration may run automatically within its documented slot/LoRa limits.
  Broader node policy changes need the existing user consent/verification path.
- Both Android flavors stay cloud-runtime-free and map-free. `google` is the Play publication label,
  not permission for Google/Firebase/ML Kit/analytics dependencies. It uses the Meshtastic device API;
  F-Droid falls back to bundled JSON. `PlatformAnalytics` remains a no-op compatibility seam.
- Preserve Android/iOS green butterfly and Windows blue branding, Windows upgrade UUID, three-second
  process-cold-start splash, and existing macOS/Linux identity. Windows installer/device claims require
  Windows QA; shared JVM tests on macOS do not supply it.
- Protect GPL provenance, privacy and secrets. Do not modify the proto submodule without explicit scope.
  Device uninstall loses data; verify exact package and follow user authorization. Prefer preserving
  existing app data and sessions. Never infer authorization to send messages from a read-only audit.

## Handover and release discipline

Update `.agent_memory/session_context.md` at the end of the task. Search by date/topic because its
sections are not globally chronological. AGENTS.md is current rules/status; dated reports and archived
paragraphs retain historical evidence. `.skills/` supplies scoped playbooks, not a replacement snapshot.

Verify before push, and inspect GitHub Actions when pushing is in scope. Do not claim signed release,
Production acceptance, permanent background execution, RF or canonical parent delivery without exact
artifact/device/store evidence. Keep `docs/google-play/` policy drafts and release checklist aligned
with the final app behavior; earlier unsigned artifacts do not establish current live store state.
