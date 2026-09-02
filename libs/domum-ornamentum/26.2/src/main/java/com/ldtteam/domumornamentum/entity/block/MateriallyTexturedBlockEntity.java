package com.ldtteam.domumornamentum.entity.block;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.component.ModDataComponents;
import com.ldtteam.domumornamentum.util.MaterialTextureDataUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import static com.ldtteam.domumornamentum.entity.block.ModBlockEntityTypes.MATERIALLY_TEXTURED;
import static com.ldtteam.domumornamentum.util.Constants.BLOCK_ENTITY_TEXTURE_DATA;

public class MateriallyTexturedBlockEntity extends AbstractMateriallyTexturedBlockEntity
{

    private MaterialTextureData textureData = MaterialTextureData.EMPTY;

    public MateriallyTexturedBlockEntity(BlockPos pos, BlockState state)
    {
        super(MATERIALLY_TEXTURED.get(), pos, state);
    }

    @Override
    public void updateTextureDataWith(final MaterialTextureData materialTextureData)
    {
        this.textureData = materialTextureData;

        if (this.textureData.isEmpty())
        {
            this.textureData = MaterialTextureDataUtil.generateRandomTextureDataFrom(this.getBlockState().getBlock());
        }
        else if (getBlockState().getBlock() instanceof final IMateriallyTexturedBlock materiallyTexturedBlock)
        {
            textureData = textureData.retainComponentsFromBlock(materiallyTexturedBlock);
        }

        this.requestModelDataUpdate();
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(final HolderLookup.Provider provider)
    {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(final ValueOutput output)
    {
        super.saveAdditional(output);
        output.store(BLOCK_ENTITY_TEXTURE_DATA, MaterialTextureData.CODEC, textureData);
    }

    @Override
    protected void loadAdditional(final ValueInput input)
    {
        super.loadAdditional(input);
        textureData = input.read(BLOCK_ENTITY_TEXTURE_DATA, MaterialTextureData.CODEC).orElse(MaterialTextureData.EMPTY);
    }

    /**
     * Fabric's replacement for NeoForge's {@code ModelData}: the chunk builder reads
     * {@code RenderDataBlockEntity#getRenderData()} (fabric-block-getter-api-v2, implemented on
     * vanilla {@code BlockEntity} — /opt/mc-src/net/minecraft/world/level/block/entity/BlockEntity.java:43)
     * and hands the object straight to the model. This mod's models therefore receive the
     * {@link MaterialTextureData} instance itself instead of a one-entry ModelData map.
     */
    @Override
    public @NotNull Object getRenderData()
    {
        return this.textureData;
    }

    @Override
    @NotNull
    public MaterialTextureData getTextureData()
    {
        return textureData;
    }

    @Override
    protected void applyImplicitComponents(final @NonNull DataComponentGetter components)
    {
        super.applyImplicitComponents(components);
        updateTextureDataWith(components.getOrDefault(ModDataComponents.TEXTURE_DATA, MaterialTextureData.EMPTY));
    }

    @Override
    protected void collectImplicitComponents(final DataComponentMap.Builder componentBuilder)
    {
        super.collectImplicitComponents(componentBuilder);
        componentBuilder.set(ModDataComponents.TEXTURE_DATA, textureData);
    }

    @Override
    public void removeComponentsFromTag(final ValueOutput output)
    {
        output.discard(BLOCK_ENTITY_TEXTURE_DATA);
    }
}
