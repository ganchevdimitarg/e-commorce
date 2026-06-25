#!/usr/bin/env bash
# Test harness for .claude/hooks. Pipes JSON fixtures into hooks and asserts exit codes.
# Fixtures use the real Claude Code hook payload shape: params nested under tool_input.
set -u
HOOKS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PASS=0; FAIL=0

# ti <inner-json> — wrap tool params in the real {"tool_input": {...}} envelope.
ti() { printf '{"tool_name":"T","tool_input":%s}' "$1"; }

# assert_exit <desc> <expected_code> <hook-file> <json-input>
assert_exit() {
  local desc="$1" exp="$2" hook="$3" json="$4" code
  printf '%s' "$json" | "$HOOKS_DIR/$hook" >/tmp/hook_out 2>/tmp/hook_err
  code=$?
  if [ "$code" = "$exp" ]; then
    PASS=$((PASS+1))
  else
    FAIL=$((FAIL+1))
    echo "FAIL: $desc — exit $code, expected $exp"
    echo "  stderr: $(head -c 200 /tmp/hook_err)"
  fi
}

# assert_lib <desc> <expected> <command...>  (sources _lib.sh, runs a snippet)
assert_lib() {
  local desc="$1" exp="$2"; shift 2
  local got
  got="$("$@")"
  if [ "$got" = "$exp" ]; then PASS=$((PASS+1)); else
    FAIL=$((FAIL+1)); echo "FAIL: $desc — got '$got', expected '$exp'"; fi
}

# --- _lib.sh json_field: nested tool_input + top-level fallback ---
lib_json() { INPUT="$1"; . "$HOOKS_DIR/_lib.sh"; json_field "$2"; }

assert_lib "json_field nested command"   "git status"             lib_json '{"tool_input":{"command":"git status"}}' command
assert_lib "json_field nested file_path" "src/Main.java"          lib_json '{"tool_input":{"file_path":"src/Main.java"}}' file_path
assert_lib "json_field windows path"     'D:\IdeaProjects\x.java' lib_json '{"tool_input":{"file_path":"D:\\IdeaProjects\\x.java"}}' file_path
assert_lib "json_field top-level fallback" "x"                    lib_json '{"command":"x"}' command
assert_lib "json_field absent -> empty"  ""                       lib_json '{"tool_input":{"command":"x"}}' file_path

# --- guard hooks: block dangerous commands (must exit 2) ---
assert_exit "block rm -rf"            2 block-dangerous.sh "$(ti '{"command":"rm -rf /tmp/x"}')"
assert_exit "block rm -fr"            2 block-dangerous.sh "$(ti '{"command":"rm -fr build"}')"
assert_exit "block git add -A"        2 block-dangerous.sh "$(ti '{"command":"git add -A"}')"
assert_exit "block DROP TABLE"        2 block-dangerous.sh "$(ti '{"command":"psql -c \"DROP TABLE widgets\""}')"
assert_exit "block DELETE no where"   2 block-dangerous.sh "$(ti '{"command":"psql -c \"DELETE FROM widgets;\""}')"
assert_exit "block DELETE quoted tbl" 2 block-dangerous.sh "$(ti '{"command":"psql -c \"DELETE FROM \\\"widgets\\\"\""}')"
assert_exit "allow safe ls"           0 block-dangerous.sh "$(ti '{"command":"ls -la"}')"
assert_exit "allow rm single file"    0 block-dangerous.sh "$(ti '{"command":"rm target/app.jar"}')"
assert_exit "allow DELETE with where" 0 block-dangerous.sh "$(ti '{"command":"psql -c \"DELETE FROM widgets WHERE id=1\""}')"

# --- guard hooks: FAIL CLOSED when key present but value empty/unparseable ---
assert_exit "fail-closed empty command"   2 block-dangerous.sh "$(ti '{"command":""}')"
assert_exit "fail-closed empty file_path" 2 protect-secrets.sh "$(ti '{"file_path":""}')"

# --- protect-secrets: paths + commands touching secrets ---
assert_exit "protect-secrets blocks dotenv"      2 protect-secrets.sh "$(ti '{"file_path":"app/.env"}')"
assert_exit "protect-secrets blocks pem"         2 protect-secrets.sh "$(ti '{"file_path":"certs/server.pem"}')"
assert_exit "protect-secrets blocks secrets.yml" 2 protect-secrets.sh "$(ti '{"file_path":"config/secrets.yml"}')"
assert_exit "protect-secrets allows java"        0 protect-secrets.sh "$(ti '{"file_path":"src/Main.java"}')"
# false-positive fixed: the guard scripts are not secret material
assert_exit "protect-secrets allows own hook"    0 protect-secrets.sh "$(ti '{"file_path":".claude/hooks/protect-secrets.sh"}')"
# Bash: real secret-file access is blocked by token, prose is not
assert_exit "protect-secrets blocks cat dotenv"  2 protect-secrets.sh "$(ti '{"command":"cat app/.env"}')"
assert_exit "protect-secrets allows normal cmd"  0 protect-secrets.sh "$(ti '{"command":"./mvnw -q test"}')"
assert_exit "protect-secrets allows commit prose" 0 protect-secrets.sh "$(ti '{"command":"git commit -m \"handle secrets.yml\""}')"

assert_exit "secret-scan ignores non-commit" 0 secret-scan.sh "$(ti '{"command":"ls"}')"

# --- path / advisory hooks ---
assert_exit "protect-migrations allows new (untracked)" 0 protect-migrations.sh "$(ti '{"file_path":"src/main/resources/db/migration/V99__new.sql"}')"
assert_exit "warn-generated blocks generated-sources"   2 warn-generated-files.sh "$(ti '{"file_path":"target/generated-sources/Foo.java"}')"
assert_exit "warn-generated allows normal java"         0 warn-generated-files.sh "$(ti '{"file_path":"src/main/java/Foo.java"}')"
assert_exit "audit-log always allows"                   0 audit-log.sh "$(ti '{"command":"ls"}')"

# --- maven / advisory hooks resolve module and never block ---
assert_exit "checkstyle non-java no-op"   0 checkstyle-on-save.sh "$(ti '{"file_path":"README.md"}')"
assert_exit "flyway non-sql no-op"        0 flyway-validate.sh "$(ti '{"file_path":"src/main/java/Foo.java"}')"
assert_exit "n+1 non-java no-op"          0 n-plus-one-check.sh "$(ti '{"file_path":"pom.xml"}')"
assert_exit "api-contract non-java no-op" 0 api-contract-check.sh "$(ti '{"file_path":"pom.xml"}')"
assert_exit "avro non-avsc no-op"         0 avro-validate.sh "$(ti '{"file_path":"Foo.java"}')"

# resolve_module: a single-module repo file resolves to "."
lib_mod() { INPUT='{}'; . "$HOOKS_DIR/_lib.sh"; resolve_module "$1"; }
assert_lib "resolve_module root file -> ." "." lib_mod "src/main/java/Foo.java"

echo "----"
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = 0 ]
