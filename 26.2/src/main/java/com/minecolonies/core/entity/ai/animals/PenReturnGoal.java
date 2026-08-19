package com.minecolonies.core.entity.ai.animals;

import com.minecolonies.core.MineColonies;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import org.jetbrains.annotations.NotNull;

/**
 * Walks a herded animal back to the hut it belongs to once it has left it.
 * <p>
 * The containment itself is vanilla's own home restriction ({@code Mob#setHomeTo}), which the wander goals consult
 * through {@code GoalUtils#isRestricted} and which therefore only stops an animal <em>choosing</em> to leave. Nothing
 * in vanilla brings a farm animal back once something else has moved it - a push, a panic, a player with wheat, a
 * boat, rising water. Vanilla does own a goal for exactly that job, {@link MoveTowardsRestrictionGoal}, but only
 * iron golems and a few others are built with it; farm animals are not. So this adds it to them.
 * <p>
 * PORT-NOTE(26.2): {@code PathfinderMob#restrictTo} of 1.21.1 is {@code Mob#setHomeTo} here, and the whole home
 * mechanism moved up from {@code PathfinderMob} to {@code Mob}. Two consequences matter downstream and both are
 * relied upon by {@link AnimalPen}: the home is written to entity NBT ({@code home_pos} / {@code home_radius},
 * {@code Mob#addAdditionalSaveData}), so containment survives a chunk unload and a server restart on its own; and
 * {@code Mob#onLeashRemoved} clears it, so unleashing an animal releases it from its pen with no code of ours
 * involved.
 * <p>
 * The goal is deliberately inert rather than absent when it does not apply: it is attached to a live entity and so
 * cannot be persisted, and re-deriving on every entity load which animals are penned would cost more than letting
 * {@code canUse} answer false. An animal with no home answers false through vanilla's own
 * {@code Mob#isWithinHome() == true} for {@code homeRadius == -1}.
 */
public class PenReturnGoal extends MoveTowardsRestrictionGoal
{
    /**
     * Walk speed on the way back. One is the animal's own walking speed - a farm animal trotting home is meant to
     * look like it wandered back, not like it was called.
     */
    private static final double SPEED = 1.0D;

    /**
     * The animal, kept because {@link MoveTowardsRestrictionGoal} holds its own reference privately.
     */
    private final PathfinderMob animal;

    /**
     * Attach the goal to an animal.
     *
     * @param animal the animal.
     */
    public PenReturnGoal(@NotNull final PathfinderMob animal)
    {
        super(animal, SPEED);
        this.animal = animal;
    }

    @Override
    public boolean canUse()
    {
        if (!MineColonies.getConfig().getServer().animalPenContainment.get())
        {
            return false;
        }

        // A leashed animal already has a home: vanilla's PathfinderMob#whenLeashedTo re-points it at the leash
        // holder every tick. Walking "home" would then mean walking at whoever is dragging it, fighting
        // closeRangeLeashBehaviour for the MOVE flag. Whoever holds the lead decides where the animal goes.
        if (animal instanceof final Leashable leashable && leashable.isLeashed())
        {
            return false;
        }

        return super.canUse();
    }
}
