package com.minecolonies.core.colony;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.IVisitorViewData;
import com.minecolonies.api.util.Utils;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * View data for visitors
 */
public class VisitorDataView extends CitizenDataView implements IVisitorViewData
{
    /**
     * The recruitment costs
     */
    private ItemStack recruitmentCosts;

    /**
     * Cached player info for custom texture.
     */
    private volatile Identifier cachedTexture;

    /**
     * Session profile cache for a given special visitor.
     */
    private GameProfile cachedProfile = null;

    /**
     * Create a CitizenData given an ID. Used as a super-constructor or during loading.
     *
     * @param id     ID of the Citizen.
     * @param colony Colony the Citizen belongs to.
     */
    public VisitorDataView(final int id, final IColonyView colony)
    {
        super(id, colony);
    }

    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.deserialize(buf);
        recruitmentCosts = Utils.deserializeCodecMess(buf);
        recruitmentCosts.setCount(buf.readInt());
    }

    @Override
    public ItemStack getRecruitCost()
    {
        return recruitmentCosts;
    }

    @Override
    public Identifier getCustomTexture()
    {
        if (textureUUID == null)
        {
            return null;
        }

        if (cachedProfile == null)
        {
            Util.backgroundExecutor().execute(() ->
            {
                if (cachedProfile == null)
                {
                    // 26.3: authlib 10 moved ProfileResult from com.mojang.authlib.yggdrasil to
                    // com.mojang.authlib.services; the record itself is unchanged.
                    final ProfileResult profile = Minecraft.getInstance().services().sessionService().fetchProfile(textureUUID, true);
                    if (profile != null)
                    {
                        cachedProfile = profile.profile();
                    }
                }
            });
        }

        if (cachedProfile != null && cachedTexture == null)
        {
            // PORT(26.2): SkinManager#getInsecureSkin is gone; createLookup(profile, false) yields the same non-secure skin.
            final Identifier texture = Minecraft.getInstance().getSkinManager().createLookup(cachedProfile, false).get().body().texturePath();
            if (texture != DefaultPlayerSkin.get(textureUUID).body().texturePath())
            {
                cachedTexture = texture;
            }
        }

        return cachedTexture == null ? DefaultPlayerSkin.get(textureUUID).body().texturePath() : cachedTexture;
    }
}
