package com.minecolonies.core.entity.other.cavalry;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.minecolonies.api.colony.IAnimalData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.managers.interfaces.IAnimalDataView;
import com.minecolonies.api.colony.managers.interfaces.IManagedAnimal;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.util.CompatibilityUtils;
import com.minecolonies.api.util.DamageSourceKeys;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.LookHandler;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.colony.jobs.guard.JobCavalry;
import com.minecolonies.core.entity.ai.cavalry.CavalryStrollGoal;
import com.minecolonies.core.entity.ai.cavalry.ReturnToStableGoal;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.mobs.AnimalColonyHandler;
import com.minecolonies.core.entity.mobs.IAnimalColonyHandler;
import com.minecolonies.core.entity.pathfinding.PathPointExtended;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Variant;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStandGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
/**
 * Cavalry Horse Entity class for Minecolonies.
 * Extends the vanilla Horse entity with custom behavior for cavalry units.
 */
public class CavalryHorseEntity extends Horse implements IManagedAnimal<CavalryHorseEntity>
{
    public static final EntityDataAccessor<Integer>  DATA_COLONY_ID         = SynchedEntityData.defineId(CavalryHorseEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer>  DATA_MANAGED_ANIMAL_ID = SynchedEntityData.defineId(CavalryHorseEntity.class, EntityDataSerializers.INT);

    /** 
     * The base width and height of the horse entity. 
     * Note that the width is deliberately slim to allow 1-wide pathing for cavalry units. 
     * Base height matches vanilla horse height.
     */
    public static final float SLIM_W = 0.70F;
    public static final float BASE_H = 1.6F;

    /**
     * The offset used to adjust the position of the rider on the horse.
     */
    private static final float SEATING_OFFSET = 0.75F;

    /**
     * The cooldown for logging when debugging.
     */
    public static final int LOG_COOLDOWN_INTERVAL = 200;
    private int logCooldown = 0;

    public static final float COMBAT_READINESS_THRESHOLD = .66f;

    /**
     * The animal colony handler.
     */
    private IAnimalColonyHandler animalColonyHandler = null;

    /**
     * The animal data associated with this cavalry horse.
     */
    IAnimalData animalData;

    /**
     * Animal data view.
     */
    private IAnimalDataView animalDataView;

    /**
     * The limit after which a reservation expires (in ticks).
     */
    private static final int RESERVATION_EXPIRATION_LIMIT = 200;
    private int reservationExpiration = 0;

    /**
     * The timepoint at which the entity last collided
     */
    private long lastHorizontalCollision = 0;

    /**
     * The timepoint at which a rider last dismounted.
     */
    private long lastDismountTime = -1;

    /**
     * The number of nodes to look ahead when checking for ladder climbing.
     */
    private static final int CLIMB_LOOKAHEAD_NODES = 8;
    private static final double LOOK_AT_HORIZONTAL_EPSILON = 0.04D;
    private static final float RIDER_ALIGN_MAX_STEP_DEGREES = 12.0F;

    /**
     * How hard a citizen rides, as a fraction of the input a player gives when he holds "forward".
     *
     * A player's zza is 1.0, so a player-ridden horse accelerates by its full MOVEMENT_SPEED every tick
     * (LivingEntity#travel -> Entity#getInputVector, which only normalises an input vector longer than 1).
     * A mob rider never touches that path: Mob#setSpeed writes the *same* number into both the speed field
     * and zza, so a horse under a citizen accelerates by MOVEMENT_SPEED **squared** - about a quarter of the
     * player figure for a typical horse, which is why cavalry used to be slower than the infantry it escorts.
     *
     * 0.60 puts a citizen at sixty percent of a gallop. See {@link #CITIZEN_RIDE_MAX_ACCELERATION} for the
     * numbers that come out of it.
     */
    private static final double CITIZEN_RIDE_INPUT = 0.60D;

    /**
     * Ceiling on the per-tick acceleration handed to a citizen-ridden horse.
     *
     * Steady-state speed on ordinary ground is acceleration / (1 - 0.6 * 0.91) = 2.203 * acceleration blocks
     * per tick, so 0.18 caps a mounted guard at 7.9 blocks/s. A citizen walks at 0.30 MOVEMENT_SPEED, which
     * by the same arithmetic is 3.96 blocks/s, so the ceiling is exactly twice a guard on foot. It exists
     * because the horse's MOVEMENT_SPEED is rolled at random and then multiplied by 1.25 on conversion
     * (see {@link #createFromVanilla}), and an uncapped fast roll would move more than half a block per tick
     * - the pathfinder hands out waypoints one block apart, and a mount that clears one per tick sails past
     * corners instead of turning at them.
     */
    private static final double CITIZEN_RIDE_MAX_ACCELERATION = 0.18D;

    /**
     * Constructor for CavalryHorseEntity.
     *
     * @param type  The entity type
     * @param level The level the entity is in
     */
    public CavalryHorseEntity(EntityType<? extends Horse> type, Level level)
    {
        super(type, level);

        final AttributeInstance step = this.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null)
        {
            step.setBaseValue(1.1D);
        }

        this.animalColonyHandler = new AnimalColonyHandler(this);
    }

