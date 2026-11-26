package com.example.hitjump;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

public class HitJumpConfigScreen extends Screen {
    private static final int BTN_TOGGLE = 0;
    private static final int BTN_FREQ_MINUS = 1;
    private static final int BTN_FREQ_PLUS = 2;
    private static final int BTN_LATENCY_MINUS = 3;
    private static final int BTN_LATENCY_PLUS = 4;
    private static final int BTN_RESET = 5;
    private static final int BTN_DONE = 6;

    private final Screen parent;
    private ButtonWidget toggleButton;

    public HitJumpConfigScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        HitJumpConfig config = HitJumpConfig.getInstance();

        int centerX = this.width / 2;
        int startY = this.height / 4;

        this.toggleButton = new ButtonWidget(
                BTN_TOGGLE,
                centerX - 100, startY,
                200, 20,
                getToggleLabel(config)
        );
        this.buttons.add(this.toggleButton);

        this.buttons.add(new ButtonWidget(BTN_FREQ_MINUS, centerX - 100, startY + 36, 40, 20, "-5%"));
        this.buttons.add(new ButtonWidget(BTN_FREQ_PLUS, centerX + 60, startY + 36, 40, 20, "+5%"));

        this.buttons.add(new ButtonWidget(BTN_LATENCY_MINUS, centerX - 100, startY + 72, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_LATENCY_PLUS, centerX + 60, startY + 72, 40, 20, "+1t"));

        this.buttons.add(new ButtonWidget(BTN_RESET, centerX - 100, startY + 112, 95, 20, "Reset"));
        this.buttons.add(new ButtonWidget(BTN_DONE, centerX + 5, startY + 112, 95, 20, "Save & Done"));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }

        HitJumpConfig config = HitJumpConfig.getInstance();

        switch (button.id) {
            case BTN_TOGGLE:
                config.enabled = !config.enabled;
                this.toggleButton.message = getToggleLabel(config);
                break;
            case BTN_FREQ_MINUS:
                config.jumpFrequency = Math.max(0, config.jumpFrequency - 5);
                break;
            case BTN_FREQ_PLUS:
                config.jumpFrequency = Math.min(100, config.jumpFrequency + 5);
                break;
            case BTN_LATENCY_MINUS:
                config.latencyTicks = Math.max(0, config.latencyTicks - 1);
                break;
            case BTN_LATENCY_PLUS:
                config.latencyTicks = Math.min(10, config.latencyTicks + 1);
                break;
            case BTN_RESET:
                config.resetDefaults();
                this.toggleButton.message = getToggleLabel(config);
                break;
            case BTN_DONE:
                HitJumpConfig.save();
                this.minecraft.openScreen(this.parent);
                break;
            default:
                break;
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();

        HitJumpConfig config = HitJumpConfig.getInstance();
        int centerX = this.width / 2;
        int startY = this.height / 4;

        this.drawCenteredString(this.textRenderer, "HitJump Settings (Press H to open)", centerX, startY - 24, 0xFFFFFF);

        this.drawCenteredString(
                this.textRenderer,
                "Frequency: " + config.jumpFrequency + "%",
                centerX, startY + 42, 0xE0E0E0
        );

        int latencyMs = config.latencyTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Latency: " + config.latencyTicks + "t (" + latencyMs + " ms)",
                centerX, startY + 78, 0xE0E0E0
        );

        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public void removed() {
        HitJumpConfig.save();
    }

    private String getToggleLabel(HitJumpConfig config) {
        return "HitJump: " + (config.enabled ? "ON" : "OFF");
    }
}
