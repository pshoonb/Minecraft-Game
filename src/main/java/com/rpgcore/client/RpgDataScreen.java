package com.rpgcore.client;

import com.rpgcore.attributes.AttributeFormulaCalculator;
import com.rpgcore.data.PlayerAttribute;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.network.AllocateAttributePayload;
import com.rpgcore.network.RequestRpgDataPayload;
import com.rpgcore.network.ResetAttributesPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full-window player level, attribute allocation, and scrollable attribute effects screen. */
public final class RpgDataScreen extends Screen {
    private static final PlayerAttribute[] ATTRIBUTES = PlayerAttribute.values();
    private static final int GRID_COLUMNS = 2;
    private static final int ROW_GAP = 6;
    private static final int SCROLL_PIXELS_PER_NOTCH = 24;

    private final Screen parentScreen;
    private final StringWidget[] nameLabels = new StringWidget[ATTRIBUTES.length];
    private final StringWidget[] valueLabels = new StringWidget[ATTRIBUTES.length];
    private final StringWidget[] effectLabels = new StringWidget[ATTRIBUTES.length];
    private final int[] cardXs = new int[ATTRIBUTES.length];
    private final int[] cardBaseYs = new int[ATTRIBUTES.length];
    private final int[] cardYs = new int[ATTRIBUTES.length];
    private final int[] cardWidths = new int[ATTRIBUTES.length];
    private final int[] cardHeights = new int[ATTRIBUTES.length];
    private final int[] nameOffsets = new int[ATTRIBUTES.length];
    private final int[] effectOffsets = new int[ATTRIBUTES.length];
    private final List<Button> attributeButtons = new ArrayList<>();
    private final List<Integer> buttonBaseYs = new ArrayList<>();
    private final List<Integer> buttonHeights = new ArrayList<>();
    private final List<StringWidget> fixedTextWidgets = new ArrayList<>();

    private StringWidget levelLabel;
    private StringWidget pointsLabel;
    private StringWidget waitingLabel;
    private Button resetButton;
    private Button backButton;
    private PlayerRpgData lastDisplayedData;

    private int contentMargin;
    private int headerBottom;
    private int resetButtonY;
    private int panelLeft;
    private int panelRight;
    private int viewportLeft;
    private int viewportRight;
    private int viewportTop;
    private int viewportBottom;
    private int contentHeight;
    private int scrollOffset;
    private int maxScroll;

    public RpgDataScreen() {
        this(null);
    }

