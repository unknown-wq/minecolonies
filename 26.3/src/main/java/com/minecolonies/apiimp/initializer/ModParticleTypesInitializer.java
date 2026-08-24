package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.particles.SleepingParticle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.core.Registry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Initializes the particle type.
 * <p>
 * <b>Port note (contract C5).</b> The two {@code @EventBusSubscriber} inner classes became plain
 * {@code init()} hooks: the common one is called from the mod entry point, the client one from the client
 * initializer.
 */
public class ModParticleTypesInitializer
{
    /**
     * Particle type
     */
    public static final SimpleParticleType SLEEPINGPARTICLE_TYPE = FabricParticleTypes.simple(true);
    public static final Identifier  SLEEPING_TEXTURE      = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "particle/sleeping");

    /**
     * Registers the particle type. Called from the mod entry point.
     */
    public static void init()
    {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, SLEEPING_TEXTURE, SLEEPINGPARTICLE_TYPE);
    }

    /**
     * Register the client side factory
     */
    @Environment(EnvType.CLIENT)
    public static final class ClientRegistration
    {
        private ClientRegistration()
        {
            throw new IllegalStateException("Tried to initialize: ClientRegistration but this is a Utility class.");
        }

        /**
         * Was {@code RegisterParticleProvidersEvent#registerSpriteSet}. Called from the client initializer.
         */
        public static void init()
        {
            ParticleProviderRegistry.getInstance().register(SLEEPINGPARTICLE_TYPE, SleepingParticle.Factory::new);
        }
    }
}
