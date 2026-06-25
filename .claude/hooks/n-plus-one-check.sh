#!/usr/bin/env bash
# PostToolUse — Write|Edit (JPA repository/entity Java files).
# Scans for common N+1 query patterns; advisory only (fails open without python).
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

FILE="$(json_field file_path)"
echo "$FILE" | grep -qE '\.java$' || exit 0
[ -f "$FILE" ] || exit 0
[ -z "$_PY" ] && exit 0

WARN="$("$_PY" - "$FILE" << 'PYEOF'
import re, sys
with open(sys.argv[1]) as f:
    content = f.read()
warnings = []
if re.search(r'(?:fun|List<?[^>]*>?|Iterable<?[^>]*>?)\s+findAll\s*\(\s*\)', content):
    if 'Pageable' not in content and 'Page<' not in content:
        warnings.append("findAll() without Pageable — unbounded query loads the entire table. Add Pageable or a WHERE clause.")
for m in re.finditer(r'@(OneToMany|ManyToMany)(?!\s*\()', content):
    warnings.append(f"@{m.group(1)} without fetch = FetchType.LAZY — provider defaults vary. Declare fetch type explicitly.")
for m in re.finditer(r'@(OneToMany|ManyToMany)\s*\([^)]*fetch\s*=\s*(?:FetchType\.)?EAGER', content):
    warnings.append(f"@{m.group(1)}(fetch = EAGER) triggers N+1 on every parent load. Use LAZY + @EntityGraph on the query.")
if '@ManyToOne' in content and 'findAll' in content and '@EntityGraph' not in content:
    warnings.append("@ManyToOne alongside findAll — potential N+1. Use @EntityGraph to join-fetch associations.")
print("\n".join(f"- {w}" for w in warnings))
PYEOF
)"

[ -n "$WARN" ] && emit_context "N+1 / performance warnings in $FILE:
$WARN"
exit 0
