package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.colonyEvents.EventStatus;
import com.minecolonies.api.colony.colonyEvents.IColonyEvent;
import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import com.minecolonies.api.colony.colonyEvents.registry.ColonyEventTypeRegistryEntry;
import com.minecolonies.api.colony.managers.interfaces.IRaiderManager;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.colony.territory.HostileTerritoryMap;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.translation.CommandTranslationConstants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.commands.arguments.MultiColonyIdArgument;
import com.minecolonies.core.commands.arguments.MultipleOptionsArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.colony.events.raid.RaidManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.*;

public class CommandRaid implements IMCOPCommand
{
    private static final DynamicCommandExceptionType ERROR_INVALID_COLONY_EVENT_TYPE =
        new DynamicCommandExceptionType(entry -> Component.translatableEscape("com.minecolonies.command.raid.colony_type.invalid", entry));

    /**
     * Bounds on the strength multiplier. One is the strength the colony would have faced anyway; the floor is low
     * enough to be a walkover and the ceiling high enough to be hopeless, which is the point of being able to set it.
     */
    private static final double MIN_STRENGTH = 0.1;
    private static final double MAX_STRENGTH = 10.0;

    /**
     * How far from the colony centre "stop" looks for raiders that are no longer attached to any event.
     */
    private static final int STRAGGLER_SWEEP_RANGE = 500;

    /**
     * How far from the colony centre "territory" will look for enemy ground to raid out of, in blocks.
     * <p>
     * Five hundred is the radius the ordinary spawn search throws its direction ray out to
     * ({@code RaidManager#calculateSpawnLocation}), so a raid out of a territory arrives from about as far away as one
     * that came off the circle. Further than this and the raiders would spend the night walking.
     */
    private static final int TERRITORY_RAID_RANGE = 500;

    /**
     * How many pieces of enemy ground, nearest first, are examined for somewhere to stand before settling for the best
     * found so far. Each probe is a {@code findAround} over a 30-block radius, which is the expensive part of this
     * command, so it is bounded rather than left to run over a large territory.
     */
    private static final int MAX_SPAWN_PROBES = 16;

    /**
     * Run the command with all fields including the raid type and ship set.
     *
     * @param ctx the command context.
     * @return the command status.
     */
    private int onExecuteWithType(final CommandContext<CommandSourceStack> ctx)
    {
        return checkPreConditionAndExecute(ctx, (context) -> {
            final String raidType = getRaidType(context);
            final boolean allowShips = BoolArgumentType.getBool(context, SHIP_ARG);
            return raidExecute(context, new IRaiderManager.RaidSettings(true, raidType, allowShips, null, null));
        });
    }

    /**
     * Run the command with all fields including the raider amount set.
     *
     * @param ctx the command context.
     * @return the command status.
     */
    private int onExecuteWithAmount(final CommandContext<CommandSourceStack> ctx)
    {
        return checkPreConditionAndExecute(ctx, (context) -> {
            final String raidType = getRaidType(context);
            final boolean allowShips = BoolArgumentType.getBool(context, SHIP_ARG);
            final int raidAmount = IntegerArgumentType.getInteger(context, RAID_AMOUNT_ARG);
            return raidExecute(context, new IRaiderManager.RaidSettings(true, raidType, allowShips, raidAmount, null));
        });
    }

    /**
     * Run the command with all fields including the location set.
     *
     * @param ctx the command context.
     * @return the command status.
     */
    private int onExecuteWithLocation(final CommandContext<CommandSourceStack> ctx)
    {
        return checkPreConditionAndExecute(ctx, (context) -> {
            final String raidType = getRaidType(context);
            final boolean allowShips = BoolArgumentType.getBool(context, SHIP_ARG);
            final int raidAmount = IntegerArgumentType.getInteger(context, RAID_AMOUNT_ARG);
            final BlockPos raidLocation = BlockPosArgument.getBlockPos(context, RAID_LOCATION_ARG);
            return raidExecute(context, new IRaiderManager.RaidSettings(true, raidType, allowShips, raidAmount, raidLocation));
        });
    }

