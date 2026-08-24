package com.minecolonies.core.entity.other.cavalry;

import com.minecolonies.api.util.Log;
import com.minecolonies.core.MineColonies;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

/**
 * Debug tracing for cavalry horses.
 * <p>
 * PORT-NOTE(26.2/Fabric): this used to be a NeoForge {@code @EventBusSubscriber} with three
 * {@code @SubscribeEvent} methods. Fabric has no annotation scanning, so the three hooks are now
 * plain callback registrations and {@link #register()} has to be called once from the mod
 * initializer (contract C5).
 * <ul>
 *     <li>{@code LivingIncomingDamageEvent} → {@link ServerLivingEntityEvents#ALLOW_DAMAGE}
 *         (fires before the damage is applied, same as the NeoForge event; the callback always
 *         returns {@code true} so it stays purely observational).</li>
 *     <li>{@code LivingDeathEvent} → {@link ServerLivingEntityEvents#AFTER_DEATH}.</li>
 *     <li>{@code EntityLeaveLevelEvent} → {@link ServerEntityEvents#ENTITY_UNLOAD}. This is
 *         server-side only; the NeoForge event also fired on the client.</li>
 * </ul>
 */
public final class CavalryHorseDebug
{
    private CavalryHorseDebug()
    {
    }

    /**
     * Register the debug callbacks. Call once from the mod initializer.
     * <p>
     * PORT-NOTE(26.2): gated on the {@code cavalrydebuglog} server config, off by default. Upstream registered
     * these unconditionally, and nobody noticed because no player could reach a cavalry horse: there was no
     * Stable to build. Now that the Stable ships, a mounted guard in a fight produced one INFO line <em>with a
     * captured stack trace</em> for every damage tick it or its horse took, and a WARN with another one every
     * time a horse left a chunk. That is a log flood and a {@code new Exception()} per hit on the server thread.
     */
    public static void register()
    {
        if (!MineColonies.getConfig().getServer().cavalryDebugLog.get())
        {
            return;
        }

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof CavalryHorseEntity)
            {
                Log.getLogger().info(
                    "CavHorse incoming damage: {} cause={} amount={}",
                    entity.getUUID(),
                    source.type().msgId(),
                    amount,
                    new Exception("Damage event stack trace")
                );
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof CavalryHorseEntity)
            {
                Log.getLogger().warn(
                    "CavHorse died: {} cause={}",
                    entity.getUUID(),
                    source.type().msgId(),
                    new Exception("Death event stack trace")
                );
            }
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof final CavalryHorseEntity ch)
            {
                Log.getLogger().warn(
                    "CavHorse left level: {} reason={}",
                    ch.getUUID(),
                    ch.getRemovalReason(),
                    new Exception("EntityLeaveLevelEvent stack trace")
                );
            }
        });
    }
}
