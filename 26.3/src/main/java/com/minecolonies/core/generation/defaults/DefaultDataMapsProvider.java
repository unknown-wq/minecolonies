package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.items.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TODO(port-26.2): DISABLED — NeoForge data maps have no Fabric counterpart.
 *
 * <p>This used to be a {@code DataMapProvider} writing {@code data/neoforge/data_maps/item/compostables.json},
 * which fed NeoForge's {@code NeoForgeDataMaps.COMPOSTABLES}. Fabric has no data maps; the equivalent is the
 * runtime {@code net.fabricmc.fabric.api.registry.CompostingChanceRegistry} from
 * {@code fabric-content-registries-v0}. The provider is therefore no longer registered in
 * {@code com.minecolonies.core.generation.MineColoniesDataGenerator}.</p>
 *
 * <p><b>Nothing is lost:</b> the exact table it produced is still computed by {@link #compostables()}. Wiring it up
 * is one loop in the mod initializer:
 * {@code compostables().forEach(CompostingChanceRegistry.INSTANCE::add)}. Until that is done, MineColonies foods,
 * ingredients, crops and composted dirt cannot be put in a vanilla composter (the mod's own compost barrel is
 * unaffected — it runs off {@code minecolonies:composting} recipes and the {@code compostables*} item tags).</p>
 */
public final class DefaultDataMapsProvider
{
    private DefaultDataMapsProvider()
    {
    }

    /**
     * The compostable chance of every MineColonies item that used to appear in the {@code compostables} data map.
     *
     * @return item to compost chance, in insertion order.
     */
    @NotNull
    public static Map<Item, Float> compostables()
    {
        final Map<Item, Float> map = new LinkedHashMap<>();

        // these items aren't registered in "getAllFoods"
        addFromNutrition(map, ModItems.milkyBread.asItem(), 6f);
        addFromNutrition(map, ModItems.sugaryBread.asItem(), 6f);
        addFromNutrition(map, ModItems.goldenBread.asItem(), 6f);
        addFromNutrition(map, ModItems.chorusBread.asItem(), 6f);

        for (final Item item : ModItems.getAllIngredients())
        {
            addFromNutrition(map, item, 10f);
        }
        for (final Item item : ModItems.getAllFoods())
        {
            addFromNutrition(map, item, 6f);
        }

        map.put(ModItems.mistletoe, 0.5f);

        for (final Block block : ModBlocks.getCrops())
        {
            map.put(block.asItem(), 0.5f);
        }
        map.put(ModBlocks.blockCompostedDirt.asItem(), 1.0f);

        return map;
    }

    private static void addFromNutrition(@NotNull final Map<Item, Float> map, final Item item, final float factor)
    {
        // 26.2: Item#getFoodProperties is gone; food is the FOOD data component on the default stack.
        final FoodProperties food = new ItemStack(item).get(DataComponents.FOOD);
        if (food != null)
        {
            final float strength = Math.min(1.0f, food.nutrition() / factor);
            if (strength > 0)
            {
                map.put(item, strength);
            }
        }
    }
}
