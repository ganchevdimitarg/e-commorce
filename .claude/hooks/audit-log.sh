#!/usr/bin/env bash
# PreToolUse — Bash (async). Appends every shell command to .claude/audit.log.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

CMD="$(json_field command)"
SESSION=${CLAUDE_SESSION_ID:-unknown}
TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
BRANCH=$(git branch --show-current 2>/dev/null || echo "unknown")

LOG_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '.')/.claude"
mkdir -p "$LOG_DIR"
printf '%s\t%s\t%s\t%s\n' "$TIMESTAMP" "$SESSION" "$BRANCH" "$CMD" >> "$LOG_DIR/audit.log"
exit 0
