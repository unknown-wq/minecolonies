package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.colony.interactionhandling.ModInteractionResponseHandlers;
import com.minecolonies.api.colony.interactionhandling.registry.InteractionResponseHandlerEntry;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.interactionhandling.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

public final class ModInteractionsInitializer
{

    private ModInteractionsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModInteractionsInitializer but this is a Utility class.");
    }

    static
    {
        ModInteractionResponseHandlers.standard = register(ModInteractionResponseHandlers.STANDARD.getPath(), () -> new InteractionResponseHandlerEntry.Builder()
                                                    .setResponseHandlerProducer(StandardInteraction::new)
                                                    .setRegistryName(ModInteractionResponseHandlers.STANDARD)
                                                    .createEntry());

        ModInteractionResponseHandlers.simpleNotification = register(ModInteractionResponseHandlers.SIMPLE_NOTIFICATION.getPath(), () -> new InteractionResponseHandlerEntry.Builder()
                                                              .setResponseHandlerProducer(StandardInteraction::new)
                                                              .setRegistryName(ModInteractionResponseHandlers.SIMPLE_NOTIFICATION)
                                                              .createEntry());

        ModInteractionResponseHandlers.pos = register(ModInteractionResponseHandlers.POS.getPath(), () -> new InteractionResponseHandlerEntry.Builder()
                                               .setResponseHandlerProducer(PosBasedInteraction::new)
                                               .setRegistryName(ModInteractionResponseHandlers.POS)
                                               .createEntry());

        ModInteractionResponseHandlers.request = register(ModInteractionResponseHandlers.REQUEST.getPath(), () -> new InteractionResponseHandlerEntry.Builder()
                                                   .setResponseHandlerProducer(RequestBasedInteraction::new)
                                                   .setRegistryName(ModInteractionResponseHandlers.REQUEST)
                                                   .createEntry());

        ModInteractionResponseHandlers.recruitment = register(ModInteractionResponseHandlers.RECRUITMENT.getPath(), () -> new InteractionResponseHandlerEntry.Builder()
                                                       .setResponseHandlerProducer(RecruitmentInteraction::new)
                                                       .setRegistryName(ModInteractionResponseHandlers.RECRUITMENT)
                                                       .createEntry());

        ModInteractionResponseHandlers.quest = register(ModInteractionResponseHandlers.QUEST.getPath(), () -> new InteractionResponseHandlerEntry.Builder()
                                                      .setResponseHandlerProducer(QuestDialogueInteraction::new)
                                                      .setRegistryName(ModInteractionResponseHandlers.QUEST)
                                                      .createEntry());

        ModInteractionResponseHandlers.questAction = register(ModInteractionResponseHandlers.QUEST_ACTION.getPath(), () -> new InteractionResponseHandlerEntry.Builder()
                                                     .setResponseHandlerProducer(QuestDeliveryInteraction::new)
                                                     .setRegistryName(ModInteractionResponseHandlers.QUEST_ACTION)
                                                     .createEntry());
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends InteractionResponseHandlerEntry> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.INTERACTION_HANDLER_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1); calling this from
     * the mod entry point is what pins the moment it happens.
     */
    public static void init()
    {
    }
}
