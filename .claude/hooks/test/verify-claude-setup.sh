#!/usr/bin/env bash
# Drift-guard for the Claude Code setup. Run from repo root.
# Exit 0 only if every invariant holds. Prints OK:/FAIL: per check.
set -u
ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT" || exit 2
fail=0
ok()   { echo "OK:   $1"; }
bad()  { echo "FAIL: $1"; fail=1; }

MODULES="authentication catalog gateway notification order payment profile client config-server eureka-server"

# 1. No per-module .claude/ trees.
for m in $MODULES; do
  if [ -d "$m/.claude" ]; then bad "$m/.claude exists (must be hoisted to root)"; else ok "no $m/.claude"; fi
done

# 2. Every @import in every CLAUDE.md resolves, relative to the importing file's dir.
# Use here-strings (not process substitution / pipes) so the loop runs in the main
# shell — `bad` must mutate $fail, and Windows git-bash mishandles `< <(...)`.
CLAUDE_FILES="$(find . -name CLAUDE.md -not -path '*/node_modules/*')"
while IFS= read -r f; do
  [ -z "$f" ] && continue
  d="$(dirname "$f")"
  imports="$(grep -hE '^@' "$f" 2>/dev/null)"
  [ -z "$imports" ] && continue
  while IFS= read -r imp; do
    imp="${imp#@}"; imp="${imp%% *}"
    [ -z "$imp" ] && continue
    if [ -f "$d/$imp" ]; then ok "@import $imp (from $f)"; else bad "missing @import '$imp' in $f"; fi
  done <<< "$imports"
done <<< "$CLAUDE_FILES"

# 3. Root CLAUDE.md line budget (<= 230 incl. tables/headers).
rc=$(wc -l < CLAUDE.md)
[ "$rc" -le 230 ] && ok "root CLAUDE.md ${rc} lines (<=230)" || bad "root CLAUDE.md ${rc} lines (>230)"

# 4. Module CLAUDE.md line budget (<= 80).
for m in $MODULES; do
  [ -f "$m/CLAUDE.md" ] || continue
  c=$(wc -l < "$m/CLAUDE.md")
  [ "$c" -le 80 ] && ok "$m/CLAUDE.md ${c} lines (<=80)" || bad "$m/CLAUDE.md ${c} lines (>80)"
done

# 5. mcp.json catalogue 'live' set == enabledMcpjsonServers.
PY="$(command -v python3 || command -v python)"
if [ -n "$PY" ] && [ -f .claude/mcp.json ] && [ -f .claude/settings.local.json ]; then
  "$PY" - <<'EOF' || fail=1
import json,sys
cat=json.load(open(".claude/mcp.json")).get("available",{})
live=sorted(k for k,v in cat.items() if v.get("status")=="live")
enabled=sorted(json.load(open(".claude/settings.local.json")).get("enabledMcpjsonServers",[]))
if live==enabled: print("OK:   mcp catalogue live == enabledMcpjsonServers", live)
else: print("FAIL: mcp live", live, "!= enabled", enabled); sys.exit(1)
EOF
fi

exit $fail
