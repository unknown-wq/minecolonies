# Changelog

Unofficial **Fabric** port of MineColonies to **Minecraft 26.2**. Not affiliated with LDTTeam.

Every release is a single installable jar — `blockui`, `structurize` and `domum_ornamentum` are
bundled inside it and must **not** be added to `mods/` separately. Requires Fabric API and Java 25.

Versions below are this port's own numbering, newest first.

---

## 0.0.55

Autopilots respect enemy airspace, and item frames in blueprints ask for the right materials.

**Simple Planes autopilots now route around enemy colonies — with common sense.** When a player is
aboard an autopilot flight, the route bends around the territory of every colony where that player
is marked hostile (and around the ownerless hostile territories that draw their own border colour).
Three deliberate exceptions keep flying possible: an empty aircraft flies straight; the territory
the flight takes off from is ignored; and the territory holding the destination is ignored, so you
can always land at your own airfield behind enemy lines. A territory too wide to route around is
crossed rather than bricking the autopilot. Needs Simple Planes 5.3.10+; either mod still loads and
runs fine without the other.

**Filled item frames in blueprints requested their contents twice and the frame never** — a
Structurize port defect (`getPickResult()` on a filled frame returns the framed item, not the
frame). Fixed in the bundled Structurize: the builder now asks for the frame plus what goes in it.

## 0.0.54

Framed blocks can be built with again, and a builder whose build disappears goes idle instead of
freezing forever.

