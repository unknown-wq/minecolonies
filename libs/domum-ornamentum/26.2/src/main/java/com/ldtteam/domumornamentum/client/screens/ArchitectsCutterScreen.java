package com.ldtteam.domumornamentum.client.screens;

import com.ldtteam.domumornamentum.DomumOrnamentum;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.container.ArchitectsCutterContainer;
import com.ldtteam.domumornamentum.item.interfaces.IDoItem;
import com.ldtteam.domumornamentum.util.Constants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ldtteam.domumornamentum.util.GuiConstants.*;

/**
 * The Architect's Cutter screen.
 *
 * <h2>26.2 screen contract</h2>
 * GUI rendering became a two-phase extract/render pipeline, so every draw method changed shape (all verified
 * against {@code /opt/mc-src/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java} and
 * {@code .../screens/Screen.java}):
 * <ul>
 *   <li>{@code GuiGraphics} -&gt; {@code GuiGraphicsExtractor}</li>
 *   <li>{@code render(GuiGraphics, int, int, float)} -&gt;
 *       {@code extractRenderState(GuiGraphicsExtractor, int, int, float)}</li>
 *   <li>{@code renderBg(GuiGraphics, float, int, int)} -&gt;
 *       {@code extractBackground(GuiGraphicsExtractor, int mouseX, int mouseY, float)} - note the argument
 *       order changed: the old signature took {@code (partialTick, x, y)}, the new one takes
 *       {@code (mouseX, mouseY, a)}.</li>
 *   <li>{@code renderLabels(GuiGraphics, int, int)} -&gt; {@code extractLabels(GuiGraphicsExtractor, int, int)}</li>
 *   <li>{@code renderTooltip(GuiGraphics, int, int)} -&gt; there is no overridable tooltip hook any more;
 *       tooltips are queued with {@code graphics.setTooltipForNextFrame(...)} from
 *       {@code extractRenderState}.</li>
 *   <li>{@code graphics.drawString(font, …)} -&gt; {@code graphics.text(font, …)};
 *       {@code graphics.renderItem(stack, x, y)} -&gt; {@code graphics.item(stack, x, y)};
 *       {@code graphics.blit(Identifier, …)} -&gt;
 *       {@code graphics.blit(RenderPipeline, Identifier, x, y, u, v, w, h, texW, texH)} - a
 *       {@code RenderPipeline} argument is now mandatory ({@code RenderPipelines.GUI_TEXTURED}).</li>
 *   <li>{@code imageWidth}/{@code imageHeight} are {@code protected final} and must be passed to
 *       {@code super(menu, inventory, title, imageWidth, imageHeight)}.</li>
 *   <li>{@code mouseClicked(double, double, int)} -&gt; {@code mouseClicked(MouseButtonEvent, boolean doubleClick)};
 *       {@code mouseDragged(double, double, int, double, double)} -&gt;
 *       {@code mouseDragged(MouseButtonEvent, double dx, double dy)}. {@code mouseScrolled} is unchanged.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ArchitectsCutterScreen extends AbstractContainerScreen<ArchitectsCutterContainer>
{
    private static final Identifier BACKGROUND_TEXTURE1 = Constants.resLocDO("textures/gui/container/architectscutter.png");
    private static final Identifier BACKGROUND_TEXTURE2 = Constants.resLocDO("textures/gui/container/architectscutter2.png");

    /** All GUI sheets in this mod are 256x256, which the 26.2 blit overload wants explicitly. */
    private static final int TEXTURE_SIZE = 256;

    private float recipeSliderProgress;

    /**
     * Is {@code true} if the player clicked on the scroll wheel in the GUI.
     */
    private boolean clickedOnRecipeScroll;

    /**
     * The index of the first recipe to display.
     * The number of recipes displayed at any time is 10 (10 recipes per row and 1 row). If the player scrolled down one
     * row, this value would be 10 (representing the index of the first slot on the second row).
     */
    private int recipeIndexOffset;

    private float typeSliderProgress;

    /**
     * Is {@code true} if the player clicked on the scroll wheel in the GUI.
     */
    private boolean clickedOnTypeScroll;

    /**
     * The index of the first recipe to display.
     * The number of recipes displayed at any time is 10 (10 recipes per row and 1 row). If the player scrolled down one
     * row, this value would be 10 (representing the index of the first slot on the second row).
     */
    private int typeIndexOffset;

    /**
     * Group index cache.
     */
    private static int groupIndexCache = 0;

    /**
     * Variant index cache.
     */
    private static int variantIndexCache = -1;

    public ArchitectsCutterScreen(final ArchitectsCutterContainer containerIn, final Inventory playerInv, final Component titleIn)
    {
        super(containerIn, playerInv, titleIn, CUTTER_BG_W, CUTTER_BG_H);
        --this.titleLabelY;
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a)
    {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.extractCustomTooltips(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a)
    {
        super.extractBackground(graphics, mouseX, mouseY, a);

        final int guiLeft = this.leftPos;
        final int guiTop = this.topPos;

        blit(graphics, getBackGroundTexture(), guiLeft, guiTop, 0, 0, this.imageWidth, this.imageHeight);

        if (this.menu.getCurrentGroup() == null)
        {
            (this.menu).clickMenuButton(Objects.requireNonNull(Objects.requireNonNull(this.minecraft).player), groupIndexCache);
            Objects.requireNonNull(this.minecraft.gameMode).handleInventoryButtonClick(this.menu.containerId, groupIndexCache);
        }

        if (this.menu.getCurrentGroup() != null && this.menu.getCurrentVariant() == null)
        {
            if (variantIndexCache == -1)
            {
                variantIndexCache = ModBlocks.getInstance().itemGroups.size();
            }
            (this.menu).clickMenuButton(Objects.requireNonNull(Objects.requireNonNull(this.minecraft).player), variantIndexCache);
            Objects.requireNonNull(this.minecraft.gameMode).handleInventoryButtonClick(this.menu.containerId, variantIndexCache);
        }

        if (this.menu.getCurrentGroup() != null)
        {
            final int sliderOffset1 = (int) (5.0F * this.recipeSliderProgress);
            blit(graphics, getBackGroundTexture(), guiLeft + CUTTER_SLIDER_X, guiTop + CUTTER_SLIDER_Y + CUTTER_RECIPE_SPACING + sliderOffset1,
              this.canScrollRecipes() ? CUTTER_SLIDER_U_ENABLED : CUTTER_SLIDER_U_DISABLED, CUTTER_SLIDER_V, CUTTER_SLIDER_W, CUTTER_SLIDER_H);
        }

        final int sliderOffset2 = (int) (5.0F * this.typeSliderProgress);
        blit(graphics, getBackGroundTexture(), guiLeft + CUTTER_SLIDER_X, guiTop + CUTTER_SLIDER_Y + sliderOffset2,
          this.canScrollTypes() ? CUTTER_SLIDER_U_ENABLED : CUTTER_SLIDER_U_DISABLED, CUTTER_SLIDER_V, CUTTER_SLIDER_W, CUTTER_SLIDER_H);

        final int recipeAreaLeft = this.leftPos + CUTTER_RECIPE_X;
        final int recipeAreaTop = this.topPos + CUTTER_RECIPE_Y;

        this.drawSlotBackgrounds(graphics);
        this.drawRecipeButtonBackgrounds(graphics, mouseX, mouseY, recipeAreaLeft, recipeAreaTop);
        this.drawRecipesItems(graphics, recipeAreaLeft, recipeAreaTop);
    }

    /**
     * 26.2 dropped the {@code blit(Identifier, x, y, u, v, w, h)} convenience overload: the surviving one takes
     * a {@code RenderPipeline} plus explicit float UVs and the texture dimensions
     * ({@code /opt/mc-src/net/minecraft/client/gui/GuiGraphicsExtractor.java}:313+).
     */
    private static void blit(
      final GuiGraphicsExtractor graphics, final Identifier texture,
      final int x, final int y, final int u, final int v, final int width, final int height)
    {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private Identifier getBackGroundTexture()
    {
        return this.menu.getCurrentGroup() == null ? BACKGROUND_TEXTURE1 : BACKGROUND_TEXTURE2;
    }

    /**
     * Replaces the old {@code renderTooltip(GuiGraphics, int, int)} override. The base class already queues the
     * hovered-slot tooltip; this only adds the group/variant button tooltips.
     */
    private void extractCustomTooltips(final GuiGraphicsExtractor graphics, final int x, final int y)
    {
        {
            final int i = this.leftPos + CUTTER_RECIPE_X;
            final int j = this.topPos + CUTTER_RECIPE_Y;
            final int k = this.typeIndexOffset + 10;
            final List<Identifier> list = new ArrayList<>(ModBlocks.getInstance().getOrComputeItemGroups().keySet());
            for (int l = this.typeIndexOffset; l < k && l < list.size(); ++l)
            {
                final int i1 = l - this.typeIndexOffset;
                final int j1 = i + i1 % 10 * CUTTER_RECIPE_W;
                final int k1 = j + i1 / 10 * CUTTER_RECIPE_H + 2;
                if (x >= j1 && x < j1 + CUTTER_RECIPE_W && y >= k1 && y < k1 + CUTTER_RECIPE_H)
                {
                    graphics.setTooltipForNextFrame(this.font,
                      Component.translatable("cuttergroup." + list.get(l).getNamespace() + "." + list.get(l).getPath()), x, y);
                }
            }
        }

        if (this.menu.getCurrentGroup() != null)
        {
            final List<ItemStack> list = ModBlocks.getInstance().getOrComputeItemGroups().get(this.menu.getCurrentGroup());
            final int i = this.leftPos + CUTTER_RECIPE_X;
            final int j = this.topPos + CUTTER_RECIPE_Y + CUTTER_RECIPE_SPACING;
            final int k = this.recipeIndexOffset + 10;

            for (int l = this.recipeIndexOffset; l < k && l < list.size(); ++l)
            {
                final int i1 = l - this.recipeIndexOffset;
                final int j1 = i + i1 % 10 * CUTTER_RECIPE_W;
                final int k1 = j + i1 / 10 * CUTTER_RECIPE_H + 2;
                if (x >= j1 && x < j1 + CUTTER_RECIPE_W && y >= k1 && y < k1 + CUTTER_RECIPE_H)
                {
                    final ItemStack stack;
                    if (this.menu.outputInventorySlot.hasItem())
                    {
                        final ItemStack input = list.get(l).copy();
                        texturizeVariantUsingCurrentInput(input);
                        stack = input;
                    }
                    else
                    {
                        stack = list.get(l);
                    }
                    graphics.setTooltipForNextFrame(this.font, stack, x, y);
                }
            }
        }
    }

    @Override
    protected void extractLabels(final GuiGraphicsExtractor graphics, final int xm, final int ym)
    {
        graphics.text(this.font, Component.translatable(Constants.MOD_ID + ".group"), 7, 22, 4210752, false);
        graphics.text(this.font, Component.translatable(Constants.MOD_ID + ".variant"), 7, 45, 4210752, false);
        graphics.text(this.font, this.title, this.titleLabelX + 70, this.titleLabelY, 4210752, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX + 32, this.inventoryLabelY + 36, 4210752, false);
    }

    private void drawRecipeButtonBackgrounds(
      final GuiGraphicsExtractor graphics, final int x, final int y, final int recipeAreaLeft, final int recipeAreaTop)
    {
        final List<Identifier> groups = new ArrayList<>(ModBlocks.getInstance().getOrComputeItemGroups().keySet());
        for (int i = this.typeIndexOffset; i < this.typeIndexOffset + 10 && i < groups.size(); ++i)
        {
            final int drawIndex = i - this.typeIndexOffset;
            final int drawLeft = recipeAreaLeft + drawIndex % 10 * CUTTER_RECIPE_W;
            final int rowIndex = drawIndex / 10;
            final int drawTop = recipeAreaTop + rowIndex * CUTTER_RECIPE_H + 2;
            int zOffset = CUTTER_RECIPE_U_NORMAL;
            if (this.menu.getCurrentGroup() != null && i == groups.indexOf(this.menu.getCurrentGroup()))
            {
                zOffset = CUTTER_RECIPE_U_SELECTED;
            }
            else if (x >= drawLeft && y >= drawTop && x < drawLeft + CUTTER_RECIPE_W && y < drawTop + CUTTER_RECIPE_H)
            {
                zOffset = CUTTER_RECIPE_U_HOVERED;
            }

            blit(graphics, BACKGROUND_TEXTURE1, drawLeft, drawTop - 1, zOffset, CUTTER_RECIPE_V, CUTTER_RECIPE_W, CUTTER_RECIPE_H);
        }

        if (this.menu.getCurrentGroup() != null)
        {
            final List<ItemStack> list = ModBlocks.getInstance().getOrComputeItemGroups().get(this.menu.getCurrentGroup());
            for (int i = this.recipeIndexOffset; i < recipeIndexOffset + 10 && i < list.size(); ++i)
            {
                final int drawIndex = i - this.recipeIndexOffset;
                final int drawLeft = recipeAreaLeft + drawIndex % 10 * CUTTER_RECIPE_W;
                final int rowIndex = drawIndex / 10;
                final int drawTop = recipeAreaTop + CUTTER_RECIPE_SPACING + rowIndex * CUTTER_RECIPE_H + 2;
                int zOffset = CUTTER_RECIPE_U_NORMAL;
                if (this.menu.getCurrentVariant() != null && i == list.indexOf(this.menu.getCurrentVariant()))
                {
                    zOffset = CUTTER_RECIPE_U_SELECTED;
                }
                else if (x >= drawLeft && y >= drawTop && x < drawLeft + CUTTER_RECIPE_W && y < drawTop + CUTTER_RECIPE_H)
                {
                    zOffset = CUTTER_RECIPE_U_HOVERED;
                }

                blit(graphics, BACKGROUND_TEXTURE1, drawLeft, drawTop - 1, zOffset, CUTTER_RECIPE_V, CUTTER_RECIPE_W, CUTTER_RECIPE_H);
            }
        }
    }

    private void drawSlotBackgrounds(final GuiGraphicsExtractor graphics)
    {
        if (this.menu.getCurrentVariant() != null
              && this.menu.getCurrentVariant().getItem() instanceof final BlockItem item
              && item.getBlock() instanceof final IMateriallyTexturedBlock block)
        {
            final int numComponents = block.getComponents().size();
            final List<Identifier> input = new ArrayList<>();
            if (item instanceof final IDoItem doItem)
            {
                input.addAll(doItem.getInputIds());
            }

            for (int i = 0; i < 2; i++)
            {
                final int drawLeft = CUTTER_INPUT_X - 1 + this.leftPos;
                final int drawTop = this.topPos + CUTTER_INPUT_Y - 1 + i * CUTTER_INPUT_SPACING;
                if (i < input.size())
                {
                    graphics.text(this.font,
                      Component.translatable(input.get(i).getNamespace() + ".desc." + input.get(i).getPath(),
                        Component.translatable(Constants.MOD_ID + ".desc.material", "")),
                      drawLeft - 88, drawTop + 5, 4210752, false);
                }
                blit(graphics, BACKGROUND_TEXTURE1, drawLeft, drawTop,
                  CUTTER_SLOT_U + (i >= numComponents ? CUTTER_SLOT_W : 0), CUTTER_SLOT_V, CUTTER_SLOT_W, CUTTER_SLOT_H);
            }
        }
    }

    private void drawRecipesItems(final GuiGraphicsExtractor graphics, final int left, final int top)
    {
        final List<Identifier> typeList = new ArrayList<>(ModBlocks.getInstance().getOrComputeItemGroups().keySet());
        for (int i = this.typeIndexOffset; i < this.typeIndexOffset + 10 && i < typeList.size(); ++i)
        {
            final int j = i - this.typeIndexOffset;
            final int k = left + j % 10 * CUTTER_RECIPE_W;
            final int l = j / 10;
            final int i1 = top + l * CUTTER_RECIPE_H + 2;

            final Identifier type = typeList.get(i);
            if (ModBlocks.getInstance().getOrComputeItemGroups().get(type).isEmpty())
            {
                DomumOrnamentum.LOGGER.error("Empty Item Category: {}", type);
                continue;
            }

            graphics.item(ModBlocks.getInstance().getOrComputeItemGroups().get(typeList.get(i)).get(0), k, i1);
        }

        if (this.menu.getCurrentGroup() != null)
        {
            final List<ItemStack> list = ModBlocks.getInstance().getOrComputeItemGroups().get(this.menu.getCurrentGroup());
            for (int i = this.recipeIndexOffset; i < this.recipeIndexOffset + 10 && i < list.size(); ++i)
            {
                final int j = i - this.recipeIndexOffset;
                final int k = left + j % 10 * CUTTER_RECIPE_W;
                final int l = j / 10;
                final int i1 = top + CUTTER_RECIPE_SPACING + l * CUTTER_RECIPE_H + 2;

                if (this.menu.outputInventorySlot.hasItem())
                {
                    final ItemStack input = list.get(i).copy();
                    texturizeVariantUsingCurrentInput(input);
                    graphics.item(input, k, i1);
                }
                else
                {
                    graphics.item(list.get(i), k, i1);
                }
            }
        }
    }

    private void texturizeVariantUsingCurrentInput(final ItemStack variantItemStack)
    {
        if (!(variantItemStack.getItem() instanceof final BlockItem bi && bi.getBlock() instanceof final IMateriallyTexturedBlock block))
        {
            return;
        }

        final MaterialTextureData.Builder textureData = MaterialTextureData.builder();
        int i = 0;
        for (final IMateriallyTexturedBlockComponent component : block.getComponents())
        {
            if (this.menu.inputInventory.getItem(i).getItem() instanceof final BlockItem blockItem)
            {
                textureData.setComponent(component.getId(), blockItem.getBlock());
            }
            i++;
        }
        textureData.writeToItemStack(variantItemStack);
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick)
    {
        final double mouseX = event.x();
        final double mouseY = event.y();

        this.clickedOnRecipeScroll = false;
        this.clickedOnTypeScroll = false;

        if (this.menu.getCurrentGroup() != null)
        {
            int leftOffset = this.leftPos + CUTTER_RECIPE_X + 1;
            final int topOffset = this.topPos + CUTTER_RECIPE_Y + CUTTER_RECIPE_SPACING;
            final int scrollOffset = this.recipeIndexOffset + 10;

            for (int index = this.recipeIndexOffset; index < scrollOffset; ++index)
            {
                final int rowIndex = index - this.recipeIndexOffset;
                final double mouseXOffset = mouseX - (double) (leftOffset + rowIndex % 10 * CUTTER_RECIPE_W);
                final double mouseYOffset = mouseY - (double) (topOffset + rowIndex / 10 * CUTTER_RECIPE_H);
                if (mouseXOffset >= 0.0D && mouseYOffset >= 0.0D && mouseXOffset < CUTTER_RECIPE_W && mouseYOffset < CUTTER_RECIPE_H
                      && (this.menu).clickMenuButton(Objects.requireNonNull(Objects.requireNonNull(this.minecraft).player),
                            index + ModBlocks.getInstance().itemGroups.size()))
                {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    Objects.requireNonNull(this.minecraft.gameMode)
                      .handleInventoryButtonClick(this.menu.containerId, index + ModBlocks.getInstance().itemGroups.size());
                    variantIndexCache = index + ModBlocks.getInstance().itemGroups.size();
                    return true;
                }
            }

            leftOffset = this.leftPos + CUTTER_SLIDER_X;
            if (mouseX >= (double) leftOffset && mouseX < (double) (leftOffset + CUTTER_SLIDER_W)
                  && mouseY >= (double) topOffset && mouseY < (double) (topOffset + CUTTER_RECIPE_H))
            {
                this.clickedOnRecipeScroll = true;
            }
        }

        if (!clickedOnRecipeScroll)
        {
            int leftOffset = this.leftPos + CUTTER_RECIPE_X + 1;
            final int topOffset = this.topPos + CUTTER_RECIPE_Y;
            final int scrollOffset = this.typeIndexOffset + 10;

            for (int index = this.typeIndexOffset; index < scrollOffset; ++index)
            {
                final int rowIndex = index - this.typeIndexOffset;
                final double mouseXOffset = mouseX - (double) (leftOffset + rowIndex % 10 * CUTTER_RECIPE_W);
                final double mouseYOffset = mouseY - (double) (topOffset + rowIndex / 10 * CUTTER_RECIPE_H);
                if (mouseXOffset >= 0.0D && mouseYOffset >= 0.0D && mouseXOffset < CUTTER_RECIPE_W && mouseYOffset < CUTTER_RECIPE_H
                      && (this.menu).clickMenuButton(Objects.requireNonNull(Objects.requireNonNull(this.minecraft).player), index))
                {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    Objects.requireNonNull(this.minecraft.gameMode).handleInventoryButtonClick(this.menu.containerId, index);
                    groupIndexCache = index;
                    recipeIndexOffset = 0;
                    recipeSliderProgress = 0;
                    return true;
                }
            }

            leftOffset = this.leftPos + CUTTER_SLIDER_X;
            if (mouseX >= (double) leftOffset && mouseX < (double) (leftOffset + CUTTER_SLIDER_W)
                  && mouseY >= (double) topOffset && mouseY < (double) (topOffset + CUTTER_RECIPE_H))
            {
                this.clickedOnTypeScroll = true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(final MouseButtonEvent event, final double dragX, final double dragY)
    {
        final double mouseY = event.y();

        if (this.clickedOnRecipeScroll && this.canScrollRecipes())
        {
            final int i = this.topPos + CUTTER_RECIPE_Y + CUTTER_RECIPE_SPACING;
            final int j = i + 10;
            this.recipeSliderProgress = ((float) mouseY - (float) i - 7.5F) / ((float) (j - i) - 5.0F);
            this.recipeSliderProgress = Mth.clamp(this.recipeSliderProgress, 0.0F, 1.0F);
            this.recipeIndexOffset = (int) ((double) (this.recipeSliderProgress * (float) this.getHiddenRecipeRows()) + 0.5D) * 10;
            return true;
        }
        else if (this.clickedOnTypeScroll && this.canScrollTypes())
        {
            final int i = this.topPos + CUTTER_RECIPE_Y;
            final int j = i + 10;
            this.typeSliderProgress = ((float) mouseY - (float) i - 7.5F) / ((float) (j - i) - 5.0F);
            this.typeSliderProgress = Mth.clamp(this.typeSliderProgress, 0.0F, 1.0F);
            this.typeIndexOffset = (int) ((double) (this.typeSliderProgress * (float) this.getHiddenTypeRows()) + 0.5D) * 10;
            return true;
        }
        else
        {
            return super.mouseDragged(event, dragX, dragY);
        }
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double deltaX, final double deltaY)
    {
        boolean onlyTypes = false;
        if (mouseX >= this.leftPos + 55 && mouseY >= this.topPos + 15 && mouseX < this.leftPos + CUTTER_SLIDER_X && mouseY < this.topPos + 35)
        {
            onlyTypes = true;
        }

        boolean onlyRecipes = false;
        if (mouseX >= this.leftPos + 55 && mouseY >= this.topPos + 40 && mouseX < this.leftPos + CUTTER_SLIDER_X && mouseY < this.topPos + 60)
        {
            onlyRecipes = true;
        }

        if (this.canScrollRecipes() && !onlyTypes)
        {
            final int i = this.getHiddenRecipeRows();
            this.recipeSliderProgress = (float) ((double) this.recipeSliderProgress - deltaY / (double) i);
            this.recipeSliderProgress = Mth.clamp(this.recipeSliderProgress, 0.0F, 1.0F);
            this.recipeIndexOffset = (int) ((double) (this.recipeSliderProgress * (float) i) + 0.5D) * 10;
        }

        if (this.canScrollTypes() && !onlyRecipes)
        {
            final int i = this.getHiddenTypeRows();
            this.typeSliderProgress = (float) ((double) this.typeSliderProgress - deltaY / (double) i);
            this.typeSliderProgress = Mth.clamp(this.typeSliderProgress, 0.0F, 1.0F);
            this.typeIndexOffset = (int) ((double) (this.typeSliderProgress * (float) i) + 0.5D) * 10;
        }

        return true;
    }

    private boolean canScrollRecipes()
    {
        return this.menu.getCurrentGroup() != null
                 && ModBlocks.getInstance().getOrComputeItemGroups().get(this.menu.getCurrentGroup()).size() > 10;
    }

    private boolean canScrollTypes()
    {
        return ModBlocks.getInstance().getOrComputeItemGroups().size() > 10;
    }

    protected int getHiddenRecipeRows()
    {
        return this.menu.getCurrentGroup() == null
                 ? 0
                 : (ModBlocks.getInstance().getOrComputeItemGroups().get(this.menu.getCurrentGroup()).size() + 10 - 1) / 10 - 1;
    }

    protected int getHiddenTypeRows()
    {
        return (ModBlocks.getInstance().getOrComputeItemGroups().size() + 10 - 1) / 10 - 1;
    }
}
