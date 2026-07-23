# Agent Session Context - Meshtastic Android

## 2026-07-23 - NTsocial butterfly foreground-service notification branding
- Replaced the remaining Meshtastic mountain notification small-icon resource with a dedicated 24dp NTsocial
  butterfly vector. The simplified segmented-wing silhouette is derived from the established NTsocial butterfly
  visual language and remains legible as an Android monochrome notification mask.
- Renamed the resource from `meshtastic_ic_notification` to `ntsocial_ic_notification` and updated the main mesh
  service foreground notification, the expedited keep-alive worker fallback, notification summaries, and the generic
  notification manager. No source reference to the retired mountain icon remains.
- Added the official `#67EA94` NTsocial green as the shared Android notification accent and channel light color. The
  notification drawer may show this accent, while Android/OEM status bars still system-tint small icons (normally
  white); the top-bar glyph shape is now the NTsocial butterfly regardless of that platform-controlled tint.
- Added Robolectric regressions for the posted notification icon/color and every canonical notification channel's
  light color. Targeted `spotlessApply :core:service:testAndroidHostTest` passed.
- Full validation passed with JDK 21, the initialized proto submodule, Android SDK, and English test locale:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache` (`BUILD
  SUCCESSFUL`, 7m7s, 1,589 actionable tasks) and `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug
  --continue --no-configuration-cache` (`BUILD SUCCESSFUL`, 2m39s, 920 actionable tasks). No device/OEM status-bar
  smoke test was performed.

## 2026-07-19 - Google Release configuration-cache signing build fix
- Replaced the remaining `doLast` implementation of
  `verifyGoogleReleaseNoCloudRuntimeDependencies` with the typed build-logic task
  `VerifyNoCloudRuntimeDependenciesTask`. The resolved forbidden-coordinate list is now a declared
  task input, so the task action no longer captures Gradle script objects that configuration cache
  cannot serialize.
- Confirmed `:app:verifyGoogleReleaseNoCloudRuntimeDependencies --configuration-cache
  --configuration-cache-problems=fail` both stores and reuses a configuration-cache entry. Confirmed
  `:app:bundleGoogleRelease` also stores and reuses its entry without configuration-cache problems;
  its `signGoogleReleaseBundle` task ran successfully.
- Full validation passed with JDK 21, Android SDK, and English test locale:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache` (BUILD SUCCESSFUL,
  16m20s, 1,895 actionable tasks).
- This CLI workspace intentionally has no `keystore.properties`, so its local AAB is unsigned when
  run without Android Studio's injected signing inputs. Do not treat that artifact as upload-ready;
  retry Android Studio's Generate Signed Bundle flow with the user's selected upload key after sync.

## 2026-07-19 - Play PNG icon and feature graphic
- Added the Play-ready PNGs under the valid no-density Android resource directory
  `app/src/main/res/drawable-nodpi/`: `ntsocial_meshlink_play_icon_512.png` (512 × 512 RGB PNG, 70,109 bytes)
  and `ntsocial_meshlink_play_feature_graphic_1024x500.png` (1024 × 500 RGB PNG, 509,855 bytes).
- The icon preserves the established NTsocial butterfly silhouette in green on black. The feature graphic identifies
  `NTsocial MeshLink` first and `LiberaNt` second, with an original green optical-fiber/radio-network background;
  it contains no map, real location, personal data, third-party logo, or unimplemented feature claim.
- Used the current project green launcher foreground as the exact butterfly source and an ImageGen-created original
  background; both final artifacts are 24-bit PNGs. `:app:verifyGoogleReleaseNoCloudRuntimeDependencies
  :app:bundleGoogleRelease --no-configuration-cache` passed with JDK 21 (BUILD SUCCESSFUL, 2m44s).
- A connected authorized `SM-S9280` phone and matching Google arm64 debug APK are available for authentic store
  screenshots, but screenshots were intentionally not generated or captured in this task because they must show
  truthful current UI and device installation/interaction requires user confirmation.

## 2026-07-19 - Explicit manual Android version configuration
- Replaced the normal local Git-derived version defaults with the two explicit, user-editable root settings
  `VERSION_CODE=1` and `VERSION_NAME=1.0.0` in `config.properties`. Android Studio and Desktop builds now resolve
  those values by default; injected Gradle/CI and environment overrides retain higher precedence for automation.
- Removed the flavor-specific version-name rewrite, so the Google release manifest now preserves the exact configured
  user-visible name instead of appending `(<code>) google`. Updated the Maven publishing fallback and release process
  documentation/workflow references from the retired `VERSION_NAME_BASE` key to `VERSION_NAME`.
- Kept `VERSION_CODE_OFFSET` only as a documented legacy fallback for builds that intentionally omit
  `VERSION_CODE`; existing release CI continues to inject its calculated values explicitly and is not changed.
