package com.minecolonies.core.entity.ai.workers.guard;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.jobs.guard.JobCavalry;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;

import com.minecolonies.core.items.ItemSpear;

import com.google.common.base.Suppliers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;

import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_DAMAGE_MULTIPLIER;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_RANGE_MULTIPLIER;

public class CavalryCombatAI extends MeleeCombatAI
{
    /**
     * Combat icon
     */
    private final static VisibleCitizenStatus CAVALRY_COMBAT_ICON =
      new VisibleCitizenStatus(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/icons/work/cavalry_combat.png"), "com.minecolonies.gui.visiblestatus.cavalry_combat");

    /**
     * The reach profile vanilla puts on every one of its spears, used for spears that carry none of their own.
     * Resolved once and lazily, because item stacks cannot be built while the registries are still loading.
     */
    private static final com.google.common.base.Supplier<AttackRange> VANILLA_SPEAR_RANGE = Suppliers.memoize(
      () -> new ItemStack(Items.IRON_SPEAR).getOrDefault(DataComponents.ATTACK_RANGE,
        new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F)));

    public CavalryCombatAI(final EntityCitizen owner, final ITickRateStateMachine<?> stateMachine, final AbstractEntityAIGuard<?, ?> parentAI)
    {
        super(owner, stateMachine, parentAI);
    }

    @Override
    protected double getAttackDamage()
    {
        // TODO: Allow this to improve through research
        return super.getAttackDamage() * CAVALRY_DAMAGE_MULTIPLIER;
    }

    /**
     * Gets the weapon type that the AI will look for when checking if it can attack.
     *
     * @return the weapon type.
     */
    @Override
    public EquipmentTypeEntry getWeaponType()
    {
        return JobCavalry.getWeaponType();
    }


    /**
     * Get the attack distance for cavalry units.
     * <p>
     * A mounted unit does not fight with a spearman's footwork - it has a horse under it and its own reach and damage
     * treatment - so the inherited figure it scales is a swordsman's {@code MAX_DISTANCE_FOR_ATTACK}. When the
     * cavalryman is in fact holding a spear, that came out shorter than the weapon in his hand: vanilla writes
     * {@code min_reach 2.0 / max_reach 4.5} on every one of its spears with a {@code mob_factor} of 0.5, which is
     * 2.25 blocks from the eyes for a non-player wielder, and about 2.85 once the guard's own width converts it to
     * the centre-to-centre distance this method is measured in. The cavalry figure was 2.4. Taking whichever is
     * greater lets a lance be used at the length it actually has, and leaves a cavalryman carrying anything else
     * exactly where he was.
     *
     * @return the attack distance, increased by {@link #CAVALRY_RANGE_MULTIPLIER}.
     */
    @Override
    protected double getAttackDistance()
    {
        final double mountedReach = super.getAttackDistance() * CAVALRY_RANGE_MULTIPLIER;

        return isUsingSpear() ? Math.max(mountedReach, getSpearReach()) : mountedReach;
    }

    /**
     * Whether the cavalryman currently has a spear in his main hand.
     * <p>
     * The mod's own {@link ItemSpear} and the seven vanilla spears behind {@code #minecraft:spears} both count; the
     * equipment type the stable hires against accepts either, so either can end up in the hand.
     *
     * @return true if so.
     */
    private boolean isUsingSpear()
    {
        final ItemStack held = user.getItemInHand(InteractionHand.MAIN_HAND);
        return held.getItem() instanceof ItemSpear || held.is(ItemTags.SPEARS);
    }

    /**
     * The distance at which the spear in hand can be brought to bear, in the same centre-to-centre units
     * {@link #getAttackDistance()} works in.
     * <p>
     * Vanilla writes the same {@code ATTACK_RANGE} onto all seven of its spears, so the fallback is read off a plain
     * iron spear rather than copied as literals here, and it stays right if vanilla retunes it. The mod's own
     * {@link ItemSpear} predates data components and carries no such profile; treating it as an ordinary spear is
     * more honest than inventing a second set of numbers for it.
     * <p>
     * {@code effectiveMaxRange} already halves the player's 4.5 to 2.25 for a non-player wielder. That figure is a
     * reach measured from the eyes to the target's hitbox, so the guard's own width is added to compare it against a
     * centre-to-centre distance.
     *
     * @return the maximum striking distance.
     */
    private double getSpearReach()
    {
        final AttackRange range = user.getItemInHand(InteractionHand.MAIN_HAND).get(DataComponents.ATTACK_RANGE);

        return (range != null ? range : VANILLA_SPEAR_RANGE.get()).effectiveMaxRange(user) + user.getBbWidth();
    }

    /**
     * Get the icon to display when in combat.
     *
     * @return the icon.
     */
    @Override
    protected VisibleCitizenStatus getCombatStatus()
    {
        return CAVALRY_COMBAT_ICON;
    }

}