package com.example.hitjump;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Random;

public class HitJumpMod implements ClientModInitializer {
    private static final Random RANDOM = new Random();
    private static final long DOUBLE_TAP_WINDOW_MS = 380L;
    private static Minecraft cachedClient = null;

    // Menu trigger state
    private static boolean wasMenuKeyDown = false;
    private static long lastMenuKeyTapMs = 0L;

    // HitJump state
    private static int lastHurtTime = 0;
    private static int pendingJumpTicks = -1;
    private static int recentCombatHurtWindow = 0;
    private static boolean wasOnGround = true;

    // Toggle Sprint state
    private static boolean wasSprintToggleKeyDown = false;
    private static boolean toggleSprintForcingKey = false;

    // GodPlace state
    private static boolean godPlaceForcingUseKey = false;

    // 1.7 Blockhit animation cooldown
    private static int blockhitSwingCooldown = 0;

    // 1.7 Smooth Sneak state
    private static float smoothEyeHeight = -1.0F;
    private static long lastCameraTimeNanos = 0L;

    // Cached reflective fields (package-private where shared with WorldVisualsRenderer)
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
    private static Object sprintKeyBindingObj = null;
    private static Field keyBindingKeyCodeField = null;
    private static Field keyBindingPressedField = null;
    private static Field keyBindingPressTimeField = null;

    static boolean entityMotionSearched = false;
    static Field entityPrevXField = null;
    static Field entityPrevYField = null;
    static Field entityPrevZField = null;
    static Field entityXField = null;
    static Field entityYField = null;
    static Field entityZField = null;
    static Field entityVelXField = null;
    static Field entityVelYField = null;
    static Field entityVelZField = null;
    static Field entityYawField = null;
    static Field entityPitchField = null;
    static Field entityPrevYawField = null;
    static Field entityPrevPitchField = null;
    static Field entityBoxField = null;
    static Field entityCollidedHorizField = null;
    static Field entityInWebField = null;
    static Field entityDeadField = null;
    static Field entityLastTickXField = null;
    static Field entityLastTickYField = null;
    static Field entityLastTickZField = null;

    private static boolean headYawSearched = false;
    private static Field headYawField = null;
    private static Field prevHeadYawField = null;

    private static boolean swingFieldsSearched = false;
    private static Field swingInProgressField = null;
    private static Field swingProgressIntField = null;

    private static boolean inputFieldsSearched = false;
    private static Field playerInputField = null;
    private static Field inputStrafeField = null;
    private static Field inputForwardField = null;
    private static Field inputSneakField = null;

    private static boolean itemRendererSearched = false;
    private static Object itemInHandRendererObj = null;
    private static Field equippedProgressField = null;
    private static Field prevEquippedProgressField = null;

    private static boolean guiContainerSearched = false;
    private static Class<?> guiContainerClass = null;
    private static Field guiXSizeField = null;
    private static Field guiLeftField = null;

    private static boolean perspectiveSearched = false;
    private static Field optionsObjectField = null;
    private static Field thirdPersonViewField = null;

    @Override
    public void onInitializeClient() {
        HitJumpConfig.load();
    }

    public static Minecraft getCachedClient() {
        return cachedClient;
    }

    public static int getTextWidth(String text) {
        return WorldVisualsRenderer.getTextWidth(text);
    }

