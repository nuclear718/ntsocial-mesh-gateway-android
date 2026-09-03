# iOS Outbound Native Message Queue Remediation Report — 2026-09-03

## Result

The one-way Meshtastic text failure between the Android node shown as `1407` and the iOS node shown as `0809` was reproduced, traced to the iOS durable outbox, minimally corrected, and verified on the physical phones. The user confirmed the final two-way message test passed.

The affected product track is iOS `NTsocial MeshLink`. The shared repository/data change only adds durable-row provenance needed by the iOS queue. Android message receive behavior and Android radio code were not changed.

## Reproduced evidence

Before the correction, the active iPhone Room database contained 15 native Meshtastic text rows on the primary and direct-message channels, all still in `QUEUED`. The same phone had already stored Android-to-iOS packets from node `!835d30ae` as `RECEIVED`, proving the channel/radio path worked in that direction. The iOS-connected radio was currently identified by the protocol as `!f2470f9e`; its saved Bluetooth/display name still ended in `0809`.

The iPhone also retained 18 older Apple Gateway `PRIVATE_APP` rows in `QUEUED`. Those rows were important because they exposed the queue-ordering fault, but their local admission state was not treated as RF-delivery evidence.

## Root cause

`IosDurableMessageQueue` used the presence of `expectedSourceChannelId` as the test for whether a durable row belonged to Apple Gateway. That assumption was wrong: a normal message composed by the user on a Meshtastic primary channel also stores the stable source-channel identity.

Consequently:

- normal iOS primary-channel text was incorrectly forced through the Apple Gateway session gate;
- when an older Gateway row could not pass that gate, the global drain loop stopped;
- later normal direct messages, although not Gateway messages, remained behind that row and never reached the Bluetooth/radio dispatch path.

The Android receive path did not contain a matching sender, slot, or PSK filter that explained the loss. The failure occurred before iOS handed the affected messages to its connected Meshtastic radio.

## Minimal correction

- `DurableQueuedPacket` now carries an explicit `requiresGatewaySession` classification.
- Room rows are classified as Gateway-owned only when they use the outbound private-app port or carry an Apple Gateway origin client-message ID. A normal locally composed Meshtastic text remains native even when it has a stable channel identity.
- Native rows are drained ahead of Gateway-gated work, so a pending Apple Gateway row cannot own the head of the native message lane.
- Apple Gateway rows retain all existing exact-session and stable-channel validation. A malformed Gateway row without its durable source identity fails closed instead of falling through to native dispatch.

No scheduler, retry architecture, Android receive behavior, channel configuration, or radio firmware behavior was redesigned.

## Focused validation

- The focused `IosDurableMessageQueueTest` suite passed 3/3, including a regression with an older blocked Gateway row followed by a normal primary-channel text carrying a non-null stable source identity.
- Changed-scope formatting and Detekt passed, and the final-source signed arm64 Debug Xcode build completed successfully.
- That exact Debug App was installed data-preservingly and launched on the physical iPhone connected to the `0809` radio.

Immediately after launch, the old native backlog was no longer uniformly `QUEUED`: one primary message reached `DELIVERED`, while the burst of later stale messages reached the firmware and was rejected with Meshtastic routing error 38 (`RATE_LIMIT_EXCEEDED`). This was expected from replaying many old rows together and independently demonstrated that the former App-side queue barrier had been removed.

After the rate limit cleared, fresh device evidence recorded:

- iOS `!f2470f9e` primary broadcasts as `DELIVERED` at 16:15:53, 16:15:56, and 16:16:23 Asia/Taipei;
- an Android `!835d30ae` primary broadcast as `RECEIVED` on iOS at 16:16:12;
- the user manually confirmed the requested two-way phone-to-phone message flow passed.

Message bodies, PSKs, account data, and precise location were not extracted or recorded.

## Validation boundary

Per the user's request, validation stopped after the focused regression, successful signed device build/install, durable-state check, and successful physical two-way exchange. The large root test/lint matrix, soak testing, RF range testing, Doze/restoration stress, Release/TestFlight/App Store validation, and unrelated multi-radio scenarios were not rerun for this targeted correction.
