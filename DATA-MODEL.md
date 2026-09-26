# DOGFOOD Data Model

## 1. Overview

The current DOGFOOD implementation uses versioned JSON documents stored under:

```text
backend/data/
```

The current persisted documents are:

```text
rubric.json
assignments.json
judge-invitations.json
ballots.json
normalization-results.json
audit-events.json
```

The application does not use an external relational database in the current
implementation.

---

## 2. Rubric

File:

```text
backend/data/rubric.json
```

Root structure:

```json
{
  "version": 2,
  "criteria": []
}
```

Each criterion contains:

```json
{
  "id": "functionality",
  "name": "Functionality",
  "weight": 40,
  "max_score": 5
}
```

Fields:

| Field                  | Type    | Description                     |
| ---------------------- | ------- | ------------------------------- |
| `version`              | integer | Rubric version                  |
| `criteria`             | array   | Active scoring criteria         |
| `criteria[].id`        | string  | Stable criterion identifier     |
| `criteria[].name`      | string  | Criterion display name          |
| `criteria[].weight`    | number  | Percentage weight               |
| `criteria[].max_score` | number  | Maximum allowed criterion score |

The configured criterion weights must total 100%.

The current fixture rubric contains three criteria:

* `functionality`
* `quality`
* `innovation`

---

## 3. Judge Assignments

File:

```text
backend/data/assignments.json
```

Root structure:

```json
{
  "version": 1,
  "assignments": []
}
```

Each assignment contains:

```json
{
  "id": "asg_jdg_02_prj_01",
  "judge": "jdg_02",
  "project": "prj_01",
  "track": "trk_04",
  "status": "ACTIVE",
  "source": "fixture_track_seed"
}
```

Fields:

| Field                   | Type    | Description                      |
| ----------------------- | ------- | -------------------------------- |
| `version`               | integer | Assignment document version      |
| `assignments`           | array   | Judge/project assignment records |
| `assignments[].id`      | string  | Assignment identifier            |
| `assignments[].judge`   | string  | Judge identifier                 |
| `assignments[].project` | string  | Project identifier               |
| `assignments[].track`   | string  | Project track identifier         |
| `assignments[].status`  | string  | Assignment state                 |
| `assignments[].source`  | string  | Assignment origin                |

The current restored fixture contains 199 assignment records.

Organizer assignment operations replace the active project set for the
selected judge.

---

## 4. Judge Invitations

File:

```text
backend/data/judge-invitations.json
```

Root structure:

```json
{
  "version": 1,
  "judges": []
}
```

Each judge record contains:

```json
{
  "judge": "jdg_01",
  "name": "Tomas Varga",
  "email": "tomas.varga@example.org",
  "status": "INVITED",
  "invited_at": "2026-09-25T21:04:23.137688321Z",
  "invite_token": "..."
}
```

Fields:

| Field                   | Type    | Description              |
| ----------------------- | ------- | ------------------------ |
| `version`               | integer | Judge document version   |
| `judges`                | array   | Judge records            |
| `judges[].judge`        | string  | Judge identifier         |
| `judges[].name`         | string  | Judge display name       |
| `judges[].email`        | string  | Judge email              |
| `judges[].status`       | string  | Invitation/account state |
| `judges[].invited_at`   | string  | Invitation timestamp     |
| `judges[].invite_token` | string  | Invitation token         |

The current fixture contains 30 judge records.

---

## 5. Ballots

File:

```text
backend/data/ballots.json
```

The root is a JSON array.

Current restored fixture count:

```text
126
```

A ballot has the following structure:

```json
{
  "judge": "jdg_08",
  "project": "prj_01",
  "criteria": {
    "functionality": 2,
    "quality": 4,
    "innovation": 2
  },
  "comment": "Runs clean."
}
```

Fields:

| Field      | Type   | Description                           |
| ---------- | ------ | ------------------------------------- |
| `judge`    | string | Judge identifier                      |
| `project`  | string | Project identifier                    |
| `criteria` | object | Criterion ID to numeric score mapping |
| `comment`  | string | Judge feedback                        |

The keys inside `criteria` are defined by the active rubric.

The backend validates submitted scores against the active criterion ranges.

A ballot is associated with a judge and project pair.

---

## 6. Normalization Results

File:

```text
backend/data/normalization-results.json
```

Root structure:

```json
{
  "runs": [],
  "published": null,
  "history": []
}
```

`runs` stores each normalization execution. `published` stores the latest
publicly published result snapshot, or `null` before the first publication.
`history` retains previously published snapshots.

A published snapshot has this structure:

```json
{
  "normalization_version": "zscore-v1",
  "published_at": "<ISO-8601 timestamp>",
  "results": [
    {
      "project": "<project id>",
      "title": "<project title>",
      "normalized_average": 0.0,
      "rank": 1
    }
  ]
}
```

The published result objects contain project-level results only. Judge
identities and raw judge scores are not exposed in the published snapshot.

Each normalization run stores the parameters and generated results for one
normalization execution.

The current run structure includes:

```text
normalization_version
generated_at
eligible_ballots
minimum_sample_size
epsilon
standard_deviation
rescaling
input_fingerprint_sha256
parameters
results
```

### Run Metadata

