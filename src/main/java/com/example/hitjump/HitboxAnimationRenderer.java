package com.example.hitjump;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public class HitboxAnimationRenderer {
    // Styles:
    // 0 = Target Ring (Up & Down Sweep + Glow Trail)
    // 1 = Orbiting Halo (Dual Tilted Spinning Rings)
    // 2 = Double Helix Spiral
    // 3 = Lock-On Brackets (Spinning & Pulsing Corners)
    // 4 = Vortex Rings (Rising Stacked Pulses)

    private static final Map<Object, Long> entityFirstSeenMs = new IdentityHashMap<Object, Long>();
    private static final Map<Object, Long> entityLastSeenMs = new IdentityHashMap<Object, Long>();
    private static int pruneTick = 0;

    public static void renderEntityAura(
            Object entity,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            HitJumpConfig config
    ) {
        if (!config.hitboxAnimEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        float fade = 1.0F;
        if (config.hitboxAnimFadeIn && entity != null) {
            Long first = entityFirstSeenMs.get(entity);
            Long last = entityLastSeenMs.get(entity);
            if (first == null || last == null || (now - last.longValue()) > 350L) {
                first = now;
                entityFirstSeenMs.put(entity, first);
            }
            entityLastSeenMs.put(entity, now);
            float t = (now - first.longValue()) / 220.0F;
            if (t < 0.0F) t = 0.0F;
            if (t > 1.0F) t = 1.0F;
            fade = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        }

        pruneTick++;
        if (pruneTick >= 240) {
            pruneTick = 0;
            pruneStaleEntries(now);
        }

        double cx = (minX + maxX) * 0.5D;
        double cz = (minZ + maxZ) * 0.5D;
        double halfW = Math.max(maxX - minX, maxZ - minZ) * 0.5D;
        double height = Math.max(0.2D, maxY - minY);
        double pad = config.hitboxAnimRadiusPad / 100.0D;
        double radius = (halfW * 1.18D) + pad;

        if (config.hitboxAnimFadeIn && fade < 1.0F) {
            radius *= (0.72D + 0.28D * fade);
        }

        double speed = config.hitboxAnimSpeed / 100.0D;
        double timeSec = (now / 1000.0D) * speed;
        float lineWidth = Math.max(1.0F, config.hitboxAnimLineWidth / 10.0F);
        int baseRgb = config.hitboxAnimColor & 0xFFFFFF;
        boolean chroma = config.hitboxAnimChroma;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glLineWidth(lineWidth);

        try {
            switch (config.hitboxAnimStyle) {
                case 0:
                    drawTargetRingSweep(cx, minY, cz, height, radius, timeSec, baseRgb, chroma, fade);
                    break;
                case 1:
                    drawOrbitingHalo(cx, minY, cz, height, radius, timeSec, baseRgb, chroma, fade);
                    break;
                case 2:
                    drawDoubleHelix(cx, minY, cz, height, radius, timeSec, baseRgb, chroma, fade);
                    break;
                case 3:
                    drawLockOnBrackets(cx, minY, cz, height, radius, timeSec, baseRgb, chroma, fade);
                    break;
                case 4:
                    drawVortexRings(cx, minY, cz, height, radius, timeSec, baseRgb, chroma, fade);
                    break;
                default:
                    drawTargetRingSweep(cx, minY, cz, height, radius, timeSec, baseRgb, chroma, fade);
                    break;
            }
        } finally {
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glPopAttrib();
        }
    }

    private static void drawTargetRingSweep(
            double cx, double minY, double cz, double height, double radius,
            double timeSec, int baseRgb, boolean chroma, float fade
    ) {
        double phase = timeSec * 2.6D;
        double normY = 0.5D - 0.5D * Math.cos(phase);
        double ringY = minY + normY * height;
        double velocity = Math.sin(phase);
        double trailSpan = Math.min(height * 0.34D, 0.55D) * Math.abs(velocity);
        double trailY = (velocity >= 0.0D) ? Math.max(minY, ringY - trailSpan) : Math.min(minY + height, ringY + trailSpan);

        int segments = 40;
        double spinOffset = timeSec * 1.4D;

        if (Math.abs(ringY - trailY) > 0.01D) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= segments; i++) {
                double frac = (double) i / (double) segments;
                double ang = frac * Math.PI * 2.0D + spinOffset;
                double px = cx + Math.cos(ang) * radius;
                double pz = cz + Math.sin(ang) * radius;
                int rgb = resolveColor(baseRgb, chroma, frac, timeSec);
                float r = ((rgb >> 16) & 0xFF) / 255.0F;
                float g = ((rgb >> 8) & 0xFF) / 255.0F;
                float b = (rgb & 0xFF) / 255.0F;

                GL11.glColor4f(r, g, b, 0.38F * fade);
                GL11.glVertex3d(px, ringY, pz);
                GL11.glColor4f(r, g, b, 0.0F);
                GL11.glVertex3d(px, trailY, pz);
            }
            GL11.glEnd();
        }

        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            double frac = (double) i / (double) segments;
            double ang = frac * Math.PI * 2.0D + spinOffset;
            setGlColor(resolveColor(baseRgb, chroma, frac, timeSec), 0.96F * fade);
            GL11.glVertex3d(cx + Math.cos(ang) * radius, ringY, cz + Math.sin(ang) * radius);
        }
        GL11.glEnd();
    }

    private static void drawOrbitingHalo(
            double cx, double minY, double cz, double height, double radius,
            double timeSec, int baseRgb, boolean chroma, float fade
    ) {
        double midY = minY + height * 0.52D;
        double tilt = Math.min(height * 0.32D, 0.55D);
        int segments = 44;

        double spin1 = timeSec * 2.4D;
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            double frac = (double) i / (double) segments;
            double ang = frac * Math.PI * 2.0D;
            double rx = cx + Math.cos(ang + spin1) * radius;
            double rz = cz + Math.sin(ang + spin1) * radius;
            double ry = midY + Math.sin(ang) * tilt;
            setGlColor(resolveColor(baseRgb, chroma, frac, timeSec), 0.95F * fade);
            GL11.glVertex3d(rx, ry, rz);
        }
        GL11.glEnd();

        double spin2 = -timeSec * 1.9D;
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            double frac = (double) i / (double) segments;
            double ang = frac * Math.PI * 2.0D;
            double rx = cx + Math.cos(ang + spin2) * (radius * 0.94D);
            double rz = cz + Math.sin(ang + spin2) * (radius * 0.94D);
            double ry = midY - Math.sin(ang) * (tilt * 0.85D);
            setGlColor(resolveColor(baseRgb, chroma, frac + 0.5D, timeSec), 0.78F * fade);
            GL11.glVertex3d(rx, ry, rz);
        }
        GL11.glEnd();

        double footY = minY + 0.04D;
        double pulseR = radius * (0.96D + 0.06D * Math.sin(timeSec * 3.5D));
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            double frac = (double) i / (double) segments;
            double ang = frac * Math.PI * 2.0D + spin1 * 0.5D;
            setGlColor(resolveColor(baseRgb, chroma, frac, timeSec), 0.60F * fade);
            GL11.glVertex3d(cx + Math.cos(ang) * pulseR, footY, cz + Math.sin(ang) * pulseR);
        }
        GL11.glEnd();
    }

    private static void drawDoubleHelix(
            double cx, double minY, double cz, double height, double radius,
            double timeSec, int baseRgb, boolean chroma, float fade
    ) {
        int steps = 44;
        double turns = 2.0D;
        double spin = timeSec * 3.2D;

        for (int strand = 0; strand < 2; strand++) {
            double strandOffset = strand * Math.PI;
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= steps; i++) {
                double frac = (double) i / (double) steps;
                double ang = frac * Math.PI * 2.0D * turns + spin + strandOffset;
                double py = minY + frac * height;
                double taper = 0.85D + 0.15D * Math.sin(frac * Math.PI);
                double px = cx + Math.cos(ang) * radius * taper;
                double pz = cz + Math.sin(ang) * radius * taper;

                float edgeAlpha = (float) Math.sin(frac * Math.PI);
                float a = Math.max(0.25F, edgeAlpha) * 0.95F * fade;
                setGlColor(resolveColor(baseRgb, chroma, frac + strand * 0.5D, timeSec), a);
                GL11.glVertex3d(px, py, pz);
            }
            GL11.glEnd();
        }
    }

    private static void drawLockOnBrackets(
            double cx, double minY, double cz, double height, double radius,
            double timeSec, int baseRgb, boolean chroma, float fade
    ) {
        double spin = timeSec * 1.6D;
        double pulse = 1.0D + 0.08D * Math.sin(timeSec * 4.2D);
        double r = radius * pulse;
        double arcSpan = Math.PI * 0.24D;
        double legH = Math.min(height * 0.22D, 0.34D);
        int arcSteps = 8;

        for (int corner = 0; corner < 4; corner++) {
            double baseAng = spin + corner * (Math.PI * 0.5D);
            setGlColor(resolveColor(baseRgb, chroma, corner * 0.25D, timeSec), 0.95F * fade);

            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int s = 0; s <= arcSteps; s++) {
                double a = baseAng - arcSpan * 0.5D + (arcSpan * s / arcSteps);
                GL11.glVertex3d(cx + Math.cos(a) * r, minY + height, cz + Math.sin(a) * r);
            }
            GL11.glEnd();

            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int s = 0; s <= arcSteps; s++) {
                double a = baseAng - arcSpan * 0.5D + (arcSpan * s / arcSteps);
                GL11.glVertex3d(cx + Math.cos(a) * r, minY + 0.02D, cz + Math.sin(a) * r);
            }
            GL11.glEnd();

            double vx = cx + Math.cos(baseAng) * r;
            double vz = cz + Math.sin(baseAng) * r;
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(vx, minY + height, vz);
            GL11.glVertex3d(vx, minY + height - legH, vz);
            GL11.glVertex3d(vx, minY + 0.02D, vz);
            GL11.glVertex3d(vx, minY + 0.02D + legH, vz);
            GL11.glEnd();
        }
    }

    private static void drawVortexRings(
            double cx, double minY, double cz, double height, double radius,
            double timeSec, int baseRgb, boolean chroma, float fade
    ) {
        int ringCount = 3;
        int segments = 32;
        for (int k = 0; k < ringCount; k++) {
            double cycle = (timeSec * 0.75D + (double) k / ringCount);
            cycle = cycle - Math.floor(cycle);
            double py = minY + cycle * height;
            double ringR = radius * (0.78D + 0.28D * Math.sin(cycle * Math.PI));
            float a = (float) Math.sin(cycle * Math.PI) * 0.92F * fade;
            if (a < 0.05F) continue;

            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < segments; i++) {
                double frac = (double) i / (double) segments;
                double ang = frac * Math.PI * 2.0D + timeSec * (k % 2 == 0 ? 2.0D : -2.0D);
                setGlColor(resolveColor(baseRgb, chroma, frac + cycle, timeSec), a);
                GL11.glVertex3d(cx + Math.cos(ang) * ringR, py, cz + Math.sin(ang) * ringR);
            }
            GL11.glEnd();
        }
    }

    private static int resolveColor(int baseRgb, boolean chroma, double offset, double timeSec) {
        if (!chroma) {
            return baseRgb;
        }
        double h = (timeSec * 0.22D) - offset;
        h = h - Math.floor(h);
        return Color.HSBtoRGB((float) h, 0.80F, 1.0F) & 0xFFFFFF;
    }

    private static void setGlColor(int rgb, float alpha) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        GL11.glColor4f(r, g, b, alpha);
    }

	private static void pruneStaleEntries(long now) {
        Iterator it = entityLastSeenMs.keySet().iterator();
        while (it.hasNext()) {
            Object key = it.next();
            Long lastVal = entityLastSeenMs.get(key);
            if (lastVal != null && now - lastVal.longValue() > 2000L) {
                entityFirstSeenMs.remove(key);
                it.remove();
            }
        }
    }
}
