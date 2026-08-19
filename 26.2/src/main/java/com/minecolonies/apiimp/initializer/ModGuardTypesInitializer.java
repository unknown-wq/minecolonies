package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.colony.guardtype.GuardType;
import com.minecolonies.api.colony.guardtype.registry.ModGuardTypes;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.guard.*;

import static com.minecolonies.api.util.constant.translation.JobTranslationConstants.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

public final class ModGuardTypesInitializer
{

    private ModGuardTypesInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModGuardTypesInitializer but this is a Utility class.");
    }

    static
    {
        ModGuardTypes.knight = register(ModGuardTypes.KNIGHT_ID.getPath(), () -> new GuardType.Builder()
            .setJobTranslationKey(JOB_KNIGHT)
            .setButtonTranslationKey(JOB_KNIGHT_BUTTON)
            .setPrimarySkill(Skill.Adaptability)
            .setSecondarySkill(Skill.Stamina)
            .setWorkerSoundName("knight")
            .setJobEntry(() -> ModJobs.knight.get())
            .setRegistryName(ModGuardTypes.KNIGHT_ID)
            .setClazz(JobKnight.class)
            .createGuardType());

        ModGuardTypes.ranger = register(ModGuardTypes.RANGER_ID.getPath(), () -> new GuardType.Builder()
            .setJobTranslationKey(JOB_RANGER)
            .setButtonTranslationKey(JOB_RANGER_BUTTON)
            .setPrimarySkill(Skill.Agility)
            .setSecondarySkill(Skill.Adaptability)
            .setWorkerSoundName("archer")
            .setJobEntry(() -> ModJobs.archer.get())
            .setRegistryName(ModGuardTypes.RANGER_ID)
            .setClazz(JobRanger.class)
            .createGuardType());

        ModGuardTypes.marksman = register(ModGuardTypes.MARKSMAN_ID.getPath(), () -> new GuardType.Builder()
            .setJobTranslationKey(JOB_MARKSMAN)
            .setButtonTranslationKey(JOB_MARKSMAN_BUTTON)
            .setPrimarySkill(Skill.Agility)
            .setSecondarySkill(Skill.Adaptability)
            .setWorkerSoundName("archer")
            .setJobEntry(() -> ModJobs.marksman.get())
            .setRegistryName(ModGuardTypes.MARKSMAN_ID)
            .setClazz(JobMarksman.class)
            .createGuardType());

        ModGuardTypes.huscarl = register(ModGuardTypes.HUSCARL_ID.getPath(), () -> new GuardType.Builder()
            .setJobTranslationKey(JOB_HUSCARL)
            .setButtonTranslationKey(JOB_HUSCARL_BUTTON)
            .setPrimarySkill(Skill.Adaptability)
            .setSecondarySkill(Skill.Stamina)
            .setWorkerSoundName("knight")
            .setJobEntry(() -> ModJobs.huscarl.get())
            .setRegistryName(ModGuardTypes.HUSCARL_ID)
            .setClazz(JobHuscarl.class)
            .createGuardType());

        ModGuardTypes.druid = register(ModGuardTypes.DRUID_ID.getPath(), () -> new GuardType.Builder()
          .setJobTranslationKey(JOB_DRUID)
          .setButtonTranslationKey(JOB_DRUID_BUTTON)
          .setPrimarySkill(Skill.Mana)
          .setSecondarySkill(Skill.Focus)
          .setWorkerSoundName("druid")
          .setJobEntry(() -> ModJobs.druid.get())
          .setRegistryName(ModGuardTypes.DRUID_ID)
          .setClazz(JobDruid.class)
          .createGuardType());

        ModGuardTypes.cavalry = register(ModGuardTypes.CAVALRY_ID.getPath(), () -> new GuardType.Builder()
                                 .setJobTranslationKey(JOB_CAVALRY)
                                 .setButtonTranslationKey(JOB_CAVALRY_BUTTON)
                                 .setPrimarySkill(Skill.Adaptability)
                                 .setSecondarySkill(Skill.Stamina)
                                 .setWorkerSoundName("archer")
                                 .setJobEntry(() -> ModJobs.cavalry.get())
                                 .setRegistryName(ModGuardTypes.CAVALRY_ID)
                                 .setClazz(JobCavalry.class)
                                 .createGuardType());

    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends GuardType> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.GUARD_TYPE_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1); calling this from
     * the mod entry point is what pins the moment it happens.
     */
    public static void init()
    {
    }
}
