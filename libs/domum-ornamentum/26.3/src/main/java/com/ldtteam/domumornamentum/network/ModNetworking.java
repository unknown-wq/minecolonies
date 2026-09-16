package com.ldtteam.domumornamentum.network;

import com.ldtteam.domumornamentum.network.messages.CreativeSetArchitectCutterSlotMessage;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Networking entry point (port contract C3).
 * <p>
 * {@link #register()} is invoked from the common mod initializer. Everything that touches client-only
 * classes lives in {@link ModClientNetworking}, which is only ever class-loaded from the client side —
 * a dedicated server never resolves it.
 */
public final class ModNetworking
{
    private ModNetworking()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModNetworking. This is a utility class");
    }

    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(CreativeSetArchitectCutterSlotMessage.ID, CreativeSetArchitectCutterSlotMessage.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CreativeSetArchitectCutterSlotMessage.ID,
            (payload, context) -> payload.onExecute(context.player()));
    }
}
