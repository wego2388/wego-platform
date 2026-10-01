"""Imports tour-content-drafts.<locale>.json into a running Safari backend as
DRAFT content (and, from the English file, facts), and (only with --publish)
publishes them.

Usage:
  python3 import_drafts.py --base-url https://api.example --email admin@... [--locale en|ar|ru|it|all] [--publish]
The password is read from the STS_ADMIN_PASSWORD environment variable, never
from the command line. Publishing sends the revision just read back, so it
publishes exactly what was imported. Never run --publish before the owner has
approved the drafts: the English file must be OWNER_APPROVED and a
translation APPROVED (both are set only after the owner's sign-off).
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
    parser.add_argument("--locale", default="en", choices=["en", "ar", "ru", "it", "all"])
    parser.add_argument("--publish", action="store_true")
    args = parser.parse_args()

    locales = ["en", "ar", "ru", "it"] if args.locale == "all" else [args.locale]
    here = os.path.dirname(__file__)
    files = []
    for locale in locales:
        drafts = json.load(open(os.path.join(here, f"tour-content-drafts.{locale}.json")))
        allowed = "OWNER_APPROVED" if locale == "en" else "APPROVED"
        if args.publish and drafts.get("status") != allowed:
            sys.exit(f"Refusing to publish {locale}: status is {drafts.get('status')}, expected {allowed}.")
        files.append(drafts)
    password = os.environ.get("STS_ADMIN_PASSWORD") or sys.exit("Set STS_ADMIN_PASSWORD.")

    status, login = call(args.base_url, "POST", "/api/v1/identity/login", body={"email": args.email, "password": password})
    if status != 200:
        sys.exit(f"Login failed: {status}")
    token = login["token"]

    failures = 0
    for drafts in files:
        failures += import_locale(args, token, drafts)
    sys.exit(1 if failures else 0)


def import_locale(args, token, drafts):
    """Imports one locale file; returns the number of failures."""
    failures = 0
    for tour in drafts["tours"]:
        status, found = call(args.base_url, "GET", f"/api/v1/tours-operator/tours/by-slug?slug={tour['slug']}")
        if status != 200:
            print(f"SKIP {tour['slug']}: not found or inactive ({status})")
            failures += 1
            continue
        tour_id = found["id"]
        steps = [("PUT", f"/api/v1/tours-operator/staff/tours/{tour_id}/content/{drafts['locale']}", tour["content"])]
        # Facts are locale-independent and owned by the English file.
        if drafts["locale"] == "en" and tour.get("facts"):
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
        print(f"OK   {drafts['locale']} {tour['slug']}")
    return failures


if __name__ == "__main__":
    main()
