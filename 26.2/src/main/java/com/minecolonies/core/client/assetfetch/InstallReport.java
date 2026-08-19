package com.minecolonies.core.client.assetfetch;

import java.util.Collections;
import java.util.List;

/**
 * The terminal result of an install run: what happened, and enough detail to say so on screen.
 *
 * <p>Delivered once, to {@link InstallListener#onFinished} and as the value of the future returned by
 * {@link AssetInstaller#start}. A run that fails has already cleaned up after itself — the staging directory
 * is gone and {@code state.json} is untouched — so a caller's only job on failure is to show
 * {@link #reason()} and offer the manual escape hatch.</p>
 *
 * @param outcome        how the run ended.
 * @param sourceId       the source that produced the install, or null when none did.
 * @param sourceUrl      where it came from, or null.
 * @param jarSha256      the accepted whole-jar hash, or null.
 * @param bytes          how many bytes were transferred in the successful attempt, or 0.
 * @param filesExtracted how many files came out of the jar.
 * @param filesPatched   how many files the patch bundle wrote.
 * @param filesVerified  how many files matched the manifest.
 * @param filesRemoved   how many files the manifest prune deleted.
 * @param packBytes      the installed pack's size on disk.
 * @param reason         a player-showable description of the outcome. Never null.
 * @param attempts       every source tried, in order, successful or not.
 */
public record InstallReport(Outcome outcome, String sourceId, String sourceUrl, String jarSha256, long bytes,
    int filesExtracted, int filesPatched, int filesVerified, int filesRemoved, long packBytes,
    String reason, List<SourceAttempt> attempts)
{
    /**
     * Normalises the attempt list so callers never see null or a mutable view.
     *
     * @param outcome        how the run ended.
     * @param sourceId       the source that produced the install, or null.
     * @param sourceUrl      where it came from, or null.
     * @param jarSha256      the accepted whole-jar hash, or null.
     * @param bytes          bytes transferred.
     * @param filesExtracted files extracted.
     * @param filesPatched   files patched.
     * @param filesVerified  files verified.
     * @param filesRemoved   files pruned.
     * @param packBytes      installed size.
     * @param reason         player-showable description.
     * @param attempts       the attempts.
     */
    public InstallReport
    {
        attempts = attempts == null ? List.of() : Collections.unmodifiableList(List.copyOf(attempts));
    }

    /**
     * Whether the assets are installed and verified.
     *
     * @return true on success.
     */
    public boolean succeeded()
    {
        return this.outcome == Outcome.INSTALLED;
    }

    /**
     * How a run ended.
     */
    public enum Outcome
    {
        /**
         * The pack is installed, verified and recorded; the caller should reload resources.
         */
        INSTALLED,

        /**
         * No source produced a usable jar. {@link InstallReport#attempts()} says why, per source.
         */
        NO_SOURCE,

        /**
         * A jar arrived but the install failed afterwards — a patch that would not apply, a file that did not
         * verify. Nothing was changed.
         */
        FAILED,

        /**
         * The player cancelled. Nothing was changed.
         */
        CANCELLED
    }
}
