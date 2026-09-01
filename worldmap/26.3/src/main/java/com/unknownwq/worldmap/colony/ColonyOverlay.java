package com.unknownwq.worldmap.colony;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The map's entire view of MineColonies: six methods, none of which mentions a MineColonies type.
 *
 * <p>{@link #NONE} is the implementation the mod runs when MineColonies is not installed, and it does
 * nothing at all -- the ticks are ignored and the snapshot is always {@link ColonySnapshot#EMPTY}, so every
 * loop in the renderer runs zero times and the map behaves exactly as it did before any of this existed. It
 * is also what is installed when the real implementation fails to start for any reason, which is why
 * {@link ColonyBridge} catches {@link Throwable} rather than a list of exceptions.</p>
 *
 * <p>The real implementation lives in {@code com.unknownwq.worldmap.colony.minecolonies} and is reached only
 * through {@link ColonyBridge}. See that class for why the indirection has to be a whole class and not just
 * an {@code if}.</p>
 */
@Environment(EnvType.CLIENT)
public interface ColonyOverlay
{
    /**
     * The do-nothing overlay: what runs when MineColonies is absent.
     */
    ColonyOverlay NONE = new ColonyOverlay()
    {
        @Override
        public String toString()
        {
            return "ColonyOverlay.NONE";
        }
    };

    /**
     * @return true when MineColonies is installed and the overlay is really tracking it. False for
     *     {@link #NONE}, and the flag the screen uses to decide whether to offer the layer toggles at all.
     */
    default boolean isActive()
    {
        return false;
    }

    /**
     * Called once per client tick, on the client thread.
     *
     * <p>This is where the live half of the integration happens: claims change while you play, so the
     * claim map is re-read and the borders rebuilt. It is bounded work per tick -- see the implementation
     * -- and it is deliberately the only thing about this map that updates by itself. Surface pixels are
     * still scanned once and never revisited.</p>
     *
     * @param minecraft the client.
     */
    default void tick(final Minecraft minecraft)
    {
    }

    /**
     * @return the current snapshot; never null. Safe to call from the render thread, and the object it
     *     returns never changes afterwards.
     */
    default ColonySnapshot snapshot()
    {
        return ColonySnapshot.EMPTY;
    }

    /**
     * Opens a hut's own window, as if the player had right-clicked the block.
     *
     * @param colonyId the colony the hut belongs to.
     * @param pos      the hut block.
     * @return true if a window was opened, so the caller knows whether to close the map.
     */
    default boolean openBuildingGui(final int colonyId, final BlockPos pos)
    {
        return false;
    }

    /**
     * Forgets one remembered colony: it is removed from the in-memory list and from the file under
     * {@code <gamedir>/worldmap/<world>/<dimension>/}.
     *
     * <p>This is the manual half of a deliberate asymmetry. A colony that was deleted while the player was
     * away can never be noticed as gone, because the live claim map only ever contains colonies somebody is
     * standing in, so nothing automatic can tell "deleted" from "out of range". Rather than guess, a
     * remembered colony is kept for ever, drawn as remembered rather than live, and removed only when the
     * player says so.</p>
     *
     * @param colonyId the colony to forget.
     * @return true if a remembered colony was removed. False if there was none with that id, or if it is
     *     live -- a live colony is not the map's to forget.
     */
    default boolean forgetColony(final int colonyId)
    {
        return false;
    }

    /**
     * Drops everything cached. Called when the player leaves a world.
     */
    default void clear()
    {
    }
}