    /**
     * Registers the goals for this entity.
     * <p>
     * This sets the float goal, follow parent goal, breed goal, validate stable goal, return to stable goal, water avoiding random stroll goal, look at player goal, and random look around goal.
     * If the entity can perform rearing, it also sets the random stand goal.
     */
    @Override
    public void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new FollowParentGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new ReturnToStableGoal(this, .80D, 20.0));
        this.goalSelector.addGoal(7, new CavalryStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        if (this.canPerformRearing())
        {
            this.goalSelector.addGoal(10, new RandomStandGoal(this));
        }
    }

    /**
     * Called when the entity's data is updated from the server. If the entity has a citizen colony handler, it calls the handler's onSyncDataUpdate method.
     * If the entity is on the client side and the data accessor is DATA_STYLE, it checks if the entity's style is in the list of valid styles and if not, it sets the style to the first valid style in the list.
     * @param dataAccessor The data accessor which contains the updated data.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> dataAccessor)
    {
        super.onSyncedDataUpdated(dataAccessor);
        if (animalColonyHandler != null)
        {
            animalColonyHandler.onSyncedDataUpdated(dataAccessor);
        }
    }

    /**
     * This method is called by the entity every tick to update its state.
     * It handles registration of the entity to the closest colony if it was summoned into the world via an ops command.
     * It also handles updating the client side of the managed animal data and the animal colony handler.
     */
    @Override
    public void aiStep()
    {
        super.aiStep();

        int colonyId = getColonyId();

        // if the entity is summoned into the world with an ops command rather than created by the stablemaster, this "autoregisters" the entity as a managed animal to the closest colony.
        if (colonyId == 0 && !CompatibilityUtils.getWorldFromEntity(this).isClientSide())
        {
            IColony colony = IColonyManager.getInstance().getClosestColony(level(), this.blockPosition());

            if (colony == null)
            {
                return;
            }

            if (getAnimalData() == null)
            {
                animalData = colony.getAnimalManager().createAndRegisterAnimalData(this);
            } 

            colonyId =  colony.getID();
            setColonyId(colonyId);
        }

        if (CompatibilityUtils.getWorldFromEntity(this).isClientSide())
        {
            animalColonyHandler.updateColonyClient();

            if (animalColonyHandler.getColonyId() != 0 && getManagedAnimalId() != 0 && getOffsetTicks() % CitizenConstants.TICKS_20 == 0)
            {
                final IColonyView colonyView = IColonyManager.getInstance().getColonyView(animalColonyHandler.getColonyId(), level().dimension());
                if (colonyView != null)
                {
                    this.animalDataView = colonyView.getAnimal(getManagedAnimalId());
                }
            }
        }
        else
        {
            animalColonyHandler.registerWithColony(colonyId, getManagedAnimalId());
        }
    }

    /**
     * Defines the synced data for this entity.
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) 
    {
        super.defineSynchedData(builder);
        builder.define(DATA_COLONY_ID, 0);          // NEEDS CONVERSION
        builder.define(DATA_MANAGED_ANIMAL_ID, 0);  // NEEDS CONVERSION
    }

    /**
     * Gets the accessor for the colony ID of this entity.
     *
     * @return the accessor for the colony ID
     */
    @Override
    public EntityDataAccessor<Integer> getColonyIdAccessor()
    {
        return DATA_COLONY_ID;
    }

    @Override
    public int getColonyId()
    {
        return entityData.get(DATA_COLONY_ID);
    }

    @Override
    public void setColonyId(final int colonyId)
    {
        entityData.set(DATA_COLONY_ID, colonyId);
        animalColonyHandler.setColonyId(colonyId);
    }

    /**
     * Gets the accessor for the animal ID of this entity.
     * 
     * @return the accessor for the animal ID
     */
    @Override
    public EntityDataAccessor<Integer> getAnimalIdAccessor()
    {
        return DATA_MANAGED_ANIMAL_ID;
    }

    /**
     * Get the managed animal ID of this entity.
     *
     * @return the managed animal ID
     */
    @Override
    public int getManagedAnimalId()
    {
        return entityData.get(DATA_MANAGED_ANIMAL_ID);
    }

    /**
     * Set the managed animal ID of this entity.
     * This method is also called whenever the entity is registered to a new colony.
     * 
     * @param managedAnimalId the managed animal ID to set
     */

    @Override
    public void setManagedAnimalId(final int managedAnimalId)
    {   
        entityData.set(DATA_MANAGED_ANIMAL_ID, managedAnimalId);
    }

    /**
     * Check if the horse has a valid cavalry rider.
     * 
     * The rider must be an instance of EntityCitizen and have a valid job handler.
     * The job handler must also be an instance of JobCavalry.
     * 
     * @return true if the horse has a valid cavalry rider, false otherwise.
     */
    public boolean hasCavalryRider()
    {
        if (this.getPassengers().isEmpty())
        {
            return false;
        }

        Entity rider = this.getFirstPassenger();

        if (rider instanceof EntityCitizen guard && guard.getCitizenJobHandler().getColonyJob() instanceof JobCavalry)
        {
            return true;
        }

        return false;
    }

    /**
     * Checks if the horse has a trainer entity (i.e. an entity which is leashing the horse) and if that entity is a citizen.
     * This is used to determine if the horse should be slim (using the vanilla width/height) or can be wide (using the custom width/height).
     *
     * @return true if the horse has a trainer citizen, false otherwise.
     */
    public boolean hasTrainer()
    {
        Entity trainer = this.getLeashHolder();

        if (trainer == null)
        {
            return false;
        }

        return trainer instanceof EntityCitizen;
    }

    /**
     * Adds a passenger to the horse, dropping the leash and clearing the recent dismount cooldown.
     *
     * @param passenger the passenger to add
     */
    @Override
    protected void addPassenger(@NotNull Entity passenger)
    {
        super.addPassenger(passenger);
        removeLeash();
        lastDismountTime = -1;
    }

    /**
     * Called when a passenger is removed from this horse.
     *
     * @param passenger the passenger being removed
     */
    @Override
    protected void removePassenger(@NotNull Entity passenger)
    {
        super.removePassenger(passenger);
        lastDismountTime = this.level().getGameTime();
    }

    /**
     * Sets the entity that this horse is leashed to.
     * 
     * @param entity the entity to leash to
     * @param sendPacket whether to send a packet to clients
     */

    @Override
    public void setLeashedTo(@NotNull Entity entity, boolean sendPacket)
    {
        super.setLeashedTo(entity, sendPacket);
    }


    /**
     * Returns the attachment point for the given passenger entity, taking into account the entity dimensions and the partial tick.
     * This method is overridden to lower the attachment point by {@link #SEATING_OFFSET} to line up with the saddle visuals.
     * 
     * @param passenger the passenger entity
     * @param dims the entity dimensions
     * @param partialTick the partial tick
     * @return the attachment point
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(@NotNull Entity passenger,
                                            @NotNull EntityDimensions dims,
                                            float partialTick)
    {
        // Vanilla attachment point
        Vec3 base = super.getPassengerAttachmentPoint(passenger, dims, partialTick);

        // Lower rider to line up with saddle visuals
        return base.add(0.0D, -SEATING_OFFSET, 0.0D);
    }

    /**
     * Gets the last time this horse was dismounted from, in game ticks.
     * 
     * @return the last time this horse was dismounted from, in game ticks
     */
    public long getLastDismountTime()
    {
        return lastDismountTime;
    }

    /**
     * Creates a new PathNavigation for this horse entity, overriding the default vanilla horse navigation. This allows the horse to
     * navigate the world in a way that is more suitable for guards.
     * 
     * @param level the level to spawn the new entity in
     * @return the new PathNavigation for this horse entity
     */
    @Override
    protected PathNavigation createNavigation(@NotNull Level level)
    {
        MinecoloniesAdvancedPathNavigate pathNavigation = new MinecoloniesAdvancedPathNavigate(this, level);
        pathNavigation.getPathingOptions().setEnterDoors(false);
        pathNavigation.getPathingOptions().setEnterGates(false);
        pathNavigation.getPathingOptions().setCanOpenDoors(false);
        pathNavigation.getPathingOptions().withDropCost(1D);
        pathNavigation.getPathingOptions().withJumpCost(1D);
        pathNavigation.getPathingOptions().setPassDanger(false);
        pathNavigation.getPathingOptions().setCanSwim(true);
        pathNavigation.getPathingOptions().setCanClimbAdvanced(false);
        pathNavigation.setCanFloat(true);

        return pathNavigation;
    }

    /** 
     * Allow breeding even when not tamed. Keep vanilla species rules. 
     * 
     */
    @Override
    public boolean canMate(@NotNull Animal other)
    {
        if (other == this) return false;
        if (!(other instanceof AbstractHorse)) return false;
        if (this.isBaby() || other.isBaby()) return false;

        // vanilla enforces isTamed() here; we intentionally skip that
        if (other instanceof Mule) return false;
        if ((this instanceof Horse && (other instanceof Horse || other instanceof Donkey)))
        {
            return true;
        }
        return false;
    }

    /**
     * Called every tick to update the horse. 
     */
    @Override
    public void tick()
    {
        super.tick();

        if (level().isClientSide()) return;

        if (hasReservation())
        {
            if (reservationExpiration > RESERVATION_EXPIRATION_LIMIT) 
            {
                clearReservation(); 
                reservationExpiration = 0;
            } 
            else
            {
                reservationExpiration++;
            }
        }

        if (!isReadyForCombat())
        {
            this.getPassengers().forEach(Entity::stopRiding);
        }

        Entity rider = this.getControllingPassenger();
        if (rider instanceof EntityCitizen cavunit)
        {
            final float horseYaw = this.getYRot();
            final float alignedYaw = approachYaw(cavunit.getYRot(), horseYaw, RIDER_ALIGN_MAX_STEP_DEGREES);
            cavunit.setYRot(alignedYaw);
            cavunit.setYBodyRot(alignedYaw);

            // The rider's navigator, not the horse's: while a citizen is aboard, the ride's orders live there and
            // nowhere else, and this horse's own navigator is empty for the whole ride.
            //
            // The mount is steered, and it is steered through vanilla's own plumbing rather than through anything
            // this mod writes. Mob#getControllingPassenger returns a Mob passenger, so the citizen becomes this
            // horse's controller; Mob#getMoveControl then answers with *this horse's* MoveControl when the citizen
            // is asked for its own. So when the guard AI calls EntityNavigationUtils#walkToPos, the citizen's
            // MinecoloniesAdvancedPathNavigate computes the path and follows it by pushing wanted positions into
            // the horse's MovementHandler, and the horse walks the guard's route. Mob#updateControlFlags takes the
            // MOVE, JUMP and LOOK flags off this horse's goalSelector for the same reason, which is what stands
            // CavalryStrollGoal and ReturnToStableGoal down while it is ridden. Nothing ever calls moveTo on the
            // horse's navigator, so getPath() on it is null for every tick of every ride.
            //
            // Reading it here therefore made both things below dead code: the rider never turned his head to the
            // route, which is precisely what a mounted guard who "just sits there and does not steer" looks like,
            // and the pre-emptive dismount before a ladder never fired. Note that AbstractEntityCitizen overrides
            // Mob#getNavigation and so does *not* inherit the matching redirect to the vehicle's navigator - which
            // is why the two navigators are different objects and why the horse's is the wrong one to ask.
            final AbstractAdvancedPathNavigate nav = cavunit.getNavigation();

            Path path = nav.getPath();

            if (path != null && !path.isDone())
            {
                BlockPos next = path.getNextNodePos();
                if (next != null)
                {
                    final double targetX = next.getX() + 0.5D;
                    final double targetZ = next.getZ() + 0.5D;
                    final double dx = targetX - cavunit.getX();
                    final double dz = targetZ - cavunit.getZ();

                    // Ignore near-vertical node transitions to avoid yaw jitter/spin while climbing/descending.
                    if ((dx * dx + dz * dz) > LOOK_AT_HORIZONTAL_EPSILON)
                    {
                        final BlockPos lookAt = next.above();
                        final LookHandler lookHandler = (LookHandler) cavunit.getLookControl();
                        lookHandler.setLookAt(lookAt.getX(), lookAt.getY(), lookAt.getZ());
                        lookHandler.setLookAtCooldown(40);
                    }
                }

                // If our upcoming path includes a ladder, force a dismount
                if (upcomingPathRequiresClimbing(path))
                {
                    cavunit.stopRiding();
                    nav.stop();
                    return;
                }
            }
        }
    }

    /**
     * Moves the horse for one tick.
     *
     * Vanilla splits this two ways (LivingEntity#aiStep): a horse whose controlling passenger is a *Player*
     * is moved by travelRidden, with the player's raw input and the horse's MOVEMENT_SPEED, and gallops;
     * everything else - including a horse under a citizen, because a citizen is a Mob - lands here with the
     * input MoveControl wrote this tick, which is the quadratic slow path described on
     * {@link #CITIZEN_RIDE_INPUT}. This override does for a citizen what travelRidden does for a player:
     * it keeps the direction the MoveControl asked for and replaces only the *magnitude* of the input.
     *
     * It cannot fight the MoveControl, because it never writes to the MoveControl's outputs. The order
     * inside one tick is: MoveControl#tick (Mob#serverAiStep) sets speed/zza/xxa, then this runs and
     * rescales a local copy of them on its way into super. speed, zza and xxa are left exactly as the
     * MoveControl left them, so the next tick's MoveControl reads back what it itself wrote and nothing
     * accumulates. Slow-downs still work: the MoveControl expresses them by lowering the speed modifier,
     * which lowers getSpeed(), which this scales - a 0.6 modifier is still 60 % of the speed, only now
     * linearly rather than 36 %.
     *
     * Riderless horses and player-ridden horses are untouched: the first fails the instanceof and gets the
     * input verbatim, the second never reaches this method at all.
     */
    @Override
    public void travel(@NotNull final Vec3 input)
    {
        super.travel(citizenRiddenInput(input));
    }

    /**
     * Rescales the MoveControl's input vector to the magnitude a citizen rider should produce.
     *
     * Displacement per tick is getFrictionInfluencedSpeed(...) * |input| (Entity#getInputVector), and on
     * ordinary ground that first factor is getSpeed(). So asking for an acceleration of {@code wanted}
     * means asking for |input| = wanted / getSpeed(). That quotient is at most CITIZEN_RIDE_INPUT by
     * construction, so the rescaled vector is never longer than 1 and never trips getInputVector's
     * normalisation - the same regime a player's input lives in.
     *
     * @param input the input vector the MoveControl produced for this tick
     * @return the input to actually travel with
     */
    private Vec3 citizenRiddenInput(final Vec3 input)
    {
        if (!(getControllingPassenger() instanceof EntityCitizen))
        {
            return input;
        }

        final double lengthSqr = input.x * input.x + input.z * input.z;
        final float speed = getSpeed();

        // Standing still, or being pushed around with no drive of its own: leave the tick alone entirely.
        if (lengthSqr < 1.0E-7D || speed < 1.0E-5F)
        {
            return input;
        }

        final double wanted = Math.min(speed * CITIZEN_RIDE_INPUT, CITIZEN_RIDE_MAX_ACCELERATION);
        final double scale = wanted / (speed * Math.sqrt(lengthSqr));

        return new Vec3(input.x * scale, input.y, input.z * scale);
    }

    /**
     * Smoothly approaches a target yaw, clamping each update step.
     */
    private static float approachYaw(final float currentYaw, final float targetYaw, final float maxStep)
    {
        final float delta = Mth.wrapDegrees(targetYaw - currentYaw);
        return currentYaw + Mth.clamp(delta, -maxStep, maxStep);
    }

    /**
     * Returns true if the upcoming path includes ladder climbing.
     */
    private boolean upcomingPathRequiresClimbing(@Nullable final Path path)
    {
        if (path == null || path.isDone())
        {
            return false;
        }

        final int start = path.getNextNodeIndex();
        final int end = Math.min(path.getNodeCount() - 1, start + CLIMB_LOOKAHEAD_NODES);

        for (int i = start; i <= end; i++)
        {
            final Node node = path.getNode(i);

            if (node instanceof PathPointExtended extended && extended.isOnLadder())
            {
                return true;
            }

            // Fallback for non-extended nodes.
            if (level().getBlockState(new BlockPos(node.x, node.y, node.z)).getBlock() instanceof LadderBlock)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Logs all active goals for this horse, including both the wrapped goal selector goals
     * and the target selector goals. This is useful for debugging purposes.
     * This function will only be executed every LOG_COOLDOWN_INTERVAL ticks.
     */
    public void logActiveGoals()
    {
        if (logCooldown > 0)
        {
            logCooldown--;
            return;
        }

        logCooldown = LOG_COOLDOWN_INTERVAL;

        for (WrappedGoal wrapped : this.goalSelector.getAvailableGoals())
        {
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning())
            {
                Log.getLogger().info("Active Wrapped Goal for horse {}: {}", this.getUUID(), goal.getClass().getSimpleName());
            }
        }

        for (WrappedGoal wrapped : this.targetSelector.getAvailableGoals())
        {
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning())
            {
                Log.getLogger().info("Active Target Goal for horse {}: {}", this.getUUID(), goal.getClass().getSimpleName());
            }
        }
    }

    /**
     * Creates a new CavalryHorseEntity from a vanilla AbstractHorse, attempting to preserve as much information as possible.
     * 
     * @param level   the level to spawn the new entity in
     * @param vanilla the vanilla horse to convert
     * @return the new CavalryHorseEntity, or null if the conversion failed
     */
    public static CavalryHorseEntity createFromVanilla(IColony colony, Level level, AbstractHorse vanilla)
    {
        if (level.isClientSide()) return null;

        // If already a CavalryHorseEntity, return it
        if (vanilla instanceof CavalryHorseEntity) return (CavalryHorseEntity) vanilla;

        // If not a living vanilla horse, return null
        if (vanilla == null || !vanilla.isAlive() || vanilla.isVehicle()) return null;

        // --- Snapshot generic AbstractHorse state ---
        final boolean wasTamed = vanilla.isTamed();
        final LivingEntity owner = vanilla.getOwner();
        final int temper = vanilla.getTemper();
        final double health = vanilla.getHealth();
        final String customName = vanilla.hasCustomName() ? vanilla.getName().getString() : null;

        AttributeInstance healthAttr = vanilla.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance speedAttr = vanilla.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jumpAttr = vanilla.getAttribute(Attributes.JUMP_STRENGTH);

        // TODO: Create research that improves the capability of CavalryHorses
        double maxHealth = healthAttr != null ? healthAttr.getBaseValue() * 1.25 : 30.0D;
        double moveSpeed = speedAttr != null ? speedAttr.getBaseValue() * 1.25 : 0.25D;
        double jumpStrength = jumpAttr != null ? 0.7D : 0.7D;

        // --- Snapshot Horse-specific state (variant/armor) if applicable ---
        Variant variant = null;
        if (vanilla instanceof Horse h)
        {
            variant = h.getVariant();
        }

        // Leash (if any)
        Entity leashHolder = vanilla.getLeashHolder();

        // Convert to CavalryHorseEntity
        CavalryHorseEntity cav = vanilla.convertTo(ModEntities.CAVALRY_HORSE,
          ConversionParams.single(vanilla, true, false),
          EntitySpawnReason.CONVERSION,
          converted -> { });
        if (cav == null) return null;

        IAnimalData animalData = colony.getAnimalManager().createAndRegisterAnimalData(cav);
        cav.setAnimalData(animalData);
        cav.setColonyId(colony.getID());

        // Re-apply attributes & health
        AttributeInstance cavHealthAttr = cav.getAttribute(Attributes.MAX_HEALTH);
        if (cavHealthAttr != null)
        {
            cavHealthAttr.setBaseValue(maxHealth);
        }

        AttributeInstance cavSpeedAttr = cav.getAttribute(Attributes.MOVEMENT_SPEED);
        if (cavSpeedAttr != null)
        {
            cavSpeedAttr.setBaseValue(moveSpeed);
        }

        AttributeInstance cavJumpAttr = cav.getAttribute(Attributes.JUMP_STRENGTH);
        if (cavJumpAttr != null)
        {
            cavJumpAttr.setBaseValue(jumpStrength);
        }

        final AttributeInstance step = cav.getAttribute(Attributes.STEP_HEIGHT);
        if (step != null)
        {
            step.setBaseValue(1.1D);
        }

        cav.setHealth((float) Math.min(health, maxHealth));

        // Re-apply AbstractHorse state
        cav.setTamed(wasTamed);
        cav.setOwner(owner);
        cav.setTemper(temper);
        cav.setPersistenceRequired();

        // Re-apply Horse-specific visuals
        if (variant != null)
        {
            // 26.2 made Horse#setVariant private; the public route is the HORSE_VARIANT component.
            cav.setComponent(net.minecraft.core.component.DataComponents.HORSE_VARIANT, variant);
        }

        // Name & leash
        if (customName != null)
        {
            cav.setCustomName(Component.literal(customName));
        }

        if (leashHolder != null)
        {
            cav.setLeashedTo(leashHolder, true);
        }

        return cav;
    }

    /**
     * Whether this entity should be saved to disk.
     * <p>As CavalryHorse entities are always saved to disk, this method always returns true.
     */
    @Override
    public boolean shouldBeSaved()
    {
        return true;
    }

    /**
     * Applies damage to this entity.
     * A portion of that damage is also applied to the combat cooldown counter.
     *
     * @param damageSource the source of the damage
     * @param damageAmount the amount of damage to apply
     * @return true if the damage was applied, false otherwise
     */
    @Override
    public boolean hurtServer(@NotNull net.minecraft.server.level.ServerLevel serverLevel, @NotNull DamageSource damageSource, float damageAmount)
    {
       
        if (level().isClientSide())
        {
            return true;
        }

        // Percentage of damage applied to combat cooldown
        float cooldownImpact = .40f;

        if (damageSource.is(DamageTypeTags.IS_EXPLOSION) && damageSource.getEntity() instanceof Creeper) 
        {
            // TODO: Introduce research to improve explosion damage mitigation.
            damageAmount *= 0.30f;
        }

        if (damageSource.is(DamageSourceKeys.STUCK_DAMAGE))
        {
            damageAmount *= 0.0f;
        }

        // TODO: Create research that provides combat cooldown mitigation
        float combatCooldown = animalData.getCombatCooldown() + (damageAmount * cooldownImpact);

        animalData.setCombatCooldown(combatCooldown);
        animalData.markDirty();

        return super.hurtServer(serverLevel, damageSource, damageAmount);
    }

    /**
     * Adds additional data to the CompoundTag that is specific to this entity type. This data is saved to disk and can be read back in
     * when the entity is loaded. The data added is as follows: - stablePos: The position of the stable block, or null if not set. -
     * stableDim: The dimension of the stable block, or null if not set.
     */
    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput tag)
    {
        super.addAdditionalSaveData(tag);

        tag.putInt(NbtTagConstants.TAG_COLONY_ID, animalColonyHandler.getColonyId());
        if (animalData != null)
        {
            tag.putInt(NbtTagConstants.TAG_MANAGED_ANIMALID, getManagedAnimalId());
        }
    }

    /**
     * Reads additional data from the given CompoundTag that is specific to this entity type.
     * <p>
     * This method is called when the entity is loaded from disk, and the data read is used to initialize the entity.
     * <p>
     * The data that is read is as follows:
     * - TAG_COLONY_ID: The id of the colony that the entity is associated with.
     * - TAG_MANAGED_ANIMALID: The id of the managed animal data that is associated with the entity.
     * <p>
     * If the TAG_MANAGED_ANIMALID is not present, then a new managed animal data is created and associated with the entity.
     * Other persisted data is managed through the associated IAnimalData.
     */
    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput tag)
    {
        super.readAdditionalSaveData(tag);

        final java.util.Optional<Integer> storedColonyId = tag.getInt(NbtTagConstants.TAG_COLONY_ID);
        if (storedColonyId.isPresent())
        {
            int colonyId = storedColonyId.get();
            setColonyId(colonyId);

            final java.util.Optional<Integer> storedAnimalId = tag.getInt(NbtTagConstants.TAG_MANAGED_ANIMALID);
            if (storedAnimalId.isPresent())
            {
                setManagedAnimalId(storedAnimalId.get());
            }
            else
            {
                IColony colony = animalColonyHandler.getColony();

                if (colony != null)
                {
                    animalData = colony.getAnimalManager().createAndRegisterAnimalData(this);
                }
            }
        }
    }

    /**
     * Prepares the horse for combat by reducing its combat cooldown by the specified amount of combat readiness. 
     * This amount is subtracted from the horse's current combat cooldown.
     * 
     * @param combatReadiness the amount of combat readiness to subtract from the horse's combat cooldown.
     */
    public void prepareForCombat(float combatReadiness)
    {
        if (animalData == null) return;

        animalData.setCombatCooldown(animalData.getCombatCooldown() - Math.abs(combatReadiness));
    }

    /**
     * Returns whether the horse is ready for combat. This is calculated by seeing if the combat cooldown is less than or equal 
     * to the readiness threshold relative to the maximum health of the horse.
     * 
     * @return true if the horse is ready for combat, false otherwise.
     */
    public boolean isReadyForCombat()
    {
        if (animalData == null) return false;

        return animalData.getCombatCooldown() <= (this.getMaxHealth() * COMBAT_READINESS_THRESHOLD);
    }

    /**
     * Returns the current combat cooldown of the horse. This is the amount of time until the horse is ready for combat.
     * A higher value means the horse is currently less ready for combat.
     * 
     * @return the current combat cooldown of the horse
     */
    public float getCombatCooldown()
    {
        if (animalData == null) return 0.0f;

        return animalData.getCombatCooldown();
    }

    /**
     * A minor horizontal collision is one that occurs when the horse has moved into a solid block. This is different from a major
     * collision, which is when the horse has moved into another entity. This method is overridden to set the lastHorizontalCollision
     * field to the current game time when a minor collision occurs.
     * 
     * @param vec3 the movement vector of the horse
     * @return true if the horse moved into a solid block, false otherwise
     */
    @Override
    protected boolean isHorizontalCollisionMinor(@NotNull Vec3 vec3)
    {
        lastHorizontalCollision = level().getGameTime();
        return super.isHorizontalCollisionMinor(vec3);
    }

    /**
     * Whether the horse collided in the last 10 ticks
     *
     * @return
     */
    public boolean hadHorizontalCollission()
    {
        return level().getGameTime() - lastHorizontalCollision < 10;
    }

    /**
     * Reserves the horse for the given entity. When the horse is reserved, it will not be able to be mounted or interacted with
     * by other entities until the reservation is cleared.
     *
     * @param reserver the entity to reserve the horse for
     */
    public void reserve(@NotNull final Entity reserver) 
    { 
        if (animalData != null)
        {
            animalData.setOwner(reserver.getUUID());
            reservationExpiration = 0;
        }
        else   
        {
            Log.getLogger().warn("Missing animalData on cavalry horse while attempting to make a reservation! ");
        }

    }

    /**
     * Clears the reservation on the horse. When the reservation is cleared, 
     * the horse can once again be mounted and interacted with by other entities.
     */
    public void clearReservation()
    {
        if (animalData != null)
        {
            animalData.setOwner(null);
            reservationExpiration = 0;
        }
        else   
        {
            Log.getLogger().warn("Missing animalData on cavalry horse while attempting to clear a reservation! ");
        }
    }

    /**
     * Clears the reservation on the horse if it is reserved by the entity with the given UUID.
     *
     * @param reserver the entity to check against
     * @return true if the reservation was cleared, false otherwise
     */
    public boolean clearFor(@NotNull final Entity reserver)
    {
        if (animalData == null) return false;

        UUID who = animalData.getOwner();
        if (reserver.getUUID().equals(who))
        {
            clearReservation();
            return true;
        }

        return false;
    }


    /**
     * Returns the UUID of the entity that has reserved this horse, or null if it is not reserved.
     * 
     * @return the UUID of the entity that has reserved this horse, or null if it is not reserved.
     */
    public @Nullable UUID reservedBy()
    {
        if (animalData == null) return null;

        return animalData.getOwner();
    }

    /**
     * Checks if the horse has a reservation by another entity. If the horse has a reservation, 
     * it will not be available as a cavalry mountn to other entities until the reservation is cleared.
     *
     * @return true if the horse has a reservation, false otherwise
     */
    public boolean hasReservation()
    {
        return reservedBy() != null;
    }

    /**
     * Returns the stable building of the horse if it exists. If the horse does not have a stable block position, 
     * or if the block position is not a stable building, this method returns null.
     * 
     * @return the stable building of the horse, or null if the horse does not have one.
     */
    public IBuilding getStableBuilding()
    {
        if (animalData == null) return null;

        IBuilding building = animalData.getHomeBuilding();

        return building;
    }

    /**
     * Checks if the horse is currently within the boundaries of its stable building.
     * <p>
     * If the horse does not have a stable, returns false.
     * </p>
     * <p>
     * If the horse is currently within the boundaries of its stable building, returns true. Otherwise, returns false.
     * </p>
     * @return true if the horse is within its stable building, false otherwise
     */
    public boolean isInStable()
    {
        IBuilding stable = getStableBuilding();
        if (!(stable instanceof BuildingStable)) return false;
        
        if (stable.isInBuilding(this.blockPosition())) 
        {
            return true;
        }

        return false;
    }

    /**
     * Returns the animal data associated with this horse.
     *
     * @return the animal data associated with this horse.
     */
    @Override
    public IAnimalData getAnimalData()
    {
        return animalData;
    }

    /**
     * Returns the animal data view associated with this horse.
     *
     * @return the animal data view associated with this horse.
     */
    @Override
    public IAnimalDataView getAnimalDataView()
    {
        return animalDataView;
    }

    /**
     * Sets the animal data associated with this horse.
     *
     * @param data The animal data associated with this horse.
     */
    @Override
    public void setAnimalData(IAnimalData data)
    {
        if (data == null) 
        {
            return;
        }

        this.animalData = data;
    }

    /**
     * Returns the entity itself. This is used in the IManagedAnimal interface to get the entity associated with the managed animal.
     * <p>
     * This method is used to get the entity associated with the managed animal, which in this case is the horse entity.
     * <p>
     * @return the horse entity associated with the managed animal.
     */
    @Override
    public CavalryHorseEntity getEntity()
    {
        return this;
    }
}
