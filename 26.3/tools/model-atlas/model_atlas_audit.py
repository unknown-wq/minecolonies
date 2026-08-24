#!/usr/bin/env python3
"""Atlas-aware audit of every model the mod can hand to the 26.2 model baker.

Why this exists
---------------
26.2 split the single 1.21.x block atlas into thirteen (``AtlasManager.KNOWN_ATLASES``),
and two of the new rules are fatal but only observable in a running client:

* ``SimpleModelWrapper.bake`` -> ``findNonBlockSprites``: a block model is thrown away
  wholesale if any of its baked quads uses a sprite that is not on
  ``minecraft:textures/atlas/blocks.png``.
* ``CuboidItemModelWrapper.validateAtlasUsage``: an item model whose quads span two
  atlases throws, and the item ends up with no model at all.

Both are decidable from the resources alone, which is what this script does. It walks
every blockstate and item definition the mod ships, resolves model -> parent -> texture
slots the way ``TextureSlots.Resolver`` does, works out which atlas each sprite is
stitched into, and applies the two rules above. It also reports the smaller problems the
client only whispers about: unresolved ``#slot`` references, sprites with no atlas at
all, and models that end up with no geometry.

Sprite -> atlas resolution mirrors ``ModelManager.CombinedBlockItemMaterialBaker``: the
items atlas is consulted **first**, the blocks atlas only as a fallback. That ordering is
the reason a sprite cannot be rescued by simply also listing it in the block atlas -- it
has to be stitched under an id that the items atlas does not have.

Usage
-----
    python3 tools/model-atlas/model_atlas_audit.py                     # src tree
    python3 tools/model-atlas/model_atlas_audit.py build/libs/<jar>    # shipped artefact
    NS=structurize python3 tools/model-atlas/model_atlas_audit.py      # another namespace

Run it from the 26.2 project directory. Exit status is non-zero when anything fatal is
found. The script is static: it proves nothing about how the models *look*, only that the
baker will accept them.
"""
import collections
import glob
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RESOURCE_DIRS = [os.path.join(ROOT, "src/main/resources"), os.path.join(ROOT, "src/main/generated")]
LIB_JARS = sorted(glob.glob(os.path.join(ROOT, ".staged-libs/com/ldtteam/*/*/*.jar")))
NS = os.environ.get("NS", "minecolonies")

# Namespaces whose models are audited but whose atlas definitions also matter: every pack
# on the stack contributes sources to the same atlas (SpriteSourceList.load walks the
# whole resource stack), so the atlas contents are the union over all containers.
FATAL = ("REJECTED", "BAKE FAILURE", "missing", "unresolvable", "unresolved")


def find_minecraft_jar():
    base = os.path.join(ROOT, ".gradle/loom-cache/minecraftMaven/net/minecraft")
    for name in sorted(os.listdir(base)):
        jar = os.path.join(base, name, "26.2", name + "-26.2.jar")
        if not os.path.exists(jar):
            continue
        with zipfile.ZipFile(jar) as z:
            if "assets/minecraft/atlases/blocks.json" in z.namelist():
                return jar
    raise SystemExit("no Minecraft jar with assets found under .gradle/loom-cache")


MC_JAR = find_minecraft_jar()
MOD = sys.argv[1] if len(sys.argv) > 1 else None      # None -> read the source tree

# ---------------------------------------------------------------- virtual resource pack
pack = {}          # "assets/<ns>/<path>" -> jar tuple or filesystem path
containers = []    # every container, in stack order, for the atlas union


def add_jar(jar):
    with zipfile.ZipFile(jar) as z:
        names = [n for n in z.namelist() if n.startswith("assets/") and not n.endswith("/")]
    for n in names:
        pack[n] = (jar, n)
    containers.append(("jar", jar, set(names)))


def add_dir(base):
    names = set()
    for dirpath, _, files in os.walk(os.path.join(base, "assets")):
        for f in files:
            p = os.path.join(dirpath, f)
            rel = os.path.relpath(p, base).replace(os.sep, "/")
            pack[rel] = p
            names.add(rel)
    containers.append(("dir", base, names))


add_jar(MC_JAR)
for jar in LIB_JARS:
    add_jar(jar)
if MOD:
    add_jar(MOD)
else:
    for d in RESOURCE_DIRS:
        add_dir(d)


def read(path):
    src = pack.get(path)
    if src is None:
        return None
    if isinstance(src, tuple):
        with zipfile.ZipFile(src[0]) as z:
            return z.read(src[1])
    with open(src, "rb") as fh:
        return fh.read()


