package com.rpgcore.client;

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

/** Simple first playable attribute allocation screen. */
public final class RpgDataScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 300;
    private static final PlayerAttribute[] ATTRIBUTES = PlayerAttribute.values();

    private final StringWidget[] nameLabels = new StringWidget[ATTRIBUTES.length];
    private final StringWidget[] valueLabels = new StringWidget[ATTRIBUTES.length];
    private StringWidget levelLabel;
    private StringWidget xpLabel;
    private StringWidget pointsLabel;
    private StringWidget waitingLabel;
    private StringWidget diagnosticLabel;

    public RpgDataScreen() {
        super(Component.translatable("screen.rpgcore.title"));
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
        xpLabel = addText(left + 22, top + 70, 340,
                Component.translatable("screen.rpgcore.xp", "..."), 0xFFF0E9DC, false);
        pointsLabel = addText(left + 22, top + 85, 350,
                Component.translatable("screen.rpgcore.points", "...", "..."), 0xFFFFD778, false);
        waitingLabel = addText(left + 22, top + 103, 340,
                Component.translatable("screen.rpgcore.waiting"), 0xFFFFD778, false);

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            final PlayerAttribute attribute = ATTRIBUTES[i];
            int rowY = top + 126 + i * 24;
            nameLabels[i] = addText(left + 22, rowY + 5, 145,
                    Component.translatable("screen.rpgcore.stat." + attribute.serializedName()),
                    0xFFF0E9DC, false);
            valueLabels[i] = addText(left + 172, rowY + 5, 54,
                    Component.literal("0"), 0xFFFFD778, false);

            addRenderableWidget(Button.builder(Component.literal("+1"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 1)))
                    .bounds(left + 240, rowY, 38, 20).build());

            addRenderableWidget(Button.builder(Component.literal("+10"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 10)))
                    .bounds(left + 282, rowY, 44, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.rpgcore.reset"),
                button -> PacketDistributor.sendToServer(new ResetAttributesPayload()))
                .bounds(left + 22, top + 258, 170, 20).build());

        diagnosticLabel = addText(left + 205, top + 263, 170,
                Component.translatable("screen.rpgcore.counter", 0), 0xFF9AF0B2, false);
        PacketDistributor.sendToServer(new RequestRpgDataPayload());
    }

    private StringWidget addText(int x, int y, int widgetWidth,
                                 Component message, int color, boolean centered) {
        StringWidget widget = new StringWidget(x, y, widgetWidth, 10, message, font).setColor(color);
        if (centered) widget.alignCenter(); else widget.alignLeft();
        return addRenderableOnly(widget);
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
            xpLabel.setMessage(Component.translatable("screen.rpgcore.xp", data.experienceProgress()));
            pointsLabel.setMessage(Component.translatable("screen.rpgcore.points",
                    data.availableAttributePoints(), data.totalAttributePoints()));

            long[] values = {data.strength(), data.agility(), data.constitution(),
                    data.intelligence(), data.spirit()};
            for (int i = 0; i < values.length; i++) {
                valueLabels[i].setMessage(Component.literal(Long.toString(values[i])));
            }
            diagnosticLabel.setMessage(Component.translatable(
                    "screen.rpgcore.counter", data.diagnosticCounter()));
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
