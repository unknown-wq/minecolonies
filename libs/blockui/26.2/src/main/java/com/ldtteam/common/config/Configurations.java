package com.ldtteam.common.config;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.common.config.AbstractConfiguration.ConfigWatcher;
import com.ldtteam.common.config.ConfigValue.Builder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Mod root configuration.
 * <p>
 * <b>Port contract K4 (a §10 cut), partially restored.</b> NeoForge's {@code ModConfig} /
 * {@code ConfigTracker} / {@code ModConfigEvent} have no Fabric or vanilla counterpart, so each of the three
 * configuration objects gets a {@link ConfigStore} instead: a plain {@code config/<modid>-<type>.toml} file that
 * is read once when the tree is built and written back, debounced, whenever a value changes.
 * <p>
 * That replaces the NeoForge lifecycle as follows:
 * <ul>
 * <li>{@code ModConfigEvent.Loading} -&gt; {@link ConfigStore#load()} immediately after the configuration object
 * is constructed, and always <em>before</em> the watchers are primed - otherwise the watchers would cache the
 * compiled-in defaults and the first genuine on-disk value would never fire a listener;</li>
 * <li>{@code ModConfigEvent.Reloading} -&gt; {@link #applyServerSync(String)} / {@link #revertServerSync()}. The
 * file is not watched, so the only two things that can change a value behind game code's back are an explicit
 * {@link #set(ConfigValue, Object)} and a server dictating its SERVER config to us; both fire the watchers;</li>
 * <li>{@code ConfigTracker} login sync -&gt; {@link ConfigSyncManager}, restored. What is still cut is the
 * <i>per-world</i> server config: the file lives in {@code config/}, so it is one per installation rather than
 * one per world.</li>
 * </ul>
 * The {@code ModContainer} / {@code IEventBus} constructor parameters remain gone.
 */
public class Configurations<CLIENT extends AbstractConfiguration,
    SERVER extends AbstractConfiguration,
    COMMON extends AbstractConfiguration>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Configurations.class);

    /**
     * Loaded clientside, not synced
     */
    @Nullable
    private final CLIENT clientConfig;

    /**
     * Loaded serverside (per world), synced on connection
     */
    @Nullable
    private final SERVER serverConfig;

    /**
     * Loaded both sides, not synced
     */
    @Nullable
    private final COMMON commonConfig;

    private final AbstractConfiguration[] activeConfigs;

    private final ConfigStore[] stores;

    /**
     * The store behind {@link #serverConfig}, i.e. the only one that takes part in the login sync. Null when this
     * mod has no server configuration.
     */
    @Nullable
    private final ConfigStore serverStore;

    /**
     * Builds configuration tree. The file each configuration is persisted to is named after the mod id its
     * {@link AbstractConfiguration} reports, i.e. the one it passes to
     * {@link AbstractConfiguration#AbstractConfiguration(Builder, String)}.
     *
     * @param clientFactory client config factory, may be null
     * @param serverFactory server config factory, may be null
     * @param commonFactory common config factory, may be null
     */
    public Configurations(@Nullable final Function<Builder, CLIENT> clientFactory,
        @Nullable final Function<Builder, SERVER> serverFactory,
        @Nullable final Function<Builder, COMMON> commonFactory)
    {
        this(null, clientFactory, serverFactory, commonFactory);
    }

    /**
     * Builds configuration tree, naming the files after an explicit mod id.
     *
     * @param modId         mod id used for {@code config/<modId>-<type>.toml}; when null it is taken from the
     *                      configuration objects themselves
     * @param clientFactory client config factory, may be null
     * @param serverFactory server config factory, may be null
     * @param commonFactory common config factory, may be null
     */
    public Configurations(@Nullable final String modId,
        @Nullable final Function<Builder, CLIENT> clientFactory,
        @Nullable final Function<Builder, SERVER> serverFactory,
        @Nullable final Function<Builder, COMMON> commonFactory)
    {
        final List<AbstractConfiguration> configs = new ArrayList<>();
        final List<ConfigStore> builtStores = new ArrayList<>();

        // dont create client classes on server to avoid class loading issues
        clientConfig = BlockUI.isClient()
            ? createConfig(clientFactory, ConfigStore.Type.CLIENT, modId, configs, builtStores)
            : null;
        serverConfig = createConfig(serverFactory, ConfigStore.Type.SERVER, modId, configs, builtStores);
        commonConfig = createConfig(commonFactory, ConfigStore.Type.COMMON, modId, configs, builtStores);

        activeConfigs = configs.toArray(AbstractConfiguration[]::new);
        stores = builtStores.toArray(ConfigStore[]::new);
        serverStore = findStore(ConfigStore.Type.SERVER);

        // every store has already been loaded by createConfig, so the watchers cache what is actually on disk
        // and the first genuine change still fires a listener
        for (final AbstractConfiguration cfg : activeConfigs)
        {
            cfg.watchers.forEach(ConfigWatcher::cacheLastValue);
        }

        registerShutdownFlush();

        // makes this tree visible to the login sync; a plain map put, so the order in which mods build their
        // configurations does not matter
        ConfigSyncManager.register(this);
    }

    @Nullable
    private ConfigStore findStore(final ConfigStore.Type type)
    {
        for (final ConfigStore store : stores)
        {
            if (store.getType() == type)
            {
                return store;
            }
        }
        return null;
    }

    @Nullable
    private <T extends AbstractConfiguration> T createConfig(@Nullable final Function<Builder, T> factory,
        final ConfigStore.Type type,
        @Nullable final String modId,
        final List<AbstractConfiguration> configs,
        final List<ConfigStore> builtStores)
    {
        if (factory == null)
        {
            return null;
        }

        final ConfigStore store = new ConfigStore(type);
        if (modId != null)
        {
            // an explicit id wins; otherwise AbstractConfiguration's constructor binds the one it was given
            store.bindModId(modId);
        }

        final T config = factory.apply(new Builder(store));
        configs.add(config);
        builtStores.add(store);

        // must happen here, before the caller primes the watchers
        store.load();

        return config;
    }

    /**
     * Writes every pending change of every configuration to disk, right now. Called automatically on shutdown;
     * exposed for a mod that wants a hard flush at some other point.
     */
    public void saveAll()
    {
        for (final ConfigStore store : stores)
        {
            store.flush();
        }
    }

    /**
     * The debounced writer can be up to {@link ConfigStore#DEBOUNCE_MILLIS} behind, so the last edits of a
     * session need a guaranteed flush. Both lifecycle events exist in fabric-api 0.154.2; the JVM hook is the
     * belt to their braces and also covers a mod that never opens a world.
     */
    private void registerShutdownFlush()
    {
        try
        {
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> saveAll());
            if (BlockUI.isClient())
            {
                // client-only class, kept behind the bouncer so a dedicated server never loads it
                ClientConfigHelper.registerClient(this::saveAll);
            }
        }
        catch (final RuntimeException | LinkageError e)
        {
            // no Fabric API around (datagen, unit tests) - the shutdown hook below still covers us
            LOGGER.warn("Could not hook the config flush onto the game lifecycle", e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveAll, "ldtteam-config-flush"));
    }

    @Nullable
    public CLIENT getClient()
    {
        return clientConfig;
    }

    @Nullable
    public SERVER getServer()
    {
        return serverConfig;
    }

    @Nullable
    public COMMON getCommon()
    {
        return commonConfig;
    }

    /**
     * Setter wrapper so watchers are fine. This should be called from any code that manually changes ConfigValues using set functions.
     * (Mostly done by settings UIs)
     * <p>
     * On a client connected to a remote server this edits, and persists, the <em>local</em> value of a server
     * config value; {@link ConfigValue#get()} keeps answering with the server's until the connection ends. A
     * settings UI should therefore skip - or at least mark - a value that reports
     * {@link ConfigValue#isSynced()}.
     */
    public <T> void set(final ConfigValue<T> configValue, final T value)
    {
        configValue.set(value);
        configValue.save();
        onConfigValueEdit(configValue);
    }

    // =============== SERVER -> CLIENT SYNC ===============

    /**
     * @return the mod this tree belongs to, or null when nothing bound one (no configuration at all, or a tree
     *         built through the public no-arg {@link Builder})
     */
    @Nullable
    String getModId()
    {
        for (final ConfigStore store : stores)
        {
            if (store.getModId() != null)
            {
                return store.getModId();
            }
        }
        return null;
    }

    /**
     * Server side: renders the SERVER configuration for a joining client.
     *
     * @return flat TOML document, or null when this mod has no server configuration or it is empty
     * @see    ConfigSync#encode(java.util.Collection)
     */
    @Nullable
    String snapshotServerConfigForSync()
    {
        if (serverStore == null || serverStore.getValues().isEmpty())
        {
            return null;
        }
        return ConfigSync.encode(serverStore.getValues());
    }

    /**
     * Client side: puts the connected server's SERVER config on top of our own and fires the watchers of
     * everything that actually changed.
     * <p>
     * The local values are not touched - the sync is an overlay, see {@link ConfigValue#applySync(Object)} - so
     * nothing here can reach {@code config/<modid>-server.toml}, and {@link #revertServerSync()} is a complete
     * undo without a snapshot to keep.
     *
     * @param document what the server sent
     */
    void applyServerSync(final String document)
    {
        if (serverStore == null)
        {
            LOGGER.warn("Ignoring a server config sync for '{}': this installation has no server configuration",
                getModId());
            return;
        }

        final ConfigSync.Outcome outcome = ConfigSync.apply(serverStore.getValues(), document);
        outcome.problems().forEach(problem -> LOGGER.warn("Server config sync for '{}': {}", getModId(), problem));

        LOGGER.info("Applied the server's configuration for '{}': {} value(s) taken from the server, "
            + "{} kept locally (the server did not send them), {} changed",
            getModId(),
            outcome.applied(),
            outcome.missingRemotely(),
            outcome.changed().size());

        fireWatchersFor(outcome.changed());
    }

    /**
     * Client side: back to this installation's own values, and fire the watchers of everything that thereby
     * changed. Called on disconnect, however that disconnect came about.
     */
    void revertServerSync()
    {
        if (serverStore == null)
        {
            return;
        }

        final List<ConfigValue<?>> changed = ConfigSync.revert(serverStore.getValues());
        if (!changed.isEmpty())
        {
            LOGGER.info("Dropped the server's configuration for '{}'; {} value(s) are back on the local setting",
                getModId(),
                changed.size());
        }
        fireWatchersFor(changed);
    }

    /**
     * @return true while a server is dictating any of this mod's server config values
     */
    public boolean isSyncedFromServer()
    {
        return serverStore != null && ConfigSync.isAnySynced(serverStore.getValues());
    }

    /**
     * Fires exactly the watchers that are attached to one of the given values, and only if the value really did
     * change - {@link ConfigWatcher#compareAndFireChangeEvent()} compares against what it last saw.
     * <p>
     * Watchers are looked up across all three configurations rather than only the server one, because
     * {@code addWatcher} is free to be called on any of them.
     */
    private void fireWatchersFor(final List<ConfigValue<?>> changed)
    {
        if (changed.isEmpty())
        {
            return;
        }

        for (final AbstractConfiguration cfg : activeConfigs)
        {
            for (final ConfigWatcher<?> configWatcher : cfg.watchers)
            {
                for (final ConfigValue<?> value : changed)
                {
                    if (configWatcher.isSameForgeConfig(value))
                    {
                        configWatcher.compareAndFireChangeEvent();
                        break;
                    }
                }
            }
        }
    }

    /**
     * This should be called from any code that manually changes ConfigValues using set functions. (Mostly done by settings UIs)
     *
     * @param configValue which config value was changed
     */
    public void onConfigValueEdit(final ConfigValue<?> configValue)
    {
        for (final AbstractConfiguration cfg : activeConfigs)
        {
            for (final ConfigWatcher<?> configWatcher : cfg.watchers)
            {
                if (configWatcher.isSameForgeConfig(configValue))
                {
                    configWatcher.compareAndFireChangeEvent();
                }
            }
        }

        // a server config edited while a world is running has to reach the clients that were told the old value
        // at login; on a remote client there is no running server, so this does nothing
        if (serverStore != null && serverStore.owns(configValue))
        {
            ConfigSyncManager.resyncToConnectedPlayers(this);
        }
    }
}
