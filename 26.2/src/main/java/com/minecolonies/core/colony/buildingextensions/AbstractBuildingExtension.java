package com.minecolonies.core.colony.buildingextensions;

import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.modules.IBuildingExtensionModule;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries.BuildingExtensionEntry;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_OWNER;

// PORT-NOTE: the hand-assigned flag below is not upstream. It exists so that a field a player pinned with the
// field stick survives the next colony tick untouched - see IBuildingExtension#isHandAssigned.

/**
 * Abstract implementation for building extension instances.
 * Contains some basic mandatory logic for building extensions.
 */
public abstract class AbstractBuildingExtension implements IBuildingExtension
{
    /**
     * NBT tag holding {@link #handAssigned}.
     */
    private static final String TAG_HAND_ASSIGNED = "handassigned";

    /**
     * Set of building extension modules this building extension has.
     */
    private final List<IBuildingExtensionModule> modules = new ArrayList<>();

    /**
     * The type of the building extension.
     */
    private final BuildingExtensionEntry buildingExtensionEntry;

    /**
     * The position of the building extension.
     */
    private final BlockPos position;

    /**
     * Building id of the building owning the building extension.
     */
    @Nullable
    private BlockPos buildingId = null;

    /**
     * Unique extension id.
     */
    private final ExtensionId extensionId;

    /**
     * Whether a player pinned this extension by hand. See {@link IBuildingExtension#isHandAssigned()}.
     */
    private boolean handAssigned = false;

    /**
     * Constructor used in NBT deserialization.
     *
     * @param buildingExtensionEntry the type of building extension.
     * @param position  the position of the building extension.
     */
    protected AbstractBuildingExtension(final @NotNull BuildingExtensionRegistries.BuildingExtensionEntry buildingExtensionEntry, final @NotNull BlockPos position)
    {
        this.buildingExtensionEntry = buildingExtensionEntry;
        this.position = position;
        this.extensionId = new ExtensionId(position, buildingExtensionEntry);
    }

    @Override
    @NotNull
    public List<IBuildingExtensionModule> getModules()
    {
        return modules;
    }

    @Override
    @NotNull
    public Class<IBuildingExtensionModule> getClassType()
    {
        return IBuildingExtensionModule.class;
    }

    @Override
    public void registerModule(final @NotNull IBuildingExtensionModule module)
    {
        this.modules.add(module);
    }

    @Override
    @NotNull
    public final BuildingExtensionRegistries.BuildingExtensionEntry getBuildingExtensionType()
    {
        return buildingExtensionEntry;
    }

    @Override
    @NotNull
    public final BlockPos getPosition()
    {
        return position;
    }

    @Override
    @Nullable
    public final BlockPos getBuildingId()
    {
        return buildingId;
    }

    @Override
    public final void setBuilding(final BlockPos buildingId)
    {
        this.buildingId = buildingId;
    }

    @Override
    public final void resetOwningBuilding()
    {
        buildingId = null;
    }

    @Override
    public final boolean isTaken()
    {
        return buildingId != null;
    }

    @Override
    public final int getSqDistance(final IBuildingView building)
    {
        return (int) Math.sqrt(BlockPosUtil.getDistanceSquared(position, building.getPosition()));
    }

    @Override
    public final boolean isHandAssigned()
    {
        return handAssigned;
    }

    @Override
    public final void setHandAssigned(final boolean handAssigned)
    {
        this.handAssigned = handAssigned;
    }

    @Override
    public @NotNull CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        CompoundTag compound = new CompoundTag();
        if (buildingId != null)
        {
            BlockPosUtil.write(compound, TAG_OWNER, buildingId);
        }
        if (handAssigned)
        {
            // Only written when set, so a save made before this existed reads back as "automatic", which is what
            // every existing field is.
            compound.putBoolean(TAG_HAND_ASSIGNED, true);
        }
        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final @NotNull CompoundTag compound)
    {
        if (compound.contains(TAG_OWNER))
        {
            buildingId = BlockPosUtil.read(compound, TAG_OWNER);
        }
        else
        {
            buildingId = null;
        }
        handAssigned = compound.getBooleanOr(TAG_HAND_ASSIGNED, false);
    }

    @Override
    public void serialize(final @NotNull RegistryFriendlyByteBuf buf)
    {
        buf.writeBoolean(buildingId != null);
        if (buildingId != null)
        {
            buf.writeBlockPos(buildingId);
        }
        buf.writeBoolean(handAssigned);
    }

    @Override
    public void deserialize(final @NotNull RegistryFriendlyByteBuf buf)
    {
        if (buf.readBoolean())
        {
            buildingId = buf.readBlockPos();
        }
        else
        {
            buildingId = null;
        }
        handAssigned = buf.readBoolean();
    }

    @Override
    public int hashCode()
    {
        int result = position.hashCode();
        result = 31 * result + buildingExtensionEntry.hashCode();
        return result;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final AbstractBuildingExtension that = (AbstractBuildingExtension) o;

        if (!position.equals(that.position))
        {
            return false;
        }
        return buildingExtensionEntry.equals(that.buildingExtensionEntry);
    }

    @Override
    public ExtensionId getId()
    {
        return extensionId;
    }
}
