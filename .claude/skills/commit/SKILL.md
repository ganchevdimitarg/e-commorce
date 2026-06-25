---
name: commit
description: Verify build is green, stage explicit paths, commit with Conventional Commits, open PR draft. Triggers on /commit.
allowed-tools: Bash(git *), Bash(./mvnw *), Bash(gh *)
---

## Steps

0. Check current branch: `git branch --show-current`
   - If on `main` or `develop`: create a feature branch first:
     `git checkout -b <type>/<scope>-<short-desc>`
     e.g. `git checkout -b feat/<service-name>-idempotent-retry`
   - Never commit directly to `main` or `develop`.

1. Run: `./mvnw clean verify` (single-module repo; for a future monorepo add `-pl <module> -am`)
   If fails: stop, report failures. Do not proceed.
2. Run: `./mvnw checkstyle:check`
   If fails: stop, report. Do not proceed.
3. Run: `./mvnw flyway:validate`
   If fails: stop, report. Do not proceed.
4. Review changed files: `git diff --name-only`
   Stage explicit paths only — never `git add -A`.
   Example: `git add src/main/java/... src/test/java/... src/main/resources/db/migration/...`
5. Write commit message (Conventional Commits, max 72 chars subject):
   ```
   type(scope): short description

   Body: what changed and why (not how).
   Refs: #<issue> if applicable.
   ```
   Types: feat | fix | docs | refactor | test | chore | migration
   Scope: module name e.g. <service-name>, common-events

   Multi-module changes: prefer one commit per module in dependency order
   (e.g. common-events first, then <service-name>).
   If a single atomic change must span modules, use combined scope:
   `feat(common-events,<service-name>): add PaymentCompletedEvent and consumer`

   Breaking changes: add `BREAKING CHANGE: <description>` footer after the body.
   Describes what callers must change; signals a major version bump.
   ```
   feat(<service-name>): remove deprecated v1 order endpoint

   Clients must migrate to /api/v2/orders before this release.

   BREAKING CHANGE: DELETE /api/v1/orders removed; use /api/v2/orders
   ```
6. Write the full commit message to a temp file, then commit from it:
   ```bash
   cat > /tmp/commit_msg.txt << 'MSG'
   <subject>

   <body>

   BREAKING CHANGE: <description>   # include only if applicable
   Refs: #<issue>                   # include only if applicable
   MSG
   git commit --file /tmp/commit_msg.txt
   rm /tmp/commit_msg.txt
   ```
   This handles multi-paragraph bodies and BREAKING CHANGE footers reliably.
7. `git push origin HEAD`
8. `gh pr create --draft --title "<subject>" --body "## Summary\n<body>\n\n## Checklist\n- [ ] Tests green\n- [ ] Checkstyle clean\n- [ ] Flyway validated\n- [ ] Avro schema registered (if Kafka event added/changed)\n- [ ] Contract tests passed (if API changed)\n- [ ] BREAKING CHANGE footer present (if breaking change)\n- [ ] Audit columns in new Flyway migrations"`

## Commit message examples
```
feat(<service-name>): add idempotent payment retry via Redis lock

Retries use correlationId stored in Redis to prevent duplicate charges.
Refs: #142

fix(auth-service): return 401 instead of 500 on expired JWT

migration(<service-name>): V5 add idx_products_category_id

test(<service-name>): add integration test for concurrent retry scenario
```

## Never
- `git add -A` — stage explicit file paths only
- Commit if any verify/checkstyle/flyway:validate step failed
- Force-push to main or develop