package com.ldtteam.structurize.event;

import com.ldtteam.structurize.api.IScrollableItem;
import com.ldtteam.structurize.api.ISpecialBlockPickItem;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.client.ModKeyMappings;
import com.ldtteam.structurize.client.gui.GuiStubs;
import com.ldtteam.structurize.items.ItemScanTool;
import com.ldtteam.structurize.network.messages.ItemMiddleMouseMessage;
import com.ldtteam.structurize.network.messages.ScanToolTeleportMessage;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BoxPreviewData;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Iterator;
import java.util.Map;

/**
 * Class with methods for receiving various client side game events.
 *
 * <p>Port note: {@code @SubscribeEvent} methods became Fabric callbacks installed by {@link #register()}.</p>
 *
 * <ul>
 * <li>{@code RenderGuiLayerEvent.Pre} + {@code VanillaGuiLayers} → {@code HudElementRegistry.replaceElement}
 * with {@code VanillaHudElements} ids; there is no "cancel", the element is wrapped instead.</li>
 * <li>{@code ClientTickEvent.Post/Pre} → {@code ClientTickEvents.END_CLIENT_TICK} /
 * {@code START_CLIENT_TICK}.</li>
 * <li>{@code InputEvent.MouseScrollingEvent} → {@code ClientHotbarScrollEvents.ALLOW}. Fabric has no generic
 * mouse wheel event; ALLOW wraps exactly the in-world hotbar scroll the old handler used to cancel, and
 * returning false is the cancel.</li>
 * <li>{@code ClientPlayerNetworkEvent.LoggingOut} → {@code ClientPlayConnectionEvents.DISCONNECT}.</li>
 * <li>{@code Minecraft#getProfiler()} → {@code Profiler.get()}, {@code Minecraft#screen} →
 * {@code Minecraft#gui.screen()}.</li>
 * </ul>
 */
public class ClientEventSubscriber
{
    /**
     * Private constructor to hide implicit public one.
     */
    private ClientEventSubscriber()
    {
        /*
         * Intentionally left empty
         */
    }

    /**
     * Installs every client game callback. Called once from the client initializer.
     */
    public static void register()
    {
        hideHudElementBehindBuildTool(VanillaHudElements.HEALTH_BAR);
        hideHudElementBehindBuildTool(VanillaHudElements.FOOD_BAR);

        ClientTickEvents.END_CLIENT_TICK.register(ClientEventSubscriber::onClientTickEvent);
        ClientTickEvents.START_CLIENT_TICK.register(ClientEventSubscriber::onPreClientTickEvent);

        ClientHotbarScrollEvents.ALLOW.register(ClientEventSubscriber::onMouseWheel);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GuiStubs.clearBuildToolStaticData());

