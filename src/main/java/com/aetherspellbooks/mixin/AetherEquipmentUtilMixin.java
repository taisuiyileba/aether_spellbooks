package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.aetherteam.aether.item.EquipmentUtil;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(value = EquipmentUtil.class, remap = false)
abstract class AetherEquipmentUtilMixin {
    @Inject(method = "findFirstAccessory(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private static void asb$findCurio(LivingEntity entity, Predicate<ItemStack> matches,
                                     CallbackInfoReturnable<Optional<SlotEntryReference>> result) {
        if (result.getReturnValue().isEmpty()) {
            var curios = AetherCuriosBridge.findEquipped(entity, matches);
            if (!curios.isEmpty()) {
                result.setReturnValue(Optional.of(curios.getFirst()));
            }
        }
    }

    @Inject(method = "getAccessories", at = @At("RETURN"), cancellable = true)
    private static void asb$getCurios(LivingEntity entity, Item item, CallbackInfoReturnable<List<SlotEntryReference>> result) {
        var curios = AetherCuriosBridge.findEquipped(entity, stack -> stack.is(item));
        if (!curios.isEmpty()) {
            var equipped = new ArrayList<>(result.getReturnValue());
            equipped.addAll(curios);
            result.setReturnValue(equipped);
        }
    }
}
