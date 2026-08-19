package com.minecolonies.core.client.assetfetch;

/**
 * Polled by every long step of the install so a player can back out of a 78 MB download.
 *
 * <p>Cancellation is cooperative and always safe: whatever step notices it throws, the pipeline unwinds
 * through the same path a failure takes, the staging directory is deleted and {@code state.json} is left
 * exactly as it was.</p>
 */
@FunctionalInterface
public interface CancelSignal
{
    /**
     * Whether the install should stop.
     *
     * @return true to stop.
     */
    boolean isCancelled();

    /**
     * A signal that never fires, for callers that do not offer cancellation.
     *
     * @return a never-cancelled signal.
     */
    static CancelSignal never()
    {
        return () -> false;
    }
}
