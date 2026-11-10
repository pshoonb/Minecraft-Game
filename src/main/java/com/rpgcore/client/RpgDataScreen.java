package com.rpgcore.client;

import com.rpgcore.attributes.AttributeFormulaCalculator;
import com.rpgcore.data.PlayerAttribute;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.network.AllocateAttributePayload;
import com.rpgcore.network.RequestRpgDataPayload;
import com.rpgcore.network.ResetAttributesPayload;
import com.rpgcore.network.UnlockTeleportPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
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
    private final MultiLineTextWidget[] nameLabels = new MultiLineTextWidget[ATTRIBUTES.length];
    private final StringWidget[] valueLabels = new StringWidget[ATTRIBUTES.length];
    private final MultiLineTextWidget[] effectLabels = new MultiLineTextWidget[ATTRIBUTES.length];
    private final int[] cardXs = new int[ATTRIBUTES.length];
    private final int[] cardBaseYs = new int[ATTRIBUTES.length];
    private final int[] cardYs = new int[ATTRIBUTES.length];
    private final int[] cardWidths = new int[ATTRIBUTES.length];
    private final int[] cardHeights = new int[ATTRIBUTES.length];
    private final int[] nameOffsets = new int[ATTRIBUTES.length];
    private final int[] valueOffsets = new int[ATTRIBUTES.length];
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
    private Button teleportUnlockButton;
    private PlayerRpgData lastDisplayedData;

    private int contentMargin;
    private int gridColumns;
    private int gridRows;
    private int columnGap;
    private int columnWidth;
    private boolean compactCards;
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
        // Screen.width/height are already GUI-scaled by Minecraft. Choose the
        // layout from those values so every player's GUI Scale is respected.
        gridColumns = width < 700 ? 1 : GRID_COLUMNS;
        gridRows = (ATTRIBUTES.length + gridColumns - 1) / gridColumns;
        columnGap = gridColumns == 1 ? 0 : Math.min(18, Math.max(6, width / 40));
        int contentWidth = Math.max(1, width - contentMargin * 2 - columnGap);
        columnWidth = contentWidth / gridColumns;
        compactCards = columnWidth < 350;

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

        boolean narrowHeader = width < 560;
        if (narrowHeader) headerBottom += 13;
        int infoSplit = narrowHeader ? width - 2 * contentMargin
                : Math.min(360, Math.max(240, width / 2));
        int levelWidth = narrowHeader ? 108 : 125;
        levelLabel = addText(contentMargin + 8, infoY, levelWidth,
                Component.translatable("screen.rpgcore.level", "..."), 0xFFF0E9DC, false);

        int unlockButtonX = contentMargin + 8 + levelWidth + 5;
        int unlockButtonWidth = narrowHeader ? 112 : Math.max(110, infoSplit - levelWidth - 18);
        teleportUnlockButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.rpgcore.teleport.locked"),
                button -> PacketDistributor.sendToServer(new UnlockTeleportPayload()))
                .bounds(unlockButtonX, infoY - 4, unlockButtonWidth, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.rpgcore.teleport.require_level")))
                .build());

        pointsLabel = addText(narrowHeader ? contentMargin + 8 : contentMargin + infoSplit,
                narrowHeader ? infoY + 13 : infoY,
                Math.max(1, width - 2 * contentMargin - (narrowHeader ? 16 : infoSplit)),
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

        int buttonHeight = height < 200 ? 12 : 16;
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            PlayerAttribute attribute = ATTRIBUTES[i];
            int column = i % gridColumns;
            int cellX = contentMargin + column * (columnWidth + columnGap);

            cardXs[i] = cellX;
            cardWidths[i] = columnWidth;

            int buttonWidth = Math.max(24, Math.min(36, (columnWidth - 28) / 5));
            int buttonGap = 2;
            int buttonGroupWidth = buttonWidth * 4 + buttonGap * 3;
            int buttonLeft = compactCards
                    ? cellX + (columnWidth - buttonGroupWidth) / 2
                    : cellX + columnWidth - 7 - buttonGroupWidth;

            int textLeft = cellX + 7;
            int valueWidth = Math.min(52, Math.max(36, columnWidth / 7));
            int nameWidth = compactCards
                    ? Math.max(1, columnWidth - 14)
                    : Math.max(1, buttonLeft - textLeft - 8 - valueWidth - 5);
            int valueX = compactCards ? textLeft : textLeft + nameWidth + 5;

            nameLabels[i] = addWrappedText(textLeft, 0, nameWidth,
                    Component.translatable("screen.rpgcore.stat." + attribute.serializedName()),
                    0xFFF0E9DC);
            valueLabels[i] = addText(valueX, 0, valueWidth,
                    Component.translatable("screen.rpgcore.stat.points", "0"),
                    0xFFFFD778, false);

            Component effectPlaceholder = attribute == PlayerAttribute.WATER_BREATHING
                    ? Component.translatable("screen.rpgcore.effect.water_breathing", "0", "0")
                    : Component.translatable("screen.rpgcore.effect." + attribute.serializedName(), "0");
            effectLabels[i] = addWrappedText(cellX + 7, 0,
                    Math.max(1, columnWidth - 14), effectPlaceholder, 0xFFB8B0A2);

            int[] adjustmentAmounts = {1, 10, 50, 100, -1, -10, -50, -100};
            for (int buttonIndex = 0; buttonIndex < adjustmentAmounts.length; buttonIndex++) {
                int amount = adjustmentAmounts[buttonIndex];
                int buttonX = buttonLeft + (buttonIndex % 4) * (buttonWidth + buttonGap);
                int buttonYOffset = (buttonIndex / 4) * (buttonHeight + 2);
                String label = amount > 0 ? "+" + amount : Integer.toString(amount);

                Button button = addRenderableWidget(Button.builder(Component.literal(label),
                        widget -> PacketDistributor.sendToServer(
                                new AllocateAttributePayload(attribute.networkId(), amount)))
                        .bounds(buttonX, 0, buttonWidth, buttonHeight).build());
                attributeButtons.add(button);
                buttonBaseYs.add(buttonYOffset);
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

        recalculateCardLayout();
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

    private MultiLineTextWidget addWrappedText(int x, int y, int widgetWidth,
                                               Component message, int color) {
        MultiLineTextWidget widget = new MultiLineTextWidget(x, y, message, font)
                .setMaxWidth(Math.max(1, widgetWidth))
                .setColor(color);
        return addRenderableOnly(widget);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Measures wrapped text and rebuilds card rows using the scaled GUI width.
     * Each row is as tall as its tallest card, so wrapped descriptions never
     * overlap another attribute or the allocation controls.
     */
    private void recalculateCardLayout() {
        int y = viewportTop;
        for (int row = 0; row < gridRows; row++) {
            int first = row * gridColumns;
            int last = Math.min(ATTRIBUTES.length, first + gridColumns);
            int rowHeight = 0;

            for (int i = first; i < last; i++) {
                int titleHeight = Math.max(10, nameLabels[i].getHeight());
                int buttonHeight = buttonHeights.get(i * 8);
                nameOffsets[i] = 5;

                int buttonTop;
                if (compactCards) {
                    valueOffsets[i] = nameOffsets[i] + titleHeight + 2;
                    buttonTop = valueOffsets[i] + 12 + 4;
                    effectOffsets[i] = buttonTop + buttonHeight * 2 + 2 + 5;
                } else {
                    valueOffsets[i] = nameOffsets[i];
                    buttonTop = 4;
                    effectOffsets[i] = Math.max(40, nameOffsets[i] + titleHeight + 5);
                }

                for (int buttonIndex = 0; buttonIndex < 8; buttonIndex++) {
                    int buttonOffset = buttonTop + (buttonIndex / 4) * (buttonHeight + 2);
                    buttonBaseYs.set(i * 8 + buttonIndex, buttonOffset);
                }

                int effectHeight = Math.max(10, effectLabels[i].getHeight());
                rowHeight = Math.max(rowHeight, effectOffsets[i] + effectHeight + 8);
            }

            for (int i = first; i < last; i++) {
                cardBaseYs[i] = y;
                cardHeights[i] = rowHeight;
            }
            y += rowHeight + ROW_GAP;
        }

        contentHeight = Math.max(0, y - viewportTop - (gridRows > 0 ? ROW_GAP : 0));
        maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        updateScrollContentPositions();
    }

    private void updateScrollContentPositions() {
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            cardYs[i] = cardBaseYs[i] - scrollOffset;

            int nameY = cardBaseYs[i] + nameOffsets[i] - scrollOffset;
            int valueY = cardBaseYs[i] + valueOffsets[i] - scrollOffset;
            int effectY = cardBaseYs[i] + effectOffsets[i] - scrollOffset;
            nameLabels[i].setY(nameY);
            valueLabels[i].setY(valueY);
            effectLabels[i].setY(effectY);

            nameLabels[i].visible = nameY + nameLabels[i].getHeight() > viewportTop
                    && nameY < viewportBottom;
            valueLabels[i].visible = valueY + 10 > viewportTop && valueY < viewportBottom;
            effectLabels[i].visible = effectY + effectLabels[i].getHeight() > viewportTop
                    && effectY < viewportBottom;
        }

        for (int i = 0; i < attributeButtons.size(); i++) {
            Button button = attributeButtons.get(i);
            int attributeIndex = i / 8;
            int y = cardBaseYs[attributeIndex] + buttonBaseYs.get(i) - scrollOffset;
            button.setY(y);
            int buttonHeight = buttonHeights.get(i);
            button.visible = y + buttonHeight > viewportTop && y < viewportBottom;
        }
    }

    private void updateTeleportUnlockButton(PlayerRpgData data) {
        if (data.teleportUnlocked()) {
            teleportUnlockButton.setMessage(Component.translatable("screen.rpgcore.teleport.unlocked"));
            teleportUnlockButton.active = false;
            teleportUnlockButton.setTooltip(Tooltip.create(
                    Component.translatable("screen.rpgcore.teleport.unlocked_tooltip")));
        } else if (data.highestLevelReached() < 30) {
            teleportUnlockButton.setMessage(Component.translatable("screen.rpgcore.teleport.locked"));
            teleportUnlockButton.active = false;
            teleportUnlockButton.setTooltip(Tooltip.create(
                    Component.translatable("screen.rpgcore.teleport.require_level")));
        } else {
            // Level 30 is the unlock eligibility gate. The server reports insufficient
            // points after a click, so the button itself is never disabled after level 30.
            teleportUnlockButton.setMessage(Component.translatable("screen.rpgcore.teleport.unlock"));
            teleportUnlockButton.active = true;
            teleportUnlockButton.setTooltip(Tooltip.create(Component.translatable(
                    "screen.rpgcore.teleport.unlock_cost", data.availableAttributePoints())));
        }
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
        teleportUnlockButton.visible = hasData;
        if (hasData) {
            PlayerRpgData data = ClientRpgDataCache.get();
            updateTeleportUnlockButton(data);
            levelLabel.setMessage(Component.translatable("screen.rpgcore.level", data.rpgLevel()));
            pointsLabel.setMessage(Component.translatable("screen.rpgcore.points",
                    data.availableAttributePoints(), data.totalAttributePoints()));
            updateAttributeRows(data);
        }
        recalculateCardLayout();

        boolean[] fixedVisibility = new boolean[fixedTextWidgets.size()];
        for (int i = 0; i < fixedTextWidgets.size(); i++) {
            fixedVisibility[i] = fixedTextWidgets.get(i).visible;
            fixedTextWidgets.get(i).visible = false;
        }
        boolean resetVisible = resetButton.visible;
        boolean backVisible = backButton.visible;
        boolean unlockVisible = teleportUnlockButton.visible;
        resetButton.visible = false;
        backButton.visible = false;
        teleportUnlockButton.visible = false;

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
        teleportUnlockButton.visible = unlockVisible;
        if (resetVisible) {
            resetButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (backVisible) {
            backButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (unlockVisible) teleportUnlockButton.render(graphics, mouseX, mouseY, partialTick);

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