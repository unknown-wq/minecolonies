package com.minecolonies.core.commands.generalcommands;

import com.minecolonies.api.compatibility.Compatibility;
import com.minecolonies.api.compatibility.simpleplanes.AircraftCompat;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.commands.colonycommands.CommandRaid;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_ADRIFT;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_ENTRY;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_FLOWN;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_NONE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_NO_MOD;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_ROUTE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_STRIKE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_SUMMARY;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_TP_NO_PLAYER;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_AIRCRAFT_TP_SUCCESS;
import static com.minecolonies.core.commands.CommandArgumentNames.AIRCRAFT_LOCATE;
import static com.minecolonies.core.commands.CommandArgumentNames.AIRCRAFT_TP;

/**
 * Finds the aircraft, and takes you somewhere you can watch one from.
 *
 * <h2>Why this exists</h2>
 * An air raid was reported as "the pirates appeared, but I never saw an aircraft". The transport is
 * real and is flown in from three hundred blocks out, but nothing in the game will tell a player
 * where it is, and until the aircraft mod's own tracking range was raised it was not even sent to a
 * client until it was almost overhead. This is the part that can be asked for: at any moment, what is
 * flying, what it is doing, and where.
 *
 * <h2>Shape</h2>
 * The same two verbs, and the same behaviour behind them, as {@code /mc colony raid <colony>
 * where|tp} and {@code /mc colony antiair <colony> where|tp}: {@code where} reports and changes
 * nothing, {@code tp} takes you to whatever the report would have pointed at, and the bare command
 * reports.
 *
 * <p><b>Not scoped to a colony</b>, which is the one place it departs from those two, and
 * deliberately. An aircraft does not belong to a colony — it is a thing in the world, and the
 * interesting one is usually the one that has not arrived at a colony yet. Scoping it would also make
 * the command useless for the case it was asked for, since a transport three hundred blocks out is
 * outside every radius a colony has. It sits at the top level next to {@code /mc whereami}, measures
 * everything from whoever typed it, and needs op like the rest of this family.
 *
 * <h2>Where the aircraft come from</h2>
 * {@link AircraftCompat#aircraft}, which reports every aircraft on a scripted plan in the level
 * whatever its distance, plus anything else within {@link #SEARCH_RADIUS} of the caller. The first
 * half is what makes this able to find an inbound transport while it is still on its way in: a
 * scripted run is known from the instant it is ordered, and no amount of scanning would find it that
 * early.
 *
 * <h2>With no aircraft mod</h2>
 * Everything comes through {@link AircraftCompat}, whose default implementation reports no aircraft at
 * all — so this could simply say "none found" and be technically true. It says something better
 * instead: {@link AircraftCompat#isPresent} distinguishes "no aircraft are flying" from "nothing in
 * this installation can fly", because those are different answers and only one of them means the
 * player should keep looking. No aircraft type is named here, which is the rule the whole compat
 * split rests on.
 */
public class CommandAircraft implements IMCOPCommand
{
    /**
     * How far from the caller to look for aircraft that are <em>not</em> on a scripted plan.
     *
     * <p>256 blocks, which comfortably covers the furthest distance at which anything in this
     * repository interacts with an aircraft — the anti-air battery's 200-block engagement range. A
     * scripted aircraft is not subject to this at all and is reported from any distance, so this bound
     * only ever applies to a plane somebody is flying by hand or one left lying in a field, and
     * neither of those is interesting a quarter of a mile away. The scan is over loaded chunks only,
     * so the unloaded majority of that radius costs nothing.
     */
    private static final int SEARCH_RADIUS = 256;

