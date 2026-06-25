---
name: review
description: Review staged/recent changes against project conventions, or audit and score existing code against best practices, design principles, and patterns. Triggers on /review.
allowed-tools: Bash(git diff *), Bash(git log *), Read, Grep
---

## Modes

The skill runs in one of two modes depending on what is being reviewed:

| Mode | When | Output |
|---|---|---|
| **Diff review** (default) | `/review` on staged/recent changes, a commit, or a PR diff | Severity-grouped findings + verdict |
| **Audit** | `/review` pointed at existing code — a file, class, package, or module (no diff implied) | Severity-grouped findings **+ a scorecard** (see below) |

Pick Audit mode when the target is existing code rather than a change set
(e.g. "review OrderService", "audit the order package", "score this class").
Apply the **full** checklist to the entire content, not just changed lines.

## Steps

1. **Diff review**: run `git diff --staged` (pre-commit) or `git diff HEAD~1` (post-commit).
   **Audit**: `Read` the target file(s) in full; `Grep` for collaborators when judging design.
2. Apply every checklist category below — including **Design & Principles**.
3. Output grouped by severity: Critical → Warning → Suggestion.
4. In Audit mode, also emit the **Scorecard**.
5. Never approve (diff review) if any Critical item is present.

## Severity key
- **Critical**: security risk, data loss, broken build, schema corruption — block merge
- **Warning**: convention violation, maintainability issue, missing safety net — must fix before merge
- **Suggestion**: style, improvement, better pattern available — fix encouraged, not blocking

---

## Checklist

### Secrets & Safety [Critical]
- No secrets, tokens, API keys, or passwords in diff
- No `git add -A` in scripts — must stage explicit paths

### Lombok [Warning]
- No `@Data` on JPA entities or domain objects with business logic
- No `@ToString` or `@EqualsAndHashCode` on JPA entities with associations
- `@Slf4j` used for logging — no manual `Logger` declaration
- `@RequiredArgsConstructor` on Spring components — no `@Autowired`

### Java [Warning unless noted]
- Immutable DTOs, commands, responses, and event payloads use records — flag any `@Value`/`@Data` class that should be a record [Suggestion: propose the equivalent record]
- No `Optional.get()` without guard — must use `orElseThrow()` [Critical]
- No `@SuppressWarnings` hiding real errors [Warning]
- No `ThreadLocal` — use `ScopedValue` on virtual threads [Warning]
- No string concatenation in log statements — use `@Slf4j` parameterised logging [Suggestion]
- No `instanceof` chains — use sealed + exhaustive switch [Suggestion]
- `String.format()` or text blocks for multiline strings — no string templates (JEP 430 withdrawn) [Warning]

### Design & Principles [Warning unless noted]
Judged against the code as a whole, not single lines. Cite the offending type/method.
- **SRP**: each class/method has one reason to change — flag god classes, mixed concerns (e.g. controller doing persistence, service formatting JSON) [Warning]
- **OCP/DIP**: depend on abstractions, not concretions; new behaviour added by extension not by editing switch/if ladders [Suggestion]
- **LSP/ISP**: subtypes honour their supertype contract; interfaces are narrow and client-specific — no fat interfaces forcing empty implementations [Suggestion]
- **DRY**: no copy-pasted logic that should be extracted; but flag premature abstraction too [Suggestion]
- **KISS / YAGNI**: no speculative generality, unused extension points, or indirection without a caller [Suggestion]
- **Cohesion & coupling**: related data+behaviour live together; no feature envy or chains of `a.getB().getC().getD()` (Law of Demeter) [Warning]
- **Layering**: domain → service → controller dependency direction respected; no controller imported into domain, no JPA entity leaked across the API boundary [Warning]
- **Design patterns**: applied where they earn their keep, not cargo-culted — flag a missing pattern that would remove duplication/branching (Strategy, Factory, Template Method) *and* an over-engineered one (Singleton-as-global, anaemic abstract base, needless Visitor) [Suggestion]
- **Encapsulation**: no leaking mutable internal state (returning the live collection, exposing setters that break invariants); prefer immutability (records/`List.copyOf`) [Warning]
- **Naming & intent**: names reveal intent; no `data`/`info`/`manager`/`util` dumping grounds; booleans read as predicates [Suggestion]
- **Error handling**: failures modelled via the `BusinessException` hierarchy / sealed results — no swallowed exceptions, no control flow by exception [Warning]
- **Method size & nesting**: flag methods that are long or nested >3 deep; suggest guard clauses / extraction [Suggestion]

### Spring [Warning unless noted]
- `@Transactional` not on controllers or repository interfaces [Warning]
- `SecurityFilterChain` bean declared explicitly — no reliance on auto-config defaults [Warning]
- `@PreAuthorize` on service layer, not controller [Warning]
- WebFlux / reactive types (`Mono`, `Flux`) only in `api-gateway` or explicitly approved streaming endpoints — flag any other usage [Warning]

