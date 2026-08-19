# Ten mechanics built on hostile territory

Design study, not an implementation. Date: 2026-08-15. Tree: `26.2/` in this repo, against
0.0.31 (`9a1a1bc3`), which shipped hostile territories. Companion to
[`hostile-territory.md`](hostile-territory.md), which mapped the ownership layer; that study is
assumed read and its `file:line` citations are not repeated here except where a price depends on
one.

Evidence standard, same as the previous study:

* **[VERIFIED]** — I read the source, the `file:line` is real and says what I claim it says.
* **[UNCHECKED]** — inference from the code, not observed.

Unlike last time, **a server was run**. A dedicated server on port 25873 with `dist/minecolonies-26.2-0.0.31.jar`
and the `colony-1000` test world was booted twice; what it confirmed is listed in the appendix.
There is still no game client here, so nothing was seen on screen.

Paths are relative to the repository root; `26.2/src/main/java/com/minecolonies/` is abbreviated to
`mc/`.

**Out of scope by instruction:** hostile mob spawning inside a territory, and aircraft no-fly rules.
Both are being written against the public API in a separate mod. They are referred to below as
context — several entries here are worth more when those two exist, and entry 6 exists mainly to
feed them.

---

## 0. The ten, cheapest first

| # | Name | New / touched | Verdict |
|---|---|---|---|
| 1 | The frontier announces itself | ~45 / 1 | Cheap and correct. **Signage, not a mechanic** — fold it into something else. |
| 2 | The red border without a scepter | ~55 / 2 | Build. Cheapest thing here that changes how the world looks all the time. |
| 3 | Raids come out of enemy ground | ~100 / 2 | **Build this first.** Almost free, pays off in one in-game night, all the UI already reports it. |
| 4 | Guard towers that walk the line | ~115 / 3 | Build. The first thing a player *does* about a territory. |
| 5 | The claim jump (seed 1, cheap) | ~130 / 3 | Works, needs no format change. But it is shopping, not war. |
| 6 | The territory notices you | ~185 / 3 | Build, especially given the spawner and the no-fly rules coming. |
| 7 | The enemy buys (seed 2, cheap) | ~60 java + ~200 data / 2 | Cheap, gives the enemy a voice. A vending machine wearing a story. |
| 8 | The raiding party | ~360 / 6 | **Best idea on the list at any price.** Every hard part has an oracle in this repo. |
| 9 | Contested ground (seed 1, full) | ~430 / 7 | The only entry that lets the border *move*. Four times entry 5 for a countdown. |
| 10 | The trade post (seed 2, full) | ~1200 / 8 | The real answer to "a rate that moves". A month, not a weekend. **Do not start here.** |

Line counts are authored lines including javadoc in this repo's house style, which runs long — a
40-line class here is a 15-line class anywhere else. Data (JSON, lang) is counted separately where
it dominates.

---

## 1. The frontier announces itself

**What the player experiences.** You walk east out of your fields and a line appears in chat:
*"You have crossed into Blackreach."* Walk back and it says you have left it. The name is the one
the operator gave the territory, so the ground on screen and the ground in the message are the same
place.

**What it attaches to.** `EventHandler.onEnteringChunk` (`mc/core/event/EventHandler.java:303`),
which is driven from `onServerTick` over the player list (`:871`), fires only when the player's
chunk actually changes (`:311`–`:318`), and already calls
`HostileTerritorySight.subscribeNearby(world, player, chunkPos)` at `:346`. The new call goes next
to it and asks `HostileTerritory.at(level.dimension(), pos)` plus
`HostileTerritoryMap.name(id)` — one hash lookup, no colony resolved, no chunk touched.
**[VERIFIED]**

**Code size.** ~45 new lines in one small class under `mc/core/colony/territory/`, one touched file
(`EventHandler`), two lang keys in `manual_en_us.json`. Per-player state is one `Map<UUID, Integer>`
of "the territory you were last in", exactly the shape of the existing `playerPositions` map
(`EventHandler.java:317`). No packets, no saved data, no commands.

**What could go wrong.** The hook is chunk-granular and gated on `getGameTime() % 100`, so a message
can lag up to five seconds and a fast diagonal across a hand-painted column strip can be missed
entirely — the border scepter paints at column precision but this hook only sees chunks.
**[UNCHECKED]** how often that shows. The map needs clearing in `onPlayerLeaveWorld` or it holds a
UUID per player who ever logged in. Performance: nil — one map probe per player per chunk change,
and `HostileTerritory.in` returns null in a world with no territory.

