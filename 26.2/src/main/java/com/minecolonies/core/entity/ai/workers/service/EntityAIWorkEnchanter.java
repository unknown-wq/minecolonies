package com.minecolonies.core.entity.ai.workers.service;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.*;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.EnchanterStationsModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingEnchanter;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.jobs.JobEnchanter;
import com.minecolonies.core.entity.ai.workers.crafting.AbstractEntityAICrafting;
import com.minecolonies.core.network.messages.client.CircleParticleEffectMessage;
import com.minecolonies.core.network.messages.client.StreamParticleEffectMessage;
import com.minecolonies.core.util.WorkerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.TranslationConstants.NO_WORKERS_TO_DRAIN_SET;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEMS_ENCHANTED;
import static com.minecolonies.api.util.constant.StatisticsConstants.CITIZENS_VISITED;

/**
 * Enchanter AI class.
 */
public class EntityAIWorkEnchanter extends AbstractEntityAICrafting<JobEnchanter, BuildingEnchanter>
{
    /**
     * Predicate to define an ancient tome which can be enchanted.
     */
    private static final Predicate<ItemStack> IS_ANCIENT_TOME = item -> !item.isEmpty() && item.getItem() == ModItems.ancientTome;

    /**
     * Predicate to define an ancient tome which can be enchanted.
     */
    private static final Predicate<ItemStack> IS_BOOK = item -> !item.isEmpty() && item.getItem() == Items.BOOK;

    /**
     * Predicate to define a finished book, which is what the enchanter spends on a worker's gear from hut level four.
     */
    private static final Predicate<ItemStack> IS_ENCHANTED_BOOK = item -> !item.isEmpty() && item.getItem() == Items.ENCHANTED_BOOK;

    /**
     * Min distance to drain from citizen.
     *
     * <p>Ten was harmless while the target was always the enchanter itself, which is never further from itself than
     * zero. Against a real worker it has to cover a citizen moving about its own hut, so it is a hut's width rather
     * than a stride. A worker genuinely away on a job still times out after {@code MAX_WAITING_TICKS} and the station
     * is left for another day.</p>
     */
    private static final long MIN_DISTANCE_TO_DRAIN = 20;

    /**
     * Max progress ticks until drainage is complete (per Level).
     */
    private static final int MAX_PROGRESS_TICKS = 60;

    /**
     * Length of one enchanting cycle at hut level one, in AI ticks (the ENCHANT target runs once a second).
     */
    private static final int MAX_ENCHANTMENT_TICKS = 60 * 5;

    /**
     * How much each hut level above the first takes off that cycle.
     *
     * <p>This used to be a division -- 300 ticks at hut one down to 60 at hut five -- which made hut level worth a
     * fivefold speed increase. Speed is the one thing the colony can never use: the only input is the ancient tome,
     * which drops from raiders and nothing else, and raids come on average every fourteen nights, so even a level
     * one enchanter spends nearly all of its day waiting for the next tome. Hut level now buys yield instead, in the
     * loot tables, and the cycle merely shortens from five minutes to three across the five levels.</p>
     */
    private static final int ENCHANTMENT_TICKS_PER_LEVEL = 30;

    /**
     * Minimum mana requirement per level.
     */
    private static final int MANA_REQ_PER_LEVEL = 10;

    /**
     * Hut level from which the enchanter carries a finished book to the hut it visits and puts what is on it onto the
     * worker's gear, instead of only playing the beam.
     */
    private static final int GEAR_SERVICE_MIN_LEVEL = 4;

    /**
     * Subtracted from the hut level to get the highest enchantment level the enchanter may apply: two at hut four,
     * three at hut five. This is the enchanter's own ceiling; the target hut has one of its own, see
     * {@link #enchantmentHeadroom}.
     */
    private static final int GEAR_LEVEL_OFFSET = 2;

    /**
     * XP per drain
     */
    private static final double XP_PER_DRAIN = 10;

    /**
     * Base XP for finishing one book. The maximum stored enchantment level is added on top, so a better book is worth
     * more, exactly as it costs more mana.
     */
    private static final double XP_PER_ENCHANT = 10;

    /**
     * The citizen entity to gather from.
     */
    private ICitizenData citizenToGatherFrom = null;

