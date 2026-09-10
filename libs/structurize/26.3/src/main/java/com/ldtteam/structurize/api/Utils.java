package com.ldtteam.structurize.api;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.Normalizer;

/**
 * General purpose utilities class.
 */
public final class Utils
{
    /**
     * Private constructor to hide the implicit public one.
     */
    private Utils()
    {
    }

    /**
     * Play a success sound.
     * @param player the player to play it for.
     */
    public static void playSuccessSound(@NotNull final Player player)
    {
        // 26.2: Player#playNotifySound(SoundEvent, SoundSource, float, float) is gone (0 hits in /opt/mc-src);
        // the remaining public API is Player#playSound(SoundEvent, float, float)
        // (/opt/mc-src/net/minecraft/world/entity/player/Player.java:397)
        player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
    }

    /**
     * Play an error sound.
     * @param player the player to play it for.
     */
    public static void playErrorSound(@NotNull final Player player)
    {
        player.playSound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.0f, 0.3f);
    }

    /**
     * Checks if directory exists, else creates it.
     *
     * @param directory the directory to check.
     */
    public static void checkDirectory(final File directory)
    {
        if (!directory.exists() && !directory.mkdirs())
        {
            Log.getLogger().error("Directory doesn't exist and failed to be created: " + directory.toString());
        }
    }

    /**
     * Get a filename that's probably safe from a player name that might contain problematic characters.
     * @param input a player name or other possibly unsafe text.
     * @return the safe filename.
     *
     * This doesn't protect against Windows reserved filenames. Most servers are Linux anyway
     * so this only hurts SP players who will have a lot of Windows things break on them too.
     */
    public static String getSafePackName(String input)
    {
        String s = Normalizer.normalize(input, Normalizer.Form.NFC);
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        s = s.replaceAll("\\p{Cntrl}", "");
        s = s.trim();
        return s;
    }
}
