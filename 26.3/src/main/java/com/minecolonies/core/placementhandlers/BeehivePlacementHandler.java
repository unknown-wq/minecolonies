package com.minecolonies.core.placementhandlers;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
// 26.2: IPlacementHandler#doesWorldStateMatchBlueprintState takes Structurize's own Tuple now --
// net.minecraft.util.Tuple is gone and each mod grew its own replacement.
import com.ldtteam.structurize.api.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.Constants.UPDATE_FLAG;

public class BeehivePlacementHandler implements IPlacementHandler
{
    @Override
    public boolean canHandle(@NotNull final Level world, @NotNull final BlockPos pos, @NotNull final BlockState blockState)
    {
        return blockState.getBlock() == Blocks.BEEHIVE || blockState.getBlock() == Blocks.BEE_NEST;
    }

    @Override
    public ActionProcessingResult handle(
      @NotNull final Level world,
      @NotNull final BlockPos pos,
      @NotNull final BlockState blockState,
      @Nullable final CompoundTag tileEntityData,
      @NotNull final IPlacementContext placementContext)
    {
        world.setBlock(pos, blockState.getBlock().defaultBlockState().setValue(BeehiveBlock.FACING, blockState.getValue(BeehiveBlock.FACING)), UPDATE_FLAG);
        return ActionProcessingResult.SUCCESS;
    }

    @Override
    public List<ItemStack> getRequiredItems(
      @NotNull final Level world,
      @NotNull final BlockPos pos,
      @NotNull final BlockState blockState,
      @Nullable final CompoundTag tileEntityData,
        @NotNull final IPlacementContext placementContext)
    {
        final List<ItemStack> itemList = new ArrayList<>();
        if (world.getBlockState(pos).getBlock() == blockState.getBlock() && placementContext.fancyPlacement())
        {
            return itemList;
        }

        itemList.add(BlockUtils.getItemStackFromBlockState(blockState));
        return itemList;
    }

    @Override
    public boolean doesWorldStateMatchBlueprintState(
        final BlockState worldState,
        final BlockState blueprintState,
        final Tuple<BlockEntity, CompoundTag> blockEntityData,
        @NotNull final IPlacementContext structureHandler)
    {
        return worldState.getBlock() == blueprintState.getBlock();
    }
}
