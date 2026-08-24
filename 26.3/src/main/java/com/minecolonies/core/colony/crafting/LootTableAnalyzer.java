package com.minecolonies.core.colony.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.items.component.AdventureData;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.Utils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import com.minecolonies.api.util.Tuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility helper that analyzes a loot table to determine a likely list of drops, along with
 * their drop rate, for presentation in JEI (and perhaps other purposes).  This is just
 * informational and in particular shouldn't be used to actually generate loot -- due to
 * constraints with how loot tables are stored this can only produce an approximation of a
 * subset of possible loot results.  Good enough to be indicative but that's all.
 * Currently it only supports a very limited set of conditions and properties; just enough
 * for current usage by MineColonies recipes.  If tables are extended with additional
 * conditions or properties then this would have to be adjusted to cope as well.
 */
public final class LootTableAnalyzer
{
    private LootTableAnalyzer() { }

    /**
     * Evaluate a loot table and report possible drops.
     *
     * @param provider    the registry provider
     * @param lootTableId the loot table id
     * @return the list of possible drops
     */
    public static List<LootDrop> toDrops(final HolderLookup.Provider provider, @NotNull final ResourceKey<LootTable> lootTableId)
    {
        try
        {
            return toDrops(provider, provider.lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(lootTableId));
        }
        catch (final IllegalStateException ex)
        {
            Log.getLogger().debug(String.format("Failed to parse loot table from %s", lootTableId), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Evaluate a loot table and report possible drops.
     *
     * @param provider  the registry provider
     * @param lootTable the loot table
     * @return the list of possible drops
     */
    public static List<LootDrop> toDrops(@NotNull final HolderLookup.Provider provider, @NotNull final Holder<LootTable> lootTable)
    {
        try
        {
            final JsonObject lootTableJson = Utils.serializeCodecMessToJson(LootTable.DIRECT_CODEC, provider, lootTable.value()).getAsJsonObject();
            return toDrops(provider, lootTableJson);
        }
        catch (final JsonParseException ex)
        {
            Log.getLogger().error(String.format("Failed to parse loot table from %s", lootTable.unwrapKey().orElse(null)), ex);
            return Collections.emptyList();
        }
    }

    /**
     * Evaluate a loot table and report possible drops.
     *
     * @param provider the registry provider
     * @param lootTableJson the loot table json
     * @return the list of possible drops
     */
    public static List<LootDrop> toDrops(@NotNull final HolderLookup.Provider provider, @NotNull final JsonObject lootTableJson)
    {
        final List<LootDrop> drops = new ArrayList<>();

        if (!lootTableJson.has("pools"))
        {
            return drops;
        }

        final JsonArray pools = GsonHelper.getAsJsonArray(lootTableJson, "pools");
        for (final JsonElement pool : pools)
        {
            final float rolls = processNumber(pool.getAsJsonObject().get("rolls"), 1.0f);
            final JsonArray entries = GsonHelper.getAsJsonArray(pool.getAsJsonObject(), "entries", new JsonArray());
            final float totalWeight = StreamSupport.stream(entries.spliterator(), false)
                    .filter(entry ->
                    {
                        final String type = GsonHelper.getAsString(entry.getAsJsonObject(), "type");
                        return type.equals("minecraft:empty") || type.equals("minecraft:item") || type.equals("minecraft:tag") || type.equals("minecraft:loot_table") || type.equals("minecraft:alternatives");
                    })
                    .mapToInt(entry -> GsonHelper.getAsInt(entry.getAsJsonObject(), "weight", 1))
                    .sum();
            final JsonArray conditions = conditionsOf(pool.getAsJsonObject());
            final boolean conditional = !conditions.isEmpty();
            if (conditionsSeemImpossible(conditions)) { continue; }
            final float modifier = adjustModifier(1f, conditions);

            for (final JsonElement ej : entries)
            {
                final JsonObject entryJson = ej.getAsJsonObject();
                final float weight = GsonHelper.getAsFloat(entryJson, "weight", 1);
                final List<LootDrop> entryDrops = entryToDrops(provider, entryJson);
                for (final LootDrop drop : entryDrops)
                {
                    drops.add(new LootDrop(drop.getItemStacks(), drop.getProbability() * (weight / totalWeight) * rolls * modifier, drop.getQuality() * rolls, conditional || drop.getConditional()));
                }
            }
        }

        drops.sort(Comparator.comparing(LootDrop::getProbability).reversed());
        return drops;
    }

    /**
     * Parse a specific entry and try to determine the possible drops.
     *
     * @param provider the registry provider
     * @param entryJson the entry json
     * @return the list of possible drops
     */
    @NotNull
    private static List<LootDrop> entryToDrops(@NotNull final HolderLookup.Provider provider, @NotNull final JsonObject entryJson)
    {
        final List<LootDrop> drops = new ArrayList<>();
        final String type = GsonHelper.getAsString(entryJson, "type");
        switch (type)
        {
            case "minecraft:item" -> {
                final Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(GsonHelper.getAsString(entryJson, "name")));
                final float quality = GsonHelper.getAsFloat(entryJson, "quality", 0);
                float modifier = 1.0F;
                final JsonArray conditions = conditionsOf(entryJson);
                final boolean conditional = !conditions.isEmpty();
                if (conditionsSeemImpossible(conditions)) { break; }
                ItemStack stack = new ItemStack(item);
                final JsonArray modifiers = modifiersOf(entryJson);
                if (!modifiers.isEmpty())
                {
                    final Tuple<ItemStack, Float> result = processFunctions(provider, stack, modifiers);
                    stack = result.getA();
                    modifier = result.getB();
                }
                modifier = adjustModifier(modifier, conditions);
                if (stack.getItem().equals(ModItems.adventureToken))
                {
                    final List<LootDrop> mobDrops = expandAdventureToken(provider, stack);
                    for (final LootDrop drop : mobDrops)
                    {
                        drops.add(new LootDrop(drop.getItemStacks(), drop.getProbability(), drop.getQuality() + quality, drop.getConditional() || conditional));
                    }
                }
                else
                {
                    drops.add(new LootDrop(Collections.singletonList(stack), modifier, quality, conditional));
                }
            }
            case "minecraft:loot_table" -> {
                final JsonElement value = entryJson.get("value");
                final List<LootDrop> tableDrops;
                if (value.isJsonObject())
                {
                    // inline loot table
                    tableDrops = toDrops(provider, value.getAsJsonObject());
                }
                else
                {
                    // loot table reference
                    final Identifier table = Identifier.parse(value.getAsString());
                    tableDrops = toDrops(provider, ResourceKey.create(Registries.LOOT_TABLE, table));
                }

                final float quality = GsonHelper.getAsFloat(entryJson, "quality", 0);
                final JsonArray conditions = conditionsOf(entryJson);
                final boolean conditional = !conditions.isEmpty();
                if (conditionsSeemImpossible(conditions)) { break; }
                for (final LootDrop drop : tableDrops)
                {
                    drops.add(new LootDrop(drop.getItemStacks(), drop.getProbability(), drop.getQuality() + quality, drop.getConditional() || conditional));
                }
            }
            case "minecraft:alternatives" -> {
                final JsonArray children = GsonHelper.getAsJsonArray(entryJson, "children", new JsonArray());
                // currently, the only one of these we're dealing with is "silk touch or not", so we'll just find the
                // first one that doesn't have conditions and call it a day, at least for now... (or failing that, just the last)
                final JsonObject childJson = StreamSupport.stream(children.spliterator(), false)
                        .map(JsonElement::getAsJsonObject)
                        .filter(j -> !j.has("condition"))
                        .findFirst()
                        .orElse(children.get(children.size() - 1).getAsJsonObject());
                drops.addAll(entryToDrops(provider, childJson));
            }
        }
        return drops;
    }

    /**
     * Collect the conditions attached to a loot pool or entry.
     *
     * <p>Port note (26.3): a pool/entry no longer carries a {@code "conditions"} array. It carries at most one
     * {@code "condition"}, and what used to be an array is now a single {@code minecraft:all_of} whose members are
     * nested under {@code "terms"} -- compare the vanilla ghast table before and after. Flattening {@code all_of}
     * back into a list restores the old meaning exactly, because every caller treats the list as a conjunction.</p>
     *
     * <p>{@code minecraft:any_of} is deliberately <em>not</em> flattened: it is a disjunction, and flattening it
     * would turn alternatives into requirements. 26.2 also saw it as one opaque element, so this is unchanged --
     * {@link #conditionsSeemImpossible} still scans it as text and {@link #adjustModifier} still ignores it.</p>
     *
     * <p>An element may also be a bare string rather than an object: {@code LootItemCondition.CODEC} is a holder
     * codec, so a condition that lives in the predicate registry serialises as its id (e.g.
     * {@code "minecraft:tool/can_silk_touch"}, 123 occurrences in the 26.3 vanilla data pack). Those are passed
     * through untouched and simply match none of the types the callers look for.</p>
     *
     * @param owner the pool or entry json.
     * @return the conditions, as a conjunction.
     */
    @NotNull
    private static JsonArray conditionsOf(@NotNull final JsonObject owner)
    {
        final JsonArray result = new JsonArray();
        final JsonElement condition = owner.get("condition");
        if (condition != null)
        {
            flattenCondition(condition, result);
        }
        return result;
    }

    /**
     * Appends one condition to {@code output}, unrolling {@code minecraft:all_of} recursively.
     *
     * @param condition the condition json.
     * @param output    the list being built.
     */
    private static void flattenCondition(@NotNull final JsonElement condition, @NotNull final JsonArray output)
    {
        if (condition.isJsonObject()
              && "minecraft:all_of".equals(GsonHelper.getAsString(condition.getAsJsonObject(), "type", "")))
        {
            for (final JsonElement term : GsonHelper.getAsJsonArray(condition.getAsJsonObject(), "terms", new JsonArray()))
            {
                flattenCondition(term, output);
            }
        }
        else
        {
            output.add(condition);
        }
    }

    /**
     * Collect the functions attached to a loot entry.
     *
     * <p>Port note (26.3): {@code "functions": [...]} became {@code "modifier"}, a single composed function. It
     * therefore serialises as an <em>object</em> when there is one and as an <em>array</em> when there are several,
     * and both forms occur in the same file (the vanilla ghast table has an array on its item entries and a lone
     * object on its third pool). Normalising to an array here keeps the rest of this class shape-agnostic.</p>
     *
     * <p>Note this reads the entry's own modifier only. Pools may carry one too, and that is ignored -- as pool-level
     * {@code "functions"} always were.</p>
     *
     * @param entryJson the entry json.
     * @return the functions, in application order.
     */
    @NotNull
    private static JsonArray modifiersOf(@NotNull final JsonObject entryJson)
    {
        final JsonElement modifier = entryJson.get("modifier");
        if (modifier == null)
        {
            return new JsonArray();
        }
        if (modifier.isJsonArray())
        {
            return modifier.getAsJsonArray();
        }

        final JsonArray single = new JsonArray();
        single.add(modifier);
        return single;
    }

    /**
     * Quick plausibility check to see if conditions on a loot table entry are impossible to ever be met by a colonist,
     * and thus should not be listed as a possible drop. This does not actually evaluate the conditions; just checks
     * what type of conditions have been specified.
     *
     * @param conditions The conditions JSON array
     * @return true if some condition seems impossible to fulfil for any colonist
     */
    private static boolean conditionsSeemImpossible(@NotNull final JsonArray conditions)
    {
        for (final JsonElement condition : conditions)
        {
            final String json = condition.toString();
            if ((json.contains("killed_by_player") || json.contains("damage_source_properties")) && !json.contains("minecraft:inverted"))
            {
                // very unlikely that colonists would match any specific damage sources (disables froglights)
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to adjust the drop probability according to conditions present on the loot pool and/or entry.
     * @param modifier   the current probability modifier.
     * @param conditions The conditions JSON array.
     * @return the new probability modifier.
     */
    private static float adjustModifier(float modifier, @NotNull final JsonArray conditions)
    {
        for (final JsonElement cj : conditions)
        {
            // 26.3: a condition may be a bare registry id instead of an object -- nothing to read from those.
            if (!cj.isJsonObject()) { continue; }

            final JsonObject condition = cj.getAsJsonObject();
            switch (GsonHelper.getAsString(condition, "type", ""))
            {
                case "minecraft:random_chance" ->
                    // 26.3: `chance` is a NumberProvider, so it can be an object as well as a bare float
                    // (LootItemRandomChanceCondition.java:11-13).
                    modifier *= processNumber(condition.get("chance"), 1f);

                case "minecraft:random_chance_with_enchanted_bonus" ->
                    // 26.3 renamed minecraft:random_chance_with_looting to this and split the field in two:
                    // `unenchanted_chance` is the one that applies to a colonist, and `enchanted_chance` is
                    // ignored exactly as the looting adjustment was before
                    // (LootItemRandomChanceWithEnchantedBonusCondition.java:19-25).
                    modifier *= GsonHelper.getAsFloat(condition, "unenchanted_chance", 1f);

                default -> { }
            }
        }
        return modifier;
    }

    /**
     * Replaces an {@link ModItems#adventureToken} with the drops from defeating the corresponding monster.
     *
     * @param provider the registry provider
     * @param token the adventure token
     * @return the list of possible drops
     */
    @NotNull
    private static List<LootDrop> expandAdventureToken(
      @NotNull final HolderLookup.Provider provider,
      @NotNull final ItemStack token)
    {
        final AdventureData component = AdventureData.readFromItemStack(token);
        if (component != null)
        {
            return component.entityType().getDefaultLootTable().map(table -> toDrops(provider, table)).orElseGet(Collections::emptyList);
        }
        return Collections.emptyList();
    }

    /**
     * Groups and sorts drops by probability (most likely first, with similar-probability items in the
     * same "slot" as alternatives).
     *
     * @param input the unsorted list of drops
     * @return the sorted list of drops
     */
    @NotNull
    public static List<LootDrop> consolidate(@NotNull final List<LootDrop> input)
    {
        return input.stream()
                .collect(Collectors.groupingBy(LootDrop::hashCode))
                .values().stream()
                .map(LootDrop::new)
                .sorted(Comparator.comparing(LootDrop::getProbability).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Rudimentary parsing of loot table functions, primarily focusing on just the ones that are most commonly present
     * in the loot tables that we care about and have a visible effect in the JEI display.
     *
     * @param provider the registry provider
     * @param stack the original {@link ItemStack} to operate on
     * @param functions the functions array json
     * @return the modified stack and 'quality' modifier
     */
    private static Tuple<ItemStack, Float> processFunctions(@NotNull final HolderLookup.Provider provider,
                                                            @NotNull ItemStack stack,
                                                            @NotNull final JsonArray functions)
    {
        float modifier = 1.0F;

        for (final JsonElement je : functions)
        {
            // 26.3: like conditions, a function may be a bare registry id rather than an inline object.
            if (!je.isJsonObject()) { continue; }

            final JsonObject function = je.getAsJsonObject();
            final String name = GsonHelper.getAsString(function, "type", "");

            try
            {
                switch (name)
                {
                    case "minecraft:set_count":         // SetItemCountFunction
                        final Tuple<Integer, Float> result = processCount(function.get("count"));
                        stack.setCount(result.getA());
                        modifier *= result.getB();
                        break;

                    case "minecraft:set_damage":        // SetItemDamageFunction
                        if (stack.isDamageableItem())
                        {
                            float damage = 1.0F - processNumber(function.get("damage"), 0F);
                            stack.setDamageValue(Mth.floor(damage * stack.getMaxDamage()));
                        }
                        break;

                    case "minecraft:set_potion":        // SetPotionFunction
                        final String id = GsonHelper.getAsString(function, "id");
                        final Holder<Potion> potion = BuiltInRegistries.POTION.get(Identifier.tryParse(id)).orElse(null);
                        if (potion != null)
                        {
                            stack.update(DataComponents.POTION_CONTENTS, PotionContents.EMPTY, potion, PotionContents::withPotion);
                        }
                        break;

                    case "minecraft:set_components":    // SetComponentsFunction
                        final DataComponentPatch patch = Utils.deserializeCodecMessFromJson(DataComponentPatch.CODEC, provider, function.get("components"));
                        stack.applyComponentsAndValidate(patch);
                        break;

                    case "minecraft:enchant_with_levels":   // EnchantWithLevelsFunction
                        final int levels = processNumber(function.get("levels"), 1);
                        // 26.3: RegistryCodecs moved to net.minecraft.core.registries.codec and homogeneousList
                        // was renamed holderSet -- this is exactly the codec vanilla's EnchantWithLevelsFunction
                        // uses for the same field (EnchantWithLevelsFunction.java:35).
                        final MapCodec<Optional<HolderSet<Enchantment>>> optionsCodec = RegistryCodecs.holderSet(Registries.ENCHANTMENT).optionalFieldOf("options");
                        final RegistryOps<JsonElement> ops = provider.createSerializationContext(JsonOps.INSTANCE);
                        final Optional<HolderSet<Enchantment>> options = optionsCodec.decode(ops, ops.getMap(function).getOrThrow()).getOrThrow();
                        final Stream<Holder<Enchantment>> enchantments = options.map(HolderSet::stream)
                                .orElseGet(() -> provider.lookupOrThrow(Registries.ENCHANTMENT).listElements().map(holder -> (Holder<Enchantment>) holder));
                        stack = EnchantmentHelper.enchantItem(RandomSource.create(), stack, levels, enchantments);
                        break;

                    case "minecraft:apply_bonus":               // ApplyBonusCount
                    case "minecraft:enchanted_count_increase":  // EnchantedCountIncreaseFunction
                        // just ignore this for now; we could possibly increase the count a little or
                        // add a tooltip to indicate it's boosted by looting/fortune, but meh.
                        break;

                    case "minecraft:furnace_smelt":     // SmeltItemFunction
                        // this is mostly just to cook the meat if an animal is on fire, which
                        // we can safely ignore.
                        break;

                    case "minecraft:explosion_decay":   // ApplyExplosionDecay
                        // ignore this; we're not expecting explosions
                        break;

                    default:
                        Log.getLogger().warn("Unhandled modifier in loot table: {}", name);
                        break;
                }
            }
            catch (Throwable e)
            {
                Log.getLogger().error("Failed to parse {} in loot table", name, e);
            }
        }

        return new Tuple<>(stack, modifier);
    }

    /**
     * Evaluates an integer-like loot quantity used as an item count (which may be a fixed value or one of
     * several random number types).  If zero is a possible return, also reports a probability modifier
     * (chance to be non-zero).
     *
     * @param json the count json
     * @return the expected upper bound on possible random values, plus a probability modifier
     */
    private static Tuple<Integer, Float> processCount(@Nullable final JsonElement json)
    {
        if (json == null) return new Tuple<>(1, 1.0F);

        if (json.isJsonObject() && GsonHelper.getAsString(json.getAsJsonObject(), "type", "").equals("minecraft:uniform"))
        {
            final int min = GsonHelper.getAsInt(json.getAsJsonObject(), "min", 0);
            final int max = GsonHelper.getAsInt(json.getAsJsonObject(), "max", 1);

            // the extra wrinkle is to alter the probability when 0 is a possible count.
            return new Tuple<>(max, min == 0 ? max / (max + 1.0F) : 1.0F);
        }

        return new Tuple<>(processNumber(json, 1), 1.0F);
    }

    /**
     * Evaluates an int-like loot quantity (which may be a fixed value or one of several random number types).
     *
     * @param json the quantity json
     * @return the expected upper bound on possible random values
     */
    private static int processNumber(@Nullable final JsonElement json, final int defaultValue)
    {
        if (json == null) return defaultValue;

        if (json.isJsonObject())
        {
            switch (GsonHelper.getAsString(json.getAsJsonObject(), "type", ""))
            {
                case "minecraft:constant":
                    return GsonHelper.getAsInt(json.getAsJsonObject(), "value", defaultValue);
                case "minecraft:uniform":
                    return GsonHelper.getAsInt(json.getAsJsonObject(), "max", defaultValue);
                default:
                    return defaultValue;
            }
        }
        else if (json.isJsonPrimitive())
        {
            return json.getAsJsonPrimitive().getAsInt();
        }

        return defaultValue;
    }

    /**
     * Evaluates a float-like loot quantity (which may be a fixed value or one of several random number types).
     *
     * @param json the quantity json
     * @return the expected upper bound on possible random values
     */
    private static float processNumber(@Nullable final JsonElement json, final float defaultValue)
    {
        if (json == null) return defaultValue;

        if (json.isJsonObject())
        {
            switch (GsonHelper.getAsString(json.getAsJsonObject(), "type", ""))
            {
                case "minecraft:constant":
                    return GsonHelper.getAsFloat(json.getAsJsonObject(), "value", defaultValue);
                case "minecraft:uniform":
                    return GsonHelper.getAsFloat(json.getAsJsonObject(), "max", defaultValue);
                default:
                    return defaultValue;
            }
        }
        else if (json.isJsonPrimitive())
        {
            return json.getAsJsonPrimitive().getAsFloat();
        }

        return defaultValue;
    }

    /**
     * Represents a single possible drop from a loot table.
     */
    public static class LootDrop
    {
        private final List<ItemStack> stacks;
        private final float probability;
        private final float quality;
        private final boolean conditional;

        public LootDrop(@NotNull final List<ItemStack> stacks, final float probability, final float quality, final boolean conditional)
        {
            this.stacks = stacks;
            this.probability = probability;
            this.quality = quality;
            this.conditional = conditional;
        }

        public LootDrop(@NotNull final List<LootDrop> drops)
        {
            this.stacks = drops.stream().flatMap(d -> d.getItemStacks().stream()).collect(Collectors.toList());
            this.probability = drops.get(0).getProbability();
            this.quality = drops.get(0).getQuality();
            this.conditional = drops.get(0).getConditional();
        }

        /** The loot item for this drop (as alternatives). */
        @NotNull public List<ItemStack> getItemStacks() { return this.stacks; }
        /** The approximate probability that this item will drop. */
        public float getProbability() { return this.probability; }
        /** If non-zero, the probability is affected by the citizen's skills (positively or negatively). */
        public float getQuality() { return this.quality; }
        /** If true, there are special conditions on whether this will drop or not. */
        public boolean getConditional() { return this.conditional; }

        /** This should be unique, covering all the properties except for the stacks */
        @Override
        public int hashCode()
        {
            return Objects.hash(probability, quality, conditional);
        }

        /** Copy a LootDrop to a packet buffer */
        public void serialize(@NotNull final RegistryFriendlyByteBuf buffer)
        {
            int prevIndex = buffer.writerIndex();
            buffer.writeVarInt(stacks.size());
            for (final ItemStack stack : stacks)
            {
                try
                {
                    Utils.serializeCodecMess(buffer, stack);
                }
                catch (Exception e)
                {
                    Log.getLogger().warn("Error serializing item stack: " + stack, e);
                    buffer.writerIndex(prevIndex);
                    buffer.writeVarInt(0);
                    break;
                }
            }

            buffer.writeFloat(probability);
            buffer.writeFloat(quality);
            buffer.writeBoolean(conditional);
        }

        /** Recover a LootDrop from a packet buffer */
        public static LootDrop deserialize(@NotNull final RegistryFriendlyByteBuf buffer)
        {
            final int size = buffer.readVarInt();
            final List<ItemStack> stacks = new ArrayList<>(size);
            for (int i = 0; i < size; ++i)
            {
                stacks.add(Utils.deserializeCodecMess(buffer));
            }
            final float probability = buffer.readFloat();
            final float quality = buffer.readFloat();
            final boolean conditional = buffer.readBoolean();
            return new LootDrop(stacks, probability, quality, conditional);
        }
    }
}
