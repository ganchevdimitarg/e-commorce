# Claude Code Hooks

Lifecycle hooks that run automatically during every Claude Code session.
All hooks are wired in `.claude/settings.json`. Scripts live in `.claude/hooks/`.

Hooks use the following exit code contract:
- `exit 0` — allow; Claude continues normally
- `exit 2` — block; stderr is shown to Claude as the reason and it must correct course
- `stdout JSON` with `additionalContext` — inject feedback into Claude's context without blocking

---

## Shared library: `_lib.sh`

Every hook sources `.claude/hooks/_lib.sh` instead of hand-rolling JSON parsing.
This removes the hard dependency on `python3` (Windows + Git Bash ships only `python`):

- `json_field <name>` / `json_field_multiline <name>` — extract a tool param.
  Claude Code nests params under `.tool_input`, so these read there first and fall
  back to a top-level key (python-first for correctness, `sed` fallback otherwise).
- `guard_require <name>` — **fail-closed** gate for guard hooks. **Call it in the
  hook's main shell, never inside `$(...)`** — an `exit` from a command
  substitution only kills the subshell. If the key is present in the raw input but
  extraction yields empty (parse failure or empty value), it blocks (`exit 2`).
- `guard_block <message>` — print to stderr and `exit 2`.
- `emit_context <message>` — print `{"additionalContext": "..."}` to stdout (no block).
- `resolve_module <file>` — returns `.` for the single-module root pom, a nested
  module dir for a monorepo, or empty when no pom exists. Maven hooks omit `-pl`
  when the module is `.`.

### Fail-closed vs fail-open
- **Guard hooks** (`block-dangerous`, `block-main-commit`, `secret-scan`,
  `protect-secrets`, `protect-migrations`, `warn-generated-files`): block on
  unparseable input — a missing interpreter must never silently disable a guard.
- **Advisory hooks** (Checkstyle, Flyway, Avro, N+1, API-contract, audit-log):
  no-op (`exit 0`) when they cannot run.

## Testing hooks

`.claude/hooks/test/run-tests.sh` pipes JSON fixtures into each hook and asserts
exit codes (and `additionalContext` presence). Run it after changing any hook;
add a fixture for every new rule:

```bash
.claude/hooks/test/run-tests.sh   # PASS=<n> FAIL=0, exit 0
```

> Single-module repos run Maven without `-pl`; `resolve_module` handles the
> monorepo case automatically. New hooks must source `_lib.sh` and add a fixture.

---

## Hook lifecycle

```
SessionStart
    └─ inject-git-context.sh       ← orient Claude with branch/commit/module state

PreToolUse  (before every tool call)
    ├─ secret-scan.sh              ← Bash(git commit): scan staged content for secrets [guard]
    ├─ block-dangerous.sh          ← Bash: block rm -rf, force-push, DROP/TRUNCATE, DELETE w/o WHERE [guard]
    ├─ block-main-commit.sh        ← Bash: block git commit on main/develop [guard]
    ├─ protect-secrets.sh          ← Read|Write|Edit|Bash: block access to secrets files [guard]
    ├─ protect-migrations.sh       ← Write|Edit: block editing committed Flyway migrations [guard]
    ├─ warn-generated-files.sh     ← Write|Edit: block overwriting generated files (target/, @Generated) [guard]
    ├─ dependency-check.sh         ← Write|Edit pom.xml: block inline <version> outside the root BOM
    └─ audit-log.sh (async)        ← Bash: append every command to .claude/audit.log

PostToolUse (after every tool call)              [all advisory — fail open]
    ├─ checkstyle-on-save.sh       ← Write|Edit .java: run Checkstyle; feed violations back
    ├─ flyway-validate.sh          ← Write .sql: run flyway:validate; feed errors back
    ├─ avro-validate.sh            ← Write|Edit .avsc: validate schema JSON + defaults; regenerate
    ├─ n-plus-one-check.sh         ← Write|Edit .java: warn on JPA N+1 patterns
    └─ api-contract-check.sh       ← Write|Edit .java: warn on non /api/v{n}/ endpoints

Stop        (before Claude finishes a turn)
    ├─ verify-gate.sh              ← ./mvnw test (compile+unit), or verify when an IT/migration changed; exit 2 forces a fix if red
    └─ session-checkpoint.sh       ← write .claude/session-checkpoint.md + sync MEMORY.md

PreCompact  (before context is summarised)
    └─ session-checkpoint.sh       ← persist checkpoint before compaction discards detail
```

All hooks source `_lib.sh`; `[guard]` hooks fail closed, advisory hooks fail open.

