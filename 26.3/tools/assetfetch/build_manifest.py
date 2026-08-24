#!/usr/bin/env python3
# WP1 manifest generator (scratch tool - not committed).
#
# Materialises the COMPLETE installed pack from a tree extracted out of an
# upstream jar by REUSING the repo's own bundle tools (composite_flatten.py,
# jsonpatch.py, gen_bundle.canonical_dumps, patch(1) for XML), then emits
# assetfetch/manifest.json in the orchestrator-fixed format.
#
# Usage: build_manifest.py --primary DIR --alt DIR --bundle DIR --pack-out DIR --out FILE

from __future__ import annotations
import argparse, collections, hashlib, json, os, shutil, subprocess, sys

TOOLS = "/workspace/minecolonies-fabric/26.2/tools/assetfetch"
sys.path.insert(0, TOOLS)
from composite_flatten import RULES            # noqa: E402
from jsonpatch import apply_patch              # noqa: E402
from gen_bundle import canonical_dumps         # noqa: E402

PACK_PREFIX = "assets/minecolonies/"


def load_json(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh, object_pairs_hook=collections.OrderedDict)


def sha256_file(path):
    d = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            d.update(chunk)
    return d.hexdigest()


def walk(root):
    for base, _dirs, names in os.walk(root):
        for name in names:
            full = os.path.join(base, name)
            yield os.path.relpath(full, root)


def install(src_tree, bundle, pack_out):
    """Extract-verbatim + patch + derive, exactly as the runtime installer must."""
    if os.path.exists(pack_out):
        shutil.rmtree(pack_out)
    root = os.path.join(pack_out, "assets", "minecolonies")
    shutil.copytree(src_tree, root)

    transforms = load_json(os.path.join(bundle, "transforms.json"))
    patched, derived = [], []

    for entry in transforms["files"]:
        rel = entry["path"]
        target = os.path.join(root, rel)
        if rel.endswith(".xml"):
            for step in entry["steps"]:
                assert step["op"] == "unifiedDiff", step
                res = subprocess.run(
                    ["patch", "--silent", "-p0", target,
                     os.path.join(bundle, step["patch"])],
                    capture_output=True, text=True)
                if res.returncode != 0:
                    raise SystemExit(f"patch failed for {rel}: {res.stdout}{res.stderr}")
            patched.append(rel)
            continue
        doc = load_json(target)
        for step in entry["steps"]:
            if step["op"] == "rule":
                doc = RULES[step["rule"]](doc)
            elif step["op"] == "jsonPatch":
                doc = apply_patch(doc, load_json(os.path.join(bundle, step["patch"])))
            else:
                raise SystemExit(f"unknown step {step['op']} for {rel}")
        with open(target, "w", encoding="utf-8") as fh:
            fh.write(canonical_dumps(doc))
        patched.append(rel)

    for entry in transforms.get("derivedFiles", []):
        rel = entry["path"]
        doc = load_json(os.path.join(root, entry["copyFrom"]))
        for step in entry["steps"]:
            if step["op"] == "jsonPatch":
                doc = apply_patch(doc, load_json(os.path.join(bundle, step["patch"])))
            else:
                raise SystemExit(f"unknown step {step['op']} for {rel}")
        target = os.path.join(root, rel)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "w", encoding="utf-8") as fh:
            fh.write(canonical_dumps(doc))
        derived.append(rel)

    return patched, derived


def hash_pack(pack_out):
    root = os.path.join(pack_out, "assets", "minecolonies")
    out = {}
    for rel in walk(root):
        full = os.path.join(root, rel)
        out[PACK_PREFIX + rel.replace(os.sep, "/")] = {
            "sha256": sha256_file(full), "size": os.path.getsize(full)}
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--primary", required=True)
    ap.add_argument("--alt", required=True)
    ap.add_argument("--bundle", required=True)
    ap.add_argument("--pack-out", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--meta", required=True)
    args = ap.parse_args()

    p_patched, p_derived = install(args.primary, args.bundle, args.pack_out + "/primary")
    a_patched, a_derived = install(args.alt, args.bundle, args.pack_out + "/alt")
    print(f"primary: {len(p_patched)} patched + {len(p_derived)} derived")
    print(f"alt:     {len(a_patched)} derived-equal check {len(a_derived)}")

    primary = hash_pack(args.pack_out + "/primary")
    alt = hash_pack(args.pack_out + "/alt")

    alt_section = collections.OrderedDict()
    for path in sorted(set(primary) | set(alt)):
        if path not in alt:
            alt_section[path] = None
        elif path not in primary or alt[path] != primary[path]:
            alt_section[path] = alt[path]

    manifest = collections.OrderedDict()
    manifest["version"] = 1
    manifest["primarySource"] = "maven-1374"
    manifest["sources"] = collections.OrderedDict([
        ("maven-1374", collections.OrderedDict([
            ("jarSha256", "9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa"),
            ("size", 78071143),
            ("url", "https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/"
                    "1.1.1374-1.21.1-snapshot/minecolonies-1.1.1374-1.21.1-snapshot.jar")])),
        ("maven-1368", collections.OrderedDict([
            ("jarSha256", "c3a2542aaced85aabfc58b38415b70e6b095a16787056e07880fc94320f09a9b"),
            ("size", 77945293),
            ("url", "https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/"
                    "1.1.1368-1.21.1/minecolonies-1.1.1368-1.21.1.jar")])),
    ])
    manifest["files"] = collections.OrderedDict(
        (k, collections.OrderedDict([("sha256", primary[k]["sha256"]), ("size", primary[k]["size"])]))
        for k in sorted(primary))
    manifest["alt"] = collections.OrderedDict([("maven-1368", collections.OrderedDict(
        (k, None if v is None else collections.OrderedDict(
            [("sha256", v["sha256"]), ("size", v["size"])]))
        for k, v in alt_section.items()))])

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")

    os.makedirs(args.meta, exist_ok=True)
    with open(os.path.join(args.meta, "wp1-patched-sha256.json"), "w", encoding="utf-8") as fh:
        fh.write(json.dumps(
            {rel: primary[PACK_PREFIX + rel]["sha256"] for rel in p_patched + p_derived},
            indent=2, sort_keys=True) + "\n")

    print(f"manifest: {len(manifest['files'])} files, "
          f"{len(manifest['alt']['maven-1368'])} alt entries")
    for k, v in manifest["alt"]["maven-1368"].items():
        print("  alt:", k, "ABSENT" if v is None else v["sha256"])


if __name__ == "__main__":
    raise SystemExit(main())
