package com.minecolonies.core.event;

import com.google.common.collect.ImmutableMap;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.structurize.items.ModItems;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.interfaces.IBuildingBrowsableBlock;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.inventory.api.InvWrapper;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.ColonyConstants;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.client.gui.WindowBuildingBrowser;
import com.minecolonies.core.client.gui.containers.WindowCitizenInventory;
import com.minecolonies.core.client.render.worldevent.ColonyBorderRenderer;
import com.minecolonies.core.client.render.worldevent.WorldEventContext;
import com.minecolonies.core.colony.crafting.CustomRecipe;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import com.minecolonies.core.util.DomumOrnamentumUtils;
import com.minecolonies.core.util.SchemAnalyzerUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.Map.Entry;

import static com.minecolonies.api.research.util.ResearchConstants.SATURATION;
import static com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX;
import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.api.util.constant.translation.DebugTranslationConstants.*;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.RESTAURANT_MENU;

/**
 * Used to handle client events.
 */
@Environment(EnvType.CLIENT)
public class ClientEventHandler
{
    /**
     * Lazy cache for crafting module lookups.
     * <p>
     * Port note: NeoForge's {@code Lazy} is gone; {@link Suppliers#memoize} from Guava is the same thing.
     */
    private static final com.google.common.base.Supplier<Map<String, BuildingEntry>> crafterToBuilding =
      com.google.common.base.Suppliers.memoize(ClientEventHandler::buildCrafterToBuildingMap);

    /**
     * Installs every client callback. Called once from the client initializer (contract C5).
     */
    public static void register()
    {
        // was: @SubscribeEvent(LOWEST) renderWorldLastEvent(RenderLevelStageEvent). Structurize's ported
        // WorldRenderMacros owns the Fabric hook now and drives renderWithinContext from it.
        WorldEventContext.INSTANCE.registerLevelRenderCallbacks();

        // was: @SubscribeEvent(LOWEST) onwWorldTick(LevelTickEvent.Pre)
        ClientTickEvents.START_LEVEL_TICK.register(level -> {
            if (ColonyConstants.rand.nextInt(20) == 0)
            {
                WorldEventContext.INSTANCE.checkNearbyColony(level);
            }
        });

        // was: @SubscribeEvent onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onPlayerLogout());

        // was: @SubscribeEvent onItemTooltipEvent(ItemTooltipEvent)
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> onItemTooltip(stack, lines));

        // was: @SubscribeEvent(LOW) onUseItem(PlayerInteractEvent.RightClickItem)
        UseItemCallback.EVENT.register(ClientEventHandler::onUseItem);

        // TODO(port-26.2): DISABLED (degradation ladder step 1) -- PlaySoundEvent muted every
        // "minecolonies:citizen.*" sound when the citizenVoices client option was off. Fabric API has no
        // sound-playback veto event and vanilla 26.2 exposes none either, so that option now does nothing.

