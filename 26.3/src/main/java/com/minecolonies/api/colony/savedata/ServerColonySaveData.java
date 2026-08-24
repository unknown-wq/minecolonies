package com.minecolonies.api.colony.savedata;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.ColonyList;
import com.minecolonies.core.util.BackUpHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import com.mojang.serialization.Codec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_COLONIES;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_COLONY_MANAGER;

/**
 * The implementation of the colonyTagCapability.
 */
public class ServerColonySaveData extends SavedData implements IServerColonySaveData
{
    /**
     * World save data name.
     */
    public static final String NAME = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "colony_manager").toDebugFileName();

    /**
     * Registry lookup used by {@link #CODEC}.
     * <p>
     * 26.2 replaced {@code SavedData.Factory} + {@code save(CompoundTag, HolderLookup.Provider)} with a
     * {@link SavedDataType} carrying a plain {@link Codec} ({@code /opt/mc-src/net/minecraft/world/level/saveddata/
     * SavedDataType.java}), and a codec has no way to reach a {@code HolderLookup.Provider}. The mod's read/write
     * both need one, so it is captured when the save data is first requested for a level — there is exactly one
     * registry access per running server, so this is stable.
     */
    private static volatile HolderLookup.Provider registries = RegistryAccess.EMPTY;

    /**
     * Remember the registry access to use for (de)serialisation.
     *
     * @param provider the server's registry access.
     */
    public static void setRegistries(@NotNull final HolderLookup.Provider provider)
    {
        registries = provider;
    }

    /**
     * Codec bridging the mod's tag-based format into 26.2's codec-based saved data.
     */
    public static final Codec<ServerColonySaveData> CODEC = CompoundTag.CODEC.xmap(
      tag ->
      {
          final ServerColonySaveData data = new ServerColonySaveData();
          data.readNBT(registries, tag);
          return data;
      },
      data -> data.writeNBT(registries, new CompoundTag()));

    /**
     * Worldsavedata type.
     */
    public static final SavedDataType<ServerColonySaveData> TYPE = new SavedDataType<>(
      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "colony_manager"), ServerColonySaveData::new, CODEC, DataFixTypes.LEVEL);

    /**
     * The list of all colonies.
     */
    @NotNull
    private final ColonyList<IColony> colonies = new ColonyList<>();

    /**
     * Is this the main overworld cap?
     */
    private boolean overworld;

    public ServerColonySaveData()
    {

    }

    @Override
    public IColony createColony(@NotNull final ServerLevel w, @NotNull final String name, @NotNull final BlockPos pos)
    {
        return colonies.create(w, name, pos);
    }

    @Override
    public void deleteColony(final int id)
    {
        colonies.remove(id);
    }

    @Override
    public IColony getColony(final int id)
    {
        return colonies.get(id);
    }

    @Override
    public List<IColony> getColonies()
    {
        return colonies.getCopyAsList();
    }

    @Override
    public void addColony(final IColony colony)
    {
        colonies.add(colony);
    }

    @Override
    public int getTopID()
    {
        return colonies.getTopID();
    }

    @Override
    public boolean isDirty()
    {
        return true;
    }

    private CompoundTag writeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag inputTag)
    {
        final CompoundTag compound = new CompoundTag();
        final ListTag colonyTag = new ListTag();
        for (final IColony colony : colonies)
        {
            try
            {
                colonyTag.add(colony.getColonyTag());
            }
            catch (Exception e)
            {
                Log.getLogger()
                  .error("Colony: " + colony.getName() + " id:" + colony.getID() + " owner:" + colony.getPermissions().getOwnerName() + " could not be saved! Error:", e);
            }
        }

        compound.put(TAG_COLONIES, colonyTag);

        if (overworld)
        {
            final CompoundTag managerCompound = new CompoundTag();
            IColonyManager.getInstance().write(provider, managerCompound);
            compound.put(TAG_COLONY_MANAGER, managerCompound);
        }

        inputTag.put(Constants.MOD_ID, compound);
        return inputTag;
    }

    @Override
    public IServerColonySaveData setOverworld(final boolean overworld)
    {
        this.overworld = overworld;
        return this;
    }

    private void readNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag inputTag)
    {
        final CompoundTag compound = inputTag.getCompoundOrEmpty(Constants.MOD_ID);

        if (!compound.contains(TAG_COLONIES))
        {
            BackUpHelper.loadManagerBackup(provider);
            return;
        }

        // Load all colonies from Nbt
        Multimap<BlockPos, IColony> tempColonies = ArrayListMultimap.create();
        for (final Tag tag : compound.getListOrEmpty(TAG_COLONIES))
        {
            final IColony colony = Colony.loadColony((CompoundTag) tag, null, provider);
            if (colony != null)
            {
                tempColonies.put(colony.getCenter(), colony);
                colonies.add(colony);
            }
        }

        if (compound.contains(TAG_COLONY_MANAGER))
        {
            IColonyManager.getInstance().read(provider, compound.getCompoundOrEmpty(TAG_COLONY_MANAGER));
            this.overworld = true;
        }

        // Check colonies for duplicates causing issues.
        for (final BlockPos pos : tempColonies.keySet())
        {
            // Check if any position has more than one colony
            if (tempColonies.get(pos).size() > 1)
            {
                Log.getLogger().warn("Detected duplicate colonies which are at the same position:");
                for (final IColony colony : tempColonies.get(pos))
                {
                    Log.getLogger()
                        .warn(
                        "ID: " + colony.getID() + " name:" + colony.getName() + " citizens:" + colony.getCitizenManager().getCitizens().size() + " building count:" + colony
                                                                                                                                                                        .getCommonBuildingManager()
                                                                                                                                                                        .getBuildings()
                                                                                                                                                                        .size());
                }
                Log.getLogger().warn("Check and remove all except one of the duplicated colonies above!");
            }
        }
    }
}