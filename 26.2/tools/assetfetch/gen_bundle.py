#!/usr/bin/env python3
# Copyright (C) 2026 the MineColonies Fabric port contributors.
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Phase 0 generator. Diffs the port's former `assets/minecolonies` tree against
# the upstream baseline tree and emits everything the runtime asset fetcher needs
# to reproduce the port's edits on top of files it downloads at install time:
#
#   * patches/**.jsonpatch  - RFC 6902 patches for edited .json files
#   * patches/**.diff       - `diff -U0` unified diffs for edited .xml files
#   * transforms.json       - the recipe: which steps run on which file
#   * lang/en_us.json       - the port-owned language keys (added + rewritten)
#
# NOTHING upstream is copied wholesale. The patches quote only the fragments the
# port actually changed; the composite-model flattening is emitted as a RULE the
# runtime recomputes rather than as a stored patch.
#
# Usage:  gen_bundle.py --upstream DIR --port DIR --bundle DIR --meta DIR

from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
import shutil
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from composite_flatten import FLATTEN_RULE_ID, flatten_composite, is_composite
from jsonpatch import apply_patch, make_patch

LANG_SOURCE = "lang/manual_en_us.json"

# --- classification of the 10 files the port ADDED under assets/minecolonies ---
#
# Six are the port's own work and ship inside our jar. Four are near-copies of one
# upstream model (`scepterborder.json` is byte-identical to it), so they are
# derivative of an All-Rights-Reserved file and must NOT ship: they are rebuilt at
# install time from the fetched original, exactly like the 65 modified files.
# `gen_bundle.py` re-derives the evidence for this split on every run and refuses
# to continue if an entry stops matching.

SHIPPED_ADDED = [
    "items/scepterclaim.json",              # 26.2 item-definition, no 1.21.1 counterpart
    "models/item/fieldstick.json",          # 4-line vanilla handheld model
    "textures/block/transparent.png",       # 16x16 fully transparent
    "textures/misc/rack_empty.png",         # 16x16 fully transparent
    "textures/item/spawn_egg.png",          # 16x16 placeholder, 3 opaque greys
    "textures/item/spawn_egg_overlay.png",  # 16x16 placeholder, white only
]

DERIVED_ADDED = {
    "models/item/scepterborder.json": "models/item/scepterpermission.json",
    "models/item/scepterclaim.json": "models/item/scepterpermission.json",
    "models/item/scepterterritory.json": "models/item/scepterpermission.json",
    "models/item/scepterunclaim.json": "models/item/scepterpermission.json",
}

# Canonical serialisation of a patched JSON document. The runtime patcher and
# the Phase 1 manifest generator MUST both produce exactly these bytes, or the
# manifest hashes will not match what is on disk. See WP0-REPORT.md.
CANONICAL_JSON = {
    "encoding": "UTF-8",
    "indent": 2,
    "keyValueSeparator": ": ",
    "itemSeparator": ",",
    "escapeNonAscii": False,
    "sortKeys": False,
    "keyOrder": "document order after patching",
    "trailingNewline": True,
    "numbers": "re-emitted from the source literal; do not re-format",
}


def canonical_dumps(doc) -> str:
    return json.dumps(doc, indent=2, ensure_ascii=False, separators=(",", ": ")) + "\n"


def load_json(path: str):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle, object_pairs_hook=collections.OrderedDict)


def walk(root: str):
    for base, _dirs, names in os.walk(root):
        for name in names:
            full = os.path.join(base, name)
            yield os.path.relpath(full, root)


def sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def classify(upstream: str, port: str):
    up = set(walk(upstream))
    po = set(walk(port))
    added = sorted(po - up)
    removed = sorted(up - po)
    modified = sorted(
        p for p in (up & po)
        if open(os.path.join(upstream, p), "rb").read() != open(os.path.join(port, p), "rb").read()
    )
    return added, removed, modified