    /**
     * Run the command with the raider count set, leaving the raid type to be picked as it normally is.
     *
     * @param ctx the command context.
     * @return the command status.
     */
    private int onExecuteWithSize(final CommandContext<CommandSourceStack> ctx)
    {
        return checkPreConditionAndExecute(ctx, (context) -> raidExecute(context,
          new IRaiderManager.RaidSettings(true, null, true, IntegerArgumentType.getInteger(context, RAID_AMOUNT_ARG), null, null, false)));
    }

    /**
     * Run the command with both the raider count and their strength set.
     *
     * @param ctx the command context.
     * @return the command status.
     */
    private int onExecuteWithSizeAndStrength(final CommandContext<CommandSourceStack> ctx)
    {
        return checkPreConditionAndExecute(ctx, (context) -> raidExecute(context,
          new IRaiderManager.RaidSettings(true,
            null,
            true,
            IntegerArgumentType.getInteger(context, RAID_AMOUNT_ARG),
            null,
            DoubleArgumentType.getDouble(context, RAID_STRENGTH_ARG),
            false)));
    }

    /**
     * Run the command with only the strength set, leaving the raider count to scale with it as it normally would.
     *
     * @param ctx the command context.
     * @return the command status.
     */
    private int onExecuteWithStrength(final CommandContext<CommandSourceStack> ctx)
    {
        return checkPreConditionAndExecute(ctx, (context) -> raidExecute(context,
          new IRaiderManager.RaidSettings(true, null, true, null, null, DoubleArgumentType.getDouble(context, RAID_STRENGTH_ARG), false)));
    }