**Every Domum Ornamentum framed block was requested as a bare "Framed" and nothing you handed over
was accepted** (issues #1 and #3). Two defects, either one fatal on its own. The builder canonicalises
the ten timber-frame shapes into one requestable item, and while doing so copied the blueprint
stack's *resolved* component map onto the canonical stack — in 26.2 that drags along each item's own
`item_name`/`item_model`, so a request derived from a `double_crossed` frame carried
`double_crossed` cosmetics on a `framed` item. Name and tooltip are drawn from the texture data
(which was correct), so the request and the crafted block read character-for-character identical and
still compared unequal. It now copies only the component *patch*. Independently, the generated
item-matching table listed six components 26.2 adds to every single item (`item_name`, `item_model`,
`break_sound`, `swing_animation`, `tooltip_display`, `use_effects`) as match-relevant, making stack
comparison stricter than upstream for all 1766 items; they are now excluded, leaving
`domum_ornamentum:texture_data` as the only key that matters for framed blocks. Verified on the real
path — blueprint NBT from a shipped mineshaft → request stack → comparison — failing before, passing
after.

**A builder whose work order vanished froze mid-swing, forever.** `CitizenAI` stops ticking a worker
the moment `canGoIdle()` says so, and for the builder that was simply "no work order" — so when the
hut was broken or the build cancelled, the AI's last tick was one where it still thought it was
building: stuck in `MINE_BLOCK`, pickaxe out, progress cursor kept and inherited by the *next* build.
No guard could fire because the AI was never ticked again; only recalling the worker helped, exactly
as reported. Going idle now drops the leftover build state cleanly. Three unguarded
`getWorkOrder().getLocation()` reads in `walkToConstructionSite` — an NPE reachable in precisely that
window — are now guarded, and AI exceptions are logged with a full stack trace, state and citizen
name instead of a bare `[STDERR]: java.lang.NullPointerException`.

**New `[BuilderDebug]` instrumentation** logs every AI state change and structure stage change, marks
the build-completion path, and warns when a state holds for 600+ ticks (with stage, progress cursor,
block, work order, position and request counts) — so a silent freeze can no longer hide. If a builder
stalls, grep `latest.log` for `[BuilderDebug]`.

## 0.0.53

The asset download got a face and eleven languages. The install screen has a real progress bar
(bytes while downloading, files while unpacking and verifying); the texts dropped raw URLs and
mixed-language debris; the success screen no longer claims more than was verified. "Not now" now
means *this session* — the game asks again on the next launch until the assets are installed. The
whole fetch UI is translated into Russian, German, French, Spanish, Italian, Polish, Brazilian
Portuguese, Ukrainian, Chinese, Japanese and Korean, with locale-aware number formatting.

## 0.0.52

The jar ships no MineColonies assets and downloads them on first start. The All-Rights-Reserved
`assets/minecolonies` tree is gone from the jar and the repository; on first client start a consent
screen offers to download LDTTeam's own build (~74.5 MB) from LDTTeam's own Maven, verify all 8474
files against a SHA-256 manifest, and inject the result as a required resource pack — no manual pack
install, no restart. Fallbacks: their 1368 release jar, an owner-enabled HTTP slot (shipped off), or
a MineColonies 1.21.1 jar the player supplies. `/minecolonies-client fetchassets` re-offers, and
every MineColonies window politely offers the download instead of crashing while assets are absent.
The port's own strings are fully translated into Russian, and the 272 huscarl/marksman voice events
missing from upstream's `sounds.json` are shipped and merge in.

## 0.0.51

The warehouse storage upgrade actually works, and a full warehouse no longer stops the colony.

Three items, all measured on a dedicated server with a 51-rack, 2727-slot warehouse rather than
argued from the source.

**The upgrade you pay an emerald block for did nothing.** `TileEntityRack#upgradeRackSize` copies the
old inventory into a larger one, and every `setStackInSlot` of that copy loop fires
`onContentsChanged` → `updateItemStorage()` against the *old*, still-assigned inventory. Nothing
recomputed after the assignment, so `freeSlots` ended the loop holding the pre-upgrade figure.
Measured on a warehouse filled to the last slot: buying the upgrade took it from 2727 to 3177 slots
with **450 genuinely empty**, while `getFreeSlots()` read **0 on all 50 upgraded racks**;
`getRackForStack` returned null and a courier carrying five stacks stored none of them. A rack healed
only when something was taken out of it, or on the next chunk load. One `updateItemStorage()` call
after the assignment fixes it: the same sequence now reports free equal to truly empty on every rack,
and the courier stores all five stacks at once.

**A full warehouse deadlocked its couriers, and through them the colony.** Measured before the change,
with a courier holding a full pack and a claimed delivery: **80 % of samples in DUMPING, 20 % in
START_WORKING, 0 % in either delivery state**. It walked to the warehouse, was refused, went back, and
`decide()` sent it straight to the warehouse again — for ever. Because it never reached
`PREPARE_DELIVERY`, it never took anything *out* of the warehouse either, so a full warehouse could not
even be drained by the workers asking for its contents. The composter's request for 88 rotten flesh
was never served.

Now `dumpInventoryIntoWareHouse` reports how many slots it emptied and carries on past a stack it
cannot place instead of abandoning the rest of the pack. A courier refused outright raises a warning
naming the warehouse — valid for exactly as long as the warehouse is still full — and suppresses its
own dump branch for two minutes so it gets on with the deliveries it can still make. The loaded round
is no longer forgotten, since the goods are still in the pack. Same scene after the change: **192
rotten flesh left the 100 %-full warehouse and reached the composter**, DUMPING fell to 1.3 %, and
delivery states appeared. Freeing space made the courier dump its whole pack and the warning
disappeared on its own.

**The warehouse's chat warnings now carry numbers, and two different ones.** *"The Warehouse at
400, 80, 400 is full: 2727 of 2727 slots taken (100.0%), while the goods in them fill only 4.7% of the
space. Sorting would free about 2599 slots."* Slot occupancy and capacity fill are printed side by
side and never averaged into one figure, because they mean different things: the first is why couriers
are stuck, the second is how much of that is fragmentation.

**Draining organic bulk through the composter needed no code at all.** Verified end to end: 280 rotten
flesh in the warehouse, ticked in the composter's Compostables list — the request was filed and
served, a courier carried 192 out, three barrels composted it, and 18 compost came back.

Two corrections to the study this came from. The claim that a later stack might fit a rack that
already holds it is **wrong**: `getRackForStack`'s first probe also requires a free slot, so it is
all-or-nothing across stacks — a warehouse holding 8181 cobblestone with 166 000 units of physical room
in those very stacks still refused three more stacks of cobblestone. And `upgradeContainers` never
upgrades the warehouse hut's own 27-slot inventory, so 50 of 51 containers grow, not all of them.

## 0.0.50

Hostile territories are told apart on the map by colour.

Every territory drew in the same red, so a second one was ground you could see but not tell from the
first. Now each has a border colour of its own, and `/mc colony territory` lists it.

`/mc colony territory create <name> [<pos>] [<colour>]` takes an optional colour — `create Redhand
red`, or `create Redhand 120 64 -300 red` when a position is given too. Leave it out and one is
picked: from twelve colours, preferring one no other territory in that dimension is already wearing,
so the first twelve come out distinct without anybody having to think about it.

`/mc colony territory colour <colony> <colour>` recolours an existing one — spelled `color` too. It
refuses an ordinary colony: a colony's colour belongs to its owner and is set in the town hall.

Four of the sixteen are left out of the automatic pick. **White** is what the border renderer draws
the player's *own* colony in, and it is also what an untouched colony's team colour already is — so it
is both the wrong signal and the value meaning "never set". That last part is what keeps this
backwards compatible: a territory made before this release still has white, and the renderer still
draws it in the old red. Black, gray and dark gray are out because a border is a one-pixel line
against whatever ground it crosses. Naming any of the four explicitly still works.

The renderer now reads a hostile colony's own colour before falling back to red, and it does so ahead
of the `colonyteamborders` client setting, so enemy ground stays distinguishable with team borders
switched off — where every foreign border would otherwise be the same shade.

Verified live: four territories created in one session came out gold, aqua, red and yellow without
colliding; explicit colours were honoured in all three argument forms; both spellings of the recolour
verb worked; and every colour was still correct after a full server stop and start.

## 0.0.49

Aircraft under Simple Planes' autopilot route around colonies their pilot is hostile in.

Simple Planes 5.3.9 publishes an `AirspaceGuard` interface and a static registry; this registers a
reflective proxy into it by class name, exactly as `SimplePlanesBlastGuard` already does. Neither mod
is on the other's compile classpath and neither declares a dependency, so both are unchanged when the
other is absent.

It enforces nothing. It answers one question about one point — *is this position inside a colony this
player is hostile in* — and that answer is advice to a route planner which weighs it against the
terrain and may still fly through. It is asked only while the autopilot is flying, so a player at the
controls is never affected, and it has no way to say no, so it cannot strand an aircraft. One launched
from inside a claim flies straight out rather than being trapped.

**Hostile means the hostile rank and nothing wider.** `Permissions#getRank` returns Neutral for every
player not in a colony's table, which on a populated server is nearly everybody; treating that as
hostile would detour aircraft around every colony their pilot is not a member of — a general
overflight ban rather than the feature asked for. Colony protection being switched off turns this off
too, since ranks restrict nothing in that world.

The geometry is the colony's own `isCoordInColony`, so the border is block-precise with no second copy
of the shape to drift. Getting there deliberately avoids `IColonyManager#getIColony`, which would load
and generate chunks on the server thread up to 220 blocks ahead of an aircraft; the claim map is read
by dimension and chunk behind a residency gate, cheapest test first.

Measured on a live server: 18 ns over unclaimed ground, 176 ns for the full chain inside a hostile
colony, at up to 105 calls a second per aircraft. Against a 208×208 claim, a hostile pilot's aircraft
left its track 230 blocks out and flew the whole length of the claim outside the border before
rejoining; set to friend on the same route it flew straight over the town centre. Minimum clearance at
one corner was 0.05 blocks — the planner's probes are ~27 blocks apart, so a corner can slip between
samples. It never entered, but it grazed, and inflating the border would mean inventing a shape rather
than asking the colony.

Needs Simple Planes 5.3.9 or newer for the guard to exist; with an older version, or none, nothing
here runs.

## 0.0.48

The sanity cleanup can be held back while a colony is being rescued.

`RegisteredStructureManager#cleanUpBuildings` destroys every building whose chunk is loaded and whose
anchor is no longer the matching block. That is right when a player mines a hut and wrong when the
block is missing for a reason that has nothing to do with the player — a world opened once without
the mod loses every one of the mod's block entities, and the colony then deletes itself building by
building as more chunks load. The code already suspects this: it warns `Did you just load a backup?`
when everything goes at once. A warning does not put anything back.

`/mc colony keepbuildings <colony> on` suspends the removal for that colony. It is saved with the
colony, it is **off by default**, and a colony that has never run the command behaves exactly as it
always did. While on, the cleanup destroys nothing and names in the log every building it would have
destroyed — once each, the first pass it is spared on, then only a change in the count, because at
one pass per 500 ticks a town of 145 orphans would otherwise write its whole list every 25 seconds.
With no argument the command reports the state and how many buildings currently have no matching hut
block.

It holds back the whole of that method's removal, not only the buildings. Building extensions and
leisure sites are dropped by the same method for the same reason and are lost just as irrecoverably;
saving the buildings and letting the fields go would be a switch that half worked. Everything the
method does that is not a removal still runs.

**The order that matters for a rescue.** Restoring a backup, then walking around, then running
`restorehuts` does not work: the cleanup deletes the restored buildings from the colony before the
command can put their blocks back, and once a building is deleted its level exists nowhere. A hut
block placed where the colony still has a building binds to it — `addNewBuilding` returns early —
but a hut block placed where the colony has *nothing* registers a fresh building, and
`createFrom` starts it at level 0. That is where the level 0 town hall came from. So:
`keepbuildings on` first, then load the backup, then `restorehuts <colony> confirm`, then
`repairall`, and only then `keepbuildings off`.

The existing `Did you just load a backup?` warning now names both commands.

## 0.0.47

The hut blocks can be put back after a world has been opened without the mod.

Open a save once with the jar missing and vanilla discards every block entity whose type it does not
know — the log fills with `Skipping block entity with invalid type: "minecolonies:colonybuilding"` —
and the hut blocks go with the chunk sections that referenced an unknown block. The colony save is a
separate file and is untouched by any of that, so the colony still lists every building, its level,
its blueprint and its position, while the world holds nothing at all.

That is not a state a world can sit in. `RegisteredStructureManager#cleanUpBuildings` walks the
colony's buildings each colony tick and deletes any whose chunk is loaded and whose anchor is no
longer the matching block — the `// Sanity cleanup` pass. It is right to do so normally; here it
means every session of play permanently erases the part of the town the player happened to walk past.

`/mc colony restorehuts <colony>` reports how many buildings are missing their hut block and changes
nothing. `/mc colony restorehuts <colony> confirm` places them: one hut block per building, at the
position the colony recorded, turned the way the colony recorded, with the pack name, blueprint path
and rotation handed back to the new block entity. Only the anchor — the structures are the builders'
job through `/mc colony repairall`.

Two things had to be right, and both were measured rather than argued.

**Binding.** The placed block has to reattach to the building that already exists, at the level it
already has, and must not register a fresh level 0 one beside it. The block goes down with
`Level#setBlock`, which never calls `Block#setPlacedBy` and so never reaches
`RegisteredStructureManager#addNewBuilding` at all; attachment then runs through the mod's own path,
`setColony` followed by `getBuilding()`, which is what triggers
`TileEntityColonyBuilding#updateColonyReferences` — that resolves the building by position and calls
`building.setTileEntity(this)`. On a live server the building objects kept their identity hashes
across a `/setblock air` and a restore, and stayed at level 5. Each building is verified after
placement and any that fails is named in the report instead of aborting the run.

**Timing.** The whole loop runs inside one command body, i.e. one server tick, and each position calls
`world.getChunk(pos)` before the placement rather than relying on the chunk being resident. That is
deliberate: the cleanup only looks at loaded chunks, so the chunks nobody has visited hold precisely
the buildings still worth saving.

Driven directly against the cleanup, a building whose hut block had been restored survived it with its
identity and level intact, while the one left without its block was destroyed exactly as before.

The command is destructive — whatever stands at a building's anchor now is replaced — which is why it
takes a confirmation word.

Pasting the blueprints instead was considered and rejected: `/structurize paste` routes a hut anchor
through `AbstractBlockHut#setup`, which sets the building level from a number parsed out of the
blueprint file name and falls back to level 1 when that parse fails. On a rescue whose whole point is
preserving levels, that is the wrong tool.

## 0.0.46

A denied player no longer gets a levitation icon that nothing can remove.

Upstream punishes a player who keeps trying: the eleventh denial inside ten seconds applies ten
seconds of levitation. That part is intended. What was not intended is that the icon stuck at `00:00`
for ever, survived `/effect clear` and a bucket of milk, and went away only on a relog.

The cause is the difference between the events this port replaced and the callbacks it replaced them
with. NeoForge's events were server-side; most of the Fabric callbacks the permission handler hooks —
`UseItemCallback`, `UseBlockCallback`, `AttackBlockCallback`, `UseEntityCallback`,
`AttackEntityCallback` — fire on **both** sides, and only the `ItemEvents.USE_ON` hook had a client
guard. In single player the client thread therefore walked the same server-side colony objects and ran
the denial bookkeeping against the client's own copy of the player.

Applied client-side, the effect is invisible to the server, and `LivingEntity#tickEffects` only
*removes* an expired effect in its `ServerLevel` branch — the client branch calls `tickClient()`,
which counts the display down and stops there. So the icon reached zero and stayed. `/effect clear`
and milk act on the server's effect list, which never held it, so no removal packet was ever sent;
only a relog rebuilt the entity.

The bookkeeping and the punishment are server-side now. The denial itself still happens on both sides,
because the callers return `FAIL` either way and that is what stops the client mispredicting an action
the server will refuse. The town hall write wanted this anyway — the server building manager is not a
client-side object.

---

## 0.0.45

Cavalry rides at sixty percent of a gallop instead of a quarter of a walk.

### The bug was vanilla's, and it was worse than the previous report said

A horse under a **player** is moved by `travelRidden`, with the player's raw input and the horse's
`MOVEMENT_SPEED` — that branch fires only when the controlling passenger is a `Player`. A citizen is
a `Mob`, so a cavalry mount never reaches it. It falls into the generic mob path instead, where
`Mob#setSpeed` writes the same number into **both** the speed field and the forward input, and
`Entity#getInputVector` does not normalise an input vector shorter than 1. Displacement therefore
scales as `MOVEMENT_SPEED` **squared**.

Measured on a live server, with the horse attribute pinned: 0.181 gave 1.4432 blocks/s and 0.250
gave 2.7533, both matching `44.05 × (modifier × attribute)²` to four digits.

0.0.44's changelog repeated the earlier figure of ~1.7 blocks/s for a guard on foot. That was an
average including idle time. A citizen's `MOVEMENT_SPEED` is 0.30, which is **3.96 blocks/s**, and a
patrolling ranger measured 4.0. So cavalry was not slightly slower than infantry — it was **1.4× to
2.7× slower than a guard walking**.

### The fix

`CavalryHorseEntity#travel` now does for a citizen what `travelRidden` does for a player: it keeps
the direction the move control asked for and rescales only the **magnitude** of the input, to 60 % of
a gallop, capped at twice a walking guard.

Linear rather than quadratic, and that is the point. The horse's attribute is rolled at random and
then multiplied by 1.25 on conversion, spanning 0.141–0.422. A quadratic multiplier tuned for a
median horse leaves the bottom of that range still slower than walking and sends the top to 11.9
blocks/s; the linear form spans 4.7–8.8. The cap exists so no lucky roll can move more than half a
block per tick past waypoints that are one block apart.

After: **4.7841 and 6.6079 blocks/s** for the two pinned attributes, again the predicted values
exactly. A ~70-block leg went from 32–48 s to 12–16 s.

Nothing fights the move control: it never writes to the move control's outputs, only rescales a
local copy on the way into `super`. The forward input, the speed field and the attribute are all
byte-identical to the baseline across 224 sampled seconds, and no attribute modifier is involved.
Riderless horses keep the old formula untouched (the return-to-stable goal still measures 0.0881
blocks per tick), and a player-ridden horse never reaches the method at all.

Checked for the damage a faster mount could do: arrival landed on the exact target block in nine of
nine legs with no circling; stutter fell from 9–12 % of moving seconds to 0–4 %; four husks died in
18 s instead of 24 s with both guards still mounted; and four long runs over a built hill, a stair
ramp and a plateau descent left every horse at full health.

**Not changed, but worth knowing:** the Stable's patrol interval still defaults to six minutes, which
is what keeps cavalry shuffling near the Stable most of the time. It is a hut setting in the Stable's
Settings tab — but it has no tooltip and no bounds, and an empty box reads as zero.

---

## 0.0.44

A warehouse that tells you what nobody has touched, a free-mode cook that eats the colony's own food
first, and a cavalry rider who finally looks where he is going.

### `/mc colony warehousestock <colony>` — what is sitting there doing nothing

A per-warehouse observer that samples the racks once per colony tick and diffs against the previous
sample. Per item type it keeps when it first appeared, when its total last went **down**, a rolling
seven-day withdrawal window and a lifetime total. **Only decreases count as a withdrawal**, so
restocking never resets the idle clock.

Both numbers are reported, because idle age alone is a bad judge of value: a thing taken rarely but
regularly would look like junk. "Idle 20 days, taken 0 times" is junk; "idle 1 day, 400 taken this
week" is a consumable that must not be sold.

The chat output is a capped summary. The full list goes to a **CSV** in the world save at
`<world>/minecolonies/warehouse_stock_colony<id>.csv`, next to where the mod already writes colony
backups — item id, name, count, days idle, taken per day, taken in the last seven days, lifetime
total, first seen, whether it was ever taken, and how many warehouses hold it. The command prints
the path, overwrites the file each run, and says so in chat if the write fails.

**Fill is reported too**, as two separate numbers that are never averaged into one: slot occupancy
is the headline, because that is what actually stops a courier storing anything, and item capacity
is second. A slot holding three cobblestone is a full slot and nearly empty capacity. Each warehouse
is listed with its position, and the colony total on top.

Cost, measured on a warehouse of 51 racks, 1827 slots and 589 item types: median **875 µs** per
sample, one sample per 25 seconds — under 0.004 % of a server thread. NBT grows by ~184 bytes per
item type, about 11 bytes per type on disk once compressed.

The correctness trap here is chunk loading: a warehouse whose racks are not loaded reads as empty,
and every item in it would look like it had just been taken. **A sample is discarded whole unless
every container resolved**, and the live test left three racks unloaded for two in-game days and
confirmed the idle ages did not reset. Racks added, broken or upgraded, and stacks shuffled between
racks, are all recognised as structural rather than as withdrawals. Over twenty in-game days
covering five restarts, an unload/reload, a storage upgrade, a broken rack and several restocks, the
CSV showed **exactly two** withdrawn item types out of 589 — the two that were actually withdrawn.

Two real bugs surfaced during that test and were fixed: a broken rack leaves its position registered
for ever, which made every later sample look structural and silently stopped tracking; and
`TileEntityRack#getFreeSlots` is stale after a storage upgrade, reporting 610 occupied slots as 1069.

### Free mode's cook eats the colony's own stock first

Free mode is meant to conjure only what the colony genuinely cannot supply — its interception sits
behind every real resolver, so warehouses get first refusal. The cook was outside that rule: it
dropped a stack of raw food into its own hut the moment the larder ran dry, winning the race against
the courier carrying the real thing. A stocked warehouse was never consumed.

It now checks the colony's warehouses first and, if the food is there, waits for the standing request
to be delivered. Measured: before, 64 raw beef conjured at 13 s while the warehouse still held its
64; after, the warehouse goes 64+64 → 0+0 and all of it arrives by courier.

**The wait is bounded at two in-game minutes.** With a stocked warehouse but every courier killed, so
delivery is impossible, the cook waited and conjured at 123 s and 122 s in two independent runs. With
no warehouse and no courier at all it conjures immediately. Free mode off changes nothing.

### The cavalry rider looks where he is going

`CavalryHorseEntity#tick` read **the horse's** navigation to decide where to point the rider's head
and whether a ladder was coming. While a citizen is aboard that navigator is empty every single tick:
vanilla redirects the rider's move control to the mount, so the orders live in the *rider's*
navigator and the horse's own is never given a path. Both features were therefore dead code — 0 hits
in 160 samples over 90 seconds — and a mounted guard who never turns his head is exactly what "he
just sits there and does not steer" looks like. This is upstream's bug, not the port's: the same line
is in the 1.21.1 snapshot.

Worth knowing, both measured rather than changed: cavalry is *slower* than infantry (~1.4 blocks/s
mounted against ~1.7 on foot), and the Stable's patrol interval defaults to six minutes, so a cavalry
guard visits one patrol point and then loiters near the Stable until the timer comes round. The
interval is a hut setting you can change in the GUI.

---

## 0.0.43

Colony protection comes back, and guards stop sleeping in water.

### Six permissions enforced again

The port had lost every NeoForge event these hung on, and the study that surveyed the damage assumed
some of them needed a mixin. None did. `minecolonies.accesswidener` was not touched either.

* **`PLACE_BLOCKS` / `PLACE_HUTS`** — on `ItemEvents.USE_ON`, which wraps `Item#useOn` and is the only
  door to `BlockItem#place`. Deliberately *not* on `UseBlockCallback`: that fires before vanilla has
  decided whether a click is a placement or a use of the clicked block, so a check there would refuse
  a friend of the colony flipping a lever while holding a block. Dispensers and falling blocks are
  still not reached — they never call `ItemStack#useOn`.
* **`FILL_BUCKET`** — on `UseItemCallback`, where `BucketItem#use` lands. Only the empty bucket picks
  a fluid up; a full one is placing, which upstream did not route through this permission either.
* **`SHOOT_ARROW`** — same callback, on the **draw** rather than on the release, so a denied player
  cannot nock the arrow at all instead of drawing and having the shot swallowed.
* **`OPEN_CONTAINER`** sees modded inventories again, through `ItemStorage.SIDED` alongside the
  vanilla `Container` test.
* **Hostile mobs no longer spawn inside a built building.** On `ServerEntityEvents.ALLOW_LOAD`,
  filtered to a real `Enemy`, a fresh spawn rather than one read back off disk, and only the two
  reasons `NaturalSpawner` uses — so the mod's own raiders, citizens, visitors and mercenaries are
  never looked at, and neither are spawners, spawn eggs, `/summon` or breeding.
* **The entity half of `turnoffexplosionsincolonies`** applies to every explosion in the game,
  vanilla ones included. On `DAMAGE_PLAYERS` a blast in the colony no longer hurts citizens,
  livestock or pets; on `DAMAGE_NOTHING` it hurts nothing there except a player — which is what the
  setting meant upstream, players included, and neither policy did anything on this port before. On
  the shipped default, `DAMAGE_ENTITIES`, nothing changes.

Measured on a running server with TNT one block from a cow and a zombie inside the colony, and an
identical pair outside as a control: on `DAMAGE_PLAYERS` the in-colony cow survives untouched and the
zombie dies; on `DAMAGE_NOTHING` both survive; outside the border both die under every policy.

**Still unenforced:** `TOSS_ITEM` and `PICKUP_ITEM` — `ItemTossEvent` and `ItemEntityPickupEvent`
have no Fabric counterpart. Colony **blocks** are still not shielded from vanilla explosions; that
needs a mixin on `ServerLevel#explode`.

### `/mc colony protection <colony> [on|off]`

Whether a colony enforces its permissions against players at all. On by default, per colony, saved
with the colony, OP only.

It is read in **exactly one place** — `Permissions#hasPermission`, the single funnel every
server-side permission test in the mod goes through. That is what makes "off" mean off: breaking,
placing, hut placing, right-clicking, containers, buckets, bows, attacking citizens, the build tool,
the supply-chest deployer and the assistant hammer all ask that one method, so nothing has to be
enumerated and nothing can be forgotten.

Two things stay on with the switch off, because they protect the colony rather than deny the player:
hostile mobs still cannot spawn inside a built building, and the explosion policy still spares
citizens and livestock. Each has its own lever, and the command's own message says so. Editing the
permission ranks still needs `EDIT_PERMISSIONS` — administration rather than protection, and the one
change that would outlive turning the switch back on.

### Guards no longer nod off in water

A guard falls asleep wherever it happens to be standing: `shouldSleep` only ever looked at the config
switch, an active fight, damage from a mob and illness. Standing in water is ordinary for a guard — a
patrol along a shore, a bridge with a gap, a swim back from a chase.

Once asleep it is pinned to a `SittingEntity`, and the only two things that end the nap are damage
from a mob and the timer running out. Drowning is neither: `getLastHurtByMob` is set by an attacker,
and drowning has none. So a guard that dozed off in water stayed there for the whole 2500–3000 tick
nap.

It now refuses to fall asleep in a fluid, and wakes if the fluid arrives afterwards — a flowing
source that finally reached it, a shove off a bridge, a bucket. The second check is not redundant:
the first only guards the moment of nodding off. Lava is included for the same cost.

---

## 0.0.42

Fishermen sit apart on the same pond — and the version before this one could leave them idle.

### The fix, and the bug it replaces

0.0.40 spread fishermen by **pond**: the water a colleague was working went into the rule that already
stops a fisherman rediscovering his own pond. It shipped unproven, and the verification found what
unproven work usually hides. On a pond too small to hold five separated spots — a 7×7 pool — two
fishermen shared a block and **three never fished at all**: every candidate the search offered was
refused, indefinitely, because the "share rather than starve" fallback only runs for a fisherman who
already has a pond, and a fresh worker never gets one. Two crowded and three idle is worse than five
crowded.

So the spreading moved off the pond and onto the **seat**. The pond stays whatever the search returned
— it is what identifies the water to his memory and to his colleagues — and only the place he sits
moves:

* **In a boat**, a seat within 6 blocks that no colleague is within 4 of, that a hull can sit on, and
  that is joined to his own water by an unbroken run of surface. Chosen when the search result is
  *used* rather than when it is queued, which also closes the window where two fishermen already in
  flight both took the same block.
* **On a bank**, the seat slides along the bank he already has — a bounded walk over standing room
  beside water, no pathfinding. Choosing among banks properly would mean re-running a synchronous A*
  per candidate inside the most node-hungry job in the game, which is not worth what it buys, and the
  numbers say so: shore gaps come out at 2–3 blocks against 4–10 in boats.
* **Sharing is the floor, never the goal**, and colleagues' ponds only enter a search when the
  fisherman has a pond of his own to fall back on — so starvation is impossible by construction rather
  than by threshold.

Measured on two hand-built stands: five fishermen 4.0–10.0 blocks apart on a deep lake, five distinct
blocks and nobody idle on the 7×7 pond, a lone fisherman unchanged. All five now find the *same* pond
— the seat choice is doing all of the spreading.

**If you are on 0.0.40 or 0.0.41 with more than one fisherman in a hut, this is worth taking.**

---

## 0.0.41

The fishing line stops twitching.

### The bobber interpolates again

Reported in play: the line between a fisherman and his hook jerks back and forth. The cause is a port
regression with an unexpected shape — 1.21.1 switched client interpolation **off** for the bobber with
an empty `lerpTo`, the port dropped that override because 26.2 has no method with that signature, and
the audit recorded the loss as "probably an improvement, but only eyes can tell".

Eyes now say otherwise, and vanilla agrees: 26.2 replaced `lerpTo` with `InterpolationHandler`, and a
plain entity defaults to **no** handler — snap to whatever the server last said, which is exactly what
the old empty override did. Vanilla's own `FishingHook` is not a plain entity: it carries a handler,
returns it from `getInterpolation()` and interpolates at the top of its tick, so the hook glides
between server updates instead of stepping to them. Ours did not. It does now, the same way.

**Unverifiable here** — there is no display in this container, so the fix is vanilla parity read off
the vanilla source, not something anybody has watched. It needs eyes in a world.

---

## 0.0.40

Racks break to explosions, the Stable moves to where people look for it, and a colony full of
children who will never grow up has a way out.

### Racks are no longer blast proof

Upstream gives the rack infinite blast resistance, which puts colony storage in bedrock's class: a
creeper in the warehouse craters the floor and leaves every rack in it hanging in the air, untouched.
It is the only block in the mod that does this — huts sit at 1, a grave at 5.

It is 6 now, which is stone: the line vanilla itself draws for "a creeper takes a bite out of it".
Hand mining is unchanged, since that is hardness and it stays at 10. The inventory is not lost with the
block — explosions end in a `setBlock` that does not suppress drops, so the contents spill the same way
they do under a pickaxe.

### The Stable moved to Military

It was generated from each style's cow barn and landed where its donor lived, under
`agriculture/husbandry`, between the chicken herder and the swineherder. The build tool groups by
folder, so that is where you had to go looking for a building whose entire purpose is cavalry. All 115
blueprints now sit under `military`, beside the barracks, and the generator writes there too.

**If you placed a Stable on 0.0.39** it still points at the old path and will want re-placing. 0.0.39
was hours old when this moved, and the building could not be built at all before it.

### `/mc colony growChildren <colony>`

Grows up every child in a colony at once. The child timer counts the time a child's AI has actually
been **active**, not elapsed game time, so children in a colony nobody visits never grow up at all —
test worlds and restored backups fill with them, usually attached to a tavern, because a child holds
no bed of its own and the orphan pass hands the homeless ones to whatever quarters will take them.
Until now the only way out was killing them one at a time.

It performs the same transition the AI performs, so a citizen grown this way is indistinguishable from
one who grew up by living: housing takes them as adults, the model and skin swap, jobs open.

### Fishermen out of one hut spread out — unverified

Five fishermen in one hut were measured converging on a single water block, each conjuring his own
boat, the hulls 0.05 blocks apart. The spots colleagues are working are now fed into the rule that
already stops a fisherman rediscovering his own pond, and picking a remembered pond prefers one nobody
is at.

**This one ships unproven.** It compiles and the colony runs, but the five-fisherman stand that
measured the crowding is gone, and the verification is still running as this is written. Worst case is
that they keep crowding, which is what they do today.

---

## 0.0.39

The Stable and mounted guards are unlocked, the fisherman fishes from a boat, and the mine stops dying
in silence.

### The Stable, in all 23 styles

The Stable's hut block had no recipe, so nothing that depended on it could ever be reached. It has one
now, and the building has a blueprint in **every** style — each derived from that style's own cow barn,
so it belongs where it stands rather than arriving as a visitor from another pack. The generator that
produced them is committed alongside, so the next style gets its Stable without hand work.

Mounted guards came with it: three defects in the existing cavalry code are fixed. Stall and patrol
markers are merged per position instead of fighting each other, a cavalry horse no longer loses its
stable when the world is saved underneath it, and being hit no longer writes a stack trace to the log
on every blow. In free mode the Stable hands out mounts already trained and armoured.

### The fisherman: a boat, and a hut that holds five

A fisherman whose pond has no reachable bank now sails out to it. There is no boat item and no request
— the hull is conjured the same way citizen boat travel already conjures one, and it is moored while he
fishes: held against the water's push, and released on its own if he dies, sleeps or is unassigned. It
is gated behind the existing **Boats** research, and a colony without that research behaves exactly as
before, which was checked rather than assumed. Shore fishing is untouched; the boat is for water that
has no bank, not a replacement for one.

The hut now holds **one fisherman per building level**, five at level 5. Pond memory really is
per worker — each remembers his own water and carries his own rod.

Three defects turned up while proving the boat worked, all of them fixed: boarding used to teleport him
across the lake, the anchor used to moor the ferry that was carrying him and strand him mid-crossing,
and the fishing spot was the water block rather than the surface above it, which sent him walking to a
place a citizen cannot stand.

### The mine no longer dies quietly

A mine that hit water or lava used to stop, and nothing said so: the miner ticked normally, the shaft
never got deeper, and `/mc colony diagnose` reported no problems. Measured in that state: 100 000 ticks,
not one block changed.

Now a stall is noticed and named. The miner says it in chat, and the shaft's depth and how long it has
been stuck show up in `/mc colony diagnose` — which matters more than the chat line, because it survives
the miner dying and being replaced. The clock lives in the hut for that reason. A healthy shaft never
raises it: one running alongside the test peaked at a fifth of the threshold.

He also gets out of the fire now. Standing in lava used to leave the AI unmoved while the citizen
burned to death; he leaves the face and walks away. He can still die — fully immersed, or four blocks
down a shaft at low health, he does not make it — so this converts "stands there until dead, silently,
forever" into "leaves, and may still die". Lava and water are no longer valid targets for a pickaxe,
the standing position falls back to the ladder column rather than a block he has abandoned, and the
fluid sweep runs on passes where an early return used to skip it.

The ladder column, checked because it looked like the weak point: it repairs itself. Breaking it
mid-shaft makes the miner read the gap as the bottom, which is exactly what starts the repair — he
walks it back down to the real bottom and carries on. Ladders do not burn in 26.2 either; only flowing
lava breaks them.

### Upstream

Every commit ldtteam/minecolonies has made since this port's snapshot is now accounted for: what was
taken, what was already here by another route, and the exact commit the snapshot sits on. Taken in
this release: empty bottles come back from recipes again
(they were at weight 0 against an empty entry at 100), the warehouse inventory sort holds for the
session, the alchemist gets blueprints in three Medieval styles, and Caledonia gets two alternative
residence sets.

### Housekeeping

The port's own notes had grown to 38 loose documents and were sorted into audits and to-do lists.
Two more audits joined them: the **miner** and the **builder**, both measured on a live server, both
with a ranked list of what to fix.

---

## 0.0.38

Cinnabar becomes red dye, and the stonemason can finally cut the 26.2 stones.

### One cinnabar block to four red dye, at the dyer

Cinnabar in 26.2 is not an ore — it is a thirteen-member decorative stone family generated as bands in
sulfur caves. The recipe therefore takes the **plain block**, the only member that is actually mined;
every other form is a crafting or cutting step away from it, so consuming one would tax the player for
nothing.

Yield four, and that number is argued rather than picked. Red dye is already free and renewable from
poppies, beetroot and red tulips — the dyer knows all three — so trading a finite cave block for a
single dye is a recipe nobody would ever use. Four matches the family's own ratio (one cinnabar makes
four polished cinnabar) and stays conservative against vanilla's own compressed pigments, where a lapis
block gives nine. Building level 2, matching the dyer's other single-input dye conversion.

