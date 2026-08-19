package com.minecolonies.core.client.assetfetch;

/**
 * Progress callbacks for whoever is showing the install on screen.
 *
 * <p>Every method has a no-op default, so a caller implements only what it draws. <b>All of them are called
 * on the installer's own thread</b>, not the client thread: an implementation that touches Minecraft must
 * hand the value over itself (store it in a volatile field the screen reads, or
 * {@code Minecraft.getInstance().execute(...)}).</p>
 *
 * <p>The installer never reloads resources itself. On {@link #onFinished} with a successful report, the UI
 * is the one that calls {@code Minecraft.reloadResourcePacks()}, on the client thread, where that is safe.</p>
 */
public interface InstallListener
{
    /**
     * The run moved to a new stage.
     *
     * @param phase the stage now starting.
     */
    default void onPhase(final InstallPhase phase)
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * A source is about to be tried.
     *
     * @param sourceId    the chain entry's id.
     * @param url         the URL or local path.
     * @param description a short player-showable description of the source.
     */
    default void onSourceStarted(final String sourceId, final String url, final String description)
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Bytes are arriving. Only fires during {@link InstallPhase#DOWNLOADING}, at most once a megabyte.
     *
     * @param transferred bytes received so far.
     * @param total       the expected total, or -1 when the server did not say.
     */
    default void onBytes(final long transferred, final long total)
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Files are being processed. Fires during extract, patch and verify.
     *
     * @param done  how many files are finished.
     * @param total how many are expected, or -1 when not yet known.
     */
    default void onFiles(final int done, final int total)
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * A source did not work out; the installer is moving on to the next one.
     *
     * @param attempt the URL, HTTP status, bytes received and error text.
     */
    default void onSourceFailed(final SourceAttempt attempt)
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * The run is over. Called exactly once, whatever the outcome.
     *
     * @param report what happened.
     */
    default void onFinished(final InstallReport report)
    {
        /*
         * Intentionally left empty.
         */
    }
}
