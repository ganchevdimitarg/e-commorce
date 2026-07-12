# evaluationAndScore.md Weakness-Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `evaluationAndScore.md` in place to fix all 12 weaknesses (W1–W12)
catalogued in `docs/prompt-engineering/2026-07-12-evaluationAndScore-prompt-evaluation.md`,
without regressing the dimensions that already scored well (role framing, task clarity).

**Architecture:** Sequential, order-dependent text edits to a single markdown file. Each
task edits a distinct, non-overlapping region so later `old_string` anchors remain valid
after earlier edits. There is no build system for this file — verification is grep-based
structural checks against the rewritten content, run after each task and comprehensively
in the final task.

**Tech Stack:** Markdown only. No Java/Spring Boot tooling applies to this file — it is a
prompt-engineering asset, not application code, so the repo's `./mvnw verify` /
checkstyle / Flyway gates in the root CLAUDE.md do not apply here.

## Global Constraints

- Fix exactly the weaknesses documented in the evaluation doc (W1–W12), plus two
  same-class duplicates surfaced during design (Exception handling/Error handling merge,
  extending W9's fix; an evidence escape-hatch, extending W6's fix) — both are called out
  explicitly in Task 2, not silently folded in.
- This pass is a **rewrite of the existing single file**, not a new versioned file — the
  asset stays canonical at `evaluationAndScore.md` (repo root); history is tracked via git.
- **Before running any `git commit` in this plan, confirm the exact commit subject line
  with the user first** — this is a standing instruction in the user's global CLAUDE.md
  and applies to every commit below, not just the first.
- New prose authored in this task uses British English spelling (per the user's global
  CLAUDE.md). Do not retroactively respell unrelated, untouched text already in the file.
- No placeholder content anywhere in the rewritten file — the worked example (Task 2) must
  be a complete, concrete illustration, not a stub.
- Every `old_string` given below is an exact, verified quote from the current file — do
  not paraphrase it when applying the edit.

---

## File Structure

Single file modified, no new files created:

- **Modify:** `evaluationAndScore.md` (repo root) — the reusable production-readiness
  review prompt. Responsibility stays the same (a scoring rubric + review instructions
  for an LLM); this plan changes its internal structure, not its purpose.

---

### Task 1: Add scoring anchors, tech-stack detection, and triage framing

**Files:**
- Modify: `evaluationAndScore.md` (preamble, immediately after the existing intro
  paragraph and before the category list)

**Interfaces:**
- Consumes: nothing (first edit in the file, top region)
- Produces: a `## Scoring rubric` heading (1–3/4–6/7–8/9–10 anchors) and a
  `## Determine the actual tech stack before scoring` heading that Task 2's per-category
  template and Task 3's closing sections both refer back to by name. Also produces the
  phrase `Depth triage` as a forward reference to the `## Depth triage` heading Task 2
  will add.

Fixes: **W5** (no scoring anchors), **W4** (stale Java 21/Spring Boot 3.x version pin),
**W11** (role/scope tension — triage instinct not operationalised).

- [ ] **Step 1: Confirm the anchor text is still present and unique**

Run:
```bash
grep -n "Evaluate and score the project (1–10) in each of the following areas:" evaluationAndScore.md
```
Expected: exactly one match, at the current line number (line 5 as of 2026-07-12).

- [ ] **Step 2: Apply the edit**

Replace:
```
Be strict, critical, and objective. Do not assume the current implementation is correct. Challenge architectural decisions, identify technical debt, and point out opportunities for improvement. Prioritize long-term maintainability, scalability, reliability, and clean architecture over simply making the code work.

Evaluate and score the project (1–10) in each of the following areas:
```

With:
```
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
```

- [ ] **Step 3: Verify the edit landed correctly**

Run:
```bash
grep -c "^## Scoring rubric$" evaluationAndScore.md
grep -c "^## Determine the actual tech stack before scoring$" evaluationAndScore.md
grep -c "Depth triage" evaluationAndScore.md
```
Expected: `1`, `1`, `1`.

- [ ] **Step 4: Confirm commit subject line with the user, then commit**

Propose subject: `docs(prompt-engineering): add scoring rubric and tech-stack detection to evaluationAndScore`
Wait for the user's confirmation of the exact wording before running:
```bash
git add evaluationAndScore.md
git commit -m "docs(prompt-engineering): add scoring rubric and tech-stack detection to evaluationAndScore"
```

---

### Task 2: Consolidate the category list and add the per-category template

**Files:**
- Modify: `evaluationAndScore.md` (category list, per-category instructions,
  cross-cutting checklist, architecture-style checklist — the whole middle block)

**Interfaces:**
- Consumes: the `## Scoring rubric` and tech-stack sections from Task 1 (referenced by
  name in the new "Improvement" field of the per-category template; not re-defined here).