### The stonemason: six recipes, and the first diagnosis was wrong

The tier-1 study reported that cinnabar and sulfur were missing from the conventional stone tag, so
"two tag lines unlock nine blocks". **The mechanism was right and the count was not**, which is why it
was checked rather than trusted.

`c:stones` in Fabric API 4.7.0 really is still the pre-26.2 six — verified by unpacking the nested jar.
Tags merge, so contributing two members needs no mixin and overrides nothing. But the nine stairs,
slabs and walls were **already** craftable: they reach the stonemason through the vanilla shape tags it
already pulls in. What was actually blocked was six recipes, and the two tag lines fix only four of
them.

Cinnabar and sulfur **bricks** — the exact symptom that started this — stayed blocked either way,
because their ingredient is the *polished* block and their output is in no product tag: vanilla's stone
brick tag still lists only its own four. They are now listed explicitly, one line below the existing
entry for tuff bricks, which is there for precisely the same gap.

Only the **item** form of the tag is contributed. The block form feeds the miner's lucky-block roll,
which is an unrelated gameplay change nobody asked for.

**Six recipes unlocked, not nine.**

### Verified

Measured on a dedicated server against a pristine copy of the same world, the same commands both ways:
the stonemason went from **250 to 256** compatible recipes — exactly the six predicted — and the dyer
from 234 to 235. Tag membership was checked block by block with `execute if items`, including the
negative case: cinnabar is deliberately **not** in the dyer's ingredient tag, because that tag is
subtracted wholesale from the stonemason's, and adding it there would have stolen the stone from the
mason to give the dyer a recipe it did not need.

