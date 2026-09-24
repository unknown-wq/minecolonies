# Changelog — 26.3

Unofficial **Fabric** port of MineColonies to **Minecraft 26.3**. Not affiliated with LDTTeam.

Every build is a single installable jar — `blockui`, `structurize` and `domum_ornamentum` are
bundled inside it and must **not** be added to `mods/` separately. Requires Fabric API and Java 25.

Versions below are this port's own numbering, newest first.

The two lines number separately and share nothing but the scheme: a version here is not the same
build as the version of that number on the 26.2 line, which keeps its own notes in `CHANGELOG.md`.

Builds through **0.0.84** were shipped before this file existed and their notes live in the commit
that shipped each one — `Build 0.0.84: released Minecraft 26.3, a mine that stops re-digging itself,
and cavalry research` is the most recent. This file starts at 0.0.85.

---

## 0.0.85

Eating at home no longer costs a citizen their food variety, the undertaker finishes a burial he
started, and the tavern's night music has a switch.

**A citizen who ate anywhere but a restaurant was punished for it.** Leftovers in the hut, an apple
picked up on the way, a stack a player left in a rack — every helping went into the food history,
and that history is what the diversity check reads. So the citizen who ate at home lost happiness by
the very mechanic that exists to reward a varied menu, and the only way not to was to walk to the
restaurant for every bite. Food eaten away from a restaurant is now recorded only when it differs
from the last thing eaten, or on a one-in-ten roll when it does not. Food from a restaurant is
recorded exactly as before.

**A burial interrupted by a sleep cycle or a break was never resumed.** The undertaker walked off to
the next grave to empty and the citizen stayed unburied for good, because nothing else ever clears
the pending burial. He now returns to it — when there is a free plot to put the body in. A graveyard
with every plot taken is an ordinary state and is left alone, so a full yard no longer stops him
emptying graves, which is where a player's lost items come back from.

**He could also count a burial that left no headstone.** If the block did not go down — the chunk
went away, the spot stopped being free between choosing it and reaching it — the grave data was
cleared anyway and the citizen was recorded as buried under a stone that does not exist. The
placement is now checked, and he picks another plot instead. A third case: the grave he was sent to
empty could be gone by the time he set off, and nothing checked before sending him to walk to it.

**Citizens no longer stall inside a waterlogged block.** Standing in a slab or a stair with water
above them, they did not rise: the check asked whether the block they stood in was water, and that
test deliberately answers no for most blocks that merely hold water. It now asks about the water
itself.

**The tavern's theme can be switched off**, on the Tavern's new settings tab, and it now plays on
the jukebox volume slider rather than the ambient one — so turning music down turns it down, without
quieting the rest of the game. The Graveyard gains a minimum-stock tab of its own, which is how the
undertaker's totems can be kept in the hut.

Inside, with nothing to see: the pathfinder follows a path by kept node references rather than
re-indexing it at every question, and its debug tracking ships one node at a time. Taken from
upstream so the two lines do not drift.
