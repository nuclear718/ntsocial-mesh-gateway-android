# Tasks: iOS NTsocial MeshLink Companion

Checked items mean the implementation exists in source and has the focused evidence named here. They do not imply signed-device, radio, RF, or App Store completion.

## Phase 0 — Contract and baseline

- [x] T001 Audit Android Gateway, shared KMP radio/data/DI, and the construction report.
- [x] T002 Audit the `NTsocial_release` iOS seam, bundle/deployment identifiers, and integration boundary.
- [x] T003 Correct first-release scope: the companion is a thin radio/integration utility, not a duplicate social or full Meshtastic client.
- [x] T004 Update the construction report, Spec Kit artifacts, AGENTS third-track status, and session memory to match current evidence.

## Phase 1 — iOS KMP production baseline

- [x] T010 Include all active KMP modules, including Gateway and MeshCore modules, in root iOS smoke compilation.
- [x] T011 Add Room KSP processing for iosArm64 and iosSimulatorArm64.
- [x] T012 Replace active-path zero-random and no-op preference implementations with Security.framework random bytes and durable DataStore paths.
- [x] T013 Implement Kable iOS peripheral reconstruction, real Bluetooth availability, central restoration configuration, and negotiated write length.
- [ ] T014 Retain one clean-run report proving every active KMP module compiles for both iosArm64 and iosSimulatorArm64; focused Gateway and simulator-runtime compilation is green, but this checklist does not substitute for the full retained log.
- [ ] T015 Enable and execute Kotlin/Native iOS tests. The repository convention currently disables native test link/run tasks.
- [x] T016 Wire the iOS runtime JVM-test source-set hierarchy to the repository's `jvmAndroid` actuals and cover shell/deep-link, exact session/active-database guards, inbound-identity signaling, bounded retries, drain-budget continuation, and durable source revalidation; `:ios:runtime:jvmTest` passes 15/15.

## Phase 2 — Neutral Gateway domain

- [x] T020 Create `core:gateway` with Apple Gateway v1 models, constraints, schema, store, provider engine, and failure reasons.
- [x] T021 Implement strict complete-`NM` validation, port 256 outbound/497 receive-only policy, and 180-byte bounds.
- [x] T022 Implement versioned length-delimited authentication bytes, HMAC-SHA256, and constant-time tag verification.
- [x] T023 Implement opaque generation rotation, 32-byte/120-second routes, client-ID validation, nonce replay defense, cursors, private-ledger idempotency, and deterministic packet IDs.
- [x] T024 Add common/JVM tests for codec, validation, route capability/lifecycle binding/rotation/expiry, replay/conflict, claim reclaim, bounded ingress, stable native feed, retention-safe overlay allocation, additive schema-v1 backfill, accepted-ledger crash replay, and ledger-commit failure; `:core:gateway:jvmTest` passes 36 tests.
- [x] T025 Add schema-v1 `overlay_epoch_state` so each epoch's high-water advances atomically, survives 128-row retention, backfills early v1 databases without changing `user_version`, and clears on explicit reset.

## Phase 3 — iOS runtime and app host

- [x] T030 Create the `ios:runtime` static `MeshLinkKit` framework and real iOS Koin/platform graph.
- [x] T031 Reuse `DirectRadioControllerImpl`, `MeshServiceOrchestrator`, shared radio repositories, and the Room-backed `IosDurableMessageQueue`; expose a Swift lifecycle facade.
- [x] T032 Add a focused Compose/Navigation 3 UI for host/App Group/Bluetooth/background/parent readiness and radio scan/select/connect/disconnect/forget.
- [x] T033 Add English and Traditional Chinese resources plus accessible state/action labels for the implemented focused UI.
- [x] T034 Create the iOS 17+ SwiftUI/Xcode host, plist, source entitlements, Privacy Manifest, AppIcon asset catalog, URL handoff, Darwin observer, app assets, and framework embed/link phase.
- [x] T035 Build with signing disabled from fresh Derived Data with no Xcode/linker/ICU warning, verify compiled AppIcon/Privacy Manifest, install, cold-launch, keep alive, and inspect UI on the `Codex iPhone 17` simulator (`E3249756-57AF-4D9C-AA2B-3332E9309529`).
- [x] T036 Close the Skiko ICU simulator-18.5 versus deployment-target-17.0 warning with a fail-closed normalization restricted to the verified data-only archive member; clean Xcode linking emits neither that mismatch nor the no-platform warning. Repeat this check on the signed archive.
- [x] T037 Add atomic selected/active radio session state, exact-session configuration completion, route/admission guard revalidation under `ChannelOperationLock`, READY-triggered drain, coalesced 500/1,000/2,000-ms retry, and delayed continuation after a 64-command drain budget.
- [x] T038 Build the generic iphoneos Release target with signing disabled from fresh Derived Data; verify arm64 Mach-O output, Info.plist/Privacy Manifest lint, and no Xcode/linker/ICU warning. Treat this only as unsigned device-architecture source-build evidence.
- [x] T039 Harden shared radio replacement with generation-bound callback validation; exact epoch through admin/readback/packet dequeue/synchronous transport send; ingress-child-work and outbound queue/status/log await barriers; authoritative selected-address and active-database/cache hydration; serialized manual/QR/reconcile/provision mutation; firmware-69420 host owner/token readback correlation; and fail-closed ambiguous outcomes. Current focused domain/data/runtime slices pass 135/135 and the final bounded audit found no reproducible P0/P1 in these boundaries.

