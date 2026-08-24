package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCColonyOfficerCommand;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Moves the citizens a colony already has into the house nearest their work.
 * <p>
 * {@link IRegisteredStructureManager#getHouseWithSpareBed(ICitizenData)} fixes where a citizen is housed <em>from now
 * on</em>. It does nothing for a colony that is already playing: the beds are taken, so nobody is ever offered a better
 * one, and a worker who got a bed on the far side of an enclave keeps it for good. This command is the pass that makes
 * an existing save converge on the rule, the same relationship the field claim range has with
 * {@code BuildingExtensionsModule#releaseDistantExtensions}.
 * <p>
 * It deliberately leaves alone anything a player decided by hand:
 * <ul>
 *     <li>a citizen living in a house on {@link HiringMode#LOCKED} is never moved out -- that mode is how the player
 *     says who lives where;</li>
 *     <li>a citizen only ever moves to a <em>strictly</em> nearer house, so two houses at the same distance can never
 *     trade a citizen back and forth;</li>
 *     <li>homeless citizens are housed, but nothing else about them is touched.</li>
 * </ul>
 * <p>
 * Running it twice does nothing the second time. Freeing a bed can make a better house available to somebody the pass
 * already looked at, so it repeats until a pass moves nobody rather than stopping after one sweep; every move strictly
 * shortens one citizen's walk to work, so the total distance falls with every move and the loop has to end.
 */
public class CommandColonyRehouse implements IMCColonyOfficerCommand
{
    /**
     * The dry run keyword. With it the command changes nothing and only reports.
     * <p>
     * It is worth having because this is the one command in the tree that rearranges a colony the player has been
     * building for weeks, and because "what would it do" is the question anybody sensibly asks first. It runs a single
     * pass: since nobody actually moves, a bed that two citizens both want is offered to both of them, so a preview can
     * name a move that the real run then gives to the nearer citizen instead. Treat the list as what the command is
     * aiming at, not as a promise.
     */
    private static final String PREVIEW_ARG = "preview";

    /**
     * Upper bound on convergence passes. Each pass that does anything strictly reduces the total distance from
     * citizens to their homes, so this cannot normally be hit; it only exists so that a bug somewhere in the housing
     * rule turns into a report rather than into a hung server thread.
     */
    private static final int MAX_PASSES = 32;

    /**
     * How many moves are named in chat before the list is cut off. The complete list always goes to the server log.
     */
    private static final int CHAT_MOVE_CAP = 10;

    /**
     * One citizen that the command moved, or would move.
     *
     * @param citizen the citizen.
     * @param from    the house it left, null when it had none.
     * @param to      the house it moved into.
     * @param before  the squared distance from its old home to its work, -1 when it had no home.
     * @param after   the squared distance from its new home to its work.
     */
    private record Move(ICitizenData citizen, IBuilding from, IBuilding to, double before, double after) {}

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return run(context, false);
    }

    /**
     * Rehouse, or report what rehousing would do.
     *
     * @param context the command context.
     * @param preview true to change nothing.
     * @return 1 always: "nothing to do" is a successful answer.
     */
    private int run(final CommandContext<CommandSourceStack> context, final boolean preview)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final IRegisteredStructureManager buildingManager = colony.getServerBuildingManager();

        // A stable order over the citizens, so two runs on the same colony consider them in the same sequence.
        final List<ICitizenData> citizens = new ArrayList<>(colony.getCitizenManager().getCitizens());
        citizens.sort(Comparator.comparingInt(ICitizenData::getId));

        // One entry per citizen that ends up somewhere else, in the order they were first moved. A later pass can move
        // the same citizen on again, in which case the entry is folded so the report says where it started and where
        // it ended rather than listing every hop.
        final Map<Integer, Move> moves = new LinkedHashMap<>();
        int passes = 0;
        boolean moved = true;
        while (moved && passes < MAX_PASSES)
        {
            moved = false;
            passes++;

            for (final ICitizenData citizen : citizens)
            {
                final Move move = citizen.isChild() ? findChildMove(colony, citizen) : findMove(buildingManager, citizen);
                if (move == null)
                {
                    continue;
                }

                moves.merge(citizen.getId(), move,
                  (first, latest) -> new Move(citizen, first.from(), latest.to(), first.before(), latest.after()));
                moved = true;

                if (preview)
                {
                    // Nobody actually moved, so a second pass would find the same move again. One pass it is.
                    continue;
                }

                apply(move);
            }

            if (preview)
            {
                break;
            }
        }

        report(context, colony, citizens, List.copyOf(moves.values()), preview, passes);
        return 1;
    }

    /**
     * Work out whether a citizen should move, without moving it.
     *
     * @param buildingManager the colony's building manager.
     * @param citizen         the citizen.
     * @return the move, or null if the citizen stays where it is.
     */
    private static Move findMove(@NotNull final IRegisteredStructureManager buildingManager, @NotNull final ICitizenData citizen)
    {
        final IBuilding current = citizen.getHomeBuilding();
        if (current != null && isLocked(current))
        {
            // The player pinned the residents of that house. Not ours to undo.
            return null;
        }

        final IBuilding target = buildingManager.getHouseWithSpareBed(citizen);
        if (target == null || (current != null && target.getPosition().equals(current.getPosition())))
        {
            return null;
        }

        final BlockPos anchor = buildingManager.getHousingAnchor(citizen);
        final double after = target.getPosition().distSqr(anchor);
        if (current == null)
        {
            return new Move(citizen, null, target, -1, after);
        }

        final double before = current.getPosition().distSqr(anchor);
        if (after >= before)
        {
            // Includes the equal case on purpose: a citizen never moves sideways, so two houses the same distance
            // away cannot pass it back and forth between runs.
            return null;
        }

        return new Move(citizen, current, target, before, after);
    }

    /**
     * Where a child should be living: with a parent.
     * <p>
     * 26.2/Fabric, and the migration path for every save written before the rule changed. A child used to be assigned
     * a bed like anybody else, and because the old birth code picked the house first and the parents second, most of
     * those children ended up in the tavern -- four beds, near the colony centre, so it won almost every time. Nothing
     * moves them on its own: the automatic housing pass deliberately leaves children alone, so without this they would
     * stay in the tavern for the rest of their childhood. This command is already the pass that makes an existing save
     * converge on the current housing rule, so it is where the convergence belongs.
     * <p>
     * A child whose parents are both dead or unhoused is left exactly where it is. That is not a failure -- it has a
     * roof and it costs the house nothing -- and moving it somewhere arbitrary would be worse than leaving it.
     *
     * @param colony the colony.
     * @param child  the child.
     * @return the move, or null if the child is already at a parent's or there is no parent to go to.
     */
    private static Move findChildMove(@NotNull final IColony colony, @NotNull final ICitizenData child)
    {
        final IBuilding current = child.getHomeBuilding();
        if (current != null && isLocked(current))
        {
            return null;
        }

        final IBuilding target = familyHome(colony, child);
        if (target == null || (current != null && target.getPosition().equals(current.getPosition())))
        {
            return null;
        }

        // Distance to work is meaningless for a child, so both figures are reported as "no previous distance"; the
        // report line for a child reads as a move from one house to another and nothing more.
        return new Move(child, current, target, -1, 0);
    }

    /**
     * The home of a parent of this child, if the colony still has one with a home.
     * <p>
     * Ids first, since those are exact. Names second, because a save written before parent ids stored nothing else,
     * and a name is what those children have -- ambiguous when two citizens share it, which is precisely the reason
     * ids were added, but better than abandoning the child in the tavern.
     *
     * @param colony the colony.
     * @param child  the child.
     * @return the parent's home, or null.
     */
    private static IBuilding familyHome(@NotNull final IColony colony, @NotNull final ICitizenData child)
    {
        for (final int id : new int[] {child.getParentIds().getA(), child.getParentIds().getB()})
        {
            if (id != ICitizenData.NO_PARENT)
            {
                final ICitizenData parent = colony.getCitizenManager().getCivilian(id);
                if (parent != null && parent.getHomeBuilding() != null)
                {
                    return parent.getHomeBuilding();
                }
            }
        }

        for (final String name : new String[] {child.getParents().getA(), child.getParents().getB()})
        {
            if (name.isEmpty())
            {
                continue;
            }
            for (final ICitizenData candidate : colony.getCitizenManager().getCitizensUnmodifiable())
            {
                if (!candidate.isChild() && candidate.getName().equals(name) && candidate.getHomeBuilding() != null)
                {
                    return candidate.getHomeBuilding();
                }
            }
        }
        return null;
    }

    /**
     * Carry out one move.
     * <p>
     * The assignment is the whole of it: {@code LivingBuildingModule#onAssignment} calls
     * {@code ICitizenData#setHomeBuilding}, which takes the citizen out of the old house's resident list, clears the
     * bed it had there and recalculates the colony's population cap. The citizen is woken first so that the bed it is
     * lying in is released and its block state stops reading as occupied - the sleep AI picks a bed by the resident's
     * index in the new house on the next night, and holds no other state across the move.
     *
     * @param move the move.
     */
    private static void apply(@NotNull final Move move)
    {
        move.citizen().getEntity().ifPresent(entity -> {
            if (entity.getCitizenSleepHandler().isAsleep())
            {
                entity.getCitizenSleepHandler().onWakeUp();
            }
        });

        move.to().getFirstModuleOccurance(LivingBuildingModule.class).assignCitizen(move.citizen());
    }

    /**
     * Whether a house has its residents pinned by the player.
     *
     * @param building the house.
     * @return true if it is locked.
     */
    private static boolean isLocked(@NotNull final IBuilding building)
    {
        if (!building.hasModule(LivingBuildingModule.class))
        {
            return false;
        }
        return HiringMode.LOCKED.equals(building.getFirstModuleOccurance(LivingBuildingModule.class).getHiringMode());
    }

    /**
     * Say what happened. The counts go to chat, the per citizen lines to chat up to a cap and to the server log in
     * full.
     *
     * @param context  the command context.
     * @param colony   the colony.
     * @param citizens every citizen of the colony.
     * @param moves    the moves made, or that would be made.
     * @param preview  whether anything was actually changed.
     * @param passes   how many convergence passes were run.
     */
    private static void report(
      final CommandContext<CommandSourceStack> context,
      @NotNull final IColony colony,
      @NotNull final List<ICitizenData> citizens,
      @NotNull final List<Move> moves,
      final boolean preview,
      final int passes)
    {
        int housed = 0;
        int homeless = 0;
        int locked = 0;
        for (final ICitizenData citizen : citizens)
        {
            final IBuilding home = citizen.getHomeBuilding();
            if (home == null)
            {
                homeless++;
            }
            else
            {
                housed++;
                if (isLocked(home))
                {
                    locked++;
                }
            }
        }

        final StringBuilder log = new StringBuilder("\n");
        final int moveCount = moves.size();

        emit(context, log, Component.translatable(preview ? COMMAND_COLONY_REHOUSE_PREVIEW : COMMAND_COLONY_REHOUSE_HEADER,
          colony.getName(), colony.getID()), ChatFormatting.GOLD);
        emit(context, log, Component.translatable(COMMAND_COLONY_REHOUSE_SUMMARY,
          moveCount, citizens.size() - moveCount, homeless, locked), moveCount == 0 ? ChatFormatting.GREEN : null);

        if (moveCount == 0)
        {
            emit(context, log, Component.translatable(COMMAND_COLONY_REHOUSE_NOTHING), ChatFormatting.GREEN);
        }

        for (int i = 0; i < moves.size(); i++)
        {
            final Move move = moves.get(i);
            // A child is not moved by distance to work at all -- it goes to a parent -- so the distance clause would
            // be meaningless noise on its line.
            final String line = move.citizen().isChild()
                                  ? String.format("  #%s %s: %s -> %s, to live with a parent",
                                    move.citizen().getId(),
                                    move.citizen().getName(),
                                    move.from() == null ? "homeless" : move.from().getPosition().toShortString(),
                                    move.to().getPosition().toShortString())
                                  : String.format("  #%s %s: %s -> %s, %s blocks from %s%s",
                                    move.citizen().getId(),
                                    move.citizen().getName(),
                                    move.from() == null ? "homeless" : move.from().getPosition().toShortString(),
                                    move.to().getPosition().toShortString(),
                                    (long) Math.sqrt(move.after()),
                                    move.citizen().getWorkBuilding() == null ? "the colony centre" : "work",
                                    move.before() < 0 ? "" : " instead of " + (long) Math.sqrt(move.before()));
            log.append(line).append('\n');

            if (i < CHAT_MOVE_CAP)
            {
                final Component component = Component.literal(line);
                context.getSource().sendSuccess(() -> component, false);
            }
        }

        if (moveCount > CHAT_MOVE_CAP)
        {
            final Component more = Component.translatable(COMMAND_COLONY_REHOUSE_MORE, moveCount - CHAT_MOVE_CAP).withStyle(ChatFormatting.GRAY);
            context.getSource().sendSuccess(() -> more, false);
        }

        if (passes >= MAX_PASSES)
        {
            emit(context, log, Component.translatable(COMMAND_COLONY_REHOUSE_NOT_SETTLED, MAX_PASSES), ChatFormatting.RED);
        }

        Log.getLogger().info(log.toString());
    }

    /**
     * Send one line to chat and record it for the log.
     *
     * @param context   the command context.
     * @param log       the log being accumulated.
     * @param component the line.
     * @param color     the chat colour, or null for the default.
     */
    private static void emit(
      final CommandContext<CommandSourceStack> context,
      @NotNull final StringBuilder log,
      @NotNull final net.minecraft.network.chat.MutableComponent component,
      final ChatFormatting color)
    {
        log.append(component.getString()).append('\n');
        final Component styled = color == null ? component : component.withStyle(color);
        context.getSource().sendSuccess(() -> styled, true);
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "rehouse";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .executes(this::checkPreConditionAndExecute)
                         .then(IMCCommand.newLiteral(PREVIEW_ARG)
                                 .executes(context -> checkPreConditionAndExecute(context, ctx -> run(ctx, true)))));
    }
}
