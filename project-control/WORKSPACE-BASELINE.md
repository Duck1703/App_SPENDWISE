# WORKSPACE-BASELINE.md — Spendwise Android

Read-only baseline of the two reference repositories, captured during P0 on
2026-10-03. This file records facts only — no architecture analysis (that is P1/P2).

## REFERENCE_ANDROID — `D:\app_spendwise\kotlin-accelerator-ai`

| Field | Value |
| --- | --- |
| EXISTS | YES |
| GIT_REPO | YES |
| BRANCH | `master` |
| HEAD | `5f9622c59ce602ce33c98ee633ba799f903b67e1` |
| DIRTY_STATE | Clean — no modified, staged, or untracked files reported by `git status --porcelain` at capture time |
| ANDROID INDICATORS | Gradle multi-module Android project: `settings.gradle.kts`, `build.gradle.kts`, `gradlew`/`gradlew.bat`, `gradle.properties`, modules `app/`, `core/`, `data/`, `domain/`, `feature/`, `build-logic/`, `theme/`, `ui-components/`, `secrets/` |
| MODIFIED_BY_P0 | NO |

## BACKEND_REFERENCE — `D:\spendwise`

| Field | Value |
| --- | --- |
| EXISTS | YES |
| GIT_REPO | YES |
| BRANCH | `main` |
| HEAD | `8194daa9959f2d568dc13ab0f211787195efc79d` |
| EXPECTED HEAD | `8194daa9959f2d568dc13ab0f211787195efc79d` — matches |
| DIRTY_STATE | Pre-existing user-owned changes (expected, left untouched): `M README.md`; untracked `HUONG-DAN-SU-DUNG.md`, `docs/SPENDWISE-REQ.md` |
| EXPECTED IDENTITY | Spendwise Spring Boot backend — authoritative server for the Android client |
| MODIFIED_BY_P0 | NO |

## Notes

- Both repositories are READ ONLY to the Android team (see
  [WORKSPACE-BOUNDARIES.md](WORKSPACE-BOUNDARIES.md)).
- Dirty state in `D:\spendwise` is user-owned and pre-existing; it is recorded,
  not a defect to fix.
- If future audits find HEAD or working-tree state differs from this baseline,
  record the actual state and investigate — never reset it.
