# DOGFOOD Threat Model

## Scope

This threat model covers the self-hosted hackathon platform submission, public gallery, community voting, judging, and result-publication surfaces.

It documents realistic abuse cases, implemented mitigations, detection and audit mechanisms, and residual risks. Controls described here are limited to behavior that exists in the current implementation.

## Assets and Trust Boundaries

### Assets

- Project submissions and repository/demo links.
- Team membership and project ownership.
- Judge assignments and private ballots.
- Rubric configuration and normalized results.
- Community votes and comments.
- Audit events and request correlation IDs.
- Certificates and signed judge records.

### Trust boundaries

1. Public browser -> Nginx -> backend.
2. Participant session -> participant submission operations.
3. Judge session -> assigned-project judging operations.
4. Organizer/admin session -> configuration, assignment, normalization, publication, export, and audit operations.
5. Backend -> filesystem-backed JSON persistence.

The backend is the authoritative authorization boundary; frontend visibility is not treated as a security control.

## Threats

### 1. Sybil voting

Threat: A person creates multiple voter identities to increase community-vote influence.

Implemented mitigations:
- Voting identity is resolved server-side rather than trusted from a client-supplied identity.
- Email-gated voting can restrict participation to an organizer-defined allow-list.
- Authenticated voting can require an authenticated account or session.
- Duplicate vote detection prevents more than one vote per resolved identity.
- Voting activity is audited.

Residual risk: Open-link voting cannot prove that multiple identities belong to different real people. Stronger identity verification would be required for high-assurance anti-Sybil protection.

### 2. Ballot stuffing and duplicate voting

Threat: An attacker repeatedly submits votes or comments for the same event or project.

Implemented mitigations:
- One vote per resolved voter identity is enforced server-side.
- Repeat votes are rejected before persistence.
- Comment duplicates are rejected.
- Comment submission is rate-limited; the documented test shows the seventh request inside the window receives HTTP 429.
- Duplicate and rate-limit events are written to the audit trail.

Residual risk: Rate limiting is identity-based, so coordinated identities are not eliminated by itself.

### 3. Submission scraping

Threat: Automated clients crawl the public gallery or extract project metadata at scale.

Implemented mitigations:
- Public submission data is intentionally exposed only through the public gallery and API surface.
- The backend controls which submitted projects belong to the current event.
- The public project API exposes submitted-project records rather than private participant or judge data.

Residual risk: Public data that is intentionally published can still be scraped. The current implementation does not claim bot detection or anti-scraping guarantees.

### 4. Judge collusion

Threat: Judges coordinate scores, inspect another judge ballot, or manipulate results together.

Implemented mitigations:
- Judge identity is resolved server-side.
- Judges can only score projects assigned to their authenticated judge identity.
- Cross-judge ballot access is rejected and audited.
- Raw judge scores are not exposed through the public published-results snapshot.
- Organizer/admin access is required for normalization and publication.
- Append-only audit events provide an investigation trail.

Residual risk: The platform cannot cryptographically prevent real-world collusion between trusted human judges. Monitoring and post-event review remain necessary.

### 5. Deadline gaming and late submission

Threat: Participants attempt to create or modify submissions after the event closes.

Implemented mitigations:
- Submission operations enforce event timing server-side.
- Closed-event submission attempts are rejected.
- Draft, edit, and submit behavior is controlled by backend validation rather than frontend timers.
- The official acceptance suite verifies that closed events refuse submissions.

Residual risk: A compromised organizer or admin account remains an administrative trust boundary.

## Additional Security Controls

### Role isolation

Organizer, judge, participant, and public operations are enforced by the backend.

### Auditability

Security-sensitive judging and voting operations create audit records containing actor, action, target, timestamp, before and after state, reason, and request ID.

### Request correlation

The X-Request-Id header is preserved or generated so security events can be correlated with individual HTTP requests.

### Self-hosted boundary

The deployment does not require a hosted authentication service, hosted database, or external runtime API.

## Verification

Relevant controls are exercised through:
- The published seven-check acceptance suite for T1 and T2 behavior.
- Manual T3 verification documented in JUDGING.md.
- T4 verification documented in t4-verification.md.
- Backend automated tests.
