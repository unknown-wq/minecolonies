package com.ldtteam.structurize.commands;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos.MutableBlockPos;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.ldtteam.structurize.api.constants.Constants.*;
import static com.ldtteam.structurize.blueprints.v1.BlueprintUtil.*;

/**
 * Command to update all schematics in structurize/updater/input to the blueprint format to structurize/updater/output.
 */
public class UpdateSchematicsCommand extends AbstractCommand
{
    private final static String NAME = "updateschematics";

    protected static LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return newLiteral(NAME).executes(s -> onExecute(s));
    }

    private static int onExecute(final CommandContext<CommandSourceStack> command) throws CommandSyntaxException
    {
        final Path gameFolder = new File(".").toPath().resolve(BLUEPRINT_FOLDER).resolve(UPDATE_FOLDER);
        try
        {
            Files.createDirectories(gameFolder);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        try
        {
            try (final Stream<Path> paths = Files.list(gameFolder.resolve("input")))
            {
                paths.forEach(element -> update(element, gameFolder.resolve("input"), gameFolder.resolve("output"), command.getSource().registryAccess()));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }


        return 1;
    }

    private static void update(final Path input, final Path globalInputFolder, final Path globalOutputFolder, final HolderLookup.Provider provider)
    {
        if (Files.isDirectory(input))
        {
            try
            {
                try (final Stream<Path> paths = Files.list(input))
                {
                    paths.forEach(element -> update(element, globalInputFolder, globalOutputFolder, provider));
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            return;
        }

        try
        {

            final Path output = globalOutputFolder.resolve(input.toString().replaceAll("\\.nbt", ".blueprint").replace(globalInputFolder.toString(), ""));
            Files.createDirectories(output.getParent());

            if (input.toString().endsWith(".blueprint"))
            {
                final CompoundTag bluePrintCompound = writeBlueprintToNBT(fixBluePrints(input, provider));
                try (final OutputStream outputstream = new BufferedOutputStream(Files.newOutputStream(output)))
                {
                    NbtIo.writeCompressed(bluePrintCompound, outputstream);
                }
                catch (final IOException e)
                {
                    Log.getLogger().warn("Exception while trying to scan.", e);
                }

                return;
            }

            CompoundTag blueprint = NbtIo.readCompressed(new ByteArrayInputStream(Files.readAllBytes(input)), NbtAccounter.unlimitedHeap());
            if (blueprint == null || blueprint.isEmpty())
            {
                return;
            }

            final ListTag blocks = blueprint.getListOrEmpty("blocks");
            final ListTag pallete = blueprint.getListOrEmpty("palette");

            final CompoundTag bluePrintCompound = new CompoundTag();

            final ListTag list = blueprint.getListOrEmpty("size");
            final int[] size = new int[] {list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0)};
            bluePrintCompound.putShort("size_x", (short) size[0]);
            bluePrintCompound.putShort("size_y", (short) size[1]);
            bluePrintCompound.putShort("size_z", (short) size[2]);

            final boolean addStructureVoid = blocks.size() != size[0] * size[1] * size[2];
            short structureVoidID = 0;
            if (addStructureVoid)
            {
                structureVoidID = (short) pallete.size();
                pallete.add(NbtUtils.writeBlockState(Blocks.STRUCTURE_VOID.defaultBlockState()));
            }

            final Set<String> mods = new HashSet<>();

            for (int i = 0; i < pallete.size(); i++)
            {
                final CompoundTag blockState = pallete.getCompoundOrEmpty(i);
                final String modid = blockState.getStringOr("Name", "").split(":")[0];
                mods.add(modid);
            }

            final ListTag requiredMods = new ListTag();
            for (final String str : mods)
            {
                requiredMods.add(StringTag.valueOf(str));
            }

            bluePrintCompound.put("palette", pallete);
            bluePrintCompound.put("required_mods", requiredMods);

            final MutableBlockPos pos = new MutableBlockPos();
            final short[][][] dataArray = new short[size[1]][size[2]][size[0]];

            if (addStructureVoid)
            {
                for (int i = 0; i < size[1]; i++)
                {
                    for (int j = 0; j < size[2]; j++)
                    {
                        for (int k = 0; k < size[0]; k++)
                        {
                            dataArray[i][j][k] = structureVoidID;
                        }
                    }
                }
            }

            final ListTag tileEntities = new ListTag();
            for (int i = 0; i < blocks.size(); i++)
            {
                final CompoundTag comp = blocks.getCompoundOrEmpty(i);
                updatePos(pos, comp);
                dataArray[pos.getY()][pos.getZ()][pos.getX()] = (short) comp.getIntOr("state", 0);
                if (comp.contains("nbt"))
                {
                    final CompoundTag te = comp.getCompoundOrEmpty("nbt");
                    te.putShort("x", (short) pos.getX());
                    te.putShort("y", (short) pos.getY());
                    te.putShort("z", (short) pos.getZ());
                    tileEntities.add(te);
                }
            }

            bluePrintCompound.putIntArray("blocks", convertBlocksToSaveData(dataArray, (short) size[0], (short) size[1], (short) size[2]));
            bluePrintCompound.put("tile_entities", tileEntities);
            bluePrintCompound.put("architects", new ListTag());
            bluePrintCompound.put("name", (StringTag.valueOf(input.toString().replaceAll("\\.nbt", ""))));
            bluePrintCompound.putInt("version", 1);

            final ListTag newEntities = new ListTag();
            if (blueprint.contains("entities"))
            {
                final ListTag entities = blueprint.getListOrEmpty("entities");
                for (int i = 0; i < entities.size(); i++)
                {
                    final CompoundTag entityData = entities.getCompoundOrEmpty(i);
                    final CompoundTag entity = entityData.getCompoundOrEmpty("nbt");
                    entity.put("Pos", entityData.getListOrEmpty("pos"));
                    newEntities.add(entity);
                }
            }
            bluePrintCompound.put("entities", newEntities);

            try (final OutputStream outputstream = new BufferedOutputStream(Files.newOutputStream(output)))
            {
                NbtIo.writeCompressed(bluePrintCompound, outputstream);
            }
            catch (final IOException e)
            {
                Log.getLogger().warn("Exception while trying to scan.", e);
            }
        }
        catch (final IOException e)
        {
            e.printStackTrace();
        }
    }

    private static Blueprint fixBluePrints(final Path input, final HolderLookup.Provider provider)
    {
        try
        {
            final CompoundTag compoundNBT = NbtIo.readCompressed(new ByteArrayInputStream(Files.readAllBytes(input)), NbtAccounter.unlimitedHeap());
            return readBlueprintFromNBT(compoundNBT, provider, input.getFileName().toString());
        }
        catch (Exception e)
        {
            Log.getLogger().warn("Could not read file:" + input.toString());
        }
        return null;
    }

    private static void updatePos(final MutableBlockPos pos, final CompoundTag comp)
    {
        final ListTag list = comp.getListOrEmpty("pos");
        pos.set(list.getIntOr(0, 0), list.getIntOr(1, 0), list.getIntOr(2, 0));
    }

}