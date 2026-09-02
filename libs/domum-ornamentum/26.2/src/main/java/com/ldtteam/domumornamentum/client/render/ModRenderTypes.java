package com.ldtteam.domumornamentum.client.render;

/**
 * TODO(port-26.2): DISABLED — the whole {@code RenderStateShard} composite-state system this class was built
 * on was deleted in 26.2.
 *
 * <p>What is gone, all verified by {@code find /opt/mc-src -name '<name>.java'}:
 * <ul>
 *   <li>{@code net.minecraft.client.renderer.RenderStateShard} - no such file. With it went
 *       {@code CompositeState.builder()}, {@code setShaderState}, {@code setLineState},
 *       {@code setDepthTestState}, {@code TextureStateShard}, {@code DepthTestStateShard},
 *       {@code LineStateShard}, {@code RENDERTYPE_LINES_SHADER} and friends.</li>
 *   <li>{@code RenderType.create(String, VertexFormat, VertexFormat.Mode, int, boolean, boolean,
 *       CompositeState)} - the only factory left is
 *       {@code RenderType.create(String name, RenderSetup state)}
 *       ({@code /opt/mc-src/net/minecraft/client/renderer/rendertype/RenderType.java}:41), and
 *       {@code RenderSetup} is built around a Blaze3D {@code RenderPipeline}, not around shards.</li>
 *   <li>Raw GL constants ({@code GL11.GL_LEQUAL}, {@code GL11.GL_GEQUAL}) cannot be fed to anything -
 *       {@code RenderSystem.depthFunc} no longer exists and 26.2 additionally ships a Vulkan backend and a
 *       reversed depth buffer, so per-draw depth-func fiddling is not expressible.</li>
 * </ul>
 *
 * <p>Consequence in game: nothing on its own. The only consumer of this enum was
 * {@link ModelGhostRenderer}, which is disabled for independent reasons.
 *
 * <p>How to fix: rebuild the two types actually used ({@code GHOST_BLOCK_PREVIEW},
 * {@code GHOST_BLOCK_COLORED_PREVIEW}) as custom {@code RenderPipeline}s via
 * {@code net.fabricmc.fabric.api.client.rendering.v1.FabricRenderPipeline.Builder} (fabric-rendering-v1),
 * then wrap them with {@code RenderType.create(name, RenderSetup)}. Copy the shape of a vanilla entry in
 * {@code /opt/mc-src/net/minecraft/client/renderer/RenderPipelines.java} and of
 * {@code /opt/mc-src/net/minecraft/client/renderer/rendertype/RenderTypes.java}. The
 * "translucent cull block sheet" variant needs no custom pipeline at all -
 * {@code Sheets.translucentCullBlockSheet()} still exists.
 *
 * <p>The original NeoForge implementation is preserved in git history and in {@code 26.1/}
 * (read-only source of the port), file {@code client/render/ModRenderTypes.java}.
 */
public final class ModRenderTypes
{
    private ModRenderTypes()
    {
        throw new IllegalStateException(
          "ModRenderTypes is disabled on 26.2; RenderStateShard composite states no longer exist.");
    }
}
