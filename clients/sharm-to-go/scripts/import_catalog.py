#!/usr/bin/env python3
"""Import the merged Sharm To Go catalog (import-manifest.json) through the
real staff API: create -> submit-for-review -> approve. Never publishes —
every service in the manifest carries placeholder media, which intentionally
blocks publish() until the owner supplies real, rights-cleared photos.

Idempotent: re-running skips any internalRef already recorded in the state
file, so partial runs (network error, server restart) can resume safely.

Usage:
    STG_API_BASE=http://localhost:8081 \
    STG_ADMIN_EMAIL=mohamed@sharmtogo.local \
    STG_ADMIN_PASSWORD='...' \
    python3 import_catalog.py [--state-file PATH] [--manifest PATH] [--dry-run]

Credentials are read from the environment only — never pass them on the
command line (shell history) and never hardcode them here.
"""
import argparse
import json
import os
import sys
from pathlib import Path

import requests

DEFAULT_MANIFEST = Path(__file__).resolve().parent.parent / "content-research" / "import-manifest.json"


def die(message):
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)


def login(base_url, email, password):
    resp = requests.post(f"{base_url}/api/v1/identity/login", json={"email": email, "password": password})
    if resp.status_code != 200:
        die(f"login failed: HTTP {resp.status_code} {resp.text}")
    return resp.json()["token"]


def auth_headers(token):
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


def ensure_categories(base_url, token, categories):
    headers = auth_headers(token)
    resp = requests.get(f"{base_url}/api/v1/travel-marketplace/categories", headers=headers, params={"size": 200})
    if resp.status_code != 200:
        die(f"listing categories failed: HTTP {resp.status_code} {resp.text}")
    existing = {c["code"]: c["id"] for c in resp.json()}

    code_to_id = {}
    for cat in categories:
        if cat["code"] in existing:
            code_to_id[cat["code"]] = existing[cat["code"]]
            continue
        body = {
            "code": cat["code"],
            "name": {"en": cat["name_en"], "ar": cat["name_ar"]},
            "description": None,
            "displayOrder": cat["order"],
            "expectedVersion": None,
        }
        resp = requests.post(f"{base_url}/api/v1/travel-marketplace/categories", headers=headers, json=body)
        if resp.status_code != 201:
            die(f"creating category {cat['code']} failed: HTTP {resp.status_code} {resp.text}")
        created = resp.json()
        code_to_id[cat["code"]] = created["id"]
        print(f"  created category {cat['code']} -> {created['id']}")
    return code_to_id


def create_service(base_url, token, category_id, svc):
    headers = auth_headers(token)
    body = {
        "categoryId": category_id,
        "name": svc["name"],
        "description": svc["description"],
        "fulfilmentModel": svc["fulfilmentModel"],
        "providerId": None,
        "confirmationType": svc["confirmationType"],
        "cancellationPolicy": svc["cancellationPolicy"],
        "pickupInfo": svc["pickupInfo"],
        "inclusions": svc["inclusions"],
        "exclusions": svc["exclusions"],
        "options": [{**o, "id": None} for o in svc["options"]],
        "media": [{**m, "id": None} for m in svc["media"]],
        "expectedVersion": None,
    }
    resp = requests.post(f"{base_url}/api/v1/travel-marketplace/services", headers=headers, json=body)
    if resp.status_code != 201:
        die(f"creating service {svc['internalRef']} failed: HTTP {resp.status_code} {resp.text}")
    return resp.json()


def transition(base_url, token, service_id, action):
    headers = auth_headers(token)
    resp = requests.post(f"{base_url}/api/v1/travel-marketplace/services/{service_id}/{action}", headers=headers)
    if resp.status_code != 200:
        die(f"{action} on service {service_id} failed: HTTP {resp.status_code} {resp.text}")
    return resp.json()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default=str(DEFAULT_MANIFEST))
    parser.add_argument("--state-file", default=str(Path.home() / ".sharm-to-go-import-state.json"))
    parser.add_argument("--dry-run", action="store_true", help="Print what would happen, make no API calls that write data.")
    args = parser.parse_args()

    base_url = os.environ.get("STG_API_BASE")
    email = os.environ.get("STG_ADMIN_EMAIL")
    password = os.environ.get("STG_ADMIN_PASSWORD")
    if not base_url or not email or not password:
        die("set STG_API_BASE, STG_ADMIN_EMAIL, STG_ADMIN_PASSWORD in the environment")

    manifest = json.loads(Path(args.manifest).read_text())

    state_path = Path(args.state_file)
    state = json.loads(state_path.read_text()) if state_path.exists() else {}

    if args.dry_run:
        pending = [s["internalRef"] for s in manifest["services"] if s["internalRef"] not in state]
        print(f"Would create/approve {len(pending)} of {len(manifest['services'])} services (state: {state_path}):")
        for ref in pending:
            print(f"  - {ref}")
        return

    token = login(base_url, email, password)
    print(f"Authenticated against {base_url}")

    code_to_id = ensure_categories(base_url, token, manifest["categories"])
    print(f"Categories ready: {len(code_to_id)}")

    created, skipped, failed = 0, 0, 0
    for svc in manifest["services"]:
        ref = svc["internalRef"]
        if ref in state:
            skipped += 1
            continue
        category_id = code_to_id[svc["categoryCode"]]
        try:
            response = create_service(base_url, token, category_id, svc)
            service_id = response["id"]
            transition(base_url, token, service_id, "submit-for-review")
            transition(base_url, token, service_id, "approve")
            state[ref] = {"serviceId": service_id, "status": "APPROVED"}
            state_path.write_text(json.dumps(state, indent=2))
            created += 1
            print(f"  approved {ref} -> {service_id}")
        except SystemExit:
            failed += 1
            print(f"  FAILED {ref} — see error above, continuing with the rest")
            continue

    print()
    print(f"Done. created={created} skipped(already done)={skipped} failed={failed} total={len(manifest['services'])}")
    print("All services stop at APPROVED — none are published, since every one still carries placeholder media.")
    print(f"State recorded at {state_path}")


if __name__ == "__main__":
    main()
