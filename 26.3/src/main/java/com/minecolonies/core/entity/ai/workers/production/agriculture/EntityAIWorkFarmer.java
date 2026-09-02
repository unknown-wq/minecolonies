package com.minecolonies.core.entity.ai.workers.production.agriculture;

import net.minecraft.world.level.block.FarmlandBlock;
import com.google.common.reflect.TypeToken;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.advancements.AdvancementTriggers;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.ai.workers.util.IBuilderUndestroyable;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.translation.RequestSystemTranslationConstants;
import com.minecolonies.core.blocks.BlockScarecrow;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import com.minecolonies.core.blocks.MinecoloniesFarmland;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.jobs.JobFarmer;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.items.ItemCrop;
import com.minecolonies.core.network.messages.client.CompostParticleMessage;
import com.minecolonies.core.util.AdvancementUtils;
import com.minecolonies.core.util.WorkerUtil;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.research.util.ResearchConstants.FARMING;
import static com.minecolonies.api.util.constant.CitizenConstants.BLOCK_BREAK_SOUND_RANGE;
import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.StatisticsConstants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.FIELD_BAD_SEED;
import static com.minecolonies.api.util.constant.TranslationConstants.FIELD_GROUND_PREPARED;
import static com.minecolonies.api.util.constant.TranslationConstants.FIELD_NO_SEED;
import static com.minecolonies.api.util.constant.TranslationConstants.NO_FREE_FIELDS;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.FARMER_FIELDS;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE;

/**
 * Farmer AI class. Created: December 20, 2014
 */
public class EntityAIWorkFarmer extends AbstractEntityAICrafting<JobFarmer, BuildingFarmer>
{
    /**
     * Return to chest after this amount of stacks.
     */
    private static final int MAX_BLOCKS_MINED = 64;

    /**
     * The default delay the farmer should have.
     */
    private static final int DEFAULT_DELAY = 40;

    /**
     * The smallest delay the farmer should have.
     */
    private static final int SMALLEST_DELAY = 1;

    /**
     * The bonus the farmer gains each update is level/divider.
     */
    private static final double DELAY_DIVIDER = 1;

    /**
     * The EXP Earned per harvest.
     */
    private static final double XP_PER_HARVEST = 0.5;

    /**
     * The maximum depth to search for a surface
     */
    private static final int MAX_DEPTH = 5;

    /**
     * How far the farmer will reach to work a field cell rather than ask the navigator to move it there.
     * <p>
     * Under the four blocks {@code walkToSafePos} itself accepts as having arrived, so nothing is worked from further
     * away than the existing code already allowed; see {@link #isWithinFieldReach}.
     */
    private static final int FIELD_WORK_REACH = 2;

