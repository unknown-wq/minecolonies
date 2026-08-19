package com.minecolonies.core.commands;

import net.minecraft.network.chat.ClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Small utility class to run an executable on a chat click event.
 */
// 26.2: ClickEvent became a sealed-ish interface of records and can no longer be subclassed; the mod's own
// implementation now implements the interface and reports RUN_COMMAND, exactly as the old super(...) call did.
public class ClickEventWithExecutable implements ClickEvent
{
    /**
     * The actions to run
     */
    private Runnable[] actions;

    /**
     * Default constructor.
     *
     * @param actions the actions this event should execute.
     */
    public ClickEventWithExecutable(@NotNull final Runnable... actions)
    {
        this.actions = actions;
    }

    /**
     * Triggered when the chat component is clicked.
     */
    @Override
    @NotNull
    public ClickEvent.Action action()
    {
        if (actions != null)
        {
            for (Runnable r : actions)
            {
                r.run();
            }
            actions = null;
        }
        return ClickEvent.Action.RUN_COMMAND;
    }
}
