package com.minecolonies.api.client.render.modeltype;

import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.NotNull;

/**
 * Amazon model.
 */
public class AmazonModel<T extends AbstractEntityMinecoloniesMonster> extends HumanoidModel<RaiderRenderState>
{
    public AmazonModel(final ModelPart part)
    {
        super(part);
    }

    @Override
    public void setupAnim(@NotNull final RaiderRenderState state)
    {
        // 26.2 animates from a render state instead of the entity (HumanoidModel.java:200).
        super.setupAnim(state);
        head.y -= 3;
        rightLeg.y -= 3.5;
        leftLeg.y -= 3.5;
        rightArm.y -= 2;
        leftArm.y -= 2;
    }
}
