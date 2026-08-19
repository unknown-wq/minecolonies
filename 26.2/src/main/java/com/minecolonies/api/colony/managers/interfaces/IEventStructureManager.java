package com.minecolonies.api.colony.managers.interfaces;


// PORT-TODO(structurize): re-check list only. This file compiles against the real 26.2 Structurize
// library and is part of the build; structurize-blocked.txt is nothing but the grep list of files worth
// re-verifying against the real API. build.gradle:128-133 states outright that this build never reads it.

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for the Event structure manager The manager takes care of structures spawned for events, takes a backup before spawning and loads the backup when the event is done.
 */
public interface IEventStructureManager
{
    /**
     * Spawns a temporary structure to place in the world, saves a backup of the previous blocks and restores them after the event ends.
     *
     * @param structure        structure thats going to be spawned
     * @param targetSpawnPoint position to spawn at
     * @param eventID          eventID to spawn for
     * @return true if successfully spawned
     */
    boolean spawnTemporaryStructure(Blueprint structure, BlockPos targetSpawnPoint, int eventID);

    /**
     * Restores backup schematics for the given event ID, may be more than one.
     *
     * @param eventID the id of the event.
     */
    void loadBackupForEvent(int eventID);

    /**
     * Reads all saved schematics from nbt, needs to happen before the event managers nbt read.
     *
     * @param compound the compound to read from.
     */
    void readFromNBT(@NotNull CompoundTag compound);

    /**
     * Writes all backup schematics to NBT
     *
     * @param compound the compound to write to.
     */
    void writeToNBT(@NotNull CompoundTag compound);
}
