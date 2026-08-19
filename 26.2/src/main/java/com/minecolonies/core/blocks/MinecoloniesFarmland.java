package com.minecolonies.core.blocks;

import com.minecolonies.api.blocks.interfaces.IBlockMinecolonies;
import com.minecolonies.api.blocks.AbstractBlockMinecolonies;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.network.messages.client.VanillaParticleMessage;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.CitizenConstants.BLOCK_BREAK_SOUND_RANGE;

public class MinecoloniesFarmland extends AbstractBlockMinecolonies<MinecoloniesFarmland> implements SimpleWaterloggedBlock
{
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final String FARMLAND         = "farmland";
    public static final String FLOODED_FARMLAND = "floodedfarmland";

    public static final    IntegerProperty MOISTURE     = BlockStateProperties.MOISTURE;
    protected final VoxelShape shape;

    private final Identifier    blockId;

    /**
     * If should behave waterlogged.
     */
    private final boolean waterLogged;

    public MinecoloniesFarmland(@NotNull final String blockName, final boolean waterLogged, final double height)
    {
        super(IBlockMinecolonies.withBlockId(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).randomTicks().strength(0.6F).sound(SoundType.GRAVEL).isViewBlocking((s,g,p) -> true).isSuffocating((s,g,p) -> true), blockName));
        this.registerDefaultState(this.stateDefinition.any().setValue(MOISTURE, 0));
        this.blockId = Identifier.fromNamespaceAndPath(Constants.MOD_ID, blockName);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, Boolean.valueOf(waterLogged)));

        this.shape = Block.box(0.0, 0.0, 0.0, 16.0, height, 16.0);
        this.waterLogged = waterLogged;
    }

    @NotNull
    @Override
    public BlockState updateShape(
      @NotNull final BlockState state,
      @NotNull final LevelReader level,
      @NotNull final ScheduledTickAccess ticks,
      @NotNull final BlockPos pos,
      @NotNull final Direction direction,
      @NotNull final BlockPos neighborPos,
      @NotNull final BlockState newState,
      @NotNull final RandomSource random)
    {
        if (direction == Direction.UP && !state.canSurvive(level, pos))
        {
            ticks.scheduleTick(pos, this, 1);
        }
        if (state.getValue(WATERLOGGED) && waterLogged)
        {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, direction, neighborPos, newState, random);
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, LevelReader level, BlockPos pos)
    {
        if (level == null)
        {
            // This is for our solid checks.
            return true;
        }
        BlockState aboveState = level.getBlockState(pos.above());
        return !aboveState.isSolid();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx)
    {
        return !this.defaultBlockState().canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? Blocks.DIRT.defaultBlockState() : super.getStateForPlacement(ctx);
    }

    @Override
    public boolean useShapeForLightOcclusion(@NotNull BlockState state)
    {
        return true;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx)
    {
        return shape;
    }

    @Override
    public void randomTick(BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource rng)
    {
        if (!state.canSurvive(level, pos))
        {
            turnToDirt(null, state, level, pos);
            return;
        }

        int i = state.getValue(MOISTURE);
        if (!level.isRainingAt(pos.above()) && !isNearWater(level, pos))
        {
            if (i > 0)
            {
                level.setBlock(pos, state.setValue(MOISTURE, i - 1), 2);
            }
            else if (!shouldMaintainFarmland(level, pos))
            {
                turnToDirt( null, state, level, pos);
            }
        }
        else if (i < 7)
        {
            level.setBlock(pos, state.setValue(MOISTURE, 7), 2);
        }

        final BlockState aboveState = level.getBlockState(pos.above());
        int growthChance = 4;
        if (level.isRaining())
        {
            // Increased growth during rain, rain is usually short and frequently skipped since citizens do not work
            growthChance = 12;
        }
        if (aboveState.getBlock() instanceof MinecoloniesCropBlock cropBlock && rng.nextInt(100) <= growthChance)
        {
            cropBlock.attemptGrow(aboveState, level, pos.above());
            new VanillaParticleMessage(pos.getX() + 0.5F, pos.getY() - 0.5F, pos.getZ() + 0.5F, ParticleTypes.HAPPY_VILLAGER).sendToTargetPoint(level, null, pos.getX(), pos.getY(), pos.getZ(), BLOCK_BREAK_SOUND_RANGE);
        }
    }

    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource rng)
    {
        if (level.isRaining() && rng.nextInt(100) < 25 && level.getBlockState(pos.above()).getBlock() instanceof MinecoloniesCropBlock)
        {
            ParticleUtils.spawnParticleInBlock(level, pos, 1, ParticleTypes.HAPPY_VILLAGER);

        }
        super.animateTick(state, level, pos, rng);
    }

    @Override
    public void fallOn(@NotNull Level level, @NotNull BlockState state, @NotNull BlockPos pos, @NotNull Entity entity, double light)
    {
        // 26.2/Fabric: NeoForge's CommonHooks#onFarmlandTrample (a cancellable trample event) has no counterpart.
        // Inlined with vanilla FarmlandBlock#fallOn's own condition, so trampling still behaves like vanilla
        // farmland -- what is lost is only other mods' ability to veto the trample.
        if (level instanceof ServerLevel serverLevel
              && level.getRandom().nextFloat() < light - 0.5F
              && entity instanceof net.minecraft.world.entity.LivingEntity
              && (entity instanceof net.minecraft.world.entity.player.Player
                    || serverLevel.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING))
              && entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() > 0.512F)
        {
            turnToDirt(entity, state, level, pos);
        }

        super.fallOn(level, state, pos, entity, light);
    }

    public static void turnToDirt(@Nullable Entity p_270981_, BlockState p_270402_, Level p_270568_, BlockPos p_270551_)
    {
        BlockState blockstate = pushEntitiesUp(p_270402_, Blocks.DIRT.defaultBlockState(), p_270568_, p_270551_);
        p_270568_.setBlockAndUpdate(p_270551_, blockstate);
        p_270568_.gameEvent(GameEvent.BLOCK_CHANGE, p_270551_, Context.of(p_270981_, blockstate));
    }

    /**
     * 26.2/Fabric: NeoForge's {@code SpecialPlantable} and {@code BlockState#canSustainPlant} are gone. Vanilla
     * asks the same question with a block tag, exactly as {@code FarmlandBlock#shouldMaintainFarmland} does.
     */
    private static boolean shouldMaintainFarmland(BlockGetter p_279219_, BlockPos p_279209_)
    {
        return p_279219_.getBlockState(p_279209_.above()).is(net.minecraft.tags.BlockTags.MAINTAINS_FARMLAND);
    }

    private static boolean isNearWater(LevelReader level, BlockPos thisPos)
    {
        BlockState state = level.getBlockState(thisPos);
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int x = thisPos.getX() -4; x <= thisPos.getX() + 4; x++)
        {
            for (int z = thisPos.getZ() -4; z <= thisPos.getZ() + 4; z++)
            {
                for (int y = thisPos.getY() - 1; y <= thisPos.getY(); y++)
                {
                    blockPos.set(x,y,z);
                    // 26.2/Fabric: BlockState#canBeHydrated was a NeoForge extension; vanilla FarmlandBlock
                    // simply tests the fluid against #minecraft:water over the same 9x2x9 box.
                    if (level.getFluidState(blockPos).is(net.minecraft.tags.FluidTags.WATER))
                    {
                        return true;
                    }
                }
            }
        }

        // TODO(port-26.2): DISABLED -- NeoForge's FarmlandWaterManager (mod-registered "this farmland is watered"
        // tickets, used by sprinkler-style mods) has no Fabric or vanilla counterpart. Without it a farmland block
        // is hydrated only by real water in range, which is exactly vanilla behaviour.
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> blockStateBuilder)
    {
        blockStateBuilder.add(MOISTURE, WATERLOGGED);
    }

    @Override
    public Identifier getRegistryName()
    {
        return blockId;
    }

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.getValue(WATERLOGGED) && waterLogged ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
