package com.minecolonies.core.client.render.worldevent;

import com.ldtteam.blockui.util.color.ColourARGB;
import com.ldtteam.blockui.util.color.ColourQuartet4i;
import com.ldtteam.blockui.util.color.IColour;
import com.ldtteam.structurize.items.ModItems;
import com.ldtteam.structurize.util.WorldRenderMacros;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.util.MutableChunkPos;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;

/**
 * Draws the colony / chunk-ticket borders around the player.
 * <p>
 * PORT-26.2: {@code Tesselator}, {@code VertexBuffer} and immediate-mode drawing are all gone, and so is BlockUI's
 * {@code ColouredVertexConsumer}. The expensive part (walking the loaded chunks and asking for claim data) is still
 * cached exactly as before, but the result is now a cached <i>vertex list</i> that is re-submitted every frame through
 * {@code SubmitNodeCollector#submitCustomGeometry}, which is the only way mod geometry reaches the screen in 26.2.
 * {@code ColourQuartet} split into {@code ColourQuartet4i}/{@code 4f}, and {@code ChatFormatting} no longer carries a
 * colour value — that table moved to {@link TextColor}.
 */
public class ColonyBorderRenderer
{
    private static final int RENDER_DIST_THRESHOLD = 3;
    private static final int CHUNK_SIZE = 16;
    private static final int PLAYER_CHUNK_STEP = CHUNK_SIZE / 4;

    /**
     * How far around the player the claims are watched for changes, in chunks.
     * <p>
     * Two is enough to cover everything a scepter can reach: the land claim scepter takes the clicked chunk and the
     * eight around it, and the clicked block is within arm's length, so the furthest chunk it can touch is two away.
     */
    private static final int WATCHED_RADIUS = 2;

    /**
     * The colour a hostile territory's border is drawn in when it has no colour of its own: full red, brighter and
     * more saturated than the {@code (255, 70, 70)} used for an ordinary foreign colony, so that "somebody else's"
     * and "the enemy's" are told apart at a glance rather than only by shade.
     */
    private static final IColour HOSTILE_BORDER = new ColourQuartet4i(255, 0, 0, 255);

    private static BorderLines colonies     = null;
    private static BorderLines chunktickets = null;
    private static ChunkPos                     lastPlayerChunkPos = null;
    private static IColonyView lastColony = null;

    /**
     * What the claims around the player looked like when the geometry was last built, so that redrawing a border
     * shows up without having to walk into the next chunk first.
     */
    private static long lastClaimSignature = 0L;

    static void render(final WorldEventContext ctx)
    {
        if (!showsBorders(ctx.mainHandItem) || !ctx.hasNearestColony())
        {
            return;
        }

        final ChunkPos playerChunkPos = ChunkPos.containing(ctx.clientPlayer.blockPosition());
        final long claimSignature = claimSignature(ctx, playerChunkPos);

        if (lastColony != ctx.nearestColony || !lastPlayerChunkPos.equals(playerChunkPos) || claimSignature != lastClaimSignature)
        {
            lastColony = ctx.nearestColony;
            lastPlayerChunkPos = playerChunkPos;
            lastClaimSignature = claimSignature;

            final Map<ChunkPos, Integer> coloniesMap = new HashMap<>();
            final Map<ChunkPos, Integer> chunkticketsMap = new HashMap<>();
            final Map<ChunkPos, IChunkClaimData> partialMap = new HashMap<>();
            final int nearestColonyId = ctx.nearestColony.getID();
            final int playerRenderDist = Math.max(ctx.clientRenderDist - RENDER_DIST_THRESHOLD, 2);
            final int range = Math.max(ctx.clientRenderDist, MineColonies.getConfig().getServer().maxColonySize.get());

            for (int chunkX = -range; chunkX <= range; chunkX++)
            {
                for (int chunkZ = -range; chunkZ <= range; chunkZ++)
                {
                    final LevelChunk chunk = ctx.clientLevel.getChunk(playerChunkPos.x() + chunkX, playerChunkPos.z() + chunkZ);
                    if (chunk.isEmpty()) { continue; }
                    final ChunkPos chunkPos = chunk.getPos();

                    final IChunkClaimData cap = IColonyManager.getInstance().getClaimData(ctx.nearestColony.getDimension(), chunkPos);;
                    if (cap != null)
                    {
                        if (cap.hasPartialClaim())
                        {
                            // A chunk whose border was drawn by hand is not a whole claim, so it goes into the
                            // chunk map as unclaimed: its whole-chunk neighbours then draw their own edge against
                            // it, and its real outline is drawn below, column by column.
                            partialMap.put(chunkPos, cap);
                            coloniesMap.put(chunkPos, NO_COLONY_ID);
                        }
                        else
                        {
                            coloniesMap.put(chunkPos, cap.getOwningColony());
                        }
                    }

                    if (ctx.nearestColony.getTicketedChunks().contains(chunkPos.pack()))
                    {
                        chunkticketsMap.put(chunkPos, nearestColonyId);
                    }
                    else
                    {
                        chunkticketsMap.put(chunkPos, 0);
                    }
                }
            }

            colonies = draw(ctx, coloniesMap, nearestColonyId, playerChunkPos, playerRenderDist);
            drawPartialClaims(ctx, colonies, partialMap, coloniesMap, nearestColonyId, playerChunkPos, playerRenderDist);
            chunktickets = draw(ctx, chunkticketsMap, nearestColonyId, playerChunkPos, playerRenderDist);
        }

        final BorderLines lines = Minecraft.getInstance().hasControlDown() ? chunktickets : colonies;
        if (lines == null || lines.isEmpty())
        {
            return;
        }

        ctx.pushPoseCameraToPos(lastPlayerChunkPos.getWorldPosition());
        ctx.submitNodeCollector.submitCustomGeometry(ctx.poseStack, WorldRenderMacros.LINES, lines::emit);
        ctx.popPose();
    }

