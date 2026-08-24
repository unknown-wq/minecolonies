package com.ldtteam.domumornamentum;

import com.ldtteam.domumornamentum.client.ClientRegistrations;
import com.ldtteam.domumornamentum.network.ModNetworking;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint (contract C2).
 *
 * <p>Everything that used to hang off a {@code Dist.CLIENT} {@code @EventBusSubscriber} lives behind
 * {@link ClientRegistrations#register()} (agent C): colour handlers
 * ({@code RegisterColorHandlersEventHandler}), the model loader
 * ({@code MateriallyTexturedModelLoader}), screen binding + render layers + item properties
 * ({@code client.event.handlers.ModBusEventHandler}), the client tick counter
 * ({@code ClientTickEventHandler}) and the ghost preview renderer
 * ({@code MateriallyTexturedBlockPreviewRenderHandler}).</p>
 *
 * <p>Client-only network receivers are registered separately, mirroring contract C3.</p>
 */
public class DomumOrnamentumClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        ClientRegistrations.register();
        ModNetworking.registerClient();
    }
}
