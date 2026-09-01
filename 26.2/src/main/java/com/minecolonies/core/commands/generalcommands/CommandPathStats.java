package com.minecolonies.core.commands.generalcommands;

import com.minecolonies.api.util.Log;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.entity.pathfinding.Pathfinding;
import com.minecolonies.core.entity.pathfinding.PathfindingStats;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;

/**
 * Reports how long path searches wait before anyone gets to them, how long they then take, and how many of them were
 * never going to arrive.
 * <p>
 * The question this answers is "why do my workers stand about": the pathfinding pool is a single thread shared by every
 * citizen, raider and animal in every colony on the server, so when it saturates, workers wait for a path they have
 * already asked for. That wait is invisible from inside the game without this.
 * <p>
 * Per-job measuring is off until {@code /mc pathstats on}, and switching it on clears the counters, so the window is
 * always "since you asked". A lifetime average would hide exactly the spike an operator is trying to catch.
 * <p>
 * Worker-side waiting — a citizen idling because it has no tool or no delivery — is deliberately <em>not</em> here:
 * {@code /mc colony diagnose} already reports every worker's AI state and how long it has held it, which is the same
 * question answered better, and gathering it again would cost time on the server thread every tick.
 */
public class CommandPathStats implements IMCOPCommand
{
    /**
     * How many rows of the per-type table are echoed into chat. The whole table always goes to the server log.
     */
    private static final int CHAT_TYPE_CAP = 6;

    /**
     * A queue wait above this is worth complaining about on its own: it is a fifth of a second, four server ticks, and
     * the audit that prompted this command measured 262 ms on a colony that was visibly struggling.
     */
    private static final double SLOW_QUEUE_MS = 100.0;

    /**
     * Above this occupancy the single pathfinding thread has no room to absorb a burst.
     */
    private static final double BUSY_FRACTION = 0.70;

    /**
     * Above this share of searches failing, the pool is mostly doing work that will be thrown away.
     */
    private static final double FAILING_FRACTION = 0.50;

    /**
     * Above this share of repeats, a result cache would pay for itself.
     */
    private static final double DUPLICATE_FRACTION = 0.25;

    /**
     * One server tick, in nanoseconds. Used to express the queue wait in the unit an operator already thinks in.
     */
    private static final double TICK_NANOS = 50_000_000.0;

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final PathfindingStats.Snapshot stats = PathfindingStats.snapshot();
        final Report report = new Report(context.getSource());

        report.emit(Component.translatable(COMMAND_PATHSTATS_HEADER, window(stats.windowNanos())), ChatFormatting.GOLD);

        // The refusal counter runs even when sampling is off, so it is reported either way. For an operator with a
        // distant outpost it is the single most useful line here.
        emitRefusals(report, stats);

