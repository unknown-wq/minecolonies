package com.unknownwq.worldmap.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The little panel that opens under the cursor on a right-click.
 *
 * <p>Deliberately not a {@code Screen} and not built out of vanilla {@code Button} widgets. A screen would
 * have to be pushed over the map, and the map would then stop receiving the drag and scroll events that are
 * most of what it is for; vanilla buttons cannot be moved to the cursor and re-laid out per click without
 * rebuilding the widget list every time, and they bring a texture and a sound this does not want. What is
 * actually needed here is a list of strings, a hit test and two fills, so that is what this is.</p>
 *
 * <p>An entry can be greyed out, and that is used rather than hidden: an entry that vanishes teaches the
 * player nothing, whereas a greyed "Teleport here" with a reason under it says what is wrong. Entries can
 * also carry a tick, for the layer toggles.</p>
 */
@Environment(EnvType.CLIENT)
public final class ContextMenu
{
    private static final int PADDING = 5;
    private static final int ROW_HEIGHT = 12;
    private static final int SEPARATOR_HEIGHT = 5;
    private static final int MIN_WIDTH = 90;

    private static final int BACKGROUND = 0xF01A1A1A;
    private static final int BORDER = 0xFF5A5A5A;
    private static final int HOVER = 0x40FFFFFF;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int TEXT_DISABLED = 0xFF6E6E6E;
    private static final int TEXT_NOTE = 0xFF9AA0A8;
    private static final int SEPARATOR = 0xFF3C3C3C;

    private final List<Entry> entries = new ArrayList<>();

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean open;

    /**
     * Opens the menu, laying it out so it stays inside the screen.
     *
     * @param anchorX      where the click was.
     * @param anchorY      where the click was.
     * @param screenWidth  the screen width.
     * @param screenHeight the screen height.
     * @param font         the font, for measuring.
     * @param items        the entries, top to bottom.
     */
    public void open(
      final int anchorX,
      final int anchorY,
      final int screenWidth,
      final int screenHeight,
      final Font font,
      final List<Entry> items)
    {
        this.entries.clear();
        this.entries.addAll(items);

        int widest = MIN_WIDTH;
        int total = PADDING * 2;
        for (final Entry entry : this.entries)
        {
            if (entry.separator())
            {
                total += SEPARATOR_HEIGHT;
                continue;
            }
            total += ROW_HEIGHT;
            widest = Math.max(widest, font.width(entry.label()) + (entry.tick() == null ? 0 : 12));
            if (entry.note() != null)
            {
                total += ROW_HEIGHT - 2;
                widest = Math.max(widest, font.width(entry.note()));
            }
        }

        this.width = widest + PADDING * 2;
        this.height = total;

        // Flip rather than clamp when the menu will not fit below or right of the cursor: a clamped menu
        // sits under the pointer and the first entry gets clicked by the release of the click that opened it.
        this.x = anchorX + this.width <= screenWidth ? anchorX : Math.max(0, anchorX - this.width);
        this.y = anchorY + this.height <= screenHeight ? anchorY : Math.max(0, anchorY - this.height);
        this.open = true;
    }

    /**
     * Shuts the menu.
     */
    public void close()
    {
        this.open = false;
        this.entries.clear();
    }

    /**
     * @return whether the menu is showing.
     */
    public boolean isOpen()
    {
        return this.open;
    }

    /**
     * @param mouseX pointer x.
     * @param mouseY pointer y.
     * @return whether the pointer is over the menu, which is how the map knows not to pan.
     */
    public boolean contains(final double mouseX, final double mouseY)
    {
        return this.open
                 && mouseX >= this.x && mouseX < this.x + this.width
                 && mouseY >= this.y && mouseY < this.y + this.height;
    }

    /**
     * Handles a click.
     *
     * @param mouseX pointer x.
     * @param mouseY pointer y.
     * @return true if the click was the menu's, whether or not it hit an entry. A click outside closes the
     *     menu and is <b>not</b> consumed, so the same click can also be a click on the map.
     */
    public boolean click(final double mouseX, final double mouseY)
    {
        if (!this.open)
        {
            return false;
        }
        if (!this.contains(mouseX, mouseY))
        {
            this.close();
            return false;
        }

        final Entry hit = this.entryAt(mouseY);
        this.close();
        if (hit != null && hit.enabled() && hit.action() != null)
        {
            hit.action().run();
        }
        return true;
    }

    /**
     * Draws the menu. Call last, so it lands over everything else.
     *
     * @param graphics the extractor.
     * @param font     the font.
     * @param mouseX   pointer x, for the hover highlight.
     * @param mouseY   pointer y.
     */
    public void render(final GuiGraphicsExtractor graphics, final Font font, final int mouseX, final int mouseY)
    {
        if (!this.open)
        {
            return;
        }

        graphics.fill(this.x - 1, this.y - 1, this.x + this.width + 1, this.y + this.height + 1, BORDER);
        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, BACKGROUND);

