package com.ldtteam.common.language;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the resource lookup behind {@code LanguageHandler.loadLangPath}, whose missing null check took mod init
 * down for every dependent mod that ships no pre-load language file of its own.
 * <p>
 * Only {@link LanguageHandler#openLangFile} is called, never the cache around it: touching {@code LanguageCache}
 * would run its constructor and therefore {@code BlockUI#isClient()}, i.e. {@code FabricLoader}, which does not
 * exist outside a running game. The lookup itself needs nothing but the classpath.
 */
class LanguageFileLookupTest
{
    private static final String BLOCKUI_LANG = "assets/blockui/lang/%s.json";
    private static final String ABSENT_LANG = "assets/no_such_mod/lang/%s.json";

    /**
     * A consumer that keeps its translations outside source control - MineColonies' {@code en_us.json} comes from
     * their translation pipeline - has a file for neither locale. That has to be a null, not a crash.
     */
    @Test
    void reportsMissingFileInsteadOfThrowing()
    {
        assertNull(LanguageHandler.openLangFile(ABSENT_LANG, "en_us"));
        assertNull(LanguageHandler.openLangFile(ABSENT_LANG, "ru_ru"));
        assertNull(LanguageHandler.openLangFile(ABSENT_LANG, null));
    }

    @Test
    void findsTheShippedLocale() throws IOException
    {
        assertFound(LanguageHandler.openLangFile(BLOCKUI_LANG, "ru_ru"));
    }

    @Test
    void fallsBackToTheDefaultLocale() throws IOException
    {
        assertFound(LanguageHandler.openLangFile(BLOCKUI_LANG, "zz_zz"));
    }

    /**
     * The locale is unknown while the client is still constructing itself, which is exactly when mod entrypoints
     * run - the null it returns then has to land on the default file rather than on a bogus {@code null.json}.
     */
    @Test
    void fallsBackWhenTheLocaleIsNotKnownYet() throws IOException
    {
        assertFound(LanguageHandler.openLangFile(BLOCKUI_LANG, null));
    }

    private static void assertFound(final InputStream is) throws IOException
    {
        assertNotNull(is);
        is.close();
    }
}
