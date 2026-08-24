package com.ldtteam.structurize.api;

import com.ldtteam.common.fakelevel.SingleBlockFakeLevel.SidedSingleBlockFakeLevel;
import com.ldtteam.structurize.compat.itemhandler.IItemHandler;
import com.ldtteam.structurize.compat.itemhandler.ItemHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Utility methods for the inventories.
 */
public final class ItemStackUtils
{
    public static final SidedSingleBlockFakeLevel ITEM_HANDLER_FAKE_LEVEL = new SidedSingleBlockFakeLevel();

    /**
     * Private constructor to hide the implicit one.
     */
    private ItemStackUtils()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Get itemStack of tileEntityData. Retrieve the data from the tileEntity. Including recursive content, eg. shulkers
     *
     * @param compound the tileEntity stored in a compound.
     * @param state the block.
     * @param level real vanilla instance for fakeLevel
     * @return the list of itemstacks.
     * @see #getListOfStackForEntity(Entity, BlockPos)
     */
    public static List<ItemStack> getItemStacksOfTileEntity(final CompoundTag compound, final BlockState state, final Level level)
    {
        if (compound == null)
        {
            return List.of();
        }

        final BlockPos blockpos = new BlockPos(compound.getIntOr("x", 0), compound.getIntOr("y", 0), compound.getIntOr("z", 0));
        final BlockEntity tileEntity = BlockEntity.loadStatic(blockpos, state, compound, level.registryAccess());
        if (tileEntity == null)
        {
            return List.of();
        }

        return ITEM_HANDLER_FAKE_LEVEL.get(level).useFakeLevelContext(state, tileEntity, level, fakeLevel -> {
            final List<ItemStack> items = new ArrayList<>();
            getItemHandlersFromProvider(tileEntity, blockpos, state).forEach(itemHandler -> deepExtractItemHandler(itemHandler, items::add));
            return items;
        });
    }

    /**
     * @param handler root itemHandler to extract
     * @param sink where to put content of all found itemStacks, incl. recursive contents
     */
    public static void deepExtractItemHandler(@Nullable final IItemHandler handler, final Consumer<ItemStack> sink)
    {
        if (handler == null)
        {
            return;
        }

        for (int slot = 0; slot < handler.getSlots(); slot++)
        {
            final ItemStack stack = handler.getStackInSlot(slot).copy();
            if (!ItemStackUtils.isEmpty(stack))
            {
                sink.accept(stack);
                deepExtractItemHandler(ItemHandlers.ofStack(stack), sink);
            }
        }
    }

    /**
     * Method to get sensible item handlers from blockEntity. Tries to provide whole deduplicated content. However this assumption is
     * weak. There still might be content (in returned set) that is not present at all or duplicated.
     *
     * @param provider The provider to get the IItemHandlers from.
     * @return A list with all the unique IItemHandlers a provider has.
     */
    public static Set<IItemHandler> getItemHandlersFromProvider(@Nullable final BlockEntity provider, final BlockPos pos, final BlockState state)
    {
        // TODO(port-26.2): DEGRADED — NeoForge capability lookups replaced by the vanilla Container view.
        // Original:
        /*
         *  final IItemHandler unsidedItemHandler = provider.getLevel().getCapability(ItemHandler.BLOCK, pos, state, provider, null);
         *  if (unsidedItemHandler != null) { return Set.of(unsidedItemHandler); }
         *  final Set<IItemHandler> handlerSet = new HashSet<>();
         *  for (final Direction side : Direction.values())
         *  {
         *      final IItemHandler cap = provider.getLevel().getCapability(ItemHandler.BLOCK, pos, state, provider, side);
         *      if (cap != null) { handlerSet.add(cap); }
         *  }
         *  return handlerSet;
         */
        final IItemHandler handler = ItemHandlers.of(provider);
        return handler == null ? Set.of() : Set.of(handler);
    }