And the saved colony proves both workers actually took the recipes: the dyer's crafting module holds
the granted cinnabar-to-red-dye storage, the stonemason's holds polished cinnabar, cinnabar bricks,
polished sulfur, sulfur bricks and potent sulfur.

Not checked: anything on screen — the dyer's recipe window and the tooltip. There is no client here.

---

## 0.0.37

Couriers: three times the deliveries, half the walking, and the same number of path searches.

Measured on a dedicated server against a pristine copy of the same world, one courier, permanent
backlog, four minutes each way:

| | before | after |
|---|---|---|
| Deliveries | **14** | **42** |
| Items delivered | 224 | 672 |
| Share of life standing at the decision delay | **27.3 %** | **3.0 %** |
| Blocks walked per delivery | **52.6** | **25.3** |
| Path searches | 267 | 270 |
| Pathfinding queue wait | 271 ms | 159 ms |

Three times the work for the same number of searches, and the colony's pathfinding pool went from 53 %
busy to 49 %.

### A courier no warehouse has taken on does nothing at all

Hiring at the hut is only half of it: the whole courier AI is gated behind a *second* assignment list
belonging to the warehouse. Measured before the fix — five couriers hired at one hut, **four of them
spent all 1840 ticks in `START_WORKING`, moved nothing and delivered nothing**, while
`/mc colony diagnose` reported `No problems found`.

Adoption is no longer gated on the colony-wide auto-hire switch, because adoption is not hiring —
nothing leaves the labour pool, and a colony that has switched off automatic hiring has not said
anything about which warehouse serves which courier. `MANUAL` and `LOCKED` are still honoured. And
`diagnose` now names each stranded courier, where he was hired, and every warehouse with its occupancy
and hiring mode, and counts it as a problem.

This mattered little when a hut held one courier. Since 0.0.33 it can hold five, so it became the
first thing a player would hit.

### The five-second stand

A courier spent **27 %** of its life standing at the decision delay with work waiting the whole time.
The fix is three lines, and the distinction that makes it work is worth recording: `setDelay` suspends
the *whole* AI for n ticks — it would have frozen the courier rather than slowing its polling.
`setCurrentDelay` writes to the transition currently executing, which the state machine has just
stamped with its own rate, so an override from inside the supplier lands and governs the next visit.

### Claiming work near the courier

The queue-position term reached 175 and swamped everything else, so twenty couriers behaved as one
colony-wide queue with twenty pairs of legs. That term is now capped and a walking term added — but
**not** the literal "distance from courier to job", which is wrong: a delivery must be fetched first, so
its true cost is courier→rack plus rack→target, and with one warehouse the first half is identical for
every candidate. The courier's own position earns its keep where sources differ, and above all for
pickups, which chain building to building without going home. An `indexOf` per entry also went — the
claim scan was quadratic in queue depth.

### Multi-drop rounds

A courier now carries for up to three destinations in one trip: head of the queue plus up to two
neighbours within 24 blocks, ordered greedily from the **rack** rather than from the courier, since it
must fetch before it can deliver.

**The danger this had to clear** was cross-contamination. Unloading extracted whole slots by item
identity, which is safe only under the assumption that the pack holds one building's goods — so two
buildings ordering cobblestone would have had the first stop empty the third's slots. From the player's
side that is not an optimisation, it is theft. It is prevented twice over: each stop carries a map of
what *it* is owed, taking the lesser of owed and slot and decrementing by what the chest actually
accepted rather than what was offered; and that map is built only from parcels actually loaded for that
stop, so a parcel whose rack was empty cannot make up its shortfall out of the next stop's goods.

**A stop that disappears mid-round** is handled per case: a demolished building fails that stop and the
round walks on, its goods dumped at the end; a full chest fires the existing interaction and sends the
courier to dump, with the rest of the round still claimed but re-fetched rather than delivered from an
empty pack; a stop that wants nothing fails and the round continues. A stop whose goods were never
loaded is dropped so its token cannot sit at the head of the queue forever.

### Multi-drop pickups: worked through, and deliberately not built

The one piece of the approved set that was not shipped, with an argument rather than a shrug. Pickups
already chain building to building, and after the decision-delay fix the re-decision between stops costs
a quarter of a second — so claiming one at a time and scoring by distance *is* greedy nearest-next,
re-evaluated from the courier's real position with everything that has arrived since, whereas a batch is
that same walk computed once from stale information.

Worse, a claim is never released. Pre-claiming three pickups hides them from the other nineteen couriers
for a round that a full pack can cut short at any moment — which is exactly the "one courier hoards, the
rest idle" shape. Delivery rounds do not share it, because their goods are gathered atomically before
departure. If it is wanted anyway, the safe form is releasing an unstarted tail back to the shared queue,
which is a change to how claims are held rather than to how they are scored.

### Three inherited defects, fixed on the way past

An unloaded-chunk penalty that was being overwritten by the line after it, so the guard had never once
been live; a pickup-merge branch testing the wrong object and therefore unreachable; and a destroyed
delivery target reporting **success**.

### Not verifiable here

A player-less server never runs a colony's slow tick at all, so the adoption loop could not be observed
firing on its own. It was proved by hand-running the module's colony tick through a probe — the gates
read open and the warehouse went from one courier to five in that single call — but that the loop fires
on a played server is inference from the state machine, not observation.

---

## 0.0.36

A broken recipe token no longer takes the colony's slow tick down with it.

### What was happening

A crafting module could hold a recipe token that nothing can resolve, and dereferencing it threw. The
colony's state machine caught that and answered by delaying the transition **five minutes** — so the
slow tick ran three times in ten minutes instead of twenty-four. Reproduced on a server before anything
was changed:

```
NullPointerException: Cannot invoke "IRecipeStorage.getRecipeType()" because "storage" is null
  at AbstractCraftingBuildingModule.checkForWorkerSpecificRecipes
  at RegisteredStructureManager.onColonyTick
  at Colony.worldTickSlow
17:55:35 slow tick   18:00:38 slow tick   18:05:41 slow tick     <- normal cadence is 25 seconds
```

**Two corrections to how this was first described**, both from reading the code rather than the
symptom. The `storage != null` guard people expected to be missing **is already there** — one
dereference further down was not covered, and that is the only one that can throw. And the delay is
applied to *the transition that threw*, not to the whole machine: the colony's subscribers, requests,
citizen data, work manager, raids, waypoints and daytime all kept running. What stalled was
`worldTickSlow` — buildings, the citizens' colony tick, visitors, animals, events, graves,
reproduction, quests. Bad enough, and with a nastier edge: the aborted loop never reached the buildings
*after* the one that threw, and since the same one throws first on every retry, **the tail of that
building map effectively stopped existing**.

### A stale token, and why it happens without anyone doing anything wrong

The two halves live in different files and nothing keeps them in step: a building's tokens are saved
with the colony, the recipe definitions in a separate file. The recipe manager **silently drops** any
stored recipe that fails to validate or throws on insert — there is a `catch` in it whose entire body
is a comment saying the exception is eaten — and the building keeps its token regardless.

The fixture world used for testing had **14 such tokens** already, from an ordinary save and load, in a
baker and a sifter. It also only fires intermittently: it needs a stale token and a recipe-replacement
in the same tick on the same module, and iteration order varies per run. That is why it looked like a
different feature was broken and cost an earlier investigation two hours.

### Cleanup, not a skip

Unresolvable tokens are now **removed**, not stepped over. There is nothing to recover: the token is a
random id minted with the storage and can never be re-minted, so skipping would leave the entry in the
list and in the save to be skipped again every 25 seconds forever. The mod already agreed with this —
the same removal has always been done when serialising a building to a watching client; it simply never
ran for a colony nobody was looking at.

Two more of the same unguarded lookup were found and fixed by walking every call site, and one loop in
the same method was removing from the list it was iterating — a concurrent-modification crash waiting
for the first recipe it decided to drop.

### One building's failure no longer costs the colony its tick

The building loop now **contains** a thrower instead of letting it escape: the first failure logs an
error naming the colony, the hut type and its position, and after that one line per 240 failures. The
rest of the colony keeps ticking. Verified with an injected per-tick throw — the 25-second cadence held
and the colony handler was never reached.

### Left deliberately, and written down

- **The five-minute penalty is disproportionate** — 12× against a transition that runs every 500 ticks,
  1200× against one that runs every 5. A penalty expressed as a multiple of the transition's own rate
  would back off just as well. It is shared by every state machine in the mod, including all worker AI,
  so changing it blind is a far bigger blast radius than the bug was.
- **A colony in the penalty box is invisible.** `/mc colony diagnose` should report any transition whose
  wait exceeds its tick rate. Cheap, and it is the real answer to "a stalled colony looks exactly like a
  colony with nothing to do".
- **A failed recipe removal responds by clearing every recipe the crafter knows** — a worse outcome than
  the failure it handles.

All of this is **inherited**: 1.21.1 and current upstream have the same unguarded dereference and the
same five-minute delay. The port introduced none of it.

### Verified

On a running server: the stall reproduced at 5m03s intervals, then 14 stale tokens pruned and a steady
25-second cadence with zero exceptions; containment holding under an injected throw; a fresh world
booting to the documented baseline of 0 errors and 10 warnings.

Not explained: where the fixture's own 14 stale tokens came from inside the recipe manager's read path.
That thread is left open.

---

## 0.0.35

Children belong to a family, they are child-sized again, and old age says so.

### A child is born to two citizens and lives with them

The causality was backwards. The game picked **a house with a free bed** first — anchored on the colony
centre, which the tavern's four beds usually won — and then took the parents from whoever happened to
live there. That is why every child ended up in the tavern, and why a child born into an empty house
had **no parents at all**, just a generated name.

Now a citizen is drawn, its family is found — its partner, else the nearest unattached citizen it could
have a child with, preferring a housemate — those two become a couple, and the child moves into their
home. Later children of the same couple stay in the same family.

**A child does not take a bed.** It shares its parents' home and costs the house nothing, so a full
house still takes its own children. When it grows up it becomes an ordinary adult, needs a bed like
anyone else, and keeps the family home if there is room or moves out if there is not.

### Keeping every bed number honest

One method is now the truth — the residence module's adult count — and everything asking "is this house
full" goes through it, including a fix caught on the live server: the base class refused a newborn
because the house was full, so **every child came out homeless** until assignment was overridden for
children.

The colony's bed total never changed (it is the sum of building levels), but the *inference*
"population − beds = homeless" did. So `/mc colony info` now counts the homeless one by one and prints
adults and children separately, `/mc citizens fill` counts real homeless adults, `/mc colony diagnose`
stopped subtracting a schooled child twice — it was reporting **−10 unemployed adults** — and the
residence window shows beds taken plus children instead of `3/2`.

`/mc colony rehouse` converges an old save: it moves a child to a parent's house and says so. And a gap
the run exposed is closed — nothing used to re-home a child whose family home was demolished or
downgraded.

### Children were adult-sized, and it is our loss, not upstream's

Two faults, one symptom.

**The size.** 1.21.1 had one line — `model.young = citizen.isBaby()`. In 26.2 vanilla removed that field
(baby geometry is a separate baked model layer now) and the port dropped the line **with nothing in its
place**. MineColonies' own child model is a full-size humanoid mesh; it never carried the shrink itself.
So since the port began, *every* child rendered at adult height while its hitbox was correctly 0.62 —
**the model was wrong and the hitbox was right, and they disagreed**. The renderer now scales the pose
by the same age scale, so they agree by construction.

**The worker's skin** — the half that made one look like the builder standing idle. The entity
re-resolved its model when a child grew *up* and did nothing when an adult was flagged as a child, and
the data-side setter never touched the entity at all. A citizen created, modelled, and only then marked
a child kept the model it was given. That is exactly why it was *some* children and not all. Both
directions now re-resolve, and the two flags are reconciled every ten seconds like the texture style
already was.

**Neither could be seen here** — there is no display in the build container. The size fix is reasoned
from vanilla's renderer and from the 1.21.1 line it replaces.

### Old age says "died of old age"

Deaths from age reported the generic message, in the client's own language, which is why the line read
`§cThe Knight … умер!` with the rest in English. Vanilla's combat tracker falls back to
`death.attack.generic` when it holds **no entries**, and entries only exist for damage actually dealt —
while ageing kills directly with the `minecolonies:oldage` damage type without dealing any. The mod's
own correct string was simply never reached.

The tracker is now preferred when it has entries — fall variants and "killed by X while fighting Y" are
unchanged — and the damage source's own message is used when it does not. That covers every death
dealt without recorded damage; today old age and `/mc citizens kill` from the console, both of which
now have proper strings.

**The "every morning" half is working as designed**, and was measured rather than assumed: ages at death
with a ten-day lifespan came out 7.7, 7.8, 7.9, 8.4, 8.5, 8.5, 8.6, 8.7, 8.9, 9.2, 9.2, 9.2, 9.7, 10.1
— genuinely spread across the jitter band, not one cohort. But the ageing pass runs once per colony
day, at wake-up, so every death **lands at dawn** whatever its age. The stagger decides which morning,
never the moment.

### School: working as designed, and what to check

Auto-hire is fine out of the box — both town-hall settings default on, and all 16 children became
pupils with no intervention. **Carpets are the real suspect**: a school without them leaves pupils
frozen, and while the game does say so through a blocking interaction, it says it **only on the child**
— never in chat and never in `/mc colony diagnose`. Recess is one decision in ten, so a tenth of the
time a pupil is legitimately running about. Nothing was changed in the school path.

