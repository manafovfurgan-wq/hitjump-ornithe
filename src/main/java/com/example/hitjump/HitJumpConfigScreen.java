package com.example.hitjump;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

public class HitJumpConfigScreen extends Screen {
    // Change this to whatever title you want at the top of the menu
    private static final String MENU_TITLE = "HitJump & NoJumpDelay";

    private static final int BTN_HITJUMP_TOGGLE = 0;
    private static final int BTN_FREQ_MINUS = 1;
    private static final int BTN_FREQ_PLUS = 2;
    private static final int BTN_LATENCY_MINUS = 3;
    private static final int BTN_LATENCY_PLUS = 4;
    private static final int BTN_NOJUMPDELAY_TOGGLE = 5;
    private static final int BTN_JUMPDELAY_MINUS = 6;
    private static final int BTN_JUMPDELAY_PLUS = 7;
    private static final int BTN_KEYBIND = 8;
    private static final int BTN_RESET = 9;
    private static final int BTN_DONE = 10;

    private final Screen parent;
    private ButtonWidget hitJumpToggleButton;
    private ButtonWidget noJumpDelayToggleButton;
    private ButtonWidget keybindButton;

    private boolean waitingForKey = false;
    private final boolean[] keysHeldAtClick = new boolean[256];

    public HitJumpConfigScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        this.waitingForKey = false;
        HitJumpConfig config = HitJumpConfig.getInstance();

        int centerX = this.width / 2;
        int startY = Math.max(28, (this.height - 176) / 2);

        // HitJump controls
        this.hitJumpToggleButton = new ButtonWidget(
                BTN_HITJUMP_TOGGLE,
                centerX - 100, startY,
                200, 20,
                getHitJumpLabel(config)
        );
        this.buttons.add(this.hitJumpToggleButton);

        this.buttons.add(new ButtonWidget(BTN_FREQ_MINUS, centerX - 100, startY + 24, 40, 20, "-5%"));
        this.buttons.add(new ButtonWidget(BTN_FREQ_PLUS, centerX + 60, startY + 24, 40, 20, "+5%"));

        this.buttons.add(new ButtonWidget(BTN_LATENCY_MINUS, centerX - 100, startY + 48, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_LATENCY_PLUS, centerX + 60, startY + 48, 40, 20, "+1t"));

        // NoJumpDelay controls
        this.noJumpDelayToggleButton = new ButtonWidget(
                BTN_NOJUMPDELAY_TOGGLE,
                centerX - 100, startY + 78,
                200, 20,
                getNoJumpDelayLabel(config)
        );
        this.buttons.add(this.noJumpDelayToggleButton);

        this.buttons.add(new ButtonWidget(BTN_JUMPDELAY_MINUS, centerX - 100, startY + 102, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_JUMPDELAY_PLUS, centerX + 60, startY + 102, 40, 20, "+1t"));

        // Keybind, Reset, Save
        this.keybindButton = new ButtonWidget(
                BTN_KEYBIND,
                centerX - 100, startY + 132,
                200, 20,
                getKeybindLabel(config)
        );
        this.buttons.add(this.keybindButton);

        this.buttons.add(new ButtonWidget(BTN_RESET, centerX - 100, startY + 156, 95, 20, "Reset"));
        this.buttons.add(new ButtonWidget(BTN_DONE, centerX + 5, startY + 156, 95, 20, "Save & Done"));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }

        HitJumpConfig config = HitJumpConfig.getInstance();

        if (button.id != BTN_KEYBIND && this.waitingForKey) {
            this.waitingForKey = false;
            this.keybindButton.message = getKeybindLabel(config);
        }

        switch (button.id) {
            case BTN_HITJUMP_TOGGLE:
                config.enabled = !config.enabled;
                this.hitJumpToggleButton.message = getHitJumpLabel(config);
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
            case BTN_NOJUMPDELAY_TOGGLE:
                config.noJumpDelayEnabled = !config.noJumpDelayEnabled;
                this.noJumpDelayToggleButton.message = getNoJumpDelayLabel(config);
                break;
            case BTN_JUMPDELAY_MINUS:
                config.jumpDelayTicks = Math.max(0, config.jumpDelayTicks - 1);
                break;
            case BTN_JUMPDELAY_PLUS:
                config.jumpDelayTicks = Math.min(10, config.jumpDelayTicks + 1);
                break;
            case BTN_KEYBIND:
                this.waitingForKey = !this.waitingForKey;
                if (this.waitingForKey) {
                    for (int k = 1; k < 256; k++) {
                        try {
                            this.keysHeldAtClick[k] = Keyboard.isKeyDown(k);
                        } catch (Exception ignored) {
                            this.keysHeldAtClick[k] = false;
                        }
                    }
                }
                this.keybindButton.message = getKeybindLabel(config);
                break;
            case BTN_RESET:
                config.resetDefaults();
                this.hitJumpToggleButton.message = getHitJumpLabel(config);
                this.noJumpDelayToggleButton.message = getNoJumpDelayLabel(config);
                this.keybindButton.message = getKeybindLabel(config);
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
        HitJumpConfig config = HitJumpConfig.getInstance();

        if (this.waitingForKey) {
            for (int key = 1; key < 256; key++) {
                boolean down;
                try {
                    down = Keyboard.isKeyDown(key);
                } catch (Exception ignored) {
                    continue;
                }
                if (down && !this.keysHeldAtClick[key]) {
                    if (key != Keyboard.KEY_ESCAPE) {
                        config.menuKeyCode = key;
                        HitJumpConfig.save();
                    }
                    this.waitingForKey = false;
                    this.keybindButton.message = getKeybindLabel(config);
                    break;
                } else if (!down) {
                    this.keysHeldAtClick[key] = false;
                }
            }
        }

        this.renderBackground();

        int centerX = this.width / 2;
        int startY = Math.max(28, (this.height - 176) / 2);

        this.drawCenteredString(this.textRenderer, MENU_TITLE, centerX, startY - 18, 0xFFFFFF);

        this.drawCenteredString(
                this.textRenderer,
                "Frequency: " + config.jumpFrequency + "%",
                centerX, startY + 30, 0xE0E0E0
        );

        int latencyMs = config.latencyTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Latency: " + config.latencyTicks + "t (" + latencyMs + " ms)",
                centerX, startY + 54, 0xE0E0E0
        );

        int jumpDelayMs = config.jumpDelayTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Jump Delay: " + config.jumpDelayTicks + "t (" + jumpDelayMs + " ms)",
                centerX, startY + 108, 0xE0E0E0
        );

        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public void removed() {
        HitJumpConfig.save();
    }

    private String getHitJumpLabel(HitJumpConfig config) {
        return "HitJump: " + (config.enabled ? "ON" : "OFF");
    }

    private String getNoJumpDelayLabel(HitJumpConfig config) {
        return "NoJumpDelay: " + (config.noJumpDelayEnabled ? "ON" : "OFF");
    }

    private String getKeybindLabel(HitJumpConfig config) {
        if (this.waitingForKey) {
            return "Menu Key: > Press any key <";
        }
        String keyName = Keyboard.getKeyName(config.menuKeyCode);
        if (keyName == null) {
            keyName = "Key " + config.menuKeyCode;
        }
        return "Menu Key: " + keyName;
    }
}
