package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.modules.IModuleWithExternalBlocks;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.NBTUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.ITEMLIST_FUEL;

/**
 * Module for all workers that need a furnace.
 */
public class FurnaceUserModule extends AbstractBuildingModule implements IPersistentModule, IModuleWithExternalBlocks, IAltersRequiredItems, ITickingModule
{
    /**
     * Tag to store the furnace position.
     */
    private static final String TAG_POS = "pos";

    /**
     * Tag to store the furnace position in compatibility (Baker)
     */
    private static final String TAG_POS_COMPAT = "furnacePos";

    /**
     * Tag to store the furnace list.
     */
    private static final String TAG_FURNACES = "furnaces";

    /**
     * List of registered furnaces.
     */
    private final List<BlockPos> furnaces = new ArrayList<>();

    /**
     * Whether the one-off footprint sweep for this load has already run.
     */
    private boolean rescanned = false;

    /**
     * Construct a new furnace user module.
     */
    public FurnaceUserModule()
    {
        super();
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        // Read into a cleared list, the way every other position-holding module does. Nothing loads a module
        // twice today, so this does not reproduce; the alchemist's duplicated brewing stands were exactly this
        // shape one save cycle later, and a duplicated position here would be a silent multiplier on output.
        furnaces.clear();
        rescanned = false;
        final ListTag furnaceTagList = compound.getListOrEmpty(TAG_FURNACES);
        for (int i = 0; i < furnaceTagList.size(); ++i)
        {
            furnaces.add(NBTUtils.readBlockPos(furnaceTagList.get(i)));
        }
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        @NotNull final ListTag furnacesTagList = new ListTag();
        for (@NotNull final BlockPos entry : furnaces)
        {
            furnacesTagList.add(NBTUtils.writeBlockPos(entry));
        }
        compound.put(TAG_FURNACES, furnacesTagList);
    }

    @Override
    public void alterItemsToBeKept(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
    {
        consumer.accept(this::isAllowedFuel, STACKSIZE * building.getBuildingLevel(), false);
    }

    /**
     * Remove a furnace from the building.
     *
     * @param pos the position of it.
     */
    public void removeFromFurnaces(final BlockPos pos)
    {
        furnaces.remove(pos);
    }

    /**
     * Check if an ItemStack is one of the accepted fuel items.
     *
     * @param stack the itemStack to check.
     * @return true if so.
     */
    public boolean isAllowedFuel(final ItemStack stack)
    {
        if (ItemStackUtils.isEmpty(stack))
        {
            return false;
        }
        return building.getModule(ITEMLIST_FUEL).isItemInList(new ItemStorage(stack));
    }

    /**
     * Return a list of furnaces assigned to this hut.
     *
     * @return copy of the list
     */
    public List<BlockPos> getFurnaces()
    {
        return new ArrayList<>(furnaces);
    }

    @Override
    public void onBlockPlacedInBuilding(@NotNull final BlockState blockState, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        if (isUsableCookingBlock(blockState) && !furnaces.contains(pos))
        {
            furnaces.add(pos);
        }
    }

    /**
     * Whether this hut adopts the block as one of its ovens.
     * <p>
     * A plain furnace counts everywhere. The other vanilla cooking blocks are opt-in per building rather than
     * accepted across the board, for two reasons: the cook, kitchen and dyer blueprints already ship smokers as
     * decoration, so accepting them wholesale would silently hand those workers extra ovens; and their AI
     * ({@code AbstractEntityAIUsesFurnace}) only knows how to drive a {@code FurnaceBlockEntity}, so those ovens
     * would be listed and then never used. Only buildings whose AI has been widened to the abstract cooking block
     * entity opt in.
     *
     * @param blockState the state to test.
     * @return true if it should be registered.
     */
    private boolean isUsableCookingBlock(@NotNull final BlockState blockState)
    {
        if (blockState.getBlock() instanceof FurnaceBlock)
        {
            return true;
        }
        return acceptsAllCookingBlocks() && blockState.getBlock() instanceof AbstractFurnaceBlock;
    }

    /**
     * Whether this hut's worker can drive the non-furnace cooking blocks.
     * <p>
     * The bakery can: its AI is {@code AbstractEntityAIRequestSmelter}, which works against the abstract cooking
     * block entity, and the mod's own datapack already carries smoking recipes for every dough it makes -- a third
     * of the furnace time. Four of the shipped bakery blueprints (caledonia 3-5, sandstone 5) contain a smoker that
     * was being ignored.
     *
     * @return true if more than the plain furnace is usable here.
     */
    private boolean acceptsAllCookingBlocks()
    {
        return building instanceof BuildingBaker;
    }

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (rescanned || !acceptsAllCookingBlocks())
        {
            return;
        }

        final Level world = colony.getWorld();
        final Tuple<BlockPos, BlockPos> corners = building.getCorners();
        if (world == null || corners == null
              || !WorldUtil.isBlockLoaded(world, corners.getA()) || !WorldUtil.isBlockLoaded(world, corners.getB()))
        {
            return;
        }

        // Ovens are otherwise only ever registered while the builder places the blueprint, so a hut that was
        // already standing when its module learned a new block type would never notice the ones it now accepts.
        // Sweep the footprint once per load for exactly those: plain furnaces keep the upstream rule of being
        // adopted at build time and no other time, so nothing here changes how many of them a hut has. Positions
        // already known are skipped and nothing is removed.
        final boolean[] complete = {true};
        BlockPos.betweenClosedStream(corners.getA(), corners.getB()).forEach(pos -> {
            // Per position, not per corner: a hut footprint can straddle more than the two chunks the corners are
            // in, and getBlockState on an unloaded one would pull that chunk in from disk on the colony thread.
            if (!WorldUtil.isBlockLoaded(world, pos))
            {
                complete[0] = false;
                return;
            }
            final BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof AbstractFurnaceBlock && !(state.getBlock() instanceof FurnaceBlock)
                  && !furnaces.contains(pos))
            {
                furnaces.add(pos.immutable());
            }
        });

        // Only counts as done when the whole footprint was readable; a sweep that had to skip part of the hut is
        // repeated on a later tick, by which time the missing chunks are usually in.
        rescanned = complete[0];
    }

    @Override
    public List<BlockPos> getRegisteredBlocks()
    {
        return new ArrayList<>(furnaces);
    }
}
