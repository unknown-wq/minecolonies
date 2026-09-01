package com.minecolonies.core.client.render;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.client.render.modeltype.CitizenRenderState;
import com.minecolonies.api.client.render.modeltype.IModelType;
import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.apiimp.initializer.ModModelTypeInitializer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Renderer for the citizens.
 * <p>
 * PORT-26.2: renderers are render-state driven. {@code render(...)} became {@code submit(...)} and no longer sees the
 * entity, so the per-citizen model choice, texture, arm poses and display armour all move into
 * {@link #extractRenderState}. {@code RenderSystem.setShaderColor} is gone, so the ghost preview is now a model tint on
 * a translucent render type.
 */
public class RenderBipedCitizen extends MobRenderer<AbstractEntityCitizen, CitizenRenderState, CitizenModel<AbstractEntityCitizen>>
{
    private static final double  SHADOW_SIZE   = 0.5F;
    public static        boolean isItGhostTime = false;

    /**
     * Alpha the ghost preview is drawn with (0.3 of 255).
     */
    private static final int GHOST_ALPHA = (int) (0.3F * 255);

    /**
     * Fallback model of this renderer; used whenever the model registry has nothing for a citizen.
     */
    private final CitizenModel<AbstractEntityCitizen> defaultModel;

    /**
     * Renders model, see {@link MobRenderer}.
     *
     * @param context the context for this Renderer.
     */
    public RenderBipedCitizen(final EntityRendererProvider.Context context)
    {
        super(context, new CitizenModel<>(context.bakeLayer(ModelLayers.PLAYER)), (float) SHADOW_SIZE);
        this.defaultModel = this.model;
        this.addLayer(new CitizenArmorLayer(this, context));
        this.addLayer(new ItemInHandLayer<>(this));
        ModModelTypeInitializer.init(context);
    }

    @NotNull
    @Override
    public CitizenRenderState createRenderState()
    {
        return new CitizenRenderState();
    }

    @Override
    public void extractRenderState(final AbstractEntityCitizen citizen, final CitizenRenderState state, final float partialTicks)
    {
        super.extractRenderState(citizen, state, partialTicks);
        HumanoidMobRenderer.extractHumanoidRenderState(citizen, state, partialTicks, this.itemModelResolver);

        state.leftArmPose = RenderUtils.getArmPose(citizen, InteractionHand.OFF_HAND);
        state.rightArmPose = RenderUtils.getArmPose(citizen, InteractionHand.MAIN_HAND);

        final ICitizenDataView view = citizen.getCitizenDataView();

        state.renderMetadata = citizen.getRenderMetadata();
        state.female = citizen.isFemale();
        state.civilianId = citizen.getCivilianID();
        state.displayHat = CitizenModel.shouldDisplayHat(citizen);
        state.riding = citizen.getVehicle() != null;
        state.ghost = isItGhostTime;

        final IModelTypeRegistry registry = IModelTypeRegistry.getInstance();
        final IModelType modelType = registry.getModelType(citizen.getModelType());
        CitizenModel<AbstractEntityCitizen> chosen = citizen.isFemale() ? modelType.getFemaleModel() : modelType.getMaleModel();

        state.hasCustomTexture = view != null && view.getCustomTexture() != null;
        if (state.hasCustomTexture)
        {
            final IModelType customType = registry.getModelType(ModModelTypes.CUSTOM_ID);
            if (customType != null && customType.getMaleModel() != null)
            {
                chosen = customType.getMaleModel();
            }
            state.texture = view.getCustomTexture();
        }
        else
        {
            state.texture = citizen.getTexture();
        }
        state.model = chosen != null ? chosen : this.defaultModel;

        final UUID textureUUID = view == null ? null : view.getCustomTextureUUID();
        state.customTextureProfile = textureUUID == null ? null : ResolvableProfile.createUnresolved(textureUUID);
        state.statusIcon = view != null && view.hasVisibleStatus() ? view.getStatusIcon() : null;

        if (view != null)
        {
            state.headEquipment = displayArmorOr(view, EquipmentSlot.HEAD, state.headEquipment);
            state.chestEquipment = displayArmorOr(view, EquipmentSlot.CHEST, state.chestEquipment);
            state.legsEquipment = displayArmorOr(view, EquipmentSlot.LEGS, state.legsEquipment);
            state.feetEquipment = displayArmorOr(view, EquipmentSlot.FEET, state.feetEquipment);
        }
    }

    /**
     * The citizen data view can override what a citizen appears to wear; that override used to be applied inside the
     * armour layer, which no longer has access to anything but the render state.
     */
    private static ItemStack displayArmorOr(final ICitizenDataView view, final EquipmentSlot slot, final ItemStack fallback)
    {
        final ItemStack display = view.getDisplayArmor(slot);
        return display == null || display.isEmpty() ? fallback : display.copy();
    }

    @Override
    public void submit(
      final CitizenRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        this.model = state.model != null ? castModel(state.model) : this.defaultModel;
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @SuppressWarnings("unchecked")
    private static CitizenModel<AbstractEntityCitizen> castModel(final CitizenModel<?> model)
    {
        return (CitizenModel<AbstractEntityCitizen>) model;
    }

    /**
     * PORT-NOTE(26.2): this is what stopped children from being child sized, and it is a port loss rather than an
     * upstream defect.
     * <p>
     * 1.21.1 wrote {@code model.young = citizen.isBaby()} in {@code render}, and {@code AgeableListModel#young} applied
     * vanilla's baby transform to whatever model the citizen had. 26.2 removed that field: vanilla now bakes baby
     * geometry as a separate model layer through {@link net.minecraft.client.model.HumanoidModel#BABY_TRANSFORMER}, and
     * every renderer that has babies picks a different baked model for them. The port dropped the line with nothing in
     * its place, and MineColonies' own child model is a full sized humanoid mesh -- it never carried the shrink itself,
     * it relied on {@code young}. So every child has been rendering at adult height while its hitbox, which comes from
     * {@code getAgeScale()} by way of {@code LivingEntity#getDefaultDimensions}, was correctly two thirds of one. Only
     * the ones that also had a worker's model looked outright wrong; a child model at adult size still reads as a
     * child until you stand next to one.
     * <p>
     * Scaling the pose by the same {@code ageScale} the hitbox already uses fixes both halves at once and makes them
     * agree by construction. It is deliberately a uniform scale and not vanilla's baby proportions: vanilla's
     * transform enlarges the head, which would put the model back out of step with the box around it.
     */
    @Override
    protected void scale(final CitizenRenderState state, @NotNull final PoseStack poseStack)
    {
        if (state.ageScale != 1.0F)
        {
            poseStack.scale(state.ageScale, state.ageScale, state.ageScale);
        }
    }

    @Override
    protected int getModelTint(final CitizenRenderState state)
    {
        return state.ghost ? ARGB.color(GHOST_ALPHA, 255, 255, 255) : -1;
    }

    @Override
    protected @Nullable RenderType getRenderType(
      final CitizenRenderState state, final boolean isBodyVisible, final boolean forceTransparent, final boolean appearGlowing)
    {
        if (state.ghost && isBodyVisible && !forceTransparent)
        {
            return net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(getTextureLocation(state));
        }
        return super.getRenderType(state, isBodyVisible, forceTransparent, appearGlowing);
    }

    @Override
    protected void submitNameDisplay(
      final CitizenRenderState state,
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final CameraRenderState camera)
    {
        super.submitNameDisplay(state, poseStack, submitNodeCollector, camera);

        if (state.statusIcon == null || state.nameTagAttachment == null || state.distanceToCameraSq > 4096.0D)
        {
            return;
        }

        final Vec3 attachment = state.nameTagAttachment;
        poseStack.pushPose();
        poseStack.translate(attachment.x, attachment.y + 0.9, attachment.z);
        poseStack.rotate(camera.orientation);
        poseStack.scale(0.025F, -0.025F, 0.025F);

        submitNodeCollector.submitCustomGeometry(poseStack,
          com.minecolonies.core.client.render.worldevent.RenderTypes.worldEntityIcon(state.statusIcon),
          (pose, buffer) ->
          {
              buffer.addVertex(pose, -5, 0, 0).setColor(-1).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(pose, 0, 0, 1);
              buffer.addVertex(pose, -5, 10, 0).setColor(-1).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(pose, 0, 0, 1);
              buffer.addVertex(pose, 5, 10, 0).setColor(-1).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(pose, 0, 0, 1);
              buffer.addVertex(pose, 5, 0, 0).setColor(-1).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(pose, 0, 0, 1);
          });

        poseStack.popPose();
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(final CitizenRenderState state)
    {
        return state.texture != null ? state.texture : MissingTextureAtlasSprite.getLocation();
    }
}
