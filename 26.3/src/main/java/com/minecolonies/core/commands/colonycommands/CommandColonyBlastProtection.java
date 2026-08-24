package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.compatibility.simpleplanes.SimplePlanesBlastGuard;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_BLASTPROTECTION_STATE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_BLASTPROTECTION_SUCCESS;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Turns blast protection on or off for one colony.
 * <p>
 * The server config {@code turnoffexplosionsincolonies} says <em>how much</em> a colony is shielded and says it
 * for every colony on the server at once. This is the other half of the switch: a single colony that would
 * rather be blown up — a testing colony, a PvP colony, one whose owner simply prefers craters — turns itself
 * off here without changing anybody else's game.
 * <p>
 * {@code /mc colony blastprotection <colony>} on its own reports the current state rather than changing it, and
 * says what the protection does and does not cover. See {@link SimplePlanesBlastGuard}.
 */
public class CommandColonyBlastProtection implements IMCOPCommand
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

        final String policy = MineColonies.getConfig().getServer().turnOffExplosionsInColonies.get().name();
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_BLASTPROTECTION_STATE,
            colony.getName(),
            serverColony.isBlastProtection(),
            policy), true);
        return 1;
    }

    /**
     * Throw the switch.
     *
     * @param context the context of the command execution.
     * @param on      whether this colony should be shielded.
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

        serverColony.setBlastProtection(on);
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_BLASTPROTECTION_SUCCESS, colony.getName(), on), true);
        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "blastprotection";
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
