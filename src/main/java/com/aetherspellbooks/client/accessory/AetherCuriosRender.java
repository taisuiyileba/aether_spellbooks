package com.aetherspellbooks.client.accessory;

import com.aetherspellbooks.compat.AetherCuriosBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wispforest.accessories.api.client.AccessoriesRendererRegistry;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.function.Consumer;

public final class AetherCuriosRender {
    private AetherCuriosRender() {}

    public static void render(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model, PoseStack pose,
                              MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        forEachVisible(player, slot -> {
            var stack = slot.stack();
            var reference = AetherCuriosBridge.reference(slot.slotContext(), stack);
            if (reference != null && CuriosRendererRegistry.getRenderer(stack.getItem()).isEmpty()) {
                var renderer = AccessoriesRendererRegistry.getRenderer(stack);
                if (renderer != null && renderer.shouldRender(true)) {
                    pose.pushPose();
                    renderer.render(stack, reference, pose, model, buffers, light, limbSwing, limbSwingAmount,
                            partialTicks, ageInTicks, netHeadYaw, headPitch);
                    pose.popPose();
                }
            }
        });
    }

    public static void renderFirstPerson(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
                                         HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light) {
        if (player.isInvisible()) {
            return;
        }
        forEachVisible(player, slot -> {
            var stack = slot.stack();
            var reference = AetherCuriosBridge.reference(slot.slotContext(), stack);
            if (reference != null) {
                var renderer = AccessoriesRendererRegistry.getRenderer(stack);
                if (renderer != null && renderer.shouldRenderInFirstPerson(arm, stack, reference)) {
                    pose.pushPose();
                    renderer.renderOnFirstPerson(arm, stack, reference, pose, model, buffers, light);
                    pose.popPose();
                }
            }
        });
    }

    public static ItemStack visibleStack(LivingEntity player, String identifier) {
        var handler = CuriosApi.getCuriosInventory(player).flatMap(inventory -> inventory.getStacksHandler(identifier)).orElse(null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                var slot = visibleSlot(player, handler, i);
                if (slot != null) {
                    return slot.stack();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void forEachVisible(LivingEntity player, Consumer<SlotResult> action) {
        if (AetherCuriosBridge.isEnabled(player)) {
            CuriosApi.getCuriosInventory(player).ifPresent(inventory -> inventory.getCurios().values().forEach(handler -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    var slot = visibleSlot(player, handler, i);
                    if (slot != null && AetherCuriosBridge.isBridged(slot.stack())) {
                        action.accept(slot);
                    }
                }
            }));
        }
    }

    private static SlotResult visibleSlot(LivingEntity player, ICurioStacksHandler handler, int index) {
        var active = handler.getActiveStates();
        if (index < active.size() && !active.get(index)) {
            return null;
        }
        var cosmetic = handler.getCosmeticStacks().getStackInSlot(index);
        var renders = handler.getRenders();
        boolean visible = index < renders.size() && renders.get(index);
        boolean hasCosmetic = !cosmetic.isEmpty();
        var stack = hasCosmetic ? cosmetic : visible ? handler.getStacks().getStackInSlot(index) : ItemStack.EMPTY;
        return stack.isEmpty() ? null : new SlotResult(new SlotContext(handler.getIdentifier(), player, index, hasCosmetic, visible), stack);
    }
}