    /**
     * Farming icon
     */
    private static final VisibleCitizenStatus FARMING_ICON =
      new VisibleCitizenStatus(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/icons/work/farmer.png"), "com.minecolonies.gui.visiblestatus.farmer");

    /**
     * Changed after finished harvesting in order to dump the inventory.
     */
    private boolean shouldDumpInventory = false;

    /**
     * If the farmer actually did any work on the field.
     */
    private boolean didWork = false;

    /**
     * Amount of time we skipped state already.
     */
    private int skippedState = 0;

    /**
     * Set when a cell wanted ground laying down and the farmer had none, cleared again as soon as it has some. Read by
     * {@link #prepareForFarming} to fetch dirt out of the hut, exactly the way compost is fetched.
     */
    private boolean needsGroundFiller = false;

    /**
     * The kinds of tool the cells walked so far wanted and the worker was not carrying, each with the highest harvest
     * level asked of it. Filled by {@link #prepareGroundIfNeeded}, emptied by {@link #prepareForFarming} as the worker
     * gets hold of them.
     * <p>
     * This is not decoration, it is what makes the non-free case work at all. The farmer keeps a hoe and an axe and
     * dumps everything else into the racks ({@code BuildingFarmer#getRequiredItemsAndAmount}), so a pickaxe it is
     * handed is gone again at the next dump - and the asynchronous tool request on its own never puts one back into
     * its hands, because nothing walks the worker to the hut to collect it. Measured, with a stone pickaxe lying in
     * the rack the whole time: a field with 30 stone cells came out of a full pass with all 30 untouched and no
     * visible request. Fetching the tool the same way compost and ground are fetched is the fix.
     * <p>
     * A map rather than a single entry because one field routinely wants several: stone needs a pickaxe, gravel a
     * shovel, a chest an axe. With one slot the farmer learned one tool per pass and a mixed field took as many field
     * cycles as it had materials; with the map it fetches them one after another before the pass starts.
     */
    private final Map<EquipmentTypeEntry, Integer> neededGroundTools = new LinkedHashMap<>();

    /**
     * What the farmer has cleared away since it last reported, by block and count. Emptied by {@link #reportPreparedGround}
     * once a pass over a field ends, which is the only place it is read.
     */
    private final Map<Block, Integer> clearedGround = new LinkedHashMap<>();

    /**
     * Constructor for the Farmer. Defines the tasks the Farmer executes.
     *
     * @param job a farmer job to use.
     */
    public EntityAIWorkFarmer(@NotNull final JobFarmer job)
    {
        super(job);
        super.registerTargets(
          new AITarget(PREPARING, this::prepareForFarming, TICKS_SECOND),
          new AITarget(FARMER_HOE, this::workAtField, 5),
          new AITarget(FARMER_PLANT, this::workAtField, 5),
          new AITarget(FARMER_HARVEST, this::workAtField, 5)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingFarmer> getExpectedBuildingClass()
    {
        return BuildingFarmer.class;
    }

    /**
     * Called to check when the InventoryShouldBeDumped.
     *
     * @return true if the conditions are met
     */
    @Override
    protected boolean wantInventoryDumped()
    {
        if (shouldDumpInventory || job.getActionsDone() >= getActionRewardForCraftingSuccess())
        {
            shouldDumpInventory = false;
            return true;
        }
        return super.wantInventoryDumped();
    }

    @Override
    protected int getActionRewardForCraftingSuccess()
    {
        return MAX_BLOCKS_MINED;
    }

    @Override
    protected void updateRenderMetaData()
    {
        worker.setRenderMetadata((getState() == FARMER_PLANT || getState() == FARMER_HARVEST) ? RENDER_META_WORKING : "");
    }

    @Override
    protected IAIState decide()
    {
        IAIState state = super.decide();

        if (state == IDLE)
        {
            return PREPARING;
        }
        return state;
    }

    @Override
    public boolean hasWorkToDo()
    {
        return true;
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return MAX_BLOCKS_MINED;
    }

    /**
     * Prepares the farmer for farming. Also requests the tools, the compost (if needed) and checks if the farmer has sufficient fields.
     *
     * @return the next IAIState
     */
    @NotNull
    private IAIState prepareForFarming()
    {
        worker.getCitizenData().setJobStatus(JobStatus.IDLE);
        if (building == null || building.getBuildingLevel() < 1)
        {
            worker.getCitizenData().setJobStatus(JobStatus.STUCK);
            return PREPARING;
        }

        final BuildingExtensionsModule module = building.getFirstModuleOccurance(BuildingExtensionsModule.class);
        if (module.getOwnedExtensions().size() == building.getMaxBuildingLevel())
        {
            AdvancementUtils.TriggerAdvancementPlayersForColony(building.getColony(), AdvancementTriggers.MAX_FIELDS.get()::trigger);
        }

        final int amountOfCompostInBuilding = InventoryUtils.hasBuildingEnoughElseCount(building, this::isCompost, 1);
        final int amountOfCompostInInv = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), this::isCompost);

        if (amountOfCompostInBuilding + amountOfCompostInInv <= 0)
        {
            if (building.requestFertilizer() && !building.hasWorkerOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(StackList.class)))
            {
                final List<ItemStack> compostAbleItems = new ArrayList<>();
                compostAbleItems.add(new ItemStack(ModItems.compost, 1));
                compostAbleItems.add(new ItemStack(Items.BONE_MEAL, 1));
                worker.getCitizenData().createRequestAsync(new StackList(compostAbleItems, RequestSystemTranslationConstants.REQUEST_TYPE_FERTILIZER, STACKSIZE, 1));
            }
        }
        else if (amountOfCompostInInv <= 0 && amountOfCompostInBuilding > 0)
        {
            needsCurrently = new Tuple<>(this::isCompost, STACKSIZE);
            return GATHERING_REQUIRED_MATERIALS;
        }

        // Ground to lay down where a cell cannot be tilled. Only fetched once a cell has actually asked for it, and
        // only out of the hut - the request itself is filed at the cell, in prepareGroundIfNeeded. Same shape as the
        // compost fetch above, and it matters for the same reason: dumping the inventory sends the dirt back to the
        // racks, so without this the farmer would work one load of dirt and then never pick it up again.
        if (needsGroundFiller && !FreeMode.isOn(building)
              && InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), this::isGroundFiller) <= 0
              && InventoryUtils.hasBuildingEnoughElseCount(building, this::isGroundFiller, 1) > 0)
        {
            needsCurrently = new Tuple<>(this::isGroundFiller, STACKSIZE);
            return GATHERING_REQUIRED_MATERIALS;
        }

        // And the tool for the block that is in the way, for the same reason and by the same route. The asynchronous
        // request filed at the cell puts the tool in the racks; this is what puts it in the worker's hands. Nothing
        // happens here while the worker already carries something that fits, or while the hut has nothing to fetch -
        // in the latter case the request simply stands and the cells wait for it.
        if (!neededGroundTools.isEmpty() && !FreeMode.isOn(building))
        {
            neededGroundTools.entrySet().removeIf(entry -> InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(),
              stack -> ItemStackUtils.hasEquipmentLevel(stack, entry.getKey(), entry.getValue(), building.getMaxEquipmentLevel())));

            for (final Map.Entry<EquipmentTypeEntry, Integer> entry : neededGroundTools.entrySet())
            {
                final Predicate<ItemStack> toolPredicate =
                  stack -> ItemStackUtils.hasEquipmentLevel(stack, entry.getKey(), entry.getValue(), building.getMaxEquipmentLevel());
                if (InventoryUtils.hasBuildingEnoughElseCount(building, toolPredicate, 1) > 0)
                {
                    needsCurrently = new Tuple<>(toolPredicate, 1);
                    return GATHERING_REQUIRED_MATERIALS;
                }
            }
        }

        if (module.hasNoExtensions())
        {
            if (worker.getCitizenData() != null)
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatableEscape(NO_FREE_FIELDS), ChatPriority.BLOCKING));
            }
            worker.getCitizenData().setJobStatus(JobStatus.STUCK);
            return IDLE;
        }

        final IBuildingExtension fieldToWork = module.getBuildingExtensionToWorkOn();
        if (fieldToWork instanceof FarmField farmField)
        {
            if (checkForToolOrWeapon(ModEquipmentTypes.hoe.get()))
            {
                worker.getCitizenData().setJobStatus(JobStatus.STUCK);
                return PREPARING;
            }
            worker.getCitizenData().setVisibleStatus(FARMING_ICON);
            worker.getCitizenData().setJobStatus(JobStatus.WORKING);
            if (farmField.getFieldStage() == FarmField.Stage.PLANTED && checkIfShouldExecute(farmField, pos -> this.findHarvestableSurface(pos) != null))
            {
                return FARMER_HARVEST;
            }
            else if (farmField.getFieldStage() == FarmField.Stage.HOED)
            {
                return canGoPlanting(farmField);
            }
            else if (farmField.getFieldStage() == FarmField.Stage.EMPTY
                       && checkIfShouldExecute(farmField, pos -> this.findHoeableSurface(pos, farmField) != null || this.findGroundToPrepare(pos, farmField) != null))
            {
                return FARMER_HOE;
            }
            farmField.nextState();
            if (++skippedState >= 4)
            {
                skippedState = 0;
                didWork = true;
                module.resetCurrentExtension();
            }
            return IDLE;
        }
        else if (fieldToWork != null)
        {
            Log.getLogger().warn("Farmer found non-FarmField extension: {}", fieldToWork.getClass());
        }
        return IDLE;
    }

    /**
     * Which of the field's seeds belongs in this cell of ground.
     * <p>
     * The whole of the mixed seed feature is this one function. Everything else in the farmer that used to read
     * {@code farmField.getSeed()} for a particular cell now reads this instead, and with one seed configured it
     * returns that seed for every cell, so a monoculture field behaves exactly as it did.
     * <p>
     * Three properties are what make it work, and all three are needed:
     * <ul>
     *     <li><b>It depends only on the cell.</b> A cell is tilled on one colony day and sown on the next, and the
     *     tilling has to lay down the farmland the sowing will accept - a MineColonies crop wants its own preferred
     *     farmland and vanilla crops want plain farmland ({@link #isRightFarmLandForCrop}). Anything that picked at
     *     random, or in the order the walk happens to reach the cells, would till for one crop and then try to sow a
     *     different one, {@link #findPlantableSurface} would refuse the cell for ever, and the field would sit
     *     tilled and bare. So it is a pure function of the cell's world coordinates and the seed list.</li>
     *     <li><b>It uses absolute coordinates</b>, not the offset from the scarecrow, so resizing a field does not
     *     re-assign the crop of every cell already in it.</li>
     *     <li><b>It is x + z</b>, which lays the crops out in diagonal stripes one cell wide: a checkerboard for two
     *     seeds, three-wide diagonals for three, and so on. Diagonals rather than rows because two cells of the same
     *     crop are then never orthogonally adjacent, which is what melons and pumpkins need - their stems grow fruit
     *     into a free cardinal neighbour - and because a striped field reads as deliberate from the ground. Each
     *     seed gets as near an equal share of the field as its shape allows.</li>
     * </ul>
     *
     * @param farmField the field being worked.
     * @param position  any position in the cell's column; only its X and Z are read.
     * @return the seed for this cell, or an empty stack if the field has no seeds at all.
     */
    @NotNull
    private ItemStack seedFor(@NotNull final FarmField farmField, @NotNull final BlockPos position)
    {
        final List<ItemStack> seeds = farmField.getSeeds();
        if (seeds.size() <= 1)
        {
            return farmField.getSeed();
        }
        final ItemStack seed = seeds.get(Math.floorMod(position.getX() + position.getZ(), seeds.size()));
        seed.setCount(1);
        return seed;
    }

    /**
     * Check if itemStack can be used as compost.
     *
     * @param itemStack the stack to check.
     * @return true if so.
     */
    private boolean isCompost(final ItemStack itemStack)
    {
        if (itemStack.getItem() == ModItems.compost)
        {
            return true;
        }
        return itemStack.getItem() == Items.BONE_MEAL;
    }

    /**
     * Handles the offset of the field for the farmer. Checks if the field needs a certain operation checked with a given predicate.
     *
     * @param farmField the field object.
     * @param predicate the predicate to test.
     * @return true if a harvestable crop was found.
     */
    private boolean checkIfShouldExecute(@NotNull final FarmField farmField, @NotNull final Predicate<BlockPos> predicate)
    {
        BlockPos position;
        do
        {
            building.setWorkingOffset(nextValidCell(farmField));
            if (building.getWorkingOffset() == null)
            {
                return false;
            }

            position = farmField.getPosition().below().south(building.getWorkingOffset().getZ()).east(building.getWorkingOffset().getX());
        }
        while (!predicate.test(position));

        return true;
    }

    /**
     * Checks if the farmer is ready to plant.
     *
     * @param farmField the field to plant.
     * @return the next AI state.
     */
    private IAIState canGoPlanting(@NotNull final FarmField farmField)
    {
        final List<ItemStack> seeds = farmField.getSeeds();
        if (seeds.isEmpty())
        {
            // Upstream tested "getSeed() == null" here, which never fires: the field initialises its seed to
            // ItemStack.EMPTY, setSeed copies, deserialization falls back to EMPTY and getSeed is @NotNull. An
            // empty seed therefore fell through to the request below, where checkIfRequestForItemExistOrCreate
            // answers "yes, you have it" for an empty stack without asking anybody for anything
            // (AbstractEntityAIBasic#checkIfRequestForItemExistOrCreate, first line). So nextState was not
            // reached either, and this method returned PREPARING on every tick for ever, with the module still
            // pointing at this field - which meant the worker not only never planted this field, it never
            // touched any other field it owns. That is the "tilled and prepared everything, planted nothing"
            // shape, and nothing said so: the job status had already been set to WORKING before we got here,
            // and the only place the missing seed showed up was a hover tooltip in the hut's field list.
            //
            // A field cannot be *assigned* without a seed, but it can be cleared afterwards in the scarecrow
            // window, and the assignment is not revisited. So say it, and let the field go so the worker gets
            // on with the rest of its work.
            if (worker.getCitizenData() != null)
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(
                  Component.translatableEscape(FIELD_NO_SEED, farmField.getPosition().toShortString()), ChatPriority.IMPORTANT));
            }
            building.getFirstModuleOccurance(BuildingExtensionsModule.class).resetCurrentExtension();
            return IDLE;
        }

        // Every seed the field carries has to be plantable, not merely one of them. A mixed field whose third seed
        // is pitcher_pod would otherwise sow two thirds of its ground and leave the last third bare for ever, and
        // say nothing about it - which is the exact failure the single seed check below was written to stop.
        final ItemStack unplantable = seeds.stream().filter(seed -> !isPlantableSeed(seed)).findFirst().orElse(ItemStack.EMPTY);
        if (!unplantable.isEmpty())
        {
            // The scarecrow window offers anything in #c:seeds as well as every crop block item
            // (WindowField#selectSeed), but plantCrop can only put a CropBlock, a StemBlock or a
            // MinecoloniesCropBlock in the ground - and when it cannot, it returns "carry on" rather than "no", so
            // every cell is skipped in silence, the pass finishes, the stage advances as though the field had been
            // sown, and the whole thing repeats next cycle. minecraft:pitcher_pod is the one that reaches this in
            // vanilla: it is in #c:seeds, so the window offers it, but PitcherCropBlock extends DoublePlantBlock,
            // not CropBlock. A field set to it is tilled for ever and never planted, which is exactly the shape
            // that is hardest to report as a bug because nothing anywhere says anything.
            if (worker.getCitizenData() != null)
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(
                  Component.translatableEscape(FIELD_BAD_SEED, unplantable.getHoverName(), farmField.getPosition().toShortString()),
                  ChatPriority.IMPORTANT));
            }
            building.getFirstModuleOccurance(BuildingExtensionsModule.class).resetCurrentExtension();
            return IDLE;
        }

        // All of them, not any of them. The cell a seed is destined for is fixed by the cell's own coordinates
        // (see seedFor), so setting off with two seeds out of three does not sow two thirds of the field and stop -
        // it sows two thirds of it and leaves a third of the cells bare, scattered through the whole plot, and the
        // stage then advances as though the field were sown. Waiting until the farmer has all of them keeps a mixed
        // field's planting pass the same all-or-nothing thing a single seed field's pass has always been; what is
        // missing is requested below and the field is handed back so the worker gets on with its other fields.
        final List<ItemStack> missing = new ArrayList<>();
        for (final ItemStack seed : seeds)
        {
            if (worker.getCitizenInventoryHandler().findFirstSlotInInventoryWith(seed.getItem()) == -1)
            {
                missing.add(seed);
            }
        }
        if (missing.isEmpty())
        {
            return FARMER_PLANT;
        }

        if (worksWithoutMaterials())
        {
            // Free mode already covers the seed, through the one mechanism the rest of this port uses: the walk
            // below ends at checkIfRequestForItemExistOrCreate, whose free mode branch
            // (AbstractEntityAIBasic#checkIfRequestForItemExistOrCreate, the worksWithoutMaterials block) hands
            // the stack over out of thin air. So this is not a second mechanism, it is the same helper called one
            // step earlier - the item is conjured straight into the worker's own inventory, so the trip to the hut
            // buys nothing, and on a field larger than one stack of seed the farmer was walking back for it every
            // 64 cells. EntityAIWorkPlanter#checkIfItemsUnavailable is the existing precedent for supplying at the
            // point of need where the shared chokepoint is behind something.
            //
            // Nothing here is reachable outside free mode: worksWithoutMaterials is IBuilding#worksWithoutMaterials,
            // which is FreeMode#isOn and nothing else. It supplies only the field's own configured seeds, which is
            // precisely what the chokepoint would have supplied a moment later.
            missing.removeIf(seed -> {
                supplyMaterialWithoutRequest(seed.copy(), seed.getMaxStackSize());
                return worker.getCitizenInventoryHandler().findFirstSlotInInventoryWith(seed.getItem()) != -1;
            });
            if (missing.isEmpty())
            {
                return FARMER_PLANT;
            }
            // Could not be handed over - the worker's inventory is full. Fall through: the walk and the request
            // below will try again and, still failing, release the field so the dump can happen.
        }

        if (!walkToBuilding())
        {
            return PREPARING;
        }

        boolean allSatisfied = true;
        for (final ItemStack seed : missing)
        {
            final ItemStack request = seed.copy();
            request.setCount(request.getMaxStackSize());
            // Deliberately not short circuited: every missing seed has to get its own request filed, or a mixed
            // field would ask for its first missing seed, be refused, and never ask for the second at all.
            allSatisfied &= checkIfRequestForItemExistOrCreateAsync(request, request.getMaxStackSize(), 1);
        }
        if (!allSatisfied)
        {
            // Out of seed, and the hut has none either; a request has just been filed by the call above.
            //
            // Upstream called farmField.nextState() here. That does stop the worker wedging on a field it
            // cannot sow, which is why it is there - but it advances the field HOED -> PLANTED, recording a
            // field that was sown half way, or not at all, as fully sown. The rest of it is then not offered
            // again until the field has been all the way round PLANTED -> EMPTY -> HOED, which is at best two
            // more colony days, and if the seed runs dry again on that pass the same thing happens again. A
            // farm whose seed arrives in dribs and drabs - which is every farm whose crop is also food, since
            // the rest of the colony eats it - therefore never fills in, and what is planted ends up scattered
            // rather than growing outwards, because each pass stops wherever the seed ran out.
            //
            // Releasing the field instead keeps the anti-wedge property (the module hands the worker its other
            // fields, or nothing, for the rest of the day) without the lie: the field stays HOED and is sown
            // the next time it comes round, by which time the request has been served.
            building.getFirstModuleOccurance(BuildingExtensionsModule.class).resetCurrentExtension();
        }
        return PREPARING;
    }

    /**
     * Checks if the ground should be hoed and the block above removed.
     *
     * @param position  the position to check.
     * @param farmField the field close to this position.
     * @return position of hoeable surface or null if there is not one
     */
    private BlockPos findHoeableSurface(@NotNull BlockPos position, @NotNull final FarmField farmField)
    {
        position = getSurfacePos(position);
        if (position == null)
        {
            return null;
        }
        // getSurfacePos only moves up or down, so the surface it found is in the same column and has the same X and
        // Z the cell was asked about - which is all seedFor reads.
        final ItemStack cellSeed = seedFor(farmField, position);
        final BlockState blockState = world.getBlockState(position);
        if (farmField.isNoPartOfField(world, position)
              || (world.getBlockState(position.above()).getBlock() instanceof CropBlock)
              || (world.getBlockState(position.above()).getBlock() instanceof BlockScarecrow)
              // PORT-NOTE(26.2/Fabric): was BlockTags.DIRT. 26.2 split the vanilla #minecraft:dirt tag apart:
              // it now holds only dirt, coarse_dirt and rooted_dirt, while grass_block, podzol, mycelium, mud,
              // muddy_mangrove_roots and the moss blocks -- everything a field is normally laid on -- moved into
              // #minecraft:grass_blocks, #minecraft:mud and #minecraft:moss_blocks. Carrying the old constant
              // across therefore silently stopped matching grass, and the farmer tilled nothing.
              // #minecraft:substrate_overworld is the union of all four. Diffed against the 1.21.1 server jar:
              // it loses nothing #minecraft:dirt held there and gains exactly one block, pale_moss_block, which
              // did not exist in 1.21.1. Note dirt_path is in neither, so the farmer still will not till paths,
              // as on 1.21.1 -- restoring the old set, not widening it.
              || (!blockState.is(BlockTags.SUBSTRATE_OVERWORLD) && !(blockState.getBlock() instanceof MinecoloniesFarmland) && !(blockState.getBlock() instanceof FarmlandBlock))
              ||  isRightFarmLandForCrop(cellSeed, blockState)
              || (world.getBlockState(position.above()).getBlock() instanceof MinecoloniesCropBlock)
        )
        {
            return null;
        }

        final BlockState aboveState = world.getBlockState(position.above());
        if (aboveState.canBeReplaced() && !(aboveState.getBlock() instanceof MinecoloniesCropBlock))
        {
            world.destroyBlock(position.above(), true);
        }

        if (!isRightFarmLandForCrop(cellSeed, blockState))
        {
            return position;
        }

        // PORT-NOTE(26.2/Fabric): BlockState#getToolModifiedState(ctx, ItemAbilities.HOE_TILL, true)
        // was a NeoForge extension. Vanilla's equivalent table, HoeItem.TILLABLES, is protected and
        // only grass / dirt path / dirt turn into farmland, and only with air above — inlined here.
        //
        // Everything below is UNREACHABLE, here and on 1.21.1 alike: the guard above returns null when
        // isRightFarmLandForCrop is true, so the check at 404 is always taken and this method has already
        // returned. Kept only to stay diffable against 1.21.1, which carries the same dead tail. In
        // particular it does NOT enforce hoe ownership -- hoeIfAble and prepareForFarming do that.
        if (InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(getInventory(),
              ModEquipmentTypes.hoe.get(),
              TOOL_LEVEL_WOOD_OR_GOLD,
              building.getMaxEquipmentLevel()) == -1)
        {
            return null;
        }

        final boolean tillsIntoFarmland =
          (blockState.is(Blocks.GRASS_BLOCK) || blockState.is(Blocks.DIRT_PATH) || blockState.is(Blocks.DIRT))
            && world.getBlockState(position.above()).isAir();
        if (!tillsIntoFarmland)
        {
            return null;
        }

        return position;
    }

    /**
     * Whether the cell's ground has to be replaced before anything can be tilled there, and if so which block is in the
     * way.
     * <p>
     * This is the counterpart of {@link #findHoeableSurface}: it answers for exactly the cells that one rejects on the
     * grounds of the block being the wrong material. A field is drawn by hand, so a cell is inside it because somebody
     * put it there, and the material found there is not a reason to refuse - stone, gravel, sand, a path, a wall, a
     * floor, whatever it is, it goes and ground goes down in its place. There is deliberately no list of protected
     * materials; see FARMER-TERRAFORM.md.
     * <p>
     * Three things it will not do, and all three are for a reason rather than out of caution:
     * <ul>
     *     <li><b>A block that carries data is left alone</b> - anything with a {@link net.minecraft.world.level.block.entity.BlockEntity}
     *     behind it, which is every container in the game and every rack, furnace, sign and spawner besides. Laying
     *     ground over one of those does not move its contents anywhere the player can reach them again, and unlike a
     *     wall it cannot be put back by placing the same block. This is the same kind of guard as the
     *     {@code IBuilderUndestroyable} test below, not a judgement about whose block it is.</li>
     *     <li><b>Anything holding a fluid is left alone</b> - the surface block itself and the block above it are both
     *     tested with {@link net.minecraft.world.level.Level#getFluidState}, which is non-empty for a source, for
     *     flowing water and for a waterlogged block alike. Crops do not grow in a flooded cell, so filling a pond in
     *     would cost the player a pond and gain nothing. Lava falls under the same test.</li>
     *     <li><b>The terrain is not reshaped.</b> {@link #getSurfacePos} finds the surface of the column, and the
     *     replacement happens exactly there. The farmer changes what the ground is made of, never where it is: it does
     *     not fill holes, does not flatten hills and does not dig. A cell whose surface sits three blocks down becomes
     *     three blocks of dirt down, the same as a cell of grass three blocks down is tilled where it lies today.</li>
     * </ul>
     *
     * @param position  the position to check.
     * @param farmField the field close to this position.
     * @return the position whose block has to be replaced, or null if nothing has to be.
     */
    @Nullable
    private BlockPos findGroundToPrepare(@NotNull BlockPos position, @NotNull final FarmField farmField)
    {
        if (!MinecoloniesAPIProxy.getInstance().getConfig().getServer().farmerPreparesGround.get())
        {
            return null;
        }

        position = getSurfacePos(position);
        if (position == null || farmField.isNoPartOfField(world, position))
        {
            return null;
        }

        final BlockState blockState = world.getBlockState(position);
        if (blockState.is(BlockTags.SUBSTRATE_OVERWORLD)
              || blockState.getBlock() instanceof MinecoloniesFarmland
              || blockState.getBlock() instanceof FarmlandBlock
              || blockState.getBlock() instanceof BlockScarecrow)
        {
            // Ground the farmer can already work with, or the field's own scarecrow. findHoeableSurface owns these.
            return null;
        }

        if (blockState.hasBlockEntity())
        {
            // A container, a rack, a furnace, a sign - something that holds more than its own block state. A wall the
            // farmer takes down can be put back by placing the same block again; what was inside a chest cannot.
            return null;
        }

        if (!world.getFluidState(position).isEmpty() || !world.getFluidState(position.above()).isEmpty())
        {
            return null;
        }

        final Block above = world.getBlockState(position.above()).getBlock();
        if (above instanceof CropBlock || above instanceof MinecoloniesCropBlock || above instanceof BlockScarecrow)
        {
            // Something is already growing on top of this cell, or the scarecrow stands on it.
            return null;
        }

        return position;
    }

    /**
     * Clear a cell the farmer cannot till and lay ground down in its place, so that the ordinary hoeing path can carry
     * on with it in the same visit.
     * <p>
     * Where the ground comes from is the one place free mode changes the behaviour rather than merely the limits:
     * <ul>
     *     <li><b>free mode on</b> - the block simply becomes dirt, no item is consumed and nothing is requested. That
     *     is what free mode already means everywhere else in this branch ({@link FreeMode});</li>
     *     <li><b>free mode off</b> - one block of ground is taken out of the worker's own inventory, and if it carries
     *     none a request for dirt is filed through the same request system that brings it hoes and compost. The cell is
     *     then skipped, not blocked: the farmer waits for the dirt to arrive over the following passes rather than
     *     creating it, and the rest of the field is worked meanwhile.</li>
     * </ul>
     *
     * @param position  the cell position, before the surface search.
     * @param farmField the field close to this position.
     * @return true if the farmer may carry on with this cell, false if it is still busy clearing and has to be called
     *         again with the same cell.
     */
    private boolean prepareGroundIfNeeded(@NotNull final BlockPos position, @NotNull final FarmField farmField)
    {
        final BlockPos toPrepare = findGroundToPrepare(position, farmField);
        if (toPrepare == null)
        {
            return true;
        }

        final BlockState toClear = world.getBlockState(toPrepare);
        if (toClear.getBlock() instanceof IBuilderUndestroyable || toClear.is(Blocks.BEDROCK))
        {
            // Not a judgement about player property - mineBlock reports success on these without removing anything
            // (AbstractEntityAIInteract#mineBlock), so laying ground down here would overwrite a hut block rather than
            // replace it. Skip the cell instead.
            return true;
        }

        final boolean free = FreeMode.isOn(building);
        if (!free && InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(getInventory(), this::isGroundFiller) == -1)
        {
            needsGroundFiller = true;
            checkIfRequestForItemExistOrCreateAsync(new ItemStack(Items.DIRT, STACKSIZE), STACKSIZE, 1);
            return true;
        }

        final int toolSlot = getMostEfficientTool(toClear, toPrepare);
        if (toolSlot < 0 && toolSlot != NO_TOOL && !worksWithoutTools())
        {
            // The farmer carries nothing that can take this block. Ask for one and leave the cell for a later pass.
            // Deliberately the *async* request and not the synchronous one mineBlock would reach on its own through
            // holdEfficientTool: a synchronous tool request pins the worker in NEEDS_ITEM until it is served, so a
            // single chest inside a field would stop the whole farm, harvest included. Observed on the test server
            // before this branch was taken - the farmer asked for an axe over one chest and did nothing further.
            final EquipmentTypeEntry toolType =
              WorkerUtil.getBestToolForBlock(toClear, toClear.getDestroySpeed(world, toPrepare), building, world, toPrepare);
            final int required = WorkerUtil.getCorrectHarvestLevelForBlock(toClear);
            neededGroundTools.merge(toolType, required, Math::max);
            checkForToolOrWeaponAsync(toolType, required, Math.max(required, building.getMaxEquipmentLevel()));
            return true;
        }

        if (!mineBlock(toPrepare))
        {
            return false;
        }

        BlockState ground = Blocks.DIRT.defaultBlockState();
        if (!free)
        {
            // Looked up again rather than reused: mineBlock takes several calls to return true and puts the drops into
            // this same inventory in between.
            final int slot = InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(getInventory(), this::isGroundFiller);
            if (slot == -1)
            {
                return true;
            }
            final ItemStack filler = getInventory().extractItem(slot, 1, false);
            if (filler.getItem() instanceof final BlockItem blockItem)
            {
                ground = blockItem.getBlock().defaultBlockState();
            }
        }

        world.setBlockAndUpdate(toPrepare, ground);
        clearedGround.merge(toClear.getBlock(), 1, Integer::sum);
        needsGroundFiller = false;
        didWork = true;
        return true;
    }

    /**
     * Tell the colony what the farmer cleared away, once per pass over a field.
     * <p>
     * The field is whatever the player drew, and the farmer will empty it without asking, so the one thing owed back is
     * that it be visible: somebody who mis-draws a rectangle over the corner of their base should read about it rather
     * than find out later. This is feedback, not a confirmation - nothing waits on it.
     *
     * @param farmField the field that was just walked.
     */
    private void reportPreparedGround(@NotNull final FarmField farmField)
    {
        if (clearedGround.isEmpty())
        {
            return;
        }

        int total = 0;
        MutableComponent detail = Component.empty();
        for (final Map.Entry<Block, Integer> entry : clearedGround.entrySet())
        {
            if (total > 0)
            {
                detail = detail.append(Component.literal(", "));
            }
            detail = detail.append(entry.getKey().getName()).append(Component.literal(" x" + entry.getValue()));
            total += entry.getValue();
        }
        clearedGround.clear();

        MessageUtils.format(FIELD_GROUND_PREPARED, worker.getCitizenData().getName(), total, farmField.getPosition().toShortString(), detail)
          .sendTo(building.getColony()).forAllPlayers();
    }

    /**
     * Whether a stack is something the farmer can lay down as ground.
     * <p>
     * Anything in {@code #minecraft:substrate_overworld}, which is the same tag {@link #findHoeableSurface} accepts as
     * tillable, so whatever is put in the hut can also be tilled once it is down. Dirt is what gets requested.
     *
     * @param stack the stack to check.
     * @return true if so.
     */
    private boolean isGroundFiller(final ItemStack stack)
    {
        return !stack.isEmpty()
                 && stack.getItem() instanceof final BlockItem blockItem
                 && blockItem.getBlock().defaultBlockState().is(BlockTags.SUBSTRATE_OVERWORLD);
    }

    /**
     * Finds the position of the surface near the specified position
     *
     * @param position the location to begin the search
     * @return the position of the surface block or null if it can't be found
     */
    private BlockPos getSurfacePos(final BlockPos position)
    {
        return getSurfacePos(position, 0);
    }

    /**
     * Finds the position of the surface near the specified position
     *
     * @param position the location to begin the search
     * @param depth    the depth of the search for the surface
     * @return the position of the surface block or null if it can't be found
     */
    private BlockPos getSurfacePos(final BlockPos position, final Integer depth)
    {
        if (Math.abs(depth) > MAX_DEPTH || !WorldUtil.isBlockLoaded(world, position))
        {
            return null;
        }
        final BlockState curBlockState = world.getBlockState(position);
        @Nullable final Block curBlock = curBlockState.getBlock();
        if ((curBlockState.isSolid() && !(curBlock instanceof PumpkinBlock) && curBlock != Blocks.MELON && !(curBlock instanceof WebBlock)) || curBlockState.liquid())
        {
            if (depth < 0)
            {
                return position;
            }
            return getSurfacePos(position.above(), depth + 1);
        }
        else
        {
            if (depth > 0)
            {
                return position.below();
            }
            return getSurfacePos(position.below(), depth - 1);
        }
    }

    /**
     * Fetch the next available block within the field. Uses mathematical quadratic equations to determine the coordinates by an index. Considers max radii set in the field gui.
     * <p>
     * The cell index runs along a square spiral around the scarecrow, so it enumerates the field's <b>bounding
     * square</b> and most of those cells are outside a field that is not square. Upstream simply stepped the index by
     * one and re-tested, which is fine while the bounding square is at most 41x41 but is not fine at all once free
     * mode allows a long thin field: a 1x4096 strip has a bounding square of 8191x8191, and stepping through its
     * 67 million cells one at a time - which {@code checkIfShouldExecute} does inside a single tick - stalls the
     * server for seconds.
     * <p>
     * So the walk below skips instead of stepping. Each ring is four sides; on a side one coordinate is fixed at
     * plus or minus the ring and the other moves linearly with the step. If the fixed one is outside the field the
     * whole side is skipped in one go, and otherwise the run of steps that keeps the moving one inside the field is
     * solved for directly. The <b>order of the cells actually returned is unchanged</b> - the same spiral, with the
     * cells that were always going to be rejected jumped over - so nothing about how the farmer walks a normal field
     * changes.
     *
     * @return the new offset position
     */
    protected BlockPos nextValidCell(FarmField farmField)
    {
        if (building.getWorkingOffset() == null)
        {
            building.setCell(-1);
        }

        final int north = farmField.getRadius(Direction.NORTH);
        final int south = farmField.getRadius(Direction.SOUTH);
        final int east = farmField.getRadius(Direction.EAST);
        final int west = farmField.getRadius(Direction.WEST);
        final int largestCell = getLargestCell(farmField);

        // Was ==. The bound is no longer a constant, so the shared cell counter can already be past the bound of
        // whichever field is being walked now (a big field worked first, a small one second): an equality test would
        // never fire and the loop would spin forever.
        int cell = building.getCell() + 1;
        while (cell < largestCell)
        {
            final int ring = Math.max(1, (int) Math.floor((Math.sqrt(cell + 1D) + 1) / 2.0));
            final int sideLength = 2 * ring;
            final int ringStart = 4 * ring * ring - 4 * ring;
            final int side = Math.floorDiv(cell - ringStart, sideLength);
            final int sideStart = ringStart + side * sideLength;
            final int step = cell - sideStart;

            // The side the spiral is on, in Direction#get2DDataValue order: south, west, north, east. On the first
            // two the moving coordinate counts down from the ring, on the other two it counts up towards it.
            final boolean fixedInField;
            final int firstStep;
            final int lastStep;
            switch (side)
            {
                case 0 ->
                {
                    // z = ring, x = ring - step
                    fixedInField = ring <= south;
                    firstStep = ring - east;
                    lastStep = ring + west;
                }
                case 1 ->
                {
                    // x = -ring, z = ring - step
                    fixedInField = ring <= west;
                    firstStep = ring - south;
                    lastStep = ring + north;
                }
                case 2 ->
                {
                    // z = -ring, x = step - ring
                    fixedInField = ring <= north;
                    firstStep = ring - west;
                    lastStep = ring + east;
                }
                default ->
                {
                    // x = ring, z = step - ring
                    fixedInField = ring <= east;
                    firstStep = ring - north;
                    lastStep = ring + south;
                }
            }

            if (!fixedInField || step > lastStep || lastStep < 0 || firstStep > sideLength - 1)
            {
                // Nothing left on this side. Jump to the start of the next one, which for the fourth side is the
                // start of the next ring.
                cell = sideStart + sideLength;
                continue;
            }

            if (step < firstStep)
            {
                cell = sideStart + firstStep;
                continue;
            }

            building.setCell(cell);
            return switch (side)
            {
                case 0 -> new BlockPos(ring - step, 0, ring);
                case 1 -> new BlockPos(-ring, 0, ring - step);
                case 2 -> new BlockPos(step - ring, 0, -ring);
                default -> new BlockPos(ring, 0, step - ring);
            };
        }

        building.setCell(largestCell);
        return null;
    }

    /**
     * How many cells of the square spiral {@link #nextValidCell} has to walk before every cell of the field has been
     * offered at least once.
     * <p>
     * The spiral covers the bounding square of the field, so the answer is the square whose half width is the largest
     * of the four radii. This used to be the constant {@code (MAX_RANGE * 2 + 1)^2}, which was correct only while no
     * radius could exceed {@code MAX_RANGE}: with free mode on, a field reaching further than twenty blocks in any
     * direction would have had everything beyond ring twenty silently ignored, and the farmer would have worked a
     * 41x41 patch of a 4x1000 strip and called the field done.
     * <p>
     * For an ordinary field this is now smaller than the old constant rather than larger - a default 11x11 field
     * costs 121 spiral steps instead of 1681 - so it is also a small saving on every field that already existed.
     *
     * @param farmField the field being walked.
     * @return one past the last cell index of the spiral.
     */
    protected int getLargestCell(FarmField farmField)
    {
        int maxRadius = 0;
        for (final Direction direction : Direction.Plane.HORIZONTAL)
        {
            maxRadius = Math.max(maxRadius, farmField.getRadius(direction));
        }
        return (int) Math.pow(maxRadius * 2D + 1D, 2);
    }

    /**
     * This (re)initializes a field. Checks the block above to see if it is a plant, if so, breaks it. Then tills.
     *
     * @return the next state to go into.
     */
    private IAIState workAtField()
    {
        final BuildingExtensionsModule module = building.getFirstModuleOccurance(BuildingExtensionsModule.class);
        final IBuildingExtension field = module.getCurrentExtension();

        worker.getCitizenData().setVisibleStatus(FARMING_ICON);
        if (field instanceof FarmField farmField)
        {
            if (building.getWorkingOffset() != null)
            {
                final BlockPos position = farmField.getPosition().below().south(building.getWorkingOffset().getZ()).east(building.getWorkingOffset().getX());

                if (!cellWantsWork(position, farmField))
                {
                    // Nothing to do here, so do not walk here. This is the same question the stage's own predicate in
                    // prepareForFarming asks before the pass starts, asked once per cell instead of once per pass.
                    //
                    // Measured on a 21x21 field, one worker, flat ground: without this the worker takes 2.5 to 2.9
                    // seconds per cell whether or not the cell wants anything, because it walks to every one of them
                    // to find out. Of that, about two seconds is the deliberate getLevelDelay pause and the rest is
                    // walking. With it, a cell that wants nothing costs one AI tick - 0.29 s per cell measured over
                    // a field of ready farmland, an 8.6x difference - and a cell that wants something is unaffected.
                    // That is most of the field on most passes: the field is walked three times per crop cycle, once
                    // per stage, and outside the first tilling pass almost every cell is already in the state that
                    // stage wants.
                    //
                    // It cannot skip a cell that wanted work: the test is the same finder the work step then calls,
                    // and where a cheap finder would be unsafe to call twice - harvesting, which fertilises and grows
                    // the crop as a side effect - it does not call the finder at all and only asks whether anything is
                    // growing there, which is a necessary condition for harvesting and nothing more.
                    //
                    // prevPos is still advanced, even though nothing was done here: it means "the cell looked at
                    // last", and plantCrop reads it for the melon and pumpkin spacing rule, which would otherwise
                    // start seeing a different sequence of cells than it saw before this skip existed.
                    building.setPrevPos(position);
                    building.setWorkingOffset(nextValidCell(farmField));
                    return building.getWorkingOffset() == null ? finishPass(module, farmField) : getState();
                }

                // Still moving to the block. Not asked when the worker is already standing near enough to work the
                // cell: EntityNavigationUtils#walkToPos only treats a *new* destination as already reached inside
                // REACHED_DIST, which is 1.5 blocks and so is false for almost every next cell even though the two
                // cells are one block apart, and it then orders a fresh walk and a fresh A* search. Arrival at the
                // same cell is judged at four blocks by walkToSafePos itself, so working from within two is well
                // inside the tolerance this code already accepts.
                if (!isWithinFieldReach(position.above()) && !walkToSafePos(position.above()))
                {
                    return getState();
                }
                equipHoe();

                // One walk, up to batchSize() cells. The delay below is what this loop exists for: setDelay parks the
                // whole AI for getLevelDelay() ticks (AbstractEntityAIBasic#waitingForSomething counts it down by the
                // tick rate and refuses to run the target until it reaches zero), and it is paid once per call of this
                // method. One call worked one cell, so a cell cost the delay plus its share of the walking. Working
                // several cells before returning pays the delay once for all of them.
                //
                // Outside free mode batchSize() is 1 and every line below behaves exactly as it did: work the cell,
                // advance the offset, set the delay, return.
                BlockPos current = position;
                int remaining = batchSize();
                while (true)
                {
                    final IAIState interrupted = workCell(current, farmField);
                    if (interrupted != null)
                    {
                        // The cell is not finished - mineBlock wants another call, or the sowing ran out of seed. The
                        // working offset is deliberately left where it is so the next call comes back to this cell.
                        return interrupted;
                    }
                    building.setPrevPos(current);

                    building.setWorkingOffset(nextValidCell(farmField));
                    if (building.getWorkingOffset() == null)
                    {
                        setDelay(getLevelDelay());
                        return finishPass(module, farmField);
                    }

                    if (--remaining <= 0)
                    {
                        break;
                    }

                    current = farmField.getPosition().below().south(building.getWorkingOffset().getZ()).east(building.getWorkingOffset().getX());
                    if (!canJoinBatch(current, farmField))
                    {
                        // Left as the working offset, unworked, for the ordinary path to pick up next call.
                        break;
                    }
                }

                setDelay(getLevelDelay());
                return getState();
            }

            building.setWorkingOffset(nextValidCell(farmField));
            if (building.getWorkingOffset() == null)
            {
                return finishPass(module, farmField);
            }
        }
        else
        {
            return IDLE;
        }
        return getState();
    }

    /**
     * Do whatever the current stage wants done to one cell.
     * <p>
     * Lifted out of {@link #workAtField} so that the batching loop and the single-cell path are the same code. The
     * return convention is the one the switch used before it was a method: null means the cell is finished and the
     * caller may move on, anything else is the AI state to hand back at once, leaving the working offset alone so the
     * next call comes back to this same cell.
     *
     * @param position  the cell position, before the surface search.
     * @param farmField the field being walked.
     * @return null if the cell is done, else the state to return.
     */
    @Nullable
    private IAIState workCell(@NotNull final BlockPos position, @NotNull final FarmField farmField)
    {
        switch ((AIWorkerState) getState())
        {
            case FARMER_HOE ->
            {
                if (!hoeIfAble(position, farmField))
                {
                    return getState();
                }
            }
            case FARMER_PLANT ->
            {
                // Repair the cell before trying to sow it. A cell whose farmland has gone back to dirt -- trampled by
                // whoever walked over it, or dried out on its own, which vanilla does to any unplanted farmland that
                // has no water within four blocks on the first random tick it gets -- is not plantable, and without
                // this it would be left for the next time the field reaches the EMPTY stage, a whole field cycle away.
                // That was never a repair on a dry field: the EMPTY pass hoes the cell and then hands the field on,
                // and hoeing and sowing are a colony day apart, so the cell dried out again before the sowing pass
                // reached it and the loop repeated for ever. Tilling here closes that gap -- the cell is tilled and
                // sown in the same visit, and the crop then keeps the farmland alive through
                // #minecraft:maintains_farmland.
                if (!tillIfHoeable(position, farmField))
                {
                    return getState();
                }
                if (!tryToPlant(farmField, position))
                {
                    return PREPARING;
                }
            }
            case FARMER_HARVEST ->
            {
                if (!harvestIfAble(position))
                {
                    return getState();
                }
            }
            default ->
            {
                return PREPARING;
            }
        }
        return null;
    }

    /**
     * How many cells the farmer may work before it has to stop and take its delay again.
     * <p>
     * One outside free mode, which is every colony that has not deliberately turned the switch on, and there the
     * batching loop in {@link #workAtField} collapses to exactly the code that was there before it. In free mode it is
     * the configured {@code freemodefarmerbatchsize}. Read at the point of use and nothing is written anywhere, so
     * turning free mode off leaves no trace of it - the same shape the rest of the free mode behaviour in this class
     * uses ({@link FreeMode#isOn(IBuilding)} and nothing else).
     *
     * @return the number of cells one visit may work.
     */
    private int batchSize()
    {
        if (!FreeMode.isOn(building))
        {
            return 1;
        }
        return Math.max(1, MinecoloniesAPIProxy.getInstance().getConfig().getServer().freeModeFarmerBatchSize.get());
    }

    /**
     * Whether the next cell in the walk may be worked from where the citizen is standing, without walking to it.
     * <p>
     * Three things have to hold, and all three are about not quietly doing something the single cell path would have
     * refused:
     * <ul>
     *     <li>it has to want work at all, which is {@link #cellWantsWork}, the same test the ordinary path makes;</li>
     *     <li>it has to be within {@link #batchSize()} blocks of the citizen. That is the configured number used as a
     *     radius as well as a count, which is what makes a run of that many cells along a row reachable and nothing
     *     further: the last cell of a run of N is N-1 blocks along, and the citizen stands a block above the row.
     *     <p>
     *     The nine ring transitions of the spiral - the only steps in the whole walk that are not to an adjacent
     *     cell - are a (1,2) jump, so sqrt(5) which is about 2.24 blocks, and at the default batch of three they are
     *     inside the radius rather than outside it. So a batch <em>reaches</em> across a ring transition rather than
     *     stopping at one, deliberately: the radius is the property that keeps this honest, not adjacency, and a cell
     *     2.24 blocks away is no further than the second cell of an ordinary straight run. At a batch of two they
     *     fall outside and the batch stops there instead. Either way no cell is skipped - whatever is not batched is
     *     left as the working offset for the next call;</li>
     *     <li>the citizen has to be able to see it. Reaching through a wall is the one way a bigger radius could do
     *     something a player could not, and a field with a wall across it is a case the ground preparation code
     *     already knows about, so it is worth the raycast rather than assuming fields are flat and open.</li>
     * </ul>
     *
     * @param position  the cell position, before the surface search.
     * @param farmField the field being walked.
     * @return true if the cell may be worked without moving.
     */
    private boolean canJoinBatch(@NotNull final BlockPos position, @NotNull final FarmField farmField)
    {
        final BlockPos target = position.above();
        if (BlockPosUtil.dist(worker.blockPosition(), target) > batchSize())
        {
            return false;
        }
        if (!cellWantsWork(position, farmField))
        {
            return false;
        }
        return hasLineOfSightTo(target);
    }

    /**
     * Whether nothing solid stands between the citizen's eyes and a block it wants to work.
     *
     * @param target the block to look at.
     * @return true if the way is clear.
     */
    private boolean hasLineOfSightTo(@NotNull final BlockPos target)
    {
        final Vec3 from = worker.getEyePosition();
        final Vec3 to = Vec3.atCenterOf(target);
        final BlockHitResult hit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, worker));
        // A miss means the ray reached the far end without being stopped. Hitting the target cell itself, or the
        // ground it sits on, is also clear: those are the blocks being worked.
        return hit.getType() == HitResult.Type.MISS
                 || hit.getBlockPos().equals(target)
                 || hit.getBlockPos().equals(target.below());
    }

    /**
     * Close a pass over a field: report, hand the field on to its next stage and let go of it.
     * <p>
     * Lifted verbatim out of the tail of {@link #workAtField} so that the skip in the middle of that method, which
     * also has to be able to run off the end of the field, ends the pass in exactly the same way rather than in a
     * second copy of it.
     *
     * @param module    the field module.
     * @param farmField the field just walked.
     * @return the next AI state.
     */
    private IAIState finishPass(@NotNull final BuildingExtensionsModule module, @NotNull final FarmField farmField)
    {
        reportPreparedGround(farmField);
        shouldDumpInventory = true;
        farmField.nextState();
        module.markDirty();
        if (didWork || ++skippedState >= 4)
        {
            module.resetCurrentExtension();
            skippedState = 0;
        }
        didWork = false;
        building.setPrevPos(null);
        return IDLE;
    }

    /**
     * Whether the cell has anything the current stage would actually do, asked before the worker walks to it.
     * <p>
     * Every branch is the same question the stage's work step asks a moment later, with one deliberate exception:
     * harvesting. {@link #findHarvestableSurface} is <b>not</b> side effect free - it spends compost and grows the
     * crop - so asking it here would fertilise from a distance and then fertilise again on arrival. Instead this asks
     * only whether anything is growing on the cell, which every harvestable cell satisfies, so a cell that could have
     * been harvested is never skipped; a cell with a crop that is not ready yet is walked to exactly as before.
     * <p>
     * The other finders are safe to ask twice. {@link #findGroundToPrepare} and {@link #findPlantableSurface} only
     * read the world. {@link #findHoeableSurface} does clear a replaceable block off the top - but only after every
     * guard has passed, which is to say only on a cell it is about to return, so the only cells it touches are cells
     * the worker then walks to anyway.
     *
     * @param position  the cell position, before the surface search.
     * @param farmField the field being walked.
     * @return true if the worker should go to this cell.
     */
    private boolean cellWantsWork(@NotNull final BlockPos position, @NotNull final FarmField farmField)
    {
        return switch ((AIWorkerState) getState())
        {
            case FARMER_HOE -> findHoeableSurface(position, farmField) != null || findGroundToPrepare(position, farmField) != null;
            case FARMER_PLANT -> findPlantableSurface(position, farmField) != null || findHoeableSurface(position, farmField) != null;
            case FARMER_HARVEST -> hasSomethingGrowing(position);
            default -> true;
        };
    }

    /**
     * Whether anything at all is growing on this cell - a necessary condition for having something to harvest, and
     * cheap and side effect free, unlike {@link #findHarvestableSurface}.
     *
     * @param position the cell position, before the surface search.
     * @return true if there is a crop, stem or gourd above the surface.
     */
    private boolean hasSomethingGrowing(@NotNull final BlockPos position)
    {
        final BlockPos surface = getSurfacePos(position);
        if (surface == null)
        {
            return false;
        }
        final Block above = world.getBlockState(surface.above()).getBlock();
        return above instanceof CropBlock
                 || above instanceof MinecoloniesCropBlock
                 || above instanceof StemBlock
                 || above == Blocks.PUMPKIN
                 || above == Blocks.MELON;
    }

    /**
     * Whether the worker is already close enough to the cell to work it without being sent walking again.
     * <p>
     * Two blocks, against the four {@link #walkToSafePos} itself accepts as "arrived" once the navigator is done, so
     * this never lets the worker work a cell it would have refused to work on arrival.
     *
     * @param target the block the work is aimed at.
     * @return true if no walk order is needed.
     */
    private boolean isWithinFieldReach(@NotNull final BlockPos target)
    {
        if (BlockPosUtil.dist(worker.blockPosition(), target) > FIELD_WORK_REACH)
        {
            return false;
        }
        // Same thing walkToPos does when it decides the worker is already there: drop whatever it was walking to, or
        // it keeps walking to the previous cell while working this one. Guarded on isDone so that a worker standing
        // still is not handed a stop every tick - MinecoloniesAdvancedPathNavigate#stop resets the stuck timers, and
        // resetting those continuously would hide a worker that really was stuck.
        if (!worker.getNavigation().isDone())
        {
            worker.getNavigation().stop();
        }
        return true;
    }

    /**
     * Checks if we can hoe, and does so if we can.
     *
     * @param position  the position to check.
     * @param farmField the field close to this position.
     * @return true if the farmer should move on.
     */
    private boolean hoeIfAble(BlockPos position, final FarmField farmField)
    {
        if (!prepareGroundIfNeeded(position, farmField))
        {
            // Still clearing. Returning false leaves the working offset where it is, so the next tick comes back to the
            // same cell, which is what mineBlock's multi-call contract needs.
            return false;
        }

        return tillIfHoeable(position, farmField);
    }

    /**
     * Till the cell if its surface is bare ground the farmer can work, and leave it alone otherwise.
     * <p>
     * Split out of {@link #hoeIfAble} so that the planting pass can call it too, without also dragging in the ground
     * preparation of {@link #prepareGroundIfNeeded}, which stays a hoeing-stage job. {@link #findHoeableSurface} is
     * what decides: it already returns null for a cell that is the right farmland for the seed and for a cell with
     * anything growing on it, so calling this on a healthy field cell does nothing at all.
     *
     * @param position  the cell position, before the surface search.
     * @param farmField the field close to this position.
     * @return true if the farmer may move on to the next cell, false if it has to come back to this one.
     */
    private boolean tillIfHoeable(BlockPos position, final FarmField farmField)
    {
        position = findHoeableSurface(position, farmField);
        if (position != null && !checkForToolOrWeapon(ModEquipmentTypes.hoe.get()))
        {
            if (mineBlock(position.above()))
            {
                didWork = true;
                equipHoe();
                worker.swingForAttack(worker.getUsedItemHand());
                createCorrectFarmlandForSeed(seedFor(farmField, position), position);
                CitizenItemUtils.damageItemInHand(worker, InteractionHand.MAIN_HAND, 1);
                worker.decreaseSaturationForContinuousAction();
                worker.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().increment(LAND_TILLED, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());

                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * Create the correct farmland for a given seed.
     * @param seed the crop.
     * @param pos the position.
     */
    private void createCorrectFarmlandForSeed(final ItemStack seed, final BlockPos pos)
    {
        if (seed.getItem() instanceof ItemCrop itemCrop)
        {
            world.setBlockAndUpdate(pos, ((MinecoloniesCropBlock) itemCrop.getBlock()).getPreferredFarmland().defaultBlockState());
        }
        else
        {
            world.setBlockAndUpdate(pos, Blocks.FARMLAND.defaultBlockState());
        }
    }

    /**
     * Check if this is the right farm land for the specific crop.
     * <p>
     * Takes the seed rather than the field, because on a mixed field the answer differs from one cell to the next:
     * a MineColonies crop wants its own preferred farmland and everything else wants plain farmland, so two
     * neighbouring cells of the same field can legitimately want two different blocks under them.
     *
     * @param seed the seed destined for this cell.
     * @param blockState the state we're testing this on.
     * @return true if so.
     */
    private boolean isRightFarmLandForCrop(final ItemStack seed, final BlockState blockState)
    {
        if (seed.getItem() instanceof ItemCrop itemCrop)
        {
            return blockState.getBlock() == ((MinecoloniesCropBlock) itemCrop.getBlock()).getPreferredFarmland();
        }
        else
        {
            return blockState.getBlock() instanceof FarmlandBlock;
        }
    }

    /**
     * Checks if we can harvest, and does so if we can.
     *
     * @param position the block to harvest.
     * @return true if we harvested or not supposed to.
     */
    private boolean harvestIfAble(BlockPos position)
    {
        position = findHarvestableSurface(position);
        if (position != null)
        {
            if (mineBlock(position.above()))
            {
                didWork = true;
                worker.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().increment(CROPS_HARVESTED, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
                worker.getCitizenExperienceHandler().addExperience(XP_PER_HARVEST);
            }
            else
            {
                return false;
            }
        }
        return true;
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

    protected int getLevelDelay()
    {
        return (int) Math.max(SMALLEST_DELAY, DEFAULT_DELAY - ((getPrimarySkillLevel() / 2.0) * DELAY_DIVIDER));
    }

    /**
     * Try to plant the field at a certain position.
     *
     * @param farmField the field to try to plant.
     * @param position  the position to try.
     * @return the next state to go to.
     */
    private boolean tryToPlant(final FarmField farmField, BlockPos position)
    {
        position = findPlantableSurface(position, farmField);
        return position == null || plantCrop(seedFor(farmField, position), position);
    }

    /**
     * Whether {@link #plantCrop} would be able to put this seed in the ground at all.
     * <p>
     * The three block kinds are the ones plantCrop knows how to place, and this is the single statement of that so
     * the check {@link #canGoPlanting} makes before entering the planting stage cannot drift from what the planting
     * itself accepts. It is a question about the item, not about any particular cell - whether the cell is ready is
     * {@link #findPlantableSurface}'s business, and whether the crop can stand there is {@code canSurvive}'s.
     *
     * @param seed the seed to test.
     * @return true if it can be planted.
     */
    private static boolean isPlantableSeed(final ItemStack seed)
    {
        return seed.getItem() instanceof final BlockItem blockItem
                 && (blockItem.getBlock() instanceof CropBlock
                       || blockItem.getBlock() instanceof StemBlock
                       || blockItem.getBlock() instanceof MinecoloniesCropBlock);
    }

    /**
     * Sets the hoe as held item.
     */
    private void equipHoe()
    {
        CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, getHoeSlot());
    }

    /**
     * Checks if the ground should be planted.
     *
     * @param position  the position to check.
     * @param farmField the field close to this position.
     * @return position of plantable surface or null
     */
    private BlockPos findPlantableSurface(@NotNull BlockPos position, @NotNull final FarmField farmField)
    {
        position = getSurfacePos(position);
        if (position == null
              || farmField.isNoPartOfField(world, position)
              || world.getBlockState(position.above()).getBlock() instanceof CropBlock
              || world.getBlockState(position.above()).getBlock() instanceof StemBlock
              || world.getBlockState(position).getBlock() instanceof BlockScarecrow
              || !isRightFarmLandForCrop(seedFor(farmField, position), world.getBlockState(position))
              || world.getBlockState(position.above()).getBlock() instanceof MinecoloniesCropBlock)
        {
            return null;
        }

        return position;
    }

    /**
     * Plants the crop at a given location.
     *
     * @param item     the crop.
     * @param position the location.
     * @return true if successful.
     */
    private boolean plantCrop(final ItemStack item, @NotNull final BlockPos position)
    {
        if (item == null || item.isEmpty())
        {
            return false;
        }
        final int slot = worker.getCitizenInventoryHandler().findFirstSlotInInventoryWith(item.getItem());
        if (slot == -1)
        {
            return false;
        }

        if (isPlantableSeed(item) && ((BlockItem) item.getItem()).getBlock().defaultBlockState().canSurvive(worker.level(), position.above()))
        {
            @NotNull final Item seed = item.getItem();
            if ((seed == Items.MELON_SEEDS || seed == Items.PUMPKIN_SEEDS) && building.getPrevPos() != null && !world.isEmptyBlock(building.getPrevPos().above()))
            {
                return true;
            }

            world.setBlockAndUpdate(position.above(), ((BlockItem) item.getItem()).getBlock().defaultBlockState());
            worker.decreaseSaturationForContinuousAction();
            getInventory().extractItem(slot, 1, false);
            didWork = true;
        }
        return true;
    }

    /**
     * Checks if the crop should be harvested.
     *
     * @param position the position to check.
     * @return position of harvestable block or null
     */
    private BlockPos findHarvestableSurface(@NotNull BlockPos position)
    {
        position = getSurfacePos(position);
        if (position == null)
        {
            return null;
        }
        BlockState state = world.getBlockState(position.above());
        Block block = state.getBlock();

        if (block == Blocks.PUMPKIN || block == Blocks.MELON)
        {
            return position;
        }

        if (block instanceof CropBlock)
        {
            @NotNull CropBlock crop = (CropBlock) block;
            if (crop.isMaxAge(state))
            {
                return position;
            }
            final int amountOfCompostInInv = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), this::isCompost);
            if (amountOfCompostInInv == 0)
            {
                return null;
            }

            if (InventoryUtils.shrinkItemCountInItemHandler(worker.getInventoryCitizen(), this::isCompost))
            {
                new CompostParticleMessage(position.above())
                    .sendToTargetPoint((ServerLevel) world, null, position.getX(), position.getY(), position.getZ(), BLOCK_BREAK_SOUND_RANGE);
                crop.growCrops(world, position.above(), state);
                state = world.getBlockState(position.above());
                block = state.getBlock();
                if (block instanceof CropBlock)
                {
                    crop = (CropBlock) block;
                }
                else
                {
                    return null;
                }
            }
            return crop.isMaxAge(state) ? position : null;
        }
        else if (block instanceof MinecoloniesCropBlock minecoloniesCrop)
        {
            if (minecoloniesCrop.isMaxAge(state))
            {
                return position;
            }
            final int amountOfCompostInInv = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), this::isCompost);
            if (amountOfCompostInInv == 0)
            {
                return null;
            }

            if (InventoryUtils.shrinkItemCountInItemHandler(worker.getInventoryCitizen(), this::isCompost))
            {
                new CompostParticleMessage(position.above())
                  .sendToTargetPoint((ServerLevel) world, null, position.getX(), position.getY(), position.getZ(), BLOCK_BREAK_SOUND_RANGE);
                minecoloniesCrop.attemptGrow(state, (ServerLevel) world, position.above());
                state = world.getBlockState(position.above());
                block = state.getBlock();
                if (block instanceof MinecoloniesCropBlock)
                {
                    minecoloniesCrop = (MinecoloniesCropBlock) block;
                }
                else
                {
                    return null;
                }
            }
            return minecoloniesCrop.isMaxAge(state) ? position : null;
        }
        return null;
    }

    @Override
    protected List<ItemStack> increaseBlockDrops(final List<ItemStack> drops)
    {
        final double increaseCrops = worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(FARMING);
        if (increaseCrops == 0)
        {
            return drops;
        }

        final List<ItemStack> newDrops = new ArrayList<>();
        for (final ItemStack stack : drops)
        {
            final ItemStack drop = stack.copy();
            if (worker.getRandom().nextDouble() < increaseCrops)
            {
                drop.setCount(drop.getCount() * 2);
            }
            newDrops.add(drop);
        }

        return newDrops;
    }

    @Override
    public int getBreakSpeedLevel()
    {
        return getSecondarySkillLevel();
    }

    /**
     * Get's the slot in which the hoe is in.
     *
     * @return slot number
     */
    private int getHoeSlot()
    {
        return InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(getInventory(), ModEquipmentTypes.hoe.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel());
    }

    /**
     * Returns the farmer's worker instance. Called from outside this class.
     *
     * @return citizen object
     */
    @Nullable
    public AbstractEntityCitizen getCitizen()
    {
        return worker;
    }

    @Override
    public boolean canGoIdle()
    {
        if (building.getModule(FARMER_FIELDS).getBuildingExtensionToWorkOn() == null)
        {
            return !super.hasWorkToDo();
        }

        return false;
    }
}
