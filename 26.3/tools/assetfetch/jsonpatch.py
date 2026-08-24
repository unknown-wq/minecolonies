#!/usr/bin/env python3
# Copyright (C) 2026 the MineColonies Fabric port contributors.
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Minimal RFC 6902 JSON Patch generator/applier used by the asset-fetch patch
# bundle. Deliberately dependency-free (stdlib only) and deliberately biased
# towards SMALL patches: the patch bundle ships in our GPL jar while the base
# documents it edits are All-Rights-Reserved upstream files, so every byte of
# upstream text a patch has to quote is a byte we would rather not quote.

from __future__ import annotations

import json


def _esc(token: str) -> str:
    return str(token).replace("~", "~0").replace("/", "~1")


def _join(path: str, token) -> str:
    return f"{path}/{_esc(token)}"


def _lcs_matrix(a: list, b: list):
    n, m = len(a), len(b)
    # rows of the LCS length table; O(n*m) is fine for model element arrays
    table = [[0] * (m + 1) for _ in range(n + 1)]
    for i in range(n - 1, -1, -1):
        for j in range(m - 1, -1, -1):
            if a[i] == b[j]:
                table[i][j] = table[i + 1][j + 1] + 1
            else:
                table[i][j] = max(table[i + 1][j], table[i][j + 1])
    return table


def _align(a: list, b: list):
    """Return an edit script of ('keep', i, j) / ('del', i, None) / ('ins', None, j)."""
    table = _lcs_matrix(a, b)
    script = []
    i = j = 0
    while i < len(a) and j < len(b):
        if a[i] == b[j]:
            script.append(("keep", i, j))
            i += 1
            j += 1
        elif table[i + 1][j] >= table[i][j + 1]:
            script.append(("del", i, None))
            i += 1
        else:
            script.append(("ins", None, j))
            j += 1
    while i < len(a):
        script.append(("del", i, None))
        i += 1
    while j < len(b):
        script.append(("ins", None, j))
        j += 1
    return script


def make_patch(src, dst, path: str = "") -> list:
    """Build an RFC 6902 patch turning `src` into `dst`."""
    if src == dst:
        return []

    if isinstance(src, dict) and isinstance(dst, dict):
        ops = []
        # removals first, so later add/replace indices are never disturbed
        for key in src:
            if key not in dst:
                ops.append({"op": "remove", "path": _join(path, key)})
        for key, value in dst.items():
            if key not in src:
                ops.append({"op": "add", "path": _join(path, key), "value": value})
            elif src[key] != value:
                ops.extend(make_patch(src[key], value, _join(path, key)))
        return ops

    if isinstance(src, list) and isinstance(dst, list):
        if len(src) == len(dst):
            ops = []
            for idx, (x, y) in enumerate(zip(src, dst)):
                ops.extend(make_patch(x, y, _join(path, idx)))
            return ops
        # Length changed: an LCS alignment keeps the patch to the elements that
        # actually moved instead of restating the whole array.
        return _script_ops(src, dst, path, _align(src, dst))

    return [{"op": "replace", "path": path, "value": dst}] if path else [
        {"op": "replace", "path": "", "value": dst}
    ]


def _script_ops(src: list, dst: list, path: str, script) -> list:
    """Turn an alignment script into ops, simulating the document as we go."""
    ops = []
    work = list(src)
    pos = 0
    for kind, i, j in script:
        if kind == "keep":
            sub = make_patch(work[pos], dst[j], _join(path, pos))
            ops.extend(sub)
            work[pos] = dst[j]
            pos += 1
        elif kind == "del":
            ops.append({"op": "remove", "path": _join(path, pos)})
            del work[pos]
        else:
            ops.append({"op": "add", "path": _join(path, pos), "value": dst[j]})
            work.insert(pos, dst[j])
            pos += 1
    return ops


def _resolve(doc, tokens):
    parent = doc
    for token in tokens[:-1]:
        parent = parent[int(token)] if isinstance(parent, list) else parent[token]
    return parent, tokens[-1]


def _unesc(token: str) -> str:
    return token.replace("~1", "/").replace("~0", "~")


def apply_patch(doc, ops):
    """Apply an RFC 6902 patch (the subset make_patch emits) to `doc`."""
    doc = json.loads(json.dumps(doc))
    for op in ops:
        pointer = op["path"]
        if pointer == "":
            if op["op"] != "replace":
                raise ValueError("only 'replace' is defined on the whole document")
            doc = op["value"]
            continue
        tokens = [_unesc(t) for t in pointer.split("/")[1:]]
        parent, last = _resolve(doc, tokens)
        kind = op["op"]
        if isinstance(parent, list):
            index = len(parent) if last == "-" else int(last)
            if kind == "add":
                parent.insert(index, op["value"])
            elif kind == "replace":
                parent[index] = op["value"]
            elif kind == "remove":
                del parent[index]
            else:
                raise ValueError(f"unsupported op {kind}")
        else:
            if kind in ("add", "replace"):
                parent[last] = op["value"]
            elif kind == "remove":
                del parent[last]
            else:
                raise ValueError(f"unsupported op {kind}")
    return doc
