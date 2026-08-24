package com.ldtteam.domumornamentum.client.event.handlers;

/**
 * TODO(port-26.2): DISABLED — both halves of {@code RegisterColorHandlersEvent} lost their target API.
 *
 * <ul>
 *   <li><b>Item half:</b> {@code net.minecraft.client.color.item.ItemColor} was removed from vanilla; item
 *       tints are declared as {@code ItemTintSource}s in the item model JSON
 *       ({@code /opt/mc-src/net/minecraft/client/color/item/ItemTintSources.java}). There is no code-side
 *       registration on Fabric.</li>
 *   <li><b>Block half:</b> {@code net.minecraft.client.color.block.BlockColor} was removed too. The Fabric
 *       replacement is {@code BlockColorRegistry.register(List&lt;BlockTintSource&gt;, Block...)}
 *       ({@code fabric-rendering-v1}), but a {@code BlockTintSource} only sees
 *       {@code (BlockState, BlockAndTintGetter, BlockPos)} and is picked by a bounds-checked <em>layer
 *       index</em> ({@code BlockColors#getTintSource}). The mod's mechanism - packing
 *       {@code (Block.getId(materialState) &lt;&lt; 8) | tintIndex} into the quad's tint index - cannot survive
 *       that.</li>
 * </ul>
 *
 * <p>No functionality is lost for placed blocks: the material tint is resolved and multiplied into the vertex
 * colours while the quads are emitted, in
 * {@code com.ldtteam.domumornamentum.client.model.utils.ModelSpriteQuadTransformer#retexture} via
 * {@code com.ldtteam.domumornamentum.client.color.MateriallyTexturedBlockBlockColor#getColor}, which still
 * honours {@code IMateriallyTexturedBlock#usesWorldSpecificTinting()}. Item stacks lose their tint - see
 * {@code MateriallyTexturedBlockItemColor} for the item-side gap.
 *
 * <p>Original NeoForge implementation:
 * <pre>
 * &#64;SubscribeEvent
 * public static void onRegisterColorHandlersItem(RegisterColorHandlersEvent.Item event) {
 *     event.register(new MateriallyTexturedBlockItemColor(), ModBlocks.getMateriallyTexturableItems());
 * }
 *
 * &#64;SubscribeEvent
 * public static void onRegisterColorHandlersBlock(RegisterColorHandlersEvent.Block event) {
 *     event.register(new MateriallyTexturedBlockBlockColor(), ModBlocks.getMateriallyTexturableBlocks());
 * }
 * </pre>
 */
public final class RegisterColorHandlersEventHandler
{
    private RegisterColorHandlersEventHandler()
    {
        throw new IllegalStateException(
          "RegisterColorHandlersEventHandler is disabled on 26.2; BlockColor/ItemColor no longer exist.");
    }

    /**
     * Disabled no-op (see the class javadoc). Kept so a future revival has a registration point.
     */
    public static void register()
    {
        // no-op
    }
}
