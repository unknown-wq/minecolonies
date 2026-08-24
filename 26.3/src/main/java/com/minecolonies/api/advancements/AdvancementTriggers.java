package com.minecolonies.api.advancements;

import com.minecolonies.api.util.constant.Constants;
import net.minecraft.advancements.triggers.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;
import java.util.function.Supplier;

/**
 * The collection of advancement triggers for minecolonies.
 * Each trigger may correspond to multiple advancements.
 */
public class AdvancementTriggers
{
    public static final Supplier<AllTowersTrigger>            ALL_TOWERS             = register(Constants.CRITERION_ALL_TOWERS, new AllTowersTrigger());
    public static final Supplier<ArmyPopulationTrigger>       ARMY_POPULATION        = register(Constants.CRITERION_ARMY_POPULATION, new ArmyPopulationTrigger());
    public static final Supplier<BuildingAddRecipeTrigger>    BUILDING_ADD_RECIPE    = register(Constants.CRITERION_BUILDING_ADD_RECIPE, new BuildingAddRecipeTrigger());
    public static final Supplier<CitizenBuryTrigger>          CITIZEN_BURY           = register(Constants.CRITERION_CITIZEN_BURY, new CitizenBuryTrigger());
    public static final Supplier<CitizenEatFoodTrigger>       CITIZEN_EAT_FOOD       = register(Constants.CRITERION_CITIZEN_EAT_FOOD, new CitizenEatFoodTrigger());
    public static final Supplier<CitizenResurrectTrigger>     CITIZEN_RESURRECT      = register(Constants.CRITERION_CITIZEN_RESURRECT, new CitizenResurrectTrigger());
    public static final Supplier<ClickGuiButtonTrigger>       CLICK_GUI_BUTTON       = register(Constants.CRITERION_CLICK_GUI_BUTTON, new ClickGuiButtonTrigger());
    public static final Supplier<ColonyPopulationTrigger>     COLONY_POPULATION      = register(Constants.CRITERION_COLONY_POPULATION, new ColonyPopulationTrigger());
    public static final Supplier<CompleteBuildRequestTrigger> COMPLETE_BUILD_REQUEST = register(Constants.CRITERION_COMPLETE_BUILD_REQUEST, new CompleteBuildRequestTrigger());
    public static final Supplier<CreateBuildRequestTrigger>   CREATE_BUILD_REQUEST   = register(Constants.CRITERION_CREATE_BUILD_REQUEST, new CreateBuildRequestTrigger());
    public static final Supplier<DeepMineTrigger>             DEEP_MINE              = register(Constants.CRITERION_DEEP_MINE, new DeepMineTrigger());
    public static final Supplier<MaxFieldsTrigger>            MAX_FIELDS             = register(Constants.CRITERION_MAX_FIELDS, new MaxFieldsTrigger());
    public static final Supplier<OpenGuiWindowTrigger>        OPEN_GUI_WINDOW        = register(Constants.CRITERION_OPEN_GUI_WINDOW, new OpenGuiWindowTrigger());
    public static final Supplier<PlaceStructureTrigger>       PLACE_STRUCTURE        = register(Constants.CRITERION_STRUCTURE_PLACED, new PlaceStructureTrigger());
    public static final Supplier<PlaceSupplyTrigger>          PLACE_SUPPLY           = register(Constants.CRITERION_SUPPLY_PLACED, new PlaceSupplyTrigger());
    public static final Supplier<UndertakerTotemTrigger>      UNDERTAKER_TOTEM       = register(Constants.CRITERION_UNDERTAKER_TOTEM, new UndertakerTotemTrigger());

    /**
     * Register one trigger eagerly (contract C1: the field stays a {@link Supplier}).
     *
     * @param name    the trigger path.
     * @param trigger the trigger instance.
     * @param <T>     the trigger type.
     * @return a supplier of the registered trigger.
     */
    private static <T extends CriterionTrigger<?>> Supplier<T> register(final String name, final T trigger)
    {
        final T value = Registry.register(BuiltInRegistries.TRIGGER_TYPES,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, name), trigger);
        return () -> value;
    }

    /**
     * Class-load hook — registration happens eagerly in the static initialisers above (contract C1).
     */
    public static void init()
    {
    }
}
