package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecolonies.api.util.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Install state of the runtime-fetched upstream assets.
 *
 * <p>This is the read side of the contract between the installer (which writes {@code state.json} and the
 * {@code pack/} directory) and everything that has to know whether those assets are present: the
 * {@link FetchedAssetsSource} resource-pack source, the window-open gate and the consent UI.</p>
 *
 * <p><b>Cache layout</b> ({@code C1} in the implementation brief), all under {@link #baseDir()}:</p>
 * <ul>
 *     <li>{@code pack/} — the pack root that gets injected: {@code pack.mcmeta} plus
 *         {@code assets/minecolonies/**}. See {@link #packDir()}.</li>
 *     <li>{@code state.json} — the install record. See {@link #stateFile()}.</li>
 *     <li>{@code tmp/} — in-flight download, atomically promoted on success. Owned by the installer;
 *         nothing here looks at it.</li>
 * </ul>
 *
 * <p><b>{@code state.json}, schema version 1</b> — UTF-8 JSON, a single object:</p>
 * <pre>
 * {
 *   "version":        1,
 *   "status":         "installed" | "declined",
 *   "sourceId":       string,   // which entry of the source chain produced the install
 *   "sourceUrl":      string,   // the URL (or local path) the jar came from
 *   "jarSha256":      string,   // whole-jar hash of what was downloaded
 *   "manifestSha256": string,   // hash of the manifest the install was verified against
 *   "installedAt":    string,   // ISO-8601 instant
 *   "customSourceUrl": string   // owner-supplied override for source 3; may be absent or empty
 * }
 * </pre>
 *
 * <p>This class only ever <em>reads</em> that file, and treats every kind of damage — missing, unreadable,
 * not an object, unknown {@code version}, absent {@code status} — as "not installed". A half-written or
 * hand-edited state file must never be able to throw on a resource reload.</p>
 *
 * <p>The same applies to {@code pack/pack.mcmeta}: {@link #isReady()} does not merely check that the file is
 * there, it parses it and requires the {@code pack.min_format} and {@code pack.max_format} integers
 * {@link PackMetaWriter} writes. That is exactly what {@code Pack.readMetaAndCreate} needs to build the pack,
 * and it returns {@code null} on anything less — at which point {@link FetchedAssetsSource} can only drop the
 * pack and log. Were {@link #isReady()} still to answer true for such a pack, the whole downloaded asset set
 * would vanish with no gate, no consent screen and no way back short of deleting the cache by hand. Judging
 * the metadata by the same standard the game does keeps a damaged install visible as "not installed", so the
 * consent screen offers the download again.</p>
 *
 * <p>Deliberately <em>not</em> annotated {@code @Environment(EnvType.CLIENT)}: it is only ever consulted on
 * the client, but it is a plain static utility with no client-side types in it, and leaving it un-stripped
 * means a caller on a shared code path cannot trip over a missing class on a dedicated server.</p>
 */
public final class AssetFetch
{
    /**
     * Directory under the game directory holding everything this feature owns.
     */
    private static final String CACHE_DIR_NAME = "fetched-assets";

    /**
     * Name of the injected pack root inside {@link #baseDir()}.
     */
    private static final String PACK_DIR_NAME = "pack";

    /**
     * The pack metadata file the game itself requires; its presence is part of {@link #isReady()}.
     */
    private static final String PACK_META_NAME = "pack.mcmeta";

    /**
     * Name of the install-state file inside {@link #baseDir()}.
     */
    private static final String STATE_FILE_NAME = "state.json";

    /**
     * The only {@code state.json} schema this build understands.
     */
    private static final int SCHEMA_VERSION = 1;

    /**
     * The one {@code status} value that means "the assets are installed and verified".
     */
    private static final String STATUS_INSTALLED = "installed";

    /**
     * Memoised result of {@link #isReady()}. {@code null} means "not computed since the last
     * {@link #invalidate()}". Volatile because the resource reload runs off the render thread.
     */
    private static volatile Boolean readyCache = null;

    /**
     * Private constructor to hide the public one.
     */
    private AssetFetch()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Root of this feature's cache: {@code <gameDir>/minecolonies/fetched-assets/}.
     *
     * @return the base directory. It is not created here; the installer creates it.
     */
    public static Path baseDir()
    {
        return FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve(CACHE_DIR_NAME);
    }

    /**
     * The pack root that {@link FetchedAssetsSource} hands to the game.
     *
     * @return {@link #baseDir()}{@code /pack}.
     */
    public static Path packDir()
    {
        return baseDir().resolve(PACK_DIR_NAME);
    }

    /**
     * The install-state file.
     *
     * @return {@link #baseDir()}{@code /state.json}.
     */
    public static Path stateFile()
    {
        return baseDir().resolve(STATE_FILE_NAME);
    }

    /**
     * Whether the fetched assets are installed and usable.
     *
     * <p>True only when {@code state.json} exists, parses as a schema-version-1 object with
     * {@code status == "installed"}, and {@code pack/pack.mcmeta} exists <em>and</em> parses as pack metadata
     * the game would accept — an object with a {@code pack} object carrying integer {@code min_format} and
     * {@code max_format} members. Anything else is false — including every parse failure.</p>
     *
     * <p>The answer is cached, because it is consulted on every {@code loadPacks} and on every
     * gated window open. Call {@link #invalidate()} after installing or uninstalling.</p>
     *
     * @return true if the pack directory may be offered to the game.
     */
    public static boolean isReady()
    {
        Boolean cached = readyCache;
        if (cached == null)
        {
            cached = computeReady();
            readyCache = cached;
        }
        return cached;
    }

    /**
     * Drops the cached {@link #isReady()} answer, so the next call re-reads the disk. The installer calls
     * this after a successful install and after an uninstall.
     */
    public static void invalidate()
    {
        readyCache = null;
    }

    /**
     * The actual disk check behind {@link #isReady()}.
     *
     * @return true if state and pack are both present and consistent.
     */
    private static boolean computeReady()
    {
        final Path state = stateFile();
        if (!Files.isRegularFile(state))
        {
            return false;
        }

        final JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(state, StandardCharsets.UTF_8))
        {
            final JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject())
            {
                Log.getLogger().warn("Ignoring {}: expected a JSON object", state);
                return false;
            }
            root = parsed.getAsJsonObject();
        }
        catch (final Exception e)
        {
            Log.getLogger().warn("Ignoring unreadable asset install state at {}", state, e);
            return false;
        }

        if (getInt(root, "version", -1) != SCHEMA_VERSION)
        {
            Log.getLogger().warn("Ignoring asset install state at {}: unsupported schema version", state);
            return false;
        }

        if (!STATUS_INSTALLED.equals(getString(root, "status")))
        {
            return false;
        }

        final Path meta = packDir().resolve(PACK_META_NAME);
        if (!Files.isRegularFile(meta))
        {
            Log.getLogger().warn("Asset install state says installed, but {} is missing -- treating as not installed", meta);
            return false;
        }

        return hasUsablePackMeta(meta);
    }

    /**
     * Whether {@code pack.mcmeta} still says what {@link PackMetaWriter} wrote, to the depth the game itself
     * demands: an object with a {@code pack} object in it holding integer {@code min_format} and
     * {@code max_format} members.
     *
     * <p>Checked because {@code Pack.readMetaAndCreate} yields {@code null} for metadata it cannot read, and a
     * {@code null} pack is only logged. Answering "not installed" here instead keeps the install visible as
     * missing, so the gate holds and the consent screen offers the download again.</p>
     *
     * <p>Cheap by construction: the file {@link PackMetaWriter} writes is a hundred-odd bytes, and this runs
     * only when {@link #isReady()} has no cached answer.</p>
     *
     * @param meta the {@code pack.mcmeta} to judge.
     * @return true if the game would be able to build a pack from it.
     */
    private static boolean hasUsablePackMeta(final Path meta)
    {
        final JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(meta, StandardCharsets.UTF_8))
        {
            final JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject())
            {
                Log.getLogger().warn("Asset install state says installed, but {} is not a JSON object"
                    + " -- treating as not installed", meta);
                return false;
            }
            root = parsed.getAsJsonObject();
        }
        catch (final Exception e)
        {
            Log.getLogger().warn("Asset install state says installed, but {} could not be read"
                + " -- treating as not installed", meta, e);
            return false;
        }

        final JsonElement section = root.get("pack");
        if (section == null || !section.isJsonObject())
        {
            Log.getLogger().warn("Asset install state says installed, but {} has no \"pack\" object"
                + " -- treating as not installed", meta);
            return false;
        }

        final JsonObject pack = section.getAsJsonObject();
        if (!hasInt(pack, "min_format") || !hasInt(pack, "max_format"))
        {
            Log.getLogger().warn("Asset install state says installed, but {} is missing an integer min_format"
                + " or max_format -- treating as not installed", meta);
            return false;
        }

        return true;
    }

    /**
     * Whether a member is present and holds a whole number, the way a pack format has to be.
     *
     * @param root   the object to read from.
     * @param member the member name.
     * @return true if the member is an integral JSON number.
     */
    private static boolean hasInt(final JsonObject root, final String member)
    {
        final JsonElement value = root.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        {
            return false;
        }
        final double number = value.getAsDouble();
        return number == Math.floor(number) && !Double.isInfinite(number);
    }

    /**
     * Reads a string member, tolerating absence and wrong types.
     *
     * @param root   the object to read from.
     * @param member the member name.
     * @return the value, or null if absent or not a string.
     */
    private static String getString(final JsonObject root, final String member)
    {
        final JsonElement value = root.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        {
            return null;
        }
        return value.getAsString();
    }

    /**
     * Reads an int member, tolerating absence and wrong types.
     *
     * @param root         the object to read from.
     * @param member       the member name.
     * @param fallback     what to return when the member is absent or not a number.
     * @return the value, or the fallback.
     */
    private static int getInt(final JsonObject root, final String member, final int fallback)
    {
        final JsonElement value = root.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        {
            return fallback;
        }
        return value.getAsInt();
    }
}
