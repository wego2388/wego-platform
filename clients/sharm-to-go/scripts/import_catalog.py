#!/usr/bin/env python3
"""Import the Sharm To Go catalog through create -> review -> approve, never
publish. Checkpoints are local and bound to one API target. A lost create
response requires explicit reconciliation; the staff create API itself is
not idempotent. Keep the state file and use only one importer per target.

STG_API_BASE, STG_ADMIN_EMAIL and STG_ADMIN_PASSWORD come from the environment.
--dry-run validates without credentials or API calls. Pending photo rights
remain pending; existing placeholder media is not a backend publication gate.
"""
import argparse
import fcntl
import hashlib
import json
import os
import sys
import tempfile
from contextlib import contextmanager
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import urlsplit
from uuid import UUID

import requests

DEFAULT_MANIFEST = Path(__file__).resolve().parent.parent / "content-research" / "import-manifest.json"
TIMEOUT = (5, 30)
PAYLOAD_FIELDS = ("name", "description", "fulfilmentModel", "confirmationType", "cancellationPolicy", "pickupInfo", "inclusions", "exclusions")


class ImportFailure(Exception):
    pass


class ApiFailure(ImportFailure):
    def __init__(self, message, status=None):
        super().__init__(message)
        self.status = status


def api(method, base, path, token=None, expected=200, **kwargs):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    try:
        response = requests.request(method, f"{base}/api/v1/{path}", headers=headers, timeout=TIMEOUT, **kwargs)
    except requests.RequestException as error:
        raise ApiFailure(f"{method} {path}: connection failed ({type(error).__name__}); check saved progress before retrying") from error
    if response.status_code != expected:
        # Never print response bodies: they may contain credentials or private data.
        raise ApiFailure(f"{method} {path}: HTTP {response.status_code}", response.status_code)
    try:
        return response.json()
    except ValueError as error:
        raise ApiFailure(f"{method} {path}: invalid JSON response") from error


def normalized_base(raw):
    base = raw.rstrip("/")
    parts = urlsplit(base)
    if parts.scheme not in ("http", "https") or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment:
        raise ImportFailure("STG_API_BASE must be an HTTP(S) URL without credentials, query or fragment")
    return base


def localized(value, label):
    if not isinstance(value, dict) or any(not isinstance(value.get(lang), str) or not value[lang].strip() for lang in ("en", "ar")):
        raise ImportFailure(f"{label}: non-empty English and Arabic text required")


def positive_integer(value, label):
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ImportFailure(f"{label}: positive integer required")


def validate_manifest(manifest):
    if not isinstance(manifest, dict) or not isinstance(manifest.get("categories"), list) or not isinstance(manifest.get("services"), list):
        raise ImportFailure("manifest requires categories and services arrays")
    codes = set()
    for category in manifest["categories"]:
        code = category.get("code")
        if not isinstance(code, str) or not code.strip() or code in codes:
            raise ImportFailure("category codes must be non-empty and unique")
        codes.add(code)
        localized({"en": category.get("name_en"), "ar": category.get("name_ar")}, code)
        order = category.get("order")
        if isinstance(order, bool) or not isinstance(order, int) or order < 0:
            raise ImportFailure(f"{code}: non-negative category order required")
    refs = set()
    for service in manifest["services"]:
        ref = service.get("internalRef")
        if not isinstance(ref, str) or not ref.strip() or ref in refs:
            raise ImportFailure("service internalRef values must be non-empty and unique")
        refs.add(ref)
        if service.get("categoryCode") not in codes:
            raise ImportFailure(f"{ref}: unknown categoryCode")
        if service.get("fulfilmentModel") != "DIRECT" or service.get("confirmationType") not in ("INSTANT", "STAFF_REVIEW"):
            raise ImportFailure(f"{ref}: unsupported fulfilment/confirmation type")
        for field in ("name", "description", "cancellationPolicy"):
            localized(service.get(field), f"{ref}.{field}")
        for field in ("pickupInfo", "inclusions", "exclusions"):
            if service.get(field) is not None:
                localized(service[field], f"{ref}.{field}")
        if not isinstance(service.get("options"), list) or not service["options"] or not isinstance(service.get("media"), list) or not service["media"]:
            raise ImportFailure(f"{ref}: options and media are required")
        for option in service["options"]:
            localized(option.get("label"), f"{ref}.option.label")
            if option.get("durationMinutes") is not None:
                positive_integer(option["durationMinutes"], f"{ref}.durationMinutes")
            positive_integer(option.get("maxParticipants"), f"{ref}.maxParticipants")
            try:
                amount = Decimal(str(option.get("priceAmount")))
                if not amount.is_finite() or amount < 0 or amount > Decimal("99999999.99") or amount != amount.quantize(Decimal("0.01")):
                    raise InvalidOperation
            except InvalidOperation as error:
                raise ImportFailure(f"{ref}: finite non-negative price with at most two decimal places required") from error
            currency = option.get("priceCurrency")
            if option.get("priceBasis") not in ("PER_PERSON", "PER_GROUP", "PER_VEHICLE", "FLAT") or not isinstance(currency, str) or len(currency) != 3 or not currency.isascii() or not currency.isalpha() or not currency.isupper():
                raise ImportFailure(f"{ref}: invalid price basis/currency")
        for media in service["media"]:
            if any(not isinstance(media.get(field), str) or not media[field].strip() for field in ("assetReference", "rightsEvidence", "locale")):
                raise ImportFailure(f"{ref}: media reference and rights text required")


