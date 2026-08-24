package com.ldtteam.domumornamentum.block;

import com.ldtteam.domumornamentum.block.decorative.*;
import com.ldtteam.domumornamentum.block.types.BrickType;
import com.ldtteam.domumornamentum.block.types.ExtraBlockType;
import com.ldtteam.domumornamentum.block.types.FramedLightType;
import com.ldtteam.domumornamentum.block.types.TimberFrameType;
import com.ldtteam.domumornamentum.block.vanilla.*;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.core.BlockIdContext;
import com.ldtteam.domumornamentum.item.decoration.*;
import com.ldtteam.domumornamentum.item.interfaces.IDoItem;
import com.ldtteam.domumornamentum.item.vanilla.*;
import com.ldtteam.domumornamentum.shingles.ShingleHeightType;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Blocks;

/**
 * Class to create the modBlocks.
 * References to the blocks can be made here
 * <p>
 * We disabled the following finals since we are neither able to mark the items as final, nor do we want to provide public accessors.
 */
@SuppressWarnings({"squid:ClassVariableVisibilityCheck", "squid:S2444", "squid:S1444", "squid:S1820",})
public final class ModBlocks implements IModBlocks {
    /**
     * Everything this mod put into {@link BuiltInRegistries#BLOCK} / {@link BuiltInRegistries#ITEM}, in
     * registration order. Replaces NeoForge's per-mod {@code DeferredRegister} view, which does not exist on
     * Fabric — vanilla registries are shared by every mod, so the mod has to remember its own entries.
     */
    private final static List<Block> BLOCKS = new ArrayList<>();
    private final static List<Item> ITEMS = new ArrayList<>();

    private static final List<Supplier<TimberFrameBlock>> TIMBER_FRAMES = new ArrayList<>();
    private static final List<Supplier<FramedLightBlock>> FRAMED_LIGHT = new ArrayList<>();
    private static final List<Supplier<FloatingCarpetBlock>> FLOATING_CARPETS = new ArrayList<>();
    private static final List<Supplier<ExtraBlock>> EXTRA_TOP_BLOCKS = new ArrayList<>();
    private static final List<Supplier<BrickBlock>> BRICK = new ArrayList<>();
    private static final List<Supplier<PillarBlock>> PILLARS = new ArrayList<>();
    private static final List<Supplier<AllBrickBlock>> ALL_BRICK = new ArrayList<>();
    private static final List<Supplier<AllBrickStairBlock>> ALL_BRICK_STAIR = new ArrayList<>();

    private static final ModBlocks INSTANCE = new ModBlocks();

    private static final Object ITEM_GROUP_LOCK = new Object();

    private static final Supplier<ArchitectsCutterBlock> ARCHITECTS_CUTTER;
    private static final Supplier<ShingleBlock> SHINGLE;
    private static final Supplier<ShingleBlock> SHINGLE_FLAT;
    private static final Supplier<ShingleBlock> SHINGLE_FLAT_LOWER;
    private static final Supplier<ShingleSlabBlock> SHINGLE_SLAB;
    private static final Supplier<PaperWallBlock> PAPER_WALL;
    private static final Supplier<BarrelBlock> STANDING_BARREL;
    private static final Supplier<BarrelBlock> LAYING_BARREL;
    private static final Supplier<FenceBlock> FENCE;
    private static final Supplier<FenceGateBlock> FENCE_GATE;
    private static final Supplier<SlabBlock> SLAB;
    private static final Supplier<WallBlock> WALL;
    private static final Supplier<StairBlock> STAIR;
    private static final Supplier<TrapdoorBlock> TRAPDOOR;
    private static final Supplier<DoorBlock> DOOR;
    private static final Supplier<PostBlock> POST;
    private static final Supplier<PanelBlock> PANEL;
    private static final Supplier<FancyDoorBlock> FANCY_DOOR;
    private static final Supplier<FancyTrapdoorBlock> FANCY_TRAPDOOR;
    private static final Supplier<PaperWallBlock> TILED_PAPER_WALL;
    private static final Supplier<DynamicTimberFrameBlock> DYNAMIC_TIMBER_FRAME;

