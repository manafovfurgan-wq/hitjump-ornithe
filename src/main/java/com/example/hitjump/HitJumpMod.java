package com.example.hitjump;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
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
import java.util.Random;

public class HitJumpMod implements ClientModInitializer {
    private static final Random RANDOM = new Random();
    private static Minecraft cachedClient = null;

    // Captured 3D camera matrices for through-wall ESP / nametag rendering
    private static final FloatBuffer PROJECTION_MATRIX = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final FloatBuffer MODELVIEW_MATRIX = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static boolean cameraCapturedThisFrame = false;

    // HitJump state
    private static int lastHurtTime = 0;
    private static int pendingJumpTicks = -1;
    private static int recentCombatHurtWindow = 0;
    private static boolean wasOnGround = true;
    private static boolean wasMenuKeyDown = false;

    // GodPlace state
    private static boolean godPlaceForcingUseKey = false;

    // 1.7 Smooth Sneak state
    private static float smoothEyeHeight = -1.0F;
    private static long lastCameraTimeNanos = 0L;

    // Cached reflective fields
    private static Field hurtTimeField = null;
    private static boolean hurtTimeSearched = false;

    private static Field jumpTicksField = null;
    private static boolean jumpTicksSearched = false;

    private static Field attackCooldownField = null;
    private static boolean attackCooldownSearched = false;

    private static Field rightClickDelayField = null;
    private static boolean rightClickDelaySearched = false;

    private static boolean keyBindingsSearched = false;
    private static Object useKeyBindingObj = null;
    private static Object pickKeyBindingObj = null;
    private static Field keyBindingKeyCodeField = null;
    private static Field keyBindingPressedField = null;
    private static Field keyBindingPressTimeField = null;

    private static boolean entityMotionSearched = false;
    private static Field entityPrevXField = null;
    private static Field entityPrevYField = null;
    private static Field entityPrevZField = null;
    private static Field entityXField = null;
    private static Field entityYField = null;
    private static Field entityZField = null;
    private static Field entityVelYField = null;
    private static Field entityYawField = null;
    private static Field entityPitchField = null;
    private static Field entityPrevYawField = null;
    private static Field entityPrevPitchField = null;
    private static Field entityBoxField = null;
    private static Field entityDeadField = null;
    private static Field entityLastTickXField = null;
    private static Field entityLastTickYField = null;
    private static Field entityLastTickZField = null;

    private static boolean boxFieldsSearched = false;
    private static Field boxMinXField = null;
    private static Field boxMinYField = null;
    private static Field boxMinZField = null;
    private static Field boxMaxXField = null;
    private static Field boxMaxYField = null;
    private static Field boxMaxZField = null;

    private static boolean nameAccessSearched = false;
    private static Field dataWatcherField = null;
    private static Method dataWatcherGetStringMethod = null;
    private static Field gameProfileField = null;
    private static Method gameProfileGetNameMethod = null;
    private static Method entityGetDisplayNameMethod = null;
    private static Method textFormattedMethod = null;
    private static boolean textFormattedSearched = false;

    private static boolean textRendererSearched = false;
    private static Object textRendererObj = null;
    private static Method textGetWidthMethod = null;
    private static Method textDrawShadowMethod = null;

    private static boolean headYawSearched = false;
    private static Field headYawField = null;
    private static Field prevHeadYawField = null;

    private static boolean inputSneakSearched = false;
    private static Field playerInputField = null;
    private static Field inputSneakField = null;

    private static boolean perspectiveSearched = false;
    private static Field optionsObjectField = null;
    private static Field thirdPersonViewField = null;

    private static class TagRenderEntry {
        final String label;
        final double relX;
        final double relY;
        final double relZ;
        final double distSq;

        TagRenderEntry(String label, double relX, double relY, double relZ, double distSq) {
            this.label = label;
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.distSq = distSq;
        }
    }

    @Override
    public void onInitializeClient() {
        HitJumpConfig.load();
    }

