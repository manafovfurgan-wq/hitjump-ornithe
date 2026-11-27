package com.example.hitjump;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
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

    @Override
    public void onInitializeClient() {
        HitJumpConfig.load();
    }

    public static void onClientTick(Minecraft client) {
        if (client == null) {
            return;
        }

        // Press 'H' in-game (when no menu is open) to open HitJump Settings
        boolean isMenuKeyDown = Keyboard.isKeyDown(Keyboard.KEY_H);
        if (isMenuKeyDown && !wasMenuKeyDown && client.screen == null && client.player != null) {
            client.openScreen(new HitJumpConfigScreen(null));
        }
        wasMenuKeyDown = isMenuKeyDown;

        HitJumpConfig config = HitJumpConfig.getInstance();
        if (!config.enabled || client.player == null || client.world == null) {
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
            // Matches vanilla 1.8.9 LivingEntity layout around hurtTime:
            // boolean, int, int, int (hurtTime), int, float, int, float, float
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
