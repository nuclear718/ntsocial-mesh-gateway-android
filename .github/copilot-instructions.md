# NTsocial MeshLink Android & Windows - Copilot Instructions

NTsocial MeshLink is led and maintained by **LiberaNt LLC and the NTsocial team** as two first-class
open-source radio companions: Android `NTsocial MeshLink` in `app/`, and Microsoft Windows
`NTsocial MeshLink` in `desktop/`, intended to serve the separate `NTsocial_Windows` product. It is a
GPL-3.0-or-later fork of Meshtastic Android. Both products display `NTsocial MeshLink`; Android uses
application ID `com.ntsocial.meshlink`, Desktop uses `com.ntsocial.meshlink.desktop`, and
project-owned packages use `com.ntsocial.meshlink.*`. Governance does not erase upstream or
contributor rights; follow `NOTICE.md`, `THIRD_PARTY_NOTICES.md`, and
`docs/copyright-and-attribution.md`.

Android Gateway v1 Provider/capability/IPC/cache/channel-provisioning behavior is concrete code.
Windows branding, installer identity, theme, and cold-start splash are concrete code, but
`NTsocial_Windows` IPC, Windows Service, Authenticator, code signing, and parent-App interoperability
are not implemented. RF scheduler expansion, node policy, persistent/reliable delivery, MeshCore
transport, and remote RF verification remain roadmap work.

## Build, Test & Lint

**Requires:** JDK 21, `ANDROID_HOME` set, proto submodule initialized. Use
`JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"` for local validation when tests assert
English resources.

Root `gradle.properties` intentionally uses G1GC because the configured Android Studio JBR 21 does
not support `UseZGC`/`ZGenerational`. Do not restore those flags without verifying the exact JVM.

```bash
# Bootstrap (run once per fresh clone)
git submodule update --init

# Full local verification (formatting -> lint -> compile -> tests)
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile \
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache

# Single module tests (KMP module)
./gradlew :core:data:allTests

# Single module tests (Android-only module like :app)
./gradlew :app:testFdroidDebugUnitTest

# Cross-platform compilation check (no tests)
./gradlew kmpSmokeCompile

# Flavor-specific lint
./gradlew lintFdroidDebug lintGoogleDebug

# Play publication candidate (still verify signing and Play-installed artifact separately)
./gradlew :app:verifyGoogleReleaseNoCloudRuntimeDependencies :app:bundleGoogleRelease

# Windows/Desktop tests
./gradlew :desktop:test

# Windows release packaging (requires a complete JDK 21 containing jpackage.exe)
./gradlew :desktop:packageReleaseDistributionForCurrentOS
```

> Both `test` and `allTests` are needed. `allTests` covers KMP modules; `test` covers pure-Android modules.

### Current Build and Release Status

- On 2026-07-23, the cloud-free, Bluetooth-only Connections UI baseline passed: the
  formatting/static/build/test command completed in 21m9s (1,589 actionable tasks), and KMP smoke
  compilation plus both flavor lint tasks completed in 2m32s (920 actionable tasks).
- The prior F-Droid arm64 debug APK passed `zipalign -c -P 16` and was clean-installed with the
  NTsocial parent on three Android 16 phones. Parent Provider status/launch, primary screens,
  lifecycle switching, English-keyboard text entry, cross-phone parent sync, and relevant crash/ANR
  logs passed in the no-radio test scope. After removing the map/native dependency path, both current
  arm64 debug APKs pass 16 KB zip alignment and the Google APK's packaged ELF load segments pass the
  0x4000 alignment audit; repeat this on the final signed delivery artifact.
- This confirms compilation, lint/static checks, tests, and debug packaging only. It is not proof of
  Google Play release readiness or remote RF delivery.
- On 2026-07-23, the branded Windows host passed `:desktop:test`, the full cross-platform baseline,
  KMP smoke compilation and Android lint, plus `:desktop:packageReleaseDistributionForCurrentOS`.
  Packaging produced unsigned `NTsocial MeshLink-1.0.0.exe` and `.msi` artifacts with vendor
  `LiberaNt LLC`, menu group `NTsocial`, and upgrade UUID
  `6784A2DD-CE59-518B-AA15-C26302D6FA85`. Coexistence/upgrade was checked from metadata only; actual
  install/upgrade, light theme, 100/150/200% scaling, and tray replay behavior still require manual QA.
