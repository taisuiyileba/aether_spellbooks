package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.aetherteam.aether.block.dispenser.AetherDispenseBehaviors;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AetherDispenseBehaviors.class, remap = false)
abstract class AetherDispenserMixin {
    @Inject(method = "dispenseAccessory", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;split(I)Lnet/minecraft/world/item/ItemStack;"),
            cancellable = true)
    private static void asb$dispenseIntoCurios(BlockSource source, ItemStack stack, CallbackInfoReturnable<Boolean> result,
                                              @Local LivingEntity target) {
        if (AetherCuriosBridge.isEnabled(target) && AetherCuriosBridge.isBridged(stack)) {
            result.setReturnValue(AetherCuriosBridge.equipFromDispenser(target, stack));
        }
    }
}
