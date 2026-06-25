#!/usr/bin/env bash
# PostToolUse — Write (db/migration/*.sql). Runs flyway:validate.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

FILE="$(json_field file_path)"
echo "$FILE" | grep -qE 'db/migration/.*\.sql$' || exit 0

MODULE="$(resolve_module "$FILE")"
[ -z "$MODULE" ] && exit 0
if [ "$MODULE" = "." ]; then PL=(); else PL=(-pl "$MODULE"); fi

OUTPUT=$(./mvnw flyway:validate "${PL[@]}" -q 2>&1)
[ $? -eq 0 ] && exit 0

REASON=$(echo "$OUTPUT" | grep -E 'ERROR|WARN|FlywayException|Validate' | head -10)
emit_context "Flyway validation failed after writing $FILE:
$REASON
Check migration version, filename format (V<n>__<desc>.sql), and checksum."
exit 0
