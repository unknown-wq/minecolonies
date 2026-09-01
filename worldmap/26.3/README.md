# World Map — client-only, Minecraft 26.3, Fabric

A full-screen top-down map of everywhere the client has been. Press **M** to open it, **Esc** to close it
and resume. There is no HUD minimap and there never will be one — this mod has exactly one screen.

Colours start from the vanilla `MapColor` palette and the same column scan a filled map item uses, and then
stop being a filled map: grass, leaves and water take their biome's own colour, relief is a real hillshade
off the surface normal rather than three brightness steps, water is a depth gradient with the sea floor lit
through it, and zooming out averages the columns it can no longer show instead of dropping three in four.
No texture-atlas averaging and no shaders -- it is arithmetic over two arrays, on the CPU.

* Loader: Fabric 0.19.4 · Minecraft 26.3-snapshot-10 · Fabric API 0.158.3+26.3 · Java 25
* `"environment": "client"`, one `client` entrypoint, no networking, no server side
* **No mixins and no access widener.** Everything is public Minecraft API or Fabric API
* **No hard dependency on anything but Fabric API.** MineColonies is optional: `fabric.mod.json` says
  `recommends`, the integration is one package behind `isModLoaded`, and with MineColonies absent the map
  is exactly the map it was before the integration existed

## Building

Always through the repository's build wrapper, never `gradle` directly — it holds a global lock and two
concurrent Loom invocations corrupt the shared cache:

```sh
tools/mc-build.sh worldmap/26.3 clean build --no-daemon
```

The jar lands in `worldmap/26.3/build/libs/worldmap-26.3-0.1.6.jar`.

The build compiles against MineColonies — the newest `../../26.3/build/libs/minecolonies-26.3-*.jar` if that
tree has been built, otherwise the newest `../../dist/minecolonies-26.3-*.jar`; override with
`-Pminecolonies_jar=<path>`. The build output comes first because the two disagree whenever this tree has
changed something the map reads, and a `dist/` jar from before that change fails the compile on a missing
method instead of quietly producing a map without the feature. **The colony overlay needs MineColonies
0.0.74 or newer**, which is the build that carries the raid time on the colony view. So in a fresh checkout
the order is `26.3` first, then `worldmap/26.3`, then `26.3` again to nest the finished map. It is `compileOnly` and is never nested into the output jar: it is a
separate mod the player installs, and the integration is optional at runtime. **The jar is not required to
build.** Without it, `build.gradle` drops `com/unknownwq/worldmap/colony/minecolonies/**` from the source
set, says so on the console, and produces a working jar with no colony overlay in it — which is possible
only because nothing outside that package names a MineColonies type or even that package's own classes;
`ColonyBridge` reaches the overlay through `Class.forName`. (Contrast `../../26.3/build.gradle`, where seven
references reach into the Simple Planes package from outside and make that jar a build requirement for a
feature that is optional at runtime.)

## Controls

| | |
|---|---|
| **M** | open the map (rebindable — Options → Controls → World Map) |
| **Esc** | close and resume |
| drag (left button) | pan |
| scroll up/down, **+** / **-**, or the two buttons in the top right | zoom |
| scroll left/right | pan sideways |
| **Space** | recentre on the player |
| right-click | the context menu — teleport, PNG export, layer toggles, forget a remembered colony |
| left-click a hut icon | open that hut's own window |

Zoom is a fixed ladder of powers of two: 1/4, 1/2, 1, 2, 4 and 8 pixels per block. Powers of two only, so a
tile is always a whole number of pixels wide and every blit lands on a pixel boundary — no seams, no
resampling blur. Zooming with the wheel or the keys keeps the block under the cursor under the cursor.

### Wheels and touchpads

One notch of a wheel is one event, so one rung per event is right for a wheel. A two-finger swipe on a
touchpad is dozens of events, and one rung per event would run the whole ladder in a single gesture.

Nothing in the API distinguishes them. `SDL_MouseWheelEvent` carries a device id and a direction, but
`SDLEventHandler` forwards only its two float axes, and `Screen#mouseScrolled` receives four bare doubles —
no event object, no source. Getting at more would need a mixin, and there are none here.

What is left is the size of the delta: SDL reports one detent as `1.0`, and a touchpad reports the same unit
in fractions of it. So an event a whole unit or more tall is treated as a notch and steps the ladder at
once — **the wheel is exactly as it was, one notch one rung, whatever the settings below say** — and
anything smaller is banked until it adds up to `scrollZoomThreshold` units, then steps one rung. A quarter
of a second of quiet ends the gesture and drops whatever is banked.

