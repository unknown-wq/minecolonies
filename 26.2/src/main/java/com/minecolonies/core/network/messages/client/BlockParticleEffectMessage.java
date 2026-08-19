package com.minecolonies.core.network.messages.client;

import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.ldtteam.common.network.PlayMessageContext;

import org.jetbrains.annotations.NotNull;

/**
 * Handles the server telling nearby clients to render a particle effect. Created: February 10, 2016
 *
 * @author Colton
 */
public class BlockParticleEffectMessage extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(Constants.MOD_ID, "block_particle_effect", BlockParticleEffectMessage::new);

    public static final int BREAK_BLOCK = -1;

    private final BlockPos   pos;
    private final BlockState block;
    private final int        side;

    /**
     * Sends a message for particle effect.
     *
     * @param pos   Coordinates
     * @param state Block State
     * @param side  Side of the block causing effect
     */
    public BlockParticleEffectMessage(final BlockPos pos, @NotNull final BlockState state, final int side)
    {
        super(TYPE);
        this.pos = pos;
        this.block = state;
        this.side = side;
    }

    public BlockParticleEffectMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        pos = buf.readBlockPos();
        block = Block.stateById(buf.readInt());
        side = buf.readInt();
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
        buf.writeInt(Block.getId(block));
        buf.writeInt(side);
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final Player player)
    {
        // 26.2: ParticleEngine#destroy(BlockPos, BlockState) and #crack(BlockPos, Direction) are gone.
        // ClientLevel#addDestroyBlockEffect is the vanilla replacement for the break burst; there is no
        // counterpart for the single "crack" particle on a face, so it is emitted by hand with the same
        // BLOCK particle the old crack() used, offset slightly out of the hit face.
        final net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
        {
            return;
        }

        if (side == BREAK_BLOCK)
        {
            level.addDestroyBlockEffect(pos, block);
        }
        else
        {
            final Direction face = Direction.from3DDataValue(side);
            level.addParticle(new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK, block),
              pos.getX() + 0.5D + face.getStepX() * 0.6D,
              pos.getY() + 0.5D + face.getStepY() * 0.6D,
              pos.getZ() + 0.5D + face.getStepZ() * 0.6D,
              0.0D, 0.0D, 0.0D);
        }
    }
}
