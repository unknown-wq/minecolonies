package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.rescue.KeepBuildings;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_KEEPBUILDINGS_STATE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_KEEPBUILDINGS_SUCCESS;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Stops the sanity cleanup deleting buildings from one colony, or lets it run again. See {@link KeepBuildings}
 * for what exactly is suspended and why.
 * <p>
 * Off by default and saved with the colony, so a colony that has never run this command behaves exactly as it
 * always did, and one that has stays held back across a restart.
 * <p>
 * {@code /mc colony keepbuildings <colony>} on its own reports the current state rather than changing it, and
 * says how many of the colony's buildings currently have no matching hut block — which is the number that would
 * be deleted, a few at a time, if it were off.
 */
public class CommandColonyKeepBuildings implements IMCOPCommand
{
    /**
     * What happens when the command is executed with no on/off given: report, do not change.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(colony instanceof final Colony serverColony))
        {
            return 0;
        }

        // The count the player actually wants: not "how many buildings are there" but "how many of them are one
        // colony tick away from being deleted". Chunks are read as they are; an unloaded one is not at risk yet.
        final Level world = colony.getWorld();
        int atRisk = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (world != null && !building.isMatchingBlock(world.getBlockState(building.getPosition()).getBlock()))
            {
                atRisk++;
            }
        }

        final int atRiskCount = atRisk;
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_KEEPBUILDINGS_STATE,
            colony.getName(),
            serverColony.isKeepBuildings(),
            atRiskCount,
            colony.getServerBuildingManager().getBuildings().size()), true);
        return 1;
    }

    /**
     * Throw the switch.
     *
     * @param context the context of the command execution.
     * @param on      whether the cleanup should be held back here.
     * @return 1 if the colony was reachable.
     */
    private int set(final CommandContext<CommandSourceStack> context, final boolean on)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(colony instanceof final Colony serverColony))
        {
            // The cleanup only ever runs on the server, so the flag lives on Colony rather than on IColony.
            return 0;
        }

        serverColony.setKeepBuildings(on);
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_KEEPBUILDINGS_SUCCESS, colony.getName(), on), true);
        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "keepbuildings";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newLiteral("on").executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, true))))
                         .then(IMCCommand.newLiteral("off").executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, false))))
                         .executes(this::checkPreConditionAndExecute));
    }
}
