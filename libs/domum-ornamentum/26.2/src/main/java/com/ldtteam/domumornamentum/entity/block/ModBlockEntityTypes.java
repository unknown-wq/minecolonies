package com.ldtteam.domumornamentum.entity.block;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.decorative.DynamicTimberFrameBlock;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class to create the modBlocks.
 * References to the blocks can be made here
 */
public final class ModBlockEntityTypes
{
    public static Supplier<BlockEntityType<BlockEntity>> MATERIALLY_TEXTURED = register(Constants.BlockEntityTypes.MATERIALLY_RETEXTURABLE,
      MateriallyTexturedBlockEntity::new,
      IMateriallyTexturedBlock.class);

    public static Supplier<BlockEntityType<BlockEntity>> DYNAMIC_TIMBERFRAME = register(Constants.BlockEntityTypes.DYNAMIC_TIMBERFRAME,
      DynamicTimberFrameBlockEntity::new,
      DynamicTimberFrameBlock.class);

    /**
     * Class-load hook. Registration happens eagerly in the static initialisers above (contract C1) and
     * therefore has to run <em>after</em> {@code ModBlocks.init()}: the valid-block sets are snapshots of
     * {@link BuiltInRegistries#BLOCK} taken at construction time.
     */
    public static void init()
    {
    }

    /**
     * 26.2 dropped {@code BlockEntityType.Builder} and the trailing data-fixer argument; the type is built with
     * the two-argument constructor {@code BlockEntityType(BlockEntitySupplier, Set<Block>)}
     * ({@code /opt/mc-src/net/minecraft/world/level/block/entity/BlockEntityType.java:18}).
     */
    private static Supplier<BlockEntityType<BlockEntity>> register(final Identifier id,
        final BlockEntityType.BlockEntitySupplier<BlockEntity> factory,
        final Class<?> validBlockType)
    {
        final Set<Block> validBlocks = BuiltInRegistries.BLOCK.stream()
            .filter(validBlockType::isInstance)
            .collect(Collectors.toSet());

        final BlockEntityType<BlockEntity> value =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, new BlockEntityType<BlockEntity>(factory, validBlocks));
        return () -> value;
    }

    /**
     * Private constructor to hide the implicit public one.
     */
    private ModBlockEntityTypes()
    {
    }
}
