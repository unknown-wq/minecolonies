package com.ldtteam.structurize.client;

import com.ldtteam.structurize.items.ItemStackTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ClientItemStackTooltip implements ClientTooltipComponent
{
    private final ItemStackTooltip component;

    public ClientItemStackTooltip(@NotNull final ItemStackTooltip component)
    {
        this.component = component;
    }

    @Override
    public int getHeight(@NotNull final Font font)
    {
        return 20;
    }

    @Override
    public int getWidth(@NotNull final Font font)
    {
        return 20 + font.width(this.component.getStack().getDisplayName().getVisualOrderText());
    }

    @Override
    public void extractText(@NotNull final GuiGraphicsExtractor graphics, @NotNull final Font font, final int x, final int y)
    {
        graphics.text(font, this.component.getStack().getHoverName(), x + 20, y + (20 - font.lineHeight) / 2, 0xffffffff, false);
    }

    @Override
    public void extractImage(final Font font, final int x, final int y, final int w, final int h, final GuiGraphicsExtractor graphics)
    {
        graphics.item(this.component.getStack(), x + 2, y + 2);
        graphics.itemDecorations(getFont(this.component.getStack()), this.component.getStack(), x + 2, y + 2);
    }

    /**
     * Item specific fonts used to be provided by NeoForge's IClientItemExtensions#getFont; 26.2 has no
     * such hook (neither vanilla nor fabric-api), so the default font is always used.
     *
     * @see com.ldtteam.blockui.BOGuiGraphics#getFont
     */
    // TODO(port-26.2): DEGRADED — IClientItemExtensions#getFont is NeoForge-only, no 26.2 equivalent; always default font
    private Font getFont(final ItemStack itemStack)
    {
        return Minecraft.getInstance().font;
    }
}