    public RpgDataScreen(Screen parentScreen) {
        super(Component.translatable("screen.rpgcore.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        attributeButtons.clear();
        buttonBaseYs.clear();
        buttonHeights.clear();
        fixedTextWidgets.clear();
        lastDisplayedData = null;

        boolean compact = height < 260;
        boolean veryCompact = height < 200;
        contentMargin = Math.min(18, Math.max(8, width / 32));
        int gridColumns = width < 700 && height >= 260 ? 1 : GRID_COLUMNS;
        int gridRows = (ATTRIBUTES.length + gridColumns - 1) / gridColumns;
        int columnGap = gridColumns == 1 ? 0 : Math.min(18, Math.max(6, width / 40));
        int contentWidth = Math.max(1, width - contentMargin * 2 - columnGap);
        int columnWidth = contentWidth / gridColumns;

        panelLeft = contentMargin - 4;
        panelRight = width - contentMargin + 4;
        viewportLeft = contentMargin;
        viewportRight = width - contentMargin;

        int titleY = veryCompact ? 3 : compact ? 5 : 13;
        int subtitleY = veryCompact ? 15 : compact ? 18 : 30;
        int infoY = veryCompact ? 28 : compact ? 32 : 50;
        headerBottom = veryCompact ? 44 : compact ? 54 : 80;

        fixedTextWidgets.add(addText(contentMargin + 4, titleY,
                width - 2 * contentMargin - 8,
                Component.translatable("screen.rpgcore.title"), 0xFFFFD778, true));
        fixedTextWidgets.add(addText(contentMargin + 4, subtitleY,
                width - 2 * contentMargin - 8,
                Component.translatable("screen.rpgcore.subtitle"), 0xFFD5CBBB, true));

        int infoSplit = Math.min(190, Math.max(120, width / 3));
        levelLabel = addText(contentMargin + 8, infoY, infoSplit - 8,
                Component.translatable("screen.rpgcore.level", "..."), 0xFFF0E9DC, false);
        pointsLabel = addText(contentMargin + infoSplit, infoY,
                Math.max(1, width - 2 * contentMargin - infoSplit),
                Component.translatable("screen.rpgcore.points", "...", "..."),
                0xFFFFD778, false);
        waitingLabel = addText(contentMargin + 8, infoY,
                width - 2 * contentMargin - 16,
                Component.translatable("screen.rpgcore.waiting"), 0xFFFFD778, false);
        fixedTextWidgets.add(levelLabel);
        fixedTextWidgets.add(pointsLabel);
        fixedTextWidgets.add(waitingLabel);

        resetButtonY = height - (compact ? 25 : 29);
        int footerTop = resetButtonY - 8;
        viewportTop = headerBottom + 8;
        viewportBottom = Math.max(viewportTop + 1, footerTop - 7);

        int rowHeight = veryCompact ? 40 : compact ? 56 : 84;
        contentHeight = gridRows * rowHeight + Math.max(0, gridRows - 1) * ROW_GAP;
        maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            PlayerAttribute attribute = ATTRIBUTES[i];
            int row = i / gridColumns;
            int column = i % gridColumns;
            int cellX = contentMargin + column * (columnWidth + columnGap);
            int cellY = viewportTop + row * (rowHeight + ROW_GAP);

            cardXs[i] = cellX;
            cardBaseYs[i] = cellY;
            cardWidths[i] = columnWidth;
            cardHeights[i] = rowHeight;

            int buttonWidth = Math.max(24, Math.min(36, (columnWidth - 28) / 5));
            int buttonGap = 2;
            int buttonGroupWidth = buttonWidth * 4 + buttonGap * 3;
            int buttonLeft = cellX + columnWidth - 7 - buttonGroupWidth;
            int buttonHeight = Math.max(10, Math.min(16, (rowHeight - 6) / 2));
            int buttonTopOffset = Math.max(0, (rowHeight - (buttonHeight * 2 + 2)) / 2);

            int textLeft = cellX + 7;
            int textWidth = Math.max(1, buttonLeft - textLeft - 8);
            int valueWidth = Math.min(48, Math.max(30, textWidth / 4));
            int nameWidth = Math.max(1, textWidth - valueWidth - 5);
            int firstLineOffset = rowHeight / 2 - 8;

            nameOffsets[i] = firstLineOffset;
            effectOffsets[i] = firstLineOffset + 13;
            nameLabels[i] = addText(textLeft, cellY + nameOffsets[i], nameWidth,
                    Component.translatable("screen.rpgcore.stat." + attribute.serializedName()),
                    0xFFF0E9DC, false);
            valueLabels[i] = addText(textLeft + nameWidth + 5, cellY + firstLineOffset,
                    valueWidth, Component.translatable("screen.rpgcore.stat.points", "0"),
                    0xFFFFD778, false);
            effectLabels[i] = addText(textLeft, cellY + effectOffsets[i], textWidth,
                    Component.translatable("screen.rpgcore.effect." + attribute.serializedName()),
                    0xFFB8B0A2, false);

            int[] adjustmentAmounts = {1, 10, 50, 100, -1, -10, -50, -100};
            for (int buttonIndex = 0; buttonIndex < adjustmentAmounts.length; buttonIndex++) {
                int amount = adjustmentAmounts[buttonIndex];
                int buttonX = buttonLeft + (buttonIndex % 4) * (buttonWidth + buttonGap);
                int buttonY = cellY + buttonTopOffset + (buttonIndex / 4) * (buttonHeight + 2);
                String label = amount > 0 ? "+" + amount : Integer.toString(amount);

                Button button = addRenderableWidget(Button.builder(Component.literal(label),
                        widget -> PacketDistributor.sendToServer(
                                new AllocateAttributePayload(attribute.networkId(), amount)))
                        .bounds(buttonX, buttonY, buttonWidth, buttonHeight).build());
                attributeButtons.add(button);
                buttonBaseYs.add(buttonY);
                buttonHeights.add(buttonHeight);
            }
        }

        int totalFooterWidth = Math.min(260, Math.max(180, width - contentMargin * 2));
        int backWidth = Math.min(100, Math.max(70, totalFooterWidth / 3));
        int resetWidth = Math.max(90, totalFooterWidth - backWidth - 10);
        int footerLeft = (width - (resetWidth + 10 + backWidth)) / 2;
        resetButton = addRenderableWidget(Button.builder(Component.translatable("screen.rpgcore.reset"),
                button -> PacketDistributor.sendToServer(new ResetAttributesPayload()))
                .bounds(footerLeft, resetButtonY, resetWidth, 18).build());
        backButton = addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                button -> onClose())
                .bounds(footerLeft + resetWidth + 10, resetButtonY, backWidth, 18).build());

