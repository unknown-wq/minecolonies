package com.minecolonies.api.blocks.interfaces;

import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public interface IBlockMinecolonies<B extends IBlockMinecolonies<B>>
{
    /**
     * Registery block at gameregistry.
     *
     * @param registry the registry to use.
     * @return the block itself.
     */
    B registerBlock(final Registry<Block> registry);

    /**
     * Registery block at gameregistry.
     *
     * @param registry   the registry to use.
     * @param properties the item properties.
     */
    void registerBlockItem(final Registry<Item> registry, final Item.Properties properties);

    /**
     * Get the registry name of the block.
     * @return the registry name.
     */
    Identifier getRegistryName();

    /**
     * Build the block registry key for a path inside the minecolonies namespace.
     * <p>
     * Port note (26.2): since 1.21.4 {@code BlockBehaviour.Properties} must already carry the block's
     * {@link ResourceKey} when the {@code Block} constructor runs -- the loot table key and the description id
     * are derived from it eagerly and it throws {@code NullPointerException: Block id not set} otherwise
     * (see {@code BlockBehaviour#effectiveDrops} / {@code #effectiveDescriptionId}). Our blocks are constructed
     * before they are registered, so the name has to travel into the constructor instead.
     *
     * @param path the registry path, identical to what {@link #getRegistryName()} returns for that block.
     * @return the block registry key.
     */
    static ResourceKey<Block> blockId(final String path)
    {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Constants.MOD_ID, path));
    }

    /**
     * Stamp the mandatory block id onto block properties, see {@link #blockId(String)}.
     *
     * @param properties the properties to stamp.
     * @param path       the registry path of the block, identical to {@link #getRegistryName()}.
     * @return the same properties instance.
     */
    static BlockBehaviour.Properties withBlockId(final BlockBehaviour.Properties properties, final String path)
    {
        return properties.setId(blockId(path));
    }

    /**
     * Stamp the mandatory item id onto the properties of a block item. {@code Item.Properties} has the very same
     * requirement as {@code BlockBehaviour.Properties}: {@code Item#<init>} resolves the description id and the
     * item model from the key and throws {@code Item id not set} when it is missing.
     * <p>
     * {@code useBlockDescriptionPrefix()} is part of the same 26.2 change: {@code BlockItem} no longer borrows the
     * description id from its block, it takes whatever the properties say, and the default is
     * {@code item.<namespace>.<path>}. Vanilla applies the prefix in {@code Items#registerBlock}
     * ({@code /opt/mc-src/net/minecraft/world/item/Items.java:2076}); we have to do the same or every block item
     * would look for a translation key our language files do not have (they all use {@code block.minecolonies.*}).
     *
     * @param properties   the properties to stamp.
     * @param registryName the registry name the item is about to be registered under.
     * @return the same properties instance.
     */
    static Item.Properties withBlockItemId(final Item.Properties properties, final Identifier registryName)
    {
        return properties.useBlockDescriptionPrefix().setId(ResourceKey.create(Registries.ITEM, registryName));
    }
}