### Observability [Warning unless noted]
- Every new service or HTTP handler has an `OncePerRequestFilter` (WebMVC) setting `traceId`, `spanId`, `userId`, `serviceId` in MDC — and clears on exit [Warning]
- All outbound HTTP calls propagate `traceparent` header [Warning]
- Kafka producers set `traceId` and `correlationId` as message headers [Warning]
- Kafka consumers read MDC from message headers before processing, clear after [Warning]
- New significant actions have a `MeterRegistry` counter — name: `<service>.<entity>.<action>` [Suggestion]
- No plain-text log format — structured JSON (logstash-logback-encoder) only [Warning]
- `/actuator/health` exposed; details restricted to internal only [Suggestion]

### Flyway [Critical unless noted]
- Migration file named correctly: `V<n>__<description>.sql` — two underscores [Warning]
- No edits to existing committed migration files [Critical]
- `spring.jpa.hibernate.ddl-auto` is `validate` — not `create`/`update`/`create-drop` [Critical]
- `spring.flyway.enabled` is not `false` in any profile [Critical]
- Each migration is one logical change; index separate from table creation [Suggestion]
- New `CREATE TABLE` migration includes `created_at`, `updated_at`, `deleted_at` audit columns [Warning]

### Redis [Warning]
- No Java serialization — Jackson JSON only
- All keys follow `<service>:<entity>:<id>` pattern
- All keys have TTL set — no immortal keys [Critical]

### Kafka [Warning unless noted]
- Topic follows `<domain>.<entity>.<event>` pattern
- Consumer group follows `<service>-group` pattern
- `@RetryableTopic` configured with DLT [Critical: missing retry = silent message loss]
- `traceId` and `correlationId` set as message headers
- Idempotency guard via Redis `correlationId` check before processing

### Validation [Warning unless noted]
- `@Valid` present on all `@RequestBody` and `@PathVariable` controller params [Warning]
- Bean Validation constraints on record components, not service method params [Warning]
- Cross-field rules in record compact constructor, not service layer [Suggestion]
- No `ValidationException` thrown from service layer for inputs that could be a Bean Validation constraint [Suggestion]

### Pagination [Warning]
- Controller uses `@PageableDefault` — no hardcoded page/size params
- `Page<Entity>` never returned directly — must map to `Page<RecordType>` then `PageResponse.of()`
- Max page size enforced (`@Max(100)` or equivalent)

### Jackson [Warning]
- No `ObjectMapper` instantiated manually — use injected bean or `JacksonConfig` customizer
- No null fields in response records — Jackson configured `NON_NULL` globally
- No epoch-long date fields — ISO-8601 strings only

### Records [Suggestion]
- Any `@Value` class with only final fields and no framework constraint → convert to record
- When suggesting conversion, output the equivalent record inline

### Testing [Warning unless noted]
- Test names follow `should_<expectedBehavior>_when_<condition>`
- Integration tests extend `AbstractIntegrationTest` — not H2 [Critical]
- No `Thread.sleep()` — use Awaitility [Warning]
- `spring.flyway.enabled` not disabled in test profiles [Critical]

### Docker [Warning unless noted]
- `COPY` uses explicit artifact name — not `*.jar` glob [Warning]
- Image not tagged `:latest` in K8s manifests [Critical]
- Non-root `USER` declared [Warning]
- `HEALTHCHECK` present [Warning]

### Dependencies [Warning]
- New dependencies have version declared in root `pom.xml` BOM

---

## Scorecard (Audit mode only)

After the severity findings, score the code on six weighted dimensions. Each dimension is
scored `0–10`; the overall is the weighted sum, expressed `/100`. Always justify each score
with one line that names the concrete evidence (a finding above, a type, a line).

| # | Dimension | Weight | What it measures |
|---|---|---|---|
| 1 | Correctness & Safety | 25 | Logic soundness, null/Optional handling, concurrency, no Critical findings |
| 2 | Convention compliance | 20 | Adherence to the CLAUDE.md checklist categories above |
| 3 | Design & SOLID | 20 | SRP/OCP/DIP, cohesion, coupling, layering, encapsulation |
| 4 | Design patterns | 10 | Patterns applied where warranted; none cargo-culted or missing |
| 5 | Readability & maintainability | 15 | Naming, method size, nesting, clarity of intent |
| 6 | Testability & tests | 10 | Seams/injectability, existing test quality, `should_…_when_…` naming |

**Scoring rules**
- A single **Critical** finding caps dimension 1 at **3/10** and the overall grade at **C** — unsafe code is never "good code".
- Score against the *target* conventions only where the relevant module actually exists
  (see CLAUDE.md repo-maturity note); do not penalise absent aspirational infrastructure.
- Be specific and honest — a 10 means "exemplary, nothing to add", not "no blockers".

**Grade bands**: A ≥ 90 · B 75–89 · C 60–74 · D 40–59 · F < 40

### Scorecard output format
```
## Scorecard — <target>

| Dimension | Score | Justification |
|---|---|---|
| Correctness & Safety        | 8/10  | <one line, cite evidence> |
| Convention compliance       | 7/10  | … |
| Design & SOLID              | 6/10  | … |
| Design patterns             | 8/10  | … |
| Readability & maintainability | 7/10 | … |
| Testability & tests         | 5/10  | … |

**Overall: 71/100 — Grade C**

**Top 3 improvements (highest score gain first)**
1. <action> — lifts <dimension>
2. …
3. …
```