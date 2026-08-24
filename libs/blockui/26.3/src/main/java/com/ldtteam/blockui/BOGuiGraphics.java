package com.ldtteam.blockui;

import com.ldtteam.blockui.util.cursor.Cursor;
import com.ldtteam.common.fakelevel.SingleBlockFakeLevel;
import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

public class BOGuiGraphics extends GuiGraphicsExtractor
{
    private SingleBlockFakeLevel fakeLevel = null;

    private int cursorMaxDepth = -1;
    private CursorType selectedCursor = Cursor.DEFAULT;

    /**
     * Own handle on the client. {@code GuiGraphicsExtractor#minecraft} is private in 26.2 and there is no
     * accessor for it, so we keep the very same instance the super class was built with.
     */
    private final Minecraft mc;

    public BOGuiGraphics(final Minecraft mc, final CountingMatrix3x2fStack ps, final GuiRenderState renderState, final int mx, final int my)
    {
        super(mc, ps, renderState, mx, my);
        this.mc = mc;
    }

    /**
     * NeoForge {@code IClientItemExtensions#getFont} has no Fabric/vanilla equivalent in 26.2, items can no longer
     * override the stack-count font, so this is always the vanilla font now.
     *
     * @param itemStack kept for source compatibility of the callers, unused
     */
    private Font getFont(@Nullable final ItemStack itemStack)
    {
        return mc.font;
    }

    public void renderItemDecorations(final ItemStack itemStack, final int x, final int y)
    {
        super.itemDecorations(getFont(itemStack), itemStack, x, y);
    }

    public void renderItemDecorations(final ItemStack itemStack, final int x, final int y, @Nullable final String altStackSize)
    {
        super.itemDecorations(getFont(itemStack), itemStack, x, y, altStackSize);
    }

    public int drawString(final String text, final int x, final int y, final int color)
    {
        return drawString(text, x, y, color, false);
    }

    public int drawString(final String text, final int x, final int y, final int color, final boolean shadow)
    {
        super.text(mc.font, text, x, y, color, shadow);
        return x + mc.font.width(text); // should return end pos
    }

    public void setCursor(final CursorType cursor)
    {
        final int size = ((CountingMatrix3x2fStack) pose()).size;
        if (size >= cursorMaxDepth)
        {
            cursorMaxDepth = size;
            selectedCursor = cursor;
        }
    }

    /**
     * @param debugXoffset debug string x offset
     */
    public CursorType applyCursor(final int debugXoffset)
    {
        if (Pane.debugging)
        {
            drawString(selectedCursor.toString(), debugXoffset, -mc.font.lineHeight, Color.getByName("white"));
        }

        // requestCursor(selectedCursor);
        // need to direct this to vanilla gui
        return selectedCursor;
    }

    public static double getAltSpeedFactor(final Minecraft mc)
    {
        return mc.hasAltDown() ? 5 : 1;
    }

    public ScreenRectangle calcTransformedPaneBounds(final Pane pane)
    {
        return new ScreenRectangle(0, 0, pane.getWidth(), pane.getHeight()).transformAxisAligned(pose());
    }

    public SingleBlockFakeLevel getFakeLevel()
    {
        if (fakeLevel == null)
        {
            fakeLevel = new SingleBlockFakeLevel(Minecraft.getInstance().level);
        }
        return fakeLevel;
    }

    /**
     * Replaces NeoForge's {@code GuiGraphics#submitPictureInPictureRenderState}. Vanilla 26.2 only
     * exposes {@link GuiRenderState#addPicturesInPictureState}, so this keeps picture-in-picture
     * submission behind the facade instead of making every caller reach into the render state.
     *
     * @param state the picture-in-picture state to submit
     */
    public void submitPictureInPictureRenderState(final PictureInPictureRenderState state)
    {
        guiRenderState.addPicturesInPictureState(state);
    }

    public static class CountingMatrix3x2fStack extends Matrix3x2fStack
    {
        private int size = 0;

        public CountingMatrix3x2fStack(final int stackSize)
        {
            super(stackSize);
        }

        @Override
        public Matrix3x2fStack clear()
        {
            size = 0;
            return super.clear();
        }

        @Override
        public Matrix3x2fStack popMatrix()
        {
            size--;
            return super.popMatrix();
        }

        @Override
        public Matrix3x2fStack pushMatrix()
        {
            size++;
            return super.pushMatrix();
        }
    }
}
