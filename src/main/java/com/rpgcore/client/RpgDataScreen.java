package com.rpgcore.client;

import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.network.IncrementDiagnosticPayload;
import com.rpgcore.network.RequestRpgDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RpgDataScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 238;

    public RpgDataScreen() {
        super(Component.translatable("screen.rpgcore.title"));
    }

    @Override
    protected void init() {
        int panelTop = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.rpgcore.increment_test"),
                button -> PacketDistributor.sendToServer(new IncrementDiagnosticPayload())
        ).bounds(this.width / 2 - 88, panelTop + 205, 176, 20).build());

        PacketDistributor.sendToServer(new RequestRpgDataPayload());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB0100F0D);

        int panelLeft = (this.width - Math.min(PANEL_WIDTH, this.width - 12)) / 2;
        int panelWidth = Math.min(PANEL_WIDTH, this.width - 12);
        int panelTop = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
        int panelRight = panelLeft + panelWidth;
        int panelBottom = Math.min(this.height - 4, panelTop + PANEL_HEIGHT);

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xF01B1A18);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 3, 0xFFE0B45C);
        graphics.fill(panelLeft + 5, panelTop + 5, panelRight - 5, panelBottom - 5, 0xFF3B3022);
        graphics.fill(panelLeft + 7, panelTop + 7, panelRight - 7, panelBottom - 7, 0xF01A1917);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 14, 0xFFFFD778);
        graphics.drawCenteredString(this.font, Component.translatable("screen.rpgcore.subtitle"),
                this.width / 2, panelTop + 28, 0xFFB9B1A3);

        int textX = panelLeft + 22;
        int textY = panelTop + 47;
        PlayerRpgData data = ClientRpgDataCache.get();

        if (!ClientRpgDataCache.hasServerData()) {
            graphics.drawString(this.font, Component.translatable("screen.rpgcore.waiting"),
                    textX, textY, 0xFFFFD778);
        } else {
            drawStat(graphics, "Health", data.health(), textX, textY);
            drawStat(graphics, "Defense", data.defense(), textX, textY + 15);
            drawStat(graphics, "Attack", data.attack(), textX, textY + 30);
            drawStat(graphics, "Speed", data.speed(), textX, textY + 45);
            drawStat(graphics, "Jump Height", data.jumpHeight(), textX, textY + 60);
            drawStat(graphics, "Luck", data.luck(), textX, textY + 75);
            drawStat(graphics, "Mining Speed", data.miningSpeed(), textX, textY + 90);

            graphics.drawString(this.font,
                    "Available Points: " + data.availablePoints()
                            + "   Lifetime Earned: " + data.lifetimeEarnedPoints(),
                    textX, textY + 111, 0xFFD6D0C6);
            graphics.drawString(this.font,
                    "Highest Level: " + data.lifetimeHighestLevel()
                            + "   Reward Baseline: " + data.rewardBaselineLevel(),
                    textX, textY + 126, 0xFFD6D0C6);
            graphics.drawString(this.font,
                    "Persistence Test Counter: " + data.diagnosticCounter(),
                    textX, textY + 148, 0xFF86D9A2);
        }

        var player = Minecraft.getInstance().player;
        if (player != null) {
            String shortId = player.getUUID().toString();
            graphics.drawString(this.font, "Player UUID: " + shortId,
                    textX, panelTop + 190, 0xFF9A948B);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawStat(GuiGraphics graphics, String name, int value, int x, int y) {
        graphics.drawString(this.font, name, x, y, 0xFFE6DED0);
        graphics.drawString(this.font, Integer.toString(value), x + 190, y, 0xFFFFD778);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