## Phase 4 — Apple Gateway persistence/security

- [x] T040 Implement the versioned App Group SQLite store with WAL-compatible coordination, caller/epoch compound keys, separate reclaimable command claims, append-only results, and consumer cursors.
- [x] T041 Implement Swift shared-Keychain loading/creation of a 32-byte HMAC key and fail-closed handling when App Group or Keychain access is unavailable.
- [x] T042 Implement canonical HMAC verification, active key version, expiry/window validation, nonce replay defense, and caller isolation.
- [x] T043 Implement status/channel projection and authoritative in-memory routes bound to caller, source, captured slot, generation, and 120-second TTL.
- [x] T044 Implement command processing through private-ledger reservation, existing Room packet admission with persisted source identity, durable message queue, accepted-ledger commit, append-only result, transient claim release, startup/reconnect replay, authenticated accepted-ledger recovery before process-local route resolution after a result-publication crash, and actual-dispatch revalidation through matching QueueStatus.
- [x] T045 Implement bounded overlay ingress (maximum 128 retained rows), retention-independent per-epoch allocation state, ports 256/497 receive policy, and stable-only paged native message changes with high-water reads.
- [x] T046 Implement payload-free Darwin state/command hints, launch/resume polling, and `ntsocial-meshlink://process`; document scheduling as best effort.
- [ ] T047 Complete signed cross-process migration, corruption, concurrent-writer, interrupted-transaction, missed-notification, restart, and key-rotation tests. Focused JVM/store/engine tests are green, but these device/process cases remain open.
- [ ] T048 Optionally improve the bounded readback-owner P2 without weakening fail-closed correlation: if an admitted readback times out/cancels and firmware never responds, the same epoch currently requires reconnect/new epoch before another exact readback can activate identity.

## Phase 5 — Parent integration and release gates

- [x] T050 In the separately authorized `NTsocial_release` worktree, implement the Swift App Group/Keychain/Darwin/deep-link adapter; canonical parent-store integration; exact first-send pending and multipart/final-ID restart correlation; slot-indexed duplicate outbound routes plus PRIMARY-then-lowest canonical/history projection with security-conflict rejection; durable gap/quarantine/abandoned-transfer records before bounded cursor recovery; commit-before-cursor behavior; terminal rejection handling; and authenticated native-text enqueue while deferring the composer. `AppleGatewayAdapterTests` passes 27/27, full SwiftPM passes 668/668, and the release build is green.
- [ ] T051 Retain one complete changed-scope formatting/static-analysis/shared-test/Android/Desktop/KMP validation report. Focused Gateway, iOS runtime, parent-adapter, and simulator gates are green; this item stays open until the full report is captured.
- [x] T052 Update memory and reports with exact source evidence and explicit unproven gates.
- [ ] T053 Configure real Apple Developer identifiers/profiles for both apps and prove the matching App Group and Keychain access group on signed physical devices.
- [ ] T054 Prove physical-device Bluetooth authorization, scan, connect/config handshake, foreground/background, restoration, reconnect, and node reboot.
- [ ] T055 Prove connected-radio durable command admission, LoRa airtime, second-radio receipt, parent import, retry/restart idempotency, and bidirectional Android/iOS interoperability.
- [ ] T056 Complete signed archive, licensing/source-offer, privacy, TestFlight, App Store metadata/review, rollback, and release evidence.

## Explicitly deferred product scope

- [ ] Companion routes/channel browser, native-text diagnostic composer, command-result browser, Gateway reset/panic-wipe UI, exported diagnostic bundle, maps, firmware update, TCP/USB/serial, MeshCore transport, and broad Meshtastic settings parity. These are follow-up work only if actual product needs justify them.
