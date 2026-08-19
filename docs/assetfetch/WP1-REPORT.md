# WP1 — Phase 1 characterization & manifest: report

Executed 2026-08-19. Both upstream jars downloaded once each to
`/home/user/assetfetch-extraction/jars/` (`$OUT/jars`, never committed) and kept
there for later work packages. Scratch trees, extracted packs and the manifest
generator live under `$OUT`; the only committed outputs are
`26.2/src/main/resources/assetfetch/manifest.json` and this report.

**Headline results**

| question | answer |
| --- | --- |
| both jars match their pinned size/SHA-256 (and SHA-512 for 1374) | **yes, exactly** |
| baseline files present and byte-identical in the 1374 jar | **4821 of 4822**; the 1 absent is `lang/manual_en_us.json`, fully covered by the jar's merged `en_us.json` |
| baseline files *changed* in the 1374 jar | **0** |
| datagen products present (merged `en_us.json`, 97 `lang/*.json`, 3481 `entity_icon`, `sounds.json`, 35 citizen `.ogg`) | **all present** |
| patch bases drifted between `325157eb` and the 1374 jar | **0 of 69** (65 patched + 1 `copyFrom` base) — **no patch needed regeneration** |
| 1368 ↔ 1374 asset diff | **1 file**: `lang/uk_ua.json` (6 Ukrainian strings) — one manifest with an `alt` entry |
| manifest | **8474 files**, 1 `alt` entry, 1 611 863 bytes |
| patched-output hashes vs `$OUT/meta/expected-patched-sha256.json` | **67 of 69 equal**; 2 differ by JSON **key order only** (explained in §8) |

Two findings need a decision that is **not WP1's to make** and are flagged in §11:
the port's 272 `huscarl`/`marksman` sound events do not exist in the fetched
`sounds.json`, and the port's own `entity_icon` datagen output is not
byte-identical to upstream's (border pixels only).

---

## 1. Downloads and integrity verification

Downloaded with `curl` through the pre-configured proxy (`--cacert
/root/.ccr/ca-bundle.crt`), HTTP 200 on both, no redirects, no retries needed.

| | source 1 — build 1374 | source 2 — build 1368 |
| --- | --- | --- |
| URL | `…/1.1.1374-1.21.1-snapshot/minecolonies-1.1.1374-1.21.1-snapshot.jar` | `…/1.1.1368-1.21.1/minecolonies-1.1.1368-1.21.1.jar` |
| bytes received | **78 071 143** | **77 945 293** |
| pinned size | 78 071 143 ✅ | 77 945 293 ✅ |
| SHA-256 measured | `9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa` | `c3a2542aaced85aabfc58b38415b70e6b095a16787056e07880fc94320f09a9b` |
| pinned SHA-256 | identical ✅ | identical ✅ |
| SHA-512 measured | `e410260c0aa617b8c3c0c747c738d15ebe26b631d5b5ebb1e3b04c76771c794e093c61e1c90825a102176840d81ebefc38008077acec2f0ccbbd2b305ed0fa83` | (not pinned) |
| pinned SHA-512 | identical ✅ | — |
| server `X-Checksum-Sha256` | matches the download | matches the download |
| `Last-Modified` | Tue, 11 Aug 2026 06:02:44 GMT | Sun, 02 Aug 2026 16:08:04 GMT |
| transfer time | 2.1 s | 1.7 s |

Nothing to stop and report: every pinned value in BRIEF.md §3 is confirmed on the
real bytes. Both jars remain at `$OUT/jars/mc-1374.jar` and `$OUT/jars/mc-1368.jar`.

## 2. Internal layout of the 1374 jar

An ordinary **NeoForge mod jar** — not a nested archive, no `pack.mcmeta` at the
root (so C8 really does have to write one).

```
24028 zip entries = 22674 files + 1354 directory entries, all DEFLATE
  blueprints/   9881 files      com/           2503 files (classes)
  assets/       8472 files      data/          1814 files
  META-INF/        3 files      minecolonies.png  1 file
```

