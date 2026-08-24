package com.ldtteam.domumornamentum.client;

import com.ldtteam.domumornamentum.client.event.handlers.ClientTickEventHandler;
import com.ldtteam.domumornamentum.client.event.handlers.MateriallyTexturedBlockPreviewRenderHandler;
import com.ldtteam.domumornamentum.client.event.handlers.ModBusEventHandler;
import com.ldtteam.domumornamentum.client.event.handlers.RegisterColorHandlersEventHandler;
import com.ldtteam.domumornamentum.client.model.loader.MateriallyTexturedModelLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Single entry point for every client-side registration in Domum Ornamentum.
 *
 * <p>Contract C2: {@code DomumOrnamentumClient implements ClientModInitializer} (owned by agent A) calls
 * {@link #register()} from {@code onInitializeClient()} and does nothing else. Everything that NeoForge used
 * to do through {@code @EventBusSubscriber(value = Dist.CLIENT)} classes is registered explicitly here, which
 * is also the client/server firewall: the dedicated server never invokes {@code onInitializeClient}, so no
 * class referenced below is ever loaded server side.
 *
 * <p>Order matters in one place only: {@link MateriallyTexturedModelLoader#register()} installs a
 * {@code ModelLoadingPlugin}, and Fabric collects plugins during mod init and replays them on every resource
 * reload - so it must be registered during client init, not lazily.
 */
@Environment(EnvType.CLIENT)
public final class ClientRegistrations
{
    private ClientRegistrations()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ClientRegistrations. This is a utility class");
    }

    public static void register()
    {
        // Block models: wraps every IMateriallyTexturedBlock's baked BlockStateModel so it retextures itself
        // from the block entity at render time.
        MateriallyTexturedModelLoader.register();

        // Screens (replaces RegisterMenuScreensEvent).
        ModBusEventHandler.register();

        // Client tick counter (replaces ClientTickEvent.Pre).
        ClientTickEventHandler.register();

        // Disabled no-op; BlockColor/ItemColor were removed from vanilla 26.2, the material
        // tint is applied while emitting quads instead. See RegisterColorHandlersEventHandler.
        RegisterColorHandlersEventHandler.register();

        // Disabled no-op; the placement ghost preview needs a rewrite onto
        // LevelRenderEvents + SubmitNodeCollector. See MateriallyTexturedBlockPreviewRenderHandler.
        MateriallyTexturedBlockPreviewRenderHandler.register();
    }
}