    /**
     * Bare {@code /mc aircraft}: report.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return onExecuteLocate(context);
    }

    /**
     * Says what is flying, what it is doing, and where.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteLocate(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        final BlockPos from = BlockPos.containing(source.getPosition());
        final List<AircraftCompat.AircraftReport> aircraft = find(source);

        if (aircraft.isEmpty())
        {
            source.sendSuccess(() -> Component.translatableEscape(
              Compatibility.aircraftCompat.isPresent() ? COMMAND_AIRCRAFT_NONE : COMMAND_AIRCRAFT_NO_MOD), true);
            return 0;
        }

        int scriptedCount = 0;
        for (final AircraftCompat.AircraftReport plane : aircraft)
        {
            if (plane.scripted())
            {
                scriptedCount++;
            }
        }

        final int scripted = scriptedCount;
        source.sendSuccess(() -> Component.translatableEscape(COMMAND_AIRCRAFT_SUMMARY, aircraft.size(), scripted), true);

        for (final AircraftCompat.AircraftReport plane : aircraft)
        {
            final BlockPos pos = BlockPos.containing(plane.position());
            source.sendSuccess(() -> Component.translatableEscape(COMMAND_AIRCRAFT_ENTRY,
              plane.type(),
              BlockPosUtil.calcDirection(from, pos).getLongText(),
              groundDistance(from, pos),
              CommandRaid.posToString(pos),
              pos.getY()), true);

            source.sendSuccess(() -> detail(plane), true);
        }
        return aircraft.size();
    }

    /**
     * Puts the player where the aircraft can be watched.
     *
     * <h2>Where exactly, and why</h2>
     * <b>On the ground directly beneath it.</b> The two obvious answers are both bad: inside the model
     * shows nothing but the inside of the model, and a point in open air beside it drops the player
     * seventy blocks. Standing under it is safe, needs no effect and no gamemode change, and puts the
     * aircraft at a distance equal to its own altitude — for a raid transport about seventy blocks,
     * comfortably inside the range at which an aircraft is now sent to a client at all. The player is
     * then turned to face it, so it is on screen on arrival rather than somewhere behind them.
     *
     * <p>If the aircraft is low or underground the surface may be at or above it; the player is put on
     * the surface regardless, which in that case simply means standing next to it. That is still the
     * right place to be and needs no special case.
     *
     * <h2>Which aircraft</h2>
     * The nearest one on a scripted plan, falling back to the nearest of all. A scripted aircraft is
     * the one that is doing something and the one a player cannot otherwise find — an aircraft
     * somebody is flying by hand already has somebody watching it.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteTeleport(final CommandContext<CommandSourceStack> context)
    {
        final CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player))
        {
            source.sendFailure(Component.translatableEscape(COMMAND_AIRCRAFT_TP_NO_PLAYER));
            return 0;
        }

        final List<AircraftCompat.AircraftReport> aircraft = find(source);
        if (aircraft.isEmpty())
        {
            source.sendSuccess(() -> Component.translatableEscape(
              Compatibility.aircraftCompat.isPresent() ? COMMAND_AIRCRAFT_NONE : COMMAND_AIRCRAFT_NO_MOD), true);
            return 0;
        }

        AircraftCompat.AircraftReport target = null;
        for (final AircraftCompat.AircraftReport plane : aircraft)
        {
            // The list is already nearest-first, so the first scripted one is the nearest scripted one.
            if (plane.scripted())
            {
                target = plane;
                break;
            }
        }
        if (target == null)
        {
            target = aircraft.get(0);
        }

        final AircraftCompat.AircraftReport chosen = target;
        final BlockPos above = BlockPos.containing(chosen.position());
        final ServerLevel level = player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        final BlockPos stand = level == null
                                 ? above
                                 : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, above);

        CommandRaid.teleport(player, stand);
        // Face it, so it is on screen rather than behind. After the teleport, or the rotation is
        // computed from where the player used to be.
        player.lookAt(EntityAnchorArgument.Anchor.EYES, chosen.position());

        source.sendSuccess(() -> Component.translatableEscape(COMMAND_AIRCRAFT_TP_SUCCESS,
          chosen.type(),
          Math.max(0, above.getY() - stand.getY()),
          CommandRaid.posToString(stand)), true);
        return 1;
    }

    /**
     * The aircraft, nearest first.
     *
     * @param source whoever typed the command; the search is centred on them.
     * @return the aircraft, possibly empty, never null.
     */
    private static List<AircraftCompat.AircraftReport> find(final CommandSourceStack source)
    {
        final ServerLevel level = source.getLevel();
        final BlockPos from = BlockPos.containing(source.getPosition());

        final List<AircraftCompat.AircraftReport> aircraft =
          new ArrayList<>(Compatibility.aircraftCompat.aircraft(level, from, SEARCH_RADIUS));
        aircraft.sort(Comparator.comparingDouble(plane -> plane.position().distanceToSqr(Vec3.atCenterOf(from))));
        return aircraft;
    }

    /**
     * The second line of an entry: what this aircraft is doing.
     *
     * @param plane the aircraft.
     * @return the line.
     */
    private static Component detail(final AircraftCompat.AircraftReport plane)
    {
        if (plane.scripted())
        {
            return Component.translatableEscape(plane.strike() ? COMMAND_AIRCRAFT_STRIKE : COMMAND_AIRCRAFT_ROUTE,
              CommandRaid.posToString(plane.destination()));
        }
        return Component.translatableEscape(plane.unmanned() ? COMMAND_AIRCRAFT_ADRIFT : COMMAND_AIRCRAFT_FLOWN);
    }

    /**
     * Distance over the ground, ignoring how high the aircraft is — the altitude is reported on its
     * own, and a straight-line distance to something seventy blocks up reads as further away than it
     * is worth walking.
     *
     * @param from where the caller is.
     * @param to   where the aircraft is.
     * @return the horizontal distance in blocks.
     */
    private static int groundDistance(final BlockPos from, final BlockPos to)
    {
        return (int) Math.sqrt(BlockPosUtil.getDistanceSquared2D(from, to));
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "aircraft";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newLiteral(AIRCRAFT_LOCATE).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteLocate)))
                 .then(IMCCommand.newLiteral(AIRCRAFT_TP).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteTeleport)))
                 .executes(this::checkPreConditionAndExecute);
    }
}
