package com.minecolonies.core;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.structurize.api.TagManager;
import com.minecolonies.api.util.constant.SchematicTagConstants;
import com.minecolonies.apiimp.initializer.ModContainerInitializers;
import com.minecolonies.apiimp.initializer.ModParticleTypesInitializer;
import com.minecolonies.core.blocks.BlockDecorationController;
import com.minecolonies.core.blocks.BlockPlantationField;
import com.minecolonies.core.blocks.huts.BlockHutGateHouse;
import com.minecolonies.core.blocks.huts.BlockHutMiner;
import com.minecolonies.core.blocks.huts.BlockHutSchool;
import com.minecolonies.core.event.ClientEventHandler;
import com.minecolonies.core.event.ClientRegistryHandler;
import com.minecolonies.core.event.ColonyStoryListener;
import com.minecolonies.core.event.DataPackSyncEventHandler;
import com.minecolonies.core.event.EventHandler;
import com.minecolonies.core.event.FMLEventHandler;
import com.minecolonies.core.event.TextureReloadListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.AtlasRegistry;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.model.sprite.AtlasManager.AtlasConfig;
import net.minecraft.resources.Identifier;

import java.util.Set;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;
import static com.minecolonies.api.util.constant.SchematicTagConstants.*;

/**
 * Client entry point (contract C2).
 *
 * <p>Everything that used to sit behind {@code Dist.CLIENT} / {@code @OnlyIn(Dist.CLIENT)} is initialised from
 * here — renderers, screens, key bindings, reload listeners, the GUI atlas. Fabric runs every mod's common
 * initializer before any client one, so by the time this runs {@link MineColonies#onInitialize()} has already
 * built every registry.</p>
 */
@Environment(EnvType.CLIENT)
public class MineColoniesClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        // The mod's GUI atlas. Both halves are mandatory: without the NAMESPACE_TO_ATLAS_MAP entry
        // com.ldtteam.blockui.controls.Image throws IllegalArgumentException the first time it draws a
        // "minecolonies:" texture, and AtlasRegistry only accepts registrations from here — after the atlas
        // configs are finalized it throws IllegalStateException. (BlockUI's own AtlasManager helper is gone;
        // vanilla took the feature over in 26.2, and its AtlasManager is the reload listener.)
        final Identifier atlasKey = Identifier.fromNamespaceAndPath(MOD_ID, MOD_ID + "_gui");
        BlockUI.NAMESPACE_TO_ATLAS_MAP.put(MOD_ID, atlasKey);
        AtlasRegistry.register(new AtlasConfig(
          AtlasRegistry.generateTextureLocation(atlasKey), atlasKey, false, Set.of(GuiMetadataSection.TYPE)));

        ClientRegistryHandler.register();
        ModContainerInitializers.Client.registerScreens();
        ModParticleTypesInitializer.ClientRegistration.init();

        ClientEventHandler.register();
        FMLEventHandler.Client.register();
        DataPackSyncEventHandler.ClientEvents.register();
        EventHandler.registerClientHolidayFeatures();

        TextureReloadListener.register();
        ColonyStoryListener.register();

        registerSchematicTagOptions();
    }

    /**
     * Structurize's scan-tool tag palette. Client-only, exactly as it was inside the old
     * {@code if (dist.isClient())} branch of the mod constructor.
     */
    private static void registerSchematicTagOptions()
    {
        TagManager.registerGlobalTagOption(TAG_WORK);
        TagManager.registerGlobalTagOption(TAG_SIT_IN);
        TagManager.registerGlobalTagOption(TAG_SIT_OUT);
        TagManager.registerGlobalTagOption(TAG_STAND_IN);
        TagManager.registerGlobalTagOption(TAG_STAND_OUT);
        TagManager.registerGlobalTagOption(TAG_SITTING);
        TagManager.registerGlobalTagOption(BUILDING_SIGN);

        TagManager.registerSpecificTagOption(TAG_GATE, b -> b instanceof BlockHutGateHouse);
        TagManager.registerSpecificTagOption(TAG_KNIGHT, b -> b instanceof BlockHutGateHouse);
        TagManager.registerSpecificTagOption(TAG_ARCHER, b -> b instanceof BlockHutGateHouse);

        TagManager.registerSpecificTagOption(TAG_COBBLE, b -> b instanceof BlockHutMiner);
        TagManager.registerSpecificTagOption(TAG_LADDER, b -> b instanceof BlockHutMiner);

        TagManager.registerSpecificTagOption(TAG_LEISURE, b -> b instanceof BlockDecorationController);

        TagManager.registerSpecificTagOption(TAG_SITTING, b -> b instanceof BlockHutSchool);

        for (final String fieldTag : SchematicTagConstants.getPlantationTags())
        {
            TagManager.registerSpecificTagOption(fieldTag, b -> b instanceof BlockPlantationField);
        }
    }
}
