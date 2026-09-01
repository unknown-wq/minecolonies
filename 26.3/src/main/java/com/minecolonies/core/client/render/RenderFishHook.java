package com.minecolonies.core.client.render;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.other.NewBobberEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Determines how the fish hook is rendered.
 * <p>
 * PORT-26.2: rewritten against the reworked vanilla {@code FishingHookRenderer}: {@code render(...)} became
 * {@code submit(...)}, geometry goes through {@code submitCustomGeometry}, the camera orientation comes off the
 * {@link CameraRenderState}, and the line origin is computed while extracting because the owner citizen is not
 * reachable at submit time. {@link #affectedByCulling} is false exactly like vanilla's — without it the bobber
 * disappears as soon as its (tiny) bounding box leaves the frustum.
 */
@Environment(EnvType.CLIENT)
public class RenderFishHook extends EntityRenderer<NewBobberEntity, RenderFishHook.BobberRenderState>
{
    /**
     * The resource location containing the particle textures (Spawned by the fishHook).
     */
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/fishing/fishing_hook.png");

    /**
     * The render type of the hook.
     */
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutCull(TEXTURE);

    /**
     * Required constructor, sets the RenderManager.
     *
     * @param context context that we use.
     */
    public RenderFishHook(final EntityRendererProvider.Context context)
    {
        super(context);
    }

    @NotNull
    @Override
    public BobberRenderState createRenderState()
    {
        return new BobberRenderState();
    }

    @Override
    public void extractRenderState(final NewBobberEntity entity, final BobberRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);

        state.visible = false;
        state.lineOriginOffset = Vec3.ZERO;

        if (!(entity.getOwner() instanceof final AbstractEntityCitizen citizen))
        {
            return;
        }

        state.visible = true;

        int invert = citizen.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        final ItemStack itemstack = citizen.getMainHandItem();
        if (!itemstack.is(Items.FISHING_ROD))
        {
            invert = -invert;
        }

        final float bodyRot = Mth.lerp(partialTicks, citizen.yBodyRotO, citizen.yBodyRot) * ((float) Math.PI / 180F);
        final double sin = Mth.sin(bodyRot);
        final double cos = Mth.cos(bodyRot);
        final double rightOffset = invert * 0.35D;

        final double handX = Mth.lerp(partialTicks, citizen.xo, citizen.getX()) - cos * rightOffset - sin * 0.8D;
        final double handY = citizen.yo + citizen.getEyeHeight() + (citizen.getY() - citizen.yo) * partialTicks - 0.45D
                               + (citizen.isCrouching() ? -0.1875F : 0.0F);
        final double handZ = Mth.lerp(partialTicks, citizen.zo, citizen.getZ()) - sin * rightOffset + cos * 0.8D;

        final double hookX = Mth.lerp((double) partialTicks, entity.xo, entity.getX());
        final double hookY = Mth.lerp((double) partialTicks, entity.yo, entity.getY()) + 0.25D;
        final double hookZ = Mth.lerp((double) partialTicks, entity.zo, entity.getZ());

        state.lineOriginOffset = new Vec3(handX - hookX, handY - hookY, handZ - hookZ);
    }

    @Override
    public void submit(
      final BobberRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (!state.visible)
        {
            return;
        }

        poseStack.pushPose();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.rotate(camera.orientation);
        poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
        submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, state.lightCoords, 0.0F, 0, 0, 1);
            vertex(buffer, pose, state.lightCoords, 1.0F, 0, 1, 1);
            vertex(buffer, pose, state.lightCoords, 1.0F, 1, 1, 0);
            vertex(buffer, pose, state.lightCoords, 0.0F, 1, 0, 0);
        });
        poseStack.popPose();

        final float xa = (float) state.lineOriginOffset.x;
        final float ya = (float) state.lineOriginOffset.y;
        final float za = (float) state.lineOriginOffset.z;
        final float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            for (int i = 0; i < 16; i++)
            {
                final float a0 = fraction(i, 16);
                final float a1 = fraction(i + 1, 16);
                stringVertex(xa, ya, za, buffer, pose, a0, a1, width);
                stringVertex(xa, ya, za, buffer, pose, a1, a0, width);
            }
        });

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    @Override
    protected boolean affectedByCulling(final NewBobberEntity entity)
    {
        return false;
    }

    private static float fraction(final int first, final int second)
    {
        return (float) first / (float) second;
    }

    private static void vertex(
      final VertexConsumer builder, final PoseStack.Pose pose, final int lightCoords, final float x, final int y, final int u, final int v)
    {
        builder.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
          .setColor(-1)
          .setUv(u, v)
          .setOverlay(OverlayTexture.NO_OVERLAY)
          .setLight(lightCoords)
          .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void stringVertex(
      final float xa,
      final float ya,
      final float za,
      final VertexConsumer stringBuffer,
      final PoseStack.Pose stringPose,
      final float aa,
      final float nexta,
      final float width)
    {
        float x = xa * aa;
        float y = ya * (aa * aa + aa) * 0.5F + 0.25F;
        float z = za * aa;
        float nx = xa * nexta - x;
        float ny = ya * (nexta * nexta + nexta) * 0.5F + 0.25F - y;
        float nz = za * nexta - z;
        final float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        stringBuffer.addVertex(stringPose, x, y, z).setColor(-16777216).setNormal(stringPose, nx, ny, nz).setLineWidth(width);
    }

    /**
     * Render state of the bobber.
     */
    @Environment(EnvType.CLIENT)
    public static class BobberRenderState extends EntityRenderState
    {
        /**
         * Whether the bobber has a citizen owner and should be drawn at all.
         */
        public boolean visible = false;

        /**
         * Offset from the bobber to the hand of its owner.
         */
        public Vec3 lineOriginOffset = Vec3.ZERO;
    }
}
