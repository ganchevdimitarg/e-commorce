#!/usr/bin/env bash
# PostToolUse — Write|Edit (controller Java files).
# Checks @RequestMapping paths follow the /api/v{n}/ convention; advisory only.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

FILE="$(json_field file_path)"
echo "$FILE" | grep -qE '\.java$' || exit 0
[ -f "$FILE" ] || exit 0
grep -qE '@RestController|@Controller' "$FILE" || exit 0
[ -z "$_PY" ] && exit 0

VIOL="$("$_PY" - "$FILE" << 'PYEOF'
import re, sys
with open(sys.argv[1]) as f:
    content = f.read()
mappings = re.findall(r'@(?:Request|Get|Post|Put|Patch|Delete)Mapping\s*\(\s*(?:value\s*=\s*)?["\']([^"\']+)["\']', content)
class_mappings = re.findall(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']', content)
violations = []
for p in mappings + class_mappings:
    if p.startswith('/actuator') or p.startswith('/error'):
        continue
    if not p.startswith('/api/v'):
        violations.append(f"  '{p}' — expected /api/v{{n}}/...")
print("\n".join(violations))
PYEOF
)"

[ -n "$VIOL" ] && emit_context "API versioning violation in $FILE:
$VIOL
All endpoints must use the /api/v{n}/ prefix. See CLAUDE.md ## Architecture."
exit 0
