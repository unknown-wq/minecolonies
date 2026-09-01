package com.unknownwq.worldmap.colony.minecolonies;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.unknownwq.worldmap.WorldMapClient;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Reads a guard tower's manual patrol route off its building view.
 *
 * <h2>Why this is its own class</h2>
 * <p>{@code AbstractBuildingGuards.View} is the only {@code com.minecolonies.core} type this mod names.
 * Everything else it touches is under {@code com.minecolonies.api}, which is the part of the mod that is
 * meant to be depended on; {@code core} is the implementation and is free to move. There is no api-side
 * route to the patrol list -- {@code IBuildingView} does not declare it -- and the mod's own
 * {@code core/client/render/worldevent/ColonyPatrolPointRenderer} reaches it exactly this way, with an
 * {@code instanceof} against the same class.</p>
 *
 * <p>So the reference is isolated here, in a class with one method, rather than sitting in the middle of
 * {@link MineColoniesOverlay}. The class this file names is resolved lazily, at the {@code instanceof}
 * instruction, and if a future MineColonies has moved or renamed it that resolution throws
 * {@link NoClassDefFoundError} -- which is caught below, latched, and costs the patrol layer and nothing
 * else. Had the {@code instanceof} been written inline in the overlay, the same failure would have been
 * thrown inside the snapshot rebuild every second for the rest of the session.</p>
 *
 * <p>The list read is the <b>manual</b> route: the positions set with the guard scepter, which is what
 * {@code AbstractBuildingGuards} serialises to the view. A tower left on automatic patrol has an empty list
 * here and nothing is drawn for it, which is correct -- there is no fixed route to draw.</p>
 */
@Environment(EnvType.CLIENT)
final class GuardPatrols
{
    /**
     * Cleared for good the first time reading a route fails. One warning, then the layer is simply absent.
     */
    private static boolean available = true;

    /**
     * @param view any building view.
     * @return the tower's manual patrol targets, or an empty list if it is not a guard tower, has no manual
     *     route, or the guard building class could not be resolved at all.
     */
    static List<BlockPos> targetsOf(final IBuildingView view)
    {
        if (!available || view == null)
        {
            return List.of();
        }

        try
        {
            if (view instanceof final AbstractBuildingGuards.View guard)
            {
                final List<BlockPos> targets = guard.getPatrolTargets();
                return targets == null ? List.of() : targets;
            }
            return List.of();
        }
        catch (final Throwable t)
        {
            available = false;
            WorldMapClient.LOGGER.warn("This MineColonies build does not expose guard patrol routes the way the map "
                                         + "reads them; the patrol layer will stay empty.", t);
            return List.of();
        }
    }

    private GuardPatrols()
    {
        /*
         * Intentionally left empty.
         */
    }
}
