# Added commands and toggles

Everything below was added on top of upstream MineColonies, mostly to make the
Fabric / 26.2 port testable without playing through the mod by hand. All the
commands are operator-only. `<colony>` accepts a colony id, `here`, `mine`, or a
player name.

## Commands

### `/mc colony diagnose <colony>`

Reports what is stuck. Per employed citizen: job, AI state, how long that state
has held. Also citizens whose entity is loaded but has no worker AI at all,
citizens with a job but no work building, requests no resolver took, work orders
with no builder, and buildings whose level or blueprint state is inconsistent.

The full report goes to the server log; chat gets a capped version.

**Couriers no warehouse has taken on.** A courier needs to be on two assignment
lists, not one: the Courier's Hut hires him, and a warehouse adopts him. Missing
from the second he does not merely idle — his AI is never started at all, so he
stands where he was spawned for ever while the warehouse queue fills up behind
him. The report now names every such courier, where he was hired, and every
warehouse with its courier occupancy and hiring mode, because "no warehouse has
room", "the colony has no warehouse" and "that warehouse is set to manual" are
three different repairs and the counts are what tell them apart. It counts as a
problem, so a colony with one no longer reports `No problems found`.

**Border patrols.** When a barracks has been set to walk a border (its `Border
Patrol` setting), the report ends with a section saying where those patrols are:
the barracks and its mode, how many waypoints the stretch has or why none were
found, each tower's slice of it and the point it is heading for, and every guard
by name with where he is standing and how far that is off his own stretch. A guard
more than 100 blocks off it is called out in words. This is the answer to "where
are my patrols and what are they doing", including the one that has ended up
somewhere odd. Colonies with no barracks, or a barracks with the setting off, add
nothing to the report and pay one string compare for it.

A **Stable** set to the `Border Patrol` task appears in the same section, but per rider
rather than per tower: a Stable is one building with a whole troop in it, so the report
names each cavalryman, the arc of the line he holds, and where he is standing relative to
it. Two riders sharing an arc is the failure that route is built to make impossible, and
this is where you would see it.

**Run it twice, a minute apart.** The mod does not record how long a citizen has
been in an AI state anywhere, so the command remembers what it saw last time and
compares. The first run prints `held=new` for everything.

### `/mc citizens fill <colony>`

Spawns citizens up to the `maxcitizenpercolony` config value, then hires every
unemployed citizen into an empty job slot. Gets as many different worker AIs
running at once as the colony has buildings for. Ends by naming the job types
that still have no worker.

It deliberately ignores the colony's own population cap, which is normally the
number of beds in built residences — otherwise you would have to build a house
per citizen before this command could do anything. Citizens past the bed count
are homeless and unhappy; the command says how many.

### `/mc citizens fill <colony> children`

The same fill, but everything it spawns is a child — a colony that has to grow up
rather than one that arrives fully formed. Pairs with the `generations` config
below, though it works without it.

**An argument on `fill`, not a command of its own**, because only one line
differs: the cap, the spawn loop, the hiring pass and all five report lines are
shared, and a second command would be three hundred lines of copy.

**It still hires, and that is not the contradiction it sounds like.** Every job
module in the game except the school's refuses a child outright, and `fill`
already knew that — its candidate selection asks each module whether it wants
children before offering it anybody. So the hiring pass can only put the new
children into a school, while the adults who were already in the colony keep
being hired into their jobs exactly as before. Filling with children and hiring
therefore means "fill the school", which is the only thing a town of children can
be hired to do.

**Children take no bed, but they do need a family.** A child lives with its
parents and holds no housing slot until it grows up, so the command's homeless
line no longer counts them. What it does instead is put each spawned child into a
house that already has adults in it, round robin, the way a birth would — without
that they would be permanently homeless, because the automatic housing pass
deliberately skips children and there is no birth here to give them a home. A
colony with no inhabited residence yet leaves them homeless, which is correct and
is what the homeless line then reports.

**The school picks them up on its own**, with or without this command — the pupil
module grabs any unemployed child on every colony tick while auto-hiring is on.
Running `fill children` just does it immediately instead of within 25 seconds.

They grow up normally, on the game's own child timer, so this is a slow-motion
start and not a permanent kindergarten.

### `/mc citizens hire <colony> <citizen> <pos> <job>` and `/mc citizens fire <colony> <citizen>`

Puts one named citizen into one named job slot, and takes him out again.

```
/mc citizens fire 1 3                          #3 gives up whatever he holds
/mc citizens hire 1 6 20 -60 0 marksman        #6 becomes the marksman of the tower at 20, -60, 0
```

**Every other way into a job goes through the hiring queue.** A hut's assignment
module hires on the colony tick, out of the colony's jobless list, and only when
the hut's hiring mode allows it and the module has room; the hut GUI's hire button
is the same call reached through a packet, and needs a client. On a server with
nobody connected there is no third way — so *which* of a guard tower's four jobs
gets filled, knight, ranger, marksman or huscarl, is a coin toss on a colony tick,
and a test that needs a **marksman** can only wait and hope. That is what this is
for.

It is the same assignment the GUI makes, with two gates opened around it:

* **The citizen's current job is given up first.** A job refuses a module whose
  job entry is not the one it already holds, so a knight can never become a ranger
  through the ordinary path — the GUI never offers it one, because it only lists
  jobless citizens. Here the old post is vacated and the new job is made fresh.
* **The hiring mode is not consulted.** Automatic hiring is a policy for who the
  colony picks on its own; an operator naming a citizen has already picked.

**The slot count is not stepped over.** A guard tower holds one guard, and its
four job modules share that one slot, so hiring a ranger into a tower that already
has a knight is refused and the occupant named. Emptying it is a separate decision
with its own command, and a hire that silently sacked somebody would be a poor
thing to type by accident. `fire` is therefore usually the first half of moving a
guard from one weapon to another.

`<job>` is the job's registry path — `knight`, `ranger`, `marksman`, `huscarl`,
`builder` and so on. Tab completion at that argument lists exactly the jobs the
building at `<pos>` offers, because the position has already been parsed by then.

A fired citizen keeps living in the colony and goes back on the jobless list,
which is where automatic hiring draws from, so a colony left to itself will
eventually re-employ him somewhere.

Operator only. Unlike `buildnow` it does not need free mode: hiring is something a
colony does anyway, and this only decides who and where.

### `/mc colony growChildren <colony>`

Grows up **every child in the colony at once**, and is the way out of a save full
of children who will never grow up on their own.

They will not, because the child timer is not a calendar: it counts the time the
child's **AI has actually been active**. A colony in a corner of the world nobody
visits does not tick its citizens, so a child left there stays a child for as long
as the save lives. Test worlds, restored backups and colonies that were abandoned
for a while all end up in that state, and the only alternative was killing the
children one at a time and waiting for the colony to breed replacements.

**It is the same transition the AI performs**, `setIsChild(false)`, not a new one:
the housing pass picks them up as adults on its next round, the model and skin
swap, and jobs open to them. A citizen grown by this command is indistinguishable
from one who grew up by living.

Children orphaned by old tests are usually the ones found attached to a tavern —
a child holds no bed of its own, and the orphan pass hands the homeless ones to
whatever living quarters will take them. Growing them up releases them into
ordinary housing.

### `/mc citizens maxstats <colony>`

Every citizen to the maximum level in every skill, plus full saturation and
health. Goes through the mod's own level-up hook, so a guard's health bonus is
applied rather than left at level one.

### `/mc citizens heal <colony>`

Cures every ill citizen and puts everyone back to full health. A sick citizen
stops working and walks to the hospital, and so does a badly hurt one, so a
freshly filled colony can stall for reasons that have nothing to do with what
you are testing.

Curing goes through the game's own cure, so the citizen also gets out of its
hospital bed and the usual post-cure immunity — about 45 minutes, or 90 with the
vaccine research. It is not permanent: once immunity lapses a citizen can fall
ill again, so run this again if the colony stalls. Diseases cannot be switched
off entirely, but the `diseasemodifier` server config makes them rarer the higher
it is set, up to 100.

### `/mc colony research completeall <colony>`

Completes the research tree — parents first, no item cost, no university needed.
Effects are recomputed and pushed to the client, so huts unlock without a relog.

Where a branch only allows one of two alternatives, one is taken and the skipped
subtree is listed by name. With the shipped datapack that means 184 of 208
researches are completed.

### `/mc colony teachRecipes <colony>`

Teaches every crafter every recipe it is allowed to learn, so the request → craft
→ deliver chain can be exercised without teaching recipes one at a time through
the hut GUI.

Run `research completeall` first — it raises the per-building recipe limit. That
limit is `2 ^ hut level`, times five for dedicated crafters, times the recipe
research bonus, and it is well below the number of vanilla recipes, so the
command reports per building how much it could not fit.

Architect's cutter recipes are skipped on purpose: the recipe list this reads
carries display approximations with the material stripped, so teaching from it
would produce recipes that craft untextured blocks.

### `/mc raid <colony> now` and the rest of the raid command

Upstream's raid command, with the delay taken out and three things added.

**It starts now.** A raid used to be created and then sit there: the event only
begins on the colony's slow tick, which runs every 500 ticks, and a ground raid
then waits at its campfires for three to six more of those before moving. Asking
for a raid and waiting up to three minutes for it is not what the command says it
does. A raid asked for with `now` starts as soon as its spawn path has been
computed — a fraction of a second, normally — and skips the campfire wait. The
path is still waited for, because a raid started without one loses the waypoints
that lead it to the colony; ten seconds is the longest it will wait before going
without.

**`size` and `strength`**, so the two things worth dialling in are reachable
without first naming a raid type and answering the ship question:

```
/mc raid <colony> now size 40             40 raiders, strength as it would have been
/mc raid <colony> now size 40 2.5         40 raiders, two and a half times as strong
/mc raid <colony> now strength 2.5        as many raiders as the colony had coming, at 2.5x
```

Strength is a multiplier on the raid difficulty, between 0.1 and 10, where 1 is
what the colony would have faced anyway. It scales what the mod scales with
difficulty: raider health, damage and armour. Set on its own it also scales the
number of raiders, because that is what the colony's difficulty normally decides;
set alongside `size` it does not, since the count is then yours. It applies to the
one raid, including raiders the raid respawns later, and the next raid is back to
normal. The existing `<raidtype> <ships> <amount> [<location>]` form still works
and is still the way to pick a specific raid type or drop one at a position.

The success message now says what was actually spawned — `24 raiders at strength
1.80` — rather than only that a raid started.

**A raid asked for by name no longer fails for want of a spawn point.** Spawn
points are searched for by walking outward over *loaded* chunks and giving up at
the first unloaded one, so a colony whose surroundings are not loaded far enough
out — a single player world, most of the time — reports `NO_SPAWN_POINT` and
nothing happens. Only for a raid the player explicitly asked for, a point on a
circle 120 to 240 blocks out is used instead, without asking whether the chunk is
loaded; the event walks it back towards the colony until it finds loaded ground,
which it already did for its own spawn points. Natural raids are unchanged.

**`territory`**, so a raid can come out of enemy ground instead of off the usual
circle:

```
/mc raid <colony> now territory           raiders come out of the nearest hostile territory
/mc raid <colony> tonight territory       the same, at nightfall
```

It looks 500 blocks around the colony centre for ground a hostile territory owns,
takes the nearest loaded piece of it, and hands that to the raid as an explicit
spawn point. Everything that reports a raid then reports it with no extra work:
`/mc raid <colony> where` prints the direction and the coordinates, and the
barracks window lists it among the spawn points.

**Only a raid you ask for by command.** A raid the colony schedules for itself is
untouched and still picks its spawn point the way it always has; there is no
config that makes natural raids prefer a territory.

It refuses rather than does something surprising, and says which of the four
reasons applies: no hostile territory in that world at all; none within 500 blocks
of the colony; some in range but not loaded, which would give you a raid bar with
no raiders under it; or nowhere in it to stand. In every one of those cases no
raid is created.

### `/mc raid <colony> where`

Says where the raiders are. Per raid: the type, the direction it came from, how
many raiders it still counts as alive, how many of those exist as entities right
now, the spawn point, and the position of the nearest one.

