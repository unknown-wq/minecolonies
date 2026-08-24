package com.minecolonies.core.commands.colonycommands;

import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.blocks.AbstractColonyBlock;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Puts the hut block back for every building the colony still knows about.
 * <p>
 * <b>What this is for.</b> A world opened once without the mod installed loses every one of the mod's block
 * entities: vanilla logs {@code Skipping block entity with invalid type} and drops them, and the hut blocks
 * themselves go with the chunk sections that referenced an unknown block. The colony save is untouched by that
 * — it lives in its own file and still lists every building, its level, its blueprint and its position — so the
 * town exists in the colony's memory and not in the world. This command copies the colony's memory back into
 * the world, one hut block per building.
 * <p>
 * <b>Why the hut block alone.</b> A hut block at the right position is all a building needs to be consistent
 * again: it is what {@link IBuilding#isMatchingBlock(Block)} looks for, which is what
 * {@code RegisteredStructureManager#cleanUpBuildings} tests before deleting a building from the colony, and it
 * is what the hut GUI, the builder and the citizens attach to. The rest of the structure is the builders' job:
 * a repair order rebuilds it from the same blueprint the colony already has recorded, without this command
 * having to load, rotate and paste 145 blueprints over terrain the player may since have changed. Pasting was
 * considered and rejected — see the class comment tail below.
 * <p>
 * <b>The one thing that has to be right: binding.</b> The placed block must reattach to the building that is
 * already in the colony, at the level it already has, and must not register a fresh level 0 one. Two mechanisms
 * make that so, and this command leans on both rather than inventing a third:
 * <ul>
 *     <li>{@code RegisteredStructureManager#addNewBuilding} is guarded by
 *     {@code if (!buildings.containsKey(tileEntity.getPosition()))} — a hut block appearing where the colony
 *     already has a building never creates a second one. This command does not go through
 *     {@code Block#setPlacedBy} at all, so it does not even reach that guard, but the guard is why an ordinary
 *     placement here would also be safe.</li>
 *     <li>{@code TileEntityColonyBuilding#updateColonyReferences} resolves
 *     {@code colony.getServerBuildingManager().getBuilding(pos)} and calls {@code building.setTileEntity(this)}.
 *     That is the mod's own attach path, and calling {@code getBuilding()} on the freshly placed block entity
 *     runs it. The building's level, blueprint, work orders, citizens and containers are never touched.</li>
 * </ul>
 * Every building is verified after placement — the block matches and the colony still returns the <em>same</em>
 * building object for that position — and any that does not is reported by name rather than aborting the run.
 * <p>
 * <b>One tick.</b> A command body runs inside a single server tick, and the sanity cleanup runs on the colony
 * tick, so every hut block of the colony is back before the cleanup can look at any of them. Chunks are pulled
 * in explicitly for each position rather than hoping the player has walked past: the cleanup ignores buildings
 * in unloaded chunks, which means the unloaded ones are exactly the ones still worth saving.
 * <p>
 * <b>Destructive, hence the confirmation word.</b> Whatever occupies a building's anchor position now is
 * replaced. On the world this is written for that is air or a stray natural block, but a hut of the wrong type
 * or a container put there since would also go, spilling its contents. {@code /mc colony restorehuts <colony>}
 * on its own therefore counts what it would do and changes nothing;
 * {@code /mc colony restorehuts <colony> confirm} does it.
 * <p>
 * <b>Why not paste the blueprints.</b> {@code /structurize paste} routes a hut anchor through
 * {@code AbstractBlockHut#setup}, which ends with
 * {@code building.setBuildingLevel(Integer.parseInt(num))} read out of the blueprint file name and a fallback of
 * {@code setBuildingLevel(1)} when that parse fails. On a rescue whose entire point is to preserve levels, a
 * path that can silently reset a level 5 building to 1 is the wrong tool; it also destroys the existing block
 * with drops, queues 145 asynchronous placement operations, and overwrites terrain the player has changed since.
 * The builders rebuilding from repair orders reach the same end state without any of that.
 */
public class CommandColonyRestoreHuts implements IMCOPCommand
{
    /**
     * The word that turns the report into the real thing.
     */
    private static final String CONFIRM_ARG = "confirm";

    /**
     * How many buildings are named in chat before the list is cut off. The complete list always goes to the
     * server log.
     */
    private static final int CHAT_BUILDING_CAP = 10;

    /**
     * With no confirmation word: say what would happen and change nothing.
     *
     * @param context the context of the command execution.
     * @return 1 if the colony was reachable.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (colony == null)
        {
            return 0;
        }

        int missing = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (!isHutPresent(colony.getWorld(), building))
            {
                missing++;
            }
        }

        final int missingCount = missing;
        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_RESTOREHUTS_ASK,
          colony.getName(),
          missingCount,
          colony.getServerBuildingManager().getBuildings().size()).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    /**
     * Place a hut block for every building that is missing one.
     *
     * @param context the context of the command execution.
     * @return 1 if the colony was reachable.
     */
    private int run(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (colony == null)
        {
            return 0;
        }

        final Level world = colony.getWorld();
        if (world == null)
        {
            context.getSource().sendFailure(Component.translatable(COMMAND_COLONY_RESTOREHUTS_NO_WORLD, colony.getName()));
            return 0;
        }

        // A stable order, so two runs on the same colony walk the buildings in the same sequence and the log of
        // the second is comparable with the log of the first.
        final List<IBuilding> buildings = new ArrayList<>(colony.getServerBuildingManager().getBuildings().values());
        buildings.sort(Comparator.comparingInt((IBuilding b) -> b.getPosition().getX())
                         .thenComparingInt(b -> b.getPosition().getZ())
                         .thenComparingInt(b -> b.getPosition().getY()));

        final List<String> placed = new ArrayList<>();
        final List<String> failed = new ArrayList<>();
        int alreadyThere = 0;

        for (final IBuilding building : buildings)
        {
            if (isHutPresent(world, building))
            {
                alreadyThere++;
                continue;
            }

            try
            {
                final String problem = restore(world, colony, building);
                if (problem == null)
                {
                    placed.add(describe(building));
                }
                else
                {
                    failed.add(describe(building) + " - " + problem);
                }
            }
            catch (final RuntimeException e)
            {
                // One bad building must not cost the other hundred and forty-four theirs.
                Log.getLogger().error("restorehuts: " + describe(building) + " threw while its hut block was being placed", e);
                failed.add(describe(building) + " - " + e.getClass().getSimpleName());
            }
        }

        report(context, colony, placed, failed, alreadyThere);
        return 1;
    }

    /**
     * Whether the world already holds the right hut block for this building.
     *
     * @param world    the world, may be null.
     * @param building the building.
     * @return true if there is nothing to do for it.
     */
    private static boolean isHutPresent(final Level world, @NotNull final IBuilding building)
    {
        return world != null && building.isMatchingBlock(world.getBlockState(building.getPosition()).getBlock());
    }

    /**
     * Put one hut block back and attach it to the building that is already there.
     *
     * @param world    the world.
     * @param colony   the colony.
     * @param building the building whose anchor is missing.
     * @return null on success, or a short reason the caller can put in the report.
     */
    private static String restore(@NotNull final Level world, @NotNull final IColony colony, @NotNull final IBuilding building)
    {
        final BlockPos pos = building.getPosition();
        if (!world.isInWorldBounds(pos))
        {
            return "position is outside the world";
        }

        final AbstractColonyBlock<?> hut = building.getBuildingType().getBuildingBlock();
        if (hut == null)
        {
            return "the building type has no hut block";
        }

        // Pull the chunk in rather than hoping the player has stood near it. The cleanup only deletes buildings
        // whose chunk is loaded, so the ones nobody has visited are precisely the ones still worth saving, and
        // they are the ones a "place it if it happens to be loaded" version would skip.
        world.getChunk(pos);

        BlockState state = hut.defaultBlockState();
        final RotationMirror rotationMirror = building.getRotationMirror();
        if (rotationMirror != null)
        {
            // The colony recorded how the structure was turned when it was built; the anchor's facing follows it,
            // so the hut faces the way it did rather than always north.
            state = rotationMirror.applyToBlockState(state);
        }

        if (!world.setBlock(pos, state, Block.UPDATE_ALL))
        {
            return "the world refused the block";
        }

        final BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof final AbstractTileEntityColonyBuilding hutEntity))
        {
            return "no hut block entity appeared";
        }

        // Attach through the mod's own path. setColony fills in the reference updateColonyReferences would
        // otherwise have to guess at, and getBuilding() then runs updateColonyReferences, which looks the
        // building up by position and calls building.setTileEntity(this). Nothing here creates a building.
        hutEntity.setColony(colony);
        final IBuilding bound = hutEntity.getBuilding();

        if (bound == null)
        {
            return "the hut did not find its building";
        }
        if (bound != building)
        {
            return "the hut attached to a different building";
        }

        // What the block entity needs to know about itself. All of it is read back off the building, so a hut
        // restored this way answers the build tool and the builder exactly as the one that was lost did.
        if (rotationMirror != null)
        {
            hutEntity.setRotationMirror(rotationMirror);
        }
        if (building.getStructurePack() != null)
        {
            hutEntity.setPackName(building.getStructurePack());
        }
        if (building.getBlueprintPath() != null)
        {
            hutEntity.setBlueprintPath(building.getBlueprintPath());
        }

        // The hut's own block entity is also a rack, and a rack records which building it belongs to. For a hut
        // that is itself; a block entity created from nothing has it at the origin until someone says otherwise.
        hutEntity.setBuildingPos(pos);

        building.setTileEntity(hutEntity);
        building.markDirty();

        if (!building.isMatchingBlock(world.getBlockState(pos).getBlock()))
        {
            return "the placed block still does not match";
        }
        if (colony.getServerBuildingManager().getBuilding(pos) != building)
        {
            return "the colony no longer holds this building at that position";
        }

        return null;
    }

    /**
     * Say what happened. Counts and a capped list to chat, the whole thing to the server log.
     *
     * @param context      the command context.
     * @param colony       the colony.
     * @param placed       the buildings that got their hut block back.
     * @param failed       the buildings that did not, with the reason.
     * @param alreadyThere how many already had the right block.
     */
    private static void report(
      final CommandContext<CommandSourceStack> context,
      @NotNull final IColony colony,
      @NotNull final List<String> placed,
      @NotNull final List<String> failed,
      final int alreadyThere)
    {
        final StringBuilder log = new StringBuilder("\n");

        emit(context, log, Component.translatable(COMMAND_COLONY_RESTOREHUTS_HEADER, colony.getName(), colony.getID()), ChatFormatting.GOLD);
        emit(context, log, Component.translatable(COMMAND_COLONY_RESTOREHUTS_SUMMARY, placed.size(), alreadyThere, failed.size()),
          failed.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);

        list(context, log, placed);
        list(context, log, failed);

        if (!placed.isEmpty())
        {
            emit(context, log, Component.translatable(COMMAND_COLONY_RESTOREHUTS_NEXT), ChatFormatting.GRAY);
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
            final Component more = Component.translatable(COMMAND_COLONY_RESTOREHUTS_MORE, lines.size() - CHAT_BUILDING_CAP)
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

    /**
     * Name one building the way the rest of the command tree does: display name, level and hut position.
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
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "restorehuts";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .executes(this::checkPreConditionAndExecute)
                         .then(IMCCommand.newLiteral(CONFIRM_ARG)
                                 .executes(context -> checkPreConditionAndExecute(context, this::run))));
    }
}