- `:app:bundleGoogleRelease` passes R8, Lint Vital, cloud-runtime guards, and AAB packaging. The local
  artifact is unsigned and therefore not Play-uploadable; the release workflow still requires an
  authorized upload keystore and Play Console setup. Neither flavor should require or package Google
  Cloud, Maps, Play services, Firebase, Crashlytics, Datadog, or ML Kit runtime/configuration.
- The current cloud-free artifact is not Production-submission-ready. Remaining gates include the
  location-FGS/API-37 policy fix or feature removal, first-send terms plus in-app UGC report/block,
  stale `analytics_notice` cleanup, final public policy URLs/in-app link, upload signing and Play
  signer pairing, current store assets/Console declarations, and Internal-track device testing.
  Account-specific closed-testing/Production-access requirements also remain external gates.
  Use `docs/google-play/README.md` and `docs/google-play/06-first-play-launch-plan-zh-TW.md` as the
  submission source of truth.

### Gradle Task Naming

| Intent | KMP modules (`core:*`, `feature:*`) | Android-only (`app`, `core:api`, `core:barcode`) |
|--------|--------------------------------------|--------------------------------------------------|
| Run tests | `:module:allTests` | `:module:testFdroidDebugUnitTest` |
| Detekt | `:module:detekt` | `:module:detekt` |
| Compile check | `:module:compileKotlinJvm` | `:module:compileFdroidDebugKotlin` |

Common mistakes:
- `:core:network:detektMain` does not exist in KMP; use `:core:network:detekt`.
- `:feature:connections:testDebugUnitTest` is ambiguous in KMP modules; use `:feature:connections:allTests`.
- `:feature:connections:compileFdroidDebugKotlin` is wrong for KMP; use `:feature:connections:compileKotlinJvm` or `kmpSmokeCompile`.

## Architecture

Kotlin Multiplatform project targeting Android, Desktop (JVM), and iOS. Business logic lives in
`commonMain`; platform shells (`app/`, `desktop/`) wire DI and host UI. Preserve the upstream
Meshtastic radio/service/database/settings foundation while adding NTsocial-specific gateway
behavior in scoped modules.

Treat Android and Microsoft Windows as separate product tracks on the shared KMP foundation.
Plans, status, validation, and release claims must identify the affected track. Changes to shared
resources, UI, navigation, service/database contracts, or features must preserve both hosts.

### Module Layers

| Layer | Modules | Role |
|-------|---------|------|
| Host | `app`, `desktop` | Platform shell, Koin root, theme |
| Feature | `feature/*` | Self-contained screens, mostly KMP, using `com.ntsocial.meshlink.kmp.feature` |
| Core | `core/*` | Shared logic, data, networking, UI components |

### Key Technologies

- UI: Compose Multiplatform + Material 3 Adaptive/Expressive
- Navigation: JetBrains Navigation 3 with `@Serializable` route keys in `core:navigation`
- DI: Koin 4.2+ with K2 compiler plugin
- Networking: Ktor
- BLE: Kable via `core:ble`
- Database: Room KMP
- I/O: Okio
- Build: Gradle Kotlin DSL with convention plugins in `build-logic/`
- Flavors: `fdroid` and `google` are both OSS/cloud-runtime-free. `google` is the Play publication
  path and uses the non-Google Meshtastic project API for device/firmware information; `fdroid`
  intentionally uses bundled JSON fallback. QR/barcode decoding is local ZXing in both flavors.
- `PlatformAnalytics` is an upstream-compatibility seam only. Every Android flavor binds it to
  `NoopPlatformAnalytics`; never add an event-recording or network-backed implementation.

### Source-Set Boundaries

- `commonMain`: business logic, ViewModels, shared UI. No `java.*` or `android.*` imports.
- `androidMain`: Android framework integration only. No business logic.
- `jvmMain` / `jvmAndroidMain`: shared JVM code for Android + Desktop.
- Platform capabilities: prefer interface + DI over `expect`/`actual`.

### Namespacing Boundaries

- New project-owned code uses `com.ntsocial.meshlink.*`.
- Android host identity is `com.ntsocial.meshlink`; Desktop host identity is
  `com.ntsocial.meshlink.desktop`.
