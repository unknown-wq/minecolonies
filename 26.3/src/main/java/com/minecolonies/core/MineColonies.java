package com.minecolonies.core;

import com.ldtteam.common.config.Configurations;
import com.ldtteam.common.language.LanguageHandler;
import com.ldtteam.structurize.storage.SurvivalBlueprintHandlers;
import com.minecolonies.api.compatibility.Compatibility;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.advancements.AdvancementTriggers;
import com.minecolonies.api.configuration.ClientConfiguration;
import com.minecolonies.api.configuration.CommonConfiguration;
import com.minecolonies.api.configuration.ServerConfiguration;
import com.minecolonies.api.creativetab.ModCreativeTabs;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.items.component.ModDataComponents;
import com.minecolonies.api.loot.DeferredLootTableEntry;
import com.minecolonies.api.loot.ModLootConditions;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.ClientMinecoloniesAPIImpl;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.apiimp.initializer.*;
import com.minecolonies.core.colony.crafting.CustomRecipeManagerMessage;
import com.minecolonies.core.commands.arguments.ModArgumentTypes;
import com.minecolonies.core.colony.permissions.ColonyPermissionEventHandler;
import com.minecolonies.core.colony.requestsystem.init.RequestSystemInitializer;
import com.minecolonies.core.colony.requestsystem.init.StandardFactoryControllerInitializer;
import com.minecolonies.core.compatibility.simpleplanes.SimplePlanesAirspaceGuard;
import com.minecolonies.core.compatibility.simpleplanes.SimplePlanesBlastGuard;
import com.minecolonies.core.debug.messages.DebugEnableMessage;
import com.minecolonies.core.debug.messages.DebugEnablePathfindingMessage;
import com.minecolonies.core.debug.messages.DebugOutputMessage;
import com.minecolonies.core.debug.messages.QueryCitizenAIHistoryMessage;
import com.minecolonies.core.entity.mobs.EntityMercenary;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseDebug;
import com.minecolonies.core.event.*;
import com.minecolonies.core.network.messages.PermissionsMessage;
import com.minecolonies.core.network.messages.client.*;
import com.minecolonies.core.network.messages.client.colony.*;
import com.minecolonies.core.network.messages.server.*;
import com.minecolonies.core.network.messages.server.colony.*;
import com.minecolonies.core.network.messages.server.colony.building.*;
import com.minecolonies.core.network.messages.server.colony.building.builder.BuilderSelectWorkOrderMessage;
import com.minecolonies.core.network.messages.server.colony.building.enchanter.EnchanterWorkerSetMessage;
import com.minecolonies.core.network.messages.server.colony.building.fields.AssignFieldMessage;
import com.minecolonies.core.network.messages.server.colony.building.fields.AssignmentModeMessage;
import com.minecolonies.core.network.messages.server.colony.building.fields.FarmFieldPlotResizeMessage;
import com.minecolonies.core.network.messages.server.colony.building.fields.FarmFieldUpdateSeedMessage;
import com.minecolonies.core.network.messages.server.colony.building.guard.GuardSetMinePosMessage;
import com.minecolonies.core.network.messages.server.colony.building.home.AssignUnassignMessage;
import com.minecolonies.core.network.messages.server.colony.building.miner.MinerRepairLevelMessage;
import com.minecolonies.core.network.messages.server.colony.building.miner.MinerSetLevelMessage;
import com.minecolonies.core.network.messages.server.colony.building.postbox.PostBoxRequestMessage;
import com.minecolonies.core.network.messages.server.colony.building.university.TryResearchMessage;
import com.minecolonies.core.network.messages.server.colony.building.warehouse.SortBuildingMessage;
import com.minecolonies.core.network.messages.server.colony.building.warehouse.UpgradeWarehouseMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.*;
import com.minecolonies.core.network.messages.server.colony.citizen.*;
import com.minecolonies.core.placementhandlers.PlacementHandlerInitializer;
import com.minecolonies.core.placementhandlers.main.SuppliesHandler;
import com.minecolonies.core.placementhandlers.main.SurvivalHandler;
import com.minecolonies.core.research.GlobalResearchTreeMessage;
import com.minecolonies.core.structures.MineColoniesStructures;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import com.minecolonies.core.generation.defaults.DefaultDataMapsProvider;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.animal.equine.Horse;

