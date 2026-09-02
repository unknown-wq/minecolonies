package com.ldtteam.domumornamentum.client.model.utils;

import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One resolved texture replacement.
 * <p>
 * 26.2 note: on NeoForge this held a whole {@code BakedQuad} of the replacement model, because the only way
 * to learn a replacement sprite was to bake the target model and read a quad out of it. In 26.2 a baked quad
 * is a {@code record BakedQuad(..., MaterialInfo materialInfo)} and the sprite is reachable directly through
 * {@link net.minecraft.client.resources.model.geometry.BakedQuad#materialInfo()}, so we only keep the
 * resolved {@link Material.Baked} plus the {@link BlockState} the tint has to be computed from.
 *
 * @param material the replacement sprite (plus its force-translucent flag)
 * @param state    the block state the replacement sprite was taken from, used for tinting
 */
public record ModelSpriteQuadTransformerData(Material.Baked material, BlockState state)
{
}