The gap between those two numbers is the answer to "the bar says they are there
but I cannot find them". A raid spawns a few hundred blocks out and walks in, and
only the part of it standing in a loaded chunk exists at all — the rest is
discarded and queued to be put back when its chunk loads again. A raid bar with no
raiders under it usually means they are waiting to be respawned somewhere you have
not been, not that they are gone.

### `/mc raid <colony> tp`

Teleports you to the raider closest to the colony centre, of the ones loaded. If
none is loaded, it puts you at the raid's spawn point instead and says so —
standing there loads the chunks, which is what makes the raid put its raiders
back.

### `/mc raid <colony> stop`

Calls the raids off: every unfinished raid event of the colony is finished, its
raiders discarded and anything it built put back. It also sweeps 500 blocks around
the colony for raiders that outlived their event, which is what a raid bar that
never empties is made of.

### `/mc colony camp <colony> [<min range> <max range>]`

Puts a raider camp on the ground near a colony — the same camp world generation
places, the same one the camp-clearing quests send you to, placed now instead of at
world generation. Without a range it looks in a band 120 to 220 blocks from the
colony centre; with two numbers it looks in the band you name.

It either says where the camp went or says why it could not put one anywhere, and
the second is the point of the command. The quest that places these camps cancels
itself with a one-line apology when it cannot find a site, and this is how you find
out what the site search actually objected to. The reasons are counted over 192
candidate positions and reported together, for example
`NO_SITE -- sloped 26, fluid 17, built_surface 149`:

| reason | what it means |
|---|---|
| `unloaded` | the chunks are not loaded. The search never loads or generates a chunk itself, so a band nobody has been to has no sites in it by definition |
| `claimed` | the ground belongs to a colony — this one or another |
| `too_close_to_building` | inside the same minimum distances a raid uses to decide it is not spawning in your walls |
| `sloped` | more than five blocks of height difference across the 27x26 footprint |
| `fluid` | water or lava on the ground |
| `built_surface` | the surface is not a material the search recognises as natural ground — planks, bricks, concrete, wool and everything else somebody lays a floor out of. It is deliberately conservative and will refuse ground it is not sure about |
| `block_entity` | there is a chest, barrel, furnace, sign, bed, banner or hut block inside the footprint or within four blocks of it |

The camp is **permanent**. Clearing it clears it: the spawners are ordinary vanilla
blocks, the mobs are persistent, and nothing in the mod ever puts either back.

### `/mc boatspeed [blocks per second]`

How fast citizens steer a colony boat. Without a number it reports the current
setting; with one it changes it. The same convenience vanilla gives minecarts
through `/gamerule maxMinecartSpeed`, and in the same unit — blocks per second,
default 6, range 2 to 20.

Boats already halfway across pick the new value up on their next tick, and it is
written to `config/minecolonies-server.toml`, so it survives a restart.

The number is what the navigator asks for, not what the boat covers. The game
damps a boat in water to 0.9 of its velocity every tick before moving it, so the
default 6 crosses water at 5.4 blocks per second; the command reports both.

20 is the ceiling because that is 0.9 blocks of travel per tick. The navigator
re-aims the hull once per tick and the pathfinder works in whole blocks, so a
boat that covers more than a block between corrections stops being able to
follow a channel whose bends are a block wide. Below 0.45 a boat would be
slower than the "is this hull stuck" threshold and every crossing would be
abandoned three seconds in, which is what the floor of 2 keeps clear of.

The citizen minecart is not affected — it has no MineColonies speed of its own
any more and runs on vanilla rail physics, so it obeys `maxMinecartSpeed` exactly
as far as vanilla does (that is, only with the `minecart_improvements`
experimental feature enabled; otherwise vanilla's own hardcoded 0.4 blocks per
tick applies).

### `/mc colony freemode <colony> <on|off>`

The colony works without any items at all. One switch, per colony, saved with the
colony, off by default. Without `on`/`off` it reports the current state.

It replaces the four hut checkboxes that used to do parts of this (Build Without
Resources, Work Without Tools, Work Without Materials, Work Without Food) — those
are gone, and the six huts whose only settings tab was one of them no longer have
a settings tab at all.

Two mechanisms, both server side, both reachable from the single symbol
`FreeMode`:

* The worker-side guards. A worker that would otherwise stop and wait is handed
  what it lacks then and there: build materials, tools and weapons, the farmer's
  seeds, the herders' breeding items, the healer's cures, the miner's cobble and
  ladders, the cook's food, the sifter's mesh and sievable block.
* The request system. Anything filed as a request that no chest, warehouse or
  crafter in the colony can serve is conjured instead of hanging. That is what
  covers the things no hut setting could ever reach: crafting recipe inputs, so a
  bakery or a sawmill actually crafts; furnace and brewing-stand fuel; the
  smeltery's ore; the school's paper; the stablemaster's tack; guard weapons and
  armour. The colony's own stock and its own crafters still get first refusal, so
  turning free mode on does not stop the colony working normally where it can.

Turning it on also unsticks workers that are already waiting. A request filed
before the switch was thrown pins its worker in `NEEDS_ITEM` from any state; free
mode hands the items over and clears the request, so the worker resumes without a
relog.

Guards get a real weapon rather than merely stopping asking for one — the combat
AI refuses to swing unless it finds a weapon in its inventory, so suppressing the
request alone left them unable to fight. Armour and, where the research allows
them, arrows are handed over the same way.

It conjures items out of nothing and does not clean up after itself — a worker
will carry free materials into a chest and leave them there. That is fine for
testing and is not something to leave switched on.

Three things beyond materials are in scope while it is on:

* **A farm field may be any shape**, area rather than radius capped — see
  [`26.2/FREEMODE.md`](26.2/FREEMODE.md).
* **A herder hut stocks its own animals.** A cowboy, shepherd, swineherd,
  chicken farmer, rabbit hutch or stable that is short of livestock gets some,
  two at a time, up to the same ceiling its own worker culls back to (two per
  hut level). They appear inside the hut, so the worker can see them straight
  away, and they are penned there like any other. Nobody has to walk cows in by
  hand any more. See [`26.2/ANIMAL-PENS.md`](26.2/ANIMAL-PENS.md).
* **A building may be built straight to any level.** The Build Options window
  grows a level picker, the button reads "Build to 5", and one work order takes
  the hut from 0 to 5 without the four upgrades in between. Any builder may take
  it, whatever the level of their own hut, and the research a hut normally needs
  for that level is not asked for. A hut that is part of another one's blueprint
  still may not outgrow its parent. See
  [`26.2/FREEMODE-BUILD-LEVEL.md`](26.2/FREEMODE-BUILD-LEVEL.md).

Out of scope, because they are world state rather than materials: mineshaft
depth, free fields, hives and flowers, plantable ground, a colony with no
warehouse or no courier at all, and the university's research start cost (use
`/mc colony research completeall`). `/mc citizens fill` is still the way to get a
worker into every hut.

### `/mc colony protection <colony> [on|off]`

Whether this colony enforces its permissions against players at all. **On by
default**, per colony, saved with the colony, OP only. Without `on`/`off` it
reports the current state.

This is the middle setting nothing else offered. The server config
`enablecolonyprotection` is the same idea for the whole server at once; the
permission screen is the same idea one action and one rank at a time. Neither is
what you want when you are testing a build in your own colony and something —
you are not sure which of a dozen gates — keeps refusing you.

**What "off" covers: every permission check a player can run into.** Breaking
blocks, breaking huts, placing blocks, placing huts by hand and with the build
tool, right-clicking anything, opening containers, filling buckets, drawing a
bow, using the scan tool, attacking a citizen or an animal, hurting a citizen,
the supply-chest deployer, the assistant hammer, and the hut GUIs that ask for
`MANAGE_HUTS`. It is one switch because all of those ask one method — the whole
feature is `grep -rn ColonyProtection src/main/java`, five lines of it in
`Permissions#hasPermission`, and nothing can be forgotten because nothing is
enumerated.

**What "off" deliberately does not cover**, and the command's own message says
so, because both of these protect the colony rather than deny you:

* **Hostile mobs are still refused a spawn inside a built building.** Nobody
  testing a build wants zombies in the warehouse while they work; turning that
  off with this switch would make it worse at its job. The lever for it is
  vanilla's, `/gamerule spawn_monsters false`.
* **The explosion policy still spares citizens and livestock.** It is protection
  of your own citizens against a test charge, not a refusal aimed at you — and
  on the shipped `turnoffexplosionsincolonies = DAMAGE_ENTITIES` it does nothing
  at all anyway. The levers for it are `/mc colony blastprotection <colony> off`
  and the config.

**Editing the permission ranks still needs `EDIT_PERMISSIONS`.** That is the one
action the switch does not grant, on purpose: it is administration rather than
protection, and it is the only change somebody could make while the switch is off
that would still be there after you turn it back on.

It is a per-colony switch **and an OP one** — `IMCOPCommand`, the same gate as
`/mc colony freemode` and `/mc colony blastprotection`, and a strictly tighter one
than `/mc colony delete`, which any colony officer may run. A player cannot use it
to strip protection from a colony that is not his.

### `/mc colony fieldseeds <colony> [<x> <y> <z> [add|set|remove <item>|clear]]`

Reads and edits the seeds a farm field is sown with. A field may carry **up to
five different seeds at once**, and the farmer then plants a mixed field rather
than a monoculture — see below for what that looks like on the ground.

Without a position it lists every farm field the colony owns, one line each, with
the seeds in order. That is the only way to see a whole colony's fields at once;
the scarecrow window only ever shows the one you are standing at.

With a position — the **lower** block of the scarecrow, the one the field is
anchored to — it edits that field:

```
/mc colony fieldseeds here 120 64 -40 set minecraft:wheat_seeds       only wheat, forget the rest
/mc colony fieldseeds here 120 64 -40 add minecraft:carrot            wheat and carrots
/mc colony fieldseeds here 120 64 -40 add minecraft:potato            and potatoes
/mc colony fieldseeds here 120 64 -40 remove minecraft:carrot         back to wheat and potatoes
/mc colony fieldseeds here 120 64 -40 clear                           no seed; the field is unassigned
```

Everything this does, the scarecrow window does too — it adds no power an
ordinary player lacks. It exists because the window is client code and this port
is developed without a display, so the server side has to be usable and checkable
on its own; and because a per-colony listing is genuinely easier here than in a
GUI. Tab completion offers only the items the farmer can actually plant, and
`add`/`set` refuse anything else outright rather than letting the farmer discover
it three colony days later.

**How the crops are laid out.** The farmer decides each cell's crop from the
cell's own world coordinates — `(x + z) mod (number of seeds)` — which comes out
as diagonal stripes one cell wide: a checkerboard for two crops, three-wide
diagonals for three, and so on. Every crop gets as near an equal share of the
ground as the field's shape allows.

Diagonals rather than blocks or rows, for a reason that is not decoration: a cell
is tilled on one colony day and sown on the next, and the tilling has to lay down
the farmland the sowing will accept — MineColonies crops each want their own
preferred farmland, vanilla crops want plain farmland. Anything random, or
anything that depended on the order the farmer happened to walk the field, would
till for one crop and then try to sow another, and the cell would stay bare
forever. Being a pure function of the cell means the two passes always agree. It
also means resizing a field does not re-assign the crop of ground already in it,
and that two crops of the same kind are never orthogonally adjacent.

**A mixed field waits for all its seeds.** The farmer will not start a planting
pass until it is carrying every seed the field lists; what is missing is
requested, and the field is handed back so the worker gets on with its other
fields meanwhile. That is the same all-or-nothing rule a single-seed field has
always had, kept deliberately: sowing two crops out of three would leave a third
of the cells bare and scattered, and the field's stage would then advance as
though it had been sown. In free mode the seeds are conjured, so this never
waits.

**Melons and pumpkins want a field to themselves.** Their stems grow fruit into a
free orthogonally adjacent block, and in a mixed field every neighbour of a melon
cell belongs to a different crop. Nothing stops you mixing them in and nothing
warns you — the stems simply never fruit. This is a property of how stems work,
not something the mix does wrong, and it is the one combination worth avoiding.