### Verified on a running server

Births with parents named and no bed taken, five births to four different couples at four homes, bed
accounting agreeing across the module, the colony maximum and the town hall at every sample, sixteen
children growing up in one pass with nobody left homeless, a save written by 0.0.31 loading clean and
its children keeping their homes, parent ids surviving NBT, and an orphaned child taken back into its
family's house within a minute.

**Not verified — all client-side:** the child's size and skin on screen, the residence window's children
line, the family window's parent ids, the colony map's fullness bar, and the death message as it
appears in chat rather than in a log.

### Two defects in other people's files, deliberately not touched

- **A stale crafting recipe token NPEs**, and the colony's state machine answers by delaying five
  minutes — so **the whole colony stops ticking**: no housing, no births, no work, recovering only to
  throw again. This is why births looked broken while investigating. A null guard fixes it; it was
  applied only in a throwaway jar, because it belongs to a different feature and deserves its own look.
- **`/mc citizens info` names the wrong job for a pupil** — it reads the school's first worker module,
  which is the teacher's.

---

## 0.0.34

The frontier tells you when you cross it, guards walk it, and a raid can come out of it.

### Crossing the line says so

Walk onto hostile ground and one line names the territory; walk off it and one line says you are out.
Both are suppressed for ten minutes per identical line per player, so pacing back and forth over a
boundary does not produce a wall of text. The departure speaks in **gold rather than red**: two
identical-looking red lines made the all-clear read like a second alarm.

A world with no hostile territory pays one map lookup for the whole feature.

### Border patrol, set on the barracks

One setting on the **barracks** — *Border Patrol*: Off, Enemy Border, Colony Border — because not every
guard should walk a line and it should be set in one place. Off by default. No guard AI was changed at
all; a hook on the guard building supplies patrol points and the barracks tower overrides it.

**How a stretch is chosen.** A 33×33-chunk scan around the barracks builds a bitmap of what counts as
inside — enemy ground, or your own claim — and border chunks are the ones on the near side of a
transition. Two arms then grow outward from the chunk nearest the barracks and interleave, so the
stretch is **centred** on the nearest point rather than starting there and running off in one
direction. Target 500 blocks for the enemy border, 550 for your own, hard cap 600. One stretch per
barracks, sliced contiguously, one slice per built tower, ordered by where each tower stands along the
line.

**What keeps a guard on his slice** — three things, and none of them is a distance check:

1. While the patrol is on, the stretch is the *only* source of patrol points: the random 20–40 block
   wander and the "pick a building somewhere in the colony" branch are both skipped.
2. Every waypoint came out of a search bounded to 16 chunks of the barracks, so **nothing further than
   264 blocks from home can ever be handed out** — the thousand-block wander is not prevented, it is
   unrepresentable.
3. The next point is the neighbour of the waypoint nearest where the guard actually is, so a guard
   dragged off by a fight rejoins at the closest point rather than at the start.

**Water needed no new code.** A waypoint resolves to the water surface, and the navigator already
launches a boat for a path over water when the colony has the Boats research.

**When the stretch is unreachable or the enemy vanishes**, the plan recomputes and the tower falls back
to today's ordinary automatic patrol — no stranding, and the reason is reported. The plan is fixed when
the patrol starts and invalidated by a mode change, a tower count change, five minutes, or the hostile
index being republished — that last one is a **reference comparison**, because the index is swapped in
whole, which makes "has the enemy border moved" free to ask.

**To see where your patrols are:** `/mc colony diagnose <colony>` grew a *Border patrols* section.
`diagnose` alone was not enough — it reported AI state but never where a guard is or what he is
supposed to be walking. It now lists the barracks and its mode, each tower's slice and the point it is
heading for, and every guard by name with his position and how far off his line he is, calling out
anything past 100 blocks in words. No new command, no new window.

### Raids out of enemy ground, by command

`/mc colony raid <colony> now|tonight territory` scans 500 blocks for hostile chunks, takes the nearest
usable one and hands the raid an explicit spawn point — a path the raid manager already had, which
skips the ordinary spawn search entirely. **Scheduled raids are untouched.** `/mc colony raid <colony>
where` describes such a raid with no work, confirmed on a server rather than assumed. Four named
refusals that each do nothing: no territory in the world, none within 500 blocks, none loaded, nowhere
to stand.

### Three defects found by running it, not by reading it

- **A water spawn lottery.** The position selector accepts a liquid with air above it, so the nearest
  coastal territory chunk was the sea surface — and the raid manager then reclassifies such a raid as
  drowned pirates one time in five, with no branch left if no ship fits, answering `NO_SPAWN_POINT`.
  One in two early attempts failed this way. Dry land is now preferred over sixteen probes, with wet
  kept as a fallback so a coastal territory can still launch a longship.
- **An erased territory kept answering.** `markDirty` only republishes the hostile index for a colony
  still flagged hostile, and by the end of a territory delete the colony is gone — so a minute after
  deleting "Blackreach", a raid command still found it while the listing said the dimension had none.
  **Pre-existing since 0.0.31**, fixed in the delete path.
- **`diagnose` could throw** on a tower with no guard assigned, in a command whose whole job is to
  survive a broken colony.

### Cost

**Zero per tick.** The call the AI makes every tick returns its cached point before reaching any of
this; the hook is only entered on arrival or the two-minute timer, and the chunk scan runs at most once
per five minutes per barracks. With the setting Off — the default — it is one virtual call returning
null on the patrol timer.

### Not verified

There is no game client here, so: guards actually walking a line, taking a boat, and the barracks
settings tab were not seen. Slicing across several towers is also untested on a server — a barracks
tower cannot be created by command, so a temporary instrumented jar was needed to exercise the stretch
finder at all. That instrumentation is reverted and the shipped jar is built from clean source.

---

## 0.0.33

One Courier's Hut holds a courier per level, and a warehouse takes twenty of them.

### Nine slots the game was willing to fill and no way to fill them

The Courier's Hut held exactly **one** courier — a bare constant `1` in its module registration,
ignoring the building level, where every other worker hut passes a function of it. The warehouse,
meanwhile, accepted `level * 2`. Measured here on a colony with 148 huts and 870 unemployed adults:

```
warehouse5 deliveryman 1/10
```

Nine places the warehouse was willing to fill, and the only way to fill them was to build more huts.
The hut was the constraint; the warehouse never was. That is worth stating plainly because the
opposite was the obvious guess, and it is the whole case for the change: raising the hut does not run
into a second ceiling, it spends headroom that has sat unused in every colony ever played.

The `1` is **inherited, not a port decision** — byte-identical in 1.21.1 and upstream. It is also
deliberate up there: the shipped *Minimum Order Quantity* research wants a **summed** Courier's Hut
level of 9, which one level-5 hut cannot reach, so upstream uses the constant to force a second hut.
That research is **left alone here**, and it is the one live question this change leaves behind: a
player who now builds a single level-5 hut has no reason to build a second and may quietly never
unlock it.

### The numbers, and why these curves

- **Courier's Hut**: one courier per building level — **5** at level 5.
- **Warehouse**: `level * 4` — 4, 8, 12, 16, **20**.

A linear multiple is the only shape that keeps the *ratio* fixed: the warehouse stays exactly four
huts ahead at every tier, so four level-2 huts fill a level-2 warehouse and four level-5 huts fill a
level-5 one. `level * level` reaches 25 at the top but collapses to **1** at level one — equal to the
hut and below today's 2, so a level-1 warehouse would have no headroom at all and a second level-1 hut
would immediately strand its courier. Rejected for that.

### The request system needed nothing; four other things did

Several couriers sharing one warehouse is not new — it is what "several courier huts" has always
meant. Requests land in a shared per-warehouse queue and a courier claims one by **removing** the
token into its own store, on the server thread, so two couriers cannot take the same request. Nothing
in the system knows which hut a courier sleeps in.

What did assume one courier per hut:

- **`getMaxParallelDeliveries` read `assignedCitizen.get(0)`** — every courier in a hut would have
  inherited the throughput of whoever happened to be listed first.
- **`canEat` read only the first citizen, and routed through `getCurrentTask()`** — which is *not*
  read-only: it claims a request off the shared queue. **A food check was handing out delivery work.**
  It now reads the claimed queue directly.

And two that only widening the cap exposed, because ten was never reachable and these loops had never
run near their stated width:

- **`DeliverymenRequestResolver.hasCouriers()`** tested `!getAssignedCitizen().isEmpty()`, and that
  getter returns a **fresh copy** of the list. So every call allocated a courier-sized list purely to
  ask whether one existed — on the request-resolution path, which is far hotter than any tick loop and
  grows with the cap. Now uses the allocation-free check that already existed.
- **`CourierAssignmentModule.onColonyTick`** wrapped that already-copied list in another copy. Honest
  sizing: at 25-second intervals this cost nothing even at twenty. Removed because it is provably
  redundant, not because it hurt.

### Verified, and the two things to eyeball

On a real server: the hut reads `1/5` and fills to **five couriers from one hut**, all adopted by the
warehouse, all working, job slots moving by exactly the expected amount and nothing else shifting.
To prove the *warehouse* cap binds, a throwaway jar with the hut at 25 was used against the real
shipped warehouse of 20: **25 offered, exactly 20 accepted**, five surplus idling gracefully. That
instrumentation was reverted and its absence checked in the shipped jar's bytecode.

**Not verified, because there is no game client here:** that the hut window lists five workers, and
that the warehouse's courier pane scrolls at twenty. That pane is `164 110` with 11-pixel rows —
**exactly ten rows, sized to the old cap**. It is a scrolling list, so it should be fine, but
truncation there would be a real bug and it wants a look.

One thing worth knowing regardless: `/mc colony diagnose` reported `No problems found` with five
couriers stranded without a warehouse. They have a work building, so no category matches. **Do not
read `No problems found` as proof of a balanced colony** — it will not tell you that you over-built
couriers.

---

## 0.0.32

You can find an aircraft now — and the reason you never saw one was in the other mod.

### The transport was invisible until it was overhead

Reported from play: an air raid landed pirates, but no aircraft was ever seen — "they just spawned in
mid-air and landed softly".

Simple Planes registered every airframe with `clientTrackingRange(5)`: **80 blocks**. That is the range
at which a server tells a client the entity exists at all. Against the drop run's own numbers — spawned
300 blocks out, cruising 1.2 blocks/tick, bay open 36 blocks from a drop point 70 blocks up — the
transport became visible for **under two seconds**, high overhead, and everything before that happened
to nobody. It was worse than cosmetic and not only about raids: guard towers engage aircraft at 160
blocks, so they were shooting at things the player could not see.

Two details explain why this went unnoticed for so long, and both were checked against the vanilla
source rather than assumed:

- `ChunkMap.TrackedEntity#getEffectiveRange` maximises over the entity's **passengers**, so anything
  with a player aboard was already tracked at the player's 32 chunks. Only **unmanned** aircraft were
  ever affected — which is precisely the autopilot and drop-run case this port invented.
- `updatePlayer` clamps the range to the player's view distance, so on a stock server any value of 10
  or more behaves identically. 10 buys the entire available fix; more would only bite where an
  operator raised view distance, and would push a fast entity's position stream onto distant players.

**The fix is in the Simple Planes repository, not in this jar.** Aircraft go to 10 chunks (vanilla's
own boat range), parachutes to 8 — argued separately, since an *occupied* canopy was never limited to
80 blocks and only empty ones vanished, at a different distance from an identical parachute with a
rider still on it. `updateInterval` stays at 3: these types broadcast velocity alongside position and
the client extrapolates between updates, so a slower interval is exactly what would make a wide radius
stutter. Bandwidth: the per-player rate does not change, only how many players pay it — at worst four
times the payers, about 400 B/s per player per aircraft, and roughly 38 kB/s server-wide with all 24
permitted autopilots flat out and crowded, which is less than sending a single chunk.

### `/mc aircraft [where|tp]`

`where` lists every aircraft nearest first: airframe, direction, ground distance, coordinates,
altitude, and what it is doing. It is deliberately **not** colony-scoped, because a transport 300
blocks out is outside every radius a colony has:

```
2 aircraft: 2 on a scripted flight.
  Plane to the South/East, 70 blocks out at 4 170 31, 170 blocks up.
    On an attack run against 300 70 300.
  Plane to the North/East, 352 blocks out at 101 150 -336, 150 blocks up.
    On a scripted flight, heading for 100 90 -400.
```

The second line is the point: found **inbound at 352 blocks**, because the autopilot registry knows
about a scripted flight from the instant it is ordered, at any distance. Only hand-flown or abandoned
aircraft need a world scan. The existing sighting code could not be reused — it reports a distant
aircraft only on an *attack* run, and a raid transport flies a plain route, so it would not have been
found until it was already overhead.

`tp` puts you **on the ground directly beneath** the aircraft, facing it — no fall, no effect, and the
aircraft ends up overhead at a distance equal to its own altitude, comfortably inside the new tracking
range. Without Simple Planes installed the command says so plainly, in words distinct from "nothing is
flying".

### The blast-protection patch is gone, absorbed by the mod it belonged to

Since 0.0.27 this repository carried a Simple Planes blast-guard patch and told the reader to apply
it by hand, because that repository was not writable from here. It is now. The patch is
**deleted**, its 287 lines live in Simple Planes as five real files, and every pointer at it has been
swept out of the documentation and the startup log line.