Two settings sit outside this. Options → Controls → **Discrete scrolling** makes Minecraft round every
delta to ±1 before any screen sees it, which makes a touchpad indistinguishable from a wheel and puts the
map back to one rung per event. And **mouse wheel sensitivity** scales every delta, so it scales the unit
too; the map divides it back out, so the threshold means the same thing at any sensitivity.

A scale bar sits above the bottom-left corner, showing the largest round number of blocks that fits in about
110 pixels; it is the only honest answer to "how far is that", since the `4x` in the header depends on the
GUI scale and the window size as much as on the zoom.

The block coordinates under the pointer are shown along the top edge — **X, Y and Z.** The Y is real data,
not a guess: it is the y of the block whose map colour was drawn at that column, recorded by the same scan
that produced the pixel (see *A tile has two planes* below). A column the map does not have in memory, or one
written by a version of this mod older than the height plane, shows `Y -`.

The player is a plain red plus at their X/Z, drawn at a constant size on screen whatever the zoom. It does
not indicate facing.

## Pausing — and when it does not happen

The screen returns `true` from `Screen#isPauseScreen()`. That is the whole mechanism, and it is worth being
precise about what it buys, because it is less than it sounds:

`Gui.isPausing()` asks the open screen, and `Minecraft` then sets its pause flag — but only when
`hasSingleplayerServer() && !singleplayerServer.isPublished()` (`Minecraft.java:1211`). **So the game pauses
in single player, and cannot pause on a multiplayer server or on a world opened to LAN.** Nothing a client
mod can do changes that: pausing means asking the integrated server to stop ticking, and on a real server
there is no integrated server to ask.

The screen checks the same condition itself and says so in the footer rather than implying a pause it cannot
deliver. On a server the map still opens and works normally; the world just keeps running behind it.

Esc is left entirely to vanilla `Screen`: `shouldCloseOnEsc()` is not overridden, and `keyPressed` hands
anything it does not recognise — Esc included — to `super`.

## The right-click menu

Right-clicking anywhere on the map opens a small panel at the cursor. It is not a `Screen` and not built out
of vanilla buttons: a screen pushed over the map would take the drag and scroll events that are most of what
the map is for, and vanilla buttons cannot be moved to the cursor without rebuilding the widget list on every
click. It is a list of strings, a hit test and two fills.

### Teleport to the clicked point

**A client-only mod cannot move the player.** Position is server state, and every client-side way to change
it is either rejected by the server's move check or is plainly cheating on a server that has none. So the
entry does the only honest thing available: it sends `/tp <x> <y> <z>` **as the player**, and the server
decides. In single player with cheats on it works. On a server where the player has no permission it is
refused, and **the server's own refusal is the feedback** — nothing here waits for a result, reports success,
or dresses the answer up.

The entry is greyed out, with the reason on the line under it, in three cases:

| | |
|---|---|
| no world | there is nothing to teleport into |
| **no height recorded for this column** | the map does not know the surface y there — see below |
| **the server has not given you `/tp`** | knowable, and not a guess: the server sends the client the command tree it is permitted to use (`ClientboundCommandsPacket`, which is what tab-completion is built from), and a player below the level `/tp` needs simply has no `tp` node in it |

That last check is not a guarantee — a permissions mod can revoke the command after the tree was sent — which
is exactly why the failure path stays honest rather than optimistic.

The y sent is **the recorded surface plus one**, so the player arrives standing on the ground. That is the
whole reason the tile format grew a height plane: teleporting to an unknown y is a choice between suffocating
in stone and a long fall.

### Save PNG

Writes `<gamedir>/worldmap/exports/<dimension>-<x>_<z>-<timestamp>.png` and puts the path in chat. Nothing is
uploaded, shared or opened.

What is exported is **the part of the world the map is currently showing, at one pixel per block whatever the
zoom**. The two alternatives are both worse. *Everything explored* has no bound — tiles live on disk, there
can be any number of them, and only a handful are in memory, so "all of it" means walking a directory and
decompressing an unknown number of megabytes on the client thread to make a file of unknown size. *What is on
screen at screen resolution* is a screenshot, which F2 already takes, and it is lossy in the case that
matters: zoomed out to 1/4 px per block, three of every four columns are simply not in the output. One pixel
per block over the visible rectangle is the version that is both bounded and lossless.

Only resident tiles are read; a tile that is not in the cache exports as black, exactly as it draws. Each
axis is capped at 4096 blocks (64 MiB of off-heap image while it is being written), and a wider view is
trimmed to that, centred, with a chat line saying so.

## How it works

### Scanning

`ColumnScanner` walks the column exactly as vanilla's `MapItem#update` does: descend from the
`WORLD_SURFACE` heightmap past blocks whose map colour is `NONE`, and count the depth of any fluid on top.
The parts of vanilla that exist only because a map item has a scale — averaging `scale × scale` columns into
one pixel and taking the most common colour — collapse away, because this map is always one pixel per
column.

