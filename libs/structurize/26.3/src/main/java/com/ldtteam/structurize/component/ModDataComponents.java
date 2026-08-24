package com.ldtteam.structurize.component;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.items.AbstractItemWithPosSelector.PosSelection;
import com.ldtteam.structurize.items.ItemTagTool.TagData;
import com.ldtteam.structurize.util.ScanToolData;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Data component types of Structurize.
 *
 * <p>Port note (contract C1): NeoForge returned a {@code DeferredHolder} here and patched
 * {@code ItemStack}/{@code Item.Properties} to accept it. Vanilla only accepts the {@link DataComponentType}
 * itself, and no call site of these fields ever used {@code .get()}, so the fields simply became the
 * component types — every existing usage compiles unchanged.</p>
 *
 * <p>Public API: dependent mods that need to enumerate every component type Structurize registers (for
 * example to strip them out of an item stack before comparing recipes) must use {@link #all()}. It replaces
 * the NeoForge-only {@code REGISTRY.getEntries()} walk over the {@code DeferredRegister}, which has no
 * equivalent in vanilla registration.</p>
 */
public class ModDataComponents
{
    /**
     * Every component type created by {@link #savedSynced}, in declaration order. Populated by the act of
     * creating a component, so it can never fall behind the field list.
     */
    private static final List<DataComponentType<?>> ALL = new ArrayList<>();

    /**
     * Immutable view handed out by {@link #all()}. {@code ALL} is only ever appended to from the static
     * initialiser below, so a single wrapper allocated here stays correct forever.
     */
    private static final Collection<DataComponentType<?>> ALL_VIEW = Collections.unmodifiableCollection(ALL);

    public static final DataComponentType<PosSelection> POS_SELECTION =
        savedSynced("pos_selection", PosSelection.CODEC, PosSelection.STREAM_CODEC);
    public static final DataComponentType<TagData> TAGS_DATA =
        savedSynced("tags", TagData.CODEC, TagData.STREAM_CODEC);
    public static final DataComponentType<ScanToolData> SCAN_TOOL =
        savedSynced("scan_tool", ScanToolData.CODEC, ScanToolData.STREAM_CODEC);
    public static final DataComponentType<CapturedBlock> CAPTURED_BLOCK =
        savedSynced("captured_block", CapturedBlock.CODEC, CapturedBlock.STREAM_CODEC);

    /**
     * Forces the static initialiser. Must run before {@link com.ldtteam.structurize.items.ModItems#init()}:
     * item properties reference these component types while the items are being constructed.
     */
    public static void init()
    {
        // intentionally empty
    }

    /**
     * Every data component type registered by Structurize.
     *
     * <p>Public API, intended for dependent mods: the replacement for the NeoForge
     * {@code ModDataComponents.REGISTRY.getEntries()} walk. Typical use is stripping all of Structurize's
     * components off a stack before an equality/recipe comparison:</p>
     *
     * <pre>{@code
     * ModDataComponents.all().forEach(typesToRemove::add);
     * }</pre>
     *
     * <p>The collection is filled by {@link #savedSynced} itself, so a component added to this class in a
     * future version shows up here without any caller change. Touching this method forces the static
     * initialiser, hence the returned collection is never empty and never partially filled.</p>
     *
     * @return unmodifiable view over all component types, in declaration order.
     */
    public static Collection<DataComponentType<?>> all()
    {
        return ALL_VIEW;
    }

    private static <D> DataComponentType<D> savedSynced(final String name,
        final Codec<D> codec,
        final StreamCodec<RegistryFriendlyByteBuf, D> streamCodec)
    {
        final DataComponentType<D> type = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Constants.resLocStruct(name),
            DataComponentType.<D>builder().persistent(codec).networkSynchronized(streamCodec).build());
        // registering here, and not in a hand written list, is what keeps all() from going stale
        ALL.add(type);
        return type;
    }
}
