# A colony control API: object handles for pull, events for push

Design study and estimate, not an implementation. Date: 2026-08-24. Tree: `26.3/` in this repo.
No API code exists yet; nothing in this document has been built.

Evidence standard, same as the other studies in this directory:

* **[VERIFIED]** — the source was read, the `file:line` is real and says what is claimed.
* **[UNCHECKED]** — inference or estimate, not observed.

`26.3/src/main/java/com/minecolonies/` is abbreviated to `mc/`.

---

## 0. The answer

**An object API is the right shape, and it is the cheaper shape.** Roughly **7000 lines** for full
coverage; **~2000** for a first stage that is already useful to a human debugging the mod, before a
single byte goes over a network.

The colony is not a document to be dumped. It is a tree of objects that already exist on the server,
each with an identity that already survives a save. An API that mirrors that tree — `colony`,
`building`, `citizen`, each with typed methods — needs no snapshots, no deltas, no compaction layer
and no polling loop. A caller asks for exactly one thing and gets exactly that thing back.

Two directions, and only two:

* **Pull — method calls on handles.** `building.level()`, `building.hire(id)`, `citizen.aiState()`.
* **Push — events, and only the ones that matter.** Building finished, citizen died, research done.
  Nothing streams by itself. The mod ticks internally as it always has; the tick is not visible
  outside.

This matters beyond tidiness. The same façade is what a person uses to debug: a command that calls
any method from the server console reproduces a player's bug in one line, with no client, no GUI and
no guesswork about what the interface actually sent.

## 1. Shape

### 1.1 Entry

```java
ColonyApi.colony(int id, ResourceKey<Level> dim)   // -> IColonyHandle
ColonyApi.colonyAt(BlockPos pos)
ColonyApi.colonies()
```

Handles are thin views over the live server objects, resolved on each call and never cached across
ticks. A handle to a deleted colony throws a typed error rather than returning stale data.

### 1.2 Colony

```java
int      id();
String   name();
BlockPos center();
int      citizenCount();   int maxCitizens();
double   happiness();

List<BuildingRef>  buildings();      // {pos, type, level} and nothing else
List<CitizenRef>   citizens();       // {id, name, job}
IBuildingHandle    building(BlockPos pos);
ICitizenHandle     citizen(int id);

boolean  claimChunk(ChunkPos pos);
boolean  unclaimChunk(ChunkPos pos);
int      orderBuild(BlockPos hut, BuildMode mode, int targetLevel);
List<IWorkOrderHandle> workOrders();
IResearchHandle        research();
IPermissionsHandle     permissions();
List<Problem>          problems();
```

`problems()` is the replacement for a polling loop: it answers "what is wrong right now", and it is
the same data `/mc colony diagnose` already collects.

### 1.3 Building

```java
BlockPos pos();  String type();
int      level();  int maxLevel();
boolean  isBuilt();
BuildProgress progress();                    // stage + what is missing
List<CitizenRef> workers();  int maxWorkers();
boolean  hire(int citizenId);  boolean fire(int citizenId);
HiringMode hiringMode();  void setHiringMode(HiringMode m);
List<Recipe> recipes();
boolean  teachRecipe(Recipe r);  boolean forgetRecipe(int id);
List<Setting> settings();                    // {key, type, value, allowed}
boolean  setSetting(String key, Object value);
List<ItemStack> stock();
void     rename(String name);
```

### 1.4 Citizen

```java
int id();  String name();  String job();
Map<Skill, Integer> skills();                // 11 skills [VERIFIED: mc/api/entity/citizen/Skill.java]
double happiness();  double saturation();
BlockPos position();  BlockPos home();  BlockPos workplace();
String aiState();  Duration aiStateHeldFor();
List<ItemStack> inventory();
void pause(boolean p);  void restart();  void recall();
```

Then the same for work orders, requests, fields, research and permissions. About **150 methods**
in total. [UNCHECKED — counted from the message and command surfaces, not from written code.]

### 1.5 Events

An event carries an identifier and a handful of facts. It never carries the object.

```json
{"e":"building_done","pos":[100,64,200],"type":"builder","lvl":3}
```

If the consumer wants more it calls the handle. One source of truth, and a stream that stays cheap
enough to leave running.

## 2. What already exists

### 2.1 The verbs are written — 83 server messages

[VERIFIED] `mc/core/network/messages/server/` holds 83 files, `client/` 40. Grouped by meaning:
construction 13, colony identity 11, hiring and citizens 11, building settings 12, warehouse and
requests 13, guards and raids 5, fields 4, permissions 9, research 1, the rest UI and telemetry.

Every one of them is a verb the API needs. They do not have to be invented, only re-housed.

### 2.2 Permissions are already UUID-based, not Player-based

[VERIFIED] `mc/api/colony/permissions/IPermissions.java:88` — `boolean addPlayer(UUID id, String
name, Rank rank)`; `:28` — `hasPermission(Rank, Action)` gates on the rank, not on a player entity.
[VERIFIED] `mc/core/colony/permissions/Permissions.java:389` reads the owner list back from NBT as
`UUID + name + rank`, so a subject registered this way survives a restart.