    private static BorderLines draw(final WorldEventContext ctx,
        final Map<ChunkPos, Integer> mapToDraw,
        final int playerColonyId,
        final ChunkPos playerChunkPos,
        final int playerRenderDist)
    {
        final MutableChunkPos mutableChunkPos = new MutableChunkPos(0, 0);
        final Map<Integer, IColour> colonyColours = new HashMap<>();
        final boolean useColonyColour = IMinecoloniesAPI.getInstance().getConfig().getClient().colonyteamborders.get();

        final BorderLines buf = new BorderLines();
        mapToDraw.forEach((chunkPos, colonyId) -> {
            if (colonyId == 0 || chunkPos.x() <= playerChunkPos.x() - playerRenderDist || chunkPos.x() >= playerChunkPos.x() + playerRenderDist
                || chunkPos.z() <= playerChunkPos.z() - playerRenderDist || chunkPos.z() >= playerChunkPos.z() + playerRenderDist)
            {
                return;
            }

            final boolean isPlayerChunkX = colonyId == playerColonyId && chunkPos.x() == playerChunkPos.x();
            final boolean isPlayerChunkZ = colonyId == playerColonyId && chunkPos.z() == playerChunkPos.z();
            final float minX = chunkPos.getMinBlockX() - playerChunkPos.getMinBlockX();
            final float maxX = chunkPos.getMaxBlockX() - playerChunkPos.getMinBlockX() + 1.0f;
            final float minZ = chunkPos.getMinBlockZ() - playerChunkPos.getMinBlockZ();
            final float maxZ = chunkPos.getMaxBlockZ() - playerChunkPos.getMinBlockZ() + 1.0f;
            final int minY = ctx.clientLevel.getMinY();
            final int maxY = ctx.clientLevel.getMaxY() + 1;
            final int testedColonyId = colonyId;

            buf.defaultColor = borderColour(ctx, colonyId, playerColonyId, useColonyColour, colonyColours);

            mutableChunkPos.setX(chunkPos.x());
            mutableChunkPos.setZ(chunkPos.z() - 1);
            final boolean north = mapToDraw.getOrDefault(mutableChunkPos, -1) != testedColonyId;

            mutableChunkPos.setZ(chunkPos.z() + 1);
            final boolean south = mapToDraw.getOrDefault(mutableChunkPos, -1) != testedColonyId;

            mutableChunkPos.setX(chunkPos.x() + 1);
            mutableChunkPos.setZ(chunkPos.z());
            final boolean east = mapToDraw.getOrDefault(mutableChunkPos, -1) != testedColonyId;

            mutableChunkPos.setX(chunkPos.x() - 1);
            final boolean west = mapToDraw.getOrDefault(mutableChunkPos, -1) != testedColonyId;

            // vert lines
            if (north || west)
            {
                buf.addVertex(minX, minY, minZ);
                buf.addVertex(minX, maxY, minZ);
            }
            if (north || east)
            {
                buf.addVertex(maxX, minY, minZ);
                buf.addVertex(maxX, maxY, minZ);
            }
            if (south || west)
            {
                buf.addVertex(minX, minY, maxZ);
                buf.addVertex(minX, maxY, maxZ);
            }
            if (south || east)
            {
                buf.addVertex(maxX, minY, maxZ);
                buf.addVertex(maxX, maxY, maxZ);
            }

            // horizontal lines
            if (north)
            {
                if (isPlayerChunkX)
                {
                    for (int shift = PLAYER_CHUNK_STEP; shift < CHUNK_SIZE; shift += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(minX + shift, minY, minZ);
                        buf.addVertex(minX + shift, maxY, minZ);
                    }
                    for (int y = minY + PLAYER_CHUNK_STEP; y < maxY; y += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(minX, y, minZ);
                        buf.addVertex(maxX, y, minZ);
                    }
                }
                else
                {
                    for (int y = minY + CHUNK_SIZE; y < maxY; y += CHUNK_SIZE)
                    {
                        buf.addVertex(minX, y, minZ);
                        buf.addVertex(maxX, y, minZ);
                    }
                }
            }
            if (south)
            {
                if (isPlayerChunkX)
                {
                    for (int shift = PLAYER_CHUNK_STEP; shift < CHUNK_SIZE; shift += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(minX + shift, minY, maxZ);
                        buf.addVertex(minX + shift, maxY, maxZ);
                    }
                    for (int y = minY + PLAYER_CHUNK_STEP; y < maxY; y += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(minX, y, maxZ);
                        buf.addVertex(maxX, y, maxZ);
                    }
                }
                else
                {
                    for (int y = minY + CHUNK_SIZE; y < maxY; y += CHUNK_SIZE)
                    {
                        buf.addVertex(minX, y, maxZ);
                        buf.addVertex(maxX, y, maxZ);
                    }
                }
            }
            if (west)
            {
                if (isPlayerChunkZ)
                {
                    for (int shift = PLAYER_CHUNK_STEP; shift < CHUNK_SIZE; shift += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(minX, minY, minZ + shift);
                        buf.addVertex(minX, maxY, minZ + shift);
                    }
                    for (int y = minY + PLAYER_CHUNK_STEP; y < maxY; y += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(minX, y, minZ);
                        buf.addVertex(minX, y, maxZ);
                    }
                }
                else
                {
                    for (int y = minY + CHUNK_SIZE; y < maxY; y += CHUNK_SIZE)
                    {
                        buf.addVertex(minX, y, minZ);
                        buf.addVertex(minX, y, maxZ);
                    }
                }
            }
            if (east)
            {
                if (isPlayerChunkZ)
                {
                    for (int shift = PLAYER_CHUNK_STEP; shift < CHUNK_SIZE; shift += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(maxX, minY, minZ + shift);
                        buf.addVertex(maxX, maxY, minZ + shift);
                    }
                    for (int y = minY + PLAYER_CHUNK_STEP; y < maxY; y += PLAYER_CHUNK_STEP)
                    {
                        buf.addVertex(maxX, y, minZ);
                        buf.addVertex(maxX, y, maxZ);
                    }
                }
                else
                {
                    for (int y = minY + CHUNK_SIZE; y < maxY; y += CHUNK_SIZE)
                    {
                        buf.addVertex(maxX, y, minZ);
                        buf.addVertex(maxX, y, maxZ);
                    }
                }
            }
        });

        return buf;
    }