**What vanilla does next is not copied.** It picks one of three brightnesses by comparing the column against
the one immediately to its north, and throws the fluid depth away. The scan here keeps both numbers and
records a *measurement*, not a pixel — see *A tile has two planes*. It also no longer needs the northern
neighbour chunk, which is why the old "the first row of a chunk is shaded flat if its neighbour had not
loaded" limit is gone rather than fixed.

The one thing the scan does decide for good is **biome tint**. Grass, leaves and water are tinted by the
biome everywhere in the world except on a map, which is why a filled map draws a swamp, a jungle and a
savanna in the same green. Here the block's map colour is moved towards the colour the game itself renders
that block with: grass and foliage are *scaled* by the ratio between the biome's colour and a temperate
plain's, so a grass block, a canopy and a moss carpet stay distinguishable from each other while all three
move together; water, whose `MapColor` resembles no rendered water at all, is interpolated instead. This is
baked in because there is nowhere in the two stored planes to keep the untinted colour as well, so
`biomeTint` only affects ground scanned after it is changed. Everything else about how a column is drawn is
decided at draw time.

**A chunk is scanned once and never revisited.** No block-change listener, no periodic refresh, no re-scan
when a chunk reloads, not even across restarts (`MapTile#hasChunk` checks whether the tile already holds
that chunk, and a tile loaded off disk knows what it holds). The map is a record of where you have been,
frozen at the moment you were there. Ground you have never visited and ground that is not loaded are both
**black**, and are deliberately indistinguishable.

The one thing on this map that does refresh by itself is the colony overlay — claims, raids and worker
assignments change while you play. That is the whole of the exception; surface pixels are untouched by it.

### A tile has two planes, and neither of them is a picture

**Base colour**, and **surface height**. The base plane's low three bytes are the column's colour before any
shading; its top byte says what the column *is* — unmapped, land, water of a given depth in blocks, or
"already finished, draw as stored". That last case is the Nether, where the drawn colour is noise with no
surface under it to light, and a column read out of a tile file written before this format existed.

The height is the y of the block whose map colour was taken — the top of the column, water surface included
— and it costs nothing to collect, because the scan already walks down from the `WORLD_SURFACE` heightmap to
find that block in order to know what colour to draw. Without it the map cannot report a Y and cannot
teleport anywhere safely; with it, both are real data.

An `int` and a `short` per column, so a tile is still a 1 MiB `int[]` plus a 512 KiB `short[]` — the water
depth rides in the byte that used to carry a single explored/unexplored bit. The height plane's own "no
data" value is `MapTile.NO_HEIGHT` rather than the base plane's kind byte, because the two genuinely
disagree in two places: a tile written by an older version has colours and no heights at all, and a roofed
dimension has colours that are vanilla's dirt-and-stone noise rather than a real surface.

### Shading, and why it is not part of the scan

The scan sees one chunk. Relief does not: the brightness of a column depends on the columns around it, and
four of a chunk's edges are in some other chunk that may not have loaded yet. Vanilla lives with that by
comparing each column against *only* the one to its north and bucketing the answer into three brightnesses,
which is exactly why a filled map reads flat — a continuous height field quantised to three values off a
one-directional gradient, with a checkerboard dither on top to hide the banding.

`MapShading` runs instead at the moment a tile becomes a GPU texture or a PNG, when the tile holds every
column it will ever hold. It is one class, it names no Minecraft type at all, and it applies, in order:

| | |
|---|---|
| **Relief** | a central-difference gradient in both axes over a 3×3-smoothed height field, softened so a forty-block cliff and a five-block bank do not saturate to the same value, lit from the north-west at about 48°. Flat ground comes out at exactly its own colour, so a plain is a plain |
| **Slope** | a small extra darkening by steepness whichever way the slope faces, which is what gives valley walls and the foot of a cliff their weight |
| **Water** | depth drives a continuous ramp from a pale shallow to a dark deep, and the relief is taken from the **sea floor** — surface height minus depth — so drowned terrain reads through the water. A water column touching land is brightened, which draws the coastline as a line |
| **Elevation** | a deliberately weak hypsometric tint: high ground a shade warmer and lighter, low ground a shade cooler |
| **Contours** | thin darkened lines where the surface crosses a multiple of `contourInterval`, skipped across cliffs where several would land in one pixel and merge into a blot. **Off by default** — see *Known limits* |

The **smoothing** is the part that matters most and is least obvious. The recorded surface is the top of the
column, which over a forest is the canopy: lighting it directly shades every individual treetop and the
landform underneath disappears into static. Averaging first pushes the relief down to the scale that is
actually terrain, and the block colours go on drawing the trees — the division of labour a printed map uses,
where the tint says what is growing and the shading says what shape the ground is.

