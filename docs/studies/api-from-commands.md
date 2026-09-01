# The command tree as an API surface

What each command under `mc/core/commands/` would look like as a method on the object API of
[`api-needs.md`](api-needs.md). Date: 2026-08-26. A study, not an implementation: no Java is changed
by it and none is proposed in detail.

`26.3/src/main/java/com/minecolonies/` is abbreviated to `mc/`.

Evidence standard, the same as the sibling studies in this directory:

* **[VERIFIED]** — the file was opened and the cited `path:line` says what is claimed.
* **[UNCHECKED]** — inference, not observed.

## Why the command tree is worth reading this way

Eighty-six files sit under `mc/core/commands/`; twelve of them are scaffolding (the Brigadier tree and
its entry point, the four argument types, the three permission interfaces, a chat-click helper, an
argument-name constants class and the shared `ColonyChunkReport`) and **seventy-four are
commands** [VERIFIED, the file list of `mc/core/commands/` against `EntryPoint.java:30-144`]. One more,
`CommandToggleDebug`, lives outside that directory at `mc/core/debug/command/CommandToggleDebug.java`
and is registered by the same entry point (`EntryPoint.java:143`) [VERIFIED]. **Seventy-five commands
are covered below.**

Every one of them is a verb that already works server-side. That matters for a different reason than
the GUI packet handlers inventoried in [`api-inventory.md`](api-inventory.md): a packet handler is
reachable only from a client, whereas a command executor runs against a `CommandSourceStack` that the
server console also holds. Where a command needs nothing but the source's *dimension* or *position*,
the verb behind it is already callable without a player at all. That question — does this verb need a
real client, an identity, or nothing — is asked of every entry below, because it is the single fact
that decides whether the API can have the verb cheaply.

## The shared frame

Four facts hold for the whole tree and are not repeated in the entries.

**Registration.** `EntryPoint.register` builds three subtrees — `kill`, `colony`, `citizens` — and
hangs them plus thirteen top-level commands off `/minecolonies`, then repeats the whole thing under the
alias `/mc` (`EntryPoint.java:30-148`) [VERIFIED]. The two roots are not identical:
`CommandEntityTrack` (`:128`) and `CommandToggleDebug` (`:143`) exist **only** under `/mc`, and
`ScanCommand` (`:121`) exists **only** under `/minecolonies` [VERIFIED]. `CommandHomeTeleport` is
registered twice, inside the colony subtree (`:52`) and at the root (`:109`, `:131`) [VERIFIED].
Paths below are given in the `/minecolonies` form except where only the alias carries the command.

**The colony argument.** `ColonyIdArgument` accepts five spellings of a colony: the literal `here`
(the colony at the source's position), `mine` (the colony owned by the calling player, which is the
one option that calls `getPlayerOrException`, `ColonyIdArgument.java:164`), a numeric id, a player
UUID and a player name (`ColonyIdArgument.java:36`, `:127-258`) [VERIFIED]. `MultiColonyIdArgument`
wraps the same five and adds `all` (`MultiColonyIdArgument.java:27-32`) [VERIFIED]. In API terms
`here` is `Colonies.nearest`, `mine` is a lookup by owner, and the rest are `Colonies.byId`; none of
them belongs in a method signature, which takes a `ColonyId`.

**How "no such colony" is reported.** `ColonyIdArgument.getColony` sends a failure component and then
throws a `RuntimeException` (`ColonyIdArgument.java:109-111`) [VERIFIED], which
`IMCCommand.checkPreConditionAndExecute` catches and logs as a warning (`IMCCommand.java:86-89`)
[VERIFIED]. Every command that resolves a colony this way therefore has the same untyped refusal, and
it is not restated per entry: in the API this is `NoSuchColonyException(ColonyId)` per §4 of
api-needs.md.

**The three permission gates.**

| interface | who passes | evidence |
|---|---|---|
| `IMCCommand` (default) | any `Player` entity, **or** any source holding `Permissions.COMMANDS_OWNER` | `IMCCommand.java:100-103`, `:26-29` [VERIFIED] |
| `IMCOPCommand` | a source holding `COMMANDS_OWNER`, **or** a `Player` who is on the server op list | `IMCOPCommand.java:20-38` [VERIFIED] |
| `IMCColonyOfficerCommand` | as `IMCOPCommand`, **or** a `Player` whose colony rank `isColonyManager()` | `IMCColonyOfficerCommand.java:21-43` [VERIFIED] |

The server console holds `COMMANDS_OWNER`, so it passes all three. Eight commands add a second gate on
top: a `ServerConfiguration` flag that decides whether a non-op *player* may use them at all. Those are
named in the entries.

**Coverage.** The seventy-five commands are grouped below by the api-needs.md block they belong to,
not by their directory. Twenty-four fit no block and are collected in section 15. Every command that
has an API form carries a four-part contract — signature, parameters, observable effect, return
fields — and all forty-eight methods those contracts define are listed together in **The methods, in
one place**, immediately before section 16.

| disposition | commands |
|---|---|
| maps onto an api-needs.md row, wholly or in part | 23 |
| a verb the API should gain that no row names | 8 |
| out of scope: the API should not have this verb | 44 |
| **total** | **75** |

---

## 1. §2.1 Colony

### `/minecolonies colony list [startpage:int≥1]`

* **What it does.** Walks `IColonyManager.getAllColonies()` and prints nine colonies per page: id, name,
  owner name, citizen count and centre coordinates, with click-to-run links to `colony info` and
  `colony teleport` and prev/next page buttons.
* **How it answers today.** `sendSuccess` only: a page header (`CommandListColonies.java:96`), two lines
  per colony (`:101`, `:111`) and a pager footer (`:123-124`) [VERIFIED]. No `sendFailure`; an
  out-of-range page silently becomes page 1 (`:74-77`) [VERIFIED].
* **Player required?** No. Nothing in `executeCommand` touches the source beyond `sendSuccess`
  (`CommandListColonies.java:61-126`) [VERIFIED].
* **Permission gate.** `IMCCommand` — any player, or an op source (`CommandListColonies.java:22`)
  [VERIFIED].
* **API form.**
  * *Signature.* `List<ColonySummary> Colonies.list()`
  * *Takes.* Nothing.
  * *Does.* Reads every colony the server holds, in every dimension. Mutates nothing.
  * *Returns.* `List<ColonySummary>`, `ColonySummary` exactly as §3.2 defines it. One element per
    colony; ordering is the manager's own and is not specified, so a caller that wants an order sorts.
    Empty when the server has no colonies. Fields: `ColonyId id` (number plus dimension, from
    `getID`, `mc/api/colony/IColony.java:98`, and `getDimension`, `:304`) [VERIFIED];
    `String name` (`:65`) [VERIFIED]; `PlayerRef owner` (uuid and name, from
    `mc/api/colony/permissions/IPermissions.java:132` `getOwner` and `:91` `getOwnerName`)
    [VERIFIED]; `BlockPos center` (`IColony.java:58`) [VERIFIED]; `int citizenCount` (a count, from
    `mc/api/colony/managers/interfaces/ICitizenManager.java:121` `getCurrentCitizenCount`)
    [VERIFIED]; `int townHallLevel` (a building level 0–5, from the town hall via
    `ICommonRegisteredStructureManager#getTownHall`) [UNCHECKED]; `boolean loaded` (whether the
    colony object is live on this server rather than a view) [UNCHECKED]. Cannot refuse.
* **Delta.** Nothing but a wrapper, and the wrapper does not live here: the traversal is
  `IColonyManager.getAllColonies()` and the fields are plain getters. Paging is a chat concern and is
  dropped — §3's convention is that a query with no answer returns an empty list, and there is no
  cursor in the design.
* **Matching row.** §2.1 `Colonies.list()`, Tier 1.

### `/minecolonies colony info <colony>`

* **What it does.** Prints one colony's id, name, mayor, `citizens/maxCitizens`, a housing line
  (adults, children, beds, homeless adults), a separate line when children are homeless, the centre
  coordinates, hours since the last owner or officer contact, and a line if raids are disabled.
