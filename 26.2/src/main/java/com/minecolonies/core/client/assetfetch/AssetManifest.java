package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The shipped manifest: which paths the installed pack is made of, and which archives are pinned whole.
 *
 * <p>Format version 2, read from {@code /assetfetch/manifest.json} in the mod jar:</p>
 * <pre>
 * {
 *   "version": 2,
 *   "sources": { "maven-1374": { "jarSha256": ..., "size": ..., "url": ... }, ... },
 *   "files":   [ "&lt;path&gt;", ... ]
 * }
 * </pre>
 *
 * <p><b>The manifest is a list of paths and nothing more.</b> It says what the pack consists of; it says
 * nothing about what any of those files contain. Whatever the archive carries at a listed path is what gets
 * installed, and a path the archive does not carry is filled in from the pack already on disk or left out —
 * see {@link PackAssembler}. There are no per-file hashes to compare against, because a build other than the
 * one the list was measured on is still a build this port wants to be able to install from, and every
 * per-file rule that tried to allow that ended up either rejecting good archives or being a special case for
 * one directory.</p>
 *
 * <p><b>Integrity lives one level up.</b> A source with a {@code jarSha256} is pinned as a whole archive and
 * is checked, hash and size, before a single entry is unpacked; that check is what stands between a player
 * and a corrupted or substituted download, and this class is where those pins are read from. A source
 * without one — a GitHub source archive, the owner's slot, a jar the player picked — pins nothing, and
 * nothing downstream of the unpack re-checks its bytes: what such a source is trusted for is exactly what it
 * carries, sat under the paths listed here. {@link SourceChain} says which sources are of which kind.</p>
 *
 * <p>{@code files} describes the whole pack except {@code pack.mcmeta}, which is written at install time
 * from the running game's pack format (see {@link PackMetaWriter}) and is therefore never listed.</p>
 *
 * <p>Paths are accepted in either spelling — pack-relative ({@code assets/minecolonies/gui/main.xml}) or
 * relative to the asset root ({@code gui/main.xml}) — and are normalised to pack-relative on load, so this
 * class agrees with whichever convention the generator used.</p>
 */
public final class AssetManifest
{
    /**
     * Name of the manifest inside the bundle.
     */
    public static final String FILE_NAME = "manifest.json";

    /**
     * The pack-relative prefix every asset file sits under.
     */
    public static final String ASSET_PREFIX = "assets/minecolonies/";

    /**
     * The only manifest schema this build understands.
     *
     * <p>Two, because version 1 was a map of paths to per-file hashes and this one is a list of paths: a
     * build reading a version-1 manifest as a path list would take its member names for paths and its hashes
     * for nothing, which is close enough to working to be worth refusing outright.</p>
     */
    private static final int FORMAT_VERSION = 2;

    /**
     * SHA-256 of the manifest bytes this instance was loaded from; recorded in {@code state.json}.
     */
    private final String manifestSha256;

    /**
     * The known upstream jars, by source id.
     */
    private final Map<String, SourceInfo> sources;

    /**
     * The pack's file set, pack-relative, in the order the manifest lists it.
     */
    private final Set<String> files;

    /**
     * Creates a manifest.
     *
     * @param manifestSha256 hash of the bytes it was parsed from.
     * @param sources        the known jars.
     * @param files          the pack's file set.
     */
    private AssetManifest(final String manifestSha256, final Map<String, SourceInfo> sources, final Set<String> files)
    {
        this.manifestSha256 = manifestSha256;
        this.sources = Collections.unmodifiableMap(sources);
        this.files = Collections.unmodifiableSet(files);
    }

    /**
     * Loads the manifest from the install bundle.
     *
     * @param resources the bundle to read {@value #FILE_NAME} from.
     * @return the parsed manifest.
     * @throws AssetInstallException if it is missing, unreadable or of an unknown version.
     */
    public static AssetManifest load(final BundleResources resources) throws AssetInstallException
    {
        final byte[] bytes;
        try
        {
            bytes = resources.read(FILE_NAME);
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("The install manifest could not be read from the mod jar", e);
        }
        return parse(bytes);
    }

