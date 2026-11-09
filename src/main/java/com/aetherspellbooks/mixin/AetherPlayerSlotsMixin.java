package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.data.EntitySlotLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

import java.util.Map;
import java.util.SequencedMap;

@Mixin(value = EntitySlotLoader.class, remap = false)
abstract class AetherPlayerSlotsMixin {
    // Accessories .48 and .53 freeze the same loader output using different map implementations.
    @Group(name = "asb$filterPlayerSlots", min = 1, max = 1)
    @WrapOperation(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableMap;copyOf(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;"),
            require = 0)
    private ImmutableMap<EntityType<?>, Map<String, SlotType>> asb$filterImmutableMap(
            Map<EntityType<?>, Map<String, SlotType>> slots, Operation<ImmutableMap<EntityType<?>, Map<String, SlotType>>> original,
            @Local(argsOnly = true) ResourceManager resources) {
        asb$removeNativePlayerSlots(slots, resources);
        return original.call(slots);
    }

    @Group(name = "asb$filterPlayerSlots", min = 1, max = 1)
    @WrapOperation(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Collections;unmodifiableSequencedMap(Ljava/util/SequencedMap;)Ljava/util/SequencedMap;"),
            require = 0)
    private SequencedMap<EntityType<?>, SequencedMap<String, SlotType>> asb$filterSequencedMap(
            SequencedMap<EntityType<?>, SequencedMap<String, SlotType>> slots,
            Operation<SequencedMap<EntityType<?>, SequencedMap<String, SlotType>>> original,
            @Local(argsOnly = true) ResourceManager resources) {
        asb$removeNativePlayerSlots(slots, resources);
        return original.call(slots);
    }

    private static void asb$removeNativePlayerSlots(Map<EntityType<?>, ? extends Map<String, SlotType>> slots,
                                                   ResourceManager resources) {
        var playerSlots = slots.get(EntityType.PLAYER);
        if (playerSlots != null && resources.getResource(AetherCuriosBridge.BRIDGE_PACK_MARKER).isPresent()) {
            AetherCuriosBridge.removeNativeSlots(playerSlots);
        }
    }
}
