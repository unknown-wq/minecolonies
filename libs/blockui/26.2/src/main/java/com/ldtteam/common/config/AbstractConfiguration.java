package com.ldtteam.common.config;

import com.ldtteam.common.config.ConfigValue.BooleanValue;
import com.ldtteam.common.config.ConfigValue.Builder;
import com.ldtteam.common.config.ConfigValue.DoubleValue;
import com.ldtteam.common.config.ConfigValue.EnumValue;
import com.ldtteam.common.config.ConfigValue.IntValue;
import com.ldtteam.common.config.ConfigValue.LongValue;
import com.ldtteam.common.config.ConfigValue.RestartType;
import com.ldtteam.common.language.LanguageHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Root of a mod's configuration tree.
 * <p>
 * <b>Port contract K4 (a §10 cut), partially restored.</b> The NeoForge {@code ModConfigSpec} builder this class
 * used to wrap has no Fabric or vanilla counterpart, so every {@code defineXxx} mints a {@link ConfigValue}
 * holding the old TOML default directly. All the {@code defineXxx} / {@code addWatcher} signatures and the
 * {@code XXX.get()} call sites are unchanged.
 * <p>
 * Every minted value is registered with the {@link ConfigStore} that {@link Configurations} put in the
 * {@link Builder}, so the tree is loaded from and saved to {@code config/<modid>-<type>.toml}, and a SERVER
 * configuration is shipped to joining clients again ({@link ConfigSyncManager}). What is still lost is per-world
 * server configs, range/element validation at <em>definition</em> time and the generated config screen.
 *
 * @see ConfigValue
 * @see ConfigStore
 * @see ConfigSyncManager
 */
public abstract class AbstractConfiguration
{
    public static final String DEFAULT_KEY_PREFIX = "blockui.config.default.";
    public static final String COMMENT_SUFFIX = ".comment";

    final List<ConfigWatcher<?>> watchers = new ArrayList<>();

    /**
     * Where {@link #build} registers what it mints. Null when this configuration was constructed with the public
     * no-arg {@link Builder}, i.e. outside {@link Configurations}; then nothing is persisted, as before.
     */
    @Nullable
    private final ConfigStore store;

    private final String modId;

    private final Deque<String> categories = new ArrayDeque<>();

    private RestartType nextRestartType = RestartType.NONE;

    protected AbstractConfiguration(final Builder builder, final String modId)
    {
        this.store = builder.store();
        this.modId = modId;

        // the store is created before the mod id is known - Configurations builds it, but only this subclass
        // knows which mod it is - so this is where the file name gets resolved
        if (store != null)
        {
            store.bindModId(modId);
        }
    }

    protected void createCategory(final String key)
    {
        if (nextRestartType != RestartType.NONE)
        {
            throw new IllegalStateException("Categories cannot have worldRestart flag!");
        }
        categories.addLast(key);
    }

    protected void swapToCategory(final String key)
    {
        finishCategory();
        createCategory(key);
    }

    protected void finishCategory()
    {
        categories.pollLast();
    }

    private String path(final String key)
    {
        return categories.isEmpty() ? key : String.join(".", categories) + "." + key;
    }

    private String nameTKey(final String key)
    {
        return modId + ".config." + key;
    }

    private String commentTKey(final String key)
    {
        return nameTKey(key) + COMMENT_SUFFIX;
    }

    /**
     * Everything must call this in the end - it consumes the pending restart flag, mints the value and hands it
     * to the store that will persist it.
     */
    private <T, C extends ConfigValue<T>> C build(final String key,
        @Nullable final String defaultDesc,
        final ValueFactory<T, C> factory)
    {
        // the config file is not watched, so a reload can never require a restart: the flag is still only
        // consumed, never acted upon
        nextRestartType = RestartType.NONE;

        String comment = translate(commentTKey(key));
        if (defaultDesc != null && !defaultDesc.isBlank())
        {
            comment += " " + defaultDesc;
        }

        final C value = factory.create(path(key), nameTKey(key), comment);
        if (store != null)
        {
            store.register(value);
        }
        return value;
    }

    private static String translate(final String key, final Object... args)
    {
        final String translated = LanguageHandler.translateKey(key);
        return args.length == 0 ? translated : translated.formatted(args);
    }

