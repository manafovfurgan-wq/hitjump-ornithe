package com.example.hitjump;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

public class HitJumpConfigScreen extends Screen {
    private static final String MENU_TITLE = "Tomato Cream";

    private static final int BTN_HITJUMP_TOGGLE = 0;
    private static final int BTN_HITJUMP_MODE = 1;
    private static final int BTN_FREQ_MINUS = 2;
    private static final int BTN_FREQ_PLUS = 3;
    private static final int BTN_LATENCY_MINUS = 4;
    private static final int BTN_LATENCY_PLUS = 5;

    private static final int BTN_NOJUMPDELAY_TOGGLE = 6;
    private static final int BTN_JUMPDELAY_MINUS = 7;
    private static final int BTN_JUMPDELAY_PLUS = 8;

    private static final int BTN_NOHITDELAY_TOGGLE = 9;
    private static final int BTN_HITDELAY_MINUS = 10;
    private static final int BTN_HITDELAY_PLUS = 11;

    private static final int BTN_GODPLACE_TOGGLE = 12;
    private static final int BTN_GODPLACE_MINUS = 13;
    private static final int BTN_GODPLACE_PLUS = 14;

    private static final int BTN_HEADFIX_TOGGLE = 15;
    private static final int BTN_SMOOTHSNEAK_TOGGLE = 16;
    private static final int BTN_PARALLAX_TOGGLE = 17;

    private static final int BTN_KEYBIND = 18;
    private static final int BTN_RESET = 19;
    private static final int BTN_DONE = 20;

    private final Screen parent;
    private ButtonWidget hitJumpToggleButton;
    private ButtonWidget hitJumpModeButton;
    private ButtonWidget noJumpDelayToggleButton;
    private ButtonWidget noHitDelayToggleButton;
    private ButtonWidget godPlaceToggleButton;
    private ButtonWidget headFixToggleButton;
    private ButtonWidget smoothSneakToggleButton;
    private ButtonWidget parallaxToggleButton;
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
        int startY = Math.max(22, (this.height - 224) / 2);

        // Left column: HitJump + NoJumpDelay
        this.hitJumpToggleButton = new ButtonWidget(
                BTN_HITJUMP_TOGGLE,
                leftX - 100, startY,
                200, 20,
                getHitJumpLabel(config)
        );
        this.buttons.add(this.hitJumpToggleButton);

        this.hitJumpModeButton = new ButtonWidget(
                BTN_HITJUMP_MODE,
                leftX - 100, startY + 24,
                200, 20,
                getHitJumpModeLabel(config)
        );
        this.buttons.add(this.hitJumpModeButton);

        this.buttons.add(new ButtonWidget(BTN_FREQ_MINUS, leftX - 100, startY + 48, 40, 20, "-5%"));
        this.buttons.add(new ButtonWidget(BTN_FREQ_PLUS, leftX + 60, startY + 48, 40, 20, "+5%"));

        this.buttons.add(new ButtonWidget(BTN_LATENCY_MINUS, leftX - 100, startY + 72, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_LATENCY_PLUS, leftX + 60, startY + 72, 40, 20, "+1t"));

        this.noJumpDelayToggleButton = new ButtonWidget(
                BTN_NOJUMPDELAY_TOGGLE,
                leftX - 100, startY + 100,
                200, 20,
                getNoJumpDelayLabel(config)
        );
        this.buttons.add(this.noJumpDelayToggleButton);

        this.buttons.add(new ButtonWidget(BTN_JUMPDELAY_MINUS, leftX - 100, startY + 124, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_JUMPDELAY_PLUS, leftX + 60, startY + 124, 40, 20, "+1t"));

        // Right column: NoHitDelay + GodPlace + 1.7 Camera / Head / Eye Fixes
        this.noHitDelayToggleButton = new ButtonWidget(
                BTN_NOHITDELAY_TOGGLE,
                rightX - 100, startY,
                200, 20,
                getNoHitDelayLabel(config)
        );
        this.buttons.add(this.noHitDelayToggleButton);

        this.buttons.add(new ButtonWidget(BTN_HITDELAY_MINUS, rightX - 100, startY + 24, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_HITDELAY_PLUS, rightX + 60, startY + 24, 40, 20, "+1t"));

        this.godPlaceToggleButton = new ButtonWidget(
                BTN_GODPLACE_TOGGLE,
                rightX - 100, startY + 52,
                200, 20,
                getGodPlaceLabel(config)
        );
        this.buttons.add(this.godPlaceToggleButton);

        this.buttons.add(new ButtonWidget(BTN_GODPLACE_MINUS, rightX - 100, startY + 76, 40, 20, "-1t"));
        this.buttons.add(new ButtonWidget(BTN_GODPLACE_PLUS, rightX + 60, startY + 76, 40, 20, "+1t"));

        this.headFixToggleButton = new ButtonWidget(
                BTN_HEADFIX_TOGGLE,
                rightX - 100, startY + 104,
                200, 20,
                getHeadFixLabel(config)
        );
        this.buttons.add(this.headFixToggleButton);

        this.smoothSneakToggleButton = new ButtonWidget(
                BTN_SMOOTHSNEAK_TOGGLE,
                rightX - 100, startY + 128,
                200, 20,
                getSmoothSneakLabel(config)
        );
        this.buttons.add(this.smoothSneakToggleButton);