        int row = this.y + PADDING;
        for (final Entry entry : this.entries)
        {
            if (entry.separator())
            {
                graphics.fill(this.x + PADDING, row + SEPARATOR_HEIGHT / 2, this.x + this.width - PADDING, row + SEPARATOR_HEIGHT / 2 + 1, SEPARATOR);
                row += SEPARATOR_HEIGHT;
                continue;
            }

            final boolean hovered = entry.enabled()
                                      && mouseX >= this.x && mouseX < this.x + this.width
                                      && mouseY >= row && mouseY < row + ROW_HEIGHT;
            if (hovered)
            {
                graphics.fill(this.x + 1, row, this.x + this.width - 1, row + ROW_HEIGHT, HOVER);
            }

            int textX = this.x + PADDING;
            if (entry.tick() != null)
            {
                // A drawn box rather than a glyph. A tick character is not in the ASCII font sheet, and
                // whether it resolves out of the unicode provider depends on the player's font settings --
                // two fills always work and never come out as a missing-glyph box.
                final int boxTop = row + 2;
                final int colour = entry.enabled() ? TEXT : TEXT_DISABLED;
                graphics.fill(textX, boxTop, textX + 8, boxTop + 8, colour);
                graphics.fill(textX + 1, boxTop + 1, textX + 7, boxTop + 7, BACKGROUND);
                if (entry.tick())
                {
                    graphics.fill(textX + 2, boxTop + 2, textX + 6, boxTop + 6, colour);
                }
                textX += 12;
            }
            graphics.text(font, entry.label(), textX, row + 2, entry.enabled() ? TEXT : TEXT_DISABLED, false);
            row += ROW_HEIGHT;

            if (entry.note() != null)
            {
                graphics.text(font, entry.note(), this.x + PADDING, row, TEXT_NOTE, false);
                row += ROW_HEIGHT - 2;
            }
        }
    }

    private Entry entryAt(final double mouseY)
    {
        int row = this.y + PADDING;
        for (final Entry entry : this.entries)
        {
            if (entry.separator())
            {
                row += SEPARATOR_HEIGHT;
                continue;
            }
            if (mouseY >= row && mouseY < row + ROW_HEIGHT)
            {
                return entry;
            }
            row += ROW_HEIGHT;
            if (entry.note() != null)
            {
                if (mouseY >= row && mouseY < row + ROW_HEIGHT - 2)
                {
                    return entry;
                }
                row += ROW_HEIGHT - 2;
            }
        }
        return null;
    }

    /**
     * One line of the menu.
     *
     * @param label     what it says; ignored for a separator.
     * @param note      a dimmer second line under it, or null. Used to say <em>why</em> a greyed entry is
     *                  greyed.
     * @param enabled   false to grey it out and ignore clicks on it.
     * @param tick      {@link Boolean#TRUE} or {@link Boolean#FALSE} to show a checkbox, null for none.
     * @param separator true for a horizontal rule with no text and no behaviour.
     * @param action    what a click does.
     */
    public record Entry(String label, String note, boolean enabled, Boolean tick, boolean separator, Runnable action)
    {
        /**
         * @param label  the text.
         * @param action what a click does.
         * @return an ordinary enabled entry.
         */
        public static Entry of(final String label, final Runnable action)
        {
            return new Entry(label, null, true, null, false, action);
        }

        /**
         * @param label  the text.
         * @param note   a second line under it, saying what the entry will do that the label does not.
         *               Unlike {@link #disabled} this one is not an excuse: the entry works.
         * @param action what a click does.
         * @return an enabled entry with a note under it.
         */
        public static Entry of(final String label, final String note, final Runnable action)
        {
            return new Entry(label, note, true, null, false, action);
        }

        /**
         * @param label  the text.
         * @param note   why it is greyed.
         * @return a greyed entry that does nothing.
         */
        public static Entry disabled(final String label, final String note)
        {
            return new Entry(label, note, false, null, false, null);
        }

        /**
         * @param label   the text.
         * @param checked whether the tick is shown.
         * @param action  what a click does.
         * @return a checkbox entry.
         */
        public static Entry toggle(final String label, final boolean checked, final Runnable action)
        {
            return new Entry(label, null, true, checked, false, action);
        }

        /**
         * @return a horizontal rule. Named {@code rule} and not {@code separator} because a record may not
         *     have a static method with the same name and signature as one of its accessors.
         */
        public static Entry rule()
        {
            return new Entry("", null, false, null, true, null);
        }
    }
}
