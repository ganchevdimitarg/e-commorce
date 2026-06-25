## Language & style
- British English for prose and comments
- Concise commit messages — no fluff words ("Refactor", "Update", "Fix" are enough context with Conventional Commits type)
- Never use // TODO without a ticket reference

## Personal tooling
- Editor: IntelliJ IDEA — generate run configs as `.run/*.xml` when creating new services
- Terminal: iTerm2 — use ANSI colour codes in scripts
- Prefer `./mvnw` over `mvn` — always use wrapper

## Output preferences
- Code examples: always include imports
- Explanations: start with the "why", then the "how"
- When multiple approaches exist: show the preferred one first, mention alternatives briefly

## Ambiguity handling

Three-tier policy for all agents and skills:

| Tier | When to apply | Action |
|---|---|---|
| **Assume and state** | Implementation details: package name, variable name, field type, default value, method visibility | Pick the CLAUDE.md-compliant default; state it in one sentence at the top of the response; proceed immediately |
| **Ask first** | Scope decisions: new table vs JSONB, new service vs existing, sync REST vs async Kafka, endpoint shape/HTTP method, breaking vs non-breaking change | Stop. Ask **one** question. Write no code until answered. |
| **Flag and continue** | Minor convention gaps where CLAUDE.md has a clear default but the deviation is worth noting | Note inline as `[convention: using X because Y]`; apply the default; continue |

## Session behaviour
- Always read MEMORY.md at session start if it exists in the project root
- Always read docs/decisions.md before proposing architectural changes
- Confirm the subject line of commit messages with me before committing
- Use /effort high for architecture, debugging, and migration planning; /effort low for simple edits
