package com.minecolonies.apiimp;

import com.ldtteam.common.config.Configurations;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import com.minecolonies.api.colony.ICitizenDataManager;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries.BuildingExtensionEntry;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.registry.IBuildingDataManager;
import com.minecolonies.api.colony.colonyEvents.registry.ColonyEventDescriptionTypeRegistryEntry;
import com.minecolonies.api.colony.colonyEvents.registry.ColonyEventTypeRegistryEntry;
import com.minecolonies.api.colony.guardtype.GuardType;
import com.minecolonies.api.colony.guardtype.registry.IGuardTypeDataManager;
import com.minecolonies.api.colony.guardtype.registry.ModGuardTypes;
import com.minecolonies.api.colony.interactionhandling.registry.IInteractionResponseHandlerDataManager;
import com.minecolonies.api.colony.interactionhandling.registry.InteractionResponseHandlerEntry;
import com.minecolonies.api.colony.jobs.registry.IJobDataManager;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.compatibility.IFurnaceRecipes;
import com.minecolonies.api.configuration.ClientConfiguration;
import com.minecolonies.api.configuration.CommonConfiguration;
import com.minecolonies.api.configuration.ServerConfiguration;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.crafting.registry.RecipeTypeEntry;
import com.minecolonies.api.entity.citizen.happiness.HappinessRegistry;
import com.minecolonies.api.entity.mobs.registry.IMobAIRegistry;
import com.minecolonies.api.entity.pathfinding.registry.IPathNavigateRegistry;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.eventbus.DefaultEventBus;
import com.minecolonies.api.eventbus.EventBus;
import com.minecolonies.api.quests.registries.QuestRegistries;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ModResearchEffects;
import com.minecolonies.api.research.ModResearchRequirements;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.CitizenDataManager;
import com.minecolonies.core.colony.ColonyManager;
import com.minecolonies.core.colony.buildings.registry.BuildingDataManager;
import com.minecolonies.core.colony.buildings.registry.GuardTypeDataManager;
import com.minecolonies.core.colony.interactionhandling.registry.InteractionResponseHandlerManager;
import com.minecolonies.core.colony.jobs.registry.JobDataManager;
import com.minecolonies.core.entity.mobs.registry.MobAIRegistry;
import com.minecolonies.core.entity.pathfinding.registry.PathNavigateRegistry;
import com.minecolonies.core.research.GlobalResearchTree;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

/**
 * Root API implementation.
 * <p>
 * <b>Port note (contract C1).</b> NeoForge's {@code NewRegistryEvent} is gone, and with it
 * {@code IMinecoloniesAPI#onRegistryNewRegistry}. The eighteen mod registries are now built eagerly with
 * {@link FabricRegistryBuilder} in the static initialiser below, so that simply touching this class -- which
 * every {@code apiimp.initializer.*} class does, because that is where the {@link ResourceKey}s live --
 * guarantees the registry exists before the first {@code Registry.register} call against it.
 * <p>
 * The instance getters kept their signatures and just hand back the static registries.
 */
