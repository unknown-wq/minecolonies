package com.unknownwq.worldmap.screen;

import com.unknownwq.worldmap.colony.ChunkOutline;
import com.unknownwq.worldmap.colony.ColonyLayer;
import com.unknownwq.worldmap.colony.ColonyLayers;
import com.unknownwq.worldmap.colony.ColonySnapshot;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a {@link ColonySnapshot} over the map, and hit-tests it.
 *
 * <p>Knows nothing about MineColonies -- the snapshot is plain records -- so this class compiles and loads
 * on an installation that does not have it, and simply gets handed {@link ColonySnapshot#EMPTY} there.</p>
 *
 * <h2>What scales and what does not</h2>
 * <p>Two kinds of thing are drawn here and the rule that separates them is the same one the player marker
 * follows. Anything that is <b>a real shape on the ground</b> scales with the map, because drawing it any
 * other size would be a lie about where it is: the claimed area, and a guard patrol route, which is a path
 * a citizen actually walks and whose length is the point of it. Anything that is <b>a glyph marking a
 * point</b> -- a hut icon, a grave cross, a waypoint diamond, a field square, a raider spawn triangle -- is
 * drawn at a constant size in screen pixels, because scaled with the map a 16-pixel icon is an invisible
 * speck at 1/4 px per block and covers half a village at 8.</p>
 *
 * <h2>Collapsing</h2>
 * <p>Fixed-size markers pile up when you zoom out -- a colony with a hundred huts becomes a hundred
 * overlapping icons on a patch of map fifty pixels across, and the map underneath disappears. So both
 * markers and labels are dropped when they would land on top of something already drawn this frame:
 * {@value #ICON_SPACING} pixels between icon centres, and no overlap at all between label boxes. Own
 * colonies are drawn last and therefore win the space. Everything dropped this way is still reachable by
 * zooming in, and the layer toggles are there for when that is not enough.</p>
 *
 * <h2>The zoomed-out cut</h2>
 * <p>Collapsing thins the pile out; it does not stop it being a pile. Below one pixel per block a colony is
 * a few dozen pixels wide and even the collapsed markers cover it whole, so the per-marker glyphs are
 * dropped outright under {@link #MARKER_MIN_SCALE}. They are all the same class of thing: a small glyph of a
 * size fixed in screen pixels, marking a point that is a handful of blocks across, and they all clutter
 * identically.</p>
 *
 * <p><b>Raider spawn points are the exception</b>, and are drawn at every zoom. The cut exists because a
 * colony carries dozens of huts, graves and fields, all of them inside the border and all of them piling up
 * on the same few dozen pixels; a colony carries a handful of spawn points and they are the only glyph here
 * that is <em>outside</em> the border, a couple of hundred blocks out. Zooming out far enough to see that
 * much ground is exactly how a player looks for one -- which made the cut delete the marker at the moment it
 * was wanted, and leave a colony tagged {@code [raid]} with nothing on the map to say where from.</p>
 *
 * <p>Claim borders, patrol routes and colony labels are deliberately <em>not</em> cut. The first two scale
 * with the map -- they are the shape of the ground itself, and hiding them would leave the far view with
 * nothing on it. Labels are one per colony, not one per building, and they already drop themselves when the
 * colony is narrower on screen than its own name, so they never form a pile.</p>
 *
 * <h2>Live, remembered, raided and hostile</h2>
 * <p>Four states, three flat drawing properties, no new colours and no animation:</p>
 * <table>
 *   <tr><th>live</th><td>solid outline, translucent fill</td></tr>
 *   <tr><th>remembered</th><td>outline dashed on the chunk grid -- half of every chunk edge -- and
 *   <em>no</em> fill, because it is a record of a border rather than a claim over the ground in front of
 *   you</td></tr>
 *   <tr><th>hostile</th><td>a heavier outline with a one-pixel dark line down its middle, which reads as the
 *   double line a frontier is drawn with</td></tr>
 *   <tr><th>raiding</th><td>a heavier outline still, and a tag on the colony's label</td></tr>
 * </table>
 */
@Environment(EnvType.CLIENT)
public final class ColonyRenderer
{
    /**
     * Alpha of the fill over a colony the player belongs to. About a quarter, so the terrain stays readable
     * through it.
     */
    private static final int OWN_FILL_ALPHA = 0x40;

    /**
     * Alpha of everybody else's fill. Weaker on purpose: on a populated server most of what is in view is
     * somebody else's, and at the same strength as your own the map turns into a patchwork.
     */
    private static final int OTHER_FILL_ALPHA = 0x1A;

    private static final int OWN_OUTLINE_WIDTH = 2;
    private static final int OTHER_OUTLINE_WIDTH = 1;

    /**
     * Added to the outline width while a colony is being raided. Two pixels rather than a colour, because a
     * colour would either be a second palette or would stop the outline being the colony's own.
     */
    private static final int RAID_OUTLINE_EXTRA = 2;

    /**
     * Fewest pixels a hostile colony's outline is drawn at, so there is room for the dark line down its
     * middle. Below three the two halves are not separable and it just looks thick.
     */
    private static final int HOSTILE_OUTLINE_WIDTH = 3;

    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 10;

    /**
     * Fewest pixels per block at which the crowding per-marker glyphs -- huts, graves, waypoints, fields --
     * are drawn at all. One, so they are there at zoom 1x and above and gone at 1/2x and below; the map's
     * scale is {@code 2^zoom}, so this is exactly "zoom is not negative" expressed in the units this class
     * is handed. Raider spawn points do not go through it; see the class notes.
     */
    private static final double MARKER_MIN_SCALE = 1.0;

    private static final int GLYPH = 3;

    /**
     * Half the side of a field square, in screen pixels.
     */
    private static final int FIELD_HALF = 3;

    /**
     * Most line segments drawn for patrol routes in one frame. A colony with a tower on every corner and a
     * long manual route on each is the case this exists for; past this the routes stop rather than the frame
     * rate does.
     */
    private static final int MAX_PATROL_SEGMENTS = 4000;

    private static final int LABEL_BACKDROP = 0xB0000000;
    private static final int TOOLTIP_BACKDROP = 0xF01A1A1A;
    private static final int TOOLTIP_BORDER = 0xFF5A5A5A;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int TEXT_DIM = 0xFF9AA0A8;

    /**
     * The dark line down the middle of a hostile colony's outline. Black, which is already the map's
     * background and its label backdrop; no new colour enters the palette for this.
     */
    private static final int HOSTILE_CORE = 0xFF000000;

    private static final long MINUTE_MILLIS = 60_000L;
    private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;

    /**
     * The markers actually drawn last frame, with their screen rectangles, in draw order. Hover and click
     * both read this rather than recomputing positions, so what you can click is exactly what you can see:
     * an icon that was collapsed away is not secretly still there under the cursor.
     */
    private final List<Placed> placedBuildings = new ArrayList<>();

    private final List<int[]> placedLabels = new ArrayList<>();

    /**
     * Label boxes of remembered colonies, with the colony they belong to. Hovering one is how "when did I
     * last see this?" is asked -- see {@link #renderTooltip}.
     */
    private final List<PlacedLabel> rememberedLabels = new ArrayList<>();

    private ColonySnapshot.BuildingMarker hoveredBuilding;
    private ColonySnapshot.FieldMarker hoveredField;
    private ColonySnapshot.PointMarker hoveredPoint;
    private ColonySnapshot.ColonyShape hoveredColony;

    /**
     * Draws everything the layers allow.
     *
     * @param graphics the extractor.
     * @param font     the font.
     * @param snapshot what to draw.
     * @param layers   which parts of it.
     * @param scale    pixels per block.
     * @param offsetX  screen x of block 0.
     * @param offsetZ  screen y of block 0.
     * @param width    screen width.
     * @param height   screen height.
     * @param top      first usable screen y, below the header.
     * @param mouseX   pointer x.
     * @param mouseY   pointer y.
     */
    public void render(
      final GuiGraphicsExtractor graphics,
      final Font font,
      final ColonySnapshot snapshot,
      final ColonyLayers layers,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top,
      final int mouseX,
      final int mouseY)
    {
        this.placedBuildings.clear();
        this.placedLabels.clear();
        this.rememberedLabels.clear();
        this.hoveredBuilding = null;
        this.hoveredField = null;
        this.hoveredPoint = null;
        this.hoveredColony = null;

        if (snapshot.isEmpty())
        {
            return;
        }

        final boolean remembered = layers.isOn(ColonyLayer.REMEMBERED);
        final boolean raids = layers.isOn(ColonyLayer.RAIDS);

        for (final ColonySnapshot.ColonyShape shape : snapshot.colonies())
        {
            if (!this.shows(layers, shape, remembered))
            {
                continue;
            }
            this.drawClaim(graphics, shape, raids, scale, offsetX, offsetZ, width, height, top);
        }

        // Patrol routes are lines on the ground, so they scale with the map and are not cut when the glyphs
        // are -- a route is legible as a shape long after a 7-pixel square has stopped being legible as
        // anything.
        if (layers.isOn(ColonyLayer.PATROLS))
        {
            int segments = 0;
            for (final ColonySnapshot.PatrolRoute route : snapshot.patrols())
            {
                if (route.remembered() && !remembered)
                {
                    continue;
                }
                segments += this.drawPatrol(graphics, route, scale, offsetX, offsetZ, width, height, top);
                if (segments > MAX_PATROL_SEGMENTS)
                {
                    break;
                }
            }
        }

        // Below one pixel per block the glyphs are dropped entirely -- see the class notes. Nothing is
        // recorded either: hover and click both read what was drawn, so with the lists left empty a hidden
        // marker cannot be tooltipped or clicked through the map it is not covering.
        final boolean markers = scale >= MARKER_MIN_SCALE;

        if (markers && layers.isOn(ColonyLayer.DEATHS))
        {
            for (final ColonySnapshot.PointMarker marker : snapshot.deaths())
            {
                this.drawCross(graphics, marker, scale, offsetX, offsetZ, width, height, top, mouseX, mouseY);
            }
        }

        if (markers && layers.isOn(ColonyLayer.WAYPOINTS))
        {
            for (final ColonySnapshot.PointMarker marker : snapshot.waypoints())
            {
                this.drawDiamond(graphics, marker, scale, offsetX, offsetZ, width, height, top, mouseX, mouseY);
            }
        }

        // Not behind `markers`: a raid comes out of the ground a few hundred blocks outside the colony, and the
        // zoom that shows both at once is below the cut the other glyphs are dropped at.
        if (raids)
        {
            for (final ColonySnapshot.PointMarker marker : snapshot.raiderSpawns())
            {
                this.drawTriangle(graphics, marker, scale, offsetX, offsetZ, width, height, top, mouseX, mouseY);
            }
        }

        if (markers && layers.isOn(ColonyLayer.FIELDS))
        {
            final boolean links = layers.isOn(ColonyLayer.BUILDINGS);
            for (final ColonySnapshot.FieldMarker marker : snapshot.fields())
            {
                if (marker.remembered() && !remembered)
                {
                    continue;
                }
                this.drawField(graphics, marker, links, scale, offsetX, offsetZ, width, height, top, mouseX, mouseY);
            }
        }

        if (markers && layers.isOn(ColonyLayer.BUILDINGS))
        {
            for (final ColonySnapshot.BuildingMarker marker : snapshot.buildings())
            {
                if (marker.remembered() && !remembered)
                {
                    continue;
                }
                this.drawBuilding(graphics, marker, scale, offsetX, offsetZ, width, height, top);
            }
            this.hoveredBuilding = this.buildingAt(mouseX, mouseY);
        }

        if (layers.isOn(ColonyLayer.LABELS))
        {
            for (final ColonySnapshot.ColonyShape shape : snapshot.colonies())
            {
                if (!this.shows(layers, shape, remembered))
                {
                    continue;
                }
                this.drawLabel(graphics, font, shape, raids, scale, offsetX, offsetZ, width, height, top);
            }
            this.hoveredColony = this.rememberedLabelAt(mouseX, mouseY);
        }
    }

    /**
     * @return whether this colony's outline is drawn at all: its own-or-other layer has to be on, and if it
     *     is remembered rather than live the remembered layer has to be on too.
     */
    private boolean shows(final ColonyLayers layers, final ColonySnapshot.ColonyShape shape, final boolean remembered)
    {
        if (shape.remembered() && !remembered)
        {
            return false;
        }
        return layers.isOn(shape.own() ? ColonyLayer.BORDERS : ColonyLayer.OTHER_COLONIES);
    }

    private void drawClaim(
      final GuiGraphicsExtractor graphics,
      final ColonySnapshot.ColonyShape shape,
      final boolean raids,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top)
    {
        final boolean raiding = raids && shape.raiding();
        final int stroke = 0xFF000000 | shape.colour();

        int strokeWidth = shape.own() ? OWN_OUTLINE_WIDTH : OTHER_OUTLINE_WIDTH;
        if (shape.hostile())
        {
            strokeWidth = Math.max(strokeWidth, HOSTILE_OUTLINE_WIDTH);
        }
        if (raiding)
        {
            strokeWidth += RAID_OUTLINE_EXTRA;
        }

        // A remembered colony gets no fill at all. It is a record of where a border was, not a claim over
        // the ground you are looking at, and a fill says the second thing.
        if (!shape.remembered())
        {
            final int fill = (shape.own() ? OWN_FILL_ALPHA : OTHER_FILL_ALPHA) << 24 | shape.colour();

            // The fill is one rectangle per claimed chunk. They are adjacent and never overlap, so a
            // translucent fill blends exactly once everywhere and there are no darker seams down the shared
            // edges -- which is also why the fill cannot simply be the outline polygon filled in: the claim
            // may have holes.
            for (final long packed : shape.chunks())
            {
                final int bx = ChunkOutline.unpackX(packed) * ChunkOutline.CHUNK_BLOCKS;
                final int bz = ChunkOutline.unpackZ(packed) * ChunkOutline.CHUNK_BLOCKS;
                final int x0 = (int) Math.floor(bx * scale + offsetX);
                final int y0 = (int) Math.floor(bz * scale + offsetZ);
                final int x1 = (int) Math.floor((bx + ChunkOutline.CHUNK_BLOCKS) * scale + offsetX);
                final int y1 = (int) Math.floor((bz + ChunkOutline.CHUNK_BLOCKS) * scale + offsetZ);
                if (x1 <= 0 || y1 <= top || x0 >= width || y0 >= height || x1 == x0 || y1 == y0)
                {
                    continue;
                }
                graphics.fill(Math.max(0, x0), Math.max(top, y0), Math.min(width, x1), Math.min(height, y1), fill);
            }
        }

        // ...and the outline is the region's own boundary: only edges with a claimed chunk on exactly one
        // side survive into the snapshot, so interior chunk edges are simply not here to draw, and a hole in
        // the middle of the colony gets outlined like the rim does.
        // A remembered colony's outline is dashed, and the dash is half of every chunk edge rather than a
        // pattern in screen pixels. Screen-space dashing looks obvious and is wrong twice: the gaps slide
        // about as the map is panned, and at 1/4 px per block a chunk edge is four pixels long, so a segment
        // can land entirely inside one gap and the border simply vanishes. Half a chunk is a real length on
        // the ground: every edge contributes exactly one dash at every zoom, and the pattern is nailed to the
        // chunk grid rather than to the window.
        final int[] edges = shape.edges();
        for (int i = 0; i + 3 < edges.length; i += 4)
        {
            final int ex0 = edges[i];
            final int ez0 = edges[i + 1];
            int ex1 = edges[i + 2];
            int ez1 = edges[i + 3];
            if (shape.remembered())
            {
                ex1 = ex0 + (ex1 - ex0) / 2;
                ez1 = ez0 + (ez1 - ez0) / 2;
            }

            final int sx0 = (int) Math.floor(ex0 * scale + offsetX);
            final int sy0 = (int) Math.floor(ez0 * scale + offsetZ);
            final int sx1 = (int) Math.floor(ex1 * scale + offsetX);
            final int sy1 = (int) Math.floor(ez1 * scale + offsetZ);

            final boolean horizontal = ez0 == ez1;
            final int x0 = Math.min(sx0, sx1);
            final int y0 = Math.min(sy0, sy1);
            // A segment that has rounded away to nothing on its long axis still gets one pixel, or a
            // zoomed-out border would come apart into dots wherever the arithmetic happened to land.
            final int x1 = Math.max(Math.max(sx0, sx1) + (horizontal ? 0 : strokeWidth), x0 + 1);
            final int y1 = Math.max(Math.max(sy0, sy1) + (horizontal ? strokeWidth : 0), y0 + 1);

            if (x1 <= 0 || y1 <= top || x0 >= width || y0 >= height)
            {
                continue;
            }

            this.clippedFill(graphics, x0, y0, x1, y1, stroke, width, height, top);

            if (shape.hostile())
            {
                // One dark pixel down the middle of the stroke: the double line a frontier is drawn with,
                // out of the two things this renderer has, a rectangle and a colour it already uses.
                final int coreOffset = strokeWidth / 2;
                final int cx0 = horizontal ? x0 : x0 + coreOffset;
                final int cy0 = horizontal ? y0 + coreOffset : y0;
                this.clippedFill(graphics, cx0, cy0, horizontal ? x1 : cx0 + 1, horizontal ? cy0 + 1 : y1,
                  HOSTILE_CORE, width, height, top);
            }
        }
    }

    /**
     * One guard tower's route, as a closed polyline through its patrol points.
     *
     * @return how many segments were drawn, for the per-frame cap.
     */
    private int drawPatrol(
      final GuiGraphicsExtractor graphics,
      final ColonySnapshot.PatrolRoute route,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top)
    {
        final List<BlockPos> points = route.points();
        final int count = points.size();
        if (count < 2)
        {
            return 0;
        }

        final int colour = (route.remembered() ? 0x80000000 : 0xC0000000) | route.colour();
        int drawn = 0;
        for (int i = 0; i < count; i++)
        {
            final BlockPos a = points.get(i);
            final BlockPos b = points.get((i + 1) % count);
            final int ax = (int) Math.floor((a.getX() + 0.5) * scale + offsetX);
            final int ay = (int) Math.floor((a.getZ() + 0.5) * scale + offsetZ);
            final int bx = (int) Math.floor((b.getX() + 0.5) * scale + offsetX);
            final int by = (int) Math.floor((b.getZ() + 0.5) * scale + offsetZ);
            this.drawLine(graphics, ax, ay, bx, by, colour, width, height, top);
            drawn++;
        }
        return drawn;
    }

    /**
     * A one-pixel line between two screen points, as a run of rectangles.
     *
     * <p>{@code GuiGraphicsExtractor} offers a filled rectangle and nothing else, so a line has to be built
     * out of them. Stepping the major axis one pixel at a time and emitting a rectangle per pixel would be
     * one call per pixel; instead consecutive pixels that share a minor coordinate are merged into one
     * rectangle, which turns a shallow line of 400 pixels into a handful of calls and leaves a diagonal at
     * worst one call per pixel.</p>
     */
    private void drawLine(
      final GuiGraphicsExtractor graphics,
      final int x0,
      final int y0,
      final int x1,
      final int y1,
      final int colour,
      final int width,
      final int height,
      final int top)
    {
        final int dx = Math.abs(x1 - x0);
        final int dy = Math.abs(y1 - y0);

        // Entirely off one side: nothing to walk.
        if ((x0 < 0 && x1 < 0) || (x0 > width && x1 > width) || (y0 < top && y1 < top) || (y0 > height && y1 > height))
        {
            return;
        }

        if (dx == 0 && dy == 0)
        {
            this.clippedFill(graphics, x0, y0, x0 + 1, y0 + 1, colour, width, height, top);
            return;
        }

        if (dx >= dy)
        {
            final int from = Math.min(x0, x1);
            final int to = Math.max(x0, x1);
            final int fromY = x0 <= x1 ? y0 : y1;
            final int toY = x0 <= x1 ? y1 : y0;
            int runStart = from;
            int runY = fromY;
            for (int x = from; x <= to; x++)
            {
                final int y = fromY + Math.round((float) (toY - fromY) * (x - from) / (to - from));
                if (y != runY)
                {
                    this.clippedFill(graphics, runStart, runY, x, runY + 1, colour, width, height, top);
                    runStart = x;
                    runY = y;
                }
            }
            this.clippedFill(graphics, runStart, runY, to + 1, runY + 1, colour, width, height, top);
        }
        else
        {
            final int from = Math.min(y0, y1);
            final int to = Math.max(y0, y1);
            final int fromX = y0 <= y1 ? x0 : x1;
            final int toX = y0 <= y1 ? x1 : x0;
            int runStart = from;
            int runX = fromX;
            for (int y = from; y <= to; y++)
            {
                final int x = fromX + Math.round((float) (toX - fromX) * (y - from) / (to - from));
                if (x != runX)
                {
                    this.clippedFill(graphics, runX, runStart, runX + 1, y, colour, width, height, top);
                    runStart = y;
                    runX = x;
                }
            }
            this.clippedFill(graphics, runX, runStart, runX + 1, to + 1, colour, width, height, top);
        }
    }

    private void clippedFill(
      final GuiGraphicsExtractor graphics,
      final int x0,
      final int y0,
      final int x1,
      final int y1,
      final int colour,
      final int width,
      final int height,
      final int top)
    {
        final int cx0 = Math.max(0, x0);
        final int cy0 = Math.max(top, y0);
        final int cx1 = Math.min(width, x1);
        final int cy1 = Math.min(height, y1);
        if (cx1 > cx0 && cy1 > cy0)
        {
            graphics.fill(cx0, cy0, cx1, cy1, colour);
        }
    }

    private void drawBuilding(
      final GuiGraphicsExtractor graphics,
      final ColonySnapshot.BuildingMarker marker,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top)
    {
        final int cx = (int) Math.floor((marker.pos().getX() + 0.5) * scale + offsetX);
        final int cy = (int) Math.floor((marker.pos().getZ() + 0.5) * scale + offsetZ);
        final int x = cx - ICON_SIZE / 2;
        final int y = cy - ICON_SIZE / 2;
        if (x + ICON_SIZE < 0 || y + ICON_SIZE < top || x > width || y > height)
        {
            return;
        }

        for (final Placed placed : this.placedBuildings)
        {
            if (Math.abs(placed.cx() - cx) < ICON_SPACING && Math.abs(placed.cy() - cy) < ICON_SPACING)
            {
                return;
            }
        }

        graphics.fill(x - 1, y - 1, x + ICON_SIZE + 1, y + ICON_SIZE + 1, 0xFF000000 | marker.colour());
        if (marker.icon().isEmpty())
        {
            graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, 0xFF303030);
        }
        else
        {
            graphics.item(marker.icon(), x, y);
        }
        if (marker.underConstruction())
        {
            // A hut with an open work order gets a corner pip, so a half-built colony reads at a glance.
            graphics.fill(x + ICON_SIZE - 4, y, x + ICON_SIZE, y + 4, 0xFFFFC000);
        }

        this.placedBuildings.add(new Placed(cx, cy, x, y, marker));
    }

    /**
     * A field: a small square, hollow when nobody is assigned to it and filled when somebody is, plus a
     * thin line to the hut that owns it.
     *
     * <p>Hollow-versus-filled rather than two colours, because the colour is already saying which colony it
     * belongs to and there is only one of those per colony.</p>
     */
    private void drawField(
      final GuiGraphicsExtractor graphics,
      final ColonySnapshot.FieldMarker marker,
      final boolean links,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top,
      final int mouseX,
      final int mouseY)
    {
        final int cx = (int) Math.floor((marker.pos().getX() + 0.5) * scale + offsetX);
        final int cy = (int) Math.floor((marker.pos().getZ() + 0.5) * scale + offsetZ);
        if (cx < -FIELD_HALF || cy < top - FIELD_HALF || cx > width + FIELD_HALF || cy > height + FIELD_HALF)
        {
            return;
        }

        final int colour = 0xFF000000 | marker.colour();
        final int x0 = cx - FIELD_HALF;
        final int y0 = cy - FIELD_HALF;
        final int x1 = cx + FIELD_HALF + 1;
        final int y1 = cy + FIELD_HALF + 1;

        if (links && marker.building() != null)
        {
            // Drawn under the square, and only when the huts are on: a line to a hut that is not being drawn
            // points at nothing.
            final int bx = (int) Math.floor((marker.building().getX() + 0.5) * scale + offsetX);
            final int by = (int) Math.floor((marker.building().getZ() + 0.5) * scale + offsetZ);
            this.drawLine(graphics, cx, cy, bx, by, 0x60000000 | marker.colour(), width, height, top);
        }

        if (marker.taken())
        {
            this.clippedFill(graphics, x0, y0, x1, y1, colour, width, height, top);
        }
        else
        {
            this.clippedFill(graphics, x0, y0, x1, y0 + 1, colour, width, height, top);
            this.clippedFill(graphics, x0, y1 - 1, x1, y1, colour, width, height, top);
            this.clippedFill(graphics, x0, y0, x0 + 1, y1, colour, width, height, top);
            this.clippedFill(graphics, x1 - 1, y0, x1, y1, colour, width, height, top);
        }

        if (this.hoveredField == null && Math.abs(mouseX - cx) <= FIELD_HALF + 1 && Math.abs(mouseY - cy) <= FIELD_HALF + 1)
        {
            this.hoveredField = marker;
        }
    }

    private void drawCross(
      final GuiGraphicsExtractor graphics,
      final ColonySnapshot.PointMarker marker,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top,
      final int mouseX,
      final int mouseY)
    {
        final int cx = (int) Math.floor((marker.pos().getX() + 0.5) * scale + offsetX);
        final int cy = (int) Math.floor((marker.pos().getZ() + 0.5) * scale + offsetZ);
        if (cx < -GLYPH || cy < top - GLYPH || cx > width + GLYPH || cy > height + GLYPH)
        {
            return;
        }
        final int colour = 0xFF000000 | marker.colour();
        graphics.fill(cx - 1, cy - GLYPH - 1, cx + 2, cy + GLYPH + 2, colour);
        graphics.fill(cx - GLYPH - 1, cy - 1, cx + GLYPH + 2, cy + 2, colour);
        this.noteHover(marker, cx, cy, mouseX, mouseY);
    }

    private void drawDiamond(
      final GuiGraphicsExtractor graphics,
      final ColonySnapshot.PointMarker marker,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top,
      final int mouseX,
      final int mouseY)
    {
        final int cx = (int) Math.floor((marker.pos().getX() + 0.5) * scale + offsetX);
        final int cy = (int) Math.floor((marker.pos().getZ() + 0.5) * scale + offsetZ);
        if (cx < -GLYPH || cy < top - GLYPH || cx > width + GLYPH || cy > height + GLYPH)
        {
            return;
        }
        final int colour = 0xFF000000 | marker.colour();
        for (int d = -GLYPH; d <= GLYPH; d++)
        {
            final int half = GLYPH - Math.abs(d);
            graphics.fill(cx - half, cy + d, cx + half + 1, cy + d + 1, colour);
        }
        this.noteHover(marker, cx, cy, mouseX, mouseY);
    }

    /**
     * A raider spawn point: a triangle pointing down at where they came out of the ground. Distinct in shape
     * from the grave cross and the waypoint diamond, because they can all be on screen at once and a colour
     * cannot tell them apart -- the colour is already saying which colony it is, or, for a raid in progress,
     * that the raid is in progress.
     */
    private void drawTriangle(
      final GuiGraphicsExtractor graphics,
      final ColonySnapshot.PointMarker marker,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top,
      final int mouseX,
      final int mouseY)
    {
        final int cx = (int) Math.floor((marker.pos().getX() + 0.5) * scale + offsetX);
        final int cy = (int) Math.floor((marker.pos().getZ() + 0.5) * scale + offsetZ);
        if (cx < -GLYPH || cy < top - GLYPH || cx > width + GLYPH || cy > height + GLYPH)
        {
            return;
        }
        final int colour = 0xFF000000 | marker.colour();
        for (int d = -GLYPH; d <= GLYPH; d++)
        {
            final int half = (GLYPH - d + 1) / 2;
            this.clippedFill(graphics, cx - half, cy + d, cx + half + 1, cy + d + 1, colour, width, height, top);
        }
        this.noteHover(marker, cx, cy, mouseX, mouseY);
    }

    private void noteHover(final ColonySnapshot.PointMarker marker, final int cx, final int cy, final int mouseX, final int mouseY)
    {
        if (this.hoveredPoint == null && Math.abs(mouseX - cx) <= GLYPH + 1 && Math.abs(mouseY - cy) <= GLYPH + 1)
        {
            this.hoveredPoint = marker;
        }
    }

    private void drawLabel(
      final GuiGraphicsExtractor graphics,
      final Font font,
      final ColonySnapshot.ColonyShape shape,
      final boolean raids,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int width,
      final int height,
      final int top)
    {
        if (shape.name().isEmpty())
        {
            return;
        }

        final StringBuilder builder = new StringBuilder(shape.name());
        if (shape.citizens() >= 0)
        {
            builder.append("  ").append(shape.citizens());
        }
        if (raids && shape.raiding())
        {
            builder.append("  ").append(Component.translatable("gui.worldmap.colony.raid_tag").getString());
        }
        final String text = builder.toString();

        final int textWidth = font.width(text);
        final int cx = (int) Math.floor((shape.centre().getX() + 0.5) * scale + offsetX);
        final int cy = (int) Math.floor((shape.centre().getZ() + 0.5) * scale + offsetZ);

        final int x0 = cx - textWidth / 2 - 2;
        final int y0 = cy - font.lineHeight - 8;
        final int x1 = x0 + textWidth + 4;
        final int y1 = y0 + font.lineHeight + 2;

        if (x1 < 0 || y1 < top || x0 > width || y0 > height)
        {
            return;
        }

        // The colony has to be at least as wide on screen as its own name, or the name says more about the
        // map than the colony does. sqrt of the chunk count is the colony's width in chunks if it were
        // square, which is close enough for a threshold.
        final double colonyPixels = Math.sqrt(shape.chunks().length) * ChunkOutline.CHUNK_BLOCKS * scale;
        if (colonyPixels < textWidth * 0.6)
        {
            return;
        }

        for (final int[] taken : this.placedLabels)
        {
            if (x0 < taken[2] && x1 > taken[0] && y0 < taken[3] && y1 > taken[1])
            {
                return;
            }
        }

        graphics.fill(x0, y0, x1, y1, LABEL_BACKDROP);
        graphics.text(font, text, x0 + 2, y0 + 1,
          shape.remembered() ? TEXT_DIM : 0xFF000000 | shape.colour(), true);
        this.placedLabels.add(new int[] {x0, y0, x1, y1});
        if (shape.remembered())
        {
            this.rememberedLabels.add(new PlacedLabel(x0, y0, x1, y1, shape));
        }
    }

    private ColonySnapshot.ColonyShape rememberedLabelAt(final int mouseX, final int mouseY)
    {
        for (final PlacedLabel label : this.rememberedLabels)
        {
            if (mouseX >= label.x0() && mouseX < label.x1() && mouseY >= label.y0() && mouseY < label.y1())
            {
                return label.shape();
            }
        }
        return null;
    }

    /**
     * @param mouseX pointer x.
     * @param mouseY pointer y.
     * @return the hut marker under the pointer, or null. Reads what was drawn, so an icon that was
     *     collapsed away -- or dropped wholesale because the map is zoomed out past
     *     {@link #MARKER_MIN_SCALE} -- is not clickable.
     */
    public ColonySnapshot.BuildingMarker buildingAt(final double mouseX, final double mouseY)
    {
        for (int i = this.placedBuildings.size() - 1; i >= 0; i--)
        {
            final Placed placed = this.placedBuildings.get(i);
            if (mouseX >= placed.x() && mouseX < placed.x() + ICON_SIZE
                  && mouseY >= placed.y() && mouseY < placed.y() + ICON_SIZE)
            {
                return placed.marker();
            }
        }
        return null;
    }

    /**
     * Draws the hover tooltip, if the pointer is over anything. Call after everything else on the map and
     * before the context menu.
     *
     * @param graphics the extractor.
     * @param font     the font.
     * @param mouseX   pointer x.
     * @param mouseY   pointer y.
     * @param width    screen width.
     * @param height   screen height.
     */
    public void renderTooltip(
      final GuiGraphicsExtractor graphics,
      final Font font,
      final int mouseX,
      final int mouseY,
      final int width,
      final int height)
    {
        final List<String> lines = new ArrayList<>();
        final List<Boolean> dim = new ArrayList<>();

        if (this.hoveredBuilding != null)
        {
            final ColonySnapshot.BuildingMarker marker = this.hoveredBuilding;
            lines.add(marker.name());
            dim.add(false);
            lines.add(Component.translatable("gui.worldmap.colony.level", marker.level(), marker.maxLevel()).getString());
            dim.add(true);
            if (marker.underConstruction())
            {
                lines.add(Component.translatable("gui.worldmap.colony.building_in_progress").getString());
                dim.add(true);
            }
            for (final String worker : marker.workers())
            {
                lines.add(worker);
                dim.add(true);
            }
            if (marker.workers().isEmpty() && !marker.remembered())
            {
                lines.add(Component.translatable("gui.worldmap.colony.no_worker").getString());
                dim.add(true);
            }
            if (marker.remembered())
            {
                lines.add(Component.translatable("gui.worldmap.colony.remembered").getString());
                dim.add(true);
            }
            final BlockPos pos = marker.pos();
            lines.add("%d, %d, %d".formatted(pos.getX(), pos.getY(), pos.getZ()));
            dim.add(true);
        }
        else if (this.hoveredField != null)
        {
            final ColonySnapshot.FieldMarker marker = this.hoveredField;
            lines.add(prettyType(marker.type()));
            dim.add(false);
            lines.add(marker.colony());
            dim.add(true);
            lines.add(Component.translatable(marker.taken()
                                               ? "gui.worldmap.colony.field_worked"
                                               : "gui.worldmap.colony.field_unassigned").getString());
            dim.add(true);
            if (marker.remembered())
            {
                lines.add(Component.translatable("gui.worldmap.colony.remembered").getString());
                dim.add(true);
            }
            final BlockPos pos = marker.pos();
            lines.add("%d, %d, %d".formatted(pos.getX(), pos.getY(), pos.getZ()));
            dim.add(true);
        }
        else if (this.hoveredPoint != null)
        {
            lines.add(this.hoveredPoint.label());
            dim.add(false);
            if (this.hoveredPoint.note() != null)
            {
                lines.add(this.hoveredPoint.note());
                dim.add(true);
            }
        }
        else if (this.hoveredColony != null)
        {
            final ColonySnapshot.ColonyShape shape = this.hoveredColony;
            lines.add(shape.name());
            dim.add(false);
            lines.add(Component.translatable("gui.worldmap.colony.remembered").getString());
            dim.add(true);
            lines.add(lastSeen(shape.lastSeen()));
            dim.add(true);
            lines.add(Component.translatable("gui.worldmap.colony.forget_hint").getString());
            dim.add(true);
        }

        if (lines.isEmpty())
        {
            return;
        }

        int widest = 0;
        for (final String line : lines)
        {
            widest = Math.max(widest, font.width(line));
        }
        final int boxWidth = widest + 8;
        final int boxHeight = lines.size() * (font.lineHeight + 1) + 6;
        final int x = mouseX + 12 + boxWidth <= width ? mouseX + 12 : Math.max(0, mouseX - 12 - boxWidth);
        final int y = mouseY + boxHeight <= height ? mouseY : Math.max(0, height - boxHeight);

        graphics.fill(x - 1, y - 1, x + boxWidth + 1, y + boxHeight + 1, TOOLTIP_BORDER);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, TOOLTIP_BACKDROP);
        for (int i = 0; i < lines.size(); i++)
        {
            graphics.text(font, lines.get(i), x + 4, y + 3 + i * (font.lineHeight + 1),
              dim.get(i) ? TEXT_DIM : TEXT, false);
        }
    }

    /**
     * A field's type, as something readable, out of its registry path.
     *
     * <p>The path and not a translation, because the path is what survives being written to disk and read
     * back in a different language, and MineColonies has no translation key for a building extension type
     * to borrow. {@code plantation_sugar_cane} becomes "Sugar cane plantation" and {@code farmfield} becomes
     * "Farm field"; anything else keeps its own words with the underscores taken out.</p>
     *
     * @param type the registry path.
     * @return a display string; never null.
     */
    public static String prettyType(final String type)
    {
        if (type == null || type.isEmpty())
        {
            return Component.translatable("gui.worldmap.colony.field").getString();
        }
        String words = type.replace('_', ' ');
        if (words.startsWith("plantation "))
        {
            words = words.substring("plantation ".length()) + " plantation";
        }
        else if (words.equals("farmfield"))
        {
            words = "farm field";
        }
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    /**
     * @param millis the epoch millisecond stamp, or 0 when none was recorded.
     * @return "last seen 3 days ago" and the like. Rounded down to the largest unit that is at least one,
     *     because the exact figure is not the question being asked -- "is this still there?" is, and the
     *     honest answer to that is an order of magnitude.
     */
    private static String lastSeen(final long millis)
    {
        if (millis <= 0L)
        {
            return Component.translatable("gui.worldmap.colony.last_seen_unknown").getString();
        }
        final long elapsed = Math.max(0L, System.currentTimeMillis() - millis);
        final String span;
        if (elapsed >= DAY_MILLIS)
        {
            span = Component.translatable("gui.worldmap.time.days", elapsed / DAY_MILLIS).getString();
        }
        else if (elapsed >= HOUR_MILLIS)
        {
            span = Component.translatable("gui.worldmap.time.hours", elapsed / HOUR_MILLIS).getString();
        }
        else if (elapsed >= MINUTE_MILLIS)
        {
            span = Component.translatable("gui.worldmap.time.minutes", elapsed / MINUTE_MILLIS).getString();
        }
        else
        {
            return Component.translatable("gui.worldmap.colony.last_seen_now").getString();
        }
        return Component.translatable("gui.worldmap.colony.last_seen", span).getString();
    }

    /**
     * One drawn hut icon.
     *
     * @param cx     screen x of its centre, for the collapse test.
     * @param cy     screen y of its centre.
     * @param x      screen x of its top-left corner.
     * @param y      screen y of its top-left corner.
     * @param marker what it is.
     */
    private record Placed(int cx, int cy, int x, int y, ColonySnapshot.BuildingMarker marker)
    {
    }

    /**
     * One drawn colony label belonging to a remembered colony.
     *
     * @param x0    box left.
     * @param y0    box top.
     * @param x1    box right.
     * @param y1    box bottom.
     * @param shape the colony.
     */
    private record PlacedLabel(int x0, int y0, int x1, int y1, ColonySnapshot.ColonyShape shape)
    {
    }
}
