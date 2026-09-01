package com.minecolonies.core.colony.jobs.guard;

import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import net.minecraft.resources.Identifier;

import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.entity.ai.workers.guard.EntityAIRange;
import com.minecolonies.core.util.AttributeModifierUtils;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import static com.minecolonies.api.util.constant.CitizenConstants.GUARD_HEALTH_MOD_LEVEL_NAME;
import static com.minecolonies.api.util.constant.GuardConstants.RANGER_HP_BONUS;
import static com.minecolonies.api.util.constant.GuardConstants.RANGER_HP_LEVEL_DIVISOR;

/**
 * The Ranger's Job class
 *
 * @author Asherslab
 */
public class JobRanger extends AbstractJobGuard<JobRanger>
{
    /**
     * Initialize citizen data.
     *
     * @param entity the citizen data.
     */
    public JobRanger(final ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public EntityAIRange generateGuardAI()
    {
        return new EntityAIRange(this);
    }

    @Override
    public void onLevelUp()
    {
        // Bonus health for rangers and marksmen (gets reset upon firing), on the same shape as JobKnight's and
        // JobDruid's and off this guard's own primary skill. AbstractJobGuard#initEntityValues re-applies it on
        // load through CitizenData#initEntityValues -> CitizenExperienceHandler#updateLevel, so the modifier
        // survives a restart even though it is transient.
        if (getCitizen().getEntity().isPresent())
        {
            final AbstractEntityCitizen citizen = getCitizen().getEntity().get();

            final AttributeModifier healthModLevel =
              new AttributeModifier(GUARD_HEALTH_MOD_LEVEL_NAME,
                getCitizen().getCitizenSkillHandler().getLevel(Skill.Agility) / (double) RANGER_HP_LEVEL_DIVISOR + RANGER_HP_BONUS,
                AttributeModifier.Operation.ADD_VALUE);
            AttributeModifierUtils.addHealthModifier(citizen, healthModLevel);
        }
    }

    @Override
    public Identifier getModel()
    {
        return ModModelTypes.ARCHER_GUARD_ID;
    }

    /**
     * Equipment type of this guard.
     * @return the type.
     */
    public EquipmentTypeEntry getEquipmentType()
    {
        // Default bow.
        return ModEquipmentTypes.bow.get();
    }
}
