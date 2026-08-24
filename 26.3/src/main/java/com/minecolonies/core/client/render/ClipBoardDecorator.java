package com.minecolonies.core.client.render;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
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

import java.util.HashSet;
import java.util.Set;

/**
 * PORT-26.2: NeoForge's per-item {@code IItemDecorator} registry is gone; Fabric's
 * {@code ExtractItemDecorationsCallback} is a single global callback, so the decorator filters on the item itself and
 * the (removed) return value is replaced by simply not drawing.
 */
@Environment(EnvType.CLIENT)
public class ClipBoardDecorator implements ExtractItemDecorationsCallback
{
    private static IColonyView colonyView;
    private static boolean     render = false;
    private        long        lastChange;

    @Override
    public void onExtractItemDecorations(final GuiGraphicsExtractor graphics, final Font font, final ItemStack stack, final int xOffset, final int yOffset)
    {
        if (!stack.is(ModItems.clipboard) || Minecraft.getInstance().level == null)
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

                    final IRequestManager requestManager = colonyView.getRequestManager();
                    if (requestManager != null)
                    {
                        final IPlayerRequestResolver resolver = requestManager.getPlayerResolver();
                        final IRetryingRequestResolver retryingRequestResolver = requestManager.getRetryingRequestResolver();

                        final Set<IToken<?>> requestTokens = new HashSet<>();
                        requestTokens.addAll(resolver.getAllAssignedRequests());
                        requestTokens.addAll(retryingRequestResolver.getAllAssignedRequests());

                        for (final ICitizenDataView view : colonyView.getCitizens().values())
                        {
                            if (view.getJobView() != null)
                            {
                                requestTokens.removeAll(view.getJobView().getAsyncRequests());
                            }
                        }

                        if (!requestTokens.isEmpty())
                        {
                            final Component text = Component.literal(requestTokens.size() + "");
                            graphics.text(font, text, xOffset + 15 - font.width(text) / 2, yOffset - 2, 0xFFFF4500);
                            return;
                        }

                    }
                }
                catch (Exception e)
                {
                    Log.getLogger().error("Something went wrong with the clipboard item decorator", e);
                }
            }
        }
    }
}