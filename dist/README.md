# dist

> [!IMPORTANT]
> **Two jars live here on the `26.3` branch, and only one of them is a release.**
>
> `minecolonies-26.3-0.0.57.jar` is a build of the **26.3-snapshot-9 port** (source in `../26.3/`).
> Minecraft 26.3 is not a release, the port is not finished, and this jar exists so the branch can
> be run rather than only read. Booted before being placed: `Done (0.837s)`, clean shutdown, one
> `/ERROR]` line — vanilla worldgen's `No key layers in MapLike[{}]`, which appears on a stock
> server too. Everything the branch's `README.md` says about unverified client rendering and input
> applies to it.
>
> `minecolonies-26.2-0.0.55.jar` is the last **26.2 release**, inherited from `main`. Note that it
> predates the framed-block fix that this branch's own `../26.2/` source already carries — the
> released 0.0.56 jar with that fix lives on `main`, not here. Do not treat this jar as a build of
> the 26.2 tree next to it.

Compiled build of the Fabric / Minecraft 26.2 port (source in `../26.2/`).

Built 2026-08-20 from `main` after merging PR #6. Booted in a real Fabric dedicated server before
being placed here: `Done (0.250s)`, no `/ERROR]` and no `/FATAL]` line, 18 warnings within the
recorded baseline.

**0.0.55 keeps autopilots out of enemy airspace, and item frames buildable.** With Simple Planes
5.3.10+ installed, an autopilot flight carrying a player routes around every colony where that
player is hostile — but an empty aircraft flies straight, the take-off territory is ignored, and so
is the territory holding the destination, so landing at an airfield behind enemy lines just works.
Either mod loads fine without the other. The bundled Structurize also stops filled item frames in
blueprints requesting their contents twice and the frame never.

**0.0.54 lets framed blocks be built with again, and stops builders freezing silently.** Every
Domum Ornamentum framed block was requested as a bare "Framed" — or looked identical to the request
and was still refused — because the canonical request stack inherited the source shape's
`item_name`/`item_model` components and because six cosmetic components 26.2 adds to every item were
treated as match-relevant. Both fixed; only `texture_data` decides for framed blocks now. A builder
whose work order vanished (hut broken, build cancelled) froze mid-swing forever with nothing in the
log — the AI stopped being ticked while it still thought it was building; it now drops the leftover
state and goes idle, NPE guards cover the vanished-order window, and swallowed AI exceptions are
logged with full stack traces. New `[BuilderDebug]` lines log AI state and build stage changes and
warn when a state holds 600+ ticks — if a builder ever stalls again, grep `latest.log` for
`[BuilderDebug]`.

**0.0.53 gives the download a face and eleven languages.** The install screen got a real progress
bar (bytes while downloading, files while unpacking and verifying); the texts dropped raw URLs and
mixed-language debris ("Скачано: 23,1 из 74,5 МБ", source named as "официальная сборка 1.1.1374,
сервер LDTTeam"); the success screen no longer claims anything beyond what was verified. "Not now"
now means *this session* — the game asks again on the next launch until the assets are installed.
The whole fetch UI (consent included) is translated into Russian, German, French, Spanish, Italian,
Polish, Brazilian Portuguese, Ukrainian, Chinese, Japanese and Korean, with locale-aware number
formatting. Screenshots and a headless-client guide live in `docs/`.

**0.0.52 ships no MineColonies assets and downloads them on first start.** The All-Rights-Reserved
`assets/minecolonies` tree is gone from the jar and the repository; on first client start a consent
screen (English/Russian) offers to download LDTTeam's own build (~74.5 MB) from LDTTeam's own Maven,
verify all 8474 files against a SHA-256 manifest, and inject the result as a required resource pack —
no manual pack install, no restart. Fallbacks: their 1368 release jar, an owner-enabled HTTP slot
(shipped off), or a MineColonies 1.21.1 jar the player supplies (e.g. from CurseForge). Declining is
remembered; `/minecolonies-client fetchassets` re-offers, and every MineColonies window politely
offers the download instead of crashing while assets are absent. The port's own strings (stables,
boats, free mode, the consent UI itself) are now also fully translated into Russian, and the 272
huscarl/marksman voice events missing from upstream's `sounds.json` are shipped and merge in.

**0.0.51 makes the warehouse storage upgrade work, and stops a full warehouse deadlocking couriers.**
Buying the upgrade grew the racks but never recomputed how many slots were free, so a warehouse with
450 genuinely empty slots reported none and refused everything a courier brought — until something was
taken out by hand or the chunk reloaded. And a courier the warehouse refused went back to the
warehouse, over and over, in 80 % of samples; because it never reached its delivery states it never
carried anything *out* either, so a full warehouse could not even be drained by the workers asking for
its contents. Now a refused courier warns you, naming the warehouse, and gets on with the deliveries
it can make. The full-warehouse chat message also carries real numbers — slots taken and how much of
that is fragmentation, side by side.

**0.0.50 gives every hostile territory its own border colour.** They all drew in the same red before,
so a second territory was ground you could see but not tell from the first. `/mc colony territory
create <name> [<pos>] [<colour>]` takes a colour, and without one it picks from twelve, preferring a
colour no other territory in that dimension is wearing. `/mc colony territory colour <colony>
<colour>` recolours one later. White is deliberately not among them — that is the colour your *own*
colony draws in — which is also why territories made before this release keep drawing in the old red.

**0.0.49 keeps hostile pilots' aircraft out of your sky — by routing, not by force.** With Simple
Planes 5.3.9 or newer installed, an aircraft under that mod's autopilot prefers a heading that does
not cross a colony where its pilot holds the hostile rank. It is advice weighed against the terrain,
not a no-fly zone: it is asked only while the autopilot is flying, so hand-flying is untouched, and it
cannot refuse a route, so an aircraft launched from inside a claim flies straight out. Neutral does
not count as hostile — that would detour aircraft around every colony their pilot is not a member of.
Without the aircraft mod, or with an older version, nothing here runs.

**0.0.48 lets a colony hold the sanity cleanup back while it is being rescued.** That cleanup deletes
every building whose chunk is loaded and whose anchor is no longer the right block — correct when a
player mines a hut, destructive when the blocks are missing because the world was opened without the
mod. `/mc colony keepbuildings <colony> on` suspends the removal for that colony, saved with the
colony, off by default, logging what it spares instead of destroying it. The rescue order is
`keepbuildings on` → load the backup → `restorehuts <colony> confirm` → `repairall` →
`keepbuildings off`; restoring a backup without the switch loses buildings before `restorehuts` can
reach them, and a building the cleanup has already deleted cannot be recovered — a hut block placed
where the colony has no building registers a fresh one at level 0.