- Validation passed with JDK 21, Android SDK, and English test locale: full baseline
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache` (18m38s,
  1,589 actionable tasks); `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug --continue
  --no-configuration-cache` (1m20s, 920 tasks); and
  `:app:verifyGoogleReleaseNoCloudRuntimeDependencies :app:bundleGoogleRelease --no-configuration-cache`
  (1m58s, 753 tasks). Bundletool validation passed and the generated `googleRelease` AAB manifest reports
  `versionCode=1` and `versionName=1.0.0`.

## 2026-07-19 - Google Play stewardship and open-source copy audit
- Audited and revised all ten `docs/google-play/` submission drafts after the LiberaNt-first repository copyright
  migration. Every document now consistently identifies LiberaNt LLC as publisher and LiberaNt LLC / the NTsocial
  team as the lead developer, integrator, and ongoing maintainer of NTsocial MeshLink.
- Reworked the Traditional Chinese store listing so its first product identity is the LiberaNt-developed open-source
  companion for Android NTsocial, followed by Meshtastic radio compatibility. The full description now names the
  protected Gateway, cross-App trust, envelope/channel boundary, KMP maintenance, and release work as major
  LiberaNt/NTsocial contributions.
- Added a canonical public-identity and provenance statement to the Play work-package README, App-content/reviewer
  notes, launch plan, release checklist, privacy policy, terms, and community guidelines. It claims LiberaNt
  copyright only in NTsocial original work and copyrightable modifications, preserves GPL-3.0-or-later freedoms,
  retains upstream/contributor rights, and disclaims official Meshtastic/MeshCore sponsorship or endorsement.
- Kept all existing Production blockers intact: location FGS/minimum-scope work, first-send terms acceptance,
  in-App UGC reporting and effective block/ignore, policy hosting and App URLs, final signing, signer pairing,
  cloud-free device testing, and Console/account requirements remain incomplete until separately verified.
- Verified Play field sizes: title 17/30 characters, short description 62/80, full description 1,526/4,000, and
  release notes 166 characters. All 22 local Markdown links resolve; code fences are balanced; `git diff --check`
  passes. With JDK 21 and the initialized proto submodule, `spotlessCheck detekt --continue
  --no-configuration-cache` passed in 1m18s (168 actionable tasks).

## 2026-07-19 - LiberaNt-first copyright and attribution audit
- Audited the fork history, the adjacent NTsocial parent repository, the MeshCore integration commits, and 2,090
  tracked files. The repository now states prominently that LiberaNt LLC leads NTsocial MeshLink development and
  maintenance and that MeshLink is the open-source companion/gateway for the NTsocial Android App.
- Replaced the blanket Meshtastic-only Spotless/Detekt header with a dual-attribution GPL header: LiberaNt LLC for
  NTsocial original work and modifications, and Meshtastic LLC only for Meshtastic-derived portions where present.
  Migrated 1,323 files to explicit LiberaNt notices; 1,325 files carry GPL SPDX and Meshtastic-derived notices.
- Added `NOTICE.md`, `THIRD_PARTY_NOTICES.md`, and `docs/copyright-and-attribution.md`; rewrote the main governance
  documents and documented the pinned MeshCore, meshcore.js, and meshcore_py sources. Exact MIT license texts for
  the two vendored/client-derived MeshCore libraries are preserved in the third-party notices.
- Deliberately did not copy the parent App's proprietary `All Rights Reserved` terms into this GPL repository.
  Contributor copyright remains with contributors unless separately assigned, while project stewardship and
  release authority are documented as LiberaNt-led. Root `LICENSE`, wrappers, and `core/proto` were not modified.
- Validation passed: `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue
  --no-configuration-cache` completed successfully in 17m (1,589 actionable tasks), and `kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache` completed successfully in 2m09s
  (920 actionable tasks). Final scans found no source-template `$YEAR`, no missing AIDL notices, no corrupt docs,
  no broken local Markdown links, no `core/proto` diff, and a clean `git diff --check` result.

## 2026-07-19 - Android Studio Kotlin compiler crash fixed and interrupted caches recovered
- Diagnosed the red `:core:repository:compileKotlinJvm` failure as a Kotlin 2.3.21 compiler-internal concurrency
  crash, not an application source error: FIR metadata serialization and asynchronous JVM code generation threw
  `ArrayIndexOutOfBoundsException` while every Kotlin JVM task was forced to use one backend thread per CPU core.
- Removed the global `-Xbackend-threads=0` advanced compiler argument from `KotlinAndroid.kt`. Gradle module/task
  parallelism remains enabled, while Kotlin JVM compilation now uses its stable default single backend thread.
- The abnormal computer interruption left two Gradle 9.5 transform locks with an invalid protocol byte. Moved only
  their exact transform workspaces and lock files to the recoverable quarantine
  `C:\Users\cth\.gradle\cache-quarantine-20260719-0658`; Gradle also isolated one corrupt local build-cache entry and
  rebuilt it. No project source or user data was deleted.
- Full baseline `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue
  --no-configuration-cache` passed in 3m32s (1,589 actionable tasks). KMP/flavor validation `kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache` passed in 1m50s (920 actionable
  tasks).
- Reopened Android Studio 2026.1.2, completed project sync, and ran Build Project from the IDE. Its Build Output shows
  `:app:assembleGoogleDebug` and `BUILD SUCCESSFUL in 2m 38s` (449 actionable tasks); only non-blocking yellow warnings
  remain.

## 2026-07-18 - Android Studio configuration-cache manifest guard fixed
- Replaced the untyped `doLast` manifest verification closure with the typed build-logic task
  `VerifyNoCloudRuntimeComponentsTask`. Its variant name, forbidden manifest entries, and merged manifest are now
  declared Gradle inputs, so no task action captures the AGP variant object.
- No application manifest content or forbidden-component policy changed. The existing fdroid/google finalizer wiring
  still runs the same cloud-runtime manifest checks after assembly and bundle tasks.
- `:app:assembleGoogleDebug --configuration-cache --configuration-cache-problems=fail` stored a configuration-cache
  entry and an exact rerun reused it successfully. This verifies the Android Studio failure's two serialization
  problems are fixed without disabling configuration cache.
- The expanded baseline `spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue --no-configuration-cache` passed in 15m29s (1,829 actionable tasks).
- Release-path verification `:app:verifyGoogleReleaseNoCloudRuntimeDependencies :app:bundleGoogleRelease
  --no-configuration-cache` passed in 2m39s (753 actionable tasks), including
  `verifyGoogleReleaseNoCloudRuntimeComponents`. This is artifact validation only; signing/Play-readiness status is
  unchanged.

## 2026-07-18 - Android Studio configuration-cache build failure diagnosed
- The Android Studio Build Output failure at 20:13 was not a Kotlin/Java compilation error. The generated Gradle 9.5
  configuration-cache report identified two serialization problems on
  `:app:verifyGoogleDebugNoCloudRuntimeComponents`.
- `app/build.gradle.kts` registers that verification task inside `androidComponents.onVariants` and its task action
  directly captures the AGP `variant` object via `variant.name`. Configuration-cache storage therefore traverses the
  Android variant graph and encounters an unsupported `JavaCompile` task plus a `DefaultLegacyConfiguration` (the
  latter incidentally attempts `fdroidDebugCompileClasspath` resolution).
- `gradle.properties` enables configuration cache globally. The immediate workaround is to run the desired Gradle task
  with `--no-configuration-cache`, as used by the current passing validation baseline. A durable repair should make the
  verification task configuration-cache-safe by snapshotting primitive/provider inputs or using a typed task instead
  of capturing the AGP variant object. No production source or Gradle fix was changed during this diagnosis.
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

## 2026-07-18 — AGENTS guide reconciled with the cloud-free code and Play submission state
- Audited the root `AGENTS.md` against the current source, artifacts, Google Play document packet, and prior validation
  evidence. Corrected three-phone/parent interoperability wording so the pre-cloud-removal device run is treated as
  regression evidence only; the current cloud-free/no-map artifact still needs its own device and Internal-track smoke.
- Split Play build success from submission readiness. The unsigned AAB proves R8/Lint/guard/packaging only, while
  location FGS/API-37 behavior, UGC terms/report/block safeguards, stale localized `analytics_notice` text, final policy
  URLs, upload signing/Play signer trust, current assets/Console declarations, and account/track testing remain gates.
- Added durable architecture rules for cloud-runtime exclusion, conservative Play Data safety disclosure, location FGS,
  UGC, store metadata/policy drafts, and the no-op `PlatformAnalytics` compatibility seam. Clarified that the `google`
  flavor's Meshtastic project API is not a Google service and that F-Droid intentionally uses bundled JSON fallback.
- Recorded the active upstream-mountain widget icon and unused legacy splash vector as branding debt, renamed the native
  rule to 16 KB page compatibility, and expanded the required KMP/flavor/Play release verification commands.
- Synchronized `.github/copilot-instructions.md` as required by `AGENTS.md`. Structural tag checks, referenced-path
  checks, stale-phrase scans, and `git diff --check` passed; no Gradle run was needed for this documentation-only task.

## 2026-07-18 — Google Play submission docs rebaselined for the cloud-free first release
- Rewrote the complete `docs/google-play/` submission packet around the current cloud-free Android runtime. The manual
  first-upload path no longer asks for GCP, Cloud billing, Maps, Firebase, Crashlytics, Datadog, ML Kit, or a Play
  service-account key. It still truthfully requires a verified Play developer account, upload signing, Play App Signing,
  Console declarations, store assets, Internal-track installation, and any account-specific testing gate.
- Replaced stale store copy and policy drafts with Traditional Chinese-first copy for the current no-map, local-ZXing,
  no-telemetry build. Legacy Fastlane metadata and screenshots are explicitly barred from the first upload because they
  still describe maps, analytics, or the official Meshtastic app.
- Corrected the Data safety baseline: no publisher analytics/crash backend does not mean `Collect = No`. Mesh messages,
  names/IDs, optional location, and other user-directed transports leave the Android device, so the draft conservatively
  uses `Collect = Yes`, `Encryption = No`, optional/App functionality types, and requires path-by-path evidence before
  relying on a `Shared = No` exception.
- Documented two source-confirmed Production blockers rather than hiding them in Console prose: the service currently
  adds the location FGS type whenever location permission exists, and the app has not yet proven first-send terms
  acceptance plus in-app UGC reporting/blocking safeguards. Target SDK 37 also requires a deliberate Android 17
  minimum-scope/location-button decision before submission.
- Final documentation audit also found unused localized `analytics_notice` resources that still describe the removed
  Firebase/Crashlytics/Datadog flow. They are not referenced by Kotlin and do not restore those SDKs, but must be removed
  or rewritten before the final AAB so packaged policy text cannot contradict the cloud-free release.
- Synchronized `BUILD_LOGIC_CONVENTIONS_GUIDE.md`, `kmp-status.md`, and `roadmap.md` with the removed map module,
  cloud-runtime guards, unsigned AAB status, and remaining Play delivery gates. Local Markdown links, code fences,
  listing field lengths, and `git diff --check` were validated; no Gradle build was needed for this documentation-only
  follow-up.

## 2026-07-18 — Cloud runtime and rendered maps removed; unsigned Google release pipeline passes
- Implemented the user's decentralized first-release boundary across both Android flavors: removed Google Cloud/Maps,
  Google Play services location, Firebase/Crashlytics, Datadog, ML Kit, osmdroid/GeoPackage, their Gradle plugins,
  credentials/configuration, mapping uploads, CI secrets, manifests, and runtime dependencies. `googleRelease` remains
  only as the existing Play publishing task name.
- Deleted the rendered-map feature, map tab/routes/deep links, inline maps, traceroute maps, map providers/preferences,
  Android Auto map metadata, and map-only controls. Node coordinates, distance, compass, position logs/CSV, Android
  platform location, Meshtastic GPS forwarding, and the user-controlled Meshtastic MQTT/radio location preference remain.
- Removed analytics/crash-report onboarding and Settings UI, analytics preferences/install ID, toggle use case, and
  platform implementations. The retained platform analytics boundary is an explicit no-op for upstream-compatible call
  sites and never records, stores, or transmits events.
- Replaced both flavor-specific ML Kit scanners with one shared on-device ZXing analyzer. ZXing's historical Java
  namespace is `com.google.zxing`, but it is the local open-source decoder and does not use a Google service or network.
- Added release dependency and merged-manifest guards for both flavors. They reject Google Play services, Firebase,
  Maps, ML Kit, Datadog, data transport, advertising/AdServices, and cloud runtime components if reintroduced.
- Updated Google Play docs, privacy/data-safety drafts, CI/release workflows, Fastlane, public project docs, and agent
  guidance to reflect the cloud-free runtime. Manual first upload does not need a Play service-account key; Play itself,
  Play App Signing, and Android Vitals remain Google-controlled distribution infrastructure.
- Fixed an existing App Bundle blocker: APK ABI splits now disable automatically for every `bundle*` invocation while
  remaining enabled for APK/F-Droid tasks. `:app:bundleGoogleRelease` then passed R8, Lint Vital, cloud guards, and AAB
  packaging (`BUILD SUCCESSFUL` in 2m23s; 706 actionable tasks).
- The generated `app/build/outputs/bundle/googleRelease/app-google-release.aab` is 25,533,958 bytes with SHA-256
  `39B2D41A07F5BBB687D3070B0BC200FACF8FEC7E5F4591627AF3A8F0DD03C511`, but `jarsigner` confirms it is unsigned
  because no upload keystore is configured. It is a release-pipeline validation artifact, not Play-uploadable.
- Full validation passed with JDK 21: `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue
  --no-configuration-cache` in 4m41s (1,569 actionable tasks), followed by `kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue --no-configuration-cache` in 1m18s (932 actionable tasks).
- Final artifact audit found zero forbidden cloud/runtime strings across the merged Google release manifest and all
  three AAB DEX files. Both current arm64 debug APKs pass `zipalign -c -P 16`; the Google arm64 APK's five ELF libraries
  all have 0x4000-aligned `PT_LOAD` segments.
- Remaining Play gates are a real upload keystore, Play App Signing and final signer synchronization with the NTsocial
  parent trust rules, a current cloud-free artifact device smoke test, store assets and Console policy declarations,
  Internal-track testing, and any account-specific closed-testing requirement. No GCP billing, Maps key,
  `google-services.json`, Firebase project, Datadog token, or ML Kit setup is required.

## 2026-07-17 - GitHub Desktop stale index-lock recovery
- GitHub Desktop could not commit because `.git/index.lock` was a zero-byte stale file left from 21:49 local time.
  There were no active merge, rebase, cherry-pick, revert, bisect, or sequencer operations and no unrelated Git process;
  the lock was moved recoverably to the Windows temp directory before any index write.
- A fresh `git fetch origin --prune` confirmed `main` and `origin/main` had zero divergence before publication. All 26
  working-tree files belong to the completed Google Play launch-document and advertising-stack removal scope; they were
  staged explicitly, with zero unstaged files and a passing `git diff --cached --check`.
- The LF-to-CRLF notices shown by GitHub Desktop are informational checkout-conversion warnings, not the commit failure.
  GitHub CLI is not installed, so this direct `main` synchronization uses ordinary Git and the existing GitHub Desktop /
  Git Credential Manager authentication rather than a CLI-created pull request.

## 2026-07-17 - Advertising stack removed and Play document pack centralized
- Centralized all nine Google Play launch documents under `docs/google-play/`; the repository root no longer contains
  separate privacy, terms, or community-guidelines copies. Local Markdown links were checked with zero broken targets.
- Removed the direct Firebase Analytics dependency, catalog alias, Analytics consent/event calls, and Analytics-specific
  manifest metadata. This also removes Play Services Measurement, Ads Identifier, Privacy Sandbox Ads, AppMeasurement,
  AdServices permissions, and Install Referrer permission from the Google release graph and merged manifest.
- Retained Firebase Crashlytics and Datadog as optional, user-controlled diagnostics only. General custom events now use
  the existing Datadog RUM pipeline; the privacy notice and Play Data safety/Advertising ID answers were synchronized.
- Added a Google-variant merged-manifest verification task that fails assemble/bundle if advertising identifiers,
  AdServices, Install Referrer, `android.ext.adservices`, or AppMeasurement reappear.
- The complete JDK 21 baseline passed twice. The final clean run included `spotlessApply spotlessCheck detekt
  assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug
  :app:processGoogleReleaseMainManifest :app:verifyGoogleReleaseNoAdvertisingComponents --continue
  --no-configuration-cache` (`BUILD SUCCESSFUL` in 1m11s; 1,962 actionable tasks). A fresh Google release runtime
  dependency scan and merged-manifest scan both reported zero forbidden matches; `git diff --check` also passed.
- No production AAB was built, signed, uploaded, or submitted. A real upload keystore, authorized production
  Crashlytics/Datadog configuration, final Play App Signing trust synchronization, and App Bundle Explorer verification
  remain required; nothing was staged, committed, or pushed.

## 2026-07-17 - Google Play launch document pack and policy audit
- Created a Traditional Chinese Google Play submission pack under `docs/google-play/`, including public-facing
  `PRIVACY_POLICY.md`, `TERMS_OF_USE.md`, and `COMMUNITY_GUIDELINES.md`. Updated the zh-TW Fastlane listing,
  release notes, support links, and the base/zh-TW in-app analytics and privacy strings. The parent App's stale,
  proprietary privacy/EULA text was not copied because it conflicts with MeshLink's actual SDKs, behavior, and GPL-3.0
  status. `LiberaNt LLC` and `huangct_2025@liber-ant.com` remain candidate publisher/contact values that the Play
  account owner must confirm.
- Audited the actual Google release manifest and runtime behavior. The merged manifest currently contains Advertising
  ID and AdServices permissions through Play Services Measurement, and the connected-device service declares both
  connected-device and location foreground-service types. Location FGS activation and the current prominent disclosure
  do not yet match the desired store declaration. These are submission blockers, not documentation-only concerns.
- Play release remains blocked on a real upload keystore and authorized production Google/Firebase/DataDog settings;
  the current release path can fall back to debug signing. Play App Signing's final signer must also be synchronized
  with MeshLink/parent package-and-certificate trust before production interoperability can be claimed.
- The App packages roughly 40 Compose locale directories and 39 Fastlane locale directories, so it is not currently
  zh-TW-only. Non-English/non-zh-TW analytics notices remain stale upstream text. For the first listing, either audit
  every advertised locale or package/list only the verified languages. Release builds do not expose Demo Mode outside
  Firebase Test Lab. Store assets, UGC terms acceptance/reporting, location disclosure implementation, and any applicable
  new-personal-account closed-testing eligibility remain open gates.
- Validation ran the expanded local baseline with JDK 21 and completed Spotless, Detekt, debug assembly, KMP smoke
  compilation, and both F-Droid/Google lint tasks. The combined command failed only on one nondeterministic
  `ScannerViewModelTest.connectionProgressText reflects connectionProgress` occurrence; a forced targeted rerun and a
  subsequent `:feature:connections:allTests` run both passed. Markdown local links, Play field lengths, XML parsing, and
  `git diff --check` were also verified.
- No Play-ready AAB was produced, no production credentials were added, and nothing was pushed, committed, or submitted
  to Play Console. All documentation and metadata changes remain in the working tree for user review.

## 2026-07-17 - Three-phone stabilization, parent interoperability, and final local acceptance
- Repaired Android 16 KB native-page compatibility by pinning `mil.nga.geopackage:geopackage-android` 6.7.5 on the
  F-Droid osmdroid dependency path. The arm64 debug APK passes `zipalign -c -P 16`; all packaged arm64 ELF load
  segments were audited at 0x4000 alignment. Do not remove the override without repeating both archive and ELF audits.
- Repaired debug parent pairing: a debuggable MeshLink host now accepts only `com.ntsocial.android.debug` sharing a
  signer digest with the host, while release trust remains pinned. Removed the stale machine-specific debug signer
  resource and added focused verifier tests. Unauthorized shell Provider access remains rejected.
- Repaired two parent-App edge cases in the adjacent `NTsocial_release` worktree: attachment transfer now treats
  `UNAVAILABLE` and `CONFLICT` receipts as terminal, and legacy sync Bloom filters use JVM-safe Base64 helpers. The
  parent `:app:testDebugUnitTest :app:assembleDebug` validation passed with 477 unit tests.
- Replaced the registration-only `NavigationAssemblyTest` Robolectric/Compose Activity launch with direct Navigation 3
  provider construction. Five forced targeted rounds passed (1/1 each, roughly 1.7-2.4s), eliminating the prior
  one-minute `UncompletedCoroutinesError` cleanup flake.
- Final MeshLink validation passed:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue --no-configuration-cache` (`BUILD SUCCESSFUL` in 10m13s; 1,951 actionable tasks).
  The final XML report set contains 2,439 tests, zero failures, and zero errors.
