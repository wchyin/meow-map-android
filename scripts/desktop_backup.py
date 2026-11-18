"""Convert an original desktop JSON export into an Android portable ZIP.

python scripts/desktop_backup.py --input C:/path/meow-backup.json --output output/meow-backup.zip
Only reads the source export/photos; never modifies the desktop project or uploads data.
"""
import argparse
import json
from pathlib import Path
import uuid
import zipfile


def convert(source: Path, target: Path):
    payload = json.loads(source.read_text(encoding="utf-8-sig"))
    if payload.get("app") != "meow-meow-map" or payload.get("version") != 1:
        raise ValueError("Expected an original meow-meow-map v1 JSON export")
    images = {}
    paths = {}
    total = 0
    def archive_image(filename):
        nonlocal total
        path = Path(filename)
        if not path.is_absolute():
            path = source.parent / path
        path = path.resolve(strict=True)
        if path not in paths:
            if path.stat().st_size > 2 * 1024 * 1024:
                raise ValueError(f"Photo exceeds server's 2 MiB limit: {path}")
            content = path.read_bytes()
            total += len(content)
            if total > 38 * 1024 * 1024:
                raise ValueError("Photos exceed portable backup size limit; split the export")
            name = f"photos/{uuid.uuid4()}"
            paths[path] = name
            images[name] = content
        return paths[path]

    for kind in ("cats", "records", "map_records"):
        for row in payload.get(kind, []):
            filenames = row.get("images") or ([row["image"]] if row.get("image") else [])
            if filenames:
                row["images"] = [archive_image(filename) for filename in filenames]
                row["image"] = row["images"][0]
            avatar = row.get("avatar")
            if kind == "cats" and avatar and not avatar.startswith("builtin:"):
                row["avatar"] = archive_image(avatar)
    target.parent.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    if total + len(encoded) > 40 * 1024 * 1024:
        raise ValueError("Backup content exceeds 40 MiB; split the export")
    # Never overwrite an existing backup.
    with zipfile.ZipFile(target, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("data.json", encoded)
        for name, content in images.items():
            archive.writestr(name, content)
    print(f"Created {target} ({len(images)} photos)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    convert(args.input, args.output)
