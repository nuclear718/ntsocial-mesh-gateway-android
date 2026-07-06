# Agent Session Context - Meshtastic Android
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

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

## Golden Context (stable across sessions)
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks (see script for install).
