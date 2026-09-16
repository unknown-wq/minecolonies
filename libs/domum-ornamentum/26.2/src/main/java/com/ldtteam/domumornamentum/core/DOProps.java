package com.ldtteam.domumornamentum.core;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The single place where Domum Ornamentum blocks build their {@link BlockBehaviour.Properties}.
 *
 * <p>Since 1.21.4 a {@code Properties} instance must already carry the block's {@link ResourceKey} when the
 * {@code BlockBehaviour} constructor runs, and every DO block builds its own properties inside its own no-arg
 * constructor, so there is no call site that could pass a pre-stamped instance in. {@code ModBlocks} publishes
 * the key of the block it is about to construct to {@link BlockIdContext}; these factories read it back and
 * stamp it. See {@link BlockIdContext} for the full story.</p>
 *
 * <p>Every DO block constructor must go through here. A plain {@code BlockBehaviour.Properties.of()} added
 * later somewhere else will not be stamped and the block will fail at registration with
 * {@code NullPointerException: Block id not set} — loudly, at startup, which is the intended failure mode.</p>
 */
public final class DOProps
{
    private DOProps()
    {
    }

    /**
     * Fresh properties for the block currently being registered.
     *
     * @return the properties, carrying the pending block id
     */
    public static BlockBehaviour.Properties of()
    {
        return stampPendingId(BlockBehaviour.Properties.of());
    }

    /**
     * Properties legacy-copied from another block, for the block currently being registered.
     * {@code ofLegacyCopy} does not copy the source block's id, so stamping afterwards is safe.
     *
     * @param source the block to copy the behaviour of
     * @return the properties, carrying the pending block id
     */
    public static BlockBehaviour.Properties ofLegacyCopy(final BlockBehaviour source)
    {
        return stampPendingId(BlockBehaviour.Properties.ofLegacyCopy(source));
    }

    private static BlockBehaviour.Properties stampPendingId(final BlockBehaviour.Properties properties)
    {
        final ResourceKey<Block> pending = BlockIdContext.get();
        return pending == null ? properties : properties.setId(pending);
    }
}
