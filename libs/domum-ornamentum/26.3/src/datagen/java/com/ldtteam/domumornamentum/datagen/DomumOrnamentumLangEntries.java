package com.ldtteam.domumornamentum.datagen;

import com.ldtteam.domumornamentum.util.Constants;
import com.ldtteam.domumornamentum.block.types.BrickType;
import com.ldtteam.domumornamentum.block.types.DoorType;
import com.ldtteam.domumornamentum.block.types.FancyDoorType;
import com.ldtteam.domumornamentum.block.types.FancyTrapdoorType;
import com.ldtteam.domumornamentum.block.types.FramedLightType;
import com.ldtteam.domumornamentum.block.types.PostType;
import com.ldtteam.domumornamentum.block.types.TimberFrameType;
import com.ldtteam.domumornamentum.block.types.TrapdoorType;

/**
 * Every English string Domum Ornamentum generates, one domain per section.
 *
 * <p>These used to be twenty-two {@code *LangEntryProvider} classes, one per domain, each of which was four
 * lines of ceremony around its list of strings. They are collected here in the order the old sub provider list
 * ran them; the order only decides who wins a duplicated key, because
 * {@link LanguageProvider#generateTranslations} hands the collected map to Fabric, which writes it sorted.</p>
 */
public class DomumOrnamentumLangEntries implements LanguageProvider.SubProvider
{
    @Override
    public void addTranslations(final LanguageProvider.LanguageAcceptor acceptor)
    {
        // --- Bricks ---
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.BEIGE.getSerializedName(), "Beige Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.BROWN.getSerializedName(), "Brown Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.CREAM.getSerializedName(), "Cream Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.SAND.getSerializedName(), "Sand Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.ROAN.getSerializedName(), "Roan Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.BEIGE_STONE.getSerializedName(), "Beige Stone Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.BROWN_STONE.getSerializedName(), "Brown Stone Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.CREAM_STONE.getSerializedName(), "Cream Stone Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.SAND_STONE.getSerializedName(), "Sand Stone Bricks");
        acceptor.add("block." + Constants.MOD_ID + "." + BrickType.ROAN_STONE.getSerializedName(), "Roan Stone Bricks");

        // --- Doors ---
        acceptor.add(Constants.MOD_ID + ".door.name.format", "%s Door");
        acceptor.add(Constants.MOD_ID + ".door.type.format", "Variant: %s");
        acceptor.add(Constants.MOD_ID + ".door.block.format", "Material: %s");

        for (final DoorType value : DoorType.values())
        {
            acceptor.add(Constants.MOD_ID + ".door.type.name." + value.getTranslationKeySuffix(), value.getDefaultEnglishTranslation());
        }

        // --- Fancy doors ---
        acceptor.add(Constants.MOD_ID + ".fancydoor.name.format", "Fancy %s Door");
        acceptor.add(Constants.MOD_ID + ".fancydoor.type.format", "Variant: %s");

        acceptor.add(Constants.MOD_ID + ".fancydoor.frame.header", "Frame:");
        acceptor.add(Constants.MOD_ID + ".fancydoor.center.header", "Center:");
        acceptor.add(Constants.MOD_ID + ".fancydoor.center.block.format", "  - Material: %s");
        acceptor.add(Constants.MOD_ID + ".fancydoor.frame.block.format", "  - Material: %s");

        for (final FancyDoorType value : FancyDoorType.values())
        {
            acceptor.add(Constants.MOD_ID + ".fancydoor.type.name." + value.getTranslationKeySuffix(), value.getDefaultEnglishTranslation());
        }

        // --- Extra blocks ---
        acceptor.add(Constants.MOD_ID + ".extra.name.format", "%s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.black", "Black %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.blue", "Blue %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.brown", "Brown %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.cyan", "Cyan %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.gray", "Gray %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.green", "Green %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.light_blue", "Light Blue %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.light_gray", "Light Gray %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.lime", "Lime %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.magenta", "Magenta %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.orange", "Orange %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.pink", "Pink %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.purple", "Purple %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.red", "Red %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.white", "White %s Extra");
        acceptor.add(Constants.MOD_ID + ".extra.name.format.yellow", "Yellow %s Extra");

        // --- Fences ---
        acceptor.add(Constants.MOD_ID + ".fence.name.format", "%s Fence");

        // --- Fence gates ---
        acceptor.add(Constants.MOD_ID + ".fence-gate.name.format", "%s Fence gate");

