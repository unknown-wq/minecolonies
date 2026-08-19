package com.minecolonies.api.colony.interactionhandling;

import com.minecolonies.api.colony.interactionhandling.registry.InteractionResponseHandlerEntry;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;


/**
 * List of mod interaction handlers.
 */
public final class ModInteractionResponseHandlers
{
    /**
     * List of IDs.
     */
    public static final Identifier STANDARD            = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "standard");
    public static final Identifier SIMPLE_NOTIFICATION = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "simplenotification");
    public static final Identifier POS                 = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pos");
    public static final Identifier REQUEST             = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "request");
    public static final Identifier RECRUITMENT         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "recruitment");
    public static final Identifier QUEST               = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "quest");
    public static final Identifier QUEST_ACTION        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "questaction");

    /**
     * List of entries.
     */
    public static Supplier<InteractionResponseHandlerEntry> standard;
    public static Supplier<InteractionResponseHandlerEntry> simpleNotification;
    public static Supplier<InteractionResponseHandlerEntry> pos;
    public static Supplier<InteractionResponseHandlerEntry> request;
    public static Supplier<InteractionResponseHandlerEntry> recruitment;
    public static Supplier<InteractionResponseHandlerEntry> quest;
    public static Supplier<InteractionResponseHandlerEntry> questAction;

    private ModInteractionResponseHandlers()
    {
        throw new IllegalStateException("Tried to initialize: ModJobs but this is a Utility class.");
    }
}
