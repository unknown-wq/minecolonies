package com.minecolonies.core.client.assetfetch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The download source chain (task C2), in the order the installer tries it.
 *
 * <ol>
 *     <li><b>{@code maven-1374}</b> — LDTTeam's own Maven, the snapshot build this port was made from.</li>
 *     <li><b>{@code maven-1368}</b> — the same Maven, the previous release build. A release directory entry,
 *         which is the most durable thing that server offers.</li>
 *     <li><b>{@code owner-http}</b> — a single configurable slot, shipped empty and therefore skipped in
 *         silence. It exists so that if LDTTeam's Maven ever dies the owner can stand up their own host and
 *         enable it by changing {@link #OWNER_HOST_URL} and rebuilding, or — without any rebuild at all — by
 *         putting a URL in {@code state.json}'s {@code customSourceUrl}, which wins over the constant.</li>
 *     <li><b>{@code local-jar}</b> — a jar the player downloaded themselves, e.g. from CurseForge. Not part
 *         of the automatic chain; the UI hands it in explicitly.</li>
 * </ol>
 *
 * <p>The whole-jar hashes below are pinned. Maven artifacts are immutable files, and both were verified
 * against the server's own published checksums, so a jar that hashes to something else is not the jar we
 * asked for and is rejected before a single entry is extracted.</p>
 */
public final class SourceChain
{
    /**
     * Source 1: the snapshot build the port is based on.
     */
    public static final AssetSource MAVEN_1374 = new AssetSource(
        "maven-1374",
        "https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/1.1.1374-1.21.1-snapshot/minecolonies-1.1.1374-1.21.1-snapshot.jar",
        AssetSource.Kind.HTTP,
        "9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa",
        78071143L,
        "MineColonies 1.1.1374 (snapshot) from LDTTeam's Maven");

    /**
     * Source 2: the previous release build, used when the snapshot has been pruned.
     */
    public static final AssetSource MAVEN_1368 = new AssetSource(
        "maven-1368",
        "https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/1.1.1368-1.21.1/minecolonies-1.1.1368-1.21.1.jar",
        AssetSource.Kind.HTTP,
        "c3a2542aaced85aabfc58b38415b70e6b095a16787056e07880fc94320f09a9b",
        77945293L,
        "MineColonies 1.1.1368 (release) from LDTTeam's Maven");

    /**
     * Source 3's compiled-in URL. <b>Empty on purpose</b>: the slot ships disabled and is skipped silently.
     *
     * <p>To enable it, put a URL here and rebuild — {@code http://} is fine, no TLS is required — or set
     * {@code customSourceUrl} in {@code state.json}, which takes precedence and needs no rebuild at all.</p>
     */
    public static final String OWNER_HOST_URL = "";

    /**
     * Id of source 3.
     */
    public static final String OWNER_HOST_ID = "owner-http";

    /**
     * Id of source 4.
     */
    public static final String LOCAL_JAR_ID = "local-jar";

    /**
     * Private constructor to hide the public one.
     */
    private SourceChain()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Builds the chain the installer walks.
     *
     * @param customSourceUrl the {@code customSourceUrl} from {@code state.json}, or null/empty when unset.
     *                        It overrides {@link #OWNER_HOST_URL}.
     * @return the sources to try, in order.
     */
    public static List<AssetSource> automatic(final String customSourceUrl)
    {
        final List<AssetSource> chain = new ArrayList<>(3);
        chain.add(MAVEN_1374);
        chain.add(MAVEN_1368);

        final String ownerUrl = customSourceUrl != null && !customSourceUrl.isBlank() ? customSourceUrl.trim() : OWNER_HOST_URL;
        if (!ownerUrl.isBlank())
        {
            chain.add(new AssetSource(OWNER_HOST_ID, ownerUrl, AssetSource.Kind.HTTP, null, -1L, "the owner-hosted copy"));
        }
        return chain;
    }

    /**
     * Wraps a jar the player picked themselves as source 4.
     *
     * @param path the file the player chose.
     * @return the source entry.
     */
    public static AssetSource localJar(final java.nio.file.Path path)
    {
        return new AssetSource(LOCAL_JAR_ID, path.toAbsolutePath().toString(), AssetSource.Kind.LOCAL_FILE, null, -1L,
            "the jar you selected");
    }

    /**
     * Every whole-jar hash this build recognises as an upstream MineColonies jar.
     *
     * @param manifest the install manifest, whose {@code sources} section may list more than the two pinned
     *                 here; may be null.
     * @return the known hashes, lower-case hex.
     */
    public static Set<String> knownJarHashes(final AssetManifest manifest)
    {
        final Set<String> known = new LinkedHashSet<>();
        known.add(MAVEN_1374.expectedSha256());
        known.add(MAVEN_1368.expectedSha256());
        if (manifest != null)
        {
            manifest.sources().values().stream()
                .map(AssetManifest.SourceInfo::jarSha256)
                .filter(hash -> hash != null && !hash.isBlank())
                .forEach(known::add);
        }
        return known;
    }

    /**
     * Which source id a jar with this hash belongs to.
     *
     * @param sha256   the whole-jar hash.
     * @param manifest the install manifest; may be null.
     * @return the matching source id, or null when the hash is not a known one.
     */
    public static String identify(final String sha256, final AssetManifest manifest)
    {
        if (MAVEN_1374.expectedSha256().equals(sha256))
        {
            return MAVEN_1374.id();
        }
        if (MAVEN_1368.expectedSha256().equals(sha256))
        {
            return MAVEN_1368.id();
        }
        if (manifest != null)
        {
            for (final var entry : manifest.sources().entrySet())
            {
                if (sha256.equals(entry.getValue().jarSha256()))
                {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * The upstream versions this build's manifest can verify, for the "that jar is not one I know" message.
     *
     * @return a human-readable list.
     */
    public static String supportedVersions()
    {
        return "MineColonies 1.1.1374-1.21.1-snapshot and 1.1.1368-1.21.1";
    }
}
