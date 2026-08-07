# Feature Specification: iOS NTsocial MeshLink Companion

**Feature Branch**: `codex/feat/ios-meshlink`
**Created**: 2026-08-07
**Status**: Source implementation complete; release verification in progress
**Input**: Build a deliberately focused iOS companion from the existing Android/KMP architecture and connect it to the separate NTsocial iOS parent for LoRa transport.

This specification treats iOS as the repository's third product track. It records what is implemented in source, not an App Store or hardware-readiness claim.

## User scenarios and testing

### User Story 1 — Connect one Meshtastic radio (P1)

An iPhone or iPad user launches NTsocial MeshLink, grants Bluetooth access, scans for a Meshtastic radio, selects it, and sees the actual connection state. The companion is the only process that owns the Meshtastic radio transport.

**Independent test**: On a signed iOS 17+ device, scan, select, connect, finish the Meshtastic configuration handshake, background/foreground the companion, disconnect, forget, and reconnect without the parent opening another radio stack.

**Acceptance scenarios**:

1. When Bluetooth is unauthorized or off, the UI reports that condition and does not report a successful scan or connection.
2. When a compatible advertisement is selected, the shared radio state machine performs discovery, subscription, configuration, durable queueing, and reconnect through Kable/CoreBluetooth.
3. The parent uses Apple Gateway only; it does not link MeshLink KMP code or open a Meshtastic BLE session.

### User Story 2 — Parent sends an NTsocial envelope over LoRa (P1)

The parent writes one authenticated command for a current projected route. MeshLink validates it, reserves restart-stable idempotency, durably admits the Room packet and retry work, commits the ledger, and then publishes `ACCEPTED_LOCAL`. The state never means firmware airtime or remote delivery.

**Independent test**: With both apps carrying matching signed App Group and Keychain entitlements, enqueue a valid complete `NM` envelope, retry the same command across process restart, and observe one deterministic local packet admission plus stable results for the same client ID.

**Acceptance scenarios**:

1. A command requires the exact caller ID, a 32-character hexadecimal client ID, current source-channel ID, unexpired route token, matching opaque generation, valid time window, 16-byte nonce, active key version, HMAC-SHA256 tag, and a complete `NM` envelope of at most 180 bytes.
2. Route validation resolves the captured current slot. Caller input never selects a raw channel index.
3. `ACCEPTED_LOCAL` is appended only after private-ledger reservation, durable radio admission, and the final accepted-ledger commit.
4. Reuse of a client ID with identical content returns the existing accepted result; reuse with different content is rejected as an idempotency conflict.
5. If the process crashes after accepted-ledger commit but before result publication, an authenticated exact retry reconstructs the original `ACCEPTED_LOCAL` packet ID before route resolution and performs no second local radio admission. This is not an exactly-once RF guarantee for an earlier crash point.

### User Story 3 — Parent receives LoRa data (P1)

MeshLink exports two bounded, durable insertion streams: complete NTsocial overlay envelopes and stable-identity native broadcast text. The parent commits accepted content to its canonical store before advancing its own cursor.

**Independent test**: In the signed two-app environment, insert port 256 and receive-only legacy port 497 envelopes plus stable native text, restart either side between reads, and prove identity-based duplicate handling, commit-before-cursor behavior, and auditable bounded recovery when retention has already made a row irrecoverable.

**Acceptance scenarios**:

