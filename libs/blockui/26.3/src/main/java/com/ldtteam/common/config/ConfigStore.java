package com.ldtteam.common.config;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The backing file for one {@link AbstractConfiguration}, and the piece that was missing from the 26.2 port:
 * without it {@link ConfigValue#save()} had nowhere to write and every setting reverted on restart.
 * <p>
 * One store owns one file, {@code <gamedir>/config/<modid>-<type>.toml}, which is the very path and name
 * NeoForge's {@code ModConfig} used, in the flat TOML dialect {@link FlatToml} documents - so an existing
 * NeoForge installation keeps its settings when it moves to the Fabric build.
 * <p>
 * Lifecycle, mirroring what {@code ModConfigEvent.Loading} used to do:
 * <ol>
 * <li>{@link AbstractConfiguration} registers every value it mints, through {@link #register(ConfigValue)};</li>
 * <li>{@link Configurations} calls {@link #load()} once the configuration object is fully built - and, crucially,
 * <em>before</em> the watchers are primed, so the first on-disk value is what the watchers start from and a
 * spurious change event is not fired for it;</li>
 * <li>every later {@link ConfigValue#save()} marks the store dirty and a debounced writer thread flushes it.</li>
 * </ol>
 * Writes are debounced because the settings UIs drive {@code Configurations#set} from slider drags: without it a
 * single drag is one file write per frame. A flush is additionally forced on world/client shutdown and from a JVM
 * shutdown hook, so at most the last {@value #DEBOUNCE_MILLIS} ms of edits can be lost, and only to a hard kill.
 * <p>
 * Client/server synchronisation lives next door, in {@link ConfigSync}. The one thing it needs from here is the
 * guarantee that a synced value can never reach the file: {@link #render()} deliberately renders
 * {@link ConfigValue#getLocalValue()}, not {@link ConfigValue#get()}, so a flush that happens while a server
 * override is in effect still writes this installation's own settings.
 */
public final class ConfigStore
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigStore.class);

    /**
     * How long a burst of {@link #markDirty()} calls is coalesced before the file is actually written.
     */
    static final int DEBOUNCE_MILLIS = 1000;

    /**
     * One daemon thread for every store of every mod: config writes are tiny, rare and must never hold up the
     * game thread or keep the JVM alive.
     */
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "ldtteam-config-writer");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Mirrors {@code net.neoforged.fml.config.ModConfig.Type}, and therefore the file name suffix.
     */
    public enum Type
    {
        CLIENT,
        SERVER,
        COMMON;

        String fileSuffix()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Type type;

    /**
     * Where the config directory is. Only overridden by tests, which have no {@link FabricLoader}.
     */
    @Nullable
    private final Path configDirOverride;

    /**
     * Insertion ordered so the file comes out in declaration order, like NightConfig's did.
     */
    private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();

    /**
     * Resolved on {@link #bindModId(String)}; null means "nothing to persist to", which is what the public
     * no-arg {@link ConfigValue.Builder} constructor still gives you.
     */
    @Nullable
    private volatile Path file;

    /**
     * Mod this store belongs to; set by {@link #bindModId(String)} even when the file could not be resolved, so
     * that {@link ConfigSync} can still address an in-memory-only configuration.
     */
    @Nullable
    private volatile String modId;

    private volatile boolean dirty = false;
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

    ConfigStore(final Type type)
    {
        this(type, null);
    }

    ConfigStore(final Type type, @Nullable final Path configDirOverride)
    {
        this.type = type;
        this.configDirOverride = configDirOverride;
    }

    /**
     * The mod id is not known when the store is created - {@link Configurations} builds the store, but only the
     * {@link AbstractConfiguration} subclass knows which mod it belongs to, and it says so in its constructor.
     * That is why this is separate from the constructor: it is what lets the whole feature land without changing
     * a single existing public signature. First binding wins; later ones are ignored.
     */
    void bindModId(final String modId)
    {
        if (this.modId != null || modId == null || modId.isBlank())
        {
            return;
        }

        this.modId = modId;

        try
        {
            final Path configDir = configDirOverride != null ? configDirOverride : FabricLoader.getInstance().getConfigDir();
            file = configDir.resolve(modId + "-" + type.fileSuffix() + ".toml");
        }
        catch (final RuntimeException e)
        {
            // e.g. FabricLoader is not initialised (unit tests, datagen) - degrade to in-memory
            LOGGER.warn("Could not resolve the config directory for '{}'; {} config will not be persisted", modId, type, e);
        }
    }

    /**
     * Called for every value {@link AbstractConfiguration} mints.
     */
    void register(final ConfigValue<?> value)
    {
        final ConfigValue<?> previous = values.putIfAbsent(value.getPath(), value);
        if (previous != null)
        {
            LOGGER.warn("Duplicate config path '{}' in the {} config; only the first one is persisted",
                value.getPath(),
                type);
            return;
        }
        value.attach(this);
    }

    /**
     * @return the file this store persists to, or null when there is none (no mod id bound, or no config dir)
     */
    @Nullable
    public Path getFile()
    {
        return file;
    }

    /**
     * @return which of the three configurations this is
     */
    Type getType()
    {
        return type;
    }

    /**
     * @return the mod this store belongs to, or null when nothing was ever bound
     */
    @Nullable
    String getModId()
    {
        return modId;
    }

    /**
     * @return every value registered here, in declaration order; the collection is a live view, do not modify
     */
    Collection<ConfigValue<?>> getValues()
    {
        return values.values();
    }

    /**
     * @param  value any config value
     * @return       true when it was minted into this store
     */
    boolean owns(final ConfigValue<?> value)
    {
        return values.get(value.getPath()) == value;
    }

    /**
     * Reads the file and pushes every recognised entry into its {@link ConfigValue}. Absent, unknown and
     * malformed entries silently keep their default - a corrupt config file must never stop the game from
     * starting - and every one of them is logged.
     */
    void load()
    {
        final Path target = file;
        if (target == null)
        {
            return;
        }

        Map<String, Object> parsed = Map.of();
        if (Files.isRegularFile(target))
        {
            try
            {
                final List<String> problems = new ArrayList<>();
                parsed = FlatToml.parse(Files.readString(target, StandardCharsets.UTF_8), problems);
                problems.forEach(problem -> LOGGER.warn("Ignoring unreadable entry in {}: {}", target, problem));
            }
            catch (final IOException | RuntimeException e)
            {
                LOGGER.warn("Could not read {}; falling back to the defaults for every value in it", target, e);
                parsed = Map.of();
            }
        }

        for (final ConfigValue<?> value : values.values())
        {
            final Object raw = parsed.get(value.getPath());
            if (raw != null)
            {
                apply(value, raw, target);
            }
        }

        // materialise the defaults for a missing file, and add keys the file predates
        markDirty();
    }

    private static <T> void apply(final ConfigValue<T> value, final Object raw, final Path target)
    {
        try
        {
            value.setRaw(ConfigCoercion.coerce(raw, value.getDefault()));
        }
        catch (final RuntimeException e)
        {
            LOGGER.warn("'{}' in {} could not be read ({}); using the default {}",
                value.getPath(),
                target,
                e.getMessage(),
                value.getDefault());
        }
    }

    /**
     * Schedules a write. Repeated calls inside the debounce window collapse into one.
     */
    void markDirty()
    {
        if (file == null)
        {
            return;
        }

        dirty = true;
        if (flushScheduled.compareAndSet(false, true))
        {
            try
            {
                WRITER.schedule(() -> {
                    flushScheduled.set(false);
                    write();
                }, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
            }
            catch (final RuntimeException e)
            {
                // executor already shut down (JVM going down) - write straight away instead of losing it
                flushScheduled.set(false);
                write();
            }
        }
    }

    /**
     * Writes right now, on the calling thread, if anything is pending. Called on shutdown; safe to call at any
     * time.
     */
    public void flush()
    {
        write();
    }

    private synchronized void write()
    {
        final Path target = file;
        if (!dirty || target == null)
        {
            return;
        }

        // cleared first: an edit that lands while we render is either included below or re-flags the store
        dirty = false;

        final Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try
        {
            final Path parent = target.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }

            Files.writeString(tmp, render(), StandardCharsets.UTF_8);
            try
            {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (final AtomicMoveNotSupportedException e)
            {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (final IOException | RuntimeException e)
        {
            dirty = true; // try again on the next edit / on shutdown
            LOGGER.error("Could not write {}", target, e);
            try
            {
                Files.deleteIfExists(tmp);
            }
            catch (final IOException suppressed)
            {
                // nothing useful to do about a leftover temp file
            }
        }
    }

    /**
     * Renders the whole file. Note the {@link ConfigValue#getLocalValue()} below: a value that a server is
     * currently overriding is written out with <em>this installation's</em> setting, never the server's. That is
     * what keeps a session on a remote server from silently rewriting the player's own config file.
     */
    private String render()
    {
        // "" is the root category and must come first; everything else keeps declaration order
        final Map<String, List<ConfigValue<?>>> byCategory = new LinkedHashMap<>();
        byCategory.put("", new ArrayList<>());
        for (final ConfigValue<?> value : values.values())
        {
            final String path = value.getPath();
            final int lastDot = path.lastIndexOf('.');
            byCategory.computeIfAbsent(lastDot < 0 ? "" : path.substring(0, lastDot), key -> new ArrayList<>())
                .add(value);
        }

        final StringBuilder sb = new StringBuilder(4096);
        sb.append("# Written by BlockUI's config store. Edit it while the game is closed - the file is not\n")
            .append("# watched, and the running game overwrites it whenever a setting changes in-game.\n");

        for (final Map.Entry<String, List<ConfigValue<?>>> category : byCategory.entrySet())
        {
            final List<ConfigValue<?>> categoryValues = category.getValue();
            if (categoryValues.isEmpty())
            {
                continue;
            }

            final String name = category.getKey();
            final String indent;
            if (name.isEmpty())
            {
                indent = "";
                sb.append('\n');
            }
            else
            {
                indent = "\t".repeat(countSegments(name));
                sb.append('\n').append("\t".repeat(countSegments(name) - 1)).append('[').append(name).append("]\n");
            }

            for (final ConfigValue<?> value : categoryValues)
            {
                appendComment(sb, indent, value.getComment());
                sb.append(indent)
                    .append(lastSegment(value.getPath()))
                    .append(" = ")
                    .append(FlatToml.format(value.getLocalValue()))
                    .append('\n');
            }
        }

        return sb.toString();
    }

    private static void appendComment(final StringBuilder sb, final String indent, @Nullable final String comment)
    {
        if (comment == null || comment.isBlank())
        {
            return;
        }
        for (final String line : comment.split("\r?\n"))
        {
            sb.append(indent).append('#').append(line.isEmpty() ? "" : " " + line.strip()).append('\n');
        }
    }

    private static int countSegments(final String dottedPath)
    {
        int segments = 1;
        for (int i = 0; i < dottedPath.length(); i++)
        {
            if (dottedPath.charAt(i) == '.')
            {
                segments++;
            }
        }
        return segments;
    }

    private static String lastSegment(final String dottedPath)
    {
        final int lastDot = dottedPath.lastIndexOf('.');
        return lastDot < 0 ? dottedPath : dottedPath.substring(lastDot + 1);
    }

    @Override
    public String toString()
    {
        return "ConfigStore[" + type + " -> " + file + "]";
    }
}
