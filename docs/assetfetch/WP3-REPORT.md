# WP3 — Phase 3 runtime installer (C1, C2, C3, C8, C9): report

Branch `claude/assetfetch-wp3`, merged on top of WP0/WP1/WP2/WP4.
New package: `26.2/src/main/java/com/minecolonies/core/client/assetfetch/` (26 new classes,
all GPLv3, no new dependencies — `java.net.http.HttpClient` and the GSON that Minecraft
already ships).

**Everything below was run, not reasoned about.** The numbers are from real runs against
LDTTeam's Maven; the two commands that produced them are named in §6.

---

## 1. What was built

| task | class(es) | what it does |
| --- | --- | --- |
| C1 | `InstallState`, `InstallConfig`, `FileTrees` | `state.json` schema v1, written atomically; `pack/`, `tmp/` layout |
| C2 | `SourceChain`, `AssetSource`, `JarDownloader`, `JarAssetExtractor` | the four sources, streaming download + hash, zip-slip-guarded extraction |
| C3 | `PatchBundle`, `CanonicalJson`, `JsonPatch`, `CompositeFlatten`, `UnifiedDiffPatcher`, `BundleResources` | applies the shipped bundle to the fetched tree |
| C8 | `PackMetaWriter` | `pack.mcmeta`, written from the running game's pack format |
| C9 | `AssetManifest`, `PackVerifier` | effective manifest per source, prune, hash-verify |
| glue | `InstallPipeline`, `AssetInstaller`, `InstallListener`, `InstallPhase`, `InstallReport`, `SourceAttempt`, `CancelSignal`, `Hashes`, `AssetInstallException`, `InstallCancelledException` | the run itself and the surface the consent UI drives |

Only **`AssetInstaller`** touches Minecraft or Fabric, and only for two things: the game
directory (via `AssetFetch.baseDir()`) and
`SharedConstants.getCurrentVersion().packVersion(CLIENT_RESOURCES)`. Everything else is
plain Java over `Path` arguments, which is why the whole pipeline could be run headlessly.

## 2. The API the consent UI (WP5) drives

```java
AssetInstaller installer = AssetInstaller.forGame();          // sources 1-3
AssetInstaller installer = AssetInstaller.forLocalJar(path);  // source 4, player-picked jar

CompletableFuture<InstallReport> done = installer.start(listener);  // own daemon thread
installer.cancel();                                                 // any thread, any time

AssetInstaller.recordDeclined();          // "Not now" -> state.json status=declined
AssetInstaller.hasDeclined();             // so the UI can offer a way back in
AssetInstaller.setCustomSourceUrl(url);   // owner's source-3 override, no rebuild
```

`InstallListener` — every method has a no-op default:

| callback | when |
| --- | --- |
| `onPhase(InstallPhase)` | `STARTING → DOWNLOADING → CHECKING_JAR → EXTRACTING → PATCHING → VERIFYING → INSTALLING → DONE` |
| `onSourceStarted(id, url, description)` | before each source is tried |
| `onBytes(transferred, total)` | during the download, at most once a megabyte; `total` is -1 if the server did not say |
| `onFiles(done, total)` | during extract/patch/verify; `total` is -1 while unknown |
| `onSourceFailed(SourceAttempt)` | a source failed — carries id, url, HTTP status, bytes received, error text |
| `onFinished(InstallReport)` | exactly once, whatever happened |

`InstallReport` carries `outcome` (`INSTALLED` / `NO_SOURCE` / `FAILED` / `CANCELLED`), a
player-showable `reason()`, the source id/url/jar hash, byte and file counts, the installed
pack size, and `attempts()` — every source tried, in order.

Two contract points for WP5:

- **All callbacks arrive on the installer's thread.** A screen must hand values over itself
  (a volatile field the render loop reads, or `Minecraft.getInstance().execute(...)`).
- **The installer never calls `Minecraft.reloadResourcePacks()`.** The UI does that on the
  client thread after a successful report. By then `state.json` is written and
  `AssetFetch.invalidate()` has already run, so the pack repository will offer the pack.

The future never completes exceptionally: a failure is a report with a non-`INSTALLED`
outcome and a message worth showing.

## 3. Canonical JSON, and the hash-equality proof

A patched JSON document has no natural byte form, so `transforms.json` pins one and every
hash in `manifest.json` was computed against it. `CanonicalJson` implements it: UTF-8,
2-space indent, `": "`, document order (never sorted), non-ASCII literal, one trailing
newline, CPython's `ensure_ascii=False` escape table, and **numbers re-emitted from their
source literal**.

