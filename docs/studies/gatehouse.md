# The gatehouse: what it is, and five things to do with it

Design study, not an implementation. Date: 2026-08-15. Tree: `26.2/` in this repo, against 0.0.31,
which shipped hostile territories. Companion to [`hostile-territory.md`](hostile-territory.md) and
[`territory-mechanics.md`](territory-mechanics.md); both are assumed read, and their `file:line`
citations are not repeated except where a price depends on one.

Evidence standard, same as those two:

* **[VERIFIED]** — I read the source, the `file:line` is real and says what I claim it says.
* **[UNCHECKED]** — inference from the code, not observed. There is no game client here; nothing
  below was played.

Paths are relative to the repository root; `26.2/src/main/java/com/minecolonies/` is abbreviated to
`mc/`.

**Being built by other agents right now, and therefore not proposed again:** the chat line when you
cross into enemy ground, guard patrols along a border stretch, and raids that come out of enemy
ground.

---

## 0. The answer in one paragraph

The gatehouse is **not** a trade post and never has been, here or upstream. It is two things bolted
together: a small guard tower with four fixed posts, and **the endpoint of a colony-to-colony
diplomacy and fast-travel network** that is fully implemented, ships in this port, and is
essentially invisible in single-player because it needs a second colony to point at. Nothing in it
moves goods. The only item that ever changes hands is a gold-nugget toll a stranger pays to
teleport, half of which is banked in the gate's chest.

So the owner is half right. There is no trade post; there *is* a building on a boundary, with an
inventory, a courier feed, a permission hole that lets strangers touch it, a module-window
framework, and sixty blueprints across nineteen styles — which is to say **most of what makes
mechanics-study entry 10 cost 1,200 lines already exists**. That is the finding worth acting on.

---

## 1. What the gatehouse actually is today

### 1.1 As a building

`BuildingGateHouse extends AbstractBuildingGuards` (`mc/core/colony/buildings/workerbuildings/BuildingGateHouse.java:31`).
**[VERIFIED]** throughout this section.

| Property | Value | Site |
|---|---|---|
| Max level | 3 | `:38` |
| Level equivalent (for guard stats) | 1 → 1, 2 → 3, 3 → 5 | `:102`–`:111` |
| Claim radius | 1 chunk at levels 1–2, 2 chunks at level 3 | `:79`–`:87` |
| Guard task | hard-locked to `GUARD`; `getTask()` ignores the setting | `:152`–`:156` |
| Garrison | 2 knights + 2 rangers, one per blueprint tag | `ModBuildingsInitializer.java:680`–`:681`, `BuildingModules.java:580`–`:585` |
| Guard posts | `knight` / `archer` schematic tags; a missing tag logs an error and parks everyone on the hut block | `:120`–`:149` |
| Bonuses | vision and health scale with level equivalent | `:90`–`:99` |

Its module list is `KNIGHT_GATE_WORK`, `RANGER_GATE_WORK`, `GUARD_ENTITY_LIST`,
`GATE_GUARD_SETTINGS`, `MIN_STOCK`, `BED`, `STATS_MODULE`, `CONNECTION_MODULE`
(`ModBuildingsInitializer.java:675`–`:688`). Two of those matter later:

* **`MIN_STOCK`** (`MinimumStockModule`, `mc/core/colony/buildings/modules/MinimumStockModule.java:41`)
  is an `ITickingModule`. It is the existing pipeline by which goods you name are requested from the
  warehouse and physically carried into this building's racks by a courier. **A gatehouse can already
  be told to keep 64 iron ingots in stock, and the colony will do it.** It is building-level, not
  worker-level — `onColonyTick` (`:113`) raises a `MinimumStack` request whenever the building holds
  less than the target, and a courier resolves it; no assigned worker is involved. Two caveats worth
  carrying forward: it only runs while the gate's chunk is loaded (`:115`), and the number of
  distinct entries is capped at `buildingLevel * STOCK_PER_LEVEL` (`:64`–`:68`). **[VERIFIED]**
* **`GATE_GUARD_SETTINGS`** (`BuildingModules.java:597`–`:600`) is a `GuardTaskSetting` with exactly
  one option, `GUARD`. The gate's guards cannot be made to patrol.

