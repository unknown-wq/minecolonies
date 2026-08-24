package com.minecolonies.core.entity.ai.workers.production.agriculture;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.loot.ModLootTables;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.Pond.PondState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFisherman;
import com.minecolonies.core.colony.interactionhandling.PosBasedInteraction;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.jobs.JobFisherman;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAISkill;
import com.minecolonies.api.entity.other.MinecoloniesBoat;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.NewBobberEntity;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.Pathfinding;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import com.minecolonies.core.entity.pathfinding.SurfaceType;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobFindWater;
import com.minecolonies.core.entity.pathfinding.pathresults.WaterPathResult;
import com.minecolonies.core.util.WorkerUtil;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.HALF_BLOCK;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.StatisticsConstants.FISH_CAUGHT;
import static com.minecolonies.api.util.constant.TranslationConstants.SUBOPTIMAL_POND;
import static com.minecolonies.api.util.constant.TranslationConstants.WATER_TOO_FAR;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE;
import static com.minecolonies.core.entity.other.NewBobberEntity.XP_PER_CATCH;

/**
 * Fisherman AI class.
 * <p>
 * A fisherman has some ponds where he randomly selects one and fishes there.
 * <p>
 * To keep it immersive he chooses his place at random around the pond.
 */
public class EntityAIWorkFisherman extends AbstractEntityAISkill<JobFisherman, BuildingFisherman>
{
    /**
     * The render name to render fish.
     */
    public static final String RENDER_META_FISH = "fish";

    /**
     * The render name to render rod.
     */
    public static final String RENDER_META_ROD = "rod";

    /**
     * How close to a suboptimal pond the fisherman has to be before he complains.
     */
    public static final int SUBOPTIMAL_POND_COMPLAINT_DISTANCE = 12;

    /**
     * The maximum number of ponds to remember at one time.
     */
    private static final int MAX_PONDS = 20;

    /**
     * Base chance to fail an action
     */
    private static final int FISHING_SKILL_CHANCE = 10;

    /**
     * The chance the fisherman has to throw his rod. Directly connected with delay.
     */
    private static final int CHANCE = 2;

    /**
     * The minimum distance in blocks to the water which is required for the fisherman to throw his rod.
     */
    private static final int MIN_DISTANCE_TO_WATER = 3;

    /**
     * The amount of catches until the fisherman empties his inventory.
     */
    private static final int MAX_FISHES_IN_INV = 10;

    /**
     * The maximum amount of adjusts of his rotation until the fisherman discards a fishing location.
     */
    private static final int MAX_ROTATIONS = 6;

    /**
     * The range in which the fisherman searches water.
     */
    private static final int SEARCH_RANGE = 50;

    /**
     * The percentage of times where the fisherman will check out a new pond.
     */
    private static final double CHANCE_NEW_POND = 0.05D;

    /**
     * How long the fisherman waits before searching for water again after a search that found nothing, indexed by how
     * many searches in a row have now failed. The last entry is the ceiling. Reset the moment a pond is found.
     */
    private static final int[] WATER_SEARCH_BACKOFF_SECONDS = {5, 10, 20, 60};

    /**
     * Time out fo fish again.
     */
    private static final int FISHING_TIMEOUT = 5;

    /**
     * Per level lure speed.
     */
    private static final int LURE_SPEED_DIVIDER = 25;

    /**
     * How many searches in a row have to fail before the fisherman says so in chat.
     */
    private static final int COMPLAIN_AFTER_FAILED_SEARCHES = 3;

    /**
     * How long a boat is asked for at a time, in ticks.
     * <p>
     * Three seconds against an AI that ticks once a second: long enough that a missed tick or a state that ticks
     * slower never drops the boat under a working fisherman, short enough that a fisherman who stops being one is
     * out of the hull before a player would notice him sitting in it.
     */
    private static final int BOAT_KEEP_TICKS = 3 * TICKS_SECOND;

    /**
     * How far past the pond a fisherman in a boat aims, in blocks. See {@link #fishingAim()}.
     */
    private static final int BOAT_AIM_DISTANCE = 4;

    /**
     * How near the spot a fisherman has to be before he takes his seat, in blocks.
     * <p>
     * Four, because that is the distance {@code walkToSafePos} treats as arrival. See {@link #hasArrivedAtTheSpot()}.
     */
    private static final int BOAT_BOARDING_REACH = 4;

    /**
     * How far from the pond node a fisherman may take his seat to keep out of a colleague's way, in blocks.
     * <p>
     * Six, because {@code PathJobFindWater} only ever hands back one node per pond and a hull floats on any water
     * surface: the pond is as wide as the search says it is, and a seat six blocks along it is the same pond. Wider
     * than that and the seat starts to be a different piece of water than the one that was proven walkable-to.
     */
    private static final int SPREAD_RADIUS = 6;

    /**
     * How far along the bank a fisherman may slide to keep out of a colleague's way, in blocks.
     * <p>
     * The same reach as {@link #SPREAD_RADIUS}, but it buys much less: open water is free in two dimensions and a
     * bank is free in one, so five fishermen on one bank end up a few blocks apart where five in boats end up a
     * {@link #SPREAD_DISTANCE} apart. Measured at three: with a slide of three the fourth man on the bank could only
     * get one block clear. See {@link #freeSpotOnBank}.
     */
    private static final int BANK_SLIDE = 6;

    /**
     * How far apart two fishermen out of the same hut keep their spots, in blocks, measured horizontally.
     */
    private static final int SPREAD_DISTANCE = 4;

    /**
     * The four ways along the ground, kept once rather than allocated per candidate.
     */
    private static final Direction[] HORIZONTAL_STEPS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /**
     * The number of executed adjusts of the fisherman's rotation.
     */
    private int executedRotations = 0;

    /**
     * How many water searches in a row have come back empty. Indexes {@link #WATER_SEARCH_BACKOFF_SECONDS}.
     */
    private int failedWaterSearches = 0;

    /**
     * Whether the fisherman is out looking for a pond he does not know yet, rather than picking one he does.
     * <p>
     * Only the {@link #CHANCE_NEW_POND} branch of a successful catch sets this. Without it every trip through the
     * water search re-ran the long-range hunt for an unknown pond, which cannot succeed while the only pond nearby
     * is one he already remembers -- {@code PathJobFindWater} rejects anything within 7 blocks of a known pond --
     * and fails at the full node ceiling while doing so.
     */
    private boolean lookingForNewPond = false;

