package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Small directory-tree helpers the installer needs and the JDK does not offer.
 */
final class FileTrees
{
    /**
     * Private constructor to hide the public one.
     */
    private FileTrees()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Deletes a directory tree, or does nothing if it is not there.
     *
     * @param root the tree to remove.
     * @throws IOException if something cannot be deleted.
     */
    static void deleteRecursively(final Path root) throws IOException
    {
        if (!Files.exists(root))
        {
            return;
        }
        try (Stream<Path> walk = Files.walk(root))
        {
            for (final Path path : walk.sorted(Comparator.reverseOrder()).toList())
            {
                Files.deleteIfExists(path);
            }
        }
    }

    /**
     * Deletes a directory tree without complaining if it cannot.
     *
     * <p>Used on the cleanup path, where a failure to tidy up must not replace the real error.</p>
     *
     * @param root the tree to remove.
     */
    static void deleteQuietly(final Path root)
    {
        try
        {
            deleteRecursively(root);
        }
        catch (final IOException e)
        {
            // Nothing useful to do: the caller is already reporting whatever went wrong first.
        }
    }

    /**
     * Total size of every regular file in a tree.
     *
     * @param root the tree to measure.
     * @return the size in bytes, or 0 if the tree is absent or unreadable.
     */
    static long size(final Path root)
    {
        if (!Files.exists(root))
        {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(root))
        {
            return walk.filter(Files::isRegularFile).mapToLong(path ->
            {
                try
                {
                    return Files.size(path);
                }
                catch (final IOException e)
                {
                    return 0L;
                }
            }).sum();
        }
        catch (final IOException e)
        {
            return 0L;
        }
    }

    /**
     * Counts the regular files in a tree.
     *
     * @param root the tree to count.
     * @return the number of files, or 0 if the tree is absent or unreadable.
     */
    static long count(final Path root)
    {
        if (!Files.exists(root))
        {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(root))
        {
            return walk.filter(Files::isRegularFile).count();
        }
        catch (final IOException e)
        {
            return 0L;
        }
    }
}
