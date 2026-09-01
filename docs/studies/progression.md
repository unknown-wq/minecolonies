# What it takes to finish MineColonies

Reference note, not an implementation. Date: 2026-08-24. Compiled from public sources — the official
wiki, upstream source, and the vanilla Minecraft wiki — then checked, where checkable, against the
`26.3/` tree in this repository.

Written because a bot or a model driving a colony through the API needs to know what the goal state
is, what gates what, and where a colony can be stalled forever. The nine other studies in this
directory look inward at our code; this one looks outward at the game the code implements.

Confidence markers, one level stricter than the source material:

* **[VERIFIED]** — checked in this repository's own tree; the `file:line` is real and says what is
  claimed.
* **[wiki]** — the official wiki, minecolonies.com. Version selector defaults to 1.21.1; everything
  below is 1.21.1 unless stated.
* **[upstream]** — upstream source on GitHub, `ldtteam/minecolonies`, branch `version/main`.
* **[config]** — the mod's config-file page, default values.
* **[inferred]** — reasoning from the mechanics, not confirmed by any source.
* **[community]** — forums, mirrors of the old wiki, modpack pages. Lowest confidence.

**[!]** marks a disagreement between sources, or between a source and this tree.

No game client exists here. Nothing below was played.

---

## 0. Checked against this tree

Three of the load-bearing claims were checkable against `26.3/`. Two hold. One is stated in the
wrong place by every source that states it at all.

**The builder's hut is the gate on building level, not the town hall.** [VERIFIED]
`26.3/src/main/java/com/minecolonies/core/colony/workorders/WorkOrderBuilding.java:217`:

```java
return (builderLevel >= this.getTargetLevel() || builderLevel == BuildingBuilder.MAX_BUILDING_LEVEL || (builderLocation.equals(getLocation()))
          || FreeMode.isOn(building.getColony()));
```

A level-3 builder will not accept a work order for a level-4 building. Three exceptions, all in that
expression: a max-level builder builds anything, a builder always upgrades their own hut, and free
mode — this fork's own testing switch, `/mc colony freemode <colony> on` — lifts the gate entirely. The
town hall caps at 5 (`BuildingTownHall.java:57`, `private static final int MAX_BUILDING_LEVEL = 5;`)
and gates research and population, not construction. The widely repeated "a building cannot exceed
the town hall's level" has no support in this tree.

**The research tree cannot be completed, by design.** [VERIFIED] Counting the datagen output under
`26.3/src/main/generated/data/minecolonies/researches/`, excluding `effects/`: civilian 70, combat
59, technology 76, unlockable 4 — **209 researches**. The `exclusiveChildResearch` flag appears on
exactly five nodes, each with two children, whose chains are 5/5/5/5/4 long. Minimum locked out:
24. **Maximum simultaneously reachable: 185.**

**[!]** The wiki, and the guide material this note is compiled from, place the exclusive choice in
"column 6" — the end of each branch. In this tree it sits at the *roots*: `civilian/higherlearning`
(researchLevel 1), `civilian/stamina` (1), `combat/accuracy` (1), `combat/avoidance` (1),
`combat/regeneration` (2). The irreversible choice is made in the first hour of a colony's life,
not the last, and it costs an entire branch rather than a single node. Anything planning a research
order has to decide these five before it decides anything else.

**Consequence for our own test harness.** `tools/run-server.sh` generates its world with
`minecraft:flat` and no preset, i.e. Classic Flat — which section 9 shows is the one world family
where progression is permanently blocked. The stand is fine for checking that a server boots. It is
useless for anything about a colony growing. A bot needs either a normal world or a superflat world
on the Overworld preset.

---

## 1. What "finishing" means

There is no boss and no victory screen. The community treats four goals as the finish line.

### 1.1. Town hall — 5 levels

Max level 5, hardcoded. [VERIFIED] `BuildingTownHall.java:57` in this tree; [upstream] the same
constant upstream.

Claim radius grows with level [wiki] https://minecolonies.com/wiki/systems/border/:

| Town hall level | Extra claim radius |
|---|---|
| 1 | 1 chunk |
| 2 | 1 chunk |
| 3 | 2 chunks |
| 4 | 3 chunks |
| 5 | 5 chunks |

Starting radius is **4 chunks** from the chunk holding the town hall block (`initialColonySize`),
maximum **20** (`maxColonySize`), minimum distance between colonies **8** (`minColonyDistance`)
[config] https://minecolonies.com/wiki/misc/configfile/

### 1.2. Buildings — 51 types (1.21.1)

The 1.21.1 wiki navigation lists 51: Alchemist Laboratory, Apiary, Archery, Bakery, Barracks,
Barracks Tower, Blacksmith's Hut, Brick Yard, Builder's Hut, Chef's Kitchen, Chicken Farmer's Hut,
Combat Academy, Composter's Hut, Concrete Mixer's Hut, Courier's Hut, Cowhand's Hut, Crusher's Hut,
Dining Hall, Dyer's Hut, Enchanter's Tower, Farmer's Hut, Fisher's Hut, Fletcher's Hut, Flowershop,
Forester's Hut, Gatehouse, Glassblower's Hut, Graveyard, Guard Tower, Hospital, Library, Mechanic's
Hut, Mine, Mystical Site, Nether Mine, Plantation, Quarry, Rabbit Hutch, Residence, Sawmill,
School, Shepherd's Hut, Sifter's Hut, Smeltery, Stable, Stonemason's Hut, Swineherd's Hut, Tavern,
Town Hall, University, Warehouse. [wiki]

**[!]** The number is version-dependent. Brick Yard and Gatehouse are 1.21-line additions; 1.20.1
lists fewer. Some entries are not free-standing buildings: a Barracks Tower only goes inside a
Barracks, and the Quarry is worked by someone hired at the Mine.

Most buildings have 5 levels. Known exceptions: **Tavern caps at 3** [wiki]; **Barracks Tower** caps
at 5 but never exceeds its Barracks; **Stash** is level 0 and is not built [upstream].

"Every building maxed" is therefore around **250 building levels**, not counting duplicates —
Residence, Guard Tower, Mine, Farmer's Hut and others can be built in any number.

### 1.3. Research — 4 branches, 209 nodes here

Branches: **Civilian, Combat, Technology, Unlockables** [wiki]
https://minecolonies.com/wiki/systems/research/

A column is the minimum University level needed to start: column 1 → University 1, … columns 5 and
6 → University 5.

Node counts from the wiki tables for 1.21.1 [wiki]:

| Branch | c1 | c2 | c3 | c4 | c5 | c6 | Total |
|---|---|---|---|---|---|---|---|
| Civilian | 6 | 16 | 14 | 13 | 10 | 9 | **68** |
| Combat | 5 | 10 | 11 | 11 | 11 | 9 | **57** |
| Technology | 7 | 20 | 24 | 11 | 8 | 7 | **77** |
| Unlockables | 1 | — | 1 | 1 | 1 | — | **4** |
| **Total** | | | | | | | **206** |

This tree has **209** (70/59/76/4) [VERIFIED] — version drift, not a contradiction.

The tree cannot be completed. The wiki states it as a column-6 rule:

