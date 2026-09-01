package com.minecolonies.core.entity.ai.workers.guard;

import com.minecolonies.api.entity.ai.workers.util.GuardGear;
import com.minecolonies.api.entity.ai.workers.util.GuardGearBuilder;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.InventoryFunctions;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.items.ItemSpear;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.minecolonies.api.inventory.api.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.research.util.ResearchConstants.ARCHER_USE_ARROWS;
import static com.minecolonies.api.research.util.ResearchConstants.SHIELD_USAGE;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.GuardConstants.*;
import static com.minecolonies.api.util.constant.EquipmentLevelConstants.*;

/**
 * Class taking of the abstract guard methods for both archer and knights.
 *
 * @param <J> the generic job.
 */
public abstract class AbstractEntityAIFight<J extends AbstractJobGuard<J>, B extends AbstractBuildingGuards> extends AbstractEntityAIInteract<J, B>
{

    /**
     * Tools and Items needed by the worker.
     */
    public final List<EquipmentTypeEntry> toolsNeeded = new ArrayList<>();

    /**
     * List of items that are required by the guard based on building level and guard level.  This array holds a pointer to the building level and then pointer to GuardGear
     */
    public final List<List<GuardGear>> itemsNeeded = new ArrayList<>();

    /**
     * The value of the speed which the guard will move.
     */
    private static final double COMBAT_SPEED = 1.0;

    /**
     * The bonus speed per worker level.
     */
    public static final double SPEED_LEVEL_BONUS = 0.01;

    /**
     * Creates the abstract part of the AI. Always use this constructor!
     *
     * @param job the job to fulfill
     */
    public AbstractEntityAIFight(@NotNull final J job)
    {
        super(job);
        super.registerTargets(
          new AITarget(IDLE, START_WORKING, 1),
          new AITarget(START_WORKING, this::startWorkingAtOwnBuilding, 100),
          new AITarget(PREPARING, this::prepare, TICKS_SECOND)
        );
        worker.setCanPickUpLoot(true);

        itemsNeeded.add(GuardGearBuilder.buildGearForLevel(ARMOR_LEVEL_LEATHER, ARMOR_LEVEL_COPPER, LEATHER_BUILDING_LEVEL_RANGE, COPPER_BUILDING_LEVEL_RANGE));
        itemsNeeded.add(GuardGearBuilder.buildGearForLevel(ARMOR_LEVEL_LEATHER, ARMOR_LEVEL_CHAIN, LEATHER_BUILDING_LEVEL_RANGE, CHAIN_BUILDING_LEVEL_RANGE));
        itemsNeeded.add(GuardGearBuilder.buildGearForLevel(ARMOR_LEVEL_LEATHER, ARMOR_LEVEL_IRON, LEATHER_BUILDING_LEVEL_RANGE, IRON_BUILDING_LEVEL_RANGE));
        itemsNeeded.add(GuardGearBuilder.buildGearForLevel(ARMOR_LEVEL_CHAIN, ARMOR_LEVEL_DIAMOND, LEATHER_BUILDING_LEVEL_RANGE, DIA_BUILDING_LEVEL_RANGE));
        itemsNeeded.add(GuardGearBuilder.buildGearForLevel(ARMOR_LEVEL_IRON, ARMOR_LEVEL_MAX, LEATHER_BUILDING_LEVEL_RANGE, DIA_BUILDING_LEVEL_RANGE));
    }

    /**
     * Redirects the guard to their building.
     *
     * @return The next {@link IAIState}.
     */
    protected IAIState startWorkingAtOwnBuilding()
    {
        if (!walkToBuilding())
        {
            return getState();
        }
        return PREPARING;
    }

    @Override
    public IAIState afterRequestPickUp()
    {
        return PREPARING;
    }

    @Override
    public IAIState getStateAfterPickUp()
    {
        return PREPARING;
    }

