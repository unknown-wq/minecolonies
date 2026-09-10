package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pulls {@code assets/minecolonies/**} out of an upstream archive and nothing else.
 *
 * <p>The archive is a whole mod — or, for a source release, a whole project: upstream's classes, its
 * {@code data/}, its blueprints, its own metadata. None of that is wanted and none of it is written to disk —
 * only the client asset subtree the fetched pack is made of. Where that subtree sits inside the archive is
 * the one thing that differs between a built jar and a source release, and that difference lives entirely
 * in {@link ArchiveLayout}.</p>
 *
 * <h2>Why it is done in two passes</h2>
 * <p>The central directory is read first and turned into a plain list of what to write where. That pass is
 * cheap, and it buys four things an entry-at-a-time loop could not have:</p>
 * <ul>
 *     <li><b>Every zip-slip check happens before a single byte is written.</b> The destination of each entry
 *         is resolved and normalised, and one whose normalised path leaves the target directory aborts the
 *         run with an empty staging tree rather than half a one. A downloaded archive is untrusted input
 *         even when its hash matched, because the hash only proves it is the file we expected, not that the
 *         file is well-behaved.</li>
 *     <li><b>A pack file is planned once, however many entries offer it.</b> A source archive's two roots
 *         can both carry the same file, and the plan settles which one wins — by the layout's root
 *         precedence, and among entries of equal precedence by the last in archive order, which is what a
 *         sequential loop left behind. Settling it here, keyed by destination, is also what makes the
 *         parallel write below safe: no two workers are ever handed the same file.</li>
 *     <li><b>Each directory is created once.</b> There are about 8500 entries under 140 directories.</li>
 *     <li><b>The writing can be spread over a few threads.</b> Inflating 82 MB is CPU work, and it was
 *         being done on one core while the player watched a progress bar.</li>
 * </ul>
 *
 * <p>The count returned is a count of pack files, not of archive entries: a file offered by two roots is
 * counted once.</p>
 */
public final class JarAssetExtractor
{
    /**
     * Copy buffer size. One of these per worker thread, not one per entry.
     */
    private static final int BUFFER_SIZE = 1 << 16;

    /**
     * Name given to the worker threads.
     */
    private static final String THREAD_NAME = "MineColonies asset unpack";

    /**
     * How many files between progress reports.
     */
    private static final int PROGRESS_STRIDE = 256;

    /**
     * The most an archive's asset entries may add up to once unpacked, by their own headers.
     *
     * <p>The downloads are capped on the wire, but a compressed cap says nothing about what comes out: a
     * few hundred megabytes of deflate can unpack to a thousand times that, and an unpinned source has no
     * hash standing in front of it at all. The real asset tree is about 82 MB, so a gigabyte is far above
     * anything an honest upstream build could reach and far below what would hurt.</p>
     */
    private static final long MAX_UNPACKED_BYTES = 1L << 30;

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
     * Extracts the asset subtree of a built mod jar.
     *
     * @param jar       the downloaded jar.
     * @param assetsDir where {@code assets/minecolonies/**} is written to, i.e. the staged pack's
     *                  {@code assets/minecolonies} directory. Created if absent.
     * @param progress  called with the running file count; may be null.
     * @param cancelled polled while unpacking; may be null.
     * @return how many files were written.
     * @throws AssetInstallException if the jar cannot be read, carries a hostile entry, or holds no assets.
     */
    public static int extract(final Path jar, final Path assetsDir, final ProgressSink progress, final CancelSignal cancelled) throws AssetInstallException
    {
        return extract(jar, assetsDir, ArchiveLayout.MOD_JAR, progress, cancelled);
    }

    /**
     * Extracts the asset subtree of an archive laid out the given way.
     *
     * <p>Everything but the entry-name mapping — the zip-slip refusal, the "this archive has no MineColonies
     * assets in it" refusal, the fact that nothing outside {@code assetsDir} is ever written — is the same
     * for every layout, on purpose: a fallback source must not get a shorter path through this code than
     * the primary one.</p>
     *
     * @param jar       the downloaded archive.
     * @param assetsDir where {@code assets/minecolonies/**} is written to. Created if absent.
     * @param layout    where the assets sit inside this archive.
     * @param progress  called with the running file count; may be null.
     * @param cancelled polled while planning and unpacking; may be null.
     * @return how many pack files were written. A layout whose roots overlap counts a file once, not once
     *         per root that offers it.
     * @throws AssetInstallException if the archive cannot be read, carries a hostile entry, or holds no
     *                               assets.
     */
    public static int extract(final Path jar, final Path assetsDir, final ArchiveLayout layout,
        final ProgressSink progress, final CancelSignal cancelled) throws AssetInstallException
    {
        final Path root = assetsDir.toAbsolutePath().normalize();

        try (ZipFile zip = new ZipFile(jar.toFile()))
        {
            Files.createDirectories(root);

            final List<Planned> planned = plan(zip, root, layout, cancelled);
            if (planned.isEmpty())
            {
                throw new AssetInstallException("The download contains no assets/minecolonies files -- it is not an upstream MineColonies "
                    + (layout == ArchiveLayout.MOD_JAR ? "jar" : "source release"));
            }
            createDirectories(planned);

            ParallelWork.run(THREAD_NAME, planned.size(), BUFFER_SIZE,
                (index, scratch) -> write(zip, planned.get(index), scratch),
                done -> report(progress, done), PROGRESS_STRIDE, cancelled);

            report(progress, planned.size());
            return planned.size();
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("The downloaded jar could not be read: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the central directory and works out what goes where, refusing anything that escapes the target
     * and settling every case of one pack file being offered more than once.
     *
     * <p>The plan is keyed by the normalised destination, so two entries that spell the same file
     * differently — from two roots of a source archive, or by way of a {@code .} segment — meet here rather
     * than on disk. The better root wins; between equals the later entry replaces the earlier one's contents
     * while keeping its place in the order, so the tree on disk does not depend on how the writing is
     * scheduled.</p>
     *
     * @param zip       the open archive.
     * @param root      the normalised destination directory.
     * @param layout    where the assets sit inside this archive.
     * @param cancelled polled per entry; may be null.
     * @return one entry per pack file to write, in archive order of first appearance.
     * @throws AssetInstallException if an entry's destination leaves {@code root}, or the run was cancelled.
     */
    private static List<Planned> plan(final ZipFile zip, final Path root, final ArchiveLayout layout, final CancelSignal cancelled)
        throws AssetInstallException
    {
        final Map<Path, Planned> selected = new LinkedHashMap<>();
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        long declared = 0L;
        while (entries.hasMoreElements())
        {
            if (cancelled != null && cancelled.isCancelled())
            {
                throw new InstallCancelledException();
            }

            final ZipEntry entry = entries.nextElement();
            final String name = entry.getName();
            if (entry.isDirectory())
            {
                continue;
            }

            final String relative = layout.assetPathOf(name);
            if (relative == null)
            {
                continue;
            }

            final Path target = root.resolve(relative).normalize();
            if (!target.startsWith(root))
            {
                throw new AssetInstallException("The downloaded jar contains an entry that escapes the target directory: " + name);
            }

            // The size an entry claims is what write() holds it to, so an entry that claims none is refused
            // here rather than trusted there; and the claims are added up over every entry, replaced ones
            // included, so the sum is an upper bound on what the plan can put on disk.
            if (entry.getSize() < 0)
            {
                throw new AssetInstallException("The downloaded jar does not state the size of " + name);
            }
            declared += entry.getSize();
            if (declared > MAX_UNPACKED_BYTES)
            {
                throw new AssetInstallException("The downloaded jar would unpack to more than " + MAX_UNPACKED_BYTES
                    + " bytes of assets, which is not a MineColonies build");
            }

            // Only a layout whose roots can offer the same file twice has a precedence to consult; for a mod
            // jar every entry is equal, and a repeated one simply replaces the earlier.
            final int precedence = layout.overlaps() ? layout.rootOf(name) : 0;
            final Planned previous = selected.get(target);
            if (previous != null && previous.precedence() < precedence)
            {
                continue;
            }
            selected.put(target, new Planned(entry, target, precedence));
        }
        return new ArrayList<>(selected.values());
    }

    /**
     * Creates every directory the plan needs, once each.
     *
     * @param planned what is about to be written.
     * @throws IOException if a directory cannot be created.
     */
    private static void createDirectories(final List<Planned> planned) throws IOException
    {
        final Set<Path> made = new HashSet<>();
        for (final Planned entry : planned)
        {
            final Path parent = entry.target().getParent();
            if (parent != null && made.add(parent))
            {
                Files.createDirectories(parent);
            }
        }
    }

    /**
     * Writes one entry.
     *
     * @param zip     the open archive; {@link ZipFile} serves streams to several threads at once.
     * @param entry   what to write and where.
     * @param scratch the calling worker's copy buffer.
     * @throws AssetInstallException if the entry cannot be read or the file cannot be written.
     */
    private static void write(final ZipFile zip, final Planned entry, final byte[] scratch) throws AssetInstallException
    {
        // The header's size was counted against the unpacked-size ceiling in the plan; an entry that inflates
        // past what it declared is lying about exactly the number that check relied on, and is stopped
        // where the lie shows.
        final long declared = entry.entry().getSize();
        long copied = 0L;
        try (InputStream in = zip.getInputStream(entry.entry()); OutputStream out = Files.newOutputStream(entry.target()))
        {
            int read;
            while ((read = in.read(scratch)) > 0)
            {
                copied += read;
                if (copied > declared)
                {
                    throw new AssetInstallException("The downloaded jar's entry " + entry.entry().getName()
                        + " is larger than its header says");
                }
                out.write(scratch, 0, read);
            }
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("The downloaded jar could not be read: " + e.getMessage(), e);
        }
    }

    /**
     * Reports progress if anybody is listening.
     *
     * @param progress the sink; may be null.
     * @param done     how many files are written.
     */
    private static void report(final ProgressSink progress, final int done)
    {
        if (progress != null)
        {
            progress.accept(done);
        }
    }

    /**
     * One file to unpack: the archive entry, the checked place it goes, and how good its root is.
     *
     * @param entry      the archive entry.
     * @param target     its destination, already checked to be inside the staging tree.
     * @param precedence the index of the layout root it came from, lower being better; 0 for a layout with
     *                   one root.
     */
    private record Planned(ZipEntry entry, Path target, int precedence)
    {
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
