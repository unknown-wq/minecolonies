package com.minecolonies.core.tileentities;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.api.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.util.constant.NbtTagConstants.*;

public class TileEntityColonyFlag extends BlockEntity
{
    /** A list of the default banner patterns, for colonies that have not chosen a flag */
    private BannerPatternLayers patterns = BannerPatternLayers.EMPTY;

    /** The colony of the player that placed this banner */
    public int colonyId = -1;

    public TileEntityColonyFlag(final BlockPos pos, final BlockState state) { super(MinecoloniesTileEntities.COLONY_FLAG.get(), pos, state); }

    public BannerPatternLayers getPatterns()
    {
        if (level != null && level.getGameTime() % 20 == 0)
        {
            final IColonyView view = IColonyManager.getInstance().getColonyView(colonyId, level.dimension());
            if (view != null)
            {
                this.patterns = view.getColonyFlag();
            }
        }

        return this.patterns;
    }

    @Override
    public void saveAdditional(@NotNull final ValueOutput compound)
    {
        super.saveAdditional(compound);

        compound.store(TAG_BANNER_PATTERNS, BannerPatternLayers.CODEC, this.patterns);

        compound.putInt(TAG_COLONY_ID, colonyId);
    }

    @Override
    public void loadAdditional(@NotNull final ValueInput compound)
    {
        super.loadAdditional(compound);

        compound.read(TAG_BANNER_PATTERNS, BannerPatternLayers.CODEC).ifPresent(read -> this.patterns = read);

        this.colonyId = compound.getIntOr(TAG_COLONY_ID, 0);

        if(this.colonyId == -1 && this.hasLevel())
        {
            IColony colony = IColonyManager.getInstance().getIColony(this.getLevel(), worldPosition);
            if (colony != null)
            {
                this.colonyId = colony.getID();
                this.setChanged();
            }
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(@NotNull final HolderLookup.Provider provider) { return this.saveWithFullMetadata(provider); }

    // 26.2 removed BlockEntity#onDataPacket and #handleUpdateTag: the vanilla client applies the update
    // tag through loadWithComponents/loadAdditional itself, so the forwarding overrides are gone.
}
