package com.ldtteam.common.inventory;

import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * The 36 main slots of a player inventory - hotbar and backpack - and nothing else. Same role as NeoForge's
 * {@code net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper}.
 * <p>
 * This type exists because {@code new InvWrapper(player.getInventory())} is a bug, not a shortcut.
 * {@link Inventory#getContainerSize()} returns the main slots <i>plus</i> the equipment slots, and {@link Inventory}
 * does not override {@code canPlaceItem} - which defaults to true - so a whole-container view happily inserts a
 * milk bottle into the helmet slot and reports success. Slots 0 to 35 are the main inventory,
 * {@link Inventory#getItem} only reaches equipment above that, so the fix is a range and not a validity check.
 */
public class PlayerMainInvWrapper extends InvWrapper
{
    /**
     * @param inventory the player inventory whose main slots to expose
     */
    public PlayerMainInvWrapper(@NotNull final Inventory inventory)
    {
        super(inventory, 0, Inventory.INVENTORY_SIZE);
    }
}
