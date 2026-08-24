package com.ldtteam.structurize.client.model;

/**
 * This exists because it seems to be the only way to override {@code BakedModel#isCustomRenderer}...
 *
 * <p>Port note (26.2): there is no {@code BakedModel} any more, no {@code BakedModelWrapper}, no
 * {@code isCustomRenderer()} and no {@code applyTransform(...)}. A block that wants bespoke geometry now
 * either ships a {@code BlockStateModel} through {@code fabric-model-loading-api-v1} or declares a
 * {@code SpecialModelRenderer}. The whole "wrap a model just to flag it as custom-rendered" idea is gone
 * together with {@code BlockEntityWithoutLevelRenderer}.</p>
 */
// TODO(port-26.2): DISABLED — BakedModel/BakedModelWrapper/isCustomRenderer removed in 26.2; the anchor block
//  falls back to its plain parent model (assets/structurize/models/block/blocktagsubstitutionoverlay.json),
//  since vanilla ignores the now-meaningless "loader" key of blocktagsubstitution.json.
public final class OverlaidBakedModel
{
    private OverlaidBakedModel()
    {
    }

    /*
    public class OverlaidBakedModel extends BakedModelWrapper<BakedModel>
    {
        public OverlaidBakedModel(final BakedModel overlay) { super(overlay); }

        @Override
        public boolean isCustomRenderer() { return true; }

        @Override
        public BakedModel applyTransform(final ItemDisplayContext transformType, final PoseStack poseStack, final boolean applyLeftHandTransform)
        {
            return new OverlaidBakedModel(originalModel.applyTransform(transformType, poseStack, applyLeftHandTransform));
        }
    }
    */
}
