package com.minecolonies.core.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Class handling the Scimitar item.
 */
public class ItemIronScimitar extends Item
{
    /**
     * Constructor method for the Scimitar Item
     *
     * @param properties the properties.
     */
    public ItemIronScimitar(final Item.Properties properties)
    {
        // 26.2 removed SwordItem/Tiers -- see ItemChiefSword for the same migration.
        super(ToolMaterial.IRON.applySwordProperties(properties, 3, -2.4F));
    }
}
