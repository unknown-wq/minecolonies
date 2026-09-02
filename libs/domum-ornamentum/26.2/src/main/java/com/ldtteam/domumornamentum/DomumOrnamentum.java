package com.ldtteam.domumornamentum;

import com.ldtteam.domumornamentum.api.DomumOrnamentumAPI;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.ModCreativeTabs;
import com.ldtteam.domumornamentum.component.ModDataComponents;
import com.ldtteam.domumornamentum.container.ModContainerTypes;
import com.ldtteam.domumornamentum.entity.block.ModBlockEntityTypes;
import com.ldtteam.domumornamentum.event.handlers.ModBusEventHandler;
import com.ldtteam.domumornamentum.recipe.ModRecipeSerializers;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.util.Constants;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common entrypoint (contract C2). Replaces the NeoForge {@code @Mod} constructor.
 *
 * <p>On Fabric every registry entry is created eagerly, so the whole job here is to touch the registry
 * holders <em>in dependency order</em>: blocks and items first, then everything that snapshots the block
 * registry ({@code ModBlockEntityTypes}) or dereferences blocks ({@code ModCreativeTabs}).</p>
 */
public class DomumOrnamentum implements ModInitializer
{
    public static final Logger LOGGER = LogManager.getLogger(Constants.MOD_ID);

    @Override
    public void onInitialize()
    {
        IDomumOrnamentumApi.Holder.setInstance(DomumOrnamentumAPI.getInstance());

        ModDataComponents.init();
        ModBlocks.init();
        ModBlockEntityTypes.init();
        ModContainerTypes.init();
        ModRecipeTypes.init();
        ModRecipeSerializers.init();
        ModCreativeTabs.init();

        ModBusEventHandler.registerCommon();
    }
}
