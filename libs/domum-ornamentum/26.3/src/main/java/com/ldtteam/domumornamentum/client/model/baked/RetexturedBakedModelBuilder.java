package com.ldtteam.domumornamentum.client.model.baked;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.client.model.utils.ModelSpriteQuadTransformerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a {@link MaterialTextureData} into "sprite name to replacement sprite" lookups.
 *
 * <h2>What replaced what</h2>
 * The NeoForge version of this class built a whole new {@code BakedModel} per (material set, render type)
 * pair with {@code SimpleBakedModel.Builder}, re-adding every quad. None of that survives in 26.2:
 * <ul>
 *   <li>{@code BakedModel} does not exist. The block-side model interface is
 *       {@code net.minecraft.client.renderer.block.dispatch.BlockStateModel}, which exposes
 *       {@code collectParts(RandomSource, List&lt;BlockStateModelPart&gt;)} rather than {@code getQuads}.</li>
 *   <li>{@code SimpleBakedModel.Builder} is gone; the closest thing is
 *       {@code net.minecraft.client.resources.model.SimpleModelWrapper}, a {@code record} over a baked
 *       {@code QuadCollection}, which can only be produced at bake time.</li>
 *   <li>{@code ChunkRenderTypeSet} is gone. Every quad now carries its own
 *       {@code ChunkSectionLayer} inside {@code BakedQuad.MaterialInfo}, so there is no reason to build one
 *       model per render type any more.</li>
 * </ul>
 * So instead of rebuilding models we resolve, once per material set, the replacement sprite for every
 * component and every face, and re-emit the parent model's quads through the Fabric {@code QuadEmitter}
 * at render time (see {@link MateriallyTexturedBakedModel}).
 */
public class RetexturedBakedModelBuilder
{
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    /** 6 directions plus the "no cull face" bucket. */
    private static final int FACE_SLOTS = Direction.values().length + 1;
    private static final int NULL_FACE_SLOT = Direction.values().length;

    private static final Cache<MaterialTextureData, RetexturedBakedModelBuilder> CACHE = CacheBuilder.newBuilder()
      .expireAfterAccess(2, TimeUnit.MINUTES)
      .concurrencyLevel(4)
      .maximumSize(10000)
      .build();

    /**
     * The model set the cache entries were resolved against. Baked models are thrown away and rebuilt on
     * every resource reload, so cached sprites from a previous atlas would point at freed texture regions.
     */
    private static volatile @Nullable BlockStateModelSet cachedAgainst = null;

    /**
     * Resolves (and caches) the replacement table for one material set.
     * <p>
     * The hit is served before the loader is written, because {@code Cache#get(K, Callable)} takes a
     * <em>capturing</em> lambda here ({@code textureData} plus {@code modelSet}) and a capturing lambda is a
     * fresh object on every evaluation - {@code LambdaMetafactory} only hands out a singleton for the
     * non-capturing case. This method is called up to three times per block per chunk-section rebuild
     * ({@link MateriallyTexturedBakedModel#emitQuads}, {@code #materialFlags}, {@code #particleMaterial}), so
     * that allocation was being paid on the hit path, which is where essentially every call lands. Miss
     * behaviour is unchanged: still one atomic load through the cache, so two threads racing on the same
     * material set still build one instance.
     */
    public static RetexturedBakedModelBuilder resolve(final MaterialTextureData textureData)
    {
        final BlockStateModelSet modelSet = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        if (cachedAgainst != modelSet)
        {
            CACHE.invalidateAll();
            cachedAgainst = modelSet;
        }

        final RetexturedBakedModelBuilder hit = CACHE.getIfPresent(textureData);
        if (hit != null)
        {
            return hit;
        }

        try
        {
            return CACHE.get(textureData, () -> new RetexturedBakedModelBuilder(textureData, modelSet));
        }
        catch (final Exception exception)
        {
            return new RetexturedBakedModelBuilder(MaterialTextureData.EMPTY, modelSet);
        }
    }

    private final Map<Identifier, ReplacementModelData> retexturingMaps = new HashMap<>();
    private final boolean containsTranslucent;
    private final boolean containsAnimated;

    private RetexturedBakedModelBuilder(final MaterialTextureData textureData, final BlockStateModelSet modelSet)
    {
        boolean translucent = false;
        boolean animated = false;

        for (final Map.Entry<Identifier, Block> entry : textureData.getTexturedComponents().entrySet())
        {
            final Block target = entry.getValue();
            if (target == null)
            {
                continue;
            }

            final ReplacementModelData replacement = resolveReplacement(target.defaultBlockState(), modelSet);
            if (replacement == null)
            {
                continue;
            }

            this.retexturingMaps.put(entry.getKey(), replacement);
            translucent |= replacement.translucent();
            animated |= replacement.animated();
        }

        this.containsTranslucent = translucent;
        this.containsAnimated = animated;
    }

    public boolean isEmpty()
    {
        return this.retexturingMaps.isEmpty();
    }

    /**
     * Extra {@code BakedQuad.MaterialFlags} contributed by the replacement materials. The renderer uses these
     * to decide up front whether a block can produce translucent or animated geometry; forgetting them makes
     * glass-textured blocks silently disappear from the translucent pass.
     */
    public int extraMaterialFlags()
    {
        int flags = 0;
        if (this.containsTranslucent)
        {
            flags |= BakedQuad.FLAG_TRANSLUCENT;
        }
        if (this.containsAnimated)
        {
            flags |= BakedQuad.FLAG_ANIMATED;
        }
        return flags;
    }

