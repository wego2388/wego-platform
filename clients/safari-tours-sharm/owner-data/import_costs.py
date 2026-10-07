"""Import tour costs and driver trip rates from the owner data hub (WEGO-016-OPS2-F).

Reads the hub sheets «أسعار الموردين» (supplier price per person per tour),
«تكاليف الرحلات» (our extra per-person / per-trip costs) and «السواقين»
(driver pay) and turns every clearly numeric cell into one cost component.
Nothing is invented: a cell that is empty, «شامل» or «لا يوجد» is skipped,
and every other non-numeric cell (e.g. '500+100', '600/800', a guide's name,
«حسب التشغيله») is FLAGGED for the owner instead of being guessed.

Default is a dry run that prints the plan and the flags. ``--apply`` creates
the components through the staff API (never the database), with credentials
from the environment only:

    SAFARI_API_BASE        e.g. https://staff.example.com (no trailing slash)
    SAFARI_STAFF_EMAIL     a staff account holding tours-operator.cost:manage
    SAFARI_STAFF_PASSWORD

Assumptions (owner-delegated, marked for review in the hub):
  * the supplier price applies to whichever supplier runs the tour (no
    supplier column is filled), so components are unlinked;
  * «رسوم دخول» is an extra own cost per person;
  * the guide column is per departure (only numbers are imported);
  * a child costs the adult amount unless a child price is given;
  * own costs start on the tour's supplier-price date, else ``--valid-from``.
Re-running is safe: a component identical to an open one (same owner,
category, label, basis, currency, amount, start) is skipped.
"""
import argparse
import datetime as dt
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

import openpyxl

HERE = Path(__file__).resolve().parent
HUB = HERE / "safari-tours-owner-data-hub.xlsx"
SKIP_WORDS = {"شامل", "لا يوجد", "لايوجد", "-", "—"}
CURRENCY = {"جنيه": "EGP", "جنيه مصري": "EGP", "egp": "EGP", "يورو": "EUR", "eur": "EUR"}
NUMBER = re.compile(r"^\d{1,7}(\.\d{1,2})?$")


def parse_amount(value):
    """('number', '1100.00') | ('skip', None) | ('flag', raw)."""
    if value is None:
        return "skip", None
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if value < 0:
            return "flag", str(value)
        return "number", f"{value:.2f}"
    text = str(value).strip()
    if not text or text in SKIP_WORDS:
        return "skip", None
    if NUMBER.match(text):
        return "number", f"{float(text):.2f}"
    return "flag", text


def currency_of(raw, flags, where):
    key = str(raw or "").strip().lower()
    if key in CURRENCY:
        return CURRENCY[key]
    flags.append(f"{where}: unknown currency {raw!r}")
    return None


def day_of(value):
    if isinstance(value, dt.datetime):
        return value.date().isoformat()
    if isinstance(value, dt.date):
        return value.isoformat()
    return None


def rows(ws, header_row=2):
    for row in ws.iter_rows(min_row=header_row + 1, values_only=True):
        if row and row[0]:
            yield row


def plan(workbook, default_from):
    components, flags, supplier_dates = [], [], {}

    ws = workbook["أسعار الموردين"]
    for r in rows(ws):
        slug, supplier_code, adult, child, unit, percent, cur, _season, valid_from, valid_to, _notes = (list(r) + [None] * 11)[:11]
        where = f"أسعار الموردين/{slug}"
        currency = currency_of(cur, flags, where)
        start = day_of(valid_from) or default_from
        supplier_dates[slug] = start
        kind, amount = parse_amount(adult)
        if kind == "flag":
            flags.append(f"{where}: adult price {amount!r} is not a single number — not imported")
        child_kind, child_amount = parse_amount(child)
        if child_kind == "flag":
            flags.append(f"{where}: child price {child_amount!r} is not a number — child costed at the adult price")
            child_amount = None
        unit_kind, unit_amount = parse_amount(unit)
        if unit_kind == "number":
            flags.append(f"{where}: unit price {unit_amount} present — add it by hand as a PER_UNIT cost if it is a separate charge")
        if percent not in (None, ""):
            flags.append(f"{where}: percent-of-sale {percent!r} is not supported — enter by hand")
        if supplier_code:
            flags.append(f"{where}: supplier code {supplier_code!r} — link the cost to it in the ERP if the price is supplier-specific")
        if kind == "number" and currency:
            components.append({
                "tour": slug, "category": "SUPPLIER", "label": "سعر المورد", "basis": "PER_PERSON", "currency": currency,
                "amount": amount, "childAmount": child_amount if child_kind == "number" else None, "validFrom": start,
                "validUntil": day_of(valid_to),
            })

    ws = workbook["تكاليف الرحلات"]
    columns = [(1, "رسوم دخول", "OWN_EXTRA", "PER_PERSON"), (2, "مرشد", "FIXED", "PER_DEPARTURE"), (3, "أكل", "OWN_EXTRA", "PER_PERSON"),
               (4, "مية/مشروبات", "OWN_EXTRA", "PER_PERSON"), (5, "تكاليف تانية", "FIXED", "PER_DEPARTURE")]
    for r in rows(ws):
        r = (list(r) + [None] * 9)[:9]
        slug = r[0]
        currency = currency_of(r[6], flags, f"تكاليف الرحلات/{slug}")
        for index, label, category, basis in columns:
            kind, amount = parse_amount(r[index])
            where = f"تكاليف الرحلات/{slug}/{label}"
            if kind == "flag":
                flags.append(f"{where}: {amount!r} is not a single number — not imported")
            elif kind == "number" and currency:
                if index == 5:
                    flags.append(f"{where}: imported as a fixed amount per departure (assumption) — check")
                components.append({
                    "tour": slug, "category": category, "label": label, "basis": basis, "currency": currency, "amount": amount,
                    "childAmount": None, "validFrom": supplier_dates.get(slug, default_from), "validUntil": None,
                })

    ws = workbook["السواقين"]
    for r in rows(ws):
        r = (list(r) + [None] * 11)[:11]
        code, name, wage, basis, cur = r[0], r[1], r[4], r[5], r[6]
        if not name:
            continue
        kind, amount = parse_amount(wage)
        where = f"السواقين/{code} {str(name).strip()}"
        if kind == "flag":
            flags.append(f"{where}: pay {amount!r} is not a number — enter a charge per departure in Settlements")
        elif kind == "number":
            if str(basis or "").strip() not in ("مشوار", "بالمشوار"):
                flags.append(f"{where}: pay basis {basis!r} is not per trip — not imported")
                continue
            currency = currency_of(cur, flags, where)
            if currency:
                components.append({
                    "driver": str(name).strip(), "category": "DRIVER", "label": "أجر المشوار", "basis": "PER_DEPARTURE",
                    "currency": currency, "amount": amount, "childAmount": None, "validFrom": default_from, "validUntil": None,
                })
    return components, flags


