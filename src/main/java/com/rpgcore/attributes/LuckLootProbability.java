package com.rpgcore.attributes;

import com.rpgcore.data.ModAttachments;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.server.level.ServerPlayer;

/**
 * Applies the Luck percentage bonus to the actual random roll used by
 * vanilla living-entity loot chance conditions.
 */
public final class LuckLootProbability {
    private LuckLootProbability() {
    }

    /**
     * Converts a vanilla random roll r into r / (1 + bonus/100).
     * This is mathematically equivalent to testing the unmodified vanilla
     * chance p against a random roll with an effective chance of
     * min(1, p * (1 + bonus/100)).
     *
     * Only living-entity loot contexts caused by a player are affected.
     */
    public static float adjustRandomRoll(float vanillaRoll, LootContext context) {
        if (context == null
                || !(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof LivingEntity)) {
            return vanillaRoll;
        }

        ServerPlayer player = resolvePlayer(context.getParamOrNull(LootContextParams.ATTACKING_ENTITY));
        if (player == null) {
            DamageSource source = context.getParamOrNull(LootContextParams.DAMAGE_SOURCE);
            if (source != null) {
                player = resolvePlayer(source.getEntity());
                if (player == null) {
                    player = resolvePlayer(source.getDirectEntity());
                }
            }
        }

        if (player == null) {
            return vanillaRoll;
        }

        long luckPoints = player.getData(ModAttachments.PLAYER_RPG_DATA.get()).luck();
        if (luckPoints <= 0L) {
            return vanillaRoll;
        }

        double bonusPercent =
                AttributeFormulaCalculator.luckEquipmentDropBonusPercent(luckPoints);
        if (!Double.isFinite(bonusPercent) || bonusPercent <= 0.0) {
            return vanillaRoll;
        }

        double multiplier = 1.0 + bonusPercent / 100.0;
        return (float) (vanillaRoll / multiplier);
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
