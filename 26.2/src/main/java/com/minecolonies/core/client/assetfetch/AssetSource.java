package com.minecolonies.core.client.assetfetch;

/**
 * One entry of the download source chain.
 *
 * <p>Every entry yields the same thing — an archive holding an upstream MineColonies build's client assets
 * — so there is exactly one pipeline downstream of the fetch. What differs is where the bytes come from,
 * how they are laid out, and how much is known about them in advance.</p>
 *
 * <p>Two of those facts decide how the entry is trusted, and exactly one of them is always set:</p>
 * <ul>
 *     <li>{@code expectedSha256} pins the whole archive. An immutable Maven artifact can be pinned this way,
 *         and an archive that hashes to anything else is refused before a single entry is unpacked.</li>
 *     <li>{@code filesOfSourceId} names the manifest file set the archive has to satisfy instead. Nothing
 *         about the archive itself is pinned — GitHub's source archives are generated per request and their
 *         compression is not promised to be stable — so the trust sits one level down, on every file that
 *         comes out of it. Not one byte reaches the pack without matching its manifest hash either way; the
 *         two forms differ in when the check happens, not in how strict it is.</li>
 * </ul>
 *
 * @param id              stable identifier, recorded in {@code state.json} and shown to the player:
 *                        {@code maven-1374}, {@code maven-1368}, {@code github-src-1374}, {@code owner-http},
 *                        {@code local-jar}.
 * @param url             the URL to fetch, or the local file path for {@link Kind#LOCAL_FILE}.
 * @param kind            how to obtain the bytes.
 * @param expectedSha256  the pinned whole-archive hash, or null when the entry pins none.
 * @param expectedSize    the pinned size in bytes, or -1 when unknown.
 * @param layout          where {@code assets/minecolonies/**} sits inside the archive.
 * @param filesOfSourceId the manifest source id whose file set this archive must satisfy, or null when the
 *                        archive is identified by its own hash instead.
 * @param absencesOfSourceId the manifest source id whose {@code mayBeAbsent} declaration applies, or null to
 *                        use this entry's own. They differ only for a source found at install time, which
 *                        has no manifest entry of its own and borrows the one written for the kind of
 *                        archive it is.
 * @param description     a short, player-showable description of the source.
 */
public record AssetSource(String id, String url, Kind kind, String expectedSha256, long expectedSize,
    ArchiveLayout layout, String filesOfSourceId, String absencesOfSourceId, String description)
{
    /**
     * Which manifest entry's declared absences this source is held to.
     *
     * @return {@link #absencesOfSourceId} when it is set, otherwise this entry's own id.
     */
    public String absencePolicySourceId()
    {
        return this.absencesOfSourceId == null ? this.id : this.absencesOfSourceId;
    }

    /**
     * Whether this entry is trusted by the files that come out of it rather than by a pinned archive hash.
     *
     * @return true when the manifest file set is the only thing standing between the archive and the pack.
     */
    public boolean verifiedByContents()
    {
        return this.expectedSha256 == null && this.filesOfSourceId != null;
    }

    /**
     * How the bytes are obtained.
     */
    public enum Kind
    {
        /**
         * An HTTP or HTTPS download. Plain {@code http://} is deliberately allowed: integrity comes from the
         * whole-archive hash and the per-file manifest, never from the transport.
         */
        HTTP,

        /**
         * A file the player already has on disk, chosen through the UI.
         */
        LOCAL_FILE
    }
}
