package com.minecolonies.core.commands.colonycommands;


// PORT-NOTE(structurize): written against the real Structurize 26.3 API. Kept as a grep marker for files that
// touch Structurize, not as an open TODO.

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.operations.PlaceStructureOperation;
import com.ldtteam.structurize.placement.BlockPlacementResult;
import com.ldtteam.structurize.placement.IBlueprintIterator.TriPredicate;
import com.ldtteam.structurize.placement.StructurePhasePlacementResult;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.BlueprintPositionInfo;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import com.minecolonies.api.util.CreativeBuildingStructureHandler;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.eventhooks.buildingEvents.BuildingBuiltEvent;
import com.minecolonies.core.colony.eventhooks.buildingEvents.BuildingDeconstructedEvent;
import com.minecolonies.core.colony.eventhooks.buildingEvents.BuildingRepairedEvent;
import com.minecolonies.core.colony.eventhooks.buildingEvents.BuildingUpgradedEvent;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import com.minecolonies.core.colony.workorders.WorkOrderDecoration;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure;
import com.minecolonies.core.tileentities.TileEntityDecorationController;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.ldtteam.structurize.placement.AbstractBlueprintIterator.NULL_POS;
import static com.minecolonies.api.util.constant.Constants.STORAGE_STYLE;
import static com.minecolonies.api.util.constant.StatisticsConstants.*;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Finishes the colony's open work orders on the spot: no builder, no walking, no materials.
 * <p>
 * A builder takes something like twenty minutes of server time to raise a level one hut, which is time every test
 * that needs a <em>built</em> colony has to spend before it can begin. This is the same construction with the waiting
 * taken out, and it is deliberately the same construction rather than a second one: the blocks go down through
 * Structurize's own instant placement path and the colony side is closed through the very calls the builder's AI
 * makes when it finishes.
 *
 * <h2>How a work order is finished</h2>
 * <ol>
 *     <li><b>The blueprint</b> is loaded synchronously - {@code StructurePacks#getBlueprint} rather than the AI's
 *     {@code loadBlueprint}, whose future would only be read on some later tick - and handed to the work order, which
 *     rotates and mirrors it exactly as it does for the builder.</li>
 *     <li><b>The blocks</b> go down through {@link PlaceStructureOperation}, the operation the build tool queues for a
 *     creative paste, over a {@link CreativeBuildingStructureHandler}, the handler that registers what it places with
 *     the colony's building. The only difference from a paste is that the operation is driven to completion here
 *     instead of one tick's worth at a time: {@code apply} answers false until the last of its five phases is done, so
 *     the whole structure is standing when the command returns. For a REMOVE order the two removal phases of
 *     {@code AbstractEntityAIStructure} are run instead, with the builder's own
 *     {@link AbstractEntityAIStructure#skipRemoval} deciding what is left standing.</li>
 *     <li><b>The work order</b> is closed through {@code IWorkOrder#onCompleted}, which is what
 *     {@code AbstractBuildingStructureBuilder#complete} calls: it copies the blueprint's schematic data onto the
 *     decoration controllers, takes the order out of the work manager - which in turn unassigns whichever builder had
 *     claimed it and stops its AI - and removes the construction tape.</li>
 *     <li><b>The building</b> ends up at the target level. Placement normally does that by itself, because the anchor
 *     block entity is given the blueprint's schematic data and {@code AbstractSchematicProvider#onUpgradeSchematicTo}
 *     reads the level out of the schematic name; the level is checked afterwards all the same and forced through
 *     {@code onUpgradeComplete}/{@code setBuildingLevel} if the schematic data did not carry it, which is the same
 *     fallback the builder's AI has. Colony statistics, the event description log and
 *     {@link BuildingConstructionModEvent} are all posted as the AI posts them.</li>
 * </ol>
 *
 * <h2>Which work orders it handles</h2>
 * All four {@link WorkOrderType}s - BUILD, UPGRADE, REPAIR and REMOVE - and all four work order classes:
 * {@code WorkOrderBuilding} (huts, the town hall included), {@code WorkOrderDecoration},
 * {@code WorkOrderPlantationField} and {@code WorkOrderMiner}. The last three have no {@code IBuilding} at their
 * location, so for them step 4 has nothing to do and the decoration controller's own schematic data - written in
 * step 3 - is the whole of their state, exactly as it is when a builder finishes one.
 *
 * <h2>Things with no work order</h2>
 * {@code at <pos>} files the order itself, and this is not a convenience: a colony's <em>first</em> order can never be
 * created through the ordinary path, because {@code AbstractBuilding#requestWorkOrder} refuses one while no builder's
 * hut with a builder in it stands within {@code maxbuilderdistance} of the site, and a builder is only hired into a
 * hut that has been built. A colony raised from the console is therefore deadlocked: no town hall, no builder's hut,
 * nothing. Going straight to {@code WorkOrderBuilding#create} steps over that gate - and only that gate - so the
 * bootstrap has an answer.
 * <p>
 * A position that holds a decoration controller rather than a hut is answered the same way, with a
 * {@code WorkOrderDecoration}. A decoration order names a pack, a path and a position rather than a building, so a
 * controller with a blueprint recorded is all it takes - and this is the only way to reach one without a client,
 * since the only other maker of that order is the decoration window's {@code DecorationBuildRequestMessage}. It takes
 * no level argument: a decoration's level, where it has one, is already part of the path its controller records.
 *
 * <h2>Why free mode is required</h2>
 * Operator rights alone would be the wrong bar. Free mode ({@code /mc colony freemode <colony> on}) is already the
 * colony-wide "this colony is a test fixture, not an economy" switch: it is what {@code /mc colony hut}'s level
 * argument needs, what lets a level one builder be handed a level five hut, and what conjures the materials a build
 * would otherwise consume. Building for nothing is precisely a free mode act, and requiring the switch means the
 * colonies this can be run against are the colonies whose owner has already said they do not mind. An operator on a
 * server carrying real colonies cannot flatten twenty minutes of somebody's work with one mistyped colony id.
 */
public class CommandColonyBuildNow implements IMCOPCommand
{
    /**
     * The keyword that names one work order by id.
     */
    private static final String ORDER_ARG = "order";

    /**
     * The id of that work order.
     */
    private static final String ID_ARG = "id";

    /**
     * The keyword that names one building by position.
     */
    private static final String AT_ARG = "at";

    /**
     * Where that building's hut block stands.
     */
    private static final String POS_ARG = "position";

    /**
     * The level to build the named building to, 0 to take it down.
     */
    private static final String LEVEL_ARG = "level";

    /**
     * How many work orders are named in chat before the list is cut off. The complete list always goes to the log.
     */
    private static final int CHAT_ORDER_CAP = 10;

    /**
     * Phases {@link PlaceStructureOperation} walks: solid, weak solid, water, non-solid, entities.
     */
    private static final int PLACEMENT_PHASES = 5;

    /**
     * Phases a deconstruction walks: water removal then block removal.
     */
    private static final int REMOVAL_PHASES = 2;

    /**
     * Ceiling on how many placement calls one work order may take, whatever its size says. A placement handler that
     * answers DENY makes {@code executeStructureStep} return without advancing, and a loop driven to completion has
     * to be able to give up rather than take the server thread with it.
     */
    private static final int MAX_STEP_CALLS = 2_000_000;

    /**
     * What became of one work order.
     *
     * @param name    what to call it in the report.
     * @param problem null if it was finished, otherwise why it was not.
     */
    private record Outcome(String name, @Nullable String problem)
    {
    }

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!allowed(context, colony))
        {
            return 0;
        }

        // A copy: finishing an order takes it out of the work manager's map.
        final List<IServerWorkOrder> orders = new ArrayList<>(colony.getWorkManager().getWorkOrders().values());
        orders.sort((a, b) -> Integer.compare(a.getID(), b.getID()));
        return run(context, colony, orders);
    }

    /**
     * Finish one work order named by its id.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int one(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!allowed(context, colony))
        {
            return 0;
        }

        final int id = IntegerArgumentType.getInteger(context, ID_ARG);
        final IServerWorkOrder order = colony.getWorkManager().getWorkOrder(id);
        if (order == null)
        {
            context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_NO_ORDER, id));
            return 0;
        }

        return run(context, colony, List.of(order));
    }

    /**
     * Finish the building or decoration at a position, filing the work order for it when there is none.
     *
     * @param context the command context.
     * @param level   the level to build to, 0 to take the building down, or -1 for the next level up.
     * @return the command status.
     */
    private int at(final CommandContext<CommandSourceStack> context, final int level) throws CommandSyntaxException
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!allowed(context, colony))
        {
            return 0;
        }

        final BlockPos pos = BlockPosArgument.getSpawnablePos(context, POS_ARG);
        final IServerWorkOrder existing = orderAt(colony, pos);
        if (existing != null)
        {
            return run(context, colony, List.of(existing));
        }

        final IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        if (building == null)
        {
            return decoration(context, colony, pos, level);
        }

        final String path = building.getBlueprintPath();
        if (path == null || path.replace(".blueprint", "").isEmpty())
        {
            context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_NO_BLUEPRINT_PATH, pos.toShortString()));
            return 0;
        }

        // Level 0 is the deconstruction, spelled the way the hut window spells it: taking a building down to nothing.
        // Refused for what AbstractBuilding#requestRemoval refuses it for, and for the same reason - the order would
        // ask for a level 0 blueprint no pack ships.
        if (level == 0)
        {
            if (building.getBuildingLevel() <= 0 || building.isDeconstructed()
                  || (building instanceof final AbstractBuilding concrete && !concrete.canDeconstruct()))
            {
                context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_NO_DECONSTRUCT, pos.toShortString()));
                return 0;
            }
        }

        final int target = level >= 0 ? level : building.getBuildingLevel() + 1;
        if (target > building.getMaxBuildingLevel())
        {
            context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_TOO_HIGH,
              target, building.getMaxBuildingLevel()));
            return 0;
        }

        final WorkOrderType type = target == 0
                                     ? WorkOrderType.REMOVE
                                     : (building.getBuildingLevel() == 0 ? WorkOrderType.BUILD : WorkOrderType.UPGRADE);

        // Straight to create/addWorkOrder rather than through requestWorkOrder, whose builder gates are the whole
        // reason this branch exists. addWorkOrder still refuses an order standing on ground the colony has not
        // claimed, and still leaves its id at zero when it does.
        final WorkOrderBuilding order = WorkOrderBuilding.create(type, building, target);
        colony.getWorkManager().addWorkOrder(order, false);
        if (order.getID() == 0 || colony.getWorkManager().getWorkOrder(order.getID()) == null)
        {
            context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_ORDER_REFUSED, pos.toShortString()));
            return 0;
        }

        return run(context, colony, List.of(order));
    }

    /**
     * File and finish a decoration order for the controller at a position.
     * <p>
     * The counterpart of the hut branch above for a decoration: a decoration work order names a pack, a path and a
     * position rather than a building, so a controller standing in the world with a blueprint recorded is everything
     * one needs. This is the only way to reach {@code WorkOrderDecoration} without a client, since the order is
     * otherwise only ever made by {@code DecorationBuildRequestMessage} out of the decoration window.
     *
     * @param context the command context.
     * @param colony  the colony.
     * @param pos     the controller's position.
     * @param level   the level named, which a decoration has no use for, or -1 for none named.
     * @return the command status.
     */
    private static int decoration(
      final CommandContext<CommandSourceStack> context,
      @NotNull final IColony colony,
      @NotNull final BlockPos pos,
      final int level)
    {
        if (!(colony.getWorld().getBlockEntity(pos) instanceof final TileEntityDecorationController controller)
              || controller.getBlueprintPath().isEmpty() || controller.getPackName().isEmpty())
        {
            context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_NO_BUILDING, pos.toShortString()));
            return 0;
        }

        if (level >= 0)
        {
            context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_DECO_LEVEL, pos.toShortString()));
            return 0;
        }

        final String path = controller.getBlueprintPath();
        final String[] split = path.split("/");
        final WorkOrderDecoration order = WorkOrderDecoration.create(
          WorkOrderType.BUILD,
          controller.getPackName(),
          path,
          split[split.length - 1].replace(".blueprint", ""),
          pos,
          controller.getRotationMirror(),
          0);
        colony.getWorkManager().addWorkOrder(order, false);
        if (order.getID() == 0 || colony.getWorkManager().getWorkOrder(order.getID()) == null)
        {
            context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_ORDER_REFUSED, pos.toShortString()));
            return 0;
        }

        return run(context, colony, List.of(order));
    }

    /**
     * Whether this colony may be built for nothing.
     *
     * @param context the command context.
     * @param colony  the colony.
     * @return true if the command may go ahead.
     */
    private static boolean allowed(final CommandContext<CommandSourceStack> context, @NotNull final IColony colony)
    {
        if (FreeMode.isOn(colony))
        {
            return true;
        }

        // Three placeholders: the name, the id, and the id again inside the command the message spells out.
        context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_BUILDNOW_NO_FREEMODE,
          colony.getName(), colony.getID(), colony.getID()));
        return false;
    }

    /**
     * The open work order for a position, if there is one.
     *
     * @param colony the colony.
     * @param pos    the position.
     * @return the order, or null.
     */
    @Nullable
    private static IServerWorkOrder orderAt(@NotNull final IColony colony, @NotNull final BlockPos pos)
    {
        for (final IServerWorkOrder order : colony.getWorkManager().getWorkOrders().values())
        {
            if (order.getLocation().equals(pos))
            {
                return order;
            }
        }
        return null;
    }

    /**
     * Finish every work order in the list and report.
     *
     * @param context the command context.
     * @param colony  the colony.
     * @param orders  the orders to finish.
     * @return 1 always: "there was nothing to build" is a successful answer.
     */
    private static int run(
      final CommandContext<CommandSourceStack> context,
      @NotNull final IColony colony,
      @NotNull final List<IServerWorkOrder> orders)
    {
        final StringBuilder log = new StringBuilder("\n");
        emit(context, log, Component.translatable(COMMAND_COLONY_BUILDNOW_HEADER, colony.getName(), colony.getID()), ChatFormatting.GOLD);

        if (orders.isEmpty())
        {
            emit(context, log, Component.translatable(COMMAND_COLONY_BUILDNOW_NOTHING), ChatFormatting.GREEN);
            Log.getLogger().info(log.toString());
            return 1;
        }

        final List<String> done = new ArrayList<>();
        final List<String> failed = new ArrayList<>();

        for (final IServerWorkOrder order : orders)
        {
            Outcome outcome;
            try
            {
                outcome = build(colony, order);
            }
            catch (final RuntimeException e)
            {
                // One odd order must not cost the other fifty theirs.
                Log.getLogger().error("buildnow: work order #" + order.getID() + " threw", e);
                outcome = new Outcome(describe(order), e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            if (outcome.problem() == null)
            {
                done.add(outcome.name());
            }
            else
            {
                failed.add(outcome.name() + " - " + outcome.problem());
            }
        }

        emit(context, log, Component.translatable(COMMAND_COLONY_BUILDNOW_SUMMARY, done.size(), failed.size()),
          failed.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.RED);
        list(context, log, done);
        list(context, log, failed);
        Log.getLogger().info(log.toString());
        return 1;
    }

    /**
     * Put one work order's structure into the world and close the order.
     *
     * @param colony the colony.
     * @param order  the work order.
     * @return what became of it.
     */
    private static Outcome build(@NotNull final IColony colony, @NotNull final IServerWorkOrder order)
    {
        final String name = describe(order);
        if (!(colony.getWorld() instanceof final ServerLevel level))
        {
            return new Outcome(name, "the colony has no server level");
        }

        final Blueprint blueprint = blueprintOf(order, level);
        if (blueprint == null)
        {
            return new Outcome(name, "no blueprint " + order.getStructurePath() + " in pack " + order.getStructurePack());
        }

        final CreativeBuildingStructureHandler handler =
          new CreativeBuildingStructureHandler(level, order.getLocation(), blueprint, order.getRotationMirror(), true);
        final StructurePlacer placer = new StructurePlacer(handler);
        if (!placer.isReady())
        {
            return new Outcome(name, "the structure placer would not start");
        }

        final boolean removal = order.getWorkOrderType() == WorkOrderType.REMOVE;
        final boolean complete = removal
                                   ? deconstruct(level, placer, budget(blueprint, handler.getStepsPerCall(), REMOVAL_PHASES))
                                   : place(level, placer, budget(blueprint, handler.getStepsPerCall(), PLACEMENT_PHASES));
        if (!complete)
        {
            // The order is deliberately left standing: half a structure with its order closed is worse than half a
            // structure a builder can still be sent at.
            return new Outcome(name, "the placement did not finish - the work order was left open");
        }

        finish(colony, order, blueprint);
        return new Outcome(name, null);
    }

    /**
     * The work order's blueprint, rotated and mirrored the way the order wants it.
     *
     * @param order the work order.
     * @param level the world.
     * @return the blueprint, or null if the pack does not have it.
     */
    @Nullable
    private static Blueprint blueprintOf(@NotNull final IServerWorkOrder order, @NotNull final ServerLevel level)
    {
        if (order.getBlueprint() != null)
        {
            return order.getBlueprint();
        }

        Blueprint blueprint = StructurePacks.getBlueprint(order.getStructurePack(), order.getStructurePath(), level.registryAccess());
        if (blueprint == null && order instanceof WorkOrderMiner)
        {
            // The miner's own fallback, see WorkOrderMiner#loadBlueprint: a mine node blueprint that is not in the
            // colony's pack is taken from the storage pack instead.
            blueprint = StructurePacks.getBlueprint(STORAGE_STYLE, order.getStructurePath(), level.registryAccess());
        }
        if (blueprint == null)
        {
            return null;
        }

        // setBlueprint is what applies the rotation and mirror - through ColonyUtils#calculateCorners - and what
        // gives the order the bounding box its own completion reads.
        order.setBlueprint(blueprint, level);
        return order.getBlueprint();
    }

    /**
     * Drive Structurize's creative placement to the end of its last phase.
     *
     * @param level  the world.
     * @param placer the placer.
     * @param budget how many calls it may take.
     * @return true if it finished.
     */
    private static boolean place(@NotNull final ServerLevel level, @NotNull final StructurePlacer placer, final int budget)
    {
        final PlaceStructureOperation operation = new PlaceStructureOperation(placer, null);
        for (int call = 0; call < budget; call++)
        {
            if (operation.apply(level))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Take the structure down: the two stages {@code AbstractEntityAIStructure} runs for a REMOVE order, each driven
     * to the end instead of one block per tick.
     *
     * @param level  the world.
     * @param placer the placer.
     * @param budget how many calls each stage may take.
     * @return true if both stages finished.
     */
    private static boolean deconstruct(@NotNull final ServerLevel level, @NotNull final StructurePlacer placer, final int budget)
    {
        return stage(level, placer, StructurePlacer.Operation.WATER_REMOVAL, false, budget,
                 (info, pos, handler) -> info.getBlockInfo().getState().getFluidState().isEmpty())
                 && stage(level, placer, StructurePlacer.Operation.BLOCK_REMOVAL, true, budget,
                 AbstractEntityAIStructure::skipRemoval);
    }

    /**
     * Run one removal stage until the iterator reports it finished.
     *
     * @param level    the world.
     * @param placer   the placer.
     * @param what     the operation.
     * @param entities whether entities are part of this stage.
     * @param budget   how many calls it may take.
     * @param skip     which positions this stage passes over.
     * @return true if the stage finished.
     */
    private static boolean stage(
      @NotNull final ServerLevel level,
      @NotNull final StructurePlacer placer,
      @NotNull final StructurePlacer.Operation what,
      final boolean entities,
      final int budget,
      @NotNull final TriPredicate<BlueprintPositionInfo, BlockPos, IStructureHandler> skip)
    {
        BlockPos progress = NULL_POS;
        for (int call = 0; call < budget; call++)
        {
            // Removal walks the blueprint backwards, and reset() at the end of a stage clears the flag again.
            placer.getIterator().setRemoving();
            final StructurePhasePlacementResult result =
              placer.executeStructureStep(level, null, progress, what, () -> placer.getIterator().decrement(skip), entities);
            if (result.getBlockResult().getResult() == BlockPlacementResult.Result.FINISHED)
            {
                return true;
            }
            progress = result.getIteratorPos();
        }
        return false;
    }

    /**
     * How many placement calls a structure of this size may need, plus room for the phase changes.
     *
     * @param blueprint     the structure.
     * @param stepsPerCall  how many blocks one call handles.
     * @param phases        how many passes are made over the structure.
     * @return the ceiling.
     */
    private static int budget(@NotNull final Blueprint blueprint, final int stepsPerCall, final int phases)
    {
        final long volume = (long) blueprint.getSizeX() * blueprint.getSizeY() * blueprint.getSizeZ();
        final long calls = (long) phases * (volume / Math.max(1, stepsPerCall) + 2) + 64;
        return (int) Math.min(MAX_STEP_CALLS, calls);
    }

    /**
     * Close the work order the way the builder's AI closes it.
     *
     * @param colony    the colony.
     * @param order     the work order.
     * @param blueprint its structure.
     */
    private static void finish(@NotNull final IColony colony, @NotNull final IServerWorkOrder order, @NotNull final Blueprint blueprint)
    {
        final String key = order.getTranslationKey();
        switch (order.getWorkOrderType())
        {
            case BUILD:
                colony.getEventDescriptionManager().addEventDescription(new BuildingBuiltEvent(order.getLocation(), key));
                colony.getStatisticsManager().increment(BUILD_BUILT, colony.getDay());
                break;
            case UPGRADE:
                colony.getEventDescriptionManager().addEventDescription(new BuildingUpgradedEvent(order.getLocation(), key, order.getTargetLevel()));
                colony.getStatisticsManager().increment(BUILD_UPGRADED, colony.getDay());
                break;
            case REPAIR:
                colony.getEventDescriptionManager().addEventDescription(new BuildingRepairedEvent(order.getLocation(), key, order.getCurrentLevel()));
                colony.getStatisticsManager().increment(BUILD_REPAIRED, colony.getDay());
                break;
            case REMOVE:
                colony.getEventDescriptionManager().addEventDescription(new BuildingDeconstructedEvent(order.getLocation(), key, order.getCurrentLevel()));
                colony.getStatisticsManager().increment(BUILD_REMOVED, colony.getDay());
                break;
        }

        // The building has to be read before the order is closed: closing it can unassign a builder, and nothing
        // about that is worth re-reading afterwards.
        final IBuilding building = colony.getServerBuildingManager().getBuilding(order.getLocation());

        // What AbstractBuildingStructureBuilder#complete calls, minus the citizen there is none of: the decoration
        // controllers get their schematic data, the order leaves the work manager, its tape comes down and whichever
        // builder had claimed it is let go.
        if (order instanceof final IBuilderWorkOrder builderOrder)
        {
            builderOrder.onCompleted(colony, null);
        }
        else
        {
            colony.getWorkManager().removeWorkOrder(order);
        }

        if (!(order instanceof final WorkOrderBuilding buildingOrder) || building == null)
        {
            return;
        }

        if (order.getWorkOrderType() == WorkOrderType.REMOVE)
        {
            building.setDeconstructed();
        }
        else if (building.getBuildingLevel() != order.getTargetLevel())
        {
            // Placement normally carries the level over on the anchor's schematic data. When the blueprint has none
            // - the AI checks for exactly this - the level is set by hand instead.
            Log.getLogger().info("buildnow: {} at {} did not take its level from the schematic data, setting it to {}",
              key, order.getLocation().toShortString(), order.getTargetLevel());
            building.onUpgradeComplete(blueprint, order.getTargetLevel());
            building.setBuildingLevel(order.getTargetLevel());
        }

        IMinecoloniesAPI.getInstance().getEventBus().post(new BuildingConstructionModEvent(building, buildingOrder));
    }

    /**
     * Name one work order the way {@code /mc colony diagnose} names it.
     *
     * @param order the order.
     * @return the text.
     */
    private static String describe(@NotNull final IServerWorkOrder order)
    {
        return String.format("#%s %s %s at %s level %s->%s",
          order.getID(),
          order.getWorkOrderType(),
          order.getStructurePath(),
          order.getLocation().toShortString(),
          order.getCurrentLevel(),
          order.getTargetLevel());
    }

    /**
     * Send a list of per order lines, capped in chat and complete in the log.
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

            if (i < CHAT_ORDER_CAP)
            {
                final Component component = Component.literal(line);
                context.getSource().sendSuccess(() -> component, false);
            }
        }

        if (lines.size() > CHAT_ORDER_CAP)
        {
            final Component more = Component.translatable(COMMAND_COLONY_BUILDNOW_MORE, lines.size() - CHAT_ORDER_CAP)
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
     * @param color     the chat colour.
     */
    private static void emit(
      final CommandContext<CommandSourceStack> context,
      @NotNull final StringBuilder log,
      @NotNull final MutableComponent component,
      final ChatFormatting color)
    {
        log.append(component.getString()).append('\n');
        final Component styled = component.withStyle(color);
        context.getSource().sendSuccess(() -> styled, true);
    }

    @Override
    public String getName()
    {
        return "buildnow";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .executes(this::checkPreConditionAndExecute)
                         .then(IMCCommand.newLiteral(ORDER_ARG)
                                 .then(IMCCommand.newArgument(ID_ARG, IntegerArgumentType.integer(1))
                                         .executes(context -> checkPreConditionAndExecute(context, this::one))))
                         .then(IMCCommand.newLiteral(AT_ARG)
                                 .then(IMCCommand.newArgument(POS_ARG, BlockPosArgument.blockPos())
                                         .executes(context -> checkPreConditionAndExecute(context, ctx -> at(ctx, -1)))
                                         .then(IMCCommand.newArgument(LEVEL_ARG, IntegerArgumentType.integer(0, 5))
                                                 .executes(context -> checkPreConditionAndExecute(context,
                                                   ctx -> at(ctx, IntegerArgumentType.getInteger(ctx, LEVEL_ARG))))))));
    }
}