**Is it actually good?** It is cheap and it is correct, and it is **signage, not a mechanic**. On
its own I would not ship a release for it. Every other entry on this list reads better with it, so
build it inside whichever one you pick.

---

## 2. The red border without a scepter

**What the player experiences.** The red lines are simply there, all the time, whatever you are
holding. Your own border and other people's still appear only with the build tool or a scepter, as
now.

**What it attaches to.** `ColonyBorderRenderer.render` (`mc/core/client/render/worldevent/ColonyBorderRenderer.java:74`),
whose first line is the gate `if (!showsBorders(ctx.mainHandItem) || !ctx.hasNearestColony()) return;`
(`:76`), and `showsBorders` (`:327`). The change is a mode flag: when the held item does not show
borders, build the maps as now and then drop every entry whose colony is not hostile before calling
`draw` (`:149`) and `drawPartialClaims` (`:445`). `draw` already skips id 0, so "drop" means
"set to `NO_COLONY_ID`". Colour needs nothing: `borderColour` (`:391`) already returns
`HOSTILE_BORDER` first, via `isHostile` (`:423`), ahead of the team-colour branch. **[VERIFIED]**
The geometry cache must learn the mode — `lastClaimSignature` (`:72`) needs a companion
`lastHostileOnly`, or the picture will not change when you put the scepter away.

**Code size.** ~55 new lines in one file, plus one client config key in `ClientConfiguration`
and one lang key for it. No packets, no saved data, no server code at all — and therefore no
client-class-on-server-path risk.

**What could go wrong.** Two real things.

1. **`ctx.hasNearestColony()` still gates it.** `WorldEventContext.nearestColony` is
   `getClosestColonyView(level, player)`, so a player standing next to a territory but far from any
   colony of his own sees nothing. Inside your own land, which is where you want to look at the
   enemy's border from, this is fine. Lifting it properly means sourcing the dimension and the
   render range from something other than `nearestColony`, which is another ~30 lines and I would
   not pay it in a first version. **[VERIFIED]** that the gate is there; **[UNCHECKED]** how often
   it bites in play.
2. **Performance.** The chunk walk is `(2·range+1)²` client `getChunk` calls with
   `range = max(clientRenderDist, maxColonySize)` (`:95`), cached and invalidated by `claimSignature`
   over a 5×5 window (`:342`). Today that loop only runs while a scepter is held. Always-on means it
   runs on every chunk change forever. Add an early-out: if no colony view in this dimension is
   hostile, return before the loop. **[VERIFIED]** the arithmetic; **[UNCHECKED]** the cost.

**Is it actually good?** Yes. The feature is half-invisible without it — a border you have to hold a
tool to see is a border you forget is there. It is also the entry with the shortest path from
"merged" to "the owner notices".

---

## 3. Raids come out of enemy ground

**What the player experiences.** The raid warning reads *"Barbarians approach from the east!"* and
the east is where you painted Blackreach. The raiders come out of the marked ground rather than off
a random circle, and the barracks window's spawn-point list points back at the territory.

**What it attaches to.** `RaidManager.raiderEvent` (`mc/core/colony/events/raid/RaidManager.java:291`).
It already branches on an explicit location — `if (raidSettings.location() != null)` at `:329`,
otherwise the loop at `:335` calling `calculateSpawnLocation()` (`:618`). The new private
`territorySpawnLocation()` goes in that else branch, tried first: ask `HostileTerritory.in(dimension)`,
walk the territory's chunks within raid distance of `colony.getCenter()`, pick one that
`WorldUtil.isEntityBlockLoaded` accepts, and floor it with the same
`BlockPosUtil.findAround(..., SOLID_AIR_POS_SELECTOR)` call `calculateSpawnLocation` already uses at
`:665`.

The thing standing in the way today is `isOtherColony` (`:768`), which returns true for any chunk
owned by a colony that is not this one — a hostile territory included — and is consulted from
`findSpawnPointInDirections` (`:732`) and `forcedSpawnLocation` (`:585`). It needs a carve-out for
hostile ids. **[VERIFIED]**

