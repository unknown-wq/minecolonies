package com.minecolonies.core.tileentities;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.google.common.collect.ImmutableList;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.entity.block.IMateriallyTexturedBlockEntity;
import com.ldtteam.structurize.blueprints.v1.DataFixerUtils;
import com.ldtteam.structurize.blueprints.v1.DataVersion;
import com.minecolonies.api.blocks.AbstractBlockMinecoloniesRack;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.blocks.types.RackType;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.inventory.api.CombinedItemHandler;
import com.minecolonies.api.inventory.container.ContainerRack;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.client.util.ClientMainThread;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import com.minecolonies.core.util.ValueIoUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;

import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.minecolonies.api.inventory.api.IItemHandler;
import com.minecolonies.api.inventory.api.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.Constants.*;
import static com.minecolonies.api.util.constant.NbtTagConstants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.RACK;

/**
 * Tile entity for the warehouse shelves.
 */
public class TileEntityRack extends AbstractTileEntityRack implements IMateriallyTexturedBlockEntity, Clearable, ExtendedMenuProvider<RegistryFriendlyByteBuf>
{
    /**
     * All Racks current version id
     */
    private static final byte VERSION = 2;

    /**
     * The racks version
     */
    private byte version = 0;

    /**
     * The content of the chest.
     */
    private final Object2IntMap<ItemStorage> content = new Object2IntOpenHashMap();

    /**
     * Size multiplier of the inventory. 0 = default value. 1 = 1*9 additional slots, and so on.
     */
    private int size = 0;

    /**
     * Amount of free slots
     */
    private int freeSlots = 0;

    /**
     * Last optional we created.
     */
    private IItemHandler lastItemHandlerCap;

    /**
     * The block state {@link #lastItemHandlerCap} was built from, or null if there is no cached handler. A double
     * rack's handler holds the twin's inventory object, so the cache is only good for as long as the pairing the
     * state describes.
     */
    private BlockState lastItemHandlerCapState;

    /**
     * Static texture mappings: the sprite each display slot of the rack model carries, and which Domum Ornamentum
     * swaps for the sprite of whatever the slot is showing.
     * <p>
     * PORT-NOTE(26.2): these were the vanilla placeholders the model shipped with -- {@code block/bricks},
     * {@code block/sand} and so on -- and a slot with nothing to show was mapped to {@link Blocks#AIR} to make it
     * disappear. That worked because Domum Ornamentum 1.21.1 <em>erased</em> quads whose replacement block had no
     * geometry (RetexturedBakedModelBuilder#withOut / #needsErasure). Its 26.2 rewrite has no erasure left: a
     * replacement with no quads falls back to the block's particle sprite, and {@code minecraft:block/air}'s model
     * declares {@code "particle": "minecraft:missingno"} -- so every empty slot painted itself with the missing
     * texture, which is the purple that showed up on every rack in the world.
     * <p>
     * The library is a prebuilt dependency here, so the fix is on this side: the placeholders are now sixteen
     * fully transparent mod sprites (one image stitched under sixteen names in the blocks atlas -- distinct names
     * are required because the swap is keyed on the sprite a quad currently uses). An empty slot is left out of the
     * texture map entirely, keeps its transparent placeholder, and is invisible again. 26.2 derives a quad's chunk
     * layer from its sprite's own pixels (FaceBakery#computeMaterialTransparency), so a fully transparent sprite is
     * cutout and draws nothing.
     */
    private static final List<Identifier> textureMapping = ImmutableList.<Identifier>builder()
        .add(rackSprite("slot0"))
        .add(rackSprite("slot1"))
        .add(rackSprite("slot2"))
        .add(rackSprite("slot3"))
        .add(rackSprite("slot4"))
        .add(rackSprite("slot5"))
        .add(rackSprite("slot6"))
        .add(rackSprite("slot7")).build();

    /**
     * The flat plane in front of each display slot, used for the items that do not render as a full block. Same
     * story as {@link #textureMapping}.
     */
    private static final List<Identifier> secondarytextureMapping = ImmutableList.<Identifier>builder()
                                                                            .add(rackSprite("surface0"))
                                                                            .add(rackSprite("surface1"))
                                                                            .add(rackSprite("surface2"))
                                                                            .add(rackSprite("surface3"))
                                                                            .add(rackSprite("surface4"))
                                                                            .add(rackSprite("surface5"))
                                                                            .add(rackSprite("surface6"))
                                                                            .add(rackSprite("surface7"))
                                                                            .build();