        this.parallaxToggleButton = new ButtonWidget(
                BTN_PARALLAX_TOGGLE,
                rightX - 100, startY + 152,
                200, 20,
                getParallaxLabel(config)
        );
        this.buttons.add(this.parallaxToggleButton);

        // Bottom centered: Keybind, Reset, Save & Done
        this.keybindButton = new ButtonWidget(
                BTN_KEYBIND,
                centerX - 100, startY + 180,
                200, 20,
                getKeybindLabel(config)
        );
        this.buttons.add(this.keybindButton);

        this.buttons.add(new ButtonWidget(BTN_RESET, centerX - 100, startY + 204, 95, 20, "Reset"));
        this.buttons.add(new ButtonWidget(BTN_DONE, centerX + 5, startY + 204, 95, 20, "Save & Done"));
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
            case BTN_HITJUMP_MODE:
                config.hitJumpPlayersOnly = !config.hitJumpPlayersOnly;
                this.hitJumpModeButton.message = getHitJumpModeLabel(config);
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
            case BTN_GODPLACE_TOGGLE:
                config.godPlaceEnabled = !config.godPlaceEnabled;
                this.godPlaceToggleButton.message = getGodPlaceLabel(config);
                break;
            case BTN_GODPLACE_MINUS:
                config.godPlaceDelayTicks = Math.max(0, config.godPlaceDelayTicks - 1);
                break;
            case BTN_GODPLACE_PLUS:
                config.godPlaceDelayTicks = Math.min(3, config.godPlaceDelayTicks + 1);
                break;
            case BTN_HEADFIX_TOGGLE:
                config.headFixEnabled = !config.headFixEnabled;
                this.headFixToggleButton.message = getHeadFixLabel(config);
                break;
            case BTN_SMOOTHSNEAK_TOGGLE:
                config.smoothSneakEnabled = !config.smoothSneakEnabled;
                this.smoothSneakToggleButton.message = getSmoothSneakLabel(config);
                break;
            case BTN_PARALLAX_TOGGLE:
                config.parallaxFixEnabled = !config.parallaxFixEnabled;
                this.parallaxToggleButton.message = getParallaxLabel(config);
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
                this.hitJumpModeButton.message = getHitJumpModeLabel(config);
                this.noJumpDelayToggleButton.message = getNoJumpDelayLabel(config);
                this.noHitDelayToggleButton.message = getNoHitDelayLabel(config);
                this.godPlaceToggleButton.message = getGodPlaceLabel(config);
                this.headFixToggleButton.message = getHeadFixLabel(config);
                this.smoothSneakToggleButton.message = getSmoothSneakLabel(config);
                this.parallaxToggleButton.message = getParallaxLabel(config);
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
        int startY = Math.max(22, (this.height - 224) / 2);

        this.drawCenteredString(this.textRenderer, MENU_TITLE, centerX, startY - 15, 0xFFFFFF);

        // Left column labels
        this.drawCenteredString(
                this.textRenderer,
                "Frequency: " + config.jumpFrequency + "%",
                leftX, startY + 54, 0xE0E0E0
        );

        int latencyMs = config.latencyTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Latency: " + config.latencyTicks + "t (" + latencyMs + " ms)",
                leftX, startY + 78, 0xE0E0E0
        );

        int jumpDelayMs = config.jumpDelayTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Jump: " + config.jumpDelayTicks + "t (" + jumpDelayMs + " ms)",
                leftX, startY + 130, 0xE0E0E0
        );

        // Right column labels
        int hitDelayMs = config.hitDelayTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Hit: " + config.hitDelayTicks + "t (" + hitDelayMs + " ms)",
                rightX, startY + 30, 0xE0E0E0
        );

        int placeDelayMs = config.godPlaceDelayTicks * 50;
        this.drawCenteredString(
                this.textRenderer,
                "Place: " + config.godPlaceDelayTicks + "t (" + placeDelayMs + " ms)",
                rightX, startY + 82, 0xE0E0E0
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

    private String getHitJumpModeLabel(HitJumpConfig config) {
        return "Target: " + (config.hitJumpPlayersOnly ? "Players Only" : "All Knockback");
    }

    private String getNoJumpDelayLabel(HitJumpConfig config) {
        return "NoJumpDelay: " + (config.noJumpDelayEnabled ? "ON" : "OFF");
    }

    private String getNoHitDelayLabel(HitJumpConfig config) {
        return "NoHitDelay: " + (config.noHitDelayEnabled ? "ON" : "OFF");
    }

    private String getGodPlaceLabel(HitJumpConfig config) {
        return "GodPlace: " + (config.godPlaceEnabled ? "ON" : "OFF");
    }

    private String getHeadFixLabel(HitJumpConfig config) {
        return "1.7 Head Fix: " + (config.headFixEnabled ? "ON" : "OFF");
    }

    private String getSmoothSneakLabel(HitJumpConfig config) {
        return "1.7 Smooth Sneak: " + (config.smoothSneakEnabled ? "ON" : "OFF");
    }

    private String getParallaxLabel(HitJumpConfig config) {
        return "Parallax Fix: " + (config.parallaxFixEnabled ? "ON" : "OFF");
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
