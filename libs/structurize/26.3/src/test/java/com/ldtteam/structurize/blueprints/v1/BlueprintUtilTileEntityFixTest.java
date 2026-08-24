package com.ldtteam.structurize.blueprints.v1;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Guards {@link BlueprintUtil#fixTileEntities} against block entity ids the vanilla data fixer cannot carry all the
 * way to the current data version.
 * <p>
 * The interesting cases are the ids that stopped being block entities at some point in vanilla's history. A bare
 * {@code References.BLOCK_ENTITY} update has no rule for them - the vanilla fixes that deal with them hang off
 * {@code CHUNK}/{@code ITEM_STACK}/{@code ENTITY}/{@code STRUCTURE} instead - so the data fixer either throws
 * ({@code flower_pot}, {@code noteblock}) or logs {@code Unsupported key: ...} at ERROR and hands the tag back
 * unfixed ({@code bed}). Blueprints are read on a background IO worker, so both failure modes are quiet.
 */
public class BlueprintUtilTileEntityFixTest
{
    /** 1.12.2, the oldest data version blueprints in the wild carry. */
    private static final int V1_12_2 = 1343;

    /** 1.21.1, i.e. long after beds got a block entity and well before 4885 took it away again. */
    private static final int V1_21_1 = 3955;

    @BeforeClass
    public static void bootstrapMinecraft()
    {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ListTag listOf(final String... ids)
    {
        final ListTag list = new ListTag();
        for (final String id : ids)
        {
            final CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            tag.putInt("x", 1);
            tag.putInt("y", 2);
            tag.putInt("z", 3);
            list.add(tag);
        }
        return list;
    }

    /**
     * Beds lost their block entity at data version 4885, which is inside the range this version fixes over. The tag
     * has to be dropped: nothing in 26.2 can load it, and handing it to the data fixer only produces an ERROR line.
     */
    @Test
    public void bedBlockEntityIsDropped()
    {
        assertNull(BlueprintUtil.fixTileEntities(V1_12_2, listOf("minecraft:bed"))[0]);
        assertNull(BlueprintUtil.fixTileEntities(V1_21_1, listOf("minecraft:bed"))[0]);
    }

    /**
     * The two ids that lost their block entity back at 1.13 are kept, not dropped - {@link BlueprintUtil#fixCross1343}
     * turns them into block states later and needs the tag to do it.
     */
    @Test
    public void flowerPotAndNoteBlockAreKeptForTheCrossFixer()
    {
        final CompoundTag[] fixed = BlueprintUtil.fixTileEntities(V1_12_2, listOf("minecraft:flower_pot", "minecraft:noteblock"));
        assertNotNull(fixed[0]);
        assertNotNull(fixed[1]);
        assertEquals("minecraft:flower_pot", fixed[0].getStringOr("id", ""));
        assertEquals("minecraft:noteblock", fixed[1].getStringOr("id", ""));
    }

    /**
     * Ordinary block entities still go through the data fixer and come out intact, so the special cases above did not
     * swallow the general path.
     */
    @Test
    public void ordinaryBlockEntitiesStillSurvive()
    {
        final CompoundTag[] fixed = BlueprintUtil.fixTileEntities(V1_12_2, listOf("minecraft:chest", "minecraft:sign", "minecraft:furnace"));
        assertEquals("minecraft:chest", fixed[0].getStringOr("id", ""));
        assertEquals("minecraft:sign", fixed[1].getStringOr("id", ""));
        assertEquals("minecraft:furnace", fixed[2].getStringOr("id", ""));
    }
}