**0.0.47 puts the hut blocks back after a world has been opened without the mod.** Load a save once
with the jar missing and vanilla drops every one of the mod's block entities — `Skipping block entity
with invalid type` in the log — and the hut blocks go with them. The colony save is a separate file
and survives whole, so the town still exists in the colony's memory while the world holds nothing.
That state destroys itself: the sanity cleanup deletes from the colony every building whose chunk is
loaded and whose anchor is no longer the right block, so each session erases more of the town for
good. `/mc colony restorehuts <colony>` counts what is missing; adding `confirm` places one hut block
per building, at its recorded position and facing, in a single server tick with the chunks pulled in
explicitly — so the cleanup never gets a look in. The blocks bind to the buildings that are already
there, at the levels they already have; nothing is registered fresh at level 0. The structures
themselves are then the builders' job, through `/mc colony repairall`.

**0.0.46 fixes a levitation icon that nothing could remove.** A player denied eleven times in ten
seconds gets ten seconds of levitation — upstream's punishment, and intended. But the permission
handler's bookkeeping was running client-side as well as server-side, because most Fabric callbacks
fire on both, so the effect landed on the client's own copy of the player where the server could never
clear it. It stuck at `00:00`, ignored `/effect clear` and milk, and needed a relog. Now server-side
only.

**0.0.45 makes cavalry faster than the infantry it escorts.** A citizen-ridden horse never reached
vanilla's player-riding path, so it moved by the mob formula, where speed scales as the attribute
*squared* — 1.4–2.8 blocks/s against 3.96 for a guard on foot. A mounted guard now rides at 4.8–6.6,
by the same linear rule vanilla uses for a player's horse, capped at twice a walking guard so it
cannot outrun its own pathfinding. A 70-block leg went from 32–48 s to 12–16 s.

**0.0.44 tells you what has been sitting in the warehouse untouched.** `/mc colony warehousestock
<colony>` reports, per item type, how long since anything was taken and how often it is taken — both
numbers, because idle age alone calls a rarely-but-regularly used consumable junk. The full list goes
to a CSV in the world save; the chat gets a summary. Warehouse fill is reported as slot occupancy and
item capacity side by side, never averaged. Also: **free mode's cook now eats the colony's own food
before conjuring any**, waiting up to two minutes for a courier before falling back; and the
**cavalry rider turns his head to the route** he is riding, which he never did.

**0.0.43 gives a colony back six permissions the port had lost, and adds a switch to turn the whole
lot off.** Placing blocks, placing huts, filling a bucket and drawing a bow are enforced against
strangers again; hostile mobs no longer spawn inside a built building; the damage half of
`turnoffexplosionsincolonies` now spares citizens and livestock from *every* explosion, vanilla ones
included; and `OPEN_CONTAINER` sees modded inventories again. `/mc colony protection <colony> off`
stops that colony refusing you anything while you work in it. Also: **guards no longer fall asleep
standing in water**, where nothing but the timer could wake them. Only `TOSS_ITEM` and `PICKUP_ITEM`
are still unenforced.

**0.0.42 seats fishermen apart on one pond, and fixes a way 0.0.40 could leave them idle.** The
pond-level spread shipped in 0.0.40 could refuse every candidate on a small pond and leave three of
five fishermen never fishing at all. Spreading now moves the *seat* rather than the pond: 4–10 blocks
apart in boats, 2–3 on a bank, and sharing a block is the floor rather than a failure. Worth taking if
you run more than one fisherman.

**0.0.41 stops the fishing line twitching.** The bobber had lost client interpolation in the port —
1.21.1 turned it off deliberately with an empty `lerpTo`, 26.2 replaced that method with
`InterpolationHandler`, and a plain entity defaults to none, so the hook stepped to each server update
instead of gliding between them. Vanilla's own bobber carries a handler; ours does now too. Needs eyes
in a world to confirm — there is no client in the build container.

**0.0.40 makes racks breakable, moves the Stable to Military, and can grow up a colony of stuck
children.**

- **Racks are no longer blast proof.** Upstream gives them infinite blast resistance — bedrock's class
  — so a creeper craters the warehouse floor and leaves every rack hanging in the air. Now 6, which is
  stone. Hand mining unchanged; the contents still spill when the block goes.
- **The Stable is under Military**, beside the barracks, instead of under agriculture/husbandry where
  its cow-barn donor lived. A Stable placed on 0.0.39 points at the old path and wants re-placing.
- **`/mc colony growChildren <colony>`** grows up every child at once. The child timer only runs while
  a child's AI is actually ticking, so children in a colony nobody visits stay children for the life of
  the save — old test worlds are full of them.
- **Fishermen out of one hut try to spread out**, after five of them were measured piling onto one
  water block. Shipping unproven: the stand that measured it is gone and verification is still running.

**0.0.39 unlocks the Stable, sends the fisherman out in a boat, and stops the mine dying in silence.**

- **The Stable has a recipe and a blueprint in all 23 styles**, each derived from that style's own cow
  barn. Nothing that depended on the Stable was reachable before, because the hut block could not be
  crafted at all. Mounted guards came with it: three defects in the existing cavalry code are fixed, and
  free mode hands out mounts already trained and armoured.
- **The fisherman fishes from a boat** where his pond has no reachable bank — no boat item, no request,
  gated behind the existing Boats research, and a colony without that research is unchanged, which was
  checked rather than assumed. Shore fishing is untouched. The **hut now holds one fisherman per
  building level**, five at level 5, each with his own ponds and his own rod.
- **A stalled mine now says so.** A shaft that hits water or lava used to stop dead while the miner
  ticked normally and `/mc colony diagnose` reported no problems — 100 000 ticks with not one block
  changed, measured. The stall is now named in chat and in `diagnose`, where it survives the miner
  dying and being replaced. The miner also leaves the fire instead of standing in it until he dies; he
  can still die, but no longer silently and no longer forever.
- **Empty bottles come back from recipes again**, from upstream: the bottle sat at weight 0 against an
  empty entry at 100, so it was only ever returned on a luck roll.

**0.0.38 turns cinnabar into red dye and lets the stonemason cut the 26.2 stones.**

- **One cinnabar block to four red dye**, at the dyer, building level 2. Cinnabar is a decorative stone
  family, not an ore, so the recipe takes the plain block — the only member actually mined. Four,
  because red dye is already free from poppies and beetroot, so a 1:1 trade of a finite cave block is a
  recipe nobody would use.
- **Six stonemason recipes unlocked** — and the earlier diagnosis of "nine blocks from two tag lines"
  was wrong, which is why it was checked. The stairs, slabs and walls were already craftable through
  vanilla's shape tags. The **bricks**, the symptom that started this, needed a second fix: their output
  is in no product tag, since vanilla's stone-brick tag still lists only its own four.
- **Measured, not assumed:** stonemason 250 → **256** compatible recipes on a real server, dyer 234 →
  235, with tag membership checked block by block including the negative case — cinnabar is deliberately
  kept *out* of the dyer's ingredient tag, which is subtracted wholesale from the stonemason's.

**0.0.37 made couriers three times as productive, on the same number of path searches.**

Measured on a server, one courier, permanent backlog, four minutes each way: deliveries **14 → 42**,
blocks walked per delivery **52.6 → 25.3**, share of life standing at the decision delay **27.3 % → 3.0
%**, path searches 267 → 270, pathfinding queue wait 271 ms → 159 ms.

- **A courier no warehouse has taken on does nothing at all** — hiring at the hut is only half of it.
  Before the fix: five hired at one hut, **four spent all 1840 ticks standing still**, while
  `/mc colony diagnose` said `No problems found`. Adoption no longer waits on the colony-wide auto-hire
  switch (adoption is not hiring), `MANUAL`/`LOCKED` still honoured, and `diagnose` now names every
  stranded courier and every warehouse's occupancy. This barely mattered when a hut held one courier;
  since 0.0.33 it holds five.
- **The five-second stand** cost 27 % of a courier's life with work waiting the whole time. Three lines.
- **Claiming prefers work near the courier** — but not literally, since a delivery must be fetched
  first; the term that matters is courier→rack→target, and the courier's own position earns its keep on
  pickups, which chain building to building. A quadratic scan in queue depth went with it.
- **Multi-drop rounds**: up to three destinations per trip, ordered from the rack. The danger was
  cross-contamination — unloading took whole slots by item identity, so two buildings ordering
  cobblestone would have had the first empty the third's. From the player's side that is theft, not
  optimisation; it is prevented twice over.
- **Multi-drop pickups: deliberately not built**, with an argument. A claim is never released, so
  pre-claiming three pickups hides them from nineteen other couriers for a round a full pack can cut
  short — the "one courier hoards, the rest idle" shape.
- **Three inherited defects** fixed on the way: a guard that had never once been live, an unreachable
  merge branch, and a destroyed delivery target reporting success.

**0.0.36 stopped a broken recipe token costing the colony its slow tick.**

- **What happened.** A crafting module could hold a recipe token nothing can resolve; dereferencing it
  threw, and the colony's state machine answered by delaying that transition **five minutes** — three
  slow ticks in ten minutes instead of twenty-four. Reproduced on a server before anything was touched.
- **Two corrections to the first description of it.** The null guard people expected to be missing is
  already there — a second dereference below it was not. And only the *transition* was delayed, not the
  whole colony: requests, raids, subscribers and citizen data kept running. What stalled was the slow
  tick — buildings, visitors, animals, events, graves, reproduction, quests — with a nastier edge: the
  aborted loop never reached the buildings *after* the thrower, and the same one throws first every
  retry, so the tail of that map effectively stopped existing.
- **Why a token goes stale on its own.** Tokens are saved with the colony, recipe definitions in a
  separate file, and the recipe manager silently drops any that fails to validate — the `catch` doing it
  has a comment saying the exception is eaten. The test world had **14 such tokens already**, from an
  ordinary save and load.
- **They are removed now, not skipped.** The token can never be re-minted, so a skip would leave it to
  be skipped again every 25 seconds forever. Two more unguarded lookups of the same shape were fixed,
  and a loop that removed from the list it was walking.
- **One building's failure no longer costs the colony its tick** — the loop contains a thrower, names it
  once in the log, and carries on.
- **All inherited**: upstream and 1.21.1 have the same dereference and the same five-minute delay.

**0.0.35 gave children a family, their proper size, and names old age as a cause of death.**

- **A child is born to two citizens and lives with them.** The game used to pick a *house with a free
  bed* first — anchored on the colony centre, which the tavern usually won — and take the parents from
  whoever lived there. Hence every child in the tavern, and a child born into an empty house having no
  parents at all. Now a couple is found first and the child moves in with them.
- **A child takes no bed.** It shares its parents' home, so a full house still takes its own children;
  on growing up it needs a bed like anyone else and moves out only if there is no room.
- **Children were adult-sized, and that was ours.** 1.21.1 had one line shrinking the model; 26.2 vanilla
  removed the field it used and the port dropped the line with nothing in its place. The hitbox was
  right the whole time, so model and hitbox disagreed. The second half — the one that made a child look
  like the builder standing idle — was the model never being re-resolved when an *adult* was flagged as
  a child, which is why it was only *some* of them.
- **Old age says so.** Vanilla's combat tracker falls back to a generic message when it holds no damage
  entries, and ageing kills without dealing damage — so the mod's own correct string was never reached.
  Deaths at dawn arriving together are working as designed: the ages were measured and genuinely spread;
  the ageing pass simply runs once a day, at wake-up.
- **School is working as designed.** Auto-hire is on by default; **carpets** are the usual culprit, and
  the game says so only on the child — never in chat or `diagnose`.
- **Not verified in play:** everything client-side — the child's size and skin, the residence and family
  windows, and the death line as it appears in chat.
- **Found, not fixed:** a stale crafting recipe token throws, and the colony's state machine answers by
  sleeping five minutes — **the whole colony stops ticking** and recovers only to throw again. It is a
  one-line guard but belongs to a different feature, so it is written down rather than slipped in here.

**0.0.34 made the frontier something you notice, walk, and can attack out of.**

- **Crossing says so.** One line when you step onto enemy ground, naming it; one when you leave, in gold
  rather than red so the all-clear does not read as a second alarm. Ten-minute suppression per line per
  player. A world with no territory pays one map lookup.
- **Border patrol, set on the barracks** — Off / Enemy Border / Colony Border, off by default, because
  not every guard should walk a line. The stretch is ~500 blocks of border **centred** on the point
  nearest the barracks, sliced one piece per tower. No guard AI was changed.
- **A patrolling guard cannot wander off**, and not because of a distance check: every waypoint comes
  from a search bounded to 16 chunks of the barracks, so a point further than 264 blocks from home is
  unrepresentable. A guard pulled away by a fight rejoins at the nearest point of his line. Water needs
  no new code — the navigator already launches a boat when the colony has the Boats research.
- **`/mc colony diagnose` grew a *Border patrols* section**: each tower's slice, where it is heading, and
  every guard by name with how far off his line he is. `diagnose` alone was not enough — it reported AI
  state but never where a guard was or what he was meant to be walking.
- **`/mc colony raid <colony> now|tonight territory`** sends a raid out of nearby hostile ground.
  Command only; scheduled raids are untouched. Four named refusals that each do nothing.
- **Three defects found by running it:** a water-spawn lottery that failed one attempt in two on a
  coastal territory; a deleted territory that went on answering queries for a minute (**pre-existing
  since 0.0.31**); and a `diagnose` that could throw on a tower with no guard.
- **Zero per-tick cost**, and with the setting off it is one virtual call returning null on the patrol
  timer.
- **Not verified in play:** guards walking a line, taking a boat, the barracks settings tab, and slicing
  across several towers — a barracks tower cannot be created by command, so that path needed a
  temporary instrumented jar, since reverted.

**0.0.33 gave the Courier's Hut a courier per level, and the warehouse room for twenty.**

- **The hut was the constraint, not the warehouse.** The hut held a bare constant **1**, ignoring its
  level; the warehouse already accepted `level * 2`. Measured on a real colony: `warehouse5 deliveryman
  1/10` — nine slots the game was willing to fill with no way to fill them. The hut is now one courier
  per level (**5** at level 5) and the warehouse `level * 4` (**20**), which keeps the warehouse exactly
  four huts ahead at every tier.
- **The `1` was inherited from upstream**, and deliberate there: the *Minimum Order Quantity* research
  wants a summed hut level of 9, which one level-5 hut cannot reach. That research is **left alone** —
  so a player who now builds a single big hut has no reason to build a second, and may never unlock it.
- **Four bugs fell out of code that assumed one courier per hut.** Two were real: every courier would
  have inherited the *first* courier's delivery throughput, and the "can this courier eat?" check
  routed through a method that **claims a request off the shared queue** — a food check was handing out
  delivery work. Two more were waste on the request-resolution path, exposed only because ten couriers
  was never actually reachable.
- **The request system itself needed nothing.** Couriers claim by removing a token from a shared
  per-warehouse queue on the server thread, so two can never take the same request; nothing in the
  system knows which hut a courier sleeps in.
- **Not verified in play:** that the hut window lists five workers, and that the warehouse's courier
  pane scrolls at twenty — it is sized to exactly ten rows, and it is a scrolling list, but that wants
  an eyeball. Also worth knowing: `/mc colony diagnose` says `No problems found` even with couriers
  stranded for want of a warehouse.

**0.0.32 let you find an aircraft, and traced "I never saw the plane" to the other mod.**

- **Why no aircraft was ever visible.** Simple Planes tracked every airframe at 80 blocks — the range at
  which a server tells a client an entity exists. The raid transport flies in from 300 blocks and opens
  its bay 36 blocks from a drop point 70 blocks up, so it existed, to you, for under two seconds
  overhead. Guard towers meanwhile engage at 160 blocks, i.e. at things you could not see. Only
  **unmanned** aircraft were affected: tracking range maximises over passengers, so anything with a
  player aboard was always fine — which is exactly why nobody hit this before autopilots existed.
- **The fix is in the Simple Planes repository, not in this jar.** Aircraft to 10 chunks (vanilla's boat
  range), parachutes to 8. **You need a rebuilt Simple Planes jar for it to take effect.**
- **`/mc aircraft [where|tp]`** lists aircraft nearest first with direction, distance, coordinates,
  altitude and what each is doing — finding a scripted flight **inbound at 350 blocks**, long before it
  is overhead, because the autopilot registry knows about it from the moment it is ordered. `tp` puts
  you on the ground beneath it, facing it. Without Simple Planes it says so plainly.
- **The blast-protection patch is gone.** Since 0.0.27 this repo carried a patch file and told you to
  apply it by hand. It now lives in Simple Planes as real code, with its own `/blastguard
  [status|on|off]` switch in that mod's own words. Every instruction to apply a patch has been swept
  out. The reflective handshake between the two mods was executed live for the first time and works.

**0.0.31 added enemy ground: mark it, see it in red, and your citizens walk around it.**

- **A hostile territory** is an ordinary colony with a flag, no town hall, no citizens, nobody as its
  owner — the cheapest thing it can be, since every "who owns this position" question in the mod is
  already written against a colony id. It may sit **flush against your own border**: the minimum
  distance between colonies is a *founding* rule and the claiming path never consults it. One chunk
  still has one owner, so bordering is fine and a chunk half yours and half theirs is not.
- **Making one:** `/mc colony territory create <name> [<pos>]`, then `grow` for a quick square, or the
  new **Territory Scepter** to take chunks one at a time for whichever territory it is bound to, and
  the **Border Scepter** to shape them column by column.
- **Rectangles for the border painter.** Left-click marks a corner, the next right-click fills between
  them, sneak erases. Bound 4096 columns (64×64) — a mis-click bound, not a feature: this is the
  hand-drawn in-chunk painter, not a bulk chunk grab. The claim/unclaim scepters deliberately did not
  get it.
- **Citizens go around.** +25 per node inside enemy ground, walking or swimming — a surcharge, not a
  ban, so a citizen already inside can walk out and a strip cutting the colony in two does not strand
  the far half. The boat and rail shortcuts, which skip up to 64 blocks unpriced, now stop at the
  border instead of sailing straight through.
- **An API to build the rest on:** `com.minecolonies.api.colony.territory` answers "is this ground
  hostile, and whose" from **any thread** — which the rest of the colony API cannot, since its claim
  lookup is a read that writes. With no territory in the world the call is one map lookup returning
  null. Aircraft no-fly rules and hostile spawns are deliberately **not** here; they are being written
  in a separate mod against this.
- **A territory does not tick.** One subscriber update every 20 ticks and nothing else — no requests,
  work manager, waypoints, day-time, travellers or raid checks for a thing with no citizens.
- **Red, and visible from your own land.** Two real gaps: a new colony's team colour is white, so a
  territory would have drawn as if it were yours; and a client only learns of a colony by standing on
  its ground, so the line would have appeared only after walking through the enemy's land once and
  vanished on relog. Both fixed.
- **Not verified in play:** every scepter interaction, the rectangle fill, the tooltips, the red border
  on screen and the citizen detour. There is no game client here. What *was* checked on a real server:
  creating, growing, listing and deleting a territory, and its surviving a restart without being handed
  the usual starting square.

**0.0.30 stopped racks crashing Sodium's chunk builder.**

- **The crash.** With Sodium installed, coming near a colony killed the client with *"Tried to access
  render state from outside the main render thread"* on `minecolonies:blockminecoloniesrack`
  ([#24](https://github.com/unknown-wq/minecolonies/issues/24)). Domum Ornamentum's model asks the rack
  for its texture data while baking, the port refreshed that cache lazily inside the getter, and the
  refresh ends in a block update — so a rack requested a chunk rebuild from inside a chunk rebuild, on
  a chunk worker thread, and Sodium asserts the main render thread there. Vanilla's renderer never
  checks, which is why this shipped through nine releases unseen.
- **The fix.** That refresh now happens on the client main thread. Off-thread callers get the cached
  map and the refresh is queued; when it changes something it issues its own block update and the next
  bake reads the new map. Worst case is one bake of a rack with bare shelves. The neighbouring-rack
  read in the same refresh was the same violation without an assert watching it, and moved with it.
- **Not verified in play.** No client, no GPU and no Sodium in the build environment — the code path
  and a clean server boot are what was checked. Whether racks fill in a frame or two after coming into
  view, and whether blueprint previews still show their contents, wants a look in game.

**0.0.29 fixed the anti-air battery's "no arrows" spam, told you which tower is dry and where it
stands, and let the tower order its own arrows.**

- **The line came six and seven at a time.** The throttle tested the game tick rather than the colony,
  so on the one tick in six hundred that matched, *every* dry tower reported in the same frame. Dry
  towers are now gathered during the colony's pass and reported **once**, on a per-colony timer of two
  minutes. The line only fires while a tower is actually tracking an aircraft it cannot shoot at, so it
  never nags in peacetime.
- **It names a tower now.** The message gives the tower's name, the side of the colony it stands on,
  and a hover with its coordinates and distance from the centre — the same convention a citizen's death
  message uses. With several dry, it names the one nearest the centre and counts the rest, instead of
  one line each.
- **A dry tower asks for arrows itself.** It files an ordinary request as a building, so the warehouse
  resolves it and a courier delivers into the tower's racks, which is where the battery looks. The chat
  line stops being the only remedy. In free mode the tower is handed a stack outright and never asks.
- **`/mc colony antiair <colony> [where|tp]`** lists every emplacement with direction, distance,
  coordinates and arrow count, or teleports you to the nearest dry one — the same tower the chat line
  names. Without Simple Planes it reports no positions and changes nothing.

The aircraft **sighting** warning was checked and left alone: its cooldown was already correct, and it
deliberately carries no coordinates, because a plane crossing at nearly three blocks a tick is gone
before you read them.

**0.0.28 fixed three things spotted in play: a citizen that believed it was full, a free-mode meal
the tooltip called inedible, and a missing line of text.**

- **A citizen could think its inventory was full when it visibly was not.** `InventoryCitizen` caches
  the number of free slots in a counter that only the methods owning a slot update — so a slot
  emptied *through the ItemStack itself*, which is what a citizen eating its last piece of food does,
  left the counter believing that slot was still taken. The drift only ever under-reports free space,
  and it heals on the next save or client sync, which is exactly why it came and went. The counter is
  still trusted when it says there **is** room; only a "no room" answer is now checked by recounting.
  Upstream has the same bare read, so this is a deliberate divergence rather than a port repair.
- **Free-mode food read as inedible.** In free mode the cook's menu gains a free meal, but the menu
  sent to the client was the stored one, without it — so the tooltip told you your steak was "not on
  the dining hall's menu" while the citizen was happily eating it. The lie was in the tooltip, not the
  food.
- **`com.minecolonies.core.item.food.tooltip.tier.0`** did not exist, so the raw key was shown for any
  ordinary food — tier 0 is the *default* for anything below 12 nutrition, not an edge case. Missing
  upstream too.

**0.0.27 added `/mc colony workoverride` and gave a colony its blast protection back.**

`/mc colony workoverride <colony> [mourning|night [on|off]]` is a family of "keep working anyway"
switches, per colony, saved in the colony, **all off by default**. `mourning` lifts only the work
stoppage — citizens still grieve, still carry their dead, still pay the happiness cost, they just do
not down tools; with a short generational lifespan a town otherwise mourns without pause. `night`
puts builders on a night shift, and resets their slept-tonight modifier so the shift costs no
happiness. Run with no switch named, it lists them all.

**Blast protection** returns, for aircraft. Every explosion Simple Planes can produce — the craftable
strike tool, the autopilot command, a bomber flown in by hand, a downed aircraft, a crash — converges
on one method, so the guard sits there. **Vanilla explosions are not covered**: creepers, TNT, and a
bomb dropped as a payload all go through Minecraft's own path, which would need the vanilla hook this
port deliberately has no mixin for. The cost of that mixin is written down in
`../26.2/BLAST-PROTECTION.md` so the decision can be made on numbers later. The Simple Planes half
lives in that mod and needs nothing applied here — just a Simple Planes build recent enough to carry
its blast-guard API. That mod also has its own `/blastguard off`, which stops it consulting any
guard at all.

**0.0.26 was the Simple Planes integration: pirates that arrive by air, guard towers that shoot
aircraft down, and a warning that reaches you before the bomb does.** All three are **optional** —
they need the Simple Planes mod installed and do nothing without it, and this jar was booted on a
server with no Simple Planes present to confirm it behaves exactly as before. Booted here:
`Done (0.407s)`, no `/ERROR]` and no `/FATAL]` line.

- **Paradrop raid.** A transport aircraft comes in from 300 blocks out at 70 blocks up, deliberately
  slow so you can see it and shoot at it, and drops pirates one at a time under parachutes. All three
  places the raid bookkeeping assumed a raider walking in from the border were fixed, including the
  one that would have left a raid running forever if the transport was destroyed before it dropped
  anybody — the horde is now rewritten to whatever actually landed, and a wave stopped in the air
  ends the raid as a win.
- **Anti-air battery.** A guard tower of level 3 or better, with an archer and arrows in its chest,
  engages aircraft at 200 blocks, one arrow a second, leading the target on three axes. Four hits
  bring an aircraft down. That is deliberately just short of what one tower can manage against a
  strike run — **one tower cannot stop it, two can**.
- **How an aircraft goes down.** A plane at zero health does not fall — the mod's own death check
  needs it on the ground — so the kill is done by taking its autopilot away, which also **disarms the
  warhead**: the blast is read from the flight plan, and with no plan it falls back to an ordinary
  one. Outside your claim the wreck is left alone to fall, hit the ground and explode where it lands.
  Inside the claim the last five blocks of the fall become smoke and noise instead of a crater in
  your roof.
- **Air-raid warning** from the autopilot registry, the moment a strike is ordered rather than when it
  crosses the border: **14 seconds of warning instead of 3**.

**What is verified and what is not.** Measured on a server: an arrow does damage an aircraft and four
of them destroy it; a parachute carries a mob 39.6 blocks over 20 seconds and lands it at full health
with no fall damage; the autopilot really does fly an unmanned route. **Not verified: whether a
raider stays seated on the parachute** — a colony with no player in the world never becomes active,
so no raid can be started headless at all. Rather than guess, the raiders are given slow falling
*and* the parachute, so they survive the drop either way.

**0.0.25 added two more fixes from the removal round, both crashes the player never saw.** A hut with
an empty blueprint path threw on a negative substring inside the work order code, which took out
*every* click on that hut — build, repair and removal alike — leaving a stack trace in the log and
nothing at all in chat. It is now refused with a message naming the hut and its coordinates.
`repairall`'s preview reports the same case instead of counting it as repairable.

**0.0.24 added `/mc colony repairall`, fixed the removal-request feedback, and made old age reach the
whole colony rather than only the part you are standing in.** `repairall` files one repair order for
every building that can take one — the hut GUI's repair button, pressed on all of them at once — and
reports what it queued and what it passed over. The work order feedback now says what was actually
ordered instead of always claiming a build was created, and a hut that was never built is handed back
rather than ordered torn down. On the generations side, a citizen whose entity is not loaded can now
die of old age: previously the kill needed a body, so in a large colony most of the population never
aged out and deaths clustered wherever the player happened to be. Such a death leaves no grave —
there is no loaded chunk to place one in — and says so in chat.

**0.0.23 was one fix on top of 0.0.22: a colony running generations that had filled every bed stopped
having children, and quietly stopped running part of its colony tick as well.** The newborn still
needed a house to inherit parents and a family name from, and the search for one only offered
buildings in loaded chunks — in a town big enough to run out of beds, usually none of them. It fell
back to the town hall, which has no living quarters, and the module lookup there threw instead of
returning nothing; the colony state machine caught the exception and abandoned the rest of the tick.
Diagnosed from a player's `latest.log`, where the stack trace repeated every 25 seconds. Only affects
saves with `generations = true`; no save repair is needed.

**0.0.22 was three pieces of work that each wanted a whole agent: guards, generations, and mixed
seeds.** The guard round is the first time anybody in this port has watched one fight; the other two
are new gameplay and both ship **off or opt-in**, so a save that ignores them is unchanged.

**Guards, measured on a combat stand** — six towers, three knights and three archers, an AI-state
sampler and a read-only observer of the threat table. Full record in `../26.2/GUARD-AUDIT.md`.

- **Three plausible theories were disproved before anything was changed**, and that is recorded first
  so the next round does not spend time on them: patrolling guards do **not** load the pathfinder
  (four guards produce 0.29 jobs per second, pool 0.1 % busy); a knight does **not** re-path on every
  swing; an archer shuffling on its post runs the cheapest job in the table, 10–15 nodes.
- **A knight is blind upwards.** The target search box is ±3 blocks vertically and nothing raises it
  for knights, while an archer on post looks 28 up. Same experiment both sides of the colony — zombie
  on a pillar four blocks away, six blocks up, plain line of sight, 250 s: **knights zero target
  acquisitions, archers three.** That is the "my knights ignore the mob on the wall" complaint. New
  `guardverticalvision`, **default 3, which is today's behaviour**; 8–12 covers a storey or two.
  Raising it makes guards fight more, which is a balance decision, hence opt-in.
- **A guard twitched in and out of combat at an enemy it could not reach**: the target search kept
  reporting a target the guard had already given up on and the threat table refuses to hand over, so
  the AI entered its attacking state and dropped straight back out, every sweep. **86 pointless round
  trips in 250 s became 11.** This one is on by default and does not change *which* enemies a guard
  fights; regression-checked with ordinary combat, 114 searches, all arrived, none hit the node limit.
- **Guards sleep on duty in daylight** — 2.6 % to 16.8 % of the working day, plus 1–5 % more spent
  walking over to hit a colleague awake. All windows sampled at `time set day`. New `guardsfallasleep`,
  **default true, which is today's behaviour**.
- **The wounded-guard cycle measured end to end**: run home 5 s, self-heal **75 s**, return to post.
  **The healer plays no part in it at all**, and without the *Regeneration* research fleeing never
  triggers — in the first run six guards out of six died.
- **The axe damage question is settled with numbers rather than a flip.** Upstream adds the axe's
  *spent durability* to the damage: a huscarl hits for 3 with a fresh axe and up to **2034** with a worn
  netherite one, climbing by one per swing (confirmed by watching a sword's damage value go 0 → 5 in a
  single fight). This port adds real attack damage, a flat 9–12 by tier against 10 for a knight with a
  netherite sword. **The port's version stays** — it is a correction, not a rebalance — and the
  divergence is now written into `MeleeCombatAI` as a table instead of being silent.

**Generations — citizens age and die, and children are born whether or not there is a bed.** Ships
**off**: `generations`, plus `generationslifespandays` (100) and `generationsbirthmodifier` (1.0).
With it off nothing changes, not even the NBT.

- Ageing is spent life, not a birthday: a citizen burns `1 / (0.5 + 0.075 × happiness)` per colony day,
  so someone at happiness 0 lives half as long and at 10 a quarter longer. Happiness already contains
  housing, food, safety and health, so **a homeless citizen in a starving town ages fastest** — that is
  the housing-to-death link, with no new subsystem.
- Death runs through the ordinary death path, so graves, the undertaker, the vacancy and the re-hire
  all come free. One exception: the colony-wide three-day mourning modifier is not applied to natural
  death, because it outlives the interval between funerals in a large town and feeds both birth rate
  and ageing speed — a town would literally grieve itself to death.
- **The housing gate on births is removed**, deliberately: a shortage is now pressure to answer rather
  than a silent contraceptive. The population cap still applies. Measured on a 1000-citizen fixture:
  population grew 999 → 1000 with 276 beds and none free, and stopped at the cap rather than at the bed
  count.
- Birth rate is derived from mortality rather than tuned: in steady state `population / lifespan` die
  per day and births are that same figure times fertility, so **the two halves meet by construction**.
  Fertility is food × colony happiness × crowding × the `GROWTH` research — a model town scores 2.6, a
  solid one 1.2, a cramped one 0.53, a starving one 0.10.
- Also new: **`/mc citizens fill <colony> children`**, which fills with children instead of adults.
- Skills do not carry over. A maxed worker dies with all its levels; the module puts a novice in the
  vacancy. Upstream's own inheritance softens it — a child takes its parents' skills ±2 when they share
  a house.

**Mixed seeds — a field may hold up to five crops.** The scarecrow window gains a *Mix in / take out*
button beside the existing seed picker, and `/mc colony fieldseeds <colony>` lists and edits them from
the console.

- The design is forced by a detail: a cell is tilled on one pass and sown on the next, a colony day
  apart, and the sowing refuses a cell whose farmland is the wrong kind. So whoever tills and whoever
  sows must pick the *same* crop for the same cell — which rules out random choice, round-robin on the
  building's cell counter, and anything keyed to the scarecrow offset. The answer is a pure function of
  world coordinates, `(x + z) mod n`: diagonal one-cell stripes, crop shares within one cell of each
  other on every field size checked.
- **Melons and pumpkins do not fruit in a mix** — a stem needs a free orthogonal neighbour and in
  stripes all four belong to other crops. That is vanilla stem behaviour, not a defect of the mix, and
  nothing warns about it. Give them their own field.
- Old saves load: a field written by an earlier build reads back as one seed, verified across a full
  server restart.

**0.0.21 is what a live measurement of the worker AI turned up, plus three limits that were hardcoded
and are now settings.** The measurement is the new part: an agent attached to a running dedicated
server sampled every citizen's `(profession, state, did-it-move)` every 50 ms and built a histogram of
where a worker's day actually goes. **The headline result is that none of it is this port's doing** —
all 75 worker AI files were compared against the 1.21.1 oracle, 21 are byte-identical and the rest
differ only by mechanical API translation and this branch's own additions. Everything below reproduces
on NeoForge too. Full record in `../26.2/WORKER-AUDIT.md`.

- **A fisherman with no pond stopped burning the most expensive search in the game.** Measured: 44
  decision cycles in 168 seconds at **zero movement**, one `PathJobFindWater` each. Run synchronously
  from the fisherman's own position, that job costs **8009 search nodes and exhausts its budget without
  arriving** when there is no valid pond, against 205 nodes when there is one — on the single thread the
  whole server shares. Now it backs off: 5 → 10 → 20 → 60 seconds, reset the moment a pond is found. He
  was already complaining in chat the entire time (the "I can't find any suitable water" interaction
  fires every cycle and names the 7×7×2 requirement) — the complaint was never the missing half, the
  cost was. The pond criteria are untouched.
- **Twenty seconds of dead time after every inventory dump.** Two transitions out of `IDLE` share a tick
  rate but not a start offset; when the idle one matures first it sets a 400-tick delay that suppresses
  *everything*, including "there is work → work", which for a lumberjack is true by definition.
  Measured: **20.2 consecutive seconds in `IDLE` at zero movement, 11.8 % of that worker's time in the
  window.** Now `idle()` checks for work first. Checked against all 15 subclasses, not just the one that
  showed the symptom.
- **Emptying an inventory took one slot per second.** A lumberjack with four occupied slots stood at the
  chest 4.1 seconds; a miner with twenty stacks stood for twenty. Now up to five slots per visit. The
  20-tick pacing is kept deliberately — it is what spreads container access across ticks.
- **`/mc citizens info` no longer crashes** on a citizen that has never eaten. Upstream, one line, and
  irritating because it is exactly the command used to find out why a worker is idle.
- **`maxbuilderdistance`** (new, **default 2000**) — how far from a builder's hut a building may stand.
  It replaces a hardcoded 100 and is the number behind *"There is no Builder close enough to work on this
  building!"*. Note the distance is three-dimensional, so depth counts as much as ground distance.
- **`maxpathfindingdistance` default raised 900 → 2000**, to match. The two are a pair: one decides which
  work orders a builder may accept, the other whether the walk is allowed at all. **The distance check
  runs before the search and does not know how the route would be travelled** — water and rails are
  refused at the same distance as broken ground, so a colony that ferries citizens across a strait needs
  this raised regardless of the boat.
- **`decorationsoutsidecolony`** (new, **default on**) — a decoration's footprint may cross the colony
  border. Placement demanded that *every chunk the blueprint touches* belong to the colony, which put a
  wall or a road a chunk short of the border it wants to sit on. A decoration owns no ground and files
  no claim, so this costs the colony nothing; huts and plantation fields are unchanged. The anchor must
  still be inside, because the work order is filed with the colony the anchor is in.

One item was dropped after inspection: the audit's account of a lumberjack failing to see a nearby
forest does not hold — the colony's loaded-chunk set is filled at colony creation for the whole initial
claim, not only by chunk-load events, and both proposed fixes were either dead code or unsafe from the
pathfinding thread. The measured symptom is real and remains unexplained; a second candidate the audit
itself measured — leaves with `persistent=true` are not counted as a tree at all — produces the same
symptom.

**0.0.20 is ten small changes, picked for value against size rather than as one theme.** Two of them
restore behaviour the port lost, two are new vanilla-26.2 content the mod could not see, one flips a
default, and the rest are cheap performance and correctness work on the pathfinding pool.

- **The florist works again.** In 26.2 `VegetationBlock.canSurvive` asks whether the block below is in
  `#minecraft:supports_vegetation`, and composted dirt was in no soil tag at all — so a flower the
  florist placed was destroyed by the very next neighbour update, and on adjacent cells each new
  planting killed the previous one in a cascade. Measured on a live server before the fix:
  `F1-COMPOST-POPPY-GONE` against `F2-DIRT-POPPY-SURVIVED`. On 1.21.1 this was NeoForge's
  `canSustainPlant` hook, which Fabric has no counterpart for; the tag is 26.2's own answer to the same
  question and is now set by datagen. One consequence to expect: a player can now also plant saplings
  and grass on composted dirt by hand, which is exactly what 1.21.1 allowed. Crops still cannot —
  `#supports_crops` in 26.2 is literally farmland alone.
