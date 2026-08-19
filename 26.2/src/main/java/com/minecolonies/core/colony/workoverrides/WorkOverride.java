package com.minecolonies.core.colony.workoverrides;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.ITimeBasedHappinessModifier;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.jobs.JobBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

import static com.minecolonies.api.util.constant.HappinessConstants.SLEPTTONIGHT;

/**
 * The "keep working anyway" switches: one per colony, all of them off by default, all of them saved with the colony.
 * <p>
 * Each switch names one thing that ordinarily stops a citizen working and says "carry on regardless". Nothing here
 * changes what the colony feels - a citizen under the mourning override is still mourning, with the grief, the
 * happiness cost and the chat bubble that go with it - it only stops that feeling downing tools. With every switch
 * off, which is how a colony that has never seen {@code /mc colony workoverride} starts, the behaviour is exactly
 * what it always was.
 * <p>
 * The whole feature is reachable from this one symbol: {@code grep -rn WorkOverride src/main/java} is a complete
 * inventory of it. Adding a third switch means adding a constant here, a lang key for its label, and the one guard
 * at the place that stops the worker - the command and the saving pick it up on their own.
 */
public enum WorkOverride
{
    /**
     * Citizens work through their mourning.
     * <p>
     * Aimed at the generational mechanic: on a large colony with a short lifespan somebody dies more or less
     * constantly, so the mourning never lifts and the colony stops. Applies to every citizen.
     */
    MOURNING("mourning"),

    /**
     * Builders work through the night instead of going to bed.
     * <p>
     * Deliberately only builders. The seam it hooks - the sleeping branch of {@code CitizenAI} - is shared by every
     * non-guard worker, so the job check is what keeps this to the profession that was asked for.
     */
    NIGHT("night");

    /**
     * NBT tag the switches are saved under, declared here rather than in {@code NbtTagConstants} so that deleting
     * this file leaves nothing of the feature behind anywhere else. A list of the names of the switches that are on;
     * a name nothing recognises is dropped on load, so a save written by a later version still loads.
     */
    public static final String TAG_WORK_OVERRIDES = "workOverrides";

    /**
     * The name this switch is typed and saved as.
     */
    private final String name;

    /**
     * The translation key of its human readable label.
     */
    private final String labelKey;

    WorkOverride(final String name)
    {
        this.name = name;
        this.labelKey = "com.minecolonies.command.colony.workoverride.switch." + name;
    }

    /**
     * The name this switch is typed and saved as.
     *
     * @return the name.
     */
    public String getSerializedName()
    {
        return name;
    }

    /**
     * The translation key of the switch's human readable label, for chat feedback.
     *
     * @return the translation key.
     */
    public String getLabelKey()
    {
        return labelKey;
    }

    /**
     * Whether this switch is on for a colony.
     * <p>
     * Deliberately not on {@link IColony}: every behaviour that reads it runs on the server, so keeping the flags on
     * {@link Colony} alone spares the interface a stub and the network a field. Nothing client side reads them.
     *
     * @param colony the colony, may be null or a client side view.
     * @return true if the switch is on.
     */
    public boolean isOn(@Nullable final IColony colony)
    {
        return colony instanceof final Colony serverColony && serverColony.isWorkOverrideOn(this);
    }

    /**
     * Find a switch by the name it is typed as.
     *
     * @param name the name.
     * @return the switch, or null if there is no such switch.
     */
    @Nullable
    public static WorkOverride byName(@Nullable final String name)
    {
        for (final WorkOverride override : values())
        {
            if (override.name.equals(name))
            {
                return override;
            }
        }
        return null;
    }

    /**
     * Read the switches that are on out of a colony's NBT.
     *
     * @param compound the colony compound.
     * @return the switches that are on, empty for a colony that has never had one thrown.
     */
    @NotNull
    public static EnumSet<WorkOverride> read(@NotNull final CompoundTag compound)
    {
        final EnumSet<WorkOverride> on = EnumSet.noneOf(WorkOverride.class);
        final ListTag list = compound.getListOrEmpty(TAG_WORK_OVERRIDES);
        for (int i = 0; i < list.size(); i++)
        {
            final WorkOverride override = byName(list.getStringOr(i, ""));
            if (override != null)
            {
                on.add(override);
            }
        }
        return on;
    }

    /**
     * Write the switches that are on into a colony's NBT.
     *
     * @param compound the colony compound.
     * @param on       the switches that are on.
     */
    public static void write(@NotNull final CompoundTag compound, @NotNull final Set<WorkOverride> on)
    {
        final ListTag list = new ListTag();
        for (final WorkOverride override : on)
        {
            list.add(StringTag.valueOf(override.name));
        }
        compound.put(TAG_WORK_OVERRIDES, list);
    }

    /**
     * Whether a citizen should get on with its work rather than stopping to mourn.
     *
     * @param colony the citizen's colony.
     * @return true if mourning should not stop this citizen working.
     */
    public static boolean worksWhileMourning(@Nullable final IColony colony)
    {
        return MOURNING.isOn(colony);
    }

    /**
     * Whether a citizen should stay out at work rather than going to bed at nightfall.
     * <p>
     * The job check is the restriction to builders: the sleeping branch this guards is walked by every non-guard
     * worker, and widening it to all of them was not what was asked for.
     *
     * @param colony the citizen's colony.
     * @param job    the citizen's job, may be null.
     * @return true if nightfall should not stop this citizen working.
     */
    public static boolean worksThroughTheNight(@Nullable final IColony colony, @Nullable final IJob<?> job)
    {
        return job instanceof JobBuilder && NIGHT.isOn(colony);
    }

    /**
     * Count a citizen that is working through the night as rested.
     * <p>
     * Going to bed is what ordinarily resets the "slept tonight" happiness modifier ({@code EntityAISleep}), and that
     * modifier climbs on its own every colony day: three days without a reset and the citizen takes a real happiness
     * penalty for sleep it was never allowed to get. A builder held at work by the night switch is therefore reset
     * here instead, so the override costs it nothing. The days check keeps this to a field read on all but the first
     * pass of each day.
     *
     * @param citizen the citizen working through the night.
     */
    public static void stayRested(@NotNull final ICitizenData citizen)
    {
        final IHappinessModifier modifier = citizen.getCitizenHappinessHandler().getModifier(SLEPTTONIGHT);
        if (modifier instanceof final ITimeBasedHappinessModifier timeBased && timeBased.getDays() > 0)
        {
            citizen.getCitizenHappinessHandler().resetModifier(SLEPTTONIGHT);
        }
    }
}
