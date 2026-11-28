package com.example.hitjump;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

public class CompanionPetsRenderer {
    // Black Baby Bunny state
    private static boolean bunnyInit = false;
    private static double bunnyX, bunnyY, bunnyZ;
    private static double bunnyPrevX, bunnyPrevY, bunnyPrevZ;
    private static float bunnyYaw, bunnyPrevYaw;
    private static float bunnyHopPhase, bunnyPrevHopPhase;
    private static float bunnyHopHeight, bunnyPrevHopHeight;

    // Small Dragon state
    private static boolean dragonInit = false;
    private static double dragonX, dragonY, dragonZ;
    private static double dragonPrevX, dragonPrevY, dragonPrevZ;
    private static float dragonYaw, dragonPrevYaw;
    private static float dragonBank, dragonPrevBank;

    private static Object lastWorldRef = null;

    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.world == null) {
            bunnyInit = false;
            dragonInit = false;
            lastWorldRef = null;
            return;
        }
        if (client.world != lastWorldRef) {
            lastWorldRef = client.world;
            bunnyInit = false;
            dragonInit = false;
        }

        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.petBunnyEnabled && !config.petDragonEnabled) {
            return;
        }

        Object player = client.player;
        HitJumpMod.ensureEntityMotionFields(player);
        if (HitJumpMod.entityXField == null || HitJumpMod.entityYawField == null) {
            return;
        }

        double px, py, pz;
        float pYaw;
        try {
            px = HitJumpMod.entityXField.getDouble(player);
            py = HitJumpMod.entityYField.getDouble(player);
            pz = HitJumpMod.entityZField.getDouble(player);
            pYaw = HitJumpMod.entityYawField.getFloat(player);
        } catch (Throwable ignored) {
            return;
        }

        double yawRad = Math.toRadians(pYaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -forwardZ;
        double rightZ = forwardX;

        if (config.petBunnyEnabled) {
            tickBunny(px, py, pz, pYaw, forwardX, forwardZ, rightX, rightZ);
        }
        if (config.petDragonEnabled) {
            tickDragon(px, py, pz, pYaw, forwardX, forwardZ, rightX, rightZ);
        }
    }

    private static void tickBunny(
            double px, double py, double pz, float pYaw,
            double forwardX, double forwardZ, double rightX, double rightZ
    ) {
        double targetX = px - forwardX * 1.35D - rightX * 0.95D;
        double targetY = py;
        double targetZ = pz - forwardZ * 1.35D - rightZ * 0.95D;

        if (!bunnyInit) {
            bunnyX = bunnyPrevX = targetX;
            bunnyY = bunnyPrevY = targetY;
            bunnyZ = bunnyPrevZ = targetZ;
            bunnyYaw = bunnyPrevYaw = pYaw;
            bunnyHopPhase = bunnyPrevHopPhase = 0.0F;
            bunnyHopHeight = bunnyPrevHopHeight = 0.0F;
            bunnyInit = true;
            return;
        }

        bunnyPrevX = bunnyX;
        bunnyPrevY = bunnyY;
        bunnyPrevZ = bunnyZ;
        bunnyPrevYaw = bunnyYaw;
        bunnyPrevHopPhase = bunnyHopPhase;
        bunnyPrevHopHeight = bunnyHopHeight;

        double dx = targetX - bunnyX;
        double dy = targetY - bunnyY;
        double dz = targetZ - bunnyZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        // Snap cleanly if player pearls or teleports far away
        if (distSq > 196.0D) {
            bunnyX = bunnyPrevX = targetX;
            bunnyY = bunnyPrevY = targetY;
            bunnyZ = bunnyPrevZ = targetZ;
            bunnyYaw = bunnyPrevYaw = pYaw;
            bunnyHopHeight = bunnyPrevHopHeight = 0.0F;
            return;
        }

        double horizDist = Math.sqrt(dx * dx + dz * dz);
        boolean moving = horizDist > 0.14D || Math.abs(dy) > 0.25D;

        if (horizDist > 0.08D) {
            double moveSpeed = Math.min(horizDist * 0.32D, 0.55D);
            bunnyX += (dx / horizDist) * moveSpeed;
            bunnyZ += (dz / horizDist) * moveSpeed;

            float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            bunnyYaw = lerpAngle(bunnyYaw, desiredYaw, 0.35F);
        } else {
            bunnyYaw = lerpAngle(bunnyYaw, pYaw, 0.15F);
        }

        bunnyY += dy * 0.30D;

        if (moving) {
            bunnyHopPhase += 0.55F;
            float hopArc = (float) Math.abs(Math.sin(bunnyHopPhase));
            bunnyHopHeight = hopArc * 0.34F;
        } else {
            bunnyHopHeight *= 0.60F;
            if (bunnyHopHeight < 0.005F) {
                bunnyHopHeight = 0.0F;
            }
        }
    }

    private static void tickDragon(
            double px, double py, double pz, float pYaw,
            double forwardX, double forwardZ, double rightX, double rightZ
    ) {
        double orbitSec = System.currentTimeMillis() / 1000.0D;
        double sway = Math.sin(orbitSec * 1.4D) * 0.28D;
        double targetX = px - forwardX * 1.45D + rightX * (1.10D + sway);
        double targetY = py + 2.05D + Math.sin(orbitSec * 2.6D) * 0.16D;
        double targetZ = pz - forwardZ * 1.45D + rightZ * (1.10D + sway);

        if (!dragonInit) {
            dragonX = dragonPrevX = targetX;
            dragonY = dragonPrevY = targetY;
            dragonZ = dragonPrevZ = targetZ;
            dragonYaw = dragonPrevYaw = pYaw;
            dragonBank = dragonPrevBank = 0.0F;
            dragonInit = true;
            return;
        }

        dragonPrevX = dragonX;
        dragonPrevY = dragonY;
        dragonPrevZ = dragonZ;
        dragonPrevYaw = dragonYaw;
        dragonPrevBank = dragonBank;

        double dx = targetX - dragonX;
        double dy = targetY - dragonY;
        double dz = targetZ - dragonZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > 256.0D) {
            dragonX = dragonPrevX = targetX;
            dragonY = dragonPrevY = targetY;
            dragonZ = dragonPrevZ = targetZ;
            dragonYaw = dragonPrevYaw = pYaw;
            dragonBank = dragonPrevBank = 0.0F;
            return;
        }

        double horizDist = Math.sqrt(dx * dx + dz * dz);
        dragonX += dx * 0.20D;
        dragonY += dy * 0.18D;
        dragonZ += dz * 0.20D;

        float desiredYaw = (horizDist > 0.12D)
                ? (float) Math.toDegrees(Math.atan2(-dx, dz))
                : pYaw;
        float yawDelta = wrapDegrees(desiredYaw - dragonYaw);
        dragonYaw = wrapDegrees(dragonYaw + yawDelta * 0.24F);

        float targetBank = clampFloat(yawDelta * 0.75F, -26.0F, 26.0F);
        dragonBank += (targetBank - dragonBank) * 0.25F;
    }

    public static void render(Minecraft client, float tickDelta, double camX, double camY, double camZ) {
        if (client == null || client.player == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.petBunnyEnabled && !config.petDragonEnabled) {
            return;
        }
        if (!config.petsShowFirstPerson && HitJumpMod.getPerspective(client) == 0) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            if (config.petBunnyEnabled && bunnyInit) {
                double bx = bunnyPrevX + (bunnyX - bunnyPrevX) * tickDelta;
                double by = bunnyPrevY + (bunnyY - bunnyPrevY) * tickDelta;
                double bz = bunnyPrevZ + (bunnyZ - bunnyPrevZ) * tickDelta;
                float hop = bunnyPrevHopHeight + (bunnyHopHeight - bunnyPrevHopHeight) * tickDelta;
                float yaw = lerpAngle(bunnyPrevYaw, bunnyYaw, tickDelta);
                float scale = (config.petBunnyScale / 100.0F) * 0.72F;

                GL11.glPushMatrix();
                GL11.glTranslated(bx - camX, by + hop - camY, bz - camZ);
                GL11.glRotatef(-yaw, 0.0F, 1.0F, 0.0F);
                GL11.glScalef(scale, scale, scale);
                drawBlackBabyBunny(hop);
                GL11.glPopMatrix();
            }

            if (config.petDragonEnabled && dragonInit) {
                double dx = dragonPrevX + (dragonX - dragonPrevX) * tickDelta;
                double dy = dragonPrevY + (dragonY - dragonPrevY) * tickDelta;
                double dz = dragonPrevZ + (dragonZ - dragonPrevZ) * tickDelta;
                float yaw = lerpAngle(dragonPrevYaw, dragonYaw, tickDelta);
                float bank = dragonPrevBank + (dragonBank - dragonPrevBank) * tickDelta;
                float scale = (config.petDragonScale / 100.0F) * 0.58F;

                GL11.glPushMatrix();
                GL11.glTranslated(dx - camX, dy - camY, dz - camZ);
                GL11.glRotatef(-yaw, 0.0F, 1.0F, 0.0F);
                GL11.glRotatef(bank, 0.0F, 0.0F, 1.0F);
                GL11.glScalef(scale, scale, scale);
                drawSmallDragon(System.currentTimeMillis() / 1000.0D);
                GL11.glPopMatrix();
            }
        } catch (Throwable ignored) {
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void drawBlackBabyBunny(float hopHeight) {
        // Pitch slightly upward when hopping
        float bodyTilt = -hopHeight * 42.0F;
        GL11.glRotatef(bodyTilt, 1.0F, 0.0F, 0.0F);

        // Fur palette: deep charcoal-black with subtle highlight tones so details pop in 3D
        int furDark = 0x17171E;
        int furMid = 0x22222C;
        int earInner = 0x35252D;
        int nosePink = 0xD97C8E;
        int eyeDark = 0x09090D;
        int eyeGlint = 0xE8EEF8;
        int tailFluff = 0x2B2B36;

        // Torso (chunky baby proportions)
        drawShadedBox(-0.18, 0.12, -0.24, 0.18, 0.40, 0.22, furDark);

        // Fluffy tail
        drawShadedBox(-0.08, 0.18, -0.34, 0.08, 0.34, -0.22, tailFluff);

        // Back haunches & paws (kick slightly during hop)
        float legKick = hopHeight * 55.0F;
        GL11.glPushMatrix();
        GL11.glTranslated(0.0, 0.16, -0.14);
        GL11.glRotatef(legKick, 1.0F, 0.0F, 0.0F);
        drawShadedBox(-0.22, -0.12, -0.10, -0.10, 0.08, 0.10, furMid);
        drawShadedBox(0.10, -0.12, -0.10, 0.22, 0.08, 0.10, furMid);
        drawShadedBox(-0.21, -0.16, -0.06, -0.11, -0.09, 0.16, furDark);
        drawShadedBox(0.11, -0.16, -0.06, 0.21, -0.09, 0.16, furDark);
        GL11.glPopMatrix();

        // Front paws
        GL11.glPushMatrix();
        GL11.glTranslated(0.0, 0.15, 0.14);
        GL11.glRotatef(-legKick * 0.7F, 1.0F, 0.0F, 0.0F);
        drawShadedBox(-0.14, -0.15, -0.05, -0.05, 0.02, 0.06, furMid);
        drawShadedBox(0.05, -0.15, -0.05, 0.14, 0.02, 0.06, furMid);
        GL11.glPopMatrix();

        // Oversized cute baby head
        GL11.glPushMatrix();
        GL11.glTranslated(0.0, 0.38, 0.22);
        GL11.glRotatef(-bodyTilt * 0.65F, 1.0F, 0.0F, 0.0F);
        drawShadedBox(-0.19, -0.04, -0.14, 0.19, 0.30, 0.22, furDark);

        // Pink nose
        drawShadedBox(-0.045, 0.06, 0.21, 0.045, 0.12, 0.25, nosePink);

        // Eyes with tiny white specular glint
        drawShadedBox(-0.195, 0.13, 0.08, -0.15, 0.21, 0.17, eyeDark);
        drawShadedBox(0.15, 0.13, 0.08, 0.195, 0.21, 0.17, eyeDark);
        drawShadedBox(-0.198, 0.175, 0.13, -0.185, 0.205, 0.165, eyeGlint);
        drawShadedBox(0.185, 0.175, 0.13, 0.198, 0.205, 0.165, eyeGlint);

        // Tall floppy baby bunny ears
        float earWiggle = -10.0F - hopHeight * 32.0F;
        GL11.glPushMatrix();
        GL11.glTranslated(-0.10, 0.29, -0.02);
        GL11.glRotatef(earWiggle, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(-10.0F, 0.0F, 0.0F, 1.0F);
        drawShadedBox(-0.05, 0.0, -0.04, 0.05, 0.32, 0.04, furDark);
        drawShadedBox(-0.03, 0.04, 0.035, 0.03, 0.26, 0.046, earInner);
        GL11.glPopMatrix();

        GL11.glPushMatrix();
        GL11.glTranslated(0.10, 0.29, -0.02);
        GL11.glRotatef(earWiggle, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(10.0F, 0.0F, 0.0F, 1.0F);
        drawShadedBox(-0.05, 0.0, -0.04, 0.05, 0.32, 0.04, furDark);
        drawShadedBox(-0.03, 0.04, 0.035, 0.03, 0.26, 0.046, earInner);
        GL11.glPopMatrix();

        GL11.glPopMatrix();
    }

    private static void drawSmallDragon(double timeSec) {
        int scaleDark = 0x15131D;
        int scaleMid = 0x211D2E;
        int ridgeGray = 0x7A7A8A;
        int wingMembrane = 0x36204F;
        int eyeGlow = 0xE255FF;

        float flap = (float) Math.sin(timeSec * 8.5D);
        float tipFlap = (float) Math.sin(timeSec * 8.5D - 0.85D);
        float bodyBob = (float) Math.sin(timeSec * 8.5D - 1.2D) * 0.04F;

        GL11.glTranslatef(0.0F, bodyBob, 0.0F);

        // Body & dorsal spine ridges
        drawShadedBox(-0.20, -0.14, -0.44, 0.20, 0.18, 0.38, scaleDark);
        drawShadedBox(-0.04, 0.18, -0.32, 0.04, 0.27, -0.16, ridgeGray);
        drawShadedBox(-0.04, 0.18, -0.08, 0.04, 0.28, 0.08, ridgeGray);
        drawShadedBox(-0.04, 0.18, 0.16, 0.04, 0.26, 0.30, ridgeGray);

        // Articulated Neck + Head + Glowing Ender Eyes + Horns
        float neckBob = (float) Math.sin(timeSec * 4.2D) * 6.0F;
        GL11.glPushMatrix();
        GL11.glTranslated(0.0, 0.06, 0.36);
        GL11.glRotatef(neckBob, 1.0F, 0.0F, 0.0F);
        drawShadedBox(-0.09, -0.08, 0.0, 0.09, 0.09, 0.26, scaleMid);
        drawShadedBox(-0.03, 0.09, 0.06, 0.03, 0.15, 0.18, ridgeGray);

        GL11.glTranslated(0.0, 0.02, 0.25);
        // Cranium & snout
        drawShadedBox(-0.14, -0.11, 0.0, 0.14, 0.14, 0.26, scaleDark);
        drawShadedBox(-0.09, -0.06, 0.24, 0.09, 0.06, 0.44, scaleMid);
        // Lower jaw
        drawShadedBox(-0.08, -0.11, 0.24, 0.08, -0.06, 0.41, scaleDark);
        // Horns
        drawShadedBox(-0.11, 0.13, 0.02, -0.05, 0.22, 0.12, ridgeGray);
        drawShadedBox(0.05, 0.13, 0.02, 0.11, 0.22, 0.12, ridgeGray);
        // Glowing purple eyes
        drawShadedBox(-0.146, 0.01, 0.14, -0.08, 0.07, 0.23, eyeGlow);
        drawShadedBox(0.08, 0.01, 0.14, 0.146, 0.07, 0.23, eyeGlow);
        GL11.glPopMatrix();

        // Tucked flying legs
        drawShadedBox(-0.22, -0.26, 0.14, -0.12, -0.08, 0.30, scaleMid);
        drawShadedBox(0.12, -0.26, 0.14, 0.22, -0.08, 0.30, scaleMid);
        drawShadedBox(-0.22, -0.28, -0.34, -0.12, -0.08, -0.14, scaleMid);
        drawShadedBox(0.12, -0.28, -0.34, 0.22, -0.08, -0.14, scaleMid);

        // Articulated Flapping Wings (Left + Right)
        for (int side = -1; side <= 1; side += 2) {
            GL11.glPushMatrix();
            GL11.glTranslated(side * 0.18, 0.12, 0.14);
            GL11.glRotatef(side * (flap * 34.0F + 8.0F), 0.0F, 0.0F, 1.0F);
            // Inner wing bone & membrane
            double ix0 = (side > 0) ? 0.0 : -0.68;
            double ix1 = (side > 0) ? 0.68 : 0.0;
            drawShadedBox(ix0, -0.04, -0.06, ix1, 0.04, 0.06, scaleDark);
            drawShadedBox(ix0, -0.015, -0.52, ix1, 0.015, 0.0, wingMembrane);

            // Outer wing joint & tip
            GL11.glTranslated(side * 0.68, 0.0, 0.0);
            GL11.glRotatef(side * (tipFlap * 26.0F - 10.0F), 0.0F, 0.0F, 1.0F);
            double ox0 = (side > 0) ? 0.0 : -0.62;
            double ox1 = (side > 0) ? 0.62 : 0.0;
            drawShadedBox(ox0, -0.03, -0.04, ox1, 0.03, 0.04, scaleMid);
            drawShadedBox(ox0, -0.012, -0.42, ox1, 0.012, 0.0, wingMembrane);
            GL11.glPopMatrix();
        }

        // 5-Segment Undulating Tail
        GL11.glPushMatrix();
        GL11.glTranslated(0.0, 0.02, -0.44);
        for (int s = 0; s < 5; s++) {
            float segYaw = (float) Math.sin(timeSec * 5.0D - s * 0.7D) * 10.0F;
            float segPitch = (float) Math.cos(timeSec * 4.5D - s * 0.6D) * 6.0F;
            GL11.glRotatef(segYaw, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(segPitch, 1.0F, 0.0F, 0.0F);
            double w = 0.11 - s * 0.014;
            drawShadedBox(-w, -w, -0.22, w, w, 0.0, (s % 2 == 0) ? scaleDark : scaleMid);
            if (s < 4) {
                drawShadedBox(-0.025, w, -0.16, 0.025, w + 0.06, -0.06, ridgeGray);
            }
            GL11.glTranslated(0.0, 0.0, -0.21);
        }
        GL11.glPopMatrix();
    }

    private static void drawShadedBox(double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;

        GL11.glBegin(GL11.GL_QUADS);
        // Top (+Y) brightest
        GL11.glColor4f(r, g, b, 1.0F);
        GL11.glVertex3d(x0, y1, z0); GL11.glVertex3d(x0, y1, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y1, z0);

        // Bottom (-Y) darkest
        GL11.glColor4f(r * 0.58F, g * 0.58F, b * 0.58F, 1.0F);
        GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x1, y0, z0);
        GL11.glVertex3d(x1, y0, z1); GL11.glVertex3d(x0, y0, z1);

        // Front / Back (+Z / -Z)
        GL11.glColor4f(r * 0.86F, g * 0.86F, b * 0.86F, 1.0F);
        GL11.glVertex3d(x0, y0, z1); GL11.glVertex3d(x1, y0, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x0, y1, z1);

        GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x0, y1, z0);
        GL11.glVertex3d(x1, y1, z0); GL11.glVertex3d(x1, y0, z0);

        // Left / Right (-X / +X)
        GL11.glColor4f(r * 0.72F, g * 0.72F, b * 0.72F, 1.0F);
        GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x0, y0, z1);
        GL11.glVertex3d(x0, y1, z1); GL11.glVertex3d(x0, y1, z0);

        GL11.glVertex3d(x1, y0, z0); GL11.glVertex3d(x1, y1, z0);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y0, z1);
        GL11.glEnd();
    }

    private static float lerpAngle(float from, float to, float alpha) {
        return wrapDegrees(from + wrapDegrees(to - from) * alpha);
    }

    private static float wrapDegrees(float deg) {
        deg %= 360.0F;
        if (deg >= 180.0F) deg -= 360.0F;
        if (deg < -180.0F) deg += 360.0F;
        return deg;
    }

    private static float clampFloat(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
