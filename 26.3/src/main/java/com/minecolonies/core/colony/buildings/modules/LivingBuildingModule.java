package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.*;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_LIVING_RESIDENTS;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_RESIDENTS;

/**
 * The living module for citizen to call their home.
 */
public class LivingBuildingModule extends AbstractAssignedCitizenModule implements IAssignsCitizen, IBuildingEventsModule, ITickingModule, IPersistentModule
{
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        if (compound.contains(TAG_RESIDENTS))
        {
            final int[] residentIds = compound.getIntArray(TAG_RESIDENTS).orElse(new int[0]);
            for (final int citizenId : residentIds)
            {
                final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(citizenId);
                if (citizen != null)
                {
                    assignCitizen(citizen);
                }
            }
        }
        else if (compound.contains(TAG_LIVING_RESIDENTS))
        {
            final int[] residentIds = compound.getIntArray(TAG_LIVING_RESIDENTS).orElse(new int[0]);
            for (final int citizenId : residentIds)
            {
                final ICitizenData citizen = building.getColony().getCitizenManager().getCivilian(citizenId);
                if (citizen != null)
                {
                    assignCitizen(citizen);
                }
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
            compound.putIntArray(TAG_LIVING_RESIDENTS, residentIds);
        }
    }

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (!isFull() && ((this.getHiringMode() == HiringMode.DEFAULT && building.getColony().getSettings().getSetting(BuildingTownHall.AUTO_HOUSING_MODE).getValue()) || getHiringMode() == HiringMode.AUTO))
        {
            // 'Capture' as many citizens into this house as possible
            // Read-only scan; assignCitizen -> setHomeBuilding + calculateMaxCitizens, neither of which adds or
            // removes a citizen from the colony. See CourierAssignmentModule#onColonyTick.
            for (@NotNull final ICitizenData citizen : building.getColony().getCitizenManager().getCitizensUnmodifiable())
            {
                if (isFull())
                {
                    break;
                }

                // Children are not given a bed of their own here -- the whole point of the rule is that a child does
                // not hold one. They are picked up by this pass the moment they grow up, which is when
                // ICitizenData#setIsChild(false) releases them; takeInOrphans below is what deals with them meanwhile.
                if (!citizen.isChild() && citizen.getHomeBuilding() == null && isNearestHouseFor(citizen))
                {
                    assignCitizen(citizen);
                }
            }
        }