`assets/` holds exactly two namespaces: `assets/minecolonies/` (**8470 files** —
the fetch target) and `assets/minecraft/` (2 files: `atlases/blocks.json` 947 B and
`textures/gui/container/creative_inventory/tab_minecolonies_background.png`
12 287 B — the latter byte-identical to the one restored on `main`, the former
*not* the same file as the port's 5877-byte version; neither is installed by us).
The only nested archive anywhere is `blueprints/minecolonies/template.zip`, which
is blueprint data, not assets. Extraction with the zip-slip guard rejected 0
entries.

`assets/minecolonies/**` inventory as extracted (81 605 094 bytes uncompressed):

| subtree | files | bytes |
| --- | ---: | ---: |
| `textures/` | 7655 | 29 220 934 |
| `lang/` | 97 | 39 613 235 |
| `models/` | 454 | 1 878 967 |
| `gui/` | 94 | 144 867 |
| `blockstates/` | 95 | 54 511 |
| `sounds/` | 68 | 7 150 708 |
| `colony/`, `atlases/`, `particles/`, `shaders/` | 5 | 28 529 |
| root (`sounds.json`, `LICENSE`) | 2 | 3 513 343 |

The 1368 jar has the identical shape (24003 entries; 25 fewer blueprints; the same
8470 asset files).

## 3. Coverage of the 4822-file baseline (`$OUT/upstream`, tree `e7aa68bf`)

Measured by SHA-256 over every file of both trees.

| | count |
| --- | ---: |
| baseline files present in the 1374 jar | **4821** |
| …of those, byte-identical to the baseline | **4821 (100 %)** |
| baseline files **missing** from the jar | **1** |
| baseline files present but **changed** | **0** |

The single missing path is `lang/manual_en_us.json` — upstream's *source* language
file, an input to their datagen that the built jar does not carry. Exactly the
1368 result, and covered by §4.1. Full per-file hash tables:
`$OUT/meta/wp1-upstream-sha.json`, `$OUT/meta/wp1-jar1374-sha.json`,
`$OUT/meta/wp1-jar1368-sha.json`.

The jar carries **3649 files beyond the baseline** (4821 + 3649 = 8470):
3481 `textures/entity_icon/**`, 97 `lang/*.json`, 68 datagen `models/item/*.json`
(the food items: `apple_pie`, `borscht`, `ramen`, …), 2 `colony/stories/*.json`
(`abandonedcolonies`, `supplies`) and `sounds.json`.

## 4. The datagen products

### 4.1 Merged `lang/en_us.json` — **complete**

- jar: **3898 keys**, 402 637 bytes.
- upstream `manual_en_us.json` (baseline): **2767 keys**.
- keys of `manual_en_us.json` absent from the jar's `en_us.json`: **0**.
- keys whose value differs: **0**.
- the remaining **1131** keys are datagen-produced (research names/subtitles,
  quests, …).

Cross-check against WP0's language split: the port-owned 357-key file
(`$OUT/meta/port_en_us.json`) intersects the jar's `en_us.json` in exactly **6**
keys — precisely WP0's 6 rewritten values — and contributes **351** new ones. The
split is consistent with the fetched file, and the Position.BOTTOM override story
holds: our jar wins on those 357 keys, the fetched file supplies the other 3541.

### 4.2 The `lang/` directory — 97 files, all present

`lang/` contains 97 files: `en_us.json`, **94 locale files** (`af_za` … `zh_tw`),
plus `entries.json` and `tag.item.json`, which are POEditor/tag data rather than
language files. The brief's "97 non-English language files" is therefore slightly
off in wording: 97 *files*, 96 of them non-`en_us`, **94 of them actual locales**.
No file in `lang/` exists in the baseline tree, i.e. all 97 arrive with the build.

### 4.3 `textures/entity_icon/**` — 3481 present, **but not byte-identical to `$OUT/generated`**

- paths: **3481 in the jar, 3481 in `$OUT/generated`, set-identical** (0 either side).
- hashes: **all 3481 differ.** Not a spot check — every file was compared.

Characterized by decoding the PNGs (16×16, 8-bit, colour type 2, in both trees):

- interior pixels (x,y ∈ 1..14) are **identical in every icon sampled** (60 icons,
  random seed 7);
- differences are confined to the **one-pixel border** (row 0, row 15, column 0,
  column 15) in 60 of 60;
- on the border the jar's channel value is ≈ **0.745 ×** the port's, max observed
  channel delta 33.

Cause, from `26.2/src/main/java/.../DefaultEntityIconProvider.java`: the port's
26.2 rewrite replaced the removed `NativeImage#blendPixel(x, y, 0x80000000)` with
`r >>= 1; g >>= 1; b >>= 1` on border pixels, whose Javadoc claims the icons "stay
byte-identical to 1.21.1". They do not — halving is not what upstream's blend
computes (empirically ≈ 0.745 of the halved value). The composite itself
(`resizeSubRectTo`) is untouched and *is* identical.

**Impact: none on this design.** B1 disables the provider and the icons are
installed verbatim from the jar, so the manifest hashes come from the jar and the
port's variant never ships. Recorded because the brief asked for the comparison
and because the Javadoc claim is wrong.

### 4.4 `sounds.json` — present, and a **272-event gap**

- jar `sounds.json`: 3 513 324 bytes, **7002 sound events**.
- `$OUT/generated/minecolonies/sounds.json`: 3 650 144 bytes, **7274 events**.
- events present in the jar but absent from the port's datagen: **0**.
- events with a differing definition: **0** — the 7002 shared events are
  semantically identical.
- events the port generates that the jar does **not** have: **272** — exactly two
  job families, `citizen.huscarl.*` (136) and `citizen.marksman.*` (136).

Every one of the 272 references only `.ogg` files that **do** exist in the fetched
pack (32 distinct sound names, 0 missing). So the audio is there; only the event
declarations are not. See §11 for what this means for B3.

### 4.5 `sounds/mob/citizen/**` — present and identical

35 `.ogg` files in 9 voice directories plus `snore.ogg`; the recursive listing is
**identical to `$OUT/meta/citizen-sound-filenames.txt`** (diff clean) and all 35
are byte-identical to the baseline (they are part of the 4821).

## 5. Patch bases: zero drift

For all **65** patched paths in `transforms.json` plus the single `derivedFiles`
`copyFrom` base `models/item/scepterpermission.json`:

- absent from the 1374 jar: **0**;
- SHA-256 different from the `325157eb` base: **0** (and **0** in the 1368 jar too).

**No patch was regenerated.** The committed patch bundle from WP0 applies to the
1374 jar unchanged. Independent replay confirms it — `verify_bundle.py` run with
`--upstream` pointed at the tree extracted from each jar:

```
jar 1374: verified 69 files (65 patched + 4 derived); 0 failure(s)
jar 1368: verified 69 files (65 patched + 4 derived); 0 failure(s)
```

The WP0 inventory shape is reconfirmed on the real jar bases: **5** rule-only,
**13** rule + JSON patch, **41** JSON-patch-only, **6** XML unified diffs = 65.

## 6. The 1368 ↔ 1374 asset diff — one file

Comparing `assets/minecolonies/**` between the two jars (8470 files each):

| | count |
| --- | ---: |
| files only in 1374 | **0** |
| files only in 1368 | **0** |
| files with differing content | **1** |

The one file is `lang/uk_ua.json` (1374: 549 477 B, 1368: 548 759 B). Both have
3898 keys, no key added or removed; **6 values changed**, all in the
`minecolonies.quests.general.adayinthefield*` group — a Ukrainian translation
update between the two builds.

**Decision (as the brief requires one): ONE manifest with per-path alternates.**
The diff is as small as it can get without being empty, so `alt["maven-1368"]`
holds exactly one entry and there is no second manifest. This is also the format
the orchestrator fixed, and the measurement supports it rather than straining it.

## 7. `manifest.json`

Written to `26.2/src/main/resources/assetfetch/manifest.json`, 1 611 863 bytes,
in the orchestrator's fixed format (`version`, `primarySource`, `sources`,
`files`, `alt`), keys sorted, 2-space indent, lowercase hex, integer sizes.