        // --- Floating carpets ---
        acceptor.add("block." + Constants.MOD_ID + ".black_floating_carpet", "Black Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".blue_floating_carpet", "Blue Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".brown_floating_carpet", "Brown Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".cyan_floating_carpet", "Cyan Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".gray_floating_carpet", "Gray Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".green_floating_carpet", "Green Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".light_blue_floating_carpet", "Light Blue Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".light_gray_floating_carpet", "Light Gray Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".lime_floating_carpet", "Lime Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".magenta_floating_carpet", "Magenta Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".orange_floating_carpet", "Orange Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".pink_floating_carpet", "Pink Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".purple_floating_carpet", "Purple Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".red_floating_carpet", "Red Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".white_floating_carpet", "White Floating Carpet");
        acceptor.add("block." + Constants.MOD_ID + ".yellow_floating_carpet", "Yellow Floating Carpet");

        // --- Framed lights ---
        for (final FramedLightType type : FramedLightType.values())
        {
            final String reference = Constants.MOD_ID + ".light.frame.type." + type.getName();
            final String value = type.getLangName();

            acceptor.add(reference, value);
        }

        acceptor.add(Constants.MOD_ID + ".light.frame.name.format", "Framed %s");
        acceptor.add(Constants.MOD_ID + ".light.frame.header", "Framing:");
        acceptor.add(Constants.MOD_ID + ".light.frame.type.format", "  - Type:     %s");
        acceptor.add(Constants.MOD_ID + ".light.frame.block.format", "  - Material: %s");
        acceptor.add(Constants.MOD_ID + ".light.center.header", "Center:");
        acceptor.add(Constants.MOD_ID + ".light.center.block.format", "  - Material: %s");

        // --- Timber frames ---
        for (final TimberFrameType type : TimberFrameType.values())
        {
            final String reference = Constants.MOD_ID + ".timber.frame.type." + type.getName();
            final String value = type.getLangName();

            acceptor.add(reference, value);
        }

        acceptor.add(Constants.MOD_ID + ".timber.frame.name.format", "Framed %s");
        acceptor.add(Constants.MOD_ID + ".timber.frame.header", "Framing:");
        acceptor.add(Constants.MOD_ID + ".timber.frame.type.format", "  - Type:     %s");
        acceptor.add(Constants.MOD_ID + ".timber.frame.block.format", "  - Material: %s");
        acceptor.add(Constants.MOD_ID + ".timber.center.header", "Center:");
        acceptor.add(Constants.MOD_ID + ".timber.center.block.format", "  - Material: %s");

        // --- Panels ---
        acceptor.add(Constants.MOD_ID + ".panel.name.format", "%s Panel");
        acceptor.add(Constants.MOD_ID + ".panel.type.format", "Variant: %s");
        acceptor.add(Constants.MOD_ID + ".panel.block.format", "Material: %s");

        for (final TrapdoorType value : TrapdoorType.values())
        {
            acceptor.add(Constants.MOD_ID + ".panel.type.name." + value.getTranslationKeySuffix(), value.getDefaultEnglishTranslation());
        }

        // --- Posts ---
        acceptor.add(Constants.MOD_ID + ".post.name.format", "%s Post");
        acceptor.add(Constants.MOD_ID + ".post.type.format", "Variant: %s");
        acceptor.add(Constants.MOD_ID + ".post.block.format", "Material: %s");

        /*
          IE "Oak Planks Double Post"
         */
        for (final PostType value : PostType.values())
        {
            acceptor.add(Constants.MOD_ID + ".post.type.name." + value.getTranslationKeySuffix(), value.getDefaultEnglishTranslation());
        }

        // --- Pillars ---
        acceptor.add(Constants.MOD_ID + ".blockpillar.name.format", "Round %s Pillar");
        acceptor.add(Constants.MOD_ID + ".blockypillar.name.format", "Voxel %s Pillar");
        acceptor.add(Constants.MOD_ID + ".squarepillar.name.format", "Square %s Pillar");

        acceptor.add(Constants.MOD_ID + ".pillar.header", "Type:");
        acceptor.add(Constants.MOD_ID + ".pillar.column.format", "Main Material: %s");

        // --- Shingles ---
        acceptor.add(Constants.MOD_ID + ".shingle.name.format.block.domum_ornamentum.shingle_flat_lower", "%s Flat Lower Shingles");
        acceptor.add(Constants.MOD_ID + ".shingle.support.format", "Supported by: %s");
        acceptor.add(Constants.MOD_ID + ".shingle.main.format", "Main Material: %s");

