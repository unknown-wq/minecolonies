package com.ldtteam.domumornamentum.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Patch builder with update support.
 * <p>
 * Used to extend {@link DataComponentPatch.Builder}; in 26.2 both its constructor and its {@code map}
 * field are private (they were reachable through NeoForge's access transformers), so this keeps its
 * own map and only touches the vanilla builder inside {@link #build()}.
 */
public class DataComponentPatchBuilder
{
    private final Map<DataComponentType<?>, Optional<?>> map = new LinkedHashMap<>();

    public DataComponentPatchBuilder()
    {
    }

    // NOTE(port-26.2): the Supplier<DataComponentType<T>> overloads that used to sit next to
    // getOrDefault/update/set are gone. ModDataComponents.ComponentType<T> implements *both*
    // DataComponentType<T> and Supplier<DataComponentType<T>>, which made every call ambiguous.

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(final DataComponentType<T> type, final T defaultValue)
    {
        return ((Optional<T>) map.getOrDefault(type, Optional.empty())).orElse(defaultValue);
    }

    public <T> DataComponentPatchBuilder update(final DataComponentType<T> type, final T defaultValue, final UnaryOperator<T> updater)
    {
        set(type, updater.apply(getOrDefault(type, defaultValue)));
        return this;
    }

    public <T> DataComponentPatchBuilder set(final DataComponentType<T> type, final T value)
    {
        map.put(type, Optional.of(value));
        return this;
    }

    public <T> DataComponentPatchBuilder remove(final DataComponentType<T> type)
    {
        map.put(type, Optional.empty());
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DataComponentPatch build()
    {
        if (map.isEmpty())
        {
            return DataComponentPatch.EMPTY;
        }

        final DataComponentPatch.Builder builder = DataComponentPatch.builder();
        for (final Map.Entry<DataComponentType<?>, Optional<?>> entry : map.entrySet())
        {
            final DataComponentType type = entry.getKey();
            if (entry.getValue().isPresent())
            {
                builder.set(type, entry.getValue().get());
            }
            else
            {
                builder.remove(type);
            }
        }
        return builder.build();
    }
}