    /**
     * Variable to check if the draining is in progress. And at which tick it is.
     */
    private int progressTicks = 0;

    /**
     * Creates the abstract part of the AI. Always use this constructor!
     *
     * @param job the job to fulfill
     */
    public EntityAIWorkEnchanter(@NotNull final JobEnchanter job)
    {
        super(job);
        super.registerTargets(
          new AITarget(ENCHANTER_DRAIN, this::gatherAndDrain, 10),
          new AITarget(ENCHANT, this::enchant, TICKS_SECOND)
        );
        worker.setCanPickUpLoot(true);
    }

    /**
     * Decide method of the enchanter. Check if everything is alright to work and then decide between gathering and draining and actually enchanting.
     *
     * @return the next state to go to.
     */
    @Override
    protected IAIState decide()
    {
        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!walkToBuilding())
        {
            return START_WORKING;
        }

        // getNextCraftingState() returns IDLE, INVENTORY_FULL, QUERY_ITEMS or GET_RECIPE -- never START_WORKING. The
        // test used to be "!= START_WORKING", which is therefore always true, so from midday onwards a worker with no
        // crafting order returned IDLE from here and stood still for the rest of the day. IDLE is the "nothing to
        // craft" answer, so testing against that gives the intended split: player orders in the afternoon, books the
        // rest of the time.
        final IAIState craftState = getNextCraftingState();
        if (craftState != IDLE && !WorldUtil.isPastTime(world, 6000))
        {
            return craftState;
        }

        if (wantInventoryDumped())
        {
            // Wait to dump before continuing.
            return getState();
        }

        final boolean produceBooks = building.getSettingValueOrDefault(BuildingEnchanter.PRODUCE_BOOKS, true);
        final boolean visitStations = building.getSettingValueOrDefault(BuildingEnchanter.VISIT_STATIONS, true);

        if (getPrimarySkillLevel() < building.getBuildingLevel() * MANA_REQ_PER_LEVEL)
        {
            if (!visitStations)
            {
                // Below the mana gate with visiting switched off there is nothing this worker can do but the player's
                // crafting orders, which were handled above.
                return IDLE;
            }

            return visitStation(true);
        }

        if (!produceBooks)
        {
            // Books switched off. Keep visiting if that is switched on -- from hut level four the visit is a service
            // in its own right, and it is the only thing left to do here.
            return visitStations ? visitStation(false) : IDLE;
        }

        final BuildingEnchanter.CraftingModule craftingModule = building.getFirstModuleOccurance(BuildingEnchanter.CraftingModule.class);
        boolean ancientTomeCraftingDisabled = false;
        for (final IToken<?> token : craftingModule.getRecipes())
        {
            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
            if (storage != null && !storage.getInput().isEmpty() && storage.getInput().get(0).getItem() == ModItems.ancientTome && craftingModule.isDisabled(token))
            {
                ancientTomeCraftingDisabled = true;
            }
        }

        if (ancientTomeCraftingDisabled)
        {
            // Nothing to make: enchant() would find no fulfillable recipe and hand straight back to decide(), which
            // would send it to ENCHANT again. Stop here instead of spinning between the two states forever.
            return IDLE;
        }

