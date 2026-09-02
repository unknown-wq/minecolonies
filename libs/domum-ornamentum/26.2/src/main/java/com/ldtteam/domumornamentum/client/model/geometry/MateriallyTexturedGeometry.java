package com.ldtteam.domumornamentum.client.model.geometry;

import net.minecraft.resources.Identifier;

/**
 * TODO(port-26.2): DISABLED — NeoForge's {@code IUnbakedGeometry}/{@code IGeometryBakingContext} baking stage
 * has no Fabric or vanilla 26.2 counterpart; unbaked geometry is now {@code UnbakedGeometry#bake(TextureSlots,
 * ModelBaker, ModelState, ModelDebugName) -> QuadCollection} and cannot produce a whole model.
 *
 * <p>Retexturing moved from bake time to render time - see
 * {@code com.ldtteam.domumornamentum.client.model.loader.MateriallyTexturedModelLoader} and
 * {@code com.ldtteam.domumornamentum.client.model.baked.MateriallyTexturedBakedModel}. This class is kept as a
 * marker only so the original shape stays visible next to its replacement.
 *
 * <p>Original NeoForge implementation:
 * <pre>
 * public class MateriallyTexturedGeometry implements IUnbakedGeometry&lt;MateriallyTexturedGeometry&gt;
 * {
 *     private final Identifier innerModelLocation;
 *
 *     public MateriallyTexturedGeometry(final Identifier innerModelLocation)
 *     {
 *         this.innerModelLocation = innerModelLocation;
 *     }
 *
 *     &#64;Override
 *     public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
 *                            Function&lt;Material, TextureAtlasSprite&gt; spriteGetter,
 *                            ModelState modelState, ItemOverrides overrides) {
 *         final UnbakedModel innerModel = baker.getModel(this.innerModelLocation);
 *
 *         if (!(innerModel instanceof BlockModel)) {
 *             throw new IllegalStateException("BlockModel parent has to be a block model.");
 *         }
 *
 *         final BakedModel innerBakedModel = innerModel.bake(baker, spriteGetter, modelState);
 *
 *         return new MateriallyTexturedBakedModel(innerBakedModel);
 *     }
 * }
 * </pre>
 */
public class MateriallyTexturedGeometry
{
    private final Identifier innerModelLocation;

    public MateriallyTexturedGeometry(final Identifier innerModelLocation)
    {
        this.innerModelLocation = innerModelLocation;
    }

    public Identifier getInnerModelLocation()
    {
        return this.innerModelLocation;
    }
}
