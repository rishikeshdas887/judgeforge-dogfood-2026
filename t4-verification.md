# T4 Verification

T4 is claimed in `.dogfood.toml`.

## T4-1 REST API and OpenAPI

Verified locally:

- `GET /api/v1/event` returned the event JSON.
- `GET /api/v1/projects` returned 41 submitted fixture projects.
- `GET /api/v1/projects/{projectId}` was implemented for submitted projects.
- `openapi.yaml` was added.

## T4-2 Webhooks

Verified locally with a local HTTP receiver:

- organizer webhook registration returned HTTP 201.
- event filtering delivered `EVENT_CONFIG_UPDATED`.
- signed delivery included `X-DogFood-Signature`.
- receiver returned HTTP 200.
- delivery records were persisted with `delivered=true` and `attempts=1`.

Webhook runtime state is ignored by Git.

## T4-3 Participation certificates

Verified locally:

- submitted fixture project `prj_01` generated 3 participation certificates.
- generated records had `status=VALID`.
- public verification through `/api/certificates/{certificateId}` returned the record.

Certificate runtime state is ignored by Git.

## T4-4 Verifiable judge records

Verified locally:

- Ed25519-signed judge records were generated.
- original record verification returned `valid=true`.
- after modifying the stored record, verification returned `valid=false`.
- public verification metadata exposed the key id and algorithm.

Private signing-key runtime state is ignored by Git.

## T4-5 Embeddable gallery

Verified locally:

- `frontend/public/embed/gallery.html`
- `frontend/public/embed/gallery.js`
- `frontend/public/embed/README.txt`

Frontend production build passed.

The embed reads submitted projects from `/api/v1/projects`.
The public projects API returned 41 fixture projects during verification.

## T4-6 Bulk project import/export

Verified locally:

- CSV export contained 41 project rows.
- exporting and re-importing the CSV imported 41 projects.
- duplicate project IDs were rejected with HTTP 400.
- after the rejected import, the project count remained 41.

## Global verification

Backend Maven tests passed.
Frontend production build passed.

All T4 work is currently isolated on the local `t4-local` branch.
