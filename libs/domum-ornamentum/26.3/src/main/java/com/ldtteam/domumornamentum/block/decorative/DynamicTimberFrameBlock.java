package com.ldtteam.domumornamentum.block.decorative;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.LevelReader;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.ldtteam.domumornamentum.block.AbstractBlock;
import com.ldtteam.domumornamentum.block.ICachedItemGroupBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.components.SimpleRetexturableComponent;
import com.ldtteam.domumornamentum.entity.block.DynamicTimberFrameBlockEntity;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeBuilder;
import com.ldtteam.domumornamentum.tag.ModTags;
import com.ldtteam.domumornamentum.util.BlockUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.world.level.block.Blocks;

/**
 * Decorative block
 */
public class DynamicTimberFrameBlock extends AbstractBlock<DynamicTimberFrameBlock> implements IMateriallyTexturedBlock, ICachedItemGroupBlock, EntityBlock
{
    public static final List<IMateriallyTexturedBlockComponent> COMPONENTS = ImmutableList.<IMateriallyTexturedBlockComponent>builder()
        .add(new SimpleRetexturableComponent(Identifier.withDefaultNamespace("block/oak_planks"), ModTags.TIMBERFRAMES_FRAME, Blocks.OAK_PLANKS))
        .add(new SimpleRetexturableComponent(Identifier.withDefaultNamespace("block/dark_oak_planks"), ModTags.TIMBERFRAMES_CENTER, Blocks.DARK_OAK_PLANKS))
        .build();

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    /**
     * The hardness this block has.
     */
    private static final float BLOCK_HARDNESS = 3F;

    /**
     * The resistance this block has.
     */
    private static final float RESISTANCE = 1F;

    /**
     * Item group cache for recipe.
     */
    private final List<ItemStack> fillItemGroupCache = Lists.newArrayList();

    /**
     * Enum defining the different block offsets we care about.
     */
    public enum Offset implements StringRepresentable
    {
        UP(BlockPos::above),
        DOWN(BlockPos::below),
        NORTH(BlockPos::north),
        EAST(BlockPos::east),
        SOUTH(BlockPos::south),
        WEST(BlockPos::west),
        UP_EAST(p -> p.above().east()),
        UP_WEST(p -> p.above().west()),
        UP_NORTH(p -> p.above().north()),
        UP_SOUTH(p -> p.above().south()),
        DOWN_EAST(p -> p.below().east()),
        DOWN_WEST(p -> p.below().west()),
        DOWN_NORTH(p -> p.below().north()),
        DOWN_SOUTH(p -> p.below().south());

        public static final Codec<Offset> CODEC = new StringRepresentable.EnumCodec<>(
            values(),
            s -> valueOf(s.toUpperCase(Locale.ROOT).replace("-", "_"))
        );

        /**
         * Translation function this direction means in relation to a blockpos change.
         */
        private final Function<BlockPos, BlockPos> posTranslationFunction;

        /**
         * Create a new offset.
         * @param changeFunction and its blockpos translation.
         */
        Offset(final Function<BlockPos, BlockPos> changeFunction)
        {
            this.posTranslationFunction = changeFunction;
        }

        /**
         * Inversion of offset.
         * @return the inverted offset.
         */
        public Offset inverted()
        {
            return switch (this)
            {
                case UP -> DOWN;
                case DOWN -> UP;
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                case WEST -> EAST;
                case UP_NORTH -> DOWN_SOUTH;
                case UP_EAST -> DOWN_WEST;
                case UP_SOUTH -> DOWN_NORTH;
                case UP_WEST -> DOWN_EAST;
                case DOWN_SOUTH -> UP_NORTH;
                case DOWN_WEST -> UP_EAST;
                case DOWN_NORTH -> UP_SOUTH;
                case DOWN_EAST -> UP_WEST;
            };
        }

        /**
         * Apply translation to blockpos.
         * @param pos input pos.
         * @return changed output pos.
         */
        public BlockPos applyToBlockPos(final BlockPos pos)
        {
            return posTranslationFunction.apply(pos);
        }