Everything downstream is free. `HordeRaidEvent.onStart` (`:381`) already prints
`BlockPosUtil.calcDirection(colony.getCenter(), spawnPoint)`, `AbstractShipRaidEvent:220` the same,
and `WindowBarracksBuilding:116` already draws `getLastSpawnPoints()`. **[VERIFIED]**

**Code size.** ~90 new lines (one private method plus its javadoc) and ~10 touched in
`RaidManager`; one config key for the chance that a raid prefers a territory when one is in range.
No new lang key is strictly needed — the existing message already says the right thing — though one
naming the territory is worth having. No packets, no saved data, no new files.

**What could go wrong.**

* **Unloaded ground.** `calculateSpawnLocation` deliberately only walks loaded chunks and gives up
  at the first unloaded one; a territory 300 blocks out is usually not loaded. A spawn point picked
  there produces the "raid bar with no raiders under it" state that `COMMANDS.md:158`–`:165` already
  documents at length. Guard on `WorldUtil.isEntityBlockLoaded` and fall back to the ordinary search
  rather than forcing a chunk load on the server thread. **[VERIFIED]** the mechanism.
* **Raiders paying their own surcharge.** They do not.
  `AbstractPathJob.hostileGroundFor` (`mc/core/entity/pathfinding/pathjobs/AbstractPathJob.java:2387`)
  returns null unless the mover is an `AbstractEntityCitizen`, so a raider spawned inside a territory
  walks out of it unpriced. **[VERIFIED]** — this was the one thing that could have made the idea
  not work, and it does not.
* Performance: one pass over the territory's chunk set, once per raid, on the server thread. A
  territory of 81 chunks is 81 probes. Nothing.

**Is it actually good?** **Yes, and it is the one I would build first.** It costs a day, it needs no
new UI because every raid readout already reports direction and spawn point, and it turns a red
rectangle into the reason your colony is on fire — within one in-game night, not a hundred hours.

---

## 4. Guard towers that walk the line

**What the player experiences.** A third patrol mode in the guard tower's settings, next to Auto and
Manual: **Border**. Guards on it stop wandering between huts and walk your side of the frontier
instead, back and forth along the chunks that touch enemy ground.

**What it attaches to.** `AbstractBuildingGuards.getNextPatrolTarget`
(`mc/core/colony/buildings/AbstractBuildingGuards.java:427`, the non-manual branch) and
`getRandomPatrolTarget` (`:486`), which today returns
`colony.getServerBuildingManager().getRandomBuilding(...)`. The setting is
`GuardPatrolModeSetting` (`mc/core/colony/buildings/modules/settings/GuardPatrolModeSetting.java`),
today a two-value `StringSettingWithDesc` of `AUTO` and `MANUAL`.

**Adding a third value is safe on existing saves**, which is the part I checked and did not assume:
`SettingsModule.deserializeNBT` calls `setting.updateSetting(settings.get(settingsKey))`
(`mc/core/colony/buildings/modules/SettingsModule.java:72`), and `StringSetting.updateSetting`
(`.../settings/StringSetting.java:169`–`:180`) clears the loaded option list, copies the *default's*
list in, and clamps the index. So every guard tower already in a world picks the new option up on
load. `SettingsFactories.GuardPatrolModeSettingFactory` (`:404`) is list-generic and needs no
change. **[VERIFIED]**

The finder itself reads `HostileTerritory.in(dimension)` and asks `chunkTerritory(x, z)` over the
tower's `getPatrolDistance()` (`:492`) box, keeping chunks where the answer is `NO_TERRITORY` but a
4-neighbour's is not.

**Code size.** ~115 new lines (the border-point finder, the setting constant, the override), 3
touched files, 1 lang key for the option and 1 for its description. No packets beyond the existing
settings sync, no saved data, no new registry entry.

**What could go wrong.** The patrol point **must land on your side of the line.** Put it one column
inside the territory and every guard pays the +25 per node
(`AbstractPathJob.java:139`, applied at `:2009`) and detours around the place you sent him, which
reads as the guards being broken. Second, `getNextPatrolTarget` clamps any target further than
`getPatrolDistance()` back to the tower itself (`:454`), so a tower well inside the colony silently
does nothing on this mode — say so in the setting's description rather than letting the player
discover it. Performance: the box scan runs once per patrol point, on the server thread, into the
immutable index — a 40-block patrol distance is a 6×6 chunk scan.

**Is it actually good?** Yes. It is the first entry where the player *does* something about the
territory instead of having something done to him, and it is visible from the town hall in a
minute — you can watch the guards go.