- **`stopsearchonarrival` now defaults to on.** It shipped off in 0.0.19 only because nothing had been
  played with it. The measured figures are unchanged: 3.7–5.6 % of the pathfinding thread on a general
  colony, and the large numbers — 800 blocks of rail 6223 expanded nodes → 16, 796 of open water
  8301 → 30 — are corridor cases, not the average. Every measured route came back identical. The off
  switch is one line of config and takes effect without a restart.
- **Chickens in warm and cold biomes are no longer invisible to the chicken farmer.** 26.2 lays by
  variant — warm gives `brown_egg`, cold `blue_egg` — and the herder's pickup check and the hut's JEI
  output both knew only white. Both now read `#minecraft:eggs`. Baker and chef recipes are deliberately
  **not** converted: a tag ingredient behaves differently in the mod's request system and that has not
  been verified.
- **A cavalryman accepts a vanilla spear.** 26.2 added seven spear items and `ItemTags.SPEARS`; the mod
  recognised only its own. Level is still measured against the mod spear's durability, so a netherite
  spear lands at level 5 and only a max-level barracks will take it — that is the standard "tool too
  good" mechanic, not a fault.
- **Four pathfinding fixes, none of them visible.** The shared `java.util.Random` used once per
  considered A* transition — 25.7 million CAS operations in 90 seconds on the measurement colony,
  8.3 % of the search thread's core — is now `ThreadLocalRandom`. A cancelled search is removed from
  the 10 000-slot job queue instead of holding its slot. `Pathfinding.shutdown()` actually shuts the
  pool down rather than only clearing the queue, and can be restarted, so a single-player world change
  no longer leaks it; `getExecutor()` is synchronised, so two pools can no longer race into existence
  over one queue.
