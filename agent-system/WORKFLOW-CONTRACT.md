# WORKFLOW-CONTRACT.md — Spendwise Android Agent Company

This is the governing workflow contract for all agents operating on the Spendwise
Android repository. It is binding on ATLAS, FLUX, SENTINEL, and ARGUS, and on any
human-directed AI tool acting in their place.

Related documents:

- [../AGENTS.md](../AGENTS.md) — runtime-neutral entrypoint
- [agents/](agents/) — canonical role definitions
- [../project-control/WORKSPACE-BOUNDARIES.md](../project-control/WORKSPACE-BOUNDARIES.md) — filesystem permissions
- [../project-control/ROADMAP.md](../project-control/ROADMAP.md) — phase plan
- [../project-control/DECISIONS.md](../project-control/DECISIONS.md) — decision log

---

## A. Authority hierarchy

```
THE HUMAN
   > ATLAS (lead / architect / coordinator)
      > FLUX (implementation)
      > SENTINEL (testing)
      > ARGUS (independent QA)
```

- THE HUMAN is the ultimate authority on scope, architecture, milestones, and releases.
- ATLAS coordinates and arbitrates technical questions but cannot override THE HUMAN.
- FLUX, SENTINEL, and ARGUS execute within their role boundaries; they do not
  self-extend their scope.

## B. Default milestone flow

Every milestone follows this sequence unless THE HUMAN explicitly approves a deviation:

1. ATLAS creates/updates the milestone brief (template:
   [../project-control/templates/MILESTONE-BRIEF.md](../project-control/templates/MILESTONE-BRIEF.md)).
2. ATLAS verifies the source-of-truth evidence the milestone depends on
   (requirements, baselines, backend contract, prior decisions).
3. FLUX implements the approved scope, then produces an implementation handoff
   (template: [../project-control/templates/IMPLEMENTATION-HANDOFF.md](../project-control/templates/IMPLEMENTATION-HANDOFF.md)).
4. SENTINEL independently tests against requirements and acceptance criteria, then
   produces a test report (template:
   [../project-control/templates/TEST-REPORT.md](../project-control/templates/TEST-REPORT.md)).
5. FLUX resolves implementation defects if needed.
6. SENTINEL reruns the affected regression scope.
7. ARGUS performs independent QA review and produces a QA report (template:
   [../project-control/templates/QA-REPORT.md](../project-control/templates/QA-REPORT.md)).
8. Findings return to the appropriate owner (FLUX for defects, ATLAS for
   architecture/requirement questions, SENTINEL for test gaps).
9. ATLAS reconciles all evidence (handoff + test report + QA report + findings).
10. ATLAS reports milestone readiness: `READY_FOR_HUMAN_REVIEW`.
11. Human gate applies where the milestone requires it. THE HUMAN decides closure.
12. Only after closure may the next milestone begin.

## C. No self-certification

- FLUX cannot QA its own code and cannot declare its work approved.
- SENTINEL cannot redefine requirements or acceptance criteria to match the
  implementation.
- ARGUS cannot silently fix production code; it reports findings only.
- ATLAS cannot bypass a human gate and cannot approve a milestone on THE HUMAN's behalf.
  ATLAS may only mark `READY_FOR_HUMAN_REVIEW`.

## D. Evidence requirements

Every milestone must preserve the following evidence (in the milestone file under
`project-control/milestones/` and/or the linked reports):

- starting SHA / repository state;
- requested scope (what was authorized, by whom);
- files changed;
- test evidence (what ran, what passed, what failed);
- QA findings (with severities);
- architecture deviations (and whether approved);
- backend gaps discovered;
- final state (SHA / summary);
- next authorized task.

A milestone without this evidence is not complete.

## E. Severity model

Findings and defects are classified as:

| Severity | Meaning |
| --- | --- |
| `BLOCKER` | Prevents the milestone from proceeding or makes the result incorrect/unsafe. Must be resolved (or explicitly waived by THE HUMAN) before the milestone can close. |
| `MAJOR` | Significant defect, architecture violation, or requirement miss. Should be resolved before closure; may be deferred only with ATLAS recommendation and HUMAN approval. |
| `MINOR` | Imperfection worth fixing; does not block milestone closure but must be logged and scheduled. |
| `NOTE` | Observation, risk, or improvement suggestion. No action required unless adopted. |

## F. Outcome model

Milestones, reports, and reviews conclude with one of:

| Outcome | Meaning |
| --- | --- |
| `PASS` | All acceptance criteria met; no open BLOCKER/MAJOR findings; evidence complete. |
| `PARTIAL` | Some criteria met; open MINOR findings or incomplete evidence, but no BLOCKER. Requires explicit ATLAS summary of what is partial and HUMAN decision on closure. |
| `BLOCKED` | A BLOCKER exists, required evidence is missing, or a stop condition was hit. Cannot close until resolved. |

## G. Stop conditions

Agents must STOP and escalate to ATLAS (and where required, THE HUMAN) instead of
improvising when:

- performing the task would exceed the agent's writable scope;
- the task would write outside `D:\app_spendwise\spendwise-android`;
- a backend change appears necessary (record a BACKEND GAP instead — see
  [../project-control/WORKSPACE-BOUNDARIES.md](../project-control/WORKSPACE-BOUNDARIES.md));
- the senior reference project would need modification;
- architecture requirements conflict or an undocumented architecture decision is needed;
- user-owned work (untracked/dirty files, existing content) might be overwritten;
- a destructive Git operation appears necessary (`reset --hard`, `clean`, `rebase`,
  `--amend`, force push, branch deletion);
- requirements are materially ambiguous;
- completing the task would expand milestone scope without approval;
- secrets or credentials are discovered that would be committed.

Stopping is a correct outcome. Improvising past a stop condition is a contract
violation.

## H. No hidden scope expansion

- No agent adds features, layers, abstractions, dependencies, or "best practice"
  refactors that were not requested in the approved milestone brief.
- If something seems obviously needed but is out of scope, the agent files a `NOTE`
  or `MINOR` finding and lets ATLAS decide whether to propose a scope change to
  THE HUMAN.
- The senior reference project is a reference, not a source to copy wholesale.
  Patterns are adopted only after explicit KEEP / ADAPT / REJECT classification.
