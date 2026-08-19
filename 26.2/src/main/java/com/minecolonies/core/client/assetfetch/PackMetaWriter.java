package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the installed pack's {@code pack.mcmeta} (task C8).
 *
 * <p>It is written at install time rather than shipped, because its content depends on the running game:
 * only the client that is installing the pack knows which pack format it speaks. That is also why
 * {@code pack.mcmeta} is the one file excluded from {@code manifest.json} — there is no fixed hash for it.</p>
 *
 * <p><b>The 26.2 shape, checked against the game's own sources rather than assumed.</b>
 * {@code SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES)} no longer returns an
 * {@code int}; since the minor-version rework it returns a {@code PackFormat(major, minor)} record
 * ({@code net/minecraft/WorldVersion.java}), and 26.2's client resource format is {@code 88.0}
 * ({@code DetectedVersion.createBuiltIn}). {@code PackFormat.lastPreMinorVersion(CLIENT_RESOURCES)} is 64,
 * and {@code PackFormat.IntermediaryFormat.validate} <em>rejects</em> a pack that declares a format above 64
 * with the old {@code supported_formats} key, and rejects one that declares {@code min_format}/
 * {@code max_format} without them below it. So for 26.2 the correct — and only accepted — spelling is
 * {@code min_format} plus {@code max_format}, with no {@code pack_format} and no {@code supported_formats}.</p>
 *
 * <p>Both bounds are written as a bare major number. Read back through {@code PackFormat.BOTTOM_CODEC} that
 * makes {@code min_format} mean {@code major.0} and through {@code TOP_CODEC} it makes {@code max_format}
 * mean {@code major.*}, so the pack stays compatible across every minor revision of the format the game
 * ships with.</p>
 */
public final class PackMetaWriter
{
    /**
     * Name of the file in the pack root.
     */
    public static final String FILE_NAME = "pack.mcmeta";

    /**
     * Shown in the resource pack screen. A literal string, because the language files that could translate it
     * live inside the pack this file describes.
     */
    private static final String DESCRIPTION = "MineColonies assets, downloaded from LDTTeam's official build";

    /**
     * Private constructor to hide the public one.
     */
    private PackMetaWriter()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Writes {@code pack.mcmeta} into a pack root.
     *
     * @param packRoot    the pack directory.
     * @param majorFormat the running game's client resource pack format, major component. Injected rather
     *                    than read from {@code SharedConstants} so the pipeline stays headless-testable.
     * @throws AssetInstallException if the file cannot be written.
     */
    public static void write(final Path packRoot, final int majorFormat) throws AssetInstallException
    {
        final String content = """
            {
              "pack": {
                "description": "%s",
                "min_format": %d,
                "max_format": %d
              }
            }
            """.formatted(DESCRIPTION, majorFormat, majorFormat);
        try
        {
            Files.createDirectories(packRoot);
            Files.write(packRoot.resolve(FILE_NAME), content.getBytes(StandardCharsets.UTF_8));
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not write " + FILE_NAME + ": " + e.getMessage(), e);
        }
    }
}
