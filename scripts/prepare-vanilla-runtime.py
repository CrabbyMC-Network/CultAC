#!/usr/bin/env python3
"""Materialize the pinned vanilla model inputs; never launch the Minecraft client."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile
import urllib.request


def matches(path, entry):
    if not path.is_file() or path.stat().st_size != entry['size']:
        return False
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha1').hexdigest() == entry['sha1']


def materialize(entry, destination, cached):
    if matches(destination, entry):
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(dir=destination.parent, prefix='.download-')
    os.close(descriptor)
    temporary = Path(temporary)
    try:
        if cached is not None and matches(cached, entry):
            shutil.copyfile(cached, temporary)
        else:
            with urllib.request.urlopen(entry['url'], timeout=60) as source, temporary.open('wb') as target:
                shutil.copyfileobj(source, target)
        if not matches(temporary, entry):
            raise ValueError(f"Checksum mismatch: {entry['url']}")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--client-cache', type=Path)
    parser.add_argument('--library-cache', type=Path)
    args = parser.parse_args()
    lock = json.loads(args.lock.read_text())
    materialize(lock['client'], args.output / 'client.jar', args.client_cache)
    for entry in lock['libraries']:
        path = Path(entry['path'])
        if path.is_absolute() or '..' in path.parts:
            raise ValueError(f'Invalid artifact path: {path}')
        materialize(entry, args.output / 'lib' / path,
                    args.library_cache / path if args.library_cache else None)
    print(f"Verified vanilla {lock['version']} and {len(lock['libraries'])} libraries")


if __name__ == '__main__':
    main()
