#!/usr/bin/env bash
# PreToolUse guard for Write/Edit: refuse content that looks like a hard-coded secret.
# A deterministic backstop to CLAUDE.md's "no secrets in source" rule (defense-in-depth: the
# policy is probabilistic, this hook is not). The tool call arrives as JSON on stdin; exit 2
# blocks the edit and returns the message to the agent. Exit 0 allows it.
set -euo pipefail
# Strip backslashes so JSON-escaped quotes (\") match as plain quotes, regardless of encoding.
payload="$(cat | tr -d '\\')"

block() {
  echo "Blocked by validate-write: $1 Secrets come from environment variables, never source" \
    "— see AI-SECURITY-POLICY.md." >&2
  exit 2
}

# High-confidence provider credentials and private-key blocks: these do not appear in legitimate
# source, so a single match is a strong signal.
printf '%s' "$payload" | grep -Eq 'sk-ant-[A-Za-z0-9_-]{16,}' && block "Anthropic API key detected."
printf '%s' "$payload" | grep -Eq 'AKIA[0-9A-Z]{16}' && block "AWS access key detected."
printf '%s' "$payload" | grep -Eq 'AIza[0-9A-Za-z_-]{20,}' && block "Google API key detected."
printf '%s' "$payload" | grep -Eq 'gh[pousr]_[A-Za-z0-9]{20,}' && block "GitHub token detected."
printf '%s' "$payload" | grep -Eq 'xox[baprs]-[A-Za-z0-9-]{10,}' && block "Slack token detected."
printf '%s' "$payload" | grep -Eq -- '-----BEGIN [A-Z ]*PRIVATE KEY-----' && block "Private key detected."

# Hand-pasted credential assignment with an inline literal value. Environment-variable
# placeholders (${...}) are allowed — that is how config is supposed to reference secrets.
if printf '%s' "$payload" \
  | grep -Eiq '(password|passwd|secret|api[_-]?key|access[_-]?token)["'"'"' ]*[:=] *"[^"$][^"]{5,}'; then
  block "A credential appears to be hard-coded."
fi

exit 0
