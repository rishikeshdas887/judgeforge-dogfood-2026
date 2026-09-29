# JudgeForge — Hack The Dog

JudgeForge is a  self-hostable hackathon judging portal with a public project gallery, organizer-controlled judge assignment, configurable weighted scoring, role-isolated judge ballots, deterministic cross-judge normalization, organizer judging progress, CSV export, and append-only audit logging.

Built for DOGFOOD 2026 (Hackathon Raptors).

## Current Tier Claim

This repository claims:

* **T1 — Core: event creation, team formation, submissions and public gallery**
* **T2 — Judge assignment and judging integrity**

The official DOGFOOD acceptance suite verifies the published T1/T2 HTTP checks, and all seven pass.

T3 (community voting), T4 (REST/webhooks, certificates, signed records, embed, bulk tools) and all four optional bonuses are also implemented but are **not claimed**, because the official checker does not verify them. Their evidence is in `docs/T3-VERIFICATION.md`, `docs/T4-VERIFICATION.md` and the bonus files listed below.

## Submission Status

| Area                                               | Status                        | Evidence / surface                                                                 |
| -------------------------------------------------- | ----------------------------- | ---------------------------------------------------------------------------------- |
| T1 — Core submission and gallery                   | **Implemented**               | Event creation + team formation + submissions + public gallery + acceptance report |
| T2 — Judge assignment and judging integrity        | **Implemented**               | Judge/Organizer UI + acceptance report + `JUDGING.md`                              |
| T3 — Community voting and anti-abuse               | **Implemented (not claimed)** | Public Community Voting UI + `docs/T3-VERIFICATION.md`                             |
| T4 — REST/webhooks/certificates/records/embed/bulk | **Implemented (not claimed)** | Local API surface + `docs/T4-VERIFICATION.md`                                      |
| Bonus — Normalization proof (+5)                   | **Implemented**               | `normalization-proof.md` + Organizer normalization UI                              |
| Bonus — Pairwise judging (+5)                      | **Implemented**               | Judge Pairwise Mode UI + Bradley-Terry results endpoint                            |
| Bonus — Threat model (+3)                          | **Implemented**               | `THREAT-MODEL.md`                                                                  |
| Bonus — API First (+3)                             | **Implemented**               | `openapi.yaml` + `API-FIRST-COVERAGE.md` + `verify-api-first.py`                   |

> Bonus points are separate from the main tier score and are used as tie-break / Best Judging Engine criteria under the DOGFOOD rules.

### What an evaluator can see immediately

1. `docker compose up` starts the seeded portal. After the Docker images have been prepared, the running application requires no external network services.
2. Public visitors can browse the fixture gallery and community-voting surface.
3. The participant role exposes team formation, invite acceptance, draft/edit/submit behavior, and deadline enforcement.
4. Judge A/B expose isolated scoring plus the optional Pairwise Mode.
5. Organizer/Admin exposes event configuration, judge assignment, rubric, progress, normalization, publication, CSV export, audit, and community-voting administration.
6. T4 integration capabilities are directly available through the documented local REST API and are backed by reproducible runtime verification.

The acceptance runner remains intentionally limited to the published seven T1/T2 checks; the additional T3/T4 and bonus evidence is documented separately rather than being presented as part of the official acceptance runner.

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
* Optional Pairwise Mode with judge-isolated comparisons, persistent comparison storage, and Bradley-Terry estimation.
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

## Bonus — Pairwise Mode

Judges can optionally switch from weighted-rubric scoring to pairwise comparison mode.

Each comparison:

* uses only projects assigned to the authenticated judge;
* is enforced server-side by the backend;
* is persisted in `backend/data/pairwise-comparisons.json`;
* records an append-only audit event;
* contributes to a Bradley-Terry estimator exposed through the organizer-only results endpoint.

Pairwise judging starts with an empty comparison set in the repository and builds comparisons during judging.

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

Clone the repository and start the seeded portal:

```bash
git clone https://github.com/rishikeshdas887/judgeforge-dogfood-2026.git
cd judgeforge-dogfood-2026
docker compose up
```

The application is available at:

```text
http://localhost:8080
```

`docker compose up` builds the local backend/frontend images when they are not already available and starts the complete portal.

The running application uses only local containers and local JSON-backed data. No hosted database, hosted authentication service, external API, cloud account, or runtime internet connection is required.

After the Docker images have been prepared, the portal can be started offline using the same command:

```bash
docker compose up
```

Stop the application with:

```bash
docker compose down
```

To explicitly rebuild the images after changing application code or Dockerfiles:

```bash
docker compose up --build
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
GET /api/judge/pairwise/next
GET /api/judge/pairwise/progress
POST /api/judge/pairwise/comparisons
GET /api/judge/pairwise/results
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
community-voting.json
judge-invitations.json
normalization-results.json
pairwise-comparisons.json
rubric.json
```

The application is designed to run with these local files and does not require a remote database for the current submission.

## Acceptance Verification

The repository includes:

```text
acceptance-report.txt
```

The official acceptance run reports the published T1/T2 checks below. T3 and T4 are additionally verified through reproducible runtime smoke tests documented in `docs/T3-VERIFICATION.md` and `docs/T4-VERIFICATION.md`.

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

## Bonus Evidence

The repository includes dedicated evidence for all four optional bonus challenges:

* **Normalization Proof (+5)** — fixture-backed raw vs normalized scores, methodology, reproducibility fingerprint, and rank movement in `normalization-proof.md`. The Organizer UI exposes the normalization action and result summary.
* **Pairwise Judging (+5)** — judge-side Pairwise Mode with persistent comparisons and a Bradley-Terry estimator. Evidence is implemented in the Judge UI and backend controller, with the API documented in `openapi.yaml`.
* **Threat Model (+3)** — `THREAT-MODEL.md` documents Sybil voting, ballot stuffing, scraping, judge collusion, deadline gaming, mitigations, and residual risks.
* **API First (+3)** — `openapi.yaml`, `API-FIRST-COVERAGE.md`, and `verify-api-first.py` cover the UI business-action API surface plus the T4 integration API.

The four bonuses are separate from the main tier score and are intended as tie-break / Best Judging Engine criteria under the DOGFOOD rules.

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
├── docs/
│   ├── T3-VERIFICATION.md
│   └── T4-VERIFICATION.md
├── fixtures.json
├── docker-compose.yml
├── .dogfood.toml
├── README.md
├── ARCHITECTURE.md
├── DATA-MODEL.md
├── JUDGING.md
├── THREAT-MODEL.md
├── normalization-proof.md
├── openapi.yaml
├── API-FIRST-COVERAGE.md
├── verify-api-first.py
├── LICENSE
├── acceptance-report.txt
├── run.py
└── spec.md
```

## Scope and Honesty

This repository intentionally documents only behavior that is implemented and verified.

The submission claims T1 and T2, which the official checker verifies. T3, T4 and the four bonus challenges were built beyond the claim and are verified separately in `docs/T3-VERIFICATION.md`and `docs/T4-VERIFICATION.md`.

The current implementation uses local JSON-backed persistence and does not require an
external database, hosted authentication service, external API, or cloud account.

**Note:** Clone into a directory Docker has permission to bind-mount (e.g., your home directory). Docker Desktop's default file-sharing settings may block bind mounts from /tmp or other restricted paths.