1. Only complete validated `NM` envelopes from port 256 or receive-only port 497 enter `overlay_ingress`.
2. `native_message_change` contains only broadcast text whose source-channel and source-message identities were captured at private-Room insertion time. Legacy rows with nullable identity are never recomputed from a current channel slot.
3. Shared rows contain only the minimum payload and stable routing facts. They contain no PSK, precise position, radio configuration, raw radio protobuf, parent social history, or account key.
4. Cursors are scoped by a durable history epoch; a consumer restarts at zero after an epoch change and advances only after its canonical-store commit.
5. Darwin notifications carry no payload, may be missed, and only prompt a database reread.
6. `overlay_epoch_state` preserves each epoch's monotonic overlay high-water after bounded row eviction; early schema-v1 databases receive a transactional additive backfill without changing `user_version`.
7. The mailbox/catalog retains slot-indexed duplicate source identities for outbound routes. The parent's canonical/history projection collapses equal stable identities deterministically—PRIMARY first, then the lowest slot—and rejects conflicting security semantics; same-epoch historical source resolution keeps retained native/overlay backlog usable after catalog replacement.
8. A retention gap, malformed envelope, or lost/expired multipart transfer advances only after the parent durably writes a gap, quarantine, or abandoned-transfer terminal record. The bounded recovery may move to `firstRetained - 1` or skip deterministic poison only after that record; transient store/projection failure leaves the cursor unchanged, and already-evicted rows are not described as recoverable.

### User Story 4 — Inspect companion readiness (P2)

The focused companion UI shows host/App Group readiness, Bluetooth authorization and power, radio selection/connection, best-effort background truth, and parent handoff state. It supports scan, connect, disconnect, and forget.

**Independent test**: Exercise simulator fixtures for ready, Bluetooth-off, App-Group-unavailable, parent-not-seen, and connection transitions; then repeat the entitlement and Bluetooth states on signed devices.

The first release does not require a companion routes browser, native-text diagnostic composer, command-results browser, Gateway reset/panic-wipe screen, maps, firmware management, or broad Meshtastic settings parity. Gateway routes and native-text command support remain available to the parent integration and tests without duplicating the parent's social UX in the companion.

## Requirements

### Functional requirements

