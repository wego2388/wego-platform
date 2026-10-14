#!/usr/bin/env python3
"""Generate bounded, EXIF-free WebP defaults from the reviewed owner manifest.

Originals are never modified. Generated metadata is site-only: it does not create
tours or alter activity, prices, availability, managed approval or ERP records.
"""
import argparse
import hashlib
import io
import json
import re
from pathlib import Path
from PIL import Image, ImageOps


def build(manifest, source, site):
    data = json.loads(manifest.read_text())
    if not data.get("ownerPermission", {}).get("confirmedOn"):
        raise ValueError("Missing owner permission")
    output = site / "public/images/catalog"
    records, seen, covers = [], set(), set()
    for item in data["images"]:
        if item["id"] in seen or not all(re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", item[k]) for k in ("id", "tour")):
            raise ValueError("Duplicate or unsafe image identity")
        seen.add(item["id"])
        if item["role"] not in {"cover", "gallery"}:
            raise ValueError("Unknown image role")
        if item["role"] == "cover":
            if item["tour"] in covers:
                raise ValueError("Duplicate tour cover")
            covers.add(item["tour"])
        if set(item["alt"]) != {"en", "ar", "ru", "it"} or not all(0 < len(t) <= 200 for t in item["alt"].values()):
            raise ValueError("Four localized captions required")
        original = (source / item["source"]).resolve()
        if not original.is_relative_to((source / "catalog").resolve()):
            raise ValueError("Source escapes owner catalog")
        original_bytes = original.read_bytes()
        with Image.open(io.BytesIO(original_bytes)) as loaded:
            img = ImageOps.exif_transpose(loaded).convert("RGB")
            img.thumbnail((1600, 1600), Image.Resampling.LANCZOS)
            variants = []
            for width in sorted({min(w, img.width) for w in (480, 960, 1600)}):
                resized = img.resize((width, round(img.height * width / img.width)), Image.Resampling.LANCZOS)
                target = output / item["tour"] / f'{item["id"]}-{width}.webp'
                target.parent.mkdir(parents=True, exist_ok=True)
                for quality in (79, 73, 67, 60):
                    resized.save(target, "WEBP", quality=quality, method=6)
                    if target.stat().st_size <= 420_000:
                        break
                if target.stat().st_size > 420_000:
                    raise ValueError(f"Image budget exceeded: {target}")
                variants.append({"path": "/" + str(target.relative_to(site / "public")), "width": width, "height": resized.height})
            records.append({"id": item["id"], "tour": item["tour"], "isCover": item["role"] == "cover",
                            "alt": item["alt"], **variants[-1], "variants": variants,
                            "sourceSha256": hashlib.sha256(original_bytes).hexdigest()})
    if covers != {r["tour"] for r in records}:
        raise ValueError("Every bundled tour needs exactly one cover")
    target = site / "app/content/curatedMedia.json"
    target.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"images": len(records), "tours": len(covers), "bytes": sum(p.stat().st_size for p in output.rglob("*.webp"))}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("manifest", "source", "site"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    build(args.manifest.resolve(), args.source.resolve(), args.site.resolve())
