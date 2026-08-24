package com.minecolonies.core.generation.defaults;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecolonies.api.util.Log;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.util.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.minecolonies.api.quests.QuestParseConstant.*;
import static com.minecolonies.api.quests.registries.QuestRegistries.DIALOGUE_OBJECTIVE_ID;
import static com.minecolonies.api.util.constant.Constants.MOD_ID;
import static com.minecolonies.core.generation.DataGeneratorConstants.COLONY_QUESTS_DIR;
import static com.minecolonies.core.quests.QuestParsingConstants.*;

/**
 * Magic translator for quests.  This parses the existing quest JSON files and moves the dialogue elements to
 * translation resources, so that translations can be provided for them.
 *
 * This requires that the 'source' quests under src/main/resources/data/minecolonies/quests only contain en-US
 * text and do not already contain translation keys.
 */
public class QuestTranslationProvider implements DataProvider
{
    private final FabricPackOutput packOutput;

    public QuestTranslationProvider(@NotNull final FabricPackOutput packOutput)
    {
        this.packOutput = packOutput;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "QuestTranslationProvider";
    }

    @NotNull
    @Override
    public CompletableFuture<?> run(@NotNull final CachedOutput cache)
    {
        final PackOutput.PathProvider questProvider = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, COLONY_QUESTS_DIR);
        final List<CompletableFuture<?>> quests = new ArrayList<>();

        // Port note (26.2 / Fabric): the source quests used to be read through a PathPackResources rooted at the
        // working-directory-relative "../../src/main/resources", which only worked for the NeoForge runData CWD.
        //
        // Reading them through the mod container's resource roots instead was tried and does not work: in a dev
        // launch that root is build/resources/main, i.e. *processed* resources -- and processResources deliberately
        // lets src/main/generated win over src/main/resources (see build.gradle), so the only quest copy there is
        // the one this provider already key-ified. Feeding that back in makes every quest string its own key
        // ("minecolonies.quests.tutorial.welcome.obj0": "minecolonies.quests.tutorial.welcome.obj0") and lang/quests.json
        // loses all of its English. The first runDatagen on a clean checkout looks fine; the next one destroys it.
        //
        // So: read the authoring directory, like the NeoForge version did, but derive it from the datagen output
        // folder (<project>/src/main/generated) rather than from the working directory, so it stays CWD independent.
        final Path questRoot = packOutput.getOutputFolder().getParent()
                                 .resolve("resources").resolve("data").resolve(MOD_ID).resolve(COLONY_QUESTS_DIR);
        if (!Files.isDirectory(questRoot))
        {
            throw new IllegalStateException("Authored quests not found at " + questRoot
                                              + " -- has the datagen output folder moved out of src/main/generated?");
        }

        final List<Path> questFiles;
        try (final Stream<Path> walk = Files.walk(questRoot))
        {
            questFiles = walk.filter(Files::isRegularFile)
                           .filter(f -> f.getFileName().toString().endsWith(".json"))
                           .toList();
        }
        catch (final IOException e)
        {
            throw new IllegalStateException("Failed to enumerate quests under " + questRoot, e);
        }

        for (final Path questFile : questFiles)
        {
            final String relative = questRoot.relativize(questFile).toString().replace('\\', '/');
            final Identifier questPath = Identifier.fromNamespaceAndPath(MOD_ID, relative.replace(".json", ""));
            final String baseKey = questPath.getNamespace() + ".quests." + questPath.getPath().replace("/", ".");
            final JsonObject langJson = new JsonObject();

            quests.add(CompletableFuture.supplyAsync(() ->
            {
                try
                {
                    final JsonObject questJson;
                    try (final Reader reader = Files.newBufferedReader(questFile))
                    {
                        questJson = GsonHelper.parse(reader);
                    }

                    processQuest(langJson, baseKey, questJson);

                    return questJson;
                }
                catch (final Exception e)
                {
                    Log.getLogger().error("Failed to process {}", questPath.toString(), e);
                    return null;
                }
            }, Util.backgroundExecutor()).thenComposeAsync(json ->
            {
                if (json != null)
                {
                    return DataProvider.saveStable(cache, json, questProvider.json(questPath))
                            .thenApply(q -> langJson);
                }
                return CompletableFuture.completedFuture(null);
            }, Util.backgroundExecutor()));
        }

        return CompletableFuture.allOf(quests.toArray(CompletableFuture[]::new))
                .thenComposeAsync(v -> saveLanguage(cache, quests.stream().map(q -> (JsonObject) q.join()).toList()), Util.backgroundExecutor());
    }

    @NotNull
    private CompletableFuture<?> saveLanguage(@NotNull final CachedOutput cache,
                                              @NotNull final List<JsonObject> langJsons)
    {
        final JsonObject langJson = new JsonObject();
        for (final JsonObject questLang : langJsons)
        {
            for (final Map.Entry<String, JsonElement> entry : questLang.entrySet())
            {
                langJson.add(entry.getKey(), entry.getValue());
            }
        }

        final PackOutput.PathProvider langProvider = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "lang");
        final Path langFile = langProvider.file(Identifier.fromNamespaceAndPath(MOD_ID, "quests"), "json");
        return DataProvider.saveStable(cache, langJson, langFile);
    }

    private void processQuest(final JsonObject langJson, final String baseKey, final JsonObject json)
    {
        final String name = json.get(NAME).getAsString();
        langJson.addProperty(baseKey, name);
        json.addProperty(NAME, baseKey);

        int objectiveCount = 0;
        for (final JsonElement objectivesJson : json.get(QUEST_OBJECTIVES).getAsJsonArray())
        {
            final String objectiveKey = baseKey + ".obj" + objectiveCount;
            final JsonObject objective = objectivesJson.getAsJsonObject();
            processObjective(langJson, objectiveKey, objective);
            ++objectiveCount;
        }
    }

    private void processObjective(final JsonObject langJson, final String baseKey, final JsonObject json)
    {
        final Identifier type = Identifier.parse(json.get(TYPE).getAsString());
        if (type.equals(DIALOGUE_OBJECTIVE_ID))
        {
            langJson.addProperty(baseKey, json.get(TEXT_ID).getAsString());
            json.addProperty(TEXT_ID, baseKey);

            int answerCount = 0;
            for (final JsonElement answerJson : json.get(OPTIONS_ID).getAsJsonArray())
            {
                final String answerKey = baseKey + ".answer" + answerCount;
                langJson.addProperty(answerKey, answerJson.getAsJsonObject().get(ANSWER_ID).getAsString());
                answerJson.getAsJsonObject().addProperty(ANSWER_ID, answerKey);

                final JsonObject result = answerJson.getAsJsonObject().get(RESULT_ID).getAsJsonObject();
                processObjective(langJson, answerKey + ".reply", result);
                ++answerCount;
            }
        }
    }

}
