package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.RecipeCraftingType;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.recipes.ArchitectsCutterCraftingType;
import com.minecolonies.core.recipes.BrewingCraftingType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

public final class ModCraftingTypesInitializer
{

    private ModCraftingTypesInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModCraftingTypesInitializer but this is a Utility class.");
    }

    static
    {
            ModCraftingTypes.SMALL_CRAFTING = register(ModCraftingTypes.SMALL_CRAFTING_ID.getPath(), () -> new RecipeCraftingType<>(ModCraftingTypes.SMALL_CRAFTING_ID,
              RecipeType.CRAFTING, r -> fitsInGrid(r.value(), 2, 2)));

            ModCraftingTypes.LARGE_CRAFTING = register(ModCraftingTypes.LARGE_CRAFTING_ID.getPath(), () -> new RecipeCraftingType<>(ModCraftingTypes.LARGE_CRAFTING_ID,
              RecipeType.CRAFTING, r -> fitsInGrid(r.value(), 3, 3)
                                          && !fitsInGrid(r.value(), 2, 2)));

            ModCraftingTypes.SMELTING = register(ModCraftingTypes.SMELTING_ID.getPath(), () -> new RecipeCraftingType<>(ModCraftingTypes.SMELTING_ID,
              RecipeType.SMELTING, null));

            ModCraftingTypes.BREWING = register(ModCraftingTypes.BREWING_ID.getPath(), BrewingCraftingType::new);

            ModCraftingTypes.ARCHITECTS_CUTTER = register(ModCraftingTypes.ARCHITECTS_CUTTER_ID.getPath(), () -> new ArchitectsCutterCraftingType());
    }

    /**
     * Whether a crafting recipe fits into a grid of the given size.
     * <p>
     * Port note: 26.2 dropped {@code Recipe#canCraftInDimensions}, so the two vanilla overrides it used to
     * dispatch to are inlined here. Anything that is neither shaped nor shapeless -- special recipes, modded
     * crafting recipes -- answers {@code true}, which is what the old default implementation on
     * {@code Recipe} did.
     *
     * @param recipe the crafting recipe.
     * @param width  grid width.
     * @param height grid height.
     * @return whether it fits.
     */
    private static boolean fitsInGrid(final CraftingRecipe recipe, final int width, final int height)
    {
        if (recipe instanceof final ShapedRecipe shaped)
        {
            return shaped.getWidth() <= width && shaped.getHeight() <= height;
        }
        if (recipe instanceof final ShapelessRecipe shapeless)
        {
            return shapeless.placementInfo().ingredients().size() <= width * height;
        }
        return true;
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends CraftingType> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.CRAFTING_TYPE_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1); calling this from
     * the mod entry point is what pins the moment it happens.
     */
    public static void init()
    {
    }
}
