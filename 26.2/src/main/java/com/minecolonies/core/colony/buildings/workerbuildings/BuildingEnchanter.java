package com.minecolonies.core.colony.buildings.workerbuildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import com.minecolonies.api.util.Tuple;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.Constants.STACKSIZE;

/**
 * The enchanter building.
 */
public class BuildingEnchanter extends AbstractBuilding
{
    /**
     * Enchanter.
     */
    private static final String ENCHANTER = "enchanter";

    /**
     * Whether the enchanter turns ancient tomes into books. Switching this off used to mean switching the recipe off
     * in the crafting tab, which left the worker with nothing to do and no way to say so.
     */
    public static final ISettingKey<BoolSetting> PRODUCE_BOOKS =
      new SettingKey<>(BoolSetting.class, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "enchanterbooks"));

    /**
     * Whether the enchanter walks out to the huts on its station list -- to gather mana, and from hut level four to
     * spend a finished book on the worker there.
     */
    public static final ISettingKey<BoolSetting> VISIT_STATIONS =
      new SettingKey<>(BoolSetting.class, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "enchantervisits"));

    /**
     * Maximum building level
     */
    private static final int MAX_BUILDING_LEVEL = 5;

    /**
     * Hut level from which the enchanter spends its own finished books on other workers' gear.
     */
    private static final int GEAR_SERVICE_MIN_LEVEL = 4;

    /**
     * How many finished books the hut holds back for that. Anything above this still goes to the warehouse.
     */
    private static final int BOOKS_KEPT_FOR_SERVICE = 4;

    /**
     * The constructor of the building.
     *
     * @param c the colony
     * @param l the position
     */
    public BuildingEnchanter(@NotNull final IColony c, final BlockPos l)
    {
        super(c, l);
        keepX.put((stack) -> stack.getItem() == ModItems.ancientTome, new Tuple<>(STACKSIZE, true));
        // From hut level four the enchanter carries a finished book out to another hut and spends it on that worker's
        // gear, so a few have to stay here rather than all going straight to the warehouse. getRequiredItemsAndAmount
        // copies this map on every call, so the level test is read live.
        keepX.put((stack) -> getBuildingLevel() >= GEAR_SERVICE_MIN_LEVEL && stack.getItem() == Items.ENCHANTED_BOOK,
          new Tuple<>(BOOKS_KEPT_FOR_SERVICE, true));
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return ENCHANTER;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return MAX_BUILDING_LEVEL;
    }

    public static class CraftingModule extends AbstractCraftingBuildingModule.Custom
    {
        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public CraftingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }

        @Override
        public boolean addRecipe(IToken<?> token)
        {
            // Enchanter only has custom recipes for now
            return false;
        }
    }
}
