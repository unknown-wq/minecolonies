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
 */
public final class PackVerifier
{
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
        final List<String> deleted = prune(packRoot, expected, cancelled);

        final List<String> missing = new ArrayList<>();
        final List<String> mismatched = new ArrayList<>();
        int verified = 0;

        for (final Map.Entry<String, AssetManifest.FileEntry> entry : expected.entrySet())
        {
            if (cancelled != null && cancelled.isCancelled())
            {
                throw new InstallCancelledException();
            }

            final Path file = packRoot.resolve(entry.getKey());
            if (!Files.isRegularFile(file))
            {
                missing.add(entry.getKey());
                continue;
            }
            try
            {
                final long size = Files.size(file);
                final String actual = Hashes.sha256(file);
                if (!actual.equals(entry.getValue().sha256())
                    || (entry.getValue().size() >= 0 && size != entry.getValue().size()))
                {
                    mismatched.add(entry.getKey());
                }
                else
                {
                    verified++;
                }
            }
            catch (final IOException e)
            {
                throw new AssetInstallException("Could not hash " + entry.getKey() + ": " + e.getMessage(), e);
            }

            if (progress != null && ((verified + missing.size() + mismatched.size()) & 0x1FF) == 0)
            {
                progress.accept(verified + missing.size() + mismatched.size());
            }
        }

        if (progress != null)
        {
            progress.accept(expected.size());
        }
        return new Result(verified, deleted, missing, mismatched);
    }

    /**
     * Deletes everything the manifest does not list, then the directories that left empty.
     *
     * @param packRoot  the staged pack root.
     * @param expected  the effective manifest.
     * @param cancelled polled while walking; may be null.
     * @return the pack-relative paths that were deleted.
     * @throws AssetInstallException if the tree cannot be walked.
     */
    private static List<String> prune(final Path packRoot, final Map<String, AssetManifest.FileEntry> expected, final CancelSignal cancelled)
        throws AssetInstallException
    {
        final List<String> deleted = new ArrayList<>();
        final Map<String, Path> present = new LinkedHashMap<>();

        try
        {
            Files.walkFileTree(packRoot, new SimpleFileVisitor<>()
            {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
                {
                    present.put(relative(packRoot, file), file);
                    return FileVisitResult.CONTINUE;
                }
            });

            for (final Map.Entry<String, Path> entry : present.entrySet())
            {
                if (cancelled != null && cancelled.isCancelled())
                {
                    throw new InstallCancelledException();
                }
                if (PackMetaWriter.FILE_NAME.equals(entry.getKey()) || expected.containsKey(entry.getKey()))
                {
                    continue;
                }
                Files.delete(entry.getValue());
                deleted.add(entry.getKey());
            }

            removeEmptyDirectories(packRoot);
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not tidy the staged pack: " + e.getMessage(), e);
        }

        return deleted;
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
     */
    public record Result(int verified, List<String> deleted, List<String> missing, List<String> mismatched)
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
