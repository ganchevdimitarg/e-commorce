#!/usr/bin/env bash
# SessionStart hook
# Injects git context at the start of every Claude Code session.
# Saves Claude 2-3 tool calls to orient itself.
# Assumption: project uses a flat module layout (all modules at repo root depth 1).
# If modules are nested deeper (e.g. services/<service-name>), increase -maxdepth to 3.

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
BRANCH=$(git branch --show-current 2>/dev/null || echo "unknown")
RECENT=$(git log --oneline -8 2>/dev/null || echo "no commits")
UNCOMMITTED=$(git diff --name-only HEAD 2>/dev/null | wc -l | tr -d ' ')
UNTRACKED=$(git ls-files --others --exclude-standard 2>/dev/null | wc -l | tr -d ' ')
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "no tags")

# W4: -maxdepth 2 covers flat layout (root pom + module/pom.xml).
# Change to -maxdepth 3 for nested layouts (e.g. services/<service-name>/pom.xml).
MODULES=$(find "$REPO_ROOT" -maxdepth 2 -name pom.xml 2>/dev/null \
  | grep -v "^$REPO_ROOT/pom.xml$" \
  | sed "s|$REPO_ROOT/||;s|/pom.xml||" \
  | sort | tr '\n' ' ')

# Extract issue number from branch name if present (e.g. feat/<service-name>-142-retry)
ISSUE=$(echo "$BRANCH" | grep -oE '[0-9]{2,}' | head -1)
ISSUE_REF=${ISSUE:+"Refs #$ISSUE"}

. "$(dirname "$0")/_lib.sh"

CONTEXT="## Session context (auto-injected)
- **Branch**: $BRANCH
- **Last tag**: $LAST_TAG
- **Uncommitted files**: $UNCOMMITTED  |  **Untracked**: $UNTRACKED
- **Active modules**: ${MODULES:-(single-module: root pom)}
- **Issue**: ${ISSUE_REF:-none detected}

### Recent commits
\`\`\`
$RECENT
\`\`\`
"

if [ -n "$_PY" ]; then
  TITLE="$BRANCH" CTX="$CONTEXT" "$_PY" -c 'import os,json;print(json.dumps({"sessionTitle":os.environ["TITLE"],"additionalContext":os.environ["CTX"]}))'
else
  emit_context "$CONTEXT"
fi

exit 0
