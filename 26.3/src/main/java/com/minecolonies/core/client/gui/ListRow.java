package com.minecolonies.core.client.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.views.ScrollingList;
import org.jetbrains.annotations.NotNull;

/**
 * Which row of a scrolling list a clicked pane belongs to.
 * <p>
 * {@link ScrollingList#getListElementIndexByPane} answers -1 for a pane that belongs to no row of the list, and it
 * answers a row whether or not the window's data still reaches that far: the row panes are built once and refreshed
 * on a later tick, so a click landing between a colony update and the refresh names a row that is already gone.
 * Reading the backing list at either answer throws inside a click handler, and nothing between the handler and the
 * game loop catches it.
 * <p>
 * Every handler that reads its list's data therefore goes through this and treats {@link #NONE} as a click on a row
 * that is no longer there, which is a click to ignore.
 */
public final class ListRow
{
    /**
     * No usable row: the pane belongs to no row of the list, or to one past the end of the data.
     */
    public static final int NONE = -1;

    /**
     * Utility class, no instances.
     */
    private ListRow()
    {
    }

    /**
     * The row a clicked pane belongs to, if the data still holds it.
     *
     * @param list the list the pane was clicked in.
     * @param pane the clicked pane.
     * @param size how many entries the backing data holds now.
     * @return the row index, or {@link #NONE}.
     */
    public static int of(@NotNull final ScrollingList list, @NotNull final Pane pane, final int size)
    {
        final int row = list.getListElementIndexByPane(pane);
        return row >= 0 && row < size ? row : NONE;
    }
}
