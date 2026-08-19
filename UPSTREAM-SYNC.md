# Upstream sync log

Where this port stands relative to [`ldtteam/minecolonies`](https://github.com/ldtteam/minecolonies),
branch **`version/1.21`**, and what has been taken from it since.

Read this before checking upstream again: it says which commit to diff from, so the next check is a
one-command job rather than an archaeology session.

## Where we are

| | |
|---|---|
| Upstream branch | `version/1.21` (NeoForge 21.1, Java 21) |
| **Snapshot base** | **`325157eba3`** — 2026-07-29, *Jinotad darkoak 1.21.1 (#11770)* |
| **Reviewed through** | **`b9fb5a7cc6`** — 2026-08-11, merge commit, branch tip at the time |
| **Runtime asset source** | **`1.1.1374-1.21.1-snapshot`** — LDTTeam's built jar on their own Maven; chosen 2026-08-19, fetched at runtime, never committed here |
| Last checked | 2026-08-15 |

The base is exact, not approximate: `1.21.1/src` in this repository is byte-identical to upstream's
`src` at `325157eba3`, with one deletion — a stray `core/entity/todo.txt~` that upstream committed by
accident. Everything else in `1.21.1/` is upstream's, verbatim, and **is never edited**. Ports go into
`26.2/`; the snapshot stays a clean diffing base, which is the only reason the table below can be
trusted.

The ARR asset tree (`assets/minecolonies/`) is **not in this repository** — players fetch it at
runtime from LDTTeam's own server. Chosen source (2026-08-19): the **`1.1.1374-1.21.1-snapshot`**
built jar from `ldtteam.jfrog.io/artifactory/modding` (78,071,143 bytes, SHA-256
`9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa`); fallback chain:
`1.1.1368-1.21.1` release jar (SHA-256
`c3a2542aaced85aabfc58b38415b70e6b095a16787056e07880fc94320f09a9b`) → owner-hosted HTTP slot
(shipped disabled) → player-supplied local jar. If the source ever moves, update this entry — it is
the record of which upstream build the runtime assets sync to.

## Commits since the base

| Commit | Date | What | Verdict |
|---|---|---|---|
| `1e1b1d8ebf` | 2026-08-02 | Citizen inventory not returning a remainder for stacks over the max size (#11772) | **Ported** — before this log existed; `26.2/.../api/inventory/InventoryCitizen.java`. Its second file was a cosmetic module-lookup swap, not taken. |
| `c83894095b` | 2026-08-10 | Fix arrow crash | **Not needed** — the same crash was found and fixed here first, in `RangeCombatAI`, off the entity that was actually hit rather than off `user`. Upstream reads `target.level()` after the guard has dropped its target, which NPEs out of the arrow's tick. |
| `486f7e714f` | 2026-08-11 | Two new residence alternatives in Caledonia (#11751) | **Ported** — 10 blueprints, `caledonia/fundamentals/resalt{a,b}1-5`. |
| `eb7b5e4180` | 2026-08-11 | gamesession sorting (#11782) | **Ported** — `WindowHutAllInventory`: the sort holds for the session, and the button label and list are put in step in one place the constructor also calls. |
| `f137bc0da3` | 2026-08-11 | Port of 11714 (#11779) | **Ported** — glass-bottle and large-bottle recipe loot tables go from weight 0 to 100, so empty bottles come back from recipes instead of only on a luck roll. Needs `runDatagen`. |
| `c7c6db2e1c` | 2025-02-09 (merged 08-11) | Moobien (#10643) | **Ported** — 15 blueprints, the alchemist in Medieval Birch, Medieval Dark Oak and Medieval Oak. Still missing in Cavern, Colonial, True Dwarven and the template. |
| `b9fb5a7cc6` | 2026-08-11 | Merge commit | Nothing of its own. |

All of the above landed in `5730d9d34a`, except `1e1b1d8ebf`, which predates this log.

## Rules this log assumes

* **`1.21.1/` is read-only.** A backport is applied to `26.2/` and recorded here; the snapshot is not
  advanced to match. Moving the base is a deliberate act — update the table above when it happens.
* **Blueprints are byte-identical between 1.21.1 and 26.2.** They are copied straight across and the
  copies are checksummed against upstream afterwards. `pack.json` holds no file index, so a new
  blueprint needs no registration; the build tool finds it by the hut block inside it.
* **A generated-data change needs `runDatagen`**, as its own invocation, before `build` — see
  `/home/user/ENV-26.2.md`. The loot-table entry above is one of those.
* **"Not needed" is a verdict, not a skip.** It means the change is already present here by another
  route, and the route is named, so nobody re-reviews it next time.

## How to check again

```sh
git fetch upstream version/1.21
git log --format='%h %ad %s' --date=short --reverse <base>..upstream/version/1.21
```

with `<base>` from the table. To inspect one commit: `git show --stat <sha>`, then
`git diff <sha>^:src/<path> <sha>:src/<path>`.

If the base ever becomes uncertain — a hand-applied backport can blur it — find it again by tree
equality rather than by date:

```sh
OURS=$(git rev-parse HEAD:1.21.1/src)
for c in $(git rev-list upstream/version/1.21 -n 400); do
  [ "$(git rev-parse -q --verify $c:src 2>/dev/null)" = "$OURS" ] && echo "base: $c" && break
done
```

That matches only if the snapshot is pristine. When it is not — as now, with `todo.txt~` deleted —
compare candidates by how many files differ instead: the true base stands out by an order of magnitude
(`git diff --name-only <candidate>:src HEAD:1.21.1/src | wc -l`), and a single file upstream touched in
a known commit will pin it exactly.
