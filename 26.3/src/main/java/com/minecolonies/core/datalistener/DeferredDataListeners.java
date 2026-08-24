package com.minecolonies.core.datalistener;

import com.minecolonies.api.util.Log;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.core.RegistryAccess;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the apply half of every datapack reload listener until item components are bound.
 * <p>
 * <b>Why this exists (port note, 26.2).</b> Item data components are no longer attached when the item is
 * registered. {@code Item#<init>} only files an initializer in
 * {@code BuiltInRegistries.DATA_COMPONENT_INITIALIZERS}; the actual binding happens in
 * {@code ReloadableServerResources#updateComponentsAndStaticRegistryTags()}, which vanilla calls
 * <i>after</i> the whole reload -- every reload listener included -- has finished:
 * <ul>
 *     <li>{@code ReloadableServerResources#loadResources} builds the {@code SimpleReloadInstance} over
 *     {@code result.listeners()} and only returns once all of them applied;</li>
 *     <li>{@code MinecraftServer#reloadResources} then calls
 *     {@code this.resources.managers.updateComponentsAndStaticRegistryTags()} ({@code /reload} path);</li>
 *     <li>{@code WorldLoader#load} does the same in its {@code thenApplyAsync} (first world load path).</li>
 * </ul>
 * So no reload listener may touch an {@link net.minecraft.world.item.ItemStack} while it runs: every stack
 * constructor and every {@code ItemStack} codec goes through {@code Item#areComponentsBound()} and blows up
 * with {@code "Item <id> does not have components yet"}. Vanilla sidesteps this by storing item
 * <i>templates</i> in its data model; the MineColonies data model ({@code ItemStorage},
 * {@code IRecipeStorage}, the custom recipes) is built on real {@code ItemStack}s and is not being rewritten.
 * <p>
 * <b>What we do instead.</b> The listeners are left exactly as they are. The wrapper installed in
 * {@code FMLEventHandler#addListener} hands each listener a reload executor that, instead of running the
 * listener's apply stage, parks it here. The queue is then drained from
 * {@link CommonLifecycleEvents#TAGS_LOADED}, which fabric-lifecycle-events injects at the {@code TAIL} of
 * {@code ReloadableServerResources#updateComponentsAndStaticRegistryTags()} -- i.e. the first instruction
 * after components are bound, on both of the paths above and on the server side only.
 * <p>
 * {@code ServerLifecycleEvents.END_DATA_PACK_RELOAD} deliberately is <b>not</b> used for this: Fabric fires it
 * from {@code MinecraftServer#reloadResources} only, so it covers {@code /reload} but not the initial world
 * load, which is exactly the path the dedicated server dies on.
 * <p>
 * Ordering is preserved bit for bit. {@code SimpleReloadInstance#prepareTasks} chains listener N+1's
 * preparation barrier onto listener N's returned future, so the parks happen strictly in registration order,
 * and the queue is drained in the order it was filled.
 */
public final class DeferredDataListeners
{
    /**
     * The parked apply stages, in the order the reload produced them.
     */
    private static final List<Pending> PENDING = new ArrayList<>();

    /**
     * Whether {@link #install()} already ran.
     */
    private static boolean installed = false;

    private DeferredDataListeners()
    {
        throw new IllegalStateException("Tried to initialize: DeferredDataListeners but this is a Utility class.");
    }

    /**
     * Installs the drain hook. Called once from the mod entry point, before the listeners are registered.
     */
    public static void install()
    {
        if (installed)
        {
            return;
        }
        installed = true;

        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client)
            {
                // the client-side firings (handleUpdateTags / handleConfigurationFinished) have nothing to do
                // with a server datapack reload.
                return;
            }
            drain(registries);
        });
    }

    /**
     * Drops anything left over from a previous, unfinished reload. Called from every listener's
     * {@code prepareSharedState}, which {@code SimpleReloadInstance#prepareTasks} runs for all listeners
     * before the first preparation task starts.
     */
    public static synchronized void beginReload()
    {
        PENDING.clear();
    }

    /**
     * Parks one listener's apply stage.
     * <p>
     * Ordering is by {@code order}, not by arrival. Parking happens on the reload executor once a listener's
     * preparation finishes, and preparations run concurrently, so arrival order is a race -- one that really
     * does bite: {@code quests} parked before {@code research} and every quest carrying a
     * {@code minecolonies:research} objective was dropped with "research is null", because the research tree was
     * still empty when the quest was parsed. Registration order is the contract the listeners were written
     * against, so that is what the drain uses.
     *
     * @param name      the listener id, for error reporting.
     * @param order     the listener's registration index.
     * @param applyTask the apply stage as handed to the listener's reload executor.
     */
    public static synchronized void park(@NotNull final String name, final int order, @NotNull final Runnable applyTask)
    {
        PENDING.add(new Pending(name, order, applyTask));
    }

    /**
     * Runs everything that was parked, in order.
     *
     * @param registries the registry access of the resources that were just reloaded.
     */
    private static void drain(@NotNull final RegistryAccess registries)
    {
        final List<Pending> tasks;
        synchronized (DeferredDataListeners.class)
        {
            if (PENDING.isEmpty())
            {
                return;
            }
            tasks = new ArrayList<>(PENDING);
            tasks.sort(java.util.Comparator.comparingInt(Pending::order));
            PENDING.clear();
        }

        // This is the lookup NeoForge's ContextAwareReloadListener#getRegistryLookup() used to inject: the
        // composite access of the resources being reloaded, datapack registries included. It is strictly
        // better than the running server's, and unlike the server it also exists during the first world load.
        DataListenerUtils.setReloadLookup(registries);

        for (final Pending task : tasks)
        {
            try
            {
                task.applyTask().run();
            }
            catch (final Throwable t)
            {
                // The apply stage is a CompletableFuture task and swallows its own throwables into the future
                // the wrapper watches, so this is only a backstop; either way nothing is silenced.
                Log.getLogger().error("[{}]: deferred datapack parse failed.", task.name(), t);
            }
        }
    }

    /**
     * One parked apply stage.
     *
     * @param name      the listener id.
     * @param applyTask the task.
     */
    private record Pending(String name, int order, Runnable applyTask) {}
}
