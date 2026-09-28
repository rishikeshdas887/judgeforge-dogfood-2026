# T3 Verification — Community Voting

## Scope

This document records reproducible verification of the DOGFOOD T3 Community
Voting implementation. The official DOGFOOD acceptance runner verifies the
published T1/T2 HTTP checks; this document provides additional runtime evidence
for the T3 implementation.

## 1. Community Voting Access

Endpoint:

GET /api/community/voting/config

Observed:

- enabled: true
- access: OPEN_LINK
- status: OPEN
- results_public: false

PASS — community voting is enabled and configurable.

## 2. Backend-Generated Ballot

Endpoint:

GET /api/community/voting/ballot

Observed:

- 41 submitted projects returned.
- Projects are supplied by the backend.
- `already_voted` is returned for the current voter identity.

PASS — ballot data comes from the backend.

## 3. Randomized Ballot Ordering

Two independent anonymous voter cookie identities were used.

Voter A first ten projects:

`prj_19, prj_05, prj_39, prj_09, prj_13, prj_33, prj_08, prj_35, prj_11, prj_27`

Voter B first ten projects:

`prj_11, prj_10, prj_08, prj_04, prj_21, prj_03, prj_12, prj_06, prj_07, prj_34`

Observed:

- `same_full_order = False`
- `same_first_project = False`

PASS — ballot ordering differs between voter identities.

The backend performs the shuffle before returning the ballot.

## 4. Vote Persistence

A vote for project `prj_19` was submitted.

Observed:

HTTP 201

Response:

`{"status":"recorded","project":"prj_19"}`

PASS — a community vote is persisted successfully.

## 5. Duplicate Vote Prevention

The same voter identity attempted to vote again.

Observed:

HTTP 409

Response:

`This voter has already voted`

PASS — duplicate voting is rejected by the backend.

## 6. Rate Limiting

Repeated vote requests were made from the same voter/IP.

Observed:

HTTP 429

Response:

`Too many requests. Please try again in a minute.`

Backend configuration:

- Vote rate limit: 10 requests/minute
- Comment rate limit: 6 requests/minute

PASS — backend rate limiting is enforced.

## 7. Comments

A temporary comment was submitted for the test project.

Observed:

HTTP 201

The comment was then read back through:

GET /api/community/comments?project=...

Observed:

`comment_found = True`

PASS — comments can be created and read through the backend.

## 8. Audit Trail

The verification run observed:

- `COMMUNITY_VOTE_SUBMITTED`
- `COMMUNITY_VOTE_DUPLICATE`
- `COMMUNITY_RATE_LIMITED`
- community comment audit event

PASS — community voting security-sensitive actions are recorded in
the append-only audit trail.

## 9. Hidden Results During Voting

While voting status was OPEN:

GET /api/community/voting/results

Observed:

HTTP 403

Response:

`Community voting results are hidden while voting is open`

PASS — results are hidden by backend state/authorization checks rather
than only being hidden in the frontend.

## T3 Summary

| Capability | Result |
|---|---|
| Configurable community access | PASS |
| Backend ballot | PASS |
| Randomized ballot ordering | PASS |
| Vote persistence | PASS |
| Duplicate vote prevention | PASS |
| Rate limiting | PASS |
| Comments | PASS |
| Audit trail | PASS |
| Results hidden during voting | PASS |

All temporary verification data was restored after testing.
