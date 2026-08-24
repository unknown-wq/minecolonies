package com.minecolonies.core.colony.buildings.workerbuildings;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.IBuildingDeliveryman;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.BuildingConstants.CONST_DEFAULT_MAX_BUILDING_LEVEL;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.COURIER_WORK;

/**
 * Class of the warehouse building.
 */
public class BuildingDeliveryman extends AbstractBuilding implements IBuildingDeliveryman
{

    private static final String DELIVERYMAN = "deliveryman";

    /**
     * Instantiates a new warehouse building.
     *
     * @param c the colony.
     * @param l the location
     */
    public BuildingDeliveryman(final IColony c, final BlockPos l)
    {
        super(c, l);
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return DELIVERYMAN;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return CONST_DEFAULT_MAX_BUILDING_LEVEL;
    }

    // PORT-NOTE(26.2): two deviations from upstream, both forced by COURIER_WORK now scaling with hut level.
    //
    // Upstream asks getFirstCitizen() only, which stops protecting anyone once a second courier moves in:
    // couriers 2..n would eat the very stack they are carrying to another building. Ask every courier.
    //
    // Upstream also asks through job.getCurrentTask(), which is not the read-only accessor its name suggests
    // -- on an empty private queue it *claims* a request off the shared WarehouseRequestQueueModule. Called
    // once per courier per food check that would hand out delivery work as a side effect of asking about
    // lunch. Read the already-claimed queue directly instead; that is the courier's actual cargo anyway.
    @Override
    public boolean canEat(final ItemStack stack)
    {
        for (final ICitizenData citizenData : getModule(COURIER_WORK).getAssignedCitizen())
        {
            if (!(citizenData.getJob() instanceof final JobDeliveryman job))
            {
                continue;
            }

            for (final IToken<?> token : job.getTaskQueue())
            {
                final IRequest<?> task = getColony().getRequestManager().getRequestForToken(token);
                if (task != null
                      && task.getRequest() instanceof final Delivery delivery
                      && ItemStack.isSameItem(delivery.getStack(), stack))
                {
                    return false;
                }
            }
        }
        return super.canEat(stack);
    }
}
