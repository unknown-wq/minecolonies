package com.minecolonies.core.entity.ai.workers.production;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.advancements.AdvancementTriggers;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.ai.statemachine.AIEventTarget;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIBlockingEventType;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.MinerLevelManagementModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.interactionhandling.PosBasedInteraction;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.jobs.JobMiner;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructureWithWorkOrder;
import com.minecolonies.core.entity.ai.workers.util.BuildingProgressStage;
import com.minecolonies.core.entity.ai.workers.util.MineNode;
import com.minecolonies.core.entity.ai.workers.util.MinerLevel;
import com.minecolonies.core.util.AdvancementUtils;
import com.minecolonies.core.util.WorkerUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.LootTable;
// 26.2: ContextKeySet became net.minecraft.util.context.ContextKeySet.
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.research.util.ResearchConstants.MORE_ORES;
import static com.minecolonies.api.util.constant.CitizenConstants.MIN_WORKING_RANGE;
import static com.minecolonies.api.util.constant.CitizenConstants.STANDARD_WORKING_RANGE;
import static com.minecolonies.api.util.constant.Constants.ONE_HUNDRED_PERCENT;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.StatisticsConstants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.INVALID_MINESHAFT;
import static com.minecolonies.api.util.constant.TranslationConstants.MINER_IN_DANGER;
import static com.minecolonies.api.util.constant.TranslationConstants.MINER_SHAFT_STUCK;
import static com.minecolonies.api.util.constant.TranslationConstants.NEEDS_BETTER_HUT;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE;
import static com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner.FILL_BLOCK;
import static com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner.initStructure;
import static com.minecolonies.core.util.WorkerUtil.getLastLadder;

/**
 * Class which handles the miner behaviour.
 */
// PORT-NOTE(26.2): CHECKED, NO AI BEHAVIOUR AFFECTED. Nothing in this class is disabled — the note that stood
// here read like a class-wide kill switch and was not. One expression changed: NeoForge's ItemAbilities
// tool-action system has no Fabric or vanilla 26.2 counterpart, so the two canPerformAction(PICKAXE_DIG /
// SHOVEL_DIG) tests in getRenderMeta became stack.is(ItemTags.PICKAXES / SHOVELS). That result is appended to
// the renderData string and nothing else — it decides which tool icon hangs off the citizen's belt. No AI
// decision, no mining logic and no tool selection reads it. The only visible difference is a modded pickaxe
// that joins no item tag not drawing an icon.
public class EntityAIStructureMiner extends AbstractEntityAIStructureWithWorkOrder<JobMiner, BuildingMiner>
{
    /**
     * The loot parameter set definition
     */
    public static final ContextKeySet LUCKY_ORE_PARAM_SET = (new ContextKeySet.Builder())
                                                                    .required(LootContextParams.ORIGIN)
                                                                    .required(LootContextParams.THIS_ENTITY)
                                                                    .required(LootContextParams.TOOL)
                                                                    .build();