    /**
     * Wrapper method to check if a stack is empty.
     * Used for easy updating to 1.11.
     *
     * @param stack The stack to check.
     * @return True when the stack is empty, false when not.
     */
    public static boolean isEmpty(@Nullable final ItemStack stack)
    {
        return stack == null || stack.isEmpty() || stack == ItemStack.EMPTY || stack.getCount() <= 0;
    }

    /**
     * get the size of the stack.
     * This is for compatibility between 1.10 and 1.11
     *
     * @param stack to get the size from
     * @return the size of the stack
     */
    public static int getSize(final ItemStack stack)
    {
        if (ItemStackUtils.isEmpty(stack))
        {
            return 0;
        }

        return stack.getCount();
    }

    /**
     * @deprecated {@link #getListOfStackForEntity(Entity)}
     */
    @Deprecated(forRemoval = true, since = "1.21.1")
    public static List<ItemStack> getListOfStackForEntity(final Entity entity, final BlockPos pos)
    {
        return getListOfStackForEntity(entity);
    }

    /**
     * Get the list of required resources for entities + entity spawning item. Same implementation as blockEntity logic. Including recursive content, eg. shulkers 
     *
     * @param entity the entity object.
     * @return a list of stacks.
     * @see #getItemStacksOfTileEntity(BlockEntity)
     */
    public static List<ItemStack> getListOfStackForEntity(final Entity entity)
    {
        if (entity == null)
        {
            return List.of();
        }

        final List<ItemStack> request = new ArrayList<>();

        // process entity itself
        final ItemStack spawnItem = getEntitySpawningItem(entity);
        if (spawnItem != null && !(spawnItem.getItem() instanceof SpawnEggItem))
        {
            request.add(spawnItem);
        }

        // process entity contents
        request.addAll(getItemStacksOfEntity(entity));

        return request.stream().filter(stack -> !stack.isEmpty()).toList();
    }

    /**
     * Get the list of required resources for entities. Same implementation as blockEntity logic. Including recursive content, eg. shulkers 
     *
     * @param entity the entity object.
     * @return a list of stacks.
     * @see #getItemStacksOfTileEntity(BlockEntity)
     */
    public static List<ItemStack> getItemStacksOfEntity(final Entity entity)
    {
        if (entity == null)
        {
            return List.of();
        }

        final List<ItemStack> entityContent = new ArrayList<>();

        // TODO(port-26.2): DEGRADED — NeoForge Capabilities.ItemHandler.ENTITY / ENTITY_AUTOMATION lookups
        // (incl. the per-side loop) have no Fabric equivalent; only vanilla Container entities are seen.
        final IItemHandler itemHandler = ItemHandlers.of(entity);

        if (itemHandler != null)
        {
            deepExtractItemHandler(itemHandler, entityContent::add);
        }
        // some vanilla entities "have inventory" but are not a Container
        else if (entity instanceof final ItemFrame itemFrame)
        {
            final ItemStack stack = itemFrame.getItem();
            entityContent.add(stack);
            deepExtractItemHandler(ItemHandlers.ofStack(stack), entityContent::add);
        }
        else if (entity instanceof final ItemEntity itemEntity)
        {
            final ItemStack stack = itemEntity.getItem();
            entityContent.add(stack);
            deepExtractItemHandler(ItemHandlers.ofStack(stack), entityContent::add);
        }

        return entityContent;
    }

    /**
     * @return item that should spawn given entity
     */
    @Nullable
    public static ItemStack getEntitySpawningItem(final Entity entity)
    {
        if (entity instanceof final ItemFrame itemFrame)
        {
            // 26.2: ItemFrame#getFrameItemStack is protected and the mod has no mixins. ItemFrame#getPickResult
            // returns the framed stack on a filled frame, so it only names the frame itself while it is empty;
            // the framed stack is requested separately by getItemStacksOfEntity.
            if (itemFrame.getItem().isEmpty())
            {
                return itemFrame.getPickResult();
            }
            return new ItemStack(itemFrame instanceof GlowItemFrame ? Items.GLOW_ITEM_FRAME : Items.ITEM_FRAME);
        }
        // 26.2: Entity#getPickedResult(HitResult) is a NeoForge patch; vanilla has Entity#getPickResult()
        // (/opt/mc-src/net/minecraft/world/entity/Entity.java:3852)
        return entity.getPickResult();
    }

