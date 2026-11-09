package com.rpgcore.client;

import com.rpgcore.data.PlayerAttribute;
import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.experience.ExperienceLootCalculator;
import com.rpgcore.network.AllocateAttributePayload;
import com.rpgcore.network.RequestRpgDataPayload;
import com.rpgcore.network.ResetAttributesPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigInteger;

/** Player level, allocated points, and the intended/current effect of each attribute. */
public final class RpgDataScreen extends Screen {
    private static final int PANEL_WIDTH = 410;
    private static final int PANEL_HEIGHT = 236;
    private static final int COLUMN_WIDTH = 198;
    private static final int ROW_HEIGHT = 34;
    private static final PlayerAttribute[] ATTRIBUTES = PlayerAttribute.values();

    private final Screen parentScreen;
    private final StringWidget[] valueLabels = new StringWidget[ATTRIBUTES.length];
    private final StringWidget[] effectLabels = new StringWidget[ATTRIBUTES.length];
    private StringWidget levelLabel;
    private StringWidget pointsLabel;
    private StringWidget waitingLabel;

    public RpgDataScreen() { this(null); }

    public RpgDataScreen(Screen parentScreen) {
        super(Component.translatable("screen.rpgcore.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        addText(left + 8, top + 7, PANEL_WIDTH - 16,
                Component.translatable("screen.rpgcore.title"), 0xFFFFD778, true);
        addText(left + 8, top + 21, PANEL_WIDTH - 16,
                Component.translatable("screen.rpgcore.subtitle"), 0xFFD5CBBB, true);

        levelLabel = addText(left + 12, top + 37, 180,
                Component.translatable("screen.rpgcore.level", "..."), 0xFFF0E9DC, false);
        pointsLabel = addText(left + 12, top + 50, PANEL_WIDTH - 24,
                Component.translatable("screen.rpgcore.points", "...", "..."), 0xFFFFD778, false);
        waitingLabel = addText(left + 12, top + 65, PANEL_WIDTH - 24,
                Component.translatable("screen.rpgcore.waiting"), 0xFFFFD778, false);

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            final PlayerAttribute attribute = ATTRIBUTES[i];
            int row = i / 2;
            int column = i % 2;
            int cellX = left + 7 + column * COLUMN_WIDTH;
            int rowY = top + 79 + row * ROW_HEIGHT;

            addText(cellX, rowY + 2, 76,
                    Component.translatable("screen.rpgcore.stat." + attribute.serializedName()),
                    0xFFF0E9DC, false);
            valueLabels[i] = addText(cellX + 76, rowY + 2, 49,
                    Component.translatable("screen.rpgcore.stat.points", "0"),
                    0xFFFFD778, false);
            effectLabels[i] = addText(cellX, rowY + 17, COLUMN_WIDTH - 8,
                    Component.translatable("screen.rpgcore.effect." + attribute.serializedName()),
                    0xFFB8B0A2, false);

            addRenderableWidget(Button.builder(Component.literal("+1"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 1)))
                    .bounds(cellX + 126, rowY, 29, 16).build());

            addRenderableWidget(Button.builder(Component.literal("+10"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 10)))
                    .bounds(cellX + 157, rowY, 34, 16).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.rpgcore.reset"),
                button -> PacketDistributor.sendToServer(new ResetAttributesPayload()))
                .bounds(left + 12, top + 215, 140, 16).build());

        PacketDistributor.sendToServer(new RequestRpgDataPayload());
    }

    private StringWidget addText(int x, int y, int widgetWidth,
                                 Component message, int color, boolean centered) {
        StringWidget widget = new StringWidget(x, y, widgetWidth, 10, message, font).setColor(color);
        if (centered) widget.alignCenter(); else widget.alignLeft();
        return addRenderableOnly(widget);
    }

    private void updateAttributeRows(PlayerRpgData data) {
        BigInteger experienceLootBonus = BigInteger.valueOf(data.experienceLoot())
                .multiply(BigInteger.valueOf(ExperienceLootCalculator.PERCENT_PER_POINT));

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            PlayerAttribute attribute = ATTRIBUTES[i];
            valueLabels[i].setMessage(Component.translatable(
                    "screen.rpgcore.stat.points", Long.toString(data.valueOf(attribute))));

            if (attribute == PlayerAttribute.EXPERIENCE_LOOT) {
                effectLabels[i].setMessage(Component.translatable(
                        "screen.rpgcore.effect.experience_loot", experienceLootBonus.toString()));
            } else {
                effectLabels[i].setMessage(Component.translatable(
                        "screen.rpgcore.effect." + attribute.serializedName()));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF0D0C0A);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF1B1A18);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 2, 0xFFE0B45C);
        graphics.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, 0xFF3B3022);
        graphics.fill(left + 6, top + 6, left + PANEL_WIDTH - 6, top + PANEL_HEIGHT - 6, 0xFF171615);

        boolean hasData = ClientRpgDataCache.hasServerData();
        waitingLabel.visible = !hasData;
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
        if (minecraft != null) minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
