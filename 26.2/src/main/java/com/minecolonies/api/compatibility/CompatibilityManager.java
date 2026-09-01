package com.minecolonies.api.compatibility;

import net.minecraft.core.Holder;
import net.minecraft.tags.BlockItemTags;
import com.minecolonies.api.util.Utils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.compatibility.dynamictrees.DynamicTreeCompat;
import com.minecolonies.api.compatibility.resourcefulbees.ResourcefulBeesCompat;
import com.minecolonies.api.compatibility.tinkers.SlimeTreeCheck;
import com.minecolonies.api.compatibility.tinkers.TinkersToolHelper;
import com.minecolonies.api.crafting.CompostRecipe;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.registry.ModRecipeSerializer;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import com.minecolonies.core.colony.crafting.LootTableAnalyzer;
import com.minecolonies.core.util.FurnaceRecipes;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.minecolonies.api.util.ItemStackUtils.*;
import static com.minecolonies.api.util.constant.Constants.DEFAULT_TAB_KEY;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_SAP_LEAF;

/**
 * CompatibilityManager handling certain list and maps of itemStacks of certain types.
 */
public class CompatibilityManager implements ICompatibilityManager
{
    /**
     * Maximum depth sub items are explored at
     */
    private static final int MAX_DEPTH = 100;

    /**
     * Replacement for NeoForge's {@code Tags.Items.ORES}. NeoForge's common-tag tree does not exist on Fabric; the
     * equivalent convention tag is {@code c:ores}.
     */
    private static final TagKey<Item> COMMON_ORES =
      TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores"));

    /**
     * BiMap of saplings and leaves.
     */
    private final Map<Block, ItemStorage> leavesToSaplingMap = new HashMap<>();

    /**
     * List of saplings. Works on client and server-side.
     */
    private final List<ItemStorage> saplings = new ArrayList<>();

    /**
     * List of all ore-like blocks. Works on client and server-side.
     */
    private final Set<Block> oreBlocks = new HashSet<>();

    /**
     * List of all ore-like items.
     */
    private final Set<ItemStorage> smeltableOres = new HashSet<>();

    /**
     * List of all the compost recipes
     */
    private final Map<Item, RecipeHolder<CompostRecipe>> compostRecipes = new HashMap<>();

    /**
     * List of all the items that can be planted.
     */
    private final Set<ItemStorage> plantables = new HashSet<>();

    /**
     * List of all the items that can be used as fuel
     */
    private final Set<ItemStorage> fuel = new HashSet<>();

    /**
     * List of all the items that can be used as food
     */
    private final Set<ItemStorage> food = new HashSet<>();

    /**
     * List of all the items that can be used as food
     */
    private final Set<ItemStorage> edibles = new HashSet<>();

    /**
     * Set of all beekeeper flowers, resolved from {@code #minecraft:flowers} the first time it is asked for and
     * dropped again by {@link #clear()}.
     * <p>
     * PORT-NOTE(26.2): this used to be scanned out of the creative tabs alongside the other lists here and shipped to
     * the client inside the compatibility packet. Both halves were removed rather than repaired. The question "which
     * items are flowers" is answered by a vanilla item tag that both sides already hold - the beekeeper's other three
     * readers ({@code BuildingBeekeeper}'s keep-in-stock predicate, {@code EntityAIWorkBeekeeper#equipBreedItem} and
     * the hut's request) all ask the tag directly - so routing it through a scan and a hand written packet bought
     * nothing and cost a single point of failure: {@link #deserialize} empties every list before refilling it, so any
     * throw anywhere in that method leaves a client with no flowers and therefore a beekeeper that cannot be
     * configured at all. Reading the tag has no such failure mode, and it also picks up tagged flowers from mods that
     * put nothing in a creative tab, which the scan silently dropped. On vanilla 26.2 the two agree exactly, 31 items
     * either way.
     */
    private ImmutableSet<ItemStorage> beekeeperflowers = ImmutableSet.of();

    /**
     * List of all blocks.
     */
    private static ImmutableList<ItemStack> allItems = ImmutableList.of();

