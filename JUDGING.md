# DOGFOOD Judging Method

## 1. Judging Overview

DOGFOOD uses backend-enforced judge isolation with organizer-controlled judge
assignment, configurable weighted scoring, deterministic cross-judge
normalization, CSV export, and an append-only audit trail.

The implementation is intentionally deterministic and self-hostable. The
judging path does not depend on an external service.

## 2. Judge Assignment

Judge assignment is organizer-controlled.

The organizer sends an explicit list of `project_ids` for a specific judge.
Before the assignment is written, the backend validates:

- the judge exists
- every requested project exists
- every project's track is allowed for that judge

The assignment store replaces that judge's previous active assignment set
with the submitted set.

Assignment records contain:

- assignment id
- judge id
- project id
- track
- status
- source

The implementation supports both explicit organizer assignment and a
deterministic automatic assignment algorithm.

The automatic assignment endpoint accepts a target number of reviews per
project, respects each judge's allowed tracks, avoids duplicate judge/project
pairs, and selects the eligible judge with the lowest current assignment load.
Ties are resolved by natural judge-id order so repeated runs are deterministic.

The endpoint supports dry-run mode. Successful persisted automatic assignments
are recorded with an `ALGORITHMIC_ASSIGNMENT_UPDATED` audit event.

Every successful assignment update produces a `JUDGE_ASSIGNED` audit event
containing the actor, target judge, before project list, after project list,
reason, timestamp, and request id.

## 3. Weighted Scoring

The organizer-configured rubric defines criteria, weights, and maximum
scores.

For each ballot, every configured criterion must contain a numeric score
within the configured range. Invalid or out-of-range scores are rejected by
the backend.

The weighted score on the five-point judging scale is:

`sum((score / max_score) * (weight / 100)) * 5`

Scores are calculated from the active rubric configuration.

Judges can only read ballots for their own judge identity and assigned
projects.

Participants cannot access judge scoring endpoints.

## 4. Cross-Judge Isolation

Judge identity is resolved server-side from the authenticated session.

The backend checks the authenticated judge against the assignment before
allowing a ballot write.

A judge attempting to score a project outside their assignment receives
HTTP 403.

Denied cross-judge ballot access is also recorded as a
`BALLOT_ACCESS_DENIED` audit event.

This is enforced in the backend rather than relying on frontend visibility.

## 5. Ballot Auditing

The audit log records security-sensitive judging actions.

Implemented judging audit events are:

- `BALLOT_SUBMITTED`
- `BALLOT_EDITED`
- `BALLOT_ACCESS_DENIED`
- `JUDGE_ASSIGNED`
- `NORMALIZATION_EXECUTED`

Each audit event records:

- actor
- action
- target
- timestamp
- before
- after
- reason
- request id

The audit store is append-only from the application API. There is no normal
delete endpoint for audit events.

The organizer or admin can read the audit log through the organizer/admin
audit endpoint.

## 6. Normalization

Normalization version: `zscore-v1`.

The system calculates normalization separately for every judge and rubric
criterion using only eligible assigned ballots.

For a judge `j`, criterion `c`:

- mean: arithmetic mean of that judge's eligible scores
- standard deviation: population standard deviation
- epsilon: `1e-6`
- z-score:

  `z = (x - mean) / max(stddev, epsilon)`

A judge/criterion with fewer than 3 eligible scores falls back to the raw
criterion score.

For criteria with sufficient samples, z-scores are min-max rescaled across
the eligible normalized z-score population for that criterion:

`mapped = ((z - min_z) / (max_z - min_z)) * criterion.max_score`

The mapped value is clamped to the configured criterion range.

If the z-score range collapses, the raw criterion score is retained rather
than creating an artificial separation.

The normalized weighted score uses the organizer-configured rubric weights:

`sum((normalized_score / max_score) *(weight/100)) * 5`

Results expose both raw and normalized averages and their delta.

Each normalization run stores:

- normalization version
- generated timestamp
- eligible-ballot count
- epsilon
- minimum sample size
- standard-deviation method
- rescaling description
- per-judge/per-criterion sample size
- mean
- standard deviation
- fallback decision
- normalized results
- SHA-256 input fingerprint

A normalization run also creates a `NORMALIZATION_EXECUTED` audit event with
the normalization version, eligible-ballot count, fingerprint, epsilon, and
minimum sample size.

## 7. Reproducibility

Eligible ballots are filtered to valid judge/project pairs with an active
assignment.

Before normalization, eligible ballots are sorted deterministically by:

1. judge id
2. project id

The input fingerprint is calculated from the rubric plus the sorted eligible
ballot inputs.

Repeated normalization runs over the same restored input state produce the
same normalization version and input fingerprint.

The verified fixture state contains 126 eligible ballots and currently
produces:

`7f95fe88f690c08bb14cc99c5cc5f675edde59e536ed306dc0f9934a198057d6`

## 8. Export

The organizer can export judging data through the CSV export endpoint.

The export is organizer/admin-only and contains judge/project scoring information
and feedback.

## 9. Result Publication

The organizer can run normalization through the results endpoint and publish
the latest normalized results as a public snapshot.

The workflow is:

```text
POST /api/results/normalize
        ↓
POST /api/results/publish
        ↓
GET /api/results/published
```

Normalization and publication require organizer access. The published endpoint
exposes project ID, project title, normalized average, rank, normalization
version, and publication timestamp. Judge identities and raw judge scores are
not exposed by the public snapshot.

Successful publication records a `RESULT_PUBLISHED` audit event.

## 10. Community Voting (T3)

Community voting is organizer-configured with three access modes: open link,
email-gated, or authenticated. The organizer sets the voting window (open/close
timestamps) and, for email-gated access, an allow-list of eligible voter emails.

### Ballot Presentation

Each voter receives a randomized project ordering per ballot request, preventing
position bias across voters. Voter identity is resolved server-side (via cookie
or gated email) rather than trusted from client input.

### Results Visibility

Results remain hidden from the public endpoint until the voting window closes.
The results endpoint checks voting status server-side; a non-organizer request
during an open voting window is refused rather than filtered client-side.

### Duplicate Prevention

A voter identity may cast one vote per event. A repeat vote attempt from the
same resolved identity returns HTTP 409, enforced at the backend before the
vote is persisted.

### Comment Moderation and Privacy

Comments are public-readable but the response never exposes the underlying
voter hash — only display name, body, and timestamp. Duplicate comment
submission from the same identity for the same project is rejected with
HTTP 409.

### Rate Limiting

Comment submission is rate-limited to 6 requests per minute per identity.
A 7th request within the window returns HTTP 429. This was verified directly
against the running portal: 6 sequential comments returned 201, and the 7th
returned 429 with a "Too many requests" message.

### Audit Trail

Organizer changes to voting configuration produce a
`COMMUNITY_VOTING_CONFIG_UPDATED` audit event, following the same
actor/action/target/before/after/reason/request-id shape as the T2 judging
audit events described in Section 5.

### Verification Note

T3 has no automated check in `run.py` by design (per spec.md); the behaviors
above were verified manually against the running portal via direct HTTP
requests (vote submission, duplicate rejection, comment rate limiting, and
result visibility before/after the voting window).