Blueprint coverage is complete: 60 `military/gatehouse{1,2,3}.blueprint` files across 19 styles,
plus decorative `walls/*/gatehouse.blueprint` in some. I parsed four of them (medievaloak 1 and 3,
caledonia 3, nordic 1) out of NBT: each carries exactly **2 `knight` tags, 2 `archer` tags and 1
`gate` tag**, which is what `getGuardPos` and the teleport arrival both expect. **[VERIFIED]**

Recipe: planks + iron sword + bow + build tool (`DefaultRecipeProvider.java:270`–`:279`). Reachable
early.

One oddity: `BuildingStable.cavalryPatrolFilter()` includes the gatehouse
(`mc/core/colony/buildings/workerbuildings/BuildingStable.java:320`), so mounted patrols already
treat it as a place worth riding to. That is the only line in the mod that connects the gatehouse to
anything outside its own two systems.

### 1.2 As the endpoint of the colony connection network

This is the part that is not obvious from the name, and it is a real, finished feature.

**The road.** You craft colony signs (`DefaultRecipeProvider.java:281`, six per craft), sneak-click
a built gatehouse with one to bind the sign to that colony (`mc/core/items/ItemColonySign.java:58`–`:107`;
an unbuilt gate is refused with `COM_MINECOLONIES_SIGN_BAD_GATEHOUSE` at `:70`), then plant signs
every ≤50 blocks along a road. Each placement calls
`IColonyConnectionManager#addNewConnectionNode` (`ItemColonySign.java:185`), which starts a
**pathfinding job between the new sign and the previous node** (`PathJobSignConnection`, via
`ColonyConnectionManager.createSignPath:440`). The connection manager's tick resolves those jobs:
a sign whose path does not reach the previous node **is destroyed in the world** and the player is
told (`ColonyConnectionManager.java:324`–`:326`). So the road is verified walkable, not merely
declared. **[VERIFIED]**

The signs render as signposts pointing back along the chain with a cumulative distance, accumulated
in `TileEntityColonySign` (`mc/core/tileentities/TileEntityColonySign.java:199`–`:220`) and drawn by
`TileEntityColonySignRenderer`. **The total length of a finished road is therefore already computed
and cached in the world.** That is worth remembering for idea 5.

**The handshake.** Sneak-click the far colony's gatehouse (or its last sign) with a sign bound to
your colony and `attemptEstablishConnection` runs (`ItemColonySign.java:91`, `:136` →
`ColonyConnectionManager.java:191`). On success `connectToColony` (`:342`) walks both chains back to
a gatehouse at each end, refuses if either end does not terminate in one (`:364`, `:390`), and
records the pair in `directlyConnectedColonies` at `DiplomacyStatus.NEUTRAL` (`:397`–`:398`).

**The diplomacy.** The town hall's Alliance page (`mc/core/client/gui/townhall/WindowAlliancePage.java:180`–`:182`)
offers three buttons per connected colony — *request ally*, *start feud*, *set neutral* — which send
`TriggerConnectionEventMessage` and land in `triggerConnectionEvent`
(`ColonyConnectionManager.java:724`), mapping `ALLY_CONFIRMED → ALLIES`, `FEUD_STARTED → HOSTILE`,
`NEUTRAL_SET → NEUTRAL` (`:751`–`:757`). Allies propagate: an ally's allies show up in your
*indirectly connected* list (`:491`–`:501`).

**The fast travel.** From the gatehouse's own window you pick a connected colony and travel to it
(`mc/core/client/gui/modules/building/ConnectionModuleWindow.java:107`–`:120`). The button is live
only at `ALLIES` and only when the far gate's position is known (`:173`). The server checks the
alliance again (`TeleportToColonyMessage.java:100`), charges the toll, and lands you on the far
gatehouse's `gate` tag (`:134`–`:142`).

**The toll**, which is the only economics the gatehouse has: an *external* player pays
`distance / 125` gold nuggets (`ConnectionModuleWindow.java:111`, `:153`); members pay nothing. The
server deducts them and puts **half** into the destination gatehouse's inventory
(`TeleportToColonyMessage.java:110`–`:131`). The other half is destroyed.

