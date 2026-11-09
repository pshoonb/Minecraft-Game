package com.rpgcore.attributes;

import com.rpgcore.data.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Applies the RPG Luck percentage bonus to player-caused living-entity loot.
 */
public final class LuckLootProbability {
    private LuckLootProbability() {
    }

    /**
     * Converts a vanilla random roll r into r / (1 + bonus/100).
     * This is equivalent to multiplying the predicate's existing probability
     * by (1 + bonus/100), with the predicate naturally capped at 100%.
     */
    public static float adjustRandomRoll(float vanillaRoll, LootContext context) {
        ServerPlayer player = resolveAttackingPlayer(context);
        if (player == null) {
            return vanillaRoll;
        }

        double bonusPercent = luckBonusPercent(player);
        if (bonusPercent <= 0.0) {
            return vanillaRoll;
        }

        return (float) (vanillaRoll / (1.0 + bonusPercent / 100.0));
    }

    /**
     * Returns true when this living-entity loot-pool invocation earns one
     * additional roll. The chance is the cumulative Luck percentage, capped
     * at 100%. The pool's normal entry selection is reused, so its original
     * weights and relative probabilities are unchanged.
     */
    public static boolean shouldAddExtraPoolRoll(LootContext context) {
        ServerPlayer player = resolveAttackingPlayer(context);
        if (player == null) {
            return false;
        }

        double bonusPercent = luckBonusPercent(player);
        if (bonusPercent <= 0.0) {
            return false;
        }

        double chance = Math.min(1.0, bonusPercent / 100.0);
        return context.getRandom().nextFloat() < chance;
    }

    private static double luckBonusPercent(ServerPlayer player) {
        long luckPoints = player.getData(ModAttachments.PLAYER_RPG_DATA.get()).luck();
        if (luckPoints <= 0L) {
            return 0.0;
        }

        double bonusPercent =
                AttributeFormulaCalculator.luckEquipmentDropBonusPercent(luckPoints);
        return Double.isFinite(bonusPercent) ? Math.max(0.0, bonusPercent) : 0.0;
    }

    /**
     * A player is eligible only when THIS_ENTITY is a living loot target and
     * the loot context identifies a player attacker. Player-owned projectiles
     * are resolved through both the attacking entity and damage source.
     */
    private static ServerPlayer resolveAttackingPlayer(LootContext context) {
        if (context == null
                || !(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof LivingEntity)) {
            return null;
        }

        ServerPlayer player =
                resolvePlayer(context.getParamOrNull(LootContextParams.ATTACKING_ENTITY));
        if (player != null) {
            return player;
        }

        DamageSource source = context.getParamOrNull(LootContextParams.DAMAGE_SOURCE);
        if (source == null) {
            return null;
        }

        player = resolvePlayer(source.getEntity());
        return player != null ? player : resolvePlayer(source.getDirectEntity());
    }

    private static ServerPlayer resolvePlayer(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return player;
        }
        if (entity instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
