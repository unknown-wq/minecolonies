package com.minecolonies.api.compatibility.dynamictrees;

/**
 * TODO(port-26.2): DISABLED — Dynamic Trees compatibility.
 *
 * <p>The original implementation compiled against {@code com.dtteam.dynamictrees.*} ({@code BranchBlock},
 * {@code TrunkShellBlock}, {@code DynamicLeavesBlock}, {@code Seed}, {@code Family}) and against NeoForge's
 * {@code net.neoforged.neoforge.common.util.FakePlayer}. What is missing is only the first half: there is no
 * Dynamic Trees build for 26.2 on this machine to compile against.</p>
 *
 * <p>The fake player is <em>not</em> a problem, and the earlier wording here ("Fabric has no FakePlayer
 * equivalent, contract C4") was simply wrong: {@code net.fabricmc.fabric.api.entity.FakePlayer} ships inside
 * fabric-api (module {@code fabric-events-interaction-v0}), and this same source tree already uses it in
 * {@code ThreatTable}, {@code TargetAI}, {@code EntityUtils}, {@code ColonyPermissionEventHandler} and
 * {@code ColonyPackageManager}. Leaving the claim in place only invited the next audit to repeat it.</p>
 *
 * <p>The class is kept so that {@code CompatibilityManager} keeps compiling and its reference site stays visible.
 * It now inherits every method from {@link DynamicTreeProxy}, i.e. it reports Dynamic Trees as absent and every
 * dynamic-tree query answers "no". Observable effect: colonists treat Dynamic Trees trees as ordinary blocks —
 * lumberjacks will not fell them as trees and will not replant dynamic saplings.</p>
 *
 * <p>To restore: put a 26.2 Dynamic Trees jar on the compile classpath, restore the original body from the
 * NeoForge 1.21.1 version of this class — upstream MineColonies carries it under this same package and name, and
 * this repository held a copy of it in the 1.21.1 snapshot it used to ship, which now survives only in the git
 * history — and swap the NeoForge {@code FakePlayer} used by {@code getTreeBreakActionCompat} for the Fabric one
 * ({@code FakePlayer.get(serverLevel)}). Only the Dynamic Trees jar is actually blocking.</p>
 */
public final class DynamicTreeCompat extends DynamicTreeProxy
{
    public DynamicTreeCompat()
    {
        /*
         * Intentionally left empty.
         */
    }
}
