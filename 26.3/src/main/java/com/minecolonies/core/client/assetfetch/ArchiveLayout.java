package com.minecolonies.core.client.assetfetch;

/**
 * Where {@code assets/minecolonies/**} sits inside a downloadable archive.
 *
 * <p>Every source in the chain yields one archive that carries the upstream client assets, but not every
 * official distribution point serves the same <em>kind</em> of archive. LDTTeam's Maven serves the built mod
 * jar, in which the assets sit at the root under {@code assets/minecolonies/}. LDTTeam's GitHub releases
 * attach no jar at all — only the two archives GitHub generates from the tag — and there the same files sit
 * in the project's source layout, split over two roots, under a top-level directory whose name changes with
 * every tag.</p>
 *
 * <p>This enum is the whole of that difference: it turns an archive entry name into the path the file has
 * inside the pack, or into {@code null} for the vast majority of entries — classes, {@code data/}, build
 * scripts — that are not wanted and are never written to disk.</p>
 */
public enum ArchiveLayout
{
    /**
     * A built mod jar: the assets are at the archive root and there is exactly one of each.
     */
    MOD_JAR("modJar", false, "assets/minecolonies/"),

    /**
     * A source release archive as GitHub generates it from a tag.
     *
     * <p>One top-level directory named after the tag, which is stripped, and then two roots that both
     * contribute to the built jar's asset tree: the checked-in resources and upstream's committed datagen
     * output. The roots are listed in precedence order and, in the tag this port pins, overlap in a single
     * byte-identical file; {@link #overlaps()} is what makes that overlap resolve the same way every run
     * instead of by archive order.</p>
     */
    SOURCE_ARCHIVE("sourceArchive", true,
        "src/main/resources/assets/minecolonies/",
        "src/datagen/generated/minecolonies/assets/minecolonies/");

    /**
     * The name this layout is written under in the install manifest.
     */
    private final String id;

    /**
     * Whether the archive wraps everything in one top-level directory that has to be stripped.
     */
    private final boolean topLevelDirectory;

    /**
     * The roots that contribute assets, in precedence order: the earlier one wins.
     */
    private final String[] roots;

    /**
     * Creates a layout.
     *
     * @param id                the manifest spelling.
     * @param topLevelDirectory whether one leading path segment has to be stripped.
     * @param roots             the contributing roots, best first.
     */
    ArchiveLayout(final String id, final boolean topLevelDirectory, final String... roots)
    {
        this.id = id;
        this.topLevelDirectory = topLevelDirectory;
        this.roots = roots;
    }

    /**
     * The layout of the given name.
     *
     * @param id the manifest spelling, or null.
     * @return the layout, or {@link #MOD_JAR} when the name is absent or unknown.
     */
    public static ArchiveLayout byId(final String id)
    {
        for (final ArchiveLayout layout : values())
        {
            if (layout.id.equals(id))
            {
                return layout;
            }
        }
        return MOD_JAR;
    }

    /**
     * The manifest spelling of this layout.
     *
     * @return the id.
     */
    public String id()
    {
        return this.id;
    }

    /**
     * Whether two roots of this layout can offer the same pack file, so the extractor has to settle which
     * one wins rather than letting archive order decide.
     *
     * @return true when more than one root contributes.
     */
    public boolean overlaps()
    {
        return this.roots.length > 1;
    }

    /**
     * Where an archive entry belongs inside the pack.
     *
     * @param entryName the entry name as the archive spells it.
     * @return the path relative to {@code assets/minecolonies/}, or null when the entry is not an asset.
     */
    public String assetPathOf(final String entryName)
    {
        final String name = strip(entryName);
        if (name == null)
        {
            return null;
        }
        for (final String root : this.roots)
        {
            if (name.startsWith(root))
            {
                final String relative = name.substring(root.length());
                return relative.isEmpty() ? null : relative;
            }
        }
        return null;
    }

    /**
     * Which root an entry came from, as a precedence number.
     *
     * <p>Only consulted when {@link #overlaps()} says it can matter.</p>
     *
     * @param entryName the entry name as the archive spells it.
     * @return the root's index, lower being better, or {@link Integer#MAX_VALUE} when the entry is not an
     *         asset.
     */
    public int rootOf(final String entryName)
    {
        final String name = strip(entryName);
        if (name == null)
        {
            return Integer.MAX_VALUE;
        }
        for (int i = 0; i < this.roots.length; i++)
        {
            if (name.startsWith(this.roots[i]))
            {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Removes the wrapping top-level directory, when this layout has one.
     *
     * @param entryName the entry name.
     * @return the name relative to the archive's content root, or null when there is no such directory.
     */
    private String strip(final String entryName)
    {
        if (!this.topLevelDirectory)
        {
            return entryName;
        }
        final int slash = entryName.indexOf('/');
        return slash < 0 ? null : entryName.substring(slash + 1);
    }
}
