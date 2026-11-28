package com.example.hitjump;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class HudSuiteRenderer {
    // Widget IDs shared with HudEditorScreen
    public static final int W_PARODY = 0;
    public static final int W_FPS = 1;
    public static final int W_CPS = 2;
    public static final int W_KEYSTROKES = 3;
    public static final int W_PAPER_DOLL = 4;
    public static final int W_BEDWARS = 5;
    public static final int W_SCOREBOARD = 6;
    public static final int W_CHAT = 7;
    public static final int W_TAB = 8;
    public static final int WIDGET_COUNT = 9;

    public static final String[] WIDGET_NAMES = {
            "Parody List", "FPS", "CPS", "Keystrokes", "Paper Doll",
            "Bedwars Tips", "Scoreboard", "Chat Box", "Tab List"
    };

    // Last rendered screen-space bounds {x, y, w, h} for HudEditorScreen hit-testing
    public static final int[][] LAST_BOUNDS = new int[WIDGET_COUNT][4];

    // Live FPS & CPS tracking
    private static final List<Long> leftClickTimes = new ArrayList<Long>();
    private static final List<Long> rightClickTimes = new ArrayList<Long>();
    private static boolean prevLeftMouse = false;
    private static boolean prevRightMouse = false;

    private static int fpsFrameCounter = 0;
    private static long fpsWindowStartMs = System.currentTimeMillis();
    private static int currentFps = 60;

    // Paper Doll dynamic visibility state
    private static long paperDollActiveUntilMs = 0L;
    private static double prevPlayerX = 0.0D;
    private static double prevPlayerZ = 0.0D;

    // Reflection caches (Java 25-safe)
    private static boolean optionsSearched = false;
    private static Field optionsObjectField = null;
    private static Field hideGuiField = null;
    private static Field guiScaleField = null;
    private static Field forceUnicodeField = null;

    private static boolean textRendererSearched = false;
    private static Object textRendererObj = null;
    private static Method textGetWidthMethod = null;
    private static Method textDrawShadowMethod = null;

    private static boolean renderMgrSearched = false;
    private static Object renderManagerObj = null;
    private static Method renderEntityPosYawMethod = null;

    private static boolean scoreboardSearched = false;
    private static Field worldScoreboardField = null;
    private static Method sbGetObjSlotMethod = null;
    private static Method sbGetSortedScoresMethod = null;
    private static Method sbGetPlayerTeamMethod = null;
    private static Method objGetDisplayNameMethod = null;
    private static Method scoreGetNameMethod = null;
    private static Method scoreGetPointsMethod = null;
    private static Method teamFormatStringMethod = null;

    public static void pollInputs() {
        long now = System.currentTimeMillis();
        boolean left = false;
        boolean right = false;
        try {
            left = Mouse.isButtonDown(0);
            right = Mouse.isButtonDown(1);
        } catch (Throwable ignored) {
        }
        if (left && !prevLeftMouse) leftClickTimes.add(now);
        if (right && !prevRightMouse) rightClickTimes.add(now);
        prevLeftMouse = left;
        prevRightMouse = right;
        pruneOld(leftClickTimes, now);
        pruneOld(rightClickTimes, now);
    }

    private static void pruneOld(List<Long> list, long now) {
        Iterator<Long> it = list.iterator();
        while (it.hasNext()) {
            if (now - it.next().longValue() > 1000L) it.remove();
            else break;
        }
    }

    public static void renderInWorld() {
        pollInputs();
        updateFpsCounter();

        Minecraft client = HitJumpMod.getCachedClient();
        if (client == null || client.player == null || client.world == null) return;
        if (client.screen instanceof HudEditorScreen) return;
        if (isHudHidden(client)) return;

        int[] scaled = computeScaledResolution(client);
        int sw = scaled[0], sh = scaled[1];
        if (sw <= 0 || sh <= 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, sw, sh, 0.0D, 1000.0D, 3000.0D);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        try {
            renderAllWidgets(client, HitJumpConfig.getInstance(), sw, sh, false);
        } catch (Throwable ignored) {
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public static void renderForEditor(int screenW, int screenH) {
        pollInputs();
        updateFpsCounter();
        Minecraft client = HitJumpMod.getCachedClient();
        if (client == null || screenW <= 0 || screenH <= 0) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            renderAllWidgets(client, HitJumpConfig.getInstance(), screenW, screenH, true);
        } catch (Throwable ignored) {
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void renderAllWidgets(Minecraft client, HitJumpConfig c, int sw, int sh, boolean editorMode) {
        ensureTextRenderer(client);
        if (textRendererObj == null) return;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Track Parody HUD approximate bounding box for HudEditorScreen
        computeParodyBounds(c, sw, sh);

        if (c.fpsHudEnabled || editorMode) renderFps(c);
        if (c.cpsHudEnabled || editorMode) renderCps(c);
        if (c.keystrokesEnabled || editorMode) renderKeystrokes(c);
        if (c.bedwarsTipsEnabled || editorMode) renderBedwarsTips(c, editorMode);
        if (c.scoreboardHudEnabled || editorMode) renderScoreboard(client, c, sw, editorMode);
        if (c.chatHudEnabled || editorMode) renderChatBox(c, sh, editorMode);
        if (c.tabHudEnabled || editorMode) renderTabOverlay(client, c, sw, editorMode);
        if (c.paperDollEnabled || editorMode) renderPaperDoll(client, c, editorMode);
    }

    // ---------- Individual HUD Widgets ----------

    private static void renderFps(HitJumpConfig c) {
        String text = currentFps + " FPS";
        renderPillWidget(W_FPS, text, c.fpsX, c.fpsY, c.fpsScale, c.fpsColor, c.fpsChroma, c.fpsBgColor, c.fpsBgOpacity);
    }

    private static void renderCps(HitJumpConfig c) {
        int l = leftClickTimes.size();
        int r = rightClickTimes.size();
        String text;
        if (c.cpsMode == 1) text = l + " LMB";
        else if (c.cpsMode == 2) text = r + " RMB";
        else text = l + " | " + r + " CPS";
        renderPillWidget(W_CPS, text, c.cpsX, c.cpsY, c.cpsScale, c.cpsColor, c.cpsChroma, c.cpsBgColor, c.cpsBgOpacity);
    }

    private static void renderPillWidget(int id, String text, int x, int y, int scalePct,
                                         int textColor, boolean chroma, int bgColor, int bgOpacity) {
        float scale = clampScale(scalePct);
        int padX = 5, padY = 3;
        int localW = getWidth(text) + padX * 2;
        int localH = 8 + padY * 2;
        setBounds(id, x, y, Math.round(localW * scale), Math.round(localH * scale));

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        if (bgOpacity > 0) drawRect(0, 0, localW, localH, bgColor, bgOpacity / 100.0F);
        int rgb = resolveColor(textColor, chroma, id);
        drawText(text, padX, padY, 0xFF000000 | rgb);
        GL11.glPopMatrix();
    }

    private static void renderKeystrokes(HitJumpConfig c) {
        float scale = clampScale(c.keystrokesScale);
        int box = 20, gap = 2;
        int totalW = box * 3 + gap * 2;
        int mouseH = 18;
        int spaceH = c.keystrokesShowSpace ? 13 : 0;
        int totalH = box * 2 + gap * 2 + mouseH + (c.keystrokesShowSpace ? (gap + spaceH) : 0);
        setBounds(W_KEYSTROKES, c.keystrokesX, c.keystrokesY, Math.round(totalW * scale), Math.round(totalH * scale));

        boolean wDown = isKeySafe(Keyboard.KEY_W);
        boolean aDown = isKeySafe(Keyboard.KEY_A);
        boolean sDown = isKeySafe(Keyboard.KEY_S);
        boolean dDown = isKeySafe(Keyboard.KEY_D);
        boolean lDown = isMouseSafe(0);
        boolean rDown = isMouseSafe(1);
        boolean spDown = isKeySafe(Keyboard.KEY_SPACE);

        float bgA = c.keystrokesBgOpacity / 100.0F;
        int textRgb = resolveColor(c.keystrokesColor, c.keystrokesChroma, 0);

        GL11.glPushMatrix();
        GL11.glTranslatef(c.keystrokesX, c.keystrokesY, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);

        drawKeyBox(box + gap, 0, box, box, "W", null, wDown, c.keystrokesBgColor, bgA, textRgb);
        drawKeyBox(0, box + gap, box, box, "A", null, aDown, c.keystrokesBgColor, bgA, textRgb);
        drawKeyBox(box + gap, box + gap, box, box, "S", null, sDown, c.keystrokesBgColor, bgA, textRgb);
        drawKeyBox((box + gap) * 2, box + gap, box, box, "D", null, dDown, c.keystrokesBgColor, bgA, textRgb);

        int my = (box + gap) * 2;
        int mouseW = (totalW - gap) / 2;
        String lSub = c.keystrokesShowCps ? (leftClickTimes.size() + " CPS") : null;
        String rSub = c.keystrokesShowCps ? (rightClickTimes.size() + " CPS") : null;
        drawKeyBox(0, my, mouseW, mouseH, "LMB", lSub, lDown, c.keystrokesBgColor, bgA, textRgb);
        drawKeyBox(mouseW + gap, my, totalW - mouseW - gap, mouseH, "RMB", rSub, rDown, c.keystrokesBgColor, bgA, textRgb);

        if (c.keystrokesShowSpace) {
            int sy = my + mouseH + gap;
            drawKeyBox(0, sy, totalW, spaceH, "-----", null, spDown, c.keystrokesBgColor, bgA, textRgb);
        }

        GL11.glPopMatrix();
    }

    private static void drawKeyBox(int x, int y, int w, int h, String main, String sub,
                                   boolean pressed, int bgColor, float bgAlpha, int textRgb) {
        if (pressed) {
            drawRect(x, y, x + w, y + h, 0xFFFFFF, Math.min(0.85F, bgAlpha + 0.35F));
        } else if (bgAlpha > 0.01F) {
            drawRect(x, y, x + w, y + h, bgColor, bgAlpha);
        }
        int drawRgb = pressed ? 0x15171C : textRgb;
        if (sub == null) {
            int mw = getWidth(main);
            drawText(main, x + (w - mw) / 2, y + (h - 8) / 2, 0xFF000000 | drawRgb);
        } else {
            int mw = getWidth(main);
            drawText(main, x + (w - mw) / 2, y + 2, 0xFF000000 | drawRgb);
            GL11.glPushMatrix();
            GL11.glTranslatef(x + w / 2.0F, y + 11.0F, 0.0F);
            GL11.glScalef(0.65F, 0.65F, 1.0F);
            int sw = getWidth(sub);
            drawText(sub, -sw / 2, 0, 0xFF000000 | drawRgb);
            GL11.glPopMatrix();
        }
    }

    private static void renderBedwarsTips(HitJumpConfig c, boolean editorMode) {
        List<String> lines = BedwarsAdvisor.getDisplayLines(c);
        if (lines.isEmpty()) {
            if (!editorMode) return;
            lines = new ArrayList<String>();
            lines.add("\u00A7lBedwars Advisor");
            lines.add("\u00A7bBlue \u00A7fbroke \u00A7cRed \u00A77(8s) -> rotating");
            lines.add("\u00A7eThreat: Blue - guard bed!");
        }
        renderLineBoxWidget(W_BEDWARS, lines, c.bedwarsTipsX, c.bedwarsTipsY, c.bedwarsTipsScale,
                c.bedwarsTipsColor, c.bedwarsTipsChroma, c.bedwarsTipsBgColor, c.bedwarsTipsBgOpacity);
    }

    private static void renderScoreboard(Minecraft client, HitJumpConfig c, int sw, boolean editorMode) {
        List<String> lines = getScoreboardLines(client, c.scoreboardHideNumbers);
        if (lines.isEmpty()) {
            if (!editorMode) return;
            lines = new ArrayList<String>();
            lines.add("\u00A7e\u00A7lBED WARS");
            lines.add("Red: \u00A7a\u2714");
            lines.add("Blue: \u00A7a\u2714");
            lines.add("Green: \u00A7c\u2718");
            lines.add("Yellow: \u00A7a\u2714");
        }
        float scale = clampScale(c.scoreboardScale);
        int maxW = 0;
        for (int i = 0; i < lines.size(); i++) {
            int lw = getWidth(lines.get(i));
            if (lw > maxW) maxW = lw;
        }
        int boxW = Math.round((maxW + 10) * scale);
        int drawX = (c.scoreboardX < 0) ? Math.max(4, sw - boxW - 6) : c.scoreboardX;
        renderLineBoxWidget(W_SCOREBOARD, lines, drawX, c.scoreboardY, c.scoreboardScale,
                c.scoreboardColor, c.scoreboardChroma, c.scoreboardBgColor, c.scoreboardBgOpacity);
    }

    private static void renderChatBox(HitJumpConfig c, int sh, boolean editorMode) {
        List<String> recent = BedwarsAdvisor.getRecentFormattedChat();
        List<String> lines = new ArrayList<String>();
        int limit = Math.min(c.chatHudMaxLines, recent.size());
        for (int i = limit - 1; i >= 0; i--) {
            lines.add(recent.get(i));
        }
        if (lines.isEmpty()) {
            if (!editorMode) return;
            lines.add("\u00A77[Chat] \u00A7fCustom Chat Box Preview");
            lines.add("\u00A7bBlue \u00A7fdestroyed \u00A7cRed \u00A7fBed!");
        }
        float scale = clampScale(c.chatHudScale);
        int boxH = Math.round((lines.size() * 10 + 6) * scale);
        int drawY = (c.chatHudY < 0) ? Math.max(4, sh - boxH - 42) : c.chatHudY;
        renderLineBoxWidget(W_CHAT, lines, c.chatHudX, drawY, c.chatHudScale,
                c.chatHudColor, c.chatHudChroma, c.chatHudBgColor, c.chatHudBgOpacity);
    }

    private static void renderTabOverlay(Minecraft client, HitJumpConfig c, int sw, boolean editorMode) {
        boolean tabHeld = isKeySafe(Keyboard.KEY_TAB);
        if (!tabHeld && !editorMode) return;

        List<String> names = getNearbyPlayerNames(client);
        if (names.isEmpty()) {
            names.add("Thewier");
            names.add("Player_2");
            names.add("Player_3");
        }
        List<String> lines = new ArrayList<String>();
        lines.add("\u00A7lPlayers (" + names.size() + ")");
        int cap = Math.min(names.size(), 16);
        for (int i = 0; i < cap; i++) {
            lines.add(" " + names.get(i));
        }

        float scale = clampScale(c.tabHudScale);
        int maxW = 0;
        for (int i = 0; i < lines.size(); i++) {
            int lw = getWidth(lines.get(i));
            if (lw > maxW) maxW = lw;
        }
        int boxW = Math.round((maxW + 12) * scale);
        int drawX = (c.tabHudX < 0) ? Math.max(4, (sw - boxW) / 2) : c.tabHudX;
        renderLineBoxWidget(W_TAB, lines, drawX, c.tabHudY, c.tabHudScale,
                c.tabHudColor, c.tabHudChroma, c.tabHudBgColor, c.tabHudBgOpacity);
    }

    private static void renderPaperDoll(Minecraft client, HitJumpConfig c, boolean editorMode) {
        if (client.player == null) return;
        float scale = clampScale(c.paperDollScale);
        int boxW = Math.round(36 * scale);
        int boxH = Math.round(54 * scale);
        int x = c.paperDollX;
        int y = c.paperDollY;
        setBounds(W_PAPER_DOLL, x, y, boxW, boxH);

        // Bedrock Dynamic visibility: show while moving/sprinting/sneaking/in-air + 2.5s hold
        if (c.paperDollDynamic && !editorMode) {
            HitJumpMod.ensureEntityMotionFields(client.player);
            boolean active = !client.player.onGround || isKeySafe(Keyboard.KEY_LSHIFT);
            if (!active && HitJumpMod.entityXField != null && HitJumpMod.entityZField != null) {
                try {
                    double px = HitJumpMod.entityXField.getDouble(client.player);
                    double pz = HitJumpMod.entityZField.getDouble(client.player);
                    double movedSq = (px - prevPlayerX) * (px - prevPlayerX) + (pz - prevPlayerZ) * (pz - prevPlayerZ);
                    prevPlayerX = px;
                    prevPlayerZ = pz;
                    if (movedSq > 0.0004D) active = true;
                } catch (Throwable ignored) {
                }
            }
            long now = System.currentTimeMillis();
            if (active) paperDollActiveUntilMs = now + 2400L;
            if (now > paperDollActiveUntilMs) return;
        }

        ensureRenderManager(client);
        if (renderManagerObj == null || renderEntityPosYawMethod == null) return;

        float playerYaw = 0.0F;
        HitJumpMod.ensureEntityMotionFields(client.player);
        if (HitJumpMod.entityYawField != null) {
            try {
                playerYaw = HitJumpMod.entityYawField.getFloat(client.player);
            } catch (Throwable ignored) {
            }
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            float centerX = x + boxW / 2.0F;
            float baseY = y + boxH - 4.0F * scale;
            float modelScale = 23.0F * scale;

            GL11.glTranslatef(centerX, baseY, 60.0F);
            GL11.glScalef(-modelScale, modelScale, modelScale);
            GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
            // Keep Bedrock 3/4 view angle regardless of compass yaw while preserving animations & cape
            GL11.glRotatef(playerYaw - 155.0F, 0.0F, 1.0F, 0.0F);

            renderEntityPosYawMethod.invoke(renderManagerObj, client.player, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
        } catch (Throwable ignored) {
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void renderLineBoxWidget(int id, List<String> lines, int x, int y, int scalePct,
                                            int textColor, boolean chroma, int bgColor, int bgOpacity) {
        float scale = clampScale(scalePct);
        int maxW = 0;
        for (int i = 0; i < lines.size(); i++) {
            int lw = getWidth(lines.get(i));
            if (lw > maxW) maxW = lw;
        }
        int padX = 5, padY = 3, step = 10;
        int localW = maxW + padX * 2;
        int localH = lines.size() * step + padY * 2;
        setBounds(id, x, y, Math.round(localW * scale), Math.round(localH * scale));

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);
        if (bgOpacity > 0) drawRect(0, 0, localW, localH, bgColor, bgOpacity / 100.0F);
        for (int i = 0; i < lines.size(); i++) {
            int rgb = resolveColor(textColor, chroma, i);
            drawText(lines.get(i), padX, padY + i * step, 0xFF000000 | rgb);
        }
        GL11.glPopMatrix();
    }

    private static void computeParodyBounds(HitJumpConfig c, int sw, int sh) {
        float scale = clampScale(c.parodyScale);
        int maxW = c.parodyShowHeader ? getWidth("\u00A7l" + (c.parodyHeaderText != null ? c.parodyHeaderText : "")) : 0;
        int count = 0;
        if (c.parodyLines != null) {
            for (int i = 0; i < c.parodyLines.size(); i++) {
                HitJumpConfig.ParodyLine pl = c.parodyLines.get(i);
                if (pl != null && pl.enabled && pl.text != null && !pl.text.trim().isEmpty()) {
                    count++;
                    int w = getWidth(pl.text.trim());
                    if (w > maxW) maxW = w;
                }
            }
        }
        int localW = Math.max(48, maxW + 10);
        int localH = Math.max(16, (c.parodyShowHeader ? 14 : 0) + count * 11);
        int bw = Math.round(localW * scale);
        int bh = Math.round(localH * scale);
        boolean isRight = (c.parodyCorner == 1 || c.parodyCorner == 3);
        boolean isBottom = (c.parodyCorner == 2 || c.parodyCorner == 3);
        int bx = isRight ? (sw - c.parodyOffsetX - bw) : c.parodyOffsetX;
        int by = isBottom ? (sh - c.parodyOffsetY - bh) : c.parodyOffsetY;
        setBounds(W_PARODY, bx, by, bw, bh);
    }

    private static void setBounds(int id, int x, int y, int w, int h) {
        LAST_BOUNDS[id][0] = x;
        LAST_BOUNDS[id][1] = y;
        LAST_BOUNDS[id][2] = Math.max(12, w);
        LAST_BOUNDS[id][3] = Math.max(12, h);
    }

    // ---------- Helpers & Safe Reflection ----------

    private static void updateFpsCounter() {
        fpsFrameCounter++;
        long now = System.currentTimeMillis();
        long elapsed = now - fpsWindowStartMs;
        if (elapsed >= 500L) {
            currentFps = (int) Math.round((fpsFrameCounter * 1000.0D) / elapsed);
            fpsFrameCounter = 0;
            fpsWindowStartMs = now;
        }
    }

    private static int resolveColor(int baseRgb, boolean chroma, int index) {
        if (!chroma) return baseRgb & 0xFFFFFF;
        float base = (System.currentTimeMillis() % 4500L) / 4500.0F;
        float hue = base - (index * 0.065F);
        hue = hue - (float) Math.floor(hue);
        return Color.HSBtoRGB(hue, 0.72F, 1.0F) & 0xFFFFFF;
    }

    private static float clampScale(int pct) {
        float s = pct / 100.0F;
        return s < 0.5F ? 0.5F : (s > 2.0F ? 2.0F : s);
    }

    private static void drawRect(float x0, float y0, float x1, float y1, int rgb, float alpha) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(r, g, b, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0); GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x1, y0);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private static int getWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (textRendererObj != null && textGetWidthMethod != null) {
            try {
                return ((Integer) textGetWidthMethod.invoke(textRendererObj, text)).intValue();
            } catch (Throwable ignored) {
            }
        }
        return text.length() * 6;
    }

    private static void drawText(String text, int x, int y, int color) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        try {
            if (textRendererObj != null && textDrawShadowMethod != null) {
                textDrawShadowMethod.invoke(textRendererObj, text, (float) x, (float) y, color);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void ensureRenderManager(Minecraft client) {
        if (renderMgrSearched || client == null || client.player == null) return;
        renderMgrSearched = true;
        Class<?> entityBase = HitJumpMod.getEntityBaseClass(client.player);
        if (entityBase == null) return;
        for (Field f : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            Object val;
            try { f.setAccessible(true); val = f.get(client); } catch (Throwable ignored) { continue; }
            if (val == null || val.getClass().isArray() || isSkippableClass(val.getClass())) continue;
            try {
                for (Method m : val.getClass().getMethods()) {
                    if (m.getReturnType() != boolean.class) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 6 && p[0] == entityBase && p[1] == double.class && p[2] == double.class
                            && p[3] == double.class && p[4] == float.class && p[5] == float.class) {
                        renderManagerObj = val;
                        renderEntityPosYawMethod = m;
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static List<String> getScoreboardLines(Minecraft client, boolean hideNumbers) {
        List<String> out = new ArrayList<String>();
        if (client == null || client.world == null) return out;
        ensureScoreboardReflection(client.world);
        if (worldScoreboardField == null || sbGetObjSlotMethod == null || sbGetSortedScoresMethod == null) return out;
        try {
            Object sb = worldScoreboardField.get(client.world);
            if (sb == null) return out;
            Object obj = sbGetObjSlotMethod.invoke(sb, 1); // 1 = Sidebar
            if (obj == null) return out;

            if (objGetDisplayNameMethod != null) {
                Object title = objGetDisplayNameMethod.invoke(obj);
                if (title instanceof String && !((String) title).isEmpty()) out.add((String) title);
            }

            Object scoresObj = sbGetSortedScoresMethod.invoke(sb, obj);
            if (!(scoresObj instanceof Collection)) return out;
            List<Object> list = new ArrayList<Object>((Collection<?>) scoresObj);
            int start = Math.max(0, list.size() - 15);
            for (int i = list.size() - 1; i >= start; i--) {
                Object score = list.get(i);
                if (score == null) continue;
                bindScoreMethods(score);
                if (scoreGetNameMethod == null) continue;
                String entry = (String) scoreGetNameMethod.invoke(score);
                if (entry == null || entry.startsWith("#")) continue;
                String formatted = entry;
                if (sbGetPlayerTeamMethod != null) {
                    Object team = sbGetPlayerTeamMethod.invoke(sb, entry);
                    if (team != null) {
                        bindTeamFormatMethod(team);
                        if (teamFormatStringMethod != null) {
                            Object res = teamFormatStringMethod.invoke(team, entry);
                            if (res instanceof String) formatted = (String) res;
                        }
                    }
                }
                if (!hideNumbers && scoreGetPointsMethod != null) {
                    int pts = ((Integer) scoreGetPointsMethod.invoke(score)).intValue();
                    formatted = formatted + " \u00A7c" + pts;
                }
                out.add(formatted);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void ensureScoreboardReflection(Object world) {
        if (scoreboardSearched || world == null) return;
        scoreboardSearched = true;
        Class<?> cls = world.getClass();
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
            Field[] fields;
            try { fields = cls.getDeclaredFields(); } catch (Throwable ignored) { break; }
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || isSkippableClass(f.getType())) continue;
                Class<?> ft = f.getType();
                Method getSlot = null, getSorted = null, getTeam = null;
                try {
                    for (Method m : ft.getMethods()) {
                        Class<?>[] p = m.getParameterTypes();
                        Class<?> rt = m.getReturnType();
                        if (p.length == 1 && p[0] == int.class && !rt.isPrimitive() && !rt.isArray() && !isSkippableClass(rt) && rt != String.class) {
                            // Verify another method takes this Objective type and returns Collection
                            for (Method m2 : ft.getMethods()) {
                                Class<?>[] p2 = m2.getParameterTypes();
                                if (p2.length == 1 && p2[0] == rt && Collection.class.isAssignableFrom(m2.getReturnType())) {
                                    getSlot = m;
                                    getSorted = m2;
                                    break;
                                }
                            }
                        }
                        if (getSlot != null) break;
                    }
                    if (getSlot != null && getSorted != null) {
                        Class<?> objClass = getSlot.getReturnType();
                        for (Method om : objClass.getMethods()) {
                            if (om.getParameterTypes().length == 0 && om.getReturnType() == String.class && !"toString".equals(om.getName())) {
                                objGetDisplayNameMethod = om; // Prefer display name (last or non-name)
                            }
                        }
                        for (Method tm : ft.getMethods()) {
                            Class<?>[] tp = tm.getParameterTypes();
                            Class<?> trt = tm.getReturnType();
                            if (tp.length == 1 && tp[0] == String.class && !trt.isPrimitive() && !isSkippableClass(trt) && trt != objClass && trt != String.class) {
                                // Check if return type has (String)->String formatString
                                for (Method fm : trt.getMethods()) {
                                    Class<?>[] fp = fm.getParameterTypes();
                                    if (fp.length == 1 && fp[0] == String.class && fm.getReturnType() == String.class) {
                                        getTeam = tm;
                                        teamFormatStringMethod = fm;
                                        break;
                                    }
                                }
                                if (getTeam != null) break;
                            }
                        }
                        f.setAccessible(true);
                        worldScoreboardField = f;
                        sbGetObjSlotMethod = getSlot;
                        sbGetSortedScoresMethod = getSorted;
                        sbGetPlayerTeamMethod = getTeam;
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static void bindScoreMethods(Object score) {
        if (scoreGetNameMethod != null) return;
        for (Method m : score.getClass().getMethods()) {
            if (m.getParameterTypes().length != 0) continue;
            if (m.getReturnType() == String.class && !"toString".equals(m.getName())) scoreGetNameMethod = m;
            else if (m.getReturnType() == int.class && !"hashCode".equals(m.getName())) scoreGetPointsMethod = m;
        }
    }

    private static void bindTeamFormatMethod(Object team) {
        if (teamFormatStringMethod != null) return;
        for (Method m : team.getClass().getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == String.class && m.getReturnType() == String.class) {
                teamFormatStringMethod = m;
                return;
            }
        }
    }

    private static List<String> getNearbyPlayerNames(Minecraft client) {
        List<String> names = new ArrayList<String>();
        if (client == null || client.world == null || client.player == null) return names;
        try {
            Class<?> playerBase = HitJumpMod.getPlayerBaseClass(client.player);
            List<?> best = null;
            Class<?> wc = client.world.getClass();
            while (wc != null && wc != Object.class && !isSkippableClass(wc)) {
                for (Field f : wc.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || !List.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object val = f.get(client.world);
                    if (val instanceof List) {
                        List<?> l = (List<?>) val;
                        if (l.contains(client.player) && (best == null || l.size() < best.size())) best = l;
                    }
                }
                wc = wc.getSuperclass();
            }
            if (best != null && playerBase != null) {
                Field profField = null;
                Method getName = null;
                for (Field pf : playerBase.getDeclaredFields()) {
                    if ("com.mojang.authlib.GameProfile".equals(pf.getType().getName())) {
                        pf.setAccessible(true);
                        profField = pf;
                        getName = pf.getType().getMethod("getName");
                        break;
                    }
                }
                if (profField != null && getName != null) {
                    for (int i = 0; i < best.size(); i++) {
                        Object p = best.get(i);
                        if (p != null && playerBase.isInstance(p)) {
                            Object prof = profField.get(p);
                            if (prof != null) {
                                Object n = getName.invoke(prof);
                                if (n instanceof String) names.add((String) n);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    private static int[] computeScaledResolution(Minecraft client) {
        if (client.screen != null && client.screen.width > 0 && client.screen.height > 0) {
            return new int[]{client.screen.width, client.screen.height};
        }
        int dw = 0, dh = 0;
        try { dw = Display.getWidth(); dh = Display.getHeight(); } catch (Throwable ignored) {}
        if (dw <= 0 || dh <= 0) return new int[]{854, 480};

        ensureOptionsFields(client);
        int guiScale = 0;
        boolean unicode = false;
        if (optionsObjectField != null) {
            try {
                Object opts = optionsObjectField.get(client);
                if (opts != null) {
                    if (guiScaleField != null) guiScale = guiScaleField.getInt(opts);
                    if (forceUnicodeField != null) unicode = forceUnicodeField.getBoolean(opts);
                }
            } catch (Throwable ignored) {}
        }
        int sf = 1;
        int target = (guiScale == 0) ? 1000 : guiScale;
        while (sf < target && dw / (sf + 1) >= 320 && dh / (sf + 1) >= 240) sf++;
        if (unicode && sf % 2 != 0 && sf != 1) sf--;
        return new int[]{(int) Math.ceil(dw / (double) sf), (int) Math.ceil(dh / (double) sf)};
    }

    private static boolean isHudHidden(Minecraft client) {
        ensureOptionsFields(client);
        if (optionsObjectField != null && hideGuiField != null) {
            try {
                Object opts = optionsObjectField.get(client);
                if (opts != null) return hideGuiField.getBoolean(opts);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static void ensureOptionsFields(Minecraft client) {
        if (optionsSearched || client == null) return;
        optionsSearched = true;
        for (Field f : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            Object obj;
            try { f.setAccessible(true); obj = f.get(client); } catch (Throwable ignored) { continue; }
            if (obj == null || obj.getClass().isArray() || isSkippableClass(obj.getClass())) continue;
            Field[] fields;
            try { fields = obj.getClass().getDeclaredFields(); } catch (Throwable ignored) { continue; }
            for (int i = 0; i <= fields.length - 11; i++) {
                if (isInstanceField(fields[i], boolean.class) && isInstanceField(fields[i + 1], int.class)
                        && isInstanceField(fields[i + 2], boolean.class) && isInstanceField(fields[i + 5], String.class)
                        && isInstanceField(fields[i + 8], float.class) && isInstanceField(fields[i + 9], float.class)) {
                    try {
                        optionsObjectField = f;
                        hideGuiField = fields[i];
                        hideGuiField.setAccessible(true);
                        if (i + 14 < fields.length && isInstanceField(fields[i + 11], int.class) && isInstanceField(fields[i + 14], boolean.class)) {
                            guiScaleField = fields[i + 11];
                            forceUnicodeField = fields[i + 14];
                            guiScaleField.setAccessible(true);
                            forceUnicodeField.setAccessible(true);
                        }
                    } catch (Throwable ignored) {}
                    return;
                }
            }
        }
    }

    private static void ensureTextRenderer(Minecraft client) {
        if (textRendererSearched || client == null) return;
        textRendererSearched = true;
        for (Field f : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            Object val;
            try { f.setAccessible(true); val = f.get(client); } catch (Throwable ignored) { continue; }
            if (val == null || val.getClass().isArray() || isSkippableClass(val.getClass())) continue;
            Method w = null, sh = null, pl = null;
            try {
                for (Method m : val.getClass().getMethods()) {
                    if (m.getReturnType() != int.class) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && p[0] == String.class) w = m;
                    else if (p.length == 4 && p[0] == String.class && p[1] == float.class && p[2] == float.class && p[3] == int.class) sh = m;
                    else if (p.length == 4 && p[0] == String.class && p[1] == int.class && p[2] == int.class && p[3] == int.class) pl = m;
                }
            } catch (Throwable ignored) { continue; }
            if (w != null && sh != null && pl != null) {
                textRendererObj = val;
                textGetWidthMethod = w;
                textDrawShadowMethod = sh;
                return;
            }
        }
    }

    private static boolean isKeySafe(int k) {
        try { return Keyboard.isKeyDown(k); } catch (Throwable ignored) { return false; }
    }

    private static boolean isMouseSafe(int b) {
        try { return Mouse.isButtonDown(b); } catch (Throwable ignored) { return false; }
    }

    private static boolean isInstanceField(Field f, Class<?> t) {
        return !Modifier.isStatic(f.getModifiers()) && f.getType() == t;
    }

    private static boolean isSkippableClass(Class<?> cls) {
        if (cls == null) return true;
        String n = cls.getName();
        return n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("jdk.") || n.startsWith("sun.")
                || n.startsWith("com.google.") || n.startsWith("org.apache.") || n.startsWith("io.netty.")
                || n.startsWith("it.unimi.") || n.startsWith("org.lwjgl.") || n.startsWith("org.slf4j.");
    }
}
