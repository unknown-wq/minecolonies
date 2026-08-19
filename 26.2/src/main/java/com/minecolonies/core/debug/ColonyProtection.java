package com.minecolonies.core.debug;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

/**
 * Colony protection: one colony wide switch that stops the colony denying a player anything.
 * <p>
 * The whole feature is reachable from this one symbol — {@code grep -rn ColonyProtection src/main/java} is a
 * complete inventory of it, exactly as it is for {@link FreeMode}. The switch itself is a boolean on
 * {@link Colony}, set by {@code /mc colony protection <colony> off}.
 * <p>
 * It is read in exactly one place: {@link Permissions#hasPermission(net.minecraft.world.entity.player.Player,
 * com.minecolonies.api.colony.permissions.Action)}, the single funnel every server side permission test in the
 * mod goes through. Putting it there rather than at each enforcement site is what makes "off" mean off: block
 * breaking, block placing, hut placing, right clicking, opening containers, filling buckets, drawing a bow,
 * attacking a citizen, the build tool, the supply chest deployer and the assistant hammer all ask that one
 * method, so all of them stop refusing at once and none of them can be forgotten.
 * <p>
 * <b>What it deliberately does not touch.</b> Two things in this mod are protection <em>for</em> a colony rather
 * than a denial <em>aimed at a player</em>, and neither is a permission:
 * <ul>
 *     <li>hostile mobs being refused a spawn inside a built building
 *     ({@code EventHandler#allowSpawn}); and</li>
 *     <li>the entity half of {@code turnoffexplosionsincolonies}, which spares citizens and livestock from
 *     blasts ({@code ColonyPermissionEventHandler#allowExplosionDamage}).</li>
 * </ul>
 * Nobody testing a build is being held up by a zombie failing to spawn in their warehouse, or by their own
 * citizens surviving a test charge; turning those off with this switch would make it worse at the job it exists
 * for, not better. Each has its own lever already — the {@code mob_griefing} and spawn gamerules for the first,
 * {@code /mc colony blastprotection} and {@code turnoffexplosionsincolonies} for the second. The command says so
 * in its own message rather than leaving the player to find out.
 * <p>
 * {@code EDIT_PERMISSIONS} is the one action the switch does not grant. Editing the rank table is administration,
 * not protection, and granting it to everyone would let a visitor make changes that outlive the switch — the one
 * effect of a testing toggle that could not be undone by turning it back on.
 */
public final class ColonyProtection
{
    /**
     * NBT tag the switch is saved under, declared here rather than in {@code NbtTagConstants} so that deleting
     * this file leaves nothing of the feature behind anywhere else.
     */
    public static final String TAG_PROTECTION = "colonyProtection";

    /**
     * Private constructor to hide the public one.
     */
    private ColonyProtection()
    {
        //Hides implicit constructor.
    }

    /**
     * Whether this colony has stopped enforcing permissions against players.
     * <p>
     * Server side only: every permission test that can deny a player runs on the server, and a client side view
     * never reaches the one call site. A view therefore answers "protection is on", which is the safe answer for
     * anything client side that might one day ask.
     *
     * @param colony the colony, may be null or a client side view.
     * @return true if the colony denies nobody anything.
     */
    public static boolean isOff(@Nullable final IColony colony)
    {
        return colony instanceof final Colony serverColony && !serverColony.isProtection();
    }
}
