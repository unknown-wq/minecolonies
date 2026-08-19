package com.minecolonies.core.generation.defaults;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.entity.mobs.RaiderType;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.constant.Constants;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.minecolonies.api.sounds.ModSoundEvents.CITIZEN_SOUND_EVENT_PREFIX;
import static com.minecolonies.core.generation.SoundsJson.createSoundJson;

/**
 * Datagen for {@code assets/minecolonies/sounds.json}.
 *
 * <p><b>Deliberately not registered</b> in {@link com.minecolonies.core.generation.MineColoniesDataGenerator}, and
 * this one matters more than the other two retired asset providers, because it would not have failed quietly.</p>
 *
 * <p>The runtime asset fetch injects the downloaded upstream pack at {@code Position.BOTTOM}, i.e. <em>below</em>
 * the mod jar: for every resource except lang files, a file in our jar wins over the fetched copy. Upstream's real
 * {@code sounds.json} is in the fetched pack. Meanwhile {@link #citizenSoundFolder()} falls back to a path that
 * cannot exist ({@code __no_citizen_sounds__}) when the sound folder is absent — which it always is at build time
 * now — and {@link #run} carries on regardless and writes a {@code sounds.json} with every citizen event missing.
 * Shipping that file would mask the real one permanently and silently. Shipping no {@code sounds.json} at all is
 * what lets the fetched one through; before the download has happened, the absent file degrades to a warning and
 * silence.</p>
 *
 * <p>The class is kept as the record of how the citizen/raider sound events are laid out.</p>
 */
public class DefaultSoundProvider implements DataProvider
{
    private final FabricPackOutput packOutput;
    private JsonObject sounds;

    public DefaultSoundProvider(@NotNull final FabricPackOutput packOutput)
    {
        this.packOutput = packOutput;
    }

    /**
     * The mod's own {@code assets/minecolonies/sounds/mob/citizen} directory.
     *
     * <p>Port note (26.2 / Fabric): this used to be reached by walking three parents up from the NeoForge datagen
     * output ({@code src/datagen/generated/minecolonies}) and back down into {@code main/resources}. The Fabric
     * output lives at {@code src/main/generated}, so that arithmetic no longer lands anywhere; the resource root
     * is asked for directly instead, which is also independent of the datagen working directory.</p>
     *
     * @return the folder, or a non-existent path when the resources are not on disk.
     */
    private Path citizenSoundFolder()
    {
        for (final Path root : packOutput.getModContainer().getRootPaths())
        {
            final Path candidate = root.resolve("assets").resolve(Constants.MOD_ID)
                                     .resolve("sounds").resolve("mob").resolve("citizen");
            if (java.nio.file.Files.isDirectory(candidate))
            {
                return candidate;
            }
        }
        return packOutput.getOutputFolder().resolve("__no_citizen_sounds__");
    }

