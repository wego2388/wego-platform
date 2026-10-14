#!/usr/bin/env python3
"""Package visually selected photos, multilingual alt text and licence records.

Mechanical image normalization only: resize (never upscale), sRGB, JPEG, no EXIF.
No synthesis, retouch, watermark removal, upload, approval or tour activation.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path

from PIL import Image, ImageCms, ImageOps
from fetch_licensed_media import validate_entry
from inspect_owner_media import sheet


def encoded_for_upload(data):
    with Image.open(io.BytesIO(data)) as original:
        image = ImageOps.exif_transpose(original)
        icc = original.info.get("icc_profile")
        if icc:
            image = ImageCms.profileToProfile(image, ImageCms.ImageCmsProfile(io.BytesIO(icc)),
                                             ImageCms.createProfile("sRGB"), outputMode="RGB")
        else:
            image = image.convert("RGB")
        image.thumbnail((2400, 2400), Image.Resampling.LANCZOS)
        # A new pixel-only image ensures no source metadata is copied implicitly.
        clean = Image.new("RGB", image.size)
        clean.paste(image)
        result = io.BytesIO()
        clean.save(result, format="JPEG", quality=88, optimize=True, progressive=True)
        return result.getvalue(), clean.size


def run(sources, selection, downloads, output):
    if output == downloads or output.is_relative_to(downloads / "originals"):
        raise ValueError("Output must not overlap the downloaded originals")
    catalog = {e["id"]: e for e in json.loads(sources.read_text())["images"]}
    report = {e["id"]: e for e in json.loads((downloads / "download-report.json").read_text())["images"]}
    selected = json.loads(selection.read_text())["selected"]
    if len({e["id"] for e in selected}) != len(selected):
        raise ValueError("Duplicate selected id")
    pending = []
    # Validate ALL selections before creating output. Fail closed on unknown provenance.
    for choice in selected:
        source = catalog[choice["id"]]
        validate_entry(source)
        if set(choice["alt"]) != {"en", "ar", "ru", "it"} or not all(choice["alt"].values()):
            raise ValueError("All four alt texts are required")
        if choice["reviewStatus"] != "VISUALLY_REVIEWED":
            raise ValueError("Image has not been visually reviewed")
        record = report[choice["id"]]
        if not record.get("downloaded") or record["sourcePage"] != source["sourcePage"]:
            raise ValueError("Source does not match download evidence")
        path = (downloads / record["path"]).resolve()
        if not path.is_relative_to((downloads / "originals").resolve()):
            raise ValueError("Image escaped original directory")
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != record["sha256"]:
            raise ValueError("Original checksum changed")
        prepared, size = encoded_for_upload(data)
        pending.append((source, choice, record, prepared, size))
    output.mkdir(parents=True, exist_ok=True)
    ready = []
    for source, choice, record, data, size in pending:
        relative = Path(source["tour"]) / (source["id"] + ".jpg")
        target = output / relative
        if not target.resolve().is_relative_to(output.resolve()):
            raise ValueError("Output escaped package directory")
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists() and target.read_bytes() != data:
            raise ValueError("Refusing to replace a different prepared image; use a new output directory")
        if not target.exists():
            with target.open("xb") as dest:
                dest.write(data)
        item = {**source, **choice, "file": str(relative), "width": size[0], "height": size[1],
                "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                "originalSha256": record["sha256"],
                "published": choice["publicationStatus"] == "PUBLISHED_AND_VERIFIED",
                "changes": "Resized to at most 2400px; sRGB JPEG re-encode; metadata stripped; no scene edits."}
        ready.append(item)
        (target.parent / (target.stem + ".rights.json")).write_text(json.dumps(item, ensure_ascii=False, indent=2) + "\n")
    published = sum(item["published"] for item in ready)
    (output / "upload-manifest.json").write_text(json.dumps({"publishedCount": published, "images": ready}, ensure_ascii=False, indent=2) + "\n")
    lines = ["# Photo source and licence records", "", "Local upload package; only per-image production receipts count as publication evidence.", "",
             "CC BY and CC BY-SA credits below must be visible publicly before those files are approved.",
             "CC BY-SA derivatives retain that licence. This does not license the website code.",
             "Stock boats, vehicles and participants must not imply supplier affiliation or guaranteed equipment.", ""]
    for item in ready:
        lines.extend([f'## {item["tour"]}', "", f'`{item["file"]}`', "",
                      f'Photo: {item["author"]} — [source]({item["sourcePage"]}) — [{item["license"]}]({item["licenseUrl"]}).',
                      item["changes"], "", "Publication conditions: " + "; ".join(item["publicationConditions"]), ""])
    (output / "PHOTO_CREDITS.md").write_text("\n".join(lines))
    sheet(output, [r["file"] for r in ready], "Safari Tours Sharm — selected licensed supplements", output / "PREVIEW.jpg")
    print(json.dumps({"selected": len(ready), "totalBytes": sum(r["bytes"] for r in ready), "published": published}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("sources", "selection", "downloads", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    run(args.sources.resolve(), args.selection.resolve(), args.downloads.resolve(), args.output.resolve())