- **The colony view stopped resending its whole ticketed-chunk set every update.** The dirty flag was
  never cleared. **This is upstream's, not this port's** — byte-identical in the 1.21.1 oracle and in
  current upstream; the chunk-ticket work here added three more places that set it, which is what made
  a latent bug expensive. On a 1524-chunk claim that was the entire set in every packet.

One item from the list turned out to be already done: the path's start point does carry its rails flag,
mirrored from the boat fix in `60ad0dda`, so nothing changed there.

**0.0.19 is a performance release, and unlike 0.0.18 most of it was measured on a running server rather
than argued from source.** Four pieces: the pathfinder finally returns as soon as it has arrived, rails
get the macro edges water got in 0.0.17, the walking distance limit becomes a setting, and a free-mode
farmer works several cells per stop.

- **A search now stops when it arrives, if you let it.** New setting `stopsearchonarrival`, **default
  off**. Until now a search that had already found its route carried on expanding every node cheaper
  than that route before returning — on arrival it rebalanced its own distance estimate down to what the
  route actually cost, which left the queue with almost no sense of direction and turned the rest of the
  run into a breadth-first sweep. On ordinary ground that tail is small. On a corridor it is nearly the
  whole search: **800 blocks of rail falls from 6223 expanded nodes to 16, 796 blocks of open water from
  8301 to 30, 400 blocks of bare plain from 8109 to 402 — returning the identical path in every case.**
  On a live 1000-citizen colony the whole tail is only 3.7–5.6 % of the pathfinding thread, because
  93–96 % of searches never arrive at all and a search that never arrives has no tail; there it takes
  the pool from 30 % busy to 26 %. **It also repairs a regression shipped in 0.0.17**: the boat probe
  fan made an open sea *dearer* to search than before (801 nodes → 8301, hitting the node limit where it
  previously did not), because a fan of rays opens area where a march opens a strip. Nothing before
  arrival changes in any branch, so the 96 % that never arrive behave identically — measured, not
  assumed. It ships off because it moves every citizen, raider and animal on the server and **nobody has
  walked a path with it on**. If your colony uses boats or rails, it is the setting to turn on first.