/**
 * Mod entry point (contract C2).
 *
 * <p><b>Port note.</b> NeoForge handed the mod constructor a {@code ModContainer} and two event buses, and the
 * whole of registration was deferred behind them. Fabric has neither: {@link #onInitialize()} runs once, on
 * both physical sides, and every registry is written to eagerly, in the order written below. Everything
 * client-only moved to {@link MineColoniesClient}.</p>
 *
 * <p><b>The order in this method is load-bearing.</b> With {@code DeferredRegister} gone, each
 * {@code init()} call below runs a static initialiser that dereferences objects registered by an earlier one,
 * and the compiler cannot see any of it. The dependency chain, in the order it is satisfied:</p>
 * <ol>
 *     <li>config and networking first — they have no dependencies and everything else may read them;</li>
 *     <li>{@code MinecoloniesAPIProxy.setApiInstance} before <em>any</em> mod-registry initialiser, because
 *         class-loading {@link CommonMinecoloniesAPIImpl} is what creates the eighteen mod registries, and
 *         {@code ModEquipmentTypes} reaches for {@code IMinecoloniesAPI.getInstance().getEquipmentTypeRegistry()}
 *         from its static block;</li>
 *     <li>{@code ModDataComponents} before blocks and items, whose properties carry components;</li>
 *     <li>{@code ModBlocksInitializer} before {@code TileEntityInitializer} (block entity types name their
 *         valid blocks), before {@code ModBuildingsInitializer} (every entry names its hut block) and before
 *         {@code ModCreativeTabs};</li>
 *     <li>{@code EntityInitializer} before {@code ModItemsInitializer}, which builds eighteen spawn eggs out
 *         of {@code ModEntities};</li>
 *     <li>{@code ModJobsInitializer} before {@code ModSoundEvents}, which enumerates {@code ModJobs.getJobs()}
 *         in its static block and would otherwise register no citizen sounds at all;</li>
 *     <li>{@code ModItemsInitializer} before {@code ModEquipmentTypes.initRegisterEquipmentTiers()} and before
 *         {@code ModCreativeTabs}, both of which name concrete items;</li>
 *     <li>everything else last, in the order the NeoForge lifecycle used to run it: FMLCommonSetup work, then
 *         FMLLoadComplete work.</li>
 * </ol>
 */
public class MineColonies implements ModInitializer
{
    /**
     * The config instance.
     */
    private static Configurations<ClientConfiguration, ServerConfiguration, CommonConfiguration> config;