All of it happens in **linear light**. Multiplying sRGB bytes directly — which is what
`MapColor.calculateARGBColor` does — crushes shadows and washes out highlights, and the difference is
visible on any slope. Two lookup tables cost a few nanoseconds a pixel. Flat ground is drawn at 0.90 of its
base colour, close enough to vanilla's `NORMAL` (220/255) that ground drawn from measurements and ground
still stored as a finished pixel do not read as two different maps, and far enough below 1.0 that a sunlit
dune does not clip to white.

Because the shading is derived rather than stored, **the look can change between builds without asking
anybody to re-walk their world**, and the settings below take effect on everything already on disk.

### Zooming out

Below one pixel per block the blit takes one column in four, or one in sixteen, and throws the rest away: a
coastline becomes a dotted line and a forest becomes static. There is no mip chain to lean on — a
`DynamicTexture` is one level with a nearest sampler, both of them vanilla's own choices and neither
reachable without a mixin — so the columns are averaged in blocks the size of the sampling stride while the
pixels are being built anyway, and the texture keeps its size so every blit still lands on a pixel boundary.
`smoothZoomedOut=false` puts the old behaviour back.

### Threads

* **Client thread** — `ClientChunkEvents.CHUNK_LOAD` records a chunk position and returns. Each tick, up to
  `chunksPerTick` (32) of those positions are resolved into `LevelChunk` objects and handed to the scanner.
  Resolving a chunk out of the chunk source is the one part that genuinely has to be on this thread.
* **Scanner thread** — one thread, minimum priority. Scans, merges into tiles, loads tiles the screen asked
  for, saves dirty tiles and evicts old ones. One thread rather than a pool because a chunk scan is a few
  hundred microseconds and serialising removes every question about two scans racing on one tile.
* **Render thread** — reads a tile's two planes directly out of the cache, shades them into a pixel buffer,
  and creates and uploads GPU textures. Vanilla's own `MapTextureManager` builds its `DynamicTexture`s from
  inside `extractRenderState`, so that is where this does it too.

A scan job carries the `LevelChunk` object, not a position, so the scanner never touches the chunk source.
If the chunk unloads first, the object is merely detached and still holds what it held when it was live.
Reading block states off the client thread is still a read of a structure the client thread may be writing,
so every scan is wrapped in a catch-all: a torn read costs one chunk of map, not a crash.

### Tiles on disk

`<gamedir>/worldmap/<world>/<dimension>/<x>_<z>.wmt`, where `<world>` is `sp.<save-folder>` in single
player and `mp.<server-address>` on a server, so two worlds never share pixels.

The format is gzip over a twelve-byte header (`WMT1`, a version, the tile side) and then the tile's planes.
**Raw, not PNG**, for three reasons that agree: a tile is updated a chunk at a time, so every save re-encodes
arrays that are already in memory and PNG buys nothing there; raw needs no codec, so a load is two reads
straight into an `int[]` and a `short[]` with no `NativeImage` and no off-heap allocation on the writer
thread; and gzip already does what PNG's filters would — map tiles are long runs of identical colour and
slowly-varying height. Saves go to a `.tmp` and are moved into place, so an interrupted write cannot leave a
tile that reads back as garbage.

| version | payload |
|---|---|
| **v1** | `512 × 512` little-endian finished ARGB ints. Colour only. Written by builds before the height plane |
| **v2** | the same ints, then `512 × 512` little-endian shorts of surface y |
| **v3** | the same shape as v2, but the ints are the **base plane** — an unshaded colour and a kind byte — rather than a finished pixel. **Written by every save now** |

v3 is the same number of bytes as v2 and compresses slightly better, because a base plane holds fewer
distinct values than a shaded one.

**Old tiles are kept, not discarded.** A v1 or v2 file is read as what it is: its ints are finished pixels,
so they load as *pre-shaded* columns and draw exactly as they drew before, and a v1 file's heights are every
one `NO_HEIGHT`. Losing a map somebody walked is a worse outcome than a region of it that is a version
behind. **A v1 file cannot be read as v2 or v3 garbage**: the version is checked before a single byte of
payload is read, and v1's payload is a different length, which the length check catches independently.

**An old region heals itself as it is walked over.** Chunks are scanned once and never revisited, with one
exception that has always existed and now covers one more case: a chunk with no heights, *or* a chunk whose
colours are finished pixels, is let through for exactly one more scan. That replaces the pixels with the
measurements they should have been, and the tile is rewritten in v3, so old ground picks up its heights, its
water depths and its relief the next time you are near it. It cannot happen twice — a re-scanned chunk has
heights everywhere and no pre-shaded columns left. Ground you never go back to keeps the flat look it was
drawn with, which is the honest answer and not a broken one.

