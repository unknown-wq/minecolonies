package com.minecolonies.core.items;

import com.minecolonies.core.client.render.worldevent.ColonyBlueprintRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.item.equipment.ArmorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.minecolonies.apiimp.initializer.ModItemsInitializer.GOGGLES;

public class ItemBuildGoggles extends Item
{
    /**
     * Constructor
     *
     * @param name            the name.
     * @param properties      the item properties.
     */
    public ItemBuildGoggles(
            @NotNull final String name,
            final Item.Properties properties)
    {
        // 26.2 removed ArmorItem: armour is a plain Item carrying the EQUIPPABLE data component, installed by
        // Item.Properties#humanoidArmor(ArmorMaterial, ArmorType) -- the same shape ModItemsInitializer uses for
        // every other piece of mod armour.
        super(properties.humanoidArmor(GOGGLES, ArmorType.HELMET).rarity(Rarity.UNCOMMON));
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> components,
      @NotNull final TooltipFlag flags)
    {
        super.appendHoverText(stack, ctx, display, components, flags);

        components.accept(Component.translatableEscape("\"%s\"",
                        Component.translatableEscape("item.minecolonies.build_goggles.lore")
                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        components.accept(Component.translatableEscape(ColonyBlueprintRenderer.willRenderBlueprints()
                ? "item.minecolonies.build_goggles.enabled" : "item.minecolonies.build_goggles.disabled")
                .withStyle(ChatFormatting.GRAY));
    }
}