def rid(id_, folder, ext=".json"):
    ns, _, path = id_.rpartition(":")
    return "assets/%s/%s/%s%s" % (ns or "minecraft", folder, path, ext)


def norm(id_):
    return id_ if ":" in id_ else "minecraft:" + id_


# ---------------------------------------------------------------------------- atlases
def stitch(atlas_id):
    """Sprite ids that end up on one atlas, unioned over every container."""
    path = rid(atlas_id, "atlases")
    sprites = set()
    for kind, where, names in containers:
        if path not in names:
            continue
        if kind == "jar":
            with zipfile.ZipFile(where) as z:
                doc = json.loads(z.read(path))
        else:
            with open(os.path.join(where, path), "rb") as fh:
                doc = json.load(fh)
        for source in doc.get("sources", []):
            kind_ = source.get("type", "").removeprefix("minecraft:")
            if kind_ == "directory":
                prefix, folder = source["prefix"], source["source"]
                for p in pack:
                    parts = p.split("/")
                    if len(parts) > 3 and parts[0] == "assets" and parts[2] == "textures" and p.endswith(".png"):
                        rest = "/".join(parts[3:])
                        if rest.startswith(folder + "/"):
                            sprites.add("%s:%s%s" % (parts[1], prefix, rest[len(folder) + 1:-4]))
            elif kind_ == "single":
                resource = norm(source["resource"])
                if read(rid(resource, "textures", ".png")) is not None:
                    sprites.add(norm(source.get("sprite", resource)))
            elif kind_ == "paletted_permutations":
                for texture in source["textures"]:
                    for permutation in source["permutations"]:
                        sprites.add(norm(texture) + "_" + permutation)
            elif kind_ == "unstitch":
                for region in source.get("regions", []):
                    sprites.add(norm(region["sprite"]))
            elif kind_ != "filter":
                print("!! unhandled atlas source type: %s" % kind_)
    return sprites


BLOCKS = stitch("minecraft:blocks")
ITEMS = stitch("minecraft:items")


def atlas_of(sprite):
    """CombinedBlockItemMaterialBaker order: items atlas wins over blocks."""
    sprite = norm(sprite)
    if sprite in ITEMS:
        return "items"
    if sprite in BLOCKS:
        return "blocks"
    return None


# ----------------------------------------------------------------------------- models
BUILTIN = {"builtin/generated": "generated", "minecraft:builtin/generated": "generated",
           "builtin/entity": "entity", "minecraft:builtin/entity": "entity"}

model_cache = {}
problems = collections.defaultdict(list)


def report(kind, what, detail=""):
    problems[kind].append((what, detail))


def load_model(id_, referrer):
    if id_ in BUILTIN:
        return {"__builtin__": BUILTIN[id_]}
    if id_ in model_cache:
        return model_cache[id_]
    raw = read(rid(id_, "models"))
    if raw is None:
        model_cache[id_] = None
        report("missing model file", id_, referrer)
        return None
    doc = json.loads(raw)
    model_cache[id_] = doc
    return doc


def chain(id_, referrer):
    out, seen, cur = [], set(), id_
    while cur and cur not in seen:
        seen.add(cur)
        doc = load_model(cur, referrer)
        if doc is None:
            return out, False
        out.append((cur, doc))
        cur = doc.get("parent")
    return out, True


def resolve(slots, key, seen=None):
    """(kind, value); kind is 'sprite', 'unresolved' (cycle) or 'missing' (no such slot)."""
    seen = seen or set()
    if key in seen:
        return "unresolved", key
    seen.add(key)
    if key not in slots:
        return "missing", key
    value = slots[key]
    if isinstance(value, dict):
        value = value.get("sprite")
    if isinstance(value, str) and value.startswith("#"):
        return resolve(slots, value[1:], seen)
    return "sprite", value


