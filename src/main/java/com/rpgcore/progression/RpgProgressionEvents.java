package com.rpgcore.progression;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
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
     * Reapply the expected modifiers once per second. This is an idempotent
     * safety net for login/respawn timing and other systems that may replace
     * the player's attribute instances after the initial allocation update.
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            RpgProgressionService.refreshGameplayEffects(player);
        }
    }
}
