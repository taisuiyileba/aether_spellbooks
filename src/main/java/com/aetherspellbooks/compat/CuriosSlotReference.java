package com.aetherspellbooks.compat;

import io.wispforest.accessories.api.slot.SlotReference;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Identifies references created for real Curios slots inside the sealed Accessories API. */
public interface CuriosSlotReference {
    SlotContext asb$getCurioContext();

    void asb$setCurioContext(SlotContext context);

    static SlotReference create(SlotContext context, String slotName) {
        SlotReference reference = SlotReference.of(context.entity(), slotName, context.index());
        ((CuriosSlotReference) (Object) reference).asb$setCurioContext(context);
        return reference;
    }

    static SlotContext context(Object reference) {
        return reference instanceof CuriosSlotReference curio ? curio.asb$getCurioContext() : null;
    }

    static IDynamicStackHandler inventory(SlotContext context) {
        return CuriosApi.getCuriosInventory(context.entity())
                .flatMap(curios -> curios.getStacksHandler(context.identifier()))
                .map(handler -> context.cosmetic() ? handler.getCosmeticStacks() : handler.getStacks())
                .filter(inventory -> context.index() >= 0 && context.index() < inventory.getSlots())
                .orElse(null);
    }
}