def sprites_of(id_, referrer):
    """(sprites used by geometry, particle sprite) or None when the chain is broken."""
    docs, ok = chain(id_, referrer)
    if not ok:
        return None
    slots = {}
    for _, doc in reversed(docs):
        slots.update(doc.get("textures") or {})

    for key in slots:
        kind, value = resolve(slots, key)
        if kind != "sprite":
            report("unresolved texture reference", "%s  #%s -> #%s" % (id_, key, value), referrer)

    used = {}
    geometry = next((doc for _, doc in docs if "elements" in doc), None)
    if geometry is not None:
        for element in geometry.get("elements", []):
            for face in element.get("faces", {}).values():
                ref = face.get("texture")
                if ref is None:
                    continue
                key = ref.lstrip("#")
                kind, value = resolve(slots, key)
                if kind == "sprite":
                    used[key] = value
                else:
                    report("face uses unresolvable slot", "%s  #%s" % (id_, key), referrer)
    else:
        builtin = docs[-1][1].get("__builtin__") if docs else None
        if builtin == "generated":
            # ItemModelGenerator traces the quads out of layer0..layerN
            for key in sorted(k for k in slots if k.startswith("layer")):
                kind, value = resolve(slots, key)
                if kind == "sprite":
                    used[key] = value
        elif builtin != "entity":       # 'entity' is drawn by a renderer, by design
            report("model has no geometry", id_, referrer)

    kind, value = resolve(slots, "particle")
    return used, (value if kind == "sprite" else None)


def check_block_model(id_, referrer):
    found = sprites_of(id_, referrer)
    if found is None:
        return
    used, particle = found
    foreign = {}
    for sprite in used.values():
        atlas = atlas_of(sprite)
        if atlas is None:
            report("missing texture (no atlas)", "%s -> %s" % (id_, sprite), referrer)
        elif atlas != "blocks":
            foreign.setdefault(atlas, set()).add(sprite)
    if foreign:
        report("BLOCK MODEL REJECTED (sprite outside the block atlas)", id_,
               "; ".join("%s: %s" % (a, ", ".join(sorted(s))) for a, s in foreign.items()))
    if particle is not None:
        atlas = atlas_of(particle)
        if atlas is None:
            report("missing particle texture", "%s -> %s" % (id_, particle), referrer)
        elif atlas != "blocks":
            report("particle sprite outside the block atlas", "%s -> %s" % (id_, particle), referrer)


def check_item_model(id_, referrer):
    found = sprites_of(id_, referrer)
    if found is None:
        return
    used, _ = found
    by_atlas = {}
    for sprite in used.values():
        atlas = atlas_of(sprite)
        if atlas is None:
            report("missing texture (no atlas)", "%s -> %s" % (id_, sprite), referrer)
        else:
            by_atlas.setdefault(atlas, set()).add(sprite)
    if len(by_atlas) > 1:
        report("ITEM MODEL BAKE FAILURE (quads span two atlases)", id_,
               "; ".join("%s: %s" % (a, ", ".join(sorted(s))) for a, s in by_atlas.items()))


def collect_model_refs(node, out):
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "model" and isinstance(value, str):
                out.add(value)
            else:
                collect_model_refs(value, out)
    elif isinstance(node, list):
        for value in node:
            collect_model_refs(value, out)


def walk(folder, checker, label):
    base = "assets/%s/%s/" % (NS, folder)
    names = sorted(p for p in pack if p.startswith(base) and p.endswith(".json"))
    print("%-22s %4d files" % (label, len(names)))
    reached = set()
    for path in names:
        refs = set()
        collect_model_refs(json.loads(read(path)), refs)
        if not refs:
            # a definition that draws through a renderer instead (minecraft:special)
            report("no model reference", path, label)
        for ref in sorted(refs):
            checker(ref, path)
            reached.add(ref)
    return reached


reached = walk("blockstates", check_block_model, "blockstates")
walk("items", check_item_model, "item definitions")

# Anything under models/block/ that no blockstate names is still audited: a defect there
# stays silent until some other pack or a later change starts referencing it.
prefix = "assets/%s/models/block/" % NS
shipped = sorted(p for p in pack if p.startswith(prefix) and p.endswith(".json"))
orphans = ["%s:%s" % (NS, p[len("assets/%s/models/" % NS):-5]) for p in shipped]
orphans = [m for m in orphans if m not in reached]
print("%-22s %4d models under models/block/ (%d unreferenced)" % ("standalone", len(shipped), len(orphans)))
for model in orphans:
    check_block_model(model, "<unreferenced models/block>")

print("\nblock atlas: %d sprites   item atlas: %d sprites\n" % (len(BLOCKS), len(ITEMS)))

status = 0
for kind in sorted(problems):
    seen = set()
    unique = [e for e in problems[kind] if not (e[0] in seen or seen.add(e[0]))]
    print("=== %s (%d) ===" % (kind, len(unique)))
    for what, detail in unique:
        print("  %-64s  %s" % (what, detail))
    print()
    if any(marker in kind for marker in FATAL):
        status = 1
sys.exit(status)
