package com.ldtteam.structurize.compat;

import com.ldtteam.domumornamentum.block.AbstractBlockDoor;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.decorative.PillarBlock;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.entity.block.IMateriallyTexturedBlockEntity;
import com.ldtteam.domumornamentum.entity.block.MateriallyTexturedBlockEntity;
import com.ldtteam.domumornamentum.entity.block.ModBlockEntityTypes;
import com.ldtteam.domumornamentum.util.Constants;
import com.ldtteam.structurize.api.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The only class in Structurize that is allowed to name a Domum Ornamentum type (contract C7).
 *
 * <p>Four files used to reach into DO directly ({@code util/BlockUtils},
 * {@code placement/handlers/placement/DoBlockPlacementHandler},
 * {@code placement/handlers/placement/DoDoorBlockPlacementHandler}, {@code datagen/BlockEntityTagProvider}) —
 * nine distinct symbols in total. They all go through this facade now, so if DO ever stops shipping a 26.x
 * build only this one class has to be neutralised instead of four.</p>
 *
 * <p>DO is a hard dependency in {@code fabric.mod.json}, so in practice {@link #isLoaded()} is always true;
 * it exists so callers can be written defensively without knowing anything about DO.</p>
 */
public final class DomumCompat
{
    /**
     * NBT key Domum Ornamentum stores the material texture map under.
     * Mirrors {@code com.ldtteam.domumornamentum.util.Constants#BLOCK_ENTITY_TEXTURE_DATA}.
     */
    public static final String TEXTURE_DATA_TAG = Constants.BLOCK_ENTITY_TEXTURE_DATA;

    /**
     * Legacy key some older blueprints still carry.
     */
    public static final String LEGACY_TEXTURE_DATA_TAG = "originalTextureData";

    private DomumCompat()
    {
    }

    /**
     * @return true when Domum Ornamentum is present.
     */
    public static boolean isLoaded()
    {
        return FabricLoader.getInstance().isModLoaded(Constants.MOD_ID);
    }

    // ---------------------------------------------------------------- blocks

    /**
     * @param state the block state to test.
     * @return true when the block is one of DO's materially textured blocks.
     */
    public static boolean isMateriallyTexturedBlock(final BlockState state)
    {
        return state.getBlock() instanceof IMateriallyTexturedBlock;
    }

    /**
     * @param state the block state to test.
     * @return true when the block is a materially textured DO door.
     */
    public static boolean isMateriallyTexturedDoor(final BlockState state)
    {
        return state.getBlock() instanceof IMateriallyTexturedBlock && state.getBlock() instanceof AbstractBlockDoor<?>;
    }

    /**
     * @param state the block state to test.
     * @return true when the block is a DO pillar, which needs the same neighbour-aware placement
     *         treatment as walls, fences and iron bars.
     */
    public static boolean isPillarBlock(final BlockState state)
    {
        return state.getBlock() instanceof PillarBlock;
    }

    // -------------------------------------------------------- block entities

    /**
     * @param blockEntity the block entity to test, may be null.
     * @return true when this is DO's concrete materially textured block entity.
     */
    public static boolean isMateriallyTexturedBlockEntity(final @Nullable BlockEntity blockEntity)
    {
        return blockEntity instanceof MateriallyTexturedBlockEntity;
    }

    /**
     * @param blockEntity the block entity to test, may be null.
     * @return true when this block entity exposes DO texture data at all (interface check, wider than
     *         {@link #isMateriallyTexturedBlockEntity(BlockEntity)}).
     */
    public static boolean hasTextureData(final @Nullable BlockEntity blockEntity)
    {
        return blockEntity instanceof IMateriallyTexturedBlockEntity;
    }

    /**
     * Compares the texture data a block entity currently carries against the texture data serialised in a
     * blueprint's block entity tag. Understands both the current and the legacy tag name.
     *
     * @param blockEntity    the block entity in the world.
     * @param serializedData the block entity tag from the blueprint, may be null.
     * @return true when both sides carry the same materials.
     */
    public static boolean textureDataMatches(final @Nullable BlockEntity blockEntity, final @Nullable CompoundTag serializedData)
    {
        if (!(blockEntity instanceof final IMateriallyTexturedBlockEntity texturedBlockEntity) || serializedData == null)
        {
            return false;
        }

        final Tag source;
        if (serializedData.contains(TEXTURE_DATA_TAG))
        {
            source = serializedData.get(TEXTURE_DATA_TAG);
        }
        else if (serializedData.contains(LEGACY_TEXTURE_DATA_TAG))
        {
            source = serializedData.get(LEGACY_TEXTURE_DATA_TAG);
        }
        else
        {
            return false;
        }

        try
        {
            final MaterialTextureData decoded = MaterialTextureData.CODEC.decode(NbtOps.INSTANCE, source).getOrThrow().getFirst();
            return texturedBlockEntity.getTextureData().equals(decoded);
        }
        catch (final RuntimeException e)
        {
            Log.getLogger().warn("Could not decode Domum Ornamentum texture data from blueprint", e);
            return false;
        }
    }

    // ----------------------------------------------------------------- items

    /**
     * Builds the item form of a materially textured block, carrying its materials.
     *
     * @param blockEntity the block entity of the placed block.
     * @param provider    registry access.
     * @return the stack, or {@link ItemStack#EMPTY} when the block entity is not a DO one.
     */
    public static ItemStack getMaterializedItemStack(final @Nullable BlockEntity blockEntity, final HolderLookup.Provider provider)
    {
        if (blockEntity == null)
        {
            return ItemStack.EMPTY;
        }
        return com.ldtteam.domumornamentum.util.BlockUtils.getMaterializedItemStack(blockEntity, provider);
    }

    // --------------------------------------------------------------- datagen

    /**
     * @return DO's materially textured block entity type, used to build the block entity tag Structurize
     *         generates.
     */
    public static BlockEntityType<BlockEntity> materiallyTexturedBlockEntityType()
    {
        return ModBlockEntityTypes.MATERIALLY_TEXTURED.get();
    }
}