    /**
     * Whether the backoff below has already been waited out, so the next visit is the one that starts the search.
     * <p>
     * The wait comes first and the search second on purpose. The other way round -- search, then wait -- means the
     * result is read a whole backoff period after the search that produced it, so a pond that appears right after a
     * search starts is only noticed two periods later; that was measured at 88-118 seconds against the 60 the
     * ladder is meant to cost.
     */
    private boolean waterSearchWaited = false;

    /**
     * The PathResult when the fisherman searches water.
     */
    @Nullable
    private WaterPathResult pathResult;

    /**
     * The Previous PathResult when the fisherman already found water.
     */
    @Nullable
    private WaterPathResult lastPathResult;

    /**
     * Connects the citizen with the fishingHook.
     */
    @Nullable
    private NewBobberEntity entityFishHook;

    /**
     * Hook stuck counter. Don't immediately retrieve until a bit of time passed.
     */
    private int stuckCounter = 3;

    /**
     * Constructor for the Fisherman. Defines the tasks the fisherman executes.
     *
     * @param job a fisherman job to use.
     */
    public EntityAIWorkFisherman(@NotNull final JobFisherman job)
    {
        super(job);
        super.registerTargets(
          new AITarget(IDLE, START_WORKING, 1),
          new AITarget(START_WORKING, this::startWorkingAtOwnBuilding, TICKS_SECOND),
          new AITarget(PREPARING, this::prepareForFishing, TICKS_SECOND),
          new AITarget(FISHERMAN_CHECK_WATER, this::tryDifferentAngles, 1),
          new AITarget(FISHERMAN_SEARCHING_WATER, this::findWater, TICKS_SECOND),
          new AITarget(FISHERMAN_WALKING_TO_WATER, this::getToWater, TICKS_SECOND),
          new AITarget(FISHERMAN_START_FISHING, this::doFishing, TICKS_SECOND)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingFisherman> getExpectedBuildingClass()
    {
        return BuildingFisherman.class;
    }

    /**
     * Redirects the fisherman to his building.
     *
     * @return the next state.
     */
    private IAIState startWorkingAtOwnBuilding()
    {
        if (!walkToBuilding())
        {
            return getState();
        }
        return PREPARING;
    }

    /**
     * Prepares the fisherman for fishing and requests fishingRod and checks if the fisherman already had found a pond.
     *
     * @return the next IAIState
     */
    private IAIState prepareForFishing()
    {
        if (checkForToolOrWeapon(ModEquipmentTypes.fishing_rod.get()))
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStackUtils.EMPTY);
            playNeedRodSound();
            return getState();
        }

        return FISHERMAN_WALKING_TO_WATER;
    }

    /**
     * Plays a sound when the fisherman needs a rod.
     */
    private void playNeedRodSound()
    {
        SoundUtils.playSoundAtCitizenWith(world, worker.blockPosition(), EventType.MISSING_EQUIPMENT, worker.getCitizenData());
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return MAX_FISHES_IN_INV;
    }

    /**
     * Here the AI can check if the fishes or rods have to be re rendered and do it.
     */
    @Override
    protected void updateRenderMetaData()
    {
        if (hasFish() && hasRodButNotEquipped())
        {
            worker.setRenderMetadata(RENDER_META_ROD + RENDER_META_FISH);
        }
        else if (hasRodButNotEquipped() && !hasFish())
        {
            worker.setRenderMetadata(RENDER_META_ROD);
        }
        else
        {
            worker.setRenderMetadata(hasFish() ? RENDER_META_FISH : "");
        }
    }

    /**
     * Checks if the fisherman has fish in his inventory.
     *
     * @return true if so.
     */
    private boolean hasFish()
    {
        return InventoryUtils.hasItemInItemHandler(getInventory(), item -> item.is(ItemTags.FISHES));
    }

    /**
     * Checks if the fisherman has a rod in his inventory but if he did not equip it.
     *
     * @return true if so.
     */
    private boolean hasRodButNotEquipped()
    {
        return InventoryUtils.hasItemHandlerEquipmentWithLevel(getInventory(), ModEquipmentTypes.fishing_rod.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel())
                 && worker.getMainHandItem() != null
                 && !ModEquipmentTypes.fishing_rod.get().checkIsEquipment(worker.getMainHandItem());
    }

    /**
     * If the job class has no water object the fisherman should search water.
     *
     * @return the next IAIState the fisherman should switch to, after executing this method.
     */
    private IAIState getToWater()
    {
        if (job.getWater() == null)
        {
            return FISHERMAN_SEARCHING_WATER;
        }

        keepTheBoat();

        if (!walkToWater())
        {
            return getState();
        } 
        else 
        {
            /*
             * Upon arrival at the pond the fisherman checks if the pond is suboptimal, complains about it if so,
             * and looks for different water.
             */
            if (lastPathResult != null && lastPathResult.pondState == PondState.SUBOPTIMAL) 
            {
                worker.getCitizenData().triggerInteraction(new PosBasedInteraction(
                    Component.translatable(SUBOPTIMAL_POND, lastPathResult.pond.getX(), lastPathResult.pond.getY(), lastPathResult.pond.getZ()),
                    ChatPriority.IMPORTANT,
                    Component.translatable(SUBOPTIMAL_POND),
                    lastPathResult.pond));

                return FISHERMAN_SEARCHING_WATER;
            }
        }

        return FISHERMAN_CHECK_WATER;
    }

    /**
     * Let's the fisherman walk to the water if the water object in his job class already has been filled.
     *
     * @return true if the fisherman has arrived at the water.
     */
    private boolean walkToWater()
    {
        return job.getWater() != null && walkToSafePos(job.getWater().getB());
    }

