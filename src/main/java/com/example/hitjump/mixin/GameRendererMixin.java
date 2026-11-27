package com.example.hitjump.mixin;

import com.example.hitjump.HitJumpMod;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "pick", at = @At("HEAD"), require = 0)
    private void hitjump$onPickHead(float tickDelta, CallbackInfo ci) {
        HitJumpMod.syncHeadYaw();
    }

    @Inject(method = "renderWorld", at = @At("HEAD"), require = 0)
    private void hitjump$onRenderWorldHead(float tickDelta, long limitTime, CallbackInfo ci) {
        HitJumpMod.syncHeadYaw();
    }

    @Inject(method = "renderWorld", at = @At("TAIL"), require = 0)
    private void hitjump$onRenderWorldTail(float tickDelta, long limitTime, CallbackInfo ci) {
        HitJumpMod.onRenderWorldTail(tickDelta);
    }

    @Inject(method = "transformCamera", at = @At("HEAD"))
    private void hitjump$onTransformCameraHead(float tickDelta, CallbackInfo ci) {
        HitJumpMod.onTransformCameraHead(tickDelta);
    }

    @Inject(method = "transformCamera", at = @At("TAIL"))
    private void hitjump$onTransformCameraTail(float tickDelta, CallbackInfo ci) {
        HitJumpMod.onTransformCameraTail(tickDelta);
    }
}
