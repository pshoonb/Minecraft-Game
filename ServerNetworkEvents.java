package com.rpgcore.network;

import com.rpgcore.RpgCoreMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers the server's outbound sync channel without loading any client classes
 * on a dedicated server. The actual receiver is registered on physical clients.
 */
@EventBusSubscriber(modid = RpgCoreMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.DEDICATED_SERVER)
public final class ServerNetworkEvents {
    private ServerNetworkEvents() {
    }

    @SubscribeEvent
    public static void registerServerOutboundPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                SyncRpgDataPayload.TYPE,
                SyncRpgDataPayload.STREAM_CODEC,
                (payload, context) -> {
                    // A dedicated server only sends this payload; it never receives it.
                }
        );
    }
}
