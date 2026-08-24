package com.minecolonies.api.sounds;

import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.entity.mobs.RaiderType;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.resources.Identifier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

import static com.minecolonies.core.generation.SoundsJson.createSoundJson;

/**
 * Registering of sound events for our colony.
 */
public final class ModSoundEvents
{
    /**
     * Citizen sound prefix.
     */
    public static final String CITIZEN_SOUND_EVENT_PREFIX = "citizen.";


    /**
     * Map of sound events.
     */
    public static Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> CITIZEN_SOUND_EVENTS = new HashMap<>();

    /**
     * Saw sound event.
     */
    public static SoundEvent SAW;

    /**
     * Private constructor to hide the implicit public one.
     */
    private ModSoundEvents()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Register the {@link SoundEvent}s.
     *
     * @param registry the registry to register at.
     */
    static
    {
        final List<Identifier> mainTypes = new ArrayList<>(ModJobs.getJobs());
        mainTypes.remove(ModJobs.PLACEHOLDER_ID);
        mainTypes.add(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "unemployed"));
        mainTypes.add(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "visitor"));

        for (final Identifier job : mainTypes)
        {
            final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> map = new HashMap<>();
            for (final EventType event : EventType.values())
            {
                final List<Tuple<SoundEvent, SoundEvent>> individualSounds = new ArrayList<>();
                for (int i = 1; i <= 4; i++)
                {
                    final SoundEvent maleSoundEvent =
                      ModSoundEvents.getSoundID(CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".male" + i + "." + event.getId());
                    final SoundEvent femaleSoundEvent =
                      ModSoundEvents.getSoundID(CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + ".female" + i + "." + event.getId());

                    register(maleSoundEvent);
                    register(femaleSoundEvent);
                    individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
                }
                map.put(event, individualSounds);
            }
            CITIZEN_SOUND_EVENTS.put(job.getPath(), map);
        }

        final Map<EventType, List<Tuple<SoundEvent, SoundEvent>>> map = new HashMap<>();
        for (final EventType event : EventType.values())
        {
            final List<Tuple<SoundEvent, SoundEvent>> individualSounds = new ArrayList<>();
            for (int i = 1; i <= 2; i++)
            {
                final SoundEvent maleSoundEvent =
                        ModSoundEvents.getSoundID(CITIZEN_SOUND_EVENT_PREFIX + "child.male" + i + "." + event.getId());
                final SoundEvent femaleSoundEvent =
                        ModSoundEvents.getSoundID(CITIZEN_SOUND_EVENT_PREFIX + "child.female" + i + "." + event.getId());

                individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
                individualSounds.add(new Tuple<>(maleSoundEvent, femaleSoundEvent));
            }
            map.put(event, individualSounds);
        }
        CITIZEN_SOUND_EVENTS.put("child", map);

        register(TavernSounds.tavernTheme);

        for (final RaiderType raiderType : RaiderType.values())
        {
            final SoundEvent raiderHurt = ModSoundEvents.getSoundID("mob." + raiderType.name().toLowerCase(Locale.US) + ".hurt");
            final SoundEvent raiderDeath = ModSoundEvents.getSoundID("mob." + raiderType.name().toLowerCase(Locale.US) + ".death");
            final SoundEvent raiderSay = ModSoundEvents.getSoundID("mob." + raiderType.name().toLowerCase(Locale.US) + ".say");

            register(raiderHurt);
            register(raiderDeath);
            register(raiderSay);

            final Map<RaiderSounds.RaiderSoundTypes, SoundEvent> sounds = new HashMap<>();
            sounds.put(RaiderSounds.RaiderSoundTypes.HURT, raiderHurt);
            sounds.put(RaiderSounds.RaiderSoundTypes.DEATH, raiderDeath);
            sounds.put(RaiderSounds.RaiderSoundTypes.SAY, raiderSay);

            RaiderSounds.raiderSounds.put(raiderType, sounds);
        }

        SAW = ModSoundEvents.getSoundID("tile.sawmill.saw");
        register(SAW);

        register(RaidSounds.WARNING);
        register(RaidSounds.WARNING_EARLY);
        register(RaidSounds.VICTORY);
        register(RaidSounds.VICTORY_EARLY);

        register(RaidSounds.AMAZON_RAID);

        register(RaidSounds.DESERT_RAID);
        register(RaidSounds.DESERT_RAID_WARNING);

        register(MercenarySounds.mercenaryAttack);
        register(MercenarySounds.mercenaryCelebrate);
        register(MercenarySounds.mercenaryDie);
        register(MercenarySounds.mercenaryHurt);
        register(MercenarySounds.mercenarySay);
        register(MercenarySounds.mercenaryStep);
    }

    /**
     * Register one {@link SoundEvent} eagerly.
     * <p>
     * 26.2 has no {@code DeferredRegister} and {@code SoundEvent} became a record, so its id is {@code location()}
     * rather than {@code getLocation()}.
     *
     * @param event the sound event to register.
     * @return the same sound event.
     */
    private static SoundEvent register(final SoundEvent event)
    {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, event.location(), event);
    }

    /**
     * Class-load hook — registration happens eagerly in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }

    /**
     * Register a {@link SoundEvent}.
     *
     * @param soundName The SoundEvent's name without the minecolonies prefix
     * @return The SoundEvent
     */
    public static SoundEvent getSoundID(final String soundName)
    {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(Constants.MOD_ID, soundName));
    }
}
