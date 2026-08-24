package com.ldtteam.domumornamentum.block.decorative;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.ldtteam.domumornamentum.block.AbstractPostBlock;
import com.ldtteam.domumornamentum.block.ICachedItemGroupBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.components.SimpleRetexturableComponent;
import com.ldtteam.domumornamentum.block.types.PostType;
import com.ldtteam.domumornamentum.entity.block.MateriallyTexturedBlockEntity;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeBuilder;
import com.ldtteam.domumornamentum.tag.ModTags;
import com.ldtteam.domumornamentum.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.Blocks.OAK_PLANKS;
import net.minecraft.world.level.block.Blocks;

// Port note (26.3): block codecs are gone. Block#simpleCodec, BlockBehaviour#propertiesCodec and the
// abstract Block#codec() were all removed in 26.3 -- vanilla blocks now declare no codec at all (see any
// block in /opt/mc-src-26.3/net/minecraft/world/level/block/). The static CODEC field and the codec()
// override were therefore deleted rather than ported.
public class PostBlock extends AbstractPostBlock<PostBlock> implements IMateriallyTexturedBlock, ICachedItemGroupBlock, EntityBlock
{
    public static final List<IMateriallyTexturedBlockComponent> COMPONENTS = ImmutableList.<IMateriallyTexturedBlockComponent>builder()
                                                                               .add(new SimpleRetexturableComponent(Identifier.withDefaultNamespace("block/oak_planks"), ModTags.POST_MATERIALS, OAK_PLANKS))
                                                                               .build();

    private final List<ItemStack> fillItemGroupCache = Lists.newArrayList();

    public PostBlock()
    {
        this(Properties.of().mapColor(MapColor.WOOD).strength(3.0F));
    }

    public PostBlock(final Properties props)
    {
        super(props);
        this.registerDefaultState(this.defaultBlockState().setValue(TYPE, PostType.PLAIN));
    }

    @Override
    public @NotNull List<IMateriallyTexturedBlockComponent> getComponents()
    {
        return COMPONENTS;
    }

    @Override
    public void fillItemCategory(final @NotNull NonNullList<ItemStack> items)
    {
        fillDOItemCategory(this, items, fillItemGroupCache, out -> {
            for (final PostType postType : PostType.values())
            {
                final ItemStack result = new ItemStack(this);
                BlockUtils.putPropertyIntoBlockStateTag(result, TYPE, postType);

                out.add(result);
            }
        });
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final @NotNull BlockPos blockPos, final @NotNull BlockState blockState)
    {
        return new MateriallyTexturedBlockEntity(blockPos, blockState);
    }

    @Override
    public ItemStack getCloneItemStack(final LevelReader world, final BlockPos pos, final BlockState state, final boolean includeData)
    {
        return BlockUtils.getMaterializedItemStack(world.getBlockEntity(pos), world.registryAccess(), TYPE);
    }

    @Override
    public void resetCache()
    {
        // guarded like the population in IMateriallyTexturedBlock#fillDOItemCategory: the cache
        // is read from both the render thread and the server thread.
        synchronized (fillItemGroupCache)
        {
            fillItemGroupCache.clear();
        }
    }

    @Override
    public void buildRecipes(final RecipeOutput recipeOutput)
    {
        for (final PostType value : PostType.values())
        {
            new ArchitectsCutterRecipeBuilder(this, RecipeCategory.DECORATIONS).resultProperty(TYPE, value)
                .count(COMPONENTS.size())
                .saveSuffix(recipeOutput, value.getSerializedName());
        }
    }

    // TODO(port-26.2): DISABLED — per-material explosion resistance. NeoForge's
    //   Block#getExplosionResistance(BlockState, BlockGetter, BlockPos, Explosion) does not exist in
    //   vanilla 26.2 (only Block#getExplosionResistance(), /opt/mc-src/.../block/Block.java:445) and
    //   Fabric has no hook for it, so the resistance of the skin block can no longer be applied.
    // @Override
    // public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
    //     return getDOExplosionResistance(super::getExplosionResistance, state, level, pos, explosion);
    // }

    @Override
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return getDODestroyProgress(super::getDestroyProgress, state, player, level, pos);
    }

    // TODO(port-26.2): DISABLED — per-material sound type. NeoForge's
    //   Block#getSoundType(BlockState, LevelReader, BlockPos, Entity) does not exist in vanilla 26.2
    //   (only BlockBehaviour#getSoundType(BlockState), /opt/mc-src/.../BlockBehaviour.java:404, which
    //   has no position to read the block entity from) and Fabric has no hook for it.
    // @Override
    // public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
    //     return getDOSoundType(super::getSoundType, state, level, pos, entity);
    // }

    @Override
    public IMateriallyTexturedBlockComponent getMainComponent() {
        return COMPONENTS.get(0);
    }
}
