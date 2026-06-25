#!/usr/bin/env bash
# PreToolUse — Write|Edit (pom.xml). Blocks adding a <dependency> with an inline
# <version> to a non-root pom.xml. All versions must live in the root BOM.
INPUT=$(cat)
. "$(dirname "$0")/_lib.sh"

FILE="$(json_field file_path)"
echo "$FILE" | grep -qE 'pom\.xml$' || exit 0

# Never block the root BOM itself.
ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
[ "$(realpath "$FILE" 2>/dev/null)" = "$(realpath "$ROOT/pom.xml" 2>/dev/null)" ] && exit 0

CONTENT="$(json_field_multiline new_content)"
[ -z "$CONTENT" ] && [ -f "$FILE" ] && CONTENT="$(cat "$FILE")"
[ -z "$CONTENT" ] && exit 0
[ -z "$_PY" ] && exit 0   # advisory: cannot analyse without python

# Pass content via stdin (no string interpolation — avoids quote/backslash breakage).
printf '%s' "$CONTENT" | "$_PY" - << 'PYEOF'
import sys, re
content = sys.stdin.read()
violations = []
for block in re.findall(r'<dependency>.*?</dependency>', content, re.DOTALL):
    if '<version>' in block:
        art = re.search(r'<artifactId>([^<]+)</artifactId>', block)
        ver = re.search(r'<version>([^<]+)</version>', block)
        art_name = art.group(1) if art else "unknown"
        ver_val  = ver.group(1) if ver else "unknown"
        if not ver_val.startswith('${'):
            violations.append(f"  {art_name}: <version>{ver_val}</version>")
if violations:
    sys.stderr.write(
        "Blocked: service pom.xml must not declare dependency versions inline.\n"
        "All versions must be managed in the root pom.xml BOM.\n"
        "Remove <version> from these dependencies (or move them to root BOM):\n"
        + "\n".join(violations) + "\n")
    sys.exit(2)
PYEOF
exit $?
