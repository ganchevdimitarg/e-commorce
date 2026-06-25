#!/usr/bin/env bash
# PostToolUse — Write|Edit (.java). Runs Checkstyle; injects violations as context.
# Escape hatch: CLAUDE_HOOK_SKIP_CHECKSTYLE=1 disables for bulk-edit sessions.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

FILE="$(json_field file_path)"
echo "$FILE" | grep -qE '\.java$' || exit 0
[ -f "$FILE" ] || exit 0
[ "${CLAUDE_HOOK_SKIP_CHECKSTYLE:-0}" = "1" ] && exit 0

MODULE="$(resolve_module "$FILE")"
[ -z "$MODULE" ] && exit 0
if [ "$MODULE" = "." ]; then PL=(); else PL=(-pl "$MODULE"); fi

OUTPUT=$(./mvnw checkstyle:check "${PL[@]}" -q 2>&1)
[ $? -eq 0 ] && exit 0

VIOLATIONS=$(echo "$OUTPUT" | grep -E '\[WARN\]|\[ERROR\]' | head -20)
emit_context "Checkstyle violations in $FILE:
$VIOLATIONS
Fix before proceeding."
exit 0
