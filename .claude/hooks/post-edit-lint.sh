#!/usr/bin/env bash
# PostToolUse advisory for Write/Edit: flag the highest-frequency CLAUDE.md forbidden patterns the
# moment they are written, ahead of the slower CheckStyle/PMD/SpotBugs gate. Fast and dependency-
# free (no Maven per edit). The tool call arrives as JSON on stdin; exit 2 surfaces the note to the
# agent (the edit has already happened — this is advice, not a block).
set -euo pipefail
payload="$(cat)"

# Only Java *files* carry these rules. Key off the edited file's path (not the content), so a doc
# that merely mentions these patterns — like AI-SECURITY-POLICY.md or a rule file — isn't flagged.
file="$(printf '%s' "$payload" \
  | grep -oE '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 \
  | sed -E 's/.*"file_path"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/')"
case "$file" in
  *.java) ;;
  *) exit 0 ;;
esac

note() {
  echo "Heads-up from post-edit-lint: $1 (CLAUDE.md forbidden pattern)." \
    "Fix before committing; the full gate is '(cd hub && ./mvnw clean verify)'." >&2
  exit 2
}

printf '%s' "$payload" | grep -Eq 'System\.(out|err)\.print' && note "System.out/err — log through SLF4J."
printf '%s' "$payload" | grep -Eq '\.printStackTrace\(' && note "printStackTrace() — log via SLF4J or rethrow."

exit 0
