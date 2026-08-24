package com.ldtteam.domumornamentum.client.color;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.client.model.utils.ModelSpriteQuadTransformer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the tint a replacement material contributes to a retextured quad.
 *
 * <h2>Why this is no longer a registered colour handler</h2>
 * TODO(port-26.2): DISABLED — the "pack a block state id into the tint index" trick is impossible in 26.2.
 * <p>
 * The NeoForge build encoded {@code (Block.getId(materialState) << 8) | tintIndex} into the quad's tint index
 * and unpacked it again in a {@code BlockColor} registered for every DO block. 26.2 removed
 * {@code net.minecraft.client.color.block.BlockColor} completely (there is no such file under
 * {@code /opt/mc-src/net/minecraft/client/color/block/}); tinting is now a per-block
 * {@code List<BlockTintSource>} and the quad's tint index is a plain <em>index into that list</em>,
 * bounds-checked by {@code BlockColors#getTintSource(BlockState, int)}. A packed 20-bit value would simply
 * fall off the end of the list and tint nothing.
 * <p>
 * Fabric's replacement registration is {@code BlockColorRegistry.register(List<BlockTintSource>, Block...)}
 * ({@code fabric-rendering-v1}), but the mod does not need it: the material's colour is resolved and
 * multiplied into the vertex colours at emit time, where the material block state is still known - see
 * {@link ModelSpriteQuadTransformer#retexture}. This class survives as the shared entry point for that
 * resolution and for {@link IMateriallyTexturedBlock#usesWorldSpecificTinting()}.
 *
 * <p>Original NeoForge implementation:
 * <pre>
 * public int getColor(BlockState state, &#64;Nullable BlockAndTintGetter level, &#64;Nullable BlockPos pos, int tintIndex) {
 *     final int blockStateId = tintIndex &gt;&gt; TINT_BITS;
 *     final BlockState containedState = Block.stateById(blockStateId);
 *     int tintValue = tintIndex &amp; TINT_MASK;
 *     if (state.getBlock() instanceof IMateriallyTexturedBlock block &amp;&amp; !block.usesWorldSpecificTinting()) {
 *         return Minecraft.getInstance().getBlockColors().getColor(containedState, null, null, tintValue);
 *     }
 *     return Minecraft.getInstance().getBlockColors().getColor(containedState, level, pos, tintValue);
 * }
 * </pre>
 */
public final class MateriallyTexturedBlockBlockColor
{
    private MateriallyTexturedBlockBlockColor()
    {
        throw new IllegalStateException(
          "Can not instantiate an instance of: MateriallyTexturedBlockBlockColor. This is a utility class");
    }

    /**
     * @param hostState      the DO block being rendered, decides whether biome tinting is position aware
     * @param materialState  the material the quad was retextured with
     * @param tintLayer      the tint layer index of the source quad
     * @param level          render view, may be {@code null}
     * @param pos            block position, may be {@code null}
     * @return an opaque ARGB multiplier, {@code -1} when the material contributes no tint
     */
    public static int getColor(
      final BlockState hostState,
      final BlockState materialState,
      final int tintLayer,
      final @Nullable BlockAndTintGetter level,
      final @Nullable BlockPos pos)
    {
        if (hostState.getBlock() instanceof final IMateriallyTexturedBlock block && !block.usesWorldSpecificTinting())
        {
            return ModelSpriteQuadTransformer.resolveTint(materialState, tintLayer, null, null);
        }

        return ModelSpriteQuadTransformer.resolveTint(materialState, tintLayer, level, pos);
    }
}
