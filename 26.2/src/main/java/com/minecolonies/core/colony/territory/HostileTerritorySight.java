package com.minecolonies.core.colony.territory;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.colony.territory.HostileTerritoryMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Gets a hostile territory onto the screen of a player who is standing <em>next to</em> it rather than in it.
 *
 * <h2>The problem this solves</h2>
 * A client only knows about a colony it has been sent a view of, and views go to close subscribers, and a player
 * becomes a close subscriber of a colony by walking into a chunk that colony owns
 * ({@code EventHandler#onEnteringChunk}) and stops being one the moment he walks out
 * ({@code ColonyPackageManager#updateClosePlayers}). For an ordinary colony that is exactly right — you care about a
 * town when you are in it. For a territory it is precisely backwards: the whole point of marking enemy ground is
 * seeing where it is <b>from your own side of the line</b>, and the one place the rule guarantees you cannot see it
 * is the place you want to look from.
 * <p>
 * Left alone the red border would be there after you had walked through the enemy's land once, and gone again after
 * a relog — which is the worst kind of bug, because it works whenever you go looking for it.
 *
 * <h2>What it does instead</h2>
 * A territory keeps as its subscribers everyone within {@value #SIGHT_RADIUS} chunks of any of its ground, whether
 * they are standing on it or not. Both halves of the subscriber lifecycle ask this one question, so a player is
 * added when he comes into sight of enemy ground and dropped when he leaves it.
 *
 * <h2>Cost</h2>
 * The scan is {@code (2r+1)²} probes into the immutable index — 625 {@code long} hash lookups, no allocation, no
 * chunk touched — and it is only reached at all when {@link HostileTerritory#in} answers non-null, i.e. when the
 * dimension actually has a territory in it. A world without one pays one map lookup per player per chunk change.
 */
public final class HostileTerritorySight
{
    /**
     * How far from a territory a player still needs to see it, in chunks.
     * <p>
     * Twelve is 192 blocks, comfortably past the distance the border renderer draws at on a default render distance,
     * so the border is already on the client before it could be wanted. Larger would only add subscribers who cannot
     * see the lines anyway, and each subscriber costs a colony-view packet whenever the territory is repainted.
     */
    private static final int SIGHT_RADIUS = 12;

    /**
     * Private constructor to hide the implicit one.
     */
    private HostileTerritorySight()
    {
        // Intentionally left empty.
    }

    /**
     * Subscribe a player to every hostile territory within sight of where he is standing.
     * <p>
     * Called from the same place ordinary subscriptions are made, so a player walking about picks territories up and
     * the existing lifecycle does the rest.
     *
     * @param level  the level.
     * @param player the player.
     * @param at     the chunk the player has just entered.
     */
    public static void subscribeNearby(final ServerLevel level, final ServerPlayer player, final ChunkPos at)
    {
        final HostileTerritoryMap index = HostileTerritory.in(level.dimension());
        if (index == null)
        {
            return;
        }

        for (int x = at.x() - SIGHT_RADIUS; x <= at.x() + SIGHT_RADIUS; x++)
        {
            for (int z = at.z() - SIGHT_RADIUS; z <= at.z() + SIGHT_RADIUS; z++)
            {
                final int territory = index.chunkTerritory(x, z);
                if (territory == HostileTerritoryMap.NO_TERRITORY)
                {
                    continue;
                }

                final IColony colony = IColonyManager.getInstance().getColonyByWorld(territory, level);
                if (colony != null)
                {
                    // Idempotent: addCloseSubscriber does nothing for somebody already subscribed, so re-walking the
                    // same ground costs nothing beyond this scan.
                    colony.getPackageManager().addCloseSubscriber(player);
                }
            }
        }
    }

    /**
     * Whether a player is still close enough to a territory to need its border.
     * <p>
     * The counterpart of {@link #subscribeNearby}, asked where the ordinary rule would drop a subscriber for no longer
     * standing on the colony's own ground.
     *
     * @param level    the level.
     * @param at       the chunk the player is in.
     * @param colonyId the territory.
     * @return true if the player should keep receiving this territory's view.
     */
    public static boolean isInSight(final ServerLevel level, final ChunkPos at, final int colonyId)
    {
        final HostileTerritoryMap index = HostileTerritory.in(level.dimension());
        if (index == null)
        {
            return false;
        }

        for (int x = at.x() - SIGHT_RADIUS; x <= at.x() + SIGHT_RADIUS; x++)
        {
            for (int z = at.z() - SIGHT_RADIUS; z <= at.z() + SIGHT_RADIUS; z++)
            {
                if (index.chunkTerritory(x, z) == colonyId)
                {
                    return true;
                }
            }
        }
        return false;
    }
}
