package com.unknownwq.worldmap.colony;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * One colony as it is written to disk and read back: everything about it that is worth surviving a restart,
 * and nothing that is not.
 *
 * <p>Separate from {@link ColonySnapshot.ColonyShape} on purpose, in both directions. This record carries
 * things the screen has no use for -- an item's registry id rather than an {@code ItemStack}, a field's
 * registry path rather than a translated label -- because those are the forms that survive a write, a
 * language change and a mod update. It leaves out things that are true only right now: whether a raid is
 * running, who is working in which hut, where the graves are. A remembered colony is not claimed to be
 * doing anything; it is claimed to have been there.</p>
 *
 * <p>It also has no MineColonies type in it, for the same reason nothing else outside
 * {@code com.unknownwq.worldmap.colony.minecolonies} does: {@link ColonyStore} reads and writes these on an
 * installation where those classes may not exist at all.</p>
 *
 * @param id           the colony id, which is what a record is keyed by.
 * @param name         its name when it was last seen.
 * @param citizens     its citizen count when it was last seen, or -1 if that was never known.
 * @param colour       0xRRGGBB team colour.
 * @param own          whether the client's player was a member of it.
 * @param hostile      whether it was flagged hostile. A durable property, unlike a raid.
 * @param centre       the colony centre.
 * @param chunks       the claimed chunks, packed as {@link ChunkOutline#pack}.
 * @param lastSeen     epoch milliseconds of the last live sighting.
 * @param huts         the hut list, empty when it was never known.
 * @param fields       the field list, empty when it was never known.
 * @param patrols      the guard patrol routes, empty when none were known.
 * @param raiderSpawns the last recorded raider spawn points, empty when none were known.
 * @param lastRaidTime the world game time the raid that produced those points started at, or
 *                     {@link #NO_RAID_TIME}. A game time and not a wall clock, because that is the number the
 *                     colony itself stamps the raid with and it stays comparable across sessions: the world's
 *                     game time only ever goes up, so a point remembered three sessions ago still dates
 *                     correctly against the clock of the session reading it.
 */
@Environment(EnvType.CLIENT)
public record ColonyMemory(
  int id,
  String name,
  int citizens,
  int colour,
  boolean own,
  boolean hostile,
  BlockPos centre,
  long[] chunks,
  long lastSeen,
  List<Hut> huts,
  List<Field> fields,
  List<Patrol> patrols,
  List<BlockPos> raiderSpawns,
  long lastRaidTime)
{
    /**
     * {@link #lastRaidTime} when the raid behind the remembered spawn points carried no time: the colony has never
     * been raided, or the record was written before the time was carried at all.
     */
    public static final long NO_RAID_TIME = Long.MIN_VALUE;

    /**
     * One hut.
     *
     * @param pos               the hut block.
     * @param itemId            the hut block's item, as {@code namespace:path}, so the icon can be looked
     *                          up out of the item registry on the way back in. Empty when the building type
     *                          had no block.
     * @param name              the display name it had when it was seen.
     * @param level             its level then.
     * @param maxLevel          the type's maximum level.
     * @param underConstruction whether a work order was open on it then.
     */
    public record Hut(BlockPos pos, String itemId, String name, int level, int maxLevel, boolean underConstruction)
    {
    }

    /**
     * One field.
     *
     * @param pos      the field block.
     * @param taken    whether a worker was assigned to it.
     * @param type     the extension's registry path.
     * @param building the hut that owned it, or null.
     */
    public record Field(BlockPos pos, boolean taken, String type, BlockPos building)
    {
    }

    /**
     * One guard tower's manual patrol route.
     *
     * @param tower  the guard tower.
     * @param points the patrol targets in order.
     */
    public record Patrol(BlockPos tower, List<BlockPos> points)
    {
    }
}