- **FR-001**: iOS MUST be a third product host with bundle identifier `com.ntsocial.meshlink.ios`, display name `NTsocial MeshLink`, and minimum deployment iOS 17.
- **FR-002**: The iOS app MUST remain a standalone GPL companion. `NTsocial_release` MUST NOT link the MeshLink KMP/radio implementation.
- **FR-003**: Radio behavior MUST reuse the shared `SharedRadioInterfaceService`, Meshtastic profile, repositories, Room model, and durable message queue through Kable's Apple backend.
- **FR-004**: No second Swift CoreBluetooth stack may duplicate Kable scanning, GATT discovery, subscription, writes, restoration, or reconnect.
- **FR-005**: Active iOS paths MUST use Security.framework cryptographic randomness, durable file-backed DataStore, and Room KMP SQLite; zero-random and no-op preference stubs are forbidden.
- **FR-006**: The focused Compose/Navigation 3 UI MUST provide truthful host, App Group, Bluetooth, background, parent-handoff, radio selection, connection, scan, disconnect, and forget states. The deferred diagnostic and administration screens above are not release prerequisites.
- **FR-007**: Apple Gateway MUST use App Group `group.com.ntsocial.meshlink.gateway`; MeshLink's Room database and idempotency ledger MUST stay in its private Application Support container.
- **FR-008**: The 32-byte HMAC key MUST be created/read through shared Keychain group `$(AppIdentifierPrefix)com.ntsocial.meshlink.gateway`; key bytes MUST NOT be stored in SQLite, defaults, logs, or diagnostics.
- **FR-009**: Gateway generations MUST be opaque random text, rotate on process start and any radio channel/routing-context inequality, and MUST NOT be counters or configuration digests. The exact routing/ingress identity MUST include the monotonic radio-session epoch, selected/active radio equality, exact-session configuration completion, selected radio's active Room database, complete-channel readback/final-snapshot generations, history epoch, Bluetooth permission/power, transport/App connection states, and complete channel fingerprint.
- **FR-010**: A route MUST use a cryptographically random 32-byte Base64URL token, a 120-second TTL, and exact caller/source/slot/generation binding. The App Group copy is a short-lived projection; the authoritative route remains process-memory-only.
- **FR-011**: Client message IDs MUST be canonical uppercase hexadecimal strings of exactly 32 characters.
- **FR-012**: Outbound overlay commands MUST carry a complete valid `NM` envelope of at most 180 bytes and use Meshtastic `PRIVATE_APP` port 256. Port 497 MUST remain receive-only compatibility.
- **FR-013**: The Gateway's native-text command MUST be nonblank, at most 180 UTF-8 bytes, broadcast-only, and MUST NOT accept a target node. A companion-side composer is deferred.
- **FR-014**: Restart-stable idempotency MUST retain at most 256 insertion-ordered records per caller with no TTL, distinguish exact replay from fingerprint conflict, and replay an authenticated accepted record before process-local route resolution after restart. A failure to persist final ACCEPTED after durable radio admission MUST remain retryable and MUST NOT be described as proof that the packet was unscheduled.
- **FR-015**: `ACCEPTED_LOCAL` MUST mean durable Room/retry admission and accepted-ledger commit, not firmware airtime, RF delivery, or remote receipt.
- **FR-016**: App Group SQLite MUST expose bounded `overlay_ingress` (maximum 128 retained rows), retention-independent `overlay_epoch_state(history_epoch, high_water)`, and paged stable-only `native_message_change` streams with epoch/sequence high-water marks. The overlay state MUST advance atomically with append, be additively backfilled for an early v1 database, and clear on explicit reset.
- **FR-017**: The App Group schema MUST be explicitly versioned, transactional, busy-timeout/WAL coordinated, and compound-keyed by caller or epoch where identifiers can repeat. Command claims MUST be separate, reclaimable records; authoritative routes and the durable idempotency ledger remain private.
- **FR-018**: HMAC verification MUST use a versioned length-delimited canonical representation, SHA-256, active key version, expiry, caller isolation, nonce replay defense, and constant-time tag comparison.
- **FR-019**: Darwin notifications `com.ntsocial.meshlink.gateway.command-available` and `com.ntsocial.meshlink.gateway.state-changed` MUST be payload-free hints. Both apps recover by rereading SQLite after launch/resume and after missed notifications.
- **FR-020**: The deep link `ntsocial-meshlink://process` may request foreground processing but MUST NOT be described as background execution proof.
- **FR-021**: iOS background behavior MUST be described as best effort. CoreBluetooth state restoration is not an always-running service or guaranteed terminated-app command wakeup.
- **FR-022**: Parent private history, user/account secrets, DM/channel keys, MeshLink PSKs/config, and both apps' private databases MUST remain outside the App Group.
- **FR-023**: The separately authorized `NTsocial_release` source MUST keep current sending routes separate from same-epoch historical source resolution, retain slot-indexed duplicate source routes for outbound use, collapse equal stable identities for canonical/history projection by PRIMARY then lowest slot, and reject conflicting security semantics. It MUST expose authenticated overlay and native broadcast-text enqueue APIs. The native-text composer remains deferred.
- **FR-024**: Android Gateway v1/v2 and Windows host behavior MUST remain unchanged. Shared changes MUST be evaluated and compiled for all three product tracks.
- **FR-025**: TCP, USB/serial, MeshCore transport, maps, MQTT, firmware update, Wi-Fi Aware, and broad Meshtastic settings parity are out of the first iOS release scope.
- **FR-026**: Radio replacement MUST synchronously revoke retired transport callbacks, pause and await radio-owned ingress plus registered child work, stop and await the retired outbound queue/status/log generation, switch the active per-radio database, hydrate that database's cache from a direct snapshot, and only then resume/connect. Expected epoch MUST survive admin/readback admission, packet-queue dequeue, and synchronous transport send. A same-address or same-transport stale callback, packet, collector, or handshake completion MUST NOT mutate the replacement session/database.
- **FR-027**: Manual/QR apply, public protected-channel reconcile, and built-in provisioning MUST share one serialized mutation contract: validate exact session/ensure admin → invalidate Gateway ingress → exact-session firmware mutation → correlated fresh readback → activate verified final identity. Mutation serialization MUST NOT hold the operation lock while the readback producer commits. Radio rejection, readback failure/mismatch, acknowledgement timeout, session replacement, or other ambiguous outcome MUST leave ingress closed.
- **FR-028**: A first-send `pendingLocalAcceptance` MUST immediately persist exact message/attempt/transport `.queued` plus `.admission` state without claiming acceptance. A same-attempt terminal result MUST acknowledge/advance at most once. Parent-private multipart restart correlation MUST preserve the final social-header message ID, attempt, part kind/index/count, transfer ID, and logical channel rather than infer them from an outer chunk header or hard-coded direct-message values; an aggregate send becomes accepted only after every part is accepted and fails once after any rejection.
- **FR-029**: Parent ingress MUST commit valid canonical content before advancing its cursor. An irrecoverable retention gap, deterministic malformed row, or lost/expired multipart transfer MAY be skipped only after a durable gap/quarantine/abandoned-transfer terminal record. Transient storage/projection failure MUST remain retryable without cursor movement. This recovery MUST NOT be described as recovering rows already evicted by bounded retention.
- **FR-030**: Production interoperability still requires matching signed entitlements and two-app physical-device proof; source declarations, tests, and unsigned builds alone are insufficient.
- **FR-031**: A durable Gateway packet MUST retain the accepted source-channel identity in private Room. Actual dispatch MUST revalidate the exact active session/ingress and slot-derived PSK/LoRa source identity, and MUST linearize that validation through exact-session queue admission and matching firmware QueueStatus. A reused numeric slot with changed identity MUST fail closed.
- **FR-032**: Exact channel readback MUST reuse firmware's `69420` config-only sentinel and a host-exclusive owner/token created only after prior FULL Stage 2 and while no other handshake owner exists. Completion MUST use a dedicated host flow and MUST reject stale/parallel FULL responses, old sessions, or generic generation movement. Config-only completion MUST NOT start FULL Stage 2/readiness side effects.
- **FR-033**: If an admitted exact readback times out/cancels and firmware never emits a late response, the same epoch MUST remain fail closed; reconnect/new epoch is the supported recovery. This bounded liveness P2 MUST NOT weaken source-identity, session, or ingress checks.

