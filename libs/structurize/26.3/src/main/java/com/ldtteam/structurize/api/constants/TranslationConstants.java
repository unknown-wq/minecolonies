package com.ldtteam.structurize.api.constants;

import org.jetbrains.annotations.NonNls;

/**
 * Constants for translation
 */
public final class TranslationConstants
{
    @NonNls
    public static final String MAX_SCHEMATIC_SIZE_REACHED = "item.sceptersteel.toobig";

    @NonNls
    public static final String ANCHOR_POS_OUTSIDE_SCHEMATIC = "item.sceptersteel.badanchorpos";

    @NonNls
    public static final String GUI_SWITCH_PACK_AUTHORS = "com.ldtteam.structurize.gui.switchpack.authors";
    @NonNls
    public static final String GUI_SWITCH_PACK_DISABLED_TEXT = "com.ldtteam.structurize.gui.switchpack.pack_disabled.hover_text";

    /**
     * Shown to a player whose blueprint predates {@link com.ldtteam.structurize.blueprints.v1.BlueprintUtil#MIN_SUPPORTED_DATA_VERSION}.
     * Takes the blueprint path as its single argument.
     */
    @NonNls
    public static final String BLUEPRINT_TOO_OLD = "structurize.blueprint.too_old";

    private TranslationConstants()
    {
        //empty default
    }
}
