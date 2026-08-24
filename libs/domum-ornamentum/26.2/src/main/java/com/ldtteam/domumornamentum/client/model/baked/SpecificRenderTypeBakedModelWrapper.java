package com.ldtteam.domumornamentum.client.model.baked;

/**
 * TODO(port-26.2): DISABLED — obsolete in 26.2; there is no per-render-type model any more.
 *
 * <p>On NeoForge a model had to be asked for its quads once per {@code RenderType}, so this wrapper existed to
 * pin a model to exactly one type ({@code ChunkRenderTypeSet.of(renderType)}). In 26.2 the layer is a property
 * of the individual quad - {@code BakedQuad.MaterialInfo#layer()} is a {@code ChunkSectionLayer} and
 * {@code ChunkRenderTypeSet} is gone entirely (verified: no {@code ChunkRenderTypeSet} anywhere under
 * {@code /opt/mc-src}, and {@code BakedQuad} is a record carrying {@code MaterialInfo}). The Fabric
 * {@code QuadEmitter} sets it per quad through {@code chunkLayer(ChunkSectionLayer)} /
 * {@code materialBake(Material.Baked, flags)}.
 *
 * <p>Nothing references this class any more; it is kept only so the original shape stays on record. The
 * original implementation is preserved in git history and in {@code 26.1/} (read-only source of the port).
 */
public final class SpecificRenderTypeBakedModelWrapper
{
    private SpecificRenderTypeBakedModelWrapper()
    {
        throw new IllegalStateException(
          "SpecificRenderTypeBakedModelWrapper is disabled on 26.2; render layers are per-quad now.");
    }
}
