package com.rpgcore.client;

import com.rpgcore.RpgCoreMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds an RPG Core entry point to the player's inventory screen.
 */
@EventBusSubscriber(modid = RpgCoreMod.MOD_ID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private static final int INVENTORY_IMAGE_HEIGHT = 166;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }

        int x = (screen.width - BUTTON_WIDTH) / 2;
        int y = (screen.height + INVENTORY_IMAGE_HEIGHT) / 2 + 4;

        Button openPanelButton = Button.builder(
                Component.translatable("button.rpgcore.open_panel"),
                button -> Minecraft.getInstance().setScreen(new RpgDataScreen(screen))
        ).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();

        event.addListener(openPanelButton);
    }
}
