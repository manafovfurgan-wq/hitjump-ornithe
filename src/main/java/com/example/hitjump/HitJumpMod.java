package com.example.hitjump;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Random;

public class HitJumpMod implements ClientModInitializer {
    private static final Random RANDOM = new Random();
    private static int lastHurtTime = 0;
    private static int pendingJumpTicks = -1;
    private static boolean wasMenuKeyDown = false;

    private static Field hurtTimeField = null;
    private static boolean hurtTimeSearched = false;

    private static Field jumpTicksField = null;
    private static boolean jumpTicksSearched = false;

    private static Field attackCooldownField = null;
    private static boolean attackCooldownSearched = false;

    @Override
    public void onInitializeClient() {
        HitJumpConfig.load();
    }

    public static void onClientTick(Minecraft client) {
        if (client == null) {
            return;
        }

        HitJumpConfig config = HitJumpConfig.getInstance();

        // Open settings menu using the configured keybind
        boolean isMenuKeyDown = isKeyDownSafe(config.menuKeyCode);
        if (isMenuKeyDown && !wasMenuKeyDown && client.screen == null && client.player != null) {
            client.openScreen(new HitJumpConfigScreen(null));
        }
        wasMenuKeyDown = isMenuKeyDown;

        if (client.player == null || client.world == null) {
            lastHurtTime = 0;
            pendingJumpTicks = -1;
            return;
        }

        // NoHitDelay: clamp vanilla's 10-tick miss penalty (never touching GUI-close 10000 lock)
        if (config.noHitDelayEnabled) {
            applyNoHitDelay(client, config.hitDelayTicks);
        }

        // NoJumpDelay: clamp vanilla's 10-tick jump cooldown before movement ticks
        if (config.noJumpDelayEnabled) {
            applyNoJumpDelay(client.player, config.jumpDelayTicks);
        }

        // HitJump: jump on damage based on frequency and latency
        if (!config.enabled) {
            lastHurtTime = 0;
            pendingJumpTicks = -1;
            return;
        }

        int currentHurtTime = getHurtTime(client.player);

        if (currentHurtTime > lastHurtTime && currentHurtTime > 0) {
            if (rollFrequency(config.jumpFrequency)) {
                if (config.latencyTicks <= 0) {
                    tryJump(client);
                    pendingJumpTicks = -1;
                } else {
                    pendingJumpTicks = config.latencyTicks;
                }
            }
        }
        lastHurtTime = currentHurtTime;

        if (pendingJumpTicks > 0) {
            pendingJumpTicks--;
            if (pendingJumpTicks == 0) {
                tryJump(client);
                pendingJumpTicks = -1;
            }
        }
    }

    public static void onBeforeAttack(Minecraft client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        HitJumpConfig config = HitJumpConfig.getInstance();
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
                // Only clamp vanilla's 1..10 miss penalty; leave the 10000 GUI-close guard intact
                if (currentDelay > maxDelayTicks && currentDelay <= 10) {
                    attackCooldownField.setInt(client, maxDelayTicks);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static Field findAttackCooldownField(Class<?> minecraftClass) {
        Field[] fields = minecraftClass.getDeclaredFields();
        // Matches vanilla 1.8.9 Minecraft field layout around attackCooldown (leftClickCounter):
        // Screen, LoadingScreenRenderer, GameRenderer, int (attackCooldown), int (tempWidth), int (tempHeight)
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
            // Matches vanilla 1.8.9 LivingEntity layout around jumpTicks:
            // boolean, LivingEntity, int, LivingEntity, int, float, int (jumpTicks), float
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
