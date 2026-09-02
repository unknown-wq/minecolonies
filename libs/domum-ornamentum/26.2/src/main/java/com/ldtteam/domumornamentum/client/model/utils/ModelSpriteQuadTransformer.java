package com.ldtteam.domumornamentum.client.model.utils;

import com.ldtteam.domumornamentum.client.color.MateriallyTexturedBlockBlockColor;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Re-textures a single quad that has already been loaded into a {@link QuadEmitter}.
 *
 * <h2>How this used to work, and why it changed</h2>
 * On NeoForge this class implemented {@code IQuadTransformer} and edited the raw {@code int[]} vertex data of
 * a {@code BakedQuad} in place ({@code quad.sprite = ...}, {@code quad.getVertices()[offset] = ...}). Neither
 * exists in 26.2:
 * <ul>
 *   <li>{@code BakedQuad} is an immutable {@code record} with {@code Vector3fc position0..3} and four
 *       {@code long packedUV} fields - no {@code int[]} and no mutable sprite field
 *       ({@code /opt/mc-src/net/minecraft/client/resources/model/geometry/BakedQuad.java}).</li>
 *   <li>{@code IQuadTransformer} / {@code QuadTransformers} are NeoForge classes with no Fabric analogue.</li>
 * </ul>
 * The Fabric Rendering API replacement is the {@code QuadEmitter}: quads are loaded with
 * {@link QuadEmitter#fromBakedQuad}, mutated through the {@code MutableQuadView} setters and pushed with
 * {@link QuadEmitter#emit()}.
 *
 * <h2>The UV round trip</h2>
 * {@code fromBakedQuad} leaves the UVs in <em>atlas</em> space (already interpolated into the source sprite).
 * To move them onto another sprite we normalise them back into the source sprite's 0..1 box and then let
 * {@link MutableQuadView#materialBake} interpolate them into the replacement sprite. The
 * {@link MutableQuadView#BAKE_NORMALIZED} flag is mandatory: without it {@code materialBake} assumes the UVs
 * are in vanilla 0..16 element space and divides them by 16 first (verified in
 * {@code net.fabricmc.fabric.impl.client.renderer.QuadSpriteBaker#bakeSprite}).
 * {@code materialBake} also updates the quad's atlas, animation flag, chunk layer and item render type from
 * the new sprite, which is what replaces the old {@code ChunkRenderTypeSet} bookkeeping.
 */
public final class ModelSpriteQuadTransformer
{
    private ModelSpriteQuadTransformer()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModelSpriteQuadTransformer. This is a utility class");
    }

    /**
     * Rewrites the quad currently held by {@code emitter} so that it uses the replacement sprite instead of
     * {@code sourceSprite}, and applies the replacement block's tint to the vertex colours.
     *
     * @param emitter      the emitter, already primed via {@link QuadEmitter#fromBakedQuad}
     * @param sourceQuad   the quad the emitter was primed from (needed for its original sprite and tint index)
     * @param replacement  the resolved replacement
     * @param hostState    the Domum Ornamentum block state being rendered
     * @param level        render view, may be {@code null} when rendering outside of a level
     * @param pos          block position, may be {@code null} when rendering outside of a level
     */
    public static void retexture(
      final QuadEmitter emitter,
      final BakedQuad sourceQuad,
      final ModelSpriteQuadTransformerData replacement,
      final BlockState hostState,
      final @Nullable BlockAndTintGetter level,
      final @Nullable BlockPos pos)
    {
        final TextureAtlasSprite sourceSprite = sourceQuad.materialInfo().sprite();

        final float minU = sourceSprite.getU0();
        final float uDelta = sourceSprite.getU1() - minU;
        final float minV = sourceSprite.getV0();
        final float vDelta = sourceSprite.getV1() - minV;

        // Degenerate sprite: nothing sane to remap onto, leave the quad as it is.
        if (uDelta == 0.0F || vDelta == 0.0F)
        {
            return;
        }

        for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++)
        {
            final float u = (emitter.u(vertexIndex) - minU) / uDelta;
            final float v = (emitter.v(vertexIndex) - minV) / vDelta;
            emitter.uv(vertexIndex, u, v);
        }

        emitter.materialBake(replacement.material(), MutableQuadView.BAKE_NORMALIZED);

        // The NeoForge build encoded "which block state should tint me" into the quad's tint index
        // ((Block.getId(state) << 8) | tintIndex) and unpacked it again inside a BlockColor/ItemColor
        // handler. That trick is impossible in 26.2: BlockColors is now a per-block List<BlockTintSource>
        // and the tint index is a plain index into that list, bounds-checked by
        // BlockColors#getTintSource (/opt/mc-src/net/minecraft/client/color/block/BlockColors.java).
        // Since we already have the level and position here, we resolve the tint ourselves and bake it
        // straight into the vertex colours, then clear the tint index so nothing tints us twice.
        if (sourceQuad.materialInfo().isTinted())
        {
            final int tint = MateriallyTexturedBlockBlockColor.getColor(
              hostState, replacement.state(), sourceQuad.materialInfo().tintIndex(), level, pos);
            emitter.multiplyColor(tint);
        }

        emitter.tintIndex(-1);
    }

    /**
     * Resolves the colour multiplier a given replacement block state contributes for the given tint layer.
     *
     * @return an opaque ARGB colour, {@code -1} (white) when the block has no tint source for this layer
     */
    public static int resolveTint(
      final BlockState replacementState,
      final int layer,
      final @Nullable BlockAndTintGetter level,
      final @Nullable BlockPos pos)
    {
        if (layer < 0)
        {
            return -1;
        }

        final BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(replacementState, layer);
        if (source == null)
        {
            return -1;
        }

        if (level == null || pos == null)
        {
            return source.color(replacementState) | 0xFF000000;
        }

        return source.colorInWorld(replacementState, level, pos) | 0xFF000000;
    }
}
