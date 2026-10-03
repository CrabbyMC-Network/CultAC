"""Translate the pinned official metadata to a JDK-readable acquisition manifest."""
import json
from pathlib import Path
import sys

lock = json.loads(Path(sys.argv[1]).read_text())
values = {"version": lock["version"], "protocol": lock["protocol"]}
for name in ("client", "server"):
    if name not in lock:
        continue
    for key in ("sha1", "size", "url"):
        values[f"{name}.{key}"] = lock[name][key]
values["libraries"] = len(lock["libraries"])
for index, artifact in enumerate(lock["libraries"]):
    for key in ("path", "sha1", "size", "url"):
        values[f"library.{index}.{key}"] = artifact[key]
output = Path(sys.argv[2])
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text("".join(f"{key}={value}\n" for key, value in values.items()), encoding="ascii")