> "You can only have one column 6 research in each of the trees. To unlock a different column 6
> research for that tree, you must undo the completed one first."

giving 206 − 8 − 8 − 6 = **184** simultaneously active. In this tree the same mechanic
(`exclusiveChildResearch`) sits at the branch roots and yields **185 of 209** — see section 0.

### 1.4. Population

* Starting citizens: **4** (`initialCitizenAmount`) [config]
* The cap is the number of free beds, but bounded above by research [wiki]
* With no research the ceiling is **25** [wiki] https://minecolonies.com/wiki/buildings/residence/
* The Civilian chain that raises it:
  * **Outpost** — 4 Residence levels total → **50**
  * **Hamlet** — 5 Residence levels total → **100**
  * **Village** — Town Hall 4 → **150**
  * **City** — Town Hall 5 → **500**

**[!]** City advertises 500, but `maxCitizenPerColony` defaults to **250** (range 25–500). On stock
config the real ceiling is 250. [config]

### 1.5. The finish line, as a checklist

1. Town hall 5/5.
2. University 5/5, Warehouse 5/5, Builder's Hut 5/5.
3. All research except the mutually exclusive branches → 184 active upstream, **185 here**.
4. Population at the config ceiling (250 by default).
5. Every building type built at least once at max level.
6. Every citizen eating tier-3 food and living in a level-5 Residence.

---

## 2. Opening: what to do, in order

### Step 0. Before the colony

Gather wood, cobblestone, coal, iron, string, leather, wool, saplings, flowers, food. [wiki]
https://minecolonies.com/wiki/tutorials/getting-started/

**Exit condition:** a stack of planks and some iron.

### Step 1. Supply Camp or Supply Ship

Gives the starting resources **and the town hall block** — the block cannot be crafted first, its
recipe unlocks only after a town hall has been placed from supplies. [wiki]

Placement:

* **Supply Camp** — flat, fully cleared ground, at least **16×17**: no holes, flowers, grass, ferns,
  seagrass or coral.
* **Supply Ship** — a body of water at least **32×20**.

**[!]** The wiki disagrees with itself: Getting Started gives those exact numbers, while the Supply
camp and ship page says the footprint depends on the chosen build style and needs "at least one
block more than the build area" — so 16×17 is the default style, not a constant. [wiki]
https://minecolonies.com/wiki/items/supply_camp_and_ship/

**One per world.** A second camp or ship cannot be placed, barring one found in treasure;
`allowInfiniteSupplyChests` defaults to `false`. [wiki+config]

If the preview refuses to place, shift one or two blocks and widen the cleared area. [wiki]

**Exit condition:** a town hall block and a build tool in inventory.

### Step 2. Scouting the town hall site — the one irreversible step

* A large, reasonably flat area. The wiki suggests **at least 8×8 chunks**. [wiki]
* The chunk holding the town hall block becomes the colony centre and the centre of its protected
  zone **permanently** — 4 chunks by default, bedrock to sky. The block can be moved within the
  zone; the zone itself cannot be moved, only removed by an admin command. [wiki]

This is the classic beginner mistake: the town hall goes into a narrow valley or onto a slope, and
the colony is short of flat ground for the rest of the game.

**Exit condition:** colony founded — right-click the block, name it, Found Colony.

### Step 3. Builder's Hut — always the first building

> "Before you build any other building, you must build the Builder's Hut. If the Builder's Hut is
> not built, the Builder cannot build other buildings."
> [wiki] https://minecolonies.com/wiki/buildings/builder/

Place the hut block with the build tool → a builder is assigned automatically → in the hut GUI,
Build Options → Build Building → the builder builds **their own hut**, requesting materials as they
go.

Put the hut at the centre of the intended town: the builder walks between hut and site. [wiki]

**Exit condition:** Builder's Hut level 1 complete.

### Step 4. Housing

* **Tavern** — 4 beds immediately, caps at level 3. Upgrades add no beds; they improve the quality
  and number of visitors, who can be recruited for items. [wiki]
  https://minecolonies.com/wiki/needs/sleep/
* **Residence** — one bed per level (level 4 = 4 beds), unlimited count. [wiki]

The first four citizens need beds **before** anyone else will arrive. [wiki]

**Exit condition:** more free beds than citizens.

### Step 5. Food

Fastest early is the **Fisher's Hut**: needs water at least **7×7×2** next to the hut, and a fishing
rod. [wiki] https://minecolonies.com/wiki/buildings/fisherman/

In parallel: **Farmer's Hut** with fields (scarecrow block), and a **Dining Hall** (called Restaurant
in 1.20.x) so food is handed out automatically.

**Exit condition:** citizen saturation stays above 5.

### Step 6. Wood and stone

* **Forester's Hut** — cuts any tree within roughly 150 blocks that is not part of a building
  schematic and has no cobblestone under it. Needs **axes and hoes** — hoes to clear leaves. [wiki]
  https://minecolonies.com/wiki/buildings/lumberjack/
* **Mine** — the miner digs a shaft and branches off it. Needs a pickaxe, a shovel, torches, and a
  great deal of cobblestone for filling caves, water and lava; the shaft stage also wants planks,
  slabs and fences. [community] https://sqwatermark.github.io/MinecoloniesWiki/source/misc/troubleshooting

**Exit condition:** construction no longer idles on wood or stone.

### Step 7. Warehouse and courier — where the colony starts running itself

**Until there is a Warehouse and at least one Courier, every request is fulfilled by hand.**

* Warehouse: 2 couriers per level, max 10. [wiki]
* Courier's Hut: **each courier needs their own hut**. [wiki]

**Exit condition:** workers' requests are satisfied without the player, whenever the material is in
the warehouse.

### Step 8. University, then research

This is where the long game starts. Section 5.

### Step 9. Expansion

Guard Tower (defence), Hospital (disease), Sawmill (wood crafting), then duplicate
Mine/Forester/Farmer/Builder. [wiki]

The wiki's own headline advice: **upgrade huts to level 5 wherever possible — it speeds the worker
up.** [wiki]

---

## 3. The dependency tree

### 3.1. The real limiter is the builder's hut level

> "The Builder can only build or upgrade any other hut up to the level of their own hut. So, in
> order for the Builder to upgrade any building, the Builder's Hut must be upgraded first."
> [wiki] https://minecolonies.com/wiki/buildings/builder/

Confirmed in this tree at `WorkOrderBuilding.java:217` — see section 0. [VERIFIED]

**[!]** The claim "a building cannot exceed the town hall's level" circulates widely, largely via
auto-generated SEO pages that reproduce each other's text verbatim. It is not in the wiki, not in
upstream (`RegisteredStructureManager.java`, `BuildingTownHall.java`), and not in this tree. Town
hall level gates **research**, not construction. A modpack datapack could add such a gate; that
would be the modpack's rule, not the mod's.

### 3.2. The actual chain

