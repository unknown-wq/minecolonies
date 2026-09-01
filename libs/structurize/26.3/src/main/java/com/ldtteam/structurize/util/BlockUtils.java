package com.ldtteam.structurize.util;

import com.ldtteam.common.util.BlockToItemHelper;
import com.ldtteam.structurize.compat.DomumCompat;
import com.ldtteam.structurize.api.ItemStackUtils;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.placement.SimplePlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.ldtteam.structurize.tag.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.ldtteam.structurize.tag.ModTags.GOOD_SOLID_FOR_PLACEHOLDER;

/**
 * Utility class for all Block type checking.
 */
public final class BlockUtils
{
    /**
     * All solid blocks in the game that may float in the air without support.
     *
     * <p>Published as a whole rather than filled in place: the filter reads
     * {@code ModTags.WEAK_SOLID_BLOCKS}, so the set has to be rebuilt when tags reload, and
     * {@link #canBlockFloatInAir} reads it from the placement path meanwhile. Swapping a finished set into
     * a volatile field means a reader sees either the old answer or the new one, never a half-filled set.</p>
     */
    private static volatile Set<Block> trueSolidBlocks = Collections.emptySet();

    /**
     * Predicated to determine if a block is free to place.
     */
        public static final List<BiPredicate<Block, BlockState>> FREE_TO_PLACE_BLOCKS = Arrays.asList(
        (block, iBlockState) -> block.equals(Blocks.AIR),
        (block, iBlockState) -> BlockUtils.isLiquidOnlyBlock(iBlockState.getBlock()),
        (block, iBlockState) -> BlockUtils.isWater(block.defaultBlockState()),
        (block, iBlockState) -> block instanceof LeavesBlock,
        (block, iBlockState) -> block instanceof DoublePlantBlock,
        (block, iBlockState) -> block.equals(Blocks.GRASS_BLOCK),
        (block, iBlockState) -> block instanceof DoorBlock && iBlockState != null && iBlockState.getValue(BooleanProperty.create("upper")));

    /**
     * Private constructor to hide the public one.
     */
    private BlockUtils()
    {
        // Hides implicit constructor.
    }

    /**
     * Check or init the non solid blocks.
     */
    public static void checkOrInit()
    {
        if (trueSolidBlocks.isEmpty())
        {
            trueSolidBlocks = computeTrueSolidBlocks();
        }
    }

    /**
     * Rebuild the solid-block set because the tags it is derived from have changed.
     *
     * <p>Only ever called for the logical server, which is the only side that populated the set before --
     * {@link #checkOrInit()} runs off the server level tick. Keeping that side condition means this adds an
     * invalidation and nothing else.</p>
     */
    public static void onTagsReloaded()
    {
        trueSolidBlocks = computeTrueSolidBlocks();
    }

    /**
     * Walk the block registry for everything that can stand unsupported.
     *
     * @return the freshly derived set.
     */
    private static Set<Block> computeTrueSolidBlocks()
    {
        final Set<Block> solid = Collections.newSetFromMap(new IdentityHashMap<>());
        BuiltInRegistries.BLOCK.stream()
            .filter(BlockUtils::canBlockSurviveWithoutSupport)
            .filter(block -> !block.defaultBlockState().canBeReplaced() && block.hasCollision && !(block instanceof Fallable) && !block.defaultBlockState().isAir()
                && !(block instanceof LiquidBlock) && !block.builtInRegistryHolder().is(ModTags.WEAK_SOLID_BLOCKS))
            .forEach(solid::add);
        return solid;
    }

    /**
     * Get the filler block at a certain location.
     *
     * @param  level         the world the block is in.
     * @param  location      the location it is at.
     * @param  virtualBlocks if null use level instead for getting surrounding block states, fnc may should return null if virtual
     *                       block is not available
     * @return               the BlockState of the filler block.
     */
    public static BlockState getSubstitutionBlockAtWorld(final Level level,
        final BlockPos location,
        @Nullable final Function<BlockPos, BlockState> virtualBlocks)
    {
        BlockState result = getWorldgenBlock(level, location, virtualBlocks);

        if (result != null && result.getBlock() == Blocks.POWDER_SNOW)
        {
            result = Blocks.SNOW_BLOCK.defaultBlockState();
        }
        else if (result == null || !BlockUtils.isAnySolid(result) || result.getBlock() == Blocks.BEDROCK)
        {
            // try default level block
            result = getDefaultBlockForLevel(level, null);

            // oh non-solid again + vanilla has stupid settings so override them
            if (result == null || !BlockUtils.isAnySolid(result) || result.getBlock() == Blocks.STONE)
            {
                result = Blocks.DIRT.defaultBlockState();
            }
        }

        return result;
    }

