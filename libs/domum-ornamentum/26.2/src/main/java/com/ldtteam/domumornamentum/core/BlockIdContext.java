package com.ldtteam.domumornamentum.core;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

/**
 * Registration-time carrier for the {@link ResourceKey} of the block that is currently being constructed.
 *
 * <p>Background (port 26.1 NeoForge → 26.2 Fabric): since 1.21.4 {@code BlockBehaviour.Properties} must carry
 * the block's {@code ResourceKey} <em>before</em> the {@code BlockBehaviour} constructor runs — it derives the
 * loot-table key and the description id from it eagerly and throws
 * {@code NullPointerException: Block id not set} otherwise
 * ({@code /opt/mc-src/net/minecraft/world/level/block/state/BlockBehaviour.java:1155,1289}).
 * NeoForge patched {@code DeferredRegister.Blocks} to stamp the id for you. Every Domum Ornamentum block
 * builds its own {@code Properties} inside its own no-arg constructor (57 classes, 13 abstract roots), so there
 * is no call site that could pass a pre-stamped {@code Properties} in without rewriting all of them.</p>
 *
 * <p>Instead {@code ModBlocks} publishes the pending key here around the block factory call and
 * {@code com.ldtteam.domumornamentum.mixin.BlockBehaviourPropertiesMixin} stamps every {@code Properties}
 * instance created inside that window. Vanilla is unaffected: {@code Blocks.register} calls
 * {@code properties.setId(key)} itself afterwards
 * ({@code /opt/mc-src/net/minecraft/world/level/block/Blocks.java:5693}), and the window is only ever open on
 * the mod-initialisation thread, long after bootstrap.</p>
 *
 * <p>This class must stay free of static initialisers with side effects: the mixin touches it very early, and
 * anything that would drag {@code ModBlocks} in would register blocks before the registries are ready.</p>
 */
public final class BlockIdContext
{
    private static ResourceKey<Block> pending;

    private BlockIdContext()
    {
    }

    public static void set(final ResourceKey<Block> key)
    {
        pending = key;
    }

    public static ResourceKey<Block> get()
    {
        return pending;
    }

    public static void clear()
    {
        pending = null;
    }
}
