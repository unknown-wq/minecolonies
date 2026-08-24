package com.minecolonies.api.blocks.interfaces;

import org.jetbrains.annotations.NotNull;

/**
 * Right-clicking this block in the air triggers the building browser window interface.
 */
public interface IBuildingBrowsableBlock
{
    /**
     * Return false if you want to prevent the building search behaviour for some reason.  Client-side only.
     */
    // C5: NeoForge's PlayerInteractEvent.RightClickItem is replaced by Fabric's UseItemCallback, which hands
    // over the player and the stack rather than an event object.
    default boolean shouldBrowseBuildings(@NotNull final net.minecraft.world.entity.player.Player player,
      @NotNull final net.minecraft.world.item.ItemStack stack)
    {
        return true;
    }
}
