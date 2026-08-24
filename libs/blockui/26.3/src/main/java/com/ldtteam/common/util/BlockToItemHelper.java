package com.ldtteam.common.util;

import com.ldtteam.blockui.mod.item.BlockStateRenderingData;
import com.ldtteam.common.fakelevel.SingleBlockFakeLevel.SidedSingleBlockFakeLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Methods for getting itemStack from blockState.
 */
public class BlockToItemHelper
{
    private static final SidedSingleBlockFakeLevel fakeLevel = new SidedSingleBlockFakeLevel();
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockToItemHelper.class);

    /**
     * Mostly for use in UI where you dont have level instance (eg. player selects block, from xml, but not when displaying real world
     * info - see {@link BlockStateRenderingData#of(Level, BlockPos, Player)}).
     *
     * @return result of player middle-mouse-button click with more sensible defaults (liquids -> buckets, fire -> flint+steel), might
     *         be {@link ItemStack#isEmpty()} in case of error
     */
    public static ItemStack getItemStack(final BlockState blockState, final BlockEntity blockEntity, final Player player)
    {
        // quick path air blocks
        if (blockState.getBlock() instanceof AirBlock)
        {
            return ItemStack.EMPTY;
        }

        // client vs server concurrency - we dont care if create two instances, the other should just disappear

        return fakeLevel.get(player.level()).useFakeLevelContext(blockState,
            blockEntity,
            player.level(),
            level -> getItemStackUsingPlayerPick(level, BlockPos.ZERO, player, null));
    }

    /**
     * Mostly for use by machines/entities when you dont have player instance.
     * <p>
     * Port note: NeoForge's {@code FakePlayerFactory} has no Fabric counterpart, but none is needed - in 26.2
     * {@code BlockState#getCloneItemStack} no longer takes a player at all, so null is passed instead.
     *
     * @return result of player middle-mouse-button click with more sensible defaults (liquids -> buckets, fire -> flint and steel), might
     *         be {@link ItemStack#isEmpty()} in case of error
     */
    public static ItemStack getItemStack(final ServerLevel serverLevel, final BlockPos pos)
    {
        return getItemStackUsingPlayerPick(serverLevel, pos, null, null);
    }

    /**
     * General method when you have everything block->item mapping needs, but you don't have hit result (ray trace from camera).
     *
     * @return result of player middle-mouse-button click with more sensible defaults (liquids -> buckets, fire -> flint and steel), might
     *         be {@link ItemStack#isEmpty()} in case of error
     */
    public static ItemStack getItemStack(final Level level, final BlockPos pos, final Player player)
    {
        return getItemStackUsingPlayerPick(level, pos, player, null);
    }

    /**
     * @return result of player middle-mouse-button click with more sensible defaults (liquids -> buckets, fire -> flint and steel), might
     *         be {@link ItemStack#isEmpty()} in case of error
     * @deprecated because vanilla removed {@link HitResult} from method signature
     */
    @Deprecated(since = "26.1")
    public static ItemStack getItemStackUsingPlayerPick(final Level level,
        final BlockPos pos,
        @Nullable final Player player,
        @Nullable HitResult hitResult)
    {
        final BlockState blockState = level.getBlockState(pos);
        // 26.2: BlockStateBase#getCloneItemStack(LevelReader, BlockPos, boolean) - the player argument the
        // NeoForge overload took is gone and the level/pos order swapped
        // (/opt/mc-src/net/minecraft/world/level/block/state/BlockBehaviour.java:894)
        ItemStack result = blockState.getCloneItemStack(level, pos, true);

        if (result.isEmpty())
        {
            result = getItem(blockState).getDefaultInstance();
        }

        return result;
    }

    /**
     * @param blockState source for item
     * @return vanilla result with few fixes
     */
    public static Item getItem(final BlockState blockState)
    {
        final Block block = blockState.getBlock();
        if (block instanceof LiquidBlock)
        {
            // 26.2: LiquidBlock#fluid is protected (it was public through the NeoForge patches), so the fluid
            // is read off the state instead - FluidState#getType (/opt/mc-src/.../material/FluidState.java:33)
            return blockState.getFluidState().getType().getBucket();
        }
        else if (block instanceof BubbleColumnBlock)
        {
            return Fluids.WATER.getBucket();
        }
        else if (block instanceof BaseFireBlock)
        {
            return Items.FLINT_AND_STEEL;
        }

        return block.asItem();
    }

    /**
     * Mimics vanilla logic, previously it was in BlockEntity, later moved to ServerGamePacketListenerImpl.
     *
     * @param blockEntity to be written
     * @param itemStack to write to
     * @param registryAccess from real level
     */
    @SuppressWarnings("deprecation")
    public static void saveBeToItem(final BlockEntity blockEntity, final ItemStack itemStack, final RegistryAccess registryAccess)
    {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "BlockUI writing block entity to item", LOGGER))
        {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registryAccess);
            blockEntity.saveCustomOnly(output);
            blockEntity.removeComponentsFromTag(output);
            BlockItem.setBlockEntityData(itemStack, blockEntity.getType(), output);
            itemStack.applyComponents(blockEntity.collectComponents());
        }
    }
}
