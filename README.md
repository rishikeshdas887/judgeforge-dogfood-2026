# DOGFOOD — Hack The Dog

A self-hostable hackathon judging portal with a public project gallery, organizer-controlled judge assignment, configurable weighted scoring, role-isolated judge ballots, deterministic cross-judge normalization, organizer judging progress, CSV export, and append-only audit logging.

## Current Tier Claim

This repository currently claims:

* **T1 — Fixture-backed hackathon portal**
* **T2 — Judge assignment and judging integrity**

T3 community voting and T4 advanced/optional capabilities are not claimed.

## Features

### T1

* Public project gallery backed by the repository fixture data.
* Project detail information including track, title, summary, team, and repository link.
* Submission endpoint with event/fixture validation.
* Closed-event submissions are rejected.
* Fixture-backed project data is available without an external hosted service.

### T2

* Organizer-controlled judge assignment.
* Explicit project assignment per judge.
* Batch and deterministic algorithmic judge assignment.
* Configurable rubric criteria, weights, and maximum scores.
* Backend validation of judge/project assignment.
* Judge scoring restricted to the authenticated judge identity and assigned projects.
* Participants cannot access judge scoring endpoints.
* Organizer judging-progress dashboard.
* Deterministic cross-judge normalization.
* Organizer/admin results access.
* Organizer/admin CSV export.
* Append-only audit events for security-sensitive judging operations.

## Architecture

The application is split into two containers:

```text
Browser
   |
   v
Frontend (React + Vite + Nginx)
   |
   | HTTP / reverse proxy
   v
Backend (Spring Boot)
   |
   +---- fixture data
   +---- assignments
   +---- ballots
   +---- rubric
   +---- judge invitations
   +---- normalization results
   +---- audit events
```

The backend stores application state as JSON files under:

```text
backend/data/
```

Docker Compose bind-mounts this directory into the backend container at:

```text
/app/data
```

No external database or hosted API is required for the current implementation.

## Running Locally

Requirements:

* Docker
* Docker Compose

Start the complete application:

```bash
docker compose up --build
```

The application is available at:

```text
http://localhost:8080
```

The frontend container serves the web application and proxies API requests to the backend container.

Stop the application:

```bash
docker compose down
```

## Authentication

The current demo uses deterministic cookie-based sessions for the local hackathon environment.

Configured demo sessions are:

```text
Organizer:
Cookie: session=org_7f2a

Judge A:
Cookie: session=jdg_a_91bc

Judge B:
Cookie: session=jdg_b_44de

Participant:
Cookie: session=prt_2e88
```

The backend resolves the authenticated role server-side.

## Main Routes

### Public / portal

```text
GET  /projects
POST /projects/new
```

### Authentication

```text
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

### Judge

```text
GET /api/judge/projects
GET /api/judge/ballots
PUT /api/judge/ballots/{projectId}
GET /api/judge/scores
```

### Organizer

```text
GET /api/organizer/rubric
PUT /api/organizer/rubric

GET  /api/organizer/judges
POST /api/organizer/judges/...
PUT  /api/organizer/judges/...
POST /api/organizer/judges/assignments/batch
POST /api/organizer/judges/assignments/auto

GET /api/organizer/judging-progress

