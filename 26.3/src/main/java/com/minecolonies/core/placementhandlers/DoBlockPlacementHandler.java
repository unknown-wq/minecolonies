package com.minecolonies.core.placementhandlers;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.domumornamentum.IDomumOrnamentumApi;
import com.ldtteam.domumornamentum.block.AbstractPostBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.decorative.*;
import com.ldtteam.domumornamentum.block.types.FancyTrapdoorType;
import com.ldtteam.domumornamentum.block.types.PostType;
import com.ldtteam.domumornamentum.block.types.TrapdoorType;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.block.vanilla.DoorBlock;
import com.ldtteam.domumornamentum.block.vanilla.TrapdoorBlock;
import com.ldtteam.domumornamentum.util.BlockUtils;
import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.util.InventoryUtils;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
// 26.2: IPlacementHandler#doesWorldStateMatchBlueprintState takes Structurize's own Tuple now --
// net.minecraft.util.Tuple is gone and each mod grew its own replacement.
import com.ldtteam.structurize.api.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ldtteam.structurize.placement.handlers.placement.DoBlockPlacementHandler.compareBEData;
import static com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.handleTileEntityPlacement;

public class DoBlockPlacementHandler implements IPlacementHandler
{
    @Override
    public boolean canHandle(@NotNull final Level world, @NotNull final BlockPos pos, @NotNull final BlockState blockState)
    {
        return blockState.getBlock() instanceof IMateriallyTexturedBlock && blockState.getBlock() != ModBlocks.blockRack;
    }

    @Override
    public ActionProcessingResult handle(
      @NotNull final Level world,
      @NotNull final BlockPos pos,
      @NotNull final BlockState blockState,
      @Nullable final CompoundTag tileEntityData,
      @NotNull final IPlacementContext placementContext)
    {
        BlockState placementState = blockState;
        if (blockState.getBlock() instanceof WallBlock
            || blockState.getBlock() instanceof FenceBlock
            || blockState.getBlock() instanceof IronBarsBlock
            || blockState.getBlock() instanceof PillarBlock)
        {
            try
            {
                final BlockState tempState = blockState.getBlock().getStateForPlacement(
                    new BlockPlaceContext(world, null, InteractionHand.MAIN_HAND, ItemStack.EMPTY,
                        new BlockHitResult(new Vec3(0, 0, 0), Direction.DOWN, pos, true)));
                if (tempState != null)
                {
                    placementState = tempState;
                }
            }
            catch (final Exception ex)
            {
                // Noop
            }
        }

        if (world.getBlockState(pos).equals(placementState))
        {
            world.removeBlock(pos, false);
        }

        if (!WorldUtil.setBlockState(world, pos, placementState, Constants.UPDATE_FLAG))
        {
            return ActionProcessingResult.PASS;
        }

        if (tileEntityData != null)
        {
            try
            {
                handleTileEntityPlacement(tileEntityData, world, pos, placementContext.getRotationMirror());
                // 26.2: Block#getCloneItemStack(BlockState, HitResult, LevelReader, BlockPos, Player) became
                // BlockState#getCloneItemStack(LevelReader, BlockPos, boolean) -- the hit result and the player
                // are gone, and "include data" replaces them.
                placementState.getBlock().setPlacedBy(world, pos, placementState, null,
                  placementState.getCloneItemStack(world, pos, true));
            }
            catch (final Exception ex)
            {
                Log.getLogger().warn("Unable to place TileEntity");
            }
        }

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
        if (tileEntityData != null)
        {
            BlockPos blockpos = new BlockPos(tileEntityData.getIntOr("x", 0), tileEntityData.getIntOr("y", 0), tileEntityData.getIntOr("z", 0));
            final BlockEntity tileEntity = BlockEntity.loadStatic(blockpos, blockState, tileEntityData, world.registryAccess());
            if (tileEntity == null)
            {
                return Collections.emptyList();
            }

            final Property<?> property;
            if (blockState.getBlock() instanceof DoorBlock)
            {
                property = DoorBlock.TYPE;
            }
            else if (blockState.getBlock() instanceof FancyDoorBlock)
            {
                property = FancyDoorBlock.TYPE;
            }
            else if (blockState.getBlock() instanceof TrapdoorBlock)
            {
                property = TrapdoorBlock.TYPE;
            }
            else if (blockState.getBlock() instanceof FancyTrapdoorBlock)
            {
                property = FancyTrapdoorBlock.TYPE;
            }
            else if (blockState.getBlock() instanceof PanelBlock)
            {
                property = PanelBlock.TYPE;
            }
            else if (blockState.getBlock() instanceof AbstractPostBlock<?>)
            {
                property = AbstractPostBlock.TYPE;
            }
            else
            {
                property = null;
            }
            itemList.add(asObtainableItem(getCorrectDOItem(property == null ? BlockUtils.getMaterializedItemStack(tileEntity, world.registryAccess()) : BlockUtils.getMaterializedItemStack(tileEntity, world.registryAccess(), property), blockState, !placementContext.fancyPlacement())));
        }
        itemList.removeIf(ItemStackUtils::isEmpty);
        return itemList;
    }

