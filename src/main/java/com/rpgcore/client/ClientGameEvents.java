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

    /** Apply jump height during local prediction as well as on the server. */
    @SubscribeEvent
    public static void onLocalPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof LocalPlayer player && ClientRpgDataCache.hasServerData()) {
            PlayerRpgData data = ClientRpgDataCache.get();
            RpgGameplayEffects.applyJumpHeight(player, data);
        }
    }

    /**
     * Keep the owning client's attributes in sync with the authoritative
     * snapshot as well. Stable modifier IDs make this safe to repeat.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && ClientRpgDataCache.hasServerData()
                && minecraft.player.tickCount % 20 == 0) {
            RpgGameplayEffects.apply(minecraft.player, ClientRpgDataCache.get());
        }

        while (ClientModBusEvents.OPEN_RPG_MENU.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(new RpgDataScreen());
            }
        }
    }
}
