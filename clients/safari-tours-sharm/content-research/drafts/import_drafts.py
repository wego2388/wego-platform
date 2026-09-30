"""Imports tour-content-drafts.en.json into a running Safari backend as DRAFT
content and facts, and (only with --publish) publishes them.

Usage:
  python3 import_drafts.py --base-url https://api.example --email admin@... [--publish]
The password is read from the STS_ADMIN_PASSWORD environment variable, never
from the command line. Publishing sends the revision just read back, so it
publishes exactly what was imported. Never run --publish before the owner has
approved the drafts (status must be OWNER_APPROVED in the JSON file).
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def call(base, method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base.rstrip("/") + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            text = resp.read().decode()
            return resp.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as err:
        return err.code, err.read().decode()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--email", required=True)
    parser.add_argument("--publish", action="store_true")
    args = parser.parse_args()

    drafts = json.load(open(os.path.join(os.path.dirname(__file__), "tour-content-drafts.en.json")))
    if args.publish and drafts.get("status") != "OWNER_APPROVED":
        sys.exit("Refusing to publish: drafts are not marked OWNER_APPROVED.")
    password = os.environ.get("STS_ADMIN_PASSWORD") or sys.exit("Set STS_ADMIN_PASSWORD.")

    status, login = call(args.base_url, "POST", "/api/v1/identity/login", body={"email": args.email, "password": password})
    if status != 200:
        sys.exit(f"Login failed: {status}")
    token = login["token"]

    failures = 0
    for tour in drafts["tours"]:
        status, found = call(args.base_url, "GET", f"/api/v1/tours-operator/tours/by-slug?slug={tour['slug']}")
        if status != 200:
            print(f"SKIP {tour['slug']}: not found or inactive ({status})")
            failures += 1
            continue
        tour_id = found["id"]
        steps = [("PUT", f"/api/v1/tours-operator/staff/tours/{tour_id}/content/{drafts['locale']}", tour["content"])]
        if tour.get("facts"):
            steps.append(("PUT", f"/api/v1/tours-operator/staff/tours/{tour_id}/facts", tour["facts"]))
        for method, path, body in steps:
            status, result = call(args.base_url, method, path, token, body)
            if status != 204:
                print(f"FAIL {tour['slug']} {path}: {status} {result}")
                failures += 1
        if args.publish:
            _, staff = call(args.base_url, "GET", f"/api/v1/tours-operator/staff/tours/{tour_id}/content", token)
            drafts_for_locale = [d for d in staff["content"].get(drafts["locale"], []) if d["stage"] == "DRAFT"]
            publishes = [(f"content/{drafts['locale']}/publish", drafts_for_locale[0]["revision"])] if drafts_for_locale else []
            facts_draft = [f for f in staff["facts"] if f["stage"] == "DRAFT"]
            if facts_draft:
                publishes.append(("facts/publish", facts_draft[0]["revision"]))
            for action, revision in publishes:
                status, result = call(args.base_url, "POST", f"/api/v1/tours-operator/staff/tours/{tour_id}/{action}", token, {"revision": revision})
                if status != 204:
                    print(f"FAIL publish {tour['slug']} {action}: {status} {result}")
                    failures += 1
        print(f"OK   {tour['slug']}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
