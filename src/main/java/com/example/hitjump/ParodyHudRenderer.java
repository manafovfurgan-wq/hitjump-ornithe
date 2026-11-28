package com.example.hitjump;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ParodyHudRenderer {
    private static boolean optionsSearched = false;
    private static Field optionsObjectField = null;
    private static Field hideGuiField = null;
    private static Field guiScaleField = null;
    private static Field forceUnicodeField = null;

    private static boolean textRendererSearched = false;
    private static Object textRendererObj = null;
    private static Method textGetWidthMethod = null;
    private static Method textDrawShadowMethod = null;
    private static Method textDrawPlainMethod = null;

    public static void renderInWorld() {
        Minecraft client = HitJumpMod.getCachedClient();
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        // HitJumpConfigScreen draws its own bright live preview over the background dim
        if (client.screen instanceof HitJumpConfigScreen) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.parodyHudEnabled) {
            return;
        }
        // Respect F1 (hidden HUD)
        if (isHudHidden(client)) {
            return;
        }

        int[] scaled = computeScaledResolution(client);
        int scaledW = scaled[0];
        int scaledH = scaled[1];
        if (scaledW <= 0 || scaledH <= 0) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, scaledW, scaledH, 0.0D, 1000.0D, 3000.0D);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        try {
            renderOverlay(client, config, scaledW, scaledH);
        } catch (Throwable ignored) {
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public static void renderPreview(int screenW, int screenH) {
        Minecraft client = HitJumpMod.getCachedClient();
        if (client == null || screenW <= 0 || screenH <= 0) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.parodyHudEnabled) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            renderOverlay(client, config, screenW, screenH);
        } catch (Throwable ignored) {
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void renderOverlay(Minecraft client, HitJumpConfig config, int screenW, int screenH) {
        ensureTextRenderer(client);
        if (textRendererObj == null) {
            return;
        }

        final List<String> lines = new ArrayList<String>();
        if (config.parodyLines != null) {
            for (int i = 0; i < config.parodyLines.size(); i++) {
                HitJumpConfig.ParodyLine entry = config.parodyLines.get(i);
                if (entry != null && entry.enabled && entry.text != null) {
                    String trimmed = entry.text.trim();
                    if (!trimmed.isEmpty()) {
                        lines.add(trimmed);
                    }
                }
            }
        }

        String header = (config.parodyShowHeader && config.parodyHeaderText != null)
                ? config.parodyHeaderText.trim()
                : "";
        boolean hasHeader = !header.isEmpty();

        if (!hasHeader && lines.isEmpty()) {
            return;
        }

        final boolean isRight = (config.parodyCorner == 1 || config.parodyCorner == 3);
        final boolean isBottom = (config.parodyCorner == 2 || config.parodyCorner == 3);

        if (config.parodySortByWidth && lines.size() > 1) {
            Collections.sort(lines, new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    int wa = getWidth(a);
                    int wb = getWidth(b);
                    return isBottom ? Integer.compare(wa, wb) : Integer.compare(wb, wa);
                }
            });
        }

        float scale = config.parodyScale / 100.0F;
        if (scale < 0.5F) scale = 0.5F;
        if (scale > 2.0F) scale = 2.0F;

        int lineStep = 11;
        int headerStep = 14;
        int totalLocalH = (hasHeader ? headerStep : 0) + (lines.size() * lineStep);

        float anchorX = isRight ? (screenW - config.parodyOffsetX) : config.parodyOffsetX;
        float anchorY = isBottom ? (screenH - config.parodyOffsetY) : config.parodyOffsetY;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glPushMatrix();
        GL11.glTranslatef(anchorX, anchorY, 0.0F);
        GL11.glScalef(scale, scale, 1.0F);

        int curY = isBottom ? -totalLocalH : 0;
        float bgAlpha = config.parodyBgOpacity / 100.0F;
        int colorIndex = 0;

        // Optional Header Title
        if (hasHeader) {
            String boldHeader = "\u00A7l" + header;
            int hw = getWidth(boldHeader);
            int rgb = getLineColor(config, colorIndex++);
            int drawX = isRight ? (-hw - 3) : 3;

            if (config.parodyShowBackground && bgAlpha > 0.01F) {
                drawRowBacking(drawX, curY, hw, 12, isRight, bgAlpha, rgb);
            }
            drawText(boldHeader, drawX, curY + 2, 0xFF000000 | rgb, config.parodyTextShadow);
            curY += headerStep;
        }

        // Parody Lines
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int tw = getWidth(text);
            int rgb = getLineColor(config, colorIndex++);
            int drawX = isRight ? (-tw - 3) : 3;

            if (config.parodyShowBackground && bgAlpha > 0.01F) {
                drawRowBacking(drawX, curY, tw, lineStep, isRight, bgAlpha, rgb);
            }
            drawText(text, drawX, curY + 1, 0xFF000000 | rgb, config.parodyTextShadow);
            curY += lineStep;
        }

        GL11.glPopMatrix();
    }

    private static void drawRowBacking(
            int textX, int rowY, int textW, int rowH,
            boolean isRight, float bgAlpha, int accentRgb
    ) {
        float x0 = textX - 3.0F;
        float x1 = textX + textW + 3.0F;
        float y0 = rowY;
        float y1 = rowY + rowH;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.04F, 0.05F, 0.07F, bgAlpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y0);
        GL11.glEnd();

        // Thin colored edge accent bar
        float ar = ((accentRgb >> 16) & 0xFF) / 255.0F;
        float ag = ((accentRgb >> 8) & 0xFF) / 255.0F;
        float ab = (accentRgb & 0xFF) / 255.0F;
        GL11.glColor4f(ar, ag, ab, Math.min(1.0F, bgAlpha + 0.45F));
        float barX0 = isRight ? (x1 - 1.5F) : x0;
        float barX1 = isRight ? x1 : (x0 + 1.5F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(barX0, y0);
        GL11.glVertex2f(barX0, y1);
        GL11.glVertex2f(barX1, y1);
        GL11.glVertex2f(barX1, y0);
        GL11.glEnd();
    }

    private static int getLineColor(HitJumpConfig config, int index) {
        if (!config.parodyChromaWave) {
            return config.parodyTextColor & 0xFFFFFF;
        }
        float base = (System.currentTimeMillis() % 4500L) / 4500.0F;
        float hue = base - (index * 0.065F);
        hue = hue - (float) Math.floor(hue);
        return Color.HSBtoRGB(hue, 0.72F, 1.0F) & 0xFFFFFF;
    }

    private static int getWidth(String text) {
        if (textRendererObj != null && textGetWidthMethod != null) {
            try {
                return ((Integer) textGetWidthMethod.invoke(textRendererObj, text)).intValue();
            } catch (Throwable ignored) {
            }
        }
        return text.length() * 6;
    }

    private static void drawText(String text, int x, int y, int color, boolean shadow) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            if (shadow && textDrawShadowMethod != null) {
                textDrawShadowMethod.invoke(textRendererObj, text, (float) x, (float) y, color);
            } else if (textDrawPlainMethod != null) {
                textDrawPlainMethod.invoke(textRendererObj, text, x, y, color);
            } else if (textDrawShadowMethod != null) {
                textDrawShadowMethod.invoke(textRendererObj, text, (float) x, (float) y, color);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int[] computeScaledResolution(Minecraft client) {
        if (client.screen != null && client.screen.width > 0 && client.screen.height > 0) {
            return new int[]{client.screen.width, client.screen.height};
        }
        int dw = 0;
        int dh = 0;
        try {
            dw = Display.getWidth();
            dh = Display.getHeight();
        } catch (Throwable ignored) {
        }
        if (dw <= 0 || dh <= 0) {
            return new int[]{854, 480};
        }

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
            } catch (Throwable ignored) {
            }
        }

        int scaleFactor = 1;
        int target = (guiScale == 0) ? 1000 : guiScale;
        while (scaleFactor < target && dw / (scaleFactor + 1) >= 320 && dh / (scaleFactor + 1) >= 240) {
            scaleFactor++;
        }
        if (unicode && scaleFactor % 2 != 0 && scaleFactor != 1) {
            scaleFactor--;
        }

        int sw = (int) Math.ceil(dw / (double) scaleFactor);
        int sh = (int) Math.ceil(dh / (double) scaleFactor);
        return new int[]{sw, sh};
    }

    private static boolean isHudHidden(Minecraft client) {
        ensureOptionsFields(client);
        if (optionsObjectField != null && hideGuiField != null) {
            try {
                Object opts = optionsObjectField.get(client);
                if (opts != null) {
                    return hideGuiField.getBoolean(opts);
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void ensureOptionsFields(Minecraft client) {
        if (optionsSearched || client == null) {
            return;
        }
        optionsSearched = true;
        for (Field f : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            Object obj;
            try {
                f.setAccessible(true);
                obj = f.get(client);
            } catch (Throwable ignored) {
                continue;
            }
            if (obj == null || obj.getClass().isArray() || isSkippableClass(obj.getClass())) continue;

            Field[] fields;
            try {
                fields = obj.getClass().getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (int i = 0; i <= fields.length - 11; i++) {
                if (isInstanceField(fields[i], boolean.class)
                        && isInstanceField(fields[i + 1], int.class)
                        && isInstanceField(fields[i + 2], boolean.class)
                        && isInstanceField(fields[i + 3], boolean.class)
                        && isInstanceField(fields[i + 4], boolean.class)
                        && isInstanceField(fields[i + 5], String.class)
                        && isInstanceField(fields[i + 6], boolean.class)
                        && isInstanceField(fields[i + 7], boolean.class)
                        && isInstanceField(fields[i + 8], float.class)
                        && isInstanceField(fields[i + 9], float.class)
                        && isInstanceField(fields[i + 10], float.class)) {
                    try {
                        optionsObjectField = f;
                        hideGuiField = fields[i];
                        hideGuiField.setAccessible(true);
                        if (i + 14 < fields.length
                                && isInstanceField(fields[i + 11], int.class)
                                && isInstanceField(fields[i + 12], int.class)
                                && isInstanceField(fields[i + 13], String.class)
                                && isInstanceField(fields[i + 14], boolean.class)) {
                            guiScaleField = fields[i + 11];
                            forceUnicodeField = fields[i + 14];
                            guiScaleField.setAccessible(true);
                            forceUnicodeField.setAccessible(true);
                        }
                    } catch (Throwable ignored) {
                    }
                    return;
                }
            }
        }
    }

    private static void ensureTextRenderer(Minecraft client) {
        if (textRendererSearched || client == null) {
            return;
        }
        textRendererSearched = true;
        for (Field f : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            Object val;
            try {
                f.setAccessible(true);
                val = f.get(client);
            } catch (Throwable ignored) {
                continue;
            }
            if (val == null || val.getClass().isArray() || isSkippableClass(val.getClass())) continue;

            Method foundWidth = null;
            Method foundShadow = null;
            Method foundPlain = null;
            try {
                for (Method m : val.getClass().getMethods()) {
                    if (m.getReturnType() != int.class) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && p[0] == String.class) {
                        foundWidth = m;
                    } else if (p.length == 4 && p[0] == String.class && p[1] == float.class && p[2] == float.class && p[3] == int.class) {
                        foundShadow = m;
                    } else if (p.length == 4 && p[0] == String.class && p[1] == int.class && p[2] == int.class && p[3] == int.class) {
                        foundPlain = m;
                    }
                }
            } catch (Throwable ignored) {
                continue;
            }
            if (foundWidth != null && foundShadow != null && foundPlain != null) {
                textRendererObj = val;
                textGetWidthMethod = foundWidth;
                textDrawShadowMethod = foundShadow;
                textDrawPlainMethod = foundPlain;
                return;
            }
        }
    }

    private static boolean isInstanceField(Field field, Class<?> expectedType) {
        return !Modifier.isStatic(field.getModifiers()) && field.getType() == expectedType;
    }

    private static boolean isSkippableClass(Class<?> cls) {
        if (cls == null) return true;
        String name = cls.getName();
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jdk.")
                || name.startsWith("sun.")
                || name.startsWith("com.google.")
                || name.startsWith("org.apache.")
                || name.startsWith("io.netty.")
                || name.startsWith("it.unimi.")
                || name.startsWith("org.lwjgl.")
                || name.startsWith("org.slf4j.");
    }
}