        acceptor.add(Constants.MOD_ID + ".shingle.name.format.block.domum_ornamentum.shingle", "%s Shingles");
        acceptor.add(Constants.MOD_ID + ".shingle.name.format.block.domum_ornamentum.shingle_flat", "%s Flat Shingles");

        // --- Shingle slabs ---
        acceptor.add(Constants.MOD_ID + ".shingle_slab.name.format", "%s Shingles");
        acceptor.add(Constants.MOD_ID + ".shingle_slab.support.format", "Supported by: %s");
        acceptor.add(Constants.MOD_ID + ".shingle_slab.cover.format", "Covered by: %s");
        acceptor.add(Constants.MOD_ID + ".shingle_slab.main.format", "Main Material: %s");

        // --- Slabs ---
        acceptor.add(Constants.MOD_ID + ".slab.name.format", "%s Slab");

        // --- Stairs ---
        acceptor.add(Constants.MOD_ID + ".stair.name.format", "%s Stairs");

        // --- Trapdoors ---
        acceptor.add(Constants.MOD_ID + ".trapdoor.name.format", "%s Trapdoor");
        acceptor.add(Constants.MOD_ID + ".trapdoor.type.format", "Variant: %s");
        acceptor.add(Constants.MOD_ID + ".trapdoor.block.format", "Material: %s");

        for (final TrapdoorType value : TrapdoorType.values())
        {
            acceptor.add(Constants.MOD_ID + ".trapdoor.type.name." + value.getTranslationKeySuffix(), value.getDefaultEnglishTranslation());
        }

        // --- Fancy trapdoors ---
        acceptor.add(Constants.MOD_ID + ".fancytrapdoor.name.format", "Fancy %s Trapdoor");
        acceptor.add(Constants.MOD_ID + ".fancytrapdoor.type.format", "Variant: %s");
        acceptor.add(Constants.MOD_ID + ".fancytrapdoor.frame.header", "Frame:");
        acceptor.add(Constants.MOD_ID + ".fancytrapdoor.center.header", "Center:");
        acceptor.add(Constants.MOD_ID + ".fancytrapdoor.center.block.format", "  - Material: %s");
        acceptor.add(Constants.MOD_ID + ".fancytrapdoor.frame.block.format", "  - Material: %s");

        for (final FancyTrapdoorType value : FancyTrapdoorType.values())
        {
            acceptor.add(Constants.MOD_ID + ".fancytrapdoor.type.name." + value.getTranslationKeySuffix(), value.getDefaultEnglishTranslation());
        }

        // --- Paper walls ---
        acceptor.add(Constants.MOD_ID + ".blockpaperwall.name.format", "%s Framed Pane");
        acceptor.add(Constants.MOD_ID + ".blockpaperwall.header", "Materials:");
        acceptor.add(Constants.MOD_ID + ".blockpaperwall.frame.format", "  - Frame:     %s");
        acceptor.add(Constants.MOD_ID + ".blockpaperwall.center.format", "  - Center:    %s");

        acceptor.add(Constants.MOD_ID + ".blocktiledpaperwall.name.format", "%s Tiled Pane");
        acceptor.add(Constants.MOD_ID + ".blocktiledpaperwall.header", "Materials:");
        acceptor.add(Constants.MOD_ID + ".blocktiledpaperwall.frame.format", "  - Frame:     %s");
        acceptor.add(Constants.MOD_ID + ".blocktiledpaperwall.center.format", "  - Center:    %s");

        // --- Walls ---
        acceptor.add(Constants.MOD_ID + ".wall.name.format", "%s Wall");

        // --- All-brick ---
        acceptor.add(Constants.MOD_ID + ".dark_brick.name.format", "Dark %s Brick");
        acceptor.add(Constants.MOD_ID + ".light_brick.name.format", "Light %s Brick");

        acceptor.add(Constants.MOD_ID + ".dark_brick_stair.name.format", "Dark %s Brick Stair");
        acceptor.add(Constants.MOD_ID + ".light_brick_stair.name.format", "Light %s Brick Stair");

        acceptor.add(Constants.MOD_ID + ".allbrick.column.format", "Main Material: %s");

        // --- Dynamic timber frames ---
        acceptor.add(Constants.MOD_ID + ".dynamic.frame.name.format", "Dynamic Framed %s");
    }
}
