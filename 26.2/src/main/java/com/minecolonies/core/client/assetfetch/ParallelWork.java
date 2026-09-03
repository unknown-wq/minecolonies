package com.minecolonies.core.client.assetfetch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * Spreads one indexed job of the install over a small, bounded pool of worker threads.
 *
 * <p>Two steps of the install are thousands of independent per-file jobs — unpacking the asset subtree and
 * hashing the staged pack — and both are limited by decompression and SHA-256, that is, by the CPU. Running
 * them on one thread leaves the rest of the machine idle while the player waits.</p>
 *
 * <p>The pool is deliberately modest and short-lived. It has at most {@value #MAX_WORKERS} threads and never
 * more than there are cores or items, it is created for one call and shut down before that call returns, and
 * its threads are daemons with a recognisable name. Nothing here ever runs on the client thread: the whole
 * pipeline is already started on the installer's own thread by {@link AssetInstaller}, and this only widens
 * that one thread into a handful for the duration of a step.</p>
 *
 * <p><b>Order.</b> Workers pull indices off a shared cursor, so items finish in no particular order. Every
 * caller therefore writes its results into a slot of its own and assembles them in index order afterwards,
 * which keeps the outcome — the files on disk, and the order paths are listed in an error — exactly what a
 * sequential run produced.</p>
 *
 * <p><b>Failure.</b> The first throwable wins and is handed back to the caller; the remaining workers notice
 * and stop pulling. Cancellation is the same mechanism with {@link InstallCancelledException} as the
 * throwable, so a cancelled step unwinds the way it always did.</p>
 */
final class ParallelWork
{
    /**
     * Upper bound on worker threads, whatever the machine has. The work is I/O-backed enough that more than a
     * handful buys nothing, and the installer has no business taking over a player's machine.
     */
    private static final int MAX_WORKERS = 8;

    /**
     * How often, in items, the cancellation signal is polled.
     */
    private static final int CANCEL_STRIDE = 64;

    /**
     * Private constructor to hide the public one.
     */
    private ParallelWork()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * How many workers a job of the given size gets.
     *
     * @param items the number of items.
     * @return the worker count, at least one.
     */
    static int workers(final int items)
    {
        return Math.max(1, Math.min(Math.min(items, MAX_WORKERS), Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Runs {@code task} once for every index in {@code [0, items)}.
     *
     * @param threadName    what to call the worker threads, so they are recognisable in a thread dump.
     * @param items         how many indices to run.
     * @param scratchSize   size of the per-worker scratch buffer handed to the task; 0 for none.
     * @param task          the work; called from several threads at once, never twice for the same index.
     * @param progress      called with the running completed count; may be null.
     * @param progressStride how many completions between progress calls.
     * @param cancelled     polled while running; may be null.
     * @throws AssetInstallException if the task failed, or the run was cancelled.
     */
    static void run(final String threadName, final int items, final int scratchSize, final Task task,
        final IntConsumer progress, final int progressStride, final CancelSignal cancelled) throws AssetInstallException
    {
        if (items <= 0)
        {
            return;
        }

        final int workers = workers(items);
        final AtomicInteger cursor = new AtomicInteger();
        final AtomicInteger done = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        if (workers == 1)
        {
            drain(items, cursor, done, failure, task, new byte[scratchSize], progress, progressStride, cancelled);
        }
        else
        {
            final ExecutorService pool = Executors.newFixedThreadPool(workers, runnable ->
            {
                final Thread thread = new Thread(runnable, threadName);
                thread.setDaemon(true);
                return thread;
            });
            try
            {
                final List<Callable<Void>> jobs = new ArrayList<>(workers);
                for (int worker = 0; worker < workers; worker++)
                {
                    final byte[] scratch = new byte[scratchSize];
                    jobs.add(() ->
                    {
                        drain(items, cursor, done, failure, task, scratch, progress, progressStride, cancelled);
                        return null;
                    });
                }
                pool.invokeAll(jobs);
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, new InstallCancelledException());
            }
            finally
            {
                pool.shutdownNow();
            }
        }

        rethrow(failure.get());
    }

    /**
     * One worker's loop: take the next index until they run out or somebody failed.
     *
     * @param items          how many indices there are.
     * @param cursor         the shared next-index counter.
     * @param done           the shared completed counter.
     * @param failure        where the first throwable is parked.
     * @param task           the work.
     * @param scratch        this worker's scratch buffer.
     * @param progress       the progress sink; may be null.
     * @param progressStride how many completions between progress calls.
     * @param cancelled      the cancellation signal; may be null.
     */
    private static void drain(final int items, final AtomicInteger cursor, final AtomicInteger done,
        final AtomicReference<Throwable> failure, final Task task, final byte[] scratch,
        final IntConsumer progress, final int progressStride, final CancelSignal cancelled)
    {
        int index;
        while ((index = cursor.getAndIncrement()) < items)
        {
            if (failure.get() != null)
            {
                return;
            }
            if (cancelled != null && (index & (CANCEL_STRIDE - 1)) == 0 && cancelled.isCancelled())
            {
                failure.compareAndSet(null, new InstallCancelledException());
                return;
            }

            try
            {
                task.run(index, scratch);
            }
            catch (final Throwable t)
            {
                failure.compareAndSet(null, t);
                return;
            }

            final int finished = done.incrementAndGet();
            if (progress != null && finished % progressStride == 0)
            {
                progress.accept(finished);
            }
        }
    }

    /**
     * Re-throws what a worker caught, on the caller's thread and with its own type kept.
     *
     * @param failure the parked throwable, or null.
     * @throws AssetInstallException if the failure was one, or was anything else that is not unchecked.
     */
    private static void rethrow(final Throwable failure) throws AssetInstallException
    {
        switch (failure)
        {
            case null -> { }
            case final AssetInstallException e -> throw e;
            case final RuntimeException e -> throw e;
            case final Error e -> throw e;
            default -> throw new AssetInstallException(failure.getMessage(), failure);
        }
    }

    /**
     * One item of a parallel job.
     */
    @FunctionalInterface
    interface Task
    {
        /**
         * Runs one item.
         *
         * @param index   which item, in {@code [0, items)}.
         * @param scratch a buffer private to the calling worker for the length of the job.
         * @throws AssetInstallException if this item failed.
         */
        void run(int index, byte[] scratch) throws AssetInstallException;
    }
}
