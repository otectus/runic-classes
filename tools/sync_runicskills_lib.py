#!/usr/bin/env python3
"""Copies the Runic Skills jar the build compiles against into libs/ (read-only on the source).

Runic Skills (../runic-skills) has no Maven coordinate. Run its `build` first; this copies
`build/libs/runicskills-<v>.jar` into libs/ under the exact name the `local.mods` Ivy repository in
build.gradle resolves. The version comes from gradle.properties (`runicskills_version`). `*.jar`
under libs/ is gitignored, so a fresh clone needs this run once before it compiles.

  python3 tools/sync_runicskills_lib.py            # copy when missing or older
  python3 tools/sync_runicskills_lib.py --check    # report only; exit 1 if missing or stale
"""
import argparse
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE_REPO = ROOT.parent / 'runic-skills'


def properties():
    props = {}
    for line in (ROOT / 'gradle.properties').read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            props[key.strip()] = value.strip()
    return props


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true', help='report only; exit 1 if missing or stale')
    args = parser.parse_args()
    version = properties()['runicskills_version']
    name = f'runicskills-{version}.jar'
    source = SOURCE_REPO / 'build' / 'libs' / name
    libs = ROOT / 'libs'
    libs.mkdir(exist_ok=True)
    target = libs / name
    if not source.is_file():
        print(f'MISSING source {source} (build ../runic-skills first)')
        return 1
    stale = not target.is_file() or source.stat().st_mtime > target.stat().st_mtime + 1
    if not stale:
        print(f'ok      {name}')
        return 0
    if args.check:
        print(f'STALE   {name} (from {source})')
        return 1
    shutil.copy2(source, target)
    print(f'copied  {name} <- {source}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
