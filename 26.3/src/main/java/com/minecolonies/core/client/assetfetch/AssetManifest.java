package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shipped per-file hash manifest: what the installed pack must contain, and nothing else.
 *
 * <p>Format version 1, read from {@code /assetfetch/manifest.json} in the mod jar:</p>
 * <pre>
 * {
 *   "version": 1,
 *   "primarySource": "maven-1374",
 *   "sources": { "maven-1374": { "jarSha256": ..., "size": ..., "url": ... },
 *                 "github-src-1374": { "url": ..., "mayBeAbsent": [ "&lt;path prefix&gt;", ... ] }, ... },
 *   "files":   { "&lt;path&gt;": { "sha256": ..., "size": ... }, ... },
 *   "alt":     { "maven-1368": { "&lt;path&gt;": { "sha256": ..., "size": ... } | null, ... }, ... }
 * }
 * </pre>
 *
 * <p>A source with a {@code jarSha256} is one whose whole archive is pinned. A source without one is
 * verified only by what comes out of it, and its {@code mayBeAbsent} names the path prefixes it is allowed
 * not to supply at all — upstream's translations, in the case of a source release, which are injected
 * during upstream's own build and are not in their repository. {@code mayBeAbsent} forgives absence and
 * nothing else: a file that does arrive is hashed and compared like every other, and a file missing outside
 * these prefixes fails the source.</p>
 *
 * <p>{@code files} describes the whole pack except {@code pack.mcmeta}, which is written at install time from
 * the running game's pack format (see {@link PackMetaWriter}) and therefore has no fixed hash. {@code alt}
 * carries the handful of files that differ between upstream builds: for a given source, an entry in
 * {@code alt} <em>replaces</em> the one in {@code files}, and a {@code null} entry means the file must not be
 * present at all for that source.</p>
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
     */
    private static final int FORMAT_VERSION = 1;

    /**
     * SHA-256 of the manifest bytes this instance was loaded from; recorded in {@code state.json}.
     */
    private final String manifestSha256;

    /**
     * Which source id the manifest's {@code files} section describes verbatim.
     */
    private final String primarySource;

    /**
     * The known upstream jars, by source id.
     */
    private final Map<String, SourceInfo> sources;

    /**
     * The base file set, keyed by pack-relative path.
     */
    private final Map<String, FileEntry> files;

    /**
     * Per-source overrides. A null value means "this file must be absent for this source".
     */
    private final Map<String, Map<String, FileEntry>> alternates;

    /**
     * Creates a manifest.
     *
     * @param manifestSha256 hash of the bytes it was parsed from.
     * @param primarySource  the source id {@code files} describes.
     * @param sources        the known jars.
     * @param files          the base file set.
     * @param alternates     the per-source overrides.
     */
    private AssetManifest(final String manifestSha256, final String primarySource, final Map<String, SourceInfo> sources,
        final Map<String, FileEntry> files, final Map<String, Map<String, FileEntry>> alternates)
    {
        this.manifestSha256 = manifestSha256;
        this.primarySource = primarySource;
        this.sources = Collections.unmodifiableMap(sources);
        this.files = Collections.unmodifiableMap(files);
        this.alternates = Collections.unmodifiableMap(alternates);
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
                optionalString(body, "url"),
                prefixes(body, "mayBeAbsent")));
        }

        final Map<String, FileEntry> files = readFileMap(object(root, "files"));

        // Each set is read by its own spelling rather than by the base set's. An empty set is pack-relative by
        // vacuum, so a manifest whose whole file list sits in alt used to have its alt paths left alone — and a
        // path that is not pack-relative matches nothing under the pack root.
        final Map<String, Map<String, FileEntry>> alternates = new LinkedHashMap<>();
        final JsonObject rawAlt = object(root, "alt");
        for (final Map.Entry<String, JsonElement> entry : rawAlt.entrySet())
        {
            final Map<String, FileEntry> alt = readFileMap(entry.getValue().getAsJsonObject());
            alternates.put(entry.getKey(), normalise(alt, isPackRelative(alt.keySet())));
        }

        final JsonElement primary = root.get("primarySource");
        return new AssetManifest(Hashes.sha256(bytes),
            primary != null && primary.isJsonPrimitive() ? primary.getAsString() : null,
            sources, normalise(files, isPackRelative(files.keySet())), alternates);
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
     * <p>An install records the {@link #sha256()} of the manifest it was verified against, so comparing that
     * recorded value against this one is what separates a pack installed by an earlier build of this mod from
     * one that matches the build now running. The manifest describes every file of the pack down to its hash,
     * so it changes whenever the pack has to change and stays byte-identical whenever it does not; that makes
     * its own hash the cheapest honest answer to "is what is on disk still what this build expects".</p>
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
     * The source id the base {@code files} section describes.
     *
     * @return the primary source id, or null if the manifest names none.
     */
    public String primarySource()
    {
        return this.primarySource;
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
     * The file set the pack must match when the assets came from a given source.
     *
     * <p>{@code files} overlaid with {@code alt[sourceId]}: an entry there replaces the base one, and a
     * {@code null} entry there removes it, meaning the file must not exist for this source.</p>
     *
     * @param sourceId which source the jar came from.
     * @return pack-relative path to expected hash and size.
     */
    public Map<String, FileEntry> effectiveFor(final String sourceId)
    {
        final Map<String, FileEntry> effective = new LinkedHashMap<>(this.files);
        final Map<String, FileEntry> overrides = this.alternates.get(sourceId);
        if (overrides != null)
        {
            for (final Map.Entry<String, FileEntry> entry : overrides.entrySet())
            {
                if (entry.getValue() == null)
                {
                    effective.remove(entry.getKey());
                }
                else
                {
                    effective.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return effective;
    }

    /**
     * The path prefixes a given source is allowed not to supply at all.
     *
     * <p>Absence, and only absence, is what this forgives, and only for the source that declares it. A file
     * covered by one of these prefixes may be missing from the finished pack; a file that is present is
     * hashed and compared like any other, and anything missing outside them is a failed source.</p>
     *
     * @param sourceId the chain entry the archive came from.
     * @return the pack-relative prefixes, possibly empty, never null.
     */
    public List<String> mayBeAbsentFor(final String sourceId)
    {
        final SourceInfo info = this.sources.get(sourceId);
        return info == null ? List.of() : info.mayBeAbsent();
    }

    /**
     * Reads a {@code path -> {sha256,size}} map, keeping null values as "must be absent" markers.
     *
     * @param object the JSON object.
     * @return the parsed map, member order preserved.
     * @throws AssetInstallException if an entry is malformed.
     */
    private static Map<String, FileEntry> readFileMap(final JsonObject object) throws AssetInstallException
    {
        final Map<String, FileEntry> map = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            if (entry.getValue().isJsonNull())
            {
                map.put(entry.getKey(), null);
                continue;
            }
            if (!entry.getValue().isJsonObject())
            {
                throw new AssetInstallException("The install manifest entry for " + entry.getKey() + " is malformed");
            }
            final JsonObject body = entry.getValue().getAsJsonObject();
            final JsonElement sha = body.get("sha256");
            if (sha == null || !sha.isJsonPrimitive())
            {
                throw new AssetInstallException("The install manifest entry for " + entry.getKey() + " has no sha256");
            }
            map.put(entry.getKey(), new FileEntry(sha.getAsString(), body.has("size") ? body.get("size").getAsLong() : -1L));
        }
        return map;
    }

    /**
     * Whether the manifest spells its paths relative to the pack root.
     *
     * @param paths the keys of {@code files}.
     * @return true if every path already carries the {@code assets/} prefix.
     */
    private static boolean isPackRelative(final Iterable<String> paths)
    {
        for (final String path : paths)
        {
            if (!path.startsWith("assets/"))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Rewrites asset-root-relative paths as pack-relative ones.
     *
     * @param map          the map to rewrite.
     * @param packRelative whether the manifest's paths are already pack-relative.
     * @return the map, keyed pack-relative.
     */
    private static Map<String, FileEntry> normalise(final Map<String, FileEntry> map, final boolean packRelative)
    {
        if (packRelative)
        {
            return map;
        }
        final Map<String, FileEntry> out = new LinkedHashMap<>(map.size());
        map.forEach((path, entry) -> out.put(ASSET_PREFIX + path, entry));
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
     * Reads an array of pack-relative path prefixes, tolerating absence and wrong types.
     *
     * <p>Prefixes are accepted in either spelling, exactly like file paths, and are normalised to
     * pack-relative here so the pipeline can compare them against manifest keys without thinking about
     * it.</p>
     *
     * @param object the object to read from.
     * @param member the member name.
     * @return the prefixes, possibly empty, never null.
     */
    private static List<String> prefixes(final JsonObject object, final String member)
    {
        final JsonElement value = object.get(member);
        if (value == null || !value.isJsonArray())
        {
            return List.of();
        }
        final JsonArray array = value.getAsJsonArray();
        final List<String> out = new ArrayList<>(array.size());
        for (final JsonElement element : array)
        {
            if (!element.isJsonPrimitive())
            {
                continue;
            }
            final String prefix = element.getAsString();
            if (!prefix.isBlank())
            {
                out.add(prefix.startsWith("assets/") ? prefix : ASSET_PREFIX + prefix);
            }
        }
        return out;
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
     * One expected file.
     *
     * @param sha256 its SHA-256, lower-case hex.
     * @param size   its size in bytes, or -1 when the manifest does not state one.
     */
    public record FileEntry(String sha256, long size)
    {
    }

    /**
     * One known upstream archive.
     *
     * @param jarSha256    the whole-archive SHA-256, or null when this source pins none and is trusted only
     *                     by the files that come out of it.
     * @param size         the archive's size in bytes, or -1 when unstated.
     * @param url          where it came from.
     * @param mayBeAbsent  pack-relative path prefixes this source is allowed not to supply. Never null.
     */
    public record SourceInfo(String jarSha256, long size, String url, List<String> mayBeAbsent)
    {
        /**
         * Normalises the prefix list so callers never see null or a mutable view.
         *
         * @param jarSha256   the whole-archive hash, or null.
         * @param size        the size, or -1.
         * @param url         the URL.
         * @param mayBeAbsent the prefixes.
         */
        public SourceInfo
        {
            mayBeAbsent = mayBeAbsent == null ? List.of() : List.copyOf(mayBeAbsent);
        }
    }
}
