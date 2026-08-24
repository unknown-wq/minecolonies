package com.minecolonies.api.entity.other;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Special minecolonies minecart that doesn't collide.
 *
 * <p>TODO(port-26.2): DISABLED — the custom rail physics (the {@code moveAlongTrack(BlockPos, BlockState)} override
 * and the hand-rolled {@code tick()} that drove it) are gone. In 26.2 {@code AbstractMinecart} no longer exposes any
 * of the pieces that code was built from: {@code moveAlongTrack} now takes a {@code ServerLevel} and delegates to a
 * {@link net.minecraft.world.entity.vehicle.minecart.MinecartBehavior}, {@code getPos}/{@code shouldDoRailFunctions}/
 * {@code moveMinecartOnRail}/{@code comeOffTrack}/{@code canUseRail} moved into {@code OldMinecartBehavior}
 * (package-private state), {@code BaseRailBlock#getRailDirection} and {@code PoweredRailBlock#isActivatorRail} no
 * longer exist, and the interpolation fields ({@code lerpSteps}, {@code lerpX}…) moved to
 * {@code Entity.InterpolationHandler}. Reimplementing it would mean re-deriving a whole vanilla subsystem. Physics is
 * therefore handed back to vanilla {@link Minecart}; the cart still doesn't collide, can't be pushed or ridden by
 * players, keeps its lowered passenger attachment and still discards itself once empty. Observable
 * difference: vanilla rail speed/curve handling instead of the tuned MineColonies one, so citizen cart rides feel
 * like vanilla minecart rides.</p>
 *
 * <p>Like {@link MinecoloniesBoat} it carries only the one citizen it was placed for -- see {@link #canAddPassenger}
 * and {@link VehicleClaim} -- and, empty, it can be broken by hand so a cart that somehow ends up occupied is never
 * permanent litter.</p>
 */
public class MinecoloniesMinecart extends Minecart
{
    private static final Vec3 LOWERED_PASSENGER_ATTACHMENT = new Vec3(0.0, 0.0, 0.0);

    /**
     * The one citizen this cart was placed for. See {@link VehicleClaim} for the rule and for why it is claimed before
     * the cart enters the world.
     */
    private final VehicleClaim claim = new VehicleClaim();

    /**
     * Constructor to create the minecart.
     *
     * @param type  the entity type.
     * @param world the world.
     */
    public MinecoloniesMinecart(final EntityType<?> type, final Level world)
    {
        super(type, world);
    }

    /**
     * Claim this cart for the citizen it was placed for. Must be called before the cart is added to the world.
     *
     * @param passenger the only entity that will ever be allowed to board.
     */
    public void claimFor(@NotNull final Entity passenger)
    {
        this.claim.claimFor(passenger);
    }

    /**
     * Refuse everyone except the citizen this cart was placed for.
     * <p>
     * PORT-NOTE(26.2): the twin of {@link MinecoloniesBoat#canAddPassenger}, and it was missing. A cart parked on a
     * colony rail is picked up by whatever walks into it: 26.2 boards a minecart from the movement behaviour rather
     * than from {@code AbstractMinecart#push}, which no longer boards anything, but the outcome is the same. Both
     * {@code NewMinecartBehavior#pickupEntities} and the inline loop in {@code OldMinecartBehavior#pushAndPickupEntities}
     * collect everything in the hitbox that is not a player, an iron golem or another minecart and call
     * {@code startRiding} on it. That is the unforced overload, so this refusal is on the path and is enough on its
     * own -- see {@link VehicleClaim}.
     * <p>
     * There is no {@code getMaxPassengers} to override to go with it, as there is on the boat: that method belongs to
     * {@code AbstractBoat}, where it returns 2, and a minecart's seat count comes from {@code Entity#canAddPassenger},
     * which is already "one passenger". {@code super} below is that method, so the cap is enforced by the same call
     * that enforces the claim.
     */
    @Override
    protected boolean canAddPassenger(@NotNull final Entity passenger)
    {
        return claim.accepts(passenger) && super.canAddPassenger(passenger);
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
     * Targetable only once nobody is aboard, the same rule {@link MinecoloniesBoat#isPickable} settled on and for the
     * same reason.
     * <p>
     * PORT-NOTE(26.2): this returned false unconditionally, which combined with {@link #tick} badly. A cart only
     * discards itself while it is empty, so a cart that something climbed into before {@link #canAddPassenger} existed
     * neither cleans itself up nor can be selected by a player's raycast: it is permanent litter with no way to clear
     * it short of {@code /kill}. Carrying its citizen it is still untouchable, so a passing player cannot break a cart
     * out from under a worker mid-journey, and {@link #destroy} still removes it without a drop, so there is no way to
     * farm carts off the colony either.
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

    @Override
    public void tick()
    {
        super.tick();

        if (this.tickCount % 20 == 19 && getPassengers().isEmpty())
        {
            this.remove(RemovalReason.DISCARDED);
        }
    }

    @Override
    @NotNull
    protected Vec3 getPassengerAttachmentPoint(@NotNull final Entity passenger, @NotNull final EntityDimensions dimensions, final float scale)
    {
        return LOWERED_PASSENGER_ATTACHMENT;
    }
}
