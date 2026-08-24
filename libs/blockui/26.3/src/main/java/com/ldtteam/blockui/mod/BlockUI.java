package com.ldtteam.blockui.mod;

import com.ldtteam.blockui.mod.container.ContainerHook;
import com.ldtteam.common.network.ModNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Common entrypoint (contract K2). Replaces the NeoForge {@code @Mod} constructor.
 *
 * <p>Everything client-side lives in {@link BlockUIClient}; this class keeps only the mod-wide
 * constants and the single lifecycle hook that is not client-only.</p>
 */
public class BlockUI implements ModInitializer
{
    public static final String MOD_ID = "blockui";

    /**
     * If your mod is using GUI atlas register it here so we know it exists.
     */
    public static final Map<String, Identifier> NAMESPACE_TO_ATLAS_MAP = new HashMap<>();

    @Override
    public void onInitialize()
    {
        // Contract K3. Registers the payload types and the serverbound receivers, and starts
        // capturing the running server for IClientboundDistributor. Without this the network layer
        // compiles but silently does nothing.
        ModNetworking.register();

        // was: @SubscribeEvent onTagsUpdated(TagsUpdatedEvent) in ClientEventSubscriber
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> ContainerHook.init());
    }

    public static Identifier resLoc(final String path)
    {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Contract K6 — the single replacement for NeoForge {@code FMLEnvironment.dist.isClient()}.
     * The signature is frozen: the seven callers in {@code com.ldtteam.common} and
     * {@code com.ldtteam.blockui} use exactly this method and nothing else.
     *
     * @return true when running on a physical client (integrated server included).
     */
    public static boolean isClient()
    {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }
}
