package com.minecolonies.core.commands.generalcommands;

import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.entity.pathfinding.Pathfinding;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_MAXPOOL_CURRENT;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_MAXPOOL_DRAINING;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_MAXPOOL_SET;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_MAXPOOL_UNCHANGED;

/**
 * Reads and changes how many worker threads the pathfinding pool runs, on a live server.
 * <p>
 * The counterpart of the {@code pathfindingthreads} config setting, which is only read when a pool is built and
 * therefore only takes effect on a restart. This is the same number, changed now: {@code /mc debug maxpool} reports,
 * {@code /mc debug maxpool <1-8>} switches.
 * <p>
 * A switch costs nothing that was already asked for. The new pool takes every submission from the moment the command
 * returns, and the pool it replaced goes on running what it was already holding until that is finished, then stops by
 * itself -- so raising the size does not orphan the searches that are mid-flight, and lowering it does not throw away
 * a backlog. Which is why the change is not written to the config: it is a knob to turn while watching
 * {@code /mc pathstats}, and the value a server should come back up with is a separate decision.
 * <p>
 * The size is worth turning up only when {@code /mc pathstats} says the queue is the bottleneck. More threads read the
 * live world through more {@code ChunkCache}s at once, and vanilla's block storage is not written for concurrent
 * readers; that is already true at one thread, and more threads widen the window rather than opening a new one.
 */
public class CommandMaxPool implements IMCOPCommand
{
    /**
     * Name of the size argument.
     */
    private static final String SIZE_ARG = "threads";

    /**
     * Report the size the pool has now, and anything still finishing on a pool that was replaced.
     *
     * @param context the context of the command execution.
     * @return 1 always.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final Pathfinding.PoolStatus status = Pathfinding.status();

        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_MAXPOOL_CURRENT,
          String.valueOf(status.threads()),
          String.valueOf(status.queued()),
          String.valueOf(Pathfinding.MIN_THREADS),
          String.valueOf(Pathfinding.MAX_THREADS)), false);

        reportDraining(context, status);
        return 1;
    }

    /**
     * Switch the pool over.
     *
     * @param context the context of the command execution.
     * @return 1 always.
     */
    private int setSize(final CommandContext<CommandSourceStack> context)
    {
        final int previous = Pathfinding.status().threads();
        final int current = Pathfinding.setPoolSize(IntegerArgumentType.getInteger(context, SIZE_ARG));

        // Read the drain back after the switch, not before: what is interesting is what the outgoing pool was left
        // holding, and before the switch there is no outgoing pool.
        final Pathfinding.PoolStatus status = Pathfinding.status();

        if (previous == current)
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_MAXPOOL_UNCHANGED, String.valueOf(current)), false);
        }
        else
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_MAXPOOL_SET,
              String.valueOf(previous),
              String.valueOf(current)).withStyle(ChatFormatting.GREEN), true);
        }

        reportDraining(context, status);
        return 1;
    }

    /**
     * Say what a replaced pool still owes, when there is one.
     *
     * @param context the context of the command execution.
     * @param status  the pool state to report.
     */
    private void reportDraining(final CommandContext<CommandSourceStack> context, final Pathfinding.PoolStatus status)
    {
        if (status.drainingPools() == 0)
        {
            return;
        }

        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_MAXPOOL_DRAINING,
          String.valueOf(status.drainingPools()),
          String.valueOf(status.drainingJobs())).withStyle(ChatFormatting.YELLOW), false);
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "maxpool";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(SIZE_ARG,
                     // Bounded here as well as in Pathfinding so that a size outside the range is refused with
                     // Brigadier's own error, naming the limits, instead of being silently clamped.
                     IntegerArgumentType.integer(Pathfinding.MIN_THREADS, Pathfinding.MAX_THREADS))
                         .executes(context -> checkPreConditionAndExecute(context, this::setSize)))
                 .executes(this::checkPreConditionAndExecute);
    }
}
