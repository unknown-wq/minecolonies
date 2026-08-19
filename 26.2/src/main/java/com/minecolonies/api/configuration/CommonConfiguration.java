package com.minecolonies.api.configuration;

import com.ldtteam.common.config.AbstractConfiguration;
import com.minecolonies.api.util.constant.Constants;
import com.ldtteam.common.config.ConfigValue.BooleanValue;

import com.ldtteam.common.config.ConfigValue.Builder;

public class CommonConfiguration extends AbstractConfiguration
{
    public final BooleanValue generateSupplyLoot;
    public final BooleanValue rsEnableDebugLogging;

    /**
     * Builds client configuration.
     *
     * @param builder config builder
     */
    public CommonConfiguration(final Builder builder)
    {
        super(builder, Constants.MOD_ID);

        createCategory("gameplay");
        generateSupplyLoot = defineBoolean("generatesupplyloot", true);

        swapToCategory("requestsystem");

        rsEnableDebugLogging = defineBoolean("enabledebuglogging", false);
        finishCategory();
    }
}
