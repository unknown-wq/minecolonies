package com.minecolonies.core.client.assetfetch.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What opens instead of a MineColonies window when the fetched assets are not installed (task D2).
 *
 * <p>Every BlockUI window this mod has is defined by an XML file under {@code assets/minecolonies/gui/}, and
 * those files arrive with the download. Without them {@code Loader.createFromXMLFile} throws
 * {@code RuntimeException("Gui at ... was not found!")} from inside a screen-open path, which crashes the
 * client. So the port checks first and opens this instead — and, since the player has just tried to use the
 * mod, offers the download right here rather than making them find it again.</p>
 */
@Environment(EnvType.CLIENT)
public class AssetsMissingScreen extends Screen
{
    /**
     * Creates the screen.
     */
    public AssetsMissingScreen()
    {
        super(Component.translatable(AssetFetchLang.GATE_TITLE));
    }

    @Override
    protected void init()
    {
        super.init();

        final int textWidth = Math.min(this.width - 40, 380);
        final LinearLayout layout = LinearLayout.vertical().spacing(8);
        layout.defaultCellSetting().alignHorizontallyCenter();

        layout.addChild(new StringWidget(this.title, this.font));
        layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.GATE_BODY), this.font)
            .setMaxWidth(textWidth).setCentered(true));

        final LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(8));
        buttons.defaultCellSetting().paddingTop(12);
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_DOWNLOAD),
            b -> this.minecraft.gui.setScreen(new AssetConsentScreen(null))).width(120).build());
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_CANCEL), b -> this.onClose()).width(120).build());

        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, this.getRectangle());
    }

    @Override
    public void onClose()
    {
        this.minecraft.gui.setScreen(null);
    }
}
