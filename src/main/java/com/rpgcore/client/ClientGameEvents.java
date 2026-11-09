package com.rpgcore.client;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.progression.RpgGameplayEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

@EventBusSubscriber(modid = RpgCoreMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRpgDataCache.clear();
    }

    /**
     * Apply jump height during local prediction as well as on the server.
     * The server-side velocity alone is not enough to change the jump the
     * owning client predicts and renders immediately.
     */
    @SubscribeEvent
    public static void onLocalPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof LocalPlayer player && ClientRpgDataCache.hasServerData()) {
            PlayerRpgData data = ClientRpgDataCache.get();
            RpgGameplayEffects.applyJumpHeight(player, data);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (ClientModBusEvents.OPEN_RPG_MENU.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new RpgDataScreen());
            }
        }
    }
}
