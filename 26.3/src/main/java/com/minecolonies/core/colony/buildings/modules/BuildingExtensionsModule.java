package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.MineColonies;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.NbtTagConstants.*;

/**
 * Abstract class to list all building extensions (assigned) to a building.
 */
public abstract class BuildingExtensionsModule extends AbstractBuildingModule implements IPersistentModule, IBuildingModule, ITickingModule
{
    /**
     * NBT tag to store assign manually.
     */
    private static final String TAG_ASSIGN_MANUALLY = "assign";
    private static final String TAG_CURRENT_EXTENSION = "currex";

    /**
     * Total order on positions, used only to break claim distance ties so that two equally close extensions are
     * always taken in the same order rather than in whatever order the extension map iterates in.
     */
    private static final Comparator<BlockPos> BLOCK_POS_ORDER = BlockPosUtil.POSITION_ORDER;

    /**
     * A map of building extensions, along with their unix timestamp of when they can next be checked again.
     */
    private final Map<IBuildingExtension.ExtensionId, Integer> checkedExtensions = new Object2IntOpenHashMap<>();

    /**
     * The building extension the citizen is currently working on.
     */
    @Nullable
    private IBuildingExtension.ExtensionId currentExtensionId;

    /**
     * Building extensions should be assigned manually to the citizen.
     */
    private boolean shouldAssignManually = false;

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        releaseDistantExtensions();
        claimExtensions();
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        shouldAssignManually = compound.getBooleanOr(TAG_ASSIGN_MANUALLY, false);
        final ListTag listTag = compound.getListOrEmpty(TAG_BUILDING_EXTENSIONS);
        for (int i = 0; i < listTag.size(); ++i)
        {
            final CompoundTag tag = listTag.getCompoundOrEmpty(i);
            if (!tag.contains(TAG_ID))
            {
                // Nothing to point at, so nothing to remember. An entry in this shape can only come from a save
                // written before the list below was stored under a key this method reads.
                continue;
            }
            checkedExtensions.put(IBuildingExtension.ExtensionId.deserializeNBT(provider, tag.getCompoundOrEmpty(TAG_ID)), tag.getIntOr(TAG_DAY, 0));
        }
        if (compound.contains(TAG_CURRENT_EXTENSION))
        {
            currentExtensionId = IBuildingExtension.ExtensionId.deserializeNBT(provider, compound.getCompoundOrEmpty(TAG_CURRENT_EXTENSION));
        }
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        compound.putBoolean(TAG_ASSIGN_MANUALLY, shouldAssignManually);