Simple Planes gained its own off switch for it — `/blastguard [status|on|off]`, in that mod's own
vocabulary, with no mention of colonies anywhere. "Off" is the pre-patch behaviour exactly: the filter
returns the blast it was handed without running a single guard. The default is on, which costs a
planes-only player nothing, because with no guard registered the filter returns immediately and the
two positions are the same server byte for byte.

**The reflective handshake between the two mods has now actually run**, for the first time since it was
written: with both jars on one server the startup log printed both halves, and `/blastguard status`
reported one guard registered — that is MineColonies, seen from the other side of the seam. What a
guard *decides* is still unverified: the test strike came down in a world with no colony.

### To actually see this

The aircraft half needs a **rebuilt Simple Planes jar**. This jar alone will not make aircraft visible
sooner — the range lives in the other mod's entity registration.

---

## 0.0.31

Enemy ground: mark it, see it in red, and keep your own people out of it.

### What a hostile territory is

An ordinary colony with a flag on it, no town hall, no citizens, nobody as its owner. That is the
cheapest thing it can be: ownership in this mod is one colony id per chunk plus a 256-bit column
mask, and everything that asks who owns a position — protection, the border on screen, the save
format, the client sync — is already written against a colony id. A second ownership layer would have
cost about fifty call sites and a new packet to buy the same answer.

It can sit **flush against your own border**. The minimum-distance rule between colonies turned out
to be a colony-*founding* check only; nothing in the claiming path consults it, so adjacency needed no
work at all. What a chunk cannot be is half yours and half theirs — one chunk has one owner, and the
column mask belongs to that owner. Bordering, and strips of no-man's-land between you, are both fine.

The flag exists for one reason: `getClosestColony` answers with the owner of the chunk you are
standing in before it measures any distance, so on enemy ground your own scepters would have started
editing the enemy. Territories are now skipped by a separate lookup that the three land scepters,
colony founding and the distance rule use; the renderer still asks the old one, because it has to
answer with the territory you are standing in.

### Making one

`/mc colony territory create <name> [<pos>]`, then `grow <colony> <radius>` for a quick square, or the
new **Territory Scepter** to take chunks one at a time for whichever territory it is pointed at
(`bind <colony>`), and the existing **Border Scepter** to shape them column by column. `delete` gives
every chunk back first — the ordinary colony delete only releases the square around the centre, which
a territory never had.

### Rectangles for the border painter

The Border Scepter painted one column per click. Left-click now marks a corner and the next
right-click fills the rectangle between them; sneak still means erase, so the same gesture rubs one
out. The bound is **4096 columns**, 64×64 — a mis-click bound, not a feature bound, since this is a
hand-drawn border and not a bulk claim. Crossing a chunk edge is allowed, pulling chunks in exactly as
a single click already does. The claim and unclaim scepters deliberately did **not** get rectangles:
theirs would be a rectangle of whole chunks, which is the bulk grab this feature is not.

### Citizens go around it

A citizen of your own colony pays **+25 per node** for a step inside hostile ground, walking or
swimming. A surcharge and not a veto, because of the two awkward cases: a citizen already standing
inside a territory has to be able to walk out, and a hostile strip cutting a colony in half must not
strand the far side. 25 reads as "one block of enemy ground is worth twenty-five of detour" and is
also the ceiling the node-budget bonus is clamped to, so a hostile step buys no more search budget
than any other expensive one.

Swimming needed a second fix. Walking and swimming share the expansion, so the surcharge covered
water for free — but the **macro edges skip up to 64 blocks unpriced**, which would have sailed a
citizen straight through enemy water. The boat probe and the rail walk now stop at the border and hand
the crossing back to block-by-block expansion.

A world with no territory in it pays one map lookup per path job and one reference comparison per
node. Nothing else in the search changes.

### The API the aircraft rules will hang off

`com.minecolonies.api.colony.territory` — `HostileTerritory.in(dimension)` for the whole map,
`at(dimension, pos)` for a single question, `anyExist()`. With no territory anywhere, `in()` is one
lookup keyed by dimension and returns null: nothing allocated, no colony resolved, no chunk touched.

**Every read is safe from any thread**, which the rest of the colony API is not —
`IColonyManager#getClaimData` is a `computeIfAbsent` on a plain `HashMap`, a read that writes, and
calling it off the server thread is a data race. The index is an immutable object rebuilt on the
server thread and published whole, so a flight tick or a pathfinding worker never has to touch the
live map. Rebuilds hang off `markDirty()`, which every edit already has to call, so no edit site can
forget one.

### A territory does not tick

`setOwnerAbandoned` does not stop a colony ticking, and a territory pressed against your colony has a
player in range nearly always, so it would have sat in `ACTIVE` running requests, the work manager,
waypoints, day-time checks, travellers and the raid check forever, for a thing with no citizens and no
buildings.

What still runs per tick for a territory: **one `updateSubscribers` call every 20 ticks, and nothing
else.** That one is kept deliberately — it is how the colony view, claim map and all, reaches the
client, and cutting it would have made the red border silently stop existing, which would have looked
exactly like a rendering bug. With nobody near it walks two empty sets; with somebody near, a packet
goes out only after an actual repaint, because the thing that sets the dirty flag on an ordinary
colony is not running. Territories are also skipped in the two other all-colony per-tick loops, the
aircraft watch and the anti-air battery.

### Red, and visible from your own side

Two things had to be fixed for the red border, and the study that preceded this work was wrong that it
came free:

- a new colony's team colour is white, so a territory would have drawn in the "this is yours" colour.
  The border colour now answers red for a hostile colony ahead of both existing branches.
- a client only receives a colony view by being a close subscriber, and you become one by **standing
  on** that colony's ground. For a territory that is backwards — the whole point is seeing the line
  from your own land. Everyone within 12 chunks of any of a territory's ground is now kept as a
  subscriber. Left alone, red would have appeared only after walking through the enemy's land once,
  and vanished on relog.

### Deliberately not built

Hostile mobs spawning inside a territory, and aircraft no-fly rules. Those are being written in a
separate mod; what this port owes it is a correct, queryable, visible ownership layer, which is what
the API above is.

### Five broken strings

`%d` does not render at all in this pipeline — vanilla throws on anything but `%s` and falls back to
showing the raw template. Five such strings belonging to the scepters touched here were fixed
(`scepterclaim.someowned`, `scepterclaim.success`, `scepterunclaim.success`, `scepterborder.info`,
`scepterborder.progress`). **165 more remain elsewhere in the file**, inherited from upstream and left
for a separate pass.

### Verified, and not

On a real server: territory creation, growth, the listing, deletion giving the chunks back, the flag
and claim surviving a stop and restart, and a fresh territory *not* being handed the usual starting
square on reload — a trap found in `onWorldLoad` that would have given it 81 chunks it never claimed.

Not verified, because there is no game client here: every scepter interaction, the rectangle fill, the
corner gesture, the tooltips, the red border on screen, and the citizen detour. The pathfinding cost
is reasoned from the node budget, not profiled.

---

## 0.0.30

Racks stop crashing Sodium's chunk builder.

### The rack asked the world to redraw it from the wrong thread