    @Override
    public void onInitialize()
    {
        LanguageHandler.loadLangPath("assets/minecolonies/lang/%s.json");

        // The modId overload is the one that persists to disk; the three-argument one keeps values in memory only.
        config = new Configurations<>(Constants.MOD_ID, ClientConfiguration::new, ServerConfiguration::new, CommonConfiguration::new);

        // Contract C3: the shared com.ldtteam.common network layer bootstraps itself. BlockUI's own
        // ModInitializer calls ModNetworking.register(), which in turn calls ServerLifecycleHooks.init(); its
        // ClientModInitializer calls ModNetworking.registerClient(). Calling any of the three from here would
        // only double-hook the lifecycle events. The "depends" block in fabric.mod.json is what guarantees
        // BlockUI initialises first.

        // Must precede every registry initialiser below: this is what builds the mod registries.
        MinecoloniesAPIProxy.getInstance().setApiInstance(
          FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
            ? new ClientMinecoloniesAPIImpl()
            : new CommonMinecoloniesAPIImpl());

        // --- vanilla registries -------------------------------------------------------------------------
        ModDataComponents.init();
        ModBlocksInitializer.init();          // blocks + block items
        TileEntityInitializer.init();
        EntityInitializer.init();
        RaiderMobUtils.MOB_ATTACK_DAMAGE.get();   // class-load hook: registers the mod's mob attribute
        ModItemsInitializer.init();           // needs ModEntities (spawn eggs) and ModBlocks (gates)
        ModContainerInitializers.init();
        ModRecipeSerializerInitializer.init();

        // 26.3: brewing stopped being a hardcoded PotionBrewing registry and became an ordinary recipe type,
        // and vanilla does not send brewing recipes to the client at all. WindowBrewingstandCrafting has to
        // know what a bottle+reagent pair brews into in order to teach the recipe, so ask Fabric to
        // synchronize that serializer. Harmless on a dedicated server: it only marks the serializer as one
        // the client may ask for during configuration.
        RecipeSynchronization.synchronizeRecipeSerializer(BrewingRecipe.SERIALIZER);

        ModParticleTypesInitializer.init();
        ModIngredientTypeInitializer.init();
        AdvancementTriggers.init();
        ModLootConditions.init();
        DeferredLootTableEntry.init();
        // PORT-NOTE(agent F -> agent G): both of these still use DeferredRegister and have no init() hook yet.
        // They must keep being called from here once they are ported, or the structure type and the two command
        // argument types are never registered.
        MineColoniesStructures.init();
        ModArgumentTypes.init();

        // --- mod registries -----------------------------------------------------------------------------
        ModJobsInitializer.init();            // must precede ModSoundEvents
        ModSoundEvents.init();                // enumerates ModJobs.getJobs()
        ModGuardTypesInitializer.init();
        ModBuildingsInitializer.init();       // names hut blocks
        ModBuildingExtensionsInitializer.init();
        ModColonyEventTypeInitializer.init();
        ModColonyEventDescriptionTypeInitializer.init();
        ModCraftingTypesInitializer.init();
        ModRecipeTypesInitializer.init();
        ModInteractionsInitializer.init();
        ModResearchEffectInitializer.init();
        ModResearchRequirementInitializer.init();
        ModQuestInitializer.init();
        ModHappinessFactorTypeInitializer.init();
        // ModEquipmentTypes has no init() hook of its own; touching a field is what class-loads it and runs
        // the static block that fills the EQUIPMENT_TYPES registry.
        ModEquipmentTypes.none.get();

        // Named items and blocks have to exist by now.
        ModCreativeTabs.init();

        // --- what FMLCommonSetupEvent used to do --------------------------------------------------------
        StandardFactoryControllerInitializer.onPreInit();
        ModTags.init();
        InteractionValidatorInitializer.init();

        // --- callbacks ----------------------------------------------------------------------------------
        registerEntityAttributes();
        registerNetworking();

        // Optional aircraft integration. The whole of it hangs off Compatibility.aircraftCompat, whose
        // default is a complete no-aircraft implementation, so this line is the only thing that ever
        // loads a xyz.przemyk class -- and it is guarded, so with Simple Planes absent nothing in that
        // package is touched and its classes need not exist at all.
        if (FabricLoader.getInstance().isModLoaded("simpleplanes"))
        {
            com.minecolonies.core.compatibility.simpleplanes.SimplePlanesCompat.init();
            Compatibility.aircraftCompat = new com.minecolonies.core.compatibility.simpleplanes.SimplePlanesCompat();
        }

        EventHandler.register();
        FMLEventHandler.register();
        DataPackSyncEventHandler.ServerEvents.register();
        QuestObjectiveEventHandler.register();
        ColonyPermissionEventHandler.hookCallbacks();
        CavalryHorseDebug.register();

        // Optional integration, in the shape the rest of the compat layer uses: a loader check, and nothing at
        // all when the mod is absent. Restores turnoffexplosionsincolonies and Action.EXPLODE for the blasts
        // Simple Planes produces -- see SimplePlanesBlastGuard for what that does and does not cover.
        SimplePlanesBlastGuard.register();

        // The other half of the same optional integration, bound the same reflective way and just as absent when
        // the aircraft mod is: tells that mod's autopilot to route around colonies its pilot is hostile in. It
        // enforces nothing -- see SimplePlanesAirspaceGuard for what it does and does not claim to do.
        SimplePlanesAirspaceGuard.register();

        // was: NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent e) -> ModTags.tagsLoaded = true)
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            ModTags.tagsLoaded = true;

            // Port note: this used to run in FMLCommonSetupEvent, i.e. after every registry was frozen. Fabric
            // has no such phase -- during onInitialize other mods may still be adding items and 26.2 has not
            // bound item components yet, so `new ItemStack(item).getMaxDamage()` would read nothing. Tag load
            // is the first moment the item registry is complete and bound. The method only ever calls
            // registerItemTierIfAbsent, so running it again on every /reload is harmless.
            ModEquipmentTypes.initRegisterEquipmentTiers();

            // 26.3: composting is no longer a registry at all. Fabric dropped CompostableRegistry because
            // vanilla turned compostability into the DataComponents.COMPOSTABLE item component, which is
            // attached when the item is built (Item.Properties#compostable). That means it cannot be applied
            // from here any more -- see registerCompostables(), called from onInitialize.
        });

        registerCompostables();

        SurvivalBlueprintHandlers.registerHandler(new SurvivalHandler());
        SurvivalBlueprintHandlers.registerHandler(new SuppliesHandler());

        // --- what FMLLoadCompleteEvent used to do -------------------------------------------------------
        PlacementHandlerInitializer.initHandlers();
        RequestSystemInitializer.onPostInit();

        logIncompatibilities();
    }

    /**
     * Makes the mod's foods, ingredients, crops and composted dirt usable in a vanilla composter.
     * <p>
     * Port note (26.3). This was the {@code compostables} NeoForge data map, then Fabric's runtime
     * {@code CompostableRegistry}. 26.3 removed both ends of that: compostability is now the
     * {@link DataComponents#COMPOSTABLE} item component, set when the item is built, and Fabric dropped the
     * registry class with it. The only way left to attach it to an already-built item is Fabric's
     * {@code DefaultItemComponentEvents.MODIFY}, which is what this does.
     * <p>
     * The chance model changed too: vanilla no longer stores a probability but a {@code NumberProvider}
     * reference that yields how many layers an insert adds. There are five stock providers (30/50/65/85/100
     * percent), so the old per-item chance is snapped to the nearest of them.
     */
    private static void registerCompostables()
    {
        DefaultItemComponentEvents.MODIFY.register(context ->
        {
            try
            {
                DefaultDataMapsProvider.compostables().forEach((item, chance) ->
                    context.modify(item, builder -> builder.set(DataComponents.COMPOSTABLE, new Compostable(compostTier(chance)))));
            }
            catch (final Exception e)
            {
                // TODO(port-26.3): DEGRADED -- never let this cost the whole mod its startup. Worst case the
                // mod's items simply cannot go into a vanilla composter; the mod's own compost barrel runs off
                // minecolonies:composting recipes and the compostables* item tags and is unaffected.
                Log.getLogger().error("Failed to register MineColonies compostables", e);
            }
        });
    }

    /**
     * Snaps an old-style compost chance to the closest of vanilla's five stock compostable number providers.
     *
     * @param chance the 0..1 chance the pre-26.3 data map carried.
     * @return the number provider key to reference from the item component.
     */
    private static ResourceKey<NumberProvider> compostTier(final float chance)
    {
        if (chance >= 0.925f)
        {
            return NumberProviders.COMPOSTABLE_ALWAYS_ADD_ONE;   // 100%
        }
        if (chance >= 0.75f)
        {
            return NumberProviders.COMPOSTABLE_MEDIUM_HIGH;      // 85%
        }
        if (chance >= 0.575f)
        {
            return NumberProviders.COMPOSTABLE_MEDIUM;           // 65%
        }
        if (chance >= 0.40f)
        {
            return NumberProviders.COMPOSTABLE_LOW_MEDIUM;       // 50%
        }
        return NumberProviders.COMPOSTABLE_LOW;                  // 30%
    }

    /**
     * Get the config handler.
     *
     * @return the config handler.
     */
    public static Configurations<ClientConfiguration, ServerConfiguration, CommonConfiguration> getConfig()
    {
        return config;
    }

    /**
     * Default attributes of the mod's living entities.
     * <p>
     * Port note (contract C5): was {@code @SubscribeEvent createEntityAttribute(EntityAttributeCreationEvent)};
     * Fabric registers them eagerly through {@link FabricDefaultAttributeRegistry}.
     */
    private static void registerEntityAttributes()
    {
        FabricDefaultAttributeRegistry.register(ModEntities.CITIZEN, AbstractEntityCitizen.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.VISITOR, AbstractEntityCitizen.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.MERCENARY, EntityMercenary.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BARBARIAN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.ARCHERBARBARIAN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CHIEFBARBARIAN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PHARAO, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.MUMMY, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.ARCHERMUMMY, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.ARCHERPIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CHIEFPIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.AMAZON, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.AMAZONSPEARMAN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.AMAZONCHIEF, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.NORSEMEN_ARCHER, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.NORSEMEN_CHIEF, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.SHIELDMAIDEN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.DROWNED_PIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.DROWNED_ARCHERPIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.DROWNED_CHIEFPIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());

        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_BARBARIAN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_ARCHERBARBARIAN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_CHIEFBARBARIAN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_PIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_ARCHERPIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_CHIEFPIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_PHARAO, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_MUMMY, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_ARCHERMUMMY, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_AMAZON, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_AMAZONSPEARMAN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_AMAZONCHIEF, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_NORSEMEN_ARCHER, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_NORSEMEN_CHIEF, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_SHIELDMAIDEN, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_DROWNED_PIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_DROWNED_ARCHERPIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAMP_DROWNED_CHIEFPIRATE, AbstractEntityMinecoloniesRaider.getDefaultAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CAVALRY_HORSE, Horse.createBaseHorseAttributes());
    }

    /**
     * Registers every payload type.
     * <p>
     * Port note (contract C3): {@code RegisterPayloadHandlersEvent} and the {@code PayloadRegistrar} are gone;
     * {@code PlayMessageType#register()} takes no argument and hooks the receivers itself. Fabric has no
     * payload versioning either, so the {@code .versioned(modVersion)} handshake guard is gone with it -- a
     * payload from a mismatched mod version now fails while decoding instead of being rejected up front.
     */
    private static void registerNetworking()
    {
        //  ColonyView messages
        ColonyViewMessage.TYPE.register();
        ColonyViewCitizenViewMessage.TYPE.register();
        ColonyViewRemoveCitizenMessage.TYPE.register();
        ColonyViewBuildingViewMessage.TYPE.register();
        ColonyViewRemoveBuildingMessage.TYPE.register();
        ColonyViewBuildingExtensionsUpdateMessage.TYPE.register();
        PermissionsMessage.View.TYPE.register();
        ColonyViewWorkOrderMessage.TYPE.register();
        ColonyViewRemoveWorkOrderMessage.TYPE.register();
        ColonyViewResearchManagerViewMessage.TYPE.register();

        //  Permission Request messages
        PermissionsMessage.Permission.TYPE.register();
        PermissionsMessage.AddPlayer.TYPE.register();
        PermissionsMessage.RemovePlayer.TYPE.register();
        PermissionsMessage.ChangePlayerRank.TYPE.register();
        PermissionsMessage.AddPlayerOrFakePlayer.TYPE.register();
        PermissionsMessage.AddRank.TYPE.register();
        PermissionsMessage.RemoveRank.TYPE.register();
        PermissionsMessage.EditRankType.TYPE.register();

        //  Colony Request messages
        BuildRequestMessage.TYPE.register();
        OpenInventoryMessage.TYPE.register();
        TownHallRenameMessage.TYPE.register();
        MinerSetLevelMessage.TYPE.register();
        RecallCitizenMessage.TYPE.register();
        HireFireMessage.TYPE.register();
        WorkOrderChangeMessage.TYPE.register();
        AssignFieldMessage.TYPE.register();
        AssignmentModeMessage.TYPE.register();
        GuardSetMinePosMessage.TYPE.register();
        RecallCitizenHutMessage.TYPE.register();
        TransferItemsRequestMessage.TYPE.register();
        MarkBuildingDirtyMessage.TYPE.register();
        ChangeFreeToInteractBlockMessage.TYPE.register();
        CreateColonyMessage.TYPE.register();
        ColonyDeleteOwnMessage.TYPE.register();
        ColonyViewRemoveMessage.TYPE.register();
        GiveToolMessage.TYPE.register();
        GetColonyInfoMessage.TYPE.register();
        MarkStoryReadOnItem.TYPE.register();
        PickupBlockMessage.TYPE.register();
        ColonyAbandonOwnMessage.TYPE.register();

        AssignUnassignMessage.TYPE.register();
        OpenCraftingGUIMessage.TYPE.register();
        AddRemoveRecipeMessage.TYPE.register();
        ChangeRecipePriorityMessage.TYPE.register();
        ChangeDeliveryPriorityMessage.TYPE.register();
        ForcePickupMessage.TYPE.register();
        UpgradeWarehouseMessage.TYPE.register();
        TransferItemsToCitizenRequestMessage.TYPE.register();
        UpdateRequestStateMessage.TYPE.register();
        BuildingSetStyleMessage.TYPE.register();
        RecallSingleCitizenMessage.TYPE.register();
        AssignFilterableItemMessage.TYPE.register();
        TeamColonyColorChangeMessage.TYPE.register();
        ColonyFlagChangeMessage.TYPE.register();
        ColonyStructureStyleMessage.TYPE.register();
        PauseCitizenMessage.TYPE.register();
        RestartCitizenMessage.TYPE.register();
        SortBuildingMessage.TYPE.register();
        PostBoxRequestMessage.TYPE.register();
        HireMercenaryMessage.TYPE.register();
        HutRenameMessage.TYPE.register();
        BuildingHiringModeMessage.TYPE.register();
        DecorationBuildRequestMessage.TYPE.register();
        DirectPlaceMessage.TYPE.register();
        TeleportToColonyMessage.TYPE.register();
        EnchanterWorkerSetMessage.TYPE.register();
        InteractionResponse.TYPE.register();
        TryResearchMessage.TYPE.register();
        HireSpiesMessage.TYPE.register();
        AddMinimumStockToBuildingModuleMessage.TYPE.register();
        RemoveMinimumStockFromBuildingModuleMessage.TYPE.register();
        FarmFieldPlotResizeMessage.TYPE.register();
        FarmFieldUpdateSeedMessage.TYPE.register();
        AdjustSkillCitizenMessage.TYPE.register();
        BuilderSelectWorkOrderMessage.TYPE.register();
        TriggerSettingMessage.TYPE.register();
        AssignFilterableEntityMessage.TYPE.register();
        BuildPickUpMessage.TYPE.register();
        SwitchBuildingWithToolMessage.TYPE.register();
        ColonyTextureStyleMessage.TYPE.register();
        MinerRepairLevelMessage.TYPE.register();
        PlantationFieldBuildRequestMessage.TYPE.register();
        PlayerAssistantBuildRequestMessage.TYPE.register();
        ResetFilterableItemMessage.TYPE.register();
        CourierHiringModeMessage.TYPE.register();
        QuarryHiringModeMessage.TYPE.register();
        ToggleRecipeMessage.TYPE.register();
        ColonyNameStyleMessage.TYPE.register();
        InteractionClose.TYPE.register();
        AlterRestaurantMenuItemMessage.TYPE.register();
        TriggerConnectionEventMessage.TYPE.register();
        QueryCitizenAIHistoryMessage.TYPE.register();
        DebugEnablePathfindingMessage.TYPE.register();

        //Client side only
        BlockParticleEffectMessage.TYPE.register();
        CompostParticleMessage.TYPE.register();
        ItemParticleEffectMessage.TYPE.register();
        LocalizedParticleEffectMessage.TYPE.register();
        OpenSuggestionWindowMessage.TYPE.register();
        UpdateClientWithCompatibilityMessage.TYPE.register();
        CircleParticleEffectMessage.TYPE.register();
        StreamParticleEffectMessage.TYPE.register();
        SleepingParticleMessage.TYPE.register();
        VanillaParticleMessage.TYPE.register();
        StopMusicMessage.TYPE.register();
        PlayAudioMessage.TYPE.register();
        PlayMusicAtPosMessage.TYPE.register();
        ColonyVisitorViewDataMessage.TYPE.register();
        ColonyViewAnimalViewDataMessage.TYPE.register();
        SyncPathMessage.TYPE.register();
        SyncPathReachedMessage.TYPE.register();
        ReactivateBuildingMessage.TYPE.register();
        PlaySoundForCitizenMessage.TYPE.register();
        OpenDecoBuildWindowMessage.TYPE.register();
        OpenPlantationFieldBuildWindowMessage.TYPE.register();
        SaveStructureNBTMessage.TYPE.register();
        GlobalQuestSyncMessage.TYPE.register();
        GlobalDiseaseSyncMessage.TYPE.register();
        DebugEnableMessage.TYPE.register();
        DebugOutputMessage.TYPE.register();

        OpenBuildingUIMessage.TYPE.register();
        OpenCantFoundColonyWarningMessage.TYPE.register();
        OpenColonyFoundingCovenantMessage.TYPE.register();
        OpenDeleteAbandonColonyMessage.TYPE.register();
        OpenReactivateColonyMessage.TYPE.register();

        //JEI Messages
        TransferRecipeCraftingTeachingMessage.TYPE.register();

        //Advancement Messages
        OpenGuiWindowTriggerMessage.TYPE.register();
        ClickGuiButtonTriggerMessage.TYPE.register();

        // Colony-Independent items
        RemoveFromRallyingListMessage.TYPE.register();
        ToggleBannerRallyGuardsMessage.TYPE.register();

        // Research-related messages.
        GlobalResearchTreeMessage.TYPE.register();

        // Crafter Recipe-related messages
        CustomRecipeManagerMessage.TYPE.register();
        SwitchRecipeCraftingTeachingMessage.TYPE.register();

        ColonyListMessage.TYPE.register();

        // Resource scroll NBT share message
        ResourceScrollSaveWarehouseSnapshotMessage.TYPE.register();

        // Item Setting message
        ItemSettingMessage.TYPE.register();
    }

    /**
     * Report known incompatibilities to the log.
     */
    private void logIncompatibilities()
    {
        if (FabricLoader.getInstance().isModLoaded("minecolonies_tweaks"))
        {
            Log.getLogger().warn("|======================================================================================================================================|");
            Log.getLogger().warn("|                                                                                                                                      |");
            Log.getLogger().warn("| Minecolonies has detected an addon mod that alters Minecolonies core code recklessly: 'Tweaks/Compatibility addon for Minecolonies'. |");
            Log.getLogger().warn("|          Please report any bugs or issues you find directly to the authors of this addon, as the Official Minecolonies Team          |");
            Log.getLogger().warn("|               will not be able to provide you any support with potential issues that will arise when using this addon.               |");
            Log.getLogger().warn("|                                                                                                                                      |");
            Log.getLogger().warn("|======================================================================================================================================|");
        }
    }
}
