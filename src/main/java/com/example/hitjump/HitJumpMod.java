package com.example.hitjump;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HitJumpMod implements ClientModInitializer {
    private static final Random RANDOM = new Random();
    private static final long DOUBLE_TAP_WINDOW_MS = 380L;
    private static Minecraft cachedClient = null;

    // Render pass guard so 3D visuals run before hand depth-clear
    private static boolean worldVisualsRenderedThisFrame = false;

    // Menu trigger state
    private static boolean wasMenuKeyDown = false;
    private static long lastMenuKeyTapMs = 0L;

    // Keep Moving After GUI & No Hotbar Scroll state
    private static Screen lastTickScreen = null;
    private static int hotbarSlotBeforeTick = -1;

    // Night Vision / Fullbright state
    private static float savedGamma = -1.0F;

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
    private static Object sprintKeyBindingObj = null;
    private static final List<Object> movementKeyBindings = new ArrayList<Object>();
    private static final List<Object> hotbarKeyBindings = new ArrayList<Object>();
    private static Field keyBindingKeyCodeField = null;
    private static Field keyBindingPressedField = null;
    private static Field keyBindingPressTimeField = null;

    private static boolean playerInventorySearched = false;
    private static Field playerInventoryField = null;
    private static Field selectedSlotField = null;

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
    private static Field gammaField = null;

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

        // Sound Mixer runs every tick
		SoundMixer.processActiveSounds(client);
        HudSuiteRenderer.pollInputs();
        BedwarsAdvisor.tick(client);

        // Night Vision / Fullbright (gamma override)
        applyNightVision(client, config);

        // Center Inventory (No Potion Shift)
        if (config.centerInventoryEnabled && client.screen != null) {
            centerContainerScreen(client.screen);
        }

        // Keep Moving After GUI Close: re-sync held movement keys the instant a screen closes
        if (config.keepMovingAfterGuiEnabled && lastTickScreen != null && client.screen == null && client.player != null) {
            resyncHeldMovementKeys(client);
        }
        lastTickScreen = client.screen;

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
            hotbarSlotBeforeTick = -1;
            return;
        }

        // Capture hotbar slot before Mouse.next() processes wheel events
        if (config.noScrollEnabled && client.screen == null) {
            hotbarSlotBeforeTick = getSelectedHotbarSlot(client.player);
        } else {
            hotbarSlotBeforeTick = -1;
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

        // NoHitDelay: clamp vanilla's 10-tick miss penalty
        if (config.noHitDelayEnabled) {
            applyNoHitDelay(client, config.hitDelayTicks);
        }

        // NoJumpDelay: clamp vanilla's 10-tick jump cooldown
        if (config.noJumpDelayEnabled) {
            applyNoJumpDelay(client.player, config.jumpDelayTicks);
        }

        // HitJump: trigger only on actual knockback hits
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

        // No Hotbar Scroll: revert wheel-driven slot changes while keeping hotbar keys 1-9 working
        if (config.noScrollEnabled && client.screen == null && hotbarSlotBeforeTick >= 0) {
            int wheel = 0;
            try {
                wheel = Mouse.getDWheel();
            } catch (Throwable ignored) {
            }
            if (wheel != 0 && !isAnyHotbarOrPickKeyDown(client)) {
                int currentSlot = getSelectedHotbarSlot(client.player);
                if (currentSlot != hotbarSlotBeforeTick) {
                    setSelectedHotbarSlot(client.player, hotbarSlotBeforeTick);
                }
            }
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

    private static void applyNightVision(Minecraft client, HitJumpConfig config) {
        ensureOptionsFields(client);
        if (optionsObjectField == null || gammaField == null) {
            return;
        }
        try {
            Object opts = optionsObjectField.get(client);
            if (opts == null) {
                return;
            }
            if (config.nightVisionEnabled) {
                float cur = gammaField.getFloat(opts);
                if (savedGamma < 0.0F) {
                    savedGamma = (cur > 1.05F) ? 1.0F : Math.max(0.0F, cur);
                }
                if (cur < 99.0F) {
                    gammaField.setFloat(opts, 100.0F);
                }
            } else if (savedGamma >= 0.0F) {
                gammaField.setFloat(opts, savedGamma);
                savedGamma = -1.0F;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void resyncHeldMovementKeys(Minecraft client) {
        ensureKeyBindingFields(client);
        for (int i = 0; i < movementKeyBindings.size(); i++) {
            Object kb = movementKeyBindings.get(i);
            int code = getKeyBindingCode(kb, 0);
            if (code != 0 && isBindDown(code)) {
                setKeyBindingPressed(kb, true);
            }
        }
    }

    private static boolean isAnyHotbarOrPickKeyDown(Minecraft client) {
        ensureKeyBindingFields(client);
        for (int i = 0; i < hotbarKeyBindings.size(); i++) {
            int code = getKeyBindingCode(hotbarKeyBindings.get(i), 0);
            if (code != 0 && isBindDown(code)) {
                return true;
            }
        }
        int pickCode = getKeyBindingCode(pickKeyBindingObj, -98);
        return pickCode != 0 && isBindDown(pickCode);
    }

    private static int getSelectedHotbarSlot(Object player) {
        ensurePlayerInventoryFields(player);
        if (playerInventoryField != null && selectedSlotField != null) {
            try {
                Object inv = playerInventoryField.get(player);
                if (inv != null) {
                    return selectedSlotField.getInt(inv);
                }
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private static void setSelectedHotbarSlot(Object player, int slot) {
        ensurePlayerInventoryFields(player);
        if (playerInventoryField != null && selectedSlotField != null && slot >= 0 && slot <= 8) {
            try {
                Object inv = playerInventoryField.get(player);
                if (inv != null) {
                    selectedSlotField.setInt(inv, slot);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void ensurePlayerInventoryFields(Object player) {
        if (playerInventorySearched || player == null) {
            return;
        }
        playerInventorySearched = true;
        Class<?> playerBase = getPlayerBaseClass(player);
        if (playerBase == null) {
            return;
        }
        try {
            for (Field f : playerBase.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || isSkippableClass(f.getType())) {
                    continue;
                }
                Field[] invFields;
                try {
                    invFields = f.getType().getDeclaredFields();
                } catch (Throwable ignored) {
                    continue;
                }
                for (int i = 0; i <= invFields.length - 4; i++) {
                    if (!Modifier.isStatic(invFields[i].getModifiers()) && invFields[i].getType().isArray()
                            && !Modifier.isStatic(invFields[i + 1].getModifiers()) && invFields[i + 1].getType().isArray()
                            && isInstanceField(invFields[i + 2], int.class)
                            && !Modifier.isStatic(invFields[i + 3].getModifiers()) && invFields[i + 3].getType() == playerBase) {
                        f.setAccessible(true);
                        invFields[i + 2].setAccessible(true);
                        playerInventoryField = f;
                        selectedSlotField = invFields[i + 2];
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void handleMenuTrigger(Minecraft client, HitJumpConfig config) {
        boolean isMenuKeyDown = isKeyDownSafe(config.menuKeyCode);
        if (isMenuKeyDown && !wasMenuKeyDown && client.screen == null && client.player != null) {
            if (config.menuTriggerMode == 0) {
                long now = System.currentTimeMillis();
                if (lastMenuKeyTapMs > 0L && (now - lastMenuKeyTapMs) <= DOUBLE_TAP_WINDOW_MS) {
                    lastMenuKeyTapMs = 0L;
                    client.openScreen(new HitJumpConfigScreen(null));
                } else {
                    lastMenuKeyTapMs = now;
                }
            } else if (config.menuTriggerMode == 1) {
                if (isModifierDown(config.menuModifierKey)) {
                    client.openScreen(new HitJumpConfigScreen(null));
                }
            } else {
                client.openScreen(new HitJumpConfigScreen(null));
            }
        }
        if (client.screen != null) {
            lastMenuKeyTapMs = 0L;
        }
        wasMenuKeyDown = isMenuKeyDown;
    }

    private static boolean isModifierDown(int modifierKey) {
        if (isKeyDownSafe(modifierKey)) return true;
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

        if (config.noSlowCobwebs && entityInWebField != null) {
            try {
                if (entityInWebField.getBoolean(player)) {
                    entityInWebField.setBoolean(player, false);
                }
            } catch (Throwable ignored) {
            }
        }

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
                } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
            }
        }

        // Soul Sand fix: handles 1 to 4 overlapping Soul Sand blocks crushing motionX/Z down to near-zero
        if (config.noSlowSoulSand
                && client.player.onGround
                && !isPlayerSneaking(player)
                && entityYField != null && entityVelXField != null && entityVelZField != null && entityYawField != null) {
            try {
                boolean hitWall = (entityCollidedHorizField != null) && entityCollidedHorizField.getBoolean(player);
                if (!hitWall) {
                    double y = entityYField.getDouble(player);
                    double fracY = y - Math.floor(y);
                    if (Math.abs(fracY - 0.875D) < 0.006D) {
                        float forward = getInputForward(player);
                        float strafe = getInputStrafe(player);
                        if (Math.abs(forward) > 0.05F || Math.abs(strafe) > 0.05F) {
                            double vx = entityVelXField.getDouble(player);
                            double vz = entityVelZField.getDouble(player);
                            double endSpeed = Math.sqrt(vx * vx + vz * vz);
                            // Normal ground post-friction speed is ~0.118 (walk) / ~0.153 (sprint);
                            // Soul Sand crushes endSpeed below 0.08 (down to 0.001 on 4-block corners)
                            if (endSpeed < 0.085D) {
                                float normF = (Math.abs(forward) > 0.05F) ? Math.signum(forward) : 0.0F;
                                float normS = (Math.abs(strafe) > 0.05F) ? Math.signum(strafe) : 0.0F;
                                float len = (float) Math.sqrt(normF * normF + normS * normS);
                                if (len > 0.001F) {
                                    normF /= len;
                                    normS /= len;
                                    float yawRad = (float) Math.toRadians(entityYawField.getFloat(player));
                                    float sin = (float) Math.sin(yawRad);
                                    float cos = (float) Math.cos(yawRad);

                                    double dirX = normS * cos - normF * sin;
                                    double dirZ = normF * cos + normS * sin;

                                    ensureKeyBindingFields(client);
                                    boolean sprintPressed = (config.toggleSprintEnabled && config.toggleSprintActive)
                                            || isBindDown(getKeyBindingCode(sprintKeyBindingObj, Keyboard.KEY_LCONTROL));
                                    boolean sprinting = sprintPressed && forward > 0.1F;
                                    double targetEndSpeed = sprinting ? 0.153D : 0.118D;

                                    entityVelXField.setDouble(player, dirX * targetEndSpeed);
                                    entityVelZField.setDouble(player, dirZ * targetEndSpeed);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void applyHeldItemAnimations(Minecraft client, HitJumpConfig config) {
        if (config.noSwapDipEnabled) {
            lockEquippedItemProgress(client);
        }

        if (blockhitSwingCooldown > 0) {
            blockhitSwingCooldown--;
        }
        if (config.blockhitAnimEnabled && client.screen == null) {
            boolean leftDown = false;
            boolean rightDown = false;
            try {
                leftDown = Mouse.isButtonDown(0);
                rightDown = Mouse.isButtonDown(1);
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
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
        for (int i = 0; i <= fields.length - 6; i++) {
            if (isInstanceField(fields[i], boolean.class)
                    && isInstanceField(fields[i + 1], int.class)
                    && isInstanceField(fields[i + 2], int.class)
                    && isInstanceField(fields[i + 3], int.class)
                    && isInstanceField(fields[i + 4], int.class)
                    && isInstanceField(fields[i + 5], float.class)) {
                try {
                    swingInProgressField = fields[i];
                    swingProgressIntField = fields[i + 1];
                    swingInProgressField.setAccessible(true);
                    swingProgressIntField.setAccessible(true);
                } catch (Throwable ignored) {
                }
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
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
                continue;
            }
            if (obj == null || obj.getClass().isArray() || isSkippableClass(obj.getClass())) continue;
            if (tryBindItemInHandRenderer(obj)) {
                return;
            }
            for (Field sub : obj.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(sub.getModifiers()) || sub.getType().isPrimitive()) continue;
                Object subObj;
                try {
                    sub.setAccessible(true);
                    subObj = sub.get(obj);
                } catch (Throwable ignored) {
                    continue;
                }
                if (subObj == null || subObj.getClass().isArray() || isSkippableClass(subObj.getClass())) continue;
                if (tryBindItemInHandRenderer(subObj)) {
                    return;
                }
            }
        }
    }

    private static boolean tryBindItemInHandRenderer(Object candidate) {
        Field[] fields;
        try {
            fields = candidate.getClass().getDeclaredFields();
        } catch (Throwable ignored) {
            return false;
        }
        if (fields.length < 6 || fields.length > 14) {
            return false;
        }
        for (int i = 0; i <= fields.length - 7; i++) {
            if (isInstanceField(fields[i], Minecraft.class)
                    && !Modifier.isStatic(fields[i + 1].getModifiers()) && !fields[i + 1].getType().isPrimitive()
                    && isInstanceField(fields[i + 2], float.class)
                    && isInstanceField(fields[i + 3], float.class)
                    && !Modifier.isStatic(fields[i + 4].getModifiers()) && !fields[i + 4].getType().isPrimitive()
                    && !Modifier.isStatic(fields[i + 5].getModifiers()) && !fields[i + 5].getType().isPrimitive()
                    && isInstanceField(fields[i + 6], int.class)) {
                try {
                    itemInHandRendererObj = candidate;
                    equippedProgressField = fields[i + 2];
                    prevEquippedProgressField = fields[i + 3];
                    equippedProgressField.setAccessible(true);
                    prevEquippedProgressField.setAccessible(true);
                    return true;
                } catch (Throwable ignored) {
                }
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
            while (cls != null && cls != Screen.class && cls != Object.class && !isSkippableClass(cls)) {
                Field[] fields;
                try {
                    fields = cls.getDeclaredFields();
                } catch (Throwable ignored) {
                    break;
                }
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
                        } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
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
        if (config.parallaxFixEnabled && getPerspective(cachedClient) == 0) {
            GL11.glTranslatef(0.0F, 0.0F, 0.1F);
        }
    }

    public static void onTransformCameraTail(float tickDelta) {
        worldVisualsRenderedThisFrame = false;
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

    public static void onRenderWorldBeforeHand(float tickDelta) {
        if (worldVisualsRenderedThisFrame) {
            return;
        }
        worldVisualsRenderedThisFrame = true;
        WorldVisualsRenderer.onRenderWorldTail(cachedClient, tickDelta);
    }

    public static void onRenderWorldTail(float tickDelta) {
        if (cachedClient != null && cachedClient.screen != null && HitJumpConfig.getInstance().centerInventoryEnabled) {
            centerContainerScreen(cachedClient.screen);
        }
        if (!worldVisualsRenderedThisFrame) {
            worldVisualsRenderedThisFrame = true;
            WorldVisualsRenderer.onRenderWorldTail(cachedClient, tickDelta);
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
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
            }
        }
    }

    private static int getKeyBindingCode(Object binding, int fallback) {
        if (binding != null && keyBindingKeyCodeField != null) {
            try {
                return keyBindingKeyCodeField.getInt(binding);
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
                continue;
            }
            if (maybeOptions == null || maybeOptions.getClass().isArray() || isSkippableClass(maybeOptions.getClass())) continue;

            Field[] optFields;
            try {
                optFields = maybeOptions.getClass().getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field optField : optFields) {
                if (Modifier.isStatic(optField.getModifiers()) || optField.getType().isPrimitive()) continue;
                Object val;
                try {
                    optField.setAccessible(true);
                    val = optField.get(maybeOptions);
                } catch (Throwable ignored) {
                    continue;
                }
                if (val == null) continue;
                if (val instanceof Object[]) {
                    Object[] arr = (Object[]) val;
                    for (int k = 0; k < arr.length; k++) {
                        if (arr[k] != null && !isSkippableClass(arr[k].getClass())) {
                            registerKeyBindingCandidate(arr[k]);
                        }
                    }
                } else if (!val.getClass().isArray() && !isSkippableClass(val.getClass())) {
                    registerKeyBindingCandidate(val);
                }
            }
            if (useKeyBindingObj != null || sprintKeyBindingObj != null || !movementKeyBindings.isEmpty()) {
                return;
            }
        }
    }

    private static void registerKeyBindingCandidate(Object val) {
        String desc = getKeyBindingDescription(val);
        if (desc == null) {
            return;
        }
        locateKeyBindingInternalFields(val.getClass());
        if ("key.use".equals(desc)) {
            useKeyBindingObj = val;
        } else if ("key.pickItem".equals(desc)) {
            pickKeyBindingObj = val;
        } else if ("key.sprint".equals(desc)) {
            sprintKeyBindingObj = val;
            if (!movementKeyBindings.contains(val)) movementKeyBindings.add(val);
        } else if ("key.forward".equals(desc)
                || "key.back".equals(desc)
                || "key.left".equals(desc)
                || "key.right".equals(desc)
                || "key.jump".equals(desc)
                || "key.sneak".equals(desc)) {
            if (!movementKeyBindings.contains(val)) movementKeyBindings.add(val);
        } else if (desc.startsWith("key.hotbar.")) {
            if (!hotbarKeyBindings.contains(val)) hotbarKeyBindings.add(val);
        }
    }

    private static String getKeyBindingDescription(Object obj) {
        Field[] fields;
        try {
            fields = obj.getClass().getDeclaredFields();
        } catch (Throwable ignored) {
            return null;
        }
        if (fields.length > 20) {
            return null;
        }
        for (Field f : fields) {
            if (isInstanceField(f, String.class)) {
                try {
                    f.setAccessible(true);
                    Object s = f.get(obj);
                    if (s instanceof String) {
                        String str = (String) s;
                        if (str.startsWith("key.")) {
                            return str;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static void locateKeyBindingInternalFields(Class<?> keyBindingClass) {
        if (keyBindingPressedField != null && keyBindingKeyCodeField != null) {
            return;
        }
        Field[] fields;
        try {
            fields = keyBindingClass.getDeclaredFields();
        } catch (Throwable ignored) {
            return;
        }
        for (int i = 0; i < fields.length - 1; i++) {
            if (isInstanceField(fields[i], int.class)
                    && !Modifier.isFinal(fields[i].getModifiers())
                    && isInstanceField(fields[i + 1], boolean.class)) {
                try {
                    keyBindingKeyCodeField = fields[i];
                    keyBindingPressedField = fields[i + 1];
                    keyBindingKeyCodeField.setAccessible(true);
                    keyBindingPressedField.setAccessible(true);
                    if (i + 2 < fields.length && isInstanceField(fields[i + 2], int.class)) {
                        keyBindingPressTimeField = fields[i + 2];
                        keyBindingPressTimeField.setAccessible(true);
                    }
                } catch (Throwable ignored) {
                }
                return;
            }
        }
        for (Field f : fields) {
            try {
                if (keyBindingPressedField == null && isInstanceField(f, boolean.class)) {
                    keyBindingPressedField = f;
                    keyBindingPressedField.setAccessible(true);
                } else if (keyBindingKeyCodeField == null
                        && isInstanceField(f, int.class)
                        && !Modifier.isFinal(f.getModifiers())) {
                    keyBindingKeyCodeField = f;
                    keyBindingKeyCodeField.setAccessible(true);
                }
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
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
            while (worldCls != null && worldCls != Object.class && !isSkippableClass(worldCls)) {
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
        } catch (Throwable ignored) {
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
        } catch (Throwable ignored) {
        }
    }

    private static double getVelocityY(Object player) {
        ensureEntityMotionFields(player);
        if (entityVelYField != null) {
            try {
                return entityVelYField.getDouble(player);
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
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
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
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
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
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
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
            for (Field f : cls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(player);
                    if (val == null || isSkippableClass(val.getClass())) continue;
                    if (bindInputClassFields(val.getClass())) {
                        playerInputField = f;
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static boolean bindInputClassFields(Class<?> startClass) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
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
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
            }
        }
        return 0.0F;
    }

    static int getPerspective(Minecraft client) {
        ensureOptionsFields(client);
        if (optionsObjectField != null && thirdPersonViewField != null) {
            try {
                Object opts = optionsObjectField.get(client);
                if (opts != null) {
                    return thirdPersonViewField.getInt(opts);
                }
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static void ensureOptionsFields(Minecraft client) {
        if (perspectiveSearched || client == null) {
            return;
        }
        perspectiveSearched = true;
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
                        thirdPersonViewField = fields[i + 1];
                        gammaField = fields[i + 9];
                        thirdPersonViewField.setAccessible(true);
                        gammaField.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    return;
                }
            }
        }
    }

    private static boolean isKeyDownSafe(int keyCode) {
        if (keyCode <= 0 || keyCode >= 256) {
            return false;
        }
        try {
            return Keyboard.isKeyDown(keyCode);
        } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
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
            } catch (Throwable ignored) {
            }
        }
    }

    private static Field findJumpTicksField(Class<?> startClass) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
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

    static int getHurtTime(Object entity) {
        if (!hurtTimeSearched) {
            hurtTimeField = findHurtTimeField(entity.getClass());
            hurtTimeSearched = true;
        }
        if (hurtTimeField != null) {
            try {
                return hurtTimeField.getInt(entity);
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static Field findHurtTimeField(Class<?> startClass) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class && !isSkippableClass(cls)) {
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
