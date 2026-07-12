# Prompt evaluation: `evaluationAndScore.md`

**Date:** 2026-07-12
**Subject:** `evaluationAndScore.md` (repo root, 104 lines) — a reusable "production
readiness review" prompt engineered as an asset for Claude Projects / GPT-compatible
platforms.
**Scope:** this document evaluates the prompt as a piece of prompt engineering. It does
**not** execute the review the prompt describes.

> **Baseline note.** A prior evaluation (47/100, weaknesses W1–W17) was produced on
> 2026-07-10 but was lost — it was never committed to git and no copy survives in any
> stash or worktree. This is an independent, from-scratch pass. The score below is **not
> comparable** to the lost 47/100; the rubric, dimension count, and methodology all
> differ.

## Methodology

Scored against 9 standard prompt-engineering dimensions, 1–10 each, aggregated to a
score out of 100. No fix suggestions are included — this pass is diagnostic only; fixes
are deferred to a later roadmap session.

## Dimension scores

| # | Dimension | Score /10 |
|---|---|---|
| 1 | Role & context framing | 7 |
| 2 | Task clarity & specificity | 5 |
| 3 | Scope control | 2 |
| 4 | Output-format / structural constraints | 4 |
| 5 | Internal consistency | 3 |
| 6 | Actionability & verifiability | 3 |
| 7 | Example/anchor usage | 1 |
| 8 | Portability across models/repos | 2 |
| 9 | Cognitive load & redundancy | 2 |
| | **Total (÷90, scaled to /100)** | **32 / 100** |

## Dimension notes

