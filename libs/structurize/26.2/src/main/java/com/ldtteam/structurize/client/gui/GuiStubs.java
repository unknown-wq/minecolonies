package com.ldtteam.structurize.client.gui;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.structurize.api.Tuple;
import com.ldtteam.structurize.util.ScanToolData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Single seam between Structurize and its user interface (contract C9).
 *
 * <p>Every window of the mod is built on {@code com.ldtteam.blockui}. Phase 4 brought the windows back into the
 * build, but the nine call sites outside {@code client/gui} keep going through this facade instead of touching
 * the window classes directly: it is the one place that has to change if the user interface ever has to be cut
 * again, and it keeps {@code items/**}, {@code network/**}, {@code event/**} and {@code client/ModKeyMappings}
 * free of BlockUI imports.</p>
 *
 * <p>Everything here is client-only and must never be touched from common code.</p>
 */
public final class GuiStubs
{
    private GuiStubs()
    {
    }

    /**
     * Opens the extended build tool. Called from {@code items/ItemBuildTool}.
     *
     * @param pos          anchor position, or null when opened from thin air.
     * @param groundstyle  one of the {@code Constants.GROUNDSTYLE_*} values.
     * @param provider     registry access of the level the tool was used in.
     */
    public static void openBuildToolWindow(final @Nullable BlockPos pos,
        final int groundstyle,
        final HolderLookup.Provider provider)
    {
        new WindowExtendedBuildTool(pos, groundstyle, null, WindowExtendedBuildTool.BLOCK_BLUEPRINT_REQUIREMENT, provider).open();
    }

    /**
     * Opens the scan tool window. Called from {@code items/ItemScanTool}.
     *
     * @param data the scan tool data of the held stack.
     */
    public static void openScanToolWindow(final ScanToolData data)
    {
        new WindowScan(data).open();
    }

    /**
     * Opens the shape tool window. Called from {@code items/ItemShapeTool}.
     *
     * @param pos      anchor position, or null when opened from thin air.
     * @param provider registry access of the level the tool was used in.
     */
    public static void openShapeToolWindow(final @Nullable BlockPos pos, final HolderLookup.Provider provider)
    {
        new WindowShapeTool(pos, provider).open();
    }

    /**
     * Opens the tag tool window. Called from {@code items/ItemTagTool}.
     *
     * @param currentTag the tag currently selected in the tool.
     * @param anchorPos  the anchor block the tool is bound to.
     * @param level      the level the tool was used in.
     * @param stack      the tool stack itself.
     */
    public static void openTagToolWindow(final String currentTag,
        final BlockPos anchorPos,
        final Level level,
        final ItemStack stack)
    {
        new WindowTagTool(currentTag, anchorPos, level, stack).open();
    }

    /**
     * Stores the undo/redo history received from the server.
     * Called from {@code network/messages/OperationHistoryMessage}.
     *
     * @param operations operation name and id pairs, newest first.
     */
    public static void setLastOperations(final List<Tuple<String, Integer>> operations)
    {
        WindowUndoRedo.lastOperations = operations;
    }

    /**
     * @return the last operation history received from the server.
     */
    public static List<Tuple<String, Integer>> getLastOperations()
    {
        return WindowUndoRedo.lastOperations;
    }

    /**
     * @return true when the extended build tool window is the screen currently on top.
     *         Called from {@code event/ClientEventSubscriber}.
     */
    public static boolean isBuildToolScreenOpen()
    {
        return currentWindow() instanceof WindowExtendedBuildTool;
    }

    /**
     * Drops the build tool's client side caches on disconnect.
     * Called from {@code event/ClientEventSubscriber}.
     */
    public static void clearBuildToolStaticData()
    {
        WindowExtendedBuildTool.clearStaticData();
    }

    /**
     * @return true when a blueprint manipulation window is the screen currently on top; drives the
     *         keybinding conflict context. Called from {@code client/ModKeyMappings}.
     */
    public static boolean isBlueprintManipulationScreenOpen()
    {
        return currentWindow() instanceof AbstractBlueprintManipulationWindow;
    }

    /**
     * @return true when any BlockUI window is the screen currently on top.
     */
    public static boolean isAnyBlockUiScreenOpen()
    {
        return Minecraft.getInstance().gui.screen() instanceof BOScreen;
    }

    /**
     * 26.2: {@code Minecraft#screen} is no longer a public field, the current screen comes from
     * {@code Minecraft#gui}.
     *
     * @return the BlockUI window currently on screen, or null when the top screen is not a BlockUI one.
     */
    private static @Nullable Object currentWindow()
    {
        return Minecraft.getInstance().gui.screen() instanceof final BOScreen screen ? screen.getWindow() : null;
    }
}
