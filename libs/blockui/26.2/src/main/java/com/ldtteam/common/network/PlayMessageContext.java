package com.ldtteam.common.network;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Replacement for NeoForge's {@code net.neoforged.neoforge.network.handling.IPayloadContext}.
 * <p>
 * Fabric hands the receiver either {@code ServerPlayNetworking.Context} or {@code ClientPlayNetworking.Context};
 * neither is usable from common code (the client one is a client-only class), so both are adapted to this
 * interface. Only the parts of {@code IPayloadContext} that message implementations actually consume survive.
 * <p>
 * <b>Port note:</b> {@link #enqueueWork(Runnable)} is kept purely so existing message bodies compile - Fabric
 * receivers already run on the logical side's main thread, so the task is executed inline.
 */
public interface PlayMessageContext
{
    /**
     * @return player which received this message; on the logical server this is always a
     *         {@link net.minecraft.server.level.ServerPlayer}
     */
    @Nullable
    Player player();

    /**
     * @return direction this message travelled in
     */
    PacketFlow flow();

    /**
     * @return running server, null when this is a clientbound message on a remote client
     */
    @Nullable
    MinecraftServer server();

    /**
     * Runs the task on the logical side's main thread.
     *
     * @param  task work to do
     * @return      already completed future - Fabric receivers are dispatched on the main thread, so the task
     *              has run by the time this returns
     */
    default CompletableFuture<Void> enqueueWork(final Runnable task)
    {
        try
        {
            task.run();
        }
        catch (final Throwable t)
        {
            return CompletableFuture.failedFuture(t);
        }
        return CompletableFuture.completedFuture(null);
    }
}
