package com.ldtteam.domumornamentum.client.color;

/**
 * TODO(port-26.2): DISABLED — {@code net.minecraft.client.color.item.ItemColor} was removed from vanilla and
 * there is no code-side item tint registration on Fabric 26.2.
 *
 * <p>Item tinting is now data driven: an item model declares a list of {@code ItemTintSource}s in
 * {@code assets/&lt;ns&gt;/items/&lt;item&gt;.json}, resolved through
 * {@code /opt/mc-src/net/minecraft/client/color/item/ItemTintSources.java} (a {@code MapCodec} registry:
 * {@code Constant}, {@code Dye}, {@code GrassColorSource}, {@code MapColor}, {@code Potion}, …). There is no
 * {@code Minecraft#getItemColors()} any more, so the NeoForge trick of packing a block state id into the
 * item's tint index and unpacking it in an {@code ItemColor} cannot be expressed at all.
 *
 * <p>Consequence in game: a Domum Ornamentum item stack in an inventory or in hand is drawn with the base
 * (untinted, un-retextured) item model. Placed blocks are unaffected - those go through
 * {@code MateriallyTexturedBakedModel} and are retextured and tinted correctly.
 *
 * <p>How to fix: wrap the baked {@code ItemModel} via
 * {@code ModelLoadingPlugin.Context#modifyItemModelAfterBake()} (see
 * {@code net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBakedItemModel}) and, inside
 * {@code ItemModel#update(ItemStackRenderState, ItemStack, …)}, emit the retextured geometry through the
 * Fabric item hooks ({@code net.fabricmc.fabric.api.client.renderer.v1.render.submit.ExtendedItemSubmit},
 * {@code FabricOrderedSubmitNodeCollector}). The material set is on the stack already -
 * {@code MaterialTextureData.readFromItemStack(stack)} - so no data plumbing is needed, only the geometry
 * emission.
 *
 * <p>Original NeoForge implementation:
 * <pre>
 * public class MateriallyTexturedBlockItemColor implements ItemColor
 * {
 *     private static final int TINT_MASK = 0xff;
 *     private static final int TINT_BITS = 8;
 *
 *     &#64;Override
 *     public int getColor(final ItemStack stack, final int tint)
 *     {
 *         final BlockState state = Block.stateById(tint &gt;&gt; TINT_BITS);
 *         if (state.getBlock() instanceof LiquidBlock) {
 *             return IClientFluidTypeExtensions.of(state.getFluidState().getType()).getTintColor();
 *         }
 *
 *         final ItemStack workingStack = new ItemStack(state.getBlock(), 1);
 *         if (workingStack.getItem() instanceof AirItem)
 *             return 0xffffff;
 *
 *         final Block block = state.getBlock();
 *         final Item itemFromBlock = block.asItem();
 *         int tintValue = tint &amp; TINT_MASK;
 *         return Minecraft.getInstance().getItemColors().getColor(new ItemStack(itemFromBlock, 1), tintValue);
 *     }
 * }
 * </pre>
 */
public final class MateriallyTexturedBlockItemColor
{
    private MateriallyTexturedBlockItemColor()
    {
        throw new IllegalStateException(
          "MateriallyTexturedBlockItemColor is disabled on 26.2; item tints are model-JSON driven.");
    }
}
