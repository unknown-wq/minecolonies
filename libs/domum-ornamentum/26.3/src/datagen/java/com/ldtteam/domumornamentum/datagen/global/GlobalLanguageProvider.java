package com.ldtteam.domumornamentum.datagen.global;

import com.ldtteam.domumornamentum.datagen.DomumOrnamentumLangEntries;
import com.ldtteam.domumornamentum.datagen.LanguageProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.util.Constants;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GlobalLanguageProvider extends LanguageProvider
{
    public GlobalLanguageProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, Constants.DEFAULT_LANG, registryLookup, List.of(
                new DomumOrnamentumLangEntries(),
                new GlobalLanguageEntries()
        ));
    }

    private final static class GlobalLanguageEntries implements SubProvider {
        @Override
        public void addTranslations(LanguageAcceptor acceptor) {
            acceptor.add("itemGroup." + Constants.MOD_ID + ".timber_frames", "DO - Framed Blocks");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".shingles", "DO - Shingles");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".general", "Domum Ornamentum (DO)");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".paperwalls", "DO - Thin Framed Walls");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".shingle_slabs", "DO - Shingle Slabs");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".extra-blocks", "DO - Decorative Blocks");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".floating-carpets", "DO - Floating Carpets");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".fences", "DO - Fences");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".slabs", "DO - Slabs");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".walls", "DO - Walls");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".stairs", "DO - Stairs");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".doors", "DO - Doors");
            acceptor.add("itemGroup." + Constants.MOD_ID + ".posts", "DO - Posts");
            acceptor.add("block." + Constants.MOD_ID + ".architectscutter", "Architect's Cutter");
            acceptor.add(Constants.MOD_ID + ".architectscutter", "Architect's Cutter");
            acceptor.add(Constants.MOD_ID + ".origin.tooltip", "Crafted in the Architect's Cutter");
            acceptor.add(Constants.MOD_ID + ".block.format", "Material: %s");
            acceptor.add(ModBlocks.getInstance().getStandingBarrel().getDescriptionId(), "Standing Barrel");
            acceptor.add(ModBlocks.getInstance().getLayingBarrel().getDescriptionId(), "Laying Barrel");

            acceptor.add("cuttergroup." + Constants.MOD_ID + ".jbrick", "Bricks");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".ddoor", "Doors");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".etrapdoor", "Trapdoors");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".ilight", "Lights");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".fpanel", "Panels");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".hpaperwall", "Paperwalls");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".gpillar", "Pillars");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".kpost", "Posts");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".cshingle", "Shingles");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".btimberframe", "Timberframes");
            acceptor.add("cuttergroup." + Constants.MOD_ID + ".avanilla", "Vanilla Blocks");

            acceptor.add(Constants.MOD_ID + ".group", "Group:");
            acceptor.add(Constants.MOD_ID + ".variant", "Variant:");

            acceptor.add(Constants.MOD_ID + ".desc.material", "Material: %s");
            acceptor.add(Constants.MOD_ID + ".desc.main", "Main %s");
            acceptor.add(Constants.MOD_ID + ".desc.support", "Support %s");
            acceptor.add(Constants.MOD_ID + ".desc.center", "Center %s");
            acceptor.add(Constants.MOD_ID + ".desc.frame", "Frame %s");
            acceptor.add(Constants.MOD_ID + ".desc.shingle", "Shingle %s");
            acceptor.add(Constants.MOD_ID + ".desc.onlyone", "%s");

        }
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Global Lang Provider";
    }
}
