#!/usr/bin/env python3
"""Create local video storyboards and metadata; leave source clips untouched."""
import argparse
import json
import subprocess
from pathlib import Path

from inspect_owner_media import sheet


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    source, output = args.source.resolve(), args.output.resolve()
    if output == source or output.is_relative_to(source):
        parser.error("Use a separate review directory")
    output.mkdir(parents=True, exist_ok=True)
    clips, frames = [], []
    for path in sorted(source.glob("*.mp4")):
        metadata = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(path)]))
        video = next(stream for stream in metadata["streams"] if stream["codec_type"] == "video")
        duration = float(metadata["format"]["duration"])
        record = {"path": str(path), "duration": duration, "width": video["width"], "height": video["height"],
                  "bytes": path.stat().st_size, "hasAudio": any(s["codec_type"] == "audio" for s in metadata["streams"])}
        clips.append(record)
        for index, fraction in enumerate((0.15, 0.5, 0.85)):
            target = output / f"{path.stem}-{index + 1}.jpg"
            if not target.exists():
                subprocess.run(["ffmpeg", "-v", "error", "-ss", str(duration * fraction), "-i", str(path), "-frames:v", "1",
                                "-vf", "scale=640:640:force_original_aspect_ratio=decrease", str(target)], check=True)
            frames.append(target.name)
    (output / "video-inventory.json").write_text(json.dumps(clips, ensure_ascii=False, indent=2) + "\n")
    for offset in range(0, len(frames), 24):
        sheet(output, frames[offset:offset + 24], "Safari source-video storyboard: 15%, 50%, 85%", output / f"storyboard-{offset // 24 + 1:02d}.jpg")
    print(json.dumps({"clips": len(clips), "frames": len(frames), "seconds": sum(c["duration"] for c in clips)}))


if __name__ == "__main__":
    main()
