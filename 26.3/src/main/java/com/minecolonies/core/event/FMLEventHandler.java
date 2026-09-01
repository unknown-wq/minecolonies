package com.minecolonies.core.event;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.HeadlessColonyMode;
import com.minecolonies.core.datalistener.*;
import com.minecolonies.core.entity.pathfinding.Pathfinding;
import com.minecolonies.core.util.BackUpHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Server/client lifecycle hooks.
 * <p>
 * <b>Port note (contract C5).</b> Every {@code @SubscribeEvent} here became a Fabric callback installed once
 * from {@link #register()} (common half) or {@link Client#register()} (client half). The tick signatures also
 * changed: {@code IColonyManager} now takes the {@code MinecraftServer} / {@code Level} directly instead of
 * the NeoForge event object, because {@code getServer()} / {@code getLevel()} was all it ever used.
 */
public class FMLEventHandler
{
    private FMLEventHandler()
    {
        throw new IllegalStateException("Tried to initialize: FMLEventHandler but this is a Utility class.");
    }

    /**
     * Installs the common (dedicated and integrated server) callbacks. Called once from the mod entry point.
     */
    public static void register()
    {
        // was: @SubscribeEvent onServerTick(ServerTickEvent.Pre)
        // IColonyManager#onServerTick is deliberately NOT driven from here any more. Both implementations of
        // IColony#onServerTick have empty bodies (Colony.java:1115, ColonyView.java:1136 - same as upstream
        // 1.21.1), while ColonyManager#onServerTick had to build getAllColonies() to reach them: one ArrayList
        // plus one ColonyList#getCopyAsList per server level, every tick, to call nothing.
        // Nothing else was lost with it: the getOrComputeSaveData side effects (ServerColonySaveData
        // setRegistries / computeIfAbsent / setOverworld) still happen for every server level every tick,
        // through onWorldTick -> getColonies below. Re-register this if IColony#onServerTick ever grows a body.
        ServerTickEvents.START_SERVER_TICK.register(DataPackSyncEventHandler.ServerEvents::load);

        // was: @SubscribeEvent onWorldTick(LevelTickEvent.Pre)
        ServerTickEvents.START_LEVEL_TICK.register(level -> IColonyManager.getInstance().onWorldTick(level));

        // was: @SubscribeEvent onPlayerLogin(PlayerEvent.PlayerLoggedInEvent). Fabric only fires this for real
        // server players, so the instanceof ServerPlayer test is gone.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
          // This automatically reloads the owner of the colony if failed.
          IColonyManager.getInstance().getIColonyByOwner(handler.player.level(), handler.player));

        // was: @SubscribeEvent onAddReloadListenerEvent(AddReloadListenerEvent)
        // The drain hook has to be installed before the listeners themselves; see DeferredDataListeners.
        DeferredDataListeners.install();
        registerReloadListeners();

        // was: @SubscribeEvent onServerAboutToStart(ServerAboutToStartEvent)
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            IColonyManager.getInstance().getRecipeManager().reset();
            // Both ends of the lifecycle, not only shutdown: an integrated server starts and stops repeatedly inside
            // one JVM, and headless colony mode must not carry from one world into the next.
            HeadlessColonyMode.reset(server);
        });

        // was: @SubscribeEvent onServerStarted(ServerStartedEvent)
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BackUpHelper.loadMissingColonies();
            HeadlessColonyMode.onServerStarted(server);
        });

        // Nothing is installed for headless colony mode unless this JVM was started asking for it, so an ordinary
        // server does not carry the per-tick callback either.
        if (HeadlessColonyMode.isArmed())
        {
            ServerTickEvents.START_SERVER_TICK.register(HeadlessColonyMode::onServerTick);
        }

        // was: @SubscribeEvent onServerStopped(ServerStoppingEvent)
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Pathfinding.shutdown();
            DataPackSyncEventHandler.ServerEvents.reset();
            HeadlessColonyMode.reset(server);
            // do not keep the reloaded world's registries alive past its server (matters on an integrated one)
            DataListenerUtils.setReloadLookup(null);
        });
    }

    /**
     * Datapack listeners. {@code AddReloadListenerEvent} has no Fabric counterpart; listeners are registered
     * once at startup instead of on every reload, and each needs an id, which the vanilla
     * {@code SimpleJsonResourceReloadListener} subclasses do not carry -- hence {@link #addListener}.
     */
    private static void registerReloadListeners()
    {
        addListener("crafter_recipes", new CrafterRecipeListener());
        addListener("research", new ResearchListener());
        addListener("custom_visitors", new CustomVisitorListener());
        addListener("citizen_names", new CitizenNameListener());
        addListener("quests", new QuestJsonListener());
        addListener("item_nbt", new ItemNbtListener());
        addListener("study_items", StudyItemListener.INSTANCE);
        addListener("diseases", new DiseasesListener());
        addListener("recruitment_items", new RecruitmentItemsListener());
    }

    /**
     * Wraps a plain vanilla reload listener into an identifiable one and registers it for server data.
     * <p>
     * <b>Port note (26.2).</b> The wrapper also defers the listener's apply stage. Item data components are
     * bound by {@code ReloadableServerResources#updateComponentsAndStaticRegistryTags()}, which runs only
     * after the entire reload is done, so a listener that builds {@code ItemStack}s -- which most of ours do,
     * through {@code ItemStorage} / {@code IRecipeStorage} -- dies with
     * {@code "Item <id> does not have components yet"}. The listener is handed a reload executor that parks
     * its apply stage in {@link DeferredDataListeners} instead of running it; the queue is drained right after
     * components are bound. See {@link DeferredDataListeners} for the full reasoning.
     * <p>
     * The prepare stage is untouched: it still runs off-thread on the reload's task executor, and the parking
     * itself still goes through the reload executor, so the main-thread guarantee and the loader's progress
     * counters are unchanged. All nine listeners go through this, not just the ones that touch items, so their
     * relative parse order stays exactly what it was.
     *
     * @param id       the id path inside the minecolonies namespace.
     * @param listener the listener to install.
     */
    private static int listenerOrder = 0;

    private static void addListener(final String id, final PreparableReloadListener listener)
    {
        final Identifier fabricId = Identifier.fromNamespaceAndPath(Constants.MOD_ID, id);
        final int order = listenerOrder++;
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener()
        {
            @Override
            public Identifier getFabricId()
            {
                return fabricId;
            }

            @Override
            public String getName()
            {
                return listener.getName();
            }

            @Override
            public void prepareSharedState(final PreparableReloadListener.SharedState currentReload)
            {
                // runs for every listener before the first preparation task of the reload starts.
                DeferredDataListeners.beginReload();
                listener.prepareSharedState(currentReload);
            }

            @Override
            public CompletableFuture<Void> reload(
              final PreparableReloadListener.SharedState currentReload,
              final Executor taskExecutor,
              final PreparableReloadListener.PreparationBarrier preparationBarrier,
              final Executor reloadExecutor)
            {
                // Completed once the apply stage has been parked, which is what the next listener's barrier
                // and the reload as a whole get to wait on -- the parse itself happens later.
                final CompletableFuture<Void> parked = new CompletableFuture<>();

                final CompletableFuture<Void> parsed = listener.reload(currentReload, taskExecutor, preparationBarrier,
                  applyTask -> reloadExecutor.execute(() -> {
                      DeferredDataListeners.park(fabricId.toString(), order, applyTask);
                      parked.complete(null);
                  }));

                parsed.whenComplete((ignored, error) -> {
                    if (error == null)
                    {
                        return;
                    }
                    // Failure before the apply stage was reached (prepare, or the barrier) still fails the
                    // reload exactly like it used to; a failure of the deferred parse itself cannot, the
                    // reload is long over by then, so it is logged instead.
                    if (!parked.completeExceptionally(error))
                    {
                        Log.getLogger().error("[{}]: deferred datapack parse failed.", fabricId, error);
                    }
                });

                return parked;
            }
        });
    }

    /**
     * Client-only half.
     */
    @Environment(EnvType.CLIENT)
    public static final class Client
    {
        private Client()
        {
            throw new IllegalStateException("Tried to initialize: FMLEventHandler.Client but this is a Utility class.");
        }

        /**
         * Installs the client callbacks. Called once from the client initializer.
         */
        public static void register()
        {
            // was: @SubscribeEvent onClientTick(ClientTickEvent.Pre)
            ClientTickEvents.START_CLIENT_TICK.register(client -> IColonyManager.getInstance().onClientTick());
        }
    }
}
