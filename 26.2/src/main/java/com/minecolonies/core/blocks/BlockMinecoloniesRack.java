package com.minecolonies.core.blocks;

import com.minecolonies.api.blocks.interfaces.IBlockMinecolonies;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.minecolonies.api.blocks.AbstractBlockMinecoloniesRack;
import com.minecolonies.api.blocks.types.RackType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.tileentities.TileEntityRack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.InteractionHand;

/**
 * Block for the shelves of the warehouse.
 */
public class BlockMinecoloniesRack extends AbstractBlockMinecoloniesRack<BlockMinecoloniesRack> implements IMateriallyTexturedBlock
{
    /**
     * Normal translation we use.
     */
    private static final Long2ObjectMap<Direction> BY_NORMAL = Arrays.stream(Direction.values()).collect(Collectors.toMap((p_235679_) -> {
        return (new BlockPos(p_235679_.getUnitVec3i())).asLong();
    }, (p_235675_) -> {
        return p_235675_;
    }, (p_235670_, p_235671_) -> {
        throw new IllegalArgumentException("Duplicate keys");
    }, Long2ObjectOpenHashMap::new));

    /**
     * The hardness this block has.
     */
    private static final float BLOCK_HARDNESS = 10.0F;

    /**
     * This blocks name.
     */
    private static final String BLOCK_NAME = "blockminecoloniesrack";

    /**
     * The resistance this block has.
     * <p>
     * Upstream makes the rack blast proof outright ({@code Float.POSITIVE_INFINITY}), which puts colony storage in
     * the same class as bedrock: a creeper in the warehouse leaves the floor cratered and every rack in it
     * untouched, floating. It is also the only block in the mod that does this -- huts sit at 1, a grave at 5.
     * <p>
     * Six is stone, and stone is the line vanilla itself draws for "a creeper takes a bite out of it". Mining by hand
     * is unaffected, since that is {@link #BLOCK_HARDNESS} and it stays at ten.
     * <p>
     * The contents are not lost when it goes. Vanilla's explosion path ends in
     * {@code BlockBehaviour#onExplosionHit} calling {@code level.setBlock(pos, AIR, 3)} -- flag 3, so drops are not
     * suppressed -- which reaches {@code AbstractTileEntityRack#preRemoveSideEffects}, the same hook a pickaxe goes
     * through. The rack's own {@link #getDrops} only ever yields the block item, and that one may still be eaten by
     * the usual explosion decay roll; the inventory does not go through it.
     */
    private static final float RESISTANCE = 6.0F;

    /**
     * Smaller shape.
     */
    private static final VoxelShape SHAPE = Shapes.box(0.1, 0.1, 0.1, 0.9, 0.9, 0.9);

