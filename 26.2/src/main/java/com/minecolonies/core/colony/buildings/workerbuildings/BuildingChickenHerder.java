package com.minecolonies.core.colony.buildings.workerbuildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.crafting.GenericRecipe;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Creates a new building for the Chicken Herder.
 */
public class BuildingChickenHerder extends AbstractBuilding
{
    /**
     * Description of the job executed in the hut.
     */
    private static final String JOB = "chickenherder";

    /**
     * The hut name, used for the lang string in the GUI
     */
    private static final String HUT_NAME = "chickenherderhut";

    /**
     * Max building level of the hut.
     */
    private static final int MAX_BUILDING_LEVEL = 5;

    /**
     * Instantiates the building.
     *
     * @param c the colony.
     * @param l the location.
     */
    public BuildingChickenHerder(final IColony c, final BlockPos l)
    {
        super(c, l);
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return JOB;
    }

    /**
     * Chicken herding module
     */
    public static class HerdingModule extends AnimalHerdingModule
    {
        public HerdingModule()
        {
            super(ModJobs.chickenHerder.get(), a -> a instanceof Chicken, new ItemStorage(Items.WHEAT_SEEDS, 2), EntityTypes.CHICKEN);
        }

        @NotNull
        @Override
        public List<IGenericRecipe> getRecipesForDisplayPurposesOnly(@NotNull Animal animal)
        {
            final List<IGenericRecipe> recipes = new ArrayList<>(super.getRecipesForDisplayPurposesOnly(animal));

            // Every item in #minecraft:eggs, not just the white one: a 26.2 chicken lays by variant, so a warm
            // chicken gives brown eggs and a cold one blue. withOutputs is "one of these will be produced", which is
            // exactly what a variant does, and this list is display only -- the request system is untouched.
            final List<ItemStack> eggs = StreamSupport.stream(BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.EGGS).spliterator(), false)
                    .map(ItemStack::new)
                    .toList();

            recipes.add(GenericRecipe.builder()
                    .withOutputs(eggs.isEmpty() ? List.of(new ItemStack(Items.EGG)) : eggs)
                    .withRequiredEntity(animal.getType())
                    .build());

            return recipes;
        }
    }
}