```
Supply Camp/Ship  ──> Town Hall block ──> colony founded
                                            │
                                            ▼
                                     Builder's Hut L1
                                            │
                    ┌───────────────────────┼───────────────────────┐
                    ▼                       ▼                       ▼
             Housing (Tavern,        Food (Fisher /          Materials (Forester,
             Residence)              Farmer + Dining Hall)   Mine)
                    │                       │                       │
                    └──────────┬────────────┴───────────┬───────────┘
                               ▼                        ▼
                          free beds            Warehouse L1 + Courier's Hut
                               │                        │
                               ▼                        ▼
                        population growth      requests handled automatically
                               │                        │
                               └───────────┬────────────┘
                                           ▼
                                      University L1..L5
                                           │
                                           ▼
                                    research (209 nodes)
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                      ▼                      ▼
        building unlocks          population cap          combat bonuses
        (Sawmill, Smeltery,       (Outpost 50 →           (damage, armour,
        Blacksmith, Stonemason,   Hamlet 100 →            shields, Barracks,
        Crusher, Sifter,          Village 150 →           Combat Academy,
        Composter, Plantation,    City 500/250)           Archery)
        School, Library,
        Hospital, Graveyard,
        Nether Mine, Alchemist)
```

### 3.3. What a building level buys

| Building | What grows with level |
|---|---|
| Builder's Hut | the maximum level of everything built in the colony |
| Residence | 1 bed per level; caps citizen skill; **sets the required food tier** |
| Tavern | not beds (always 4) but visitor quality and count; caps at 3 |
| Warehouse | +2 couriers per level (max 10); sorting from L3; rack expansion from L5, 3 times, for emerald blocks |
| Courier's Hut | stacks per trip: 2 / 3 / 4 / 5 / unlimited |
| University | +1 researcher per level (max 5); offline research from L3 |
| Guard Tower | patrol radius 80 / 110 / 140 / 170 / 200 blocks |
| Barracks | towers 1/2/3/4/4 and their max level 1/2/3/4/5 |
| Mine | shaft depth (section 10.3) |
| Sifter's Hut | blocks sifted per day: 64 / 256 / 576 / 1024 / unlimited |
| Crusher's Hut | output per day: 16 / 64 / 144 / 256 / 999 |
| Chef's Kitchen | recipes: 10 / 20 / 40 / 80 / 160 |
| Farmer's Hut | fields 1/2/3/4/5; recipes 10/20/40/80/160 |
| Any worker hut | maximum tool tier and enchantment level (section 10.2) |

Sources: the corresponding pages under https://minecolonies.com/wiki/buildings/

### 3.4. What the town hall level actually gates

Research that names the town hall directly [wiki]:

* Town Hall 1 → First Aid; Becoming more like the real thing (guard armour)
* Town Hall 2 → First Aid II; Remembrance (unlocks Graveyard); Extra leathery
* Town Hall 3 → Lifesaver; Haste/Nimble; Iron Skin; **teleport to allied colonies**
* Town Hall 4 → Lifesaver II; Agile; Iron Armor; **Village (150 citizens)**
* Town Hall 5 → Guardian Angel; Swift; Steel Armor; **City (500 citizens)**

---

## 4. Economy

### 4.1. Food

Saturation [wiki] https://minecolonies.com/wiki/needs/food/:

* Every citizen has their own saturation bar, behaving like the player's hunger bar.
* It drains during work and **again on going to sleep**; higher-level workers lose more overnight.
* At 0 the citizen **stops working**, stops gaining levels, gets Slowness, and complains in chat.
* Below a threshold they do not heal at all; at full saturation they heal twice as fast.
* Colony-average saturation above 5 raises happiness, below 5 lowers it. [wiki]

Food tiers (1.21):

| Tier | Saturation | Examples |
|---|---|---|
| 1 | **5** | Flatbread, Cheese Ravioli, Chicken Broth, Cheddar Cheese, Polenta |
| 2 | **7** | Pierogi, Lembas Scone, Apple Pie, Fish N Chips, Rice Ball |
| 3 | **9** | Borscht, Steak Dinner, Ramen, Sushi Roll, Schnitzel, Tacos |

Vanilla food works, but carries a happiness penalty and worse saturation. [wiki]

**The required tier is set by the citizen's HOME level, not their workplace.** A citizen refuses food
below their requirement. [wiki]

**[!]** The wiki does not publish the Residence-level → minimum-tier table. Patch notes only reveal
that the first level-3 Residence triggers a "build a Chef's Kitchen" quest, implying tier 2 at
around home level 3. [inferred]

Production chain (1.21):

```
Farmer (fields, mod crops) ──> raw ingredients
                                  │
Cowhand/Shepherd/Chicken/Swine ──┤
                                  ▼
                          Chef's Kitchen (Chef)  ── tier 1-3 dishes
                                  │
                          Bakery (Baker)  ── baked goods
                                  │
                                  ▼
                          Dining Hall (Waiter) ── distribution
```

**Crops are biome-locked.** Each mod crop grows only in its biome category: any / cold / temperate /
hot-humid / hot-dry. Some tier-3 dishes need ingredients from **different** biomes, which means
trade with a second colony. Directly: **in a single-biome world the top food tier can be
unreachable without a second colony.** [wiki]

Seeds for mod crops drop from grass and ferns — Short Grass, Tall Grass, Fern, Seagrass, Small
Dripleaf, Dead Bush. [wiki]

**[!] Versions.** The whole tier system, the Chef's Kitchen, and the Restaurant → Dining Hall /
Cook → Waiter rename belong to the new food system. Video guide titles suggest it was backported
into late 1.20.1 builds, but early 1.20.1 and 1.19 have only Restaurant + Cook and vanilla food.
Both names appear on wiki pages because of the version selector.

**How much food per citizen.** No source gives a per-day figure. What is certain: consumption rises
with worker level and is charged at least once per night. The Civilian chain Gourmand → Gorger →
Stuffer → Epicure → Glutton adds **+50%** saturation per meal in total. [inferred] For planning,
assume one chef, one waiter and one baker serve roughly 25–40 citizens, and double the staff at
each additional hundred.

### 4.2. Tools and durability

The tool tier a worker can even pick up is set by their hut level [wiki]
https://minecolonies.com/wiki/needs/gear/:

| Tool | Minimum hut level |
|---|---|
| Wood / gold | 0 |
| Stone | 1 |
| Iron | 2 |
| Diamond | 3 |
| Netherite and above | 4 |

Enchantment level is capped separately: hut 0 — unenchanted only, 1 — level 1, 2 — level 2, 3 —
level 3, 4 — level 4, 5 — level 5+. For bows and rods: hut 0–1 unenchanted, 2 — one enchantment,
3 — two, 4 — three. [wiki]

**Practical consequence:** a diamond pickaxe placed in a level-1 hut is simply **not picked up**, and
the worker sits in "waiting for tool". One of the most common causes of idling.

Durability is improved by the Technology chain Strong → Hardened → Reinforced → Steel Bracing →
Diamond Coated, **+90%** in total. [wiki]

### 4.3. Supplying construction

The builder requests materials as a list. The Required Resources tab and the **Resource Scroll** show
what is missing: red — neither you nor the hut has it; green — you have it; black — the builder
already has it. [wiki]

Useful behaviour: the builder keeps blocks they break and dumps them into the hut when the build
finishes. They **will not start the next order until the current one is done**. [wiki]

### 4.4. Warehouse, couriers and the request system

How a request resolves [wiki] https://minecolonies.com/wiki/systems/request/:

