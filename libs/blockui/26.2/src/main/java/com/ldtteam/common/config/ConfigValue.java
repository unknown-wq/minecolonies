package com.ldtteam.common.config;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Stand-in for NeoForge's {@code net.neoforged.neoforge.common.ModConfigSpec.ConfigValue} and friends.
 * <p>
 * <b>Port contract K4 (a §10 cut), partially restored.</b> {@code ModConfigSpec} has no counterpart in Fabric and
 * none in vanilla, so NightConfig, the per-world server config and the generated config screen are still gone.
 * What is kept is exactly the call-site shape the dependent mods rely on - {@code XXX.get()}, {@code XXX.set(v)}
 * and the {@code BooleanValue}/{@code IntValue}/... nesting.
 * <p>
 * Persistence, however, is back: a value minted through {@link AbstractConfiguration} is attached to a
 * {@link ConfigStore}, is initialised from {@code config/<modid>-<type>.toml} at startup and is written back
 * there by {@link #save()}. A value built outside that path - i.e. through the public no-arg
 * {@link Builder} - has no store and stays purely in memory, exactly as before.
 * <p>
 * Server -&gt; client synchronisation is back too, as an <b>overlay</b>: {@link #applySync(Object)} parks the
 * server's value in a second field and {@link #get()} prefers it, while the local value this installation loaded
 * from its own file is left untouched underneath and is what {@link ConfigStore} writes back. Dropping the
 * overlay ({@link #clearSync()}, on disconnect) is therefore all it takes to be back on the local values - there
 * is no snapshot that could be lost to a crashed connection.
 * <p>
 * The nesting mirrors {@code ModConfigSpec} one-to-one, so a dependent mod only has to rewrite the import:
 * {@code net.neoforged.neoforge.common.ModConfigSpec} -&gt; {@code com.ldtteam.common.config.ConfigValue}.
 *
 * @param <T> value type
 * @see ConfigStore
 */
public class ConfigValue<T> implements Supplier<T>
{
    private final String path;
    private final String translationKey;
    @Nullable
    private final String comment;
    private final T defaultValue;

    /**
     * Volatile: read on the game thread, read again on the config writer thread when the store flushes.
     */
    private volatile T value;

    /**
     * The value the server we are connected to has for this setting, or null when there is no such override -
     * which is always the case on a server, in singleplayer and on a client that is not in a world.
     * <p>
     * Volatile for the same reason as {@link #value}, plus the fact that it is written from the client's network
     * receive path and read from wherever game code calls {@link #get()}.
     *
     * @see ConfigSync
     */
    @Nullable
    private volatile T syncedValue;

    /**
     * The file this value belongs to, or null when it is a purely in-memory value. Attached by
     * {@link ConfigStore#register(ConfigValue)} rather than passed to the constructor, so that the
     * package-private constructors of all five subclasses below stay untouched.
     */
    @Nullable
    private ConfigStore owner;

    ConfigValue(final String path, final String translationKey, @Nullable final String comment, final T defaultValue)
    {
        this.path = path;
        this.translationKey = translationKey;
        this.comment = comment;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    /**
     * @param owner the store that persists this value; may only be set once
     */
    void attach(final ConfigStore owner)
    {
        if (this.owner != null && this.owner != owner)
        {
            throw new IllegalStateException("Config value " + path + " is already owned by " + this.owner);
        }
        this.owner = owner;
    }

    /**
     * Load path: assigns the value without scheduling a write back to the file it just came from, and without
     * going through {@code Configurations#set}, so no watcher fires for it either - {@link ConfigStore#load()}
     * runs before the watchers are primed and is priming the value, not editing it.
     * <p>
     * It does route through {@link #set(Object)}, so the numeric subclasses still clamp an out-of-range value
     * that somebody typed into the file by hand.
     */
    void setRaw(final T newValue)
    {
        set(newValue);
    }

    @Override
    public T get()
    {
        final T synced = syncedValue;
        return synced != null ? synced : value;
    }

    public void set(final T newValue)
    {
        this.value = newValue;
    }

    /**
     * The value of this installation, ignoring any server override. This is what is persisted, what
     * {@link #set(Object)} writes and what {@link #get()} goes back to once the overlay is dropped.
     *
     * @return local value, never null
     */
    T getLocalValue()
    {
        return value;
    }

    /**
     * Hook for the numeric subclasses to clamp into their declared range. Applied by {@link #set(Object)} and by
     * {@link #applySync(Object)}, so a value that arrives from a server whose bounds are wider than ours is
     * clamped exactly like one typed into the config file by hand.
     */
    T sanitise(final T newValue)
    {
        return newValue;
    }

    /**
     * Sync path: parks the connected server's value on top of the local one. The local value is not touched, so
     * nothing about this can reach the config file - see {@link ConfigStore#render()}, which renders
     * {@link #getLocalValue()}.
     *
     * @param  newValue value received from the server, already coerced to this value's type
     * @return          true when {@link #get()} now answers differently, i.e. a watcher has to fire
     */
    boolean applySync(final T newValue)
    {
        Objects.requireNonNull(newValue, "a synced config value is never null");

        final T previous = get();
        syncedValue = sanitise(newValue);
        return !Objects.equals(previous, get());
    }

    /**
     * Drops the server override, on disconnect or when a (re)joined server does not know this key at all.
     *
     * @return true when {@link #get()} now answers differently, i.e. a watcher has to fire
     */
    boolean clearSync()
    {
        if (syncedValue == null)
        {
            return false;
        }

        final T previous = get();
        syncedValue = null;
        return !Objects.equals(previous, get());
    }

    /**
     * @return true while the server this client is connected to is dictating this value, i.e. while {@link #get()}
     *         does <em>not</em> answer with this installation's own setting. Always false on a server and in
     *         singleplayer. Meant for a settings UI, which should not offer to edit such a value.
     */
    public boolean isSynced()
    {
        return syncedValue != null;
    }

    public T getDefault()
    {
        return defaultValue;
    }

    /**
     * Dot separated config path, eg. {@code category.key}.
     */
    public String getPath()
    {
        return path;
    }

    public String getTranslationKey()
    {
        return translationKey;
    }

    @Nullable
    public String getComment()
    {
        return comment;
    }

    /**
     * Persists the current <em>local</em> value. Writes are coalesced by the owning {@link ConfigStore}, so
     * calling this on every frame of a slider drag costs one file write, not one per frame.
     * <p>
     * Does nothing for a value with no store behind it. A server override is never persisted, whether or not
     * this is called while one is in effect.
     */
    public void save()
    {
        final ConfigStore store = owner;
        if (store != null)
        {
            store.markDirty();
        }
    }

    /**
     * No-op, and deliberately so.
     * <p>
     * In NeoForge this dropped a cache in front of the NightConfig file so the next {@link #get()} re-read it.
     * Here the field <i>is</i> the value: the file is read once into it at startup and every change is written
     * back out through {@link #save()}, so there is never a newer value on disk to pick up. Re-reading the file
     * from an arbitrary call site would only be able to undo an edit that has not been flushed yet.
     */
    public void clearCache()
    {
        // Intentionally left empty - see javadoc above.
    }

    @Override
    public String toString()
    {
        final T synced = syncedValue;
        return synced != null ? path + " = " + synced + " (server; local " + value + ")" : path + " = " + value;
    }

    public static class BooleanValue extends ConfigValue<Boolean>
    {
        BooleanValue(final String path, final String translationKey, @Nullable final String comment, final boolean defaultValue)
        {
            super(path, translationKey, comment, defaultValue);
        }

        public boolean getAsBoolean()
        {
            return get();
        }
    }

    public static class IntValue extends ConfigValue<Integer>
    {
        private final int min;
        private final int max;

        IntValue(final String path,
            final String translationKey,
            @Nullable final String comment,
            final int defaultValue,
            final int min,
            final int max)
        {
            super(path, translationKey, comment, defaultValue);
            this.min = min;
            this.max = max;
        }

        /**
         * Kept as an override, rather than folded into {@link #sanitise(Integer)}, so that the erased
         * {@code set(Integer)} entry point a downstream jar was compiled against still exists.
         */
        @Override
        public void set(final Integer newValue)
        {
            super.set(sanitise(newValue));
        }

        @Override
        Integer sanitise(final Integer newValue)
        {
            return Math.clamp(newValue.longValue(), min, max);
        }

        public int getAsInt()
        {
            return get();
        }
    }

    public static class LongValue extends ConfigValue<Long>
    {
        private final long min;
        private final long max;

        LongValue(final String path,
            final String translationKey,
            @Nullable final String comment,
            final long defaultValue,
            final long min,
            final long max)
        {
            super(path, translationKey, comment, defaultValue);
            this.min = min;
            this.max = max;
        }

        /**
         * @see IntValue#set(Integer)
         */
        @Override
        public void set(final Long newValue)
        {
            super.set(sanitise(newValue));
        }

        @Override
        Long sanitise(final Long newValue)
        {
            return Math.clamp(newValue, min, max);
        }

        public long getAsLong()
        {
            return get();
        }
    }

    public static class DoubleValue extends ConfigValue<Double>
    {
        private final double min;
        private final double max;

        DoubleValue(final String path,
            final String translationKey,
            @Nullable final String comment,
            final double defaultValue,
            final double min,
            final double max)
        {
            super(path, translationKey, comment, defaultValue);
            this.min = min;
            this.max = max;
        }

        /**
         * @see IntValue#set(Integer)
         */
        @Override
        public void set(final Double newValue)
        {
            super.set(sanitise(newValue));
        }

        @Override
        Double sanitise(final Double newValue)
        {
            return Math.clamp(newValue, min, max);
        }

        public double getAsDouble()
        {
            return get();
        }
    }

    public static class EnumValue<V extends Enum<V>> extends ConfigValue<V>
    {
        EnumValue(final String path, final String translationKey, @Nullable final String comment, final V defaultValue)
        {
            super(path, translationKey, comment, defaultValue);
        }
    }

    /**
     * Mirror of {@code ModConfigSpec.RestartType}. Nothing acts on it any more - there is no config file whose
     * reload could require a restart - but it is kept so {@code requires(...)} call sites still compile.
     */
    public enum RestartType
    {
        NONE,
        WORLD,
        GAME;
    }

    /**
     * Mirror of {@code ModConfigSpec.Builder}, reduced to a token: it exists so the
     * {@code MyConfig(Builder builder)} constructor shape of the dependent mods survives.
     * <p>
     * It now also carries the {@link ConfigStore} the configuration being built writes to. That is what keeps
     * persistence off every existing public signature: {@link Configurations} puts the store in here, and
     * {@link AbstractConfiguration#AbstractConfiguration(Builder, String)} takes it back out - unchanged - on
     * the other side.
     */
    public static final class Builder
    {
        @Nullable
        private final ConfigStore store;

        /**
         * Builds values with no file behind them. Kept public and behaviour-compatible for any caller that
         * constructs a configuration outside {@link Configurations}.
         */
        public Builder()
        {
            this(null);
        }

        Builder(@Nullable final ConfigStore store)
        {
            this.store = store;
        }

        @Nullable
        ConfigStore store()
        {
            return store;
        }
    }
}
