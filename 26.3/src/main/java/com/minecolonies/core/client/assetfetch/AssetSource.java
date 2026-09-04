package com.minecolonies.core.client.assetfetch;

/**
 * One entry of the download source chain.
 *
 * <p>Every entry yields the same thing — an archive holding an upstream MineColonies build's client assets
 * — so there is exactly one pipeline downstream of the fetch. What differs is where the bytes come from,
 * how they are laid out, and how much is known about them in advance.</p>
 *
 * <p>Two facts decide how far an entry is trusted before it is unpacked:</p>
 * <ul>
 *     <li>{@code expectedSha256} pins the whole archive. An immutable Maven artifact or a file on
 *         CurseForge's file store can be pinned this way, and an archive that hashes to anything else is
 *         refused before a single entry is unpacked. This is the one integrity check the feature makes:
 *         downstream of the unpack nothing looks at any file's contents.</li>
 *     <li>{@code unpinned} says the entry pins nothing and is not meant to — GitHub generates its source
 *         archives per request and promises nothing about their bytes — so it is taken for what it carries.
 *         Such an entry is allowed to fail late and hand over to the next source, because failing to unpack
 *         or to be patchable is that source's failure and not the install's.</li>
 * </ul>
 *
 * <p>An entry that is neither pinned nor {@code unpinned} — the owner's slot, a jar the player picked — has
 * to hash to an archive the manifest names; a player's jar that does not is still accepted, unchecked and
 * with the player told so, because a build newer than any this manifest lists is exactly the jar somebody
 * would fetch by hand.</p>
 *
 * @param id              stable identifier, recorded in {@code state.json} and shown to the player:
 *                        {@code maven-1374}, {@code cdn-1376}, {@code github-src-1368}, {@code owner-http},
 *                        {@code local-jar}.
 * @param url             the URL to fetch, or the local file path for {@link Kind#LOCAL_FILE}.
 * @param kind            how to obtain the bytes.
 * @param expectedSha256  the pinned whole-archive hash, or null when the entry pins none.
 * @param expectedSize    the pinned size in bytes, or -1 when unknown.
 * @param layout          where {@code assets/minecolonies/**} sits inside the archive.
 * @param unpinned        whether this entry pins nothing on purpose and is accepted for what it carries.
 * @param description     a short, player-showable description of the source.
 */
public record AssetSource(String id, String url, Kind kind, String expectedSha256, long expectedSize,
    ArchiveLayout layout, boolean unpinned, String description)
{
    /**
     * How the bytes are obtained.
     */
    public enum Kind
    {
        /**
         * An HTTP or HTTPS download. Plain {@code http://} is deliberately allowed: where there is integrity
         * to be had it comes from the whole-archive hash, never from the transport.
         */
        HTTP,

        /**
         * A file the player already has on disk, chosen through the UI.
         */
        LOCAL_FILE
    }
}
