package com.minecolonies.api.entity.other;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The rule that a colony vehicle carries exactly the one citizen it was placed for, held in one place so the boat and
 * the minecart cannot drift into two subtly different versions of it.
 * <p>
 * Vanilla vehicles are a public service. A boat sweeps its own bounding box every tick and calls {@code startRiding} on
 * any pushable living entity it finds ({@code AbstractBoat#tick}); a minecart does the same from its behaviour's
 * {@code pushAndPickupEntities} ({@code NewMinecartBehavior#pickupEntities}, and the inline equivalent in
 * {@code OldMinecartBehavior}). Neither asks whether the vehicle was put there for somebody. A colony vehicle spawned
 * on a road or a shore is therefore filled by whatever wandered into it -- a cow, a zombie, or a second citizen, whose
 * own path follower would then delete the vehicle out from under the passenger it was actually carrying.
 * <p>
 * Refusing is the whole mechanism, and it is enough because of how those sweeps board: both call the unforced
 * {@code Entity#startRiding(Entity)}, which is {@code startRiding(entity, false, true)}, and with {@code force} false
 * that method only seats the rider if {@code entityToRide.canAddPassenger(this)} agrees. Nothing has to be ejected
 * afterwards. The navigator's own mount, by contrast, passes {@code force} as true and deliberately bypasses the
 * check -- which is exactly why the claim has to be in place before {@code addFreshEntity}, not after it. Between
 * those two calls the vehicle is in the world and, unclaimed, would accept anyone.
 * <p>
 * The claim is deliberately not saved. A colony vehicle only outlives a tick while it is carrying someone, a reload
 * re-attaches its passenger with a forced {@code startRiding} that does not consult it, and an unclaimed empty
 * vehicle discards itself within a second of loading. An empty claim therefore means "accept nobody", which is the
 * safe reading of a vehicle nobody can prove ownership of.
 */
public final class VehicleClaim
{
    /**
     * The one entity allowed aboard, or null while the vehicle is unclaimed.
     */
    @Nullable
    private UUID claimedFor;

    /**
     * Claim the vehicle. Must be called before it is added to the world.
     *
     * @param passenger the only entity that will ever be allowed to board.
     */
    public void claimFor(@NotNull final Entity passenger)
    {
        this.claimedFor = passenger.getUUID();
    }

    /**
     * Whether an entity is the one this vehicle was placed for. Callers combine this with {@code super}'s own
     * {@code canAddPassenger}, which is what still enforces the seat count.
     *
     * @param passenger the entity asking to board.
     * @return true only for the entity the vehicle was claimed for.
     */
    public boolean accepts(@NotNull final Entity passenger)
    {
        return passenger.getUUID().equals(claimedFor);
    }
}
