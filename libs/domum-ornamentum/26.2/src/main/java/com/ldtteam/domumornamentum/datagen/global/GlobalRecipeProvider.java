package com.ldtteam.domumornamentum.datagen.global;

import com.ldtteam.domumornamentum.block.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Port note (26.2 / Fabric): {@code RecipeProvider} is no longer a {@code DataProvider} you extend directly. It is
 * built around {@code RecipeProvider(HolderLookup.Provider, RecipeOutput)} with an abstract, argument-less
 * {@code buildRecipes()} ({@code /opt/mc-src/net/minecraft/data/recipes/RecipeProvider.java:102,111}), and the
 * {@code DataProvider} half lives in {@code RecipeProvider.Runner}, of which
 * {@link FabricRecipeProvider} is the Fabric flavour. {@code has(...)} and {@code shaped/shapeless(...)} are
 * instance methods of the inner provider now, and the old {@code ShapedRecipeBuilder.shaped(category, item, count)}
 * static took an extra leading {@code HolderGetter<Item>}, so the inherited helpers are used instead.
 */
public class GlobalRecipeProvider extends FabricRecipeProvider {

    public GlobalRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(final HolderLookup.Provider registryLookup, final RecipeOutput exporter) {
        return new RecipeProvider(registryLookup, exporter) {
            @Override
            public void buildRecipes() {
                buildCutterRecipe(exporter);
                buildBarrelRecipe(exporter);
            }

            private void buildCutterRecipe(RecipeOutput writer) {
                final ShapedRecipeBuilder cutterRecipeBuilder = shaped(RecipeCategory.TOOLS, ModBlocks.getInstance().getArchitectsCutter().asItem(), 1);
                cutterRecipeBuilder.define('X', Items.IRON_INGOT);
                cutterRecipeBuilder.define('S', Items.STONE_SLAB);
                cutterRecipeBuilder.define('L', ItemTags.LOGS);
                cutterRecipeBuilder.pattern(" X ");
                cutterRecipeBuilder.pattern("SSS");
                cutterRecipeBuilder.pattern("LLL");
                cutterRecipeBuilder.unlockedBy("has_iron_ingot", has(Items.IRON_INGOT));
                cutterRecipeBuilder.unlockedBy("has_stone_slab", has(Items.STONE_SLAB));
                cutterRecipeBuilder.unlockedBy("has_log", has(ItemTags.LOGS));
                cutterRecipeBuilder.save(writer);
            }

            private void buildBarrelRecipe(RecipeOutput writer) {
                shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.getInstance().getStandingBarrel())
                        .define('S', Items.STICK)
                        .define('W', ItemTags.PLANKS)
                        .pattern("SWS")
                        .pattern("SWS")
                        .pattern("SWS")
                        .unlockedBy("has_stick", has(Items.STICK))
                        .unlockedBy("has_planks", has(ItemTags.PLANKS))
                        .save(writer);

                shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.getInstance().getLayingBarrel())
                        .define('S', Items.STICK)
                        .define('W', ItemTags.PLANKS)
                        .pattern("SSS")
                        .pattern("WWW")
                        .pattern("SSS")
                        .unlockedBy("has_stick", has(Items.STICK))
                        .unlockedBy("has_planks", has(ItemTags.PLANKS))
                        .save(writer);
            }
        };
    }

    @NotNull
    @Override
    public String getName() {
        return "Global Blocks Recipe Provider";
    }
}
