package com.ldtteam.structurize.client.gui.util;

import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.api.ItemStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Client-side Item utility class
 */
public class ItemUtil
{
    /**
     * NOT a gap, and the marker that used to sit here was wrong. {@code BucketItem.content} is indeed
     * {@code protected} in 26.2, but 26.2 also carries a public accessor for it,
     * {@code BucketItem#getContent()} ({@code /opt/mc-src/net/minecraft/world/item/BucketItem.java}:159), so
     * neither an access widener nor a substitute test is needed - and {@code util/BlockUtils} (lines 419 and 548)
     * has been calling {@code getContent()} in this very tree all along.
     * <p>
     * The stand-in that was here, {@code getFluidContext() != ClipContext.Fluid.SOURCE_ONLY}, is not equivalent to
     * the test it replaced. It asks "does this bucket raytrace past fluids", which is a different question:
     * vanilla's own {@code MobBucketItem} answers {@code NONE} ({@code MobBucketItem.java}:71) for reasons that
     * have nothing to do with being full, and a modded bucket may override it independently of its contents. This
     * is now literally the 1.21.1 test again.
     *
     * @param item the item to test.
     * @return true when the item is a bucket holding something.
     */
    private static boolean isFilledBucket(final Item item)
    {
        return item instanceof final BucketItem bucket && bucket.getContent() != Fluids.EMPTY;
    }

    /**
     * Creates a list of all items that can be picked
     *
     * @return
     */
    public static List<ItemStack> getAllItems()
    {
        return ImmutableList.copyOf(StreamSupport.stream(Spliterators.spliteratorUnknownSize(BuiltInRegistries.ITEM.iterator(), Spliterator.ORDERED), false)
            .filter(item -> item instanceof AirItem || item instanceof BlockItem || isFilledBucket(item))
            .map(ItemStack::new)
            .collect(Collectors.toList()));
    }

    /**
     * Creates a list of all items that can be picked inlcuding player items
     * Client-side
     *
     * @return
     */
    public static List<ItemStack> getAllItemsInlcudingInventory()
    {
        final Set<ItemStorage> items = new HashSet<>();
        for (final Item item : BuiltInRegistries.ITEM)
        {
            if (item instanceof AirItem || item instanceof BlockItem || isFilledBucket(item))
            {
                items.add(new ItemStorage(new ItemStack(item)));
            }
        }

        for (final ItemStack stack : Minecraft.getInstance().player.getInventory().getNonEquipmentItems())
        {
            final Item item = stack.getItem();
            if (item instanceof AirItem || item instanceof BlockItem || isFilledBucket(item))
            {
                items.add(new ItemStorage(stack.copy()));
            }
        }

        final List<ItemStack> stackList = new ArrayList<>(items.size());

        for (final ItemStorage storage : items)
        {
            stackList.add(storage.getItemStack());
        }

        return stackList;
    }
}
