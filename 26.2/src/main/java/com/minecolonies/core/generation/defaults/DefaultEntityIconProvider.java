package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.util.Log;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;
import static net.minecraft.client.gui.components.PlayerFaceExtractor.*;

/**
 * Datagen for entity_icon.
 *
 * <p><b>Deliberately not registered</b> in {@link com.minecolonies.core.generation.MineColoniesDataGenerator}. It
 * walked {@code assets/minecolonies/textures/entity/{citizen,raiders}/**} and cut a 16x16 face out of every skin,
 * 3481 icons in all. Those icons are a crop of upstream's all-rights-reserved skin textures, so they are exactly as
 * derivative as the skins; this repository carries neither. The runtime asset fetch downloads the upstream jar,
 * which already contains all 3481 of them, and injects it as a resource pack
 * (see {@code docs/assetfetch/BRIEF.md}). With the skins absent {@link #run} would simply find no root to walk and
 * write nothing, so leaving it registered would be harmless but misleading — it is the class, not the run, that is
 * retired. The code is kept because it is the only record of how the icons are derived.</p>
 *
 * <p>Port notes (26.2 / Fabric):</p>
 * <ul>
 *   <li>NeoForge's {@code ResourcePackLoader.createPackForMod(ModList.get().getModFileById(…))} is gone. Fabric
 *       exposes the mod's own resource roots directly through {@code ModContainer#getRootPaths()}, which
 *       {@link FabricPackOutput#getModContainer()} hands us; the skins are walked off disk instead of through a
 *       {@code PackResources}.</li>
 *   <li>{@code net.minecraft.Util} moved to {@code net.minecraft.util.Util}, and
 *       {@code PlayerFaceRenderer} was renamed to {@code PlayerFaceExtractor} (same {@code SKIN_HEAD_*}
 *       constants).</li>
 *   <li>{@code NativeImage#blendPixel} and {@code NativeImage#asByteArray} were removed. The border is now blended
 *       while copying into the {@link BufferedImage}: the old call blended {@code 0x80000000} (50 % black) over an
 *       opaque pixel, which on the 24-bit output is exactly "halve every channel". {@code resizeSubRectTo} — the
 *       part that actually decides the pixels — is untouched, so the icons stay byte-identical to 1.21.1.</li>
 * </ul>
 */
public class DefaultEntityIconProvider implements DataProvider
{
    private final FabricPackOutput packOutput;

    public DefaultEntityIconProvider(@NotNull final FabricPackOutput packOutput)
    {
        this.packOutput = packOutput;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Default Citizen Icons";
    }

    private static boolean isEntitySkin(@NotNull final String path)
    {
        return path.endsWith(".png") &&
                (path.startsWith("textures/entity/citizen/") || path.startsWith("textures/entity/raiders/"));
    }

    @NotNull
    @Override
    public CompletableFuture<?> run(@NotNull final CachedOutput cache)
    {
        final PackOutput.PathProvider outputProvider =
          packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures/entity_icon");

        final List<CompletableFuture<?>> icons = new ArrayList<>();
        final ModContainer container = packOutput.getModContainer();

        for (final Path root : container.getRootPaths())
        {
            final Path assets = root.resolve("assets").resolve(MOD_ID);
            if (!Files.isDirectory(assets))
            {
                continue;
            }

            try (final Stream<Path> files = Files.walk(assets))
            {
                for (final Path file : files.filter(Files::isRegularFile).toList())
                {
                    final String relative = assets.relativize(file).toString().replace('\\', '/');
                    if (!isEntitySkin(relative))
                    {
                        continue;
                    }

                    final Identifier iconId = Identifier.fromNamespaceAndPath(MOD_ID,
                            relative.replace("textures/entity/", "").replace(".png", ""));
                    icons.add(generateIcon(outputProvider, iconId, file, cache));
                }
            }
            catch (final IOException e)
            {
                Log.getLogger().error("Failed to enumerate citizen skins under {}", assets, e);
            }
        }

        return CompletableFuture.allOf(icons.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<?> generateIcon(@NotNull final PackOutput.PathProvider outputProvider,
                                              @NotNull final Identifier id,
                                              @NotNull final Path skinFile,
                                              @NotNull final CachedOutput cache)
    {
        return CompletableFuture.runAsync(() ->
        {
            try (final InputStream input = Files.newInputStream(skinFile))
            {
                try (final NativeImage skin = NativeImage.read(input))
                {
                    try (final NativeImage icon = createIconForSkin(skin))
                    {
                        saveIcon(outputProvider, id, icon, cache);
                    }
                }
            }
            catch (final IOException e)
            {
                Log.getLogger().error("Failed to save file to {}", id, e);
            }

        }, Util.backgroundExecutor());
    }

    private static NativeImage createIconForSkin(@NotNull final NativeImage skin)
    {
        final NativeImage icon = new NativeImage(16, 16, false);

        skin.resizeSubRectTo(SKIN_HEAD_U, SKIN_HEAD_V, SKIN_HEAD_WIDTH, SKIN_HEAD_HEIGHT, icon);

        return icon;
    }

    /**
     * @param x the icon x coordinate.
     * @param y the icon y coordinate.
     * @return true when this pixel is on the one-pixel darkened border the 1.21.1 provider blended in.
     */
    private static boolean isBorder(final int x, final int y)
    {
        return x == 0 || x == 15 || y == 0 || y == 15;
    }

    private static void saveIcon(@NotNull final PackOutput.PathProvider outputProvider,
                                 @NotNull final Identifier id,
                                 @NotNull final NativeImage icon,
                                 @NotNull final CachedOutput cache) throws IOException
    {
        // convert to 24-bit, to reduce file size a bit
        final BufferedImage optimized = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < icon.getHeight(); ++y)
        {
            for (int x = 0; x < icon.getWidth(); ++x)
            {
                final int argb = icon.getPixel(x, y);
                int r = ARGB.red(argb);
                int g = ARGB.green(argb);
                int b = ARGB.blue(argb);
                if (isBorder(x, y))
                {
                    // was: icon.blendPixel(x, y, 0x80000000) -- 50% black over an opaque pixel
                    r >>= 1;
                    g >>= 1;
                    b >>= 1;
                }
                optimized.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final HashingOutputStream hashStream = new HashingOutputStream(Hashing.sha1(), outputStream);
        ImageIO.write(optimized, "PNG", hashStream);

        cache.writeIfNeeded(outputProvider.file(id, "png"), outputStream.toByteArray(), hashStream.hash());
    }
}
