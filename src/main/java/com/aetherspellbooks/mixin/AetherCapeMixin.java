package com.aetherspellbooks.mixin;

import com.aetherspellbooks.client.accessory.AetherCuriosRender;
import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.aetherteam.aether.item.accessories.cape.CapeItem;
import com.aetherteam.aether.mixin.AetherMixinHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AetherMixinHooks.class, remap = false)
abstract class AetherCapeMixin {
    @Inject(method = "isCapeVisible", at = @At("HEAD"), cancellable = true)
    private static void asb$useCurioCape(LivingEntity entity, CallbackInfoReturnable<ItemStack> result) {
        if (AetherCuriosBridge.isEnabled(entity)) {
            var cape = AetherCuriosRender.visibleStack(entity, "back");
            result.setReturnValue(cape.getItem() instanceof CapeItem && AetherCuriosBridge.isBridged(cape) ? cape : ItemStack.EMPTY);
        }
    }
}
