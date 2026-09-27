# DOGFOOD Architecture

## 1. System Overview

DOGFOOD is implemented as a self-hostable two-container application:

```text
Browser
   |
   v
+-------------------------+
| Frontend                |
| React + Vite + Nginx    |
| Port 8080               |
+------------+------------+
             |
             | HTTP / API proxy
             v
+-------------------------+
| Backend                 |
| Spring Boot             |
| Java                    |
+------------+------------+
             |
             v
+-------------------------+
| Local JSON state        |
| backend/data/            |
+-------------------------+
```

The current implementation does not require an external database, hosted API,
or third-party runtime service.

## 2. Container Architecture

### Frontend

The frontend is a React application built with Vite.

The production frontend is served by Nginx.

Its responsibilities are:

* public project gallery
* authentication/session interaction
* judge-facing UI
* organizer judging controls
* rubric configuration UI
* judging progress display

The frontend communicates with the backend through HTTP requests.

### Backend

The backend is a Spring Boot application.

Its responsibilities are:

* authentication/session resolution
* project and submission endpoints
* judge assignment
* rubric configuration
* ballot validation and persistence
* judge access isolation
* judging progress
* normalization
* CSV export
* audit logging

The backend is the security boundary for judging operations.

## 3. Request Flow

A typical browser request follows this path:

```text
Browser
   |
   v
Nginx / Frontend container
   |
   v
Spring Boot backend
   |
   +--> Controller
   |
   +--> Authentication / authorization
   |
   +--> Service / store
   |
   v
backend/data/*.json
```

For example, a judge ballot update follows:

```text
PUT /api/judge/ballots/{projectId}
        |
        v
JudgingController
        |
        v
Resolve authenticated session
        |
        v
Verify judge role
        |
        v
Verify judge/project assignment
        |
        +---- denied ---> 403 + BALLOT_ACCESS_DENIED audit event
        |
        v
Validate rubric criteria and score ranges
        |
        v
BallotStore
        |
        v
ballots.json
        |
        v
BALLOT_SUBMITTED / BALLOT_EDITED audit event
```

## 4. Backend Components

The backend uses controllers, security helpers, and JSON-backed stores.

### Controllers

```text
controller/
├── AuditController.java
├── AuthController.java
├── JudgingConfigController.java
├── JudgingController.java
├── OrganizerJudgesController.java
├── OrganizerJudgingController.java
├── PortalController.java
└── ResultsController.java
```

### Security

```text
security/
├── AuthService.java
└── RequestIdFilter.java
```

`AuthService` resolves the current local demo session and role.

`RequestIdFilter` accepts an incoming `X-Request-Id` header or generates a
request ID and makes it available to the request lifecycle.

### Stores

```text
service/
├── AssignmentStore.java
├── AuditStore.java
├── BallotStore.java
├── FixtureStore.java
├── JudgeInvitationStore.java
├── NormalizationStore.java
└── RubricStore.java
```

These stores persist application state in JSON files under `backend/data/`.

## 5. Authentication and Roles

The local demo defines the following roles:

```text
ADMIN
ORGANIZER
JUDGE_A
JUDGE_B
PARTICIPANT
```

Unauthenticated public requests provide the visitor/public surface.

The current demo authentication uses deterministic session cookies.

The backend resolves the role from the server-side session mapping rather than
trusting a role value supplied by the frontend.

Organizer-only operations include:

* rubric configuration
* judge assignment
* judging-progress access
* audit-log access
* normalization
* results access
* CSV export

Judge operations are restricted to the authenticated judge's own assignments.

Participants cannot access judge scoring operations.

## 6. Judge Assignment Flow

Judge assignment is organizer-controlled and supports both explicit batch assignment and deterministic automatic assignment.

For explicit assignment, the organizer submits a project list for a judge.

The backend validates:

1. the judge exists
2. every requested project exists
3. every requested project is permitted for the judge's allowed track

The assignment store then replaces the active project set for that judge.

Automatic assignment accepts a target number of reviews per project and:

1. respects judge track eligibility
2. avoids duplicate judge/project pairs
3. balances by current assignment load
4. uses natural judge-id order as a deterministic tie-break
5. supports dry-run validation before applying changes

Automatic assignment changes create:

```text
ALGORITHMIC_ASSIGNMENT_UPDATED
```

and explicit assignment changes create:

```text
JUDGE_ASSIGNED
```

audit records containing before/after assignment state.

## 7. Rubric and Scoring Flow

The organizer configures the active rubric.

Each criterion contains:

* id
* name
* weight
* max score

The total configured weight must equal 100%.

