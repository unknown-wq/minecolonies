package com.ldtteam.domumornamentum.datagen.floatingcarpet;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.FloatingCarpetBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Port note: NeoForge's {@code Tags.Items.STRINGS} becomes Fabric's {@code ConventionalItemTags.STRINGS}; both
 * serialize to {@code #c:strings}, so the emitted JSON is unchanged.
 */
public class FloatingCarpetRecipeProvider extends FabricRecipeProvider {

    public FloatingCarpetRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(final HolderLookup.Provider registryLookup, final RecipeOutput exporter) {
        return new RecipeProvider(registryLookup, exporter) {
            @Override
            public void buildRecipes() {
                final Map<DyeColor, Block> wools = new HashMap<>();
                wools.put(DyeColor.WHITE, Blocks.WOOL.pick(DyeColor.WHITE));
                wools.put(DyeColor.LIGHT_GRAY, Blocks.WOOL.pick(DyeColor.LIGHT_GRAY));
                wools.put(DyeColor.GRAY, Blocks.WOOL.pick(DyeColor.GRAY));
                wools.put(DyeColor.BLACK, Blocks.WOOL.pick(DyeColor.BLACK));
                wools.put(DyeColor.BROWN, Blocks.WOOL.pick(DyeColor.BROWN));
                wools.put(DyeColor.RED, Blocks.WOOL.pick(DyeColor.RED));
                wools.put(DyeColor.ORANGE, Blocks.WOOL.pick(DyeColor.ORANGE));
                wools.put(DyeColor.YELLOW, Blocks.WOOL.pick(DyeColor.YELLOW));
                wools.put(DyeColor.LIME, Blocks.WOOL.pick(DyeColor.LIME));
                wools.put(DyeColor.GREEN, Blocks.WOOL.pick(DyeColor.GREEN));
                wools.put(DyeColor.CYAN, Blocks.WOOL.pick(DyeColor.CYAN));
                wools.put(DyeColor.LIGHT_BLUE, Blocks.WOOL.pick(DyeColor.LIGHT_BLUE));
                wools.put(DyeColor.BLUE, Blocks.WOOL.pick(DyeColor.BLUE));
                wools.put(DyeColor.PURPLE, Blocks.WOOL.pick(DyeColor.PURPLE));
                wools.put(DyeColor.MAGENTA, Blocks.WOOL.pick(DyeColor.MAGENTA));
                wools.put(DyeColor.PINK, Blocks.WOOL.pick(DyeColor.PINK));

                for (final FloatingCarpetBlock block : ModBlocks.getInstance().getFloatingCarpets()) {
                    final DyeColor color = block.getColor();
                    shapeless(RecipeCategory.DECORATIONS, block, 3)
                            .requires(wools.get(color), 2)
                            .requires(ConventionalItemTags.STRINGS)
                            .group("floating_carpets")
                            .unlockedBy("has_string", has(ConventionalItemTags.STRINGS))
                            .unlockedBy("has_wool", has(wools.get(color)))
                            .save(exporter);
                }
            }
        };
    }

    @NotNull
    @Override
    public String getName() {
        return "Floating Carpet Recipe Provider";
    }
}
