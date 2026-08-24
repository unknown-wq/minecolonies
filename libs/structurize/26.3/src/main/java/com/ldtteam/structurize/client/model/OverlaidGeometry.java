package com.ldtteam.structurize.client.model;

/**
 * Simple wrapper to create {@code OverlaidBakedModel}.
 *
 * <p>Port note (26.2): the NeoForge baking stage this hung off
 * ({@code IUnbakedGeometry#bake(IGeometryBakingContext, ModelBaker, spriteGetter, ModelState, ItemOverrides)})
 * does not exist in vanilla or Fabric. Vanilla's unbound geometry is
 * {@code UnbakedGeometry#bake(TextureSlots, ModelBaker, ModelState, ModelDebugName) -> QuadCollection}: it
 * returns quads, not a model, so it cannot produce a wrapper. Same finding as Domum Ornamentum's
 * PORT-GAPS entry 10.</p>
 */
// TODO(port-26.2): DISABLED — IUnbakedGeometry/IGeometryBakingContext/ItemOverrides/BakedModel all removed
public final class OverlaidGeometry
{
    private OverlaidGeometry()
    {
    }

    /*
    public class OverlaidGeometry implements IUnbakedGeometry<OverlaidGeometry>
    {
        private Identifier overlayModelId;

        public OverlaidGeometry(final Identifier overlayModelId) { this.overlayModelId = overlayModelId; }

        @Override
        public BakedModel bake(final IGeometryBakingContext context, final ModelBaker baker,
                               final Function<Material, TextureAtlasSprite> spriteGetter,
                               final ModelState modelState, final ItemOverrides overrides)
        {
            UnbakedModel unbaked = baker.getModel(overlayModelId);
            BakedModel baked = unbaked.bake(baker, spriteGetter, modelState);
            if (baked == null) { baked = Minecraft.getInstance().getModelManager().getMissingModel(); }
            return new OverlaidBakedModel(baked);
        }
    }
    */
}
