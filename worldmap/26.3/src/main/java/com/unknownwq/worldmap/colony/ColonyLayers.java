package com.unknownwq.worldmap.colony;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.EnumSet;

/**
 * Which colony layers are switched on, for this session.
 *
 * <p>Lives outside the screen because the screen is thrown away every time the map closes, and a player who
 * has just turned four layers off does not want them back thirty seconds later. Lives outside
 * {@code worldmap.properties} for the opposite reason: the answer depends on what is on the map in front of
 * you right now, not on a preference, and the config file is read once at startup and never rewritten.</p>
 *
 * <p>Written by the client thread from the context menu and read by the render thread; an {@link EnumSet} is
 * neither, so both go through {@code synchronized} here. The contention is a handful of reads per frame on
 * an uncontended lock.</p>
 */
@Environment(EnvType.CLIENT)
public final class ColonyLayers
{
    private final EnumSet<ColonyLayer> on = EnumSet.noneOf(ColonyLayer.class);

    /**
     * Starts every layer at its default.
     */
    public ColonyLayers()
    {
        for (final ColonyLayer layer : ColonyLayer.values())
        {
            if (layer.defaultOn())
            {
                this.on.add(layer);
            }
        }
    }

    /**
     * @param layer the layer.
     * @return whether it is drawn.
     */
    public synchronized boolean isOn(final ColonyLayer layer)
    {
        return this.on.contains(layer);
    }

    /**
     * Flips one layer.
     *
     * @param layer the layer.
     */
    public synchronized void toggle(final ColonyLayer layer)
    {
        if (!this.on.remove(layer))
        {
            this.on.add(layer);
        }
    }
}
