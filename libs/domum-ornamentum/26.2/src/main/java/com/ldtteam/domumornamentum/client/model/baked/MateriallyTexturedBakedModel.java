package com.ldtteam.domumornamentum.client.model.baked;

import com.ldtteam.domumornamentum.client.model.baked.RetexturedBakedModelBuilder.ReplacementModelData;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.client.model.utils.ModelSpriteQuadTransformer;
import com.ldtteam.domumornamentum.client.model.utils.ModelSpriteQuadTransformerData;
import com.ldtteam.domumornamentum.entity.block.IMateriallyTexturedBlockEntity;
import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.client.renderer.v1.model.ModelHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The materially textured block model.
 *
 * <h2>Why this is a {@code BlockStateModel} and not a {@code BakedModel}</h2>
 * {@code net.minecraft.client.resources.model.BakedModel} does not exist in 26.2. The block rendering
 * pipeline was split into {@code BlockStateModel} (per block state, collects
 * {@code BlockStateModelPart}s) and the item pipeline ({@code ItemModel} + {@code ItemStackRenderState}).
 * Confirmed against {@code /opt/mc-src/net/minecraft/client/renderer/block/dispatch/BlockStateModel.java}.
 *
 * <h2>Where the material data comes from</h2>
 * NeoForge shipped per-block-entity model data through {@code ModelData} / {@code ModelProperty}, which
 * Fabric has no equivalent of. It is not needed: the Fabric Rendering API adds
 * {@code FabricBlockStateModel#emitQuads(QuadEmitter, BlockAndTintGetter, BlockPos, BlockState, RandomSource,
 * Predicate&lt;Direction&gt;)} to every {@code BlockStateModel}, and that call already carries the render view
 * and the position, so the block entity - and with it {@link MaterialTextureData} - can simply be read
 * straight out of the level. No attachment, no {@code RenderAttachmentBlockEntity}, no model data.
 *
 * <h2>Caching</h2>
 * {@link #createGeometryKey} returns the material set, so the terrain renderer's geometry cache treats two
 * neighbouring blocks with the same materials as identical. Returning {@code null} (the FRAPI default) would
 * disable that cache entirely.
 */
public class MateriallyTexturedBakedModel extends WrapperBlockStateModel
{
    public MateriallyTexturedBakedModel(final BlockStateModel innerModel)
    {
        super(innerModel);
    }

    /**
     * Reads the material set for the block at {@code pos}.
     *
     * <p>Preferred channel is {@code BlockGetter#getBlockEntityRenderData(BlockPos)} from
     * {@code fabric-block-getter-api-v2}: 26.2's {@code BlockGetter} extends
     * {@code net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter}
     * ({@code /opt/mc-src/net/minecraft/world/level/BlockGetter.java}:25), and the render-section snapshot
     * captures whatever {@code RenderDataBlockEntity#getRenderData()} returned on the main thread. This is the
     * direct replacement for NeoForge's {@code ModelData} and the only thread-safe way to read block-entity
     * state from a chunk-builder worker. {@code AbstractMateriallyTexturedBlockEntity} returns the
     * {@link MaterialTextureData} instance itself from {@code getRenderData()}.
     *
     * <p>The block-entity fallback covers views that carry no render data (for example a plain level lookup
     * from an item or preview render path).
     */
    private static MaterialTextureData readTextureData(final @Nullable BlockAndTintGetter level, final @Nullable BlockPos pos)
    {
        if (level == null || pos == null)
        {
            return MaterialTextureData.EMPTY;
        }

        // The cast is a no-op at runtime - BlockGetter already extends FabricBlockGetter once Loom has
        // applied fabric-block-getter-api-v2's class tweaker - but it is required to compile against the
        // raw deobfuscated Minecraft jar, which has no interface injection applied (same class of false
        // positive as MenuScreens#register "has private access").
        final Object renderData = ((FabricBlockGetter) level).getBlockEntityRenderData(pos);
        if (renderData instanceof final MaterialTextureData textureData)
        {
            return textureData;
        }

        final BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof final IMateriallyTexturedBlockEntity materiallyTextured)
        {
            return materiallyTextured.getTextureData();
        }

        return MaterialTextureData.EMPTY;
    }

    @Override
    public void emitQuads(
      final QuadEmitter emitter,
      final BlockAndTintGetter level,
      final BlockPos pos,
      final BlockState state,
      final RandomSource random,
      final Predicate<Direction> cullTest)
    {
        final MaterialTextureData textureData = readTextureData(level, pos);
        if (textureData.isEmpty())
        {
            super.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }

        final RetexturedBakedModelBuilder replacements = RetexturedBakedModelBuilder.resolve(textureData);
        if (replacements.isEmpty())
        {
            super.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }

        final List<BlockStateModelPart> parts = new ArrayList<>();
        this.wrapped.collectParts(random, parts);

        for (final BlockStateModelPart part : parts)
        {
            emitPart(emitter, part, replacements, state, level, pos, cullTest);
        }
    }

    /**
     * Re-implementation of {@code net.fabricmc.fabric.impl.client.renderer.VanillaBlockModelPartEncoder}
     * with a sprite swap inserted between {@code fromBakedQuad} and {@code emit}. The surrounding calls
     * (cull face first, then {@code fromBakedQuad}, then ambient occlusion, then
     * {@link ShadeMode#VANILLA}) mirror that class exactly - the order matters, {@code fromBakedQuad}
     * resets parts of the emitter state.
     */
    private static void emitPart(
      final QuadEmitter emitter,
      final BlockStateModelPart part,
      final RetexturedBakedModelBuilder replacements,
      final BlockState hostState,
      final @Nullable BlockAndTintGetter level,
      final @Nullable BlockPos pos,
      final Predicate<Direction> cullTest)
    {
        final TriState ambientOcclusion = part.useAmbientOcclusion() ? TriState.DEFAULT : TriState.FALSE;

        for (int faceIndex = 0; faceIndex <= ModelHelper.NULL_FACE_ID; faceIndex++)
        {
            final Direction cullFace = ModelHelper.faceFromIndex(faceIndex);
            // The predicate answers "should this cull face be skipped".
            if (cullTest.test(cullFace))
            {
                continue;
            }

            for (final BakedQuad quad : part.getQuads(cullFace))
            {
                final Identifier spriteName = quad.materialInfo().sprite().contents().name();
                final ReplacementModelData replacement = replacements.lookup(spriteName);
                // The placeholder is mapped to a block with no geometry - air, most of the time, which is how
                // a model says "this component shows nothing". The quad is dropped rather than emitted with
                // some stand-in sprite.
                if (replacement != null && replacement.erases())
                {
                    continue;
                }

                emitter.cullFace(cullFace);
                emitter.fromBakedQuad(quad);
                emitter.ambientOcclusion(ambientOcclusion);
                emitter.shadeMode(ShadeMode.VANILLA);

                if (replacement != null)
                {
                    ModelSpriteQuadTransformer.retexture(
                      emitter, quad, replacement.dataFor(cullFace, quad.direction()), hostState, level, pos);
                }

                emitter.emit();
            }
        }
    }

    @Override
    public Object createGeometryKey(
      final BlockAndTintGetter level, final BlockPos pos, final BlockState state, final RandomSource random)
    {
        final MaterialTextureData textureData = readTextureData(level, pos);
        return textureData.isEmpty() ? state : new GeometryKey(state, textureData);
    }

    @Override
    public int materialFlags(
      final BlockAndTintGetter level, final BlockPos pos, final BlockState state, final RandomSource random)
    {
        final MaterialTextureData textureData = readTextureData(level, pos);
        if (textureData.isEmpty())
        {
            return this.wrapped.materialFlags();
        }

        return this.wrapped.materialFlags() | RetexturedBakedModelBuilder.resolve(textureData).extraMaterialFlags();
    }

    @Override
    public Material.Baked particleMaterial(final BlockAndTintGetter level, final BlockPos pos, final BlockState state)
    {
        final Material.Baked base = this.wrapped.particleMaterial();
        final MaterialTextureData textureData = readTextureData(level, pos);
        if (textureData.isEmpty())
        {
            return base;
        }

        final RetexturedBakedModelBuilder replacements = RetexturedBakedModelBuilder.resolve(textureData);
        final ReplacementModelData replacement = replacements.lookup(base.sprite().contents().name());
        // An erasing replacement has no sprite to offer. Particles need one regardless - there is no such
        // thing as breaking a block without them - so the model's own particle sprite stands.
        if (replacement == null || replacement.erases())
        {
            return base;
        }

        final ModelSpriteQuadTransformerData data = replacement.dataFor(null, null);
        return data == null ? base : data.material();
    }

    private record GeometryKey(BlockState state, MaterialTextureData textureData)
    {
    }
}