- Fixed MeshLink and parent debug APKs were clean-installed on three Android 16/API 36 arm64 phones: Samsung SM-S9080
  (`R5CT30QMRTY`), Samsung SM-S9280 (`R5CWC4KNTRL`), and OPPO CPH2695 (`TWBYJJRWSGHIGU55`). Onboarding and all primary
  MeshLink destinations opened; parent status correctly reported MeshLink preparing the radio channel; explicit launch,
  repeated foreground/background switching, English-keyboard text entry, and parent-local/BLE cross-phone sync passed.
  Relevant logs had zero crash, ANR, fatal, Provider trust, or app-process failure signals.
- No Meshtastic/MeshCore node was available in this run. Therefore no radio connection, LoRa transmission, RF reception,
  remote receipt, or MeshCore transport was tested or implied. Prior connected-radio queue acceptance still proves only
  the local queue/Gateway boundary. Play upload also remains blocked on a real upload keystore and authorized production
  Google/Firebase/DataDog configuration; debug device success is not a Play-ready AAB claim.
- The main and parent worktrees intentionally remain uncommitted for user review. Root `AGENTS.md` and the Copilot quick
  reference were synchronized with these current build, packaging, interop, testing, and release boundaries.

## 2026-07-17 - MeshCore UI aligned with Android NTsocial visual language
- Used the local `NTsocial_release` Android implementation as the visual source of truth, specifically its theme,
  shell/header, people list, private chat, message bubble, and composer components. The non-dynamic MeshLink palette
  already exactly matched NTsocial indigo `#4F46E5`, emerald `#10B981`, amber `#F59E0B`, and the corresponding surfaces.
- Added feature-local `MeshCoreNtsocialVisualTheme` primitives with NTsocial's exact monospace typography metrics,
  standard Material shapes, elevated header treatment, uppercase section labels, 16x10 list rows, and 28dp identity
  icons. The boundary intentionally preserves the host theme and Dynamic Color contract instead of changing global or
  Meshtastic UI behavior.
- Reworked the independent MeshCore home, directory, radio/settings, and conversation screens to match NTsocial's
  compact information hierarchy. Conversations now use 78%-width asymmetric 16dp/4dp bubbles, NTsocial semantic
  container colors, aligned metadata, and the same compact composer structure while remaining truthfully read-only.
- Added `MeshCoreNtsocialVisualsTest` to lock the typography contract, updated English/Traditional Chinese pending-
  transport wording, regenerated the string index, and documented the visual boundary in `docs/meshcore-integration.md`.
- Targeted MeshCore formatting, Detekt, JVM/Android/Desktop compile and tests passed. The first full parallel baseline
  encountered one unrelated Desktop notification-test timeout; its isolated retry passed, and the complete baseline
  then passed on retry: `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile --continue
  --no-configuration-cache` (`BUILD SUCCESSFUL in 1m 5s`, 1,632 actionable tasks).
- The Desktop host launched successfully for a visual run, but Windows UI automation could not capture the window
  because `GetCursorPos` returned access denied. No screenshot-based visual claim was made. MeshCore transport remains
  pending, and no Meshtastic radio/service/database/settings internals or Gateway v1 contract were changed.

