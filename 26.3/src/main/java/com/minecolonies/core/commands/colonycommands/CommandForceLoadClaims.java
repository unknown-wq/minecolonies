package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;
import static com.minecolonies.core.commands.colonycommands.CommandColonyInfo.ID_TEXT;
import static com.minecolonies.core.commands.colonycommands.CommandColonyInfo.NAME_TEXT;

/**
 * Turn one colony's whole-claim force-loading on or off without editing a toml or restarting.
 * <p>
 * Deliberately per colony rather than server-wide. The cost of the setting is proportional to how much ground a colony
 * owns -- roughly one extra player's worth of simulated area per 441 claimed chunks -- so it is a decision about one
 * colony, not about a server: the town somebody plays in can be on while every other colony stays off. The server
 * config remains as the default for colonies nobody has decided about.
 * <p>
 * Operator-gated, not officer-gated, because the price is paid by the server rather than by the colony. That is also
 * why this is a command rather than a row in the town hall settings GUI: those are editable by any colony officer, and
 * they are two-state, where this needs three -- on, off, and "no opinion, follow the config".
 */
public class CommandForceLoadClaims implements IMCOPCommand
{
    /**
     * Argument literals. Three, not two: {@code default} is what clears a colony's own answer and hands it back to the
     * server config.
     */
    private static final String ARG_ON      = "on";
    private static final String ARG_OFF     = "off";
    private static final String ARG_DEFAULT = "default";

    /**
     * Report the current state.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return report(context, null, false);
    }

    /**
     * Set the colony's own answer, then report the result.
     *
     * @param context the context of the command execution.
     * @param value   true, false, or null to go back to the server config.
     * @return 1.
     */
    private int set(final CommandContext<CommandSourceStack> context, @Nullable final Boolean value)
    {
        return report(context, value, true);
    }

    /**
     * Shared body of both forms.
     *
     * @param context the context of the command execution.
     * @param value   the value to set, if setting.
     * @param setting whether to set at all.
     * @return 1 on success, 0 if the colony could not be read on the server.
     */
    private int report(final CommandContext<CommandSourceStack> context, @Nullable final Boolean value, final boolean setting)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(colony instanceof final Colony serverColony))
        {
            context.getSource().sendFailure(Component.literal("Colony " + colony.getID() + " is not loaded on this server."));
            return 0;
        }

        if (setting)
        {
            final boolean was = serverColony.isForceLoadAllClaims();
            serverColony.setForceLoadAllClaimsOverride(value);
            final boolean now = serverColony.isForceLoadAllClaims();

            Log.getLogger()
              .info("Colony " + serverColony.getID() + " (" + serverColony.getName() + ") force-load-whole-claim set to "
                      + (value == null ? "server default" : value.toString()) + " by " + context.getSource().getTextName()
                      + "; effective value " + was + " -> " + now + ".");
        }

        final ColonyChunkReport report = ColonyChunkReport.gather(context.getSource().getServer(), serverColony);

        context.getSource()
          .sendSuccess(() -> Component.literal(ID_TEXT)
            .append(Component.literal("" + colony.getID()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" " + NAME_TEXT))
            .append(Component.literal("" + colony.getName()).withStyle(ChatFormatting.YELLOW)), true);
        report.send(context.getSource());

        if (setting && report.enabled)
        {
            // The sweep runs on the colony's slow tick, and only while the force-load timer is alive, which an
            // owner or officer standing in the colony is what starts. Saying so beats the operator watching an
            // unchanged number and assuming the switch is broken.
            context.getSource()
              .sendSuccess(() -> Component.literal(
                  "  Takes effect within 500 ticks (25s), and only while the colony's force-load timer is running -- an owner or officer has to be in the colony to start it.")
                .withStyle(ChatFormatting.GRAY), true);
        }

        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "forceloadclaims";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        // Three literals rather than a boolean argument, because the third state has to be reachable: "default" is
        // what gives a colony back to the server config after it has been set. They also tab-complete, which
        // BoolArgumentType's true/false does less readably for a switch an operator meets once.
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newLiteral(ARG_ON)
                                 .executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, true))))
                         .then(IMCCommand.newLiteral(ARG_OFF)
                                 .executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, false))))
                         .then(IMCCommand.newLiteral(ARG_DEFAULT)
                                 .executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, null))))
                         .executes(this::checkPreConditionAndExecute));
    }
}
