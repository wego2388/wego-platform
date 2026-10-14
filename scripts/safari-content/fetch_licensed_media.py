#!/usr/bin/env python3
"""Fetch explicitly reviewed public image sources; never publish or change originals.

Only images. No crawling, credentials, redirects to arbitrary hosts or runtime writes.
The manifest is an editorial decision, not proof of operator ownership or a model release.
"""
import argparse
import hashlib
import html
import io
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote, urlsplit, urlunsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

from PIL import Image
from inspect_owner_media import sheet

ALLOWED = {"commons.wikimedia.org", "upload.wikimedia.org", "images.pexels.com"}
LICENSES = {
    "Pexels": "https://www.pexels.com/license/",
    "CC0-1.0": "https://creativecommons.org/publicdomain/zero/1.0/",
    "CC-BY-4.0": "https://creativecommons.org/licenses/by/4.0/",
    "CC-BY-SA-4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
}
MAX_BYTES = 25 * 1024 * 1024


def checked_url(url):
    parts = urlsplit(url)
    if parts.scheme != "https" or parts.hostname not in ALLOWED or parts.username or parts.password or parts.port:
        raise ValueError("Source must use an approved public HTTPS host")
    return urlunsplit((parts.scheme, parts.netloc, quote(parts.path, safe="/%:().,~-_"), parts.query, ""))


class SafeRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return super().redirect_request(req, fp, code, msg, headers, checked_url(newurl))


def fetch(url, limit=MAX_BYTES):
    req = Request(checked_url(url), headers={"User-Agent": "SafariToursSharm-media-curation/1.0"})
    with build_opener(SafeRedirect()).open(req, timeout=25) as response:
        data = response.read(limit + 1)
        if len(data) > limit:
            raise ValueError("Download exceeds size limit")
        return data


def commons_image_url(page):
    text = fetch(page, 2 * 1024 * 1024).decode("utf-8")
    links = re.findall(r'href="(https://upload\.wikimedia\.org/[^\"]+)"', text)
    originals = [html.unescape(url).split("?")[0] for url in links if "/thumb/" not in url]
    if not originals:
        raise ValueError("No original image URL in reviewed Commons page")
    return originals[0]


def validate_entry(entry):
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", entry["id"]):
        raise ValueError("Unsafe image id")
    for key in ("sourcePage", "author", "license", "licenseUrl", "tour", "usage"):
        if not entry.get(key):
            raise ValueError(f"Missing provenance: {key}")
    if LICENSES.get(entry["license"]) != entry["licenseUrl"]:
        raise ValueError("Unreviewed license")
    if not re.fullmatch(r"(sea|cultural|desert|shows|transfers)/[a-z0-9]+(?:-[a-z0-9]+)*", entry["tour"]):
        raise ValueError("Invalid tour folder")
    source = urlsplit(entry["sourcePage"])
    if source.scheme != "https" or source.hostname not in ("commons.wikimedia.org", "www.pexels.com"):
        raise ValueError("Invalid source page")
    if source.username or source.password or source.port:
        raise ValueError("Invalid source page credentials or port")
    if entry.get("downloadUrl"):
        checked_url(entry["downloadUrl"])


def run(manifest, output):
    entries = json.loads(manifest.read_text())["images"]
    if len({e["id"] for e in entries}) != len(entries):
        raise ValueError("Duplicate image id")
    for entry in entries:
        validate_entry(entry)
    original = output / "originals"
    original.mkdir(parents=True, exist_ok=True)
    previous_path = output / "download-report.json"
    previous = {e["id"]: e for e in json.loads(previous_path.read_text())["images"]} if previous_path.exists() else {}
    results = []
    for entry in entries:
        record = dict(entry)
        path = original / f'{entry["id"]}.jpg'
        try:
            cached = previous.get(entry["id"])
            if path.is_symlink():
                raise ValueError("Refusing symlink cache entry")
            if path.exists():
                if not cached or any(cached.get(k) != entry.get(k) for k in ("sourcePage", "downloadUrl")):
                    raise ValueError("Existing file has no matching provenance; use a new image id")
                if hashlib.sha256(path.read_bytes()).hexdigest() != cached.get("sha256"):
                    raise ValueError("Cached file checksum changed")
            url = entry.get("downloadUrl") or (cached.get("resolvedDownloadUrl") if cached else None) or commons_image_url(entry["sourcePage"])
            checked_url(url)
            record["resolvedDownloadUrl"] = url
            data = path.read_bytes() if path.exists() else fetch(url)
            with Image.open(io.BytesIO(data)) as im:
                if im.format != "JPEG":
                    raise ValueError("Expected JPEG image, not a web page or other file")
                width, height = im.size
                im.verify()
            if not path.exists():
                with path.open("xb") as dest:
                    dest.write(data)
            record.update(path=str(path.relative_to(output)), sha256=hashlib.sha256(data).hexdigest(),
                          bytes=len(data), width=width, height=height,
                          uploadSizeOk=len(data) <= 10 * 1024 * 1024 and width * height <= 24_000_000,
                          downloaded=True)
            print(f'{entry["id"]}: {width}x{height}, {len(data)} bytes', flush=True)
        except Exception as exc:
            record.update(downloaded=False, error=type(exc).__name__ + ": " + str(exc))
            print(f'{entry["id"]}: {record["error"]}', flush=True)
        results.append(record)
    report = {"checkedAt": datetime.now(timezone.utc).isoformat(), "published": False, "images": results}
    (output / "download-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    paths = [r["path"] for r in results if r["downloaded"]]
    for i in range(0, len(paths), 24):
        sheet(output, paths[i:i + 24], "Licensed source candidates — visual review required",
              output / f"contact-sheet-{i // 24 + 1:02d}.jpg")
    if any(not r["downloaded"] for r in results):
        raise SystemExit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    run(args.manifest.resolve(), args.output.resolve())
