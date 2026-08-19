package com.minecolonies.api.colony.buildings.workerbuildings;

import net.minecraft.network.codec.ByteBufCodecs;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.colonyEvents.descriptions.IColonyEventDescription;
import com.minecolonies.api.colony.permissions.PermissionEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;

public interface ITownHallView extends IBuildingView
{
    /**
     * Get a list of permission events.
     *
     * @return a copy of the list of events.
     */
    List<PermissionEvent> getPermissionEvents();

    /**
     * Gets a list if colony events.
     * 
     * @return a copy of the list of events.
     */
    List<IColonyEventDescription> getColonyEvents();

    /**
     * Check if the player can use the teleport command.
     *
     * @return true if so.
     */
    boolean canPlayerUseTP();

    /**
     * Getter for the mapdata.
     * @return the original list.
     */
    List<MapEntry> getMapDataList();

    public record MapEntry(MapId mapId, MapItemSavedData mapData)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, MapEntry> STREAM_CODEC =
            StreamCodec.composite(MapId.STREAM_CODEC,
                MapEntry::mapId,
                // 26.2: MapItemSavedData#save/#load are gone; it is codec-based now (MapItemSavedData.CODEC).
                ByteBufCodecs.fromCodecWithRegistries(MapItemSavedData.CODEC),
                MapEntry::mapData,
                MapEntry::new);
    }
}
