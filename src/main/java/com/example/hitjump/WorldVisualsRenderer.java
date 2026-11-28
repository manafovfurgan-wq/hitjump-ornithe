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

    // Targeted block outline reflection cache
    private static Field hitResultField = null;
    private static Field hitTypeField = null;
    private static Field hitBlockPosField = null;
    private static Field blockPosXField = null;
    private static Field blockPosYField = null;
    private static Field blockPosZField = null;
    private static boolean blockBoxMethodSearched = false;
    private static Method worldGetBlockStateMethod = null;
    private static Method stateGetBlockMethod = null;
    private static Method blockSelectedBoxMethod = null;

    private static class TagRenderEntry {
        final String distanceLine;
        final String nameLine;
        final String armorLine;
        final String tntLine;
        final int tntColor;
        final double relX;
        final double relY;
        final double relZ;
        final double distSq;

        TagRenderEntry(
                String distanceLine,
                String nameLine,
                String armorLine,
                String tntLine,
                int tntColor,
                double relX, double relY, double relZ, double distSq
        ) {
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
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
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

        if (!needHitboxPass && !needTagPass && !needHitColorPass && !needBlockOutlinePass) {
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

        double camX;
        double camY;
        double camZ;
        float viewYaw;
        float viewPitch;
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

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
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

            // 0. Custom Block Selection Outline (depth-tested)
            if (needBlockOutlinePass) {
                renderCustomBlockOutline(client, config, entityBaseClass, camX, camY, camZ);
            }

            // 1. Entity Hitboxes & Separated Eye Line (respects hitboxThroughWalls toggle)
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
                    if (entity == null || entity == player) {
                        continue;
                    }
                    if (HitJumpMod.isEntityDead(entity)) {
                        continue;
                    }

                    boolean isPlayer = playerBaseClass != null && playerBaseClass.isInstance(entity);
                    boolean isLiving = livingBaseClass != null && livingBaseClass.isInstance(entity);
                    boolean isTnt = isPrimedTnt(entity, entityBaseClass, livingBaseClass);

                    double ex;
                    double ey;
                    double ez;
                    double interpX;
                    double interpY;
                    double interpZ;
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
                                if (bh > 0.01D) {
                                    boxHeight = bh;
                                }
                                hasValidBox = (bw > 0.01D && bh > 0.01D);
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    // Hitbox & Separated Eye Line
                    if (needHitboxPass && hasValidBox && distSq <= hitboxMaxDistSq) {
                        boolean targetMatch;
                        if (config.hitboxTargetMode == 1) {
                            targetMatch = isPlayer;
                        } else if (config.hitboxTargetMode == 0) {
                            targetMatch = isLiving;
                        } else {
                            targetMatch = true;
                        }
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

                    // Custom Hit Color overlays
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

                    // Collect Tags (TNT Fuse Timer, Wall Nametags, Distance Tag, Armor Enchant Info)
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
                                tagsToRender.add(new TagRenderEntry(
                                        distLine, nameLine, armorLine, null, 0xFFFFFFFF,
                                        relX, tagY, relZ, distSq
                                ));
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

            // 3. Render Nametags, Distance Tags, Armor Enchantments & TNT Timers (through-wall)
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

    private static void renderCustomBlockOutline(
            Minecraft client, HitJumpConfig config, Class<?> entityBaseClass,
            double camX, double camY, double camZ
    ) {
        Object hit = getCrosshairHitResult(client, entityBaseClass);
        if (hit == null || hitTypeField == null || hitBlockPosField == null) {
            return;
        }
        try {
            Object hitType = hitTypeField.get(hit);
            if (!(hitType instanceof Enum) || ((Enum<?>) hitType).ordinal() != 1) {
                return; // Only draw when aiming at a BLOCK (ordinal 1)
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

            double minX = bx;
            double minY = by;
            double minZ = bz;
            double maxX = bx + 1.0D;
            double maxY = by + 1.0D;
            double maxZ = bz + 1.0D;

            // Try to fetch exact block selection bounds (slabs, stairs, chests, beds, fences)
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
                        minX = sMinX;
                        minY = sMinY;
                        minZ = sMinZ;
                        maxX = sMaxX;
                        maxY = sMaxY;
                        maxZ = sMaxZ;
                    }
                }
            }

            double pad = 0.0025D;
            double x0 = minX - pad - camX;
            double y0 = minY - pad - camY;
            double z0 = minZ - pad - camZ;
            double x1 = maxX + pad - camX;
            double y1 = maxY + pad - camY;
            double z1 = maxZ + pad - camZ;

            float r = ((config.blockOutlineColor >> 16) & 0xFF) / 255.0F;
            float g = ((config.blockOutlineColor >> 8) & 0xFF) / 255.0F;
            float b = (config.blockOutlineColor & 0xFF) / 255.0F;
            float a = config.blockOutlineOpacity / 100.0F;

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(config.blockOutlineWidth / 10.0F);
            GL11.glColor4f(r, g, b, a);

            GL11.glBegin(GL11.GL_LINES);
            // Bottom face
            GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x1, y0, z0);
            GL11.glVertex3d(x1, y0, z0); GL11.glVertex3d(x1, y0, z1);
            GL11.glVertex3d(x1, y0, z1); GL11.glVertex3d(x0, y0, z1);
            GL11.glVertex3d(x0, y0, z1); GL11.glVertex3d(x0, y0, z0);
            // Top face
            GL11.glVertex3d(x0, y1, z0); GL11.glVertex3d(x1, y1, z0);
            GL11.glVertex3d(x1, y1, z0); GL11.glVertex3d(x1, y1, z1);
            GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x0, y1, z1);
            GL11.glVertex3d(x0, y1, z1); GL11.glVertex3d(x0, y1, z0);
            // Vertical edges
            GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x0, y1, z0);
            GL11.glVertex3d(x1, y0, z0); GL11.glVertex3d(x1, y1, z0);
            GL11.glVertex3d(x1, y0, z1); GL11.glVertex3d(x1, y1, z1);
            GL11.glVertex3d(x0, y0, z1); GL11.glVertex3d(x0, y1, z1);
            GL11.glEnd();
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
        } catch (Throwable ignored) {
        }
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
                    foundPos.setAccessible(true);
                    hitResultField = f;
                    hitTypeField = foundType;
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
                }
            } catch (Throwable ignored) {
                return false;
            }
            c = c.getSuperclass();
        }
        return intCount == 3 && totalCount == 3;
    }

    private static void ensureBlockPosFields(Object posObj) {
        if (blockPosXField != null || posObj == null) {
            return;
        }
        Class<?> c = posObj.getClass();
        while (c != null && c != Object.class && !isSkippableClass(c)) {
            Field[] ints = new Field[3];
            int idx = 0;
            try {
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                        ints[idx++] = f;
                        if (idx == 3) break;
                    }
                }
            } catch (Throwable ignored) {
                return;
            }
            if (idx == 3) {
                try {
                    ints[0].setAccessible(true);
                    ints[1].setAccessible(true);
                    ints[2].setAccessible(true);
                    blockPosXField = ints[0];
                    blockPosYField = ints[1];
                    blockPosZField = ints[2];
                } catch (Throwable ignored) {
                }
                return;
            }
            c = c.getSuperclass();
        }
    }

    private static Object getSelectedBlockBox(Minecraft client, Object posObj) {
        if (!blockBoxMethodSearched) {
            blockBoxMethodSearched = true;
            try {
                Class<?> posClass = posObj.getClass();
                Class<?> worldClass = client.world.getClass();
                Class<?> boxClass = HitJumpMod.entityBoxField.getType();

                for (Method wm : worldClass.getMethods()) {
                    Class<?>[] wp = wm.getParameterTypes();
                    if (wp.length != 1 || wp[0] != posClass) continue;
                    Class<?> stateIface = wm.getReturnType();
                    if (!stateIface.isInterface() || isSkippableClass(stateIface)) continue;

                    for (Method sm : stateIface.getMethods()) {
                        if (sm.getParameterTypes().length != 0) continue;
                        Class<?> blockClass = sm.getReturnType();
                        if (blockClass.isPrimitive() || blockClass.isInterface() || isSkippableClass(blockClass)) continue;

                        for (Method bm : blockClass.getMethods()) {
                            Class<?>[] bp = bm.getParameterTypes();
                            if (bp.length == 2
                                    && bp[0].isAssignableFrom(worldClass)
                                    && bp[1] == posClass
                                    && bm.getReturnType() == boxClass) {
                                worldGetBlockStateMethod = wm;
                                stateGetBlockMethod = sm;
                                blockSelectedBoxMethod = bm;
                                break;
                            }
                        }
                        if (blockSelectedBoxMethod != null) break;
                    }
                    if (blockSelectedBoxMethod != null) break;
                }
            } catch (Throwable ignored) {
            }
        }

        if (worldGetBlockStateMethod != null && stateGetBlockMethod != null && blockSelectedBoxMethod != null) {
            try {
                Object state = worldGetBlockStateMethod.invoke(client.world, posObj);
                if (state != null) {
                    Object block = stateGetBlockMethod.invoke(state);
                    if (block != null) {
                        return blockSelectedBoxMethod.invoke(block, client.world, posObj);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static void drawHitboxAndEyeLine(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            boolean isPlayer, boolean isLiving,
            boolean drawBox, boolean drawEyeLine,
            boolean useInvisColor,
            float invisR, float invisG, float invisB
    ) {
        if (drawBox) {
            if (useInvisColor) {
                GL11.glColor4f(invisR, invisG, invisB, 0.98F);
            } else if (isPlayer) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.95F);
            } else {
                GL11.glColor4f(0.85F, 0.92F, 1.0F, 0.78F);
            }

            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(maxX, minY, minZ);
            GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, minY, maxZ);
            GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(minX, minY, maxZ);
            GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, minY, minZ);

            GL11.glVertex3d(minX, maxY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
            GL11.glVertex3d(maxX, maxY, minZ); GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
            GL11.glVertex3d(minX, maxY, maxZ); GL11.glVertex3d(minX, maxY, minZ);

            GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, maxY, minZ);
            GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
            GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
            GL11.glEnd();
        }

        if (drawEyeLine && isLiving) {
            double h = maxY - minY;
            double eyeY = minY + (isPlayer ? Math.min(h - 0.12D, h * 0.90D) : (h * 0.85D));
            GL11.glColor4f(1.0F, 0.22F, 0.22F, 0.95F);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(minX, eyeY, minZ); GL11.glVertex3d(maxX, eyeY, minZ);
            GL11.glVertex3d(maxX, eyeY, minZ); GL11.glVertex3d(maxX, eyeY, maxZ);
            GL11.glVertex3d(maxX, eyeY, maxZ); GL11.glVertex3d(minX, eyeY, maxZ);
            GL11.glVertex3d(minX, eyeY, maxZ); GL11.glVertex3d(minX, eyeY, minZ);
            GL11.glEnd();
        }
    }

    private static void drawFilledBox(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, float a
    ) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(minX, minY, maxZ);

        GL11.glVertex3d(minX, maxY, minZ); GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(maxX, maxY, minZ);

        GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ); GL11.glVertex3d(maxX, minY, minZ);

        GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);

        GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ); GL11.glVertex3d(minX, maxY, minZ);

        GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glEnd();
    }

    private static void drawWorldTagStack(
            TagRenderEntry entry,
            float viewYaw,
            float viewPitch,
            float bgR, float bgG, float bgB, float bgA
    ) {
        double dist = Math.sqrt(entry.distSq);
        float scale = 0.02666667F;
        if (dist > 10.0D) {
            scale = (float) (0.02666667D * (1.0D + (dist - 10.0D) * 0.045D));
            if (scale > 0.16F) {
                scale = 0.16F;
            }
        }

        GL11.glPushMatrix();
        GL11.glTranslated(entry.relX, entry.relY, entry.relZ);
        GL11.glRotatef(-viewYaw, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(viewPitch, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);

        if (entry.tntLine != null) {
            drawSingleTagLine(entry.tntLine, 0, entry.tntColor, 0.0F, 0.0F, 0.0F, 0.55F);
            GL11.glPopMatrix();
            return;
        }

        int nameY = 0;
        if (entry.armorLine != null) {
            drawSingleTagLine(entry.armorLine, 0, 0xFF7BE8FF, 0.0F, 0.0F, 0.0F, Math.max(0.25F, bgA));
            nameY = -12;
        }

        if (entry.nameLine != null) {
            drawSingleTagLine(entry.nameLine, nameY, 0xFFFFFFFF, bgR, bgG, bgB, bgA);
        }

        if (entry.distanceLine != null) {
            int distY = (entry.nameLine != null) ? (nameY - 12) : nameY;
            drawSingleTagLine(entry.distanceLine, distY, 0xFFD6E4FF, 0.0F, 0.0F, 0.0F, Math.max(0.22F, bgA * 0.85F));
        }

        GL11.glPopMatrix();
    }

    private static void drawSingleTagLine(
            String text,
            int yOffset,
            int textColor,
            float bgR, float bgG, float bgB, float bgA
    ) {
        int textWidth = getTextWidth(text);
        if (textWidth <= 0) {
            return;
        }
        int halfWidth = textWidth / 2;

        if (bgA > 0.005F) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(bgR, bgG, bgB, bgA);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3d(-halfWidth - 2, yOffset - 2, 0.0D);
            GL11.glVertex3d(-halfWidth - 2, yOffset + 9, 0.0D);
            GL11.glVertex3d(halfWidth + 2, yOffset + 9, 0.0D);
            GL11.glVertex3d(halfWidth + 2, yOffset - 2, 0.0D);
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTextWithShadow(text, -halfWidth, yOffset, textColor);
    }

    private static boolean isEntityInvisible(Object entity) {
        if (dataWatcherField == null || dataWatcherGetByteMethod == null) {
            return false;
        }
        try {
            Object dw = dataWatcherField.get(entity);
            if (dw != null) {
                byte flags = ((Byte) dataWatcherGetByteMethod.invoke(dw, 0)).byteValue();
                return (flags & 0x20) != 0;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isPrimedTnt(Object entity, Class<?> entityBaseClass, Class<?> livingBaseClass) {
        if (entity == null || entityBaseClass == null || livingBaseClass == null) {
            return false;
        }
        Class<?> cls = entity.getClass();
        if (tntClassChecked && tntEntityClass != null) {
            return cls == tntEntityClass;
        }
        if (cls.getSuperclass() != entityBaseClass) {
            return false;
        }

        Field foundInt = null;
        boolean hasLivingRef = false;
        int instanceFieldCount = 0;
        try {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                instanceFieldCount++;
                if (f.getType() == int.class) {
                    foundInt = f;
                } else if (f.getType() == livingBaseClass) {
                    hasLivingRef = true;
                }
            }
            if (instanceFieldCount == 2 && foundInt != null && hasLivingRef) {
                foundInt.setAccessible(true);
                tntEntityClass = cls;
                tntFuseField = foundInt;
                tntClassChecked = true;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int getTntFuse(Object tntEntity) {
        if (tntFuseField == null) {
            return -1;
        }
        try {
            return tntFuseField.getInt(tntEntity);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String getArmorEnchantSummary(Object entity) {
        if (entityGetInventoryMethod == null) {
            return null;
        }
        Object[] inv;
        try {
            Object res = entityGetInventoryMethod.invoke(entity);
            if (!(res instanceof Object[])) {
                return null;
            }
            inv = (Object[]) res;
        } catch (Throwable ignored) {
            return null;
        }
        if (inv.length < 4) {
            return null;
        }

        int startIdx = inv.length - 1;
        int endIdx = inv.length - 4;
        boolean hasAnyArmor = false;
        StringBuilder sb = new StringBuilder();

        for (int i = startIdx; i >= endIdx; i--) {
            Object stack = inv[i];
            if (i != startIdx) {
                sb.append(' ');
            }
            if (stack == null) {
                sb.append('-');
                continue;
            }
            hasAnyArmor = true;
            sb.append(formatArmorPieceEnchants(stack));
        }

        return hasAnyArmor ? sb.toString() : null;
    }

    private static String formatArmorPieceEnchants(Object itemStack) {
        ensureItemStackNbtField(itemStack);
        if (itemStackNbtField == null) {
            return "A";
        }
        try {
            Object nbt = itemStackNbtField.get(itemStack);
            if (nbt == null) {
                return "0";
            }
            String s = nbt.toString();
            int enchStart = s.indexOf("ench:[");
            if (enchStart < 0) {
                return "0";
            }
            int listStart = enchStart + 6;
            int listEnd = s.indexOf(']', listStart);
            if (listEnd <= listStart) {
                return "*";
            }
            String listStr = s.substring(listStart, listEnd);
            if (listStr.trim().isEmpty()) {
                return "*";
            }

            int prot = 0;
            int fireProt = 0;
            int blastProt = 0;
            int projProt = 0;
            int thorns = 0;

            int pos = 0;
            while (pos < listStr.length()) {
                int open = listStr.indexOf('{', pos);
                if (open < 0) break;
                int close = listStr.indexOf('}', open);
                if (close < 0) break;
                String entry = listStr.substring(open + 1, close);
                int id = parseTagNumber(entry, "id:");
                int lvl = parseTagNumber(entry, "lvl:");
                if (id == 0 && lvl > prot) prot = lvl;
                else if (id == 1 && lvl > fireProt) fireProt = lvl;
                else if (id == 3 && lvl > blastProt) blastProt = lvl;
                else if (id == 4 && lvl > projProt) projProt = lvl;
                else if (id == 7 && lvl > thorns) thorns = lvl;
                pos = close + 1;
            }

            if (prot > 0) return "P" + prot + (thorns > 0 ? "T" + thorns : "");
            if (blastProt > 0) return "BP" + blastProt;
            if (projProt > 0) return "PP" + projProt;
            if (fireProt > 0) return "FP" + fireProt;
            if (thorns > 0) return "T" + thorns;
            return "*";
        } catch (Throwable ignored) {
            return "A";
        }
    }

    private static int parseTagNumber(String entry, String key) {
        int idx = entry.indexOf(key);
        if (idx < 0) return -1;
        int i = idx + key.length();
        int val = 0;
        boolean foundDigit = false;
        while (i < entry.length()) {
            char c = entry.charAt(i);
            if (c >= '0' && c <= '9') {
                val = val * 10 + (c - '0');
                foundDigit = true;
                i++;
            } else {
                break;
            }
        }
        return foundDigit ? val : -1;
    }

    private static void ensureItemStackNbtField(Object itemStack) {
        if (itemStackNbtSearched || itemStack == null) {
            return;
        }
        itemStackNbtSearched = true;
        try {
            for (Field f : itemStack.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                Class<?> ft = f.getType();
                if (isSkippableClass(ft)) continue;
                boolean hasStringBool = false;
                boolean hasStringInt = false;
                boolean hasStringShort = false;
                for (Method m : ft.getMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && p[0] == String.class) {
                        if (m.getReturnType() == boolean.class) hasStringBool = true;
                        else if (m.getReturnType() == int.class) hasStringInt = true;
                        else if (m.getReturnType() == short.class) hasStringShort = true;
                    }
                }
                if (hasStringBool && hasStringInt && hasStringShort) {
                    f.setAccessible(true);
                    itemStackNbtField = f;
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static int getTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (HitJumpMod.getCachedClient() != null) {
            ensureTextRenderer(HitJumpMod.getCachedClient());
        }
        if (textRendererObj != null && textGetWidthMethod != null) {
            try {
                return ((Integer) textGetWidthMethod.invoke(textRendererObj, text)).intValue();
            } catch (Throwable ignored) {
            }
        }
        return text.length() * 6;
    }

    private static void drawTextWithShadow(String text, float x, float y, int color) {
        if (textRendererObj != null && textDrawShadowMethod != null) {
            try {
                textDrawShadowMethod.invoke(textRendererObj, text, x, y, color);
            } catch (Throwable ignored) {
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
                return;
            }
        }
    }

    private static String getEntityTagLabel(Object entity, boolean isPlayer) {
        if (isPlayer) {
            String display = getFormattedDisplayName(entity);
            if (display != null && !display.trim().isEmpty()) {
                return display;
            }
            return getPlayerProfileName(entity);
        }

        String custom = getCustomNameTag(entity);
        if (custom != null && !custom.trim().isEmpty()) {
            return custom;
        }
        return null;
    }

    private static String getCustomNameTag(Object entity) {
        if (dataWatcherField == null || dataWatcherGetStringMethod == null) {
            return null;
        }
        try {
            Object dw = dataWatcherField.get(entity);
            if (dw != null) {
                Object val = dataWatcherGetStringMethod.invoke(dw, 2);
                return (val instanceof String) ? (String) val : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String getPlayerProfileName(Object playerEntity) {
        if (gameProfileField == null || gameProfileGetNameMethod == null) {
            return null;
        }
        try {
            Object profile = gameProfileField.get(playerEntity);
            if (profile != null) {
                Object name = gameProfileGetNameMethod.invoke(profile);
                return (name instanceof String) ? (String) name : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String getFormattedDisplayName(Object entity) {
        if (entityGetDisplayNameMethod == null) {
            return null;
        }
        try {
            Object textObj = entityGetDisplayNameMethod.invoke(entity);
            if (textObj == null) {
                return null;
            }
            if (!textFormattedSearched) {
                textFormattedSearched = true;
                Class<?> textInterface = entityGetDisplayNameMethod.getReturnType();
                Method best = null;
                String bestVal = "";
                for (Method m : textInterface.getMethods()) {
                    if (m.getParameterTypes().length != 0 || m.getReturnType() != String.class) continue;
                    if ("toString".equals(m.getName())) continue;
                    try {
                        Object res = m.invoke(textObj);
                        if (res instanceof String) {
                            String s = (String) res;
                            if (s.indexOf('\u00A7') >= 0) {
                                best = m;
                                break;
                            }
                            if (s.length() > bestVal.length()) {
                                best = m;
                                bestVal = s;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                textFormattedMethod = best;
            }
            if (textFormattedMethod != null) {
                Object res = textFormattedMethod.invoke(textObj);
                if (res instanceof String) {
                    return (String) res;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void ensureNameAccessFields(Object player) {
        if (nameAccessSearched || player == null) {
            return;
        }
        nameAccessSearched = true;

        Class<?> entityClass = HitJumpMod.getEntityBaseClass(player);
        Class<?> playerBaseClass = HitJumpMod.getPlayerBaseClass(player);

        if (entityClass != null) {
            try {
                for (Field f : entityClass.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    Class<?> ft = f.getType();
                    if (isSkippableClass(ft)) continue;
                    Method strM = null;
                    Method byteM = null;
                    boolean hasInt = false;
                    for (Method m : ft.getMethods()) {
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length == 1 && p[0] == int.class) {
                            if (m.getReturnType() == String.class) strM = m;
                            else if (m.getReturnType() == byte.class) byteM = m;
                            else if (m.getReturnType() == int.class) hasInt = true;
                        }
                    }
                    if (strM != null && byteM != null && hasInt) {
                        f.setAccessible(true);
                        dataWatcherField = f;
                        dataWatcherGetByteMethod = byteM;
                        dataWatcherGetStringMethod = strM;
                        break;
                    }
                }

                for (Method m : entityClass.getMethods()) {
                    if (m.getParameterTypes().length != 0) continue;
                    Class<?> rt = m.getReturnType();
                    if (entityGetDisplayNameMethod == null && rt.isInterface() && !rt.getName().startsWith("java.")) {
                        entityGetDisplayNameMethod = m;
                    } else if (entityGetInventoryMethod == null
                            && rt.isArray()
                            && !rt.getComponentType().isPrimitive()
                            && rt.getComponentType() != entityClass) {
                        entityGetInventoryMethod = m;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (playerBaseClass != null) {
            try {
                for (Field f : playerBaseClass.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if ("com.mojang.authlib.GameProfile".equals(f.getType().getName())) {
                        f.setAccessible(true);
                        gameProfileField = f;
                        gameProfileGetNameMethod = f.getType().getMethod("getName");
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void ensureBoxFields(Object boxObj) {
        if (boxFieldsSearched || boxObj == null) {
            return;
        }
        boxFieldsSearched = true;
        try {
            Field[] fields = boxObj.getClass().getDeclaredFields();
            Field[] doubles = new Field[6];
            int idx = 0;
            for (Field f : fields) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == double.class) {
                    doubles[idx++] = f;
                    if (idx == 6) break;
                }
            }
            if (idx == 6) {
                boxMinXField = doubles[0];
                boxMinYField = doubles[1];
                boxMinZField = doubles[2];
                boxMaxXField = doubles[3];
                boxMaxYField = doubles[4];
                boxMaxZField = doubles[5];
                for (int i = 0; i < 6; i++) {
                    doubles[i].setAccessible(true);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static List<?> getLoadedEntityList(Minecraft client) {
        try {
            List<?> bestList = null;
            Class<?> worldCls = client.world.getClass();
            while (worldCls != null && worldCls != Object.class && !isSkippableClass(worldCls)) {
                for (Field f : worldCls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (!List.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object val = f.get(client.world);
                    if (val instanceof List) {
                        List<?> list = (List<?>) val;
                        if (list.contains(client.player)) {
                            if (bestList == null || list.size() > bestList.size()) {
                                bestList = list;
                            }
                        }
                    }
                }
                worldCls = worldCls.getSuperclass();
            }
            return bestList;
        } catch (Throwable ignored) {
            return null;
        }
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
