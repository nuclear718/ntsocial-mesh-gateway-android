# Data Model: Apple Gateway v1

The implemented shared database is `<App Group container>/gateway-v1.sqlite`, schema/user version 1. All identifiers are validated text or bounded blobs; times are Unix milliseconds; `radio_generation` is opaque random text. SQLite opens with a 5-second busy timeout, foreign keys, WAL, `synchronous=FULL`, FULLMUTEX connections, and `BEGIN IMMEDIATE` write transactions.

The App Group database is a projection/mailbox, not either app's canonical database. It contains no PSK, precise position, raw radio configuration/protobuf, private history, account credential, or HMAC key.

## Shared tables

### `gateway_meta`

Singleton row (`singleton_id = 1`) with:

- `schema_version`
- `provider_instance_id`
- `readiness`
- opaque text `radio_generation`
- nullable `history_epoch`
- `overlay_high_water`
- `native_text_high_water`
- `active_key_version`
- `updated_at_millis`

The writer rejects a database whose `PRAGMA user_version` is newer than the supported version. Fresh creation is transactional.

### `gateway_caller_projection`

Primary key `caller_id`, plus active key version, revocation flag, and last-seen time. This is authorization metadata only; it never contains key bytes. Version 1 authorizes the exact parent caller `com.ntsocial.ios`.

### `channel_projection`

Primary key `(radio_generation, slot_index)` with non-unique `source_channel_id`, display name, role, security class, capability set, projected route token, and expiry.

The projected token lets the parent return a short-lived capability, but is not authoritative. The in-memory `AppleGatewayRouteRegistry` is the only source of truth and additionally binds the token to caller, source channel, captured slot, current generation, and expiry.

### `command_inbox`

Primary key `(caller_id, client_message_id)`. The parent inserts one immutable request containing:

- schema/request/caller/client/source/generation/route facts;
- issued/expiry times, active key version, and 16-byte nonce;
- command type and bounded body;
- optional overlay destination, hop limit, and acknowledgment request;
- 32-byte HMAC authentication tag and receive time.

The parent insert is `INSERT OR IGNORE`; the companion validates all semantics independently. Processing state is not stored in this row.

### `command_claim`

Primary key `(caller_id, client_message_id)` and foreign key to `command_inbox`, with provider instance and claim time. A new provider process can immediately reclaim a claim from the prior provider instance. The same provider can reclaim after 30 seconds. Commands with terminal `ACCEPTED_LOCAL` or `REJECTED` results are not claimed again; transient failure releases its claim.

### `command_result`

Primary key `(caller_id, client_message_id, result_seq)` with append-only state, nullable deterministic packet ID, nullable safe rejection reason, and update time.

Defined states are `PENDING_PROVIDER_WAKE`, `PROCESSING`, `ACCEPTED_LOCAL`, `REJECTED`, and `QUEUED_RADIO`. The current provider engine appends `ACCEPTED_LOCAL`, `REJECTED`, or retryable `PENDING_PROVIDER_WAKE`; no state implies RF or remote delivery.

### `overlay_ingress`

Primary key `(history_epoch, change_seq)` with stable source-channel/message/node identity, unsigned packet ID stored as integer, port number, complete raw `NM` envelope, and receive time.

- Only validated complete envelopes on port 256 or receive-only port 497 are admitted.
- Sequence allocation and insertion occur in one transaction.
- After every insertion, the table is trimmed to the newest 128 rows globally by receive time/epoch/sequence.
- Reads require the epoch, `after >= 0`, and a limit from 1 through 128. A dedicated epoch-scoped high-water read comes from `overlay_epoch_state`, not the maximum retained row.

### `overlay_epoch_state`

Primary key `history_epoch`, with monotonic `high_water`. It is additive within schema/user version 1, is backfilled
transactionally from retained `overlay_ingress` rows when absent, advances atomically with every overlay append, survives
the 128-row retention trim, and clears only on explicit reset. This prevents sequence reuse after every row for an epoch
has been evicted.

### `native_message_change`

Primary key `(history_epoch, change_seq)` with stable source-channel/message identity, stable sender node ID, unsigned packet ID, validated broadcast text, receive time, and nullable `origin_client_message_id`.