public class CommonMinecoloniesAPIImpl implements IMinecoloniesAPI
{
    public static final ResourceKey<Registry<BuildingEntry>>          BUILDINGS           = key("buildings");
    public static final ResourceKey<Registry<BuildingExtensionEntry>> BUILDING_EXTENSIONS = key("building_extensions");
    public static final ResourceKey<Registry<JobEntry>>               JOBS                = key("jobs");
    public static final ResourceKey<Registry<GuardType>> GUARD_TYPES = key("guardtypes");
    public static final ResourceKey<Registry<InteractionResponseHandlerEntry>> INTERACTION_RESPONSE_HANDLERS = key("interactionresponsehandlers");
    public static final ResourceKey<Registry<ColonyEventTypeRegistryEntry>> COLONY_EVENT_TYPES = key("colonyeventtypes");
    public static final ResourceKey<Registry<ColonyEventDescriptionTypeRegistryEntry>> COLONY_EVENT_DESC_TYPES = key("colonyeventdesctypes");
    public static final ResourceKey<Registry<CraftingType>> CRAFTING_TYPES = key("craftingtypes");
    public static final ResourceKey<Registry<RecipeTypeEntry>>                                  RECIPE_TYPE_ENTRIES        = key("recipetypeentries");
    public static final ResourceKey<Registry<ModResearchRequirements.ResearchRequirementEntry>> RESEARCH_REQUIREMENT_TYPES = key("researchrequirementtypes");
    public static final ResourceKey<Registry<ModResearchEffects.ResearchEffectEntry>>           RESEARCH_EFFECT_TYPES      = key("researcheffecttypes");
    public static final ResourceKey<Registry<QuestRegistries.ObjectiveEntry>>         QUEST_OBJECTIVES           = key("questobjectives");
    public static final ResourceKey<Registry<QuestRegistries.RewardEntry>> QUEST_REWARDS = key("questrewards");
    public static final ResourceKey<Registry<QuestRegistries.TriggerEntry>> QUEST_TRIGGERS = key("questtriggers");
    public static final ResourceKey<Registry<QuestRegistries.DialogueAnswerEntry>> QUEST_ANSWER_RESULTS = key("questanswerresults");
    public static final ResourceKey<Registry<HappinessRegistry.HappinessFactorTypeEntry>> HAPPINESS_FACTOR_TYPES = key("happinessfactortypes");
    public static final ResourceKey<Registry<HappinessRegistry.HappinessFunctionEntry>> HAPPINESS_FUNCTION = key("happinessfunction");
    public static final ResourceKey<Registry<EquipmentTypeEntry>> EQUIPMENT_TYPES = key("equipmenttypes");