        final ListTag listTag = new ListTag();
        for (final Map.Entry<IBuildingExtension.ExtensionId, Integer> entry : checkedExtensions.entrySet())
        {
            final CompoundTag listEntry = new CompoundTag();
            listEntry.put(TAG_ID, entry.getKey().serializeNBT(provider));
            listEntry.putInt(TAG_DAY, entry.getValue());
            listTag.add(listEntry);
        }
        compound.put(TAG_BUILDING_EXTENSIONS, listTag);
        if (currentExtensionId != null)
        {
            compound.put(TAG_CURRENT_EXTENSION, currentExtensionId.serializeNBT(provider));
        }
    }

    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeBoolean(shouldAssignManually);
        buf.writeInt(getMaxExtensionCount());
    }

    /**
     * Getter to obtain the maximum building extension count.
     *
     * @return an integer stating the maximum building extension count.
     */
    public abstract int getMaxExtensionCount();

    /**
     * Get the class type which is expected for the building extensions to have.
     *
     * @return the class type.
     */
    public abstract Class<?> getExpectedExtensionType();

    /**
     * Getter of the current building extension.
     * <p>
     * The ownership test is not upstream and is the fix for a farmer that keeps walking to a field belonging to
     * somebody else. {@code currentExtensionId} is a pointer cached when the worker picked the extension up, and
     * upstream resolved it straight out of the colony's extension list with no further question:
     * {@link #getBuildingExtensionToWorkOn} returns it before it ever reaches the loop that filters on ownership,
     * so once the pointer outlived the ownership the worker walked to that extension forever. Only this building's
     * own {@link #freeExtension} ever cleared it, which covers releasing but not reassignment - and after a world
     * reload it did not even cover that, see the note there.
     * <p>
     * Checking here rather than at every place ownership can change closes the whole class of it: whatever route
     * the extension left by, the pointer is dropped the next time the worker asks for it.
     *
     * @return a building extension object.
     */
    @Nullable
    public IBuildingExtension getCurrentExtension()
    {
        if (currentExtensionId == null)
        {
            return null;
        }

        final IBuildingExtension extension = building.getColony().getServerBuildingManager().getMatchingBuildingExtension(currentExtensionId);
        if (extension == null || !building.getID().equals(extension.getBuildingId()))
        {
            // Straight to null rather than through resetCurrentExtension: that one records "the worker looked at
            // this extension today" so it is not picked again immediately, which is the wrong thing to remember
            // about an extension this building does not own any more.
            currentExtensionId = null;
            return null;
        }
        return extension;
    }

    /**
     * Retrieves the building extension to work on for the citizen, as long as the current building extension has work, it will keep returning that building extension.
     * Else it will retrieve a random building extension to work on for the citizen.
     * This method will also automatically claim any building extensions that are not in use if the building is on automatic assignment mode.
     *
     * @return a building extension to work on.
     */
    @Nullable
    public IBuildingExtension getBuildingExtensionToWorkOn()
    {
        final IBuildingExtension currentExtension = getCurrentExtension();
        if (currentExtension != null)
        {
            return currentExtension;
        }

        IBuildingExtension.ExtensionId lastUsedExtension = null;
        int lastUsedExtensionDay = building.getColony().getDay();

        for (final IBuildingExtension extension : getOwnedExtensions())
        {
            if (!checkedExtensions.containsKey(extension.getId()))
            {
                currentExtensionId = extension.getId();
                return extension;
            }

            final int lastDay = checkedExtensions.get(extension.getId());
            if (lastDay < lastUsedExtensionDay)
            {
                lastUsedExtension = extension.getId();
                lastUsedExtensionDay = lastDay;
            }
        }
        currentExtensionId = lastUsedExtension;
        return getCurrentExtension();
    }

    /**
     * Returns list of owned building extensions.
     *
     * @return a list of building extension objects.
     */
    @NotNull
    public final List<IBuildingExtension> getOwnedExtensions()
    {
        return getMatchingExtension(f -> building.getID().equals(f.getBuildingId()));
    }

    /**
     * Returns list of building extensions.
     *
     * @return a list of building extension objects.
     */
    @NotNull
    public abstract List<IBuildingExtension> getMatchingExtension(final Predicate<IBuildingExtension>  predicateToMatch);

    /**
     * Attempt to automatically claim free building extensions, if possible and if any building extensions are available.
     * <p>
     * Upstream took whichever free extension the colony's building manager happened to list first and stopped, with
     * no distance term anywhere in the path. Whichever hut ticked first therefore won, so a farmer a thousand blocks
     * away could hold the field a farmer standing next to it wanted, and there was no way back short of unassigning
     * it by hand. Two things changed, and both are needed - ordering alone still hands a hut the only free field in
     * the world:
     * <ul>
     *     <li><b>Nearest first.</b> Candidates are sorted by {@link IBuildingExtension#getFootprintDistanceSq}, the
     *     squared horizontal distance to the <em>nearest block the extension covers</em>. Not its anchor, and not
     *     its centre: free mode allows a 4x1000 strip, and for one of those the anchor is up to 500 blocks from
     *     either end, which would make a hut standing on the field look far away. Nearest block is also the only
     *     one of the three under which "the hut right next to the field" always wins, which is the complaint.</li>
     *     <li><b>A range cap.</b> {@code maxfieldclaimdistance} blocks, 0 to switch it off. Beyond it a free
     *     extension is not a candidate at all.</li>
     *     <li><b>The nearest hut wins the extension, not the first one to tick.</b> Ordering a single hut's own
     *     candidates only decides which of several fields it takes; it does not stop a hut a hundred blocks away
     *     from taking the field another hut is standing on, which is the complaint in its own words - "не дает
     *     фермеру который рядом совсем". So before claiming, a hut checks that no nearer building in the colony
     *     could take the same extension; see {@link #isNearestClaimant}.</li>
     * </ul>
     * Equal distances are broken on position so the order is total and does not depend on the map's iteration
     * order, and the same tie-break is used on both sides so exactly one of two equidistant huts believes it has
     * won. Nothing here can oscillate: an extension that gets claimed becomes taken, and taken extensions are never
     * offered again until something explicitly releases them.
     */
    public void claimExtensions()
    {
        if (shouldAssignManually)
        {
            return;
        }

        final long maxDistanceSq = getMaxClaimDistanceSq();
        final BlockPos from = building.getPosition();

        final List<IBuildingExtension> candidates = new ArrayList<>();
        for (final IBuildingExtension extension : getFreeExtensions())
        {
            if (extension.getFootprintDistanceSq(from) <= maxDistanceSq)
            {
                candidates.add(extension);
            }
        }

        candidates.sort(Comparator.comparingLong((IBuildingExtension extension) -> extension.getFootprintDistanceSq(from))
                          .thenComparing(IBuildingExtension::getPosition, BLOCK_POS_ORDER));

        for (final IBuildingExtension extension : candidates)
        {
            if (!canAssignExtension(extension) || !isNearestClaimant(extension))
            {
                continue;
            }
            if (assignExtension(extension))
            {
                break;
            }
        }
    }

    /**
     * Whether this building is the one that should get an extension, out of every building in the colony that could
     * take it.
     * <p>
     * A rival counts only if it really would claim the extension on its own next tick: it has a module of the same
     * kind, expects this kind of extension, is not on manual assignment, has room and passes its own assignment
     * rules, and its chunk is loaded - the last because {@code RegisteredStructureManager#onColonyTick} only ticks
     * loaded buildings, and a rival that never ticks must not be allowed to reserve an extension nobody then works.
     * A rival that is full, or in manual mode, or that refuses the extension, therefore steps aside rather than
     * blocking, and the extension still gets claimed by the nearest hut that can actually use it.
     * <p>
     * Ties are broken on building position with the same total order the candidate list uses, so of two equidistant
     * huts exactly one considers itself the winner and they cannot hand the extension back and forth every tick.
     *
     * @param extension the extension being considered.
     * @return true if nothing nearer wants it.
     */
    private boolean isNearestClaimant(final IBuildingExtension extension)
    {
        final BlockPos from = building.getPosition();
        final long ours = extension.getFootprintDistanceSq(from);

        for (final IBuilding rival : building.getColony().getServerBuildingManager().getBuildings().values())
        {
            if (rival.getID().equals(building.getID()))
            {
                continue;
            }

            final long theirs = extension.getFootprintDistanceSq(rival.getPosition());
            if (theirs > ours || (theirs == ours && BLOCK_POS_ORDER.compare(rival.getPosition(), from) > 0))
            {
                continue;
            }

            final BuildingExtensionsModule rivalModule = rival.getFirstModuleOccurance(BuildingExtensionsModule.class);
            if (rivalModule == null
                  || rivalModule.shouldAssignManually
                  || !rivalModule.getExpectedExtensionType().isInstance(extension)
                  || !WorldUtil.isBlockLoaded(building.getColony().getWorld(), rival.getPosition())
                  || !rivalModule.canAssignExtension(extension))
            {
                continue;
            }

            return false;
        }
        return true;
    }

    /**
     * Let go of any owned extension that is further away than the claim range allows.
     * <p>
     * Sorting future claims does not fix a colony that already has the wrong hut holding a field - the field is
     * taken, so it is never offered to anybody else. This pass is what makes an existing save converge on the new
     * rule instead of needing the player to unassign by hand. It deliberately does not touch anything a player
     * decided on:
     * <ul>
     *     <li>a building in manual assignment mode is skipped entirely, so the hut GUI's switch is also the way to
     *     keep a deliberately distant field;</li>
     *     <li>an extension pinned with the field stick is skipped, see
     *     {@link IBuildingExtension#isHandAssigned()};</li>
     *     <li>with {@code maxfieldclaimdistance} at 0 nothing is ever released.</li>
     * </ul>
     * A released extension is picked up on a later tick by whichever hut is in range and nearest, or by nobody.
     */
    private void releaseDistantExtensions()
    {
        if (shouldAssignManually)
        {
            return;
        }

        final long maxDistanceSq = getMaxClaimDistanceSq();
        if (maxDistanceSq == Long.MAX_VALUE)
        {
            return;
        }

        final BlockPos from = building.getPosition();
        for (final IBuildingExtension extension : getOwnedExtensions())
        {
            if (!extension.isHandAssigned() && extension.getFootprintDistanceSq(from) > maxDistanceSq)
            {
                freeExtension(extension);
            }
        }
    }

    /**
     * The claim range, squared, as configured.
     *
     * @return the squared range, or {@link Long#MAX_VALUE} when the cap is switched off.
     */
    private static long getMaxClaimDistanceSq()
    {
        final int maxDistance = MineColonies.getConfig().getServer().maxFieldClaimDistance.get();
        return maxDistance <= 0 ? Long.MAX_VALUE : (long) maxDistance * maxDistance;
    }

    /**
     * Returns list of free building extensions.
     * <p>
     * Extensions a player pinned by hand are not free even when nothing owns them: releasing one with the field
     * stick and having the nearest hut claim it back on the next colony tick is exactly the behaviour the tool
     * exists to escape.
     *
     * @return a list of building extension objects.
     */
    public final List<IBuildingExtension> getFreeExtensions()
    {
        return getMatchingExtension(extension -> !extension.isTaken() && !extension.isHandAssigned());
    }

    /**
     * Method called to assign a building extension to the building.
     *
     * @param extension the building extension to add.
     */
    public boolean assignExtension(final IBuildingExtension extension)
    {
        if (canAssignExtension(extension))
        {
            extension.setBuilding(building.getID());
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Check to see if a new building extension can be assigned to the worker.
     *
     * @param extension the building extension which is being added.
     * @return true if so.
     */
    public final boolean canAssignExtension(final IBuildingExtension extension)
    {
        return getOwnedExtensions().size() < getMaxExtensionCount() && canAssignExtensionOverride(extension);
    }

    @Override
    public void markDirty()
    {
        super.markDirty();
        building.getColony().getServerBuildingManager().markBuildingExtensionsDirty();
    }

    /**
     * Additional checks to see if this building extension can be assigned to the building.
     *
     * @param extension the building extension which is being added.
     * @return true if so.
     */
    protected abstract boolean canAssignExtensionOverride(final IBuildingExtension extension);

    /**
     * Getter for the assign manually.
     *
     * @return true if he should.
     */
    public final boolean assignManually()
    {
        return shouldAssignManually;
    }

    /**
     * Checks if the building has any building extensions.
     *
     * @return true if he has none.
     */
    public final boolean hasNoExtensions()
    {
        return getOwnedExtensions().isEmpty();
    }

    /**
     * Switches the assign manually of the building.
     *
     * @param assignManually true if assignment should be manual.
     */
    public final void setAssignManually(final boolean assignManually)
    {
        this.shouldAssignManually = assignManually;
    }

    /**
     * Method called to free a building extension.
     *
     * @param extension the building extension to be freed.
     */
    public void freeExtension(final IBuildingExtension extension)
    {
        extension.resetOwningBuilding();
        markDirty();

        // Upstream compared the two ids with ==. ExtensionId is a record and AbstractBuildingExtension hands out one
        // cached instance per extension, so reference equality happened to hold for a pointer set in this session -
        // but currentExtensionId read back out of NBT is a fresh record, so after a world reload the comparison was
        // false forever and releasing a field left the worker still pointing at it.
        if (extension.getId().equals(currentExtensionId))
        {
            resetCurrentExtension();
        }
    }

    /**
     * Resets the current building extension if the worker indicates this building extension should no longer be worked on.
     */
    public void resetCurrentExtension()
    {
        if (currentExtensionId != null)
        {
            checkedExtensions.put(currentExtensionId, building.getColony().getDay());
        }
        currentExtensionId = null;
    }
}