    public static void onClientTick(Minecraft client) {
        if (client == null) {
            return;
        }
        cachedClient = client;

        HitJumpConfig config = HitJumpConfig.getInstance();

        // Sound Mixer runs every tick even in menus
        SoundMixer.processActiveSounds(client);

        // Center Inventory (No Potion Shift)
        if (config.centerInventoryEnabled && client.screen != null) {
            centerContainerScreen(client.screen);
        }

        // Anti-Accidental Menu Trigger (Double-Tap, Modifier + Key, or Single Press)
        handleMenuTrigger(client, config);

        if (client.player == null || client.world == null) {
            releaseForcedUseKey(true);
            releaseForcedSprintKey(true);
            lastHurtTime = 0;
            pendingJumpTicks = -1;
            recentCombatHurtWindow = 0;
            wasOnGround = true;
            smoothEyeHeight = -1.0F;
            return;
        }

        // Toggle Sprint + Dedicated Hotkey
        handleToggleSprint(client, config);

        // 1.7 Head Position fix (MC-67665)
        if (config.headFixEnabled) {
            syncHeadYawInternal(client);
        }

        // No Item-Swap Dip & 1.7 Blockhit Animation
        applyHeldItemAnimations(client, config);

        // NoSlow (Cobwebs & Item Use pre-movement)
        if (config.noSlowEnabled) {
            applyNoSlowHead(client, config);
        }

        // GodPlace: ONLY active while dedicated keybind (default: Middle Mouse) is held
        boolean godPlaceActive = config.godPlaceEnabled
                && client.screen == null
                && isBindDown(config.godPlaceKeyCode);

        if (godPlaceActive) {
            ensureKeyBindingFields(client);
            setKeyBindingPressed(useKeyBindingObj, true);
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
                recentCombatHurtWindow = 2;
            } else {
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
        if (client == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();

        SoundMixer.processActiveSounds(client);

        if (config.centerInventoryEnabled && client.screen != null) {
            centerContainerScreen(client.screen);
        }

        if (client.player == null) {
            return;
        }

        if (config.godPlaceEnabled && client.screen == null && isBindDown(config.godPlaceKeyCode)) {
            applyGodPlace(client, config.godPlaceDelayTicks);
            clearPickKeyPressTimeIfShared(client, config.godPlaceKeyCode);
        }
        if (config.noSlowEnabled) {
            applyNoSlowTail(client, config);
        }
        if (config.noSwapDipEnabled) {
            lockEquippedItemProgress(client);
        }
        if (config.headFixEnabled) {
            syncHeadYawInternal(client);
        }
    }

    private static void handleMenuTrigger(Minecraft client, HitJumpConfig config) {
        boolean isMenuKeyDown = isKeyDownSafe(config.menuKeyCode);
        if (isMenuKeyDown && !wasMenuKeyDown && client.screen == null && client.player != null) {
            if (config.menuTriggerMode == 0) {
                // Double-Tap mode
                long now = System.currentTimeMillis();
                if (lastMenuKeyTapMs > 0L && (now - lastMenuKeyTapMs) <= DOUBLE_TAP_WINDOW_MS) {
                    lastMenuKeyTapMs = 0L;
                    client.openScreen(new HitJumpConfigScreen(null));
                } else {
                    lastMenuKeyTapMs = now;
                }
            } else if (config.menuTriggerMode == 1) {
                // Modifier + Key mode
                if (isModifierDown(config.menuModifierKey)) {
                    client.openScreen(new HitJumpConfigScreen(null));
                }
            } else {
                // Single Press mode
                client.openScreen(new HitJumpConfigScreen(null));
            }
        }
        if (client.screen != null) {
            lastMenuKeyTapMs = 0L;
        }
        wasMenuKeyDown = isMenuKeyDown;
    }

    private static boolean isModifierDown(int modifierKey) {
        if (isKeyDownSafe(modifierKey)) {
            return true;
        }
        if (modifierKey == Keyboard.KEY_LCONTROL) return isKeyDownSafe(Keyboard.KEY_RCONTROL);
        if (modifierKey == Keyboard.KEY_RCONTROL) return isKeyDownSafe(Keyboard.KEY_LCONTROL);
        if (modifierKey == Keyboard.KEY_LSHIFT) return isKeyDownSafe(Keyboard.KEY_RSHIFT);
        if (modifierKey == Keyboard.KEY_RSHIFT) return isKeyDownSafe(Keyboard.KEY_LSHIFT);
        if (modifierKey == Keyboard.KEY_LMENU) return isKeyDownSafe(Keyboard.KEY_RMENU);
        if (modifierKey == Keyboard.KEY_RMENU) return isKeyDownSafe(Keyboard.KEY_LMENU);
        return false;
    }

    private static void handleToggleSprint(Minecraft client, HitJumpConfig config) {
        if (!config.toggleSprintEnabled) {
            wasSprintToggleKeyDown = false;
            releaseForcedSprintKey(false);
            return;
        }

        boolean hotkeyDown = (client.screen == null) && isBindDown(config.toggleSprintKeyCode);
        if (hotkeyDown && !wasSprintToggleKeyDown) {
            config.toggleSprintActive = !config.toggleSprintActive;
            HitJumpConfig.save();
        }
        wasSprintToggleKeyDown = hotkeyDown;

        if (config.toggleSprintActive && client.screen == null) {
            ensureKeyBindingFields(client);
            setKeyBindingPressed(sprintKeyBindingObj, true);
            toggleSprintForcingKey = true;
        } else if (toggleSprintForcingKey) {
            releaseForcedSprintKey(client.screen != null);
        }
    }

    private static void applyNoSlowHead(Minecraft client, HitJumpConfig config) {
        Object player = client.player;
        ensureEntityMotionFields(player);

        // Cobwebs: clear Entity.isInWeb before moveEntity() reads it
        if (config.noSlowCobwebs && entityInWebField != null) {
            try {
                if (entityInWebField.getBoolean(player)) {
                    entityInWebField.setBoolean(player, false);
                }
            } catch (Exception ignored) {
            }
        }

        // Item Use (eating, drinking, bow draw, sword blocking): restore the 80% movement loss
        if (config.noSlowItems && client.screen == null && !isPlayerSneaking(player)) {
            float forward = getInputForward(player);
            float strafe = getInputStrafe(player);
            boolean slowedByItem = (Math.abs(forward) > 0.01F && Math.abs(forward) <= 0.25F)
                    || (Math.abs(strafe) > 0.01F && Math.abs(strafe) <= 0.25F);

            if (slowedByItem && entityVelXField != null && entityVelZField != null && entityYawField != null) {
                try {
                    float normF = (Math.abs(forward) > 0.01F) ? Math.signum(forward) : 0.0F;
                    float normS = (Math.abs(strafe) > 0.01F) ? Math.signum(strafe) : 0.0F;
                    float len = (float) Math.sqrt(normF * normF + normS * normS);
                    if (len > 0.001F) {
                        normF /= len;
                        normS /= len;
                        float yawRad = (float) Math.toRadians(entityYawField.getFloat(player));
                        float sin = (float) Math.sin(yawRad);
                        float cos = (float) Math.cos(yawRad);

                        // Add missing ~80% of normal walk/sprint acceleration
                        double boost = client.player.onGround ? 0.072D : 0.020D;
                        double addX = (normS * cos - normF * sin) * boost;
                        double addZ = (normF * cos + normS * sin) * boost;

                        double vx = entityVelXField.getDouble(player) + addX;
                        double vz = entityVelZField.getDouble(player) + addZ;
                        double maxSpeed = client.player.onGround ? 0.28D : 0.34D;
                        double horiz = Math.sqrt(vx * vx + vz * vz);
                        if (horiz > maxSpeed) {
                            double s = maxSpeed / horiz;
                            vx *= s;
                            vz *= s;
                        }
                        entityVelXField.setDouble(player, vx);
                        entityVelZField.setDouble(player, vz);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void applyNoSlowTail(Minecraft client, HitJumpConfig config) {
        Object player = client.player;
        ensureEntityMotionFields(player);

        if (config.noSlowCobwebs && entityInWebField != null) {
            try {
                if (entityInWebField.getBoolean(player)) {
                    entityInWebField.setBoolean(player, false);
                }
            } catch (Exception ignored) {
            }
        }

        // Soul Sand: vanilla sets Y-height to .875 and multiplies motionX/Z by 0.4 during block collisions
        if (config.noSlowSoulSand
                && client.player.onGround
                && !isPlayerSneaking(player)
                && entityYField != null && entityVelXField != null && entityVelZField != null) {
            try {
                boolean hitWall = (entityCollidedHorizField != null) && entityCollidedHorizField.getBoolean(player);
                if (!hitWall) {
                    double y = entityYField.getDouble(player);
                    double fracY = y - Math.floor(y);
                    if (Math.abs(fracY - 0.875D) < 0.003D) {
                        double vx = entityVelXField.getDouble(player);
                        double vz = entityVelZField.getDouble(player);
                        double speed = Math.sqrt(vx * vx + vz * vz);
                        if (speed > 0.015D && speed < 0.14D) {
                            double factor = Math.min(2.5D, 0.23D / speed);
                            entityVelXField.setDouble(player, vx * factor);
                            entityVelZField.setDouble(player, vz * factor);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void applyHeldItemAnimations(Minecraft client, HitJumpConfig config) {
        if (config.noSwapDipEnabled) {
            lockEquippedItemProgress(client);
        }

        // 1.7 Blockhit Animation: allow visual sword swing while blocking (holding Right + Left Click)
        if (blockhitSwingCooldown > 0) {
            blockhitSwingCooldown--;
        }
        if (config.blockhitAnimEnabled && client.screen == null) {
            boolean leftDown = false;
            boolean rightDown = false;
            try {
                leftDown = Mouse.isButtonDown(0);
                rightDown = Mouse.isButtonDown(1);
            } catch (Exception ignored) {
            }
            if (leftDown && rightDown && blockhitSwingCooldown == 0) {
                triggerVisualArmSwing(client.player);
                blockhitSwingCooldown = 6;
            }
        }
    }

    private static void triggerVisualArmSwing(Object player) {
        ensureSwingFields(player);
        if (swingInProgressField != null && swingProgressIntField != null) {
            try {
                boolean active = swingInProgressField.getBoolean(player);
                int prog = swingProgressIntField.getInt(player);
                if (!active || prog >= 3 || prog < 0) {
                    swingProgressIntField.setInt(player, -1);
                    swingInProgressField.setBoolean(player, true);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void ensureSwingFields(Object player) {
        if (swingFieldsSearched || player == null) {
            return;
        }
        swingFieldsSearched = true;
        Class<?> livingClass = getLivingBaseClass(player);
        if (livingClass == null) {
            return;
        }
        Field[] fields = livingClass.getDeclaredFields();
        // Matches vanilla 1.8.9 LivingEntity layout around swing:
        // boolean isSwingInProgress, int swingProgressInt, int arrowHitTimer, int hurtTime, int maxHurtTime, float attackedAtYaw
        for (int i = 0; i <= fields.length - 6; i++) {
            if (isInstanceField(fields[i], boolean.class)
                    && isInstanceField(fields[i + 1], int.class)
                    && isInstanceField(fields[i + 2], int.class)
                    && isInstanceField(fields[i + 3], int.class)
                    && isInstanceField(fields[i + 4], int.class)
                    && isInstanceField(fields[i + 5], float.class)) {
                swingInProgressField = fields[i];
                swingProgressIntField = fields[i + 1];
                swingInProgressField.setAccessible(true);
                swingProgressIntField.setAccessible(true);
                return;
            }
        }
    }

    private static void lockEquippedItemProgress(Minecraft client) {
        ensureItemInHandRenderer(client);
        if (itemInHandRendererObj != null && equippedProgressField != null && prevEquippedProgressField != null) {
            try {
                equippedProgressField.setFloat(itemInHandRendererObj, 1.0F);
                prevEquippedProgressField.setFloat(itemInHandRendererObj, 1.0F);
            } catch (Exception ignored) {
            }
        }
    }

    private static void ensureItemInHandRenderer(Minecraft client) {
        if (itemRendererSearched || client == null) {
            return;
        }
        itemRendererSearched = true;
        for (Field mcField : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(mcField.getModifiers()) || mcField.getType().isPrimitive()) continue;
            Object obj;
            try {
                mcField.setAccessible(true);
                obj = mcField.get(client);
            } catch (Exception ignored) {
                continue;
            }
            if (obj == null || obj.getClass().isArray()) continue;
            if (tryBindItemInHandRenderer(obj)) {
                return;
            }
            // Check inside GameRenderer as well
            for (Field sub : obj.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(sub.getModifiers()) || sub.getType().isPrimitive()) continue;
                Object subObj;
                try {
                    sub.setAccessible(true);
                    subObj = sub.get(obj);
                } catch (Exception ignored) {
                    continue;
                }
                if (subObj == null || subObj.getClass().isArray()) continue;
                if (tryBindItemInHandRenderer(subObj)) {
                    return;
                }
            }
        }
    }

    private static boolean tryBindItemInHandRenderer(Object candidate) {
        Field[] fields = candidate.getClass().getDeclaredFields();
        if (fields.length < 6 || fields.length > 14) {
            return false;
        }
        // Matches vanilla 1.8.9 ItemRenderer (ItemInHandRenderer):
        // Minecraft, ItemStack, float equippedProgress, float prevEquippedProgress, RenderManager, RenderItem, int equippedItemSlot
        for (int i = 0; i <= fields.length - 7; i++) {
            if (isInstanceField(fields[i], Minecraft.class)
                    && !Modifier.isStatic(fields[i + 1].getModifiers()) && !fields[i + 1].getType().isPrimitive()
                    && isInstanceField(fields[i + 2], float.class)
                    && isInstanceField(fields[i + 3], float.class)
                    && !Modifier.isStatic(fields[i + 4].getModifiers()) && !fields[i + 4].getType().isPrimitive()
                    && !Modifier.isStatic(fields[i + 5].getModifiers()) && !fields[i + 5].getType().isPrimitive()
                    && isInstanceField(fields[i + 6], int.class)) {
                itemInHandRendererObj = candidate;
                equippedProgressField = fields[i + 2];
                prevEquippedProgressField = fields[i + 3];
                equippedProgressField.setAccessible(true);
                prevEquippedProgressField.setAccessible(true);
                return true;
            }
        }
        return false;
    }

    private static void centerContainerScreen(Screen screen) {
        if (screen == null) {
            return;
        }
        if (!guiContainerSearched) {
            Class<?> cls = screen.getClass();
            while (cls != null && cls != Screen.class && cls != Object.class) {
                Field[] fields = cls.getDeclaredFields();
                // Matches vanilla 1.8.9 GuiContainer:
                // int xSize, int ySize, Container inventorySlots, int guiLeft, int guiTop
                for (int i = 0; i <= fields.length - 5; i++) {
                    if (isInstanceField(fields[i], int.class)
                            && isInstanceField(fields[i + 1], int.class)
                            && !Modifier.isStatic(fields[i + 2].getModifiers()) && !fields[i + 2].getType().isPrimitive()
                            && isInstanceField(fields[i + 3], int.class)
                            && isInstanceField(fields[i + 4], int.class)) {
                        try {
                            fields[i].setAccessible(true);
                            int xSizeVal = fields[i].getInt(screen);
                            if (xSizeVal >= 120 && xSizeVal <= 300) {
                                fields[i + 3].setAccessible(true);
                                guiContainerClass = cls;
                                guiXSizeField = fields[i];
                                guiLeftField = fields[i + 3];
                                guiContainerSearched = true;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (guiContainerSearched) break;
                cls = cls.getSuperclass();
            }
        }

        if (guiContainerClass != null && guiContainerClass.isInstance(screen) && guiXSizeField != null && guiLeftField != null) {
            try {
                int xSize = guiXSizeField.getInt(screen);
                int centeredLeft = (screen.width - xSize) / 2;
                int shiftedLeft = 160 + (screen.width - xSize - 200) / 2;
                int currentLeft = guiLeftField.getInt(screen);
                if (currentLeft == shiftedLeft && currentLeft != centeredLeft) {
                    guiLeftField.setInt(screen, centeredLeft);
                }
            } catch (Exception ignored) {
            }
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
        int pickCode = getKeyBindingCode(pickKeyBindingObj, -98);
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
        if (config.noSwapDipEnabled) {
            lockEquippedItemProgress(cachedClient);
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

        WorldVisualsRenderer.captureCameraMatrices();
    }

    public static void onRenderWorldTail(float tickDelta) {
        if (cachedClient != null && cachedClient.screen != null && HitJumpConfig.getInstance().centerInventoryEnabled) {
            centerContainerScreen(cachedClient.screen);
        }
        WorldVisualsRenderer.onRenderWorldTail(cachedClient, tickDelta);
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
        boolean physicallyHeld = !forceOff && isBindDown(getKeyBindingCode(useKeyBindingObj, -99));
        setKeyBindingPressed(useKeyBindingObj, physicallyHeld);
        godPlaceForcingUseKey = false;
    }

    private static void releaseForcedSprintKey(boolean forceOff) {
        if (!toggleSprintForcingKey) {
            return;
        }
        boolean physicallyHeld = !forceOff && isBindDown(getKeyBindingCode(sprintKeyBindingObj, Keyboard.KEY_LCONTROL));
        setKeyBindingPressed(sprintKeyBindingObj, physicallyHeld);
        toggleSprintForcingKey = false;
    }

    private static void setKeyBindingPressed(Object binding, boolean pressed) {
        if (binding != null && keyBindingPressedField != null) {
            try {
                keyBindingPressedField.setBoolean(binding, pressed);
            } catch (Exception ignored) {
            }
        }
    }

    private static int getKeyBindingCode(Object binding, int fallback) {
        if (binding != null && keyBindingKeyCodeField != null) {
            try {
                return keyBindingKeyCodeField.getInt(binding);
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static void clearPickKeyPressTimeIfShared(Minecraft client, int godPlaceKeyCode) {
        ensureKeyBindingFields(client);
        if (pickKeyBindingObj != null
                && keyBindingPressTimeField != null
                && getKeyBindingCode(pickKeyBindingObj, -98) == godPlaceKeyCode) {
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
                } else if ("key.sprint".equals(desc)) {
                    sprintKeyBindingObj = val;
                    locateKeyBindingInternalFields(val.getClass());
                }
            }
            if (useKeyBindingObj != null || sprintKeyBindingObj != null) {
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
                    if ("key.use".equals(s) || "key.pickItem".equals(s) || "key.sprint".equals(s)) {
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

    static Class<?> getPlayerBaseClass(Object player) {
        Class<?> c = player.getClass().getSuperclass();
        return (c != null) ? c.getSuperclass() : null;
    }

    static Class<?> getLivingBaseClass(Object player) {
        Class<?> c = getPlayerBaseClass(player);
        return (c != null) ? c.getSuperclass() : null;
    }

    static Class<?> getEntityBaseClass(Object player) {
        Class<?> c = getLivingBaseClass(player);
        return (c != null) ? c.getSuperclass() : null;
    }

    static boolean isEntityDead(Object entity) {
        if (entityDeadField != null) {
            try {
                return entityDeadField.getBoolean(entity);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    static void ensureEntityMotionFields(Object entity) {
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
                    entityVelXField = fields[i + 6];
                    entityVelYField = fields[i + 7];
                    entityVelZField = fields[i + 8];
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
                    entityVelXField.setAccessible(true);
                    entityVelYField.setAccessible(true);
                    entityVelZField.setAccessible(true);
                    entityYawField.setAccessible(true);
                    entityPitchField.setAccessible(true);
                    entityPrevYawField.setAccessible(true);
                    entityPrevPitchField.setAccessible(true);

                    if (!Modifier.isStatic(fields[i + 13].getModifiers()) && !fields[i + 13].getType().isPrimitive()) {
                        entityBoxField = fields[i + 13];
                        entityBoxField.setAccessible(true);
                    }
                    if (i + 15 < fields.length && isInstanceField(fields[i + 15], boolean.class)) {
                        entityCollidedHorizField = fields[i + 15];
                        entityCollidedHorizField.setAccessible(true);
                    }
                    if (i + 19 < fields.length && isInstanceField(fields[i + 19], boolean.class)) {
                        entityInWebField = fields[i + 19];
                        entityInWebField.setAccessible(true);
                    }
                    if (i + 21 < fields.length && isInstanceField(fields[i + 21], boolean.class)) {
                        entityDeadField = fields[i + 21];
                        entityDeadField.setAccessible(true);
                    }
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

    private static void ensureInputFields(Object player) {
        if (inputFieldsSearched || player == null) {
            return;
        }
        inputFieldsSearched = true;
        Class<?> cls = player.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(player);
                    if (val == null) continue;
                    if (bindInputClassFields(val.getClass())) {
                        playerInputField = f;
                        return;
                    }
                } catch (Exception ignored) {
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static boolean bindInputClassFields(Class<?> startClass) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class) {
            Field[] fields = cls.getDeclaredFields();
            if (fields.length >= 4 && fields.length <= 8) {
                for (int i = 0; i <= fields.length - 4; i++) {
                    if (isInstanceField(fields[i], float.class)
                            && isInstanceField(fields[i + 1], float.class)
                            && isInstanceField(fields[i + 2], boolean.class)
                            && isInstanceField(fields[i + 3], boolean.class)) {
                        inputStrafeField = fields[i];
                        inputForwardField = fields[i + 1];
                        inputSneakField = fields[i + 3];
                        inputStrafeField.setAccessible(true);
                        inputForwardField.setAccessible(true);
                        inputSneakField.setAccessible(true);
                        return true;
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static boolean isPlayerSneaking(Object player) {
        ensureInputFields(player);
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

    private static float getInputForward(Object player) {
        ensureInputFields(player);
        if (playerInputField != null && inputForwardField != null) {
            try {
                Object input = playerInputField.get(player);
                if (input != null) {
                    return inputForwardField.getFloat(input);
                }
            } catch (Exception ignored) {
            }
        }
        return 0.0F;
    }

    private static float getInputStrafe(Object player) {
        ensureInputFields(player);
        if (playerInputField != null && inputStrafeField != null) {
            try {
                Object input = playerInputField.get(player);
                if (input != null) {
                    return inputStrafeField.getFloat(input);
                }
            } catch (Exception ignored) {
            }
        }
        return 0.0F;
    }

    static int getPerspective(Minecraft client) {
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
        if (!jumpTicks