Judges submit scores against the active rubric.

For each criterion the backend validates that the submitted score is numeric
and within the configured range.

The weighted score is calculated as:

```text
sum((score / max_score) * (weight / 100)) * 5
```

The active rubric is therefore the source of truth for score weighting.

## 8. Judge Isolation

Judge identity is obtained from the authenticated backend session.

For ballot writes, the backend checks:

```text
authenticated judge
        ==
assigned judge for project
```

When the project is not assigned to that judge:

```text
HTTP 403
```

is returned.

The denied access is also written to the audit store as:

```text
BALLOT_ACCESS_DENIED
```

This protection is enforced in backend code rather than relying on frontend
visibility.

## 9. Ballot Storage

Judge ballots are stored in:

```text
backend/data/ballots.json
```

A ballot contains judge/project information, criterion scores, and feedback
fields used by the judging implementation.

Creating a new ballot produces:

```text
BALLOT_SUBMITTED
```

Editing an existing ballot produces:

```text
BALLOT_EDITED
```

The audit record captures the previous and resulting ballot state.

## 10. Normalization Architecture

Normalization is implemented in `ResultsController`.

The process is:

```text
Eligible ballots
      |
      v
Deterministic sorting
      |
      v
Group scores by judge + criterion
      |
      v
Calculate mean + population stddev
      |
      v
Sample-size fallback when < 3
      |
      v
Calculate z-scores
      |
      v
Global criterion min-max mapping
      |
      v
Clamp to configured criterion range
      |
      v
Calculate normalized weighted scores
      |
      v
Persist normalization run
      |
      v
Write NORMALIZATION_EXECUTED audit event
```

Normalization version:

```text
zscore-v1
```

The implementation uses:

```text
epsilon = 1e-6
minimum sample size = 3
```

A SHA-256 fingerprint is calculated from the rubric and eligible sorted ballot
inputs.

The normalization run stores its parameters and results.

## 11. Audit Architecture

Audit records are stored in:

```text
backend/data/audit-events.json
```

The audit store exposes append and read operations to the application.

Each event contains:

```text
id
actor
action
target
timestamp
before
after
reason
request_id
```

The application does not expose a normal audit-delete API.

The organizer can read the audit log through:

```text
GET /api/audit
```

The audit mechanism is also used for denied cross-judge access so that an
authorization failure leaves an auditable record.

## 12. Request Correlation

Every request receives a request ID.

If the client supplies:

```text
X-Request-Id
```

the backend preserves it.

Otherwise `RequestIdFilter` generates a new request identifier.

The request ID is returned in the response header and is included in audit
records created during that request.

This makes judging/security events traceable to individual HTTP requests.

## 13. Persistence Model

The current application uses filesystem-backed JSON state rather than a
relational database.

Current state files include:

```text
assignments.json
audit-events.json
ballots.json
judge-invitations.json
normalization-results.json
rubric.json
```

The Docker Compose configuration mounts:

```text
./backend/data
```

to:

```text
/app/data
```

inside the backend container.

This allows state to survive backend-container recreation while keeping the
application self-contained for the hackathon environment.

## 14. Deployment Topology

Docker Compose defines:

```text
backend
frontend
```

The backend is available to the frontend container over the internal Docker
network.

The frontend publishes port:

```text
8080
```

to the host.

The expected local entry point is:

```text
http://localhost:8080
```

No external database or hosted dependency is required by the current
implementation.

## 15. Security Boundary

The backend is the authoritative enforcement layer for:

* role checks
* judge/project assignment checks
* score validation
* organizer-only operations
* audit creation

The frontend is treated as a presentation layer and is not relied upon as the
sole security mechanism.

## 16. Current Scope

The architecture currently supports the claimed T1 and T2 functionality.

Implemented T2 capabilities include:

* organizer-controlled assignments
* configurable weighted rubric
* judge isolation
* ballot persistence
* judging progress
* deterministic normalization
* organizer CSV export
* append-only audit events

The current implementation does not claim:

* T3 community voting
* T4 stretch capabilities
* automatic judge balancing
* external database infrastructure


## T4 stretch capabilities

The local T4 implementation adds:

1. Public read-only REST API under `/api/v1`.
2. OpenAPI description in `openapi.yaml`.
3. Organizer webhook registration with signed HMAC-SHA256 delivery and retry handling.
4. Participation certificates with public verification records.
5. Ed25519-signed judge records with tamper verification.
6. Embeddable static project gallery under `/embed/`.
7. Organizer-only bulk project CSV import/export with validation and atomic rejection.

Runtime secrets, signing keys, and generated state remain outside version control.