    /**
     * Parses manifest bytes.
     *
     * @param bytes the UTF-8 manifest.
     * @return the parsed manifest.
     * @throws AssetInstallException if it is malformed or of an unknown version.
     */
    public static AssetManifest parse(final byte[] bytes) throws AssetInstallException
    {
        final JsonElement parsed;
        try
        {
            parsed = CanonicalJson.parse(bytes, FILE_NAME);
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("The install manifest is not valid JSON", e);
        }
        if (!parsed.isJsonObject())
        {
            throw new AssetInstallException("The install manifest is not a JSON object");
        }

        final JsonObject root = parsed.getAsJsonObject();
        final JsonElement version = root.get("version");
        if (version == null || !version.isJsonPrimitive() || version.getAsInt() != FORMAT_VERSION)
        {
            throw new AssetInstallException("The install manifest has an unsupported version");
        }

        final Map<String, SourceInfo> sources = new LinkedHashMap<>();
        final JsonObject rawSources = object(root, "sources");
        for (final Map.Entry<String, JsonElement> entry : rawSources.entrySet())
        {
            final JsonObject body = entry.getValue().getAsJsonObject();
            sources.put(entry.getKey(), new SourceInfo(
                optionalString(body, "jarSha256"),
                body.has("size") ? body.get("size").getAsLong() : -1L,
                optionalString(body, "url")));
        }

        return new AssetManifest(Hashes.sha256(bytes), sources, readFiles(root));
    }

    /**
     * SHA-256 of the manifest resource this instance came from.
     *
     * @return the hash, recorded in {@code state.json} as {@code manifestSha256}.
     */
    public String sha256()
    {
        return this.manifestSha256;
    }

    /**
     * The hash of the manifest a build ships, without parsing it.
     *
     * <p>An install records the {@link #sha256()} of the manifest it was installed against, so comparing
     * that recorded value against this one is what separates a pack installed by an earlier build of this
     * mod from one that matches the build now running. The manifest lists every file the pack is made of, so
     * it changes whenever the pack's composition has to change and stays byte-identical whenever it does
     * not; that makes its own hash the cheapest honest answer to "is what is on disk still the set of files
     * this build expects".</p>
     *
     * <p>Deliberately no parsing: this runs on the way to the title screen, the bytes are the identity, and a
     * manifest this build cannot parse is the installer's problem to report, not the freshness check's.</p>
     *
     * @param resources the bundle to read {@value #FILE_NAME} from.
     * @return the hash, lower-case hex.
     * @throws IOException if the manifest is missing or unreadable.
     */
    public static String shippedSha256(final BundleResources resources) throws IOException
    {
        return Hashes.sha256(resources.read(FILE_NAME));
    }

    /**
     * The upstream jars the manifest knows about.
     *
     * @return source id to jar description.
     */
    public Map<String, SourceInfo> sources()
    {
        return this.sources;
    }

    /**
     * The paths the installed pack is made of.
     *
     * <p>The same set for every source: which archive the assets came from changes what is inside those
     * files, never which files there are.</p>
     *
     * @return the pack-relative paths, in manifest order.
     */
    public Set<String> files()
    {
        return this.files;
    }

    /**
     * Reads the {@code files} array and normalises it to pack-relative paths.
     *
     * @param root the manifest root.
     * @return the file set, member order preserved.
     * @throws AssetInstallException if it is missing, not an array, or holds something that is not a path.
     */
    private static Set<String> readFiles(final JsonObject root) throws AssetInstallException
    {
        final JsonElement value = root.get("files");
        if (value == null || !value.isJsonArray())
        {
            throw new AssetInstallException("The install manifest has no files array");
        }
        final Set<String> raw = new LinkedHashSet<>();
        for (final JsonElement element : value.getAsJsonArray())
        {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            {
                throw new AssetInstallException("The install manifest lists something that is not a path: " + element);
            }
            final String path = element.getAsString();
            if (path.isBlank())
            {
                throw new AssetInstallException("The install manifest lists an empty path");
            }
            raw.add(path);
        }

        // Either spelling is accepted, but not a mixture: a list that is pack-relative in part would have
        // half of it prefixed twice, and the pack would come out with a directory nothing ever reads.
        final Set<String> out = new LinkedHashSet<>(raw.size());
        for (final String path : raw)
        {
            out.add(path.startsWith("assets/") ? path : ASSET_PREFIX + path);
        }
        return out;
    }

    /**
     * Reads a top-level object member, tolerating its absence.
     *
     * @param root   the manifest root.
     * @param member the member name.
     * @return the object, or an empty one.
     */
    private static JsonObject object(final JsonObject root, final String member)
    {
        final JsonElement value = root.get(member);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    /**
     * Reads a string member, tolerating absence and wrong types.
     *
     * @param object the object to read from.
     * @param member the member name.
     * @return the value, or null.
     */
    private static String optionalString(final JsonObject object, final String member)
    {
        final JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /**
     * One known upstream archive.
     *
     * <p>The whole-archive pin is the one integrity check this feature still makes, and this is where the
     * value it is checked against is read from. A source with no {@code jarSha256} is not pinned at all.</p>
     *
     * @param jarSha256 the whole-archive SHA-256, or null when this source pins none.
     * @param size      the archive's size in bytes, or -1 when unstated.
     * @param url       where it came from.
     */
    public record SourceInfo(String jarSha256, long size, String url)
    {
    }
}