        final int ancientTomesInInv = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), IS_ANCIENT_TOME);
        if (ancientTomesInInv <= 0)
        {
            final int amountOfAncientTomes = InventoryUtils.hasBuildingEnoughElseCount(building, IS_ANCIENT_TOME, 1);
            if (amountOfAncientTomes > 0)
            {
                needsCurrently = new Tuple<>(IS_ANCIENT_TOME, 1);
                return GATHERING_REQUIRED_MATERIALS;
            }
            checkIfRequestForItemExistOrCreateAsync(new ItemStack(ModItems.ancientTome, 1), 1, 1, false);
            return IDLE;
        }

        return ENCHANT;
    }

    /**
     * Set up a visit to one of the huts on the station list: check there is one, take a book along, and go.
     *
     * @param complainIfNoStations whether an empty station list should raise the blocking chat. It should when the
     *                             worker is below its mana gate and cannot work without visiting; it should not when
     *                             the player has merely left visiting switched on with nothing to visit.
     * @return the next state to go to.
     */
    private IAIState visitStation(final boolean complainIfNoStations)
    {
        final EnchanterStationsModule module = building.getModule(BuildingModules.ENCHANTER_STATIONS);
        if (module.getBuildingsToGatherFrom().isEmpty())
        {
            if (complainIfNoStations && worker.getCitizenData() != null)
            {
                worker.getCitizenData()
                  .triggerInteraction(new StandardInteraction(Component.translatableEscape(NO_WORKERS_TO_DRAIN_SET), ChatPriority.BLOCKING));
            }
            return IDLE;
        }

        final BlockPos posToDrainFrom = module.getRandomBuildingToDrainFrom();
        if (posToDrainFrom == null)
        {
            return IDLE;
        }

        // At hut four and up the visit spends a finished book on the visited worker's gear, so take one along if the
        // building has any -- this is what turns the warehouse pile into something the colony uses. The plain book
        // below is still required either way: it is what the visit falls back on when nothing the visited worker
        // carries can take what the finished book offers, and without it a visit could end having spent nothing.
        if (building.getBuildingLevel() >= GEAR_SERVICE_MIN_LEVEL
              && InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), IS_ENCHANTED_BOOK) <= 0
              && InventoryUtils.hasBuildingEnoughElseCount(building, IS_ENCHANTED_BOOK, 1) > 0)
        {
            needsCurrently = new Tuple<>(IS_ENCHANTED_BOOK, 1);
            return GATHERING_REQUIRED_MATERIALS;
        }

        if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), IS_BOOK) <= 0)
        {
            if (InventoryUtils.hasBuildingEnoughElseCount(building, IS_BOOK, 1) > 0)
            {
                needsCurrently = new Tuple<>(IS_BOOK, 1);
                return GATHERING_REQUIRED_MATERIALS;
            }
            checkIfRequestForItemExistOrCreateAsync(new ItemStack(Items.BOOK, 1));
            return IDLE;
        }

        job.setBuildingToDrainFrom(posToDrainFrom);
        return ENCHANTER_DRAIN;
    }

    @Override
    public boolean hasWorkToDo()
    {
        return true;
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return 1;
    }

    /**
     * Actually do the enchanting. Making some great effects for some time and then apply a random enchantment. Reduce own levels depending on the found enchantment.
     *
     * @return the next state to go to.
     */
    private IAIState enchant()
    {
        // this assumes that the only empty-output (pure loot table) recipes are for ancient tome -> enchanted book
        currentRecipeStorage = building.getFirstModuleOccurance(BuildingEnchanter.CraftingModule.class).getFirstFulfillableRecipe(ItemStackUtils::isEmpty, 1, false);
        if (currentRecipeStorage == null)
        {
            progressTicks = 0;
            return IDLE;
        }

        if (progressTicks++ < MAX_ENCHANTMENT_TICKS - (building.getBuildingLevel() - 1) * ENCHANTMENT_TICKS_PER_LEVEL)
        {
            new CircleParticleEffectMessage(worker.position().add(0, 2, 0), ParticleTypes.ENCHANT, progressTicks)
                .sendToTrackingEntity(worker);

            new CircleParticleEffectMessage(worker.position().add(0, 1.5, 0), ParticleTypes.ENCHANT, progressTicks)
                .sendToTrackingEntity(worker);

            new CircleParticleEffectMessage(worker.position().add(0, 1, 0), ParticleTypes.ENCHANT, progressTicks)
                .sendToTrackingEntity(worker);

            worker.queueSound(SoundEvents.ENCHANTMENT_TABLE_USE, worker.blockPosition().above(), 20, 0, 0.5f, worker.getRandom().nextFloat());

            if (worker.getRandom().nextBoolean())
            {
                worker.swing(InteractionHand.MAIN_HAND);
            }
            else
            {
                worker.swing(InteractionHand.OFF_HAND);
            }
            return getState();
        }

        final ICitizenData data = worker.getCitizenData();
        if (data != null)
        {
            final List<ItemStack> loot = currentRecipeStorage.fullfillRecipeAndCopy(getLootContext(), building.getHandlers(), true);
            if (loot != null)
            {
                final int enchantmentLevel = loot.stream()
                                               .mapToInt(EntityAIWorkEnchanter::getEnchantedBookLevel)
                                               .max().orElse(0);

                //Decrement mana.
                data.getCitizenSkillHandler().incrementLevel(Skill.Mana, -enchantmentLevel);
                // A finished book is the enchanter's unit of work; without this the worker earned experience only from
                // draining and its level froze the moment it cleared the mana gate.
                worker.getCitizenExperienceHandler().addExperience(XP_PER_ENCHANT + enchantmentLevel);
                recordEnchantmentStats(loot);
                incrementActionsDoneAndDecSaturation();
            }
        }

        currentRecipeStorage = null;
        progressTicks = 0;
        return IDLE;
    }

    private static int getEnchantedBookLevel(@NotNull final ItemStack stack)
    {
        if (stack.getItem().equals(Items.ENCHANTED_BOOK))
        {
            int level = 0;
            // An enchanted book keeps its enchantments under STORED_ENCHANTMENTS, not ENCHANTMENTS. Reading the latter
            // returned an empty map for every book ever made here, so the mana debit below was always zero and an
            // enchanter that had once passed its level gate never drained again. getEnchantmentsForCrafting picks the
            // component that matches the item.
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())
            {
                level = Math.max(level, entry.getIntValue());
            }
            return level;
        }
        return 0;
    }

    /**
     * Gather experience from a worker. Go to the hut of the worker. Wait for the worker. Drain, and then return to work building.
     *
     * @return next state to go to.
     */
    private IAIState gatherAndDrain()
    {
        if (job.getPosToDrainFrom() == null)
        {
            return IDLE;
        }

        final IBuilding buildingWorker = building.getColony().getServerBuildingManager().getBuilding(job.getPosToDrainFrom());
        if (!walkToBuilding(buildingWorker))
        {
            return getState();
        }

        if (buildingWorker == null)
        {
            resetDraining();
            building.getFirstModuleOccurance(EnchanterStationsModule.class).removeWorker(job.getPosToDrainFrom());
            return IDLE;
        }

        if (citizenToGatherFrom == null)
        {
            // getModuleForJob() is the module of the enchanter's OWN job, not of the building it just walked to, so
            // this picked the enchanter itself every time: the walk, the beam and everything that followed landed on
            // the worker doing the visiting. The citizens to look at are the ones assigned to the building at hand.
            final List<AbstractEntityCitizen> workers = new ArrayList<>();
            for (final WorkerBuildingModule module : buildingWorker.getModulesByType(WorkerBuildingModule.class))
            {
                for (final Optional<AbstractEntityCitizen> citizen : module.getAssignedEntities())
                {
                    citizen.filter(c -> c.getCitizenData() != worker.getCitizenData()).ifPresent(workers::add);
                }
            }

            final AbstractEntityCitizen citizen;
            if (workers.size() > 1)
            {
                citizen = workers.get(worker.getRandom().nextInt(workers.size()));
            }
            else
            {
                if (workers.isEmpty())
                {
                    resetDraining();
                    return START_WORKING;
                }
                citizen = workers.get(0);
            }

            citizenToGatherFrom = citizen.getCitizenData();
            progressTicks = 0;
            return getState();
        }

        if (!citizenToGatherFrom.getEntity().isPresent())
        {
            citizenToGatherFrom = null;
            return getState();
        }

        if (progressTicks == 0)
        {
            // If worker is too far away wait.
            if (BlockPosUtil.getDistance2D(citizenToGatherFrom.getEntity().get().blockPosition(), worker.blockPosition()) > MIN_DISTANCE_TO_DRAIN)
            {
                if (!job.incrementWaitingTicks())
                {
                    resetDraining();
                    return START_WORKING;
                }
                return getState();
            }
        }

        progressTicks++;
        if (progressTicks < MAX_PROGRESS_TICKS)
        {
            final Vec3 start = worker.position().add(0, 2, 0);
            final Vec3 goal = citizenToGatherFrom.getEntity().get().position().add(0, 2, 0);

            new StreamParticleEffectMessage(start, goal, ParticleTypes.ENCHANT, progressTicks % MAX_PROGRESS_TICKS, MAX_PROGRESS_TICKS)
                .sendToTrackingEntity(worker);

            new CircleParticleEffectMessage(start, ParticleTypes.HAPPY_VILLAGER, progressTicks).sendToTrackingEntity(worker);

            WorkerUtil.faceBlock(BlockPos.containing(goal), worker);

            if (worker.getRandom().nextBoolean())
            {
                worker.swing(InteractionHand.MAIN_HAND);
            }
            else
            {
                worker.swing(InteractionHand.OFF_HAND);
            }

            return getState();
        }

        // From hut level four the visit is a service: the enchanter brings one of its own finished books and puts what
        // is written on it onto this worker's tool or armour. Below that level, and whenever no book in stock fits
        // anything this worker carries, it falls back to the cheap random enchantment the visit has always done.
        int bookSlot = -1;
        if (building.getBuildingLevel() >= GEAR_SERVICE_MIN_LEVEL)
        {
            final int stockedBook = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.ENCHANTED_BOOK);
            if (stockedBook != -1 && applyBook(worker.getInventoryCitizen().getStackInSlot(stockedBook), buildingWorker))
            {
                bookSlot = stockedBook;
            }
        }

        if (bookSlot == -1)
        {
            bookSlot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.BOOK);
            if (bookSlot != -1)
            {
                final int size = citizenToGatherFrom.getInventory().getSlots();
                final int attempts = (int) (getSecondarySkillLevel() / 5.0);

                for (int i = 0; i < attempts; i++)
                {
                    int randomSlot = worker.getRandom().nextInt(size);
                    final ItemStack stack = citizenToGatherFrom.getInventory().getStackInSlot(randomSlot);
                    if (!stack.isEmpty() && stack.isEnchantable())
                    {
                        EnchantmentHelper.enchantItem(worker.getRandom(), stack, getSecondarySkillLevel() > 50 ? 2 : 1, world.registryAccess(), Optional.empty());
                        break;
                    }
                }
            }
        }

        if (bookSlot != -1)
        {
            worker.getInventoryCitizen().extractItem(bookSlot, 1, false);
            worker.getCitizenData().getCitizenSkillHandler().incrementLevel(Skill.Mana, 1);
            worker.getCitizenExperienceHandler().addExperience(XP_PER_DRAIN);
            worker.getCitizenData().markDirty(80);
            citizenToGatherFrom.markDirty(80);
            StatsUtil.trackStat(building, CITIZENS_VISITED, 1);
        }
        resetDraining();
        return IDLE;
    }

    /**
     * Put what is written on one enchanted book onto the best piece of gear the visited worker carries.
     *
     * <p>Each enchantment is applied only where vanilla would allow it -- the item has to be a legal target, and the
     * enchantment has to be compatible with what is already on the item, unless it IS what is already on the item, in
     * which case this is an upgrade. The level applied is the lowest of three ceilings: what the book says, what this
     * enchanter's own hut level allows, and what the visited worker's hut will still accept. That last one matters:
     * a hut refuses gear whose equipment level plus enchantment level exceeds its own ceiling, so an over-enthusiastic
     * enchantment would leave the worker unable to hold its own tool.</p>
     *
     * @param book           the enchanted book to spend.
     * @param targetBuilding the building the visited worker belongs to.
     * @return true if anything was applied, in which case the book is spent.
     */
    private boolean applyBook(@NotNull final ItemStack book, @NotNull final IBuilding targetBuilding)
    {
        final ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(book);
        if (stored.isEmpty())
        {
            return false;
        }

        final int enchanterCeiling = building.getBuildingLevel() - GEAR_LEVEL_OFFSET;
        for (final ItemStack stack : enchantableGear(citizenToGatherFrom))
        {

            final int headroom = enchantmentHeadroom(targetBuilding, stack);
            if (headroom <= 0)
            {
                continue;
            }

            final ItemEnchantments existing = EnchantmentHelper.getEnchantmentsForCrafting(stack);
            boolean applied = false;
            for (final Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet())
            {
                final Holder<Enchantment> enchantment = entry.getKey();
                final int current = existing.getLevel(enchantment);
                if (!enchantment.value().canEnchant(stack)
                      || (current == 0 && !EnchantmentHelper.isEnchantmentCompatible(existing.keySet(), enchantment)))
                {
                    continue;
                }

                final int level = Math.min(Math.min(entry.getIntValue(), enchanterCeiling), headroom);
                if (level <= current)
                {
                    continue;
                }

                stack.enchant(enchantment, level);
                StatsUtil.trackStatByName(building, ITEMS_ENCHANTED, Enchantment.getFullname(enchantment, level), 1);
                applied = true;
            }

            if (applied)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Everything a citizen carries or wears that an enchantment could go on.
     *
     * <p>{@link InventoryCitizen#getSlots()} covers the main inventory only, so worn armour -- which for a guard is
     * most of what is worth enchanting -- has to be collected separately.</p>
     *
     * @param citizen the citizen.
     * @return the stacks, in the order they should be considered.
     */
    @NotNull
    private static List<ItemStack> enchantableGear(@NotNull final ICitizenData citizen)
    {
        final List<ItemStack> gear = new ArrayList<>();
        final InventoryCitizen inventory = citizen.getInventory();
        for (final EquipmentSlot slot : EquipmentSlot.values())
        {
            if (slot.isArmor())
            {
                gear.add(inventory.getArmorInSlot(slot));
            }
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            gear.add(inventory.getStackInSlot(slot));
        }
        gear.removeIf(stack -> stack.isEmpty() || stack.is(Items.ENCHANTED_BOOK) || stack.is(Items.BOOK) || !stack.isEnchantable());
        return gear;
    }

    /**
     * The highest enchantment level the given hut will still let a worker hold the given item at.
     *
     * <p>{@code ItemStackUtils.verifyEquipmentLevel} accepts an item when its equipment level plus
     * {@code max(maxEnchantmentLevel - 1, 0)} is within the hut's ceiling, so the answer is
     * {@code ceiling - equipmentLevel + 1}. A hut at its own maximum level has no ceiling, and an item the colony
     * does not grade as equipment cannot be refused for this reason either.</p>
     *
     * @param targetBuilding the hut that will have to accept the item.
     * @param stack          the item.
     * @return the highest level that may be applied, or a non-positive number if none may be.
     */
    private static int enchantmentHeadroom(@NotNull final IBuilding targetBuilding, @NotNull final ItemStack stack)
    {
        final int ceiling = targetBuilding.getMaxEquipmentLevel();
        if (ceiling == Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }

        int equipmentLevel = Integer.MIN_VALUE;
        for (final EquipmentTypeEntry type : ModEquipmentTypes.getRegistry())
        {
            if (type.checkIsEquipment(stack))
            {
                equipmentLevel = Math.max(equipmentLevel, type.getMiningLevel(stack));
            }
        }

        return equipmentLevel == Integer.MIN_VALUE ? Integer.MAX_VALUE : ceiling - equipmentLevel + 1;
    }

    /**
     * Helper method to reset all variables of the draining.
     */
    private void resetDraining()
    {

        building.getFirstModuleOccurance(EnchanterStationsModule.class).setAsGathered(job.getPosToDrainFrom());
        citizenToGatherFrom = null;
        job.setBuildingToDrainFrom(null);
        progressTicks = 0;
        incrementActionsDoneAndDecSaturation();
    }

    @Override
    public Class<BuildingEnchanter> getExpectedBuildingClass()
    {
        return BuildingEnchanter.class;
    }

    /**
     * Records stats for items enchanted by the enchanter.
     * @param loot the items to record stats for
     */
    public void recordEnchantmentStats(List<ItemStack> loot)
    {
        for (ItemStack stack : loot)
        {
            Component name = stack.getHoverName();

            if (stack.is(Items.ENCHANTED_BOOK))
            {
                ItemEnchantments ench = EnchantmentHelper.getEnchantmentsForCrafting(stack);

                if (!ench.isEmpty())
                {
                    if (ench.size() == 1)
                    {
                        Holder<Enchantment> h = ench.keySet().iterator().next();
                        int lvl = ench.getLevel(h);
                        name = Enchantment.getFullname(h, lvl);
                    }
                    else
                    {
                        List<Component> parts = ench.keySet().stream().map(h -> Enchantment.getFullname(h, ench.getLevel(h))).toList();
                        name = ComponentUtils.formatList(parts, Component.literal(", "));
                    }
                }
            }

            StatsUtil.trackStatByName(building, ITEMS_ENCHANTED, name, stack.getCount());
        }
    }

    /**
     * Returns the name of the crafting stat used in the building's statistics.
     * @return The name of the enchanting statistic.
     */

    protected String getCraftingStatName()
    {
        return ITEMS_ENCHANTED;
    }
}
