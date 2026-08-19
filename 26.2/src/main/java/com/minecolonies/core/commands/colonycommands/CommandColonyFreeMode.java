package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.debug.FreeMode;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_FREEMODE_STATE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_FREEMODE_SUCCESS;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Turns {@link FreeMode} on or off for a colony: every worker then gets on with its job without the items it would
 * normally have to be given. Purely a testing aid.
 * <p>
 * {@code /mc colony freemode <colony>} on its own reports the current state rather than changing it.
 */
public class CommandColonyFreeMode implements IMCOPCommand
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
        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_FREEMODE_STATE, colony.getName(), FreeMode.isOn(colony)), true);
        return 1;
    }

    /**
     * Throw the switch.
     *
     * @param context the context of the command execution.
     * @param on      whether free mode should be on.
     * @return 1 if the colony was reachable.
     */
    private int set(final CommandContext<CommandSourceStack> context, final boolean on)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(colony instanceof final Colony serverColony))
        {
            // Free mode is only ever read on the server, so the flag lives on Colony rather than on IColony.
            return 0;
        }

        serverColony.setFreeMode(on);
        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_FREEMODE_SUCCESS, colony.getName(), on), true);
        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "freemode";
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
