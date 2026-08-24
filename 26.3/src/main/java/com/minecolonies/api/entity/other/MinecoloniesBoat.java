package com.minecolonies.api.entity.other;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Special minecolonies boat that exists only to ferry a citizen across water.
 * <p>
 * The water counterpart of {@link MinecoloniesMinecart}: it does not collide, cannot be pushed, cannot be entered or
 * picked by a player, drops nothing when destroyed, and discards itself once it has no passenger left, so an abandoned
 * crossing never litters the world.
 * <p>
 * Unlike the minecart this keeps <em>all</em> of vanilla's physics. {@link Boat} floats, damps and collides on its own,
 * and on the server {@code AbstractBoat#tick} runs {@code floatBoat()} followed by {@code move(SELF, deltaMovement)}
 * whenever {@code isLocalInstanceAuthoritative()} holds -- which it does here, because that only turns false when the
 * controlling passenger is a {@link Player}, and ours is a citizen. Steering therefore consists purely of writing a
 * horizontal delta movement each tick from the navigator (see {@code MinecoloniesAdvancedPathNavigate#handleBoats});
 * vanilla's {@code invFriction} of 0.9 while {@code IN_WATER} damps that into a stable cruise speed. The vertical
 * component is deliberately left alone so buoyancy keeps working.
 */
public class MinecoloniesBoat extends Boat
{
    /**
     * The one citizen this boat was placed for. See {@link VehicleClaim} for the rule and for why it is claimed before
     * the hull enters the world.
     */
    private final VehicleClaim claim = new VehicleClaim();

    /**
     * Constructor to create the boat.
     *
     * @param type  the entity type.
     * @param world the world.
     */
    public MinecoloniesBoat(final EntityType<? extends Boat> type, final Level world)
    {
        // AbstractBoat takes the drop item as a supplier in 26.2. It is only ever read through the final
        // getDropItem()/getPickResult(), and destroy() below makes sure neither path can ever run for us.
        super(type, world, () -> Items.OAK_BOAT);
    }

    /**
     * Claim this boat for the citizen it was placed for. Must be called before the boat is added to the world.
     *
     * @param passenger the only entity that will ever be allowed to board.
     */
    public void claimFor(@NotNull final Entity passenger)
    {
        this.claim.claimFor(passenger);
    }

    /**
     * Refuse everyone except the citizen this boat was placed for.
     * <p>
     * The vanilla brake on a boat's boarding sweep is a {@link Player} at the helm; ours is steered by a citizen, so
     * that brake never applies and the free second seat would be filled by whatever wandered into the hull -- a cow in
     * the shallows before our citizen even got aboard, or a second citizen, whose own path follower would then see a
     * vehicle on a non-boat node and delete the ferry out from under the passenger we were actually carrying. See
     * {@link VehicleClaim} for why refusing is sufficient and needs no eject behind it.
     */
    @Override
    protected boolean canAddPassenger(@NotNull final Entity passenger)
    {
        return claim.accepts(passenger) && super.canAddPassenger(passenger);
    }

    /**
     * One seat. Belt and braces next to {@link #canAddPassenger}: with the seat taken, vanilla's boarding sweep stops
     * considering candidates at all rather than asking and being refused.
     */
    @Override
    protected int getMaxPassengers()
    {
        return 1;
    }

    /**
     * Seat the citizen in the hull rather than half a block above it.
     * <p>
     * PORT-NOTE(26.2): a rider ends up at {@code vehicle.y + rideHeight - passenger.getVehicleAttachmentPoint().y}
     * ({@code Entity#positionRider}). 26.2 carries the passenger's half of that offset on the <em>passenger's</em>
     * entity type, as {@link EntityAttachment#VEHICLE}, and every player-shaped vanilla type declares it:
     * {@code EntityTypes.PLAYER} and {@code MANNEQUIN} both pass {@link Avatar#DEFAULT_VEHICLE_ATTACHMENT}, which is
     * {@code (0, 0.6, 0)}. It exists because a seated humanoid is drawn with its legs folded forward, so the model has
     * to be anchored well below the deck for the body to land on it.
     * <p>
     * A citizen is player-shaped -- 0.6 by 1.8, the player model, and the seated pose vanilla gives any passenger --
     * but {@code EntityInitializer} builds its type without that attachment, so its half of the offset is the
     * {@code AT_FEET} fallback of zero. The citizen therefore rode exactly 0.6 higher than a player would in the same
     * boat, which is the reported "sits half a block above the boat rather than in it".
     * <p>
     * Corrected here rather than on the citizen's entity type: the type is shared with the minecart and the cavalry
     * horse, both of which already place their riders to their own taste, and moving them is not this boat's business.
     * Only the one citizen this ferry was claimed for can ever board it (see {@link #canAddPassenger}), so subtracting
     * the attachment that citizen does not carry is exact rather than approximate.
     */
    @Override
    protected double rideHeight(@NotNull final EntityDimensions dimensions)
    {
        return super.rideHeight(dimensions) - Avatar.DEFAULT_VEHICLE_ATTACHMENT.y;
    }

    @Override
    protected void destroy(@NotNull final ServerLevel level, @NotNull final DamageSource source)
    {
        this.kill(level);
    }

    @Override
    @NotNull
    public InteractionResult interact(@NotNull final Player player, @NotNull final InteractionHand hand, @NotNull final Vec3 location)
    {
        return InteractionResult.FAIL;
    }

    /**
     * Targetable only once nobody is aboard.
     * <p>
     * While it is carrying a citizen it stays untouchable, so a player cannot shoot the ferry out from under them
     * mid-crossing. Empty, it is an ordinary entity and can be broken by hand -- {@link #destroy} removes it without a
     * drop, so there is still no way to farm boats off the colony.
     * <p>
     * Returning false unconditionally meant the player's raycast never selected the boat at all, so no attack could
     * reach it however much damage it would have taken. {@link #tick} discards an empty hull within a second, but that
     * only runs where the entity ticks: one left behind in a chunk that is loaded without ticking, or by a build whose
     * passenger never dismounted, simply sat there with no way to clear it short of {@code /kill}.
     */
    @Override
    public boolean isPickable()
    {
        return getPassengers().isEmpty();
    }

    @Override
    public void push(@NotNull final Entity entityIn)
    {
        // Do nothing
    }

    @Override
    public void playerTouch(@NotNull final Player entityIn)
    {
        // Do nothing
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public boolean canCollideWith(final Entity other)
    {
        return false;
    }

    /**
     * How long after spawning a boat is allowed to sit empty before it cleans itself up.
     * <p>
     * The navigator mounts its citizen in the same tick it spawns the boat, so in normal use nothing is ever riding
     * on this grace period; it exists so a boat can still be summoned and boarded by hand for testing.
     */
    private static final int EMPTY_GRACE_TICKS = 20;

    /**
     * Game time until which this hull is moored: held in place and not cleaned up by the navigator.
     * <p>
     * A deadline rather than a flag, and refreshed by whoever wants the boat rather than cleared by them. The
     * navigator discards a colony boat the moment its passenger has no path left to follow -- which is every tick a
     * citizen stands still -- so something has to say "leave this one alone"; but a plain flag would be a way to
     * strand a citizen in a hull for the rest of the save the first time the thing that set it stopped running,
     * whether because the worker was unassigned, went to sleep, died or had his chunk unloaded. A deadline expires
     * on its own and hands the boat straight back to the ordinary cleanup.
     */
    private long mooredUntil = 0L;

    /**
     * Moor this hull until the given game time, keeping it in place and out of the navigator's cleanup.
     *
     * @param untilGameTime the game time the mooring lapses at. Call again to extend it.
     */
    public void moor(final long untilGameTime)
    {
        this.mooredUntil = Math.max(this.mooredUntil, untilGameTime);
    }

    /**
     * @return true while this hull is moored.
     */
    public boolean isMoored()
    {
        return level().getGameTime() < mooredUntil;
    }

    @Override
    public void tick()
    {
        // Anchor. Vanilla pushes a boat with flowing water -- that is how a boat goes down a river -- and a fisherman
        // whose hull drifts off the pond he was sent to would carry on casting into whatever he ended up over. Only
        // the horizontal position is held: the vertical one is buoyancy, and the class comment above says why that is
        // left alone. It costs nothing at all for a boat that is not moored.
        //
        // Both sides of super.tick() are needed, and the second is the one that does the work. Clearing the delta
        // beforehand throws away momentum the hull had already gathered, but the push itself is applied inside the
        // tick, between the clear and the move, so a hull in a current still creeps by one tick's worth of it every
        // tick -- 0.014 per tick for water, about a quarter of a block a second, which is drift and not mooring.
        // Putting the horizontal coordinates back afterwards is what actually pins it.
        final boolean moored = !level().isClientSide() && isMoored();
        final double mooredX = getX();
        final double mooredZ = getZ();
        if (moored)
        {
            final Vec3 movement = getDeltaMovement();
            setDeltaMovement(0.0D, movement.y, 0.0D);
        }

        super.tick();

        if (moored && (getX() != mooredX || getZ() != mooredZ))
        {
            setPos(mooredX, getY(), mooredZ);
            final Vec3 movement = getDeltaMovement();
            setDeltaMovement(0.0D, movement.y, 0.0D);
        }

        // Server only. Whether an entity exists is the server's call, and a client that has the boat but has not yet
        // been told who is riding it would otherwise delete its own copy after a second and never get it back -- the
        // add-entity packet is not resent. Vanilla does send the passenger link from both ends
        // (ServerEntity#sendPairingData emits ClientboundSetPassengersPacket for the vehicle and again for the
        // passenger), so the window is small, but nothing is gained by racing it.
        //
        // Checked every tick rather than the minecart's once every twenty. An orphaned minecart merely sits on its
        // rails; an orphaned boat drifts, so it should go the moment its passenger is gone -- whether they stepped
        // ashore, were teleported away, or died mid-crossing.
        if (!level().isClientSide() && this.tickCount > EMPTY_GRACE_TICKS && getPassengers().isEmpty())
        {
            this.remove(RemovalReason.DISCARDED);
        }
    }
}