- Keep generated upstream Meshtastic protobufs under `org.meshtastic.proto`.
- Do not create new `org.meshtastic.*` or `com.geeksville.mesh` project packages.
- Existing semantic names such as `MeshtasticNavDisplay`, `MeshtasticBleConstants`, and
  `MeshtasticDatabase` may remain when they describe upstream protocol/device/shell behavior.

### Navigation Pattern

Feature navigation graphs are extension functions on `EntryProviderScope<NavKey>` in `commonMain`.
The host shell renders via `MeshtasticNavDisplay`. Use `NavigationBackHandler`, not Android's
`BackHandler`.

Entry-provider assembly tests should directly construct their `NavBackStack` and providers. Do not
launch Robolectric Activity/Compose infrastructure for registration-only assertions; that setup caused
a coroutine-cleanup timeout flake under the full parallel baseline.

## Key Conventions

### Copyright & Attribution

- Use the synchronized `config/spotless/copyright.*` and Detekt templates; LiberaNt's NTsocial
  original work/modifications appear first, while the Meshtastic LLC line is conditional on derived
  portions and the header records the 2026 modification date.
- Never remove applicable upstream GPL, copyright, warranty, MIT, or third-party notices.
- Do not copy the adjacent parent App's proprietary `All Rights Reserved`/EULA text, private
  business logic, assets, credentials, or secrets into this GPL repository.
- Preserve Gradle wrapper, `core/proto`, generated, and third-party file headers instead of forcing
  the project template onto them.

### Strings & Formatting

- All shared strings live in `core/resources/src/commonMain/composeResources/values/strings.xml`.
- Use `stringResource(Res.string.key)`; avoid hardcoded UI strings.
- CMP only supports `%N$s` and `%N$d`; pre-format floats with `NumberFormatter.format()`.
- Run `python3 scripts/sort-strings.py` after adding strings.

### Error Handling

- Use `safeCatching {}` from `core:common` instead of `runCatching {}` in suspend/coroutine code.
  `runCatching` swallows `CancellationException`.

### Dispatchers

- Use `com.ntsocial.meshlink.core.common.util.ioDispatcher`; never use `Dispatchers.IO` directly.
- Inject `CoroutineDispatchers` from `core:di`.

### Build Logic

- Convention plugins include `com.ntsocial.meshlink.kmp.feature`,
  `com.ntsocial.meshlink.kmp.library`, `com.ntsocial.meshlink.kmp.jvm.android`, and
  `com.ntsocial.meshlink.koin`.
- Use `libs.library("alias-name")` string-based lookups, not type-safe accessors, in convention plugins.
- Prefer lazy Gradle configuration with `configureEach`, `withPlugin`, and provider APIs.

### Icons

- Use `MeshtasticIcons` from `core/ui/icon/` instead of `material.icons.Icons`.

### Protos

- `core/proto/` is a read-only git submodule from `meshtastic/protobufs`. Never modify proto files
  unless explicitly assigned upstream protocol/submodule work.

### Design Standards

- Current NTsocial skinning is token-based: non-Dynamic themes use NTsocial indigo, emerald, amber,
  gray surfaces, and mixed monospace typography for compact metadata.
- Preserve `AppTheme`, Dynamic Color behavior, Material 3 Expressive, and the existing adaptive
  Navigation 3 shell unless a UI redesign is explicitly requested. Optional shared theme,
  typography, or brand-painter overrides must preserve the existing no-override behavior.
- Keep the shared first-release Connections UI Bluetooth-only: retain the connection-status card,
  BLE scan/device list, region warning, and disconnect/navigation behavior, but do not restore
  transport filter chips, USB/TCP sections, manual TCP controls, or screen-driven network scanning.
  Preserve USB/TCP discovery, models, transports, handlers, preferences, and tests as backend code.
- Use the established NTsocial butterfly for primary branding. Android launcher, store, splash, and
  in-app variants use Meshtastic green `#67EA94` on black. Windows uses the authorized blue
  butterfly and fiber background documented in `desktop/BRANDING_ASSETS.md`; never swap one
  platform's approved colorway into the other.
