package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.crafting.CountedIngredient;
import com.minecolonies.core.recipes.FoodIngredient;
import com.minecolonies.core.recipes.PlantIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;

/**
 * Custom ingredient registration.
 * <p>
 * <b>Port note (contracts C1/C5).</b> NeoForge kept custom ingredients in a registry
 * ({@code NeoForgeRegistries.Keys.INGREDIENT_TYPES}) reached through a {@code DeferredRegister}. Fabric has no
 * such registry: {@code fabric-recipe-api-v1} keeps its own map keyed by
 * {@link CustomIngredientSerializer#getIdentifier()}, so registration is a single static call and there are no
 * {@code IngredientType} handles to hand out any more. The three {@code DeferredHolder} constants are
 * therefore gone -- nothing outside this class ever read them.
 */
public final class ModIngredientTypeInitializer
{
    private ModIngredientTypeInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModIngredientTypeInitializer but this is a Utility class.");
    }

    /**
     * Registers the custom ingredient serializers. Called from the mod entry point.
     */
    public static void init()
    {
        CustomIngredientSerializer.register(CountedIngredient.SERIALIZER);
        CustomIngredientSerializer.register(PlantIngredient.SERIALIZER);
        CustomIngredientSerializer.register(FoodIngredient.SERIALIZER);
    }
}
