package com.example.hitjump;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class HitJumpConfigScreen extends Screen {
    private static final String MENU_TITLE = "Tomato Cream";

    private static final int TAB_COMBAT = 0;
    private static final int TAB_VISUALS = 1;

    private static final int BTN_TAB_COMBAT = 100;
    private static final int BTN_TAB_VISUALS = 101;

    // Combat & Movement tab buttons
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
    private static final int BTN_GODPLACE_KEY = 13;
    private static final int BTN_GODPLACE_MINUS = 14;
    private static final int BTN_GODPLACE_PLUS = 15;

    private static final int BTN_HEADFIX_TOGGLE = 16;
    private static final int BTN_SMOOTHSNEAK_TOGGLE = 17;
    private static final int BTN_PARALLAX_TOGGLE = 18;

    // Visuals tab buttons
    private static final int BTN_WALL_HITBOX_TOGGLE = 22;
    private static final int BTN_HITBOX_TARGET = 23;
    private static final int BTN_HITBOX_RANGE_MINUS = 24;
    private static final int BTN_HITBOX_RANGE_PLUS = 25;
    private static final int BTN_WALL_NAMETAG_TOGGLE = 26;
    private static final int BTN_NAMETAG_RANGE_MINUS = 27;
    private static final int BTN_NAMETAG_RANGE_PLUS = 28;

    // Shared bottom controls
    private static final int BTN_KEYBIND = 19;
    private static final int BTN_RESET = 20;
    private static final int BTN_DONE = 21;

    private final Screen parent;
    private int activeTab = TAB_COMBAT;

    private ButtonWidget tabCombatButton;
    private ButtonWidget tabVisualsButton;

    private ButtonWidget hitJumpToggleButton;
    private ButtonWidget hitJumpModeButton;
    private ButtonWidget noJumpDelayToggleButton;
    private ButtonWidget noHitDelayToggleButton;
    private ButtonWidget godPlaceToggleButton;
    private ButtonWidget godPlaceKeyButton;
    private ButtonWidget headFixToggleButton;
    private ButtonWidget smoothSneakToggleButton;
    private ButtonWidget parallaxToggleButton;

    private ButtonWidget wallHitboxToggleButton;
    private ButtonWidget hitboxTargetButton;
    private ButtonWidget wallNametagToggleButton;

    private ButtonWidget keybindButton;

    private boolean waitingForMenuKey = false;
    private boolean waitingForGodPlaceKey = false;
    private final boolean[] keysHeldAtClick = new boolean[256];
    private final boolean[] mouseHeldAtClick = new boolean[16];

    // Interactive HSB color picker state
    private float pickerHue = 0.0F;
    private float pickerSat = 0.0F;
    private float pickerVal = 0.0F;
    private int activePickerDrag = 0; // 0 = none, 1 = SV square, 2 = Hue bar, 3 = Opacity bar
    private boolean wasLeftMouseDown = false;

    public HitJumpConfigScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        syncPickerFromConfig();
        rebuildButtons();
    }

    private void syncPickerFromConfig() {
        int rgb = HitJumpConfig.getInstance().nametagBgColor;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.pickerHue = hsb[0];
        this.pickerSat = hsb[1];
        this.pickerVal = hsb[2];
    }

    private void rebuildButtons() {
        this.buttons.clear();
        this.waitingForMenuKey = false;
        this.waitingForGodPlaceKey = false;
        this.activePickerDrag = 0;

        HitJumpConfig config = HitJumpConfig.getInstance();

        int centerX = this.width / 2;
        int leftX = centerX - 105;
        int rightX = centerX + 105;
        int startY = getStartY();
        int bodyY = startY + 26;

        // Top tab switcher
        this.tabCombatButton = new ButtonWidget(
                BTN_TAB_COMBAT,
                leftX - 100, startY,
                200, 20,
                (this.activeTab == TAB_COMBAT ? "> Combat & Movement <" : "Combat & Movement")
        );
        this.tabCombatButton.active = (this.activeTab != TAB_COMBAT);
        this.buttons.add(this.tabCombatButton);

        this.tabVisualsButton = new ButtonWidget(
                BTN_TAB_VISUALS,
                rightX - 100, startY,
                200, 20,
                (this.activeTab == TAB_VISUALS ? "> Visuals & Tags <" : "Visuals & Tags")
        );
        this.tabVisualsButton.active = (this.activeTab != TAB_VISUALS);
        this.buttons.add(this.tabVisualsButton);

        if (this.activeTab == TAB_COMBAT) {
            // Left column: HitJump + NoJumpDelay + NoHitDelay
            this.hitJumpToggleButton = new ButtonWidget(
                    BTN_HITJUMP_TOGGLE, leftX - 100, bodyY, 200, 20, getHitJumpLabel(config)
            );
            this.buttons.add(this.hitJumpToggleButton);

            this.hitJumpModeButton = new ButtonWidget(
                    BTN_HITJUMP_MODE, leftX - 100, bodyY + 24, 200, 20, getHitJumpModeLabel(config)
            );
            this.buttons.add(this.hitJumpModeButton);

            this.buttons.add(new ButtonWidget(BTN_FREQ_MINUS, leftX - 100, bodyY + 48, 40, 20, "-5%"));
            this.buttons.add(new ButtonWidget(BTN_FREQ_PLUS, leftX + 60, bodyY + 48, 40, 20, "+5%"));

            this.buttons.add(new ButtonWidget(BTN_LATENCY_MINUS, leftX - 100, bodyY + 72, 40, 20, "-1t"));
            this.buttons.add(new ButtonWidget(BTN_LATENCY_PLUS, leftX + 60, bodyY + 72, 40, 20, "+1t"));

            this.noJumpDelayToggleButton = new ButtonWidget(
                    BTN_NOJUMPDELAY_TOGGLE, leftX - 100, bodyY + 100, 200, 20, getNoJumpDelayLabel(config)
            );
            this.buttons.add(this.noJumpDelayToggleButton);

            this.buttons.add(new ButtonWidget(BTN_JUMPDELAY_MINUS, leftX - 100, bodyY + 124, 40, 20, "-1t"));
            this.buttons.add(new ButtonWidget(BTN_JUMPDELAY_PLUS, leftX + 60, bodyY + 124, 40, 20, "+1t"));

            this.noHitDelayToggleButton = new ButtonWidget(
                    BTN_NOHITDELAY_TOGGLE, leftX - 100, bodyY + 152, 200, 20, getNoHitDelayLabel(config)
            );
            this.buttons.add(this.noHitDelayToggleButton);

            this.buttons.add(new ButtonWidget(BTN_HITDELAY_MINUS, leftX - 100, bodyY + 176, 40, 20, "-1t"));
            this.buttons.add(new ButtonWidget(BTN_HITDELAY_PLUS, leftX + 60, bodyY + 176, 40, 20, "+1t"));

            // Right column: GodPlace + 1.7 Fixes + Menu Key + Reset / Save
            this.godPlaceToggleButton = new ButtonWidget(
                    BTN_GODPLACE_TOGGLE, rightX - 100, bodyY, 200, 20, getGodPlaceLabel(config)
            );
            this.buttons.add(this.godPlaceToggleButton);

            this.godPlaceKeyButton = new ButtonWidget(
                    BTN_GODPLACE_KEY, rightX - 100, bodyY + 24, 200, 20, getGodPlaceKeyLabel(config)
            );
            this.buttons.add(this.godPlaceKeyButton);

            this.buttons.add(new ButtonWidget(BTN_GODPLACE_MINUS, rightX - 100, bodyY + 48, 40, 20, "-1t"));
            this.buttons.add(new ButtonWidget(BTN_GODPLACE_PLUS, rightX + 60, bodyY + 48, 40, 20, "+1t"));

            this.headFixToggleButton = new ButtonWidget(
                    BTN_HEADFIX_TOGGLE, rightX - 100, bodyY + 76, 200, 20, getHeadFixLabel(config)
            );
            this.buttons.add(this.headFixToggleButton);

            this.smoothSneakToggleButton = new ButtonWidget(
                    BTN_SMOOTHSNEAK_TOGGLE, rightX - 100, bodyY + 100, 200, 20, getSmoothSneakLabel(config)
            );
            this.buttons.add(this.smoothSneakToggleButton);

            this.parallaxToggleButton = new ButtonWidget(
                    BTN_PARALLAX_TOGGLE, rightX - 100, bodyY + 124, 200, 20, getParallaxLabel(config)
            );
            this.buttons.add(this.parallaxToggleButton);

            this.keybindButton = new ButtonWidget(
                    BTN_KEYBIND, rightX - 100, bodyY + 152, 200, 20, getKeybindLabel(config)
            );
            this.buttons.add(this.keybindButton);

            this.buttons.add(new ButtonWidget(BTN_RESET, rightX - 100, bodyY + 176, 95, 20, "Reset"));
            this.buttons.add(new ButtonWidget(BTN_DONE, rightX + 5, bodyY + 176, 95, 20, "Save & Done"));
        } else {
            // Visuals & Tags tab - Left column: Hitboxes + Nametags + Menu Key + Reset / Save
            this.wallHitboxToggleButton = new ButtonWidget(
                    BTN_WALL_HITBOX_TOGGLE, leftX - 100, bodyY, 200, 20, getWallHitboxLabel(config)
            );
            this.buttons.add(this.wallHitboxToggleButton);

            this.hitboxTargetButton = new ButtonWidget(
                    BTN_HITBOX_TARGET, leftX - 100, bodyY + 24, 200, 20, getHitboxTargetLabel(config)
            );
            this.buttons.add(this.hitboxTargetButton);

            this.buttons.add(new ButtonWidget(BTN_HITBOX_RANGE_MINUS, leftX - 100, bodyY + 48, 40, 20, "-8m"));
            this.buttons.add(new ButtonWidget(BTN_HITBOX_RANGE_PLUS, leftX + 60, bodyY + 48, 40, 20, "+8m"));

            this.wallNametagToggleButton = new ButtonWidget(
                    BTN_WALL_NAMETAG_TOGGLE, leftX - 100, bodyY + 80, 200, 20, getWallNametagLabel(config)
            );
            this.buttons.add(this.wallNametagToggleButton);

            this.buttons.add(new ButtonWidget(BTN_NAMETAG_RANGE_MINUS, leftX - 100, bodyY + 104, 40, 20, "-16m"));
            this.buttons.add(new ButtonWidget(BTN_NAMETAG_RANGE_PLUS, leftX + 60, bodyY + 104, 40, 20, "+16m"));

            this.keybindButton = new ButtonWidget(
                    BTN_KEYBIND, leftX - 100, bodyY + 152, 200, 20, getKeybindLabel(config)
            );
            this.buttons.add(this.keybindButton);

            this.buttons.add(new ButtonWidget(BTN_RESET, leftX - 100, bodyY + 176, 95, 20, "Reset"));
            this.buttons.add(new ButtonWidget(BTN_DONE, leftX + 5, bodyY + 176, 95, 20, "Save & Done"));
        }
    }

    private int getStartY() {
        return Math.max(20, (this.height - 224) / 2);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!button.active) {
            return;
        }

        HitJumpConfig config = HitJumpConfig.getInstance();

        if (button.id != BTN_KEYBIND && this.waitingForMenuKey) {
            this.waitingForMenuKey = false;
            if (this.keybindButton != null) {
                this.keybindButton.message = getKeybindLabel(config);
            }
        }
        if (button.id != BTN_GODPLACE_KEY && this.waitingForGodPlaceKey) {
            this.waitingForGodPlaceKey = false;
            if (this.godPlaceKeyButton != null) {
                this.godPlaceKeyButton.message = getGodPlaceKeyLabel(config);
            }
        }

        switch (button.id) {
            case BTN_TAB_COMBAT:
                this.activeTab = TAB_COMBAT;
                rebuildButtons();
                return;
            case BTN_TAB_VISUALS:
                this.activeTab = TAB_VISUALS;
                syncPickerFromConfig();
                rebuildButtons();
                return;
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
            case BTN_GODPLACE_KEY:
                this.waitingForGodPlaceKey = !this.waitingForGodPlaceKey;
                if (this.waitingForGodPlaceKey) {
                    snapshotInputs();
                }
                this.godPlaceKeyButton.message = getGodPlaceKeyLabel(config);
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
            case BTN_WALL_HITBOX_TOGGLE:
                config.wallHitboxesEnabled = !config.wallHitboxesEnabled;
                this.wallHitboxToggleButton.message = getWallHitboxLabel(config);
                break;
            case BTN_HITBOX_TARGET:
                config.hitboxTargetMode = (config.hitboxTargetMode + 1) % 3;
                this.hitboxTargetButton.message = getHitboxTargetLabel(config);
                break;
            case BTN_HITBOX_RANGE_MINUS:
                config.hitboxRange = Math.max(8, config.hitboxRange - 8);
                break;
            case BTN_HITBOX_RANGE_PLUS:
                config.hitboxRange = Math.min(256, config.hitboxRange + 8);
                break;
            case BTN_WALL_NAMETAG_TOGGLE:
                config.wallNametagsEnabled = !config.wallNametagsEnabled;
                this.wallNametagToggleButton.message = getWallNametagLabel(config);
                break;
            case BTN_NAMETAG_RANGE_MINUS:
                config.nametagRange = Math.max(16, config.nametagRange - 16);
                break;
            case BTN_NAMETAG_RANGE_PLUS:
                config.nametagRange = Math.min(256, config.nametagRange + 16);
                break;
            case BTN_KEYBIND:
                this.waitingForMenuKey = !this.waitingForMenuKey;
                if (this.waitingForMenuKey) {
                    snapshotInputs();
                }
                this.keybindButton.message = getKeybindLabel(config);
                break;
            case BTN_RESET:
                config.resetDefaults();
                syncPickerFromConfig();
                rebuildButtons();
                break;
            case BTN_DONE:
                HitJumpConfig.save();
                this.minecraft.openScreen(this.parent);
                break;
            default:
                break;
        }
    }

    private void snapshotInputs() {
        for (int k = 1; k < 256; k++) {
            try {
                this.keysHeldAtClick[k] = Keyboard.isKeyDown(k);
            } catch (Exception ignored) {
                this.keysHeldAtClick[k] = false;
            }
        }
        int btnCount = 0;
        try {
            btnCount = Math.min(Mouse.getButtonCount(), this.mouseHeldAtClick.length);
        } catch (Exception ignored) {
        }
        for (int b = 0; b < this.mouseHeldAtClick.length; b++) {
            if (b < btnCount) {
                try {
                    this.mouseHeldAtClick[b] = Mouse.isButtonDown(b);
                } catch (Exception ignored) {
                    this.mouseHeldAtClick[b] = false;
                }
            } else {
                this.mouseHeldAtClick[b] = false;
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        HitJumpConfig config = HitJumpConfig.getInstance();

        handleKeyRebinding(config);

        this.renderBackground();

        int centerX = this.width / 2;
        int leftX = centerX - 105;
        int rightX = centerX + 105;
        int startY = getStartY();
        int bodyY = startY + 26;

        this.drawCenteredString(this.textRenderer, MENU_TITLE, centerX, startY - 14, 0xFFFFFF);

        if (this.activeTab == TAB_COMBAT) {
            this.drawCenteredString(
                    this.textRenderer,
                    "Frequency: " + config.jumpFrequency + "%",
                    leftX, bodyY + 54, 0xE0E0E0
            );

            int latencyMs = config.latencyTicks * 50;
            this.drawCenteredString(
                    this.textRenderer,
                    "Latency: " + config.latencyTicks + "t (" + latencyMs + " ms)",
                    leftX, bodyY + 78, 0xE0E0E0
            );

            int jumpDelayMs = config.jumpDelayTicks * 50;
            this.drawCenteredString(
                    this.textRenderer,
                    "Jump: " + config.jumpDelayTicks + "t (" + jumpDelayMs + " ms)",
                    leftX, bodyY + 130, 0xE0E0E0
            );

            int hitDelayMs = config.hitDelayTicks * 50;
            this.drawCenteredString(
                    this.textRenderer,
                    "Hit: " + config.hitDelayTicks + "t (" + hitDelayMs + " ms)",
                    leftX, bodyY + 182, 0xE0E0E0
            );

            int placeDelayMs = config.godPlaceDelayTicks * 50;
            this.drawCenteredString(
                    this.textRenderer,
                    "Place: " + config.godPlaceDelayTicks + "t (" + placeDelayMs + " ms)",
                    rightX, bodyY + 54, 0xE0E0E0
            );
        } else {
            this.drawCenteredString(
                    this.textRenderer,
                    "Range: " + config.hitboxRange + "m",
                    leftX, bodyY + 54, 0xE0E0E0
            );

            this.drawCenteredString(
                    this.textRenderer,
                    "Tag Range: " + config.nametagRange + "m",
                    leftX, bodyY + 110, 0xE0E0E0
            );

            renderColorPickerSection(config, rightX - 100, bodyY, mouseX, mouseY);
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    private void renderColorPickerSection(HitJumpConfig config, int x, int y, int mouseX, int mouseY) {
        int svX = x;
        int svY = y + 14;
        int svW = 168;
        int svH = 104;

        int hueX = x + 176;
        int hueY = svY;
        int hueW = 24;
        int hueH = svH;

        int alphaX = x;
        int alphaY = svY + svH + 10;
        int alphaW = 200;
        int alphaH = 16;

        int previewX = x;
        int previewY = alphaY + alphaH + 10;
        int previewW = 200;
        int previewH = 26;

        // Handle click & drag on SV box, Hue bar, and Opacity bar
        boolean leftDown = false;
        try {
            leftDown = Mouse.isButtonDown(0);
        } catch (Exception ignored) {
        }

        if (leftDown) {
            if (!this.wasLeftMouseDown && this.activePickerDrag == 0) {
                if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                    this.activePickerDrag = 1;
                } else if (mouseX >= hueX && mouseX <= hueX + hueW && mouseY >= hueY && mouseY <= hueY + hueH) {
                    this.activePickerDrag = 2;
                    // Lift out of pure black/white if user clicks the rainbow bar directly
                    if (this.pickerVal < 0.08F) {
                        this.pickerVal = 0.85F;
                    }
                    if (this.pickerSat < 0.08F) {
                        this.pickerSat = 0.85F;
                    }
                } else if (mouseX >= alphaX && mouseX <= alphaX + alphaW && mouseY >= alphaY && mouseY <= alphaY + alphaH) {
                    this.activePickerDrag = 3;
                }
            }

            if (this.activePickerDrag == 1) {
                this.pickerSat = clamp01((mouseX - svX) / (float) svW);
                this.pickerVal = 1.0F - clamp01((mouseY - svY) / (float) svH);
                config.nametagBgColor = Color.HSBtoRGB(this.pickerHue, this.pickerSat, this.pickerVal) & 0xFFFFFF;
            } else if (this.activePickerDrag == 2) {
                this.pickerHue = clamp01((mouseY - hueY) / (float) hueH);
                config.nametagBgColor = Color.HSBtoRGB(this.pickerHue, this.pickerSat, this.pickerVal) & 0xFFFFFF;
            } else if (this.activePickerDrag == 3) {
                float ratio = clamp01((mouseX - alphaX) / (float) alphaW);
                config.nametagBgOpacity = Math.round(ratio * 100.0F);
            }
        } else {
            if (this.activePickerDrag != 0) {
                HitJumpConfig.save();
                this.activePickerDrag = 0;
            }
        }
        this.wasLeftMouseDown = leftDown;

        // Labels above picker
        this.drawCenteredString(this.textRenderer, "Nametag Background Color", x + 100, y + 2, 0xFFFFFF);

        int pureHueRgb = Color.HSBtoRGB(this.pickerHue, 1.0F, 1.0F);
        float hr = ((pureHueRgb >> 16) & 0xFF) / 255.0F;
        float hg = ((pureHueRgb >> 8) & 0xFF) / 255.0F;
        float hb = (pureHueRgb & 0xFF) / 255.0F;

        int curRgb = config.nametagBgColor;
        float cr = ((curRgb >> 16) & 0xFF) / 255.0F;
        float cg = ((curRgb >> 8) & 0xFF) / 255.0F;
        float cb = (curRgb & 0xFF) / 255.0F;
        float ca = config.nametagBgOpacity / 100.0F;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        // 1. SV Square border + White->Hue horizontal gradient + Transparent->Black vertical overlay
        drawSolidRect(svX - 1, svY - 1, svX + svW + 1, svY + svH + 1, 0.7F, 0.7F, 0.7F, 0.9F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F); GL11.glVertex2f(svX, svY);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F); GL11.glVertex2f(svX, svY + svH);
        GL11.glColor4f(hr, hg, hb, 1.0F);       GL11.glVertex2f(svX + svW, svY + svH);
        GL11.glColor4f(hr, hg, hb, 1.0F);       GL11.glVertex2f(svX + svW, svY);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.0F); GL11.glVertex2f(svX, svY);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 1.0F); GL11.glVertex2f(svX, svY + svH);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 1.0F); GL11.glVertex2f(svX + svW, svY + svH);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.0F); GL11.glVertex2f(svX + svW, svY);
        GL11.glEnd();

        // SV Cursor crosshair
        float cursorX = svX + this.pickerSat * svW;
        float cursorY = svY + (1.0F - this.pickerVal) * svH;
        drawSolidRect(cursorX - 3, cursorY - 3, cursorX + 3, cursorY + 3, 0.0F, 0.0F, 0.0F, 1.0F);
        drawSolidRect(cursorX - 2, cursorY - 2, cursorX + 2, cursorY + 2, 1.0F, 1.0F, 1.0F, 1.0F);

        // 2. Vertical Rainbow Hue Bar (6 smooth segments)
        drawSolidRect(hueX - 1, hueY - 1, hueX + hueW + 1, hueY + hueH + 1, 0.7F, 0.7F, 0.7F, 0.9F);
        float[][] hueStops = {
                {1.0F, 0.0F, 0.0F},
                {1.0F, 1.0F, 0.0F},
                {0.0F, 1.0F, 0.0F},
                {0.0F, 1.0F, 1.0F},
                {0.0F, 0.0F, 1.0F},
                {1.0F, 0.0F, 1.0F},
                {1.0F, 0.0F, 0.0F}
        };
        float segH = hueH / 6.0F;
        for (int s = 0; s < 6; s++) {
            float y0 = hueY + s * segH;
            float y1 = (s == 5) ? (hueY + hueH) : (hueY + (s + 1) * segH);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(hueStops[s][0], hueStops[s][1], hueStops[s][2], 1.0F);
            GL11.glVertex2f(hueX, y0);
            GL11.glColor4f(hueStops[s + 1][0], hueStops[s + 1][1], hueStops[s + 1][2], 1.0F);
            GL11.glVertex2f(hueX, y1);
            GL11.glColor4f(hueStops[s + 1][0], hueStops[s + 1][1], hueStops[s + 1][2], 1.0F);
            GL11.glVertex2f(hueX + hueW, y1);
            GL11.glColor4f(hueStops[s][0], hueStops[s][1], hueStops[s][2], 1.0F);
            GL11.glVertex2f(hueX + hueW, y0);
            GL11.glEnd();
        }
        float hueCursorY = hueY + this.pickerHue * hueH;
        drawSolidRect(hueX - 2, hueCursorY - 2, hueX + hueW + 2, hueCursorY + 2, 1.0F, 1.0F, 1.0F, 1.0F);
        drawSolidRect(hueX - 1, hueCursorY - 1, hueX + hueW + 1, hueCursorY + 1, 0.0F, 0.0F, 0.0F, 1.0F);

        // 3. Opacity Bar with subtle checkerboard underneath
        drawSolidRect(alphaX - 1, alphaY - 1, alphaX + alphaW + 1, alphaY + alphaH + 1, 0.7F, 0.7F, 0.7F, 0.9F);
        int cell = 8;
        for (int cx = alphaX; cx < alphaX + alphaW; cx += cell) {
            for (int cy = alphaY; cy < alphaY + alphaH; cy += cell) {
                boolean light = (((cx - alphaX) / cell) + ((cy - alphaY) / cell)) % 2 == 0;
                float shade = light ? 0.55F : 0.32F;
                drawSolidRect(cx, cy, Math.min(cx + cell, alphaX + alphaW), Math.min(cy + cell, alphaY + alphaH), shade, shade, shade, 1.0F);
            }
        }
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(cr, cg, cb, 0.0F); GL11.glVertex2f(alphaX, alphaY);
        GL11.glColor4f(cr, cg, cb, 0.0F); GL11.glVertex2f(alphaX, alphaY + alphaH);
        GL11.glColor4f(cr, cg, cb, 1.0F); GL11.glVertex2f(alphaX + alphaW, alphaY + alphaH);
        GL11.glColor4f(cr, cg, cb, 1.0F); GL11.glVertex2f(alphaX + alphaW, alphaY);
        GL11.glEnd();

        float alphaCursorX = alphaX + (config.nametagBgOpacity / 100.0F) * alphaW;
        drawSolidRect(alphaCursorX - 2, alphaY - 2, alphaCursorX + 2, alphaY + alphaH + 2, 1.0F, 1.0F, 1.0F, 1.0F);
        drawSolidRect(alphaCursorX - 1, alphaY - 1, alphaCursorX + 1, alphaY + alphaH + 1, 0.0F, 0.0F, 0.0F, 1.0F);

        // 4. Live Nametag Preview Container + Colored Tag Background
        drawSolidRect(previewX - 1, previewY - 1, previewX + previewW + 1, previewY + previewH + 1, 0.45F, 0.45F, 0.45F, 0.85F);
        drawSolidRect(previewX, previewY, previewX + previewW, previewY + previewH, 0.14F, 0.16F, 0.20F, 0.90F);

        String sampleName = "Thewier";
        int sampleW = HitJumpMod.getTextWidth(sampleName);
        int tagCenterX = previewX + previewW / 2;
        int tagTextY = previewY + (previewH - 8) / 2;
        drawSolidRect(
                tagCenterX - sampleW / 2 - 4,
                tagTextY - 2,
                tagCenterX + sampleW / 2 + 4,
                tagTextY + 10,
                cr, cg, cb, ca
        );

        GL11.glPopAttrib();

        // Draw preview text + Opacity & Hex readout
        this.drawCenteredString(this.textRenderer, "Opacity: " + config.nametagBgOpacity + "%", alphaX + alphaW / 2, alphaY + 4, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, sampleName, tagCenterX, tagTextY, 0xFFFFFF);

        String hexInfo = String.format("Color: #%06X  |  Alpha: %d%%", config.nametagBgColor, config.nametagBgOpacity);
        this.drawCenteredString(this.textRenderer, hexInfo, x + 100, previewY + previewH + 6, 0xD0D0D0);
    }

    private void drawSolidRect(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y0);
        GL11.glEnd();
    }

    private float clamp01(float v) {
        if (v < 0.0F) return 0.0F;
        if (v > 1.0F) return 1.0F;
        return v;
    }

    private void handleKeyRebinding(HitJumpConfig config) {
        if (this.waitingForMenuKey) {
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
                    this.waitingForMenuKey = false;
                    if (this.keybindButton != null) {
                        this.keybindButton.message = getKeybindLabel(config);
                    }
                    break;
                } else if (!down) {
                    this.keysHeldAtClick[key] = false;
                }
            }
        } else if (this.waitingForGodPlaceKey) {
            boolean captured = false;

            for (int key = 1; key < 256; key++) {
                boolean down;
                try {
                    down = Keyboard.isKeyDown(key);
                } catch (Exception ignored) {
                    continue;
                }
                if (down && !this.keysHeldAtClick[key]) {
                    if (key != Keyboard.KEY_ESCAPE) {
                        config.godPlaceKeyCode = key;
                        HitJumpConfig.save();
                    }
                    this.waitingForGodPlaceKey = false;
                    if (this.godPlaceKeyButton != null) {
                        this.godPlaceKeyButton.message = getGodPlaceKeyLabel(config);
                    }
                    captured = true;
                    break;
                } else if (!down) {
                    this.keysHeldAtClick[key] = false;
                }
            }

            if (!captured) {
                int btnCount = 0;
                try {
                    btnCount = Math.min(Mouse.getButtonCount(), this.mouseHeldAtClick.length);
                } catch (Exception ignored) {
                }
                for (int b = 1; b < btnCount; b++) {
                    boolean down;
                    try {
                        down = Mouse.isButtonDown(b);
                    } catch (Exception ignored) {
                        continue;
                    }
                    if (down && !this.mouseHeldAtClick[b]) {
                        config.godPlaceKeyCode = -100 + b;
                        HitJumpConfig.save();
                        this.waitingForGodPlaceKey = false;
                        if (this.godPlaceKeyButton != null) {
                            this.godPlaceKeyButton.message = getGodPlaceKeyLabel(config);
                        }
                        break;
                    } else if (!down) {
                        this.mouseHeldAtClick[b] = false;
                    }
                }
            }
        }
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

    private String getGodPlaceKeyLabel(HitJumpConfig config) {
        if (this.waitingForGodPlaceKey) {
            return "GodPlace Key: > Press key/btn <";
        }
        return "GodPlace Key: " + formatBindName(config.godPlaceKeyCode);
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

    private String getWallHitboxLabel(HitJumpConfig config) {
        return "Wall Hitboxes: " + (config.wallHitboxesEnabled ? "ON" : "OFF");
    }

    private String getHitboxTargetLabel(HitJumpConfig config) {
        if (config.hitboxTargetMode == 1) return "Show: Players Only";
        if (config.hitboxTargetMode == 2) return "Show: All Entities";
        return "Show: Players & Mobs";
    }

    private String getWallNametagLabel(HitJumpConfig config) {
        return "Wall Nametags: " + (config.wallNametagsEnabled ? "ON" : "OFF");
    }

    private String getKeybindLabel(HitJumpConfig config) {
        if (this.waitingForMenuKey) {
            return "Menu Key: > Press any key <";
        }
        return "Menu Key: " + formatBindName(config.menuKeyCode);
    }

    private String formatBindName(int code) {
        if (code < 0) {
            int btn = code + 100;
            if (btn == 0) return "Left Click";
            if (btn == 1) return "Right Click";
            if (btn == 2) return "Middle Mouse";
            return "Mouse " + (btn + 1);
        }
        String keyName = Keyboard.getKeyName(code);
        return keyName != null ? keyName : ("Key " + code);
    }
}