**The permission hole, which is the most interesting single line in the building.**
`BlockHutGateHouse.canRightClickWithoutPermissions()` returns `true`
(`mc/core/blocks/huts/BlockHutGateHouse.java:50`), which makes `EventHandler` pass the interaction
through rather than refusing it (`mc/core/event/EventHandler.java:560`–`:563`), and a player with no
permission at all gets the travel window instead of a refusal
(`BlockHutGateHouse.java:76`–`:81`, `new ConnectionModuleWindow(building, true)`). **The gatehouse
is the only building in the mod deliberately open to strangers.** Every "the enemy comes to your
gate" idea below is standing on that line.

**The clock.** `connectionManager.tick()` runs from `Colony.onWorldTick` every 20 ticks
(`mc/core/colony/Colony.java:1650`–`:1653`) — and note it sits *after* the hostile bypass at
`:1644`, so a territory never runs it. Buildings tick from
`RegisteredStructureManager.onColonyTick:297` into `AbstractBuilding.onColonyTick:832`, which runs
every `ITickingModule`. **Anything at the gate has a clock, and it is the player's colony, never the
territory's.** **[VERIFIED]**

### 1.3 What it does *not* do

Stated plainly, because this is the part the owner may be misremembering.

* **No trade of any kind.** Nothing in the connection system moves an item between colonies. The
  gold nugget is a toll on a teleport, not a purchase. There is no stock, no price, no offer, no
  counterparty. `TeleportToColonyMessage.java:117` is the only line in the whole feature that puts
  an item into a container.
* **`DiplomacyStatus.HOSTILE` does nothing at all.** I grepped every use. Outside the connection
  package itself it appears in exactly two places: it hides a button
  (`WindowAlliancePage.java:180`–`:182`) and it fails a teleport
  (`TeleportToColonyMessage.java:100`, `ConnectionModuleWindow.java:173`). **Declaring a feud on a
  neighbour has no mechanical consequence whatsoever.** **[VERIFIED]**
* **`ConnectionEventType.DISCONNECTED` is never fired** by anything.
* **No relation to the colony border.** The gatehouse claims chunks like any building
  (`AbstractBuilding.java:318`, `:1100`) and gets no say in where the frontier is. Its guards are
  pinned to blueprint tags and cannot patrol.
* **No relation to hostile territories.** The word does not occur in any connection-system file. A
  territory has no gatehouse, cannot be a connection endpoint, and cannot appear in either list.
* **Dead in single-player.** The entire second half of the building requires a second colony with a
  built gatehouse. MineColonies defaults to one colony per player. In a solo world the gate is a
  four-guard tower with an empty window. **[UNCHECKED]** as a play observation, but it follows
  directly from `connectToColony` refusing any chain that does not end in another colony's gatehouse
  (`ColonyConnectionManager.java:364`, `:390`).

### 1.4 Upstream does no more

I diffed the reference: upstream MineColonies (`/workspace/ldtteam/minecolonies`, `2d453335`) has the
same 34 gatehouse references in the same files — same `BuildingGateHouse`, same
`ColonyConnectionManager`, same toll, same permission hole. The only structural difference is where
the schematic tag options are registered: upstream does it in `MineColonies.java:143`–`:145`, this
port in `MineColoniesClient.java:86`–`:88`, i.e. client-side only, which is correct since those
options exist for the scan-tool UI. **Nothing is missing from the port, and there is no upstream
feature to copy.** **[VERIFIED]**

### 1.5 Four defects found while reading

Not the deliverable, but cheap to fix and each is user-visible.

1. **`ColonyConnectionManager.java:112`** sends `COM_MINECOLONIES_SIGN_TOO_FAR` with `distance`,
   which at that point is *always* `Integer.MAX_VALUE` — the variable is only ever assigned inside
   the branch that also sets `potentialConnection`, and the message only fires when that is null.
   The player is told his nearest sign is **2147483647 blocks away**. **[VERIFIED]**
2. **The same lang key uses `%d`** (`manual_en_us.json:3253`), against this port's rule that lang
   keys use `%s`. It is the only `%d` I found in the connection strings.
