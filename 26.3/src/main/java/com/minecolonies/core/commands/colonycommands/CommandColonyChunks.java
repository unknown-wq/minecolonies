package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCColonyOfficerCommand;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import java.util.List;
import net.minecraft.server.level.TicketType;

import java.util.HashSet;
import java.util.Set;

import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;
import static com.minecolonies.core.commands.colonycommands.CommandColonyInfo.ID_TEXT;
import static com.minecolonies.core.commands.colonycommands.CommandColonyInfo.NAME_TEXT;

public class CommandColonyChunks implements IMCColonyOfficerCommand
{
    /**
     * What happens when the command is executed after preConditions are successful.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);

        // The level has to be the colony's own, not the caller's: chunk levels and tickets are per dimension, and
        // asking the overworld about a nether colony's chunks answers about entirely different ground.
        final ServerLevel colonyLevel = context.getSource().getServer().getLevel(colony.getDimension());
        final DistanceManager distanceManager =
          colonyLevel == null ? null : colonyLevel.getChunkSource().chunkMap.getDistanceManager();

        Set<TicketType> types = new HashSet<>();

        if (distanceManager != null)
        {
            for (final Long chunkLong : colony.getLoadedChunks())
            {
                // 26.2: DistanceManager#tickets moved into TicketStorage (widened in the access widener), Ticket
                // lost its type parameter, and the per-chunk lookup is TicketStorage#getTickets(long).
                final List<Ticket> tickets = distanceManager.ticketStorage.getTickets(chunkLong);
                for (final Ticket ticket : tickets)
                {
                    types.add(ticket.getType());
                }
            }
        }

        StringBuilder ticketString = new StringBuilder();
        for (final TicketType type : types)
        {
            ticketString.append("[").append(type).append("]");
        }

        context.getSource()
          .sendSuccess(() -> Component.literal(ID_TEXT)
            .append(Component.literal("" + colony.getID()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" " + NAME_TEXT))
            .append(Component.literal("" + colony.getName()).withStyle(ChatFormatting.YELLOW)), true);
        context.getSource().sendSuccess(() -> Component.literal("Loaded chunks:").append(Component.literal(" " + colony.getLoadedChunkCount()).withStyle(ChatFormatting.YELLOW)), true);

        // Shared with /mc colony forceloadclaims rather than duplicated: two commands answering the same question
        // differently is how a diagnostic stops being trusted.
        ColonyChunkReport.gather(context.getSource().getServer(), colony).send(context.getSource());

        context.getSource().sendSuccess(() -> Component.translatableEscape("Ticket types: ").append(Component.literal(ticketString.toString()).withStyle(ChatFormatting.YELLOW)), true);

        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "chunkstatus";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
          .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id()).executes(this::checkPreConditionAndExecute));
    }
}
