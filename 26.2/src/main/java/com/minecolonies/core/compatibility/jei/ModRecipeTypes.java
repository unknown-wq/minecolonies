package com.minecolonies.core.compatibility.jei;

// PORT-TODO(optional-integration): DISABLED for the 26.2 port.
// This file is excluded from compilation via 26.2/optional-integrations.txt. Nothing
// outside this package depends on it -- Compatibility.jeiProxy already defaults to a
// no-op IJeiProxy, and the JourneyMap code was never referenced from the mod proper.
// To bring the integration back, delete the matching line from that list.


import com.minecolonies.api.crafting.CompostRecipe;
import com.minecolonies.core.colony.crafting.ToolUsage;
import mezz.jei.api.recipe.RecipeType;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

public class ModRecipeTypes
{
    public static final RecipeType<CropRecipeCategory.CropRecipe> CROPS =
        RecipeType.create(MOD_ID, "crops", CropRecipeCategory.CropRecipe.class);

    public static final RecipeType<CompostRecipe> COMPOSTING =
            RecipeType.create(MOD_ID, "composting", CompostRecipe.class);

    public static final RecipeType<FishermanRecipeCategory.FishingRecipe> FISHING =
            RecipeType.create(MOD_ID, "fishing", FishermanRecipeCategory.FishingRecipe.class);

    public static final RecipeType<ToolUsage> TOOLS =
            RecipeType.create(MOD_ID, "tools", ToolUsage.class);

    public static final RecipeType<FloristRecipeCategory.FloristRecipe> FLOWERS =
            RecipeType.create(MOD_ID, "flowers", FloristRecipeCategory.FloristRecipe.class);

    private ModRecipeTypes()
    {
        // purely static
    }
}
