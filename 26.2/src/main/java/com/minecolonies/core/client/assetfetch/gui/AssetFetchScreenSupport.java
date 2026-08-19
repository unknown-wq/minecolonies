package com.minecolonies.core.client.assetfetch.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.Locale;

/**
 * Small shared helpers for the asset-fetch screens: number formatting and the one place that changes screens.
 *
 * <p>These screens are deliberately built out of <b>vanilla widgets only</b>. BlockUI is exactly what cannot
 * be used here — with the fetched assets absent, loading any {@code minecolonies:gui/*.xml} throws out of
 * {@code Loader.createFromXMLFile}, which is the crash these screens exist to replace.</p>
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
     * Formats a byte count the way a download dialog should: megabytes with one decimal.
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
     * Formats an exact byte count with thousands separators, for the consent screen's fine print.
     *
     * @param bytes the count.
     * @return e.g. {@code 78,071,143}.
     */
    public static String exactBytes(final long bytes)
    {
        return String.format(Locale.ROOT, "%,d", bytes);
    }

    /**
     * Formats a plain count with thousands separators.
     *
     * @param count the count.
     * @return e.g. {@code 8,474}.
     */
    public static String count(final int count)
    {
        return String.format(Locale.ROOT, "%,d", count);
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
