package com.minecolonies.core.client.gui;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.util.Mth;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.minecolonies.api.util.constant.translation.BaseGameTranslationConstants.BASE_GUI_DONE;
import static net.minecraft.client.gui.components.Button.DEFAULT_NARRATION;

/**
 * A custom rendered Screen (i.e. not blockui) that renders a picker for the banners,
 * similar to a loom. The resulting banner cannot be extracted.
 */
@Environment(EnvType.CLIENT)
public class WindowBannerPicker extends Screen
{
    /** The Y position of the layers */
    private static final int GUI_Y = 30;

    /** The side length for layer and palette buttons */
    private static final int SIDE = 20;

    /** The height of the pattern buttons */
    private static final int PATTERN_HEIGHT = 30;

    /** The width of the pattern buttons */
    private static final int PATTERN_WIDTH = PATTERN_HEIGHT / 2;

    /** The margin after each pattern button */
    private static final int PATTERN_MARGIN = 3;

    /** The number of columns the patterns are arranged in */
    private static final int PATTERN_COLUMNS = 8;

    /** The number of rows the patterns are arranged in */
    private static final int PATTERN_ROWS = 4;


    /**
     * The list of patterns that usually require charges, or are to be made more valuable
     * by excluding them from lower TH levels. Sorted by the TH level they are first introduced at
     */
    private static final ResourceKey[][] EXCLUSION = {
            {    // 1
                BannerPatterns.GRADIENT,
                BannerPatterns.GRADIENT_UP
            }, { // 2
                BannerPatterns.BRICKS,
                BannerPatterns.FLOWER
            }, { // 3
                BannerPatterns.SKULL,
                BannerPatterns.CREEPER
            }, { // 4
                BannerPatterns.GLOBE,
                BannerPatterns.PIGLIN
            }, { // 5
                BannerPatterns.MOJANG
            }, { // Excluded completely
                BannerPatterns.BASE
            }
    };

    /** The list of banner patterns, to be excluded and cached */
    private final List<Holder<BannerPattern>> patterns;

    /** The final list of patterns and colors of the flag */
    private final List<BannerPatternLayers.Layer> layers;

    /** The colony this flag refers to */
    private final IColonyView colony;

    /** The town hall window that called this picker. Will be used to return to it. */
    private final AbstractWindowTownHall window;

    /** The flag model used for the banner preview. */
    private final BannerFlagModel flagModel;

    /**
     * Local reference of feature unlocked flag.
     */
    private final AtomicBoolean isFeatureUnlocked;

    /** The currently selected palette color. */
    private ColorPalette colors;

    /** The currently selected layer. Zero is the base. */
    private int activeLayer = 0;

    /** Whether or not the player is dragging the scrollbar */
    private boolean scrolling = false;

    /** The number of rows scrolled past */
    private int scrollRow = 0;

