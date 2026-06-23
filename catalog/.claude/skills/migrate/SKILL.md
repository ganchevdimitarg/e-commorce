---
name: migrate
description: Plan, write, and validate a Flyway database migration. Triggers on /migrate.
allowed-tools: Read, Write, Bash, Grep, Glob
---

## Steps

1. **Determine next version number**
   ```bash
   ls <module>/src/main/resources/db/migration/V*.sql | sort -t V -k2 -n | tail -1
   ```
   Increment by 1. Check git for pending migrations from other branches.

2. **Check for version conflicts**
   ```bash
   git fetch origin && git diff origin/main --name-only | grep "db/migration/V"
   ```
   If conflict exists, take the next available version.

3. **Plan before writing**: state what SQL will be written, whether it is backward-compatible,
   and whether a data backfill is needed (always a separate subsequent migration).

4. **Write the file** — `V<n>__<snake_case_description>.sql` in `<module>/src/main/resources/db/migration/`
   - New table: include audit columns (`created_at`, `updated_at`, `deleted_at`)
   - New column: `ADD COLUMN IF NOT EXISTS ... NULL` or with DEFAULT (backward compat)
   - New index: always in a separate migration; use `CREATE INDEX CONCURRENTLY IF NOT EXISTS`
   - Guards: always `IF NOT EXISTS` / `IF EXISTS` on every DDL statement

5. **Validate**: `./mvnw flyway:validate`

6. **Migrate**: `./mvnw flyway:migrate` — confirm `Success` in `flyway:info`

7. **Full verify**: `./mvnw clean verify`
   Hibernate `validate` confirms schema matches entity mappings.

8. **Backfill** (if needed): write `V<n+1>__backfill_<desc>.sql` separately.
   Use batched updates (`WHERE id > $last LIMIT 1000`) to avoid lock contention.

## Never
- Edit an existing committed migration
- `DROP COLUMN` / `DROP TABLE` on first pass — deprecate first, remove later
- Rename a column directly — add new + migrate data + drop old in three migrations
- `CREATE INDEX` without `CONCURRENTLY` on large tables (table lock risk)
- Omit `IF NOT EXISTS` / `IF EXISTS` guards
- Omit audit columns on new tables
- `spring.jpa.hibernate.ddl-auto` other than `validate`

## Output
1. Migration file path and version
2. SQL written (summarised)
3. `flyway:validate` result
4. `flyway:migrate` result (Success / Failed + error if failed)
5. Whether a follow-up backfill migration is needed
