# Test worlds

Two server worlds produced while working on this branch, kept because they are expensive to
recreate and neither can be built from commands alone.

Both were generated **by a dedicated server running this branch's jar**, so they carry mod data.
Open either without MineColonies — or with a different version of it — and at best the colony data is
ignored, at worst the load fails. Use the jar from [`dist/`](../dist) or one built from `26.2/`.

| | `colony-1000.zip` | `boat-arena.zip` |
|---|---|---|
| Colony | **yes** — 1000 citizens, 148 huts | **none** |
| Terrain | default generation, plus the colony | default generation, plus two built arenas |
| Use it for | anything needing a populated colony: boats, pathfinding, load | inspecting the boat pathfinding arenas |
| Size unpacked | ~16 MB | ~14 MB |

## Installing one

```sh
unzip colony-1000.zip -d /path/to/server        # yields <server>/world
cd /path/to/server && java -Xmx6G -jar fabric-server-launch.jar nogui
```

The archive contains a directory called `world`, which is the default `level-name`. To keep several
side by side, unpack under different names and point `level-name` in `server.properties` at the one
you want.

Give the 1000-citizen world **6 GB** — 4 GB is enough to boot it but not to play it.

## `colony-1000.zip` — what is actually in it

Colony 1 at the world spawn: 1000 citizens, the cap `maxcitizenpercolony` allows, and 148 huts. It was
built by an instrumented agent for the measurements in
[`26.2/AI-SCALE-AUDIT.md`](../26.2/audit/AI-SCALE-AUDIT.md), which is why it is lopsided in ways a played
colony would not be, and the report leans on that:

* **871 of the citizens are unemployed** — only 129 have a job, because the hut mix was chosen for
  citizen count rather than for balance. Anything you measure about worker AI is measuring those 129.
* **724 have no bed.** The colony houses 276. They are and will stay unhappy.
* **The buildings have no blueprints**, having been registered rather than built. Builders will not
  behave normally, and the audit's share of unreachable paths is inflated by this.
* Two job types have no building at all: `cookassistant`, `druid`.

So it is a load fixture, not a demonstration colony. For judging whether the AI *behaves* well it is
misleading; for asking what 1000 citizens cost, it is the point.

### It logs 17 errors on first tick, and they are the fixture's fault

Booting this world is silent, but the moment the colony ticks — `/mc colony diagnose 1` is enough —
you get seventeen of these:

```
[Structurize IO Worker #0/ERROR]: Error loading blueprint: Colonial:/blueprints/minecolonies/colonial/null1.blueprint
[Structurize IO Worker #0/ERROR]: Error loading blueprint: Colonial:/blueprints/minecolonies/colonial/fundamentals/miner1.blueprint
```

**This is not a regression in the branch.** The buildings were registered programmatically rather than
built, and they were registered under *job* ids where the pack names its schematics differently: the
Colonial pack's farmer hut is `farm1.blueprint`, not `farmer1.blueprint` — the same in the 1.21.1 tree,
so it is not a porting loss either. `null1.blueprint` is the giveaway: that building has no blueprint
name at all. The Colonial pack itself is intact, all 550 files of it, and the empty world boots with
zero errors.

Expect those seventeen, and treat any *other* error as real.

## `boat-arena.zip` — what is actually in it

**No colony.** Creating one needs a player to place a town hall and there is no client in the build
container, so this world was only ever used for physics and pathfinding tests, which do not need one.
Without a colony there are no citizens, so **you cannot watch a citizen board a boat in this world** —
that is what `colony-1000.zip` is for.

Two structures were built into the terrain, and they are still there:

* a stone-and-water tank around **(100–112, 100–104)** — used to check that a boat floats, that a
  citizen accepts ours and refuses a vanilla one, and that an empty boat discards itself;
* a flattened plateau with a water channel across it, around **(200–265, 96–106, 200–245)** — used to
  prove the pathfinder emits `ENTRY` / `BOAT` / `EXIT` legs across water.

The surrounding terrain could not be restored: the region file holding the arenas also holds spawn.

## What neither world proves

No citizen in either world has ridden a boat from one shore to the other along a path. The boat work is
verified at its inputs (the A\* really emits boat legs) and its outputs (the boat floats, carries a
passenger, steers and cleans itself up), never end to end, because that needs a client. If you are
testing this branch by hand, **that is the gap worth aiming at**.