    /**
     * @param name the sprite name under {@code minecolonies:block/rack/}.
     * @return the sprite id, which has to match the texture slot in the rack model files.
     */
    private static Identifier rackSprite(final String name)
    {
        return Identifier.fromNamespaceAndPath(MOD_ID, "block/rack/" + name);
    }

    /**
     * Cached resmap.
     * <p>
     * 26.2/Fabric: volatile because {@link #getTextureData()} is read from chunk meshing threads (vanilla's
     * section builder and Sodium's chunk workers both bake off the main thread) while the refresh that writes it
     * runs on the client main thread -- without it a worker can bake from a stale or half-published map.
     */
    private volatile MaterialTextureData textureDataCache = new MaterialTextureData(Map.of());

    /**
     * If we did a double check after startup.
     * <p>
     * 26.2/Fabric: an atomic, because the first {@link #getTextureData()} that flips it can come from any number
     * of meshing threads at once and the refresh it triggers must be requested exactly once per rack.
     */
    private final AtomicBoolean checkedAfterStartup = new AtomicBoolean(false);

    /**
     * Create a new rack.
     * @param type the specific block entity type.
     * @param pos the position.
     * @param state its state.
     */
    public TileEntityRack(final BlockEntityType<? extends TileEntityRack> type, final BlockPos pos, final BlockState state)
    {
        super(type, pos, state);
        this.freeSlots = inventory.getSlots();
    }

    /**
     * Create a rack with a specific inventory size.
     * @param type the specific block entity type.
     * @param pos the position.
     * @param state its state.
     * @param size the ack size.
     */
    public TileEntityRack(final BlockEntityType<? extends TileEntityRack> type, final BlockPos pos, final BlockState state, final int size)
    {
        super(type, pos, state, size);
        this.size = ((size - DEFAULT_SIZE) / SLOT_PER_LINE);
        this.freeSlots = inventory.getSlots();
    }

    /**
     * Create a new default rack.
     * @param pos the position.
     * @param state its state.
     */
    public TileEntityRack(final BlockPos pos, final BlockState state)
    {
        super(MinecoloniesTileEntities.RACK.get(), pos, state);
    }

    @Override
    public void setInWarehouse(final Boolean isInWarehouse)
    {
        this.inWarehouse = isInWarehouse;
    }

    @Override
    public int getFreeSlots()
    {
        return freeSlots;
    }

    @Override
    public boolean hasItemStack(final ItemStack stack, final int count, final boolean ignoreDamageValue)
    {
        final ItemStorage checkItem = new ItemStorage(stack, ignoreDamageValue);

        return content.getOrDefault(checkItem, 0) >= count;
    }

    @Override
    public boolean hasItemStorage(final ItemStorage storage, final int count)
    {
        return content.getOrDefault(storage, 0) >= count;
    }

    @Override
    public int getCount(final ItemStack stack, final boolean ignoreDamageValue, final boolean ignoreNBT)
    {
        final ItemStorage checkItem = new ItemStorage(stack, ignoreDamageValue, ignoreNBT);
        return getCount(checkItem);
    }

    @Override
    protected void updateBlockState()
    {

    }

    @Override
    public int getCount(final ItemStorage storage)
    {
        if (storage.ignoreDamageValue() || storage.ignoreNBT())
        {
            if (!content.containsKey(storage))
            {
                return 0;
            }

            int count = 0;
            for (final Map.Entry<ItemStorage, Integer> contentStorage : content.entrySet())
            {
                if (contentStorage.getKey().equals(storage))
                {
                    count += contentStorage.getValue();
                }
            }
            return count;
        }

        return content.getOrDefault(storage, 0);
    }