    /**
     * Hashmap of mobs we may or may not attack.
     */
    private ImmutableSet<Identifier> monsters = ImmutableSet.of();

    /**
     * Mapping of itemstorage to creativemodetab.
     */
    private final Map<ItemStorage, CreativeModeTab> creativeModeTabMap = new HashMap<>();

    /**
     * Furnace recipes storage
     */
    private final FurnaceRecipes furnaceRecipes = new FurnaceRecipes();

    /**
     * Cached mapping of items and colors to dyes.
     */
    private final Int2ObjectMap<Int2IntMap> dyeColorMap = new Int2ObjectOpenHashMap<>();

    /**
     * Instantiates the compatibilityManager.
     */
    public CompatibilityManager()
    {
        /*
         * Intentionally left empty.
         */
    }

    private void clear()
    {
        saplings.clear();
        oreBlocks.clear();
        smeltableOres.clear();
        plantables.clear();
        beekeeperflowers = ImmutableSet.of();

        food.clear();
        edibles.clear();
        fuel.clear();
        compostRecipes.clear();

        monsters = ImmutableSet.of();
        creativeModeTabMap.clear();
    }

    /**
     * Called server-side *only* to calculate the various lists of items from the registry, recipes, and tags.
     *
     * @param recipeManager The vanilla recipe manager.
     */
    @Override
    public void discover(@NotNull final RecipeManager recipeManager, final Level level)
    {
        clear();
        discoverAllItems(level);
        discoverLeaves(level);

        discoverModCompat();

        discoverCompostRecipes(recipeManager);
        discoverMobs();
    }

    @Override
    public void serialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(CHECKED_NBT_KEYS.size());
        for (final var entry : CHECKED_NBT_KEYS.entrySet())
        {
            buf.writeInt(BuiltInRegistries.ITEM.getId(entry.getKey()));
            buf.writeInt(entry.getValue().size());
            for (final DataComponentType<?> key : entry.getValue())
            {
                Utils.serializeCodecMess(DataComponentType.STREAM_CODEC, buf, key);
            }
        }

        serializeItemStorageList(buf, saplings);
        serializeBlockList(buf, oreBlocks);
        serializeItemStorageList(buf, smeltableOres);
        serializeItemStorageList(buf, plantables);

        serializeItemStorageList(buf, food);
        serializeItemStorageList(buf, edibles);
        serializeItemStorageList(buf, fuel);
        serializeRegistryIds(buf, BuiltInRegistries.ENTITY_TYPE, monsters);

