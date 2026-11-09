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

import java.util.Locale;

/** Full-window player level, attribute allocation, and attribute effects screen. */
public final class RpgDataScreen extends Screen {
    private static final PlayerAttribute[] ATTRIBUTES = PlayerAttribute.values();
    private static final int GRID_COLUMNS = 2;
    private static final int GRID_ROWS = 4;

    private final Screen parentScreen;
    private final StringWidget[] valueLabels = new StringWidget[ATTRIBUTES.length];
    private final StringWidget[] effectLabels = new StringWidget[ATTRIBUTES.length];
    private final int[] cardXs = new int[ATTRIBUTES.length];
    private final int[] cardYs = new int[ATTRIBUTES.length];
    private final int[] cardWidths = new int[ATTRIBUTES.length];
    private final int[] cardHeights = new int[ATTRIBUTES.length];

    private StringWidget levelLabel;
    private StringWidget pointsLabel;
    private StringWidget waitingLabel;
    private PlayerRpgData lastDisplayedData;
    private int contentMargin;
    private int headerBottom;
    private int resetButtonY;

    public RpgDataScreen() {
        this(null);
    }

    public RpgDataScreen(Screen parentScreen) {
        super(Component.translatable("screen.rpgcore.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        boolean compact = height < 260;
        boolean veryCompact = height < 200;
        contentMargin = Math.min(18, Math.max(8, width / 32));
        int columnGap = Math.min(18, Math.max(6, width / 40));
        int contentWidth = Math.max(1, width - contentMargin * 2 - columnGap);
        int columnWidth = contentWidth / GRID_COLUMNS;

        int titleY = veryCompact ? 3 : compact ? 5 : 13;
        int subtitleY = veryCompact ? 15 : compact ? 18 : 30;
        int infoY = veryCompact ? 28 : compact ? 32 : 50;
        int waitingY = infoY;
        headerBottom = veryCompact ? 44 : compact ? 54 : 80;

        addText(contentMargin + 4, titleY, width - 2 * contentMargin - 8,
                Component.translatable("screen.rpgcore.title"), 0xFFFFD778, true);
        addText(contentMargin + 4, subtitleY, width - 2 * contentMargin - 8,
                Component.translatable("screen.rpgcore.subtitle"), 0xFFD5CBBB, true);

        int infoSplit = Math.min(190, Math.max(120, width / 3));
        levelLabel = addText(contentMargin + 8, infoY, infoSplit - 8,
                Component.translatable("screen.rpgcore.level", "..."), 0xFFF0E9DC, false);
        pointsLabel = addText(contentMargin + infoSplit, infoY,
                Math.max(1, width - 2 * contentMargin - infoSplit),
                Component.translatable("screen.rpgcore.points", "...", "..."),
                0xFFFFD778, false);
        waitingLabel = addText(contentMargin + 8, waitingY,
                width - 2 * contentMargin - 16,
                Component.translatable("screen.rpgcore.waiting"), 0xFFFFD778, false);

        resetButtonY = height - (compact ? 25 : 29);
        int footerTop = resetButtonY - 8;
        int availableGridHeight = Math.max(4, footerTop - headerBottom - 8);
        int rowHeight = Math.max(20, Math.min(compact ? 48 : 84, availableGridHeight / GRID_ROWS));
        int gridTop = headerBottom
                + Math.max(0, (availableGridHeight - rowHeight * GRID_ROWS) / 2);

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            PlayerAttribute attribute = ATTRIBUTES[i];
            int row = i / GRID_COLUMNS;
            int column = i % GRID_COLUMNS;
            int cellX = contentMargin + column * (columnWidth + columnGap);
            int cellY = gridTop + row * rowHeight;

            cardXs[i] = cellX;
            cardYs[i] = cellY;
            cardWidths[i] = columnWidth;
            cardHeights[i] = rowHeight;

            int buttonWidth = Math.max(22, Math.min(30, (columnWidth - 20) / 7));
            int buttonGap = 3;
            int buttonGroupWidth = buttonWidth * 2 + buttonGap;
            int buttonLeft = cellX + columnWidth - 7 - buttonGroupWidth;
            int buttonHeight = Math.max(10, Math.min(14, (rowHeight - 5) / 2));
            int buttonTop = cellY + (rowHeight - (buttonHeight * 2 + 2)) / 2;

            int textLeft = cellX + 7;
            int textWidth = Math.max(64, buttonLeft - textLeft - 8);
            int valueWidth = Math.min(48, Math.max(34, textWidth / 4));
            int nameWidth = Math.max(34, textWidth - valueWidth - 5);
            int firstLineY = cellY + rowHeight / 2 - 8;
            int effectY = firstLineY + 13;

            addText(textLeft, firstLineY, nameWidth,
                    Component.translatable("screen.rpgcore.stat." + attribute.serializedName()),
                    0xFFF0E9DC, false);
            valueLabels[i] = addText(textLeft + nameWidth + 5, firstLineY, valueWidth,
                    Component.translatable("screen.rpgcore.stat.points", "0"),
                    0xFFFFD778, false);
            effectLabels[i] = addText(textLeft, effectY, textWidth,
                    Component.translatable("screen.rpgcore.effect." + attribute.serializedName()),
                    0xFFB8B0A2, false);

            int leftButtonX = buttonLeft;
            int rightButtonX = buttonLeft + buttonWidth + buttonGap;
            int lowerButtonY = buttonTop + buttonHeight + 2;

            addRenderableWidget(Button.builder(Component.literal("+1"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 1)))
                    .bounds(leftButtonX, buttonTop, buttonWidth, buttonHeight).build());

            addRenderableWidget(Button.builder(Component.literal("+10"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 10)))
                    .bounds(rightButtonX, buttonTop, buttonWidth, buttonHeight).build());

            addRenderableWidget(Button.builder(Component.literal("+50"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 50)))
                    .bounds(leftButtonX, lowerButtonY, buttonWidth, buttonHeight).build());

