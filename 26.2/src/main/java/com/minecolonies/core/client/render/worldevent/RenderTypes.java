package com.minecolonies.core.client.render.worldevent;

import com.minecolonies.api.util.constant.Constants;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * Render types owned by MineColonies.
 * <p>
 * PORT-26.2: {@code RenderStateShard} and {@code RenderType.CompositeState} are gone; a render type is now
 * {@code RenderType.create(name, RenderSetup)} around a Blaze3D {@link RenderPipeline}, and every former shard is a
 * pipeline property. The old {@code AlwaysDepthTestStateShard} borrowed from Structurize became
 * {@link CompareOp#ALWAYS_PASS}. This mirrors the shape Structurize itself uses in
 * {@code WorldRenderMacros.RenderTypes}.
 */
public class RenderTypes
{
    /**
     * Pipeline for the citizen status icons: entity format, emissive (no lightmap), translucent and always passing the
     * depth test, so the icon stays readable through terrain the way it did on 1.21.1.
     */
    private static final RenderPipeline WORLD_ENTITY_ICON_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pipeline/world_entity_icon"))
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .withShaderDefine("PER_FACE_LIGHTING")
        .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withCull(false)
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build());

    private static final Function<Identifier, RenderType> WORLD_ENTITY_ICON = Util.memoize(texture ->
      RenderType.create("minecolonies_entity_icon",
        RenderSetup.builder(WORLD_ENTITY_ICON_PIPELINE)
          .withTexture("Sampler0", texture)
          .useOverlay()
          .createRenderSetup()));

    /**
     * Usable for rendering simple flat textures.
     *
     * @param resLoc texture location
     * @return render type
     */
    public static RenderType worldEntityIcon(final Identifier resLoc)
    {
        return WORLD_ENTITY_ICON.apply(resLoc);
    }
}