### Memory

| | |
|---|---|
| Tile | 512 × 512 blocks: a 1 MiB base-colour `int[]` plus a 512 KiB height `short[]` — **1.5 MiB** |
| CPU cache | **64 tiles = 96 MiB**, evicted least-recently-used by the scanner thread, saving anything dirty on the way out |
| GPU textures | **48 textures = 48 MiB**, colour only, owned by the screen and freed the moment it closes — nothing sits in video memory while the map is shut |
| Texture uploads | **2 per frame**, so a newly opened map fills in over a few frames instead of stalling one |
| Shading scratch | one `MapShading` per screen, about **2.5 MiB** of reused buffers, plus a 1 MiB pixel scratch |

An upload is no longer a copy: it shades 262144 columns and then writes them. Measured over forty runs on a
shared 2.8 GHz container vCPU it is **7.6 ms per tile at best and about 8.5 ms on average**, against
roughly 1 ms for the plain copy it replaces on the same machine — call it eight times the cost, and expect
rather better than that on a desktop CPU that is not sharing four cores with a build.

That is bounded rather than continuous. The result is cached against the tile's revision, so a tile that has
not changed costs nothing, and at most two tiles are uploaded per frame. The worst case is opening the map
onto a screenful of explored ground it has no textures for: a couple of dozen frames at a reduced rate while
they fill in, on a screen that is drawing nothing else and, in single player, with the world paused behind
it. Setting `hillshade`, `waterDepth`, `elevationTint` and `contourInterval` all to 0 takes a short cut that
skips the whole of it and puts the copy back.

The height plane raised the CPU ceiling from 64 MiB to **96 MiB** at the default `cpuTileCap=64`. The tile
count was left alone rather than cut to hold the old byte figure, because 64 is not an arbitrary number: it
is what covers a 1920×1080 window at GUI scale 2 down to the widest zoom the map offers (1/4 px per block
over 3840×2160 blocks is 8×5 tiles) with room for panning. Lower `cpuTileCap` if 96 MiB is too much — the
cache simply evicts sooner and reloads from disk. The GPU figure is unchanged: textures carry colour only.

A view that needs more textures than the GPU cap — a 4K window at GUI scale 1, fully explored, zoomed right
out — draws the surplus tiles as black until they cycle back in. Both caps are configurable.

## MineColonies

Optional, in the strong sense: **the map runs, opens and behaves identically with MineColonies absent**, and
on such an installation no class of the integration is ever loaded. `fabric.mod.json` carries a `recommends`
entry and no `depends`.

### How the optionality is actually enforced

`ColonyOverlay` is an interface with a complete do-nothing default, `ColonyOverlay.NONE`. Every draw loop in
the screen reads a `ColonySnapshot` — plain records of positions, colours and strings, **not one
MineColonies type among them** — so the screen compiles and loads whatever is installed, and gets
`ColonySnapshot.EMPTY` when nothing is. Everything that names a `com.minecolonies` class lives in the single
package `com.unknownwq.worldmap.colony.minecolonies`.

`ColonyBridge` picks between the two behind `FabricLoader.isModLoaded("minecolonies")`, and it names the real
overlay **as a string, resolved with `Class.forName`**. That is not fussiness. The obvious version —

```java
return loaded ? new MineColoniesOverlay() : ColonyOverlay.NONE;
```

— fails twice. At runtime, naming the class in a `return` whose declared type is `ColonyOverlay` makes the
verifier check assignability, which may load `MineColoniesOverlay` and through it every missing
`com.minecolonies` class **when `ColonyBridge` is linked** — before the `if` runs, and outside any `catch`.
At build time, a compile-time reference would also stop `build.gradle` from dropping the package on a machine
with no MineColonies jar. With the string, neither happens: the `catch (Throwable)` really can catch a
loading failure, and a build without the jar really does produce a working mod.

### What is drawn

| | |
|---|---|
| **Your colony borders** | 25 % fill plus a 2 px opaque outline, in the colony's own team colour |
| **Other colonies** | the same, with a much weaker fill and a 1 px outline — on a populated server most of what is in view is somebody else's, and at equal strength the map becomes a patchwork |
| **Huts** | the hut block as an item, 16 px on screen at every zoom, framed in the colony colour, with a corner pip when a work order is open |
| **Colony names** | at the centre, with the citizen count |
| **Graves** | a cross per grave |
| **Waypoints** | a diamond per waypoint (off by default — a colony that uses them has a lot of them) |
| **Raids** | a raided colony's outline gains 2 px and its label gains a `[raid]` tag; each recorded raider spawn point gets a small downward triangle, **red while that colony is being raided** and in the colony's own colour once it is over. Drawn at every zoom, unlike the other glyphs — see below |
| **Hostile colonies** | a heavier outline with a one-pixel black line down its middle — the double line a frontier is drawn with. Permanent, not a passing state |
| **Fields** | a 7 px square per farmer or plantation field, hollow when nobody is assigned to it and filled when somebody is, with a thin line to the hut that owns it |
| **Patrol routes** | a closed polyline through a guard tower's manual patrol points (off by default) |
| **Remembered colonies** | a dashed outline and *no* fill, for a colony read back from disk that has not been seen live this session |

