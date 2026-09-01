package com.minecolonies.api.tileentities;

import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static com.minecolonies.api.util.constant.Constants.TAG_STRING;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_CONTENT;

public class AbstractTileEntityNamedGrave extends BlockEntity
{
    /**
     * The position it faces.
     */
    public static final EnumProperty<Direction> FACING       = HorizontalDirectionalBlock.FACING;

    /**
     * The text displayed on the name plate
     */
    private ArrayList<String> textLines = new ArrayList<>();

    public AbstractTileEntityNamedGrave(BlockEntityType<?> tileEntityTypeIn, final BlockPos pos, final BlockState state)
    {
        super(tileEntityTypeIn, pos, state);
        textLines.add("Unknown Citizen");
    }

    public ArrayList<String> getTextLines()
    {
        return textLines;
    }

    public void setTextLines(final ArrayList<String> content)
    {
        this.textLines = content;
        setChanged();
    }

    /**
     * The name of the citizen resting under this headstone, as the graveyard's resting list spells it.
     * <p>
     * The stone carries the name split across its first two lines - a first name and everything after it, the second
     * still holding the space that separated them - so joining the two reproduces the name exactly as
     * {@code GraveyardManagementModule} recorded it. A stone that has never been written on has one line and no
     * name.
     *
     * @return the citizen's name, or an empty string if this stone names nobody.
     */
    public String getRestingCitizenName()
    {
        if (textLines.size() < 2)
        {
            return "";
        }
        return textLines.get(0) + textLines.get(1);
    }

    @Override
    protected void loadAdditional(@NotNull final ValueInput input)
    {
        // 26.2: BlockEntity NBT is codec-based, CompoundTag+Provider -> ValueInput/ValueOutput
        // (/opt/mc-src/net/minecraft/world/level/block/entity/BlockEntity.java:97,109).
        super.loadAdditional(input);

        textLines.clear();
        textLines.addAll(input.read(TAG_CONTENT, Codec.STRING.listOf()).orElse(List.of()));
    }

    @Override
    protected void saveAdditional(@NotNull final ValueOutput output)
    {
        super.saveAdditional(output);
        output.store(TAG_CONTENT, Codec.STRING.listOf(), List.copyOf(textLines));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull final HolderLookup.Provider provider)
    {
        return this.saveWithoutMetadata(provider);
    }

    // 26.2 removed BlockEntity#onDataPacket and #handleUpdateTag(CompoundTag, Provider): the vanilla client applies
    // the update tag through loadWithComponents/loadAdditional itself, so the two forwarding overrides are gone.

    @Override
    public void setChanged()
    {
        if (level != null)
        {
            WorldUtil.markChunkDirty(level, worldPosition);
        }
    }
}
