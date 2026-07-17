# NTsocial MeshLink Android - Copilot Instructions

NTsocial MeshLink is a GPL-3.0 fork of Meshtastic Android. App identity is `NTsocial MeshLink`,
application ID is `com.ntsocial.meshlink`, and project-owned packages use `com.ntsocial.meshlink.*`.
Gateway/cache/IPC/scheduler features are roadmap items unless concrete code exists.

## Build, Test & Lint

**Requires:** JDK 21, `ANDROID_HOME` set, proto submodule initialized. Use
`JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"` for local validation when tests assert
English resources.

Root `gradle.properties` intentionally uses G1GC because the configured Android Studio JBR 21 does
not support `UseZGC`/`ZGenerational`. Do not restore those flags without verifying the exact JVM.

```bash
# Bootstrap (run once per fresh clone)
git submodule update --init
[ -f local.properties ] || cp secrets.defaults.properties local.properties

# Full local verification (formatting -> lint -> compile -> tests)
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests

# Single module tests (KMP module)
./gradlew :core:data:allTests

# Single module tests (Android-only module like :app)
./gradlew :app:testFdroidDebugUnitTest

# Cross-platform compilation check (no tests)
./gradlew kmpSmokeCompile

# Flavor-specific lint
./gradlew lintFdroidDebug lintGoogleDebug
```

> Both `test` and `allTests` are needed. `allTests` covers KMP modules; `test` covers pure-Android modules.

### Current Build and Release Status

- On 2026-07-15, Gradle Sync and the full local verification command above passed; Google universal
  debug APK packaging also succeeded.
- This confirms compilation, lint/static checks, tests, and debug packaging only. It is not proof of
  Google Play release readiness.
- No Play-uploadable AAB is currently validated or tracked. The Google release trial reached R8 but
  production mapping upload rejected the dummy Firebase configuration. The unchanged official
  release workflow requires the authorized upload keystore and production Google/Firebase/DataDog
  configuration; a fallback debug signature must never be treated as Play-ready.

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
- Flavors: `fdroid` (OSS) and `google` (Maps + DataDog)

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

## Key Conventions

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