---

## Hooks reference

### `inject-git-context.sh` — SessionStart

**Purpose:** Injects current repo state at the start of every session. Claude arrives oriented
without burning tool calls to discover branch, module layout, or recent commits.

**Output injected:**
- Current branch name (also sets `sessionTitle`)
- Last 8 commits
- Uncommitted / untracked file counts
- Active Maven modules
- Issue number extracted from branch name (e.g. `feat/<service-name>-142-retry` → `Refs #142`)

**Config:**
```json
{ "matcher": "startup", "hooks": [{ "type": "command", "command": "...inject-git-context.sh" }] }
```

---

### `block-dangerous.sh` — PreToolUse · Bash

**Purpose:** Blocks destructive shell commands before Claude executes them. Enforces multiple
items from the project Never list deterministically rather than relying on instruction alone.

**Blocked patterns:**
| Pattern | Reason |
|---|---|
| `rm -rf` | Irreversible bulk deletion |
| `git push --force` to main/develop | Rewrites shared history |
| `git add -A` | Stages secrets and unintended files |
| `DROP TABLE` / `DROP DATABASE` / `TRUNCATE` | Schema destruction outside Flyway |
| `DELETE FROM <table>;` without WHERE | Unguarded full-table wipe |
| `docker rm/stop/kill $(docker ps ...)` | Bulk container teardown |

**Exit:** `2` on match; error written to stderr, shown to Claude as block reason.

---

### `block-main-commit.sh` — PreToolUse · Bash

**Purpose:** Blocks `git commit` when the current branch is `main`, `develop`, or `master`.
Claude must create a feature branch first.

**Message to Claude:**
```
Blocked: direct commits to 'main' are not permitted.
Create a feature branch first:
  git checkout -b <type>/<scope>-<short-desc>
```

---

### `protect-secrets.sh` — PreToolUse · Read|Write|Edit|Bash

**Purpose:** Blocks tool access to secrets files. For `Read`/`Write`/`Edit` it matches the
`file_path` directly. For `Bash` it splits the command into path-like **tokens** and matches
each one — it never greps the raw command line, so prose in a commit message or heredoc body
(e.g. a `secrets.yml` mentioned in a message) does not trip the guard. `git commit`/`tag`/
`merge`/`stash` are skipped entirely (their message text is prose); secret *content* staged in
a commit is caught by `secret-scan.sh` instead.

**Blocked patterns:** `*.env`, `*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.der`,
`secrets.<data-ext>` (`yml`/`json`/`properties`/`env`/`conf`/`cfg`/`toml`/`txt`/`enc`/…),
`credentials` (file), `id_rsa`, `id_ed25519`, `id_ecdsa`,
`application-prod.yml`, `application-production.yml`.
The `secrets.` rule requires a real secret-data extension, so guard scripts such as
`protect-secrets.sh` are **not** matched.

**Exit:** `2` on match with explanation and redirect to environment variables / secrets manager.

---

### `protect-migrations.sh` — PreToolUse · Write|Edit

**Purpose:** Blocks editing a Flyway migration file (`db/migration/V*.sql`) that already
exists in git history. New untracked migration files are always allowed.

**Detection:** Uses `git ls-files --error-unmatch` — if the file is tracked, it is committed
and therefore immutable.

**Message to Claude:**
```
Blocked: editing a committed Flyway migration is not permitted.
Committed migrations are immutable — create V<n+1>__<description>.sql instead.
```

---

### `audit-log.sh` — PreToolUse · Bash (async)

**Purpose:** Appends every shell command Claude runs to `.claude/audit.log` for traceability.
Runs async — zero latency impact on the agent loop.

**Log format:** `timestamp | session_id | branch | command`

```
2025-06-14T10:23:01Z  sess_abc123  feat/retry-v2  ./mvnw clean verify
2025-06-14T10:23:45Z  sess_abc123  feat/retry-v2  git diff --staged
```

**Note:** `.claude/audit.log` is gitignored by default. Add these lines to your `.gitignore`:

```gitignore
# Claude Code — local only; never commit
.claude/audit.log
.claude/settings.local.json
```

You can apply this in one command:
```bash
printf '\n# Claude Code — local only\n.claude/audit.log\n.claude/settings.local.json\n' >> .gitignore
```

---

### `checkstyle-on-save.sh` — PostToolUse · Write|Edit

**Purpose:** Runs Checkstyle immediately after Claude writes or edits a `.java` file.
Violations are injected as `additionalContext` so Claude fixes them in the same turn,
not at commit time.

**Module detection:** Extracts top-level directory from `file_path` and checks for `pom.xml`.
Skips if module cannot be determined.

