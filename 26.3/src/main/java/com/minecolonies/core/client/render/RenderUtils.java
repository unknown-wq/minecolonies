package com.minecolonies.core.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

public class RenderUtils
{
    /**
     * Arm pose helper, taken from PlayerRenderer#getArmPose.
     * <p>
     * PORT-26.2: {@code UseAnim} is now {@code ItemUseAnimation}, {@code ArmPose.THROW_SPEAR} is {@code ArmPose.SPEAR},
     * and the NeoForge {@code IClientItemExtensions#getArmPose} hook has no Fabric equivalent.
     *
     * @param entity the mob holding the item.
     * @param hand   the hand to check.
     * @return the arm pose.
     */
    public static HumanoidModel.ArmPose getArmPose(final Mob entity, InteractionHand hand)
    {
        if (entity.isLeftHanded())
        {
            hand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        }

        ItemStack itemstack = entity.getItemInHand(hand);
        if (itemstack.isEmpty())
        {
            return HumanoidModel.ArmPose.EMPTY;
        }
        else
        {
            if (entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0)
            {
                ItemUseAnimation useanim = itemstack.getUseAnimation();
                if (useanim == ItemUseAnimation.BLOCK)
                {
                    return HumanoidModel.ArmPose.BLOCK;
                }

                if (useanim == ItemUseAnimation.BOW)
                {
                    return HumanoidModel.ArmPose.BOW_AND_ARROW;
                }

                if (useanim == ItemUseAnimation.SPEAR)
                {
                    return HumanoidModel.ArmPose.SPEAR;
                }

                if (useanim == ItemUseAnimation.TRIDENT)
                {
                    return HumanoidModel.ArmPose.THROW_TRIDENT;
                }

                if (useanim == ItemUseAnimation.CROSSBOW && hand == entity.getUsedItemHand())
                {
                    return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
                }

                if (useanim == ItemUseAnimation.SPYGLASS)
                {
                    return HumanoidModel.ArmPose.SPYGLASS;
                }

                if (useanim == ItemUseAnimation.TOOT_HORN)
                {
                    return HumanoidModel.ArmPose.TOOT_HORN;
                }

                if (useanim == ItemUseAnimation.BRUSH)
                {
                    return HumanoidModel.ArmPose.BRUSH;
                }
            }
            else if (!entity.isSwinging() && itemstack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(itemstack))
            {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }

            // TODO(port-26.2): DISABLED — IClientItemExtensions#getArmPose is a NeoForge client extension with no
            //  Fabric counterpart, so items from other mods can no longer override the pose here.
            return HumanoidModel.ArmPose.ITEM;
        }
    }

    /**
     * Same as {@link #getArmPose(Mob, InteractionHand)} but for the arm based hook 26.2 renderers use
     * ({@code HumanoidMobRenderer#getArmPose}).
     *
     * @param entity the mob holding the item.
     * @param arm    the arm to check.
     * @return the arm pose.
     */
    public static HumanoidModel.ArmPose getArmPose(final Mob entity, final HumanoidArm arm)
    {
        return getArmPose(entity, arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }
}
