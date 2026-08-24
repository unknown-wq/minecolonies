# PORT-STATUS — MineColonies → Fabric / Minecraft 26.2

Living document for the port (§11 of the bundle). **Written by the orchestrator only.** Agents read it
but do not edit it: everything destined for this file — snapshots, deviations, results — goes through
their final report.

The law of the port is [`../porting-26.2/PORTING-BUNDLE-26.2.md`](../porting-26.2/PORTING-BUNDLE-26.2.md).
This mod's plan is [`../PORT-PLAN.md`](../PORT-PLAN.md).

---

## AI subsystem: audit and fixes

* [`AI-AUDIT.md`](audit/AI-AUDIT.md) — audit of `core/entity/ai`, `core/entity/pathfinding` and `api/entity/ai`
  from 2026-08-01 (against `222c36bc`): 20 findings, ranked at the end. A hypothesis, not a specification.
* [`AI-FIXES.md`](audit/AI-FIXES.md) — what was actually done about it: items **1-10, 17, 18**, each with
  proof in the built jar's bytecode. Also what was rejected, what was excluded and what is blocked, plus
  the decision on item 6 (the marksman: parity with 1.21.1 was restored, rather than the behaviour
  upstream apparently meant to write).

Three of those findings were comments of the form "this API has no Fabric counterpart" which were
**false**, and which disabled working game logic while the build stayed green. The generalisation lives
in [`../porting-26.2/findings/`](../porting-26.2/findings/).

---

## Outside the AI subsystem: audit and fixes

* `OPT-PLAN.md` — audit of the colony tick, the request system, networking, persistence, the GUI and the
  client renderer from 2026-08-01 (against `74f9ee6b`): port regressions, marker verification,
  measurements, and a ranked list of 17 items. A hypothesis, not a specification. **Not placed in the
  tree** — unlike `AI-AUDIT.md` it exists only in that pass's scratchpad; if it is wanted next to
  `OPT-FIXES.md` it has to be brought over separately.
* [`OPT-FIXES.md`](audit/OPT-FIXES.md) — what was actually done about it: items **1-10**, each with proof in
  the built jar's bytecode. Also the re-taken measurements (packet buffer amplification, the cost of the
  defensive citizen-list copies), four places where the audit was wrong, judgement calls, and
  observations left off the list.

The same pattern again: two more "Fabric has no `FakePlayer`" comments turned out to be false, and one of
them had replaced a working check with a condition that **could never fire**.

---

## Debug conveniences

[`../COMMANDS.md`](../COMMANDS.md) documents the commands, hut settings and the item added to make this
port testable without playing through the mod by hand — what each one does, which huts carry which
setting, and what deliberately cannot be reached from a hut setting.

---

## Toolchain — done, do not reinstall

| | |
|---|---|
| Java | `/usr/lib/jvm/java-25-openjdk-amd64` |
| Gradle | `/opt/gradle-9.6.1/bin/gradle` — **never `./gradlew`** (the proxy answers 403 for GitHub assets) |
| Project | `/home/user/minecolonies/26.2` |
| Source of truth (read only) | `/home/user/minecolonies/1.21.1` — **do not edit** |
| Decompiled vanilla | `/opt/mc-src` — 7055 files. **Not part of the repository: it lives outside the tree and a fresh container does not have it.** Check before trusting it, and regenerate if it is missing (recipe below) |

Any build:

```sh
cd /home/user/minecolonies/26.2 && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
  /opt/gradle-9.6.1/bin/gradle <task> --no-daemon 2>&1 | tee /tmp/errors.txt
```

**One Gradle invocation at a time.** Two in parallel corrupt the Loom cache.

Datagen and the jar are two invocations, not one. In a single `runDatagen build`, resources are
snapshotted before datagen finishes writing, and the jar ships without the newest generated file.

### Restoring `/opt/mc-src`

Decompiled vanilla is the tie-breaker for every "does 26.2 still have this API" question, and this
document used to say it was ready and must not be regenerated. That was true of one container. It is
**not** true of a fresh one, where the path simply does not exist — and an empty `grep -rn` against a
missing directory reads exactly like "the API is gone", which is the specific mistake that has already
cost this port five false "Fabric has no counterpart" comments. Check the path exists before you believe
a negative result from it.