        // TODO(port-26.2): DISABLED (degradation ladder step 1) -- CustomizeGuiOverlayEvent.DebugText added the
        // colony name / distance lines to the F3 screen. 26.2 has no extension point on DebugScreenOverlay and
        // Fabric API offers no debug-text event; see #appendDebugText, kept but never called.
    }

    private static void onPlayerLogout()
    {
        ColonyBorderRenderer.cleanup();
        WindowBuildingBrowser.clearCache();
        IColonyManager.getInstance().resetColonyViews();
        Log.getLogger().info("Removed all colony views");
    }

    /**
     * Additional tooltips added to specific items
     */
    public static Map<Item, Component> extraItemTooltips = new HashMap<>();

    /**
     * Fires when an item tooltip is requested, generally from inventory, JEI, or when minecraft is first populating the recipe book.
     *
     * @param event An ItemTooltipEvent
     */
    private static void onItemTooltip(final ItemStack stack, final List<Component> toolTip)
    {
        // Vanilla recipe books populate tooltips once before the player exists on remote clients, some other cases.
        // Port note: ItemTooltipCallback does not hand over the player, so it comes from the client instance.
        final LocalPlayer tooltipPlayer = Minecraft.getInstance().player;
        if (tooltipPlayer == null)
        {
            return;
        }
        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getIColony(tooltipPlayer.level(), tooltipPlayer.blockPosition());

        if (extraItemTooltips.containsKey(stack.getItem()))
        {
            toolTip.add(extraItemTooltips.get(stack.getItem()));
        }

        // Port note: the vanilla "dyeable" item tag is gone in 26.2; the DYED_COLOR component answers
        // the same question.
        if (stack.has(DataComponents.DYED_COLOR) && IMinecoloniesAPI.getInstance().getConfig().getClient().showdyetooltips.get())
        {
            IMinecoloniesAPI.getInstance().getColonyManager().getCompatibilityManager().getDyeColor(stack).ifPresent(c ->
            {
                toolTip.removeIf(line -> line.getContents() instanceof TranslatableContents t && t.getKey().equals("item.dyed"));
                toolTip.add(1, Component.translatable("%s: %s",
                    Component.translatable("item.dyed"),
                    Component.translatable("color.minecraft." + c.getName()).withStyle(Style.EMPTY.withColor(c.getTextColor()).withItalic(false)))
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)));
            });
        }

        if (colony == null)
        {
            colony = IMinecoloniesAPI.getInstance().getColonyManager().getIColonyByOwner(tooltipPlayer.level(), tooltipPlayer);
        }

        if (colony == null)
        {
            return;
        }

        handleCrafterRecipeTooltips(colony, toolTip, stack.getItem());
        if (stack.getItem() instanceof BlockItem)
        {
            final BlockItem blockItem = (BlockItem) stack.getItem();
            if (blockItem.getBlock() instanceof AbstractBlockHut)
            {
                handleHutBlockResearchUnlocks(colony, toolTip, blockItem.getBlock());
            }

            if (tooltipPlayer.isCreative() && InventoryUtils.hasItemInItemHandler(new InvWrapper(tooltipPlayer.getInventory()), ModItems.scanTool.get()))
            {
                int tier = SchemAnalyzerUtil.getBlockTier(blockItem.getBlock());

                if (DomumOrnamentumUtils.isDoBlock(blockItem.getBlock()))
                {
                    for (Block block : MaterialTextureData.readFromItemStack(stack).getTexturedComponents().values())
                    {
                        tier = Math.max(tier, SchemAnalyzerUtil.getBlockTier(block));
                    }
                }

                toolTip.add(Component.translatableEscape("com.minecolonies.coremod.tooltip.schematic.tier", tier));
            }
        }

        if (WindowCitizenInventory.activeCitizenInventory != null && ItemStackUtils.ISFOOD.test(stack))
        {
            if (!FoodUtils.EDIBLE.test(stack))
            {
                toolTip.add(Component.translatable("com.minecolonies.coremod.item.tooltip.wrongfood").withStyle(ChatFormatting.RED));
                return;
            }

            final int foodTier = FoodUtils.getFoodTier(stack);

            final ICitizenDataView citizenData = (ICitizenDataView) WindowCitizenInventory.activeCitizenInventory.getCitizenData();
            final IColonyView colonyView = citizenData.getColony();

            IBuildingView cookBuilding = null;
            for (final IBuildingView buildingView : colonyView.getClientBuildingManager().getBuildings().values())
            {
                if (buildingView.getBuildingType() == ModBuildings.cook.get())
                {
                    if (cookBuilding == null || cookBuilding.getID().distSqr(citizenData.getPosition()) > buildingView.getID().distSqr(citizenData.getPosition()))
                    {
                        cookBuilding = buildingView;
                    }
                }
            }

            final int homeBuildingLevel =
                colonyView.getClientBuildingManager().getBuilding(citizenData.getHomeBuilding()) == null ? 0 : colonyView.getClientBuildingManager().getBuilding(citizenData.getHomeBuilding()).getBuildingLevel();
            if (FoodUtils.canEatLevel(stack, homeBuildingLevel))
            {
                toolTip.add(Component.translatable(TranslationConstants.TIER_TOOLTIP + foodTier).withStyle(ChatFormatting.GRAY));
                if (cookBuilding != null && !cookBuilding.getModuleView(RESTAURANT_MENU).getMenu().contains(new ItemStorage(stack)))
                {
                    toolTip.add(Component.translatable("com.minecolonies.coremod.item.tooltip.nomenu").withStyle(ChatFormatting.RED));
                }
            }
            else
            {
                toolTip.add(Component.translatable("com.minecolonies.coremod.item.tooltip.needbetterfood").withStyle(ChatFormatting.RED));
            }
        }
    }

    /**
     * Display crafter recipe-related information on the client.
     *
     * @param colony  The colony to check against, if one is present.
     * @param toolTip The tooltip to add the text onto.
     * @param item    The item that will have the tooltip text added.
     */
    private static void handleCrafterRecipeTooltips(@Nullable final IColony colony, final List<Component> toolTip, final Item item)
    {
        final List<CustomRecipe> recipes = CustomRecipeManager.getInstance().getRecipeByOutput(item);
        if (recipes.isEmpty())
        {
            return;
        }

        final Map<BuildingEntry, Integer> minimumBuildingLevels = new HashMap<>();

        for (CustomRecipe rec : recipes)
        {
            if (!rec.getShowTooltip() || rec.getCrafter().length() < 2)
            {
                continue;
            }
            final BuildingEntry craftingBuilding = crafterToBuilding.get().get(rec.getCrafter());
            if (craftingBuilding == null)
            {
                continue;
            }
            minimumBuildingLevels.putIfAbsent(craftingBuilding, null);
            if (minimumBuildingLevels.get(craftingBuilding) == null || rec.getMinBuildingLevel() < minimumBuildingLevels.get(craftingBuilding))
            {
                minimumBuildingLevels.put(craftingBuilding, rec.getMinBuildingLevel());
            }
            for (final Identifier id : rec.getRequiredResearchIds())
            {
                final Set<IGlobalResearch> researches;
                if (IMinecoloniesAPI.getInstance().getGlobalResearchTree().hasResearch(id))
                {
                    researches = new HashSet<>();
                    researches.add(IMinecoloniesAPI.getInstance().getGlobalResearchTree().getResearch(id));
                }
                else
                {
                    researches = IMinecoloniesAPI.getInstance().getGlobalResearchTree().getResearchForEffect(id);
                }
                if (researches != null)
                {
                    final ChatFormatting researchFormat;
                    if (colony != null && (colony.getResearchManager().getResearchTree().hasCompletedResearch(id) ||
                        colony.getResearchManager().getResearchEffects().getEffectStrength(id) > 0))
                    {
                        researchFormat = ChatFormatting.AQUA;
                    }
                    else
                    {
                        researchFormat = ChatFormatting.RED;
                    }

                    for (IGlobalResearch research : researches)
                    {
                        toolTip.add(Component.translatableEscape(COM_MINECOLONIES_COREMOD_ITEM_REQUIRES_RESEARCH_TOOLTIP_GUI,
                          MutableComponent.create(research.getName())).setStyle(Style.EMPTY.withColor(researchFormat)));
                    }
                }
            }
        }

        for (final Entry<BuildingEntry, Integer> crafterBuildingCombination : minimumBuildingLevels.entrySet())
        {
            final Component craftingBuildingName = getFullBuildingName(crafterBuildingCombination.getKey());
            final Integer minimumLevel = crafterBuildingCombination.getValue();
            if (minimumLevel > 0)
            {
                final Identifier schematicName = crafterBuildingCombination.getKey().getRegistryName();
                // the above is not guaranteed to match (and indeed doesn't for a few buildings), but
                // does match for all currently interesting crafters, at least.  there doesn't otherwise
                // appear to be an easy way to get the schematic name from a BuildingEntry ... or
                // unless we can change how colony.hasBuilding uses its parameter...

                final MutableComponent reqLevelText = Component.translatableEscape(COM_MINECOLONIES_COREMOD_ITEM_BUILDLEVEL_TOOLTIP_GUI, craftingBuildingName, minimumLevel);
                if (colony != null && colony.getCommonBuildingManager().hasBuilding(schematicName, minimumLevel, true))
                {
                    reqLevelText.setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA));
                }
                else
                {
                    reqLevelText.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
                }
                toolTip.add(reqLevelText);
            }
            else
            {
                final MutableComponent reqBuildingTxt = Component.translatableEscape(COM_MINECOLONIES_COREMOD_ITEM_AVAILABLE_TOOLTIP_GUI, craftingBuildingName)
                                                          .setStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY));
                toolTip.add(reqBuildingTxt);
            }
        }
    }

    /**
     * Gets a string like "ModName Building Name" for the specified building entry.
     *
     * @param building The building entry
     * @return The translated building name
     */
    private static Component getFullBuildingName(@NotNull final BuildingEntry building)
    {
        final String namespace = building.getBuildingBlock().getRegistryName().getNamespace();
        // Port note: ModList became FabricLoader#getModContainer, and the display name lives in the metadata.
        final String modName = FabricLoader.getInstance().getModContainer(namespace)
            .map(m -> m.getMetadata().getName())
            .orElse(namespace);
        final Component buildingName = building.getBuildingBlock().getName();
        return Component.literal(modName + " ").append(buildingName);
    }

    /**
     * Builds a mapping from crafting module ids to the corresponding buildings.
     *
     * @return The mapping
     */
    private static Map<String, BuildingEntry> buildCrafterToBuildingMap()
    {
        final ImmutableMap.Builder<String, BuildingEntry> builder = new ImmutableMap.Builder<>();
        for (final BuildingEntry building : IMinecoloniesAPI.getInstance().getBuildingRegistry())
        {
            for (final BuildingEntry.ModuleProducer moduleProducer : building.getModuleProducers())
            {
                final IBuildingModule module = BuildingEntry.produceModuleWithoutBuilding(moduleProducer.key);
                if (module instanceof ICraftingBuildingModule craftingBuildingModule && craftingBuildingModule.getCraftingJob() != null)
                {
                    builder.put(craftingBuildingModule.getCustomRecipeKey(), building);
                }
            }
        }
        return builder.build();
    }

    /**
     * Display research-related information on MineColonies Building hut blocks.
     * While this test can handle other non-hut blocks, research can only currently effect AbstractHutBlocks.
     *
     * @param colony  The colony to check against, if one is present.
     * @param tooltip The tooltip to add the text onto.
     * @param block   The hut block
     */
    private static void handleHutBlockResearchUnlocks(final IColony colony, final List<Component> tooltip, final Block block)
    {
        if (colony == null)
        {
            return;
        }
        final Identifier effectId = colony.getResearchManager().getResearchEffectIdFrom(block);
        if (colony.getResearchManager().getResearchEffects().getEffectStrength(effectId) > 0)
        {
            return;
        }
        if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().getResearchForEffect(effectId) != null)
        {
            tooltip.add(Component.translatableEscape(TranslationConstants.HUT_NEEDS_RESEARCH_TOOLTIP_1, block.getName()));
            tooltip.add(Component.translatableEscape(TranslationConstants.HUT_NEEDS_RESEARCH_TOOLTIP_2, block.getName()));
        }
    }

    /**
     * Event when the debug screen is opened. Event gets called by displayed text on the screen, we only need it when f3 is clicked.
     */
    /**
     * <b>NEVER CALLED (degradation ladder step 2).</b> Was {@code CustomizeGuiOverlayEvent.DebugText}.
     *
     * @param left the left-hand debug lines to append to.
     */
    public static void appendDebugText(final List<String> left)
    {
        final Minecraft mc = Minecraft.getInstance();

            final ClientLevel world = mc.level;
            final LocalPlayer player = mc.player;
            final BlockPos pos = player.blockPosition();
            IColony colony = IColonyManager.getInstance().getIColony(world, pos);
            if (colony == null)
            {
                if (IColonyManager.getInstance().isFarEnoughFromColonies(world, pos))
                {
                    left.add(Component.translatableEscape(DEBUG_NO_CLOSE_COLONY).getString());
                    return;
                }
                colony = IColonyManager.getInstance().getClosestIColony(world, pos);

                if (colony == null)
                {
                    return;
                }

                left
                  .add(Component.translatableEscape(DEBUG_NEXT_COLONY,
                    (int) Math.sqrt(colony.getDistanceSquared(pos)),
                    IColonyManager.getInstance().getMinimumDistanceBetweenTownHalls()).getString());
                return;
            }

            left.add(colony.getName() + " : " + Component.translatableEscape(DEBUG_BLOCKS_FROM_CENTER, (int) Math.sqrt(colony.getDistanceSquared(pos))).getString());
    }

    /**
     * Opens the building browser when the player right-clicks a browsable hut item in mid air.
     * <p>
     * Port note: was {@code PlayerInteractEvent.RightClickItem}; the Fabric counterpart is
     * {@link UseItemCallback}, which reports the same "used an item, not a block" interaction and expresses
     * cancellation as a non-PASS {@link InteractionResult}. {@code IBuildingBrowsableBlock#shouldBrowseBuildings}
     * takes {@code (Player, ItemStack)} now, because there is no event object to hand it.
     *
     * @param player the player.
     * @param level  the level.
     * @param hand   the hand used.
     * @return SUCCESS when the browser was opened, PASS otherwise.
     */
    private static InteractionResult onUseItem(@NotNull final Player player, final Level level, final InteractionHand hand)
    {
        if (!level.isClientSide())
        {
            return InteractionResult.PASS;
        }

        final ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof BlockItem blockItem)
        {
            // this still triggers on right-clicking a block, so we need to filter that out
            if (Minecraft.getInstance().hitResult != null && Minecraft.getInstance().hitResult.getType() != HitResult.Type.MISS)
            {
                return InteractionResult.PASS;
            }

            final Block block = blockItem.getBlock();

            if (block instanceof IBuildingBrowsableBlock browsable && browsable.shouldBrowseBuildings(player, stack))
            {
                MinecoloniesAPIProxy.getInstance().getBuildingDataManager().openBuildingBrowser(block);

                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}
