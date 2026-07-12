Perform a comprehensive code review of this Java Spring Boot repository as if you were a Senior Staff Engineer conducting a production readiness review.

Be strict, critical, and objective. Do not assume the current implementation is correct. Challenge architectural decisions, identify technical debt, and point out opportunities for improvement. Prioritize long-term maintainability, scalability, reliability, and clean architecture over simply making the code work.

A real senior staff engineer triages under time pressure: they go deep on the categories that carry the most risk and move quickly through the rest. Apply that same judgement — see "Depth triage" below for how to allocate depth across categories.

## Scoring rubric

Use this rubric for every category score below. Do not invent your own scale:

- **1-3 (Critical/absent):** the practice is missing entirely, actively broken, or actively dangerous (e.g. no tests exist, secrets are hardcoded, SQL injection is possible).
- **4-6 (Present with significant gaps):** the practice exists but has material gaps that would cause real incidents or rework (e.g. some endpoints validated, others not; tests exist but miss the main failure paths).
- **7-8 (Solid, minor gaps):** the practice is followed correctly in the large majority of cases; remaining gaps are edge cases or polish, not risk to production.
- **9-10 (Exemplary):** you would not change this if you owned the codebase; it reflects current best practice and you can find no meaningful gap.

## Determine the actual tech stack before scoring

Before scoring anything, read the build file(s) (`pom.xml`, `build.gradle`, or equivalent) to find the actual Java version and Spring Boot version this repository targets. Use that version — not an assumed one — as the baseline for every "suggest improvements" instruction below. If the repository is a multi-module monorepo, note any modules whose declared version differs from the majority and flag that drift explicitly; do not silently average it away.

Evaluate and score the project (1–10) in each of the following areas:

1. Overall architecture & architectural-style adherence — including whether the project follows Hexagonal Architecture, Clean Architecture, Domain-Driven Design (where appropriate), Layered Architecture, CQRS (if applicable), and event-driven design (if applicable). Do not recommend a pattern unless it provides a clear, stated benefit for this codebase — avoid over-engineering.
2. Package/module organization
3. SOLID principles
4. Design patterns (correct usage, misuse, and missed opportunities)
5. Code quality & maintainability (naming, function/class size, readability, long-term cost of change — treat these as one concern, not three)
6. Object-oriented design
7. Separation of concerns
8. Spring Boot best practices
9. Dependency Injection
10. Configuration management
11. Exception & error handling
12. Validation
13. Security
14. Authentication & authorization
15. API design (REST conventions)
16. DTO and entity separation
17. JPA/Hibernate usage
18. Transaction management
19. Database design
20. Performance & efficiency (including N+1 queries and other performance bottlenecks)
21. Caching opportunities
22. Concurrency & thread safety (including race conditions and resource leaks)
23. Logging and observability
24. Testing (unit, integration, end-to-end)
25. Scalability
26. Documentation

## Depth triage

Before writing anything, rank all 26 categories by risk to production. Give the full template below to your 10 highest-risk categories. For the remaining categories, give only the score and a one-sentence rationale — skip the extended fields. State which categories you triaged as low-depth and why, in one line, before the category write-ups.

## Per-category template

Write every category — full-depth or triaged — as:

### <N>. <Category name> — Score: X/10

**Rationale:** why this score, against the rubric above.

**Evidence:** cite every example as `path/to/File.java:123` (or a line range `path/to/File.java:120-135`). Do not describe an example you have not located at a specific path and line. If you cannot locate a concrete example for a claim, say so explicitly instead of writing a general statement as if it were evidence.

**Red flags:** note any that apply to this category specifically — code smell, anti-pattern, over-engineering, under-engineering, DRY/KISS/YAGNI violation, hidden bug, dead code, duplicate logic, tight coupling, low cohesion, circular dependency, God class/method, poor naming, transactional issue. Skip this field if none apply — do not force one.

**Risk:** what breaks, and under what conditions, if this is not addressed.

**Improvement:** a concrete suggestion following the actual tech-stack version identified above. Include a code example only for full-depth categories.

### Worked example (for calibration — do not repeat this example, it is illustrative only)

### 11. Exception & error handling — Score: 4/10

**Rationale:** exceptions are caught close to where they occur, but several handlers swallow the original cause, which will make production incidents undiagnosable.

**Evidence:** `src/main/java/com/example/order/OrderService.java:88-91` catches `SQLException` and rethrows `new RuntimeException("failed")` without wrapping the original exception as the cause.

**Red flags:** hidden bug — the original stack trace is discarded, so any monitoring tool downstream of this handler will show "failed" with no way to trace it back to the SQL error that caused it.

**Risk:** the first production incident that hits this path will be undiagnosable from logs alone; on-call will need to reproduce it manually.

**Improvement:** wrap the cause — `throw new RuntimeException("Order lookup failed for id=" + orderId, e);` — and prefer a typed domain exception over `RuntimeException` so callers can distinguish this failure from others.

At the end, provide:

## Overall Score
An overall score from 1–10.

## Production Readiness
Would you approve this project for production? Why or why not?

## Top 10 Critical Issues
Rank the most important issues by severity.

## Quick Wins
List improvements that can be completed in under one day.

## Long-Term Refactoring Plan
Recommend architectural improvements in priority order.

## Strengths
Highlight parts of the project that are particularly well designed.

Be candid and highly critical. Treat this review as if the code will support a high-traffic production system with millions of users. Focus on practical, evidence-based recommendations rather than theoretical perfection.