    /**
     * Prepares the guard. Fills his required armor and tool lists and transfer from building chest if required.
     *
     * @return The next {@link IAIState}.
     */
    private IAIState prepare()
    {
        for (final EquipmentTypeEntry tool : toolsNeeded)
        {
            if (checkForToolOrWeapon(tool))
            {
                return getState();
            }
            InventoryFunctions.matchFirstInProviderWithSimpleAction(worker,
              stack -> !ItemStackUtils.isEmpty(stack)
                         && ItemStackUtils.doesItemServeAsWeapon(stack)
                         && ItemStackUtils.hasEquipmentLevel(stack, tool, stack.getItem() instanceof ItemSpear ? -1 : 0, building.getMaxEquipmentLevel()),
              itemStack -> CitizenItemUtils.setMainHeldItem(worker, itemStack));

            // Arrows gate nothing, but a bow without them shoots for less damage and nothing in the colony ever asks
            // for them, so free mode hands them over here rather than leaving the archer permanently under-armed.
            if (tool == ModEquipmentTypes.bow.get()
                  && FreeMode.isOn(building)
                  && worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ARCHER_USE_ARROWS) > 0
                  && !InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), stack -> stack.getItem() instanceof ArrowItem))
            {
                FreeMode.supply(worker.getInventoryCitizen(), new ItemStack(Items.ARROW), Items.ARROW.getDefaultMaxStackSize());
            }
        }

        equipInventoryArmor();

        // Can only "see" the inventory and check for items if at the building
        if (worker.blockPosition().distSqr(building.getID()) > 50)
        {
            return DECIDE;
        }

        atBuildingActions();
        return DECIDE;
    }

    /**
     * Task to do when at the own building, as guards only go there on requests and on dump
     */
    protected void atBuildingActions()
    {
        for (final GuardGear item : itemsNeeded.get(building.getBuildingLevelEquivalent() - 1))
        {
            if (!(building.getBuildingLevelEquivalent() >= item.getMinBuildingLevelRequired() && building.getBuildingLevelEquivalent() <= item.getMaxBuildingLevelRequired()))
            {
                continue;
            }
            if (item.getItemNeeded() == ModEquipmentTypes.shield.get()
                  && worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(SHIELD_USAGE) <= 0)
            {
                continue;
            }

            int bestSlot = -1;
            int bestLevel = -1;
            // Material level alone cannot separate an enchanted diamond chestplate from a plain one -- for armour that
            // level is Compatibility.getItemLevel, which does not look at enchantments -- so the guard kept whichever
            // he found first and the Enchanter's work sat in the rack. This is the tie-break, in the same units the
            // acceptance test already uses: GuardGear#test goes through verifyEquipmentLevel, which adds exactly this
            // term to the material level before comparing against the band. Everything reached below has therefore
            // already been accepted at its enchanted level, so preferring by it cannot pick a piece the hut will then
            // refuse. It does mean a level one enchantment scores as plain, since that is what the acceptance term
            // does with it.
            int bestEnchantment = -1;
            IItemHandler bestHandler = null;

            if (item.getType().isArmor())
            {
                if (!ItemStackUtils.isEmpty(worker.getInventoryCitizen().getArmorInSlot(item.getType())))
                {
                    final ItemStack worn = worker.getInventoryCitizen().getArmorInSlot(item.getType());
                    bestLevel = item.getItemNeeded().getMiningLevel(worn);
                    bestEnchantment = ItemStackUtils.getMaxEnchantmentLevel(worn);
                }
            }
            else
            {
                if (!ItemStackUtils.isEmpty(worker.getItemBySlot(item.getType())))
                {
                    final ItemStack held = worker.getItemBySlot(item.getType());
                    bestLevel = item.getItemNeeded().getMiningLevel(held);
                    bestEnchantment = ItemStackUtils.getMaxEnchantmentLevel(held);
                }
            }


            final Map<IItemHandler, List<Integer>> items = InventoryUtils.findAllSlotsInProviderWith(building, item);
            if (items.isEmpty())
            {
                // The hut holds nothing that fits this band. Ask for a piece whenever what the guard already carries
                // is below the band his hut licenses -- bestLevel is still the level of the worn/held piece here, and
                // -1 when the slot is empty, so the old "only if the slot is empty" case is contained in this one.
                //
                // That old condition was the whole of it, which meant a guard issued leather on his first day in a
                // level-one tower never asked for anything again: his slots were no longer empty, so no request was
                // ever created, and upgrading the tower to level five left him in the same leather -- 7 armour points
                // where the gear bands plainly intend 20. The only route to better armour was the player stocking the
                // rack by hand, which the branch below then handled correctly.
                if (bestLevel < item.getMinArmorLevel())
                {
                    // create request
                    checkForToolOrWeaponAsync(item.getItemNeeded(), item.getMinArmorLevel(), item.getMaxArmorLevel());
                }
            }
            else
            {
                // Compare levels
                for (Map.Entry<IItemHandler, List<Integer>> entry : items.entrySet())
                {
                    for (final Integer slot : entry.getValue())
                    {
                        final ItemStack stack = entry.getKey().getStackInSlot(slot);
                        if (ItemStackUtils.isEmpty(stack))
                        {
                            continue;
                        }

                        final int currentLevel = item.getItemNeeded().getMiningLevel(stack);
                        final int currentEnchantment = ItemStackUtils.getMaxEnchantmentLevel(stack);

                        if (currentLevel > bestLevel || (currentLevel == bestLevel && currentEnchantment > bestEnchantment))
                        {
                            bestLevel = currentLevel;
                            bestEnchantment = currentEnchantment;
                            bestSlot = slot;
                            bestHandler = entry.getKey();
                        }
                    }
                }
            }

            // Transfer if needed
            if (bestHandler != null)
            {
                if (item.getType().isArmor())
                {
                    if (!ItemStackUtils.isEmpty(worker.getInventoryCitizen().getArmorInSlot(item.getType())))
                    {
                        final ItemStack armorStack = worker.getInventoryCitizen().getArmorInSlot(item.getType());
                        worker.getInventoryCitizen().moveArmorToInventory(item.getType());
                        final int slot =
                          InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getInventoryCitizen(), stack -> stack == armorStack);
                        if (slot > -1)
                        {
                            InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, building);
                        }
                    }

                    final ItemStack newStack = bestHandler.getStackInSlot(bestSlot);
                    InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(bestHandler, bestSlot, worker.getInventoryCitizen());
                    final int slot = InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getInventoryCitizen(), stack -> stack == newStack);
                    if (slot > -1)
                    {
                        worker.getInventoryCitizen().transferArmorToSlot(item.getType(), slot);
                    }
                }
                else
                {
                    if (!ItemStackUtils.isEmpty(worker.getItemBySlot(item.getType())))
                    {
                        final int slot =
                          InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getInventoryCitizen(), stack -> stack == worker.getItemBySlot(item.getType()));
                        if (slot > -1)
                        {
                            InventoryUtils.transferItemStackIntoNextFreeSlotInProvider(worker.getInventoryCitizen(), slot, building);
                        }
                    }

                    // Used for further comparisons, set to the right inventory slot afterwards
                    worker.setItemSlot(item.getType(), bestHandler.getStackInSlot(bestSlot));
                    InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(bestHandler, bestSlot, worker.getInventoryCitizen());
                }
            }
        }

        equipInventoryArmor();
    }

    @Override
    public IAIState afterDump()
    {
        return PREPARING;
    }

    /**
     * Equips armor existing in inventory
     */
    public void equipInventoryArmor()
    {
        cleanVisibleSlots();
        final Set<EquipmentSlot> equipment = new HashSet<>();
        for (final GuardGear item : itemsNeeded.get(building.getBuildingLevelEquivalent() - 1))
        {
            if (equipment.contains(item.getType()))
            {
                continue;
            }
            if (item.getType().isArmor())
            {
                if (building.getBuildingLevelEquivalent() >= item.getMinBuildingLevelRequired() && building.getBuildingLevelEquivalent() <= item.getMaxBuildingLevelRequired())
                {
                    int slot = InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getInventoryCitizen(), item);
                    if (slot <= -1)
                    {
                        continue;
                    }

                    equipment.add(item.getType());
                    final ItemStack current = worker.getInventoryCitizen().getArmorInSlot(item.getType());
                    if (!current.isEmpty() && current.has(DataComponents.EQUIPPABLE))
                    {
                        final ItemStack candidate = worker.getInventoryCitizen().getStackInSlot(slot);
                        final int currentLevel = item.getItemNeeded().getMiningLevel(current);
                        final int newLevel = item.getItemNeeded().getMiningLevel(candidate);
                        // The same tie-break as in atBuildingActions, and it also stops a pointless swap between two
                        // identical pieces, which the old "keep only if strictly better" test allowed.
                        if (currentLevel > newLevel
                              || (currentLevel == newLevel
                                    && ItemStackUtils.getMaxEnchantmentLevel(current) >= ItemStackUtils.getMaxEnchantmentLevel(candidate)))
                        {
                            continue;
                        }
                    }
                    worker.getInventoryCitizen().transferArmorToSlot(item.getType(), slot);
                }
            }
            else
            {
                if (ItemStackUtils.isEmpty(worker.getItemBySlot(item.getType())) && building.getBuildingLevelEquivalent() >= item.getMinBuildingLevelRequired()
                      && building.getBuildingLevelEquivalent() <= item.getMaxBuildingLevelRequired())
                {
                    equipment.add(item.getType());
                    int slot = InventoryUtils.findFirstSlotInItemHandlerNotEmptyWith(worker.getInventoryCitizen(), item);
                    if (slot > -1)
                    {
                        worker.setItemSlot(item.getType(), worker.getInventoryCitizen().getStackInSlot(slot));
                    }
                }
            }
        }
    }

    /**
     * Removes currently equipped shield
     */
    public void cleanVisibleSlots()
    {
        final ItemStack stack = worker.getItemBySlot(EquipmentSlot.OFFHAND);
        if (stack.isEmpty()
              || InventoryUtils.findFirstSlotInItemHandlerWith(getInventory(), itemStack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, itemStack, false, true)) == -1)
        {
            worker.setItemSlot(EquipmentSlot.OFFHAND, ItemStackUtils.EMPTY);
        }
        worker.setItemSlot(EquipmentSlot.HEAD, ItemStackUtils.EMPTY);
        worker.setItemSlot(EquipmentSlot.CHEST, ItemStackUtils.EMPTY);
        worker.setItemSlot(EquipmentSlot.LEGS, ItemStackUtils.EMPTY);
        worker.setItemSlot(EquipmentSlot.FEET, ItemStackUtils.EMPTY);
    }
}
