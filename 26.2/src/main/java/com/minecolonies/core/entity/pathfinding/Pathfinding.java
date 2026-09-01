package com.minecolonies.core.entity.pathfinding;

import com.minecolonies.api.util.Log;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Static class the handles all the Pathfinding.
 */
public final class Pathfinding
{
    /**
     * Bounds on the pool size, matching the range {@code pathfindingthreads} is declared with.
     */
    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 8;

    /**
     * How many jobs a pool will hold before its queue refuses more. Every pool gets its own queue of this size, so a
     * pool that is draining keeps the backlog it was given and the pool that replaced it starts empty.
     */
    private static final int QUEUE_CAPACITY = 10000;

    /**
     * The pool everything is submitted to. Volatile so the size and backlog can be read for a report without taking
     * the lock that submissions and switches share.
     */
    private static volatile ThreadPoolExecutor executor;

    /**
     * Pools that have been replaced and are working through what was already on them. Entries are dropped once they
     * have terminated; a switch that finds none has nothing to do here.
     */
    private static final List<ThreadPoolExecutor> draining = new ArrayList<>();

    /**
     * The size asked for by the command, or 0 while nobody has asked. Kept separate from the config value so that a
     * live change is not written to disk and does not survive a restart, and so that the config still says what the
     * server will come back up with.
     */
    private static int requestedThreads = 0;

    /**
     * Minecolonies specific thread factory.
     */
    public static class MinecoloniesThreadFactory implements ThreadFactory
    {
        /**
         * Ongoing thread IDs. Atomic because worker threads are not only made at submit time under the class lock:
         * a pool replaces a dead worker from one of its own threads, and while a resize is draining, two pools are
         * alive and can both be doing so at once.
         */
        private static final java.util.concurrent.atomic.AtomicInteger id = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public Thread newThread(@NotNull final Runnable runnable)
        {
            final Thread thread = new Thread(runnable, "Minecolonies Pathfinding Worker #" + id.getAndIncrement());
            thread.setDaemon(true);

            thread.setUncaughtExceptionHandler((thread1, throwable) -> Log.getLogger().error("Minecolonies Pathfinding Thread errored! ", throwable));
            return thread;
        }
    }

    /**
     * Creates a new thread pool for pathfinding jobs
     *
     * @return the threadpool executor.
     */
    public static synchronized ThreadPoolExecutor getExecutor()
    {
        // synchronized because this is reached from the server thread, from client-side navigation and from the
        // fisherman's water search, and the lazy check below is not atomic: two callers could both see null and both
        // build a pool, at which point two worker sets are running and only one of the two pools is the one anybody
        // keeps a reference to.
        //
        // isShutdown as well as null, because shutdown() now really shuts the pool down. On an integrated server a
        // world change stops the server and starts another one in the same JVM; without this the mod would come back
        // holding a dead executor that rejects every submission, and nothing would ever path again until the game was
        // restarted.
        if (executor == null || executor.isShutdown())
        {
            executor = newPool(threadCount());
        }
        return executor;
    }