    /**
     * Rotates the fisherman to guarantee that the fisherman throws his rod in the correct direction.
     *
     * @return the next IAIState the fisherman should switch to, after executing this method.
     */
    @NotNull
    private IAIState tryDifferentAngles()
    {
        if (job.getWater() == null)
        {
            return FISHERMAN_SEARCHING_WATER;
        }
        if (executedRotations >= MAX_ROTATIONS)
        {
            forgetCurrentPond();
            job.setWater(null);
            executedRotations = 0;
            return FISHERMAN_SEARCHING_WATER;
        }

        if (isBoatSpot())
        {
            // Standing in water is the whole point of this spot, so none of the emergency below applies to it. Left
            // as it was, that branch ends a boat trip before it starts: a seated citizen's blockPosition is the water
            // block -- the hull floats 0.5 into it and MinecoloniesBoat seats its passenger 0.41 below the hull -- so
            // the liquid test is always true, and the block under a boat spot is water and therefore never solid, so
            // the first approach would throw the pond away and walk off.
            if (!hasArrivedAtTheSpot())
            {
                return FISHERMAN_WALKING_TO_WATER;
            }
            boardTheBoat();
        }
        else if (world.getBlockState(worker.blockPosition()).liquid())
        {
            if (!BlockUtils.isAnySolid(world.getBlockState(job.getWater().getB().below())))
            {
                forgetCurrentPond();
                job.setWater(null);
                executedRotations = 0;
                return START_WORKING;
            }
            else
            {
                executedRotations++;
            }
        }

        //Try a different angle to throw the hook not that far
        WorkerUtil.faceBlock(fishingAim(), worker);
        executedRotations++;
        return FISHERMAN_START_FISHING;
    }

    /**
     * Whether the spot the fisherman is fishing from is on the water rather than on a bank.
     * <p>
     * A pond he remembers is a pair of (water, place to fish from). {@code PathJobFindWater} normally fills the
     * second in with a bank; when it cannot find one it puts the pond itself there instead, and that pair is a boat
     * spot. Recognised by reading the world rather than by a flag on the job, so it needs nothing added to the NBT
     * and a pond remembered by an older save is read correctly either way.
     * <p>
     * The BOATS research is asked here as well as in the search, so a colony that somehow loses the research stops
     * treating its remembered boat ponds as boat ponds: the ordinary liquid branch of {@link #tryDifferentAngles()}
     * then drops such a pond on the first approach and the fisherman goes and finds himself a bank.
     *
     * @return true if this is a spot on the water.
     */
    private boolean isBoatSpot()
    {
        final Tuple<BlockPos, BlockPos> water = job.getWater();
        return water != null
                 && canUseBoat()
                 && PathfindingUtils.isWater(world, water.getB().below());
    }

    /**
     * @return true if this citizen's colony has finished the BOATS research.
     */
    private boolean canUseBoat()
    {
        return worker.getNavigation().getPathingOptions().canUseBoat();
    }

    /**
     * Ask the navigator to leave the boat where it is for the next few seconds.
     * <p>
     * Called on every tick of every state that wants the boat, and never cleared. The navigator's rule is that a
     * colony boat whose passenger has no path left is a boat nobody is going to steer, so it takes the citizen out
     * and discards the hull -- correct for travelling and wrong for a fisherman, who reaches open water and then
     * deliberately stops moving. Asking for a few seconds at a time and asking again is what makes every way of
     * ceasing to be a fisherman -- eating, sleeping, being unassigned, dying -- end with the boat gone, without this
     * class having to know about any of them.
     */
    private void keepTheBoat()
    {
        if (!isBoatSpot())
        {
            return;
        }

        if (worker.getVehicle() instanceof final MinecoloniesBoat ferry && !isParkedOnTheSpot(ferry))
        {
            // A hull still on its way there is not the one he fishes from. Mooring it anchors it where it stands, and
            // the navigator then reports the crossing as held up and puts him ashore in the middle of the lake to swim
            // the rest: measured on the stand, a fisherman ferried out of the hut got no further than a third of the
            // way across before his own AI stopped his boat under him.
            return;
        }

        if (worker.getNavigation() instanceof final MinecoloniesAdvancedPathNavigate navigate)
        {
            navigate.keepBoat(BOAT_KEEP_TICKS);
        }

        if (worker.getVehicle() instanceof final MinecoloniesBoat boat)
        {
            boat.moor(world.getGameTime() + BOAT_KEEP_TICKS);
        }
    }

    /**
     * Whether the fisherman is at his boat spot and may take his seat.
     * <p>
     * A boat is placed where the spot is and mounting one teleports its passenger to it, so boarding is a way of
     * getting somewhere as well as a way of sitting down: without this the fisherman vanished from his hut door and
     * reappeared in the middle of the lake the instant he picked a remembered pond, which is every time he starts
     * work. Measured on the stand: five fishermen crossing twenty blocks in the same second, once each on waking.
     * <p>
     * The distance is the one {@code walkToSafePos} counts as having arrived, and it has to stay that way: a stricter
     * test here than the walk's own would put the two states in a loop, {@link #getToWater()} reporting him arrived
     * and this one sending him back to walk, for the rest of the day and without a fish.
     *
     * @return true if he has got there.
     */
    private boolean hasArrivedAtTheSpot()
    {
        if (worker.getVehicle() instanceof final MinecoloniesBoat boat && isParkedOnTheSpot(boat))
        {
            return true;
        }

        if (BlockPosUtil.dist(worker.blockPosition(), job.getWater().getB()) <= BOAT_BOARDING_REACH)
        {
            return true;
        }

        walkToWater();
        return false;
    }

    /**
     * Whether this hull is the one parked on the fishing spot rather than one still carrying him somewhere.
     *
     * @param boat the hull he is riding.
     * @return true if it is floating on the fishing spot.
     */
    private boolean isParkedOnTheSpot(final MinecoloniesBoat boat)
    {
        final BlockPos spot = job.getWater().getB();
        return Math.abs(boat.getX() - (spot.getX() + HALF_BLOCK)) <= 1.0D
                 && Math.abs(boat.getZ() - (spot.getZ() + HALF_BLOCK)) <= 1.0D;
    }

