package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.research.ModResearchRequirements;
import com.minecolonies.api.research.requirements.BuildingAlternatesResearchRequirement;
import com.minecolonies.api.research.requirements.BuildingResearchRequirement;
import com.minecolonies.api.research.requirements.ResearchResearchRequirement;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import net.minecraft.resources.Identifier;


import static com.minecolonies.api.research.ModResearchRequirements.*;
import net.minecraft.core.Registry;
import java.util.function.Supplier;

/**
 * Registry initializer for the {@link ModResearchRequirements}.
 */
public class ModResearchRequirementInitializer
{
    static
    {
        buildingResearchRequirement = create(BUILDING_RESEARCH_REQ_ID, BuildingResearchRequirement::new, BuildingResearchRequirement::new);
        buildingAlternatesResearchRequirement = create(BUILDING_ALTERNATES_RESEARCH_REQ_ID, BuildingAlternatesResearchRequirement::new, BuildingAlternatesResearchRequirement::new);
        buildingSingleResearchRequirement = create(BUILDING_SINGLE_RESEARCH_REQ_ID, BuildingResearchRequirement::new, BuildingResearchRequirement::new);

        researchResearchRequirement = create(RESEARCH_RESEARCH_REQ_ID, ResearchResearchRequirement::new, ResearchResearchRequirement::new);
    }
    private ModResearchRequirementInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModResearchRequirementInitializer but this is a Utility class.");
    }

    /**
     * Utility method to aid in the creation of a research requirement.
     *
     * @param registryName the registry name for this entry.
     * @param readFromNBT  function to read this item from json.
     * @param readFromJson function to read this item from NBT.
     * @return the finalized registry object.
     */
    private static Supplier<ResearchRequirementEntry> create(
        final Identifier registryName,
        final ReadFromNBTFunction readFromNBT,
        final ReadFromJsonFunction readFromJson)
    {
        return register(registryName.getPath(), () -> new ResearchRequirementEntry(registryName, readFromNBT, readFromJson));
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends ResearchRequirementEntry> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.RESEARCH_REQUIREMENT_REGISTRY,
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