    public static void onClientTick(Minecraft client) {
        if (client == null) {
            return;
        }
        cachedClient = client;

        HitJumpConfig config = HitJumpConfig.getInstance();

        // Open settings menu using the configured keybind
        boolean isMenuKeyDown = isKeyDownSafe(config.menuKeyCode);
        if (isMenuKeyDown && !wasMenuKeyDown && client.screen == null && client.player != null) {
            client.openScreen(new HitJumpConfigScreen(null));
        }
        wasMenuKeyDown = isMenuKeyDown;

        if (client.player == null || client.world == null) {
            releaseForcedUseKey(true);
            lastHurtTime = 0;
            pendingJumpTicks = -1;
            recentCombatHurtWindow = 0;
            wasOnGround = true;
            smoothEyeHeight = -1.0F;
            return;
        }

        // 1.7 Head Position fix (MC-67665)
        if (config.headFixEnabled) {
            syncHeadYawInternal(client);
        }

        // GodPlace: ONLY active while the dedicated GodPlace keybind (default: Middle Mouse) is held.
        // Regular Right Click (Mouse Button 2) stays 100% vanilla.
        boolean godPlaceActive = config.godPlaceEnabled
                && client.screen == null
                && isBindDown(config.godPlaceKeyCode);

        if (godPlaceActive) {
            ensureKeyBindingFields(client);
            setUseKeyPressed(true);
            godPlaceForcingUseKey = true;
            if (config.godPlaceDelayTicks == 0) {
                applyGodPlace(client, 0);
            }
        } else if (godPlaceForcingUseKey) {
            releaseForcedUseKey(client.screen != null);
        }

        // NoHitDelay: clamp vanilla's 10-tick miss penalty (never touching GUI-close 10000 lock)
        if (config.noHitDelayEnabled) {
            applyNoHitDelay(client, config.hitDelayTicks);
        }

        // NoJumpDelay: clamp vanilla's 10-tick jump cooldown before movement ticks
        if (config.noJumpDelayEnabled) {
            applyNoJumpDelay(client.player, config.jumpDelayTicks);
        }

        // HitJump: only trigger on actual knockback hits (ignores fall, fire, cactus, poison, void)
        if (!config.enabled) {
            lastHurtTime = 0;
            pendingJumpTicks = -1;
            recentCombatHurtWindow = 0;
            wasOnGround = client.player.onGround;
            return;
        }

        int currentHurtTime = getHurtTime(client.player);
        double velY = getVelocityY(client.player);
        boolean hasUpwardKnockback = velY > 0.05D;
        boolean justTookDamage = currentHurtTime > lastHurtTime && currentHurtTime > 0;

        if (justTookDamage) {
            if (hasUpwardKnockback) {
                triggerHitJumpIfValid(client, config);
                recentCombatHurtWindow = 0;
            } else if (wasOnGround && client.player.onGround) {
                // Allow a 2-tick grace window in case server velocity packet arrives 1 tick after hurt packet
                recentCombatHurtWindow = 2;
            } else {
                // Player just landed from a fall -> ignore fall damage
                recentCombatHurtWindow = 0;
            }
        } else if (recentCombatHurtWindow > 0) {
            recentCombatHurtWindow--;
            if (hasUpwardKnockback) {
                triggerHitJumpIfValid(client, config);
                recentCombatHurtWindow = 0;
            }
        }

        lastHurtTime = currentHurtTime;
        wasOnGround = client.player.onGround;

        if (pendingJumpTicks > 0) {
            pendingJumpTicks--;
            if (pendingJumpTicks == 0) {
                tryJump(client);
                pendingJumpTicks = -1;
            }
        }
    }

    public static void onClientTickEnd(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (config.godPlaceEnabled && client.screen == null && isBindDown(config.godPlaceKeyCode)) {
            applyGodPlace(client, config.godPlaceDelayTicks);
            clearPickKeyPressTimeIfShared(client, config.godPlaceKeyCode);
        }
        if (config.headFixEnabled) {
            syncHeadYawInternal(client);
        }
    }

    public static boolean shouldBlockPickBlock() {
        if (cachedClient == null || cachedClient.player == null || cachedClient.screen != null) {
            return false;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.godPlaceEnabled) {
            return false;
        }
        ensureKeyBindingFields(cachedClient);
        int pickCode = getPickKeyCode();
        if (pickCode != 0 && config.godPlaceKeyCode == pickCode) {
            return true;
        }
        return isBindDown(config.godPlaceKeyCode);
    }

    public static void onBeforeAttack(Minecraft client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (config.headFixEnabled) {
            syncHeadYawInternal(client);
        }
        if (config.noHitDelayEnabled) {
            applyNoHitDelay(client, config.hitDelayTicks);
        }
    }

    public static void onAfterAttack(Minecraft client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (config.noHitDelayEnabled) {
            applyNoHitDelay(client, config.hitDelayTicks);
        }
    }

    public static void syncHeadYaw() {
        if (cachedClient == null || cachedClient.player == null) {
            return;
        }
        if (HitJumpConfig.getInstance().headFixEnabled) {
            syncHeadYawInternal(cachedClient);
        }
    }

