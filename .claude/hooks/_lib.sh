#!/usr/bin/env bash
# Shared helpers for .claude/hooks. SOURCE this file; do not execute it.
# Caller must set:  INPUT=$(cat)  before using *_field / emit_context.

# Resolve a python interpreter once (correctness fallback for parsing/emit).
_PY="$(command -v python3 2>/dev/null || command -v python 2>/dev/null)"

# json_field <name> - echo a tool-input field from $INPUT.
# Claude Code nests tool params under .tool_input; we read there first and fall
# back to a top-level key. python-first (handles escaped quotes), sed fallback.
json_field() {
  local name="$1"
  if [ -n "$_PY" ]; then
    printf '%s' "${INPUT:-}" | "$_PY" -c '
import sys, json
name = sys.argv[1]
try:
    d = json.load(sys.stdin)
    src = d.get("tool_input", d) if isinstance(d, dict) else {}
    val = src.get(name)
    if val is None and isinstance(d, dict):
        val = d.get(name)
    print(val if val is not None else "")
except Exception:
    pass
' "$name" 2>/dev/null
    return
  fi
  # No python: best-effort sed (searches the whole payload, nesting-agnostic).
  printf '%s' "${INPUT:-}" \
    | sed -n "s/.*\"$name\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" \
    | head -n1
}

# json_field_multiline <name> - for fields that may contain newlines (file content).
# Reads .tool_input first; tries name, then common content keys.
json_field_multiline() {
  local name="$1"
  [ -n "$_PY" ] || return 0
  printf '%s' "${INPUT:-}" | "$_PY" -c '
import sys, json
name = sys.argv[1]
try:
    d = json.load(sys.stdin)
    src = d.get("tool_input", d) if isinstance(d, dict) else {}
    for k in (name, "content", "new_string", "new_content"):
        v = src.get(k)
        if v:
            print(v); break
except Exception:
    pass
' "$name" 2>/dev/null
}

# guard_require <name> - FAIL-CLOSED gate. MUST be called in the hook's main shell,
# never inside $(...) - an exit from a command substitution only kills the subshell.
# If the "name" key is present in raw $INPUT yet extraction is empty (parse failure
# or empty value), block. If the key is absent, return cleanly (not this hook's input).
guard_require() {
  local name="$1"
  if printf '%s' "${INPUT:-}" | grep -qF "\"$name\"" && [ -z "$(json_field "$name")" ]; then
    guard_block "Blocked: safety hook could not parse '$name' from tool input (fail-closed)."
  fi
}

# guard_block <message> - print to stderr and block.
guard_block() { echo "$1" >&2; exit 2; }

# resolve_module <file_path> - echo Maven module dir for the file:
#   nested "<seg>" when "<seg>/pom.xml" exists, else "." for the root pom, else empty.
resolve_module() {
  local file="$1" root seg
  root="$(git rev-parse --show-toplevel 2>/dev/null)" || return 0
  seg="${file%%/*}"
  if [ -n "$seg" ] && [ "$seg" != "$file" ] && [ -f "$root/$seg/pom.xml" ]; then
    printf '%s' "$seg"; return 0
  fi
  [ -f "$root/pom.xml" ] && printf '.'
}

# emit_context <message> - print {"additionalContext": "..."} to stdout (no block).
emit_context() {
  local msg="$1"
  if [ -n "$_PY" ]; then
    MSG="$msg" "$_PY" -c 'import os,json;print(json.dumps({"additionalContext":os.environ["MSG"]}))'
  else
    msg="${msg//\\/\\\\}"; msg="${msg//\"/\\\"}"; msg="${msg//$'\n'/\\n}"
    printf '{"additionalContext":"%s"}\n' "$msg"
  fi
}