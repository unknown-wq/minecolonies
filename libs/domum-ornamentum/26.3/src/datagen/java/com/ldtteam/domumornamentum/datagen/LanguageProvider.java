package com.ldtteam.domumornamentum.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Drop-in replacement for {@code com.ldtteam.data.LanguageProvider}, which has no 26.x build.
 *
 * <p>Port note (26.2 / Fabric): the LDTTeam data library is not available for 26.x, so its two nested types
 * ({@code SubProvider} and {@code LanguageAcceptor}) are reproduced here with the exact same shape, and the provider
 * itself now extends {@link FabricLanguageProvider}. The 23 {@code *LangEntryProvider} classes therefore only needed
 * their import swapped.</p>
 *
 * <p>Fabric writes the translations through a {@code TreeMap}, i.e. sorted by key, which matches the reference
 * {@code en_us.json} produced by the LDTTeam provider.</p>
 */
public abstract class LanguageProvider extends FabricLanguageProvider {

    private final List<SubProvider> subProviders;

    protected LanguageProvider(final FabricPackOutput output,
                               final String languageCode,
                               final CompletableFuture<HolderLookup.Provider> registryLookup,
                               final List<SubProvider> subProviders) {
        super(output, languageCode, registryLookup);
        this.subProviders = subProviders;
    }

    @Override
    public void generateTranslations(final HolderLookup.Provider registries, final TranslationBuilder builder) {
        // Collected first so that a key written twice by two sub providers is a last-one-wins overwrite, as it was
        // with the LDTTeam provider. Fabric's TranslationBuilder throws on a duplicate key instead.
        final Map<String, String> translations = new LinkedHashMap<>();
        final LanguageAcceptor acceptor = translations::put;
        for (final SubProvider subProvider : this.subProviders) {
            subProvider.addTranslations(acceptor);
        }
        translations.forEach(builder::add);
    }

    /**
     * A single domain's translations.
     */
    @FunctionalInterface
    public interface SubProvider {
        void addTranslations(LanguageAcceptor acceptor);
    }

    /**
     * Sink handed to a {@link SubProvider}.
     */
    @FunctionalInterface
    public interface LanguageAcceptor {
        void add(String key, String value);
    }
}
