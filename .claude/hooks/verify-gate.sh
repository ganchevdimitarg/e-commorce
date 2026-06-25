#!/usr/bin/env bash
# Stop hook — adaptive gate on modules with changed .java/.sql files:
#   • default          → ./mvnw test   (fast: compile + unit)
#   • IT/migration touched → ./mvnw verify (compile + unit + Failsafe integration)
# This keeps every turn fast while still catching integration/migration regressions
# on the turn that caused them. Full `clean verify` + checkstyle still runs at /commit.
# Build red → exit 2 forces Claude to fix rather than stop with a broken repo.
# Stop hooks have no file matcher; the RELEVANT check below is the sole guard.
. "$(dirname "$0")/_lib.sh"
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null)
[ -z "$REPO_ROOT" ] && exit 0

# Run git from the repo root so every command emits root-relative paths. `git diff` is
# already root-relative, but `git ls-files --others` prints paths relative to the CWD —
# from a subdir that drops the module prefix (e.g. "src/..." instead of "catalog/src/..."),
# which resolve_module then mis-maps to "." (the root reactor). -C $REPO_ROOT keeps them aligned.
CHANGED=$( {
  git -C "$REPO_ROOT" diff --name-only 2>/dev/null
  git -C "$REPO_ROOT" diff --cached --name-only 2>/dev/null
  git -C "$REPO_ROOT" ls-files --others --exclude-standard 2>/dev/null
} | sort -u )

RELEVANT=$(echo "$CHANGED" | grep -E '\.(java|sql)$')
[ -z "$RELEVANT" ] && exit 0

# Phase selection: integration tests (Failsafe) and Flyway migrations are only validated
# by `verify`. If the turn touched an *IT.java / *IntegrationTest.java file or a
# db/migration/*.sql, run the full `verify`; otherwise fast `test` (compile + unit).
if echo "$RELEVANT" | grep -qE '(IT|IntegrationTest)\.java$|db/migration/.*\.sql$'; then
  GOAL="verify"; SCOPE="compile + unit + integration"
else
  GOAL="test";   SCOPE="compile + unit"
fi

# Resolve unique owning modules ( "." = root single-module ).
MODULES=$(for f in $RELEVANT; do resolve_module "$f"; echo; done | sort -u | grep -v '^$')

FAILED=()
for MODULE in $MODULES; do
  # Build the changed module via its OWN pom (-f), not the root reactor (-pl -am).
  # This monorepo's gateway/profile POMs are pre-broken (missing dependency
  # versions, un-migrated to Boot 4 / Spring Cloud 2025); a root reactor build
  # fails at model-construction reading those siblings BEFORE the -pl subset is
  # selected, even for modules that don't depend on them. `-f <module>/pom.xml`
  # builds the module standalone (deps resolved from ~/.m2), the documented way
  # to build catalog in this repo. "." = root single-module.
  if [ "$MODULE" = "." ]; then PL=(); else PL=(-f "$MODULE/pom.xml"); fi
  OUTPUT=$(cd "$REPO_ROOT" && ./mvnw "$GOAL" "${PL[@]}" -q 2>&1)
  if [ $? -ne 0 ]; then
    # Fallback: clean run to rule out stale class files.
    OUTPUT=$(cd "$REPO_ROOT" && ./mvnw clean "$GOAL" "${PL[@]}" -q 2>&1)
  fi
  if [ $? -ne 0 ]; then
    FAILED+=("$MODULE")
    echo "BUILD FAILURE in module: $MODULE" >&2
    echo "$OUTPUT" | grep -E 'ERROR|FAILED|Tests run:.*Failures|BUILD' | head -15 >&2
  fi
done

if [ ${#FAILED[@]} -gt 0 ]; then
  emit_context "Build is RED in: ${FAILED[*]} ($SCOPE). Fix all failures before stopping. Run './mvnw $GOAL' and address root causes — do not suppress errors or skip tests."
  exit 2
fi
exit 0
