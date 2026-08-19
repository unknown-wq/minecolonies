#!/usr/bin/env python3
# Copyright (C) 2026 the MineColonies Fabric port contributors.
# SPDX-License-Identifier: GPL-3.0-or-later
#
# The one mechanical transform in the asset-fetch bundle.
#
# Upstream (NeoForge) writes some hut block models with the `neoforge:composite`
# model loader: a root model plus a `children` map of sub-models, each with its
# own `textures`/`elements`. Fabric has no such loader, so the port flattened
# every one of them into a single plain vanilla model. That flattening is purely
# mechanical, which means the runtime can RECOMPUTE it from the fetched upstream
# file instead of shipping a stored patch full of upstream geometry.

FLATTEN_RULE_ID = "neoforge-composite-flatten"


def is_composite(model) -> bool:
    return isinstance(model, dict) and model.get("loader") == "neoforge:composite"


def flatten_composite(model: dict) -> dict:
    """Collapse a `neoforge:composite` model into a plain vanilla block model.

    Rule, in order:
      1. copy every root key except `loader` and `children`;
      2. walk `children` in document order, merging each child's `textures` into
         the root `textures` (a later child wins on a repeated key) and appending
         each child's `elements` to the root `elements`;
      3. drop every other child-only key (`render_type`, `parent`, `groups`) —
         they exist only to describe a sub-model that no longer exists.
    """
    result = {k: v for k, v in model.items() if k not in ("loader", "children")}
    textures = dict(result.get("textures", {}))
    elements = list(result.get("elements", []))
    for child in model.get("children", {}).values():
        textures.update(child.get("textures", {}))
        elements.extend(child.get("elements", []))
    if textures:
        result["textures"] = textures
    if elements:
        result["elements"] = elements
    return result


RULES = {FLATTEN_RULE_ID: flatten_composite}
