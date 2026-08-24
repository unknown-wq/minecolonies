package com.minecolonies.core.entity.pathfinding;

import com.minecolonies.api.util.Log;
import com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Static class the handles all the Pathfinding.
 */
public final class Pathfinding
{
    private static final ArrayBlockingQueue<Runnable> jobQueue = new ArrayBlockingQueue<>(10000, false);
    private static       ThreadPoolExecutor           executor;

    /**
     * Minecolonies specific thread factory.
     */
    public static class MinecoloniesThreadFactory implements ThreadFactory
    {
        /**
         * Ongoing thread IDs.
         */
        public static int id;

        @Override
        public Thread newThread(@NotNull final Runnable runnable)
        {
            final Thread thread = new Thread(runnable, "Minecolonies Pathfinding Worker #" + (id++));
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
        // build a pool over the same shared jobQueue, at which point two worker threads pull from one queue and only
        // one of the two pools is the one anybody keeps a reference to.
        //
        // isShutdown as well as null, because shutdown() now really shuts the pool down. On an integrated server a
        // world change stops the server and starts another one in the same JVM; without this the mod would come back
        // holding a dead executor that rejects every submission, and nothing would ever path again until the game was
        // restarted.
        if (executor == null || executor.isShutdown())
        {
            executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, jobQueue, new MinecoloniesThreadFactory());
        }
        return executor;
    }

    /**
     * Stops all running threads in this thread pool
     */
    public static synchronized void shutdown()
    {
        jobQueue.clear();
        if (executor != null)
        {
            // shutdownNow, not shutdown: the queued jobs were just dropped and the running one is searching a world
            // that is going away, so there is nothing worth waiting for. Until this existed the method only emptied
            // the queue and the worker thread outlived the server it was started for -- once per world change in
            // single player.
            executor.shutdownNow();
            executor = null;
        }
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
        job.getResult().startJob(getExecutor());
    }
}
