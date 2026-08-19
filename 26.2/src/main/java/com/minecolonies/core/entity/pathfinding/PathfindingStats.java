package com.minecolonies.core.entity.pathfinding;

import com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob;
import com.minecolonies.core.entity.pathfinding.pathjobs.IDestinationPathJob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counters behind {@code /mc pathstats}: how long path jobs wait in the queue before anyone looks at them, how long
 * they then take, and how many of them were never going to work.
 *
 * <p><b>Cost.</b> Everything per-job is behind {@link #isSampling()}, which is {@code false} until a player asks for
 * it. While it is off, the whole of this class costs two reads of a {@code volatile boolean} per path job — the
 * accumulators are never touched and nothing is allocated. While it is on, a job costs two {@code System.nanoTime()}
 * calls, a handful of {@link LongAdder} increments, one {@link ConcurrentHashMap} lookup on a {@code Class} key and one
 * probe of a fixed-size table; no allocation on the job path at all. A job that <em>reaches</em> its destination pays
 * more: a third {@code nanoTime} and two walks of its own parent chain, which is what the post-arrival and route
 * quality lines are measured from. That is O(path length) on a third of jobs, tens of steps each, against the ~9 ms a
 * real {@code PathJobMoveToLocation} takes — noise, but not free, which is why it is a toggle.</p>
 *
 * <p>{@link #recordRefusedTooFar} is the exception and is <em>always</em> counted. It only runs on the path where
 * {@code MinecoloniesAdvancedPathNavigate} has already decided to build an exception and log an error, which costs
 * about a thousand times more than the counter next to it, and it is the single number an operator with a distant
 * outpost most needs to see.</p>
 *
 * <p><b>Threads.</b> Jobs are submitted from the server thread and run on the pathfinding pool, so every accumulator
 * here is a concurrent one and every published field is {@code volatile}. The one value handed across threads is the
 * submission timestamp, written onto the job before {@code ExecutorService#submit} and read at the start of
 * {@code call()}; submitting a task to an executor happens-before the task runs, so that hand-off is ordered by the
 * memory model rather than by luck, and the field is {@code volatile} as well so the ordering is visible in the code.
 * Nothing here reads live world state, so it adds no exposure of the kind {@code AI-SCALE-AUDIT.md} §2.2 records.</p>
 */
public final class PathfindingStats
{
    /**
     * Slots in the recently-seen table used for duplicate detection. A power of two so the index is a mask. 4096 slots
     * cover about thirty seconds of traffic at the rate a thousand-citizen colony produces, which is enough to spot
     * the pattern the audit found (one worker re-asking for the same unreachable place every second or two).
     */
    private static final int RECENT_SLOTS = 4096;

    /**
     * Two searches with the same type, start and destination count as a repeat only if they land this close together.
     */
    private static final long DUPLICATE_WINDOW_NANOS = 30L * 1_000_000_000L;

    /**
     * Whether per-job sampling is running. Off until {@code /mc pathstats on}.
     */
    private static volatile boolean sampling = false;

    /**
     * When the current window opened, from {@link System#nanoTime()}.
     */
    private static volatile long windowStartNanos = System.nanoTime();

    private static final LongAdder submitted      = new LongAdder();
    private static final LongAdder started        = new LongAdder();
    private static final LongAdder finished       = new LongAdder();
    private static final LongAdder waitSamples    = new LongAdder();
    private static final LongAdder waitNanos      = new LongAdder();
    private static final LongAdder computeNanos   = new LongAdder();
    private static final LongAdder reached        = new LongAdder();
    private static final LongAdder noPath         = new LongAdder();
    private static final LongAdder nodeLimited    = new LongAdder();
    private static final LongAdder nodesVisited   = new LongAdder();
    private static final LongAdder duplicates     = new LongAdder();

    /**
     * The post-reach tail: for searches that did reach their destination, how much of their work happened after the
     * moment they first reached it. The search does not stop on reaching — it goes on until the queue hands it a node
     * more expensive than the route it found — so this is the part an earlier exit condition could recover, and
     * measured against {@link #computeNanos} it is the ceiling on what any such change can be worth. Searches that
     * never reach contribute nothing here, which is the point: they have no tail to cut.
     */
    private static final LongAdder reachNodesBefore = new LongAdder();
    private static final LongAdder reachNodesAfter  = new LongAdder();
    private static final LongAdder reachNanosBefore = new LongAdder();
    private static final LongAdder reachNanosAfter  = new LongAdder();

    /**
     * The other half of that trade: what the route looked like at the moment of arrival against what was finally
     * returned. The search can re-parent the destination onto a cheaper chain while it goes on expanding, so leaving
     * early is not free — and how much it costs is a question of measurement, which is what these four are for.
     * Costs are kept in thousandths so a {@link LongAdder} can carry them.
     */
    private static final LongAdder qualityJobs        = new LongAdder();
    private static final LongAdder qualityCostAtReach = new LongAdder();
    private static final LongAdder qualityCostFinal   = new LongAdder();
    private static final LongAdder qualityBlocksAtReach = new LongAdder();
    private static final LongAdder qualityBlocksFinal   = new LongAdder();
    private static final LongAdder qualityChanged       = new LongAdder();

    /**
     * Fixed-point scale for the path costs above.
     */
    private static final double COST_SCALE = 1000.0;

    private static final AtomicLong waitMaxNanos    = new AtomicLong();
    private static final AtomicLong computeMaxNanos = new AtomicLong();
    private static final AtomicLong queuePeak       = new AtomicLong();

    /**
     * Per job type, so a report can say which kind of search is eating the pool.
     */
    private static final Map<Class<?>, TypeCounters> byType = new ConcurrentHashMap<>();

    /**
     * Recently seen search keys and when they were seen, for duplicate detection. Allocated the first time sampling is
     * switched on and reused afterwards, so the job path never allocates. {@link AtomicLongArray} rather than
     * {@code long[]} because a {@code long} write is not guaranteed atomic and the pool is allowed to grow past one
     * thread; a torn read here would silently corrupt the duplicate count.
     */
    private static volatile AtomicLongArray recentKeys  = null;
    private static volatile AtomicLongArray recentTimes = null;

    /**
     * Requests the navigator refused for being further than its hard limit. Always counted, see the class comment.
     */
    private static final LongAdder refusedTooFar = new LongAdder();

    /**
     * Human-readable description of the most recent refusal, or null if there has not been one.
     */
    private static volatile String lastRefusal = null;

    private PathfindingStats()
    {
        // Static only.
    }

    /**
     * @return whether per-job sampling is running.
     */
    public static boolean isSampling()
    {
        return sampling;
    }

    /**
     * Turn per-job sampling on or off. Switching it on clears the counters, so the window always starts at the moment
     * the operator asked for it rather than at some forgotten earlier point.
     *
     * @param on whether to sample.
     */
    public static void setSampling(final boolean on)
    {
        if (on && recentKeys == null)
        {
            recentTimes = new AtomicLongArray(RECENT_SLOTS);
            recentKeys = new AtomicLongArray(RECENT_SLOTS);
        }
        if (on)
        {
            reset();
        }
        sampling = on;
    }

    /**
     * Clear every counter and reopen the window. The refusal counter is cleared too: it is the one number an operator
     * is most likely to want to watch across a specific change, and leaving it running while everything else restarts
     * would make the report mix two different windows.
     */
    public static void reset()
    {
        submitted.reset();
        started.reset();
        finished.reset();
        waitSamples.reset();
        waitNanos.reset();
        computeNanos.reset();
        reached.reset();
        noPath.reset();
        nodeLimited.reset();
        nodesVisited.reset();
        duplicates.reset();
        reachNodesBefore.reset();
        reachNodesAfter.reset();
        reachNanosBefore.reset();
        reachNanosAfter.reset();
        qualityJobs.reset();
        qualityCostAtReach.reset();
        qualityCostFinal.reset();
        qualityBlocksAtReach.reset();
        qualityBlocksFinal.reset();
        qualityChanged.reset();
        waitMaxNanos.set(0);
        computeMaxNanos.set(0);
        queuePeak.set(0);
        byType.clear();
        refusedTooFar.reset();
        lastRefusal = null;

        final AtomicLongArray times = recentTimes;
        if (times != null)
        {
            for (int i = 0; i < RECENT_SLOTS; i++)
            {
                times.set(i, 0L);
            }
        }

        windowStartNanos = System.nanoTime();
    }

    /**
     * Stamp a job with its submission time. Called on the server thread immediately before the job is handed to the
     * executor.
     *
     * @param job the job about to be submitted.
     */
    public static void onSubmit(@NotNull final AbstractPathJob job)
    {
        if (!sampling)
        {
            return;
        }
        job.setSubmitNanos(System.nanoTime());
        submitted.increment();
    }

    /**
     * Note how deep the backlog got. Called on the server thread just after the job was handed to the executor, so the
     * queue length it reads includes the job that was just added.
     *
     * @param executor the pathfinding pool.
     */
    public static void onQueued(@Nullable final ExecutorService executor)
    {
        if (!sampling || !(executor instanceof final ThreadPoolExecutor pool))
        {
            return;
        }
        max(queuePeak, pool.getQueue().size());
    }

    /**
     * Called on the pool thread as a job begins.
     *
     * @param job the job.
     * @return the start timestamp to hand back to {@link #onFinish}, or 0 if this job is not being sampled.
     */
    public static long onStart(@NotNull final AbstractPathJob job)
    {
        if (!sampling)
        {
            return 0L;
        }

        final long now = System.nanoTime();
        final long submittedAt = job.getSubmitNanos();
        if (submittedAt != 0L)
        {
            final long waited = now - submittedAt;
            if (waited >= 0)
            {
                waitNanos.add(waited);
                waitSamples.increment();
                max(waitMaxNanos, waited);
            }
        }

        started.increment();
        countDuplicate(job, now);
        return now;
    }

    /**
     * Called on the pool thread as a job ends, whether it produced a path, produced nothing, threw, or was interrupted.
     *
     * @param job           the job.
     * @param startedNanos  what {@link #onStart} returned; 0 means the job was not sampled.
     * @param path          the produced path, or null.
     * @param visited       how many nodes the search expanded.
     * @param hitNodeLimit  whether the search gave up on its node budget, as recorded by the search loop itself.
     */
    public static void onFinish(
      @NotNull final AbstractPathJob job,
      final long startedNanos,
      @Nullable final Path path,
      final int visited,
      final boolean hitNodeLimit)
    {
        if (startedNanos == 0L || !sampling)
        {
            return;
        }

        final long ended = System.nanoTime();
        final long took = ended - startedNanos;
        computeNanos.add(took);

        final int nodesAtReach = job.getNodesAtReach();
        long afterNanos = 0L;
        if (nodesAtReach >= 0)
        {
            reachNodesBefore.add(nodesAtReach);
            reachNodesAfter.add(Math.max(0, visited - nodesAtReach));

            final long reachedAt = job.getReachNanos();
            if (reachedAt != 0L && reachedAt >= startedNanos && reachedAt <= ended)
            {
                afterNanos = ended - reachedAt;
                reachNanosBefore.add(reachedAt - startedNanos);
                reachNanosAfter.add(afterNanos);

                final long costAtReach = Math.round(job.getReachPathCost() * COST_SCALE);
                final long costFinal    = Math.round(job.getFinalPathCost() * COST_SCALE);
                final int blocksAtReach = job.getReachPathBlocks();
                final int blocksFinal   = job.getFinalPathBlocks();
                qualityJobs.increment();
                qualityCostAtReach.add(costAtReach);
                qualityCostFinal.add(costFinal);
                qualityBlocksAtReach.add(blocksAtReach);
                qualityBlocksFinal.add(blocksFinal);
                if (costAtReach != costFinal || blocksAtReach != blocksFinal)
                {
                    qualityChanged.increment();
                }
            }
        }

        max(computeMaxNanos, took);
        finished.increment();
        nodesVisited.add(visited);

        final boolean didReach = path != null && path.canReach();
        if (didReach)
        {
            reached.increment();
        }
        if (path == null)
        {
            noPath.increment();
        }
        // Deliberately not derived from the node count: see AbstractPathJob#hitNodeLimit. This overlaps the two above
        // rather than partitioning with them -- a search can exhaust its budget and still hand back a path that
        // reaches -- so a report must not add the three together.
        if (hitNodeLimit)
        {
            nodeLimited.increment();
        }

        byType.computeIfAbsent(job.getClass(), key -> new TypeCounters()).record(took, visited, didReach, afterNanos);
    }

    /**
     * Record that the navigator refused a walk order for being beyond its hard distance limit. Always counted; see the
     * class comment for why this one is not behind the toggle.
     *
     * @param who      display name of the entity that asked.
     * @param distance the straight-line distance it asked for.
     * @param from     where it was.
     * @param to       where it wanted to go.
     */
    public static void recordRefusedTooFar(final String who, final double distance, final BlockPos from, final BlockPos to)
    {
        refusedTooFar.increment();
        lastRefusal = String.format("%s, %.0f blocks, from %s to %s", who, distance, from.toShortString(), to.toShortString());
    }

    /**
     * Count this search as a repeat if the same type, start and destination went through recently.
     *
     * @param job the job.
     * @param now the current timestamp.
     */
    private static void countDuplicate(@NotNull final AbstractPathJob job, final long now)
    {
        if (!(job instanceof final IDestinationPathJob destinationJob))
        {
            // Wandering jobs have no destination to repeat, so "the same search twice" is not defined for them.
            return;
        }

        final AtomicLongArray keys = recentKeys;
        final AtomicLongArray times = recentTimes;
        if (keys == null || times == null)
        {
            return;
        }

        final BlockPos destination = destinationJob.getDestination();
        if (destination == null)
        {
            return;
        }

        long key = job.getClass().hashCode();
        key = key * 31L + job.getStart().asLong();
        key = key * 31L + destination.asLong();
        // Never let a legitimate key collide with the "empty slot" marker.
        if (key == 0L)
        {
            key = 1L;
        }

        final int slot = (int) ((key ^ (key >>> 32)) & (RECENT_SLOTS - 1));
        final long previousKey = keys.get(slot);
        final long previousTime = times.get(slot);
        if (previousKey == key && previousTime != 0L && now - previousTime <= DUPLICATE_WINDOW_NANOS)
        {
            duplicates.increment();
        }
        keys.set(slot, key);
        times.set(slot, now);
    }

    /**
     * Raise a running maximum.
     *
     * @param target the maximum to raise.
     * @param value  the candidate.
     */
    private static void max(final AtomicLong target, final long value)
    {
        long current = target.get();
        while (value > current && !target.compareAndSet(current, value))
        {
            current = target.get();
        }
    }

    /**
     * @return an immutable read of everything the report needs, taken as close to one instant as a set of independent
     *     counters allows. The numbers are not a consistent snapshot of a single moment and do not need to be: they are
     *     rates and averages over a window measured in minutes.
     */
    public static Snapshot snapshot()
    {
        return new Snapshot(
          sampling,
          Math.max(1L, System.nanoTime() - windowStartNanos),
          submitted.sum(),
          started.sum(),
          finished.sum(),
          waitSamples.sum(),
          waitNanos.sum(),
          waitMaxNanos.get(),
          computeNanos.sum(),
          computeMaxNanos.get(),
          reached.sum(),
          noPath.sum(),
          nodeLimited.sum(),
          nodesVisited.sum(),
          duplicates.sum(),
          reachNodesBefore.sum(),
          reachNodesAfter.sum(),
          reachNanosBefore.sum(),
          reachNanosAfter.sum(),
          qualityJobs.sum(),
          qualityCostAtReach.sum(),
          qualityCostFinal.sum(),
          qualityBlocksAtReach.sum(),
          qualityBlocksFinal.sum(),
          qualityChanged.sum(),
          queuePeak.get(),
          refusedTooFar.sum(),
          lastRefusal,
          Map.copyOf(byType));
    }

    /**
     * A read of every counter, plus the length of the window it covers.
     *
     * @param sampling      whether per-job sampling was running.
     * @param windowNanos   how long the window has been open.
     * @param submitted     jobs handed to the pool.
     * @param started       jobs the pool began.
     * @param finished      jobs the pool completed.
     * @param waitSamples   how many of those had a usable queue-wait measurement.
     * @param waitNanos     total queue wait.
     * @param waitMaxNanos  worst single queue wait.
     * @param computeNanos  total time spent searching.
     * @param computeMaxNanos worst single search.
     * @param reached       searches that reached their destination.
     * @param noPath        searches that produced no path at all.
     * @param nodeLimited   searches that ran out of node budget.
     * @param nodesVisited  total nodes expanded.
     * @param duplicates    searches that repeated a recent type/start/destination.
     * @param reachNodesBefore nodes expanded up to the moment of reaching, over searches that reached.
     * @param reachNodesAfter  nodes expanded after reaching, over the same searches.
     * @param reachNanosBefore time up to the moment of reaching, over the same searches.
     * @param reachNanosAfter  time after reaching, over the same searches. Includes turning the node chain into a
     *                         {@code Path}, which no exit condition can avoid, so this is an upper bound on the tail.
     * @param qualityJobs        searches contributing to the four numbers below.
     * @param qualityCostAtReach total route cost at the moment of arrival, in thousandths.
     * @param qualityCostFinal   total route cost as finally returned, in thousandths.
     * @param qualityBlocksAtReach total route length in blocks at the moment of arrival.
     * @param qualityBlocksFinal   total route length in blocks as finally returned.
     * @param qualityChanged     how many of those routes the search changed after arriving.
     * @param queuePeak     deepest the backlog was seen to get.
     * @param refusedTooFar walk orders refused for distance.
     * @param lastRefusal   description of the most recent refusal, or null.
     * @param byType        per job type counters.
     */
    public record Snapshot(
      boolean sampling,
      long windowNanos,
      long submitted,
      long started,
      long finished,
      long waitSamples,
      long waitNanos,
      long waitMaxNanos,
      long computeNanos,
      long computeMaxNanos,
      long reached,
      long noPath,
      long nodeLimited,
      long nodesVisited,
      long duplicates,
      long reachNodesBefore,
      long reachNodesAfter,
      long reachNanosBefore,
      long reachNanosAfter,
      long qualityJobs,
      long qualityCostAtReach,
      long qualityCostFinal,
      long qualityBlocksAtReach,
      long qualityBlocksFinal,
      long qualityChanged,
      long queuePeak,
      long refusedTooFar,
      @Nullable String lastRefusal,
      Map<Class<?>, TypeCounters> byType) {}

    /**
     * Counters for one kind of path job. Every field is a concurrent accumulator because the pool may have more than
     * one thread.
     */
    public static final class TypeCounters
    {
        private final LongAdder count      = new LongAdder();
        private final LongAdder nanos      = new LongAdder();
        private final LongAdder nodes      = new LongAdder();
        private final LongAdder reached    = new LongAdder();
        private final LongAdder afterReach = new LongAdder();

        /**
         * Add one finished job of this type.
         *
         * @param took       how long the search took.
         * @param visited    how many nodes it expanded.
         * @param didReach   whether it reached its destination.
         * @param afterNanos how much of {@code took} came after it first reached the destination.
         */
        void record(final long took, final int visited, final boolean didReach, final long afterNanos)
        {
            count.increment();
            nanos.add(took);
            nodes.add(visited);
            afterReach.add(afterNanos);
            if (didReach)
            {
                reached.increment();
            }
        }

        public long count()
        {
            return count.sum();
        }

        public long nanos()
        {
            return nanos.sum();
        }

        public long nodes()
        {
            return nodes.sum();
        }

        public long reached()
        {
            return reached.sum();
        }

        /**
         * @return time this type spent searching on after it had already reached its destination.
         */
        public long afterReach()
        {
            return afterReach.sum();
        }
    }
}
