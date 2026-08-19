package com.minecolonies.core.client.assetfetch.gui;

import com.minecolonies.core.client.assetfetch.SourceChain;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Small shared helpers for the asset-fetch screens: number formatting, the human name of a source, and the
 * one place that changes screens.
 *
 * <p>These screens are deliberately built out of <b>vanilla widgets only</b>. BlockUI is exactly what cannot
 * be used here — with the fetched assets absent, loading any {@code minecolonies:gui/*.xml} throws out of
 * {@code Loader.createFromXMLFile}, which is the crash these screens exist to replace.</p>
 *
 * <p><b>Numbers follow the game's language.</b> A Russian player reads {@code 78,6} and {@code 8 474}, not
 * {@code 78.6} and {@code 8,474}, so the formatters below take their separators from the selected language
 * rather than from {@link Locale#ROOT}. The <i>unit</i> never comes from here: {@code MB} / {@code МБ} lives
 * in the translated string, so each language says it once, in its own alphabet.</p>
 */
@Environment(EnvType.CLIENT)
public final class AssetFetchScreenSupport
{
    /**
     * Private constructor to hide the public one.
     */
    private AssetFetchScreenSupport()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Formats a byte count as a bare number of megabytes, for a string that says {@code MB} itself.
     *
     * @param bytes the count; negative means "not known".
     * @return e.g. {@code 74.5}, {@code 74,5} in a comma-decimal language, or {@code ?} when unknown.
     */
    public static String megabytesNumber(final long bytes)
    {
        if (bytes < 0)
        {
            return "?";
        }
        return format("%.1f", bytes / 1_048_576.0D);
    }

    /**
     * Formats a byte count with the unit attached, for the diagnostic lines on the failure screen.
     *
     * @param bytes the count; negative means "not known".
     * @return e.g. {@code 74.5 MB}, or {@code ?} when the count is unknown.
     */
    public static String megabytes(final long bytes)
    {
        if (bytes < 0)
        {
            return "?";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0D);
    }

    /**
     * Formats a plain count with the selected language's thousands separator.
     *
     * @param count the count.
     * @return e.g. {@code 8,474}, or {@code 8 474} in a space-grouping language.
     */
    public static String count(final int count)
    {
        return format("%,d", count);
    }

    /**
     * Names the source being fetched from the way a player would name it — no URL.
     *
     * <p>The full URL is diagnostic, not conversational: it is long enough to be clipped mid-string on the
     * progress screen, and reads like a log line when it is not. It stays in two places that want it — the
     * per-source detail lines on the failure screen, and {@code Trying MineColonies asset source ... at ...}
     * in the log — and is kept off the happy path.</p>
     *
     * @param sourceId the chain entry's id, e.g. {@code maven-1374}.
     * @param url      the URL or local path it is fetching.
     * @return the line to show.
     */
    public static Component sourceLine(final String sourceId, final String url)
    {
        if (SourceChain.LOCAL_JAR_ID.equals(sourceId))
        {
            return Component.translatable(AssetFetchLang.PROGRESS_SOURCE_FILE, fileName(url));
        }
        if (SourceChain.MAVEN_1374.id().equals(sourceId))
        {
            return Component.translatable(AssetFetchLang.PROGRESS_SOURCE_OFFICIAL, mavenVersion(url, sourceId));
        }
        if (SourceChain.MAVEN_1368.id().equals(sourceId))
        {
            return Component.translatable(AssetFetchLang.PROGRESS_SOURCE_BACKUP, mavenVersion(url, sourceId));
        }
        return Component.translatable(AssetFetchLang.PROGRESS_SOURCE_HOST, host(url, sourceId));
    }

    /**
     * The upstream version a Maven URL points at.
     *
     * <p>Read out of the artifact's version directory — {@code .../minecolonies/1.1.1374-1.21.1-snapshot/...}
     * — with the Minecraft-version suffix cut off, so it says {@code 1.1.1374} without this class keeping a
     * second copy of a number {@link SourceChain} already owns.</p>
     *
     * @param url      the artifact URL.
     * @param fallback what to say when the URL is not shaped like a Maven path.
     * @return the version, e.g. {@code 1.1.1374}.
     */
    private static String mavenVersion(final String url, final String fallback)
    {
        try
        {
            final Path path = Path.of(URI.create(url).getPath());
            final Path versionDir = path.getParent();
            if (versionDir == null || versionDir.getFileName() == null)
            {
                return fallback;
            }
            final String version = versionDir.getFileName().toString();
            final int cut = version.indexOf('-');
            return cut > 0 ? version.substring(0, cut) : version;
        }
        catch (final RuntimeException e)
        {
            return fallback;
        }
    }

    /**
     * The host part of a URL, for a source this build knows nothing else about.
     *
     * @param url      the URL.
     * @param fallback what to say when it does not parse.
     * @return the host, e.g. {@code assets.example.org}.
     */
    private static String host(final String url, final String fallback)
    {
        try
        {
            final String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? fallback : host;
        }
        catch (final RuntimeException e)
        {
            return fallback;
        }
    }

    /**
     * The file name part of a local path, for the jar the player picked themselves.
     *
     * @param path the absolute path.
     * @return just the file name.
     */
    private static String fileName(final String path)
    {
        try
        {
            final Path name = Path.of(path).getFileName();
            return name == null ? path : name.toString();
        }
        catch (final RuntimeException e)
        {
            return path;
        }
    }

    /**
     * Formats a number in the selected language, with any non-breaking group separator turned into a plain
     * space — the vanilla font has no glyph for U+00A0, and a box in the middle of a file count looks broken.
     *
     * @param pattern the format pattern.
     * @param value   the value.
     * @return the formatted number.
     */
    private static String format(final String pattern, final Object value)
    {
        return String.format(numberLocale(), pattern, value)
            .replace('\u00A0', ' ')
            .replace('\u202F', ' ');
    }

    /**
     * The locale whose number separators match the language the player is reading.
     *
     * @return the locale, or {@link Locale#ROOT} when the game cannot say.
     */
    private static Locale numberLocale()
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null)
        {
            return Locale.ROOT;
        }
        final LanguageManager languages = mc.getLanguageManager();
        if (languages == null)
        {
            return Locale.ROOT;
        }
        final String code = languages.getSelected();
        if (code == null || code.isBlank())
        {
            return Locale.ROOT;
        }
        final int split = code.indexOf('_');
        return split < 0
            ? Locale.of(code)
            : Locale.of(code.substring(0, split), code.substring(split + 1).toUpperCase(Locale.ROOT));
    }

    /**
     * Switches screens on the client thread, whatever thread the caller is on.
     *
     * <p>The installer's callbacks arrive on its own thread, so every result screen goes through here.</p>
     *
     * @param screen the screen to show, or null to return to the game.
     */
    public static void setScreen(final Screen screen)
    {
        final Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(screen));
    }
}
