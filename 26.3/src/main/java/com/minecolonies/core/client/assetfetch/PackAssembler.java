package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Makes a staged tree hold exactly the paths the manifest lists — no more, and as many of them as can be
 * had.
 *
 * <p>The manifest is a list of paths, so every file has one of three fates and the decision is made on the
 * path alone. Contents are never looked at.</p>
 * <ol>
 *     <li><b>Listed and supplied.</b> The archive put a file there. It stays, whatever is in it.</li>
 *     <li><b>Supplied and not listed.</b> Deleted. An upstream jar carries far more than the pack needs —
 *         datagen leftovers, files the port replaced, whatever a future build happens to add — and none of
 *         it belongs in the pack. {@code pack.mcmeta} alone is exempt: it is written from the running game's
 *         pack format and deliberately has no manifest entry.</li>
 *     <li><b>Listed and not supplied.</b> Copied out of the pack the player already has, if there is one
 *         with that file in it; otherwise counted as absent and simply not there. This is what makes an
 *         install a merge rather than a replacement: a source that cannot carry part of the file set — a
 *         GitHub source archive has no translations in it at all — no longer takes those files off a player
 *         who has them.</li>
 * </ol>
 *
 * <p><b>What this does not do is check anything.</b> There is no hashing here and no comparison: the bytes
 * that came out of the archive are the bytes that get installed. Where a source's integrity is guaranteed at
 * all it is guaranteed by the whole-archive pin checked before the unpack (see {@link AssetManifest}), and
 * for a source with no pin nothing vouches for the contents at any point. What is still guaranteed for every
 * source, pinned or not, is composition: nothing outside the manifest's list reaches the pack, and nothing
 * outside the staging directory is written until the swap.</p>
 *
 * <p>The whole thing runs on the staging directory, before it is promoted to {@code pack/}, and the carried
 * files are <em>copied</em> out of the installed pack rather than moved, so a failure at any point here
 * leaves an install that already works exactly as it was.</p>
 */
public final class PackAssembler
{
    /**
     * How many files between progress reports.
     */
    private static final int PROGRESS_STRIDE = 512;

    /**
     * Private constructor to hide the public one.
     */
    private PackAssembler()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Prunes what the manifest does not list and fills in what the archive did not supply.
     *
     * @param packRoot  the staged pack root: {@code pack.mcmeta} plus {@code assets/minecolonies/**}.
     * @param expected  the manifest's file set, pack-relative.
     * @param installed the pack currently installed, to take missing files from; it need not exist, and on a
     *                  first install it does not.
     * @param progress  called with the number of manifest entries settled so far; may be null.
     * @param cancelled polled while walking; may be null.
     * @return what was written, carried, left absent and deleted.
     * @throws AssetInstallException if the tree cannot be walked or a carried file cannot be copied.
     */
    public static Result assemble(final Path packRoot, final Set<String> expected, final Path installed,
        final ProgressSink progress, final CancelSignal cancelled) throws AssetInstallException
    {
        final Prune pruned = prune(packRoot, expected, cancelled);
        long bytes = pruned.bytes();

        final List<String> carried = new ArrayList<>();
        final List<String> absent = new ArrayList<>();
        int written = 0;
        int done = 0;

        try
        {
            for (final String path : expected)
            {
                if (cancelled != null && cancelled.isCancelled())
                {
                    throw new InstallCancelledException();
                }
                if (Files.isRegularFile(packRoot.resolve(path)))
                {
                    written++;
                }
                else
                {
                    final long copied = carry(installed, packRoot, path);
                    if (copied < 0)
                    {
                        absent.add(path);
                    }
                    else
                    {
                        carried.add(path);
                        bytes += copied;
                    }
                }
                if (++done % PROGRESS_STRIDE == 0)
                {
                    report(progress, done);
                }
            }
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not take a missing file from the installed pack: " + e.getMessage(), e);
        }

        report(progress, expected.size());
        return new Result(written, carried, absent, pruned.deleted(), bytes);
    }

    /**
     * Copies one manifest file out of the installed pack into the staged one.
     *
     * <p>Only a plain file is taken, and it is taken by path: the installed pack was put together by this
     * same code against a manifest of its own, so a file sitting at a listed path there is the file that
     * belongs at that path here.</p>
     *
     * @param installed the installed pack root; may be null or not exist.
     * @param packRoot  the staged pack root.
     * @param path      the pack-relative path.
     * @return the number of bytes copied, or -1 when the installed pack has nothing to give.
     * @throws IOException if the copy fails.
     */
    private static long carry(final Path installed, final Path packRoot, final String path) throws IOException
    {
        if (installed == null)
        {
            return -1L;
        }
        final Path from = installed.resolve(path);
        if (!Files.isRegularFile(from))
        {
            return -1L;
        }
        final Path to = packRoot.resolve(path);
        Files.createDirectories(to.getParent());
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        return Files.size(to);
    }

    /**
     * Reports progress if anybody is listening.
     *
     * @param progress the sink; may be null.
     * @param done     how many manifest entries have been settled.
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
     * than in a second walk afterwards.</p>
     *
     * @param packRoot  the staged pack root.
     * @param expected  the manifest's file set.
     * @param cancelled polled while walking; may be null.
     * @return the pack-relative paths that were deleted, and what the tree weighs afterwards.
     * @throws AssetInstallException if the tree cannot be walked.
     */
    private static Prune prune(final Path packRoot, final Set<String> expected, final CancelSignal cancelled)
        throws AssetInstallException
    {
        final List<String> deleted = new ArrayList<>();
        final Map<String, Found> present = new LinkedHashMap<>();
        long bytes = 0L;

        try
        {
            Files.walkFileTree(packRoot, new SimpleFileVisitor<>()
            {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
                {
                    present.put(relative(packRoot, file), new Found(file, attributes.size()));
                    return FileVisitResult.CONTINUE;
                }
            });

            for (final Map.Entry<String, Found> entry : present.entrySet())
            {
                if (cancelled != null && cancelled.isCancelled())
                {
                    throw new InstallCancelledException();
                }
                if (PackMetaWriter.FILE_NAME.equals(entry.getKey()) || expected.contains(entry.getKey()))
                {
                    bytes += entry.getValue().size();
                    continue;
                }
                Files.delete(entry.getValue().file());
                deleted.add(entry.getKey());
            }

            removeEmptyDirectories(packRoot);
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not tidy the staged pack: " + e.getMessage(), e);
        }

        return new Prune(deleted, bytes);
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
     * What an assembly produced.
     *
     * @param written how many manifest files the archive supplied and the pack now carries from it.
     * @param carried manifest files the archive did not supply and that were taken from the installed pack.
     * @param absent  manifest files nothing could supply. On a first install from a partial source this is
     *                where they end up; the pack works without them, minus whatever they were.
     * @param deleted pack-relative paths the prune removed for not being in the manifest.
     * @param bytes   what the tree weighs, {@code pack.mcmeta} included.
     */
    public record Result(int written, List<String> carried, List<String> absent, List<String> deleted, long bytes)
    {
        /**
         * How many of the manifest's files the pack ended up with.
         *
         * @return written plus carried.
         */
        public int present()
        {
            return this.written + this.carried.size();
        }
    }

    /**
     * How many manifest entries have been settled so far.
     */
    @FunctionalInterface
    public interface ProgressSink
    {
        /**
         * Reports progress.
         *
         * @param done the number of entries settled.
         */
        void accept(int done);
    }
}
