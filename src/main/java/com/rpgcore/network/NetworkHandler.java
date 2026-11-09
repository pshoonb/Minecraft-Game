package com.rpgcore.network;

import com.rpgcore.data.ModAttachments;
import com.rpgcore.data.PlayerAttribute;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.progression.RpgProgressionService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RequestRpgDataPayload.TYPE, RequestRpgDataPayload.STREAM_CODEC,
                NetworkHandler::handleRequest);
        registrar.playToServer(IncrementDiagnosticPayload.TYPE, IncrementDiagnosticPayload.STREAM_CODEC,
                NetworkHandler::handleIncrement);
        registrar.playToServer(AllocateAttributePayload.TYPE, AllocateAttributePayload.STREAM_CODEC,
                NetworkHandler::handleAllocate);
        registrar.playToServer(ResetAttributesPayload.TYPE, ResetAttributesPayload.STREAM_CODEC,
                NetworkHandler::handleReset);
    }

    private static void handleRequest(RequestRpgDataPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RpgProgressionService.initializeIfNeeded(player);
            sendSnapshot(player);
        }
    }

    private static void handleIncrement(IncrementDiagnosticPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        RpgProgressionService.initializeIfNeeded(player);
        PlayerRpgData current = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        int next = current.diagnosticCounter() >= 1_000_000
                ? 1_000_000 : current.diagnosticCounter() + 1;
        player.setData(ModAttachments.PLAYER_RPG_DATA.get(), current.withDiagnosticCounter(next));
        sendSnapshot(player);
    }

    private static void handleAllocate(AllocateAttributePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        PlayerAttribute attribute = PlayerAttribute.byNetworkId(payload.attributeId());
        RpgProgressionService.allocateAttribute(player, attribute, payload.amount());
    }

    private static void handleReset(ResetAttributesPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RpgProgressionService.resetAttributes(player);
        }
    }

    public static void sendSnapshot(ServerPlayer player) {
        PlayerRpgData data = player.getData(ModAttachments.PLAYER_RPG_DATA.get());
        PacketDistributor.sendToPlayer(player, new SyncRpgDataPayload(player.getUUID(), data));
    }
}
