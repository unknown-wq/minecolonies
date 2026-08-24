package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.constants.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Class to register items to Structurize.
 *
 * <p>Port note (contract C1): {@code DeferredRegister.Items} / {@code DeferredItem} are NeoForge-only, so the
 * fields keep their {@link Supplier} shape and registration moved to {@link Registry#register}.
 * {@link Item.Properties#setId(ResourceKey)} is mandatory in 26.2 — without it item construction throws
 * {@code NullPointerException: Item id not set} — which is why every item constructor now takes its
 * properties instead of building them inline.</p>
 */
public final class ModItems
{
    private ModItems() { /* prevent construction */ }

    /*
     *  Items
     */

    public static final Supplier<ItemBuildTool>       buildTool;
    public static final Supplier<ItemShapeTool>       shapeTool;
    public static final Supplier<ItemScanTool>        scanTool;
    public static final Supplier<ItemTagTool>         tagTool;
    public static final Supplier<ItemCaliper>         caliper;
    public static final Supplier<ItemTagSubstitution> blockTagSubstitution;

    /**
     * Registers one item.
     *
     * @param name       the registry path of the item.
     * @param factory    a factory taking the id-stamped properties.
     * @param properties the properties of the item, without the id.
     * @param <I>        the item subclass for the factory response.
     * @return a supplier of the registered item.
     */
    public static <I extends Item> Supplier<I> register(final String name,
        final Function<Item.Properties, I> factory,
        final Item.Properties properties)
    {
        final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Constants.resLocStruct(name));
        final I item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
        return () -> item;
    }

    /**
     * Forces the static initialiser. Called from the mod initializer.
     */
    public static void init()
    {
        // intentionally empty
    }

    static
    {
        buildTool            = register("sceptergold", ItemBuildTool::new, new Item.Properties().stacksTo(1));
        shapeTool            = register("shapetool", ItemShapeTool::new, new Item.Properties().stacksTo(1));
        scanTool             = register("sceptersteel", ItemScanTool::new, ItemScanTool.defaultProperties());
        tagTool              = register("sceptertag", ItemTagTool::new, ItemTagTool.defaultProperties());
        caliper              = register("caliper", ItemCaliper::new, new Item.Properties().stacksTo(1));
        blockTagSubstitution = register("blocktagsubstitution", ItemTagSubstitution::new, ItemTagSubstitution.defaultProperties());
    }
}
