package com.rpgcore.client;

import com.rpgcore.network.RequestTeleportTargetsPayload;
import com.rpgcore.network.TeleportTargetsPayload;
import com.rpgcore.network.TeleportToPlayerPayload;
import com.rpgcore.network.TeleportIncomingRequestsPayload;
import com.rpgcore.network.RespondTeleportRequestPayload;
import com.rpgcore.client.ClientTeleportRequestsCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Scrollable online-player picker; the server validates the selected UUID on confirmation. */
public final class PlayerTeleportScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int SCROLL_PIXELS_PER_NOTCH = 24;

    private final Screen parentScreen;
    private final List<StringWidget> fixedTextWidgets = new ArrayList<>();
    private final List<StringWidget> playerNameWidgets = new ArrayList<>();

    private StringWidget titleLabel;
    private StringWidget subtitleLabel;
    private StringWidget onlineLabel;
    private StringWidget emptyLabel;
    private StringWidget selectedLabel;

    private UUID selectedPlayerId;
    private Button confirmButton;
    private Button backButton;
    private Button sendTabButton;
    private Button receivedTabButton;
    private final List<StringWidget> incomingNameWidgets = new ArrayList<>();
    private final List<Button> incomingAcceptButtons = new ArrayList<>();
    private final List<Button> incomingDenyButtons = new ArrayList<>();
    private boolean showingIncomingRequests;
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
        fixedTextWidgets.clear();
        playerNameWidgets.clear();
        incomingNameWidgets.clear();
        incomingAcceptButtons.clear();
        incomingDenyButtons.clear();
        showingIncomingRequests = false;

        int panelWidth = Math.min(420, Math.max(160, width - 24));
        panelLeft = (width - panelWidth) / 2;
        panelRight = panelLeft + panelWidth;
        listLeft = panelLeft + 10;
        listRight = panelRight - 10;
        maxScroll = 0;

        titleLabel = addFixedText(0, 0, width, 10, title, 0xFFFFD778, true);
        subtitleLabel = addFixedText(0, 0, width, 10,
                Component.translatable("screen.rpgcore.teleport.subtitle"),
                0xFFD5CBBB, true);
        onlineLabel = addFixedText(listLeft + 3, 0,
                Math.max(1, listRight - listLeft - 6), 10,
                Component.empty(), 0xFFFFD778, false);
        emptyLabel = addFixedText(listLeft, 0,
                Math.max(1, listRight - listLeft), 10,
                Component.translatable("screen.rpgcore.teleport.empty"),
                0xFFB8B0A2, true);
        selectedLabel = addFixedText(0, 0, width, 10,
                Component.empty(), 0xFFFFD778, true);
        emptyLabel.visible = false;
        selectedLabel.visible = false;

        int buttonsLeft = (width - 212) / 2;
        confirmButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.rpgcore.teleport.confirm"),
                button -> confirmTeleport())
                .bounds(buttonsLeft, 0, 124, 20).build());
        confirmButton.active = false;
        sendTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.rpgcore.teleport.tab_send"), b -> switchTab(false)).bounds(0, 0, 110, 20).build());
        receivedTabButton = addRenderableWidget(Button.builder(Component.translatable("screen.rpgcore.teleport.tab_received"), b -> switchTab(true)).bounds(0, 0, 110, 20).build());
        backButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.rpgcore.teleport.back"),
                button -> onClose())
                .bounds(buttonsLeft + 132, 0, 80, 20).build());
        updateLayout(0);

        ClientTeleportTargetsCache.clear();
        PacketDistributor.sendToServer(new RequestTeleportTargetsPayload());
    }

    private StringWidget addFixedText(int x, int y, int widgetWidth, int widgetHeight,
                                      Component message, int color, boolean centered) {
        StringWidget widget = addText(x, y, widgetWidth, widgetHeight, message, color, centered);
        fixedTextWidgets.add(widget);
        return widget;
    }

    private StringWidget addText(int x, int y, int widgetWidth, int widgetHeight,
                                 Component message, int color, boolean centered) {
        StringWidget widget = new StringWidget(x, y, widgetWidth, widgetHeight, message, font)
                .setColor(color);
        if (centered) {
            widget.alignCenter();
        } else {
            widget.alignLeft();
        }
        return addRenderableOnly(widget);
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

        sendTabButton.setX(panelLeft + 12); sendTabButton.setY(panelTop + 42);
        receivedTabButton.setX(panelLeft + 128); receivedTabButton.setY(panelTop + 42);
        confirmButton.setY(footerY);
        backButton.setY(footerY);

        titleLabel.setY(panelTop + 9);
        subtitleLabel.setY(panelTop + 25);
        onlineLabel.setY(listTop - 14);
        emptyLabel.setY(listTop + Math.max(0, (listHeight - 10) / 2));
        selectedLabel.setY(listBottom + 10);
    }

    private void updatePlayerNameWidgets(List<TeleportTargetsPayload.Target> targets) {
        int textWidth = Math.max(1, listRight - listLeft - 26);
        while (playerNameWidgets.size() < targets.size()) {
            playerNameWidgets.add(addText(
                    listLeft + 10, listTop, textWidth, 10,
                    Component.empty(), 0xFFF0E9DC, false));
        }

        for (int i = 0; i < playerNameWidgets.size(); i++) {
            StringWidget widget = playerNameWidgets.get(i);
            if (i >= targets.size()) {
                widget.visible = false;
                continue;
            }

            TeleportTargetsPayload.Target target = targets.get(i);
            int rowY = listTop + i * ROW_HEIGHT - scrollOffset;
            widget.setMessage(Component.literal(target.playerName()));
            widget.setX(listLeft + 10);
            widget.setY(rowY + 7);
            widget.visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
        }
    }

    private void switchTab(boolean incoming) {
        showingIncomingRequests = incoming;
        selectedPlayerId = null;
        confirmButton.active = false;
        scrollOffset = 0;
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
        List<TeleportIncomingRequestsPayload.Request> incoming = ClientTeleportRequestsCache.get();
        int displayedCount = showingIncomingRequests ? incoming.size() : targets.size();
        updateLayout(displayedCount);

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF171819);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF5A4931);

        maxScroll = Math.max(0, displayedCount * ROW_HEIGHT - (listBottom - listTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        sendTabButton.active = showingIncomingRequests;
        receivedTabButton.active = !showingIncomingRequests;
        onlineLabel.setMessage(Component.translatable(showingIncomingRequests
                ? "screen.rpgcore.teleport.incoming_count" : "screen.rpgcore.teleport.online", displayedCount));
        emptyLabel.setMessage(Component.translatable(showingIncomingRequests
                ? "screen.rpgcore.teleport.incoming_empty" : "screen.rpgcore.teleport.empty"));
        emptyLabel.visible = displayedCount == 0;

        graphics.fill(listLeft, listTop - 2, listRight, listBottom + 2, 0xFF101112);
        if (displayedCount == 0) {
            selectedLabel.visible = false;
        } else {
            for (int i = 0; i < displayedCount; i++) {
                int rowY = listTop + i * ROW_HEIGHT - scrollOffset;
                boolean selected = !showingIncomingRequests && targets.get(i).playerId().equals(selectedPlayerId);
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
            }

            String selectedName = null;
            for (TeleportTargetsPayload.Target target : targets) {
                if (target.playerId().equals(selectedPlayerId)) {
                    selectedName = target.playerName();
                    break;
                }
            }
            selectedLabel.visible = selectedName != null;
            if (selectedName != null) {
                selectedLabel.setMessage(Component.translatable(
                        "screen.rpgcore.teleport.selected", selectedName));
            }
        }

        if (showingIncomingRequests) {
            updateIncomingRequestWidgets(incoming);
            selectedLabel.visible = false;
            confirmButton.visible = false;
        } else {
            updatePlayerNameWidgets(targets);
            hideIncomingRequestWidgets();
            confirmButton.visible = true;
            confirmButton.active = selectedLabel.visible;
        }

        // Match RpgDataScreen: render list text as vanilla StringWidgets while clipped,
        // then render fixed labels and buttons after the scissor is disabled.
        boolean[] fixedVisibility = new boolean[fixedTextWidgets.size()];
        for (int i = 0; i < fixedTextWidgets.size(); i++) {
            StringWidget widget = fixedTextWidgets.get(i);
            fixedVisibility[i] = widget.visible;
            widget.visible = false;
        }

        boolean confirmVisible = confirmButton.visible;
        boolean backVisible = backButton.visible;
        confirmButton.visible = false;
        backButton.visible = false;
        sendTabButton.visible = false;
        receivedTabButton.visible = false;

        graphics.enableScissor(listLeft, listTop, listRight, listBottom);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        for (int i = 0; i < fixedTextWidgets.size(); i++) {
            StringWidget widget = fixedTextWidgets.get(i);
            widget.visible = fixedVisibility[i];
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        sendTabButton.visible = true; receivedTabButton.visible = true;
        sendTabButton.render(graphics, mouseX, mouseY, partialTick);
        receivedTabButton.render(graphics, mouseX, mouseY, partialTick);
        confirmButton.visible = confirmVisible;
        backButton.visible = backVisible;
        if (confirmVisible) {
            confirmButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (backVisible) {
            backButton.render(graphics, mouseX, mouseY, partialTick);
        }

        if (showingIncomingRequests) {
            renderIncomingButtons(graphics, mouseX, mouseY, partialTick);
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
    }

    private void updateIncomingRequestWidgets(List<TeleportIncomingRequestsPayload.Request> requests) {
        int textWidth = Math.max(1, listRight - listLeft - 140);
        while (incomingNameWidgets.size() < requests.size()) {
            incomingNameWidgets.add(addText(listLeft + 8, listTop, textWidth, 10,
                    Component.empty(), 0xFFF0E9DC, false));
            int index = incomingNameWidgets.size() - 1;
            incomingAcceptButtons.add(addRenderableWidget(Button.builder(
                    Component.translatable("screen.rpgcore.teleport.accept"),
                    b -> respondToRequest(index, true)).bounds(0, 0, 54, 18).build()));
            incomingDenyButtons.add(addRenderableWidget(Button.builder(
                    Component.translatable("screen.rpgcore.teleport.deny"),
                    b -> respondToRequest(index, false)).bounds(0, 0, 54, 18).build()));
        }
        for (int i = 0; i < incomingNameWidgets.size(); i++) {
            boolean visible = i < requests.size();
            StringWidget name = incomingNameWidgets.get(i);
            Button accept = incomingAcceptButtons.get(i);
            Button deny = incomingDenyButtons.get(i);
            name.visible = visible; accept.visible = visible; deny.visible = visible;
            if (!visible) continue;
            int rowY = listTop + i * ROW_HEIGHT - scrollOffset;
            name.setMessage(Component.literal(requests.get(i).playerName()));
            name.setX(listLeft + 8); name.setY(rowY + 5);
            name.visible = rowY + ROW_HEIGHT > listTop && rowY < listBottom;
            accept.setX(listRight - 112); accept.setY(rowY + 2); accept.visible = name.visible;
            deny.setX(listRight - 56); deny.setY(rowY + 2); deny.visible = name.visible;
        }
    }

    private void hideIncomingRequestWidgets() {
        for (StringWidget widget : incomingNameWidgets) widget.visible = false;
        for (Button button : incomingAcceptButtons) button.visible = false;
        for (Button button : incomingDenyButtons) button.visible = false;
    }

    private void renderIncomingButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (Button button : incomingAcceptButtons) if (button.visible) button.render(graphics, mouseX, mouseY, partialTick);
        for (Button button : incomingDenyButtons) if (button.visible) button.render(graphics, mouseX, mouseY, partialTick);
    }

    private void respondToRequest(int index, boolean accept) {
        List<TeleportIncomingRequestsPayload.Request> requests = ClientTeleportRequestsCache.get();
        if (index < 0 || index >= requests.size()) return;
        PacketDistributor.sendToServer(new RespondTeleportRequestPayload(requests.get(index).requesterId(), accept));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listLeft && mouseX < listRight
                && mouseY >= listTop && mouseY < listBottom) {
            int index = (int) ((mouseY - listTop + scrollOffset) / ROW_HEIGHT);
            List<TeleportTargetsPayload.Target> targets = ClientTeleportTargetsCache.get();
            if (!showingIncomingRequests && index >= 0 && index < targets.size()) {
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