GET /api/audit
```

### Results

```text
POST /api/results/normalize
GET  /api/results
```

### Export

```text
GET /api/export.csv
```

Organizer/admin endpoints enforce role access on the backend.

## Judge Assignment

Judge assignment is organizer-controlled.

The organizer supplies an explicit list of project IDs for a judge. The backend validates:

* the judge exists
* each project exists
* each project belongs to an allowed track for that judge

The active assignment set for that judge is then replaced with the submitted list.

The current implementation does not claim an automatic balancing algorithm.

## Scoring

The active rubric defines:

* criterion ID
* criterion name
* criterion weight
* maximum score

Weights must total 100%.

Each ballot must contain a numeric score for every configured criterion and scores outside the configured range are rejected.

The weighted score is calculated as:

```text
sum((score / max_score) * (weight / 100)) * 5
```

The calculation is performed from the active rubric configuration.

## Judge Isolation

The authenticated judge identity is resolved by the backend.

A judge may only access ballots for projects assigned to that judge.

A request to score an unassigned project returns:

```text
HTTP 403
```

The backend also records a `BALLOT_ACCESS_DENIED` audit event for this denied access attempt.

This protection is enforced server-side and does not depend on hiding controls in the frontend.

## Auditing

Audit events are stored in:

```text
backend/data/audit-events.json
```

Each event contains:

```text
actor
action
target
timestamp
before
after
reason
request_id
```

Implemented judging-related events include:

```text
BALLOT_SUBMITTED
BALLOT_EDITED
BALLOT_ACCESS_DENIED
JUDGE_ASSIGNED
NORMALIZATION_EXECUTED
```

The application exposes audit records through:

```text
GET /api/audit
```

This endpoint is organizer/admin-only.

There is no normal application endpoint for deleting audit history.

Requests receive an `X-Request-Id` response header. A supplied request ID is preserved; otherwise the backend generates one.

## Normalization

Normalization version:

```text
zscore-v1
```

Only eligible assigned ballots are included.

For each judge and rubric criterion, the system calculates:

```text
mean
population standard deviation
```

The z-score is:

```text
z = (x - mean) / max(stddev, 1e-6)
```

The minimum sample size for normalization is:

```text
3
```

When fewer than three eligible scores exist for a judge/criterion pair, the raw score is retained.

For normalized criteria, z-scores are min-max mapped into:

```text
[0, criterion.max_score]
```

If the global z-score range collapses, the raw criterion score is retained.

The normalized weighted score uses the same configured rubric weights:

```text
sum((normalized_score / max_score) * (weight / 100)) * 5
```

Normalization results retain both raw and normalized values and their delta.

Each normalization run records parameters including sample sizes, means, standard deviations, fallback decisions, rescaling details, and the SHA-256 input fingerprint.

## Reproducibility

Eligible ballots are filtered to valid judge/project pairs with active assignments.

Before normalization they are processed deterministically by:

```text
judge id
project id
```

The input fingerprint is computed from the rubric and the sorted eligible ballot inputs.

For the restored fixture state, the verified input contains:

```text
126 eligible ballots
```

and produces the fingerprint:

```text
7f95fe88f690c08bb14cc99c5cc5f675edde59e536ed306dc0f9934a198057d6
```

Repeated normalization runs against the same restored input state produce the same normalization version and fingerprint.

## CSV Export

The organizer can export judging information through:

```text
GET /api/export.csv
```

The export is organizer/admin-only.

## Results

The current implementation provides the latest normalization run through:

```text
GET /api/results
```

This endpoint is organizer/admin-only.

A separate result-publication action is not currently implemented.

Therefore this repository does **not** claim a `RESULT_PUBLISHED` audit event.

## Data Files

The current backend data directory contains:

```text
assignments.json
audit-events.json
ballots.json
judge-invitations.json
normalization-results.json
rubric.json
```

The application is designed to run with these local files and does not require a remote database for the current submission.

## Acceptance Verification

The repository includes:

```text
acceptance-report.txt
```

The verified acceptance run reports:

```text
T1  gallery is public ................. PASS
T1  project from fixtures shown ....... PASS
T1  closed event refuses submissions .. PASS
T2  judge sees own scores ............. PASS
T2  judge cannot see peer scores ...... PASS
T2  participant blocked ............... PASS
T2  csv export works .................. PASS
```

The current claim is therefore:

```text
claimed T1 T2, verified T1 T2
```

## Repository Structure

```text
.
├── backend/
│   ├── src/
│   ├── data/
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   ├── Dockerfile
│   └── package.json
├── fixtures.json
├── docker-compose.yml
├── .dogfood.toml
├── JUDGING.md
├── acceptance-report.txt
├── run.py
└── spec.md
```

## Scope and Honesty

This repository intentionally documents only behavior that is implemented and verified.

The current submission does not claim:

* T3 community voting
* T4 stretch capabilities
* a separate result-publication workflow
* a `RESULT_PUBLISHED` audit event
* an external database or hosted infrastructure
