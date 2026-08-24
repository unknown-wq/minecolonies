package com.ldtteam.blockui.mod;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Loader;
import com.ldtteam.blockui.UiRenderMacros;
import com.ldtteam.blockui.hooks.HookManager;
import com.ldtteam.blockui.mod.item.BlockStatePipRenderer;
import com.ldtteam.common.network.ModNetworking;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.AtlasRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.model.sprite.AtlasManager.AtlasConfig;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

import java.util.Set;

/**
 * Client entrypoint (contract K2). Every piece of client-side registration in this mod lives here
 * and nowhere else; the handler bodies stay in {@link ClientEventSubscriber}.
 *
 * <p>{@code ClientLifecycleSubscriber} is gone: three of its four handlers moved into
 * {@link #onInitializeClient()} verbatim and the fourth ({@code ModMismatchEvent}) has no Fabric
 * counterpart — the loader performs no mod-version handshake at all.</p>
 */
public class BlockUIClient implements ClientModInitializer
{
    /**
     * Key-bind category for the developer test window. 26.2 dropped string categories: a category is
     * an {@code Identifier} that has to be registered before any mapping references it.
     * ({@code /opt/mc-src/net/minecraft/client/KeyMapping.java:206,221})
     */
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(BlockUI.resLoc("main"));

    /**
     * Opens the developer test window. Default binding is <b>X</b>, and — as under NeoForge — the key
     * only fires while <b>ctrl + alt + shift</b> are all held, so the full default combination is
     * <b>ctrl + alt + shift + X</b>. Rebinding the key in Options → Controls moves the "X" part.
     */
    public static final KeyMapping OPEN_TEST_GUI =
        new KeyMapping("key.blockui.open_test_gui", InputConstants.KEY_X, CATEGORY);

    @Override
    public void onInitializeClient()
    {
        // Contract K3, client half: registers the clientbound receivers. The payload types
        // themselves are registered by ModNetworking.register() from the common entrypoint.
        ModNetworking.registerClient();

        KeyMappingHelper.registerKeyMapping(OPEN_TEST_GUI);

        // was: AddClientReloadListenersEvent
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Loader.RELOADABLE_LISTEN_RES_LOC, Loader.INSTANCE);

        // was: RegisterTextureAtlasesEvent
        BlockUI.NAMESPACE_TO_ATLAS_MAP.put(Identifier.DEFAULT_NAMESPACE, AtlasIds.GUI);
        final Identifier atlasKey = BlockUI.resLoc("blockui_gui");
        BlockUI.NAMESPACE_TO_ATLAS_MAP.put(BlockUI.MOD_ID, atlasKey);
        AtlasRegistry.register(
            new AtlasConfig(BlockUI.resLoc("textures/atlas/blockui_gui.png"), atlasKey, false, Set.of(GuiMetadataSection.TYPE)));

        // was: RegisterRenderPipelinesEvent — 26.2 makes RenderPipelines.register public, no event needed
        RenderPipelines.register(UiRenderMacros.GUI_POS_COLOR_LINES);
        RenderPipelines.register(UiRenderMacros.GUI_POS_COLOR_TRIANGLES);
        RenderPipelines.register(UiRenderMacros.GUI_POS_TEX_COLOR_TRIANGLES);
        RenderPipelines.register(UiRenderMacros.GUI_POS_TEX_TRIANGLES);

        // was: RegisterPictureInPictureRenderersEvent — the render-state class comes from
        // PictureInPictureRenderer#getRenderStateClass() instead of being passed in
        PictureInPictureRendererRegistry.register(context -> new BlockStatePipRenderer());

        // was: ClientTickEvent.Pre / ClientTickEvent.Post
        ClientTickEvents.START_CLIENT_TICK.register(ClientEventSubscriber::onClientTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(ClientEventSubscriber::onClientTickEnd);

        // was: InputEvent.MouseScrollingEvent with EventPriority.HIGHEST.
        // Fabric has no generic "mouse scrolled in world" callback; the hotbar-scroll ALLOW hook sits on
        // the exact vanilla branch the NeoForge event guarded (MouseHandler#onScroll, no screen open).
        ClientHotbarScrollEvents.ALLOW.register((inventory, currentSlot, nextSlot, horizontal, vertical) ->
            !HookManager.onScroll(horizontal, vertical));

        // was: RenderGuiLayerEvent.Pre + VanillaGuiLayers.CROSSHAIR (cancel while a BOScreen is open)
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, deltaTracker) -> {
            if (!(Minecraft.getInstance().gui.screen() instanceof BOScreen))
            {
                original.extractRenderState(graphics, deltaTracker);
            }
        });
    }

    /**
     * @return true while the (rebindable) test-window key is physically held down.
     */
    public static boolean isTestGuiKeyDown(final Minecraft mc)
    {
        final InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(OPEN_TEST_GUI);
        // 26.3: InputConstants.Type.KEYSYM/SCANCODE collapsed into a single KEYBOARD, and isKeyDown
        // no longer takes a Window (SDL queries the global keyboard state).
        return key.getType() == InputConstants.Type.KEYBOARD
            && key.getValue() != InputConstants.UNKNOWN.getValue()
            && InputConstants.isKeyDown(key.getValue());
    }
}