* **How it answers today.** `sendSuccess` throughout: identity (`CommandColonyInfo.java:51`), mayor
  (`:53`), population (`:55`), housing (`:82-87`), homeless children (`:93`), coordinates (`:97-99`),
  last contact (`:100`), "cannot be raided" (`:104`) [VERIFIED]. The config refusal is also a
  `sendSuccess` (`:46`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCColonyOfficerCommand` (`:20`), plus `canPlayerUseShowColonyInfoCommand` for
  a non-op (`:44-48`) [VERIFIED].
* **API form.** Three methods.

  **1.**
  * *Signature.* `ColonyIdentity colony.identity()`
  * *Takes.* Nothing.
  * *Does.* Reads the colony's fixed description. Mutates nothing.
  * *Returns.* `ColonyIdentity` as §3.2 defines it. `ColonyId id`, `String name`
    (`mc/api/colony/IColony.java:65`), `PlayerRef owner`
    (`mc/api/colony/permissions/IPermissions.java:132`, `:91`), `String dimension`
    (`IColony.java:304`), `BlockPos center` (`:58`), `ChunkRef centerChunk` (a shift of `center`, no
    separate source), `long foundedGameTime` (ticks — **no source in the tree**; `IColony.java:484`
    `getDay()` is a day counter, not a founding mark), `StyleId style` (`:223` `getStructurePack`)
    [VERIFIED for each cited line]. Cannot refuse.

  **2.**
  * *Signature.* `ColonyProgress colony.progress()`
  * *Takes.* Nothing.
  * *Does.* Reads the one-call "where am I" summary: population split, building counts and the four
    landmark levels. Mutates nothing.
  * *Returns.* `ColonyProgress` as §3.2 defines it, fourteen `int` fields, every one a count or a
    building level rather than a ratio. `int citizens`
    (`mc/api/colony/managers/interfaces/ICitizenManager.java:121`) [VERIFIED];
    `int citizenCap` (`:100` `getMaxCitizens`) [VERIFIED]; `int adultMen`, `int adultWomen`,
    `int children` (a walk of `:82` `getCitizens()` reading `mc/api/colony/ICitizen.java:43`
    `isChild()` and `:29` `isFemale()`) [VERIFIED]; `int buildings`,
    `int buildingsUnderConstruction`, `int buildingTypesBuilt`, `int townHallLevel`,
    `int maxBuilderHutLevel`, `int warehouseCount`, `int courierCount`, `int universityLevel`,
    `int researchFinished` — each a filter or a maximum over the building manager's map that
    **nothing in the tree performs today**; the warehouse list is the one exception,
    `mc/api/colony/managers/interfaces/IRegisteredStructureManager.java:183` `getWareHouses()`
    [VERIFIED]. Cannot refuse.

  **3.**
  * *Signature.* `ColonyCapacity colony.capacity()`
  * *Takes.* Nothing.
  * *Does.* Reads the population ceiling and what sets it. Mutates nothing; note that the count it
    reads is only refreshed by `ICitizenManager#calculateMaxCitizens`
    (`mc/api/colony/managers/interfaces/ICitizenManager.java:61`) [VERIFIED], which this method must
    **not** call, or the query would mutate.
  * *Returns.* `ColonyCapacity` as §3.2 defines it. `int beds` and `int bedsFree` (counts of bed
    blocks, per residence only in this tree — **no colony-wide aggregate exists**);
    `int citizens` (`ICitizenManager.java:121`) [VERIFIED]; `int citizenCap` (`:100`) [VERIFIED];
    `CapSource capSource`, one of `BEDS`, `RESEARCH`, `CONFIG`, `TOWN_HALL` — **not readable**, since
    `getMaxCitizens` returns the minimum of three terms and discards which one bound it, so the three
    inputs (`:107` `getPotentialMaxCitizens`, `:114` `maxCitizensFromResearch`, and the config
    ceiling) must be compared again [VERIFIED for the three accessors]; `int configCeiling`
    (`maxCitizenPerColony`) [UNCHECKED]. Cannot refuse.
* **Delta.** The report is assembled inline in the executor and must become records. The one piece of
  real logic here is the homeless count — counted citizen by citizen rather than derived from
  population minus beds, with children excluded (`:61-78`) [VERIFIED] — and that loop is the body to
  extract. `lastContactInHours` and the raid flag have no field in `ColonyProgress`; the first is an
  operator's number that belongs in §2.14, the second in `RaidForecast` (§2.10).
* **Matching row.** §2.1 `colony.progress()`, Tier 1; partly §2.1 `colony.capacity()`, Tier 1.

### `/minecolonies whereami`

* **What it does.** Finds the closest colony to the caller and reports its name, id and the horizontal
  distance to its centre, wording the answer differently according to whether the caller is standing
  inside any colony's claim.
* **How it answers today.** Neither `sendSuccess` nor `sendFailure`: three `MessageUtils.format(...)
  .sendTo((Player) sender)` calls (`CommandWhereAmI.java:34`, `:45`, `:49`) [VERIFIED]. The command
  returns 0 even on success (`:51`) [VERIFIED].
* **Player required?** **Yes, twice over.** `context.getSource().getEntity()` at `:27` is dereferenced
  with no null check at `:29` (`sender.blockPosition()`), and every output is cast to `Player`
  [VERIFIED]. The position is the only *semantic* need; the casts are only there because the answer is
  delivered as a chat message. From a console this throws.
* **Permission gate.** `IMCCommand` (`:17`) [VERIFIED].
* **API form.** Two methods; `chunkOwner` is contracted in full under `colony claiminfo` in
  section 11 and is only named here.

  * *Signature.* `List<ColonyDistance> Colonies.nearest(WorldPos pos, int maxBlocks)`
  * *Takes.*
    * `WorldPos pos` — the point to measure from: a dimension id and a block position. Required. A
      dimension the server does not have is rejected.
    * `int maxBlocks` — the search radius in blocks. Required, must be positive and at or below the
      server's scan limit; anything else throws `IllegalArgumentException` per §4.
  * *Does.* Measures the horizontal distance from `pos` to the centre of every colony in that
    dimension and keeps those within `maxBlocks`. Mutates nothing. It must **not** reuse
    `IColonyManager#getClosestColony` (`mc/api/colony/IColonyManager.java:233`) [VERIFIED], which
    short-circuits on the chunk owner and so is not a distance query at all on claimed ground.
  * *Returns.* `List<ColonyDistance>` as §3.2 defines it, **nearest first**, empty when nothing is in
    range. Each element: `ColonySummary colony` (see `Colonies.list()` above);
    `double distanceBlocks` (blocks, horizontal, derived from `mc/api/colony/IColony.java:91`
    `getDistanceSquared(BlockPos)`) [VERIFIED]; `int distanceChunks` (the same divided by 16).
    Cannot refuse.
* **Delta.** Needs a caller-supplied `WorldPos` instead of the source entity's position, and the two
  distinct questions the command conflates — nearest colony, and whose claim am I on — become two
  calls. `IColonyManager.getClosestColony` returns one colony and takes no radius, so the list form is
  a new loop; api-inventory says the same.
* **Matching row.** §2.1 `Colonies.nearest()`, Tier 1; §2.11 `Colonies.chunkOwner()`, Tier 1.

### `/minecolonies whoami`

* **What it does.** Looks up the colony owned by the calling player and prints the player's name, the
  colony's name, its id and its centre.
* **How it answers today.** `MessageUtils` again, not `sendSuccess`: the no-colony case at
  `CommandWhoAmI.java:36` and the answer at `:44` [VERIFIED].
* **Player required?** **Yes, for identity.** `getEntity() instanceof Player` at `:26-30`, and the
  player's UUID is the lookup key at `:32` [VERIFIED]. A console caller returns 0 and prints nothing.
* **Permission gate.** `IMCCommand` (`:16`) [VERIFIED].
* **API form.** New method; §2.1 has no row for it.
  * *Signature.* `List<ColonySummary> Colonies.ownedBy(PlayerRef owner)`
  * *Takes.*
    * `PlayerRef owner` — the player whose colonies are wanted, by uuid; the name field is ignored for
      the lookup. Required. The player need not be online. An unknown uuid is not an error and yields
      an empty list.
  * *Does.* Looks up the colony owned by that player in each dimension the server has. Mutates
    nothing. Making the identity an argument rather than the caller is the whole point: it is what
    lets a console ask the question at all.
  * *Returns.* `List<ColonySummary>`, the §3.2 record, one element per dimension in which that player
    owns a colony — a list rather than a single value because
    `IColonyManager#getIColonyByOwner(Level, UUID)` (`mc/api/colony/IColonyManager.java:273`)
    [VERIFIED] is keyed per level. Ordering is by dimension id. Empty when the player owns nothing.
    Cannot refuse.
* **Delta.** The lookup itself is `IColonyManager.getIColonyByOwner` and is one call; the delta is
  taking the player as a parameter rather than as the caller, and returning a list because the
  underlying store is keyed per dimension.
* **Matching row.** **None.** The API should gain it: `Api.callerIdentity()` (§2.13) plus a
  by-owner lookup is how an automation account finds the colony it is meant to drive, and §2.1's
  three lookups (`list`, `byId`, `nearest`) cannot answer "which colony is mine".

---

## 2. §2.2 Construction & Build Orders

### `/minecolonies colony repairall <colony> [preview]`

* **What it does.** Files a repair work order for every building in the colony that can take one, in a
  stable position order, counting and naming the ones that refuse. With `preview` it changes nothing
  and reports what would be queued.
* **How it answers today.** `sendSuccess` only, through a private `emit`/`report` pair that also
  accumulates the full text for the server log: per-building lines at
  `CommandColonyRepairAll.java:293`, an "and N more" line at `:301`, and the summary lines at `:321`
  [VERIFIED].
* **Player required?** No. The executor reads only the colony and the building manager
  (`:86-88`) [VERIFIED].
* **Permission gate.** `IMCColonyOfficerCommand` (`:59`) [VERIFIED]. No config flag.
* **API form.** Two methods.

  **1.**
  * *Signature.* `BuildEligibility building.buildEligibility(int targetLevel)`
  * *Takes.*
    * `int targetLevel` — the level an order would aim at. Required. Below 1 or above the type's
      maximum throws `IllegalArgumentException` per §4.
  * *Does.* Answers whether a builder would accept an order for this building at this level, and what
    blocks it. **Mutates nothing** — which is the entire difficulty, because in this tree the same
    decisions are taken inside a mutating call.
  * *Returns.* `BuildEligibility` as §3.3 defines it. `boolean allowed`; `int targetLevel` (echoed);
    `int maxBuilderHutLevel` (a level 0–5, the maximum over the colony's builder huts — **no
    accessor exists**, only a per-level yes/no); `EligibilityBlock block`, one of the eight constants
    of §3.3; `String message` (human-readable). The `block` values map onto the existing
    `WorkOrderRequestResult` constants — `NO_BUILDER_GOOD_ENOUGH`
    (`mc/api/colony/workorders/WorkOrderRequestResult.java:20`), `NO_BUILDER_IN_RANGE` (`:22`),
    `ALREADY_QUEUED` (`:16`), `TOO_HIGH` (`:24`), `TOO_LOW` (`:26`), `NO_BLUEPRINT` (`:33`),
    `NOT_BUILT` (`:35`), `DECONSTRUCTED` (`:40`) [VERIFIED] — but `OUTSIDE_CLAIM` has no counterpart
    that is decided before the order is queued. Cannot refuse; it is a query.

  **2.**
  * *Signature.* `BuildOrderResult building.requestRepair()`
  * *Takes.* Nothing.
  * *Does.* Files a repair work order for this building, which the work manager then hands to a
    builder on its own tick. **Side effects a caller would not predict from the name:** the order
    wraps the building in construction tape as soon as it is added, and the builders will consume the
    materials each repair needs until the queue empties.
  * *Returns.* `BuildOrderResult` as §3.3 defines it: `boolean created`; `BuildOrderId order` (the
    absent sentinel when nothing was created); `CommandOutcome outcome`. Refusal codes it can
    produce: `REFUSED_STATE` (level 0, deconstructed, an order already open), `REFUSED_RULE` (no
    builder good enough, no builder in range, target out of the world), `REFUSED_UNKNOWN_TARGET` (no
    blueprint recorded). The underlying call already returns every one of these as a
    `WorkOrderRequestResult` (`mc/api/colony/buildings/IBuilding.java:228`
    `requestRepair(BlockPos, boolean)`) [VERIFIED].
* **Delta.** This command is the closest thing in the tree to `buildEligibility`, and it is worth
  reading before that row is built. `repair(building, preview)` (`:138-168`) [VERIFIED] returns a
  typed `WorkOrderRequestResult` rather than chat, and in preview mode it evaluates three refusals
  without acting — level 0 (`:140`), deconstructed (`:144`), already queued (`:149`) — and a fourth,
  the missing blueprint, by a pure read (`:161-163`) [VERIFIED]. Its own comment states the limit
  exactly: everything past those "is decided inside requestRepair, which cannot answer without also
  acting" (`:155-160`) [VERIFIED]. So the delta for `buildEligibility` is precisely the refusals this method
  could not preview, and the delta for `requestRepair` is nothing but a wrapper — `IBuilding
  #requestRepair` already returns the result. The colony-wide loop is not an API call; a caller writes
  it over `colony.buildings()`.
* **Matching row.** §2.2 `building.requestRepair()`, Tier 2; §2.2 `building.buildEligibility(int)`,
  Tier 1.

---

## 3. §2.3 Buildings & Levels

### `/minecolonies colony restorehuts <colony> [confirm]`

* **What it does.** For every building the colony still records, checks whether the matching hut block
  is in the world and, with `confirm`, puts it back — the repair for a world that was opened once
  without the mod and lost every one of its block entities. Bare, it counts what is missing and
  changes nothing.
* **How it answers today.** `sendSuccess` for the count (`CommandColonyRestoreHuts.java:124`), the
  per-building lines (`:356`), the "and N more" line (`:364`) and the summary (`:384`);
  `sendFailure` once, when the colony has no world (`:148`) [VERIFIED]. Individual failures are
  strings built inline (`:220`, `:226`, `:245`, `:251`, `:262`, `:266`, `:293`, `:297`) [VERIFIED].
* **Player required?** No. Everything runs off the colony's own `Level`.
* **Permission gate.** `IMCOPCommand` (`CommandColonyRestoreHuts.java:86`) [VERIFIED].
* **API form.** None proposed.
* **Delta.** Not applicable.
* **Matching row.** **None,** and the API should deliberately not have it. §5 non-goal 4 of
  api-needs.md forbids world editing except `Sites.clearArea`; this command sets blocks. Its diagnostic
  half — how many recorded buildings have no hut block — is worth keeping as a `ColonyProblem` kind in
  §2.14, and the count already exists as a loop at `:113-120` [VERIFIED].

### `/minecolonies colony keepbuildings <colony> [on|off]`

* **What it does.** Suspends the colony's sanity cleanup so it stops deleting buildings whose hut block
  is missing. Bare, reports the flag plus how many buildings are currently at risk.
* **How it answers today.** `sendSuccess` for the report (`CommandColonyKeepBuildings.java:60-65`) and
  for the set (`:86-88`) [VERIFIED]. A colony that is not a server `Colony` returns 0 silently
  (`:42-45`, `:79-83`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:31`) [VERIFIED].
* **API form.** None as a setter. The at-risk count belongs in §2.14 as a `ColonyProblem`.
* **Delta.** The at-risk loop (`:50-57`) [VERIFIED] is three lines and is the only place in the tree
  that counts buildings whose recorded block does not match the world.
* **Matching row.** **None.** The setter should not exist: it disables a maintenance pass, which is a
  server operator's decision about a broken save, not a colony-driving verb. The count should exist,
  as one entry in `colony.problems()`.

---

## 4. §2.4 Citizens & Professions

Thirteen commands, the largest group in the tree, and also the group where the split between "a query
the API wants" and "a cheat the API must not have" is sharpest.

### `/minecolonies citizens list <colony> [startpage:int≥1]`

* **What it does.** Prints nine citizens per page: id and name, with a click-to-run link to
  `citizens info`, and the entity's position where the entity is loaded.
* **How it answers today.** `sendSuccess` only: header (`CommandCitizenList.java:79`), name line
  (`:106`), position line (`:112`), pager (`:144`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCColonyOfficerCommand` (`:31`) [VERIFIED].
* **API form.**
  * *Signature.* `List<CitizenSummary> colony.citizens()`
  * *Takes.* Nothing.
  * *Does.* Reads everyone alive in the colony, children included. Mutates nothing.
  * *Returns.* `List<CitizenSummary>`, the §3.5 record, one element per citizen, ordered by citizen
    id ascending; empty only for a colony with no citizens. Fields: `CitizenId id`
    (`mc/api/colony/ICitizen.java:15` `getId()`, paired with the colony id) [VERIFIED];
    `String name` (`:22`) [VERIFIED]; `JobType job` (`mc/api/colony/ICitizenData.java:68` `getJob()`,
    absent sentinel when unemployed) [VERIFIED]; `BuildingId workBuilding` (`:61`) [VERIFIED];
    `BuildingId homeBuilding` (`:46` `getHomeBuilding()`, and this is the accessor to use rather than
    `:531` `getHomePosition()`, which falls back to the nearest tavern) [VERIFIED]; `int jobLevel`
    (a level, via `:155` `getCitizenSkillHandler()`) [VERIFIED]; `double saturation` (0 to
    `MAX_SATURATION`, `mc/api/colony/ICitizen.java:36`) [VERIFIED]; `double happiness` (0–10 —
    reaches a caller today only through the client packet, **no plain server accessor**);
    `boolean child` (`ICitizen.java:43`) [VERIFIED]; `boolean idle`
    (`mc/api/colony/ICitizenData.java:257` `isIdleAtJob()`) [VERIFIED]. Cannot refuse.
* **Delta.** Nothing but a wrapper plus the record. The source is `ICitizenManager#getCitizens`, one
  call (`:62`) [VERIFIED]; everything else in the file is paging and click events. Note that
  `CitizenSummary` carries job, home, saturation, happiness and the idle flag, none of which this
  command prints, so the record is wider than the command.
* **Matching row.** §2.4 `colony.citizens()`, Tier 1.

### `/minecolonies citizens info <colony> <citizenID:int≥1>`

* **What it does.** The fullest per-citizen readout in the tree: id and name, position and health or a
  "not loaded" line, home building with an explicit homeless case, parents with their ids, work
  building, job, the citizen AI's state history and the work AI's state history, the pathing stuck
  level, age against life expectancy when the ageing mechanic is on, and the food handler's quality,
  diversity and last-eaten list.
* **How it answers today.** `sendSuccess` with `broadcastToOps = false` throughout — sixteen distinct
  lines at `CommandCitizenInfo.java:48`, `:52`, `:60`, `:66`, `:71`, `:76`, `:86`, `:89`, `:98`,
  `:106`, `:115`, `:120`, `:125`, `:134-135`, `:139`, `:147`, `:158`, `:167`, `:188` [VERIFIED]. The
  not-found case is a `sendSuccess`, not a `sendFailure` (`:48`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCColonyOfficerCommand` (`:33`) [VERIFIED].
* **API form.** Three methods.

  **1.**
  * *Signature.* `CitizenInfo citizen.info()`
  * *Takes.* Nothing.
  * *Does.* Reads the citizen's identity and standing. Mutates nothing.
  * *Returns.* `CitizenInfo` as §3.5 defines it, **plus two fields §3.5 has not got**:
    `CitizenId motherOrFatherA` and `CitizenId motherOrFatherB`, because the command prints parents
    with their ids and the catalogue's record has nowhere to put them. Fields: `CitizenId id`
    (`mc/api/colony/ICitizen.java:15`); `String name` (`:22`); `Gender gender` (`:29` `isFemale()`,
    a boolean that must be widened to the enum); `boolean child` (`:43`); `JobType job`
    (`mc/api/colony/ICitizenData.java:68`); `BuildingId workBuilding` (`:61`);
    `BuildingId homeBuilding` (`:46`); `BlockPos position` (the loaded entity's, or `:99`
    `getLastPosition()` when it is not loaded — the two are different facts and the record must not
    conflate them); `int jobLevel` and `int experience` (levels and raw xp, via `:155`); and
    `List<SkillLevel> skills`, eleven entries, one per `Skill` [VERIFIED for each cited line].
    Cannot refuse.

  **2.**
  * *Signature.* `CitizenNeeds citizen.needs()`
  * *Takes.* Nothing.
  * *Does.* Reads everything about the citizen that can be unmet. Mutates nothing.
  * *Returns.* `CitizenNeeds` as §3.5 defines it, fifteen fields. `CitizenId id`; `double saturation`
    (`mc/api/colony/ICitizen.java:36`) [VERIFIED]; `int requiredFoodTier` (1–3, follows the **home**
    level, not the workplace); `double health` and `double maxHealth` (half-hearts — reachable only
    through the loaded entity today); `double happiness` (0–10, see `colony.citizens()` above);
    `boolean hasHome` and `boolean hasBed` (`mc/api/colony/ICitizenData.java:46` decides `hasHome`,
    and it must be `getHomeBuilding()` rather than `getHomePosition()`) [VERIFIED]; `boolean sick`,
    `boolean injured`, `boolean mourning`, `int mourningDaysLeft` (days);
    `int homeToWorkDistance` (blocks, the commute the §7.1 complaint is measured against);
    `int daysWithoutHome` and `int nightsWithoutBed` (days and nights — **no source in the tree**,
    nothing is timestamped, which is the same gap that defeats `colony.problems()`). Cannot refuse.

  **3.**
  * *Signature.* `WorkStatus citizen.workStatus()`
  * *Takes.* Nothing.
  * *Does.* Reads what the citizen is doing and why it might not be. Mutates nothing — and this is a
    change of behaviour, not just of shape: the command obtains its activity string by calling
    `setHistoryEnabled(true, 16)` first (`CommandCitizenInfo.java:146`) [VERIFIED], and a query may
    not do that.
  * *Returns.* `WorkStatus` as §3.6 defines it. `CitizenId id`; `WorkState state`, one of the ten
    constants of §3.6, derived from the citizen AI's own state, which is reachable **only through the
    loaded entity**, so an unloaded citizen has no state to report; `String activity`
    (human-readable); `BlockPos position`; `long lastProductiveGameTime` (world ticks — **no source
    in the tree**, see `colony.problems()` in section 14); `List<WorkProblem> problems`, contracted
    under `colony diagnose`. Cannot refuse.
* **Delta.** Considerable, and it is the most valuable single body in the tree for §2.5. Three points.
  (1) The homeless distinction is already made correctly here and nowhere else: `getHomePosition()`
  falls back to the nearest tavern, so the command asks `getHomeBuilding()` instead and says which of
  the two it is (`:79-108`) [VERIFIED] — that is exactly `CitizenNeeds.hasHome`. (2) The activity
  string is obtained by **switching on history recording as a side effect of asking**:
  `setHistoryEnabled(true, 16)` at `:146` [VERIFIED]. A query that mutates the subject cannot be an
  API method as it stands; either history is always on or `WorkStatus.activity` is derived from the
  current state alone. (3) The parent lines have no home in the catalogue's `CitizenInfo` record,
  which has no parent fields — either the record grows two `CitizenId`s or the information is lost.
* **Matching row.** §2.4 `citizen.info()` and `citizen.needs()`, both Tier 1; §2.5
  `citizen.workStatus()`, Tier 1.

### `/minecolonies citizens fill <colony> [children]`

* **What it does.** Spawns citizens until the colony reaches the `maxCitizenPerColony` config value —
  deliberately not `getMaxCitizens()`, so beds do not bind — then hires every one it can into an empty
  job slot, then reports spawned, hired, remaining room, homeless, unemployed and the professions with
  no worker. With `children`, everything spawned is a child.
* **How it answers today.** `sendSuccess` for all six report lines
  (`CommandCitizenFill.java:112`, `:116`, `:122`, `:129`, `:148`, `:162`) and the missing-jobs section
  (`:414`, `:418`, `:424`) [VERIFIED].
* **Player required?** No. `spawnOrCreateCivilian(null, colony.getWorld(), …)` takes no player
  [VERIFIED, the same call shape as `CommandCitizenSpawnNew.java:34`].
* **Permission gate.** `IMCOPCommand` (`:49`) [VERIFIED].
* **API form.** Two methods; the spawning half gets none.

  **1.**
  * *Signature.* `List<ProfessionCoverage> colony.professionCoverage()`
  * *Takes.* Nothing.
  * *Does.* Reads, per profession, how much of it the colony has built and how much is staffed.
    Mutates nothing.
  * *Returns.* `List<ProfessionCoverage>`, the §3.5 record, one element per profession the installed
    version publishes — including professions with nothing built, since an absent profession is the
    answer the caller wants. Ordering: by job id, so two runs compare. Fields: `JobType job`;
    `BuildingType building` (the type that provides the job); `int buildingsBuilt` (a count);
    `int slots` (total job slots across those buildings, from each assignment module's module
    maximum); `int staffed` (assigned citizens across the same). The traversal exists once, in
    `CommandCitizenFill.reportMissingJobs` (`CommandCitizenFill.java:373`) [VERIFIED], and joins
    names into a string rather than returning them. Cannot refuse.

  **2.**
  * *Signature.* `List<CitizenSummary> colony.unemployed()`
  * *Takes.* Nothing.
  * *Does.* Reads the citizens with no job. Mutates nothing.
  * *Returns.* `List<CitizenSummary>` as above, ordered by citizen id, empty when everyone is
    employed. **Children must be excluded**, and the filter is exactly `getJob() == null &&
    !isChild()` (`CommandCitizenFill.java:151-154`) [VERIFIED]. The neighbouring command records what
    happens otherwise: counting a schooled child as both employed and a child subtracts it twice, and
    a colony with sixteen pupils reported "-10 unemployed adults"
    (`CommandColonyDiagnose.java:143-145`) [VERIFIED]. Cannot refuse.
* **Delta.** `reportMissingJobs` (`CommandCitizenFill.java:373`, output at `:414`, `:418`, `:424`)
  [VERIFIED] is the only
  place in the tree that enumerates professions with no staffed building, which is precisely
  `professionCoverage()`; it must return a list of records instead of joining names into a string. The
  homeless and unemployed counts (`:132-163`) [VERIFIED] are two more loops that belong in
  `ColonyCapacity` and `colony.unemployed()`. The hiring pass goes through the buildings' own
  assignment modules, so it is a caller-side loop over `building.hire(...)`, not a call.
* **Matching row.** §2.4 `colony.professionCoverage()`, Tier 2; §2.4 `colony.unemployed()`, Tier 1.
  The spawn-to-cap verb matches **none** and should not be added: §5 non-goal 5 forbids the API
  creating things, and a citizen forced past the bed cap is exactly that.

### `/minecolonies citizens spawnNew <colony>`

* **What it does.** Force-spawns one citizen, bypassing the town hall's move-in setting, and posts
  `CitizenAddedModEvent` with source `COMMANDS`.
* **How it answers today.** One `sendSuccess` with the new citizen's name
  (`CommandCitizenSpawnNew.java:35`) [VERIFIED].
* **Player required?** No (`:34`) [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:23`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** and the API should deliberately not have it. Citizens arrive because
  beds and settings allow them; a call that conjures one makes `colony.capacity()`,
  `PopulationCapReachedEvent` and the whole of §2.1's growth story untestable. The one thing worth
  taking from this file is the event post at `:37` [VERIFIED], which is the firing point
  `CitizenSpawnedEvent` (§2.4, Tier 1) needs.

### `/minecolonies citizens heal <colony>`

* **What it does.** Cures every sick citizen through the disease handler's own `cure()` and sets every
  loaded citizen's health to maximum, counting both.
* **How it answers today.** `sendSuccess` for the config refusal (`CommandCitizenHeal.java:62`), the
  creative-mode refusal (`:69`), the empty colony (`:77`), "nothing to do" (`:109`), the success
  counts (`:116`) and an immunity note (`:120`) [VERIFIED].
* **Player required?** No — but a *player* caller must be in creative: `isPlayer() && !getPlayer()
  .isCreative()` refuses (`:67-71`), and the console passes (`:66` comment) [VERIFIED].
* **Permission gate.** `IMCColonyOfficerCommand` (`:32`), plus `canPlayerUseModifyCitizensCommand` for
  a non-op (`:60-64`), plus creative mode (`:67`) [VERIFIED]. The strictest gate in the tree.
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** and deliberately not. §2.4's `CitizenSickEvent` and §2.8's health fields
  exist so a caller can build a hospital; a call that cures for free removes the reason to.

### `/minecolonies citizens maxstats <colony>`

* **What it does.** Raises every skill of every citizen to `MAX_CITIZEN_LEVEL`, runs the same
  `levelUp` follow-up the experience path runs, sets saturation to `MAX_SATURATION`, refills health
  and invalidates every citizen's cached happiness.
* **How it answers today.** `sendSuccess` for the config refusal (`CommandCitizenMaxStats.java:63`),
  creative refusal (`:70`), empty colony (`:78`), nothing to do (`:100`) and success (`:105`)
  [VERIFIED].
* **Player required?** No; creative for a player caller (`:68`) [VERIFIED].
* **Permission gate.** As `citizens heal` (`:33`, `:61`, `:68`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** deliberately not — same argument as `heal`. `citizen.skills()` (§2.4,
  Tier 2) is the read this file's `ICitizenSkillHandler` access shows is available.

### `/minecolonies citizens modify <colony> <citizenID> saturation (=|+|-) <value:double 0..MAX_SATURATION>`

* **What it does.** Sets, raises or lowers one citizen's saturation. `saturation` is the only
  attribute the builder wires up; `onExecute` itself returns 0 and does nothing
  (`CommandCitizenModify.java:38-41`) [VERIFIED].
* **How it answers today.** `sendSuccess` for the change (`:87`), the config refusal (`:105`), the
  creative refusal (`:121`) and citizen-not-found (`:129`) [VERIFIED].
* **Player required?** No; creative for a player caller (`:111-123`) [VERIFIED].
* **Permission gate.** As `citizens heal` (`:33`, `:103`, `:115`) [VERIFIED].
* **API form.** The read only.
  * *Signature.* `double citizen.saturation()`
  * *Takes.* Nothing.
  * *Does.* Reads one citizen's saturation. Mutates nothing.
  * *Returns.* `double`, from 0 to `ICitizenData.MAX_SATURATION`, the same scale the command's own
    argument is bounded to (`CommandCitizenModify.java:59`) [VERIFIED]. Note that this is **not** the
    catalogue's food-tier scale: the tree's colony-average threshold is `AVERAGE_SATURATION = 10`,
    not the 5 §2.8 assumes. The getter is `mc/api/colony/ICitizen.java:36` [VERIFIED]. Cannot refuse.
* **Delta.** The getter already exists (`citizen.getSaturation()`, used at `:62`) [VERIFIED]; the
  reading half is a wrapper. The writing half gets nothing.
* **Matching row.** §2.8 `citizen.saturation()`, Tier 1, for the read. The three setters match
  **none** and should not be added: feeding citizens is what a dining hall is for, and §2.8 is a
  diagnosis block, not a cheat.

### `/minecolonies citizens reload <colony> <citizenID>`

* **What it does.** Calls `ICitizenData#updateEntityIfNecessary`, which respawns the entity for a
  citizen whose entity went missing.
* **How it answers today.** `sendSuccess` for not-found (`CommandCitizenReload.java:36`) and for
  success (`:41`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCColonyOfficerCommand` (`:21`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None.** A verb for repairing an entity that should not have gone missing is a
  developer's tool. The one API-relevant fact in the file is that `citizenData.getEntity()` can be
  empty — which every §2.4 and §2.5 record has to represent honestly rather than by omission.

### `/minecolonies citizens kill <colony> <citizenID>`

* **What it does.** Prints the citizen's id, name and position and then kills them with the `CONSOLE`
  damage source.
* **How it answers today.** `sendSuccess` for the config refusal (`CommandCitizenKill.java:41`),
  not-found (`:49`), not-loaded (`:57`) and three information lines before the kill (`:61`, `:63`,
  `:64-65`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCColonyOfficerCommand` (`:27`), plus `canPlayerUseKillCitizensCommand` for a
  non-op (`:39-43`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** deliberately not. §5 non-goal 6 rules out entity control; killing a
  citizen is the most destructive form of it. `CitizenDiedEvent` (§2.4, Tier 1) is the API's interest
  in citizen death, and this file shows the moment it fires.

### `/minecolonies citizens teleport <colony> <citizenID> <location:vec3>`

* **What it does.** Snaps the citizen entity to a position and stops its navigation, only when the
  source's level is the citizen's level.
* **How it answers today.** `sendSuccess` for not-found (`CommandCitizenTeleport.java:42`) and
  not-loaded (`:50`); **nothing at all on success** (`:58-64`) [VERIFIED].
* **Player required?** No, but the source's dimension is used as an implicit filter
  (`context.getSource().getLevel()` at `:58`) [VERIFIED], which silently makes the command a no-op
  from a console attached to another dimension.
* **Permission gate.** `IMCColonyOfficerCommand` (`:26`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** deliberately not. §5 non-goal 6 again. `building.recallWorkers()` (§2.4,
  Tier 1) is the only teleport the design allows, and it is a switch the game itself offers.

### `/minecolonies citizens walk stop` and `/minecolonies citizens walk <colony> <citizenID> <location:vec3>`

* **What it does.** Injects a one-time AI transition that makes the citizen walk to a position, giving
  up after three minutes, and remembers the target in a static map keyed by the caller's UUID so a
  later `walk stop` can cancel it. A citizen with no job is moved with a plain navigation call
  instead.
* **How it answers today.** `sendSuccess` for not-found (`CommandCitizenTriggerWalkTo.java:56`) and
  not-loaded (`:64`); **nothing on success**, and nothing at all from `stop` (`:139-144`) [VERIFIED].
* **Player required?** No. The source entity is used only as a map key and is null-tolerant
  (`:76`, `:141`) [VERIFIED] — though the fallback `UUID.fromString("unknown")` throws
  `IllegalArgumentException`, so the console path is broken.
* **Permission gate.** `IMCColonyOfficerCommand` (`:35`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** deliberately not. §5 non-goal 6 is explicit that the API does not move
  citizens block by block.

### `/minecolonies colony rehouse <colony> [preview]`

* **What it does.** Repeatedly moves every citizen into the house nearest their work, until a pass
  moves nobody, respecting locked residences and only ever moving a citizen strictly nearer. Homeless
  citizens are housed. `preview` runs one pass and changes nothing.
* **How it answers today.** `sendSuccess` through the same `emit`/`report` pair as `repairall`:
  per-move lines (`CommandColonyRehouse.java:384`), an "and N more" line (`:391`) and the summary
  (`:418`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCColonyOfficerCommand` (`:52`) [VERIFIED].
* **API form.** Two methods, one of them new.

  **1.**
  * *Signature.* `BuildingId colony.bestHomeFor(CitizenId citizen)` — **new**; §2.4 has no row.
  * *Takes.*
    * `CitizenId citizen` — the citizen to house. Required. A citizen that is not in this colony
      throws `StaleHandleException` per §4.
  * *Does.* Works out which residence the citizen should live in, without moving anybody. Mutates
    nothing. It applies the two rules the command already applies: a residence on `HiringMode.LOCKED`
    is never emptied, and a citizen is only ever offered a **strictly** nearer house, so two houses at
    equal distance cannot trade a citizen back and forth (`CommandColonyRehouse.java:159`) [VERIFIED].
  * *Returns.* `BuildingId` — the residence, or the absent sentinel `BuildingId(colony,
    BlockPos(0,0,0))` when the citizen should stay where it is or no bed is free. The two inputs both
    exist: `IRegisteredStructureManager#getHouseWithSpareBed(ICitizenData)`
    (`mc/api/colony/managers/interfaces/IRegisteredStructureManager.java:239`) and
    `#getHousingAnchor(ICitizenData)` (`:248`) [VERIFIED]. Cannot refuse; it is a query.

  **2.**
  * *Signature.* `CommandOutcome citizen.assignHome(BuildingId residence)`
  * *Takes.*
    * `BuildingId residence` — the residence or tavern to move the citizen into. Required. A building
      that is not a living building, or is in another colony, is rejected.
  * *Does.* Un-assigns the citizen from its current home and assigns it to `residence`, taking a bed
    there. **Side effect:** freeing the old bed can make a better house available to somebody else,
    which is why the command loops until a pass moves nobody (`CommandColonyRehouse.java:115-146`)
    [VERIFIED]; a single call does not converge and the API does not loop on the caller's behalf.
  * *Returns.* `CommandOutcome` (§3.1). Refusal codes: `REFUSED_STATE` (no free bed there),
    `REFUSED_RULE` (not a residence or tavern), `REFUSED_UNKNOWN_TARGET` (no such building),
    `NO_EFFECT` (already living there).
* **Delta.** Small and unusually clean. `findMove` (`CommandColonyRehouse.java:159`) [VERIFIED] is
  already `private static` and pure: it takes the building manager and the citizen, consults
  `IRegisteredStructureManager#getHouseWithSpareBed`, refuses to move out of a `LOCKED` house and
  compares squared distances to `getHousingAnchor`. Making it a method on the colony handle is a
  signature change and nothing else. `apply(move)` (`:281`) [VERIFIED] is the write, and that is `citizen.assignHome`.
  The convergence loop is a caller's concern, not an API call.
* **Matching row.** §2.4 `citizen.assignHome(BuildingId)`, Tier 1. `bestHomeFor` matches **none** and
  is worth adding: without it a caller has to reimplement the anchor-distance rule that this file and
  `getHouseWithSpareBed` already agree on.

### `/minecolonies colony growChildren <colony>`

* **What it does.** Calls `setIsChild(false)` on every child in the colony, so they take beds and jobs
  at once, then marks the colony dirty.
* **How it answers today.** One `sendSuccess` with the count
  (`CommandColonyGrowChildren.java:55-56`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:30`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** deliberately not. Children grow up by being ticked; a call that skips
  the timer is a testing aid. The child flag is already readable through `CitizenSummary.child` and
  `CitizenInfo.child` (§3.5), which is what the API needs from this area.

### §2.5's place in this block

No command implements §2.5's `citizen.problems()` or `building.problems()` directly. The two closest
bodies are `citizens info`, above, and `colony diagnose`, in §14 below; both are named again in the
work list at the end.

---

## 5. §2.5 Work & Idleness

### `/minecolonies colony workoverride <colony> [<switch> [on|off]]`

* **What it does.** Reads and throws the colony's "keep working anyway" switches — one literal per
  constant of the `WorkOverride` enum, built off the enum so a new switch needs no change here. Each
  names one thing that ordinarily stops a citizen working and tells it to carry on. Bare, it reports
  every switch; with a switch and no `on`/`off`, it reports that one.
* **How it answers today.** `sendSuccess` for the header (`CommandColonyWorkOverride.java:34`), one
  line per switch (`:65-68`) and the set confirmation (`:89`) [VERIFIED]. A colony that is not a
  server `Colony` returns 0 silently (`:82-86`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:23`) [VERIFIED].
* **API form.** Two methods, both new; §2.5 has no row for either.

  **1.**
  * *Signature.* `List<WorkOverrideState> colony.workOverrides()`
  * *Takes.* Nothing.
  * *Does.* Reads every "keep working anyway" switch and whether it is thrown. Mutates nothing.
  * *Returns.* `List<WorkOverrideState>` — **a new record**, §3 has none: `WorkOverrideState(
    WorkOverrideKind kind, String displayName, boolean on)`. One element per constant of the tree's
    `WorkOverride` enum (`mc/core/colony/workoverrides/WorkOverride.java:34`), in declaration order,
    never empty. `kind` is the enum widened to the API's own, whose serialized names already exist
    (`:80` `getSerializedName`); `displayName` is human-readable (`:90` `getLabelKey`); `on` is
    `:104` `isOn(IColony)`, already a predicate over the colony [VERIFIED for each]. Cannot refuse.

  **2.**
  * *Signature.* `CommandOutcome colony.setWorkOverride(WorkOverrideKind kind, boolean on)`
  * *Takes.*
    * `WorkOverrideKind kind` — which switch. Required. An unknown constant throws
      `IllegalArgumentException` per §4.
    * `boolean on` — the value to set. Required; there is no toggle form, deliberately.
  * *Does.* Throws one switch, so citizens keep working through the condition it names. Saved with
    the colony. **This is an operator's testing switch**: with it thrown, some of §2.5's nine problem
    kinds will not appear even though their cause is present, so it must carry the same gate as
    `colony.setFreeMode`.
  * *Returns.* `CommandOutcome`. Refusal codes: `REFUSED_PERMISSION` (caller is not an operator),
    `NO_EFFECT` (already at that value). The write is `Colony#setWorkOverride`
    (`mc/core/colony/Colony.java:2084`) [VERIFIED].
* **Delta.** Nothing but a wrapper on the read side — `WorkOverride#isOn(colony)` is already a
  predicate over the colony (`:68`) [VERIFIED] and the enum already carries a serialized name and a
  label key. The write is `Colony#setWorkOverride` (`:88`) [VERIFIED], one call.
* **Matching row.** **None,** and this is the one debug switch in the tree the API arguably should
  gain — as a **read**, not a write. §2.5's `ProblemKind` list names nine causes of a worker standing
  still; a colony with an override thrown will not exhibit some of them, and a caller diagnosing
  "why is nothing happening" that cannot see the overrides will draw the wrong conclusion. The write
  belongs with `colony.setFreeMode` (§2.13, Tier 3, operator-only) and should be gated the same way.

---

## 6. §2.6 Requests & Logistics

### `/minecolonies colony requestsystem-reset <colony>`

* **What it does.** Calls `IRequestManager#reset()` on one colony, dropping every open request.
* **How it answers today.** `sendSuccess` for the config refusal (`CommandRSReset.java:29`) and for
  success (`:34`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCCommand` — the weakest gate, any player — plus `canPlayerUseResetCommand`
  for a non-op (`:15`, `:27-31`) [VERIFIED].
* **API form.**
  * *Signature.* `CommandOutcome colony.resetRequestSystem()`
  * *Takes.* Nothing.
  * *Does.* Rebuilds the colony's request manager from empty. **Destructive, and the name understates
    it:** every open request is dropped, including ones a courier was part-way through, and every
    citizen waiting on one starts again. §2.6 calls it the last resort of the escalation list.
  * *Returns.* `CommandOutcome`. The catalogue's version returns nothing more; this document proposes
    the message carry the number of requests dropped, since the caller has no other way to learn what
    it destroyed. Refusal codes: `REFUSED_PERMISSION` only. The verb is
    `IRequestManager#reset()` (`mc/api/colony/requestsystem/manager/IRequestManager.java:205`),
    reached through `mc/api/colony/IColony.java:153` `getRequestManager()` [VERIFIED].
* **Delta.** Nothing but a wrapper. The verb is one line (`:33`) [VERIFIED] and needs no player. The
  only addition is the outcome: the command cannot fail and says so unconditionally, whereas the API
  should at least report how many requests were dropped.
* **Matching row.** §2.6 `colony.resetRequestSystem()`, Tier 2.

### `/minecolonies colony requestsystem-reset-all`

* **What it does.** The same reset over `IColonyManager.getAllColonies()`.
* **How it answers today.** One `sendSuccess` (`CommandRSResetAll.java:26`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:12`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** and the API should not have it. It is `colony.resetRequestSystem()` in a
  loop, and the API deliberately has no server-wide mutation: every command in §2 hangs off a colony
  handle. A caller with `Colonies.list()` can write the loop, and should have to.

---

## 7. §2.7 Storage & Resources

### `/minecolonies colony warehousestock <colony>`

* **What it does.** Reads the `WarehouseIdleTrackerModule` of every warehouse and reports, per item
  kind, the count held, how many days it has sat unused, how fast it is being drawn down and how much
  went in the last window; plus rack occupancy per warehouse and for the colony. Chat gets the fifteen
  worst rows, the complete table goes to a CSV file under the world save.
* **How it answers today.** `sendSuccess` for no-warehouse (`CommandColonyWarehouseStock.java:78`),
  no-data (`:124`), the fill lines (`:126`, `:143`, `:151`), the header (`:135`), fifteen item rows
  (`:166`), the "and N more" line (`:173`) and the file confirmation (`:280`); `sendFailure` once,
  when the file cannot be written (`:288`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:52`) [VERIFIED].
* **API form.** Three methods, two of them new.

  **1.**
  * *Signature.* `List<StockEntry> colony.stock()`
  * *Takes.* Nothing.
  * *Does.* Reads what the colony holds, per item, split by where it is. Mutates nothing. **This
    command does not implement it**: it reads warehouses only, so the traversal of other buildings
    and of citizen inventories is still absent.
  * *Returns.* `List<StockEntry>`, the §3.8 record, one element per distinct item, ordered by item
    id; empty for a colony holding nothing. Fields: `ItemSpec item`; `int inWarehouses` (a count of
    items, not stacks — the warehouse figure is available as
    `WarehouseIdleTrackerModule#getItemCount`,
    `mc/core/colony/buildings/modules/WarehouseIdleTrackerModule.java:319`) [VERIFIED];
    `int inBuildings` and `int inCitizenInventories` (**no source in the tree** for either as a
    colony-wide figure); `int total`. Any total is a **lower bound**: unloaded chunks are skipped
    silently. Cannot refuse.

  **2.**
  * *Signature.* `List<WarehouseFill> colony.warehouseFill()` — **new**, and a new record.
  * *Takes.* Nothing.
  * *Does.* Reads each warehouse's rack occupancy from the last sample the module took. Mutates
    nothing and measures nothing: the sampling is done once per colony tick elsewhere.
  * *Returns.* `List<WarehouseFill>` — **new record**, §3 has none: `WarehouseFill(BuildingId
    warehouse, int usedSlots, int totalSlots, long itemCount, double stackEquivalents, boolean
    sampled, long lastSampleGameTime)`. One element per warehouse, ordered by position; empty when
    the colony has none. `usedSlots` (`WarehouseIdleTrackerModule.java:303`), `totalSlots` (`:311`),
    `itemCount` (`:319`), `stackEquivalents` (`:327`), `lastSampleGameTime` (`:361`
    `getLastSampleTick`) [VERIFIED]. **`sampled` is not decoration:** a warehouse whose chunks have
    never loaded since start-up returns a negative sample tick and has no occupancy at all rather
    than zero occupancy, and the command says so in words rather than printing zeroes
    (`CommandColonyWarehouseStock.java:100-106`) [VERIFIED]. Cannot refuse.

  **3.**
  * *Signature.* `List<StockAge> colony.stockAge()` — **new**, and a new record.
  * *Takes.* Nothing.
  * *Does.* Reads, per item, how long it has sat unused and how fast it is being drawn down. Mutates
    nothing.
  * *Returns.* `List<StockAge>` — **new record**, §3 has none: `StockAge(ItemSpec item, int count,
    double idleDays, double takenPerDay, long takenInWindow, int windowDays, long totalTaken,
    boolean everTaken, long firstSeenGameTime)`. Ordered **worst first**, meaning longest idle, which
    is the order the aggregation already produces. Every field is already a field or a method of
    `WarehouseIdleTrackerModule.Aggregate` (`:602`): `item` (`:607`), `count` (`:612`), `firstSeen`
    (`:617`), `lastTaken` (`:623`), `everTaken` (`:628`), `takenInWindow` (`:633`), `totalTaken`
    (`:638`), `getIdleDays(now)` (`:682`), `getTakenPerDay()` (`:699`), and the aggregation across
    warehouses is `aggregate(histories, now)` (`:712`) [VERIFIED]. `windowDays` is the constant
    `WINDOW_DAYS = 7` (`:69`) [VERIFIED], carried in the record so the caller does not hard-code it.
    Cannot refuse.
* **Delta.** Two of the three are close. The occupancy figures are already fields on the module
  (`getUsedSlots`, `getTotalSlots`, `getItemCount`, `getStackEquivalents`, read at `:111-115`)
  [VERIFIED], and the per-item aggregation across warehouses is already a static method returning
  records, `WarehouseIdleTrackerModule.aggregate(histories, now)` (`:119`) [VERIFIED] — a wrapper.
  What this command does **not** give is `colony.stock()` as the catalogue defines it: it reads
  warehouses only, never other buildings or citizen inventories, so it is a warehouse report and not a
  colony one. api-inventory's judgement that the colony-wide traversal does not exist stands.
  The CSV writing is a command affordance and has no API form; the "not sampled yet" case (`:100-106`)
  [VERIFIED] is a real state a `StockEntry` has to be able to express, since an unloaded warehouse has
  no occupancy rather than zero occupancy.
* **Matching row.** §2.7 `colony.stock()`, Tier 1, partially. `stockAge` and `warehouseFill` match
  **none** and are both worth adding: §2.7 has no notion of *time* at all, and "this item has sat for
  forty days" is the answer to a question — is my economy actually consuming what it produces — that
  no call in the catalogue can ask.

### `/minecolonies colony teachRecipes <colony>`

* **What it does.** For every crafting module of every building, checks datapack recipes, then teaches
  every vanilla recipe the module `canLearn`, up to the module's recipe limit, skipping the
  Architect's Cutter because the analyzer's cutter recipes are display approximations.
* **How it answers today.** `sendSuccess` for the per-building line
  (`CommandColonyTeachRecipes.java:173`), the no-crafters case (`:181`), the summary (`:191`), the
  "some were full" note (`:196`) and the cutter note (`:201`) [VERIFIED].
* **Player required?** No, though it reads the server's recipe manager
  (`context.getSource().getServer().getRecipeManager()`, `:64`) [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:44`) [VERIFIED].
* **API form.** The read only.
  * *Signature.* `List<CraftingOption> colony.craftableBy(ItemSpec item)`
  * *Takes.*
    * `ItemSpec item` — the item the caller wants made. Required. An unknown item id is not an error
      and yields an empty list.
  * *Does.* Reads which of the colony's crafting buildings could make this item, whether the building
    exists and whether anyone is in it. Mutates nothing — and that is a real constraint here, because
    the command's own path calls `module.checkForWorkerSpecificRecipes()` first
    (`CommandColonyTeachRecipes.java:92`) [VERIFIED], which a query must not do.
  * *Returns.* `List<CraftingOption>`, the §3.8 record, one element per profession that could make
    the item, ordered staffed-first so the caller's first choice is the one that will actually act;
    empty when nothing in the colony can. Fields: `ItemSpec item` (echoed); `JobType job`;
    `BuildingType building`; `BuildingId builtAt` (absent sentinel when the type is not built);
    `boolean staffed`; `boolean unlocked`; `List<ItemAmount> ingredients`. The two inputs exist:
    `RecipeAnalyzer.buildVanillaRecipesMap(recipeManager, world)`
    (`CommandColonyTeachRecipes.java:64`) and `ICraftingBuildingModule#canLearn(CraftingType)`
    (`:96`) [VERIFIED]. Cannot refuse.
* **Delta.** The teaching verb gets nothing. But the *lookup* it performs is exactly what
  `craftableBy` needs and does not exist anywhere else: `RecipeAnalyzer.buildVanillaRecipesMap` plus
  `ICraftingBuildingModule#canLearn` per crafting type (`:64`, `:96`) [VERIFIED] answers "which
  building could make this", and the module's assigned worker answers "and is it staffed". Inverting
  the loop from building-major to item-major is the delta.
* **Matching row.** §2.7 `colony.craftableBy(ItemSpec)`, Tier 2, for the lookup. The teaching verb
  matches **none** and should not be added: a crafter learns recipes because a player taught it
  through the hut GUI, and granting them all at once is a testing shortcut.

---

## 8. §2.8 Food & Happiness

### `/minecolonies colony fieldseeds <colony> [<field:blockpos> (add|set|remove <seed:id> | clear)]`

* **What it does.** With no position, prints every farm field the colony owns and what it is sown
  with, one line each. With a position and a verb, edits that field's seed list, refusing an item that
  is not plantable and refusing to exceed `FarmField.MAX_SEEDS`.
* **How it answers today.** `sendSuccess` for the no-fields case
  (`CommandColonyFieldSeeds.java:75`), the no-such-field case (`:110`), a bad item id (`:124`), a
  non-plantable seed (`:139`), a full field (`:154`) and the per-field state line (`:198`) [VERIFIED].
  Every refusal is a `sendSuccess`, not a `sendFailure`.
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:50`) [VERIFIED].
* **API form.** Two methods.

  **1.**
  * *Signature.* `List<FieldInfo> farm.fields()`
  * *Takes.* Nothing; the farm is the handle.
  * *Does.* Reads the fields attached to this farm. Mutates nothing.
  * *Returns.* `List<FieldInfo>`, the §3.9 record, **extended in one place**: §3.9 gives
    `ItemSpec seed`, and this tree's field holds a **list** of up to `FarmField.MAX_SEEDS = 5`
    (`mc/core/colony/buildingextensions/FarmField.java:74`, `:248` `getSeeds()`) [VERIFIED], so the
    field must be `List<ItemSpec> seeds`. One element per field, ordered by scarecrow position;
    empty when the farm has none. Fields: `BlockPos scarecrow`; `BuildingId farm`;
    `List<ItemSpec> seeds`; `boolean hydrated`; `boolean assigned`; `FieldState state`, one of the
    seven constants of §3.9. The listing traversal exists but is **colony-wide, not per-farm**, in
    `CommandColonyFieldSeeds.java:71-72` [VERIFIED]; hydration and `FieldState` have **no source**
    there. Cannot refuse.

  **2.**
  * *Signature.* `CommandOutcome field.setSeeds(List<ItemSpec> seeds)` — the arity correction of
    §2.8's `setSeed(ItemSpec)`; see the objection in section 19.
  * *Takes.*
    * `List<ItemSpec> seeds` — the complete seed list to set, replacing whatever is there. Required;
      an empty list clears the field. More than `FarmField.MAX_SEEDS` entries is rejected rather than
      truncated. An item the farmer cannot plant is rejected.
  * *Does.* Replaces the field's seed list, which is what the scarecrow window would have done.
    Marks the field dirty so the change survives a save.
  * *Returns.* `CommandOutcome`. Refusal codes: `REFUSED_RULE` (an item that is not a `CropBlock`,
    `StemBlock` or `MinecoloniesCropBlock` item — the predicate is already static at
    `CommandColonyFieldSeeds.java:212`) [VERIFIED]; `REFUSED_STATE` (more seeds than
    `MAX_SEEDS`); `REFUSED_UNKNOWN_TARGET` (no field at this position). Today every one of these is
    reported as a `sendSuccess`, not a failure (`:124`, `:139`, `:154`) [VERIFIED].
* **Delta.** Modest and well-shaped. The listing loop reads the colony's building extensions filtered
  to `farmField` (`:71-72`) [VERIFIED], which is the traversal `farm.fields()` needs, though it is
  colony-wide here and per-farm in the catalogue. The plantability test is already a static predicate,
  `isPlantable(ItemStack)` (`:212`) [VERIFIED], restated from the farmer AI's own private method — which means the API can reuse it
  but should really share it with the AI rather than keep a third copy. The `FieldInfo` record wants
  hydration and state, which this command does not read.
* **Matching row.** §2.8 `farm.fields()`, Tier 2; §2.8 `field.setSeed(ItemSpec)`, Tier 2. Note that
  the catalogue's `setSeed` takes one seed and this field holds a list, so the row's signature is
  wrong for this tree and should become `setSeeds(List<ItemSpec>)`.

---

## 9. §2.9 Research

### `/minecolonies colony research completeall <colony>`

* **What it does.** Walks every branch of the global research tree in dependency order, completing
  each node through the model's own completion route with no cost and no university, picking the
  first child at every exclusive fork and collecting the branches that were therefore skipped; then
  recomputes every effect from the completed list and reapplies them to every citizen.
* **How it answers today.** `sendSuccess` for the count (`CommandColonyResearch.java:87`), the
  exclusion header (`:92`) and one line per excluded research (`:97`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:35`) [VERIFIED].
* **API form.** The exclusion analysis only.
  * *Signature.* `List<ExclusiveChoice> research.exclusiveChoices()`
  * *Takes.* Nothing.
  * *Does.* Walks the global research tree looking for nodes that allow only one of their children to
    be taken, and reports each fork with what taking each option would cost. Mutates nothing — which
    is the whole delta, since today the walk is entangled with a call that completes each node.
  * *Returns.* `List<ExclusiveChoice>`, the §3.10 record, one element per fork, ordered by branch
    then by the node's own sort order; empty only if the installed datapacks define no exclusive
    node. Fields: `String branch` (`mc/api/research/IGlobalResearch.java:90` `getBranch()`);
    `boolean decided` (whether the colony has already taken one side); `ResearchId chosen` (absent
    sentinel while undecided); `List<ExclusiveOption> options`, each
    `ExclusiveOption(ResearchId id, String displayName, int subtreeSize)` where `subtreeSize` is
    **how many nodes become unreachable if a different option is taken** — a count, and the one
    number in this record that has to be computed rather than read. All four inputs exist:
    `hasOnlyChild()` (`:139`), `getChildren()` (`:185`), `getSortOrder()` (`:104`) and `getId()`
    (`:75`) [VERIFIED], and the recursive collection of a losing subtree is already written and
    already non-mutating (`CommandColonyResearch.java:188-200`) [VERIFIED]. Cannot refuse.
* **Delta.** The completion verb gets nothing. The exclusion walk is the interesting half:
  `completeSubTree` detects `research.hasOnlyChild()` with more than one child and collects the losing
  subtree via `collectSubTree` (`:130-134`, `:188-200`) [VERIFIED]. §0 of api-needs.md calls
  `exclusiveChoices()` "the most consequential single call in the block", and this is the only code in
  the tree that enumerates the forks and counts what each option costs. Turning it into a
  non-mutating query means separating the traversal from `complete(...)`, which is a clean split
  because `collectSubTree` already does not mutate.
* **Matching row.** §2.9 `research.exclusiveChoices()`, Tier 2, for the analysis. The complete-all
  verb matches **none** and should not be added: §2.9 exists because research is the long pole of the
  game, and a call that skips 587 hours of it makes the block pointless.

---

## 10. §2.10 Military & Raids

### `/minecolonies colony raid <colony|all> (now|tonight) [ … ]`

The busiest command in the tree. After the colony and the time word, the builder offers five further
shapes (`CommandRaid.java:376-419`) [VERIFIED]:

| shape | arguments | executor |
|---|---|---|
| bare | — | `onExecute` `:423` |
| by type | `<raidtype:resourcekey> <allowships:bool> [<raidamount:int≥1> [<raidlocation:blockpos>]]` | `:94`, `:109`, `:125` |
| by size | `size <raidamount:int≥1> [<raidstrength:double 0.1..10>]` | `:142`, `:154` |
| by strength | `strength <raidstrength:double 0.1..10>` | `:172` |
| from a territory | `territory` | `:191` |

and three verbs that replace the time word entirely: `where` (`:410`), `tp` (`:411`) and `stop`
(`:412`) [VERIFIED].

* **What it does.** `now` and `tonight` build an `IRaiderManager.RaidSettings` and either call
  `raiderEvent(settings.withImmediateStart())` or `setRaidNextNight(settings)` (`:459`, `:492`)
  [VERIFIED]. `territory` first finds a loaded spawn point inside the nearest hostile territory within
  500 blocks and passes it as an explicit location (`:191-227`) [VERIFIED]. `where` reports, per
  active raid, how many raiders are alive and how many are loaded, plus the nearest one's bearing.
  `stop` sets every active raid to `DONE`, runs the event manager's finishing pass immediately and
  sweeps 500 blocks for raiders that outlived their event. `tp` puts the caller next to the nearest
  raider, or at the spawn point if nothing is loaded.
* **How it answers today.** `sendSuccess` for the success cases (`:209`, `:217`, `:467`, `:493`,
  `:513`, `:527`, `:533`, `:543`, `:549`, `:578`, `:599`, `:609`, `:629`, `:660`) and `sendFailure` for
  the refusals (`:200`, `:225`, `:474`, `:569`) [VERIFIED]. The refusal at `:474` carries a typed
  `RaidSpawnResult` value formatted into the message — the one place in the tree where a machine
  readable refusal already exists and is thrown away as text.
* **Player required?** Only for `tp`: `getEntity() instanceof ServerPlayer` at `:567-571` [VERIFIED],
  and it needs a real client because the point is to move the caller. `now`, `tonight`, `territory`,
  `where` and `stop` need no player at all; `territory` uses the source's level for the dimension.
* **Permission gate.** `IMCOPCommand` (`:55`) [VERIFIED]. No config flag.
* **API form.** Four methods.

  **1.**
  * *Signature.* `CommandOutcome colony.triggerRaid(RaiderType type, int size, double strength,
    WorldPos where, RaidTiming timing)` — §2.10's row is `triggerRaid(RaiderType, int)`; the other
    three parameters are added because the command has proved each of them useful.
  * *Takes.*
    * `RaiderType type` — which horde. Optional; `null` lets the colony pick as it normally would,
      which is the bare form of the command.
    * `int size` — how many raiders. Optional; `-1` means "as the colony would have decided". Zero or
      negative other than `-1` throws `IllegalArgumentException`.
    * `double strength` — a difficulty multiplier. Optional; `1.0` is the strength the colony would
      have faced anyway. Bounded 0.1 to 10.0, the bounds the command's own argument declares
      (`CommandRaid.java:64-65`) [VERIFIED].
    * `WorldPos where` — an explicit spawn point. Optional; absent lets the ordinary spawn search run.
      **A position in an unloaded chunk must be rejected**, not accepted: it produces a raid bar with
      no raiders under it, which is why the command checks and refuses (`:200`) [VERIFIED].
    * `RaidTiming timing` — `NOW` or `NEXT_NIGHT`. Required.
  * *Does.* With `NOW`, spawns the raid immediately; with `NEXT_NIGHT`, records it so the colony
    raids at the next nightfall. **Side effect:** raiders are real entities in the world from the
    moment `NOW` returns, and citizens drop their work and run home, which is §2.5's
    `RAID_IN_PROGRESS`.
  * *Returns.* `CommandOutcome`. The refusal codes map straight off the existing
    `IRaiderManager.RaidSpawnResult` enum (`mc/api/colony/managers/interfaces/IRaiderManager.java:18`)
    [VERIFIED], which `raiderEvent` already returns (`:74`) [VERIFIED]: `REFUSED_STATE` (a raid is
    already running), `REFUSED_RULE` (raids disabled for this colony), `REFUSED_PERMISSION`,
    `REFUSED_UNKNOWN_TARGET` (nowhere to spawn). Today that typed value is formatted into a chat
    string and discarded (`CommandRaid.java:474`) [VERIFIED].

  **2.**
  * *Signature.* `RaidStatus colony.currentRaid()`
  * *Takes.* Nothing.
  * *Does.* Reads whether a raid is running and how it is going. Mutates nothing.
  * *Returns.* `RaidStatus` as §3.11 defines it, **plus one field it has not got**: `int
    raidersLoaded`. `boolean active`; `RaiderType type`; `String direction` (human-readable);
    `int raidersRemaining` (a count); `int raidersKilled`; `int citizensLost`;
    `long startedGameTime` (world ticks); `boolean spiesActive`. The extra field is not decoration —
    a raid can have raiders that exist in the save but are in unloaded chunks, and a caller told only
    "twelve remaining" will hunt for eight that are not in the world; the command reports the two
    numbers separately for exactly that reason (`CommandRaid.java:527`) [VERIFIED]. Cannot refuse.

  **3.**
  * *Signature.* `List<RaiderPosition> raid.remainingRaiders()`
  * *Takes.* Nothing.
  * *Does.* Reads where the surviving raiders of this raid are. Mutates nothing. **Not gated on
    spies** — see the objection in section 19.
  * *Returns.* `List<RaiderPosition>`, the §3.11 record, one element per **loaded** raider, ordered
    nearest to the colony centre first; empty when none is loaded, which is a different fact from the
    raid being over and the caller must read `currentRaid().raidersRemaining` to tell them apart.
    Fields: `int raiderId`; `RaiderType type`; `BlockPos position`; `double health` (half-hearts).
    The source is the event's own entity list,
    `mc/api/colony/colonyEvents/IColonyEntitySpawnEvent.java:19` `getEntities()` [VERIFIED]. Cannot
    refuse.

  **4.**
  * *Signature.* `CommandOutcome colony.endRaid()` — **new**; §2.10 has no row.
  * *Takes.* Nothing.
  * *Does.* Ends every raid running against this colony. **Three side effects a caller would not
    predict from the name**, all of them necessary and all of them already in the command: every
    active event is set to `DONE`; the event manager's finishing pass is run immediately rather than
    up to 500 ticks later, which is what actually discards the raiders and puts back whatever the
    raid built; and the area within 500 blocks of the centre is swept for raiders that outlived their
    event and would otherwise never be removed (`CommandRaid.java:622-661`) [VERIFIED].
  * *Returns.* `CommandOutcome`, whose message should carry how many raiders were removed, since
    nothing else reports it. Refusal codes: `NO_EFFECT` (no raid running),
    `REFUSED_PERMISSION`.
* **Delta.** `triggerRaid` is nothing but a wrapper with a widened signature: `raiderEvent` already
  takes a settings record and already returns a typed result, so `RaidSpawnResult` maps straight onto
  `OutcomeCode` (`:459-475`) [VERIFIED]. The catalogue's signature takes only type and size and needs
  the other three parameters this command has proved useful. `remainingRaiders` is `getActiveRaids`
  plus `raid.getEntities()` (`:671`) [VERIFIED], and it
  should **not** be gated on spies as the catalogue proposes: the server knows where the raiders are
  whether or not spies were hired, and the catalogue's gate is a GUI concession. The loaded/alive
  split this command reports (`:527`) is real and belongs in `RaidStatus`: a raid can have raiders that
  exist in the save but not in the world, and a caller told only "twelve remaining" will hunt for eight
  that are not there.
* **Matching row.** §2.10 `colony.triggerRaid(RaiderType, int)`, Tier 3; §2.10
  `raid.remainingRaiders()`, Tier 3; §2.10 `colony.currentRaid()`, Tier 2. `stop` matches **none** and
  is worth adding as `colony.endRaid()` — §6.3 of progression.md and the catalogue both name "a raid
  that will not end" as a real state, and hiring spies is the only remedy the catalogue offers.

### `/minecolonies colony raidhistory <colony>`

* **What it does.** Prints every recorded raid with how many hours ago it happened.
* **How it answers today.** `sendSuccess` per raid (`CommandColonyRaidsInfo.java:42`) and for the
  config refusal (`:33`) [VERIFIED]. The line is `hoursSince + " hours ago:" + history` — the record's
  `toString`, unformatted.
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:19`), plus `canPlayerUseShowColonyInfoCommand` for a non-op
  (`:31-35`) [VERIFIED].
* **API form.**
  * *Signature.* `List<RaidRecord> colony.raidHistory(int maxEntries)`
  * *Takes.*
    * `int maxEntries` — how many of the most recent raids to return. Required, at least 1; below 1
      throws `IllegalArgumentException` per §4. There is no cursor and no paging.
  * *Does.* Reads what the colony recorded about past raids. Mutates nothing.
  * *Returns.* `List<RaidRecord>`, the §3.11 record, **most recent first**, at most `maxEntries`
    long, empty for a colony that has never been raided. Fields and their sources, all on the
    existing `RaidManager.RaidHistory` (`mc/core/colony/events/raid/RaidManager.java:1293`)
    [VERIFIED]: `int day` (the colony day — derived from `public final long raidTime` at `:1322`,
    which is world ticks, so the record's `day` is a conversion and not a read); `RaiderType type`
    (from the spawn data at `:1332`); `int raiders` (`public final int raiderAmount`, `:1312`);
    `int citizensLost` (`public int lostCitizens`, `:1307`); `boolean noLosses` (that field against
    zero); `long durationTicks` (**no source in the tree**; the record holds a start time and a dead
    count, `public int deadRaiders` at `:1317`, but no end time) [VERIFIED for each cited field].
    A `double difficulty` field (`:1327`) exists and §3.11's record has nowhere to put it; it should
    be added, since §6.2 says the difficulty formula is not public and this is the only number that
    reveals it. The list itself is `getAllRaids()` (`:1285`) [VERIFIED]. Cannot refuse.
* **Delta.** **Nothing but a wrapper.** `((RaidManager) colony.getRaiderManager()).getAllRaids()`
  already returns `List<RaidManager.RaidHistory>` (`:37`) [VERIFIED] — records, already carrying the
  raid time, the raider count and the difficulty (all three are read at `CommandRaid.java:463-465`)
  [VERIFIED]. The only work is the `maxEntries` bound and the cast off `IRaiderManager`.
* **Matching row.** §2.10 `colony.raidHistory(int)`, Tier 3.

### `/minecolonies colony canSpawnRaiders <colony> <canSpawn:bool>`

* **What it does.** Sets the colony's "may be raided" flag and marks the colony dirty.
* **How it answers today.** One `sendSuccess` (`CommandCanRaiderSpawn.java:33`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:16`) [VERIFIED].
* **API form.** One new field on an existing record, and one new method.

  **1.**
  * *Signature.* `RaidForecast colony.raidForecast()`, read for its **new** `boolean raidsEnabled`
    field; §3.11's record does not carry one.
  * *Takes.* Nothing.
  * *Does.* Reads whether raids are possible against this colony at all, alongside the rest of the
    forecast. Mutates nothing.
  * *Returns.* `RaidForecast` as §3.11 defines it, plus `boolean raidsEnabled`
    (`mc/api/colony/managers/interfaces/IRaiderManager.java:32` `canHaveRaiderEvents()`) [VERIFIED].
    The rest: `int nightsSinceLastRaid`, `int minimumNights`, `int averageNights` (all nights, from
    config); `boolean possibleTonight`; `int maxHordeSize`, `int hordeDifficulty` (config);
    `RaiderType likelyType` (from the colony's biome); `boolean sizeIsPredictable`, always false.
    Without `raidsEnabled` a caller will build guard towers for a colony that can never be raided.
    Cannot refuse.

  **2.**
  * *Signature.* `CommandOutcome colony.setRaidsEnabled(boolean on)` — **new**; §2.10 has no row.
  * *Takes.*
    * `boolean on` — whether raids may happen. Required.
  * *Does.* Sets the colony's raid flag and marks the colony dirty so the change is saved. Does not
    stop a raid that is already running; `colony.endRaid()` does that.
  * *Returns.* `CommandOutcome`. Refusal codes: `REFUSED_PERMISSION`, `NO_EFFECT` (already at that
    value). The setter is `IRaiderManager#setCanHaveRaiderEvents(boolean)`
    (`mc/api/colony/managers/interfaces/IRaiderManager.java:46`) [VERIFIED] and needs no player.
* **Delta.** A wrapper each way. `IRaiderManager#setCanHaveRaiderEvents` and
  `canHaveRaiderEvents()` both exist and need no player (`:31`, and the read at
  `CommandColonyInfo.java:102`) [VERIFIED].
* **Matching row.** **None** for the setter; the read belongs in `RaidForecast` (§2.10, Tier 2) and
  the record as specified has no field for it. Both are worth adding: a caller planning defences that
  cannot tell whether raids are switched off at all will build guard towers for nothing.

### `/minecolonies colony antiair <colony|all> [where|tp|settings|reset|range|rate|damage|minlevel [<value>]]`

* **What it does.** Lists the colony's anti-air guard towers with bearing, distance and arrow stock;
  teleports to the one that is empty; reports or sets four tuning numbers (engagement range, rate of
  fire, damage, minimum tower level); and resets all four to their shipped defaults. Each tuning verb
  reports with no value and sets with one.
* **How it answers today.** `sendSuccess` for the tower listing
  (`CommandColonyAntiAir.java:141`, `:147`), the no-emplacements case (`:168`), the teleport results
  (`:212`, `:220`), the settings readout (`:248`, `:251`, `:520`, `:537`, `:548`, `:558`, `:574`), the
  reset (`:281`) and each set (`:317`, `:363`, `:412`, `:447`); `sendFailure` for a non-server colony
  (`:243`, `:274`, `:479`), for a missing player (`:186`) and — deliberately — for a value outside
  bounds (`:513`, with the reason stated in the comment at `:499`) [VERIFIED].
* **Player required?** Only for `tp` (`:184-188`) [VERIFIED]. Everything else, including all eight
  tuning verbs, runs from a console.
* **Permission gate.** `IMCOPCommand` (`:99`) [VERIFIED].
* **API form.** None proposed for the catalogue as written.
* **Delta.** Not applicable.
* **Matching row.** **None,** and the API should not gain it in this form. The emplacements come
  through `Compatibility.aircraftCompat`, whose default implementation reports nothing (`:120`,
  `:200`) [VERIFIED] — so the whole listing half is conditional on a third-party mod, and the
  catalogue is explicit that it models MineColonies and not its compatibility layers. The tuning half
  is per-colony configuration of a MineColonies feature and would fit a future §2.10 settings row, but
  no such row exists and inventing one is out of this document's remit. Worth recording that this is
  the only command in the tree that refuses out-of-range values with an explanation rather than
  clamping (`:499-513`) [VERIFIED] — which is exactly the `CommandOutcome(false, REFUSED_RULE, message)`
  contract of §4, already implemented once.

### `/minecolonies aircraft [where|tp]`

* **What it does.** Lists every aircraft on a scripted plan in the level plus anything else within 256
  blocks of the caller, with type, bearing, ground distance, coordinates and altitude; `tp` puts the
  caller on the ground beneath the nearest scripted one and turns them to face it.
* **How it answers today.** `sendSuccess` for the none/no-mod case (`CommandAircraft.java:115`,
  `:182`), the summary (`:130`), one entry and one detail line per aircraft (`:135`, `:142`) and the
  teleport result (`:214`); `sendFailure` for a missing player (`:175`) [VERIFIED].
* **Player required?** For `tp`, yes (`:173-176`) [VERIFIED]. For `where`, no — but the search is
  centred on `source.getPosition()` (`:110`) [VERIFIED], an implicit position, so a console caller
  searches around the world origin.
* **Permission gate.** `IMCOPCommand` (`:75`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** and deliberately not: aircraft belong to a third-party mod behind
  `AircraftCompat`, and §5's non-goals put entity control outside the API besides.

---

## 11. §2.11 Territory & Claims

### `/minecolonies colony claiminfo [<location:blockpos>]`

* **What it does.** Reads one chunk's claim data and prints the owning colony id, every colony with a
  static claim on it, and every building claiming it, resolving each building to its display name.
* **How it answers today.** One `sendSuccess` carrying a component assembled by
  `buildClaimCommandResult` (`CommandShowClaim.java:51`, built at `:63-118`) [VERIFIED].
* **Player required?** No, but the position defaults to
  `BlockPos.containing(context.getSource().getPosition())` (`:39`) [VERIFIED] — an implicit position
  from the source, which for a console is wherever the console's source stack sits.
* **Permission gate.** `IMCOPCommand` (`:26`) [VERIFIED].
* **API form.**
  * *Signature.* `ChunkOwner Colonies.chunkOwner(ChunkRef chunk)`
  * *Takes.*
    * `ChunkRef chunk` — dimension plus chunk coordinates. Required. A dimension the server does not
      have is rejected. **The chunk need not be loaded**: claim data is read from a per-dimension map,
      not from the chunk, which is what makes this cheap enough to ask in a loop.
  * *Does.* Reads who owns this chunk and who else has a claim on it. Mutates nothing and loads
    nothing.
  * *Returns.* `ChunkOwner` as §3.12 defines it, **plus two fields it has not got**, because the
    command already reads both and the catalogue's four-field record cannot hold them:
    `List<ColonyId> staticClaims` and `List<ClaimContribution> claimingBuildings`. Fields:
    `ChunkRef chunk` (echoed); `boolean claimed`; `ColonyId colony` (the absent sentinel
    `ColonyId(0, "")` when unclaimed — the tree's own no-colony value is the constant `NO_COLONY_ID`);
    `boolean inBuildRange`; `List<ColonyId> staticClaims`, every colony with a direct claim, ordered
    by id, from `mc/api/util/ColonyUtils.java:239` `getStaticClaims(ChunkAccess)` [VERIFIED];
    `List<ClaimContribution> claimingBuildings`, from `:228` `getAllClaimingBuildings(ChunkAccess)`
    [VERIFIED], which returns a `Map<Integer, Set<BlockPos>>` and so gives the building position and
    owning colony but **not** the type, level or radius contribution that §3.12's
    `ClaimContribution` names — those are a second lookup per building. The owner itself is `:177`
    `getOwningColony(ChunkAccess)` [VERIFIED]. Cannot refuse.
* **Delta.** The reads are already static utilities that need nothing but a chunk:
  `ColonyUtils.getStaticClaims(chunk)`, `ColonyUtils.getOwningColony(chunk)` and
  `ColonyUtils.getAllClaimingBuildings(chunk)` (`:67`, `:68`, `:86`) [VERIFIED]. The delta is that
  `buildClaimCommandResult` builds a `MutableComponent` and must build a record instead; the catalogue's
  `ChunkOwner(chunk, claimed, colony, inBuildRange)` is narrower than what this command already knows,
  and should gain the per-building contributions — which are also what §2.11's
  `ClaimInfo.contributions` needs.
* **Matching row.** §2.11 `Colonies.chunkOwner(ChunkRef)`, Tier 1; feeds §2.11 `colony.claim()`,
  Tier 1.

### `/minecolonies colony claim <colony> <range:int 0..10> <add:bool>`

* **What it does.** Adds or removes claim for a colony over a square of chunks centred on the caller,
  refusing a range above the `maxColonySize` config.
* **How it answers today.** `MessageUtils`, not `sendSuccess`: too-large (`CommandClaimChunks.java:48`),
  claimed (`:57`), unclaimed (`:61`) [VERIFIED].
* **Player required?** **Yes, for position.** `sender instanceof Player` at `:35`, and
  `sender.blockPosition()` is the centre of the claimed square at `:54` [VERIFIED]. Only a position and
  a level are needed semantically; a console form would take a `WorldPos`.
* **Permission gate.** `IMCOPCommand` (`:23`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** and the API should deliberately not have it. The claim is derived from
  buildings and their levels — §2.11's `ClaimInfo.contributions` says so — and a call that grants
  ground directly makes `claimAfter()` and the whole guard-tower-extends-the-claim decision
  meaningless. It is an operator's repair tool for a claim map that has gone wrong.

### `/minecolonies colony reclaimchunks <colony>`

* **What it does.** Calls `BackUpHelper.reclaimChunks(colony)`, which rebuilds the colony's claim from
  its buildings.
* **How it answers today.** `MessageUtils` only (`CommandReclaimChunks.java:37`) [VERIFIED].
* **Player required?** **Yes, but only to be told.** `sender instanceof Player` at `:30` gates the
  command, and the sender is used for nothing but the message at `:37`; the verb itself is
  `reclaimChunks(colony)` (`:36`) [VERIFIED]. A console caller is refused for no reason.
* **Permission gate.** `IMCOPCommand` (`:18`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None.** Same argument as `claim`: a repair for a corrupted claim map, not a
  colony verb. Worth flagging as the clearest example in the tree of a player requirement that is
  pure accident.

### `/minecolonies colony chunkstatus <colony>`

* **What it does.** Reports the colony's id and name, its loaded chunk count, the shared
  `ColonyChunkReport` (claimed chunks, how many are entity-ticking, tickets held against the ceiling)
  and the set of chunk ticket types the colony's chunks carry.
* **How it answers today.** `sendSuccess` for the identity line
  (`CommandColonyChunks.java:65-69`), the loaded count (`:70`) and the ticket types (`:76`); the shared
  report emits four to six further `sendSuccess` lines (`ColonyChunkReport.java:335`, `:337`, `:339`,
  `:342`, `:348`, `:356`) [VERIFIED].
* **Player required?** No — and pointedly so: the level is taken from the colony's own dimension, not
  the caller's, with a comment saying why (`CommandColonyChunks.java:37-39`) [VERIFIED].
* **Permission gate.** `IMCColonyOfficerCommand` (`:25`) [VERIFIED].
* **API form.** None in the catalogue's terms.
* **Delta.** Not applicable.
* **Matching row.** **None.** Chunk tickets are a Minecraft server concern, not a colony one, and the
  catalogue has no place for them. One observation belongs elsewhere though: `ColonyChunkReport.gather`
  is already a `record`-shaped value object with a separate `send(CommandSourceStack)`
  (`ColonyChunkReport.java:252`, `:332`) [VERIFIED] — the only place in the tree where a command's
  report is already separated from its rendering. It is the pattern every other entry in this document
  asks for.

### `/minecolonies colony forceloadclaims <colony> [on|off|default]`

* **What it does.** Sets, clears or reports a colony's own answer to "force-load the whole claim",
  three-valued so that `default` hands the colony back to the server config, then prints the same
  `ColonyChunkReport`.
* **How it answers today.** `sendSuccess` for the identity line
  (`CommandForceLoadClaims.java:96-100`) and a timing note (`:108-111`), plus the shared report;
  `sendFailure` when the colony is not loaded on this server (`:78`) [VERIFIED].
* **Player required?** No; the caller's name is read for the log line only
  (`context.getSource().getTextName()`, `:90`) [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:32`), and the class comment states the reason it is operator
  rather than officer: the cost is paid by the server (`:27-30`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None.** Chunk loading policy is server administration; §5 non-goal 7 already
  puts config out of the API's reach and this is config with a per-colony override.

### `/minecolonies colony territory [create <name> [<pos>] [<colour>] | colour|color <colony> <colour> | grow <colony> <radius:0..MAX> | bind <colony> | delete <colony>]`

* **What it does.** Makes, recolours, grows, points a scepter at, and erases hostile territories —
  colonies flagged hostile, owned by nobody, whose ground every player is locked out of. Bare, it
  lists every hostile territory in the caller's dimension with chunks owned, chunks indexed and colour.
* **How it answers today.** `sendSuccess` for the listing (`CommandColonyTerritory.java:162`), the
  none case (`:177`), a usage line (`:179`), creation (`:286`), recolour (`:321`), bind (`:408`),
  growth (`:464`) and deletion (`:511`); `sendFailure` for every refusal (`:253`, `:263`, `:305`,
  `:313`, `:391`, `:398`, `:404`, `:429`, `:488`, `:496`) [VERIFIED].
* **Player required?** Only for `bind` (`:389-393`) [VERIFIED], which needs a scepter in hand.
  `create` uses a player *if there is one*, to bind the scepter as a convenience (`:283-286`)
  [VERIFIED], and otherwise takes the position from the source — the comment at `:240-243` says this
  is deliberate so the command runs from a console.
* **Permission gate.** `IMCOPCommand` (`:74`) [VERIFIED].
* **API form.** The listing only, and it is new; §2.11 has no row.
  * *Signature.* `List<HostileTerritory> Colonies.territories(String dimension)`
  * *Takes.*
    * `String dimension` — which dimension to list. Required; a dimension the server does not have is
      rejected. Not optional, because the territory index is per dimension.
  * *Does.* Reads the colonies in that dimension that are flagged hostile — ground every player is
    locked out of and which draws in an enemy colour. Mutates nothing.
  * *Returns.* `List<HostileTerritory>` — **a new record**, §3 has none:
    `HostileTerritory(ColonyId id, String name, BlockPos centre, int chunksOwned, int chunksIndexed,
    String colour)`. One element per hostile colony, ordered by id; empty when the dimension has
    none, which is the ordinary case. `id`, `name` and `centre` are the plain colony getters
    (`mc/api/colony/IColony.java:98`, `:65`, `:58`) [VERIFIED]; the hostile flag is
    `Colony#isHostile()` (`mc/core/colony/Colony.java:2029`) [VERIFIED]; `colour` is
    `Colony#getTeamColonyColor()` (`:2496`) [VERIFIED], and a caller must know that `WHITE` is what a
    territory made before colours existed still carries and that it actually draws red
    (`CommandColonyTerritory.java:168-172`) [VERIFIED]. `chunksOwned` and `chunksIndexed` are two
    counts that differ — owned is read off the claim map, indexed off the territory index — and both
    are already computed, as private counters called at `CommandColonyTerritory.java:159-160`
    [VERIFIED]. Cannot refuse.
* **Delta.** The listing half already computes what a record needs: chunks owned and chunks indexed
  are two private counters, `countOwnedChunks` and `countIndexedChunks`, called at `:159-160`
  [VERIFIED]. The creating half is `IServerColonySaveData#createColony` plus three setters
  (`:260-277`) [VERIFIED] and is a wrapper, but see below.
* **Matching row.** **None.** The read is worth adding: §2.11 has `colony.allies()` and
  `setRelation(ColonyId, Relation)`, and a hostile territory is a `Relation.ENEMY` whose other end is
  not a colony anybody plays. A caller that cannot see enemy ground cannot plan around it. The
  create/grow/delete verbs should **not** be added: they are world authorship, the same class of act
  as `Colonies.found`, and the catalogue puts even colony deletion behind `caller is not an operator`
  at Tier 3.

---

## 12. §2.12 World & Placement

Note that the supply-camp rows of §2.12 are cancelled, so nothing below maps onto them.

### `/minecolonies scan <pos1:blockpos> <pos2:blockpos> [<anchor:blockpos>] | [<player:gameprofile> [<filename:string> [<anchor:blockpos>]]]`

* **What it does.** Saves the blocks between two corners as a Structurize schematic and hands it to a
  player, optionally with an anchor position and a file name.
* **How it answers today.** `sendFailure` for everything, including success:
  `NO_PERMISSION_MESSAGE` (`ScanCommand.java:285`), player not found (`:294`, `:304`) and —
  evidently a mistake — `SCAN_SUCCESS_MESSAGE` (`:310`) [VERIFIED].
* **Player required?** **Yes, and a real client.** `source.getPlayerOrException()` at `:283` and
  `:300`, and the named player must be online (`:291-296`) [VERIFIED]; the scan is delivered to that
  player's client.
* **Permission gate.** None of the three interfaces — `ScanCommand` extends Structurize's
  `AbstractCommand`, not `IMCCommand` [VERIFIED, its declaration is absent from the tree-wide grep for
  `implements IMC*`]. The only check is that a player caller is in creative (`:283-286`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** deliberately not. §5 non-goal 3 rules out anything that runs on a
  client, and non-goal 4 rules out schematic work outside the mod's own placement.

### `/minecolonies resetsupplies <playername:string>`

* **What it does.** Awards `-1` of the supply-chest use statistic to a named online player, which is
  how the mod's one-supply-camp-per-world rule is undone.
* **How it answers today.** `sendSuccess` for the not-found case when the caller is not a player
  (`CommandResetPlayerSupplies.java:40`) and for success (`:46`); `MessageUtils` for the player-facing
  copies (`:36`, `:47`) [VERIFIED].
* **Player required?** No for the caller; **yes for the target**, who must be online
  (`getPlayerByName`, `:30-43`) [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:19`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None.** `Sites.supplyState(PlayerRef)` would have been the read half, but the
  supply-camp rows are cancelled, so there is nothing for this to attach to. The statistic write is an
  operator's undo in any case.

---

## 13. §2.13 Permissions

### `/minecolonies colony addOfficer <colony> <playername:gameprofile>`

* **What it does.** Adds an online player to the colony's permission map at the officer rank and
  registers them as an important colony player for packet purposes.
* **How it answers today.** `sendSuccess` for the config refusal (`CommandAddOfficer.java:32`), the
  player-not-found case (`:53`) and success (`:59`) [VERIFIED].
* **Player required?** No for the caller; **yes for the target**: the command refuses a profile whose
  player is not on the player list (`:50-55`) [VERIFIED], which means an offline player cannot be
  promoted.
* **Permission gate.** `IMCColonyOfficerCommand` (`:20`), plus `canPlayerUseAddOfficerCommand` for a
  non-op (`:30-34`) [VERIFIED].
* **API form.**
  * *Signature.* `CommandOutcome colony.permissions().setRank(PlayerRef player, PermissionRank rank)`
  * *Takes.*
    * `PlayerRef player` — whose rank to set, by uuid; the name is stored alongside. Required. **The
      player need not be online**, which is the one behavioural change from the command: the
      underlying `addPlayer(UUID, String, Rank)` takes no player object
      (`mc/api/colony/permissions/IPermissions.java:88`) [VERIFIED], and the command's online check
      exists only because the line after it wants a `ServerPlayer` for an unrelated packet
      registration (`CommandAddOfficer.java:57`) [VERIFIED].
    * `PermissionRank rank` — the rank to give. Required. See the objection in section 19: this
      tree's ranks are a map with ids and names (`IPermissions.java:38` `getRanks()`) [VERIFIED], not
      the five constants of §3.14, so either the parameter is widened or custom ranks are unreachable.
  * *Does.* Sets that player's rank in this colony, replacing whatever it was. Saved with the colony.
  * *Returns.* `CommandOutcome`. Refusal codes: `REFUSED_PERMISSION` (the caller is not the owner),
    `REFUSED_RULE` (attempting to set the owner's own rank — use `setOwner` instead),
    `REFUSED_UNKNOWN_TARGET` (no such rank in this colony), `NO_EFFECT` (already at that rank). The
    two writes are `IPermissions#addPlayer(UUID, String, Rank)` (`:88`) and
    `#setPlayerRank(UUID, Rank, Level)` (`:142`) [VERIFIED].
* **Delta.** Nothing but a wrapper plus one fix. `IPermissions#addPlayer(UUID, String, Rank)` takes no
  player object and needs no online presence (`:56`) [VERIFIED]; the online check exists only because
  the next line wants a `ServerPlayer` for `addImportantColonyPlayer` (`:57`) [VERIFIED]. Splitting
  those two makes the verb work for an offline player, which is what a `PlayerRef` argument implies.
* **Matching row.** §2.13 `colony.permissions().setRank(PlayerRef, PermissionRank)`, Tier 2.

### `/minecolonies colony setRank <colony> <playername:gameprofile> <rank:greedystring>`

* **What it does.** Sets a player's rank to any rank the colony defines, matching by name
  case-insensitively and tolerating the `"<id> <name>"` form the suggestion provider offers.
* **How it answers today.** `sendSuccess` for player-not-found (`CommandSetRank.java:50`), an unknown
  rank — as a bare English literal, `"Rank does not exist"` (`:72`) — and success, also a literal
  (`:78`) [VERIFIED].
* **Player required?** No for the caller; **yes for the target**, online (`:47-52`) [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:23`) — stricter than `addOfficer`, which is officer-gated
  [VERIFIED].
* **API form.** The same method as `colony addOfficer`, contracted in full there. This command adds
  nothing to the signature; it differs only in reaching every rank the colony defines rather than the
  officer constant, which is the argument for widening the `rank` parameter recorded there and in
  section 19.
* **Delta.** The write is `IPermissions#setPlayerRank(UUID, Rank, Level)` (`:76`) [VERIFIED], a
  wrapper. The interesting delta is the type: this colony's ranks are **data**, not the five constants
  of the catalogue's `PermissionRank` enum — `colony.getPermissions().getRanks()` returns a map and
  the command searches it by name (`:61-68`) [VERIFIED]. §3.14's fixed enum cannot express a
  custom rank, and either the enum becomes a record with an id or `setRank` needs a second form.
* **Matching row.** §2.13 `colony.permissions().setRank(PlayerRef, PermissionRank)`, Tier 2 — with
  the type objection above.

### `/minecolonies colony setowner <colony> <playername:gameprofile>`

* **What it does.** Transfers ownership of the colony to an online player.
* **How it answers today.** `sendSuccess` for player-not-found (`CommandChangeOwner.java:47`) and
  success (`:53`) [VERIFIED].
* **Player required?** No for the caller; **yes for the target**, and here genuinely so:
  `IPermissions#setOwner` takes a `Player` object, not a UUID (`:51`) [VERIFIED].
* **Permission gate.** `IMCColonyOfficerCommand` (`:20`) [VERIFIED]. Notably an *officer* may hand the
  colony away, not only the owner.
* **API form.** New method; §2.13 has no row.
  * *Signature.* `CommandOutcome colony.permissions().setOwner(PlayerRef player)`
  * *Takes.*
    * `PlayerRef player` — the new owner, by uuid. Required. **Today the player must be online**,
      because `IPermissions#setOwner(Player)` takes a `Player` object
      (`mc/api/colony/permissions/IPermissions.java:115`) [VERIFIED]; a `PlayerRef` parameter needs a
      uuid-and-name overload, which is the same change `setRank` needs.
  * *Does.* Transfers ownership of the colony. **Side effect:** the previous owner is not removed
    from the permission map, so a caller that wants them gone calls `setRank` afterwards.
  * *Returns.* `CommandOutcome`. Refusal codes: `REFUSED_PERMISSION` (the caller is neither the owner
    nor an operator), `REFUSED_UNKNOWN_TARGET` (unknown player), `NO_EFFECT` (already the owner).
* **Delta.** The delta is the `Player` parameter: to accept a `PlayerRef` the permission model needs a
  UUID-and-name overload of `setOwner`, which is the same change `addOfficer` needs.
* **Matching row.** **None.** Worth adding. §2.13 has `setRank` but no way to move the owner, and
  §3.14 makes `OWNER` a rank that `setRank` explicitly cannot set ("cannot change the owner's own
  rank"). Handing a colony over is a real multiplayer act with no other route.

### `/minecolonies colony setAbandoned <colony>`

* **What it does.** Marks the colony's owner as abandoned, and — if the caller is a colony manager —
  adds the caller as an officer first, so the colony does not become unreachable.
* **How it answers today.** One `sendSuccess`, reusing the owner-change message with the literal
  `"[abandoned]"` (`CommandSetAbandoned.java:43`) [VERIFIED].
* **Player required?** **Yes, for identity, optionally.** `sender != null` gates the rank read and the
  officer grant (`:31-41`) [VERIFIED]; a console caller skips both and the colony is simply abandoned.
  The unguarded cast `(Player) sender` at `:31` would fail for a non-player entity source.
* **Permission gate.** `IMCColonyOfficerCommand` (`:17`) [VERIFIED].
* **API form.** New method; §2.13 has no row.
  * *Signature.* `CommandOutcome colony.permissions().abandon()`
  * *Takes.* Nothing.
  * *Does.* Marks the colony's owner as abandoned, so the colony has no owner. **It deliberately does
    not do what the command does next:** the command promotes the caller to officer first so the
    colony does not become unreachable (`CommandSetAbandoned.java:30-41`) [VERIFIED], and that is a
    command convenience, not part of the verb — a caller that wants it calls `setRank` first, in that
    order.
  * *Returns.* `CommandOutcome`. Refusal codes: `REFUSED_PERMISSION`, `NO_EFFECT` (already
    abandoned). The verb is `IPermissions#setOwnerAbandoned()`
    (`mc/api/colony/permissions/IPermissions.java:129`) [VERIFIED], one call taking nothing.
* **Delta.** `IPermissions#setOwnerAbandoned()` is one call and needs nothing (`:36`) [VERIFIED]. The
  self-promotion is a command convenience and should not be in the method; a caller that wants it
  calls `setRank` first.
* **Matching row.** **None.** Worth adding, for the same reason as `setowner`: a colony whose owner
  has left the server is a state the API must be able to reach, and `Colonies.delete` is the only
  alternative the catalogue offers.

### `/minecolonies ranks <playername:gameprofile> [startpage:int]`

* **What it does.** Lists every colony on the server in which the named player holds a rank other than
  neutral, ten per page, with the rank name.
* **How it answers today.** `sendSuccess` for player-not-found (`CommandGetRanks.java:73`), the page
  header (`:109`), one line per colony (`:114`) and the pager (`:125`) [VERIFIED].
* **Player required?** No for the caller; **yes for the target**, online (`:70-75`) [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:27`) [VERIFIED].
* **API form.** New method; §2.13 has the per-colony direction only.
  * *Signature.* `List<PlayerColonyRank> Colonies.ranksOf(PlayerRef player)`
  * *Takes.*
    * `PlayerRef player` — whose standing is wanted, by uuid. Required. **The player need not be
      online**: `IPermissions#getRank(UUID)` (`mc/api/colony/permissions/IPermissions.java:153`)
      [VERIFIED] needs no player object, and the command's online check can be dropped.
  * *Does.* Reads, across every colony on the server, the rank this player holds where it is anything
    other than neutral. Mutates nothing. This is the call an automation account makes first, to find
    out where it may act at all.
  * *Returns.* `List<PlayerColonyRank>` — **a new record**, §3 has none:
    `PlayerColonyRank(ColonyId colony, String colonyName, PermissionRank rank)`. One element per
    colony in which the player is not neutral, ordered by colony id; empty for a player with no
    standing anywhere, which is the ordinary case and not an error. The filter already exists as one
    stream comparing `getRankNeutral()` (`:69`) against `getRank(UUID)` (`:153`) over
    `IColonyManager#getAllColonies()` (`mc/api/colony/IColonyManager.java:148`)
    (`CommandGetRanks.java:78-82`) [VERIFIED]. Cannot refuse.
* **Delta.** The filter is one stream over `getAllColonies()` comparing
  `getRankNeutral()` against `getRank(uuid)` (`:78-82`) [VERIFIED] — a wrapper, once the online check
  is dropped, which it can be because `getRank(UUID)` needs no player object.
* **Matching row.** **None.** §2.13's `colony.permissions().list()` is the per-colony direction; this
  is the per-player one, and it is the call an automation account makes to find out where it is allowed
  to act at all. Worth adding.

### `/minecolonies colony delete <colony> [<keep/delete buildings:bool> [<confirm:bool>]]`

* **What it does.** Backs the colony data up and deletes the colony, optionally tearing the buildings
  down. With fewer arguments it walks the caller through a two-step confirmation built out of
  click-to-run chat buttons.
* **How it answers today.** `sendSuccess` for the config refusal
  (`CommandDeleteColony.java:152`), the two guided prompts (`:113`, `:143`) and the success line
  (`:174`) [VERIFIED]. An unconfirmed invocation returns 1 having done nothing (`:167-170`)
  [VERIFIED].
* **Player required?** No — but the confirmation flow is chat-only and useless from a console; a
  console caller must supply both booleans.
* **Permission gate.** `IMCColonyOfficerCommand` (`:34`), plus `canPlayerUseDeleteColonyCommand` for a
  non-op (`:150-154`) [VERIFIED]. The catalogue puts this at operator-only, which this tree does not.
* **API form.**
  * *Signature.* `CommandOutcome Colonies.delete(ColonyId id, boolean deleteBuildings)`
  * *Takes.*
    * `ColonyId id` — the colony to remove. Required. A colony that does not exist throws
      `NoSuchColonyException` per §4 rather than refusing.
    * `boolean deleteBuildings` — whether to tear the structures down as well as removing the colony.
      Required, with **no default**, because the guided confirmation the command offers has no API
      equivalent and an explicit argument is what replaces it.
  * *Does.* Removes the colony. **Two side effects a caller would not predict from the name.** The
    colony save data is backed up first, unconditionally (`CommandDeleteColony.java:172`) [VERIFIED],
    and that policy should be kept — it is the only thing standing between a mistyped id and a lost
    town. Every handle taken from that colony becomes stale and throws on its next call, per §4.
  * *Returns.* `CommandOutcome`. Refusal codes: `REFUSED_PERMISSION`. Note the gate disagreement
    recorded in the Matching row below: §2.13 says operator-only, the command allows a colony officer
    whenever a config flag is on (`:150`) [VERIFIED]. The verb is
    `IColonyManager#deleteColonyByDimension(int, boolean, ResourceKey<Level>)`
    (`mc/api/colony/IColonyManager.java:66`) [VERIFIED], one call taking no player.
* **Delta.** Nothing but a wrapper. `IColonyManager#deleteColonyByDimension(id, deleteBuildings,
  dimension)` is one call taking no player (`:173`) [VERIFIED]. The backup at `:172` [VERIFIED] is a
  policy this method should keep. The confirmation is a chat affordance with no API form — the API's
  equivalent is the argument being explicit.
* **Matching row.** §2.13 `Colonies.delete(ColonyId, boolean)`, Tier 3. **Disagreement worth
  recording:** the catalogue says this call fails when "caller is not an operator", but the command as
  registered is available to a colony officer whenever `canPlayerUseDeleteColonyCommand` is on
  (`:150`) [VERIFIED]. Either the catalogue's gate or the config flag has to give.

### `/minecolonies colony freemode <colony> [on|off]`

* **What it does.** Turns `FreeMode` on or off for a colony, so every worker gets on with its job
  without the items it would normally be given. Bare, reports the state.
* **How it answers today.** `sendSuccess` for the report (`CommandColonyFreeMode.java:35`) and the set
  (`:56`) [VERIFIED]. A non-server colony returns 0 silently (`:49-53`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:24`) [VERIFIED], which matches the catalogue.
* **API form.** One field on an existing record, and one method.

  **1.**
  * *Signature.* `ColonySettings colony.settings()`, read for its `freeModeOn` field.
  * *Takes.* Nothing.
  * *Does.* Reads the town hall switches. Mutates nothing.
  * *Returns.* `ColonySettings` exactly as §3.2 defines it, six `boolean` fields:
    `newCitizensEnabled`, `autoHiringEnabled`, `autoHousingEnabled`, `movingInEnabled`,
    `freeModeOn`, `progressToChat`. Only `freeModeOn` has a source this command reaches:
    `FreeMode.isOn(IColony)` (`mc/core/debug/FreeMode.java:95`) [VERIFIED], a static predicate. The
    other five are settings-module keys read one at a time; **nothing enumerates them**, so the
    record has to name the five keys itself. Cannot refuse.

  **2.**
  * *Signature.* `CommandOutcome colony.setFreeMode(boolean on)`
  * *Takes.*
    * `boolean on` — whether free mode is on. Required; no toggle form.
  * *Does.* Lifts the builder's-hut level gate and lets every worker get on with its job without the
    items it would normally be given. **An operator's testing switch**: with it on, most of §2.2's
    eligibility answers and most of §2.5's problem kinds stop reflecting the real game, so a caller
    driving a real colony must not use it.
  * *Returns.* `CommandOutcome`. Refusal codes: `REFUSED_PERMISSION` (caller is not an operator),
    `REFUSED_RULE` (free mode disabled by config), `NO_EFFECT` (already at that value). The setter is
    `Colony#setFreeMode(boolean)` (`mc/core/colony/Colony.java:2017`) [VERIFIED], a plain setter
    needing no player.
* **Delta.** **Nothing but a wrapper both ways.** `FreeMode.isOn(colony)` is a static predicate
  (`:35`) and `Colony#setFreeMode(boolean)` is a plain setter (`:55`) [VERIFIED], neither needing a
  player. The catalogue's `ColonySettings` record already has a `freeModeOn` field (§3.2).
* **Matching row.** §2.13 `colony.setFreeMode(boolean)`, Tier 3; §2.1 `colony.settings()`, Tier 1, for
  the read. This is the cheapest complete row in the whole tree.

### `/minecolonies colony protection <colony> [on|off]`

* **What it does.** Turns permission enforcement on or off for one colony — the middle ground between
  the server-wide `enablecolonyprotection` config and editing the rank table action by action. Bare,
  reports the state.
* **How it answers today.** `sendSuccess` for the report (`CommandColonyProtection.java:45-46`) and
  the set (`:67-68`) [VERIFIED]. A non-server colony returns 0 silently (`:40-43`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:29`) [VERIFIED].
* **API form.** The read only, and it is new; §2.13 has no row.
  * *Signature.* `boolean colony.permissions().enforced()`
  * *Takes.* Nothing.
  * *Does.* Reads whether this colony enforces its permission table at all. Mutates nothing.
  * *Returns.* `boolean` — `true` when the rank table is in force, `false` when it is switched off for
    this colony. There is no third value: the server-wide `enablecolonyprotection` config is a
    separate question and belongs in `ConfigReport` (§3.15). The source is `Colony#isProtection()`
    (`mc/core/colony/Colony.java:2169`) [VERIFIED]. Cannot refuse. The matching setter,
    `Colony#setProtection(boolean)` (`:2179`) [VERIFIED], is deliberately **not** proposed; the
    Matching row below says why.
* **Delta.** Nothing but a wrapper. `Colony#isProtection()` and `setProtection(boolean)` are a plain
  getter and setter, read and written by the command at `CommandColonyProtection.java:46` and `:66`
  [VERIFIED].
* **Matching row.** **None.** The read is worth adding and the write is not. §2.13's whole point is
  that a caller asks `permissions().can(player, action)` before acting; on a colony with protection
  off, that predicate is a lie, and a caller with no way to see the switch cannot know it. The write
  is an operator's testing switch and belongs beside `setFreeMode` if anywhere.

---

## 14. §2.14 Diagnostics

### `/minecolonies colony diagnose <colony>`

The largest command in the tree at 819 lines, and the single most valuable body in it.

* **What it does.** Sweeps a colony for the states a healthy one never reaches and prints a report:
  every employed citizen with its AI state, how long it has held that state and its job status, sorted
  worst first; citizens whose entity is loaded but whose `IJob#getWorkerAI()` is null; jobs with no
  work building; couriers with no warehouse; open job slots; requests that have not moved; work orders
  claimed by builders that no longer exist; buildings with impossible level, blueprint or parent
  state, including a mine whose shaft has stalled; and the barracks border-patrol plan with each
  guard's distance off its assigned stretch.
* **How it answers today.** All through a private `Report` helper that writes each line to chat with
  `sendSuccess(..., false)` and simultaneously accumulates the whole text for the server log: five
  summary lines (`CommandColonyDiagnose.java:193`, `:194`, `:200`, `:205`, `:209`), a problem count
  (`:214`) and nine capped sections (`:220`, `:222-229`), then `Log.getLogger().info(report.getLog())`
  (`:231`) [VERIFIED].
* **Player required?** No. Nothing in `onExecute` touches the source except through `Report`
  (`:107-232`) [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:54`) [VERIFIED]. api-inventory describes the same prototype
  as requiring operator rank, and that is right.
* **API form.** Three methods.

  **1.**
  * *Signature.* `List<ColonyProblem> colony.problems()`
  * *Takes.* Nothing.
  * *Does.* Sweeps citizens, buildings, work orders and requests and returns everything wrong with the
    colony. Mutates nothing — which the command's prototype does not manage, because it writes a
    static observation map as it goes (see the Delta below).
  * *Returns.* `List<ColonyProblem>`, the §3.15 record, **sorted worst first**: by severity
    descending, then by how many citizens each affects. Empty for a healthy colony, which is a real
    and common answer. Fields: `ProblemKind kind`, one of the thirty-one constants of §3.6;
    `ProblemSeverity severity`, `INFO`/`WARNING`/`BLOCKING` — the tree already assigns an equivalent,
    as an `int` inside a private record, 3 for a citizen whose AI was never created, 2 for an idle
    worker, 1 for an unloaded entity, 0 otherwise (`CommandColonyDiagnose.java:267`, `:275`, `:280`)
    [VERIFIED]; `String message` and `String suggestedFix` (human-readable, translatable);
    `CitizenId citizen`, `BuildingId building`, `BuildOrderId order`, `RequestId request` (each the
    absent sentinel when the problem is not about that kind of thing — exactly one is normally set);
    `int affectedCitizens` (a count). **One `ProblemKind` the catalogue has not got** is needed: the
    stalled mine shaft, which the command detects from `BuildingMiner#getShaftStallTicks`
    (`mc/core/colony/buildings/workerbuildings/BuildingMiner.java:354`, read at
    `CommandColonyDiagnose.java:480`) [VERIFIED] and which shows up nowhere else in the report.
    Cannot refuse; it is a query, though it is operator-gated as a command today.

  **2.**
  * *Signature.* `List<WorkProblem> citizen.problems()`
  * *Takes.* Nothing.
  * *Does.* Returns everything blocking this one citizen. Mutates nothing.
  * *Returns.* `List<WorkProblem>`, the §3.6 record, **all that apply**, not just the first, in the
    check order §2.5 gives; empty for a citizen with nothing wrong. Fields: `ProblemKind kind`;
    `ProblemSeverity severity`; `String message`; `ItemSpec item` and `BuildingId building` and
    `CitizenId citizen` (the subject or the thing the problem concerns, absent sentinel where not
    applicable); `String suggestedFix`. Cannot refuse.

  **3.**
  * *Signature.* `List<WorkProblem> building.problems()`
  * *Takes.* Nothing.
  * *Does.* Returns the problems that belong to the building rather than to a person — paused, no
    worker hired, no field assigned, mine below its depth limit. Mutates nothing.
  * *Returns.* `List<WorkProblem>` as above, empty for a building with nothing wrong. **The empty hut
    is the case this method exists for**, and it is the case the tree cannot currently reach: every
    building-shaped predicate in the mod starts from a citizen and arrives at the building through
    `getWorkBuilding()`, so a hut with nobody in it reports nothing. The exception, and the model to
    copy, is this command's own `collectBuildingProblems`
    (`CommandColonyDiagnose.java:471-520`) [VERIFIED], which is building-major already. Cannot refuse.
* **Delta.** Large but well understood, and this command is the reason. Four things.
  1. **Every list is `List<String>`.** Nine of them are declared at `:118-126` [VERIFIED], and each
     entry is a `String.format` with the subject's id, name and position baked into the text. Each has
     to become a record carrying `ProblemKind`, severity, the subject id and a message — the
     information is all there, it is just already stringified.
  2. **The severity ordering already exists.** `collectWorker` assigns `severity` 3 for a missing AI,
     2 for an idle worker, 1 for an unloaded entity and 0 otherwise (`:267`, `:275`, `:280`) [VERIFIED], and the sort is severity descending then age descending (`:219`) [VERIFIED].
     That is `ProblemSeverity` and the catalogue's "sorted worst first" in one line.
  3. **The age is a workaround that the API must not inherit.** Nothing in the colony model records
     how long a citizen has been in an AI state, so the command keeps a static per-colony map of
     observations and reports the gap between two invocations of itself
     (`OBSERVATIONS`, `:91`; the `Observation` record, `:99`; `observe`, `:661`) [VERIFIED]. `formatAge` returns the literal `"new"` whenever it cannot tell (`:685`) [VERIFIED]. `WorkStatus.lastProductiveGameTime`, `CitizenComplaintEvent.daysStanding` and the whole
     grace-period mechanism of §2.5 sit on state that does not exist; api-inventory says the same and
     this file is its proof.
  4. **One check has no other source.** The stalled-mine detection reads
     `BuildingMiner#getShaftStallTicks` (`:480`) [VERIFIED],
     and the comment records that without it the command answered "No problems found" through a shaft
     that had not moved in a hundred thousand ticks. That is a `ProblemKind` the catalogue does not
     have.
* **Matching row.** §2.14 `colony.problems()`, Tier 1; §2.5 `citizen.problems()` and
  `building.problems()`, both Tier 1; §2.5 `citizen.workStatus()`, Tier 1.

### `/minecolonies colony printStats <colony>`

* **What it does.** Prints identity, mayor, population against cap, coordinates, hours since last
  contact, whether the colony can be auto-deleted, whether it can be raided, the last raid record, the
  building count with average level and a line per built building, and the completed research count
  with a line per research id. The whole text also goes to the server log.
* **How it answers today.** `sendSuccess(..., false)` throughout, via a `literalAndRemember` helper
  that appends to a field: `CommandColonyPrintStats.java:53`, `:55`, `:57`, `:59`, `:62`, `:63`,
  `:67`, `:73`, `:93`, `:95`, `:106`, `:107`, then `Log.getLogger().info(fullLog)` (`:109`)
  [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:29`) [VERIFIED].
* **API form.**
  * *Signature.* `ProgressionReport colony.progression()`
  * *Takes.* Nothing.
  * *Does.* Reads where the colony stands against the finish-line checklist. Mutates nothing.
  * *Returns.* `ProgressionReport` exactly as §3.15 defines it, thirteen fields, twelve of them `int`
    counts or building levels and the thirteenth an enum. Of the twelve, **four have a source in this
    command and eight do not**. Present: `int population`
    (`mc/api/colony/managers/interfaces/ICitizenManager.java:121`) [VERIFIED];
    `int populationCap` (`:100`) [VERIFIED]; `int buildingLevelsBuilt` (the sum the command already
    computes, `CommandColonyPrintStats.java:78-90`, whose average divides by a count that is zero for
    a colony with nothing built — a latent divide-by-zero the record's arithmetic must not copy)
    [VERIFIED]; `int researchFinished` (`LocalResearchTree#getCompletedList().size()`, read at
    `:105`) [VERIFIED]. Absent, each a filter or a maximum over the building map that nothing
    performs: `int townHallLevel`, `int builderHutMaxLevel`, `int warehouseLevel`, `int courierCount`,
    `int universityLevel`, `int researchReachable`, `int buildingTypesBuilt`,
    `int buildingTypesTotal`. And `ProgressionStage stage`, one of the eleven constants of §3.15, is
    a judgement over all of the above and exists nowhere. Cannot refuse.
* **Delta.** This is the closest thing in the tree to `colony.progression()`, and the gap is
  instructive. It has population, building count, building levels and finished research — four of the
  twelve fields of `ProgressionReport` (§3.15). It does not have town hall level, builder's hut
  maximum, warehouse level, courier count, university level, reachable research or the coarse stage;
  each of those is a filter over `getBuildings()` that nothing here performs. The average-level
  computation (`:78-90`) [VERIFIED] divides by a count that is zero when no building is built, which
  is a latent divide-by-zero the record's arithmetic must not copy. `getCompletedList()` off
  `LocalResearchTree` (`:105`) [VERIFIED] is `researchFinished` for free.
* **Matching row.** §2.14 `colony.progression()`, Tier 2; overlaps §2.1 `colony.progress()`, Tier 1.

### `/minecolonies colony resetStats <colony>`

* **What it does.** Clears the colony-wide statistics manager and every building's
  `BuildingStatisticsModule`, marking each changed building dirty.
* **How it answers today.** One `sendSuccess` with the count of buildings cleared
  (`CommandColonyResetStats.java:56-57`) [VERIFIED].
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:20`) [VERIFIED].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None,** and the API should not have it. §5 non-goal 10 says the API stores
  nothing on the caller's behalf and returns what the colony records; clearing what the colony records
  is the opposite act. What the file proves is that per-colony and per-building statistics **exist**
  (`colony.getStatisticsManager()` at `:32`, `BuildingStatisticsModule` at `:40`) [VERIFIED], and no
  call in the catalogue reads them — a gap worth noting against §2.14.

---

## 15. Commands that fit no block

Twenty-four commands. Each entry is shorter, because the disposition is the same in every case: the
verb is a server-administration or debugging affordance and the API is not the place for it. Where a
command nonetheless contains a fact the API needs, that is said.

### The kill subtree — `/minecolonies kill (animals|chicken|cow|monster|pig|raider|sheep)`

* **What they do.** Each removes every entity of one kind from the caller's level and counts them.
  `animals` and `monster` test by class (`CommandKillAnimal.java:25`, `CommandKillMonster.java:25`);
  the four species test by entity type (`CommandKillChicken.java:25`, `CommandKillCow.java:24`,
  `CommandKillPig.java:24`, `CommandKillSheep.java:24`) [VERIFIED]. `raider` additionally kills with
  the `CONSOLE` damage source and sets the raider's colony event to `DONE`
  (`CommandKillRaider.java:33-41`) [VERIFIED].
* **How they answer.** One `sendSuccess` each, the bare English literal `"N entities killed"`
  (`CommandKillAnimal.java:33`, `CommandKillChicken.java:30`, `CommandKillCow.java:29`,
  `CommandKillMonster.java:33`, `CommandKillPig.java:29`, `CommandKillRaider.java:46`,
  `CommandKillSheep.java:29`) [VERIFIED] — the only unlocalised success messages in the tree.
* **Player required?** No; the level comes from the source.
* **Permission gate.** `IMCOPCommand`, all seven [VERIFIED at each class declaration].
* **API form.** None.
* **Delta.** Not applicable.
* **Matching row.** **None.** Entity removal is §5 non-goal 6, and a colony API that can clear the
  world of cows makes every §2.8 food question meaningless. `kill raider` overlaps `colony raid
  <colony> stop`, which is the colony-scoped verb worth having instead (see §2.10 above); note also
  that each of these seven mutates a non-final instance field to count
  (`CommandKillAnimal.java:13`) [VERIFIED], which is not thread-safe and would be a defect in a
  method.

### `/minecolonies colony teleport <colony>` and `/minecolonies home` (also `/minecolonies colony home`)

* **What they do.** `colony teleport` moves the caller to a named colony; `home` moves them to their
  own colony's town hall.
* **How they answer.** Neither uses `sendSuccess`: `colony teleport` sends the config refusal by
  `MessageUtils` (`CommandTeleport.java:37`) and otherwise says nothing (`:44`); `home` likewise
  (`CommandHomeTeleport.java:29`, `:33`) [VERIFIED].
* **Player required?** **Yes, a real client, unconditionally.** Both cast the source entity to
  `ServerPlayer` (`CommandTeleport.java:43`, `CommandHomeTeleport.java:33`) [VERIFIED], with no
  instance check in either — a console caller throws.
* **Permission gate.** `colony teleport` is `IMCColonyOfficerCommand` (`:23`) plus
  `canPlayerUseColonyTPCommand` (`:35-39`); `home` is `IMCCommand` plus `canPlayerUseHomeTPCommand`
  (`:15`, `:27-31`) [VERIFIED].
* **Matching row.** **None.** §3.14 lists `ColonyAction.TELEPORT` as a permission, so the *right* is
  modelled; the act is not, and should not be — it moves a human being's avatar and has no meaning for
  a headless caller.

### `/minecolonies backup`, `/minecolonies colony export <colony>`, `/minecolonies colony loadBackup <colony>`, `/minecolonies colony loadAllColoniesFromBackup`

* **What they do.** Write the colony save data to disk; export one colony's region and backup files;
  restore one colony from backup; restore all of them.
* **How they answer.** `sendSuccess` in every case, including failure —
  `CommandBackup.java:25` and `:29` are both `sendSuccess`, one saying "success" and one saying
  "failed" [VERIFIED]; also `CommandExportColony.java:28`, `CommandLoadBackup.java:30`,
  `CommandLoadAllBackups.java:22` [VERIFIED].
* **Player required?** None of the four.
* **Permission gate.** `IMCOPCommand`, all four (`CommandBackup.java:12`,
  `CommandExportColony.java:19`, `CommandLoadBackup.java:18`, `CommandLoadAllBackups.java:11`)
  [VERIFIED].
* **Matching row.** **None.** Save-file management is server administration. It is also the one place
  where a `CommandOutcome` would be an improvement on today's behaviour, since `backup` currently
  reports its own failure as a success.

### `/minecolonies colony setDeleteable <colony> <deletable:bool>`

* **What it does.** Sets `IColony#setCanBeAutoDeleted`. The file's own comment says it is unused
  (`CommandSetDeletable.java:16`) [VERIFIED].
* **How it answers.** One `sendSuccess` (`:33-34`) [VERIFIED].
* **Player required?** No. **Permission gate.** `IMCOPCommand` (`:17`) [VERIFIED].
* **Matching row.** **None.** A flag for a feature that does not exist.

### `/minecolonies colony blastprotection <colony> [on|off]`

* **What it does.** Turns explosion shielding on or off for one colony, reporting the server-wide
  policy alongside. Bare, reports.
* **How it answers.** `sendSuccess` for the report (`CommandColonyBlastProtection.java:47-51`) and the
  set (`:72-73`) [VERIFIED].
* **Player required?** No. **Permission gate.** `IMCOPCommand` (`:30`) [VERIFIED].
* **Matching row.** **None.** Damage policy is a server rule, and §5 non-goal 7 keeps config out.

### `/minecolonies help`

* **What it does.** Prints two translated lines and two clickable URLs.
* **How it answers.** Four `sendSuccess` calls (`CommandHelp.java:34`, `:37`, `:38`, `:39`)
  [VERIFIED].
* **Player required?** **Yes, as a gate only** — `getEntity() instanceof Player` at `:29-32`, after
  which the output goes to the source rather than the player [VERIFIED]. A console caller gets
  nothing, for no reason.
* **Permission gate.** `IMCCommand` (`:14`) [VERIFIED].
* **Matching row.** **None.** `Api.version()` (§2.14, Tier 1) is the API's equivalent of "which thing
  am I talking to"; documentation links are not an API concern.

### `/minecolonies boatspeed [<blockspersecond:double>]`

* **What it does.** Reads and writes the `boatSpeed` server config value, persisting it to
  `minecolonies-server.toml`, and reports the in-water damping factor alongside so the effective speed
  is not a surprise.
* **How it answers.** `sendSuccess` for the report (`CommandBoatSpeed.java:65-69`) and the set
  (`:89-92`) [VERIFIED]. The set reads the value back after writing, because `DoubleValue#set` clamps
  (`:86-88`) [VERIFIED].
* **Player required?** No. **Permission gate.** `IMCOPCommand` (`:36`) [VERIFIED].
* **Matching row.** **None** for the write — §5 non-goal 7 is explicit that `Colonies.config()` reads
  and does not write. The value itself belongs in `ConfigReport` if a caller ever needs it, and
  `ConfigReport` as specified (§3.15) does not carry it.

### `/minecolonies forceunloadchunks`

* **What it does.** Clears the force-load flag from every visible chunk in the caller's level.
* **How it answers.** `MessageUtils` with a bare English literal
  (`CommandUnloadForcedChunks.java:37`) [VERIFIED].
* **Player required?** **Yes, in creative.** The precondition is overridden to
  `sender instanceof Player && ((Player) sender).isCreative()` (`:44-48`) [VERIFIED] — the only
  command in the tree whose gate is creative mode alone, with no op check at all, and therefore the
  loosest destructive command here.
* **Permission gate.** As above; `IMCCommand` is the declared interface (`:19`) but its precondition
  is replaced [VERIFIED].
* **Matching row.** **None.** Chunk loading is not modelled.

### `/minecolonies prune-world-now <stage:int≥1> [<additional block protection radius:int 100..5000>]`

* **What it does.** Deletes every region file in the caller's dimension that is far enough from every
  colony building, after being invoked four times — the first three invocations only print a warning
  (`CommandPruneWorld.java:68-72`) [VERIFIED].
* **How it answers.** `sendSuccess` for the warning (`:70`), per deleted or undeletable file (`:114`,
  `:119`) and for the total (`:126`) [VERIFIED], all bare English literals except the warning.
* **Player required?** No; the dimension comes from the source (`:78`) [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:26`) [VERIFIED].
* **Matching row.** **None,** emphatically. It deletes world files.

### `/minecolonies pathstats [on|off|reset]`

* **What it does.** Reports how long path searches wait in the shared single-thread pool, how long
  they take, how many never arrive, the pool's occupancy and queue peak, and a per-job-type table;
  `on`/`off` toggle sampling and `reset` clears the counters.
* **How it answers.** Through the same `Report` helper shape as `diagnose`: `sendSuccess(..., false)`
  per line (`CommandPathStats.java:412`, `:437`), plus `sendSuccess` for the three switches (`:290`,
  `:303`) [VERIFIED]. The whole report also goes to the log.
* **Player required?** No.
* **Permission gate.** `IMCOPCommand` (`:40`) [VERIFIED].
* **Matching row.** **None.** It measures a server thread pool, not a colony, and its own class
  comment says worker-side waiting is deliberately left to `colony diagnose` (`:36-38`) [VERIFIED].
  That division is right and the API should keep it: §2.5 answers "why is this worker idle", and "the
  pathfinder is saturated" is a server health question that belongs to a profiler.

### `/mc trackPath <entity:entities>`, `/minecolonies citizens trackPath <colony> <citizenID>`, `/minecolonies citizens trackPathType <pathjobname:word>`

* **What they do.** Toggle path-rendering for an entity, for a citizen, or for every path job whose
  class name contains a string, by writing the caller's UUID into a static map in `PathfindingUtils`
  and sending a `SyncPathMessage` to the caller's client.
* **How they answer.** `sendSuccess` for enabled/disabled (`CommandEntityTrack.java:54`, `:60`;
  `CommandCitizenTrack.java:67`, `:73`) and `sendSystemMessage` with bare literals
  (`CommandTrackType.java:34`, `:39`) [VERIFIED].
* **Player required?** **Yes, a real client, in all three.** `CommandEntityTrack.java:36-40` and
  `:56`, `CommandCitizenTrack.java:41-45` and `:69`, `CommandTrackType.java:25-28` and `:33`, `:38`
  [VERIFIED]. The state they write is keyed by player UUID and the output is a rendering packet.
* **Permission gate.** `CommandEntityTrack` and `CommandCitizenTrack` are `IMCColonyOfficerCommand`
  (`:26`, `:31`); `CommandTrackType` is `IMCOPCommand` (`:15`) [VERIFIED].
* **Matching row.** **None,** deliberately not. §5 non-goal 3 forbids anything that draws on a client
  and non-goal 6 forbids exposing pathfinding.

### `/mc toggleDebugging <playername:gameprofile>`

* **What it does.** Toggles a per-player debug flag in `DebugPlayerManager` and, if the player is
  online, sends them a `DebugEnableMessage` and a chat line.
* **How it answers.** `sendSuccess` with bare English literals (`CommandToggleDebug.java:127`,
  `:131`) [VERIFIED].
* **Player required?** No for the caller; the target need not be online (`:134-147` is guarded)
  [VERIFIED].
* **Permission gate.** `IMCOPCommand` (`:22`) [VERIFIED].
* **Matching row.** **None.** A client rendering flag.

---

## The methods, in one place

Every method proposed anywhere above, once. Nothing appears here that has no contract in a per-command
entry, and no contract above is absent here. Ordered by api-needs.md block; the last column names the
numbered section of this document where the full contract — signature, parameters, effect and return
fields — lives.

`R` is read-only and mutates nothing; `M` mutates. **new** marks a method §2 of api-needs.md does not
name; **+n** marks a return type extended by `n` fields beyond what §3 of api-needs.md gives it.

| block | handle | method | arguments | returns | R/M | §
|---|---|---|---|---|---|---|
| 2.1 | `Colonies` | `list()` | — | `List<ColonySummary>` | R | 1 |
| 2.1 | `Colonies` | `nearest(...)` | `WorldPos pos`, `int maxBlocks` | `List<ColonyDistance>` | R | 1 |
| 2.1 | `Colonies` | `ownedBy(...)` **new** | `PlayerRef owner` | `List<ColonySummary>` | R | 1 |
| 2.1 | `colony` | `identity()` | — | `ColonyIdentity` | R | 1 |
| 2.1 | `colony` | `progress()` | — | `ColonyProgress` | R | 1 |
| 2.1 | `colony` | `capacity()` | — | `ColonyCapacity` | R | 1 |
| 2.2 | `building` | `buildEligibility(...)` | `int targetLevel` | `BuildEligibility` | R | 2 |
| 2.2 | `building` | `requestRepair()` | — | `BuildOrderResult` | M | 2 |
| 2.4 | `colony` | `citizens()` | — | `List<CitizenSummary>` | R | 4 |
| 2.4 | `colony` | `unemployed()` | — | `List<CitizenSummary>` | R | 4 |
| 2.4 | `colony` | `professionCoverage()` | — | `List<ProfessionCoverage>` | R | 4 |
| 2.4 | `colony` | `bestHomeFor(...)` **new** | `CitizenId citizen` | `BuildingId` | R | 4 |
| 2.4 | `citizen` | `info()` | — | `CitizenInfo` **+2** | R | 4 |
| 2.4 | `citizen` | `needs()` | — | `CitizenNeeds` | R | 4 |
| 2.4 | `citizen` | `saturation()` | — | `double` | R | 4 |
| 2.4 | `citizen` | `assignHome(...)` | `BuildingId residence` | `CommandOutcome` | M | 4 |
| 2.5 | `citizen` | `workStatus()` | — | `WorkStatus` | R | 4 |
| 2.5 | `colony` | `workOverrides()` **new** | — | `List<WorkOverrideState>` **new type** | R | 5 |
| 2.5 | `colony` | `setWorkOverride(...)` **new** | `WorkOverrideKind kind`, `boolean on` | `CommandOutcome` | M | 5 |
| 2.6 | `colony` | `resetRequestSystem()` | — | `CommandOutcome` | M | 6 |
| 2.7 | `colony` | `stock()` | — | `List<StockEntry>` | R | 7 |
| 2.7 | `colony` | `warehouseFill()` **new** | — | `List<WarehouseFill>` **new type** | R | 7 |
| 2.7 | `colony` | `stockAge()` **new** | — | `List<StockAge>` **new type** | R | 7 |
| 2.7 | `colony` | `craftableBy(...)` | `ItemSpec item` | `List<CraftingOption>` | R | 7 |
| 2.8 | `farm` | `fields()` | — | `List<FieldInfo>` **+1** | R | 8 |
| 2.8 | `field` | `setSeeds(...)` | `List<ItemSpec> seeds` | `CommandOutcome` | M | 8 |
| 2.9 | `research` | `exclusiveChoices()` | — | `List<ExclusiveChoice>` | R | 9 |
| 2.10 | `colony` | `triggerRaid(...)` | `RaiderType type`, `int size`, `double strength`, `WorldPos where`, `RaidTiming timing` | `CommandOutcome` | M | 10 |
| 2.10 | `colony` | `currentRaid()` | — | `RaidStatus` **+1** | R | 10 |
| 2.10 | `colony` | `endRaid()` **new** | — | `CommandOutcome` | M | 10 |
| 2.10 | `colony` | `raidHistory(...)` | `int maxEntries` | `List<RaidRecord>` **+1** | R | 10 |
| 2.10 | `colony` | `raidForecast()` | — | `RaidForecast` **+1** | R | 10 |
| 2.10 | `colony` | `setRaidsEnabled(...)` **new** | `boolean on` | `CommandOutcome` | M | 10 |
| 2.10 | `raid` | `remainingRaiders()` | — | `List<RaiderPosition>` | R | 10 |
| 2.11 | `Colonies` | `chunkOwner(...)` | `ChunkRef chunk` | `ChunkOwner` **+2** | R | 11 |
| 2.11 | `Colonies` | `territories(...)` **new** | `String dimension` | `List<HostileTerritory>` **new type** | R | 11 |
| 2.13 | `Colonies` | `ranksOf(...)` **new** | `PlayerRef player` | `List<PlayerColonyRank>` **new type** | R | 13 |
| 2.13 | `Colonies` | `delete(...)` | `ColonyId id`, `boolean deleteBuildings` | `CommandOutcome` | M | 13 |
| 2.13 | `colony` | `settings()` | — | `ColonySettings` | R | 13 |
| 2.13 | `colony` | `setFreeMode(...)` | `boolean on` | `CommandOutcome` | M | 13 |
| 2.13 | `colony.permissions()` | `setRank(...)` | `PlayerRef player`, `PermissionRank rank` | `CommandOutcome` | M | 13 |
| 2.13 | `colony.permissions()` | `setOwner(...)` **new** | `PlayerRef player` | `CommandOutcome` | M | 13 |
| 2.13 | `colony.permissions()` | `abandon()` **new** | — | `CommandOutcome` | M | 13 |
| 2.13 | `colony.permissions()` | `enforced()` **new** | — | `boolean` | R | 13 |
| 2.14 | `colony` | `problems()` | — | `List<ColonyProblem>` | R | 14 |
| 2.14 | `colony` | `progression()` | — | `ProgressionReport` | R | 14 |
| 2.14 | `citizen` | `problems()` | — | `List<WorkProblem>` | R | 14 |
| 2.14 | `building` | `problems()` | — | `List<WorkProblem>` | R | 14 |

**Forty-eight methods**: thirty-five read-only and thirteen mutating. Thirteen are marked **new**,
which is a larger number than the eight new verbs counted in the coverage table at the top of this
document, and deliberately so: the units differ. Eight *commands* propose a verb the catalogue lacks;
those eight yield thirteen *methods*, because a read and its matching write are two methods, and
because `colony workoverride` and `colony warehousestock` each yield more than one.

**Thirty-four distinct return types.** Two are primitives (`double`, `boolean`). Twenty-seven come
from §3 of api-needs.md unchanged or extended — `ColonySummary`, `ColonyDistance`, `ColonyIdentity`,
`ColonyProgress`, `ColonyCapacity`, `ColonySettings`, `BuildEligibility`, `BuildOrderResult`,
`CitizenSummary`, `CitizenInfo`, `CitizenNeeds`, `ProfessionCoverage`, `WorkStatus`, `WorkProblem`,
`StockEntry`, `CraftingOption`, `FieldInfo`, `ExclusiveChoice`, `RaidStatus`, `RaidForecast`,
`RaidRecord`, `RaiderPosition`, `ChunkOwner`, `ColonyProblem`, `ProgressionReport`, `BuildingId`,
`CommandOutcome`. **Five are new** and are defined in the entries that propose them:
`WorkOverrideState` (section 5), `WarehouseFill` and `StockAge` (section 7), `HostileTerritory`
(section 11), `PlayerColonyRank` (section 13).

**Six of the twenty-seven need extending**, and the reason is the same each time — the tree already
holds a fact the catalogue's record has nowhere to put: `CitizenInfo` (two parent ids), `FieldInfo`
(one seed becomes a list of up to five), `RaidStatus` (raiders loaded as distinct from raiders alive),
`RaidRecord` (the difficulty multiplier), `RaidForecast` (whether raids are enabled at all) and
`ChunkOwner` (static claims and claiming buildings).

**Fields that could not be pinned to a source in the tree.** These are the return fields a caller
would ask for and that nothing today produces; every one is stated again in the entry it belongs to.

| method | fields with no source |
|---|---|
| `colony.identity()` | `foundedGameTime` — the colony counts days, it does not record when it began |
| `colony.progress()` | nine of fourteen: every building count, type count and landmark level except `warehouseCount` |
| `colony.capacity()` | `beds`, `bedsFree` colony-wide; `capSource`, discarded by a `min()` of three terms |
| `colony.citizens()`, `citizen.needs()` | `happiness`, computed only on the way to a client packet |
| `citizen.needs()` | `daysWithoutHome`, `nightsWithoutBed`, `requiredFoodTier`; `health` and `maxHealth` only through a loaded entity |
| `citizen.workStatus()` | `lastProductiveGameTime` — nothing in the model is timestamped |
| `building.buildEligibility(int)` | `maxBuilderHutLevel` — only a per-level yes/no exists, never a maximum |
| `colony.stock()` | `inBuildings`, `inCitizenInventories` — no colony-wide traversal |
| `farm.fields()` | `hydrated`, `state` |
| `colony.raidHistory(int)` | `durationTicks` — a start time is recorded, an end time is not |
| `colony.progression()` | eight of twelve counts, plus `stage`, which is a judgement over all of them |

Eleven methods, and the pattern behind them is one sentence long: **the tree records states, not
histories, and answers per-building questions but not per-colony ones.**

---

## 16. What the command tree gives us for free

The work list. Every entry below is an api-needs.md row whose working implementation is already inside
a command executor, ranked cheapest first — cheapest meaning "least code has to move". The tier is the
catalogue's.

**Tier A — nothing but a wrapper.** The verb is a method call that already takes no player and already
returns something usable; the API method is a signature and a record around it.

| # | API row | tier | where the implementation is | what changes |
|---|---|---|---|---|
| 1 | `colony.setFreeMode(boolean)` and `settings().freeModeOn` | 3 / 1 | `CommandColonyFreeMode.java:35`, `:55` | Two calls, one predicate and one setter; both already player-free. |
| 2 | `colony.resetRequestSystem()` | 2 | `CommandRSReset.java:33` | One call. Add a count to the outcome. |
| 3 | `colony.raidHistory(int maxEntries)` | 3 | `CommandColonyRaidsInfo.java:37` | `getAllRaids()` already returns records. Add the bound and drop the `RaidManager` cast. |
| 4 | `colony.triggerRaid(...)` | 3 | `CommandRaid.java:459`, `:492` | `raiderEvent` takes a settings record and returns `RaidSpawnResult`; map that onto `OutcomeCode`. Widen the row's signature to carry size, strength, location and timing. |
| 5 | `colony.permissions().setRank(PlayerRef, PermissionRank)` | 2 | `CommandSetRank.java:76`, `CommandAddOfficer.java:56` | One call. Drop the online-player requirement, which exists only for a side effect. |
| 6 | `Colonies.delete(ColonyId, boolean)` | 3 | `CommandDeleteColony.java:172-173` | One call plus the backup. Settle the operator-versus-officer gate. |
| 7 | `Colonies.chunkOwner(ChunkRef)` | 1 | `CommandShowClaim.java:67`, `:68`, `:86` | Three static utilities that take a chunk. Return a record instead of a `MutableComponent`. |
| 8 | `colony.workOverrides()` (new) | — | `CommandColonyWorkOverride.java:68` | An enum plus a predicate per constant. |
| 9 | `colony.citizens()` | 1 | `CommandCitizenList.java:62` | `getCitizens()` plus the `CitizenSummary` record. |

**Tier B — the body exists, in the wrong shape.** The logic is right and is already separated from the
chat, or nearly so; the work is a record and a signature.

| # | API row | tier | where the implementation is | what changes |
|---|---|---|---|---|
| 10 | `citizen.assignHome(BuildingId)` plus a `bestHomeFor` read | 1 | `CommandColonyRehouse.java:159`, `:281` | `findMove` is already `private static` and pure. Move it onto the colony handle and split the read from the write. |
| 11 | `building.requestRepair()` | 2 | `CommandColonyRepairAll.java:167` | `IBuilding#requestRepair` already returns `WorkOrderRequestResult`. Wrap it as `BuildOrderResult`. |
| 12 | `field.setSeed(...)` and `farm.fields()` | 2 | `CommandColonyFieldSeeds.java:71`, `:212` | The traversal and the plantability predicate exist. Return `FieldInfo` records and add hydration and state. |
| 13 | `colony.progress()` and `colony.capacity()` | 1 | `CommandColonyInfo.java:61-78` | The homeless-and-children count is the only real logic; it becomes record fields. |
| 14 | `colony.professionCoverage()` and `colony.unemployed()` | 2 / 1 | `CommandCitizenFill.java:373`, `:132-163` | Three loops that currently join names into strings. |
| 15 | `research.exclusiveChoices()` | 2 | `CommandColonyResearch.java:130-134`, `:188-200` | Separate the exclusion walk from `complete(...)`; `collectSubTree` is already non-mutating. |
| 16 | `colony.craftableBy(ItemSpec)` | 2 | `CommandColonyTeachRecipes.java:64`, `:96` | Invert the loop from building-major to item-major. |
| 17 | `colony.progression()` | 2 | `CommandColonyPrintStats.java:53-107` | Four of twelve fields exist. The other eight are filters over `getBuildings()` that nothing performs. |

**Tier C — large, and the tree is the best starting point there is.**

| # | API row | tier | where the implementation is | what changes |
|---|---|---|---|---|
| 18 | `citizen.workStatus()`, `citizen.problems()` | 1 | `CommandCitizenInfo.java:79-193`, `CommandColonyDiagnose.java:248-300` | Nine `List<String>` become records; the severity ordering already exists; the activity read must stop switching history on as a side effect. |
| 19 | `building.problems()` | 1 | `CommandColonyDiagnose.java:475-520` | The building checks are already building-major, unlike everything else in the tree — this is the one place the empty-hut case is reachable. |
| 20 | `colony.problems()` | 1 | `CommandColonyDiagnose.java:107-232` | The whole traversal, aggregated and sorted. The blocker is not the sweep, it is that nothing is timestamped: `OBSERVATIONS` (`:91`) is a per-invocation workaround and `formatAge` says `"new"` (`:685`) when it cannot tell. |
| 21 | `building.buildEligibility(int)` | 1 | `CommandColonyRepairAll.java:138-168` | Four refusals are already evaluated without acting. The command's own comment names the rest as undecidable without acting, which is the exact scope of the remaining work. |

Two structural observations that apply to the whole list.

**One file already does it right.** `ColonyChunkReport` (`mc/core/commands/colonycommands/ColonyChunkReport.java`)
is a value object built by a static `gather(server, colony)` (`:252`) with a separate
`send(CommandSourceStack)` (`:332`) [VERIFIED], shared between two commands so they cannot drift apart.
That is the shape every entry above is asking for, and it is worth pointing at when the work starts.

**Two more report helpers are one refactor from being that.** `CommandColonyDiagnose.Report` and
`CommandPathStats.Report` are private inner classes with the same three methods
(`emit`, `section`, `getLog`) and near-identical bodies (`CommandColonyDiagnose.java:728-802`,
`CommandPathStats.java:382-449`) [VERIFIED].
`CommandColonyRepairAll`, `CommandColonyRehouse` and `CommandColonyRestoreHuts` each carry their own
copy of the same `emit` (`:321`, `:418`, `:384`) [VERIFIED]. Five copies of one renderer is a sign that the
data and the rendering want separating anyway.

---

## 17. What the commands do that the API does not want

Forty-four of the seventy-five commands have no API form and should not acquire one. They fall into
seven kinds. Where a command appears under two kinds — `scan` is both world editing and client work,
`aircraft` is both a mod-compat listing and a teleport — it is counted once. Sub-verbs of a command
counted elsewhere (`raid … tp`, `antiair … tp`) are named for illustration and not counted again.

**1. Entity control.** `citizens kill`, `citizens teleport`, `citizens walk`, `citizens reload`, and the
seven `kill` commands. §5 non-goal 6 of api-needs.md states that the API does not move citizens block
by block, does not steer entities and does not expose the AI's task state; `recallWorkers` and
`setPatrolMode` are named as the only two movement-adjacent commands allowed, and both are switches the
game itself offers. Removing entities from the world additionally breaks §2.8: a colony API that can
clear the map of cows makes every food question unanswerable.

**2. Creating things from nothing.** `citizens spawnNew`, `citizens fill` (the spawning half),
`colony growChildren`, `colony research completeall`, `colony teachRecipes`, `citizens maxstats`,
`citizens heal`, `citizens modify saturation`. §5 non-goal 5 forbids the API bringing anything into
existence, and each of these skips a mechanic the rest of the catalogue exists to drive: beds and the
population cap (§2.1), the ageing timer (§2.4), 587 hours of research (§2.9), the crafting-teaching
GUI (§2.7), the level curve and the dining hall (§2.4, §2.8). Their *reads* are wanted and are listed
in §16; their writes are not.

**3. World editing.** `scan`, `prune-world-now`, `colony restorehuts`, `colony claim`,
`colony reclaimchunks`, `colony territory create|grow|delete`. §5 non-goal 4 allows exactly one world
edit, `Sites.clearArea`, and says so in order to prevent this class of call. Deleting region files is
several steps beyond even that. Granting claim directly defeats §2.11's whole model, in which the claim
is derived from buildings and their levels.

**4. Server administration and save files.** `backup`, `colony export`, `colony loadBackup`,
`colony loadAllColoniesFromBackup`, `colony chunkstatus`, `colony forceloadclaims`,
`forceunloadchunks`, `colony setDeleteable`, `boatspeed` (the write), `colony blastprotection`,
`requestsystem-reset-all`. These are decisions about a server, not about a colony. §5 non-goal 7 puts
config writing outside the API explicitly; chunk tickets and region files are not modelled at all; and
`requestsystem-reset-all` is a loop over a per-colony call, which a caller with `Colonies.list()` can
and should write itself.

**5. Anything that needs a client.** `colony teleport`, `home`, `raid … tp`, `antiair … tp`,
`aircraft … tp`, `trackPath`, `citizens trackPath`, `citizens trackPathType`, `toggleDebugging`,
`scan`. §5 non-goal 3 is unambiguous: nothing in the API runs on a client, renders anything, draws an
overlay or sends a packet to a player's screen. Every one of these either moves a human's avatar or
turns on a rendering.

**6. Third-party mod compatibility.** `aircraft`, `colony antiair`. Both reach the world through
`Compatibility.aircraftCompat`, whose default implementation reports nothing. The catalogue models
MineColonies, and a call whose answer is always empty without another mod installed does not belong in
it. The one thing worth stealing from `antiair` is its refusal style (`CommandColonyAntiAir.java:499-513`)
[VERIFIED], which is already §4's `CommandOutcome` contract in everything but type.

**7. Debug switches on the colony itself.** `colony protection` (write), `colony keepbuildings`
(write), `colony workoverride` (write), `colony resetStats`. Each suspends or clears something the rest
of the API is supposed to observe. Their *reads* are a different matter and three of them are argued
for above: whether protection is enforced, how many buildings are at risk, and which work overrides are
thrown all change what a caller should conclude from `colony.problems()` and
`permissions().can(...)`. A caller that cannot see the switch will diagnose the colony wrongly.

---

## 18. Where this document and `api-inventory.md` disagree

Four places, one of them structural.

**1. "COMMAND EXISTS" in `api-inventory.md` does not mean a command.** That document defines the state
as "the verb is implemented, but not as a callable method: it lives inside a packet handler, needs a
player, returns `void`, and reports refusal as chat" (`api-inventory.md:19-20`) [VERIFIED]. Thirteen of
its fourteen rows so labelled cite a class under `mc/core/network/messages/` — `CreateColonyMessage`,
`TriggerSettingMessage`, `BuildRequestMessage`, `WorkOrderChangeMessage`, `PauseCitizenMessage`,
`HireFireMessage` (twice), `RecallCitizenHutMessage`, `AssignUnassignMessage`,
`UpdateRequestStateMessage`, `TransferItemsRequestMessage`, `AssignFilterableItemMessage`,
`DirectPlaceMessage` — and the fourteenth, `building.requestUpgrade`, cites a `protected` method on
`AbstractBuilding` [VERIFIED, the fourteen rows at `api-inventory.md:78`, `:83`, `:102`, `:103`, `:107`,
`:137`, `:162`, `:163`, `:164`, `:165`, `:211`, `:233`, `:250`, `:284`]. **No COMMAND EXISTS row cites a
file under `mc/core/commands/`.** The label is therefore best read as "GUI message handler exists".

The command tree is not absent from that document — files under `mc/core/commands/` are cited at
`api-inventory.md:80`, `:100`, `:117`, `:171`, `:187`, `:189`, `:206`, `:207`, `:212`, `:229`, `:266`,
`:282`, `:302`, `:315`, `:323` [VERIFIED] — but always inside the *what is missing* column of a PARTIAL
or MISSING row, as "the nearest thing that exists", never as the state itself. A reader scanning the
state column will not find a single command behind it. This document is that missing half: seventy-five
verbs that already run with no client at all, nine of which are a wrapper away from a catalogue row.

**2. Nine rows of §16 above are cheaper than the inventory's own state for them implies.** The clearest
are `Colonies.chunkOwner` (§2.11, Tier 1), which this document places in Tier A because
`CommandShowClaim` already reads all three claim structures from a bare chunk, and `colony.progress()`
(§2.1, Tier 1), where the inventory says "no aggregate" and cites `CommandColonyInfo.java:50-107` as
"the nearest thing that exists" [VERIFIED] — which is correct, but understates it: the homeless count
that file performs is not merely *near* the row, it is the only correct implementation of it in the
tree, because it is the one place that does not derive homelessness from population minus beds.

**3. Two rows the inventory does not cover at all have working command implementations.**
`colony.resetRequestSystem()` (§2.6, Tier 2) and `colony.setFreeMode(boolean)` (§2.13, Tier 3) are both
one-line wrappers over `CommandRSReset.java:33` and `CommandColonyFreeMode.java:55` [VERIFIED]. Neither
appears in `api-inventory.md`, and correctly so — that document is scoped to Tier 1 (`api-inventory.md:23-24`)
[VERIFIED]. This is a scope gap rather than a contradiction, but it is worth stating that the two
cheapest complete rows in the catalogue are both outside the inventory's window.

**4. One agreement worth confirming rather than a disagreement.** The inventory cites
`CommandColonyRepairAll.java:138-167` as having "already tried this and got three of the refusals out in
preview mode" [VERIFIED]. Reading the method confirms it: `repair(...)` runs from `:138` to `:168`, and
it evaluates four refusals without acting, not three — level 0 (`:140`), deconstructed (`:144`),
already queued (`:149`) and missing blueprint (`:161-163`) [VERIFIED]. The comment the inventory quotes
is at `:155-160` [VERIFIED] and says exactly what it is quoted as saying.

---

## 19. Objections

Three, stated once and not argued.

**1. `raid.remainingRaiders()` should not be gated on spies.** §2.10 makes the call fail when "spies not
active". The server knows where every raider is; `CommandRaid`'s `where` verb prints exactly that with
no spy check (`CommandRaid.java:506-560`) [VERIFIED]. Gating a server-side query on an in-game item is
importing a GUI concession into an API that has no GUI.

**2. `PermissionRank` should not be a fixed enum.** §3.14 declares five constants. This tree's colonies
carry a *map* of ranks with ids and names, and `/mc colony setRank` matches against it by name
(`CommandSetRank.java:61-68`) [VERIFIED]. A caller handed the enum cannot name a custom rank, and
`setRank` cannot set one.

**3. `field.setSeed(ItemSpec)` has the wrong arity.** §2.8 gives the scarecrow one seed. `FarmField`
holds a list bounded by `FarmField.MAX_SEEDS`, and the command has four verbs — add, set, remove, clear
— because of it (`CommandColonyFieldSeeds.java:245-264`) [VERIFIED]. The row should take a list.
