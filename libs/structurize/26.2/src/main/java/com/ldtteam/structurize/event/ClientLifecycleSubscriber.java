package com.ldtteam.structurize.event;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.blockentities.ModBlockEntities;
import com.ldtteam.structurize.client.ClientItemStackTooltip;
import com.ldtteam.structurize.client.TagSubstitutionRenderer;
import com.ldtteam.common.language.LanguageHandler;
import com.ldtteam.structurize.items.ItemStackTooltip;
import com.ldtteam.structurize.storage.ClientStructurePackLoader;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Client side lifecycle hooks.
 *
 * <p>Port note: NeoForge spread this over eight mod bus events. Fabric has no mod bus, so
 * {@link #register()} is called from {@link com.ldtteam.structurize.StructurizeClient} and installs
 * everything by hand:</p>
 *
 * <ul>
 * <li>{@code FMLClientSetupEvent} → direct call, client init is already the right moment.</li>
 * <li>{@code FMLLoadCompleteEvent} (moved here from {@code LifecycleSubscriber}, it was client only) →
 * direct call to {@code LanguageHandler.setMClanguageLoaded()}.</li>
 * <li>{@code RegisterClientReloadListenersEvent} → {@code ResourceManagerHelper}, which needs an
 * {@code Identifier} for the listener — NeoForge listeners were anonymous.</li>
 * <li>{@code RegisterClientTooltipComponentFactoriesEvent} → {@code ClientTooltipComponentCallback},
 * which is a filter chain rather than a class-keyed map, so the {@code instanceof} is explicit.</li>
 * </ul>
 *
 * <p>The five remaining hooks were all render side and are disabled below — see the TODO markers.</p>
 */
public class ClientLifecycleSubscriber
{
    /**
     * Private constructor to hide implicit public one.
     */
    private ClientLifecycleSubscriber()
    {
        /*
         * Intentionally left empty
         */
    }

    /**
     * Installs every client lifecycle callback. Called once from the client initializer.
     */
    public static void register()
    {
        LanguageHandler.setMClanguageLoaded();

        ClientStructurePackLoader.onClientLoading();

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener()
        {
            @Override
            public Identifier getFabricId()
            {
                return Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blueprint_renderer_cache");
            }

            @Override
            public void onResourceManagerReload(final ResourceManager manager)
            {
                Log.getLogger().debug("Clearing blueprint renderer cache.");
                BlueprintHandler.getInstance().clearCache();
            }
        });

        ClientTooltipComponentCallback.EVENT.register(
            component -> component instanceof final ItemStackTooltip itemStackTooltip ? new ClientItemStackTooltip(itemStackTooltip) : null);

        // TODO(port-26.2): DISABLED — ItemBlockRenderTypes was removed in 26.2, the render layer now comes
        // from "render_type" in the block model JSON. Fix belongs in
        // assets/structurize/models/block/blocksubstitution.json: add "render_type": "translucent".
        /*
         * ItemBlockRenderTypes.setRenderLayer(ModBlocks.blockSubstitution.get(), RenderType.translucent());
         */

        // TODO(port-26.2): DISABLED — CLOSED FOR GOOD. IGeometryLoader / IUnbakedGeometry / BakedModel are all
        // gone from 26.2, so OverlaidModelLoader has nothing left to implement and nothing to register into.
        /*
         * event.register(Constants.resLocStruct("overlaid"), new OverlaidModelLoader());
         */

        // The in-world half of the tag anchor renderer is back: TagSubstitutionRenderer now implements the
        // 26.2 render-state BlockEntityRenderer<T, S>. Its item half stays cut, see the renderer's own markers.
        BlockEntityRendererRegistry.register(ModBlockEntities.TAG_SUBSTITUTION.get(), TagSubstitutionRenderer::new);

        // TODO(port-26.2): DISABLED — CLOSED FOR GOOD. RegisterRenderBuffersEvent is NeoForge only and 26.2
        // has no mod owned render buffers at all: batching is done by SubmitNodeCollection. Nothing to port.
        /*
         * WorldRenderMacros.RenderTypes.registerBuffer(event);
         */

        // TODO(port-26.2): DISABLED — IClientItemExtensions#getCustomRenderer is NeoForge only and
        // BlockEntityWithoutLevelRenderer no longer exists in 26.2 (render agent).
        /*
         * event.registerItem(new IClientItemExtensions()
         * {
         *     @Override
         *     public BlockEntityWithoutLevelRenderer getCustomRenderer()
         *     {
         *         return TagSubstitutionRenderer.getInstance();
         *     }
         * }, ModItems.blockTagSubstitution.get());
         */
    }
}