class Api:
    def __init__(self, base, email, password):
        self.base = base.rstrip("/")
        self.token = self.call("POST", "/api/v1/identity/login", {"email": email, "password": password}, auth=False)["token"]

    def call(self, method, path, body=None, auth=True):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        if auth:
            request.add_header("Authorization", f"Bearer {self.token}")
        with urllib.request.urlopen(request, timeout=30) as response:  # noqa: S310 (operator-supplied base URL)
            text = response.read().decode()
            return json.loads(text) if text else None


def apply(components, flags):
    base, email, password = (os.environ.get(k) for k in ("SAFARI_API_BASE", "SAFARI_STAFF_EMAIL", "SAFARI_STAFF_PASSWORD"))
    if not (base and email and password):
        sys.exit("Set SAFARI_API_BASE, SAFARI_STAFF_EMAIL and SAFARI_STAFF_PASSWORD to apply.")
    api = Api(base, email, password)
    tours, page = {}, 0
    while True:
        batch = api.call("GET", f"/api/v1/tours-operator/staff/tours?page={page}&size=100")
        tours.update({t["slug"]: t["id"] for t in batch})
        if len(batch) < 100:
            break
        page += 1
    drivers = {d["name"].strip(): d["id"] for d in api.call("GET", "/api/v1/tours-operator/staff/drivers")}
    existing = api.call("GET", "/api/v1/tours-operator/staff/costs")
    created = skipped = 0
    for c in components:
        owner = {"tourId": tours.get(c.get("tour"))} if "tour" in c else {"driverId": drivers.get(c.get("driver"))}
        if not next(iter(owner.values())):
            flags.append(f"{c.get('tour') or c.get('driver')}: not found in the system — skipped")
            continue
        same = [e for e in existing if e.get("tourId") == owner.get("tourId") and e.get("driverId") == owner.get("driverId")
                and e["category"] == c["category"] and e["label"] == c["label"] and e["basis"] == c["basis"]
                and e["amount"] == {"amount": c["amount"], "currencyCode": c["currency"]} and e["validFrom"] == c["validFrom"]]
        if same:
            skipped += 1
            continue
        body = {**owner, **{k: c[k] for k in ("category", "label", "basis", "currency", "validFrom", "validUntil")},
                "amount": float(c["amount"]), "childAmount": float(c["childAmount"]) if c["childAmount"] else None,
                "note": "Imported from the owner data hub (OPS2-F import_costs.py)"}
        api.call("POST", "/api/v1/tours-operator/staff/costs", body)
        created += 1
    print(f"created {created}, already present {skipped}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--hub", type=Path, default=HUB)
    parser.add_argument("--valid-from", default=dt.date.today().isoformat(), help="start date when the hub gives none (YYYY-MM-DD)")
    parser.add_argument("--apply", action="store_true", help="create the components through the staff API (default: dry run)")
    args = parser.parse_args()
    dt.date.fromisoformat(args.valid_from)
    workbook = openpyxl.load_workbook(args.hub, data_only=True, read_only=True)
    components, flags = plan(workbook, args.valid_from)
    print(f"{len(components)} cost component(s) planned:")
    for c in components:
        owner = c.get("tour") or f"driver {c.get('driver')}"
        child = f" (child {c['childAmount']})" if c["childAmount"] else ""
        print(f"  {owner:34} {c['category']:9} {c['basis']:13} {c['amount']:>10} {c['currency']}{child} from {c['validFrom']}  [{c['label']}]")
    if args.apply:
        apply(components, flags)
    print(f"\n{len(flags)} cell(s) need the owner's attention (not imported):")
    for f in flags:
        print(f"  ! {f}")
    if not args.apply:
        print("\nDry run: nothing was sent. Re-run with --apply to create these through the staff API.")


if __name__ == "__main__":
    main()
