#!/usr/bin/env python3
# Copyright (C) 2026 the MineColonies Fabric port contributors.
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Independent check on the Phase 0 bundle: replay transforms.json against a copy
# of the upstream asset tree and assert every result equals the port's file.
#
# JSON results are compared as parsed documents (the port's hand-formatting is
# not reproducible and does not matter - the runtime re-serialises canonically);
# XML results are compared byte for byte, since `patch` reproduces them exactly.
#
# Usage: verify_bundle.py --upstream DIR --port DIR --bundle DIR [--work DIR]

from __future__ import annotations

import argparse
import collections
import json
import os
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from composite_flatten import RULES
from jsonpatch import apply_patch


def load_json(path: str):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle, object_pairs_hook=collections.OrderedDict)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", required=True)
    parser.add_argument("--port", required=True)
    parser.add_argument("--bundle", required=True)
    parser.add_argument("--work")
    args = parser.parse_args()

    transforms = load_json(os.path.join(args.bundle, "transforms.json"))
    work = args.work or tempfile.mkdtemp(prefix="assetfetch-verify-")
    failures = []

    for entry in transforms["files"]:
        rel = entry["path"]
        src = os.path.join(args.upstream, rel)
        want = os.path.join(args.port, rel)

        if rel.endswith(".xml"):
            staged = os.path.join(work, rel)
            os.makedirs(os.path.dirname(staged), exist_ok=True)
            shutil.copyfile(src, staged)
            for step in entry["steps"]:
                assert step["op"] == "unifiedDiff", step
                patch = os.path.join(args.bundle, step["patch"])
                result = subprocess.run(
                    ["patch", "--silent", "-p0", staged, patch], capture_output=True, text=True
                )
                if result.returncode != 0:
                    failures.append(f"{rel}: patch failed: {result.stdout}{result.stderr}")
            if open(staged, "rb").read() != open(want, "rb").read():
                failures.append(f"{rel}: patched XML differs from the port file")
            continue

        doc = load_json(src)
        for step in entry["steps"]:
            if step["op"] == "rule":
                doc = RULES[step["rule"]](doc)
            elif step["op"] == "jsonPatch":
                doc = apply_patch(doc, load_json(os.path.join(args.bundle, step["patch"])))
            else:
                failures.append(f"{rel}: unknown step {step['op']}")
        if doc != load_json(want):
            failures.append(f"{rel}: patched JSON differs from the port file")

    for entry in transforms.get("derivedFiles", []):
        rel = entry["path"]
        doc = load_json(os.path.join(args.upstream, entry["copyFrom"]))
        for step in entry["steps"]:
            if step["op"] == "jsonPatch":
                doc = apply_patch(doc, load_json(os.path.join(args.bundle, step["patch"])))
            else:
                failures.append(f"{rel}: unknown step {step['op']}")
        if doc != load_json(os.path.join(args.port, rel)):
            failures.append(f"{rel}: derived JSON differs from the port file")

    total = len(transforms["files"]) + len(transforms.get("derivedFiles", []))
    print(f"verified {total} files "
          f"({len(transforms['files'])} patched + "
          f"{len(transforms.get('derivedFiles', []))} derived); {len(failures)} failure(s)")
    for line in failures:
        print("  FAIL", line)
    if not args.work:
        shutil.rmtree(work, ignore_errors=True)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
