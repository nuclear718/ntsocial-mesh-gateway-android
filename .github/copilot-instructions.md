# NTsocial MeshLink Android - Copilot Instructions

NTsocial MeshLink is led and maintained by **LiberaNt LLC and the NTsocial team** as the core
open-source companion app for Android NTsocial. It is a GPL-3.0-or-later fork of Meshtastic Android.
App identity is `NTsocial MeshLink`, application ID is `com.ntsocial.meshlink`, and project-owned
packages use `com.ntsocial.meshlink.*`. Governance does not erase upstream or contributor rights;
follow `NOTICE.md`, `THIRD_PARTY_NOTICES.md`, and `docs/copyright-and-attribution.md`.
Gateway v1 Provider/capability/IPC/cache/channel-provisioning behavior is concrete code. RF scheduler
expansion, node policy, persistent/reliable delivery, MeshCore transport, and remote RF verification
remain roadmap work.

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
```

> Both `test` and `allTests` are needed. `allTests` covers KMP modules; `test` covers pure-Android modules.

### Current Build and Release Status

- On 2026-07-18, the cloud-free baseline passed: the formatting/static/build/test command completed
  in 4m41s (1,569 actionable tasks), and KMP smoke compilation plus both flavor lint tasks completed
  in 1m18s (932 actionable tasks).
- The prior F-Droid arm64 debug APK passed `zipalign -c -P 16` and was clean-installed with the
  NTsocial parent on three Android 16 phones. Parent Provider status/launch, primary screens,
  lifecycle switching, English-keyboard text entry, cross-phone parent sync, and relevant crash/ANR
  logs passed in the no-radio test scope. After removing the map/native dependency path, both current
  arm64 debug APKs pass 16 KB zip alignment and the Google APK's packaged ELF load segments pass the
  0x4000 alignment audit; repeat this on the final signed delivery artifact.
- This confirms compilation, lint/static checks, tests, and debug packaging only. It is not proof of
  Google Play release readiness or remote RF delivery.
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
  Navigation 3 shell unless a UI redesign is explicitly requested.
- Use the established NTsocial butterfly for primary branding. MeshLink launcher, store, splash,
  and in-app variants keep that silhouette and use Meshtastic green `#67EA94` on black.
- Use upstream Meshtastic design patterns when preserving existing Meshtastic screens, but do not
  treat the upstream mountain logo or palette as primary NTsocial branding.
- Known branding debt: `feature/widget/src/main/res/drawable/widget_app_icon.xml` still uses the upstream mountain and
  is rendered by `LocalStatsWidget`; replace it before claiming the current asset set is complete.

### Gateway Roadmap Boundaries

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