The hut's field list still shows one icon per field — the **first** seed. The
scarecrow window is where the whole set is visible.

### `/mc colony forceloadclaims <colony> [on|off|default]`

Keeps **every chunk the colony owns** loaded and ticking on the server, rather
than only the chunks its buildings stand on. Without `on`/`off`/`default` it
reports the current state. Per colony, saved with the colony, and it takes effect
without a restart.

This is what stops citizens out on claimed ground — fields, roads, mine
approaches, enclaves — from freezing and then vanishing. A chunk that is loaded
but below entity-ticking level keeps its entities in memory without ever ticking
them, and a citizen registers itself with its colony from inside its own tick, so
`/mc colony diagnose` reports it as `state=<no entity>` rather than as a citizen
standing still.

**Three states, and the command tells you which one you are in.**

| shown as | means |
|---|---|
| `on (set for this colony)` | an operator ran `on` here; the config no longer affects it |
| `off (set for this colony)` | an operator ran `off` here; the config no longer affects it |
| `on/off (server default, not set for this colony)` | nobody has decided; it follows `forceloadallclaims` in the server config, which is off by default |

`default` clears a colony's own answer and hands it back to the config.

It reports what it is doing rather than only what it was told: claimed chunks,
how many of them are entity-ticking *right now* (read from the level's simulation
chunk tracker, not inferred), tickets held against the ceiling, and — when
several colonies have it on — the server-wide total.

**It is not free.** A player at the default simulation distance ticks 441 chunks,
so a colony with N claimed chunks costs roughly N/441 of an extra player being
online, permanently, for as long as its force-load timer is running. Mobs do not
spawn out there and nothing is rendered anywhere, but entity AI, block entities
and random ticks all run. That is why it is per colony: turn it on for the town
you play in, leave the rest of the server's colonies off.

Two things worth knowing:

* **Turning it off gives the ground back immediately.** It does not merely stop
  taking new tickets — the chunks the whole-claim rule was holding lose their
  tickets on the spot, keeping only those the ordinary building rule wants.
* **`maxforcedchunks` is a per-colony ceiling, not a shared pool.** Colonies
  cannot starve each other of tickets. If a colony's claim is larger than the
  ceiling, the chunks nearest the town hall are covered and the command says so
  in red rather than leaving you to wonder why the counts disagree.

Turning it **on** only does something while the colony's force-load timer is
running, which an owner or officer being in the colony is what starts — the same
condition `forceloadcolony` has always had.

### `/mc colony repairall <colony> [preview]`

Files a repair work order for **every building in the colony** that can take one.
The hut GUI's repair button does one building at a time, which stops being usable
somewhere around the twentieth hut; this is that same button pressed on all of
them. Each building goes through `IBuilding#requestRepair` with no builder named,
exactly as `BuildRequestMessage` does for a repair asked for by hand, so the work
manager hands the orders out the usual way.

`preview` lists what it would queue and changes nothing.

It reports back rather than working silently: how many orders were queued, how
many buildings were passed over and for which reason, then the buildings
themselves — ten of each in chat, all of them in the server log.

**What it leaves alone:**

| passed over | why |
|---|---|
| level 0 | nothing is built there to repair |
| already has a work order | building, upgrading, repairing or being taken down; a second order for the same hut is never created anyway |
| deconstructed | a repair order on one of those *rebuilds* it, which is why the hut's own repair button reads "Build" once it is down. Undoing a deconstruction the player asked for is not what "repair the colony" means |
| no builder good enough, or none within `maxbuilderdistance` | the colony refuses it, the same refusal a single repair would have given |

**Nothing is capped, and nothing needs to be.** `WorkManager#onColonyTick`
assigns at most one order per builder hut and skips builders that already have
one, so a hundred orders do not wedge anybody — they queue and are taken as
builders come free, in work order id order. Two things to expect on a large town:
every queued building is wrapped in construction tape the moment its order is
made, so the colony goes striped all at once; and the builders will then work
through the lot, consuming whatever materials each repair needs. Cancel single
orders from the hut's own build button or the town hall's work order tab.

Unlike a repair asked for from a hut, the per-building chat line is suppressed —
sixty of those is not a report. Nothing else about the path differs.

### `/mc colony keepbuildings <colony> [on|off]`

Stops the sanity cleanup deleting buildings from this colony. Per colony, saved
with the colony, **off by default** — a colony that has never run this command
behaves exactly as it always did.

```
/mc colony keepbuildings here          the state, and how much is at risk
/mc colony keepbuildings here on       hold the cleanup back
/mc colony keepbuildings here off      let it run again
```

`RegisteredStructureManager#cleanUpBuildings` runs on the colony tick and, for
every building whose chunk is loaded and whose anchor is no longer the matching
block, calls `IBuilding#destroy` — the building leaves the colony for good, with
its level, its work orders and its worker's assignment. That is right when a
player mines a hut and wrong when the block is missing for a reason that has
nothing to do with the player: a world opened once **without the mod installed**
loses every one of the mod's block entities, and the colony then deletes itself
building by building as the player walks around and more chunks load. The code
already suspects this case — it warns *"Did you just load a backup?"* when every
building goes at once — but a warning does not put anything back.

With the switch on, the cleanup destroys nothing, and every building it would
have destroyed is named in the server log so the player gets the damage list.
Each building is named **once**, the first pass it is spared on; after that only
a change in the count says anything, because at one pass per 500 game ticks a
town of a hundred and forty-five orphans would otherwise write its whole list
every twenty-five seconds.

**It holds back the whole of that method's removal, not only the buildings.**
Building extensions (fields, plantation fields) and leisure sites are dropped by
the same method for the same reason — their block is not where it was — and are
lost just as irrecoverably, so a switch that saved the buildings and let the
fields go would be a switch that half worked. Everything the method does that is
not a removal still happens.

**It is a pause, not a repair.** The buildings stay in the colony, but the world
still has no hut blocks, so nothing else about them works — no hut GUI, no worker
attachment, no repair order. It buys the time to run `/mc colony restorehuts`,
below. Once the anchors are back the buildings are consistent again and the
switch should go **off**, so the cleanup resumes noticing huts the player really
did mine.

The report with no `on`/`off` says how many of the colony's buildings currently
have no matching hut block — that is the number that would be deleted.

### `/mc colony restorehuts <colony> [confirm]`

Puts the **hut block** back for every building the colony still knows about, at
its stored position, bound to the building that is already there.

For a world that was opened once **without the mod installed**. Vanilla drops
block entities it does not recognise (`Skipping block entity with invalid type:
"minecolonies:colonybuilding"`) and the hut blocks go with the chunk sections that
referenced an unknown block — but the colony save is a separate file and is
untouched, so the town still exists in the colony's memory and no longer in the
world. This copies the memory back into the world.

```
/mc colony restorehuts here            counts what is missing, changes nothing
/mc colony restorehuts here confirm    places them
```

**Why this matters urgently.** `RegisteredStructureManager#cleanUpBuildings` runs
on the colony tick and *deletes from the colony* every building whose chunk is
loaded and whose anchor is no longer the matching block. With no hut blocks, each
session of play erases more of the town permanently. Every hut block of the colony
goes back inside a **single server tick**, and chunks are pulled in explicitly
rather than hoped to be loaded, so the cleanup never gets a look in.

**It binds to the existing building — it does not make a new one.** The block is
placed with `Level#setBlock`, which does not go through `Block#setPlacedBy`, so
`addNewBuilding` is never reached. The new block entity is then attached through
the mod's own path: `setColony` followed by `getBuilding()`, which runs
`TileEntityColonyBuilding#updateColonyReferences` — that looks the building up by
position and calls `building.setTileEntity(this)`. Level, blueprint, work orders,
citizens and containers are untouched. Each building is verified afterwards (the
block matches, and the colony still returns the *same* building object for that
position); one that fails is named in the report rather than aborting the run.

The block entity is also given back what the building already recorded: rotation
and mirror, structure pack, blueprint path.

**It only places the anchor.** The structure itself is the builders' job — run
`/mc colony repairall` afterwards and they rebuild from the blueprint the colony
already has. Pasting the blueprints was considered and rejected: `/structurize
paste` routes a hut anchor through `AbstractBlockHut#setup`, which ends by setting
the building level from a number parsed out of the blueprint file name and falls
back to level 1 when that parse fails — on a rescue whose whole point is
preserving levels, a path that can silently reset a level 5 building to 1 is the
wrong tool. It also destroys the existing block with drops and overwrites terrain
the player has changed since.

**Destructive, hence the confirmation word.** Whatever stands at a building's
anchor position now is replaced — normally air, but a container put there since
would go too, spilling its contents.

### `/mc colony workoverride <colony> [mourning|night [on|off]]`

The colony's "keep working anyway" switches. Each one names a thing that
ordinarily stops a citizen working and tells it to carry on regardless. Per
colony, saved with the colony, **every one of them off by default** — a save that
has never run this command behaves exactly as it always did.

```
/mc colony workoverride here                  every switch and its state
/mc colony workoverride here mourning         just that one
/mc colony workoverride here mourning on      throw it
/mc colony workoverride here night off        and back
```

| switch | what it lifts | who it applies to |
|---|---|---|
| `mourning` | mourning stops the citizen working | every citizen |
| `night` | nightfall sends the citizen to bed | builders only |

**`mourning`.** Aimed at the generational mechanic. On a large colony with a short
lifespan somebody dies more or less constantly, every death puts the dead
citizen's family and friends into mourning, and the mourning never lifts — the
town stops, not because of any one death but because it is always somebody's.
With the switch on, the mourning branch of `CitizenAI` no longer short-circuits
the work branch.

It lifts the work stoppage and **nothing else**. The citizen is still mourning in
every other sense: the mourn handler still says so, the deceased are still on its
list, the happiness cost is unchanged, and it still raises the "mourning X" chat
bubble over its head, which the interaction validator keeps alive for as long as
the grief lasts. Mourning still ends the way it always did, at the colony's next
dawn (`CitizenManager#onWakeUp`). The only visible difference beyond the work
itself is the citizen's status icon, which now shows what it is doing rather than
the mourning icon — with three hundred citizens permanently grieving, an icon
that never changes tells the player nothing.

**`night`.** Builders skip the going-to-bed half of `CitizenAI`'s sleeping branch
and keep building. A builder that was already asleep when the switch was thrown
gets up rather than lying there until dawn.

An overridden builder **is not counted as sleep-deprived**. Going to bed is what
ordinarily resets the "slept tonight" happiness modifier, and that modifier climbs
on its own every colony day — three days without a reset is a real happiness
penalty. A builder held at work by this switch has that modifier reset for it, so
the night shift costs it nothing. It keeps its bed and its home the whole time;
it simply never walks to it while the switch is on.

Only builders, deliberately. The seam is shared by every non-guard worker, so the
job check (`JobBuilder`) is the whole of the restriction — widening it to another
profession is one line, and widening it to all of them is deleting that line.
Guards are unaffected either way: they never go through this branch and already
work at night.

Two things it does not touch. A raid still sends everybody indoors, before either
switch is consulted — that branch sits above them both. And a colony with a
night-shift builder never prints "all citizens are sleeping", because one of them
is not.

Adding a third switch means one constant in `WorkOverride`, one lang key for its
label, and one guard at the place that stops the worker; the command, the
saving and the reporting pick it up from the enum on their own.

### `/mc colony blastprotection <colony> [on|off]`

Whether this colony shields its claimed chunks from blasts. On by default.

This is one half of a switch whose other half is the server config. The config
`turnoffexplosionsincolonies` says **how much** a colony is shielded, and says it
for every colony on the server at once; this says **whether** a particular colony
takes part. A testing colony, a PvP colony, or one whose owner simply prefers
craters turns itself off here without changing anybody else's game.

With no `on|off` given it reports the current state, the server's policy, and what
the protection covers.

