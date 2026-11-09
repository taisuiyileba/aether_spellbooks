package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.CuriosSlotReference;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SlotReference.class, remap = false)
interface SlotReferenceMixin {
    @Inject(method = "getStack", at = @At("HEAD"), cancellable = true)
    private void asb$getCurioStack(CallbackInfoReturnable<ItemStack> result) {
        var context = CuriosSlotReference.context(this);
        if (context != null) {
            var inventory = CuriosSlotReference.inventory(context);
            result.setReturnValue(inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(context.index()));
        }
    }

    @Inject(method = "setStack", at = @At("HEAD"), cancellable = true)
    private void asb$setCurioStack(ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        var context = CuriosSlotReference.context(this);
        if (context != null) {
            var inventory = CuriosSlotReference.inventory(context);
            if (inventory != null) {
                inventory.setStackInSlot(context.index(), stack);
            }
            result.setReturnValue(inventory != null);
        }
    }

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void asb$isCurioSlotValid(CallbackInfoReturnable<Boolean> result) {
        var context = CuriosSlotReference.context(this);
        if (context != null) {
            result.setReturnValue(CuriosSlotReference.inventory(context) != null);
        }
    }

    @Inject(method = "createSlotPath", at = @At("HEAD"), cancellable = true)
    private void asb$createCurioSlotPath(CallbackInfoReturnable<String> result) {
        var context = CuriosSlotReference.context(this);
        if (context != null) {
            result.setReturnValue(AccessoryAttributeBuilder.createSlotPath(context.identifier(), context.index()));
        }
    }
}