To rebuild it, roughly 2 minutes:

```sh
cd /home/user/minecolonies/26.2 && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
  /opt/gradle-9.6.1/bin/gradle genSources --no-daemon

unzip -q -o .gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.2/*-sources.jar \
  -d /opt/mc-src
```

The sources jar lands in the **project-local** `26.2/.gradle/loom-cache`, not in `~/.gradle/caches/fabric-loom`
— what sits in the shared cache is `decompile/v1.zip`, whose entries are named by content hash and are
therefore useless for finding a class by path.

Without decompiled sources, `javap` off the merged jar still answers questions about existence and
visibility, which covers most of them:

```sh
javap -p -classpath /root/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar \
  net.minecraft.world.item.ItemStack | grep copyWithCount
```

### Reference mods on disk

Mods already ported to 26.2 and taken to a green server. Every recipe in the bundle says "copy the shape
from a ported mod" — these are they.

| Mod | Path | Nature | Useful for |
|---|---|---|---|
| **Domum Ornamentum** | `/workspace/domum-ornamentum/26.2` | NeoForge 26.1 → Fabric 26.2, same LDT Team | everyone; `build.gradle`, `fabric.mod.json`, entrypoints, and the `PORT-STATUS.md` / `PORT-GAPS.md` of a finished port |
| **simple-planes** | `/workspace/simple-planes/26.2` | **NeoForge 1.21.1 → Fabric 26.2 — the same route as ours** | `simpleplanes.accesswidener` as a direct AT→AW translation; screens on `GuiGraphicsExtractor` |
| **BlockUI** | `/workspace/blockui/26.2` | both a dependency and a model | the whole GUI layer |

### Dependencies

| Library | Status | Path |
|---|---|---|
| Domum Ornamentum | ✅ ported, acceptance green (28/28 required classes present) | `/workspace/domum-ornamentum/26.2` |
| BlockUI | ✅ in sync with `version/main` (`74651c8`) | `/workspace/blockui/26.2` |
| `com.ldtteam.common` | ✅ **inside BlockUI**, 10/10 required classes | `/workspace/blockui/26.2/src/main/java/com/ldtteam/common` |
| Structurize | ✅ wired up and working: structure packs register on a live server | `/workspace/structurize/26.2` |

**Both of our requests to BlockUI are closed** in `74651c8`:

* `97ee934` — **`ColouredVertexConsumer` restored** as public API. Agent D had by then already rewritten
  `ColonyBorderRenderer` onto a vertex cache plus `submitCustomGeometry`, so we no longer need the
  wrapper, but it exists again.
* `64f586b` — **server config sync to the client** (`ConfigSync`, `ConfigSyncManager`, `ConfigSyncMessage`).
  This was our priority #1: 56 of MineColonies' 65 settings are server-side, and without the sync a
  client on a remote server made decisions from its own values.

⚠️ **Blocker raised against BlockUI:** at `74651c8` two crashes in `com.ldtteam.common.language` are live,
and they stop **every dependent mod** from starting — MineColonies and Structurize alike. Analysis and
patch: `BLOCKUI-RUNTIME-FIXES.patch`. A PR is being prepared in BlockUI's
own repository (branch `claude/fix-mod-init-crashes`); until it merges our build needs the patch applied
locally.

### Planned: a shared `com.ldtteam.common.inventory`

NeoForge's `net.neoforged.neoforge.items.*` is gone, and **both** projects grew their own copy: ours at
`api/inventory/api/` (8 types, 102 imports across 81 files), Structurize's at `compat/itemhandler/`
(3 types, 7 files). The copies are **method-for-method identical**, but Java has no structural typing, so
agent G had to write `core/util/StructurizeItemHandlerBridge` — an adapter between two identical
interfaces — to implement `IStructureHandler.getInventory()`.

Agreed with Structurize: the type lives **in BlockUI**, package `com.ldtteam.common.inventory`. All three
depend on BlockUI; only we depend on Structurize, and otherwise any future mod would pull in a building
library for the sake of an inventory interface.

The order of work is strict and cross-repository:

1. **BlockUI** creates the package (PR `claude/common-inventory-api`) — additions only, nothing removed.
2. **Structurize** and **MineColonies** move onto it and delete their copies; ours takes
   `StructurizeItemHandlerBridge` with it.

⚠️ **A trap that must not be lost in the generalisation.** A naive `InvWrapper` over vanilla's `Inventory`
is wrong: in 26.2 `Inventory.getContainerSize()` counts the equipment slots and `canPlaceItem` is not
overridden, so an "every slot in order" wrapper will accept a milk bucket into an armour slot. That is why
we have a separate `core/util/PlayerMainInvWrapper` covering the 36 main slots. The shared library must
solve this explicitly.

**`ldtteam.common` covers networking and config.** `PlayMessageContext` is written as a direct
replacement for NeoForge's `IPayloadContext`, `ModNetworking.register()/registerClient()` for
`PayloadRegistrar`, and `ConfigValue.*` for `ModConfigSpec.*`. The signatures of
`PlayMessageType.forClient/forServer` and the `AbstractClientPlayMessage` constructors match what the mod
already writes. Of the 129 files that take an `IPayloadContext`, **exactly one** calls a method on it —
the other 128 accept it as a parameter and never touch it. Network and config registration is entirely in
`core/MineColonies.java`.

---

## Progress

### Stage 0 — environment and skeleton ✅

| | |
|---|---|
| JDK 25, `unrar` | installed |
| Gradle 9.6.1 | `/opt/gradle-9.6.1`, from the vendored `gradle-dist/` |
| `26.2/` skeleton | `settings.gradle`, `gradle.properties`, `build.gradle` on Loom 1.17.13 |
| Pins | MC `26.2`, loader `0.19.3`, fabric-api `0.154.2+26.2`, Java 25, no `mappings` |
| `genSources` | ✅ 7055 files → `/opt/mc-src` |
| `fabric.mod.json` | written; entrypoints `com.minecolonies.core.MineColonies` / `MineColoniesClient` |
| AccessWidener | ✅ 84 AT lines → 43 AW entries, `validateAccessWidener` green |
| Entry points | skeletal `ModInitializer` / `ClientModInitializer` — filled in by agent A |
| `build` | ✅ green |
| `runServer` | see "Verification" |

### Stage 2 — agents