---

## 5. The claim jump — seed 1, the cheap answer

**What the player experiences.** You right-click an enemy chunk that touches ground you already own,
holding the Land Claim Scepter. If you can pay the price, the chunk changes hands there and then,
with a message and a sound. The red border retreats by one chunk.

**What it attaches to.** `ItemScepterClaim` — `mayEditClaim` (`:93`, into
`ChunkDataHelper.mayEditClaim` at `mc/core/util/ChunkDataHelper.java:450`), the refusal at
`isOwnedByAnotherColony` (`:157`, called at `:116`) which currently makes an owned chunk a no-op,
and the claim itself at `:124`. The mechanism already exists and has never been used: `tryClaim`'s
`forceOwnerChange` parameter (`ChunkDataHelper.java:459`), which every scepter passes `false`.
**[VERIFIED]**

**On the seed's data question, which is the one worth answering:** this needs **no format change at
all**, because there is no disputed state to store. The chunk has one owner before the click and one
owner after it. That is the entire reason this entry is a third of the price of entry 9.

**Code size.** ~130 new lines across `ItemScepterClaim` and a small helper, 3 touched files, 1
config key (the price, or the required adjacency), 3 lang keys. No saved data, no packets.

**What could go wrong.**

* **This is the first caller ever to pass `forceOwnerChange = true` from a scepter.** The path exists
  (`ChunkDataHelper.java:459`+) but only `staticClaimInRange` has used it. Expect to find the
  rough edges. **[UNCHECKED]**
* **A stale column mask.** The mask belongs to the chunk's owner (`ChunkClaimData.java:60`), so a
  hand-painted enemy border left behind after the chunk changes hands would silently shape *your*
  claim. `ItemScepterTerritory.release` already does the right thing
  (`mc/core/items/ItemScepterTerritory.java:191`–`:197`); copy it. **[VERIFIED]**
* `ChunkClaimData.removeColony` picks a replacement owner in arbitrary hash order (study §C.3), so
  do not rely on what happens if the territory ends up owning nothing.

**Is it actually good?** It works and it is honest, but **it is shopping, not war** — there is no
moment of tension, only a transaction. It is on the list because it is exactly what "contested
ground" costs if the owner will not pay for a dispute, and because knowing that number makes entry 9
a real choice rather than the only option.

---

## 6. The territory notices you

**What the player experiences.** Walk into enemy ground and after a while: *"You have been seen in
Blackreach."* Stay, and it escalates: *"Blackreach is hunting you."* Leave and it cools off over the
next day. That is the whole of this entry — the consequences of being hunted belong to the mob
spawner and the no-fly rules being written elsewhere, which read the level off a getter.

**What it attaches to.** The rise: `EventHandler.onEnteringChunk` (`:303`), same hook as entry 1,
and optionally a slower sweep over players already standing still. The decay: **nothing**, and that
is the design. A territory does not tick (`Colony.territoryTick`, `mc/core/colony/Colony.java:1683`,
one `updateSubscribers` every 20 ticks and no state machine), so instead of a countdown, store
`alarm` and `lastSeenGameTime` and compute the decay lazily whenever the value is read. Persistence
rides the colony exactly as the `hostile` flag does — read at `Colony.java:1216`, written at `:1385`.
**[VERIFIED]**

**Where the time comes from, stated plainly, since the brief asks:** from the player's own
movement, and from arithmetic on `getGameTime()` at read time. A world with fifty territories that
nobody is standing in does no work whatsoever, because the only thing that ever touches a
territory's alarm is a player standing on that territory's ground.

**Code size.** ~185 new lines across 2 small new files and 3 touched, 2 NBT tags, 3–4 lang keys,
one extra column on the `/mc colony territory` listing
(`mc/core/commands/colonycommands/CommandColonyTerritory.java:114`). No packets.

**What could go wrong.** The 100-tick sampling means a player can cross a corner of the territory
and never be seen — acceptable, arguably correct. The decay curve is invisible, so the player cannot
learn the rules unless you show him; the command line is the cheap answer and a boss-bar the
expensive one. **The one design trap** is exposing the alarm through `HostileTerritoryMap`: that
index is rebuilt whole and republished on every change (`HostileTerritoryIndex.refresh`), and an
alarm that moves every few seconds must not drive a rebuild. Keep it in a separate small
thread-safe map beside the index, or make the getter server-thread-only and say so in the javadoc.
**[VERIFIED]** that `refresh` rebuilds from scratch.

