package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Task C9: makes a staged pack match the manifest exactly, then proves it does.
 *
 * <p>Two steps, in this order and for a reason:</p>
 * <ol>
 *     <li><b>Prune.</b> The upstream jar carries far more than the pack needs — datagen leftovers, files the
 *         port replaced, anything a future upstream build happens to add. Everything under the pack root that
 *         the effective manifest does not list is deleted, {@code pack.mcmeta} alone excepted, because that
 *         one is written from the running game's pack format and deliberately has no manifest entry. This is
 *         also how {@code alt}'s {@code null} entries take effect: a file that must be absent for this source
 *         is simply not in the effective set.</li>
 *     <li><b>Verify.</b> Every remaining entry is hashed and compared. Nothing is deleted in this step —
 *         a mismatch leaves the staging tree intact so the failure can be looked at.</li>
 * </ol>
 *
 * <p>The whole thing runs on the staging directory, before it is promoted to {@code pack/}, so a failure
 * never touches an install that already works.</p>
 *
 * <p>The hashing is spread over {@link ParallelWork}'s bounded pool, because it is 8500 independent digests
 * of about 82 MB in total and nothing else in the install depends on the order they happen in. Each result
 * goes into a slot of its own and the missing and mismatched lists are assembled from those slots in
 * manifest order afterwards, so the verdict and the paths a failure names do not depend on the scheduling.
 * The prune before it stays sequential: it is a single tree walk, and it is what decides which files there
 * are to hash.</p>
 */
public final class PackVerifier
{
    /**
     * Verdict for a file that matched the manifest.
     */
    private static final byte VERIFIED = 0;

    /**
     * Verdict for a manifest entry with no file on disk.
     */
    private static final byte MISSING = 1;

    /**
     * Verdict for a file whose hash or size is not the expected one.
     */
    private static final byte MISMATCHED = 2;

    /**
     * Name given to the worker threads.
     */
    private static final String THREAD_NAME = "MineColonies asset verify";

    /**
     * How many files between progress reports.
     */
    private static final int PROGRESS_STRIDE = 512;