**Read that last part.** It covers explosions produced by the Simple Planes mod —
the craftable strike tool, the `/autopilot` command, a bomber flown in by hand, a
downed aircraft, a crash. **The blocks of a colony are not protected from vanilla
explosions**: a creeper, TNT, a bed in the Nether or a bomb dropped from a plane as
a payload will all take the blueprint apart. Those go through Minecraft's own
explosion path, which this port has no hook into by design — see
`26.2/BLAST-PROTECTION.md` for what one would cost.

The **entity** half of the policy is a different story and does apply to every
explosion in the game, vanilla ones included. On `turnoffexplosionsincolonies =
DAMAGE_PLAYERS` a blast inside the colony no longer hurts citizens, livestock or
pets, though it still hurts hostiles and still hurts players; on `DAMAGE_NOTHING`
it hurts nothing in the colony except a player. Both of those are what the setting
meant on 1.21.1, and neither did anything on this port before. On the shipped
default, `DAMAGE_ENTITIES`, nothing changes — that policy never protected an
entity upstream either. This colony switch turns the entity half off along with
everything else.

The Simple Planes side of it lives in that mod, so there is nothing to apply here —
it just needs a Simple Planes build recent enough to have the blast-guard API. That
mod also has its own server-wide switch, `/blastguard [status|on|off]`, which decides
whether it consults any guard at all; with it off, this command still reports the
colony's setting but nothing ever asks for it.

### `/mc colony warehousestock <colony>`

What has been sitting in this colony's warehouses without anybody taking it, how
fast each thing is actually being drawn down, and how full the warehouses are.

Two numbers per item type, never one. **Days idle** is time since the total of that
item last went *down* — a decrease is the only evidence a stock level carries that
somebody wanted the thing. **Taken per day** is a rolling seven-day withdrawal rate.
Idle age alone misjudges a thing taken rarely but regularly: "idle 20 days, never
taken" is junk worth clearing out, "idle 1 day, 400 taken this week" is a consumable
that must not be sold. Restocking deliberately does **not** reset the idle clock, so
a barrel that is topped up every morning and never drawn from still shows as idle.

**Fill is also two numbers, and they are not averaged.** *Slot occupancy* is the
headline, because that is what actually stops a courier storing anything — a slot
holding three cobblestone is occupied. *Capacity fill* is the goods measured in full
stacks of their own item over the same slots. A real warehouse reads something like
`596/1827 slots occupied (32.6%); 9556 item(s) = 149.3 full stacks of a possible 1827
(8.2% of capacity)`: a third unusable while nearly empty by weight. With more than
one warehouse each is listed separately with its position, plus a colony total.

**Chat is capped at 15 item rows; the file has everything.** Every run writes the
complete, unpaginated list to

```
<world>/minecolonies/warehouse_stock_colony<id>.csv
```

— the same directory the colony backups and tag audits use — and prints the absolute
path in chat. On the test server that resolved to
`/home/user/fabric-server-26.2/world/minecolonies/warehouse_stock_colony1.csv`. The
name comes from the colony id and nothing else: there is no filename argument, so
nothing typed can steer where it lands, and each run overwrites the previous file
rather than leaving a directory of dated copies. If the write fails the reason is
said in chat, because a capped chat report plus a silently missing file would look
like the whole answer.

Columns: `item_id, display_name, count, days_idle, taken_per_day, taken_last_7_days,
total_taken, first_seen_days_ago, ever_taken, warehouses`. The warehouse totals sit in
a short `#`-prefixed header block **above** the header row, so that everything below
the header row is uniform per-item data and a spreadsheet's sort covers all of it.

Sorted by idle time descending, so the first rows are the candidates for clearing out.

The data comes from a per-warehouse observer that samples the racks once per colony
tick (~25 s); nothing hooks the withdrawal paths. A sample is discarded whole unless
every rack of that warehouse is in a loaded chunk, so walking away from a colony
never looks like somebody emptying it — the command says so if no warehouse has
sampled yet. Design note and measurements: `26.2/audit/WAREHOUSE-IDLE.md`.

### `/mc colony antiair <colony> [where|tp|settings|range|rate|damage|minlevel|reset]`

Where the anti-air positions are, which of them needs arrows, and what they are
tuned to.

A guard tower mounts a battery at level 3 by default and fires arrows out of its own racks,
so a tower can be fully staffed, in range, and still silent because nobody has
put arrows in it. The battery says so in chat when it happens, and hangs the
tower's coordinates off that message as a hover — but a hover scrolls out of the
log and only ever names one tower. This is the part you can ask for.

`where`, which is also what the bare command does, lists every emplacement: its
name, the direction and distance from the colony centre, its coordinates, and how
many arrows it has. `tp` puts you next to the emptiest one — the nearest dry
tower to the centre, which is the same tower the chat message names, so the two
never point at different places. If every tower has arrows it says so and puts
you at the nearest one anyway.

Without the Simple Planes mod a colony has no anti-air positions and the command
says exactly that. Nothing here names an aircraft type; it all goes through the
same `AircraftCompat` bridge as the rest of the feature.

A dry tower now also **asks** for arrows. It files an ordinary async request the
way any building does, so the warehouse serves it and a courier carries the
arrows to the tower's racks. The chat warning is therefore no longer the only
remedy — it is the warning that the automatic one may not arrive before the
aircraft does. In free mode the tower is simply handed arrows and never runs dry,
so neither the request nor the warning happens.

**The battery is tunable per colony.** Four numbers, each its own subcommand,
each of which **reports when given no value and sets when given one** — the same
shape `/mc colony blastprotection` has. `settings` prints all four at once with
their defaults and bounds; `reset` puts everything back. The settings are saved
with the colony and survive a restart. A colony that has never touched the
command writes no tag at all and behaves exactly as the battery shipped.

| | default | allowed | what it is |
|---|---|---|---|
| `range` | 200 | 16-384 | engagement radius, blocks |
| `rate` | 1.0 | 0.05-4.0 | arrows per second, per position |
| `damage` | 3.0 | 0.5-20.0 | damage per round |
| `minlevel` | 3 | 1-5 | lowest guard tower that mounts a battery |

**Out of bounds is refused, never clamped**, with a message naming the setting,
the bounds and the number you offered, and nothing is changed on any colony —
including when the colony argument named several. An unbounded radius is a
server-killer (the scan box grows as its cube around every colony centre) and you
should be told no rather than find out; this is deliberately not the shape the
Stable's patrol interval has, where the field is unbounded and an empty box reads
as zero.

**Rate of fire is a rate but the battery counts ticks**, so `rate` is a request
for a whole-tick interval: `round(20 / rate)`. The reply always says both the
achieved rate and the interval it is made of, and when your number could not be
honoured it says so outright — `rate 3` answers "one round every 7 ticks, so the
real rate is 2.86/s. There is no setting that gives exactly 3/s." The steps are
fine at the bottom and coarse at the top: between 2.0/s and the ceiling the only
rates that exist at all are 4.00, 3.33, 2.86 and 2.50. What is stored is the
interval, not the number you typed, so every later report is what actually
happens.

**Above 2 arrows a second you are paying for nothing**, and the command says so.
An aircraft is immune for 10 ticks after every hit, so two damaging rounds a
second is the ceiling the aircraft mod imposes; the surplus is still fired, still
consumed and still carried by couriers. If you want a deadlier battery, that is
what `damage` is for. The rate ceiling is 4.0 rather than 2.0 because over-firing
does convert into hits against several targets or a manoeuvring one.

**Raising the rate multiplies the courier load, and the order size follows it.**
A stack of arrows is 64 seconds of fire at the default rate and 16 at the ceiling,
while the courier round trip is the same either way — so the restock order scales
with the rate (one to four stacks, `orderMin` a quarter of it) to keep one
delivery worth about a minute of shooting whatever the rate is. At the default it
is exactly the 64/16 it always was. What has **not** changed is *when* the order
is filed: still only once the tower has run dry. At 4.0/s one position eats 240
arrows a minute; six of them under sustained attack is about 1500 arrows a minute
asked of your warehouse.

`launchspeed` and the scan interval are deliberately **not** exposed. Launch speed
is the constant the 384 range bound is derived from, and lowering it would leave a
range the ballistic solver cannot reach — a battery that tracks, computes no
solution and holds fire for ever with nothing saying why. The scan interval only
changes how fast a hand-flown plane is noticed, and every value that saves work
makes the battery look broken.

Design note, bounds reasoning and measurements: `26.2/audit/ANTIAIR-TUNING.md`.

### `/mc aircraft [where|tp]`

Finds the aircraft, and takes you somewhere you can watch one from.

An air raid's transport is flown in from 300 blocks out, but nothing in the game
tells you where it is, so a raid could look like pirates appearing out of thin
air. This is the part you can ask for.

`where`, which is also what the bare command does, lists what is flying: the
airframe, its direction and ground distance from **you**, its coordinates, its
altitude, and on a second line what it is doing — an attack run against a named
point, a scripted flight heading for one, or no plan at all with somebody flying
it by hand. Nearest first.

**It finds an inbound transport while it is still inbound.** Anything on a
scripted flight is reported from any distance at all, because a flight is known
from the moment it is ordered rather than when something manages to scan for it.
Aircraft that are *not* on a scripted flight can only be found by looking, so
those are limited to 256 blocks around you.

`tp` puts you **on the ground directly under** the aircraft and turns you to face
it — safe, no falling, and it is then overhead at a distance equal to its own
altitude, which for a raid transport is about 70 blocks. It picks the nearest
aircraft on a scripted flight, or the nearest of any kind if none is scripted; a
plane somebody is flying by hand already has somebody watching it.

Unlike the raid and anti-air commands this one takes no colony, deliberately: an
aircraft is a thing in the world rather than something a colony owns, and the
interesting one is usually the one that has not arrived anywhere yet. Everything
is measured from whoever typed it.

Without the Simple Planes mod it says so plainly instead of reporting an empty
sky — those are different answers and only one of them means keep looking.
Nothing here names an aircraft type; it goes through the same `AircraftCompat`
bridge as the rest of the feature.

### `/mc colony rehouse <colony>`

Moves the citizens a colony already has into the house nearest their work.

The housing rule only decides where a citizen is put **from now on**. It does
nothing for a colony already being played: the beds are taken, so nobody is ever
offered a better one, and a worker who once got a bed on the far side of an
enclave keeps it for good. This is the pass that makes an existing save converge
on the rule.

It leaves alone anything the player decided by hand. A citizen living in a house
set to **locked** hiring is never moved — that mode is how you say who lives
where. A citizen only ever moves to a *strictly* nearer house, so two houses at
equal distance can never trade a citizen back and forth. Homeless citizens are
housed, and nothing else about them is touched.

**Children are moved by a different rule: to a parent.** A child is not housed by
bed at all — it lives with its parents and holds no bed until it grows up — so
"nearest to work" means nothing for one. In a save written before that rule, the
children were given beds like anybody else, and because the old birth code chose
the house before the parents, most of them ended up in the tavern. Nothing moves
them on its own, since the automatic housing pass leaves children alone, so this
command is where the migration lives: run it once and every child that still has
a parent with a home goes to live with them. Its report line reads
`… -> …, to live with a parent` rather than a distance. A child whose parents are
dead or themselves homeless is left exactly where it is.

### `/mc colony chunkstatus <colony>`

What a colony's force-loading actually looks like on the running server: how much
ground it owns, how much of that is really ticking right now, and whether the
ceiling is quietly cutting the answer short.

Every number is read from the live server rather than from config: the claim from
the same per-dimension map the border renderer draws, and the ticking state from
the level's own simulation chunk tracker — which is the only thing that decides
whether a citizen standing there gets a tick at all. `/mc colony forceloadclaims`
reports from the same place, so the two can never drift apart.

### `/mc pathstats [on|off|reset]`

How long path searches wait before anyone gets to them, how long they then take,
and how many were never going to arrive.

This answers "why do my workers stand about". The pathfinding pool is **one
thread by default**, shared by every citizen, raider and animal in every colony
on the server, so when it saturates, workers wait for a path they have already
asked for — and that wait is invisible from inside the game without this.
`/mc debug maxpool` below changes the size of that pool without a restart.

