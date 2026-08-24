package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.Colony;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * What a colony's force-loading actually looks like on the running server, for the commands that report it.
 * <p>
 * Both {@code /mc colony chunkstatus} and {@code /mc colony forceloadclaims} have to answer the same three questions --
 * how much ground the colony owns, how much of it is really ticking, and whether the ceiling is quietly cutting the
 * answer short -- so they ask them here rather than each growing their own copy that can drift from the other.
 * <p>
 * Every number is read from the live server, never from a config alone: the claim from the colony manager's
 * per-dimension claim map (the same map the border renderer draws), and the ticking state from the level's simulation
 * chunk tracker, which is the only thing that decides whether a citizen standing there gets a tick at all.
 */
public final class ColonyChunkReport
{
    /**
     * How many chunks the colony owns.
     */
    public final int claimed;

    /**
     * How many of those are entity-ticking right now.
     */
    public final int ticking;

    /**
     * How many force-load tickets the colony holds. Not the same as {@link #claimed}: the upstream building rule can
     * ticket a chunk the colony does not own, and the ceiling can stop it owning one it does.
     */
    public final int ticketed;

    /**
     * {@code maxforcedchunks}, or 0 for no ceiling.
     */
    public final int cap;

    /**
     * Whether the colony is force-loading its whole claim right now.
     */
    public final boolean enabled;

    /**
     * The colony's own answer, or null while it follows the server config.
     */
    @Nullable
    public final Boolean override;

    /**
     * The server config value, i.e. what the colony does while it has no answer of its own.
     */
    public final boolean configDefault;

    /**
     * Total tickets held by every colony on the server that is force-loading its whole claim. The ceiling is per
     * colony, so colonies cannot starve each other of tickets -- but the server pays the sum, and the sum is the
     * number worth looking at before switching a third colony on.
     */
    public final int serverWideTickets;

    /**
     * How many colonies that sum is spread over.
     */
    public final int serverWideColonies;

    private ColonyChunkReport(
      final int claimed,
      final int ticking,
      final int ticketed,
      final int cap,
      final boolean enabled,
      @Nullable final Boolean override,
      final boolean configDefault,
      final int serverWideTickets,
      final int serverWideColonies)
    {
        this.claimed = claimed;
        this.ticking = ticking;
        this.ticketed = ticketed;
        this.cap = cap;
        this.enabled = enabled;
        this.override = override;
        this.configDefault = configDefault;
        this.serverWideTickets = serverWideTickets;
        this.serverWideColonies = serverWideColonies;
    }

    /**
     * Measure a colony.
     *
     * @param server the running server, for the colony's own level.
     * @param colony the colony.
     * @return the report.
     */
    public static ColonyChunkReport gather(final MinecraftServer server, final IColony colony)
    {
        // The level has to be the colony's own, not the caller's: chunk levels are per dimension, and asking the
        // overworld's tracker about a nether colony's chunks answers about entirely different ground.
        final ServerLevel colonyLevel = server.getLevel(colony.getDimension());
        final DistanceManager distanceManager =
          colonyLevel == null ? null : colonyLevel.getChunkSource().chunkMap.getDistanceManager();

        final Map<ChunkPos, IChunkClaimData> claims = IColonyManager.getInstance().getClaimData(colony.getDimension());
        int owned = 0;
        int ownedTicking = 0;
        for (final Map.Entry<ChunkPos, IChunkClaimData> entry : claims.entrySet())
        {
            if (entry.getValue() == null || entry.getValue().getOwningColony() != colony.getID())
            {
                continue;
            }

            owned++;
            if (distanceManager != null && distanceManager.inEntityTickingRange(entry.getKey().pack()))
            {
                ownedTicking++;
            }
        }

        int otherTickets = 0;
        int otherColonies = 0;
        for (final IColony other : IColonyManager.getInstance().getAllColonies())
        {
            if (other instanceof final Colony serverColony && serverColony.isForceLoadAllClaims())
            {
                otherTickets += serverColony.getTicketedChunks().size();
                otherColonies++;
            }
        }

        final boolean enabled = colony instanceof final Colony serverColony && serverColony.isForceLoadAllClaims();
        final Boolean override = colony instanceof final Colony serverColony ? serverColony.getForceLoadAllClaimsOverride() : null;

        return new ColonyChunkReport(owned,
          ownedTicking,
          colony.getTicketedChunks().size(),
          MineColonies.getConfig().getServer().maxForcedChunks.get(),
          enabled,
          override,
          MineColonies.getConfig().getServer().forceLoadAllClaims.get(),
          otherTickets,
          otherColonies);
    }

    /**
     * Whether the ceiling is stopping the colony covering ground it owns. Only meaningful while the whole-claim mode
     * is on; the upstream building rule never wanted every claimed chunk in the first place.
     *
     * @return true if there is claimed ground the colony is not allowed to ticket.
     */
    public boolean isTruncated()
    {
        return enabled && cap != 0 && claimed > cap;
    }

    /**
     * Which of the three states the colony is in, in words.
     *
     * @return "on (set for this colony)", "off (set for this colony)" or "on/off (server default)".
     */
    public String stateDescription()
    {
        if (override == null)
        {
            return (configDefault ? "on" : "off") + " (server default, not set for this colony)";
        }
        return (override ? "on" : "off") + " (set for this colony)";
    }

    /**
     * Print the whole report to a command source.
     *
     * @param source the command source.
     */
    public void send(final CommandSourceStack source)
    {
        final ColonyChunkReport report = this;
        source.sendSuccess(() -> Component.literal("Force-load whole claim: ")
          .append(Component.literal(report.stateDescription()).withStyle(report.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY)), true);
        source.sendSuccess(() -> Component.literal("Claimed chunks: ")
          .append(Component.literal("" + report.claimed).withStyle(ChatFormatting.YELLOW)), true);
        source.sendSuccess(() -> Component.literal("Claimed chunks entity-ticking: ")
          .append(Component.literal(report.ticking + " / " + report.claimed)
            .withStyle(report.ticking == report.claimed ? ChatFormatting.GREEN : ChatFormatting.YELLOW)), true);
        source.sendSuccess(() -> Component.literal("Tickets held: ")
          .append(Component.literal(report.ticketed + " / " + (report.cap == 0 ? "unlimited" : String.valueOf(report.cap)))
            .withStyle(ChatFormatting.YELLOW)), true);

        if (report.isTruncated())
        {
            source.sendSuccess(() -> Component.literal(
                "  maxforcedchunks (" + report.cap + ") is smaller than this colony's claim (" + report.claimed
                  + "). The " + report.cap + " chunks nearest the town hall are covered; the rest are not. Raise maxforcedchunks to cover them.")
              .withStyle(ChatFormatting.RED), true);
        }

        if (report.serverWideColonies > 1)
        {
            source.sendSuccess(() -> Component.literal(
                "  Server-wide: " + report.serverWideColonies + " colonies force-loading their claim, "
                  + report.serverWideTickets + " tickets between them. The ceiling is per colony, so they do not compete for it -- but the server ticks all of it.")
              .withStyle(ChatFormatting.GRAY), true);
        }
    }
}