3. **`ColonyConnectionManager.java:151`**, the missing-link guard, is dead:
   `if (tempNode == null && !gateHouses.contains(tempNode))`. `tempNode` comes from
   `ColonyConnectionNode#getPreviousNode`, which is initialised to `BlockPos.ZERO` and never null
   (`api/colony/connections/ColonyConnectionNode.java:18`), so the condition can never be true and
   `COM_MINECOLONIES_SIGN_MISSING_LINK` never reaches a player. **[VERIFIED]**
4. **`manual_en_us.json:3261`** is a truncated string: `"Attempted to connect to another colony, but
   the s"`. Present upstream too.

---

## 2. What the gatehouse gives a designer for free

Before the ideas, the ledger — this is why the prices below are what they are.

| Already exists | Where | Worth |
|---|---|---|
| A block, a hut, a building, a registry entry | `BlockHutGateHouse`, `ModBuildingsInitializer.java:675` | entry 10's block + BE + recipe |
| 60 blueprints, 19 styles, 3 levels, tagged | `26.2/src/main/resources/blueprints/*/military/gatehouse*.blueprint` | entry 10's blueprint |
| An inventory a stranger may reach | `TileEntityColonyBuilding#getInventory`, used at `TeleportToColonyMessage.java:117` | entry 10's stock container |
| A courier feed into it | `MIN_STOCK` on the entry, `MinimumStockModule` is `ITickingModule` | nothing in entry 10 — this is *better* |
| A module-window framework, no new packet | `ColonyConnectionModuleView`, `layoutcolonyconnection.xml` | entry 10's window + 2 packets |
| A clock that is not the territory's | `Colony.java:1652`, `AbstractBuilding.java:832` | the question entry 10 exists to answer |
| Strangers may interact without permission | `BlockHutGateHouse.java:50` | the thing entry 10 *cannot* have inside a territory |
| Four guards on fixed posts | `BuildingGateHouse.java:120` | a reason the place is defensible |
| A verified, length-known road to elsewhere | `ColonyConnectionManager.createSignPath:440`, `TileEntityColonySign.java:205` | idea 5's travel time |

Against the two threads the brief names:

* **Trade.** Yes — decisively. Entry 10 of the mechanics study priced ~1,200 lines because it had to
  invent a block, a block entity, a recipe, a blueprint, a standalone window and two packets before
  it could write a single line of economics. All six already exist here. What is genuinely left to
  write is the goods table, the moving rate, its lazy decay and the exchange itself. **The sweet
  spot between entries 7 and 10 is real and it is roughly a third of entry 10's price.** That is
  idea 3.
* **Borders.** Also yes, and it is the more evocative half. A gate is the thing a border has when the
  border is meant to move; this codebase now has a border that could move and no tool that moves it
  except a scepter click. That is idea 4.

---

## 3. The five, cheapest first

| # | Name | New / touched | Verdict |
|---|---|---|---|
| 1 | The gate names its enemies | ~95 / 2 | Correct, and it is **signage**. Fold it into 3 or 4, do not ship it alone. |
| 2 | A gate that charges | ~150 / 4 | Works, honest, **dull** — and multiplayer-only. The cheap-but-boring entry. |
| 3 | The gate pushes the frontier | ~230 / 4 | Build. The only thing in the mod that lets a *building* move a border. |
| 4 | The border post — the enemy buys at your gate | ~340 java + ~90 data / 3 | **Build this.** Most of entry 10 for a third of entry 10. |
| 5 | The caravan on the sign road | ~450 / 6 | Best-looking, worst-targeted. Servers only. |

Line counts are authored lines in this repo's house style, javadoc included, which runs long.

---

## Idea 1 — The gate names its enemies

**What the player experiences.** You open your gatehouse. Today you see two lists headed *directly
connected* and *indirectly connected*, both empty, forever. Now there is a third: **Frontiers**. It
names every hostile territory near you — *Blackreach, 210 blocks east, 81 chunks, Hostile* — with the
travel button greyed out and a tooltip saying there is no road to it. The building whose whole
purpose is "who is out there and what do we owe them" finally answers in a world with one colony.