## 2026-07-17 - Independent MeshCore UI and Companion protocol foundation
- Added isolated `core/meshcore` and `feature/meshcore` KMP modules. The former owns MeshCore-only domain models and a
  bounds-checked Companion Radio Protocol codec; the latter owns a StateFlow store, Koin ViewModel, dedicated messages,
  contacts/channels, radio/settings panels, and conversation screen. No Meshtastic radio/service/database/settings
  implementation or Gateway v1 contract was changed.
- Added a sixth MeshCore top-level destination, a serializable Navigation 3 graph/conversation route, `/meshcore` deep
  link, Android/Desktop graph assembly, Koin wiring, icons, and English/Traditional Chinese resources.
- Official reference snapshot used on 2026-07-17: `meshcore-dev/MeshCore`
  `219812b9f136744c3478908e9487afd0d6031b53` (source identifies Companion v1.16.0), `meshcore-dev/meshcore.js`
  `bbe1f9301b801cbd48a053687f16eea9634634cd`, and `meshcore-dev/meshcore_py`
  `5bac3573b51c4298062881885b6d15a994109076`. The codec covers the official Nordic UART UUIDs, app target protocol 3,
  176-byte frames, contacts/channels, device/self/radio state, message sync, direct/channel text and v3 signal metadata.
- Added protocol codec tests, state-store tests, route serialization/parity, deep-link, Android/Desktop assembly, and
  top-level parity coverage. The full local baseline passed with JBR 21, Android SDK and English locale:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile --continue --no-configuration-cache`
  (`BUILD SUCCESSFUL in 18m 9s`, 1,632 actionable tasks).
- Truthful status: this phase establishes a real protocol/UI boundary but does not yet implement MeshCore BLE/USB/TCP
  transport, scanning/connection lifecycle, settings writes, message sending, persistence, background sync, parent-app
  IPC, or two-radio RF validation. UI text and `docs/meshcore-integration.md` explicitly identify transport as pending.

## 2026-07-15 - Build and release status synchronized into agent guidance
- Updated root `AGENTS.md` with the verified G1GC/JBR compatibility rule, the successful 2026-07-15 full baseline and
  Google debug packaging evidence, and the explicit boundary between local compile success and Play release readiness.
- Recorded that no Play-uploadable AAB is validated or Git-tracked, that the Google release trial reached R8 before
  dummy Firebase configuration caused production mapping upload rejection, and that the official workflow still needs
  an authorized upload keystore plus production Google/Firebase/DataDog configuration.
- Synchronized the same day-to-day build and release guidance into `.github/copilot-instructions.md` as required by the
  repository documentation-sync contract. No source code, signing material, production credentials, or artifacts changed.

## 2026-07-15 - Android Studio Gradle Sync repair and compile verification
- Fixed Android Studio's `Unable to start the daemon process` failure by replacing unsupported
  `-XX:+UseZGC -XX:+ZGenerational` with `-XX:+UseG1GC` in root `gradle.properties`. The configured Android Studio
  JBR is JDK 21 but does not include ZGC; `gradlew help --stacktrace` subsequently completed successfully.
- Re-ran the required local baseline after the abnormal shutdown with JDK 21, the local Android SDK, and English locale:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache`. It completed
  successfully in 3m42s with 1,533 actionable tasks; Gradle discarded and rebuilt the few interrupted local caches.
- Confirmed a Google universal debug APK at
  `app/build/outputs/apk/google/debug/app-google-universal-debug.apk` (83,759,469 bytes). This proves compilation,
  packaging, static checks, and tests work locally; it is not a Google Play release artifact.
- A Google release AAB trial reached release compilation/R8 but local mapping upload failed because production
  Firebase/release credentials are absent. Per the user's revised scope, the retry was explicitly stopped and no AAB
  was copied into Git or prepared for cloud backup. Temporary AAB-specific build-script/Fastlane changes were reverted.
- Final intended worktree change is only the G1GC compatibility fix plus this mandatory handover entry. A production
  Play AAB still requires the project's real upload keystore and production service configuration.

## 2026-07-10 - Manual per-channel LoRa rule and AGENTS synchronization
- Updated both repository `AGENTS.md` files to record the implemented Provider/capability/explicit-command/event
  architecture, port-256-only outbound policy, MeshLink radio ownership, variant pairing, privacy boundary, and the
  2026-07-10 hardware E2E evidence. Older parent AIDL notes are explicitly historical rather than current guidance.
- User-confirmed product rule: there is no automatic channel binding. A normal NTsocial channel may use LoRa only after
  the user manually enables and binds it in that channel UI; an unbound channel remains BLE-only. Removed the parent
  `MeshtasticGatewayManager` refresh-time logic that wrote the canonical lane into every joined channel and removed the
  now-invalid contract helper/tests.
- Added `ChatViewModelMeshtasticTest.sendPost_withUnmappedChannel_keepsLoRaOutOfTransport`. Target validation passed:
  `:app:assembleDebug`, `MeshLinkGatewayContractTest` (4 tests), and the new unbound-channel test. The parent project
  has no Spotless task; an attempted `:app:spotlessCheck` therefore did not execute compilation or tests.
- The updated parent debug APK was built, but the USB device disconnected before `adb install -r`; no device data or
  installed APK was changed in this follow-up. Reconnect the same device before installing the updated APK if a fresh
  on-device check is desired.

## 2026-07-10 - Cross-app Android LoRa hardware E2E acceptance
- Retested both installed debug applications over the connected Android 16 device without clearing data or changing the
  user's radio configuration. The user-created normal channel `#NTsocialLORA` was already LoRa-enabled and bound; the
  parent channel UI showed `LoRa enabled` before the send.
- A 20-second idle baseline for the parent and MeshLink processes had zero error-level log lines. The outbound test was
  entered through the normal channel `MessageInput` and submitted with its actual accessible Send control (not the
  Meshtastic test/feed UI). The input cleared and one committed local message appeared in that channel.
- Parent evidence in the send window: one normal-channel LoRa route preparation, port 256 only, three explicit protected
  Gateway command broadcasts, one accepted dispatch, and one accepted asynchronous lane completion. No legacy AIDL call
  or port 497 outbound path was used.
- MeshLink evidence in the same window: three radio-transport writes, eight observed queue-status events, and three
  `queueJob ... success true` firmware responses. The parent and MeshLink windows both had zero error-level logs and no
  reject, timeout, disconnected-radio, inactive-transport, or write-failure signal.
- This proves normal NTsocial channel -> protected Provider/capability -> explicit COMMAND broadcast -> MeshLink ->
  connected radio firmware queue delivery. It does not by itself prove RF reception at a remote peer; that requires a
  second receiving radio or an on-air receiver/return message.

## 2026-07-10 - MeshLink ContentProvider / Intent Gateway boundary (pending parent full validation)
- Added the Android-only cross-app Gateway boundary: `${applicationId}.gateway` Provider paths `/v1/status`,
  `/v1/envelopes`, `/v1/nodes`, and `/v1/channels`; an explicit `com.ntsocial.meshlink.gateway.COMMAND` receiver;
  and explicit, metadata-only `com.ntsocial.meshlink.gateway.EVENT` broadcasts. Events invoke `ContentResolver.notifyChange`
  and never include message bytes. `NtsocialGatewayEventPublisher` starts after Koin in `MeshUtilApplication`.
- Provider status includes connection, cache count/limit, ports and limits, canonical default-channel readiness/index,
  provisioning outcome, local node ID, and sanitized gateway health. Nodes omit positions, keys, notes, and raw protobufs;
  channels expose only index/name/uplink/downlink, never PSKs or RF config. Envelope bytes remain Provider-only.
- Security: ACCESS/CONTROL `signature|knownSigner` permissions and release/debug signer resources are declared. The
  Provider pins caller UID/package/certificate to `com.ntsocial.android`; debug is accepted only for a debuggable
  MeshLink host and `com.ntsocial.android.debug`. Package visibility queries were added. Android 8-13 commands use a
  short-lived, single-use Provider-issued capability bound to `request_id`; Android 14+ also verifies sender UID/package/
  certificate directly.
- Added raw envelope outbound path: validates an existing `NM` envelope, sends it unchanged only on PRIVATE_APP/256,
  limits complete external envelopes to 180 bytes, caches it as outbound, and rejects a noncanonical channel index.
  Legacy 497 remains inbound-only. Cache uses a Mutex; event dedupe state is bounded to current cache keys.
- Preserved AIDL Gateway service as deprecated compatibility and fixed `AndroidRadioControllerImpl` to use
  `context.packageName`, not a hard-coded package.
- Focused tests added for raw envelope behavior, capability one-time/request/UID/expiry behavior, and Provider contract
  constants. Targeted commands passed before final manifest/event-only polish:
  `:core:service:compileAndroidMain :app:processFdroidDebugMainManifest`,
  `:core:service:testAndroidHostTest --tests NtsocialGatewayCommandCapabilityStoreTest`, and
  `:core:data:jvmTest --tests NtsocialGatewayRepositoryImplTest` (8 tests, zero failures in XML).
- A broad `spotlessApply` attempt found one max-line-length issue in the new capability test; it was fixed afterward.
  An overlapping earlier Gradle invocation also produced a Kotlin compiler internal AIOOBE, so the parent should run the
  normal single baseline after this handoff rather than treat that as a source failure.