        // Was two @SubscribeEvent methods on RenderLevelStageEvent (the second one only to flush the mod's
        // own buffer source at EventPriority.LOWEST). 26.2 collects geometry through a submit queue, so
        // WorldRenderMacros hooks LevelRenderEvents.COLLECT_SUBMITS itself and there is nothing left to flush.
        WorldRenderContext.INSTANCE.registerLevelRenderCallbacks();
    }

    /**
     * Wraps a vanilla hud element so that it is skipped while the build tool window is open. The NeoForge
     * version cancelled {@code RenderGuiLayerEvent.Pre}; Fabric has no cancel, elements are replaced.
     *
     * @param elementId the vanilla hud element to wrap.
     */
    private static void hideHudElementBehindBuildTool(final Identifier elementId)
    {
        HudElementRegistry.replaceElement(elementId,
            original -> (extractor, deltaTracker) ->
            {
                if (!GuiStubs.isBuildToolScreenOpen())
                {
                    original.extractRenderState(extractor, deltaTracker);
                }
            });
    }

    /**
     * Called at the end of a client tick. Cleans the renderer cache every 5 seconds (100 ticks) and handles
     * the scan tool teleport key.
     *
     * @param mc the client.
     */
    private static void onClientTickEvent(final Minecraft mc)
    {
        Profiler.get().push("structurize");

        if (mc.level != null && mc.level.getGameTime() % (Constants.TICKS_SECOND * BlueprintHandler.CACHE_EXPIRE_CHECK_SECONDS) == 0)
        {
            Profiler.get().push("blueprint_manager_tick");
            BlueprintHandler.getInstance().cleanCache();
            Profiler.get().pop();
        }

        if (ModKeyMappings.TELEPORT.get().consumeClick() && mc.level != null && mc.player != null &&
            mc.player.getMainHandItem().getItem() instanceof ItemScanTool tool)
        {
            if (tool.onTeleport(mc.player, mc.player.getMainHandItem()))
            {
                new ScanToolTeleportMessage().sendToServer();
            }
        }

        Profiler.get().pop();
    }

    /**
     * Called at the start of a client tick. Handles the pick block key for special items and expires cached
     * box previews.
     *
     * @param mc the client.
     */
    private static void onPreClientTickEvent(final Minecraft mc)
    {
        if (mc.player == null || mc.gui.screen() != null || mc.level == null)
        {
            return;
        }

        if (mc.options.keyPickItem.consumeClick())
        {
            BlockPos pos = mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) mc.hitResult).getBlockPos() : null;
            if (pos != null && mc.level.getBlockState(pos).isAir())
            {
                pos = null;
            }

            final ItemStack current = mc.player.getInventory().getSelectedItem();
            if (current.getItem() instanceof ISpecialBlockPickItem clickableItem)
            {
                final boolean ctrlKey = hasControlDown();
                // InteractionResult is a sealed interface in 26.2, not an enum, so this cannot be a switch.
                final InteractionResult result = clickableItem.onBlockPick(mc.player, current, pos, ctrlKey);
                if (result instanceof InteractionResult.Pass)
                {
                    ++mc.options.keyPickItem.clickCount;
                }
                else if (!(result instanceof InteractionResult.Fail))
                {
                    new ItemMiddleMouseMessage(pos, ctrlKey).sendToServer();
                }
            }
            else
            {
                ++mc.options.keyPickItem.clickCount;
            }
        }

        for (Iterator<Map.Entry<String, BoxPreviewData>> iterator = RenderingCache.boxRenderingCache.entrySet().iterator(); iterator.hasNext(); )
        {
            final var entry = iterator.next();
            if (entry.getValue().isExpired())
            {
                iterator.remove();
            }
        }
    }

    /**
     * Called before the mouse wheel changes the selected hotbar slot.
     *
     * @param inventory   the player inventory.
     * @param currentSlot the currently selected slot.
     * @param newSlot     the slot the vanilla handler wants to select.
     * @param scrollX     horizontal scroll delta.
     * @param scrollY     vertical scroll delta.
     * @return false to swallow the scroll, which is the Fabric equivalent of cancelling the old event.
     */
    private static boolean onMouseWheel(final Inventory inventory, final int currentSlot, final int newSlot, final double scrollX, final double scrollY)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null || mc.level == null)
        {
            return true;
        }
        if (!mc.player.isShiftKeyDown())
        {
            return true;
        }

        final ItemStack current = mc.player.getInventory().getSelectedItem();
        if (current.getItem() instanceof IScrollableItem scrollableItem)
        {
            final boolean ctrlKey = hasControlDown();
            // InteractionResult is a sealed interface in 26.2, not an enum, so this cannot be a switch.
            final InteractionResult result = scrollableItem.onMouseScroll(mc.player, current, scrollX, scrollY, ctrlKey);
            if (result instanceof InteractionResult.Pass)
            {
                return true;
            }
            if (!(result instanceof InteractionResult.Fail))
            {
                new ItemMiddleMouseMessage(scrollX, scrollY, ctrlKey).sendToServer();
            }
            return false;
        }
        return true;
    }

    /**
     * Replacement for the removed {@code Screen#hasControlDown()}: in 26.2 modifier state is only handed out
     * inside input events, so it is read straight off the window here.
     *
     * @return true while a control key (command on macOS) is held.
     */
    private static boolean hasControlDown()
    {
        // TODO(port-26.3): SDL replaced GLFW - isKeyDown no longer takes a Window, and the super/command key
        //  scancodes are named KEY_LGUI/KEY_RGUI instead of KEY_LSUPER/KEY_RSUPER.
        if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY)
        {
            return InputConstants.isKeyDown(InputConstants.KEY_LGUI) || InputConstants.isKeyDown(InputConstants.KEY_RGUI);
        }
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }
}
