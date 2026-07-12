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

1. Overall architecture
2. Package/module organization
3. SOLID principles
4. Design patterns (correct usage, misuse, and missed opportunities)
5. Clean Code principles
6. Object-oriented design
7. Separation of concerns
8. Spring Boot best practices
9. Dependency Injection
10. Configuration management
11. Exception handling
12. Validation
13. Security
14. Authentication & authorization
15. API design (REST conventions)
16. DTO and entity separation
17. JPA/Hibernate usage
18. Transaction management
19. Database design
20. Performance and efficiency
21. Caching opportunities
22. Concurrency and thread safety
23. Logging and observability
24. Testing (unit, integration, end-to-end)
25. Error handling
26. Code readability
27. Maintainability
28. Scalability
29. Documentation
30. Production readiness

For every category:

- Assign a score from 1–10.
- Explain why the score was given.
- Point to concrete examples in the code.
- Explain the risks of the current implementation.
- Suggest improvements following modern Java 21 and Spring Boot 3.x best practices.
- Include code examples where appropriate.

Additionally, identify:

- Code smells
- Anti-patterns
- Over-engineering
- Under-engineering
- Violations of SOLID
- Violations of DRY
- Violations of KISS
- Violations of YAGNI
- Hidden bugs
- Potential race conditions
- Resource leaks
- Security vulnerabilities
- Performance bottlenecks
- N+1 query problems
- Transactional issues
- Poor naming
- Dead code
- Duplicate logic
- Tight coupling
- Low cohesion
- Circular dependencies
- Large or "God" classes
- Methods that should be refactored

Evaluate whether the project follows:

- Hexagonal Architecture
- Clean Architecture
- Domain-Driven Design (where appropriate)
- Layered Architecture
- CQRS (if applicable)
- Event-driven design (if applicable)

Do not recommend patterns unless they provide a clear benefit. Avoid over-engineering.

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