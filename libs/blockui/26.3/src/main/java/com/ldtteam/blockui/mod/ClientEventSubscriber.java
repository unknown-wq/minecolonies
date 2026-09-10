package com.ldtteam.blockui.mod;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.util.SpacerTextComponent;
import com.ldtteam.blockui.util.resloc.OutOfJarResourceLocation;
import com.ldtteam.blockui.views.BOWindow;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bodies of the former {@code NeoForge.EVENT_BUS} client handlers. Registration lives in
 * {@link BlockUIClient} (contract K2); nothing here subscribes to anything by itself.
 */
public class ClientEventSubscriber
{
    /**
     * Start of the client tick — opens the developer test window on ctrl + alt + shift + the bound key
     * (X by default), exactly as the NeoForge {@code ClientTickEvent.Pre} handler did.
     *
     * <p>The same menu is reachable without a keyboard through {@code -Dblockui.testgui=menu}, see
     * {@link TestGuiLauncher}.</p>
     *
     * @param mc the client instance handed over by {@code ClientTickEvents.START_CLIENT_TICK}.
     */
    public static void onClientTickStart(final Minecraft mc)
    {
        if (mc.hasAltDown() && mc.hasControlDown() && mc.hasShiftDown())
        {
            if (BlockUIClient.isTestGuiKeyDown(mc) &&
                !(mc.gui.screen() instanceof final BOScreen screen &&
                    screen.getWindow().getXmlResourceLocation().getPath().equals("test_gui")))
            {
                buildDevMenu().open();
            }
        }
    }

