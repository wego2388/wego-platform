#!/usr/bin/env python3
"""Assemble a silent, local Safari hero from owner-supplied footage.

No original is modified. Clips are brief scene-setting views, not itinerary or
supplier promises. Rotation metadata on video-23 is intentionally ignored.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
from PIL import Image


def build(source, site, ledger):
    clips = [("video-24.mp4", 1, False), ("video-23.mp4", 50, True), ("video-16.mp4", 3, False)]
    output = site / "public/videos"
    output.mkdir(parents=True, exist_ok=True)
    command = ["ffmpeg", "-v", "error", "-y", "-filter_complex_threads", "1"]
    records = []
    for name, start, ignore_rotation in clips:
        path = source / name
        command += ["-ss", str(start)] + (["-noautorotate"] if ignore_rotation else []) + ["-t", "4", "-i", str(path)]
        records.append({"source": f"assets/videos/{name}", "startSeconds": start, "durationSeconds": 4,
                        "ignoreRotationMetadata": ignore_rotation, "sha256": hashlib.file_digest(path.open("rb"), "sha256").hexdigest()})
    filters = [f"[{i}:v]scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,setsar=1,fps=24,setpts=PTS-STARTPTS,format=yuv420p[v{i}]" for i in range(3)]
    filters += ["[v0][v1]xfade=transition=fade:duration=0.5:offset=3.5[x]", "[x][v2]xfade=transition=fade:duration=0.5:offset=7,fade=t=in:st=0:d=0.35,fade=t=out:st=10.65:d=0.35[out]"]
    target = output / "safari-hero-20261015.mp4"
    subprocess.run(command + ["-filter_complex", ";".join(filters), "-map", "[out]", "-an", "-map_metadata", "-1", "-metadata:s:v:0", "rotate=0",
                             "-c:v", "libx264", "-preset", "slow", "-crf", "27", "-maxrate", "1800k", "-bufsize", "3600k",
                             "-threads", "2", "-movflags", "+faststart", str(target)], check=True)
    if target.stat().st_size > 3_000_000:
        raise ValueError("Hero exceeds 3 MB budget")
    poster = output / "safari-hero-poster.webp"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", "2", "-i", str(source / "video-24.mp4"), "-frames:v", "1", "-c:v", "libwebp", "-quality", "80", str(poster)], check=True)
    with Image.open(poster) as img:
        img.resize((640, 360), Image.Resampling.LANCZOS).save(output / "safari-hero-poster-640.webp", "WEBP", quality=79, method=6)
    metadata = {"source": "Owner-supplied Safari media library; owner requested hero assembly in conversation 2026-10-15",
                "edits": "11-second silent montage; sea/desert/parasailing; crossfades, size compression, stripped metadata; originals preserved",
                "clips": records, "output": "/videos/" + target.name, "bytes": target.stat().st_size,
                "sha256": hashlib.file_digest(target.open("rb"), "sha256").hexdigest(), "audio": False}
    ledger.write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps({"videoBytes": target.stat().st_size, "posterBytes": poster.stat().st_size}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "site", "ledger"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    build(args.source.resolve(), args.site.resolve(), args.ledger.resolve())
