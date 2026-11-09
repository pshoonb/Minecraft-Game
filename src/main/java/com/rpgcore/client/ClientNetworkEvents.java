package com.rpgcore.client;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.network.ClientPayloadHandler;
import com.rpgcore.network.SyncRpgDataPayload;
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
    }
}
