# Agent Session Context - Meshtastic Android
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

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
