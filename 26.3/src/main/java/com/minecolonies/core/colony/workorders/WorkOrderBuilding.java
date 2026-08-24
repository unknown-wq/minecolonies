package com.minecolonies.core.colony.workorders;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.advancements.AdvancementTriggers;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IWorkManager;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.entity.ai.workers.util.ConstructionTapeHelper;
import com.minecolonies.core.util.AdvancementUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.core.MineColonies.getConfig;

/**
 * Represents one building order to complete. Has his own structure for the building.
 */
public class WorkOrderBuilding extends AbstractWorkOrder
{
    private static final String TAG_CUSTOM_NAME            = "customName";
    private static final String TAG_CUSTOM_PARENT_NAME     = "customParentName";
    private static final String TAG_PARENT_TRANSLATION_KEY = "parentTranslationKey";

    /**
     * Maximum distance a builder can have from the building site, squared, read from the server config
     * ({@code maxbuilderdistance}) rather than the 100 blocks this used to hardcode. Both users of it compare
     * against {@link BlockPos#distSqr}, which is three-dimensional: a hut level with the builder at 100 blocks
     * is in reach, the same hut sunk 60 blocks into a mountainside is not.
     *
     * @return the squared reach, in blocks.
     */
    private static double maxDistanceSq()
    {
        final double reach = getConfig().getServer().maxBuilderDistance.get();
        return reach * reach;
    }

    /**
     * The custom name of the building.
     */
    private String customName;

    /**
     * The custom name of the parent building.
     */
    private String customParentName;

    /**
     * The translation key of the parent building.
     */
    private String parentTranslationKey;

    public static WorkOrderBuilding create(@NotNull final WorkOrderType type, @NotNull final IBuilding building)
    {
        int targetLevel = building.getBuildingLevel();
        switch (type)
        {
            case BUILD:
                targetLevel = 1;
                break;
            case UPGRADE:
                targetLevel++;
                break;
            case REMOVE:
                targetLevel = 0;
                break;
        }

        return create(type, building, targetLevel);
    }

    /**
     * Create a work order that goes to a level of the player's choosing rather than to the next one.
     * <p>
     * Only free mode reaches this with a level more than one above the building's own - see
     * {@link com.minecolonies.core.colony.buildings.AbstractBuilding#requestUpgradeTo}. The work order itself does not
     * care how big the jump is: the builder reads the target level's blueprint and
     * {@code AbstractEntityAIStructureWithWorkOrder} sets the building to {@link #getTargetLevel()} when it finishes,
     * so the intermediate levels are never built and never needed.
     *
     * @param type        what to do with the building.
     * @param building    the building.
     * @param targetLevel the level to end up at, ignored for {@link WorkOrderType#REMOVE}.
     * @return the work order.
     */
    public static WorkOrderBuilding create(@NotNull final WorkOrderType type, @NotNull final IBuilding building, final int targetLevel)
    {
        final int targetSchematicLevel = type == WorkOrderType.REMOVE ? building.getBuildingLevel() : targetLevel;
        String schemPath = building.getBlueprintPath().replace(".blueprint", "");
        schemPath = schemPath.substring(0, schemPath.length() - 1) + targetSchematicLevel + ".blueprint";
        WorkOrderBuilding wo = new WorkOrderBuilding(
          building.getStructurePack(),
          schemPath,
          building.getBuildingType().getTranslationKey(),
          type,
          building.getID(),
          building.getTileEntity() == null ? building.getRotationMirror() : building.getTileEntity().getRotationMirror(),
          building.getBuildingLevel(),
          type == WorkOrderType.REMOVE ? 0 : targetLevel);
        wo.setCustomName(building);
        return wo;
    }

    /**
     * Unused constructor for reflection.
     */
    public WorkOrderBuilding()
    {
        super();
    }

    private WorkOrderBuilding(
      String packName,
      String path,
      String translationKey,
      WorkOrderType workOrderType,
      BlockPos location,
      RotationMirror rotMir,
      int currentLevel,
      int targetLevel)
    {
        super(packName, path, translationKey, workOrderType, location, rotMir, currentLevel, targetLevel);
    }

    public String getCustomName()
    {
        return customName;
    }

    public String getCustomParentName()
    {
        return customParentName;
    }

    public String getParentTranslationKey()
    {
        return parentTranslationKey;
    }

    public void setCustomName(@NotNull final IBuilding building)
    {
        this.customName = building.getCustomName();
        this.customParentName = "";
        this.parentTranslationKey = "";

        if (building.hasParent())
        {
            final IBuilding parentBuilding = building.getColony().getServerBuildingManager().getBuilding(building.getParent());
            if (parentBuilding != null)
            {
                this.customParentName = parentBuilding.getCustomName();
                this.parentTranslationKey = parentBuilding.getBuildingType().getTranslationKey();
            }
        }
    }

    @Override
    public Component getDisplayName()
    {
        String customParentName = getCustomParentName();
        String customName = getCustomName();
        Component buildingComponent = customName.isEmpty() ? Component.translatableEscape(getTranslationKey()) : Component.literal(customName);

        if (parentTranslationKey.isEmpty())
        {
            return buildingComponent;
        }
        else
        {
            Component parentComponent = customParentName.isEmpty() ? Component.translatableEscape(parentTranslationKey) : Component.literal(customParentName);
            return Component.translatableEscape("%s / %s", parentComponent, buildingComponent);
        }
    }

