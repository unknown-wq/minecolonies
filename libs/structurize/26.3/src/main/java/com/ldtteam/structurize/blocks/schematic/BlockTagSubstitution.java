package com.ldtteam.structurize.blocks.schematic;

import com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution;
import com.ldtteam.structurize.blocks.interfaces.IAnchorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This block is a substitution block (it disappears on normal build) but stores blueprint data (mostly tags) during scan.
 */
public class BlockTagSubstitution extends BlockSubstitution implements IAnchorBlock, EntityBlock
{
    /**
     * @param properties the id-stamped block properties, built by {@code ModBlocks}.
     */
    public BlockTagSubstitution(final Properties properties)
    {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final @NotNull BlockPos blockPos, final @NotNull BlockState blockState)
    {
        return new BlockEntityTagSubstitution(blockPos, blockState);
    }

    /**
     * 26.2 merged the two 1.21.1 pick-block hooks into a single
     * {@code getCloneItemStack(LevelReader, BlockPos, BlockState, boolean)}
     * (/opt/mc-src/net/minecraft/world/level/block/state/BlockBehaviour.java:408); the
     * {@code (BlockState, HitResult, LevelReader, BlockPos, Player)} overload no longer exists.
     */
    @NotNull
    @Override
    protected ItemStack getCloneItemStack(@NotNull final LevelReader level,
        @NotNull final BlockPos pos,
        @NotNull final BlockState blockState,
        final boolean includeData)
    {
        return cloneItemStack(super.getCloneItemStack(level, pos, blockState, includeData), level, pos);
    }

    private ItemStack cloneItemStack(final ItemStack stack, final LevelReader level, final BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof final BlockEntityTagSubstitution entity)
        {
            // 26.2: BlockEntity#saveToItem is gone, vanilla applies the collected components instead
            // (/opt/mc-src/net/minecraft/world/level/block/ShulkerBoxBlock.java:113)
            stack.applyComponents(entity.collectComponents());
        }
        return stack;
    }
}