## 2026-07-10 - Physical BLE/logcat diagnostic (no fix applied yet)
- Real-device debug of the F-Droid Debug app on Android 16 found no `FATAL EXCEPTION` or app crash, but reproduced a live BLE failure: the UI shows `Connection timeout — no data received` after the service liveness guard observes 67,128 ms without a received radio packet (threshold 60,000 ms).
- The observed connection timeline had successful BLE profile setup and Stage 1/Stage 2 handshakes, an earlier genuine remote BLE disconnect/reconnect, then a later liveness timeout. After the timeout, no new BLE connection attempt/retry appeared for over three minutes.
- Root cause is an application bug: `SharedRadioInterfaceService.checkLiveness()` calls `onDisconnect(isPermanent = false)`, which only changes the connection state to `DeviceSleep`; it does not close/rebuild `radioTransport`. `BleRadioTransport` only runs its reconnect loop when it observes a real GATT disconnect or exception. Result: a zombie transport can leave the app visibly disconnected without recovery. The app-level `MeshConnectionManagerImpl` maps the state to `Disconnected` when power saving is off but also does not restart the transport.
- Prior to the timeout, current-connection UI RSSI reads were timing out repeatedly. `CurrentlyConnectedInfo` polls GATT RSSI every two seconds with a one-second timeout, so a stale link produces a timeout/log cycle roughly every three seconds instead of becoming a reconnect signal. A heartbeat may also be sent while BLE `toRadio` is unavailable during reconnect; this creates avoidable warning noise.
- Performance evidence: while the Connections screen has user-enabled BLE scan active, the indeterminate `LinearProgressIndicator` drives ~120 fps foreground rendering. Android 16 logs `View.setRequestedFrameRate(frameRate=NaN)` twice per frame, filling logcat (~9,904 entries in 41.2 seconds). Frame pacing itself was healthy (0.88% jank, 7 ms median); the concern is battery/log-buffer overhead, not a demonstrated UI stall. Stable background rendering stopped (0 frames over a 10-second settled background interval).
- Privacy defect: do not reproduce/expose device data, but current debug Logcat contains a full position emitted by `CommandSenderImpl.sendPosition()` and other raw protobuf/position log paths. This violates the project privacy rule and needs removal/sanitisation. Temporary device/local screenshots used for diagnosis were deleted; no PII was retained in this handover.
- Recommended fix sequence (requires user authorization to implement): add an explicit transient transport-restart path for liveness failure under the transport mutex; gate heartbeats until a transport is actually connected/ready; add regression tests for liveness-restart and repeated RSSI timeouts; remove/sanitize precise-location and raw-packet logging; then retest reconnect on this phone.

## 2026-07-10 - Physical Android deployment verified
- User connected an ADB-authorized Samsung `SM_S9280` (`R5CWC4KNTRL`) over USB. Confirmed its ABI is `arm64-v8a`.
- Built `:app:assembleFdroidDebug` successfully with JDK 21 and the local Android SDK. The build emits ABI-split APKs; installed `app/build/outputs/apk/fdroid/debug/app-fdroid-arm64-v8a-debug.apk` (56.37 MiB) through `adb install -r -t`.
- Install succeeded. The F-Droid debug application ID is `com.ntsocial.meshlink.fdroid.debug` (not the production base ID); verified its package path, running PID, `MainActivity` window, and `topResumedActivity`. The app is currently launched in the foreground on the device.
- Deployment used the F-Droid flavor intentionally because it is the directly testable open-source build and does not rely on production Google Maps/DataDog credentials. Do not silently uninstall it when updating; use `adb install -r -t` and preserve app data unless the user explicitly requests removal.

## 2026-07-10 - Windows Robolectric SQLite host-runtime repair
- Fixed the reproducible Windows-only `UnsatisfiedLinkError: no sqliteJni in java.library.path` in Android Robolectric tests. This was a missing host-JVM native SQLite runtime, not a Windows desktop-app target or a production Android SQLite defect.
- Added the upstream-compatible `androidx.sqlite:sqlite-bundled-jvm:2.6.2` runtime dependency to the app's unit-test classpath and `core:database` Android-host-test runtime classpath. It supplies the host-native `sqliteJni` loader, including `sqliteJni.dll` on Windows.
- Removed the Windows skip guards from `PacketFtsSearchTest` and `MigrationTest`; those tests now execute on Windows rather than hiding the native-load issue. No production source or protobufs changed.
- Verified with JDK 21, Android SDK, English locale, and no configuration cache: targeted `:app:testFdroidDebugUnitTest :app:testGoogleDebugUnitTest :core:database:testAndroidHostTest` passed. XML confirms NavigationAssemblyTest passes in both flavors, four migration tests and three FTS tests execute with `skipped=0`, `failures=0`, and `errors=0`.
- Full required baseline also passed: `spotlessCheck detekt assembleDebug test allTests -Pci=true --continue --no-configuration-cache` (`BUILD SUCCESSFUL`, 1,469 actionable tasks). Existing non-failing KMP host-test/Skiko version warnings remain unrelated to SQLite. Android instrumentation/LoRa hardware acceptance is still a separate emulator/device setup and is not replaced by Robolectric.

## 2026-07-10 - Completion audit and full local verification report
- User requested a current-completion audit and actual full local test of the NTsocial MeshLink fork. Read-only audit found `main` at `cf52c9fd`, 12 fork-only commits and 701 commits behind `upstream/main` `a3aa9769` (2.8.0 line), while `config.properties` still says `2.7.14`.
- Local CI-equivalent static matrix passed using JDK 21, Android SDK, English locale: `spotlessCheck`, `detekt`, app F-Droid/Google lint, barcode F-Droid/Google lint, API lint, and `kmpSmokeCompile` (`BUILD SUCCESSFUL in 4m 47s`).
- Full CI test task matrix was run with all 19 core KMP, 8 feature KMP, and 7 app/desktop/Android task paths. It finished in `12m 46s` with an expected non-zero result: `:app:testFdroidDebugUnitTest` and `:app:testGoogleDebugUnitTest` each fail `NavigationAssemblyTest.verifyNavigationGraphsAssembleWithoutCrashing` after the configured two retries. Test XML records 2,221 executions, 6 retry-attempt failures, 0 errors, 7 skipped. Root cause is reproducible Windows/Robolectric `UnsatisfiedLinkError: no sqliteJni in java.library.path`, triggered when navigation/Koin initialization runs the Room FTS backfill through `BundledSQLiteDriver`; it is not a navigation assertion failure. Four migration and three Android-host FTS tests are intentionally skipped on Windows for the same native SQLite limitation.
- Packaging passed: `:app:assembleFdroidDebug`, `:app:assembleGoogleDebug`, and `:desktop:createDistributable` all succeeded (`BUILD SUCCESSFUL in 36s`). Worktree remained clean (no product-source changes).
- Audit conclusion: official modern Meshtastic parity is roughly 55% (+/-5%), NTsocial Gateway goal roughly 30-35%, combined "complete official app + production NTsocial bridge" roughly 45-50%. Existing 2.8 slices include FTS, Device Links, air quality, XEdDSA, protected Gateway IPC/MVP port 256 + receive-only 497, and default NTsocial channel provisioning. Major missing work includes discovery, docs, Android Auto, AI App Functions, translation/geofence/lockdown/TINY preset paths, persistent/reliable Gateway delivery, scheduler/policy/dashboard, cross-app E2E, and hardware acceptance.
- CI risks discovered: public main has no observable Actions runs/check contexts or branch protection; F-Droid reproducible-build signing-block check in `.github/workflows/reusable-check.yml` has quoted-heredoc and inverted-status logic; tracked Google-map test sources have no matching Gradle test task; API/widget tasks are `NO-SOURCE`; no device/hardware acceptance coverage.

## 2026-07-06 - Meshtastic 2.8 parity stop report: database slice + interrupted baseline
- User requested stopping work and reporting at this point. Do not mark the overall 2.8 parity goal complete.
- Current branch is `codex/meshtastic-2-8-parity`. `gradle.properties` was restored to the original
  `UseZGC + ZGenerational` JVM args after temporary local Gradle/JBR G1GC runs.
- Added official 2.8.0 Android database reliability parity:
  `MeshtasticDatabase.configureCommon(multiConnection)` now supports upstream-style multi vs single
  connection pools, and Android `DatabaseBuilder` uses `BundledSQLiteDriver` with a single connection
  pool, matching official 2.8.0 behavior while preserving NTsocial database package/schema.
- Adjusted FTS tests for Windows/local reliability after bundled SQLite native loading failed in
  Robolectric host tests. `PacketFtsSearchTest` now has JVM coverage that actually runs all three FTS
  cases; Android host FTS/migration tests use `BundledSQLiteDriver` and skip on Windows where
  `sqliteJni` cannot load.
- Targeted validation passed:
  `spotlessApply :core:database:jvmTest :core:database:testAndroidHostTest --no-configuration-cache`.
  JVM FTS tests passed; Android host FTS/migration tests were skipped on Windows by design.