    @Override
    @NotNull
    public CompletableFuture<?> run(@NotNull final CachedOutput cache)
    {
        sounds = new JsonObject();
        final File soundFolder = citizenSoundFolder().toFile();
        final List<Identifier> mainTypes = new ArrayList<>(ModJobs.getJobs());
        mainTypes.remove(ModJobs.placeHolder.get().getKey());
        mainTypes.add(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "unemployed"));
        mainTypes.add(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "visitor"));
        mainTypes.add(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "child"));

        if (soundFolder.isDirectory())
        {
            final File[] list = soundFolder.listFiles();
            for (final File file : list)
            {
                final String name = file.getName();
                if (name.equals("child"))
                {
                    continue;
                }

                if (file.isDirectory())
                {
                    final List<String> soundList = new ArrayList<>();
                    final File[] subList = file.listFiles();

                    for (final File soundFile : subList)
                    {
                        final String soundName = soundFile.getName();
                        soundList.add("minecolonies:mob/citizen/" + name + "/" + soundName.replace(".ogg", ""));
                    }

                    for (final Identifier job : mainTypes)
                    {
                        for (final EventType event : EventType.values())
                        {
                            sounds.add(CITIZEN_SOUND_EVENT_PREFIX + job.getPath() + "." + name + "." + event.getId(),
                              createSoundJson("neutral", getDefaultProperties(), soundList));
                        }
                    }
                }
            }
        }

        final List<String> childSounds = new ArrayList<>();
        childSounds.add("minecolonies:mob/citizen/child/laugh1");
        childSounds.add("minecolonies:mob/citizen/child/laugh2");

        for (final EventType soundEvents : EventType.values())
        {
            sounds.add(CITIZEN_SOUND_EVENT_PREFIX + "child.male." + soundEvents.name().toLowerCase(Locale.US), createSoundJson("neutral", getDefaultProperties(), childSounds));
            sounds.add(CITIZEN_SOUND_EVENT_PREFIX + "child.female." + soundEvents.name().toLowerCase(Locale.US), createSoundJson("neutral", getDefaultProperties(), childSounds));
        }

        for (final RaiderType type : RaiderType.values())
        {
            sounds.add("mob." + type.name().toLowerCase(Locale.US) + ".death", createSoundJson("hostile", getDefaultProperties(), ImmutableList.of("minecolonies:mob/barbarian/death")));
            sounds.add("mob." + type.name().toLowerCase(Locale.US) + ".say", createSoundJson("hostile", getDefaultProperties(), ImmutableList.of("minecolonies:mob/barbarian/say")));
            
            sounds.add("mob." + type.name().toLowerCase(Locale.US) + ".hurt",
              createSoundJson("hostile",
                getDefaultProperties(),
                ImmutableList.of("minecolonies:mob/barbarian/hurt1", "minecolonies:mob/barbarian/hurt2", "minecolonies:mob/barbarian/hurt3", "minecolonies:mob/barbarian/hurt4")));
        }

        sounds.add("mob.citizen.snore", createSoundJson("neutral", getDefaultProperties(), ImmutableList.of("minecolonies:mob/citizen/snore")));

        JsonObject tavernProperties = getDefaultProperties();
        tavernProperties.addProperty("attenuation_distance", 23);
        tavernProperties.addProperty("stream", true);
        tavernProperties.addProperty("comment", "Credits to Darren Curtis - Fireside Tales");
        sounds.add("tile.tavern.tavern_theme", createSoundJson("music", tavernProperties, ImmutableList.of("minecolonies:tile/tavern/tavern_theme")));

        sounds.add("mob.mercenary.attack", createSoundJson("neutral", getDefaultProperties(), ImmutableList.of("minecolonies:mob/mercenary/attack/attack1", "minecolonies:mob/mercenary/attack/attack2", "minecolonies:mob/mercenary/attack/attack3", "minecolonies:mob/mercenary/attack/attack4")));
        sounds.add("mob.mercenary.celebrate", createSoundJson("neutral", getDefaultProperties(), ImmutableList.of("minecolonies:mob/mercenary/celebrate/celebrate1")));
        sounds.add("mob.mercenary.die", createSoundJson("neutral", getDefaultProperties(), ImmutableList.of("minecolonies:mob/mercenary/die/death1", "minecolonies:mob/mercenary/die/death2")));
        sounds.add("mob.mercenary.hurt", createSoundJson("neutral", getDefaultProperties(), ImmutableList.of("minecolonies:mob/mercenary/hurt/hurt1", "minecolonies:mob/mercenary/hurt/hurt2", "minecolonies:mob/mercenary/hurt/hurt3")));
        sounds.add("mob.mercenary.say", createSoundJson("neutral", getDefaultProperties(), ImmutableList.of("minecolonies:mob/mercenary/say/say1", "minecolonies:mob/mercenary/say/say2", "minecolonies:mob/mercenary/say/say3")));
        sounds.add("mob.mercenary.step", createSoundJson("neutral", getDefaultProperties(), ImmutableList.of("minecolonies:mob/mercenary/step/step1", "minecolonies:mob/mercenary/step/step2", "minecolonies:mob/mercenary/step/step3", "minecolonies:mob/mercenary/step/step4")));
        sounds.add("tile.sawmill.saw", createSoundJson("neutral", getDefaultProperties(), ImmutableList.of("minecolonies:tile/sawmill/saw")));

        addMusic("record", false,
          "raid.raid_alert",
          "raid.raid_alert_early",
          "raid.raid_won",
          "raid.raid_won_early");

        addMusic("music", true,
          "raid.desert.desert_raid",
          "raid.desert.desert_raid_warning",
          "raid.desert.desert_raid_victory",
          "raid.amazon.amazon_raid");

        return DataProvider.saveStable(cache, sounds, getPath());
    }

    protected Path getPath()
    {
        return this.packOutput
                 .getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                 .resolve(Constants.MOD_ID)
                 .resolve("sounds.json");
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Default Sound Json Provider";
    }

    private JsonObject getDefaultProperties()
    {
        JsonObject properties = new JsonObject();
        properties.addProperty("stream", false);
        return properties;
    }

    private void addMusic(String category, boolean stream, String... ids)
    {
        for (String id : ids)
        {
            JsonObject obj = new JsonObject();
            obj.addProperty("stream", stream);
            obj.addProperty("relative", true);
            sounds.add(id, createSoundJson(category, obj, ImmutableList.of(Constants.MOD_ID+":"+id.replace(".", "/"))));
        }
    }
}