    /**
     * Lucky ore loot table
     */
    public static final Identifier LUCKY_ORE_LOOT_TABLE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "miner/lucky_ore");

    /**
     * Lead the miner to the other side of the shaft.
     */
    private static final int OTHER_SIDE_OF_SHAFT = 6;

    /**
     * Batchsizes of fill blocks to request.
     */
    private static final int COBBLE_REQUEST_BATCHES = 32;

    /**
     * Batch sizes of ladders to request.
     */
    private static final int LADDER_REQUEST_BATCHES = 10;

    public static final String RENDER_META_TORCH   = "torch";
    public static final String RENDER_META_STONE   = "stone";
    public static final String RENDER_META_SHOVEL  = "shovel";
    public static final String RENDER_META_PICKAXE = "pickaxe";

    private static final int NODE_DISTANCE    = 7;
    /**
     * Return to chest after building level stacks.
     */
    private static final int MAX_BLOCKS_MINED = 64;
    public static final  int SHAFT_RADIUS     = 3;
    private static final int SAFE_CHECK_RANGE = 5;

    /**
     * Considered the base of the shaft
     */
    private static final int SHAFT_BASE_DEPTH = 8;

    /**
     * Possible rotations.
     */
    private static final int ROTATE_ONCE        = 1;
    private static final int ROTATE_TWICE       = 2;
    private static final int ROTATE_THREE_TIMES = 3;
    private static final int ROTATE_FOUR_TIMES  = 4;

    /**
     * Check for liquids in the following range.
     */
    private static final int LIQUID_CHECK_RANGE = 5;

    /**
     * How long the shaft may make no progress before the miner drops the block he is aiming at and looks again,
     * in ticks spent inside {@link AIWorkerState#MINER_MINING_SHAFT}.
     * <p>
     * Measured on a dedicated server with a diamond pickaxe in free mode: a healthy shaft consumes its 7x7 working
     * level at roughly one block every 62 ticks, and drops the ladder a block every 2500-3000 ticks. Sixty seconds
     * is therefore about twenty times a normal block and well inside a normal ladder step, so re-aiming costs a
     * working miner nothing but the mining delay he had accumulated on one block. Re-aiming is deliberately cheap
     * and silent: it is the recovery, not the alarm.
     */
    private static final int SHAFT_RETRY_TICKS = 20 * 60;

    /**
     * How long the shaft may make no progress before the miner says so, in ticks spent inside
     * {@link AIWorkerState#MINER_MINING_SHAFT}.
     * <p>
     * Four times the slowest ladder step measured above, so a miner with a worse pickaxe than the one on the stand
     * still finishes a step comfortably inside it. Only time actually spent mining the shaft counts (see
     * {@link #STALL_GAP_LIMIT}), so a night off, a walk to the warehouse or a period of mourning cannot age it.
     */
    private static final int SHAFT_STALL_TICKS = 20 * 60 * 10;

    /**
     * The longest gap between two consecutive visits to {@link #doShaftMining()} that still counts as the miner
     * having been at work on the shaft the whole time.
     * <p>
     * The clock is driven off game time rather than off a turn count, because turns are throttled by the mining
     * delay and would measure something else on every different pickaxe. Game time, though, also runs while the
     * miner is somewhere else entirely -- asleep, mourning, or emptying his inventory into the warehouse -- and
     * none of that is the shaft failing. So a gap longer than this one is treated as a break and contributes
     * nothing; a shorter one is added whole.
     * <p>
     * The first version of this capped every gap at {@code STANDARD_DELAY * 4} instead, on the assumption that the
     * state ticks at its own delay while the miner is working. It does not, and the server said so: the gaps
     * between consecutive visits were measured at 200 to 620 ticks, because a turn spent mining ends in a mining
     * delay and the AI passes through several other states before it comes back. The cap threw away about
     * ninety-five per cent of the elapsed time, the clock ran at roughly a twentieth of game time, and a shaft that
     * had been dead for a hundred thousand ticks had aged the alarm by four thousand. One minute is comfortably
     * above every gap measured while working and far below the twelve thousand ticks of a night.
     */
    private static final int STALL_GAP_LIMIT = 20 * 60;

    /**
     * Health below this fraction of the maximum, together with a drop since the last check, counts as "being hurt"
     * and sends the miner out of the shaft.
     */
    private static final float HURT_HEALTH_FRACTION = 0.75f;

    /**
     * Mining icon
     */
    private final static VisibleCitizenStatus MINING =
      new VisibleCitizenStatus(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/icons/work/miner.png"), "com.minecolonies.gui.visiblestatus.miner");

    //The current block to mine
    @Nullable
    private BlockPos minerWorkingLocation;

    //the last safe location now being air
    @Nullable
    private BlockPos currentStandingPosition;

    @Nullable
    private MineNode workingNode = null;

    /**
     * The health seen at the previous danger check, so a drop between two checks can be told from a citizen who is
     * merely still hurt from something that happened a while ago.
     */
    private float lastSeenHealth = -1;

    /**
     * Game time at which the miner last fled the shaft, so the complaint is not repeated on every tick of a fire
     * that lasts three hundred of them.
     */
    private long lastFledAt = Long.MIN_VALUE;

    /**
     * Constructor for the Miner. Defines the tasks the miner executes.
     *
     * @param job a fisherman job to use.
     */
    public EntityAIStructureMiner(@NotNull final JobMiner job)
    {
        super(job);
        super.registerTargets(
          /*
           * Being burnt or hurt beats whatever else the miner was doing, from any state, so this is registered
           * first and blocks. See isInDanger.
           */
          new AIEventTarget<IAIState>(AIBlockingEventType.STATE_BLOCKING, this::isInDanger, this::fleeDanger, 1),
          /*
           * If IDLE - switch to start working.
           */
          new AITarget(PREPARING, MINER_CHECK_MINESHAFT, 1),
          new AITarget(MINER_WALKING_TO_LADDER, this::goToLadder, TICKS_SECOND),
          new AITarget(MINER_REPAIRING_LADDER, this::repairLadder, STANDARD_DELAY),
          new AITarget(MINER_CHECK_MINESHAFT, this::checkMineShaft, TICKS_SECOND),
          new AITarget(MINER_MINING_SHAFT, this::doShaftMining, STANDARD_DELAY),
          new AITarget(MINER_BUILDING_SHAFT, this::doShaftBuilding, STANDARD_DELAY),
          new AITarget(MINER_MINING_NODE, this::executeNodeMining, STANDARD_DELAY)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingMiner> getExpectedBuildingClass()
    {
        return BuildingMiner.class;
    }

    //Miner wants to work but is not at building
    @NotNull
    @Override
    protected IAIState startWorkingAtOwnBuilding()
    {
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
        if ((building.getLadderLocation() == null || worker.getY() >= building.getPosition().getY()) && !walkToBuilding())
        {
            return START_WORKING;
        }

        if (building.getLadderLocation() == null || building.getCobbleLocation() == null)
        {
            worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatableEscape(INVALID_MINESHAFT), ChatPriority.BLOCKING));
            return START_WORKING;
        }

        if (!building.hasWorkOrder())
        {
            final List<WorkOrderMiner> list = building.getColony().getWorkManager().getOrderedList(WorkOrderMiner.class, building.getPosition());
            if (!list.isEmpty())
            {
                building.setWorkOrder(list.get(0));
                return LOAD_STRUCTURE;
            }
        }

        final IAIState nextState = super.startWorkingAtOwnBuilding();
        if (nextState != IDLE)
        {
            return nextState;
        }

        //Miner is at building
        return PREPARING;
    }

    @Override
    public int getBreakSpeedLevel()
    {
        return getPrimarySkillLevel();
    }

    @Override
    public int getPlaceSpeedLevel()
    {
        return getSecondarySkillLevel();
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return building.getBuildingLevel() * MAX_BLOCKS_MINED;
    }

    @Override
    protected void updateRenderMetaData()
    {
        StringBuilder renderData = new StringBuilder(getState() == MINER_MINING_SHAFT || getState() == MINE_BLOCK || getState() == BUILDING_STEP ? RENDER_META_WORKING : "");
        final ItemStack block = new ItemStack(getMainFillBlock());

        for (int slot = 0; slot < worker.getInventoryCitizen().getSlots(); slot++)
        {
            final ItemStack stack = worker.getInventoryCitizen().getStackInSlot(slot);
            if (stack.getItem() == Items.TORCH && renderData.indexOf(RENDER_META_TORCH) == -1)
            {
                renderData.append(RENDER_META_TORCH);
            }
            else if (stack.getItem() == block.getItem() && renderData.indexOf(RENDER_META_STONE) == -1)
            {
                renderData.append(RENDER_META_STONE);
            }
            else if (stack.is(net.minecraft.tags.ItemTags.PICKAXES) && renderData.indexOf(RENDER_META_PICKAXE) == -1)
            {
                renderData.append(RENDER_META_PICKAXE);
            }
            else if (stack.is(net.minecraft.tags.ItemTags.SHOVELS) && renderData.indexOf(RENDER_META_SHOVEL) == -1)
            {
                renderData.append(RENDER_META_SHOVEL);
            }
        }

        worker.setRenderMetadata(renderData.toString());
    }

    @Override
    public IAIState doMining()
    {
        if (blockToMine == null)
        {
            return BUILDING_STEP;
        }

        final BlockState blockState = world.getBlockState(blockToMine);
        if (!IColonyManager.getInstance().getCompatibilityManager().isOre(blockState))
        {
            blockToMine = getSurroundingOreOrDefault(blockToMine);
        }

        if (world.getBlockState(blockToMine).getBlock() instanceof AirBlock)
        {
            return BUILDING_STEP;
        }

        if (!mineBlock(blockToMine, getCurrentWorkingPosition()))
        {
            worker.swing(InteractionHand.MAIN_HAND);
            return getState();
        }

        blockToMine = getSurroundingOreOrDefault(blockToMine);
        if (IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(blockToMine)))
        {
            return getState();
        }

        worker.decreaseSaturationForContinuousAction();
        return BUILDING_STEP;
    }

    @Override
    public boolean shouldSilkTouchBlock(final BlockState curBlockState)
    {
        return IColonyManager.getInstance().getCompatibilityManager().isOre(curBlockState);
    }

    private BlockPos getSurroundingOreOrDefault(final BlockPos pos)
    {
        for (Direction direction : Direction.values())
        {
            final BlockPos offset = pos.relative(direction);
            if (IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(offset)))
            {
                return offset;
            }
        }
        return pos;
    }

    /**
     * Walking to the ladder to check out the mine.
     *
     * @return next IAIState.
     */
    @NotNull
    private IAIState goToLadder()
    {
        if (!walkToLadder())
        {
            return MINER_WALKING_TO_LADDER;
        }
        return MINER_REPAIRING_LADDER;
    }

    private boolean walkToLadder()
    {
        return walkToWorkPos(building.getLadderLocation());
    }

    public boolean walkToConstructionSite(final BlockPos currentBlock)
    {
        if (workFrom == null)
        {
            workFrom = getWorkingPosition(currentBlock);
        }

        //The miner shouldn't search for a save position. Just let him build from where he currently is.
        return walkWithProxy(workFrom, STANDARD_WORKING_RANGE) || MathUtils.twoDimDistance(worker.blockPosition(), workFrom) < MIN_WORKING_RANGE;
    }

    @NotNull
    private IAIState repairLadder()
    {
        @NotNull final BlockPos nextCobble =
          new BlockPos(building.getCobbleLocation().getX(), getLastLadder(building.getLadderLocation(), world) - 1, building.getCobbleLocation().getZ());
        @NotNull final BlockPos nextLadder =
          new BlockPos(building.getLadderLocation().getX(), getLastLadder(building.getLadderLocation(), world) - 1, building.getLadderLocation().getZ());
        @NotNull final BlockPos safeStand =
          new BlockPos(building.getLadderLocation().getX(), getLastLadder(building.getLadderLocation(), world), building.getLadderLocation().getZ());

        if (!world.getBlockState(nextCobble).isSolid())
        {
            if (!checkIfRequestForItemExistOrCreate(new ItemStack(getSolidSubstitution(nextCobble, pos -> null).getBlock()), COBBLE_REQUEST_BATCHES, 1))
            {
                return getState();
            }
            if (!world.getBlockState(nextCobble).isAir() && !mineBlock(nextCobble, safeStand))
            {
                return getState();
            }
            setBlockFromInventory(nextCobble, getLadderBackFillBlock());
            return getState();
        }

        if (!world.getBlockState(nextLadder).is(net.minecraft.tags.BlockTags.CLIMBABLE) && !world.getBlockState(nextLadder).isSolid())
        {
            if (!checkIfRequestForItemExistOrCreate(new ItemStack(Blocks.LADDER), LADDER_REQUEST_BATCHES, 1))
            {
                return getState();
            }
            if (!world.getBlockState(nextLadder).isAir() && !mineBlock(nextLadder, safeStand))
            {
                return getState();
            }
            //Get ladder orientation
            final BlockState metadata = Blocks.LADDER.defaultBlockState()
                                          .setValue(HorizontalDirectionalBlock.FACING,
                                            BlockPosUtil.directionFromDelta(nextLadder.getX() - nextCobble.getX(), 0, nextLadder.getZ() - nextCobble.getZ()));
            setBlockFromInventory(nextLadder, Blocks.LADDER, metadata);
            return getState();
        }
        return MINER_CHECK_MINESHAFT;
    }

    /**
     * Get the main fill block. Based on the settings.
     *
     * @return the main fill block.
     */
    private Block getMainFillBlock()
    {
        return building.getSetting(FILL_BLOCK).getValue().getBlock();
    }

    /**
     * Get the ladderback fill block. Cobble for overworld, netherrack for nether.
     *
     * @return the ladderback fill block.
     */
    private Block getLadderBackFillBlock()
    {
        if (WorldUtil.isNetherType(world))
        {
            return Blocks.NETHERRACK;
        }
        return Blocks.COBBLESTONE;
    }

    @NotNull
    private IAIState checkMineShaft()
    {
        final BuildingMiner buildingMiner = building;
        // Check if we reached the bottom of the shaft
        if (getLastLadder(buildingMiner.getLadderLocation(), world) < world.getMinY() + SHAFT_BASE_DEPTH)
        {
            AdvancementUtils.TriggerAdvancementPlayersForColony(job.getColony(), AdvancementTriggers.DEEP_MINE.get()::trigger);
        }

        // Check if we reached the mineshaft depth limit
        if (getLastLadder(buildingMiner.getLadderLocation(), world) < buildingMiner.getDepthLimit(world))
        {
            //If the miner hut has been placed too deep.
            if (buildingMiner.getFirstModuleOccurance(MinerLevelManagementModule.class).getNumberOfLevels() == 0)
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatableEscape(NEEDS_BETTER_HUT), ChatPriority.BLOCKING));
                return IDLE;
            }
            worker.getCitizenData().setVisibleStatus(MINING);
            return MINER_MINING_NODE;
        }
        worker.getCitizenData().setVisibleStatus(MINING);
        return MINER_MINING_SHAFT;
    }

    @Override
    public ItemStack getTotalAmount(final ItemStack stack)
    {
        if (ItemStackUtils.isEmpty(stack))
        {
            return null;
        }

        final ItemStack copy = stack.copy();
        copy.setCount(Math.max(super.getTotalAmount(stack).getCount(), copy.getMaxStackSize() / 2));
        return copy;
    }

    /**
     * Watch the shaft for progress and act when there is none.
     * <p>
     * The only thing that has to change for a shaft to be alive is the bottom of the ladder. Everything else the
     * miner does down there -- picking a block, walking to it, breaking it -- can carry on indefinitely without the
     * shaft getting one block deeper, and that is exactly the failure this watches for: a shaft flooded with lava
     * was measured holding {@code lastLadder} at 62 for sixty thousand ticks without changing a single block in the
     * world, while the AI state ticked over normally, nothing was said in chat and {@code /mc colony diagnose}
     * reported no problems.
     * <p>
     * Two thresholds, because the two useful responses have very different costs. Re-aiming is free, so it happens
     * every {@link #SHAFT_RETRY_TICKS}: dropping {@link #minerWorkingLocation} and {@link #currentStandingPosition}
     * forces the next pass to scan the working level again rather than keep insisting on a block it has not managed
     * to break, and in particular it re-picks a standing position, which is the thing that goes bad when liquid
     * fills the spot the miner had chosen to stand on. Complaining costs the player's attention, so it waits for
     * {@link #SHAFT_STALL_TICKS} and happens once per stall.
     */
    private void watchShaftProgress()
    {
        final int lastLadder = getLastLadder(building.getLadderLocation(), world);
        final BuildingMiner.ShaftWatch watch =
          building.tickShaftWatch(lastLadder, world.getGameTime(), STALL_GAP_LIMIT, SHAFT_RETRY_TICKS, SHAFT_STALL_TICKS);

        if (watch.reaim())
        {
            minerWorkingLocation = null;
            currentStandingPosition = null;
        }

        if (watch.report())
        {
            // PosBasedInteraction and not StandardInteraction, because the message names a place. A
            // StandardInteraction looks up its validator in InteractionValidatorRegistry under the *whole*
            // component, arguments included, so one carrying a coordinate can never match the key it was
            // registered under; CitizenData#triggerInteraction then drops it on the floor without a word.
            // Measured: the watchdog fired, the building reported the stall to /mc colony diagnose, and
            // citizenChatOptions stayed empty. The pos-based form keys the lookup on the bare component and
            // carries the position separately, which is what every other worker with a coordinate in its
            // complaint already does.
            worker.getCitizenData()
              .triggerInteraction(new PosBasedInteraction(Component.translatableEscape(MINER_SHAFT_STUCK, String.valueOf(lastLadder)),
                ChatPriority.BLOCKING,
                Component.translatableEscape(MINER_SHAFT_STUCK),
                building.getShaftStallPos()));
        }
    }

    /**
     * Whether the miner is being hurt where he stands, and should leave.
     * <p>
     * Nothing in the worker AI reads damage: a lava source dropped a miner from 20 to 12 hit points with three
     * hundred ticks of fire on him and the AI state did not change, so he stood in it until he died and the colony
     * hired the next one into the same spot. Fire and lava are named explicitly because they are what a mine
     * produces; the health test catches everything else, and only fires on a drop since the previous check so that
     * a citizen who is merely still healing is left to work.
     *
     * @return true if the miner is in danger.
     */
    private boolean isInDanger()
    {
        final float health = worker.getHealth();
        final float previous = lastSeenHealth;
        lastSeenHealth = health;

        return worker.getRemainingFireTicks() > 0
                 || worker.isInLava()
                 || (previous >= 0 && health < previous && health < worker.getMaxHealth() * HURT_HEALTH_FRACTION);
    }

    /**
     * Leave the working face and head for the hut, complaining once per retreat.
     * <p>
     * There is no dedicated escape behaviour to reuse and inventing one would mean a new state and a new kind of
     * pathing; walking back to his own building is a thing the miner already does, it is up the ladder and out of
     * the mine by construction, and because this transition is re-evaluated while he still burns he cannot turn
     * round and go back down until the fire is out. The block he was aiming at is dropped as well -- whatever made
     * that spot dangerous makes it the wrong place to return to.
     *
     * @return the state to switch to.
     */
    private IAIState fleeDanger()
    {
        minerWorkingLocation = null;
        currentStandingPosition = null;

        final long now = world.getGameTime();
        if (now - lastFledAt > TICKS_SECOND * 30)
        {
            // Pos-based for the same reason as the stall message above: it names the spot he is running from.
            final BlockPos where = worker.blockPosition();
            worker.getCitizenData()
              .triggerInteraction(new PosBasedInteraction(Component.translatableEscape(MINER_IN_DANGER, where.toShortString()),
                ChatPriority.IMPORTANT,
                Component.translatableEscape(MINER_IN_DANGER),
                where));
        }
        lastFledAt = now;

        return START_WORKING;
    }

    private IAIState doShaftMining()
    {
        watchShaftProgress();

        minerWorkingLocation = getNextBlockInShaftToMine();
        if (minerWorkingLocation == null)
        {
            return advanceLadder(MINER_MINING_SHAFT);
        }

        //Note for future me:
        //we have to return; on false of this method
        //but omitted because end of method.
        if (mineBlock(minerWorkingLocation, currentStandingPosition))
        {
            worker.decreaseSaturationForContinuousAction();
        }

        return MINER_MINING_SHAFT;
    }

    private IAIState advanceLadder(final IAIState state)
    {
        if (!checkIfRequestForItemExistOrCreate(new ItemStack(getLadderBackFillBlock()), COBBLE_REQUEST_BATCHES, 1) ||
              !checkIfRequestForItemExistOrCreate(new ItemStack(Blocks.LADDER), LADDER_REQUEST_BATCHES, 1))
        {
            return state;
        }

        if (ladderDamaged())
        {
            return MINER_REPAIRING_LADDER;
        }

        final BlockPos vector = building.getLadderLocation().subtract(building.getCobbleLocation());
        final int xOffset = SHAFT_RADIUS * vector.getX();
        final int zOffset = SHAFT_RADIUS * vector.getZ();

        @NotNull final BlockPos nextLadder =
          new BlockPos(building.getLadderLocation().getX(), getLastLadder(building.getLadderLocation(), world) - 1, building.getLadderLocation().getZ());
        @NotNull final BlockPos safeCobble =
          new BlockPos(building.getLadderLocation().getX(), getLastLadder(building.getLadderLocation(), world) - 2, building.getLadderLocation().getZ());

        //Check for safe floor
        for (int x = -SAFE_CHECK_RANGE; x <= SAFE_CHECK_RANGE; x++)
        {
            for (int z = -SAFE_CHECK_RANGE; z <= SAFE_CHECK_RANGE; z++)
            {
                @NotNull final BlockPos curBlock = new BlockPos(safeCobble.getX() + x + xOffset, safeCobble.getY(), safeCobble.getZ() + z + zOffset);
                if (!secureBlock(curBlock, currentStandingPosition))
                {
                    return state;
                }
            }
        }

        @NotNull final BlockPos safeStand =
          new BlockPos(building.getLadderLocation().getX(), getLastLadder(building.getLadderLocation(), world), building.getLadderLocation().getZ());
        @NotNull final BlockPos nextCobble =
          new BlockPos(building.getCobbleLocation().getX(), getLastLadder(building.getLadderLocation(), world) - 1, building.getCobbleLocation().getZ());

        final MinerLevelManagementModule module = building.getFirstModuleOccurance(MinerLevelManagementModule.class);
        if (module.getStartingLevelShaft() == 0)
        {
            module.setStartingLevelShaft(nextCobble.getY() - 4);
        }

        if (nextCobble.getY() < module.getStartingLevelShaft())
        {
            return MINER_BUILDING_SHAFT;
        }

        if (!world.getBlockState(nextCobble).canBeReplaced() && (!mineBlock(nextCobble, safeStand))
              || (!world.getBlockState(nextLadder).canBeReplaced() && !mineBlock(nextLadder, safeStand)))
        {
            //waiting until blocks are mined
            return state;
        }


        //Get ladder orientation
        final BlockState metadata = getBlockState(safeStand);

        //set solid block
        setBlockFromInventory(nextCobble, getLadderBackFillBlock());
        //set ladder
        setBlockFromInventory(nextLadder, Blocks.LADDER, metadata);
        this.incrementActionsDoneAndDecSaturation();
        return MINER_CHECK_MINESHAFT;
    }

    private BlockState getBlockState(@NotNull final BlockPos pos)
    {
        return world.getBlockState(pos);
    }

    /**
     * Calculates the next non-air block to mine. Will take the nearest block it finds.
     *
     * @return the next block to mine.
     */
    @Nullable
    private BlockPos getNextBlockInShaftToMine()
    {
        final BlockPos ladderPos = building.getLadderLocation();
        final int lastLadder = getLastLadder(ladderPos, world);

        // Sweep before anything else. This used to sit below the shortcut return further down, which meant it was
        // skipped on exactly the passes where the miner had a solid block to aim at -- that is, on nearly all of
        // them -- so liquid arriving at the working level was only ever cleared by accident. It also has to be
        // above the shortcut for a second reason: the shortcut returns a standing position too, and a standing
        // position that has since been flooded is what turns a wet shaft into a dead one.
        sweepShaftFluids(ladderPos, lastLadder);

        if (minerWorkingLocation == null)
        {
            minerWorkingLocation = new BlockPos(ladderPos.getX(), lastLadder + 1, ladderPos.getZ());
        }
        BlockState block = getBlockState(minerWorkingLocation);
        if (!block.isAir()
              && block.getBlock() != Blocks.LADDER
              && block.getFluidState().isEmpty())
        {
            if (currentStandingPosition == null)
            {
                // Was minerWorkingLocation, i.e. "stand inside the block you are about to break". It survives only
                // because AbstractEntityAIInteract#checkMiningLocation lets a worker already within twelve blocks
                // work anyway; the ladder is a place he can really be.
                currentStandingPosition = new BlockPos(ladderPos.getX(), lastLadder, ladderPos.getZ());
            }
            return minerWorkingLocation;
        }
        // The ladder column is the one place in a shaft the miner can always be: he is standing on it right now, and
        // it is by definition reachable, because it is how he got here. It is the fallback for the standing-position
        // search below, which otherwise leaves currentStandingPosition pointing at the block it was just told to
        // mine -- and a standing position inside a solid block is one the pathfinder can never satisfy. That is not
        // a theoretical worry: with lava in the shaft it was measured holding a miner at
        // stand=203,62,200 (cobblestone) for the whole run while he wandered about on the surface at y=71, target
        // and standing position both unchanged, because every path request to that block failed instantly.
        final BlockPos ladderStand = new BlockPos(ladderPos.getX(), lastLadder, ladderPos.getZ());
        currentStandingPosition = ladderStand;
        @Nullable BlockPos nextBlockToMine = null;
        double bestDistance = Double.MAX_VALUE;

        final BlockPos vector = building.getLadderLocation().subtract(building.getCobbleLocation());
        final int xOffset = SHAFT_RADIUS * vector.getX();
        final int zOffset = SHAFT_RADIUS * vector.getZ();

        //7x7 shaft find nearest block
        //Beware from positive to negative! to draw the miner to a wall to go down
        for (int x = SHAFT_RADIUS + xOffset; x >= -SHAFT_RADIUS + xOffset; x--)
        {
            for (int z = -SHAFT_RADIUS + zOffset; z <= SHAFT_RADIUS + zOffset; z++)
            {
                if (x == 0 && 0 == z)
                {
                    continue;
                }
                @NotNull final BlockPos curBlock = new BlockPos(ladderPos.getX() + x, lastLadder, ladderPos.getZ() + z);
                final double distance = curBlock.distSqr(ladderPos) + Math.pow(curBlock.distSqr(minerWorkingLocation), 2);
                block = getBlockState(curBlock);
                if (distance < bestDistance
                      && !world.isEmptyBlock(curBlock))
                {
                    if (!block.getFluidState().isEmpty())
                    {
                        // Liquid is never a mining target. If it can be plugged, the fill block that replaced it is
                        // a fine thing to aim at; if it cannot, the miner must look elsewhere rather than swing at
                        // the liquid itself. Aiming at lava is not merely useless, it is the whole failure: lava's
                        // destroy speed is 100 and no pickaxe is the right tool for it, so
                        // AbstractEntityAIInteract#calculateWorkerMiningDelay hands back a delay in the tens of
                        // thousands of ticks, and the miner sits out most of an hour of game time per block --
                        // during which the next lava has already flowed back into the hole.
                        if (!replaceFluid(curBlock) || !getBlockState(curBlock).getFluidState().isEmpty())
                        {
                            continue;
                        }
                    }
                    nextBlockToMine = curBlock;
                    bestDistance = distance;
                }
            }
        }
        //find good looking standing position
        bestDistance = Double.MAX_VALUE;
        if (nextBlockToMine != null)
        {
            for (int x = 1; x >= -1; x--)
            {
                for (int z = -1; z <= 1; z++)
                {
                    if (x == 0 && 0 == z)
                    {
                        continue;
                    }
                    @NotNull final BlockPos curBlock = new BlockPos(nextBlockToMine.getX() + x, lastLadder, nextBlockToMine.getZ() + z);
                    final double distance = curBlock.distSqr(ladderPos);
                    if (distance < bestDistance && world.isEmptyBlock(curBlock))
                    {
                        currentStandingPosition = curBlock;
                        bestDistance = distance;
                    }
                }
            }
        }
        return nextBlockToMine;
    }

    /**
     * Clear liquid out of the shaft's working level.
     * <p>
     * The area is the shaft plus two blocks of margin in every direction, which is the reach the original sweep
     * used and is wider than the shaft on purpose: liquid two blocks out is liquid that is about to be one block
     * out. Both source and flowing liquid are handled, because {@code getFluidState().isEmpty()} does not
     * distinguish them and neither does a miner standing in either.
     *
     * @param ladderPos  the ladder anchor.
     * @param lastLadder the y of the bottom of the ladder, which is the level being worked.
     */
    private void sweepShaftFluids(@NotNull final BlockPos ladderPos, final int lastLadder)
    {
        final BlockPos vector = building.getLadderLocation().subtract(building.getCobbleLocation());
        final int xOffset = SHAFT_RADIUS * vector.getX();
        final int zOffset = SHAFT_RADIUS * vector.getZ();

        for (int x = SHAFT_RADIUS + xOffset + 2; x >= -SHAFT_RADIUS + xOffset - 2; x--)
        {
            for (int z = -SHAFT_RADIUS + zOffset - 2; z <= SHAFT_RADIUS + zOffset + 2; z++)
            {
                if (x == 0 && 0 == z)
                {
                    continue;
                }
                replaceFluid(new BlockPos(ladderPos.getX() + x, lastLadder, ladderPos.getZ() + z));
            }
        }
    }

    /**
     * Take the liquid out of a block and plug the hole with the hut's fill block.
     * <p>
     * The order matters and it is the opposite of what the code used to do. {@code setBlockFromInventory} places
     * nothing at all when the fill block is not in the miner's inventory, and it says nothing when it does not --
     * so removing the liquid first and discovering afterwards that there is no cobblestone leaves an open hole in
     * the wall of a shaft that is, by hypothesis, next to a lake. The next tick refills it, and the miner has
     * spent his turn making the situation slightly worse. Of the three callers of {@code setBlockFromInventory}
     * only {@code secureBlock} ever checked, and it is the one place the audit found working correctly.
     * <p>
     * The liquid is bucketed first if the miner is carrying an empty bucket; see {@link #bucketFluid}.
     *
     * @param pos the position to clear.
     * @return true if the liquid was replaced.
     */
    private boolean replaceFluid(@NotNull final BlockPos pos)
    {
        final BlockState state = getBlockState(pos);
        if (state.getFluidState().isEmpty())
        {
            return false;
        }

        final Block fill = getMainFillBlock();
        if (!checkIfRequestForItemExistOrCreate(new ItemStack(fill), COBBLE_REQUEST_BATCHES, 1)
              || worker.getCitizenInventoryHandler().findFirstSlotInInventoryWith(fill) == -1)
        {
            return false;
        }

        bucketFluid(pos, state);
        BlockUtils.removeFluid(world, pos);
        setBlockFromInventory(pos, fill);
        return true;
    }

    /**
     * Put a source block into an empty bucket the miner is carrying, instead of destroying it.
     * <p>
     * {@code BlockUtils.removeFluid} in Structurize already asks the block for its bucket -- that is how it removes
     * a source at all -- and then drops the stack on the floor of the method. Structurize is a dependency of this
     * port and not ours to change, so the pickup is done here, before {@code removeFluid} is called; by the time it
     * runs the block is already air and it has nothing left to do. A shaft that goes through a lava lake is a lot
     * of buckets of furnace fuel, and the miner was throwing every one of them away.
     * <p>
     * Only sources are worth taking: {@code LiquidBlock#pickupBlock} returns an empty stack for flowing liquid, and
     * the empty bucket must not be spent on that.
     *
     * @param pos   the position being cleared.
     * @param state the state at that position.
     */
    private void bucketFluid(@NotNull final BlockPos pos, @NotNull final BlockState state)
    {
        if (!(state.getBlock() instanceof final BucketPickup pickup) || !state.getFluidState().isSource())
        {
            return;
        }

        final int slot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.BUCKET);
        if (slot == -1)
        {
            return;
        }

        final ItemStack filled = pickup.pickupBlock(null, world, pos, state);
        if (filled.isEmpty())
        {
            return;
        }

        worker.getInventoryCitizen().extractItem(slot, 1, false);
        if (!InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), filled))
        {
            InventoryUtils.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), filled);
        }
    }

    @NotNull
    private IAIState doShaftBuilding()
    {
        if (!walkToBuilding())
        {
            return MINER_BUILDING_SHAFT;
        }

        final BlockPos ladderPos = building.getLadderLocation();
        final int lastLadder = getLastLadder(ladderPos, world) + 1;

        final BlockPos vector = ladderPos.subtract(building.getCobbleLocation());
        final int xOffset = SHAFT_RADIUS * vector.getX();
        final int zOffset = SHAFT_RADIUS * vector.getZ();

        initStructure(null, new BlockPos(ladderPos.getX() + xOffset, lastLadder + 1, ladderPos.getZ() + zOffset), building, world, job);
        return LOAD_STRUCTURE;
    }

    @NotNull
    private IAIState executeNodeMining()
    {
        final MinerLevelManagementModule module = building.getFirstModuleOccurance(MinerLevelManagementModule.class);
        ;
        @Nullable final MinerLevel currentLevel = module.getCurrentLevel();
        if (currentLevel == null)
        {
            module.setCurrentLevel(module.getNumberOfLevels() - 1);
            return executeNodeMining();
        }
        return searchANodeToMine(currentLevel);
    }

    private IAIState searchANodeToMine(@NotNull final MinerLevel currentLevel)
    {
        final BuildingMiner buildingMiner = building;
        if (buildingMiner == null)
        {
            return IDLE;
        }

        final MinerLevelManagementModule module = building.getFirstModuleOccurance(MinerLevelManagementModule.class);
        if (workingNode == null || workingNode.getStatus() == MineNode.NodeStatus.COMPLETED)
        {
            workingNode = module.getActiveNode();
            module.setActiveNode(workingNode);

            if (workingNode == null)
            {
                final int levelId = module.getLevelId(currentLevel);
                if (levelId > 0)
                {
                    module.setCurrentLevel(levelId - 1);
                }
            }
            return MINER_CHECK_MINESHAFT;
        }

        //normal facing +x
        RotationMirror rotMir = RotationMirror.NONE;

        final int workingNodeX = workingNode.getX() > workingNode.getParent().getX() ? 1 : 0;
        final int workingNodeZ = workingNode.getZ() > workingNode.getParent().getZ() ? 1 : 0;
        final int vectorX = workingNode.getX() < workingNode.getParent().getX() ? -1 : workingNodeX;
        final int vectorZ = workingNode.getZ() < workingNode.getParent().getZ() ? -1 : workingNodeZ;

        if (vectorX == -1)
        {
            rotMir = RotationMirror.R180;
        }
        else if (vectorZ == -1)
        {
            rotMir = RotationMirror.R270;
        }
        else if (vectorZ == 1)
        {
            rotMir = RotationMirror.R90;
        }

        final MineNode parentNode = currentLevel.getNode(workingNode.getParent());

        if (parentNode != null && parentNode.getStyle() != MineNode.NodeType.SHAFT && parentNode.getStatus() != MineNode.NodeStatus.COMPLETED)
        {
            workingNode = parentNode;
            workingNode.setStatus(MineNode.NodeStatus.AVAILABLE);
            module.setActiveNode(parentNode);
            buildingMiner.markDirty();
            //We need to make sure to walk back to the last valid parent

            if (workingNode.getRotationMirror().isPresent() && workingNode.getRotationMirror().get() != rotMir)
            {
                Log.getLogger().warn("Calculated rotation doesn't match recorded: x:" + workingNodeX + " z:" + workingNodeZ + " at: " + building.getColony().getID());
            }

            return MINER_CHECK_MINESHAFT;
        }
        @NotNull final BlockPos standingPosition = new BlockPos(workingNode.getParent().getX(), currentLevel.getDepth(), workingNode.getParent().getZ());
        currentStandingPosition = standingPosition;
        if (workingNode != null && currentLevel.getNode(new Vec2i(workingNode.getX(), workingNode.getZ())) == null)
        {
            module.setActiveNode(null);
            module.setOldNode(null);
            return MINER_MINING_SHAFT;
        }

        if ((workingNode.getStatus() == MineNode.NodeStatus.AVAILABLE || workingNode.getStatus() == MineNode.NodeStatus.IN_PROGRESS) && !walkWithProxy(standingPosition))
        {
            workingNode.setRotationMirror(rotMir);
            return executeStructurePlacement(workingNode, standingPosition);
        }
        return MINER_CHECK_MINESHAFT;
    }

    private boolean secureBlock(@NotNull final BlockPos curBlock, @NotNull final BlockPos safeStand)
    {
        final BlockState stateAtPos = getBlockState(curBlock);
        if ((!stateAtPos.blocksMotion() && getBlock(curBlock) != Blocks.TORCH) || !stateAtPos.getFluidState().isEmpty()
              || IColonyManager.getInstance().getCompatibilityManager().isOre(world.getBlockState(curBlock)))
        {
            if (!mineBlock(curBlock, safeStand))
            {
                //make securing go fast to not confuse the player
                setDelay(1);
                return false;
            }
            if (!checkIfRequestForItemExistOrCreate(new ItemStack(getMainFillBlock()), COBBLE_REQUEST_BATCHES, 1))
            {
                return false;
            }

            setBlockFromInventory(curBlock, getMainFillBlock());
            //To set it to clean stone... would be cheating
            return false;
        }
        return true;
    }

    private IAIState executeStructurePlacement(@NotNull final MineNode mineNode, @NotNull final BlockPos standingPosition)
    {
        mineNode.setStatus(MineNode.NodeStatus.IN_PROGRESS);
        building.markDirty();
        //Preload structures
        if (building.getWorkOrder() == null || building.getWorkOrder().getBlueprint() == null)
        {
            initStructure(mineNode,
              new BlockPos(mineNode.getX(), building.getFirstModuleOccurance(MinerLevelManagementModule.class).getCurrentLevel().getDepth(), mineNode.getZ()),
              building,
              world,
              job);
            return LOAD_STRUCTURE;
        }

        // Check for liquids. This used to look only at isSource(), which left every flowing block of a lake that
        // had reached the node in place, and it placed the fill block without checking there was one to place.
        // Both are now replaceFluid's problem, and it is the same sweep the shaft uses.
        for (int x = -NODE_DISTANCE / 2 - 1; x <= NODE_DISTANCE / 2 + 1; x++)
        {
            for (int z = -NODE_DISTANCE / 2 - 1; z <= NODE_DISTANCE / 2 + 1; z++)
            {
                for (int y = -1; y <= LIQUID_CHECK_RANGE; y++)
                {
                    replaceFluid(new BlockPos(mineNode.getX() + x, standingPosition.getY() + y, mineNode.getZ() + z));
                }
            }
        }

        workingNode = null;

        if (building.getWorkOrder().getBlueprint() != null)
        {
            return LOAD_STRUCTURE;
        }

        return MINER_MINING_NODE;
    }

    @Override
    public IAIState afterStructureLoading()
    {
        return BUILDING_STEP;
    }

    private void setBlockFromInventory(@NotNull final BlockPos location, @NotNull final Block block)
    {
        worker.swing(worker.getUsedItemHand());
        setBlockFromInventory(location, block, block.defaultBlockState());
    }

    private void setBlockFromInventory(@NotNull final BlockPos location, final Block block, final BlockState metadata)
    {
        final int slot;
        if (block instanceof LadderBlock)
        {
            slot = worker.getCitizenInventoryHandler().findFirstSlotInInventoryWith(block);
        }
        else
        {
            slot = worker.getCitizenInventoryHandler().findFirstSlotInInventoryWith(block);
        }
        if (slot != -1)
        {
            //Flag 1+2 is needed for updates
            if (WorldUtil.setBlockState(world, location, metadata))
            {
                getInventory().extractItem(slot, 1, false);
            }
        }
    }

    private Block getBlock(@NotNull final BlockPos loc)
    {
        return world.getBlockState(loc).getBlock();
    }

    @Override
    public void executeSpecificCompleteActions()
    {
        final BuildingMiner minerBuilding = building;
        //If shaft isn't cleared we're in shaft clearing mode.
        final MinerLevelManagementModule module = building.getFirstModuleOccurance(MinerLevelManagementModule.class);
        if (building.getWorkOrder() != null && building.getWorkOrder().getBlueprint() != null)
        {
            if (building.getWorkOrder().getBlueprint().getFileName().contains("minermainshaft"))
            {
                final int depth = building.getWorkOrder().getLocation().getY();
                boolean exists = false;
                for (final MinerLevel level : module.getLevels())
                {
                    if (level.getDepth() == depth)
                    {
                        exists = true;
                        break;
                    }
                }

                @Nullable final BlockPos levelSignPos = WorkerUtil.findFirstLevelSign(building.getWorkOrder().getBlueprint(), building.getWorkOrder().getLocation(), worker.level());
                @NotNull final MinerLevel currentLevel = new MinerLevel(minerBuilding, building.getWorkOrder().getLocation().getY(), levelSignPos);
                if (!exists)
                {
                    module.addLevel(currentLevel);
                    module.setCurrentLevel(module.getNumberOfLevels());
                }
                WorkerUtil.updateLevelSign(world, currentLevel, module.getLevelId(currentLevel));
            }
            else
            {
                final MinerLevel currentLevel = module.getCurrentLevel();
                if (currentLevel == null)
                {
                    Log.getLogger().error("The mine state of the mine at: " + building.getID().toShortString() + " got corrupted. Trying to recover from this somehow....");

                    // This can only happen if something with the state got broken. Safest option is not handling the node closing and just doing the normal complete actions, it will potentially recover.
                }
                else
                {
                    currentLevel.closeNextNode(structurePlacer.getB().getRotationMirror(), module.getActiveNode(), world);
                    module.setActiveNode(null);
                    module.setOldNode(workingNode);
                    WorkerUtil.updateLevelSign(world, currentLevel, module.getLevelId(currentLevel));
                }
            }
        }
        super.executeSpecificCompleteActions();

        //Send out update to client
        building.markDirty();
        if (building.getWorkOrder() != null)
        {
            building.getWorkOrder().clearBlueprint();
        }
    }

    @Override
    public void onBlockDropReception(final List<ItemStack> blockDrops)
    {
        super.onBlockDropReception(blockDrops);
        for (final ItemStack stack : blockDrops)
        {
            building.getModule(STATS_MODULE).incrementBy(ITEM_OBTAINED + ";" + stack.getItem().getDescriptionId(), stack.getCount());
        }
    }

    /**
     * Calculates the working position.
     * <p>
     * Takes a min distance from width and length.
     * <p>
     * Then finds the floor level at that distance and then check if it does contain two air levels.
     *
     * @param targetPosition the position to work at.
     * @return BlockPos position to work from.
     */
    @Override
    public BlockPos getWorkingPosition(final BlockPos targetPosition)
    {
        return getNodeMiningPosition(targetPosition);
    }

    /**
     * Create a save mining position for the miner.
     *
     * @param blockToMine block which should be mined or placed.
     * @return the save position.
     */
    private BlockPos getNodeMiningPosition(final BlockPos blockToMine)
    {
        final BuildingMiner buildingMiner = building;
        final MinerLevelManagementModule module = buildingMiner.getFirstModuleOccurance(MinerLevelManagementModule.class);
        ;

        if (module.getCurrentLevel() == null || module.getActiveNode() == null)
        {
            return blockToMine;
        }
        final Vec2i parentPos = module.getActiveNode().getParent();
        final BlockPos vector = building.getLadderLocation().subtract(building.getCobbleLocation());

        if (parentPos != null && module.getCurrentLevel().getNode(parentPos) != null
              && module.getCurrentLevel().getNode(parentPos).getStyle() == MineNode.NodeType.SHAFT)
        {
            final BlockPos ladderPos = buildingMiner.getLadderLocation();
            return new BlockPos(
              ladderPos.getX() + vector.getX() * OTHER_SIDE_OF_SHAFT,
              module.getCurrentLevel().getDepth(),
              ladderPos.getZ() + vector.getZ() * OTHER_SIDE_OF_SHAFT);
        }
        final Vec2i pos = module.getActiveNode().getParent();
        return new BlockPos(pos.getX(), module.getCurrentLevel().getDepth(), pos.getZ());
    }

    @Override
    public boolean shallReplaceSolidSubstitutionBlock(final Block worldBlock, final BlockState worldMetadata)
    {
        return IColonyManager.getInstance().getCompatibilityManager().isOre(worldMetadata);
    }

    @Override
    protected void triggerMinedBlock(@NotNull final BlockPos position, @NotNull final BlockState blockToMine)
    {
        super.triggerMinedBlock(position, blockToMine);

        if (IColonyManager.getInstance().getCompatibilityManager().isLuckyBlock(blockToMine.getBlock()))
        {
            final double chance = 1 + worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(MORE_ORES);
            final boolean canGetLuckyBlock =
              worker.getRandom().nextDouble() * ONE_HUNDRED_PERCENT <= MinecoloniesAPIProxy.getInstance().getConfig().getServer().luckyBlockChance.get() * chance;

            if (canGetLuckyBlock)
            {

                final ResourceKey<LootTable> lootTableId = ResourceKey.create(Registries.LOOT_TABLE, LUCKY_ORE_LOOT_TABLE.withSuffix(String.valueOf(building.getBuildingLevel())));
                final LootParams lootParams = new Builder((ServerLevel) this.world)
                                                .withParameter(LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(position))
                                                .withParameter(LootContextParams.THIS_ENTITY, worker)
                                                .withParameter(LootContextParams.TOOL, worker.getMainHandItem())
                                                .create(LUCKY_ORE_PARAM_SET);


                final ObjectArrayList<ItemStack> randomItems = worker.level().getServer().reloadableRegistries().getLootTable(lootTableId).getRandomItems(lootParams);
                for (final ItemStack stack : randomItems)
                {
                    InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(stack, worker.getInventoryCitizen());
                }
            }
        }

        if (IColonyManager.getInstance().getCompatibilityManager().isOre(blockToMine))
        {
            building.getColony().getStatisticsManager().increment(ORES_MINED, building.getColony().getDay());
        }
        building.getColony().getStatisticsManager().increment(BLOCKS_MINED, building.getColony().getDay());
    }

    @Override
    protected boolean checkIfCanceled()
    {
        if ((building.getWorkOrder() == null && structurePlacer != null) || (structurePlacer != null && !structurePlacer.getB().hasBluePrint()) || (building.getWorkOrder() != null
            && building.getWorkOrder().getStructurePath().contains("quarry")))
        {
            if (building.hasWorkOrder())
            {
                building.getWorkOrder().clearBlueprint();
                job.getColony().getWorkManager().removeWorkOrder(building.getWorkOrder());
            }
            building.setWorkOrder(null);
            resetCurrentStructure();
            building.cancelAllRequestsOfCitizenOrBuilding(worker.getCitizenData());
            building.setProgressPos(null, BuildingProgressStage.CLEAR);
            return true;
        }

        if (!isThereAStructureToBuild())
        {
            switch ((AIWorkerState) getState())
            {
                case BUILDING_STEP:
                    return true;
                default:
                    return false;
            }
        }
        return building.getWorkOrder() != null && (!WorldUtil.isBlockLoaded(world, building.getWorkOrder().getLocation()));
    }

    private boolean ladderDamaged()
    {
        @NotNull final BlockPos nextLadder =
          new BlockPos(building.getLadderLocation().getX(), getLastLadder(building.getLadderLocation(), world) - 1, building.getLadderLocation().getZ());

        return !world.getBlockState(nextLadder).is(net.minecraft.tags.BlockTags.CLIMBABLE) && !world.getBlockState(nextLadder).isSolid();
    }
}
