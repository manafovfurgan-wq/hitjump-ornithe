package com.example.hitjump.mixin;

import com.example.hitjump.HitJumpMod;
import com.example.hitjump.ParodyHudRenderer;
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

    @Inject(method = "renderWorld(FJ)V", at = @At("HEAD"), require = 0)
    private void hitjump$onRenderWorldHead(float tickDelta, long limitTime, CallbackInfo ci) {
        HitJumpMod.syncHeadYaw();
    }

    // Fires right before vanilla clears the depth buffer for the first-person hand
    @Inject(method = "renderWorld(IFJ)V", at = @At(value = "CONSTANT", args = "stringValue=hand"), require = 0)
    private void hitjump$onRenderWorldBeforeHand(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        HitJumpMod.onRenderWorldBeforeHand(tickDelta);
    }

    @Inject(method = "renderWorldPass(IFJ)V", at = @At(value = "CONSTANT", args = "stringValue=hand"), require = 0)
    private void hitjump$onRenderWorldPassBeforeHand(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
        HitJumpMod.onRenderWorldBeforeHand(tickDelta);
    }

    @Inject(method = "renderWorld(FJ)V", at = @At("TAIL"), require = 0)
    private void hitjump$onRenderWorldTail(float tickDelta, long limitTime, CallbackInfo ci) {
        HitJumpMod.onRenderWorldTail(tickDelta);
        ParodyHudRenderer.renderInWorld();
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