**What it attaches to.** Client only. `ConnectionModuleWindow` builds its two lists at
`mc/core/client/gui/modules/building/ConnectionModuleWindow.java:84`–`:85`; a third is built the same
way from `IColonyManager#getColonyViews(Level)` (`mc/api/colony/IColonyManager.java:194`) filtered on
`IColonyView#isHostile()`, which is already on the wire — written at `mc/core/colony/ColonyView.java:389`,
read at `:1381`. **[VERIFIED]** The client has views of nearby territories because
`HostileTerritorySight.subscribeNearby` (`mc/core/colony/territory/HostileTerritorySight.java:65`)
exists precisely to push them. The window XML gains one `<list>`
(`assets/minecolonies/gui/layouthuts/layoutcolonyconnection.xml`), and the status column reuses
`DiplomacyStatus.HOSTILE.translationKey()`, which already has a lang string.

**Code size.** ~95 new lines in one client file, ~25 lines of XML, 4 lang keys. **No packets, no
saved data, no server code, and therefore no client-class-on-server-path risk** — everything touched
is already `@Environment(EnvType.CLIENT)` by virtue of living under `core/client/gui`.

**What could go wrong.** The list shows only territories the client holds a view of, so a territory
the player has never walked near is simply absent — which reads as a broken feature rather than a
range limit. Say the range in the window. Second, distance would naturally be measured to
`colony.getCenter()`, and a territory's centre is wherever the operator created it, not the nearest
red chunk; on a large hand-painted territory that number is wrong by a hundred blocks. Measure to
the nearest owned chunk from the view's claim data instead — the same data the border renderer
already walks. Performance: nil, a list rebuilt when a window opens.

**Is it actually good?** It is correct, it is cheap, and **it is signage, not a mechanic** — the same
verdict mechanics-study entry 1 got, for the same reason. Nothing changes in the world. But it is the
front page of ideas 3 and 4, and it is the difference between the gatehouse window being empty
forever in single-player and it having something in it. Build it *inside* whichever of the others
you pick; do not ship it alone.

---

## Idea 2 — A gate that charges

**What the player experiences.** A *Toll* setting on the gatehouse: an item and a number. Outsiders
using your gate to travel pay that, and the whole toll lands in the gate's chest where your couriers
pick it up. Set it to zero and the gate is free; set it high and your allies notice. The tooltip on
a gate you cannot afford already exists and already says the right thing
(`com.ldtteam.gatehouse.travel.cost`).

**What it attaches to.** The toll is already there, just hardcoded in three places: the client
computes `dist / 125` gold nuggets at `ConnectionModuleWindow.java:111` and `:153`, and the server
charges it and banks half at `TeleportToColonyMessage.java:110`–`:131`. Replace the constant with two
settings on the existing `GATE_GUARD_SETTINGS`-style module — `IntSetting`
(`mc/core/colony/buildings/modules/settings/IntSetting.java`, already exists) for the amount and a
`BlockSetting`-shaped item pick for the currency. The message already carries `cost` on the wire
(`TeleportToColonyMessage.java:83`), so **the packet does not change shape**; the server must simply
stop trusting the client's number and recompute it, which it does not do today. That is a small
security fix riding along.

**Code size.** ~150 new lines, 4 touched files, 2 lang keys plus 2 descriptions, no new packets, no
new saved data beyond the settings module's existing NBT.

**What could go wrong.** The client currently *computes* the price and the server *believes* it —
`cost` is read straight off the buffer (`TeleportToColonyMessage.java:67`) and charged unmodified
(`:110`), with no server-side recomputation anywhere. Anyone can send zero. **[VERIFIED]** Fixing that is the right
thing to do and is the only part of this idea with any teeth. Beyond that: a full destination chest
silently voids the toll (`InventoryUtils.addItemStackToItemHandler` at `:117` and `:126` — check the
return), and the half-that-vanishes at `:114` should probably stop vanishing if the toll is now the
player's choice. Performance: nil.

**Is it actually good?** **No — this is the cheap-and-dull one, and I am marking it as such.** It
polishes an existing transaction rather than creating a mechanic, it only exists when two human
players share a server, and in single-player it changes nothing at all. It is on the list because
the server-trusts-client bug inside it is real and someone should fix that regardless.

---

## Idea 3 — The gate pushes the frontier

**What the player experiences.** You build a gatehouse hard against enemy ground and level it up.
Once it is finished and manned, the red border in front of it starts to give: one chunk at a time,
over days, the frontier retreats past the gate. Break the gate, or let its four guards die and not
replace them, and the ground goes back. The gate is how your border advances, which is what a gate
has always been for.