        if (!stats.sampling())
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_SAMPLING_OFF), ChatFormatting.YELLOW);
            Log.getLogger().info(report.getLog());
            return 1;
        }

        if (stats.finished() == 0)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_NO_JOBS), ChatFormatting.GRAY);
            Log.getLogger().info(report.getLog());
            return 1;
        }

        final double windowSeconds = stats.windowNanos() / 1e9;
        // status() rather than getExecutor(): asking about the pool should not build one, and the report has to be
        // able to say that a pool which was replaced is still finishing its backlog. The size and the backlog below
        // are the live pool's own, so neither is inflated by a drain in flight.
        final Pathfinding.PoolStatus pool = Pathfinding.status();
        final int threads = Math.max(1, pool.threads());
        final double waitAvgNanos = stats.waitSamples() == 0 ? 0 : (double) stats.waitNanos() / stats.waitSamples();
        final double computeAvgNanos = (double) stats.computeNanos() / stats.finished();
        final double busy = (double) stats.computeNanos() / (stats.windowNanos() * (double) threads);
        final long failed = stats.finished() - stats.reached();

        report.emit(Component.translatable(COMMAND_PATHSTATS_JOBS,
          String.valueOf(stats.finished()),
          String.format("%.1f", stats.finished() / windowSeconds)));

        report.emit(Component.translatable(COMMAND_PATHSTATS_WAIT,
          duration(waitAvgNanos),
          duration(stats.waitMaxNanos()),
          String.format("%.1f", waitAvgNanos / TICK_NANOS)));

        report.emit(Component.translatable(COMMAND_PATHSTATS_COMPUTE,
          duration(computeAvgNanos),
          duration(stats.computeMaxNanos()),
          String.valueOf(stats.nodesVisited() / stats.finished())));

        report.emit(Component.translatable(COMMAND_PATHSTATS_POOL,
          String.valueOf(threads),
          percent(busy),
          String.valueOf(pool.queued()),
          String.valueOf(stats.queuePeak()),
          String.valueOf(pool.capacity())));

        // The occupancy above is measured against the pool as it is now, so while a replaced pool is still working
        // it is understated -- searching was going on across more, or fewer, threads than the line says. Naming the
        // drain is what keeps the numbers honest without pretending to a precision the window does not have.
        if (pool.drainingPools() > 0)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_DRAINING,
              String.valueOf(pool.drainingPools()),
              String.valueOf(pool.drainingJobs())), ChatFormatting.YELLOW);
        }

        // A partition, and it has to stay one: arrived, stopped short of the destination, or produced nothing at all.
        // Running out of node budget is not a fourth case but a reason, and it overlaps the first two, so it gets its
        // own line rather than a share that would push the total past 100 %.
        report.emit(Component.translatable(COMMAND_PATHSTATS_OUTCOME,
          share(stats.reached(), stats.finished()),
          share(stats.finished() - stats.reached() - stats.noPath(), stats.finished()),
          share(stats.noPath(), stats.finished())));

        report.emit(Component.translatable(COMMAND_PATHSTATS_BUDGET,
          share(stats.nodeLimited(), stats.finished())));

        // What an earlier exit condition could ever be worth. A search does not stop when it reaches its destination:
        // it goes on until the queue hands it a node dearer than the route it found. That tail is recoverable work,
        // and everything else on this report is not, so the first share here is the ceiling on any such change. It is
        // an upper bound twice over: searches that never reach have no tail at all, and the tail as timed includes
        // building the Path, which happens either way.
        // One decimal here and nowhere else on the report: this share is small by nature on a colony whose searches
        // mostly never arrive, and "0%" and "0.4%" are different answers to the question of whether it is worth acting
        // on, where 34% and 35% of arrivals are the same answer.
        report.emit(Component.translatable(COMMAND_PATHSTATS_AFTERREACH,
          fineShare(stats.reachNanosAfter(), stats.computeNanos()),
          fineShare(stats.reachNodesAfter(), stats.nodesVisited()),
          fineShare(stats.reachNanosAfter(), stats.reachNanosBefore() + stats.reachNanosAfter())));

        // And what that tail buys, which is the other half of the same trade: the search can re-parent the destination
        // onto a cheaper chain while it goes on expanding, so the route at the moment of arrival is not always the
        // route returned. This line says how far apart the two are on this world.
        if (stats.qualityJobs() > 0)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_QUALITY,
              fineShare(stats.qualityCostAtReach() - stats.qualityCostFinal(), Math.max(1, stats.qualityCostFinal())),
              fineShare(stats.qualityBlocksAtReach() - stats.qualityBlocksFinal(), Math.max(1, stats.qualityBlocksFinal())),
              fineShare(stats.qualityChanged(), stats.qualityJobs())));
        }

        report.emit(Component.translatable(COMMAND_PATHSTATS_DUPLICATES,
          share(stats.duplicates(), stats.started())));

        emitVerdicts(report, stats, waitAvgNanos, busy, failed);
        emitByType(report, stats);

        Log.getLogger().info(report.getLog());
        return 1;
    }

    /**
     * The "too far" line, which is reported whether or not sampling is on.
     *
     * @param report the report.
     * @param stats  the counters.
     */
    private void emitRefusals(@NotNull final Report report, @NotNull final PathfindingStats.Snapshot stats)
    {
        if (stats.refusedTooFar() == 0)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_NO_REFUSED), ChatFormatting.GRAY);
            return;
        }

        report.emit(Component.translatable(COMMAND_PATHSTATS_REFUSED,
          String.valueOf(stats.refusedTooFar()),
          String.valueOf(stats.lastRefusal())), ChatFormatting.RED);
    }

    /**
     * Turn the numbers into the one or two sentences that say what to do about them. Deliberately short: an operator
     * should be able to read the verdict and stop.
     *
     * @param report       the report.
     * @param stats        the counters.
     * @param waitAvgNanos the average queue wait.
     * @param busy         the fraction of wall time the pool spent searching.
     * @param failed       how many searches did not reach their destination.
     */
    private void emitVerdicts(
      @NotNull final Report report,
      @NotNull final PathfindingStats.Snapshot stats,
      final double waitAvgNanos,
      final double busy,
      final long failed)
    {
        boolean anything = false;

        if (waitAvgNanos / 1e6 > SLOW_QUEUE_MS)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_VERDICT_QUEUE, duration(waitAvgNanos)), ChatFormatting.RED);
            anything = true;
        }
        if (busy > BUSY_FRACTION)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_VERDICT_BUSY, percent(busy)), ChatFormatting.RED);
            anything = true;
        }
        if (stats.finished() > 0 && (double) failed / stats.finished() > FAILING_FRACTION)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_VERDICT_FAILING, share(failed, stats.finished())), ChatFormatting.YELLOW);
            anything = true;
        }
        if (stats.refusedTooFar() > 0)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_VERDICT_TOO_FAR, String.valueOf(stats.refusedTooFar())), ChatFormatting.RED);
            anything = true;
        }
        if (stats.started() > 0 && (double) stats.duplicates() / stats.started() > DUPLICATE_FRACTION)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_VERDICT_DUPLICATES, share(stats.duplicates(), stats.started())), ChatFormatting.YELLOW);
            anything = true;
        }

        if (!anything)
        {
            report.emit(Component.translatable(COMMAND_PATHSTATS_VERDICT_OK), ChatFormatting.GREEN);
        }
    }

    /**
     * The per-search-type table, heaviest first.
     *
     * @param report the report.
     * @param stats  the counters.
     */
    private void emitByType(@NotNull final Report report, @NotNull final PathfindingStats.Snapshot stats)
    {
        if (stats.byType().isEmpty())
        {
            return;
        }

        final List<Map.Entry<Class<?>, PathfindingStats.TypeCounters>> rows = new ArrayList<>(stats.byType().entrySet());
        rows.sort(Comparator.comparingLong((final Map.Entry<Class<?>, PathfindingStats.TypeCounters> e) -> e.getValue().nanos()).reversed());

        final List<String> lines = new ArrayList<>(rows.size());
        for (final Map.Entry<Class<?>, PathfindingStats.TypeCounters> row : rows)
        {
            final PathfindingStats.TypeCounters counters = row.getValue();
            final long count = counters.count();
            if (count == 0)
            {
                continue;
            }
            lines.add(String.format("%-28s %6s searches  %9s each  %6s nodes  %5s arrived  %5s after arriving",
              row.getKey().getSimpleName(),
              count,
              duration((double) counters.nanos() / count),
              counters.nodes() / count,
              share(counters.reached(), count),
              share(counters.afterReach(), counters.nanos())));
        }

        report.section(Component.translatable(COMMAND_PATHSTATS_BY_TYPE), lines, CHAT_TYPE_CAP);
    }

    /**
     * Switch measuring on or off.
     *
     * @param context the context.
     * @param on      whether to measure.
     * @return 1 always.
     */
    private int setSampling(final CommandContext<CommandSourceStack> context, final boolean on)
    {
        PathfindingStats.setSampling(on);
        context.getSource().sendSuccess(() -> Component.translatable(on ? COMMAND_PATHSTATS_STARTED : COMMAND_PATHSTATS_STOPPED), true);
        return 1;
    }

    /**
     * Clear the counters without changing whether measuring is on.
     *
     * @param context the context.
     * @return 1 always.
     */
    private int resetCounters(final CommandContext<CommandSourceStack> context)
    {
        PathfindingStats.reset();
        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_PATHSTATS_CLEARED), true);
        return 1;
    }

    /**
     * Render a duration for a player, choosing a unit that keeps three significant figures without a wall of decimals.
     *
     * @param nanos the duration.
     * @return the display string.
     */
    private static String duration(final double nanos)
    {
        if (nanos >= 1_000_000_000.0)
        {
            return String.format("%.2f s", nanos / 1e9);
        }
        if (nanos >= 10_000_000.0)
        {
            return String.format("%.0f ms", nanos / 1e6);
        }
        if (nanos >= 1_000_000.0)
        {
            return String.format("%.1f ms", nanos / 1e6);
        }
        return String.format("%.2f ms", nanos / 1e6);
    }

    /**
     * Render a fraction as a percentage.
     *
     * @param fraction the fraction.
     * @return the display string.
     */
    private static String percent(final double fraction)
    {
        return String.format("%.0f%%", fraction * 100.0);
    }

    /**
     * Render a part of a whole as a percentage with one decimal, for shares small enough that rounding to a whole
     * percent would lose the answer.
     *
     * @param part  the part.
     * @param whole the whole.
     * @return the display string.
     */
    private static String fineShare(final long part, final long whole)
    {
        return whole == 0 ? "0%" : String.format("%.1f%%", 100.0 * part / whole);
    }

    /**
     * Render a part of a whole as a percentage, tolerating a zero whole.
     *
     * @param part  the part.
     * @param whole the whole.
     * @return the display string.
     */
    private static String share(final long part, final long whole)
    {
        return whole == 0 ? "0%" : percent((double) part / whole);
    }

    /**
     * Render the length of the measuring window.
     *
     * @param nanos the window length.
     * @return the display string.
     */
    private static String window(final long nanos)
    {
        final long seconds = nanos / 1_000_000_000L;
        return seconds < 60 ? seconds + "s" : (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    /**
     * Sends the report to chat while accumulating the complete text for the server log, the same way
     * {@code CommandColonyDiagnose} does it.
     */
    private static final class Report
    {
        private final CommandSourceStack source;
        private final StringBuilder      log = new StringBuilder("\n");

        Report(final CommandSourceStack source)
        {
            this.source = source;
        }

        /**
         * Emit one unstyled line to both chat and the log.
         *
         * @param component the line.
         */
        void emit(final MutableComponent component)
        {
            emit(component, null);
        }

        /**
         * Emit one line to both chat and the log.
         *
         * @param component the line.
         * @param color     the chat colour, or null for the default.
         */
        void emit(final MutableComponent component, @Nullable final ChatFormatting color)
        {
            log.append(component.getString()).append('\n');
            final Component styled = color == null ? component : component.withStyle(color);
            source.sendSuccess(() -> styled, false);
        }

        /**
         * Emit a titled list. The whole list goes to the log, chat gets the first {@code cap} entries.
         *
         * @param title   the section title.
         * @param entries the entries.
         * @param cap     how many entries to show in chat.
         */
        void section(final MutableComponent title, final List<String> entries, final int cap)
        {
            if (entries.isEmpty())
            {
                return;
            }

            emit(title, ChatFormatting.GRAY);
            for (int i = 0; i < entries.size(); i++)
            {
                final String entry = "  " + entries.get(i);
                log.append(entry).append('\n');
                if (i < cap)
                {
                    final Component line = Component.literal(entry);
                    source.sendSuccess(() -> line, false);
                }
            }
        }

        /**
         * @return the complete report for the server log.
         */
        String getLog()
        {
            return log.toString();
        }
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "pathstats";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newLiteral("on").executes(context -> checkPreConditionAndExecute(context, ctx -> setSampling(ctx, true))))
                 .then(IMCCommand.newLiteral("off").executes(context -> checkPreConditionAndExecute(context, ctx -> setSampling(ctx, false))))
                 .then(IMCCommand.newLiteral("reset").executes(context -> checkPreConditionAndExecute(context, this::resetCounters)))
                 .executes(this::checkPreConditionAndExecute);
    }
}