- It is an insertion feed, not an update/delete feed.
- Runtime supplies only rows whose stable identities were captured at private-Room insertion time; nullable legacy identities are excluded and never reconstructed from the current slot.
- Reads require the epoch and nonnegative `after`; page size defaults to 100 and is capped at 200.
- A dedicated epoch-scoped high-water read uses the same stable-only table.

### `consumer_cursor`

Primary key `(caller_id, stream_name)` with history epoch, committed sequence, and update time. Within one epoch, commits cannot move backward. A changed epoch may reset the committed sequence. The parent advances only after committing accepted data to its canonical store.

### `used_nonce`

Primary key `(caller_id, key_version, nonce)` with canonical client ID, request fingerprint, and expiry. Expired rows are removed transactionally during reservation. The exact same client ID/fingerprint may resume after a provider crash; the nonce with a different client ID or fingerprint is a replay rejection.

## MeshLink-private state

### Authoritative route registry

In-memory only. Each process starts with a new 32-byte CSPRNG Base64URL generation. A channel snapshot or routing-context inequality rotates generation and clears all routes. Each issued route uses a separate 32-byte CSPRNG Base64URL token and a 120-second TTL.

### `private_ledger`

Stored in the companion's private Application Support directory, never the App Group. Primary key `(caller_id, client_message_id)` with request fingerprint, `PENDING`/`ACCEPTED`, deterministic packet ID, and insertion sequence.

- Maximum 256 insertion-ordered records per caller.
- No TTL.
- Exact fingerprint replay returns the existing packet/result.
- Different fingerprint reuse is `IDEMPOTENCY_CONFLICT`.
- A pending reservation survives process restart and reuses the same deterministic packet ID.

### Private Room and durable queue

The existing Room database remains the canonical radio/cache store. `IosDurableMessageQueue` treats Room `QUEUED` packet rows as restart/reconnect work records. The Apple Gateway result becomes `ACCEPTED_LOCAL` only after the radio port has durably admitted the Room packet and queue work and the private ledger has been marked accepted.

A Gateway-originated queued row retains its accepted `gateway_source_channel_id`. At actual drain, the runtime revalidates
the exact active session/ingress plus the current slot's PSK/LoRa-derived source identity, and holds the operation boundary
through exact-session packet admission and matching firmware QueueStatus. A changed identity marks the old work fail closed;
the same numeric slot is not sufficient.

### Host exact-readback owner

One process-local owner/token reserves a firmware `69420` config-only readback only after prior FULL Stage 2 is complete
and no other handshake owner exists. Its dedicated completion flow binds the response to the exact configured session;
stale/parallel FULL responses, old sessions, and generic generation movement cannot complete it. If its caller times out or
cancels and firmware never returns, the same epoch remains fail closed until reconnect/new epoch (known bounded-liveness P2).

## Command transition rules

```text
immutable inbox row
→ reclaimable command claim
→ structural/time/caller/key validation
→ constant-time HMAC verification
→ authoritative route resolution
→ nonce reservation
→ body validation
→ private idempotency reservation
→ durable Room + retry-queue admission
→ private ledger ACCEPTED
→ append ACCEPTED_LOCAL result
```

An identical already-accepted command skips radio admission and appends/reuses the accepted outcome. A permanent failure appends `REJECTED`. A transient queue/radio failure appends `PENDING_PROVIDER_WAKE`, releases the claim, and is eligible for retry. A crash after local admission but before the final accepted ledger/result remains a retry boundary; the deterministic packet and exact-content checks reduce local duplication but do not constitute exactly-once RF delivery.

## Wake and consistency rules

- Parent command hint: `com.ntsocial.meshlink.gateway.command-available`.
- Companion state hint: `com.ntsocial.meshlink.gateway.state-changed`.
- Both notifications are payload-free and unauthenticated hints; readers always reread SQLite and verify durable state.
- `ntsocial-meshlink://process` requests a foreground handoff only.
- Source entitlement strings do not prove real sandbox access; matching signed provisioning for both apps remains a release gate.