| Wave | Agent | Zone | Status |
|---|---|---|---|
| 1 | **S** | Structurize stubs | ✅ 84 files, 132 types; Structurize errors 454 → 0 (stubs later replaced by the real library) |
| 1 | **B** | `core/colony/**` | ✅ 208 of 404 files touched; zone errors 1433 → 1 |
| 1 | **A** | `api/**` | ✅ zone closed |
| 1 | **C** | `core/entity/**` | ✅ zone closed |
| 2 | **F** | entry point, `apiimp/**`, `core/event/**` | ✅ 37 files; zone errors ~700 → 13 (all in other agents' files) |
| 2 | **E** | `core/generation/**` | ✅ 35 of 55 files plus 2 new; zone errors **592 → 0** |
| 2 | **D** | `core/client/**` | ✅ 191 files; zone errors **~942 → 0** |
| 2 | **G** | the rest of `core/**` | ✅ ~250 files; zone errors → 0 |
| 3 | **H** | block and item registry ids | ✅ 83 files; a runtime defect, invisible to the compiler |
| 3 | **I** | datapack listener ordering | ✅ 4 files; deferred apply stage |

**Mechanical work before the agents** (orchestrator): the tree moved to `26.2/src`, 148 relocated vanilla
imports redirected, `ResourceLocation` → `Identifier` (2087 references plus 1648 constructors),
`IPayloadContext` → `PlayMessageContext` (268), `ModConfigSpec` → `ConfigValue` (6).

**JEI and JourneyMap are parked** — 3826 lines across 25 files excluded from compilation via
`optional-integrations.txt`, files left in place. The seam is clean: `Compatibility.jeiProxy` is a no-op
by default anyway and nothing outside references it. To bring them back, delete the line from the list.

**`Tuple` solved globally**: 26.2 has no `net.minecraft.util.Tuple`, and the mod has its own with the same
`getA()/getB()`. All 52 remaining files, 5 fully-qualified references and the stubs were moved onto it.

### Compilation errors

| Moment | Errors | Files |
|---|---|---|
| Baseline (immediately after the move) | 9650 | 856 |
| After the Structurize stubs | 8520 | — |
| After zone B | 7314 | 538 |
| After zone C | 6764 | — |
| Stubs replaced with the real Structurize | 6974 | — |
| After zone F | 1134 | 140 |
| After zone E | 658 | — |
| After zone D | 260 | — |
| After zone G | **0** | **0** |

Swapping the stubs for the live library cost **+104 errors**, of which only 32 even mention
`structurize`. That is the return on generating the stubs from the real 1.21.1 sources rather than
inventing them.

### Stages 1, 3–6

Not started.

---

## AccessWidener — what did not survive five versions

84 AT lines gave 59 unique entries: **43 carried over, 16 dead**. Every dead one is a change in the mod's
code rather than in the AW. The full breakdown with reasons is in the comments of
`src/main/resources/minecolonies.accesswidener`; in short:

| What | Became |
|---|---|
| `ResourceLocation.<init>(String,String)` | class is gone: `resources.Identifier` plus `Identifier.fromNamespaceAndPath(...)` |
| `RenderStateShard.setupState` | class is gone — 26.x rewrote the render pipeline |
| `HumanoidArmorLayer.armorTrimAtlas` | trims moved to `EquipmentLayerRenderer` |
| `HumanoidArmorLayer.getArmorModel` / `renderArmorPiece` | **alive, but different signatures** — they take a render state, not an entity |
| `DistanceManager.tickets` | → `ticketStorage`, of type `TicketStorage` |
| `Sheep.ITEM_BY_DYE` | field is gone; the class itself moved to `world.entity.animal.sheep` |
| `Entity.updateFluidOnEyes()` | method is gone |
| `GoalSelector.profiler` | not a field: `Profiler.get()` inside `tick()` |
| `ThrownPotion.applySplash(...)` ×2 | the class split into `AbstractThrownPotion` / `ThrownSplashPotion` / `ThrownLingeringPotion`, and the method exists in none of them |
| `AbstractMinecart.lerpSteps/X/Y/Z/XRot/YRot` | interpolation moved to `Entity.InterpolationHandler` and `NewMinecartBehavior` |
| `AbstractFurnaceBlockEntity.isLit()` | → the `litTimeRemaining` field |
| `Block.canSurvive(...)` | moved up into `BlockBehaviour`, where it is `protected` |
| `AbstractArrow`, `AbstractMinecart` | alive, but a package deeper: `projectile.arrow.*`, `vehicle.minecart.*` |

---

## Deferred — waiting on the first green `runDatagen`

**Duplicate resources (the same rake Domum Ornamentum describes in its `PORT-GAPS.md`).**
`src/main/generated` is mounted as a second resource root of the main sourceSet, and `processResources`
will fail on any overlap with `src/main/resources`. The overlap has been counted — **39 files** — and
datagen genuinely owns:

* `assets/minecolonies/models/item/cooked_rice.json` — the only model overlap;
* `data/minecolonies/colony/quests/**` (~38 files) — **not cosmetic**: `QuestTranslationProvider` lifts
  literal English text into `lang/quests.json` and substitutes keys. `src/main/resources` holds the
  *authoring* version with literals, `src/main/generated` the *shipping* version with keys. The generated
  one has to win, or the quests lose their translatability.

Recipe: once `runDatagen` is green and checked against the oracle, delete those files from
`src/main/resources`.

### Expected differences from the `1.21.1/src/datagen/generated` oracle — not regressions

* 3 `minecolonies:composting` recipes — the `input` field changes shape: NeoForge `CompoundIngredient`
  (a bare array) → Fabric `{"fabric:type":"fabric:any","ingredients":[…]}`. The item set is the same.
* `data/minecolonies/loot_modifiers/**`, `data/neoforge/loot_modifiers/**` and
  `data/neoforge/data_maps/**` are no longer written — runtime code replaced them (see "Restored").
* `assets/minecolonies/items/*.json` — **new files, absent from the oracle.** Since 1.21.4 an item model
  definition is mandatory for every item, and `src/main/resources` has none at all. Without them **every
  item in the mod is a pink-and-black cube.**
* `#minecraft:trim_templates` was removed from vanilla in 26.2 — its former contents (18
  `*_armor_trim_smithing_template`) are now written out by name.

---

## Contract deviations

_(Any signature an agent was forced to change against the contract. The integrator reads this first.)_

---

## Disabled content

### Colony protection — 6 permissions do not work (agent B, step 3)

All of them in `ColonyPermissionEventHandler`, all for the same reason: Fabric has no corresponding event.
**This is a visible loss for the player — the colony stops being protected against these actions.**

| Permission | Was (NeoForge) |
|---|---|
| `PLACE_BLOCKS` / `PLACE_HUTS` | `BlockEvent.EntityPlaceEvent` |
| `EXPLODE` — and the whole `turnOffExplosionsInColonies` config | `ExplosionEvent.Start` / `.Detonate` |
| `TOSS_ITEM` | `ItemTossEvent` |
| `PICKUP_ITEM` | `ItemEntityPickupEvent.Pre` |
| `FILL_BUCKET` | `VanillaGameEvent` FLUID_PICKUP |
| `SHOOT_ARROW` | `ArrowLooseEvent` |

Preserved and working: `BREAK_BLOCKS`, `BREAK_HUTS`, `ACCESS_HUTS`, `ACCESS_TOGGLEABLES`,
`RIGHTCLICK_BLOCK`, `RIGHTCLICK_ENTITY`, `OPEN_CONTAINER`, `THROW_POTION`, `USE_SCAN_TOOL`,
`ATTACK_CITIZEN`, `ATTACK_ENTITY`, and the suppression of damage a guard deals to its own colony during a
raid.

**Do not treat this as final.** `PLACE_BLOCKS` at least can be restored through `UseBlockCallback` —
placing a block goes through using an item on a block, and that callback is already wired up for
`RIGHTCLICK_BLOCK` and can cancel. Return to it once the build is green.

The later non-AI audit found that `PLACE_HUTS` is in fact intact through a different path, contrary to
the port's own comment here.

### Point degradations

| What | Consequence |
|---|---|
| `TravellingManager` moved from `NbtUtils.writeBlockPos` to `BlockPos.CODEC` | **the save format of that field changed** — old worlds will read `BlockPos.ZERO` |
| `entity.isAddedToLevel()` (a NeoForge extension, 11 sites) → `!entity.isRemoved()` | no longer distinguishes "not yet added" from "removed" |
| `LivingDamageEvent.Pre#setNewDamage(0)` → `ALLOW_DAMAGE` returning `false` | Fabric can only cancel damage outright, not zero it; here the effect is the same |
| `contains(key, TYPE)` → `contains(key)` | the NBT value's type is no longer checked, only the key's presence |
| `Capabilities.ItemHandler.BLOCK` → `blockEntity instanceof Container` | "is this a container" is now vanilla's definition rather than a capability-based one (C4) |
| `build_goggles`: the `minecraft:disabled` model override was dropped | the builder's goggles do not switch to their "off" texture; waiting on a `ConditionalItemModelProperty` registration in zone D |
| `ItemNbtCalculator`: `instanceof ArmorItem` → `EQUIPPABLE` into an armour slot | wider than the original — `dyed_color` will now also reach pumpkins, heads and the elytra (the oracle had exactly 44 items) |
| `DatagenLootTableManager` reads the vanilla datapack only | a nested reference to one of *our* loot tables gives an incomplete drop list (with a log entry); there are none today |
| `getKnownBlocks` / `getKnownEntityTypes` removed (26.2's supertype has neither) | the datagen "did you forget a block" check is lost; the table contents are unchanged |
| **`SpearItemTileEntityRenderer` removed** — `BlockEntityWithoutLevelRenderer` is gone and Fabric has no counterpart to `IClientItemExtensions#getCustomRenderer` (step 4) | the spear is a flat item model in inventory and in hand. Fixable in datagen: `"minecraft:special"` in `items/spear.json` |
| The vanilla-recipe branch in `RestaurantMenuModuleWindow` (`Level#getRecipeManager` does not exist on the client) | ingredients are parsed from MineColonies' custom recipes only |
| `IClientItemExtensions#getArmPose` | items from other mods do not override a citizen's arm pose |
| `ParticleTypes.DRAGON_BREATH` → `END_ROD` when a colony is created | in 26.2 that is a `ParticleType<PowerParticleOption>` and `VanillaParticleMessage` only accepts a `SimpleParticleType` — a different effect |
| `Model#renderToBuffer` is final in 26.2 and draws the **whole** `root()` | the citizen's default fallback model, baked from `ModelLayers.PLAYER`, now draws the overlays (`jacket`, `*_sleeve`, `*_pants`) that 1.21.1 did not. **The compiler will not catch this.** The claim that "the mod's own job models are unaffected" was too narrow — see the scarecrow row below, and note that the test is not "is it a job model" but "does the mesh start from a vanilla one" |

### Restored after agent E's report

Two losses that agent E left as data with no consumer, which the orchestrator wired up:

| What was dead | How it was closed |
|---|---|
| **Colony crops did not drop from vanilla blocks, and supply chests did not appear in 27 vanilla chest tables** — NeoForge global loot modifiers do not exist on Fabric | `EventHandler#onLootTableLoad` now reads `DefaultLootModifiersProvider` directly: the "add table T under condition C" modifier became a pool holding a single `NestedLootTable.lootTableReference(T)` under `C`. The `GenerateSupplyLoot` gate (config `generateSupplyLoot`) is preserved |
| **The mod's items could not go into a vanilla composter** — NeoForge data maps do not exist on Fabric | `DefaultDataMapsProvider.compostables()` is poured into `CompostableRegistry.INSTANCE` on `TAGS_LOADED` |

Three more on the scarecrow and the colony flag — the two `renderSingleBlock` / `renderStatic` rows that used
to sit in *Point degradations*, plus one defect nobody had noticed:

| What was dead | How it was closed |
|---|---|
| **The lantern on the scarecrow** — `BlockRenderDispatcher#renderSingleBlock` is gone | `SubmitNodeCollector#submitMovingBlock` with a `MovingBlockRenderState`, which is the entry point vanilla's own `PistonHeadRenderer` uses to draw one block state from a block entity renderer. The state carries the block, the biome, the light engine and a `blockPos`, and answers air for every other position, so the lantern is lit and shaded as a free-standing block. It must be built in `extractRenderState`, which is the only phase with a level. The 1.21.1 transform is kept verbatim, so the lantern hangs where it always did |
| **The creative placeholder above the colony flag** — `ItemRenderer#renderStatic` is gone | `BlockEntityRendererProvider.Context#itemModelResolver()` → `updateForTopItem(…, ItemDisplayContext.FIXED, …)` in extract, then `ItemStackRenderState#submit` in submit. Same split as the lantern: resolve where there is a level, draw later |
| **`ScarecrowModel` drew two humanoid parts it never asked for.** Its mesh started from `HumanoidModel.createMesh` and overwrote the parts it wanted; 1.21.1 then hid the rest by overriding `renderToBuffer` to draw eleven named parts. That override is impossible in 26.2 (the method is `final`), so the port deleted it — and `body`, plus `hat`, which `PartDefinition#addOrReplaceChild` **carries over onto the replacing `head`**, started being drawn. `hat` is a 9×9×9 cube that lands half-sunk in the ground beside the scarecrow's base. Its texels come from the unused gaps of the 64×32 skin layout the sheet still uses, which are stored as *opaque-white* `(255,255,255,0)`; `entitySolid` ignores alpha, so most of its faces draw as solid white slabs and its upward face as a dark brown one | the mesh starts from `new MeshDefinition()`. The scarecrow declares all eleven of its parts itself and never reads a vanilla one, so nothing else changes and the leftovers simply never exist. Giving them "correct" UVs would have been the wrong fix: they are unwanted geometry, not mis-mapped geometry — `body` is a second copy of `torso`, and `hat` is a helmet overlay for a humanoid the scarecrow is not |

The scarecrow's own UVs are **not** confused between 64×32 and 128×64, which is the first thing to suspect
here. All eleven parts land inside 128×64 and on drawn content; the sheet is a 64×32 skin layout on a
128×64 canvas precisely so that `post` (`texOffs(0, 32)`, 2×16×2, reaching v = 50) and the four pegs have
somewhere to live. Both variants — `blockscarecrownormal` and `blockscarecrowpumpkin` — have byte-identical
alpha, so the defect and the fix are the same on each.

Two more after agent D's report, in `ClientRegistryHandler`:

| What was dead | How it was closed |
|---|---|
| The colony map and tablet overlays on the item in the inventory | both decorators were rewritten onto `ExtractItemDecorationsCallback` and filter the stack themselves (the callback is global, not per-item) |
| The cavalry horse **did not compile**: `RegistrationHelper#register` is invariant in the render state — `HorseRenderer`'s model is an `EntityModel<EquineRenderState>` while the layer is a `RenderLayer<HorseRenderState,…>`, so even vanilla's `HorseMarkingLayer` cannot be registered that way | `HorseRenderer` is `final` in 26.2, hence our own `CavalryHorseRenderer`: it adds `CavalryOverlayLayer` itself and is the **only** thing that can read combat readiness off the entity — layers no longer see it, so the value travels on the render state |

All four run on `TAGS_LOADED` / at loot table load / during client registration rather than in
`onInitialize`: the compostability table is derived from each item's `FOOD` component, which is not yet
attached during `onInitialize` — the same trap that moved
`ModEquipmentTypes.initRegisterEquipmentTiers()` there too.

> Correction to agent E's report: in fabric-api `0.154.2+26.2` the class is called
> **`CompostableRegistry`**, not `CompostingChanceRegistry` — verified against the
> `fabric-content-registries-v0-11.2.2` jar.

---

## Sprite atlases: why huts no longer take textures from `item/`

In 1.21.1 the block atlas stitched both `block/` and `item/`, so a block model could happily draw a carrot
or an arrow straight from an item texture. In 26.2 there are 13 atlases and `item/` moved into its own
`minecraft:textures/atlas/items.png` (`AtlasManager.KNOWN_ATLASES`). The consequences for models:

* `SimpleModelWrapper.bake` → `findNonBlockSprites`: if even one quad of a block model references a sprite
  from outside the block atlas, **the whole model** is replaced with the missing model —
  `Rejecting block model …, since it contains sprites from outside of supported atlas`;
* `CuboidItemModelWrapper.validateAtlasUsage`: an item model whose quads span two atlases throws an
  `IllegalStateException` → `Unable to bake item model: …`. The huts' item models inherit from their block
  models, so they hit both errors at once.

**Simply adding `item/carrot` to `assets/minecraft/atlases/blocks.json` does not work.** The sprite would
get the same id in two atlases, and `ModelManager.CombinedBlockItemMaterialBaker` — the single baker for
both blocks and items — tries **the item atlas first** and only then the block one, so the model would
stay rejected and a `Duplicate sprite … This will be rejected in a future version` would appear on top.

So `assets/minecraft/atlases/blocks.json` gained `minecraft:single` entries that **rename** through the
`sprite` field: the same vanilla texture is stitched into the block atlas under its own id
`minecolonies:block/item/<name>`, and the block models reference that. No PNG is copied into the mod, a
resource pack recolouring the vanilla item keeps working (`SingleFile` reads through the
`ResourceManager`), and the id is unique, so there is no duplicate across atlases. The items' own models
are untouched and still live on the item atlas.

17 models were affected: 11 huts whose geometry was restored from `neoforge:composite` (until then the
model was empty, produced no quads, and never reached the atlas check), plus `blockhutchickenherder`,
`blockhutfield` and the `digsite` → `simplequarry` / `mediumquarry` / `largequarry` chain — those six
broke independently of composite, and not all of them would have shown up in a live client's log because
some models are not pulled in by a blockstate.

While there, the parent of `blockhutbarracks`, `blockhutblacksmith`, `blockhutcombatacademy` and
`blockhutstonesmeltery` changed from `block/cube_all` to `minecraft:block/block`: `cube_all` declares
`down`/`up`/`north`/… as `#all`, which those models do not define, hence
`Unresolved texture references in …`. All four have their own geometry and set `particle` explicitly, so
the only thing they used from `cube_all` were those dangling references. This defect is separate from the
atlas one and is present in the 1.21.1 oracle too.

One pre-existing `Duplicate sprite` remains: `minecraft:entity/chest/normal` sits both in the block atlas
(through the mod's `single`, which nine block models need) and in `minecraft:chests`. Today that is only a
warning, since the block baker does not query the chest atlas. It is fixable the same way — rename the
`single` to `minecolonies:block/entity/chest_normal` and update those nine models.

---

## Verification

| Step | Result |
|---|---|
| `compileJava` | ✅ green on the skeleton (2 entry-point files) |
| `build` | ✅ green, `validateAccessWidener` passes |
| `runDatagen` | ✅ **green and reproducible** — 5039 files, checked against the `1.21.1/src/datagen/generated` oracle (see above) |
| **Models against atlases** | ✅ `tools/model-atlas/model_atlas_audit.py` — working **from the built jar**, it resolves blockstate/item-definition → model → parent → texture slots and repeats both of 26.2's fatal checks (`findNonBlockSprites`, `validateAtlasUsage`). On the pre-fix jar: 17 rejected block models and 14 unbakeable item models; after: zero. The check is static — it proves the baker will accept the model, and says nothing about how it **looks** |
| **Production jar** | ✅ **the strongest check available here.** `gradle build` produces `minecolonies-26.2-0.0.11.jar` (72 MB) with three nested dependencies via Fabric Jar-in-Jar. The jar was placed in the `mods/` of a **real Fabric server installation** (`fabric-installer`, loader 0.19.3, with only fabric-api alongside) and started: `Done (4.421s)!`, **zero `/ERROR]` lines**, mod tree showing `minecolonies 0.0.11` with `blockui` / `domum_ornamentum` / `structurize` nested. That is the artifact being verified, not a dev classpath |
| `runServer` | ✅ **green on a clean world with the mod's full code.** `Done (6.327s)! For help, type "help"`, **zero `/ERROR]` lines**. The data really loaded: 161 recipes for 16 crafters, 208 researches across 4 branches, 103 effects, quests, 1761 items with NBT keys |
| **Client** | ⚠️ **checked by hand, not automatically.** There is no display in the container and `runClient` does not start, so nothing here covers `client/**` — 314 files and the whole GUI — automatically. What coverage exists comes from the user playing the built jar and reporting defects; that route has already found and fixed real ones, including the invisible dialogue text |

This is acceptance of the **port**, not of the skeleton: the build contains the mod's whole code, datagen
ran and was checked against the oracle, and the server came up and parsed all of its datapacks without a
single error.

### The server harness, and the baseline to hold

The server check above is worth nothing if the next person has to reinvent it, so here it is as a
procedure. A stock Fabric dedicated server (loader `0.19.3`, `fabric-api-0.154.2+26.2`, EULA accepted,
offline mode) with **exactly two** files in `mods/` — the Fabric API and our jar. A loose copy of
`blockui`, `structurize` or `domum_ornamentum` in `mods/` beats the nested one regardless of version and
logs nothing about it, which is how you end up reading a stack trace against source that is not running.

Re-measured on the shipped `dist/` jar, 2026-08-08:

```
Loading 45 mods:  minecolonies 0.0.12  |-- blockui 0.0.1  |-- domum_ornamentum 1.0.0  \-- structurize 1.0.0
Done (5.583s)!    /ERROR] 0    /FATAL] 0    /WARN] 10
```

All ten warnings are benign — five are vanilla's own offline-mode banner, the rest are Structurize's pack
discovery and the mod's compat scan, which log at WARN out of habit. **Zero errors is the bar**, and it
is cheap to check:

```sh
grep -c '/ERROR\]' server-run.log
```

Every change from here is measured against those numbers, not against whether the source reads well.

Every fix in this tree is verified against the built artifact rather than the source. That rule was not
chosen for elegance: this port has repeatedly shipped defects whose source read fine, and five separate
comments claiming a 26.2 API had no counterpart turned out to be false while the same source tree was
already calling it. Two of them had silently disabled working game logic.
