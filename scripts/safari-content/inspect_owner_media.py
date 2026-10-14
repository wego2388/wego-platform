#!/usr/bin/env python3
"""Read originals; build a local, reproducible media review without altering them."""
import argparse
import hashlib
import json
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps


def inspect(path, root):
    record = {"path": str(path.relative_to(root)), "bytes": path.stat().st_size}
    record["sha256"] = hashlib.sha256(path.read_bytes()).hexdigest()
    try:
        with Image.open(path) as im:
            record.update(width=im.width, height=im.height, format=im.format,
                          hasExif=bool(im.getexif()), hasIcc=bool(im.info.get("icc_profile")))
        with Image.open(path) as im:
            im.verify()
    except Exception as exc:
        record["error"] = type(exc).__name__
    return record


def sheet(root, paths, title, output):
    cols, cellw, cellh = 4, 300, 248
    canvas = Image.new("RGB", (cols * cellw, 55 + ((len(paths) + cols - 1) // cols) * cellh), "#f1f4f8")
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 14)
    draw.text((15, 15), title, fill="#142a3e", font=font)
    for i, path in enumerate(paths):
        x, y = (i % cols) * cellw, 55 + (i // cols) * cellh
        try:
            with Image.open(root / path) as source:
                im = ImageOps.exif_transpose(source).convert("RGB")
                dims = f"{im.width} x {im.height}"
                im.thumbnail((cellw - 12, 194))
                canvas.paste(im, (x + (cellw - im.width) // 2, y + (194 - im.height) // 2))
            label = Path(path).name
            draw.text((x + 6, y + 198), label[:36], fill="#142a3e", font=font)
            draw.text((x + 6, y + 220), dims, fill="#526375", font=font)
        except Exception:
            draw.text((x + 8, y + 10), f"UNREADABLE: {Path(path).name}", fill="red", font=font)
    canvas.save(output, quality=85)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root, output = args.source.resolve(), args.output.resolve()
    if output == root or any(output.is_relative_to(root / folder) for folder in ("catalog", "assets")):
        parser.error("output must be a separate review directory")
    output.mkdir(parents=True, exist_ok=True)
    index = json.loads((root / "catalog/catalog-media-index.json").read_text())
    records = []
    for folder in (root / "assets", root / "catalog"):
        for path in sorted(folder.rglob("*")):
            if path.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp") and path.is_file():
                records.append(inspect(path, root))
    by_path = {r["path"]: r for r in records}
    by_hash = defaultdict(list)
    for r in records:
        by_hash[r["sha256"]].append(r["path"])
    missing, tour_reports = [], []
    for tour in index["tours"]:
        folder = f'catalog/{tour["category"].lower()}/{tour["slug"]}'
        paths = [f"{folder}/{name}" for name in tour["gallery"]]
        missing.extend(p for p in paths if p not in by_path)
        paths = [p for p in paths if p in by_path]
        distinct = list(dict.fromkeys(by_path[p]["sha256"] for p in paths))
        tour_reports.append({"slug": tour["slug"], "images": len(paths), "distinctImages": len(distinct),
                             "cover": tour.get("coverPath"),
                             "smallImages": [p for p in paths if min(by_path[p].get("width", 0), by_path[p].get("height", 0)) < 400]})
        for offset in range(0, len(paths), 32):
            sheet(root, paths[offset:offset + 32], f'{tour["slug"]} / {offset + 1}-{min(offset + 32, len(paths))}',
                  output / f'{tour["slug"]}-{offset // 32 + 1:02d}.jpg')
    covers = [t["coverPath"] for t in index["tours"] if t.get("coverPath") in by_path]
    sheet(root, covers, "Candidate covers — original supplied order", output / "all-covers.jpg")
    report = {"source": str(root), "files": records, "totalImages": len(records),
              "uniqueImages": len(by_hash), "duplicateGroups": [paths for paths in by_hash.values() if len(paths) > 1],
              "missingReferences": missing, "tours": tour_reports,
              "readErrors": [r for r in records if "error" in r]}
    (output / "media-audit.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({"images": len(records), "unique": len(by_hash), "duplicateGroups": len(report["duplicateGroups"]),
                      "missing": len(missing), "errors": len(report["readErrors"]), "output": str(output)}))


if __name__ == "__main__":
    main()
