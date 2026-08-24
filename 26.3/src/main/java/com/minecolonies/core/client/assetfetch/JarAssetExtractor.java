package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pulls {@code assets/minecolonies/**} out of an upstream mod jar and nothing else.
 *
 * <p>The jar is a whole mod: upstream's classes, its {@code data/}, its blueprints, its own metadata. None
 * of that is wanted and none of it is written to disk — only the client asset subtree the fetched pack is
 * made of.</p>
 *
 * <p>Every entry is checked against zip-slip before anything is created: the destination path is resolved
 * and normalised, and an entry whose normalised path leaves the target directory is refused. A downloaded
 * archive is untrusted input even when its hash matched, because the hash only proves it is the file we
 * expected, not that the file is well-behaved.</p>
 */
public final class JarAssetExtractor
{
    /**
     * The only prefix inside the jar that is extracted.
     */
    private static final String ASSET_PREFIX = "assets/minecolonies/";

    /**
     * Copy buffer size.
     */
    private static final int BUFFER_SIZE = 1 << 16;

    /**
     * Private constructor to hide the public one.
     */
    private JarAssetExtractor()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Extracts the asset subtree.
     *
     * @param jar       the downloaded jar.
     * @param assetsDir where {@code assets/minecolonies/**} is written to, i.e. the staged pack's
     *                  {@code assets/minecolonies} directory. Created if absent.
     * @param progress  called with the running entry count; may be null.
     * @param cancelled polled per entry; may be null.
     * @return how many files were written.
     * @throws AssetInstallException if the jar cannot be read, carries a hostile entry, or holds no assets.
     */
    public static int extract(final Path jar, final Path assetsDir, final ProgressSink progress, final CancelSignal cancelled) throws AssetInstallException
    {
        final Path root = assetsDir.toAbsolutePath().normalize();
        int written = 0;

        try (ZipFile zip = new ZipFile(jar.toFile()))
        {
            Files.createDirectories(root);
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
            {
                if (cancelled != null && cancelled.isCancelled())
                {
                    throw new InstallCancelledException();
                }

                final ZipEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(ASSET_PREFIX))
                {
                    continue;
                }

                final String relative = name.substring(ASSET_PREFIX.length());
                if (relative.isEmpty())
                {
                    continue;
                }

                final Path target = root.resolve(relative).normalize();
                if (!target.startsWith(root))
                {
                    throw new AssetInstallException("The downloaded jar contains an entry that escapes the target directory: " + name);
                }

                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry); OutputStream out = Files.newOutputStream(target))
                {
                    final byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) > 0)
                    {
                        out.write(buffer, 0, read);
                    }
                }
                written++;
                if (progress != null && (written & 0xFF) == 0)
                {
                    progress.accept(written);
                }
            }
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("The downloaded jar could not be read: " + e.getMessage(), e);
        }

        if (written == 0)
        {
            throw new AssetInstallException("The downloaded jar contains no " + ASSET_PREFIX + " files -- it is not an upstream MineColonies jar");
        }

        if (progress != null)
        {
            progress.accept(written);
        }
        return written;
    }

    /**
     * How many entries have been extracted so far.
     */
    @FunctionalInterface
    public interface ProgressSink
    {
        /**
         * Reports progress.
         *
         * @param done the number of files written.
         */
        void accept(int done);
    }
}
