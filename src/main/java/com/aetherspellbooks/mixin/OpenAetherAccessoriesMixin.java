package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.aetherteam.aether.network.packet.serverbound.OpenAccessoriesPacket;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;
import top.theillusivec4.curios.common.network.server.CuriosServerPayloadHandler;

@Mixin(value = OpenAccessoriesPacket.class, remap = false)
abstract class OpenAetherAccessoriesMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void asb$openCurios(OpenAccessoriesPacket packet, IPayloadContext context, CallbackInfo callback) {
        if (AetherCuriosBridge.isEnabled(context.player())) {
            CuriosServerPayloadHandler.getInstance().handleOpenCurios(new CPacketOpenCurios(packet.carryStack()), context);
            callback.cancel();
        }
    }
}
