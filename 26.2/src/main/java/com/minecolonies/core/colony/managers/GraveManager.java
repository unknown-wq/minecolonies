package com.minecolonies.core.colony.managers;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.GraveData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.managers.interfaces.IGraveManager;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.blocks.BlockMinecoloniesGrave;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.tileentities.TileEntityGrave;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateConstants.MAX_TICKRATE;
import static com.minecolonies.api.research.util.ResearchConstants.GRAVE_DECAY_BONUS;
import static com.minecolonies.api.util.constant.NbtTagConstants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.WARNING_GRAVE_LAVA;
import static com.minecolonies.api.util.constant.TranslationConstants.WARNING_GRAVE_WATER;

public class GraveManager implements IGraveManager
{
    /**
     * List of grave in the colony.
     */
    @NotNull
    private final Map<BlockPos, Boolean> graves = new HashMap<>();

    /**
     * The colony of the manager.
     */
    private final Colony colony;

    /**
     * Creates the GraveManager for a colony.
     *
     * @param colony the colony.
     */
    public GraveManager(final Colony colony)
    {
        this.colony = colony;
    }

    /**
     * Read the graves from NBT.
     *
     * @param compound the compound.
     */
    @Override
    public void read(@NotNull final CompoundTag compound)
    {
        graves.clear();
        final ListTag gravesTagList = compound.getListOrEmpty(TAG_GRAVE);
        for (int i = 0; i < gravesTagList.size(); ++i)
        {
            final CompoundTag graveCompound = gravesTagList.getCompoundOrEmpty(i);
            if (graveCompound.contains(TAG_POS) && graveCompound.contains(TAG_RESERVED))
            {
                graves.put(BlockPosUtil.read(graveCompound, TAG_POS), graveCompound.getBooleanOr(TAG_RESERVED, false));
            }
        }
    }

    /**
     * Write the graves to NBT.
     *
     * @param compound the compound.
     */
    @Override
    public void write(@NotNull final CompoundTag compound)
    {
        @NotNull final ListTag gravesTagList = new ListTag();
        for (@NotNull final BlockPos blockPos : graves.keySet())
        {
            @NotNull final CompoundTag graveCompound = new CompoundTag();
            BlockPosUtil.write(graveCompound, TAG_POS, blockPos);
            graveCompound.putBoolean(TAG_RESERVED, graves.get(blockPos));
            gravesTagList.add(graveCompound);
        }
        compound.put(TAG_GRAVE, gravesTagList);
    }

    /**
     * Ticks all grave when this building manager receives a tick.
     *
     * @param colony the colony which is being ticked.
     */
    @Override
    public void onColonyTick(final IColony colony)
    {
        // A raid is when citizens die, and CitizenAI puts every non-guard - the undertaker with them - to sleep for
        // the whole of it. The decay clock used to keep running through that, so a grave opened at the start of a
        // raid could be most of the way to gone before the man whose job it is was allowed out of the door. Hold the
        // clock while the colony is under attack; nothing else about the grave changes, and no citizen is sent
        // anywhere he would not otherwise go.
        final boolean raided = colony.getRaiderManager().isRaided();

        for (final Iterator<BlockPos> iterator = graves.keySet().iterator(); iterator.hasNext(); )
        {
            final BlockPos pos = iterator.next();
            if (!WorldUtil.isBlockLoaded(colony.getWorld(), pos))
            {
                continue;
            }

            final BlockEntity graveEntity = colony.getWorld().getBlockEntity(pos);
            if (!(graveEntity instanceof TileEntityGrave))
            {
                iterator.remove();
                colony.markDirty();
                continue;
            }

            if (raided)
            {
                continue;
            }

            if (!((TileEntityGrave) graveEntity).onColonyTick(MAX_TICKRATE))
            {
                iterator.remove();
                colony.markDirty();
            }
        }
    }

    /**
     * Returns a map with all graves within the colony. Key is ID (Coordinates), value is isReserved boolean.
     *
     * @return Map with ID (coordinates) as key, value is isReserved boolean.
     */
    @NotNull
    @Override
    public Map<BlockPos, Boolean> getGraves()
    {
        return graves;
    }

