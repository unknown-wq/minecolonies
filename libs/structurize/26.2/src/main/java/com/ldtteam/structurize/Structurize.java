package com.ldtteam.structurize;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blockentities.ModBlockEntities;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.blueprints.v1.DataFixerUtils;
import com.ldtteam.structurize.blueprints.v1.DataVersion;
import com.ldtteam.common.config.AbstractConfiguration;
import com.ldtteam.common.config.Configurations;
import com.ldtteam.common.language.LanguageHandler;
import com.ldtteam.structurize.component.ModDataComponents;
import com.ldtteam.structurize.config.ClientConfiguration;
import com.ldtteam.structurize.config.ServerConfiguration;
import com.ldtteam.structurize.event.EventSubscriber;
import com.ldtteam.structurize.event.LifecycleSubscriber;
import com.ldtteam.structurize.items.ModItemGroups;
import com.ldtteam.structurize.items.ModItems;
import com.ldtteam.structurize.storage.ServerFutureProcessor;
import com.ldtteam.structurize.storage.ServerStructurePackLoader;
import com.ldtteam.structurize.storage.rendering.ServerPreviewDistributor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.datafix.DataFixers;

/**
 * Mod main class — the {@code main} entrypoint declared in {@code fabric.mod.json} (contract C5).
 *
 * <p>Port note: NeoForge's {@code @Mod} constructor took the mod container and two event buses. Fabric has
 * neither: registration is eager and ordered by hand here, and everything that used to be an
 * {@code @SubscribeEvent} method is now a callback installed by the {@code register()} hook of its own
 * subscriber class. Everything client-only moved to {@link StructurizeClient}.</p>
 */
public class Structurize implements ModInitializer
{
    /**
     * The config instance.
     */
    private static Configurations<ClientConfiguration, ServerConfiguration, ?> config;

    @Override
    public void onInitialize()
    {
        LanguageHandler.loadLangPath("assets/structurize/lang/%s.json");
        config = new Configurations<ClientConfiguration, ServerConfiguration, AbstractConfiguration>(
            ClientConfiguration::new,
            ServerConfiguration::new,
            null);

        // The shared com.ldtteam.common networking bootstrap (ServerLifecycleHooks) is owned by BlockUI's own
        // common initializer, so there is nothing to wire up here any more.

        // Registration order matters: item properties reference the data components, the block items and the
        // block entity type reference the blocks, and the creative tab dereferences both.
        ModDataComponents.init();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModItemGroups.init();

        LifecycleSubscriber.register();
        EventSubscriber.register();

        ServerStructurePackLoader.register();
        ServerFutureProcessor.register();
        ServerPreviewDistributor.init();

        checkDataFixer();
    }

    /**
     * Sanity check that the vanilla data fixer is new enough for the blueprint format.
     */
    private static void checkDataFixer()
    {
        if (DataFixerUtils.isVanillaDF)
        {
            if ((DataFixers.getDataFixer().getSchema(Integer.MAX_VALUE - 1).getVersionKey()) >= DataVersion.UPCOMING.getDataVersion() * 10)
            {
                throw new RuntimeException(
                    "You are trying to run old mod on much newer vanilla. Missing some newest data versions. Please update com/ldtteam/structures/blueprints/v1/DataVersion");
            }
            else if (FabricLoader.getInstance().isDevelopmentEnvironment() && DataVersion.CURRENT == DataVersion.UPCOMING)
            {
                throw new RuntimeException(
                    "Missing some newest data versions. Please update src/main/java/com/ldtteam/structurize/blueprints/v1/DataVersion.java");
            }
        }
        else
        {
            Log.getLogger().error("----------------------------------------------------------------- \n "
                                    + "Invalid DataFixer detected, schematics might not paste correctly! \n"
                                    + "The following DataFixer was added: " + DataFixers.getDataFixer().getClass() + "\n"
                                    + "-----------------------------------------------------------------");
        }
    }

    /**
     * Get the config handler.
     *
     * @return the config handler.
     */
    public static Configurations<ClientConfiguration, ServerConfiguration, ?> getConfig()
    {
        return config;
    }
}