While a pool that has just been replaced is still finishing its backlog, the
report says so on a line of its own. The thread count and the backlog it prints
are the live pool's own, so neither is inflated by the drain; the occupancy is
the one number the drain makes approximate, because the searches in the window
ran across a number of threads that was changing.

Per-job measuring is off until `/mc pathstats on`, and switching it on clears the
counters, so the window is always "since you asked". A lifetime average would
hide exactly the spike you are trying to catch. `reset` clears them without
changing whether sampling is on.

Worker-side waiting — a citizen idling for want of a tool or a delivery — is
deliberately *not* here. `/mc colony diagnose` already reports every worker's AI
state and how long it has held it, which is the same question answered better,
and collecting it twice would cost server-thread time every tick.

### `/mc debug maxpool [<1-8>]`

How many worker threads the pathfinding pool runs, changed on a live server. With
no argument it reports the size, the backlog, and anything a previous switch has
left unfinished.

The counterpart of the `pathfindingthreads` config setting below, which is read
only when a pool is built and therefore only takes effect on a restart. This is
the same number, changed now — the knob to turn while watching `/mc pathstats`,
which is the command that says whether the queue is your bottleneck in the first
place. It is deliberately **not** written back to the config: what a server
should come back up with is a separate decision from what it needs this minute.

**A switch loses nothing.** A new pool of the size asked for is built and
published first, so every search from that moment on goes to it; only then is the
old pool told to shut down, which for a thread pool means "take nothing further,
finish what is queued, then let the threads exit". Searches that were running go
on running on the threads they started on, searches that were waiting are still
taken by the workers that were going to take them, and nothing is cancelled,
moved or submitted twice. The old pool disappears by itself once it is empty.
That is true in both directions: dropping from four threads to one does not
throw a four-thread backlog away.

Because submissions and switches take the same lock, a search can never be handed
to a pool that has already been shut down, which is the one way a switch could
otherwise have thrown `RejectedExecutionException` out of an entity tick.

Measured on a live server: fifteen switches in both directions under load, with
15 to 18 searches in flight at each one; every replaced pool finished every job
it held and then terminated, and the count of searches submitted matched the
count completed plus the count cancelled by their own callers, exactly, at every
observation.

### `/mc debug headless [on|off]`

Lets the colonies on this server run with nobody logged in to watch them. With no
argument it reports whether the mode is on.

**On an ordinary install this command does not exist.** The literal is added to
the command tree only when the server JVM was started with
`-Dminecolonies.headless=true`; without that property `/mc debug headless` is an
unknown command, there is nothing to tab-complete, and the mode cannot be
reached by any other route — there is no config key for it and no packet that
sets it.

#### What it changes

`Colony#updateState` decides whether a colony ticks by asking whether anybody can
see it: a close subscriber, or a loaded claim plus an important colony player.
Those are two different questions — *should this colony run* and *is somebody
looking at it* — and answering the first with the second is right for a server
people play on and wrong for one nobody is logged into. There, every colony sits
at `INACTIVE`: the work manager never runs, so a work order is never handed to a
builder, and the force-load timer is never refreshed, so the ground the colony
stands on stops ticking and its citizens stop existing.

The mode answers the first question on its own merits. Three things follow from
it, and nothing else does:

* `updateState` returns `ACTIVE`, so the whole colony state machine runs — work
  manager, citizen ticks, requests, raids, the slow tick;
* `updateChunkLoadTimer` refreshes the force-load timer as an officer standing in
  the colony would;
* one chunk ticket per colony keeps its dimension awake.

**Why the third.** A colony that ticks is no use in a level whose entities do
not. `ServerLevel#tick` counts up an empty timer and, past 300 ticks, stops
walking the entity tick list and the block entities altogether; the only things
that reset it are a player and a chunk ticket whose type carries
`TicketType.FLAG_KEEP_DIMENSION_ACTIVE` — vanilla's `FORCED`, the one
`/forceload` registers, is such a type. The colony's own force-load tickets are
loading-and-simulation only, so fifteen seconds after the last player leaves, a
colony's citizens stand exactly where they were: the colony ticks, its chunks are
held, and nothing in them moves. Measured on a dedicated server before this was
added: 81 of 81 claimed chunks force-loaded and reported entity-ticking, four
citizens spawned, a work order raised and claimed, and every citizen still at the
same coordinates, to fifteen decimal places, four minutes later; a summoned arrow
did not fall. So while the mode is on, one ticket carrying that flag is held per
colony, registered with radius 0 — enough to load its own chunk and no more,
because its whole purpose is the flag. It is given back the moment the mode goes
off, and it is not a persistent ticket type, so it cannot survive a restart.

The colony's own ticket type is deliberately left alone: giving it the flag would
keep a dimension awake for every install that force-loads a colony, which is a
change to make on its own merits and not as a side effect of this.

**It is not a fake player.** The other way to reach `ACTIVE` is to hand the
package manager a subscriber, and `ColonyPackageManager#addCloseSubscriber`
refuses a `FakePlayer` for a good reason: a close subscriber is an address that
colony view, permission and work-order packets are serialised and sent to on
every update interval. A fake subscriber would mean re-serialising the whole
colony several times a second into a connection that throws it away, and it would
mean weakening a guard that protects every install in order to help the one case
that wants this. Under headless mode the subscriber sets stay empty, every send
path in `ColonyPackageManager` is already conditional on them, and **no packet is
produced at all**.

**It does not force-load anything by itself.** Refreshing the timer is what an
officer's presence buys; which chunks a running timer then tickets is still
decided by `colonyloadstrictness`, or by the whole claim where
`forceloadallclaims` / `/mc colony forceloadclaims <colony> on` is on. A colony
that would not have been force-loaded with a player standing in it is not
force-loaded here either — so on a server with genuinely nobody in the world,
**pair this with `/mc colony forceloadclaims <colony> on`** or the citizens will
have nothing ticking under them. The command says so when you switch it on.

#### The chain before any of this runs

Five things, and a normal player does none of them:

1. the mod is installed on a **dedicated** server — an integrated server
   (singleplayer, or a world opened to LAN) is refused even with everything else
   in place;
2. the server JVM is started with **`-Dminecolonies.headless=true`**. It is read
   once at class initialisation. There is no config key, no command and no packet
   that can set it, and no launcher, modpack or host adds it on its own;
3. the sender is an **operator** (`IMCOPCommand`, full command permissions);
4. somebody runs **`/mc debug headless on`** explicitly. Off is the state the
   server comes up in;
5. and it lasts **only until the server stops**. Nothing about the mode is
   written to a colony's NBT, to the server config, or to any other file.

The last point is deliberate, and it is the point. A flag persisted in a colony's
saved data would travel with a world backup into somebody else's server and
quietly keep their colonies ticking, which is exactly the failure worth designing
against; re-arming a test run costs one command.

#### It says so

A server in this mode is doing something no ordinary server does, so it is never
quiet about it:

* a `WARN` at startup whenever the JVM is armed, whether or not the mode is on,
  so an operator who inherited a start script finds out from the log;
* a `WARN` when the mode is switched, in either direction, and when the server
  stops with it on;
* a `WARN` repeated every ten minutes for as long as it is on;
* a line at the top of `/mc colony diagnose <colony>`, because the mode changes
  how every number under it should be read.

#### What it is not for

Every colony on the server ticks under it, and none of them will ever have been
ticked by somebody looking at them, which is the state the mod's timings assume.
It is a switch for a server being measured or driven from a console — with
`/mc colony found`, `/mc colony hut` and the rest — not a way to keep a colony
running while its owner is away.

### `/mc citizens info <colony> <citizen>` — the age line

Upstream's command, with one line added by this port. When the generational
mechanic is on (`generations`), it prints the citizen's age and its personal life
expectancy in colony days.

This is the only place a player can see how far through its life a citizen is,
and it is what makes the mechanic checkable without waiting a hundred colony days
to find out. With generations off the line is not printed at all, since there
would be no age to report.

### `/mc citizens info <colony> <citizen>` — home, and parents

Three more lines from this port, all about where a citizen belongs.

**Home.** Upstream printed a home position for everybody, taken from
`getHomePosition()` — which, for a citizen with no residence, falls back to a
tavern. So the command said "home position: the tavern" for a citizen that had
nowhere to live, and the player had no way to tell that apart from one that
genuinely lived there. It now says `Home: Homeless - no residence assigned`
followed by `Sleeps meanwhile at:` and that fallback position, or the home
position and nothing else.

**Children.** A child living with its parents gets one extra grey line saying so,
because otherwise a house showing more residents than beds looks like a bug. A
child takes no bed until it grows up; see `/mc colony info` below.

**Parents.** `Parents: Marie (#118), Anton (#204)`. The ids are the argument to
another `/mc citizens info`, which is what makes a parent findable at all —
before this the game stored parents as bare names. A parent with a name but no id
is either dead or from a save written before parent ids existed; both read the
same way, without the id.

### `/mc colony info <colony>` — the housing line

One line added under `Citizens: 13/10`, spelling out what those two numbers are
now made of:

```
Citizens: 13/10
  of which 10 adults and 3 children; 10 beds, 0 adults homeless
```

Population and beds stopped being subtractable when children stopped holding a
bed. `13/10` no longer means "three citizens are homeless"; it can equally mean
ten adults in ten beds with three children living with their parents, which is
the healthy case. The added line says which, and counts the homeless one by one
rather than deriving them. A fourth line appears only when children are homeless
too — that means their parents are, and it is the one case the birth rule cannot
house a child.

### `/mc colony found <name> [<pos>]`

Founds an ordinary colony from the server console: places a town hall at the
position given, or where the sender is, and builds the colony around it.

There was no way to do this without a game client. Founding a colony goes through
a player right-clicking a town hall with the build tool, and every step of that
assumes a `ServerPlayer` — the message, the permission owner, the packet sent
back. `territory create` below reaches the same save data from the console but
deliberately makes a *hostile territory*, which has no town hall, no citizens and
a tick path that does nothing but repaint a border.

It runs the three steps `ColonyManager#createColony` runs, minus the player:
create the colony in the world's save data, claim the usual square of chunks,
register the town hall as the first building. The owner is left `[abandoned]`,
as a territory's is, because there is nobody to be the owner. The colony is given
the `Colonial` pack and the town hall its level 1 blueprint.

It builds nothing — the town hall is placed at level 0 with a blueprint recorded
but not raised, exactly as a freshly placed hut is.

**A colony founded this way does not build on its own, and neither does one
founded by a player who then logs off.** `Colony#updateState` only reaches
`ACTIVE` while the colony has a subscriber, and a subscriber is a connected
`ServerPlayer` — `ColonyPackageManager#addCloseSubscriber` refuses a Fabric
`FakePlayer` outright. An `INACTIVE` colony runs no work manager, so a work order
is never handed to a builder. Citizen entities in loaded chunks still tick and
still walk, which is what makes this useful for pathfinding and AI load.

For construction with no client attached, `/mc debug headless on` above is what
lifts that — on a server started with `-Dminecolonies.headless=true`, and nowhere
else.

### `/mc colony hut <colony> "<hut block>" <pos> "<blueprint>" [<level>]`

Adds a hut to a colony from the console, and with a level, asks for it to be
built.

```
/mc colony hut 1 "minecolonies:blockhutbuilder" -20 67 0 "fundamentals/builder1.blueprint" 5
```

The companion to `found`, and it exists for the same reason: a hut becomes a
building through `AbstractColonyBlock#setPlacedBy`, and `/setblock` does not call
that, so a hut block dropped in by command is one the colony has never heard of.
This gives the block entity the colony's pack and a blueprint inside it, then
registers it.

The blueprint path has to be given because nothing derives it: the Colonial pack
calls the farmer's hut `farm1.blueprint`, not `farmer1.blueprint`, and a building
with no path cannot have a work order made for it at all.

The level argument goes through `requestUpgradeTo`, so **free mode must be on for
the colony** — `/mc colony freemode <colony> on` — both for the direct jump to a
level and for the materials.

Run it twice over the same hut to lay a town out and then build it: the first hut
of all is refused a work order, because the colony has no builder's hut within
reach of the site yet. The second call finds the building already there and only
files the order. `buildnow` below has no such gate, and is how the first hut of a
console-raised colony gets built at all.

