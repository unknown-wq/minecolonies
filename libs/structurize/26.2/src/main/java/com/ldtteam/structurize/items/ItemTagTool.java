package com.ldtteam.structurize.items;

import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.client.gui.GuiStubs;
import com.ldtteam.structurize.component.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Item for tagging positions with tags
 */
public class ItemTagTool extends AbstractItemWithPosSelector
{
    /**
     * Creates default scan tool item.
     */
    public static Properties defaultProperties()
    {
        return new Properties().durability(0)
            .rarity(Rarity.UNCOMMON)
            .component(ModDataComponents.TAGS_DATA, TagData.EMPTY);
    }

    /**
     * MC constructor.
     *
     * @param properties properties
     */
    public ItemTagTool(final Properties properties)
    {
        super(properties);
    }

    @Override
    public AbstractItemWithPosSelector getRegisteredItemInstance()
    {
        return ModItems.tagTool.get();
    }

    @Override
    public InteractionResult onAirRightClick(final BlockPos start, final BlockPos end, final Level worldIn, final Player playerIn, final ItemStack itemStack)
    {
        if (worldIn.isClientSide())
        {
            final TagData tagData = TagData.readFromItemStack(itemStack);
            if (tagData.anchorPos().isEmpty())
            {
                playerIn.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.noanchor"));
                return InteractionResult.FAIL;
            }

            GuiStubs.openTagToolWindow(tagData.currentTag().orElse(""), tagData.anchorPos().get(), worldIn, itemStack);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level worldIn, Player playerIn, InteractionHand handIn)
    {
        return onAirRightClick(null, null, worldIn, playerIn, playerIn.getItemInHand(handIn));
    }

    @Override
    public InteractionResult useOn(final UseOnContext context)
    {
        if (context.getPlayer() == null)
        {
            return InteractionResult.SUCCESS;
        }

        // Set anchor
        if (context.getPlayer().isShiftKeyDown())
        {
            BlockEntity te = context.getLevel().getBlockEntity(context.getClickedPos());
            if (te instanceof IBlueprintDataProviderBE)
            {
                TagData.updateItemStack(context.getItemInHand(), tags -> tags.setAnchorPos(context.getClickedPos()));
                if (context.getLevel().isClientSide())
                {
                    context.getPlayer().sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.anchorsaved"));
                }
                return InteractionResult.SUCCESS;
            }
            else
            {
                if (context.getLevel().isClientSide())
                {
                    context.getPlayer().sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.anchor.notvalid"));
                }
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * 26.2: {@code Item#canAttackBlock} became
     * {@code Item#canDestroyBlock(ItemStack, BlockState, Level, BlockPos, LivingEntity)}
     * (/opt/mc-src/net/minecraft/world/item/Item.java:169).
     */
    @Override
    public boolean canDestroyBlock(final ItemStack heldStack, final BlockState state, final Level worldIn, final BlockPos pos, final LivingEntity user)
    {
        if (!(user instanceof final Player player))
        {
            return super.canDestroyBlock(heldStack, state, worldIn, pos, user);
        }
        final ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != ModItems.tagTool.get())
        {
            return false;
        }

        final TagData tagData = TagData.readFromItemStack(stack);

        if (tagData.anchorPos().isEmpty())
        {
            player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.noanchor"));
            return false;
        }

        if (tagData.currentTag().isEmpty())
        {
            player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.notag"));
            return false;
        }

        // Apply tag to item
        final BlockPos anchorPos = tagData.anchorPos().get();
        final String currentTag = tagData.currentTag().get();
        BlockPos relativePos = pos.subtract(anchorPos);

        final BlockEntity te = worldIn.getBlockEntity(anchorPos);
        if (!(te instanceof final IBlueprintDataProviderBE blueprintBe))
        {
            player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.anchor.notvalid"));
            TagData.updateItemStack(stack, tags -> tags.setAnchorPos(null));
            return false;
        }

        // add/remove tags
        Map<BlockPos, List<String>> tagPosMap = blueprintBe.getPositionedTags();

        if (!tagPosMap.containsKey(relativePos) || !tagPosMap.get(relativePos).contains(currentTag))
        {
            blueprintBe.addTag(relativePos, currentTag);
            if (worldIn.isClientSide())
            {
                player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.addtag",
                        currentTag,
                        worldIn.getBlockState(pos).getBlock().getName()));
            }
        }
        else
        {
            blueprintBe.removeTag(relativePos, currentTag);
            if (worldIn.isClientSide())
            {
                player.sendSystemMessage(Component.translatable("com.ldtteam.structurize.gui.tagtool.removed",
                        currentTag,
                        worldIn.getBlockState(pos).getBlock().getName()));
            }
        }

        return false;
    }

    /**
     * Data components for storing start and end pos
     */
    public record TagData(Optional<BlockPos> anchorPos, Optional<String> currentTag)
    {     
        public static final TagData EMPTY = new TagData(Optional.empty(), Optional.empty());

        public static final Codec<TagData> CODEC = RecordCodecBuilder.create(
            builder -> builder
                .group(BlockPos.CODEC.optionalFieldOf("anchor_pos_tag").forGetter(TagData::anchorPos),
                    Codec.STRING.optionalFieldOf("current_tag").forGetter(TagData::currentTag))
                .apply(builder, TagData::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, TagData> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
                TagData::anchorPos,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
                TagData::currentTag,
                TagData::new);

        /**
         * For use with {@link ItemStack#update(DataComponentType, Object, UnaryOperator)}
         */
        public TagData setAnchorPos(final BlockPos pos)
        {
            return new TagData(Optional.ofNullable(pos), currentTag);
        }

        /**
         * For use with {@link ItemStack#update(DataComponentType, Object, UnaryOperator)}
         */
        public TagData setCurrentTag(final String currentTag)
        {
            return new TagData(anchorPos, Optional.ofNullable(currentTag.isEmpty() ? null : currentTag));
        }

        /**
         * Writes this tagData into given itemStack.
         * 
         * @see BlockEntity#saveToItem(ItemStack, net.minecraft.core.HolderLookup.Provider)
         */
        public void writeToItemStack(final ItemStack itemStack)
        {
            itemStack.set(ModDataComponents.TAGS_DATA, this);
        }
    
        /**
         * @return tagData stored in given itemStack (or empty instance)
         */
        public static TagData readFromItemStack(final ItemStack itemStack)
        {
            return itemStack.getOrDefault(ModDataComponents.TAGS_DATA, TagData.EMPTY);
        }
    
        /**
         * Performs updating of tagData in given itemStack
         */
        public static void updateItemStack(final ItemStack itemStack, final UnaryOperator<TagData> updater)
        {
            updater.apply(readFromItemStack(itemStack)).writeToItemStack(itemStack);
        }
    }
}
