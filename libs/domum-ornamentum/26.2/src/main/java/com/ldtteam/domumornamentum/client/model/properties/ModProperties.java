package com.ldtteam.domumornamentum.client.model.properties;

/**
 * TODO(port-26.2): DISABLED — {@code net.neoforged.neoforge.model.data.ModelProperty} / {@code ModelData}
 * do not exist on Fabric and have no vanilla 26.2 equivalent.
 *
 * <p>Material data no longer needs a transport channel between the block entity and the model: Fabric's
 * {@code FabricBlockStateModel#emitQuads(QuadEmitter, BlockAndTintGetter, BlockPos, BlockState, RandomSource,
 * Predicate&lt;Direction&gt;)} already receives the render view and the block position, so
 * {@code MateriallyTexturedBakedModel} reads {@code IMateriallyTexturedBlockEntity#getTextureData()} straight
 * out of the level.
 *
 * <p>Original NeoForge implementation:
 * <pre>
 * public static ModelProperty&lt;MaterialTextureData&gt; MATERIAL_TEXTURE_PROPERTY = new ModelProperty&lt;&gt;();
 * </pre>
 */
public class ModProperties
{
    private ModProperties()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModProperties. This is a utility class");
    }
}