    /**
     * Whether holding this item should draw the colony borders.
     * <p>
     * Structurize's build tool has always drawn them, which is where you look at a border while placing a hut. The
     * three scepters are where you look at one while <i>changing</i> it, so they draw it too — claiming, releasing or
     * repainting a border with nothing on screen to show for it is guesswork.
     *
     * @param stack what the player is holding.
     * @return true if the borders should be drawn.
     */
    private static boolean showsBorders(final ItemStack stack)
    {
        final Item item = stack.getItem();
        return item == ModItems.buildTool.get()
                 || item == com.minecolonies.api.items.ModItems.scepterClaim
                 || item == com.minecolonies.api.items.ModItems.scepterUnclaim
                 || item == com.minecolonies.api.items.ModItems.scepterBorder
                 || item == com.minecolonies.api.items.ModItems.scepterTerritory;
    }

    /**
     * A number that changes whenever the claims near the player do.
     * <p>
     * The geometry is expensive enough to be cached until the player walks into another chunk, which was fine while
     * only the build tool drew it — it does not change what it draws. A scepter does, and painting a border column by
     * column with the picture frozen until you walk away is unusable. This is cheap enough to run every frame:
     * {@value #WATCHED_RADIUS} chunks around the player is a couple of dozen map lookups, and it covers everything a
     * scepter can reach.
     *
     * @param ctx            the render context.
     * @param playerChunkPos the chunk the player is in.
     * @return the signature, to be compared against the one the cached geometry was built from.
     */
    private static long claimSignature(final WorldEventContext ctx, final ChunkPos playerChunkPos)
    {
        long signature = 0L;

        for (int x = -WATCHED_RADIUS; x <= WATCHED_RADIUS; x++)
        {
            for (int z = -WATCHED_RADIUS; z <= WATCHED_RADIUS; z++)
            {
                final IChunkClaimData data = IColonyManager.getInstance()
                  .getClaimData(ctx.nearestColony.getDimension(), new ChunkPos(playerChunkPos.x() + x, playerChunkPos.z() + z));

                // Both halves matter: the owner catches a chunk being claimed or released, the column count catches a
                // border being painted inside one that already belongs to the same colony.
                signature = signature * 31L + (data == null ? 0L : (long) data.getOwningColony() * 257L + data.getClaimedColumnCount());
            }
        }

        return signature;
    }

