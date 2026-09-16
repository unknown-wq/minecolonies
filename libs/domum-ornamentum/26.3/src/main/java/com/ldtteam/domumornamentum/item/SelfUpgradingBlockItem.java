package com.ldtteam.domumornamentum.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem for Domum Ornamentum blocks that once carried their own data fixer.
 * <p>
 * NOTE(port-26.2): this class was built around NeoForge's {@code IItemExtension#verifyComponentsAfterLoad(ItemStack)}
 * hook, which called an {@code upgrade(ItemStack)} that migrated pre-1.21 DO item NBT
 * ({@code Type} on the root tag, {@code textureData} on the root / block-entity tag) onto the
 * data-component layout. Vanilla 26.3 has no such hook and {@code FabricItem} does not add one, so the
 * migration never ran; re-attaching it would mean mixing into {@code ItemStack}, which this project does
 * not do. The migration code has been removed — items saved by the NeoForge build before 1.21 keep their
 * legacy NBT and come back untextured. See git history for the implementation.
 */
public class SelfUpgradingBlockItem extends BlockItem
{
    public SelfUpgradingBlockItem(final Block block, final Properties properties)
    {
        super(block, properties);
    }
}
