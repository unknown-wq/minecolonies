package com.minecolonies.core.items;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.items.component.Desc;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.core.network.messages.client.VanillaParticleMessage;
import com.minecolonies.core.util.TeleportHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
// 26.2: ParticleTypes.INSTANT_EFFECT is a ParticleType<SpellParticleOption> now, not a
// SimpleParticleType -- it needs an explicit colour/power. White at full power reproduces the
// 1.21.1 look of the option-less instant-effect particle.
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SpellParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import java.util.List;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.*;

/**
 * Teleport scroll to teleport you back to the set colony. Requires colony permissions
 */
public class ItemScrollColonyTP extends AbstractItemScroll
{
    /**
     * Sets the name, creative tab, and registers the item.
     *
     * @param properties the properties.
     */
    public ItemScrollColonyTP(final Properties properties)
    {
        super("scroll_tp", properties);
    }

    @Override
    protected ItemStack onItemUseSuccess(final ItemStack itemStack, final Level world, final ServerPlayer player)
    {
        if (world.getRandom().nextInt(10) == 0)
        {
            // Fail
            player.sendSystemMessage(Component.translatableEscape("minecolonies.scroll.failed" + (world.getRandom().nextInt(FAIL_RESPONSES_TOTAL) + 1)).setStyle(Style.EMPTY.withColor(
              ChatFormatting.GOLD)));

            BlockPos pos = null;
            for (final Direction dir : Direction.Plane.HORIZONTAL)
            {
                pos = BlockPosUtil.findAround(world,
                  player.blockPosition().relative(dir, 10),
                  5,
                  5,
                  (predWorld, predPos) -> BlockUtils.isAnySolid(predWorld.getBlockState(predPos.below())) && predWorld.getBlockState(predPos).isAir() && predWorld.getBlockState(predPos.above()).isAir());
                if (pos != null)
                {
                    break;
                }
            }

            if (pos != null)
            {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, TICKS_SECOND * 7));
                player.teleportTo((ServerLevel) world, pos.getX(), pos.getY(), pos.getZ(), java.util.Set.of(), player.getYRot(), player.getXRot(), false);
            }

            SoundUtils.playSoundForPlayer(player, SoundEvents.BAT_TAKEOFF, 0.4f, 1.0f);
        }
        else
        {
            // Success
            doTeleport(player, getColony(itemStack), itemStack);
            SoundUtils.playSoundForPlayer(player, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6f, 1.0f);
        }

        itemStack.shrink(1);
        return itemStack;
    }

    @Override
    protected boolean needsColony()
    {
        return true;
    }

    /**
     * Does the teleport action
     *
     * @param player user of the item
     * @param colony colony to teleport to
     */
    protected void doTeleport(final ServerPlayer player, final IColony colony, final ItemStack stack)
    {
        TeleportHelper.colonyTeleport(player, colony);
    }

    @Override
    public void onUseTick(Level worldIn, LivingEntity entity, ItemStack stack, int count)
    {
        if (!worldIn.isClientSide() && worldIn.getGameTime() % 5 == 0)
        {
            final Entity entity1 = entity;
            new VanillaParticleMessage(entity.getX(), entity.getY(), entity.getZ(), SpellParticleOption.create(ParticleTypes.INSTANT_EFFECT, 0xFFFFFF, 1.0F)).sendToTrackingEntity(entity1);
            new VanillaParticleMessage(entity.getX(), entity.getY(), entity.getZ(), SpellParticleOption.create(ParticleTypes.INSTANT_EFFECT, 0xFFFFFF, 1.0F)).sendToPlayer((ServerPlayer) entity);
        }
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> tooltip,
      @NotNull final TooltipFlag flagIn)
    {
        final MutableComponent guiHint = Component.translatableEscape(TOOL_COLONY_TELEPORT_SCROLL_DESCRIPTION);
        guiHint.setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GREEN));
        tooltip.accept(guiHint);

        Component colonyDesc = Component.translatable(TOOL_COLONY_TELEPORT_SCROLL_NO_COLONY);

        final IColony colony = getColonyView(stack);
        if (colony != null)
        {
            colonyDesc = Component.literal(colony.getName());
        }

        final MutableComponent guiHint2 = Component.translatable(TOOL_COLONY_TELEPORT_SCROLL_COLONY_NAME, colonyDesc);
        guiHint2.setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD));
        tooltip.accept(guiHint2);
    }
}
