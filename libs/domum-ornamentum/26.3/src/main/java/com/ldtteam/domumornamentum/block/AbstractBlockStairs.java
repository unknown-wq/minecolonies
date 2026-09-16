package com.ldtteam.domumornamentum.block;

import com.ldtteam.domumornamentum.block.interfaces.IDOBlock;
import net.minecraft.resources.Identifier;

public abstract class AbstractBlockStairs<B extends AbstractBlockStairs<B>> extends DOStairBlock implements IDOBlock<B>
{
    public AbstractBlockStairs(final Properties properties)
    {
        super(properties);
    }

    @Override
    public Identifier getRegistryName()
    {
        return getRegistryName(this);
    }
}
