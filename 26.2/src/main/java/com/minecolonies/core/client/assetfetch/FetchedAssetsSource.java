package com.minecolonies.core.client.assetfetch;

import com.minecolonies.api.util.Log;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Offers the runtime-fetched upstream assets to the client's resource pack repository.
 *
 * <p>One instance is appended to the client {@code PackRepository}'s source set by
 * {@code com.minecolonies.core.mixin.PackRepositoryMixin}. Fabric API's public
 * {@code ResourceLoader.registerBuiltinPack} cannot be used for this: it only serves packs that live
 * inside the mod jar, and the whole point here is a directory outside it.</p>
 *
 * <p>Two properties of the offered pack are load-bearing:</p>
 * <ul>
 *     <li>{@code required = true} — {@code PackRepository.rebuildSelected} force-selects every available
 *         required pack on each {@code reload()}, so the player cannot switch it off in the pack screen
 *         and get a half-textured game.</li>
 *     <li>{@code Position.BOTTOM} with {@code fixedPosition = true} — it sits just above vanilla and below
 *         every other pack, so anything the mod jar itself ships (port-authored files, the two restored
 *         {@code assets/minecraft} files, the port's own language entries) wins over the downloaded copy.</li>
 * </ul>
 *
 * <p>{@link AssetFetch#isReady()} is consulted inside {@link #loadPacks(Consumer)} rather than once at
 * construction, because the repository outlives an install: after the installer finishes it calls
 * {@link AssetFetch#invalidate()} and {@code Minecraft.reloadResourcePacks()}, which re-runs discovery
 * against this same instance. Before consent it offers nothing at all, so a fresh game is untouched.</p>
 */
@Environment(EnvType.CLIENT)
public final class FetchedAssetsSource implements RepositorySource
{
    /**
     * Id of the injected pack, as it appears in {@code Reloading ResourceManager} log lines and in
     * {@code options.txt}.
     */
    public static final String PACK_ID = "minecolonies:fetched_assets";

    /**
     * Required, un-droppable, pinned to the bottom of the stack. See the class javadoc for why.
     */
    private static final PackSelectionConfig SELECTION_CONFIG = new PackSelectionConfig(true, Pack.Position.BOTTOM, true);

    /**
     * Title shown in the resource pack screen. Deliberately a literal: it has to render before the pack it
     * names is loaded, and the language files live inside that pack.
     */
    private static final Component TITLE = Component.literal("MineColonies Assets (downloaded)");

    @Override
    public void loadPacks(final Consumer<Pack> result)
    {
        if (!AssetFetch.isReady())
        {
            return;
        }

        final Path root = AssetFetch.packDir();
        final PackLocationInfo location = new PackLocationInfo(PACK_ID, TITLE, PackSource.BUILT_IN, Optional.empty());
        final Pack pack = Pack.readMetaAndCreate(location, new PathPackResources.PathResourcesSupplier(root), PackType.CLIENT_RESOURCES, SELECTION_CONFIG);

        if (pack == null)
        {
            // readMetaAndCreate has already logged the reason. Do not throw: a damaged cache must degrade to
            // "assets missing", which the rest of the mod already handles, not to a crash on resource reload.
            // AssetFetch.isReady() judges pack.mcmeta by the same standard readMetaAndCreate does, so getting
            // here means the metadata was damaged between that check and this one.
            Log.getLogger().error("Downloaded MineColonies assets at {} could not be read as a resource pack."
                + " Their pack.mcmeta is damaged; the download will be offered for install again.", root);
            return;
        }

        result.accept(pack);
    }
}