Colours come from `IColony#getTeamColonyColor()`. **MineColonies has already given every colony a colour**,
players know their own by it, and it is on their banners and their citizens; this map does not invent a
second palette. A colony whose view has not reached the client yet is drawn in neutral grey with no label,
and picks up its colour and name the moment the view arrives.

`Action.MAP_BORDER` and `Action.MAP_DEATHS` are honoured — they are the permissions a colony uses to say who
may see its outline and its graves on a map, and a map that ignored them would be a wallhack with extra
steps. Huts, fields, patrol routes, graves and waypoints are drawn for colonies you belong to only, because
the client is not sent anybody else's building list; there is nothing to read for the others and no point
pretending otherwise. Raid state and the hostile flag are the exception: they are fields on the colony view
itself, which the client already has for every colony whose border it can see.

### The outline is the region's boundary, not a box per chunk

Two obvious approaches are both wrong, and MineColonies' own JourneyMap integration
(`../../26.3/src/main/java/com/minecolonies/core/compatibility/journeymap/ColonyBorderMapping.java`, parked in
`optional-integrations.txt`) says so by construction. Stroking a rectangle around every claimed chunk fills
the interior with a grid of seams and buries the real border in them. Taking the outer contour and drawing
that **draws a lie**: a colony's claim is not necessarily simply connected — a chunk in the middle can belong
to a neighbour or to nobody — which is why JourneyMap's type for this is `MapPolygonWithHoles`, hull *plus*
holes.

`ChunkOutline` keeps every grid edge with a claimed chunk on **exactly one** side. That set *is* the
boundary, outer ring and every hole together. It needs no ring-chaining pass and no winding decision, and the
case that breaks contour tracing — four chunks meeting at a corner, two of them claimed diagonally — is not
ambiguous here, because nothing has to be chained through that corner.

The fill cannot then be "the outline polygon, filled", because of those holes. It is one rectangle per
claimed chunk instead: adjacent, never overlapping, so a translucent fill blends exactly once everywhere and
there are no darker seams down the shared edges.

### Live, and only the colony layer

Claims change while you play — a builder finishes a hut on the rim, a colony is abandoned and a hole opens in
the middle of its neighbour, a raid starts — so the claim map is re-read once a second and the shapes rebuilt.
**This is the only thing on this map that refreshes by itself.** Surface pixels are still scanned once per
chunk and never revisited; nothing in the integration touches that rule.

The re-read is batched at **250 entries per client tick**, which is `ColonyBorderMapping.UPDATES_PER_TICK`
kept deliberately: on a populated server the claim map has tens of thousands of entries and walking all of
them inside one tick is a visible stutter. A poll that has not finished draining blocks the next one rather
than queueing a second copy of the same work behind it. Every cached chunk is queued as *unowned* ahead of
the fresh entries, which is what makes a claim that has **gone away** disappear — without it a colony could
only ever grow on screen.

### Colonies that survive leaving and restarting

The claim map on the client is filled in by `ColonyView` packets, and the server sends those to a player
standing on the colony's claimed chunks — plus, once, at login, to a player who holds a **colony manager**
rank in it (`EventHandler.onPlayerEnterWorld` walks every colony and pushes a view to the managers). So
borders survived walking away, because the client's map only ever grows within a session, but they did not
survive quitting: on the next login you had the colonies you manage and **nothing else**, and every
neighbour's border came back only by walking into it again. That is the opposite of what the rest of this
map promises.

So the overlay now keeps one `ColonyMemory` record per colony id and writes it to
`<gamedir>/worldmap/<world>/<dimension>/colonies.wmc` — **the same directory as the tiles, under the same
world key** (`sp.<save-folder>` / `mp.<server-address>`), gzipped behind a `WMC1` header, written to a
`.tmp` and moved into place, exactly as `TileStore` does it. Deleting a world's map directory has always
deleted everything the map knows about that world, and it still does.

What is in it: id, name, citizen count, team colour, membership, the hostile flag, the centre, the packed
claimed chunks, a **last-seen timestamp**, and the hut, field, patrol-route and raider-spawn lists. What is
not: whether a raid is running, who is working in which hut, and where the graves are. A remembered colony
is not claimed to be *doing* anything; it is claimed to have *been there*. The outline is recomputed from
the chunk set on load rather than stored, so the file cannot disagree with itself.

