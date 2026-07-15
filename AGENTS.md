# NTsocial MeshLink Android - Unified Agent & Developer Guide

<role>
You are an expert Android/KMP engineer working on NTsocial MeshLink, a GPL-3.0 fork of Meshtastic Android. Maintain architectural boundaries, preserve upstream-compatible Meshtastic radio/service/database/settings behavior, and use MAD standards with Compose Multiplatform + Navigation 3.
</role>

<context_and_memory>
- **Project Identity:** App display name is `NTsocial MeshLink`. Application ID is `com.ntsocial.meshlink`. Project-owned source packages use `com.ntsocial.meshlink.*`.
- **Current Status:** This is still an early fork, but Gateway v1 is implemented and hardware-validated: protected Provider snapshots, single-use command capabilities, explicit command/event IPC, canonical NTsocial channel provisioning, and an in-memory envelope cache are concrete code. RF scheduler expansion, node policy, persistent/reliable delivery, and remote RF-reception verification remain follow-up work.
- **Project Goal:** Build an open-source transport bridge between the NTsocial App and Meshtastic radios. The NTsocial App owns social UX and canonical history; this Gateway owns Meshtastic radio control, LoRa transport, cache, matching, node policy, and IPC.
- **Upstream Base:** This fork is based on `meshtastic/Meshtastic-Android`. Preserve upstream attribution and compatibility unless a scoped NTsocial change explicitly requires divergence.
- **Tech:** Kotlin 2.3+ with JDK 21, Ktor, Okio, Room KMP, DataStore, Koin 4.2+, Compose Multiplatform, Material 3 Expressive, and Navigation 3.
- **Local Build Status (2026-07-15):** Android Studio Gradle Sync and the full local baseline `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache` pass with the bundled Android Studio JBR 21 after selecting G1GC. The Google universal debug APK also packages successfully. This verifies local compilation, static checks, tests, and debug packaging; it does not establish Play release readiness.
- **Play Release Status (2026-07-15):** No Play-uploadable AAB has been validated or added to Git. The local Google release trial reached R8, then the production mapping upload rejected the repository's dummy Firebase configuration. A real upload keystore and the authorized production Google/Firebase/DataDog configuration are required for the unchanged official release workflow. Never present the build-script fallback debug signature as Play-ready or disable production mapping uploads merely to claim release success.
- **Agent Memory:** Consult `.agent_memory/session_context.md` for the latest task-specific handovers and project state.
- **Skills Directory (CONSULT THESE FIRST):**
  - `.skills/project-overview/` - Codebase map, namespacing, **Bootstrap Steps**.
  - `.skills/kmp-architecture/` - Expect/actual, source-sets, conventions.
  - `.skills/compose-ui/` - Adaptive UI, **String Resources (consult `strings-index.txt` first)**.
  - `.skills/navigation-and-di/` - Navigation 3 & Koin annotations.
  - `.skills/testing-ci/` - Validation commands, **CI Architecture**.
  - `.skills/ci-cost-control/` - **CI Budgeting & Monitoring**.
  - `.skills/implement-feature/` - Feature workflow.
  - `.skills/code-review/` - **PR & Commit Hygiene**, validation checklist.
  - `.skills/new-branch/` - Branching and rebasing recipes.
  - `.skills/speckit/` - **Spec Kit SDD workflow**, slash commands, constitution, feature specs.
</context_and_memory>