    public BlockMinecoloniesRack()
    {
        super(IBlockMinecolonies.withBlockId(Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(BLOCK_HARDNESS, RESISTANCE), BLOCK_NAME));
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(VARIANT, RackType.EMPTY));
    }

    @Override
    public Identifier getRegistryName()
    {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, BLOCK_NAME);
    }

    @Override
    protected boolean propagatesSkylightDown(@NotNull final BlockState state)
    {
        return true;
    }

    @NotNull
    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter worldIn, final BlockPos pos, final CollisionContext context)
    {
        return SHAPE;
    }

    @NotNull
    @Override
    public VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext ctx)
    {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context)
    {
        if (context.getPlayer() != null)
        {
            return defaultBlockState().setValue(FACING, context.getPlayer().getDirection().getOpposite());
        }
        return super.getStateForPlacement(context);
    }

    /**
     * Convert the BlockState into the correct metadata value.
     *
     * @deprecated (Remove this as soon as minecraft offers anything better).
     */
    @NotNull
    @Override
    @Deprecated
    public BlockState rotate(@NotNull final BlockState state, final Rotation rot)
    {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    /**
     * @deprecated (Remove this as soon as minecraft offers anything better).
     */
    @NotNull
    @Override
    @Deprecated
    public BlockState mirror(@NotNull final BlockState state, final Mirror mirrorIn)
    {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    @NotNull
    public BlockState updateShape(
      @NotNull final BlockState state,
      @NotNull final LevelReader level,
      @NotNull final ScheduledTickAccess ticks,
      @NotNull final BlockPos pos,
      @NotNull final Direction dir,
      @NotNull final BlockPos neighbourPos,
      @NotNull final BlockState neighbourState,
      @NotNull final RandomSource random)
    {
        if (state.getBlock() != this || pos.subtract(neighbourPos).getY() != 0)
        {
            return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
        }

        final BlockEntity here = level.getBlockEntity(pos);
        if (!(here instanceof TileEntityRack hereRack))
        {
            return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
        }

        if (neighbourState.getBlock() != this)
        {
            // Reset to single
            if (state.getValue(VARIANT).isDoubleVariant() && pos.relative(state.getValue(FACING)).equals(neighbourPos))
            {
                return state.setValue(VARIANT, hereRack.isEmpty() ? RackType.EMPTY : RackType.FULL);
            }

            return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
        }


        // Connect two
        if (!state.getValue(VARIANT).isDoubleVariant() && !neighbourState.getValue(VARIANT).isDoubleVariant())
        {

            final BlockEntity neighbour = level.getBlockEntity(neighbourPos);

            if (!(neighbour instanceof TileEntityRack neighborRack))
            {
                return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
            }

            boolean isEmpty = hereRack.isEmpty() && neighborRack.isEmpty();

            // PORT-NOTE(26.2): updateShape's level parameter is typed LevelReader now, which has no setBlock. Every
            // vanilla caller still hands in a LevelAccessor -- it passes the same object twice, as `level, level`
            // (Block#updateFromNeighbourShapes, NeighborUpdater#shapeUpdate, StructureTemplate, UpgradeData) -- so
            // the twin is still updated in place, through a checked cast rather than a blind one.
            if (level instanceof final LevelAccessor writable)
            {
                writable.setBlock(neighbourPos,
                  neighbourState.setValue(FACING, BY_NORMAL.get(neighbourPos.subtract(pos).asLong()).getOpposite()).setValue(VARIANT, RackType.NO_RENDER),
                  1);
            }
            return state.setValue(VARIANT, isEmpty ? RackType.EMPTY_DOUBLE : RackType.FULL_DOUBLE)
                     .setValue(FACING, BY_NORMAL.get(neighbourPos.subtract(pos).asLong()));
        }

        // Validate double variant
        if (state.getValue(VARIANT).isDoubleVariant() && pos.relative(state.getValue(FACING)).equals(neighbourPos))
        {
            if (!neighbourState.getValue(FACING).equals(state.getValue(FACING).getOpposite()) || !neighbourState.getValue(VARIANT).isDoubleVariant())
            {
                return state.setValue(VARIANT, hereRack.isEmpty() ? RackType.EMPTY : RackType.FULL);
            }

            if (neighbourState.getValue(VARIANT) != RackType.NO_RENDER && state.getValue(VARIANT) != RackType.NO_RENDER)
            {
                return state.setValue(VARIANT, RackType.NO_RENDER);
            }
        }

        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
    }

    @Override
    public InteractionResult useItemOn(
      final ItemStack p_316304_,
      final BlockState state,
      final Level worldIn,
      final BlockPos pos,
      final Player player,
      final InteractionHand hand,
      final BlockHitResult ray)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(worldIn, pos);
        final BlockEntity tileEntity = worldIn.getBlockEntity(pos);

        if ((colony == null || colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
              && tileEntity instanceof TileEntityRack)
        {
            final TileEntityRack rack = (TileEntityRack) tileEntity;
            if (!worldIn.isClientSide())
            {
                ((ServerPlayer) player).openMenu(rack);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, VARIANT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull final BlockPos blockPos, @NotNull final BlockState blockState)
    {
        return new TileEntityRack(blockPos, blockState);
    }

    @Override
    public List<ItemStack> getDrops(final BlockState state, final LootParams.Builder builder)
    {
        final List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(this, 1));
        return drops;
    }

    // PORT-NOTE(26.2): 1.21.1 did both halves of this in BlockBehaviour#onRemove -- drop the contents, then refresh
    // the comparator signal. 26.2 replaced onRemove with affectNeighborsAfterRemoval(BlockState, ServerLevel,
    // BlockPos, boolean), which vanilla only calls when the block really changed (so the old guard is implicit) but
    // which now runs *after* the block entity has been removed from the chunk, i.e. too late to read the inventory
    // (/opt/mc-src/net/minecraft/world/level/chunk/LevelChunk.java:307-320). The drop moved to the block entity's
    // preRemoveSideEffects, which is the hook that still sees it; only the signal refresh is left here.
    @Override
    protected void affectNeighborsAfterRemoval(@NotNull BlockState state, @NotNull ServerLevel worldIn, @NotNull BlockPos pos, boolean movedByPiston)
    {
        worldIn.updateNeighbourForOutputSignal(pos, this);
        super.affectNeighborsAfterRemoval(state, worldIn, pos, movedByPiston);
    }

    @Override
    public @NotNull Collection<IMateriallyTexturedBlockComponent> getComponents()
    {
        return Collections.emptyList();
    }

    @Override
    public void buildRecipes(final RecipeOutput recipeOutput)
    {
        // noop, for DO blocks only
    }
}
