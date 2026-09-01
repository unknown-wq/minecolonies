package com.unknownwq.worldmap.colony;

import com.unknownwq.worldmap.WorldMapClient;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Decides, once, whether the map gets a real colony overlay or {@link ColonyOverlay#NONE}.
 *
 * <h2>Why the overlay is named as a string and not as a class</h2>
 * <p>The obvious version of this is one line:</p>
 * <pre>return loaded ? new MineColoniesOverlay() : ColonyOverlay.NONE;</pre>
 * <p>and it fails twice over.</p>
 *
 * <p><b>At runtime.</b> Naming {@code MineColoniesOverlay} in a {@code return} whose declared type is
 * {@link ColonyOverlay} makes the bytecode verifier check that one is assignable to the other, and to answer
 * that it may load {@code MineColoniesOverlay} -- which loads its field and signature types, which are
 * {@code com.minecolonies} classes that are not installed. That happens when <em>this</em> class is linked,
 * before the {@code if} has run and before any {@code catch} could see it. The class is therefore named only
 * as a string, and {@link Class#forName} is what resolves it -- inside the branch, where it is known to be
 * safe, and where a failure is an ordinary {@link Throwable} that can be caught and reported.</p>
 *
 * <p><b>At compile time.</b> A compile-time reference would also mean the integration package could not be
 * dropped from the source set on a machine with no MineColonies jar to compile against -- the reference
 * would dangle and the whole mod would stop building. This is exactly the trap {@code ../../26.3/build.gradle}
 * documents for the Simple Planes integration, where seven such references make the jar a build requirement
 * for a feature that is optional at runtime. Here there are none, so {@code build.gradle} can and does drop
 * the package and produce a working jar without it.</p>
 *
 * <p>The mod id is checked with Fabric's own loader rather than by catching a failure, because that is the
 * question actually being asked, and because {@code fabric.mod.json} deliberately does <b>not</b> depend on
 * MineColonies -- there is a {@code recommends} entry and nothing more, so the loader starts this mod on its
 * own without complaint.</p>
 */
@Environment(EnvType.CLIENT)
public final class ColonyBridge
{
    /**
     * The mod id checked for. MineColonies' own {@code Constants.MOD_ID}, spelled out here because this
     * class must not name a MineColonies type even to read a constant off one.
     */
    public static final String MINECOLONIES = "minecolonies";

    /**
     * The implementation, by name. See the class notes for why this is a string.
     */
    private static final String OVERLAY_CLASS = "com.unknownwq.worldmap.colony.minecolonies.MineColoniesOverlay";

    /**
     * @return a live overlay when MineColonies is installed and the integration loads cleanly,
     *     {@link ColonyOverlay#NONE} otherwise. Never null, and never throws: an overlay that will not start
     *     costs the colony layers, and a map that will not open costs everything.
     */
    public static ColonyOverlay create()
    {
        if (!FabricLoader.getInstance().isModLoaded(MINECOLONIES))
        {
            WorldMapClient.LOGGER.info("MineColonies is not installed -- the map runs without the colony overlay.");
            return ColonyOverlay.NONE;
        }

        try
        {
            final Object overlay = Class.forName(OVERLAY_CLASS).getDeclaredConstructor().newInstance();
            WorldMapClient.LOGGER.info("MineColonies found -- colony overlay enabled.");
            return (ColonyOverlay) overlay;
        }
        catch (final ClassNotFoundException e)
        {
            // The mod was built without the integration package -- see build.gradle. Not an error: this is
            // that build working as intended, on an installation that happens to have MineColonies too.
            WorldMapClient.LOGGER.info("This build of the map carries no MineColonies integration.");
            return ColonyOverlay.NONE;
        }
        catch (final Throwable t)
        {
            WorldMapClient.LOGGER.warn("MineColonies is installed but the colony overlay could not start; "
                                         + "the map will run without it.", t);
            return ColonyOverlay.NONE;
        }
    }

    private ColonyBridge()
    {
        /*
         * Intentionally left empty.
         */
    }
}
