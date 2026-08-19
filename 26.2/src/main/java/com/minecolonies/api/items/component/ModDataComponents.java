package com.minecolonies.api.items.component;

import com.minecolonies.api.util.constant.Constants;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

public class ModDataComponents
{
    public static final Wrapper<Timestamp> TIME_COMPONENT =
      savedSynced("timestamp", Timestamp.CODEC, Timestamp.STREAM_CODEC);

    public static final Wrapper<SupplyData> SUPPLY_COMPONENT =
      savedSynced("supplies", SupplyData.CODEC, SupplyData.STREAM_CODEC);

    public static final Wrapper<PatrolTarget> PATROL_TARGET =
      savedSynced("patrol_target", PatrolTarget.CODEC, PatrolTarget.STREAM_CODEC);

    public static final Wrapper<WarehouseSnapshot> WAREHOUSE_SNAPSHOT_COMPONENT =
      savedSynced("warehouse_snapshot", WarehouseSnapshot.CODEC, WarehouseSnapshot.STREAM_CODEC);

    public static final Wrapper<RallyData> RALLY_COMPONENT =
      savedSynced("rally", RallyData.CODEC, RallyData.STREAM_CODEC);

    public static final Wrapper<AdventureData> ADVENTURE_COMPONENT =
      savedSynced("adventure", AdventureData.CODEC, AdventureData.STREAM_CODEC);

    public static final Wrapper<ColonyId> COLONY_ID_COMPONENT =
      savedSynced("colony_id", ColonyId.CODEC, ColonyId.STREAM_CODEC);
    
    public static final Wrapper<BuildingId> HUT_ID_COMPONENT =
      savedSynced("building_id", BuildingId.CODEC, BuildingId.STREAM_CODEC);

    public static final Wrapper<Desc> DESC_COMPONENT =
      savedSynced("desc", Desc.CODEC, Desc.STREAM_CODEC);

    public static final Wrapper<HutBlockData> HUT_COMPONENT =
      savedSynced("hut", HutBlockData.CODEC, HutBlockData.STREAM_CODEC);

    public static final Wrapper<PermissionMode> PERMISSION_MODE =
      savedSynced("perm_mode", PermissionMode.CODEC, PermissionMode.STREAM_CODEC);

    public static final Wrapper<FieldSelection> FIELD_SELECTION =
      savedSynced("field_selection", FieldSelection.CODEC, FieldSelection.STREAM_CODEC);

    private static <D> Wrapper<D> savedSynced(final String name,
      final Codec<D> codec,
      final StreamCodec<? super RegistryFriendlyByteBuf, D> streamCodec)
    {
        final DataComponentType<D> built = DataComponentType.<D>builder().persistent(codec).networkSynchronized(streamCodec).build();
        final Wrapper<D> wrapper = new Wrapper<>(built);
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name), wrapper);
        return wrapper;
    }

    /**
     * A component type that is also a {@link Supplier} of itself.
     * <p>
     * The mod writes both {@code ModDataComponents.X} and {@code ModDataComponents.X.get()} at different call sites,
     * and vanilla's {@code ItemStack#get/set} only take the raw {@link DataComponentType} — NeoForge's
     * {@code Supplier} overloads do not exist. Registering this wrapper makes both spellings compile unchanged
     * (PORTING-BUNDLE, "Обёртка DataComponentType + Supplier").
     *
     * @param <D> the component value type.
     */
    public static final class Wrapper<D> implements DataComponentType<D>, Supplier<DataComponentType<D>>
    {
        private final DataComponentType<D> delegate;

        Wrapper(final DataComponentType<D> delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public DataComponentType<D> get()
        {
            return this;
        }

        @Override
        public Codec<D> codec()
        {
            return this.delegate.codec();
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, D> streamCodec()
        {
            return this.delegate.streamCodec();
        }

        @Override
        public boolean ignoreSwapAnimation()
        {
            return this.delegate.ignoreSwapAnimation();
        }
    }

    /**
     * Class-load hook — registration happens eagerly in the static initialisers above (contract C1).
     */
    public static void init()
    {
    }
}