### `/mc colony buildnow <colony> [order <id> | at <pos> [<level>]]`

Finishes the colony's open work orders on the spot — no builder, no walking, no
materials. A builder takes something like twenty minutes of server time to raise
a level one hut, which is time every test that needs a *built* colony has to
spend before it can begin.

```
/mc colony buildnow 1                              every open work order
/mc colony buildnow 1 order 7                      just that one
/mc colony buildnow 1 at 20 67 20                  that hut, one level up
/mc colony buildnow 1 at 20 67 20 5                that hut, straight to level 5
/mc colony buildnow 1 at 20 67 20 0                take that hut down
/mc colony buildnow 1 at -50 67 -30                that decoration controller
```

**Operator, and free mode must be on for the colony.** Operator rights alone
would be the wrong bar: free mode
(`/mc colony freemode <colony> on`, above) is already the colony-wide "this
colony is a test fixture, not an economy" switch — it is what the level argument
on `hut` needs, and what conjures the materials a build would otherwise consume.
Building for nothing is precisely a free-mode act, and requiring the switch means
one mistyped colony id cannot flatten twenty minutes of somebody else's work on a
server carrying real colonies.

**It is the builder's own construction with the waiting taken out**, not a second
one. The blocks go down through `PlaceStructureOperation` over
`CreativeBuildingStructureHandler` — Structurize's instant-paste operation and
MineColonies' own handler for it, the pair the build tool uses to paste a hut in
creative — driven to the end of its last phase instead of one tick's worth per
tick, so the whole structure is standing when the command returns. A `REMOVE`
order runs the two removal stages of `AbstractEntityAIStructure` instead, with
the builder's own `skipRemoval` deciding what is left standing (the hut block
and anything else `IBuilderUndestroyable` stays, as it does for a builder).

Closing the order is the other half, and it goes through the same calls
`AbstractBuildingStructureBuilder#complete` makes: the decoration controllers get
their schematic data, the order leaves the work manager — which unassigns
whichever builder had claimed it and stops its AI mid-order — the construction
tape comes down, the colony statistics and the event log are written, and
`BuildingConstructionModEvent` is posted. The building's level rides in on the
anchor's schematic data exactly as it does for a builder, and is set by hand if
the blueprint did not carry it.

All four work order types are handled — `BUILD`, `UPGRADE`, `REPAIR`, `REMOVE` —
and all four work order classes: buildings (the town hall included), decorations,
plantation fields and mine nodes. The last three have no `IBuilding` at their
location, so for them the controller's own schematic data is the whole of their
state, exactly as it is when a builder finishes one.

**`at <pos>` files the order itself when there is none**, and that is not a
convenience. A colony's *first* work order can never be created through the
ordinary path: `requestWorkOrder` refuses one while no builder's hut with a
builder in it stands within `maxbuilderdistance` of the site, and a builder is
only hired into a hut that has been built. A colony raised with `found` is
therefore deadlocked — no town hall, no builder's hut, nothing. `at` goes
straight to `WorkOrderBuilding#create` and steps over that gate, and only that
gate: an order whose footprint reaches onto ground the colony has not claimed is
still refused.

A position holding a decoration controller rather than a hut is answered the same
way, with a `WorkOrderDecoration`. It takes no level: a decoration's level, where
it has one, is already part of the path its controller records.

**What it does not do.** It does not tidy up after a deconstruction any more than
the builder does: `REMOVE` flags the building deconstructed and leaves its level
where it was, which is what `AbstractEntityAIStructureWithWorkOrder` leaves too —
so `/mc colony diagnose` reports "flagged as deconstructed but still at level N"
afterwards, for a builder's deconstruction and for this one alike. A structure
whose placement does not finish inside its step budget is reported as failed and
its **work order is left open** on purpose: half a structure a builder can still
be sent at is better than half a structure with its order closed.

### `/mc colony territory [create <name> [<pos>] [<colour>] | colour <colony> <colour> | grow <colony> <radius> | bind <colony> | delete <colony>]`

Hostile territory: ground marked as an enemy's. It is a real colony underneath —
that is what buys it protection, persistence, client sync and a border on screen
for nothing — but a colony with a flag on it, no town hall, no citizens, no owner
and, crucially, **no land at birth**. You draw every chunk of it yourself.

With no verb it lists the territories in the dimension: name, id, centre, the
colour its border draws in, how many chunks each owns, and how many of those the
outside-facing `HostileTerritory` query can see. Those last two should always agree — the query is served from an
index that is rebuilt rather than edited, and the two numbers disagreeing is the
one failure of that design that would otherwise be invisible from in game.

`create <name>` makes one centred on where you are standing, or on `<pos>` if you
give one, so it runs from the server console too. It calls the colony
save data directly and deliberately skips the claim step `/mc` colony creation
normally runs, which is the one thing that would otherwise hand it the
`initialcolonysize` square around its centre. The owner is then set to
`[abandoned]`, which leaves every player at `NEUTRAL` rank there: nobody may
build in it, nobody may break in it, and nobody can be given permission to. If
you are holding a Territory Scepter it is pointed at the new territory for you.

Every territory is given a **border colour**, named or not. Before this they all
drew in the same red, so a second territory was ground you could see but not tell
from the first. Add a colour to `create` to choose it — `create Redhand red`, or
`create Redhand 120 64 -300 red` when a position is given too — or leave it out
and one is picked for you.

An unnamed colour is drawn from twelve, and one no other territory in that
dimension is already wearing is preferred, so the first twelve are all distinct
without anybody having to think about it. Past twelve there is nothing left to
give and it repeats.

Four of the sixteen are not in that palette. **White** is the colour the border
renderer uses for *your own* colony, and it is also what an untouched colony's
team colour already is, so it is both the wrong signal and the value that means
"never set" — a territory made before colours existed still has it, and still
draws in the old red. Black, gray and dark gray are out because a border is a
one-pixel line against whatever ground it crosses, and those lose against stone,
shadow and rain. Naming any of them explicitly still works; only the automatic
pick avoids them.

`colour <colony> <colour>` recolours a territory that already exists — spelled
`color` as well, both do the same thing. It refuses an ordinary colony: a
colony's colour belongs to its owner and is set in the town hall, and an operator
changing it from here would be editing somebody's town without being in it.

`grow <colony> <radius>` takes a square of chunks for it in one go — the bulk form
of what the Territory Scepter does a click at a time, capped at radius 8 (a 17x17
square, under the 21x21 `/mc colony claim` already allows). The cap is about
synchronous chunk generation: claiming a chunk loads it, and on ground nobody has
visited that means generating it on the server thread. Chunks another colony owns
are left alone and counted, never taken.

`bind <colony>` points the Territory Scepter in your hand at an existing colony.
`delete <colony>` gives every chunk the territory holds back and then removes the
colony. It refuses an ordinary colony — `/mc colony delete` is that, and it asks
whether to tear the buildings down.

A territory does not tick. `Colony#onWorldTick` takes a separate path for it that
does one thing, once every twenty ticks: `updateSubscribers`, which is how the
border reaches the client at all. No citizens, no buildings, no requests, no work
manager, no waypoints, no travellers, no day-time check, no raid check, and no
state machine. With nobody standing in it that call iterates two empty sets; with
somebody standing in it a packet only goes out when the territory has actually
been repainted, because nothing else marks it dirty.

Enemy ground is not treated as a neighbour. `getClosestNonHostileColony` is what
the three ordinary scepters, colony founding and the minimum-distance rule ask,
so standing on a territory does not make your own tools start editing it.

`/mc colony list` shows a territory like any other colony, and that is deliberate:
it is where you look up the id you need for `bind` and `delete`.

## Item

All four claim scepters draw the colony borders while held, the same picture
Structurize's build tool draws. The build tool shows you a border while you place
a hut against it; a scepter is where you *change* one, and claiming, releasing or
repainting with nothing on screen is guesswork.

The borders redraw as you edit them rather than when you walk into the next
chunk, so a column painted in or cut out appears at once.

### Land Claim Scepter

Right-click a block to claim the chunk you clicked and the eight around it.
Requires the `MANAGE_HUTS` permission, i.e. officer and above.

The only rule is that a chunk must have no owner. Distance is not a rule, so a
colony's territory may come in disconnected pieces with unclaimed ground between
them. Chunks another colony already owns are skipped rather than taken, and the
scepter says how many it left alone.

### Land Release Scepter

The other direction, which nothing offered before: right-click a block to give
its chunk back, or sneak-right-click to give back the eight around it as well —
one sneak-click undoes one claim-scepter click. Same `MANAGE_HUTS` permission,
asked of the colony that owns the chunk rather than the nearest one.

Chunks belonging to another colony are skipped, so a wide click on a border does
not take land off a neighbour. Building claims for the colony go with the chunk.
The one chunk it refuses is the colony's centre: everything that asks which
colony a position is in reads the chunk claim, so a colony whose centre is
unclaimed stops recognising its own town hall.

### Border Scepter

Draws a border inside a chunk instead of along the chunk grid. Right-click a
block to put its column inside the border, sneak-right-click to cut one out, and
hold the button to paint — the client repeats the interaction about five times a
second. Progress goes to the action bar rather than the chat, so painting does
not flood it.

It paints outside the border as well as inside it. A column drawn on ground no
colony owns takes that chunk for the nearest colony and starts its border empty,
so what the colony gains is the column rather than the whole chunk it fell in —
keep painting and the border follows wherever you walk. Rubbing the last column
off a chunk gives the chunk back, so the drawing can be undone as freely as it
is made. The colony's centre chunk is the one exception and is kept whatever its
border looks like.

Sneak-right-click the air to drop the drawing and go back to the whole chunk,
and right-click the air to be told what the chunk you are standing in currently
looks like.

**Rectangles.** Left-click a block to mark a corner; the next right-click fills
every column between that corner and where it lands, or cuts them out if you were
sneaking — the same sneak rule as a single click. The corner is consumed by the
fill, so holding the button never repeats a rectangle and the click after one is a
single column again. Left-click was free: the scepter is not a tool and still
breaks nothing.

The cap is 4096 columns, a 64x64 block square. That is a whole field and a great
deal more than anyone marks by accident; it is there to bound the mis-click, not
the feature. A rectangle that spills over a chunk boundary is allowed and pulls
those chunks in exactly as a single click does — at that size, at most 25 chunks,
against the 9 an ordinary claim-scepter click already loads. Chunks another colony
owns are skipped whole and counted.

The claim and release scepters did **not** get rectangles. Theirs would be a
rectangle of whole chunks, which is a bulk land grab rather than a hand-drawn
border, and it is the one that runs into synchronous chunk generation and the
claim map going out whole in every colony view packet.

### Territory Scepter

Takes whole chunks for a colony you do not belong to — in practice for a hostile
territory, which is how you paint the enemy's ground next to your own. Operators
only, and not by permission: a territory belongs to nobody on purpose, so there is
no permission on it that could ever be granted.

It carries its target rather than deducing it. The other three scepters resolve
their colony from where you are standing, which cannot work here: a territory that
owns nothing yet is at no distance from anywhere. So the target is written onto
the item by `/mc colony territory create` or `/mc colony territory bind`, and shown
on the tooltip and on every mid-air right-click.

Right-click a block to take its chunk, sneak-right-click to give one back. Chunks
another colony owns are refused; the centre chunk is refused too, the same reason
the release scepter refuses one — use `/mc colony territory delete` to erase a
territory outright.

It claims whole chunks and nothing finer, on purpose: shaping a territory inside a
chunk is the Border Scepter's job, and that item already works on a territory for
an operator.

**The border draws red.** A hostile territory's outline is full red whatever the
`colonyteamborders` client setting says — the check sits ahead of both the team
colour and the white/blue pair, because a new colony's team colour is WHITE, which
is the colour that means "this one is yours".

**And it draws from your side of the line.** A client only knows about a colony it
has been sent a view of, and views go to close subscribers, and you become one by
walking into a chunk that colony owns and stop being one the moment you walk out.
For a town that is right; for enemy ground it is backwards, since the whole point
is seeing where the line is from your own land. So a territory keeps as subscribers
everyone within 12 chunks of any of its ground, standing on it or not. Without that
the red border would appear only after you had walked through the enemy's land
once, and vanish again on relog.