        takeInOrphans();
    }

    /**
     * Take in any homeless child of a resident of this house.
     * <p>
     * A child is given its home once, at birth, by {@code ReproductionManager}, and nothing in the ordinary housing
     * pass ever offers it another -- so a child that loses the one it has stays homeless for the rest of its
     * childhood, with the homelessness happiness penalty running against it and the seven day complaint on its way. It
     * can lose it: its family's house being torn down or downgraded clears it through
     * {@code ICitizenData#onRemoveBuilding}, and that was seen happening on a live colony.
     * <p>
     * Deliberately narrow. This does not house every homeless child anywhere -- only one whose own parent lives here,
     * which is the same rule the birth used and the same rule {@code /mc colony rehouse} migrates old saves onto. A
     * child whose parents are all dead or homeless stays homeless, correctly, and {@code /mc colony info} says so.
     * <p>
     * Costs nothing in a colony with no children, which is what the {@code hasChilds} guard is for; the scan itself
     * only runs when this house has residents to be somebody's parent.
     */
    private void takeInOrphans()
    {
        if (assignedCitizen.isEmpty() || !building.getColony().hasChilds())
        {
            return;
        }

        for (@NotNull final ICitizenData citizen : building.getColony().getCitizenManager().getCitizensUnmodifiable())
        {
            if (!citizen.isChild() || citizen.getHomeBuilding() != null)
            {
                continue;
            }

            for (final int parentId : new int[] {citizen.getParentIds().getA(), citizen.getParentIds().getB()})
            {
                if (parentId != ICitizenData.NO_PARENT && hasAssignedCitizen(building.getColony().getCitizenManager().getCivilian(parentId)))
                {
                    assignCitizen(citizen);
                    break;
                }
            }
        }
    }

    /**
     * How many of this house's beds are actually taken.
     * <p>
     * 26.2/Fabric: not the same as {@link #getAssignedCitizen()}{@code .size()} any more. A child lives with its
     * parents and consumes no housing slot -- the owner's rule, and the reason the tavern stopped being the colony
     * nursery -- so it is on the resident list, and shown in the house, without counting against the level.
     * <p>
     * Everything that asks "is there room here" has to go through this rather than through the list size, or the two
     * disagree and the colony's bed arithmetic stops matching what a player can see: {@link #isFull()},
     * {@code RegisteredStructureManager#hasSpareBedFor} and the {@link HiringMode#LOCKED} branch of
     * {@code CitizenManager#calculateMaxCitizens} are the three.
     *
     * @return the number of adult residents.
     */
    public int getResidentCount()
    {
        int adults = 0;
        for (final ICitizenData citizen : assignedCitizen)
        {
            if (!citizen.isChild())
            {
                adults++;
            }
        }
        return adults;
    }

    @Override
    public boolean isFull()
    {
        return getResidentCount() >= getModuleMax();
    }

    @Override
    public boolean assignCitizen(final ICitizenData citizen)
    {
        // A child moves in with its parents whether or not their house has a spare bed, because it is not asking for
        // one. Without this the rule fails in exactly the case it exists for: the family home is normally full -- the
        // parents are what filled it -- so the base class's isFull() check refused every child its own home and the
        // colony's children came out homeless. Observed on a live colony before this override existed.
        //
        // It also matters on load: deserializeNBT re-assigns the saved residents through this method, and a child
        // saved into a full house would silently lose its home on the next world load.
        if (citizen != null && citizen.isChild() && !hasAssignedCitizen(citizen))
        {
            assignedCitizen.add(citizen);
            onAssignment(citizen);
            markDirty();
            return true;
        }

        return super.assignCitizen(citizen);
    }

    /**
     * Whether this house is the one the colony would pick for that citizen.
     * <p>
     * Upstream, a homeless citizen was taken by whichever house happened to tick first, which is map iteration order
     * and has nothing to do with where the citizen works. In a colony with an enclave that reliably hands an enclave
     * worker a bed in the main town, a kilometre away. Asking the colony rather than deciding here means the automatic
     * path and {@code /mc colony rehouse} use one and the same rule; see
     * {@link com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager#getHouseWithSpareBed(ICitizenData)}.
     * <p>
     * Only reached for a citizen that is actually homeless, so a colony where everybody has a bed pays nothing for it.
     *
     * @param citizen the homeless citizen.
     * @return true if this building is the nearest house with a spare bed for that citizen.
     */
    private boolean isNearestHouseFor(final ICitizenData citizen)
    {
        final IBuilding nearest = building.getColony().getServerBuildingManager().getHouseWithSpareBed(citizen);
        return nearest == null || nearest.getPosition().equals(building.getPosition());
    }

    @Override
    void onAssignment(final ICitizenData citizen)
    {
        citizen.setHomeBuilding(building);
        building.getColony().getCitizenManager().calculateMaxCitizens();
    }

    @Override
    void onRemoval(final ICitizenData citizen)
    {
        citizen.setHomeBuilding(null);
        building.getColony().getCitizenManager().calculateMaxCitizens();
    }

    @Override
    public int getModuleMax()
    {
        return building.getBuildingLevel();
    }

    @Override
    public void onUpgradeComplete(final int newLevel)
    {
        for (final Optional<AbstractEntityCitizen> entityCitizen : Objects.requireNonNull(getAssignedEntities()))
        {
            if (entityCitizen.isPresent() && entityCitizen.get().getCitizenJobHandler().getColonyJob() == null)
            {
                entityCitizen.get().getCitizenJobHandler().setModelDependingOnJob(null);
            }
        }
        building.getColony().getCitizenManager().calculateMaxCitizens();
    }

    @Override
    protected String getModuleSerializationIdentifier()
    {
        return "living";
    }
}
