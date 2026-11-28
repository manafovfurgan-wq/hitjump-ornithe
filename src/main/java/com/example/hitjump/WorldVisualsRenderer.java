package com.example.hitjump;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WorldVisualsRenderer {
    private static final FloatBuffer PROJECTION_MATRIX = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final FloatBuffer MODELVIEW_MATRIX = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static boolean cameraCapturedThisFrame = false;

    private static boolean boxFieldsSearched = false;
    private static Field boxMinXField = null;
    private static Field boxMinYField = null;
    private static Field boxMinZField = null;
    private static Field boxMaxXField = null;
    private static Field boxMaxYField = null;
    private static Field boxMaxZField = null;

    private static boolean nameAccessSearched = false;
    private static Field dataWatcherField = null;
    private static Method dataWatcherGetByteMethod = null;
    private static Method dataWatcherGetStringMethod = null;
    private static Field gameProfileField = null;
    private static Method gameProfileGetNameMethod = null;
    private static Method entityGetDisplayNameMethod = null;
    private static Method entityGetInventoryMethod = null;
    private static Method textFormattedMethod = null;
    private static boolean textFormattedSearched = false;

    private static boolean itemStackNbtSearched = false;
    private static Field itemStackNbtField = null;

    private static boolean tntClassChecked = false;
    private static Class<?> tntEntityClass = null;
    private static Field tntFuseField = null;

    private static boolean textRendererSearched = false;
    private static Object textRendererObj = null;
    private static Method textGetWidthMethod = null;
    private static Method textDrawShadowMethod = null;

    // Targeted block reflection cache
    private static Field hitResultField = null;
    private static Field hitTypeField = null;
    private static Field hitSideField = null;
    private static Field hitBlockPosField = null;
    private static Field blockPosXField = null;
    private static Field blockPosYField = null;
    private static Field blockPosZField = null;
    private static boolean blockBoxMethodSearched = false;
    private static Method worldGetBlockStateMethod = null;
    private static Method stateGetBlockMethod = null;
    private static Method blockSelectedBoxMethod = null;

    private static class TagRenderEntry {
        final String distanceLine, nameLine, armorLine, tntLine;
        final int tntColor;
        final double relX, relY, relZ, distSq;

        TagRenderEntry(String distanceLine, String nameLine, String armorLine, String tntLine,
                       int tntColor, double relX, double relY, double relZ, double distSq) {
            this.distanceLine = distanceLine;
            this.nameLine = nameLine;
            this.armorLine = armorLine;
            this.tntLine = tntLine;
            this.tntColor = tntColor;
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.distSq = distSq;
        }
    }

    private static class HitOverlayEntry {
        final double minX, minY, minZ, maxX, maxY, maxZ;
        final float alphaScale;

        HitOverlayEntry(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float alphaScale) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            this.alphaScale = alphaScale;
        }
    }

    public static void captureCameraMatrices() {
        PROJECTION_MATRIX.clear();
        MODELVIEW_MATRIX.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION_MATRIX);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW_MATRIX);
        cameraCapturedThisFrame = true;
    }

    public static void onRenderWorldTail(Minecraft client, float tickDelta) {
        if (!cameraCapturedThisFrame || client == null || client.player == null || client.world == null) {
            return;
        }
        cameraCapturedThisFrame = false;

        HitJumpConfig config = HitJumpConfig.getInstance();
        boolean needHitboxPass = config.wallHitboxesEnabled || config.hitboxEyeLineEnabled;
        boolean needTagPass = config.wallNametagsEnabled || config.armorEnchantInfoEnabled || config.tntTimerEnabled;
        boolean needHitColorPass = config.customHitColorEnabled;
        boolean needBlockOutlinePass = config.blockOutlineEnabled && config.blockOutlineOpacity > 0;
        boolean needBlockOverlayPass = config.blockOverlayEnabled && config.blockOverlayOpacity > 0;

        if (!needHitboxPass && !needTagPass && !needHitColorPass && !needBlockOutlinePass && !needBlockOverlayPass) {
            return;
        }

        Object player = client.player;
        HitJumpMod.ensureEntityMotionFields(player);
        ensureNameAccessFields(player);
        ensureTextRenderer(client);

        if (HitJumpMod.entityXField == null || HitJumpMod.entityPrevXField == null || HitJumpMod.entityBoxField == null) {
            return;
        }

        Class<?> playerBaseClass = HitJumpMod.getPlayerBaseClass(player);
        Class<?> livingBaseClass = HitJumpMod.getLivingBaseClass(player);
        Class<?> entityBaseClass = HitJumpMod.getEntityBaseClass(player);

        double camX, camY, camZ;
        float viewYaw, viewPitch;
        try {
            double pPrevX = HitJumpMod.entityPrevXField.getDouble(player);
            double pPrevY = HitJumpMod.entityPrevYField.getDouble(player);
            double pPrevZ = HitJumpMod.entityPrevZField.getDouble(player);
            camX = pPrevX + (HitJumpMod.entityXField.getDouble(player) - pPrevX) * tickDelta;
            camY = pPrevY + (HitJumpMod.entityYField.getDouble(player) - pPrevY) * tickDelta;
            camZ = pPrevZ + (HitJumpMod.entityZField.getDouble(player) - pPrevZ) * tickDelta;

            float pPrevYaw = HitJumpMod.entityPrevYawField.getFloat(player);
            float pYaw = HitJumpMod.entityYawField.getFloat(player);
            float pPrevPitch = HitJumpMod.entityPrevPitchField.getFloat(player);
            float pPitch = HitJumpMod.entityPitchField.getFloat(player);
            viewYaw = pPrevYaw + (pYaw - pPrevYaw) * tickDelta;
            viewPitch = pPrevPitch + (pPitch - pPrevPitch) * tickDelta;

            if (HitJumpMod.getPerspective(client) == 2) {
                viewYaw += 180.0F;
                viewPitch = -viewPitch;
            }
        } catch (Throwable ignored) {
            return;
        }

        List<?> entities = (needHitboxPass || needTagPass || needHitColorPass) ? getLoadedEntityList(client) : null;
        double hitboxMaxDistSq = (double) config.hitboxRange * (double) config.hitboxRange;
        double nametagMaxDistSq = (double) config.nametagRange * (double) config.nametagRange;

        List<TagRenderEntry> tagsToRender = needTagPass ? new ArrayList<TagRenderEntry>() : null;
        List<HitOverlayEntry> hitOverlays = needHitColorPass ? new ArrayList<HitOverlayEntry>() : null;

        float invisR = ((config.invisHitboxColor >> 16) & 0xFF) / 255.0F;
        float invisG = ((config.invisHitboxColor >> 8) & 0xFF) / 255.0F;
        float invisB = (config.invisHitboxColor & 0xFF) / 255.0F;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        PROJECTION_MATRIX.clear();
        GL11.glLoadMatrix(PROJECTION_MATRIX);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        MODELVIEW_MATRIX.clear();
        GL11.glLoadMatrix(MODELVIEW_MATRIX);

        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);

            // 0. Custom Block Overlay & Outline
            if (needBlockOutlinePass || needBlockOverlayPass) {
                renderCustomBlockHighlights(client, config, entityBaseClass, camX, camY, camZ, needBlockOutlinePass, needBlockOverlayPass);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            // 1. Entity Hitboxes & Separated Eye Line (respects See Through Walls)
            if (entities != null && !entities.isEmpty()) {
                if (config.hitboxThroughWalls) {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                } else {
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glDepthFunc(GL11.GL_LEQUAL);
                }
                if (needHitboxPass) {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glLineWidth(1.5F);
                }

                int count = entities.size();
                for (int i = 0; i < count; i++) {
                    Object entity;
                    try {
                        entity = entities.get(i);
                    } catch (Throwable ignored) {
                        break;
                    }
                    if (entity == null || entity == player || HitJumpMod.isEntityDead(entity)) {
                        continue;
                    }

                    boolean isPlayer = playerBaseClass != null && playerBaseClass.isInstance(entity);
                    boolean isLiving = livingBaseClass != null && livingBaseClass.isInstance(entity);
                    boolean isTnt = isPrimedTnt(entity, entityBaseClass, livingBaseClass);

                    double ex, ey, ez, interpX, interpY, interpZ;
                    Object boxObj;
                    try {
                        ex = HitJumpMod.entityXField.getDouble(entity);
                        ey = HitJumpMod.entityYField.getDouble(entity);
                        ez = HitJumpMod.entityZField.getDouble(entity);

                        double prevX = (HitJumpMod.entityLastTickXField != null)
                                ? HitJumpMod.entityLastTickXField.getDouble(entity)
                                : HitJumpMod.entityPrevXField.getDouble(entity);
                        double prevY = (HitJumpMod.entityLastTickYField != null)
                                ? HitJumpMod.entityLastTickYField.getDouble(entity)
                                : HitJumpMod.entityPrevYField.getDouble(entity);
                        double prevZ = (HitJumpMod.entityLastTickZField != null)
                                ? HitJumpMod.entityLastTickZField.getDouble(entity)
                                : HitJumpMod.entityPrevZField.getDouble(entity);

                        interpX = prevX + (ex - prevX) * tickDelta;
                        interpY = prevY + (ey - prevY) * tickDelta;
                        interpZ = prevZ + (ez - prevZ) * tickDelta;
                        boxObj = HitJumpMod.entityBoxField.get(entity);
                    } catch (Throwable ignored) {
                        continue;
                    }

                    double relX = interpX - camX;
                    double relY = interpY - camY;
                    double relZ = interpZ - camZ;
                    double distSq = relX * relX + relY * relY + relZ * relZ;

                    double boxHeight = 1.8D;
                    boolean hasValidBox = false;
                    double bMinX = 0, bMinY = 0, bMinZ = 0, bMaxX = 0, bMaxY = 0, bMaxZ = 0;

                    if (boxObj != null) {
                        ensureBoxFields(boxObj);
                        if (boxMinXField != null) {
                            try {
                                double shiftX = interpX - ex - camX;
                                double shiftY = interpY - ey - camY;
                                double shiftZ = interpZ - ez - camZ;
                                bMinX = boxMinXField.getDouble(boxObj) + shiftX;
                                bMinY = boxMinYField.getDouble(boxObj) + shiftY;
                                bMinZ = boxMinZField.getDouble(boxObj) + shiftZ;
                                bMaxX = boxMaxXField.getDouble(boxObj) + shiftX;
                                bMaxY = boxMaxYField.getDouble(boxObj) + shiftY;
                                bMaxZ = boxMaxZField.getDouble(boxObj) + shiftZ;
                                double bw = bMaxX - bMinX;
                                double bh = bMaxY - bMinY;
                                if (bh > 0.01D) boxHeight = bh;
                                hasValidBox = (bw > 0.01D && bh > 0.01D);
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    if (needHitboxPass && hasValidBox && distSq <= hitboxMaxDistSq) {
                        boolean targetMatch = (config.hitboxTargetMode == 1) ? isPlayer
                                : (config.hitboxTargetMode == 0 ? isLiving : true);
                        if (targetMatch) {
                            boolean invisible = isEntityInvisible(entity);
                            drawHitboxAndEyeLine(
                                    bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ,
                                    isPlayer, isLiving,
                                    config.wallHitboxesEnabled,
                                    config.hitboxEyeLineEnabled,
                                    invisible && config.invisHitboxEnabled,
                                    invisR, invisG, invisB
                            );
                        }
                    }

                    if (hitOverlays != null && isLiving && hasValidBox && distSq <= hitboxMaxDistSq) {
                        int ht = HitJumpMod.getHurtTime(entity);
                        if (ht > 0) {
                            float fade = Math.min(1.0F, ht / 10.0F);
                            hitOverlays.add(new HitOverlayEntry(
                                    bMinX - 0.015D, bMinY - 0.015D, bMinZ - 0.015D,
                                    bMaxX + 0.015D, bMaxY + 0.015D, bMaxZ + 0.015D,
                                    fade
                            ));
                        }
                    }

                    if (tagsToRender != null && distSq <= nametagMaxDistSq) {
                        if (isTnt && config.tntTimerEnabled) {
                            int fuse = getTntFuse(entity);
                            if (fuse >= 0 && fuse <= 200) {
                                float secs = Math.max(0.0F, (fuse - tickDelta) / 20.0F);
                                String tntStr = String.format("TNT %.1fs", secs);
                                int color = (secs > 2.5F) ? 0xFF55FF55 : (secs > 1.0F ? 0xFFFFFF55 : 0xFFFF5555);
                                double tagY = hasValidBox ? (bMaxY + 0.35D) : (relY + 1.15D);
                                tagsToRender.add(new TagRenderEntry(null, null, null, tntStr, color, relX, tagY, relZ, distSq));
                            }
                        } else {
                            String nameLine = config.wallNametagsEnabled ? getEntityTagLabel(entity, isPlayer) : null;
                            String armorLine = (config.armorEnchantInfoEnabled && isLiving) ? getArmorEnchantSummary(entity) : null;
                            String distLine = null;
                            if (config.showDistanceTag && (nameLine != null || armorLine != null)) {
                                int distInt = (int) Math.round(Math.sqrt(distSq));
                                distLine = distInt + (config.distanceUnitBlocks ? "B" : "M");
                            }
                            if (nameLine != null || armorLine != null) {
                                double tagY = hasValidBox ? (bMaxY + 0.45D) : (relY + boxHeight + 0.45D);
                                tagsToRender.add(new TagRenderEntry(distLine, nameLine, armorLine, null, 0xFFFFFFFF, relX, tagY, relZ, distSq));
                            }
                        }
                    }
                }
            }

            // 2. Depth-tested Custom Hit Color overlays
            if (hitOverlays != null && !hitOverlays.isEmpty() && config.customHitOpacity > 0) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glDepthMask(false);

                float hr = ((config.customHitColor >> 16) & 0xFF) / 255.0F;
                float hg = ((config.customHitColor >> 8) & 0xFF) / 255.0F;
                float hb = (config.customHitColor & 0xFF) / 255.0F;
                float baseAlpha = config.customHitOpacity / 100.0F;

                for (int i = 0; i < hitOverlays.size(); i++) {
                    HitOverlayEntry h = hitOverlays.get(i);
                    float a = baseAlpha * h.alphaScale;
                    if (a > 0.01F) {
                        drawFilledBox(h.minX, h.minY, h.minZ, h.maxX, h.maxY, h.maxZ, hr, hg, hb, a);
                    }
                }
            }

            // 3. Render Nametags, Distance Tags, Armor Enchants & TNT Timers
            if (tagsToRender != null && !tagsToRender.isEmpty() && textRendererObj != null) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                Collections.sort(tagsToRender, new Comparator<TagRenderEntry>() {
                    @Override
                    public int compare(TagRenderEntry a, TagRenderEntry b) {
                        return Double.compare(b.distSq, a.distSq);
                    }
                });

                float bgR = ((config.nametagBgColor >> 16) & 0xFF) / 255.0F;
                float bgG = ((config.nametagBgColor >> 8) & 0xFF) / 255.0F;
                float bgB = (config.nametagBgColor & 0xFF) / 255.0F;
                float bgA = config.nametagBgOpacity / 100.0F;

                for (int i = 0; i < tagsToRender.size(); i++) {
                    drawWorldTagStack(tagsToRender.get(i), viewYaw, viewPitch, bgR, bgG, bgB, bgA);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void renderCustomBlockHighlights(
            Minecraft client, HitJumpConfig config, Class<?> entityBaseClass,
            double camX, double camY, double camZ,
            boolean drawOutline, boolean drawOverlay
    ) {
        Object hit = getCrosshairHitResult(client, entityBaseClass);
        if (hit == null || hitTypeField == null || hitBlockPosField == null) {
            return;
        }
        try {
            Object hitType = hitTypeField.get(hit);
            if (!(hitType instanceof Enum) || ((Enum<?>) hitType).ordinal() != 1) {
                return;
            }
            Object posObj = hitBlockPosField.get(hit);
            if (posObj == null) {
                return;
            }
            ensureBlockPosFields(posObj);
            if (blockPosXField == null || blockPosYField == null || blockPosZField == null) {
                return;
            }

            int bx = blockPosXField.getInt(posObj);
            int by = blockPosYField.getInt(posObj);
            int bz = blockPosZField.getInt(posObj);

            double minX = bx, minY = by, minZ = bz;
            double maxX = bx + 1.0D, maxY = by + 1.0D, maxZ = bz + 1.0D;

            // Accurate mode queries exact block bounds (slabs, stairs, chests, beds); Full Block mode keeps 1x1x1 cube
            if (!config.blockOutlineFullBlock) {
                Object selectedBox = getSelectedBlockBox(client, posObj);
                if (selectedBox != null) {
                    ensureBoxFields(selectedBox);
                    if (boxMinXField != null) {
                        double sMinX = boxMinXField.getDouble(selectedBox);
                        double sMinY = boxMinYField.getDouble(selectedBox);
                        double sMinZ = boxMinZField.getDouble(selectedBox);
                        double sMaxX = boxMaxXField.getDouble(selectedBox);
                        double sMaxY = boxMaxYField.getDouble(selectedBox);
                        double sMaxZ = boxMaxZField.getDouble(selectedBox);
                        if (sMaxX > sMinX && sMaxY > sMinY && sMaxZ > sMinZ) {
                            minX = sMinX; minY = sMinY; minZ = sMinZ;
                            maxX = sMaxX; maxY = sMaxY; maxZ = sMaxZ;
                        }
                    }
                }
            }

            int sideOrdinal = -1;
            if (hitSideField != null) {
                Object sideObj = hitSideField.get(hit);
                if (sideObj instanceof Enum) {
                    sideOrdinal = ((Enum<?>) sideObj).ordinal();
                }
            }

            double pad = 0.0025D;
            double x0 = minX - pad - camX;
            double y0 = minY - pad - camY;
            double z0 = minZ - pad - camZ;
            double x1 = maxX + pad - camX;
            double y1 = maxY + pad - camY;
            double z1 = maxZ + pad - camZ;

            GL11.glDisable(GL11.GL_TEXTURE_2D);

            // 1. Block Overlay (Fill) — Aimed Face or Full Cube (See-Through) with Blend Modes
            if (drawOverlay) {
                float r = ((config.blockOverlayColor >> 16) & 0xFF) / 255.0F;
                float g = ((config.blockOverlayColor >> 8) & 0xFF) / 255.0F;
                float b = (config.blockOverlayColor & 0xFF) / 255.0F;
                float a = config.blockOverlayOpacity / 100.0F;

                if (config.blockOverlayBlendMode == 1) {
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                } else if (config.blockOverlayBlendMode == 2) {
                    GL11.glBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR);
                    r *= a; g *= a; b *= a;
                } else {
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                }

                if (config.blockOverlayFaceOnly && sideOrdinal >= 0 && sideOrdinal <= 5) {
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL11.glDepthFunc(GL11.GL_LEQUAL);
                    drawSingleBlockFace(x0, y0, z0, x1, y1, z1, sideOrdinal, r, g, b, a);
                } else {
                    GL11.glDisable(GL11.GL_DEPTH_TEST);
                    drawFilledBox(x0, y0, z0, x1, y1, z1, r, g, b, a);
                }
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            // 2. Block Outline
            if (drawOutline) {
                float r = ((config.blockOutlineColor >> 16) & 0xFF) / 255.0F;
                float g = ((config.blockOutlineColor >> 8) & 0xFF) / 255.0F;
                float b = (config.blockOutlineColor & 0xFF) / 255.0F;
                float a = config.blockOutlineOpacity / 100.0F;

                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glLineWidth(config.blockOutlineWidth / 10.0F);
                GL11.glColor4f(r, g, b, a);

                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x1, y0, z0);
                GL11.glVertex3d(x1, y0, z0); GL11.glVertex3d(x1, y0, z1);
                GL11.glVertex3d(x1, y0, z1); GL11.glVertex3d(x0, y0, z1);
                GL11.glVertex3d(x0, y0, z1); GL11.glVertex3d(x0, y0, z0);

                GL11.glVertex3d(x0, y1, z0); GL11.glVertex3d(x1, y1, z0);
                GL11.glVertex3d(x1, y1, z0); GL11.glVertex3d(x1, y1, z1);
                GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x0, y1, z1);
                GL11.glVertex3d(x0, y1, z1); GL11.glVertex3d(x0, y1, z0);

                GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x0, y1, z0);
                GL11.glVertex3d(x1, y0, z0); GL11.glVertex3d(x1, y1, z0);
                GL11.glVertex3d(x1, y0, z1); GL11.glVertex3d(x1, y1, z1);
                GL11.glVertex3d(x0, y0, z1); GL11.glVertex3d(x0, y1, z1);
                GL11.glEnd();
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void drawSingleBlockFace(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int sideOrdinal, float r, float g, float b, float a
    ) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        switch (sideOrdinal) {
            case 0: // DOWN (-Y)
                GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(maxX, minY, minZ);
                GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(minX, minY, maxZ);
                break;
            case 1: // UP (+Y)
                GL11.glVertex3d(minX, maxY, minZ); GL11.glVertex3d(minX, maxY, maxZ);
                GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(maxX, maxY, minZ);
                break;
            case 2: // NORTH (-Z)
                GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, maxY, minZ);
                GL11.glVertex3d(maxX, maxY, minZ); GL11.glVertex3d(maxX, minY, minZ);
                break;
            case 3: // SOUTH (+Z)
                GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(maxX, minY, maxZ);
                GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
                break;
            case 4: // WEST (-X)
                GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, minY, maxZ);
                GL11.glVertex3d(minX, maxY, maxZ); GL11.glVertex3d(minX, maxY, minZ);
                break;
            case 5: // EAST (+X)
                GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
                GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(maxX, minY, maxZ);
                break;
            default:
                break;
        }
        GL11.glEnd();
    }

    private static Object getCrosshairHitResult(Minecraft client, Class<?> entityBaseClass) {
        if (hitResultField != null) {
            try {
                return hitResultField.get(client);
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (entityBaseClass == null) {
            return null;
        }
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

            Field foundType = null;
            Field foundSide = null;
            Field foundPos = null;
            boolean hasEntityField = false;

            Field[] subFields;
            try {
                subFields = val.getClass().getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            if (subFields.length < 4 || subFields.length > 10) continue;

            for (Field sf : subFields) {
                if (Modifier.isStatic(sf.getModifiers())) continue;
                Class<?> st = sf.getType();
                if (st.isEnum()) {
                    Object[] constants = st.getEnumConstants();
                    if (constants != null) {
                        if (constants.length == 3) foundType = sf;
                        else if (constants.length == 6) foundSide = sf;
                    }
                } else if (st == entityBaseClass) {
                    hasEntityField = true;
                } else if (!st.isPrimitive() && !st.isArray() && !isSkippableClass(st) && hasThreeIntCoords(st)) {
                    foundPos = sf;
                }
            }

            if (foundType != null && foundSide != null && foundPos != null && hasEntityField) {
                try {
                    foundType.setAccessible(true);
                    foundSide.setAccessible(true);
                    foundPos.setAccessible(true);
                    hitResultField = f;
                    hitTypeField = foundType;
                    hitSideField = foundSide;
                    hitBlockPosField = foundPos;
                    return val;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean hasThreeIntCoords(Class<?> cls) {
        Class<?> c = cls;
        int intCount = 0;
        int totalCount = 0;
        while (c != null && c != Object.class && !isSkippableClass(c)) {
            try {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    totalCount++;
                    if (f.getType() == int.class) intCount++;