    /**
     * Put the fisherman in a boat on his fishing spot, if he is not in one already.
     * <p>
     * He often is: a path that ends in water gives the navigator a boat leg and it places a hull and mounts him for
     * the crossing, and {@link #keepTheBoat()} is what stops that hull being taken away again when the path ends. The
     * spawn below is for the other arrival, the one where the water was close enough to swim to and no leg was
     * needed. It is the navigator's own placement, for the same reasons given there: dropped in from the block above
     * the surface so vanilla buoyancy seats it rather than starting it UNDER_WATER, and claimed before it enters the
     * world so nothing else can take the seat first.
     * <p>
     * No boat item and no request: the hull is conjured exactly as a crossing's is, so a fisherman needs nothing in
     * his inventory to fish from one and the colony is never asked to make him a boat.
     */
    private void boardTheBoat()
    {
        keepTheBoat();

        if (worker.getVehicle() != null)
        {
            return;
        }

        final BlockPos spot = job.getWater().getB();
        final MinecoloniesBoat boat = ModEntities.BOAT.create(world, EntitySpawnReason.MOB_SUMMONED);
        if (boat == null)
        {
            return;
        }

        final double x = spot.getX() + 0.5D;
        final double z = spot.getZ() + 0.5D;
        final double y = spot.getY() + (PathfindingUtils.isWater(world, spot) ? 1.0D : 0.0D);

        boat.setPos(x, y, z);
        boat.setDeltaMovement(Vec3.ZERO);
        boat.xo = x;
        boat.yo = y;
        boat.zo = z;
        boat.setYRot(worker.getYRot());
        boat.yRotO = boat.getYRot();
        boat.claimFor(worker);
        boat.moor(world.getGameTime() + BOAT_KEEP_TICKS);

        world.addFreshEntity(boat);
        worker.startRiding(boat, true, true);
    }

    /**
     * Where to aim the rod from.
     * <p>
     * From a bank that is the pond, which lies in front of him. From a boat he is sitting <em>on</em> the pond, and
     * facing a block he is already in makes him look straight down at his own feet: the pitch comes out of
     * {@code atan2(yDifference, 0)}. Aiming a few blocks further out from the hut keeps the cast horizontal and over
     * the water he is floating on, which is water in every direction.
     *
     * @return the block to face.
     */
    private BlockPos fishingAim()
    {
        final BlockPos pond = job.getWater().getA();
        if (!isBoatSpot())
        {
            return pond;
        }

        final BlockPos hut = building.getPosition();
        return pond.offset(Integer.signum(pond.getX() - hut.getX()) * BOAT_AIM_DISTANCE,
          0,
          Integer.signum(pond.getZ() - hut.getZ()) * BOAT_AIM_DISTANCE);
    }

    /**
     * Checks if the fisherman already has found 20 pools, if yes search a water pool out of these 20, else search a new one.
     *
     * @return the next IAIState the fisherman should switch to, after executing this method.
     */
    private IAIState findWater()
    {

        //Reset executedRotations when fisherman searches a new Pond
        executedRotations = 0;
        //If he can't find any pond, tell that to the player
        //If 20 ponds are already stored, take a random stored location
        //the fishman should not go to find water when fishman can find water but cant find pond
        if (job.getPonds().size() >= MAX_PONDS || (lastPathResult != null && lastPathResult.pond == null && job.getPonds().size() > 0))
        {
            return setRandomWater();
        }

        // A pond he already knows is the cheap answer, and the right one unless he set out to find a new one:
        // hunting for an unknown pond with a known one nearby is a search that has to fail, and it fails at the
        // node ceiling.
        if (!job.getPonds().isEmpty() && !lookingForNewPond)
        {
            return setRandomWater();
        }
        return findNewWater();
    }

    /**
     * Used to find a water.
     *
     * @param range in the range.
     * @param speed walking speed.
     * @param ponds a list of ponds.
     * @return the result of the search.
     */
    public WaterPathResult searchWater(final int range, final double speed, final List<Tuple<BlockPos, BlockPos>> ponds)
    {
        @NotNull final BlockPos start = PathfindingUtils.prepareStart(worker);
        final PathJobFindWater job = new PathJobFindWater(CompatibilityUtils.getWorldFromEntity(worker),
          start,
          worker.getCitizenColonyHandler().getWorkBuilding().getPosition(),
          range,
          ponds,
          worker);
        job.setPathingOptions(worker.getNavigation().getPathingOptions());
        final WaterPathResult waterPathresult = job.getResult();
        waterPathresult.startJob(Pathfinding.getExecutor());
        return waterPathresult;
    }

    /**
     * If the fisherman can't find 20 ponds or already has found 20, the fisherman should randomly choose a fishing spot from the previously found ones.
     *
     * @return the next IAIState.
     */
    private IAIState setRandomWater()
    {
        if (job.getPonds().isEmpty())
        {
            // Say so once the search has failed often enough to mean it. The condition used to lean on
            // WaterPathResult.isEmpty, which is written nowhere in this tree, upstream's or the 1.21.1 snapshot's --
            // so the complaint could never fire again after the fisherman had found a pond even once, and freezing
            // or filling in his lake stopped him in silence. failedWaterSearches is the same signal, and it is real.
            if ((pathResult != null && pathResult.failedToReachDestination() && lastPathResult == null)
                  || failedWaterSearches >= COMPLAIN_AFTER_FAILED_SEARCHES)
            {
                if (worker.getCitizenData() != null)
                {
                    worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatableEscape(WATER_TOO_FAR), ChatPriority.IMPORTANT));
                }
            }

            // Back off before asking again. Without this the fisherman runs START_WORKING -> PREPARING ->
            // WALKING_TO_WATER -> SEARCHING_WATER -> here in a closed loop, measured at 44 turns in 168 seconds
            // without moving a block, and every turn queues a PathJobFindWater. That job is the most expensive
            // one in the pool and it is at its most expensive precisely when it fails: measured from a fisherman
            // with no reachable pond it exhausted the node budget at 8009 nodes, against 205 when a pond exists,
            // all of it on the single pathfinding thread. The complaint the player reads (WATER_TOO_FAR, just
            // above) is triggered on the same failure, so waiting longer costs the player no information.
            //
            // The wait comes first and the search after it, so the result is read on the wake-up right behind the
            // search that produced it rather than a full period later; see waterSearchWaited.
            if (!waterSearchWaited)
            {
                waterSearchWaited = true;
                setDelay(TICKS_SECOND * WATER_SEARCH_BACKOFF_SECONDS[Math.min(failedWaterSearches, WATER_SEARCH_BACKOFF_SECONDS.length - 1)]);
                return START_WORKING;
            }

            if (pathResult == null || !pathResult.isInProgress())
            {
                waterSearchWaited = false;
                pathResult = searchWater(SEARCH_RANGE, 1.0D, job.getPonds());
                failedWaterSearches++;
                setDelay(TICKS_SECOND);
            }