**Is it actually good?** Yes — and it is worth more than its own line count, because it is the thing
that makes the spawner and the no-fly rules read as *one enemy reacting to you* rather than two
unrelated debuffs attached to a rectangle.

---

## 7. The enemy buys — seed 2, the cheap answer

**What the player experiences.** A citizen stops you with a quest: word has come from the territory
next door that they will pay well for iron. Deliver it, take the payment. The offer only exists while
there is a hostile territory near your colony.

**What it attaches to.** The quest datapack, which is almost entirely data. A new
`TerritoryQuestTriggerTemplate` registered in `QuestRegistries`
(`mc/api/quests/registries/QuestRegistries.java:182`–`:187`, six lines of registration) modelled
line for line on `WorldDifficultyTriggerTemplate`
(`mc/core/quests/triggers/WorldDifficultyTriggerTemplate.java`, 43 lines including javadoc — the
whole file), whose `canTriggerQuest(IColony)` returns a `BooleanTriggerReturnData`. Ours asks
`HostileTerritory.in(colony.getDimension())` and measures to `colony.getCenter()`. The quest itself
is a JSON of the shape of `src/main/generated/data/minecolonies/colony/quests/tutorial/military/zombies.json`
using `minecolonies:dialogue`, `minecolonies:delivery` and `minecolonies:item`; text goes in
`QuestTranslationProvider`. **[VERIFIED]**

**On the seed's two questions.** *Where does the trading physically happen?* At one of your own
citizens, in your own colony. *Where does the clock come from?* `QuestManager.onColonyTick`
(`mc/core/quests/QuestManager.java:117`) — the **player's** colony, which ticks normally. The
territory is never asked to do anything.

**One fact worth knowing before designing around visitors:** a visitor **cannot** be the quest
giver. `QuestInstance.getQuestGiver` resolves through `colony.getCitizenManager().getCivilian(...)`
(`mc/core/quests/QuestInstance.java:129`, `:143`), and visitors live in `getVisitorManager()`. So the
"emissary at the tavern hands you a contract" picture needs a custom interaction handler
(~200 lines, the shape of `RecruitmentInteraction`, 218 lines, which already contains the
take-items-from-the-player pattern at `:161`–`:165`), not a quest. That is the middle price between
this entry and entry 10, and it buys a face for the enemy but not a moving rate. **[VERIFIED]**

**Code size.** ~60 new lines of Java (the trigger plus registration), 2 touched files, 1–3 quest
JSONs, ~15 lang keys. No packets, no saved data — quests already persist.

**What could go wrong.** Structurally nothing; this is the safest entry on the list. The honest
weakness is that **the rate is printed in the JSON**, so this is barter at a fixed price. It is not
a market and should not be described as one.

**Is it actually good?** Cheap and pleasant, and it is the only entry that gives the territory a
*voice*. But it is a vending machine wearing a story, and the owner should know that before he picks
it over entry 10 on price.

---

## 8. The raiding party

**What the player experiences.** From the Barracks you order a party out against a named territory.
Four guards walk to the border and are gone — really gone, not standing about invisible. Ten real
minutes later they come back: carrying loot, or wounded, or three of the four.

**What it attaches to.** `ITravellingManager` and its implementation
(`mc/core/colony/managers/TravellingManager.java`), and the working precedent in this tree:
`EntityAIWorkNether.goToVault` (`mc/core/entity/ai/workers/production/EntityAIWorkNether.java:181`–`:190`),
which is **ten lines** — a sound, `startTravellingTo(citizenData, pos, ticks)`, and
`worker.remove(DISCARDED)`. The return is `decide()` at `:192`–`:208`:
`isTravelling` → stay away; otherwise `setNextRespawnPosition(EntityUtils.getSpawnPoint(...))` and
`updateEntityIfNecessary()`. The clock is the player's colony, which already ticks the travelling
manager (`Colony.java:559`–`:561`, transition registered at `:493`). Loot comes from a loot table
exactly as the Nether trip's does. **[VERIFIED]** throughout.

**Code size.** ~360 new lines: a building module or a guard task, the away/return states, the
outcome roll, the messaging. 6 touched files, 1 loot table JSON, ~10 lang keys, 2 NBT tags. No
packets beyond the module view.

