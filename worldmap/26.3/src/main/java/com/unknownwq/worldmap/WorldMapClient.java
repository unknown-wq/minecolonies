package com.unknownwq.worldmap;

import com.unknownwq.worldmap.colony.ColonyBridge;
import com.unknownwq.worldmap.colony.ColonyLayers;
import com.unknownwq.worldmap.colony.ColonyOverlay;
import com.unknownwq.worldmap.map.MapService;
import com.unknownwq.worldmap.screen.WorldMapScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Client only -- {@code fabric.mod.json} declares {@code "environment": "client"} and a single
 * {@code client} entrypoint, and there is no network protocol, so a server never sees this mod at all.
 *
 * <p>The mod has no mixins and no access widener. Everything it touches is public Minecraft API or Fabric
 * API: {@code ClientChunkEvents} tells it which chunks arrived, {@code ClientTickEvents} gives it a pump and
 * a place to poll the key binding, {@code BlockState#getMapColor} gives it the colours, and
 * {@code Screen#isPauseScreen} gives it the pause.</p>
 */
@Environment(EnvType.CLIENT)
public final class WorldMapClient implements ClientModInitializer
{
    /**
     * The mod id, which is also the tile directory name and the key binding namespace.
     */
    public static final String MOD_ID = "worldmap";

    /**
     * Shared logger.
     */
    public static final Logger LOGGER = LoggerFactory.getLogger("World Map");

    private static MapService service;
    private static ColonyOverlay colonies = ColonyOverlay.NONE;
    private static ColonyLayers layers;

    @Override
    public void onInitializeClient()
    {
        final FabricLoader loader = FabricLoader.getInstance();
        final WorldMapConfig config = WorldMapConfig.load(loader.getConfigDir().resolve(MOD_ID + ".properties"));
        service = new MapService(config, loader.getGameDir());
        layers = new ColonyLayers();

        // MineColonies is optional and stays optional: fabric.mod.json only *recommends* it, and this call
        // returns a do-nothing overlay when it is not there. Nothing below this line, and nothing in the
        // screen, ever names a MineColonies type -- see ColonyBridge for why that has to be true rather than
        // merely tidy.
        colonies = ColonyBridge.create();

        WorldMapKeys.register();

        ClientChunkEvents.CHUNK_LOAD.register(service::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(service::onChunkUnload);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            service.tick(minecraft);
            colonies.tick(minecraft);

            // consumeClick drains a counter, so the loop matters: a press and release inside one tick still
            // opens the map exactly once.
            boolean pressed = false;
            while (WorldMapKeys.openMap().consumeClick())
            {
                pressed = true;
            }
            if (pressed && minecraft.level != null && minecraft.gui.screen() == null)
            {
                // Everything the client has loaded round the player is re-scanned here, on the way in, so
                // the map shows the ground as it is now and not as it was the first time it was walked
                // past. The queueing is a few thousand array lookups; the scanning itself is handed to the
                // background thread and lands while the player is reading the map. Nothing re-scans while
                // the map is shut, and nothing re-scans while it is open either -- the next refresh is the
                // next time it is opened.
                service.refreshAroundPlayer(minecraft);
                minecraft.gui.setScreen(new WorldMapScreen(service, colonies, layers));
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
            // Colonies first: clear() is what writes the remembered-colony file, and it writes it on this
            // thread, so it has to happen while there is still a thread to write on.
            colonies.clear();
            service.shutdown();
        });

        LOGGER.info("World map ready -- press M in game.");
    }

    /**
     * @return the map service, or null before the client entrypoint has run.
     */
    public static MapService service()
    {
        return service;
    }

    /**
     * @return the colony overlay. {@link ColonyOverlay#NONE} when MineColonies is not installed, and before
     *     the client entrypoint has run.
     */
    public static ColonyOverlay colonies()
    {
        return colonies;
    }

    /**
     * @return which colony layers are switched on, or null before the client entrypoint has run.
     */
    public static ColonyLayers layers()
    {
        return layers;
    }
}