    public static final Registry<BuildingEntry>                                    BUILDING_REGISTRY             = syncedRegistry(BUILDINGS);
    public static final Registry<BuildingExtensionEntry>                           BUILDING_EXTENSION_REGISTRY   = syncedRegistry(BUILDING_EXTENSIONS);
    public static final Registry<JobEntry>                                         JOB_REGISTRY                  = syncedRegistry(JOBS);
    public static final Registry<GuardType>                                        GUARD_TYPE_REGISTRY           = defaultedSyncedRegistry(GUARD_TYPES, ModGuardTypes.KNIGHT_ID);
    public static final Registry<InteractionResponseHandlerEntry>                  INTERACTION_HANDLER_REGISTRY  = syncedRegistry(INTERACTION_RESPONSE_HANDLERS);
    public static final Registry<ColonyEventTypeRegistryEntry>                     COLONY_EVENT_REGISTRY         = syncedRegistry(COLONY_EVENT_TYPES);
    public static final Registry<ColonyEventDescriptionTypeRegistryEntry>          COLONY_EVENT_DESC_REGISTRY    = syncedRegistry(COLONY_EVENT_DESC_TYPES);
    public static final Registry<CraftingType>                                     CRAFTING_TYPE_REGISTRY        = syncedRegistry(CRAFTING_TYPES);
    public static final Registry<RecipeTypeEntry>                                  RECIPE_TYPE_REGISTRY          =
      defaultedSyncedRegistry(RECIPE_TYPE_ENTRIES, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "classic"));
    public static final Registry<ModResearchRequirements.ResearchRequirementEntry> RESEARCH_REQUIREMENT_REGISTRY = syncedRegistry(RESEARCH_REQUIREMENT_TYPES);
    public static final Registry<ModResearchEffects.ResearchEffectEntry>           RESEARCH_EFFECT_REGISTRY      = syncedRegistry(RESEARCH_EFFECT_TYPES);
    public static final Registry<QuestRegistries.ObjectiveEntry>                   QUEST_OBJECTIVE_REGISTRY      = syncedRegistry(QUEST_OBJECTIVES);
    public static final Registry<QuestRegistries.RewardEntry>                      QUEST_REWARD_REGISTRY         = syncedRegistry(QUEST_REWARDS);
    public static final Registry<QuestRegistries.TriggerEntry>                     QUEST_TRIGGER_REGISTRY        = syncedRegistry(QUEST_TRIGGERS);
    public static final Registry<QuestRegistries.DialogueAnswerEntry>              QUEST_ANSWER_REGISTRY         = syncedRegistry(QUEST_ANSWER_RESULTS);
    public static final Registry<HappinessRegistry.HappinessFactorTypeEntry>       HAPPINESS_FACTOR_REGISTRY     = syncedRegistry(HAPPINESS_FACTOR_TYPES);
    public static final Registry<HappinessRegistry.HappinessFunctionEntry>         HAPPINESS_FUNCTION_REGISTRY   = syncedRegistry(HAPPINESS_FUNCTION);
    public static final Registry<EquipmentTypeEntry>                               EQUIPMENT_TYPE_REGISTRY       = syncedRegistry(EQUIPMENT_TYPES);

    private final IColonyManager                         colonyManager          = new ColonyManager();
    private final ICitizenDataManager                    citizenDataManager     = new CitizenDataManager();
    private final IMobAIRegistry                         mobAIRegistry          = new MobAIRegistry();
    private final IPathNavigateRegistry                  pathNavigateRegistry   = new PathNavigateRegistry();
    private final IBuildingDataManager                   buildingDataManager    = new BuildingDataManager();
    private final IJobDataManager                        jobDataManager         = new JobDataManager();
    private final IGuardTypeDataManager                  guardTypeDataManager   = new GuardTypeDataManager();
    private final IInteractionResponseHandlerDataManager interactionDataManager = new InteractionResponseHandlerManager();
    private final IGlobalResearchTree                    globalResearchTree     = new GlobalResearchTree();

    private EventBus eventBus = new DefaultEventBus();

    /**
     * Class-load hook. Building the registries happens in the static initialiser; calling this from the mod
     * entry point is what pins the moment it happens (contract C1).
     */
    public static void init()
    {
    }

    @Override
    @NotNull
    public IColonyManager getColonyManager()
    {
        return colonyManager;
    }

    @Override
    @NotNull
    public ICitizenDataManager getCitizenDataManager()
    {
        return citizenDataManager;
    }

    @Override
    @NotNull
    public IMobAIRegistry getMobAIRegistry()
    {
        return mobAIRegistry;
    }

    @Override
    @NotNull
    public IPathNavigateRegistry getPathNavigateRegistry()
    {
        return pathNavigateRegistry;
    }

    @Override
    @NotNull
    public IBuildingDataManager getBuildingDataManager()
    {
        return buildingDataManager;
    }

    @Override
    @NotNull
    public Registry<BuildingEntry> getBuildingRegistry()
    {
        return BUILDING_REGISTRY;
    }

    @Override
    @NotNull
    public Registry<BuildingExtensionEntry> getBuildingExtensionRegistry()
    {
        return BUILDING_EXTENSION_REGISTRY;
    }

    @Override
    public IJobDataManager getJobDataManager()
    {
        return jobDataManager;
    }

    @Override
    public Registry<JobEntry> getJobRegistry()
    {
        return JOB_REGISTRY;
    }

    @Override
    public Registry<InteractionResponseHandlerEntry> getInteractionResponseHandlerRegistry()
    {
        return INTERACTION_HANDLER_REGISTRY;
    }

    @Override
    public IGuardTypeDataManager getGuardTypeDataManager()
    {
        return guardTypeDataManager;
    }

    @Override
    public Registry<GuardType> getGuardTypeRegistry()
    {
        return GUARD_TYPE_REGISTRY;
    }

    @Override
    public IModelTypeRegistry getModelTypeRegistry()
    {
        return null;
    }

    @Override
    public Configurations<ClientConfiguration, ServerConfiguration, CommonConfiguration> getConfig()
    {
        return MineColonies.getConfig();
    }

    @Override
    public IFurnaceRecipes getFurnaceRecipes()
    {
        return IColonyManager.getInstance().getCompatibilityManager().getFurnaceRecipes();
    }

    @Override
    public IInteractionResponseHandlerDataManager getInteractionResponseHandlerDataManager()
    {
        return interactionDataManager;
    }

    @Override
    public IGlobalResearchTree getGlobalResearchTree()
    {
        return globalResearchTree;
    }

    @Override
    public Registry<ModResearchRequirements.ResearchRequirementEntry> getResearchRequirementRegistry()
    {
        return RESEARCH_REQUIREMENT_REGISTRY;
    }

    @Override
    public Registry<ModResearchEffects.ResearchEffectEntry> getResearchEffectRegistry()
    {
        return RESEARCH_EFFECT_REGISTRY;
    }

    private static <T> ResourceKey<Registry<T>> key(final String registryName)
    {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Constants.MOD_ID, registryName));
    }

    /**
     * Builds a synced mod registry with no default entry. NeoForge's {@code new RegistryBuilder<>(key).sync(true)}
     * maps onto {@link RegistryAttribute#SYNCED}.
     * <p>
     * Port note (26.2): upstream passed {@code defaultKey(minecolonies:null)} here, but <b>nothing is ever
     * registered under that name</b> in any of these sixteen registries. Vanilla is stricter than NeoForge was:
     * {@code BuiltInRegistries#validate} resolves every defaulted registry's default entry during
     * {@code bootStrap()}, so an unpopulated default key crashes the game before the title screen with
     * {@code NullPointerException: Cannot invoke "Holder$Reference.value()" because "this.defaultValue" is null}.
     * A registry with an unregistered default is a defaulted registry in name only -- any lookup that fell
     * through to it would have dereferenced the same null -- so these are plain registries now. The two that do
     * name a real, registered entry ({@code guardtypes} and {@code recipetypes}) still use
     * {@link #defaultedSyncedRegistry}.
     *
     * @param registryKey the registry key.
     * @return the built and root-registered registry.
     */
    private static <T> Registry<T> syncedRegistry(final ResourceKey<Registry<T>> registryKey)
    {
        return FabricRegistryBuilder.create(registryKey)
                 .attribute(RegistryAttribute.SYNCED)
                 .attribute(RegistryAttribute.MODDED)
                 .buildAndRegister();
    }

    /**
     * Builds a synced mod registry whose default entry really exists, see {@link #syncedRegistry}.
     *
     * @param registryKey the registry key.
     * @param defaultKey  the entry to fall back to; it <b>must</b> be registered, or the game will not boot.
     * @return the built and root-registered registry.
     */
    private static <T> Registry<T> defaultedSyncedRegistry(final ResourceKey<Registry<T>> registryKey, final Identifier defaultKey)
    {
        return FabricRegistryBuilder.createDefaulted(registryKey, defaultKey)
                 .attribute(RegistryAttribute.SYNCED)
                 .attribute(RegistryAttribute.MODDED)
                 .buildAndRegister();
    }

    @Override
    public Registry<ColonyEventTypeRegistryEntry> getColonyEventRegistry()
    {
        return COLONY_EVENT_REGISTRY;
    }

    @Override
    public Registry<ColonyEventDescriptionTypeRegistryEntry> getColonyEventDescriptionRegistry()
    {
        return COLONY_EVENT_DESC_REGISTRY;
    }

    @Override
    public Registry<RecipeTypeEntry> getRecipeTypeRegistry()
    {
        return RECIPE_TYPE_REGISTRY;
    }

    @Override
    public Registry<CraftingType> getCraftingTypeRegistry()
    {
        return CRAFTING_TYPE_REGISTRY;
    }

    @Override
    public Registry<QuestRegistries.RewardEntry> getQuestRewardRegistry()
    {
        return QUEST_REWARD_REGISTRY;
    }

    @Override
    public Registry<QuestRegistries.ObjectiveEntry> getQuestObjectiveRegistry()
    {
        return QUEST_OBJECTIVE_REGISTRY;
    }

    @Override
    public Registry<QuestRegistries.TriggerEntry> getQuestTriggerRegistry()
    {
        return QUEST_TRIGGER_REGISTRY;
    }

    @Override
    public Registry<QuestRegistries.DialogueAnswerEntry> getQuestDialogueAnswerRegistry()
    {
        return QUEST_ANSWER_REGISTRY;
    }

    @Override
    public Registry<HappinessRegistry.HappinessFactorTypeEntry> getHappinessTypeRegistry()
    {
        return HAPPINESS_FACTOR_REGISTRY;
    }

    @Override
    public Registry<HappinessRegistry.HappinessFunctionEntry> getHappinessFunctionRegistry()
    {
        return HAPPINESS_FUNCTION_REGISTRY;
    }

    @Override
    public Registry<EquipmentTypeEntry> getEquipmentTypeRegistry()
    {
        return EQUIPMENT_TYPE_REGISTRY;
    }

    @Override
    public EventBus getEventBus()
    {
        return eventBus;
    }
}