    @Override
    public boolean hasItemStack(@NotNull final Predicate<ItemStack> itemStackSelectionPredicate)
    {
        for (final Map.Entry<ItemStorage, Integer> entry : content.entrySet())
        {
            if (itemStackSelectionPredicate.test(entry.getKey().getItemStack()))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasSimilarStack(@NotNull final ItemStack stack)
    {
        final ItemStorage checkItem = new ItemStorage(stack, true, true);
        if (content.containsKey(checkItem))
        {
            return true;
        }

        for (final ItemStorage storage : content.keySet())
        {
            if (IColonyManager.getInstance().getCompatibilityManager().getCreativeTab(checkItem) == IColonyManager.getInstance().getCompatibilityManager().getCreativeTab(storage))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Gets the content of the Rack
     *
     * @return the map of content.
     */
    public Map<ItemStorage, Integer> getAllContent()
    {
        return content;
    }

    @Override
    public void upgradeRackSize()
    {
        ++size;
        final RackInventory tempInventory = new RackInventory(DEFAULT_SIZE + size * SLOT_PER_LINE);
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            tempInventory.setStackInSlot(slot, inventory.getStackInSlot(slot));
        }

        inventory = tempInventory;

        // Every setStackInSlot in the loop above fired onContentsChanged -> updateItemStorage() -> updateContent(),
        // and each of those ran against the *old*, still-assigned inventory -- so freeSlots ended the loop holding the
        // pre-upgrade figure, and nothing recomputed it after the assignment. Measured on a full 51-rack warehouse:
        // 2727 slots of which 450 were genuinely empty, while getFreeSlots() read 0 on all 50 upgraded racks;
        // TileEntityWareHouse#getRackForStack therefore returned null and a courier carrying five stacks stored
        // nothing. The rack healed only when something was extracted from it, or on the next chunk load. That is the
        // exact moment the player has just paid an emerald block for the space, so recompute here.
        updateItemStorage();

        final BlockState state = level.getBlockState(worldPosition);
        level.sendBlockUpdated(worldPosition, state, state, 0x03);
        invalidateCap();
    }

    @Override
    public int getItemCount(final Predicate<ItemStack> predicate)
    {
        int matched = 0;
        for (final Map.Entry<ItemStorage, Integer> entry : content.entrySet())
        {
            if (predicate.test(entry.getKey().getItemStack()))
            {
                matched += entry.getValue();
            }
        }
        return matched;
    }

    @Override
    public void updateItemStorage()
    {
        if (level != null && !level.isClientSide())
        {
            final boolean beforeEmpty = content.isEmpty();
            updateContent();
            if (getBlockState().getBlock() == ModBlocks.blockRack)
            {
                boolean afterEmpty = content.isEmpty();
                @Nullable final BlockEntity potentialNeighbor = getOtherChest();
                if (potentialNeighbor instanceof TileEntityRack && !((TileEntityRack) potentialNeighbor).isEmpty())
                {
                    afterEmpty = false;
                }

                if ((beforeEmpty && !afterEmpty) || (!beforeEmpty && afterEmpty))
                {
                    level.setBlockAndUpdate(getBlockPos(),
                      getBlockState().setValue(AbstractBlockMinecoloniesRack.VARIANT,
                        getBlockState().getValue(AbstractBlockMinecoloniesRack.VARIANT).getInvBasedVariant(afterEmpty)));


                    if (potentialNeighbor != null)
                    {
                        level.setBlockAndUpdate(potentialNeighbor.getBlockPos(),
                          potentialNeighbor.getBlockState()
                            .setValue(AbstractBlockMinecoloniesRack.VARIANT,
                              potentialNeighbor.getBlockState().getValue(AbstractBlockMinecoloniesRack.VARIANT).getInvBasedVariant(afterEmpty)));
                    }
                }
            }
            setChanged();
        }
    }

    /**
     * Just do the content update.
     */
    private void updateContent()
    {
        content.clear();
        freeSlots = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ItemStack stack = inventory.getStackInSlot(slot);

            if (ItemStackUtils.isEmpty(stack))
            {
                freeSlots++;
                continue;
            }

            final ItemStorage storage = new ItemStorage(stack.copy());
            int amount = ItemStackUtils.getSize(stack);
            if (content.containsKey(storage))
            {
                amount += content.remove(storage);
            }
            content.put(storage, amount);
        }
    }

    @Override
    public AbstractTileEntityRack getOtherChest()
    {
        if (getBlockState().getBlock() != ModBlocks.blockRack)
        {
            return null;
        }

        final RackType type = getBlockState().getValue(AbstractBlockMinecoloniesRack.VARIANT);
        if (!type.isDoubleVariant())
        {
            return null;
        }

        final BlockEntity tileEntity = level.getBlockEntity(worldPosition.relative(getBlockState().getValue(AbstractBlockMinecoloniesRack.FACING)));
        if (tileEntity instanceof TileEntityRack && !(tileEntity instanceof AbstractTileEntityColonyBuilding))
        {
            return (AbstractTileEntityRack) tileEntity;
        }

        return null;
    }

    @Override
    public ItemStackHandler createInventory(final int slots)
    {
        return new RackInventory(slots);
    }

    @Override
    public boolean isEmpty()
    {
        return content.isEmpty();
    }

    // 26.2: BlockEntity persistence moved from (CompoundTag, HolderLookup.Provider) to ValueInput/ValueOutput
    // (/opt/mc-src/net/minecraft/world/level/block/entity/BlockEntity.java:97,109). The key names and the slot
    // ordering are unchanged, so the on-disk layout still matches 1.21.1; only the access API differs.
    @Override
    public void loadAdditional(@NotNull final ValueInput input)
    {
        super.loadAdditional(input);
        final Optional<Integer> storedSize = input.getInt(TAG_SIZE);
        if (storedSize.isPresent())
        {
            size = storedSize.get();
            inventory = createInventory(DEFAULT_SIZE + size * SLOT_PER_LINE);
        }

        // The slot list is still the plain list of stack compounds it has always been, so it is read as raw tags: the
        // pre-1.20.2 entries below have to reach the data fixer before any codec sees them. The slot bound is a
        // port-added guard -- the list length always equals the inventory the size above just built, and truncating
        // beats the IndexOutOfBounds the 1.21.1 loop would have thrown on a hand-edited save.
        final HolderLookup.Provider lookup = input.lookup();
        final List<CompoundTag> inventoryTagList = input.read(TAG_INVENTORY, CompoundTag.CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < inventoryTagList.size() && i < inventory.getSlots(); i++)
        {
            final CompoundTag slotTag = inventoryTagList.get(i);
            if (slotTag.contains(TAG_EMPTY))
            {
                continue;
            }

            final CompoundTag stackTag = slotTag.contains("Count")
                                           ? DataFixerUtils.runDataFixer(slotTag, References.ITEM_STACK, DataVersion.v1_20_1)
                                           : slotTag;
            inventory.setStackInSlot(i, parseStack(lookup, stackTag));
        }

        updateContent();

        this.inWarehouse = input.getBooleanOr(TAG_IN_WAREHOUSE, false);
        if (input.child(TAG_POS).isPresent())
        {
            this.buildingPos = ValueIoUtils.readPos(input, TAG_POS);
        }
        version = input.getByteOr(TAG_VERSION, (byte) 0);

        invalidateCap();

        if (level != null && level.isClientSide())
        {
            refreshTextureCache();
        }
    }

    // PORT-NOTE(26.2): the 1.21.1 body was `inventoryTagList.add(inventory.getStackInSlot(slot).saveOptional(provider))`
    // into a ListTag. ItemStack#saveOptional is gone, so the list is written slot by slot through the ValueOutput:
    // ItemStack.MAP_CODEC merged into the child compound for a filled slot, nothing at all for an empty one, which
    // leaves the empty compound saveOptional used to produce. Same list, same order, same bytes as the 0.0.15 jar.
    // Two reasons for the per-slot form over `store(TAG_INVENTORY, ItemStack.OPTIONAL_CODEC.listOf(), ...)`: a slot
    // that fails to encode stays an empty compound instead of vanishing and shifting every slot after it, and each
    // child inherits the ops of the ValueOutput -- so the registries are the caller's. That last part matters on the
    // one save path where this block entity has no level of its own: Structurize's
    // PlacementHandlers#handleTileEntityPlacement round-trips a detached rack through saveWithFullMetadata when a
    // blueprint places it, and a level-derived lookup would have been RegistryAccess.EMPTY there, silently dropping
    // every stack whose components need a dynamic registry.
    @Override
    public void saveAdditional(@NotNull final ValueOutput output)
    {
        super.saveAdditional(output);
        output.putInt(TAG_SIZE, size);
        final ValueOutput.ValueOutputList inventoryTagList = output.childrenList(TAG_INVENTORY);
        for (int slot = 0; slot < inventory.getSlots(); slot++)
        {
            final ValueOutput slotOutput = inventoryTagList.addChild();
            final ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty())
            {
                slotOutput.store(ItemStack.MAP_CODEC, stack);
            }
        }
        output.putBoolean(TAG_IN_WAREHOUSE, inWarehouse);
        ValueIoUtils.writePos(output, TAG_POS, buildingPos);
        output.putByte(TAG_VERSION, version);
    }

    /**
     * 26.2 replacement for {@code ItemStack#parseOptional(HolderLookup.Provider, CompoundTag)}, which is gone:
     * decode through {@link ItemStack#OPTIONAL_CODEC} with registry-aware ops. An empty compound is an empty
     * stack, and so is anything that fails to decode -- one unreadable slot must not cost the whole rack.
     *
     * @param lookup the registry lookup.
     * @param tag    the tag to decode.
     * @return the stack.
     */
    @NotNull
    private static ItemStack parseStack(@NotNull final HolderLookup.Provider lookup, @NotNull final CompoundTag tag)
    {
        return ItemStack.OPTIONAL_CODEC.parse(lookup.createSerializationContext(NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // PORT-NOTE(26.2): 1.21.1 returned saveWithId(provider); the tag-returning saveWithId is gone and
    // saveWithFullMetadata is the nearest thing left (/opt/mc-src/net/minecraft/world/level/block/entity/BlockEntity.java:111).
    // It adds x/y/z on top of the type id, which the receiving side ignores -- ClientPacketListener resolves the block
    // entity from the packet's own position before feeding the tag to loadWithComponents.
    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull final HolderLookup.Provider provider)
    {
        return this.saveWithFullMetadata(provider);
    }

    // 26.2 removed BlockEntity#onDataPacket and #handleUpdateTag: the client applies the update tag through
    // loadWithComponents/loadAdditional itself, so the two forwarding overrides are gone.

    @Override
    @Nullable
    public IItemHandler getItemHandlerCap(Direction direction)
    {
        if (version != VERSION)
        {
            version = VERSION;
        }

        if (remove)
        {
            lastItemHandlerCap = new RackInventory(0);
            lastItemHandlerCapState = getBlockState();
            return lastItemHandlerCap;
        }

        // The cached handler is only valid for the block state it was built from. A double rack's handler holds the
        // twin's inventory object, and breaking either half rewrites both halves' VARIANT (see
        // BlockMinecoloniesRack#updateShape) -- so a state that is still the one we cached for means the pairing is
        // still the one we cached. Without this check a rack whose twin was broken keeps handing out a handler with
        // the dead half's slots in it, and anything a worker puts there is inserted into a detached inventory and
        // lost. Block states are interned, so the comparison is an identity check.
        if (lastItemHandlerCap != null && lastItemHandlerCapState == getBlockState())
        {
            return lastItemHandlerCap;
        }
        lastItemHandlerCapState = getBlockState();

        // getOtherChest() is null unless this is the rack block, in a double variant, with a rack on the facing side --
        // the three cases that used to be spelled out here one by one, each ending in the same single-inventory handler.
        final AbstractTileEntityRack other = getOtherChest();
        if (other == null)
        {
            lastItemHandlerCap = new CombinedItemHandler(RACK, getInventory());
        }
        else if (getBlockState().getValue(AbstractBlockMinecoloniesRack.VARIANT) != RackType.EMPTY)
        {
            lastItemHandlerCap = new CombinedItemHandler(RACK, getInventory(), other.getInventory());
        }
        else
        {
            lastItemHandlerCap = new CombinedItemHandler(RACK, other.getInventory(), getInventory());
        }

        return lastItemHandlerCap;
    }


    @Override
    public int getUpgradeSize()
    {
        return size;
    }

    @Override
    public void setChanged()
    {
        if (level != null)
        {
            WorldUtil.markChunkDirty(level, worldPosition);
            super.setChanged();
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(final int id, @NotNull final Inventory inv, @NotNull final Player player)
    {
        refreshTextureCache();
        return new ContainerRack(id, inv, getBlockPos(), getOtherChest() == null ? BlockPos.ZERO : getOtherChest().getBlockPos());
    }

    /**
     * 26.2/Fabric: NeoForge's {@code ServerPlayer#openMenu(MenuProvider, Consumer<FriendlyByteBuf>)} is replaced
     * by {@code fabric-menu-api-v1}'s {@link ExtendedMenuProvider}: the extra screen-opening data is produced
     * here instead of being written at the call site (see {@code ModContainerInitializers}). What is written has
     * to match {@link ContainerRack#fromFriendlyByteBuf}: this rack's position, then its twin's.
     *
     * @param player the player the screen is being opened for.
     * @return the buffer the client-side container factory reads.
     */
    @NotNull
    @Override
    public RegistryFriendlyByteBuf getScreenOpeningData(@NotNull final ServerPlayer player)
    {
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
        buf.writeBlockPos(getBlockPos());
        buf.writeBlockPos(getOtherChest() == null ? BlockPos.ZERO : getOtherChest().getBlockPos());
        return buf;
    }

    @NotNull
    @Override
    public Component getDisplayName()
    {
        return Component.literal("Rack");
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        invalidateCap();
    }

    /**
     * Invalidates the cap
     */
    private void invalidateCap()
    {
        // 26.2/Fabric: capabilities are not ported (contract C4), so there is nothing to invalidate upstream;
        // dropping the cached handler is all that is left of NeoForge's invalidateCapabilities().
        lastItemHandlerCap = null;
        lastItemHandlerCapState = null;
    }

    @Override
    public void updateTextureDataWith(final MaterialTextureData materialTextureData)
    {
        // noop
    }

    /**
     * Refresh the texture mapping.
     * <p>
     * 26.2/Fabric: reads the neighbouring rack out of the level and ends in a block update, so on a client this
     * belongs on the main thread and nowhere else. The three callers keep to that: the menu and the lazy
     * first-use path when there is no client involved run on the server thread, the client's block entity update
     * packet is applied on the main thread, and the lazy first-use path on a client goes through
     * {@link #refreshTextureCacheOnMainThread()}.
     */
    private void refreshTextureCache()
    {
        final Map<Identifier, Block> resMap = new HashMap<>();
        final int displayPerSlots = this.getInventory().getSlots() / 4;
        int index = 0;
        boolean update = false;
        boolean alreadyAddedItem = false;

        final HashMap<ItemStorage, Integer> mapCopy = new HashMap<>(content);
        if (this.getOtherChest() instanceof TileEntityRack neighborRack)
        {
            for (final Map.Entry<ItemStorage, Integer> entry : neighborRack.content.entrySet())
            {
                int value = entry.getValue() + mapCopy.getOrDefault(entry.getKey(), 0);
                mapCopy.put(entry.getKey(), value);
            }
        }
        final List<Map.Entry<ItemStorage, Integer>> list = mapCopy.entrySet().stream().sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue())).toList();

        final Queue<Block> extraBlockQueue = new ArrayDeque<>();
        final Queue<Block> itemQueue = new ArrayDeque<>();
        for (final Map.Entry<ItemStorage, Integer> entry : list)
        {
            // Need more solid checks!
            if (index < textureMapping.size())
            {
                Block block = Blocks.BARREL;
                boolean isBlockItem = false;
                if (entry.getKey().getItemStack().getItem() instanceof BlockItem blockItem)
                {
                    block = blockItem.getBlock();
                    isBlockItem = true;
                }

                int displayRows = (int) Math.ceil((Math.max(1.0, (double) entry.getValue() / entry.getKey().getItemStack().getMaxStackSize())) / displayPerSlots);
                if (displayRows > 1)
                {
                    for (int i = 0; i < displayRows - 1; i++)
                    {
                        if (isBlockItem)
                        {
                            extraBlockQueue.add(block);
                        }
                        else
                        {
                            itemQueue.add(block);
                        }
                    }
                }

                if (!isBlockItem)
                {
                    if (alreadyAddedItem)
                    {
                        itemQueue.add(block);
                        continue;
                    }
                    else
                    {
                        alreadyAddedItem = true;
                    }
                }

                if (entry.getValue() < 16 && !extraBlockQueue.isEmpty())
                {
                    block = extraBlockQueue.poll();
                }

                update |= assignTexture(resMap, index, block);
                index++;
            }
            else
            {
                break;
            }
        }

        extraBlockQueue.addAll(itemQueue);

        for (int i = index; i < textureMapping.size(); i++)
        {
            Block block = Blocks.AIR;
            if (!extraBlockQueue.isEmpty())
            {
                block = extraBlockQueue.poll();
            }

            update |= assignTexture(resMap, i, block);
        }

        if (update)
        {
            this.textureDataCache = new MaterialTextureData(resMap);
            // 26.2/Fabric: NeoForge's ModelData channel is gone (see Domum Ornamentum ModProperties); the model
            // reads getTextureData() out of the level, so a block update is the whole refresh.
            if (level != null)
            {
                level.sendBlockUpdated(getBlockPos(), Blocks.AIR.defaultBlockState(), getBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Assign one display slot of the rack model: the block goes on the primary texture, or -- when it does not render
     * as a full block -- on the flat plane in front of it, with a barrel standing in for it on the primary.
     * <p>
     * {@link Blocks#AIR} means "this display slot shows nothing", and nothing is expressed by leaving the slot out
     * of the map: the model then keeps the transparent placeholder it was baked with. See {@link #textureMapping}
     * for why it is not mapped to air any more. Only the trailing fill loop can hand in air; a stack always resolves
     * to a block item's block or to the fallback barrel.
     *
     * @param resMap the texture map being built.
     * @param index  the display slot to fill.
     * @param block  the block to show in it.
     * @return true if this slot differs from the cached texture data, i.e. the model has to be rebuilt.
     */
    private boolean assignTexture(@NotNull final Map<Identifier, Block> resMap, final int index, Block block)
    {
        final Identifier secondaryResLoc = secondarytextureMapping.get(index);
        if (block != Blocks.AIR && !block.defaultBlockState().isSolidRender())
        {
            resMap.put(secondaryResLoc, block);
            block = Blocks.BARREL;
        }

        final Identifier resLoc = textureMapping.get(index);
        if (block != Blocks.AIR)
        {
            resMap.put(resLoc, block);
        }

        final Map<Identifier, Block> cached =
          this.textureDataCache == null ? Map.of() : this.textureDataCache.getTexturedComponents();
        return !Objects.equals(cached.get(resLoc), resMap.get(resLoc))
                 || !Objects.equals(cached.get(secondaryResLoc), resMap.get(secondaryResLoc));
    }

    // 26.2/Fabric: BlockEntity#getModelData and NeoForge's ModelData/ModelProperty do not exist. Domum
    // Ornamentum's model asks the block entity for getTextureData() directly, so the lazy first-use refresh
    // that used to live in getModelData() moved into getTextureData().
    //
    // PORT-NOTE(26.2): that move put the refresh on whatever thread bakes the model, and in 26.2 that is a chunk
    // meshing worker, not the main thread. refreshTextureCache() ends in level.sendBlockUpdated(), which reaches
    // the renderer: vanilla only marks the section dirty and never checks, but Sodium asserts the main render
    // thread and kills the chunk build with "Tried to access render state from outside the main render thread"
    // (issue #24). getOtherChest() -> level.getBlockEntity() in the same refresh is the same violation, quieter.
    // So off the main thread the model gets the cache as it stands and the refresh is queued: when it then finds
    // something to change it issues its own block update, the section is rebuilt, and the next bake reads the
    // fresh map. That costs at most one bake of a rack without its shelf items. On the main thread it still runs
    // inline, which keeps blueprint previews -- baked on the main thread against Structurize's fake level, whose
    // sendBlockUpdated is a noop -- showing their contents in the very first frame, as they do today.
    @Override
    public @NotNull MaterialTextureData getTextureData()
    {
        if (level != null && checkedAfterStartup.compareAndSet(false, true))
        {
            if (level.isClientSide())
            {
                // Only reached on a client, so it is the only place allowed to touch the client-only hop.
                ClientMainThread.runOrSchedule(this::refreshTextureCacheOnMainThread);
            }
            else
            {
                refreshTextureCache();
            }
        }

        return textureDataCache;
    }

    /**
     * 26.2/Fabric: the client-side entry point of the lazy first-use refresh, always on the main thread.
     * <p>
     * The bake that asked for the texture data can be several frames old by the time this runs, so the rack has
     * to still be the live block entity of its position -- a chunk that unloaded in between leaves this instance
     * behind with a level it may no longer send block updates to.
     */
    private void refreshTextureCacheOnMainThread()
    {
        if (isRemoved() || level == null || level.getBlockEntity(worldPosition) != this)
        {
            return;
        }

        refreshTextureCache();
    }

    @Override
    public void clearContent()
    {
        for (int i = 0; i < this.getInventory().getSlots(); i++)
        {
            this.getInventory().setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