<architecture_boundaries>
- **Project Namespace:** New project-owned code must use `com.ntsocial.meshlink.*`. Do not introduce new `org.meshtastic.*` or `com.geeksville.mesh` project packages.
- **Protocol Boundary:** Keep generated upstream Meshtastic protobufs under `org.meshtastic.proto`. Do not edit `core/proto` unless the user explicitly requests upstream protocol/submodule work.
- **Semantic Meshtastic Names:** Existing identifiers such as `MeshtasticNavDisplay`, `MeshtasticNavigationSuite`, `MeshtasticBleConstants`, and `MeshtasticDatabase` may remain when they describe upstream protocol, device, database, or shell semantics. Do not perform cosmetic class renames without a scoped migration plan.
- **Gateway Boundary (implemented v1):** New NTsocial integrations must use the protected Gateway IPC. `NtsocialGatewayService`/AIDL remains a deprecated compatibility adapter only; do not add features that bind it or directly bind `IMeshService`. New outbound traffic uses `PRIVATE_APP / port 256`; legacy `497` is receive-only compatibility.
- **Gateway Provider:** The authority is `${applicationId}.gateway`; v1 read-only endpoints are `/v1/status`, `/v1/envelopes`, `/v1/nodes`, and `/v1/channels`. Do not add unversioned endpoints, mutations, selection/sort semantics, or canonical NTsocial history to this Provider.
- **Gateway Commands:** A parent App first obtains a short-lived, single-use capability tied to `request_id` from the Provider, then sends an explicit `com.ntsocial.meshlink.gateway.COMMAND`. Android 8-13 relies on that consumed capability; Android 14+ also verifies the broadcast sender UID, package, and certificate. Never trust caller-supplied package names or ports, and never use implicit broadcasts.
- **Gateway Events:** `com.ntsocial.meshlink.gateway.EVENT` is explicitly package-scoped and metadata-only. It may carry event type, URI, request ID, packet ID, or rejection reason; it must never carry message bytes, destinations, precise location, PSKs, tokens, or radio configuration. A client re-queries the Provider after an event.
- **Trusted Pairing:** The in-process UID/package/certificate verifier is authoritative. Debug parents are accepted only by a debuggable MeshLink host. A parent package or signer change requires a synchronized update to verifier rules, known-signer resources, package visibility, and both build configurations; never broaden this to arbitrary installed Apps.
- **Outbound Transport Contract:** External commands accept only a validated complete `NM` envelope, unchanged, on the canonical default RF channel and `PRIVATE_APP / port 256`; the complete external envelope maximum is 180 bytes. `497` is never an outbound command port.
- **Cache and Acceptance Semantics:** The bridge cache is transient, in-memory, and bounded to 128 envelopes; NTsocial remains the social UX and canonical-history owner. `COMMAND_ACCEPTED` means MeshLink accepted a command into the local radio send queue, not that a remote radio received it.
- **Node Policy:** `rebroadcast_mode = ALL` is an NTsocial Node Policy applied with clear user consent and verification, not a silent forced configuration.
- **Channel Model:** NTsocial `channelId` is the logical route. Meshtastic `channelIndex` is the RF lane. Many-to-one channel binding is valid.
- **Parent Channel Opt-In:** MeshLink default-channel readiness must never auto-bind, restore, or choose a parent NTsocial logical channel. The parent owns the user's per-channel LoRa on/off decision; MeshLink only verifies that an opted-in command uses the canonical ready RF index.
- **Built-In NTsocial Channel:** NTsocial MeshLink must bundle the canonical public NTsocial Meshtastic channel and automatically ensure it is registered on every connected node after node DB readiness. Provisioning may update an existing NTsocial-named or same-PSK slot, add a secondary slot, replace the last secondary when full, and replace primary only on one-channel radios. This automatic channel registration does not require a user confirmation dialog.
- **LoRa Config Preservation:** Built-in NTsocial channel provisioning may apply the QR LoRa/RF config only when the radio is effectively unconfigured, such as missing LoRa config or `region == UNSET`; never overwrite an already configured region/frequency/preset.
- **LoRa Payload Policy:** Do not route image bytes, voice bytes, or PTT media over LoRa. LoRa roadmap payloads are text, state/control, profile/channel snapshots, roster/probe data, chunks, and receipts.
- **Open-Source Boundary:** This GPL fork must not embed closed NTsocial App business logic, private assets, secrets, or production credentials. Protocol/schema/IPC work intended for this Gateway should remain open.
- **Restricted Data Surface:** Raw envelope bytes are available only to a verified Provider client. Node/channel snapshots must not grow to expose positions, PSKs, RF configuration, notes, or raw protobufs, and none of those values may appear in events or logs.
- **Device Validation (2026-07-10):** A normal NTsocial LoRa-enabled channel was tested on device through parent -> Provider/capability -> explicit command -> MeshLink -> connected radio firmware queue. The run used only port 256 and did not use AIDL or outbound 497. This proves local radio-queue acceptance, not remote over-air reception; the latter needs a second receiver or a return message.
</architecture_boundaries>

