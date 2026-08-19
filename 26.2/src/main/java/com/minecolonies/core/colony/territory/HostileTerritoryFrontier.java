package com.minecolonies.core.colony.territory;

import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.colony.territory.HostileTerritoryMap;
import com.minecolonies.api.util.MessageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a player, in one line of chat, that he has walked onto somebody's hostile ground — and that he has walked off
 * it again.
 *
 * <h2>Why this exists</h2>
 * A hostile territory is a red rectangle on screen and a heavy pathfinding surcharge underfoot, and neither of those
 * says <em>whose</em> ground it is or that you are now standing on it. The border scepter paints a name; nothing in the
 * game ever spoke it. This is signage and nothing more: no state on the colony, no packets, no saved data, no mechanic
 * hanging off it.
 *
 * <h2>The rule</h2>
 * One line when the territory under the player changes from nothing (or from another territory) to a territory, one
 * line when it changes back to nothing. Both are suppressed if the identical line was already sent to that player
 * within {@link #REPEAT_GRACE_TICKS}, which is what stops a player pacing his own border line from being told about it
 * every five seconds.
 *
 * <h2>Cost</h2>
 * Driven from {@code EventHandler#onEnteringChunk}, so it is reached once per player per chunk change, at most every
 * 100 game ticks. In a world with no hostile territory in the dimension the whole of {@link #onEnteringChunk} is one
 * {@link HostileTerritory#in} map lookup returning null and an early return — nothing is allocated and the per-player
 * map stays empty. With territories present it is one {@code long} hash probe plus, on the rare tick where the answer
 * actually changed, one {@code HashMap} write.
 */
public final class HostileTerritoryFrontier
{
    /**
     * How long the same line is held back before it may be said again, in game ticks.
     * <p>
     * 600 ticks is thirty seconds. The hook upstream only fires on a chunk change and only every 100 ticks, so without
     * this a player walking to and fro across one chunk boundary could be told twelve times a minute; with it, at most
     * once each way per half minute. Long enough to stop the pacing case being noise, short enough that genuinely
     * leaving and coming back later is still reported.
     */
    private static final int REPEAT_GRACE_TICKS = 600;

    /**
     * The territory each player was last known to be standing on, or {@link HostileTerritoryMap#NO_TERRITORY}.
     * <p>
     * Server thread only, in step with {@code EventHandler#playerPositions}, which has exactly this shape and lifetime.
     * Entries are dropped by {@link #forget} when the player disconnects, or this would hold a UUID for everyone who
     * ever logged in.
     */
    private static final Map<UUID, Integer> standingOn = new HashMap<>();

    /**
     * The last line said to each player, so an identical repeat can be held back. Same lifetime as
     * {@link #standingOn}.
     */
    private static final Map<UUID, Announcement> lastSaid = new HashMap<>();

    /**
     * Private constructor to hide the implicit one.
     */
    private HostileTerritoryFrontier()
    {
        // Intentionally left empty.
    }

    /**
     * Check whether the player has crossed into or out of a hostile territory, and say so if he has.
     *
     * @param level  the level the player is in.
     * @param player the player.
     */
    public static void onEnteringChunk(final ServerLevel level, final ServerPlayer player)
    {
        final HostileTerritoryMap index = HostileTerritory.in(level.dimension());
        if (index == null)
        {
            // No territory in this dimension. Whatever the player was last told about belonged to a dimension or a
            // territory that is gone, so drop the memory rather than announcing a departure from ground that no longer
            // exists.
            standingOn.remove(player.getUUID());
            return;
        }

        final BlockPos pos = player.blockPosition();
        final int now = index.owningTerritory(pos.getX(), pos.getZ());
        final Integer previousBoxed = standingOn.put(player.getUUID(), now);
        final int previous = previousBoxed == null ? HostileTerritoryMap.NO_TERRITORY : previousBoxed;

        if (now == previous)
        {
            return;
        }

        if (now != HostileTerritoryMap.NO_TERRITORY)
        {
            // Walking straight from one territory into another announces the arrival only. The departure is implied by
            // the arrival naming somewhere else, and two lines in one tick reads as a bug.
            say(player, TranslationConstants.ENTERED, now, index.name(now), level.getGameTime());
        }
        else
        {
            say(player, TranslationConstants.LEFT, previous, index.name(previous), level.getGameTime());
        }
    }

    /**
     * Drop everything remembered about a player, when he leaves the world.
     *
     * @param player the player.
     */
    public static void forget(final ServerPlayer player)
    {
        standingOn.remove(player.getUUID());
        lastSaid.remove(player.getUUID());
    }

    /**
     * Say one line, unless the very same line was already said recently.
     *
     * @param player      the player to tell.
     * @param key         the translation key, one of {@link TranslationConstants}.
     * @param territoryId the territory the line is about.
     * @param name        its name, or null if the index no longer knows it.
     * @param gameTime    the current game time.
     */
    private static void say(final ServerPlayer player, final String key, final int territoryId, final String name, final long gameTime)
    {
        if (name == null)
        {
            // A territory that has been deleted between two chunk changes. There is nothing to name, so say nothing.
            return;
        }

        final Announcement previous = lastSaid.get(player.getUUID());
        if (previous != null
              && previous.territoryId == territoryId
              && previous.key.equals(key)
              && gameTime - previous.gameTime < REPEAT_GRACE_TICKS)
        {
            return;
        }

        lastSaid.put(player.getUUID(), new Announcement(key, territoryId, gameTime));
        // Walking on is the warning and reads red; walking off is the all-clear and should not. Two lines that look
        // identical would make the second one feel like a second alarm.
        final MessageUtils.MessagePriority priority =
          key.equals(TranslationConstants.ENTERED) ? MessageUtils.MessagePriority.DANGER : MessageUtils.MessagePriority.IMPORTANT;
        MessageUtils.format(key, name).withPriority(priority).sendTo(player);
    }

    /**
     * One line already said to one player.
     *
     * @param key         the translation key that was used.
     * @param territoryId the territory it named.
     * @param gameTime    when it was said.
     */
    private record Announcement(String key, int territoryId, long gameTime) {}

    /**
     * The two lines this class can say.
     */
    public static final class TranslationConstants
    {
        /**
         * Translation key for having walked onto a hostile territory. One argument: the territory's name.
         */
        public static final String ENTERED = "com.minecolonies.core.colony.territory.entered";

        /**
         * Translation key for having walked off one. One argument: the territory's name.
         */
        public static final String LEFT = "com.minecolonies.core.colony.territory.left";

        /**
         * Private constructor to hide the implicit one.
         */
        private TranslationConstants()
        {
            // Intentionally left empty.
        }
    }
}
