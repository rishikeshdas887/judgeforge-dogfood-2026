# DOGFOOD — Hack The Dog

A self-hostable hackathon judging portal with a public project gallery, organizer-controlled judge assignment, configurable weighted scoring, role-isolated judge ballots, deterministic cross-judge normalization, organizer judging progress, CSV export, and append-only audit logging.

## Current Tier Claim

This repository claims:

* **T1 — Core submission and gallery workflow**
* **T2 — Judge assignment and judging integrity**
* **T3 — Community voting, comments, and anti-abuse controls**

The official acceptance suite verifies all seven published T1/T2 checks. T3 has no
automated check by design (per spec.md) and is documented in JUDGING.md, verified
manually against the running portal. T4 stretch capabilities are claimed and manually verified in `t4-verification.md`.

## Implementation Contributions

The implementation combines a Spring Boot backend with a React/Vite/Nginx frontend
and local JSON-backed persistence. The main engineering work delivered for this
submission includes:

* Server-side session handling and role isolation for admin, organizer, judge, and
  participant workflows, plus the public visitor surface.
* Configurable event management for dates, tracks, prizes, and organizer-defined custom
  questions.
* Team formation through invite links and a project lifecycle supporting draft
  creation, draft editing, final submission, and deadline enforcement.
* Rich project submissions covering repository/live/demo links, media, technology tags,
  track selection, and custom-question answers.
* Judge invitation and management with manual assignment, batch assignment, and
  deterministic automatic assignment.
* Automatic assignment with target review counts, track eligibility, duplicate
  judge/project prevention, assignment-load balancing, deterministic judge-id
  tie-breaking, and dry-run validation.
* Configurable weighted rubrics with backend-enforced judge/project isolation and
  protection against peer-score and participant access.
* Judge progress tracking, deterministic cross-judge normalization, organizer/admin
  results access, CSV export, and append-only audit events.
* Fixture-backed acceptance verification, backend tests, frontend production-build
  validation, and Docker-based local startup.

## Features

### T1

* Public project gallery backed by the repository fixture data, with search/filter support.
* Project detail information including track, title, summary, team, and repository link.
* Submission workflow with event validation, draft creation, draft editing, final submission, and deadline enforcement.
* Team formation through invite links.
* Event configuration for dates, tracks, prizes, and custom questions.
* Rich submission fields including repository/live/demo links, media, tech tags, track, and custom-question answers.
* Closed-event submissions are rejected.
* Fixture-backed project data is available without an external hosted service.

### T2

* Judge invitation and organizer-controlled judge assignment.
* Explicit project assignment per judge.
* Batch and deterministic algorithmic judge assignment with target review counts, track eligibility, duplicate-pair prevention, load balancing, deterministic tie-breaking, and dry-run validation.
* Configurable rubric criteria, weights, and maximum scores.
* Backend validation of judge/project assignment.
* Judge scoring restricted to the authenticated judge identity and assigned projects.
* Participants cannot access judge scoring endpoints or peer ballots.
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

## Results

The organizer can normalize and publish the latest judging results through:

```text
POST /api/results/normalize
POST /api/results/publish
GET /api/results/published
```

Normalization and publication are organizer/admin-only. The public published snapshot exposes project ID, project title, normalized average, rank, normalization version, and publication timestamp. Judge identities and raw judge scores are not exposed by the published snapshot.

Successful publication records a `RESULT_PUBLISHED` audit event.

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

The official acceptance run reports the T1/T2 checks below. T3 and T4 are additionally verified through backend tests and documented manual smoke tests in `t4-verification.md`.

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
claimed T1 T2 T3 T4; official automated checks verify T1 T2; T3 T4 are verified separately as documented
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
├── ARCHITECTURE.md
├── DATA-MODEL.md
├── JUDGING.md
├── acceptance-report.txt
├── run.py
└── spec.md
```

## Scope and Honesty

This repository intentionally documents only behavior that is implemented and verified.

The current submission claims T1, T2, T3, and T4.

The official acceptance runner verifies the published T1/T2 checks. T3 and T4 are
documented separately and were manually verified against the running implementation.

The current implementation uses local JSON-backed persistence and does not require an
external database, hosted authentication service, external API, or cloud account.