def write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--upstream", required=True)
    parser.add_argument("--port", required=True)
    parser.add_argument("--bundle", required=True, help="assetfetch/ resource dir to write")
    parser.add_argument("--lang-out", required=True, help="port-owned lang/en_us.json path")
    parser.add_argument("--added-out", required=True,
                        help="assets/minecolonies dir the port-authored added files go to")
    parser.add_argument("--meta", required=True, help="non-shipped report data dir")
    args = parser.parse_args()

    added, removed, modified = classify(args.upstream, args.port)
    print(f"delta: {len(added)} added / {len(modified)} modified / {len(removed)} deleted")
    if (len(added), len(modified), len(removed)) != (10, 66, 0):
        print("STOP: delta does not match the expected 10/66/0", file=sys.stderr)
        return 2

    patch_root = os.path.join(args.bundle, "patches")
    entries = []
    stats = collections.Counter()
    expected = {}

    for rel in modified:
        if rel == LANG_SOURCE:
            stats["lang(not shipped as a patch)"] += 1
            continue
        up_path = os.path.join(args.upstream, rel)
        po_path = os.path.join(args.port, rel)

        if rel.endswith(".json"):
            raw = load_json(up_path)
            dst = load_json(po_path)
            steps = []
            flattened = is_composite(raw)
            base = flatten_composite(raw) if flattened else raw
            if flattened:
                steps.append({"op": "rule", "rule": FLATTEN_RULE_ID})
            ops = make_patch(base, dst)
            if ops:
                write(os.path.join(patch_root, f"{rel}.jsonpatch"),
                      json.dumps(ops, indent=2, ensure_ascii=False) + "\n")
                steps.append({"op": "jsonPatch", "patch": f"patches/{rel}.jsonpatch"})
                stats["flatten + json patch" if flattened else "json patch"] += 1
            else:
                stats["flatten only (rule, no stored patch)"] += 1
            if apply_patch(base, ops) != dst:
                print(f"STOP: patch for {rel} does not reproduce the port file", file=sys.stderr)
                return 3
            entries.append({"path": rel, "steps": steps})
            expected[rel] = hashlib.sha256(canonical_dumps(dst).encode()).hexdigest()

        elif rel.endswith(".xml"):
            diff = subprocess.run(
                ["diff", "-U0", "--label", f"a/{rel}", "--label", f"b/{rel}", up_path, po_path],
                capture_output=True, text=True,
            ).stdout
            patch_rel = f"patches/{rel}.diff"
            write(os.path.join(patch_root, f"{rel}.diff"), diff)
            entries.append({"path": rel, "steps": [{"op": "unifiedDiff", "patch": patch_rel}]})
            stats["xml unified diff"] += 1
            expected[rel] = sha256(po_path)

        else:
            print(f"STOP: no patch strategy for {rel}", file=sys.stderr)
            return 4

    # ---- the 10 added files ------------------------------------------
    if sorted(SHIPPED_ADDED + list(DERIVED_ADDED)) != sorted(added):
        print("STOP: the added-file classification no longer covers the delta", file=sys.stderr)
        return 5

    up_by_hash = collections.defaultdict(list)
    for rel in walk(args.upstream):
        up_by_hash[sha256(os.path.join(args.upstream, rel))].append(rel)
    added_report = {}
    for rel in added:
        digest = sha256(os.path.join(args.port, rel))
        added_report[rel] = {
            "size": os.path.getsize(os.path.join(args.port, rel)),
            "sha256": digest,
            "identicalUpstreamFiles": sorted(up_by_hash.get(digest, [])),
            "verdict": "ship" if rel in SHIPPED_ADDED else "derive-at-install",
        }

    derived_entries = []
    for rel, base_rel in sorted(DERIVED_ADDED.items()):
        base = load_json(os.path.join(args.upstream, base_rel))
        dst = load_json(os.path.join(args.port, rel))
        ops = make_patch(base, dst)
        if apply_patch(base, ops) != dst:
            print(f"STOP: derivation of {rel} does not reproduce the port file", file=sys.stderr)
            return 6
        steps = []
        if ops:
            write(os.path.join(patch_root, f"{rel}.jsonpatch"),
                  json.dumps(ops, indent=2, ensure_ascii=False) + "\n")
            steps.append({"op": "jsonPatch", "patch": f"patches/{rel}.jsonpatch"})
        derived_entries.append({"path": rel, "copyFrom": base_rel, "steps": steps})
        added_report[rel]["derivedFrom"] = base_rel
        added_report[rel]["derivationOps"] = len(ops)
        expected[rel] = hashlib.sha256(canonical_dumps(dst).encode()).hexdigest()

    for rel in SHIPPED_ADDED:
        target = os.path.join(args.added_out, rel)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(os.path.join(args.port, rel), target)

    transforms = collections.OrderedDict()
    transforms["formatVersion"] = 1
    transforms["comment"] = (
        "Recipe for reproducing this port's edits on top of the upstream asset "
        "files fetched at install time. `files` edits a fetched file in place; "
        "`derivedFiles` writes a NEW pack file starting from `copyFrom`. Steps "
        "run in order. Patches quote only the changed fragments; the composite "
        "flattening is a rule the runtime recomputes rather than a stored patch."
    )
    transforms["canonicalJson"] = CANONICAL_JSON
    transforms["rules"] = {
        FLATTEN_RULE_ID: {
            "description": (
                "Collapse a NeoForge `neoforge:composite` model into a plain vanilla "
                "model: drop `loader` and `children`; merge each child's `textures` "
                "into the root map in document order (later child wins); append each "
                "child's `elements` to the root list; discard child-only keys "
                "(`render_type`, `parent`, `groups`)."
            )
        }
    }
    transforms["files"] = sorted(entries, key=lambda e: e["path"])
    transforms["derivedFiles"] = derived_entries
    write(os.path.join(args.bundle, "transforms.json"),
          json.dumps(transforms, indent=2, ensure_ascii=False) + "\n")

    # ---- language split ------------------------------------------------
    up_lang = load_json(os.path.join(args.upstream, LANG_SOURCE))
    po_lang = load_json(os.path.join(args.port, LANG_SOURCE))
    lang_added = [k for k in po_lang if k not in up_lang]
    lang_changed = [k for k in po_lang if k in up_lang and po_lang[k] != up_lang[k]]
    lang_removed = [k for k in up_lang if k not in po_lang]
    owned = collections.OrderedDict(
        (k, po_lang[k]) for k in po_lang if k in set(lang_added) | set(lang_changed)
    )
    write(args.lang_out, json.dumps(owned, indent=2, ensure_ascii=False) + "\n")

    os.makedirs(args.meta, exist_ok=True)
    write(os.path.join(args.meta, "delta.json"), json.dumps({
        "added": added, "removed": removed, "modified": modified,
        "counts": {"added": len(added), "modified": len(modified), "deleted": len(removed)},
    }, indent=2) + "\n")
    write(os.path.join(args.meta, "lang-split.json"), json.dumps({
        "upstreamKeys": len(up_lang), "portKeys": len(po_lang),
        "addedKeys": len(lang_added), "rewrittenKeys": len(lang_changed),
        "removedKeys": len(lang_removed), "portOwnedKeys": len(owned),
        "rewritten": {k: {"upstream": up_lang[k], "port": po_lang[k]} for k in lang_changed},
    }, indent=2, ensure_ascii=False) + "\n")
    write(os.path.join(args.meta, "added-files.json"),
          json.dumps(added_report, indent=2, sort_keys=True) + "\n")
    write(os.path.join(args.meta, "expected-patched-sha256.json"),
          json.dumps(expected, indent=2, sort_keys=True) + "\n")

    print("patch inventory:")
    for kind, count in sorted(stats.items()):
        print(f"  {kind}: {count}")
    print(f"  files in transforms.json: {len(entries)}")
    print(f"added files: {len(SHIPPED_ADDED)} shipped in the jar, "
          f"{len(derived_entries)} derived at install time")
    print(f"lang: upstream {len(up_lang)} keys, port {len(po_lang)} keys, "
          f"{len(lang_added)} added + {len(lang_changed)} rewritten = {len(owned)} port-owned")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
