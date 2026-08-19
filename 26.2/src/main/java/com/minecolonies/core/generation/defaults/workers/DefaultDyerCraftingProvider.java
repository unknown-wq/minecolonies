package com.minecolonies.core.generation.defaults.workers;

import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.generation.CustomRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.BuildingConstants.MODULE_CRAFTING;

/**
 * Datagen for Dyer
 */
public class DefaultDyerCraftingProvider extends CustomRecipeProvider
{
    private static final String DYER = ModJobs.DYER_ID.getPath();

    public DefaultDyerCraftingProvider(@NotNull final PackOutput packOutput, final CompletableFuture<HolderLookup.Provider> lookupProvider)
    {
        super(packOutput, lookupProvider);
    }

    @NotNull
    @Override
    public String getName()
    {
        return "DefaultDyerCraftingProvider";
    }

    @Override
    protected void registerRecipes(@NotNull final Consumer<CustomRecipeBuilder> consumer)
    {
        recipe(DYER, MODULE_CRAFTING, "red_sand")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.SAND), 4),
                        new ItemStorage(new ItemStack(Items.DYE.pick(DyeColor.RED)))))
                .result(new ItemStack(Items.RED_SAND, 4))
                .build(consumer);

        recipe(DYER, MODULE_CRAFTING, "black_dye")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.CHARCOAL))))
                .result(new ItemStack(Items.DYE.pick(DyeColor.BLACK)))
                .minBuildingLevel(2)
                .build(consumer);

        recipe(DYER, MODULE_CRAFTING, "dark_prismarine")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.PRISMARINE, 4)),
                        new ItemStorage(new ItemStack(Items.DYE.pick(DyeColor.BLACK)))))
                .result(new ItemStack(Items.DARK_PRISMARINE, 4))
                .minBuildingLevel(3)
                .build(consumer);

        // 26.2/Fabric: cinnabar is new in 26.2 and is a decorative stone family (thirteen block items, no
        // nugget or shard form), so the plain block is the only form that is mined rather than crafted -- every
        // other member costs a crafting step first. Vanilla ships no cinnabar -> dye path, so this is a custom
        // recipe rather than one the dyer could ever be taught. Deliberately NOT mirrored into
        // minecolonies:dyer_ingredient: that tag is subtracted wholesale from the stonemason's ingredients, and
        // custom crafter recipes are injected by AbstractCraftingBuildingModule#checkForWorkerSpecificRecipes
        // without consulting the ingredient tags at all.
        recipe(DYER, MODULE_CRAFTING, "red_dye_from_cinnabar")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.CINNABAR))))
                .result(new ItemStack(Items.DYE.pick(DyeColor.RED), 4))
                .minBuildingLevel(2)
                .build(consumer);
    }
}
