package com.minecolonies.core.event;

import com.minecolonies.api.util.constant.Constants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Specific texture reload listener.
 * <p>
 * <b>Port note (contract C5).</b> {@code RegisterClientReloadListenersEvent} became
 * {@link ResourceLoader#registerReloadListener}, which wants an explicit id.
 * <p>
 * BlockUI's {@code com.ldtteam.blockui.AtlasManager} is gone for good -- vanilla took the feature over in
 * 26.2. The mod's GUI atlas is therefore declared straight from
 * {@code MineColoniesClient#onInitializeClient()} (it must happen there, before the atlas configs are
 * finalized), not from this reload listener: the vanilla {@code AtlasManager} is a reload listener itself.
 */
@Environment(EnvType.CLIENT)
public class TextureReloadListener extends SimplePreparableReloadListener<TextureReloadListener.TexturePacks>
{
    /**
     * List of all texture packs available.
     */
    public static final List<String> TEXTURE_PACKS = new ArrayList<>();

    /**
     * Id of this reload listener.
     */
    private static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "citizen_textures");

    @NotNull
    @Override
    protected TexturePacks prepare(@NotNull final ResourceManager manager, @NotNull final ProfilerFiller profiler)
    {
        final Set<String> set = new HashSet<>();
        final List<Identifier> resLocs = new ArrayList<>(manager.listResources("textures/entity/citizen", f -> true).keySet());
        for (final Identifier res : resLocs)
        {
            if (res.getPath().contains("png") && res.getPath().contains("textures/entity/citizen"))
            {
                final String folder = res.getPath().split("/")[3];
                if (!folder.isEmpty())
                {
                    set.add(folder);
                }
            }
        }

        final TexturePacks packs = new TexturePacks();
        packs.packs = new ArrayList<>(set);
        return packs;
    }

    @Override
    protected void apply(@NotNull final TexturePacks packs, @NotNull final ResourceManager manager, @NotNull final ProfilerFiller profiler)
    {
       TextureReloadListener.TEXTURE_PACKS.clear();
       TextureReloadListener.TEXTURE_PACKS.addAll(packs.packs);
    }

    /**
     * Storage class to hand the texture packs from off-thread to the main thread.
     */
    public static class TexturePacks
    {
        public List<String> packs = new ArrayList<>();
    }

    /**
     * Installs the listener. Called once from the client initializer.
     */
    public static void register()
    {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(LISTENER_ID, new TextureReloadListener());
    }
}