1. The citizen looks in their own inventory, then the hut block, then the racks in their hut.
2. Not found — they raise a **request**.
3. In the warehouse — a courier brings it.
4. Not in the warehouse — the system looks for **a worker who can craft it** and issues an order,
   recursively: oak stairs → planks → logs.
5. Nobody can — the request lands on **you**, shown as a red gear over the citizen's head.

Hence the rule: **the more distinct professions are staffed, the fewer requests reach the player.**

Player-side tools:

* **Clipboard** — every request in the colony, from anywhere near it; "Show Important Requests Only"
  hides perpetual ones such as ore for the Smelter. [wiki]
* **Postbox** — request an item from the colony.
* **Stash** — a reverse postbox: couriers collect what is put in it.
* **Pickup Priority** (0–10) per building — affects **collection only**; deliveries from the
  warehouse are always high priority. [wiki]

### 4.5. Bottlenecks by stage

| Stage | What stalls | What to do |
|---|---|---|
| Day 1–3 | No builder / no flat ground | Builder's Hut first, in the centre |
| Day 3–10 | **You are carrying every block by hand** | Warehouse + Courier as early as possible |
| Day 10–25 | Wood and cobblestone | Second Forester, second Mine, Sawmill (via research), Stonemason |
| Day 25–60 | Food: citizens at 0 saturation | Farmer + Chef's Kitchen + Dining Hall, set Minimum Stock |
| Day 60+ | **One builder** — a work-order queue | 3–5 builders with level-5 huts |
| Late | Research time (real hours) | 5 researchers in a level-5 University, always 5 in parallel |
| Late | Rare resources (diamond, emerald, netherite) | Sifter + Crusher, Nether Mine, trade |

---

## 5. Research

### 5.1. Structure

* 4 branches: Civilian, Combat, Technology, Unlockables.
* Column = required University level (1→1, …, 5 and 6 → 5).
* Researchers = University level, max 5. **More researchers do not speed up one research — they run
  different ones in parallel.** [wiki] https://minecolonies.com/wiki/buildings/university/
* Research time is **real time**, while the colony is ticking. From University 3, researchers
  partly catch up on time spent offline; how much depends on the Knowledge skill, capped by Mana.
  [wiki]
* Cost: the items must be **in the player's inventory** when the research starts. Cancelling does
  **not** refund them. [wiki]
* Some research is marked non-cancellable (redstone torch icon).

### 5.2. How long, from the source

Duration formula [upstream] `GlobalResearchBranch.java`:

```
baseTime(depth) = BASE_RESEARCH_TIME * branchBaseTime * 2^(depth-1)
hours(depth)    = baseTime(depth) * 25 / 3600
BASE_RESEARCH_TIME = 3600 / 25 / 2 = 72   (ResearchConstants.java)
branchBaseTime default = 1.0
```

At a branch multiplier of 1.0:

| Column | Real hours |
|---|---|
| 1 | 0.5 |
| 2 | 1 |
| 3 | 2 |
| 4 | 4 |
| 5 | 8 |
| 6 | 16 |

[inferred] Applying the node distribution from 1.3, and taking only one column-6 node per branch:
Civilian ≈ 195 h, Combat ≈ 182 h, Technology ≈ 195 h, Unlockables ≈ 15 h — **≈ 587 hours of
research time**. With five researchers never idle, that is **≈ 117 real hours** as a lower bound,
and only for research; prerequisites usually prevent keeping five running at once.

**Caveat:** `branchBaseTime` can be overridden in a branch's data file; only the default was checked.
Modpack numbers may differ.

### 5.3. What pays off most

**Technology building unlocks first**, because half the economy is behind them. Key nodes and their
requirements [wiki]:

| Unlocks | Requires |
|---|---|
| **Sawmill** | Forester's Hut, 3 levels total + any planks |
| **Fletcher's Hut** | Sawmill 1 |
| **Sifter's Hut** | Fisher's Hut, 3 levels total + string |
| **Stonemason's Hut** | Mine, 3 levels total + chiseled stone bricks |
| **Crusher's Hut** | Stonemason 1 + stone |
| **Stone Smeltery** | Stonemason 1 + brick |
| **Concrete Mixer** | Crusher 1 + any concrete |
| **Smeltery** | Mine, 2 levels total + lava bucket |
| **Blacksmith's Hut** | Mine, 3 levels total + anvil |
| **Mechanic's Hut** | Blacksmith, 3 levels total + redstone |
| **Glassblower's Hut** | Smeltery, 3 levels total + glass |
| **Composter's Hut** | Farmer, 3 levels total + bone meal |
| **Plantation** | Farmer, 3 levels total + compost |
| **Dyer's Hut** | Composter, 3 levels total + poppy |
| **Flowershop** | Composter, 3 levels total + compost |
| **Nether Mine** | 1 gilded blackstone |
| **Alchemist Laboratory** | 1 nether wart |
| **School** / **Library** | Residence, 3 levels total + book |
| **Hospital** | 1 carrot |
| **Graveyard** | Town Hall 2 + bone |
| **Mystical Site** | 1 diamond |
| **Barracks** | Guard Tower, 3 levels total + iron block |
| **Combat Academy** / **Archery** | Barracks, 3 levels total + iron block |

An order that looks optimal [inferred + wiki]:

1. **Column 1 immediately**: Stamina (→Hospital), Keen (→Library), Higher Learning (→School), First
   Aid, Remembrance (→Graveyard). **[!]** In this tree, Stamina and Higher Learning are two of the
   five exclusive roots — taking them commits a branch. See section 0. [VERIFIED]
2. **Technology production unlocks**: Sawmill → Stonemason → Crusher → Smeltery → Blacksmith. This
   closes out building-material crafting and removes the main early-game pain.
3. **Population cap**: Outpost (50) as soon as possible, then Hamlet (100).
4. **Sifter** (via Fisher 3) once a steady ore/sapling stream is needed.
5. **Work speed**: the Citizen Block Place Speed and Citizen Block Break Speed chains (+10/15/25/50/
   100%) — a direct multiplier on build and mining speed.
6. **Tool durability** (Strong…Diamond Coated, +90%) — takes load off the blacksmith and warehouse.
7. **Night shift**: Night Owl and Night Owl II add **+2 hours** of working day in total.
8. **Combat** — only once raids start killing citizens (section 6).
9. **The exclusive nodes** — deliberately, because only one per branch is ever active:
   * Civilian: Theater (+30% happiness) / Academic (+50% XP) / Guardian Angel II (+10 HP)
   * Combat: Savage Strike (+2 knight damage) / Master Swordsman (+50% armour) / Full Retreat
   * Technology: Motherlode (+100% miner ore) / Madness (+100% block placement speed) / Magic
     Compost (+125% crop yield)

---

## 6. Military

### 6.1. How raids work

[wiki] https://minecolonies.com/wiki/systems/raid/

* A raid starts **only at nightfall**. The direction is announced in chat and a progress bar appears.
* Raiders spawn almost anywhere **except near buildings** — with a scattered town, they can spawn
  between the buildings.
* **During a raid, citizens drop their work and run home.**
* Raider type depends on the spawn biome:

