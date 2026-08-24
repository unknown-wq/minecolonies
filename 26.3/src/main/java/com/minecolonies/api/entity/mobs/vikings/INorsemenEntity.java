package com.minecolonies.api.entity.mobs.vikings;

import com.minecolonies.api.util.IItemHandlerCapProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import com.minecolonies.api.inventory.api.IItemHandler;
import org.jetbrains.annotations.Nullable;

public interface INorsemenEntity extends Enemy, IItemHandlerCapProvider
{
    @Override
    @Nullable
    default IItemHandler getItemHandlerCap(final Direction direction)
    {
        // TODO(port-26.2): DISABLED — NeoForge registered an item-handler capability on every LivingEntity;
        // Fabric has no equivalent (contract C4) and vanilla raiders own no Container, so there is nothing to expose.
        // Raider inventories are therefore not readable through this provider.
        return null;
    }
}
