package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.CuriosSlotReference;
import com.aetherteam.aether.item.combat.abilities.armor.PhoenixArmor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PhoenixArmor.class, remap = false)
interface PhoenixGlovesMixin {
    @WrapOperation(method = "breakPhoenixGloves", at = @At(value = "INVOKE",
            target = "Lio/wispforest/accessories/api/AccessoriesCapability;get(Lnet/minecraft/world/entity/LivingEntity;)Lio/wispforest/accessories/api/AccessoriesCapability;"))
    private static AccessoriesCapability asb$replaceCurioGloves(LivingEntity entity, Operation<AccessoriesCapability> original,
            @Local(argsOnly = true) SlotEntryReference equipped, @Local(argsOnly = true) ItemStack replacement) {
        var reference = equipped.reference();
        if (CuriosSlotReference.context(reference) != null) {
            reference.setStack(replacement);
            return null;
        }
        return original.call(entity);
    }
}
