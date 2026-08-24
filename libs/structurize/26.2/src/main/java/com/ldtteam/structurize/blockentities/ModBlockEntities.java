package com.ldtteam.structurize.blockentities;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.blocks.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Block entity types of Structurize.
 *
 * <p>Port note (contract C1): {@code DeferredRegister} / {@code DeferredHolder} are NeoForge-only. 26.2 also
 * dropped {@code BlockEntityType.Builder} — the type is built with the two argument constructor
 * {@code BlockEntityType(BlockEntitySupplier, Set&lt;Block&gt;)}, with no trailing data-fixer argument
 * (/opt/mc-src/net/minecraft/world/level/block/entity/BlockEntityType.java:18).</p>
 */
public final class ModBlockEntities
{
    private ModBlockEntities() { /* prevent construction */ }

    public static final Supplier<BlockEntityType<BlockEntityTagSubstitution>> TAG_SUBSTITUTION;

    /**
     * Forces the static initialiser. Must run after {@link ModBlocks#init()}.
     */
    public static void init()
    {
        // intentionally empty
    }

    static
    {
        final ResourceKey<BlockEntityType<?>> key =
            ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Constants.resLocStruct("tagsubstitution"));
        final BlockEntityType<BlockEntityTagSubstitution> type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            key,
            new BlockEntityType<>(BlockEntityTagSubstitution::new, Set.of(ModBlocks.blockTagSubstitution.get())));
        TAG_SUBSTITUTION = () -> type;
    }
}
