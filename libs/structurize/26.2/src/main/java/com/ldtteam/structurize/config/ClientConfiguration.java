package com.ldtteam.structurize.config;

import com.ldtteam.common.config.AbstractConfiguration;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.network.messages.SyncSettingsToServer;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.ldtteam.common.config.ConfigValue.BooleanValue;
import com.ldtteam.common.config.ConfigValue.Builder;
import com.ldtteam.common.config.ConfigValue;
import com.ldtteam.common.config.ConfigValue.DoubleValue;
import com.ldtteam.common.config.ConfigValue.IntValue;

import java.util.function.Consumer;

/**
 * Mod client configuration.
 * Loaded clientside, not synced.
 */
public class ClientConfiguration extends AbstractConfiguration
{
    // blueprint renderer

    public final BooleanValue renderPlaceholdersNice;
    public final BooleanValue sharePreviews;
    public final BooleanValue displayShared;
    public final IntValue rendererLightLevel;
    public final DoubleValue rendererTransparency;
    public final BooleanValue scanToolScrolling;

    /**
     * Builds client configuration.
     *
     * @param builder config builder
     */
    public ClientConfiguration(final Builder builder)
    {
        super(builder, Constants.MOD_ID);

        createCategory("blueprint");
        createCategory("renderer");
        // if you add anything to this category, also add it #collectPreviewRendererSettings()
        
        renderPlaceholdersNice = defineBoolean("render_placeholders_nice", false);
        sharePreviews = defineBoolean("share_previews", false);
        displayShared = defineBoolean("see_shared_previews", false);
        rendererLightLevel = defineInteger("light_level", 15, -1, 15);
        rendererTransparency = defineDouble("transparency", -1, -1, 1);

        // lazy on purpose: BlueprintHandler is a client class and must not be loaded on a dedicated server
        addWatcher(() -> BlueprintHandler.getInstance().clearCache(), renderPlaceholdersNice, rendererLightLevel);
        addWatcher(displayShared, (oldValue, isSharingEnabled) -> {
            // notify server
            new SyncSettingsToServer().sendToServer();
            if (!isSharingEnabled)
            {
                RenderingCache.removeSharedPreviews();
            }
        });
        addWatcher(sharePreviews, (oldVal, shouldSharePreviews) -> {
            if (shouldSharePreviews)
            {
                RenderingCache.getBlueprintsToRender().forEach(BlueprintPreviewData::syncChangesToServer);
            }
        });

        finishCategory();   // renderer
        finishCategory();   // blueprint

        createCategory("gameplay");
        scanToolScrolling = defineBoolean("scan_tool_scrolling", false);
        finishCategory();
    }

    /**
     * Things which should be in buildtool settings, order is mostly carried over to gui order
     */
    public void collectPreviewRendererSettings(final Consumer<ConfigValue<?>> sink)
    {
        sink.accept(sharePreviews);
        sink.accept(displayShared);
        sink.accept(renderPlaceholdersNice);
        sink.accept(rendererLightLevel);
        sink.accept(rendererTransparency);
    }
}
