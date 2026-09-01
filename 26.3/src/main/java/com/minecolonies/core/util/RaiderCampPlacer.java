package com.minecolonies.core.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.ColonyUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.events.raid.RaidManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;
import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Puts a raider camp on the ground near a colony, at the moment something asks for one.
 *
 * <h2>Why at runtime and not through worldgen</h2>
 * The barbarian camp already exists as world generation: a jigsaw structure on a {@code random_spread} set at
 * {@code spacing: 55, separation: 25} (
 * {@code data/minecolonies/worldgen/structure_set/barbarian_camp.json}). That is one placement attempt per 55x55
 * chunks -- 880 x 880 blocks -- and the attempt only succeeds if the chosen chunk happens to be in one of the
 * biomes the camp's biome tag lists. {@code desert_camp} and {@code amazon_camp} carry the identical salt, spacing and
 * separation, so all three compete for the same chunk in each region and at most one of them can win it. A colony
 * therefore has no reliable camp anywhere near it, and a quest that sends the player to one it cannot promise exists
 * is not a quest.
 * <p>
 * Making the structure set denser is the other way to fix that, and it is the wrong one: a worldgen change applies to
 * every world including existing saves, cannot be undone for chunks already generated, and is untestable without
 * generating terrain. Placing the camp when the quest needs it is self-contained, affects nothing the player has not
 * asked for, and can be driven from a command.
 *
 * <h2>What it places</h2>
 * The worldgen templates themselves -- {@code minecolonies:camps/small_barbarian_camp} and friends, the same
 * {@code .nbt} files the jigsaw would have used, complete with their {@code minecraft:spawner} block entities and their
 * loot barrels. Nothing new is added to the repository: no blueprint, no data file, no asset. The
 * {@code minecolonies:placeholder_replacement} processor list is applied exactly as the template pool applies it,
 * because the templates are 60 % Structurize substitution blocks and without it the camp arrives full of them.
 *
 * <h2>What it costs to run</h2>
 * One synchronous burst on the server thread, at the moment a quest is accepted or the command is typed, and nothing
 * afterwards. The search evaluates at most {@link #CANDIDATE_ATTEMPTS} sites; each one reads at most a hundred
 * columns and abandons the site on the first bad column, so the common case is a handful of block reads per
 * candidate. Placement itself is the larger half: the footprint is cleared and then written, which for the small
 * camp is about 7000 block writes. Measured on a dedicated server, a placement that rejected 120 candidates first
 * produced no "Can't keep up" warning and no visible tick spike.
 *
 * <h2>What happens afterwards: nothing</h2>
 * The camp is placed once and never touched again. There is no tick handler, no state kept on the colony, and no
 * re-arming: the spawners are ordinary vanilla blocks a pickaxe removes and the camp mobs are persistent, so a camp
 * that has been cleared stays cleared for the life of the world. That is deliberate -- a camp that grows back would
 * undo the work the quest just asked the player to do.
 */
public final class RaiderCampPlacer
{
    /**
     * The camp this places unless something names another. The small barbarian camp is 27 x 10 x 26 with five
     * spawners and twelve loot containers -- the large one is 29 x 20 x 23 and eight spawners, which is a lot to drop
     * on ground a player may be about to build on.
     */
    public static final Identifier DEFAULT_CAMP = Identifier.fromNamespaceAndPath(MOD_ID, "camps/small_barbarian_camp");

    /**
     * The processor list the template pool applies to these same templates. Without it every
     * {@code structurize:blocksolidsubstitution} in the camp -- 482 of the small camp's 7020 blocks in its floor layer
     * alone -- is placed as the substitution block itself instead of as grass.
     */
    private static final ResourceKey<StructureProcessorList> CAMP_PROCESSORS =
      ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.fromNamespaceAndPath(MOD_ID, "placeholder_replacement"));

    /**
     * How many candidate sites to try before giving up. Each rejected candidate costs a handful of heightmap reads,
     * which are array lookups on a chunk that is already loaded.
     */
    private static final int CANDIDATE_ATTEMPTS = 192;

    /**
     * The columns of the footprint are sampled on this stride rather than every one of the ~750. Three blocks is
     * finer than the flatness tolerance below, so nothing a stride of three misses can matter.
     */
    private static final int SAMPLE_STRIDE = 3;

    /**
     * How much height variation the footprint may have.
     * <p>
     * The worldgen version of this camp gets {@code terrain_adaptation: beard_box}, which reshapes the ground under
     * the structure; there is no such thing available at runtime, so the site has to be nearly flat and the rest is
     * done by hand -- see {@link #levelFootprint}. Two was tried first and found nothing at all in a band 120-220
     * blocks around world spawn on an ordinary noise world (seed 90210); five, on the same world and the same band,
     * found a site after 120 rejected candidates. Five layers of fill over a 27 x 26 footprint is still a small
     * amount of ground moved. Note that the failure at 2 is not by itself evidence about slope -- the surface probe
     * in {@link #naturalTop} was wrong at the time, and most of those rejections were for the wrong reason.
     */
    private static final int MAX_HEIGHT_SPREAD = 5;

    /**
     * Margin left clear around the footprint when looking for block entities, so the camp does not land with its wall
     * against a player's chest.
     */
    private static final int CLEARANCE_MARGIN = 4;

    /**
     * How far down a column is walked, past whatever is growing on it, looking for real ground.
     */
    private static final int SURFACE_PROBE_DEPTH = 32;

    /**
     * Sentinel for "this column has no natural ground under whatever is on top of it".
     */
    private static final int NO_GROUND = Integer.MIN_VALUE;

    /**
     * Why a placement did not happen. Every one of these is reported to whoever asked, rather than being logged and
     * swallowed: a quest that silently fails to build its own objective is worse than one that says it cannot.
     */
    public enum Failure
    {
        /** It worked. */
        NONE,
        /** The colony is not in a server level, or the level has no such structure template. */
        NO_TEMPLATE,
        /** Every candidate site was rejected. {@link Placement#detail} says what for. */
        NO_SITE
    }

    /**
     * Why one candidate site was refused. Tallied across the whole search and reported, because "no site" on its own
     * tells an operator nothing about whether to move the colony, load more chunks or give up.
     */
    public enum Reject
    {
        OK,
        UNLOADED,
        CLAIMED,
        TOO_CLOSE_TO_BUILDING,
        SLOPED,
        FLUID,
        BUILT_SURFACE,
        BLOCK_ENTITY
    }

    /**
     * The outcome of an attempt.
     *
     * @param pos     the centre of the placed camp, or null on failure.
     * @param failure why it did not happen, {@link Failure#NONE} if it did.
     */
    public record Placement(@Nullable BlockPos pos, @NotNull Failure failure, @NotNull String detail)
    {
        public boolean succeeded()
        {
            return failure == Failure.NONE;
        }
    }

    private RaiderCampPlacer()
    {
        throw new IllegalStateException("Tried to initialize: RaiderCampPlacer but this is a Utility class.");
    }

    /**
     * Find somewhere for a camp near this colony and put one there.
     *
     * @param colony     the colony the camp is meant to threaten.
     * @param templateId the structure template to place.
     * @param minRange   closest the camp centre may be to the colony centre, in blocks.
     * @param maxRange   furthest the camp centre may be from the colony centre, in blocks.
     * @return where it went, or why it did not.
     */
    public static Placement place(
      @NotNull final IColony colony,
      @NotNull final Identifier templateId,
      final int minRange,
      final int maxRange)
    {
        if (!(colony.getWorld() instanceof final ServerLevel level))
        {
            return new Placement(null, Failure.NO_TEMPLATE, "colony is not in a server level");
        }

        final Optional<StructureTemplate> maybeTemplate = level.getStructureTemplateManager().get(templateId);
        if (maybeTemplate.isEmpty())
        {
            Log.getLogger().warn("Raider camp template {} does not exist, no camp placed for colony {}", templateId, colony.getID());
            return new Placement(null, Failure.NO_TEMPLATE, "no such structure template: " + templateId);
        }

        final StructureTemplate template = maybeTemplate.get();
        final RandomSource random = level.getRandom();
        final Rotation rotation = Rotation.getRandom(random);
        final Vec3i size = template.getSize(rotation);
        final Collection<IBuilding> buildings = colony.getServerBuildingManager().getBuildings().values();
        final BlockPos centre = colony.getCenter();
        final EnumMap<Reject, Integer> tally = new EnumMap<>(Reject.class);

        for (int attempt = 0; attempt < CANDIDATE_ATTEMPTS; attempt++)
        {
            final double angle = random.nextDouble() * 2 * Math.PI;
            final int range = minRange + random.nextInt(Math.max(1, maxRange - minRange + 1));
            final BlockPos candidate = centre.offset(
              (int) Math.round(Math.cos(angle) * range),
              0,
              (int) Math.round(Math.sin(angle) * range));

            final BlockPos lowCorner = candidate.offset(-size.getX() / 2, 0, -size.getZ() / 2);
            final Site site = evaluateSite(level, colony, buildings, lowCorner, size);
            if (site.reject() != Reject.OK)
            {
                tally.merge(site.reject(), 1, Integer::sum);
                continue;
            }

            final int groundY = site.floorY();
            clearFootprint(level, lowCorner, size, groundY);
            levelFootprint(level, lowCorner, size, groundY);

            final StructurePlaceSettings settings = new StructurePlaceSettings()
              .setRotation(rotation)
              .setMirror(Mirror.NONE)
              .setIgnoreEntities(false)
              .setRandom(random);
            final Optional<Holder.Reference<StructureProcessorList>> processors =
              level.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).get(CAMP_PROCESSORS);
            if (processors.isPresent())
            {
                processors.get().value().list().forEach(settings::addProcessor);
            }
            else
            {
                Log.getLogger().warn("Processor list {} is missing; the raider camp will contain substitution blocks", CAMP_PROCESSORS.identifier());
            }

            // Same shape as vanilla's own template placements (FossilFeature, /place template): the transform gives
            // back the corner a rotated template has to start from in order to occupy the footprint that was checked.
            final BlockPos target = template.getZeroPositionWithTransform(lowCorner.atY(groundY), Mirror.NONE, rotation);
            template.placeInWorld(level, target, target, settings, random, Block.UPDATE_CLIENTS);

            final BlockPos placedCentre = new BlockPos(candidate.getX(), groundY, candidate.getZ());
            Log.getLogger().info("Placed raider camp {} for colony {} at {} ({} blocks from centre, {} candidates rejected)",
              templateId, colony.getID(), placedCentre.toShortString(), (int) Math.sqrt(centre.distSqr(placedCentre)), attempt);
            return new Placement(placedCentre, Failure.NONE, "after " + attempt + " rejected candidates");
        }

        return new Placement(null, Failure.NO_SITE, describe(tally));
    }

    /**
     * Turn the rejection tally into something an operator can act on.
     */
    private static String describe(@NotNull final EnumMap<Reject, Integer> tally)
    {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<Reject, Integer> entry : tally.entrySet())
        {
            if (!sb.isEmpty())
            {
                sb.append(", ");
            }
            sb.append(entry.getKey().name().toLowerCase(Locale.ROOT)).append(' ').append(entry.getValue());
        }
        return sb.isEmpty() ? "no candidates evaluated" : sb.toString();
    }

    /**
     * A candidate site's verdict, and the height its floor would sit at if the verdict is {@link Reject#OK}.
     */
    private record Site(@NotNull Reject reject, int floorY)
    {
        private static final Site UNLOADED = new Site(Reject.UNLOADED, 0);
    }

    /**
     * Decide whether a camp may go here, and at what height.
     * <p>
     * Everything this refuses, in the order it refuses it:
     * <ol>
     *   <li><b>Unloaded chunks.</b> Reading a heightmap out of an unloaded chunk generates it, on the server thread,
     *       and a quest accepted by one player is not allowed to generate nine chunks of terrain. A candidate whose
     *       footprint is not already loaded is skipped rather than loaded.</li>
     *   <li><b>Any claimed chunk.</b> Including this colony's own. A camp inside the claim is a camp inside somebody's
     *       town, and the claim is the only machine-readable statement of where that town is.</li>
     *   <li><b>Anything near a colony building.</b> Through {@link RaidManager#isValidSpawnPoint}, the same test the
     *       raid uses to decide it is not spawning inside the walls: 35 blocks plus a per-level bonus that is largest
     *       for the town hall, guard towers and housing.</li>
     *   <li><b>Ground that is not flat.</b> More than {@link #MAX_HEIGHT_SPREAD} blocks of spread across the
     *       footprint and the site is rejected, because there is no terrain adaptation here to hide it.</li>
     *   <li><b>Water and lava.</b> A column whose ground has fluid on it or in it fails.</li>
     *   <li><b>Ground that is not natural.</b> The surface block of every sampled column has to be dirt, sand,
     *       stone, gravel, snow, moss, mud, clay or terracotta. Planks, bricks, concrete, wool and every other thing a
     *       player builds a floor out of fail here.</li>
     *   <li><b>Any block entity in the footprint plus a four-block margin.</b> Chests, barrels, furnaces, signs, beds,
     *       banners, hut blocks, spawners -- everything that marks a place as built rather than grown.</li>
     * </ol>
     * What it cannot see is a player's dirt hut, or a bridge of natural stone: the surface-material test is a
     * heuristic and is stated as one. The block-entity test is what catches most real builds, because a build a player
     * would mind losing has something in it.
     */
    @NotNull
    private static Site evaluateSite(
      @NotNull final ServerLevel level,
      @NotNull final IColony colony,
      @NotNull final Collection<IBuilding> buildings,
      @NotNull final BlockPos lowCorner,
      @NotNull final Vec3i size)
    {
        final int minChunkX = (lowCorner.getX() - CLEARANCE_MARGIN) >> 4;
        final int maxChunkX = (lowCorner.getX() + size.getX() + CLEARANCE_MARGIN) >> 4;
        final int minChunkZ = (lowCorner.getZ() - CLEARANCE_MARGIN) >> 4;
        final int maxChunkZ = (lowCorner.getZ() + size.getZ() + CLEARANCE_MARGIN) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++)
        {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
            {
                if (!level.hasChunk(cx, cz))
                {
                    return Site.UNLOADED;
                }
                if (ColonyUtils.getOwningColony(colony.getDimension(), new ChunkPos(cx, cz)) != NO_COLONY_ID)
                {
                    return new Site(Reject.CLAIMED, 0);
                }
            }
        }

        final BlockPos centre = new BlockPos(lowCorner.getX() + size.getX() / 2, lowCorner.getY(), lowCorner.getZ() + size.getZ() / 2);
        if (!RaidManager.isValidSpawnPoint(buildings, centre))
        {
            return new Site(Reject.TOO_CLOSE_TO_BUILDING, 0);
        }

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = 0; dx <= size.getX(); dx += SAMPLE_STRIDE)
        {
            for (int dz = 0; dz <= size.getZ(); dz += SAMPLE_STRIDE)
            {
                final int x = lowCorner.getX() + Math.min(dx, size.getX() - 1);
                final int z = lowCorner.getZ() + Math.min(dz, size.getZ() - 1);

                final BlockPos top = new BlockPos(x, naturalTop(level, x, z), z);
                if (top.getY() == NO_GROUND)
                {
                    return new Site(Reject.BUILT_SURFACE, 0);
                }
                if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty())
                {
                    return new Site(Reject.FLUID, 0);
                }

                lowest = Math.min(lowest, top.getY());
                highest = Math.max(highest, top.getY());
                if (highest - lowest > MAX_HEIGHT_SPREAD)
                {
                    return new Site(Reject.SLOPED, 0);
                }
            }
        }

        if (lowest == Integer.MAX_VALUE)
        {
            return new Site(Reject.SLOPED, 0);
        }

        if (hasBlockEntities(level, lowCorner, size, lowest, highest, minChunkX, maxChunkX, minChunkZ, maxChunkZ))
        {
            return new Site(Reject.BLOCK_ENTITY, 0);
        }

        // The camp's own y=0 layer is its floor, so it sits on the highest ground in the footprint and the dip
        // underneath is filled rather than left as a hole. Placing on the highest rather than the lowest is what makes
        // the clearing above it a no-op in the common case: nothing of the terrain reaches into the camp.
        return new Site(Reject.OK, highest + 1);
    }

    /**
     * Whether anything in the footprint, plus the clearance margin and a generous vertical band, is a block entity.
     */
    private static boolean hasBlockEntities(
      @NotNull final ServerLevel level,
      @NotNull final BlockPos lowCorner,
      @NotNull final Vec3i size,
      final int lowest,
      final int highest,
      final int minChunkX,
      final int maxChunkX,
      final int minChunkZ,
      final int maxChunkZ)
    {
        final int minX = lowCorner.getX() - CLEARANCE_MARGIN;
        final int maxX = lowCorner.getX() + size.getX() + CLEARANCE_MARGIN;
        final int minZ = lowCorner.getZ() - CLEARANCE_MARGIN;
        final int maxZ = lowCorner.getZ() + size.getZ() + CLEARANCE_MARGIN;
        final int minY = lowest - CLEARANCE_MARGIN;
        final int maxY = highest + size.getY() + CLEARANCE_MARGIN;

        for (int cx = minChunkX; cx <= maxChunkX; cx++)
        {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++)
            {
                final LevelChunk chunk = level.getChunk(cx, cz);
                for (final BlockPos pos : chunk.getBlockEntitiesPos())
                {
                    if (pos.getX() >= minX && pos.getX() <= maxX
                          && pos.getZ() >= minZ && pos.getZ() <= maxZ
                          && pos.getY() >= minY && pos.getY() <= maxY)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The y of the topmost block of real ground in this column, or {@link #NO_GROUND}.
     * <p>
     * Not the heightmap value. {@link Heightmap.Types#OCEAN_FLOOR} answers with whatever blocks motion, and in a
     * forest that is the top of a tree -- measured on an ordinary noise world, 166 of 192 candidate sites were
     * rejected as "built surface" for exactly this reason, because the block the heightmap pointed at was leaves.
     * So the column is walked down from the heightmap through the things that grow on ground -- foliage, logs, snow,
     * crops, cactus, bamboo, mushroom stems -- and the first thing that is neither overgrowth nor ground stops the
     * walk and refuses the column, because that is what a floor somebody laid looks like from above.
     */
    private static int naturalTop(@NotNull final ServerLevel level, final int x, final int z)
    {
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int start = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        final int floor = Math.max(level.getMinY(), start - SURFACE_PROBE_DEPTH);
        for (int y = start; y >= floor; y--)
        {
            pos.set(x, y, z);
            final BlockState state = level.getBlockState(pos);
            if (isNaturalGround(state))
            {
                return y;
            }
            if (!isOvergrowth(state))
            {
                return NO_GROUND;
            }
        }
        return NO_GROUND;
    }

    /**
     * Whether this is something that grew on the ground rather than something that is the ground or was built on it.
     */
    private static boolean isOvergrowth(@NotNull final BlockState state)
    {
        return state.isAir()
                 || state.is(BlockTags.LEAVES)
                 || state.is(BlockTags.LOGS)
                 || state.is(BlockTags.REPLACEABLE)
                 || state.is(BlockTags.FLOWERS)
                 || state.is(BlockTags.SAPLINGS)
                 || state.is(BlockTags.CROPS)
                 || state.is(BlockTags.WART_BLOCKS)
                 || state.is(Blocks.SNOW)
                 || state.is(Blocks.CACTUS)
                 || state.is(Blocks.SUGAR_CANE)
                 || state.is(Blocks.BAMBOO)
                 || state.is(Blocks.MOSS_CARPET)
                 || state.is(Blocks.PUMPKIN)
                 || state.is(Blocks.MELON)
                 || state.is(Blocks.MUSHROOM_STEM)
                 || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                 || state.is(Blocks.RED_MUSHROOM_BLOCK);
    }

    /**
     * Whether this looks like ground rather than like a floor somebody laid.
     */
    private static boolean isNaturalGround(@NotNull final BlockState state)
    {
        return state.is(BlockTags.DIRT)
                 || state.is(BlockTags.SAND)
                 || state.is(BlockTags.BASE_STONE_OVERWORLD)
                 || state.is(BlockTags.TERRACOTTA)
                 || state.is(Blocks.GRAVEL)
                 || state.is(Blocks.CLAY)
                 || state.is(Blocks.SNOW_BLOCK)
                 || state.is(Blocks.POWDER_SNOW)
                 || state.is(Blocks.MOSS_BLOCK)
                 || state.is(Blocks.MUD);
    }

    /**
     * Take out whatever is standing in the volume the camp is about to occupy -- trees, tall grass, the last block of
     * a hillock that reaches above the chosen floor. The site check has already established that this ground is
     * natural and carries no block entity, so there is nothing here a player put down.
     */
    private static void clearFootprint(
      @NotNull final ServerLevel level,
      @NotNull final BlockPos lowCorner,
      @NotNull final Vec3i size,
      final int floorY)
    {
        final BlockState air = Blocks.AIR.defaultBlockState();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < size.getX(); dx++)
        {
            for (int dz = 0; dz < size.getZ(); dz++)
            {
                final int x = lowCorner.getX() + dx;
                final int z = lowCorner.getZ() + dz;
                // Up to the camp's own height, and past it if a tree stands taller than that -- a trunk sheared off
                // at the camp roof line would be left hanging otherwise.
                final int top = Math.max(floorY + size.getY(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                for (int y = floorY; y <= top; y++)
                {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir())
                    {
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /**
     * Fill the dip under the footprint, so a camp on ground that slopes does not stand on stilts. Bounded by
     * {@link #MAX_HEIGHT_SPREAD}: at most five layers over the footprint.
     */
    private static void levelFootprint(
      @NotNull final ServerLevel level,
      @NotNull final BlockPos lowCorner,
      @NotNull final Vec3i size,
      final int floorY)
    {
        final BlockState filler = Blocks.DIRT.defaultBlockState();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < size.getX(); dx++)
        {
            for (int dz = 0; dz < size.getZ(); dz++)
            {
                final int x = lowCorner.getX() + dx;
                final int z = lowCorner.getZ() + dz;
                final int ground = naturalTop(level, x, z);
                for (int y = (ground == NO_GROUND ? floorY : ground + 1); y < floorY; y++)
                {
                    pos.set(x, y, z);
                    level.setBlock(pos, filler, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Whether the colony's world can be asked for a camp at all. Cheap enough to call from a quest trigger.
     *
     * @param colony the colony.
     * @return true if the colony sits in a loaded server level.
     */
    public static boolean isUsable(@NotNull final IColony colony)
    {
        return colony.getWorld() instanceof ServerLevel && WorldUtil.isBlockLoaded(colony.getWorld(), colony.getCenter());
    }
}