| Biome | Type |
|---|---|
| Taiga | Nordic |
| Desert | Mummy (a Pharaoh may appear, dropping a sceptre bow) |
| Large water body | Pirate (a ship with spawners, which must be broken) |
| Jungle | Amazon |
| Everything else | Barbarian |

* Raiders **break blocks, place ladders, bridge gaps, and swim through water and lava unharmed**.
  Walling them out generally does not work. Hitting a raider too hard reflects damage back at the
  attacker; turrets from other mods do not affect them.
* If a non-guard citizen dies, their family **does not work the next day** (mourning). Guards are not
  mourned.
* A raid survived without losses gives a colony-wide happiness bonus. [wiki]

### 6.2. What drives raid strength

**[!]** The wiki says outright that this is undocumented:

> "As you defeat more raiders and develop your colony, the raids will increase in difficulty. How
> quickly they increase in difficulty or what affects their difficulty is not publicly known."

What is certain comes from config [config] https://minecolonies.com/wiki/misc/configfile/:

| Setting | Default | Range |
|---|---|---|
| `doBarbariansSpawn` | true | — |
| `barbarianHordeDifficulty` | **5** | up to 10 |
| `maxBarbarianSize` | **80** | 6–400 |
| `averageNumberOfNightsBetweenRaids` | **14** | 1–50 |
| `minimumNumberOfNightsBetweenRaids` | **10** | 1–30 |
| `doBarbariansBreakThroughWalls` | true | — |
| `shouldRaiderBreakDoors` | true | — |

So by default a raid arrives **no more often than every 10 nights, on average every 14**, with up to
80 in a horde. Large colonies get several waves from different directions, possibly of different
types.

### 6.3. What to build

**Guard Tower** — one guard each; patrol radius grows 80 / 110 / 140 / 170 / 200. Towers at the
colony edge **extend the claim** (2/3/3/4/5 chunks). [wiki]

**Barracks** — maximum defence. It holds Barracks Towers, each giving **one guard per tower level,
up to 5**. Official styles contain **4 towers → 20 guards per Barracks**. [wiki]

| Barracks level | Towers | Max tower level |
|---|---|---|
| 1 | 1 | 1 |
| 2 | 2 | 2 |
| 3 | 3 | 3 |
| 4 | 4 | 4 |
| 5 | 4 | 5 |

**Important:** every new guard needs a **free Residence bed to appear at all**; once hired into a
tower, the bed is released again. [wiki]

**Barracks 3+ unlocks "Hire Spies"**: raiders glow during a raid, which is how you find the last
stuck enemy and end it. [wiki]

### 6.4. Guard types

| Type | Needs | Safe training |
|---|---|---|
| **Knight** | sword (optionally shield, armour) | Combat Academy |
| **Archer** | bow (optionally arrows, armour) | Archery |
| **Druid** | throws potions at allies | no dedicated building |

### 6.5. Preparation order

1. **Before the first raid (first ~10 nights)** — 2–3 Guard Towers around the perimeter. Enough.
2. **Accuracy** research (Guard Tower 1 level total + iron ingot) — critical hits.
3. **Tactic Training** (Guard Tower 3 levels total + iron block) → **Barracks**.
4. Barracks 3 → **Combat Academy** and **Archery** (both need Barracks 3 levels total + iron block).
5. Armour and damage: Parry/Duelist/Provost (knights, up to +50% armour), Dip/Dive (archers), Iron
   Skin → Steel Armor → Diamond Skin (armour durability, up to +90%).
6. **Avoidance** (Guard Tower 3 levels total) — knights start using shields.
7. **Happiness:** "at least 2 guards per 3 citizens" is the ceiling of the security factor; partial
   progress counts. [wiki]

---

## 7. Dead ends and common mistakes

The main troubleshooting source is a mirror of the old official wiki — `wiki.minecolonies.com` no
longer resolves: https://sqwatermark.github.io/MinecoloniesWiki/source/misc/troubleshooting
[community]

### 7.1. Planning mistakes, irreversible or expensive

1. **Town hall in a bad spot.** The colony zone is fixed forever; moving it means an operator
   deleting the colony. Scout 8×8 chunks first.
2. **Supply Camp/Ship wasted.** One per world.
3. **Builder's hut off in a corner.** The builder walks; late game this is tens of minutes lost.
4. **Buildings spread over a large area.** Couriers walk further, raiders spawn **between**
   buildings, and citizens complain about the commute — the complaint starts above 100 blocks from
   home to work. [wiki]
5. **One builder for the whole game.** A builder will not take the next order until the current one
   is finished.

### 7.2. "The worker is standing still"

Check in this order [community]:

1. **Night, rain or snow** — no work by default; rain is removed by the "Raindrops are falling on my
   head…" research.
2. **Mourning** — someone died; the whole family is off the next day.
3. **A raid is running** — everyone hides until the last raider dies; find them with Hire Spies.
4. **The hut is paused** — huts have a pause button, and it is easy to forget.
5. **Hunger** — at 0 saturation work stops.
6. **An open request** — a missing tool or material.
7. **Wrong tool tier** — a diamond pickaxe in a level-1 hut will not be picked up (4.2).
8. **Full inventory** — nowhere to put the output.
9. **Low hut level or low experience** — low-level workers do stall between actions; that is normal.

Escalating fixes: recall workers to the town hall → pause the hut for a couple of minutes → unpause
→ recall → fire and rehire → clear the worker's and the hut's inventory and give back **only what
was requested** → Repair the hut → `/mc colony requestsystem-reset`.

This list is the specification for a `problems()` accessor on a citizen handle: nine causes, each
observable server-side, none of them currently visible from outside.

### 7.3. "The builder is not building"

* **Build Building was never pressed.** Placing the block does not start construction.
* The builder is busy with another order.
* The target level exceeds the Builder's Hut level — upgrade the hut first.
* Materials missing — check Required Resources / Resource Scroll for red entries.
* Cancelling mid-build and re-issuing **resumes** where it stopped; deleting the work order restarts
  it. [wiki]

### 7.4. "Starving"

* No Dining Hall / Restaurant → citizens ask the player directly.
* **Fuel not enabled in the Dining Hall**: by default **all fuel types are off** and the cook can
  cook nothing. Non-obvious and very common. [wiki] https://minecolonies.com/wiki/buildings/cook/
* Food exists but is the wrong tier: citizens with high-level homes refuse tier 1.
* Too much vanilla food → happiness penalty.
* Farmer: no field assigned, no seeds in the scarecrow, unhydrated soil, no hoe or axe. The farmer
  also **requires the daylight cycle to be on**. [wiki]

### 7.5. "The colony is not growing"

* No free beds — the cap is beds.
* The research ceiling has been reached (25 → Outpost → Hamlet → Village → City).
* "New citizens spawning" is switched off in the town hall settings.
* Children are only born if the colony has at least one man and one woman, and a child needs a bed
  too. [wiki]

### 7.6. Technical traps

* **`doDaylightCycle false` breaks the game.** The wiki warns that turning off the day cycle has
  unpredictable effects on raids, farming, disease and children growing up. [wiki]
* **OptiFine** — the usual cause of missing hut textures, broken build-tool previews and citizen
  GUIs that will not open. [community]
