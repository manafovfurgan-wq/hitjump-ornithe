package com.example.hitjump;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class HitJumpConfigScreen extends Screen {
    private static final String HEADER_TITLE = "Tomato Cream | Main menu";
    private static final String[] TAB_NAMES = {
            "Combat", "Movement", "Building", "Visuals", "Animations", "HUD", "Audio", "Settings"
    };

    private static final int TAB_COMBAT = 0;
    private static final int TAB_MOVEMENT = 1;
    private static final int TAB_BUILDING = 2;
    private static final int TAB_VISUALS = 3;
    private static final int TAB_ANIMATIONS = 4;
    private static final int TAB_HUD = 5;
    private static final int TAB_AUDIO = 6;
    private static final int TAB_SETTINGS = 7;

    // Bind targets: 0=none, 1=MenuKey, 2=MenuMod, 3=GodPlaceKey, 4=ToggleSprintKey
    private int waitingBindTarget = 0;
    private final boolean[] keysHeldAtClick = new boolean[256];
    private final boolean[] mouseHeldAtClick = new boolean[16];

    // Text input focus: 0=none, 1=Search, 2=HUD Header, 3=New Parody Line
    private int focusedInput = 0;
    private String searchQuery = "";
    private String newParodyLineDraft = "";
    private final boolean[] typingKeyPrev = new boolean[256];
    private long backspaceDownMs = 0L;
    private long lastBackspaceRepeatMs = 0L;

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

    // Viewport bounds
    private int viewX, viewY, viewW, viewH;
    private boolean mouseInViewport = false;

    // Deferred hover tooltip (drawn above scissor clip)
    private String hoveredTooltip = null;
    private int tooltipX = 0;
    private int tooltipY = 0;

    // HSB state for the 5 inline color pickers:
    // 0=Invis, 1=Nametag, 2=HitColor, 3=BlockOutline, 4=ParodyHUD
    private final float[][] pickerHsb = new float[5][3];

    public HitJumpConfigScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        this.waitingBindTarget = 0;
        this.focusedInput = 0;
        this.activeSliderId = -1;
        this.activePickerDrag = 0;
        this.draggingScrollbar = false;
        snapshotTypingKeys();
        syncPickersFromConfig();
    }

    private void syncPickersFromConfig() {
        HitJumpConfig c = HitJumpConfig.getInstance();
        rgbToPicker(0, c.invisHitboxColor);
        rgbToPicker(1, c.nametagBgColor);
        rgbToPicker(2, c.customHitColor);
        rgbToPicker(3, c.blockOutlineColor);
        rgbToPicker(4, c.parodyTextColor);
    }

    private void rgbToPicker(int idx, int rgb) {
        Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, pickerHsb[idx]);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        HitJumpConfig config = HitJumpConfig.getInstance();

        if (this.waitingBindTarget != 0) {
            handleKeyRebinding(config);
        } else {
            handleTextTyping(config);
        }

        boolean curLeft = false;
        try {
            curLeft = Mouse.isButtonDown(0);
        } catch (Throwable ignored) {
        }
        this.leftJustClicked = curLeft && !this.wasLeftDown;
        this.leftDown = curLeft;
        this.hoveredTooltip = null;

        if (!this.leftDown) {
            if (this.activeSliderId != -1 || this.activePickerDrag != 0) {
                HitJumpConfig.save();
            }
            this.activeSliderId = -1;
            this.activePickerDrag = 0;
            this.draggingScrollbar = false;
        }

        // Scroll input
        if (this.waitingBindTarget == 0) {
            try {
                int wheel = Mouse.getDWheel();
                if (wheel != 0) {
                    this.scrollOffset -= (wheel > 0 ? 22 : -22);
                }
            } catch (Throwable ignored) {
            }
            if (this.focusedInput == 0) {
                if (isKeySafe(Keyboard.KEY_DOWN)) this.scrollOffset += 5;
                if (isKeySafe(Keyboard.KEY_UP)) this.scrollOffset -= 5;
                if (isKeySafe(Keyboard.KEY_NEXT)) this.scrollOffset += 14;
                if (isKeySafe(Keyboard.KEY_PRIOR)) this.scrollOffset -= 14;
            }
        }
        this.scrollOffset = clampInt(this.scrollOffset, 0, this.maxScroll);

        // Panel geometry
        int panelW = Math.min(552, this.width - 18);
        int panelH = Math.min(316, this.height - 16);
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        int headerH = 26;
        int sideW = 108;

        this.viewX = panelX + sideW + 8;
        this.viewY = panelY + headerH + 6;
        this.viewW = panelW - sideW - 22;
        this.viewH = panelH - headerH - 12;
        this.mouseInViewport = mouseX >= viewX && mouseX <= viewX + viewW
                && mouseY >= viewY && mouseY <= viewY + viewH;

        this.renderBackground();

        // Live Parody HUD preview behind the menu so edits appear immediately
        ParodyHudRenderer.renderPreview(this.width, this.height);

        // Main window frame
        fillRect(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0.30F, 0.32F, 0.36F, 0.95F);
        fillRect(panelX, panelY, panelX + panelW, panelY + panelH, 0.08F, 0.09F, 0.11F, 0.96F);

        // Header bar
        fillRect(panelX, panelY, panelX + panelW, panelY + headerH, 0.11F, 0.12F, 0.15F, 1.0F);
        fillRect(panelX, panelY + headerH - 1, panelX + panelW, panelY + headerH, 0.25F, 0.27F, 0.32F, 1.0F);
        drawLeftText(HEADER_TITLE, panelX + 10, panelY + 9, 0xFFFFFF);

        // Lightweight Search Bar in top header
        int closeX = panelX + panelW - 24;
        int closeY = panelY + 4;
        int searchW = Math.min(180, Math.max(100, panelW - 250));
        int searchX = closeX - searchW - 8;
        int searchY = panelY + 4;
        boolean hoverSearch = isInside(mouseX, mouseY, searchX, searchY, searchW, 18);
        boolean searchFocused = (this.focusedInput == 1);
        fillRect(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + 19,
                searchFocused ? 0.48F : 0.24F, searchFocused ? 0.62F : 0.26F, searchFocused ? 0.85F : 0.32F, 1.0F);
        fillRect(searchX, searchY, searchX + searchW, searchY + 18, 0.06F, 0.07F, 0.09F, 1.0F);

        boolean hasSearch = !this.searchQuery.isEmpty();
        int clearSearchX = searchX + searchW - 15;
        boolean hoverClearSearch = hasSearch && isInside(mouseX, mouseY, clearSearchX, searchY, 15, 18);
        if (this.leftJustClicked) {
            if (hoverClearSearch) {
                this.searchQuery = "";
                this.scrollOffset = 0;
                this.focusedInput = 1;
            } else if (hoverSearch) {
                this.focusedInput = 1;
                this.waitingBindTarget = 0;
            } else if (this.focusedInput == 1) {
                this.focusedInput = 0;
            }
        }
        String searchDisp = hasSearch
                ? trimToWidth(this.searchQuery, searchW - 22) + (searchFocused && blinkCursor() ? "_" : "")
                : (searchFocused && blinkCursor() ? "_" : "Search options...");
        drawLeftText(searchDisp, searchX + 5, searchY + 5, hasSearch ? 0xFFFFFF : 0x7A8290);
        if (hasSearch) {
            this.drawCenteredString(this.textRenderer, "x", clearSearchX + 7, searchY + 5,
                    hoverClearSearch ? 0xFF7070 : 0xA8B0BC);
        }

        // Top-right Close ("X") button
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

        // Left Sidebar
        fillRect(panelX, panelY + headerH, panelX + sideW, panelY + panelH, 0.06F, 0.07F, 0.09F, 1.0F);
        fillRect(panelX + sideW, panelY + headerH, panelX + sideW + 1, panelY + panelH, 0.22F, 0.24F, 0.28F, 1.0F);

        int tabY = panelY + headerH + 6;
        boolean searching = !this.searchQuery.trim().isEmpty();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean selected = !searching && (i == this.activeTab);
            boolean hovered = isInside(mouseX, mouseY, panelX + 6, tabY, sideW - 12, 20);
            if (selected) {
                fillRect(panelX + 6, tabY, panelX + sideW - 6, tabY + 20, 0.22F, 0.26F, 0.34F, 1.0F);
                fillRect(panelX + 6, tabY, panelX + 9, tabY + 20, 0.90F, 0.92F, 0.98F, 1.0F);
            } else if (hovered) {
                fillRect(panelX + 6, tabY, panelX + sideW - 6, tabY + 20, 0.14F, 0.15F, 0.19F, 1.0F);
            }
            drawLeftText(TAB_NAMES[i], panelX + 14, tabY + 6, selected ? 0xFFFFFF : 0xB8BDC7);
            if (this.leftJustClicked && hovered) {
                this.activeTab = i;
                this.searchQuery = "";
                this.scrollOffset = 0;
                this.waitingBindTarget = 0;
                this.focusedInput = 0;
            }
            tabY += 23;
        }

        // Pinned Save & Done button at bottom of sidebar
        int saveY = panelY + panelH - 26;
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
        int endY;
        if (searching) {
            String filter = this.searchQuery.trim().toLowerCase();
            int curY = startY;
            curY = renderCombatTab(config, this.viewX, curY, this.viewW, mouseX, mouseY, filter);
            curY = renderMovementTab(config, this.viewX, curY, this.viewW, mouseX, mouseY, filter);
            curY = renderBuildingTab(config, this.viewX, curY, this.viewW, mouseX, mouseY, filter);
            curY = renderVisualsTab(config, this.viewX, curY, this.viewW, mouseX, mouseY, filter);
            curY = renderAnimationsTab(config, this.viewX, curY, this.viewW, mouseX, mouseY, filter);
            curY = renderHudTab(config, this.viewX, curY, this.viewW, mouseX, mouseY, filter);
            curY = renderAudioTab(config, this.viewX, curY, this.viewW, mouseX, mouseY, filter);
            curY = renderSettingsTab(config, this.viewX, curY, this.viewW, mouseX, mouseY, filter);
            if (curY == startY) {
                this.drawCenteredString(this.textRenderer,
                        "No options match \"" + this.searchQuery.trim() + "\"",
                        this.viewX + this.viewW / 2, startY + 24, 0x9AA2B0);
                curY = startY + 46;
            }
            endY = curY;
        } else {
            endY = renderActiveTabContent(config, this.viewX, startY, this.viewW, mouseX, mouseY);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        int contentH = endY - startY;
        this.maxScroll = Math.max(0, contentH - this.viewH);
        this.scrollOffset = clampInt(this.scrollOffset, 0, this.maxScroll);

        renderScrollbar(panelX + panelW - 10, this.viewY, 6, this.viewH, contentH, mouseX, mouseY);

        // Render floating info tooltip on top of everything
        if (this.hoveredTooltip != null) {
            drawInfoTooltip(this.hoveredTooltip, this.tooltipX, this.tooltipY);
        }

        this.wasLeftDown = curLeft;
    }

    private int renderActiveTabContent(HitJumpConfig c, int x, int y, int w, int mx, int my) {
        switch (this.activeTab) {
            case TAB_COMBAT: return renderCombatTab(c, x, y, w, mx, my, "");
            case TAB_MOVEMENT: return renderMovementTab(c, x, y, w, mx, my, "");
            case TAB_BUILDING: return renderBuildingTab(c, x, y, w, mx, my, "");
            case TAB_VISUALS: return renderVisualsTab(c, x, y, w, mx, my, "");
            case TAB_ANIMATIONS: return renderAnimationsTab(c, x, y, w, mx, my, "");
            case TAB_HUD: return renderHudTab(c, x, y, w, mx, my, "");
            case TAB_AUDIO: return renderAudioTab(c, x, y, w, mx, my, "");
            case TAB_SETTINGS: return renderSettingsTab(c, x, y, w, mx, my, "");
            default: return y;
        }
    }

    private int renderCombatTab(HitJumpConfig c, int x, int y, int w, int mx, int my, String filter) {
        if (matchesFilter(filter, "combat hitjump jump reset knockback frequency latency players")) {
            int cardH = 114;
            drawCard(x, y, w, cardH, "HitJump (Jump Reset)");
            int iy = y + 24;
            c.enabled = drawToggleRow(x + 10, iy, w - 20, "HitJump Module", c.enabled, true,
                    "Automatically jumps on combat knockback hits to reduce knockback. Ignores fall and fire damage.", mx, my);
            iy += 22;
            if (drawCycleRow(x + 10, iy, w - 20, "Trigger Target",
                    c.hitJumpPlayersOnly ? "Players Only" : "All Knockback",
                    "Players Only jumps near enemy players; All Knockback also triggers on mobs and projectiles.", mx, my)) {
                c.hitJumpPlayersOnly = !c.hitJumpPlayersOnly;
            }
            iy += 22;
            c.jumpFrequency = drawSliderRow(101, x + 10, iy, w - 20, "Jump Frequency",
                    c.jumpFrequency, 0, 100, c.jumpFrequency + "%",
                    "Chance (0-100%) to trigger a jump reset when you take knockback.", mx, my);
            iy += 22;
            c.latencyTicks = drawSliderRow(102, x + 10, iy, w - 20, "Jump Latency",
                    c.latencyTicks, 0, 10, c.latencyTicks + "t (" + (c.latencyTicks * 50) + "ms)",
                    "Optional delay in ticks before jumping after a hit (1 tick = 50 ms).", mx, my);
            y += cardH + 8;
        }

        if (matchesFilter(filter, "combat nohitdelay hit delay miss penalty click 1.8.9")) {
            int cardH = 70;
            drawCard(x, y, w, cardH, "NoHitDelay (1.8.9 Miss Fix)");
            int iy = y + 24;
            c.noHitDelayEnabled = drawToggleRow(x + 10, iy, w - 20, "NoHitDelay Module", c.noHitDelayEnabled, true,
                    "Removes 1.8.9's 10-tick left-click freeze after missing a swing while keeping GUI-close protection.", mx, my);
            iy += 22;
            c.hitDelayTicks = drawSliderRow(103, x + 10, iy, w - 20, "Hit Delay Cap",
                    c.hitDelayTicks, 0, 10, c.hitDelayTicks + "t (" + (c.hitDelayTicks * 50) + "ms)",
                    "Maximum remaining miss penalty in ticks (0t = instant 1.7-style clicking).", mx, my);
            y += cardH + 8;
        }
        return y;
    }

    private int renderMovementTab(HitJumpConfig c, int x, int y, int w, int mx, int my, String filter) {
        if (matchesFilter(filter, "movement nojumpdelay jump delay cooldown headhitter ceiling")) {
            int cardH = 70;
            drawCard(x, y, w, cardH, "NoJumpDelay");
            int iy = y + 24;
            c.noJumpDelayEnabled = drawToggleRow(x + 10, iy, w - 20, "NoJumpDelay Module", c.noJumpDelayEnabled, true,
                    "Removes vanilla's 10-tick pause between consecutive jumps while holding Spacebar.", mx, my);
            iy += 22;
            c.jumpDelayTicks = drawSliderRow(201, x + 10, iy, w - 20, "Jump Cooldown Cap",
                    c.jumpDelayTicks, 0, 10, c.jumpDelayTicks + "t (" + (c.jumpDelayTicks * 50) + "ms)",
                    "Maximum delay in ticks between held jumps (0t = instant bounce).", mx, my);
            y += cardH + 8;
        }

        if (matchesFilter(filter, "movement togglesprint toggle sprint run hotkey keybind")) {
            int cardH = 92;
            drawCard(x, y, w, cardH, "Toggle Sprint");
            int iy = y + 24;
            c.toggleSprintEnabled = drawToggleRow(x + 10, iy, w - 20, "Toggle Sprint Module", c.toggleSprintEnabled, true,
                    "Automatically holds Sprint for you whenever you move forward.", mx, my);
            iy += 22;
            c.toggleSprintActive = drawToggleRow(x + 10, iy, w - 20, "Sprint Lock Active", c.toggleSprintActive, false,
                    "Current sprint lock state. Can also be flipped in-game with your Toggle Sprint Hotkey.", mx, my);
            iy += 22;
            drawBindRow(4, x + 10, iy, w - 20, "Toggle Sprint Hotkey", c.toggleSprintKeyCode,
                    "Key or mouse button to toggle Sprint Lock ON/OFF during gameplay.", mx, my);
            y += cardH + 8;
        }

        if (matchesFilter(filter, "movement noslow noslowdown eating bow sword block cobwebs soul sand")) {
            int cardH = 114;
            drawCard(x, y, w, cardH, "NoSlow (NoSlowDown)");
            int iy = y + 24;
            c.noSlowEnabled = drawToggleRow(x + 10, iy, w - 20, "NoSlow Module", c.noSlowEnabled, true,
                    "Prevents movement slowdown. Warning: modifies movement speed and may flag multiplayer anti-cheats.", mx, my);
            iy += 22;
            c.noSlowItems = drawToggleRow(x + 10, iy, w - 20, "Eating / Bow / Sword Block", c.noSlowItems, false,
                    "Keeps normal walking speed while eating, drinking, drawing a bow, or blocking.", mx, my);
            iy += 22;
            c.noSlowCobwebs = drawToggleRow(x + 10, iy, w - 20, "Cobwebs", c.noSlowCobwebs, false,
                    "Removes cobweb movement slowdown.", mx, my);
            iy += 22;
            c.noSlowSoulSand = drawToggleRow(x + 10, iy, w - 20, "Soul Sand", c.noSlowSoulSand, false,
                    "Removes soul sand walking slowdown.", mx, my);
            y += cardH + 8;
        }
        return y;
    }

    private int renderBuildingTab(HitJumpConfig c, int x, int y, int w, int mx, int my, String filter) {
        if (matchesFilter(filter, "building godplace fastplace right click middle mouse place block delay")) {
            int cardH = 92;
            drawCard(x, y, w, cardH, "GodPlace (Hold Key FastPlace)");
            int iy = y + 24;
            c.godPlaceEnabled = drawToggleRow(x + 10, iy, w - 20, "GodPlace Module", c.godPlaceEnabled, true,
                    "Bypasses the 4-tick block placement cooldown only while holding your dedicated GodPlace key.", mx, my);
            iy += 22;
            drawBindRow(3, x + 10, iy, w - 20, "GodPlace Hold Key", c.godPlaceKeyCode,
                    "Hold this key/mouse button to fast-place blocks while normal Right Click stays vanilla.", mx, my);
            iy += 22;
            c.godPlaceDelayTicks = drawSliderRow(301, x + 10, iy, w - 20, "Placement Delay",
                    c.godPlaceDelayTicks, 0, 3, c.godPlaceDelayTicks + "t (" + (c.godPlaceDelayTicks * 50) + "ms)",
                    "Cooldown between placements while held (0t = 20 blocks/sec, 3t = vanilla speed).", mx, my);
            y += cardH + 8;
        }
        return y;
    }

    private int renderVisualsTab(HitJumpConfig c, int x, int y, int w, int mx, int my, String filter) {
        if (matchesFilter(filter, "visuals hitbox hitboxes wall see through walls eye line entity filter range esp")) {
            int cardH = 136;
            drawCard(x, y, w, cardH, "Entity Hitboxes & Eye Line");
            int iy = y + 24;
            c.wallHitboxesEnabled = drawToggleRow(x + 10, iy, w - 20, "Hitbox Wireframe", c.wallHitboxesEnabled, true,
                    "Draws crisp wireframe hitboxes around entities within your chosen render range.", mx, my);
            iy += 22;
            c.hitboxThroughWalls = drawToggleRow(x + 10, iy, w - 20, "See Through Walls", c.hitboxThroughWalls, false,
                    "When ON, hitboxes and eye lines show through walls and terrain. Turn OFF for normal line-of-sight only.", mx, my);
            iy += 22;
            c.hitboxEyeLineEnabled = drawToggleRow(x + 10, iy, w - 20, "Eye-Level Line (Separate)", c.hitboxEyeLineEnabled, true,
                    "Independent toggle for the red eye-height band; works with or without the outer hitbox.", mx, my);
            iy += 22;
            String targetStr = (c.hitboxTargetMode == 1) ? "Players Only"
                    : (c.hitboxTargetMode == 2 ? "All Entities" : "Players & Mobs");
            if (drawCycleRow(x + 10, iy, w - 20, "Entity Filter", targetStr,
                    "Choose whether hitboxes render on Players & Mobs, Players Only, or All Entities.", mx, my)) {
                c.hitboxTargetMode = (c.hitboxTargetMode + 1) % 3;
            }
            iy += 22;
            c.hitboxRange = drawSliderRow(401, x + 10, iy, w - 20, "Hitbox Render Range",
                    c.hitboxRange, 8, 256, c.hitboxRange + "m",
                    "Maximum distance in blocks/meters to render entity hitboxes.", mx, my);
            y += cardH + 8;
        }

        if (matchesFilter(filter, "visuals invisible invis entity hitbox color picker red")) {
            int cardH = 136;
            drawCard(x, y, w, cardH, "Invisible Entity Hitbox Color");
            int iy = y + 24;
            c.invisHitboxEnabled = drawToggleRow(x + 10, iy, w - 20, "Highlight Invisible Entities", c.invisHitboxEnabled, true,
                    "Recolors hitboxes of players or mobs with Invisibility so they stand out immediately.", mx, my);
            iy += 24;
            c.invisHitboxColor = drawInlineColorPicker(0, x + 10, iy, w - 20, c.invisHitboxColor, -1, null, mx, my)[0];
            y += cardH + 8;
        }

        if (matchesFilter(filter, "visuals block outline highlight thickness width opacity color picker")) {
            int cardH = 180;
            drawCard(x, y, w, cardH, "Custom Block Outline");
            int iy = y + 24;
            c.blockOutlineEnabled = drawToggleRow(x + 10, iy, w - 20, "Custom Block Outline", c.blockOutlineEnabled, true,
                    "Draws a customizable colored outline around the block your crosshair is pointing at.", mx, my);
            iy += 22;
            c.blockOutlineWidth = drawSliderRow(403, x + 10, iy, w - 20, "Outline Thickness",
                    c.blockOutlineWidth, 5, 80, String.format("%.1fpx", c.blockOutlineWidth / 10.0F),
                    "Adjusts the line thickness of the targeted block outline (0.5px to 8.0px).", mx, my);
            iy += 24;
            int[] outCol = drawInlineColorPicker(3, x + 10, iy, w - 20, c.blockOutlineColor, c.blockOutlineOpacity, null, mx, my);
            c.blockOutlineColor = outCol[0];
            c.blockOutlineOpacity = outCol[1];
            y += cardH + 8;
        }

        if (matchesFilter(filter, "visuals nametag nametags wall distance meters blocks background color opacity")) {
            int cardH = 224;
            drawCard(x, y, w, cardH, "Wall Nametags & Distance Tag");
            int iy = y + 24;
            c.wallNametagsEnabled = drawToggleRow(x + 10, iy, w - 20, "Through-Wall Nametags", c.wallNametagsEnabled, true,
                    "Shows player and custom-named mob tags cleanly through walls and mountains.", mx, my);
            iy += 22;
            c.nametagRange = drawSliderRow(402, x + 10, iy, w - 20, "Nametag Render Range",
                    c.nametagRange, 16, 256, c.nametagRange + "m",
                    "Maximum distance to display through-wall nametags, distance tags, and TNT timers.", mx, my);
            iy += 22;
            c.showDistanceTag = drawToggleRow(x + 10, iy, w - 20, "Distance Above Nametag", c.showDistanceTag, true,
                    "Displays live distance right above each entity's nametag so you can judge depth through walls.", mx, my);
            iy += 22;
            if (drawCycleRow(x + 10, iy, w - 20, "Distance Unit",
                    c.distanceUnitBlocks ? "B (Blocks)" : "M (Meters)",
                    "Switch the distance suffix between M (Meters) and B (Blocks).", mx, my)) {
                c.distanceUnitBlocks = !c.distanceUnitBlocks;
            }
            iy += 24;
            int[] tagCol = drawInlineColorPicker(1, x + 10, iy, w - 20, c.nametagBgColor, c.nametagBgOpacity, "Thewier", mx, my);
            c.nametagBgColor = tagCol[0];
            c.nametagBgOpacity = tagCol[1];
            y += cardH + 8;
        }

        if (matchesFilter(filter, "visuals armor enchantment info protection tnt fuse countdown timer overlay")) {
            int cardH = 70;
            drawCard(x, y, w, cardH, "Combat Info Overlays");
            int iy = y + 24;
            c.armorEnchantInfoEnabled = drawToggleRow(x + 10, iy, w - 20, "Armor Enchantment Info", c.armorEnchantInfoEnabled, true,
                    "Shows equipped armor protection/enchant levels (e.g. P4) on players, mobs, and armor stands.", mx, my);
            iy += 22;
            c.tntTimerEnabled = drawToggleRow(x + 10, iy, w - 20, "TNT Fuse Countdown Tag", c.tntTimerEnabled, true,
                    "Displays a live color-coded countdown timer directly above ignited TNT.", mx, my);
            y += cardH + 8;
        }

        if (matchesFilter(filter, "visuals custom hit color damage flash tint opacity picker")) {
            int cardH = 158;
            drawCard(x, y, w, cardH, "Custom Hit Color");
            int iy = y + 24;
            c.customHitColorEnabled = drawToggleRow(x + 10, iy, w - 20, "Custom Hit Color", c.customHitColorEnabled, true,
                    "Highlights damaged players and mobs with your custom color and opacity.", mx, my);
            iy += 24;
            int[] hitCol = drawInlineColorPicker(2, x + 10, iy, w - 20, c.customHitColor, c.customHitOpacity, null, mx, my);
            c.customHitColor = hitCol[0];
            c.customHitOpacity = hitCol[1];
            y += cardH + 8;
        }

        if (matchesFilter(filter, "visuals center inventory potion shift parallax fix camera")) {
            int cardH = 70;
            drawCard(x, y, w, cardH, "Interface & Camera");
            int iy = y + 24;
            c.centerInventoryEnabled = drawToggleRow(x + 10, iy, w - 20, "Center Inventory (No Potion Shift)", c.centerInventoryEnabled, true,
                    "Keeps your inventory centered instead of pushing it right when potion effects are active.", mx, my);
            iy += 22;
            c.parallaxFixEnabled = drawToggleRow(x + 10, iy, w - 20, "Parallax Fix (MC-1846)", c.parallaxFixEnabled, true,
                    "Removes 1.8.9's -0.1 block backward first-person camera offset for true eye alignment.", mx, my);
            y += cardH + 8;
        }
        return y;
    }

    private int renderAnimationsTab(HitJumpConfig c, int x, int y, int w, int mx, int my, String filter) {
        if (matchesFilter(filter, "animations 1.7 swap dip blockhit sword smooth sneak head position fix viewmodel")) {
            int cardH = 114;
            drawCard(x, y, w, cardH, "1.7 & Viewmodel Animations");
            int iy = y + 24;
            c.noSwapDipEnabled = drawToggleRow(x + 10, iy, w - 20, "No Item-Swap Dip Animation", c.noSwapDipEnabled, true,
                    "Stops your held item from dropping down and popping up when rapidly switching hotbar slots.", mx, my);
            iy += 22;
            c.blockhitAnimEnabled = drawToggleRow(x + 10, iy, w - 20, "1.7 Blockhit Sword Animation", c.blockhitAnimEnabled, true,
                    "Restores the 1.7 visual sword swing while blocking (100% client-side, zero extra packets).", mx, my);
            iy += 22;
            c.smoothSneakEnabled = drawToggleRow(x + 10, iy, w - 20, "1.7 Smooth Sneak", c.smoothSneakEnabled, true,
                    "Smoothly animates your eye height when crouching instead of 1.8.9's instant snap.", mx, my);
            iy += 22;
            c.headFixEnabled = drawToggleRow(x + 10, iy, w - 20, "1.7 Head Position Fix", c.headFixEnabled, true,
                    "Fixes MC-67665 so your entity head yaw stays synced with your crosshair on fast flicks.", mx, my);
            y += cardH + 8;
        }
        return y;
    }

    private int renderHudTab(HitJumpConfig c, int x, int y, int w, int mx, int my, String filter) {
        if (matchesFilter(filter, "hud parody corner arraylist text position padding scale sort width")) {
            int cardH = 158;
            drawCard(x, y, w, cardH, "Parody HUD Layout & Placement");
            int iy = y + 24;
            c.parodyHudEnabled = drawToggleRow(x + 10, iy, w - 20, "Parody Corner HUD", c.parodyHudEnabled, true,
                    "Displays a 2022-style parody text list in any corner of your HUD (100% visual, respects F1).", mx, my);
            iy += 22;
            String[] corners = {"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"};
            if (drawCycleRow(x + 10, iy, w - 20, "Screen Corner", corners[c.parodyCorner],
                    "Select which corner of the screen the list anchors to (auto-aligns left/right).", mx, my)) {
                c.parodyCorner = (c.parodyCorner + 1) % 4;
                HitJumpConfig.save();
            }
            iy += 22;
            c.parodySortByWidth = drawToggleRow(x + 10, iy, w - 20, "Sort by Text Width", c.parodySortByWidth, false,
                    "Automatically orders lines from longest to shortest (or reversed on bottom corners).", mx, my);
            iy += 22;
            c.parodyOffsetX = drawSliderRow(501, x + 10, iy, w - 20, "Edge Padding X",
                    c.parodyOffsetX, 0, 150, c.parodyOffsetX + "px",
                    "Nudges the HUD list inward from the left or right edge of your screen.", mx, my);
            iy += 22;
            c.parodyOffsetY = drawSliderRow(502, x + 10, iy, w - 20, "Edge Padding Y",
                    c.parodyOffsetY, 0, 150, c.parodyOffsetY + "px",
                    "Nudges the HUD list down or up so it never overlaps scoreboards or keystrokes.", mx, my);
            iy += 22;
            c.parodyScale = drawSliderRow(503, x + 10, iy, w - 20, "Text Scale",
                    c.parodyScale, 50, 200, String.format("%.2fx", c.parodyScale / 100.0F),
                    "Scales the entire parody list from 0.50x (tiny) up to 2.00x (large).", mx, my);
            y += cardH + 8;
        }

        if (matchesFilter(filter, "hud parody style color picker chroma wave shadow header background opacity")) {
            int cardH = 246;
            drawCard(x, y, w, cardH, "Parody HUD Styling & Color");
            int iy = y + 24;
            c.parodyTextShadow = drawToggleRow(x + 10, iy, w - 20, "Text Drop Shadow", c.parodyTextShadow, false,
                    "Adds a crisp shadow behind each line so text stays readable over bright skyboxes.", mx, my);
            iy += 22;
            c.parodyChromaWave = drawToggleRow(x + 10, iy, w - 20, "Chroma Wave Effect", c.parodyChromaWave, false,
                    "Cycles a smooth rainbow wave down the list instead of using a single static color.", mx, my);
            iy += 22;
            c.parodyShowHeader = drawToggleRow(x + 10, iy, w - 20, "Show Header Title", c.parodyShowHeader, false,
                    "Displays a bold title line above the list (editable right below).", mx, my);
            iy += 22;
            drawTextInputRow(2, x + 10, iy, w - 20, "Header Text", c.parodyHeaderText, "Tomato Cream",
                    "Click to edit the bold header title shown above your parody list.", mx, my);
            iy += 22;
            c.parodyShowBackground = drawToggleRow(x + 10, iy, w - 20, "Subtle Background Bar", c.parodyShowBackground, false,
                    "Draws a translucent dark backing and thin accent bar behind each line.", mx, my);
            iy += 24;
            int[] hudCol = drawInlineColorPicker(4, x + 10, iy, w - 20, c.parodyTextColor, c.parodyBgOpacity, null, mx, my);
            c.parodyTextColor = hudCol[0];
            c.parodyBgOpacity = hudCol[1];
            y += cardH + 8;
        }

        if (matchesFilter(filter, "hud parody custom text lines presets skilltoggled worsehitreg unsafewalk reallag add")) {
            int lineCount = (c.parodyLines != null) ? c.parodyLines.size() : 0;
            int cardH = 74 + (lineCount * 22);
            drawCard(x, y, w, cardH, "Parody Lines & Custom Text Editor");
            int iy = y + 24;

            // Add Custom Text input box + "+ Add" button
            int addBtnW = 48;
            int inputW = w - 20 - addBtnW - 6;
            boolean focusNew = (this.focusedInput == 3);
            boolean hoverNew = this.mouseInViewport && isInside(mx, my, x + 10, iy, inputW, 18);
            fillRect(x + 9, iy - 1, x + 11 + inputW, iy + 19,
                    focusNew ? 0.48F : 0.24F, focusNew ? 0.62F : 0.26F, focusNew ? 0.85F : 0.32F, 1.0F);
            fillRect(x + 10, iy, x + 10 + inputW, iy + 18, 0.06F, 0.07F, 0.09F, 1.0F);
            if (this.leftJustClicked && hoverNew) {
                this.focusedInput = 3;
                this.waitingBindTarget = 0;
            }
            boolean hasDraft = !this.newParodyLineDraft.isEmpty();
            String draftDisp = hasDraft
                    ? trimToWidth(this.newParodyLineDraft, inputW - 10) + (focusNew && blinkCursor() ? "_" : "")
                    : (focusNew && blinkCursor() ? "_" : "Type custom funny text here...");
            drawLeftText(draftDisp, x + 15, iy + 5, hasDraft ? 0xFFFFFF : 0x7A8290);

            int addX = x + 10 + inputW + 6;
            boolean hoverAdd = this.mouseInViewport && isInside(mx, my, addX, iy, addBtnW, 18);
            fillRect(addX, iy, addX + addBtnW, iy + 18,
                    hoverAdd ? 0.22F : 0.16F, hoverAdd ? 0.56F : 0.44F, hoverAdd ? 0.30F : 0.24F, 1.0F);
            this.drawCenteredString(this.textRenderer, "+ Add", addX + addBtnW / 2, iy + 5, 0xFFFFFF);
            if (this.leftJustClicked && hoverAdd) {
                commitNewParodyLine(c);
            }
            iy += 24;

            // Existing lines list (Toggle + Delete per line)
            int deleteIndex = -1;
            if (c.parodyLines != null) {
                for (int i = 0; i < c.parodyLines.size(); i++) {
                    HitJumpConfig.ParodyLine line = c.parodyLines.get(i);
                    if (line == null) continue;
                    int delW = 20;
                    int togW = 42;
                    int delX = x + w - 10 - delW;
                    int togX = delX - 4 - togW;
                    int maxLabelW = togX - (x + 14);

                    drawLeftText(trimToWidth(line.text, Math.max(40, maxLabelW)), x + 10, iy + 5,
                            line.enabled ? 0xE6EAF2 : 0x7D8492);

                    boolean hoverTog = this.mouseInViewport && isInside(mx, my, togX, iy, togW, 18);
                    if (line.enabled) {
                        fillRect(togX, iy, togX + togW, iy + 18,
                                hoverTog ? 0.20F : 0.14F, hoverTog ? 0.56F : 0.45F, hoverTog ? 0.28F : 0.22F, 1.0F);
                    } else {
                        fillRect(togX, iy, togX + togW, iy + 18,
                                hoverTog ? 0.36F : 0.24F, hoverTog ? 0.18F : 0.15F, hoverTog ? 0.18F : 0.16F, 1.0F);
                    }
                    this.drawCenteredString(this.textRenderer, line.enabled ? "ON" : "OFF", togX + togW / 2, iy + 5, 0xFFFFFF);
                    if (this.leftJustClicked && hoverTog) {
                        line.enabled = !line.enabled;
                        HitJumpConfig.save();
                    }

                    boolean hoverDel = this.mouseInViewport && isInside(mx, my, delX, iy, delW, 18);
                    fillRect(delX, iy, delX + delW, iy + 18,
                            hoverDel ? 0.58F : 0.28F, hoverDel ? 0.18F : 0.14F, hoverDel ? 0.18F : 0.15F, 1.0F);
                    this.drawCenteredString(this.textRenderer, "X", delX + delW / 2, iy + 5, 0xFFFFFF);
                    if (this.leftJustClicked && hoverDel) {
                        deleteIndex = i;
                    }
                    iy += 22;
                }
            }
            if (deleteIndex >= 0 && c.parodyLines != null && deleteIndex < c.parodyLines.size()) {
                c.parodyLines.remove(deleteIndex);
                HitJumpConfig.save();
            }

            // Restore Default Presets button
            boolean hoverRestore = this.mouseInViewport && isInside(mx, my, x + 10, iy + 2, 126, 18);
            fillRect(x + 10, iy + 2, x + 136, iy + 20,
                    hoverRestore ? 0.25F : 0.18F, hoverRestore ? 0.28F : 0.20F, hoverRestore ? 0.36F : 0.26F, 1.0F);
            this.drawCenteredString(this.textRenderer, "Restore Presets", x + 73, iy + 7, 0xFFFFFF);
            if (this.leftJustClicked && hoverRestore) {
                c.parodyLines = HitJumpConfig.createDefaultParodyLines();
                HitJumpConfig.save();
            }
            y += cardH + 8;
        }
        return y;
    }

    private int renderAudioTab(HitJumpConfig c, int x, int y, int w, int mx, int my, String filter) {
        if (matchesFilter(filter, "audio sound mixer master volume pitch mute")) {
            int cardH = 48;
            drawCard(x, y, w, cardH, "Custom Sound Mixer");
            c.soundMixerEnabled = drawToggleRow(x + 10, y + 24, w - 20, "Sound Mixer Master", c.soundMixerEnabled, true,
                    "Master switch for per-category sound muting, volume scaling, and pitch control.", mx, my);
            y += cardH + 8;
        }

        if (matchesFilter(filter, "audio sound hit hurt damage mono pitch volume mute")) {
            y = renderSoundCard(600, "Hit & Hurt Sounds", x, y, w, mx, my,
                    c.soundHitEnabled, c.soundHitMuted, c.soundHitVolume, c.soundHitPitch,
                    new SoundConsumer() {
                        @Override
                        public void apply(boolean en, boolean mu, int vol, int pit) {
                            c.soundHitEnabled = en; c.soundHitMuted = mu; c.soundHitVolume = vol; c.soundHitPitch = pit;
                        }
                    });
        }

        if (matchesFilter(filter, "audio sound explosion explosions tnt fireball pitch volume mute")) {
            y = renderSoundCard(610, "Explosions (TNT / Fireballs)", x, y, w, mx, my,
                    c.soundExplosionEnabled, c.soundExplosionMuted, c.soundExplosionVolume, c.soundExplosionPitch,
                    new SoundConsumer() {
                        @Override
                        public void apply(boolean en, boolean mu, int vol, int pit) {
                            c.soundExplosionEnabled = en; c.soundExplosionMuted = mu; c.soundExplosionVolume = vol; c.soundExplosionPitch = pit;
                        }
                    });
        }

        if (matchesFilter(filter, "audio sound lightning thunder storm pitch volume mute")) {
            y = renderSoundCard(620, "Lightning & Thunder", x, y, w, mx, my,
                    c.soundLightningEnabled, c.soundLightningMuted, c.soundLightningVolume, c.soundLightningPitch,
                    new SoundConsumer() {
                        @Override
                        public void apply(boolean en, boolean mu, int vol, int pit) {
                            c.soundLightningEnabled = en; c.soundLightningMuted = mu; c.soundLightningVolume = vol; c.soundLightningPitch = pit;
                        }
                    });
        }

        if (matchesFilter(filter, "audio sound weather rain pitch volume mute")) {
            y = renderSoundCard(630, "Weather & Rain", x, y, w, mx, my,
                    c.soundRainEnabled, c.soundRainMuted, c.soundRainVolume, c.soundRainPitch,
                    new SoundConsumer() {
                        @Override
                        public void apply(boolean en, boolean mu, int vol, int pit) {
                            c.soundRainEnabled = en; c.soundRainMuted = mu; c.soundRainVolume = vol; c.soundRainPitch = pit;
                        }
                    });
        }

        if (matchesFilter(filter, "audio sound nether portal hum pitch volume mute")) {
            y = renderSoundCard(640, "Nether Portals", x, y, w, mx, my,
                    c.soundPortalEnabled, c.soundPortalMuted, c.soundPortalVolume, c.soundPortalPitch,
                    new SoundConsumer() {
                        @Override
                        public void apply(boolean en, boolean mu, int vol, int pit) {
                            c.soundPortalEnabled = en; c.soundPortalMuted = mu; c.soundPortalVolume = vol; c.soundPortalPitch = pit;
                        }
                    });
        }

        if (matchesFilter(filter, "audio sound ghast blaze fireball scream pitch volume mute")) {
            y = renderSoundCard(650, "Ghast & Fireball Screams", x, y, w, mx, my,
                    c.soundGhastEnabled, c.soundGhastMuted, c.soundGhastVolume, c.soundGhastPitch,
                    new SoundConsumer() {
                        @Override
                        public void apply(boolean en, boolean mu, int vol, int pit) {
                            c.soundGhastEnabled = en; c.soundGhastMuted = mu; c.soundGhastVolume = vol; c.soundGhastPitch = pit;
                        }
                    });
        }

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
        enabled = drawToggleRow(x + 10, iy, w - 20, "Custom Mix", enabled, true,
                "Enables custom mute, volume, and pitch overrides for " + title + ".", mx, my);
        iy += 22;
        if (drawCycleRow(x + 10, iy, w - 20, "Mute Audio", muted ? "MUTED" : "Active",
                "Silences this sound category completely when set to MUTED.", mx, my)) {
            muted = !muted;
            HitJumpConfig.save();
        }
        iy += 22;
        volume = drawSliderRow(baseId + 1, x + 10, iy, w - 20, "Volume", volume, 0, 200, volume + "%",
                "Scales loudness for this sound category from 0% to 200%.", mx, my);
        iy += 22;
        pitch = drawSliderRow(baseId + 2, x + 10, iy, w - 20, "Pitch",
                pitch, 50, 200, String.format("%.2fx", pitch / 100.0F),
                "Lowers (0.50x) or raises (2.00x) pitch to soften harsh high-pitched audio.", mx, my);
        out.apply(enabled, muted, volume, pitch);
        return y + cardH + 8;
    }

    private int renderSettingsTab(HitJumpConfig c, int x, int y, int w, int mx, int my, String filter) {
        if (matchesFilter(filter, "settings menu key trigger double tap modifier combo open protection")) {
            int cardH = 92;
            drawCard(x, y, w, cardH, "Menu Open Protection");
            int iy = y + 24;
            String modeLabel = (c.menuTriggerMode == 0) ? "Double-Tap"
                    : (c.menuTriggerMode == 1 ? "Modifier + Key" : "Single Press");
            if (drawCycleRow(x + 10, iy, w - 20, "Trigger Mode", modeLabel,
                    "Prevents accidental menu opens: require Double-Tap, Modifier + Key, or Single Press.", mx, my)) {
                c.menuTriggerMode = (c.menuTriggerMode + 1) % 3;
                HitJumpConfig.save();
            }
            iy += 22;
            drawBindRow(1, x + 10, iy, w - 20, "Menu Key", c.menuKeyCode,
                    "Primary key used to open Tomato Cream | Main menu.", mx, my);
            iy += 22;
            drawBindRow(2, x + 10, iy, w - 20, "Modifier Key (Combo)", c.menuModifierKey,
                    "Key to hold alongside Menu Key when Trigger Mode is set to Modifier + Key.", mx, my);
            y += cardH + 8;
        }

        if (matchesFilter(filter, "settings configuration reset defaults save config json")) {
            int cardH = 54;
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
            y += cardH + 8;
        }
        return y;
    }

    // ---------- Reusable UI Primitives ----------

    private void drawCard(int x, int y, int w, int h, String title) {
        fillRect(x, y, x + w, y + h, 0.12F, 0.13F, 0.16F, 0.95F);
        fillRect(x, y, x + w, y + 18, 0.15F, 0.17F, 0.21F, 1.0F);
        fillRect(x, y + 18, x + w, y + 19, 0.24F, 0.26F, 0.32F, 1.0F);
        drawLeftText(title, x + 10, y + 5, 0xFFFFFF);
    }

    private void drawInfoBadge(int labelX, int rowY, String label, int controlLeftX, String info, int mx, int my) {
        if (info == null || info.isEmpty()) {
            return;
        }
        int ix = labelX + HitJumpMod.getTextWidth(label) + 5;
        int iy = rowY + 3;
        int sz = 11;
        if (ix + sz >= controlLeftX - 4) {
            return;
        }
        boolean hover = this.mouseInViewport && isInside(mx, my, ix - 1, iy - 1, sz + 2, sz + 2);
        fillRect(ix, iy, ix + sz, iy + sz,
                hover ? 0.34F : 0.20F, hover ? 0.50F : 0.24F, hover ? 0.74F : 0.33F, 1.0F);
        this.drawCenteredString(this.textRenderer, "i", ix + 6, iy + 2, hover ? 0xFFFFFF : 0xC6D0E0);
        if (hover) {
            this.hoveredTooltip = info;
            this.tooltipX = mx + 10;
            this.tooltipY = my + 10;
        }
    }

    private boolean drawToggleRow(int x, int y, int w, String label, boolean state, boolean useEnableText, String info, int mx, int my) {
        int pillW = 72;
        int px = x + w - pillW;
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        drawInfoBadge(x, y, label, px, info, mx, my);

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

    private boolean drawCycleRow(int x, int y, int w, String label, String value, String info, int mx, int my) {
        int btnW = 104;
        int bx = x + w - btnW;
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        drawInfoBadge(x, y, label, bx, info, mx, my);

        boolean hover = this.mouseInViewport && isInside(mx, my, bx, y, btnW, 18);
        fillRect(bx, y, bx + btnW, y + 18,
                hover ? 0.24F : 0.18F, hover ? 0.27F : 0.20F, hover ? 0.34F : 0.26F, 1.0F);
        this.drawCenteredString(this.textRenderer, value, bx + btnW / 2, y + 5, 0xFFFFFF);
        return this.leftJustClicked && hover;
    }

    private void drawBindRow(int bindTarget, int x, int y, int w, String label, int keyCode, String info, int mx, int my) {
        int btnW = 114;
        int bx = x + w - btnW;
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        drawInfoBadge(x, y, label, bx, info, mx, my);

        boolean waiting = (this.waitingBindTarget == bindTarget);
        boolean hover = this.mouseInViewport && isInside(mx, my, bx, y, btnW, 18);
        fillRect(bx, y, bx + btnW, y + 18,
                waiting ? 0.38F : (hover ? 0.24F : 0.18F),
                waiting ? 0.32F : (hover ? 0.27F : 0.20F),
                waiting ? 0.14F : (hover ? 0.34F : 0.26F), 1.0F);
        String text = waiting ? "> Press Key <" : formatBindName(keyCode);
        this.drawCenteredString(this.textRenderer, text, bx + btnW / 2, y + 5, 0xFFFFFF);
        if (this.leftJustClicked && hover) {
            this.focusedInput = 0;
            if (waiting) {
                this.waitingBindTarget = 0;
            } else {
                this.waitingBindTarget = bindTarget;
                snapshotInputs();
            }
        }
    }

    private void drawTextInputRow(int focusId, int x, int y, int w, String label, String value, String placeholder, String info, int mx, int my) {
        int boxW = 148;
        int bx = x + w - boxW;
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        drawInfoBadge(x, y, label, bx, info, mx, my);

        boolean focused = (this.focusedInput == focusId);
        boolean hover = this.mouseInViewport && isInside(mx, my, bx, y, boxW, 18);
        fillRect(bx - 1, y - 1, bx + boxW + 1, y + 19,
                focused ? 0.48F : 0.24F, focused ? 0.62F : 0.26F, focused ? 0.85F : 0.32F, 1.0F);
        fillRect(bx, y, bx + boxW, y + 18, 0.06F, 0.07F, 0.09F, 1.0F);
        if (this.leftJustClicked && hover) {
            this.focusedInput = focusId;
            this.waitingBindTarget = 0;
        }
        String val = (value != null) ? value : "";
        boolean hasVal = !val.isEmpty();
        String disp = hasVal
                ? trimToWidth(val, boxW - 10) + (focused && blinkCursor() ? "_" : "")
                : (focused && blinkCursor() ? "_" : placeholder);
        drawLeftText(disp, bx + 5, y + 5, hasVal ? 0xFFFFFF : 0x7A8290);
    }

    private int drawSliderRow(int id, int x, int y, int w, String label, int val, int min, int max, String readout, String info, int mx, int my) {
        int barW = 148;
        int bx = x + w - barW;
        int by = y + 2;
        int bh = 14;
        drawLeftText(label, x, y + 5, 0xD8DCE4);
        drawInfoBadge(x, y, label, bx, info, mx, my);

        boolean hover = this.mouseInViewport && isInside(mx, my, bx, by, barW, bh);
        if (this.leftJustClicked && hover) {
            this.activeSliderId = id;
            this.focusedInput = 0;
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
                this.focusedInput = 0;
            } else if (isInside(mx, my, hueX, y, hueW, svH)) {
                this.activePickerDrag = dragHue;
                this.focusedInput = 0;
                if (pickerHsb[pickerId][2] < 0.08F) pickerHsb[pickerId][2] = 0.85F;
                if (pickerHsb[pickerId][1] < 0.08F) pickerHsb[pickerId][1] = 0.85F;
            } else if (hasAlpha && isInside(mx, my, x, alphaY, alphaW, alphaH)) {
                this.activePickerDrag = dragAlpha;
                this.focusedInput = 0;
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

        // Right-side Swatch / Preview
        drawQuad(infoX, y, infoX + infoW, y + 36, 0.16F, 0.18F, 0.22F, 1.0F);
        if (previewName != null) {
            int tw = HitJumpMod.getTextWidth(previewName);
            int midX = infoX + infoW / 2;
            drawQuad(midX - tw / 2 - 4, y + 11, midX + tw / 2 + 4, y + 25, cr, cg, cb, ca);
        } else {
            drawQuad(infoX + 4, y + 4, infoX + infoW - 4, y + 32, cr, cg, cb, hasAlpha && pickerId != 4 ? ca : 1.0F);
        }

        GL11.glPopAttrib();

        if (previewName != null) {
            this.drawCenteredString(this.textRenderer, previewName, infoX + infoW / 2, y + 14, 0xFFFFFF);
        }
        if (hasAlpha) {
            String alphaLabel = (pickerId == 4 ? "Bg Opacity: " : "Opacity: ") + opacityOrNeg + "%";
            this.drawCenteredString(this.textRenderer, alphaLabel, x + alphaW / 2, alphaY + 3, 0xFFFFFF);
        }
        drawLeftText(String.format("Hex: #%06X", curRgb), infoX, y + 44, 0xD0D4DC);
        if (hasAlpha) {
            drawLeftText((pickerId == 4 ? "Bg: " : "Alpha: ") + opacityOrNeg + "%", infoX, y + 58, 0xA8B0BC);
        }
        return new int[]{curRgb, opacityOrNeg};
    }

    private void drawInfoTooltip(String text, int rawX, int rawY) {
        List<String> lines = wrapText(text, 185);
        int maxW = 0;
        for (int i = 0; i < lines.size(); i++) {
            int lw = HitJumpMod.getTextWidth(lines.get(i));
            if (lw > maxW) maxW = lw;
        }
        int boxW = maxW + 12;
        int boxH = (lines.size() * 10) + 10;
        int tx = rawX;
        int ty = rawY;
        if (tx + boxW > this.width - 4) tx = this.width - boxW - 4;
        if (ty + boxH > this.height - 4) ty = rawY - boxH - 12;
        if (tx < 4) tx = 4;
        if (ty < 4) ty = 4;

        fillRect(tx - 1, ty - 1, tx + boxW + 1, ty + boxH + 1, 0.42F, 0.54F, 0.78F, 0.95F);
        fillRect(tx, ty, tx + boxW, ty + boxH, 0.06F, 0.07F, 0.10F, 0.97F);
        for (int i = 0; i < lines.size(); i++) {
            drawLeftText(lines.get(i), tx + 6, ty + 5 + (i * 10), 0xEEF2F8);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> out = new ArrayList<String>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String candidate = (line.length() == 0) ? words[i] : (line.toString() + " " + words[i]);
            if (HitJumpMod.getTextWidth(candidate) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(candidate);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(words[i]);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
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

    // ---------- Typing & Rebinding ----------

    private void handleTextTyping(HitJumpConfig config) {
        if (this.focusedInput == 0) {
            snapshotTypingKeys();
            return;
        }
        boolean shift = isKeySafe(Keyboard.KEY_LSHIFT) || isKeySafe(Keyboard.KEY_RSHIFT);
        long now = System.currentTimeMillis();

        // Backspace with hold-to-repeat
        boolean backDown = isKeySafe(Keyboard.KEY_BACK);
        if (backDown && !this.typingKeyPrev[Keyboard.KEY_BACK]) {
            deleteLastChar(config);
            this.backspaceDownMs = now;
            this.lastBackspaceRepeatMs = now;
        } else if (backDown && (now - this.backspaceDownMs) > 360L && (now - this.lastBackspaceRepeatMs) > 55L) {
            deleteLastChar(config);
            this.lastBackspaceRepeatMs = now;
        }
        this.typingKeyPrev[Keyboard.KEY_BACK] = backDown;

        // Enter key commits new line or unfocuses
        boolean enterDown = isKeySafe(Keyboard.KEY_RETURN) || isKeySafe(Keyboard.KEY_NUMPADENTER);
        if (enterDown && !this.typingKeyPrev[Keyboard.KEY_RETURN]) {
            if (this.focusedInput == 3) {
                commitNewParodyLine(config);
            } else {
                this.focusedInput = 0;
                HitJumpConfig.save();
            }
        }
        this.typingKeyPrev[Keyboard.KEY_RETURN] = enterDown;

        for (int key = 1; key < 256; key++) {
            if (key == Keyboard.KEY_BACK || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                continue;
            }
            boolean down = isKeySafe(key);
            if (down && !this.typingKeyPrev[key]) {
                char ch = mapKeyToChar(key, shift);
                if (ch != 0) {
                    appendChar(config, ch);
                }
            }
            this.typingKeyPrev[key] = down;
        }
    }

    private void appendChar(HitJumpConfig c, char ch) {
        if (this.focusedInput == 1) {
            if (this.searchQuery.length() < 32) {
                this.searchQuery += ch;
                this.scrollOffset = 0;
            }
        } else if (this.focusedInput == 2) {
            if (c.parodyHeaderText == null) c.parodyHeaderText = "";
            if (c.parodyHeaderText.length() < 28) {
                c.parodyHeaderText += ch;
                HitJumpConfig.save();
            }
        } else if (this.focusedInput == 3) {
            if (this.newParodyLineDraft.length() < 36) {
                this.newParodyLineDraft += ch;
            }
        }
    }

    private void deleteLastChar(HitJumpConfig c) {
        if (this.focusedInput == 1) {
            if (!this.searchQuery.isEmpty()) {
                this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                this.scrollOffset = 0;
            }
        } else if (this.focusedInput == 2) {
            if (c.parodyHeaderText != null && !c.parodyHeaderText.isEmpty()) {
                c.parodyHeaderText = c.parodyHeaderText.substring(0, c.parodyHeaderText.length() - 1);
                HitJumpConfig.save();
            }
        } else if (this.focusedInput == 3) {
            if (!this.newParodyLineDraft.isEmpty()) {
                this.newParodyLineDraft = this.newParodyLineDraft.substring(0, this.newParodyLineDraft.length() - 1);
            }
        }
    }

    private void commitNewParodyLine(HitJumpConfig c) {
        String trimmed = this.newParodyLineDraft.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (c.parodyLines == null) {
            c.parodyLines = new ArrayList<HitJumpConfig.ParodyLine>();
        }
        c.parodyLines.add(new HitJumpConfig.ParodyLine(trimmed, true));
        this.newParodyLineDraft = "";
        HitJumpConfig.save();
    }

    private char mapKeyToChar(int key, boolean shift) {
        switch (key) {
            case Keyboard.KEY_SPACE: return ' ';
            case Keyboard.KEY_A: return shift ? 'A' : 'a';
            case Keyboard.KEY_B: return shift ? 'B' : 'b';
            case Keyboard.KEY_C: return shift ? 'C' : 'c';
            case Keyboard.KEY_D: return shift ? 'D' : 'd';
            case Keyboard.KEY_E: return shift ? 'E' : 'e';
            case Keyboard.KEY_F: return shift ? 'F' : 'f';
            case Keyboard.KEY_G: return shift ? 'G' : 'g';
            case Keyboard.KEY_H: return shift ? 'H' : 'h';
            case Keyboard.KEY_I: return shift ? 'I' : 'i';
            case Keyboard.KEY_J: return shift ? 'J' : 'j';
            case Keyboard.KEY_K: return shift ? 'K' : 'k';
            case Keyboard.KEY_L: return shift ? 'L' : 'l';
            case Keyboard.KEY_M: return shift ? 'M' : 'm';
            case Keyboard.KEY_N: return shift ? 'N' : 'n';
            case Keyboard.KEY_O: return shift ? 'O' : 'o';
            case Keyboard.KEY_P: return shift ? 'P' : 'p';
            case Keyboard.KEY_Q: return shift ? 'Q' : 'q';
            case Keyboard.KEY_R: return shift ? 'R' : 'r';
            case Keyboard.KEY_S: return shift ? 'S' : 's';
            case Keyboard.KEY_T: return shift ? 'T' : 't';
            case Keyboard.KEY_U: return shift ? 'U' : 'u';
            case Keyboard.KEY_V: return shift ? 'V' : 'v';
            case Keyboard.KEY_W: return shift ? 'W' : 'w';
            case Keyboard.KEY_X: return shift ? 'X' : 'x';
            case Keyboard.KEY_Y: return shift ? 'Y' : 'y';
            case Keyboard.KEY_Z: return shift ? 'Z' : 'z';
            case Keyboard.KEY_0: return shift ? ')' : '0';
            case Keyboard.KEY_1: return shift ? '!' : '1';
            case Keyboard.KEY_2: return shift ? '@' : '2';
            case Keyboard.KEY_3: return shift ? '#' : '3';
            case Keyboard.KEY_4: return shift ? '$' : '4';
            case Keyboard.KEY_5: return shift ? '%' : '5';
            case Keyboard.KEY_6: return shift ? '^' : '6';
            case Keyboard.KEY_7: return shift ? '&' : '7';
            case Keyboard.KEY_8: return shift ? '*' : '8';
            case Keyboard.KEY_9: return shift ? '(' : '9';
            case Keyboard.KEY_MINUS: return shift ? '_' : '-';
            case Keyboard.KEY_EQUALS: return shift ? '+' : '=';
            case Keyboard.KEY_LBRACKET: return shift ? '{' : '[';
            case Keyboard.KEY_RBRACKET: return shift ? '}' : ']';
            case Keyboard.KEY_SEMICOLON: return shift ? ':' : ';';
            case Keyboard.KEY_APOSTROPHE: return shift ? '"' : '\'';
            case Keyboard.KEY_COMMA: return shift ? '<' : ',';
            case Keyboard.KEY_PERIOD: return shift ? '>' : '.';
            case Keyboard.KEY_SLASH: return shift ? '?' : '/';
            case Keyboard.KEY_BACKSLASH: return shift ? '|' : '\\';
            default: return 0;
        }
    }

    private void snapshotTypingKeys() {
        for (int k = 1; k < 256; k++) {
            this.typingKeyPrev[k] = isKeySafe(k);
        }
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
            } catch (Throwable ignored) {
            }
            for (int b = 1; b < btnCount; b++) {
                boolean down = false;
                try {
                    down = Mouse.isButtonDown(b);
                } catch (Throwable ignored) {
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
        } catch (Throwable ignored) {
        }
        for (int b = 0; b < this.mouseHeldAtClick.length; b++) {
            if (b < btnCount) {
                try {
                    this.mouseHeldAtClick[b] = Mouse.isButtonDown(b);
                } catch (Throwable ignored) {
                    this.mouseHeldAtClick[b] = false;
                }
            } else {
                this.mouseHeldAtClick[b] = false;
            }
        }
    }

    private boolean matchesFilter(String filter, String keywords) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        String[] parts = filter.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty() && !keywords.contains(parts[i])) {
                return false;
            }
        }
        return true;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (HitJumpMod.getTextWidth(text) <= maxWidth) return text;
        String s = text;
        while (s.length() > 1 && HitJumpMod.getTextWidth(s + "..") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private boolean blinkCursor() {
        return ((System.currentTimeMillis() / 480L) % 2L) == 0L;
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
        } catch (Throwable ignored) {
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
        this.focusedInput = 0;
        HitJumpConfig.save();
    }
}
