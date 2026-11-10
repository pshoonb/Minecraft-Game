package com.rpgcore.client;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.client.ClientTeleportTargetsCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client-side lifecycle hooks for the cached RPG player data. */
@EventBusSubscriber(modid = RpgCoreMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRpgDataCache.clear();
        ClientTeleportTargetsCache.clear();
    }
}
