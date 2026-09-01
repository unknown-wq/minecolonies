package com.unknownwq.worldmap.colony;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The things the colony overlay can draw, each of which the player can switch off from the map's context
 * menu.
 *
 * <p>Not cosmetic. A colony of any size puts a building icon on nearly every chunk it owns and a label at
 * its centre, and with three neighbouring colonies in view the map underneath stops being readable at all.
 * The toggles are the cheapest possible fix and they are per-session -- nothing here is written to the
 * config file, because the answer changes with what you are looking at.</p>
 */
@Environment(EnvType.CLIENT)
public enum ColonyLayer
{
    /**
     * Claimed-chunk fill and outline for colonies the player is a member of.
     */
    BORDERS("borders", true),

    /**
     * Claimed-chunk fill and outline for everybody else's colonies, at a weaker fill.
     */
    OTHER_COLONIES("other_colonies", true),

    /**
     * Hut icons for the player's own colonies. Only own colonies have a building list on the client at all.
     */
    BUILDINGS("buildings", true),

    /**
     * The colony name at its centre.
     */
    LABELS("labels", true),

    /**
     * Graves, from the colony's grave manager.
     */
    DEATHS("deaths", true),

    /**
     * Colony waypoints.
     */
    WAYPOINTS("waypoints", false),

    /**
     * Raid state: the heavier outline and the label tag on a colony that is being raided right now, plus a
     * marker on each of the last recorded raider spawn points. On by default -- it is the one thing on this
     * map that is urgent.
     */
    RAIDS("raids", true),

    /**
     * Farmer and plantation fields.
     */
    FIELDS("fields", true),

    /**
     * Manual guard patrol routes. Off by default: a colony with a full guard rota has a route out of every
     * tower, they cross each other and they cross the huts, and unlike everything else here they are lines
     * across open ground rather than marks on one point.
     */
    PATROLS("patrols", false),

    /**
     * Colonies read back from disk that have not been seen live this session. On by default -- remembering
     * where you have been is what this map is for -- but a toggle, because a long-played world accumulates
     * them and there is no automatic forgetting.
     */
    REMEMBERED("remembered", true);

    private final String key;
    private final boolean defaultOn;

    ColonyLayer(final String key, final boolean defaultOn)
    {
        this.key = key;
        this.defaultOn = defaultOn;
    }

    /**
     * @return the suffix of this layer's translation key, {@code gui.worldmap.layer.<key>}.
     */
    public String translationKey()
    {
        return "gui.worldmap.layer." + this.key;
    }

    /**
     * @return whether the layer starts switched on. Waypoints and patrol routes do not: a colony that uses
     *     waypoints has a lot of them and they are of no use unless you went looking for them, and patrol
     *     routes are long lines over ground the rest of the map is trying to show you.
     */
    public boolean defaultOn()
    {
        return this.defaultOn;
    }
}