    /**
     * Method to compare to stacks, ignoring their stacksize.
     *
     * @param itemStack1 The left stack to compare.
     * @param itemStack2 The right stack to compare.
     * @return True when they are equal except the stacksize, false when not.
     */
    public static boolean compareItemStacksIgnoreStackSize(final ItemStack itemStack1, final ItemStack itemStack2)
    {
        return compareItemStacksIgnoreStackSize(itemStack1, itemStack2, true, true);
    }

    /**
     * Method to compare to stacks, ignoring their stacksize.
     *
     * @param itemStack1  The left stack to compare.
     * @param itemStack2  The right stack to compare.
     * @param matchDamage Set to true to match damage data.
     * @param matchNBT    Set to true to match nbt
     * @return True when they are equal except the stacksize, false when not.
     */
    public static boolean compareItemStacksIgnoreStackSize(final ItemStack itemStack1, final ItemStack itemStack2, final boolean matchDamage, final boolean matchNBT)
    {
        return compareItemStacksIgnoreStackSize(itemStack1, itemStack2, matchDamage, matchNBT, false);
    }

    /**
     * Method to compare to stacks, ignoring their stacksize.
     *
     * @param itemStack1  The left stack to compare.
     * @param itemStack2  The right stack to compare.
     * @param matchDamage Set to true to match damage data.
     * @param matchNBT    Set to true to match nbt
     * @param min         if the count of stack2 has to be at least the same as stack1.
     * @return True when they are equal except the stacksize, false when not.
     */
    public static boolean compareItemStacksIgnoreStackSize(
      final ItemStack itemStack1,
      final ItemStack itemStack2,
      final boolean matchDamage,
      final boolean matchNBT,
      final boolean min)
    {
        if (isEmpty(itemStack1) && isEmpty(itemStack2))
        {
            return true;
        }

        if (isEmpty(itemStack1) != isEmpty(itemStack2))
        {
            return false;
        }

        if (itemStack1.getItem() == itemStack2.getItem() && (!matchDamage || itemStack1.getDamageValue() == itemStack2.getDamageValue()))
        {
            if (!matchNBT)
            {
                // Not comparing nbt
                return true;
            }

            if (min && itemStack1.getCount() > itemStack2.getCount())
            {
                return false;
            }

            // Then sort on NBT
            if (!itemStack1.getComponents().isEmpty() && !itemStack2.getComponents().isEmpty())
            {
                final DataComponentMap nbt1 = itemStack1.getComponents();
                final DataComponentMap nbt2 = itemStack2.getComponents();

                for(final DataComponentType<?> key : nbt1.keySet())
                {
                    if(!matchDamage && key == DataComponents.DAMAGE)
                    {
                        continue;
                    }
                    if(!nbt2.has(key) || !nbt1.get(key).equals(nbt2.get(key)))
                    {
                        return false;
                    }
                }

                return nbt1.keySet().size() == nbt2.keySet().size();
            }
            else
            {
                return itemStack1.getComponents().isEmpty() == itemStack2.getComponents().isEmpty();
            }
        }
        return false;
    }

    /**
     * Item serializer helper, including air
     *
     * @param stack
     * @param buf
     */
    public static void serializeToBuffer(final ItemStack stack, RegistryFriendlyByteBuf buf)
    {
        buf.writeBoolean(stack.isEmpty());
        if (!stack.isEmpty())
        {
            ItemStack.STREAM_CODEC.encode(buf, stack);
        }
    }

    /**
     * Item deserializer helper, including air. Must be serialized with the above util
     *
     * @param buf
     */
    public static ItemStack deserializeFromBuffer(RegistryFriendlyByteBuf buf)
    {
        if (!buf.readBoolean())
        {
            return ItemStack.STREAM_CODEC.decode(buf);
        }

        return ItemStack.EMPTY;
    }
}