Chunks another colony owns are not taken: painting on one needs `MANAGE_HUTS`
there, exactly as painting on your own does.

The result is a 16x16 mask on the chunk's claim, so it is real rather than
cosmetic: protection, whether a citizen thinks it is home, the raid spawner and
everything else that asks which colony a position belongs to all follow the
drawn line. The border the build tool draws follows it too, block by block
rather than chunk by chunk. A chunk with no mask — every chunk, until something
edits it — costs nothing extra to store or to send.

Not covered: the Journeymap overlay, which is chunk-level and will keep drawing
a partly claimed chunk as a whole one.

## Config

`forceloadallclaims` (new, default off) is the **default for colonies nobody has
decided about**, not a global switch: any colony that has been set with
`/mc colony forceloadclaims` keeps its own answer whatever this later says. Left
alone, changing it still moves every colony an operator has not spoken about.

`maxforcedchunks` default raised from 256 to 1024. It is a ceiling **per colony**
on force-load tickets, and 256 was chosen when only chunks with buildings on them
were ticketed — a real 138-building town already hit it and was being silently
cut short. 1024 covers a 32x32-chunk town; the geometric maximum inside
`maxColonySize` 20 is 1681; 0 disables the ceiling.

`maxbuilderdistance` (new, **default 2000**, range 16 to 5000) is how far from a
builder's hut a building may stand and still be built. It replaces a hardcoded 100,
which is the number upstream has always used and which this raises deliberately —
set it back to 100 for upstream behaviour.

This is the number behind **"There is no Builder close enough to work on this
building!"**. It is checked twice, and both checks now read this key: once when the
hut is placed, which is the message above and only appears when no builder was
assigned by hand; and once for every builder deciding whether it may take the work
order, which is what actually stops a distant hut from ever being built.

**The distance is three-dimensional.** Both checks use `BlockPos#distSqr`, so depth
counts as much as ground distance — a hut 100 blocks away and level with the builder
is in reach, the same hut sunk 60 blocks into a mountainside or up a tower is not.
That is worth knowing before raising the number to chase a symptom that is really
about height.

**It is paired with `maxpathfindingdistance`, which is now also 2000.** That setting
refuses a walk order beyond itself outright, so if you raise this one past it a
builder will accept a work order it cannot walk to. What saves such a builder is
`stuckrescueseconds`: a worker that spends a minute without getting closer is
teleported to where its job sent it. That is a rescue, not a design — the builder
arrives after standing still for a minute rather than never. Keep the two in step,
and read `maxpathfindingdistance`'s own entry below, because a long order only
computes along a corridor.

Raising it does not make a builder walk any faster, and a route that long needs the
chunks along it loaded the whole way — which in practice means `/forceload` or a
player travelling it, since a colony will not hold the middle. A builder sent 500
blocks off will spend most of its day walking. The alternative the message itself
suggests — assigning a builder to the hut by hand in the build screen — bypasses
this check entirely and always has.

Three things are exempt from the distance rule however it is set, because they are
what a colony needs before it has a second builder in place: a builder's own hut,
the town hall, and any hut assigned to a builder manually.

`decorationsoutsidecolony` (new, **default on**) lets a **decoration** stick out past
the colony border. It is the answer to *"Error on placement! Building or Decoration
partially outside of the colony!"* when what you are placing is a wall, a road, a
bridge, a statue or a scan.

Placement used to demand that **every chunk the blueprint touches** belongs to the
colony — not the anchor block, the whole footprint. A long wall laid along the
border therefore had to sit a chunk short of it, which is precisely where a wall
wants to be.

A decoration is anything whose anchor is neither a hut nor a plantation field. The
distinction matters because a decoration **owns no ground and files no claim**: it
is a work order and a pile of blocks. Letting its footprint cross the border costs
the colony nothing and takes nothing from a neighbour. Huts are unchanged, and so is
the plantation field, because both do claim.

**The anchor must still stand inside the colony.** The work order is filed with the
colony the anchor is in, and a decoration placed entirely outside every colony has
nobody to build it — the refusal in that case is not this rule but the absence of an
owner, and it uses the same message, which is worth knowing when the wall you are
placing is far enough out that you are really outside altogether.

Turn it off for upstream behaviour.

`raiderverticalvision` (new, **default 16**, range 3 to 64) and `raiderarchervision`
(new, **default 40**, range 16 to 64) are how far a raider looks for something to
attack. They are the raider side only; `guardverticalvision` is unchanged and this
does not touch it.

Both defaults are changes in behaviour and both old values are the range minimum, so
`3` and `16` together restore exactly what raiders did before.

**Vertical.** A raider looked three blocks up and three blocks down, because it
inherited the guard default and nobody ever overrode it. Its box sideways is sixteen.
So a defender standing on a four-block wall was not a target and the horde walked
underneath him. The default of 16 makes the box a cube of the sideways range it
already had. This is read off the box arithmetic; it has not been watched happening,
because there is no client here and summoned camp mobs die to the pathing stuck
handler inside a minute, which is too short to stage the fight.

**Archer.** A raider archer's bow opens fire at forty blocks and its eyes reached
thirty-two along one axis and sixteen along the other, so it could never use its own
range. The default of 40 matches the bow; the maximum of 64 is where the far corner
of the search box reaches the eighty blocks past which a raider drops the target
anyway.

**What it costs.** This search is the largest per-raider cost in a raid and the
defaults multiply the box volume of an average horde by about six. Measured on a
dedicated server with eighty-one raider archers spread over three levels plus eighty
mobs to scan: mean tick time 4.1-4.9 ms at the old values, 4.6-4.9 ms at the
defaults, 4.0-5.1 ms at the maximum — the three are not distinguishable. Six times a
small number is still a small number. Lower them anyway if a large server wants the
headroom back; the old behaviour is the minimum of each.

`maxcitizenpercolony` now accepts up to 1000, default unchanged at 250. A colony
is capped by the smallest of its bed count, the citizen-cap research and this
option, so the research ladder's top rung was raised to match — otherwise a
config of 1000 is clamped straight back to 500.

`boatspeed` (new, default 6, range 2 to 20) is how fast a citizen steers a colony
boat, in blocks per second. It replaces a hardcoded 0.3 blocks per tick, which is
the same speed. `/mc boatspeed` reads and writes it live — see above for the unit,
the ceiling and why the boat travels 0.9 of the number.

`maxpathfindingdistance` (new, **default 2000**, range 100 to 5000) is how far a
citizen may be ordered to walk in one go. An order beyond it is refused outright
and the citizen is sent home, which is why an enclave further away than this can
never be reached. The hardcoded number this replaced was 900, and 900 is what to
set for upstream behaviour.

**The check happens before the search and does not care how the route would be
travelled.** Water, rails and broken ground are all refused at the same distance —
nothing knows there is an ocean to sail until the search runs, and the refusal is
ahead of it. So a colony that ferries citizens across a strait needs this raised
past the strait, boat or no boat.

It is raised to 2000 by default so that `maxbuilderdistance`, also 2000, is
actually reachable. The two are a pair: the builder setting decides which work
orders may be taken, this one decides whether the walk to them is allowed at all,
and a builder that accepts an order it cannot walk to is worse than one that
refuses it.

**Raising it does not make any single search work harder.** The node budget is
capped at 8000 regardless of distance, so a 3000-block order gets exactly the
same allowance as a 300-block one, and a search that fails gives up in the same
place either way — measured, roughly 100 blocks from the start, whether the
target was 150 blocks off or 1000. What a long order does cost is one bigger
block cache, built on the server thread; that is now bounded too.

**It only helps where there is a corridor.** 3000 blocks along rails or open
water computes a full path in about 3000 nodes and 30 ms. The same 3000 blocks
over broken ground fails, exactly as 150 blocks of broken ground already fails.
Rails and water also need the chunks kept loaded the whole way, which in
practice means `/forceload` or a player who travels the route — a colony will
not hold the middle of it. `26.2/PATHFINDING-RAILS.md` has the measurements.

**A long route is not a cheap one — unless `stopsearchonarrival` is on.** With
that setting off, a 3000-block ride costs the single pathfinding thread the
same ~3000 nodes it always would have, and every other citizen on the server
waits behind it: the macro edges find the route much sooner but the search
still runs to exhaustion before returning it, so a raised limit buys reach, not
speed. With it on — which it now is by default — the same ride costs tens of
nodes instead of thousands.

`stopsearchonarrival` (new, **default on**) makes a search stop once it has
arrived and proved nothing cheaper remains, instead of expanding everything
cheaper than the route it found.

That sounds like it should always have been the behaviour, and the reason it
was not is worth knowing: on arrival the search rebalanced its own distance
estimate down to what the route actually cost, which left the queue with almost
no sense of direction and turned the rest of the run into "expand every node
cheaper than the answer". On ordinary ground that is a small tail. On a
corridor — rails, open water, a road — it is nearly the whole search.

**What it is worth, measured.** On a live 1000-citizen colony the whole tail is
only **3.7–5.6 %** of the pathfinding thread's time, because 93–96 % of
searches never arrive at all and a search that never arrives has no tail. Turn
it on there and the pool goes from 30 % busy to 26 %, about 10 % off the
average search. But on the routes it was built for it is not marginal: 800
blocks of rail falls from 6223 nodes to **16**, 796 blocks of open water from
8301 to **30**, 400 blocks of bare plain from 8109 to **402**. Every one of
those returned the identical path.

It also repairs a regression: the boat macro edges added in 0.0.17 made an open
sea *dearer* to search than before (801 nodes → 8301, and hitting the node
limit where it previously did not), because a fan of probe rays opens area
where a block-at-a-time march opens only a strip. This setting removes that
entirely.

**Why it shipped off in 0.0.19, and why it is on now.** It shipped off for one
reason only: it changes how every citizen, raider and animal on the server
finds its way, and at the time nobody had walked a path with it on — the
measurements were from a server with no player in it. That is no longer true,
so the default is flipped and the setting is being played. Nothing before
arrival changes in any branch, so a search that never arrives behaves
identically, and that was measured too. Measured upper bound on route quality
if the exit condition were wrong: 1.5–5.2 % dearer. Measured actual: −0.1 %.
The honest caveat that remains is that this is a broad behaviour change carried
on a small measured win on ordinary ground — 3.7–5.6 % of the pathfinding
thread — so if you see citizens routing oddly, `stopsearchonarrival false` is
the first thing to try, and worth reporting.

`/mc pathstats` reports the arrival tail directly, so the effect is visible
rather than taken on trust. `26.2/PATHFINDING-EXIT.md` has the full record.

`pathfindingthreads` (new, **default 1**, range 1 to 8) is how many worker
threads the pathfinding pool runs. One is the size the pool was hard-coded to
before this setting existed, so a server that leaves it alone builds exactly the
pool it built before. It is read once, when the pool is first made, so a change
here lands on the next server start; `/mc debug maxpool` above is how the size of
a pool that is already running is changed, and a size asked for that way holds
until the next switch or the next restart.

**Why it exists.** `/mc pathstats` can already say "the queue is the bottleneck:
a worker waits 164 ms before its search even starts", and until now there was
nothing an operator could do about it. Every path search on the server — every
citizen, raider and animal, in every colony — goes through the one thread.

**What raising it is measured to buy, on one small stand.** 60 citizens walking
between a dozen targets on a flat arena, ten minutes each at one thread and at
four, everything else identical:

| | 1 thread | 4 threads |
|---|---|---|
| searches finished | 17067 (28.3/s) | 15775 (26.2/s) |
| queue wait, average | **164 ms** (3.3 ticks) | **73 ms** (1.5 ticks) |
| queue wait, worst | 1.85 s | 2.16 s |
| searches that produced no path at all | **4 %** | **0 %** |
| mean tick time | 5.6 ms | 5.6 ms |

