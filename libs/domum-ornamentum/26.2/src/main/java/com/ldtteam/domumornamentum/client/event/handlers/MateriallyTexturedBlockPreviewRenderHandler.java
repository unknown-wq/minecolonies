package com.ldtteam.domumornamentum.client.event.handlers;

/**
 * TODO(port-26.2): DISABLED — driven by {@code RenderLevelStageEvent}, which is NeoForge-only, and it renders
 * through {@link com.ldtteam.domumornamentum.client.render.ModelGhostRenderer}, which is itself disabled.
 *
 * <p>Consequence in game: no translucent preview of the block about to be placed. Purely cosmetic; placement,
 * the vanilla block outline and everything server-side are unaffected.
 *
 * <p>How to fix: register
 * {@code net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AfterTranslucentTerrain} (or
 * {@code LevelExtractionEvents.EndExtraction}) from {@code fabric-rendering-v1} out of
 * {@code ClientRegistrations#register()} and call the revived {@code ModelGhostRenderer} from there. The
 * ray-trace and held-stack logic below still compiles as-is on 26.2 - only the event and the actual drawing
 * need replacing.
 *
 * <p>Original NeoForge implementation:
 * <pre>
 * &#64;EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
 * public class MateriallyTexturedBlockPreviewRenderHandler {
 *
 *     &#64;SubscribeEvent
 *     public static void onRenderLevelStage(RenderLevelStageEvent event) {
 *         if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
 *             final PoseStack poseStack = event.getPoseStack();
 *             renderMateriallyTexturedBlockPreview(poseStack);
 *         }
 *     }
 *
 *     public static void renderMateriallyTexturedBlockPreview(final PoseStack poseStack) {
 *         final HitResult rayTraceResult = Minecraft.getInstance().hitResult;
 *         if (!(rayTraceResult instanceof final BlockHitResult blockRayTraceResult)
 *               || blockRayTraceResult.getType() == HitResult.Type.MISS)
 *             return;
 *
 *         final Player playerEntity = Minecraft.getInstance().player;
 *         if (playerEntity == null || playerEntity.isSpectator())
 *             return;
 *
 *         final ItemStack heldStack = ItemStackUtils.getMateriallyTexturedItemStackFromPlayer(playerEntity);
 *         if (heldStack.isEmpty())
 *             return;
 *
 *         Vec3 targetedRenderPos = Vec3.atLowerCornerOf(
 *             blockRayTraceResult.getBlockPos().offset(blockRayTraceResult.getDirection().getNormal()));
 *         renderGhost(poseStack, heldStack, targetedRenderPos, blockRayTraceResult, Minecraft.getInstance().level);
 *     }
 *
 *     private static void renderGhost(final PoseStack poseStack, final ItemStack heldStack,
 *                                     final Vec3 targetedRenderPos, BlockHitResult blockRayTraceResult,
 *                                     ClientLevel level) {
 *         ModelGhostRenderer.getInstance().renderGhost(
 *             poseStack, heldStack, targetedRenderPos, blockRayTraceResult, level, false);
 *     }
 * }
 * </pre>
 */
public final class MateriallyTexturedBlockPreviewRenderHandler
{
    private MateriallyTexturedBlockPreviewRenderHandler()
    {
        throw new IllegalStateException(
          "MateriallyTexturedBlockPreviewRenderHandler is disabled on 26.2; RenderLevelStageEvent is NeoForge-only.");
    }

    /**
     * Disabled no-op (see the class javadoc). Kept so a future revival has a registration point.
     */
    public static void register()
    {
        // no-op
    }
}
