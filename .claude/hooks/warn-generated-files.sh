#!/usr/bin/env bash
# PreToolUse — Write|Edit. Blocks overwriting generated files before the write.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

guard_require file_path
FILE="$(json_field file_path)"
[ -z "$FILE" ] && exit 0

# Block writes inside generated-sources directories.
echo "$FILE" | grep -qE '(target/generated-sources|target/generated-test-sources)' && \
  guard_block "Blocked: '$FILE' is inside target/generated-sources — auto-generated, overwritten on next build.
Edit the source instead:
- Avro classes  → edit the .avsc in common-events/src/main/avro/ then run: ./mvnw generate-sources
- Lombok        → edit the @Lombok annotation on the source class
- MapStruct     → edit the mapper interface or @Mapper configuration"

# Block writes to Java files that already contain an @Generated annotation.
echo "$FILE" | grep -qE '\.java$' && [ -f "$FILE" ] && \
  grep -qE '@Generated(\s|\()|@javax\.annotation\.Generated|@jakarta\.annotation\.Generated' "$FILE" && \
  guard_block "Blocked: '$FILE' contains @Generated — auto-generated. Edit the source template or annotation instead, then regenerate."

exit 0
