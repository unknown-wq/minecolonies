package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blocks.ModBlocks;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Class used to handle the creativeTab of structurize.
 *
 * <p>Port note (contract C1): on Fabric a creative tab is built through
 * {@link FabricCreativeModeTab#builder()} and registered into {@link BuiltInRegistries#CREATIVE_MODE_TAB}
 * under its own {@link ResourceKey}; {@code new CreativeModeTab.Builder(Row, column)} is not usable for mods
 * because fabric-creative-tab-api-v1 assigns the row/column itself.</p>
 */
public final class ModItemGroups
{
    /**
     * Registry key of the Structurize tab.
     */
    public static final ResourceKey<CreativeModeTab> GENERAL_KEY =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Constants.resLocStruct("general"));

    public static final Supplier<CreativeModeTab> GENERAL = register(GENERAL_KEY, () -> FabricCreativeModeTab.builder()
        .icon(() -> new ItemStack(ModItems.buildTool.get()))
        .title(Component.translatable("itemGroup." + Constants.MOD_ID))
        .displayItems((config, output) -> {
            output.accept(ModBlocks.blockSubstitution.get());
            output.accept(ModBlocks.blockSolidSubstitution.get());
            output.accept(ModBlocks.blockFluidSubstitution.get());

            output.accept(ModItems.buildTool.get());
            output.accept(ModItems.shapeTool.get());
            output.accept(ModItems.scanTool.get());
            output.accept(ModItems.tagTool.get());
            output.accept(ModItems.caliper.get());
            output.accept(ModItems.blockTagSubstitution.get());
        })
        .build());

    /**
     * Private constructor to hide the implicit one.
     */
    private ModItemGroups()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Forces the static initialiser. Must run after {@link ModBlocks#init()} and {@link ModItems#init()}.
     */
    public static void init()
    {
        // intentionally empty
    }

    private static Supplier<CreativeModeTab> register(final ResourceKey<CreativeModeTab> key, final Supplier<CreativeModeTab> factory)
    {
        final CreativeModeTab tab = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, factory.get());
        return () -> tab;
    }
}