- `files`: **8474** entries = the 8470 files extracted from the primary jar (with
  the 65 patched outputs replacing their fetched bytes in place) **+ 4** derived
  files (`models/item/scepter{border,claim,territory,unclaim}.json`).
- `pack.mcmeta` is **not** in the manifest (C8 writes it at install time).
- the 6 port-authored added files are **not** in the manifest either — they ship
  inside our jar and are not part of the fetched pack.
- installed pack size: **82 435 042 bytes** from 1374, 82 434 324 bytes from 1368.
- `alt["maven-1368"]`: **1** entry, `assets/minecolonies/lang/uk_ua.json` →
  `7cc5b4c6…`, 548 759 B. No `null` entries: nothing in the pack is absent when
  installing from 1368.

**How the hashes were produced (not hand-computed).** A generator at
`$OUT/tools/build_manifest.py` *imports* the repo's own tools —
`composite_flatten.RULES`, `jsonpatch.apply_patch`, `gen_bundle.canonical_dumps`
— and GNU `patch(1)` for the XML steps, materialises the complete pack from the
tree extracted out of the real jar, and hashes what lands on disk. It is the same
code path `verify_bundle.py` replays, so `transforms.json`'s `canonicalJson`
block remains the single contract. The generator is scratch tooling and is not
committed (per WP1's committed-output rule); say the word and it goes into
`26.2/tools/assetfetch/` where it belongs for reproducibility.

**Self-check.** Both materialised packs were verified back against the effective
manifest (`files` overlaid with `alt[S]`), which is exactly what C9 will do:

```
maven-1374: manifest 8474 files, on disk 8474; missing=0 extra=0 hash-mismatch=0
maven-1368: manifest 8474 files, on disk 8474; missing=0 extra=0 hash-mismatch=0
```

## 8. Cross-check against `$OUT/meta/expected-patched-sha256.json`

69 reference hashes, 69 produced. **67 identical, 2 different**:

| path | WP0 reference | WP1 (patcher on the 1374 base) |
| --- | --- | --- |
| `models/block/blockhutcook.json` | `fd67bbd1…` | `14306e25…` |
| `models/block/blockhutfletcher.json` | `1a8a41fc…` | `bd5e59db…` |

**Explanation — key order, nothing else.** Both differing files were compared
parsed: the documents are equal, and a scalar-by-scalar walk of all 69 outputs
found **0** differences in type or value (so no `16` → `16.0` number drift
anywhere). The two files serialise differently because WP0's reference hash was
computed from *the port's hand-written file* (`canonical_dumps(port_doc)`), while
the runtime — and therefore WP1 — computes from *the patch result*, whose key
order is "document order after patching" as `canonicalJson` specifies. In
`blockhutcook.json` the port had `display` before `textures`/`elements`; the
flatten rule emits it after. In `blockhutfletcher.json` the port had `textures`
near the end; the rule hoists it. Order is irrelevant to Minecraft's model loader
and both files remain semantically the port's.

**The manifest is right and the WP0 reference is stale for those two paths.** The
manifest is what the runtime must match, and it was produced by running the
patcher, exactly as instructed. WP0's `expected-patched-sha256.json` is not
regenerated here (it is `$OUT` scratch, not a shipped artifact); the authoritative
per-file hashes live in `manifest.json`. The 6 XML outputs matched their WP0
reference byte for byte (LF endings, sizes identical to the port's files), as did
the other 61 JSON outputs.

## 9. Notes the Java runtime (WP3) must honour

Measured on the actual 69 outputs, to make WP0's canonical-JSON warning concrete:

- the 63 JSON outputs contain **39 096 integer literals** and 9576 float literals.
  A reader that parses every number as `double` corrupts *every one of the 63
  files* (`16` → `16.0`), and every manifest hash misses. Gson's
  `LazilyParsedNumber` behaviour (or any literal-preserving reader) is mandatory.
- of the **48 740** numeric literals across the patch-base files, **0** are in a
  non-shortest form (no `3.50`, no exponents, max 5 decimals, magnitudes 0.01 …
  hundreds). So literal preservation and shortest-round-trip re-emission agree on
  this corpus; the only real hazard is the int/float one above.
- **0** of the 69 outputs contain a non-ASCII byte, so `escapeNonAscii:false`
  costs nothing here but must still be honoured for future bases.
- the 6 XML outputs are LF-only and must come out byte-identical to what GNU
  `patch -p0` produces from a `diff -U0` patch; that is what the manifest hashes.
- the whole patched/derived set is 1 560 566 bytes of the 82 MB pack — everything
  else is copied verbatim and needs no serialisation contract at all.

## 10. Reproduction

```bash
OUT=/home/user/assetfetch-extraction
# 1. verify the jars
sha256sum $OUT/jars/mc-1374.jar $OUT/jars/mc-1368.jar
sha512sum $OUT/jars/mc-1374.jar
# 2. extract assets/minecolonies/** (zip-slip guarded) -> $OUT/jar1374, $OUT/jar1368
# 3. replay the bundle against the extracted trees
python3 26.2/tools/assetfetch/verify_bundle.py \
  --upstream $OUT/jar1374 --port $OUT/port-26.2 \
  --bundle 26.2/src/main/resources/assetfetch
# 4. materialise both packs and emit the manifest
python3 $OUT/tools/build_manifest.py \
  --primary $OUT/jar1374 --alt $OUT/jar1368 \
  --bundle 26.2/src/main/resources/assetfetch \
  --pack-out $OUT/packs --meta $OUT/meta \
  --out 26.2/src/main/resources/assetfetch/manifest.json
```

## 11. Unexpected findings and open items

1. **The 272 missing sound events (`huscarl`, `marksman`) — needs a decision.**
   B3 says our jar must ship **no** `sounds.json` so the fetched one is not
   masked, and the fetched one has 7002 events. The port's two added job
   families are declared only in the port's own generated `sounds.json`, so after
   this design lands, huscarl and marksman citizens will be **silent** (missing
   sound event → warn + silence, the documented degradation). All 32 `.ogg` files
   they reference are present in the fetched pack, so the fix is a declaration
   problem, not an asset problem. Options, none of them WP1's to pick:
   (a) accept the silence and note it in the release notes;
   (b) add `sounds.json` to the transform bundle as a JSON patch that appends the
   272 events (quotes no upstream prose — only sound *file names* and the
   `category`/`stream` scaffolding — but adds ≈137 KB to the bundle, the exact
   size difference between the two `sounds.json` files, and makes
   `sounds.json`'s manifest hash a patched-output hash);
   (c) ship a *second*, minimal `sounds.json`-like overlay — not possible, since
   `sounds.json` is not per-key mergeable across packs the way lang files are.
   If (b) is chosen the manifest must be regenerated; everything else in this
   report is unaffected.
2. **`entity_icon` byte-mismatch with the port's datagen** (§4.3). Harmless here,
   but it falsifies the Javadoc claim in `DefaultEntityIconProvider` that the
   rewrite kept icons byte-identical to 1.21.1. Since B1 disables the provider,
   the cheapest correct action is to fix that comment when B1 lands.
3. **Two stale WP0 reference hashes** (§8) — key order, semantically identical.
   No action beyond knowing `manifest.json` is authoritative.
4. **`lang/` is 97 files but only 94 locales** (§4.2) — the brief's "97 language
   files" counts `en_us.json`, `entries.json` and `tag.item.json`. Cosmetic.
5. **`assets/minecraft/atlases/blocks.json` differs between the jar (947 B) and
   the port's restored file (5877 B).** We do not install `assets/minecraft`, so
   this is out of scope for the fetch — recorded only so nobody later "fixes" the
   restored file to match the jar.
6. **`lang/manual_en_us.json` is not in the jar** and therefore not in the
   manifest. Nothing at runtime needs it; B2 reads the port-owned 357-key file
   from our own resources.

## 12. What WP1 did NOT verify

- **Nothing was built or run.** No Gradle, no datagen, no client, no server —
  WP1 needed none of it. The manifest's correctness rests on the Python reference
  patcher; whether the **Java** implementation reproduces these bytes is WP3's
  acceptance criterion, not something this report can assert.
- **The 1368 jar's SHA-512** is not pinned in the brief and was not compared
  against a sidecar (its size, SHA-256 and the server's `X-Checksum-Sha256` all
  match).
- **No `.sha256`/`.sha512` sidecar files were downloaded**; integrity was checked
  against BRIEF.md's pinned values plus the server's checksum headers on HEAD.
- **Icon comparison depth**: all 3481 icons were hash-compared (all differ), but
  the pixel-level "border only" characterization is from a 60-icon random sample,
  not all 3481.
- **`sounds.json` events were compared as parsed documents**, not for
  `.ogg`-file existence beyond the 272 extra events (the shared 7002 are
  upstream's own and the sound files are the 4821 byte-identical set).
- **Whether option (b) in §11.1 is acceptable licence-wise** — the 272 events
  would embed upstream sound *paths*, which is the same class of quoting the
  owner already has under review for the XML diffs.
