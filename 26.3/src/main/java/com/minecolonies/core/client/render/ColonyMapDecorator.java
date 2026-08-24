package com.minecolonies.core.client.render;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;
import com.minecolonies.api.items.ModItems;

/**
 * PORT-26.2: NeoForge's per-item {@code IItemDecorator} registry is gone; Fabric's
 * {@code ExtractItemDecorationsCallback} is a single global callback, so the decorator filters on the item itself and
 * the (removed) return value is replaced by simply not drawing.
 */
@Environment(EnvType.CLIENT)
public class ColonyMapDecorator implements ExtractItemDecorationsCallback
{
    private static IColonyView colonyView;
    private static boolean     render = false;
    private        long        lastChange;

    @Override
    public void onExtractItemDecorations(final GuiGraphicsExtractor graphics, final Font font, final ItemStack stack, final int xOffset, final int yOffset)
    {
        if (!stack.is(ModItems.colonyMap) || Minecraft.getInstance().level == null)
        {
            return;
        }

        final long gametime = Minecraft.getInstance().level.getGameTime();

        if (lastChange != gametime && gametime % 40 == 0)
        {
            lastChange = gametime;
            render = !render;
        }

        if (render)
        {
            colonyView = ColonyId.readColonyViewFromItemStack(stack);
            if (colonyView != null)
            {
                try
                {
                    int count = 0;
                    for (final ICitizenDataView view : colonyView.getCitizens().values())
                    {
                        if (view.hasBlockingInteractions())
                        {
                            count++;
                        }
                    }

                    if (count > 0)
                    {
                        final Component text = Component.literal(count + "");
                        graphics.text(font, text, xOffset + 15 - font.width(text) / 2, yOffset - 2, 0xFF4500 | (255 << 24));
                        return;
                    }
                }
                catch (Exception e)
                {
                    Log.getLogger().error("Something went wrong with the colonymap item decorator", e);
                }
            }
        }
    }
}