    /**
     * Builds the developer menu: a code-only window of buttons, each opening one of the test layouts as a layer over
     * it. Split out of {@link #onClientTickStart(Minecraft)} so {@link TestGuiLauncher} can open the very same window
     * from a launch argument.
     *
     * @return the menu window, not yet opened.
     */
    public static BOWindow buildDevMenu()
    {
        final BOWindow window = new BOWindow(BlockUI.resLoc("test_gui"), false)
        {
            @Override
            public void onUpdate()
            {
                this.blurBackground = Minecraft.getInstance().hasControlDown();
                this.lightbox = Minecraft.getInstance().hasShiftDown();
                super.onUpdate();
            }
        };
        int id = 0;

        final Button dumpAtlases = createTestGuiButton(id++, "Dump ALL atlases to run folder");
        dumpAtlases.setHandler(b -> {
            final Path dumpingFolder = Path.of("atlas_dump").toAbsolutePath().normalize();
            Minecraft.getInstance().player
                .sendSystemMessage(Component.literal("Dumping atlases into: " + dumpingFolder.toString()));
            Minecraft.getInstance().getAtlasManager().forEach((resLoc, atlas) -> {
                try
                {
                    Files.createDirectories(dumpingFolder);
                    atlas.dumpContents(resLoc, dumpingFolder);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            });
        });
        window.addChild(dumpAtlases);

        window.addChild(createTestGuiLayerButton(id++, "Every control and view", () -> ShowcaseGui.showcase()));
        window.addChild(createTestGuiLayerButton(id++, "Nesting stress", () -> ShowcaseGui.nesting()));
        window.addChild(createTestGuiLayerButton(id++, "Parser edge cases", () -> ShowcaseGui.parser()));
        window.addChild(createTestGuiLayerButton(id++, "inherit + layout include", () -> ShowcaseGui.inherited()));
        window.addChild(createTestGuiLayerButton(id++, "General All-in-one", () -> buildTestWindow(1)));
        window.addChild(createTestGuiLayerButton(id++, "Tooltip Positioning", () -> buildTestWindow(2)));
        window.addChild(createTestGuiLayerButton(id++, "ItemIcon To BlockState", () -> buildTestWindow(3)));
        window.addChild(createTestGuiLayerButton(id++, "Scrolling Lists", () -> buildTestWindow(4)));

        final Text builderTest = new Text();
        builderTest.setSize(ButtonImage.DEFAULT_BUTTON_WIDTH * 2 + 20, ButtonImage.DEFAULT_BUTTON_HEIGHT * 2);
        builderTest.setPosition(0, ((id + 1) / 2) * (builderTest.getHeight() + 10));
        PaneBuilders.textBuilder()
            .append(Component.literal(BlockUI.MOD_ID))
            .append(Component.literal(" - "))
            .append(Component.literal(modVersion()))
            .paragraphBreak()
            .append(SpacerTextComponent.of(5))
            .newLine()
            .colorName("red")
            .underlined()
            .append(Component.translatable("blockui.tooltip.item_additional_info",
                Component.translatable("key.keyboard.left.control")
                    .append(" + ")
                    .append(Component.translatable("key.keyboard.left.shift"))
                    .append(" + ")
                    .append(Component.translatable("key.keyboard.left.alt"))
                    .setStyle(Style.EMPTY.withItalic(true))))
            .applyToPane(builderTest);
        window.addChild(builderTest);

        return window;
    }

    /**
     * The four historical {@code gui/testN.xml} windows, by number, with their code-side setup attached.
     *
     * @param index 1 to 4.
     * @return the window, not yet opened.
     */
    public static BOWindow buildTestWindow(final int index)
    {
        return switch (index)
        {
            case 1 -> testWindow(BlockUI.resLoc("gui/test.xml"), ClientEventSubscriber::setupOutOfJarImages);
            case 2 -> testWindow(BlockUI.resLoc("gui/test2.xml"));
            case 3 -> testWindow(BlockUI.resLoc("gui/test3.xml"), BlockStateTestGui::setup);
            case 4 -> testWindow(BlockUI.resLoc("gui/test4.xml"), ScrollingListsGui::setup);
            default -> throw new IllegalArgumentException("There is no gui/test" + index + ".xml");
        };
    }

    /**
     * The out-of-jar and player-skin image sources of {@code gui/test.xml}, which cannot be expressed in xml.
     */
    private static void setupOutOfJarImages(final BOWindow parent)
    {
        parent.findPaneOfTypeByID("missing_out_of_jar", Image.class)
            .setImage(OutOfJarResourceLocation.ofMinecraftFolder(BlockUI.MOD_ID, "missing_out_of_jar.png"), false);
        parent.findPaneOfTypeByID("working_out_of_jar", Image.class)
            .setImage(OutOfJarResourceLocation.of(BlockUI.MOD_ID, Path.of("../../src/test/resources/button.png")), false);
        OutOfJarResourceLocation.ofMinecraftSkin(Minecraft.getInstance(), Minecraft.getInstance().getGameProfile(), null)
            .thenAccept(resLoc -> parent.findPaneOfTypeByID("player_skin", Image.class).setImage(resLoc, false));
        OutOfJarResourceLocation
            .ofMinecraftSkin(Minecraft.getInstance(), Minecraft.getInstance().getGameProfile(), PlayerSkin::cape)
            .thenAccept(resLoc -> {
                if (resLoc != null)
                {
                    parent.findPaneOfTypeByID("player_cape", Image.class).setImage(resLoc, false);
                }
            });
        OutOfJarResourceLocation
            .ofMinecraftSkin(Minecraft.getInstance(), Minecraft.getInstance().getGameProfile(), PlayerSkin::elytra)
            .thenAccept(resLoc -> {
                if (resLoc != null)
                {
                    parent.findPaneOfTypeByID("player_elytra", Image.class).setImage(resLoc, false);
                }
            });
    }

    /**
     * Fabric has no {@code ModList}; the loader metadata is the replacement.
     */
    private static String modVersion()
    {
        return FabricLoader.getInstance()
            .getModContainer(BlockUI.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    /**
     * Builds a window from an xml layout and runs the given setups every time it is opened.
     *
     * @param testGuiResLoc the layout.
     * @param setups        code-side wiring the xml cannot express (data providers, dynamic textures, ...).
     * @return the window, not yet opened.
     */
    @SafeVarargs
    static BOWindow testWindow(final Identifier testGuiResLoc, final Consumer<BOWindow>... setups)
    {
        return new BOWindow(testGuiResLoc)
        {
            @Override
            public void onOpened()
            {
                super.onOpened();
                for (final Consumer<BOWindow> setup : setups)
                {
                    setup.accept(this);
                }
            }
        };
    }

    private static Button createTestGuiButton(final int order, final String name)
    {
        final Button button = new ButtonImage(true);
        button.setPosition((order % 2) * (button.getWidth() + 20), (order / 2) * (button.getHeight() + 10));
        button.setText(Component.literal(name));
        return button;
    }

    private static Button createTestGuiLayerButton(final int order, final String name, final Supplier<BOWindow> window)
    {
        final Button button = createTestGuiButton(order, name);
        button.setHandler(b -> window.get().openAsLayer());
        return button;
    }
}
