package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.colony.colonyEvents.registry.ColonyEventTypeRegistryEntry;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.events.raid.amazonevent.AmazonRaidEvent;
import com.minecolonies.core.colony.events.raid.barbarianEvent.BarbarianRaidEvent;
import com.minecolonies.core.colony.events.raid.egyptianevent.EgyptianRaidEvent;
import com.minecolonies.core.colony.events.raid.norsemenevent.NorsemenRaidEvent;
import com.minecolonies.core.colony.events.raid.norsemenevent.NorsemenShipRaidEvent;
import com.minecolonies.core.colony.events.raid.pirateEvent.DrownedPirateRaidEvent;
import com.minecolonies.core.colony.events.raid.pirateEvent.PirateAirRaidEvent;
import com.minecolonies.core.colony.events.raid.pirateEvent.PirateGroundRaidEvent;
import com.minecolonies.core.colony.events.raid.pirateEvent.PirateRaidEvent;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

/**
 * Initializer for colony event types, register new event types here.
 */
public final class ModColonyEventTypeInitializer
{

    private ModColonyEventTypeInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModColonyEventTypeInitializer but this is a Utility class.");
    }

    static
    {
        register(PirateRaidEvent.PIRATE_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(PirateRaidEvent::loadFromNBT, PirateRaidEvent.PIRATE_RAID_EVENT_TYPE_ID, true));
        register(BarbarianRaidEvent.BARBARIAN_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(BarbarianRaidEvent::loadFromNBT, BarbarianRaidEvent.BARBARIAN_RAID_EVENT_TYPE_ID, true));
        register(EgyptianRaidEvent.EGYPTIAN_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(EgyptianRaidEvent::loadFromNBT, EgyptianRaidEvent.EGYPTIAN_RAID_EVENT_TYPE_ID, true));
        register(AmazonRaidEvent.AMAZON_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(AmazonRaidEvent::loadFromNBT, AmazonRaidEvent.AMAZON_RAID_EVENT_TYPE_ID, true));
        register(NorsemenRaidEvent.NORSEMEN_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(NorsemenRaidEvent::loadFromNBT, NorsemenRaidEvent.NORSEMEN_RAID_EVENT_TYPE_ID, true));
        register(NorsemenShipRaidEvent.NORSEMEN_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(NorsemenShipRaidEvent::loadFromNBT, NorsemenShipRaidEvent.NORSEMEN_RAID_EVENT_TYPE_ID, true));
        register(PirateGroundRaidEvent.PIRATE_GROUND_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(PirateGroundRaidEvent::loadFromNBT, PirateGroundRaidEvent.PIRATE_GROUND_RAID_EVENT_TYPE_ID, true));
        register(DrownedPirateRaidEvent.PIRATE_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(DrownedPirateRaidEvent::loadFromNBT, DrownedPirateRaidEvent.PIRATE_RAID_EVENT_TYPE_ID, true));
        register(PirateAirRaidEvent.PIRATE_AIR_RAID_EVENT_TYPE_ID.getPath(), () -> new ColonyEventTypeRegistryEntry(PirateAirRaidEvent::loadFromNBT, PirateAirRaidEvent.PIRATE_AIR_RAID_EVENT_TYPE_ID, true));
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends ColonyEventTypeRegistryEntry> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.COLONY_EVENT_REGISTRY,
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
