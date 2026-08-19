package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.colony.colonyEvents.registry.ColonyEventDescriptionTypeRegistryEntry;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.eventhooks.buildingEvents.BuildingBuiltEvent;
import com.minecolonies.core.colony.eventhooks.buildingEvents.BuildingDeconstructedEvent;
import com.minecolonies.core.colony.eventhooks.buildingEvents.BuildingRepairedEvent;
import com.minecolonies.core.colony.eventhooks.buildingEvents.BuildingUpgradedEvent;
import com.minecolonies.core.colony.eventhooks.citizenEvents.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

/**
 * Initializer for colony event types, register new event types here.
 */
public final class ModColonyEventDescriptionTypeInitializer
{

    private ModColonyEventDescriptionTypeInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModColonyEventDescriptionTypeInitializer but this is a Utility class.");
    }

    static
    {
        register(CitizenBornEvent.CITIZEN_BORN_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(CitizenBornEvent::loadFromNBT, CitizenBornEvent::loadFromFriendlyByteBuf, CitizenBornEvent.CITIZEN_BORN_EVENT_ID));
        register(CitizenSpawnedEvent.CITIZEN_SPAWNED_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(CitizenSpawnedEvent::loadFromNBT, CitizenSpawnedEvent::loadFromFriendlyByteBuf, CitizenSpawnedEvent.CITIZEN_SPAWNED_EVENT_ID));
        register(VisitorSpawnedEvent.VISITOR_SPAWNED_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(VisitorSpawnedEvent::loadFromNBT, VisitorSpawnedEvent::loadFromFriendlyByteBuf, VisitorSpawnedEvent.VISITOR_SPAWNED_EVENT_ID));
        register(CitizenDiedEvent.CITIZEN_DIED_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(CitizenDiedEvent::loadFromNBT, CitizenDiedEvent::loadFromFriendlyByteBuf, CitizenDiedEvent.CITIZEN_DIED_EVENT_ID));
        register(CitizenGrownUpEvent.CITIZEN_GROWN_UP_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(CitizenGrownUpEvent::loadFromNBT, CitizenGrownUpEvent::loadFromFriendlyByteBuf, CitizenGrownUpEvent.CITIZEN_GROWN_UP_EVENT_ID));
        register(BuildingBuiltEvent.BUILDING_BUILT_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(BuildingBuiltEvent::loadFromNBT, BuildingBuiltEvent::loadFromFriendlyByteBuf, BuildingBuiltEvent.BUILDING_BUILT_EVENT_ID));
        register(BuildingUpgradedEvent.BUILDING_UPGRADED_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(BuildingUpgradedEvent::loadFromNBT, BuildingUpgradedEvent::loadFromFriendlyByteBuf, BuildingUpgradedEvent.BUILDING_UPGRADED_EVENT_ID));
        register(BuildingRepairedEvent.BUILDING_REPAIRED_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(BuildingRepairedEvent::loadFromNBT, BuildingRepairedEvent::loadFromFriendlyByteBuf, BuildingRepairedEvent.BUILDING_REPAIRED_EVENT_ID));
        register(BuildingDeconstructedEvent.BUILDING_DECONSTRUCTED_EVENT_ID.getPath(), () -> new ColonyEventDescriptionTypeRegistryEntry(BuildingDeconstructedEvent::loadFromNBT, BuildingDeconstructedEvent::loadFromFriendlyByteBuf, BuildingDeconstructedEvent.BUILDING_DECONSTRUCTED_EVENT_ID));
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends ColonyEventDescriptionTypeRegistryEntry> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.COLONY_EVENT_DESC_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1); calling this from
     * the mod entry point is what pins the moment it happens.
     */
    public static void init()
    {
    }
}
