package com.ldtteam.domumornamentum.item;

import com.ldtteam.domumornamentum.DomumOrnamentum;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;

import static com.ldtteam.domumornamentum.util.Constants.BLOCK_ENTITY_TEXTURE_DATA;
import static com.ldtteam.domumornamentum.util.Constants.TYPE_BLOCK_PROPERTY;

/**
 * BlockItem with own DFU on tag load/set.
 * <p>
 * NOTE(port-26.2): the {@code verifyComponentsAfterLoad(ItemStack)} hook this class was
 * built around is a NeoForge {@code IItemExtension} method. Vanilla 26.2 has no such hook and Fabric's
 * {@code FabricItem} does not add one, so {@link #upgrade(ItemStack)} is no longer called
 * automatically. The migration logic itself is kept and ported so a mixin on
 * {@code ItemStack#<init>}/{@code ItemStack.CODEC} can re-attach it later.
 */
public class SelfUpgradingBlockItem extends BlockItem
{
    public SelfUpgradingBlockItem(final Block block, final Properties properties)
    {
        super(block, properties);
    }

    // TODO(port-26.2): DISABLED — no 26.2/Fabric equivalent of NeoForge's verifyComponentsAfterLoad.
    // @Override
    // public void verifyComponentsAfterLoad(final ItemStack itemStack)
    // {
    //     super.verifyComponentsAfterLoad(itemStack);
    //     upgrade(itemStack);
    // }

    /**
     * Migrates pre-1.21 DO item NBT onto the data-component layout.
     * <p>
     * 26.2: this no longer needs the running server. {@link MaterialTextureData#CODEC} is built on
     * {@code BuiltInRegistries.BLOCK.byNameCodec()}, i.e. only static registries, so plain
     * {@link NbtOps#INSTANCE} is enough and NeoForge's {@code ServerLifecycleHooks.getCurrentServer()}
     * (which has no Fabric counterpart) is not required.
     */
    public static void upgrade(final ItemStack itemStack)
    {
        final DynamicOps<Tag> dynamicops = NbtOps.INSTANCE;

        CustomData.update(DataComponents.CUSTOM_DATA, itemStack, oldData -> {
            // move Type from root to BlockStateTag
            oldData.getString(TYPE_BLOCK_PROPERTY).ifPresent(type -> {
                itemStack.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> with(props, TYPE_BLOCK_PROPERTY, type));
                oldData.remove(TYPE_BLOCK_PROPERTY);
            });

            // move TextureData from root to BlockEntityTag
            oldData.getCompound(BLOCK_ENTITY_TEXTURE_DATA).ifPresent(textureData -> {
                saveTextureDataFromNbt(itemStack, dynamicops, textureData);
                oldData.remove(BLOCK_ENTITY_TEXTURE_DATA);
            });
        });

        // 26.2: DataComponents.BLOCK_ENTITY_DATA holds a TypedEntityData<BlockEntityType<?>>, not CustomData
        // (/opt/mc-src/net/minecraft/core/component/DataComponents.java:267).
        final TypedEntityData<?> blockEntityData = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData != null)
        {
            final CompoundTag tag = blockEntityData.getUnsafe().getCompoundOrEmpty(BLOCK_ENTITY_TEXTURE_DATA);
            if (!tag.isEmpty())
            {
                saveTextureDataFromNbt(itemStack, dynamicops, tag);
            }
        }
    }

    private static void saveTextureDataFromNbt(final ItemStack itemStack, final DynamicOps<Tag> dynamicops, final CompoundTag tag)
    {
        MaterialTextureData.CODEC.parse(dynamicops, tag)
            .resultOrPartial(DomumOrnamentum.LOGGER::error)
            .orElse(MaterialTextureData.EMPTY)
            .writeToItemStack(itemStack);
    }

    private static BlockItemStateProperties with(BlockItemStateProperties properties, String key, String value)
    {
        return new BlockItemStateProperties(Util.copyAndPut(properties.properties(), key, value));
    }
}
