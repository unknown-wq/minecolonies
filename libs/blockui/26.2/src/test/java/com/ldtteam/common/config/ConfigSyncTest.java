package com.ldtteam.common.config;

import com.ldtteam.common.config.ConfigValue.BooleanValue;
import com.ldtteam.common.config.ConfigValue.DoubleValue;
import com.ldtteam.common.config.ConfigValue.EnumValue;
import com.ldtteam.common.config.ConfigValue.IntValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cover for the restored server -&gt; client config sync: the wire round trip, the guarantee that a synced value
 * never reaches the client's own file, the revert on disconnect, and every version-mismatch case.
 * <p>
 * Like {@link ConfigStoreTest}, the values are minted directly instead of through {@link AbstractConfiguration},
 * which would drag in {@code LanguageHandler} and therefore {@code FabricLoader}. That is also why
 * {@link ConfigSync} was kept free of any game type: everything below is the exact code the network message
 * runs, minus the message.
 */
class ConfigSyncTest
{
    enum Difficulty
    {
        EASY,
        NORMAL,
        HARD
    }

    /**
     * One side's SERVER configuration - the same shape on both sides unless a test says otherwise.
     */
    private record Side(ConfigStore store,
        BooleanValue raids,
        IntValue colonySize,
        DoubleValue guardDamage,
        ConfigValue<String> motd,
        EnumValue<Difficulty> difficulty,
        ConfigValue<List<? extends String>> allowedDims)
    {
        List<ConfigValue<?>> values()
        {
            return List.of(raids, colonySize, guardDamage, motd, difficulty, allowedDims);
        }
    }

    private static Side side(final Path configDir)
    {
        final ConfigStore store = new ConfigStore(ConfigStore.Type.SERVER, configDir);
        store.bindModId("minecolonies");

        final BooleanValue raids = new BooleanValue("raids", "t", null, true);
        final IntValue colonySize = new IntValue("gameplay.colonySize", "t", null, 20, 1, 100);
        final DoubleValue guardDamage = new DoubleValue("gameplay.guardDamage", "t", null, 1.0, 0.0, 10.0);
        final ConfigValue<String> motd = new ConfigValue<>("motd", "t", null, "local");
        final EnumValue<Difficulty> difficulty = new EnumValue<>("gameplay.difficulty", "t", null, Difficulty.NORMAL);
        final ConfigValue<List<? extends String>> allowedDims =
            new ConfigValue<>("gameplay.dims", "t", null, List.of("overworld"));

        final Side result = new Side(store, raids, colonySize, guardDamage, motd, difficulty, allowedDims);
        result.values().forEach(store::register);
        return result;
    }

    /**
     * The whole hop, minus the packet: the server renders its values, the client applies the document.
     */
    private static ConfigSync.Outcome hop(final Side server, final Side client)
    {
        return ConfigSync.apply(client.values(), ConfigSync.encode(server.values()));
    }

    @Test
    void everyTypeSurvivesTheRoundTrip(@TempDir final Path serverDir, @TempDir final Path clientDir)
    {
        final Side server = side(serverDir);
        final Side client = side(clientDir);

        server.raids().set(false);
        server.colonySize().set(75);
        server.guardDamage().set(2.5);
        server.motd().set("from the server");
        server.difficulty().set(Difficulty.HARD);
        server.allowedDims().set(List.of("overworld", "the_nether"));

        final ConfigSync.Outcome outcome = hop(server, client);

        assertTrue(outcome.problems().isEmpty(), () -> outcome.problems().toString());
        assertEquals(6, outcome.applied());
        assertEquals(0, outcome.missingLocally());
        assertEquals(0, outcome.missingRemotely());

        assertEquals(false, client.raids().get());
        assertEquals(75, client.colonySize().get());
        assertEquals(2.5, client.guardDamage().get());
        assertEquals("from the server", client.motd().get());
        assertEquals(Difficulty.HARD, client.difficulty().get());
        assertEquals(List.of("overworld", "the_nether"), client.allowedDims().get());
    }

