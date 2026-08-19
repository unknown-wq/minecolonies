# MineColonies for Fabric — Minecraft 26.2

**MineColonies on Fabric at last.** Found a colony, hire NPC workers, and grow a camp into a city:
builders, farmers, miners, guards, couriers and cooks, blueprint-based construction, raids, a 200+ node
research tree and quests — now running on **Minecraft 26.2** with the **Fabric loader**, in a single jar.

This is a community port of LDTTeam's colony simulator, **plus a set of features and bug fixes made
here** — citizens that sail boats, three land-claim scepters with block-precise colony borders, working
outposts, a farmer that prepares its own ground, and fixes for several bugs that have been in the mod
for years.

| | |
|---|---|
| **Minecraft** | 26.2 |
| **Loader** | Fabric 0.19.3 or newer |
| **Fabric API** | 0.154.2+26.2 (required) |
| **Java** | 25 (a hard requirement of Minecraft 26.2 itself) |
| **Sides** | Client **and** server |
| **License** | GPL-3.0-only |

---

## ⚠️ Unofficial port — read this first

This is an **unofficial, community-made Fabric port**. MineColonies itself is the work of
**[LDTTeam (Let's Dev Together)](https://github.com/ldtteam)**; this port is **not affiliated with,
endorsed by, or supported by them**.

- **Do not send bug reports from this build to LDTTeam.** Use this project's issue tracker.
- If the same bug also happens on the official NeoForge build, it belongs upstream instead.
- Original mod: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecolonies) ·
  [minecolonies.com](https://minecolonies.com/) · [GitHub](https://github.com/ldtteam/minecolonies) ·
  support the authors on [Patreon](https://www.patreon.com/Minecolonies).

Base: upstream branch `version/1.21` (NeoForge 21.1.80, Java 21), carried across **two axes at once** —
NeoForge → Fabric and Minecraft 1.21.1 → 26.2.

---

## 🧱 What MineColonies is

A **colony simulator / town building mod** for Minecraft. You place a town hall, and everything after
that is done by NPC citizens who live in your town:

- **Dozens of jobs** — builder, farmer, miner, lumberjack, fisherman, guard, archer, courier, cook,
  baker, blacksmith, healer, teacher, beekeeper, florist and many more, each with skills that level up.
- **Blueprint building** — huts are placed as previews and built block by block by your builders, from
  several complete architectural styles.
- **A colony economy** — requests, deliveries, warehouses, minimum stock, crafting chains.
- **Research** — a 200+ node tree across four branches, unlocking huts, worker abilities and colony-wide bonuses.
- **Raids and defence** — barbarians, pirates, Egyptians and Norsemen come for your town; guards, towers and walls keep it.
- **Quests, happiness, disease, schooling** and a full simulation underneath it all.

Single player or multiplayer. The mod is required on both the client and the dedicated server.

---

## ✨ Added in this port

Everything below is **new relative to upstream MineColonies** and exists only in this build.

### 🛶 Citizens sail boats

Your citizens can now **cross water by boat instead of walking around the lake** — or refusing to go at all.

- A new colony boat entity, integrated into the mod's pathfinder: open water is priced as a route the
  citizen can actually take, and the path is planned through it.
- The citizen **boards from the bank, steers, and lands on the far shore** by itself. Interrupt the path,
  send it somewhere else, or take the boat away mid-crossing and it gets out cleanly instead of drifting
  off across the lake.
- **Gated behind its own research** — `Boats`, sitting beside `Rails`, costing boats and requiring a
  level 3 Fisherman, which is the building that has any business owning them. Guards drop it while in
  combat, so nobody sails into a fight.
- Empty boats are discarded, and only the citizen a boat was placed for can board it — no fleets of
  abandoned boats on your coastline.

Measured on a live server: an 800-block water route builds a complete path in 7–11 ms; the same route
**on foot, swimming, does not build at all**. Boats turn coastal and island colonies from "the worker
stands there" into a working commute.

### 🗺️ Three claim scepters, and colony borders you can actually draw

- **Land Claim Scepter** — right-click to claim a chunk and the eight around it. No distance rule, so
  your territory can spread wherever you want it, in as many pieces as you like.
- **Land Release Scepter** — the direction the mod never had: **give land back**. One chunk per click,
  or nine while sneaking, exactly undoing one claim click.
- **Border Scepter** — draw the border **inside a chunk**, block by block. Click to add a column, sneak-
  click to cut one out, hold to paint. Follow a river, a wall or a cliff instead of the chunk grid.

The drawn border is **real, not cosmetic**: protection, whether a citizen calls a place home, the raid
spawner and the build tool's preview all follow the line you painted. All three scepters **draw the
colony borders while held**, redrawing live as you edit them, so claiming and releasing land is no
longer guesswork.

### 🏘️ Outposts and enclaves that work

Upstream quietly refuses to let a colony live far from its town hall — building claims, chunk loading
and bed assignment all measured distance to the centre. All three now ask **who owns the ground**
instead:

- A hut placed on your land **claims its own footprint at any distance** (new `maxoutlyingchunks` config).
- An outpost **stays chunk-loaded on its own** (new `maxforcedchunks` config).
- Citizens get a **bed near their work**, not near the town hall — plus `/mc colony rehouse` to move
  citizens who are already living in the wrong place.

Mining outposts, coastal docks and satellite farms are a viable way to play now instead of a bug report.

### 🌾 A farmer that prepares its own ground

- **The farmer terraforms.** A field square it can't hoe because of what's lying there — stone, gravel,
  sand, a path, someone's floor — is no longer skipped in silence. The farmer **clears it, lays down
  dirt and tills it** in the same pass. Water is the one hard exception.
- It **tells you in chat what it cleared**, with a count and a per-block breakdown, so a rectangle drawn
  over the corner of your base is something you read about rather than discover later.
- **Field marker stick** — click the scarecrow, then drag out a rectangle with two clicks and bind it to
  a specific farm right there. No hut GUI, no guessing which farm grabbed which field.
- **Fields of any shape** in free mode: the rule is total area (default 4096 blocks, server-configurable)
  instead of upstream's "sum of four radii ≤ 20", which capped every field at 11×11. Long 4×1000 strips
  along a river are now expressible.

### ⚔️ Raids on demand

The raid command, made usable for testing and for server events:

- **`/mc raid <colony> now` starts immediately** instead of waiting up to three minutes for a slow tick
  and a campfire timer.
- **`size` and `strength`** — ask for 40 raiders at 2.5× health, damage and armour, in one line, without
  naming a raid type first.
- **`where`** tells you where the raiders actually are and how many of them exist as entities right now —
  the answer to "the raid bar is full but the town is empty".
- **`tp`** puts you next to them, **`stop`** calls every raid off and sweeps up raiders that outlived
  their event.
- A raid you asked for by name no longer fails with `NO_SPAWN_POINT` just because your surroundings
  aren't loaded far enough out. Natural raids are untouched.

### 🧰 Free mode — build and test without the grind

`/mc colony freemode <colony> on` makes a colony work **with no items at all**. One switch, saved with
the colony, off by default. It replaces the four scattered hut checkboxes with something that covers
everything they never reached: build materials, tools, weapons, seeds, breeding items, cures, food —
*and* the request system, so crafting recipe inputs, furnace fuel, smeltery ore, school paper and guard
equipment are conjured too. Guards get a real weapon rather than merely being told to stop asking for
one. Workers already stuck waiting for a request resume without a relog.

Ideal for creative builds, screenshots, server showcases and testing a layout before committing to it.

### 🩺 Diagnostics and admin commands

- **`/mc colony diagnose`** — what is stuck and why: every worker's job, AI state and how long it's been
  held, citizens with no AI, requests nobody took, work orders with no builder, inconsistent buildings.
- **`/mc citizens fill`** — populate the colony and hire everyone into an empty job slot.
- **`/mc citizens maxstats`** / **`heal`** — max every skill, or cure and heal everyone at once.
- **`/mc colony research completeall`** — finish the research tree, parents first, no cost, no university.
- **`/mc colony teachRecipes`** — teach every crafter everything it's allowed to learn.

### ⚙️ Configuration

- **`maxcitizenpercolony` now accepts up to 1000** (default unchanged at 250), with the research ladder's
  top rung raised to match — otherwise a config of 1000 was clamped straight back to 500.
- **`stuckrescueseconds`** (new, default 60) teleports a worker to where its job sent it once it has
  spent that long without getting any closer. See below for why that matters.

---

## 🔧 Long-standing bugs fixed here

These are **upstream's bugs** — they reproduce on the official NeoForge build, and each was read line by
line in upstream's own 1.21.1 source before being called upstream's rather than this port's doing. They
are fixed here:

- **"The builder refuses to build."** The "is this order inside the colony" check stepped through the
  building's footprint in a way that, for a 17×17 hut, checked **exactly one chunk — and the wrong one**,
  then reported the coordinates of the hut, which was fine. Now every chunk under the building is checked
  and the message names the chunk that is actually unclaimed.
- **A farm 1000 blocks away stealing a field from the farm next to it.** Field auto-claim never looked at
  distance at all. It does now — and a citizen no longer keeps walking to a field that changed owner.
- **A worker standing still forever.** The navigator's stuck handler only rescues a worker that is walking
  and getting nowhere; it can do nothing for one whose destination has no path at all — nothing moves,
  nothing is logged, no request is outstanding, and the usual fix is breaking the hut. `stuckrescueseconds`
  watches the destination instead of the path and puts the worker there.

Separately, and not upstream's fault: **the farmer never hoeing the ground**, which was 26.2's doing —
vanilla split the `#minecraft:dirt` tag, moving grass, podzol and mud out of it, so the hoeable-surface
test stopped covering the ground every real field is laid on and the farmer walked its whole spiral doing
nothing. Measured on an identical grass scene: 0 of 121 cells tilled before the fix, 80 within three
minutes after.

## ⚡ Performance

The port was profiled on a **real 1000-citizen colony** on a dedicated server, with the pathfinding pool
instrumented live. A round of measured hot-path fixes shipped from it — the server tick no longer walks
every colony for empty callbacks, defensive copies of the citizen list are off the tick paths, GUI lists
stopped refreshing twice, A* stopped reading the world clock per node, courier lookups are hashed, and
request-system logging no longer builds strings behind a disabled flag.

The full measurement — including what is *still* expensive and why — is published in the repository
rather than summarised into a marketing number.

---

## 📥 Installation

1. Install **Fabric Loader 0.19.3+** for **Minecraft 26.2**.
2. Put exactly **two files** in `mods/`:
   - `fabric-api-0.154.2+26.2.jar`
   - `minecolonies-26.2-<version>.jar`
3. Launch. Java 25 is required by Minecraft 26.2 itself.

The jar carries the three required LDTTeam libraries **inside it** via Fabric's Jar-in-Jar:
`blockui`, `structurize` and `domum_ornamentum`, each ported to Fabric 26.2 as well.

### ⚠️ Do not install the libraries separately

**A jar in `mods/` shadows a nested one regardless of version, silently.** The resulting crash points at
source lines that do not exist in the loaded code, and nothing about the substitution appears in the log.

In your mod list, `blockui`, `domum_ornamentum` and `structurize` must appear **indented under
`minecolonies`**. If any of them is at the top level, there is a stray jar in `mods/` — remove it.

---

## ⚠️ Known limitations vs. the official build

Some NeoForge-only hooks have no equivalent in Fabric or in vanilla 26.2. Nothing was deleted — it is
commented and logged.

- **Colony protection:** `PLACE_BLOCKS` / `PLACE_HUTS`, `EXPLODE`, `TOSS_ITEM`, `PICKUP_ITEM`,
  `FILL_BUCKET` and `SHOOT_ARROW` are not enforced. The other eleven permissions, including
  `BREAK_BLOCKS`, `ACCESS_HUTS` and `ATTACK_CITIZEN`, work.
- **Rendering:** the spear renders as a flat item; the scarecrow draws without its lantern; no banner
  placement preview; the fallback citizen model shows player overlays (the mod's own job models are fine).
- **Other:** one `TravellingManager` field reads back as `BlockPos.ZERO` in worlds made before the port;
  restaurant menus parse ingredients only from MineColonies' own recipes; the colony creation particle
  differs.

**Core gameplay — building, jobs, worker AI, pathfinding, raids, research, quests and the request
system — works fully.** The complete list, with what each looks like in game, is in `PORT-STATUS.md`.

This is not a build that was compiled and uploaded. Roughly **three person-days of hands-on play** went
into it on a live client and a dedicated server — colonies founded and grown, huts placed and built,
the hut interfaces driven by hand, workers followed around, raids called in — and several of the fixes
above exist because a player hit the bug and reported it.

---

## ❓ FAQ

**Does MineColonies work on Fabric?**
Officially, no — upstream ships for NeoForge only. This is a community Fabric port of it for Minecraft 26.2.

**Is there a MineColonies version for Minecraft 26.2?**
This one. It is built from upstream's 1.21.1 branch and carried up to 26.2.

**Do I need Structurize, BlockUI and Domum Ornamentum?**
No — Fabric ports of all three are bundled inside the jar. Installing them separately **breaks the game**;
see the warning above.

**Does it work on a dedicated server?**
Yes, and the mod is required on both sides. It has been booted and played on a real Fabric server with
Fabric API alongside it.

**Can I use my old MineColonies world?**
A world from the NeoForge 1.21.1 build is not directly portable — Minecraft 26.2 changes the save format
itself. Start fresh.

**Is it compatible with other Fabric mods?**
Nothing about it is unusually invasive — it uses no mixins at all — but it is a large mod, and conflicts
are always possible. Report them here, with the mod list.

**Where do I report bugs?**
This project's issue tracker on GitHub. Include your Minecraft / Fabric Loader / Fabric API versions, the
full `logs/latest.log` or crash report, your other mods, and the steps to reproduce. If the same bug
happens on the official NeoForge build, report it upstream instead.

---

## 🙏 Credits

**MineColonies is the work of [LDTTeam (Let's Dev Together)](https://github.com/ldtteam)** and its many
contributors — more than a decade of work. Every worker, hut, research branch, blueprint and line of
colony logic originates with them. Please support them on
[Patreon](https://www.patreon.com/Minecolonies) and get the official build from
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecolonies).

| Component | Upstream | This port's source |
|---|---|---|
| MineColonies | [ldtteam/minecolonies](https://github.com/ldtteam/minecolonies) | [unknown-wq/minecolonies](https://github.com/unknown-wq/minecolonies) |
| BlockUI | [ldtteam/BlockUI](https://github.com/ldtteam/BlockUI) | [unknown-wq/BlockUI](https://github.com/unknown-wq/BlockUI) |
| Structurize | [ldtteam/Structurize](https://github.com/ldtteam/Structurize) | [unknown-wq/Structurize](https://github.com/unknown-wq/Structurize) |
| Domum Ornamentum | [ldtteam/Domum-Ornamentum](https://github.com/ldtteam/Domum-Ornamentum) | [unknown-wq/Domum-Ornamentum](https://github.com/unknown-wq/Domum-Ornamentum) |

## 📄 License

Licensed under **GPL-3.0-only**, the same license as every upstream project above, and distributed under
its terms. Complete corresponding source for each released jar is at the links in the table.
