package com.aetherspellbooks.mixin;

import com.aetherspellbooks.client.accessory.AccessoryWearLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class PlayerHandsMixin {
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void asb$renderCastingGlove(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                        ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        var playerModel = ((PlayerRenderer) (Object) this).getModel();
        AccessoryWearLayer.renderFirstPerson(player, arm, arm == playerModel.rightArm, pose, buffers, light);
    }
}
