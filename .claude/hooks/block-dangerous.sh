#!/usr/bin/env bash
# PreToolUse — Bash. Blocks destructive shell commands. Fail-closed.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

guard_require command     # fail-closed if "command" present but unparseable
CMD="$(json_field command)"
[ -z "$CMD" ] && exit 0   # key absent / genuinely empty -> nothing to guard

# rm -rf / rm -fr (both flag orderings)
echo "$CMD" | grep -qE '(^|\s|/)rm\s+(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r|--recursive.*--force|--force.*--recursive)' && \
  guard_block "Blocked: 'rm -rf' / 'rm -fr' is not permitted. Use explicit paths or 'git clean -fd'."

# Force-push to protected branches
echo "$CMD" | grep -qE 'git\s+push.*--(force|force-with-lease)' && \
  echo "$CMD" | grep -qE '(origin\s+(main|develop|master)|origin/(main|develop|master))' && \
  guard_block "Blocked: force-push to main/develop/master is not permitted."

# git add -A / --all
echo "$CMD" | grep -qE 'git\s+add\s+(-A|--all)\b' && \
  guard_block "Blocked: 'git add -A' is not permitted — stage explicit file paths."

# Destructive SQL (must live in Flyway migrations)
echo "$CMD" | grep -qiE '(DROP\s+TABLE|DROP\s+DATABASE|DROP\s+SCHEMA|TRUNCATE\s+TABLE)' && \
  guard_block "Blocked: destructive SQL (DROP/TRUNCATE) must live in a Flyway migration."

# DELETE without WHERE — block any 'DELETE FROM' that has no WHERE clause
# (covers quoted, unquoted, and schema-qualified table names without fragile table-name regex)
echo "$CMD" | grep -qiE 'DELETE\s+FROM\s' && \
  ! echo "$CMD" | grep -qiE 'DELETE\s+FROM\s+.*\sWHERE\s' && \
  guard_block "Blocked: DELETE without WHERE detected — add a WHERE clause or soft-delete (deleted_at = now())."

# Bulk Docker teardown
echo "$CMD" | grep -qE 'docker\s+(rm|stop|kill)\s+\$\(docker\s+(ps|container)' && \
  guard_block "Blocked: bulk Docker container removal/stop is not permitted in agentic sessions."

exit 0
