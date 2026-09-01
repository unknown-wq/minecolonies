package com.ldtteam.structurize.event;

import com.ldtteam.structurize.commands.EntryPoint;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.util.BlockUtils;
import com.ldtteam.structurize.util.IOPool;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * Class with methods for receiving various common game events.
 *
 * <p>Port note: the {@code @SubscribeEvent} methods became Fabric callbacks installed by
 * {@link #register()}. {@code RegisterCommandsEvent} → {@code CommandRegistrationCallback} (which also hands
 * over a {@code CommandBuildContext} we do not need), {@code LevelTickEvent.Pre} →
 * {@code ServerTickEvents.START_LEVEL_TICK} (server levels only — the client half of the old handler lives in
 * {@link ClientEventSubscriber}), {@code ServerStoppingEvent} → {@code ServerLifecycleEvents.SERVER_STOPPING}.</p>
 */
public class EventSubscriber
{
    /**
     * Private constructor to hide implicit public one.
     */
    private EventSubscriber()
    {
        /*
         * Intentionally left empty
         */
    }

    /**
     * Installs every common game callback. Called once from the mod initializer.
     */
    public static void register()
    {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) -> EntryPoint.register(dispatcher, environment));

        ServerTickEvents.START_LEVEL_TICK.register(EventSubscriber::onWorldTick);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> IOPool.shutdown());

        // Two caches in the placement path are derived from block tags and were never rebuilt when a
        // datapack reload changed them: PlacementHandlers' block-to-handler map (BlackListedBlockPlacementHandler
        // tests ModTags.BLUEPRINT_BLACKLIST) and BlockUtils' solid-block set (filtered on
        // ModTags.WEAK_SOLID_BLOCKS). There was no tag-reload hook in this mod at all; this is it.
        // The handler cache is cleared on both sides, since the client builds resource lists through
        // PlacementHandlers.getHandler too. The solid-block set is rebuilt for the logical server only,
        // which is the only side that ever populated it.
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            PlacementHandlers.invalidateHandlerCache();
            if (!client)
            {
                BlockUtils.onTagsReloaded();
            }
        });
    }

    /**
     * Called before a server level ticks.
     *
     * @param serverLevel the ticking level.
     */
    private static void onWorldTick(final ServerLevel serverLevel)
    {
        BlockUtils.checkOrInit();
        Manager.onWorldTick(serverLevel);
    }
}
