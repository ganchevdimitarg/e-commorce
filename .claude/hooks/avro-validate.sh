#!/usr/bin/env bash
# PostToolUse — Write|Edit (.avsc). Validates Avro schema (JSON, required keys,
# every field has a default) then regenerates sources. Advisory (fails open).
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

FILE="$(json_field file_path)"
echo "$FILE" | grep -qE '\.avsc$' || exit 0
[ -f "$FILE" ] || exit 0
[ -z "$_PY" ] && exit 0

ISSUES="$("$_PY" - "$FILE" << 'PYEOF'
import json, sys
try:
    with open(sys.argv[1]) as f:
        schema = json.load(f)
except json.JSONDecodeError as e:
    print(f"- not valid JSON: {e}")
    sys.exit(0)
issues = []
if schema.get("type") != "record":
    issues.append("schema 'type' must be 'record'")
if not schema.get("name"):
    issues.append("schema is missing 'name'")
if not schema.get("namespace"):
    issues.append("schema is missing 'namespace' (required for Schema Registry subject naming)")
fields = schema.get("fields", [])
if not fields:
    issues.append("schema has no 'fields'")
no_default = [f.get("name", "?") for f in fields if "default" not in f]
if no_default:
    issues.append(f"fields missing 'default' (breaks BACKWARD compatibility): {', '.join(no_default)}. Add a default to every field.")
print("\n".join(f"- {i}" for i in issues))
PYEOF
)"

if [ -n "$ISSUES" ]; then
  emit_context "Avro schema issues in $FILE:
$ISSUES"
  exit 0
fi

# Schema valid → regenerate Java sources for the owning module.
MODULE="$(resolve_module "$FILE")"
[ -z "$MODULE" ] && exit 0
if [ "$MODULE" = "." ]; then PL=(); else PL=(-pl "$MODULE"); fi
OUTPUT=$(./mvnw generate-sources "${PL[@]}" -q 2>&1)
[ $? -eq 0 ] && exit 0
emit_context "Avro code generation failed after editing $FILE:
$(echo "$OUTPUT" | head -20)"
exit 0