    /**
     * Reduce a materialised Domum Ornamentum stack to the shape a player can actually hold, so that the
     * requirement the builder records is component-for-component the item that will be handed to it.
     * <p>
     * This matters beyond {@link ItemStackUtils#compareItemStacksIgnoreStackSize}, which only looks at the
     * keys {@code data/minecolonies/compatibility/itemnbtmatching.json} lists for the item (for a DO block:
     * {@code domum_ornamentum:texture_data} and, where the shape is a block state, {@code minecraft:block_state}).
     * The builder's own bookkeeping does <em>not</em> go through that comparison: every needed-resource map in
     * the mod is keyed by the literal string {@code item.getDescriptionId() + "-" + stack.getComponentsPatch().hashCode()}
     * -- see BuildingResourcesModule#addNeededResource, AbstractBuildingStructureBuilder#hasResourceInBucket and
     * #buildingRequiresCertainAmountOfItem, and ItemResourceScroll#getWarehouseSnapshot. Any component on the
     * requirement that the player's copy cannot have puts the two stacks in different buckets, and the builder
     * then refuses to keep, count or report a block that it happily compares equal to.
     * <p>
     * Two such components are produced by the blueprint path:
     * <ol>
     * <li>{@code minecraft:block_entity_data}. Domum Ornamentum's {@code DynamicTimberFrameBlockEntity#saveToItem}
     *     calls {@code BlockItem.setBlockEntityData} on top of the components (the other DO block entities do
     *     not), so every stack materialised from a {@code domum_ornamentum:dynamic_timberframe} in a blueprint
     *     carries a copy of the tile entity. {@link #getCorrectDOItem} then converts it to the canonical
     *     {@code domum_ornamentum:framed} and copies the patch across, tag and all. Nothing an architect's
     *     cutter, a loot drop or a delivery ever produces has that component.</li>
     * <li>Texture components the target block does not declare. The tile entity loads its texture data
     *     verbatim ({@code MateriallyTexturedBlockEntity#loadAdditional}, no {@code retainComponentsFromBlock}),
     *     so a blueprint saved against an older Domum Ornamentum keeps skins for components that have since
     *     been dropped -- shipped shingle slabs still carry the third {@code minecraft:block/acacia_planks} skin
     *     that {@code ShingleSlabBlock} no longer has. That one is fatal even to the lenient comparison, because
     *     texture data is compared as a whole map.</li>
     * </ol>
     *
     * @param stack the materialised stack, modified in place.
     * @return the same stack.
     */
    public static ItemStack asObtainableItem(final ItemStack stack)
    {
        if (ItemStackUtils.isEmpty(stack))
        {
            return stack;
        }

        stack.remove(DataComponents.BLOCK_ENTITY_DATA);

        if (stack.getItem() instanceof final BlockItem blockItem
              && blockItem.getBlock() instanceof final IMateriallyTexturedBlock texturedBlock)
        {
            final MaterialTextureData data = MaterialTextureData.readFromItemStack(stack);
            if (!data.isEmpty())
            {
                final Map<Identifier, Block> known = new LinkedHashMap<>();
                for (final IMateriallyTexturedBlockComponent component : texturedBlock.getComponents())
                {
                    final Block skin = data.getTexturedComponents().get(component.getId());
                    if (skin != null)
                    {
                        known.put(component.getId(), skin);
                    }
                }

                if (known.size() != data.getTexturedComponents().size())
                {
                    if (known.isEmpty())
                    {
                        stack.remove(IDomumOrnamentumApi.getInstance().getMaterialTextureComponentType());
                    }
                    else
                    {
                        new MaterialTextureData(known).writeToItemStack(stack);
                    }
                }
            }
        }

        return stack;
    }

