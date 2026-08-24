package com.ldtteam.domumornamentum.entity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Abstract MateriallyTexturedBlockEntity.
 */
public abstract class AbstractMateriallyTexturedBlockEntity extends BlockEntity implements IMateriallyTexturedBlockEntity
{
    /**
     * Abstract constructor.
     * @param type blockentity type.
     * @param pos creation pos.
     * @param state creation state.
     */
    public AbstractMateriallyTexturedBlockEntity(
        final BlockEntityType<?> type,
        final BlockPos pos,
        final BlockState state)
    {
        super(type, pos, state);
    }

    /**
     * Replaces NeoForge's {@code BlockEntity#requestModelDataUpdate()}. Fabric exposes render data
     * through {@code RenderDataBlockEntity#getRenderData()} (pulled by the chunk builder), so the
     * only thing left to do is to ask the client to re-bake this position.
     */
    public void requestModelDataUpdate()
    {
        if (level != null && level.isClientSide())
        {
            level.setBlocksDirty(worldPosition, Blocks.AIR.defaultBlockState(), getBlockState());
        }
    }

    /**
     * Replaces vanilla 1.21.1's {@code BlockEntity#saveToItem(ItemStack, HolderLookup.Provider)},
     * which is gone in 26.2 — vanilla now writes the block entity onto a stack with
     * {@code stack.applyComponents(blockEntity.collectComponents())}
     * (/opt/mc-src/net/minecraft/world/level/block/ShulkerBoxBlock.java:113).
     */
    public void saveToItem(final ItemStack stack, final HolderLookup.Provider provider)
    {
        stack.applyComponents(this.collectComponents());
    }
}
