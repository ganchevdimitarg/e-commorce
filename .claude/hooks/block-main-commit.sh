#!/usr/bin/env bash
# PreToolUse — Bash. Blocks git commit on main/develop/master.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

guard_require command
CMD="$(json_field command)"
echo "$CMD" | grep -qE 'git\s+commit' || exit 0

BRANCH=$(git branch --show-current 2>/dev/null)
case "$BRANCH" in
  main|develop|master)
    guard_block "Blocked: direct commits to '$BRANCH' are not permitted. Create a feature branch: git checkout -b <type>/<scope>-<desc>"
    ;;
esac
exit 0