    /**
     * The colour a colony's border is drawn in, which is either its team colour or white for the player's own colony
     * and red for anyone else's.
     *
     * @param ctx              the render context.
     * @param colonyId         the colony whose border is being drawn.
     * @param playerColonyId   the colony the player belongs to.
     * @param useColonyColour  whether the client is configured to use team colours.
     * @param cache            the per-frame cache of resolved team colours.
     * @return the colour.
     */
    private static IColour borderColour(final WorldEventContext ctx,
      final int colonyId,
      final int playerColonyId,
      final boolean useColonyColour,
      final Map<Integer, IColour> cache)
    {
        // Ahead of both branches below, and not merged into the team colour lookup, because hostile ground has to
        // read as hostile whatever the client has chosen -- including with colonyteamborders off, where the ordinary
        // branch would paint every foreign border the same (255, 70, 70).
        //
        // A territory's own colour wins when it has one, so several enemies can be told apart on the map, and red is
        // what is left when it has not. WHITE is not a colour a territory may have: it is the colour that means "this
        // is yours", and it is what an untouched colony's team colour is, so a territory made before this existed
        // reads as the plain enemy red rather than as the player's own ground.
        if (isHostile(ctx, colonyId))
        {
            return cache.computeIfAbsent(colonyId, id ->
            {
                final IColonyView colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyView(id, ctx.clientLevel.dimension());
                if (colony == null || colony.getTeamColonyColor() == ChatFormatting.WHITE)
                {
                    return HOSTILE_BORDER;
                }

                final TextColor textColor = TextColor.fromLegacyFormat(colony.getTeamColonyColor());
                return textColor == null ? HOSTILE_BORDER : new ColourARGB(textColor.getValue() | 0xff000000).asIntQuartet();
            });
        }

        if (useColonyColour)
        {
            return cache.computeIfAbsent(colonyId, id ->
            {
                final IColonyView colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyView(id, ctx.clientLevel.dimension());
                final ChatFormatting team = colony != null ? colony.getTeamColonyColor()
                        : id == playerColonyId ? ChatFormatting.WHITE : ChatFormatting.RED;
                final TextColor textColor = TextColor.fromLegacyFormat(team);
                final int rgb = textColor != null ? textColor.getValue() : 0xFFFFFF;
                return new ColourARGB(rgb | 0xff000000).asIntQuartet();
            });
        }

        return colonyId == playerColonyId ? new ColourQuartet4i(255, 255, 255, 255) : new ColourQuartet4i(255, 70, 70, 255);
    }