### Key entities

- **GatewayStatus**: provider instance, schema version, readiness, opaque generation, optional history epoch, overlay/native high-water marks, active key version, and update time.
- **ChannelProjection / Route**: stable opaque source-channel identity, display slot/name/role/security, capabilities, projected token/expiry, and generation; authoritative caller/source/slot binding remains in memory.
- **GatewayCommand / CommandClaim**: immutable authenticated mailbox request plus a separately reclaimable processing claim.
- **CommandResult**: append-only caller/client result sequence including pending wake, local admission, rejection, and optional later radio-queue facts.
- **OverlayIngress**: bounded complete `NM` envelope with epoch/sequence and stable origin/routing identity.
- **NativeMessageChange**: stable-only native broadcast text insertion row with epoch/sequence and optional `origin_client_message_id`.
- **UsedNonce**: caller/key/nonce reservation tied to client ID and request fingerprint.
- **IdempotencyRecord**: MeshLink-private caller/client record with fingerprint, deterministic packet ID, `PENDING`/`ACCEPTED` state, and insertion order.
- **OverlayEpochState**: App-Group schema-v1 epoch/high-water allocation state that survives ingress retention and prevents sequence reuse.
- **RadioSessionGuard**: Private exact snapshot of session epoch, selected/active radio, configuration completion, Bluetooth, transport/App state, and channel fingerprint, captured for route context and rechecked under the channel-operation lock before admission.
- **GatewayIngressIdentity**: Private exact session/radio/database/readback/snapshot/history/channel identity that gates inbound caching and is republished only after an unambiguous final channel state.
- **ExactReadbackOwner**: Host-private session/token reservation for one firmware-69420 config-only response and its dedicated completion flow; it is never a new firmware nonce.
- **DurableGatewayDispatchIdentity**: Private Room source-channel identity carried from local acceptance to actual drain, where the numeric slot and exact session are revalidated.
- **ParentAdmissionCorrelation**: Parent-private exact message/attempt/transport and multipart final-ID/part/transfer/logical-channel state used to survive provider or view-model restart without false acceptance or an infinite first-send loop.
- **IngressTerminalRecord**: Parent-private durable gap, quarantine, or abandoned-transfer fact that must precede any bounded cursor movement past irrecoverable retained-stream data.