A bot is therefore a normal colony member with its own rank. The one thing it cannot be is the
owner: `setOwner` takes a `Player`.

### 2.3 There is room for API-specific permissions

[VERIFIED] `mc/api/colony/permissions/Action.java` — 27 actions, `long flag = 0x1L << bit` (`:57`),
highest bit in use 30. Bits 31–63 are free, so new API-only actions can be added without changing
the save format.

### 2.4 The default permission on every colony message is one constant

[VERIFIED] `mc/core/network/messages/server/AbstractColonyServerMessage.java:61` returns
`Action.MANAGE_HUTS` for everything unless overridden, and the check runs at `:91`.

### 2.5 Nineteen events already exist

[VERIFIED] `mc/api/eventbus/events/` — 23 files, 4 of them abstract bases, so 19 concrete events:
colony created/deleted/renamed/reflagged/recoloured, **building added/removed/constructed**
(`BuildingConstructionModEvent` carries both the building and the work order), **citizen
added/removed/died/job-changed**, player rank changed, player entering/leaving, colony manager
loaded/unloaded, custom recipes reloaded, and one client-only view update.

Roughly half of what a bot needs is published today.

### 2.6 The observation prototype is written and is 819 lines

[VERIFIED] `mc/core/commands/colonycommands/CommandColonyDiagnose.java` — 819 lines, entirely
server-side, no player required. It already collects worker AI states with how long each has been
held, stalled requests, work orders with no builder, couriers no warehouse adopted, and patrol
positions. It lacks exactly two things: a structure instead of flat text, and a return value instead
of `return 1`.

### 2.7 The insertion point is one line

[VERIFIED] `mc/core/MineColonies.java:212-217` — a block of `register()` calls. Config is built
earlier, at `:129`. A new subsystem is one more line in that block.

### 2.8 No new dependencies, and no mixins

[VERIFIED] Gson arrives transitively from Minecraft and is already used in this tree
(`mc/core/event/ColonyStoryListener.java:41`). `com.sun.net.httpserver` ships in the JDK and Java
here is 25. Nothing in this design needs bytecode injection: every hook is a public API or a Fabric
event.

### 2.9 Server-side reading has no blind spots

The client view omits a great deal — worker AI state, the contents of building chests, raid
scheduling, server-side resolvers. All of it is reachable directly from `IColony`. Building the API
on the server objects rather than on `IColonyView` makes that entire category of gap disappear.

## 3. What has to be built

**Reading from outside does not exist at all.** There is no request/response message anywhere;
colony state only ever leaves as an unsolicited push to a subscribed client. This is the single
largest piece of new work.

**Actions return nothing.** Each message is fire-and-forget; the result reaches the player as a chat
line. An API needs a real result per call.

**Building settings are opaque.** Everything configurable on a building travels through one
`TriggerSettingMessage` keyed by `ISettingKey<?>`. Listing which settings a building has, and which
values are legal, is not possible from outside today. About 40 module types need a bridge.

**Missing events.** Research completed, building level increased, work order created and completed,
request stalled, raid started and ended, citizen sick or starving, colony under attack. Twelve to
eighteen new ones. [UNCHECKED — the count is a judgement, not a survey.]

## 4. Traps

**The event bus dispatches on the exact class.** [VERIFIED]
`mc/api/eventbus/DefaultEventBus.java:31` — `eventHandlersPerType.get(event.getClass())`. Subscribing
to a base type silently receives nothing. Subscribe to every concrete class.

**The event bus is not thread-safe.** [VERIFIED] same file `:19`, a plain `HashMap` of
`ArrayList`. Subscribe during init only.

**Nothing may run on an IO thread.** Every handle call touches live colony state and must be hopped
onto the server thread. Every event must be handed to a ring buffer and delivered from elsewhere, so
a slow or absent consumer can lose the tail of the stream but can never stall a tick.

**The API token must not live in `ServerConfiguration`.** [VERIFIED] BlockUI ships a server-to-client
config sync (`libs/blockui/26.2/src/main/java/com/ldtteam/common/config/`, exercised by
`ConfigSyncTest`), so anything placed there is handed to every client that connects. A separate file
or an environment variable.

**On an integrated server the lifecycle repeats.** `SERVER_STARTED` / `SERVER_STOPPING` fire many
times in one process during single-player, so the listener must be restartable and must release its
port on stop.

**Distance is barely checked today.** Only 4 of the 83 server messages check interaction range, and
`ownerOnly()` is overridden nowhere. Colony management is already possible from another dimension.
Convenient for a bot, but it means the access model rests entirely on the rank, and should be
designed deliberately rather than inherited by accident.

## 5. The fork in the road

The logic for every action currently sits inside the `onExecute` of a network message.

**Option A — the façade duplicates it.** Faster to start. Two copies of every rule, drifting apart
at the first fix, and "works in the GUI, not through the API" becomes routine.