* **MineColonies/Structurize version mismatch** — crashes on placing structures. [community]
* Multiple warehouses: **a courier only sees their own warehouse**. [wiki]
* Miner: placing the Mine hut **below** its maximum depth leaves the miner refusing to work and
  demanding an upgrade. Keep the hut at least 4 blocks above the level's depth limit. [wiki]
* Do not help the miner dig: hitting air while sinking the shaft is read as a cave, and no platform
  is built. [wiki]

---

## 8. Timings

**Honest caveat:** no reproducible "this stage takes N days" measurements exist in the open sources —
not on the wiki, not in forums, not in video descriptions. Only the mechanical constants are firm.
Constants first, estimates after, marked as estimates.

### 8.1. Firm constants

| Quantity | Value | Source |
|---|---|---|
| Starting citizens | 4 | [config] |
| Minimum nights between raids | 10 | [config] |
| Average nights between raids | 14 | [config] |
| Research time, column 1 / column 6 | 0.5 h / 16 h real time | [upstream] |
| Full tree (max reachable) | ≈ 587 h of research, ≈ 117 h with 5 researchers | [inferred] |
| Farmer | **one action per field per day** (till, plant **or** harvest) | [wiki] |
| Sifter | 64 / 256 / 576 / 1024 / ∞ blocks per day | [wiki] |
| Crusher | 16 / 64 / 144 / 256 / 999 items per day | [wiki] |
| Working-day extension | +2 h total (Night Owl + Night Owl II) | [wiki] |
| Homelessness complaint | after 2 weeks without a home | [wiki] |
| Sleep complaint | after 3 consecutive nights without a bed | [wiki] |

From "one action per field per day" it follows directly that a one-field farm is literally one
operation per game day. A level-5 Farmer's Hut (5 fields) and several farmers are a necessity, not a
luxury.

### 8.2. Stage estimates [inferred]

Single-player, no modpack accelerators, active play — the player hauls resources early on:

| Stage | Game days | Real time |
|---|---|---|
| Supplies → colony founded → Builder's Hut 1 | 1–2 | 30–60 min |
| First 4 citizens housed, food working | 3–6 | 1–3 h |
| Warehouse + Courier running (the colony "starts") | 8–15 | 3–6 h |
| University 1 and first research | 12–20 | 5–10 h |
| First raid | night 10 at the earliest, 14 on average | — |
| Core production (Sawmill, Stonemason, Smeltery, Blacksmith, Crusher, Sifter) | 30–60 | 15–40 h |
| Town Hall 5, Builder's Hut 5, population 100+ | 100–200 | 60–150 h |
| "Everything built, everything researched" | — | **hundreds of hours**; research alone floors it at ≈ 117 h |

The spread is wide because research time is **real**, not in-game, and does not compress when the
game speeds up. Even a perfectly supplied colony with five researchers runs into that 117-hour
floor. Full completion is a weeks-to-months project, not a weekend.

---

## 9. Can it be finished on a superflat world, without cheats?

**"Without cheats" means:** ordinary survival, no `/give`, no `/gamemode creative`, no external world
editing. World-generation settings chosen at creation — the superflat preset, the "Generate
Structures" checkbox — are not cheats; they are part of creating a world honestly. Editing the mod
config is a borderline case, discussed in 9.8.

### 9.0. The answer in one line

> **Yes — but only on a superflat preset with `decoration` (ore and trees), `lake` + `lava_lake`
> (water and lava) and structure generation. Exactly one of the nine standard presets qualifies:
> "Overworld". On "Classic Flat" a colony can be built but never finished: no lava means no
> obsidian, no obsidian means no Nether, and without the Nether some buildings and research are
> locked out permanently.**

### 9.1. The fact that changes the picture

**Superflat only changes the Overworld. The Nether and the End generate normally.**

> "Superflat worlds allow the player to access the Nether and the End in the usual ways, which
> generate as normal."
> [minecraft.wiki] https://minecraft.wiki/w/Superflat

So the question reduces to: **can the Nether be reached honestly**, i.e. can obsidian be obtained.
Obsidian = lava + water, or natural obsidian, or a ruined portal.

### 9.2. What the standard presets provide

[minecraft.wiki] https://minecraft.wiki/w/Superflat

| Preset | Layers | Biome | Structures | `decoration` (ore/trees) | Lakes/lava |
|---|---|---|---|---|---|
| **Classic Flat** | grass, 2 dirt, bedrock | Plains | villages only | ✘ | ✘ |
| **Tunnelers' Dream** | grass, 5 dirt, **230 stone**, bedrock | Windswept Hills | strongholds, mineshafts, spawner rooms | **✔** | ✘ |
| **Water World** | 90 water, gravel, dirt, stone, deepslate | Deep Ocean | monuments, ruins, shipwrecks | ✘ | ✘ |
| **Overworld** | grass, 3 dirt, **59 stone**, bedrock | Plains | **villages, outposts, strongholds, mineshafts, rooms, ruined portals** | **✔** | **✔ (incl. lava_lake)** |
| **Snowy Kingdom** | snow, grass, 3 dirt, 59 stone | Snowy Plains | villages, igloos | ✘ | ✘ |
| **Bottomless Pit** | grass, 3 dirt, 2 cobblestone (no bedrock) | Plains | villages | ✘ | ✘ |
| **Desert** | 8 sand, 52 sandstone, 3 stone | Desert | villages, pyramids, strongholds, mineshafts | ✘ | ✘ |
| **Redstone Ready** | 116 sandstone, 3 stone | Desert | none | ✘ | ✘ |
| **The Void** | air | Plains/Void | none | ✔ | ✘ |

The "Overworld" preset code, from open sources:
`3;minecraft:bedrock,59*minecraft:stone,3*minecraft:dirt,minecraft:grass;1;stronghold,biome_1,village,decoration,dungeon,lake,mineshaft,lava_lake`
[community] — the `decoration` flag is what adds **ore, trees, tall grass and flowers**; `lava_lake`
adds lava.

**So "Overworld" is essentially a normal Overworld that happens to be flat, with 59 layers of stone.
Everything in MineColonies works there.**

### 9.3. Ore

**Is there ore in a flat world?** It depends on `decoration`, not on flatness:

* "Overworld" and "Tunnelers' Dream" — ore **generates**.
* "Classic Flat", "Desert", "Snowy Kingdom", "Redstone Ready" and the rest — **no ore at all**.

**Does the miner work with only a few layers and bedrock underneath?** The miner sinks a shaft to a
Y level set by the hut level. [wiki] In "Classic Flat" there are 2 dirt and then bedrock — nothing to
dig, the mine is inoperable. [inferred] The wiki also warns that a Mine placed below its depth limit
makes the miner refuse to work. In "Overworld" (59 layers) and "Tunnelers' Dream" (230) the mine
works normally.

**Getting ore without ore generation** — this is where the mod gives real leverage.

**Sifter** — sifts dirt, gravel, sand and soul sand, turning a block into loot [wiki]:

| Sifted | Possible output |
|---|---|
| **Dirt** | beetroot/wheat/melon/pumpkin seeds, carrot, potato, **oak, spruce, birch, jungle, acacia, dark oak saplings** |
| **Gravel** | **coal, diamond, lapis, emerald, flint, gold ingot, iron ingot, iron nugget, redstone** |
| **Sand** | cactus, cocoa beans, gold nugget, sugar cane |
| **Soul sand** | blaze powder, glowstone dust, magma cream, nether wart, quartz, skulls |

