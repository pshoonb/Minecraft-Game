package com.rpgcore.network;

import com.rpgcore.progression.RpgProgressionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class PlayerSyncEvents {
    private PlayerSyncEvents() {}

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RpgProgressionService.initializeIfNeeded(player);
            NetworkHandler.sendSnapshot(player);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RpgProgressionService.initializeIfNeeded(player);
            NetworkHandler.sendSnapshot(player);
        }
    }
}