            return START_WORKING;
        }
        failedWaterSearches = 0;

        // Prefer a pond none of his colleagues is standing at. A second lake is a better answer than a second seat on
        // the first one, when there is a second lake. When there is not -- and a colony with one lake is the ordinary
        // case -- every pond he knows is taken, the old behaviour stands, and spreadOnPond below is what separates
        // him from the colleague already there.
        final List<Tuple<BlockPos, BlockPos>> taken = pondsOfColleagues();
        final List<Tuple<BlockPos, BlockPos>> free = job.getPonds().stream()
                                                       .filter(pond -> taken.stream().noneMatch(other -> other.getA().equals(pond.getA())))
                                                       .toList();
        final List<Tuple<BlockPos, BlockPos>> choices = free.isEmpty() ? job.getPonds() : free;

        final Tuple<BlockPos, BlockPos> pond = choices.get(worker.getRandom().nextInt(choices.size()));
        job.setWater(spreadOnPond(pond.getA(), pond.getB(), taken));
        return FISHERMAN_CHECK_WATER;
    }

    /**
     * The water every other fisherman of this hut is fishing at right now.
     * <p>
     * Read on the server thread and handed to the path job as a plain list, the same way the fisherman's own ponds
     * are: the job runs on the pathfinding thread and must not reach back into colony state.
     *
     * @return their spots, empty when he works alone.
     */
    private List<Tuple<BlockPos, BlockPos>> pondsOfColleagues()
    {
        if (building == null)
        {
            return List.of();
        }

        final List<Tuple<BlockPos, BlockPos>> spots = new ArrayList<>();
        for (final ICitizenData citizen : building.getAllAssignedCitizen())
        {
            if (citizen.getId() == worker.getCivilianID() || !(citizen.getJob() instanceof final JobFisherman other))
            {
                continue;
            }

            final Tuple<BlockPos, BlockPos> water = other.getWater();
            if (water != null)
            {
                spots.add(water);
            }
        }
        return spots;
    }

    /**
     * The spot on this pond that this fisherman takes, given who else is already on it.
     * <p>
     * The pond is not the spot. {@code PathJobFindWater} hands back one node per pond and every fisherman searching
     * from the same hut door gets the same one, so five of them out of one hut arrive at a single block: measured,
     * four of five on the same water, each conjuring his own hull, the boats 0.05 blocks apart. Excluding a pond a
     * colleague is on cannot fix that in the case that matters, because a colony with one lake has no other pond to
     * go to -- it only moves the crowd when there is a second lake, and starves the fisherman when there is not
     * (measured: on a pond too small for two spots, two fishermen shared one block and three never fished at all).
     * <p>
     * So the pond stays what it was and the <em>seat</em> moves. A is the pond, which is what identifies it to
     * everyone including his own memory; B is where this fisherman sits, and only B varies.
     *
     * @param pond  the water the search or his memory named.
     * @param spot  the spot that came with it.
     * @param taken where his colleagues are, read once by the caller: {@link #setRandomWater()} needs the same list
     *              to choose the pond, and reading it twice would be reading colony state twice for one decision.
     * @return the pond and the spot he should take, never null and never without a spot.
     */
    private Tuple<BlockPos, BlockPos> spreadOnPond(final BlockPos pond, final BlockPos spot, final List<Tuple<BlockPos, BlockPos>> taken)
    {
        if (isClearOfColleagues(spot, taken))
        {
            return new Tuple<>(pond, spot);
        }

        return new Tuple<>(pond, isBoatSeat(spot) ? freeSeatOnWater(pond, spot, taken) : freeSpotOnBank(spot, taken));
    }

    /**
     * Whether no colleague is fishing within {@link #SPREAD_DISTANCE} of this spot.
     *
     * @param spot  the spot to test.
     * @param taken where his colleagues are.
     * @return true if he may sit there.
     */
    private boolean isClearOfColleagues(final BlockPos spot, final List<Tuple<BlockPos, BlockPos>> taken)
    {
        return gapToColleagues(spot, taken) >= SPREAD_DISTANCE;
    }

    /**
     * How far this spot is from the nearest colleague, horizontally, in blocks.
     * <p>
     * Nought means somebody is on it. A small number is still worth having: two hulls a block apart are two hulls,
     * where two hulls on the same block are the thing that was reported.
     *
     * @param spot  the spot to measure.
     * @param taken where his colleagues are.
     * @return the distance, capped at {@link #SPREAD_DISTANCE} because nothing further is worth comparing.
     */
    private int gapToColleagues(final BlockPos spot, final List<Tuple<BlockPos, BlockPos>> taken)
    {
        int gap = SPREAD_DISTANCE;
        for (final Tuple<BlockPos, BlockPos> other : taken)
        {
            gap = Math.min(gap, Math.max(Math.abs(other.getB().getX() - spot.getX()), Math.abs(other.getB().getZ() - spot.getZ())));
        }
        return gap;
    }

    /**
     * A seat on this pond that nobody is at, for a pond fished from a boat.
     * <p>
     * A hull floats on any water surface, so the whole pond is available and the search's own node has no special
     * standing beyond being the one it happened to return. Candidates are walked in an order that starts at a
     * per-worker random cell, so two fishermen picking in the same second do not walk the same order; and because
     * the pick is made on the server thread and {@code setWater} is called the moment it is made, the second of the
     * two already sees the first one's seat in {@link #pondsOfColleagues()} and moves off it.
     * <p>
     * A candidate has to be a seat a boat can sit on, joined to his own spot by an unbroken run of water surface --
     * so the seat is on <em>this</em> pond rather than a puddle over the wall -- and {@link PondState#VALID}, the
     * same all-source-blocks test the search applies, because a hull moored in a current drifts.
     *
     * @param pond  the pond node.
     * @param spot  the spot he would otherwise take.
     * @param taken where his colleagues are.
     * @return a free seat, or his own spot when the pond has no room: crowded and fishing beats spread out and idle.
     */
    private BlockPos freeSeatOnWater(final BlockPos pond, final BlockPos spot, final List<Tuple<BlockPos, BlockPos>> taken)
    {
        final int span = SPREAD_RADIUS * 2 + 1;
        final int cells = span * span;
        final int first = worker.getRandom().nextInt(cells);

        // Sharing the block is the floor, never the goal: keep the best seat found so far and stop as soon as one is
        // a full SPREAD_DISTANCE clear. On a pond with no room for that, this still ends up a block or two off the
        // colleague rather than inside his hull.
        BlockPos best = spot;
        int bestGap = gapToColleagues(spot, taken);

        for (int i = 0; i < cells && bestGap < SPREAD_DISTANCE; i++)
        {
            final int cell = (first + i) % cells;
            final BlockPos candidate = new BlockPos(pond.getX() + cell % span - SPREAD_RADIUS,
              spot.getY(),
              pond.getZ() + cell / span - SPREAD_RADIUS);

            // Cheapest tests first: the gap is a handful of comparisons, the seat is three block reads, the run along
            // the surface is a few more, and checkPond is fifty. Only the candidate that has passed everything else
            // is worth asking about.
            final int gap = gapToColleagues(candidate, taken);
            if (gap <= bestGap || !isBoatSeat(candidate) || !surfaceRunsTo(spot, candidate))
            {
                continue;
            }

            if (Pond.checkPond(world, candidate.below(), null) == PondState.VALID)
            {
                best = candidate;
                bestGap = gap;
            }
        }

        return best;
    }

    /**
     * A place on the bank that nobody is at, for a pond fished from the shore.
     * <p>
     * A bank is not free ground the way open water is: it has to be walkable, it has to be beside the water, and it
     * has to be somewhere he can actually get to. That is why the search runs a nested path job for it and takes the
     * nearest one it finds. Choosing among <em>several</em> banks would mean running that job again per candidate --
     * it is a synchronous A* inside the most node-hungry job in the pool -- so this does the cheap thing instead and
     * slides along the bank he already has: a short walk over adjoining standing room, no pathfinding at all. It
     * buys a few blocks of separation, not a different shore, and it is bounded by {@link #BANK_SLIDE}.
     *
     * @param spot  the bank spot he would otherwise take.
     * @param taken where his colleagues are.
     * @return a free place on the bank, or his own spot when there is none.
     */
    private BlockPos freeSpotOnBank(final BlockPos spot, final List<Tuple<BlockPos, BlockPos>> taken)
    {
        final List<BlockPos> free = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        final Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(spot);
        seen.add(spot);

        // Sharing is the floor here too: only a spot strictly further from the nearest colleague than the one he
        // already has is worth moving to, and among equally good ones he takes his pick.
        int bestGap = gapToColleagues(spot, taken);

        while (!queue.isEmpty())
        {
            final BlockPos at = queue.poll();
            final int gap = gapToColleagues(at, taken);
            if (gap > bestGap)
            {
                bestGap = gap;
                free.clear();
                free.add(at);
            }
            else if (gap == bestGap && !free.isEmpty())
            {
                free.add(at);
            }

            for (final Direction step : HORIZONTAL_STEPS)
            {
                for (int dy = 1; dy >= -1; dy--)
                {
                    final BlockPos next = at.relative(step).above(dy);
                    if (Math.abs(next.getX() - spot.getX()) > BANK_SLIDE || Math.abs(next.getZ() - spot.getZ()) > BANK_SLIDE)
                    {
                        continue;
                    }
                    if (!seen.add(next))
                    {
                        continue;
                    }
                    if (isBankSpot(next))
                    {
                        queue.add(next);
                    }
                }
            }
        }

        return free.isEmpty() ? spot : free.get(worker.getRandom().nextInt(free.size()));
    }

    /**
     * Whether a boat may be parked here: open space with water under it and room above for the hull.
     *
     * @param pos the place to test.
     * @return true if it is a seat.
     */
    private boolean isBoatSeat(final BlockPos pos)
    {
        return PathfindingUtils.isWater(world, pos.below())
                 && world.getBlockState(pos).isAir()
                 && world.getBlockState(pos.above()).isAir();
    }

    /**
     * Whether a fisherman may stand here and cast: standing room on solid walkable ground, with water next to it so
     * {@code isReadyToFish}'s own "am I near water" test still passes.
     *
     * @param pos the place to test.
     * @return true if it is a place on the bank.
     */
    private boolean isBankSpot(final BlockPos pos)
    {
        final BlockState below = world.getBlockState(pos.below());
        if (SurfaceType.getSurfaceType(world, below, pos.below()) != SurfaceType.WALKABLE
              || !BlockUtils.isAnySolid(below)
              || !world.getBlockState(pos).isAir()
              || !world.getBlockState(pos.above()).isAir())
        {
            return false;
        }

        for (final Direction step : HORIZONTAL_STEPS)
        {
            if (PathfindingUtils.isWater(world, pos.relative(step)) || PathfindingUtils.isWater(world, pos.relative(step).below()))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an unbroken run of water surface joins these two seats, so the far one is on the same pond as the near
     * one rather than a separate pool the same distance away.
     *
     * @param from the seat he has.
     * @param to   the seat he is considering.
     * @return true if the water runs from one to the other.
     */
    private boolean surfaceRunsTo(final BlockPos from, final BlockPos to)
    {
        return legRunsTo(from, to, true) || legRunsTo(from, to, false);
    }

    /**
     * One L-shaped run between two seats, along x then z or along z then x.
     *
     * @param from        the seat he has.
     * @param to          the seat he is considering.
     * @param alongXFirst which of the two Ls to walk.
     * @return true if every block of the run is a seat.
     */
    private boolean legRunsTo(final BlockPos from, final BlockPos to, final boolean alongXFirst)
    {
        int x = from.getX();
        int z = from.getZ();

        for (int leg = 0; leg < 2; leg++)
        {
            final int endX = (leg == 0) == alongXFirst ? to.getX() : from.getX();
            final int endZ = (leg == 0) == alongXFirst ? from.getZ() : to.getZ();

            while (x != endX || z != endZ)
            {
                x += Integer.signum(endX - x);
                z += Integer.signum(endZ - z);

                if (!isBoatSeat(new BlockPos(x, from.getY(), z)))
                {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Forget the pond the fisherman is at.
     * <p>
     * By the pond rather than by the pair, because {@link #spreadOnPond} may have moved his seat off the one his
     * memory holds, and {@code removeFromPonds} matches on both halves.
     */
    private void forgetCurrentPond()
    {
        final Tuple<BlockPos, BlockPos> water = job.getWater();
        if (water == null)
        {
            return;
        }

        for (final Tuple<BlockPos, BlockPos> pond : job.getPonds())
        {
            if (pond.getA().equals(water.getA()))
            {
                job.removeFromPonds(pond);
            }
        }
    }

    /**
     * Uses the pathFinding system to search close water spots which possibilitate fishing. Sets a number of possible water pools and sets the water pool the fisherman should fish
     * now.
     *
     * @return the next IAIState the fisherman should switch to, after executing this method
     */
    private IAIState findNewWater()
    {
        if (pathResult == null)
        {
            // Three times the range only when he has nowhere at all to fish. A fisherman who is merely curious
            // about a second pond looks 50 blocks out, which is the range the rest of his searches use; the
            // long-range hunt costs up to the full 8000-node ceiling and it is not worth that for curiosity.
            // A fisherman with somewhere to fish already may be pushed off his colleagues' water and go and look for
            // a second lake -- that is the better answer when a second lake exists. A fisherman with nowhere at all
            // may not: PathJobFindWater refuses every node within seven of anything on this list, so handing it his
            // colleagues' ponds is handing it the whole of the only lake, and he then never fishes again. Measured:
            // on a pond too small to hold two spots, three of five fishermen never fished at all while the other two
            // sat on one block. The emptiness of his own list is exactly the test for "has he anywhere to fall back
            // to", because setRandomWater's fallback is that list.
            final List<Tuple<BlockPos, BlockPos>> avoid = new ArrayList<>(job.getPonds());
            if (!avoid.isEmpty())
            {
                avoid.addAll(pondsOfColleagues());
            }

            pathResult = searchWater(job.getPonds().isEmpty() ? SEARCH_RANGE * 3 : SEARCH_RANGE, 1.0D, avoid);
            return getState();
        }
        if (pathResult.failedToReachDestination())
        {
            lookingForNewPond = false;
            return setRandomWater();
        }
        if (pathResult.isPathReachingDestination())
        {
            lookingForNewPond = false;
            if (pathResult.pond != null)
            {
                failedWaterSearches = 0;
                // The seat is chosen here rather than only where the search is launched, because the exclusion list
                // a job is handed is a snapshot taken when it is queued: two searches in flight at once were both
                // told the pond was free and both came back with it. Measured -- two fishermen on one block for
                // thirty seconds. Choosing the seat as the result is consumed reads the colleagues as they are now.
                job.setWater(spreadOnPond(pathResult.pond, pathResult.parent, pondsOfColleagues()));
                job.addToPonds(pathResult.pond, pathResult.parent);
            }
            lastPathResult = pathResult;
            pathResult = null;
            return FISHERMAN_CHECK_WATER;
        }
        if (pathResult.isCancelled())
        {
            lookingForNewPond = false;
            pathResult = null;
            return PREPARING;
        }
        return getState();
    }

    /**
     * Main fishing methods, let's the fisherman gather xp orbs next to him, check if all requirements to fish are given. Actually fish, retrieve his rod if stuck or if a fish
     * bites.
     *
     * @return the next IAIState the fisherman should switch to, after executing this method.
     */
    @Nullable
    private IAIState doFishing()
    {
        if (isBoatSpot())
        {
            if (!hasArrivedAtTheSpot())
            {
                return FISHERMAN_WALKING_TO_WATER;
            }
            boardTheBoat();
        }

        @Nullable final IAIState notReadyState = isReadyToFish();
        if (notReadyState != null)
        {
            return notReadyState;
        }

        if (caughtFish())
        {
            playCaughtFishSound();
            this.incrementActionsDoneAndDecSaturation();

            // A pond that just gave up a fish is a good pond. executedRotations counts approaches that came to
            // nothing, and MAX_ROTATIONS of them makes the fisherman forget the pond entirely -- but it was only
            // ever reset when he went looking for a new one, so it counted visits and he threw away a working pond
            // after every sixth catch, walked off to search, and usually found the same water again.
            executedRotations = 0;

            if (worker.getRandom().nextDouble() < CHANCE_NEW_POND)
            {
                job.setWater(null);
                lookingForNewPond = true;
                return FISHERMAN_SEARCHING_WATER;
            }
            return FISHERMAN_WALKING_TO_WATER;
        }
        return throwOrRetrieveHook();
    }

    /**
     * Plays a sound with a chance when a fish has been caught.
     */
    private void playCaughtFishSound()
    {
        SoundUtils.playSoundAtCitizenWith(world, worker.blockPosition(), EventType.SUCCESS, worker.getCitizenData());
    }

    /**
     * Check if a hook is out there, and throw/retrieve it if needed.
     *
     * @return the next IAIState the fisherman should switch to, after executing this method
     */
    private IAIState throwOrRetrieveHook()
    {
        if (entityFishHook == null)
        {
            //Only sometimes the fisherman gets to throw its Rod (depends on intelligence)
            if (testRandomChance())
            {
                return getState();
            }
            throwRod();
        }
        else
        {
            //Check if hook landed on ground or in water, in some cases the hook bugs -> remove it after 2 minutes.
            if (isFishHookStuck())
            {
                if (stuckCounter > 3)
                {
                    stuckCounter = 0;
                    retrieveRod();
                    return FISHERMAN_WALKING_TO_WATER;
                }
                stuckCounter++;
            }
            else
            {
                stuckCounter = 0;
            }
            this.entityFishHook.setInUse();
        }
        return getState();
    }

    /**
     * Let's the fisherman face the water, play the throw sound and create the fishingHook and throw it.
     */
    private void throwRod()
    {
        if (!world.isClientSide())
        {
            WorkerUtil.faceBlock(job.getWater().getA(), worker);
            world.playSound(null,
              this.worker.blockPosition(),
              SoundEvents.FISHING_BOBBER_THROW,
              SoundSource.NEUTRAL,
              0.5F,
              (float) (0.4D / (this.world.getRandom().nextFloat() * 0.4D + 0.8D)));

            this.entityFishHook = new NewBobberEntity(ModEntities.FISHHOOK, worker, worker.level(),
              EnchantmentHelper.getFishingLuckBonus((ServerLevel) worker.level(), worker.getMainHandItem(), worker),
              (int) (5 + (getPrimarySkillLevel() / LURE_SPEED_DIVIDER) + EnchantmentHelper.getFishingTimeReduction((ServerLevel) worker.level(), worker.getMainHandItem(), worker)));

            world.addFreshEntity(this.entityFishHook);
        }

        worker.swingForAttack(worker.getUsedItemHand());
    }

    /**
     * Checks if the fishHook is stuck on land or in an entity. If the fishhook is neither in water,land nether connected with an entity, give it a time to land in water.
     *
     * @return false if the hook landed in water, else return true
     */
    private boolean isFishHookStuck()
    {
        return (!entityFishHook.isInWater() && (entityFishHook.onGround() || entityFishHook.shouldStopFishing(worker))) || !entityFishHook.isAlive()
                 || entityFishHook.getHookedEntity() != null;
    }

    /**
     * Checks how lucky the fisherman is.
     * <p>
     * This check depends on his fishing skill. Which in turn depends on intelligence.
     *
     * @return true if he has to wait.
     */
    private boolean testRandomChance()
    {
        //+1 since the level may be 0
        setDelay(FISHING_TIMEOUT);
        final double chance = worker.getRandom().nextInt(FISHING_SKILL_CHANCE + ((getSecondarySkillLevel()) / 5));
        return chance <= CHANCE;
    }

    /**
     * Checks if the fisherman has his fishingRod in his hand and is close to the water.
     *
     * @return true if fisherman meets all requirements to fish, else returns false.
     */
    private IAIState isReadyToFish()
    {
        final int rodSlot = getRodSlot();
        //We really do have our Rod in our inventory?
        if (rodSlot == -1)
        {
            if (worksWithoutTools())
            {
                // No rod, and none is going to be requested. Bouncing back to PREPARING would spin forever, so fish
                // bare handed instead: the bobber is our own entity built from the citizen, the rod only ever
                // contributed its enchantment bonuses.
                if (!Utils.isBlockInRange(world, Blocks.WATER, (int) worker.getX(), (int) worker.getY(), (int) worker.getZ(), MIN_DISTANCE_TO_WATER))
                {
                    return FISHERMAN_WALKING_TO_WATER;
                }
                return null;
            }

            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStackUtils.EMPTY);
            return PREPARING;
        }

        //If there is no close water, try to move closer
        if (!Utils.isBlockInRange(world, Blocks.WATER, (int) worker.getX(), (int) worker.getY(), (int) worker.getZ(), MIN_DISTANCE_TO_WATER))
        {
            return FISHERMAN_WALKING_TO_WATER;
        }

        //Check if Rod is held item if not put it as held item
        if (worker.getMainHandItem() == null || !ItemStackUtils.compareItemStacksIgnoreStackSize(worker.getMainHandItem(),
          worker.getItemHandlerCitizen().getStackInSlot(rodSlot),
          false,
          true))
        {
            equipRod();
            return getState();
        }
        return null;
    }

    /**
     * Sets the rod as held item.
     */
    private void equipRod()
    {
        CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, getRodSlot());
    }

    /**
     * Get's the slot in which the rod is in.
     *
     * @return slot number
     */
    private int getRodSlot()
    {
        return InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(getInventory(), ModEquipmentTypes.fishing_rod.get(),
          TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel());
    }

    /**
     * Will be called to check if the fisherman caught a fish. If the hook hasn't noticed a fish it will return false. Else the method will pick up the loot and call the method to
     * retrieve the rod.
     *
     * @return true when the fisherman caught a fish.
     */
    private boolean caughtFish()
    {
        if (entityFishHook == null)
        {
            return false;
        }
        if (!entityFishHook.isReadyToCatch())
        {
            return false;
        }

        if (testRandomChance())
        {
            return false;
        }

        worker.setCanPickUpLoot(true);
        retrieveRod();
        return true;
    }

    /**
     * Retrieves the previously thrown fishingRod. If the fishingRod still has a hook connected to it, destroy the hook object.
     */
    private void retrieveRod()
    {
        if (entityFishHook != null)
        {
            worker.swingForAttack(worker.getUsedItemHand());
            final int i = entityFishHook.retrieve(worker.getMainHandItem());
            generateBonusLoot();
            CitizenItemUtils.damageItemInHand(worker, InteractionHand.MAIN_HAND, i);
            entityFishHook = null;
        }
    }

    /**
     * Generates bonus fishing loot according to the building-level table
     */
    private void generateBonusLoot()
    {
        final LootParams context = (new LootParams.Builder((ServerLevel) this.world))
                                     .withParameter(LootContextParams.ORIGIN, entityFishHook.position())
                                     .withParameter(LootContextParams.THIS_ENTITY, entityFishHook)
                                     .withParameter(LootContextParams.TOOL, worker.getMainHandItem())
                                     // PORT(26.2): no ATTACKING_ENTITY. The FISHING parameter set is required
                                     // ORIGIN + required TOOL + optional THIS_ENTITY, and ContextMap.Builder throws
                                     // on anything else rather than ignoring it: every bonus roll threw, and the
                                     // state machine paused the fisherman for longer each time. ResearchUnlocked
                                     // read the citizen from this parameter and falls back through THIS_ENTITY (the
                                     // hook) to ORIGIN, which resolves the colony from the hook's position.
                                     .withLuck((float) getPrimarySkillLevel())
                                     .create(LootContextParamSets.FISHING);
        final LootTable bonusLoot =
          this.world.getServer().reloadableRegistries().getLootTable(ModLootTables.FISHERMAN_BONUS.get(this.building.getBuildingLevel()));
        final List<ItemStack> loot = bonusLoot.getRandomItems(context);

        for (final ItemStack itemstack : loot)
        {
            final ItemEntity itementity = new ItemEntity(this.world, entityFishHook.position().x, entityFishHook.position().y, entityFishHook.position().z, itemstack);
            final double d0 = worker.getX() - entityFishHook.position().x;
            final double d1 = (worker.getY() + 0.5D) - entityFishHook.position().y;
            final double d2 = worker.getZ() - entityFishHook.position().z;
            itementity.noPhysics = true;
            itementity.setDeltaMovement(d0 * 0.1D, d1 * 0.1D + Math.sqrt(Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2)) * 0.08D, d2 * 0.1D);
            this.world.addFreshEntity(itementity);
            worker.level().addFreshEntity(new ExperienceOrb(worker.level(),
              worker.getX(),
              worker.getY() + 0.5D,
              worker.getZ() + 0.5D,
              XP_PER_CATCH));
        }
    }

    /**
     * Returns the fisherman's worker instance. Called from outside this class.
     *
     * @return citizen object.
     */
    @Nullable
    public AbstractEntityCitizen getCitizen()
    {
        return worker;
    }
}
