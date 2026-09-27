#!/usr/bin/env python3
"""Verify that the published OpenAPI contract covers the UI/API action surface."""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent
OPENAPI = ROOT / "openapi.yaml"

EXPECTED = [
    ("GET", "/api/auth/me"),
    ("POST", "/api/auth/login"),
    ("POST", "/api/auth/logout"),
    ("GET", "/projects"),
    ("GET", "/api/results/published"),
    ("GET", "/api/participant/event"),
    ("GET", "/api/teams"),
    ("POST", "/api/teams"),
    ("POST", "/api/teams/{teamId}/invites"),
    ("POST", "/api/teams/invites/{token}/accept"),
    ("GET", "/api/projects/mine"),
    ("POST", "/api/projects"),
    ("PUT", "/api/projects/{projectId}"),
    ("POST", "/api/projects/{projectId}/submit"),
    ("GET", "/api/judge/projects"),
    ("GET", "/api/judge/rubric"),
    ("GET", "/api/judge/ballots"),
    ("PUT", "/api/judge/ballots/{projectId}"),
    ("GET", "/api/judge/scores"),
    ("GET", "/api/organizer/event"),
    ("PUT", "/api/organizer/event"),
    ("POST", "/api/organizer/events"),
    ("GET", "/api/organizer/rubric"),
    ("PUT", "/api/organizer/rubric"),
    ("GET", "/api/organizer/judges"),
    ("POST", "/api/organizer/judges/{judgeId}/invite"),
    ("PUT", "/api/organizer/judges/{judgeId}/assignments"),
    ("POST", "/api/organizer/judges/assignments/batch"),
    ("POST", "/api/organizer/judges/assignments/auto"),
    ("GET", "/api/organizer/judging-progress"),
    ("GET", "/api/results"),
    ("POST", "/api/results/normalize"),
    ("POST", "/api/results/publish"),
    ("GET", "/api/export.csv"),
    ("GET", "/api/community/voting/config"),
    ("GET", "/api/community/voting/ballot"),
    ("POST", "/api/community/voting/vote"),
    ("GET", "/api/community/comments"),
    ("POST", "/api/community/comments"),
    ("GET", "/api/community/voting/results"),
    ("GET", "/api/organizer/community-voting"),
    ("PUT", "/api/organizer/community-voting"),
    ("GET", "/api/audit"),
    ("GET", "/api/v1/event"),
    ("GET", "/api/v1/projects"),
    ("GET", "/api/v1/projects/{projectId}"),
    ("POST", "/api/organizer/webhooks"),
    ("GET", "/api/organizer/webhooks"),
    ("DELETE", "/api/organizer/webhooks/{webhookId}"),
    ("POST", "/api/organizer/certificates/projects/{projectId}"),
    ("GET", "/api/organizer/certificates/projects/{projectId}"),
    ("GET", "/api/certificates/{certificateId}"),
    ("GET", "/api/participant/participation-records"),
    ("POST", "/api/organizer/judge-records/snapshot"),
    ("GET", "/api/organizer/judge-records"),
    ("GET", "/api/v1/judge-records/{recordId}"),
    ("GET", "/api/v1/judge-records/{recordId}/verify"),
    ("GET", "/api/v1/judge-records/public-key"),
    ("GET", "/api/organizer/projects/export.csv"),
    ("POST", "/api/organizer/projects/import"),
]


def find_operation(document: str, method: str, path: str) -> bool:
    # YAML path keys may be quoted or unquoted. Restrict the search to the
    # named path block so a method mentioned elsewhere does not count.
    path_pattern = re.compile(
        rf"(?m)^\s*(?:['\"]{re.escape(path)}['\"]|{re.escape(path)}):\s*$"
    )
    match = path_pattern.search(document)
    if not match:
        return False
    next_path = re.search(r"(?m)^\s{2}(?:['\"]/|/)[^\n]*:\s*$", document[match.end():])
    block = document[match.end(): match.end() + (next_path.start() if next_path else len(document))]
    return bool(re.search(rf"(?m)^\s{{4}}{method.lower()}:\s*$", block))


def main() -> int:
    if not OPENAPI.exists():
        print(f"FAIL: {OPENAPI} not found")
        return 1
    doc = OPENAPI.read_text(encoding="utf-8")

    missing = [(m, p) for m, p in EXPECTED if not find_operation(doc, m, p)]
    print(f"OpenAPI file: {OPENAPI}")
    print(f"Expected operations: {len(EXPECTED)}")
    print(f"Missing operations: {len(missing)}")

    if missing:
        for method, path in missing:
            print(f"  MISSING  {method:6} {path}")
        return 1

    # Best-effort structural parse when PyYAML is available.
    try:
        import yaml  # type: ignore
    except Exception:
        print("PASS: all method/path pairs are documented (PyYAML not installed; text verification only)")
        return 0

    try:
        parsed = yaml.safe_load(doc)
    except Exception as exc:
        print(f"FAIL: OpenAPI YAML does not parse: {exc}")
        return 1

    if parsed.get("openapi") != "3.0.3":
        print("FAIL: expected OpenAPI 3.0.3")
        return 1
    if not isinstance(parsed.get("paths"), dict):
        print("FAIL: OpenAPI paths object is missing")
        return 1

    print("PASS: all UI/T4 method/path pairs are documented and the YAML parses")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
