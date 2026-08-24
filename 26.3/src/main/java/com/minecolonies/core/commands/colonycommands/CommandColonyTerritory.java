package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.ChunkClaimData;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.colony.savedata.IServerColonySaveData;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.territory.HostileTerritoryIndex;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.items.ItemScepterTerritory;
import com.minecolonies.core.util.ChunkDataHelper;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.colony.territory.HostileTerritoryMap;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.TeamColorArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.scores.TeamColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Makes, points at and erases hostile territories: ground marked as an enemy's, which every player is locked out of
 * and which draws in red.
 *
 * <h2>Why a command and not an item</h2>
 * Creating a territory is not a thing done in the world, it is a thing done to the world, and it has exactly the shape
 * a command has: one operator, once, with a name to give. An item would need somewhere to keep the name, would have to
 * be crafted or given before anything could happen at all, and would still have to be told which of several
 * territories it meant. Painting the ground afterwards <em>is</em> a thing done in the world, and that is
 * {@link ItemScepterTerritory}'s half — the split is between "declare that this enemy exists" and "draw where he is".
 *
 * <h2>What create actually does</h2>
 * It calls {@code IServerColonySaveData#createColony} directly and deliberately does <b>not</b> go on to
 * {@code ChunkDataHelper#claimColonyChunks}, which is the one thing {@code ColonyManager#createColony} adds and the one
 * thing a territory must not have: an ordinary colony is born owning the {@code initialColonySize} square around its
 * centre, and a territory has to start owning nothing so that the player draws every chunk of it by hand. The owner is
 * then set to {@code [abandoned]}, which leaves every player at {@code NEUTRAL} rank — nobody may build in it, nobody
 * may break in it, and nobody can be given permission to, which is exactly what "the enemy's ground" means.
 * <p>
 * The centre is the operator's own position. It has to be somewhere real ({@code Colony#getCenter} is final and read
 * by half the mod), it is the one chunk the scepter refuses to release, and it is where the territory is listed as
 * being in {@code /mc colony list}.
 */
public class CommandColonyTerritory implements IMCOPCommand
{
    /**
     * The name argument of {@code territory create}.
     */
    private static final String NAME_ARG = "name";

    /**
     * The centre argument of {@code territory create}.
     */
    private static final String POS_ARG = "pos";

    /**
     * The radius argument of {@code territory grow}.
     */
    private static final String RADIUS_ARG = "radius";

    /**
     * The colour argument of {@code territory create} and {@code territory colour}.
     */
    private static final String COLOUR_ARG = "colour";

    /**
     * The colours a territory may be given when nobody names one.
     * <p>
     * WHITE is absent on purpose: it is what the border renderer draws the player's <em>own</em> colony in, and it is
     * what an untouched colony's team colour already is, so it is both the wrong signal and the value that means
     * "never set". BLACK, DARK_GRAY and GRAY are absent because a border line is one pixel wide against whatever
     * ground it crosses, and those three lose against stone, shadow and rain. The twelve that remain are the ones
     * that read as a colour at a glance, which is the whole point of giving a territory one.
     */
    private static final List<ChatFormatting> PALETTE = List.of(
      ChatFormatting.RED,
      ChatFormatting.GOLD,
      ChatFormatting.YELLOW,
      ChatFormatting.GREEN,
      ChatFormatting.DARK_GREEN,
      ChatFormatting.AQUA,
      ChatFormatting.DARK_AQUA,
      ChatFormatting.BLUE,
      ChatFormatting.DARK_BLUE,
      ChatFormatting.LIGHT_PURPLE,
      ChatFormatting.DARK_PURPLE,
      ChatFormatting.DARK_RED);

    /**
     * Where the unnamed colours come from. Not seeded, and not required to be reproducible: two territories made in
     * the same session get different colours because {@link #pickColour} looks at what is already taken, not because
     * of anything this does.
     */
    private static final Random RANDOM = new Random();

    /**
     * The largest square {@code grow} will take in one go, in chunks either side of the centre.
     * <p>
     * Eight gives a 17x17 square, 289 chunks, which is under the 21x21 {@code /mc colony claim} already allows. The
     * limit is about synchronous chunk generation, not about ownership: claiming a chunk loads it, and on ground
     * nobody has visited that means generating it on the server thread.
     */
    private static final int MAX_GROW_RADIUS = 8;

    /**
     * Bare {@code /mc colony territory}: list what territories exist and how much ground each holds.
     * <p>
     * The chunk counts are read out of the published {@link HostileTerritory} index rather than off the colonies, so
     * this doubles as the way to see whether the outside-facing query agrees with what the world actually looks like.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final ServerLevel level = context.getSource().getLevel();
        final HostileTerritoryMap index = HostileTerritory.in(level.dimension());

        int found = 0;
        for (final IColony iColony : IColonyManager.getInstance().getColonies(level))
        {
            if (!(iColony instanceof final Colony colony) || !colony.isHostile())
            {
                continue;
            }

            found++;
            final int owned = countOwnedChunks(colony);
            final int seen = countIndexedChunks(index, colony);
            final ChatFormatting colour = colony.getTeamColonyColor();
            context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_TERRITORY_LISTED,
              colony.getName(),
              String.valueOf(colony.getID()),
              colony.getCenter().toShortString(),
              String.valueOf(owned),
              String.valueOf(seen),
              // WHITE is what a territory made before colours existed still has, and it is not a colour this draws
              // in -- the border falls back to red -- so it is named as what the player will actually see.
              colour == ChatFormatting.WHITE
                ? Component.literal("red").withStyle(ChatFormatting.RED)
                : Component.literal(colour.name().toLowerCase(Locale.ROOT)).withStyle(colour)), true);
        }

        if (found == 0)
        {
            context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_TERRITORY_NONE), true);
        }
        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_TERRITORY_USAGE), true);
        return 1;
    }

    /**
     * How many chunks a territory actually owns, read off the colony.
     *
     * @param colony the territory.
     * @return the count.
     */
    private static int countOwnedChunks(final Colony colony)
    {
        int owned = 0;
        for (final Long2ObjectMap.Entry<ChunkClaimData> entry : colony.getClaimData().long2ObjectEntrySet())
        {
            if (entry.getValue().getOwningColony() == colony.getID())
            {
                owned++;
            }
        }
        return owned;
    }

    /**
     * How many of those chunks the outside-facing index agrees are the territory's.
     * <p>
     * Reported next to the real count on purpose. The index is what {@link HostileTerritory} answers outside callers
     * with, it is rebuilt rather than edited, and the two numbers disagreeing is the one failure mode of that design
     * that would otherwise be invisible from in game.
     *
     * @param index  the dimension's index, possibly null.
     * @param colony the territory.
     * @return the count.
     */
    private static int countIndexedChunks(@Nullable final HostileTerritoryMap index, final Colony colony)
    {
        if (index == null)
        {
            return 0;
        }

        int seen = 0;
        for (final Long2ObjectMap.Entry<ChunkClaimData> entry : colony.getClaimData().long2ObjectEntrySet())
        {
            if (entry.getValue().getOwningColony() != colony.getID())
            {
                continue;
            }

            final long key = entry.getLongKey();
            if (index.chunkTerritory(ChunkPos.getX(key), ChunkPos.getZ(key)) == colony.getID())
            {
                seen++;
            }
        }
        return seen;
    }

    /**
     * Make a new hostile territory, centred on the command source unless a position is given.
     * <p>
     * The source rather than a player, so this runs from the server console too: a territory is made <em>somewhere</em>
     * rather than <em>by somebody</em>, and the only thing the player adds is a scepter to bind.
     *
     * @param context the context of the command execution.
     * @param at      where to centre it.
     * @return the command status.
     */
    private int onCreate(final CommandContext<CommandSourceStack> context, final BlockPos at, @Nullable final ChatFormatting colour)
    {
        final ServerLevel level = context.getSource().getLevel();
        final IServerColonySaveData saveData = IServerColonySaveData.getOrComputeSaveData(level);
        if (saveData == null)
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_FAILED));
            return 0;
        }

        final String name = StringArgumentType.getString(context, NAME_ARG);
        final BlockPos centre = at;

        final IColony created = saveData.createColony(level, name, centre);
        if (!(created instanceof final Colony colony))
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_FAILED));
            return 0;
        }

        colony.setName(name);
        // Abandoned rather than owned by the operator who made it: an owner would be a colony manager there, and a
        // territory whose creator may build in it is not hostile to anybody.
        colony.getPermissions().setOwnerAbandoned();
        colony.setHostile(true);

        // Given a colour whether or not one was asked for. Territories all drew in the same red before this, so a
        // second one was ground you could see but not tell from the first; the colour is the only thing on the map
        // that says which enemy this is.
        final ChatFormatting chosen = colour != null ? colour : pickColour(level);
        colony.setColonyColor(chosen);

        Log.getLogger().info("New hostile territory id {} named {} at {} drawn in {}", colony.getID(), name, centre, chosen.name());

        if (context.getSource().getEntity() instanceof final ServerPlayer player)
        {
            bindHeldScepter(player, colony);
        }

        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_TERRITORY_CREATED,
          colony.getName(),
          String.valueOf(colony.getID()),
          centre.toShortString(),
          Component.literal(chosen.name().toLowerCase(Locale.ROOT)).withStyle(chosen)), true);
        return 1;
    }

    /**
     * Recolour a territory that already exists.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    private int onColour(final CommandContext<CommandSourceStack> context)
    {
        final IColony iColony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(iColony instanceof final Colony colony))
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_FAILED));
            return 0;
        }

        // Refused on an ordinary colony rather than quietly working: a colony's colour is its owner's to set in the
        // town hall, and an operator changing it from here would be editing somebody's town without being in it.
        if (!colony.isHostile())
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_NOT_A_TERRITORY, colony.getName()));
            return 0;
        }

        final ChatFormatting colour = colourArgument(context);
        colony.setColonyColor(colour);
        colony.markDirty();

        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_TERRITORY_RECOLOURED,
          colony.getName(),
          String.valueOf(colony.getID()),
          Component.literal(colour.name().toLowerCase(Locale.ROOT)).withStyle(colour)), true);
        return 1;
    }

    /**
     * Read the colour argument and turn it into the {@link ChatFormatting} the colony stores.
     * <p>
     * Matched by the colour's actual value rather than by enum name, so the two enums are allowed to diverge: vanilla
     * owns {@link TeamColor} and this mod does not. The sixteen team colours are exactly the sixteen legacy formatting
     * colours, so there is always a match, and the fallback is only there because the compiler cannot know that.
     *
     * @param context the context of the command execution.
     * @return the formatting colour.
     */
    private static ChatFormatting colourArgument(final CommandContext<CommandSourceStack> context)
    {
        final int rgb = TeamColorArgument.getTeamColor(context, COLOUR_ARG).rgb();
        for (final ChatFormatting formatting : ChatFormatting.values())
        {
            final TextColor colour = TextColor.fromLegacyFormat(formatting);
            if (colour != null && colour.getValue() == rgb)
            {
                return formatting;
            }
        }
        return ChatFormatting.RED;
    }

    /**
     * Choose a colour for a territory nobody named one for.
     * <p>
     * Colours already worn by another territory in this dimension are skipped, so the first twelve territories are
     * all told apart on sight without anybody having to think about it. Past twelve there is nothing left to give and
     * it repeats, which is the honest outcome: the alternative is inventing colours outside the sixteen the border
     * renderer can draw.
     *
     * @param level the dimension the territory is being made in.
     * @return the colour.
     */
    private static ChatFormatting pickColour(final ServerLevel level)
    {
        final Set<ChatFormatting> taken = new HashSet<>();
        for (final IColony iColony : IColonyManager.getInstance().getColonies(level))
        {
            if (iColony instanceof final Colony colony && colony.isHostile())
            {
                taken.add(colony.getTeamColonyColor());
            }
        }

        final List<ChatFormatting> free = new ArrayList<>(PALETTE);
        free.removeAll(taken);

        final List<ChatFormatting> from = free.isEmpty() ? PALETTE : free;
        return from.get(RANDOM.nextInt(from.size()));
    }

    /**
     * Point the held territory scepter at an existing territory.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    private int onBind(final CommandContext<CommandSourceStack> context)
    {
        if (!(context.getSource().getEntity() instanceof final ServerPlayer player))
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_NO_PLAYER));
            return 0;
        }

        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (colony == null)
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_FAILED));
            return 0;
        }

        if (!bindHeldScepter(player, colony))
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_NO_SCEPTER));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_TERRITORY_BOUND,
          colony.getName(),
          String.valueOf(colony.getID())), true);
        return 1;
    }

    /**
     * Take a square of chunks for a territory in one go.
     * <p>
     * The bulk form of what the Territory Scepter does one click at a time, and the only way to make a territory at
     * all without a client. It is the same call the scepter makes, with the same rule: {@code forceOwnerChange} false,
     * so a chunk somebody already owns is left alone and counted rather than taken.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    private int onGrow(final CommandContext<CommandSourceStack> context)
    {
        final IColony iColony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(iColony instanceof final Colony colony) || colony.getWorld() == null)
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_FAILED));
            return 0;
        }

        final ServerLevel level = colony.getWorld();
        final int radius = Math.min(MAX_GROW_RADIUS, IntegerArgumentType.getInteger(context, RADIUS_ARG));
        final ChunkPos centre = ChunkPos.containing(colony.getCenter());

        int claimed = 0;
        int skipped = 0;
        for (int x = centre.x() - radius; x <= centre.x() + radius; x++)
        {
            for (int z = centre.z() - radius; z <= centre.z() + radius; z++)
            {
                final ChunkPos chunkPos = new ChunkPos(x, z);
                final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
                final int owner = claimData == null ? NO_COLONY_ID : claimData.getOwningColony();
                if (owner == colony.getID())
                {
                    continue;
                }
                if (owner != NO_COLONY_ID)
                {
                    skipped++;
                    continue;
                }

                ChunkDataHelper.tryClaim(level, chunkPos.getWorldPosition(), true, colony, false);
                claimed++;
            }
        }
        colony.markDirty();

        final int took = claimed;
        final int left = skipped;
        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_TERRITORY_GREW,
          colony.getName(),
          String.valueOf(took),
          String.valueOf(left)), true);
        return 1;
    }

    /**
     * Erase a territory: give every chunk of it back, then delete the colony behind it.
     * <p>
     * The claim has to go first and by hand. {@code ColonyManager#deleteColony} releases the
     * {@code initialColonySize} square around the centre, which is the ground an ordinary colony was given at birth —
     * a territory was given none of it and owns instead whatever the player painted, which may be nowhere near the
     * centre. Left behind, those chunks would keep answering with an id that no longer resolves to anything: the
     * border would still draw and the ground would still be protected, by a colony that is gone.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    private int onDelete(final CommandContext<CommandSourceStack> context)
    {
        final IColony iColony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (!(iColony instanceof final Colony colony) || colony.getWorld() == null)
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_FAILED));
            return 0;
        }

        if (!colony.isHostile())
        {
            // Ordinary colonies are deleted with /mc colony delete, which asks whether to tear the buildings down.
            // Refusing here rather than forwarding keeps this from being a second, quieter way to lose a town.
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_TERRITORY_NOT_A_TERRITORY, colony.getName()));
            return 0;
        }

        final ServerLevel level = colony.getWorld();
        final int released = releaseAllGround(level, colony);
        IColonyManager.getInstance().deleteColonyByWorld(colony.getID(), false, level);

        // The index is republished from Colony#markDirty, which only does it for a colony that is still flagged
        // hostile -- and by here this one is not merely unflagged, it is gone, so nothing above has rebuilt it.
        // Without this the erased territory keeps answering HostileTerritory.at long after its border stopped
        // drawing: measured on a server, /mc colony raid <colony> now territory still found "Blackreach" a minute
        // after Blackreach had been deleted and the listing said the dimension had no territories left.
        HostileTerritoryIndex.refresh(level);

        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_TERRITORY_DELETED,
          colony.getName(),
          String.valueOf(released)), true);
        return 1;
    }

    /**
     * Give every chunk a territory holds back to nobody.
     *
     * @param level  the level.
     * @param colony the territory.
     * @return how many chunks were released.
     */
    private static int releaseAllGround(final ServerLevel level, final Colony colony)
    {
        // Copied out first: tryClaim edits the very map being walked.
        final LongList owned = new LongArrayList();
        for (final Long2ObjectMap.Entry<ChunkClaimData> entry : colony.getClaimData().long2ObjectEntrySet())
        {
            if (entry.getValue().getOwningColony() == colony.getID())
            {
                owned.add(entry.getLongKey());
            }
        }

        for (final long key : owned)
        {
            final ChunkPos chunkPos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            ChunkDataHelper.tryClaim(level, chunkPos.getWorldPosition(), false, colony, false);

            final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
            if (claimData != null && claimData.getOwningColony() == NO_COLONY_ID && claimData.hasPartialClaim())
            {
                claimData.clearPartialClaim(level.getChunk(chunkPos.x(), chunkPos.z()));
            }
        }
        return owned.size();
    }

    /**
     * Write a colony onto whichever territory scepter the player is holding, if any.
     *
     * @param player the player.
     * @param colony the colony to bind to.
     * @return true if a scepter was found and bound.
     */
    private static boolean bindHeldScepter(final ServerPlayer player, final IColony colony)
    {
        for (final InteractionHand hand : InteractionHand.values())
        {
            final ItemStack stack = player.getItemInHand(hand);
            if (ItemScepterTerritory.isTerritoryScepter(stack))
            {
                ItemScepterTerritory.bind(stack, colony);
                return true;
            }
        }
        return false;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "territory";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newLiteral("create")
                         .then(IMCCommand.newArgument(NAME_ARG, StringArgumentType.string())
                                 .then(IMCCommand.newArgument(POS_ARG, BlockPosArgument.blockPos())
                                         .then(IMCCommand.newArgument(COLOUR_ARG, TeamColorArgument.teamColor())
                                                 .executes(ctx -> checkPreConditionAndExecute(ctx,
                                                   c -> onCreate(c, BlockPosArgument.getBlockPos(c, POS_ARG), colourArgument(c)))))
                                         .executes(ctx -> checkPreConditionAndExecute(ctx,
                                           c -> onCreate(c, BlockPosArgument.getBlockPos(c, POS_ARG), null))))
                                 // A colour where a position could also stand. A position is three numbers and a
                                 // colour is a word, so the two never both parse, and brigadier takes whichever does.
                                 .then(IMCCommand.newArgument(COLOUR_ARG, TeamColorArgument.teamColor())
                                         .executes(ctx -> checkPreConditionAndExecute(ctx,
                                           c -> onCreate(c, BlockPos.containing(c.getSource().getPosition()), colourArgument(c)))))
                                 .executes(ctx -> checkPreConditionAndExecute(ctx,
                                   c -> onCreate(c, BlockPos.containing(c.getSource().getPosition()), null)))))
                 .then(IMCCommand.newLiteral("colour")
                         .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                                 .then(IMCCommand.newArgument(COLOUR_ARG, TeamColorArgument.teamColor())
                                         .executes(ctx -> checkPreConditionAndExecute(ctx, this::onColour)))))
                 .then(IMCCommand.newLiteral("color")
                         .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                                 .then(IMCCommand.newArgument(COLOUR_ARG, TeamColorArgument.teamColor())
                                         .executes(ctx -> checkPreConditionAndExecute(ctx, this::onColour)))))
                 .then(IMCCommand.newLiteral("grow")
                         .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                                 .then(IMCCommand.newArgument(RADIUS_ARG, IntegerArgumentType.integer(0, MAX_GROW_RADIUS))
                                         .executes(ctx -> checkPreConditionAndExecute(ctx, this::onGrow)))))
                 .then(IMCCommand.newLiteral("bind")
                         .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                                 .executes(ctx -> checkPreConditionAndExecute(ctx, this::onBind))))
                 .then(IMCCommand.newLiteral("delete")
                         .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                                 .executes(ctx -> checkPreConditionAndExecute(ctx, this::onDelete))))
                 .executes(this::checkPreConditionAndExecute);
    }
}
