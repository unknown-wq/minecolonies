package com.minecolonies.core.client.assetfetch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The download source chain, in the order the installer tries it.
 *
 * <ol>
 *     <li><b>{@code maven-1374}</b> and <b>{@code maven-1368}</b> — LDTTeam's own Maven: the snapshot build
 *         this port was made from, then the previous release build. Both are pinned whole and checked before
 *         a single entry is unpacked.</li>
 *     <li><b>{@code cdn-1376}</b>, <b>{@code cdn-1375}</b>, <b>{@code cdn-1374}</b> and <b>{@code cdn-1368}</b>
 *         — the same kind of built jar, from the file store behind LDTTeam's CurseForge project page. Four
 *         builds, newest first, every one pinned whole. Two of them are the very files the Maven entries
 *         serve, byte for byte, under a second address; the other two are the builds published after this
 *         port's reference build, which differ from it in their translations and in nothing else. See below
 *         for why they can be in the chain at all.</li>
 *     <li><b>{@code github-src-1368}</b> — one pinned source release from LDTTeam's own GitHub. Its contents
 *         were measured against this manifest before shipping, so it is the last thing in the chain known to
 *         hold what it should rather than found out about at run time.</li>
 *     <li><b>discovered releases</b> — whatever that repository is actually serving today, asked for once,
 *         and only once everything above has failed. Appended by the pipeline rather than listed here, which
 *         is what keeps a working install from ever causing a request to anybody's API. See
 *         {@link GithubReleases}.</li>
 *     <li><b>{@code owner-http}</b> — a single configurable slot, shipped empty and therefore skipped in
 *         silence, kept as the owner's own way in if every upstream path above is gone.</li>
 *     <li><b>{@code local-jar}</b> — a jar the player downloaded themselves, e.g. from CurseForge. Not part
 *         of the automatic chain at all: the UI hands it in when the player asks for it, which is why it
 *         comes after everything the installer can do on its own.</li>
 * </ol>
 *
 * <p>The order is the point. What was checked in advance is tried before what is learned on the way, and
 * both are tried before the player is asked to do anything by hand.</p>
 *
 * <h2>Why GitHub is a source archive and not a jar</h2>
 *
 * <p>LDTTeam tag every build in their public repository and GitHub publishes a release for each tag, but
 * they attach no built artifact to those releases: the only files a release carries are the two source
 * archives GitHub generates from the tag itself. So the GitHub entries fetch the source archive and take the
 * assets out of the project's own source layout, which is the same content the jar is built from.</p>
 *
 * <p>One tag is pinned, and it is a release tag rather than a snapshot one. A pin exists to be the thing
 * that still answers when nothing else does, so it should be the most durable kind of tag there is —
 * {@value #PINNED_TAG} is what upstream currently presents as the release for this game version, and if they
 * ever tidy snapshots away it is the kind that survives. Being current is not the pin's job; that is what
 * discovery is for, and discovery is not frozen at build time the way a constant is.</p>
 *
 * <p>That has one honest cost. Upstream's translations are injected during their build and are not committed
 * to the repository, so a source archive carries no {@code lang/} at all. That is no longer a declaration
 * anything has to make in advance: a path the archive does not supply is taken from the pack the player
 * already has, and on a first install it is simply not there. Which is why a source release is worth having
 * in the chain — it is a whole working pack minus the translations, and for a player who already has them
 * it is a whole working pack.</p>
 *
 * <h2>Why builds other than the reference one can be in the chain</h2>
 *
 * <p>Between one build and the next, upstream changes the translations and little else. Under a
 * byte-for-byte rule that made every build but the one the manifest was measured on unusable, which is a
 * strange thing to say about files the game reads by name. The manifest now lists the paths the pack is made
 * of and nothing about their contents, so any of these builds installs: what comes out of the archive at a
 * listed path is what goes into the pack. The archives themselves are still pinned whole, so what is
 * installed is still exactly the build named here and not something else served under its URL.</p>
 *
 * <h2>CurseForge</h2>
 *
 * <p>The four {@code cdn-} entries are direct links to jars on the file store CurseForge serves its
 * downloads from. Those links answer any plain request, with no key and no account, exactly as they do for
 * a browser; they are the same links a player follows by hand. What is <em>not</em> used is CurseForge's
 * distribution API, which answers every unauthenticated request with {@code 403 Forbidden: API Key missing
 * or invalid} and requires a registered key: nothing here asks it for anything, nothing scrapes a project
 * page for a link, and the request carries this mod's own {@code User-Agent} rather than a browser's. The
 * links were taken by hand, once, and pinned by hash like the Maven entries, so a file that has changed
 * behind one of them is refused before a single entry is extracted. Of the four, 1368 is a release build
 * and the other three are snapshots; upstream does not usually tidy release tags away, so that one is the
 * likeliest to still answer in a year.</p>
 *
 * <p>The whole-archive hashes below are pinned for the Maven and CDN entries only: those serve immutable
 * files, and each one was hashed after download and, for Maven, checked against the server's own published
 * checksums, so a jar that hashes to something else is not the jar we asked for. <b>For those six entries
 * that pin is the whole of the integrity story</b> — nothing after the unpack looks at a single file again.
 * GitHub generates its source archives per request and promises nothing about their bytes, so there is
 * nothing honest to pin there, and nothing pretends otherwise: what such an archive carries is installed as
 * it comes.</p>
 */
public final class SourceChain
{
    /**
     * LDTTeam's repository, {@code owner/name}.
     */
    public static final String REPOSITORY = "ldtteam/minecolonies";

    /**
     * The Minecraft version this port is for. Upstream's tags carry it, so it is what tells a tag meant for
     * this game apart from one meant for another.
     */
    public static final String GAME_VERSION = "1.21.1";

    /**
     * The one pinned GitHub tag.
     */
    public static final String PINNED_TAG = "v1.21.1-1.1.1368";

    /**
     * The upstream build the manifest's base file set describes. Discovery orders what it finds by nearness
     * to this, because a build that changed the assets makes every later build differ too.
     */
    public static final int REFERENCE_BUILD = 1374;

    /**
     * Source 1: the snapshot build the port is based on.
     */
    public static final AssetSource MAVEN_1374 = new AssetSource(
        "maven-1374",
        "https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/1.1.1374-1.21.1-snapshot/minecolonies-1.1.1374-1.21.1-snapshot.jar",
        AssetSource.Kind.HTTP,
        "9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa",
        78071143L,
        ArchiveLayout.MOD_JAR,
        false,
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
        ArchiveLayout.MOD_JAR,
        false,
        "MineColonies 1.1.1368 (release) from LDTTeam's Maven");

    /**
     * Where CurseForge keeps the files it serves: the root every direct file link starts with.
     */
    private static final String CDN_ROOT = "https://mediafilez.forgecdn.net/files/";

    /**
     * Source 3: the newest build published after the reference one, from CurseForge's file store.
     */
    public static final AssetSource CDN_1376 = new AssetSource(
        "cdn-1376",
        CDN_ROOT + "8782/142/minecolonies-1.1.1376-1.21.1-snapshot.jar",
        AssetSource.Kind.HTTP,
        "ac86d034b696d9eaccdc089c67d788dde21681902cfc75ada075fd677b479a6d",
        78069211L,
        ArchiveLayout.MOD_JAR,
        false,
        "MineColonies 1.1.1376 (snapshot) from CurseForge's file store");

    /**
     * Source 4: the build between the reference one and {@link #CDN_1376}, from CurseForge's file store.
     */
    public static final AssetSource CDN_1375 = new AssetSource(
        "cdn-1375",
        CDN_ROOT + "8765/939/minecolonies-1.1.1375-1.21.1-snapshot.jar",
        AssetSource.Kind.HTTP,
        "0cb7bca88edac4ea662685d4847e79e03a5065fcc833ac786f62145457dd71c2",
        78070754L,
        ArchiveLayout.MOD_JAR,
        false,
        "MineColonies 1.1.1375 (snapshot) from CurseForge's file store");

    /**
     * Source 5: the reference build again, from CurseForge's file store. Byte-identical to
     * {@link #MAVEN_1374} — the same hash, so the same jar under a second address.
     */
    public static final AssetSource CDN_1374 = new AssetSource(
        "cdn-1374",
        CDN_ROOT + "8621/898/minecolonies-1.1.1374-1.21.1-snapshot.jar",
        AssetSource.Kind.HTTP,
        MAVEN_1374.expectedSha256(),
        MAVEN_1374.expectedSize(),
        ArchiveLayout.MOD_JAR,
        false,
        "MineColonies 1.1.1374 (snapshot) from CurseForge's file store");

    /**
     * Source 6: the previous release build again, from CurseForge's file store. Byte-identical to
     * {@link #MAVEN_1368}, and the one release build among the four CDN entries.
     */
    public static final AssetSource CDN_1368 = new AssetSource(
        "cdn-1368",
        CDN_ROOT + "8562/588/minecolonies-1.1.1368-1.21.1.jar",
        AssetSource.Kind.HTTP,
        MAVEN_1368.expectedSha256(),
        MAVEN_1368.expectedSize(),
        ArchiveLayout.MOD_JAR,
        false,
        "MineColonies 1.1.1368 (release) from CurseForge's file store");

    /**
     * Every entry pinned by a whole-jar hash, in chain order.
     */
    private static final List<AssetSource> PINNED_JARS = List.of(MAVEN_1374, MAVEN_1368, CDN_1376, CDN_1375, CDN_1374, CDN_1368);

    /**
     * Id of the pinned GitHub source.
     */
    public static final String GITHUB_PINNED_ID = "github-src-1368";

    /**
     * Source 7: the one pinned source release, from LDTTeam's own GitHub.
     */
    public static final AssetSource GITHUB_PINNED = new AssetSource(
        GITHUB_PINNED_ID,
        archiveUrl(PINNED_TAG),
        AssetSource.Kind.HTTP,
        null,
        -1L,
        ArchiveLayout.SOURCE_ARCHIVE,
        true,
        "the MineColonies 1.1.1368 source release on LDTTeam's GitHub");

    /**
     * The owner's slot's compiled-in URL. <b>Empty on purpose</b>: the slot ships disabled and is skipped
     * silently.
     *
     * <p>To enable it, put a URL here and rebuild — {@code http://} is fine, no TLS is required — or set
     * {@code customSourceUrl} in {@code state.json}, which takes precedence and needs no rebuild at all.</p>
     */
    public static final String OWNER_HOST_URL = "";

    /**
     * Id of the owner's slot.
     */
    public static final String OWNER_HOST_ID = "owner-http";

    /**
     * Id of the manual entry.
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
     * The download URL of a repository tag's source archive.
     *
     * <p>Built from the tag rather than taken from anywhere, which is what makes it safe to call with a tag
     * that came off the network — provided the caller has checked its shape first, as {@link GithubReleases}
     * does.</p>
     *
     * @param tag the tag name.
     * @return the archive URL.
     */
    public static String archiveUrl(final String tag)
    {
        return "https://github.com/" + REPOSITORY + "/archive/refs/tags/" + tag + ".zip";
    }

    /**
     * Everything tried before discovery: the two pinned Maven artifacts, the four pinned CDN jars, then the
     * one pinned source release.
     *
     * <p>Neither the manual entry nor the owner's slot is in it, and neither is discovery. The pipeline runs
     * this list first, then asks discovery, then runs {@link #afterDiscovery}, which is what puts those three
     * in the order the chain documents.</p>
     *
     * <p>Each entry is tried once. There is no retry loop anywhere in this feature: a source that answers
     * 404, 403 or nothing at all is a source that has failed, and asking it again would only make somebody
     * else's server carry the cost of our optimism.</p>
     *
     * @return the sources to try, in order.
     */
    public static List<AssetSource> automatic()
    {
        final List<AssetSource> sources = new ArrayList<>(PINNED_JARS);
        sources.add(GITHUB_PINNED);
        return List.copyOf(sources);
    }

    /**
     * Everything tried after discovery has had its turn: the owner's slot, when it has been filled in.
     *
     * <p>Shipped builds leave it empty, so this is normally an empty list and the chain ends at discovery.
     * It is run whether or not discovery found anything, because whether the owner has a host of their own
     * has nothing to do with what GitHub happened to answer.</p>
     *
     * @param customSourceUrl the {@code customSourceUrl} from {@code state.json}, or null/empty when unset.
     *                        It overrides {@link #OWNER_HOST_URL}.
     * @return the sources to try last, in order.
     */
    public static List<AssetSource> afterDiscovery(final String customSourceUrl)
    {
        final String ownerUrl = customSourceUrl != null && !customSourceUrl.isBlank() ? customSourceUrl.trim() : OWNER_HOST_URL;
        if (ownerUrl.isBlank())
        {
            return List.of();
        }
        return List.of(new AssetSource(OWNER_HOST_ID, ownerUrl, AssetSource.Kind.HTTP, null, -1L,
            ArchiveLayout.MOD_JAR, false, "the owner-hosted copy"));
    }

    /**
     * The discoverer the game uses: LDTTeam's release listing, minus the tag already pinned above.
     *
     * @return the discoverer.
     */
    public static SourceDiscovery discovery()
    {
        return new GithubReleases(REPOSITORY, GAME_VERSION, REFERENCE_BUILD, Set.of(PINNED_TAG));
    }

    /**
     * Wraps a tag found at install time as a source.
     *
     * <p>It is unpinned and stays unpinned: GitHub builds these archives per request, so there is no hash to
     * give it. What it carries at the manifest's paths is what gets installed, and whatever it does not carry
     * — its {@code lang/} above all — comes from the pack the player already has, or from nowhere. Being
     * unpinned is also what lets it fail late without taking the install down: if it will not unpack or will
     * not patch, the chain simply goes on to the next entry.</p>
     *
     * @param tag   the tag, already checked against {@link GithubReleases}'s accepted shape.
     * @param build the upstream build number it names.
     * @return the source entry.
     */
    public static AssetSource discovered(final String tag, final int build)
    {
        return new AssetSource("github-src-" + build, archiveUrl(tag), AssetSource.Kind.HTTP, null, -1L,
            ArchiveLayout.SOURCE_ARCHIVE, true,
            "the MineColonies " + build + " source release on LDTTeam's GitHub");
    }

    /**
     * Wraps a jar the player picked themselves as the manual entry.
     *
     * @param path the file the player chose.
     * @return the source entry.
     */
    public static AssetSource localJar(final java.nio.file.Path path)
    {
        return new AssetSource(LOCAL_JAR_ID, path.toAbsolutePath().toString(), AssetSource.Kind.LOCAL_FILE, null, -1L,
            ArchiveLayout.MOD_JAR, false, "the jar you selected");
    }

    /**
     * Every whole-jar hash this build recognises as an upstream MineColonies jar.
     *
     * @param manifest the install manifest, whose {@code sources} section may list more than the ones pinned
     *                 here; may be null.
     * @return the known hashes, lower-case hex.
     */
    public static Set<String> knownJarHashes(final AssetManifest manifest)
    {
        final Set<String> known = new LinkedHashSet<>();
        for (final AssetSource source : PINNED_JARS)
        {
            known.add(source.expectedSha256());
        }
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
        // Chain order, so a jar two entries serve byte for byte is named after the first of them.
        for (final AssetSource source : PINNED_JARS)
        {
            if (source.expectedSha256().equals(sha256))
            {
                return source.id();
            }
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
     * The upstream versions this build knows by hash, for the "that jar is not one I know" message.
     *
     * @return a human-readable list.
     */
    public static String supportedVersions()
    {
        return "MineColonies 1.1.1376-1.21.1-snapshot, 1.1.1375-1.21.1-snapshot, 1.1.1374-1.21.1-snapshot and 1.1.1368-1.21.1";
    }
}