**Live always wins, and an empty read never wins.** Three rules, and they are the whole of the merge:

* a colony with live claim data is **live**, and its chunks, name, colour and centre come from the live
  view — nothing off disk can overrule them;
* a live colony whose *view* has not arrived yet keeps the remembered name and colour instead of being drawn
  as an anonymous grey blob. That is filling a hole, not overwriting anything;
* an **empty** live list does not replace a non-empty remembered one. Building extensions and building lists
  are pushed to close subscribers only, so a colony whose border you can see but whose ground you have not
  stood on this session reports zero huts and zero fields. The hut list is what tells the two apart:
  MineColonies sends buildings and extensions to the same audience in the same pass, so a **non-empty** hut
  list means this client is a close subscriber and an empty field list from that colony is the truth — the
  last field really was removed and the marker goes.

**A remembered colony is never deleted automatically**, and cannot be. The live claim map only ever contains
colonies somebody is standing in, so "deleted while you were away" and "out of range" are the same
observation, and a map that guessed would be wrong in whichever direction it guessed. Instead a remembered
colony is drawn as remembered — dashed outline, no fill, dimmed label — its tooltip says how long ago it was
last seen, and right-clicking inside it offers **Forget \<name\>**, which removes it from memory and from the
file. That entry is offered for remembered colonies only: a live one is not the map's to forget and the next
poll would put it straight back.

A colony that revokes `Action.MAP_BORDER` is dropped from the remembered list as well as from the live one,
so having seen a border yesterday is not a way round the permission.

The file is rewritten whole, at most every 30 seconds, on a single low-priority daemon thread — and
synchronously on the calling thread when the world is left, when the client stops and when the player
forgets a colony, because in those three cases there may be no later chance.

### Collapsing, and the layer toggles

Fixed-size markers pile up as you zoom out: a colony with a hundred huts becomes a hundred overlapping icons
on a patch of map fifty pixels across. So a hut icon within 10 px of one already drawn this frame is dropped,
and a name whose box would overlap one already placed is dropped; own colonies draw last and win the space.
What you can click is exactly what you can see — hit-testing reads what was drawn, so a collapsed icon is not
secretly still there under the cursor.

Past that, the ten layer toggles in the right-click menu are the answer, and they are the reason a large
colony stays readable at all.

**What scales and what does not.** Anything that is a real shape on the ground scales with the map, because
drawing it any other size would be a lie about where it is: the claimed area, the outline (dashed, doubled or
heavier as the case may be), and a guard patrol route, which is a path a citizen actually walks and whose
length is the point of it. Anything that is a glyph marking a point is fixed in screen pixels and is dropped
below 1 px per block along with the rest — hut icons, grave crosses, waypoint diamonds and field squares.

**Raider-spawn triangles are the exception and are drawn at every zoom.** The cut exists because a colony
carries dozens of huts, graves and fields, all of them inside the border and all of them landing on the same
few dozen pixels once you zoom out. A colony carries a handful of spawn points and they are the only glyph
here that is *outside* the border — a raid comes out of the ground one to five hundred blocks away, and
zooming out far enough to see that much ground is precisely how anybody looks for one. Cutting them at 1/2x
deleted the marker at the moment it was wanted and left a colony tagged `[raid]` with nothing on the map
saying where from, which is what "the Raids layer is on and nothing appears" looks like from the inside.

## Configuration

`config/worldmap.properties`, written with its defaults on first run and never rewritten afterwards, so a
hand-edited file is safe. Read once at startup; changes need a restart.

```properties
chunksPerTick=32          # chunks fed to the scanner each client tick
cpuTileCap=64             # live tiles in heap; 1.5 MiB each -> 96 MiB at this value
gpuTileCap=48             # live GPU textures; 1 MiB each (colour only)
saveIntervalSeconds=20    # how long a modified tile may wait before being flushed
scanEnabled=true          # false: stop scanning, still display what is already on disk
scrollZoomThreshold=2.0   # wheel notches' worth of fine scrolling per rung of the zoom ladder
scrollPanPixels=16.0      # screen pixels panned per unit of horizontal scroll; 0 turns it off
biomeTint=1.0             # how far grass, leaves and water move towards their biome colour; baked in at scan time
hillshade=1.0             # relief shading strength; 0 draws the ground flat
waterDepth=1.0            # water depth ramp and coastline highlight
elevationTint=0.55        # hypsometric tint: high ground warmer, low ground cooler
contourInterval=0         # contour line spacing in blocks; 0 draws none
smoothZoomedOut=true      # average columns together below 1 px per block instead of dropping three in four
```