- **Rails get macro edges.** A rail block's own state names the two blocks the track continues into, so
  a rail edge is a *walk* along the track rather than the ray probe water needed — which also means the
  recorded geometry survives curves, slopes and switches instead of being reconstructed as a straight
  line and steering the cart off the rails. Nodes to *find* a route: 2001 → 34 at 2000 blocks, 3001 → 49
  at 3000. On its own that bought no server time, for exactly the reason the setting above now fixes.
  Three real bugs surfaced on the way and are fixed: only the last block of an edge was flagged as
  track, which would have ejected a citizen one block after boarding; `railsLength` counted nodes, so a
  200-block ride made of edges read as 4 and failed the minimum-rails check; and the path's start point
  carried no rails flag, the cart twin of a boat bug fixed earlier.
- **`maxpathfindingdistance`** (default 900, range 100–5000) replaces the hardcoded limit on how far a
  citizen may be ordered to walk. Raising it does not make any single search work harder — the node
  budget is capped regardless of distance — but it only helps where there is a corridor: 3000 blocks
  along rails or open water computes; 3000 blocks over broken ground fails exactly as 150 blocks of
  broken ground already fails. The block cache that *did* grow with distance is now bounded, and a
  refused order no longer costs a stack trace.
- **A free-mode farmer works up to three cells per stop.** The cost of a cell is dominated by a
  deliberate pause the worker takes once per visit, so paying it once for three cells rather than three
  times is most of the win: **2.56 s per cell down to 1.19 s, measured on one field in one run —
  2.15×**, with 36 % fewer path searches per cell. Tilling, sowing and fertilising batch; harvesting a
  ripe crop deliberately does not, because breaking a real block goes through the mining delay every
  worker in the mod obeys. `freemodefarmerbatchsize`, default 3, range 1–8; the size doubles as the
  reach radius and a wall across a field still stops it. **Normal mode is untouched** — at batch size 1
  the loop collapses to the previous code line for line.

