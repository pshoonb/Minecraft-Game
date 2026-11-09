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

/** Player level, allocated points, and currently active attribute effects. */
public final class RpgDataScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int PANEL_HEIGHT = 386;
    private static final PlayerAttribute[] ATTRIBUTES = PlayerAttribute.values();

    private final Screen parentScreen;
    private final StringWidget[] valueLabels = new StringWidget[ATTRIBUTES.length];
    private final StringWidget[] effectLabels = new StringWidget[ATTRIBUTES.length];
    private StringWidget levelLabel;
    private StringWidget pointsLabel;
    private StringWidget waitingLabel;

    public RpgDataScreen() {
        this(null);
    }

    public RpgDataScreen(Screen parentScreen) {
        super(Component.translatable("screen.rpgcore.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        addText(left + 8, top + 12, PANEL_WIDTH - 16,
                Component.translatable("screen.rpgcore.title"), 0xFFFFD778, true);
        addText(left + 12, top + 32, PANEL_WIDTH - 24,
                Component.translatable("screen.rpgcore.subtitle"), 0xFFD5CBBB, true);

        levelLabel = addText(left + 22, top + 55, 340,
                Component.translatable("screen.rpgcore.level", "..."), 0xFFF0E9DC, false);
        pointsLabel = addText(left + 22, top + 75, 440,
                Component.translatable("screen.rpgcore.points", "...", "..."), 0xFFFFD778, false);
        waitingLabel = addText(left + 22, top + 96, 440,
                Component.translatable("screen.rpgcore.waiting"), 0xFFFFD778, false);

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            final PlayerAttribute attribute = ATTRIBUTES[i];
            int rowY = top + 116 + i * 36;

            addText(left + 22, rowY + 3, 120,
                    Component.translatable("screen.rpgcore.stat." + attribute.serializedName()),
                    0xFFF0E9DC, false);
            valueLabels[i] = addText(left + 148, rowY + 3, 105,
                    Component.translatable("screen.rpgcore.stat.points", "0"),
                    0xFFFFD778, false);
            effectLabels[i] = addText(left + 22, rowY + 18, 365,
                    Component.translatable("screen.rpgcore.effect.pending"),
                    0xFFB8B0A2, false);

            addRenderableWidget(Button.builder(Component.literal("+1"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 1)))
                    .bounds(left + 400, rowY + 7, 38, 20).build());

            addRenderableWidget(Button.builder(Component.literal("+10"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 10)))
                    .bounds(left + 442, rowY + 7, 44, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.rpgcore.reset"),
                button -> PacketDistributor.sendToServer(new ResetAttributesPayload()))
                .bounds(left + 22, top + 350, 170, 20).build());

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
                // Use the same balance constant as the server-side XP calculation.
                effectLabels[i].setMessage(Component.translatable(
                        "screen.rpgcore.effect.experience_loot",
                        experienceLootBonus.toString()));
            } else {
                // These five gameplay conversions do not exist yet; do not
                // display speculative values as if they were active bonuses.
                effectLabels[i].setMessage(
                        Component.translatable("screen.rpgcore.effect.pending"));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF0D0C0A);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF1B1A18);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 3, 0xFFE0B45C);
        graphics.fill(left + 5, top + 5, left + PANEL_WIDTH - 5, top + PANEL_HEIGHT - 5, 0xFF3B3022);
        graphics.fill(left + 7, top + 7, left + PANEL_WIDTH - 7, top + PANEL_HEIGHT - 7, 0xFF171615);

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
        if (minecraft != null) {
            minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
