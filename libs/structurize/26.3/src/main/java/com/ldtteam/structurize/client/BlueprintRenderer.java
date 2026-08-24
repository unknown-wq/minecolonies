package com.ldtteam.structurize.client;

import com.ldtteam.structurize.Structurize;
import com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtils;
import com.ldtteam.structurize.client.fakelevel.BlueprintBlockAccess;
import com.ldtteam.structurize.component.CapturedBlock;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.ldtteam.structurize.tag.ModTags;
import com.ldtteam.structurize.util.BlockInfo;
import com.ldtteam.structurize.util.WorldRenderMacros;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The renderer for blueprint.
 * Holds all information required to render a blueprint.
 *
 * <p><b>Port note (26.2).</b> The 1.21.1 implementation baked the whole blueprint into per-{@code RenderType}
 * {@code VertexBuffer}s once and then drew them by hand with a {@code ShaderInstance} plus the
 * {@code CHUNK_OFFSET} uniform. None of that survives: {@code VertexBuffer}, {@code ShaderInstance},
 * {@code Uniform#CHUNK_OFFSET}, {@code RenderType#setupRenderState}, {@code ItemBlockRenderTypes},
 * {@code BakedModel}, {@code ModelData} and {@code MultiBufferSource} are all gone, and level rendering is a
 * submit queue. The blueprint is therefore resolved once into per block {@link BlockModelRenderState}s and
 * re-submitted every frame through {@link BlockModelRenderState#submit}. See the report for the visible
 * consequences (no ambient occlusion, no fluids, no transparency).</p>
 */
public class BlueprintRenderer implements AutoCloseable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BlueprintRenderer.class);

    private static boolean hasWarnedExceptions = false;

    /**
     * 26.2 resolves block models against a display context instead of a level; a single shared instance is
     * enough, the class carries no state.
     */
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlueprintBlockAccess blockAccess;
    List<Entity> entities = List.of();
    private List<BlockEntity> tileEntities;
    /**
     * One resolved model per visible block of the blueprint, in blueprint local coordinates.
     */
    private List<ResolvedBlock> resolvedBlocks;
    private long lastGameTime;
    private boolean bypassMainFrustum = false;
    private Set<Object> crashingObjects = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * A blueprint block whose model has already been resolved.
     */
    private record ResolvedBlock(BlockPos pos, BlockModelRenderState model, int lightCoords)
    {
    }

    /**
     * Static factory utility method to handle the extraction of the values from the blueprint.
     *
     * @param blueprint The blueprint to create an instance for.
     * @return The renderer.
     */
    public static BlueprintRenderer buildRendererForBlueprint(final Blueprint blueprint)
    {
        final BlueprintBlockAccess blockAccess = new BlueprintBlockAccess(blueprint);
        return new BlueprintRenderer(blockAccess);
    }

    private BlueprintRenderer(final BlueprintBlockAccess blockAccess)
    {
        this.blockAccess = blockAccess;
    }

    /**
     * Updates blueprint reference if it has same hash.
     *
     * @param previewData blueprint and context from active structure
     */
    public void updateBlueprint(final BlueprintPreviewData previewData)
    {
        if (blockAccess.getLevelSource() != previewData.getBlueprint() && blockAccess.getLevelSource().hashCode() == previewData.getBlueprint().hashCode())
        {
            blockAccess.setLevelSource(previewData.getBlueprint());
        }
    }

    private void init(final BlueprintPreviewData previewData, final Map<Object, Exception> suppressedExceptions)
    {
        final Blueprint blueprint = previewData.getBlueprint();
        final BlockModelResolver modelResolver = new BlockModelResolver(Minecraft.getInstance().getModelManager());

        final Map<BlockPos, BlockEntity> tileEntitiesMap = new HashMap<>();
        for (final BlockInfo raw : blueprint.getBlockInfoAsList())
        {
            final BlockInfo blockInfo = BlueprintBlockInfoTransformHandler.getInstance().Transform(raw);
            if (!blockInfo.hasTileEntityData())
            {
                continue;
            }
            final BlockEntity be = BlueprintUtils.constructTileEntity(blockInfo, blockAccess, blueprint.getRegistryAccess());
            if (be != null)
            {
                tileEntitiesMap.put(blockInfo.getPos(), be);
            }
        }
        entities = BlueprintUtils.instantiateEntities(blueprint, blockAccess);

        blockAccess.setBlockEntities(tileEntitiesMap);
        blockAccess.setEntities(entities);
        blockAccess.setSolidSubstitutionOverride(previewData.getSolidSubstitutionOverride());
        blockAccess.setRenderBlocksNiceOverride(previewData.getRenderBlocksNice());

        final List<ResolvedBlock> resolved = new ArrayList<>();

        for (final BlockInfo blockInfo : blueprint.getBlockInfoAsList())
        {
            final BlockPos blockPos = blockInfo.getPos();
            BlockState state = blockInfo.getState();
            // specially handle blockTagSub here cuz of block entity changes
            if (previewData.getRenderBlocksNice() && state.getBlock() == ModBlocks.blockTagSubstitution.get())
            {
                if (tileEntitiesMap.remove(blockPos) instanceof final BlockEntityTagSubstitution tagTE)
                {
                    final CapturedBlock replacement = tagTE.getReplacement();
                    state = replacement.blockState();

                    replacement.serializedBE().map(tag -> BlockEntity.loadStatic(blockPos, replacement.blockState(), tag, blueprint.getRegistryAccess())).ifPresent(newBe -> {
                        newBe.setLevel(blockAccess);
                        tileEntitiesMap.put(blockPos, newBe);
                    });
                }
                else
                {
                    state = Blocks.AIR.defaultBlockState();
                }
            }
            else
            {
                state = blockAccess.prepareBlockStateForRendering(state, blockPos);
            }

            // TODO(port-26.2): DISABLED — fluid geometry. BlockRenderDispatcher#renderLiquid, ItemBlockRenderTypes
            //  and the ChunkOffsetBufferBuilderWrapper it fed no longer exist; 26.2 renders fluids from
            //  FluidRenderer inside the terrain section builder, which is not reachable for a fake level.
            /*
            final FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty())
            {
                final RenderType renderType = ItemBlockRenderTypes.getRenderLayer(fluidState);
                ...
                blockRenderer.renderLiquid(blockPos, blockAccess, fluidBufferWrapper, state, fluidState);
            }
            */

            try
            {
                if (state.getRenderShape() != RenderShape.INVISIBLE)
                {
                    final BlockModelRenderState modelState = new BlockModelRenderState();
                    modelResolver.update(modelState, state, BLOCK_DISPLAY_CONTEXT);

                    if (!modelState.isEmpty())
                    {
                        resolved.add(new ResolvedBlock(blockPos, modelState, LightCoordsUtil.getLightCoords(blockAccess, blockPos)));
                    }
                }
            }
            catch (final ReportedException e)
            {
                suppressedExceptions.put(blockInfo, e);
            }
        }

        blockAccess.setSolidSubstitutionOverride(null);
        blockAccess.setRenderBlocksNiceOverride(Structurize.getConfig().getClient().renderPlaceholdersNice.get());

        resolvedBlocks = resolved;
        tileEntities = new ArrayList<>(tileEntitiesMap.values());
    }

    /**
     * Draws structure into world.
     */
    public void draw(final BlueprintPreviewData previewData, final BlockPos pos, final WorldRenderMacros ctx)
    {
        // we've crashed hard before, full skip
        if (crashingObjects == null)
        {
            return;
        }

        try
        {
            final Map<Object, Exception> suppressedExceptions = drawUnsafe(previewData, pos, ctx);
            if (!suppressedExceptions.isEmpty())
            {
                if (!hasWarnedExceptions)
                {
                    hasWarnedExceptions = true;
                    Minecraft.getInstance().player.sendSystemMessage(Component.translatable("structurize.preview_renderer.exception"));
                }

                boolean crashReported = false;
                boolean isEmpty = true;
                for (final Map.Entry<Object, Exception> e : suppressedExceptions.entrySet())
                {
                    if (!crashingObjects.add(e.getKey()))
                    {
                        continue;
                    }
                    isEmpty = false;

                    if (e.getValue() instanceof final ReportedException reportedException)
                    {
                        printCrashReport(reportedException.getReport(), previewData);
                        crashReported = true;
                    }
                    else
                    {
                        LOGGER.error("", e.getValue());
                    }
                }

                if (!crashReported && !isEmpty)
                {
                    printCrashReport(CrashReport.forThrowable(new Exception(), "Small exception, rendering partially"), previewData);
                }
            }
        }
        catch (final Exception e)
        {
            printCrashReport(CrashReport.forThrowable(e, "Fatal exception, cannot render"), previewData);

            crashingObjects = null;
            Minecraft.getInstance().player.sendSystemMessage(
                Component.translatable("structurize.preview_renderer.cannot_render", previewData.getBlueprint().getName()));
        }
    }

    private static void printCrashReport(final CrashReport report, final BlueprintPreviewData previewData)
    {
        previewData.getBlueprint().describeSelfInCrashReport(report.addCategory("Blueprint"));
        LOGGER.error(report.getFriendlyReport(new ReportType("Problem during blueprint rendering", ReportType.TEST.nuggets())));
    }

    /**
     * Draws structure into world.
     *
     * @return suppressed exceptions
     */
    public Map<Object, Exception> drawUnsafe(final BlueprintPreviewData previewData, final BlockPos pos, final WorldRenderMacros ctx)
    {
        final BlockPos anchorPos = pos.subtract(previewData.getBlueprint().getPrimaryBlockOffset());

        // cull entire rendering
        // Blueprint#getAABB() came from com.ldtteam.common's IFakeLevelBlockGetter, which the C8
        // re-implementation does not carry; the box is trivially rebuilt from the blueprint size.
        final Blueprint culled = previewData.getBlueprint();
        final AABB blueprintBox = new AABB(anchorPos.getX(),
            anchorPos.getY(),
            anchorPos.getZ(),
            anchorPos.getX() + culled.getSizeX(),
            anchorPos.getY() + culled.getSizeY(),
            anchorPos.getZ() + culled.getSizeZ());
        if (!ctx.frustum.isVisible(blueprintBox) && !bypassMainFrustum)
        {
            return Map.of();
        }

        final Map<Object, Exception> suppressedExceptions = new IdentityHashMap<>();
        final Minecraft mc = Minecraft.getInstance();
        final long gameTime = mc.level.getGameTime();
        final PoseStack matrixStack = ctx.poseStack;
        final SubmitNodeCollector collector = ctx.submitNodeCollector;
        final CameraRenderState cameraState = ctx.cameraRenderState;
        final ProfilerFiller profiler = Profiler.get();

        profiler.push("struct_render_init");

        // make sure instances are synced
        updateBlueprint(previewData);
        blockAccess.setWorldPos(anchorPos);

        // init
        if (resolvedBlocks == null)
        {
            init(previewData, suppressedExceptions);
        }

        profiler.popPush("struct_render_prepare");
        final Vec3 viewPosition = ctx.cameraPosition;
        final Vec3 realRenderRootVecd = Vec3.atLowerCornerOf(anchorPos).subtract(viewPosition);

        final float partialTicks;
        {
            final Entity entity = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
            partialTicks = mc.level.tickRateManager().isEntityFrozen(entity) ? 1.0F
                : ctx.deltaTracker.getGameTimeDeltaPartialTick(!mc.level.tickRateManager().isFrozen());
        }

        // camera position expressed in blueprint local space; that is all the 26.2 dispatchers need
        final Vec3 localCameraPos = viewPosition.subtract(anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());

        final BlockEntityRenderDispatcher beDispatcher = mc.getBlockEntityRenderDispatcher();
        final EntityRenderDispatcher entityDispatcher = mc.getEntityRenderDispatcher();
        beDispatcher.prepare(localCameraPos);

        final Frustum blueprintLocalFrustum = new Frustum(ctx.frustum);
        blueprintLocalFrustum.prepare(localCameraPos.x(), localCameraPos.y(), localCameraPos.z());
        bypassMainFrustum = false;

        // TODO(port-26.2): DISABLED — Lighting.setupLevel/setupNetherLevel and FogRenderer.setupFog/setupNoFog
        //  took no arguments we can supply any more (fog is a GpuBufferSlice owned by the level renderer) and
        //  the submit queue applies the level's own lighting/fog to our nodes anyway.
        /*
        if (mc.level.effects().constantAmbientLight()) { Lighting.setupNetherLevel(); } else { Lighting.setupLevel(); }
        FogRenderer.setupFog(...);
        */

        // Blocks

        profiler.popPush("struct_render_blocks");
        matrixStack.pushPose();
        matrixStack.translate(realRenderRootVecd.x(), realRenderRootVecd.y(), realRenderRootVecd.z());

        for (final ResolvedBlock resolvedBlock : resolvedBlocks)
        {
            final BlockPos blockPos = resolvedBlock.pos();
            matrixStack.pushPose();
            matrixStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            resolvedBlock.model().submit(matrixStack, collector, resolvedBlock.lightCoords(), OverlayTexture.NO_OVERLAY, 0);
            matrixStack.popPose();
        }

        // Entities

        profiler.popPush("struct_render_entities");
        for (final Entity entity : entities)
        {
            // TODO(port-26.3): EntityRenderDispatcher#shouldRender gained a trailing partialTicks parameter,
            //  which it forwards to EntityRenderer#shouldRender. Feed it the same partial tick the extract
            //  call below already uses, so culling and state extraction agree on the frame.
            if (!entityDispatcher.shouldRender(entity,
                blueprintLocalFrustum,
                localCameraPos.x(),
                localCameraPos.y(),
                localCameraPos.z(),
                partialTicks))
            {
                continue;
            }

            // EntityType#is(TagKey) is gone in 26.2 - ask the holder instead
            if (gameTime != lastGameTime && entity.getType().builtInRegistryHolder().is(ModTags.PREVIEW_TICKING_ENTITIES))
            {
                try
                {
                    entity.tick();
                }
                catch (final Exception e)
                {
                    // well, noop
                    suppressedExceptions.put(entity, e);
                }
            }

            // TODO(port-26.2): DEGRADED — Entity#noCulling is gone in 26.2; whether an entity ignores culling is
            //  now the renderer's business (EntityRenderer#affectedByCulling, protected). The blueprint AABB
            //  test is therefore never bypassed: an entity sticking far out of the blueprint box can pop.
            try
            {
                final EntityRenderState entityState = entityDispatcher.extractEntity(entity, partialTicks);
                entityDispatcher.submit(entityState, cameraState, entity.getX(), entity.getY(), entity.getZ(), matrixStack, collector);
            }
            catch (final ClassCastException e)
            {
                // Oops
                suppressedExceptions.put(entity, e);
            }
        }

        // Block entities

        profiler.popPush("struct_render_blockentities");
        for (final BlockEntity tileEntity : tileEntities)
        {
            final BlockPos tePos = tileEntity.getBlockPos();

            if (gameTime != lastGameTime)
            {
                // hooks from EntityBlock#getTicker(Level, BlockState, BlockEntityType) for client side
                // either mc.level and anchorPos - particles, player distance etc.
                // or blockAccess and tePos - blueprint neighborhood
                if (tileEntity instanceof final SpawnerBlockEntity spawner)
                {
                    SpawnerBlockEntity.clientTick(mc.level, anchorPos.offset(tePos), blockAccess.getBlockState(tePos), spawner);
                }
                else if (tileEntity instanceof final EnchantingTableBlockEntity enchTable)
                {
                    EnchantingTableBlockEntity
                        .bookAnimationTick(mc.level, anchorPos.offset(tePos), blockAccess.getBlockState(tePos), enchTable);
                }
                else if (tileEntity instanceof final CampfireBlockEntity campfire)
                {
                    final BlockState bs = blockAccess.getBlockState(tePos);
                    if (bs.getBlock() instanceof CampfireBlock && bs.getValue(CampfireBlock.LIT))
                    {
                        CampfireBlockEntity.particleTick(mc.level, anchorPos.offset(tePos), bs, campfire);
                    }
                }
                else if (tileEntity instanceof final SkullBlockEntity skull)
                {
                    final BlockState bs = blockAccess.getBlockState(tePos);
                    if (bs.getBlock() instanceof SkullBlock && (bs.is(Blocks.DRAGON_HEAD) || bs.is(Blocks.DRAGON_WALL_HEAD) ||
                        bs.is(Blocks.PIGLIN_HEAD) ||
                        bs.is(Blocks.PIGLIN_WALL_HEAD)))
                    {
                        SkullBlockEntity.animation(blockAccess, tePos, bs, skull);
                    }
                }
                else if (tileEntity instanceof final BeaconBlockEntity beacon)
                {
                    // uses sound and applies buffs, but we dont want any of this since we're preview
                    BeaconBlockEntity.tick(blockAccess, tePos, blockAccess.getBlockState(tePos), beacon);
                }
                else if (tileEntity instanceof final VaultBlockEntity vault)
                {
                    VaultBlockEntity.Client.tick(mc.level, anchorPos.offset(tePos), blockAccess.getBlockState(tePos), vault.getClientData(), vault.getSharedData());
                }
                else if (tileEntity instanceof final TrialSpawnerBlockEntity trialSpawner)
                {
                    trialSpawner.getTrialSpawner().tickClient(mc.level, anchorPos.offset(tePos), blockAccess.getBlockState(tePos).getOptionalValue(TrialSpawnerBlock.OMINOUS).orElse(false));
                }
            }

            // TODO(port-26.2): DEGRADED — BlockEntityRenderer#getRenderBoundingBox and #shouldRenderOffScreen(be)
            //  no longer exist (shouldRenderOffScreen() is now argument-less and lives behind tryExtractRenderState),
            //  so per block entity frustum culling of the preview is gone; the whole blueprint AABB test above stays.
            final BlockEntityRenderState beState = beDispatcher.tryExtractRenderState(tileEntity, partialTicks, null, false);
            if (beState == null)
            {
                continue;
            }

            matrixStack.pushPose();
            matrixStack.translate(tePos.getX(), tePos.getY(), tePos.getZ());
            beDispatcher.submit(beState, matrixStack, collector, cameraState);
            matrixStack.popPose();
        }

        matrixStack.popPose();

        // restore vanilla setup
        beDispatcher.prepare(viewPosition);

        lastGameTime = gameTime;
        profiler.pop();

        return suppressedExceptions;
    }

    /**
     * Clears cached geometry.
     */
    private void clearVertexBuffers()
    {
        // 26.2 keeps no GL objects on our side any more - the resolved models are plain heap objects.
        resolvedBlocks = null;
    }

    @Override
    public void close()
    {
        clearVertexBuffers();
    }

    /**
     * Assuming there's no blend function active let's take advantage of OpenGL blend color constant
     * which doesnt require any shader changes at all.
     * More info at: https://registry.khronos.org/OpenGL-Refpages/gl4/html/glBlendColor.xhtml
     */
    // TODO(port-26.2): DISABLED — glBlendColor + GlStateManager.BLEND + RenderSystem.blendFunc are all gone:
    //  blending is a property of the immutable RenderPipeline of a render type in 26.2, and there is no way to
    //  set a global blend constant. THRESHOLD is kept because event/WorldRenderContext still branches on it.
    public static class TransparencyHack
    {
        public static final float THRESHOLD = 0.99f;
        protected static boolean applied = false;

        public static void apply(final float overrideValue)
        {
            /*
            if (applied || GlStateManager.BLEND.mode.enabled) { return; }
            float alpha = Structurize.getConfig().getClient().rendererTransparency.get().floatValue();
            if (overrideValue != -1) { alpha = Mth.clamp(overrideValue, 0, 1); }
            if (alpha < 0 || alpha > THRESHOLD) { return; }
            applied = true;
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(SourceFactor.CONSTANT_ALPHA, DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            GL20C.glBlendColor(0, 0, 0, alpha);
            */
        }

        public static void reset()
        {
            /*
            if (!applied) { return; }
            applied = false;
            RenderSystem.disableBlend();
            */
        }
    }
}