    /**
     * @param spriteName the sprite the source quad is currently using
     * @return the entry for that placeholder, or {@code null} when this sprite is not a material placeholder
     *         at all and the quad has to be emitted untouched
     */
    public @Nullable ReplacementModelData lookup(final Identifier spriteName)
    {
        return this.retexturingMaps.get(spriteName);
    }

    /**
     * Walks the replacement block's baked model and remembers one sprite per face bucket.
     */
    private static @Nullable ReplacementModelData resolveReplacement(final BlockState state, final BlockStateModelSet modelSet)
    {
        final BlockStateModel model = modelSet.get(state);
        if (model == null)
        {
            return null;
        }

        final List<BlockStateModelPart> parts = new ArrayList<>();
        synchronized (RANDOM)
        {
            RANDOM.setSeed(42L);
            model.collectParts(RANDOM, parts);
        }

        final Material.Baked[] perFace = new Material.Baked[FACE_SLOTS];
        Material.Baked fallback = null;
        boolean translucent = false;
        boolean animated = false;

        for (final BlockStateModelPart part : parts)
        {
            for (int slot = 0; slot < FACE_SLOTS; slot++)
            {
                final Direction face = slot == NULL_FACE_SLOT ? null : Direction.from3DDataValue(slot);
                final List<BakedQuad> quads = part.getQuads(face);
                if (quads.isEmpty())
                {
                    continue;
                }

                final BakedQuad.MaterialInfo info = quads.getFirst().materialInfo();
                final Material.Baked material = new Material.Baked(info.sprite(), info.layer().translucent());

                translucent |= info.layer().translucent();
                animated |= info.sprite().contents().isAnimated();

                if (perFace[slot] == null)
                {
                    perFace[slot] = material;
                }
                if (fallback == null)
                {
                    fallback = material;
                }
            }
        }

        // A replacement block with no geometry at all - air above all - leaves fallback null, and that is an
        // erasure: the quads carrying this placeholder are dropped by the caller rather than retextured.
        //
        // This used to fall back to the replacement's particle sprite instead, on the reasoning that the
        // source geometry should at least keep a valid texture. It does not: block/air's model declares its
        // particle as minecraft:missingno, so every erased quad painted itself with the missing texture.
        // MineColonies racks, whose empty display slots are mapped to air to make them disappear, turned
        // purple everywhere because of it. The NeoForge version erased these quads too, through
        // RetexturedBakedModelBuilder#withOut / #needsErasure, and that path was lost in the 26.2 rewrite.

        // Pre-wrap every resolved sprite into the record the per-quad path hands out, so that path stays
        // allocation free. Done here because this whole method already runs once per material set and is
        // behind the cache in resolve(...).
        final ModelSpriteQuadTransformerData[] perFaceData = new ModelSpriteQuadTransformerData[FACE_SLOTS];
        for (int slot = 0; slot < FACE_SLOTS; slot++)
        {
            if (perFace[slot] != null)
            {
                perFaceData[slot] = new ModelSpriteQuadTransformerData(perFace[slot], state);
            }
        }

        return new ReplacementModelData(perFaceData,
          fallback == null ? null : new ModelSpriteQuadTransformerData(fallback, state),
          state,
          translucent,
          animated);
    }

    /**
     * One resolved replacement block.
     *
     * @param perFace   replacement per cull-face bucket, indexed by {@link Direction#get3DDataValue()} with the
     *                  last slot holding the "no cull face" bucket; entries may be {@code null}
     * @param fallback  replacement to use when the requested bucket is empty; {@code null} marks an erasure,
     *                  in which case every {@code perFace} entry is {@code null} as well
     */
    public record ReplacementModelData(
      ModelSpriteQuadTransformerData[] perFace,
      @Nullable ModelSpriteQuadTransformerData fallback,
      BlockState state,
      boolean translucent,
      boolean animated)
    {
        /**
         * @return whether the quads using this placeholder are to be dropped instead of retextured, because the
         *         replacement block has no geometry to take a sprite from.
         */
        public boolean erases()
        {
            return this.fallback == null;
        }

        /**
         * Resolves the replacement for one quad.
         * <p>
         * The returned record is not minted here, which would put one allocation on the per-quad path:
         * {@link MateriallyTexturedBakedModel#emitPart} reaches this once for every {@code BakedQuad} of every
         * part of every DO block in a rebuilt chunk section. Both of its fields are constant for a given
         * (sprite, face) pair - the material comes out of the fixed per-face table, the state is a field of
         * this very entry - so the whole record is built once, in {@code resolveReplacement}, and only handed
         * out here. It is immutable and its callers ({@code ModelSpriteQuadTransformer#retexture},
         * {@link MateriallyTexturedBakedModel#particleMaterial}) only read it, so sharing one instance across
         * quads is observationally identical to minting a fresh one.
         *
         * @param cullFace the cull face bucket the quad was collected from, may be {@code null}
         * @param quadFace the quad's own facing, used when the replacement model culls differently than the
         *                 source model does
         * @return the replacement, or {@code null} for an erasure.
         */
        public @Nullable ModelSpriteQuadTransformerData dataFor(
          final @Nullable Direction cullFace, final @Nullable Direction quadFace)
        {
            final ModelSpriteQuadTransformerData byCullFace = bucket(cullFace);
            if (byCullFace != null)
            {
                return byCullFace;
            }

            final ModelSpriteQuadTransformerData byQuadFace = bucket(quadFace);
            return byQuadFace != null ? byQuadFace : this.fallback;
        }

        private @Nullable ModelSpriteQuadTransformerData bucket(final @Nullable Direction face)
        {
            return this.perFace[face == null ? NULL_FACE_SLOT : face.get3DDataValue()];
        }
    }
}