    /**
     * Build a pool of the given size.
     * <p>
     * The queue is constructed here rather than shared, and that is load-bearing rather than tidiness: a switch leaves
     * the replaced pool running until it has finished what it was given, so two pools are alive at once, and two pools
     * over one queue means each one's workers pull the other one's jobs. With a queue apiece, "what is still on the
     * old pool" is a question that has an answer.
     *
     * @param threads how many workers.
     * @return the pool.
     */
    private static ThreadPoolExecutor newPool(final int threads)
    {
        return new ThreadPoolExecutor(threads, threads, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY, false), new MinecoloniesThreadFactory());
    }

    /**
     * How many worker threads to build the pool with.
     * <p>
     * The pool size was hard-coded to 1 for as long as this class has existed, and 1 is still the default, so the
     * behaviour of a server that does not set {@code pathfindingthreads} and never runs {@code /mc debug maxpool} is
     * unchanged.
     * <p>
     * The config is the value the server starts with; a size asked for at runtime overrides it for the rest of the
     * server's life, because an operator who has just changed the pool does not expect the next pool this class builds
     * to be the old size again.
     * <p>
     * The config is not necessarily loaded when the first path is asked for -- a path job can be raised from a
     * world that is coming up -- so a failure to read it falls back to one thread rather than taking the caller
     * down.
     *
     * @return the number of worker threads, at least one.
     */
    private static int threadCount()
    {
        if (requestedThreads != 0)
        {
            return requestedThreads;
        }

        try
        {
            return Math.max(MIN_THREADS, MineColonies.getConfig().getServer().pathfindingThreads.get());
        }
        catch (final Exception e)
        {
            Log.getLogger().warn("Could not read the pathfinding thread count, falling back to one thread", e);
            return MIN_THREADS;
        }
    }

    /**
     * Change the size of the pool on a running server.
     * <p>
     * Nothing is cancelled and nothing is moved. A new pool of the requested size is built and published first, so
     * that every submission from this point on lands on it, and only then is the old pool told to shut down -- which
     * for a {@link ThreadPoolExecutor} means "accept nothing further, finish the queue, then let the threads exit".
     * Jobs that were running go on running on the threads they started on, and jobs that were waiting are still waited
     * on by the workers that were going to take them. The old pool is kept in {@link #draining} only so that a server
     * stop can still reach it.
     * <p>
     * Publishing before shutting down is what makes a rejection impossible: submissions take the same lock as this
     * method, so a caller either submitted before the switch began -- to a pool that was still accepting -- or is
     * holding for the lock and will read the new pool.
     *
     * @param threads the size wanted, clamped into range.
     * @return the size the pool now has.
     */
    public static synchronized int setPoolSize(final int threads)
    {
        final int wanted = Math.max(MIN_THREADS, Math.min(MAX_THREADS, threads));
        requestedThreads = wanted;

        reapDrained();

        final ThreadPoolExecutor previous = executor;
        if (previous != null && !previous.isShutdown() && previous.getCorePoolSize() == wanted)
        {
            return wanted;
        }

        executor = newPool(wanted);

        if (previous != null && !previous.isShutdown())
        {
            final long handed = previous.getTaskCount();
            final long done = previous.getCompletedTaskCount();
            previous.shutdown();
            draining.add(previous);

            Log.getLogger()
              .info("Pathfinding pool resized from {} to {} threads; {} of {} jobs left on the old pool to finish",
                previous.getCorePoolSize(),
                wanted,
                handed - done,
                handed);
        }

        return wanted;
    }

    /**
     * Drop the pools that have finished. Called under the lock from the two places that can care.
     */
    private static void reapDrained()
    {
        for (final Iterator<ThreadPoolExecutor> iterator = draining.iterator(); iterator.hasNext(); )
        {
            if (iterator.next().isTerminated())
            {
                iterator.remove();
            }
        }
    }

    /**
     * What the pool looks like right now, for a report.
     *
     * @param threads        workers on the pool being submitted to.
     * @param queued         jobs waiting on it.
     * @param capacity       how many it would hold.
     * @param drainingPools  how many replaced pools have not finished yet.
     * @param drainingJobs   jobs still owed by those pools, queued or running.
     */
    public record PoolStatus(int threads, int queued, int capacity, int drainingPools, long drainingJobs) {}

    /**
     * Read the pool without building one. {@link #getExecutor()} would construct a pool as a side effect of being
     * asked about it, which is not what a report wants.
     *
     * @return the current state of the pool and of anything still draining.
     */
    public static synchronized PoolStatus status()
    {
        reapDrained();

        long owed = 0;
        for (final ThreadPoolExecutor pool : draining)
        {
            owed += pool.getTaskCount() - pool.getCompletedTaskCount();
        }

        final ThreadPoolExecutor current = executor;
        if (current == null || current.isShutdown())
        {
            return new PoolStatus(threadCount(), 0, QUEUE_CAPACITY, draining.size(), owed);
        }

        return new PoolStatus(current.getCorePoolSize(),
          current.getQueue().size(),
          current.getQueue().size() + current.getQueue().remainingCapacity(),
          draining.size(),
          owed);
    }

    /**
     * Stops all running threads in this thread pool
     */
    public static synchronized void shutdown()
    {
        // Every pool, not just the current one: a switch leaves the replaced pool alive on purpose, and a world that
        // is going away has to take those with it too or their workers outlive the server they were started for.
        if (executor != null)
        {
            // shutdownNow, not shutdown: the queued jobs are searching a world that is going away, so there is
            // nothing worth waiting for. Until this existed the method only emptied the queue and the worker thread
            // outlived the server it was started for -- once per world change in single player.
            executor.shutdownNow();
            executor = null;
        }

        for (final ThreadPoolExecutor pool : draining)
        {
            pool.shutdownNow();
        }
        draining.clear();

        // The size asked for belonged to the server that is stopping. The next one reads the config again, which is
        // what the config is for.
        requestedThreads = 0;
    }

    private Pathfinding()
    {
        //Hides default constructor.
    }

    /**
     * Add a job to the queue for processing.
     *
     * @param job PathJob
     */
    public static void enqueue(@NotNull final AbstractPathJob job)
    {
        submit(job.getResult());
    }

    /**
     * Hand a result's job to the pool.
     * <p>
     * Synchronized on the same monitor as {@link #setPoolSize(int)} so that reading which pool is current and
     * submitting to it are one step. Split in two they are not: a switch landing between them would submit to a pool
     * that had just been shut down, and a shut-down pool answers a submission with
     * {@link java.util.concurrent.RejectedExecutionException} -- on the server thread, inside an entity tick.
     *
     * @param result the result whose job to run.
     */
    public static synchronized void submit(@Nullable final PathResult<?> result)
    {
        if (result != null)
        {
            result.startJob(getExecutor());
        }
    }
}
