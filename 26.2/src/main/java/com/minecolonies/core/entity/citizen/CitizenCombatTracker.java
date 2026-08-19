package com.minecolonies.core.entity.citizen;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.util.constant.TranslationConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Adaptation of CombatTracker to properly handle citizen death messages.
 */
public class CitizenCombatTracker extends CombatTracker
{
    private static final Style         INTENTIONAL_GAME_DESIGN_STYLE =
      Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create("https://bugs.mojang.com/browse/MCPE-28723")))
        .withHoverEvent(new HoverEvent.ShowText(Component.literal("MCPE-28723")));
    private final        EntityCitizen citizen;

    public CitizenCombatTracker(EntityCitizen citizen)
    {
        super(citizen);
        this.citizen = citizen;
    }

    /**
     * PORT-NOTE(26.2): CombatTracker#getMostSignificantFall() is private in 26.2 and not covered by
     * the AccessWidener, so this is a verbatim copy of the vanilla logic over the widened
     * {@code entries} list.
     *
     * @return the fall entry that should drive the death message, or null.
     */
    private CombatEntry mostSignificantFall()
    {
        CombatEntry result = null;
        CombatEntry alternative = null;
        float altDamage = 0.0F;
        float bestFall = 0.0F;

        for (int i = 0; i < this.entries.size(); i++)
        {
            final CombatEntry entry = this.entries.get(i);
            final CombatEntry previous = i > 0 ? this.entries.get(i - 1) : null;
            final DamageSource source = entry.source();
            final boolean isFakeFall = source.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL);
            final float fallDistance = isFakeFall ? Float.MAX_VALUE : entry.fallDistance();
            if ((source.is(DamageTypeTags.IS_FALL) || isFakeFall) && fallDistance > 0.0F && (result == null || fallDistance > bestFall))
            {
                result = i > 0 ? previous : entry;
                bestFall = fallDistance;
            }

            if (entry.fallLocation() != null && (alternative == null || entry.damage() > altDamage))
            {
                alternative = entry;
                altDamage = entry.damage();
            }
        }

        if (bestFall > 5.0F && result != null)
        {
            return result;
        }
        return altDamage > 5.0F && alternative != null ? alternative : null;
    }

    /**
     * How a citizen is named in a death message: "the Knight Johnathan" when it had a job, plain otherwise.
     * <p>
     * Pulled out of {@link #getDeathMessage()} so that {@link #getDeathMessage(DamageSource)} and
     * {@code CitizenAging#killAway} name a citizen the same way. A death away from the loaded world and a death with a
     * body ought to read alike; they did not, because only this method knew how to build the name.
     *
     * @param citizen the citizen.
     * @return the name component.
     */
    @NotNull
    public static Component deathName(@NotNull final ICitizenData citizen)
    {
        final IJob<?> job = citizen.getJob();
        if (job != null)
        {
            return Component.translatableEscape(
              TranslationConstants.WORKER_DESC,
              Component.translatableEscape(job.getJobRegistryEntry().getTranslationKey()),
              citizen.getName());
        }
        return Component.translatable(TranslationConstants.CITIZEN_DEATH_DESC, citizen.getName());
    }

    /**
     * The death message for a death that the tracker may know nothing about.
     * <p>
     * 26.2/Fabric: the tracker only holds entries for damage that actually went through {@code hurt}. A citizen killed
     * by calling {@code die} directly -- which is how {@code CitizenAging} kills of old age -- leaves it empty, and
     * vanilla's fallback for an empty tracker is {@code death.attack.generic}: "%s died". So every death of old age was
     * announced as an unexplained death and the mod's own {@code death.attack.oldage} was never reached, even though
     * the colony event log right below it already read the message off the damage source and got it right.
     * <p>
     * The tracker is still preferred whenever it has anything, because for a violent death it says materially more --
     * fall variants, "killed by X while fighting Y", the attacker's named weapon. This only replaces the case where it
     * has nothing to say.
     *
     * @param source what killed the citizen.
     * @return the message.
     */
    @NotNull
    public Component getDeathMessage(@NotNull final DamageSource source)
    {
        if (!entries.isEmpty())
        {
            return getDeathMessage();
        }

        // DamageSource#getLocalizedDeathMessage, with the citizen's decorated name in place of its display name.
        final Component nameComponent = deathName(citizen.getCitizenData());
        final String key = "death.attack." + source.type().msgId();
        final Entity attacker = source.getEntity() == null ? source.getDirectEntity() : source.getEntity();
        return attacker == null
                 ? Component.translatableEscape(key, nameComponent)
                 : Component.translatableEscape(key, nameComponent, attacker.getDisplayName());
    }

    @Override
    @NotNull
    public Component getDeathMessage()
    {
        final Component nameComponent = deathName(citizen.getCitizenData());
        //CombatTracker#getDeathMessage
        if (entries.isEmpty())
        {
            return Component.translatableEscape("death.attack.generic", nameComponent);
        }
        else
        {
            DamageSource lastSource = entries.get(entries.size() - 1).source();
            DeathMessageType messageType = lastSource.type().deathMessageType();
            CombatEntry fallEntry = mostSignificantFall();
            if (messageType == DeathMessageType.FALL_VARIANTS && fallEntry != null)
            {
                //CombatTracker#getFallMessage
                DamageSource fallSource = fallEntry.source();
                Entity lastEntity = lastSource.getEntity();
                if (!fallSource.is(DamageTypeTags.IS_FALL) && !fallSource.is(DamageTypeTags.ALWAYS_MOST_SIGNIFICANT_FALL))
                {
                    Entity fallEntity = fallSource.getEntity();
                    Component fallMessage = fallEntity == null ? null : fallEntity.getDisplayName();
                    Component lastMessage = lastEntity == null ? null : lastEntity.getDisplayName();
                    if (fallMessage != null && !fallMessage.equals(lastMessage))
                    {
                        //CombatTracker#getMessageForAssistedFall
                        ItemStack stack = fallEntity instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
                        return !stack.isEmpty() && stack.has(DataComponents.CUSTOM_NAME)
                                 ? Component.translatableEscape("death.fell.assist.item", nameComponent, fallMessage, stack.getDisplayName())
                                 : Component.translatableEscape("death.fell.assist", nameComponent, fallMessage);
                    }
                    else
                    {
                        if (lastMessage != null)
                        {
                            //CombatTracker#getMessageForAssistedFall
                            ItemStack stack = lastEntity instanceof LivingEntity livingentity ? livingentity.getMainHandItem() : ItemStack.EMPTY;
                            return !stack.isEmpty() && stack.has(DataComponents.CUSTOM_NAME) ? Component.translatableEscape("death.fell.finish.item",
                              nameComponent,
                              lastMessage,
                              stack.getDisplayName()) : Component.translatableEscape("death.fell.finish", nameComponent, lastMessage);
                        }
                        return Component.translatableEscape("death.fell.killer", nameComponent);
                    }
                }
                else
                {
                    return Component.translatableEscape(Objects.requireNonNullElse(fallEntry.fallLocation(), FallLocation.GENERIC).languageKey(), nameComponent);
                }
            }
            else if (messageType == DeathMessageType.INTENTIONAL_GAME_DESIGN)
            {
                String s = "death.attack." + lastSource.getMsgId();
                return Component.translatableEscape(s + ".message", nameComponent, ComponentUtils.wrapInSquareBrackets(Component.translatableEscape(s + ".link")).withStyle(INTENTIONAL_GAME_DESIGN_STYLE));
            }
            else
            {
                //DamageSource#getLocalizedDeathMessage
                String s = "death.attack." + lastSource.type().msgId();
                Entity entity = lastSource.getEntity();
                Entity directEntity = lastSource.getDirectEntity();
                if (directEntity == null && entity == null)
                {
                    LivingEntity living = citizen.getKillCredit();
                    return living != null ? Component.translatableEscape(s + ".player", nameComponent, living.getDisplayName()) : Component.translatableEscape(s, nameComponent);
                }
                else
                {
                    Component component = entity == null ? directEntity.getDisplayName() : entity.getDisplayName();
                    ItemStack stack = entity instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
                    return !stack.isEmpty() && stack.has(DataComponents.CUSTOM_NAME)
                             ? Component.translatableEscape(s + ".item", nameComponent, component, stack.getDisplayName())
                             : Component.translatableEscape(s, nameComponent, component);
                }
            }
        }
    }
}
