package com.minecolonies.core.generation.defaults;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecolonies.api.util.constant.Constants;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Language diagnostics for {@code assets/minecolonies/lang/en_us.json}. <b>Deliberately not registered</b> in
 * {@link com.minecolonies.core.generation.MineColoniesDataGenerator} — register it by hand when you want the report
 * it produces. It writes nothing.
 *
 * <h2>What this used to be, and why it stopped</h2>
 * Upstream never had an {@code en_us.json} in the repository: {@code tools/export_lang/exportLang.py} pulls every
 * language, English included, out of POEditor and drops the result into
 * {@code src/main/resources/assets/minecolonies/lang/} as part of the release pipeline. Only
 * {@code manual_en_us.json} — the strings POEditor does not manage — was committed. This port has no POEditor
 * project, so nothing produced {@code en_us.json} and every string rendered as its raw key; this provider closed
 * that hole by merging {@code manual_en_us.json} with the three lang files datagen writes
 * ({@code default.json} from {@code AbstractResearchProvider}, {@code quests.json} from
 * {@link QuestTranslationProvider}, {@code tag.item.json} from {@link DefaultItemTagsProvider}) into one 4252-key
 * {@code en_us.json}.
 * <p>
 * That whole arrangement rested on {@code manual_en_us.json} being in the repository, and it is not any more: it is
 * upstream's text, all-rights-reserved, and this repository carries no MineColonies asset. What ships instead is the
 * 357-key <em>port-owned overlay</em> at {@code src/main/resources/assets/minecolonies/lang/en_us.json} — the 351
 * keys this port added plus the 6 upstream values it rewrote, and nothing else (see
 * {@code docs/assetfetch/WP0-REPORT.md} §4). Upstream's own {@code en_us.json} arrives at runtime inside the
 * fetched asset pack.
 * <p>
 * Nothing has to merge those two, because language files are the one resource type Minecraft does not resolve by
 * "topmost pack wins": {@code ClientLanguage} walks the entire resource stack and folds every
 * {@code lang/en_us.json} it finds into one map, later packs overriding individual keys. The fetched pack sits at
 * {@code Position.BOTTOM} and our jar above it, so upstream's 3898 keys load first and the port's 357 overwrite the
 * 6 they mean to. A merged file in the jar would add nothing and would have to be regenerated every time upstream
 * changed a string.
 * <p>
 * The three generated lang files are still written by their own providers. They are inert — Minecraft only loads
 * {@code lang/<code>.json} files named after a real language — and they are kept as the input this class reads and
 * as the record of what the port's own research and quest text is.
 *
 * <h2>The report</h2>
 * {@link #run} merges the same four inputs in memory and then walks the block, item and entity registries, listing
 * every {@code minecolonies:} entry whose description id no key covers. With upstream's text living in the fetched
 * pack rather than in the build, nearly every entry is "missing" from the build's point of view, which is why this
 * is a hand-run tool and not a registered provider: the interesting output is the small set of keys that neither
 * the overlay nor the fetched pack defines. As of the last check, three exist —
 * {@code com.minecolonies.research.civilian.boats.name}, {@code …boats.subtitle} and
 * {@code com.minecolonies.research.effects.boatsunlock.description}: port-added research whose text is in
 * {@code default.json} but in neither the overlay nor upstream's {@code en_us.json}.
 */
public class DefaultLanguageProvider implements DataProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLanguageProvider.class);

    /**
     * The port-owned overlay, read off the classpath ({@code src/main/resources}) rather than the datagen output
     * folder: nothing generates it, and nothing may — it is the file that ships. Was
     * {@code lang/manual_en_us.json}, upstream's authored file, which this repository no longer carries.
     */
    private static final String PORT_LANG_RESOURCE = "/assets/" + Constants.MOD_ID + "/lang/en_us.json";

    /**
     * The generated files, read back out of the datagen output folder — i.e. this run's output, not whatever a
     * previous run happened to leave on the classpath.
     */
    private static final List<String> GENERATED_INPUTS = List.of("default", "quests", "tag.item");

    private final PackOutput.PathProvider langPath;

    public DefaultLanguageProvider(@NotNull final FabricPackOutput output)
    {
        this.langPath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "lang");
    }

    @NotNull
    @Override
    public String getName()
    {
        return "MineColonies Language Audit";
    }

    /**
     * Reads the four language inputs and reports what they do not cover. Writes nothing: the shipping
     * {@code en_us.json} is the committed overlay, and a second copy of it under {@code src/main/generated} would
     * land in the same jar entry (both directories are resource roots) — see the class javadoc.
     */
    @NotNull
    @Override
    public CompletableFuture<?> run(@NotNull final CachedOutput cache)
    {
        final Map<String, JsonElement> merged = new LinkedHashMap<>();

        readPortLang(merged);
        for (final String name : GENERATED_INPUTS)
        {
            readGenerated(merged, name);
        }

        auditRegistries(merged);

        return CompletableFuture.completedFuture(null);
    }

    private void readPortLang(@NotNull final Map<String, JsonElement> merged)
    {
        try (final InputStream in = DefaultLanguageProvider.class.getResourceAsStream(PORT_LANG_RESOURCE))
        {
            if (in == null)
            {
                throw new IllegalStateException(PORT_LANG_RESOURCE + " is not on the datagen classpath");
            }
            try (final Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
            {
                absorb(merged, JsonParser.parseReader(reader).getAsJsonObject(), PORT_LANG_RESOURCE);
            }
        }
        catch (final IOException e)
        {
            throw new IllegalStateException("Could not read " + PORT_LANG_RESOURCE, e);
        }
    }

    private void readGenerated(@NotNull final Map<String, JsonElement> merged, @NotNull final String name)
    {
        final Path path = langPath.json(Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
        if (!Files.isRegularFile(path))
        {
            // Not fatal on its own, but it means a provider that should have run before this one did not.
            LOGGER.error("Language input {} is missing -- is {} still registered last in MineColoniesDataGenerator?",
              path, DefaultLanguageProvider.class.getSimpleName());
            return;
        }

        try (final Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            absorb(merged, JsonParser.parseReader(reader).getAsJsonObject(), path.toString());
        }
        catch (final IOException e)
        {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }

    private void absorb(@NotNull final Map<String, JsonElement> merged,
                        @NotNull final JsonObject source,
                        @NotNull final String origin)
    {
        for (final Map.Entry<String, JsonElement> entry : source.entrySet())
        {
            final JsonElement previous = merged.put(entry.getKey(), entry.getValue());
            if (previous != null && !previous.equals(entry.getValue()))
            {
                LOGGER.warn("Translation key {} is defined twice with different text; {} wins", entry.getKey(), origin);
            }
        }
    }

    /**
     * Walks the registries this mod contributes to and reports anything whose translation key the merged file does
     * not cover. Purely informational — a missing string is not worth failing a build over, but it is exactly the
     * class of bug ("the block shows its key in the creative menu") that is otherwise only found by playing.
     */
    private void auditRegistries(@NotNull final Map<String, JsonElement> merged)
    {
        final List<String> missing = new ArrayList<>();

        collectMissing(missing, merged, BuiltInRegistries.BLOCK, "block", b -> b.getDescriptionId());
        collectMissing(missing, merged, BuiltInRegistries.ITEM, "item", i -> i.getDescriptionId());
        collectMissing(missing, merged, BuiltInRegistries.ENTITY_TYPE, "entity", e -> e.getDescriptionId());

        if (missing.isEmpty())
        {
            LOGGER.info("Language: {} keys, every registered block/item/entity is translated", merged.size());
        }
        else
        {
            LOGGER.warn("Language: {} keys, but {} registry entries have no translation:", merged.size(), missing.size());
            missing.forEach(key -> LOGGER.warn("  untranslated: {}", key));
        }
    }

    private <T> void collectMissing(@NotNull final List<String> missing,
                                    @NotNull final Map<String, JsonElement> merged,
                                    @NotNull final Registry<T> registry,
                                    @NotNull final String what,
                                    @NotNull final Function<T, String> descriptionId)
    {
        for (final Map.Entry<net.minecraft.resources.ResourceKey<T>, T> entry : registry.entrySet())
        {
            if (!entry.getKey().identifier().getNamespace().equals(Constants.MOD_ID))
            {
                continue;
            }
            final String key = descriptionId.apply(entry.getValue());
            if (!merged.containsKey(key))
            {
                missing.add(what + " " + entry.getKey().identifier() + " -> " + key);
            }
        }
    }
}
