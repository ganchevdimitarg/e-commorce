# Project layout

Single-module Spring Boot application (`com.ganchevdimitarg.claudetemplate`).
Not a multi-module microservice repo — adapt module-specific rules accordingly.

```
/
├── CLAUDE.md                       ← master context (imports docs/context/* on demand)
├── MEMORY.md                       ← Claude's auto-maintained scratchpad
├── SETUP.md                        ← placement + setup guide
├── HELP.md                         ← Spring Boot generated help
├── pom.xml                         ← dependencies (single module)
├── mvnw / mvnw.cmd                 ← Maven wrapper
│
├── docs/
│   ├── decisions.md                ← running decision log
│   ├── adr/                        ← Architecture Decision Records
│   └── context/                    ← @import targets (loaded on demand)
│
├── src/
│   ├── main/java/com/ganchevdimitarg/claudetemplate/
│   │   └── ClaudeTemplateApplication.java
│   ├── main/resources/
│   │   ├── application.properties
│   │   └── db/migration/           ← Flyway migrations (V<n>__*.sql)
│   └── test/java/com/ganchevdimitarg/claudetemplate/
│       └── ClaudeTemplateApplicationTests.java
│
└── .claude/
    ├── settings.json               ← hook wiring (commit)
    ├── settings.local.json         ← personal overrides (gitignore)
    ├── mcp.json                    ← MCP server config
    ├── hooks.md                    ← hook documentation
    ├── CLAUDE.md                   ← project-scoped extra context
    ├── agents/                     ← code-writer, code-reviewer, git-agent, test-agent, scaffold-agent, debug-agent
    ├── skills/                     ← write, review, commit, test, migrate
    ├── context/                    ← kafka-setup, testcontainers-patterns
    └── hooks/                      ← lifecycle scripts (chmod +x after clone)
```

## Note on your current structure
- Agents live in `.claude/agents/` (plural) — the layout Claude Code expects. ✓
- `docs/sagas/` already holds a `_template.md`; add one flow doc per cross-service saga as modules appear.
- When you add microservice modules later, give each its own `CLAUDE.md`.