    /**
     * @param colony            the colony to make the flag for
     * @param hallWindow        the calling town hall window to return to
     * @param isFeatureUnlocked
     */
    public WindowBannerPicker(IColonyView colony, AbstractWindowTownHall hallWindow, final AtomicBoolean isFeatureUnlocked)
    {
        super(Component.literal("Flag"));

        this.colony = colony;
        this.window = hallWindow;
        this.flagModel = new BannerFlagModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));

        /* Get all patterns, then remove excluded and item-required patterns */
        List<Holder<BannerPattern>> exclusion = new ArrayList<>();
        for (int i = hallWindow.buildingView.getBuildingLevel(); i <= hallWindow.buildingView.getBuildingMaxLevel(); i++)
        {
            for (final ResourceKey key : EXCLUSION[i])
            {
                exclusion.add(Utils.getRegistryValue(key, colony.getWorld()));
            }
        }

        this.patterns = colony.getWorld().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).listElements().map(h -> (Holder<BannerPattern>) h).collect(Collectors.toCollection(LinkedList::new));
        this.patterns.removeAll(exclusion);
        this.isFeatureUnlocked = isFeatureUnlocked;

        // Fetch the patterns as a List and not ListNBT
        this.layers = new ArrayList<>(colony.getColonyFlag().layers());
        // Remove the extra base layer created by the above function
    }

    @Override
    protected void init()
    {
        int paletteX = center(this.width, PATTERN_COLUMNS, PATTERN_WIDTH, 0, 0) - 70;
        this.colors = new ColorPalette(paletteX, this.height/2, 2, this::addRenderableWidget);
        colors.onchange = color -> setLayer(null, color);

        createLayerButtons();
        createPatternButtons();
        createCloseButtons();
    }

    /**
     * Creates the buttons for banner layer selection; Base, 1-6, and the remove button
     */
    protected void createLayerButtons()
    {
        for (int layer = 0; layer <= 6; layer++)
        {
            int posX = (this.width - SIDE * 6) / 2 + layer * SIDE;

            this.addRenderableWidget(new LayerButton(posX, GUI_Y, SIDE, SIDE, layer));
        }

        // PORT-26.2: Button is abstract now (AbstractButton#extractContents); the "remove layer" button keeps its
        //  behaviour through a small named subclass instead of an anonymous `new Button(...)`.
        this.addRenderableWidget(new RemoveLayerButton(center(this.width, 6, SIDE, 7, 0), GUI_Y, SIDE, SIDE));
    }

    /**
     * Creates the buttons behind each pattern.
     */
    protected void createPatternButtons()
    {
        for (int i = 0; i < patterns.size(); i++)
        {
            int posX = center(this.width, PATTERN_COLUMNS, PATTERN_WIDTH, i % PATTERN_COLUMNS, PATTERN_MARGIN);
            int posY = center(this.height+30, PATTERN_ROWS, PATTERN_HEIGHT, Math.floorDiv(i, PATTERN_COLUMNS), PATTERN_MARGIN);

            final PatternButton button = new PatternButton(posX, posY, PATTERN_HEIGHT, patterns.get(i));
            this.addRenderableWidget(button);

            if (!isFeatureUnlocked.get() && patterns.get(i).unwrapKey().get().identifier().getNamespace().equals(Constants.MOD_ID))
            {
                button.setTooltip(Tooltip.create(Component.translatable("com.minecolonies.core.gui.banner.patreon")));
                button.blocked = true;
            }
        }
    }

    /**
     * Creates the Done and Cancel buttons, to return to the town hall window and save the banner or not, respectively.
     */
    protected void createCloseButtons()
    {
        this.addRenderableWidget(Button.builder(Component.translatableEscape(BASE_GUI_DONE),
                pressed -> {
                    BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
                    for (BannerPatternLayers.Layer layer : layers)
                        builder.add(layer);

                    colony.setColonyFlag(builder.build());
                    window.open();
                })
            .bounds(center(this.width, 2, 80, 1, 10), this.height - 40, 80, SIDE)
            .build());
        this.addRenderableWidget(Button.builder(Component.translatableEscape("gui.cancel"), pressed -> window.open())
            .bounds(center(this.width, 2, 80, 0, 10), this.height - 40, 80, SIDE)
            .build());
    }

    /**
     * Positions a button within a grid based on the center coordinates of that grid.
     * This method is Axis agnostic.
     * @param length the length of the grid
     * @param count the number of items along that length
     * @param side the side length of the items in the relevant axis
     * @param n the nth item we are positioning
     * @param margin the gap between elements, half of this gap length borders the hole grid
     * @return the coordinate along the relevant axis
     */
    public static int center(int length, int count, int side, int n, int margin)
    {
        return (length - count * (side + margin)) / 2 + n * (side + margin) + margin / 2;
    }

    /**
     * Tries to set the layer in the banner pattern list with the given information
     * @param pattern the pattern to set in the layer. Uses the existing or BASE if null
     * @param color the associated color for the pattern
     */
    public void setLayer(@Nullable Holder<BannerPattern> pattern, DyeColor color)
    {
        if (pattern == null)
        {
            // Drop out if only the color was selected.
            if (activeLayer == layers.size()) return;
            else if (activeLayer == 0) pattern = Utils.getRegistryValue(BannerPatterns.BASE, colony.getWorld());
            else pattern = layers.get(activeLayer).pattern();
        }

        if (activeLayer == layers.size())
            layers.add(new BannerPatternLayers.Layer(pattern, color));
        else
            layers.set(activeLayer, new BannerPatternLayers.Layer(pattern, color));
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor stack, int mouseX, int mouseY, float partialTicks)
    {
        super.extractRenderState(stack, mouseX, mouseY, partialTicks);
        drawFlag(stack);

        // Draw the scrollbar
        int scrollRows = (int) (Math.ceil(this.patterns.size() / (float) PATTERN_COLUMNS) - PATTERN_ROWS);
        if (scrollRows > 0 && activeLayer > 0)
        {
            int trackHeight = (PATTERN_HEIGHT + PATTERN_MARGIN) * PATTERN_ROWS;
            double barHeight = trackHeight * (PATTERN_ROWS / (float)(scrollRows + PATTERN_ROWS));
            int trackX = center(this.width, PATTERN_COLUMNS, PATTERN_WIDTH, PATTERN_COLUMNS, PATTERN_MARGIN);
            int trackY = (int) (center(this.height, PATTERN_ROWS, PATTERN_HEIGHT, 0, PATTERN_MARGIN)
                                + this.scrollRow * (trackHeight / (float)(scrollRows + PATTERN_ROWS)));

            stack.fill(trackX+2, trackY, trackX+6, trackY+ (int) barHeight, 0xBBFFFFFF);
        }


        // Render the instructions
        final String instructions = Component.translatableEscape("com.minecolonies.coremod.gui.flag.choose").getString();
        stack.text(this.font, instructions, this.width / 2 - this.font.width(instructions) / 2, 16, 0xFFFFFFFF);
    }

    /**
     * Sets the large final preview of the banner for rendering
     */
    private void drawFlag(final GuiGraphicsExtractor stack)
    {
        // PORT-26.2: banners in a GUI are a picture-in-picture render state now
        // (GuiGraphicsExtractor#bannerPattern, see LoomScreen); PoseStack/Lighting/BannerRenderer are all gone here.
        final BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
        for (final BannerPatternLayers.Layer layer : this.layers)
        {
            builder.add(layer);
        }

        final int x0 = (int) ((this.width + PATTERN_HEIGHT / 2.0 * PATTERN_COLUMNS) / 2 + SIDE * 2) - 20;
        final int y0 = this.height / 2 - 40;
        stack.bannerPattern(this.flagModel, colors.getSelected(), builder.build(), x0, y0, x0 + 40, y0 + 80);
    }

    /**
     * Sets a specific banner pattern in place to be rendered
     * @param pattern the banner pattern to render
     * @param x the left x position of the banner
     * @param y the top y position of the banner
     * @param stack 
     */
    private void drawBannerPattern(final Holder<BannerPattern> pattern, final int x, final int y, final GuiGraphicsExtractor stack)
    {
        // PORT-26.2: the small pattern previews are blitted straight off the banner-pattern atlas, exactly the way
        //  vanilla's LoomScreen#extractBannerOnButton does it; the old PoseStack + BannerRenderer path is gone.
        final TextureAtlasSprite sprite = stack.getSprite(Sheets.getBannerSprite(pattern));
        final boolean blocked = !isFeatureUnlocked.get()
                                  && pattern.unwrapKey().get().identifier().getNamespace().equals(Constants.MOD_ID);

        stack.pose().pushMatrix();
        stack.pose().translate(x + 2, y + 2);

        final float u0 = sprite.getU0();
        final float u1 = u0 + (sprite.getU1() - u0) * 21.0F / 64.0F;
        final float vSpan = sprite.getV1() - sprite.getV0();
        final float v0 = sprite.getV0() + vSpan / 64.0F;
        final float v1 = v0 + vSpan * 40.0F / 64.0F;

        stack.fill(0, 0, PATTERN_WIDTH - 2, PATTERN_HEIGHT - 6, DyeColor.GRAY.getTextureDiffuseColor());
        stack.blit(sprite.atlasLocation(), 0, 0, PATTERN_WIDTH - 2, PATTERN_HEIGHT - 6, u0, u1, v0, v1);
        if (blocked)
        {
            stack.fill(0, 0, PATTERN_WIDTH - 2, PATTERN_HEIGHT - 6, 0xAA000000);
        }

        stack.pose().popMatrix();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (activeLayer > 0) {
            this.scrollRow = (int) Mth.clamp(
                    this.scrollRow - scrollY,
                    0,
                    Math.ceil(this.patterns.size() / PATTERN_COLUMNS) - PATTERN_ROWS + 1 // Extra 1 so it is inclusive
            );
        }

        return true;
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick)
    {
        final double mouseX = event.x();
        final double mouseY = event.y();
        this.scrolling = false;

        int trackX = center(this.width, PATTERN_COLUMNS, PATTERN_WIDTH, PATTERN_COLUMNS, PATTERN_MARGIN);
        int trackY = center(this.height, PATTERN_ROWS, PATTERN_HEIGHT, 0, PATTERN_MARGIN);
        int trackEnd = trackY + PATTERN_ROWS*(PATTERN_HEIGHT + PATTERN_MARGIN);
        if (mouseX > trackX + 2 && mouseX < trackX + 8 && mouseY > trackY && mouseY < trackEnd)
            this.scrolling = true;
        
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(final MouseButtonEvent event, final double deltaX, final double deltaY)
    {
        final double mouseY = event.y();
        if (this.scrolling && this.activeLayer > 0) {

            int trackStart = center(this.height, PATTERN_ROWS, PATTERN_HEIGHT, 0, PATTERN_MARGIN);
            int trackLength = PATTERN_ROWS*(PATTERN_HEIGHT + PATTERN_MARGIN);

            double scrollRatio = Mth.clamp(
                    (mouseY - trackStart) / trackLength,
                    0, 1
            );
            this.scrollRow = (int) Math.round(scrollRatio * (Math.ceil(this.patterns.size() / PATTERN_COLUMNS) - PATTERN_ROWS + 1));

            return true;
        } else {
            return super.mouseDragged(event, deltaX, deltaY);
        }
    }

    /**
     * The "remove active layer" button.
     */
    public class RemoveLayerButton extends Button
    {
        public RemoveLayerButton(final int x, final int y, final int width, final int height)
        {
            super(x, y, width, height, Component.literal(ChatFormatting.RED + "X"), pressed -> {}, DEFAULT_NARRATION);
        }

        @Override
        public void onPress(final InputWithModifiers input)
        {
            if (activeLayer < layers.size() && activeLayer != 0)
            {
                layers.remove(activeLayer);
            }
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor stack, final int mouseX, final int mouseY, final float partialTicks)
        {
            this.active = activeLayer < layers.size() && activeLayer != 0;
            // PORT-26.2: Button is abstract and the default look lives in Button.Plain#extractContents, which is not
            //  reachable via super; reproduce it.
            this.extractDefaultSprite(stack);
            this.extractDefaultLabel(stack.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }
    }

    /**
     * A custom button for each layer, to override click and render logic
     */
    public class LayerButton extends Button
    {
        private final int layer;

        /**
         * @param x the left x position of the button
         * @param y the top y position of the button
         * @param width the width of the button. Probably 20. Overridden if layer is 0.
         * @param height the height of the button. Probably 20.
         * @param layer the layer this button represents.
         */
        public LayerButton(int x, int y, int width, int height, int layer)
        {
            super(
                    x - (layer == 0 ? width*2 : 0), y,
                    width * (layer == 0 ? 3 : 1), height,
                    layer == 0
                            ? Component.translatableEscape("com.minecolonies.coremod.gui.flag.base_layer")
                            : Component.literal(String.valueOf(layer)),
                    pressed -> {},
                    DEFAULT_NARRATION
            );
            this.layer = layer;
        }

        @Override
        public void onPress(final InputWithModifiers input)
        {
            activeLayer = this.layer;

            if (this.layer >= layers.size())
                colors.setSelected(layers.get(0).color().equals(DyeColor.BLACK) ? DyeColor.WHITE : DyeColor.BLACK);
            else
                colors.setSelected(layers.get(activeLayer).color());
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor stack, int p_render_1_, int p_render_2_, float p_render_3_)
        {
            this.active = this.layer <= layers.size();
            // PORT-26.2: Button is abstract and the default look lives in Button.Plain#extractContents, which is not
            //  reachable via super; reproduce it.
            this.extractDefaultSprite(stack);
            this.extractDefaultLabel(stack.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));

            if (activeLayer == this.layer)
                stack.fill(this.getX(), this.getY(), this.getX()+this.width, this.getY()+this.height, 0x66DD99FF);
        }
    }

    /**
     * A custom button for each pattern, to override click and render logic
     */
    public class PatternButton extends Button
    {
        private final Holder<BannerPattern> pattern;
        private int index = -1;
        private boolean blocked = false;

        /**
         * @param x the left x position of the button
         * @param y the top y position of the button
         * @param height the height of the button. Twice the width, always
         * @param pattern the pattern this button represents
         */
        public PatternButton(int x, int y, int height, Holder<BannerPattern> pattern)
        {
            super(x, y, height/2, height, Component.literal(""), btn -> {}, DEFAULT_NARRATION);
            this.pattern = pattern;
            int tempIndex = 0;
            for (final Holder<BannerPattern> pat : WindowBannerPicker.this.patterns)
            {
                if (pat.value().assetId().equals(pattern.value().assetId()))
                {
                    this.index = tempIndex;
                    break;
                }
                tempIndex++;
            }
        }

        @Override
        public void onPress(final InputWithModifiers input)
        {
            if (!this.blocked)
            {
                setLayer(this.pattern, colors.getSelected());
            }
        }

        @Override
        protected void extractContents(final GuiGraphicsExtractor stack, int mx, int my, float p_renderButton_3_)
        {
            boolean isVisible = scrollRow * PATTERN_COLUMNS <= this.index && this.index < PATTERN_COLUMNS * (scrollRow + PATTERN_ROWS);
            this.active = activeLayer != 0;

            if (!this.active || !this.visible || !isVisible) return;

            int position = Math.floorDiv(this.index - scrollRow*PATTERN_COLUMNS, PATTERN_COLUMNS);
            this.setY(center(WindowBannerPicker.this.height, PATTERN_ROWS, PATTERN_HEIGHT, position, PATTERN_MARGIN));
            this.isHovered = mx >= this.getX() && my >= this.getY() && mx < this.getX() + this.width && my < this.getY() + this.height;

            // PORT-26.2: Button is abstract and the default look lives in Button.Plain#extractContents, which is not
            //  reachable via super; reproduce it.
            this.extractDefaultSprite(stack);
            this.extractDefaultLabel(stack.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));

            if (isVisible)
            {
                if (this.blocked)
                {
                    stack.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF000000);
                }
                else if (this.visible)
                {
                    if (this.isHovered && this.active)
                        stack.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xDDFFFFFF);

                    if (activeLayer < layers.size() && layers.get(activeLayer).pattern() == this.pattern)
                        stack.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFDD88FF);

                    else
                        stack.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x33888888);
                }
            }

            try
            {
                drawBannerPattern(this.pattern, this.getX(), this.getY(), stack);
            }
            catch (final Exception ex)
            {
                Log.getLogger().warn(pattern.value().translationKey());
                Log.getLogger().error(ex);
            }
        }
    }
}