The queue wait roughly halves and the tick time does not move. The searches are
not comparable one for one — a search that has waited 164 ms is often started
against a citizen that has since been given a different order, and gives up
early, which is where the 4 % of empty results and the lower average node count
at one thread come from.

**Four threads on a four-core box did not starve the server thread**: the server
thread held 9–12 % of a core in both runs and the mean tick time was the same
5.6 ms. What went up was the pool's own consumption, 20 % of a core at one
thread against 30–48 % at four, because more of the queued work actually ran.

**The caveat is the one in the config comment, and it is not small.** The
searches read the live world through `ChunkCache`, which holds `LevelChunk`s and
reads them off the server thread; vanilla's `PalettedContainer` takes no lock on
the read path and its palette can be grown under a reader. Worse, a block with a
dynamic shape reaches `LevelChunk#getBlockEntity`, which *writes* to two plain
hash maps — `minecraft:moving_piston` is that block. None of this is new at one
thread: the pool has always run off the server thread and the race has always
been there. More threads widen the window rather than opening a new one.

**Measured against that, not just left as a worry.** The same stand was built to
hit exactly that branch: 225 pistons firing twice a second in the floor the
citizens walk on, and roughly 700 000 block changes per run poured into the
chunks being searched. An instrumented build counted **over 30 000
`moving_piston` states read by the worker threads out of live chunks in five
minutes**, with **four searches running at once**. Nothing broke — no
`MissingPaletteEntryException`, no `ConcurrentModificationException`, no null
from a worker, no watchdog, no crash, in either run or in the instrumented one.

That is an absence of failures over about half an hour on one small stand, on
one machine, at one moment of one snapshot. It is not a proof of safety: a data
race that does not fire is still a data race, and the honest reading is that the
window is narrow, not that it is closed. **1 stays the default for that reason.**
Raise it if `/mc pathstats` says the queue is your bottleneck, on a server you
can watch, and report anything odd.


`stuckrescueseconds` (new, default 60, 0 to disable) teleports a worker to where
its job sent it once it has spent that long without getting any closer.

The navigator already has a stuck handler, but it only rescues a worker that is
walking and getting nowhere. It cannot do anything for one whose destination has
no path to it at all: the pathfinder returns nothing, the AI sees "not there
yet", and asks again next tick. Nothing moves, nothing is logged, no request is
outstanding — the worker just stands there, and the usual answer is to break the
hut and place it again. A builder sent to a bridge over water or a tower on an
island does this indefinitely.

So this watches the destination instead of the path: a worker that keeps asking
for the same place and never closes the distance is stuck, whatever the
pathfinder is doing. It is put there, and the server log says who was moved,
where, and how close they had got. Arriving, or being sent somewhere else,
resets the timer — a worker standing at its work site is not stuck, and neither
is one making slow progress.

`animalpencontainment` (new, default on) keeps herded animals in the hut they
belong to. A cow, sheep, pig, chicken, rabbit or stabled horse standing inside a
herder hut is given that hut as its home — vanilla's own `Mob` home restriction,
the same one a leash sets — and every wander goal in the game then refuses to
pick a destination outside it. One that has been pushed, frightened or carried
out walks back on its own, because farm animals are given the return goal
vanilla already ships for iron golems and never gave to livestock.

The home is written to the animal's NBT, so it survives chunk unload and a
server restart with nothing tracking it. Default on because a pen that empties
itself over a few days is the behaviour being fixed, not a preference; turn it
off for vanilla wandering.

**To take an animal out of a hut on purpose, leash it, lead it out and unleash
it.** Unleashing clears the home — that is vanilla, not something added here —
and nothing tries to fetch the animal back afterwards. Leading it into another
herder hut of the right kind hands it to that hut instead. Pushing an animal out
without a lead does *not* release it: it will be brought back.

`animalpenslack` (new, default 4, 0 to 64) is how many blocks of grazing room
the animals get beyond the hut's own blueprint footprint.

`animalpenrecalldistance` (new, default 32, 0 to disable) is how far outside its
hut an animal has to be before it is put back by hand instead of being left to
walk. This is the backstop for the ones that cannot walk home at all — across
water, down a ravine, taken away in a boat — and 32 blocks is far enough that it
is not something a player standing at the fence ever sees happen.

Free mode stocks herder huts as well: see
[`26.2/ANIMAL-PENS.md`](26.2/ANIMAL-PENS.md).

`visitorspertavernlevel` (new, default 3, 0 to 10) is how many visitors a tavern
holds **for each level it has been built to**. It replaces a hardcoded 3, so the
default changes nothing: a level 1 tavern holds 3 and a level 5 tavern 15. At the
maximum a finished tavern holds 50.

Per level rather than a flat number because the tavern is the thing being
upgraded — a flat ceiling would either crowd a hut nobody has built up yet or
leave a finished one no busier than a fresh one.

Visitors are not free. Each one is a real entity walking around a loaded colony,
with vanilla goals, a small state machine and its share of the pathfinder. What
it does not have is the expensive half of a citizen: no job, no requests, and no
per-colony-tick data update. Call it a third of a citizen. 50 visitors is then
about what 17 extra citizens cost, against a colony ceiling of 250 — a real bill,
but a payable one, and that is why 10 is where the setting stops.

Set to 0 for no visitors at all. Nothing else spawns them in this port — the
cured zombie villager route is dead code on Fabric, see
[`26.2/VISITORS.md`](26.2/VISITORS.md) §2.2 — so 0 also means no recruitment.

`visitorintervalmodifier` (new, default 1.0, 0.05 to 5.0) multiplies **every**
wait between visitor arrivals: the delay before a new tavern's first visitor, the
gap between arrivals after that, and the pause after a visitor is killed. Below 1
they come faster, above 1 slower.

**Raise the ceiling and this together, or the ceiling will look broken.** A fresh
tavern waits 10000 ticks — over eight minutes — for its first visitor, and a
level 5 tavern in a full colony averages 2700 ticks between arrivals afterwards.
Those numbers were chosen against a ceiling of 15. Against a ceiling of 50 the
tavern takes something like two hours of play to fill, which reads as the setting
not working rather than as a queue being slow. At 0.2 the same tavern fills in
about 25 minutes.

There is a floor no setting gets past: the tavern is only asked once per 500
ticks, so it can never produce more than one visitor per 25 seconds. Filling a
ceiling of 50 therefore takes at least 20 minutes whatever this is set to.

What a visitor actually does once it arrives, and what it would take to make them
tourists instead of recruitment candidates, is in
[`26.2/VISITORS.md`](26.2/VISITORS.md).

`generations` (new, **default off**) turns citizens mortal. They age one colony
day per colony day, and when they have used up a lifespan they die of old age —
through the same death the game already has, so there is a grave, an undertaker
job to do, a vacancy the hut refills, an entry in the colony log and a line in
the death statistic.

**Off by default and it will stay off by default.** This kills citizens in a town
somebody may have played for weeks, and there is no undo. Nothing about it is a
fix; it is a mode you opt into.

Turning it on also changes reproduction, because the two cannot be separated:

* **Births no longer need a free bed.** A housing shortage is meant to be a
  pressure you answer, not a silent contraceptive. What still stops a birth is the
  population ceiling — the citizen-cap research and `maxcitizenpercolony` — but
  deliberately *not* the bed count, which is what `getMaxCitizens()` folds in and
  which would put the gate straight back.
* **The birth rate stops being a fixed timer and starts depending on the colony.**
  It is written as the death rate times a fertility multiplier — population over
  lifespan, times food, happiness, crowding and the `GROWTH` research — so a colony
  in neutral conditions exactly replaces itself and a well run one grows. A well
  fed, happy, roomy town with the growth research runs about **2.6×** replacement;
  a starving, miserable, three-to-a-bed one about **0.1×** and is dying. All four
  factors and their numbers are tabulated in [`26.2/GENERATIONS.md`](26.2/GENERATIONS.md).
* **A child born without a bed is homeless**, with the happiness penalty and the
  complaints the game already gives homeless adults — and, because misery shortens
  a life here, a shorter one. The colony managers are also told the same day, once
  per colony day, rather than being left to work it out from sinking happiness.

**Existing citizens do not all die at once.** They have no age recorded, so on the
first dawn after you switch this on each adult is given a random age between
nothing and *half* a lifespan. Nobody is close to death on the day you flip the
switch, and by the time deaths start they are already spread across the town.
Children start at zero; childhood does not count against the lifespan.

`generationslifespandays` (new, default 100, 7 to 10000) is how long an adult
lives, in **colony days** — one in-game day of *loaded* colony, twenty real
minutes. A colony nobody visits does not age.

100 comes off the turnover arithmetic. Deaths a day are population divided by
lifespan, so 100 puts a thirty-citizen town at one funeral roughly every hour of
play and the 250-citizen ceiling at two and a half a day. Below about 40 the
smallest colonies lose people faster than a school raises replacements; above a
few hundred nothing visibly happens in a session. Each citizen also gets a
personal deviation of up to a quarter either way, so a colony founded on one day
is not a cohort that dies on one day, and living conditions move it further:
misery shortens a life by up to half, a very happy life lengthens it by a quarter.

A safety valve caps old-age deaths at population/20 per colony day. At the default
that is twenty times looser than the natural rate and never binds; it exists so
that dropping this setting from 100 to 10 on a live colony does not kill the whole
top of the age distribution in one dawn.

`generationsbirthmodifier` (new, default 1.0, 0.1 to 5.0) multiplies the birth
rate. 1.0 means neutral conditions replace exactly; raise it if your colony keeps
shrinking, lower it if the school cannot keep up. No effect unless `generations`
is on.

The whole design, the arithmetic behind every number, and what was deliberately
not built, is in [`26.2/GENERATIONS.md`](26.2/GENERATIONS.md).

## Vanilla gamerules a colony owner needs to know about

Two threats to a colony have **no mod-side setting at all** on this port, and one
vanilla gamerule each. Both gamerules are world-wide and blunt: they cannot be
pointed at a colony, so turning either on changes the game everywhere, for
everybody, inside the borders and out. They are written down here because a server
owner who does not know they exist has nothing whatever for these two, and a
server owner who does know has a real answer even if it is a heavy one.

### Fire — `/gamerule fire_spread_radius_around_player 0`

**There is no colony fire protection, and there never was** — the 1.21.1 original
did not hook fire or lava either, so this is not something the port lost. What
changed is on Minecraft's side: `doFireTick` is gone in 26.2, and fire spread and
lava ignition now both gate on how close a *player* is, through
`ServerLevel#canSpreadFireAround`. The default radius is **128**, and `-1` means
"everywhere, as before".

Setting it to `0` disables fire spread and lava ignition outright, world-wide. It
is the only lever there is. The mod cannot narrow it to a colony: there is no
callback in front of `FireBlock#tick` or `LavaFluid#randomTick`, and the gamerule
is a radius around a player, not a region — so nothing the mod could set would
mean "inside these chunks".

Worth knowing even if you leave it alone, because the 26.2 default has an odd
consequence for colonies: **a colony with no player near it cannot burn**, and a
fire already burning in it does not go out either — it freezes at whatever age it
had and resumes when somebody walks back into range. Citizens do not count;
`anyPlayerCloseEnoughTo` walks tracked players, and a citizen is not one. So a
fully staffed colony with no human in it is fire-proof, and the same colony with
its owner standing in it is exactly as flammable as it was on 1.21.1.

### Mob griefing — `/gamerule mob_griefing false`

**There is no colony protection against mob griefing either, and again there never
was.** Endermen taking grass and dirt, ravagers trampling crops and eating leaves,
silverfish infesting stone, zombies breaking doors on hard difficulty, a wither
chewing through a wall — the 1.21.1 original hooked none of it, and neither does
this port. 26.2 routes all of it through the single global `mob_griefing` gamerule
with no per-region hook, and NeoForge's `EntityMobGriefingEvent`, which *was* the
per-region hook on the other loader, has no Fabric counterpart. So `false`,
world-wide, is the only answer available.

What survives mob griefing regardless, because it is blast- and break-proof on its
own account: **every hut block and every rack**. What does not: the blueprint
around them, which is ordinary vanilla blocks and is exactly as fragile inside a
colony as outside one.
