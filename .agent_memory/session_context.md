# Agent Session Context - Meshtastic Android
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

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

## Golden Context (stable across sessions)
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks (see script for install).