Reported from outside ([#24](https://github.com/unknown-wq/minecolonies/issues/24)): with Sodium
installed, walking near a colony crashed the client with *"Tried to access render state from outside
the main render thread"* while building chunk meshes. The block was always
`minecolonies:blockminecoloniesrack`.

Domum Ornamentum's model asks the rack's block entity for its texture data while baking, and the port
had put a lazy first-use refresh in that getter. The refresh ends in `level.sendBlockUpdated(...)` —
a request to rebuild the section — so a rack asked for a chunk rebuild from inside a chunk rebuild.
That in itself is only wasteful; what made it fatal is the thread. In 26.2 models bake on chunk
worker threads, and Sodium's `scheduleRebuild` asserts it is on the main render thread and takes the
chunk build down with it. Vanilla's renderer only marks the section dirty and never checks, which is
why the port shipped nine releases with this in it and nobody without Sodium ever saw it.

The refresh now runs on the client main thread: off-thread callers get the cache as it stands and the
refresh is queued, and when it finds something to change it issues its own block update, the section
is rebuilt, and the next bake reads the fresh map. The cost is at most one bake of a rack with bare
shelves. On the main thread it still runs inline, which is what keeps blueprint previews — baked on
the main thread against Structurize's fake level, whose `sendBlockUpdated` is a noop — filled in
their very first frame.

The same refresh also read the neighbouring rack out of the level (`getOtherChest()`), which was the
identical violation, just one that no assert was watching. It is on the main thread now too.

Two supporting changes: the "already refreshed" flag became atomic, so several meshing threads
reaching the same rack at once queue exactly one refresh rather than one each, and the cached texture
map became `volatile`, since it is now written on one thread and read on others.

The pass over the rest of the port for the same mistake found nothing else: the rack is the only
block entity in the port that implements Domum Ornamentum's texture-data interface, and no other
`sendBlockUpdated` call site is reachable from a render path. The port has no custom baked models,
block-colour providers or render-attachment implementations of its own.

**Not verified in play.** There is no client, no GPU and no Sodium in the build environment, so what
was checked is the code path and a clean server boot. That racks show their contents a frame or two
after coming into view, and that blueprint previews still fill in, wants a look in game.

---

## 0.0.29

The anti-air battery stops shouting, says where it is, and asks for its own arrows.

### The "no arrows" line came six and seven at a time

The throttle was written as `gameTime % 600 == 0` — a property of the **tick**, not of the colony. On
the one tick in six hundred that matched it, every dry tower in the colony reported in that same
frame, which is exactly the wall of identical lines. Dry towers are now collected during the colony's
own pass and reported **once** at the end of it, against a per-colony timestamp. Whichever tower gets
there first stamps it; the rest of that same tick already fail the test, so a single tick can no
longer produce two of these lines.

The interval is two minutes. The line only ever fires while a tower has an aircraft in its sights and
a firing solution it cannot take, so it cannot nag in peacetime however long the racks stay empty.
Two minutes is twice the sighting cooldown — an alarm you can do nothing about should come more often
than a chore you have to walk to — and long enough that one run-in is one line, but short enough that
a second wave on a still-empty tower says so while the aircraft is overhead.

### The line now names a tower and carries its coordinates

> §6The Guard Tower of ScaleTest has no arrows left for its anti-air position. It stands on the South
> side of the colony.§6 61 other guard towers are dry as well.

The "It stands on the … side" clause is underlined and hovers the block coordinates and the distance
from the centre, the same convention a citizen's death message uses. With several towers dry, the
line names the one **nearest the colony centre** — the shortest walk, and the point the direction and
distance are measured from — plus a count of the others. Never one line per tower; that was the bug.

### A dry tower now orders arrows itself

This was the missing half. Before, the tower complained and the player had to walk over with a stack
in hand, which is not how a single other building in this mod is supplied. A dry tower now files an
ordinary async request **as the building**, so the warehouse resolves it and a courier delivers into
the tower's racks — precisely where the battery looks for a round. Deduplicated against the tower's
own open requests. Citizen requests are deliberately ignored: a ranger's arrows go into the ranger's
inventory and never reach the racks.

In free mode the tower is simply handed a stack, the same way a ranger is handed arrows, so it never
runs dry, never asks and never warns.

### `/mc colony antiair <colony> [where|tp]`

`where` lists every emplacement with name, direction, distance, coordinates and arrow count. `tp`
puts you at the nearest dry tower — the same tower the chat line names, so the two never disagree. A
colony without Simple Planes loaded reports that it has no anti-air positions and behaves exactly as
before.

### Left alone deliberately

The aircraft **sighting** warning already had a correct per-colony cooldown and was not rewritten. It
still carries no coordinates, and that is on purpose: a position is for a static thing you go and
fix, and a plane crossing at nearly three blocks a tick is stale before the line is read. The
paradrop lines are one-shot and event-driven, so they need no throttle.

---

## 0.0.28

Three fixes, all found by playing.

### A citizen could believe its inventory was full when it was not

`InventoryCitizen` keeps a cached count of free slots, and only the methods that own a slot keep it
up to date. A slot emptied **through the ItemStack itself** — which is what a citizen eating its last
piece of food does — left the counter thinking the slot was still occupied. The drift only ever runs
one way, under-reporting free space, so the symptom is exactly "the worker acts full and the grid is
plainly not". It healed on the next colony save or client sync, which is why it came and went instead
of sticking.

The counter is kept as the optimisation it is: it is still trusted when it says there **is** room, and
only a "no room" answer is now verified by recounting the at-most-54-slot list. Upstream has the same
unchecked read, so this is a deliberate divergence, not a port repair.

### Free-mode food was reported as inedible

In free mode the cook's menu gains a free meal that is added when the menu is read rather than stored
in it — but the copy sent to the client was the stored one. So the tooltip told you the steak in a
citizen's hands was "not on the dining hall's menu" while that citizen was eating it perfectly well.
The client now receives the same menu the server serves from.

### A missing line of text

`com.minecolonies.core.item.food.tooltip.tier.0` did not exist, so the raw key was printed instead of
a description. Tier 0 is not an edge case — it is what *any* food below 12 nutrition scores, which is
most ordinary food. Missing upstream as well.

---

## 0.0.27

### Make citizens keep working: `/mc colony workoverride`

`/mc colony workoverride <colony> [mourning|night [on|off]]`. A family of "keep working anyway"
switches, per colony, stored in the colony, **every one off by default** — a save that never runs the
command behaves exactly as before. With no switch named it lists them and their state.

- **`mourning`** lifts only the work stoppage. The citizen still grieves: still counted as mourning,
  still carrying its deceased, still paying the happiness cost, still raising the mourning bubble.
  It simply does not down tools. This matters most with a short generational lifespan, where somebody
  dies more or less constantly and the town would otherwise never come out of mourning.
- **`night`** puts builders on a night shift. An overridden builder is **not** treated as sleep
  deprived — going to bed is what resets the slept-tonight happiness modifier, so the override resets
  it for them and the night shift costs no happiness. They keep their bed and their home; they just
  never walk to it. Guards are unaffected, they already work at night. Raids still send everybody
  indoors — that check sits above both switches.

### Blast protection, for aircraft

Every explosion the Simple Planes mod can produce — the craftable strike tool, the `/autopilot`
command, a bomber a hostile player flies in by hand, a downed aircraft, an ordinary crash — converges
on one method, and the guard now sits there. It honours the existing `turnoffexplosionsincolonies`
policy and the `EXPLODE` colony permission rather than adding a new config, and
`/mc colony blastprotection <colony> [on|off]` is the per-colony off switch the global config cannot
express.

**Vanilla explosions are not covered.** Creepers, TNT, beds in the Nether, and a bomb dropped from a
plane as a payload all go through Minecraft's own explosion path, which would need a vanilla hook —
and this port contains no mixins by design. What one mixin would cost and buy was written down
separately, so that call can be made on numbers rather than on a feeling.

The Simple Planes side shipped as a **patch** in this repository at the time, because that repository
was not this one's to push to, and until it was applied there this side was inert. *(It has since been
applied upstream and the patch file removed — all that is needed now is a Simple Planes build carrying
`xyz.przemyk.simpleplanes.api.BlastGuards`. That mod also has its own `/blastguard` switch.)*

---

## 0.0.26

**Simple Planes integration — optional.** Everything below needs the Simple Planes mod installed and
does nothing at all without it. This build was booted on a server with no Simple Planes present to
confirm a colony behaves exactly as it did before.

### Pirates arrive by air

A transport aircraft approaches from 300 blocks out at 70 blocks up, deliberately slowly so you can
see it coming and shoot at it, opens up over one of your buildings and drops pirates one at a time
under parachutes.

The raid bookkeeping assumed a raider that walks in from the border, and all three places that
mattered were fixed: raiders in unloaded chunks are no longer culled while still in the air, a killed
paratrooper is no longer replaced by an infantryman walking in from the edge (an air raid is one
wave, which is what makes the transport worth shooting at), and the raid direction reported to you
points at the drop zone. The worst case is also closed: a transport destroyed before it dropped
anybody used to leave the horde count untouched and **the raid would never end**. The horde is now
rewritten to whatever actually landed, and a wave stopped in the air ends the raid as a win.

### Guard towers shoot aircraft down

A guard tower of level 3 or better, with an archer and arrows in its chest, engages aircraft at **200
blocks**, one arrow per second, leading the target on three axes. Four hits bring one down.

That range is a design choice, not a number to minimise: 200 blocks against a strike run gives about
71 ticks of tracking and a kill takes about 80, so **one tower cannot stop a strike and two can**.

An aircraft at zero health does not fall — the mod's own death check requires it to be on the ground
already — so the kill works by taking away its autopilot. That also **disarms the warhead**: the
blast is read from the flight plan, and with no plan it falls back to an ordinary explosion, so a
power-16 incendiary block-breaker becomes ordinary TNT the moment the plan is gone. Outside your
claim the wreck is then left entirely alone: it falls, hits the ground and explodes where it lands,
which is the visible reward for hitting it. Inside your claim the last five blocks of the fall become
smoke and noise instead of a crater in your roof.

### An air-raid warning that arrives in time

The alarm reads the autopilot registry, so it sees a strike the moment it is ordered rather than when
it crosses your border: **14 seconds of warning instead of 3**. Manually flown aircraft are picked up
by a slow scan.

### What is verified, and what is not

Measured on a running server: an arrow does damage an aircraft, and four destroy it; a parachute
carries a mob 39.6 blocks over 20 seconds and lands it at full health with no fall damage; the
autopilot really does fly an unmanned route.

**Not verified: whether a raider stays seated on the parachute.** A colony with no player in the
world never becomes active, so no raid can be started on a headless server at all. Rather than ship
on a guess, raiders are given slow falling **and** the parachute — if one does slide off, it still
lands alive.

---

## 0.0.25

Two more fixes from the removal round. Both are crashes you would never have seen, because nothing
was shown in chat when they happened.

- **A hut with no blueprint path broke every button on itself.** The work order code cut the last
  character off the path to substitute the building level, which on an empty path is a negative
  index. It threw, and the exception took out the whole click — building, repairing and removing
  alike — writing a stack trace to the server log and telling the player nothing. Such huts are real
  enough that `/mc colony diagnose` already looks for them. The request is now refused with a message
  naming the hut and where it is.
- **`repairall`'s preview** reports a missing blueprint as a reason for skipping, instead of counting
  the building as repairable and then failing on it.

---

## 0.0.24

### Repair the whole colony with one command

`/mc colony repairall <colony> [preview]` files a repair work order for every building that can take
one. The hut GUI's repair button does one building at a time, which stops being usable somewhere
around the twentieth hut; this is that button pressed on all of them, through exactly the same code
path, so the work manager hands the orders out as usual.

- It reports rather than working silently: how many orders were queued, how many buildings were
  passed over and why, then the buildings themselves.
- `preview` lists what it would do and changes nothing.
- It leaves alone anything at level 0, anything that already has a work order, anything deconstructed
  (a repair order on one of those *rebuilds* it, which is not what "repair the colony" means), and
  anything with no builder able to reach it.
- Nothing is capped and nothing needs to be — the work manager assigns at most one order per builder
  and skips builders that already have one. Expect the whole colony to go striped with construction
  tape at once, and expect the repairs to cost materials.

### Work order requests say what they actually did

Asking for a removal or a repair used to answer "Build request created" regardless of what was
ordered. It now names the actual outcome, and a hut that was never built is handed back to you rather
than ordered torn down.

### Old age reaches the whole colony

Only affects saves with `generations = true`. A citizen could only die of old age while its entity
was loaded in the world. In a colony big enough for the mechanic to matter that is a small part of
the population at any moment, so deaths came in well below the configured rate and clustered around
wherever you were standing. Citizens away from the loaded world now die on schedule like everybody
else. Such a death leaves **no grave** — there is no loaded chunk to put one in — and the death
message says so, so you are not left hunting for one.

---

## 0.0.23

A single fix, but an important one: with generations turned on, a large colony could stop having
children entirely — and quietly stop running part of its colony tick with them.

### A colony that ran out of beds stopped ticking

Only affects saves with `generations = true`. Found from a player's server log, traced to
`ReproductionManager#trySpawnChild`.

- When generations is on, a birth no longer needs a free bed — but the newborn still needs a house to
  take its parents and family name from. The search for one only offered buildings **in loaded
  chunks**, which in a town large enough to run out of beds is usually none of them: the player stands
  in one corner of a colony that spans several chunks.
- With no house found, the code fell back to the town hall — which has no living quarters — and then
  asked it for its living-quarters module with a call that **throws** rather than returning nothing.
- The colony's state machine caught that exception and abandoned the rest of the tick. So the colony
  never had another child, and everything ordered after reproduction in the tick — the quest manager
  among it — silently stopped running too. Nothing appeared in chat; the only trace was a stack trace
  repeating in the server log every 25 seconds.

The fix looks for any residence regardless of whether its chunk is loaded, keeps the town hall as a
last resort for a colony that has not built a residence yet, and uses the non-throwing lookup so the
town hall case is handled instead of exploding. A child born with no house available is simply born
without inherited parents — homeless, as intended, and housed as soon as a bed frees up.

If your colony has been over its bed count with generations on, this is why births stopped. No save
repair is needed — it starts working again as soon as you load with this build.

---

## 0.0.22

Three pieces of work: guards, generations, and mixed seeds. The guard round is the first time anyone
in this port has watched one fight; the other two are new gameplay and both are off or opt-in, so a
save that ignores them behaves exactly as before.

### Generations — citizens age and die (off by default)

Turn on with `generations` in the server config, tune with `generationslifespandays` (default 100) and
`generationsbirthmodifier` (default 1.0). With it off, nothing changes at all.

- Citizens age and eventually die of old age, through the normal death path — so they get a grave, the
  undertaker comes, the job falls vacant and somebody is hired into it.
- **How fast you age depends on how well you live.** Happiness already accounts for housing, food,
  safety and health, so a homeless citizen in a starving town ages roughly twice as fast as a
  comfortable one, and a well-kept town gets a quarter more life out of everybody.
- **Children are now born whether or not there is a bed free.** This is deliberate: a housing shortage
  should be a problem you answer, not something that silently stops your town growing. The population
  cap still applies. A child with no bed is homeless, complains like any homeless citizen, and moves in
  as soon as a bed exists.
- **Birth rate responds to the colony**: food, happiness, crowding and the child-growth research
  multiply together. A model town breeds about 26 times faster than a starving one. The default
  lifespan is set so that births and deaths balance when fertility is 1.
- **Skills do not carry over.** A maxed-out worker dies with all its levels and a novice takes the job.
  The game's own inheritance softens this a little — children take their parents' skills ±2 when the
  parents share a house.
- New: **`/mc citizens fill <colony> children`** fills a colony with children instead of adults, for a
  town that has to grow up.

### Mixed seeds — a field can grow up to five crops

- The scarecrow window keeps its **Pick seed** button, which still means "this crop only", and gains
  **Mix in / take out** beside it, with a row of five icons showing the mix.
- Crops are sown in diagonal one-cell stripes. This is not decoration: a cell is tilled one day and
  sown the next, and the farmland laid down has to match the crop that will go in it, so the choice has
  to be a fixed function of the cell's position rather than a random pick.
- **Melons and pumpkins will not fruit in a mix.** A stem needs an empty square next to it and in a
  mix all four neighbours belong to other crops. Give them a field of their own.
- In normal play the farmer waits until it has *all* of the field's seeds; in free mode they are
  conjured, so it never waits.
- `/mc colony fieldseeds <colony>` lists every field and its seeds, and edits them by scarecrow
  position — useful as a colony-wide overview.
- Fields saved by older versions load normally as single-crop fields.

### Guards

- **A guard no longer twitches in and out of combat at an enemy it cannot reach.** Once the guard had
  given up on a target, the target search still reported it as found on every sweep, so the AI entered
  its attacking state and dropped straight back out, over and over, for as long as the enemy stood
  there. Measured on a test colony: 86 of these pointless round trips in 250 seconds became 11. This
  does not change which enemies a guard will fight.
- **`guardverticalvision`** (server config, `combat`, default `3` — unchanged behaviour). How far up
  and down a guard looks for enemies. At the stock value a knight standing four blocks from a zombie
  six blocks above him, in plain sight, never notices it at all — measured: zero target acquisitions
  in 250 seconds, while an archer beside him engaged the same target. That is the "my knights ignore
  the mob on the wall" complaint. Raising it makes guards fight more, which is a balance decision, so
  it is off by default; 8-12 covers a storey or two. It also widens the entity search each guard runs
  every four seconds, so do not set it to the maximum without reason.
- **`guardsfallasleep`** (server config, `combat`, default `true` — unchanged behaviour). Guards nod
  off on duty by day as well as by night, and a colleague walks over and hits them awake. Measured at
  between 2.6 % and 16.8 % of a guard's working day, plus another 1-5 % spent on the wake-up trip.
  Set it to `false` to switch the mechanic off.
- **Guard axe damage is confirmed as a deliberate divergence from upstream, and stays.** Upstream adds
  the axe's *spent durability* to the damage, so a huscarl hits for 3 with a fresh axe and up to 2034
  with a worn-out netherite one; this port adds the axe's real attack damage, giving a flat 9-12
  depending on tier, against 10 for a knight with a netherite sword. Numbers measured on a live
  server; the reasoning is now written into the code rather than left silent.
- **Guards do not visit the healer.** A wounded guard runs home and heals itself, which takes about
  75 seconds of standing still — and only if the *Regeneration* combat research is bought. Without it
  a guard fights to the death. Both measured.

---

## 0.0.21

Workers that stand around doing nothing, and three limits that were hardcoded.

The worker changes come from measuring a running server rather than reading code — an AI-state
sampler snapshotting every citizen 20 times a second. Worth knowing: **none of these are specific to
this Fabric port.** All 75 worker AI files were compared against the original NeoForge sources and
they match; everything below happens on any MineColonies.

### Fixed

- **A fisherman with no suitable pond nearby no longer grinds the server.** He was searching for water
  every four seconds, forever, and that search is the most expensive one in the game — measured at 8009
  search nodes and a give-up when there is no pond, against 205 when there is one, all on the single
  thread every citizen, animal and raider shares. He now waits longer and longer between attempts, up to
  a minute, and goes back to normal the moment water appears. He was already telling you in chat the
  whole time ("I can't find any suitable water for fishing! There should be an area of water at least 7
  blocks long and wide, and 2 blocks deep nearby") — that message is worth reading; it is exact.
- **Twenty seconds of standing still after every trip to the chest.** Measured on a lumberjack: 20.2
  seconds motionless, nearly 12 % of his working time. Two internal timers raced and the wrong one won,
  and its 20-second pause suppressed even "there is obviously work to do".
- **Emptying an inventory moved one slot per second.** A miner with twenty stacks stood at the chest for
  twenty seconds. Now up to five per visit.
- **`/mc citizens info` crashed** on a citizen who had never eaten — annoying, since that is the command
  you use to find out why someone is idle.

### Changed

- **`maxbuilderdistance`** (new, default **2000** blocks) — how far from a builder's hut a building may
  stand and still get built. This is the number behind *"There is no Builder close enough to work on
  this building!"*, and it used to be a hardcoded 100. Careful: the distance is three-dimensional, so a
  hut sunk deep into a mountainside can be out of range while a hut the same ground distance away on the
  flat is fine.
- **`maxpathfindingdistance` default raised from 900 to 2000** to match, since it decides whether a
  citizen is even allowed to walk that far. The check runs before any route is worked out, so **water
  and rails are refused at the same distance as broken ground** — a colony that ferries citizens across
  a strait needs this raised regardless of having boats.
- **`decorationsoutsidecolony`** (new, default **on**) — a decoration may now stick out past the colony
  border. Placing a wall or a road used to require every chunk it touched to belong to the colony, which
  is exactly not where you want to put a wall. Huts are unchanged. The anchor block still has to be
  inside the colony.

### Known side effect

Workers who always have something to do — lumberjack, farmer, planter, enchanter, alchemist — no longer
sit down or wander around the hut when idle, because the fix above removed the state that did it. Huts
will look a little less lively. Reports welcome on whether that trade is worth it.

---

## 0.0.20

Ten small changes, picked for value against size rather than as one theme.

### Fixed

- **The florist's flowers no longer vanish the moment they are planted.** In 26.2 a plant survives
  only if the block below it is in `#minecraft:supports_vegetation`, and composted dirt was in no soil
  tag at all — so every flower the florist placed was destroyed by the next neighbour update, and on
  adjacent cells each new planting killed the previous one in a cascade. On 1.21.1 this was handled by
  a NeoForge hook that Fabric has no counterpart for. Side effect, and it matches 1.21.1: you can now
  plant saplings and grass on composted dirt by hand. Crops still cannot go there — in 26.2
  `#supports_crops` is farmland and nothing else.
- **Chicken farmers in warm and cold biomes stop losing their eggs.** 26.2 chickens lay by variant —
  warm gives brown eggs, cold gives blue — and both the herder's pickup check and the hut's recipe
  display knew only the white one. Both now read `#minecraft:eggs`. Baker and chef recipes still ask
  for white eggs specifically; that is deliberate and unchanged for now.
- **A cavalryman accepts a vanilla spear.** 26.2 added seven spear items; the mod recognised only its
  own. Note that spear level is still measured against the mod spear, so a netherite spear counts as
  level 5 and only a max-level barracks will accept it — that is the usual "this tool is too good for
  this hut" rule, not a bug.
- **The colony stopped re-sending its entire claimed-chunk list in every update packet.** On a large
  claim that was the whole set, several times a second, to every nearby player.

### Changed

- **`stopsearchonarrival` now defaults to on.** Introduced switched off in 0.0.19 because nothing had
  been played with it. It makes a path search return once it has arrived and proved nothing cheaper
  remains, instead of expanding every node cheaper than the route it already found. Worth a few
  percent of the pathfinding thread on an ordinary colony, and 40–200× on rails, open water or a long
  road. Every measured route came back identical. Set it back to `false` in
  `config/minecolonies-server.toml` if citizens start routing oddly — it takes effect without a
  restart.

### Performance

- Path cost no longer draws from one `Random` shared by the whole mod. It was called once per
  considered step — 25.7 million times in 90 seconds on the measurement colony — and each call was a
  contended atomic operation.
- A cancelled path search is removed from the job queue instead of holding its slot.
- The pathfinding pool now actually shuts down when a single-player world is left, and can be
  restarted; two pools can no longer race into existence over one queue.

---

## 0.0.19

A performance release, and the first one here whose headline numbers were measured on a running
server rather than argued from source.

### Added

- **`stopsearchonarrival`** (shipped off, on by default from 0.0.20 — see above). Measured on
  corridor fixtures: 800 blocks of rail 6223 search nodes → 16; 796 blocks of open sea 8301 → 30;
  400 blocks of bare plain 8109 → 402. Identical path in every case. It also repairs a regression
  from 0.0.17, where the boat search's probe fan made open sea *dearer* to cross than before.
- **`maxpathfindingdistance`** (default 900, range 100–5000) — how far a citizen may be sent to walk
  in one go, previously hardcoded. Raising it does not make any single search work harder, but it only
  helps where there is a corridor: 3000 blocks along rails or open water computes, 3000 blocks over
  broken ground fails exactly as 150 blocks of broken ground already fails.
- **`freemodefarmerbatchsize`** (default 3, range 1–8) — a free-mode farmer tills, sows and fertilises
  up to this many cells from one stop. Measured 2.56 s per cell down to 1.19 s. Harvesting is
  deliberately not batched. **Normal mode is untouched**; at size 1 the code is the previous code.
- **`visitorspertavernlevel`** (default 3, per tavern level) and **`visitorintervalmodifier`** — the
  tavern's visitor ceiling and arrival pace. Raise both together: raising the ceiling alone fills it
  over about two hours, which reads as broken.

### Fixed

- **Rails are searched junction to junction** instead of one node per rail block. Three older bugs
  surfaced and were fixed with it: only the last block of a ride was flagged as track, which would
  eject a citizen one block after boarding; ride length was counted in nodes, so a 200-block ride read
  as 4 and failed the minimum-rails check; and the path's start point carried no rails flag.
- **The beekeeper's Flowers window is no longer empty.** The list was lost in the client sync, not in
  the tag. It is now read straight off `#minecraft:flowers` on both sides, which also picks up modded
  flowers that are tagged but appear in no creative tab.
- **A free-mode beekeeper no longer stands idle** waiting to be told which flowers to use — it uses
  all of them.
- **The mourning period after a visitor is killed had never once applied.** A building was tested
  against a module type it can never be. Same defect upstream.

---

## 0.0.18

Six pieces of work from a play session.

### Added

- **`/mc boatspeed [<blocks per second>]`** and config key `boatspeed` (default 6, range 2–20) — how
  fast citizens steer a colony boat, changeable without a rebuild and live for boats already crossing.
- **`/mc colony forceloadclaims <colony> [on|off|default]`** and config key `forceloadallclaims`
  (default off) — keep a colony's **whole claimed territory** loaded and ticking, not just the chunks
  with buildings on them, so citizens out on fields, roads and mine approaches stop freezing. Per
  colony, so one town on a shared server can have it without the rest. The command reports how many
  chunks are ticketed and how many are actually ticking right now.
- **`maxforcedchunks`** raised 256 → 1024. It is a ceiling **per colony**, not a shared pool; the old
  value was sized for building-only tickets and was silently cutting large colonies short.
- **Free mode stocks herder huts with their own animals**, up to the same cap the worker culls back
  to, instead of leaving them empty until livestock is walked in by hand.

### Fixed

- **Boats no longer stall on arrival.** A hull idling against a pier — sliding along the face it
  touches, bobbing — read as "still moving" every tick, so the rescue that ends a stuck crossing never
  fired. Arrival is now judged on whether the boat is getting closer, not on raw movement.
- **Mobs can no longer climb into a colony boat or minecart.** 26.2 rebuilt minecart boarding without
  a movement gate, so a cart left on the rails while its citizen finished a delivery was open to
  anything that touched it. Both vehicles now claim their seat before entering the world. A citizen
  whose path ends while still riding is now put down properly on rails as well as in water.
- **The farmer no longer strands a half-sown field.** Running out of seed marked the field as fully
  planted, so the next visit believed it was done and the field sat mostly bare forever. A seed that
  cannot actually be planted is now refused with a message naming the field, instead of tilling
  forever and planting nothing, and a trampled or dried-out cell is re-tilled in the same visit.
- **Farm animals stay in their pen.** Every mob in 26.2 carries a "home" that its own wander, panic
  and avoid goals respect — farm animals were simply never given one. Herder huts now set it to their
  own footprint. To take an animal out on purpose, leash it, lead it out and unleash it; that clears
  the home, as it does in vanilla. Off switch: `animalpencontainment`, with `animalpenslack` and
  `animalpenrecalldistance` to tune it.
- **The scarecrow renders correctly, with its lantern.** A stray humanoid body-and-hat shape was
  drawing underneath it, and the lantern on top — cut during the port because the drawing API it used
  no longer exists — is back, along with the placeholder shown over the colony flag while holding a
  banner.

---

## 0.0.17

- **Open water is crossed in one hop instead of one search node per block.** A long crossing used to
  cost thousands of search nodes.
- **Boarding is priced at the water's edge** rather than charging a swim-entry cost to a citizen who
  is about to get into a boat, so **a boat is worth taking from about 15 blocks of water instead of
  about 50**.

---

## 0.0.16

- **Breaking a rack, a grave or a hut no longer destroys everything inside it.** 26.2 moved the
  removal hook so it runs *after* the block entity is gone, and all three read the block entity back
  out of the level from inside it. Player break, explosion and blueprint replacement alike dropped
  nothing at all. A building upgrade still keeps its chest.
- **A rack placed by a blueprint no longer saves its contents against an empty registry**, which
  encoded anything with dynamic components to nothing.
- **The surviving half of a broken double rack no longer hands out its dead twin's nine slots**, where
  items put in were lost.
- **The purple rectangles on rack shelves are gone.** Empty display slots fell back to a missing
  texture after Domum Ornamentum's 26.2 rewrite dropped the erasure they relied on.
- The bundled **Domum Ornamentum** carries a pillar-model fix: twelve models never filled the six face
  slots they declared, which is fifteen unresolved-texture warnings on every resource reload.

---

## 0.0.15

- **Citizens no longer stop existing as entities in bulk.** A colony reported everyone standing about
  with a log full of duplicate-UUID errors: a *failed* spawn was being recorded as a successful one,
  because the port had substituted a check that asks a different question than the one upstream asks.
  Corrected in eleven places across the citizen, visitor and animal managers. Visitors had no guard at
  all, which is why they duplicated the most.
- **Boats sail straight instead of in an axis-aligned staircase** — the boat is now steered at the
  farthest node it has clear water to.
- **A crossing that ends against an awkward bank no longer strands its passenger forever.** Arrival is
  judged on proximity to the exit, and a boat that stops making progress is abandoned after three
  seconds and the citizen swims the rest.
- **Boat riders sat 0.6 blocks too high.** 26.2 moved the seat offset onto the passenger's entity
  type, which every player-shaped vanilla type declares and the citizen did not.
- **New: `/mc pathstats`** — measures why workers stand about. The pathfinding pool is one thread
  shared by every citizen, raider and animal on the server, and when it saturates a worker waits for a
  path it has already asked for with nothing in the game showing it. Reports queue wait, search time,
  budget exhaustion, repeat searches and refused walk orders. Off until `/mc pathstats on`.

---

## 0.0.14

- **Corrects the node-budget figure `/mc pathstats` printed.** Searches that finished normally were
  being counted as having given up. Outcomes are now arrived / stopped short / no path, which do add
  up to 100 %, with the budget on its own line.

---

## Earlier in the port

The foundation, before per-version notes were kept:

- **If your farmer never hoed the ground, that is fixed.** 26.2 split the vanilla `#minecraft:dirt`
  block tag and grass fell out of it, so every field laid on grass — that is, every normal field — was
  rejected cell by cell and the farmer walked it doing nothing.
- **Field assignment**: a farm across the map could hold a field the hut next door should have had. A
  new **field stick** marks a field out as a rectangle and binds it to a hut in one flow.
- **The farmer prepares its own ground**, clearing stone and gravel and laying dirt, never touching
  water. On by default; `farmerpreparesground` turns it off.
- **Enclaves** — chunks claimed well away from the colony — now work: buildings there claim their own
  ground, the enclave stays loaded, citizens get beds near their work, and `/mc colony rehouse` moves
  the ones already housed a thousand blocks away.
- **Free mode** (`/mc colony freemode <colony> on`): the colony works without any items at all, one
  switch per colony. It replaces the four hut checkboxes that used to do parts of this. Also lets a
  builder put a hut **straight to any level** in one work order, and lets a farm field be any shape.
- Various quality-of-life commands: `/mc colony diagnose`, `/mc citizens fill`, `/mc citizens
  maxstats`, `/mc citizens heal`, `/mc colony research completeall`, `/mc colony teachRecipes`, and a
  raid command that starts the raid **now**, takes `size` and `strength`, and can tell you where the
  raiders are (`/mc raid <colony> where`), teleport you to them, or call them off.

---

## A note on verification

There is no Minecraft client in the build environment used for this port, so **nothing here has been
verified by looking at it**. Every release is compiled and booted on a real Fabric dedicated server
with zero errors before it is published, and where a figure is quoted it was measured on a running
server. But no path has been walked, no cow contained and no scarecrow looked at by the person who
wrote the fix. Bug reports from actual play are the thing this port most needs.

Commands and config keys are documented in [`COMMANDS.md`](COMMANDS.md).
