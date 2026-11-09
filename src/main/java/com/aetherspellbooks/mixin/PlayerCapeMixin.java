package com.aetherspellbooks.mixin;

import com.aetherspellbooks.client.accessory.WearableSlot;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
abstract class PlayerCapeMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void asb$useWornMantle(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                   float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (WearableSlot.MANTLE.isVisible(player)) {
            ci.cancel();
        }
    }
}
