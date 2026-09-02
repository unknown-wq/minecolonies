package com.ldtteam.domumornamentum.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * List of possible network targets when sending from server to client.
 * <p>
 * NeoForge's {@code PacketDistributor} has no Fabric counterpart; every target below is expressed as
 * {@link PlayerLookup} + {@link ServerPlayNetworking#send(ServerPlayer, CustomPacketPayload)}.
 */
public interface IClientboundDistributor extends CustomPacketPayload
{
    /**
     * @see #sendToPlayer(ServerPlayer)
     */
    public default void sendToPlayer(final Collection<ServerPlayer> players)
    {
        for (final ServerPlayer serverPlayer : players)
        {
            sendToPlayer(serverPlayer);
        }
    }

    public default void sendToPlayer(final ServerPlayer player)
    {
        ServerPlayNetworking.send(player, this);
    }

    public default void sendToDimension(final ServerLevel serverLevel)
    {
        sendToPlayer(PlayerLookup.level(serverLevel));
    }

    public default void sendToTargetPoint(final ServerLevel level,
        @Nullable final ServerPlayer excluded,
        final double x,
        final double y,
        final double z,
        final double radius)
    {
        for (final ServerPlayer serverPlayer : PlayerLookup.around(level, new Vec3(x, y, z), radius))
        {
            if (serverPlayer != excluded)
            {
                sendToPlayer(serverPlayer);
            }
        }
    }

    public default void sendToAllClients()
    {
        final MinecraftServer server = ModNetworking.getCurrentServer();
        if (server == null)
        {
            return;
        }

        sendToPlayer(PlayerLookup.all(server));
    }

    public default void sendToTrackingEntity(final Entity entity)
    {
        sendToPlayer(PlayerLookup.tracking(entity));
    }

    public default void sendToTrackingEntityAndSelf(final Entity entity)
    {
        sendToTrackingEntity(entity);
        if (entity instanceof final ServerPlayer serverPlayer)
        {
            sendToPlayer(serverPlayer);
        }
    }

    public default void sendToPlayersTrackingChunk(final LevelChunk chunk)
    {
        if (chunk.getLevel() instanceof final ServerLevel level)
        {
            sendToPlayersTrackingChunk(level, chunk.getPos());
            return;
        }

        final String crash =
            "Got client chunk for server network message: " + this.getClass().getName() + " - " + chunk.getClass().getName();
        if (FabricLoader.getInstance().isDevelopmentEnvironment())
        {
            throw new IllegalArgumentException(crash);
        }
        else
        {
            new IllegalArgumentException(crash).printStackTrace();
        }
    }

    public default void sendToPlayersTrackingChunk(final ServerLevel serverLevel, final ChunkPos chunkPos)
    {
        sendToPlayer(PlayerLookup.tracking(serverLevel, chunkPos));
    }
}
