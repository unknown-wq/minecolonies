package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.util.BuildingUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_COURIERS;

/**
 * The Courier module for the warehouse.
 */
public class CourierAssignmentModule extends AbstractAssignedCitizenModule implements IAssignsJob, IBuildingEventsModule, ITickingModule, IPersistentModule
{
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        // PORT-NOTE(26.2): this loop used to sit behind BuildingUtils#canAutoHire, and that is the wrong gate for it.
        // Adoption is not hiring. Nothing here is taken out of the labour pool: the citizen is already a courier,
        // already employed at a Courier's Hut, and the only thing the loop does is tell a warehouse to serve him --
        // which is the second of two assignment lists a courier needs before EntityAIWorkDeliveryman#checkIfExecute
        // will let him run his AI at all. Missing from this one, he is not idle, he is inert: he stands where he was
        // spawned forever. With one courier per hut that was a misconfiguration a player noticed, because the one
        // complaint and the one statue lined up. Since 0.0.33 a level-5 hut holds five, so the colony-wide auto-hiring
        // switch being off produced five hires and four statues, and there is no configuration in which that is what
        // anybody wanted. MANUAL and LOCKED are still honoured, because those are a player saying "keep couriers off
        // this warehouse" about this warehouse specifically; DEFAULT and AUTO now adopt whether or not the town hall's
        // colony-wide switch is on. canAssignCitizens is kept: an unbuilt warehouse has nothing to serve with.
        if (!isFull()
              && building.canAssignCitizens()
              && getHiringMode() != HiringMode.MANUAL
              && getHiringMode() != HiringMode.LOCKED
              && BuildingUtils.getAllowedJobs(colony.getWorld(), building.getPosition()).test(getJobEntry()))
        {
            // Read-only scan; assignCitizen only touches this module's own list, it never adds or removes a
            // citizen from the colony, so the unmodifiable view is safe and saves a full copy of the citizen
            // map on every colony tick of every warehouse.
            for (final ICitizenData data : colony.getCitizenManager().getCitizensUnmodifiable())
            {
                if (data.getJob() instanceof JobDeliveryman && !hasAssignedCitizen(data) && ((JobDeliveryman) data.getJob()).findWareHouse() == null)
                {
                    assignCitizen(data);
                }
            }
        }

        // 26.2/Fabric: getAssignedCitizen() already hands back an independent ArrayList copy, so wrapping it
        // in another one copied a copy. Harmless at the old cap of two couriers, and still only ~25s apart
        // at the new cap of twenty, but it is the one loop here that runs at the module's full width every
        // colony tick, so drop the redundant allocation rather than quadruple it.
        for (final ICitizenData citizenData : getAssignedCitizen())
        {
            if (!(citizenData.getJob() instanceof JobDeliveryman))
            {
                removeCitizen(citizenData);
            }
        }
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        if (compound.contains(getModuleSerializationIdentifier()))
        {
            compound = compound.getCompoundOrEmpty(getModuleSerializationIdentifier());
        }

        final int[] residentIds = compound.getIntArray(TAG_COURIERS).orElse(new int[0]);
        for (final int citizenId : residentIds)
        {
            final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(citizenId);
            if (citizen != null)
            {
                assignCitizen(citizen);
            }
        }
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        super.serializeNBT(provider, compound);
        if (!assignedCitizen.isEmpty())
        {
            final int[] residentIds = new int[assignedCitizen.size()];
            for (int i = 0; i < assignedCitizen.size(); ++i)
            {
                residentIds[i] = assignedCitizen.get(i).getId();
            }
            compound.putIntArray(TAG_COURIERS, residentIds);
        }
    }

    @Override
    public void onRemoval(final ICitizenData citizen)
    {

    }

    @Override
    public void onAssignment(final ICitizenData citizen)
    {

    }

    // PORT-NOTE(26.2): upstream is buildingLevel * 2, ten at level five. Widened to twenty at the cap.
    // Kept as a linear multiple of the level rather than a curve that merely lands on twenty at level
    // five, because the property worth preserving is the *ratio* to the hut: COURIER_WORK is one courier
    // per hut level, so a constant multiple here means the warehouse sits a fixed four huts ahead of the
    // hut curve at every tier, and the feature never half-works at some middle level the way level*level
    // (1, 4, 9, 16, 25) would at level one. Four level-N huts fill one level-N warehouse, at every N.
    @Override
    public int getModuleMax()
    {
        return this.building.getBuildingLevel() * 4;
    }

    @Override
    public JobEntry getJobEntry()
    {
        return ModJobs.delivery.get();
    }

    @Override
    protected String getModuleSerializationIdentifier()
    {
        return "warehouse";
    }
}