**What it attaches to.** One verified line is the whole obstacle:
`ChunkClaimData.addBuildingClaim` takes ownership of a chunk **only when nobody owns it** —
`if (owningColony == NO_COLONY_ID) setOwningColony(...)` at
`mc/api/colony/claim/ChunkClaimData.java:239`. **[VERIFIED]** A gatehouse standing beside enemy
ground today already registers itself as a claiming building on those chunks (through
`ChunkDataHelper.tryClaimBuilding:509`, reached from `AbstractBuilding.java:318` and `:1100` via
`getClaimRadius`, which for the gatehouse is 1–2 chunks, `BuildingGateHouse.java:79`) and gets
nothing for it. The change is a narrow, gated branch — **put it in `tryClaimBuilding`, not in
`addBuildingClaim`, so the general claim path is untouched**: if the current owner is hostile
(`IColony#isHostile()`, `mc/api/colony/IColony.java:117`), and the claiming building is a level-3
gatehouse, and the pacing module says it is time, force the owner change with the existing
`ChunkDataHelper.tryClaim(..., forceOwnerChange = true)` (`mc/core/util/ChunkDataHelper.java:459`,
`:484`).

**Where the clock comes from**, since the territory has none: the gatehouse's own `ITickingModule`
on *your* colony's tick (`AbstractBuilding.java:832`–`:834`). One chunk per interval, measured in
in-game days. The territory is never asked to do anything.

**Code size.** ~230 new lines: a small pacing/reversal module on the gatehouse entry, the branch in
`tryClaimBuilding`, and the column-mask hygiene. 4 touched files
(`ChunkDataHelper`, `ModBuildingsInitializer`, `BuildingModules`, `NbtTagConstants`), 1 config key
(chunks per day), 5 lang keys, 1 NBT tag. **No packets** — the claim map already reaches every client
in every colony-view packet.

**What could go wrong.**

* **The stale column mask.** The 256-bit mask belongs to the chunk's owner
  (`ChunkClaimData.java:60`). A hand-painted enemy border left behind after the chunk changes hands
  silently shapes *your* claim. `ItemScepterTerritory.release` already does the right thing
  (`mc/core/items/ItemScepterTerritory.java:191`–`:197`); copy it. Same trap as mechanics-study
  entry 5.
* **The reversal is the risky half.** When the gate is destroyed the chunks must go back to the
  territory, and `ChunkClaimData.removeColony` picks a replacement owner in arbitrary hash order
  (hostile-territory study §C.3). Get it wrong and the chunk ends up unowned — which is *invisible*,
  because it looks exactly like a hole in the red border rather than a bug.
* **Bandwidth.** `ColonyView.serializeNetworkData` writes the **entire** claim map into **every**
  colony-view packet with no dirty check (`mc/core/colony/ColonyView.java:404` onward). A claim that
  moves on a fast timer is a per-subscriber bandwidth bug. Pacing it in days rather than seconds is
  not just flavour, it is the mitigation. **[VERIFIED]**
* Performance otherwise: one chunk considered per gate per interval.

**Is it actually good?** Yes, and it is the most *gate-like* thing available. It is cheaper than
mechanics-study entry 9 (~430, the countdown version) and better fiction than entry 5 (~130, which
that study fairly called "shopping, not war"), because the price of a chunk is a built, manned,
breakable building rather than a click. Its honest weakness is that it is **one-directional**: the
enemy never pushes back, because a territory does not tick and has nobody to send. It closes the
loop properly only once raids-out-of-enemy-ground lands — which another agent is building right now,
so that pairing is free.

---

## Idea 4 — The border post: the enemy buys at your gate

**What the player experiences.** A hostile territory sits across the valley. Your gatehouse's window
gains a page, *Frontier trade*, and on it a standing offer from the ground opposite: **Blackreach
wants iron ingots. 3 emeralds each, up to 64.** You put iron in the gate's chest — or set the gate's
minimum stock and let your couriers do it, which they already will. Between one dawn and the next
the iron is gone and the emeralds are there. Sell them iron all week and the price they will pay
falls; leave them alone a while and it recovers. What they want changes. You never see who takes it.

**What it attaches to.**