    /**
     * Get the worldGen block for a certain location. Always gives DIRT for non vanilla worlds including blueprint.
     *
     * @param  level         the world the block is in.
     * @param  location      the real world location.
     * @param  virtualBlocks if null use level instead for getting surrounding block states, fnc may should return null if virtual
     *                       block is not available
     * @return               the BlockState of the filler block.
     * @see                  net.minecraft.world.level.levelgen.material.MaterialRules for possible blockstates
     */
    @Nullable
    public static BlockState getWorldgenBlock(final Level level, final BlockPos location, @Nullable final Function<BlockPos, BlockState> virtualBlocks)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            final ChunkGenerator generator = serverLevel.getChunkSource().getGenerator();
            if (generator instanceof NoiseBasedChunkGenerator chunkGenerator)
            {
                final NoiseGeneratorSettings generatorSettings = chunkGenerator.generatorSettings().value();

                // TODO(port-26.3): 26.3-snapshot-10 deleted SurfaceRules and replaced it with the material rules
                //  under net.minecraft.world.level.levelgen.material. MaterialRuleContext, the context they
                //  evaluate against, is a public class -- but its constructor and its updateXZ/updateY are
                //  package-private, so it can be neither built nor driven from outside vanilla, and the
                //  evaluation this method used to inline cannot be reached any more. MaterialSystem#topMaterial
                //  is the public way in, and is what vanilla itself calls to ask the same question about one
                //  position.
                //
                //  Two things go with the inline. topMaterial pins the stone depth above and below at one, where
                //  the loops here measured the real column, and it takes no virtual-block overlay, so blueprint
                //  blocks that are not placed yet are invisible to it. Both only bite below the surface. The two
                //  callers in FallingBlockPlacementHandler walk down from the block that needs support, and only
                //  the first step of that walk stands on the surface -- which is the step topMaterial answers
                //  correctly. Deeper steps now return null and fall through to the handler's DIRT/STONE default.
                final RandomState randomState = serverLevel.getChunkSource().randomState();
                final ChunkAccess chunk = serverLevel.getChunk(location);

                // Whether the column is submerged directly above the queried block: the one thing topMaterial still
                // takes from the caller, and what the old loop derived its water height from. Read it through the
                // overlay when there is one, as that loop did.
                final BlockPos above = location.above();
                final BlockState stateAbove = virtualBlocks == null ? chunk.getBlockState(above) :
                    Objects.requireNonNullElseGet(virtualBlocks.apply(above), () -> chunk.getBlockState(above));

                return randomState.surfaceSystem()
                    .topMaterial(generatorSettings.materialRule().value(),
                        randomState,
                        new WorldGenerationContext(chunkGenerator, serverLevel),
                        serverLevel.getBiomeManager()::getBiome,
                        chunk,
                        randomState.samplersWithContext(SamplerContext.EMPTY_UNCACHED),
                        location,
                        !stateAbove.getFluidState().isEmpty())
                    .orElse(null);
            }
            else if (generator instanceof FlatLevelSource chunkGenerator)
            {
                final List<BlockState> layers = chunkGenerator.settings().getLayers();
                final int locY = location.getY() - serverLevel.getMinY();
                if (locY >= 0 && locY < layers.size())
                {
                    return layers.get(locY);
                }
            }
        }

        return null;
    }


    /**
     * Checks if the block is water.
     *
     * @param iBlockState block state to be checked.
     * @return true if is water.
     */
    public static boolean isWater(final BlockState iBlockState)
    {
        return iBlockState.getBlock() == Blocks.WATER;
    }

    @Deprecated(forRemoval = true, since = "1.21")
    private static Item getItem(final BlockState blockState)
    {
        final Block block = blockState.getBlock();
        if (block.equals(Blocks.LAVA))
        {
            return Items.LAVA_BUCKET;
        }
        else if (block instanceof CropBlock)
        {
            final ItemStack stack = blockState.getCloneItemStack(null, null, false);
            if (stack != null)
            {
                return stack.getItem();
            }

            return Items.WHEAT_SEEDS;
        }
        // oh no... 
        // TODO(port-26.3): DirtPathBlock was renamed and generalised to PathBlock (it now carries the base
        //  block it was made from). Vanilla 26.3 still instantiates it exactly once, for Blocks.DIRT_PATH,
        //  so this test covers the same vanilla blocks as before; a modded path over some other base would
        //  now also answer DIRT here, which is the same approximation 26.2 made for subclasses.
        else if (block instanceof FarmlandBlock || block instanceof PathBlock)
        {
            return getItemFromBlock(Blocks.DIRT);
        }
        else if (block instanceof FireBlock)
        {
            return Items.FLINT_AND_STEEL;
        }
        else if (block instanceof FlowerPotBlock)
        {
            return Items.FLOWER_POT;
        }
        else if (block == Blocks.BAMBOO_SAPLING)
        {
            return Items.BAMBOO;
        }
        else
        {
            return getItemFromBlock(block);
        }
    }

    @Deprecated(forRemoval = true, since = "1.21")
    private static Item getItemFromBlock(final Block block)
    {
        return Item.BY_BLOCK.get(block);
    }

    /**
     * For structure placement, check if two blocks are alike or if action has to be taken.
     * @param structureState the first blockState.
     * @param worldState the second blockState.
     * @param shallReplace the not solid condition.
     * @param fancy if fancy paste.
     * @param tileEntityData
     * @param worldEntity
     * @return true if nothing has to be done.
     */
    public static boolean areBlockStatesEqual(
      final BlockState structureState,
      final BlockState worldState,
      final Predicate<BlockState> shallReplace,
      final boolean fancy,
      final BiPredicate<BlockState, BlockState> specialEqualRule,
      final CompoundTag tileEntityData, final BlockEntity worldEntity)
    {
        if (structureState == null || worldState == null)
        {
            return true;
        }

        final Block structureBlock = structureState.getBlock();
        final Block worldBlock = worldState.getBlock();
        if (fancy && structureBlock == ModBlocks.blockSubstitution.get())
        {
            return true;
        }

        if (worldState.equals(structureState))
        {
            if (tileEntityData == null)
            {
                return true;
            }
            else if (worldEntity == null)
            {
                return false;
            }
            else if (DomumCompat.isMateriallyTexturedBlockEntity(worldEntity) && tileEntityData.contains(DomumCompat.TEXTURE_DATA_TAG))
            {
                return DomumCompat.textureDataMatches(worldEntity, tileEntityData);
            }
            return true;
        }
        else if (DomumCompat.isMateriallyTexturedBlockEntity(worldEntity))
        {
            return false;
        }

        if (fancy)
        {
            if (structureBlock instanceof AirBlock && worldBlock instanceof AirBlock)
            {
                return true;
            }

            if (structureBlock == Blocks.DIRT && worldState.is(BlockTags.DIRT))
            {
                return true;
            }

            if (structureBlock == ModBlocks.blockSolidSubstitution.get() && !shallReplace.test(worldState))
            {
                return true;
            }

            // if the other block has fluid already or is not waterloggable, take no action
            if (
                // structure -> world
                (structureBlock == ModBlocks.blockFluidSubstitution.get() &&
                (worldState.getFluidState().isSource() || !worldState.hasProperty(BlockStateProperties.WATERLOGGED) && BlockUtils.isAnySolid(worldState))) ||
                // world -> structure
                (worldBlock == ModBlocks.blockFluidSubstitution.get() &&
                (structureState.getFluidState().isSource() || !structureState.hasProperty(BlockStateProperties.WATERLOGGED) && BlockUtils.isAnySolid(structureState))))
            {
                return true;
            }
        }

        return specialEqualRule.test(structureState, worldState);
    }

    /**
     * Get a blockState from an itemStack.
     *
     * @param stack the stack to analyze.
     * @return the IBlockState.
     */
    public static BlockState getBlockStateFromStack(final ItemStack stack)
    {
        return getBlockStateFromStack(stack, Blocks.AIR.defaultBlockState());
    }

    /**
     * Get a blockState from an itemStack.
     *
     * @param stack the stack to analyze.
     * @param def   default blockstate if stack is not transformable
     * @return the IBlockState.
     */
    public static BlockState getBlockStateFromStack(final ItemStack stack, final BlockState def)
    {
        if (stack.getItem() == Items.AIR)
        {
            return Blocks.AIR.defaultBlockState();
        }
        else if (stack.getItem() instanceof final BucketItem bucket)
        {
            return bucket.getContent().defaultFluidState().createLegacyBlock();
        }
        else if (stack.getItem() instanceof final BlockItem blockItem)
        {
            return blockItem.getBlock().defaultBlockState();
        }

        return def;
    }

    /**
     * Mimics pick block.
     *
     * @param blockState the block and state we are creating an ItemStack for.
     * @return ItemStack fromt the BlockState.
     * @see BlockToItemHelper
     */
    public static ItemStack getItemStackFromBlockState(final BlockState blockState)
    {
        if (blockState.getBlock() instanceof LiquidBlock)
        {
            // 26.2: LiquidBlock#fluid is protected; the default fluid state carries the same fluid.
            return new ItemStack(blockState.getFluidState().getType().getBucket(), 1);
        }
        final Item item = getItem(blockState);
        if (item != Items.AIR && item != null)
        {
            return new ItemStack(item, 1);
        }

        return new ItemStack(blockState.getBlock(), 1);
    }

    /**
     * Check if a block is matching against the in-world block.
     *
     * @param block    the input block.
     * @param world    the level to check in.
     * @param position the position to check at.
     * @return true if so.
     */
    public static boolean doBlocksMatch(final ItemStack block, final ServerLevel world, final BlockPos position)
    {
        final BlockState blockState = world.getBlockState(position);
        final BlockEntity tileEntity = world.getBlockEntity(position);
        boolean isMatch = false;

        if (block.getItem() == Items.AIR && blockState.isAir())
        {
            isMatch = true;
        }
        else
        {
            final IPlacementHandler handler = PlacementHandlers.getHandler(world, BlockPos.ZERO, blockState);
            final List<ItemStack> itemList =
              handler.getRequiredItems(world, position, blockState, tileEntity == null ? null : tileEntity.saveWithFullMetadata(world.registryAccess()), new SimplePlacementContext(false, RotationMirror.NONE));
            if (!itemList.isEmpty() && ItemStackUtils.compareItemStacksIgnoreStackSize(itemList.get(0), block))
            {
                isMatch = true;
            }
        }

        return isMatch;
    }

    /**
     * Handle the placement of a specific block for a blockState at a certain position with a fakePlayer.
     *
     * @param world      the world object.
     * @param fakePlayer the fake player to place.
     * @param itemStack  the describing itemStack.
     * @param blockState the blockState in the world.
     * @param here       the position.
     */
    public static void handleCorrectBlockPlacement(
        final Level world,
        final FakePlayer fakePlayer,
        final ItemStack itemStack,
        final BlockState blockState,
        final BlockPos here)
    {
        final ItemStack stackToPlace = itemStack.copy();
        final Item item = stackToPlace.getItem();
        stackToPlace.setCount(stackToPlace.getMaxStackSize());

        if (item instanceof AirItem)
        {
            world.removeBlock(here, false);
        }
        else if (item instanceof BlockItem)
        {
            final Block targetBlock = ((BlockItem) item).getBlock();
            BlockState newState = copyFirstCommonBlockStateProperties(targetBlock.defaultBlockState(), blockState);

            if (newState == null)
            {
                fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, stackToPlace);
                if (stackToPlace.is(ItemTags.BEDS) && blockState.hasProperty(HorizontalDirectionalBlock.FACING))
                {
                    fakePlayer.setYRot(blockState.getValue(HorizontalDirectionalBlock.FACING).get2DDataValue() * 90);
                }

                newState = targetBlock.getStateForPlacement(new BlockPlaceContext(new UseOnContext(fakePlayer,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(0, 0, 0),
                        // TODO(port-26.3): BedItem was removed together with the other item subclasses; the
                        //  bed test now uses the same ItemTags.BEDS tag the branch above already uses.
                        itemStack.is(ItemTags.BEDS) ? Direction.UP : Direction.NORTH,
                        here,
                        true))));

                if (newState == null)
                {
                    return;
                }
            }

            // place
            world.setBlock(here, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            world.setBlock(here, newState, Constants.UPDATE_FLAG);
            final BlockEntity blockEntity = world.getBlockEntity(here);
            if (blockEntity != null)
            {
                blockEntity.applyComponentsFromItemStack(stackToPlace);
            }
            targetBlock.setPlacedBy(world, here, newState, fakePlayer, stackToPlace);
        }
        else if (item instanceof BucketItem)
        {
            final Block sourceBlock = blockState.getBlock();
            final BucketItem bucket = (BucketItem) item;
            final Fluid fluid = bucket.getContent();

            // place
            if (sourceBlock instanceof final LiquidBlockContainer liquidContainer)
            {
                if (liquidContainer.canPlaceLiquid(fakePlayer, world, here, blockState, fluid))
                {
                    liquidContainer.placeLiquid(world, here, blockState, fluid.defaultFluidState());
                    bucket.checkExtraContent(null, world, stackToPlace, here);
                }
            }
            else
            {
                world.setBlock(here, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                world.setBlock(here, fluid.defaultFluidState().createLegacyBlock(), Constants.UPDATE_FLAG);
                bucket.checkExtraContent(null, world, stackToPlace, here);
            }
        }
        else
        {
            throw new IllegalArgumentException(
                MessageFormat.format("Cannot handle placing of {0} instead of {1}?!", itemStack.toString(), blockState.toString()));
        }
    }

    /**
     * Removes the fluid from the given position.
     * 
     * @param world the world to remove the fluid from.
     * @param pos   the position where to remove the fluid.
     */
    public static void removeFluid(Level world, BlockPos pos)
    {
        final BlockState state = world.getBlockState(pos);
        final Block block = state.getBlock();
        if((!(block instanceof final BucketPickup bucketBlock) || bucketBlock.pickupBlock(null, world, pos, state).isEmpty()) && block instanceof LiquidBlock)
        {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Constants.UPDATE_FLAG);
        }
    }

    /**
     * A simple check to fetch the default fluid block for this dimension
     * @param world the world of the dimension
     * @return the default blockstate for the default fluid
     */
    public static BlockState getFluidForDimension(final Level world)
    {
        if (world instanceof ServerLevel serverLevel)
        {
            final ChunkGenerator generator = serverLevel.getChunkSource().getGenerator();
            if (generator instanceof NoiseBasedChunkGenerator chunkGenerator)
            {
                final BlockState defaultFluid = chunkGenerator.generatorSettings().value().defaultFluid();
                if (!defaultFluid.getFluidState().isEmpty())
                {
                    return defaultFluid;
                }
            }
        }
        // 26.2: DimensionType#ultraWarm is gone, the same fact now lives in the WATER_EVAPORATES environment attribute.
        return world == null || !world.environmentAttributes().getDimensionValue(EnvironmentAttributes.WATER_EVAPORATES)
                 ? Blocks.WATER.defaultBlockState() : Blocks.LAVA.defaultBlockState();
    }

    /**
     * A simple check to fetch the default block for this dimension
     * @param level the world of the dimension
     * @param defaultValue return this if unable to get default block for given level
     * @return the default blockstate 
     */
    public static BlockState getDefaultBlockForLevel(final Level level, final BlockState defaultValue)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            final ChunkGenerator generator = serverLevel.getChunkSource().getGenerator();
            if (generator instanceof NoiseBasedChunkGenerator chunkGenerator)
            {
                return chunkGenerator.generatorSettings().value().defaultBlock();
            }
        }
        return defaultValue;
    }

    /**
     * Returns a list of drops possible mining a specific block with specific
     * fortune level.
     *
     * @param world   World the block is in.
     * @param coords  Coordinates of the block.
     * @param fortune Level of fortune on the pickaxe.
     * @param stack the tool.
     * @return List of {@link ItemStack} with possible drops.
     */
    public static List<ItemStack> getBlockDrops(final Level world, final BlockPos coords, final int fortune, final ItemStack stack)
    {
        if (!(world instanceof final ServerLevel serverLevel))
        {
            throw new IllegalArgumentException("trying to get block drops at client side?!");
        }
        return world.getBlockState(coords)
            .getDrops(new LootParams.Builder(serverLevel).withLuck(fortune)
                .withParameter(LootContextParams.ORIGIN, Vec3.atLowerCornerOf(coords))
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, world.getBlockEntity(coords))
                .withParameter(LootContextParams.TOOL, stack));
    }


    /**
     * Copies property values from propertiesOrigin into new blockstate made from target Block.
     * If source and target are not the same block find the first common superclass and use its properties.
     *
     * @param target           properties destination
     * @param propertiesOrigin properties source
     * @return blockState of target block with properties of common super class or null if no common superclass found
     */
    public static BlockState copyFirstCommonBlockStateProperties(final BlockState target, final BlockState propertiesOrigin)
    {
        BlockState newState = target;
        for (final Property<?> property : propertiesOrigin.getProperties())
        {
            if (target.hasProperty(property))
            {
                newState = copyProperty(propertiesOrigin, newState, property);
            }
        }

        return newState;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(final BlockState from, final BlockState to, final Property<T> property)
    {
        return to.setValue(property, from.getValue(property));
    }

    /**
     * @return true iff block can exist without any support (cannot decay, {@link Block#canSurvive(BlockState, LevelReader, BlockPos)} always return true)
     */
    public static boolean canBlockFloatInAir(final BlockState blockState)
    {
        if (blockState.getBlock() instanceof LeavesBlock)
        {
            return !blockState.isRandomlyTicking();
        }
        return trueSolidBlocks.contains(blockState.getBlock());
    }

    /**
     * @return true iff block is any variant of any fluid (waterlogged etc doesnt count)
     */
    public static boolean isLiquidOnlyBlock(final BlockState blockState)
    {
        return blockState.liquid() || isLiquidOnlyBlock(blockState.getBlock());
    }

    /**
     * @return true iff block is any variant of any fluid (waterlogged etc doesnt count)
     */
    public static boolean isLiquidOnlyBlock(final Block block)
    {
        return block instanceof LiquidBlock || block instanceof BubbleColumnBlock;
    }

    /**
     * @return true iff block MAY require any support to exist (can decay/fall) and also MAY be support to floating blocks
     */
    public static boolean isWeakSolidBlock(final BlockState blockState)
    {
        if (blockState.getBlock() instanceof LeavesBlock)
        {
            return blockState.isRandomlyTicking();
        }

        if (blockState.canBeReplaced() || !blockState.getBlock().hasCollision)
        {
            return false;
        }

        final Block block = blockState.getBlock();
        return block.builtInRegistryHolder().is(ModTags.WEAK_SOLID_BLOCKS) && canBlockSurviveWithoutSupport(block);
    }

    public static boolean canBlockSurviveWithoutSupport(final Block block)
    {
        // TODO: add tag
        // TODO(port-26.3): DirtPathBlock -> PathBlock, see getItemFromBlock above.
        if (block instanceof FarmlandBlock || block instanceof PathBlock)
        {
            return true;
        }
        try
        {
            return block.defaultBlockState().canSurvive(null, null);
        }
        catch (final Exception e)
        {
            return false;
        }
    }

    /**
     * Check if a block is a standard full block.
     * @param block the block to check.
     * @return true if so.
     */
    public static boolean isGoodFullBlock(final BlockState block)
    {
        try
        {
            return block.getShape(null, null) == Shapes.block();
        }
        catch (final Exception e)
        {
            return false;
        }
    }

    public static boolean isAnySolid(final BlockState blockState)
    {
        return canBlockFloatInAir(blockState) || isWeakSolidBlock(blockState);
    }

    public static boolean isGoodFloorBlock(final BlockState blockState)
    {
        return (isGoodFullBlock(blockState) && !blockState.is(ModTags.UNSUITABLE_SOLID_FOR_PLACEHOLDER)) || blockState.is(GOOD_SOLID_FOR_PLACEHOLDER);
    }

    public static SolidnessInfo getSolidInfo(final BlockState blockState)
    {
        return new SolidnessInfo(canBlockFloatInAir(blockState), isWeakSolidBlock(blockState));
    }

    public record SolidnessInfo(boolean canFloatInAir, boolean isWeakSolid)
    {
        public boolean isAnySolid()
        {
            return canFloatInAir || isWeakSolid;
        }
    }
}
