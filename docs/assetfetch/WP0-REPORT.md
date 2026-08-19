# WP0 — Phase 0 extraction & delta: report

Executed 2026-08-19 on the box holding the retired working copy `/home/user/minecolonies`.
Scratch/work directory (never committed): `/home/user/assetfetch-extraction` (`$OUT`).

**Stop-condition result: PASSED.** The upstream→port delta is exactly
**10 added / 66 modified / 0 deleted**.

Two numbers in the brief did not survive measurement (details in
[Deviations](#deviations)): the mechanical-vs-hand-edited split of the model
patches is **5/54**, not 6/53, and the port-owned language file has **357 keys**
(351 added + 6 rewritten), not 314 (312 + 2). Both are counted, evidenced and
reproducible; neither affects the 65-file patch inventory.

---

## 1. Provenance and extraction

Objects re-verified with `git cat-file` before use:

| object | type | role |
| --- | --- | --- |
| `ea4243c8f50bb0317554fa82bf813adcec5d4a92` | commit | retired port head — everything port-side is read from here |
| `325157eba3d971d75502cb58107a176db883c9be` | commit | upstream asset baseline (`version/1.21`) |
| `e7aa68bf8459544bc940af24de22bb0fff30bc55` | tree | that baseline's `assets/minecolonies` tree |

The working copy sits on the unrelated branch `claude/fisherman-boat`; every tree
was read out of the commit/tree objects with `git archive`, never off disk.
Nothing had to be fetched from `upstream` — all objects were already local.

Extraction is scripted at `26.2/tools/assetfetch/extract_phase0.sh`:

```
26.2/tools/assetfetch/extract_phase0.sh /home/user/minecolonies /home/user/assetfetch-extraction
```

### File counts per tree (measured)

| tree | files | note |
| --- | ---: | --- |
| `$OUT/port-26.2` | **4832** | port's former `26.2` ARR tree at `ea4243c8` |
| `$OUT/upstream` | **4822** | upstream baseline tree `e7aa68bf` |
| `$OUT/port-1.21.1` | **4822** | port's `1.21.1` snapshot — **byte-identical to `$OUT/upstream`, 0 differences** |
| `$OUT/generated` | **3792** | datagen output at `ea4243c8` (incl. 3481 `textures/entity_icon`) |

`4832 = 4822 + 10`. The `port-1.21.1 == upstream` result is an independent
confirmation that `e7aa68bf` really is the port's asset baseline.

### Reconciliation of known-unverified item 8 (the "4834 removed" figure)

Resolved, with no branch drift involved:

```
git ls-tree -r --name-only ea4243c8 26.2/src/main/resources/assets | wc -l   ->  4834
```

4834 = **4832** files under `assets/minecolonies/` + **2** files under
`assets/minecraft/` (`atlases/blocks.json`,
`textures/gui/container/creative_inventory/tab_minecolonies_background.png`).
The original sweep removed the whole `assets/` directory and counted 4834; the
two `assets/minecraft` files were later restored on `main` in `49eb26f7`. The
brief's guess that "the difference is likely `LICENSE`" was wrong in mechanism:
`LICENSE` (19 bytes, the string `All Rights Reserved`) lives **inside**
`assets/minecolonies/` in both trees, is byte-identical between them, and is
therefore already inside both the 4822 and the 4832 counts. It is neither an
added file nor a patched one; it simply arrives with the fetched pack.

---

## 2. The delta

`diff -rq $OUT/upstream $OUT/port-26.2`:

| class | count | expected |
| --- | ---: | ---: |
| added (port-only) | **10** | 10 |
| modified | **66** | 66 |
| deleted (upstream-only) | **0** | 0 |

Full machine-readable list: `$OUT/meta/delta.json`; raw `diff -rq` output:
`$OUT/meta/diff-raw.txt`.

Modified breakdown: 60 `.json` + 6 `.xml`. Of the 60 JSONs, one is
`lang/manual_en_us.json` (handled as the language split, §4, **not** as a
patch), leaving **59 model JSONs**. No modified file turned out to be a
formatting-only change (checked: 0 semantically-equal pairs).

---

## 3. Patch inventory

Bundle: `26.2/src/main/resources/assetfetch/`
(`transforms.json` + `patches/**`). Generator:
`26.2/tools/assetfetch/gen_bundle.py`; independent replay check:
`26.2/tools/assetfetch/verify_bundle.py`.

```
26.2/tools/assetfetch/gen_bundle.py \
  --upstream   /home/user/assetfetch-extraction/upstream \
  --port       /home/user/assetfetch-extraction/port-26.2 \
  --bundle     26.2/src/main/resources/assetfetch \
  --added-out  26.2/src/main/resources/assets/minecolonies \
  --lang-out   26.2/src/main/resources/assets/minecolonies/lang/en_us.json \
  --meta       /home/user/assetfetch-extraction/meta
```

### The 65 shipped patched files

| kind | files | stored patch files | bytes |
| --- | ---: | ---: | ---: |
| composite flattening only — **transform rule**, no stored patch | 5 | 0 | 0 |
| composite flattening + RFC 6902 JSON Patch | 13 | 13 | — |
| RFC 6902 JSON Patch only | 41 | 41 | — |
| `diff -U0` unified diff (GUI XML) | 6 | 6 | 3 871 |
| **total** | **65** | **60** | 23 232 |

All 57 `.jsonpatch` files together are 19 361 bytes (that count includes the 3
derived-file patches from §5); `transforms.json` is 16 771 bytes. The whole
shipped runtime bundle is ~23 KB of patches plus the recipe.

`manual_en_us.json` is the 66th modified file and is deliberately **not** in the
bundle (rev. 2 does no install-time language patching).

The five flatten-only models — the ones the runtime recomputes rather than
patches — are:

```
models/block/blockhutcook.json      models/block/blockhutfarmer.json
models/block/blockhutenchanter.json models/block/blockhutflorist.json
models/block/blockhutminer.json
```

### The flattening rule

`26.2/tools/assetfetch/composite_flatten.py`, rule id
`neoforge-composite-flatten`, also stated verbatim in `transforms.json`:

> Drop `loader` and `children`; merge each child's `textures` into the root map
> in document order (a later child wins on a repeated key); append each child's
> `elements` to the root list; discard the child-only keys `render_type`,
> `parent`, `groups`.

18 of the 59 model JSONs are `neoforge:composite` upstream. 5 flatten exactly
onto the port's file; the other 13 need a small patch afterwards (a texture
remap, a `parent` swap). Because the rule runs *first*, those 13 patches address
the already-flat document and stay tiny. A 19th model, `models/item/spear.json`,
uses `neoforge:separate_transforms` and was replaced outright by the port with a
6-line vanilla model — its patch removes four keys and adds port-authored text.

### How much upstream text the bundle quotes

This was the design goal and it came out well:

- **JSON patches quote no upstream prose or geometry at all.** Every operation
  is a JSON Pointer (structural key names such as `/textures/12`,
  `/elements/64/faces/up/texture`) plus a *port-authored* value. The largest
  embedded value in the whole bundle is 90 bytes:
  `{"layer0": "minecolonies:item/spawn_egg", "layer1": "minecolonies:item/spawn_egg_overlay"}`.
  A handful of replacement values are plain vanilla Minecraft resource ids
  (e.g. `block/red_concrete`, `minecraft:block/block`), not LDTTeam content.
- **The 6 XML diffs quote 11 upstream lines in total** (`-U0`, so no context
  beyond the changed lines themselves): 6 in `gui/citizen/job.xml`, 2 in
  `gui/windowfield.xml`, 1 each in `gui/citizen/main.xml`,
  `gui/layouthuts/layoutfarmfields.xml`, `gui/townhall/layoutcitizens.xml`.
  `gui/windowbuildbuilding.xml` is a pure insertion and quotes **zero** upstream
  lines. Each quoted line is a single `<image .../>` element being given a
  `source=` / `visible=` attribute.

**Owner sign-off item:** those 11 XML lines and the JSON Pointer key names are
the only upstream-derived text in the shipped jar. The brief anticipated this
("flag to the owner for sign-off that even minimal patches quote fragments of
ARR files"). Consider it flagged; nothing further can be trimmed without giving
up the ability to reproduce the port's GUI edits.

### Verification

`verify_bundle.py` replays `transforms.json` against a clean copy of
`$OUT/upstream` and compares each result with `$OUT/port-26.2`:

```
verified 69 files (65 patched + 4 derived); 0 failure(s)
```

JSON results are compared as parsed documents (the port's hand formatting is not
reproducible and does not need to be); XML results are compared byte for byte
after running real `patch(1)`.

### Canonical serialisation — an obligation on WP1/WP3

A patched JSON document has no natural byte form, so `transforms.json` carries a
`canonicalJson` block: UTF-8, 2-space indent, `": "` between key and value,
document order preserved, non-ASCII emitted literally, one trailing newline, and
**numbers re-emitted from their source literal**. That last clause matters: a
Java implementation that reparses numbers as `double` will turn `16` into `16.0`
and `4.52` into a different literal, and every manifest hash will miss. WP3 must
use a number-literal-preserving reader (Gson's `LazilyParsedNumber` behaviour),
and WP1 must generate `manifest.json` by running the **same** patcher that the
runtime will run, not a second implementation.

Reference hashes of the patched outputs *as this generator serialises them*, for
the 65 + 4 files against the `325157eb` base, are in
`$OUT/meta/expected-patched-sha256.json`. They are a cross-check for WP1, not a
substitute for the real manifest — WP1 regenerates against the 1374 jar's bases.

---

## 4. The language split

Derived per key by diffing `lang/manual_en_us.json` in the two trees.

| measurement | value | brief expected |
| --- | ---: | ---: |
| upstream keys | **2767** | 2767 |
| port keys | **3118** | 3079 |
| keys the port added | **351** | 312 |
| keys the port rewrote | **6** | 2 |
| keys the port removed | **0** | 0 |
| **port-owned file** | **357 keys** | 314 |

Shipped as `26.2/src/main/resources/assets/minecolonies/lang/en_us.json`
(53 336 bytes). Full data incl. every old→new pair:
`$OUT/meta/lang-split.json`.

Incidental finding: both files contain the key `com.ldtteam.tag.tooltip.gate`
twice (2768 / 3119 raw pairs vs 2767 / 3118 unique). It is an upstream
duplicate, identical in both trees, and has no effect on the split.

### The rewritten values, old → new

**1. `com.minecolonies.coremod.gui.home.assigned`** — the `%d`→`%s` one the
brief mentions.

- old: `Assigned Citizens: %d/%d`
- new: `Assigned Citizens: %s/%s`

**2. `com.minecolonies.building.stable.desc`** — the port's Stable feature.

- old: `Stable cares for cavarly mounts`
- new: `The Stable trains horses into cavalry mounts and stables them.`

**3. `minecolonies.config.colonyloadstrictness.comment`**

- old: `This controls how many chunks are loaded with the "Chunk Load Colony" option. The higher this value, the fewer chunks will be loaded. (The innermost chunks will be loaded first.) 1 = load all claimed chunks.`
- new: `How many building claims have to overlap on a chunk before the "Chunk Load Colony" option keeps it loaded. The higher this value, the fewer chunks will be loaded. 1 keeps every chunk a building stands on or reaches; it still does not cover claimed ground with no building on it - for that use "Keep All Claimed Chunks Loaded".`

**4. `com.minecolonies.coremod.warehouse.full`**

- old: `The Warehouse is full, please upgrade it!`
- new: `The Warehouse at %1$s is full: %2$s of %3$s slots taken (%4$s%%), while the goods in them fill only %5$s%% of the space. Sorting would free about %6$s slots. Until then your couriers cannot store anything, and they stop delivering as well. Level the Warehouse up to add racks.`

**5. `com.minecolonies.coremod.warehouse.full.level5`**

- old: `The Warehouse is full, please pay an emerald block to upgrade the racks!`
- new: `The Warehouse at %1$s is full: %2$s of %3$s slots taken (%4$s%%), while the goods in them fill only %5$s%% of the space. Sorting would free about %6$s slots. Until then your couriers cannot store anything, and they stop delivering as well. You have %7$s rack upgrade(s) left, one emerald block each.`

**6. `com.minecolonies.coremod.warehouse.full.max`**

- old: `The Warehouse is full, please build another one and make some space!`
- new: `The Warehouse at %1$s is full: %2$s of %3$s slots taken (%4$s%%), while the goods in them fill only %5$s%% of the space. Sorting would free about %6$s slots. Until then your couriers cannot store anything, and they stop delivering as well. This Warehouse is fully upgraded: sort it, empty it, or build a second one.`

Five of the six are wholly new port text. **One is not:**
`Assigned Citizens: %s/%s` is upstream's own 24-character label with the format
specifier changed. It is the single piece of upstream *prose* in the shipped jar.
Flagged for the owner; the alternative (turning one key into an install-time
patch, reintroducing the lang patching rev. 2 just abolished) looks clearly
worse.

---

## 5. The 10 added files

Every one was checked against the **whole** upstream tree by content hash, not
just at its own path. Machine-readable: `$OUT/meta/added-files.json`.

| # | path | bytes | what it is | upstream counterpart | verdict |
| --- | --- | ---: | --- | --- | --- |
| 1 | `items/scepterclaim.json` | 98 | 26.2-format item definition pointing at `minecolonies:item/scepterclaim` | none — upstream (1.21.1) has no `items/` directory at all | **ship** |
| 2 | `models/item/fieldstick.json` | 90 | 4-line model: `item/handheld` + `minecraft:item/stick` | none | **ship** |
| 3 | `textures/block/transparent.png` | 75 | 16×16 RGBA, all 256 pixels `(0,0,0,0)` | none | **ship** |
| 4 | `textures/misc/rack_empty.png` | 75 | same file as #3, second path | none | **ship** |
| 5 | `textures/item/spawn_egg.png` | 125 | 16×16 RGBA placeholder, 72 opaque px in 3 greys | none | **ship** |
| 6 | `textures/item/spawn_egg_overlay.png` | 91 | 16×16 RGBA placeholder, 20 white px | none | **ship** |
| 7 | `models/item/scepterborder.json` | 566 | — | **byte-identical to upstream `models/item/scepterpermission.json`** | **do not ship — derive at install** |
| 8 | `models/item/scepterclaim.json` | 560 | — | that same upstream file with `layer0` → `minecolonies:item/sceptergold` | **do not ship — derive at install** |
| 9 | `models/item/scepterterritory.json` | 563 | — | that same upstream file with `layer0` → `minecolonies:item/pharaohscepter` | **do not ship — derive at install** |
| 10 | `models/item/scepterunclaim.json` | 561 | — | that same upstream file with `layer0` → `minecolonies:item/sceptersteel` | **do not ship — derive at install** |

Files 1–6 are committed under their original
`26.2/src/main/resources/assets/minecolonies/**` paths.

Files 7–10 are **not** committed anywhere. #7 is literally an upstream file under
a new name; #8–#10 are that file with one string changed. Shipping them would
put ARR bytes back in the jar. The brief covers this case — "if any turns out
derivative, move it to the install-time patch bundle instead" — so they are now a
`derivedFiles` section in `transforms.json`: `copyFrom`
`models/item/scepterpermission.json` (which the fetched pack already contains),
then a one-operation JSON patch for #8–#10 and no patch at all for #7. This
quotes zero upstream text and is verified by `verify_bundle.py` alongside the
65. The three target textures (`sceptergold`, `pharaohscepter`, `sceptersteel`)
all exist upstream, so the derived models resolve.

`derivedFiles` is deliberately a separate array from `files`, so the "65 patched
files" inventory stays exactly the 65 the brief specifies and stays auditable.

Two notes for the owner rather than blockers:

- **#5/#6 provenance.** Neither has any LDTTeam counterpart, which is the red
  line this project cares about, and both are trivial 16×16 placeholders — the
  brief's own example of a shippable added file. They exist because the port
  repointed the 19 spawn-egg models away from the NeoForge tinted-egg path. I
  could not compare them against *vanilla Minecraft's* spawn-egg silhouette (no
  client jar on this box, and running Gradle is out of scope), so "hand-drawn vs
  traced from Mojang's template" is unresolved. Low risk, recorded for honesty.
- **#1 is redundant with datagen.** `$OUT/generated/minecolonies/items/scepterclaim.json`
  is the same content (differing only by a trailing newline), so B2's datagen
  would emit it anyway. Shipping the manual copy preserves parity with the
  retired tree; deleting it later is safe.

---

## 6. Insurance (stays on this box — never committed, never uploaded)

Sound filename list (names only, no audio):
`$OUT/meta/citizen-sound-filenames.txt` — 35 entries, the full recursive listing
of `upstream/sounds/mob/citizen`: 9 voice directories (`child`, `female1`…`female4`,
`male1`…`male4`) plus a top-level `snore.ogg`.
Nothing ships it and nothing consumes it; per B3 it is insurance only.

Tarballs in `/home/user/assetfetch-extraction/insurance/` (checksums also in
`insurance/SHA256SUMS`):

| file | bytes | SHA-256 |
| --- | ---: | --- |
| `port-26.2.tar.gz` | 21 769 926 | `52cf9d13cb3cfd86d92a7a2ed1290512a2584b23289b04af421cde56caf6e2a2` |
| `port-1.21.1.tar.gz` | 21 755 179 | `5a835093d11dcfd33447b8e2f8148ef8453c1adc2cd4683ccdbb44733977fd6e` |
| `upstream.tar.gz` | 21 755 660 | `325407c98bde826c6305d9c9808f39622f947877ee3569b35fdff4e01c5afcd4` |
| `generated.tar.gz` | 2 188 038 | `1832f65d29171e405111cf91ea654cb2a5fafc5a776bf0dc3c03830c910c7e31` |

The four extracted trees themselves remain at `$OUT/{port-26.2,port-1.21.1,upstream,generated}`
for WP1 to measure the fetched jars against. **None of it — trees, tarballs, or
any file drawn from them beyond the minimal patches described above — may be
committed or uploaded anywhere.**

---

## Deviations

1. **Mechanical/hand-edited model split is 5/54, not the brief's 6/53.** Measured
   by applying the flattening rule to all 18 `neoforge:composite` models and
   comparing with the port's file: 5 match exactly, 13 need a residual patch. The
   13 residuals are all one- to five-line texture/`parent` edits (listed in
   `transforms.json`); there is no candidate for a sixth pure model. The shipped
   inventory is unaffected — still **65** files (5 rule-only + 54 JSON patches +
   6 XML diffs).
2. **The port-owned language file has 357 keys, not 314** (351 added + 6
   rewritten vs the expected 312 + 2). Upstream's side matches the brief exactly
   (2767 keys), so this is port-side drift between whenever the 312/2 figure was
   taken and the retired head `ea4243c8` — consistent with the Stable feature and
   the warehouse-full rework, both of which appear among the new keys. The brief's
   "one merely `%d`→`%s`" rewrite is present and identified.
   **This was not a stop-condition**, and the derivation method the brief
   specifies is what produced the number, so WP0 proceeded. B2 must repoint
   `DefaultLanguageProvider` at a **357**-key source.
3. **Four of the 10 added files are derivative and are not shipped** (§5). The
   brief expected owner confirmation that all 10 were port-authored; the
   measurement says 6 are. Handled by the brief's own fallback (move them to the
   patch bundle), so nothing is blocked, but the owner should know the added-file
   count in the jar is 6.
4. **`assets/minecolonies/LICENSE` exists in both trees** and is byte-identical;
   it is an upstream file that arrives with the fetched pack. It is not shipped,
   not patched, and not one of the 10 additions (§1).

## Held for owner sign-off

- The minimal-quote patch approach itself: 11 upstream XML lines + JSON Pointer
  key names in the shipped jar (§3).
- `Assigned Citizens: %s/%s` — upstream's label, one specifier changed, shipped
  in our `en_us.json` (§4).
- Provenance of `spawn_egg.png` / `spawn_egg_overlay.png` against *vanilla*
  Minecraft's spawn-egg template — unverifiable on this box (§5).
- Collection of the four insurance tarballs (§6); they stay on this box until
  the owner takes them.