<ui_branding>
- **Current Skin:** Phase 1 NTsocial skinning is design-token based. Non-Dynamic themes use NTsocial indigo primary, emerald secondary, amber status emphasis, gray surfaces, and mixed monospace typography for compact metadata.
- **Theme Contracts:** Preserve `AppTheme(darkTheme, dynamicColor, content)`, `MaterialExpressiveTheme`, `MODE_DYNAMIC`, Dynamic Color behavior, `ThemePickerDialog`, `UiPrefsImpl`, and existing theme-selection flow.
- **Navigation Shell:** Keep the current `MeshtasticNavigationSuite`, `MeshtasticNavDisplay`, Navigation 3, and adaptive shell unless the user explicitly asks for a navigation redesign.
- **Branding Assets:** Do not reuse the upstream Meshtastic logo as primary NTsocial branding. No NTsocial logo asset is currently established in this repo.
- **Public Docs:** Public identity docs should be truthful about implementation status. Traditional Chinese-first wording is preferred for project-facing identity docs, with concise English summaries where useful.
</ui_branding>

<process_essentials>
- **Think First:** Read only what you need. Consult indices such as `strings-index.txt` before reading large resource files.
- **Hygiene:** Run `python3 scripts/sort-strings.py` after adding string resources to maintain organization and update the index.
- **Memory Persistence:** Update `.agent_memory/session_context.md` at the end of every session or major task.
- **Bootstrap First:** Run the mandatory bootstrap steps in `.skills/project-overview/SKILL.md` before any Gradle build.
- **Validation Environment:** Use JDK 21 and a valid `ANDROID_HOME`. For local validation, set `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"` when tests depend on English resources.
- **Gradle JVM Compatibility:** Root `gradle.properties` intentionally uses `-XX:+UseG1GC`. The currently configured Android Studio JBR 21 reports `Option -XX:+UseZGC not supported`; do not restore `UseZGC` or `ZGenerational` unless the exact build JVM has first been verified to support them.
- **Plan Before Execution:** Use `.agent_plans/` (git-ignored) for complex refactors.
- **Baseline Verification:** Always run `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests` for implementation changes. Use `--no-configuration-cache` when cache or stale problems-report issues interfere.
</process_essentials>

<rules>
- **Token Hygiene:** Never read binary files such as PNG, MP3, APK, or large non-code resources unless essential. Use file paths to reason about assets.
- **Context Discipline:** Limit context to relevant modules. Do not vacuum the entire codebase for localized fixes.
- **No Lazy Coding:** Do not use placeholders such as `// ... existing code ...`. Provide complete, valid code.
- **No Framework Bleed:** Never import `java.*` or `android.*` in `commonMain`. Use KMP equivalents such as Okio, `Mutex`, and atomicfu.
- **CMP Over Android:** Use Compose Multiplatform constraints. Pre-format floats with `NumberFormatter.format()`. Use `MeshtasticNavDisplay` and `NavigationBackHandler`.
- **Zero Lint Tolerance:** Work is incomplete if `detekt` or `spotlessCheck` fails.
- **Verify Before Push:** Treat any push as verify-then-push. Check GitHub Actions state before pushing when GitHub context is relevant.
- **Never Touch Protos or Secrets:** `core/proto` is an upstream submodule. Secrets are git-ignored and must not be logged, committed, or exposed.
- **Privacy First:** Never log or expose PII, precise location, private messages, cryptographic keys, tokens, or pairing credentials.
- **Truthful Status:** Do not describe planned NTsocial gateway features as shipped. Mark roadmap, docs, and UI placeholders clearly when behavior is not implemented yet.
</rules>

<documentation_sync>
`AGENTS.md` is the source of truth for rules and principles. `.github/copilot-instructions.md` provides a quick-reference subset optimized for Copilot sessions, including build commands, task naming, and conventions. `CLAUDE.md` and `GEMINI.md` redirect here. When this guide changes in a way that affects day-to-day commands or naming, keep the quick-reference docs aligned.
</documentation_sync>

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan.
<!-- SPECKIT END -->
