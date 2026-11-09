package com.rpgcore.network;

import com.rpgcore.attributes.PlayerAttributeEffects;
import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.progression.RpgProgressionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class PlayerSyncEvents {
    private PlayerSyncEvents() {}

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RpgProgressionService.syncVanillaLevel(player);
            PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
            PlayerAttributeEffects.apply(player, data);
            NetworkHandler.sendSnapshot(player);
        }
    }

    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RpgProgressionService.syncVanillaLevel(player);
            PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
            PlayerAttributeEffects.apply(player, data);
            NetworkHandler.sendSnapshot(player);
        }
    }
}