That is: **dirt → saplings (wood out of nothing), gravel → iron and diamonds**. A flat world has
unlimited dirt.

**Crusher** closes the loop [wiki]:

```
cobblestone → gravel → sand → clay
bone / bone block → bone meal
```

So **cobblestone → gravel → Sifter → diamonds/iron/coal/emeralds**. Default ratio 2:1; the Gilded
Hammer research makes it 1:1.

But starting that machine needs:

* the Sifter research, which needs **Fisher's Hut 3 levels total + string** → a 7×7×2 water body;
* meshes, made by the Fletcher (string mesh, Sifter 1), Stonemason (flint, Sifter 3), Blacksmith
  (iron, Sifter 4), Mechanic (diamond, Sifter 5) — the Unlockables branch;
* the Crusher, unlocked via the Stonemason, which needs **Mine 3 levels total** — and a Mine is
  pointless in a world with no stone.

**The Quarry does not help with ore:** the wiki states it yields **only the blocks it digs and
produces no extra ore, unlike the Mine**. [wiki] The quarrier also has to be hired at the Mine
first.

**Nether Mine, an important subtlety:** the nether worker **does not actually travel to the Nether or
break blocks there** — the building generates loot virtually, by level (level 1: nether quartz,
gold, ender pearls, blaze rods…; level 5: **ancient debris**). It can also craft a **lava bucket**.
[wiki] **But** the research that unlocks it needs **1 gilded blackstone**, which only occurs in
Nether bastions. The circle closes: to get a free Nether you must first reach the Nether. [inferred]

**Fisher:** food and junk; treasure, including enchanted books, only in an ocean biome — elsewhere
only fish and junk. [wiki] No ore.

**Available at the start without iron:** none of the above. Sifter, Crusher, Smeltery and Blacksmith
all sit behind a research chain rooted in the Mine and the Fisher, and the Mine needs stone.

### 9.4. Wood is the real opening barrier

Without wood there is no **build tool** (planks and sticks), no chests for the Supply Camp, no town
hall block, no builder's hut. **Without the first piece of wood the game does not start at all.**

A flat world has no trees unless `decoration` is on. Honest sources of the first wood:

| Source | Works? | Note |
|---|---|---|
| **Villages** | ✔ if structures are enabled | Village houses are logs and planks, breakable by hand. Villages generate even in "Classic Flat". The standard route. [inferred from preset data] |
| **Village chests** | ✔ | Saplings, seeds, sometimes emeralds and iron |
| **Village farms** | ✔ | Wheat, potatoes, carrots, beetroot — solves food immediately |
| **Trading with villagers** | ✔ | Emeralds → purchases; villagers are renewable by breeding |
| **Wandering trader** | partly | Sells saplings, but for emeralds, which must come from somewhere |
| **Fishing** | ✘ | No saplings in the fishing loot table |
| **Bone meal on grass** | ✘ | Gives grass and flowers, not saplings |
| **Sifting dirt** | ✔, but late game | Needs a working colony, which needs wood. Chicken and egg at the start, but an excellent way to make wood renewable afterwards |
| **Custom preset with a log layer** | ✔ | Any block can be set as a layer at world creation, `oak_log` included. That is world generation, not a cheat |

**So: with structures off AND `decoration` off, MineColonies cannot be started at all. That is an
absolute blocker.** [inferred]

### 9.5. Water and food

**Water** is needed for the Supply Ship (32×20), the Fisher's Hut (7×7×2), and hydrating farmer
fields.

* "Overworld" — lakes generate. ✔
* "Water World" — plenty of water, no land for a colony. ✘ in practice
* "Classic Flat", "Tunnelers' Dream", "Desert" — no surface water. Sources:
  * **a plains village well** — source blocks ✔, available anywhere villages generate;
  * **a cauldron filling from rain** → bucket → two sources → infinite water. Costs 7 iron.
    [inferred]
* **A Supply Ship cannot be placed in a flat world** without hand-digging a 32×20 pool, so the start
  is effectively always a **Supply Camp**, which only needs a cleared ~16×17 patch — trivial on flat
  ground. ✔

**Food.**

* Farmer: works given seeds and hydrated soil. Vanilla seeds come from grass (if `decoration` is on)
  or from village farms and chests.
* **MineColonies crops drop from grass, ferns, seagrass and dead bushes** [wiki] — so **without
  `decoration` there is no tall grass and no way to obtain them**, which puts tier 2–3 dishes out of
  reach. [inferred]
* Animal herders (Cowhand, Shepherd, Chicken Farmer, Swineherd) need starting animals. In a flat
  world animals **spawn at world generation on grass** — "Classic Flat" and "Overworld" are Plains
  with a grass surface, so they do. Breeding takes it from there. ✔ [inferred]
* Fisher: needs 7×7×2 water, above.

**A separate limit:** a flat world has one biome everywhere. Mod crops are split by biome category,
and some tier-3 dishes need ingredients from **different** biomes. [wiki] In a one-biome world those
dishes are unreachable except through trade with another colony (Colony Connections, requiring a
Gatehouse at both ends). In single-player that can close off part of the top food tier. [inferred]

### 9.6. Raids and mobs

* **Mobs spawn normally.** Flatness does not prevent hostile spawns at night. Skeletons → bones →
  bone meal; spiders → string (needed for the Sifter research); zombies → occasional iron.
* **MineColonies raids work.** Raiders spawn around the colony, not from world generation. [wiki]
* **Raider type follows the spawn biome.** With one biome you always get the same type: Plains →
  **Barbarian**, Desert → **Mummy**, Snowy Plains → Barbarian, Windswept Hills → Barbarian. Pirates
  never appear without a large water body. [inferred]
* **Raid difficulty does not depend on world type** — it follows colony development and config
  (`barbarianHordeDifficulty` 5, `maxBarbarianSize` 80). The exact formula is undocumented. [wiki]
* In practice raids are **more dangerous** on flat ground: no terrain to anchor a defence, and the
  whole colony has line of sight through it. [inferred]

### 9.7. Preset by preset

| Preset | Colony start | Ore | Full completion | Blocker |
|---|---|---|---|---|
| **Overworld** | ✔ | ✔ | **✔ YES** | Nothing. One biome → some tier-3 dishes need a second colony |
| **Tunnelers' Dream** | ✔ (trees from `decoration`) | ✔ | **probably ✔** | No lakes, no lava. Water via a cauldron in rain. Lava only if `decoration` generates underground lava springs — **unconfirmed** → Nether access uncertain [inferred, low confidence] |
| **Classic Flat** | ✔ only with structures on (wood from villages) | ✘ | **✘ NO** | No stone → no Mine → no Stonemason/Crusher/Smeltery/Blacksmith. No lava → no obsidian → no Nether → **Nether Mine and Alchemist Laboratory unreachable forever** |
| **Desert** | ✔ (villages, pyramids) | ✘ | **✘ NO** | As above, plus no water and no grass |
| **Snowy Kingdom** | ✔ | ✘ | ✘ | As Classic Flat |
| **Bottomless Pit** | ✔ | ✘ | ✘ | Plus the risk of falling into the void |
| **Redstone Ready** | ✘ | ✘ | ✘ | No structures → no wood at all |
| **Water World** | ✘/✔ | ✘ | ✘ | Nowhere to place a town hall without landfill |
| **The Void** | ✘ | ✘ | ✘ | Nothing at all |
| **Custom** (stone layers + structures on) | ✔ | depends | depends | The `decoration`/`lake`/`lava_lake` flags are not exposed in the 1.20/1.21 Java UI — they are inherited from the chosen preset and have to be changed through JSON or a datapack [inferred] |