| Field                      | Type    | Description                                 |
| -------------------------- | ------- | ------------------------------------------- |
| `normalization_version`    | string  | Normalization algorithm version             |
| `generated_at`             | string  | Run timestamp                               |
| `eligible_ballots`         | integer | Number of eligible ballots used             |
| `minimum_sample_size`      | integer | Threshold before raw-score fallback         |
| `epsilon`                  | number  | Numerical stability constant                |
| `standard_deviation`       | string  | Standard deviation method                   |
| `rescaling`                | string  | Description of z-score rescaling            |
| `input_fingerprint_sha256` | string  | SHA-256 fingerprint of normalization inputs |

### Parameters

`parameters` is grouped by judge and criterion.

For each judge/criterion pair the stored statistics include:

```json
{
  "sample_size": 6,
  "mean": 4.3333,
  "stddev": 0.7454,
  "raw_fallback": false
}
```

Fields:

| Field          | Type    | Description                             |
| -------------- | ------- | --------------------------------------- |
| `sample_size`  | integer | Eligible scores for the judge/criterion |
| `mean`         | number  | Arithmetic mean                         |
| `stddev`       | number  | Population standard deviation           |
| `raw_fallback` | boolean | Whether raw scores were retained        |

The complete `results` object contains the normalized scoring output generated
by the normalization process.

---

## 7. Audit Events

File:

```text
backend/data/audit-events.json
```

The root is a JSON array.

Each audit record has this structure:

```json
{
  "id": "aud_61a716c3-2d4c-41a9-be31-56db28946d13",
  "actor": "organizer",
  "action": "NORMALIZATION_EXECUTED",
  "target": "results:normalization",
  "timestamp": "2026-09-25T21:55:11.966264473Z",
  "before": null,
  "after": {
    "normalization_version": "zscore-v1",
    "eligible_ballots": 126,
    "input_fingerprint_sha256": "7f95...",
    "epsilon": 1e-06,
    "minimum_sample_size": 3
  },
  "reason": "normalization_run",
  "request_id": "req_..."
}
```

Fields:

| Field        | Type        | Description                         |
| ------------ | ----------- | ----------------------------------- |
| `id`         | string      | Audit event identifier              |
| `actor`      | string      | Authenticated actor                 |
| `action`     | string      | Event type                          |
| `target`     | string      | Resource affected                   |
| `timestamp`  | string      | Event timestamp                     |
| `before`     | object/null | Previous state when applicable      |
| `after`      | object/null | Resulting state when applicable     |
| `reason`     | string      | Reason recorded by the application  |
| `request_id` | string      | HTTP request correlation identifier |

Current judging-related audit actions include:

```text
BALLOT_SUBMITTED
BALLOT_EDITED
BALLOT_ACCESS_DENIED
JUDGE_ASSIGNED
NORMALIZATION_EXECUTED
```

Audit records are append-only through the application API.

---

## 8. Request IDs

HTTP requests can contain:

```text
X-Request-Id
```

If a request does not provide one, the backend generates a request ID.

The request ID is returned in the response header and stored in audit records
created during that request.

This creates a traceable relationship between an HTTP operation and its audit
event.

---

## 9. Relationships

The principal logical relationships are:

```text
Judge
  |
  +----< Assignment >---- Project
  |
  +----< Ballot >------- Project
  |
  +----< Normalization parameters
```

More explicitly:

```text
judge-invitations.json
        |
        | judge
        v
assignments.json
        |
        | judge + project
        v
ballots.json
        |
        v
normalization-results.json
```

The rubric is shared by the judging and normalization layers:

```text
rubric.json
    |
    +----> ballot validation
    |
    +----> weighted score calculation
    |
    +----> normalization
```

Audit events record important mutations and authorization failures across
these operations.

---

## 10. Data Integrity Rules

The backend enforces the following rules in the current judging path:

1. A judge must be authenticated before accessing judge operations.
2. A judge can only score projects assigned to that judge.
3. A participant cannot access judge scoring operations.
4. Submitted criterion scores must be numeric.
5. Criterion scores must remain within the configured maximum range.
6. Rubric weights must total 100% before the rubric is saved.
7. Normalization only includes eligible assigned ballots.
8. Audit records are not exposed through a normal delete operation.

---

## 11. Normalization Input Identity

Normalization uses the rubric and eligible ballots as its input state.

Eligible ballots are processed deterministically by:

```text
judge id
project id
```

The input fingerprint is a SHA-256 digest of the serialized rubric followed by
the deterministic eligible ballot inputs.

For the verified restored fixture state:

```text
Eligible ballots: 126

Fingerprint:
7f95fe88f690c08bb14cc99c5cc5f675edde59e536ed306dc0f9934a198057d6
```

This fingerprint allows a normalization run to be associated with the exact
input state from which it was produced.

---

## 12. Persistence and Versioning

Each structured store uses a JSON document with a version field where the
current implementation defines one.

Current document versions verified in the repository:

```text
rubric.json              version 2
assignments.json         version 1
judge-invitations.json   version 1
```

`ballots.json` and `audit-events.json` are currently root JSON arrays.

`normalization-results.json` currently uses a root object containing a
`runs` array.

The files are persisted under `backend/data/` and are bind-mounted into the
backend Docker container.
