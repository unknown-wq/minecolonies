package com.ldtteam.domumornamentum.datagen.bricks;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.BrickBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class BrickRecipeProvider extends FabricRecipeProvider {

    public BrickRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(final HolderLookup.Provider registryLookup,
        final BootstrapContext<Recipe<?>> recipeOutput,
        final BootstrapContext<Advancement> advancementOutput) {
        return new RecipeProvider(recipeOutput, advancementOutput) {
            @Override
            public void buildRecipes() {
                ModBlocks.getInstance().getBricks().forEach(brickBlock -> brickBlockRecipe(this.output, brickBlock));
            }

            private void brickBlockRecipe(RecipeOutput recipeWriter, BrickBlock brickBlock) {
                final ShapelessRecipeBuilder builder = shapeless(RecipeCategory.TOOLS, brickBlock, 4);
                builder.requires(brickBlock.getType().getIngredient(), 2);
                builder.requires(brickBlock.getType().getIngredient2(), 2);
                builder.unlockedBy("has_item1_" + Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(brickBlock.asItem().asItem())).toString().replace(":", "_"), has(brickBlock.getType().getIngredient()));
                builder.unlockedBy("has_item2_" + Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(brickBlock.asItem().asItem())).toString().replace(":", "_"), has(brickBlock.getType().getIngredient()));
                builder.save(recipeWriter);
            }
        };
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Brick Blocks Recipe Provider";
    }
}
