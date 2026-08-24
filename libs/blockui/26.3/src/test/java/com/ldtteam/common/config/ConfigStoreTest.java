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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end cover for the persistence that {@code ConfigValue#save()} used to be missing.
 * <p>
 * The values are minted directly rather than through {@link AbstractConfiguration}, because that route goes
 * through {@code LanguageHandler} and therefore through {@code FabricLoader}, which does not exist outside a
 * running game. The store, the coercion and the file format - everything that was actually added - are exercised
 * exactly as they are in production.
 */
class ConfigStoreTest
{
    enum Mode
    {
        OFF,
        ON,
        AUTO
    }

    private record Fixture(ConfigStore store,
        BooleanValue flag,
        IntValue count,
        DoubleValue ratio,
        ConfigValue<String> name,
        EnumValue<Mode> mode,
        ConfigValue<List<? extends String>> list)
    {}

    private static Fixture fixture(final Path configDir)
    {
        final ConfigStore store = new ConfigStore(ConfigStore.Type.COMMON, configDir);
        store.bindModId("testmod");

        final BooleanValue flag = new BooleanValue("flag", "t", "Root level flag.", true);
        final IntValue count = new IntValue("gui.count", "t", "How many.", 5, 0, 10);
        final DoubleValue ratio = new DoubleValue("gui.ratio", "t", null, 0.5, 0.0, 1.0);
        final ConfigValue<String> name = new ConfigValue<>("gui.name", "t", "A name.", "default");
        final EnumValue<Mode> mode = new EnumValue<>("gui.advanced.mode", "t", "A mode.", Mode.OFF);
        final ConfigValue<List<? extends String>> list =
            new ConfigValue<>("gui.advanced.list", "t", null, List.of("a", "b"));

        for (final ConfigValue<?> value : List.of(flag, count, ratio, name, mode, list))
        {
            store.register(value);
        }

        return new Fixture(store, flag, count, ratio, name, mode, list);
    }

    private static Map<String, Object> readBack(final Path file) throws IOException
    {
        final List<String> problems = new ArrayList<>();
        final Map<String, Object> parsed = FlatToml.parse(Files.readString(file, StandardCharsets.UTF_8), problems);
        assertTrue(problems.isEmpty(), () -> "the store wrote something it cannot read: " + problems);
        return parsed;
    }

    @Test
    void aMissingFileLeavesTheDefaultsAndMaterialisesThem(@TempDir final Path configDir) throws IOException
    {
        final Fixture f = fixture(configDir);
        f.store().load();
        f.store().flush();

        assertEquals(true, f.flag().get());
        assertEquals(5, f.count().get());
        assertEquals(Mode.OFF, f.mode().get());

        final Path file = f.store().getFile();
        assertNotNull(file);
        assertEquals(configDir.resolve("testmod-common.toml"), file);
        assertTrue(Files.isRegularFile(file), "the defaults should have been written out");

        final Map<String, Object> written = readBack(file);
        assertEquals(Boolean.TRUE, written.get("flag"));
        assertEquals(5L, written.get("gui.count"));
        assertEquals("OFF", written.get("gui.advanced.mode"));
        assertEquals(List.of("a", "b"), written.get("gui.advanced.list"));
    }

    @Test
    void loadsEveryTypeOffDisk(@TempDir final Path configDir) throws IOException
    {
        Files.writeString(configDir.resolve("testmod-common.toml"), """
            flag = false

            \t[gui]
            \t\tcount = 9
            \t\tratio = 0.25
            \t\tname = "from disk"

            \t[gui.advanced]
            \t\tmode = "AUTO"
            \t\tlist = ["x", "y", "z"]
            """, StandardCharsets.UTF_8);

        final Fixture f = fixture(configDir);
        f.store().load();

        assertEquals(false, f.flag().get());
        assertEquals(9, f.count().get());
        assertEquals(0.25, f.ratio().get());
        assertEquals("from disk", f.name().get());
        assertEquals(Mode.AUTO, f.mode().get());
        assertEquals(List.of("x", "y", "z"), f.list().get());
    }

    @Test
    void savingAValueWritesItBackAndItSurvivesAReload(@TempDir final Path configDir) throws IOException
    {
        final Fixture first = fixture(configDir);
        first.store().load();

        first.count().set(8);
        first.count().save();
        first.mode().set(Mode.ON);
        first.mode().save();
        first.store().flush();

        // a completely fresh tree over the same directory, i.e. a game restart
        final Fixture second = fixture(configDir);
        second.store().load();

        assertEquals(8, second.count().get());
        assertEquals(Mode.ON, second.mode().get());
    }

    @Test
    void garbageAndUnknownKeysFallBackToTheDefaultWithoutThrowing(@TempDir final Path configDir) throws IOException
    {
        Files.writeString(configDir.resolve("testmod-common.toml"), """
            flag = "not a boolean"
            somethingNobodyDefined = 1

            \t[gui]
            \t\tcount = "seven"
            \t\tname = 12
            \t[gui.advanced]
            \t\tmode = "NO_SUCH_CONSTANT"
            """, StandardCharsets.UTF_8);

        final Fixture f = fixture(configDir);
        f.store().load();

        assertEquals(true, f.flag().get(), "an unparseable boolean keeps its default");
        assertEquals(5, f.count().get(), "an unparseable int keeps its default");
        assertEquals(Mode.OFF, f.mode().get(), "an unknown enum constant keeps its default");
        assertEquals("12", f.name().get(), "a number is still a perfectly good string");
    }

    @Test
    void anOutOfRangeValueOnDiskIsClampedNotRejected(@TempDir final Path configDir) throws IOException
    {
        Files.writeString(configDir.resolve("testmod-common.toml"), """
            \t[gui]
            \t\tcount = 9999
            """, StandardCharsets.UTF_8);

        final Fixture f = fixture(configDir);
        f.store().load();

        assertEquals(10, f.count().get());
    }

    @Test
    void aCorruptFileNeverThrows(@TempDir final Path configDir) throws IOException
    {
        Files.writeString(configDir.resolve("testmod-common.toml"),
            "\0\0\0 this is not toml at all ]]] [[[ = = =\n",
            StandardCharsets.UTF_8);

        final Fixture f = fixture(configDir);
        f.store().load();

        assertEquals(true, f.flag().get());
        assertEquals(5, f.count().get());
    }

    @Test
    void aValueWithNoStoreBehindItStillWorksInMemory()
    {
        final BooleanValue orphan = new BooleanValue("orphan", "t", null, false);
        orphan.set(true);
        orphan.save(); // must not throw
        assertEquals(true, orphan.get());
    }

    @Test
    void theWrittenFileCarriesTheComments(@TempDir final Path configDir) throws IOException
    {
        final Fixture f = fixture(configDir);
        f.store().load();
        f.store().flush();

        final String text = Files.readString(f.store().getFile(), StandardCharsets.UTF_8);
        assertTrue(text.contains("# Root level flag."), text);
        assertTrue(text.contains("# How many."), text);
        assertTrue(text.contains("[gui]"), text);
        assertTrue(text.contains("[gui.advanced]"), text);
    }
}
