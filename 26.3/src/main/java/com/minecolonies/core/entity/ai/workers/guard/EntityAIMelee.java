package com.minecolonies.core.entity.ai.workers.guard;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.workers.util.GuardGear;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.jobs.guard.JobHuscarl;
import com.minecolonies.core.colony.jobs.guard.JobKnight;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static com.minecolonies.api.research.util.ResearchConstants.SHIELD_USAGE;
import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_MAXIMUM;
import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.GuardConstants.SHIELD_BUILDING_LEVEL_RANGE;
import static com.minecolonies.api.util.constant.GuardConstants.SHIELD_LEVEL_RANGE;

/**
 * Knight AI, which deals with gear specifics
 */
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class EntityAIMelee extends AbstractEntityAIGuard<JobKnight, AbstractBuildingGuards>
{
    /**
     * The spear stand-in for {@link #toolsNeeded}, or null for a huscarl, who stays on his axe.
     */
    private final List<EquipmentTypeEntry> spearInstead;

    public EntityAIMelee(@NotNull final JobKnight job)
    {
        super(job);
        super.registerTargets();

        if (job instanceof JobHuscarl)
        {
            toolsNeeded.add(ModEquipmentTypes.axe.get());
            spearInstead = null;
        }
        else
        {
            toolsNeeded.add(ModEquipmentTypes.sword.get());
            spearInstead = Collections.singletonList(ModEquipmentTypes.spear.get());
        }

        for (final List<GuardGear> list : itemsNeeded)
        {
            list.add(new GuardGear(ModEquipmentTypes.shield.get(),
              EquipmentSlot.OFFHAND,
              TOOL_LEVEL_WOOD_OR_GOLD,
              TOOL_LEVEL_MAXIMUM,
              SHIELD_LEVEL_RANGE,
              SHIELD_BUILDING_LEVEL_RANGE));
        }

        new MeleeCombatAI((EntityCitizen) worker, getStateAI(), this);
    }

    /**
     * A knight fights with a spear instead of a sword as soon as the colony has actually armed him with one.
     * <p>
     * There is no new switch for the player to find, and no new setting to translate: dropping spears into the guard
     * hut is the switch. From there the ordinary machinery does everything -- the guard puts in a request for a
     * spear within his hut's equipment level, the request resolves out of the hut like any other tool request, and
     * once the spear is in his pack {@code AbstractEntityAIFight#prepare} puts it in his hand and
     * {@code MeleeCombatAI} fights with it as a spear.
     * <p>
     * Two rules keep this from ever making a knight worse off:
     * <ul>
     *     <li>a knight who still has a working sword keeps fighting with it while the spear is on its way, so
     *     stocking spears never parks an armed guard in {@code PREPARING} waiting for a courier;</li>
     *     <li>a knight with no sword either falls back on the ordinary sword request the moment no spear is on
     *     order any more, so a colony that runs out of spears re-arms its guards instead of stranding them.</li>
     * </ul>
     *
     * @return the spear if this knight is a spearman, the fixed list ({@code sword}, or {@code axe} for a huscarl)
     *         otherwise.
     */
    @Override
    protected List<EquipmentTypeEntry> getToolsNeeded()
    {
        if (spearInstead == null || building == null)
        {
            return toolsNeeded;
        }

        final EquipmentTypeEntry spear = ModEquipmentTypes.spear.get();
        final int maxLevel = building.getMaxEquipmentLevel();

        if (InventoryUtils.isEquipmentInItemHandler(worker.getInventoryCitizen(), spear, TOOL_LEVEL_WOOD_OR_GOLD, maxLevel))
        {
            return spearInstead;
        }

        if (InventoryUtils.isEquipmentInItemHandler(worker.getInventoryCitizen(), ModEquipmentTypes.sword.get(), TOOL_LEVEL_WOOD_OR_GOLD, maxLevel))
        {
            // Armed and waiting: he fights on with the sword rather than standing in the hut for the spear.
            return toolsNeeded;
        }

        // Unarmed. If a spear is already coming, wait for that rather than ordering a second weapon on top of it.
        return hasSpearOnOrder(spear) ? spearInstead : toolsNeeded;
    }

    /**
     * On top of the usual armour restocking, put in a standing order for a spear whenever the guard's own hut is
     * holding one he is allowed to use.
     * <p>
     * Deliberately the asynchronous request, the same kind the guard's armour uses: it is a wish, not a blocker, so
     * a knight who is already carrying a sword goes on patrolling with it instead of idling at the hut until the
     * spear arrives. This runs only from {@code AbstractEntityAIFight#prepare}, which calls it only once the guard
     * is standing at his own hut -- everywhere else the hut's racks may not even be loaded.
     * {@code checkForToolOrWeaponAsync} already refuses to stack a second request on an outstanding one, so calling
     * this on every visit is idempotent.
     */
    @Override
    protected void atBuildingActions()
    {
        super.atBuildingActions();

        if (spearInstead == null || building == null)
        {
            return;
        }

        final EquipmentTypeEntry spear = ModEquipmentTypes.spear.get();
        final int maxLevel = building.getMaxEquipmentLevel();

        if (InventoryUtils.isEquipmentInItemHandler(worker.getInventoryCitizen(), spear, TOOL_LEVEL_WOOD_OR_GOLD, maxLevel))
        {
            return;
        }

        final Predicate<ItemStack> usableSpear = stack -> !ItemStackUtils.isEmpty(stack)
                                                            && spear.checkIsEquipment(stack)
                                                            && spear.getMiningLevel(stack) <= maxLevel;

        if (InventoryUtils.hasItemInProvider(building, usableSpear))
        {
            checkForToolOrWeaponAsync(spear, TOOL_LEVEL_WOOD_OR_GOLD, maxLevel);
        }
    }

    /**
     * Whether this guard already has a spear request in flight, open or delivered-but-not-yet-collected.
     *
     * @param spear the spear equipment type.
     * @return true if a spear is already on order for this guard.
     */
    private boolean hasSpearOnOrder(final EquipmentTypeEntry spear)
    {
        return !building.getOpenRequestsOfTypeFiltered(worker.getCitizenData(),
          TypeToken.of(Tool.class),
          r -> r.getRequest().getEquipmentType().equals(spear)).isEmpty()
                 || !building.getCompletedRequestsOfTypeFiltered(worker.getCitizenData(),
          TypeToken.of(Tool.class),
          r -> r.getRequest().getEquipmentType().equals(spear)).isEmpty();
    }

    @NotNull
    @Override
    protected List<ItemStorage> itemsNiceToHave()
    {
        final List<ItemStorage> list = super.itemsNiceToHave();
        if (worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(SHIELD_USAGE) > 0)
        {
            list.add(new ItemStorage(Items.SHIELD, 1));
        }
        return list;
    }
}
