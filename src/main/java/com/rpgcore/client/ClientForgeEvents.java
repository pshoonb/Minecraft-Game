package com.rpgcore.client;

import com.rpgcore.RpgCoreMod;
import com.rpgcore.attributes.AttributeFormulaCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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

        boolean teleportUnlocked = ClientRpgDataCache.hasServerData()
                && ClientRpgDataCache.get().teleportUnlocked();
        int groupWidth = BUTTON_WIDTH + (teleportUnlocked ? 5 + 58 : 0);
        int x = (screen.width - groupWidth) / 2;
        int y = (screen.height + INVENTORY_IMAGE_HEIGHT) / 2 + 4;

        Button openPanelButton = Button.builder(
                Component.translatable("button.rpgcore.open_panel"),
                button -> Minecraft.getInstance().setScreen(new RpgDataScreen(screen))
        ).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();

        event.addListener(openPanelButton);

        if (teleportUnlocked) {
            Button teleportButton = Button.builder(
                    Component.translatable("button.rpgcore.open_teleport"),
                    button -> Minecraft.getInstance().setScreen(new PlayerTeleportScreen(screen))
            ).bounds(x + BUTTON_WIDTH + 5, y, 58, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("button.rpgcore.open_teleport.tooltip")))
                    .build();
            event.addListener(teleportButton);
        }
    }
    /**
     * Mirrors the server-side mining bonus in the local player's dig-speed
     * calculation so block-breaking progress and server completion stay aligned.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof LocalPlayer)
                || !ClientRpgDataCache.hasServerData()) {
            return;
        }

        long points = ClientRpgDataCache.get().miningSpeed();
        double bonusPercent = AttributeFormulaCalculator.miningSpeedPercent(points);
        if (bonusPercent > 0.0) {
            event.setNewSpeed((float) (event.getNewSpeed() * (1.0 + bonusPercent / 100.0)));
        }
    }

}
