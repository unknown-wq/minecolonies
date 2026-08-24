package com.ldtteam.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a value read out of the config file into the type a {@link ConfigValue} was declared with.
 * <p>
 * {@link FlatToml} only knows TOML's types - {@code Boolean}, {@code Long}, {@code Double}, {@code String} and
 * {@code List} - while a {@code defineXxx} may have asked for an {@code Integer} or an enum constant. NightConfig
 * did this conversion inside {@code ModConfigSpec.ValueSpec}; here it is driven off the declared default, which
 * is the only type information a {@link ConfigValue} carries.
 * <p>
 * Every failure is an {@link IllegalArgumentException}: {@link ConfigStore#load()} catches it and keeps the
 * default, so a hand-mangled file costs one setting rather than the game's startup.
 */
final class ConfigCoercion
{
    private ConfigCoercion()
    {
        throw new IllegalStateException("Tried to initialize: ConfigCoercion but this is a Utility class.");
    }

    /**
     * @param  raw          value as parsed out of the file, never null
     * @param  defaultValue the declared default, whose runtime type is the target type
     * @return              raw, converted to the type of defaultValue
     */
    @SuppressWarnings("unchecked")
    static <T> T coerce(final Object raw, final T defaultValue)
    {
        if (defaultValue == null)
        {
            // no type information to go on - hand it back and let the caller's declared type decide
            return (T) raw;
        }

        final Class<?> target = defaultValue instanceof final Enum<?> enumDefault
            ? enumDefault.getDeclaringClass()
            : defaultValue.getClass();

        return (T) coerceTo(raw, target, defaultValue);
    }

    private static Object coerceTo(final Object raw, final Class<?> target, final Object defaultValue)
    {
        if (target == Boolean.class)
        {
            return asBoolean(raw);
        }
        if (target == Integer.class)
        {
            final long value = asLong(raw);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            {
                throw new IllegalArgumentException("'" + raw + "' does not fit in an int");
            }
            return (int) value;
        }
        if (target == Long.class)
        {
            return asLong(raw);
        }
        if (target == Double.class)
        {
            return asDouble(raw);
        }
        if (target == Float.class)
        {
            return (float) asDouble(raw);
        }
        if (target == String.class)
        {
            if (raw instanceof final String s)
            {
                return s;
            }
            if (raw instanceof List)
            {
                throw new IllegalArgumentException("expected a string but found a list");
            }
            return String.valueOf(raw);
        }
        if (Enum.class.isAssignableFrom(target))
        {
            return asEnum(raw, target);
        }
        if (List.class.isAssignableFrom(target))
        {
            return asList(raw, defaultValue);
        }

        if (target.isInstance(raw))
        {
            return raw;
        }
        throw new IllegalArgumentException("cannot convert '" + raw + "' to " + target.getSimpleName());
    }

    private static boolean asBoolean(final Object raw)
    {
        if (raw instanceof final Boolean b)
        {
            return b;
        }
        if (raw instanceof final String s)
        {
            if (s.equalsIgnoreCase("true"))
            {
                return true;
            }
            if (s.equalsIgnoreCase("false"))
            {
                return false;
            }
        }
        throw new IllegalArgumentException("'" + raw + "' is not a boolean");
    }

    private static long asLong(final Object raw)
    {
        if (raw instanceof final Number n)
        {
            if (n instanceof Double || n instanceof Float)
            {
                final double d = n.doubleValue();
                if (d != Math.rint(d) || Double.isNaN(d) || Double.isInfinite(d))
                {
                    throw new IllegalArgumentException("'" + raw + "' is not a whole number");
                }
                return (long) d;
            }
            return n.longValue();
        }
        if (raw instanceof final String s)
        {
            try
            {
                return Long.parseLong(s.trim());
            }
            catch (final NumberFormatException e)
            {
                throw new IllegalArgumentException("'" + raw + "' is not a number");
            }
        }
        throw new IllegalArgumentException("'" + raw + "' is not a number");
    }

    private static double asDouble(final Object raw)
    {
        if (raw instanceof final Number n)
        {
            return n.doubleValue();
        }
        if (raw instanceof final String s)
        {
            try
            {
                return Double.parseDouble(s.trim());
            }
            catch (final NumberFormatException e)
            {
                throw new IllegalArgumentException("'" + raw + "' is not a number");
            }
        }
        throw new IllegalArgumentException("'" + raw + "' is not a number");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object asEnum(final Object raw, final Class<?> target)
    {
        final String name = String.valueOf(raw).trim();
        try
        {
            return Enum.valueOf((Class<? extends Enum>) target, name);
        }
        catch (final IllegalArgumentException exact)
        {
            // NightConfig accepted any casing here, and so do hand-edited files
            for (final Object constant : target.getEnumConstants())
            {
                if (((Enum<?>) constant).name().equalsIgnoreCase(name))
                {
                    return constant;
                }
            }
            throw new IllegalArgumentException("'" + name + "' is not one of " + java.util.Arrays
                .toString(target.getEnumConstants())
                .toLowerCase(Locale.ROOT));
        }
    }

    /**
     * A {@code defineList} erases its element type, so the elements are coerced against the first element of the
     * declared default when there is one, and otherwise left exactly as the file had them.
     */
    private static Object asList(final Object raw, final Object defaultValue)
    {
        if (!(raw instanceof final List<?> rawList))
        {
            throw new IllegalArgumentException("'" + raw + "' is not a list");
        }

        final Object elementPrototype = defaultValue instanceof final List<?> defaultList && !defaultList.isEmpty()
            ? defaultList.get(0)
            : null;
        if (elementPrototype == null)
        {
            return List.copyOf(rawList);
        }

        final List<Object> coerced = new ArrayList<>(rawList.size());
        for (final Object element : rawList)
        {
            coerced.add(coerce(element, elementPrototype));
        }
        return List.copyOf(coerced);
    }
}