- Windows dark mode uses `#5B63EB`, `#3730A3`, `#10B981`, `#F59E0B`, and
  `#0E1420`/`#161E2C`/`#212B3B`, with translucent surfaces and Segoe UI/Cascadia Mono fallbacks.
  Preserve the three-second process-cold-start splash and do not replay it after tray re-show.
- Use upstream Meshtastic design patterns when preserving existing Meshtastic screens, but do not
  treat the upstream mountain logo or palette as primary NTsocial branding.
- Known branding debt: `feature/widget/src/main/res/drawable/widget_app_icon.xml` still uses the upstream mountain and
  is rendered by `LocalStatsWidget`; replace it before claiming the current asset set is complete.

### Gateway Roadmap Boundaries

- The implemented ContentProvider/capability/broadcast Gateway is Android-only. It is not the
  Windows IPC contract.
- Windows is currently an independent Meshtastic desktop client. Before connecting it to
  `NTsocial_Windows`, define an explicit IPC/protocol, authentication, lifecycle, versioning, and
  threat model. Do not import proprietary parent-App code or expose internal database/service
  objects.
- Windows packaging identity is `NTsocial MeshLink`, vendor `LiberaNt LLC`, menu group `NTsocial`,
  application ID `com.ntsocial.meshlink.desktop`, and upgrade UUID
  `6784A2DD-CE59-518B-AA15-C26302D6FA85`. Keep macOS/Linux branding unchanged unless explicitly in
  scope.
- Treat `C:\Users\cth\Documents\GitHub\NTsocial_Windows` as read-only. Copy only explicitly
  authorized brand assets, record provenance and SHA-256 in `desktop/BRANDING_ASSETS.md`, and never
  import secrets, credentials, unrelated data, or proprietary business logic.
- Planned NTsocial overlay transport is `PRIVATE_APP / port 256`; legacy `497` is receive-only.
- NTsocial `channelId` is the logical route; Meshtastic `channelIndex` is the RF lane.
- NTsocial MeshLink must bundle and automatically register the canonical public NTsocial Meshtastic
  channel after node DB readiness. Preserve primary when possible, replace the last secondary when
  full, and apply QR LoRa/RF config only when the radio is unconfigured or `region == UNSET`.
- `rebroadcast_mode = ALL` must be applied with user consent and verification.
- Do not send image, voice, or PTT media bytes over LoRa.
- Do not describe planned gateway behavior as shipped until implemented.

### Branching

- For Codex agent work, use the `codex/` branch prefix by default unless the user or task requires otherwise.
- Confirm remotes before assuming `origin` points to upstream Meshtastic.
- Do not silently rebase, reset, or discard user work.

### Push Workflow

Before push:

```bash
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
```

After push:

```bash
gh pr checks <PR_NUMBER>
# or
gh run list --branch <branch> --limit 3
```

Report CI status only after fetching actual results.

### Multi-Flavor Device Installs

Release variants use the configured base application ID `com.ntsocial.meshlink`; debug variants use
`com.ntsocial.meshlink.fdroid.debug` and `com.ntsocial.meshlink.google.debug`. When switching
installed variants on a device:
- Check the exact installed package before uninstalling.
- Be aware that uninstalling loses onboarding state, permissions, and bonded-device data. Ask before
  uninstalling if the user has an active session.
- After changing native dependencies, verify the target APK with `zipalign -c -P 16` and audit all
  packaged arm64 ELF `PT_LOAD` alignments.

Both MeshLink build types may interoperate with the exact NTsocial debug and release packages only
when their package-specific stable team-debug or approved release signer is pinned. Keep private
keys outside the repository and preserve Provider capability plus sender verification.

## Deeper Guidance

Consult `.skills/` for detailed playbooks:
- `.skills/project-overview/` - Full codebase map and bootstrap
- `.skills/kmp-architecture/` - Source-set rules, expect/actual
- `.skills/compose-ui/` - Adaptive UI, string resources
- `.skills/navigation-and-di/` - Nav 3 & Koin patterns
- `.skills/testing-ci/` - CI architecture, verification matrix
- `.skills/implement-feature/` - Feature development workflow
- `.skills/code-review/` - PR hygiene checklist
- `.skills/speckit/` - Spec Kit SDD workflow, slash commands, constitution
