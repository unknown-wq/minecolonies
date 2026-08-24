package com.ldtteam.domumornamentum.block;

import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.entity.block.IMateriallyTexturedBlockEntity;
import com.ldtteam.domumornamentum.util.Constants;
import com.ldtteam.domumornamentum.util.QuadFunction;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface IMateriallyTexturedBlock
{
    Logger LOGGER = LogManager.getLogger(Constants.MOD_ID);

    @NotNull
    Collection<IMateriallyTexturedBlockComponent> getComponents();

    void buildRecipes(RecipeOutput recipeOutput);

    @NotNull
    default MaterialTextureData getRandomMaterials()
    {
        final MaterialTextureData.Builder textureData = MaterialTextureData.builder();
        for (final IMateriallyTexturedBlockComponent component : getComponents())
        {
            final List<Block> candidates = new ArrayList<>(
              StreamSupport
                .stream(BuiltInRegistries.BLOCK.getTagOrEmpty(component.getValidSkins()).spliterator(), false)
                .map(Holder::value).toList());
            if (candidates.isEmpty()) continue;

            final Block texture = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            textureData.setComponent(component.getId(), texture);
        }
        return textureData.build();
    }


    /**
     * Method to tell mods like minecolonies if a tool is the right tool.
     * @param state the state to mine.
     * @param stack the stack trying to mine it.
     * @param level the level the block is in.
     * @param pos the position the block is at.
     * @return true if correct tool.
     */
    default boolean isCorrectToolForDrops(BlockState state, final ItemStack stack, BlockGetter level, BlockPos pos)
    {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMateriallyTexturedBlockEntity mtbe) {
            if (getMainComponent() == null)
            {
                return stack.isCorrectToolForDrops(state);
            }
            Block block = mtbe.getTextureData().getTexturedComponents().get(getMainComponent().getId());
            if (block != null)
            {
                return stack.isCorrectToolForDrops(block.defaultBlockState());
            }
        }
        return stack.isCorrectToolForDrops(state);
    }

    /**
     * Returns the explosion resistance of the skin block placed in the main component, falling back
     * to this block's own value.
     * <p>
     * 26.2: vanilla only has {@code Block#getExplosionResistance()} with no arguments
     * (/opt/mc-src/net/minecraft/world/level/block/Block.java:445) — NeoForge's positional overload is
     * gone and Fabric has no replacement hook, so no block calls this any more. Kept (and rewired onto
     * the no-arg vanilla method) so third parties such as MineColonies keep compiling.
     */
    default float getDOExplosionResistance(final QuadFunction<BlockState, BlockGetter, BlockPos, Explosion, Float> inputFunction, BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMateriallyTexturedBlockEntity mtbe) {
            if (getMainComponent() == null)
            {
                return inputFunction.apply(state, level, pos, explosion);
            }
            Block block = mtbe.getTextureData().getTexturedComponents().get(getMainComponent().getId());
            if (block != null)
            {
                return block.getExplosionResistance();
            }
        }
        return inputFunction.apply(state, level, pos, explosion);
    }

    default float getDODestroyProgress(final QuadFunction<BlockState, Player, BlockGetter, BlockPos, Float> inputFunction, BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMateriallyTexturedBlockEntity mtbe) {
            if (getMainComponent() == null)
            {
                return inputFunction.apply(state, player, level, pos);
            }
            Block block = mtbe.getTextureData().getTexturedComponents().get(getMainComponent().getId());
            if (block != null)
            {
                return block.defaultBlockState().getDestroyProgress(player, level, pos);
            }
        }
        return inputFunction.apply(state, player, level, pos);
    }

    /**
     * Returns the sound type of the skin block placed in the main component, falling back to this
     * block's own value.
     * <p>
     * 26.2: vanilla only has {@code BlockBehaviour#getSoundType(BlockState)}
     * (/opt/mc-src/net/minecraft/world/level/block/state/BlockBehaviour.java:404) — NeoForge's
     * positional overload is gone and Fabric has no replacement hook, so no block calls this any more.
     * Kept (and rewired onto the skin block's default state) so third parties keep compiling.
     */
    default SoundType getDOSoundType(final QuadFunction<BlockState, LevelReader, BlockPos, Entity, SoundType> inputFunction, BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMateriallyTexturedBlockEntity mtbe) {
            if (getMainComponent() == null)
            {
                return inputFunction.apply(state, level, pos, entity);
            }
            Block block = mtbe.getTextureData().getTexturedComponents().get(getMainComponent().getId());
            if (block != null)
            {
                return block.defaultBlockState().getSoundType();
            }
        }

        return inputFunction.apply(state, level, pos, entity);
    }

    default void fillDOItemCategory(final Block inputBlock, final @NotNull NonNullList<ItemStack> items, List<ItemStack> fillItemGroupCache) {
        fillDOItemCategory(inputBlock, items, fillItemGroupCache, out -> out.add(new ItemStack(inputBlock)));
    }

    /**
     * Fills {@code items} with this block's creative-tab variants, computing them once and remembering them
     * in {@code fillItemGroupCache}.
     * <p>
     * The cache is shared mutable state on a singleton {@link Block}, and the creative tab generator that
     * reads it runs on <em>both</em> the render thread ({@code CreativeModeInventoryScreen} rebuilding tab
     * contents) and the server thread (the Architect's Cutter menu, and third parties such as MineColonies
     * walking every creative tab from {@code CompatibilityManager}). On a single player world those two
     * overlap, so the population has to be atomic:
     * <ul>
     *   <li>the whole check-fill-read sequence is guarded, so two threads can never both decide the cache is
     *       empty and each append a full set of variants — that produced duplicate stacks, which vanilla
     *       rejects outright in {@code CreativeModeTab$ItemDisplayBuilder#accept}, and interleaved
     *       {@code ArrayList#add} calls, which produced
     *       {@code ArrayIndexOutOfBoundsException: Index n out of bounds for length 0};</li>
     *   <li>the variants are built into a local list and only published once complete, so a failure part way
     *       through cannot leave a permanently half-filled cache behind.</li>
     * </ul>
     *
     * @param inputBlock the block being filled, used for diagnostics only
     * @param items      the destination list
     * @param fillItemGroupCache the block's own cache field
     * @param generator  builds the variants into the list it is handed; never the cache itself
     */
    default void fillDOItemCategory(final Block inputBlock,
        final @NotNull NonNullList<ItemStack> items,
        final List<ItemStack> fillItemGroupCache,
        final Consumer<List<ItemStack>> generator)
    {
        synchronized (fillItemGroupCache)
        {
            if (fillItemGroupCache.isEmpty())
            {
                final List<ItemStack> built = new ArrayList<>();
                try
                {
                    generator.accept(built);
                    fillItemGroupCache.addAll(built);
                }
                catch (final RuntimeException exception)
                {
                    // Nothing is published, so the next caller retries instead of inheriting a partial list.
                    // This is reachable before the game is fully up (a stack cannot be built until item
                    // components have been bound by ReloadableServerResources), which is why it is not fatal
                    // — but it is never silent: an empty DO creative tab has to be traceable to a reason.
                    LOGGER.warn("Could not build the creative tab variants of {}; the tab will be missing them until this is retried.",
                        BuiltInRegistries.BLOCK.getKey(inputBlock), exception);
                }
            }

            items.addAll(fillItemGroupCache);
        }
    }

    /**
     * Get the main component of the block.
     * @return the main component.
     */
    default IMateriallyTexturedBlockComponent getMainComponent()
    {
        return null;
    }

    /**
     * Method to tell if the block tinting is world specific.
     *
     * @return true if world specific tinting.
     */
    default boolean usesWorldSpecificTinting() {
        return true;
    }
}
