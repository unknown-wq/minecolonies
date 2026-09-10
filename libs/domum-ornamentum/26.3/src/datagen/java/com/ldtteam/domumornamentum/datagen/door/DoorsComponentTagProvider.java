package com.ldtteam.domumornamentum.datagen.door;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class DoorsComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.DOORS_MATERIALS)
                .addTags(
                        ModTags.GLOBAL_DEFAULT,
                        BlockTags.PLANKS
                );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Doors Tag Provider";
    }
}