Also in this release: the beekeeper's Flowers list is no longer empty (it was lost in the client
compatibility sync, not in the tag — `#minecraft:flowers` is a strict superset in 26.2, 26 items to 31,
and the list is now read straight off the tag on both sides), a free-mode beekeeper defaults to every
flower instead of standing idle waiting to be told, and the tavern's visitor ceiling and arrival pace
are configurable (`visitorspertavernlevel`, `visitorintervalmodifier`).

**0.0.18 is six pieces of work landed as one release: boats, the two vehicles' passenger claim, claimed
chunks staying loaded, the farmer's seed handling, animal pens, and the scarecrow's rendering.** None of
it has been played — see *Verification status* below for exactly what that means here.

- **Boat speed is now a server setting, not a rebuild.** `/mc boatspeed [<blocks per second>]`, config
  key `boatspeed`, default 6 (unchanged from the old hardcoded value), range 2–20. Chosen in blocks per
  second so it reads against vanilla's `maxMinecartSpeed` the same way; the ceiling is where the
  navigator's one-tick-per-node steering stops being able to track the pathfinder's own grid, not a
  collision limit.
- **The boat arrival stall is fixed.** A hull idling against a pier — sliding along the face it is
  touching, bobbing — was read by the old stuck check as "still moving" every single tick, so the
  60-tick rescue that was supposed to end a stuck crossing never fired. The check now measures whether
  the boat is getting closer to where it is going, not raw movement, and it runs the whole time a
  citizen is aboard rather than only while the followed node still carries a boat flag.
- **Mobs can no longer climb into a colony boat or minecart.** Both vehicles now claim their one seat
  for the citizen they were spawned for before they ever enter the world, the way a citizen's own ferry
  already did on 1.21.1. A minecart left on the rails while its citizen finishes a delivery was open to
  anything that touched it in 26.2's rebuilt minecart boarding; it is not anymore. The equivalent
  dismount fix that already applied to boats — a citizen whose path ends while still riding is put down
  properly rather than left waiting — now covers minecarts on rails too.
- **A colony can keep its whole claimed territory loaded and ticking, not just the chunks with a
  building on them.** New setting `forceloadallclaims` (default **off**) and a per-colony override,
  `/mc colony forceloadclaims <colony> [on|off|default]`, so it can be turned on for the colony you are
  actually playing without changing the behaviour of every colony on a shared server. `maxforcedchunks`
  raised 256 → 1024 either way, since the old ceiling was sized for building-only tickets and was
  silently cutting off large colonies. Cost is real — a fully loaded claim runs full citizen AI with no
  player nearby, though mobs still do not spawn there — so this is opt-in and the command reports the
  ticket count against the cap rather than silently under-covering.
