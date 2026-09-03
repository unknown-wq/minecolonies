package com.minecolonies.core.client.assetfetch.gui;

import com.minecolonies.core.client.assetfetch.AssetFetch;
import com.minecolonies.core.client.assetfetch.AssetInstaller;
import com.minecolonies.core.client.assetfetch.SourceChain;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The consent screen (task D1): the first thing a player sees on a fresh install, and the way back in from
 * the window-open gate and the client command.
 *
 * <p>It says three things, because the player is being asked to fetch someone else's copyrighted files:
 * <b>what</b> is downloaded (LDTTeam's own build, from LDTTeam's own Maven server, with the exact byte
 * count), <b>where the files end up</b> (this computer, and nowhere else), and <b>what the alternative is</b>
 * (point the mod at a MineColonies 1.21.1 jar the player already has). Nothing happens until a button is
 * pressed.</p>
 *
 * <p>"Not now" lasts the session and nothing more, so the next launch asks again; within a session,
 * {@link AssetsMissingScreen} and {@code /minecolonies-client fetchassets} bring the screen back.</p>
 *
 * <p>It says the same three things when the assets are installed but were downloaded for an earlier version
 * of this port, with one difference the player needs: the first line then explains that what is on this
 * computer no longer matches the mod and that the replacement is fetched before anything already installed
 * is touched. Saying no leaves that older pack exactly where it is and working.</p>
 */
@Environment(EnvType.CLIENT)
public class AssetConsentScreen extends Screen
{
    /**
     * Where "Not now" and Escape go back to. Null means "back to the game".
     */
    private final @Nullable Screen parent;

    /**
     * Creates the screen.
     *
     * @param parent the screen to return to, or null to return to the game.
     */
    public AssetConsentScreen(final @Nullable Screen parent)
    {
        super(Component.translatable(AssetFetch.isStale() ? AssetFetchLang.CONSENT_TITLE_UPDATE : AssetFetchLang.CONSENT_TITLE));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        super.init();

        final long size = SourceChain.MAVEN_1374.expectedSize();
        final int textWidth = Math.min(this.width - 40, 380);

        final LinearLayout layout = LinearLayout.vertical().spacing(8);
        layout.defaultCellSetting().alignHorizontallyCenter();

        layout.addChild(new StringWidget(this.title, this.font));
        layout.addChild(new MultiLineTextWidget(
            // A bare number, because consent.body carries the unit itself -- MB, МБ, Mo, whichever the
            // language uses. The exact byte count that used to sit next to it said nothing a player acts on;
            // it is in the log, on both the successful attempt and the size-mismatch error.
            Component.translatable(AssetFetch.isStale() ? AssetFetchLang.CONSENT_BODY_UPDATE : AssetFetchLang.CONSENT_BODY,
                AssetFetchScreenSupport.megabytesNumber(size)),
            this.font).setMaxWidth(textWidth).setCentered(true));
        layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.CONSENT_LICENCE), this.font)
            .setMaxWidth(textWidth).setCentered(true));
        layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.CONSENT_MANUAL), this.font)
            .setMaxWidth(textWidth).setCentered(true));

        final LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(8));
        buttons.defaultCellSetting().paddingTop(12);
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_DOWNLOAD), b -> this.download()).width(120).build());
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_NOT_NOW), b -> this.notNow()).width(120).build());

        final LinearLayout manual = layout.addChild(LinearLayout.horizontal());
        manual.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_LOCAL_JAR), b -> this.localJar()).width(248).build());

        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, this.getRectangle());
    }

    /**
     * Starts the automatic source chain.
     */
    private void download()
    {
        this.minecraft.gui.setScreen(AssetInstallScreen.startAutomatic(this.parent));
    }

    /**
     * Records the decline and leaves.
     */
    private void notNow()
    {
        AssetInstaller.recordDeclined();
        this.onClose();
    }

    /**
     * Opens the path-entry screen for source 4.
     */
    private void localJar()
    {
        this.minecraft.gui.setScreen(new AssetLocalJarScreen(this, this.parent));
    }

    @Override
    public void onClose()
    {
        this.minecraft.gui.setScreen(this.parent);
    }
}
