package com.ldtteam.common.language;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.blockui.mod.Log;
import net.minecraft.locale.Language;
import org.apache.commons.io.IOUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for localization and sending player messages.
 * Note that MineColonies is still using some of these, so it's not safe to delete yet.
 */
public final class LanguageHandler
{
    /**
     * Locale every consumer is expected to ship, used whenever the current one has no file of its own.
     */
    private static final String DEFAULT_LOCALE = "en_us";

    /**
     * Private constructor to hide implicit one.
     */
    private LanguageHandler()
    {
        // Intentionally left empty.
    }

    /**
     * Translates key to readable string.
     *
     * @param key translation key
     * @return readable string
     */
    public static String translateKey(final String key)
    {
        return LanguageCache.getInstance().translateKey(key);
    }

    /**
     * Sets our cache to use mc default one.
     */
    public static void setMClanguageLoaded()
    {
        LanguageCache.getInstance().isMCloaded = true;
        LanguageCache.getInstance().languageMap = null;
    }

    public static void loadLangPath(final String path)
    {
        LanguageCache.getInstance().load(path);
    }

    /**
     * Looks up the pre-load language file of one consumer, falling back to {@link #DEFAULT_LOCALE} the way this
     * cache always has.
     * <p>
     * Package-private for {@code LanguageFileLookupTest}: it touches neither {@link LanguageCache} nor the game,
     * so it can be exercised without a running client or loader.
     *
     * @param path   classpath pattern holding a single {@code %s} for the locale
     * @param locale locale to try first, may be null when it cannot be known yet
     * @return stream to read the file from, or null when neither the locale nor the default has one - which is a
     *         legitimate state for a consumer whose translations live outside source control, not an error
     */
    static InputStream openLangFile(final String path, final String locale)
    {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final InputStream is = locale == null ? null : classLoader.getResourceAsStream(String.format(path, locale));

        return is != null ? is : classLoader.getResourceAsStream(String.format(path, DEFAULT_LOCALE));
    }

    private static class LanguageCache
    {
        private static final LanguageCache instance = new LanguageCache();
        private boolean isMCloaded = false;
        private Map<String, String> languageMap = new ConcurrentHashMap<>();

        private LanguageCache()
        {
            load("assets/blockui/lang/%s.json");
        }

        private void load(final String path)
        {
            if (isMCloaded)
            {
                // The vanilla language is up and setMClanguageLoaded has dropped the pre-load map, so there is
                // nothing left to fill - translateKey serves every key from Language from here on.
                return;
            }

            // contract K6: FMLEnvironment.getDist().isClient() -> BlockUI.isClient()
            final String locale = BlockUI.isClient() ? ClientLocale.getLocale() : ServerLocale.getLocale();

            final InputStream is = openLangFile(path, locale);
            if (is == null)
            {
                // Not every consumer ships a pre-load language file: MineColonies' en_us.json is produced by
                // their translation pipeline instead of being kept in git, so in a dev or datagen run neither
                // the requested locale nor the default one exists. Reading the missing stream used to take mod
                // init down with it; an empty cache is the correct outcome instead, because translateKey falls
                // through to Language for every key it does not hold. Logged once per load, never per lookup.
                Log.getLogger()
                    .warn("No pre-load language file for '{}' (neither locale '{}' nor '{}'), its keys stay untranslated"
                        + " until the resources are loaded.", path, locale, DEFAULT_LOCALE);
                return;
            }

            languageMap.putAll(new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>()
            {}.getType()));

            IOUtils.closeQuietly(is);
        }

        private static LanguageCache getInstance()
        {
            return instance;
        }

        private String translateKey(final String key)
        {
            if (isMCloaded)
            {
                return Language.getInstance().getOrDefault(key);
            }
            else
            {
                final String res = languageMap.get(key);
                return res == null ? Language.getInstance().getOrDefault(key) : res;
            }
        }
    }
}
