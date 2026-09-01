package com.minecolonies.core.commands.colonycommands;

import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.savedata.IServerColonySaveData;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.core.util.ChunkDataHelper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Founds an ordinary colony from the server console: places a town hall and builds the colony around it.
 * <p>
 * There was no way to do this without a game client. A colony is created by a player right-clicking a town hall with
 * the build tool, and every step of that -- the message, the permission owner, the packet the client is sent back --
 * assumes a {@code ServerPlayer}. {@code /mc colony territory create} makes a colony object from the console but
 * deliberately makes a <em>hostile territory</em>: no town hall, no citizens, and a tick path that does nothing but
 * repaint a border. That is the wrong shape for testing anything a colony actually does.
 * <p>
 * This runs the same three steps {@code ColonyManager#createColony} runs, minus the player: create the colony in the
 * world's save data, claim the usual square of chunks around it, and register the town hall as its first building. The
 * owner is left abandoned, exactly as a territory's is, because there is no player to be the owner and an operator who
 * founded a town from the console is not its mayor.
 * <p>
 * What it does not do is build anything: the town hall is left at level 0 with a blueprint recorded but not raised,
 * exactly as a freshly placed hut is. {@code /mc colony hut} adds the rest of the town and can file the work orders
 * that build it.
 */
public class CommandColonyFound implements IMCOPCommand
{
    /**
     * The colony name argument.
     */
    private static final String NAME_ARG = "name";

    /**
     * Where the town hall goes.
     */
    private static final String POS_ARG = "position";

    /**
     * Structure pack the colony is given. Colonial is the pack the mod treats as its default everywhere else, and a
     * colony needs a real one: the pack is where every hut placed afterwards looks for its blueprint, and a name no
     * pack answers to leaves the town hall with nothing to build from.
     */
    private static final String DEFAULT_PACK = "Colonial";

    /**
     * Blueprint the town hall is pointed at, inside the pack. Without a path a work order cannot be made for it at
     * all -- the order builds the blueprint name by rewriting the last character of this one with the level wanted.
     */
    private static final String TOWN_HALL_BLUEPRINT = "fundamentals/townhall1.blueprint";

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return found(context, BlockPos.containing(context.getSource().getPosition()));
    }

    /**
     * Found a colony at an explicit position.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    private int foundAt(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return found(context, BlockPosArgument.getSpawnablePos(context, POS_ARG));
    }

    /**
     * Place the town hall and build the colony around it.
     *
     * @param context the context of the command execution.
     * @param pos     where the town hall goes.
     * @return 1 on success, 0 on failure.
     */
    private int found(final CommandContext<CommandSourceStack> context, @NotNull final BlockPos pos)
    {
        final ServerLevel level = context.getSource().getLevel();
        final String name = StringArgumentType.getString(context, NAME_ARG);

        level.setBlockAndUpdate(pos, ModBlocks.blockHutTownHall.defaultBlockState());
        final BlockEntity tileEntity = level.getBlockEntity(pos);
        if (!(tileEntity instanceof final TileEntityColonyBuilding hut))
        {
            context.getSource().sendFailure(Component.literal("Could not place a town hall at " + pos.toShortString()));
            return 0;
        }

        final IServerColonySaveData saveData = IServerColonySaveData.getOrComputeSaveData(level);
        if (saveData == null || !(saveData.createColony(level, name, pos) instanceof final Colony colony))
        {
            context.getSource().sendFailure(Component.literal("Could not create a colony at " + pos.toShortString()));
            return 0;
        }

        colony.setName(name);
        colony.setStructurePack(DEFAULT_PACK);
        colony.getPermissions().setOwnerAbandoned();

        hut.setStructurePack(StructurePacks.getStructurePack(DEFAULT_PACK));
        hut.setBlueprintPath(TOWN_HALL_BLUEPRINT);

        ChunkDataHelper.claimColonyChunks(level, true, colony, colony.getCenter());
        colony.getServerBuildingManager().addNewBuilding(hut, level);

        Log.getLogger().info("Founded colony id {} named {} at {} from the console", colony.getID(), name, pos);
        context.getSource().sendSuccess(() -> Component.literal(
          "Founded colony " + colony.getName() + " (id " + colony.getID() + ") at " + pos.toShortString()), true);
        return 1;
    }

    @Override
    public String getName()
    {
        return "found";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(NAME_ARG, StringArgumentType.word())
                         .executes(this::checkPreConditionAndExecute)
                         .then(IMCCommand.newArgument(POS_ARG, BlockPosArgument.blockPos())
                                 .executes(context -> checkPreConditionAndExecute(context, this::foundAt))));
    }
}