**What could go wrong, and this is the entry where it matters most.** `EntityAIWorkNether` is 990
lines and most of that is not the trip — it is the safety around a citizen who is not in the world.
A citizen away when the chunk unloads, when the colony is deleted, when the server stops.
`TravellingManager.recallAllTravellingCitizens` (`:69`–`:96`) exists for precisely that and even
logs *"has returned very confused"* when the entity is missing (`:88`). **Get this wrong and you
lose citizens permanently, which is the worst defect class this mod has.** Honour the recall path,
and test a save/reload with a party out before shipping. Performance: negative — four citizens
removed from the world is four fewer AIs ticking.

**Is it actually good?** **This is the best idea on the list regardless of price.** It is the only
mechanic where the player attacks the territory instead of reacting to it; it needs no territory
tick, no new ownership state, and no rendering; and every hard part of it — a citizen who leaves the
world and comes back with loot — already runs in this repo and has for years. If the owner wants one
*feature* rather than one *day*, it is this.

---

## 9. Contested ground — seed 1, the full answer

**What the player experiences.** You lay claim to a chunk of enemy ground and it becomes contested:
the border there goes from solid red to something unsettled, a countdown runs, and at the end the
ground is yours — unless you stopped paying for it, or unless the enemy pushed back.

**What it attaches to, and the seed's data question, which is the point of this entry.**

The ownership layer has **one** `owningColony` int per chunk and **one** column mask belonging to
that owner (`mc/api/colony/claim/ChunkClaimData.java:47`, `:60`). A second owner on one chunk is
genuinely not expressible, and adding one means touching the save format, the network format and
every reader — the fifty-call-site problem the previous study priced and rejected.

**It does not have to be expressible, because the dispute belongs to the challenger, not to the
chunk.** Hold a `Long2LongMap` of chunk key → deadline on the *player's* `Colony`, saved in colony
NBT beside `TAG_CLAIM_DATA` (`Colony.java:1387`–`:1395`), ticked by that colony's own state machine
(`Colony.java:496`, the `worldTickSlow` ACTIVE transition), and resolved with a single
`tryClaim(..., forceOwnerChange = true)` when it expires. The chunk keeps exactly one owner
throughout: the territory's, until the moment it is not. **No save-format change, no
network-format change, no second owner anywhere.** That is the answer I found, and it is the cheap
one. **[VERIFIED]** that all four pieces exist as cited.

**Code size.** ~430 new lines: the field, its NBT, a state-machine transition, the resolution, the
scepter entry point, whatever makes the territory resist (guard presence in the chunk, a paid cost,
or a fight), and the messaging. 7 touched files, 2 NBT tags, ~8 lang keys.

**What inflates it beyond entry 5 — and it is not the countdown, it is the picture.** The client
cannot draw a disputed chunk unless the disputed set reaches it, which means adding it to
`ColonyView.serializeNetworkData`. That serialiser writes the **entire** claim map into **every**
colony-view packet with no dirty check (`mc/core/colony/ColonyView.java:404` onward — note the
ticketed chunks immediately above it at `:390` *are* gated on
`hasNewSubscribers || isTicketedChunksDirty`). A dispute set that changes on every slow tick,
serialised on that path, is a per-subscriber bandwidth bug. Gate it, or accept that a dispute is
reported in chat and not on screen — which halves the cost of this entry and most of its appeal.
**[VERIFIED]**

**What else could go wrong.** A dispute whose deadline passes while the colony is `INACTIVE`
(`Colony.updateState`, `:507`) never resolves, because `worldTickSlow` is an ACTIVE-only transition
— decide whether "you have to be there" is a feature. Two colonies disputing the same chunk are two
independent countdowns racing; the first to land must cancel the second.

**Is it actually good?** Yes, and it is the only entry that lets the frontier *move* under player
pressure. But be clear-eyed: it is more than three times entry 5, and what the extra money buys is
the countdown and the tension around it. If that tension is the point, pay it. If the goal is
"I want to be able to take that chunk", entry 5 already does that.

---

## 10. The trade post — seed 2, the full answer

**What the player experiences.** A trade post at the border with a real counterparty behind it: a
list of what the territory wants and what it offers, stock that runs down as you buy and refills
over days, and a rate that moves — sell them iron all week and iron gets cheap.