    @Override
    public boolean doesWorldStateMatchBlueprintState(final BlockState worldState, final BlockState blueprintState, final Tuple<BlockEntity, CompoundTag> blockEntityData, final @NotNull IPlacementContext structureHandler)
    {
        if (blueprintState.getBlock() == worldState.getBlock()
            && (blueprintState.getBlock() instanceof WallBlock
            || blueprintState.getBlock() instanceof FenceBlock
            || blueprintState.getBlock() instanceof IronBarsBlock
            || blueprintState.getBlock() instanceof FenceGateBlock)
            && compareBEData(blockEntityData))
        {
            return true;
        }

        return worldState.equals(blueprintState) && compareBEData(blockEntityData);
    }

    /**
     * Calculate the correct DO item.
     * Considering type and, for the builder we do want the generic type to be used here.
     * @param item the item to output.
     * @param blockState the blockstate in the world.
     * @return the adjusted item.
     */
    public static ItemStack getCorrectDOItem(final ItemStack item, final BlockState blockState, final boolean complete)
    {
        final BlockItemStateProperties properties = item.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        if (blockState.getBlock() instanceof TrapdoorBlock)
        {
            item.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> props.with(TrapdoorBlock.TYPE, complete ? blockState.getValue(TrapdoorBlock.TYPE) : TrapdoorType.FULL));
        }
        else if (blockState.getBlock() instanceof FancyTrapdoorBlock)
        {
            item.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> props.with(FancyTrapdoorBlock.TYPE, complete ? blockState.getValue(FancyTrapdoorBlock.TYPE) : FancyTrapdoorType.FULL));
        }
        else if (blockState.getBlock() instanceof PanelBlock)
        {
            item.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> props.with(PanelBlock.TYPE, complete ? blockState.getValue(PanelBlock.TYPE) : TrapdoorType.FULL));
        }
        else if (blockState.getBlock() instanceof AbstractPostBlock<?>)
        {
            item.update(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY, props -> props.with(PostBlock.TYPE, complete ? blockState.getValue(PostBlock.TYPE) : PostType.PLAIN));
        }
        else if (blockState.getBlock() instanceof TimberFrameBlock || blockState.getBlock() instanceof DynamicTimberFrameBlock)
        {
            if (complete)
            {
                return item;
            }
            final ItemStack tempItem = new ItemStack(com.ldtteam.domumornamentum.block.ModBlocks.getInstance().getTimberFrames().get(2));
            // Copy the *patch* -- what this particular stack carries on top of its item's defaults -- and not the
            // resolved component map.  The resolved map contains the source item's prototype too, and in 26.2 that
            // prototype is no longer interchangeable between two items: every item now carries its own item_name
            // and item_model (Item's constructor sets both from the item's own id, /opt/mc-src Item.java:136).
            // Copying the whole map therefore stamped, say, a double_crossed timber frame's name and model onto the
            // generic framed one, so the resulting stack was no longer equal to a framed frame the player crafts --
            // both are checked by ItemStackUtils#compareItemStacksIgnoreStackSize.  The patch holds exactly the
            // texture data (and nothing else, for a stack that came out of a block entity), which is the whole
            // point of the conversion.  In 1.21.1 the two maps were interchangeable, which is why upstream copies
            // the resolved one.
            tempItem.applyComponents(item.getComponentsPatch());
            return tempItem;
        }
        return item;
    }

    @Override
    public void handleRemoval(
            final IStructureHandler handler,
            final Level world,
            final BlockPos pos)
    {
        if (!handler.isCreative())
        {
            final List<ItemStack> items = com.ldtteam.structurize.util.BlockUtils.getBlockDrops(world, pos, 0, handler.getHeldItem());
            for (final ItemStack item : items)
            {
                final BlockState state = world.getBlockState(pos);
                InventoryUtils.transferIntoNextBestSlot(getCorrectDOItem(item, state, false),
                  handler.getInventory());
            }
        }
        world.removeBlock(pos, false);
    }
}
