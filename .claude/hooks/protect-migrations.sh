#!/usr/bin/env bash
# PreToolUse — Write|Edit. Blocks editing a Flyway migration already committed to git.
# New (untracked) migration files are always allowed.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

guard_require file_path
FILE="$(json_field file_path)"
echo "$FILE" | grep -qE 'db/migration/V[0-9]+__.*\.sql$' || exit 0

# Allow if the file is not yet tracked (new migration).
git ls-files --error-unmatch "$FILE" 2>/dev/null || exit 0

guard_block "Blocked: editing a committed Flyway migration is not permitted.
Committed migrations are immutable — their checksums are recorded in flyway_schema_history.
Create a new versioned migration instead: V<n+1>__<description>.sql"