    public static void onTransformCameraHead(float tickDelta) {
        if (cachedClient == null || cachedClient.player == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (config.headFixEnabled) {
            syncHeadYawInternal(cachedClient);
        }
        // Parallax Fix (MC-1846): cancel vanilla 1.8.9's -0.1F backward first-person camera shift
        if (config.parallaxFixEnabled && getPerspective(cachedClient) == 0) {
            GL11.glTranslatef(0.0F, 0.0F, 0.1F);
        }
    }

    public static void onTransformCameraTail(float tickDelta) {
        if (cachedClient == null || cachedClient.player == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.smoothSneakEnabled) {
            smoothEyeHeight = -1.0F;
            lastCameraTimeNanos = 0L;
        } else {
            long now = System.nanoTime();
            float dt = 0.016F;
            if (lastCameraTimeNanos != 0L) {
                dt = (now - lastCameraTimeNanos) / 1_000_000_000.0F;
                if (dt < 0.0F) dt = 0.0F;
                if (dt > 0.1F) dt = 0.1F;
            }
            lastCameraTimeNanos = now;

            boolean sneaking = isPlayerSneaking(cachedClient.player);
            float vanillaEyeHeight = sneaking ? 1.54F : 1.62F;

            if (smoothEyeHeight < 0.0F) {
                smoothEyeHeight = vanillaEyeHeight;
            } else {
                float speed = sneaking ? 16.0F : 20.0F;
                float factor = 1.0F - (float) Math.exp(-speed * dt);
                smoothEyeHeight += (vanillaEyeHeight - smoothEyeHeight) * factor;
                if (Math.abs(vanillaEyeHeight - smoothEyeHeight) < 0.0005F) {
                    smoothEyeHeight = vanillaEyeHeight;
                }
            }

            float delta = vanillaEyeHeight - smoothEyeHeight;
            if (Math.abs(delta) > 0.0001F) {
                GL11.glTranslatef(0.0F, delta, 0.0F);
            }
        }

        // Capture the finalized 3D projection & modelview matrices for through-wall ESP/nametags
        PROJECTION_MATRIX.clear();
        MODELVIEW_MATRIX.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION_MATRIX);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW_MATRIX);
        cameraCapturedThisFrame = true;
    }

    public static void onRenderWorldTail(float tickDelta) {
        if (!cameraCapturedThisFrame || cachedClient == null || cachedClient.player == null || cachedClient.world == null) {
            return;
        }
        cameraCapturedThisFrame = false;

        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.wallHitboxesEnabled && !config.wallNametagsEnabled) {
            return;
        }

        Object player = cachedClient.player;
        ensureEntityMotionFields(player);
        ensureNameAccessFields(player);
        ensureTextRenderer(cachedClient);

        if (entityXField == null || entityPrevXField == null || entityBoxField == null) {
            return;
        }

        List<?> entities = getLoadedEntityList(cachedClient);
        if (entities == null || entities.isEmpty()) {
            return;
        }

        Class<?> playerBaseClass = getPlayerBaseClass(player);
        Class<?> livingBaseClass = getLivingBaseClass(player);

        double camX;
        double camY;
        double camZ;
        float viewYaw;
        float viewPitch;
        try {
            double pPrevX = entityPrevXField.getDouble(player);
            double pPrevY = entityPrevYField.getDouble(player);
            double pPrevZ = entityPrevZField.getDouble(player);
            camX = pPrevX + (entityXField.getDouble(player) - pPrevX) * tickDelta;
            camY = pPrevY + (entityYField.getDouble(player) - pPrevY) * tickDelta;
            camZ = pPrevZ + (entityZField.getDouble(player) - pPrevZ) * tickDelta;

            float pPrevYaw = entityPrevYawField.getFloat(player);
            float pYaw = entityYawField.getFloat(player);
            float pPrevPitch = entityPrevPitchField.getFloat(player);
            float pPitch = entityPitchField.getFloat(player);
            viewYaw = pPrevYaw + (pYaw - pPrevYaw) * tickDelta;
            viewPitch = pPrevPitch + (pPitch - pPrevPitch) * tickDelta;

            if (getPerspective(cachedClient) == 2) {
                viewYaw += 180.0F;
                viewPitch = -viewPitch;
            }
        } catch (Exception ignored) {
            return;
        }

        double hitboxMaxDistSq = (double) config.hitboxRange * (double) config.hitboxRange;
        double nametagMaxDistSq = (double) config.nametagRange * (double) config.nametagRange;
        List<TagRenderEntry> tagsToRender = config.wallNametagsEnabled ? new ArrayList<TagRenderEntry>() : null;

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
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            if (config.wallHitboxesEnabled) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glLineWidth(1.5F);
            }

            int count = entities.size();
            for (int i = 0; i < count; i++) {
                Object entity;
                try {
                    entity = entities.get(i);
                } catch (Exception ignored) {
                    break;
                }
                if (entity == null || entity == player) {
                    continue;
                }
                if (isEntityDead(entity)) {
                    continue;
                }

                boolean isPlayer = playerBaseClass != null && playerBaseClass.isInstance(entity);
                boolean isLiving = livingBaseClass != null && livingBaseClass.isInstance(entity);

                double ex;
                double ey;
                double ez;
                double interpX;
                double interpY;
                double interpZ;
                Object boxObj;
                try {
                    ex = entityXField.getDouble(entity);
                    ey = entityYField.getDouble(entity);
                    ez = entityZField.getDouble(entity);

                    double prevX = (entityLastTickXField != null)
                            ? entityLastTickXField.getDouble(entity)
                            : entityPrevXField.getDouble(entity);
                    double prevY = (entityLastTickYField != null)
                            ? entityLastTickYField.getDouble(entity)
                            : entityPrevYField.getDouble(entity);
                    double prevZ = (entityLastTickZField != null)
                            ? entityLastTickZField.getDouble(entity)
                            : entityPrevZField.getDouble(entity);

                    interpX = prevX + (ex - prevX) * tickDelta;
                    interpY = prevY + (ey - prevY) * tickDelta;
                    interpZ = prevZ + (ez - prevZ) * tickDelta;
                    boxObj = entityBoxField.get(entity);
                } catch (Exception ignored) {
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
                        } catch (Exception ignored) {
                        }
                    }
                }

                // 1. Through-wall Hitbox
                if (config.wallHitboxesEnabled && hasValidBox && distSq <= hitboxMaxDistSq) {
                    boolean targetMatch;
                    if (config.hitboxTargetMode == 1) {
                        targetMatch = isPlayer;
                    } else if (config.hitboxTargetMode == 0) {
                        targetMatch = isLiving;
                    } else {
                        targetMatch = true;
                    }
                    if (targetMatch) {
                        drawWireframeHitbox(bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, isPlayer, isLiving);
                    }
                }

                // 2. Collect Through-wall Nametags (players + mobs/entities with custom nametags)
                if (tagsToRender != null && distSq <= nametagMaxDistSq) {
                    String label = getEntityTagLabel(entity, isPlayer);
                    if (label != null && !label.isEmpty()) {
                        double tagY = hasValidBox ? (bMaxY + 0.45D) : (relY + boxHeight + 0.45D);
                        tagsToRender.add(new TagRenderEntry(label, relX, tagY, relZ, distSq));
                    }
                }
            }

            // 3. Render Nametags sorted farthest-to-nearest so closer tags draw on top
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
                    drawWorldNametag(entry, viewYaw, viewPitch, bgR, bgG, bgB, bgA);
                }
            }
        } catch (Exception ignored) {
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void drawWireframeHitbox(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            boolean isPlayer, boolean isLiving
    ) {
        if (isPlayer) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.95F);
        } else {
            GL11.glColor4f(0.85F, 0.92F, 1.0F, 0.78F);
        }

        GL11.glBegin(GL11.GL_LINES);
        // Bottom face
        GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, minY, minZ);

        // Top face
        GL11.glVertex3d(minX, maxY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ); GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ); GL11.glVertex3d(minX, maxY, minZ);

        // Vertical edges
        GL11.glVertex3d(minX, minY, minZ); GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, minZ); GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ); GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ); GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glEnd();

        // Classic red eye-level band for living entities (like F3+B)
        if (isLiving) {
            double h = maxY - minY;
            double eyeY = minY + (isPlayer ? Math.min(h - 0.12D, h * 0.90D) : (h * 0.85D));
            GL11.glColor4f(1.0F, 0.2F, 0.2F, 0.95F);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(minX, eyeY, minZ); GL11.glVertex3d(maxX, eyeY, minZ);
            GL11.glVertex3d(maxX, eyeY, minZ); GL11.glVertex3d(maxX, eyeY, maxZ);
            GL11.glVertex3d(maxX, eyeY, maxZ); GL11.glVertex3d(minX, eyeY, maxZ);
            GL11.glVertex3d(minX, eyeY, maxZ); GL11.glVertex3d(minX, eyeY, minZ);
            GL11.glEnd();
        }
    }

    private static void drawWorldNametag(
            TagRenderEntry entry,
            float viewYaw,
            float viewPitch,
            float bgR, float bgG, float bgB, float bgA
    ) {
        int textWidth = getTextWidth(entry.label);
        if (textWidth <= 0) {
            return;
        }
        int halfWidth = textWidth / 2;

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

        if (bgA > 0.005F) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(bgR, bgG, bgB, bgA);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3d(-halfWidth - 2, -2, 0.0D);
            GL11.glVertex3d(-halfWidth - 2, 9, 0.0D);
            GL11.glVertex3d(halfWidth + 2, 9, 0.0D);
            GL11.glVertex3d(halfWidth + 2, -2, 0.0D);
            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTextWithShadow(entry.label, -halfWidth, 0, 0xFFFFFFFF);

        GL11.glPopMatrix();
    }

    public static int getTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (cachedClient != null) {
            ensureTextRenderer(cachedClient);
        }
        if (textRendererObj != null && textGetWidthMethod != null) {
            try {
                return ((Integer) textGetWidthMethod.invoke(textRendererObj, text)).intValue();
            } catch (Exception ignored) {
            }
        }
        return text.length() * 6;
    }

    private static void drawTextWithShadow(String text, float x, float y, int color) {
        if (textRendererObj != null && textDrawShadowMethod != null) {
            try {
                textDrawShadowMethod.invoke(textRendererObj, text, x, y, color);
            } catch (Exception ignored) {
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
            } catch (Exception ignored) {
                continue;
            }
            if (val == null || val.getClass().isArray()) continue;

            Method foundWidth = null;
            Method foundShadow = null;
            Method foundPlain = null;
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

        // For non-player entities (mobs, villagers, named armor stands): only show if they have a custom nametag
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
                    } catch (Exception ignored) {
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
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void ensureNameAccessFields(Object player) {
        if (nameAccessSearched || player == null) {
            return;
        }
        nameAccessSearched = true;

        Class<?> entityClass = getEntityBaseClass(player);
        Class<?> playerBaseClass = getPlayerBaseClass(player);

        // Locate DataWatcher field on Entity (has (int)->byte, (int)->int, (int)->String)
        if (entityClass != null) {
            for (Field f : entityClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                Class<?> ft = f.getType();
                Method strM = null;
                boolean hasByte = false;
                boolean hasInt = false;
                for (Method m : ft.getMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && p[0] == int.class) {
                        if (m.getReturnType() == String.class) strM = m;
                        else if (m.getReturnType() == byte.class) hasByte = true;
                        else if (m.getReturnType() == int.class) hasInt = true;
                    }
                }
                if (strM != null && hasByte && hasInt) {
                    f.setAccessible(true);
                    dataWatcherField = f;
                    dataWatcherGetStringMethod = strM;
                    break;
                }
            }

            // Locate getDisplayName() on Entity (0-arg public method returning non-java.* interface)
            for (Method m : entityClass.getMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                Class<?> rt = m.getReturnType();
                if (rt.isInterface() && !rt.getName().startsWith("java.")) {
                    entityGetDisplayNameMethod = m;
                    break;
                }
            }
        }

        // Locate GameProfile field on PlayerEntity as rock-solid fallback for player usernames
        if (playerBaseClass != null) {
            for (Field f : playerBaseClass.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if ("com.mojang.authlib.GameProfile".equals(f.getType().getName())) {
                    f.setAccessible(true);
                    gameProfileField = f;
                    try {
                        gameProfileGetNameMethod = f.getType().getMethod("getName");
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        }
    }

    private static Class<?> getPlayerBaseClass(Object player) {
        Class<?> c = player.getClass().getSuperclass(); // ClientPlayerEntity
        return (c != null) ? c.getSuperclass() : null;  // PlayerEntity
    }

    private static Class<?> getLivingBaseClass(Object player) {
        Class<?> c = getPlayerBaseClass(player);
        return (c != null) ? c.getSuperclass() : null;  // LivingEntity
    }

    private static Class<?> getEntityBaseClass(Object player) {
        Class<?> c = getLivingBaseClass(player);
        return (c != null) ? c.getSuperclass() : null;  // Entity
    }

    private static boolean isEntityDead(Object entity) {
        if (entityDeadField != null) {
            try {
                return entityDeadField.getBoolean(entity);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static void ensureBoxFields(Object boxObj) {
        if (boxFieldsSearched || boxObj == null) {
            return;
        }
        boxFieldsSearched = true;
        Field[] fields = boxObj.getClass().getDeclaredFields();
        Field[] doubles = new Field[6];
        int idx = 0;
        for (Field f : fields) {
            if (isInstanceField(f, double.class)) {
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
    }

    private static List<?> getLoadedEntityList(Minecraft client) {
        try {
            List<?> bestList = null;
            Class<?> worldCls = client.world.getClass();
            while (worldCls != null && worldCls != Object.class) {
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
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isBindDown(int bindCode) {
        if (bindCode < 0) {
            int button = bindCode + 100;
            if (button < 0) {
                return false;
            }
            try {
                if (button < Mouse.getButtonCount()) {
                    return Mouse.isButtonDown(button);
                }
            } catch (Exception ignored) {
            }
            return false;
        }
        return isKeyDownSafe(bindCode);
    }

    private static void releaseForcedUseKey(boolean forceOff) {
        if (!godPlaceForcingUseKey) {
            return;
        }
        boolean physicallyHeld = !forceOff && isBindDown(getUseKeyCode());
        setUseKeyPressed(physicallyHeld);
        godPlaceForcingUseKey = false;
    }

    private static void setUseKeyPressed(boolean pressed) {
        if (useKeyBindingObj != null && keyBindingPressedField != null) {
            try {
                keyBindingPressedField.setBoolean(useKeyBindingObj, pressed);
            } catch (Exception ignored) {
            }
        }
    }

    private static int getUseKeyCode() {
        if (useKeyBindingObj != null && keyBindingKeyCodeField != null) {
            try {
                return keyBindingKeyCodeField.getInt(useKeyBindingObj);
            } catch (Exception ignored) {
            }
        }
        return -99; // Default Right Click
    }

    private static int getPickKeyCode() {
        if (pickKeyBindingObj != null && keyBindingKeyCodeField != null) {
            try {
                return keyBindingKeyCodeField.getInt(pickKeyBindingObj);
            } catch (Exception ignored) {
            }
        }
        return -98; // Default Middle Mouse
    }

    private static void clearPickKeyPressTimeIfShared(Minecraft client, int godPlaceKeyCode) {
        ensureKeyBindingFields(client);
        if (pickKeyBindingObj != null && keyBindingPressTimeField != null && getPickKeyCode() == godPlaceKeyCode) {
            try {
                keyBindingPressTimeField.setInt(pickKeyBindingObj, 0);
            } catch (Exception ignored) {
            }
        }
    }

    private static void ensureKeyBindingFields(Minecraft client) {
        if (keyBindingsSearched) {
            return;
        }
        keyBindingsSearched = true;
        for (Field clientField : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(clientField.getModifiers()) || clientField.getType().isPrimitive()) continue;
            Object maybeOptions;
            try {
                clientField.setAccessible(true);
                maybeOptions = clientField.get(client);
            } catch (Exception ignored) {
                continue;
            }
            if (maybeOptions == null || maybeOptions.getClass().isArray()) continue;

            for (Field optField : maybeOptions.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(optField.getModifiers()) || optField.getType().isPrimitive()) continue;
                Object val;
                try {
                    optField.setAccessible(true);
                    val = optField.get(maybeOptions);
                } catch (Exception ignored) {
                    continue;
                }
                if (val == null || val.getClass().isArray()) continue;
                String desc = getKeyBindingDescription(val);
                if ("key.use".equals(desc)) {
                    useKeyBindingObj = val;
                    locateKeyBindingInternalFields(val.getClass());
                } else if ("key.pickItem".equals(desc)) {
                    pickKeyBindingObj = val;
                    locateKeyBindingInternalFields(val.getClass());
                }
            }
            if (useKeyBindingObj != null) {
                return;
            }
        }
    }

    private static String getKeyBindingDescription(Object obj) {
        Field[] fields = obj.getClass().getDeclaredFields();
        if (fields.length > 20) {
            return null;
        }
        for (Field f : fields) {
            if (isInstanceField(f, String.class)) {
                try {
                    f.setAccessible(true);
                    Object s = f.get(obj);
                    if ("key.use".equals(s) || "key.pickItem".equals(s)) {
                        return (String) s;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static void locateKeyBindingInternalFields(Class<?> keyBindingClass) {
        if (keyBindingPressedField != null && keyBindingKeyCodeField != null) {
            return;
        }
        Field[] fields = keyBindingClass.getDeclaredFields();
        for (int i = 0; i < fields.length - 1; i++) {
            if (isInstanceField(fields[i], int.class)
                    && !Modifier.isFinal(fields[i].getModifiers())
                    && isInstanceField(fields[i + 1], boolean.class)) {
                keyBindingKeyCodeField = fields[i];
                keyBindingPressedField = fields[i + 1];
                keyBindingKeyCodeField.setAccessible(true);
                keyBindingPressedField.setAccessible(true);
                if (i + 2 < fields.length && isInstanceField(fields[i + 2], int.class)) {
                    keyBindingPressTimeField = fields[i + 2];
                    keyBindingPressTimeField.setAccessible(true);
                }
                return;
            }
        }
        for (Field f : fields) {
            if (keyBindingPressedField == null && isInstanceField(f, boolean.class)) {
                keyBindingPressedField = f;
                keyBindingPressedField.setAccessible(true);
            } else if (keyBindingKeyCodeField == null
                    && isInstanceField(f, int.class)
                    && !Modifier.isFinal(f.getModifiers())) {
                keyBindingKeyCodeField = f;
                keyBindingKeyCodeField.setAccessible(true);
            }
        }
    }

    private static void applyGodPlace(Minecraft client, int delayTicks) {
        if (!rightClickDelaySearched) {
            rightClickDelayField = findRightClickDelayField(client.getClass());
            rightClickDelaySearched = true;
        }
        if (rightClickDelayField != null) {
            try {
                int targetTimer = (delayTicks <= 0) ? 0 : Math.min(4, delayTicks + 1);
                int currentDelay = rightClickDelayField.getInt(client);
                if (currentDelay > targetTimer) {
                    rightClickDelayField.setInt(client, targetTimer);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Field findRightClickDelayField(Class<?> minecraftClass) {
        Field[] fields = minecraftClass.getDeclaredFields();
        for (int i = 0; i <= fields.length - 6; i++) {
            if (isInstanceField(fields[i], int.class)
                    && isInstanceField(fields[i + 1], String.class)
                    && isInstanceField(fields[i + 2], int.class)
                    && isInstanceField(fields[i + 3], boolean.class)
                    && isInstanceField(fields[i + 4], long.class)
                    && isInstanceField(fields[i + 5], int.class)) {
                fields[i].setAccessible(true);
                return fields[i];
            }
        }
        return null;
    }

    private static void triggerHitJumpIfValid(Minecraft client, HitJumpConfig config) {
        if (config.hitJumpPlayersOnly && !hasNearbyPlayer(client, 8.0D)) {
            return;
        }
        if (rollFrequency(config.jumpFrequency)) {
            if (config.latencyTicks <= 0) {
                tryJump(client);
                pendingJumpTicks = -1;
            } else {
                pendingJumpTicks = config.latencyTicks;
            }
        }
    }

    private static boolean hasNearbyPlayer(Minecraft client, double maxRange) {
        ensureEntityMotionFields(client.player);
        if (entityXField == null || entityYField == null || entityZField == null) {
            return true;
        }
        try {
            double px = entityXField.getDouble(client.player);
            double py = entityYField.getDouble(client.player);
            double pz = entityZField.getDouble(client.player);
            double maxDistSq = maxRange * maxRange;

            Class<?> clientPlayerBase = client.player.getClass().getSuperclass();
            List<?> bestList = null;

            Class<?> worldCls = client.world.getClass();
            while (worldCls != null && worldCls != Object.class) {
                for (Field f : worldCls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (!List.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object val = f.get(client.world);
                    if (val instanceof List) {
                        List<?> list = (List<?>) val;
                        if (list.contains(client.player)) {
                            if (bestList == null || list.size() < bestList.size()) {
                                bestList = list;
                            }
                        }
                    }
                }
                worldCls = worldCls.getSuperclass();
            }

            if (bestList == null) {
                return false;
            }

            for (int i = 0; i < bestList.size(); i++) {
                Object other = bestList.get(i);
                if (other == null || other == client.player) continue;
                if (clientPlayerBase != null && !clientPlayerBase.isInstance(other)) continue;

                double ox = entityXField.getDouble(other);
                double oy = entityYField.getDouble(other);
                double oz = entityZField.getDouble(other);

                double dx = ox - px;
                double dy = oy - py;
                double dz = oz - pz;
                if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void syncHeadYawInternal(Minecraft client) {
        Object player = client.player;
        ensureEntityMotionFields(player);
        ensureHeadYawFields(player);
        if (entityYawField == null || entityPrevYawField == null || headYawField == null || prevHeadYawField == null) {
            return;
        }
        try {
            headYawField.setFloat(player, entityYawField.getFloat(player));
            prevHeadYawField.setFloat(player, entityPrevYawField.getFloat(player));
        } catch (Exception ignored) {
        }
    }

    private static double getVelocityY(Object player) {
        ensureEntityMotionFields(player);
        if (entityVelYField != null) {
            try {
                return entityVelYField.getDouble(player);
            } catch (Exception ignored) {
            }
        }
        return 0.0D;
    }

    private static void ensureEntityMotionFields(Object entity) {
        if (entityMotionSearched) {
            return;
        }
        entityMotionSearched = true;
        Class<?> cls = entity.getClass();
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (int i = 0; i <= fields.length - 14; i++) {
                if (isInstanceField(fields[i], double.class)
                        && isInstanceField(fields[i + 1], double.class)
                        && isInstanceField(fields[i + 2], double.class)
                        && isInstanceField(fields[i + 3], double.class)
                        && isInstanceField(fields[i + 4], double.class)
                        && isInstanceField(fields[i + 5], double.class)
                        && isInstanceField(fields[i + 6], double.class)
                        && isInstanceField(fields[i + 7], double.class)
                        && isInstanceField(fields[i + 8], double.class)
                        && isInstanceField(fields[i + 9], float.class)
                        && isInstanceField(fields[i + 10], float.class)
                        && isInstanceField(fields[i + 11], float.class)
                        && isInstanceField(fields[i + 12], float.class)) {
                    entityPrevXField = fields[i];
                    entityPrevYField = fields[i + 1];
                    entityPrevZField = fields[i + 2];
                    entityXField = fields[i + 3];
                    entityYField = fields[i + 4];
                    entityZField = fields[i + 5];
                    entityVelYField = fields[i + 7];
                    entityYawField = fields[i + 9];
                    entityPitchField = fields[i + 10];
                    entityPrevYawField = fields[i + 11];
                    entityPrevPitchField = fields[i + 12];

                    entityPrevXField.setAccessible(true);
                    entityPrevYField.setAccessible(true);
                    entityPrevZField.setAccessible(true);
                    entityXField.setAccessible(true);
                    entityYField.setAccessible(true);
                    entityZField.setAccessible(true);
                    entityVelYField.setAccessible(true);
                    entityYawField.setAccessible(true);
                    entityPitchField.setAccessible(true);
                    entityPrevYawField.setAccessible(true);
                    entityPrevPitchField.setAccessible(true);

                    if (!Modifier.isStatic(fields[i + 13].getModifiers()) && !fields[i + 13].getType().isPrimitive()) {
                        entityBoxField = fields[i + 13];
                        entityBoxField.setAccessible(true);
                    }
                    if (i + 21 < fields.length && isInstanceField(fields[i + 21], boolean.class)) {
                        entityDeadField = fields[i + 21];
                        entityDeadField.setAccessible(true);
                    }
                    // Scan ahead for lastTickPosX, lastTickPosY, lastTickPosZ (3 consecutive doubles)
                    int maxScan = Math.min(fields.length - 3, i + 40);
                    for (int j = i + 14; j <= maxScan; j++) {
                        if (isInstanceField(fields[j], double.class)
                                && isInstanceField(fields[j + 1], double.class)
                                && isInstanceField(fields[j + 2], double.class)) {
                            entityLastTickXField = fields[j];
                            entityLastTickYField = fields[j + 1];
                            entityLastTickZField = fields[j + 2];
                            entityLastTickXField.setAccessible(true);
                            entityLastTickYField.setAccessible(true);
                            entityLastTickZField.setAccessible(true);
                            break;
                        }
                    }
                    return;
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static void ensureHeadYawFields(Object player) {
        if (headYawSearched) {
            return;
        }
        headYawSearched = true;
        Class<?> playerBaseClass = getPlayerBaseClass(player);
        Class<?> cls = player.getClass();
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (int i = 0; i <= fields.length - 9; i++) {
                if (isInstanceField(fields[i], float.class)
                        && isInstanceField(fields[i + 1], float.class)
                        && isInstanceField(fields[i + 2], float.class)
                        && isInstanceField(fields[i + 3], float.class)
                        && isInstanceField(fields[i + 4], float.class)
                        && !Modifier.isStatic(fields[i + 5].getModifiers())
                        && (playerBaseClass == null || fields[i + 5].getType() == playerBaseClass)
                        && isInstanceField(fields[i + 6], int.class)
                        && isInstanceField(fields[i + 7], boolean.class)
                        && isInstanceField(fields[i + 8], int.class)) {
                    headYawField = fields[i + 2];
                    prevHeadYawField = fields[i + 3];
                    headYawField.setAccessible(true);
                    prevHeadYawField.setAccessible(true);
                    return;
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static boolean isPlayerSneaking(Object player) {
        if (!inputSneakSearched) {
            inputSneakSearched = true;
            Class<?> cls = player.getClass();
            while (cls != null && cls != Object.class) {
                for (Field f : cls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    try {
                        f.setAccessible(true);
                        Object val = f.get(player);
                        if (val == null) continue;
                        Field sneak = findInputSneakField(val.getClass());
                        if (sneak != null) {
                            playerInputField = f;
                            inputSneakField = sneak;
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (inputSneakField != null) break;
                cls = cls.getSuperclass();
            }
        }
        if (playerInputField != null && inputSneakField != null) {
            try {
                Object input = playerInputField.get(player);
                if (input != null) {
                    return inputSneakField.getBoolean(input);
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static Field findInputSneakField(Class<?> startClass) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            if (fields.length >= 4 && fields.length <= 8) {
                for (int i = 0; i <= fields.length - 4; i++) {
                    if (isInstanceField(fields[i], float.class)
                            && isInstanceField(fields[i + 1], float.class)
                            && isInstanceField(fields[i + 2], boolean.class)
                            && isInstanceField(fields[i + 3], boolean.class)) {
                        fields[i + 3].setAccessible(true);
                        return fields[i + 3];
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static int getPerspective(Minecraft client) {
        if (!perspectiveSearched) {
            perspectiveSearched = true;
            for (Field f : client.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    Object obj = f.get(client);
                    if (obj == null) continue;
                    Field tpv = findThirdPersonViewField(obj.getClass());
                    if (tpv != null) {
                        optionsObjectField = f;
                        thirdPersonViewField = tpv;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (optionsObjectField != null && thirdPersonViewField != null) {
            try {
                Object opts = optionsObjectField.get(client);
                if (opts != null) {
                    return thirdPersonViewField.getInt(opts);
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static Field findThirdPersonViewField(Class<?> optionsClass) {
        Field[] fields = optionsClass.getDeclaredFields();
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
                fields[i + 1].setAccessible(true);
                return fields[i + 1];
            }
        }
        return null;
    }

    private static boolean isKeyDownSafe(int keyCode) {
        if (keyCode <= 0 || keyCode >= 256) {
            return false;
        }
        try {
            return Keyboard.isKeyDown(keyCode);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void applyNoHitDelay(Minecraft client, int maxDelayTicks) {
        if (!attackCooldownSearched) {
            attackCooldownField = findAttackCooldownField(client.getClass());
            attackCooldownSearched = true;
        }
        if (attackCooldownField != null) {
            try {
                int currentDelay = attackCooldownField.getInt(client);
                if (currentDelay > maxDelayTicks && currentDelay <= 10) {
                    attackCooldownField.setInt(client, maxDelayTicks);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Field findAttackCooldownField(Class<?> minecraftClass) {
        Field[] fields = minecraftClass.getDeclaredFields();
        for (int i = 0; i <= fields.length - 6; i++) {
            if (isInstanceField(fields[i], Screen.class)
                    && !Modifier.isStatic(fields[i + 1].getModifiers()) && !fields[i + 1].getType().isPrimitive()
                    && !Modifier.isStatic(fields[i + 2].getModifiers()) && !fields[i + 2].getType().isPrimitive()
                    && isInstanceField(fields[i + 3], int.class)
                    && isInstanceField(fields[i + 4], int.class)
                    && isInstanceField(fields[i + 5], int.class)) {
                fields[i + 3].setAccessible(true);
                return fields[i + 3];
            }
        }
        return null;
    }

    private static void applyNoJumpDelay(Object player, int maxDelayTicks) {
        if (!jumpTicksSearched) {
            jumpTicksField = findJumpTicksField(player.getClass());
            jumpTicksSearched = true;
        }
        if (jumpTicksField != null) {
            try {
                int currentDelay = jumpTicksField.getInt(player);
                if (currentDelay > maxDelayTicks) {
                    jumpTicksField.setInt(player, maxDelayTicks);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Field findJumpTicksField(Class<?> startClass) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (int i = 0; i <= fields.length - 8; i++) {
                if (isInstanceField(fields[i], boolean.class)
                        && isInstanceField(fields[i + 1], cls)
                        && isInstanceField(fields[i + 2], int.class)
                        && isInstanceField(fields[i + 3], cls)
                        && isInstanceField(fields[i + 4], int.class)
                        && isInstanceField(fields[i + 5], float.class)
                        && isInstanceField(fields[i + 6], int.class)
                        && isInstanceField(fields[i + 7], float.class)) {
                    fields[i + 6].setAccessible(true);
                    return fields[i + 6];
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static int getHurtTime(Object player) {
        if (!hurtTimeSearched) {
            hurtTimeField = findHurtTimeField(player.getClass());
            hurtTimeSearched = true;
        }
        if (hurtTimeField != null) {
            try {
                return hurtTimeField.getInt(player);
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static Field findHurtTimeField(Class<?> startClass) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            for (int i = 0; i <= fields.length - 9; i++) {
                if (isInstanceField(fields[i], boolean.class)
                        && isInstanceField(fields[i + 1], int.class)
                        && isInstanceField(fields[i + 2], int.class)
                        && isInstanceField(fields[i + 3], int.class)
                        && isInstanceField(fields[i + 4], int.class)
                        && isInstanceField(fields[i + 5], float.class)
                        && isInstanceField(fields[i + 6], int.class)
                        && isInstanceField(fields[i + 7], float.class)
                        && isInstanceField(fields[i + 8], float.class)) {
                    fields[i + 3].setAccessible(true);
                    return fields[i + 3];
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static boolean isInstanceField(Field field, Class<?> expectedType) {
        return !Modifier.isStatic(field.getModifiers()) && field.getType() == expectedType;
    }

    private static boolean rollFrequency(int frequencyPercent) {
        if (frequencyPercent <= 0) return false;
        if (frequencyPercent >= 100) return true;
        return RANDOM.nextInt(100) < frequencyPercent;
    }

    private static void tryJump(Minecraft client) {
        if (client.player != null && client.player.onGround) {
            client.player.jump();
        }
    }
}
