package com.ldtteam.domumornamentum.client.event.handlers;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Client tick counter.
 *
 * <p>Ported from the NeoForge {@code @EventBusSubscriber(bus = GAME)} +
 * {@code @SubscribeEvent onTickClientTick(ClientTickEvent.Pre)} shape to Fabric's
 * {@code ClientTickEvents.END_CLIENT_TICK} (contract C5: every {@code @SubscribeEvent} becomes an explicit
 * registration from the client entrypoint). Registration happens in
 * {@code com.ldtteam.domumornamentum.client.ClientRegistrations#register()}.
 *
 * <p>NeoForge fired {@code Pre} (before the tick); Fabric's lifecycle module exposes
 * {@code START_CLIENT_TICK} and {@code END_CLIENT_TICK}. Only the counter's phase within a tick changes,
 * which nothing in the mod depends on.
 */
public class ClientTickEventHandler
{
    private static final ClientTickEventHandler INSTANCE = new ClientTickEventHandler();

    public static ClientTickEventHandler getInstance()
    {
        return INSTANCE;
    }

    private long clientTicks = 0;
    private long nonePausedTicks = 0;

    private ClientTickEventHandler()
    {
    }

    /**
     * Hooks the counter into the client tick loop.
     */
    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> getInstance().onClientTick(minecraft));
    }

    private void onClientTick(final Minecraft minecraft)
    {
        clientTicks++;
        if (!minecraft.isPaused())
        {
            nonePausedTicks++;
        }
    }

    public long getClientTicks()
    {
        return clientTicks;
    }

    public long getNonePausedTicks()
    {
        return nonePausedTicks;
    }
}
