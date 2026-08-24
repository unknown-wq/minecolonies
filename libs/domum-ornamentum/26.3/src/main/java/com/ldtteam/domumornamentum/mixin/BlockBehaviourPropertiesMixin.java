package com.ldtteam.domumornamentum.mixin;

import com.ldtteam.domumornamentum.core.BlockIdContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stamps the pending block {@link ResourceKey} onto every {@code BlockBehaviour.Properties} created while
 * {@code ModBlocks} is constructing one of its blocks. See {@link BlockIdContext} for why this is needed.
 */
@Mixin(BlockBehaviour.Properties.class)
public abstract class BlockBehaviourPropertiesMixin
{
    @Inject(method = "<init>", at = @At("RETURN"))
    private void domum_ornamentum$applyPendingId(final CallbackInfo ci)
    {
        final ResourceKey<Block> pending = BlockIdContext.get();
        if (pending != null)
        {
            ((BlockBehaviour.Properties) (Object) this).setId(pending);
        }
    }
}
