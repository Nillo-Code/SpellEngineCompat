package net.spell_engine.spellbinding;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpellBinding {
    public static final String name = "spell_binding";
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, name);
    private static final float LIBRARY_POWER_BASE = 10;
    private static final float LIBRARY_POWER_MULTIPLIER = 1.5F;
    private static final int LIBRARY_POWER_CAP = 22;

    public record Offer(int id, int cost, int levelRequirement) {  }

    public static List<Offer> offersFor(ItemStack itemStack, int libraryPower) {
        var container = SpellContainerHelper.containerFromItemStack(itemStack);
        var pool = SpellContainerHelper.getPool(container);
        if (container == null || pool == null || pool.spellIds().isEmpty()) {
            return List.of();
        }
        var spells = new HashMap<Identifier, Spell>();
        for (var id: pool.spellIds()) {
            spells.put(id, SpellRegistry.getSpell(id));
        }
        return spells.entrySet().stream()
                .filter(entry -> entry.getValue().learn != null
                        && entry.getValue().learn.tier > 0)
                .sorted(SpellContainerHelper.spellSorter)
                .map(entry -> new Offer(
                    SpellRegistry.rawId(entry.getKey()),
                    entry.getValue().learn.tier * entry.getValue().learn.level_cost_per_tier,
                    entry.getValue().learn.tier * entry.getValue().learn.level_requirement_per_tier
                ))
                .filter(offer -> (libraryPower == LIBRARY_POWER_CAP)
                        || ((LIBRARY_POWER_BASE + libraryPower * LIBRARY_POWER_MULTIPLIER) >= offer.levelRequirement))
                .collect(Collectors.toList());
    }

    public static class State {
        public enum ApplyState { ALREADY_APPLIED, NO_MORE_SLOT, APPLICABLE, INVALID }
        public ApplyState state;
        public State(ApplyState state, Requirements requirements) {
            this.state = state;
            this.requirements = requirements;
        }

        public Requirements requirements;
        public record Requirements(int lapisCost, int levelCost, int requiredLevel) {
            public boolean satisfiedFor(PlayerEntity player, int lapisCount) {
                return player.isCreative() ||
                        (metRequiredLevel(player)
                        && hasEnoughLapis(lapisCount)
                        && hasEnoughLevelsToSpend(player));
            }

            public boolean metRequiredLevel(PlayerEntity player) {
                return player.experienceLevel >= requiredLevel;
            }

            public boolean hasEnoughLapis(int lapisCount) {
                return lapisCount >= lapisCost;
            }

            public boolean hasEnoughLevelsToSpend(PlayerEntity player) {
                return player.experienceLevel >= levelCost;
            }
        }

        public static State of(int spellId, ItemStack itemStack, int cost, int requiredLevel) {
            var validId = SpellRegistry.fromRawId(spellId);
            if (validId.isEmpty()) {
                return new State(ApplyState.INVALID, null);
            }
            return State.of(validId.get(), itemStack, cost, requiredLevel);
        }

        public static State of(Identifier spellId, ItemStack itemStack, int cost, int requiredLevel) {
            var container = SpellContainerHelper.containerFromItemStack(itemStack);
            int lapisCost = cost;
            int levelCost = cost;
            var requirements = new Requirements(lapisCost, levelCost, requiredLevel);
            if (container == null) {
                return new State(ApplyState.INVALID, requirements);
            }
            if (container.spell_ids.contains(spellId.toString())) {
                return new State(ApplyState.ALREADY_APPLIED, requirements);
            }
            if (container.spell_ids.size() >= container.max_spell_count) {
                return new State(ApplyState.NO_MORE_SLOT, requirements);
            }
            return new State(ApplyState.APPLICABLE, requirements);
        }

        public boolean readyToApply(PlayerEntity player, int lapisCount) {
            return state == SpellBinding.State.ApplyState.APPLICABLE
                    && requirements != null
                    && requirements.satisfiedFor(player, lapisCount);
        }
    }
}