    static {
        ARCHITECTS_CUTTER = registerSimpleBlockItem("architectscutter", ArchitectsCutterBlock::new);

        for (final TimberFrameType blockType : TimberFrameType.values()) {
            TIMBER_FRAMES.add(registerCustomBlockItem(blockType.getName(), () -> new TimberFrameBlock(blockType), TimberFrameBlockItem::new));
        }
        DYNAMIC_TIMBER_FRAME = registerCustomBlockItem("dynamic_timberframe", () -> new DynamicTimberFrameBlock(), DynamicTimberFrameBlockItem::new);

        SHINGLE = registerCustomBlockItem("shingle", ShingleBlock::new, ShingleBlockItem::new);
        SHINGLE_FLAT = registerCustomBlockItem("shingle_flat", ShingleBlock::new, ShingleBlockItem::new);
        SHINGLE_FLAT_LOWER = registerCustomBlockItem("shingle_flat_lower", ShingleBlock::new, ShingleBlockItem::new);

        SHINGLE_SLAB = registerCustomBlockItem("shingle_slab", ShingleSlabBlock::new, ShingleSlabBlockItem::new);
        PAPER_WALL = registerCustomBlockItem("blockpaperwall", PaperWallBlock::new, PaperwallBlockItem::new);
        TILED_PAPER_WALL = registerCustomBlockItem("blocktiledpaperwall", PaperWallBlock::new, PaperwallBlockItem::new);

        PILLARS.add(registerCustomBlockItem("blockpillar", PillarBlock::new, PillarBlockItem::new));
        PILLARS.add(registerCustomBlockItem("blockypillar", PillarBlock::new, PillarBlockItem::new));
        PILLARS.add(registerCustomBlockItem("squarepillar", PillarBlock::new, PillarBlockItem::new));

        for (final ExtraBlockType blockType : ExtraBlockType.values()) {
            EXTRA_TOP_BLOCKS.add(registerCustomBlockItem(blockType.getSerializedName(), () -> new ExtraBlock(blockType), ExtraBlockItem::new));
        }

        for (final FramedLightType blockType : FramedLightType.values())
        {
            FRAMED_LIGHT.add(registerCustomBlockItem(blockType.getName(), () -> new FramedLightBlock(blockType), FramedLightBlockItem::new));
        }

        for (final DyeColor color : DyeColor.values()) {
            FLOATING_CARPETS.add(registerSimpleBlockItem(color.getName().toLowerCase(Locale.ROOT) + "_floating_carpet", () -> new FloatingCarpetBlock(color)));
        }

        for (final BrickType type : BrickType.values()) {
            BRICK.add(registerSimpleBlockItem(type.getSerializedName(), () -> new BrickBlock(type)));
        }

        STANDING_BARREL = registerSimpleBlockItem("blockbarreldeco_standing", BarrelBlock::new);
        LAYING_BARREL = registerSimpleBlockItem("blockbarreldeco_onside", BarrelBlock::new);

        FENCE = registerCustomBlockItem("vanilla_fence_compat", FenceBlock::new, FenceBlockItem::new);
        FENCE_GATE = registerCustomBlockItem("vanilla_fence_gate_compat", FenceGateBlock::new, FenceGateBlockItem::new);
        SLAB = registerCustomBlockItem("vanilla_slab_compat", SlabBlock::new, SlabBlockItem::new);
        WALL = registerCustomBlockItem("vanilla_wall_compat", WallBlock::new, WallBlockItem::new);
        STAIR = registerCustomBlockItem("vanilla_stairs_compat", StairBlock::new, StairsBlockItem::new);
        TRAPDOOR = registerCustomBlockItem("vanilla_trapdoors_compat", TrapdoorBlock::new, TrapdoorBlockItem::new);
        DOOR = registerCustomBlockItem("vanilla_doors_compat", DoorBlock::new, DoorBlockItem::new);
        PANEL = registerCustomBlockItem("panel", PanelBlock::new, PanelBlockItem::new);
        ALL_BRICK.add(registerCustomBlockItem("light_brick", AllBrickBlock::new, AllBrickBlockItem::new));
        ALL_BRICK.add(registerCustomBlockItem("dark_brick", AllBrickBlock::new, AllBrickBlockItem::new));
        ALL_BRICK_STAIR.add(registerCustomBlockItem("light_brick_stair", AllBrickStairBlock::new, AllBrickStairBlockItem::new));
        ALL_BRICK_STAIR.add(registerCustomBlockItem("dark_brick_stair", AllBrickStairBlock::new, AllBrickStairBlockItem::new));

        POST = registerCustomBlockItem("post", PostBlock::new, PostBlockItem::new);

        FANCY_DOOR = registerCustomBlockItem("fancy_door", FancyDoorBlock::new, FancyDoorBlockItem::new);
        FANCY_TRAPDOOR = registerCustomBlockItem("fancy_trapdoors", FancyTrapdoorBlock::new, FancyTrapdoorBlockItem::new);
    }