def payload(service, category_id):
    return {
        **{field: service.get(field) for field in PAYLOAD_FIELDS},
        "categoryId": category_id, "providerId": None,
        "options": [{**option, "id": None} for option in service["options"]],
        "media": [{**media, "id": None} for media in service["media"]],
        "expectedVersion": None,
    }


def canonical_content(record):
    result = {field: record.get(field) for field in (*PAYLOAD_FIELDS, "providerId")}
    result["options"] = [{key: value for key, value in option.items() if key != "id"} for option in record["options"]]
    for option in result["options"]:
        option["priceAmount"] = str(Decimal(str(option["priceAmount"])).normalize())
    result["media"] = [{key: value for key, value in media.items() if key != "id"} for media in record["media"]]
    return result


def digest(service):
    content = {"categoryCode": service["categoryCode"], **canonical_content(payload(service, None))}
    return hashlib.sha256(json.dumps(content, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def verify_record(record, service, category_id):
    if record.get("categoryId") != category_id or canonical_content(record) != canonical_content(payload(service, category_id)):
        raise ImportFailure(f"{service['internalRef']}: saved service differs from manifest; reconcile in ERP before continuing")
    if record.get("status") not in ("DRAFT", "REVIEW", "APPROVED"):
        raise ImportFailure(f"{service['internalRef']}: status {record.get('status')} is outside the importer lifecycle")


def save_state(path, state):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, prefix=f".{path.name}.", delete=False) as handle:
            temporary = Path(handle.name)
            json.dump(state, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        temporary = None
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


@contextmanager
def locked_state(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.with_name(path.name + ".lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ImportFailure("another importer is using this state file") from error
        yield


def load_state(path, base, services):
    raw = json.loads(path.read_text()) if path.exists() else {}
    if not isinstance(raw, dict):
        raise ImportFailure("state file must be a JSON object")
    if "schemaVersion" not in raw:
        state = {"schemaVersion": 2, "apiBase": base, "services": raw}
        legacy = bool(raw)
    else:
        if raw.get("schemaVersion") != 2 or raw.get("apiBase") != base or not isinstance(raw.get("services"), dict):
            raise ImportFailure("state version/API target mismatch; use the state file belonging to this target")
        state, legacy = raw, False
    by_ref = {service["internalRef"]: service for service in services}
    for ref, entry in state["services"].items():
        if ref not in by_ref or not isinstance(entry, dict):
            raise ImportFailure("state contains unknown or malformed service entries")
        if not legacy and entry.get("payloadDigest") != digest(by_ref[ref]):
            raise ImportFailure(f"{ref}: manifest changed since import; reconcile saved content before continuing")
        if entry.get("serviceId"):
            UUID(entry["serviceId"])
        elif entry.get("status") != "CREATE_PENDING":
            raise ImportFailure(f"{ref}: missing saved service ID")
    return state, legacy


def list_categories(base, token):
    return {category["code"]: category for category in api("GET", base, "travel-marketplace/categories", token)}


def ensure_categories(base, token, categories, existing):
    code_to_id = {}
    for category in categories:
        code = category["code"]
        if code in existing:
            if existing[code]["status"] != "ACTIVE":
                raise ImportFailure(f"category {code} is archived; reconcile in ERP first")
            code_to_id[code] = existing[code]["id"]
            continue
        body = {"code": code, "name": {"en": category["name_en"], "ar": category["name_ar"]}, "description": None, "displayOrder": category["order"], "expectedVersion": None}
        created = api("POST", base, "travel-marketplace/categories", token, expected=201, json=body)
        code_to_id[code] = created["id"]
        print(f"  created category {code}")
    return code_to_id


def service_record(base, token, service_id):
    return api("GET", base, f"travel-marketplace/services/{service_id}", token)


def import_services(base, token, manifest, state_path, state, legacy, recoveries, retry_uncreated):
    entries = state["services"]
    by_ref = {service["internalRef"]: service for service in manifest["services"]}
    existing = list_categories(base, token)
    for category in manifest["categories"]:
        if category["code"] in existing and existing[category["code"]]["status"] != "ACTIVE":
            raise ImportFailure(f"category {category['code']} is archived; reconcile in ERP first")
    # Validate all known identities before any catalog writes, including old flat state.
    for ref, entry in entries.items():
        if entry.get("serviceId"):
            category_id = existing.get(by_ref[ref]["categoryCode"], {}).get("id")
            verify_record(service_record(base, token, entry["serviceId"]), by_ref[ref], category_id)
            entry["payloadDigest"] = digest(by_ref[ref])
    for assignment in recoveries:
        ref, separator, service_id = assignment.partition("=")
        if not separator or ref not in entries or entries[ref].get("serviceId"):
            raise ImportFailure("--recover-service requires an unresolved REF=service-UUID")
        UUID(service_id)
        category_id = existing.get(by_ref[ref]["categoryCode"], {}).get("id")
        record = service_record(base, token, service_id)
        verify_record(record, by_ref[ref], category_id)
        if any(entry.get("serviceId") == service_id for entry in entries.values()):
            raise ImportFailure("recovery ID is already linked to another reference")
        entries[ref] = {"serviceId": service_id, "status": record["status"], "payloadDigest": digest(by_ref[ref])}
    for ref in retry_uncreated:
        if ref not in entries or entries[ref].get("serviceId") or entries[ref].get("status") != "CREATE_PENDING":
            raise ImportFailure("--retry-uncreated requires an unresolved reference")
        del entries[ref]
    unresolved = [ref for ref, entry in entries.items() if not entry.get("serviceId")]
    if unresolved:
        raise ImportFailure(f"uncertain create outcome for {', '.join(unresolved)}; inspect ERP and use --recover-service REF=UUID, or --retry-uncreated REF only after confirming nothing was created")
    if legacy or recoveries or retry_uncreated:
        save_state(state_path, state)
    categories = ensure_categories(base, token, manifest["categories"], existing)
    created, resumed, skipped, failed = 0, 0, 0, 0
    for service in manifest["services"]:
        ref = service["internalRef"]
        entry = entries.get(ref)
        was_new = entry is None
        try:
            if was_new:
                # Persist intent before the non-idempotent POST. Never blindly repeat it.
                entry = {"serviceId": None, "status": "CREATE_PENDING", "payloadDigest": digest(service)}
                entries[ref] = entry
                save_state(state_path, state)
                try:
                    record = api("POST", base, "travel-marketplace/services", token, expected=201, json=payload(service, categories[service["categoryCode"]]))
                except ApiFailure as error:
                    if error.status in (400, 401, 403, 404, 422):
                        del entries[ref]  # Definitive API rejection, not an uncertain commit.
                        save_state(state_path, state)
                    raise
                entry["serviceId"] = str(UUID(record["id"]))
                entry["status"] = record["status"]
                save_state(state_path, state)
            else:
                record = service_record(base, token, entry["serviceId"])
            verify_record(record, service, categories[service["categoryCode"]])
            if record["status"] == "APPROVED":
                entry["status"] = "APPROVED"
                save_state(state_path, state)
                skipped += 1
                continue
            for status, action in (("DRAFT", "submit-for-review"), ("REVIEW", "approve")):
                if record["status"] == status:
                    record = api("POST", base, f"travel-marketplace/services/{entry['serviceId']}/{action}", token)
                    entry["status"] = record["status"]
                    save_state(state_path, state)
            if record["status"] != "APPROVED":
                raise ImportFailure(f"{ref}: approval did not complete")
            if was_new:
                created += 1
            else:
                resumed += 1
            print(f"  approved {ref}")
        except (ImportFailure, KeyError, ValueError) as error:
            failed += 1
            print(f"  FAILED {ref}: {error}", file=sys.stderr)
    print(f"Done. created={created} resumed={resumed} skipped(already approved)={skipped} failed={failed} total={len(manifest['services'])}")
    print("Importer stops at APPROVED; never publishes. Photo rights remain pending.")
    print(f"State recorded at {state_path}")
    return 1 if failed else 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default=str(DEFAULT_MANIFEST))
    parser.add_argument("--state-file", default=str(Path.home() / ".sharm-to-go-import-state.json"))
    parser.add_argument("--dry-run", action="store_true", help="Validate/list without credentials or API calls")
    parser.add_argument("--recover-service", action="append", default=[], metavar="REF=UUID", help="Link a verified existing service after a lost create response")
    parser.add_argument("--retry-uncreated", action="append", default=[], metavar="REF", help="Retry only after you confirmed in ERP that the earlier create made no service")
    args = parser.parse_args(argv)
    try:
        manifest = json.loads(Path(args.manifest).read_text())
        validate_manifest(manifest)
        state_path = Path(args.state_file).expanduser().resolve()
        if args.dry_run:
            if args.recover_service or args.retry_uncreated:
                raise ImportFailure("recovery flags cannot be combined with --dry-run")
            print(f"Validated {len(manifest['categories'])} categories and {len(manifest['services'])} services. No API calls; saved/remote status not checked.")
            for service in manifest["services"]:
                print(f"  - {service['internalRef']}")
            return 0
        base_raw, email, password = (os.environ.get(key) for key in ("STG_API_BASE", "STG_ADMIN_EMAIL", "STG_ADMIN_PASSWORD"))
        if not base_raw or not email or not password:
            raise ImportFailure("set STG_API_BASE, STG_ADMIN_EMAIL, STG_ADMIN_PASSWORD in the environment")
        base = normalized_base(base_raw)
        with locked_state(state_path):
            state, legacy = load_state(state_path, base, manifest["services"])
            token = api("POST", base, "identity/login", json={"email": email, "password": password})["token"]
            return import_services(base, token, manifest, state_path, state, legacy, args.recover_service, args.retry_uncreated)
    except (ImportFailure, OSError, ValueError, KeyError, TypeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
