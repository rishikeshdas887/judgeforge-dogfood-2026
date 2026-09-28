# T4 Verification

This document records reproducible runtime evidence for the T4 features.
The official DOGFOOD acceptance runner verifies the published T1/T2 checks;
T4 is additionally verified through the runtime checks recorded here.

Verification date: 2026-09-28

## T4.1 — REST API and Webhooks

Public REST API runtime checks:

- `GET /api/v1/projects` returned HTTP 200.
- The response was an array containing 41 submitted projects.
- `GET /api/v1/event` returned HTTP 200.

Webhook runtime checks:

- Organizer webhook creation returned HTTP 201.
- A real `PROJECTS_BULK_IMPORTED` audit event was triggered through
  `/api/organizer/projects/import`.
- The webhook delivery record reported:
  - `attempts: 1`
  - `status_code: 200`
  - `delivered: true`
  - `error: null`
- The temporary webhook was then deleted with HTTP 204.

The first host-network receiver attempt failed with `ConnectException`
because the Docker backend could not reach the host receiver. That temporary
test webhook was deleted. A second in-network delivery test succeeded.

## T4.2 — Certificates and Participation Records

Runtime check:

- `POST /api/organizer/certificates/projects/prj_01` returned HTTP 200.
- Participation certificates were generated for the project's participants.
- A generated certificate was retrieved from
  `GET /api/certificates/{certificateId}` with HTTP 200.
- The certificate reported `status: VALID`.

## T4.3 — Signed and Publicly Verifiable Judge Records

Runtime check:

- `POST /api/organizer/judge-records/snapshot` returned HTTP 200.
- The snapshot generated 126 judge participation records.
- Records contained:
  - SHA-256 record hashes
  - Ed25519 signatures
  - signing key IDs
  - signing timestamps
- `GET /api/v1/judge-records/{recordId}` returned HTTP 200.
- `GET /api/v1/judge-records/{recordId}/verify` returned HTTP 200 with:
  - `valid: true`
  - `algorithm: Ed25519`
- `GET /api/v1/judge-records/public-key` returned HTTP 200 and exposed
  the corresponding public verification key.

## T4.4 — Embeddable Gallery

Runtime check:

- `GET /embed/gallery.html` returned HTTP 200.
- `GET /embed/gallery.js` returned HTTP 200.
- The gallery loads projects from `/api/v1/projects`.
- The embed script creates an iframe pointing to `gallery.html`.
- The public API returned the same 41 submitted projects used by the gallery.

The embed assets are served by the portal itself and do not require a
runtime CDN.

## T4.5 — Bulk Project Import/Export

Runtime round-trip:

- Organizer CSV export returned HTTP 200.
- Export contained 41 project rows.
- The exported CSV was imported through
  `POST /api/organizer/projects/import`.
- Import returned HTTP 200 with `imported: 41`.
- A subsequent public project query still returned 41 projects.
- No persistent project-data diff remained after the test.

## Repository State After Verification

Temporary certificate, judge-record, webhook, and audit-event test mutations
were not retained in the submission repository.

The pairwise comparison fixture remains empty in the repository so that
comparisons are generated during actual judge use.