Everything from `hillshade` down is applied when a tile is drawn, so changing it repaints the whole map,
including ground mapped years ago. **`biomeTint` is the exception**: it is decided when a chunk is scanned
and chunks are never re-scanned, so it only applies to ground you have not mapped yet.

`scrollZoomThreshold` is the touchpad knob, and the one worth touching. It applies only to fractional
deltas, so raising it never makes a wheel sluggish. **Raise it** — 4, 6, 8 — if a two-finger swipe still
runs through the zoom levels too fast; **lower it** towards 1 if the map barely moves. How much scroll a
swipe produces varies by platform and driver, so there is no default that is right everywhere; 2.0 is a
starting point, not a considered answer for your laptop.

The colony layer toggles are deliberately **not** here. They belong to what is on the map in front of you
right now rather than to a preference, they change several times a session, and this file is read once at
startup and never rewritten. They live in the right-click menu and reset when the game restarts.

## Known limits

* **Contour lines are off by default**, and the reason is the height field rather than the drawing. The
  surface this map records is the top of the column — canopy, roof, snow layer — so a contour taken from it
  traces the vegetation as much as the ground, and draws a false line round the edge of every forest. On
  bare terrain they look like cartography and on wooded terrain they look like clutter, and the relief
  shading already carries the landform, so `contourInterval` is there for anyone who wants them and set to
  0 otherwise.
* A tile's outermost row and column take a one-sided gradient rather than a central one, because a tile is
  shaded on its own. That is one pixel in 512 and the smoothing pass makes the error smaller still; reaching
  into whichever neighbouring tiles happened to be resident would cost a lookup per edge column for
  something nobody can see.
* Roofed dimensions (the Nether) get vanilla's pseudo-random dirt-and-stone noise, exactly as a map item
  does, because the real surface there is the bedrock ceiling.
* A chunk that is nothing but air and void is re-scanned each time it loads, since it leaves no pixels
  behind to recognise it by. It costs 256 heightmap lookups and produces the same nothing.
* Tiles written before the height plane keep their colours and never gain heights — see *Tiles on disk*.
* Colony claims are drawn at **chunk** granularity. MineColonies can claim part of a chunk
  (`IChunkClaimData#hasPartialClaim()`, for borders redrawn with the Border Scepter) and a partially claimed
  chunk is drawn whole here. Doing better means running the outline on a 16× finer grid.
* **Raider spawn points are dated, and the date comes from MineColonies.** `getLastSpawnPoints()` is a list
  of positions and nothing else, so a marker on its own could be from a raid that ended days ago and there
  was no way to tell. The manager did know: every entry in its raid history is stamped with the world's game
  time as the raid is created, and nothing outside the manager could read it. It is exposed now
  (`IRaiderManager#getLastRaidTime()`), synced on the colony view beside the positions, and remembered on
  disk with them, so the tooltip says *how old* — "last raid — 2 in-game days ago" — instead of
  "it carries no date". Game time and not wall-clock: it is the number the colony stamps the raid with, it
  is not what `/time set` moves, and a world nobody has played for a week has not aged its raid by a week.
  `isRaiding()` is still the separate answer to whether one is happening now, and a marker of a live raid is
  drawn red and says so. The undated wording survives for the two cases that really are undated — a colony
  remembered from a file written before the time was carried, which picks its date up the next time it is
  seen live.
* **Only manual patrol routes are drawn.** A guard tower left on automatic patrol has an empty
  `getPatrolTargets()`, because that list is the positions set with the guard scepter; there is no fixed
  route to draw for an automatic tower and none is invented.
* A remembered colony's hut names and its field states are frozen at the moment it was last seen, and hut
  names in particular were resolved to text in whatever language was in use then. Walking back into the
  colony replaces the lot.
* **`AbstractBuildingGuards.View` is a `com.minecolonies.core` class** — the one such reference in this mod,
  and the only route to the patrol list, since `IBuildingView` does not declare it. It is isolated in
  `GuardPatrols` behind an `instanceof`, and a build of MineColonies that has moved or renamed it costs the
  patrol layer, one warning, and nothing else.

## Intentionally not built

HUD minimap, cave mode, entity radar, texture-atlas colours, LOD pyramids, shaders of any kind. There is no
HUD minimap and there never will be one; this mod has exactly one screen.

**Texture-atlas colours** — averaging each block's actual texture instead of using its `MapColor` — is the
one of these that would visibly help, and it is still not built. It needs the block atlas, which means the
render thread and a resource reload hook for a value the scanner thread wants, and it replaces a palette
players already recognise from every filled map they have ever held with one nobody has seen. Biome tint
gets most of the same benefit for none of that.
