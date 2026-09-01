# Running a colony with nobody logged in

A colony normally ticks only while somebody can see it. `Colony#updateState`
reaches `ACTIVE` through a close subscriber — a connected `ServerPlayer` — or
through a loaded claim plus an important colony player. On a server nobody plays
on, every colony sits at `INACTIVE`: the work manager never runs, a work order is
never handed to a builder, and the force-load timer is never refreshed, so the
ground under the colony stops ticking and its citizens stop existing.

Headless mode answers *should this colony run* without asking *is somebody
looking at it*. It exists for automated runs — AI and pathfinding load, build
regression, long soak tests — on a server with no client attached.

`COMMANDS.md` documents what the mode changes inside the colony and why it is not
a fake player. This file is the runbook: how to get from an empty server to a
colony that is actually laying blocks.

## 1. Arm the server

Headless mode is off unless four separate things are true, and an ordinary
install does none of them.

**Dedicated server only.** An integrated server — singleplayer, or a world opened
to LAN — is refused even with everything else in place.

**Start the JVM with the property:**

```
java -Dminecolonies.headless=true -Xmx4G -jar fabric-server-launch.jar nogui
```

The property is read once at class initialisation. There is no config key, no
command and no packet that can set it. Without it the `headless` literal is not
added to the command tree at all: `/mc debug headless` is an unknown command, and
there is nothing to tab-complete.

A `WARN` appears at startup whenever the JVM is armed, whether or not the mode
gets switched on, so an operator who inherited a start script finds out from the
log.

**Be an operator.** The command takes full command permissions.

**Switch it on explicitly:**

```
/mc debug headless on
```

Off is the state the server comes up in. Nothing about the mode is written to a
colony's NBT, to the server config, or to any other file, so this has to be run
again after every restart. That is deliberate: a flag persisted in saved data
would travel with a world backup onto somebody else's server and quietly keep
their colonies ticking.

## 2. Keep the ground loaded

Headless mode makes a colony *tick*. It does not force-load anything by itself —
refreshing the force-load timer is what an officer's presence buys, and which
chunks a running timer then tickets is still decided by `colonyloadstrictness`
and `forceloadallclaims`.

On a server with genuinely nobody in the world, pair the mode with:

```
/mc colony forceloadclaims <colony> on
```

Without it the colony ticks and its citizens have nothing ticking under them. The
command says as much when you switch the mode on.

## 3. Raise a colony from an empty world

Founding a colony normally goes through a player right-clicking a town hall with
the build tool. Two console commands stand in for that.

```
/mc colony found Testville 0 67 0
/mc colony freemode 1 on
```

`found` creates the colony in save data, claims the usual square of chunks and
registers the town hall as the first building. It builds nothing — the town hall
is placed at level 0 with a blueprint recorded but not raised, exactly as a
freshly placed hut is. The owner is left `[abandoned]`, because there is nobody
to be the owner.

Free mode is required for what follows: the level argument on `hut` goes through
`requestUpgradeTo`, and both the direct jump to a level and the materials for it
need free mode on.

Then place huts. **A builder's hut has to come first** — the first hut of all is
refused a work order, because the colony has no builder within reach of the site
yet:

```
/mc colony hut 1 "minecolonies:blockhutbuilder" -20 67 0 "fundamentals/builder1.blueprint" 5
```

Run the same call a second time over an existing hut to file the work order: the
first call places and registers, the second finds the building already there and
only orders the build.

The blueprint path has to be given because nothing derives it — the Colonial pack
calls the farmer's hut `farm1.blueprint`, not `farmer1.blueprint`, and a building
with no path cannot have a work order made for it at all.

Citizens arrive on the colony's own schedule. To skip the wait:

```
/mc citizens fill 1
```

## 4. Check that it is actually running

```
/mc colony diagnose 1
```

The report leads with a yellow line stating the colony is ticking with no player
watching it, directly under the header — every number below it has to be read in
that light.

Two things worth reading from the server itself rather than from the colony:

* **Chunk tickets.** While the mode is on, one ticket per colony carries
  `TicketType.FLAG_KEEP_DIMENSION_ACTIVE`, registered at radius 0. It is what
  keeps `ServerLevel#tick` walking the entity tick list at all; without it a
  colony ticks, its chunks are held, and nothing inside them moves.
* **Entity-ticking chunk count.** On a working run this matches the claimed chunk
  count. Zero entity-ticking chunks against a non-zero loaded count is the
  signature of the dimension having gone to sleep.

A quick negative control: summon an arrow above the colony. If it hangs in the
air instead of falling, the level is not ticking entities.

## 5. What to expect, and what will waste your time

**Citizens do not appear the moment you switch the mode on.** Citizen entities
are respawned on `CitizenManager#onColonyTick`, which runs on a five-minute
cadence. On a first run against a world whose citizens were never spawned, expect
several minutes before a builder exists at all; on a world where the entity was
already in the save, about a minute.

**A level-0 builder's hut deadlocks the builder.** With nowhere to dump what it
mines, the AI loops in `INVENTORY_FULL` and never advances. Give the builder's
hut a level in the `hut` call.

**A wooded site makes the CLEAR stage very long.** Old-growth trees inside the
town hall footprint have to come down block by block before anything is placed.
Choose a flat site, or clear the volume first, if the point of the run is to
watch blocks go down rather than to measure clearing.

**The toggle is global.** Every colony on the server ticks; there is no
per-colony switch.

## 6. Turning it off

```
/mc debug headless off
```

The keep-alive ticket is given back immediately — a summoned arrow stops falling
again within the minute. The colony's force-load tickets are released on the
ordinary unload timer instead, so the claimed chunks stay loaded for
`loadtime` minutes after the last refresh before the count drops to zero.

The mode is never quiet about itself: a `WARN` on every switch in either
direction, a `WARN` repeated every ten minutes for as long as it is on, and a
`WARN` if the server stops with it still on.

## 7. Known limits

* The colony's own chunk ticket type is deliberately left without
  `FLAG_KEEP_DIMENSION_ACTIVE`. A colony force-loaded on an ordinary install, in
  a world with no players, is therefore still frozen. Giving that ticket type the
  flag would change behaviour for every install and is a change to make on its
  own merits.
* Behaviour with several colonies across several dimensions at once has not been
  exercised.
* Behaviour with players connected while the mode is on has not been exercised —
  the mode is additive to the ordinary subscriber path, but that combination has
  not been run.
* A full build order has not been driven to completion headless. Foundations were
  observed going down; `BUILD_SOLID` and `DECORATE` to the end of an order were
  not reached.