    @Test
    void onlyTheValuesThatActuallyChangedAreReportedForTheWatchers(@TempDir final Path serverDir,
        @TempDir final Path clientDir)
    {
        final Side server = side(serverDir);
        final Side client = side(clientDir);

        // the server differs in exactly one setting; every other value is identical to the client's
        server.colonySize().set(42);

        final ConfigSync.Outcome outcome = hop(server, client);

        assertEquals(List.of(client.colonySize()), outcome.changed(),
            "a watcher must fire for the value that changed, and only for it");
        assertEquals(6, outcome.applied(), "all six are still taken from the server");
    }

    @Test
    void aSyncedValueIsNeverWrittenToTheClientsOwnFile(@TempDir final Path serverDir, @TempDir final Path clientDir)
        throws IOException
    {
        final Side server = side(serverDir);
        final Side client = side(clientDir);

        client.store().load();
        client.colonySize().set(7);
        client.colonySize().save();
        client.store().flush();

        server.colonySize().set(99);
        hop(server, client);

        assertEquals(99, client.colonySize().get(), "the server's value is what game code sees");

        // anything at all can flush the store while we are connected - a settings screen, the shutdown hook
        client.motd().set("edited while connected");
        client.motd().save();
        client.store().flush();

        final Map<String, Object> onDisk = readBack(client.store().getFile());
        assertEquals(7L, onDisk.get("gameplay.colonySize"),
            "the client's own file must keep the client's own value, or disconnecting would leave the player "
                + "with whatever server they last joined");
        assertEquals("edited while connected", onDisk.get("motd"));
    }

    @Test
    void disconnectingRestoresTheLocalValues(@TempDir final Path serverDir, @TempDir final Path clientDir)
    {
        final Side server = side(serverDir);
        final Side client = side(clientDir);

        client.colonySize().set(7);
        client.difficulty().set(Difficulty.EASY);
        server.colonySize().set(99);
        server.difficulty().set(Difficulty.EASY); // identical on both sides on purpose

        hop(server, client);
        assertEquals(99, client.colonySize().get());
        assertTrue(client.colonySize().isSynced());

        final List<ConfigValue<?>> changed = ConfigSync.revert(client.values());

        assertEquals(7, client.colonySize().get(), "back on the player's own setting");
        assertEquals(Difficulty.EASY, client.difficulty().get());
        assertFalse(client.colonySize().isSynced());
        assertEquals(List.of(client.colonySize()), changed,
            "only the value whose answer changed needs a watcher; difficulty was the same on both sides");
    }

    @Test
    void rejoiningADifferentServerLeavesNothingStaleBehind(@TempDir final Path firstDir,
        @TempDir final Path secondDir,
        @TempDir final Path clientDir)
    {
        final Side first = side(firstDir);
        final Side second = side(secondDir);
        final Side client = side(clientDir);

        client.colonySize().set(7);
        first.colonySize().set(99);
        first.motd().set("first server");
        hop(first, client);

        // the second server is an older build that does not declare colonySize at all
        final String document = ConfigSync.encode(second.values())
            .lines()
            .filter(line -> !line.startsWith("gameplay.colonySize"))
            .reduce("", (a, b) -> a + b + "\n");
        second.motd().set("second server");

        final ConfigSync.Outcome outcome = ConfigSync.apply(client.values(), document);

        assertEquals(7, client.colonySize().get(),
            "a key the new server does not know falls back to the local value, not to the old server's");
        assertFalse(client.colonySize().isSynced());
        assertEquals(1, outcome.missingRemotely());
    }

    @Test
    void aKeyOnlyTheServerHasIsIgnored(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);

        final ConfigSync.Outcome outcome = ConfigSync.apply(client.values(), """
            raids = false
            aSettingFromANewerVersion = 5
            gameplay.andAnotherOne = "hello"
            """);

