package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Asks of an <em>installed</em> pack the one question {@link AssetFetch#isReady()} never asked: is all of it
 * still there?
 *
 * <p>{@code isReady()} checks that {@code state.json} says "installed" and that {@code pack.mcmeta} parses.
 * Both can be true of a pack that lost half its files — to an interrupted copy, a disk that filled up, a
 * backup tool, a hand-cleaned cache — and such a pack reads as a finished install, so the command that exists
 * to fetch the assets used to answer "already installed" and do nothing. The player was left with a
 * checkerboarded game and no way out short of deleting the cache directory by hand. This class is what turns
 * that into a question the command can actually answer.</p>
 *
 * <p><b>Read-only, by construction.</b> Nothing here creates, moves or deletes anything, and the only path it
 * ever touches is {@code packRoot} resolved against a manifest entry. Repairing is the install pipeline's
 * job, which stages and swaps; an audit that went round deleting what it disliked could only ever make a
 * damaged install worse.</p>
 *
 * <h2>What counts as damage</h2>
 * <p>A manifest entry is <b>missing</b> when the pack has no regular file at its path, or has one of zero
 * length. Nothing else is judged, and in particular <b>no contents are checked</b>: the manifest is format
 * version 2, a list of paths with no per-file hashes in it (see {@link AssetManifest}), so a file that is
 * present and the wrong size is a thing this build has no way to recognise. The zero-length case is in
 * because it is the shape a half-written file takes and because no file the manifest lists is legitimately
 * empty; it is a judgement about a truncated write, not about contents.</p>
 *
 * <p>Files the pack holds that the manifest does <em>not</em> list are not damage and are not reported. They
 * are harmless — nothing reads them — and the install pipeline prunes them anyway on its next run
 * ({@link PackAssembler}), so counting them as a reason to download 78 MB would be a poor trade.</p>
 *
 * <p>Plain Java over {@link Path} arguments, with no Minecraft or Fabric types in it, exactly like the rest
 * of the pipeline, so it can be run headlessly against a directory.</p>
 */
public final class PackAudit
{
    /**
     * How many missing paths to name in a log line or a report before counting the rest.
     */
    private static final int NAMED = 8;

    /**
     * Private constructor to hide the public one.
     */
    private PackAudit()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Checks an installed pack against the manifest's file list.
     *
     * <p>One {@code stat} per manifest entry — some eight and a half thousand of them, on a directory tree
     * the game has just finished reading as a resource pack, so it is warm in the page cache and costs
     * milliseconds. Cheap enough to do in front of the player who typed the command, which is the point:
     * the answer has to arrive before anything is downloaded.</p>
     *
     * @param packRoot the installed pack root, normally {@link AssetFetch#packDir()}.
     * @param expected the manifest's file set, pack-relative; {@link AssetManifest#files()}.
     * @return what was found. Never null, and never throws: a path that cannot be examined is counted as
     *         missing, which is the answer that gets it fetched again.
     */
    public static Result audit(final Path packRoot, final Set<String> expected)
    {
        final List<String> missing = new ArrayList<>();
        for (final String path : expected)
        {
            if (!isIntact(packRoot.resolve(path)))
            {
                missing.add(path);
            }
        }
        return new Result(expected.size(), missing);
    }

    /**
     * Whether one pack file is there and is not a zero-length stub.
     *
     * @param file the file to look at.
     * @return true when it is a regular file with something in it.
     */
    private static boolean isIntact(final Path file)
    {
        try
        {
            return Files.isRegularFile(file) && Files.size(file) > 0L;
        }
        catch (final IOException | RuntimeException e)
        {
            // Unreadable is as good as absent here, and for the same remedy. Deliberately not logged per
            // file: a pack on a disk that has gone away would write eight thousand lines.
            return false;
        }
    }

    /**
     * What an audit found.
     *
     * @param expected how many files the manifest lists.
     * @param missing  the pack-relative paths that are absent or empty, in manifest order.
     */
    public record Result(int expected, List<String> missing)
    {
        /**
         * Defensive copy, so a caller cannot change a finding after the fact.
         *
         * @param expected how many files the manifest lists.
         * @param missing  the paths that are absent or empty.
         */
        public Result
        {
            missing = List.copyOf(missing);
        }

        /**
         * Whether every file the manifest lists is present.
         *
         * @return true when nothing is missing.
         */
        public boolean ok()
        {
            return this.missing.isEmpty();
        }

        /**
         * How many of the manifest's files the pack actually holds.
         *
         * @return the count.
         */
        public int present()
        {
            return this.expected - this.missing.size();
        }

        /**
         * Names the first few missing paths and counts the rest, for the log.
         *
         * @return the description, or an empty string when nothing is missing.
         */
        public String describe()
        {
            if (ok())
            {
                return "";
            }
            final StringBuilder out = new StringBuilder();
            for (int i = 0; i < Math.min(NAMED, this.missing.size()); i++)
            {
                out.append(i > 0 ? ", " : "").append(this.missing.get(i));
            }
            if (this.missing.size() > NAMED)
            {
                out.append(", and ").append(this.missing.size() - NAMED).append(" more");
            }
            return out.toString();
        }
    }
}
