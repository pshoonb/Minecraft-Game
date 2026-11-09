package com.rpgcore.client;

import com.rpgcore.data.PlayerRpgData;
import com.rpgcore.network.IncrementDiagnosticPayload;
import com.rpgcore.network.RequestRpgDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RpgDataScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 238;

    private static final String[] STAT_NAMES = {
            "Health", "Defense", "Attack", "Speed",
            "Jump Height", "Luck", "Mining Speed"
    };

    private final StringWidget[] statNameLabels = new StringWidget[STAT_NAMES.length];
    private final StringWidget[] statValueLabels = new StringWidget[STAT_NAMES.length];

    private StringWidget titleLabel;
    private StringWidget subtitleLabel;
    private StringWidget waitingLabel;
    private StringWidget pointsLabel;
    private StringWidget historyLabel;
    private StringWidget counterLabel;
    private StringWidget uuidLabel;

    public RpgDataScreen() {
        super(Component.translatable("screen.rpgcore.title"));
    }

    @Override
    protected void init() {
        int panelLeft = (this.width - Math.min(PANEL_WIDTH, this.width - 12)) / 2;
        int panelWidth = Math.min(PANEL_WIDTH, this.width - 12);
        int panelTop = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
        int textX = panelLeft + 22;
        int textY = panelTop + 47;
        int textWidth = Math.max(80, panelWidth - 44);

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.rpgcore.increment_test"),
                button -> PacketDistributor.sendToServer(new IncrementDiagnosticPayload())
        ).bounds(this.width / 2 - 88, panelTop + 205, 176, 20).build());

        // Render every text line as a vanilla StringWidget, just like the working
        // button uses vanilla widget rendering, instead of drawing text directly.
        this.titleLabel = addTextLabel(
                panelLeft + 7, panelTop + 14, panelWidth - 14, 10,
                this.title, 0xFFFFD778, true
        );
        this.subtitleLabel = addTextLabel(
                panelLeft + 7, panelTop + 28, panelWidth - 14, 10,
                Component.translatable("screen.rpgcore.subtitle"), 0xFFD5CBBB, true
        );

        this.waitingLabel = addTextLabel(
                textX, textY, textWidth, 10,
                Component.translatable("screen.rpgcore.waiting"), 0xFFFFD778, false
        );

        for (int i = 0; i < STAT_NAMES.length; i++) {
            this.statNameLabels[i] = addTextLabel(
                    textX, textY + i * 15, 160, 10,
                    Component.literal(STAT_NAMES[i]), 0xFFF0E9DC, false
            );
            this.statValueLabels[i] = addTextLabel(
                    textX + 190, textY + i * 15, 75, 10,
                    Component.literal("0"), 0xFFFFD778, false
            );
            this.statNameLabels[i].visible = false;
            this.statValueLabels[i].visible = false;
        }

        this.pointsLabel = addTextLabel(
                textX, textY + 111, textWidth, 10,
                Component.empty(), 0xFFF0E9DC, false
        );
        this.historyLabel = addTextLabel(
                textX, textY + 126, textWidth, 10,
                Component.empty(), 0xFFF0E9DC, false
        );
        this.counterLabel = addTextLabel(
                textX, textY + 148, textWidth, 10,
                Component.empty(), 0xFF9AF0B2, false
        );
        this.uuidLabel = addTextLabel(
                textX, panelTop + 190, textWidth, 10,
                Component.empty(), 0xFFBEB7AB, false
        );

        this.pointsLabel.visible = false;
        this.historyLabel.visible = false;
        this.counterLabel.visible = false;
        this.uuidLabel.visible = false;

        PacketDistributor.sendToServer(new RequestRpgDataPayload());
    }

    private StringWidget addTextLabel(int x, int y, int width, int height,
                                      Component message, int color, boolean centered) {
        StringWidget label = new StringWidget(x, y, width, height, message, this.font)
                .setColor(color);
        if (centered) {
            label.alignCenter();
        } else {
            label.alignLeft();
        }
        return this.addRenderableOnly(label);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the diagnostic panel fully opaque and render it before all widgets.
        graphics.fill(0, 0, this.width, this.height, 0xFF0D0C0A);

        int panelLeft = (this.width - Math.min(PANEL_WIDTH, this.width - 12)) / 2;
        int panelWidth = Math.min(PANEL_WIDTH, this.width - 12);
        int panelTop = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
        int panelRight = panelLeft + panelWidth;
        int panelBottom = Math.min(this.height - 4, panelTop + PANEL_HEIGHT);

        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xFF1B1A18);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 3, 0xFFE0B45C);
        graphics.fill(panelLeft + 5, panelTop + 5, panelRight - 5, panelBottom - 5, 0xFF3B3022);
        graphics.fill(panelLeft + 7, panelTop + 7, panelRight - 7, panelBottom - 7, 0xFF171615);

        PlayerRpgData data = ClientRpgDataCache.get();
        boolean hasData = ClientRpgDataCache.hasServerData();
        this.waitingLabel.visible = !hasData;

        if (hasData) {
            int[] values = {
                    data.health(), data.defense(), data.attack(), data.speed(),
                    data.jumpHeight(), data.luck(), data.miningSpeed()
            };
            for (int i = 0; i < values.length; i++) {
                this.statNameLabels[i].visible = true;
                this.statValueLabels[i].visible = true;
                this.statValueLabels[i].setMessage(Component.literal(Integer.toString(values[i])));
            }

            this.pointsLabel.visible = true;
            this.pointsLabel.setMessage(Component.literal(
                    "Available Points: " + data.availablePoints()
                            + "   Lifetime Earned: " + data.lifetimeEarnedPoints()
            ));

            this.historyLabel.visible = true;
            this.historyLabel.setMessage(Component.literal(
                    "Highest Level: " + data.lifetimeHighestLevel()
                            + "   Reward Baseline: " + data.rewardBaselineLevel()
            ));

            this.counterLabel.visible = true;
            this.counterLabel.setMessage(Component.literal(
                    "Persistence Test Counter: " + data.diagnosticCounter()
            ));
        }

        var player = Minecraft.getInstance().player;
        this.uuidLabel.visible = player != null;
        if (player != null) {
            this.uuidLabel.setMessage(Component.literal("Player UUID: " + player.getUUID()));
        }

        // Vanilla widgets render after the panel fill, keeping every line on the
        // same widget rendering path as the increment button.
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
