# AI Security Policy

How AI coding agents (e.g., Claude Code) may act in this repository. It is **defense-in-depth**:
this policy is the probabilistic layer (the agent reads and mostly follows it); the deterministic
layers are the `hooks/` (which the agent cannot bypass), the quality gate (`./mvnw clean verify`),
and human review on every pull request. A rule here is a guardrail, not the only line of defense.

## What the AI may do

**Read** — all source, tests, build config, and docs in this repo.

**Write** — source, tests, build/CI config, and docs, then verify with `./mvnw clean verify`.
Every change is reviewed by a human on the PR before merge.

**Requires explicit human approval** — pushing, force-pushing, opening/merging PRs, deleting
branches or files it did not create, changing `.github/` workflows or branch protection, and
adding or upgrading dependencies (supply-chain surface).

**Forbidden** — committing secrets in any form (see below); disabling or weakening a quality gate
or a hook to make a change pass; `@SuppressWarnings` / `// NOPMD` / `// CHECKSTYLE:OFF` without a
comment stating exactly why; irreversible shell commands (`rm -rf`, `git push --force`, `sudo`,
piping a download into a shell).

**Never send to an AI / external service** — real credentials, API keys, tokens, the Hue bridge
application key, or any personal data. Use placeholders (`${SMARTHOME_*}`) when discussing config.

## Secrets

- **No secrets in source — ever.** API keys, passwords, tokens, and the Hue bridge application key
  come from environment variables or runtime pairing, never committed. Config references them as
  placeholders (e.g. `app-key: ${SMARTHOME_HUE_APP_KEY}`).
- The backstops are `.gitignore` (keeps secret files out of the repo), `gitleaks` (scans every
  push/PR in CI), and the `validate-write` hook (blocks an edit that contains a secret pattern).
- **Hue application key at rest.** The bridge app key is held only in memory for the run
  (`HueBridgeService`), seeded from configuration/environment; it is never logged and never
  persisted by the current code. If a future change persists UI-configured integration credentials
  (ADR 5), they must be encrypted at rest and excluded from logs and API responses — pairing
  traffic to the Hue v1 API is already cleartext on the LAN, so the stored key is the asset to
  protect.

## Deterministic hooks (`hooks/`, wired in `.claude/settings.json`)

- **`validate-write.sh`** (PreToolUse) — blocks a Write/Edit whose content matches a high-confidence
  secret pattern (provider keys, private-key blocks, hard-coded credential assignments).
- **`validate-bash.sh`** (PreToolUse) — blocks irreversible/unsafe commands (`rm -rf`, force-push,
  `sudo`, `chmod 777`, `curl … | sh`, fork bombs).
- **`post-edit-lint.sh`** (PostToolUse) — instantly flags the highest-frequency CLAUDE.md forbidden
  patterns (`System.out`/`err`, `printStackTrace`) on a Java edit, ahead of the full gate.

Hooks run on the developer's machine and cannot be talked out of by the model — that is the point.
