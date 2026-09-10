package com.minecolonies.core.client.assetfetch.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * The manual escape hatch (source 4): the player types the path of a MineColonies 1.21.1 jar they already
 * have — from CurseForge, from another instance, from a backup — and the identical extract, patch and assemble
 * pipeline runs against it.
 *
 * <p>This is a plain path field rather than a native file chooser on purpose: a native dialog needs either
 * AWT on the render thread or an LWJGL/tinyfd call, both of which are their own portability problem, and this
 * screen has to work on a client whose resources are missing. The path is only checked for "is a readable
 * file" here; everything that matters — the whole-jar hash, the manifest's file list — is the installer's
 * job and its failure text is what the player sees.</p>
 */
@Environment(EnvType.CLIENT)
public class AssetLocalJarScreen extends Screen
{
    /**
     * Where Cancel goes back to: the consent screen this was opened from.
     */
    private final Screen consent;

    /**
     * What the install screen should return to when it finishes. Null means "back to the game".
     */
    private final @Nullable Screen parent;

    /**
     * The typed path. Kept across {@link #init} so a window resize does not clear it.
     */
    private String path = "";

    /**
     * Set when the typed path is not a readable file, cleared on the next attempt.
     */
    private boolean showError = false;

    /**
     * Creates the screen.
     *
     * @param consent the consent screen to go back to.
     * @param parent  what the install screen returns to when it is done.
     */
    public AssetLocalJarScreen(final Screen consent, final @Nullable Screen parent)
    {
        super(Component.translatable(AssetFetchLang.LOCALJAR_TITLE));
        this.consent = consent;
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        super.init();

        final int textWidth = Math.min(this.width - 40, 380);
        final LinearLayout layout = LinearLayout.vertical().spacing(8);
        layout.defaultCellSetting().alignHorizontallyCenter();

        layout.addChild(new StringWidget(this.title, this.font));
        layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.LOCALJAR_BODY), this.font)
            .setMaxWidth(textWidth).setCentered(true));

        final EditBox box = layout.addChild(new EditBox(this.font, textWidth, 20, Component.translatable(AssetFetchLang.LOCALJAR_HINT)));
        box.setMaxLength(512);
        box.setHint(Component.translatable(AssetFetchLang.LOCALJAR_HINT));
        box.setValue(this.path);
        box.setResponder(value -> this.path = value);

        if (this.showError)
        {
            layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.LOCALJAR_NOT_A_FILE), this.font)
                .setMaxWidth(textWidth).setCentered(true));
        }

        final LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(8));
        buttons.defaultCellSetting().paddingTop(12);
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_DOWNLOAD), b -> this.accept()).width(120).build());
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_CANCEL), b -> this.onClose()).width(120).build());

        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, this.getRectangle());
    }

    /**
     * Validates the typed path and hands it to the installer.
     */
    private void accept()
    {
        final Path jar = this.parsePath();
        if (jar == null)
        {
            this.showError = true;
            this.rebuildWidgets();
            return;
        }

        this.showError = false;
        this.minecraft.gui.setScreen(AssetInstallScreen.startLocalJar(this.parent, jar));
    }

    /**
     * Turns the typed text into a readable file, or null when it is not one.
     *
     * @return the file, or null.
     */
    private @Nullable Path parsePath()
    {
        final String typed = this.path.trim().replace("\"", "");
        if (typed.isEmpty())
        {
            return null;
        }

        try
        {
            final Path candidate = Path.of(typed);
            return Files.isRegularFile(candidate) && Files.isReadable(candidate) ? candidate : null;
        }
        catch (final InvalidPathException e)
        {
            return null;
        }
    }

    @Override
    public void onClose()
    {
        this.minecraft.gui.setScreen(this.consent);
    }
}