    /**
     * Add a grave from the Colony.
     *
     * @param pos position of the TileEntityGrave to add.
     * @return the grave that was created and added.
     */
    @Override
    public boolean addNewGrave(@NotNull final BlockPos pos)
    {
        final TileEntityGrave graveEntity = (TileEntityGrave) colony.getWorld().getBlockEntity(pos);
        if (graveEntity == null)
        {
            return false;
        }

        if (graves.containsKey(pos))
        {
            return true;
        }

        graves.put(pos, false);
        colony.markDirty();
        return true;
    }

    /**
     * Remove a TileEntityGrave from the Colony (when it is destroyed).
     *
     * @param pos position of the TileEntityGrave to remove.
     */
    @Override
    public void removeGrave(@NotNull final BlockPos pos)
    {
        graves.remove(pos);
        colony.markDirty();
    }

    /**
     * Reserve a grave
     *
     * @param pos the id of the grave.
     * @return is the grave successfully reserved.
     */
    @Override
    public boolean reserveGrave(@NotNull final BlockPos pos)
    {
        if (!graves.containsKey(pos) || graves.get(pos))
        {
            return false;
        }

        graves.put(pos, true);
        colony.markDirty();
        return true;
    }

    @Override
    public void unReserveGrave(@NotNull final BlockPos pos)
    {
        if (graves.containsKey(pos) && graves.get(pos))
        {
            graves.put(pos, false);
            colony.markDirty();
        }
    }

    /**
     * Reserve the next free grave
     *
     * @return the grave successfully reserved or null if none available
     */
    @Override
    public BlockPos reserveNextFreeGrave()
    {
        // Take the grave closest to being gone, not the first one HashMap iteration order happens to produce. After
        // a raid there are several outstanding at once and the undertaker used to walk past one with a minute left
        // to reach one with twenty, which is how a player loses the gear off a dead guard.
        BlockPos mostUrgent = null;
        int leastRemaining = Integer.MAX_VALUE;

        for (@NotNull final BlockPos pos : new ArrayList<>(graves.keySet()))
        {
            if (!WorldUtil.isBlockLoaded(colony.getWorld(), pos))
            {
                continue;
            }

            final BlockEntity graveEntity = colony.getWorld().getBlockEntity(pos);
            if (!(graveEntity instanceof final TileEntityGrave grave))
            {
                // removeGrave, not graves.remove: the bare map removal never marked the colony dirty, so a stale
                // entry dropped here could come straight back on the next load.
                removeGrave(pos);
                continue;
            }

            if (Boolean.TRUE.equals(graves.get(pos)))
            {
                continue;
            }

            final int remaining = grave.getRemainingDecayTicks();
            if (mostUrgent == null || remaining < leastRemaining)
            {
                mostUrgent = pos;
                leastRemaining = remaining;
            }
        }

        if (mostUrgent != null && reserveGrave(mostUrgent))
        {
            return mostUrgent;
        }

        return null;
    }

