package com.minecolonies.core.commands.colonycommands;

import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.blocks.AbstractColonyBlock;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Adds a hut to a colony from the server console, and optionally files the work order that builds it.
 * <p>
 * The companion to {@code /mc colony found}, and it exists for the same reason: a hut becomes a building when a player
 * places its block, through {@code AbstractColonyBlock#setPlacedBy}, and {@code /setblock} does not call that. A hut
 * block dropped in by command is therefore a block the colony has never heard of -- it renders, it can be broken, and
 * nothing else about it works.
 * <p>
 * This does what the placement would have done: give the block entity the colony's structure pack and a blueprint
 * inside it, then register it with {@code addNewBuilding}. The blueprint path has to be given because there is no
 * convention that derives it: the Colonial pack calls the farmer's hut {@code farm1.blueprint}, not
 * {@code farmer1.blueprint}, and a building with no path cannot have a work order made for it at all.
 * <p>
 * With a level argument it also asks for the building to be raised to that level, which is the whole point of being
 * able to do this without a client: a builder that has work builds, and a colony where something is being built is a
 * colony whose world is changing. That path goes through {@code requestUpgradeTo}, so <b>free mode must be on for the
 * colony</b> ({@code /mc colony freemode <colony> on}) -- without it the request falls back to the ordinary upgrade,
 * and without it the builder waits on materials nobody is going to deliver.
 * <p>
 * Run it twice over the same hut to lay a whole town out and then build it: a work order is refused while the colony
 * has no builder's hut within reach of the site, which is always true of the first hut placed. The second call finds
 * the building already there and only files the order.
 */
public class CommandColonyHut implements IMCOPCommand
{
    /**
     * The hut block's registry name.
     */
    private static final String BLOCK_ARG = "hutblock";

    /**
     * Where the hut block goes.
     */
    private static final String POS_ARG = "position";

    /**
     * The blueprint inside the colony's pack, e.g. {@code fundamentals/builder1.blueprint}.
     */
    private static final String PATH_ARG = "blueprint";

    /**
     * The level to ask for, or absent to place the hut and file nothing.
     */
    private static final String LEVEL_ARG = "level";

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        try
        {
            return place(context, 0);
        }
        catch (final CommandSyntaxException e)
        {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    /**
     * Place the hut and ask for it to be built to the level given.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    private int placeAndBuild(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return place(context, IntegerArgumentType.getInteger(context, LEVEL_ARG));
    }

    /**
     * Place the hut block, register the building, and file a work order if one was asked for.
     *
     * @param context     the context of the command execution.
     * @param targetLevel the level to build to, 0 to file nothing.
     * @return 1 on success, 0 on failure.
     */
    private int place(final CommandContext<CommandSourceStack> context, final int targetLevel) throws CommandSyntaxException
    {
        final ServerLevel level = context.getSource().getLevel();
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final BlockPos pos = BlockPosArgument.getSpawnablePos(context, POS_ARG);
        final String path = StringArgumentType.getString(context, PATH_ARG);

        final Identifier id = Identifier.parse(StringArgumentType.getString(context, BLOCK_ARG));
        final Registry<Block> blocks = BuiltInRegistries.BLOCK;
        final Block block = blocks.containsKey(id) ? blocks.getValue(id) : null;
        if (!(block instanceof AbstractColonyBlock))
        {
            context.getSource().sendFailure(Component.literal(id + " is not a hut block"));
            return 0;
        }

        if (!level.getBlockState(pos).is(block))
        {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
        final BlockEntity tileEntity = level.getBlockEntity(pos);
        if (!(tileEntity instanceof final TileEntityColonyBuilding hut))
        {
            context.getSource().sendFailure(Component.literal("Could not place " + id + " at " + pos.toShortString()));
            return 0;
        }

        hut.setStructurePack(StructurePacks.getStructurePack(colony.getStructurePack()));
        hut.setBlueprintPath(path);

        // addNewBuilding answers null for a position the colony already has a building at, which is not a failure:
        // running this twice is how a hut is placed before there is a builder to build it and asked for afterwards,
        // and a work order is refused outright while the colony has no builder's hut in range of the site.
        IBuilding building = colony.getServerBuildingManager().addNewBuilding(hut, level);
        if (building == null)
        {
            building = colony.getServerBuildingManager().getBuilding(pos);
        }
        if (building == null)
        {
            context.getSource().sendFailure(Component.literal("The colony refused a building at " + pos.toShortString()));
            return 0;
        }

        if (targetLevel > 0)
        {
            // No player: nobody founded this town and nobody is watching it. Every message the request would send goes
            // to one, and the success path sends none.
            building.requestUpgradeTo(null, BlockPos.ZERO, targetLevel);
        }

        Log.getLogger().info("Added {} to colony {} at {} from the console, blueprint {}, asked for level {}",
          id, colony.getID(), pos, path, targetLevel);
        context.getSource().sendSuccess(() -> Component.literal(
          "Added " + id + " at " + pos.toShortString() + (targetLevel > 0 ? ", building to level " + targetLevel : "")), true);
        return 1;
    }

    @Override
    public String getName()
    {
        return "hut";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newArgument(BLOCK_ARG, StringArgumentType.string())
                                 .then(IMCCommand.newArgument(POS_ARG, BlockPosArgument.blockPos())
                                         .then(IMCCommand.newArgument(PATH_ARG, StringArgumentType.string())
                                                 .executes(this::checkPreConditionAndExecute)
                                                 .then(IMCCommand.newArgument(LEVEL_ARG, IntegerArgumentType.integer(1, 5))
                                                         .executes(context -> checkPreConditionAndExecute(context, this::placeAndBuild))))))); 
    }
}
