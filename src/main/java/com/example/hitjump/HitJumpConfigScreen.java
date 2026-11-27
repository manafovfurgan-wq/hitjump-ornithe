package com.example.hitjump;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class HitJumpConfigScreen extends Screen {
    private static final String HEADER_TITLE = "Tomato Cream | Main menu";
    private static final String[] TAB_NAMES = {
            "Combat", "Movement", "Building", "Visuals", "Animations", "Audio", "Settings"
    };

    private static final int TAB_COMBAT = 0;
    private static final int TAB_MOVEMENT = 1;
    private static final int TAB_BUILDING = 2;
    private static final int TAB_VISUALS = 3;
    private static final int TAB_ANIMATIONS = 4;
    private static final int TAB_AUDIO = 5;
    private static final int TAB_SETTINGS = 6;

    // Bind targets: 0=none, 1=MenuKey, 2=MenuMod, 3=GodPlaceKey, 4=ToggleSprintKey
    private int waitingBindTarget = 0;
    private final boolean[] keysHeldAtClick = new boolean[256];
    private final boolean[] mouseHeldAtClick = new boolean[16];

    private final Screen parent;
    private int activeTab = TAB_COMBAT;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // Mouse / drag state
    private boolean wasLeftDown = false;
    private boolean leftJustClicked = false;
    private boolean leftDown = false;
    private int activeSliderId = -1;
    private int activePickerDrag = 0; // pickerId * 10 + part (1=SV, 2=Hue, 3=Alpha)
    private boolean draggingScrollbar = false;

    // Viewport bounds (computed each frame)
    private int viewX, viewY, viewW, viewH;
    private boolean mouseInViewport = false;

    // HSB state for the 3 inline color pickers
    private final float[][] pickerHsb = new float[3][3]; // 0=Invis, 1=Nametag, 2=HitColor

    public HitJumpConfigScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        this.waitingBindTarget = 0;
        this.activeSliderId = -1;
        this.activePickerDrag = 0;
        this.draggingScrollbar = false;
        syncPickersFromConfig();
    }

    private void syncPickersFromConfig() {
        HitJumpConfig c = HitJumpConfig.getInstance();
        rgbToPicker(0, c.invisHitboxColor);
        rgbToPicker(1, c.nametagBgColor);
        rgbToPicker(2, c.customHitColor);
    }

    private void rgbToPicker(int idx, int rgb) {
        Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, pickerHsb[idx]);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        HitJumpConfig config = HitJumpConfig.getInstance();

        handleKeyRebinding(config);

        boolean curLeft = false;
        try {
            curLeft = Mouse.isButtonDown(0);
        } catch (Exception ignored) {
        }
        this.leftJustClicked = curLeft && !this.wasLeftDown;
        this.leftDown = curLeft;

        if (!this.leftDown) {
            if (this.activeSliderId != -1 || this.activePickerDrag != 0) {
                HitJumpConfig.save();
            }
            this.activeSliderId = -1;
            this.activePickerDrag = 0;
            this.draggingScrollbar = false;
        }

        // Scroll input (mouse wheel + arrow/page keys when not rebinding)
        if (this.waitingBindTarget == 0) {
            try {
                int wheel = Mouse.getDWheel();
                if (wheel != 0) {
                    this.scrollOffset -= (wheel > 0 ? 22 : -22);
                }
            } catch (Exception ignored) {
            }
            if (isKeySafe(Keyboard.KEY_DOWN)) this.scrollOffset += 5;
            if (isKeySafe(Keyboard.KEY_UP)) this.scrollOffset -= 5;
            if (isKeySafe(Keyboard.KEY_NEXT)) this.scrollOffset += 14;
            if (isKeySafe(Keyboard.KEY_PRIOR)) this.scrollOffset -= 14;
        }
        this.scrollOffset = clampInt(this.scrollOffset, 0, this.maxScroll);

        // Panel geometry
        int panelW = Math.min(540, this.width - 20);
        int panelH = Math.min(310, this.height - 20);
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        int headerH = 26;
        int sideW = 112;

        this.viewX = panelX + sideW + 8;
        this.viewY = panelY + headerH + 6;
        this.viewW = panelW - sideW - 22;
        this.viewH = panelH - headerH - 12;
        this.mouseInViewport = mouseX >= viewX && mouseX <= viewX + viewW
                && mouseY >= viewY && mouseY <= viewY + viewH;

        this.renderBackground();

        // Main window frame
        fillRect(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0.30F, 0.32F, 0.36F, 0.95F);
        fillRect(panelX, panelY, panelX + panelW, panelY + panelH, 0.08F, 0.09F, 0.11F, 0.96F);

        // Header bar
        fillRect(panelX, panelY, panelX + panelW, panelY + headerH, 0.11F, 0.12F, 0.15F, 1.0F);
        fillRect(panelX, panelY + headerH - 1, panelX + panelW, panelY + headerH, 0.25F, 0.27F, 0.32F, 1.0F);
        drawLeftText(HEADER_TITLE, panelX + 10, panelY + 9, 0xFFFFFF);

        // Top-right Close ("X") button
        int closeX = panelX + panelW - 24;
        int closeY = panelY + 4;
        boolean hoverClose = isInside(mouseX, mouseY, closeX, closeY, 18, 18);
        fillRect(closeX, closeY, closeX + 18, closeY + 18,
                hoverClose ? 0.65F : 0.20F, hoverClose ? 0.18F : 0.21F, hoverClose ? 0.18F : 0.25F, 1.0F);
        this.drawCenteredString(this.textRenderer, "X", closeX + 9, closeY + 5, 0xFFFFFF);
        if (this.leftJustClicked && hoverClose) {
            HitJumpConfig.save();
            this.minecraft.openScreen(this.parent);
            this.wasLeftDown = curLeft;
            return;
        }

        // Left Sidebar background
        fillRect(panelX, panelY + headerH, panelX + sideW, panelY + panelH, 0.06F, 0.07F, 0.09F, 1.0F);
        fillRect(panelX + sideW, panelY + headerH, panelX + sideW + 1, panelY + panelH, 0.22F, 0.24F, 0.28F, 1.0F);

        // Sidebar category tabs
        int tabY = panelY + headerH + 8;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean selected = (i == this.activeTab);
            boolean hovered = isInside(mouseX, mouseY, panelX + 6, tabY, sideW - 12, 22);
            if (selected) {
                fillRect(panelX + 6, tabY, panelX + sideW - 6, tabY + 22, 0.22F, 0.26F, 0.34F, 1.0F);
                fillRect(panelX + 6, tabY, panelX + 9, tabY + 22, 0.90F, 0.92F, 0.98F, 1.0F);
            } else if (hovered) {
                fillRect(panelX + 6, tabY, panelX + sideW - 6, tabY + 22, 0.14F, 0.15F, 0.19F, 1.0F);
            }
            drawLeftText(TAB_NAMES[i], panelX + 15, tabY + 7, selected ? 0xFFFFFF : 0xB8BDC7);
            if (this.leftJustClicked && hovered && !selected) {
                this.activeTab = i;
                this.scrollOffset = 0;
                this.waitingBindTarget = 0;
            }
            tabY += 25;
        }

        // Pinned Save & Done button at bottom of sidebar
        int saveY = panelY + panelH - 28;
        boolean hoverSave = isInside(mouseX, mouseY, panelX + 8, saveY, sideW - 16, 20);
        fillRect(panelX + 8, saveY, panelX + sideW - 8, saveY + 20,
                hoverSave ? 0.22F : 0.15F, hoverSave ? 0.48F : 0.36F, hoverSave ? 0.28F : 0.20F, 1.0F);
        this.drawCenteredString(this.textRenderer, "Save & Done", panelX + sideW / 2, saveY + 6, 0xFFFFFF);
        if (this.leftJustClicked && hoverSave) {
            HitJumpConfig.save();
            this.minecraft.openScreen(this.parent);
            this.wasLeftDown = curLeft;
            return;
        }

        // Scrollable content area with OpenGL Scissor clip
        enableScissor(this.viewX, this.viewY, this.viewW, this.viewH);
        int startY = this.viewY - this.scrollOffset;
        int endY = renderActiveTabContent(config, this.viewX, startY, this.viewW, mouseX, mouseY);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        int contentH = endY - startY;
        this.maxScroll = Math.max(0, contentH - this.viewH);
        this.scrollOffset = clampInt(this.scrollOffset, 0, this.maxScroll);

        // Scrollbar on the right edge of the viewport
        renderScrollbar(panelX + panelW - 10, this.viewY, 6, this.viewH, contentH, mouseX, mouseY);

        this.wasLeftDown = curLeft;
    }

    private int renderActiveTabContent(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        switch (this.activeTab) {
            case TAB_COMBAT:
                return renderCombatTab(c, x, y, w, mx, my);
            case TAB_MOVEMENT:
                return renderMovementTab(c, x, y, w, mx, my);
            case TAB_BUILDING:
                return renderBuildingTab(c, x, y, w, mx, my);
            case TAB_VISUALS:
                return renderVisualsTab(c, x, y, w, mx, my);
            case TAB_ANIMATIONS:
                return renderAnimationsTab(c, x, y, w, mx, my);
            case TAB_AUDIO:
                return renderAudioTab(c, x, y, w, mx, my);
            case TAB_SETTINGS:
                return renderSettingsTab(c, x, y, w, mx, my);
            default:
                return y;
        }
    }

    private int renderCombatTab(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        // Card 1: HitJump
        int cardH = 114;
        drawCard(x, y, w, cardH, "HitJump (Jump Reset)");
        int iy = y + 24;
        c.enabled = drawToggleRow(x + 10, iy, w - 20, "HitJump Module", c.enabled, true, mx, my);
        iy += 22;
        if (drawCycleRow(x + 10, iy, w - 20, "Trigger Target",
                c.hitJumpPlayersOnly ? "Players Only" : "All Knockback", mx, my)) {
            c.hitJumpPlayersOnly = !c.hitJumpPlayersOnly;
        }
        iy += 22;
        c.jumpFrequency = drawSliderRow(101, x + 10, iy, w - 20, "Jump Frequency",
                c.jumpFrequency, 0, 100, c.jumpFrequency + "%", mx, my);
        iy += 22;
        c.latencyTicks = drawSliderRow(102, x + 10, iy, w - 20, "Jump Latency",
                c.latencyTicks, 0, 10, c.latencyTicks + "t (" + (c.latencyTicks * 50) + "ms)", mx, my);
        y += cardH + 8;

        // Card 2: NoHitDelay
        cardH = 70;
        drawCard(x, y, w, cardH, "NoHitDelay (1.8.9 Miss Fix)");
        iy = y + 24;
        c.noHitDelayEnabled = drawToggleRow(x + 10, iy, w - 20, "NoHitDelay Module", c.noHitDelayEnabled, true, mx, my);
        iy += 22;
        c.hitDelayTicks = drawSliderRow(103, x + 10, iy, w - 20, "Hit Delay Cap",
                c.hitDelayTicks, 0, 10, c.hitDelayTicks + "t (" + (c.hitDelayTicks * 50) + "ms)", mx, my);
        return y + cardH + 6;
    }

    private int renderMovementTab(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        // Card 1: NoJumpDelay
        int cardH = 70;
        drawCard(x, y, w, cardH, "NoJumpDelay");
        int iy = y + 24;
        c.noJumpDelayEnabled = drawToggleRow(x + 10, iy, w - 20, "NoJumpDelay Module", c.noJumpDelayEnabled, true, mx, my);
        iy += 22;
        c.jumpDelayTicks = drawSliderRow(201, x + 10, iy, w - 20, "Jump Cooldown Cap",
                c.jumpDelayTicks, 0, 10, c.jumpDelayTicks + "t (" + (c.jumpDelayTicks * 50) + "ms)", mx, my);
        y += cardH + 8;

        // Card 2: Toggle Sprint
        cardH = 92;
        drawCard(x, y, w, cardH, "Toggle Sprint");
        iy = y + 24;
        c.toggleSprintEnabled = drawToggleRow(x + 10, iy, w - 20, "Toggle Sprint Module", c.toggleSprintEnabled, true, mx, my);
        iy += 22;
        c.toggleSprintActive = drawToggleRow(x + 10, iy, w - 20, "Sprint Lock Active", c.toggleSprintActive, false, mx, my);
        iy += 22;
        drawBindRow(4, x + 10, iy, w - 20, "Toggle Sprint Hotkey", c.toggleSprintKeyCode, mx, my);
        y += cardH + 8;

        // Card 3: NoSlow
        cardH = 114;
        drawCard(x, y, w, cardH, "NoSlow (NoSlowDown)");
        iy = y + 24;
        c.noSlowEnabled = drawToggleRow(x + 10, iy, w - 20, "NoSlow Module", c.noSlowEnabled, true, mx, my);
        iy += 22;
        c.noSlowItems = drawToggleRow(x + 10, iy, w - 20, "Eating / Bow / Sword Block", c.noSlowItems, false, mx, my);
        iy += 22;
        c.noSlowCobwebs = drawToggleRow(x + 10, iy, w - 20, "Cobwebs", c.noSlowCobwebs, false, mx, my);
        iy += 22;
        c.noSlowSoulSand = drawToggleRow(x + 10, iy, w - 20, "Soul Sand", c.noSlowSoulSand, false, mx, my);
        return y + cardH + 6;
    }

    private int renderBuildingTab(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        int cardH = 92;
        drawCard(x, y, w, cardH, "GodPlace (Hold Key FastPlace)");
        int iy = y + 24;
        c.godPlaceEnabled = drawToggleRow(x + 10, iy, w - 20, "GodPlace Module", c.godPlaceEnabled, true, mx, my);
        iy += 22;
        drawBindRow(3, x + 10, iy, w - 20, "GodPlace Hold Key", c.godPlaceKeyCode, mx, my);
        iy += 22;
        c.godPlaceDelayTicks = drawSliderRow(301, x + 10, iy, w - 20, "Placement Delay",
                c.godPlaceDelayTicks, 0, 3, c.godPlaceDelayTicks + "t (" + (c.godPlaceDelayTicks * 50) + "ms)", mx, my);
        return y + cardH + 6;
    }

    private int renderVisualsTab(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        // Card 1: Hitboxes & Separated Eye Line
        int cardH = 114;
        drawCard(x, y, w, cardH, "Wall Hitboxes & Eye Line");
        int iy = y + 24;
        c.wallHitboxesEnabled = drawToggleRow(x + 10, iy, w - 20, "Hitbox Wireframe", c.wallHitboxesEnabled, true, mx, my);
        iy += 22;
        c.hitboxEyeLineEnabled = drawToggleRow(x + 10, iy, w - 20, "Eye-Level Line (Separate)", c.hitboxEyeLineEnabled, true, mx, my);
        iy += 22;
        String targetStr = (c.hitboxTargetMode == 1) ? "Players Only"
                : (c.hitboxTargetMode == 2 ? "All Entities" : "Players & Mobs");
        if (drawCycleRow(x + 10, iy, w - 20, "Entity Filter", targetStr, mx, my)) {
            c.hitboxTargetMode = (c.hitboxTargetMode + 1) % 3;
        }
        iy += 22;
        c.hitboxRange = drawSliderRow(401, x + 10, iy, w - 20, "Hitbox Render Range",
                c.hitboxRange, 8, 256, c.hitboxRange + "m", mx, my);
        y += cardH + 8;

        // Card 2: Invisible Entity Hitbox Color
        cardH = 136;
        drawCard(x, y, w, cardH, "Invisible Entity Hitbox Color");
        iy = y + 24;
        c.invisHitboxEnabled = drawToggleRow(x + 10, iy, w - 20, "Highlight Invisible Entities", c.invisHitboxEnabled, true, mx, my);
        iy += 24;
        c.invisHitboxColor = drawInlineColorPicker(0, x + 10, iy, w - 20, c.invisHitboxColor, -1, null, mx, my)[0];
        y += cardH + 8;

        // Card 3: Wall Nametags, Distance Tag & Background Picker
        cardH = 224;
        drawCard(x, y, w, cardH, "Wall Nametags & Distance Tag");
        iy = y + 24;
        c.wallNametagsEnabled = drawToggleRow(x + 10, iy, w - 20, "Through-Wall Nametags", c.wallNametagsEnabled, true, mx, my);
        iy += 22;
        c.nametagRange = drawSliderRow(402, x + 10, iy, w - 20, "Nametag Render Range",
                c.nametagRange, 16, 256, c.nametagRange + "m", mx, my);
        iy += 22;
        c.showDistanceTag = drawToggleRow(x + 10, iy, w - 20, "Distance Above Nametag", c.showDistanceTag, true, mx, my);
        iy += 22;
        if (drawCycleRow(x + 10, iy, w - 20, "Distance Unit",
                c.distanceUnitBlocks ? "B (Blocks)" : "M (Meters)", mx, my)) {
            c.distanceUnitBlocks = !c.distanceUnitBlocks;
        }
        iy += 24;
        int[] tagCol = drawInlineColorPicker(1, x + 10, iy, w - 20, c.nametagBgColor, c.nametagBgOpacity, "Thewier", mx, my);
        c.nametagBgColor = tagCol[0];
        c.nametagBgOpacity = tagCol[1];
        y += cardH + 8;

        // Card 4: Armor Enchants & TNT Timer
        cardH = 70;
        drawCard(x, y, w, cardH, "Combat Info Overlays");
        iy = y + 24;
        c.armorEnchantInfoEnabled = drawToggleRow(x + 10, iy, w - 20, "Armor Enchantment Info", c.armorEnchantInfoEnabled, true, mx, my);
        iy += 22;
        c.tntTimerEnabled = drawToggleRow(x + 10, iy, w - 20, "TNT Fuse Countdown Tag", c.tntTimerEnabled, true, mx, my);
        y += cardH + 8;

        // Card 5: Custom Hit Color
        cardH = 158;
        drawCard(x, y, w, cardH, "Custom Hit Color");
        iy = y + 24;
        c.customHitColorEnabled = drawToggleRow(x + 10, iy, w - 20, "Custom Hit Color", c.customHitColorEnabled, true, mx, my);
        iy += 24;
        int[] hitCol = drawInlineColorPicker(2, x + 10, iy, w - 20, c.customHitColor, c.customHitOpacity, null, mx, my);
        c.customHitColor = hitCol[0];
        c.customHitOpacity = hitCol[1];
        y += cardH + 8;

        // Card 6: Interface & Camera
        cardH = 70;
        drawCard(x, y, w, cardH, "Interface & Camera");
        iy = y + 24;
        c.centerInventoryEnabled = drawToggleRow(x + 10, iy, w - 20, "Center Inventory (No Potion Shift)", c.centerInventoryEnabled, true, mx, my);
        iy += 22;
        c.parallaxFixEnabled = drawToggleRow(x + 10, iy, w - 20, "Parallax Fix (MC-1846)", c.parallaxFixEnabled, true, mx, my);
        return y + cardH + 6;
    }

    private int renderAnimationsTab(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        int cardH = 114;
        drawCard(x, y, w, cardH, "1.7 & Viewmodel Animations");
        int iy = y + 24;
        c.noSwapDipEnabled = drawToggleRow(x + 10, iy, w - 20, "No Item-Swap Dip Animation", c.noSwapDipEnabled, true, mx, my);
        iy += 22;
        c.blockhitAnimEnabled = drawToggleRow(x + 10, iy, w - 20, "1.7 Blockhit Sword Animation", c.blockhitAnimEnabled, true, mx, my);
        iy += 22;
        c.smoothSneakEnabled = drawToggleRow(x + 10, iy, w - 20, "1.7 Smooth Sneak", c.smoothSneakEnabled, true, mx, my);
        iy += 22;
        c.headFixEnabled = drawToggleRow(x + 10, iy, w - 20, "1.7 Head Position Fix", c.headFixEnabled, true, mx, my);
        return y + cardH + 6;
    }

    private int renderAudioTab(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        int cardH = 48;
        drawCard(x, y, w, cardH, "Custom Sound Mixer");
        c.soundMixerEnabled = drawToggleRow(x + 10, y + 24, w - 20, "Sound Mixer Master", c.soundMixerEnabled, true, mx, my);
        y += cardH + 8;

        y = renderSoundCard(600, "Hit & Hurt Sounds", x, y, w, mx, my,
                c.soundHitEnabled, c.soundHitMuted, c.soundHitVolume, c.soundHitPitch,
                new SoundConsumer() {
                    @Override
                    public void apply(boolean en, boolean mu, int vol, int pit) {
                        c.soundHitEnabled = en; c.soundHitMuted = mu; c.soundHitVolume = vol; c.soundHitPitch = pit;
                    }
                });

        y = renderSoundCard(610, "Explosions (TNT / Fireballs)", x, y, w, mx, my,
                c.soundExplosionEnabled, c.soundExplosionMuted, c.soundExplosionVolume, c.soundExplosionPitch,
                new SoundConsumer() {
                    @Override
                    public void apply(boolean en, boolean mu, int vol, int pit) {
                        c.soundExplosionEnabled = en; c.soundExplosionMuted = mu; c.soundExplosionVolume = vol; c.soundExplosionPitch = pit;
                    }
                });

        y = renderSoundCard(620, "Lightning & Thunder", x, y, w, mx, my,
                c.soundLightningEnabled, c.soundLightningMuted, c.soundLightningVolume, c.soundLightningPitch,
                new SoundConsumer() {
                    @Override
                    public void apply(boolean en, boolean mu, int vol, int pit) {
                        c.soundLightningEnabled = en; c.soundLightningMuted = mu; c.soundLightningVolume = vol; c.soundLightningPitch = pit;
                    }
                });

        y = renderSoundCard(630, "Weather & Rain", x, y, w, mx, my,
                c.soundRainEnabled, c.soundRainMuted, c.soundRainVolume, c.soundRainPitch,
                new SoundConsumer() {
                    @Override
                    public void apply(boolean en, boolean mu, int vol, int pit) {
                        c.soundRainEnabled = en; c.soundRainMuted = mu; c.soundRainVolume = vol; c.soundRainPitch = pit;
                    }
                });

        y = renderSoundCard(640, "Nether Portals", x, y, w, mx, my,
                c.soundPortalEnabled, c.soundPortalMuted, c.soundPortalVolume, c.soundPortalPitch,
                new SoundConsumer() {
                    @Override
                    public void apply(boolean en, boolean mu, int vol, int pit) {
                        c.soundPortalEnabled = en; c.soundPortalMuted = mu; c.soundPortalVolume = vol; c.soundPortalPitch = pit;
                    }
                });

        y = renderSoundCard(650, "Ghast & Fireball Screams", x, y, w, mx, my,
                c.soundGhastEnabled, c.soundGhastMuted, c.soundGhastVolume, c.soundGhastPitch,
                new SoundConsumer() {
                    @Override
                    public void apply(boolean en, boolean mu, int vol, int pit) {
                        c.soundGhastEnabled = en; c.soundGhastMuted = mu; c.soundGhastVolume = vol; c.soundGhastPitch = pit;
                    }
                });

        return y;
    }

    private interface SoundConsumer {
        void apply(boolean enabled, boolean muted, int volume, int pitch);
    }

    private int renderSoundCard(
            int baseId, String title, int x, int y, int w, int mx, int my,
            boolean enabled, boolean muted, int volume, int pitch, SoundConsumer out
    ) {
        int cardH = 114;
        drawCard(x, y, w, cardH, title);
        int iy = y + 24;
        enabled = drawToggleRow(x + 10, iy, w - 20, "Custom Mix", enabled, true, mx, my);
        iy += 22;
        if (drawCycleRow(x + 10, iy, w - 20, "Mute Audio", muted ? "MUTED" : "Active", mx, my)) {
            muted = !muted;
        }
        iy += 22;
        volume = drawSliderRow(baseId + 1, x + 10, iy, w - 20, "Volume", volume, 0, 200, volume + "%", mx, my);
        iy += 22;
        pitch = drawSliderRow(baseId + 2, x + 10, iy, w - 20, "Pitch",
                pitch, 50, 200, String.format("%.2fx", pitch / 100.0F), mx, my);
        out.apply(enabled, muted, volume, pitch);
        return y + cardH + 8;
    }

    private int renderSettingsTab(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        int cardH = 92;
        drawCard(x, y, w, cardH, "Menu Open Protection");
        int iy = y + 24;
        String modeLabel = (c.menuTriggerMode == 0) ? "Double-Tap"
                : (c.menuTriggerMode == 1 ? "Modifier + Key" : "Single Press");
        if (drawCycleRow(x + 10, iy, w - 20, "Trigger Mode", modeLabel, mx, my)) {
            c.menuTriggerMode = (c.menuTriggerMode + 1) % 3;
            HitJumpConfig.save();
        }
        iy += 22;
        drawBindRow(1, x + 10, iy, w - 20, "Menu Key", c.menuKeyCode, mx, my);
        iy += 22;
        drawBindRow(2, x + 10, iy, w - 20, "Modifier Key (Combo Mode)", c.menuModifierKey, mx, my);
        y += cardH + 8;

        cardH = 54;
        drawCard(x, y, w, cardH, "Configuration");
        int btnW = (w - 28) / 2;
        int by = y + 24;
        boolean hoverReset = this.mouseInViewport && isInside(mx, my, x + 10, by, btnW, 20);
        fillRect(x + 10, by, x + 10 + btnW, by + 20,
                hoverReset ? 0.55F : 0.36F, hoverReset ? 0.18F : 0.14F, hoverReset ? 0.18F : 0.14F, 1.0F);
        this.drawCenteredString(this.textRenderer, "Reset All Defaults", x + 10 + btnW / 2, by + 6, 0xFFFFFF);
        if (this.leftJustClicked && hoverReset) {
            c.resetDefaults();
            HitJumpConfig.save();
            syncPickersFromConfig();
        }

        int sx = x + 18 + btnW;
        boolean hoverSave = this.mouseInViewport && isInside(mx, my, sx, by, btnW, 20);
        fillRect(sx, by, sx + btnW, by + 20,
                hoverSave ? 0.22F : 0.15F, hoverSave ? 0.48F : 0.36F, hoverSave ? 0.28F : 0.20F, 1.0F);
        this.drawCenteredString(this.textRenderer, "Save Config Now", sx + btnW / 2, by + 6, 0xFFFFFF);
        if (this.leftJustClicked && hoverSave) {
            HitJumpConfig.save();
        }
        return y + cardH + 6;
    }

    // ---------- Reusable UI Primitives ----------

    private void drawCard(int x, int y, int w, int h, String title) {
        fillRect(x, y, x + w, y + h, 0.12F, 0.13F, 0.16F, 0.95F);
        fillRect(x, y, x + w, y + 18, 0.15F, 0.17F, 0.21F, 1.0F);
        fillRect(x, y + 18, x + w, y + 19, 0.24F, 0.26F, 0.32F, 1.0F);
        drawLeftText(title, x + 10, y + 5, 0xFFFFFF);
    }

    private boolean drawToggleRow(int x, int y, int w, String label, boolean state, boolean useEnableText, int mx, int my) {
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        int pillW = 72;
        int px = x + w - pillW;
        boolean hover = this.mouseInViewport && isInside(mx, my, px, y, pillW, 18);
        if (state) {
            fillRect(px, y, px + pillW, y + 18,
                    hover ? 0.20F : 0.14F, hover ? 0.56F : 0.45F, hover ? 0.28F : 0.22F, 1.0F);
        } else {
            fillRect(px, y, px + pillW, y + 18,
                    hover ? 0.36F : 0.24F, hover ? 0.18F : 0.15F, hover ? 0.18F : 0.16F, 1.0F);
        }
        String text = useEnableText ? (state ? "Enabled" : "Disabled") : (state ? "ON" : "OFF");
        this.drawCenteredString(this.textRenderer, text, px + pillW / 2, y + 5, 0xFFFFFF);
        if (this.leftJustClicked && hover) {
            HitJumpConfig.save();
            return !state;
        }
        return state;
    }

    private boolean drawCycleRow(int x, int y, int w, String label, String value, int mx, int my) {
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        int btnW = 104;
        int bx = x + w - btnW;
        boolean hover = this.mouseInViewport && isInside(mx, my, bx, y, btnW, 18);
        fillRect(bx, y, bx + btnW, y + 18,
                hover ? 0.24F : 0.18F, hover ? 0.27F : 0.20F, hover ? 0.34F : 0.26F, 1.0F);
        this.drawCenteredString(this.textRenderer, value, bx + btnW / 2, y + 5, 0xFFFFFF);
        return this.leftJustClicked && hover;
    }

    private void drawBindRow(int bindTarget, int x, int y, int w, String label, int keyCode, int mx, int my) {
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        int btnW = 114;
        int bx = x + w - btnW;
        boolean waiting = (this.waitingBindTarget == bindTarget);
        boolean hover = this.mouseInViewport && isInside(mx, my, bx, y, btnW, 18);
        fillRect(bx, y, bx + btnW, y + 18,
                waiting ? 0.38F : (hover ? 0.24F : 0.18F),
                waiting ? 0.32F : (hover ? 0.27F : 0.20F),
                waiting ? 0.14F : (hover ? 0.34F : 0.26F), 1.0F);
        String text = waiting ? "> Press Key <" : formatBindName(keyCode);
        this.drawCenteredString(this.textRenderer, text, bx + btnW / 2, y + 5, 0xFFFFFF);
        if (this.leftJustClicked && hover) {
            if (waiting) {
                this.waitingBindTarget = 0;
            } else {
                this.waitingBindTarget = bindTarget;
                snapshotInputs();
            }
        }
    }

    private int drawSliderRow(int id, int x, int y, int w, String label, int val, int min, int max, String readout, int mx, int my) {
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        int barW = 148;
        int bx = x + w - barW;
        int by = y + 2;
        int bh = 14;

        boolean hover = this.mouseInViewport && isInside(mx, my, bx, by, barW, bh);
        if (this.leftJustClicked && hover) {
            this.activeSliderId = id;
        }
        if (this.leftDown && this.activeSliderId == id) {
            float ratio = clamp01((mx - bx) / (float) barW);
            val = min + Math.round(ratio * (max - min));
        }

        float pct = (max > min) ? ((val - min) / (float) (max - min)) : 0.0F;
        fillRect(bx, by, bx + barW, by + bh, 0.07F, 0.08F, 0.10F, 1.0F);
        fillRect(bx, by, bx + (int) (barW * pct), by + bh, 0.30F, 0.40F, 0.58F, 1.0F);
        this.drawCenteredString(this.textRenderer, readout, bx + barW / 2, by + 3, 0xFFFFFF);
        return clampInt(val, min, max);
    }

    private int[] drawInlineColorPicker(
            int pickerId, int x, int y, int w,
            int curRgb, int opacityOrNeg, String previewName, int mx, int my
    ) {
        int svW = 156;
        int svH = 74;
        int hueX = x + svW + 6;
        int hueW = 16;
        int infoX = hueX + hueW + 10;
        int infoW = Math.max(80, w - (infoX - x));
        boolean hasAlpha = (opacityOrNeg >= 0);
        int alphaY = y + svH + 6;
        int alphaW = svW + 6 + hueW;
        int alphaH = 14;

        int dragSv = pickerId * 10 + 1;
        int dragHue = pickerId * 10 + 2;
        int dragAlpha = pickerId * 10 + 3;

        if (this.leftJustClicked && this.mouseInViewport) {
            if (isInside(mx, my, x, y, svW, svH)) {
                this.activePickerDrag = dragSv;
            } else if (isInside(mx, my, hueX, y, hueW, svH)) {
                this.activePickerDrag = dragHue;
                if (pickerHsb[pickerId][2] < 0.08F) pickerHsb[pickerId][2] = 0.85F;
                if (pickerHsb[pickerId][1] < 0.08F) pickerHsb[pickerId][1] = 0.85F;
            } else if (hasAlpha && isInside(mx, my, x, alphaY, alphaW, alphaH)) {
                this.activePickerDrag = dragAlpha;
            }
        }

        if (this.leftDown) {
            if (this.activePickerDrag == dragSv) {
                pickerHsb[pickerId][1] = clamp01((mx - x) / (float) svW);
                pickerHsb[pickerId][2] = 1.0F - clamp01((my - y) / (float) svH);
                curRgb = Color.HSBtoRGB(pickerHsb[pickerId][0], pickerHsb[pickerId][1], pickerHsb[pickerId][2]) & 0xFFFFFF;
            } else if (this.activePickerDrag == dragHue) {
                pickerHsb[pickerId][0] = clamp01((my - y) / (float) svH);
                curRgb = Color.HSBtoRGB(pickerHsb[pickerId][0], pickerHsb[pickerId][1], pickerHsb[pickerId][2]) & 0xFFFFFF;
            } else if (hasAlpha && this.activePickerDrag == dragAlpha) {
                opacityOrNeg = Math.round(clamp01((mx - x) / (float) alphaW) * 100.0F);
            }
        }

        int pureHue = Color.HSBtoRGB(pickerHsb[pickerId][0], 1.0F, 1.0F);
        float hr = ((pureHue >> 16) & 0xFF) / 255.0F;
        float hg = ((pureHue >> 8) & 0xFF) / 255.0F;
        float hb = (pureHue & 0xFF) / 255.0F;

        float cr = ((curRgb >> 16) & 0xFF) / 255.0F;
        float cg = ((curRgb >> 8) & 0xFF) / 255.0F;
        float cb = (curRgb & 0xFF) / 255.0F;
        float ca = hasAlpha ? (opacityOrNeg / 100.0F) : 1.0F;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);

        // SV Box
        drawQuad(x - 1, y - 1, x + svW + 1, y + svH + 1, 0.5F, 0.5F, 0.55F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(1, 1, 1, 1); GL11.glVertex2f(x, y);
        GL11.glColor4f(1, 1, 1, 1); GL11.glVertex2f(x, y + svH);
        GL11.glColor4f(hr, hg, hb, 1); GL11.glVertex2f(x + svW, y + svH);
        GL11.glColor4f(hr, hg, hb, 1); GL11.glVertex2f(x + svW, y);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0, 0, 0, 0); GL11.glVertex2f(x, y);
        GL11.glColor4f(0, 0, 0, 1); GL11.glVertex2f(x, y + svH);
        GL11.glColor4f(0, 0, 0, 1); GL11.glVertex2f(x + svW, y + svH);
        GL11.glColor4f(0, 0, 0, 0); GL11.glVertex2f(x + svW, y);
        GL11.glEnd();

        float cx = x + pickerHsb[pickerId][1] * svW;
        float cy = y + (1.0F - pickerHsb[pickerId][2]) * svH;
        drawQuad(cx - 3, cy - 3, cx + 3, cy + 3, 0, 0, 0, 1);
        drawQuad(cx - 2, cy - 2, cx + 2, cy + 2, 1, 1, 1, 1);

        // Hue Bar
        drawQuad(hueX - 1, y - 1, hueX + hueW + 1, y + svH + 1, 0.5F, 0.5F, 0.55F, 1.0F);
        float[][] stops = {{1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 0, 0}};
        float segH = svH / 6.0F;
        for (int s = 0; s < 6; s++) {
            float y0 = y + s * segH;
            float y1 = (s == 5) ? (y + svH) : (y + (s + 1) * segH);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(stops[s][0], stops[s][1], stops[s][2], 1); GL11.glVertex2f(hueX, y0);
            GL11.glColor4f(stops[s + 1][0], stops[s + 1][1], stops[s + 1][2], 1); GL11.glVertex2f(hueX, y1);
            GL11.glColor4f(stops[s + 1][0], stops[s + 1][1], stops[s + 1][2], 1); GL11.glVertex2f(hueX + hueW, y1);
            GL11.glColor4f(stops[s][0], stops[s][1], stops[s][2], 1); GL11.glVertex2f(hueX + hueW, y0);
            GL11.glEnd();
        }
        float hy = y + pickerHsb[pickerId][0] * svH;
        drawQuad(hueX - 2, hy - 2, hueX + hueW + 2, hy + 2, 1, 1, 1, 1);
        drawQuad(hueX - 1, hy - 1, hueX + hueW + 1, hy + 1, 0, 0, 0, 1);

        // Opacity Bar
        if (hasAlpha) {
            drawQuad(x - 1, alphaY - 1, x + alphaW + 1, alphaY + alphaH + 1, 0.5F, 0.5F, 0.55F, 1.0F);
            drawQuad(x, alphaY, x + alphaW, alphaY + alphaH, 0.25F, 0.25F, 0.28F, 1.0F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(cr, cg, cb, 0); GL11.glVertex2f(x, alphaY);
            GL11.glColor4f(cr, cg, cb, 0); GL11.glVertex2f(x, alphaY + alphaH);
            GL11.glColor4f(cr, cg, cb, 1); GL11.glVertex2f(x + alphaW, alphaY + alphaH);
            GL11.glColor4f(cr, cg, cb, 1); GL11.glVertex2f(x + alphaW, alphaY);
            GL11.glEnd();
            float ax = x + ca * alphaW;
            drawQuad(ax - 2, alphaY - 1, ax + 2, alphaY + alphaH + 1, 1, 1, 1, 1);
        }

        // Right-side Swatch / Live Preview
        drawQuad(infoX, y, infoX + infoW, y + 36, 0.16F, 0.18F, 0.22F, 1.0F);
        if (previewName != null) {
            int tw = HitJumpMod.getTextWidth(previewName);
            int midX = infoX + infoW / 2;
            drawQuad(midX - tw / 2 - 4, y + 11, midX + tw / 2 + 4, y + 25, cr, cg, cb, ca);
        } else {
            drawQuad(infoX + 4, y + 4, infoX + infoW - 4, y + 32, cr, cg, cb, ca);
        }

        GL11.glPopAttrib();

        if (previewName != null) {
            this.drawCenteredString(this.textRenderer, previewName, infoX + infoW / 2, y + 14, 0xFFFFFF);
        }
        if (hasAlpha) {
            this.drawCenteredString(this.textRenderer, "Opacity: " + opacityOrNeg + "%", x + alphaW / 2, alphaY + 3, 0xFFFFFF);
        }
        drawLeftText(String.format("Hex: #%06X", curRgb), infoX, y + 44, 0xD0D4DC);
        if (hasAlpha) {
            drawLeftText("Alpha: " + opacityOrNeg + "%", infoX, y + 58, 0xA8B0BC);
        }
        return new int[]{curRgb, opacityOrNeg};
    }

    private void renderScrollbar(int x, int y, int w, int h, int contentH, int mx, int my) {
        fillRect(x, y, x + w, y + h, 0.06F, 0.07F, 0.09F, 1.0F);
        if (this.maxScroll <= 0 || contentH <= 0) {
            return;
        }
        int thumbH = Math.max(24, (int) ((h / (float) contentH) * h));
        int trackRange = Math.max(1, h - thumbH);
        int thumbY = y + (int) ((this.scrollOffset / (float) this.maxScroll) * trackRange);

        boolean hover = isInside(mx, my, x - 2, y, w + 4, h);
        if (this.leftJustClicked && hover) {
            this.draggingScrollbar = true;
        }
        if (this.leftDown && this.draggingScrollbar) {
            float ratio = clamp01((my - y - thumbH / 2.0F) / (float) trackRange);
            this.scrollOffset = Math.round(ratio * this.maxScroll);
            thumbY = y + (int) (ratio * trackRange);
        }
        fillRect(x, thumbY, x + w, thumbY + thumbH,
                (hover || this.draggingScrollbar) ? 0.55F : 0.36F,
                (hover || this.draggingScrollbar) ? 0.60F : 0.40F,
                (hover || this.draggingScrollbar) ? 0.70F : 0.48F, 1.0F);
    }

    private void handleKeyRebinding(HitJumpConfig config) {
        if (this.waitingBindTarget == 0) {
            return;
        }
        boolean allowMouse = (this.waitingBindTarget == 3 || this.waitingBindTarget == 4);

        for (int key = 1; key < 256; key++) {
            boolean down = isKeySafe(key);
            if (down && !this.keysHeldAtClick[key]) {
                if (key != Keyboard.KEY_ESCAPE) {
                    applyBoundCode(config, key);
                }
                this.waitingBindTarget = 0;
                return;
            } else if (!down) {
                this.keysHeldAtClick[key] = false;
            }
        }

        if (allowMouse) {
            int btnCount = 0;
            try {
                btnCount = Math.min(Mouse.getButtonCount(), this.mouseHeldAtClick.length);
            } catch (Exception ignored) {
            }
            for (int b = 1; b < btnCount; b++) {
                boolean down = false;
                try {
                    down = Mouse.isButtonDown(b);
                } catch (Exception ignored) {
                }
                if (down && !this.mouseHeldAtClick[b]) {
                    applyBoundCode(config, -100 + b);
                    this.waitingBindTarget = 0;
                    return;
                } else if (!down) {
                    this.mouseHeldAtClick[b] = false;
                }
            }
        }
    }

    private void applyBoundCode(HitJumpConfig c, int code) {
        if (this.waitingBindTarget == 1) c.menuKeyCode = code;
        else if (this.waitingBindTarget == 2) c.menuModifierKey = code;
        else if (this.waitingBindTarget == 3) c.godPlaceKeyCode = code;
        else if (this.waitingBindTarget == 4) c.toggleSprintKeyCode = code;
        HitJumpConfig.save();
    }

    private void snapshotInputs() {
        for (int k = 1; k < 256; k++) {
            this.keysHeldAtClick[k] = isKeySafe(k);
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

    private void enableScissor(int x, int y, int w, int h) {
        double scaleX = Display.getWidth() / (double) Math.max(1, this.width);
        double scaleY = Display.getHeight() / (double) Math.max(1, this.height);
        int sx = (int) Math.floor(x * scaleX);
        int sw = (int) Math.ceil(w * scaleX);
        int sy = (int) Math.floor((this.height - (y + h)) * scaleY);
        int sh = (int) Math.ceil(h * scaleY);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(Math.max(0, sx), Math.max(0, sy), Math.max(0, sw), Math.max(0, sh));
    }

    private void drawLeftText(String text, int x, int y, int color) {
        int w = HitJumpMod.getTextWidth(text);
        this.drawCenteredString(this.textRenderer, text, x + w / 2, y, color);
    }

    private void fillRect(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        drawQuad(x0, y0, x1, y1, r, g, b, a);
        GL11.glPopAttrib();
    }

    private void drawQuad(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y0);
        GL11.glEnd();
    }

    private boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean isKeySafe(int key) {
        try {
            return Keyboard.isKeyDown(key);
        } catch (Exception ignored) {
            return false;
        }
    }

    private float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private int clampInt(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
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

    @Override
    public void removed() {
        this.waitingBindTarget = 0;
        HitJumpConfig.save();
    }
}
