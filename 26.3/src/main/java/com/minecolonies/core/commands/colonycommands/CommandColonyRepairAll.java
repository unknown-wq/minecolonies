package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderRequestResult;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCColonyOfficerCommand;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Files a repair work order for every building in the colony that can take one.
 * <p>
 * The hut GUI's repair button is one building at a time, which stops being usable somewhere around the twentieth hut.
 * This is the same button pressed on all of them: each building goes through
 * {@link IBuilding#requestRepair(BlockPos, boolean)} - the very method
 * {@code BuildRequestMessage} calls for {@code Mode.REPAIR} - with no builder named, so the work manager hands the
 * orders out exactly as it does for a repair asked for by hand.
 * <p>
 * The one difference from pressing the button sixty times is that the per building chat line is suppressed: the
 * sender gets counts and a list instead. Nothing else about the path is changed, and the refusals a single repair
 * would have announced are still counted and named here.
 * <p>
 * <b>What it leaves alone,</b> because a repair order for it would be a no-op or would undo something the player did:
 * <ul>
 *     <li>buildings at level 0 - there is nothing built to repair;</li>
 *     <li>buildings that already have a work order of any kind, including one being built, upgraded or taken down.
 *     {@code AbstractBuilding#requestWorkOrder} never makes a second order for the same hut anyway, so this only
 *     makes the reason visible;</li>
 *     <li>buildings flagged deconstructed. A repair order on one of those does not repair it, it <em>rebuilds</em> it
 *     - which is why the GUI relabels its repair button "Build" - and rebuilding what the player deliberately took
 *     down is not what "repair the colony" means.</li>
 * </ul>
 * <p>
 * <b>On queueing a lot of orders at once.</b> Nothing is capped. {@code WorkManager#onColonyTick} assigns at most one
 * order per builder hut and skips builders that already have one, so a hundred orders do not wedge anything: they sit
 * in the queue and are taken as builders come free, in work order id order. Two consequences worth knowing before
 * running it on a large town: every queued building is wrapped in construction tape straight away
 * ({@code WorkOrderBuilding#onAdded}), so the whole colony goes striped at once; and the builders will work through
 * the lot, consuming the materials each repair needs, until the queue is empty. Cancel individual orders from the
 * town hall's work order tab, or from each hut's own build button.
 */
public class CommandColonyRepairAll implements IMCColonyOfficerCommand
{
    /**
     * The dry run keyword: list what would be queued and change nothing.
     */
    private static final String PREVIEW_ARG = "preview";

    /**
     * How many buildings are named in chat before the list is cut off. The complete list always goes to the server log.
     */
    private static final int CHAT_BUILDING_CAP = 10;

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return run(context, false);
    }

    /**
     * Queue the repairs, or report which ones would be queued.
     *
     * @param context the command context.
     * @param preview true to change nothing.
     * @return 1 always: "nothing needed repairing" is a successful answer.
     */
    private int run(final CommandContext<CommandSourceStack> context, final boolean preview)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);

        // A stable order over the buildings, so two runs on the same colony consider them in the same sequence and
        // the work orders come out in the same order as well.
        final List<IBuilding> buildings = new ArrayList<>(colony.getServerBuildingManager().getBuildings().values());
        buildings.sort(Comparator.comparingInt((IBuilding b) -> b.getPosition().getX())
                         .thenComparingInt(b -> b.getPosition().getZ())
                         .thenComparingInt(b -> b.getPosition().getY()));

        final List<String> queued = new ArrayList<>();
        final Map<WorkOrderRequestResult, Integer> skipped = new EnumMap<>(WorkOrderRequestResult.class);
        final List<String> refused = new ArrayList<>();

        for (final IBuilding building : buildings)
        {
            WorkOrderRequestResult result;
            try
            {
                result = repair(building, preview);
            }
            catch (final RuntimeException e)
            {
                // A colony-wide command must not die on the first odd building: the other fifty-nine still want
                // their order. Whatever went wrong is logged with the building that caused it and counted as a skip.
                Log.getLogger().error("repairall: " + describe(building) + " threw while asking for a repair", e);
                result = WorkOrderRequestResult.FAILED;
            }
            if (result == WorkOrderRequestResult.QUEUED)
            {
                queued.add(describe(building));
                continue;
            }

            skipped.merge(result, 1, Integer::sum);
            if (isRefusal(result))
            {
                refused.add(describe(building) + " - " + reason(result).getString());
            }
        }

        report(context, colony, queued, skipped, refused, preview);
        return 1;
    }

    /**
     * Ask one building for a repair, or work out what asking would answer.
     *
     * @param building the building.
     * @param preview  true to change nothing.
     * @return the outcome.
     */
    private static WorkOrderRequestResult repair(@NotNull final IBuilding building, final boolean preview)
    {
        if (building.getBuildingLevel() <= 0)
        {
            return WorkOrderRequestResult.NOT_BUILT;
        }
        if (building.isDeconstructed())
        {
            // A repair order here rebuilds rather than repairs. Left to the player, who took it down on purpose.
            return WorkOrderRequestResult.DECONSTRUCTED;
        }
        if (building.isPendingConstruction())
        {
            return WorkOrderRequestResult.ALREADY_QUEUED;
        }

        if (preview)
        {
            // The blueprint check is a pure read, so a preview can give the same answer the real run would; on a
            // colony carrying huts with no recorded blueprint that is much the commonest refusal, and a preview that
            // hid it would be misleading. Everything past it is decided inside requestRepair, which cannot answer
            // without also acting - so a preview promises no more than "this one is a candidate", and the real run
            // may still refuse it for want of a builder and says so when it does.
            final String path = building.getBlueprintPath();
            return path == null || path.replace(".blueprint", "").isEmpty()
                     ? WorkOrderRequestResult.NO_BLUEPRINT
                     : WorkOrderRequestResult.QUEUED;
        }

        return building.requestRepair(BlockPos.ZERO, false);
    }

    /**
     * Whether an outcome is the colony refusing rather than the command declining to ask.
     *
     * @param result the outcome.
     * @return true if it is worth naming the building in the report.
     */
    private static boolean isRefusal(@NotNull final WorkOrderRequestResult result)
    {
        return switch (result)
        {
            case NO_BUILDER_GOOD_ENOUGH, NO_BUILDER_IN_RANGE, TOO_HIGH, TOO_LOW, ASSIGNED_BUILDER_REFUSED, NO_BLUEPRINT, FAILED -> true;
            default -> false;
        };
    }

    /**
     * The line that explains one outcome.
     *
     * @param result the outcome.
     * @return the component.
     */
    private static MutableComponent reason(@NotNull final WorkOrderRequestResult result)
    {
        return Component.translatable(switch (result)
        {
            case ALREADY_QUEUED -> COMMAND_COLONY_REPAIRALL_SKIP_WORKORDER;
            case NOT_BUILT -> COMMAND_COLONY_REPAIRALL_SKIP_NOTBUILT;
            case DECONSTRUCTED -> COMMAND_COLONY_REPAIRALL_SKIP_DECONSTRUCTED;
            case NO_BUILDER_GOOD_ENOUGH, ASSIGNED_BUILDER_REFUSED -> COMMAND_COLONY_REPAIRALL_SKIP_NOBUILDER;
            case NO_BUILDER_IN_RANGE -> COMMAND_COLONY_REPAIRALL_SKIP_TOOFAR;
            case TOO_HIGH, TOO_LOW -> COMMAND_COLONY_REPAIRALL_SKIP_OUTOFWORLD;
            case NO_BLUEPRINT -> COMMAND_COLONY_REPAIRALL_SKIP_NOBLUEPRINT;
            case CANNOT_DECONSTRUCT, FAILED, QUEUED -> COMMAND_COLONY_REPAIRALL_SKIP_OTHER;
        });
    }

    /**
     * Name one building the way the rest of the command tree does: display name and hut position.
     *
     * @param building the building.
     * @return the text.
     */
    private static String describe(@NotNull final IBuilding building)
    {
        return Component.translatable(building.getBuildingType().getTranslationKey()).getString()
                 + " " + building.getBuildingLevel() + " at " + building.getPosition().toShortString();
    }

    /**
     * Say what happened. Counts to chat, per building lines to chat up to a cap and to the server log in full.
     *
     * @param context the command context.
     * @param colony  the colony.
     * @param queued  the buildings a repair was queued for.
     * @param skipped how many buildings were passed over, by reason.
     * @param refused the buildings the colony itself turned down, with the reason.
     * @param preview whether anything was actually changed.
     */
    private static void report(
      final CommandContext<CommandSourceStack> context,
      @NotNull final IColony colony,
      @NotNull final List<String> queued,
      @NotNull final Map<WorkOrderRequestResult, Integer> skipped,
      @NotNull final List<String> refused,
      final boolean preview)
    {
        final StringBuilder log = new StringBuilder("\n");

        emit(context, log, Component.translatable(preview ? COMMAND_COLONY_REPAIRALL_PREVIEW : COMMAND_COLONY_REPAIRALL_HEADER,
          colony.getName(), colony.getID()), ChatFormatting.GOLD);

        int skippedTotal = 0;
        for (final int count : skipped.values())
        {
            skippedTotal += count;
        }

        emit(context, log, Component.translatable(preview ? COMMAND_COLONY_REPAIRALL_SUMMARY_PREVIEW : COMMAND_COLONY_REPAIRALL_SUMMARY,
          queued.size(), skippedTotal), queued.isEmpty() ? ChatFormatting.GREEN : null);

        if (queued.isEmpty())
        {
            emit(context, log, Component.translatable(COMMAND_COLONY_REPAIRALL_NOTHING), ChatFormatting.GREEN);
        }

        // One line per reason, so "why did it skip forty of them" is answered without reading the log.
        for (final Map.Entry<WorkOrderRequestResult, Integer> entry : skipped.entrySet())
        {
            emit(context, log, Component.translatable(COMMAND_COLONY_REPAIRALL_SKIPPED, entry.getValue(),
              reason(entry.getKey())), ChatFormatting.GRAY);
        }

        list(context, log, queued);
        list(context, log, refused);

        if (!queued.isEmpty() && !preview)
        {
            emit(context, log, Component.translatable(COMMAND_COLONY_REPAIRALL_TAPE), ChatFormatting.GRAY);
        }

        Log.getLogger().info(log.toString());
    }

    /**
     * Send a list of per building lines, capped in chat and complete in the log.
     *
     * @param context the command context.
     * @param log     the log being accumulated.
     * @param lines   the lines.
     */
    private static void list(
      final CommandContext<CommandSourceStack> context,
      @NotNull final StringBuilder log,
      @NotNull final List<String> lines)
    {
        for (int i = 0; i < lines.size(); i++)
        {
            final String line = "  " + lines.get(i);
            log.append(line).append('\n');

            if (i < CHAT_BUILDING_CAP)
            {
                final Component component = Component.literal(line);
                context.getSource().sendSuccess(() -> component, false);
            }
        }

        if (lines.size() > CHAT_BUILDING_CAP)
        {
            final Component more = Component.translatable(COMMAND_COLONY_REPAIRALL_MORE, lines.size() - CHAT_BUILDING_CAP)
                                     .withStyle(ChatFormatting.GRAY);
            context.getSource().sendSuccess(() -> more, false);
        }
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
      @NotNull final MutableComponent component,
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
        return "repairall";
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
