package com.rpgcore.client;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.network.ClientPayloadHandler;
import com.rpgcore.network.SyncRpgDataPayload;
import com.rpgcore.network.TeleportTargetsPayload;
import com.rpgcore.network.TeleportIncomingRequestsPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = RpgCoreMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientNetworkEvents {
    private ClientNetworkEvents() {
    }

    @SubscribeEvent
    public static void registerClientPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                SyncRpgDataPayload.TYPE,
                SyncRpgDataPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSync
        );
        event.registrar("1").playToClient(
                TeleportIncomingRequestsPayload.TYPE,
                TeleportIncomingRequestsPayload.STREAM_CODEC,
                ClientPayloadHandler::handleIncomingRequests
        );
        event.registrar("1").playToClient(
                TeleportTargetsPayload.TYPE,
                TeleportTargetsPayload.STREAM_CODEC,
                ClientPayloadHandler::handleTeleportTargets
        );
    }
}
