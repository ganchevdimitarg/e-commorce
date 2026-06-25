#!/usr/bin/env bash
# Stop hook — saves session state each time Claude finishes a turn.
# Lets Claude resume context after /compact or session restart without tool calls.

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
BRANCH=$(git branch --show-current 2>/dev/null || echo "unknown")
RECENT=$(git log --oneline -5 2>/dev/null || echo "none")
UNCOMMITTED=$(git diff --name-only 2>/dev/null | head -20)
TIMESTAMP=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

cat > "$REPO_ROOT/.claude/session-checkpoint.md" << CHECKPOINT
# Session checkpoint — $TIMESTAMP

## Branch
$BRANCH

## Recent commits
$RECENT

## Uncommitted files at checkpoint
$UNCOMMITTED

## Resume instruction
Read this file at the start of the next session to resume context.
Check git status and the above branch/commits to understand where work stopped.
CHECKPOINT

echo "Session checkpoint saved to .claude/session-checkpoint.md" >&2

# --- Sync ## Active work section in MEMORY.md ---
. "$(dirname "$0")/_lib.sh"
MEMORY_FILE="$REPO_ROOT/MEMORY.md"
if [ -f "$MEMORY_FILE" ] && [ -n "$_PY" ]; then
  "$_PY" -c "
import re, sys

memory_path = sys.argv[1]
branch = sys.argv[2]
uncommitted = sys.argv[3]

with open(memory_path, 'r', encoding='utf-8') as f:
    content = f.read()

file_list = uncommitted.strip()
if file_list:
    bullet_lines = '\n'.join(f'- {line}' for line in file_list.splitlines())
    new_section = f'## Active work\n- Branch: \`{branch}\`\n{bullet_lines}\n'
else:
    new_section = f'## Active work\n- Branch: \`{branch}\`\n- No uncommitted files\n'

# Replace content between ## Active work and the next ## header (or EOF)
pattern = r'## Active work\n.*?(?=\n## |\Z)'
if re.search(pattern, content, re.DOTALL):
    content = re.sub(pattern, new_section.rstrip() + '\n', content, count=1, flags=re.DOTALL)
else:
    content = content.rstrip() + '\n\n' + new_section

with open(memory_path, 'w', encoding='utf-8') as f:
    f.write(content)
" "$MEMORY_FILE" "$BRANCH" "$UNCOMMITTED" 2>/dev/null && \
  echo "MEMORY.md ## Active work section updated" >&2 || \
  echo "MEMORY.md update skipped (Python not available or error)" >&2
fi

exit 0
