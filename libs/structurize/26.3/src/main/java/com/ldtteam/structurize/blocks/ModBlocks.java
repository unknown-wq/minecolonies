package com.ldtteam.structurize.blocks;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blocks.schematic.BlockFluidSubstitution;
import com.ldtteam.structurize.blocks.schematic.BlockSolidSubstitution;
import com.ldtteam.structurize.blocks.schematic.BlockSubstitution;
import com.ldtteam.structurize.blocks.schematic.BlockTagSubstitution;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Class to register blocks to Structurize.
 *
 * <p>Port note (contract C1): NeoForge's {@code DeferredRegister.Blocks} / {@code DeferredBlock} do not exist
 * on Fabric. The fields keep their {@link Supplier} shape so every {@code .get()} in the mod is untouched;
 * only the registration itself moved to {@link Registry#register}. Since 1.21.4 a block's
 * {@link BlockBehaviour.Properties} must already carry its {@link ResourceKey} when the {@code Block}
 * constructor runs, so the four schematic blocks take their properties as a constructor argument instead of
 * building them inline — that is why {@link #register} stamps the key with {@code setId} before calling the
 * factory.</p>
 */
public final class ModBlocks
{
    private ModBlocks() { /* prevent construction */ }

    public static final TagKey<Block> NULL_PLACEMENT = TagKey.create(Registries.BLOCK, Constants.resLocStruct("null_placement"));

    public static final Supplier<BlockSubstitution>      blockSubstitution;
    public static final Supplier<BlockSolidSubstitution> blockSolidSubstitution;
    public static final Supplier<BlockFluidSubstitution> blockFluidSubstitution;
    public static final Supplier<BlockTagSubstitution>   blockTagSubstitution;

    /**
     * Registers a block.
     *
     * @param name       the registry path of the block.
     * @param factory    a factory taking the id-stamped properties.
     * @param properties the properties of the block, without the id.
     * @param <B>        the block subclass for the factory response.
     * @return a supplier of the registered block.
     */
    public static <B extends Block> Supplier<B> register(final String name,
        final Function<BlockBehaviour.Properties, B> factory,
        final BlockBehaviour.Properties properties)
    {
        final ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Constants.resLocStruct(name));
        final B block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        return () -> block;
    }

    /**
     * Registers a block together with a plain {@link BlockItem} of the same name.
     *
     * @param name       the registry path of the block.
     * @param factory    a factory taking the id-stamped properties.
     * @param properties the properties of the block, without the id.
     * @param <B>        the block subclass for the factory response.
     * @return a supplier of the registered block.
     */
    public static <B extends Block> Supplier<B> registerWithBlockItem(final String name,
        final Function<BlockBehaviour.Properties, B> factory,
        final BlockBehaviour.Properties properties)
    {
        final Supplier<B> registered = register(name, factory, properties);
        final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Constants.resLocStruct(name));
        Registry.register(BuiltInRegistries.ITEM,
            itemKey,
            new BlockItem(registered.get(), new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
        return registered;
    }

    /**
     * Forces the static initialiser. Called from the mod initializer.
     */
    public static void init()
    {
        // intentionally empty
    }

    /*
     *  Registration
     */

    static
    {
        blockSubstitution      = registerWithBlockItem("blocksubstitution",
            BlockSubstitution::new,
            // don't kill farmland and path blocks underneath
            BlockSubstitution.defaultSubstitutionProperties().forceSolidOff());
        blockSolidSubstitution = registerWithBlockItem("blocksolidsubstitution",
            BlockSolidSubstitution::new,
            BlockSubstitution.defaultSubstitutionProperties());
        blockFluidSubstitution = registerWithBlockItem("blockfluidsubstitution",
            BlockFluidSubstitution::new,
            BlockSubstitution.defaultSubstitutionProperties());
        blockTagSubstitution   = register("blocktagsubstitution",
            BlockTagSubstitution::new,
            BlockSubstitution.defaultSubstitutionProperties().forceSolidOff());
    }
}
