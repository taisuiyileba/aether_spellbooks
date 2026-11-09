package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.CuriosSlotReference;
import io.wispforest.accessories.api.slot.SlotReferenceImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.theillusivec4.curios.api.SlotContext;

@Mixin(value = SlotReferenceImpl.class, remap = false)
abstract class SlotReferenceImplMixin implements CuriosSlotReference {
    @Unique
    private SlotContext asb$curioContext;

    @Override
    public SlotContext asb$getCurioContext() {
        return asb$curioContext;
    }

    @Override
    public void asb$setCurioContext(SlotContext context) {
        asb$curioContext = context;
    }
}
