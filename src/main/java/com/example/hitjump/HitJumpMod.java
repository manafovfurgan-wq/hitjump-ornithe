package com.example.hitjump;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.Random;

public class HitJumpMod implements ClientModInitializer {
    private static final Random RANDOM = new Random();
    private static int lastHurtTime = 0;
    private static int pendingJumpTicks = -1;
    private static boolean wasMenuKeyDown = false;

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

        int currentHurtTime = client.player.hurtTime;

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
