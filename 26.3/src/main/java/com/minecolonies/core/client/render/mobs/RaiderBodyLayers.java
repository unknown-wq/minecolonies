package com.minecolonies.core.client.render.mobs;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * Body meshes the raider renderers used to borrow from {@code ModelLayers.PLAYER_INNER_ARMOR} /
 * {@code ModelLayers.PLAYER_OUTER_ARMOR}.
 * <p>
 * PORT-26.2: both constants are gone. 26.2 registers armour per equipment slot ({@code ModelLayers.PLAYER_ARMOR}, an
 * {@code ArmorModelSet}) and each entry keeps only the parts of that slot, so none of them is a whole humanoid any
 * more. The meshes are recreated here with exactly the deformations the old layers had (0.5 inner / 1.0 outer at
 * 64x32) and baked directly through {@link LayerDefinition#bakeRoot()}, which needs no layer registration at all.
 */
@Environment(EnvType.CLIENT)
public final class RaiderBodyLayers
{
    private static final LayerDefinition INNER =
      LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F), 64, 32);

    private static final LayerDefinition OUTER =
      LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F), 64, 32);

    private RaiderBodyLayers()
    {
        // utility
    }

    /**
     * @return a freshly baked root of the former {@code PLAYER_INNER_ARMOR} mesh.
     */
    public static ModelPart innerBody()
    {
        return INNER.bakeRoot();
    }

    /**
     * @return a freshly baked root of the former {@code PLAYER_OUTER_ARMOR} mesh.
     */
    public static ModelPart outerBody()
    {
        return OUTER.bakeRoot();
    }
}
