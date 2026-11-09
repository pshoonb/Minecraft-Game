package com.rpgcore.network;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerRpgData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkHandler {
    private NetworkHandler() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestRpgDataPayload.TYPE, RequestRpgDataPayload.STREAM_CODEC,
                NetworkHandler::handleRequest);
        registrar.playToServer(IncrementDiagnosticPayload.TYPE, IncrementDiagnosticPayload.STREAM_CODEC,
                NetworkHandler::handleIncrement);
    }

    private static void handleRequest(RequestRpgDataPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            sendSnapshot(serverPlayer);
        }
    }

    private static void handleIncrement(IncrementDiagnosticPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        PlayerRpgData current = serverPlayer.getData(ModAttachments.PLAYER_RPG_DATA.get());
        int next = current.diagnosticCounter() >= 1_000_000
                ? 1_000_000
                : current.diagnosticCounter() + 1;

        // The client sends only an intent. The server owns and writes the value.
        serverPlayer.setData(ModAttachments.PLAYER_RPG_DATA.get(), current.withDiagnosticCounter(next));
        sendSnapshot(serverPlayer);
    }

    public static void sendSnapshot(ServerPlayer player) {
        PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        PacketDistributor.sendToPlayer(player, new SyncRpgDataPayload(player.getUUID(), data));
    }
}
