package com.minecolonies.core.colony.buildings.modules.settings;

import net.minecraft.util.Mth;

/**
 * The number of minutes a stable's cavalry waits at the building between patrol sorties.
 * <p>
 * A plain {@link IntSetting} would do everything this does except hold the value inside a range, and an interval is a
 * quantity a player can put a silly number into: a negative one reads as "always due" and a four figure one parks the
 * unit at its stable for the rest of the save. Both ends are therefore clamped on every write - the text field, the
 * NBT read, and the network read all land in {@link #setValue(int)} or the constructor - so a value that is out of
 * range cannot exist, rather than being tolerated at the point it is read.
 * <p>
 * Zero is a legal setting and means "no wait": the cavalry leaves on the next sortie as soon as it finishes the last
 * one. That is deliberately the bottom of the range rather than one, because the field is edited by clearing it and
 * typing, and a lower bound above zero would fight the player for the moment the box is empty.
 */
public class PatrolIntervalSetting extends IntSetting
{
    /**
     * Shortest interval: no wait at the stable at all.
     */
    public static final int MIN_INTERVAL = 0;

    /**
     * Longest interval, in minutes. An hour of colony time between sorties is already a unit that is barely on duty.
     */
    public static final int MAX_INTERVAL = 60;

    /**
     * The interval a stable starts with, in minutes.
     */
    public static final int DEFAULT_INTERVAL = 6;

    /**
     * Create a new patrol interval setting at the default interval.
     */
    public PatrolIntervalSetting()
    {
        super(DEFAULT_INTERVAL);
    }

    /**
     * Create a new patrol interval setting.
     *
     * @param value the current value, in minutes.
     * @param def   the default value, in minutes.
     */
    public PatrolIntervalSetting(final int value, final int def)
    {
        super(clamp(value), clamp(def));
    }

    @Override
    public void setValue(final int value)
    {
        super.setValue(clamp(value));
    }

    /**
     * Bring an interval into range.
     *
     * @param value the interval to clamp, in minutes.
     * @return the same interval, no lower than {@link #MIN_INTERVAL} and no higher than {@link #MAX_INTERVAL}.
     */
    public static int clamp(final int value)
    {
        return Mth.clamp(value, MIN_INTERVAL, MAX_INTERVAL);
    }
}
