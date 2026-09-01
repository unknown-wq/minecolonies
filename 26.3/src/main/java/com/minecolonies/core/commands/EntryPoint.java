package com.minecolonies.core.commands;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.HeadlessColonyMode;
import com.minecolonies.core.commands.citizencommands.*;
import com.minecolonies.core.commands.colonycommands.*;
import com.minecolonies.core.commands.colonycommands.requestsystem.CommandRSReset;
import com.minecolonies.core.commands.colonycommands.requestsystem.CommandRSResetAll;
import com.minecolonies.core.commands.generalcommands.*;
import com.minecolonies.core.commands.killcommands.*;
import com.minecolonies.core.debug.command.CommandToggleDebug;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Entry point to commands.
 */
public class EntryPoint
{

    private EntryPoint()
    {
        // Intentionally left empty
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher)
    {
        /*
         * Kill commands subtree
         */
        final CommandTree killCommands = new CommandTree("kill")
            .addNode(new CommandKillAnimal().build())
            .addNode(new CommandKillChicken().build())
            .addNode(new CommandKillCow().build())
            .addNode(new CommandKillMonster().build())
            .addNode(new CommandKillPig().build())
            .addNode(new CommandKillRaider().build())
            .addNode(new CommandKillSheep().build());

        /*
         * Colony commands subtree
         */
        final CommandTree colonyCommands = new CommandTree("colony")
            .addNode(new CommandAddOfficer().build())
            .addNode(new CommandSetRank().build())
            .addNode(new CommandChangeOwner().build())
            .addNode(new CommandClaimChunks().build())
            .addNode(new CommandShowClaim().build())
            .addNode(new CommandTeleport().build())
            .addNode(new CommandDeleteColony().build())
            .addNode(new CommandCanRaiderSpawn().build())
            .addNode(new CommandRaid().build())
            .addNode(new CommandHomeTeleport().build())
            .addNode(new CommandListColonies().build())
            .addNode(new CommandSetDeletable().build())
            .addNode(new CommandReclaimChunks().build())
            .addNode(new CommandLoadBackup().build())
            .addNode(new CommandLoadAllBackups().build())
            .addNode(new CommandColonyInfo().build())
            .addNode(new CommandColonyDiagnose().build())
            .addNode(new CommandColonyPrintStats().build())
            .addNode(new CommandColonyResetStats().build())
            .addNode(new CommandColonyGrowChildren().build())
            .addNode(new CommandColonyRaidsInfo().build())
            .addNode(new CommandColonyChunks().build())
            .addNode(new CommandForceLoadClaims().build())
            .addNode(new CommandColonyResearch().build())
            .addNode(new CommandColonyTeachRecipes().build())
            .addNode(new CommandColonyFreeMode().build())
            .addNode(new CommandColonyProtection().build())
            .addNode(new CommandColonyBlastProtection().build())
            .addNode(new CommandColonyWarehouseStock().build())
            .addNode(new CommandColonyAntiAir().build())
            .addNode(new CommandColonyFieldSeeds().build())
            .addNode(new CommandColonyRehouse().build())
            .addNode(new CommandColonyRepairAll().build())
            .addNode(new CommandColonyRestoreHuts().build())
            .addNode(new CommandColonyKeepBuildings().build())
            .addNode(new CommandColonyWorkOverride().build())
            .addNode(new CommandRSReset().build())
            .addNode(new CommandRSResetAll().build())
            .addNode(new CommandSetAbandoned().build())
            .addNode(new CommandColonyTerritory().build())
            .addNode(new CommandColonyCamp().build())
            .addNode(new CommandColonyFound().build())
            .addNode(new CommandColonyHut().build())
            .addNode(new CommandColonyBuildNow().build())
            .addNode(new CommandExportColony().build());

        /*
         * Citizen commands subtree
         */
        final CommandTree citizenCommands = new CommandTree("citizens")
            .addNode(new CommandCitizenFill().build())
            .addNode(new CommandCitizenFire().build())
            .addNode(new CommandCitizenHire().build())
            .addNode(new CommandCitizenHeal().build())
            .addNode(new CommandCitizenInfo().build())
            .addNode(new CommandCitizenKill().build())
            .addNode(new CommandCitizenList().build())
            .addNode(new CommandCitizenMaxStats().build())
            .addNode(new CommandCitizenModify().build())
            .addNode(new CommandCitizenReload().build())
            .addNode(new CommandCitizenSpawnNew().build())
            .addNode(new CommandCitizenTeleport().build())
            .addNode(new CommandCitizenTriggerWalkTo().build())
            .addNode(new CommandCitizenTrack().build())
            .addNode(new CommandTrackType().build());

        /*
         * Debug commands subtree. Operator knobs that exist to be turned while watching a running server, rather than
         * settings a server is configured with.
         */
        final CommandTree debugCommands = new CommandTree("debug")
            .addNode(new CommandMaxPool().build());

        // Headless colony mode is the one thing here that changes what the mod does rather than how fast it does it,
        // so it is not offered unless the JVM was started asking for it. On every other install the literal is absent
        // from the tree: it cannot be run, completed or mistyped into, and there is no other way to reach the mode.
        if (HeadlessColonyMode.isArmed())
        {
            debugCommands.addNode(new CommandHeadless().build());
        }

        /*
         * Root minecolonies command tree, all subtrees are added here.
         */
        final CommandTree minecoloniesRoot = new CommandTree(Constants.MOD_ID)
            .addNode(killCommands)
            .addNode(colonyCommands)
            .addNode(new CommandHomeTeleport().build())
            .addNode(citizenCommands)
            .addNode(new CommandWhereAmI().build())
            .addNode(new CommandAircraft().build())
            .addNode(new CommandWhoAmI().build())
            .addNode(new CommandGetRanks().build())
            .addNode(new CommandUnloadForcedChunks().build())
            .addNode(new CommandBackup().build())
            .addNode(new CommandResetPlayerSupplies().build())
            .addNode(new CommandHelp().build())
            .addNode(new CommandPathStats().build())
            .addNode(new CommandBoatSpeed().build())
            .addNode(debugCommands)
            .addNode(ScanCommand.build())
            .addNode(new CommandPruneWorld().build());

        /*
         * Root minecolonies alias command tree, all subtrees are added here.
         */
        final CommandTree minecoloniesRootAlias = new CommandTree("mc")
            .addNode(new CommandEntityTrack().build())
            .addNode(killCommands)
            .addNode(colonyCommands)
            .addNode(new CommandHomeTeleport().build())
            .addNode(citizenCommands)
            .addNode(new CommandWhereAmI().build())
            .addNode(new CommandAircraft().build())
            .addNode(new CommandWhoAmI().build())
            .addNode(new CommandGetRanks().build())
            .addNode(new CommandUnloadForcedChunks().build())
            .addNode(new CommandBackup().build())
            .addNode(new CommandResetPlayerSupplies().build())
            .addNode(new CommandHelp().build())
            .addNode(new CommandPathStats().build())
            .addNode(new CommandBoatSpeed().build())
            .addNode(debugCommands)
            .addNode(new CommandToggleDebug().build())
            .addNode(new CommandPruneWorld().build());

        // Adds all command trees to the dispatcher to register the commands.
        dispatcher.register(minecoloniesRoot.build());
        dispatcher.register(minecoloniesRootAlias.build());
    }
}