* **The counterparty**: `HostileTerritory.at(dimension, pos)` / `HostileTerritory.in(dimension)`
  (`mc/api/colony/territory/HostileTerritory.java`), the immutable index. One hash lookup, safe from
  any thread, `null` when no territory exists anywhere — so the module's fast path in a normal world
  is a null check.
* **The place**: the gatehouse's own inventory, `TileEntityColonyBuilding#getInventory`, which the
  existing toll already writes into at `TeleportToColonyMessage.java:117`. **[VERIFIED]**
* **The delivery**: `MIN_STOCK` is already on the building entry
  (`ModBuildingsInitializer.java:685`) and `MinimumStockModule` is already an `ITickingModule`
  (`mc/core/colony/buildings/modules/MinimumStockModule.java:41`). The "how do goods physically get
  to the border" problem — which entry 10 never solved — is solved.
* **The window**: a new module view beside `ColonyConnectionModuleView`
  (`mc/core/colony/buildings/moduleviews/ColonyConnectionModuleView.java`), rendered as a page of the
  hut GUI. Module views ride the existing building-view serialisation, so **no new packet**.
* **The clock**: `AbstractBuilding.onColonyTick:832` → the module. **Your** colony's tick, never the
  territory's.
* **The state**: the offer table and the moving rate live on the *territory* `Colony`, so two gates
  facing the same enemy see one market and one stock, persisted in territory NBT beside the
  `hostile` flag (read `Colony.java:1216`, written `:1385`) and **decayed lazily on read** from
  `getGameTime()` — the same trick mechanics-study entry 6 uses for its alarm, and for the same
  reason: a territory has no tick to decay anything in.

**Code size.** ~340 new lines of Java in 3 new files (the module, the module view, the offer/rate
record) and 3 touched (`ModBuildingsInitializer`, `BuildingModules`, `NbtTagConstants`); ~60 lines of
window XML; ~90 lines of datapack goods table; ~18 lang keys; 1 config key (how near the enemy must
be); 3 NBT tags. **No packets. No new block, block entity, recipe or blueprint.**

**Why this is a third of entry 10 and not a rounding error off it.** Entry 10's 1,200 lines were: a
block, a block entity, a BlockUI window, two packets, the stock/price state, a goods table, ~40 lang
keys, a recipe and a blueprint. Six of those nine already exist and are wired. What is genuinely
left is the goods table, the rate and its decay, and the exchange — which is exactly the part that
was interesting. **This is the sweet spot between entries 7 and 10 that the brief asks about, and it
is a week rather than a month.**

It is also strictly better placed than entry 10 was. That entry noted a trade post cannot stand
*inside* a territory, because a hostile territory is protected at `NEUTRAL` rank for every player
(`mc/core/colony/permissions/Permissions.java:788`–`:793`), so the post had to sit on the player's
side of the line anyway. The gatehouse is already on the player's side of the line and is already
the one building strangers may touch (`BlockHutGateHouse.java:50`). The fiction and the code agree
for once.

**What could go wrong.**

* **Duplication.** Do the exchange **entirely server-side inside the module tick**, atomically within
  one tick, and let the window only display. Never a two-tick "took the goods, will pay next tick" —
  a player breaking the gate between those two ticks is a free item printer. The building's inventory
  can also be opened by hand while the module runs; take and give in the same synchronous block.
* **A full chest.** The payment must check capacity before removing the goods, or it voids itself —
  the existing toll has exactly this bug at `TeleportToColonyMessage.java:117` and `:126` (return
  value ignored). Do not copy it.
* **Goods only arrive while somebody is there.** `MinimumStockModule.onColonyTick` is gated on
  `WorldUtil.isBlockLoaded` (`:115`), so a gate on an unvisited frontier is never restocked. That is
  correct behaviour but it will read as the couriers ignoring the gate; say it in the window, or
  make the exchange itself skip an unloaded gate rather than trading out of an empty chest.
* **The rate must not ride the territory index.** `HostileTerritoryIndex.refresh` rebuilds the index
  whole and republishes it on every change; a price that moves must live beside it, not in it. Same
  trap mechanics-study entry 6 names. **[VERIFIED]** that `refresh` rebuilds from scratch.
