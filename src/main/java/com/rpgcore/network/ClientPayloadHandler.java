package com.rpgcore.network;

import com.rpgcore.client.ClientRpgDataCache;
import com.rpgcore.client.ClientTeleportTargetsCache;
import com.rpgcore.client.ClientTeleportRequestsCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleIncomingRequests(TeleportIncomingRequestsPayload payload, IPayloadContext context) {
        ClientTeleportRequestsCache.update(payload.requests());
    }

    public static void handleTeleportTargets(TeleportTargetsPayload payload, IPayloadContext context) {
        ClientTeleportTargetsCache.update(payload.targets());
    }

    public static void handleSync(SyncRpgDataPayload payload, IPayloadContext context) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null || !localPlayer.getUUID().equals(payload.playerId())) {
            return;
        }
        ClientRpgDataCache.update(payload.data());
    }
}
