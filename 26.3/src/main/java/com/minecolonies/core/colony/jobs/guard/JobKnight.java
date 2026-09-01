package com.minecolonies.core.colony.jobs.guard;

import com.minecolonies.api.colony.jobs.IJobWithColonyFlag;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;

import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.entity.ai.workers.guard.EntityAIMelee;
import com.minecolonies.core.util.AttributeModifierUtils;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.research.util.ResearchConstants.SHIELD_USAGE;
import static com.minecolonies.api.util.constant.CitizenConstants.GUARD_HEALTH_MOD_LEVEL_NAME;
import static com.minecolonies.api.util.constant.GuardConstants.KNIGHT_HP_BONUS;

/**
 * The Knight's job class
 *
 * @author Asherslab
 */
public class JobKnight extends AbstractJobGuard<JobKnight> implements IJobWithColonyFlag
{
    /**
     * Desc of knight job.
     */
    public static final String DESC = "com.minecolonies.coremod.job.knight";

    /**
     * Initialize citizen data.
     *
     * @param entity the citizen data.
     */
    public JobKnight(final ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public EntityAIMelee generateGuardAI()
    {
        return new EntityAIMelee(this);
    }

    @Override
    public void onLevelUp()
    {
        // Bonus Health for knights(gets reset upon Firing)
        if (getCitizen().getEntity().isPresent())
        {
            final AbstractEntityCitizen citizen = getCitizen().getEntity().get();

            // +1 Heart every 2 level
            final AttributeModifier healthModLevel =
              new AttributeModifier(GUARD_HEALTH_MOD_LEVEL_NAME,
                getCitizen().getCitizenSkillHandler().getLevel(Skill.Stamina) + KNIGHT_HP_BONUS,
                AttributeModifier.Operation.ADD_VALUE);
            AttributeModifierUtils.addHealthModifier(citizen, healthModLevel);
        }
    }

    @Override
    public Identifier getModel()
    {
        return ModModelTypes.KNIGHT_GUARD_ID;
    }

    @Override
    public boolean ignoresDamage(@NotNull final DamageSource damageSource)
    {
        // The shield has to actually be up. This used to accept a shield anywhere in the knight's pack -- and then
        // equip and raise it as a side effect of being asked whether the damage counted -- so a researched knight
        // standing idle with a spare shield in his bag was simply immune to creepers, ghasts and TNT, facing away or
        // not. MeleeCombatAI#attackProtect is what raises the shield, every eight ticks while the knight is fighting
        // and not about to swing, so a knight in a fight still shrugs off a creeper and one caught unawares does not.
        if (damageSource.is(DamageTypeTags.IS_EXPLOSION) && this.getColony().getResearchManager().getResearchEffects().getEffectStrength(SHIELD_USAGE) > 0
              && this.getCitizen().getEntity().isPresent())
        {
            final AbstractEntityCitizen worker = this.getCitizen().getEntity().get();
            if (worker.isUsingItem() && worker.getUsedItemHand() == InteractionHand.OFF_HAND
                  && worker.getInventoryCitizen().getHeldItem(InteractionHand.OFF_HAND).is(Items.SHIELD))
            {
                return true;
            }
        }
        return super.ignoresDamage(damageSource);
    }

    @Override
    public void onColonyFlagChanged()
    {
        if (this.getCitizen().getEntity().isPresent())
        {
            final AbstractEntityCitizen worker = this.getCitizen().getEntity().get();
            CitizenItemUtils.setHeldItem(worker, InteractionHand.OFF_HAND, InventoryUtils.findFirstSlotInItemHandlerWith(this.getCitizen().getInventory(), Items.SHIELD));
            worker.startUsingItem(InteractionHand.OFF_HAND);
            ItemStack shieldStack = worker.getInventoryCitizen().getHeldItem(InteractionHand.OFF_HAND);
            if (!shieldStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).equals(worker.getCitizenData().getColony().getColonyFlag()))
            {
                shieldStack.set(DataComponents.BANNER_PATTERNS, worker.getCitizenData().getColony().getColonyFlag());
                worker.getInventoryCitizen().markDirty();
            }
        }
    }
}