**Where it happens and where the clock comes from**, since those are the two questions the seed
correctly identifies as deciding the price. It happens at a `BlockEntity` you place on **your** side
of the line, and the clock is that block entity's own tick. This is the one design in the study
that is allowed to tick without the territory ticking, and it is self-limiting: a block entity ticks
only while its chunk is loaded, i.e. only while a player is near. Stock and prices live on the
territory `Colony` (so they survive the block being broken and are shared between two posts on the
same enemy), decayed lazily on read the way entry 6 decays its alarm.

**Code size.** ~1,200 new lines: block, block entity, a BlockUI window, at least two packets (open,
transact), the stock/price state and its NBT, a datapack goods table, ~40 lang keys, a recipe, and a
blueprint if it is to be built rather than crafted. 8 touched files.

**What could go wrong.** Everything a GUI holding items can do wrong: desync between the window's
view of stock and the server's, duplication on a transaction that half-fails, the block broken
mid-trade. And one thing specific to this feature — a hostile territory is protected at `NEUTRAL`
rank for every player (`mc/core/colony/permissions/Permissions.java:788`–`:793`), so a post placed
*inside* the territory cannot be interacted with at all by the person it is for. It must sit on the
player's side of the line, which is also better fiction.

**Is it actually good?** It is the only real answer to "a counterparty with a rate that moves", and
it is a month of work rather than a weekend. **I would not start here.** Ship entry 7, find out
whether trading with the enemy at a fixed price is fun, and let that decide whether the moving rate
is worth a block, a window and two packets.

---

## 11. What I considered and did not list

Short, because a rejected idea is worth one line.

* **Fields that stop at the border.** A scarecrow whose radius overlaps hostile ground already
  works — the farmer pays +25 per node and routes around, and protection stops him planting there.
  The only thing missing is a message saying why. That is one lang key inside entry 1.
* **Graves behind enemy lines.** `EntityCitizen.die` already drops the inventory rather than making
  a grave outside the colony (`mc/core/entity/citizen/EntityCitizen.java:1644`–`:1651`). Making that
  *mean* something — the enemy keeps your dead — needs a container in the territory, which is entry
  10's cost for a smaller payoff.
* **Research that reduces the hostile surcharge.** Cheap (the job snapshot is taken on the server
  thread in `AbstractPathJob`'s constructor, so a per-colony multiplier is legal), and completely
  invisible in play. Cheap and boring.
* **Scouting: territories hidden until discovered.** Fights `HostileTerritorySight`
  (`mc/core/colony/territory/HostileTerritorySight.java:65`), which exists precisely to make a
  territory visible from outside, and needs per-player-per-territory state. Expensive, and it
  removes the feature's best property.
* **A colony-map overlay.** `mc/core/client/gui/map/` draws buildings and citizens, not claims
  (study §D.3). JourneyMap already shows a territory named, for free. Nothing to do.

---

## Appendix: what the server run established

A dedicated server on **port 25873** (`dist/minecolonies-26.2-0.0.31.jar`, Fabric API 0.154.2+26.2,
the `colony-1000` test world) was booted, driven from the console, stopped, and booted again. It has
been stopped; nothing in `26.2/` was modified at any point, and the whole server lived in a
scratchpad directory outside the repository.

**[VERIFIED] in play:**

* `/mc colony territory create Blackreach 200 72 0` makes a territory beside a 999-citizen colony
  without complaint: `New hostile territory id 2 named Blackreach at BlockPos{x=200, y=72, z=0}`.
* `/mc colony territory grow 2 4` took 81 chunks and skipped none.
* `/mc colony territory` reports `owns 81 chunks, 81 of them visible to the hostile territory query`
  — the colony's own claim and the published index agree.
* **After a full stop and restart, the same line comes back identical.** Persistence and the
  index rebuild on world load both work. This matters for entries 6, 9 and 10, all of which store
  state on the territory colony and assume it survives a reload.
* A territory next to a 999-citizen colony produced no log noise of its own over several minutes.

**Not established:** anything requiring a client (the red border, the scepters, rectangles,
tooltips, the citizen detour), and raid spawning — `/mc raid 1 now` and every variant I tried were
rejected from the **server console** with `Incorrect argument for command`, caret at end of line. I
did not chase it; it looks like console-versus-player argument resolution in `MultiColonyIdArgument`
rather than anything about raids, and no price in this report depends on it. Whoever builds entry 3
should test the raid command from a player first and, if it is genuinely broken from the console,
that is a separate small bug worth filing.
