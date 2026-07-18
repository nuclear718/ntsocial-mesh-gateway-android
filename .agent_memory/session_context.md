# Agent Session Context - Meshtastic Android
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

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

## Golden Context (stable across sessions)
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks (see script for install).