    /**
     * Private constructor to hide the public one.
     */
    private PackVerifier()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Prunes and verifies a staged pack.
     *
     * @param packRoot  the staged pack root: {@code pack.mcmeta} plus {@code assets/minecolonies/**}.
     * @param expected  the effective manifest for the source the assets came from.
     * @param progress  called with the number of files hashed so far; may be null.
     * @param cancelled polled while hashing; may be null.
     * @return what was deleted, what was missing and what did not match.
     * @throws AssetInstallException if the tree cannot be walked or a file cannot be hashed.
     */
    public static Result verify(final Path packRoot, final Map<String, AssetManifest.FileEntry> expected,
        final ProgressSink progress, final CancelSignal cancelled) throws AssetInstallException
    {
        final Prune pruned = prune(packRoot, expected, cancelled);

        // Snapshotted into arrays so the workers never touch the caller's map, and so the verdicts can be
        // read back in manifest order however they were scheduled.
        final String[] paths = expected.keySet().toArray(new String[0]);
        final AssetManifest.FileEntry[] entries = expected.values().toArray(new AssetManifest.FileEntry[0]);
        final byte[] verdicts = new byte[paths.length];

        ParallelWork.run(THREAD_NAME, paths.length, Hashes.BUFFER_SIZE,
            (index, scratch) -> verdicts[index] = check(packRoot, paths[index], entries[index], scratch),
            done -> report(progress, done), PROGRESS_STRIDE, cancelled);

        final List<String> missing = new ArrayList<>();
        final List<String> mismatched = new ArrayList<>();
        int verified = 0;
        for (int index = 0; index < verdicts.length; index++)
        {
            switch (verdicts[index])
            {
                case MISSING -> missing.add(paths[index]);
                case MISMATCHED -> mismatched.add(paths[index]);
                default -> verified++;
            }
        }

        report(progress, expected.size());
        return new Result(verified, pruned.deleted(), missing, mismatched, pruned.bytes());
    }

    /**
     * Checks one expected file against its manifest entry.
     *
     * @param packRoot the staged pack root.
     * @param path     the manifest key.
     * @param entry    the expected hash and size.
     * @param scratch  the calling worker's read buffer.
     * @return {@link #VERIFIED}, {@link #MISSING} or {@link #MISMATCHED}.
     * @throws AssetInstallException if the file is there but cannot be read.
     */
    private static byte check(final Path packRoot, final String path, final AssetManifest.FileEntry entry, final byte[] scratch)
        throws AssetInstallException
    {
        final Path file = packRoot.resolve(path);
        if (!Files.isRegularFile(file))
        {
            return MISSING;
        }
        try
        {
            final long size = Files.size(file);
            final String actual = Hashes.sha256(file, scratch);
            if (!actual.equals(entry.sha256()) || (entry.size() >= 0 && size != entry.size()))
            {
                return MISMATCHED;
            }
            return VERIFIED;
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not hash " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reports progress if anybody is listening.
     *
     * @param progress the sink; may be null.
     * @param done     how many files have been checked.
     */
    private static void report(final ProgressSink progress, final int done)
    {
        if (progress != null)
        {
            progress.accept(done);
        }
    }

    /**
     * Deletes everything the manifest does not list, then the directories that left empty.
     *
     * <p>The walk already has every file's size in hand, so the tree's total size is added up here rather
     * than in a second walk after verification.</p>
     *
     * @param packRoot  the staged pack root.
     * @param expected  the effective manifest.
     * @param cancelled polled while walking; may be null.
     * @return the pack-relative paths that were deleted, and what the tree weighs afterwards.
     * @throws AssetInstallException if the tree cannot be walked.
     */
    private static Prune prune(final Path packRoot, final Map<String, AssetManifest.FileEntry> expected, final CancelSignal cancelled)
        throws AssetInstallException
    {
        final List<String> deleted = new ArrayList<>();
        final Map<String, Found> present = new LinkedHashMap<>();
        final AtomicLong bytes = new AtomicLong();

        try
        {
            Files.walkFileTree(packRoot, new SimpleFileVisitor<>()
            {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
                {
                    present.put(relative(packRoot, file), new Found(file, attributes.size()));
                    bytes.addAndGet(attributes.size());
                    return FileVisitResult.CONTINUE;
                }
            });

            for (final Map.Entry<String, Found> entry : present.entrySet())
            {
                if (cancelled != null && cancelled.isCancelled())
                {
                    throw new InstallCancelledException();
                }
                if (PackMetaWriter.FILE_NAME.equals(entry.getKey()) || expected.containsKey(entry.getKey()))
                {
                    continue;
                }
                Files.delete(entry.getValue().file());
                bytes.addAndGet(-entry.getValue().size());
                deleted.add(entry.getKey());
            }

            removeEmptyDirectories(packRoot);
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not tidy the staged pack: " + e.getMessage(), e);
        }

        return new Prune(deleted, bytes.get());
    }

    /**
     * A file the prune's walk found, and what it weighs.
     *
     * @param file the file.
     * @param size its size in bytes.
     */
    private record Found(Path file, long size)
    {
    }

    /**
     * What the prune left behind.
     *
     * @param deleted the pack-relative paths it removed.
     * @param bytes   the total size of every file still in the tree.
     */
    private record Prune(List<String> deleted, long bytes)
    {
    }

    /**
     * Removes directories the prune emptied, deepest first, leaving the root itself alone.
     *
     * @param packRoot the staged pack root.
     * @throws IOException if the tree cannot be walked.
     */
    private static void removeEmptyDirectories(final Path packRoot) throws IOException
    {
        final List<Path> directories;
        try (Stream<Path> walk = Files.walk(packRoot))
        {
            directories = walk.filter(Files::isDirectory)
                .filter(path -> !path.equals(packRoot))
                .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                .toList();
        }
        for (final Path directory : directories)
        {
            try (Stream<Path> children = Files.list(directory))
            {
                if (children.findAny().isEmpty())
                {
                    Files.delete(directory);
                }
            }
        }
    }

    /**
     * The pack-relative, {@code /}-separated form of a path inside the pack.
     *
     * @param packRoot the pack root.
     * @param file     the file.
     * @return its manifest key.
     */
    private static String relative(final Path packRoot, final Path file)
    {
        return packRoot.relativize(file).toString().replace(java.io.File.separatorChar, '/');
    }

    /**
     * What a verification run found.
     *
     * @param verified   how many files matched the manifest.
     * @param deleted    pack-relative paths the prune removed.
     * @param missing    manifest entries with no file on disk.
     * @param mismatched manifest entries whose file has the wrong hash or size.
     * @param bytes      what the pruned tree weighs, {@code pack.mcmeta} included. Counted from the prune's
     *                   own walk, which already had every size, instead of walking the tree again for it.
     */
    public record Result(int verified, List<String> deleted, List<String> missing, List<String> mismatched, long bytes)
    {
        /**
         * Whether the pack matches the manifest exactly.
         *
         * @return true if nothing is missing and nothing mismatched.
         */
        public boolean ok()
        {
            return this.missing.isEmpty() && this.mismatched.isEmpty();
        }

        /**
         * A short, player-showable summary of the failure.
         *
         * @param limit how many paths to name before summarising the rest.
         * @return the description, or an empty string when the run succeeded.
         */
        public String describeFailure(final int limit)
        {
            if (ok())
            {
                return "";
            }
            final StringBuilder out = new StringBuilder();
            out.append(this.missing.size()).append(" file(s) missing and ")
                .append(this.mismatched.size()).append(" file(s) with the wrong contents");
            final List<String> named = new ArrayList<>(this.missing);
            named.addAll(this.mismatched);
            out.append(": ");
            for (int i = 0; i < Math.min(limit, named.size()); i++)
            {
                out.append(i > 0 ? ", " : "").append(named.get(i));
            }
            if (named.size() > limit)
            {
                out.append(", and ").append(named.size() - limit).append(" more");
            }
            return out.toString();
        }
    }

    /**
     * How many files have been hashed so far.
     */
    @FunctionalInterface
    public interface ProgressSink
    {
        /**
         * Reports progress.
         *
         * @param done the number of files checked.
         */
        void accept(int done);
    }
}
