# API-First Coverage — DOGFOOD 2026

## Purpose

This document is evidence for the DOGFOOD 2026 **API First +3** bonus. The published `openapi.yaml` documents the business actions exposed by the web UI plus the public/T4 integration APIs.

The API contract is intentionally aligned to the existing JudgeForge routes. It does **not** require a hosted database, third-party authentication provider, API key, paid service, or cloud account.

## Bonus interpretation

The DOGFOOD requirement is that every action available in the UI is available through a documented API, with a published OpenAPI specification. The repository's `openapi.yaml` is the published contract for that surface.

## UI action → API coverage

| UI surface / action | Method | API route |
|---|---:|---|
| Session check | GET | `/api/auth/me` |
| Role login | POST | `/api/auth/login?role=...` |
| Logout | POST | `/api/auth/logout` |
| Public gallery search | GET | `/projects` |
| Published results | GET | `/api/results/published` |
| Participant event | GET | `/api/participant/event` |
| Team list | GET | `/api/teams` |
| Create team | POST | `/api/teams` |
| Create invite | POST | `/api/teams/{teamId}/invites` |
| Accept invite | POST | `/api/teams/invites/{token}/accept` |
| My projects | GET | `/api/projects/mine` |
| Create project draft | POST | `/api/projects` |
| Edit project draft | PUT | `/api/projects/{projectId}` |
| Submit project | POST | `/api/projects/{projectId}/submit` |
| Judge project list | GET | `/api/judge/projects` |
| Judge rubric | GET | `/api/judge/rubric` |
| Judge ballots | GET | `/api/judge/ballots` |
| Save judge ballot | PUT | `/api/judge/ballots/{projectId}` |
| Judge score isolation probe | GET | `/api/judge/scores` |
| Organizer event view | GET | `/api/organizer/event` |
| Organizer event update | PUT | `/api/organizer/event` |
| Create event | POST | `/api/organizer/events` |
| Organizer rubric view | GET | `/api/organizer/rubric` |
| Organizer rubric update | PUT | `/api/organizer/rubric` |
| Judge list | GET | `/api/organizer/judges` |
| Judge invite | POST | `/api/organizer/judges/{judgeId}/invite` |
| Assign projects to judge | PUT | `/api/organizer/judges/{judgeId}/assignments` |
| Batch assignments | POST | `/api/organizer/judges/assignments/batch` |
| Auto assignments | POST | `/api/organizer/judges/assignments/auto` |
| Judging progress | GET | `/api/organizer/judging-progress` |
| Organizer results | GET | `/api/results` |
| Normalize results | POST | `/api/results/normalize` |
| Publish results | POST | `/api/results/publish` |
| Results CSV export | GET | `/api/export.csv` |
| Community voting config | GET | `/api/community/voting/config` |
| Community randomized ballot | GET | `/api/community/voting/ballot` |
| Community vote | POST | `/api/community/voting/vote` |
| Community comments | GET | `/api/community/comments` |
| Add community comment | POST | `/api/community/comments` |
| Community voting results | GET | `/api/community/voting/results` |
| Organizer voting config | GET | `/api/organizer/community-voting` |
| Organizer voting update | PUT | `/api/organizer/community-voting` |
| Audit trail | GET | `/api/audit` |

## T4 / integration coverage

| Capability | Method | API route |
|---|---:|---|
| Public event API | GET | `/api/v1/event` |
| Public project list API | GET | `/api/v1/projects` |
| Public project API | GET | `/api/v1/projects/{projectId}` |
| Register webhook | POST | `/api/organizer/webhooks` |
| List webhooks | GET | `/api/organizer/webhooks` |
| Delete webhook | DELETE | `/api/organizer/webhooks/{webhookId}` |
| Generate certificate | POST | `/api/organizer/certificates/projects/{projectId}` |
| List project certificates | GET | `/api/organizer/certificates/projects/{projectId}` |
| Read certificate | GET | `/api/certificates/{certificateId}` |
| Participant records | GET | `/api/participant/participation-records` |
| Create signed judge snapshot | POST | `/api/organizer/judge-records/snapshot` |
| List signed judge records | GET | `/api/organizer/judge-records` |
| Public judge record | GET | `/api/v1/judge-records/{recordId}` |
| Verify signed judge record | GET | `/api/v1/judge-records/{recordId}/verify` |
| Public verification key | GET | `/api/v1/judge-records/public-key` |
| Bulk project export | GET | `/api/organizer/projects/export.csv` |
| Bulk project import | POST | `/api/organizer/projects/import` |

## What is deliberately not an API action

Local UI-only state such as search-box keystrokes, tab selection, modal opening, client-side filtering, and navigation does not represent a server-side business action and therefore does not need a separate endpoint.

## Authentication and role isolation

The contract uses the existing local `session` cookie. Protected operations require the appropriate server-side role; documentation does not replace backend authorization. The acceptance suite separately probes judge score isolation and participant blocking.

## Reproducible verification

Run the included verifier against the repository's `openapi.yaml` and the actual frontend route map:

```bash
python3 verify-api-first.py
```

The verifier is intentionally dependency-light. When PyYAML is available, it also parses the OpenAPI document; otherwise it still checks every expected method/path pair as text.

## Cost / dependency note

This documentation requires no paid API, hosted database, cloud account, proprietary service, or external auth provider. It is a static OpenAPI contract plus a local verification script.
