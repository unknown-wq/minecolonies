package com.minecolonies.core.client.assetfetch.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The progress bar on the install screen: a rail, a filled portion and a percentage.
 *
 * <p><b>It draws itself out of plain fills, on purpose.</b> This bar is on screen exactly when the fetched
 * MineColonies assets are <i>absent</i>, so it may not name a single {@code minecolonies:} texture — not even
 * a one-pixel one. Everything below is {@link GuiGraphicsExtractor#fill}, which is 26.2's replacement for the
 * old immediate-mode {@code GuiGraphics}: the widget contributes coloured quads to the frame's render state
 * and the GUI renderer draws them later. That is the same idiom vanilla's own
 * {@code LevelLoadingScreen.drawProgressBar} uses — two fills, no texture, no atlas.</p>
 *
 * <p>Two modes. With a known total the fill is the fraction and the percentage is written across it; with an
 * unknown one ({@link #setIndeterminate}) a short block slides back and forth instead, which is the honest
 * thing to show while, say, the extractor is walking a jar whose entry count it has not counted yet.</p>
 */
@Environment(EnvType.CLIENT)
public class AssetProgressBar extends AbstractWidget
{
    /**
     * How tall the bar is. Twelve pixels: the 1px border, a pixel of padding and enough room for the 9px
     * percentage to sit inside the rail rather than under it.
     */
    public static final int HEIGHT = 12;

    /**
     * The 1px frame around the bar.
     */
    private static final int COLOUR_BORDER = 0xFF_FFFFFF;

    /**
     * The unfilled rail behind the progress.
     */
    private static final int COLOUR_RAIL = 0xFF_555555;

    /**
     * The filled portion, and the sliding block in the indeterminate mode.
     */
    private static final int COLOUR_FILL = 0xFF_3BDB3B;

    /**
     * The percentage written across the bar.
     */
    private static final int COLOUR_TEXT = 0xFF_FFFFFF;

    /**
     * How long one sweep of the indeterminate block takes, in milliseconds.
     */
    private static final long SWEEP_MILLIS = 1600L;

    /**
     * The font the percentage is drawn in.
     */
    private final Font font;

    /**
     * How far along, 0..1, or negative for "no total known".
     */
    private float progress = -1.0F;

    /**
     * Creates a bar.
     *
     * @param width how wide the bar is.
     * @param font  the font for the percentage.
     */
    public AssetProgressBar(final int width, final Font font)
    {
        super(0, 0, width, HEIGHT, Component.empty());
        this.font = font;
        this.active = false;
    }

    /**
     * Sets the fraction shown.
     *
     * @param done  how much is done.
     * @param total how much there is, or a non-positive value when that is not known yet.
     */
    public void setProgress(final long done, final long total)
    {
        if (total <= 0L || done < 0L)
        {
            this.setIndeterminate();
            return;
        }
        this.progress = Mth.clamp((float) ((double) done / (double) total), 0.0F, 1.0F);
    }

    /**
     * Switches the bar to the sliding-block mode, for a phase whose total is not known.
     */
    public void setIndeterminate()
    {
        this.progress = -1.0F;
    }

    @Override
    protected void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a)
    {
        final int left = this.getX();
        final int top = this.getY();
        final int right = left + this.getWidth();
        final int bottom = top + this.getHeight();

        graphics.fill(left, top, right, bottom, COLOUR_BORDER);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, COLOUR_RAIL);

        final int innerLeft = left + 1;
        final int innerTop = top + 1;
        final int innerWidth = this.getWidth() - 2;
        final int innerBottom = bottom - 1;

        if (this.progress < 0.0F)
        {
            final int block = Math.max(8, innerWidth / 4);
            // 0 -> 1 -> 0 over one sweep, so the block runs to the end and comes back instead of jumping.
            final float phase = (float) (Util.getMillis() % SWEEP_MILLIS) / SWEEP_MILLIS;
            final float position = 1.0F - Mth.abs(phase * 2.0F - 1.0F);
            final int offset = Math.round((innerWidth - block) * position);
            graphics.fill(innerLeft + offset, innerTop, innerLeft + offset + block, innerBottom, COLOUR_FILL);
            return;
        }

        final int filled = Math.round(innerWidth * this.progress);
        if (filled > 0)
        {
            graphics.fill(innerLeft, innerTop, innerLeft + filled, innerBottom, COLOUR_FILL);
        }
        graphics.centeredText(this.font, this.percentText(), left + this.getWidth() / 2, top + (HEIGHT - 8) / 2, COLOUR_TEXT);
    }

    /**
     * The percentage written across the bar.
     *
     * @return e.g. {@code 47%}.
     */
    private String percentText()
    {
        return Math.round(this.progress * 100.0F) + "%";
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput output)
    {
        if (this.progress >= 0.0F)
        {
            // A vanilla key: it lives in the game's own language files, which are here whatever else is not.
            output.add(NarratedElementType.TITLE, Component.translatable("loading.progress", Math.round(this.progress * 100.0F)));
        }
    }
}
