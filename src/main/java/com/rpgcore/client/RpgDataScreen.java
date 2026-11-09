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
    private static final String[] DISPLAY_NAMES = {
            "Strength", "Agility", "Constitution", "Intelligence", "Spirit"
    };

    private final StringWidget[] valueLabels = new StringWidget[ATTRIBUTES.length];
    private StringWidget levelLabel;
    private StringWidget xpLabel;
    private StringWidget pointsLabel;
    private StringWidget waitingLabel;
    private StringWidget diagnosticLabel;

    public RpgDataScreen() {
        super(Component.literal("RPG Core"));
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        addText(left + 8, top + 12, PANEL_WIDTH - 16, "RPG CORE", 0xFFFFD778, true);
        addText(left + 12, top + 32, PANEL_WIDTH - 24,
                "Independent level and freely assigned attributes", 0xFFD5CBBB, true);

        levelLabel = addText(left + 22, top + 55, 340, "RPG Level: ...", 0xFFF0E9DC, false);
        xpLabel = addText(left + 22, top + 70, 340, "RPG Experience: ...", 0xFFF0E9DC, false);
        pointsLabel = addText(left + 22, top + 85, 340, "Available Points: ...", 0xFFFFD778, false);
        waitingLabel = addText(left + 22, top + 103, 340, "Waiting for server data...", 0xFFFFD778, false);

        for (int i = 0; i < ATTRIBUTES.length; i++) {
            final PlayerAttribute attribute = ATTRIBUTES[i];
            int rowY = top + 126 + i * 24;
            addText(left + 22, rowY + 5, 145, DISPLAY_NAMES[i], 0xFFF0E9DC, false);
            valueLabels[i] = addText(left + 172, rowY + 5, 54, "0", 0xFFFFD778, false);

            addRenderableWidget(Button.builder(Component.literal("+1"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 1)))
                    .bounds(left + 240, rowY, 38, 20).build());

            addRenderableWidget(Button.builder(Component.literal("+10"),
                    button -> PacketDistributor.sendToServer(
                            new AllocateAttributePayload(attribute.networkId(), 10)))
                    .bounds(left + 282, rowY, 44, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Reset Attributes (Free)"),
                button -> PacketDistributor.sendToServer(new ResetAttributesPayload()))
                .bounds(left + 22, top + 258, 170, 20).build());

        diagnosticLabel = addText(left + 205, top + 263, 170, "Test Counter: 0", 0xFF9AF0B2, false);
        PacketDistributor.sendToServer(new RequestRpgDataPayload());
    }

    private StringWidget addText(int x, int y, int width, String text, int color, boolean centered) {
        StringWidget widget = new StringWidget(x, y, width, 10, Component.literal(text), font).setColor(color);
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
            levelLabel.setMessage(Component.literal("RPG Level: " + data.rpgLevel()));
            xpLabel.setMessage(Component.literal("RPG XP Progress: " + data.experienceProgress()));
            pointsLabel.setMessage(Component.literal("Available Points: "
                    + data.availableAttributePoints() + "   Total Earned: " + data.totalAttributePoints()));

            long[] values = {data.strength(), data.agility(), data.constitution(),
                    data.intelligence(), data.spirit()};
            for (int i = 0; i < values.length; i++) {
                valueLabels[i].setMessage(Component.literal(Long.toString(values[i])));
            }
            diagnosticLabel.setMessage(Component.literal("Test Counter: " + data.diagnosticCounter()));
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
