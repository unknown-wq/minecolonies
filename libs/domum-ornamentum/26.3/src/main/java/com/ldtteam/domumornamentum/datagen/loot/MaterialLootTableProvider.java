package com.ldtteam.domumornamentum.datagen.loot;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.FancyDoorBlock;
import com.ldtteam.domumornamentum.block.decorative.FancyTrapdoorBlock;
import com.ldtteam.domumornamentum.block.decorative.PanelBlock;
import com.ldtteam.domumornamentum.block.decorative.PostBlock;
import com.ldtteam.domumornamentum.block.vanilla.DoorBlock;
import com.ldtteam.domumornamentum.block.vanilla.TrapdoorBlock;
import com.ldtteam.domumornamentum.component.ModDataComponents;
import com.ldtteam.domumornamentum.shingles.ShingleHeightType;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.CopyBlockState;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.MatchBlock;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * LootTables for {@link IMateriallyTexturedBlock}s.
 *
 * <p>Port note (26.2 / Fabric): NeoForge's {@code LootTableProvider} + {@code SubProviderEntry} pair is replaced by
 * {@link FabricBlockLootSubProvider}, which is itself a {@code DataProvider}. Two further 26.2 changes matter:</p>
 * <ul>
 *   <li>{@code BlockLootSubProvider#getKnownBlocks()} no longer exists — vanilla now walks the whole block registry
 *       and demands a table for every block ({@code /opt/mc-src/net/minecraft/data/loot/BlockLootSubProvider.java:839}).
 *       Fabric's subclass replaces that walk with a mod-namespace filter that only reports missing tables when strict
 *       validation is switched on, so the override was dropped.</li>
 *   <li>The constructor is {@code (FabricPackOutput, CompletableFuture<HolderLookup.Provider>)} instead of
 *       {@code (Set<Item>, FeatureFlagSet, HolderLookup.Provider)}.</li>
 * </ul>
 *
 * <p>Port note (26.3): the {@code loot.entries} package was rebuilt and two class names moved.</p>
 * <ul>
 *   <li>{@code LootPoolSingletonContainer} split into {@code SingleEntryContainerBase} (the entry) and
 *       {@code UniformContainerBase} (which owns the {@code Builder}). {@code LootItem.lootTableItem} now returns
 *       {@code UniformContainerBase.Builder<?>} ({@code
 *       /opt/mc-src-26.3/net/minecraft/world/level/storage/loot/entries/LootItem.java:41}), which is why the
 *       {@code UnaryOperator} below is typed on that and not on {@code SingleEntryContainerBase}.</li>
 *   <li>{@code LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(props)} is gone;
 *       the replacement is {@code MatchBlock.blockMatches(HolderGetter<Block>, Block, StatePropertiesPredicate.Builder)}
 *       ({@code .../loot/predicates/MatchBlock.java:41}). The lookup is the inherited {@code this.blocks}, exactly as
 *       vanilla's {@code VanillaBlockLoot} uses it. Serialized form changes from {@code minecraft:block_state_property}
 *       to {@code minecraft:match_block}; the regenerated tables under {@code src/main/generated} reflect that.</li>
 * </ul>
 */
public class MaterialLootTableProvider extends FabricBlockLootSubProvider
{
    public MaterialLootTableProvider(final FabricPackOutput output, final CompletableFuture<HolderLookup.Provider> registries)
    {
        super(output, registries);
    }

    @Override
    public void generate()
    {
        dropDoorMateriallyWithProp(ModBlocks.getInstance().getDoor(), DoorBlock.TYPE);
        dropDoorMateriallyWithProp(ModBlocks.getInstance().getFancyDoor(), FancyDoorBlock.TYPE);
        dropSlabMaterially(ModBlocks.getInstance().getSlab());

        dropSelfMaterially(ModBlocks.getInstance().getFence());
        dropSelfMaterially(ModBlocks.getInstance().getFenceGate());
        dropSelfMaterially(ModBlocks.getInstance().getPaperWall());
        dropSelfMaterially(ModBlocks.getInstance().getShingle(ShingleHeightType.DEFAULT));
        dropSelfMaterially(ModBlocks.getInstance().getShingle(ShingleHeightType.FLAT_LOWER));
        dropSelfMaterially(ModBlocks.getInstance().getShingle(ShingleHeightType.FLAT));
        dropSelfMaterially(ModBlocks.getInstance().getShingleSlab());
        dropSelfMaterially(ModBlocks.getInstance().getStair());
        dropSelfMaterially(ModBlocks.getInstance().getWall());
        dropSelfMaterially(ModBlocks.getInstance().getTiledPaperWall());

        dropSelfMateriallyWithProp(ModBlocks.getInstance().getFancyTrapdoor(), FancyTrapdoorBlock.TYPE);
        dropSelfMateriallyWithProp(ModBlocks.getInstance().getPanel(), PanelBlock.TYPE);
        dropSelfMateriallyWithProp(ModBlocks.getInstance().getPost(), PostBlock.TYPE);
        dropSelfMateriallyWithProp(ModBlocks.getInstance().getTrapdoor(), TrapdoorBlock.TYPE);

        ModBlocks.getInstance().getAllBrickBlocks().forEach(this::dropSelfMaterially);
        ModBlocks.getInstance().getAllBrickStairBlocks().forEach(this::dropSelfMaterially);
        ModBlocks.getInstance().getFramedLights().forEach(this::dropSelfMaterially);
        ModBlocks.getInstance().getPillars().forEach(this::dropSelfMaterially);
        ModBlocks.getInstance().getTimberFrames().forEach(this::dropSelfMaterially);
        dropSelfMaterially(ModBlocks.getInstance().getDynamicTimberFrame());
    }

    /**
     * Helper method to create default textureData lootTable
     */
    protected void dropSelfMaterially(final Block block, final UnaryOperator<UniformContainerBase.Builder<?>> itemPoolBuilder)
    {
        add(block,
            LootTable.lootTable()
                .withPool(LootPool.lootPool()
                    .setRolls(ContextIntProviders.exactly(1))
                    .add(itemPoolBuilder.apply(LootItem.lootTableItem(block)
                        .apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
                            .include(ModDataComponents.TEXTURE_DATA.get()))))));
    }

    /**
     * Drops block: textureData
     */
    protected void dropSelfMaterially(final Block block)
    {
        dropSelfMaterially(block, UnaryOperator.identity());
    }

    /**
     * Drops block: textureData + given blockState property
     */
    protected void dropSelfMateriallyWithProp(final Block block, final Property<?> property)
    {
        dropSelfMaterially(block, item -> item.apply(CopyBlockState.copyState(block).copy(property)));
    }

    /**
     * Drops door block: textureData + given blockState property
     */
    protected void dropDoorMateriallyWithProp(final net.minecraft.world.level.block.DoorBlock block, final Property<?> property)
    {
        dropSelfMaterially(block,
            item -> item.apply(CopyBlockState.copyState(block).copy(property))
                .when(MatchBlock.blockMatches(this.blocks, block,
                    StatePropertiesPredicate.Builder.properties().hasProperty(DoorBlock.HALF, DoubleBlockHalf.LOWER))));
    }

    /**
     * Drops slab block: textureData
     */
    protected void dropSlabMaterially(final SlabBlock block)
    {
        dropSelfMaterially(block,
            item -> item.apply(SetItemCountFunction.setCount(ContextIntProviders.exactly(2))
                .when(MatchBlock.blockMatches(this.blocks, block,
                    StatePropertiesPredicate.Builder.properties().hasProperty(SlabBlock.TYPE, SlabType.DOUBLE)))));
    }

    @Override
    public String getName()
    {
        return "Materially Textured Block Loot Tables";
    }
}
