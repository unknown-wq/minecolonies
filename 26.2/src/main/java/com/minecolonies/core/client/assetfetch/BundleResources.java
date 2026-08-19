package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the install bundle is read from: {@code transforms.json}, the patches under {@code patches/} and
 * {@code manifest.json}.
 *
 * <p>In the game that is the mod jar's {@code /assetfetch/} directory. In a headless test it is an ordinary
 * directory. Keeping it an interface is what lets the whole patch and verify pipeline run outside Minecraft,
 * over nothing but {@link Path} arguments.</p>
 *
 * <p>Paths are relative to the bundle root and always use {@code /}, matching the way
 * {@code transforms.json} spells them ({@code patches/models/block/blockhutcook.json.jsonpatch}).</p>
 */
@FunctionalInterface
public interface BundleResources
{
    /**
     * Reads one bundle resource.
     *
     * @param relativePath the path within the bundle, {@code /}-separated.
     * @return its bytes.
     * @throws IOException if it is missing or unreadable.
     */
    byte[] read(String relativePath) throws IOException;

    /**
     * The bundle as it ships: {@code /assetfetch/} inside the mod jar.
     *
     * @return a resource reader over the mod jar.
     */
    static BundleResources ofModJar()
    {
        return relativePath ->
        {
            final String resource = "/assetfetch/" + relativePath;
            try (InputStream in = BundleResources.class.getResourceAsStream(resource))
            {
                if (in == null)
                {
                    throw new IOException("Missing bundle resource " + resource + " in the mod jar");
                }
                return in.readAllBytes();
            }
        };
    }

    /**
     * The bundle as an unpacked directory, for headless tests and for the generator tooling.
     *
     * @param root the directory holding {@code transforms.json}.
     * @return a resource reader over that directory.
     */
    static BundleResources ofDirectory(final Path root)
    {
        return relativePath -> Files.readAllBytes(root.resolve(relativePath));
    }
}