## Implemented evidence and remaining gates

Implemented in the current worktree:

- `MeshLinkKit` static framework and SwiftUI host with a source Privacy Manifest and AppIcon asset catalog, real Koin runtime, Kable/CoreBluetooth transport, Security.framework random bytes, DataStore, Room KMP, durable message replay, App Group SQLite/HMAC/Keychain/Darwin/deep-link wiring, the Apple Gateway engine, exact radio-session admission guard, READY drain/bounded retry, and the focused UI.
- Current-source focused slices pass 135/135: domain 16/16, data 104/104, and `:ios:runtime:jvmTest` 15/15. Runtime coverage is session/active-database guard 4, bounded retry 3, command-drain budget 3, durable-dispatch identity 2, inbound-projection signal 1, and shell/deep-link 2. `:core:gateway:jvmTest` separately passed 36; deterministic coverage plus the final bounded audit found no reproducible P0/P1 in the same-address, readback-owner, mutation, activation, and dispatch boundaries. The parent adapter's focused Swift suite passed 27/27, full SwiftPM passed 668/668, and its release build is green.
- An earlier retained revision has signing-disabled Simulator Debug/AppIcon/Privacy Manifest/install/cold-launch/UI and generic-iphoneos Release arm64 Mach-O/no-Xcode-linker-ICU-warning evidence. Current-source Xcode clean/cold-launch results remain pending and MUST replace—not inherit—that evidence. All such evidence is unsigned and is not a signed archive or physical-device result.
- The former Skiko ICU simulator-18.5 versus deployment-target-17.0 warning is closed by a fail-closed build-phase normalization restricted to the verified data-only archive member; the normalized member reports iOS Simulator minimum 17.0.

Not yet proven:

- matching real App Group/Keychain entitlements and provisioning on two signed apps;
- physical-device Bluetooth permission, scan, handshake, restoration, connected-radio admission, LoRa airtime, second-radio reception, or remote receipt;
- permanent background execution, TestFlight/App Store readiness, signing/archive/final privacy and linked-API/release review;
- iOS native tests: the repository convention still disables native test link/run tasks, so current iOS test evidence is compilation/link evidence only;
- bounded readback-owner liveness after admitted timeout/cancel with no firmware late response; reconnect/new epoch is the supported fail-closed recovery;

## Success criteria

- **SC-001**: Every active iOS KMP module compiles for iosArm64 and iosSimulatorArm64, and root smoke coverage includes all active KMP modules.
- **SC-002**: The static framework links into the SwiftUI iOS 17+ host, and the host builds, installs, cold-launches, and renders on a simulator with signing disabled.
- **SC-003**: Gateway/runtime/shared tests cover strict validation, canonical authentication, route expiry/generation rotation, exact accepted-ledger crash replay/conflict, claim reclaim, retention-safe overlay allocation, stable-only native cursors, exact radio-session/active-database guards, same-address replacement, firmware-69420 host-owner readback correlation, serialized mutation/producer progress, durable source revalidation at dispatch, inbound-identity drain signaling, bounded READY retry, and 64-command drain continuation.
- **SC-004**: Parent tests cover the shared HMAC vector, invalid HMAC, nonce replay, idempotency, additive schema-v1 migration, newer-schema rejection, stream/result round-trips, restart-stable first-send pending and multipart/final-ID correlation, deterministic duplicate-source projection and security conflict, durable gap/quarantine recovery before bounded cursor movement, terminal rejection, native-text enqueue, and canonical commit-before-cursor behavior.
- **SC-005**: No regression is introduced in affected Android/JVM tests, formatting, static analysis, Desktop/KMP compilation, or the deployed Android/Windows contracts.
- **SC-006**: A release claim additionally requires signed dual-app entitlement proof, physical BLE/LoRa/interoperability evidence, background truth validation, enabled native test execution, signed archive verification of the AppIcon/Privacy Manifest/ICU normalization path, and normal App Store delivery gates.
