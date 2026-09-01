package com.unknownwq.worldmap.colony;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Everything the screen draws for MineColonies, in one immutable object with no MineColonies type anywhere
 * in it.
 *
 * <p>That restriction is the whole point of this class and it is not decoration. The screen is compiled and
 * loaded whether or not MineColonies is installed, so no field, parameter or return type it can see may name
 * a {@code com.minecolonies} class -- one such reference in a method signature is enough for the verifier to
 * try to load that class the first time the screen is opened, on an installation that does not have it. The
 * translation from colony objects to this happens entirely inside
 * {@code com.unknownwq.worldmap.colony.minecolonies}, which is the only package that is ever allowed to
 * mention them, and which is never loaded when the mod is absent.</p>
 *
 * <p>Snapshots are built on the client thread, published by a single volatile write and then never touched
 * again, so the render thread reads a consistent set of shapes without a lock and without ever seeing a
 * colony half-way through a claim update. Every list handed in must already be unmodifiable.</p>
 *
 * @param colonies     every colony with at least one claimed chunk in this dimension, own ones first.
 * @param buildings    hut markers, own colonies only -- the client has a building list for no others.
 * @param deaths       grave markers.
 * @param waypoints    colony waypoint markers.
 * @param raiderSpawns the last recorded raider spawn points. <b>Last</b>: a marker here says nothing on its
 *                     own about whether a raid is happening now, and {@link ColonyShape#raiding()} is the
 *                     answer to that question. Each marker's own label and note are built with that answer
 *                     and with the age of the raid already folded in, so the screen only has to draw them.
 * @param fields       farmer and plantation fields.
 * @param patrols      manual guard patrol routes, one per guard tower that has any.
 */
@Environment(EnvType.CLIENT)
public record ColonySnapshot(
  List<ColonyShape> colonies,
  List<BuildingMarker> buildings,
  List<PointMarker> deaths,
  List<PointMarker> waypoints,
  List<PointMarker> raiderSpawns,
  List<FieldMarker> fields,
  List<PatrolRoute> patrols)
{
    /**
     * What the screen draws when MineColonies is absent, when the overlay has not produced anything yet, or
     * when the player is between worlds. Every list is empty, so every draw loop runs zero times.
     */
    public static final ColonySnapshot EMPTY =
      new ColonySnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    /**
     * @return true if there is nothing at all to draw.
     */
    public boolean isEmpty()
    {
        return this.colonies.isEmpty() && this.buildings.isEmpty() && this.deaths.isEmpty()
                 && this.waypoints.isEmpty() && this.raiderSpawns.isEmpty() && this.fields.isEmpty()
                 && this.patrols.isEmpty();
    }

    /**
     * One colony's claimed area.
     *
     * @param id         the colony id.
     * @param name       its name, for the centre label.
     * @param citizens   how many citizens it has, or -1 when that is not known from a view.
     * @param colour     0xRRGGBB, straight from {@code IColony#getTeamColonyColor()}. The mod assigns every
     *                   colony a team colour already and the players know their own by it, so there is no
     *                   second palette here.
     * @param own        true if the client's player is a member of this colony.
     * @param centre     the colony centre, where the label goes.
     * @param chunks     the claimed chunks, packed two ints to a long as
     *                   {@code (long) x << 32 | (z & 0xFFFFFFFFL)}. Used for the fill: one rectangle per
     *                   chunk, and because the rectangles are adjacent rather than overlapping the
     *                   translucent fill comes out an even tone with no seams.
     * @param edges      the boundary of that set, four ints per edge -- x0, z0, x1, z1 in block coordinates.
     *                   Only edges with a claimed chunk on one side and something else on the other are in
     *                   here, so what gets stroked is the outline of the region and not a box around every
     *                   chunk, and a hole in the middle of a colony gets its own edges like any other border.
     * @param raiding    the colony is under raid right now, from {@code IColonyView#isRaiding()}. Live only:
     *                   a remembered colony is never drawn as raiding, whatever it was doing when it was
     *                   last seen.
     * @param hostile    the colony is flagged hostile, from {@code IColony#isHostile()}. A durable property
     *                   of the colony rather than a passing state, so it is remembered.
     * @param remembered this colony was read back from disk and has <b>not</b> been seen live this session.
     *                   Drawn differently and labelled with {@link #lastSeen()}, because it may have been
     *                   abandoned, renamed or grown since.
     * @param lastSeen   epoch milliseconds when live data for this colony last arrived, or 0 when that is
     *                   not known.
     */
    public record ColonyShape(
      int id,
      String name,
      int citizens,
      int colour,
      boolean own,
      BlockPos centre,
      long[] chunks,
      int[] edges,
      boolean raiding,
      boolean hostile,
      boolean remembered,
      long lastSeen)
    {
        /**
         * @param chunkX a chunk x.
         * @param chunkZ a chunk z.
         * @return whether this colony claims that chunk. Linear in the claim size, which is what the
         *     right-click menu wants: it asks once per click, about one point.
         */
        public boolean claims(final int chunkX, final int chunkZ)
        {
            final long wanted = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            for (final long packed : this.chunks)
            {
                if (packed == wanted)
                {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * One hut.
     *
     * @param colonyId    the owning colony.
     * @param pos         the hut block.
     * @param colour      the colony's team colour, for the marker's frame.
     * @param icon        the hut block as an item, which is the icon: MineColonies already ships a distinct
     *                    model and texture for every hut, so the map does not need -- and should not invent
     *                    -- a second icon set. Empty when the building type has no block.
     * @param name        the display name, already resolved from the custom name or the type.
     * @param level       current level, 0 while unbuilt.
     * @param maxLevel    the type's maximum level.
     * @param workers     assigned citizens, already resolved to names; empty for a building with no worker,
     *                    and always empty for a remembered hut, because citizens are not persisted.
     * @param underConstruction whether a work order is open on it.
     * @param remembered  this hut came off disk rather than from a live building list.
     */
    public record BuildingMarker(
      int colonyId,
      BlockPos pos,
      int colour,
      ItemStack icon,
      String name,
      int level,
      int maxLevel,
      List<String> workers,
      boolean underConstruction,
      boolean remembered)
    {
    }

    /**
     * A grave, a waypoint or a raider spawn point: a labelled point with no other structure to it.
     *
     * @param pos   where it is.
     * @param colour 0xRRGGBB.
     * @param label what to say about it on hover.
     * @param note  a second, dimmer tooltip line, or null. Used to say whether a raider spawn point belongs to
     *              a raid happening now or to the last one, and how long ago that was.
     */
    public record PointMarker(BlockPos pos, int colour, String label, String note)
    {
    }

    /**
     * One farmer or plantation field.
     *
     * @param colonyId   the owning colony.
     * @param pos        the field's scarecrow or plantation block.
     * @param colour     the colony's team colour.
     * @param taken      whether a worker is assigned to it. Hollow when not, filled when it is.
     * @param type       the extension's registry path, e.g. {@code farmfield} or
     *                   {@code plantation_sugar_cane}. The registry path and not a translated name, because
     *                   it has to survive being written to disk and read back in another language.
     * @param colony     the owning colony's name, for the tooltip.
     * @param building   the hut that owns it, or null when it is unassigned. Drawn as a thin link line.
     * @param remembered this field came off disk rather than from a live extension list.
     */
    public record FieldMarker(
      int colonyId,
      BlockPos pos,
      int colour,
      boolean taken,
      String type,
      String colony,
      BlockPos building,
      boolean remembered)
    {
    }

    /**
     * One guard tower's manual patrol route.
     *
     * @param colonyId   the owning colony.
     * @param tower      the guard tower.
     * @param colour     the colony's team colour.
     * @param points     the patrol targets, in the order the tower holds them. Drawn as a closed polyline,
     *                   because that is what a patrol is: the guard walks the list and starts again.
     * @param remembered this route came off disk rather than from a live building view.
     */
    public record PatrolRoute(
      int colonyId,
      BlockPos tower,
      int colour,
      List<BlockPos> points,
      boolean remembered)
    {
    }
}