    @Override
    public boolean canBuild(final IBuilding building)
    {
        //  A Build WorkOrder may be fulfilled by a Builder as long as any ONE of the following is true:
        //  - The Builder's Work AbstractBuilding is built
        //  - OR the WorkOrder is for the Builder's Work AbstractBuilding
        //  - OR the WorkOrder is for the TownHall
        //  - OR the WorkOrder is within maxbuilderdistance of any builder and not manually assigned

        return building instanceof BuildingBuilder
            && canBuildIgnoringDistance(building, building.getPosition(), building.getBuildingLevel())
            && (building.getPosition().distSqr(getLocation()) <= maxDistanceSq());
    }

    /**
     * Checks if a builder may accept this workOrder while ignoring the distance to the builder.
     *
     * @param builderLocation position of the builders own hut.
     * @param builderLevel    level of the builders hut.
     * @return true if so.
     */
    @Override
    public boolean canBuildIgnoringDistance(@NotNull IBuilding building, @NotNull final BlockPos builderLocation, final int builderLevel)
    {
        //  A Build WorkOrder may be fulfilled by a Builder as long as any ONE of the following is true:
        //  - The Builder's Work AbstractBuilding is built
        //  - OR the WorkOrder is for the Builder's Work AbstractBuilding
        //  - OR free mode is on: a level 1 builder may then build a level 5 hut, which is the whole point of being
        //    allowed to order one. Without this the order would be created and then never claimed by anybody, because
        //    this is what WorkManager#onColonyTick assigns by.

        return (builderLevel >= this.getTargetLevel() || builderLevel == BuildingBuilder.MAX_BUILDING_LEVEL || (builderLocation.equals(getLocation()))
                  || FreeMode.isOn(building.getColony()));
    }

    @Override
    public boolean tooFarFromAnyBuilder(final IColony colony, final int level)
    {
        return colony.getServerBuildingManager()
          .getBuildings()
          .values()
          .stream()
          .noneMatch(building -> building instanceof BuildingBuilder && !building.getAllAssignedCitizen().isEmpty()
                                   && building.getPosition().distSqr(getLocation()) <= maxDistanceSq());
    }

    /**
     * Is this WorkOrder still valid?  If not, it will be deleted.
     *
     * @param colony The colony that owns the Work Order.
     * @return True if the building for this work order still exists.
     */
    @Override
    public boolean isValid(@NotNull final IColony colony)
    {
        return super.isValid(colony) && colony.getServerBuildingManager().getBuilding(getLocation()) != null;
    }

    /**
     * Read the WorkOrder data from the CompoundTag.
     *
     * @param compound NBT Tag compound.
     * @param manager  the work manager.
     */
    @Override
    public void read(@NotNull final CompoundTag compound, final IWorkManager manager)
    {
        super.read(compound, manager);
        customName = compound.getStringOr(TAG_CUSTOM_NAME, "");
        customParentName = compound.getStringOr(TAG_CUSTOM_PARENT_NAME, "");
        parentTranslationKey = compound.getStringOr(TAG_PARENT_TRANSLATION_KEY, "");
    }

    /**
     * Save the Work Order to an CompoundTag.
     *
     * @param compound NBT tag compound.
     */
    @Override
    public void write(@NotNull final CompoundTag compound)
    {
        super.write(compound);
        compound.putString(TAG_CUSTOM_NAME, customName);
        compound.putString(TAG_CUSTOM_PARENT_NAME, customParentName);
        compound.putString(TAG_PARENT_TRANSLATION_KEY, parentTranslationKey);
    }

    @Override
    public void serializeViewNetworkData(@NotNull RegistryFriendlyByteBuf buf)
    {
        super.serializeViewNetworkData(buf);
        buf.writeUtf(customName);
        buf.writeUtf(customParentName);
        buf.writeUtf(parentTranslationKey);
    }

    @Override
    public void onCompleted(final IColony colony, ICitizenData citizen)
    {
        super.onCompleted(colony, citizen);

        if (getWorkOrderType() != WorkOrderType.REMOVE)
        {
            final IBuilding building = colony.getServerBuildingManager().getBuilding(getLocation());
            if (building != null)
            {
                AdvancementUtils.TriggerAdvancementPlayersForColony(colony,
                        player -> AdvancementTriggers.COMPLETE_BUILD_REQUEST.get().trigger(player, building.getSchematicName(), this.getTargetLevel()));
            }
        }
    }

    @Override
    public void onAdded(final IColony colony, final boolean readingFromNbt)
    {
        if (!readingFromNbt && colony != null && colony.getWorld() != null)
        {
            final IBuilding building = colony.getServerBuildingManager().getBuilding(getLocation());
            if (building != null)
            {
                ConstructionTapeHelper.placeConstructionTape(building.getCorners(), colony);
            }
        }
    }

    @Override
    public void onRemoved(final IColony colony)
    {
        final IBuilding building = colony.getServerBuildingManager().getBuilding(getLocation());
        if (building != null)
        {
            building.markDirty();
            ConstructionTapeHelper.removeConstructionTape(building.getCorners(), colony.getWorld());
        }
    }
}
