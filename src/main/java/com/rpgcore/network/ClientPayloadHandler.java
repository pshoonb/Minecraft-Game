package com.rpgcore.network;

import com.rpgcore.client.ClientRpgDataCache;
import com.rpgcore.progression.RpgGameplayEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleSync(SyncRpgDataPayload payload, IPayloadContext context) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || !localPlayer.getUUID().equals(payload.playerId())) {
            return;
        }
        ClientRpgDataCache.update(payload.data());

        // Attribute modifiers are installed locally as well as on the server,
        // so client-side movement and block-breaking calculations see the new values.
        RpgGameplayEffects.apply(localPlayer, payload.data());
    }
}
