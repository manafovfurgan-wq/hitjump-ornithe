package com.example.hitjump;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

public class HitJumpConfigScreen extends Screen {
    // Change this to whatever title you want at the top of the menu
    private static final String MENU_TITLE = "HitJump & Delay Fixes";

    private static final int BTN_HITJUMP_TOGGLE = 0;
    private static final int BTN_FREQ_MINUS = 1;
    private static final int BTN_FREQ_PLUS = 2;
    private static final int BTN_LATENCY_MINUS = 3;
    private static final int BTN_LATENCY_PLUS = 4;

    private static final int BTN_NOJUMPDELAY_TOGGLE = 5;
    private static final int BTN_JUMPDELAY_MINUS = 6;
    private static final int BTN_JUMPDELAY_PLUS = 7;

    private static final int BTN_NOHITDELAY_TOGGLE = 8;
    private static final int BTN_HITDELAY_MINUS = 9;
    private static final int BTN_HITDELAY_PLUS = 10;

    private static final int BTN_KEYBIND = 11;
    private static final int BTN_RESET = 12;
    private static final int BTN_DONE = 13;

    private final Screen parent;
    private ButtonWidget hitJumpToggleButton;
    private ButtonWidget noJumpDelayToggleButton;
    private ButtonWidget noHitDelayToggleButton;
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
        int leftX = centerX - 105;
        int rightX = centerX + 105;
        int startY = Math.max(28, (this.height - 160) / 2);

        // Left column: HitJump
        this.hitJumpToggleButton = new ButtonWidget(
                BTN_HITJUMP_TOGGLE,
                leftX - 100, startY,
                200, 20,
                getHitJumpLabel(config)
        );
        this.buttons.add(this.hitJumpToggleButton);

        this.buttons.add(new ButtonWidget(BTN_FREQ_MINUS, leftX - 100, startY + 26, 40, 20, "-5%"));
        this.buttons.add(new ButtonWidget(BTN_FREQ_PLUS, leftX + 60, startY + 26, 40, 20, "+5%"));

        this.buttons.add(new ButtonWidget(BTN_LATENCY_MINUS, leftX - 100, startY + 52, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_LATENCY_PLUS, leftX + 60, startY + 52, 40, 20, "+1t"));

        // Right column: NoJumpDelay & NoHitDelay
        this.noJumpDelayToggleButton = new ButtonWidget(
                BTN_NOJUMPDELAY_TOGGLE,
                rightX - 100, startY,
                200, 20,
                getNoJumpDelayLabel(config)
        );
        this.buttons.add(this.noJumpDelayToggleButton);

        this.buttons.add(new ButtonWidget(BTN_JUMPDELAY_MINUS, rightX - 100, startY + 26, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_JUMPDELAY_PLUS, rightX + 60, startY + 26, 40, 20, "+1t"));

        this.noHitDelayToggleButton = new ButtonWidget(
                BTN_NOHITDELAY_TOGGLE,
                rightX - 100, startY + 56,
                200, 20,
                getNoHitDelayLabel(config)
        );
        this.buttons.add(this.noHitDelayToggleButton);

        this.buttons.add(new ButtonWidget(BTN_HITDELAY_MINUS, rightX - 100, startY + 82, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_HITDELAY_PLUS, rightX + 60, startY + 82, 40, 20, "+1t"));

        // Bottom centered: Keybind, Reset, Save & Done
        this.keybindButton = new ButtonWidget(
                BTN_KEYBIND,
                centerX - 100, startY + 114,
                200, 20,
                getKeybindLabel(config)
        );
        this.buttons.add(this.keybindButton);

        this.buttons.add(new ButtonWidget(BTN_RESET, centerX - 100, startY + 140, 95, 20, "Reset"));
        this.buttons.add(new ButtonWidget(BTN_DONE, centerX + 5, startY + 140, 95, 20, "Save & Done"));
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
            case BTN_NOHITDELAY_TOGGLE:
                config.noHitDelayEnabled = !config.noHitDelayEnabled;
                this.noHitDelayToggleButton.message = getNoHitDelayLabel(config);
                break;
            case BTN_HITDELAY_MINUS:
                config.hitDelayTicks = Math.max(0, config.hitDelayTicks - 1);
                break;
            case BTN_HITDELAY_PLUS:
                config.hitDelayTicks = Math.min(10, config.hitDelayTicks + 1);
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
                this.noHitDelayToggleButton.message = getNoHitDelayLabel(config);
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
        int leftX = centerX - 105;
        int rightX = centerX + 105;
        int startY = Math.max(28, (this.height - 160) / 2);

        this.drawCenteredString(this.textRenderer, MENU_TITLE, centerX, startY - 20, 0xFFFFFF);

        // Left column labels
        this.drawCenteredString(
                this.textRenderer,
                "Frequency: " + config.jumpFrequency + "%",
                leftX, startY + 32, 0xE0E0E0
        );

        int latencyMs = config.latencyTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Latency: " + config.latencyTicks + "t (" + latencyMs + " ms)",
                leftX, startY + 58, 0xE0E0E0
        );

        // Right column labels
        int jumpDelayMs = config.jumpDelayTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Jump: " + config.jumpDelayTicks + "t (" + jumpDelayMs + " ms)",
                rightX, startY + 32, 0xE0E0E0
        );

        int hitDelayMs = config.hitDelayTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Hit: " + config.hitDelayTicks + "t (" + hitDelayMs + " ms)",
                rightX, startY + 88, 0xE0E0E0
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

    private String getNoHitDelayLabel(HitJumpConfig config) {
        return "NoHitDelay: " + (config.noHitDelayEnabled ? "ON" : "OFF");
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
