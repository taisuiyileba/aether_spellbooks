package com.aetherspellbooks.compat;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import static com.aetherspellbooks.compat.AetherCuriosBridge.reference;
import static io.wispforest.accessories.api.AccessoriesAPI.getOrDefaultAccessory;

final class AetherCurio implements ICurioItem {
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        if (!(context.entity() instanceof Player)) {
            return ICurioItem.super.canEquip(context, stack);
        }
        var reference = reference(context, stack);
        return reference != null && AccessoriesAPI.canEquip(stack, reference);
    }

    @Override
    public boolean canUnequip(SlotContext context, ItemStack stack) {
        var reference = reference(context, stack);
        return reference == null || AccessoriesAPI.canUnequip(stack, reference);
    }

    @Override
    @SuppressWarnings("removal") // Honor overrides of the bundled Accessories slot-aware method.
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        var reference = reference(context, stack);
        return reference != null && getOrDefaultAccessory(stack).canEquipFromUse(stack, reference);
    }

    @Override
    public void onEquipFromUse(SlotContext context, ItemStack stack) {
        var reference = reference(context, stack);
        if (reference != null) {
            getOrDefaultAccessory(stack).onEquipFromUse(stack, reference);
        }
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext context, ItemStack stack) {
        var reference = reference(context, stack);
        if (reference != null) {
            var sound = getOrDefaultAccessory(stack).getEquipSound(stack, reference);
            if (sound != null) {
                return new ICurio.SoundInfo(sound.event().value(), sound.volume(), sound.pitch());
            }
        }
        return ICurioItem.super.getEquipSound(context, stack);
    }

    @Override
    public void curioTick(SlotContext context, ItemStack stack) {
        var reference = reference(context, stack);
        if (reference != null) {
            getOrDefaultAccessory(stack).tick(stack, reference);
        }
    }

    @Override
    public void onEquip(SlotContext context, ItemStack previous, ItemStack stack) {
        var reference = reference(context, stack);
        if (reference != null) {
            getOrDefaultAccessory(stack).onEquip(stack, reference);
        }
    }

    @Override
    public void onUnequip(SlotContext context, ItemStack next, ItemStack stack) {
        var reference = reference(context, stack);
        if (reference != null) {
            getOrDefaultAccessory(stack).onUnequip(stack, reference);
        }
    }

    @Override
    public void curioBreak(SlotContext context, ItemStack stack) {
        var reference = reference(context, stack);
        if (reference != null) {
            getOrDefaultAccessory(stack).onBreak(stack, reference);
        }
    }

    @Override
    @SuppressWarnings("removal") // Curios 9.5.1's item capability delegates to this overload.
    public ICurio.DropRule getDropRule(SlotContext context, DamageSource source, int looting, boolean recentlyHit, ItemStack stack) {
        var reference = reference(context, stack);
        if (reference == null) {
            return ICurio.DropRule.DEFAULT;
        }
        return switch (getOrDefaultAccessory(stack).getDropRule(stack, reference, source)) {
            case KEEP -> ICurio.DropRule.ALWAYS_KEEP;
            case DROP -> ICurio.DropRule.ALWAYS_DROP;
            case DESTROY -> ICurio.DropRule.DESTROY;
            case DEFAULT -> ICurio.DropRule.DEFAULT;
        };
    }

    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext context, ResourceLocation id, ItemStack stack) {
        var reference = reference(context, stack);
        if (reference == null) {
            return ImmutableMultimap.of();
        }
        // Read component modifiers using the original slot name, then give dynamic modifiers the live slot.
        var modifiers = new AccessoryAttributeBuilder(reference)
                .addFrom(AccessoriesAPI.getAttributeModifiers(stack, null, reference.slotName(), reference.slot()));
        getOrDefaultAccessory(stack).getDynamicModifiers(stack, reference, modifiers);
        return modifiers.getAttributeModifiers(true);
    }
}