        serializeCompostRecipes(buf, compostRecipes);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf, final ClientLevel level)
    {
        clear();

        for (int i = 0, amount = buf.readInt(); i < amount; i++)
        {
            final Item item = BuiltInRegistries.ITEM.byId(buf.readInt());
            Set<DataComponentType<?>> nbtKeys = new HashSet<>();
            for (int j = 0, children = buf.readInt(); j < children; j++)
            {
                nbtKeys.add(Utils.deserializeCodecMess(DataComponentType.STREAM_CODEC, buf));
            }

            CHECKED_NBT_KEYS.put(item, nbtKeys);
        }

        discoverAllItems(level);

        saplings.addAll(deserializeItemStorageList(buf));
        oreBlocks.addAll(deserializeBlockList(buf));
        smeltableOres.addAll(deserializeItemStorageList(buf));
        plantables.addAll(deserializeItemStorageList(buf));

        food.addAll(deserializeItemStorageList(buf));
        edibles.addAll(deserializeItemStorageList(buf));
        fuel.addAll(deserializeItemStorageList(buf));
        monsters = ImmutableSet.copyOf(deserializeRegistryIds(buf, BuiltInRegistries.ENTITY_TYPE));

        Log.getLogger().info("Synchronized {} saplings", saplings.size());
        Log.getLogger().info("Synchronized {} ore blocks with {} smeltable ores", oreBlocks.size(), smeltableOres.size());
        Log.getLogger().info("Synchronized {} plantables", plantables.size());
        Log.getLogger().info("Resolved {} flowers", getImmutableFlowers().size());

        Log.getLogger().info("Synchronized {} food types with {} edible", food.size(), edibles.size());
        Log.getLogger().info("Synchronized {} fuel types", fuel.size());
        Log.getLogger().info("Synchronized {} monsters", monsters.size());

        discoverCompostRecipes(deserializeCompostRecipes(buf));

        // the below are loaded from config files, which have been synched already by this point
        discoverModCompat();
    }

    private static void serializeItemStorageList(
      @NotNull final RegistryFriendlyByteBuf buf,
      @NotNull final Collection<ItemStorage> list)
    {
        buf.writeCollection(list, (buffer, storage) -> StandardFactoryController.getInstance().serialize((RegistryFriendlyByteBuf) buffer, storage));
    }

    @NotNull
    private static List<ItemStorage> deserializeItemStorageList(@NotNull final RegistryFriendlyByteBuf buf)
    {
        return buf.readList((buffer) -> StandardFactoryController.getInstance().deserialize((RegistryFriendlyByteBuf) buffer));
    }

    private static void serializeBlockList(
      @NotNull final RegistryFriendlyByteBuf buf,
      @NotNull final Collection<Block> list)
    {
        buf.writeCollection(list.stream().map(ItemStack::new).toList(), (b, stack) -> Utils.serializeCodecMess((RegistryFriendlyByteBuf) b, stack));
    }

    @NotNull
    private static List<Block> deserializeBlockList(@NotNull final RegistryFriendlyByteBuf buf)
    {
        final List<ItemStack> stacks = buf.readList(b -> Utils.deserializeCodecMess((RegistryFriendlyByteBuf) b));
        return stacks.stream()
          .flatMap(stack -> stack.getItem() instanceof BlockItem blockItem
                              ? Stream.of(blockItem.getBlock()) : Stream.empty())
          .toList();
    }

    private static void serializeRegistryIds(
      @NotNull final RegistryFriendlyByteBuf buf,
      @NotNull final Registry<?> registry,
      @NotNull final Collection<Identifier> ids)
    {
        buf.writeCollection(ids, (b, id) -> b.writeIdentifier(id));
    }

    @NotNull
    private static <T> List<Identifier>
    deserializeRegistryIds(
      @NotNull final RegistryFriendlyByteBuf buf,
      @NotNull final Registry<T> registry)
    {
        return buf.readList(b -> b.readIdentifier());
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<RecipeHolder<?>>> RECIPE_LIST_STREAM_CODEC =
            RecipeHolder.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static void serializeCompostRecipes(
      @NotNull final RegistryFriendlyByteBuf buf,
      @NotNull final Map<Item, RecipeHolder<CompostRecipe>> compostRecipes)
    {
        final List<RecipeHolder<CompostRecipe>> recipes = compostRecipes.values().stream().distinct().toList();
        //RECIPE_LIST_STREAM_CODEC.encode(buf, recipes);
        buf.writeCollection(recipes, (b, holder) -> RecipeHolder.STREAM_CODEC.encode((RegistryFriendlyByteBuf) b, holder));
    }

    @NotNull
    private static List<RecipeHolder<CompostRecipe>> deserializeCompostRecipes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        //return RECIPE_LIST_STREAM_CODEC.decode(buf).stream().map(r -> (RecipeHolder<CompostRecipe>) r).toList();
        return buf.readList(b -> (RecipeHolder<CompostRecipe>) RecipeHolder.STREAM_CODEC.decode((RegistryFriendlyByteBuf) b));
    }

    /**
     * Getter for the list.
     *
     * @return the list of itemStacks.
     */
    @Override
    public List<ItemStack> getListOfAllItems()
    {
        if (allItems.isEmpty())
        {
            Log.getLogger().error("getListOfAllItems when empty");
        }
        return allItems;
    }

    @Override
    public List<ItemStack> getListOfMatchingItems(final Predicate<ItemStack> predicate)
    {
        List<ItemStack> list = new ArrayList<>();
        for (final ItemStack stack : allItems)
        {
            if (predicate.test(stack))
            {
                list.add(stack);
            }
        }
        return list;
    }

    @Override
    public Set<ItemStorage> getSetOfAllItems()
    {
        if (creativeModeTabMap.isEmpty())
        {
            Log.getLogger().error("getSetOfAllItems when empty");
        }
        return creativeModeTabMap.keySet();
    }

    @Override
    public boolean isPlantable(final ItemStack itemStack)
    {
        return !itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem && itemStack.is(ModTags.floristFlowers);
    }

    @Override
    public boolean isLuckyBlock(final Block block)
    {
        return block.defaultBlockState().is(ModTags.oreChanceBlocks);
    }

    @Nullable
    @Override
    public ItemStack getSaplingForLeaf(final Block block)
    {
        if (leavesToSaplingMap.containsKey(block))
        {
            return leavesToSaplingMap.get(block).getItemStack();
        }
        return null;
    }

    @Override
    public Set<ItemStorage> getCopyOfSaplings()
    {
        if (saplings.isEmpty())
        {
            Log.getLogger().error("getCopyOfSaplings when empty");
        }
        return new HashSet<>(saplings);
    }

    @Override
    public Set<ItemStorage> getFuel()
    {
        if (fuel.isEmpty())
        {
            Log.getLogger().error("getFuel when empty");
        }
        return fuel;
    }

    @Override
    public Set<ItemStorage> getFood()
    {
        if (food.isEmpty())
        {
            Log.getLogger().error("getFood when empty");
        }
        return food;
    }

    @Override
    public Set<ItemStorage> getEdibles(final int minNutrition)
    {
        if (edibles.isEmpty())
        {
            Log.getLogger().error("getEdibles when empty");
        }
        final Set<ItemStorage> filteredEdibles = new HashSet<>();
        for (final ItemStorage storage : edibles)
        {
            if ((storage.getItemStack().get(DataComponents.FOOD) != null && storage.getItemStack().get(DataComponents.FOOD).nutrition() >= minNutrition))
            {
                filteredEdibles.add(storage);
            }
        }
        return filteredEdibles;
    }

    @Override
    public Set<ItemStorage> getSmeltableOres()
    {
        if (smeltableOres.isEmpty())
        {
            Log.getLogger().error("getSmeltableOres when empty");
        }
        return smeltableOres;
    }

    @Override
    public Map<Item, RecipeHolder<CompostRecipe>> getCopyOfCompostRecipes()
    {
        if (compostRecipes.isEmpty())
        {
            Log.getLogger().error("getCopyOfCompostRecipes when empty");
        }
        return ImmutableMap.copyOf(compostRecipes);
    }

    @Override
    public Set<ItemStorage> getCompostInputs()
    {
        if (compostRecipes.isEmpty())
        {
            Log.getLogger().error("getCompostInputs when empty");
        }
        return compostRecipes.keySet().stream()
          .map(item -> new ItemStorage(new ItemStack(item)))
          .collect(Collectors.toSet());
    }

    @Override
    public Set<ItemStorage> getCopyOfPlantables()
    {
        if (plantables.isEmpty())
        {
            Log.getLogger().error("getCopyOfPlantables when empty");
        }
        return new HashSet<>(plantables);
    }

    @Override
    public Set<ItemStorage> getImmutableFlowers()
    {
        if (beekeeperflowers.isEmpty())
        {
            final ImmutableSet.Builder<ItemStorage> builder = new ImmutableSet.Builder<>();
            for (final Holder<Item> flower : BuiltInRegistries.ITEM.getTagOrEmpty(BlockItemTags.FLOWERS.item()))
            {
                builder.add(new ItemStorage(new ItemStack(flower.value())));
            }
            beekeeperflowers = builder.build();

            if (beekeeperflowers.isEmpty())
            {
                // Only reachable before the tags are bound, or if a datapack really did empty #minecraft:flowers.
                Log.getLogger().error("getImmutableFlowers when empty");
            }
        }
        return beekeeperflowers;
    }

    @Override
    public boolean isOre(final BlockState block)
    {
        if (oreBlocks.isEmpty())
        {
            Log.getLogger().error("isOre when empty");
        }

        return oreBlocks.contains(block.getBlock());
    }

    @Override
    public boolean isOre(@NotNull final ItemStack stack)
    {
        if (isBreakableOre(stack))
        {
            return true;
        }
        if (isMineableOre(stack) || stack.is(ModTags.raw_ore))
        {
            ItemStack smeltingResult = MinecoloniesAPIProxy.getInstance().getFurnaceRecipes().getSmeltingResult(stack);
            return !smeltingResult.isEmpty();
        }

        return false;
    }

    @Override
    public boolean isMineableOre(@NotNull final ItemStack stack)
    {
        return !isEmpty(stack) && stack.is(COMMON_ORES);
    }

    @Override
    public boolean isBreakableOre(@NotNull final ItemStack stack)
    {
        if (stack.is(ModTags.breakable_ore))
        {
            final Block block = Block.byItem(stack.getItem());
            if (!block.defaultBlockState().isAir())
            {
                final List<LootTableAnalyzer.LootDrop> drops = block.getLootTable().map(table -> CustomRecipeManager.getInstance().getLootDrops(table)).orElse(Collections.emptyList());
                for (final LootTableAnalyzer.LootDrop drop : drops)
                {
                    for (final ItemStack dropStack : drop.getItemStacks())
                    {
                        if (ItemStackUtils.compareItemStacksIgnoreStackSize(stack, dropStack))
                        {
                            return false;   // blocks that drop themselves are not breakable ore
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void write(@NotNull final HolderLookup.Provider provider, @NotNull final CompoundTag compound)
    {
        @NotNull final ListTag saplingsLeavesTagList =
          leavesToSaplingMap.entrySet()
            .stream()
            .filter(entry -> entry.getKey() != null && !entry.getValue().getItemStack().isEmpty())
            .map(entry -> writeLeafSaplingEntryToNBT(provider, entry.getKey().defaultBlockState(), entry.getValue()))
            .collect(NBTUtils.toListNBT());
        compound.put(TAG_SAP_LEAF, saplingsLeavesTagList);
    }

    @Override
    public void read(@NotNull final HolderLookup.Provider provider, @NotNull final CompoundTag compound)
    {
        NBTUtils.streamCompound(compound.getListOrEmpty(TAG_SAP_LEAF))
          .map(nbt -> CompatibilityManager.readLeafSaplingEntryFromNBT(provider, nbt))
          .filter(key -> !key.getA().isAir() && !leavesToSaplingMap.containsKey(key.getA().getBlock()) && !leavesToSaplingMap.containsValue(key.getB()))
          .forEach(key -> leavesToSaplingMap.put(key.getA().getBlock(), key.getB()));
    }

    @Override
    public void connectLeafToSapling(final Block leaf, final ItemStack stack)
    {
        if (!leavesToSaplingMap.containsKey(leaf))
        {
            leavesToSaplingMap.put(leaf, new ItemStorage(stack, false, true));
        }
    }

    @Override
    public CreativeModeTab getCreativeTab(final ItemStorage checkItem)
    {
        return creativeModeTabMap.get(checkItem);
    }

    @Override
    public int getCreativeTabKey(final ItemStorage checkItem)
    {
        final CreativeModeTab creativeTab = creativeModeTabMap.get(checkItem);
        return creativeTab == null ? DEFAULT_TAB_KEY : creativeModeTabMap.get(checkItem).column();
    }

    @Override
    public ImmutableSet<Identifier> getAllMonsters()
    {
        if (monsters.isEmpty())
        {
            Log.getLogger().error("getAllMonsters when empty");
        }
        return monsters;
    }

    //------------------------------- Private Utility Methods -------------------------------//

    /**
     * Calculate all monsters.
     */
    private void discoverMobs()
    {
        Set<Identifier> monsterSet = new HashSet<>();

        for (final Map.Entry<ResourceKey<EntityType<?>>, EntityType<?>> entry : BuiltInRegistries.ENTITY_TYPE.entrySet())
        {
            if (entry.getValue().getCategory() == MobCategory.MONSTER)
            {
                monsterSet.add(entry.getKey().identifier());
            }
            else if (entry.getValue().builtInRegistryHolder().is(ModTags.hostile))
            {
                monsterSet.add(entry.getKey().identifier());
            }
        }

        monsters = ImmutableSet.copyOf(monsterSet);
    }

    /**
     * Create complete list of all existing items, client side only.
     */
    private void discoverAllItems(final Level level)
    {
        if (!food.isEmpty())
        {
            return;
        }

        final Set<ItemStorage> tempDuplicates = new HashSet<>();

        final CreativeModeTab.ItemDisplayParameters tempDisplayParams = new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), false, level.registryAccess());

        final ImmutableList.Builder<ItemStack> listBuilder = new ImmutableList.Builder<>();

        CraftingUtils.forEachCreativeTabItems(tempDisplayParams, (tab, stacks) ->
        {
            final Object2IntLinkedOpenHashMap<Item> mapping = new Object2IntLinkedOpenHashMap<>();
            for (final ItemStack item : stacks)
            {
                if (!tempDuplicates.add(new ItemStorage(item)) || mapping.addTo(item.getItem(), 1) > MAX_DEPTH)
                {
                    continue;
                }

                listBuilder.add(item);
                discoverSaplings(item);
                discoverOres(item);
                discoverPlantables(item);
                discoverFood(item);
                discoverFuel(level, item);

                creativeModeTabMap.put(new ItemStorage(item), tab);
            }
        });

        discoverFungi();

        Log.getLogger().info("Finished discovering Ores " + oreBlocks.size() + " " + smeltableOres.size());
        Log.getLogger().info("Finished discovering saplings " + saplings.size());
        Log.getLogger().info("Finished discovering plantables " + plantables.size());
        Log.getLogger().info("Finished discovering food " + edibles.size() + " " + food.size());
        Log.getLogger().info("Finished discovering fuel " + fuel.size());
        Log.getLogger().info("Finished discovering flowers " + getImmutableFlowers().size());


        allItems = listBuilder.build();
        Log.getLogger().info("Finished discovering items " + allItems.size());
    }

    /**
     * Discover ores for the Smelter and Miners.
     */
    private void discoverOres(final ItemStack stack)
    {
        if (stack.is(COMMON_ORES) || stack.is(ModTags.breakable_ore) || stack.is(ModTags.raw_ore))
        {
            if (stack.getItem() instanceof BlockItem)
            {
                oreBlocks.add(((BlockItem) stack.getItem()).getBlock());
            }
            if (!MinecoloniesAPIProxy.getInstance().getFurnaceRecipes().getSmeltingResult(stack).isEmpty())
            {
                smeltableOres.add(new ItemStorage(stack));
            }
        }
    }

    /**
     * Discover saplings from the vanilla Saplings tag, used for the Forester
     */
    private void discoverSaplings(final ItemStack stack)
    {
        if (stack.is(ItemTags.SAPLINGS) || stack.is(COMMON_MUSHROOMS) || stack.is(ModTags.fungi))
        {
            // c:mushrooms follows #minecraft:mushrooms, which now also holds the shelf mushroom. That one grows out of
            // the side of a log rather than out of the ground: the lumberjack cannot plant it on a stump, so listing
            // it as a species gave his hut a row nobody can act on and a reserved stack nobody ever spends. Everything
            // he can actually plant is ground vegetation.
            if (stack.getItem() instanceof BlockItem blockItem && !(blockItem.getBlock() instanceof VegetationBlock))
            {
                return;
            }
            saplings.add(new ItemStorage(stack, false, true));
        }
    }

    /**
     * Number of times a leaf's loot table is rolled while looking for the sapling it drops.
     */
    private static final int LEAF_DROP_ROLLS = 500;

    /**
     * Work out, up front, which sapling each kind of leaf drops.
     * <p>
     * This table used to fill itself only as trees were felled, from main-thread code as each tree was chosen.
     * The lumberjack's "do not cut this species" list is consulted from the pathfinding thread, which cannot roll loot
     * tables, so it asked this table, got nothing back for a species the colony had never cut, and cut the tree anyway.
     * The setting therefore did nothing at all until one tree of that species had already been felled -- including for
     * the species added by this snapshot.
     * <p>
     * Rolling every leaf's loot table once here costs a few thousand table evaluations at datapack load and makes the
     * setting work from the first tree. Entries already known (the nether wart blocks, or anything restored from the
     * colony's own save) are left alone.
     *
     * @param level the server level to roll the loot tables against.
     */
    private void discoverLeaves(final Level level)
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        // Mangrove leaves drop nothing; the propagule hangs off them as a block of its own.
        leavesToSaplingMap.putIfAbsent(Blocks.MANGROVE_LEAVES, new ItemStorage(new ItemStack(Items.MANGROVE_PROPAGULE), false, true));

        final Vec3 origin = Vec3.atCenterOf(BlockPos.ZERO);
        for (final Block block : BuiltInRegistries.BLOCK)
        {
            final BlockState state = block.defaultBlockState();
            if (leavesToSaplingMap.containsKey(block) || !(state.is(BlockTags.LEAVES) || state.is(ModTags.hugeMushroomBlocks)))
            {
                continue;
            }

            for (int roll = 0; roll < LEAF_DROP_ROLLS; roll++)
            {
                final ItemStack sapling = firstSaplingIn(state.getDrops(new LootParams.Builder(serverLevel)
                                                                          .withParameter(LootContextParams.TOOL, new ItemStack(Items.WOODEN_AXE))
                                                                          .withLuck(100)
                                                                          .withParameter(LootContextParams.ORIGIN, origin)));
                if (!sapling.isEmpty())
                {
                    leavesToSaplingMap.put(block, new ItemStorage(sapling, false, true));
                    break;
                }
            }
        }

        Log.getLogger().info("Finished discovering leaves " + leavesToSaplingMap.size());
    }

    /**
     * @param drops one roll of a leaf's loot table.
     * @return the first sapling or mushroom in it, or an empty stack.
     */
    private static ItemStack firstSaplingIn(final List<ItemStack> drops)
    {
        for (final ItemStack drop : drops)
        {
            if (drop.is(ItemTags.SAPLINGS) || drop.is(COMMON_MUSHROOMS))
            {
                return drop;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * "Discover" associated saplings for fungi; there currently isn't a great way to do this automatically,
     * so it's just hard-coded for now.  (TODO: datapack this in 1.20.4?)
     */
    private void discoverFungi()
    {
        // regular saplings and overworld mushrooms are discovered by loot drops, so will populate this table on
        // their own (though only after the first tree is cut); nether "leaves" don't drop saplings by default
        // though, so we instead use this table to force that.
        leavesToSaplingMap.put(Blocks.NETHER_WART_BLOCK, new ItemStorage(new ItemStack(Items.CRIMSON_FUNGUS)));
        leavesToSaplingMap.put(Blocks.WARPED_WART_BLOCK, new ItemStorage(new ItemStack(Items.WARPED_FUNGUS)));
    }

    /**
     * Create complete list of compost recipes.
     *
     * @param recipeManager recipe manager
     */
    private void discoverCompostRecipes(@NotNull final RecipeManager recipeManager)
    {
        if (compostRecipes.isEmpty())
        {
            // 26.2: RecipeManager#getAllRecipesFor is gone; filter the full recipe collection by type instead.
            discoverCompostRecipes(recipeManager.getRecipes().stream()
                                     .filter(holder -> holder.value().getType() == ModRecipeSerializer.CompostRecipeType.get())
                                     .map(holder -> (RecipeHolder<CompostRecipe>) (RecipeHolder<?>) holder)
                                     .toList());
            Log.getLogger().info("Finished discovering compostables " + compostRecipes.size());
        }
    }

    private void discoverCompostRecipes(@NotNull final List<RecipeHolder<CompostRecipe>> recipes)
    {
        for (final RecipeHolder<CompostRecipe> recipe : recipes)
        {
            for (final Holder<Item> item : recipe.value().getInput().items().toList())
            {
                // there can be duplicates due to overlapping tags.  weakest one wins.
                compostRecipes.merge(item.value(), recipe,
                  (r1, r2) -> r1.value().getStrength() < r2.value().getStrength() ? r1 : r2);
            }
        }
    }

    /**
     * Create complete list of plantable items, from the "minecolonies:florist_flowers" tag, for the Florist.
     */
    private void discoverPlantables(final ItemStack stack)
    {
        if (stack.is(ModTags.floristFlowers))
        {
            if (stack.getItem() instanceof BlockItem)
            {
                plantables.add(new ItemStorage(stack));
            }
        }
    }

    /**
     * Create complete list of fuel items.
     */
    private void discoverFuel(final Level level, final ItemStack stack)
    {
        // 26.2: FurnaceBlockEntity#isFuel is gone; fuel is a per-level FuelValues lookup (Level.java:1107).
        if (level != null && level.fuelValues().isFuel(stack))
        {
            fuel.add(new ItemStorage(stack));
        }
    }

    /**
     * Create complete list of food items.
     */
    private void discoverFood(final ItemStack stack)
    {
        if (ISFOOD.test(stack) || ISCOOKABLE.test(stack))
        {
            food.add(new ItemStorage(stack));
            if (FoodUtils.EDIBLE.test(stack))
            {
                edibles.add(new ItemStorage(stack));
            }
        }
    }

    private static CompoundTag writeLeafSaplingEntryToNBT(@NotNull final HolderLookup.Provider provider, final BlockState state, final ItemStorage storage)
    {
        final CompoundTag compound = NbtUtils.writeBlockState(state);
        compound.put(NbtTagConstants.STACK, Utils.serializeCodecMess(ItemStack.OPTIONAL_CODEC, provider, storage.getItemStack()));
        return compound;
    }

    private static Tuple<BlockState, ItemStorage> readLeafSaplingEntryFromNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        return new Tuple<>(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, compound), new ItemStorage(Utils.deserializeCodecMess(ItemStack.OPTIONAL_CODEC, provider, compound.getCompoundOrEmpty(NbtTagConstants.STACK)), false, true));
    }

    /**
     * Inits compats
     */
    private void discoverModCompat()
    {
        if (FabricLoader.getInstance().isModLoaded("resourcefulbees"))
        {
            Compatibility.beeHiveCompat = new ResourcefulBeesCompat();
        }
        if (FabricLoader.getInstance().isModLoaded("tconstruct"))
        {
            Compatibility.tinkersCompat = new TinkersToolHelper();
            Compatibility.tinkersSlimeCompat = new SlimeTreeCheck();
        }
        if (FabricLoader.getInstance().isModLoaded("dynamictrees"))
        {
            Compatibility.dynamicTreesCompat = new DynamicTreeCompat();
        }
    }

    @Override
    public FurnaceRecipes getFurnaceRecipes()
    {
        return furnaceRecipes;
    }

    @Override
    public int getNumberOfSaplings()
    {
        return saplings.size();
    }

    @Override
    public Optional<DyeColor> getDyeColor(final ItemStack stack)
    {
        // 26.2 has no #minecraft:dyeable item tag; carrying a DYED_COLOR component is the equivalent test.
        if (stack.has(DataComponents.DYED_COLOR))
        {
            final int color = DyedItemColor.getOrDefault(stack, -1);
            if (color != -1)
            {
                final ItemStack undyedStack = stack.copy();
                undyedStack.remove(DataComponents.DYED_COLOR);

                final int dyeId = dyeColorMap.computeIfAbsent(Item.getId(undyedStack.getItem()), id ->
                {
                    final Int2IntMap map = new Int2IntOpenHashMap();
                    for (final DyeColor dye : DyeColor.values())
                    {
                        final ItemStack dyed = DyedItemColor.applyDyes(undyedStack, List.of(dye));
                        if (!dyed.isEmpty())
                        {
                            map.put(DyedItemColor.getOrDefault(dyed, -1), dye.getId());
                        }
                    }
                    return map;
                }).getOrDefault(color, -1);

                return dyeId < 0 ? Optional.empty() : Optional.of(DyeColor.byId(dyeId));
            }
        }

        return Optional.empty();
    }
}