    /**
     * Specific item groups.
     * <p>
     * Published as a whole by {@link #getOrComputeItemGroups()} once it is complete, never filled in place:
     * the Architect's Cutter reads this from the server thread ({@code ArchitectsCutterContainer}) and from
     * the render thread ({@code ArchitectsCutterScreen}) at the same time on a single player world. Filling a
     * plain {@link TreeMap} from both at once corrupted it and duplicated every variant.
     */
    public volatile Map<Identifier, List<ItemStack>> itemGroups = Map.of();

    /**
     * Private constructor to hide the implicit public one.
     */
    private ModBlocks() {
    }

    public static ModBlocks getInstance() {
        return INSTANCE;
    }

    /**
     * Class-load hook. Everything is registered eagerly from the static initialiser above (contract C1);
     * calling this from the mod initialiser is what pins the moment it happens.
     */
    public static void init()
    {
    }

    public static ResourceKey<Block> blockKey(final String name)
    {
        return ResourceKey.create(Registries.BLOCK, Constants.resLocDO(name));
    }

    public static ResourceKey<Item> itemKey(final String name)
    {
        return ResourceKey.create(Registries.ITEM, Constants.resLocDO(name));
    }

    /**
     * Utility shorthand to register blocks eagerly into the vanilla registries.
     * Register item block together.
     *
     * @param name  the registry name of the block
     * @param block a factory / constructor to create the block on demand
     * @param <B>   the block subclass for the factory response
     * @return a supplier of the block entry saved to the registry
     */
    public static <B extends Block> Supplier<B> registerSimpleBlockItem(String name, Supplier<B> block)
    {
        final B registered = registerBlock(name, block);
        registerItem(name, new BlockItem(registered, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey(name))));
        return () -> registered;
    }

    public static <B extends Block> Supplier<B> registerCustomBlockItem(String name, Supplier<B> block, BiFunction<B, Item.Properties, ? extends BlockItem> item)
    {
        final B registered = registerBlock(name, block);
        registerItem(name, item.apply(registered, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey(name))));
        return () -> registered;
    }

    /**
     * Builds and registers one block. The pending {@link ResourceKey} is published to {@link BlockIdContext}
     * around the factory call so that the {@code BlockBehaviour.Properties} the block builds inside its own
     * constructor gets its mandatory id — see {@link BlockIdContext} for the full story.
     */
    private static <B extends Block> B registerBlock(final String name, final Supplier<B> factory)
    {
        final ResourceKey<Block> key = blockKey(name);
        final B block;
        BlockIdContext.set(key);
        try
        {
            block = factory.get();
        }
        finally
        {
            BlockIdContext.clear();
        }

        Registry.register(BuiltInRegistries.BLOCK, key, block);
        BLOCKS.add(block);
        return block;
    }

    private static <I extends Item> I registerItem(final String name, final I item)
    {
        Registry.register(BuiltInRegistries.ITEM, itemKey(name), item);
        ITEMS.add(item);
        return item;
    }

    @Override
    public ArchitectsCutterBlock getArchitectsCutter() {
        return ModBlocks.ARCHITECTS_CUTTER.get();
    }

    @Override
    public ShingleBlock getShingle(final ShingleHeightType heightType) {
        return switch (heightType)
        {
            case DEFAULT -> ModBlocks.SHINGLE.get();
            case FLAT -> ModBlocks.SHINGLE_FLAT.get();
            case FLAT_LOWER -> ModBlocks.SHINGLE_FLAT_LOWER.get();
        };
    }

    @Override
    public List<TimberFrameBlock> getTimberFrames() {
        return ModBlocks.TIMBER_FRAMES.stream().map(Supplier::get).collect(Collectors.toList());
    }

    @Override
    public List<FramedLightBlock> getFramedLights()
    {
        return ModBlocks.FRAMED_LIGHT.stream().map(Supplier::get).collect(Collectors.toList());
    }

    @Override
    public List<PillarBlock> getPillars()
    {
        return ModBlocks.PILLARS.stream().map(Supplier::get).collect(Collectors.toList());
    }

    @Override
    public ShingleSlabBlock getShingleSlab() {
        return ModBlocks.SHINGLE_SLAB.get();
    }

    @Override
    public PaperWallBlock getPaperWall() {
        return ModBlocks.PAPER_WALL.get();
    }

    @Override
    public PaperWallBlock getTiledPaperWall() {
        return ModBlocks.TILED_PAPER_WALL.get();
    }

    @Override
    public List<ExtraBlock> getExtraTopBlocks() {
        return ModBlocks.EXTRA_TOP_BLOCKS.stream().map(Supplier::get).toList();
    }

    @Override
    public List<FloatingCarpetBlock> getFloatingCarpets() {
        return ModBlocks.FLOATING_CARPETS.stream().map(Supplier::get).toList();
    }

    @Override
    public BarrelBlock getStandingBarrel() {
        return ModBlocks.STANDING_BARREL.get();
    }

    @Override
    public BarrelBlock getLayingBarrel() {
        return ModBlocks.LAYING_BARREL.get();
    }

    @Override
    public FenceBlock getFence() {
        return ModBlocks.FENCE.get();
    }

    @Override
    public FenceGateBlock getFenceGate() {
        return ModBlocks.FENCE_GATE.get();
    }

    @Override
    public SlabBlock getSlab() {
        return ModBlocks.SLAB.get();
    }

    @Override
    public List<BrickBlock> getBricks() {
        return ModBlocks.BRICK.stream().map(Supplier::get).toList();
    }

    @Override
    public WallBlock getWall() {
        return ModBlocks.WALL.get();
    }

    @Override
    public StairBlock getStair() {
        return ModBlocks.STAIR.get();
    }

    @Override
    public TrapdoorBlock getTrapdoor() {
        return ModBlocks.TRAPDOOR.get();
    }

    @Override
    public PanelBlock getPanel() {
        return ModBlocks.PANEL.get();
    }

    @Override
    public PostBlock getPost() {
        return ModBlocks.POST.get();
    }

    @Override
    public DoorBlock getDoor() {
        return ModBlocks.DOOR.get();
    }

    @Override
    public FancyDoorBlock getFancyDoor() {
        return ModBlocks.FANCY_DOOR.get();
    }

    @Override
    public FancyTrapdoorBlock getFancyTrapdoor() {
        return ModBlocks.FANCY_TRAPDOOR.get();
    }

    @Override
    public List<AllBrickBlock> getAllBrickBlocks() {
        return ModBlocks.ALL_BRICK.stream().map(Supplier::get).toList();
    }

    @Override
    public List<AllBrickStairBlock> getAllBrickStairBlocks() {
        return ModBlocks.ALL_BRICK_STAIR.stream().map(Supplier::get).toList();
    }

    @Override
    public DynamicTimberFrameBlock getDynamicTimberFrame() {
        return ModBlocks.DYNAMIC_TIMBER_FRAME.get();
    }

    /**
     * Get or compute the item group specifics.
     * @return the item group.
     */
    public Map<Identifier, List<ItemStack>> getOrComputeItemGroups()
    {
        Map<Identifier, List<ItemStack>> groups = itemGroups;
        if (!groups.isEmpty())
        {
            return groups;
        }

        synchronized (ITEM_GROUP_LOCK)
        {
            groups = itemGroups;
            if (!groups.isEmpty())
            {
                return groups;
            }

            final Map<Identifier, List<ItemStack>> computed = new TreeMap<>();
            BuiltInRegistries.ITEM.forEach(item -> {
                if (item instanceof IDoItem)
                {
                    final List<ItemStack> itemList = computed.computeIfAbsent(((IDoItem) item).getGroup(), k -> new ArrayList<>());
                    if (item instanceof BlockItem blockitem && blockitem.getBlock() instanceof IMateriallyTexturedBlock texturedBlock) {
                        if (blockitem.getBlock() instanceof ICachedItemGroupBlock cachedItemGroupBlock)
                        {
                            final NonNullList<ItemStack> stacks = NonNullList.create();
                            cachedItemGroupBlock.fillItemCategory(stacks);

                            for (final ItemStack stack : stacks)
                            {
                                itemList.add(process(stack.copy(), texturedBlock));
                            }
                        }
                        else
                        {
                            itemList.add(process(new ItemStack(item), texturedBlock));
                        }
                    }
                }
            });

            itemGroups = computed;
            return computed;
        }
    }

    private ItemStack process(final ItemStack stack, final IMateriallyTexturedBlock block)
    {
        final @NotNull List<IMateriallyTexturedBlockComponent> components = new ArrayList<>(block.getComponents());
        final MaterialTextureData.Builder textureData = MaterialTextureData.builder();

        for (final IMateriallyTexturedBlockComponent component : components)
        {
            textureData.setComponent(component.getId(), component.getDefault());
        }

        textureData.writeToItemStack(stack);

        return stack;
    }

    public static Block[] getMateriallyTexturableBlocks() {
        return BLOCKS.stream()
                .filter(IMateriallyTexturedBlock.class::isInstance)
                .toArray(Block[]::new);
    }

    public static Item[] getMateriallyTexturableItems() {
        return Arrays.stream(getMateriallyTexturableBlocks())
                .map(BuiltInRegistries.BLOCK::getKey)
                .map(BuiltInRegistries.ITEM::getValue)
                .filter(Objects::nonNull)
                .toArray(Item[]::new);
    }
}
