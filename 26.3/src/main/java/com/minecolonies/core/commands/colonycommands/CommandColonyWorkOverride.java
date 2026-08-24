package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.workoverrides.WorkOverride;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Throws the colony's "keep working anyway" switches - see {@link WorkOverride} - each of which names one thing that
 * ordinarily stops a citizen working and tells it to carry on regardless.
 * <p>
 * {@code /mc colony workoverride <colony>} on its own reports every switch rather than changing anything.
 */
public class CommandColonyWorkOverride implements IMCOPCommand
{
    /**
     * What happens when the command is executed with no switch given: report them all, change nothing.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_WORKOVERRIDE_HEADER, colony.getName()), true);
        for (final WorkOverride override : WorkOverride.values())
        {
            report(context, colony, override);
        }
        return 1;
    }

    /**
     * What happens when the command is executed with a switch but no on/off: report that one switch, change nothing.
     *
     * @param context  the context of the command execution.
     * @param override the switch to report.
     * @return 1 if the colony was reachable.
     */
    private int report(final CommandContext<CommandSourceStack> context, final WorkOverride override)
    {
        report(context, ColonyIdArgument.getColony(context, COLONYID_ARG), override);
        return 1;
    }

    /**
     * Print one switch and its state.
     *
     * @param context  the context of the command execution.
     * @param colony   the colony.
     * @param override the switch to report.
     */
    private static void report(final CommandContext<CommandSourceStack> context, final IColony colony, final WorkOverride override)
    {
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_WORKOVERRIDE_STATE,
            Component.translatable(override.getLabelKey()),
            override.getSerializedName(),
            state(override.isOn(colony))), true);
    }

    /**
     * Throw one switch.
     *
     * @param context  the context of the command execution.
     * @param override the switch to throw.
     * @param on       whether it should be on.
     * @return 1 if the colony was reachable.
     */
    private int set(final CommandContext<CommandSourceStack> context, final WorkOverride override, final boolean on)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(colony instanceof final Colony serverColony))
        {
            // The switches are only ever read on the server, so they live on Colony rather than on IColony.
            return 0;
        }

        serverColony.setWorkOverride(override, on);
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_WORKOVERRIDE_SUCCESS,
            Component.translatable(override.getLabelKey()),
            override.getSerializedName(),
            state(on),
            colony.getName()), true);
        return 1;
    }

    /**
     * The word for a switch's state, so that chat says "on" and "off" rather than "true" and "false".
     *
     * @param on whether the switch is on.
     * @return the component to put in the message.
     */
    private static Component state(final boolean on)
    {
        return Component.translatable(on ? COMMAND_COLONY_WORKOVERRIDE_ON : COMMAND_COLONY_WORKOVERRIDE_OFF);
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "workoverride";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        // One literal per switch, built off the enum, so that a switch added there needs nothing here.
        final var colonyArgument = IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id());
        for (final WorkOverride override : WorkOverride.values())
        {
            colonyArgument.then(IMCCommand.newLiteral(override.getSerializedName())
                                  .then(IMCCommand.newLiteral("on").executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, override, true))))
                                  .then(IMCCommand.newLiteral("off").executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, override, false))))
                                  .executes(context -> checkPreConditionAndExecute(context, ctx -> report(ctx, override))));
        }

        return IMCCommand.newLiteral(getName())
                 .then(colonyArgument.executes(this::checkPreConditionAndExecute));
    }
}