        assertEquals(false, client.raids().get());
        assertEquals(2, outcome.missingLocally());
        assertEquals(1, outcome.problems().size(), () -> outcome.problems().toString());
        assertTrue(outcome.problems().get(0).contains("aSettingFromANewerVersion"), outcome.problems().toString());
    }

    @Test
    void aKeyOnlyTheClientHasKeepsItsLocalValue(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);
        client.motd().set("mine");

        final ConfigSync.Outcome outcome = ConfigSync.apply(client.values(), "raids = false\n");

        assertEquals("mine", client.motd().get());
        assertFalse(client.motd().isSynced());
        assertEquals(1, outcome.applied());
        assertEquals(5, outcome.missingRemotely());
    }

    @Test
    void aTypeMismatchKeepsTheLocalValueInsteadOfThrowing(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);
        client.colonySize().set(7);

        // the server declares colonySize as a string, and difficulty as a constant we do not have
        final ConfigSync.Outcome outcome = ConfigSync.apply(client.values(), """
            gameplay.colonySize = "twenty"
            gameplay.difficulty = "LUDICROUS"
            """);

        assertEquals(7, client.colonySize().get());
        assertFalse(client.colonySize().isSynced());
        assertEquals(Difficulty.NORMAL, client.difficulty().get());
        assertEquals(2, outcome.problems().size(), () -> outcome.problems().toString());
    }

    @Test
    void aNumberIsStillAcceptedForAStringDeclaredKey(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);

        final ConfigSync.Outcome outcome = ConfigSync.apply(client.values(), "motd = 12\n");

        assertEquals("12", client.motd().get());
        assertTrue(outcome.problems().isEmpty(), () -> outcome.problems().toString());
    }

    @Test
    void aValueOutsideThisSidesRangeIsClampedNotRejected(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);

        // a server whose build allows a bigger colony than ours does
        ConfigSync.apply(client.values(), "gameplay.colonySize = 5000\n");

        assertEquals(100, client.colonySize().get());
    }

    @Test
    void anUnreadableDocumentLeavesEveryValueAlone(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);
        client.colonySize().set(7);

        final ConfigSync.Outcome outcome = ConfigSync.apply(client.values(), "\0\0 ]]] not toml [[[ = = =\n");

        assertEquals(7, client.colonySize().get());
        assertEquals(0, outcome.applied());
        assertFalse(outcome.problems().isEmpty());
    }

    @Test
    void anEmptyDocumentIsNotAnError(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);
        client.motd().set("mine");

        final ConfigSync.Outcome outcome = ConfigSync.apply(client.values(), "");

        assertEquals("mine", client.motd().get());
        assertEquals(0, outcome.applied());
        assertTrue(outcome.problems().isEmpty());
    }

    @Test
    void theServerSendsItsOwnValuesEvenWhileItIsItselfSynced(@TempDir final Path hostDir, @TempDir final Path guestDir)
    {
        // a LAN host that still carries an overlay from a server it visited earlier must ship its own settings,
        // not that server's - encode reads the local value for exactly this reason
        final Side host = side(hostDir);
        final Side guest = side(guestDir);

        host.colonySize().set(30);
        host.colonySize().applySync(80);

        ConfigSync.apply(guest.values(), ConfigSync.encode(host.values()));

        assertEquals(30, guest.colonySize().get());
    }

    @Test
    void aValueThatIsNotSyncedReportsSoAndOneThatIsSaysSo(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);

        assertFalse(ConfigSync.isAnySynced(client.values()));
        ConfigSync.apply(client.values(), "raids = false\n");
        assertTrue(ConfigSync.isAnySynced(client.values()));
        assertTrue(client.raids().isSynced());
        assertFalse(client.motd().isSynced());
    }

    @Test
    void editingALocalValueWhileSyncedDoesNotDisturbTheServersValue(@TempDir final Path clientDir)
    {
        final Side client = side(clientDir);

        ConfigSync.apply(client.values(), "gameplay.colonySize = 99\n");
        client.colonySize().set(3);

        assertEquals(99, client.colonySize().get(), "the server still wins while we are connected");
        ConfigSync.revert(client.values());
        assertEquals(3, client.colonySize().get(), "and the edit is what we come back to");
    }

    private static Map<String, Object> readBack(final Path file) throws IOException
    {
        assertNotNull(file);
        final List<String> problems = new ArrayList<>();
        final Map<String, Object> parsed = FlatToml.parse(Files.readString(file, StandardCharsets.UTF_8), problems);
        assertTrue(problems.isEmpty(), () -> "the store wrote something it cannot read: " + problems);
        return parsed;
    }
}
