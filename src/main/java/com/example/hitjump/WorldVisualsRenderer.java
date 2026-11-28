package com.example.hitjump;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Constructor;
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
import java.util.Map;

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
    private static Method entityGetHeldItemMethod = null;
    private static Method textFormattedMethod = null;
    private static boolean textFormattedSearched = false;

    // Vanilla nametag range override (prevents double player names without mapping-fragile mixins)
    private static boolean vanillaTagRangeSearched = false;
    private static Field vanillaNameRangeField = null;
    private static Field vanillaSneakNameRangeField = null;
    private static boolean vanillaTagSuppressed = false;

    // ArmorStand hologram detector cache
    private static Class<?> armorStandClass = null;
    private static boolean armorStandChecked = false;

    // Line-of-sight raycast cache (World.rayTraceBlocks(Vec3, Vec3, boolean, boolean, boolean))
    private static boolean rayTraceSearched = false;
    private static Method worldRayTraceMethod = null;
    private static Constructor<?> vec3Constructor = null;

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
        final String distanceLine, nameLine, enchantLine, tntLine;
        final int tntColor;
        final boolean depthTestedOnly;
        final double relX, relY, relZ, distSq;

        TagRenderEntry(String distanceLine, String nameLine, String enchantLine, String tntLine,
                       int tntColor, boolean depthTestedOnly,
                       double relX, double relY, double relZ, double distSq) {
            this.distanceLine = distanceLine;
            this.nameLine = nameLine;
            this.enchantLine = enchantLine;
            this.tntLine = tntLine;
            this.tntColor = tntColor;
            this.depthTestedOnly = depthTestedOnly;
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
        WorldEnvOptimizer.onRenderTick(client);
        syncVanillaNametagOverride(client, config);

        boolean needHitboxPass = config.wallHitboxesEnabled || config.hitboxEyeLineEnabled || config.hitboxAnimEnabled;
        boolean needTagPass = config.wallNametagsEnabled || config.showDistanceTag
                || config.armorEnchantInfoEnabled || config.tntTimerEnabled || vanillaTagSuppressed;
        boolean needHitColorPass = config.customHitColorEnabled;
        boolean needBlockOutlinePass = config.blockOutlineEnabled && config.blockOutlineOpacity > 0;
        boolean needBlockOverlayPass = config.blockOverlayEnabled && config.blockOverlayOpacity > 0;
        boolean needPetsPass = config.petBunnyEnabled || config.petDragonEnabled;

        if (!needHitboxPass && !needTagPass && !needHitColorPass
                && !needBlockOutlinePass && !needBlockOverlayPass && !needPetsPass) {
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

        double eyeX = camX;
        double eyeY = camY + 1.62D;
        double eyeZ = camZ;

        List<?> entities = (needHitboxPass || needTagPass || needHitColorPass) ? getLoadedEntityList(client) : null;
        double hitboxMaxDistSq = (double) config.hitboxRange * (double) config.hitboxRange;
        double nametagMaxDistSq = (double) config.nametagRange * (double) config.nametagRange;
        double distanceMaxDistSq = (double) config.distanceRange * (double) config.distanceRange;
        double enchantMaxDistSq = (double) config.enchantRange * (double) config.enchantRange;

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

            // 0. Companion Pets (Black Baby Bunny & Small Flapping Dragon)
            if (needPetsPass) {
                CompanionPetsRenderer.render(client, tickDelta, camX, camY, camZ);
                GL11.glDepthMask(false);
            }

            // 1. Custom Block Overlay & Outline
            if (needBlockOutlinePass || needBlockOverlayPass) {
                renderCustomBlockHighlights(client, config, entityBaseClass, camX, camY, camZ, needBlockOutlinePass, needBlockOverlayPass);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }

            // 2. Entity Hitboxes, Eye Line & Hitbox Animations
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

                boolean thirdPerson = HitJumpMod.getPerspective(client) != 0;
                int count = entities.size();
                for (int i = 0; i < count; i++) {
                    Object entity;
                    try {
                        entity = entities.get(i);
                    } catch (Throwable ignored) {
                        break;
                    }
                    if (entity == null || HitJumpMod.isEntityDead(entity)) {
                        continue;
                    }

                    boolean isSelf = (entity == player);
                    if (isSelf && (!config.hitboxAnimEnabled || !config.hitboxAnimShowSelf || !thirdPerson)) {
                        continue;
                    }

                    boolean isPlayer = playerBaseClass != null && playerBaseClass.isInstance(entity);
                    boolean isLiving = livingBaseClass != null && livingBaseClass.isInstance(entity);
                    boolean isArmorStand = isArmorStandEntity(entity, livingBaseClass);
                    boolean isTnt = !isLiving && isPrimedTnt(entity, entityBaseClass, livingBaseClass);

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

                    // Self-preview only renders the Hitbox Animation in F5
                    if (isSelf) {
                        if (hasValidBox) {
                            HitboxAnimationRenderer.renderEntityAura(entity, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, config);
                        }
                        continue;
                    }

                    // Lazy-computed line of sight for this entity
                    int losCache = -1; // -1=unknown, 0=blocked, 1=visible

                    if (needHitboxPass && hasValidBox && distSq <= hitboxMaxDistSq) {
                        boolean targetMatch = (config.hitboxTargetMode == 1) ? isPlayer
                                : (config.hitboxTargetMode == 0 ? (isLiving && !isArmorStand) : true);
                        if (targetMatch) {
                            boolean visible = true;
                            if (!config.hitboxThroughWalls) {
                                losCache = hasLineOfSight(client, eyeX, eyeY, eyeZ,
                                        bMinX + camX, bMinY + camY, bMinZ + camZ,
                                        bMaxX + camX, bMaxY + camY, bMaxZ + camZ) ? 1 : 0;
                                visible = (losCache == 1);
                            }
                            if (visible) {
                                boolean invisible = isEntityInvisible(entity);
                                if (config.wallHitboxesEnabled || config.hitboxEyeLineEnabled) {
                                    drawHitboxAndEyeLine(
                                            bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ,
                                            isPlayer, isLiving,
                                            config.wallHitboxesEnabled,
                                            config.hitboxEyeLineEnabled,
                                            invisible && config.invisHitboxEnabled,
                                            invisR, invisG, invisB
                                    );
                                }
                                if (config.hitboxAnimEnabled) {
                                    HitboxAnimationRenderer.renderEntityAura(
                                            entity, bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, config
                                    );
                                }
                            }
                        }
                    }

                    if (hitOverlays != null && isLiving && !isArmorStand && hasValidBox && distSq <= hitboxMaxDistSq) {
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

                    // 3. Collect Tags (TNT Timer, Nametags, Standalone Distance, Sharpness+Armor Enchants)
                    if (tagsToRender != null) {
                        if (isTnt && config.tntTimerEnabled && distSq <= nametagMaxDistSq) {
                            int fuse = getTntFuse(entity);
                            if (fuse >= 0 && fuse <= 200) {
                                float secs = Math.max(0.0F, (fuse - tickDelta) / 20.0F);
                                String tntStr = String.format("TNT %.1fs", secs);
                                int color = (secs > 2.5F) ? 0xFF55FF55 : (secs > 1.0F ? 0xFFFFFF55 : 0xFFFF5555);
                                double tagY = hasValidBox ? (bMaxY + 0.35D) : (relY + 1.15D);
                                tagsToRender.add(new TagRenderEntry(null, null, null, tntStr, color, false, relX, tagY, relZ, distSq));
                            }
                            continue;
                        }

                        // Preserve visible local holograms (Bedwars shop/generators) when vanilla nametags are suppressed
                        if (isArmorStand && config.nametagIgnoreHolograms) {
                            if (vanillaTagSuppressed && distSq <= 1024.0D) {
                                String holo = getCustomNameTag(entity);
                                if (holo != null && !holo.trim().isEmpty()) {
                                    if (losCache == -1) {
                                        losCache = hasLineOfSight(client, eyeX, eyeY, eyeZ,
                                                interpX - 0.25D, interpY, interpZ - 0.25D,
                                                interpX + 0.25D, interpY + boxHeight, interpZ + 0.25D) ? 1 : 0;
                                    }
                                    if (losCache == 1) {
                                        double tagY = hasValidBox ? (bMaxY + 0.30D) : (relY + boxHeight + 0.30D);
                                        tagsToRender.add(new TagRenderEntry(null, holo, null, null, 0xFFFFFFFF, true, relX, tagY, relZ, distSq));
                                    }
                                }
                            }
                            continue;
                        }

                        boolean nameFilterMatch = matchesNametagFilter(config, isPlayer, isLiving, isArmorStand);

                        String nameLine = null;
                        if (config.wallNametagsEnabled && nameFilterMatch && distSq <= nametagMaxDistSq) {
                            nameLine = getEntityTagLabel(entity, isPlayer);
                        }

                        String distLine = null;
                        if (config.showDistanceTag && nameFilterMatch && distSq <= distanceMaxDistSq) {
                            if (isPlayer || isLiving) {
                                int distInt = (int) Math.round(Math.sqrt(distSq));
                                distLine = distInt + (config.distanceUnitBlocks ? "B" : "M");
                            }
                        }

                        String enchantLine = null;
                        if (config.armorEnchantInfoEnabled && isLiving && !isArmorStand && distSq <= enchantMaxDistSq) {
                            if (nameFilterMatch || isPlayer) {
                                enchantLine = getCombatEnchantSummary(entity, config.enchantShowWeapon);
                            }
                        }

                        if (nameLine != null || distLine != null || enchantLine != null) {
                            double tagY = hasValidBox ? (bMaxY + 0.45D) : (relY + boxHeight + 0.45D);
                            tagsToRender.add(new TagRenderEntry(
                                    distLine, nameLine, enchantLine, null, 0xFFFFFFFF, false,
                                    relX, tagY, relZ, distSq
                            ));
                        }
                    }
                }
            }

            // 4. Depth-tested Custom Hit Color overlays
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

            // 5. Render Nametags, Standalone Distance Tags, Sharpness+Armor Enchants & TNT Timers
            if (tagsToRender != null && !tagsToRender.isEmpty() && textRendererObj != null) {
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
                    TagRenderEntry entry = tagsToRender.get(i);
                    if (entry.depthTestedOnly) {
                        GL11.glEnable(GL11.GL_DEPTH_TEST);
                        GL11.glDepthFunc(GL11.GL_LEQUAL);
                    } else {
                        GL11.glDisable(GL11.GL_DEPTH_TEST);
                    }
                    drawWorldTagStack(entry, config, viewYaw, viewPitch, bgR, bgG, bgB, bgA);
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

    private static boolean matchesNametagFilter(HitJumpConfig config, boolean isPlayer, boolean isLiving, boolean isArmorStand) {
        if (isArmorStand && config.nametagIgnoreHolograms) {
            return false;
        }
        if (config.nametagTargetMode == 1) {
            return isPlayer;
        }
        if (config.nametagTargetMode == 0) {
            return isLiving;
        }
        return true;
    }

    private static void syncVanillaNametagOverride(Minecraft client, HitJumpConfig config) {
        boolean wantSuppress = config.wallNametagsEnabled && config.overrideVanillaNametags;
        if (!wantSuppress && !vanillaTagSuppressed) {
            return;
        }
        ensureVanillaTagRangeFields(client);
        if (vanillaNameRangeField == null) {
            return;
        }
        try {
            if (wantSuppress && !vanillaTagSuppressed) {
                vanillaNameRangeField.setFloat(null, 0.0F);
                if (vanillaSneakNameRangeField != null) {
                    vanillaSneakNameRangeField.setFloat(null, 0.0F);
                }
                vanillaTagSuppressed = true;
            } else if (!wantSuppress && vanillaTagSuppressed) {
                vanillaNameRangeField.setFloat(null, 64.0F);
                if (vanillaSneakNameRangeField != null) {
                    vanillaSneakNameRangeField.setFloat(null, 32.0F);
                }
                vanillaTagSuppressed = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void ensureVanillaTagRangeFields(Minecraft client) {
        if (vanillaTagRangeSearched || client == null) {
            return;
        }
        vanillaTagRangeSearched = true;
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

            // Scan RenderManager's Maps for any LivingEntityRenderer subclass
            Field[] subFields;
            try {
                subFields = val.getClass().getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field sf : subFields) {
                if (Modifier.isStatic(sf.getModifiers()) || !Map.class.isAssignableFrom(sf.getType())) continue;
                Object mapObj;
                try {
                    sf.setAccessible(true);
                    mapObj = sf.get(val);
                } catch (Throwable ignored) {
                    continue;
                }
                if (!(mapObj instanceof Map)) continue;
                for (Object renderer : ((Map<?, ?>) mapObj).values()) {
                    if (renderer == null) continue;
                    if (bindStaticTagRangeFields(renderer.getClass())) {
                        return;
                    }
                }
            }
        }
    }

    private static boolean bindStaticTagRangeFields(Class<?> startClass) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
            Field f64 = null, f32 = null;
            Field[] fields;
            try {
                fields = cls.getDeclaredFields();
            } catch (Throwable ignored) {
                return false;
            }
            for (Field f : fields) {
                if (!Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()) || f.getType() != float.class) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    float v = f.getFloat(null);
                    if (Math.abs(v - 64.0F) < 0.01F) f64 = f;
                    else if (Math.abs(v - 32.0F) < 0.01F) f32 = f;
                } catch (Throwable ignored) {
                }
            }
            if (f64 != null && f32 != null) {
                vanillaNameRangeField = f64;
                vanillaSneakNameRangeField = f32;
                return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static boolean hasLineOfSight(
            Minecraft client,
            double eyeX, double eyeY, double eyeZ,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ
    ) {
        ensureRayTrace(client.world);
        if (worldRayTraceMethod == null || vec3Constructor == null) {
            return true;
        }
        double cx = (minX + maxX) * 0.5D;
        double cy = (minY + maxY) * 0.5D;
        double cz = (minZ + maxZ) * 0.5D;
        double headY = minY + (maxY - minY) * 0.85D;
        double feetY = minY + (maxY - minY) * 0.15D;

        if (isRayClear(client.world, eyeX, eyeY, eyeZ, cx, headY, cz)) return true;
        if (isRayClear(client.world, eyeX, eyeY, eyeZ, cx, cy, cz)) return true;
        if (isRayClear(client.world, eyeX, eyeY, eyeZ, cx, feetY, cz)) return true;
        if (isRayClear(client.world, eyeX, eyeY, eyeZ, minX, cy, minZ)) return true;
        if (isRayClear(client.world, eyeX, eyeY, eyeZ, maxX, cy, maxZ)) return true;
        return false;
    }

    private static boolean isRayClear(Object world, double x0, double y0, double z0, double x1, double y1, double z1) {
        try {
            Object v0 = vec3Constructor.newInstance(x0, y0, z0);
            Object v1 = vec3Constructor.newInstance(x1, y1, z1);
            Object hit = worldRayTraceMethod.invoke(world, v0, v1, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE);
            if (hit == null) {
                return true;
            }
            if (hitTypeField != null) {
                Object t = hitTypeField.get(hit);
                if (t instanceof Enum && ((Enum<?>) t).ordinal() == 0) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void ensureRayTrace(Object world) {
        if (rayTraceSearched || world == null) {
            return;
        }
        rayTraceSearched = true;
        try {
            for (Method m : world.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 5
                        && p[0] == p[1]
                        && !p[0].isPrimitive()
                        && !isSkippableClass(p[0])
                        && p[2] == boolean.class
                        && p[3] == boolean.class
                        && p[4] == boolean.class
                        && m.getReturnType() != void.class
                        && !m.getReturnType().isPrimitive()) {
                    try {
                        Constructor<?> ctor = p[0].getConstructor(double.class, double.class, double.class);
                        worldRayTraceMethod = m;
                        vec3Constructor = ctor;
                        return;
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isArmorStandEntity(Object entity, Class<?> livingBaseClass) {
        if (entity == null || livingBaseClass == null) return false;
        Class<?> cls = entity.getClass();
        if (armorStandChecked && armorStandClass != null) {
            return cls == armorStandClass;
        }
        if (cls.getSuperclass() != livingBaseClass) return false;

        int rotFields = 0;
        try {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (!ft.isPrimitive() && !ft.isArray() && !isSkippableClass(ft) && hasThreeFloats(ft)) {
                    rotFields++;
                }
            }
            if (rotFields >= 6) {
                armorStandClass = cls;
                armorStandChecked = true;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean hasThreeFloats(Class<?> cls) {
        int floats = 0, total = 0;
        try {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                total++;
                if (f.getType() == float.class) floats++;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return floats == 3 && total == 3;
    }

    // ---------- Block Highlight & Face-Lock Helpers ----------

    public static int[] getCrosshairBlockAndSide(Minecraft client) {
        if (client == null || client.player == null) return null;
        Class<?> entityBase = HitJumpMod.getEntityBaseClass(client.player);
        Object hit = getCrosshairHitResult(client, entityBase);
        if (hit == null || hit