- Full baseline was started:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache`.
  It was interrupted per user request. Before interruption, many core/app/feature tests passed,
  including NTsocial Gateway, NTsocial channel provisioning, device links, model/navigation/network,
  and database targeted paths. One known failing item appeared before interruption:
  `:app:testFdroidDebugUnitTest` `NavigationAssemblyTest > verifyNavigationGraphsAssembleWithoutCrashing`
  failed with `UnsatisfiedLinkError`. Because the run was stopped, normal XML/HTML test reports were
  not fully written; only in-progress binary test-result files remained.
- No Gradle/Java processes from this workspace were left running after cleanup.

## 2026-07-06 - Meshtastic 2.8 parity slice: XEdDSA + air-quality persistence
- Completed the next targeted parity slice on `codex/meshtastic-2-8-parity` while preserving NTsocial Gateway/private-app
  behavior and branding. Prior slices in this worktree already include message search, device links, and air-quality UI/logs.
- Added the missing persistence path for air quality telemetry: `NodeEntity` now has `air_quality_metrics` with Room
  default `x''`, `NodeRepositoryImpl` writes it, `NodeManagerImpl.handleReceivedTelemetry()` updates it, and Room schema
  `41.json` includes it.
- Added XEdDSA signing support:
  `DataPacket.xeddsaSigned`, `Message.xeddsaSigned`, `MeshDataMapper` propagation from `MeshPacket.xeddsa_signed`,
  `Node.signsPackets`, `NodeEntity.has_xeddsa_signed`, `NodeManagerImpl.installNodeInfo()` from
  `NodeInfo.has_xeddsa_signed`, signed message badge, signed node status icon/dialog, node details signed row, and resources.
- Proto note: bumped `core/proto/src/main/proto` submodule only to protobufs commit
  `108919393a2a3fdf6ab82e50e10965e74394620f` (`Add initial protobufs for XEdDSA (#753)`). Do not use protobuf
  `origin/master` blindly here: it introduced later config changes that broke local traffic-management UI fields.
- Tests/validation passed with temporary local `gradle.properties` ZGC->G1GC switch restored afterward:
  `:core:database:kspKotlinJvm`, targeted `:core:model:jvmTest :core:database:jvmTest :core:data:jvmTest
  :core:navigation:jvmTest :feature:node:jvmTest :feature:messaging:jvmTest :feature:settings:jvmTest`,
  `spotlessApply`, `spotlessCheck detekt`, a final `:feature:node:jvmTest`, and `git diff --check`.
- Known validation warning only: existing Skiko version mismatch warning in feature node Compose checks and existing
  deprecation/no-cast warnings; they did not fail validation.

## 2026-07-06 - Official Meshtastic feature completeness audit
- Audited local `main` against freshly fetched official `meshtastic/Meshtastic-Android` `upstream/main`.
  Local HEAD is `c77785a023ad0d9ee7ae66d33f0cbf8bbdd6207a`; official latest is
  `4d07bc6641335cbeaa1d279b755b9e3cd11fccaf` from 2026-07-06; merge-base is
  `c0d95d6ac4196fcbc705f2d3f174c7d9c46a77b2`. Local is 623 commits behind and 10
  NTsocial commits ahead.
- Conclusion: NTsocial MeshLink does **not** currently implement all latest official Meshtastic Android
  features. Local `VERSION_NAME_BASE=2.7.14`; official `main` is `2.8.0`.
- Confirmed missing/latest-upstream feature modules: official has `feature:discovery`, `feature:docs`,
  `feature:car`, `core:konsist`, `baselineprofile`, `screenshot-tests`, and `docs-screenshots`; local
  lacks those modules. Official README highlights full-text message search, mesh network discovery,
  Android Auto, air-quality telemetry, device hardware links, and App Functions/system-AI integration.
- Concrete gaps found in local code: no FTS5 `PacketFts`/`searchMessages`/`MessageSearchBar`; no
  `feature/discovery`; no in-app docs browser/Chirpy docs assistant; no Car App Library
  `CarAppService`/templates (only older notification metadata); no App Functions or `AiFunctionProvider`;
  no `device_links.json`/msh.to link directory; air-quality support is request/config-only and lacks
  official PM/CO2 persistence, node cards, chart/log, and CSV export path.
- Additional official changes still unmerged include message translation, Mesh Beacon offers, waypoint
  geofences, NFC tag writing, firmware lockdown, XEdDSA signing UI, LoRa region-preset/TINY preset
  support, stale cache fixes, crash/fuzz hardening, and the removal of upstream AIDL/service architecture.
- Existing local specs corroborate unfinished work: `001-local-mesh-discovery` has 50 unchecked tasks,
  `002-node-list-layout` has 38 unchecked tasks, and `003-app-docs-markdown` has 90 unchecked tasks.
  These specs are marked Not Started.
- Important merge risk: NTsocial-specific code to preserve includes protected Gateway IPC, private-app
  transport/cache, default NTsocial channel provisioning, branding/assets, app id/package namespace, and
  the `core:api` transitional IPC surface even though official removed upstream AIDL.

## 2026-07-05 - NTsocial_release visual alignment pass
- Imported visual assets from `C:\Users\USER\Desktop\GitHub\NTsocial_release`: butterfly intro/wordmark,
  dark background art, flag images, butterfly logo, Android launcher mipmaps, and play-store icon.
- Added NTsocial intro visual helpers in `feature:intro`: animated butterfly splash, shared background
  scaffold, dark scrim, white copy, blue CTA pills, and reused the background across welcome,
  permission, and critical-alert intro screens.
- Updated visible shell touchpoints: app bar fallback branding now uses the NTsocial butterfly logo,
  the no-device connections card uses NTsocial background art, connection action buttons use rounder
  NTsocial-style shapes, and Android splash uses the imported launcher foreground on black.
- Stabilized unrelated flaky validation blockers encountered during full baseline:
  `DesktopNotificationManagerTest` now waits on fallback flow with coroutine timeouts, and
  `MessageViewModelTest` waits for returned `Job`s from IO-backed `safeLaunch` calls.
- Verification passed with `ANDROID_HOME=C:\Users\USER\AppData\Local\Android\Sdk`,
  `JAVA_HOME=C:\Users\USER\.jdks\openjdk-21`, and
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`:
  `.\gradlew.bat spotlessApply spotlessCheck detekt assembleDebug test allTests`.

## 2026-06-29 - Upstream firmware/Android divergence audit
- Confirmed this workspace is an Android/KMP app fork, not a direct `meshtastic/firmware` checkout.
  `upstream` points to `meshtastic/Meshtastic-Android`; firmware must be compared separately.
- Local `main` / `origin/main` is `638c04d64`; official Android `upstream/main` is `e634e71ea`;
  merge-base is `c0d95d6ac`. Local fork is 8 commits ahead and 543 Android upstream commits behind.
- Local app base is `VERSION_NAME_BASE=2.7.14`; official Android upstream main is `2.8.0`.
  Local bundled firmware metadata has stable `v2.7.15.567b8ea` and alpha up to
  `v2.7.22.96dd647`; upstream Android metadata has alpha up to `v2.7.26.54e0d8d`.
- Local protobuf submodule `core/proto/src/main/proto` is `v2.7.23-7-g1d6f1a7`; official
  protobufs has `v2.7.26`. The delta is 38 protobuf commits, including newer hardware models,
  Lockdown auth/status messages, TAK/ATAK schema changes, ITU region split, and KMP publishing.

## 2026-05-09 - AGENTS channel provisioning rule sync
- Updated `AGENTS.md` architecture boundaries to make built-in NTsocial channel provisioning a
  durable project rule: bundle the canonical public NTsocial channel, auto-register it after node DB
  readiness without confirmation, preserve primary when possible, replace the last secondary when
  full, and apply QR LoRa/RF config only on effectively unconfigured radios.
- Synchronized `.github/copilot-instructions.md` with the same quick-reference guidance.
- No Gradle validation was run for this docs-only change; `git diff --check` should remain clean.

## 2026-05-09 - Built-in NTsocial channel provisioning
- Added `NtsocialDefaultChannel` in `core:model` as the canonical built-in public NTsocial
  Meshtastic channel. The decoded channel is `NTsocial`, has a 32-byte PSK, uplink/downlink enabled,
  and includes LoRa config. The decodable QR payload uses `...GPoBIAQoBTg...`; the visually similar
  `...GPoBIASoBTg...` transcription fails protobuf decoding.
- Added `NtsocialChannelProvisioner` in `core:data`. It checks post-handshake channel state, treats
  same name or same PSK as NTsocial, updates non-canonical NTsocial slots, adds a free secondary
  slot, replaces the last secondary when full, and replaces primary only on one-channel radios.
- LoRa/RF config from the QR is only applied when the current local LoRa config is missing or
  `region == UNSET`; configured radios keep their existing region/frequency/preset.
- Wired provisioning from `MeshConnectionManagerImpl.onNodeDbReady()` after owner/session seeding,
  using the existing local admin session refresh flow before `set_channel` / `set_config` writes.
- Added decode and provisioner tests, plus manager wiring coverage. Verification passed:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache` with
  `ANDROID_HOME=C:\Users\USER\AppData\Local\Android\Sdk`,
  `JAVA_HOME=C:\Users\USER\.jdks\openjdk-21`, and English `JAVA_TOOL_OPTIONS`.

## 2026-05-09 - NTsocial protected Gateway IPC MVP
- Added project-owned protected NTsocial Gateway IPC in `core:api`: `INtsocialGatewayService`,
  `INtsocialEnvelopeCallback`, `NtsocialEnvelopeData`, `NtsocialGatewayStatus`, and
  `NtsocialGatewayContract`. The contract exposes `sendNtsocialPayload`, envelope observation,
  cache snapshot, and gateway status without exposing the deprecated `IMeshService` surface.
- Added `NtsocialGatewayService` in `core:service/androidMain`. It is a separate Binder service
  that enforces the NTsocial signature permission for cross-process callers, sends through
  `NtsocialGatewayRepository.sendTestPayload`, streams cached validated envelopes via callbacks,
  and reports connection/cache/port/payload-limit status.
- Declared `com.ntsocial.meshlink.permission.BIND_NTSOCIAL_GATEWAY` as a signature permission and
  exported `com.ntsocial.meshlink.core.service.NtsocialGatewayService` with bind action
  `com.ntsocial.meshlink.gateway.BIND`. Existing `MeshService` / `IMeshService` behavior was left
  unchanged.
- Added Android host contract tests and a fake NTsocial Gateway binder to verify the new IPC
  contract, mapper surface, constants, PRIVATE_APP port 256, legacy receive-only 497, and payload
  size status.
- Verification: `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache`
  passed with `ANDROID_HOME=C:\Users\USER\AppData\Local\Android\Sdk` and
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`.

## 2026-05-09 - NTsocial PRIVATE_APP transport MVP
- Added the first NTsocial Gateway data plane: `NtsocialEnvelopeCodec` validates the MVP
  `NM + version + 16-byte headerMsgId + payload` envelope, caps raw envelope size at 200 bytes,
  and keeps outbound payload capacity at 181 bytes.
- Added `NtsocialGatewayRepository` plus an in-memory cache/dedup implementation. Outbound test
  payloads are sent only on `PRIVATE_APP / port 256`; legacy port `497` is accepted only by the
  inbound cache path.
- Wired `MeshDataHandlerImpl` so incoming private-app-compatible data packets are offered to the
  NTsocial cache while preserving existing generic Meshtastic broadcast behavior.
- Added focused tests for envelope parsing, invalid magic/version rejection, size limits, cache
  deduplication, legacy receive-only handling, outbound port policy, and the MeshDataHandler hook.
- Verification: `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache`
  passed with `ANDROID_HOME=C:\Users\USER\AppData\Local\Android\Sdk` and
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`. One earlier `:desktop:test` run hit
  an existing flaky fallback notification assertion; an immediate rerun passed, and the final full
  baseline passed.

## 2026-05-09 - AGENTS current-state audit
- Rewrote `AGENTS.md` for the current `NTsocial MeshLink` identity: app id
  `com.ntsocial.meshlink`, project package boundary `com.ntsocial.meshlink.*`, preserved
  upstream `org.meshtastic.proto`, NTsocial token skinning, and gateway roadmap status.
- Clarified that gateway/cache/IPC/RF scheduler/node-policy features are planned unless code exists,
  and documented port 256, legacy 497 receive-only, channelId/channelIndex, LoRa media exclusion,
  user-consent `rebroadcast_mode = ALL`, and GPL/open-source boundaries.
- Synchronized `.github/copilot-instructions.md` with the new naming, validation command including
  `spotlessCheck`, debug package IDs, branch guidance, and NTsocial UI/gateway boundaries.

## 2026-05-09 - Rename to NTsocial MeshLink package identity
- Renamed project-owned Android/KMP source packages and paths from `org.meshtastic.*` /
  `com.geeksville.mesh` to `com.ntsocial.meshlink.*`; preserved upstream protocol boundary
  `org.meshtastic.proto`.
- Updated app display labels to `NTsocial MeshLink`, app id to `com.ntsocial.meshlink`,
  Gradle convention plugin ids to `com.ntsocial.meshlink.*`, desktop ids to
  `com.ntsocial.meshlink.desktop`, and Room schema path to the new database package.
- Verification: bootstrap completed with JDK 21 and Android SDK. Full
  `spotlessCheck detekt assembleDebug test allTests` passed with
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`. DataStore-style prefs tests
  now use in-memory test stores to avoid Windows atomic rename flakes.

## 2026-05-09 - NTsocial visual token skinning phase 1
- Updated core UI theme tokens only: `Color.kt`, `CustomColors.kt`, and `Type.kt`.
- Non-Dynamic theme now uses NTsocial indigo/emerald/amber with gray surfaces; Dynamic Color,
  `AppTheme` API, theme picker, prefs, MainActivity, and navigation shell remain unchanged.
- Verification: bootstrap completed with Android SDK and JDK 21; full
  `spotlessApply spotlessCheck detekt assembleDebug test allTests` passed when run with
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"` after clearing the stale Gradle
  problems report. Without English locale, hardcoded English Compose tests fail against zh-rTW
  resources.

## 2026-05-09 - NTsocial Gateway README identity rewrite
- Rewrote root `README.md` only as a Traditional Chinese-first early-fork identity for
  "NTsocial Meshtastic Gateway for Android", with a short English summary.
- Removed upstream download badges/release-channel claims and kept upstream attribution,
  GPL-3.0 license boundary, planned `PRIVATE_APP / port 256`, receive-only legacy `497`,
  user-consent `rebroadcast_mode = ALL` policy, and `channelId` vs `channelIndex` framing.
- Bootstrap note: `local.properties` was initialized from `secrets.defaults.properties`
  and remains git-ignored. Proto submodule update required elevated permissions for `.git/modules`.
- Verification: stale upstream download URL `rg` check passed; `spotlessCheck detekt` passed.
  Full `spotlessApply spotlessCheck detekt assembleDebug test allTests` did not pass because
  `:app:testGoogleDebugUnitTest` hit a Robolectric native ICU runtime failure and Gradle also
  reported an existing problems-report output collision.

## 2026-05-02 — CI cost-control PR review fixes
- Applied PR review feedback: encoding fixes in sort-strings.py, NUL-delimited staged-files loop
  in ai-guardrail.sh, installation instructions added, typo fix in strings.xml, command order
  fixed in AGENTS.md, narrowed .aiexclude/.gitattributes patterns, allTests added to SKILL.md.

## 2026-04-XX — Token Mitigation (Phase 1-3)
- `.copilotignore` and `.aiexclude` updated with stricter ignore rules.
- `AGENTS.md` modularized to ~3KB base; detailed rules moved to `.skills/`.
- `scripts/ai-guardrail.sh` added to prevent binary/log leaks (installation: see script header).
- CI Cost Control skill added at `.skills/ci-cost-control/SKILL.md`.

## 2026-07-10 - Android liveness recovery, RSSI resilience, and device validation
- Restored Windows Robolectric SQLite coverage by adding `sqlite-bundled-jvm` to the relevant app and
  database host-test runtimes, then re-enabled the migration and FTS tests that had been skipped on Windows.
- Fixed the BLE zombie-link path in `SharedRadioInterfaceService`: a >60-second liveness timeout now
  silently closes and recreates the BLE transport under the transport mutex. It is gated by an explicit
  `connectionRequested` flag, protected from overlapping restarts, avoids writing a polite disconnect frame
  into the unresponsive transport, and never raises the user-facing connection-timeout alert.
- Added `SharedRadioInterfaceServiceLivenessTest` coverage for restart/recreate behavior, no permanent
  disconnect or alert, stale/repeated restart suppression, inbound-data reset, non-BLE no-op, and explicit
  disconnect behavior. Added `FakeRadioTransport.closeCount` test support.
- Changed presentation-only RSSI polling from a 2-second/1-second-timeout loop to normal 5-second polling
  with capped 5/10/20/30-second exponential retry backoff. Added `RssiPollingTest`.
- Reduced sensitive Logcat exposure: packet/position/telemetry/store-forward/serial-diagnostic logs now
  retain only safe type/size metadata, HTTP logging is `INFO` instead of `BODY`, and stream-frame logs no
  longer include endpoint-derived tags. Existing MeshLog database retention remains a separate product
  decision and was not silently altered.
- Verification passed in full with JDK 21, Android SDK, and English test locale:
  `./gradlew.bat spotlessCheck detekt assembleDebug test allTests -Pci=true --continue --no-configuration-cache`
  (`BUILD SUCCESSFUL`, 9m31s). Targeted service, connection, data, and network suites also passed.
- Deployed the F-Droid ARM64 debug APK to a USB-connected Android device. A clean launch reached
  `Connected`; ADB-driven message navigation and the visible Send button successfully produced an outbound
  radio-frame write, and the user visually confirmed the sent message. The non-mutating Settings/About page
  also rendered. A 75-second sanitized idle monitor showed no liveness-timeout alert or connection warning.

## 2026-07-10 - Cross-app ContentProvider Gateway integration and validation
- Replaced the parent app's LoRa-side Meshtastic AIDL integration with the MeshLink ContentProvider + explicit
  command/event boundary. The parent package `com.ntsocial.android.debug` targets
  `com.ntsocial.meshlink.fdroid.debug` in debug builds; the production variant targets `com.ntsocial.meshlink`.
  BLE/GATT code was not changed.
- MeshLink now exposes verified `/v1/status`, `/v1/envelopes`, `/v1/nodes`, and `/v1/channels` snapshots. Commands
  require a short-lived, single-use Provider-issued capability on API 26-33 and sender verification on API 34+.
  New outbound traffic is raw `NM` traffic on port 256 only, bounded to 180 bytes and the canonical provisioned
  NTsocial channel; legacy port 497 stays inbound-only.
- Provider data excludes radio configuration, PSKs, positions, notes, and raw node protobufs. Events are explicit and
  metadata-only; the parent re-queries the Provider. Parent readiness automatically binds joined logical channels to
  the canonical RF channel many-to-one.
- Validation passed: MeshLink's required full baseline
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache` completed with final exit
  code 0 using a single-worker, reduced-memory local invocation; parent debug assembly plus
  `MeshLinkGatewayContractTest` completed with exit code 0. Both debug APKs were verified as Android Debug signed
  with SHA-256 `B578F8445925AEA570F7E916C335172559773D7B6EC92DB0D76355E0E8F3FF8D` and have the expected packages.
- Device follow-up is still required: the initially connected Android 16 device (`R5CWC4KNTRL`) disappeared from ADB
  before installation, so neither new APK was installed and no device settings/data were changed. Once the device
  reconnects, install both APKs with `adb install -r`, query the Provider as the debug parent, verify unauthorized
  shell denial, launch both apps, and monitor sanitized logs for the cross-app event/re-query path.

## 2026-07-18 - Google Play launch plan documentation
- Saved the complete Traditional Chinese Google Play first-launch plan at
  `docs/google-play/06-first-play-launch-plan-zh-TW.md`.
- This session was documentation-only; no application code, release credentials, Play Console state, or GCP
  resources were changed, and no Gradle validation was required.

## 2026-07-23 - Bluetooth-only first-release connections UI
- Simplified the shared `ConnectionsScreen` presentation to a single Bluetooth device section. Removed the BLE/TCP/USB
  transport filter chips, the TCP/network and USB list sections and empty states, the manual TCP address sheet, and the
  screen-level network auto-scan/permission trigger. The connection status card, BLE scan action, BLE devices, region
  warning, and disconnect/navigation behavior remain.
- Narrowed `bleDevicesForUi`, `BluetoothDeviceList`, and `DeviceListItem` to `DeviceListEntry.Ble`, so USB/TCP icons and
  actions cannot be rendered through this UI. Because the screen is in `commonMain`, the simplified presentation is
  shared by the Android and desktop hosts.
- Preserved all TCP/USB backend code: Android/JVM discovery, `ScannerViewModel` TCP/USB flows and scan/select handlers,
  device models, transports, preferences, and tests remain. Existing connection tests confirmed BLE, TCP, and USB
  backend behavior still passes.
- Validation passed with JDK 21, Android SDK, and English test locale: targeted
  `:feature:connections:allTests` (`BUILD SUCCESSFUL`, 10m35s); required full
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache`
  (`BUILD SUCCESSFUL`, 21m9s; 1,589 actionable tasks); and
  `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache`
  (`BUILD SUCCESSFUL`, 2m32s; 920 actionable tasks). No device UI smoke test was performed in this session.
- Synchronized `AGENTS.md` and `.github/copilot-instructions.md` with the Bluetooth-only first-release Connections UI
  rule, retained USB/TCP backend boundary, current build evidence, and outstanding device-smoke limitation.

## 2026-07-23 - NTsocial MeshLink Windows full branding
- Rebranded only the Windows `:desktop` product as `NTsocial MeshLink`: window/taskbar/tray/default app-bar imagery,
  toast identity, MSI/EXE/start-menu name, `LiberaNt LLC` vendor, `NTsocial` menu group, and stable upgrade UUID
  `6784A2DD-CE59-518B-AA15-C26302D6FA85`. macOS/Linux metadata and icons remain unchanged; application ID remains
  `com.ntsocial.meshlink.desktop`.
- Copied the authorized blue NTsocial ICO and 24/48/512 PNG marks from the read-only adjacent `NTsocial_Windows`
  repository at HEAD `84c6f8c4349eaecff741a09d4e77a7c3e9d04b68`. Exact source paths, introduction commits, and SHA-256 values are
  recorded in `desktop/BRANDING_ASSETS.md`. The reference repository remained clean. The shared butterfly fiber
  background already had an identical SHA-256, so it is reused without duplication.
- Added optional shared color-scheme, typography, and default-brand-painter CompositionLocals while preserving the
  `AppTheme(darkTheme, dynamicColor, content)` API, Material Expressive, Dynamic Color fallback, Android green
  butterfly, and event-edition branding priority. The shared navigation scaffold gained an optional container color
  whose default preserves all existing hosts.
- Added the Windows indigo/emerald/amber translucent palette, Segoe UI Variable/Segoe UI plus Cascadia Mono/Consolas
  resolution, full-window butterfly background, transparent navigation host, and a fixed three-second cold-start
  overlay. Koin, Mesh service, and data initialization still start immediately; process-level state prevents a tray
  re-show from replaying the splash.
- Added desktop tests for Windows/non-Windows identity selection, application/notification IDs, exact splash phase
  boundaries, one-shot cold-launch consumption, and classpath resource availability. Updated the Windows notification
  sender test and rewrote `desktop/README.md` to describe the truthful current architecture and product boundary.
- Validation passed with full JBR JDK 21, Android SDK, and English locale: targeted `:desktop:test` (`BUILD SUCCESSFUL`,
  4m33s); required full `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue
  --no-configuration-cache` (`BUILD SUCCESSFUL`, 11m; 1,589 actionable tasks); and `kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache` (`BUILD SUCCESSFUL`, 5m17s; 920
  actionable tasks).
- Windows release packaging also passed with the complete JetBrains JDK 21 (`BUILD SUCCESSFUL`, 7m42s). It produced
  `NTsocial MeshLink-1.0.0.exe` and `.msi`; MSI properties read back as ProductName `NTsocial MeshLink`, Manufacturer
  `LiberaNt LLC`, and the planned UpgradeCode. The MSI start-menu directory is `NTsocial`, and both shortcuts are named
  `NTsocial MeshLink`. Install/upgrade/coexistence was verified structurally from installer metadata, not by changing
  the machine's installed applications.
- A dark-theme Windows smoke launch rendered the branded title, blue taskbar/app-bar mark, palette, and Bluetooth-only
  Connections screen without crash. Computer Use was stopped by the user before further interaction; 100/150/200%
  scaling, light-theme, tray re-show, and the final post-transparency visual should still receive manual release QA.

## 2026-07-23 - Android and Windows dual-track agent guidance
- Updated `AGENTS.md` so the repository explicitly has two first-class product tracks: Android `NTsocial MeshLink`
  in `app/` and Microsoft Windows `NTsocial MeshLink` in `desktop/`, with shared KMP changes requiring impact review
  against both hosts.
- Added separate product IDs, current status, architecture/integration boundaries, platform brand systems, Windows
  installer identity, automated validation, and manual release-QA requirements. The guide explicitly records that
  Windows branding and packaging are implemented while `NTsocial_Windows` IPC, Windows Service, Authenticator, code
  signing, and parent-App interoperability are not.
- Synchronized `.github/copilot-instructions.md` because the change affects day-to-day scope, naming, build commands,
  and release claims. This follow-up changed guidance only; it did not modify product code or rerun Gradle validation.

## 2026-07-23 - Windows Bluetooth discovery UI smoke test
- Launched the current Windows `NTsocial MeshLink` development application with `:desktop:run` on the user's laptop.
- The Bluetooth-only Connections UI automatically entered its scanning state and displayed the nearby
  `Meshtastic_fe66` node at approximately RSSI -81 dBm. The same node remained visible after a five-second refresh.
- This validates local Windows UI discovery of the nearby BLE advertisement in this environment. The session did not
  select/connect to the device, exchange Meshtastic data, alter radio configuration, or prove reconnect behavior.
- The application was left running on the Connections screen.

## 2026-07-23 - Windows BLE first-pairing blocker diagnosis
- Follow-up device selection failed before the Meshtastic handshake with Windows HRESULT `0x80650005`
  (`E_BLUETOOTH_ATT_INSUFFICIENT_AUTHENTICATION`). The nearby node is healthy and had just been disconnected from the
  user's phone; discovery remained functional.
- Root cause: the JVM `KableBluetoothRepository` always reports unbonded and implements `bond()` as a no-op, while the
  common and JVM Connections paths incorrectly assume Desktop Kable/Windows will pair automatically during GATT
  connection. Kable 0.42.0's JVM btleplug FFI exposes connect/read/write/subscribe but no pairing operation, so the
  app reaches a protected Meshtastic characteristic without triggering Windows pairing/PIN UI.
- The `unnamed-...` regression is separate: scan aggregation replaces the stored device whenever RSSI changes even if
  the newer advertisement has no name; `DeviceListEntry.Ble` then falls back to `unnamed-{address}`. Preserve the last
  non-blank name for an address when merging advertisements.
- No product code was changed in this diagnostic turn. A proper fix needs a Windows-only pairing service/API before
  selecting the radio, actionable authentication error mapping, stable scan-name merging, focused tests, and an
  on-device pairing/PIN/connection validation.

## 2026-07-23 - Windows BLE PairAsync root-cause follow-up
- Microsoft explicitly lists `DeviceInformationPairing.PairAsync` as unsupported in Desktop apps. The current
  fork-only `JvmDesktopBluetoothPairingService` invokes exactly that basic-pairing API from a hidden, non-interactive
  PowerShell desktop process, matching the observed `PAIRING_STATUS=Failed` result and absence of a PIN dialog.
- Upstream `main` still documents that BLE bonding is not supported on Desktop. Its JVM `isBonded()` remains false and
  `bond()` remains a no-op; Kable 0.42.0 in this fork and Kable 0.44.3 upstream expose no JVM pair/bond/PIN API.
  Therefore Windows first-pair/PIN was not an upstream-complete feature that branding broke.
- The fork's PowerShell/WinRT helper, fake tests, and fail-before-GATT integration were introduced together in
  `ce20e086c`; the prior `8ae1bd4e` state retained the upstream no-op behavior. The helper also discards the child
  process exit code and exception detail, has no custom-pairing/PIN UI state machine, and is retried indefinitely even
  though `BlePairingException` is classified as permanent.
- Focused validation passed on JDK 21:
  `:core:ble:jvmTest :core:network:allTests :feature:connections:allTests :desktop:test --no-configuration-cache`.
  These tests use a fake pairing process and no Windows hardware/WinRT ceremony, so they do not validate real
  first-pairing.
- Recommended implementation direction is a Windows-only custom-pairing bridge using
  `DeviceInformation.Pairing.Custom`, `PairingRequested`, and an ephemeral Compose pairing state/PIN flow, followed by
  real Windows Settings/reference-helper and Meshtastic hardware testing. A Windows Settings pre-pair flow is the
  lowest-risk interim workaround. No product code or OS Bluetooth state was changed in this investigation.

## Golden Context (stable across sessions)
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks (see script for install).
