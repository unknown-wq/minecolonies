# IMPLEMENTATION BRIEF — Runtime Asset Fetch for MineColonies Fabric 26.2
## Revision 2 (jar source) — supersedes the source-archive brief in full

You are implementing a runtime asset downloader for an unofficial Fabric port of
MineColonies. You start with zero context; everything you need is in this document.
Where this document says "verified", it was checked against real sources and you may
build on it. Where it says "you must measure/verify", do not proceed on assumption.

**What changed in rev. 2 (owner's decision, 2026-08-19):** the download source is now a
**built jar from LDTTeam's own Maven**, not the GitHub source archive. A built jar
carries the datagen products the source archive lacked, so the former named tasks C4
(merged `en_us.json`), C5 (3481 citizen face icons), C6 (97 POEditor language files)
and C7 (`sounds.json`) are **gone as implementation work** — they arrive in the
download. B1 shrinks to "disable the provider", B3 **reverses direction** (our jar must
NOT ship a `sounds.json`), and verification becomes strict (Maven artifacts are
immutable files with server-published checksums; the old "zip hash is advisory" caveat
is dead). The source chain is four entries, below. Everything else — Phase 0 urgency,
the patch bundle, pack injection, consent UX, crash guards, escalation — stands.

---

## 1. Starting state

- **Repository you work in:** `/workspace/minecolonies-fabric` (GitHub:
  `unknown-wq/minecolonies-fabric`), branch `main`, head `49eb26f7`. Active project
  dir: `26.2/`. There is also a `1.21.1/` upstream snapshot dir; you will normally not
  touch it.
- **Licensing split:** all Java code is GPLv3 and ships. Everything that lived under
  `26.2/src/main/resources/assets/minecolonies/` was All Rights Reserved (LDTTeam has
  withdrawn permission) and has **already been removed** from this repository and its
  history, along with `26.2/src/main/generated/assets/`, the same tree in `1.21.1/`,
  and built jars. The removal is done; do not re-do it, and **never re-add upstream
  content from `assets/minecolonies/` to the repo or the jar** — that is the ARR tree.
  (Port-authored files under that path are a different matter; see Phase 0 and B5.)
- **Already restored — done, committed and pushed to `main` (commit `49eb26f7`), not
  tasks for you:** the blueprints and the two GPL-side `assets/minecraft` files, which
  the original sweep had removed alongside the ARR tree. Details in Phase 6; the owner
  has accepted the GPLv3 determination for the blueprints.
- Verified: `/workspace/minecolonies-fabric/26.2/src/main/resources/` now contains
  `data/`, `blueprints/` (9996 files, tree `4cd26b61ef38a671c0fef8bf551fde85fcc2b5b5`),
  `assets/minecraft/` holding exactly the two restored files —
  `atlases/blocks.json` (5877 bytes) and
  `textures/gui/container/creative_inventory/tab_minecolonies_background.png`
  (12287 bytes), the only two files that ever lived under `assets/` outside
  `assets/minecolonies/` — plus `fabric.mod.json`, `minecolonies.accesswidener` and
  two PNG logos. There is **no `assets/minecolonies/`**; that is the tree your work
  fetches at runtime.
- `data/minecolonies/structure/` (4.4 MB worldgen NBT, verified byte-identical to
  upstream) is outside the ARR directory and keeps shipping. Leave it alone.
- **Environment:** Fabric loader 0.19.3, fabric-api 0.154.2+26.2, Java 25, Gradle 9.6.1
  + Loom. Vanilla 26.2 sources readable at `/opt/mc-src`. BlockUI sources at
  `/workspace/blockui`. All Gradle work goes through `/home/user/mc-build.sh` (see
  acceptance criteria). Outbound HTTPS goes through a pre-configured proxy; CA bundle
  `/root/.ccr/ca-bundle.crt`. Verified 2026-08-19: `ldtteam.jfrog.io` is reachable
  anonymously through this proxy (directory listing, HEAD and sidecar downloads all
  succeeded). GitHub archive downloads for `ldtteam/*` are NOT reachable from this
  session (403 from the session proxy — a session-scope restriction, irrelevant to
  players and now also irrelevant to the design).
- **The old repository and working copy still exist but are going away** — see Phase 0.
  This is why Phase 0 is first and non-negotiable. Verified 2026-08-19: the old working
  copy `/home/user/minecolonies` is on branch `claude/fisherman-boat` and still
  contains commit `ea4243c8` and tree `e7aa68bf…` (both `cat-file`-checked).

## 2. Goal

A player downloads only our jar. On first client start they get an in-game
confirmation; on consent the mod downloads the original assets — as LDTTeam's own
built jar, from LDTTeam's own public Maven server — verifies them, installs the
`assets/minecolonies` subtree into a local cache directory, injects that directory as
a *required, fixed-position* resource pack, and reloads resources. No manual resource
pack install. The server needs none of this (verified: everything under `assets/` is
client-only; the server sends translation keys).

We never redistribute the ARR files: not in the jar, not in the repo, not on any
public mirror. The player's machine fetches them from LDTTeam's server — or from a
copy the player already has — and they never travel through this project.

## 3. The source chain (owner's decision — implement exactly this)

One pluggable source layer, four entries, tried in order. Every entry yields the same
thing — an upstream **built jar** — so there is exactly ONE pipeline downstream of the
fetch: extract `assets/minecolonies/**` → patch → verify. No per-source special cases
after the bytes are on disk.

### Source 1 — primary: Maven jar, build 1374 (same build as the port's chosen tag)

```
https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/1.1.1374-1.21.1-snapshot/minecolonies-1.1.1374-1.21.1-snapshot.jar
```

Verified 2026-08-19 by HEAD + checksum sidecar (anonymous access, no credentials):

- size **78,071,143 bytes**, Last-Modified 2026-08-11;
- SHA-256 `9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa`
  (server's `X-Checksum-Sha256` header);
- SHA-512 (from the `.jar.sha512` sidecar)
  `e410260c0aa617b8c3c0c747c738d15ebe26b631d5b5ebb1e3b04c76771c794e093c61e1c90825a102176840d81ebefc38008077acec2f0ccbbd2b305ed0fa83`.

Being a *built* jar it is expected to contain the datagen products — the merged
`lang/en_us.json`, the 3481 `textures/entity_icon/**` icons (~20 MB), the 97 POEditor
language files, and `sounds.json`. **Expected, not yet verified for this build** —
the measured facts below are from the 1368 jar; Phase 1 measures 1374.

### Source 2 — fallback: Maven jar, build 1368 (release, previously measured)

```
https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/1.1.1368-1.21.1/minecolonies-1.1.1368-1.21.1.jar
```

- size **77,945,293 bytes** (HEAD-verified 2026-08-19), SHA-256
  `c3a2542aaced85aabfc58b38415b70e6b095a16787056e07880fc94320f09a9b` (matches the
  earlier full-download measurement — this is the exact jar examined before);
- measured earlier on the full download: 4821 of 4822 baseline files byte-identical to
  upstream `325157eb`; the one exception (`manual_en_us.json`) fully covered by the
  jar's merged `en_us.json` (2767/2767 keys, identical values); contains all 3481
  entity icons, all 97 language files, `sounds.json`.
- It is a *release* directory entry (not `-snapshot`), which is the strongest
  durability we can get from their Maven.

### Source 3 — owner-hosted HTTP slot (SHIPPED DISABLED; build it now, leave it off)

A single configurable URL slot that, when non-empty, is tried after sources 1–2. The
owner will stand up a plain-HTTP host if LDTTeam's Maven dies or blocks; enabling must
be **trivial and fast** — substitute an IP/host, nothing else:

- In code: one constant, e.g. `OWNER_HOST_URL = ""` (empty ⇒ the source is skipped
  silently). Setting it to e.g. `http://203.0.113.7/minecolonies-1.1.1374-1.21.1-snapshot.jar`
  and rebuilding is the fast path.
- Additionally honor an override in the mod's local state/config file (same
  `state.json` from C1, key `customSourceUrl`), so the owner can hand players a URL
  without shipping a rebuild. Config override wins over the constant.
- **Plain `http://` must work** (no TLS requirement): integrity does not come from the
  transport — the downloaded jar must hash to one of the known whole-jar SHA-256s
  (source 1 or 2 above), and every installed file is verified against the per-file
  manifest regardless. A jar matching neither known hash is rejected before
  extraction, with the hash shown in the error.
- This slot is the operational form of the escalation rule (see below): the owner
  hosting a copy for their own project's users is the owner's decision and risk,
  taken knowingly. **The repository and the jar still never contain the assets**, and
  nobody but the owner ever stands up such a host.

### Source 4 — player-supplied local jar (manual escape hatch)

A file picker where the player points at an upstream MineColonies 1.21.1 jar they
downloaded themselves (e.g. from CurseForge), running through the identical
extract/patch/verify pipeline. Identify the file by whole-jar SHA-256 against the
known list (1374, 1368); on an unknown hash, still attempt extraction and per-file
manifest verification — if it verifies, accept it (CurseForge may serve a
byte-identical build under another name); if any file mismatches, reject with a
message naming the supported upstream versions. This path survives LDTTeam blocking
or removing everything, and requires hosting by nobody.

Mention the CurseForge route in the consent screen's help text as the manual escape
hatch for users who cannot download in-game.

### Rejected alternative (recorded so nobody re-litigates it)

The GitHub source archive of tag `v1.21.1-1.1.1374-snapshot` (rev. 1's primary) is
dropped from v1: it lacks the three datagen products (would force install-time lang
merging, install-time icon generation with byte-identity risk, and dropping all 97
translations for v1), its zip-level hash is unstable (GitHub generates tag archives on
the fly), and its size is unknown (it contains the whole upstream repo). The pluggable
source layer keeps the option open if it is ever needed; do not implement it now.

### Hosting the upstream artifact publicly is not an option

Putting LDTTeam's jar on a public mirror this project points users at by default is
redistribution of exactly the files we removed from our jar. The architecture works
because the bytes travel from LDTTeam's own public server — or from a copy the player
already has, or from the owner's own explicitly-enabled host — straight to the player,
and never through this repository. Sources 3 and 4 are the clean answers to a dead
source; a public mirror is not.

---

## PHASE 0 — ONE-TIME EXTRACTION. DO THIS BEFORE ANYTHING ELSE.

The patch set, hash manifest, and port-authored files are computed by diffing the
port's former ARR tree against upstream's. **Neither tree exists in the new
repository.** They survive in exactly two places, both scheduled to disappear:

1. The old repository `unknown-wq/minecolonies`, branch
   `claude/minecolonies-version-port-4w3x2m`, head `ea4243c8` — the owner is retiring
   it.
2. The old working copy on disk at `/home/user/minecolonies` (currently checked out on
   a *different* branch, `claude/fisherman-boat`, so extract from the commit, not the
   working tree). Verified present (re-checked 2026-08-19) and containing all needed
   objects:
   - commit `ea4243c8f50bb0317554fa82bf813adcec5d4a92` (the port head to extract from),
   - upstream commit `325157eba3d971d75502cb58107a176db883c9be` (branch `version/1.21`,
     the documented asset baseline; remote `upstream` = `github.com/ldtteam/minecolonies`),
   - upstream asset tree `e7aa68bf8459544bc940af24de22bb0fff30bc55`,
   - structure tree `297c8329…` (already shipping; informational).

Once the old repo is deleted and that working copy is reclaimed, reconstructing the
delta means re-deriving the port's 76-file hand-edit history from nothing. Extract now.

Work in a scratch directory outside both repos, e.g. `~/assetfetch-extraction/`:

```bash
OLD=/home/user/minecolonies
OUT=~/assetfetch-extraction
mkdir -p $OUT/{port-26.2,port-1.21.1,upstream,generated,meta}

# 1. Port's 26.2 ARR tree at the retired head (expected ≈ 4832 files + LICENSE)
git -C $OLD archive ea4243c8 26.2/src/main/resources/assets/minecolonies \
  | tar -x --strip-components=5 -C $OUT/port-26.2

# 2. Upstream baseline asset tree (expected 4822 files) — tree id works directly
git -C $OLD archive e7aa68bf8459544bc940af24de22bb0fff30bc55 | tar -x -C $OUT/upstream

# 3. Port's 1.21.1 snapshot ARR tree (insurance)
git -C $OLD archive ea4243c8 1.21.1/src/main/resources/assets/minecolonies \
  | tar -x --strip-components=5 -C $OUT/port-1.21.1

# 4. Datagen outputs at the retired head (3792 files incl. 3481 entity_icon; also
#    sounds.json, generated lang/, items/, colony/stories/) — INSURANCE + VERIFICATION
#    REFERENCE ONLY in rev. 2 (nothing is generated at install time any more; this
#    tree lets Phase 1 cross-check the fetched jar's datagen products)
git -C $OLD archive ea4243c8 26.2/src/main/generated/assets \
  | tar -x --strip-components=3 -C $OUT/generated

# (The blueprints and the two assets/minecraft files need no extraction — already
#  restored on main, commit 49eb26f7; see the starting state.)
```

Then compute, with a small script (commit the script, it is yours/GPL):

- **The delta:** `diff -rq $OUT/upstream $OUT/port-26.2`. Expected, from the prior
  verified investigation: **10 added, 66 modified, 0 deleted** (4832 = 4822 + 10).
  If your numbers differ, stop and reconcile before continuing.
- **The patch set** for the modified files, of which **65 ship** (the 66th is
  `manual_en_us.json`, handled below and NOT an install-time patch in rev. 2).
  Composition (verified earlier): 6 model files are a purely mechanical
  NeoForge-composite flattening (recomputable — prefer to encode as a *transform
  rule*, not a stored patch); 53 models carry hand edits (texture-path remaps, parent
  swaps, display transforms); 6 are GUI XML attribute/element edits. Store patches as
  **minimal-context structured edits** — RFC 6902 JSON Patch for `.json`, `-U0`
  unified diff for the XML — so the shipped patch bundle quotes as little ARR text as
  possible. Flag to the owner for sign-off that even minimal patches quote fragments
  of ARR files; this is the approach the prior design settled on, but the owner gets
  to see it.
- **The lang split — rev. 2 version.** `manual_en_us.json` in the port = upstream's
  2767 keys + the port's 312 added keys + 2 rewritten values (one merely `%d`→`%s`).
  The 312 added keys AND the 2 rewritten values together become one jar-shipped,
  port-owned `assets/minecolonies/lang/en_us.json` (314 keys total). Rationale:
  language files merge per key across packs (verified), and the mod jar sits ABOVE
  the fetched pack (Position.BOTTOM, Phase 4), so the jar's 2 rewritten values
  override the fetched `en_us.json` per key. **No install-time lang patching remains
  at all**; the fetched jar's merged `en_us.json` is installed verbatim.
- **The 10 added files** (6 JSONs + 4 placeholder PNGs of 75–125 bytes): confirm with
  the owner they are port-authored from scratch; if so they ship **in the jar** under
  `assets/minecolonies/…` (no upstream conflict exists for them). If any turns out
  derivative, move it to the install-time patch bundle instead.
- **The citizen sound filename list**: `ls $OUT/upstream/sounds/mob/citizen` (names
  only, no content). In rev. 2 this is **insurance only** — nothing ships it and
  nothing consumes it at build time (see B3). Keep it in `meta/`, cost is one `ls`.
- **Insurance tarballs** of `port-26.2`, `port-1.21.1`, `upstream`, `generated`:
  hand to the owner for private offline storage. **Do not commit them anywhere.**

Committed outputs of Phase 0 go into the new repo (all port-authored/derived-minimal):
suggested layout `26.2/src/main/resources/assetfetch/` for the runtime bundle
(`patches/`, `transforms.json`, later `manifest.json`) and `26.2/tools/assetfetch/`
for the generator scripts. The port-owned `lang/en_us.json` goes under
`26.2/src/main/resources/assets/minecolonies/lang/` (or stays a datagen product of
B2 — implementer's choice, but the 314 keys are the port's and ship in the jar).

The hash **manifest** is finalized in Phase 1 (it must describe what the 1374 jar
actually contains, not what the `325157eb` baseline contained).

---

## PHASE 1 — Characterize the 1374 jar (measure, don't assume)

Download the source-1 jar **once, manually** (curl through the pre-configured proxy;
CA bundle `/root/.ccr/ca-bundle.crt`; `ldtteam.jfrog.io` verified reachable from this
environment). Record and report:

- total bytes and SHA-256/SHA-512 of the download — these must EXACTLY match the
  values in section 3 (Maven artifacts are immutable; a mismatch is a stop-and-report,
  not a warning);
- the jar's internal layout: confirm assets live at `assets/minecolonies/**` (a mod
  jar, not a nested archive);
- of the 4822 upstream-baseline files: how many are present, how many byte-identical
  to the `$OUT/upstream` baseline, and the full list of missing/changed paths;
- specifically confirm the datagen products (expect YES to all; measured on 1368, not
  yet on 1374): `lang/en_us.json` (merged; compare against
  `$OUT/port-26.2/lang/manual_en_us.json` + the 312/2 split — expect it to cover all
  2767 upstream keys), the 97 non-English `lang/*.json`, `textures/entity_icon/**`
  (3481 files — spot-check a sample of hashes against `$OUT/generated`), `sounds.json`
  (compare semantically against `$OUT/generated/minecolonies/sounds.json`), and
  `sounds/mob/citizen/*.ogg`;
- for each of the 65 patched files: whether its 1374 base still hashes equal to its
  `325157eb` base. For any that changed, regenerate that patch against the 1374 base
  (this is exactly why Phase 0 keeps full trees);
- **the 1368↔1374 asset diff** (download source 2 as well — it is the fallback and
  must verify too): list files under `assets/minecolonies/**` that differ between the
  two jars. If the diff is small (expected), ship ONE manifest with per-path alternate
  hashes for the differing files; if it is large, ship two manifests keyed by source
  version. Decide from the measurement and document the decision.

Then generate `manifest.json`: for every file the *installed pack* must contain —
fetched files verbatim and patched outputs — its path, SHA-256, and size.
`pack.mcmeta` is EXCLUDED from the manifest (written at install time with the running
game's pack version, C8 — its content is environment-dependent). This manifest ships
in the jar and is the runtime source of truth for "assets ready".

Extraction rule (applies here and in C2): extract only `assets/minecolonies/**`;
guard against zip-slip (reject entries whose normalized path escapes the target).
The jar also contains upstream's classes, `data/`, and blueprints — none of that is
wanted; it is discarded unextracted.

---

## PHASE 2 — Build-side changes (repo `/workspace/minecolonies-fabric/26.2`)

**B1. Datagen: `DefaultEntityIconProvider` — disable.**
`src/main/java/com/minecolonies/core/generation/defaults/DefaultEntityIconProvider.java`.
Currently walks `assets/minecolonies/textures/entity/{citizen,raiders}/**` off the
mod's root paths and writes 3481 icons (~20 MB) into `src/main/generated` — which used
to ship and must never ship again (the icons are derivative of ARR textures). With
assets absent it already no-ops (`if (!Files.isDirectory(assets)) continue;` — verified
in source). Rev. 2: the icons arrive inside the fetched jar, so **no runtime utility
extraction is needed** — just disable/no-op the provider cleanly (remove its
registration or make it emit nothing) and make sure it can never crash datagen.

**B2. Datagen: `DefaultLanguageProvider` — repoint.**
Same package. Reads `/assets/minecolonies/lang/manual_en_us.json` from resources
(constant `MANUAL_RESOURCE`, line ~77) — that file is gone. Repoint it at the
port-owned 314-key lang source from Phase 0 (312 added keys + 2 rewritten values), so
datagen's generated `en_us.json` contains only port-authored keys. It must not crash
when the ARR file is absent (it will be, always, at build time).

**B3. Datagen: `DefaultSoundProvider` — REVERSED from rev. 1: ensure NO `sounds.json`
ships in our jar.** Same package. The fetched pack now carries upstream's real
`sounds.json`. Phase 4's injection puts the fetched pack at Position.BOTTOM, meaning
**jar-shipped files beat fetched files** — so a `sounds.json` in our jar would
permanently mask the real one. The provider's absent-folder fallback
(`__no_citizen_sounds__` path — verified in source) still *writes* a `sounds.json`
with no citizen events; that crippled file must not end up in the built jar. Disable
the provider (like B1) and verify the built jar contains no
`assets/minecolonies/sounds.json`. Pre-install, a missing `sounds.json` degrades to
warn + silence (verified failure mode, acceptable). The Phase 0 filename list is NOT
consumed; it stays as insurance.

**B4. (Already done — nothing to implement.)** The two `assets/minecraft` files are
restored on `main` (commit `49eb26f7`; see the starting state). Do not regress them.

**B5. Ship the fetch bundle.** `assetfetch/manifest.json` (+ the alternate-hash or
second manifest per Phase 1's decision), `assetfetch/patches/**`,
`assetfetch/transforms.json`, the 10 added files (if cleared as port-authored), and
the port-owned 314-key `lang/en_us.json` — all inside the jar.

After B1–B3, `runDatagen` must succeed from a clean checkout with no assets present
(verified plausible by reading the providers: `DefaultItemModelProvider`, all `data/`
providers, stories and quests read no assets; `SchemFixerUtil` is a no-op legacy
renamer) — but *plausible from reading is not verified from running*; running it is
part of your acceptance criteria. Additionally verify the built jar's
`assets/minecolonies/` contains EXACTLY: the 10 added files, `lang/en_us.json`
(314 keys), and whatever datagen legitimately emits from port-owned inputs — and
explicitly NO `sounds.json`, NO `textures/entity_icon/**`, and no other upstream
content.

---

## PHASE 3 — Runtime fetch & install (client-only code)

New package suggestion: `com.minecolonies.core.client.assetfetch`. Wire from the
existing client entrypoint `com.minecolonies.core.MineColoniesClient` (declared in
`fabric.mod.json`). Use `java.net.http.HttpClient` with redirects enabled; no new
dependencies. Task numbering keeps rev. 1's C-numbers so cross-references survive;
C4–C7 are closed by the jar source.

**C1. Cache layout.** `<gameDir>/minecolonies/fetched-assets/` containing:
`pack/` (pack root: `pack.mcmeta` + `assets/minecolonies/**`), `state.json`
(installed source id + URL + whole-jar hash + manifest hash + consent/decline record
+ the `customSourceUrl` override slot for source 3), `tmp/` (in-flight download,
atomically promoted on success). Persist state in this JSON file rather than touching
the mod's config schema.

**C2. Download step.** Iterate the source chain in order (skip source 3 when its URL
is empty, source 4 is UI-driven only). Stream the jar to `tmp/`, log bytes +
SHA-256. Enforce the whole-jar hash: sources 1–2 must match their pinned hash from
section 3; source 3 must match ONE of the known hashes; source 4 as described in
section 3. Then extract only the `assets/minecolonies` subtree with the zip-slip
guard. Per-source failures are logged with URL, HTTP status, bytes received, and
error text, then the next source is tried; the UI shows which source succeeded.

**C3. Patch step.** Apply the Phase 0/1 patch bundle: JSON Patches, XML diffs, the
mechanical composite-flattening transform for the 6 models — 65 files total. Write
results into `pack/`.

**C4. — closed by the jar source.** The merged `lang/en_us.json` arrives in the
download and is installed verbatim; the port's 314 keys live in OUR jar and override
per key. No install-time lang work exists.

**C5. — closed by the jar source.** The 3481 `entity_icon` files arrive in the
download and are installed verbatim (manifest-verified like everything else). No
runtime icon generation exists.

**C6. — closed by the jar source.** All 97 POEditor language files arrive in the
download. Non-English players get full translations in v1 — the rev. 1 "known
limitation" is gone.

**C7. — closed by the jar source.** `sounds.json` arrives in the download. See B3
for the build-side inversion this requires.

**C8. `pack.mcmeta`** — write at install time using the running game's own
`SharedConstants.getCurrentVersion().packVersion(CLIENT_RESOURCES)` (verified
mechanism from the prior design).

**C9. Verify step.** After C2–C3 and C8, hash every file against `manifest.json`
(remember: `pack.mcmeta` is exempt). All-match → atomically mark installed in
`state.json`. Any mismatch → report which files, delete nothing the user needs for
diagnosis, do not mark installed, and surface the failure in-game.

## PHASE 4 — Pack injection

### A note on mixins — not a blocker

**This port currently has no mixins.** The only widening mechanism in it is
`26.2/src/main/resources/minecolonies.accesswidener` (verified: `fabric.mod.json` has
an `accessWidener` entry and no `mixins` entry). The design below introduces the first
one, and **the owner has explicitly approved that** — do not spend time looking for an
accesswidener-only alternative, and do not treat this as a decision needing argument.

What it means practically is that the mixin plumbing does not exist yet and you have
to create it: a new `26.2/src/main/resources/minecolonies.mixins.json`, its `mixins`
entry in `fabric.mod.json`, and whatever Loom configuration the build needs. Budget
for that rather than assuming a working mixin pipeline.

Keep the surface small — ordinary good practice, not a special constraint. One
client-only mixin class injecting into the `PackRepository` constructor to append one
`RepositorySource`, the identical shape to Fabric API's own
`PackRepositoryMixin.construct` in `fabric-resource-loader-v1` 2.0.13 (verified
precedent). Nothing else migrates to mixins on the back of this.

### The injection itself (verified design — build on it)

- The `RepositorySource` offers a `Pack` over the cache `pack/` dir via
  `PathPackResources`, created with `Pack.readMetaAndCreate` and
  `PackSelectionConfig(required=true, fixedPosition=true, Position.BOTTOM)`.
- `required=true` → `PackRepository.rebuildSelected` (lines 82–92) force-selects it on
  every `reload()`; the user cannot disable it in the pack screen.
- `Position.BOTTOM` → just above vanilla, below mod packs, so **jar-shipped files win
  conflicts** (this is load-bearing three times over in rev. 2: for B5, for the
  restored `assets/minecraft` files, for the 2 rewritten lang values — and it is the
  entire reason B3 must ship NO `sounds.json` and C3 writes patched files into the
  cache pack rather than the jar).
- The source offers nothing when `state.json` says not-installed, so pre-consent the
  game is untouched.
- After a successful install, call `Minecraft.reloadResourcePacks()` (public,
  `Minecraft.java` line 995, verified) — it re-discovers sources before reloading.
- Fabric API's public `ResourceLoader.registerBuiltinPack` cannot be used — it only
  serves packs inside the mod jar (verified; that is the whole reason for the mixin).

## PHASE 5 — Consent UX and crash guards

**D1. Consent flow.** Verified: with assets absent the client boots to a visually
clean title screen. On first arrival at the title screen with state = neither
installed nor declined, show a confirmation screen: what will be downloaded
(LDTTeam's original assets, as their official build from their own Maven server,
≈78 MB — 78,071,143 bytes), that the files are ARR and stay on this machine, buttons
Download / Not now, and a help line pointing at the manual CurseForge escape hatch
(source 4). "Not now" persists; provide a way back in (a button on the mod's own
screens and/or a client command, e.g. `/minecolonies-client fetchassets`). Download
progress must be visible (78 MB is noticeable) and failure states legible, including
which source failed and which succeeded (see escalation section for what failure must
*not* do).

**D2. Crash guard: BlockUI windows.** Verified crash path: opening any MineColonies
window with assets absent throws `RuntimeException("Gui at … was not found!")`
(BlockUI `Loader.java` line 176, `/workspace/blockui/26.2/...`), unhandled in a
screen-open path. Add a central `AssetFetch.isReady()` gate at the port's window-open
choke point(s) (you must locate them — likely where `BOWindow`/`BOScreen` instances
are constructed/opened); when not ready, open a small "assets not installed" screen
offering the download instead of the window. Do not try-catch around BlockUI as the
primary mechanism; gate before opening.

**D3. Crash guard: SLEEPING particle.** Verified crash path: `SLEEPING` is a
sprite-set particle; with its JSON absent the sprite set is never rebound and
`MutableSpriteSet.get` NPEs **when the particle first spawns** (a citizen sleeping in
a loaded chunk — not at boot, so it will not show up in a lazy test). Gate the
client-side spawn site(s) of MineColonies sprite-set particles behind `isReady()`.
Do **not** ship a placeholder particle JSON in the jar — jar files win over the
fetched pack (Position.BOTTOM), so a jar placeholder would permanently mask the real
asset.

**D4. Missing-asset behavior you do *not* need to guard** (verified): missing texture
→ log + checkerboard; missing model/blockstate → log + missing model; missing sound →
warn + silence; language merges per key. Only D2 and D3 crash.

## PHASE 6 — Blueprints: already resolved, nothing to do

An earlier draft scoped blueprint recovery as open work. It is done: the owner
accepted the GPLv3 determination (blueprints sit outside `assets/`, carry no licence
file of their own, and fall under the old repository root's GPLv3 — upstream's own
repo-root LICENSE at `325157eb` is likewise GPLv3), and
`26.2/src/main/resources/blueprints/` (9996 files, 24 packs, ~31 MB in-jar, tree
`4cd26b61ef38a671c0fef8bf551fde85fcc2b5b5`) is restored, committed and pushed to
`main` together with the two `assets/minecraft` files (commit `49eb26f7`). They ship
in the jar as before. Your only obligation is not to regress them; the server boot
test (acceptance criterion 3) now also exercises blueprint loading in passing.

Why blueprints are deliberately **outside** the fetch design (verified facts, kept
for the record):

- The tree is the upstream baseline at `325157eb` (9856 files, all byte-identical)
  plus 140 additions, 0 modified, 0 deleted.
- Of the 140, only 25 exist upstream at all — the two syncs recorded in
  `UPSTREAM-SYNC.md`: 15 alchemist blueprints from `c7c6db2e1c` (Moobien, #10643) and
  10 `caledonia/fundamentals/resalt{a,b}1-5` from `486f7e714f` (#11751), all still
  byte-identical at upstream head `b9fb5a7c`.
- The remaining 115 are the port's own work and exist in **no** upstream commit:
  `<style>/military/stable{1..5}.blueprint` across 23 style packs — the port's Stable
  feature, each derived from that style's cow barn (README, "The Stable, unlocked").
  A fetch-only route could therefore never reproduce the tree; it would delete the
  Stable feature outright.
- They are not resource-pack content and are needed **server-side**, so the Phase 4
  pack injection would not have delivered them anyway.

The `dist/` built jars and `src/main/generated/assets` remain permanently out; nothing
in this brief re-adds upstream `assets/minecolonies/` content to repo or jar.

---

## ACCEPTANCE CRITERIA — "implemented but untested" is a failed result

1. **Build green.** ALL Gradle work goes through
   `/home/user/mc-build.sh <project-dir> <task>` — it holds a global lock; two
   concurrent Gradle runs corrupt the shared Loom cache. Never invoke `gradle` or
   `./gradlew` directly. `runDatagen` is always a separate invocation from `build`,
   and the jar must be rebuilt after datagen or its generated content is stale.
   Required: `runDatagen` succeeds from the asset-less checkout, then `build`
   succeeds, then the jar-content check from B5's acceptance paragraph passes.
2. **A real download, actually performed and actually verified** — not "the code
   looks right". Report numbers: bytes transferred, SHA-256 of what arrived (must
   equal the pinned hash), files extracted, files whose hash matched `manifest.json`
   (must be all), resulting on-disk size of the installed pack. Exercise at least
   source 1 end-to-end; source 2 may be exercised by pointing the chain at it
   (e.g. temporarily failing source 1) or in Phase 1's characterization download.
3. **Boot test.** The built jar boots on the dedicated-server harness (pattern:
   `/home/user/srv-integ`, port 25611, console via `mkfifo cmd.fifo` then
   `tail -f cmd.fifo | java …`). Evidence required: a `Done (` line, ERROR and FATAL
   counts, WARN count compared against the recorded baseline. Kill only your own
   server PID — **never** `pkill -f fabric-server-launch.jar` (other sessions share
   the box). A port-bind failure immediately after killing a server is a race; retry
   once before calling it a fault.
4. **Proof the assets are really there and really used.** On disk: expected files
   with expected hashes (criterion 2). In game: evidence the pack is actually
   selected, not merely present — the `Reloading ResourceManager` log line lists
   loaded pack ids and must include the injected pack; plus at least one concrete
   asset observation (e.g. a MineColonies window opens without the D2 gate tripping,
   or a specific texture resolves). If your environment cannot run a client, say so
   explicitly in the report and enumerate exactly which of these proofs you could and
   could not produce — do not substitute silence.

---

## THE ESCALATION RULE

If the download genuinely does not work — sources 1 AND 2 are gone, jfrog blocks
anonymous access, the transfer will not complete — you **STOP and tell the owner**.
The owner will stand up their own HTTP host and enable source 3 (the slot you built
for exactly this). Your report must contain, per source tried: the URL you hit, the
HTTP status, bytes received before failure, and the verbatim error text — so the
owner can act instead of guess.

What you must NOT do instead, under any framing:

- Do not mirror the assets anywhere yourself (no gist, no release, no bucket, no
  branch, no "temporary" host). Source 3 is enabled by the OWNER standing up the
  OWNER's host; it is never you uploading anything anywhere.
- Do not commit them to any repository.
- Do not bundle them into the jar, a test fixture, or a "fallback pack".
- Do not devise any workaround that ends with this project distributing those files.

The entire point of this architecture is that we never redistribute them.

The same rule applies at install time in shipped code: a failed or partial download
must leave the user in the clean pre-consent state with a readable error, never in a
half-installed one.

---

## KNOWN-UNVERIFIED — assumptions you must convert into measurements

Everything below is stated in this brief as expectation, not fact:

1. The 1374 jar's contents: presence and correctness of the datagen products
   (`en_us.json`, 97 lang files, 3481 `entity_icon`, `sounds.json`, the `.ogg`
   files), and how many of the 4822 baseline files are present/byte-identical.
   (The 4821/4822 figure and the datagen-product inventory were measured on the
   *1368* jar; the 1374 jar's existence, size and hashes are verified, its contents
   are not.)
2. Whether any of the 65 patch bases changed between upstream `325157eb` and the
   1374 jar (patch bases may need regeneration), and the size of the 1368↔1374
   asset diff (drives the one-vs-two manifest decision).
3. That `runDatagen` actually passes after B1–B3, and that the built jar carries no
   `sounds.json`/`entity_icon` (provider reading says yes; only a run proves it).
4. The exact code sites for the D2 window-open gate and D3 particle-spawn gate.
5. That the 10 added files are wholly port-authored (owner sign-off), and owner
   sign-off on the minimal-quote patch bundle.
6. The runtime `packVersion(CLIENT_RESOURCES)` value (mechanism verified; the number
   comes from the running game).
7. Whether a client can be run in your environment for criterion 4's in-game proof.
8. Exact file counts at `ea4243c8` (the on-disk working copy showed 4832 files in the
   26.2 ARR tree, prior investigation recorded 4834 removed — reconcile during
   Phase 0; the difference is likely `LICENSE` plus branch drift).
9. Snapshot retention on `ldtteam.jfrog.io` (watch item, low risk: snapshots back to
   build 955 are still present as of 2026-08-19; source 2 is a release entry and the
   chain absorbs a disappearance either way).

Established facts you do NOT need to re-verify: the pack-injection design mechanics
(required/fixedPosition/BOTTOM force-selection, `reloadResourcePacks()` publicity,
Fabric's `PackRepositoryMixin.construct` precedent), the missing-asset failure modes,
the two crash paths' existence, the 10/66/0 delta composition, per-key language
merging across packs, the datagen providers' asset dependencies, the server's
independence from `assets/`, and (verified 2026-08-19) the existence, sizes, SHA-256s
and anonymous reachability of both Maven jars.

---

## WORK PACKAGES — execution split

Sized so each package is independently testable and reviewable. Dependencies are
strict; parallelize only where stated.

- **WP0 — Phase 0 extraction & delta.** Runs on the box holding
  `/home/user/minecolonies`. No dependencies. Hard stop-condition: delta must be
  10 added / 66 modified / 0 deleted, else reconcile before anything else proceeds.
  Deliverables: `$OUT` trees, patch bundle + transforms, 314-key lang file, 10-file
  report for sign-off, insurance tarballs handed to the owner, generator scripts
  committed.
- **WP1 — Phase 1 characterization & manifest.** Depends on WP0 (`$OUT/upstream`,
  `$OUT/generated`, patch bases). Downloads both jars once, produces the measurement
  report and `manifest.json` (+ the one-vs-two-manifests decision), regenerates any
  drifted patches.
- **WP2 — Phase 2 datagen changes (B1, B2, B3, B5).** Depends on WP0 (lang file) and
  WP1 (manifest for B5; B1–B3 can start after WP0). Acceptance: criterion 1 incl.
  the jar-content check.
- **WP3 — Phase 3 installer & source chain (C1–C3, C8, C9).** Depends on WP0
  (patches) + WP1 (manifest format). Testable headless against the real source 1.
- **WP4 — Phase 4 mixin & pack injection.** Depends on nothing but the repo (C1's
  `state.json` shape should be agreed with WP3 first — one page of interface).
  Parallel with WP2/WP3.
- **WP5 — Phase 5 consent UI + D2/D3 gates.** Depends on WP3/WP4 interfaces
  (`isReady()`, install trigger).
- **WP6 — Acceptance run.** Depends on all. Executes criteria 1–4 end-to-end and
  writes the numbers-included report.