- Produces: a single consolidated, numbered category list (26 items, replacing the old
  30 + 22 + 6 = 58 raw items), a `## Depth triage` heading, a `## Per-category template`
  heading with a fixed field order (`Rationale` / `Evidence` / `Red flags` / `Risk` /
  `Improvement`), and one fully worked example category. Task 3's closing sections refer
  back to "the category scores above" and "category numbers" produced here.

Fixes: **W1** (63 sub-deliverables → 26 categories + depth triage), **W2** (removes
"Production readiness" as a duplicate scored category — the closing section becomes the
single source of truth, wired up in Task 3), **W3** (SOLID and Security stay single
categories; no separate top-level restatement), **W6** (mandatory `path:line` citation
format, plus an explicit "say so" escape hatch when no example exists — this escape
hatch extends W6's fix; it wasn't separately catalogued but shares W6's root cause),
**W7** (one complete worked example), **W8** (fixed heading level and field order per
category), **W9** (merges "Clean Code principles" / "Code readability" / "Maintainability"
into one category, and separately merges "Exception handling" / "Error handling" into
one — the second merge extends W9's fix to an identical duplicate the evaluation doc
didn't separately number), **W10** (cross-cutting list and architecture checklist folded
into categories, cutting the four-tier restatement down to two tiers going into Task 3).

- [ ] **Step 1: Confirm the anchor text is still present and unique**

Run:
```bash
grep -n "^1\. Overall architecture$" evaluationAndScore.md
grep -n "Do not recommend patterns unless they provide a clear benefit\. Avoid over-engineering\.$" evaluationAndScore.md
```
Expected: one match each, confirming the block boundaries are unchanged by Task 1.

- [ ] **Step 2: Apply the edit**

Replace the entire block starting at `1. Overall architecture` and ending at
`Do not recommend patterns unless they provide a clear benefit. Avoid over-engineering.`
(this spans the original 30-item category list, the "For every category" bullets, the
22-item cross-cutting list, and the 6-item architecture-style checklist) with:

```
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
```

- [ ] **Step 3: Verify the edit landed correctly**

Run:
```bash
grep -cE "^[0-9]+\. " evaluationAndScore.md
grep -c "^## Depth triage$" evaluationAndScore.md
grep -c "^## Per-category template$" evaluationAndScore.md
grep -c "Worked example" evaluationAndScore.md
grep -c "path/to/File.java:123" evaluationAndScore.md
grep -c "^30\. Production readiness$" evaluationAndScore.md
grep -c "^- Violations of SOLID$" evaluationAndScore.md
grep -c "^25\. Error handling$" evaluationAndScore.md
grep -c "^27\. Maintainability$" evaluationAndScore.md
```
Expected: `26`, `1`, `1`, `1`, `1`, `0`, `0`, `0`, `0` (the first four confirm the new
structure exists; the last four confirm the duplicated/removed old items are gone).

- [ ] **Step 4: Confirm commit subject line with the user, then commit**

Propose subject: `docs(prompt-engineering): consolidate evaluationAndScore categories and add per-category template`
Wait for the user's confirmation of the exact wording before running:
```bash
git add evaluationAndScore.md
git commit -m "docs(prompt-engineering): consolidate evaluationAndScore categories and add per-category template"
```

---

### Task 3: Rewrite closing sections to synthesise from categories, not re-derive

**Files:**
- Modify: `evaluationAndScore.md` (closing sections: Overall Score, Production
  Readiness, Top 10 Critical Issues, Quick Wins, Long-Term Refactoring Plan, Strengths)

**Interfaces:**
- Consumes: "the category scores above" and "category number" references, both defined
  by Task 2's consolidated 26-category list.
- Produces: the final section of the document — nothing downstream depends on this task.

Fixes: **W2** (Production Readiness verdict must be synthesised from, and reconciled
with, the category scores rather than independently derived), **W12** (multi-module
repos get a per-module score table before the single composite Overall Score), **W10**
(Top 10 / Quick Wins / Long-Term Plan must cite the category number they came from,
closing the remaining two of the original four restatement tiers).

- [ ] **Step 1: Confirm the anchor text is still present and unique**

Run:
```bash
grep -n "^At the end, provide:$" evaluationAndScore.md
```
Expected: exactly one match.

- [ ] **Step 2: Apply the edit**

Replace:
```
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
```

With:
```
At the end, provide:

## Overall Score
A single overall score from 1–10, computed as the average of the category scores above. If the repository is a multi-module monorepo, first give a per-module score table (module name, weighted average of that module's category scores, one-line rationale), then the single composite Overall Score beneath it — do not collapse an unevenly-mature monorepo into one number with no way to see the spread.

## Production Readiness
Would you approve this project for production? Answer by synthesising the category scores already given above — name the specific categories (by number) that drove your verdict, especially Security, Testing, Concurrency & thread safety, and Logging and observability. Do not re-derive a separate, independent judgement; if your verdict here would contradict any category score above, resolve the contradiction by revising the category score, not by ignoring it here.

## Top 10 Critical Issues
Rank the most important issues by severity. Every issue listed here must cite the category number it came from (e.g. "Category 13 — Security"). Do not introduce a new finding in this section that wasn't raised in a category write-up above.

## Quick Wins
List improvements that can be completed in under one day. Cite the category number each one came from.

## Long-Term Refactoring Plan
Recommend architectural improvements in priority order. Cite the category number(s) each one came from.

## Strengths
Highlight parts of the project that are particularly well designed.

Be candid and highly critical. Treat this review as if the code will support a high-traffic production system with millions of users. Focus on practical, evidence-based recommendations rather than theoretical perfection.
```

- [ ] **Step 3: Verify the edit landed correctly**

Run:
```bash
grep -c "per-module score table" evaluationAndScore.md
grep -c "Do not re-derive a separate, independent judgement" evaluationAndScore.md
grep -c "must cite the category number it came from" evaluationAndScore.md
```
Expected: `1`, `1`, `1`.

- [ ] **Step 4: Confirm commit subject line with the user, then commit**

Propose subject: `docs(prompt-engineering): tie evaluationAndScore closing sections to category scores`
Wait for the user's confirmation of the exact wording before running:
```bash
git add evaluationAndScore.md
git commit -m "docs(prompt-engineering): tie evaluationAndScore closing sections to category scores"
```

---

### Task 4: Full-file verification against all 12 weaknesses

**Files:**
- None modified — this task only reads `evaluationAndScore.md`.

**Interfaces:**
- Consumes: the fully rewritten file produced by Tasks 1–3.
- Produces: a pass/fail confirmation that every weakness W1–W12 (plus the two flagged
  extensions) was actually fixed, not just individually plausible in isolation.

- [ ] **Step 1: Run the comprehensive checklist**

Run:
```bash
echo "W4 — stale version pin gone:"; grep -c "Java 21" evaluationAndScore.md; grep -c "Spring Boot 3\.x" evaluationAndScore.md
echo "W5 — scoring rubric present:"; grep -c "^## Scoring rubric$" evaluationAndScore.md
echo "W1 — category count and depth triage:"; grep -cE "^[0-9]+\. " evaluationAndScore.md; grep -c "^## Depth triage$" evaluationAndScore.md
echo "W2 — duplicate production-readiness category gone, closing section synthesises:"; grep -c "^30\. Production readiness$" evaluationAndScore.md; grep -c "Do not re-derive a separate, independent judgement" evaluationAndScore.md
echo "W3 — no standalone cross-cutting duplication:"; grep -c "^- Violations of SOLID$" evaluationAndScore.md; grep -c "^- Security vulnerabilities$" evaluationAndScore.md
echo "W6 — citation format and escape hatch:"; grep -c "path/to/File.java:123" evaluationAndScore.md; grep -c "say so explicitly" evaluationAndScore.md
echo "W7 — worked example present:"; grep -c "Worked example" evaluationAndScore.md
echo "W8 — per-category template present:"; grep -c "^## Per-category template$" evaluationAndScore.md
echo "W9 — merged near-synonyms gone:"; grep -c "^27\. Maintainability$" evaluationAndScore.md; grep -c "^25\. Error handling$" evaluationAndScore.md
echo "W10 — closing sections cite category numbers:"; grep -c "must cite the category number it came from" evaluationAndScore.md
echo "W11 — triage framing present:"; grep -c "triages under time pressure" evaluationAndScore.md
echo "W12 — per-module table present:"; grep -c "per-module score table" evaluationAndScore.md
```
Expected: every "gone"/duplicate check returns `0`; every "present" check returns `1`;
the category-count check returns `26`.

- [ ] **Step 2: Read the full rewritten file once, top to bottom**

Confirm the document reads coherently as a single prompt — no leftover references to the
old 30/22/6 structure, no broken forward-reference to "Depth triage" or "the rubric
above," no duplicate headings. Fix inline and re-run Step 1 if anything is off; no
separate commit is needed for this task since it makes no file changes.

---

## Self-Review

**Spec coverage:** W1 → Tasks 1(triage sentence)+2(categories+depth-triage); W2 → Tasks
2(category removed)+3(closing synthesis); W3 → Task 2; W4 → Task 1; W5 → Task 1; W6 →
Task 2; W7 → Task 2; W8 → Task 2; W9 → Task 2 (both merges); W10 → Tasks 2(tiers
folded)+3(citations back to categories); W11 → Task 1(framing)+2(depth triage
mechanism); W12 → Task 3. All 12 covered, no gaps.

**Placeholder scan:** no TBD/TODO markers; the worked example is a complete, concrete
write-up, not a stub.

**Type/name consistency:** "Depth triage" (Task 1's forward reference) matches the `##
Depth triage` heading text added in Task 2 exactly. "Scoring rubric" and "the actual
tech-stack version identified above" (used in Task 2's template) match Task 1's heading
names exactly. "category scores above" / "category number" (Task 3) match the numbered
list format (`<N>. <Category name>`) established in Task 2.
