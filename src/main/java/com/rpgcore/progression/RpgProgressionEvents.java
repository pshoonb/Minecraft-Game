package com.rpgcore.progression;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import net.minecraft.server.level.ServerPlayer;\nimport net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;\nimport net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Mirrors vanilla levels and applies player-only RPG gameplay effects. */
public final class RpgProgressionEvents {
    private RpgProgressionEvents() {}

    public static void onVanillaLevelChange(PlayerXpEvent.LevelChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int resultingLevel = player.experienceLevel + event.getLevels();
            RpgProgressionService.syncVanillaLevel(player, resultingLevel);
        }
    }

    public static void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
            RpgGameplayEffects.applyJumpHeight(player, data);
        }
    }

    /**
     * Check the untouched incoming damage before armor, shields, or absorption
     * are processed. Cancelling here makes below-threshold immunity complete,
     * rather than merely setting final health damage to zero after armor wears.
     */
    public static void onPlayerIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Preserve intentional bypasses such as out-of-world damage.
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        float threshold = RpgGameplayEffects.damageImmunityThreshold(data.defense());
        if (threshold > 0.0F && event.getOriginalAmount() < threshold) {
            event.setCanceled(true);
        }
    }

    /** Defense also protects against damage types that bypass vanilla armor. */
    public static void onPlayerDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
            float multiplier = RpgGameplayEffects.damageMultiplier(data.defense());
            event.setNewDamage(event.getNewDamage() * multiplier);
        }
    }

    /** Luck adds extra resource drops when a player mines supported ore blocks. */
    public static void onOreDrops(BlockDropsEvent event) {
        if (event.getBreaker() instanceof ServerPlayer player) {
            PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
            RpgGameplayEffects.applyMiningLuckDrops(event, data);
        }
    }

    /** Refresh saved modifiers once per second on the authoritative server. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            RpgProgressionService.refreshGameplayEffects(player);
        }
    }
}
