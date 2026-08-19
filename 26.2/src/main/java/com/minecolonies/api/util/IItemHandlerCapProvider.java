package com.minecolonies.api.util;

import com.minecolonies.api.inventory.api.IItemHandler;
import com.minecolonies.api.inventory.api.InvWrapper;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Our class for to join {@link IItemHandler} providers, so we can have type independent code.
 *
 * <p>Contract C4: NeoForge capabilities do not exist on Fabric, so the three {@code wrap} factories no longer go
 * through {@code Capabilities.ItemHandler.BLOCK/ENTITY/ITEM}. They now resolve the target directly:</p>
 * <ul>
 *   <li>a {@link BlockEntity} that already is an {@code IItemHandlerCapProvider} answers for itself; otherwise, if it
 *       is a vanilla {@link Container}, it is adapted with {@link InvWrapper} (a {@link WorldlyContainer} keeps its
 *       per-face slot filtering);</li>
 *   <li>an {@link Entity} likewise, with the player inventory as the only vanilla case;</li>
 *   <li>an {@link ItemStack} has no vanilla inventory concept at all — that wrapper now always yields {@code null}.</li>
 * </ul>
 * <p>Observable effect: MineColonies no longer sees item inventories exposed by other mods purely through the
 * capability system (it still sees anything that implements vanilla {@code Container}), and bundle-like item
 * inventories are not readable.</p>
 */
@FunctionalInterface
public interface IItemHandlerCapProvider
{
    /**
     * For EntityCap register only
     */
    @Nullable
    default IItemHandler getItemHandlerCap(final Void nothing)
    {
        return getItemHandlerCap();
    }

    /**
     * @return direction-unaware itemHandler
     */
    @Nullable
    default IItemHandler getItemHandlerCap()
    {
        return getItemHandlerCap((Direction) null);
    }

    /**
     * @return direction-aware itemHandler
     */
    @Nullable
    IItemHandler getItemHandlerCap(final Direction direction);

    /**
     * @param blockEntity the block entity to expose.
     * @return a provider for it.
     */
    static IItemHandlerCapProvider wrap(final BlockEntity blockEntity)
    {
        if (blockEntity instanceof final IItemHandlerCapProvider provider)
        {
            return provider;
        }
        if (blockEntity instanceof final Container container)
        {
            final InvWrapper wrapper = new InvWrapper(container);
            return direction -> wrapper;
        }
        return direction -> null;
    }

    /**
     * @param entity the entity to expose.
     * @param sided  kept for call-site compatibility; without capabilities there is no per-face entity inventory.
     * @return a provider for it.
     */
    static IItemHandlerCapProvider wrap(final Entity entity, final boolean sided)
    {
        if (entity instanceof final IItemHandlerCapProvider provider)
        {
            return provider;
        }
        if (entity instanceof final Player player)
        {
            final InvWrapper wrapper = new InvWrapper(player.getInventory());
            return direction -> wrapper;
        }
        if (entity instanceof final Container container)
        {
            final InvWrapper wrapper = new InvWrapper(container);
            return direction -> wrapper;
        }
        return direction -> null;
    }

    /**
     * @param entity the entity to expose.
     * @return a provider for it.
     */
    static IItemHandlerCapProvider wrap(final Entity entity)
    {
        return wrap(entity, false);
    }

    /**
     * @param itemStack the stack; kept for call-site compatibility.
     * @return a provider that never resolves — see the class javadoc.
     */
    static IItemHandlerCapProvider wrap(final ItemStack itemStack)
    {
        return direction -> null;
    }
}
