package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.debug.ColonyProtection;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_PROTECTION_STATE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_PROTECTION_SUCCESS;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Turns permission enforcement on or off for one colony. See {@link ColonyProtection}.
 * <p>
 * The server config {@code enablecolonyprotection} is the same idea for the whole server at once, and the rank
 * table is the same idea one action at a time. This is the middle one nothing else offers: a single colony that
 * is being tested rather than played, whose owner does not want to walk the permission screen rank by rank to
 * find whichever gate is refusing him this time.
 * <p>
 * {@code /mc colony protection <colony>} on its own reports the current state rather than changing it, and says
 * what the switch does and does not cover.
 */
public class CommandColonyProtection implements IMCOPCommand
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

        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_PROTECTION_STATE, colony.getName(), serverColony.isProtection()), true);
        return 1;
    }

    /**
     * Throw the switch.
     *
     * @param context the context of the command execution.
     * @param on      whether this colony should enforce its permissions.
     * @return 1 if the colony was reachable.
     */
    private int set(final CommandContext<CommandSourceStack> context, final boolean on)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(colony instanceof final Colony serverColony))
        {
            // The flag is only ever read on the server, so it lives on Colony rather than on IColony.
            return 0;
        }

        serverColony.setProtection(on);
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_PROTECTION_SUCCESS, colony.getName(), on), true);
        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "protection";
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