        public Offset rotate()
        {
            return switch (this)
            {
                case NORTH -> EAST;
                case SOUTH -> WEST;
                case EAST -> SOUTH;
                case WEST -> NORTH;
                case UP_NORTH -> UP_EAST;
                case UP_EAST -> UP_SOUTH;
                case UP_SOUTH -> UP_WEST;
                case UP_WEST -> UP_NORTH;
                case DOWN_SOUTH -> DOWN_WEST;
                case DOWN_WEST -> DOWN_NORTH;
                case DOWN_NORTH -> DOWN_EAST;
                case DOWN_EAST -> DOWN_SOUTH;
                default -> this;
            };
        }

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT).replace("_", "-");
        }
    }

    /**
     * Constructor for the TimberFrame
     */
    public DynamicTimberFrameBlock()
    {
        // Port note (26.3): the whole PushReaction enum was renamed and reordered. PUSH_ONLY is now PUSH
        // (26.2 NORMAL/DESTROY/BLOCK/IGNORE/PUSH_ONLY -> 26.3 PUSH_PULL/PUSH/POPPED/IMMOVEABLE/IGNORE_ENTITY).
        // Mapping by ordinal is wrong here: old DESTROY and new PUSH share index 1 but mean different things.
        super(Properties.of().mapColor(MapColor.WOOD).pushReaction(PushReaction.PUSH).strength(BLOCK_HARDNESS, RESISTANCE).noOcclusion());
    }

    // TODO(port-26.2): DISABLED — IBlockExtension#shouldDisplayFluidOverlay is a NeoForge-only hook
    //   (no vanilla or Fabric equivalent in 26.2). Waterlogged dynamic timber frames render the plain
    //   water surface instead of the "overlay" variant.
    // @Override
    // public boolean shouldDisplayFluidOverlay(final BlockState state, final BlockAndTintGetter level, final BlockPos pos, final FluidState fluidState)
    // {
    //     return true;
    // }

    @Override
    protected void createBlockStateDefinition(@NotNull final StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
    }

    @Override
    public @NotNull List<IMateriallyTexturedBlockComponent> getComponents()
    {
        return COMPONENTS;
    }

    @Override
    public void setPlacedBy(final Level worldIn, final BlockPos pos, final BlockState state, @Nullable final LivingEntity placer, final ItemStack stack)
    {
        super.setPlacedBy(worldIn, pos, state, placer, stack);

        final BlockEntity tileEntity = worldIn.getBlockEntity(pos);
        if (tileEntity instanceof DynamicTimberFrameBlockEntity timberFrameBlockEntity)
        {
            timberFrameBlockEntity.applyComponentsFromItemStack(stack);
            timberFrameBlockEntity.refreshTextureCache();

            for (Offset offset: Offset.values())
            {
                updateNeighbor(timberFrameBlockEntity, worldIn.getBlockEntity(offset.applyToBlockPos(pos)), offset, true);
            }
        }
    }

    /**
     * Utility method to update neighbor with new neighborhood info.
     * @param thisEntity the blockentity being changed.
     * @param neighborEntity the blockentity that is being notified.
     * @param offset the offset at which the neighbor is at.
     * @param added if this blockentity was being added or removed.
     */
    private static void updateNeighbor(final DynamicTimberFrameBlockEntity thisEntity, final BlockEntity neighborEntity, final Offset offset, final boolean added)
    {
        if (neighborEntity != null && neighborEntity instanceof DynamicTimberFrameBlockEntity timberFrameBlockEntity)
        {
            timberFrameBlockEntity.onNeighborUpdate(thisEntity, offset.inverted(), added);
            if (thisEntity != null && timberFrameBlockEntity.getFrameBlock() == thisEntity.getFrameBlock() && timberFrameBlockEntity.getCenterBlock() == thisEntity.getCenterBlock())
            {
                thisEntity.onNeighborUpdate(thisEntity, offset, added);
            }
        }
    }

    @Override
    protected BlockState updateShape(
        final BlockState state,
        final LevelReader level,
        final ScheduledTickAccess ticks,
        final BlockPos pos,
        final Direction directionToNeighbour,
        final BlockPos neighbourPos,
        final BlockState neighbourState,
        final RandomSource random)
    {
        final BlockEntity tileEntity = level.getBlockEntity(pos);

        if (tileEntity instanceof DynamicTimberFrameBlockEntity timberFrameBlockEntity)
        {
            for (Offset offset : Offset.values())
            {
                updateNeighbor(timberFrameBlockEntity, level.getBlockEntity(offset.applyToBlockPos(pos)), offset, true);
            }
        }

        return state;
    }

    /**
     * 26.2: {@code Block#onRemove(BlockState, Level, BlockPos, BlockState, boolean)} is gone; the
     * "tell my neighbours I am gone" half of it is now
     * {@code affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean)}
     * (/opt/mc-src/net/minecraft/world/level/block/state/BlockBehaviour.java:173).
     */
    @Override
    protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel worldIn, final BlockPos pos, final boolean movedByPiston)
    {
        super.affectNeighborsAfterRemoval(state, worldIn, pos, movedByPiston);

        for (Offset offset: Offset.values())
        {
            updateNeighbor(null, worldIn.getBlockEntity(offset.applyToBlockPos(pos)), offset, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final @NotNull BlockPos blockPos, final @NotNull BlockState blockState)
    {
        return new DynamicTimberFrameBlockEntity(blockPos, blockState);
    }

    @Override
    public void resetCache()
    {
        // guarded like the population in IMateriallyTexturedBlock#fillDOItemCategory: the cache
        // is read from both the render thread and the server thread.
        synchronized (fillItemGroupCache)
        {
            fillItemGroupCache.clear();
        }
    }

    @Override
    public ItemStack getCloneItemStack(final LevelReader world, final BlockPos pos, final BlockState state, final boolean includeData)
    {
        return BlockUtils.getMaterializedItemStack(world.getBlockEntity(pos), world.registryAccess());
    }

    @Override
    public void buildRecipes(final RecipeOutput recipeOutput)
    {
        new ArchitectsCutterRecipeBuilder(this, RecipeCategory.BUILDING_BLOCKS).count(COMPONENTS.size()).save(recipeOutput);
    }

    // TODO(port-26.2): DISABLED — per-material explosion resistance. NeoForge's
    //   Block#getExplosionResistance(BlockState, BlockGetter, BlockPos, Explosion) does not exist in
    //   vanilla 26.2 (only Block#getExplosionResistance(), /opt/mc-src/.../block/Block.java:445) and
    //   Fabric has no hook for it, so the resistance of the skin block can no longer be applied.
    // @Override
    // public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
    //     return getDOExplosionResistance(super::getExplosionResistance, state, level, pos, explosion);
    // }

    @Override
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return getDODestroyProgress(super::getDestroyProgress, state, player, level, pos);
    }

    // TODO(port-26.2): DISABLED — per-material sound type. NeoForge's
    //   Block#getSoundType(BlockState, LevelReader, BlockPos, Entity) does not exist in vanilla 26.2
    //   (only BlockBehaviour#getSoundType(BlockState), /opt/mc-src/.../BlockBehaviour.java:404, which
    //   has no position to read the block entity from) and Fabric has no hook for it.
    // @Override
    // public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
    //     return getDOSoundType(super::getSoundType, state, level, pos, entity);
    // }

    @Override
    public IMateriallyTexturedBlockComponent getMainComponent() {
        return COMPONENTS.get(1);
    }

    @Override
    public void fillItemCategory(final @NotNull NonNullList<ItemStack> items) {
        fillDOItemCategory(this, items, fillItemGroupCache);
    }

    // TODO(port-26.2): DISABLED — Block#rotate(BlockState, LevelAccessor, BlockPos, Rotation) is a
    //   NeoForge-only overload; vanilla 26.2 only has rotate(BlockState, Rotation)
    //   (/opt/mc-src/net/minecraft/world/level/block/state/BlockBehaviour.java:255), which has no
    //   position and therefore no block entity to rotate. Rotating a dynamic timber frame (structure
    //   tooling / MineColonies blueprints) no longer rotates its internal offset map.
    // @Override
    // public BlockState rotate(final BlockState state, final LevelAccessor level, final BlockPos pos, final Rotation direction)
    // {
    //     if (level.getBlockEntity(pos) instanceof DynamicTimberFrameBlockEntity dynamicTimberFrameBlockEntity) {
    //         dynamicTimberFrameBlockEntity.rotate(direction.ordinal());
    //     }
    //     return super.rotate(state, level, pos, direction);
    // }

    @Override
    public BlockState rotate(final BlockState p_60530_, final Rotation p_60531_)
    {
        return super.rotate(p_60530_, p_60531_);
    }
}
