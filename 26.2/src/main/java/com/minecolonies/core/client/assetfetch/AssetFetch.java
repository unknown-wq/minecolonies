package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.minecolonies.api.util.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

public final class AssetFetch
{
    private static final String CACHE_DIR_NAME = "fetched-assets";

    private static final String PACK_DIR_NAME = "pack";

    private static final String PACK_META_NAME = "pack.mcmeta";

    private static final String STATE_FILE_NAME = "state.json";

    private static final String TEMP_DIR_NAME = "tmp";

    private static final int SCHEMA_VERSION = 1;

    private static final String STATUS_INSTALLED = "installed";

    private static volatile Boolean readyCache = null;

    private static volatile Boolean staleCache = null;

    private AssetFetch()
    {
    }

    public static Path baseDir()
    {
        return FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve(CACHE_DIR_NAME);
    }

    public static Path packDir()
    {
        return baseDir().resolve(PACK_DIR_NAME);
    }

    public static Path stateFile()
    {
        return baseDir().resolve(STATE_FILE_NAME);
    }

    private static Path tempDir()
    {
        return baseDir().resolve(TEMP_DIR_NAME);
    }

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

    public static boolean isStale()
    {
        Boolean cached = staleCache;
        if (cached == null)
        {
            cached = computeStale();
            staleCache = cached;
        }
        return cached;
    }

    public static void invalidate()
    {
        readyCache = null;
        staleCache = null;
    }

    private static boolean computeReady()
    {
        InstallPipeline.recoverInterruptedSwap(packDir(), tempDir(), stateFile());

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

    private static boolean computeStale()
    {
        if (!isReady())
        {
            return false;
        }

        final String shipped;
        try
        {
            shipped = AssetManifest.shippedSha256(BundleResources.ofModJar());
        }
        catch (final IOException | RuntimeException e)
        {
            Log.getLogger().error("Could not read this build's asset manifest, so the installed assets cannot be"
                + " checked against it -- leaving them alone", e);
            return false;
        }

        if (InstallState.read(stateFile()).matchesManifest(shipped))
        {
            return false;
        }

        Log.getLogger().warn("The installed MineColonies assets were installed against a different manifest than this"
            + " build ships -- they are from an earlier version and will be offered for reinstall");
        return true;
    }

    private static boolean hasUsablePackMeta(final Path meta)
    {
        final JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(meta, StandardCharsets.UTF_8))
        {
            final JsonReader strict = new JsonReader(reader);
            strict.setStrictness(Strictness.STRICT);
            final JsonElement parsed = JsonParser.parseReader(strict);
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

    private static String getString(final JsonObject root, final String member)
    {
        final JsonElement value = root.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        {
            return null;
        }
        return value.getAsString();
    }

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
