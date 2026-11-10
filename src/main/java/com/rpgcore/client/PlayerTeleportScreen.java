package com.rpgcore.client;

import com.rpgcore.network.RequestTeleportTargetsPayload;
import com.rpgcore.network.TeleportTargetsPayload;
import com.rpgcore.network.TeleportToPlayerPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import java.util.UUID;

/** Scrollable online-player picker; the server validates the selected UUID on confirmation. */
public final class PlayerTeleportScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int SCROLL_PIXELS_PER_NOTCH = 24;
    private final Screen parentScreen;
    private UUID selectedPlayerId;
    private Button confirmButton;
    private Button backButton;
    private int panelLeft, panelRight, panelTop, panelBottom;
    private int listLeft, listRight, listTop, listBottom;
    private int footerY, scrollOffset, maxScroll;

    public PlayerTeleportScreen(Screen parentScreen) {
        super(Component.translatable("screen.rpgcore.teleport.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        selectedPlayerId = null;
        scrollOffset = 0;
        int panelWidth = Math.min(420, Math.max(160, width - 24));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        listLeft = panelLeft + 10;
        listRight = panelRight - 10;
        maxScroll = 0;

        ClientTeleportTargetsCache.clear();
        int buttonsLeft = (width - 212) / 2;
        confirmButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.rpgcore.teleport.confirm"),
                button -> confirmTeleport())
                .bounds(buttonsLeft, footerY, 124, 20).build());
        confirmButton.active = false;
        backButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.rpgcore.teleport.back"),
                button -> onClose())
                .bounds(buttonsLeft + 132, footerY, 80, 20).build());
        updateLayout(0);

        PacketDistributor.sendToServer(new RequestTeleportTargetsPayload());
    }

    private void updateLayout(int targetCount) {
        int maxPanelHeight = Math.max(140, height - 24);
        int maxListHeight = Math.max(ROW_HEIGHT, Math.min(240, maxPanelHeight - 124));
        int desiredListHeight = Math.max(ROW_HEIGHT * 2, targetCount * ROW_HEIGHT);
        int listHeight = Math.min(maxListHeight, desiredListHeight);

        int panelHeight = 124 + listHeight;
        panelTop = Math.max(12, (height - panelHeight) / 2);
        panelBottom = panelTop + panelHeight;
        listTop = panelTop + 58;
        listBottom = listTop + listHeight;
        footerY = listBottom + 34;
        confirmButton.setY(footerY);
        backButton.setY(footerY);
    }

    private void confirmTeleport() {
        if (selectedPlayerId == null) return;
        PacketDistributor.sendToServer(new TeleportToPlayerPayload(selectedPlayerId));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF0D0E10);
        graphics.fill(0, 0, width, 3, 0xFFE0B45C);
        List<TeleportTargetsPayload.Target> targets = ClientTeleportTargetsCache.get();
        updateLayout(targets.size());

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF171819);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF5A4931);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 9, 0xFFFFD778);
        graphics.drawCenteredString(font,
                Component.translatable("screen.rpgcore.teleport.subtitle"),
                width / 2, panelTop + 25, 0xFFD5CBBB);

        maxScroll = Math.max(0, targets.size() * ROW_HEIGHT - (listBottom - listTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        graphics.fill(listLeft, listTop - 2, listRight, listBottom + 2, 0xFF101112);
        graphics.drawString(font,
                Component.translatable("screen.rpgcore.teleport.online", targets.size()),
                listLeft + 3, listTop - 14, 0xFFFFD778, false);

        if (targets.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.rpgcore.teleport.empty"),
                    width / 2, listTop + 16, 0xFFB8B0A2);
        } else {
            graphics.enableScissor(listLeft, listTop, listRight, listBottom);
            for (int i = 0; i < targets.size(); i++) {
                TeleportTargetsPayload.Target target = targets.get(i);
                int rowY = listTop + i * ROW_HEIGHT - scrollOffset;
                boolean selected = target.playerId().equals(selectedPlayerId);
                boolean hovered = mouseX >= listLeft && mouseX < listRight
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
                if (selected) {
                    graphics.fill(listLeft + 1, rowY + 1, listRight - 1,
                            rowY + ROW_HEIGHT - 1, 0xFF514026);
                    graphics.fill(listLeft + 1, rowY + 1, listLeft + 4,
                            rowY + ROW_HEIGHT - 1, 0xFFE4B85E);
                    graphics.fill(listLeft + 4, rowY + 1, listRight - 1,
                            rowY + 2, 0xFF9C7940);
                } else {
                    graphics.fill(listLeft + 1, rowY + 1, listRight - 1,
                            rowY + ROW_HEIGHT - 1, hovered ? 0xFF292723 : 0xFF1D1C1A);
                }
                graphics.drawString(font, target.playerName(), listLeft + 10, rowY + 7,
                        selected ? 0xFFFFE3A1 : 0xFFF0E9DC, false);
            }
            graphics.disableScissor();
        }

        String selectedName = null;
        if (selectedPlayerId != null) {
            for (TeleportTargetsPayload.Target target : targets) {
                if (target.playerId().equals(selectedPlayerId)) {
                    selectedName = target.playerName();
                    break;
                }
            }
        }
        if (selectedName != null) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.rpgcore.teleport.selected", selectedName),
                    width / 2, listBottom + 10, 0xFFFFD778);
        }

        if (maxScroll > 0) {
            int trackX = panelRight - 5;
            int trackHeight = listBottom - listTop;
            int contentHeight = targets.size() * ROW_HEIGHT;
            int thumbHeight = Math.max(18, trackHeight * trackHeight / contentHeight);
            int thumbTravel = Math.max(0, trackHeight - thumbHeight);
            int thumbY = listTop
                    + (int) Math.round(thumbTravel * (double) scrollOffset / maxScroll);
            graphics.fill(trackX, listTop, trackX + 2, listBottom, 0xFF34312B);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFD2A75D);
        }

        confirmButton.active = selectedName != null;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listLeft && mouseX < listRight
                && mouseY >= listTop && mouseY < listBottom) {
            int index = (int) ((mouseY - listTop + scrollOffset) / ROW_HEIGHT);
            List<TeleportTargetsPayload.Target> targets = ClientTeleportTargetsCache.get();
            if (index >= 0 && index < targets.size()) {
                selectedPlayerId = targets.get(index).playerId();
                confirmButton.active = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (mouseX < listLeft || mouseX >= listRight || mouseY < listTop || mouseY >= listBottom
                || maxScroll <= 0 || Math.abs(scrollY) < 0.01) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset = Math.max(0, Math.min(maxScroll,
                scrollOffset - (int) Math.round(scrollY * SCROLL_PIXELS_PER_NOTCH)));
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
