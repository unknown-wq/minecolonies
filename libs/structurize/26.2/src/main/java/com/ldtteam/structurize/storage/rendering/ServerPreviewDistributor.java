package com.ldtteam.structurize.storage.rendering;

import com.ldtteam.structurize.network.messages.SyncPreviewCacheToClient;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import java.util.UUID;

/**
 * Class handling blueprint syncing between players.
 */
public class ServerPreviewDistributor
{
    /**
     * Players that signed up to receive blueprint data.
     */
    private static Object2BooleanMap<UUID> registeredPlayers = new Object2BooleanOpenHashMap<>();

    /**
     * Register the server side lifecycle hooks. Called from the mod initializer.
     * Named init() because register(ServerPlayer, boolean) already owns the register name.
     */
    public static void init()
    {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLogout(handler.player));
    }

    /**
     * NeoForge's PlayerLoggedOutEvent also fired client side and cleared the RenderingCache there.
     * The Fabric disconnect event is server only; the client side clear now happens in
     * ClientStructurePackLoader#onWorldTick when the level goes away.
     */
    public static void onLogout(final ServerPlayer player)
    {
        registeredPlayers.removeBoolean(player.getUUID());
    }

    /**
     * Distribute this rendering cache to all that are wanting to listen.
     * @param renderingCache the cache to distribute.
     */
    public static void distribute(final BlueprintPreviewData renderingCache, final ServerPlayer sourcePlayer)
    {
        for (final ServerPlayer player : sourcePlayer.level().getServer().getLevel(sourcePlayer.level().dimension()).players())
        {
            if ((player.blockPosition().distSqr(renderingCache.getPos()) < 128 * 128 || renderingCache.getPos().equals(BlockPos.ZERO)) && // within sensible distance
                !player.getUUID().equals(sourcePlayer.getUUID()) && // dont send to source
                player.isAlive() && // dont send to dead
                registeredPlayers.getBoolean(player.getUUID())) // only those who want to see previews
            {
                new SyncPreviewCacheToClient(renderingCache, player.getUUID()).sendToPlayer(player);
            }
        }
    }

    /**
     * Register a player with their settings.
     * @param player the player.
     * @param displayShared if displayed is shared or not.
     */
    public static void register(final ServerPlayer player, final boolean displayShared)
    {
        registeredPlayers.put(player.getUUID(), displayShared);
    }
}
