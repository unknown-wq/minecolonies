package com.minecolonies.api.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntry;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * A loot pool entry that rolls another loot table, named by key and looked up <em>when the pool is rolled</em>
 * rather than when the entry is built.
 * <p>
 * This exists because of a 26.3 ordering problem that vanilla's own {@code minecraft:loot_table} entry cannot
 * solve. In 26.3 loot tables are a registry ({@code minecraft:loot_table}, one of
 * {@code RegistryDataLoader#RELOADABLE_REGISTRIES}), and
 * {@link net.minecraft.world.level.storage.loot.entries.NestedLootTable#lootTableReference} therefore takes a
 * bound {@code Holder<LootTable>} instead of the {@code ResourceKey} it took before. The mod's injections into
 * other people's tables happen from {@code LootTableEvents.MODIFY}, which Fabric fires from inside the decode of
 * each element of that very registry -- so at that moment {@code Registries.LOOT_TABLE} does not exist in the
 * provider yet and there is no holder to hand over. Nor is an unbound stand-alone holder an option:
 * {@code NestedLootTable} dereferences with {@code Holder#value()} while rolling, which would move the crash from
 * boot to the moment a player opens a chest.
 * <p>
 * The key never needs resolving early, though. {@link LootContext#getResolver()} is the full lookup of the
 * reload the context was created in ({@code LootContext.Builder#create} takes it from
 * {@code MinecraftServer#reloadableRegistries}), so the table can simply be looked up at roll time, from exactly
 * the registry the roll belongs to. That also makes {@code /reload} a non-event: nothing is cached, and a pool
 * built during one reload can only ever be rolled through a context carrying that same reload's registries.
 * <p>
 * Behaviour otherwise matches {@code NestedLootTable} in its non-expanded form (which is what
 * NeoForge's {@code AddTableLootModifier} amounted to): one entry of the given weight that, when chosen, rolls
 * every pool of the referenced table.
 */
public class DeferredLootTableEntry extends UniformContainerBase
{
    /**
     * Registry id of this entry type.
     */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(MOD_ID, "deferred_loot_table");

    /**
     * Codec. Registered so the type is a first-class loot pool entry rather than something that only survives as
     * long as nobody looks at it; datapacks may use {@code "type": "minecolonies:deferred_loot_table"} with a
     * {@code "value"} table id, which behaves like {@code minecraft:loot_table} except that a missing target is
     * an empty roll instead of a load error.
     */
    public static final MapCodec<DeferredLootTableEntry> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(LootTable.KEY_CODEC.fieldOf("value").forGetter(entry -> entry.table))
                    .and(uniformFields(instance))
                    .apply(instance, DeferredLootTableEntry::new));

    /**
     * The table to roll.
     */
    private final ResourceKey<LootTable> table;

    private DeferredLootTableEntry(
      final ResourceKey<LootTable> table,
      final int weight,
      final int quality,
      final Optional<Holder<LootItemCondition>> condition,
      final Optional<Holder<LootItemFunction>> modifier)
    {
        super(weight, quality, condition, modifier);
        this.table = table;
    }

    /**
     * Registers the entry type. Called once from the mod entry point; the class is otherwise only reached from
     * the loot table event handler, which runs far too late to register anything.
     */
    public static void init()
    {
        Registry.register(BuiltInRegistries.LOOT_POOL_ENTRY_TYPE, ID, CODEC);
    }

    /**
     * @param table the table to roll when this entry is chosen.
     * @return a builder for an entry rolling that table.
     */
    @NotNull
    public static UniformContainerBase.Builder<?> lootTableReference(@NotNull final ResourceKey<LootTable> table)
    {
        return simpleBuilder((weight, quality, condition, modifier) ->
                               new DeferredLootTableEntry(table, weight, quality, condition, modifier));
    }

    @NotNull
    @Override
    public MapCodec<? extends UniformContainerBase> codec()
    {
        return CODEC;
    }

    @Override
    protected boolean expandRaw(@NotNull final LootContext context, @NotNull final Consumer<LootPoolEntry> output)
    {
        output.accept(new EntryBase()
        {
            @Override
            public void createItemStack(@NotNull final Consumer<ItemStack> stacks, @NotNull final LootContext rollContext)
            {
                final Optional<Holder.Reference<LootTable>> resolved = rollContext.getResolver().get(table);
                if (resolved.isEmpty())
                {
                    // validate() already reported this once per reload; rolling an absent table is simply empty,
                    // which is also what ReloadableServerRegistries.Holder#getLootTable does.
                    return;
                }
                // getRandomItemsRaw, not getRandomItems: no stack splitter, because the caller of this entry is
                // itself inside a table roll that will apply one. Recursion is caught by the visited-element
                // breadcrumb LootTable pushes there.
                resolved.get().value().getRandomItemsRaw(rollContext, stacks);
            }
        });
        return true;
    }

    @Override
    public void validate(@NotNull final ValidationContext context)
    {
        super.validate(context);

        final Optional<Holder.Reference<LootTable>> resolved;
        try
        {
            resolved = context.resolver().get(this.table);
        }
        catch (final UnsupportedOperationException e)
        {
            // A ValidationContext without a resolver ("References not allowed") -- e.g. the one used for loot
            // inside item components. This entry is never placed there by the mod, and there is nothing to check.
            return;
        }

        if (resolved.isEmpty())
        {
            context.reportProblem(new MissingTableProblem(this.table));
            return;
        }

        if (context.hasVisitedElement(this.table))
        {
            context.reportProblem(new ValidationContext.RecursiveElementReferenceProblem(this.table));
            return;
        }

        resolved.get()
          .value()
          .validate(context.enterElement(new ProblemReporter.ElementReferencePathElement(this.table), this.table));
    }

    /**
     * Reported when the referenced table is not in the registry of the reload being validated. Deliberately not
     * fatal: a missing addition should cost the addition, not the world.
     *
     * @param table the table that is missing.
     */
    public record MissingTableProblem(ResourceKey<LootTable> table) implements ProblemReporter.Problem
    {
        @NotNull
        @Override
        public String description()
        {
            return "Referenced loot table " + this.table.identifier() + " is missing; this entry will roll nothing";
        }
    }
}
