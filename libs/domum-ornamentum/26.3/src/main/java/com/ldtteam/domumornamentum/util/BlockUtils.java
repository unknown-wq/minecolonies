package com.ldtteam.domumornamentum.util;

import com.ldtteam.domumornamentum.entity.block.AbstractMateriallyTexturedBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.fabricmc.loader.api.FabricLoader;

public class BlockUtils
{

    private BlockUtils()
    {
        throw new IllegalStateException("Can not instantiate an instance of: BlockUtils. This is a utility class");
    }

    /**
     * The block's own display name.
     *
     * <p>This used to be {@code new ItemStack(block).getHoverName()}, which allocated a stack per call at
     * roughly thirty tooltip call sites. {@link Block#getName()} is public and returns
     * {@code Component.translatable(getDescriptionId())}; every block item that can reach this method is
     * registered with {@code Item.Properties#useBlockDescriptionPrefix()}, so its {@code ITEM_NAME} component
     * resolves to that very key and the two produce the same component.</p>
     */
    public static Component getHoverName(final Block block) {
        return block.getName();
    }

    public static ItemStack getMaterializedItemStack(final BlockEntity blockEntity,
        final HolderLookup.Provider provider,
        final Property<?>... blockStateProperties)
    {
        if (!(blockEntity instanceof final AbstractMateriallyTexturedBlockEntity texturedBlockEntity))
        {
            return ItemStack.EMPTY;
        }

        final ItemStack result = new ItemStack(blockEntity.getBlockState().getBlock());
        texturedBlockEntity.saveToItem(result, provider);

        if (blockStateProperties.length > 0)
        {
            final BlockState blockState = texturedBlockEntity.getBlockState();
            result.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> {
                for (final Property<?> property : blockStateProperties)
                {
                    props = props.with(property, blockState);
                }
                return props;
            });
        }

        return result;
    }

    public static <T extends Comparable<T>> void putPropertyIntoBlockStateTag(final ItemStack itemStack,
        final Property<T> property,
        final T value)
    {
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && !(itemStack.getItem() instanceof BlockItem))
        {
            throw new IllegalArgumentException("item not BlockItem: " + itemStack.getItem());
        }
        itemStack.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> props.with(property, value));
    }

    public static <T extends Comparable<T>> T getPropertyFromBlockStateTag(final ItemStack itemStack,
        final Property<T> property,
        final T defaultValue)
    {
        final T blockValue = itemStack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(property);
        return blockValue == null ? defaultValue : blockValue;
    }
}
