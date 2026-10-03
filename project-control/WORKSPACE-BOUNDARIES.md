# WORKSPACE-BOUNDARIES.md — Spendwise Android

This file defines the filesystem permissions for all Android-team agents. It is
binding. See [../agent-system/WORKFLOW-CONTRACT.md](../agent-system/WORKFLOW-CONTRACT.md)
for the workflow that depends on it.

## Locations

| Name | Path | Mode |
| --- | --- | --- |
| WORKSPACE ROOT | `D:\app_spendwise` | container only — no writes outside PRODUCT_ANDROID |
| REFERENCE_ANDROID | `D:\app_spendwise\kotlin-accelerator-ai` | **READ ONLY** |
| PRODUCT_ANDROID | `D:\app_spendwise\spendwise-android` | **READ / WRITE** |
| BACKEND_REFERENCE | `D:\spendwise` | **READ ONLY** |

## Permissions

- **REFERENCE_ANDROID** (`D:\app_spendwise\kotlin-accelerator-ai`): agents may read
  files and run read-only Git inspection. NEVER: edit, format, commit, reset,
  stash, clean, change dependencies, run builds that mutate tracked files, or
  intentionally write generated files into that repository.
- **BACKEND_REFERENCE** (`D:\spendwise`): agents may read source, configuration,
  and docs. NEVER modify anything. The backend is owned outside the Android team.
- **PRODUCT_ANDROID** (`D:\app_spendwise\spendwise-android`): the ONLY writable
  location for this project. All artifacts — governance, docs, and eventually the
  Android app — live here.
- No other path under `D:\app_spendwise` may be created or written by agents.

## Backend gap procedure

If Android work requires a backend capability that does not exist (or a contract
detail that is missing/incorrect):

1. Document a **BACKEND GAP** (in the milestone file and/or BACKEND-CONTRACT.md):
   what capability, where it was discovered, and the evidence.
2. Explain the mobile impact (what the app cannot do or must degrade).
3. Propose the minimal backend contract needed.
4. **STOP** work that depends on it.
5. Wait for HUMAN approval / a separate backend task.

Agents must NOT:

- modify `D:\spendwise` to close the gap;
- silently work around incorrect/missing backend behavior in the Android app when
  doing so would create architectural debt (e.g., inventing parsing, dedup, or
  domain rules locally that belong server-side).

## Violation handling

Any write attempt outside PRODUCT_ANDROID is a contract violation. Stop, report to
ATLAS, and record the event in the milestone evidence.
