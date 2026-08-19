package com.minecolonies.core.research;

import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.Constants;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import com.ldtteam.common.network.PlayMessageContext;

import org.jetbrains.annotations.NotNull;

/**
 * The message used to synchronize global research trees from a server to a remote client.
 */
public class GlobalResearchTreeMessage extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(Constants.MOD_ID, "global_research_tree", GlobalResearchTreeMessage::new, true, false);

    /**
     * The buffer with the data.
     */
    private final RegistryFriendlyByteBuf treeBuffer;

    /**
     * Add or Update a GlobalResearchTree on the client.
     *
     * @param buf               the bytebuffer.
     */
    public GlobalResearchTreeMessage(final RegistryFriendlyByteBuf buf)
    {
        super(TYPE);
        this.treeBuffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(buf.copy()), buf.registryAccess());
    }

    protected GlobalResearchTreeMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        treeBuffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(Unpooled.wrappedBuffer(buf.readByteArray())), buf.registryAccess());
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        Utils.writeBufferContents(buf, treeBuffer);
    }

    @Override
    public void onExecute(final PlayMessageContext context, final Player player)
    {
        IGlobalResearchTree.getInstance().handleGlobalResearchTreeMessage(treeBuffer);
    }
}