### 9.8. What makes completion outright impossible

With no lava, no ruined portals and no natural obsidian:

1. No obsidian → **no Nether portal**.
2. No Nether → no gilded blackstone → **the Nether Mine research is unreachable forever**, and the
   Nether Mine is one of the 51 buildings.
3. No nether wart → **Alchemist Laboratory unreachable forever**.
4. No soul sand → an entire Sifter loot branch disappears (quartz, glowstone, magma cream).
5. Enhanced Gates II needs **1 obsidian** — unreachable.
6. Scuba needs a **Heart of the Sea**, buried-treasure only — unreachable without an ocean.
7. Theater (an exclusive Civilian node) needs an **enchanted golden apple**, found only in structure
   chests, and "Classic Flat" has only villages. Not fatal, since only one node per branch is ever
   active, but it narrows the choice.

**So "Classic Flat" and the other stone-free, lava-free presets allow a pleasant colony but not
completion as defined in section 1.**

**On config.** The mod ships options aimed at unusual worlds [config]:

* `noSupplyPlacementRestrictions` — "Disables supply camp placing restrictions, intended for
  **skyworlds and similar**";
* `skyRaiders` — raiders spawn in the air.

Editing config is not a cheat in the "gave myself items" sense, but it **changes the rules**. Playing
honestly means leaving them alone, or recording them as a condition of the run.

**On modpacks.** The community solves the flat-world problem with packs rather than honest play:
**Superflat Colonies** (flat world plus Ars Caelum so unobtainable resources can be gathered — "ores
can be gathered by buffed colony's miner"), and **Flattened Dimensions** (flat, but with caves,
biomes, ore, rivers and oceans, Y64…Y−64). That these packs exist is indirect confirmation
[community] that vanilla superflat does not suit MineColonies unmodified.

### 9.9. Minimum conditions for a superflat run

1. The **"Overworld"** preset, or a custom one with `decoration`, `lake`, `lava_lake` and at least
   ~50 stone layers.
2. **"Generate Structures" on** — villages as the source of starting wood, iron and water, and
   ruined portals.
3. Everything else as in a normal world: Supply **Camp** (not Ship), town hall on cleared ground,
   then section 2.
4. Accept that some tier-3 dishes will need a second colony in a different biome — which does not
   exist — and are therefore unavailable. That is the only partial limitation on a fully correct
   preset. [inferred]

---

## 10. Appendix: reference tables

### 10.1. Beds and population

| Bed source | Beds | Note |
|---|---|---|
| Residence level N | N (max 5) | unlimited number of Residences |
| Tavern | 4 | caps at level 3, adds no beds |
| Guard Tower | 1 (for the guard) | the guard must first appear via a Residence bed |
| Barracks Tower level N | N guards (max 5) | 4 towers per Barracks = 20 guards |

### 10.2. Tool tier by hut level

| Hut level | Max tool | Max enchantment |
|---|---|---|
| 0 | wood/gold | none |
| 1 | stone | 1 |
| 2 | iron | 2 |
| 3 | diamond | 3 |
| 4 | netherite+ | 4 |
| 5 | netherite+ | 5+ |

Source: https://minecolonies.com/wiki/needs/gear/

### 10.3. Mine depth

**[!]** The Mine page carries two descriptions that do not agree. [wiki]

**A (by ore levels):** the miner digs to the first "ore level" below the hut, plus one level per
building level. Ore levels: copper Y48, iron Y16, gold Y−16, diamond at bedrock.

**B (a table on the same page):**

| Mine level | Shaft Y |
|---|---|
| 1 | 40 |
| 2 | 20 |
| 3 | 0 |
| 4 | bedrock |

Table B is probably left over from an older version. The practical conclusion is the same either
way: **Mine level 3–4 is needed to reach the diamond layer.**

### 10.4. Claim by building

| Building | L1 | L2 | L3 | L4 | L5 |
|---|---|---|---|---|---|
| Town Hall | 1 | 1 | 2 | 3 | 5 |
| Guard Tower | 2 | 3 | 3 | 4 | 5 |
| Barracks | 2 | 2 | 2 | 2 (3 with all towers at 4) | 2 |
| Everything else | 1 | 1 | 1 | 2 | 2 |

Source: https://minecolonies.com/wiki/systems/border/

### 10.5. Happiness: three base factors

| Factor | Threshold for maximum |
|---|---|
| Saturation | above 5, ideally full |
| Housing | home above level 2.5 (for guards, their workplace) |
| Security | at least 2 guards per 3 citizens |

Also positive: a raid survived without losses, nearby Guard Towers, having a profession, an available
bed. Negative: illness, homelessness, unemployment, "nothing to do at work", a citizen's death
(3 days of mourning), injury.

Source: https://minecolonies.com/wiki/needs/happiness/

### 10.6. Sources

**Official wiki (primary, version 1.21.1):** tutorials/getting-started, items/supply_camp_and_ship,
buildings/{townhall, builder, residence, tavern, warehouse, deliveryman, university, cook, kitchen,
farmer, fisherman, lumberjack, miner, quarry, sifter, crusher, netherworker, guardtower, barracks},
systems/{research, request, raid, border, colonyconnections, quest}, needs/{food, gear, happiness,
sleep}, misc/configfile — all under https://minecolonies.com/wiki/

**Upstream source:** https://github.com/ldtteam/minecolonies, branch `version/main` —
`BuildingTownHall.java` (MAX_BUILDING_LEVEL), `GlobalResearchBranch.java` and
`ResearchConstants.java` (research time formula), `RegisteredStructureManager.java` (absence of a
town-hall gate).

**This repository:** `26.3/src/main/java/com/minecolonies/core/colony/workorders/WorkOrderBuilding.java`,
`.../buildings/workerbuildings/BuildingTownHall.java`, `26.3/src/main/generated/data/minecolonies/researches/`.

**Vanilla Minecraft:** https://minecraft.wiki/w/Superflat

**Community (lower confidence):**
https://sqwatermark.github.io/MinecoloniesWiki/source/misc/troubleshooting (mirror of the old
official wiki), https://www.curseforge.com/minecraft/modpacks/superflat-colonies,
https://github.com/Quidvio/Flattened-Dimensions

**Deliberately not used:** a family of auto-generated articles titled "Minecolonies Town Hall Levels:
Your Ultimate Guide", reproduced verbatim across dozens of unrelated domains. They contain
plausible but unsupported claims — notably "Town Hall 4 = 150 citizens, Town Hall 5 = 500" without
mentioning that these are the Village and City researches, and "the town hall level caps building
levels", which section 0 disproves against this tree. Treat numbers from such pages as unverified.