    /**
     * Attempt to create a TileEntityGrave at @pos containing the specific @citizenData
     * <p>
     * On failure: drop all the citizen inventory on the ground.
     *
     * @param world       The world.
     * @param pos         The position where to spawn a grave
     * @param citizenData The citizenData
     */
    @Override
    public BlockPos createCitizenGrave(final Level world, final BlockPos pos, final ICitizenData citizenData)
    {
        final BlockState here = world.getBlockState(pos);
        if (here.getBlock() == Blocks.LAVA)
        {
            // A grave placed in lava would be pointless, but returning without dropping anything used to make this
            // the one death in the game that destroys fire-resistant gear. A player who falls in lava drops
            // everything and walks back to find his netherite floating in it; a citizen's netherite was deleted.
            // Drop the lot where he fell and let the lava decide, which is what vanilla does.
            MessageUtils.format(WARNING_GRAVE_LAVA).sendTo(colony).forManagers();
            InventoryUtils.dropCitizenInventory(citizenData.getInventory(), world, pos.getX(), pos.getY(), pos.getZ());
            return null;
        }

        BlockPos firstValidPosition = null;
        if (here.getBlock() == Blocks.WATER)
        {
            for (int i = 1; i <= 10; i++)
            {
                final BlockPos surface = pos.above(i);
                if (world.getBlockState(surface).getBlock() instanceof AirBlock)
                {
                    // Search around the surface that was just found, not around the body. The old call centred the
                    // box on the drowned citizen with a vertical range of 1 - three y-levels of water for anyone who
                    // drowns in open water, and never able to contain the air block the loop had just located - so
                    // the search failed every time and the whole inventory, armour included, went into the sea.
                    firstValidPosition = BlockPosUtil.findAround(world, surface, 3, 16,
                      (blockAccess, current) ->
                        blockAccess.getBlockState(current).isAir() &&
                          BlockUtils.isAnySolid(blockAccess.getBlockState(current.below())));
                    break;
                }
            }

            if (firstValidPosition == null)
            {
                MessageUtils.format(WARNING_GRAVE_WATER).sendTo(colony).forManagers();
            }
        }
        else
        {
            firstValidPosition = BlockPosUtil.findAround(world, pos, 10, 10,
              (blockAccess, current) ->
                blockAccess.getBlockState(current).isAir() &&
                  BlockUtils.isAnySolid(blockAccess.getBlockState(current.below())));
        }


        if (firstValidPosition != null)
        {
            world.setBlockAndUpdate(firstValidPosition,
              BlockMinecoloniesGrave.getPlacementState(ModBlocks.blockGrave.defaultBlockState(), firstValidPosition));
            final TileEntityGrave graveEntity = (TileEntityGrave) world.getBlockEntity(firstValidPosition);
            if (!InventoryUtils.transferAllItemHandler(citizenData.getInventory(), graveEntity.getInventory()))
            {
                InventoryUtils.dropItemHandler(citizenData.getInventory(), world, pos.getX(), pos.getY(), pos.getZ());
            }
            // Two things this loop has to get right, and used to get wrong.
            //
            // It walks InventoryCitizen.ARMOR_SLOTS rather than EquipmentSlot.values() filtered by isArmor():
            // BODY passes isArmor() and shares armour index 0 with FEET, so the old loop read the boots on the FEET
            // iteration and again on the BODY one and put two pairs in the grave, on every death of a booted citizen.
            //
            // And it clears each slot as it copies it, because the citizen NBT the grave keeps for the resurrection
            // is serialised further down. Copying without clearing left the armour in that snapshot as well, so a
            // successful resurrection handed the colony one set out of the grave and put a second set back on the
            // citizen who walked out of it.
            for (final EquipmentSlot equipmentSlot : InventoryCitizen.ARMOR_SLOTS)
            {
                final ItemStack stack = citizenData.getInventory().getArmorInSlot(equipmentSlot);
                if (ItemStackUtils.isEmpty(stack))
                {
                    continue;
                }

                citizenData.getInventory().forceClearArmorInSlot(equipmentSlot, stack);
                if (!InventoryUtils.addItemStackToItemHandler(graveEntity.getInventory(), stack))
                {
                    InventoryUtils.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }


            graveEntity.delayDecayTimer(colony.getResearchManager().getResearchEffects().getEffectStrength(GRAVE_DECAY_BONUS));

            GraveData graveData = new GraveData();
            graveData.setCitizenName(citizenData.getName());
            if (citizenData.getJob() != null)
            {
                final Component jobName = Component.translatableEscape(citizenData.getJob().getJobRegistryEntry().getTranslationKey().toLowerCase());
                graveData.setCitizenJobName(jobName.getString());
            }
            graveData.setCitizenDataNBT(citizenData.serializeNBT(world.registryAccess()));
            graveEntity.setGraveData(graveData);

            addNewGrave(firstValidPosition);
            return firstValidPosition;
        }
        else
        {
            // No room for a grave anywhere near. This is the only exit that does not reach the armour loop
            // above, so it has to drop the worn pieces itself or they are destroyed.
            InventoryUtils.dropCitizenInventory(citizenData.getInventory(), world, pos.getX(), pos.getY(), pos.getZ());
        }
        return null;
    }
}