    /**
     * Send a raid out of a hostile territory instead of off the usual circle around the colony.
     * <p>
     * This is a command-only door and deliberately nothing more: a raid the colony schedules for itself still picks
     * its spawn point the way it always has. All this does is find a spot inside the nearest painted enemy ground and
     * hand it to the raid manager as an explicit location, which is a path {@code RaidManager#raiderEvent} already has
     * (`raidSettings.location() != null`) and which skips the ordinary spawn search entirely. Because it is the same
     * spawn point field, everything downstream reports it with no work at all — {@code /mc raid <colony> where} prints
     * the direction and the coordinates, and the barracks window lists it among the spawn points.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteFromTerritory(final CommandContext<CommandSourceStack> context)
    {
        final String raidTime = StringArgumentType.getString(context, RAID_TIME_ARG);

        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            final TerritorySpawn spawn = findTerritorySpawn(colony);
            if (spawn.pos() == null)
            {
                context.getSource().sendFailure(Component.translatableEscape(spawn.message(), colony.getName(), TERRITORY_RAID_RANGE));
                continue;
            }

            final IRaiderManager.RaidSettings settings = new IRaiderManager.RaidSettings(true, null, true, null, spawn.pos());
            if (RAID_TONIGHT.equals(raidTime))
            {
                colony.getRaiderManager().setRaidNextNight(settings);
                context.getSource()
                    .sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_TERRITORY_TONIGHT, colony.getName(), spawn.territoryName()), true);
                continue;
            }

            final IRaiderManager.RaidSpawnResult result = colony.getRaiderManager().raiderEvent(settings.withImmediateStart());
            if (result == IRaiderManager.RaidSpawnResult.SUCCESS)
            {
                context.getSource()
                    .sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_TERRITORY_SUCCESS,
                      colony.getName(),
                      spawn.territoryName(),
                      posToString(spawn.pos()),
                      BlockPosUtil.calcDirection(colony.getCenter(), spawn.pos()).getLongText()), true);
            }
            else
            {
                context.getSource().sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_RAID_NOW_FAILURE, colony.getName(), result));
            }
        }
        return 1;
    }

    /**
     * Find somewhere inside the nearest hostile territory for a raid to come out of.
     * <p>
     * The territory index is asked chunk by chunk over a box around the colony rather than iterated, because it is a
     * point-query structure by design and there is no listing on it; that is {@code (2*31+1)²} long hash probes, once,
     * when a player types the command. Nothing is loaded to answer it — the index knows about ground nobody has ever
     * visited — which is exactly why the loaded check below is a separate and necessary step: spawning a raid in an
     * unloaded chunk produces the "raid bar with no raiders under it" state that {@code COMMANDS.md} documents, so it
     * is refused with a message rather than done.
     *
     * @param colony the colony to raid.
     * @return the spawn, whose position is null when there is nothing to raid out of and whose message then says why.
     */
    private static TerritorySpawn findTerritorySpawn(final IColony colony)
    {
        final HostileTerritoryMap territory = HostileTerritory.in(colony.getDimension());
        if (territory == null || colony.getWorld() == null)
        {
            return new TerritorySpawn(null, "", COMMAND_RAID_TERRITORY_NONE);
        }

        final BlockPos centre = colony.getCenter();
        final int radius = TERRITORY_RAID_RANGE / 16;
        final int centreChunkX = centre.getX() >> 4;
        final int centreChunkZ = centre.getZ() >> 4;

        boolean sawTerritory = false;
        final List<BlockPos> candidates = new ArrayList<>();
        int nearestTerritory = HostileTerritoryMap.NO_TERRITORY;
        long nearestDistance = Long.MAX_VALUE;

        for (int dz = -radius; dz <= radius; dz++)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                final int owner = territory.chunkTerritory(centreChunkX + dx, centreChunkZ + dz);
                if (owner == HostileTerritoryMap.NO_TERRITORY)
                {
                    continue;
                }
                sawTerritory = true;

                final BlockPos candidate = new BlockPos(((centreChunkX + dx) << 4) + 8, centre.getY(), ((centreChunkZ + dz) << 4) + 8);
                if (territory.owningTerritory(candidate) == HostileTerritoryMap.NO_TERRITORY)
                {
                    // A chunk the player only painted the corner of; its middle is not actually enemy ground.
                    continue;
                }
                if (!WorldUtil.isEntityBlockLoaded(colony.getWorld(), candidate))
                {
                    continue;
                }

                candidates.add(candidate);
                final long distance = BlockPosUtil.getDistanceSquared2D(centre, candidate);
                if (distance < nearestDistance)
                {
                    nearestDistance = distance;
                    nearestTerritory = owner;
                }
            }
        }

        if (candidates.isEmpty())
        {
            return new TerritorySpawn(null, "", sawTerritory ? COMMAND_RAID_TERRITORY_UNLOADED : COMMAND_RAID_TERRITORY_TOO_FAR);
        }

        final String name = territory.name(nearestTerritory) == null ? "" : territory.name(nearestTerritory);
        candidates.sort(java.util.Comparator.comparingLong(pos -> BlockPosUtil.getDistanceSquared2D(centre, pos)));

        // Nearest first, but dry land wins over sea. Not squeamishness: a spawn point standing in water makes
        // RaidManager reclassify the raid as a drowned pirate raid one time in five, and if no ship will fit there it
        // then has no branch left to take and answers NO_SPAWN_POINT. Coming out of an enemy's coast is a perfectly
        // good raid -- the ship branches handle it -- but it should not be preferred to the beach next to it.
        BlockPos wet = null;
        for (int i = 0; i < candidates.size() && i < MAX_SPAWN_PROBES; i++)
        {
            final BlockPos floor = BlockPosUtil.getFloor(candidates.get(i), colony.getWorld());
            final BlockPos standing = BlockPosUtil.findAround(colony.getWorld(), floor, 30, 3, BlockPosUtil.SOLID_AIR_POS_SELECTOR);
            if (standing == null)
            {
                continue;
            }
            if (!colony.getWorld().getBlockState(standing).liquid())
            {
                return new TerritorySpawn(standing, name, COMMAND_RAID_TERRITORY_SUCCESS);
            }
            if (wet == null)
            {
                wet = standing;
            }
        }

        if (wet != null)
        {
            return new TerritorySpawn(wet, name, COMMAND_RAID_TERRITORY_SUCCESS);
        }

        // Same selector the ordinary spawn search uses, so "nowhere to stand" means the same thing here as there.
        return new TerritorySpawn(null, name, COMMAND_RAID_TERRITORY_NO_FOOTING);
    }

    /**
     * Where a territory raid would come from, or why it cannot.
     *
     * @param pos           the spawn point, null when there is none.
     * @param territoryName the name the player gave the territory, empty when unknown.
     * @param message       the translation key to report, whether that is the success or the refusal.
     */
    private record TerritorySpawn(@Nullable BlockPos pos, String territoryName, String message) {}

    /**
     * Internal method for processing the raid type.
     *
     * @param context the command context.
     * @return the raid type.
     * @throws CommandSyntaxException if something goes wrong with the command processing.
     */
    private String getRaidType(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        final Identifier raidType =
            ResourceKeyArgument.resolveKey(context, RAID_TYPE_ARG, CommonMinecoloniesAPIImpl.COLONY_EVENT_TYPES, ERROR_INVALID_COLONY_EVENT_TYPE).key().identifier();
        final ColonyEventTypeRegistryEntry colonyEventTypeRegistryEntry = IMinecoloniesAPI.getInstance().getColonyEventRegistry().getValue(raidType);
        if (colonyEventTypeRegistryEntry != null && colonyEventTypeRegistryEntry.isRaidEvent())
        {
            return raidType.getPath();
        }
        throw ERROR_INVALID_COLONY_EVENT_TYPE.create(raidType);
    }

    @Override
    public final LiteralArgumentBuilder<CommandSourceStack> build()
    {
        final List<String> raidTimes = List.of(RAID_NOW, RAID_TONIGHT);
        final List<Identifier> raidTypes = new ArrayList<>();
        for (final ColonyEventTypeRegistryEntry colonyEventType : IMinecoloniesAPI.getInstance().getColonyEventRegistry())
        {
            if (colonyEventType.isRaidEvent())
            {
                raidTypes.add(colonyEventType.getRegistryName());
            }
        }

        final RequiredArgumentBuilder<CommandSourceStack, Coordinates> raidLocationArg =
            IMCCommand.newArgument(RAID_LOCATION_ARG, BlockPosArgument.blockPos()).executes(this::onExecuteWithLocation);
        final RequiredArgumentBuilder<CommandSourceStack, Integer> raidAmountArg =
            IMCCommand.newArgument(RAID_AMOUNT_ARG, IntegerArgumentType.integer(1)).executes(this::onExecuteWithAmount).then(raidLocationArg);
        final RequiredArgumentBuilder<CommandSourceStack, Boolean> raidShipArg =
            IMCCommand.newArgument(SHIP_ARG, BoolArgumentType.bool()).executes(this::onExecuteWithType).then(raidAmountArg);
        final RequiredArgumentBuilder<CommandSourceStack, ResourceKey<ColonyEventTypeRegistryEntry>> raidTypeArg =
            IMCCommand.newArgument(RAID_TYPE_ARG, ResourceKeyArgument.key(CommonMinecoloniesAPIImpl.COLONY_EVENT_TYPES))
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(raidTypes, builder))
                .then(raidShipArg);
        // "size <count> [<strength>]" and "strength <strength>", so the two things you usually want to dial in are
        // reachable without having to name a raid type and answer the ship question first.
        final RequiredArgumentBuilder<CommandSourceStack, Double> sizeStrengthArg =
            IMCCommand.newArgument(RAID_STRENGTH_ARG, DoubleArgumentType.doubleArg(MIN_STRENGTH, MAX_STRENGTH)).executes(this::onExecuteWithSizeAndStrength);
        final RequiredArgumentBuilder<CommandSourceStack, Integer> sizeArg =
            IMCCommand.newArgument(RAID_AMOUNT_ARG, IntegerArgumentType.integer(1)).executes(this::onExecuteWithSize).then(sizeStrengthArg);
        final LiteralArgumentBuilder<CommandSourceStack> sizeLiteral = IMCCommand.newLiteral(RAID_SIZE).then(sizeArg);

        final RequiredArgumentBuilder<CommandSourceStack, Double> strengthArg =
            IMCCommand.newArgument(RAID_STRENGTH_ARG, DoubleArgumentType.doubleArg(MIN_STRENGTH, MAX_STRENGTH)).executes(this::onExecuteWithStrength);
        final LiteralArgumentBuilder<CommandSourceStack> strengthLiteral = IMCCommand.newLiteral(RAID_STRENGTH).then(strengthArg);

        // "territory": come out of the enemy's ground rather than off the usual circle. A leaf of its own, next to
        // size and strength, because it takes no argument and does not combine with the raid type's ship question.
        final LiteralArgumentBuilder<CommandSourceStack> territoryLiteral =
            IMCCommand.newLiteral(RAID_TERRITORY).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteFromTerritory));

        final RequiredArgumentBuilder<CommandSourceStack, String> raidTimeArg = IMCCommand.newArgument(RAID_TIME_ARG, StringArgumentType.string())
            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(raidTimes, builder))
            .executes(this::checkPreConditionAndExecute)
            .then(sizeLiteral)
            .then(strengthLiteral)
            .then(territoryLiteral)
            .then(raidTypeArg);

        final LiteralArgumentBuilder<CommandSourceStack> locateLiteral = IMCCommand.newLiteral(RAID_LOCATE).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteLocate));
        final LiteralArgumentBuilder<CommandSourceStack> tpLiteral = IMCCommand.newLiteral(RAID_TP).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteTeleport));
        final LiteralArgumentBuilder<CommandSourceStack> stopLiteral = IMCCommand.newLiteral(RAID_STOP).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteStop));

        final RequiredArgumentBuilder<CommandSourceStack, MultipleOptionsArgument.OptionContainer<List<Integer>>> colonyIdArg =
            IMCCommand.newArgument(COLONYID_ARG, MultiColonyIdArgument.id()).then(raidTimeArg).then(locateLiteral).then(tpLiteral).then(stopLiteral);
        final RequiredArgumentBuilder<CommandSourceStack, String> allColoniesArg =
            IMCCommand.newArgument(COLONYID_ARG, StringArgumentType.string()).then(raidTimeArg).then(locateLiteral).then(tpLiteral).then(stopLiteral);

        return IMCCommand.newLiteral(getName()).then(colonyIdArg).then(allColoniesArg);
    }

    @Override
    public final int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return raidExecute(context, new IRaiderManager.RaidSettings(true, null, true, null, null));
    }

    /**
     * Actually find the colony and assign the raid event.
     *
     * @param context      command context from the user.
     * @param raidSettings type of raid, or "" if determining naturally.
     * @return zero if failed, one if successful.
     */
    private int raidExecute(final CommandContext<CommandSourceStack> context, final IRaiderManager.RaidSettings raidSettings)
    {
        final String raidTime = StringArgumentType.getString(context, RAID_TIME_ARG);

        return switch (raidTime)
        {
            case RAID_NOW -> startRaidNow(context, raidSettings);
            case RAID_TONIGHT -> startRaidTonight(context, raidSettings);
            default -> 0;
        };
    }

    /**
     * Handler for stating a raid right now.
     *
     * @param context      the command context.
     * @param raidSettings the raid settings.
     * @return the command status.
     */
    private int startRaidNow(final CommandContext<CommandSourceStack> context, final IRaiderManager.RaidSettings raidSettings)
    {
        final List<IColony> colonies = MultiColonyIdArgument.getColonies(context, COLONYID_ARG);
        for (final IColony colony : colonies)
        {
            final IRaiderManager.RaidSpawnResult result = colony.getRaiderManager().raiderEvent(raidSettings.withImmediateStart());

            if (result == IRaiderManager.RaidSpawnResult.SUCCESS)
            {
                final RaidManager.RaidHistory raid = ((RaidManager) colony.getRaiderManager()).getLastRaid();
                final int raiders = raid == null ? 0 : raid.raiderAmount;
                final double strength = raid == null ? 1.0 : raid.difficulty;
                context.getSource()
                    .sendSuccess(() -> Component.translatableEscape(CommandTranslationConstants.COMMAND_RAID_NOW_SUCCESS_DETAIL,
                      colony.getName(),
                      raiders,
                      String.format(Locale.ROOT, "%.2f", strength)), true);
            }
            else
            {
                context.getSource().sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_RAID_NOW_FAILURE, colony.getName(), result));
            }
        }
        return 1;
    }

    /**
     * Handler for stating a raid right tonight.
     *
     * @param context      the command context.
     * @param raidSettings the raid settings.
     * @return the command status.
     */
    private int startRaidTonight(final CommandContext<CommandSourceStack> context, final IRaiderManager.RaidSettings raidSettings)
    {
        final List<IColony> colonies = MultiColonyIdArgument.getColonies(context, COLONYID_ARG);
        for (final IColony colony : colonies)
        {
            colony.getRaiderManager().setRaidNextNight(raidSettings);
            context.getSource().sendSuccess(() -> Component.translatableEscape(CommandTranslationConstants.COMMAND_RAID_TONIGHT_SUCCESS, colony.getName()), true);
        }
        return 1;
    }

    /**
     * Say where the raiders of a colony are. A raid spawns a few hundred blocks out and walks in, and only the part of
     * it standing in a loaded chunk exists as entities at any moment, so "the bar says they are there but I cannot find
     * them" is the normal state of affairs rather than a fault.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteLocate(final CommandContext<CommandSourceStack> context)
    {
        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            final List<IColonyRaidEvent> raids = getActiveRaids(colony);
            if (raids.isEmpty())
            {
                context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_LOCATE_NONE, colony.getName()), true);
                continue;
            }

            int loadedTotal = 0;
            int aliveTotal = 0;
            for (final IColonyRaidEvent raid : raids)
            {
                loadedTotal += raid.getEntities().size();
                aliveTotal += raid.getRemainingRaiderCount();
            }

            final int loaded = loadedTotal;
            final int alive = aliveTotal;
            context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_LOCATE_SUMMARY, colony.getName(), raids.size(), alive, loaded), true);

            for (final IColonyRaidEvent raid : raids)
            {
                final BlockPos spawn = raid.getSpawnPos();
                context.getSource()
                    .sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_LOCATE_RAID,
                      raid.getEventTypeID().getPath(),
                      BlockPosUtil.calcDirection(colony.getCenter(), spawn).getLongText(),
                      raid.getRemainingRaiderCount(),
                      raid.getEntities().size(),
                      posToString(spawn)), true);

                final Entity closest = closestRaider(raid, colony.getCenter());
                if (closest == null)
                {
                    context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_LOCATE_NOT_LOADED), true);
                }
                else
                {
                    final BlockPos pos = closest.blockPosition();
                    context.getSource()
                        .sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_LOCATE_NEAREST,
                          (int) Math.sqrt(BlockPosUtil.getDistanceSquared2D(colony.getCenter(), pos)),
                          posToString(pos)), true);
                }
            }
        }
        return 1;
    }

    /**
     * Teleport to the raiders, since walking out to look for them in the right direction only works if they have not
     * already walked past you.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteTeleport(final CommandContext<CommandSourceStack> context)
    {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player))
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_RAID_TP_NO_PLAYER));
            return 0;
        }

        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            final List<IColonyRaidEvent> raids = getActiveRaids(colony);
            if (raids.isEmpty())
            {
                context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_LOCATE_NONE, colony.getName()), true);
                continue;
            }

            Entity target = null;
            for (final IColonyRaidEvent raid : raids)
            {
                final Entity candidate = closestRaider(raid, colony.getCenter());
                if (candidate != null && (target == null
                                            || BlockPosUtil.getDistanceSquared2D(colony.getCenter(), candidate.blockPosition())
                                                 < BlockPosUtil.getDistanceSquared2D(colony.getCenter(), target.blockPosition())))
                {
                    target = candidate;
                }
            }

            if (target != null)
            {
                final BlockPos pos = target.blockPosition();
                teleport(player, pos.above());
                context.getSource()
                    .sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_TP_SUCCESS,
                      (int) Math.sqrt(BlockPosUtil.getDistanceSquared2D(colony.getCenter(), pos)),
                      posToString(pos)), true);
            }
            else
            {
                // Nothing is loaded, so the spawn point is the only place worth standing: the event respawns whatever
                // it lost there as soon as the chunks are loaded, which arriving does.
                final BlockPos spawn = raids.get(0).getSpawnPos();
                teleport(player, spawn.above());
                context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_TP_SPAWN_POINT, posToString(spawn)), true);
            }
            return 1;
        }
        return 1;
    }

    /**
     * Call off the raids of a colony and clear the raiders away.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteStop(final CommandContext<CommandSourceStack> context)
    {
        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            final List<IColonyRaidEvent> raids = getActiveRaids(colony);
            if (raids.isEmpty())
            {
                context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_LOCATE_NONE, colony.getName()), true);
                continue;
            }

            int removed = 0;
            for (final IColonyRaidEvent raid : raids)
            {
                removed += raid.getEntities().size();
                raid.setStatus(EventStatus.DONE);
            }

            // Runs the event manager's finishing pass now rather than up to 500 ticks from now, which is what actually
            // discards the raiders and puts back whatever the raid built.
            colony.getEventManager().onColonyTick(colony);

            // Anything that outlived its event - a raider whose chunk was unloaded while the event was cleared, most
            // likely - is not on any list to be removed by that, so sweep the area for stragglers as well.
            if (colony.getWorld() != null)
            {
                final AABB area = new AABB(colony.getCenter()).inflate(STRAGGLER_SWEEP_RANGE);
                for (final AbstractEntityMinecoloniesRaider raider : colony.getWorld().getEntitiesOfClass(AbstractEntityMinecoloniesRaider.class, area))
                {
                    if (raider.getColony() == null || raider.getColony().getID() == colony.getID())
                    {
                        raider.remove(Entity.RemovalReason.DISCARDED);
                        removed++;
                    }
                }
            }

            final int total = removed;
            context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_RAID_STOP_SUCCESS, raids.size(), colony.getName(), total), true);
        }
        return 1;
    }

    /**
     * All raids of a colony that have not finished yet.
     *
     * @param colony the colony to look at.
     * @return the raids.
     */
    private static List<IColonyRaidEvent> getActiveRaids(final IColony colony)
    {
        final List<IColonyRaidEvent> raids = new ArrayList<>();
        if (colony.getEventManager() == null)
        {
            return raids;
        }

        for (final IColonyEvent event : colony.getEventManager().getEvents().values())
        {
            if (event instanceof IColonyRaidEvent raid && raid.getStatus() != EventStatus.DONE && raid.getStatus() != EventStatus.CANCELED)
            {
                raids.add(raid);
            }
        }
        return raids;
    }

    /**
     * The raider of a raid standing closest to a position, of the ones currently loaded.
     *
     * @param raid the raid to look through.
     * @param from the position to measure from.
     * @return the closest raider, or null when none of them is loaded.
     */
    private static Entity closestRaider(final IColonyRaidEvent raid, final BlockPos from)
    {
        Entity closest = null;
        for (final Entity entity : raid.getEntities())
        {
            if (!entity.isAlive())
            {
                continue;
            }
            if (closest == null
                  || BlockPosUtil.getDistanceSquared2D(from, entity.blockPosition()) < BlockPosUtil.getDistanceSquared2D(from, closest.blockPosition()))
            {
                closest = entity;
            }
        }
        return closest;
    }

    /**
     * Puts a player at a position in their current dimension.
     *
     * <p>Public rather than private because there is nothing raid specific about it and three commands
     * now want it: {@link CommandColonyAntiAir} for a guard tower and {@code CommandAircraft}, which
     * lives outside this package, for an aircraft. A second copy would only be a second place to forget
     * the chunk ticket.
     *
     * @param player the player to move.
     * @param pos    where to put them.
     */
    public static void teleport(final ServerPlayer player, final BlockPos pos)
    {
        player.level().getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(pos), 1);
        player.stopRiding();
        if (player.isSleeping())
        {
            player.stopSleepInBed(true, true);
        }
        player.teleportTo(player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), false);
    }

    /**
     * Formats a position the way a player would type it into a teleport. Public for the same reason as
     * {@link #teleport}: every command in this family quotes coordinates and they must all look alike.
     *
     * @param pos the position.
     * @return the text.
     */
    public static String posToString(final BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @Override
    public String getName()
    {
        return "raid";
    }
}
