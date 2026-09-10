package com.ldtteam.domumornamentum.datagen.post;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class PostComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        /*
          Exactly as others.  FUTURE, would like to allow the cutter to make slabs with vanilla materials, so those can also be placed sideways
         */
        sink.tag(ModTags.POST_MATERIALS)

            .addTags(
                    ModTags.GLOBAL_DEFAULT,
                    BlockTags.PLANKS
            );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Post Tag Provider";
    }
}
