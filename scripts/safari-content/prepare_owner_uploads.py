#!/usr/bin/env python3
"""Prepare owner-approved, visually selected photos without changing originals."""
import argparse
import hashlib
import json
import re
from pathlib import Path

from prepare_media_uploads import encoded_for_upload
from inspect_owner_media import sheet


def run(manifest, source, output):
    data = json.loads(manifest.read_text())
    if not data.get("ownerPermission", {}).get("confirmedOn"):
        raise ValueError("Owner permission confirmation is required")
    if output == source or output.is_relative_to(source / "catalog") or output.is_relative_to(source / "assets"):
        raise ValueError("Output must be outside original image folders")
    pending = []
    for entry in data["images"]:
        if not all(re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", entry[key]) for key in ("id", "tour")):
            raise ValueError("Unsafe id or tour slug")
        path = (source / entry["source"]).resolve()
        if not path.is_relative_to((source / "catalog").resolve()) or path.is_symlink():
            raise ValueError("Invalid original path")
        if set(entry["alt"]) != {"en", "ar", "ru", "it"} or not all(0 < len(text) <= 200 for text in entry["alt"].values()):
            raise ValueError("Four valid alt texts are required")
        original = path.read_bytes()
        encoded, size = encoded_for_upload(original)
        pending.append((entry, encoded, size, hashlib.sha256(original).hexdigest()))
    output.mkdir(parents=True, exist_ok=True)
    records = []
    for entry, encoded, size, original_hash in pending:
        relative = Path(entry["tour"]) / (entry["id"] + ".jpg")
        target = output / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists() and target.read_bytes() != encoded:
            raise ValueError("Prepared image differs; select a new output directory")
        if not target.exists():
            with target.open("xb") as stream:
                stream.write(encoded)
        records.append({**entry, "file": str(relative), "width": size[0], "height": size[1],
                        "sha256": hashlib.sha256(encoded).hexdigest(), "originalSha256": original_hash,
                        "bytes": len(encoded), "uploaded": False, "published": False})
    (output / "upload-manifest.json").write_text(json.dumps({"ownerPermission": data["ownerPermission"], "images": records}, ensure_ascii=False, indent=2) + "\n")
    sheet(output, [entry["file"] for entry in records], "Owner-approved curated tour images", output / "PREVIEW.jpg")
    print(json.dumps({"images": len(records), "tours": len({r["tour"] for r in records}), "bytes": sum(r["bytes"] for r in records)}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("manifest", "source", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    run(args.manifest.resolve(), args.source.resolve(), args.output.resolve())
