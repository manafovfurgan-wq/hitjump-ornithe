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
    private static Minecraft cachedClient = null;

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
    private static Field entityXField = null;
    private static Field entityYField = null;
    private static Field entityZField = null;
    private static Field entityVelYField = null;
    private static Field entityYawField = null;
    private static Field entityPrevYawField = null;

    private static boolean headYawSearched = false;
    private static Field headYawField = null;
    private static Field prevHeadYawField = null;

    private static boolean inputSneakSearched = false;
    private static Field playerInputField = null;
    private static Field inputSneakField = null;

    private static boolean perspectiveSearched = false;
    private static Field optionsObjectField = null;
    private static Field thirdPersonViewField = null;

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
            return;
        }

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
        // Matches vanilla 1.8.9 KeyBinding layout: int keyCode (non-final), boolean pressed, int pressTime
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
        // Fallback if layout differs
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
                // 0t = every tick (20/sec), 1t = skip 1 tick (10/sec), 2t = skip 2 ticks, 3t = vanilla (4-tick gap)
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
            for (int i = 0; i <= fields.length - 13; i++) {
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
                    entityXField = fields[i + 3];
                    entityYField = fields[i + 4];
                    entityZField = fields[i + 5];
                    entityVelYField = fields[i + 7];
                    entityYawField = fields[i + 9];
                    entityPrevYawField = fields[i + 11];
                    entityXField.setAccessible(true);
                    entityYField.setAccessible(true);
                    entityZField.setAccessible(true);
                    entityVelYField.setAccessible(true);
                    entityYawField.setAccessible(true);
                    entityPrevYawField.setAccessible(true);
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
        Class<?> playerBaseClass = player.getClass().getSuperclass();
        if (playerBaseClass != null) {
            playerBaseClass = playerBaseClass.getSuperclass();
        }
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
