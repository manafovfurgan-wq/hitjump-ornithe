package com.example.hitjump.mixin;

import com.example.hitjump.HitJumpMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void hitjump$onTickHead(CallbackInfo ci) {
        HitJumpMod.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hitjump$onTickTail(CallbackInfo ci) {
        HitJumpMod.onClientTickEnd((Minecraft) (Object) this);
    }

    @Inject(method = "doAttack", at = @At("HEAD"), require = 0)
    private void hitjump$onBeforeAttack(CallbackInfo ci) {
        HitJumpMod.onBeforeAttack((Minecraft) (Object) this);
    }

    @Inject(method = "doAttack", at = @At("TAIL"), require = 0)
    private void hitjump$onAfterAttack(CallbackInfo ci) {
        HitJumpMod.onAfterAttack((Minecraft) (Object) this);
    }

    @Inject(method = "doPick", at = @At("HEAD"), cancellable = true, require = 0)
    private void hitjump$onDoPick(CallbackInfo ci) {
        if (HitJumpMod.shouldBlockPickBlock()) {
            ci.cancel();
        }
    }
}
