package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.wispforest.accessories.impl.AccessoriesContainerImpl;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AccessoriesContainerImpl.class, remap = false)
abstract class RemovedAccessorySlotsMixin {
    @WrapOperation(method = "readContainers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/SimpleContainer;fromTag(Lnet/minecraft/nbt/ListTag;Lnet/minecraft/core/HolderLookup$Provider;)V"))
    private static void asb$recoverRemovedSlotContents(SimpleContainer container, ListTag stacks, HolderLookup.Provider registries,
                                                       Operation<Void> original, @Local LocalRef<SimpleContainer> decoded) {
        // The bundled loader uses a zero-size container here. Give its invalid-stack recovery room to read both lists.
        if (!stacks.isEmpty() && AetherCuriosBridge.hasBridgeTags()) {
            container = new SimpleContainer(stacks.size());
            decoded.set(container);
        }
        original.call(container, stacks, registries);
    }
}
