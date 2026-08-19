package com.minecolonies.core.client.assetfetch;

/**
 * One entry of the download source chain.
 *
 * <p>Every entry yields the same thing — an upstream MineColonies build jar — so there is exactly one
 * pipeline downstream of the fetch. What differs is where the bytes come from and how much is known about
 * them in advance.</p>
 *
 * @param id             stable identifier, recorded in {@code state.json} and shown to the player:
 *                       {@code maven-1374}, {@code maven-1368}, {@code owner-http}, {@code local-jar}.
 * @param url            the URL to fetch, or the local file path for {@link Kind#LOCAL_FILE}.
 * @param kind           how to obtain the bytes.
 * @param expectedSha256 the pinned whole-jar hash, or null when the entry accepts any known hash.
 * @param expectedSize   the pinned size in bytes, or -1 when unknown.
 * @param description    a short, player-showable description of the source.
 */
public record AssetSource(String id, String url, Kind kind, String expectedSha256, long expectedSize, String description)
{
    /**
     * How the bytes are obtained.
     */
    public enum Kind
    {
        /**
         * An HTTP or HTTPS download. Plain {@code http://} is deliberately allowed: integrity comes from the
         * whole-jar hash and the per-file manifest, never from the transport.
         */
        HTTP,

        /**
         * A file the player already has on disk, chosen through the UI.
         */
        LOCAL_FILE
    }
}