    /**
     * Whether the colony owning a chunk is a hostile territory.
     * <p>
     * Asked of the client's view of that colony, which is the only place the flag exists on this side. A view the
     * client has never been sent answers "not hostile", which is the right way round to be wrong: an unknown colony
     * keeps the colour it has always had rather than every unknown border suddenly turning red.
     *
     * @param ctx      the render context.
     * @param colonyId the colony owning the chunk.
     * @return true if that colony is enemy ground.
     */
    private static boolean isHostile(final WorldEventContext ctx, final int colonyId)
    {
        final IColonyView colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyView(colonyId, ctx.clientLevel.dimension());
        return colony != null && colony.isHostile();
    }

    /**
     * Draw the outline of the chunks whose border was redrawn by hand with the border scepter.
     * <p>
     * {@link #draw} works a chunk at a time and can only ever produce lines on the chunk grid, which is the whole
     * point of the scepter to escape. This walks the columns of such a chunk instead and puts a line wherever a
     * claimed column meets something that is not the same colony's land — the column next to it in the same chunk, or
     * whatever the neighbouring chunk is.
     *
     * @param ctx              the render context.
     * @param buf              the geometry to add to, the same one the chunk borders went into.
     * @param partials         the partly claimed chunks in range.
     * @param chunkOwners      the owning colony of every chunk in range, with the partial ones marked unclaimed.
     * @param playerColonyId   the colony the player belongs to.
     * @param playerChunkPos   the chunk the player is in, the origin of the geometry.
     * @param playerRenderDist how far out to draw.
     */
    private static void drawPartialClaims(final WorldEventContext ctx,
      final BorderLines buf,
      final Map<ChunkPos, IChunkClaimData> partials,
      final Map<ChunkPos, Integer> chunkOwners,
      final int playerColonyId,
      final ChunkPos playerChunkPos,
      final int playerRenderDist)
    {
        if (partials.isEmpty())
        {
            return;
        }

        final Map<Integer, IColour> colonyColours = new HashMap<>();
        final boolean useColonyColour = IMinecoloniesAPI.getInstance().getConfig().getClient().colonyteamborders.get();
        final MutableChunkPos lookupChunk = new MutableChunkPos(0, 0);
        final BlockPos.MutableBlockPos lookupColumn = new BlockPos.MutableBlockPos();

        final int baseX = playerChunkPos.getMinBlockX();
        final int baseZ = playerChunkPos.getMinBlockZ();
        final int minY = ctx.clientLevel.getMinY();
        final int maxY = ctx.clientLevel.getMaxY() + 1;

        partials.forEach((chunkPos, claimData) -> {
            final int owner = claimData.getOwningColony();
            if (owner == NO_COLONY_ID || chunkPos.x() <= playerChunkPos.x() - playerRenderDist || chunkPos.x() >= playerChunkPos.x() + playerRenderDist
                  || chunkPos.z() <= playerChunkPos.z() - playerRenderDist || chunkPos.z() >= playerChunkPos.z() + playerRenderDist)
            {
                return;
            }

            buf.defaultColor = borderColour(ctx, owner, playerColonyId, useColonyColour, colonyColours);

            for (int dx = 0; dx < CHUNK_SIZE; dx++)
            {
                for (int dz = 0; dz < CHUNK_SIZE; dz++)
                {
                    final int worldX = chunkPos.getMinBlockX() + dx;
                    final int worldZ = chunkPos.getMinBlockZ() + dz;
                    if (!isColumnOwnedBy(partials, chunkOwners, lookupChunk, lookupColumn, worldX, worldZ, owner))
                    {
                        continue;
                    }

                    final float x = worldX - baseX;
                    final float z = worldZ - baseZ;

                    if (!isColumnOwnedBy(partials, chunkOwners, lookupChunk, lookupColumn, worldX - 1, worldZ, owner))
                    {
                        addWall(buf, x, z, x, z + 1.0f, minY, maxY);
                    }
                    if (!isColumnOwnedBy(partials, chunkOwners, lookupChunk, lookupColumn, worldX + 1, worldZ, owner))
                    {
                        addWall(buf, x + 1.0f, z, x + 1.0f, z + 1.0f, minY, maxY);
                    }
                    if (!isColumnOwnedBy(partials, chunkOwners, lookupChunk, lookupColumn, worldX, worldZ - 1, owner))
                    {
                        addWall(buf, x, z, x + 1.0f, z, minY, maxY);
                    }
                    if (!isColumnOwnedBy(partials, chunkOwners, lookupChunk, lookupColumn, worldX, worldZ + 1, owner))
                    {
                        addWall(buf, x, z + 1.0f, x + 1.0f, z + 1.0f, minY, maxY);
                    }
                }
            }
        });
    }

