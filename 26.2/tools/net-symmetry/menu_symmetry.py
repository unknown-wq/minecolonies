#!/usr/bin/env python3
"""Cross-check, from the real sources, that every menu's screen-opening writer emits the same
primitive sequence its container factory reads."""
import re, sys, os

ROOT = os.environ.get("MC_ROOT", "/home/user/minecolonies/26.2/src/main/java/com/minecolonies")

def body_after(src, pattern):
    m = re.search(pattern, src)
    if not m:
        return None
    i = src.index('{', m.end() - 1)
    d = 0
    for j in range(i, len(src)):
        if src[j] == '{': d += 1
        elif src[j] == '}':
            d -= 1
            if d == 0:
                return src[i:j + 1]
    return None

def ops(body, kind):
    # ordered sequence of buffer primitive calls
    if body is None:
        return None
    return re.findall(r'\.(' + kind + r'[A-Za-z]+)\s*\(', body)

def read(path):
    return open(os.path.join(ROOT, path)).read()

# --- writers: the ExtendedMenuProvider#getScreenOpeningData bodies ---------------------------------
writers = {}

tile_rack = read("core/tileentities/TileEntityRack.java")
writers["rack_inv"] = ops(body_after(tile_rack, r'getScreenOpeningData\s*\([^)]*\)'), "write")

tile_grave = read("core/tileentities/TileEntityGrave.java")
writers["grave_inv"] = ops(body_after(tile_grave, r'getScreenOpeningData\s*\([^)]*\)'), "write") \
    or writers["rack_inv"]

tile_building = read("core/tileentities/TileEntityColonyBuilding.java")
# TileEntityColonyBuilding and TileEntityGrave both extend TileEntityRack: with no override of their own
# they inherit the rack's writer, which is a real mismatch rather than a missing one.
writers["building_inv"] = ops(body_after(tile_building, r'getScreenOpeningData\s*\([^)]*\)'), "write") \
    or writers["rack_inv"]

open_inv = read("core/network/messages/server/colony/OpenInventoryMessage.java")
writers["citizen_inv"] = ops(body_after(open_inv, r'getScreenOpeningData\s*\([^)]*\)'), "write")

# three anonymous providers in one file, in registration order furnace / brewingstand / crafting
craft = read("core/network/messages/server/colony/building/OpenCraftingGUIMessage.java")
craft_bodies = []
pos = 0
while True:
    m = re.compile(r'getScreenOpeningData\s*\([^)]*\)').search(craft, pos)
    if not m:
        break
    b = body_after(craft[m.start():], r'getScreenOpeningData\s*\([^)]*\)')
    craft_bodies.append(b)
    pos = m.end()
assert len(craft_bodies) == 3, craft_bodies
writers["crafting_furnace"], writers["crafting_brewingstand"], writers["crafting_building"] = \
    [ops(b, "write") for b in craft_bodies]

# --- readers: the container factories -------------------------------------------------------------
CONTAINER = {
    "rack_inv": "ContainerRack",
    "grave_inv": "ContainerGrave",
    "citizen_inv": "ContainerCitizenInventory",
    "building_inv": "ContainerBuildingInventory",
    "crafting_furnace": "ContainerCraftingFurnace",
    "crafting_brewingstand": "ContainerCraftingBrewingstand",
    "crafting_building": "ContainerCrafting",
}

readers = {}
for menu, cls in CONTAINER.items():
    src = read("api/inventory/container/%s.java" % cls)
    body = body_after(src, r'static\s+\w+\s+fromFriendlyByteBuf\s*\([^)]*\)')
    seq = ops(body, "read")
    if not seq:
        # ContainerGrave delegates to a constructor taking the buffer
        ctor = body_after(src, r'public\s+%s\s*\(\s*final int windowId[^)]*RegistryFriendlyByteBuf[^)]*\)' % cls)
        seq = ops(ctor, "read")
    readers[menu] = seq

MIRROR = {"writeBlockPos": "readBlockPos", "writeVarInt": "readVarInt", "writeInt": "readInt",
          "writeBoolean": "readBoolean", "writeUtf": "readUtf", "writeLong": "readLong",
          "writeByte": "readByte", "writeDouble": "readDouble", "writeFloat": "readFloat"}

bad = 0
print("%-24s %-42s %-42s %s" % ("menu", "writes", "reads", "verdict"))
for menu in CONTAINER:
    w, r = writers[menu], readers[menu]
    if w is None:
        w, ok = ["<NO WRITER>"], False
    else:
        ok = [MIRROR.get(x, "?" + x) for x in w] == r
    if not ok:
        bad += 1
    print("%-24s %-42s %-42s %s" % (menu, ",".join(w), ",".join(r), "MATCH" if ok else "MISMATCH"))

print()
print("ALL MENUS SYMMETRIC" if bad == 0 else "%d MENU(S) ASYMMETRIC" % bad)
sys.exit(0 if bad == 0 else 1)
