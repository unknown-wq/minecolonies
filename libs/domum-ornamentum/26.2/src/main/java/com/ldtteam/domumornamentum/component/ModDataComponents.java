package com.ldtteam.domumornamentum.component;

import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.util.Constants;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.function.Supplier;

public class ModDataComponents
{
    public static ComponentType<MaterialTextureData> TEXTURE_DATA =
        savedSynced("texture_data", MaterialTextureData.CODEC, MaterialTextureData.STREAM_CODEC);

    /**
     * Class-load hook — registration happens eagerly in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }

    private static <D> ComponentType<D> savedSynced(final String name,
        final Codec<D> codec,
        final StreamCodec<RegistryFriendlyByteBuf, D> streamCodec)
    {
        final ComponentType<D> value = new ComponentType<>(
            DataComponentType.<D>builder().persistent(codec).networkSynchronized(streamCodec).build());
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Constants.resLocDO(name), value);
    }

    /**
     * A registered component type that is <em>both</em> a {@link DataComponentType} and a
     * {@link Supplier} of itself.
     *
     * <p>NeoForge's {@code DeferredHolder} is a {@code Supplier}, and NeoForge additionally patches
     * {@code ItemStack}/{@code DataComponentHolder} with {@code Supplier<DataComponentType<T>>} overloads of
     * {@code get}/{@code set}/{@code getOrDefault}. Vanilla has neither, so this codebase contains both
     * {@code TEXTURE_DATA.get()} call sites (datagen) and {@code componentBuilder.set(TEXTURE_DATA, …)} call
     * sites (block entities). Implementing both interfaces keeps every one of them compiling untouched —
     * the recipe from {@code porting-26.2/NOTES-A.md §1}.</p>
     *
     * <p>{@link #get()} deliberately returns {@code this}: the object that lives in
     * {@link BuiltInRegistries#DATA_COMPONENT_TYPE} is the wrapper, not the delegate, and component lookup is
     * identity-based.</p>
     */
    public static final class ComponentType<D> implements DataComponentType<D>, Supplier<DataComponentType<D>>
    {
        private final DataComponentType<D> delegate;

        private ComponentType(final DataComponentType<D> delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Codec<D> codec()
        {
            return delegate.codec();
        }

        @Override
        public boolean ignoreSwapAnimation()
        {
            return delegate.ignoreSwapAnimation();
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, D> streamCodec()
        {
            return delegate.streamCodec();
        }

        @Override
        public DataComponentType<D> get()
        {
            return this;
        }

        @Override
        public String toString()
        {
            return String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(this));
        }
    }
}
