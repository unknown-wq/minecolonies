package com.minecolonies.core.placementhandlers;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.api.util.WorldUtil;
// 26.2: IPlacementHandler#doesWorldStateMatchBlueprintState takes Structurize's own Tuple now --
// net.minecraft.util.Tuple is gone and each mod grew its own replacement.
import com.ldtteam.structurize.api.Tuple;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.ldtteam.structurize.api.constants.Constants.UPDATE_FLAG;

/**
 * Makes lava in the nether free and water everywhere else.
 */
public class DimensionFluidHandler implements IPlacementHandler
{
    @Override
    public boolean canHandle(@NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState blockState)
    {
         return blockState.getBlock() instanceof LiquidBlock || blockState.getBlock() instanceof BubbleColumnBlock;
    }

    @Override
    public List<ItemStack> getRequiredItems(
      @NotNull Level world,
      @NotNull BlockPos pos,
      @NotNull BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      @NotNull final IPlacementContext placementContext)
    {
        final List<ItemStack> itemList = new ArrayList<>();
        if (!placementContext.fancyPlacement())
        {
            itemList.add(BlockUtils.getItemStackFromBlockState(blockState));
            return itemList;
        }
        if (WorldUtil.isNetherType(world) && blockState.getBlock() == Blocks.LAVA)
        {
            return Collections.emptyList();
        }
        else if (blockState.getBlock() == Blocks.WATER)
        {
            return Collections.emptyList();
        }

        if (!blockState.getFluidState().isSource())
        {
            return Collections.emptyList();
        }

        itemList.add(BlockUtils.getItemStackFromBlockState(blockState));
        return itemList;
    }

    @Override
    public IPlacementHandler.ActionProcessingResult handle(
      @NotNull Level world,
      @NotNull BlockPos pos,
      @NotNull BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      @NotNull final IPlacementContext placementContext)
    {
        if (!blockState.getFluidState().isSource() && placementContext.fancyPlacement())
        {
            return ActionProcessingResult.PASS;
        }
        world.setBlock(pos, blockState, UPDATE_FLAG);
        world.scheduleTick(pos, blockState.getFluidState().getType(), blockState.getFluidState().getType().getTickDelay(world));
        return ActionProcessingResult.SUCCESS;
    }

    @Override
    public boolean doesWorldStateMatchBlueprintState(
        final BlockState blueprintState,
        final BlockState worldState,
        final Tuple<BlockEntity, CompoundTag> tuple,
        @NotNull final IPlacementContext iPlacementContext)
    {
        return blueprintState.equals(worldState);
    }
}
