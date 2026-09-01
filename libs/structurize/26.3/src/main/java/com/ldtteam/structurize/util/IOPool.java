package com.ldtteam.structurize.util;

import com.ldtteam.structurize.api.Log;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

/**
 * Class handling our IO pool.
 */
public final class IOPool
{
    /**
     * Hold our IO queue.
     */
    private static final BlockingQueue<Runnable> ioQueue = new LinkedBlockingDeque<>();

    /**
     * Holds the specific executor for the queue.
     */
    private static ThreadPoolExecutor executor;

    /**
     * Structurize specific thread factory.
     */
    public static class StructurizeThreadFactory implements ThreadFactory
    {
        /**
         * Ongoing thread IDs.
         */
        public static int id;

        @Override
        public Thread newThread(@NotNull final Runnable runnable)
        {
            final Thread thread = new Thread(runnable, "Structurize IO Worker #" + (id++));
            thread.setDaemon(true);

            thread.setUncaughtExceptionHandler((thread1, throwable) -> Log.getLogger().error("Structurize IO Thread errored! ", throwable));
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
        if (executor == null)
        {
            // Core size 2, not 1. A ThreadPoolExecutor only starts a thread beyond corePoolSize when the
            // queue refuses an offer, and ioQueue is an unbounded LinkedBlockingDeque, which never does --
            // so with a core of 1 the maximumPoolSize of 2 was unreachable and every blueprint load in the
            // game queued behind a single thread. allowCoreThreadTimeOut keeps the old idle behaviour: both
            // threads still retire after the same 10 seconds without work.
            executor = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, ioQueue, new StructurizeThreadFactory());
            executor.allowCoreThreadTimeOut(true);
        }
        return executor;
    }

    /**
     * Stops all running threads in this thread pool
     */
    public static synchronized void shutdown()
    {
        getExecutor().shutdownNow();
        ioQueue.clear();
        executor = null;
    }

    private IOPool()
    {
        //Hides default constructor.
    }

    /**
     * Submit a task for processing tothe pool.
     * @param task the task to run.
     * @return the future.
     */
    public static <T> Future<T> submit(@NotNull final Callable<T> task)
    {
        return getExecutor().submit(task);
    }


    /**
     * Execute a task to be processed in the pool.
     * @param task the task to run.
     */
    public static void execute(@NotNull final Runnable task)
    {
        getExecutor().execute(task);
    }
}
