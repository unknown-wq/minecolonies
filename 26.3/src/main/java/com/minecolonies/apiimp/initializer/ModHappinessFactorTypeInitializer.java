package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.entity.citizen.happiness.ExpirationBasedHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.HappinessRegistry;
import com.minecolonies.api.entity.citizen.happiness.HappinessRegistry.HappinessFactorTypeEntry;
import com.minecolonies.api.entity.citizen.happiness.HappinessRegistry.HappinessFunctionEntry;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.TimeBasedHappinessModifier;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.JobPupil;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler;

import static com.minecolonies.api.entity.citizen.happiness.HappinessRegistry.*;
import static com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

/**
 * Happiness factory initializer of the values.
 */
public final class ModHappinessFactorTypeInitializer
{

    private ModHappinessFactorTypeInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModHappinessFactorTypeInitializer but this is a Utility class.");
    }

    static
    {
        HappinessRegistry.staticHappinessModifier = registerFactor(STATIC_MODIFIER.getPath(), () -> new HappinessFactorTypeEntry(StaticHappinessModifier::new));

        HappinessRegistry.expirationBasedHappinessModifier = registerFactor(EXPIRATION_MODIFIER.getPath(), () -> new HappinessFactorTypeEntry(ExpirationBasedHappinessModifier::new));

        HappinessRegistry.timeBasedHappinessModifier = registerFactor(TIME_PERIOD_MODIFIER.getPath(), () -> new HappinessFactorTypeEntry(TimeBasedHappinessModifier::new));


        HappinessRegistry.schoolFunction = registerFunction(SCHOOL_FUNCTION.getPath(), () -> new HappinessFunctionEntry(data -> data.isChild() ? data.getJob() instanceof JobPupil ? 2.0 : 0.0 : 1.0));
        HappinessRegistry.securityFunction = registerFunction(SECURITY_FUNCTION.getPath(), () -> new HappinessFunctionEntry(data -> getGuardFactor(data.getColony())));
        HappinessRegistry.socialFunction = registerFunction(SOCIAL_FUNCTION.getPath(), () -> new HappinessFunctionEntry(data -> getSocialModifier(data.getColony())));
        HappinessRegistry.mysticalSiteFunction = registerFunction(MYSTICAL_SITE_FUNCTION.getPath(), () -> new HappinessFunctionEntry(data -> getMysticalSiteFactor(data.getColony())));

        HappinessRegistry.housingFunction = registerFunction(HOUSING_FUNCTION.getPath(), () -> new HappinessFunctionEntry(data -> data.getHomeBuilding() == null ? 0.0 : data.getHomeBuilding().getBuildingLevel() / 3.0));
        HappinessRegistry.unemploymentFunction = registerFunction(UNEMPLOYMENT_FUNCTION.getPath(), () -> new HappinessFunctionEntry(data -> data.isChild() ? 1.0 : (data.getWorkBuilding() == null ? 0.5 : data.getWorkBuilding().getBuildingLevel() > 3 ? 2.0 : 1.0)));
        HappinessRegistry.healthFunction = registerFunction(HEALTH_FUNCTION.getPath(),
            () -> new HappinessFunctionEntry(data -> data.getEntity().isPresent() ? (data.getCitizenDiseaseHandler().isSick() ? 0.5 : 1.0) : 1.0));
        HappinessRegistry.idleatjobFunction = registerFunction(IDLEATJOB_FUNCTION.getPath(), () -> new HappinessFunctionEntry(data -> data.isIdleAtJob() ? 0.5 : 1.0));

        HappinessRegistry.sleptTonightFunction = registerFunction(SLEPTTONIGHT_FUNCTION.getPath(), () -> new HappinessFunctionEntry(data -> data.getJob() instanceof AbstractJobGuard ? 1 : 0.5));
        HappinessRegistry.foodFunction = registerFunction(FOOD_FUNCTION.getPath(), () -> new HappinessFunctionEntry(CitizenHappinessHandler::getFoodFactor));
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends HappinessFactorTypeEntry> Supplier<T> registerFactor(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.HAPPINESS_FACTOR_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends HappinessFunctionEntry> Supplier<T> registerFunction(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.HAPPINESS_FUNCTION_REGISTRY,
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
