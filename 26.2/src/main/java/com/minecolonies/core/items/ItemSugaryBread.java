package com.minecolonies.core.items;

import com.minecolonies.api.util.constant.TranslationConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sweet Bread, made by the baker. Adds speed, removes poison
 */
public class ItemSugaryBread extends ItemFood
{

    /**
     * Setup the food definition
     */
    private static FoodProperties sweetBread = (new FoodProperties.Builder())
                                        .nutrition(6)
                                        .saturationModifier(0.7F)
                                        .build();

    /**
     * 26.2: FoodProperties no longer carries status effects -- they moved to the CONSUMABLE data component
     * (see net.minecraft.world.item.component.Consumables). MobEffects.SPEED is now MobEffects.SPEED.
     */
    private static final Consumable SWEET_BREAD_CONSUMABLE = Consumables.defaultFood()
                                        .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SPEED, 600), 1.0F))
                                        .build();

    /**
     * Sets the name, creative tab, and registers the Sweet Bread item.
     *
     * @param properties the properties.
     */
    public ItemSugaryBread(final Properties properties)
    {
        super(properties.food(sweetBread, SWEET_BREAD_CONSUMABLE), 1);
    }

   /**
    * Remove the poison effect
    */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level worldIn, LivingEntity entityLiving) {

        if (!worldIn.isClientSide())
        {
            entityLiving.removeEffect(MobEffects.POISON);
        }
  
        return super.finishUsingItem(stack, worldIn, entityLiving);
    }    
    
    @Override
    public void appendHoverText(@NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> tooltip,
      @NotNull final TooltipFlag flagIn)
    {
        final MutableComponent guiHint = Component.translatableEscape(TranslationConstants.COM_MINECOLONIES_COREMOD_SUGARY_BREAD_TOOLTIP_GUI);
        guiHint.setStyle(Style.EMPTY.withColor(ChatFormatting.GRAY));
        tooltip.accept(guiHint);

        super.appendHoverText(stack, ctx, display, tooltip, flagIn);
    }
}
