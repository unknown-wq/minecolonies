package com.unknownwq.worldmap.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.unknownwq.worldmap.WorldMapClient;
import com.unknownwq.worldmap.WorldMapKeys;
import com.unknownwq.worldmap.colony.ChunkOutline;
import com.unknownwq.worldmap.colony.ColonyLayer;
import com.unknownwq.worldmap.colony.ColonyLayers;
import com.unknownwq.worldmap.colony.ColonyOverlay;
import com.unknownwq.worldmap.colony.ColonySnapshot;
import com.unknownwq.worldmap.export.MapExport;
import com.unknownwq.worldmap.map.MapService;
import com.unknownwq.worldmap.map.MapShading;
import com.unknownwq.worldmap.map.MapTile;
import com.unknownwq.worldmap.map.TileKey;
import com.unknownwq.worldmap.map.TileStore;
import com.unknownwq.worldmap.render.TileTextures;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The map. Full screen, one pixel per block at zoom 1, nothing else on it.
 *
 * <h2>Pausing</h2>
 * <p>{@link #isPauseScreen()} returns true, and that is the whole mechanism: {@code Gui.isPausing} asks the
 * open screen, and {@code Minecraft} then sets its pause flag -- but only if
 * {@code hasSingleplayerServer() && !singleplayerServer.isPublished()} (Minecraft.java:1211). So the pause
 * is real in single player, and impossible on a server or on an open-to-LAN world. The screen checks the
 * same condition itself in {@link #gamePauses()} and says so in the footer rather than implying a pause it
 * cannot deliver.</p>
 *
 * <h2>Esc</h2>
 * <p>Left entirely to {@link Screen}: it closes on Esc by default and {@link Screen#onClose()} sets the
 * screen back to null, which resumes. Nothing here overrides {@code shouldCloseOnEsc}, and
 * {@link #keyPressed} passes anything it does not recognise -- Esc included -- to {@code super}.</p>
 */
@Environment(EnvType.CLIENT)
public final class WorldMapScreen extends Screen
{
    /**
     * Zoom is an exponent on a fixed ladder: the map draws {@code 2^zoom} pixels per block, so the levels are
     * 1/32, 1/16, 1/8, 1/4, 1/2, 1, 2, 4 and 8. Powers of two only, because a tile is then a whole number of
     * pixels wide and every blit lands on an exact pixel boundary -- no seams between tiles, no resampling
     * blur. That property is intact at every new rung as well: a 512-block tile is 16 screen pixels at
     * {@value #MIN_ZOOM} and a whole number at every level above it.
     *
     * <p>The floor is 1/32 rather than 1/16 because of what the scale bar can then say. The bar picks the
     * largest round distance that fits in {@value #SCALE_BAR_MAX} pixels: at 1/16 that is 1000 blocks, and at
     * 1/32 it is 2500, which is the reading this ladder was extended to reach.</p>
     *
     * <p>Three separate things had to move with it, all of them in the same direction -- the widest view now
     * covers thousands of tile squares instead of tens. {@link MapService} keeps an index of which squares
     * have anything behind them, so empty ground costs a set lookup instead of a blank 1.5 MiB tile and a
     * missing-file probe; {@link TileTextures} makes each texture the size the tile is actually drawn, so a
     * tile at this zoom is a kilobyte of video memory rather than a megabyte; and {@link #drawTiles} asks for
     * at most {@value #TILE_REQUESTS_PER_FRAME} tiles a frame, so the view fills in steadily instead of
     * demanding a thousand loads at once and evicting them again before any of them is drawn.</p>
     */
    private static final int MIN_ZOOM = -5;
    private static final int MAX_ZOOM = 3;

    /**
     * SDL scancode for the numpad minus. {@code InputConstants} names the numpad plus ({@code KEY_ADD}) but
     * not this one, and the SDL scancode table is fixed, so it is written out here.
     */
    private static final int SDL_SCANCODE_KP_MINUS = 86;

    /**
     * Hard ceiling on tiles actually drawn in one frame. Only reachable by zooming right out on a world with
     * an improbable amount of explored ground in view; it keeps a pathological frame from turning into an
     * unbounded number of blits.
     */
    private static final int MAX_TILES_PER_FRAME = 1024;

    /**
     * Hard ceiling on grid squares looked at in one frame, drawn or not. The real bound is the intersection
     * of the view with the explored rectangle {@link MapService#exploredBounds} reports, which on any normal
     * world is far smaller than either; this is what stops the loop while that rectangle is still being
     * built, in the first moments after joining a world.
     */
    private static final int MAX_TILE_SLOTS_PER_FRAME = 65536;

    /**
     * Tiles asked for from disk in one frame.
     *
     * <p>Deliberately close to {@link TileTextures}'s per-frame upload budget rather than to the number of
     * tiles on screen. A wide view can hold hundreds of tiles the cache does not; asking for all of them at
     * once loads hundreds of megabytes that are evicted again before their turn to be uploaded ever comes,
     * and the map ends up thrashing the disk without filling in any faster. Asking for a few a frame keeps
     * the loads just ahead of the uploads that consume them.</p>
     */
    private static final int TILE_REQUESTS_PER_FRAME = 8;

    /**
     * A gap longer than this between two scroll events ends the gesture: whatever fraction of a rung was
     * banked is dropped rather than carried into the next swipe. A touchpad gesture arrives as an unbroken
     * stream of events a few milliseconds apart, so a quarter of a second never falls inside one.
     */
    private static final long SCROLL_GESTURE_GAP_NANOS = 250_000_000L;

    private static final int HEADER_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 14;

    /**
     * Widest the scale bar is allowed to draw. It picks the largest round distance that fits, so this is
     * really the granularity of the thing: at 110 pixels every rung of the zoom ladder lands on a different
     * round number rather than two rungs sharing one.
     */
    private static final int SCALE_BAR_MAX = 110;

    /**
     * The round distances the scale bar is allowed to show. Every one of them is a number a player already
     * thinks in -- a chunk, a hundred blocks, a region.
     */
    private static final int[] SCALE_BAR_STEPS = {1, 2, 5, 10, 16, 25, 50, 100, 250, 500, 1000, 2500, 5000};

    /**
     * Clear space kept between the three pieces of the header strip, so that "next to" never becomes
     * "on top of" at any window width.
     */
    private static final int GAP = 10;
    private static final int BUTTON_SIZE = 14;

    private static final int UNEXPLORED = 0xFF000000;
    private static final int PANEL = 0xC8000000;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int TEXT_DIM = 0xFF9AA0A8;
    private static final int BUTTON_FACE = 0x60FFFFFF;
    private static final int BUTTON_FACE_HOVER = 0xA0FFFFFF;

    /**
     * The hairline that separates a panel from the map. A panel drawn straight onto terrain has no edge at
     * all where the ground under it happens to be dark, and the strip then reads as part of the map.
     */
    private static final int PANEL_EDGE = 0x40FFFFFF;

    /**
     * Drawn one pixel down and right of every stroke of the scale bar, so the bar stays readable over pale
     * sand and dark forest alike without needing a panel behind it.
     */
    private static final int SCALE_SHADOW = 0xA0000000;

    /**
     * Player marker: pure red, with arms long enough to be picked out at a glance without covering anything.
     * It carries no outline. Against black unexplored ground and against every common terrain colour it
     * reads clearly; the one palette entry it could hide in is {@code MapColor.FIRE} at full brightness,
     * which is exactly this red, and which only fire, magma and a few red blocks produce.
     */
    private static final int MARKER = 0xFFFF0000;
    private static final int MARKER_ARM = 4;

    /**
     * The facing arrow: a flat triangle in the same red, starting just past the tip of the marker's arms and
     * reaching {@value #FACING_TIP} pixels from its centre, {@value #FACING_HALF_WIDTH} pixels to either
     * side at its base. Small enough to read as part of the marker rather than as a second thing on the map,
     * big enough to tell four directions apart from a normal viewing distance.
     */
    private static final double FACING_BASE = MARKER_ARM + 1.5;
    private static final double FACING_TIP = MARKER_ARM + 6.0;
    private static final double FACING_HALF_WIDTH = 3.5;

    /**
     * The vanilla command that moves a player. A client mod cannot move one itself -- position is the
     * server's, and there is no client-side way to change it that is not a cheat the server rejects -- so
     * the teleport entry sends this as the player and lets the server decide. See {@link #teleportTo}.
     */
    private static final String TELEPORT_COMMAND = "tp";

    /**
     * Where the teleport entry aims when the map has no surface height for the column.
     *
     * <p>It used to refuse instead, which is the complaint this answers: the whole point of a map is to
     * click somewhere you have not been, and the one place the map has no height is exactly the place you
     * have not been. So it goes ahead at y {@value}, and the menu says so on the line under the entry.</p>
     *
     * <p><b>It is not a safe spot and nothing here pretends it is.</b> 100 is above ground over most of the
     * overworld and inside the rock of anything taller, and there is no cheap way to know which from here:
     * the client has no blocks for a chunk it has not been sent, which is the same fact that left the map
     * without a height in the first place. Vanilla's {@code /tp} does not place safely either -- that is
     * {@code /spreadplayers}, which has its own rules and its own permission. What does protect the player is
     * that this is the height they were told they were going to, on the entry they clicked.</p>
     */
    private static final int UNKNOWN_HEIGHT_TELEPORT_Y = 100;

    private final MapService service;
    private final ColonyOverlay colonies;
    private final ColonyLayers layers;
    private final ColonyRenderer colonyRenderer = new ColonyRenderer();
    private final ContextMenu menu = new ContextMenu();

    /**
     * What {@link MapShading} is told to apply, read from the configuration once. Held rather than rebuilt
     * per frame because the texture cache compares it against the options it last uploaded with, and an
     * equal-but-new record every frame is an allocation for nothing.
     */
    private final MapShading.Options shading;

    private TileTextures textures;

    private double centreX;
    private double centreZ;
    private int zoom;
    private boolean centred;

    private double pointerX;
    private double pointerY;

    /**
     * True between a press on the map and its release.
     *
     * <p>Necessary, not belt-and-braces. {@code MouseHandler.onMove} calls {@code Screen#mouseDragged}
     * whenever it believes a button is held, and in this environment it believes that after a press whose
     * release it never saw -- a click that dismissed a screen just before a long world load was enough. The
     * symptom is the map sliding under a mouse that is only being moved. Tracking the press here makes the
     * pan depend on a press this screen itself received.</p>
     */
    private boolean panning;

    /**
     * Fine-grained vertical scroll banked towards the next rung of the zoom ladder, and the time the last
     * scroll event of any kind arrived. See {@link #mouseScrolled}.
     */
    private double zoomScroll;
    private long lastScrollNanos;

    /**
     * @param service  the map service to read tiles from.
     * @param colonies the colony overlay; {@link ColonyOverlay#NONE} when MineColonies is not installed, in
     *                 which case every colony draw loop below runs zero times and no MineColonies class is
     *                 ever loaded.
     * @param layers   which colony layers the player has switched on.
     */
    public WorldMapScreen(final MapService service, final ColonyOverlay colonies, final ColonyLayers layers)
    {
        super(Component.translatable("gui.worldmap.title"));
        this.service = service;
        this.colonies = colonies;
        this.layers = layers;
        this.shading = service.config().shadingOptions();
    }

    @Override
    protected void init()
    {
        if (this.textures == null)
        {
            this.textures = new TileTextures(this.service.config().gpuTileCap);
        }
        if (!this.centred)
        {
            this.centreOnPlayer();
            this.centred = true;
        }
        this.pointerX = this.width / 2.0;
        this.pointerY = this.height / 2.0;
        this.panning = false;
        this.zoomScroll = 0.0;
    }

    @Override
    public boolean isPauseScreen()
    {
        return true;
    }

    /**
     * @return true if opening this screen actually stops the world. Mirrors the condition Minecraft itself
     *     applies to {@code isPauseScreen}: an integrated server that is not open to LAN.
     */
    private boolean gamePauses()
    {
        if (!this.minecraft.hasSingleplayerServer())
        {
            return false;
        }
        final IntegratedServer server = this.minecraft.getSingleplayerServer();
        return server != null && !server.isPublished();
    }

    @Override
    public void removed()
    {
        if (this.textures != null)
        {
            this.textures.close();
            this.textures = null;
        }
        this.zoomScroll = 0.0;
        super.removed();
    }

    /**
     * Replaces the vanilla blurred-and-panelled menu background with flat black. Black is also what an
     * unexplored or unloaded chunk looks like -- the two are deliberately indistinguishable -- so the
     * background and the gaps in the map are the same colour by design, not by accident.
     */
    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a)
    {
        graphics.fill(0, 0, this.width, this.height, UNEXPLORED);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a)
    {
        final double scale = this.pixelsPerBlock();
        final double offsetX = this.width / 2.0 - this.centreX * scale;
        final double offsetZ = this.height / 2.0 - this.centreZ * scale;

        this.textures.beginFrame(this.shading, this.tileDetail(), this.service.config().smoothZoomedOut);
        this.drawTiles(graphics, scale, offsetX, offsetZ);
        this.colonyRenderer.render(graphics, this.font, this.colonies.snapshot(), this.layers,
          scale, offsetX, offsetZ, this.width, this.height, HEADER_HEIGHT, mouseX, mouseY);
        this.drawPlayer(graphics, scale, offsetX, offsetZ);
        this.drawScaleBar(graphics, scale);
        this.drawHeader(graphics, scale, offsetX, offsetZ, mouseX, mouseY);
        this.drawFooter(graphics);
        if (!this.menu.isOpen())
        {
            this.colonyRenderer.renderTooltip(graphics, this.font, mouseX, mouseY, this.width, this.height);
        }
        this.menu.render(graphics, this.font, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void drawTiles(final GuiGraphicsExtractor graphics, final double scale, final double offsetX, final double offsetZ)
    {
        final String dimension = this.service.dimension();
        if (dimension.isEmpty())
        {
            return;
        }

        final int tilePixels = (int) Math.round(MapTile.SIZE * scale);
        int minTileX = Math.floorDiv((int) Math.floor(-offsetX / scale), MapTile.SIZE);
        int maxTileX = Math.floorDiv((int) Math.floor((this.width - offsetX) / scale), MapTile.SIZE);
        int minTileZ = Math.floorDiv((int) Math.floor(-offsetZ / scale), MapTile.SIZE);
        int maxTileZ = Math.floorDiv((int) Math.floor((this.height - offsetZ) / scale), MapTile.SIZE);

        // Clipped to what exists before anything else. At 1/32 of a pixel per block the view is tens of
        // thousands of tile squares across and all but a handful of them are ground nobody has walked;
        // walking them anyway is the difference between a frame that costs what the map holds and one that
        // costs what the window covers.
        final TileStore.Bounds explored = this.service.exploredBounds(dimension);
        if (explored != null)
        {
            minTileX = Math.max(minTileX, explored.minX());
            maxTileX = Math.min(maxTileX, explored.maxX());
            minTileZ = Math.max(minTileZ, explored.minZ());
            maxTileZ = Math.min(maxTileZ, explored.maxZ());
        }

        int drawn = 0;
        int slots = 0;
        int requests = 0;
        for (int tz = minTileZ; tz <= maxTileZ; tz++)
        {
            for (int tx = minTileX; tx <= maxTileX; tx++)
            {
                if (++slots > MAX_TILE_SLOTS_PER_FRAME || drawn >= MAX_TILES_PER_FRAME)
                {
                    return;
                }
                if (!this.service.mayHaveTile(dimension, tx, tz))
                {
                    continue;
                }

                final TileKey key = new TileKey(dimension, tx, tz);

                // Asked for without queueing a load: the texture is what gets drawn, and it outlives the
                // tile behind it. A tile that is resident anyway -- the ground round the player, which the
                // scanner is writing into -- is handed over so its texture can be refreshed.
                final MapTile tile = this.service.residentTile(key);
                if (tile != null)
                {
                    tile.touch();
                }

                final DynamicTexture texture = this.textures.textureFor(key, tile);
                if (texture == null)
                {
                    if (tile == null && requests < TILE_REQUESTS_PER_FRAME)
                    {
                        requests++;
                        this.service.requestTile(key);
                    }
                    continue;
                }

                drawn++;
                final int x0 = (int) Math.floor(key.originX() * scale + offsetX);
                final int y0 = (int) Math.floor(key.originZ() * scale + offsetZ);
                graphics.blit(texture.getTextureView(), texture.getSampler(),
                  x0, y0, x0 + tilePixels, y0 + tilePixels, 0.0F, 1.0F, 0.0F, 1.0F);
            }
        }
    }

    /**
     * The player marker: a red plus at the player's X/Z, with a small triangle off one side of it showing
     * which way they are facing. No label, and the plus itself does not rotate -- only the triangle moves,
     * so the marker's centre stays exactly where the player is at every angle.
     *
     * <p>Drawn in screen space at a fixed pixel size, so it stays the same on every rung of the zoom ladder.
     * Scaling it with the map would make it a single invisible pixel at 1/4 px per block and a
     * house-sized blob at 8.</p>
     *
     * <p>The yaw is pure client state -- the client is the authority on where its own player is looking, and
     * it is on {@code LocalPlayer} already -- so the arrow costs no packet and asks the server nothing.</p>
     */
    private void drawPlayer(final GuiGraphicsExtractor graphics, final double scale, final double offsetX, final double offsetZ)
    {
        final LocalPlayer player = this.minecraft.player;
        if (player == null)
        {
            return;
        }

        final int x = (int) Math.floor(player.getX() * scale + offsetX);
        final int y = (int) Math.floor(player.getZ() * scale + offsetZ);
        // Margin is the facing arrow's reach, not the plus's: the arrow is the part that sticks out furthest,
        // and a player just off the edge should still show whichever half of the marker is on screen.
        final int margin = (int) Math.ceil(FACING_TIP) + 1;
        if (x < -margin || y < -margin || x > this.width + margin || y > this.height + margin)
        {
            return;
        }

        graphics.fill(x - MARKER_ARM, y, x + MARKER_ARM + 1, y + 1, MARKER);
        graphics.fill(x, y - MARKER_ARM, x + 1, y + MARKER_ARM + 1, MARKER);
        this.drawFacing(graphics, x, y, player.getYRot());
    }

    /**
     * The facing triangle.
     *
     * <h2>Getting the sign right</h2>
     * <p>Yaw 0 faces south, which is +Z, and it increases clockwise seen from above. That is not a
     * convention taken on trust: {@code Entity#calculateViewVector} builds the look vector as
     * {@code (sin(-yaw), ..., cos(-yaw))}, so at yaw 0 it is {@code (0, 0, +1)} -- due south -- and at yaw 90
     * it is {@code (-1, 0, 0)}, due west.</p>
     *
     * <p>This map puts +X to the right and +Z <em>down</em> the screen, so world-to-screen is the identity on
     * both axes and the screen direction is simply {@code (-sin(yaw), +cos(yaw))} in (x, y) pixels. Yaw 0
     * therefore points the triangle straight down the screen, which is south, and yaw 90 points it left,
     * which is west; that clockwise sense matches the map because +Z pointing down is what makes the screen
     * agree with the world's handedness in the first place. Flipping either sign here is the classic bug and
     * shows up immediately as an arrow that lags the player by a quarter or a half turn.</p>
     *
     * @param graphics the extractor.
     * @param cx       screen x of the marker's centre.
     * @param cy       screen y of the marker's centre.
     * @param yaw      the player's yaw in degrees.
     */
    private void drawFacing(final GuiGraphicsExtractor graphics, final int cx, final int cy, final float yaw)
    {
        final double radians = Math.toRadians(yaw);
        final double forwardX = -Math.sin(radians);
        final double forwardY = Math.cos(radians);

        // Rasterised in screen space rather than by rotating a stamp: every candidate pixel in the bounding
        // box is mapped back into the triangle's own frame, so the shape has no holes at the diagonals, and
        // each row of it collapses to a single fill because the triangle is convex.
        final int reach = (int) Math.ceil(FACING_TIP) + 1;
        for (int dy = -reach; dy <= reach; dy++)
        {
            int first = Integer.MAX_VALUE;
            int last = Integer.MIN_VALUE;
            for (int dx = -reach; dx <= reach; dx++)
            {
                // Offsets are measured from the centre of the marker's own pixel, not from its corner, so
                // the triangle is symmetric about the plus rather than half a pixel to one side of it.
                final double along = dx * forwardX + dy * forwardY;
                if (along < FACING_BASE || along > FACING_TIP)
                {
                    continue;
                }
                final double across = Math.abs(dx * -forwardY + dy * forwardX);
                final double allowed = FACING_HALF_WIDTH * (FACING_TIP - along) / (FACING_TIP - FACING_BASE);
                if (across > allowed)
                {
                    continue;
                }
                first = Math.min(first, dx);
                last = Math.max(last, dx);
            }
            if (first <= last)
            {
                graphics.fill(cx + first, cy + dy, cx + last + 1, cy + dy + 1, MARKER);
            }
        }
    }

    /**
     * The top strip: title on the left, the block coordinates under the cursor in the middle, dimension and
     * zoom plus the two zoom buttons on the right.
     *
     * <p>X, Z and -- since the tile format grew a height plane -- Y. The Y is real data: it is the y of the
     * block whose map colour was drawn at that column, recorded by the same scan that produced the pixel.
     * A column the map has not got in memory, or one that came off disk in the old colour-only format,
     * shows {@code Y -} rather than a guess.</p>
     */
    private void drawHeader(
      final GuiGraphicsExtractor graphics,
      final double scale,
      final double offsetX,
      final double offsetZ,
      final int mouseX,
      final int mouseY)
    {
        graphics.fill(0, 0, this.width, HEADER_HEIGHT, PANEL);
        graphics.fill(0, HEADER_HEIGHT - 1, this.width, HEADER_HEIGHT, PANEL_EDGE);

        final int textY = (HEADER_HEIGHT - this.font.lineHeight) / 2 + 1;
        graphics.text(this.font, this.title.getString(), 6, textY, TEXT_DIM, true);

        final int cursorX = (int) Math.floor((mouseX - offsetX) / scale);
        final int cursorZ = (int) Math.floor((mouseY - offsetZ) / scale);
        final short cursorY = this.service.heightAt(cursorX, cursorZ);
        final String coordinates = cursorY == MapTile.NO_HEIGHT
                                     ? "X %d   Y -   Z %d".formatted(cursorX, cursorZ)
                                     : "X %d   Y %d   Z %d".formatted(cursorX, cursorY, cursorZ);

        // Zoom buttons sit at the right edge; the dimension and zoom label go immediately left of them.
        final int minusX = this.width - 6 - BUTTON_SIZE * 2 - 2;
        final int plusX = this.width - 6 - BUTTON_SIZE;
        final int buttonY = (HEADER_HEIGHT - BUTTON_SIZE) / 2;
        this.zoomButton(graphics, minusX, buttonY, "-", this.zoom > MIN_ZOOM, mouseX, mouseY);
        this.zoomButton(graphics, plusX, buttonY, "+", this.zoom < MAX_ZOOM, mouseX, mouseY);

        // The coordinates used to be centred on the whole window while this label was right-aligned, so on
        // a narrow window -- or simply with a dimension id as long as "minecraft:overworld" -- the two ran
        // into each other and the reading became unusable. Nothing measured the collision. Now the three
        // pieces are laid out against each other and the right-hand label gives way first: it loses the
        // dimension, then the zoom, then all of it, and the coordinates are centred in whatever gap is
        // left rather than in the window. The coordinates are the point of the strip and are never dropped.
        final int titleEnd = 6 + this.font.width(this.title.getString());
        final int rightEdge = minusX - 8;
        final int coordinatesWidth = this.font.width(coordinates);

        String label = "%s   %s".formatted(shortDimension(), zoomLabel(this.zoom));
        if (rightEdge - this.font.width(label) - GAP < titleEnd + GAP + coordinatesWidth)
        {
            label = zoomLabel(this.zoom);
            if (rightEdge - this.font.width(label) - GAP < titleEnd + GAP + coordinatesWidth)
            {
                label = "";
            }
        }

        final int labelX = label.isEmpty() ? rightEdge : rightEdge - this.font.width(label);
        if (!label.isEmpty())
        {
            graphics.text(this.font, label, labelX, textY, TEXT_DIM, true);
        }

        final int freeLeft = titleEnd + GAP;
        final int freeRight = labelX - GAP;
        final int centred = freeLeft + (freeRight - freeLeft - coordinatesWidth) / 2;
        graphics.text(this.font, coordinates, Math.max(freeLeft, centred), textY, TEXT, true);
    }

    /**
     * The dimension as it is worth reading in a header: {@code minecraft:overworld} is nineteen characters
     * of which nine carry the meaning, and the namespace is {@code minecraft} for every dimension a player
     * will normally be looking at. A modded dimension keeps its namespace, because there the namespace is
     * the part that says which mod it belongs to.
     *
     * @return a display name for the current dimension, or {@code -} when there is none.
     */
    private String shortDimension()
    {
        final String dimension = this.service.dimension();
        if (dimension.isEmpty())
        {
            return "-";
        }
        return dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
    }

    private void zoomButton(
      final GuiGraphicsExtractor graphics,
      final int x,
      final int y,
      final String glyph,
      final boolean enabled,
      final int mouseX,
      final int mouseY)
    {
        final boolean hovered = enabled && mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
        graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, hovered ? BUTTON_FACE_HOVER : BUTTON_FACE);
        graphics.text(this.font, glyph,
          x + (BUTTON_SIZE - this.font.width(glyph)) / 2, y + (BUTTON_SIZE - this.font.lineHeight) / 2 + 1,
          enabled ? 0xFF101010 : 0xFF606060, false);
    }

    private static String zoomLabel(final int zoom)
    {
        return zoom >= 0 ? (1 << zoom) + "x" : "1/" + (1 << -zoom) + "x";
    }

    /**
     * A bar of a round number of blocks, above the bottom-left of the footer.
     *
     * <p>The zoom label in the header says {@code 1/4x} or {@code 4x}, which is the truth and is no help at
     * all in answering "how far is it from here to there" -- that depends on the GUI scale and the window
     * size as much as on the rung of the ladder. A bar is the answer a map has always given, and it is
     * correct at any window size without anybody having to work anything out.</p>
     *
     * <p>The largest round distance that fits in {@value #SCALE_BAR_MAX} pixels wins, so the bar changes
     * length as the map is zoomed and the number changes with it.</p>
     */
    private void drawScaleBar(final GuiGraphicsExtractor graphics, final double scale)
    {
        int blocks = SCALE_BAR_STEPS[0];
        for (final int candidate : SCALE_BAR_STEPS)
        {
            if (candidate * scale <= SCALE_BAR_MAX)
            {
                blocks = candidate;
            }
        }

        final int length = Math.max(8, (int) Math.round(blocks * scale));
        final int x = 8;
        final int y = this.height - FOOTER_HEIGHT - 8;
        final int tick = 4;

        graphics.fill(x + 1, y + 1, x + length + 1, y + 2, SCALE_SHADOW);
        graphics.fill(x + 1, y - tick + 1, x + 2, y + 2, SCALE_SHADOW);
        graphics.fill(x + length, y - tick + 1, x + length + 1, y + 2, SCALE_SHADOW);

        graphics.fill(x, y, x + length, y + 1, TEXT);
        graphics.fill(x, y - tick, x + 1, y + 1, TEXT);
        graphics.fill(x + length - 1, y - tick, x + length, y + 1, TEXT);

        final String label = Component.translatable("gui.worldmap.scale", blocks).getString();
        graphics.text(this.font, label,
          x + (length - this.font.width(label)) / 2, y - tick - this.font.lineHeight - 1, TEXT, true);
    }

    /**
     * The bottom strip: player position and cache counters on the left, and on the right either the control
     * hint or -- when the world is still running because this is not single player -- a note saying so.
     */
    private void drawFooter(final GuiGraphicsExtractor graphics)
    {
        final int y = this.height - 11;
        graphics.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height, PANEL);
        graphics.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height - FOOTER_HEIGHT + 1, PANEL_EDGE);

        final LocalPlayer player = this.minecraft.player;
        final String left = player == null
                              ? ""
                              : "you %d, %d, %d   tiles %d/%d   textures %d   queued %d".formatted(
                                (int) Math.floor(player.getX()), (int) Math.floor(player.getY()), (int) Math.floor(player.getZ()),
                                this.service.residentTiles(), this.service.config().cpuTileCap,
                                this.textures.size(), this.service.backlog());
        graphics.text(this.font, left, 6, y, TEXT_DIM, true);

        final String right = this.gamePauses()
                               ? Component.translatable("gui.worldmap.hint").getString()
                               : Component.translatable("gui.worldmap.not_paused").getString();
        final int rightX = this.width - 6 - this.font.width(right);
        if (rightX > 6 + this.font.width(left) + 8)
        {
            graphics.text(this.font, right, rightX, y, this.gamePauses() ? TEXT_DIM : 0xFFE0B050, true);
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------------

    @Override
    public void mouseMoved(final double x, final double y)
    {
        this.pointerX = x;
        this.pointerY = y;
    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick)
    {
        this.pointerX = event.x();
        this.pointerY = event.y();

        // The menu gets first refusal. A click inside it is consumed whether or not it landed on an entry;
        // a click outside merely dismisses it and then falls through, so dismissing the menu and panning
        // are the same gesture rather than two.
        if (this.menu.click(event.x(), event.y()))
        {
            return true;
        }

        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT)
        {
            this.openContextMenu(event.x(), event.y());
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
        {
            return true;
        }

        final int minusX = this.width - 6 - BUTTON_SIZE * 2 - 2;
        final int plusX = this.width - 6 - BUTTON_SIZE;
        final int buttonY = (HEADER_HEIGHT - BUTTON_SIZE) / 2;
        if (event.y() >= buttonY && event.y() < buttonY + BUTTON_SIZE)
        {
            if (event.x() >= minusX && event.x() < minusX + BUTTON_SIZE)
            {
                this.changeZoom(-1, this.width / 2.0, this.height / 2.0);
                return true;
            }
            if (event.x() >= plusX && event.x() < plusX + BUTTON_SIZE)
            {
                this.changeZoom(1, this.width / 2.0, this.height / 2.0);
                return true;
            }
        }

        final ColonySnapshot.BuildingMarker building = this.colonyRenderer.buildingAt(event.x(), event.y());
        if (building != null && this.colonies.openBuildingGui(building.colonyId(), building.pos()))
        {
            // The hut window replaced this screen; do not also start a pan under it.
            return true;
        }

        this.panning = true;
        return true;
    }

    /**
     * Opens the right-click menu for the block under the cursor.
     *
     * <p>Two entries always, plus one per colony layer when MineColonies is installed. The teleport entry is
     * greyed rather than hidden when it cannot work, with the reason on the line under it -- see
     * {@link #teleportTo} for what "cannot work" means here and why the answer is knowable at all.</p>
     */
    private void openContextMenu(final double screenX, final double screenY)
    {
        final double scale = this.pixelsPerBlock();
        final double offsetX = this.width / 2.0 - this.centreX * scale;
        final double offsetZ = this.height / 2.0 - this.centreZ * scale;
        final int blockX = (int) Math.floor((screenX - offsetX) / scale);
        final int blockZ = (int) Math.floor((screenY - offsetZ) / scale);

        // The blocking read, not the one that answers out of memory: this has to say where the player is
        // going at the moment the menu is built, and a tile the map is drawing is not necessarily resident.
        final short blockY = this.service.heightAtNow(blockX, blockZ);
        final boolean guessed = blockY == MapTile.NO_HEIGHT;
        final int targetY = guessed ? UNKNOWN_HEIGHT_TELEPORT_Y : blockY + 1;

        final List<ContextMenu.Entry> entries = new ArrayList<>();

        if (this.service.dimension().isEmpty())
        {
            entries.add(ContextMenu.Entry.disabled(
              Component.translatable("gui.worldmap.menu.teleport_here").getString(),
              Component.translatable("gui.worldmap.menu.teleport_no_world").getString()));
        }
        else if (!this.canSendTeleport())
        {
            // Still refused, and for a reason that has nothing to do with the map: the server did not put
            // /tp in the command tree it sent this client. Unexplored ground is now allowed; a player who
            // may not teleport at all still may not.
            entries.add(ContextMenu.Entry.disabled(
              Component.translatable("gui.worldmap.menu.teleport_to", blockX, targetY, blockZ).getString(),
              Component.translatable("gui.worldmap.menu.teleport_no_permission").getString()));
        }
        else if (guessed)
        {
            entries.add(ContextMenu.Entry.of(
              Component.translatable("gui.worldmap.menu.teleport_to", blockX, targetY, blockZ).getString(),
              Component.translatable("gui.worldmap.menu.teleport_guessed_height", targetY).getString(),
              () -> this.teleportTo(blockX, targetY, blockZ)));
        }
        else
        {
            entries.add(ContextMenu.Entry.of(
              Component.translatable("gui.worldmap.menu.teleport_to", blockX, targetY, blockZ).getString(),
              () -> this.teleportTo(blockX, targetY, blockZ)));
        }

        entries.add(ContextMenu.Entry.of(
          Component.translatable("gui.worldmap.menu.save_png").getString(), this::savePng));

        if (this.colonies.isActive())
        {
            final ColonySnapshot.ColonyShape forget = this.rememberedColonyAt(blockX, blockZ);
            if (forget != null)
            {
                // Only ever offered for a colony that is remembered rather than live. Nothing here removes a
                // colony that is really out there: the map cannot tell "deleted while you were away" from
                // "out of range", so it never guesses, and this is the player answering the question.
                final int id = forget.id();
                final String name = forget.name().isEmpty()
                                      ? Integer.toString(id)
                                      : forget.name();
                entries.add(ContextMenu.Entry.rule());
                entries.add(ContextMenu.Entry.of(
                  Component.translatable("gui.worldmap.menu.forget_colony", name).getString(),
                  () -> this.colonies.forgetColony(id)));
            }

            entries.add(ContextMenu.Entry.rule());
            for (final ColonyLayer layer : ColonyLayer.values())
            {
                entries.add(ContextMenu.Entry.toggle(
                  Component.translatable(layer.translationKey()).getString(),
                  this.layers.isOn(layer),
                  () -> this.layers.toggle(layer)));
            }
        }

        this.menu.open((int) screenX, (int) screenY, this.width, this.height, this.font, entries);
        this.panning = false;
    }

    /**
     * The remembered colony claiming the chunk a click landed in, if any.
     *
     * <p>Reads the snapshot rather than anything live, so it agrees with what is on screen, and only ever
     * returns a colony the overlay has marked remembered -- a live colony is not the map's to forget, and
     * the next poll would put it straight back anyway.</p>
     *
     * @param blockX the clicked block x.
     * @param blockZ the clicked block z.
     * @return the colony, or null.
     */
    private ColonySnapshot.ColonyShape rememberedColonyAt(final int blockX, final int blockZ)
    {
        final int chunkX = Math.floorDiv(blockX, ChunkOutline.CHUNK_BLOCKS);
        final int chunkZ = Math.floorDiv(blockZ, ChunkOutline.CHUNK_BLOCKS);
        for (final ColonySnapshot.ColonyShape shape : this.colonies.snapshot().colonies())
        {
            if (shape.remembered() && shape.claims(chunkX, chunkZ))
            {
                return shape;
            }
        }
        return null;
    }

    /**
     * Whether the player is allowed to teleport themselves.
     *
     * <p>This is knowable, and it is knowable without guessing: the server sends the client the command tree
     * it is permitted to use ({@code ClientboundCommandsPacket}, which is what tab-completion is built from),
     * and a player without the permission level {@code /tp} requires simply does not have that node in it.
     * So the absence of a {@code tp} child on the root is the server's own answer to the question, and the
     * entry is greyed on the strength of it.</p>
     *
     * <p>It is not a guarantee. A permissions mod can revoke the command after the tree was sent, and then
     * the entry looks enabled and the send is refused. That case is handled by not pretending: nothing here
     * reports success, and the server's refusal arrives in chat as the server worded it.</p>
     */
    private boolean canSendTeleport()
    {
        final LocalPlayer player = this.minecraft.player;
        if (player == null || player.connection == null)
        {
            return false;
        }
        final ClientPacketListener connection = player.connection;
        return connection.getCommands().getRoot().getChild(TELEPORT_COMMAND) != null;
    }

    /**
     * Sends {@code /tp x y z} as the player and closes the map.
     *
     * <h2>What this can and cannot do</h2>
     * <p>A client-only mod cannot move the player. Position is server state; every client-side attempt to
     * change it is either rejected by the server's move check or is straightforwardly cheating on a server
     * that has none. The only honest route is to ask, as the player, using a command the player is entitled
     * to use -- so that is what this does, and in single player with cheats on it works, and on a server
     * where the player has no permission it is refused.</p>
     *
     * <p><b>The refusal is not swallowed and no success is faked.</b> Nothing here waits for a result or
     * reports one: whatever the server says about the command -- "You do not have permission to use this
     * command", an unknown-command parse error, or nothing at all because it worked -- lands in the player's
     * chat in the server's own words, which is both more accurate and more useful than anything this screen
     * could invent.</p>
     *
     * <p>The y is the recorded surface plus one, so the player arrives standing on the ground rather than
     * inside it. That is the whole reason the tile format grew a height plane: without it the only options
     * were suffocating in stone or falling out of the sky. Where the map has no surface to stand on,
     * {@link #UNKNOWN_HEIGHT_TELEPORT_Y} is used instead and the menu entry says so before it is clicked --
     * see {@link #openContextMenu}.</p>
     */
    private void teleportTo(final int x, final int y, final int z)
    {
        final LocalPlayer player = this.minecraft.player;
        if (player == null || player.connection == null)
        {
            return;
        }
        this.onClose();
        player.connection.sendCommand("%s %d %d %d".formatted(TELEPORT_COMMAND, x, y, z));
    }

    /**
     * Writes the visible part of the map to a PNG under the game directory and says where it went.
     *
     * <p>Synchronous, on the client thread. The work is a memory copy of at most {@value MapExport#MAX_SIDE}
     * squared pixels and one STB encode, the game is paused behind this screen in single player, and a
     * background thread would need its own copy of every tile it touched to be safe against the scanner
     * writing into them. A brief hitch is the cheaper answer.</p>
     */
    private void savePng()
    {
        final double scale = this.pixelsPerBlock();
        final double offsetX = this.width / 2.0 - this.centreX * scale;
        final double offsetZ = this.height / 2.0 - this.centreZ * scale;

        final int blockX = (int) Math.floor(-offsetX / scale);
        final int blockZ = (int) Math.floor((HEADER_HEIGHT - offsetZ) / scale);
        final int width = (int) Math.ceil(this.width / scale);
        final int height = (int) Math.ceil((this.height - HEADER_HEIGHT) / scale);

        try
        {
            final Path gameDir = FabricLoader.getInstance().getGameDir();
            final Path file = MapExport.write(
              this.service, gameDir, this.service.dimension(), blockX, blockZ, width, height);

            final Path shown = relativise(gameDir, file);
            this.chat(Component.translatable("gui.worldmap.export.saved", shown.toString()));
            if (width > MapExport.MAX_SIDE || height > MapExport.MAX_SIDE)
            {
                this.chat(Component.translatable("gui.worldmap.export.trimmed", MapExport.MAX_SIDE));
            }
        }
        catch (final Exception e)
        {
            WorldMapClient.LOGGER.warn("Could not export the map", e);
            this.chat(Component.translatable("gui.worldmap.export.failed", String.valueOf(e.getMessage())));
        }
    }

    private static Path relativise(final Path gameDir, final Path file)
    {
        try
        {
            return gameDir.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        }
        catch (final IllegalArgumentException e)
        {
            return file;
        }
    }

    /**
     * Puts one line in the player's chat. {@code ChatComponent#addClientSystemMessage} is public API, needs
     * no server and is named for exactly this case: the map talking to the player, not a message relayed
     * from anywhere.
     */
    private void chat(final Component message)
    {
        if (this.minecraft.gui != null)
        {
            this.minecraft.gui.hud.getChat().addClientSystemMessage(message);
        }
    }

    @Override
    public boolean mouseReleased(final MouseButtonEvent event)
    {
        this.panning = false;
        return true;
    }

    @Override
    public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy)
    {
        this.pointerX = event.x();
        this.pointerY = event.y();
        if (!this.panning)
        {
            return false;
        }
        final double scale = this.pixelsPerBlock();
        this.centreX -= dx / scale;
        this.centreZ -= dy / scale;
        return true;
    }

    /**
     * Vertical scroll zooms, horizontal scroll pans.
     *
     * <h2>Why the wheel and a touchpad need telling apart, and how far that is possible</h2>
     * <p>One notch of a wheel is one event, so stepping the ladder once per event is right for a wheel. A
     * two-finger swipe on a touchpad is dozens of events, and stepping once per event runs the whole ladder
     * in a single gesture.</p>
     *
     * <p>Nothing in the API says which is which. {@code SDL_MouseWheelEvent} carries a device id and a
     * direction, but {@code SDLEventHandler.handleMouseWheelEvent} forwards only its {@code x()} and
     * {@code y()} floats; {@code MouseHandler.onScroll} scales them by the wheel-sensitivity option and
     * calls {@code Screen.mouseScrolled(x, y, scrollX, scrollY)} -- four bare doubles, no event object, no
     * source. Recovering more would mean a mixin, and this mod has none.</p>
     *
     * <p>What is left is the size of the delta. SDL reports one detent as 1.0, and a touchpad reports the
     * same unit in fractions of it. So an event a whole unit or more tall is taken as a notch and steps the
     * ladder at once -- the wheel behaves exactly as it always did -- and anything smaller is banked, and
     * steps only once the bank reaches {@code scrollZoomThreshold} units. The unit is the sensitivity
     * option itself, since the raw 1.0 has already been multiplied by it before it gets here.</p>
     *
     * <p>The block under the cursor is held under the cursor per rung stepped, not per event: an event that
     * banks without crossing the threshold changes nothing to anchor. During a touchpad gesture the fingers
     * are on the pad and the pointer is not moving, so this is the same anchor either way.</p>
     */
    @Override
    public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY)
    {
        this.pointerX = x;
        this.pointerY = y;

        final long now = System.nanoTime();
        if (now - this.lastScrollNanos > SCROLL_GESTURE_GAP_NANOS)
        {
            this.zoomScroll = 0.0;
        }
        this.lastScrollNanos = now;

        boolean handled = false;
        if (scrollX != 0.0)
        {
            handled = this.panBy(scrollX);
        }
        if (scrollY != 0.0)
        {
            handled |= this.zoomBy(scrollY, x, y);
        }
        return handled;
    }

    /**
     * Applies one vertical scroll delta to the zoom ladder.
     *
     * @param delta   the delta, in wheel units scaled by the sensitivity option.
     * @param anchorX screen x to hold fixed.
     * @param anchorY screen y to hold fixed.
     * @return true; the map consumes vertical scroll whether or not it moved a rung.
     */
    private boolean zoomBy(final double delta, final double anchorX, final double anchorY)
    {
        final double unit = this.scrollUnit();
        if (Math.abs(delta) >= unit * 0.999)
        {
            // A wheel notch, or several coalesced into one event. Nothing is banked across it.
            this.zoomScroll = 0.0;
            final int rungs = Math.max(1, (int) (Math.abs(delta) / unit));
            this.changeZoom(delta > 0.0 ? rungs : -rungs, anchorX, anchorY);
            return true;
        }

        if (this.zoomScroll != 0.0 && Math.signum(delta) != Math.signum(this.zoomScroll))
        {
            this.zoomScroll = 0.0;
        }
        this.zoomScroll += delta;

        final double perRung = unit * this.service.config().scrollZoomThreshold;
        final int rungs = (int) (this.zoomScroll / perRung);
        if (rungs != 0)
        {
            this.zoomScroll -= rungs * perRung;
            this.changeZoom(rungs, anchorX, anchorY);
        }
        return true;
    }

    /**
     * Pans on a horizontal scroll delta -- a two-finger sideways swipe on a touchpad, or a tilt wheel.
     *
     * <p>No accumulator here, unlike the zoom: panning is continuous, {@code centreX} is a double, and a
     * stream of tiny deltas adds up in it on its own.</p>
     *
     * @param delta the delta, in wheel units scaled by the sensitivity option.
     * @return true if the map moved.
     */
    private boolean panBy(final double delta)
    {
        final double pixels = this.service.config().scrollPanPixels;
        if (pixels <= 0.0)
        {
            return false;
        }
        this.centreX += delta / this.scrollUnit() * pixels / this.pixelsPerBlock();
        return true;
    }

    /**
     * @return the size of one wheel notch as this screen sees it. {@code MouseHandler.onScroll} multiplies
     *     SDL's raw delta -- 1.0 for one detent -- by the wheel-sensitivity option before any screen is
     *     called, so the option's value is the size of a notch. Floored well above zero because the option
     *     goes down to 0.01 and it is used as a divisor.
     */
    private double scrollUnit()
    {
        return Math.max(1.0E-3, this.minecraft.options.mouseWheelSensitivity().get());
    }

    @Override
    public boolean keyPressed(final KeyEvent event)
    {
        // Esc shuts the context menu before it shuts the map, which is what every other menu in the game
        // does. With the menu closed it falls through to Screen exactly as before.
        if (this.menu.isOpen() && event.key() == InputConstants.KEY_ESCAPE)
        {
            this.menu.close();
            return true;
        }

        switch (event.key())
        {
            case InputConstants.KEY_SPACE ->
            {
                this.centreOnPlayer();
                return true;
            }
            case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD ->
            {
                this.changeZoom(1, this.pointerX, this.pointerY);
                return true;
            }
            case InputConstants.KEY_MINUS, SDL_SCANCODE_KP_MINUS ->
            {
                this.changeZoom(-1, this.pointerX, this.pointerY);
                return true;
            }
            default ->
            {
                // Fall through to the map key and then to Screen.
            }
        }

        final KeyMapping open = WorldMapKeys.openMap();
        if (open != null)
        {
            final InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(open);
            if (bound.getType() == InputConstants.Type.KEYBOARD && bound.getValue() == event.key())
            {
                this.onClose();
                return true;
            }
        }

        // Everything else, Esc included, goes to Screen -- which is what closes and resumes.
        return super.keyPressed(event);
    }

    /**
     * Steps one rung up or down the zoom ladder, keeping whatever block is under the given point under it.
     *
     * @param steps  +1 to zoom in, -1 to zoom out.
     * @param anchorX screen x to hold fixed.
     * @param anchorY screen y to hold fixed.
     */
    private void changeZoom(final int steps, final double anchorX, final double anchorY)
    {
        final int previous = this.zoom;
        this.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, this.zoom + steps));
        if (this.zoom == previous)
        {
            return;
        }

        final double before = Math.scalb(1.0, previous);
        final double after = this.pixelsPerBlock();
        final double dx = anchorX - this.width / 2.0;
        final double dy = anchorY - this.height / 2.0;
        this.centreX = this.centreX + dx / before - dx / after;
        this.centreZ = this.centreZ + dy / before - dy / after;
    }

    private void centreOnPlayer()
    {
        final LocalPlayer player = this.minecraft.player;
        if (player != null)
        {
            this.centreX = player.getX();
            this.centreZ = player.getZ();
        }
    }

    /**
     * How many times a tile is halved on its way to a texture.
     *
     * <p>Exactly enough that one texel is one screen pixel: a tile is {@code 512 * 2^zoom} pixels wide, so
     * below one pixel per block it is halved {@code -zoom} times. Nothing is lost by it -- the sampler is
     * nearest and would have discarded the same pixels -- and at the widest zoom it is the difference
     * between a megabyte of video memory per tile and a kilobyte, which is what makes a view of thousands of
     * tiles possible at all. Whether the discarded pixels are averaged in or simply dropped is the
     * {@code smoothZoomedOut} setting, and it is passed separately.</p>
     *
     * @return 0 at one pixel per block and above, up to {@code -MIN_ZOOM} at the widest.
     */
    private int tileDetail()
    {
        return Math.max(0, -this.zoom);
    }

    private double pixelsPerBlock()
    {
        return Math.scalb(1.0, this.zoom);
    }
}
