package com.example.hitjump;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class HudEditorScreen extends Screen {
    private final Screen parent;
    private int draggingWidget = -1;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean wasLeftDown = false;
    private boolean wasRightDown = false;

    public HudEditorScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        this.draggingWidget = -1;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        HitJumpConfig c = HitJumpConfig.getInstance();

        boolean leftDown = false, rightDown = false;
        int wheel = 0;
        try {
            leftDown = Mouse.isButtonDown(0);
            rightDown = Mouse.isButtonDown(1);
            wheel = Mouse.getDWheel();
        } catch (Throwable ignored) {
        }
        boolean leftClick = leftDown && !this.wasLeftDown;
        boolean rightClick = rightDown && !this.wasRightDown;

        // Subtle dark overlay + center guide crosshairs
        fillRect(0, 0, this.width, this.height, 0.03F, 0.04F, 0.06F, 0.38F);
        fillRect(this.width / 2.0F, 0, this.width / 2.0F + 1, this.height, 1.0F, 1.0F, 1.0F, 0.12F);
        fillRect(0, this.height / 2.0F, this.width, this.height / 2.0F + 1, 1.0F, 1.0F, 1.0F, 0.12F);

        // Render live previews of Parody HUD + all HUD widgets
        ParodyHudRenderer.renderPreview(this.width, this.height);
        HudSuiteRenderer.renderForEditor(this.width, this.height);

        // Top control bar
        int barW = 310, barH = 22;
        int barX = (this.width - barW) / 2, barY = 6;
        fillRect(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0.35F, 0.40F, 0.50F, 0.95F);
        fillRect(barX, barY, barX + barW, barY + barH, 0.08F, 0.09F, 0.12F, 0.95F);
        this.drawCenteredString(this.textRenderer, "Drag: Move | Scroll: Resize | Right-Click: Toggle",
                barX + barW / 2, barY + 7, 0xDCE4F2);

        int btnY = barY + barH + 4;
        int resetX = barX + 18, resetW = 110;
        int backX = barX + barW - 128, backW = 110;
        boolean hoverReset = isInside(mouseX, mouseY, resetX, btnY, resetW, 18);
        boolean hoverBack = isInside(mouseX, mouseY, backX, btnY, backW, 18);

        fillRect(resetX, btnY, resetX + resetW, btnY + 18,
                hoverReset ? 0.48F : 0.30F, hoverReset ? 0.18F : 0.14F, hoverReset ? 0.18F : 0.15F, 0.95F);
        this.drawCenteredString(this.textRenderer, "Reset Layout", resetX + resetW / 2, btnY + 5, 0xFFFFFF);

        fillRect(backX, btnY, backX + backW, btnY + 18,
                hoverBack ? 0.22F : 0.15F, hoverBack ? 0.50F : 0.38F, hoverBack ? 0.28F : 0.22F, 0.95F);
        this.drawCenteredString(this.textRenderer, "Save & Back", backX + backW / 2, btnY + 5, 0xFFFFFF);

        if (leftClick && hoverReset) {
            resetHudPositions(c);
            HitJumpConfig.save();
            this.wasLeftDown = leftDown;
            this.wasRightDown = rightDown;
            return;
        }
        if (leftClick && hoverBack) {
            HitJumpConfig.save();
            this.minecraft.openScreen(this.parent);
            this.wasLeftDown = leftDown;
            this.wasRightDown = rightDown;
            return;
        }

        // Hit-test active widgets (top-most first)
        int hovered = -1;
        for (int i = HudSuiteRenderer.WIDGET_COUNT - 1; i >= 0; i--) {
            if (!isWidgetPresentInEditor(c, i)) continue;
            int[] b = HudSuiteRenderer.LAST_BOUNDS[i];
            if (b[2] <= 0 || b[3] <= 0) continue;
            if (isInside(mouseX, mouseY, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4)) {
                hovered = i;
                break;
            }
        }

        if (leftClick && hovered >= 0) {
            this.draggingWidget = hovered;
            int[] b = HudSuiteRenderer.LAST_BOUNDS[hovered];
            this.dragOffsetX = mouseX - b[0];
            this.dragOffsetY = mouseY - b[1];
        }
        if (!leftDown) {
            if (this.draggingWidget >= 0) HitJumpConfig.save();
            this.draggingWidget = -1;
        }
        if (leftDown && this.draggingWidget >= 0) {
            int[] b = HudSuiteRenderer.LAST_BOUNDS[this.draggingWidget];
            int newX = clampInt(mouseX - this.dragOffsetX, 0, Math.max(0, this.width - b[2]));
            int newY = clampInt(mouseY - this.dragOffsetY, 0, Math.max(0, this.height - b[3]));
            applyWidgetPosition(c, this.draggingWidget, newX, newY, b[2], b[3]);
        }

        if (rightClick && hovered >= 0) {
            toggleWidget(c, hovered);
            HitJumpConfig.save();
        }
        if (wheel != 0 && hovered >= 0) {
            adjustWidgetScale(c, hovered, wheel > 0 ? 10 : -10);
            HitJumpConfig.save();
        }

        // Draw bounding frames & labels
        for (int i = 0; i < HudSuiteRenderer.WIDGET_COUNT; i++) {
            if (!isWidgetPresentInEditor(c, i)) continue;
            int[] b = HudSuiteRenderer.LAST_BOUNDS[i];
            if (b[2] <= 0 || b[3] <= 0) continue;

            boolean on = isWidgetEnabled(c, i);
            boolean active = (i == this.draggingWidget || i == hovered);
            float r = active ? 0.35F : (on ? 0.25F : 0.70F);
            float g = active ? 0.75F : (on ? 0.78F : 0.25F);
            float bl = active ? 1.00F : (on ? 0.45F : 0.25F);
            drawOutline(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, r, g, bl, active ? 0.95F : 0.55F);

            if (active || !on) {
                String label = getWidgetLabel(c, i);
                String tag = label + (on ? "" : " [OFF]");
                int tw = HitJumpMod.getTextWidth(tag) + 6;
                int ly = Math.max(2, b[1] - 11);
                fillRect(b[0], ly, b[0] + tw, ly + 10, 0.05F, 0.06F, 0.08F, 0.85F);
                this.drawCenteredString(this.textRenderer, tag, b[0] + tw / 2, ly + 1, on ? 0xFFFFFF : 0xFF7878);
            }
        }

        this.wasLeftDown = leftDown;
        this.wasRightDown = rightDown;
    }

    private boolean isWidgetPresentInEditor(HitJumpConfig c, int id) {
        if (id == HudSuiteRenderer.W_KEYSTROKES_MOUSE || id == HudSuiteRenderer.W_KEYSTROKES_SPACE) {
            return c.keystrokesSplitPieces;
        }
        return true;
    }

    private String getWidgetLabel(HitJumpConfig c, int id) {
        if (id == HudSuiteRenderer.W_KEYSTROKES && c.keystrokesSplitPieces) {
            return "Keystrokes (WASD)";
        }
        return HudSuiteRenderer.WIDGET_NAMES[id];
    }

    private void applyWidgetPosition(HitJumpConfig c, int id, int x, int y, int bw, int bh) {
        switch (id) {
            case HudSuiteRenderer.W_PARODY:
                boolean right = (x + bw / 2) > (this.width / 2);
                boolean bottom = (y + bh / 2) > (this.height / 2);
                c.parodyCorner = (bottom ? 2 : 0) + (right ? 1 : 0);
                c.parodyOffsetX = right ? Math.max(0, this.width - (x + bw)) : x;
                c.parodyOffsetY = bottom ? Math.max(0, this.height - (y + bh)) : y;
                break;
            case HudSuiteRenderer.W_FPS: c.fpsX = x; c.fpsY = y; break;
            case HudSuiteRenderer.W_CPS: c.cpsX = x; c.cpsY = y; break;
            case HudSuiteRenderer.W_KEYSTROKES: c.keystrokesX = x; c.keystrokesY = y; break;
            case HudSuiteRenderer.W_KEYSTROKES_MOUSE: c.keystrokesMouseX = x; c.keystrokesMouseY = y; break;
            case HudSuiteRenderer.W_KEYSTROKES_SPACE: c.keystrokesSpaceX = x; c.keystrokesSpaceY = y; break;
            case HudSuiteRenderer.W_PAPER_DOLL: c.paperDollX = x; c.paperDollY = y; break;
            case HudSuiteRenderer.W_BEDWARS: c.bedwarsTipsX = x; c.bedwarsTipsY = y; break;
            case HudSuiteRenderer.W_SCOREBOARD: c.scoreboardX = x; c.scoreboardY = y; break;
            case HudSuiteRenderer.W_CHAT: c.chatHudX = x; c.chatHudY = y; break;
            case HudSuiteRenderer.W_TAB: c.tabHudX = x; c.tabHudY = y; break;
            default: break;
        }
    }

    private void adjustWidgetScale(HitJumpConfig c, int id, int delta) {
        switch (id) {
            case HudSuiteRenderer.W_PARODY: c.parodyScale = clampInt(c.parodyScale + delta, 50, 200); break;
            case HudSuiteRenderer.W_FPS: c.fpsScale = clampInt(c.fpsScale + delta, 50, 200); break;
            case HudSuiteRenderer.W_CPS: c.cpsScale = clampInt(c.cpsScale + delta, 50, 200); break;
            case HudSuiteRenderer.W_KEYSTROKES:
            case HudSuiteRenderer.W_KEYSTROKES_MOUSE:
            case HudSuiteRenderer.W_KEYSTROKES_SPACE:
                c.keystrokesScale = clampInt(c.keystrokesScale + delta, 50, 200);
                break;
            case HudSuiteRenderer.W_PAPER_DOLL: c.paperDollScale = clampInt(c.paperDollScale + delta, 50, 200); break;
            case HudSuiteRenderer.W_BEDWARS: c.bedwarsTipsScale = clampInt(c.bedwarsTipsScale + delta, 50, 200); break;
            case HudSuiteRenderer.W_SCOREBOARD: c.scoreboardScale = clampInt(c.scoreboardScale + delta, 50, 200); break;
            case HudSuiteRenderer.W_CHAT: c.chatHudScale = clampInt(c.chatHudScale + delta, 50, 200); break;
            case HudSuiteRenderer.W_TAB: c.tabHudScale = clampInt(c.tabHudScale + delta, 50, 200); break;
            default: break;
        }
    }

    private void toggleWidget(HitJumpConfig c, int id) {
        switch (id) {
            case HudSuiteRenderer.W_PARODY: c.parodyHudEnabled = !c.parodyHudEnabled; break;
            case HudSuiteRenderer.W_FPS: c.fpsHudEnabled = !c.fpsHudEnabled; break;
            case HudSuiteRenderer.W_CPS: c.cpsHudEnabled = !c.cpsHudEnabled; break;
            case HudSuiteRenderer.W_KEYSTROKES:
                if (c.keystrokesSplitPieces) c.keystrokesShowWasd = !c.keystrokesShowWasd;
                else c.keystrokesEnabled = !c.keystrokesEnabled;
                break;
            case HudSuiteRenderer.W_KEYSTROKES_MOUSE: c.keystrokesShowMouse = !c.keystrokesShowMouse; break;
            case HudSuiteRenderer.W_KEYSTROKES_SPACE: c.keystrokesShowSpace = !c.keystrokesShowSpace; break;
            case HudSuiteRenderer.W_PAPER_DOLL: c.paperDollEnabled = !c.paperDollEnabled; break;
            case HudSuiteRenderer.W_BEDWARS: c.bedwarsTipsEnabled = !c.bedwarsTipsEnabled; break;
            case HudSuiteRenderer.W_SCOREBOARD: c.scoreboardHudEnabled = !c.scoreboardHudEnabled; break;
            case HudSuiteRenderer.W_CHAT: c.chatHudEnabled = !c.chatHudEnabled; break;
            case HudSuiteRenderer.W_TAB: c.tabHudEnabled = !c.tabHudEnabled; break;
            default: break;
        }
    }

    private boolean isWidgetEnabled(HitJumpConfig c, int id) {
        switch (id) {
            case HudSuiteRenderer.W_PARODY: return c.parodyHudEnabled;
            case HudSuiteRenderer.W_FPS: return c.fpsHudEnabled;
            case HudSuiteRenderer.W_CPS: return c.cpsHudEnabled;
            case HudSuiteRenderer.W_KEYSTROKES:
                return c.keystrokesEnabled && (!c.keystrokesSplitPieces || c.keystrokesShowWasd);
            case HudSuiteRenderer.W_KEYSTROKES_MOUSE:
                return c.keystrokesEnabled && c.keystrokesShowMouse;
            case HudSuiteRenderer.W_KEYSTROKES_SPACE:
                return c.keystrokesEnabled && c.keystrokesShowSpace;
            case HudSuiteRenderer.W_PAPER_DOLL: return c.paperDollEnabled;
            case HudSuiteRenderer.W_BEDWARS: return c.bedwarsTipsEnabled;
            case HudSuiteRenderer.W_SCOREBOARD: return c.scoreboardHudEnabled;
            case HudSuiteRenderer.W_CHAT: return c.chatHudEnabled;
            case HudSuiteRenderer.W_TAB: return c.tabHudEnabled;
            default: return false;
        }
    }

    private void resetHudPositions(HitJumpConfig c) {
        c.parodyCorner = 1; c.parodyOffsetX = 6; c.parodyOffsetY = 6; c.parodyScale = 100;
        c.fpsX = 6; c.fpsY = 126; c.fpsScale = 100;
        c.cpsX = 6; c.cpsY = 144; c.cpsScale = 100;
        c.keystrokesX = 6; c.keystrokesY = 164;
        c.keystrokesMouseX = 6; c.keystrokesMouseY = 208;
        c.keystrokesSpaceX = 6; c.keystrokesSpaceY = 228;
        c.keystrokesScale = 100;
        c.paperDollX = 28; c.paperDollY = 58; c.paperDollScale = 100;
        c.bedwarsTipsX = 6; c.bedwarsTipsY = 258; c.bedwarsTipsScale = 90;
        c.scoreboardX = -1; c.scoreboardY = 120; c.scoreboardScale = 100;
        c.chatHudX = 6; c.chatHudY = -1; c.chatHudScale = 100;
        c.tabHudX = -1; c.tabHudY = 14; c.tabHudScale = 100;
    }

    private void fillRect(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0); GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x1, y0);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private void drawOutline(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1.5F);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x0, y0); GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x0, y1);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int clampInt(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    @Override
    public void removed() {
        HitJumpConfig.save();
    }
}
