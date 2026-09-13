package com.ldtteam.structurize.blueprints.v1;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Guards {@link BlueprintUtil#fixTileEntities} against block entity ids the vanilla data fixer cannot carry all the
 * way to the current data version.
 * <p>
 * The interesting cases are the ids that stopped being block entities at some point in vanilla's history. A bare
 * {@code References.BLOCK_ENTITY} update has no rule for them - the vanilla fixes that deal with them hang off
 * {@code CHUNK}/{@code ITEM_STACK}/{@code ENTITY}/{@code STRUCTURE} instead - so the data fixer either throws
 * ({@code flower_pot}, {@code noteblock}) or logs {@code Unsupported key: ...} at ERROR and hands the tag back
 * unfixed ({@code bed}). Blueprints are read on a background IO worker, so both failure modes are quiet. All three
 * are dropped here instead.
 */
public class BlueprintUtilTileEntityFixTest
{
    /** 1.16.5, the oldest data version any blueprint shipped with this repository carries. */
    private static final int V1_16_5 = 2586;

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
        assertNull(BlueprintUtil.fixTileEntities(V1_16_5, listOf("minecraft:bed"))[0]);
        assertNull(BlueprintUtil.fixTileEntities(V1_21_1, listOf("minecraft:bed"))[0]);
    }

    /**
     * Flower pots and note blocks stopped being block entities at the flattening, which is the floor
     * {@link BlueprintUtil#MIN_SUPPORTED_DATA_VERSION} now draws, and the 1.12-era cross-fixer that used to turn them
     * back into block states is gone. Nothing can be salvaged from either tag and handing it to the data fixer throws,
     * so they are dropped like the bed above.
     */
    @Test
    public void flowerPotAndNoteBlockAreDropped()
    {
        final CompoundTag[] fixed = BlueprintUtil.fixTileEntities(V1_16_5, listOf("minecraft:flower_pot", "minecraft:noteblock"));
        assertNull(fixed[0]);
        assertNull(fixed[1]);
    }

    /**
     * Blueprints below {@link BlueprintUtil#MIN_SUPPORTED_DATA_VERSION} are refused outright rather than migrated, and
     * refusal means a null return, not an exception escaping into whatever thread is reading the file.
     */
    @Test
    public void blueprintsOlderThanTheFloorAreRefused()
    {
        assertTrue(BlueprintUtil.isTooOldToLoad(blueprintTaggedWith(1343)));
        assertTrue(BlueprintUtil.isTooOldToLoad(blueprintTaggedWith(1465)));
        assertTrue(BlueprintUtil.isTooOldToLoad(blueprintTaggedWith(BlueprintUtil.MIN_SUPPORTED_DATA_VERSION - 1)));
        assertFalse(BlueprintUtil.isTooOldToLoad(blueprintTaggedWith(BlueprintUtil.MIN_SUPPORTED_DATA_VERSION)));
        assertFalse(BlueprintUtil.isTooOldToLoad(blueprintTaggedWith(V1_16_5)));
        assertFalse(BlueprintUtil.isTooOldToLoad(blueprintTaggedWith(V1_21_1)));

        // No "mcversion" tag at all means the file predates the tag, i.e. 1.12-era.
        assertTrue(BlueprintUtil.isTooOldToLoad(new CompoundTag()));

        assertNull(BlueprintUtil.readBlueprintFromNBT(blueprintTaggedWith(1343), null, "too_old.blueprint"));
    }

    private static CompoundTag blueprintTaggedWith(final int dataVersion)
    {
        final CompoundTag tag = new CompoundTag();
        tag.putByte("version", (byte) 1);
        tag.putInt("mcversion", dataVersion);
        return tag;
    }

    /**
     * Ordinary block entities still go through the data fixer and come out intact, so the special cases above did not
     * swallow the general path.
     */
    @Test
    public void ordinaryBlockEntitiesStillSurvive()
    {
        final CompoundTag[] fixed = BlueprintUtil.fixTileEntities(V1_16_5, listOf("minecraft:chest", "minecraft:sign", "minecraft:furnace"));
        assertEquals("minecraft:chest", fixed[0].getStringOr("id", ""));
        assertEquals("minecraft:sign", fixed[1].getStringOr("id", ""));
        assertEquals("minecraft:furnace", fixed[2].getStringOr("id", ""));
    }
}
