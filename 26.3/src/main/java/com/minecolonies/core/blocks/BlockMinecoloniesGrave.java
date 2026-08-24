package com.minecolonies.core.blocks;

import com.minecolonies.api.blocks.interfaces.IBlockMinecolonies;
import com.minecolonies.api.blocks.AbstractBlockMinecoloniesGrave;
import com.minecolonies.api.blocks.types.GraveType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.tileentities.TileEntityGrave;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.resources.Identifier;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Block for the graves
 */
public class BlockMinecoloniesGrave extends AbstractBlockMinecoloniesGrave<BlockMinecoloniesGrave>
{
    /**
     * The hardness this block has.
     */
    private static final float BLOCK_HARDNESS = 1.5F;

    /**
     * This blocks name.
     */
    private static final String BLOCK_NAME = "blockminecoloniesgrave";

    /**
     * The resistance this block has.
     */
    private static final float RESISTANCE = 5F;

    /**
     * Smaller shape.
     */
    private static final VoxelShape SHAPE = Shapes.box(0.1, 0.1, 0.1, 0.9, 0.9, 0.9);

    public BlockMinecoloniesGrave()
    {
        super(IBlockMinecolonies.withBlockId(Properties.of().mapColor(MapColor.STONE).sound(SoundType.STONE).strength(BLOCK_HARDNESS, RESISTANCE), BLOCK_NAME));
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH).setValue(VARIANT, GraveType.DEFAULT));
    }

    @Override
    public Identifier getRegistryName()
    {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, BLOCK_NAME);
    }

    @Override
    protected boolean propagatesSkylightDown(@NotNull final BlockState state)
    {
        return false;
    }

    @NotNull
    @Override
    public VoxelShape getShape(final BlockState state, final BlockGetter worldIn, final BlockPos pos, final CollisionContext context)
    {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context)
    {
        final Level worldIn = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final BlockState state = defaultBlockState();
        final BlockEntity entity = worldIn.getBlockEntity(pos);

        if (!(entity instanceof TileEntityGrave))
        {
            return super.getStateForPlacement(context);
        }

        return getPlacementState(state, pos);
    }

    /**
     * Get the statement ready.
     *
     * @param state  the state to place.
     * @param pos    the position.
     * @return the next state.
     */
    public static BlockState getPlacementState(final BlockState state, final BlockPos pos)
    {
        return state.setValue(VARIANT, GraveType.DEFAULT);
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
    public InteractionResult useItemOn(
      final ItemStack stack,
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
              && tileEntity instanceof TileEntityGrave)
        {
            final TileEntityGrave grave = (TileEntityGrave) tileEntity;
            if (!worldIn.isClientSide())
            {
                ((ServerPlayer) player).openMenu(grave);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void setPlacedBy(final Level worldIn, final BlockPos pos, final BlockState state, @Nullable final LivingEntity placer, final ItemStack stack)
    {
        BlockState tempState = state;
        tempState = tempState.setValue(VARIANT, GraveType.DEFAULT);
        if (placer != null)
        {
            tempState = tempState.setValue(FACING, placer.getDirection().getOpposite());
        }

        worldIn.setBlock(pos, tempState, 2);
    }

    @Override
    public VoxelShape getCollisionShape(final BlockState p_60572_, final BlockGetter p_60573_, final BlockPos p_60574_, final CollisionContext p_60575_)
    {
        return Shapes.empty();
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
        return new TileEntityGrave(blockPos, blockState);
    }

    // PORT-NOTE(26.2): 1.21.1 did both halves of this in BlockBehaviour#onRemove -- spill the grave, then refresh the
    // comparator signal. 26.2 replaced onRemove with affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos,
    // boolean), which vanilla only calls when the block really changed (so the old guard is implicit) but which now
    // runs *after* the block entity has been removed from the chunk, i.e. too late to read the inventory
    // (/opt/mc-src/net/minecraft/world/level/chunk/LevelChunk.java:307-320) -- the ported drop could never fire, and
    // a broken grave swallowed whatever the dead citizen was carrying. The drop moved to the one hook that still sees
    // the block entity, AbstractTileEntityRack#preRemoveSideEffects, which every grave inherits; only the signal
    // refresh is left here.
    @Override
    protected void affectNeighborsAfterRemoval(@NotNull BlockState state, @NotNull ServerLevel worldIn, @NotNull BlockPos pos, boolean movedByPiston)
    {
        worldIn.updateNeighbourForOutputSignal(pos, this);
        super.affectNeighborsAfterRemoval(state, worldIn, pos, movedByPiston);
    }
}
