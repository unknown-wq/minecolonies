package com.ldtteam.domumornamentum.datagen.global;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.util.Constants;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class MateriallyTexturedBlockRecipeProvider extends FabricRecipeProvider
{
    public MateriallyTexturedBlockRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(final HolderLookup.Provider registryLookup,
        final BootstrapContext<Recipe<?>> recipeOutput,
        final BootstrapContext<Advancement> advancementOutput) {
        return new RecipeProvider(recipeOutput, advancementOutput) {
            @Override
            public void buildRecipes() {
                BuiltInRegistries.BLOCK.forEach(
                        block -> {
                            if (Objects.requireNonNull(BuiltInRegistries.BLOCK.getKey(block)).getNamespace().equals(Constants.MOD_ID)
                                    && block instanceof IMateriallyTexturedBlock materiallyTexturedBlock) {
                                materiallyTexturedBlock.buildRecipes(this.output);
                            }
                        }
                );
            }
        };
    }

    @Override
    public @NotNull String getName()
    {
        return "Materially textured block recipes";
    }
}