    /**
     * Whether one column of the world belongs to a given colony, wherever it happens to fall.
     *
     * @param partials     the partly claimed chunks in range.
     * @param chunkOwners  the owning colony of every chunk in range.
     * @param lookupChunk  a mutable chunk position to look up with, so this does not allocate per column.
     * @param lookupColumn a mutable block position, likewise.
     * @param worldX       the column's x.
     * @param worldZ       the column's z.
     * @param owner        the colony being asked about.
     * @return true if the column is that colony's land.
     */
    private static boolean isColumnOwnedBy(final Map<ChunkPos, IChunkClaimData> partials,
      final Map<ChunkPos, Integer> chunkOwners,
      final MutableChunkPos lookupChunk,
      final BlockPos.MutableBlockPos lookupColumn,
      final int worldX,
      final int worldZ,
      final int owner)
    {
        lookupChunk.setX(worldX >> 4);
        lookupChunk.setZ(worldZ >> 4);

        final IChunkClaimData partial = partials.get(lookupChunk);
        if (partial != null)
        {
            return partial.getOwningColony() == owner && partial.isColumnClaimed(lookupColumn.set(worldX, 0, worldZ));
        }

        return chunkOwners.getOrDefault(lookupChunk, NO_COLONY_ID) == owner;
    }

    /**
     * Add one block wide slice of border, drawn the same way a chunk border is: upright lines at both ends and
     * horizontal ones every chunk of height, from the bottom of the world to the top.
     *
     * @param buf  the geometry to add to.
     * @param x1   the first end's x.
     * @param z1   the first end's z.
     * @param x2   the second end's x.
     * @param z2   the second end's z.
     * @param minY the bottom of the world.
     * @param maxY the top of the world.
     */
    private static void addWall(final BorderLines buf, final float x1, final float z1, final float x2, final float z2, final int minY, final int maxY)
    {
        buf.addVertex(x1, minY, z1);
        buf.addVertex(x1, maxY, z1);
        buf.addVertex(x2, minY, z2);
        buf.addVertex(x2, maxY, z2);

        for (int y = minY; y < maxY; y += CHUNK_SIZE)
        {
            buf.addVertex(x1, y, z1);
            buf.addVertex(x2, y, z2);
        }
    }

    /**
     * Cleanup on logout.
     */
    public static void cleanup()
    {
        colonies = null;
        chunktickets = null;
        lastColony = null;
        lastPlayerChunkPos = null;
        lastClaimSignature = 0L;
    }

    /**
     * Cached border geometry: a flat list of line vertices plus their colours, re-emitted every frame.
     * <p>
     * PORT-26.2: replaces the {@code VertexBuffer} the 1.21.1 code uploaded once — 26.2 has no mod accessible
     * vertex-buffer upload path, everything goes through {@code submitCustomGeometry}.
     */
    private static final class BorderLines implements net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer
    {
        private final FloatArrayList positions = new FloatArrayList();
        private final IntArrayList   colours   = new IntArrayList();

        /**
         * Colour applied to every vertex added from here on; the direct replacement of
         * {@code ColouredVertexConsumer#defaultColor}.
         */
        private IColour defaultColor = new ColourQuartet4i(255, 255, 255, 255);

        private void addVertex(final float x, final float y, final float z)
        {
            positions.add(x);
            positions.add(y);
            positions.add(z);
            colours.add(defaultColor.argb());
        }

        private boolean isEmpty()
        {
            return positions.isEmpty();
        }

        @Override
        public void render(final PoseStack.Pose pose, final VertexConsumer buffer)
        {
            for (int i = 0, v = 0; i < positions.size(); i += 3, v++)
            {
                buffer.addVertex(pose, positions.getFloat(i), positions.getFloat(i + 1), positions.getFloat(i + 2))
                  .setColor(colours.getInt(v));
            }
        }

        private void emit(final PoseStack.Pose pose, final VertexConsumer buffer)
        {
            render(pose, buffer);
        }
    }
}