**1. Role & context framing — 7/10.** The persona ("Senior Staff Engineer conducting a
production readiness review," line 1) and tone instructions ("strict, critical, and
objective," line 3) are set clearly, and the closing line reinforces the same framing
("as if the code will support a high-traffic production system with millions of users,"
line 104) — a sound open/close reinforcement pattern. It loses points because nothing
tells the model what to do if repository context is partial or exceeds context window,
and the persona (a senior engineer who scopes and triages) sits in tension with the
exhaustive, unscoped task that follows (see W11).

**2. Task clarity & specificity — 5/10.** The per-category deliverable shape is spelled
out (lines 40–45: score, rationale, examples, risks, improvements, code samples), which
is genuinely useful structure. It's held back by categories with no stated boundaries
from each other (see W9) and no stated depth/length target relative to repo size, so
"comprehensive" (line 1) is left entirely to model discretion.

**3. Scope control — 2/10.** The lowest score in this evaluation. The prompt asks for 30
individually-scored categories (lines 7–36), a further 22-item cross-cutting checklist
(lines 49–71), a 6-item architecture-style checklist (lines 75–80), and 5 closing
summary sections (lines 86–102) — 63 sub-deliverables in one pass, each with its own
sub-structure. See W1.

**4. Output-format / structural constraints — 4/10.** The five closing sections get
explicit `##` headers (lines 86–102), which is a real anchor. The 30 category
write-ups and 22 cross-cutting items get no equivalent template — see W8.

**5. Internal consistency — 3/10.** Two direct duplications exist without a
reconciliation rule: category 30 vs. the closing "Production Readiness" section (W2),
and category 3 / category 13 vs. their cross-cutting-list restatements (W3).

**6. Actionability & verifiability — 3/10.** "Point to concrete examples in the code"
(line 42) pushes toward evidence-based output, which is good instinct, but neither a
citation format (W6) nor any scoring anchor (W5) is defined, so both the score and the
"evidence" backing it are unverifiable by a reader.

**7. Example/anchor usage — 1/10.** Zero worked examples anywhere in the file. See W7.

**8. Portability across models/repos — 2/10.** Despite being engineered as a reusable
asset, the prompt hardcodes a stack/version pair (line 44) and a single-repo,
single-module assumption (W4, W12) with no parameterization.

**9. Cognitive load & redundancy — 2/10.** The four-tier structure (scored categories →
cross-cutting list → architecture checklist → closing summaries) asks the model to
re-derive and re-rank overlapping findings up to four times with no consistency
requirement between them (W10).

## Weakness catalog

**W1 — Scope control.** Quote: "Evaluate and score the project (1–10) in each of the
following areas:" (line 5, 30 items at lines 7–36), plus "Additionally, identify:"
(line 47, 22 items at lines 49–71), plus "Evaluate whether the project follows:"
(line 73, 6 items at lines 75–80), plus five closing sections (lines 86–102). 63
sub-deliverables demanded in a single pass. **Failure mode:** output is either
truncated before reaching the closing sections, or every category gets shallow
boilerplate to fit a token budget — defeating "comprehensive" (line 1) and "strict"
(line 3).

**W2 — Internal consistency.** Quote: category 30 "Production readiness" (line 36) vs.
"## Production Readiness / Would you approve this project for production? Why or why
not?" (lines 89–90). Two independently-elicited verdicts on the same question with no
reconciliation rule. **Failure mode:** the two can contradict each other in a single
output (e.g. an 8/10 category score alongside a "not production ready" verdict) with
nothing in the prompt to catch it.

**W3 — Internal consistency.** Quote: "Violations of SOLID" (line 53) duplicates
category 3 "SOLID principles" (line 9); "Security vulnerabilities" (line 60) duplicates
category 13 "Security" (line 19). **Failure mode:** the model either repeats itself
verbatim across two sections or silently diverges — different findings or severity for
the "same" concern — with no rule for which should win.

**W4 — Portability.** Quote: "Suggest improvements following modern Java 21 and Spring
Boot 3.x best practices" (line 44) inside a prompt whose line 1 otherwise frames a
generic "Java Spring Boot repository." **Failure mode:** the moment the target repo is
on a newer stack — this very repo is Java 25 / Spring Boot 4.1.0 per its own CLAUDE.md —
every improvement suggestion is anchored to a stale target, steering the user toward
advice for a superseded version.

**W5 — Actionability & verifiability.** Quote: "Assign a score from 1–10." (line 40)
with no accompanying definition anywhere in the file of what a 1, 5, or 10 means for any
of the 30 categories. **Failure mode:** scores are unreproducible — the same repo scored
in two separate runs, or by two different models, can land anywhere across a wide range
with no basis to say one is more correct, making the numeric output decorative rather
than diagnostic.

**W6 — Actionability & verifiability.** Quote: "Point to concrete examples in the code"
(line 42) — no `file:line` citation format is mandated. **Failure mode:** the model can
satisfy the instruction with a paraphrased or fabricated example that reads as concrete
evidence but cannot be traced back to the actual repository.

**W7 — Example/anchor usage.** No worked example of a completed category write-up
appears anywhere in the 104-line prompt. **Failure mode:** with 30 categories each
needing 5 sub-elements (score/rationale/example/risk/improvement) and no anchor,
formatting and depth drift within a single output — early categories get full
treatment, later ones taper off as the response grows.

**W8 — Output-format constraints.** Quote: lines 38–45 give the per-category
requirements as a flat bullet list with no heading level or field order specified,
unlike the five closing sections which get explicit `##` headers (lines 86–102).
**Failure mode:** category output structure is left to model discretion, so
headings/ordering vary run to run, making the output hard to diff or to
script-extract from downstream (e.g. "give me category 13's score").

**W9 — Task clarity.** Quote: "Clean Code principles" (line 11), "Code readability"
(line 32), and "Maintainability" (line 33) are listed as three separately-scored
categories with no stated boundary between them. **Failure mode:** the model produces
three overlapping write-ups repeating the same observations under different headings,
inflating length without adding distinct signal.

**W10 — Cognitive load & redundancy.** Quote: compare "Code smells" (line 49) and "Poor
naming" (line 64) against category 26 "Code readability" (line 32) and the closing
"## Top 10 Critical Issues" (line 92) — the same underlying findings can be
re-derived and re-ranked up to four times across the document's four tiers.
**Failure mode:** severity or ranking judgements about the same issue diverge across
the four restatements (e.g. an issue implied critical in the Top 10 but not flagged, or
scored high, in its own category section), with no instruction requiring consistency.

**W11 — Role framing vs. scope.** Quote: line 1 frames a single "Senior Staff Engineer"
persona; lines 5–102 demand 63 unscoped sub-deliverables at uniform depth.
**Failure mode:** the persona (a time-constrained senior engineer who triages) and the
task (exhaustive, equal-depth coverage of everything) pull in opposite directions — the
model cannot simultaneously act like a reviewer who prioritizes and produce an unbounded,
uniformly-deep review of all 30 categories.

**W12 — Portability.** Quote: "Java Spring Boot repository" (line 1) and the version
pin at line 44 assume a single-module, single-stack target; "## Overall Score / An
overall score from 1–10" (lines 86–87) forces one composite number regardless of
whether the codebase is a multi-module monorepo with uneven module maturity.
**Failure mode:** a genuinely uneven codebase (some modules production-grade, others
early-stage) gets flattened into one misleading composite score with no way to see the
spread.

## Summary for next steps

Weakest dimensions, in order: **example/anchor usage (1/10)**, **scope control (2/10)**,
**portability (2/10)**, and **cognitive load & redundancy (2/10)**. These four,
addressed together, would move the needle furthest: adding one worked example category,
cutting the 63 sub-deliverables down to a single non-overlapping list, parameterizing
the stack/version assumptions, and collapsing the four-tier restatement structure into
one.

This document is diagnostic only. Fix proposals and a rewrite plan are deferred to a
separate roadmap-brainstorming session, per the staged evaluate → roadmap → rewrite
workflow this asset is being built under.