That last clause is the trap WP0 warned about. It is honoured by never converting a JSON
number to a Java numeric type: GSON's parser stores every number as a `LazilyParsedNumber`
holding the original text, and the serialiser writes that text back through
`JsonPrimitive.getAsString()`. Nothing in the patcher calls `getAsInt`/`getAsDouble`, so
`16` stays `16` and `4.52` stays `4.52`.

Measured, running the shipped patcher over the `325157eb` upstream tree
(`$OUT/upstream`, 4822 files):

```
patched 69 files in 101 ms
vs expected-patched-sha256.json : 67 match, 2 differ
vs manifest.json                : 69 match, 0 differ
```

The two are `models/block/blockhutcook.json` and `models/block/blockhutfletcher.json`, and
they are **not** a patcher bug — WP1 reached the same conclusion independently. WP0's
`expected-patched-sha256.json` hashed the *port's hand-written file*, whose root keys happen
to run `…, display, textures, elements`; the flatten rule, per its own definition and per
`canonicalJson`'s "document order after patching", produces `…, textures, display, elements`.
Same document, different key order. Both files' bytes were also compared against a fresh
replay of the Python reference implementation (`jsonpatch.py` + `composite_flatten.py` +
`gen_bundle.py`'s `canonical_dumps`) and are **byte-identical to it**:

```
blockhutcook.json      python replay 14306e25…   java 14306e25…
blockhutfletcher.json  python replay bd5e59db…   java bd5e59db…
```

`manifest.json` is the authority (it was generated by running a patcher, as WP0 required),
and against it the Java patcher is 69/69.

## 4. `pack.mcmeta` in 26.2 — the API is not what the brief assumed

The brief said `SharedConstants.getCurrentVersion().packVersion(CLIENT_RESOURCES)` returns
the pack version. Checked against `/opt/mc-src`: in 26.2 it returns a
**`PackFormat(major, minor)` record**, not an `int` (`net/minecraft/WorldVersion.java`), and
26.2's client resource format is **88.0** (`DetectedVersion.createBuiltIn`).

That changes the file's shape. `PackFormat.lastPreMinorVersion(CLIENT_RESOURCES)` is 64, and
`PackFormat.IntermediaryFormat.validate` *rejects* a pack declaring a format above 64 that
carries the old `supported_formats` key, and rejects `min_format`/`max_format` without it
below 64. So for 26.2 the only accepted spelling is:

```json
{"pack": {"description": "...", "min_format": 88, "max_format": 88}}
```

Read back through `BOTTOM_CODEC`/`TOP_CODEC` those bare majors mean `88.0` and `88.*`, which
`PackCompatibility.forVersion` scores `COMPATIBLE` against a running `88.0` and against any
future minor of 88. The number is a parameter of `PackMetaWriter.write(packRoot, major)`;
`AssetInstaller` supplies it from the running game, the headless tests pass 88.

## 5. Failure behaviour (the escalation rule in shipped code)

Nothing outside `tmp/` is touched until everything has verified. The jar lands in `tmp/`, is
unpacked into `tmp/stage/`, patched there, given its `pack.mcmeta` there and verified there;
only then is the staged tree swapped into `pack/` (an existing pack is parked in `tmp/` and
put back if the swap fails) and `state.json` rewritten. `tmp/` is deleted in a `finally`,
whatever happened. Verified by test: a failed install leaves `state.json` byte-identical, no
`pack/`, and no `tmp/`.

Per-source failures record URL, HTTP status, bytes received and error text, log them, hand
them to the UI, and move to the next source. A jar whose whole-jar hash matches nothing known
is rejected **before extraction**, with the actual hash in the message.

`customSourceUrl` survives every rewrite of `state.json` — install, decline, both — because
it is the owner's operational fix and losing it would silently undo it. It is read even when
`status` is absent or the schema version is wrong.

## 6. Test results

### 6.1 Patcher, headless (`PatchTest`)

See §3. 69 files patched from the `325157eb` bases; 69/69 hashes equal `manifest.json`.

### 6.2 End-to-end, source 1, real download (`E2ETest`)

The real `JarDownloader`, `JarAssetExtractor`, `PatchBundle`, `PackVerifier` and
`InstallPipeline`, run from a plain `java` command against a scratch game directory:

| measurement | value |
| --- | --- |
| bytes transferred | **78,071,143** (equals the pinned size) |
| whole-jar SHA-256 | **9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa** (equals the pin) |
| HTTP status | 200, one request, redirects enabled |
| files extracted from `assets/minecolonies/**` | **8470** |
| files patched (65 edited + 4 derived) | **69** |
| files pruned as not-in-manifest | **0** |
| files verified against `manifest.json` | **8474 / 8474** |
| files in `pack/` afterwards | 8475 (the manifest set + `pack.mcmeta`) |
| installed pack size on disk | **82,435,189 bytes** |
| wall clock | 5 s (this container's proxy is fast; a player's will not be) |
| `tmp/` afterwards | gone |

`state.json` written:

```json
{"version":1,"status":"installed","sourceId":"maven-1374","sourceUrl":"https://ldtteam.jfrog.io/.../minecolonies-1.1.1374-1.21.1-snapshot.jar",
 "jarSha256":"9ea739a2…","manifestSha256":"723b120be1abf2edb4bad0ef014e27e62e7f9f41cc962e23234c609cce33b9fa","installedAt":"2026-08-19T18:50:01Z"}
```

### 6.3 End-to-end, source 2, real download (`Source2Test`)

Source 1 pointed at a non-existent Maven path so the chain falls through:

```
attempt: maven-1374 (…/does-not-exist.jar): HTTP 404, 0 bytes received, the server answered HTTP 404
attempt: maven-1368 (…/minecolonies-1.1.1368-1.21.1.jar): ok, 77945293 bytes
outcome=INSTALLED source=maven-1368
bytes=77,945,293 sha=c3a2542a… extracted=8470 patched=69 verified=8474 removed=0 packBytes=82,434,471
alt-manifest file uk_ua.json sha256 = 7cc5b4c64896d765d1f3312d5399b2f2ba06fe4143452672b756b95891ab9f5e
```

The last line is the `alt` overlay doing its job: `uk_ua.json` differs between the two builds,
and the 1368 install verified against the 1368 hash, not the base one.

### 6.4 Chain, rejection, state and cancellation (`ChainTest`, 24 checks, all pass)

Over a local plain-HTTP server, so `http://` is exercised for real:

- dead sources (404, garbage) fall through to a **plain-`http://`** owner host, which installs;
- a garbage jar is refused with its actual hash and the supported versions named;
- a pinned source refuses a jar of the wrong size/hash, installs nothing, cleans `tmp/`;
- a truncated transfer fails the source with the bytes recorded;
- source 4 with a known-hash local jar installs and is verified as that build;
- source 4 with an unknown jar that cannot verify is refused, naming the supported versions;
- a zip-slip entry (`assets/minecolonies/../../../../../../tmp/pwned.txt`) is refused and no
  file appears outside the target;
- `customSourceUrl` survives install and decline; the chain picks it up as source 3;
- a failed install leaves `state.json` byte-identical and no `tmp/`;
- cancelling mid-download ends as `CANCELLED` and leaves nothing behind;
- the prune deletes an unlisted file and keeps `pack.mcmeta`.

### 6.5 Bundle loaded from the built mod jar

`BundleResources.ofModJar()` against `build/libs/minecolonies-26.2-0.0.51.jar`: `transforms.json`
16,771 B, `manifest.json` 1,611,863 B (sha256 `723b120b…`, matching what the install recorded),
72 patch files, 69 bundle entries, 8474 effective files for both sources.

### 6.6 Build

`/home/user/mc-build.sh /workspace/wt-wp3/26.2 build` → **BUILD SUCCESSFUL** (26 s,
after the final edit). No `runDatagen` was run (WP2 owns that).

Test drivers live at `/home/user/wp3-test/` (`PatchTest`, `E2ETest`, `Source2Test`,
`ChainTest`, `JarBundleCheck`) and are deliberately **not** committed — they operate on
extracted upstream trees. No downloaded jar, extracted tree or ARR file was committed or
uploaded anywhere.

## 7. Not verified

1. **Anything requiring a running client.** No client was started, so: the pack actually
   appearing in a `Reloading ResourceManager` line, a texture resolving in game, and the real
   value of `packVersion(CLIENT_RESOURCES).major()` at runtime. The *mechanism* is read from
   26.2's own sources and the file it produces is what those sources accept; the number 88 is
   `DetectedVersion`'s compiled-in value, and the installer takes it from the running game
   rather than hard-coding it.
2. **`AssetInstaller` itself** — the only class not exercised headlessly, because it is the one
   that needs `SharedConstants` and Fabric's game directory. It compiles into the jar; its body
   is thread creation plus three static one-liners over `InstallState`. Everything it delegates
   to was run.
3. **Windows path behaviour.** Paths are handled with `Path`/`/`-normalisation throughout and
   the atomic-move fallback is in place, but nothing was run on Windows.
4. **A source-3 host in anger.** The slot ships empty; it was exercised against a local HTTP
   server, not a real owner host.
5. **Very slow or flaky networks.** The connect timeout is 30 s and the body has none by
   design (a player can cancel); no test simulated a stalled-but-open connection.
