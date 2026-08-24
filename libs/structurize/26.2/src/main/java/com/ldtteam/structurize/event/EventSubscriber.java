package com.ldtteam.structurize.event;

import com.ldtteam.structurize.commands.EntryPoint;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.util.BlockUtils;
import com.ldtteam.structurize.util.IOPool;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