        updateScrollContentPositions();
        PacketDistributor.sendToServer(new RequestRpgDataPayload());
    }

    private StringWidget addText(int x, int y, int widgetWidth,
                                 Component message, int color, boolean centered) {
        StringWidget widget = new StringWidget(x, y, widgetWidth, 10, message, font).setColor(color);
        if (centered) {
            widget.alignCenter();
        } else {
            widget.alignLeft();
        }
        return addRenderableOnly(widget);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void updateScrollContentPositions() {
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            cardYs[i] = cardBaseYs[i] - scrollOffset;
            nameLabels[i].setY(cardBaseYs[i] + nameOffsets[i] - scrollOffset);
            valueLabels[i].setY(cardBaseYs[i] + nameOffsets[i] - scrollOffset);
            effectLabels[i].setY(cardBaseYs[i] + effectOffsets[i] - scrollOffset);

            setWidgetVisibleInViewport(nameLabels[i], nameLabels[i].getY(), 10);
            setWidgetVisibleInViewport(valueLabels[i], valueLabels[i].getY(), 10);
            setWidgetVisibleInViewport(effectLabels[i], effectLabels[i].getY(), 10);
        }

        for (int i = 0; i < attributeButtons.size(); i++) {
            Button button = attributeButtons.get(i);
            int y = buttonBaseYs.get(i) - scrollOffset;
            button.setY(y);
            int buttonHeight = buttonHeights.get(i);
            button.visible = y + buttonHeight > viewportTop && y < viewportBottom;
        }
    }

    private void setWidgetVisibleInViewport(StringWidget widget, int y, int widgetHeight) {
        widget.visible = y + widgetHeight > viewportTop && y < viewportBottom;
    }

    private void updateAttributeRows(PlayerRpgData data) {
        if (data.equals(lastDisplayedData)) {
            return;
        }
        lastDisplayedData = data;

        double strength = AttributeFormulaCalculator.strengthDamage(data.strength());
        double defense = AttributeFormulaCalculator.defenseReductionPercent(data.defense());
        double health = AttributeFormulaCalculator.healthBonus(data.health());
        double mining = AttributeFormulaCalculator.miningSpeedPercent(data.miningSpeed());
        double movement = AttributeFormulaCalculator.movementSpeedPercent(data.speed());
        double jump = AttributeFormulaCalculator.jumpHeightBlocks(data.jumpHeight());
        double luck = AttributeFormulaCalculator.luckEquipmentDropBonusPercent(data.luck());
        double experience = AttributeFormulaCalculator.experienceLootBonusPercent(data.experienceLoot());
        int waterBreathingTicks =
                AttributeFormulaCalculator.waterBreathingBonusAirTicks(data.waterBreathing());

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            PlayerAttribute attribute = ATTRIBUTES[i];
            valueLabels[i].setMessage(Component.translatable(
                    "screen.rpgcore.stat.points", Long.toString(data.valueOf(attribute))));

            String value = switch (attribute) {
                case STRENGTH -> fmt(strength);
                case DEFENSE -> fmt(defense);
                case HEALTH -> fmt(health);
                case MINING_SPEED -> fmt(mining);
                case SPEED -> fmt(movement);
                case JUMP_HEIGHT -> fmt(jump);
                case LUCK -> fmt(luck);
                case EXPERIENCE_LOOT -> fmt(experience);
                case WATER_BREATHING -> Integer.toString(waterBreathingTicks);
            };

            if (attribute == PlayerAttribute.WATER_BREATHING) {
                effectLabels[i].setMessage(Component.translatable(
                        "screen.rpgcore.effect.water_breathing",
                        value, fmt(waterBreathingTicks / 30.0)));
            } else {
                effectLabels[i].setMessage(Component.translatable(
                        "screen.rpgcore.effect." + attribute.serializedName(), value));
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (minecraft == null || minecraft.screen != this
                || maxScroll <= 0
                || mouseX < panelLeft || mouseX >= panelRight
                || mouseY < 7 || mouseY >= height - 7
                || Math.abs(scrollY) < 0.01) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        int previousOffset = scrollOffset;
        int delta = (int) Math.round(scrollY * SCROLL_PIXELS_PER_NOTCH);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta));
        if (scrollOffset != previousOffset) {
            updateScrollContentPositions();
        }
        // Consume wheel input inside the open panel even at either scroll limit.
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean insideViewport = mouseX >= viewportLeft && mouseX < viewportRight
                && mouseY >= viewportTop && mouseY < viewportBottom;
        if (insideViewport) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Widgets clipped out of the viewport must not react to clicks in the
        // fixed header/footer or in the area outside the panel.
        boolean[] previousVisibility = new boolean[attributeButtons.size()];
        for (int i = 0; i < attributeButtons.size(); i++) {
            Button widget = attributeButtons.get(i);
            previousVisibility[i] = widget.visible;
            widget.visible = false;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        for (int i = 0; i < attributeButtons.size(); i++) {
            attributeButtons.get(i).visible = previousVisibility[i];
        }
        return handled;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF0D0E10);
        graphics.fill(0, 0, width, 3, 0xFFE0B45C);
        graphics.fill(panelLeft, 7, panelRight, height - 7, 0xFF171819);
        graphics.fill(panelLeft, 7, panelRight, 8, 0xFF5A4931);
        graphics.fill(contentMargin, headerBottom - 1, width - contentMargin,
                headerBottom + 1, 0xFF5A4931);
        graphics.fill(contentMargin, resetButtonY - 7, width - contentMargin,
                resetButtonY - 6, 0xFF39332A);

        boolean hasData = ClientRpgDataCache.hasServerData();
        waitingLabel.visible = !hasData;
        levelLabel.visible = hasData;
        pointsLabel.visible = hasData;
        if (hasData) {
            PlayerRpgData data = ClientRpgDataCache.get();
            levelLabel.setMessage(Component.translatable("screen.rpgcore.level", data.rpgLevel()));
            pointsLabel.setMessage(Component.translatable("screen.rpgcore.points",
                    data.availableAttributePoints(), data.totalAttributePoints()));
            updateAttributeRows(data);
        }

        boolean[] fixedVisibility = new boolean[fixedTextWidgets.size()];
        for (int i = 0; i < fixedTextWidgets.size(); i++) {
            fixedVisibility[i] = fixedTextWidgets.get(i).visible;
            fixedTextWidgets.get(i).visible = false;
        }
        boolean resetVisible = resetButton.visible;
        boolean backVisible = backButton.visible;
        resetButton.visible = false;
        backButton.visible = false;

        graphics.enableScissor(viewportLeft, viewportTop, viewportRight, viewportBottom);
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            int x = cardXs[i];
            int y = cardYs[i];
            int cardWidth = cardWidths[i];
            int cardHeight = cardHeights[i];
            graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xFF292723);
            graphics.fill(x + 1, y + 2, x + cardWidth - 1, y + cardHeight - 1, 0xFF1D1C1A);
            graphics.fill(x + 2, y + 2, x + cardWidth - 2, y + 3, 0xFF625037);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        for (int i = 0; i < fixedTextWidgets.size(); i++) {
            StringWidget widget = fixedTextWidgets.get(i);
            widget.visible = fixedVisibility[i];
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        resetButton.visible = resetVisible;
        backButton.visible = backVisible;
        if (resetVisible) {
            resetButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (backVisible) {
            backButton.render(graphics, mouseX, mouseY, partialTick);
        }

        if (maxScroll > 0) {
            int trackX = panelRight - 3;
            int trackHeight = viewportBottom - viewportTop;
            int thumbHeight = Math.max(24, trackHeight * trackHeight / contentHeight);
            int thumbTravel = Math.max(0, trackHeight - thumbHeight);
            int thumbY = viewportTop
                    + (int) Math.round(thumbTravel * (double) scrollOffset / maxScroll);
            graphics.fill(trackX, viewportTop, trackX + 2, viewportBottom, 0xFF34312B);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFD2A75D);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}