    protected AbstractConfiguration requiresWorldRestart()
    {
        return requires(RestartType.WORLD);
    }

    protected AbstractConfiguration requiresGameRestart()
    {
        return requires(RestartType.GAME);
    }

    protected AbstractConfiguration requires(final RestartType restartType)
    {
        nextRestartType = restartType;
        return this;
    }

    protected BooleanValue defineBoolean(final String key, final boolean defaultValue)
    {
        return build(key,
            translate(DEFAULT_KEY_PREFIX + "boolean", defaultValue),
            (p, t, c) -> new BooleanValue(p, t, c, defaultValue));
    }

    protected IntValue defineInteger(final String key, final int defaultValue)
    {
        return defineInteger(key, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    protected IntValue defineInteger(final String key, final int defaultValue, final int min, final int max)
    {
        checkRange(key, defaultValue, min, max);
        return build(key,
            translate(DEFAULT_KEY_PREFIX + "number", defaultValue, min, max),
            (p, t, c) -> new IntValue(p, t, c, defaultValue, min, max));
    }

    protected ConfigValue<String> defineString(final String key, final String defaultValue)
    {
        return this.<String, ConfigValue<String>>build(key,
            translate(DEFAULT_KEY_PREFIX + "string", defaultValue),
            (p, t, c) -> new ConfigValue<>(p, t, c, defaultValue));
    }

    protected LongValue defineLong(final String key, final long defaultValue)
    {
        return defineLong(key, defaultValue, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    protected LongValue defineLong(final String key, final long defaultValue, final long min, final long max)
    {
        checkRange(key, defaultValue, min, max);
        return build(key,
            translate(DEFAULT_KEY_PREFIX + "number", defaultValue, min, max),
            (p, t, c) -> new LongValue(p, t, c, defaultValue, min, max));
    }

    protected DoubleValue defineDouble(final String key, final double defaultValue)
    {
        // NOT Double.MIN_VALUE: that is the smallest *positive* double (4.9E-324), so using it as a lower bound
        // made DoubleValue#set clamp every zero/negative assignment up to 4.9E-324. -Double.MAX_VALUE is the most
        // negative finite double and the symmetric counterpart of the Double.MAX_VALUE upper bound; infinities are
        // deliberately not used, as min/max are also rendered into the user facing default description above.
        return defineDouble(key, defaultValue, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    protected DoubleValue defineDouble(final String key, final double defaultValue, final double min, final double max)
    {
        checkRange(key, defaultValue, min, max);
        return build(key,
            translate(DEFAULT_KEY_PREFIX + "number", defaultValue, min, max),
            (p, t, c) -> new DoubleValue(p, t, c, defaultValue, min, max));
    }

    /**
     * Integral range guard, also used for {@code int} through widening.
     * <p>
     * The numeric {@link ConfigValue} subclasses clamp in {@code set}, so a default outside its own declared range
     * would be silently rewritten. Failing here instead turns that into a hard error during mod init.
     */
    private static void checkRange(final String key, final long defaultValue, final long min, final long max)
    {
        if (min > max || defaultValue < min || defaultValue > max)
        {
            throw new IllegalArgumentException(
                "Config '" + key + "': default " + defaultValue + " is outside of range [" + min + ", " + max + "]");
        }
    }

    /**
     * Floating point range guard. Written as negated positive assertions so that a NaN default or bound is rejected
     * as well - {@code Math.clamp} would otherwise let it through.
     */
    private static void checkRange(final String key, final double defaultValue, final double min, final double max)
    {
        if (!(min <= max) || !(defaultValue >= min && defaultValue <= max))
        {
            throw new IllegalArgumentException(
                "Config '" + key + "': default " + defaultValue + " is outside of range [" + min + ", " + max + "]");
        }
    }

    /**
     * @deprecated by neo, potentially forRemoval?
     * @see #defineList(String, Supplier, Predicate, Object...)
     */
    @Deprecated(since = "1.21")
    protected <T> ConfigValue<List<? extends T>> defineList(final String key,
        final List<? extends T> defaultValue,
        final Predicate<Object> elementValidator)
    {
        return defineListInternal(key, defaultValue);
    }

    protected <T> ConfigValue<List<? extends T>> defineList(final String key,
        final Supplier<T> newUiInstance,
        final Predicate<Object> elementValidator,
        final List<? extends T> defaultValue)
    {
        return defineListInternal(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    protected <T> ConfigValue<List<? extends T>> defineList(final String key,
        final Supplier<T> newUiInstance,
        final Predicate<Object> elementValidator,
        final T... values)
    {
        return defineListInternal(key, List.of(values));
    }

    /**
     * @deprecated by neo, potentially forRemoval?
     * @see #defineListAllowEmpty(String, Supplier, Predicate, Object...)
     */
    @Deprecated(since = "1.21")
    protected <T> ConfigValue<List<? extends T>> defineListAllowEmpty(final String key,
        final List<? extends T> defaultValue,
        final Predicate<Object> elementValidator)
    {
        return defineListInternal(key, defaultValue);
    }

    protected <T> ConfigValue<List<? extends T>> defineListAllowEmpty(final String key,
        final Supplier<T> newUiInstance,
        final Predicate<Object> elementValidator,
        final List<? extends T> defaultValue)
    {
        return defineListInternal(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    protected <T> ConfigValue<List<? extends T>> defineListAllowEmpty(final String key,
        final Supplier<T> newUiInstance,
        final Predicate<Object> elementValidator,
        final T... values)
    {
        return defineListInternal(key, List.of(values));
    }

    private <T> ConfigValue<List<? extends T>> defineListInternal(final String key, final List<? extends T> defaultValue)
    {
        return this.<List<? extends T>, ConfigValue<List<? extends T>>>build(key,
            null,
            (p, t, c) -> new ConfigValue<>(p, t, c, defaultValue));
    }

    protected <V extends Enum<V>> EnumValue<V> defineEnum(final String key, final V defaultValue)
    {
        return build(key,
            translate(DEFAULT_KEY_PREFIX + "enum",
                defaultValue,
                Arrays.stream(defaultValue.getDeclaringClass().getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "))),
            (p, t, c) -> new EnumValue<>(p, t, c, defaultValue));
    }

    protected <T> void addWatcher(final ConfigValue<T> configValue, final ConfigListener<T> listener)
    {
        watchers.add(new ConfigWatcher<>(listener, configValue));
    }

    @SuppressWarnings("unchecked")
    protected void addWatcher(final Runnable listener, final ConfigValue<?>... configValues)
    {
        final ConfigListener<Object> typedListener = (o, n) -> listener.run();
        for (final ConfigValue<?> c : configValues)
        {
            watchers.add(new ConfigWatcher<>(typedListener, (ConfigValue<Object>) c));
        }
    }

    @FunctionalInterface
    private interface ValueFactory<T, C extends ConfigValue<T>>
    {
        C create(String path, String translationKey, @Nullable String comment);
    }

    @FunctionalInterface
    public static interface ConfigListener<T>
    {
        /**
         * @param oldValue old config value
         * @param newValue new/current config value
         */
        void onChange(T oldValue, T newValue);
    }

    /**
     * synchronized due to nature of config events
     */
    static class ConfigWatcher<T>
    {
        private final ConfigListener<T> listener;
        private final ConfigValue<T> forgeConfig;

        @Nullable
        private T lastValue;

        private ConfigWatcher(final ConfigListener<T> listener, final ConfigValue<T> forgeConfig)
        {
            this.listener = listener;
            this.forgeConfig = forgeConfig;
        }

        boolean isSameForgeConfig(final ConfigValue<?> other)
        {
            return other == forgeConfig;
        }

        synchronized void cacheLastValue()
        {
            lastValue = forgeConfig.get();
        }

        synchronized void compareAndFireChangeEvent()
        {
            final T newValue = forgeConfig.get();

            if (!Objects.equals(newValue, lastValue))
            {
                // §10: NeoForge posted this onto the client executor / server tick queue because config
                // reloads arrived off-thread. Without a config file the only source of a change is an explicit
                // Configurations#set from game code, which already runs on the right thread.
                listener.onChange(lastValue, newValue);
                lastValue = newValue;
            }
        }
    }
}