            addRenderableWidget(Button.builder(Component.literal("+100"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 100)))
                    .bounds(rightButtonX, lowerButtonY, buttonWidth, buttonHeight).build());
        }

        int totalFooterWidth = 260;
        int footerLeft = (width - totalFooterWidth) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen.rpgcore.reset"),
                button -> PacketDistributor.sendToServer(new ResetAttributesPayload()))
                .bounds(footerLeft, resetButtonY, 150, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                button -> onClose())
                .bounds(footerLeft + 160, resetButtonY, 100, 18).build());

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
            };
            effectLabels[i].setMessage(Component.translatable(
                    "screen.rpgcore.effect." + attribute.serializedName(), value));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF0D0E10);
        graphics.fill(0, 0, width, 3, 0xFFE0B45C);

        graphics.fill(contentMargin - 4, 7, width - contentMargin + 4,
                height - 7, 0xFF171819);
        graphics.fill(contentMargin - 4, 7, width - contentMargin + 4,
                8, 0xFF5A4931);
        graphics.fill(contentMargin, headerBottom - 1, width - contentMargin,
                headerBottom + 1, 0xFF5A4931);
        graphics.fill(contentMargin, resetButtonY - 7, width - contentMargin,
                resetButtonY - 6, 0xFF39332A);

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            int x = cardXs[i];
            int y = cardYs[i];
            int cardWidth = cardWidths[i];
            int cardHeight = cardHeights[i];
            graphics.fill(x, y, x + cardWidth, y + cardHeight, 0xFF292723);
            graphics.fill(x + 1, y + 2, x + cardWidth - 1, y + cardHeight - 1, 0xFF1D1C1A);
            graphics.fill(x + 2, y + 2, x + cardWidth - 2, y + 3, 0xFF625037);
        }

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

        super.render(graphics, mouseX, mouseY, partialTick);
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