**Option B — the logic moves into the façade and the message becomes a three-line caller.** Costs
about 800 lines of churn across the message files. In exchange the GUI button and the API call
physically cannot diverge, because they enter the same function — which is the whole debugging
argument for building this in the first place.

Option B, applied one verb at a time as each is touched, not as a single sweep.

## 6. Cost

[UNCHECKED — all of it. Estimates from the surfaces measured above.]

| Block | Lines |
|---|---|
| Handles and their methods (~150) | 2400 |
| Value types (`BuildingRef`, `BuildProgress`, `Problem`, `Setting`, …) | 400 |
| Lookup, entry point, server-thread hop | 300 |
| Caller identity and per-method permission checks | 250 |
| Building-settings bridge (~40 module types) | 250 |
| New events (12–18) and their publication sites | 500 |
| Outward event stream: subscribe, filter, buffer, deliver | 450 |
| Transport bridge: annotation → endpoint + generated schema | 700 |
| HTTP, authentication, lifecycle | 500 |
| `/mc api …` debug command | 200 |
| Tests | 1200 |
| **Total** | **≈7150** |

Plus ~800 lines of churn if option B is taken across all 83 messages.

The transport is not the expensive part. The expensive part is 150 careful signatures with real
argument validation; everything around them is about a third of the volume.

## 7. Stage 1, by file

**Target: the façade works, and a person can drive it from the console. No network at all.**

New package `com.minecolonies.api.control` (interfaces and value types) and
`com.minecolonies.core.control` (implementations).

| File | Lines | Contents |
|---|---|---|
| `api/control/ColonyApi.java` | 60 | Static entry: `colony(id, dim)`, `colonyAt(pos)`, `colonies()` |
| `api/control/IColonyHandle.java` | 120 | Colony surface from §1.2 |
| `api/control/IBuildingHandle.java` | 120 | Building surface from §1.3 |
| `api/control/ICitizenHandle.java` | 100 | Citizen surface from §1.4 |
| `api/control/IWorkOrderHandle.java` | 50 | Id, type, priority, claimant, stage, progress |
| `api/control/values/*.java` | 200 | `BuildingRef`, `CitizenRef`, `BuildProgress`, `Problem`, `ResourceNeed`, `SkillSet` as records |
| `api/control/ApiResult.java` | 60 | Success or typed failure; no exceptions across the boundary |
| `api/control/ApiError.java` | 40 | Error enum: no such colony, no such building, no permission, illegal argument, wrong state |
| `core/control/ColonyHandleImpl.java` | 350 | |
| `core/control/BuildingHandleImpl.java` | 300 | |
| `core/control/CitizenHandleImpl.java` | 250 | |
| `core/control/WorkOrderHandleImpl.java` | 80 | |
| `core/control/Actor.java` | 120 | Who is calling; binds a UUID to a rank and checks it per method |
| `core/control/ServerThread.java` | 60 | Hop a call onto the server thread and wait for its result |
| `core/control/ProblemCollector.java` | 200 | The collectors from `CommandColonyDiagnose`, returning `List<Problem>` |
| `core/commands/CommandApi.java` | 200 | `/mc api colony 1 building 100,64,200 progress` |
| **New code** | **≈2310** | |

Edits to existing files:

| File | Lines | Change |
|---|---|---|
| `core/MineColonies.java` | 1 | One `register()` in the callbacks block at `:212` |
| `core/commands/EntryPoint.java` | 1 | One node for `/mc api` |
| `core/commands/colonycommands/CommandColonyDiagnose.java` | ~40 | Text emission reads `ProblemCollector` instead of collecting inline |

The fifteen verbs worth having in stage 1: order a build, cancel a work order, set work-order
priority, claim and unclaim a chunk, hire, fire, set hiring mode, rename a building, set delivery
priority, pause a citizen, restart a citizen, recall a citizen, rename the colony, request a
delivery, start a research.

What stage 1 buys before any network exists: a player's bug reproduced from the console in one line,
`problems()` readable as data instead of as a wall of chat text, and tests that drive a colony with
no client in the loop.

## 8. Later stages

| Stage | Lines | Contents |
|---|---|---|
| 2 | ~1200 | Events outward, transport, generated schema. A bot can play from here. |
| 3 | ~2500 | Remaining methods, settings bridge, requests, research, fields |
| 4 | ~1200 | Tests, and moving message logic into the façade (option B) |

## 9. Still open

* Whether the transport is HTTP request/response with a separate event stream, or one duplex socket.
  HTTP is easier to debug by hand; a socket suits a long-lived bot.
* Whether the bot's rank is a stock officer or a purpose-made rank with its own bits from the free
  31–63 range. The latter is tidier and costs a migration.
* Whether `problems()` keeps the delta cache that `diagnose` uses for "how long has this been stuck".
  It is useful and it is a leak as written — the static map is never pruned when a colony is deleted.
* Field creation has no verb anywhere: fields are made by placing a scarecrow. Either the API places
  a block, or field creation stays outside it.
