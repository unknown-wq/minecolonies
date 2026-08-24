package com.ldtteam.domumornamentum.datagen.extra;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.ExtraBlock;
import com.ldtteam.domumornamentum.block.types.ExtraBlockType;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ExtraRecipeProvider extends FabricRecipeProvider
{
    public ExtraRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(final HolderLookup.Provider registryLookup, final RecipeOutput exporter) {
        return new RecipeProvider(registryLookup, exporter) {
            @Override
            public void buildRecipes() {
                ModBlocks.getInstance().getExtraTopBlocks().forEach(extraBlock -> extraBlockRecipe(exporter, extraBlock));
            }

            private void extraBlockRecipe(RecipeOutput writer, ExtraBlock extraBlock) {
                final ExtraBlockType type = extraBlock.getType();
                final ShapedRecipeBuilder builder = shaped(RecipeCategory.TOOLS, extraBlock, 4);
                builder.pattern("X X");
                builder.pattern(" Z ");
                builder.pattern("X X");
                builder.define('X', type.getMaterial());
                if (type.getColor() == null) {
                    builder.define('Z', type.getMaterial());
                } else {
                    builder.define('Z', Items.DYE.pick(type.getColor()));
                }
                builder.unlockedBy("has_material", has(type.getMaterial()));
                if (type.getColor() != null) {
                    builder.unlockedBy("has_dye", has(Items.DYE.pick(type.getColor())));
                }
                builder.save(writer);
            }
        };
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Extra Blocks Recipe Provider";
    }
}
