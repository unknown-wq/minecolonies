package com.minecolonies.core.client.gui.containers;

import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.inventory.container.ContainerCitizenInventory;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Inventory window of a citizen.
 * <p>
 * PORT-26.2: {@code GuiGraphicsExtractor} became {@code GuiGraphicsExtractor} and screens extract instead of render;
 * {@code imageWidth}/{@code imageHeight} are final and go through the super constructor; and the immediate mode
 * {@code InventoryScreen.renderEntityInInventory} was replaced by the picture-in-picture
 * {@link GuiGraphicsExtractor#entity} call.
 */
public class WindowCitizenInventory extends AbstractContainerScreen<ContainerCitizenInventory>
{
    /**
     * Texture res loc.
     */
    private static final Identifier TEXT = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/citizen_container.png");

    /**
     * Offset inside the texture to use.
     */
    private static final int TEXTURE_OFFSET = 130;

    /**
     * Offset of each slot.
     */
    private static final int SLOT_OFFSET = 18;

    /**
     * Size of the custom texture.
     */
    private static final int TEXTURE_SIZE = 350;

    /**
     * Offet of the screen for the texture.
     */
    private static final int TEXTURE_HEIGHT = 96;

    /**
     * General y offset.
     */
    private static final int Y_OFFSET = 114;

    /**
     * Amount of slots each row.
     */
    private static final int SLOTS_EACH_ROW = 9;

    /**
     * Current active citizen inventory window
     */
    public static WindowCitizenInventory activeCitizenInventory = null;

    /**
     * Citizen of this UI
     */
    private ICitizen citizenData;

    /**
     * window height is calculated with these values; the more rows, the heigher
     */
    private final int inventoryRows;

    public WindowCitizenInventory(final ContainerCitizenInventory container, final Inventory playerInventory, final Component iTextComponent)
    {
        super(container, playerInventory, iTextComponent, 245,
          Y_OFFSET + Math.min(SLOTS_EACH_ROW, (container.getItems().size() - 36) / 9) * SLOT_OFFSET);
        this.inventoryRows = (container.getItems().size() - 36) / 9;
        activeCitizenInventory = this;
        citizenData = container.getCitizenData();
    }

    /**
     * Draw the foreground layer for the GuiContainer (everything in front of the items)
     */
    @Override
    protected void extractLabels(@NotNull final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY)
    {
        graphics.text(this.font, this.menu.getDisplayName(), 80, 9, 0xFF404040, false);
        graphics.text(this.font, this.playerInventoryTitle.getString(), 8, 25 + this.inventoryRows * SLOT_OFFSET, 0xFF404040, false);
    }

    /**
     * Draws the background layer of this container (behind the items).
     */
    @Override
    public void extractBackground(@NotNull final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTicks)
    {
        final int i = (this.width - this.imageWidth) / 2;
        final int j = (this.height - this.imageHeight) / 2;

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXT, i, j, 0, 0, this.imageWidth, 10 + this.inventoryRows * SLOT_OFFSET + 12, TEXTURE_SIZE, TEXTURE_SIZE);

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXT, i, j + 10 + this.inventoryRows * SLOT_OFFSET + 12, 0, TEXTURE_OFFSET,
          this.imageWidth, TEXTURE_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXT, i + 172, j + 22, 0, 227, 49, 72, TEXTURE_SIZE, TEXTURE_SIZE);

        for (int index = 0; index < 4; index++)
        {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXT, i + 222, j + 22 + index * 18, 0, 300, 18, 18, TEXTURE_SIZE, TEXTURE_SIZE);
        }

        extractEntityInInventoryFollowsMouse(graphics, i + 197, j + 88, 30,
          (float) (i + 51) - mouseX, (float) (j + 75 - 50) - mouseY, this.menu.getEntity());
    }

    public static void extractEntityInInventoryFollowsMouse(
      final GuiGraphicsExtractor graphics,
      final int x,
      final int y,
      final int scale,
      final float mouseX,
      final float mouseY,
      final Optional<? extends Entity> optionalEntity)
    {
        optionalEntity.ifPresent(entity -> {
            final float relativeMouseX = (float) Math.atan(mouseX / 40.0F);
            final float relativeMouseY = (float) Math.atan(mouseY / 40.0F);
            extractEntityInInventoryFollowsAngle(graphics, x, y, scale, relativeMouseX, relativeMouseY, (LivingEntity) entity);
        });
    }

    public static void extractEntityInInventoryFollowsAngle(
      final GuiGraphicsExtractor graphics,
      final int x,
      final int y,
      final int scale,
      final float angleXComponent,
      final float angleYComponent,
      final LivingEntity entity)
    {
        final float f = angleXComponent;
        final float f1 = angleYComponent;
        final Quaternionf quaternionf = new Quaternionf().rotateZ((float) Math.PI);
        final Quaternionf quaternionf1 = new Quaternionf().rotateX(f1 * 20.0F * ((float) Math.PI / 180F));
        quaternionf.mul(quaternionf1);
        final float f2 = entity.yBodyRot;
        final float f3 = entity.getYRot();
        final float f4 = entity.getXRot();
        final float f5 = entity.yHeadRotO;
        final float f6 = entity.yHeadRot;
        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-f1 * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        extractEntityInInventory(graphics, x, y, scale, quaternionf, quaternionf1, entity);
        entity.yBodyRot = f2;
        entity.setYRot(f3);
        entity.setXRot(f4);
        entity.yHeadRotO = f5;
        entity.yHeadRot = f6;
    }

    /**
     * PORT-26.2: the old body pushed a pose, called {@code EntityRenderDispatcher#render} and flushed the buffer
     * source; none of that exists any more. 26.2 extracts the render state once and hands it to
     * {@link GuiGraphicsExtractor#entity}, which takes a rectangle instead of a centre point, so the previous
     * (x, y, scale) triple is expanded into a box around that point.
     */
    public static void extractEntityInInventory(
      final GuiGraphicsExtractor graphics,
      final int x,
      final int y,
      final int scale,
      final Quaternionf rotation,
      final @Nullable Quaternionf overrideCameraAngle,
      final LivingEntity entity)
    {
        final EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        final EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
        final EntityRenderState renderState = renderer.createRenderState(entity, 1.0F);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;

        if (overrideCameraAngle != null)
        {
            overrideCameraAngle.conjugate();
        }

        final Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F, 0.0F);
        graphics.entity(renderState, scale, translation, rotation, overrideCameraAngle, x - scale, y - scale * 2, x + scale, y + scale);
    }

    @Override
    public void onClose()
    {
        activeCitizenInventory = null;
        super.onClose();
    }

    /**
     * Get the citizen for this UI
     *
     * @return the citizen data.
     */
    public ICitizen getCitizenData()
    {
        return citizenData;
    }
}
