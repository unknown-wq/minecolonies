package com.minecolonies.core.items;

import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.entity.mobs.barbarians.AbstractEntityBarbarianRaider;
import com.minecolonies.api.items.IChiefSwordItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.Constants.GLOW_EFFECT_DISTANCE;
import static com.minecolonies.api.util.constant.Constants.GLOW_EFFECT_DURATION;

/**
 * Class handling the Chief Sword item.
 */
public class ItemChiefSword extends Item implements IChiefSwordItem
{
    private static final int LEVITATION_EFFECT_DURATION   = 20 * 10;
    private static final int LEVITATION_EFFECT_MULTIPLIER = 2;

    /**
     * Constructor method for the Chief Sword Item
     *
     * @param properties the properties.
     */
    public ItemChiefSword(final Properties properties)
    {
        // 26.2 removed SwordItem/TieredItem/Tiers: a sword is a plain Item whose properties carry the TOOL and
        // WEAPON data components, which is exactly what ToolMaterial#applySwordProperties installs. The numbers
        // are the ones the old SwordItem.createAttributes(Tiers.WOOD, 3, -2.4F) produced.
        super(ToolMaterial.DIAMOND.applySwordProperties(properties, 3, -2.4F));
    }

    @Override
    public void inventoryTick(@NotNull final ItemStack stack,
      @NotNull final ServerLevel worldIn,
      @NotNull final Entity entityIn,
      @Nullable final EquipmentSlot equipmentSlot)
    {
        if (entityIn instanceof Player && equipmentSlot == EquipmentSlot.MAINHAND)
        {
            RaiderMobUtils.getBarbariansCloseToEntity(entityIn, GLOW_EFFECT_DISTANCE)
                .forEach(entity -> entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_EFFECT_DURATION, 0)));
        }
    }

    // 26.2: Item#hurtEnemy returns void (it returned boolean in 1.21.1).
    @Override
    public void hurtEnemy(final ItemStack stack, final LivingEntity target, final LivingEntity attacker)
    {
        if (attacker instanceof Player && target instanceof AbstractEntityBarbarianRaider)
        {
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_EFFECT_DURATION, LEVITATION_EFFECT_MULTIPLIER));
        }

        super.hurtEnemy(stack, target, attacker);
    }
}
