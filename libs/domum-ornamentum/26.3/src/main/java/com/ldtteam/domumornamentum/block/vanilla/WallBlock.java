package com.ldtteam.domumornamentum.block.vanilla;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.ldtteam.domumornamentum.block.AbstractBlockWall;
import com.ldtteam.domumornamentum.block.ICachedItemGroupBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.components.SimpleRetexturableComponent;
import com.ldtteam.domumornamentum.entity.block.MateriallyTexturedBlockEntity;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeBuilder;
import com.ldtteam.domumornamentum.tag.ModTags;
import com.ldtteam.domumornamentum.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.Blocks.OAK_PLANKS;

public class WallBlock extends AbstractBlockWall<WallBlock> implements IMateriallyTexturedBlock, EntityBlock, ICachedItemGroupBlock
{
    public static final List<IMateriallyTexturedBlockComponent> COMPONENTS = ImmutableList.<IMateriallyTexturedBlockComponent>builder()
                                                                               .add(new SimpleRetexturableComponent(Identifier.withDefaultNamespace("block/oak_planks"), ModTags.WALL_MATERIALS, OAK_PLANKS))
                                                                               .build();

    // 26.2: the NORTH_WALL/EAST_WALL/... aliases on WallBlock are gone; the properties are named
    // NORTH/EAST/SOUTH/WEST there and still live in BlockStateProperties
    // (/opt/mc-src/net/minecraft/world/level/block/WallBlock.java:36-39).
    public static ImmutableMap<Direction, EnumProperty<WallSide>> PROPERTIES = ImmutableMap.of(
            Direction.NORTH, BlockStateProperties.NORTH_WALL,
            Direction.EAST, BlockStateProperties.EAST_WALL,
            Direction.SOUTH, BlockStateProperties.SOUTH_WALL,
            Direction.WEST, BlockStateProperties.WEST_WALL
    );

    private final List<ItemStack> fillItemGroupCache = Lists.newArrayList();

    public WallBlock()
    {
        super(Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F));
    }
    @Override
    public @NotNull List<IMateriallyTexturedBlockComponent> getComponents()
    {
        return COMPONENTS;
    }
    @Nullable
    @Override
    public BlockEntity newBlockEntity(final @NotNull BlockPos blockPos, final @NotNull BlockState blockState)
    {
        return new MateriallyTexturedBlockEntity(blockPos, blockState);
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
    public ItemStack getCloneItemStack(final LevelReader world, final BlockPos pos, final BlockState state, final boolean includeData)
    {
        return BlockUtils.getMaterializedItemStack(world.getBlockEntity(pos), world.registryAccess());
    }

    @Override
    public void buildRecipes(final RecipeOutput recipeOutput)
    {
        new ArchitectsCutterRecipeBuilder(this, RecipeCategory.DECORATIONS).save(recipeOutput);
    }

    @Override
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return getDODestroyProgress(super::getDestroyProgress, state, player, level, pos);
    }

    @Override
    public IMateriallyTexturedBlockComponent getMainComponent() {
        return COMPONENTS.get(0);
    }

    @Override
    public void fillItemCategory(final @NotNull NonNullList<ItemStack> items) {
        fillDOItemCategory(this, items, fillItemGroupCache);
    }
}