**Feedback to Claude:**
```
Checkstyle violations found after editing src/main/java/.../OrderService.java:
[WARN] Line 42: 'if' construct must use '{}'s. [NeedBraces]
Fix these before proceeding.
```

---

### `flyway-validate.sh` — PostToolUse · Write

**Purpose:** Runs `flyway:validate` immediately after Claude writes a new `.sql` file under
`db/migration/`. Catches naming errors, duplicate version numbers, and checksum drift
before they reach CI.

**Feedback to Claude:**
```
Flyway validation failed after writing src/main/resources/db/migration/V5__add_index.sql:
Validate failed: Migration checksum mismatch for migration version 5
Check migration version, filename format (V<n>__<desc>.sql), and checksum.
```

---

### `avro-validate.sh` — PostToolUse · Write|Edit

**Purpose:** Validates Avro schema files (`.avsc`) after Claude creates or edits them.

**Checks performed:**
1. Valid JSON syntax
2. `type` is `"record"`
3. `name` is present
4. `namespace` is present (required for Schema Registry subject naming)
5. All fields have a `"default"` value (backward compatibility requirement)
6. `./mvnw generate-sources` succeeds (Java class generation; `resolve_module` adds `-pl` only in a monorepo)

**Feedback example:**
```
Avro schema issues in common-events/src/main/avro/order/PaymentCompletedEvent.avsc:
- fields missing 'default' (breaks BACKWARD compatibility): amount, currency
  Add a default value to every field.
```

---

### `warn-generated-files.sh` — PreToolUse · Write|Edit

**Purpose:** Blocks Claude from overwriting generated files before the write happens.
Moved to PreToolUse so the damage is prevented, not warned about after the fact.

**Detection:**
- Path contains `target/generated-sources` or `target/generated-test-sources`
- File contains `@Generated`, `@javax.annotation.Generated`, or `@jakarta.annotation.Generated`

**Redirect guidance:**
| Generated file | Edit this instead |
|---|---|
| Avro Java classes | `.avsc` in `common-events/src/main/avro/` |
| Lombok-generated methods | Annotation on the source class |
| MapStruct mappers | Mapper interface |

---

### `verify-gate.sh` — Stop

**Purpose:** The most powerful hook. Runs `./mvnw test` (compile + unit tests) on all
modules touched in the current turn before Claude is allowed to stop. If the build is red,
exit 2 forces Claude to continue and fix rather than stopping with a broken repo.

**Adaptive by design:** Stop fires often, so the gate defaults to the fast Surefire/unit
phase (`./mvnw test`). It escalates to the full `./mvnw verify` (adding the slow
Failsafe/Testcontainers integration phase) **only when the turn changed an integration
test (`*IT.java` / `*IntegrationTest.java`) or a `db/migration/*.sql`** — so regressions in
those surface on the turn that caused them, not later. Checkstyle and the exhaustive
`clean verify` still run in `/commit`. Fast feedback every turn; full verification before a commit.

**Trigger condition:** Only fires if `.java` or `.sql` files appear in `git diff HEAD`.
Silent no-op for turns that only read files or run tests.

**Module detection:** `resolve_module` per changed file (`.` = single-module root → no `-pl`).

**Feedback to Claude:**
```
Build is RED in: <module>. Fix all failures before stopping.
Run './mvnw verify' and address root causes —
do not suppress errors.
```

---

## `settings.json` structure

```
.claude/settings.json          ← committed; shared by the whole team
.claude/settings.local.json    ← gitignored; personal overrides only
```

Team hooks (all P1 + P2) go in `settings.json`.
Personal preferences (e.g. custom notification, extra audit destinations) go in `settings.local.json`.

---

## Adding a new hook

1. Create the script in `.claude/hooks/<name>.sh`
2. `chmod +x .claude/hooks/<name>.sh`
3. Add the entry to `.claude/settings.json` under the correct lifecycle event
4. Test locally: `echo '{"command":"your test input"}' | .claude/hooks/<name>.sh`
5. Add documentation to this file
6. Commit both the script and the updated `settings.json`

**Exit code reference:**

| Code | Meaning |
|---|---|
| `0` | Allow — Claude continues |
| `2` | Block — stderr shown to Claude; Claude must correct course |
| `stdout JSON {"additionalContext":"..."}` | Inject feedback without blocking |
| `stdout JSON {"sessionTitle":"..."}` | Set session title (SessionStart only) |

**Async hooks** (`"async": true`): run in the background; exit code ignored; no latency impact.
Use for logging/observability only — never for blocking.
