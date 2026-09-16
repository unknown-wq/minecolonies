package com.ldtteam.structurize.blueprints.v1;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Guards the entity list a blueprint is written with against nulls.
 * <p>
 * A null lands in {@link Blueprint#getEntities()} from two places: {@link BlueprintUtil#fixEntities} when an entity
 * cannot be read, and the rotation path when an entity cannot be transformed. {@link ListTag} does not reject a null
 * element on add, but writing the list walks every element to work out its type, so a single null turns saving the
 * blueprint into a {@link NullPointerException} - on whichever thread happens to be saving.
 */
public class BlueprintEntityWriteTest
{
    @BeforeClass
    public static void bootstrapMinecraft()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Blueprint oneAirBlock()
    {
        final List<BlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());

        final List<String> requiredMods = new ArrayList<>();
        requiredMods.add("minecraft");

        return new Blueprint((short) 1, (short) 1, (short) 1, (short) 1, palette,
            new short[1][1][1], new CompoundTag[0], requiredMods, null);
    }

    private static CompoundTag entity(final String id)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        return tag;
    }

    /**
     * The null has to be dropped, and what is left has to survive an actual write - asserting on the list alone would
     * not catch it, because the list accepts the null and only fails when it is serialised.
     */
    @Test
    public void nullEntitiesAreNotWritten() throws IOException
    {
        final Blueprint blueprint = oneAirBlock();
        blueprint.setEntities(new CompoundTag[] {entity("minecraft:pig"), null, entity("minecraft:cow")});

        final CompoundTag written = BlueprintUtil.writeBlueprintToNBT(blueprint);
        NbtIo.writeCompressed(written, new ByteArrayOutputStream());

        assertEquals(2, ((ListTag) written.get("entities")).size());
    }

    /**
     * An all-null array is the degenerate case of the same thing: an empty list, not a crash.
     */
    @Test
    public void onlyNullEntitiesWriteAsAnEmptyList() throws IOException
    {
        final Blueprint blueprint = oneAirBlock();
        blueprint.setEntities(new CompoundTag[] {null, null});

        final CompoundTag written = BlueprintUtil.writeBlueprintToNBT(blueprint);
        NbtIo.writeCompressed(written, new ByteArrayOutputStream());

        assertEquals(0, ((ListTag) written.get("entities")).size());
    }
}
