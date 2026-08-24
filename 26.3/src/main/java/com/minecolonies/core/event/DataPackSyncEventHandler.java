package com.minecolonies.core.event;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import com.minecolonies.core.compatibility.CraftingTagAuditor;
import com.minecolonies.core.datalistener.DiseasesListener;
import com.minecolonies.core.datalistener.QuestJsonListener;
import com.minecolonies.core.network.messages.client.UpdateClientWithCompatibilityMessage;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.jetbrains.annotations.NotNull;

/**
 * Handles synching of custom datapack and compatibility data from server to client (and initial population
 * for the server in both single-player and dedicated).
 *
 * As of Forge 36.2.4, at least, events happen in this order:
 *
 * For Single Player, on startup:
 *  -- JsonReloadListeners, TagsUpdatedEvent, FMLServerAboutToStart, FMLServerStarted, OnDatapackSyncEvent, RecipesUpdatedEvent
 * For Dedicated Server, on startup:
 *  -- JsonReloadListeners, TagsUpdatedEvent, FMLServerAboutToStart, FMLServerStarted
 * For Remote Client, on login:
 *  -- OnDatapackSyncEvent [server], PlayerLoggedInEvent [server], RecipesUpdatedEvent [client], TagsUpdatedEvent [client]
 * On /reload:
 *  -- JsonReloadListeners, TagsUpdatedEvent [server], OnDatapackSyncEvent [server], TagsUpdatedEvent [remote client], RecipesUpdatedEvent [client]
 */
public class DataPackSyncEventHandler
{
    /**
     * Events subscribed on both client and server (but mostly for server-side events).
     */
    public static class ServerEvents
    {
        /**
         * If the initial worldload was done.
         */
        private static boolean loaded = false;

        /**
         * Updates internal caches of vanilla recipes and tags.
         * This is only called server-side, after JsonReloadListeners have finished.
         *
         * @param server The server.
         */
        private static void discoverCompatLists(@NotNull final MinecraftServer server)
        {
            Log.getLogger().warn("Starting Compat Discovery");
            IMinecoloniesAPI.getInstance().getColonyManager().getCompatibilityManager().getFurnaceRecipes().loadRecipes(server.getRecipeManager(), server.overworld());
            IMinecoloniesAPI.getInstance().getColonyManager().getCompatibilityManager().discover(server.getRecipeManager(), server.overworld());
            CustomRecipeManager.getInstance().resolveTemplates(server.registryAccess());
            CustomRecipeManager.getInstance().buildLootData(server.overworld());
        }

        /**
         * Send custom sync packets to the given player.
         *
         * @param player    the player to send the sync packets to.
         * @param compatMsg a cached copy of this message, to avoid rebuilding it for each player.
         */
        private static void sendPackets(@NotNull final ServerPlayer player,
                                        @NotNull final UpdateClientWithCompatibilityMessage compatMsg)
        {
            compatMsg.sendToPlayer(player);
            IGlobalResearchTree.getInstance().sendGlobalResearchTreePackets(player);
            QuestJsonListener.sendGlobalQuestPackets(player);
            DiseasesListener.sendGlobalDiseasesPackets(player);

            // always send this last; we rely on CustomRecipesReloadedEvent signalling that all packets are processed
            CustomRecipeManager.getInstance().sendCustomRecipeManagerPackets(player);
        }


        /**
         * Installs the server callbacks. Called once from the mod entry point.
         * <p>
         * Port note (contract C5): {@code OnDatapackSyncEvent} split in two on Fabric. The
         * {@code getPlayer() == null} branch -- "datapacks were just reloaded, rebuild everything and push it
         * to everybody" -- is {@link ServerLifecycleEvents#END_DATA_PACK_RELOAD}; the per-player branch is
         * {@link ServerLifecycleEvents#SYNC_DATA_PACK_CONTENTS}, which replaces the old
         * {@code PlayerLoggedInEvent} hook and fires at the right point in the login sequence.
         */
        public static void register()
        {
            ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> onDataPackReload(server));
            ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register((player, joined) -> {
                if (joined)
                {
                    sendPackets(player, new UpdateClientWithCompatibilityMessage(player.level().registryAccess()));
                }
            });
        }

        /**
         * Rebuilds the derived lists after a datapack reload and pushes the results to every connected player.
         *
         * @param server the server.
         */
        private static void onDataPackReload(@NotNull final MinecraftServer server)
        {
            final CustomRecipeManager recipeManager = CustomRecipeManager.getInstance();
            final GameProfile owner = server.getSingleplayerProfile();

            // for a reload event, we also want to rebuild various lists (mirroring FMLServerStartedEvent)
            discoverCompatLists(server);

            // and then finally update every player with the results
            final UpdateClientWithCompatibilityMessage compatMsg = new UpdateClientWithCompatibilityMessage(server.registryAccess());
            for (final ServerPlayer player : server.getPlayerList().getPlayers())
            {
                if (player.getGameProfile() == owner)
                {
                    // SP 'server' doesn't need most of the packets, but does need compatmgr since we keep separate instances
                    compatMsg.sendToPlayer(player);
                }
                else
                {
                    sendPackets(player, compatMsg);
                }
            }

            if (MineColonies.getConfig().getServer().auditCraftingTags.get())
            {
                CraftingTagAuditor.doRecipeAudit(server, recipeManager);
            }
        }

        /**
         * Handle initial load. But only once.
         * @param server the server to load it for.
         */
        public static void load(@NotNull final MinecraftServer server)
        {
            if (loaded)
            {
                return;
            }
            loaded = true;
            discoverCompatLists(server);
        }

        /**
         * Reset on shutdown.
         */
        public static void reset()
        {
            loaded = false;
        }
    }

    /**
     * Events subscribed on client-side only.
     */
    @Environment(EnvType.CLIENT)
    public static class ClientEvents
    {
        /**
         * Installs the client callbacks. Called once from the client initializer.
         * <p>
         * <b>DISABLED (degradation ladder step 3).</b> This used to hook NeoForge's {@code RecipesUpdatedEvent}
         * and rebuild the client's furnace-recipe cache from the synced recipe manager. 26.2 no longer sends
         * the recipe collection to clients at all -- {@code ClientPacketListener#recipes()} hands out a
         * {@link net.minecraft.world.item.crafting.RecipeAccess}, which has no way to enumerate recipes -- and
         * Fabric has no replacement event either. Consequence: on a remote client
         * {@code IColonyManager.getCompatibilityManager().getFurnaceRecipes()} stays empty, so any GUI that
         * looks up a smelting recipe client-side shows nothing. The server-side cache
         * ({@link ServerEvents#discoverCompatLists}) is unaffected.
         */
        public static void register()
        {
            // intentionally empty, see javadoc
        }
    }
}