- **The farmer no longer strands a field.** Running out of seed used to mark a half-sown field as fully
  planted, which is why a field could sit mostly bare with a few scattered ripe crops forever — the next
  visit believed it was done. Out of seed now releases the field instead, and a seed that cannot
  actually be planted (reachable through the scarecrow's own selection window) is refused with a message
  naming the field, instead of tilling that field forever and planting nothing. A trampled or
  dried-out cell is re-tilled in the same visit it is sown, rather than waiting a colony day it usually
  does not survive.
- **Farm animals stay in their pen.** Every 26.2 mob already has a vanilla "home" that its own wander,
  panic and avoid goals respect — farm animals were just never given one. Herder buildings now set it to
  their own footprint, so cows, sheep, pigs, chickens and rabbits stop drifting off over a few in-game
  days; a stray gets walked back by the same restriction goal vanilla ships, with recall as a last
  resort. Leashing an animal out and unleashing it elsewhere still releases it on purpose. Off switch:
  `animalpencontainment` (default **on**, since the old behaviour is the bug). Free mode also now stocks
  a herder building with its own animals up to its level's cap, instead of leaving it empty until a
  player walks livestock in by hand.
- **The scarecrow renders correctly, with its lantern.** A stray humanoid body-and-hat shape was drawing
  underneath it — leftover geometry from the vanilla player mesh the model used to build on, which 26.2
  can no longer hide the way 1.21.1 did — and is gone. The lantern that sits on top when placed, cut
  during the port because the API it used to draw with no longer exists, is back, along with the
  similar placeholder shown over the colony flag while holding a banner.

**0.0.17 crosses open water in one hop instead of one search node per block.** A boat crossing used to
be searched a block at a time, so an 800-block sea cost 2200 expanded nodes — and a wide one could cost
16 000 and give up. Water is now probed: from a node on the surface the search casts straight rays in
eight directions, up to 64 blocks each, and each ray becomes a single node at its far end.

**Correction, measured in 0.0.19:** that release predicted "~60–120 nodes for an 800-block crossing
instead of 2200", and as a *total* that is wrong. What collapses is how long the search takes to
**reach** the destination — 805 nodes before, **18** after, on the same jar in the same world. The
total does not move, because after reaching, the search goes on expanding everything cheaper than the
route it just found, and on a corridor that is the corridor. The cause is located and written up in
[`../26.2/PATHFINDING-RAILS.md`](../26.2/PATHFINDING-RAILS.md); fixing it needs an admissible
heuristic, which this codebase does not have, and it affects roads and rails identically, so it was
deliberately not attempted here.

Boarding is priced honestly with it. The first water block used to cost `swimCostEnter` (24) even for a
citizen who was about to board a boat, because the search could not know yet whether five water blocks
would accumulate; a citizen with the Boats research now pays the boarding charge (8) at the water's
edge, and the `minimumwatertoboat` threshold is measured in blocks rather than in nodes. The practical
effect: **a boat becomes worth it from about 15 blocks of water instead of about 50**, so citizens will
cross medium lakes rather than walk around them. Two jobs are deliberately exempt — a citizen escaping
water still heads for the nearest dry block rather than the best-connected one, and the fisherman still
walks into the lake he was sent to for free.

This changes how paths are *found*, not how the boat moves: the course was already straightened in
0.0.15. Debug path rendering will show sparse nodes over water instead of a solid ribbon; that is the
change working.

**0.0.16 stops the storage racks, graves and huts from eating what is inside them, and repaints the
racks.** Breaking a rack, a grave or a hut dropped nothing at all: 26.2 moved the removal hook so that
`affectNeighborsAfterRemoval` now runs *after* the block entity is gone, and all three read the block
entity back out of the level from inside it. Player break, explosion or blueprint replacement alike,
the contents were destroyed. A building upgrade still keeps its chest — vanilla only fires the new
hook when the block actually changed, which is narrower than the 1.21.1 behaviour it replaces. Two
more rack defects go with it: a rack placed by a blueprint saved its stacks against an empty registry
(anything with dynamic components encoded to nothing), and the surviving half of a broken double rack
kept handing out its dead twin's nine slots, so items put there were lost.

The **purple rectangles on rack shelves** are gone too, and the cause was not what it looked like. An
empty display slot was mapped to `Blocks.AIR`, which used to mean *erase the quad*; Domum Ornamentum's
26.2 rewrite dropped quad erasure, so a replacement with no geometry now falls back to the block's
particle sprite — and vanilla's `block/air` model declares its particle as `minecraft:missingno`. One
missing texture per empty slot. Empty slots now carry a transparent sprite instead, and the rack omits
them from the texture map entirely.

The bundled **Domum Ornamentum** in this jar also carries the pillar-model fix: twelve `*_spec` models
inherited `block/cube_all` and never filled the six face slots it declares, which is fifteen
`Unresolved texture references` warnings on every resource reload.

**0.0.15 fixes what a 242-citizen colony's log exposed, and three defects in boat travel.**

A colony reported citizens standing about, `state=<no entity>` across the board and a log full of
`UUID of added entity already exists`. Behind it: `CitizenManager` treated a *failed* spawn as a
successful one. Upstream gates that on NeoForge's `Entity#isAddedToLevel()`; the port had substituted
`!isRemoved()`, which is not the same question — `addFreshEntity` refuses a duplicate UUID and returns
false without ever marking the entity removed, so the colony was handed a body that had never entered
the world. The spawn is now gated on the level's own answer, and a body that is loaded but sitting in
a chunk below entity-ticking level — invisible to `ServerLevel#getEntity(UUID)` while its UUID is
still taken — is detected before a second one is spawned on top of it. Visitors had no such guard at
all, which is why they duplicated the most. The same substitution is corrected in eleven places
across the citizen, visitor and animal managers.

Boats: a crossing used to be sailed as an axis-aligned staircase, because the A\* search expands only
the four cardinals and the boat was steered node by node. It is now steered at the farthest node it
has a clear line of water to, so the boat cuts straight across the same staircase. A crossing that
ended against an awkward bank used to strand its passenger forever — the disembark test waited for a
node the hull could never reach — so arrival is now judged on proximity to the exit, and a boat that
stops making progress is abandoned after three seconds and the citizen swims the remainder. The rider
also sat exactly 0.6 blocks too high: 26.2 moved the seat offset onto the *passenger's* entity type as
`EntityAttachment.VEHICLE`, which every player-shaped vanilla type declares and the citizen did not.

The colony's citizens still stop existing as entities past `maxforcedchunks` (default 256) — that is
this port's own cap on force-load tickets, not a defect. A colony of 138 buildings needs it raised.

**`/mc pathstats` measures why workers stand about.** The pathfinding pool is one thread shared by
every citizen, raider and animal on the server; when it saturates, a worker waits for a path it has
already asked for, and nothing in the game shows that. The command reports the queue wait, the search
time, how many searches ran out of node budget, how many repeat one from the last thirty seconds, and
how many walk orders were refused for being over 900 blocks away. Per-job measuring is **off** until
`/mc pathstats on`, which also clears the counters, so the window is always "since you asked".

**0.0.14 corrects the node-budget figure `0.0.13` printed.** That build inferred "ran out of node
budget" from the node count against `maxNodes`, but the search's real cutoff is
`maxNodes + maxCost² × 2` and a subclass may decline to stop even past it, so searches that finished
normally were counted as having given up. It is now recorded at the one place the loop actually
breaks. On the same fixture the figure moves from 60 % to 47 %. The outcome line was also a partition
in wording only — a search can exhaust its budget *and* arrive, so the three shares could add up past
100 %, which is what a 0.0.13 report showing `89 % arrived, 15 % ran out, 0 % no path` was saying.
Outcomes are now arrived / stopped short / no path, which do add to 100, and the budget has its own
line.

**With free mode on, a builder can put a hut straight at any level** — pick 5 in the Build Options
window and one work order takes it from nothing to level 5, no builder-hut level or research needed.
See [`../26.2/FREEMODE-BUILD-LEVEL.md`](../26.2/FREEMODE-BUILD-LEVEL.md).

**If your farmer never hoed the ground, this is the build that fixes it.** 26.2 split the vanilla
`#minecraft:dirt` block tag and grass fell out of it, so every field laid on grass — that is, every
normal field — was rejected cell by cell and the farmer walked it doing nothing. See
[`../26.2/FARMER-AUDIT.md`](../26.2/audit/FARMER-AUDIT.md).

**If a farm across the map held a field the hut next door should have had**, that is fixed too, along
with two more defects behind the same symptom — see [`../26.2/FIELD-ASSIGNMENT.md`](../26.2/FIELD-ASSIGNMENT.md).
A new **field stick** marks a field out as a rectangle in the world and binds it to a hut in one flow.

**If you play with an enclave** — chunks claimed well away from the colony — buildings there now claim
their own ground, the enclave stays loaded, citizens get beds near their work, and `/mc colony rehouse`
moves the ones already housed a thousand blocks away. See
[`../26.2/ENCLAVE-BUILD.md`](../26.2/ENCLAVE-BUILD.md) and
[`../26.2/ENCLAVE-FEATURES.md`](../26.2/ENCLAVE-FEATURES.md).

The farmer also **prepares its own ground** now, clearing stone and gravel and laying dirt, never
touching water. On by default, `farmerpreparesground` turns it off.

`minecolonies-26.2-0.0.55.jar` is a **single installable file**. All three dependencies sit inside
it in `META-INF/jars/`, and the loader brings them up as ordinary mods:

| Nested mod | Version |
|---|---|
| `blockui` | 0.0.1 |
| `domum_ornamentum` | 26.2-1.0.0 |
| `structurize` | 26.2-1.0.0 |

| File | `minecolonies-26.2-0.0.55.jar` |
|---|---|
| Size | 44 MB |
| Minecraft | 26.2 |
| Loader | Fabric, loader ≥ 0.19.3 |
| Java | 25 |
| Requires | Fabric API 0.154.2+26.2 or newer — nothing else |
| Environment | client **and** dedicated server |

## Installation

Exactly two files go into `mods/`:

```
mods/
├── fabric-api-0.154.2+26.2.jar
└── minecolonies-26.2-0.0.55.jar
```

### Do not add the three dependencies separately

**`blockui`, `structurize` and `domum_ornamentum` must not be present in `mods/` as their own
files.** Not because of a duplicate warning — because the loose jar takes the mod id and silently
shadows the nested one. The candidate sort in `ModPrioSorter#compare` (fabric-loader 0.19.3) is:

```
isRoot()  →  id  →  version  →  minNestLevel  →  parents
```

`isRoot()` comes **first**, so a jar in `mods/` wins over a nested one **regardless of version** —
bumping the nested version does not help. Nothing about this appears in the log; the classes from
the old jar simply execute. That is how a `mod init` crash was once produced against already-fixed
code, with a stack trace pointing at a line that no longer exists in the source.

Check the startup log: in the loaded-mod list `blockui` must appear **indented under
`minecolonies`**. If it sits at the top level, there is a stray file in `mods/`.

## Verification status

- **0.0.23's fix is traced, not reproduced.** The cause is certain — it is a stack trace from a real
  player's server, read line by line back to the source, and the throwing call it names is gone. What
  was **not** done is stage a colony over its bed count and watch a child be born: that needs a 300
  citizen town, which the harness here cannot build. This jar was booted: `Done (0.308s)!`, no
  `/ERROR]` and no `/FATAL]` line.

- **0.0.22's findings are measured; two of its three features are only partly so.** That jar came
  up on the harness below: `Done (0.326s)!`, `/ERROR] 0`, `/FATAL] 0`, `/WARN] 10`, with
  `minecolonies 0.0.22` over its three nested dependencies.

  **Guards:** every number is a reading off a live combat stand, including the three disproved
  theories, and both opt-in fixes were run with the setting on. Not covered: no mod raid was staged —
  combat used vanilla zombies — so the branch where alerting guards walks every building in the colony
  is still only read, and it is the most likely source of real load. Cavalry, druid, training yards,
  `FOLLOW` and the rally banner are untouched. The stand world is flat, so all pathfinding figures are
  a lower bound.

  **Generations:** the mechanic was run with the lifespan shortened to 7 days — 229 deaths of old age
  over 7 dawns, ages at death 5.3–5.8, nobody died on the first dawn, age survived a full restart, and
  population grew past the bed count to the cap. **A full generation at the shipped 100-day lifespan is
  33 hours of loaded play and has not been observed**; nor has a grave or a child been seen by eye. The
  run needed a temporary build with a system property to make a colony tick with no player present —
  both edits were reverted and are absent from the tree. Note the fixture was a stress case, 724 of 999
  homeless and almost nobody working, so it says nothing about whether the default numbers feel right.

  **Mixed seeds:** the command, the five-seed cap, seed validation, old-save loading and a full NBT
  round trip across a restart are all verified on a running server. **The scarecrow window has not been
  seen** — its layout is computed from coordinates and every new panel is null-checked so a mismatch
  leaves a gap rather than crashing a client. **Striped sowing has not been watched either**: the only
  world here with a colony and fields is the chunk-ticket fixture, and a scarecrow cannot be placed
  without a client.

- **0.0.21's worker findings are measured on a running server; its fixes are not.** Its jar came
  up on the harness: `Done (0.316s)!`, `/ERROR] 0`, `/FATAL] 0`, `/WARN] 10`, with
  `minecolonies 0.0.21` over its three nested dependencies. The four worker numbers quoted above —
  44 cycles at zero movement, 8009 nodes against 205, 20.2 seconds in `IDLE`, 4.1 seconds at a chest —
  are all readings from a live stand, and so is the oracle comparison across all 75 worker AI files.
  **The fixes themselves were verified only by compiling and booting**: no fisherman has been watched to
  see whether it backs off, no lumberjack timed after the idle change.

  One deliberate side effect to watch, because it is the kind of thing only play will settle: workers
  whose `hasWorkToDo()` is constantly true — lumberjack, farmer, planter, enchanter, alchemist — can no
  longer reach `idle()` at all, so they stop sitting and wandering around the hut when they have nothing
  to do. A farmer with no fields also now cycles roughly once every 1.5 seconds instead of once every 11.
  Neither involves a path search, but both are visible.

  The three new limits (`maxbuilderdistance`, `maxpathfindingdistance` at 2000, `decorationsoutsidecolony`)
  are read-from-source changes with no measurement behind the defaults; they were chosen to be useful,
  not derived. A builder sent 2000 blocks needs the chunks along the route loaded the whole way, which a
  colony will not do for the middle of it.

- **0.0.20 is compiled and booted; three of its ten items rest on a real measurement and the rest on
  reading.** This exact jar came up on the harness: `Done (0.415s)!`, `/ERROR] 0`, `/FATAL] 0`,
  `/WARN] 10`, with `minecolonies 0.0.20` over its three nested dependencies. Measured: the florist's
  soil (the cascade was reproduced on a live server before the fix, and the generated tag file was
  read back after datagen), the vanilla tag contents for spears and eggs (unzipped out of
  `server-26.2.jar`), and the exit-condition figures carried over from 0.0.19. Read from source, not
  observed: the colony-view packet saving, the four pathfinding pool fixes, and every gameplay
  consequence — **no flower has been watched to see whether it survives, no coloured egg picked up,
  no spear handed to a cavalryman.** The one claim that was checked against history rather than
  either: the path start point already carried its rails flag, so the tenth item was dropped.

  Two things to watch when playing it. `stopsearchonarrival` is now on by default and changes routing
  for every entity on the server — if citizens walk visibly oddly, set it false, which is reversible
  and writes nothing to the world. And a netherite spear will be refused by a barracks below max
  level, which reads like a bug and is the intended tool-level mechanic.

- **0.0.19 was the first release here with numbers taken from a running server.** `gradle build` was
  green and its jar came up on the harness below: `Done (4.842s)!`, `/ERROR] 0`, `/FATAL] 0`,
  `/WARN] 10`, with `minecolonies 0.0.19` over its three nested dependencies. Beyond that, the
  pathfinding and farmer figures quoted for 0.0.19 are **measured, not derived**: the exit-condition
  numbers come from a live 1000-citizen colony sampled A/B/A/B within one boot plus purpose-built
  corridor fixtures, and the farmer's 2.56 → 1.19 s per cell from one field, one run, one worker with
  the arms toggled by the real free-mode switch. Where a claim is still read from source rather than
  measured, the write-ups say so line by line — `26.2/PATHFINDING-EXIT.md`,
  `26.2/PATHFINDING-RAILS.md`.

  **What is still not verified is everything a person would see.** There is no client in this
  container, so no path has been walked, no cart ridden, no field watched. Two 0.0.19 items rest
  hardest on that: `stopsearchonarrival` changes how *every* entity on the server routes and is
  therefore shipped off; and one measurement is honestly unexplained — `PathJobMoveToLocation` came
  out 8 % cheaper end to end while its own arrival tail measures 2 %, so the split is 5 %
  attributable against 10 % observed.

- **Everything in 0.0.18 is compiled and booted, and none of it has been played.** Every behavioural
  claim in the 0.0.18 list above was derived from reading vanilla 26.2's source and this mod's, and
  then compiled — **not one of them has been observed in a running game.** No boat has crossed water,
  no cow has been contained, no scarecrow has been looked at. The changes with the most riding on an
  unverified reading, in order: the boat arrival stall (the mechanism is inferred from a screenshot
  plus hull geometry, and the fix logs the citizen, node and distances precisely so the next
  occurrence can be diagnosed from a server log rather than guessed at again); animal containment
  (whether it *feels* right rather than pinning animals against a fence is exactly the thing
  arithmetic cannot answer); and the scarecrow (the corrected geometry was verified by
  re-implementing the model's UV layout in software and comparing against pixels in a screenshot,
  which is a good check and is not the same as looking at it). **The water macro-edges are additionally unmeasured**: the node counts quoted for
  0.0.17 are the design's prediction, not a reading. They were checked another way — the search's own
  probe, cost and path-building methods were driven directly against a synthetic water grid, 63 checks
  with no mismatch, and the cost formula reproduces the design's figures exactly — but no crossing has
  been searched in a running world. The model
  audit (`../26.2/tools/model-atlas/model_atlas_audit.py`) is clean on this jar, and the bundled
  Domum Ornamentum is byte-identical to the fixed build. Nothing beyond that was observed: **no rack
  was broken, no shelf was looked at, no citizen was watched spawning, no boat was sailed and no seat
  was looked at** — there is no client in the build container. The spawn fix is argued from vanilla's own
  `ServerLevel#addEntity` returning false without marking the entity removed; the seat offset is
  arithmetic against `Entity#positionRider` and `Avatar.DEFAULT_VEHICLE_ATTACHMENT` (0.6 exactly).
  Both still want a human with a client. To check the boats: send a citizen across open water at least
  five blocks wide and diagonally — the wake should be one straight run rather than axis-aligned steps,
  and the citizen must step ashore, never sit in the boat indefinitely.
- **The farmer fix was verified in a running game, both directions.** On an identical grass field:
  before, 0 tilled cells out of 121 with the field already advanced to `HOED`; after, 18 within 50 s
  and 80 within 3 minutes. A control on plain `dirt` with the *unfixed* build tilled normally, so the
  failure tracked the block type rather than the test harness. The tag change itself was confirmed in
  this jar's bytecode, and the replacement tag was diffed against the real 1.21.1 server jar: it
  loses nothing the old `#minecraft:dirt` held and gains one block that did not exist back then.
- **Every vanilla tag the mod names was diffed 1.21.1 → 26.2**, all 83 of them, expanded recursively
  to concrete blocks and items. Exactly two lost members: `#minecraft:dirt`, which is the bug above,
  and `#minecraft:needs_stone_tool`, where copper doors left every `needs_*_tool` tag — so the mod's
  harvest-level lookup now returns 0 for them, matching vanilla. No other tag drifted.
- `build` green, `validateAccessWidener` green.
- `runDatagen` green and reproducible: 5039 files, diffed against the previous version's output as
  an oracle, including 3481 generated textures compared by pixel.
- **The production artefact was booted, not just the dev classpath.** This jar was placed in the
  `mods/` folder of a real Fabric server install (fabric-installer, loader 0.19.3, Fabric API
  alongside) and started: `Done (4.421s)!`, zero `/ERROR]` lines, with the three dependencies
  listed as nested mods.
- `runServer` on a clean world with the full mod: `Done (6.327s)!`, zero `/ERROR]` lines. The
  datapacks loaded for real — 161 recipes for 16 crafters, 208 researches in 4 branches, 103
  effects, quests, 1761 items with NBT keys.
- **Models were checked against the atlases statically** (`../26.2/tools/model-atlas/`), reproducing
  both of 26.2's fatal checks on the built jar: 17 rejected block models and 14 unbakeable item
  models before the fix, zero after. This proves the baker will accept them; it says nothing about
  how they look.

- **The free-mode level jump was verified in a running game, end to end.** A level 1 builder hut was
  handed a level 5 work order for an unbuilt residence and built it: the hut ended at `5/5` with the
  child stash from the level 5 blueprint registered, and the order survived a full server restart on
  the way. Above the hut's maximum is refused, and with free mode off the same request falls back to
  the ordinary one-step upgrade. The level picker in the window is client side and was written blind -
  the build container has no display - so that part is for a human with a client to confirm.
- **`/mc pathstats` was measured on a 999-citizen colony, and it caught a bottleneck being created.**
  On the stock config the queue wait was 24 ms average, 187 ms worst, backlog 0, pool busy 22 %.
  Raising `pathNodeLimitMultiplier` from 1 to 4 and changing nothing else moved that to **409 ms
  average, 2.72 s worst — 8.2 server ticks — with a standing backlog of 153 and the pool 67 % busy**,
  and the command's own verdict line named it: "the queue is the bottleneck". Eight walk orders sent
  2000 blocks out moved the refusal counter 0 → 393 in 18 seconds, naming the citizen and the
  destination. **The 0.0.14 correction was re-measured on the same fixture**: two minutes on the
  999-citizen world gave 14 136 searches, a 21 ms queue wait, the pool 22 % busy, and outcomes of
  51 % arrived / 49 % stopped short / 0 % no path — adding to exactly 100, against a separate 47 %
  that gave up on the node budget. 17 `/ERROR]` lines, all the fixture's own `Error loading
  blueprint`; zero `Log.catching`, zero `MissingPaletteEntryException`.
  Separately, this exact jar was driven from a server console in all four states on an
  empty world, which is also what proves the twenty-one new translation keys reached it and render —
  a broken key shows as its own source text. The one thing not established by measurement is the
  cost of leaving measuring **on**: seven paired `tick sprint` runs gave 15.19 vs 15.89 ms/tick while
  the control runs alone spanned 14.46–16.52 ms, so the overhead is below what a colony that size can
  resolve. The arithmetic — ~140 ns per job, ~6.5 jobs a tick — puts it near 0.002 % of the tick
  budget. Full account in [`../26.2/PATHFINDING-DISTANCE.md`](../26.2/PATHFINDING-DISTANCE.md) §12.
- **No debug harness ships in this jar.** Six were written during this work and all six are gone;
  `unzip -l` over the built artefact finds none of them, and the same scan over this build finds only
  `CommandPathStats`, which is meant to be there, and upstream's own `CommandToggleDebug`.
- **A citizen has now crossed 800 blocks of open ocean by boat**, shore to shore, at 4.8 blocks a
  second against 6.1 on foot and 8.0 in a minecart. That closes the gap
  [`../testworlds/README.md`](../testworlds/README.md) flagged.
- **Long journeys have a hard ceiling, and it is not the jar's fault.** The navigator refuses any
  destination more than **900 blocks** away in a straight line, before a search is ever created —
  measured at exactly 901 failing and 899 succeeding — and snaps the citizen home. Below that,
  infrastructure decides everything: over natural terrain an 800-block walk dies about 100 blocks in,
  while the same distance along a rail costs 802 nodes and arrives. See
  [`../26.2/PATHFINDING-DISTANCE.md`](../26.2/PATHFINDING-DISTANCE.md).

- **This jar has been played, not just booted.** Roughly three person-days of hands-on testing on a
  live client and a dedicated server: colonies founded and grown, huts placed and built, the hut GUIs
  and module tabs driven by hand, workers followed around, raids called in, and every added feature
  exercised in game.

Anything still off? Please
[open an issue](https://github.com/unknown-wq/minecolonies/issues).

## Rebuilding

From the repository root, with the dependency jars available at the paths in
`../26.2/gradle.properties`:

```sh
cd ../26.2 && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 /opt/gradle-9.6.1/bin/gradle build
```

The result appears in `../26.2/build/libs/`.
