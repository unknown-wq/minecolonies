package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.ISpecialBlockPickItem;
import com.ldtteam.structurize.api.Utils;
import com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution;
import com.ldtteam.structurize.blocks.ModBlocks;
import com.ldtteam.structurize.component.CapturedBlock;
import com.ldtteam.structurize.component.ModDataComponents;
import com.ldtteam.structurize.network.messages.AbsorbBlockMessage;
import com.ldtteam.structurize.tag.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import java.util.Optional;

public class ItemTagSubstitution extends BlockItem implements ISpecialBlockPickItem
{
    /**
     * <p>Port note: {@code useBlockDescriptionPrefix()} is mandatory for a hand-registered {@link BlockItem}
     * since 1.21.4. An item no longer borrows its {@code descriptionId} from the block it places, it takes it
     * from its own {@link Properties}, and the default there is {@code ITEM_DESCRIPTION_ID}, i.e.
     * {@code item.<ns>.<path>} (/opt/mc-src/net/minecraft/world/item/Item.java:382,637,654 — the id is
     * resolved once in {@code Item#<init>} at line 135). Vanilla applies the block prefix inside
     * {@code Items#registerBlock}; a mod that calls {@code Registry.register} itself has to say so. Without
     * it this item asks for {@code item.structurize.blocktagsubstitution}, which no language file defines —
     * the shipped one has {@code block.structurize.blocktagsubstitution} — and the item renders as the raw
     * translation key with nothing in the log.</p>
     *
     * @return the properties for the tag anchor item, without the id.
     */
    public static Properties defaultProperties()
    {
        return new Properties().useBlockDescriptionPrefix().component(ModDataComponents.CAPTURED_BLOCK, CapturedBlock.EMPTY);
    }

    public ItemTagSubstitution(final Properties properties)
    {
        super(ModBlocks.blockTagSubstitution.get(), properties);
    }

    @NotNull
    @Override
    public InteractionResult onBlockPick(@NotNull Player player,
                                         @NotNull ItemStack stack,
                                         @Nullable BlockPos pos,
                                         final boolean ctrlKey)
    {
        if (pos == null)
        {
            if (!player.level().isClientSide())
            {
                CapturedBlock.EMPTY.writeToItemStack(stack);
            }
            return InteractionResult.SUCCESS;
        }

        final BlockState blockstate = player.level().getBlockState(pos);
        if (blockstate.is(BlockTags.WITHER_IMMUNE))
        {
            // this way lies madness, and/or Sparta...
            if (!player.level().isClientSide())
            {
                CapturedBlock.EMPTY.writeToItemStack(stack);
            }
            return InteractionResult.SUCCESS;
        }

        if (player.level().isClientSide())
        {
            ItemStack pick = getPickedBlock(player, pos, blockstate);

            // sadly we can't use the default message since we want to pass an extra ItemStack...
            //   (and getCloneItemStack is client-side-only, somewhat strangely)
            new AbsorbBlockMessage(pos, pick).sendToServer();
        }
        return InteractionResult.FAIL;
    }

    @NotNull
    private ItemStack getPickedBlock(@NotNull Player player, @NotNull BlockPos pos, @NotNull BlockState blockstate)
    {
        // 26.2 merged the pick-block hooks into getCloneItemStack(LevelReader, BlockPos, boolean includeData)
        // (/opt/mc-src/net/minecraft/world/level/block/state/BlockBehaviour.java:893); the HitResult / Player
        // arguments are gone.
        return blockstate.getCloneItemStack(player.level(), pos, true);
    }

    public void onAbsorbBlock(@NotNull final ServerPlayer player,
                              @NotNull final ItemStack stack,
                              @NotNull final BlockPos pos,
                              @NotNull final ItemStack absorbItem)
    {
        final BlockState blockstate = player.level().getBlockState(pos);
        final BlockEntity blockentity = player.level().getBlockEntity(pos);

        final CapturedBlock replacement;
        if (blockentity instanceof BlockEntityTagSubstitution blockception)
        {
            replacement = blockception.getReplacement();
        }
        else if (!isAllowed(blockentity))
        {
            Utils.playErrorSound(player);
            return;
        }
        else
        {
            replacement = new CapturedBlock(blockstate, blockentity, player.level().registryAccess(), absorbItem);
        }

        replacement.writeToItemStack(stack);
    }

    private boolean isAllowed(@Nullable final BlockEntity blockentity)
    {
        if (blockentity == null) return true;

        // 26.2: Registry#getTag(TagKey) is gone; the Holder itself answers tag membership
        // (/opt/mc-src/net/minecraft/core/Registry.java:137 only keeps getTagOrEmpty)
        return blockentity.getType().builtInRegistryHolder().is(ModTags.SUBSTITUTION_ABSORB_WHITELIST);
    }

    /**
     * TODO(port-26.2): DISABLED — {@code IItemExtension#getHighlightTip(ItemStack, Component)} is a NeoForge
     * extension with no vanilla or Fabric API equivalent in 26.2. Effect: the hotbar name of a tag
     * substitution holding an absorbed block no longer shows " - &lt;absorbed block&gt;".
     * Original:
     * <pre>
     * &#64;Override
     * public Component getHighlightTip(final ItemStack stack, final Component displayName)
     * {
     *     final ItemStack absorbed = CapturedBlock.readFromItemStack(stack).itemStack();
     *     if (!absorbed.isEmpty())
     *     {
     *         return Component.empty()
     *                 .append(super.getHighlightTip(stack, displayName))
     *                 .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
     *                 .append(absorbed.getHoverName());
     *     }
     *     return super.getHighlightTip(stack, displayName);
     * }
     * </pre>
     */
}
