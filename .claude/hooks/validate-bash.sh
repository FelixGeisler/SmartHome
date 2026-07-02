#!/usr/bin/env bash
# PreToolUse guard for Bash: refuse irreversible or unsafe commands. Deterministic guardrail
# (defense-in-depth). The tool call arrives as JSON on stdin; exit 2 blocks it, exit 0 allows it.
set -euo pipefail
payload="$(cat)"

block() {
  echo "Blocked by validate-bash: $1 If this is truly intended, run it yourself outside the agent." >&2
  exit 2
}

# Recursive force-remove in any flag order (rm -rf, rm -fr, rm -Rfv, …).
printf '%s' "$payload" \
  | grep -Eq 'rm[[:space:]]+-[A-Za-z]*r[A-Za-z]*f|rm[[:space:]]+-[A-Za-z]*f[A-Za-z]*r' \
  && block "recursive force-remove (rm -rf)."

# Force-push rewrites shared history; --force-with-lease is allowed.
printf '%s' "$payload" \
  | grep -Eq 'git[[:space:]]+push([[:space:]]+[^|;&]*)?[[:space:]](--force([[:space:]]|$)|-f([[:space:]]|$))' \
  && block "force-push (rewrites shared history)."

printf '%s' "$payload" | grep -Eq '(^|[[:space:];&|])sudo[[:space:]]' && block "sudo (privilege escalation)."
printf '%s' "$payload" | grep -Eq 'chmod[[:space:]]+(-[A-Za-z]+[[:space:]]+)*777' && block "chmod 777 (world-writable)."
printf '%s' "$payload" | grep -Eq '(curl|wget)[[:space:]][^|]*\|[[:space:]]*(sudo[[:space:]]+)?(ba)?sh' \
  && block "piping a download straight into a shell."
printf '%s' "$payload" | grep -Eq ':\(\)[[:space:]]*\{[[:space:]]*:' && block "fork bomb."

exit 0
