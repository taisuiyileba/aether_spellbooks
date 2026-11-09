package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.aetherspellbooks.compat.CuriosSlotReference;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Collection;

@Mixin(value = AccessoriesAPI.class, remap = false)
abstract class AccessoriesApiMixin {
    @Inject(method = "canInsertIntoSlot", at = @At("HEAD"), cancellable = true)
    private static void asb$useCuriosForAetherGear(ItemStack stack, SlotReference reference, CallbackInfoReturnable<Boolean> result) {
        if (AetherCuriosBridge.isEnabled(reference.entity()) && AetherCuriosBridge.isBridged(stack)
                && CuriosSlotReference.context(reference) == null) {
            result.setReturnValue(false);
        }
    }

    @Inject(method = "getStackSlotTypes(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/Collection;",
            at = @At("HEAD"), cancellable = true)
    private static void asb$hideNativeSlotSuggestions(Level level, LivingEntity entity, ItemStack stack,
                                                     CallbackInfoReturnable<Collection<SlotType>> result) {
        if (AetherCuriosBridge.isEnabled(entity) && AetherCuriosBridge.isBridged(stack)) {
            result.setReturnValue(new ArrayList<>());
        }
    }

    @Inject(method = "breakStack", at = @At("HEAD"), cancellable = true)
    private static void asb$breakCurio(SlotReference reference, CallbackInfo callback) {
        var context = CuriosSlotReference.context(reference);
        if (context != null) {
            CuriosApi.broadcastCurioBreakEvent(context);
            callback.cancel();
        }
    }
}