* Performance: one module tick per gatehouse per colony tick, returning immediately when
  `HostileTerritory.in(dimension)` is null — i.e. always, in a world with no territory.

**Is it actually good?** **Yes, and it is the one I would build.** It is the trade thread at a third
of the price, it works in single-player against ground the player painted himself, it needs no clock
the world does not already have, and it puts an economy on the border rather than in a menu. The
honest weakness: it is still a vending machine, only one that changes its prices. The enemy never
refuses you, never sends anything back, and never notices that you are also taking their chunks.
If you want it to notice, gate the offers on mechanics-study entry 6's alarm — about 40 more lines,
and it turns "the enemy buys" into "the enemy will not deal with you any more", which is the first
time the red rectangle would have an opinion.

---

## Idea 5 — The caravan on the sign road

**What the player experiences.** Two colonies, a road of signs between them, peace declared. From
either gatehouse: *Send a caravan.* Four citizens leave with what is in the gate's chest and are
gone — really gone, not standing about invisible — for as long as the road is long. They come back
with the far colony's goods. If the road ran through enemy ground, some of them do not come back,
and neither does some of the cargo.

**What it attaches to.** `ITravellingManager` and `mc/core/colony/managers/TravellingManager.java`,
with the working ten-line precedent `EntityAIWorkNether.goToVault`
(`mc/core/entity/ai/workers/production/EntityAIWorkNether.java:181`–`:190`) — a sound,
`startTravellingTo(citizenData, pos, ticks)`, `worker.remove(DISCARDED)`; return at `:192`–`:208`.
Mechanics-study entry 8 already priced and verified this machinery. What the gatehouse adds is that
**the two hard inputs are free**: the trip time is the road's own length, already accumulated and
cached in `TileEntityColonySign` (`mc/core/tileentities/TileEntityColonySign.java:205`–`:213`); and
the risk is one walk of the sign chain asking `HostileTerritory.at` at each node, so "what fraction
of this road runs through enemy ground" is the loss chance, computed once when the caravan sets out.
The endpoints are the two gatehouse inventories. The clock is your colony, which already ticks the
travelling manager.

**Code size.** ~450 new lines, 6 touched files, 2 NBT tags, ~14 lang keys, 1 goods/loot table, no
new packets beyond the module view.

**What could go wrong.** This is the entry where **you can lose citizens permanently**, which is the
worst defect class this mod has. `TravellingManager.recallAllTravellingCitizens` (`:69`–`:96`) exists
for exactly that and even logs *"has returned very confused"* when the entity is missing (`:88`);
honour it, and test a save/reload with a caravan out before shipping. Second: a road can be broken
while the caravan is on it — a sign destroyed mid-trip leaves the destination unreachable, and the
chain's `previousNode` is already reset to `BlockPos.ZERO` in that case
(`ColonyConnectionManager.removeConnectionNode:171`–`:188`), so there is a signal to react to.
Performance: negative, four fewer AIs ticking.

**Is it actually good?** It is the best-*looking* idea here and the worst-*targeted* one. **It
requires two colonies**, which on a solo world means the player must own two, against the default
config. Everything about it is charming on a server and dead content otherwise. Build it only if
this port is aimed at servers; if the owner plays alone, idea 4 is the same fiction — goods leaving
your gate and payment arriving — with a counterparty that actually exists in his world.

---

## 4. Recommendation

**Build idea 4, the border post, with idea 1 as its front page.**

It answers the trade question the owner was actually asking, at roughly a third of the month that
mechanics-study entry 10 would have cost, and for the specific reason he half-remembered: the
gatehouse really does already carry most of a trade post — a block, sixty blueprints, an inventory, a
courier feed into that inventory, a window framework that needs no packet, a clock that is not the
territory's, and a standing permission hole that makes it the one building in the mod meant to face
outward. What is left to write is the interesting part.

If the owner would rather the border *moved* than the goods, **idea 3** is a hundred lines cheaper
and is the only mechanic in this repo or the previous study where a building — rather than a scepter
click — changes who owns the ground. It pairs for free with the raids-from-enemy-ground work already
in flight.

Ideas 1 and 2 are not features. Ideas 1 is a page of a window; idea 2 is a settings row wrapped
around a bug fix that should happen anyway.
