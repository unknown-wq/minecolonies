package com.minecolonies.api.colony.buildingextensions;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.modules.IBuildingExtensionModule;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.modules.IModuleContainer;
import com.minecolonies.api.util.BlockPosUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import com.minecolonies.api.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_ID;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_POS;

/**
 * Interface for building extension instances.
 */
public interface IBuildingExtension extends IModuleContainer<IBuildingExtensionModule>
{
    /**
     * Return the building extension type for this building extension.
     *
     * @return the building extension registry entry.
     */
    @NotNull BuildingExtensionRegistries.BuildingExtensionEntry getBuildingExtensionType();

    /**
     * Gets the position of the building extension.
     *
     * @return central location of the building extension.
     */
    @NotNull BlockPos getPosition();

    /**
     * Getter for the owning building of the building extension.
     *
     * @return the id or null.
     */
    @Nullable BlockPos getBuildingId();

    /**
     * Sets the owning building of the building extension.
     *
     * @param buildingId id of the building.
     */
    void setBuilding(final BlockPos buildingId);

    /**
     * Resets the ownership of the building extension.
     */
    void resetOwningBuilding();

    /**
     * Has the building extension been taken.
     *
     * @return true if the building extension is not free to use, false after releasing it.
     */
    boolean isTaken();

    /**
     * Get the distance to a building.
     *
     * @param building the building to get the distance to.
     * @return the distance
     */
    int getSqDistance(IBuildingView building);

    /**
     * Squared horizontal distance from a position to the nearest block this extension covers.
     * <p>
     * This is the number automatic claiming orders and caps by, so which points it measures between decides which
     * hut owns which field. The default is the distance to the extension's own anchor, which is all a point shaped
     * extension has; anything with a footprint - a farm field, for one - overrides it with the distance to the
     * nearest block of that footprint. See {@code BuildingExtensionsModule#claimExtensions} for why the nearest
     * block and not the centre.
     * <p>
     * Squared, horizontal and in {@code long}: squared to keep the comparison exact, horizontal because a field is
     * a plan and a hut two hundred blocks up a mountain is not further from it in any sense the farmer cares about,
     * and {@code long} because a coordinate difference of two billion squares straight out of an {@code int}.
     *
     * @param from the position to measure from, normally a building's own position.
     * @return the squared horizontal distance, never negative.
     */
    default long getFootprintDistanceSq(@NotNull final BlockPos from)
    {
        final long dx = (long) from.getX() - getPosition().getX();
        final long dz = (long) from.getZ() - getPosition().getZ();
        return dx * dx + dz * dz;
    }

    /**
     * Whether a player pinned this extension to a building by hand.
     * <p>
     * A pinned extension is left alone by everything automatic: it is never picked up by
     * {@code BuildingExtensionsModule#claimExtensions} while free, and never let go by the out of range release
     * pass while owned. It is the flag that stops the tool and the automatic claim from fighting over the same
     * field one tick after the player set it.
     *
     * @return true if the extension is under manual control.
     */
    boolean isHandAssigned();

    /**
     * Pin or unpin this extension. See {@link #isHandAssigned()}.
     *
     * @param handAssigned true to take the extension out of automatic assignment.
     */
    void setHandAssigned(boolean handAssigned);

    /**
     * Stores the NBT data of the building extension.
     */
    @NotNull CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider);

    /**
     * Reconstruct the building extension from the given NBT data.
     *
     * @param compound the compound to read from.
     */
    void deserializeNBT(@NotNull final HolderLookup.Provider provider, @NotNull CompoundTag compound);

    /**
     * Serialize a building extension to a buffer.
     *
     * @param buf the buffer to write the building extension data to.
     */
    void serialize(@NotNull RegistryFriendlyByteBuf buf);

    /**
     * Deserialize a building extension from a buffer.
     *
     * @param buf the buffer to read the building extension data from.
     */
    void deserialize(@NotNull RegistryFriendlyByteBuf buf);

    /**
     * Condition to check whether this building extension instance is currently properly placed down.
     *
     * @param colony the colony this building extension is in.
     * @return true if the building extension is correctly placed at the current position.
     */
    boolean isValidPlacement(IColony colony);

    /**
     * Hashcode implementation for this building extension.
     */
    int hashCode();

    /**
     * Equals implementation for this building extension.
     */
    boolean equals(Object other);

    /**
     * Get the unique extension id.
     * @return the unique id.
     */
    ExtensionId getId();

    /**
     * Register a specific module to the object.
     *
     * @param module the module to register.
     */
    void registerModule(@NotNull final IBuildingExtensionModule module);

    /**
     * Unique extension id.
     * @param pos the pos it's at.
     * @param entry it's entry type.
     */
    record ExtensionId(BlockPos pos, BuildingExtensionRegistries.BuildingExtensionEntry entry)
    {
        public Tag serializeNBT(final HolderLookup.Provider provider)
        {
            final CompoundTag tag = new CompoundTag();
            BlockPosUtil.write(tag, TAG_POS, pos);
            tag.putString(TAG_ID, entry.getRegistryName().toString());
            return tag;
        }

        public static ExtensionId deserializeNBT(final HolderLookup.Provider provider, final CompoundTag nbt)
        {
            return new ExtensionId(BlockPosUtil.read(nbt, TAG_POS), BuildingExtensionRegistries.getBuildingExtensionRegistry().getValue(Identifier.parse(nbt.getStringOr(TAG_ID, ""))));
        }
    }
}
